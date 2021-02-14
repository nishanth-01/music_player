package com.example.mediasession;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContentResolverCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.room.Room;

import com.example.mediasession.room.PlaylistsDatabase;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ServiceMediaPlayback extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener, Repositary.Observer {
    private static final String TAG = "ServiceMediaPlayback";
    /* move all declared constants and make sure they dont conflict */

    /*must check - https://developer.android.com/about/versions/pie/power*/
    /*test it with malformated with audio file*/
    static final byte SERVICE_FAILED = -2;
    static final byte SERVICE_SUSPENDED = 1;
    /*this also  means that controllers are ready*/
    static final byte SERVICE_CONNECTED = 2;

    static final String MEDIA_DESCRIPTION_KEY_ARTIST = "md_key_artist";
    static final String MEDIA_DESCRIPTION_KEY_GENRE = "md_key_genre";
    static final String MEDIA_DESCRIPTION_KEY_DURATION = "md_key_duration";

    /* custom actions */
    static final String ACTION_ADD_PLAYLIST = "action_add_playlist";
    static final String ACTION_REMOVE_PLAYLIST = "action_remove_playlist";
    static final String ACTION_REMOVE_QUEUE_ITEM = "action_remove_queue_item";
    static final String ACTION_ADD_PLAYLIST_MEMBER = "action_add_playlist_member";
    static final String ACTION_REMOVE_PLAYLIST_MEMBER = "action_remove_playlist_member";
    /** CADK - Custom Action Data Key **/
    static final String CADK_PLAYLIST_NAME = "cadk_playlist_name";
    /** CADK - Custom Action Data Key **/
    static final String CADK_QUEUE_ID = "cadk_queue_id";
    /** CADK - Custom Action Data Key **/
    static final String CADK_PLAYLIST_ID = "cadk_playlist_id";
    /** CADK - Custom Action Data Key **/
    static final String CADK_MEMBER_URI = "cadk_member_uri";

    static final String EXTRAS_KEY_PLAYER_STATE = "exoplayer_state";

    private final int THUMBNAIL_SIDES = 60;//change by checking screen size

    private final String[] LOCAL_MEDIA_QUERY_PROJECTION =
            new String[] {MediaStore.Audio.Media._ID , MediaStore.Audio.Media.DISPLAY_NAME };
    private ContentObserver mInternalContentObserver;
    private ContentObserver mExternalContentObserver;
    private final String[] PLAYLIST_MEMBER_SELECTION =
            new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID};

    private AudioFocusRequest mAFocusRequest;

    /*Browsable MediaItem ID*/
    //add better key
    static final String ROOT_ID_ALL_CONTENT = "rootid_all_content##"; //Make it Random   -client browse content ON

    //MediaSession Extras key
    static final String EXTRAS_KEY_PLAY_WHEN_READY = "playWhenReady";

    private static final String MEDIA_SESSION_DEBUGGER_ID = "MEDIA_SESSION_DEBUGGER";
    private final String CHANNEL_ID = "My_Channel_ID";
    private final int MAIN_NOTIFICATION_ID = 1;
    private final int PENDING_INTENT_CODE_CONTENT = 2;   //Notification content intent

    private MediaSessionCompat mMediaSession;
    @NonNull private final PlaybackStateCompat.Builder mSessionStateBuilder = new PlaybackStateCompat.Builder();
    @NonNull private final MediaMetadataCompat.Builder mSessionMetaDataBuilder = new MediaMetadataCompat.Builder();
    private MediaSessionCompat.Callback mSessionCallback;

    /*  Media Player  */
    private SimpleExoPlayer mPlayer ;
    private final MediaItem.Builder mExoMediaItemBuilder = new MediaItem.Builder();

    private PlayerNotificationManager mPlayerNotification ; //Notification

    private final MediaDescriptionCompat.Builder mMediaDiscriptionBuilder = new MediaDescriptionCompat.Builder();

    private MediaMetadataCompat mCurrentMetaData;   //only for mGetMetadataFrom

    private boolean mInForeground ;
    private boolean mLoadBrowsableMediaItemsCalled;

    @NonNull private final List<MediaBrowserCompat.MediaItem> mCollectionRootList = new ArrayList<>();

    private ContentResolver mContentResolver;

    // Data
    /** List of All Music from shared storage **/
    @NonNull private final List<MediaBrowserCompat.MediaItem> mSharedStorageMedia = new ArrayList<>();
    /** key - String representation of the uri ; value - Corresponding playable MediaItem **/
    private final HashMap<String, MediaBrowserCompat.MediaItem> mSharedStorageMusicMap = new HashMap<>();

    /** PlayList **/
    @NonNull private final List<MediaBrowserCompat.MediaItem> mAllPlayListMediaItem = new ArrayList<>();
    @NonNull private final List<String> mAllPlayListName = new ArrayList<>();
    private Repositary mRepositary;

    private PlaylistsDatabase mPlaylistsDatabase;

    private boolean mReciveTransportControlls;


    /*  add low-memeory device varient  */
    /*  check id device in power saving mode */
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        //add random rootId for unique to each client request when thirdparty controll allowed
        //also use clientUid
        if(clientPackageName.equals(this.getPackageName())) {
            return new BrowserRoot(ROOT_ID_ALL_CONTENT, null);
            //if controller only mode requested in Bundle rootHints give that
        } else {
            return null;    //return different parent id
        }
    }

    // Warning : MediaItems should not contain icon bitmaps
    // Note : returning null means request is rejected
    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        Log.w(TAG, LT.TEMP_IMPLEMENTATION+"add result.detach, result.sendError");
        /*  change code which loads data on request  */
        final String ID_LOCAL_MEDIA = getString(R.string.id_local_media);
        final String ID_LOCAL_PLAYLISTS = getString(R.string.id_local_playlists);
        if(parentId.equals(ID_LOCAL_MEDIA)){
            result.sendResult(mSharedStorageMedia);
        } else if(parentId.equals(ID_LOCAL_PLAYLISTS)) {
            /* add async */
            result.sendResult(mAllPlayListMediaItem);
        } else {
            result.sendResult(null);//Invalid parentId
        }
    }



    //  ____________________________ Service Callbacks ____________________________

    @Override
    public void onCreate() {
        super.onCreate();

        //_________ Loading DATA _________

        if(mContentResolver==null)
            mContentResolver = getContentResolver();
        /*     ***OVERRIDE ALL METHODES FOR COMPATABILITY***    */
        mInternalContentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                Log.i(LT.IP , LT.TEMP_IMPLEMENTATION+"___INTERENAL DATA CHANGED___");
            }
        };
        mExternalContentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                Log.i(LT.IP , LT.TEMP_IMPLEMENTATION+"___EXTERNAL DATA CHANGED___");
            }
        };
        mContentResolver.registerContentObserver(MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                /*check this arg*/false, mInternalContentObserver);
        mContentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                /*check this arg*/false, mExternalContentObserver);

        mSharedStorageMedia.addAll(mLoadSharedStorageMedia(MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                mSharedStorageMusicMap));
        Log.w(TAG, LT.UNIMPLEMENTED+"add toggle button for disabling local media");
        mSharedStorageMedia.addAll(mLoadSharedStorageMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mSharedStorageMusicMap));

        if(mPlaylistsDatabase==null){
            mPlaylistsDatabase = Room.databaseBuilder(getApplicationContext(),
                    PlaylistsDatabase.class, "playlists_database").build();
            mRepositary = new Repositary(mPlaylistsDatabase);
            //mRepositary.loadRepository();
        }

        mRepositary.subscribePlaylistData(this::onDataChanged);


        //________________________________

        // ____________MediaSession _______________
        mMediaSession = new MediaSessionCompat( this , MEDIA_SESSION_DEBUGGER_ID);
        mMediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onCommand(String command, Bundle extras, ResultReceiver cb) {

            }

            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                //rewrite
                if ((mIsStringEmpty(mediaId)) || (!mSharedStorageMusicMap.containsKey(mediaId))){
                    Log.w(ServiceMediaPlayback.this.getClass().getCanonicalName(),
                            LT.TEMP_IMPLEMENTATION+"try to handle invalid mediaId without throwing exception");
                    throw new IllegalArgumentException("invalid mediaId");
                }

                MediaBrowserCompat.MediaItem mediaItem = mSharedStorageMusicMap.get((String) mediaId);

                if(mediaItem.isBrowsable()){
                    //Handle various MediaItem like playlist by data in MediaItem bundle
                    Log.w(LT.IP , "onPlayFromMediaId requested mediaItem is browsable");
                } else if(mediaItem.isPlayable()){
                    mPlayer.clearMediaItems();
                    MediaItem exoMediaItem = mExoMediaItemBuilder
                            .setUri(mediaItem.getDescription().getMediaUri())
                            .setMediaId(mediaItem.getMediaId()).build();
                    mPlayer.setMediaItem(exoMediaItem);
                    mPlayer.prepare();
                    mPlayer.setPlayWhenReady(true);
                }
            }

            @Override
            public void onAddQueueItem(MediaDescriptionCompat description) {
                mPlayer.addMediaItem(MediaItem.fromUri(description.getMediaUri()));
            }

            @Override
            public void onPlay() {
                if(mReciveTransportControlls){
                    mPlayer.setPlayWhenReady(true);
                }/*else do nothing*/
            }

            @Override
            public void onPause() {
                if(mReciveTransportControlls){
                    mPlayer.setPlayWhenReady(false);
                }/*else do nothing*/
            }

            @Override
            public void onSkipToQueueItem(long id) {
                try{
                    mPlayer.seekTo((int)id, C.TIME_UNSET);
                    mPlayer.setPlayWhenReady(true);
                } catch (IllegalSeekPositionException e){
                    Log.w(LT.IP, LT.UNIMPLEMENTED+"onSkipToQueueItem, notify invalid seek position ");
                }
            }

            @Override
            public void onSkipToNext() {
                mPlayer.next();
            }

            @Override
            public void onSkipToPrevious() {
                mPlayer.previous();
            }
        });

        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        //State
        //mSessionStateBuilder = new PlaybackStateCompat.Builder();

        setSessionToken(mMediaSession.getSessionToken());

        // _________________________________________

        //_______ExoPlayer_______
        mPlayer = new SimpleExoPlayer.Builder( this ).build();
        AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder();
        Log.w(getClass().getCanonicalName(), "Check setAllowedCapturePolicy and set right value");
        audioAttributesBuilder.setContentType(C.CONTENT_TYPE_MUSIC)
                .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL).setUsage(C.USAGE_MEDIA);
        mPlayer.setAudioAttributes(audioAttributesBuilder.build() , true );
        mPlayer.setHandleAudioBecomingNoisy(true);
        mPlayer.addListener( new Player.EventListener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state){
                    case Player.STATE_IDLE: mReciveTransportControlls = false;
                    mPlayer.setPlayWhenReady(false);break;
                    case Player.STATE_BUFFERING: break;
                    case Player.STATE_READY: break;
                    case Player.STATE_ENDED: {
                        mPlayer.setPlayWhenReady(false);
                        try{
                            mPlayer.seekTo(0, C.TIME_UNSET);
                        } catch (IllegalSeekPositionException e){
                            //do nothing
                        }
                        break;
                    }
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                /*try{
                    mPlayer.prepare();
                    if(playWhenReady) mPlayer.seekTo(mPlayer.getCurrentWindowIndex(), C.TIME_UNSET);
                } catch (IllegalSeekPositionException e){
                    //do nothing
                }*/
                if(playWhenReady) mPlayer.prepare();

                Bundle bundle = new Bundle(1);
                bundle.putBoolean(EXTRAS_KEY_PLAY_WHEN_READY, playWhenReady);
                mSessionStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                        mPlayer.getCurrentPosition(), mPlayer.getPlaybackParameters().speed)
                        .setExtras(bundle);
                mMediaSession.setPlaybackState(mSessionStateBuilder.build());
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if(isPlaying) {
                    mSessionStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                            mPlayer.getCurrentPosition(), mPlayer.getPlaybackParameters().speed);
                    mMediaSession.setPlaybackState(mSessionStateBuilder.build());
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e(LT.IP, "ExoPlaybackException");
                error.printStackTrace();
                Toast.makeText(ServiceMediaPlayback.this, "onPlayerError occured", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPositionDiscontinuity(int reason) {

            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                Log.w(TAG, LT.UNTESTED+"onTimelineChanged");
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED){
                    if(timeline.isEmpty()){
                        mReciveTransportControlls = false;
                        mMediaSession.setQueue(new ArrayList<>());
                        return;
                    }
                    mReciveTransportControlls = true;
                    int windowCount = timeline.getWindowCount();
                    List<MediaSessionCompat.QueueItem> queue = new ArrayList<>(windowCount);
                    Log.w(TAG, LT.TEMP_IMPLEMENTATION+"manage queue id properly");
                    for(int i=0; i<windowCount; i++){
                        Timeline.Window window = timeline.getWindow(i, new Timeline.Window());
                        MediaBrowserCompat.MediaItem mediaItem =
                                mSharedStorageMusicMap.get(window.mediaItem.mediaId);
                        if(mediaItem != null)
                            queue.add(new MediaSessionCompat.QueueItem(mediaItem.getDescription(), i));
                        else Log.e(TAG, "onTimelineChanged ,mediaItem doesn't exist on map");
                    }
                    mMediaSession.setQueue(queue);
                } else if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE){

                } else {

                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                //update current queue item mPlayer.getCurrentWindowIndex();

                Log.w(TAG, LT.TEMP_IMPLEMENTATION+"check if metadata is updated preperly");
                if (mediaItem != null) {
                    MediaBrowserCompat.MediaItem mediaItem1 =
                            mSharedStorageMusicMap.get(mediaItem.mediaId);
                    final MediaDescriptionCompat md = mediaItem1.getDescription();
                    int albumArtSize = getResources().getDimensionPixelSize(R.dimen.player_main_albumart_sides);
                    final Uri albumArtUri = md.getMediaUri(); Bitmap albumArt = null;
                    try{
                        albumArt = getContentResolver().loadThumbnail(albumArtUri,
                                new Size(albumArtSize, albumArtSize), null);
                    } catch (IOException e){
                        Log.e(TAG, "IOException failed to load AlbumArt");e.printStackTrace();
                    } finally {
                        if(albumArt == null) albumArt = BitmapFactory
                                .decodeResource(getResources(), R.drawable.baseline_album_black_48dp);
                    }
                    mSessionMetaDataBuilder
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                                    (String) md.getTitle())
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                                    md.getExtras().getString(MediaStore.Audio.Media.ARTIST))
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
                } else {
                    mSessionMetaDataBuilder
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, null)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, null)
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
                }
                mCurrentMetaData = mSessionMetaDataBuilder.build();
                mMediaSession.setMetadata(mSessionMetaDataBuilder.build());
            }
        });
        //_____________________

        //______Notifications______
        PlayerNotificationManager.MediaDescriptionAdapter mediaDescriptionAdapter
                = new PlayerNotificationManager.MediaDescriptionAdapter() {
            @Override
            public CharSequence getCurrentContentTitle(Player player) {
                return mCurrentMetaData.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
            }

            @Nullable
            @Override
            public PendingIntent createCurrentContentIntent(Player player) {
                Intent intent = new Intent(getApplicationContext() , MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                return PendingIntent.getActivity(getApplicationContext(),
                        PENDING_INTENT_CODE_CONTENT, intent ,PendingIntent.FLAG_UPDATE_CURRENT);
            }

            @Nullable
            @Override
            public CharSequence getCurrentContentText(Player player) {
                return mCurrentMetaData.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            }

            @Nullable
            @Override
            public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                return mCurrentMetaData.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            }
        };

        mPlayerNotification = PlayerNotificationManager.createWithNotificationChannel(
                this ,
                CHANNEL_ID ,
                R.string.notification_channel_name ,
                R.string.notification_channel_description ,
                MAIN_NOTIFICATION_ID,
                mediaDescriptionAdapter ,
                new PlayerNotificationManager.NotificationListener() {
                    //VOLATILE
                    @Override
                    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                        if (notificationId == MAIN_NOTIFICATION_ID) {
                            if (dismissedByUser)/* stopSelf();//add*/;
                            else Log.d("Notification" , "onNotificationCancelled called with dismissedByUser:false");
                        }
                    }

                    @Override
                    public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                        if (MAIN_NOTIFICATION_ID == notificationId) {
                            if ( ongoing ) {
                                if (!mInForeground) {
                                    Intent startFSIntent = new Intent(getApplicationContext() , ServiceMediaPlayback.class);
                                    startForegroundService(startFSIntent);
                                    mInForeground = true;
                                    startForeground(MAIN_NOTIFICATION_ID, notification);
                                }
                                NotificationManager notificationManager =
                                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                notificationManager.notify(MAIN_NOTIFICATION_ID, notification);
                            }
                            else {
                                stopForeground(false);
                                mInForeground = false;
                            }
                        } else {
                            Log.i(ServiceMediaPlayback.this.getClass().getCanonicalName(),
                                    "onNotificationPosted() called with notificationId:"+notificationId);
                        }
                    }
                });
        mPlayerNotification.setPlayer( mPlayer );
        mPlayerNotification.setMediaSessionToken( mMediaSession.getSessionToken() );
        mPlayerNotification.setPriority(NotificationCompat.PRIORITY_MAX);
        mPlayerNotification.setUseChronometer( true );
        //________________________

        //Activiting MediaSession
        mMediaSession.setActive(true);  //Must be active to recive callbacks
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent , flags , startId);
        MediaButtonReceiver.handleIntent(mMediaSession, intent);
        Log.d("ServiceMediaPlayback" , "onStartCommand()");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        mPlayer.setForegroundMode(true);
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.w(ServiceMediaPlayback.this.getClass().getCanonicalName() ,
                LT.TEMP_IMPLEMENTATION+"handle properly");
        if(mPlayer!=null){
            mPlayer.setForegroundMode(false);
            if(mPlayer.isPlaying()) return false;
        }
        stopSelf();
        return false;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d("ServiceMediaPlayback" , "onRebind()");
    }

    @Override
    public void onDestroy() {
        mRepositary.unsubscribePlaylistData(this::onDataChanged);
        mRepositary.updateDatabaseAndRelease();

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(mAFocusRequest!=null) audioManager.abandonAudioFocusRequest(mAFocusRequest);

        Log.d("ServiceMediaPlayback" , "onDestroy called");
        mContentResolver.unregisterContentObserver(mInternalContentObserver);
        mContentResolver.unregisterContentObserver(mExternalContentObserver);
        //Release Resources
        if (mPlayerNotification != null) mPlayerNotification.setPlayer(null);

        if(mPlayer != null) {
            mPlayer.setHandleAudioBecomingNoisy(false);
            mPlayer.setForegroundMode(false);
            mPlayer.release();
            mPlayer = null;
        }

        mMediaSession.setActive(false); mMediaSession.release(); mMediaSession = null;
        super.onDestroy();
    }

    @Override
    public void onCustomAction(@NonNull String action, Bundle extras, @NonNull Result<Bundle> result) {
        switch (action){
            case ACTION_REMOVE_QUEUE_ITEM:
                final int queueId = (int)extras.getInt(CADK_QUEUE_ID, -2);
                try{
                    mPlayer.removeMediaItem(queueId);
                } catch (Exception e){
                    Log.w(LT.IP, LT.UNIMPLEMENTED+"onCustomAction cant remove queue item");
                    result.sendError(null);
                    return;
                }
                result.sendResult(null);
                return;

            case ACTION_ADD_PLAYLIST:
                final String addPlaylistName = extras.getString(CADK_PLAYLIST_NAME);
                mRepositary.addPlaylists((addPlaylistName ==null ? "" : addPlaylistName),
                        null, null, result);
                return;

            case ACTION_REMOVE_PLAYLIST:
                final int removePlaylistId = extras.getInt(CADK_PLAYLIST_ID, -1);
                mRepositary.removePlaylist(removePlaylistId, result);
                return;
                //action add/remove member
            default: super.onCustomAction(action, extras, result);
        }
    }

    // _____________________________________________________________________________

    /**
     * @param uri the uri to query
     * @param hashMap puts the result into the map if NonNull
     * @return null in case of error
     * **/
    @Nullable
    private List<MediaBrowserCompat.MediaItem> mLoadSharedStorageMedia(
            @NonNull Uri uri , @Nullable HashMap<String,MediaBrowserCompat.MediaItem> hashMap)
            throws IllegalArgumentException{
        if(uri==null) throw new IllegalArgumentException("uri can't be null");
        // *** use IS_MUSIC constant
        Cursor localMediaCursor = ContentResolverCompat.query( getContentResolver(),
                        uri ,
                        LOCAL_MEDIA_QUERY_PROJECTION,
                        null,/*add*/
                        null,/*add*/
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER,
                        null);

        List<MediaBrowserCompat.MediaItem> localMediaItems = null;
        if (localMediaCursor != null) {
            if (localMediaCursor.moveToFirst()) {
                //default values
                final String defaultArtistName = getString(R.string.mediadescription_default_artist_name);
                final String defaultGenreName = getString(R.string.mediadescription_default_genre_name);
                final String defaultDuration = getString(R.string.mediadescription_default_duration);
                final String defaultDisplayName = getString(R.string.mediadescription_default_display_name);
                //_____________

                localMediaItems = new ArrayList<>();
                boolean appendToMap = false;
                if(hashMap!=null) appendToMap = true;
                int i = 0;
                do {
                    long id = localMediaCursor
                            .getLong(localMediaCursor.getColumnIndex(MediaStore.Audio.Media._ID));
                    Uri mediaUri = ContentUris.withAppendedId(uri,id);
                    String mediaId = mediaUri.toString();
                    String displayName = localMediaCursor.
                            getString(localMediaCursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                    if(mIsStringEmpty(displayName)) displayName = defaultDisplayName;

                    MediaDescriptionCompat mediaDescription =
                            mMakeMediaDescriptor(mediaUri , mediaId , displayName, defaultArtistName,
                                    defaultGenreName, defaultDuration);
                    if (mediaDescription == null){
                        i++;//skip this element
                        continue;
                    }
                    MediaBrowserCompat.MediaItem mediaItem =
                            new MediaBrowserCompat.MediaItem(mediaDescription, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);

                    localMediaItems.add( mediaItem );
                    if(appendToMap) hashMap.put(mediaId , mediaItem);

                    i++;
                } while (localMediaCursor.moveToNext());

                localMediaCursor.close(); localMediaCursor = null;
            }
        } else {    /*  else no media   */
            Toast.makeText( ServiceMediaPlayback.this , "cant load data(Replace with dialouge asking to retry)",
                    Toast.LENGTH_SHORT).show();    //replace with dialouge
        }
        return localMediaItems;
    }

    /*Replace with MediaStore Way*/
    @Nullable
    private MediaDescriptionCompat mMakeMediaDescriptor(@NonNull Uri uri,
                                                        @NonNull String mediaId,
                                                        String displayName,
                                                        String defaultArtistName,
                                                        String defaultGenre,
                                                        String defaultDuration) {
        Log.w(LT.IP , LT.INEFFICIENT+"check if this can be improved");
        Log.w(LT.IP, LT.UNIMPLEMENTED+"add appropriate name if any data is empty");
        if(uri==null || mediaId==null) return null;

        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        Log.w(LT.IP, LT.TEMP_IMPLEMENTATION+"Replace MediaMetadataRetriever with MediaStore Way");
        try {
            mediaMetadataRetriever.setDataSource(getApplicationContext(), uri);
        } catch (Exception e) {
            return null;
        }

        String stringGenre = mediaMetadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
        String stringArtist = mediaMetadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String durationString = mediaMetadataRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        mediaMetadataRetriever.release();
        Log.w(LT.IP, "mMakeMediaDescriptor, check duration is right duration");

        if(mIsStringEmpty(stringGenre)) stringGenre = defaultGenre;
        if(mIsStringEmpty(stringArtist)) stringArtist = defaultArtistName;
        if(mIsStringEmpty(durationString)) durationString = defaultDuration;
        final Bundle extras = new Bundle(3);
        extras.putString(MEDIA_DESCRIPTION_KEY_ARTIST, stringArtist);
        extras.putString(MEDIA_DESCRIPTION_KEY_DURATION, durationString);
        extras.putString(MEDIA_DESCRIPTION_KEY_GENRE, stringGenre);

        Bitmap bitMapThumbNail = null;
        //Note : Extend it to support various screen sizes
        //Make Sure this doesnt take too much memory
        Log.w(LT.IP, "ThumbNail Loader code");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bitMapThumbNail = getContentResolver()
                        .loadThumbnail(uri, new Size(THUMBNAIL_SIDES, THUMBNAIL_SIDES), null);
            } else {
                Log.e(LT.IP, LT.UNIMPLEMENTED+"mMakeMediaDescriptor");
            }
        } catch (IOException e) {
            //do nothing finally does the job
        }
        return mMediaDiscriptionBuilder.setTitle(displayName)
                .setMediaId(mediaId).setMediaUri(uri).setIconBitmap(bitMapThumbNail).setExtras(extras)
                .build();
    }

    @Nullable
    private MediaBrowserCompat.MediaItem mMakeBrowsableMediaItem( @NonNull String mediaId )  {
        MediaDescriptionCompat mediaDescription = mMediaDiscriptionBuilder.setMediaId(mediaId).build();
        return new MediaBrowserCompat.MediaItem(mediaDescription, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    //exoplayer handles the audio focus , see (SimpleExoPlayer Instance).setAudioAttributes
    private void mRequestAudioFocus(){
        /**@see https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change**/
        Log.i(getClass().getCanonicalName() , LT.TEMP_IMPLEMENTATION+"mRequestAudioFocus");
        android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                /*check*/
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .setAllowedCapturePolicy(android.media.AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM)
                .build();
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int  apiLevel = Build.VERSION.SDK_INT;
        int requestResult = -1;
        if(apiLevel >= Build.VERSION_CODES.O){
            mAFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener((AudioManager.OnAudioFocusChangeListener)this)
                    .build();
            requestResult = audioManager.requestAudioFocus(mAFocusRequest);
            if(requestResult==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                Log.i(getClass().getCanonicalName() , LT.TEMP_IMPLEMENTATION+"AudioFocus change - granted");
            } else if(requestResult==AudioManager.AUDIOFOCUS_REQUEST_DELAYED){
                Log.i(getClass().getCanonicalName() , LT.TEMP_IMPLEMENTATION+"AudioFocus change - delayed");
            } else if(requestResult==AudioManager.AUDIOFOCUS_REQUEST_FAILED){
                Log.i(getClass().getCanonicalName() , LT.TEMP_IMPLEMENTATION+"AudioFocus change - Failed");
            }

        } else if((apiLevel >= Build.VERSION_CODES.LOLLIPOP) && (apiLevel < Build.VERSION_CODES.O)){
            //throw new RuntimeException("does support this Api level , audio focus hav to be implemented");
            requestResult = audioManager.requestAudioFocus(this,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);
        } else {
            throw new RuntimeException("does support this Api level , audio focus have to be implemented");
        }
    }

    @Override
    public void onDataChanged(List<MediaBrowserCompat.MediaItem> mediaItems, int changeType) {
        //TODO : use change type
        mAllPlayListMediaItem.clear();
        mAllPlayListMediaItem.addAll(mediaItems);
        notifyChildrenChanged(getString(R.string.id_local_playlists), new Bundle(0));
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.w(TAG, LT.UNIMPLEMENTED);
    }

    private boolean mIsStringEmpty(@Nullable String s){
        return s==null || s.isEmpty();
    }
}
