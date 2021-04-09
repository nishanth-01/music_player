package com.example.mediasession;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

/*  check UAMP source   */
public class MainSharedViewModel extends AndroidViewModel {
    private static final String TAG = "MainSharedViewModel";

    private final MutableLiveData<MediaMetadataCompat> mMetadataMLD = new MutableLiveData<>();
    private final MutableLiveData<PlaybackStateCompat> mPlaybackStateMLD = new MutableLiveData<>();
    private final MutableLiveData<Byte> mServiceConnectionStatusMLD = new MutableLiveData<>((byte) 0);
    //null is error for the below values
    private final MutableLiveData<List<MediaSessionCompat.QueueItem>> mQueueMLD =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<MediaBrowserCompat.MediaItem>> mAllLocalMediaMLD =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<MediaBrowserCompat.MediaItem>> mAllPlaylistsMLD =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Bundle> mExtrasMLD = new MutableLiveData<>(Bundle.EMPTY);

    private Context mApplicationContext;

    @NonNull private MediaBrowserCompat mMediaBrowser;//initilized by constructor
    private MediaControllerCompat mMediaController;
    private MediaBrowserCompat.ConnectionCallback mBrowserConnectionCallback;
    private MediaControllerCompat.Callback mMediaControllerCallback;

    private MediaBrowserCompat.SubscriptionCallback mSharedMediaSubCallback;
    private MediaBrowserCompat.SubscriptionCallback mPlaylistSubCallback;


    public MainSharedViewModel(@NonNull Application application) {
        super(application);
        mApplicationContext = application.getApplicationContext();
        mBrowserConnectionCallback = new MediaBrowserCompat.ConnectionCallback(){
            @Override
            public void onConnected() {
                mMediaController = new MediaControllerCompat(mApplicationContext, mMediaBrowser.getSessionToken());
                if(mMediaControllerCallback == null) {
                    mMediaControllerCallback = new MediaControllerCompat.Callback() {
                        @Override
                        public void onPlaybackStateChanged(PlaybackStateCompat state) {
                            mPlaybackStateMLD.setValue(state);
                        }

                        @Override
                        public void onMetadataChanged(MediaMetadataCompat metadata) {
                            mMetadataMLD.setValue(metadata);
                        }

                        @Override
                        public void onExtrasChanged(Bundle extras) { mExtrasMLD.setValue(extras); }

                        @Override
                        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
                            mQueueMLD.setValue(queue);
                        }
                    };
                }
                mMediaController.registerCallback(mMediaControllerCallback); mSubscribeData();
                mServiceConnectionStatusMLD.setValue(ServiceMediaPlayback.SERVICE_CONNECTED);
            }
            @Override
            public void onConnectionSuspended() {
                if(mMediaController!=null){
                    try{
                        mMediaController.unregisterCallback(mMediaControllerCallback);
                    } catch (Exception e){
                        //ignore
                    }
                    mMediaController = null;
                }
                mServiceConnectionStatusMLD.setValue(ServiceMediaPlayback.SERVICE_SUSPENDED);
            }
            @Override
            public void onConnectionFailed() {
                if(mMediaController!=null){
                    try{ mMediaController.unregisterCallback(mMediaControllerCallback); }
                    catch (Exception e){ /* do nothin */ }
                    mMediaController = null;
                }
                mServiceConnectionStatusMLD.setValue(ServiceMediaPlayback.SERVICE_FAILED);
            }
        };

        //idle state
        //check
        mMetadataMLD.setValue(new MediaMetadataCompat.Builder().build());
        mPlaybackStateMLD.setValue(new PlaybackStateCompat.Builder().build());

        Log.w(TAG, LT.UNIMPLEMENTED+"check rootHits parameter of MediaBrowserCompat constructor");
        Context applicationContext = mApplicationContext;
        mMediaBrowser = new MediaBrowserCompat(applicationContext,
                new ComponentName(applicationContext, ServiceMediaPlayback.class),
                mBrowserConnectionCallback, null);

        mMediaBrowser.connect();
    }

    @Nullable
    LiveData<Byte> getServiceConnectionLD(){
        return (LiveData) mServiceConnectionStatusMLD;
    }

    @NonNull
    LiveData<MediaMetadataCompat> getMetadataLD(){
        return (LiveData)mMetadataMLD;
    }

    @NonNull
    LiveData<PlaybackStateCompat> getPlaybackStateLD(){
        return (LiveData)mPlaybackStateMLD;
    }

    @NonNull
    LiveData<Bundle> getExtrasLD(){ return (LiveData)mExtrasMLD; }

    @NonNull
    LiveData<List<MediaSessionCompat.QueueItem>> getQueueLD(){return (LiveData)mQueueMLD;}

    @NonNull
    LiveData<List<MediaBrowserCompat.MediaItem>> getAllLocalMediaLD(){
        return (LiveData)mAllLocalMediaMLD;
    }

    @NonNull
    LiveData<List<MediaBrowserCompat.MediaItem>> getPlaylistsLD(){
        return (LiveData) mAllPlaylistsMLD;
    }

    private void mSubscribeData(){
        if(mSharedMediaSubCallback == null) {
            mSharedMediaSubCallback = new MediaBrowserCompat.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(@NonNull String parentId,
                                             @NonNull List<MediaBrowserCompat.MediaItem> children,
                                             @NonNull Bundle options) {
                    //TODO : check bundle if its has the error flag notify it to activity
                    mAllLocalMediaMLD.setValue(children);
                }

                @Override
                public void onError(@NonNull String parentId, @NonNull Bundle options) {
                    //TODO : handle error playlists
                }
            };
        }
        mMediaBrowser.subscribe(mApplicationContext.getString(R.string.id_local_media),
                /*dummy*/new Bundle(0), mSharedMediaSubCallback);

        if(mPlaylistSubCallback == null){
            mPlaylistSubCallback = new MediaBrowserCompat.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(@NonNull String parentId,
                                             @NonNull List<MediaBrowserCompat.MediaItem> children,
                                             @NonNull Bundle options) {
                    //TODO : check bundle if its has the error flag notify it to activity
                    mAllPlaylistsMLD.setValue(children);
                }

                @Override
                public void onError(@NonNull String parentId, @NonNull Bundle options) {
                    //TODO : handle error
                }
            };
        }
        mMediaBrowser.subscribe(mApplicationContext.getString(R.string.id_local_playlists),
                /*dummy*/new Bundle(0), mPlaylistSubCallback);

        //add recents etc...
    }

    void subscribePlaylistMember(@NonNull String playlistId, @NonNull MediaBrowserCompat.SubscriptionCallback callback)
            throws IllegalArgumentException {
        if(callback == null || playlistId == null) throw new IllegalArgumentException();
        mMediaBrowser.subscribe(playlistId, new Bundle(0), callback);
    }

    void unsubscribePlaylistMember(@NonNull String playlistId,
                                    @NonNull MediaBrowserCompat.SubscriptionCallback callback)
            throws IllegalArgumentException {
        if(callback == null || playlistId == null) throw new IllegalArgumentException();
        mMediaBrowser.unsubscribe(playlistId, callback);
    }

    void playFromMediaId(@NonNull /*TODO : @NonEmpty*/ String id, Bundle extras){
        if(id == null || id.isEmpty()) {
            Log.w(LT.IP, LT.UNIMPLEMENTED+"MainSharedViewModel:playFromMediaId, empty id");
            return;//TODO : notify error to user "cant play media"
        }

        if(mMediaBrowser.isConnected() && mMediaController != null)
            mMediaController.getTransportControls().playFromMediaId(id, extras);
        else Log.w(LT.IP, LT.UNIMPLEMENTED+"playFromMediaId, cant play");//TODO : notify error to user
    }

    void playPlaylist(String playlistId){
        if(mMediaBrowser.isConnected()) {
            final Bundle extras = new Bundle(1);
            extras.putString(ServiceMediaPlayback.CADK_PLAYLIST_ID, playlistId);

            mMediaBrowser.sendCustomAction(ServiceMediaPlayback.ACTION_PLAY_PLAYLIST, extras, null);
        } else {
            //TODO : notify user "try again"
        }
    }

    void addToQueue(MediaDescriptionCompat mediaDescription){
        if(mMediaController!=null){
            if(mediaDescription!=null){
                try {
                    mMediaController.addQueueItem(mediaDescription);
                } catch (UnsupportedOperationException e){
                    Log.w(LT.IP, LT.UNIMPLEMENTED+"Service doest support queue commands error");
                }
            } else {
                Log.w(LT.IP, LT.UNIMPLEMENTED+"addToQueue, cant add to queue mediaDescription is null");
            }
        } else {
            Log.w(LT.IP, LT.UNIMPLEMENTED+"addToQueue, cant add to queue mMediaController is null");
        }
    }

    void togglePlayPause(){
        if(mMediaBrowser != null){
            mMediaBrowser.sendCustomAction(ServiceMediaPlayback.ACTION_TOGGLE_PLAY_PAUSE, null, null);
        } else {
            Log.w(TAG, LT.UNIMPLEMENTED+"togglePlayPause, mMediaController is null");
        }
    }

    void playNext(){
        if(mMediaController!=null){
            MediaControllerCompat.TransportControls tc = mMediaController.getTransportControls();
            tc.skipToNext();
        } else {
            Log.w(TAG, LT.UNIMPLEMENTED+"playNext");
        }
    }

    void playPrevious(){
        if(mMediaController!=null){
            MediaControllerCompat.TransportControls tc = mMediaController.getTransportControls();
            tc.skipToPrevious();
        } else {
            Log.w(TAG, LT.UNIMPLEMENTED+"playPrevious");
        }
    }

    void skipToQueueItem(@NonNull MediaSessionCompat.QueueItem queueItem){
        if(mMediaController!=null){
            MediaControllerCompat.TransportControls tc = mMediaController.getTransportControls();
            tc.skipToQueueItem(queueItem.getQueueId());
        } else {
            Log.w(TAG, LT.UNIMPLEMENTED+"skipToQueueItem");
        }
    }

    void removeQueueItem(@NonNull MediaSessionCompat.QueueItem queueItem){
        if(mMediaBrowser.isConnected()) {
            final Bundle args = new Bundle(1);
            args.putInt(ServiceMediaPlayback.CADK_QUEUE_ID, (int) queueItem.getQueueId());
            mMediaBrowser.sendCustomAction(ServiceMediaPlayback.ACTION_REMOVE_QUEUE_ITEM, args,
                    /* reuse this object, make it singleton */
                    new MediaBrowserCompat.CustomActionCallback() {
                @Override
                public void onProgressUpdate(String action, Bundle extras, Bundle data) {
                    Log.w(TAG, LT.UNIMPLEMENTED+"removeQueueItem onProgressUpdate");
                }

                @Override
                public void onResult(String action, Bundle extras, Bundle resultData) {
                    Log.w(TAG, LT.UNIMPLEMENTED+"removeQueueItem onResult");
                }

                @Override
                public void onError(String action, Bundle extras, Bundle data) {
                    Log.w(TAG, LT.UNIMPLEMENTED+"removeQueueItem onError");
                }
            });
        } else {
            Log.w(TAG, LT.UNIMPLEMENTED+"skipToQueueItem");
        }
    }

    void addPlaylist(@Nullable String playlistName){
        if(mMediaBrowser.isConnected()){
            Bundle bundle = new Bundle(1);
            bundle.putString(ServiceMediaPlayback.CADK_PLAYLIST_NAME, playlistName);
            mMediaBrowser.sendCustomAction(ServiceMediaPlayback.ACTION_ADD_PLAYLIST, bundle,
                    /* reuse this object, make it singleton */
                    new MediaBrowserCompat.CustomActionCallback() {
                        @Override
                        public void onProgressUpdate(String action, Bundle extras, Bundle data) {
                            //TODO : notify error to caller
                        }

                        @Override
                        public void onResult(String action, Bundle extras, Bundle resultData) {
                            //TODO : notify error to caller
                        }

                        @Override
                        public void onError(String action, Bundle extras, Bundle data) {
                            //TODO : notify error to caller
                        }
                    });
        } else {
            //TODO : notify error to caller
        }
    }

    void removePlaylist(String playlistId){
        final Bundle bundle = new Bundle(1);
        bundle.putInt(ServiceMediaPlayback.CADK_PLAYLIST_ID, Integer.valueOf(playlistId));
        mMediaBrowser.sendCustomAction(ServiceMediaPlayback.ACTION_REMOVE_PLAYLIST, bundle,
                new MediaBrowserCompat.CustomActionCallback() {
                    @Override
                    public void onProgressUpdate(String action, Bundle extras, Bundle data) {
                        Log.w(TAG, LT.UNIMPLEMENTED+"removePlaylist onProgressUpdate");
                    }

                    @Override
                    public void onResult(String action, Bundle extras, Bundle resultData) {
                        Log.w(TAG, LT.UNIMPLEMENTED+"removePlaylist onResult");
                    }

                    @Override
                    public void onError(String action, Bundle extras, Bundle data) {
                        Log.e(TAG, LT.UNIMPLEMENTED+"removePlaylist onError");
                    }
                });
    }

    void addPlaylistMemeber(int playlistId, String memberId, 
                            @NonNull MediaBrowserCompat.CustomActionCallback callBack) {
        if(mMediaBrowser.isConnected()){
            final Bundle bundle = new Bundle(2);
            bundle.putInt(ServiceMediaPlayback.CADK_PLAYLIST_ID, playlistId);
            bundle.putString(ServiceMediaPlayback.CADK_PLAYLIST_MEMBER_ID, memberId);
            mMediaBrowser.sendCustomAction(
                        ServiceMediaPlayback.ACTION_ADD_PLAYLIST_MEMBER, 
                            bundle, callBack);
        } else {
            //TODO : Maybe show "try again later" to user
        }
    }

    void removePlaylistMemeber(int playlistId, String memberId) {
        if(memberId == null) {
            //TODO : notify user "cant remove"
            return;
        }
        if(mMediaBrowser.isConnected()){
            final Bundle bundle = new Bundle(2);
            bundle.putInt(ServiceMediaPlayback.CADK_PLAYLIST_ID, playlistId);
            bundle.putString(ServiceMediaPlayback.CADK_PLAYLIST_MEMBER_ID, memberId);

            MediaBrowserCompat.CustomActionCallback callBack = new MediaBrowserCompat.CustomActionCallback() {
                @Override
                public void onProgressUpdate(String action, Bundle extras, Bundle data) {
                    //TODO
                }

                @Override
                public void onResult(String action, Bundle extras, Bundle resultData) {
                    //TODO
                }

                @Override
                public void onError(String action, Bundle extras, Bundle data) {
                    //TODO
                }
            };

            mMediaBrowser.sendCustomAction(ServiceMediaPlayback.ACTION_REMOVE_PLAYLIST_MEMBER,
                    bundle, callBack);
        }
    }

    /**Retry to connect service**/
    void retryServiceConnection(){
        if(!mMediaBrowser.isConnected()) mMediaBrowser.connect();
    }

    @Override
    protected void onCleared() {
        //TODO
        Log.d(LT.IP, "MainSharedViewModel onCleared called");
        mMediaBrowser.disconnect();
        mMediaBrowser=null;mMediaController=null;
        super.onCleared();
    }
}
