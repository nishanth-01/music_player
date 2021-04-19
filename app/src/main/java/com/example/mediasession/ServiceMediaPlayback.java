package com.example.mediasession;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.service.notification.StatusBarNotification;
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
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//TODO : add a loading implementation, keep the ui in sync with it

public class ServiceMediaPlayback extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener{
    private static final String TAG = ServiceMediaPlayback.class.getCanonicalName();
    /* TODO : move all declared constants and make sure they dont conflict */

    /*must check - https://developer.android.com/about/versions/pie/power*/
    /*test it with malformated with audio file*/
    static final byte SERVICE_FAILED    = -2;
    static final byte SERVICE_SUSPENDED = 1;
    /** this also  means that controllers are ready **/
    static final byte SERVICE_CONNECTED = 2;
    
    static final String KEY_FLAG_ERROR = "key_flag_error";

    /* MDEK - MediaDescription Extras Key */
    static final String MDEK_GENRE                      = "md_key_genre";
    static final String MDEK_ARTIST                     = "md_key_artist";
    static final String MDEK_DURATION                   = "md_key_duration";
    static final String MDEK_PLAYLIST_NAME              = "md_key_playlist_name";
    static final String MDEK_PLAYLIST_DESCRIPTION       = "md_key_playlist_discription";
    static final String MDEK_PLAYLIST_MEMBERS_URI       = "md_key_playlist_members_uri";
    static final String MDEK_PLAYLIST_DISPLAY_PICTURE   = "md_key_playlist_display_picture";

    /* custom actions */
    static final String ACTION_ADD_PLAYLIST                 = "1";
    static final String ACTION_PLAY_PLAYLIST                = "2";
    static final String ACTION_REMOVE_PLAYLIST              = "3";
    static final String ACTION_TOGGLE_PLAY_PAUSE            = "4";
    static final String ACTION_REMOVE_QUEUE_ITEM            = "5";
    static final String ACTION_ADD_PLAYLIST_MEMBER          = "6";
    static final String ACTION_REMOVE_PLAYLIST_MEMBER       = "7";
    static final String ACTION_INCLUDE_INTERNAL_STOARAGE    = "8";

    /** CADK - Custom Action Data Key **/
    static final String CADK_QUEUE_ID                           = "01";
    static final String CADK_PLAYLIST_MEMBER_ID                 = "02";
    static final String CADK_PLAYLIST_ID                        = "03";
    static final String CADK_PLAYLIST_NAME                      = "04";
    static final String CADK_INCLUDE_INTERNAL_STORAGE_BOOLEAN   = "05";

    //Notification ACTIONS
    private static final String INTENT_ACTION_PLAY_PREVIOUS = "0001";
    private static final String INTENT_ACTION_PLAY_PAUSE    = "0002";
    private static final String INTENT_ACTION_PLAY_NEXT     = "0003";

    private static final int PENDING_INTENT_REQUEST_CODE_MEDIA_NOTIFICATION_CONTENT = 1;
    private static final int PENDING_INTENT_REQUEST_CODE_PLAY_PREVIOUS              = 2;
    private static final int PENDING_INTENT_REQUEST_CODE_PLAY_PAUSE                 = 3;
    private static final int PENDING_INTENT_REQUEST_CODE_PLAY_NEXT                  = 4;

    /* keys of MediaSession extras bundle and its nested bundles */
    /* Use package name to avoid conflicks */
    static final String EXTRAS_KEY_TRANSPORTS_CONTROLS_BUNDLE       = TAG+".ek_1";
    static final String TRANSPORTS_CONTROLS_BUNDLE_KEY_HAS_NEXT     = TAG+".tcbk_2";
    static final String TRANSPORTS_CONTROLS_BUNDLE_KEY_HAS_PREVIOUS = TAG+".tcbk_3";
    static final String TRANSPORTS_CONTROLS_BUNDLE_KEY_CAN_PLAY     = TAG+".tcbk_4";

    // Effectively constant
    private int MAIN_PLAYER_ALBUM_ART_SIDES;

    private AudioFocusRequest mAFocusRequest;

    /*Browsable MediaItem ID*/
    //add better key
    static final String ROOT_ID_ALL_CONTENT = "rootid_all_content##"; //Make it Random   -client browse content ON

    private static final String MEDIA_SESSION_DEBUGGER_ID = "MEDIA_SESSION_DEBUG";

    //Notification Channel IDs
    private final String MEDIA_NOTIFICATION_CHANNEL_ID = "1";

    //Notification IDs
    private final int MEDIA_NOTIFICATION_ID = 1;

    @NonNull private String mParentIdSSA;
    @NonNull private String mParentIdPlaylists;

    private ContentResolver mContentResolver;
    private ContentObserver mInternalContentObserver;
    private ContentObserver mExternalContentObserver;

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManagerCompat mNotificationMangerCompat;
    private NotificationManager mNotificationManger;
    private androidx.media.app.NotificationCompat.MediaStyle mMediaStyle;
    private PendingIntent mMediaNotificationContentPendingIntent;
    private PendingIntent mPlayPreviousPendingIntent;
    private PendingIntent mPlayNextPendingIntent;
    private PendingIntent mPlayPausePendingIntent;

    private MediaSessionCompat mMediaSession;
    private MediaSessionCompat.Callback mMediaSessionCallback;
    private final PlaybackStateCompat.Builder mSessionStateBuilder = new PlaybackStateCompat.Builder();
    private MediaMetadataCompat.Builder mSessionMetaDataBuilder;
    private MediaSessionCompat.Callback mSessionCallback;

    /*  Media Player  */
    private SimpleExoPlayer mPlayer ;
    private Player.EventListener mPlayerEventListener;
    private final MediaItem.Builder mExoMediaItemBuilder = new MediaItem.Builder();

    private PlayerNotificationManager mPlayerNotification ; //Notification

    private final MediaDescriptionCompat.Builder mMediaDiscriptionBuilder = new MediaDescriptionCompat.Builder();
    private final Bundle EXTRAS_BUNDLE = new Bundle();

    private MediaMetadataCompat mCurrentMetaData;   //only for mGetMetadataFrom
    private Bitmap mDefMusicBitmap;

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

    private boolean mReciveTransportControlls;//FIXME : use this properly
    
    private Repositary.Observer mSSMObserver;
    private Repositary.Observer mPlaylistsObserver;

    /* TODO make sure this variables updated properly */
    private boolean mCanPlay;
    private boolean mCanPlayNext;
    private boolean mCanPlayPrevious;


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
        MAIN_PLAYER_ALBUM_ART_SIDES = getResources()
                .getDimensionPixelSize(R.dimen.fragment_player_albumart_sides);

        mNotificationManger = getSystemService(NotificationManager.class);
        mNotificationMangerCompat = NotificationManagerCompat.from(this);
        mNotificationBuilder = new NotificationCompat.Builder(this, MEDIA_NOTIFICATION_CHANNEL_ID);

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

        // ________________________________

        // ____________ MediaSession _______________
        mMediaSession = new MediaSessionCompat(this, MEDIA_SESSION_DEBUGGER_ID);
        mMediaSessionCallback = new MediaSessionCompat.Callback() {
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
                    Toast.makeText(ServiceMediaPlayback.this, "Operation Failed", Toast.LENGTH_SHORT).show();
                    //TODO : notify invalid seek position
                }
            }

            @Override
            public void onSkipToNext() {  mPlayer.next(); }

            @Override
            public void onSkipToPrevious() { mPlayer.previous(); }
        };
        mMediaSession.setCallback(mMediaSessionCallback);

        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

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
        mPlayerEventListener = new Player.EventListener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                mOnPlaybackStateChanged(state);
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                //if(playWhenReady) mPlayer.prepare();
            }

            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                //TODO : fill in the blanks

                //Temp solution for notification seekbar
                if(!isLoading) mUpdateMetadata(mPlayer.getCurrentMediaItem());
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                //TODO : temp solution
                mOnPlaybackStateChanged(mPlayer.getPlaybackState());

                //Notifications
                if(!mNotificationActive(MEDIA_NOTIFICATION_ID)) return;


                mNotificationBuilder.clearActions();
                mNotificationBuilder
                        .addAction(new NotificationCompat.Action(
                                R.drawable.exo_controls_previous,
                                "Play Previous",
                                mPlayPreviousPendingIntent))
                        .addAction(new NotificationCompat.Action(
                                (isPlaying) ? R.drawable.exo_controls_pause : R.drawable.exo_controls_play,
                                "Play-Pause Toggle",
                                mPlayPausePendingIntent))
                        .addAction(new NotificationCompat.Action(
                                R.drawable.exo_controls_next,
                                "Play Next",
                                mPlayNextPendingIntent))
                        .setStyle(mMediaStyle);
                mNotificationMangerCompat.notify(MEDIA_NOTIFICATION_ID, mNotificationBuilder.build());
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e(LT.IP, "onPlayerError");
                Toast.makeText(ServiceMediaPlayback.this, "Error!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPositionDiscontinuity(int reason) {
                //TODO : fill in the blank
            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {//TODO : does it gets called when mediaItem moves
                //TODO : test

                if(timeline.isEmpty()){
                    mReciveTransportControlls = false; mMediaSession.setQueue(new ArrayList<>(0));

                    //TODO : check what if this called when service is not in foreground
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    return;
                }
                startForeground(
                        MEDIA_NOTIFICATION_ID,
                        mNotificationBuilder.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                );

                mUpdateTransportsControls(timeline);

                mReciveTransportControlls = true;

                //Loading queue
                final int windowCount = timeline.getWindowCount();
                final List<MediaSessionCompat.QueueItem> queue = new ArrayList<>(windowCount);
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
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                mUpdateMetadata(mediaItem);
                mUpdateTransportsControls(mPlayer.getCurrentTimeline());
            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
                mOnPlaybackStateChanged(mPlayer.getPlaybackState());
            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                Toast.makeText(ServiceMediaPlayback.this, "onTracksChanged", Toast.LENGTH_SHORT).show();
                //TODO : what is this about?
            }
        };
        // - - - - - - - - - -

        //Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.media_notification_channel_name);
            String description = getString(R.string.media_notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(MEDIA_NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description); channel.setSound(null, null);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            mNotificationManger.createNotificationChannel(channel);
        }

        Context applicationContext = getApplicationContext();
        Intent contentIntent = new Intent(applicationContext, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mMediaNotificationContentPendingIntent = PendingIntent.getActivity(this,
                PENDING_INTENT_REQUEST_CODE_MEDIA_NOTIFICATION_CONTENT,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        mPlayPreviousPendingIntent = PendingIntent.getService(this,
                PENDING_INTENT_REQUEST_CODE_PLAY_PREVIOUS,
                new Intent(INTENT_ACTION_PLAY_PREVIOUS, null, applicationContext, ServiceMediaPlayback.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        mPlayNextPendingIntent = PendingIntent.getService(this,
                PENDING_INTENT_REQUEST_CODE_PLAY_NEXT,
                new Intent(INTENT_ACTION_PLAY_NEXT, null, applicationContext, ServiceMediaPlayback.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        mPlayPausePendingIntent = PendingIntent.getService(this,
                PENDING_INTENT_REQUEST_CODE_PLAY_PAUSE,
                new Intent(INTENT_ACTION_PLAY_PAUSE, null, applicationContext, ServiceMediaPlayback.class),
                PendingIntent.FLAG_UPDATE_CURRENT);

        //Notification default values
        mMediaStyle = new androidx.media.app.NotificationCompat.MediaStyle();
        mMediaStyle.setMediaSession(mMediaSession.getSessionToken());
        mMediaStyle.setShowActionsInCompactView(0, 1, 2);
        mNotificationBuilder
                /*TODO : .setLargeIcon() */
                .setSmallIcon(R.drawable.ic_default_albumart_thumb)//replace with proper icon
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setChannelId(MEDIA_NOTIFICATION_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(null)
                .setContentIntent(mMediaNotificationContentPendingIntent)
                .setAutoCancel(false);

        // - - - - - - - - - -

        mPlayer.addListener(mPlayerEventListener);

        //Activiting MediaSession
        mMediaSession.setActive(true);  //Must be active to recive callbacks
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Handling Media Notification buttons
        switch(intent.getAction()){
            case INTENT_ACTION_PLAY_NEXT : {
                mPlayer.next();
                break;
            }
            case INTENT_ACTION_PLAY_PREVIOUS : {
                mPlayer.previous();
                break;
            }
            case INTENT_ACTION_PLAY_PAUSE : {
                final int playerState = mPlayer.getPlaybackState();
                if(mPlayer.isPlaying()) {
                    mPlayer.pause();
                } else if(playerState == Player.STATE_READY || playerState == Player.STATE_BUFFERING){
                    mPlayer.play();
                } else ;//TODO : fill in the blank
                break;
            }
        }

        MediaButtonReceiver.handleIntent(mMediaSession, intent);
        return super.onStartCommand(intent , flags , startId);
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
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(mAFocusRequest != null) audioManager.abandonAudioFocusRequest(mAFocusRequest);

        //Data
        if(mInternalContentObserver != null)
            mContentResolver.unregisterContentObserver(mInternalContentObserver);
        if(mExternalContentObserver != null)
            mContentResolver.unregisterContentObserver(mExternalContentObserver);

        //Player
        if(mPlayer != null) {
            mPlayer.setHandleAudioBecomingNoisy(false); mPlayer.setForegroundMode(false);
            mPlayer.clearMediaItems();//this calls onTimelineChanged with empty timeline which stops foreground service
            mPlayer.removeListener(mPlayerEventListener);
            mPlayer.release(); mPlayer = null;
        }

        //Notifications
        if(mNotificationActive(MEDIA_NOTIFICATION_ID)) mNotificationMangerCompat.cancel(MEDIA_NOTIFICATION_ID);
        mMediaNotificationContentPendingIntent.cancel(); mMediaNotificationContentPendingIntent = null;
        mPlayNextPendingIntent.cancel(); mPlayNextPendingIntent = null;
        mPlayPreviousPendingIntent.cancel(); mPlayPreviousPendingIntent = null;
        mPlayPausePendingIntent.cancel(); mPlayPausePendingIntent = null;

        mNotificationBuilder = null; mNotificationMangerCompat = null; mNotificationManger = null;

        mMediaSession.setActive(false); mMediaSession.release(); mMediaSession = null;

        if(mDefMusicBitmap != null){
            mDefMusicBitmap.recycle(); mDefMusicBitmap = null;
        }
    }

    @Override
    public void onCustomAction(@NonNull String action, Bundle extras, @NonNull Result<Bundle> result) {
        switch (action){
            case ACTION_TOGGLE_PLAY_PAUSE: {
                final int playerState = mPlayer.getPlaybackState();
                if(mPlayer.isPlaying()){ mPlayer.pause(); result.sendResult(null); }
                else if(playerState == Player.STATE_READY || playerState == Player.STATE_BUFFERING){
                    mPlayer.play(); result.sendResult(null);
                } else {
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
                final int queueId = extras.getInt(CADK_QUEUE_ID, -2);
                try {
                    mPlayer.removeMediaItem(queueId);
                } catch (Exception e) {
                    Log.e(TAG, "onCustomAction code:"+ACTION_REMOVE_QUEUE_ITEM+" cant remove queue item");
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
                    if(mediaItems.isEmpty()){
                        //TODO : fill in the blank
                        result.sendResult(null);
                    } else {
                        mPlayer.setPlayWhenReady(false);
                        mPlayer.clearMediaItems();
                        List<MediaItem> exoMediaItemList = new ArrayList<>(mediaItems.size());
                        for(MediaBrowserCompat.MediaItem item : mediaItems) {
                            exoMediaItemList.add(
                                    mExoMediaItemBuilder
                                            .setUri(item.getDescription().getMediaUri())
                                            .setMediaId(item.getMediaId()).build()
                            );
                        }
                        mPlayer.addMediaItems(exoMediaItemList);
                        mPlayer.prepare();
                        mPlayer.setPlayWhenReady(true);
                        result.sendResult(null);
                    }
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

    //TODO : fix this method gets called multiple times unnecessarily
    private void mOnPlaybackStateChanged(int state){
        //TODO : also handle other PlaybackStateCompat.STATE_ etc..
        //mUpdateTransportControlsState();
        final float playbackSpeed = mPlayer.getPlaybackParameters().speed;
        switch (state){
            case Player.STATE_IDLE: {
                mReciveTransportControlls = false; mPlayer.setPlayWhenReady(false);

                mUpdatePlaybackState(PlaybackStateCompat.STATE_NONE,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, playbackSpeed);
                break;
            }
            case Player.STATE_BUFFERING: {
                mUpdatePlaybackState(PlaybackStateCompat.STATE_BUFFERING,
                        mPlayer.getContentPosition(), playbackSpeed);
                break;
            }
            case Player.STATE_READY: {
                int playbackState = PlaybackStateCompat.STATE_NONE;
                if(mPlayer.isPlaying()) playbackState  = PlaybackStateCompat.STATE_PLAYING;
                else playbackState  = PlaybackStateCompat.STATE_PAUSED;

                mUpdatePlaybackState(playbackState, mPlayer.getContentPosition(), playbackSpeed);
                break;
            }
            case Player.STATE_ENDED: {
                //reloading playlist
                mPlayer.setPlayWhenReady(false);
                if(!mPlayer.getCurrentTimeline().isEmpty()) {
                    mPlayer.seekTo(0, C.TIME_UNSET); mPlayer.prepare();
                } //else do nothing
                break;
            }
        }
    }

    /** all media session state update should go through this **/
    private void mUpdatePlaybackState(int state, long position, float playbackSpeed){
        mSessionStateBuilder.setState(state, position, playbackSpeed);
        mMediaSession.setPlaybackState(mSessionStateBuilder.build());
    }

    /** only for Player.onMediaItemTransition, Player.onTimelineChange **/
    //TODO : add syncronization
    private void mUpdateTransportsControls(@NonNull Timeline timeline){
        final Bundle b = new Bundle(3);
        b.putBoolean(TRANSPORTS_CONTROLS_BUNDLE_KEY_HAS_NEXT, mPlayer.hasNext());
        b.putBoolean(TRANSPORTS_CONTROLS_BUNDLE_KEY_HAS_PREVIOUS, mPlayer.hasPrevious());
        b.putBoolean(TRANSPORTS_CONTROLS_BUNDLE_KEY_CAN_PLAY, timeline.isEmpty() ? false : true);
        EXTRAS_BUNDLE.putBundle(EXTRAS_KEY_TRANSPORTS_CONTROLS_BUNDLE, b);
        mMediaSession.setExtras(EXTRAS_BUNDLE);
    }

    private void mUpdateMetadata(MediaItem mediaItem){
        if(mSessionMetaDataBuilder == null) mSessionMetaDataBuilder = new MediaMetadataCompat.Builder();

        if(mediaItem == null){
            //TODO : fib
            return;
        }
        String title    = null;
        String artist   = null;
        final long duration = mPlayer.getContentDuration();

        String currentMediaId = mediaItem.mediaId;
        MediaDescriptionCompat md = mSharedStorageAudioMap.get(currentMediaId).getDescription();//TODO : CAN_CRASH

        title = (String)md.getTitle(); artist = md.getExtras().getString(MDEK_ARTIST);
        //TODO : is their any other ways to check if a bitmap uri is valid, if true replace the below method with that
        Bitmap albumArt = null;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            try{
                albumArt = getContentResolver().loadThumbnail(
                        md.getMediaUri(),
                        new Size(MAIN_PLAYER_ALBUM_ART_SIDES, MAIN_PLAYER_ALBUM_ART_SIDES),
                        null
                );
            } catch (IOException e){
                Log.e(TAG, "failed to load AlbumArt");
            }
        } else {
            //TODO : fill in the blank
        }
        //TEMP
        if(mDefMusicBitmap == null){
            mDefMusicBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_def_music_l);
        }

        mSessionMetaDataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
                /* TODO : add bitmap */
        mMediaSession.setMetadata(mSessionMetaDataBuilder.build());

        //Notifications
        mNotificationBuilder.clearActions();
        //TODO : use appropriate icons
        mNotificationBuilder
                .addAction(new NotificationCompat.Action(
                        R.drawable.exo_controls_previous,
                        "Play Previous",
                        mPlayPreviousPendingIntent))
                .addAction(new NotificationCompat.Action(
                        (mPlayer.isPlaying()) ? R.drawable.exo_controls_pause : R.drawable.exo_controls_play,
                        "Play-Pause Toggle",
                        mPlayPausePendingIntent))
                .addAction(new NotificationCompat.Action(
                        R.drawable.exo_controls_next,
                        "Play Next",
                        mPlayNextPendingIntent))
                .setLargeIcon((albumArt == null) ? mDefMusicBitmap : albumArt)
                .setContentTitle(title)
                .setContentText(artist)
                .setStyle(mMediaStyle);
        mNotificationMangerCompat.notify(MEDIA_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private boolean mNotificationActive(int notificationId){
        StatusBarNotification activeNotification[] = mNotificationManger.getActiveNotifications();

        for(StatusBarNotification n : activeNotification){
            if(n.getId() == notificationId) return true;
        }
        return false;
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

    //exoplayer handles the audio focus , see <SimpleExoPlayer Instance>.setAudioAttributes
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
    public void onAudioFocusChange(int focusChange) {
        Log.w(TAG, LT.UNIMPLEMENTED);
    }
}
