package com.example.mediasession;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
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
import androidx.core.app.NotificationManagerCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//TODO : add a loading implementation, keep the ui in sync with it

public class ServiceMediaPlayback extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener{
    private static final String TAG = "ServiceMediaPlayback";
    /* move all declared constants and make sure they dont conflict */

    /*must check - https://developer.android.com/about/versions/pie/power*/
    /*test it with malformated with audio file*/
    static final byte SERVICE_FAILED = -2;
    static final byte SERVICE_SUSPENDED = 1;
    /*this also  means that controllers are ready*/
    static final byte SERVICE_CONNECTED = 2;
    
    static final String KEY_FLAG_ERROR = "key_flag_error";

    /* MDEK - MediaDescription Extras Key */
    static final String MDEK_GENRE = "md_key_genre";
    static final String MDEK_ARTIST = "md_key_artist";
    static final String MDEK_DURATION = "md_key_duration";
    static final String MDEK_PLAYLIST_NAME = "md_key_playlist_name";
    static final String MDEK_PLAYLIST_DESCRIPTION = "md_key_playlist_discription";
    static final String MDEK_PLAYLIST_MEMBERS_URI = "md_key_playlist_members_uri";
    static final String MDEK_PLAYLIST_DISPLAY_PICTURE = "md_key_playlist_display_picture";

    /* custom actions */
    static final String ACTION_ADD_PLAYLIST = "1";
    static final String ACTION_PLAY_PLAYLIST = "2";
    static final String ACTION_REMOVE_PLAYLIST = "3";
    static final String ACTION_TOGGLE_PLAY_PAUSE = "4";
    static final String ACTION_REMOVE_QUEUE_ITEM = "5";
    static final String ACTION_ADD_PLAYLIST_MEMBER = "6";
    static final String ACTION_REMOVE_PLAYLIST_MEMBER = "7";
    static final String ACTION_INCLUDE_INTERNAL_STOARAGE = "8";

    /** CADK - Custom Action Data Key **/
    static final String CADK_QUEUE_ID = "01";
    static final String CADK_PLAYLIST_MEMBER_ID = "02";
    static final String CADK_PLAYLIST_ID = "03";
    static final String CADK_PLAYLIST_NAME = "04";
    static final String CADK_INCLUDE_INTERNAL_STORAGE_BOOLEAN = "05";

    /* Use conanical name to avoid conflicks */
    static final String EXTRAS_KEY_PLAYER_STATE = TAG+"el_3";
    static final String EXTRAS_KEY_HAS_NEXT = TAG+"ek_1";
    static final String EXTRAS_KEY_HAS_PREVIOUS = TAG+"ek_2";

    private ContentResolver mContentResolver;
    private ContentObserver mInternalContentObserver;
    private ContentObserver mExternalContentObserver;
    
    private final String[] PLAYLIST_MEMBER_SELECTION = new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID};

    private AudioFocusRequest mAFocusRequest;

    /*Browsable MediaItem ID*/
    //add better key
    static final String ROOT_ID_ALL_CONTENT = "rootid_all_content##"; //Make it Random   -client browse content ON
    
    @NonNull private String mParentIdSSA;
    @NonNull private String mParentIdPlaylists;

    //MediaSession Extras key
    static final String EXTRAS_KEY_PLAY_WHEN_READY = "playWhenReady";

    private static final String MEDIA_SESSION_DEBUGGER_ID = "MEDIA_SESSION_DEBUGGER";

    //Notification Channel IDs
    private final String CHANNEL_ID = "1";

    //Notification IDs
    private final int MEDIA_NOTIFICATION_ID = 1;

    //Pending Intent  Request Codes
    private final int MEDIA_NOTIFICATION_PENDING_INTENT_REQUEST_CODE = 1;

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManagerCompat mNotificationManger;
    private final int MAIN_NOTIFICATION_ID = 1;//TODO : remove
    private PendingIntent mMediaNotificationPendingIntent;
    private final int PENDING_INTENT_CODE_CONTENT = 2;   //Notification content intent

    private MediaSessionCompat mMediaSession;
    private final PlaybackStateCompat.Builder mSessionStateBuilder = new PlaybackStateCompat.Builder();
    @NonNull private final MediaMetadataCompat.Builder mSessionMetaDataBuilder = new MediaMetadataCompat.Builder();
    private MediaSessionCompat.Callback mSessionCallback;

    /*  Media Player  */
    private SimpleExoPlayer mPlayer ;
    private final MediaItem.Builder mExoMediaItemBuilder = new MediaItem.Builder();

    private PlayerNotificationManager mPlayerNotification ; //Notification

    private final MediaDescriptionCompat.Builder mMediaDiscriptionBuilder = new MediaDescriptionCompat.Builder();
    private final Bundle EXTRAS_BUNDLE = new Bundle();

    private MediaMetadataCompat mCurrentMetaData;   //only for mGetMetadataFrom

    private boolean mInForeground ;
    private boolean mLoadBrowsableMediaItemsCalled;

    @NonNull private final List<MediaBrowserCompat.MediaItem> mCollectionRootList = new ArrayList<>();

    // Data
    private Repositary mRepositary;
    
    /** cache for onLoadChildren **/
    @NonNull private final List<MediaBrowserCompat.MediaItem> mSharedStorageAudio = new ArrayList<>();
    /** key - String representation of the uri ; value - Corresponding playable MediaItem **/
    @NonNull private final HashMap<String, MediaBrowserCompat.MediaItem> mSharedStorageAudioMap = new HashMap<>();

    /** PlayList **/
    @NonNull private final List<MediaBrowserCompat.MediaItem> mPlaylists = new ArrayList<>();
    @NonNull private final HashMap<String, List<MediaBrowserCompat.MediaItem>> mPlaylistMembersMap = new HashMap<>();

    private boolean mReciveTransportControlls;
    
    private Repositary.Observer mSSMObserver;
    private Repositary.Observer mPlaylistsObserver;


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
            return null;
        }
    }

    // Warning : MediaItems should not contain icon bitmaps
    // Note : returning null means request is rejected
    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        //TODO : add result.detach, result.sendError, async stuff
        /*  TODO : change code which loads data on request  */
        if(parentId.equals(mParentIdSSA)){
            result.sendResult(mSharedStorageAudio);
        } else if(parentId.equals(mParentIdPlaylists)) {
            result.sendResult(mPlaylists);
        } else if(mPlaylistMembersMap.containsKey(parentId)){
            result.sendResult(mPlaylistMembersMap.get(parentId));
        } else {
            result.sendResult(null);//Invalid parentId
        }
    }


    // ____________________________ Service Callbacks ____________________________

    @Override
    public void onCreate() {
        super.onCreate();
        
        mParentIdSSA = getString(R.string.id_local_media);
        mParentIdPlaylists = getString(R.string.id_local_playlists);

        //_________ Loading DATA _________

        mRepositary = new Repositary(getApplicationContext());
        mSSMObserver = new Repositary.Observer(){
            @Override
            public void onDataChanged(List<MediaBrowserCompat.MediaItem> mediaItems){
                mSharedStorageAudioMap.clear();
                mSharedStorageAudio.clear();
                if(mediaItems == null){
                    final Bundle extras = new Bundle(1);
                    extras.putBoolean(KEY_FLAG_ERROR, true);
                    notifyChildrenChanged(mParentIdSSA, extras);
                    return;
                }
                mSharedStorageAudio.addAll(mediaItems);
                for(MediaBrowserCompat.MediaItem mediaItem : mediaItems){
                    mSharedStorageAudioMap.put(mediaItem.getMediaId(), mediaItem);
                }
                notifyChildrenChanged(mParentIdSSA, Bundle.EMPTY);
            }
        };
        mRepositary.setExternalSSAObserver(mSSMObserver);
        
        //TODO : optionally load internal shared storage media

        mPlaylistsObserver = new Repositary.Observer(){
            @Override
            public void onDataChanged(List<MediaBrowserCompat.MediaItem> mediaItems){
                //check previous implementation before writing
                mPlaylists.clear();
                if(mediaItems == null){
                    final Bundle extras = new Bundle(1);
                    extras.putBoolean(KEY_FLAG_ERROR, true);
                    notifyChildrenChanged(mParentIdPlaylists, extras);
                    return;
                }
                mPlaylists.addAll(mediaItems);
                mLoadPlaylistMembers();
                notifyChildrenChanged(mParentIdPlaylists, Bundle.EMPTY);
            }
        };
        mRepositary.setPlaylistsObserver(mPlaylistsObserver);

        //TODO : Temp solution to the bug that crashes the app if playlist_item play button clicked as soon as the app starts
        mCurrentMetaData = mSessionMetaDataBuilder.build();


        // ________________________________

        // Create the NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.media_notification_channel_name);
            String description = getString(R.string.media_notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_MAX;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        mNotificationManger = NotificationManagerCompat.from(this);
        mNotificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mMediaNotificationPendingIntent = PendingIntent
                .getActivity(this, MEDIA_NOTIFICATION_PENDING_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);


        // ____________ MediaSession _______________
        mMediaSession = new MediaSessionCompat(this, MEDIA_SESSION_DEBUGGER_ID);
        mMediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onCommand(String command, Bundle extras, ResultReceiver cb) {
                super.onCommand(command, extras, cb);
            }

            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                MediaBrowserCompat.MediaItem mediaItem = mSharedStorageAudioMap.get((String) mediaId);

                if(mediaItem == null){ /* TODO : notify user error */ return; }
                if(mediaItem.isPlayable()){
                    mPlayer.clearMediaItems();
                    MediaItem exoMediaItem = mExoMediaItemBuilder
                            .setUri(mediaItem.getDescription().getMediaUri())
                            .setMediaId(mediaItem.getMediaId()).build();
                    mPlayer.setMediaItem(exoMediaItem);
                    mPlayer.prepare();
                    mPlayer.setPlayWhenReady(true);
                } else {
                    Log.w(LT.IP , "onPlayFromMediaId requested mediaItem is browsable");
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
        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state){
                    case Player.STATE_IDLE: {
                        mReciveTransportControlls = false; mPlayer.setPlayWhenReady(false);

                        mSessionStateBuilder.setState(PlaybackStateCompat.STATE_NONE, 0,
                                mPlayer.getPlaybackParameters().speed);
                        mMediaSession.setPlaybackState(mSessionStateBuilder.build());
                        break;
                    }
                    case Player.STATE_BUFFERING: {
                        mSessionStateBuilder.setState(PlaybackStateCompat.STATE_BUFFERING,
                                mPlayer.getCurrentPosition(), mPlayer.getPlaybackParameters().speed);
                        mMediaSession.setPlaybackState(mSessionStateBuilder.build());
                        break;
                    }
                    case Player.STATE_READY: {
                        int playbackState = PlaybackStateCompat.STATE_NONE;
                        if(mPlayer.isPlaying()) playbackState  = PlaybackStateCompat.STATE_PLAYING;
                        else playbackState  = PlaybackStateCompat.STATE_PAUSED;
                        mSessionStateBuilder.setState(playbackState,
                                mPlayer.getCurrentPosition(), mPlayer.getPlaybackParameters().speed);
                        mMediaSession.setPlaybackState(mSessionStateBuilder.build());
                        break;
                    }
                    case Player.STATE_ENDED: {
                        mPlayer.setPlayWhenReady(false);
                        try{
                            mPlayer.seekTo(0, C.TIME_UNSET); mPlayer.prepare();
                        } catch (IllegalSeekPositionException e){
                            mSessionStateBuilder.setState(PlaybackStateCompat.STATE_STOPPED,
                                    0, mPlayer.getPlaybackParameters().speed);
                            mMediaSession.setPlaybackState(mSessionStateBuilder.build());
                        }
                        break;
                    }
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                //if(playWhenReady) mPlayer.prepare();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                int playbackState = PlaybackStateCompat.STATE_NONE;
                long position = 0;
                if(isPlaying){
                    playbackState = PlaybackStateCompat.STATE_PLAYING;
                    position = mPlayer.getCurrentPosition();
                } else {
                    switch (mPlayer.getPlaybackState()){
                        case(Player.STATE_READY):{
                            playbackState = PlaybackStateCompat.STATE_PAUSED;
                            position = mPlayer.getCurrentPosition();
                            break;
                        }
                        case(Player.STATE_BUFFERING):{
                            playbackState = PlaybackStateCompat.STATE_BUFFERING;
                            position = mPlayer.getCurrentPosition();
                            break;
                        }
                        case(Player.STATE_ENDED):{
                            playbackState = PlaybackStateCompat.STATE_STOPPED;
                            break;
                        }
                    }
                }
                mSessionStateBuilder.setState(playbackState, position, mPlayer.getPlaybackParameters().speed);
                mMediaSession.setPlaybackState(mSessionStateBuilder.build());
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e(LT.IP, "onPlayerError");
                Toast.makeText(ServiceMediaPlayback.this, "Error!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPositionDiscontinuity(int reason) {

            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                //TODO : test
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED){
                    if(timeline.isEmpty()){
                        mReciveTransportControlls = false;
                        mMediaSession.setQueue(new ArrayList<>(0));
                        mUpdateTransportControllsData(true);

                        mNotificationBuilder
                                .setSmallIcon(R.drawable.ic_default_albumart_thumb)
                                .setContentTitle("Empty Queue")
                                .setContentText("")
                                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                                .setPriority(NotificationCompat.PRIORITY_MAX)
                                .setContentIntent(mMediaNotificationPendingIntent)
                                .setAutoCancel(false);

                        mNotificationManger.notify(MEDIA_NOTIFICATION_ID, mNotificationBuilder.build());
                        return;
                    }
                    mReciveTransportControlls = true;

                    mUpdateTransportControllsData(false);

                    int windowCount = timeline.getWindowCount();
                    List<MediaSessionCompat.QueueItem> queue = new ArrayList<>(windowCount);
                    //TODO : manage queue id properly
                    for(int i=0; i<windowCount; i++){
                        Timeline.Window window = timeline.getWindow(i, new Timeline.Window());
                        MediaBrowserCompat.MediaItem mediaItem =
                                mSharedStorageAudioMap.get(window.mediaItem.mediaId);
                        if(mediaItem != null)
                            queue.add(new MediaSessionCompat.QueueItem(mediaItem.getDescription(), i));
                        else Log.e(TAG, "onTimelineChanged ,mediaItem doesn't exist on map");
                    }
                    mMediaSession.setQueue(queue);

                    MediaDescriptionCompat md = null;
                    MediaItem mediaItem = mPlayer.getCurrentMediaItem();
                    if(mediaItem != null){
                        MediaBrowserCompat.MediaItem item = mSharedStorageAudioMap.get(mediaItem.mediaId);
                        if(item != null) md = item.getDescription();
                    }

                    androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
                            new androidx.media.app.NotificationCompat.MediaStyle();
                    mediaStyle.setMediaSession(mMediaSession.getSessionToken());
                    mNotificationBuilder
                            .setSmallIcon(R.drawable.ic_default_albumart_thumb)
                            .setContentTitle(md.getTitle())
                            .setContentText(md.getExtras().getString(MDEK_ARTIST, ""))
                            .setStyle(mediaStyle)
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setContentIntent(mMediaNotificationPendingIntent).set
                            .setAutoCancel(false);

                    mNotificationManger.notify(MEDIA_NOTIFICATION_ID, mNotificationBuilder.build());
                } else if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE){
                    //TODO
                } else {
                    //TODO
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                //update current queue item mPlayer.getCurrentWindowIndex();

                //TODO : check if metadata is updated preperly
                if (mediaItem != null) {
                    mUpdateTransportControllsData(false);

                    MediaBrowserCompat.MediaItem mediaItem1 =
                            mSharedStorageAudioMap.get(mediaItem.mediaId);
                    final MediaDescriptionCompat md = mediaItem1.getDescription();
                    int albumArtSize = getResources().getDimensionPixelSize(R.dimen.fragment_player_albumart_sides);
                    final Uri albumArtUri = md.getMediaUri(); Bitmap albumArt = null;
                    try{
                        albumArt = getContentResolver().loadThumbnail(albumArtUri,
                                new Size(albumArtSize, albumArtSize), null);
                    } catch (IOException e){
                        Log.e(TAG, "IOException failed to load AlbumArt");
                    }
                    if(albumArt == null) {
                        //TODO
                    }
                    mSessionMetaDataBuilder
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                                    (String) md.getTitle())
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                                    md.getExtras().getString(MediaStore.Audio.Media.ARTIST))
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
                } else {
                    mUpdateTransportControllsData(true);

                    mSessionMetaDataBuilder
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, null)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, null)
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
                }
                mCurrentMetaData = mSessionMetaDataBuilder.build();
                mMediaSession.setMetadata(mCurrentMetaData);
            }
        });
        //_____________________

        //Default Notification(Media Not Playing)
        mNotificationBuilder
                .setSmallIcon(R.drawable.ic_default_albumart_thumb)
                .setContentTitle("Ready To Play")
                .setContentText("")
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(mMediaNotificationPendingIntent)
                .setAutoCancel(false);

        mNotificationManger.notify(MEDIA_NOTIFICATION_ID, mNotificationBuilder.build());

        //Activiting MediaSession
        mMediaSession.setActive(true);  //Must be active to recive callbacks
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent , flags , startId);
        MediaButtonReceiver.handleIntent(mMediaSession, intent);
        return START_STICKY;
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
        //mRepositary.unsubscribePlaylistData(this::onDataChanged);
        mRepositary.setPlaylistsObserver(null);
        mRepositary.setInternalSSAObserver(null);
        mRepositary.setExternalSSAObserver(null);
        mRepositary.clear();

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(mAFocusRequest!=null) audioManager.abandonAudioFocusRequest(mAFocusRequest);

        Log.d("ServiceMediaPlayback" , "onDestroy called");
        if(mInternalContentObserver != null)
            mContentResolver.unregisterContentObserver(mInternalContentObserver);
        if(mExternalContentObserver != null)
            mContentResolver.unregisterContentObserver(mExternalContentObserver);

        /*if (mPlayerNotification != null) mPlayerNotification.setPlayer(null);
        mPl*/

        //Notifications
        mNotificationManger.cancel(MEDIA_NOTIFICATION_ID);
        if(mMediaNotificationPendingIntent != null) {
            mMediaNotificationPendingIntent.cancel(); mMediaNotificationPendingIntent = null;
        }
        mNotificationBuilder = null; mNotificationManger = null;

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
            case ACTION_TOGGLE_PLAY_PAUSE: {
                if(mPlayer.isPlaying()){ mPlayer.pause(); result.sendResult(null); return; }
                if(mPlayer.getPlaybackState() == Player.STATE_READY){
                    mPlayer.play(); result.sendResult(null);
                } else {
                    //TODO : check and handle
                    result.sendError(null);
                }
                return;
            }
            case ACTION_INCLUDE_INTERNAL_STOARAGE: {
                boolean include = extras.getBoolean(CADK_INCLUDE_INTERNAL_STORAGE_BOOLEAN, false);
                //TODO - IMP : try observing the shared preference ,if can remove this from custom action
                result.sendError(null);
                return;
            }
            case ACTION_REMOVE_QUEUE_ITEM: {
                final int queueId = (int) extras.getInt(CADK_QUEUE_ID, -2);
                try {
                    mPlayer.removeMediaItem(queueId);
                } catch (Exception e) {
                    Log.w(LT.IP, LT.UNIMPLEMENTED + "onCustomAction cant remove queue item");
                    result.sendError(null);
                    return;
                }
                result.sendResult(null);
                return;
            }
            case ACTION_PLAY_PLAYLIST : {
                final String playlistId = extras.getString(CADK_PLAYLIST_ID, null);
                if(playlistId == null){
                    //TODO : notify error
                    result.sendError(null);
                    return;
                }

                List<MediaBrowserCompat.MediaItem> mediaItems = null;
                try {
                    mediaItems = mPlaylistMembersMap.get(playlistId);
                } catch (Exception e) { /* do nothing */ }

                if(mediaItems != null) {
                    result.sendResult(null);
                    mPlayer.setPlayWhenReady(false);
                    mPlayer.clearMediaItems();
                    for(MediaBrowserCompat.MediaItem item : mediaItems) {
                        MediaItem exoMediaItem = mExoMediaItemBuilder
                                .setUri(item.getDescription().getMediaUri())
                                .setMediaId(item.getMediaId()).build();
                        mPlayer.addMediaItem(exoMediaItem);
                    }
                    mPlayer.prepare();
                    mPlayer.setPlayWhenReady(true);
                } else {
                    //TODO : notify error
                    result.sendError(null);
                }

                return;
            }

            case ACTION_ADD_PLAYLIST: {
                final String addPlaylistName = extras.getString(CADK_PLAYLIST_NAME);
                mRepositary.addPlaylists((addPlaylistName == null ? "" : addPlaylistName),
                        null, null, result);
                return;
            }

            case ACTION_REMOVE_PLAYLIST: {
                final int removePlaylistId = extras.getInt(CADK_PLAYLIST_ID, -2);
                mRepositary.removePlaylist(removePlaylistId, result);
                return;
            }
            case ACTION_ADD_PLAYLIST_MEMBER: {
                int playlistId = extras.getInt(CADK_PLAYLIST_ID, -2);
                String memberId = extras.getString(CADK_PLAYLIST_MEMBER_ID, "");
                mRepositary.addPlaylistMember(playlistId, memberId, result);
                return;
            }
            case ACTION_REMOVE_PLAYLIST_MEMBER: {
                int playlistId = extras.getInt(CADK_PLAYLIST_ID, -2);
                String memberId = extras.getString(CADK_PLAYLIST_MEMBER_ID, "");
                mRepositary.removePlaylistMember(playlistId, memberId, result);
                return;
            }
            default: super.onCustomAction(action, extras, result);
        }
    }

    // _____________________________________________________________________________

    @Nullable
    private MediaBrowserCompat.MediaItem mMakeBrowsableMediaItem( @NonNull String mediaId )  {
        MediaDescriptionCompat mediaDescription = mMediaDiscriptionBuilder.setMediaId(mediaId).build();
        return new MediaBrowserCompat.MediaItem(mediaDescription, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }
    
    private void mLoadPlaylistMembers(){
        mPlaylistMembersMap.clear();
    
        if(mPlaylists == null){
            //TODO : check and send error
            return;
        }
        
        for(MediaBrowserCompat.MediaItem playlist : mPlaylists){
            List<String> membersUriList = playlist.getDescription().getExtras()
                    .getStringArrayList(ServiceMediaPlayback.MDEK_PLAYLIST_MEMBERS_URI);
                    
            membersUriList = (membersUriList == null) ? new ArrayList<>(0) : membersUriList;
            
            final List<MediaBrowserCompat.MediaItem> members = new ArrayList<>();
            //TODO : optimize
            for(String s : membersUriList){
                MediaBrowserCompat.MediaItem member = null;
                try{
                    member = mSharedStorageAudioMap.get(s);
                } catch(Exception e){ /* do nothing */ }
                if(member != null){
                    members.add(member);
                } else {
                    /* TODO : add a mediaitem to notify user that this
                        element is deleted, instead of skipping this element */
                    continue;
                }
            }
            String mediaId = playlist.getMediaId();
            //TODO : check what is mediaId doesnt exist in notifyChildrenChanged
            mPlaylistMembersMap.put(mediaId, members);
            Log.d(TAG, "a"+members.toString());
            notifyChildrenChanged(mediaId, Bundle.EMPTY);
        }
    }

    private void mUpdateTransportControllsData(boolean clear){
        //TODO : include notification
        if(clear){
            EXTRAS_BUNDLE.putBoolean(EXTRAS_KEY_HAS_NEXT, false);
            EXTRAS_BUNDLE.putBoolean(EXTRAS_KEY_HAS_PREVIOUS, false);
            mMediaSession.setExtras(EXTRAS_BUNDLE);
            return;
        }
        int currentWindowIndex = mPlayer.getCurrentWindowIndex();
        int lastWindowIndex = mPlayer.getCurrentTimeline().getWindowCount() - 1;
                    /*Log.d(TAG, "currentIndex:"+Integer.toString(currentWindowIndex)
                            +" lastIndex:"+Integer.toString(lastWindowIndex));*/
        EXTRAS_BUNDLE.putBoolean(EXTRAS_KEY_HAS_NEXT,
                (currentWindowIndex > -1 && currentWindowIndex < lastWindowIndex) ? true : false);
        EXTRAS_BUNDLE.putBoolean(EXTRAS_KEY_HAS_PREVIOUS,
                currentWindowIndex > 0 ? true : false);
        mMediaSession.setExtras(EXTRAS_BUNDLE);
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

    /*@Override
    public void onDataChanged(List<MediaBrowserCompat.MediaItem> mediaItems, int changeType) {
        //TODO : use change type
        mAllPlayListMediaItem.clear();
        mAllPlayListMediaItem.addAll(mediaItems);
        notifyChildrenChanged(mParentIdPlaylists, Bundle.EMPTY);
    }*/

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.w(TAG, LT.UNIMPLEMENTED);
    }
    
    /* util methods */
    private boolean mIsStringEmpty(@Nullable String s){
        return s==null || s.isEmpty();
    }
}
