package com.example.mediasession;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.Build;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContentResolverCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.example.mediasession.annotations.Unsatisfied;
import com.example.mediasession.room.PlaylistEntity;
import com.example.mediasession.room.PlaylistsDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@Unsatisfied(reason = {"threading"})
/* warning make sure to set the updated flag of PlaylistEntity */
//TODO : update the database at end
//TODO : replace livedata with appropriate custom implementation(our implementation doesnt require lifecycle)
//TODO : test the behaviour of room practically (like querying invalid entity etc..)
/** this class should only used by one service**/
class Repositary {
    static final String TAG = "Repositary";
    
    private final int THUMBNAIL_SIDES = 60;//TODO : change it based on screen size

    /** PEK - Playlist Extras Key **/
    static final String PEK_NO_OF_SONGS = "pek_no_of_songs";

    static final int MAX_NUMBER_OF_PLAYLIST = 99;
    static final int MAX_NUMBER_OF_PLAYLIST_MEMBER = 200;
    
    private static final String[] LOCAL_MEDIA_QUERY_PROJECTION = new String[] {MediaStore.Audio.Media._ID , MediaStore.Audio.Media.DISPLAY_NAME };
    
    private final MediaDescriptionCompat.Builder mMediaDiscriptionBuilder = new MediaDescriptionCompat.Builder();

    private Context mContext;
    private ContentResolver mContentResolver;
    
    private Uri mSharedStorageExternalUri;
    private Uri mSharedStorageInternalUri;
    
    private ContentObserver mInternalContentObserver;
    private ContentObserver mExternalContentObserver;
    
    private Repositary.Observer mSharedStorageMediaObserver;
    private Repositary.Observer mPlaylistObserver;

    //must be final
    /* SSM - shared storage media */
    private final List<MediaBrowserCompat.MediaItem> mSSMExternal = new ArrayList<>();
    private final List<MediaBrowserCompat.MediaItem> mSSMInternal = new ArrayList<>();    
    
    private final Object SHARED_STORAGE_LOCK = new Object();
    private final Object SHARED_STORAGE_UPDATE_LOCK = new Object();
    
    private boolean mIncludeInternalSSM;/* false by default */
    
    //TODO : switch to executors
    //TODO : inefficient
    
    private PlaylistsDatabase mDatabase;
    private androidx.lifecycle.Observer<List<PlaylistEntity>> mDatabaseObserver;
    //TODO : use a map that doesnt allow repeated values
    private final HashMap<Integer, PlaylistEntity> mAllPlaylistEntityMap = new HashMap<>();
    private final MediaDescriptionCompat.Builder mMediaDescriptionBuilder = new MediaDescriptionCompat.Builder();
    private int mPlaylistCount;

    private final List<MediaBrowserCompat.MediaItem> mMediaItemPlaylists = new ArrayList<>();
    private final List<Observer> mActivePlaylistObservers = new ArrayList<>();
    private final List<Observer> mActiveSharedStorageObservers = new ArrayList<>();
    
    Repositary(@NonNull Context context){
        if(context == null) throw new IllegalArgumentException("context can't be null");
        mContext = context; mContentResolver = mContext.getContentResolver();
        
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            mSharedStorageExternalUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            mSharedStorageInternalUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL);
        } else {
            mSharedStorageExternalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            mSharedStorageInternalUri = MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
        }
    }
    
    static interface OnLoadedCallback{
        public abstract void onLoaded(List<MediaBrowserCompat.MediaItem> updatedData);
    }
    
    static interface Observer{
        public abstract void onDataChanged(List<MediaBrowserCompat.MediaItem> mediaItems, int changeType);
    }
    
    /** includes/excludes shared storage in internal storage **/
    void includeInternalStorage(boolean include){
        mIncludeInternalSSM = include;
    }
    
    /** @return null on error **/
    @Nullable
    private List<MediaBrowserCompat.MediaItem> mGetSharedStorageMedia(@NonNull Uri uri) throws IllegalArgumentException {
        if(uri==null) throw new IllegalArgumentException();
        //TODO : use IS_MUSIC constant, fill the arguments with appropriate values
        Cursor cursor = ContentResolverCompat.query(mContext.getContentResolver(),
                uri ,
                Repositary.LOCAL_MEDIA_QUERY_PROJECTION,
                null,
                null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER,
                null);

        if(cursor == null) {
            Log.e(TAG, "cant load media for uri:"+uri.toString()+" , cursor is null");
            return null;
        }
        final Resources res = mContext.getResources();
        final List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        if (cursor.moveToFirst()) {
            //default values
            final String defaultArtistName = res.getString(R.string.mediadescription_default_artist_name);
            final String defaultGenreName = res.getString(R.string.mediadescription_default_genre_name);
            final String defaultDuration = res.getString(R.string.mediadescription_default_duration);
            final String defaultDisplayName = res.getString(R.string.mediadescription_default_display_name);
            //_____________

            do {
                long id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
                Uri mediaUri = ContentUris.withAppendedId(uri,id);
                String mediaId = mediaUri.toString();
                String displayName = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                if(mIsStringEmpty(displayName)) displayName = defaultDisplayName;

                MediaDescriptionCompat md = mMakeMediaDescriptor(mediaUri ,mediaId ,displayName, defaultArtistName, defaultGenreName, defaultDuration);/* rename mMakeMediaDescriptor method */
                if (md == null){
                    Log.e(TAG, "Method:mGetSharedStorageMedia can't make mediadescription, item skipped");
                    continue;//skip this item
                }
                MediaBrowserCompat.MediaItem mediaItem = new MediaBrowserCompat.MediaItem(md, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);

                mediaItems.add(mediaItem);
            } while (cursor.moveToNext());
        }    
        return mediaItems;
    }
    
    /** loads/reloads and updates shared storage media **/
    void loadSharedStorageMedia(){
        //setup content observer if null
        //load data async and update observers on loaded
        
        /* TODO : check add proper content observer */
        /* TODO : OVERRIDE ALL METHODES FOR COMPATABILITY */
        if(mInternalContentObserver == null){
            mInternalContentObserver = new ContentObserver(null) {
                @Override public void onChange(boolean selfChange) {
                    Log.i(TAG , "___INTERENAL DATA CHANGED___");
                    
                    List<MediaBrowserCompat.MediaItem> internalMediaItems = mGetSharedStorageMedia(mSharedStorageInternalUri);
                    if(internalMediaItems != null){
                        mSSMInternal.clear();
                        mSSMInternal.addAll(internalMediaItems);
                        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
                        mediaItems.addAll(mSSMExternal);
                        mediaItems.addAll(mSSMInternal);
                        mUpdateSharedStorageMediaObserver(mediaItems, 0);
                    } else {
                        //TODO : notify error
                        mUpdateSharedStorageMediaObserver(null, 0);
                    }
                }
            };
            
        }
        if(mExternalContentObserver == null){
            mExternalContentObserver = new ContentObserver(null) {
                @Override public void onChange(boolean selfChange) {
                    Log.i(TAG, "___EXTERNAL DATA CHANGED___");
                    
                    List<MediaBrowserCompat.MediaItem> externalMediaItems = mGetSharedStorageMedia(mSharedStorageExternalUri);
                    if(externalMediaItems != null){
                        mSSMExternal.clear();
                        mSSMExternal.addAll(externalMediaItems);
                        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
                        mediaItems.addAll(mSSMExternal);
                        mediaItems.addAll(mSSMInternal);
                        mUpdateSharedStorageMediaObserver(mediaItems, 0);
                    } else {
                        //TODO : notify error
                        mUpdateSharedStorageMediaObserver(null, 0);
                    }
                }
            };
        }
        mContentResolver.registerContentObserver(mSharedStorageInternalUri,
                /*check this arg*/false, mInternalContentObserver);
        mContentResolver.registerContentObserver(mSharedStorageExternalUri,
                /*check this arg*/false, mExternalContentObserver);
        
        synchronized(SHARED_STORAGE_LOCK){
            new Thread(()->{
                final List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList(0);
                List<MediaBrowserCompat.MediaItem> externalMediaItems = mGetSharedStorageMedia(mSharedStorageExternalUri);
                if(externalMediaItems != null){
                    mSSMExternal.clear();
                    mSSMExternal.addAll(externalMediaItems);
                    mediaItems.addAll(externalMediaItems);
                } else {
                    //TODO : notify error
                }
                
                if(!mIncludeInternalSSM){
                    return;
                } else {
                    mSSMInternal.clear();
                }
                
                List<MediaBrowserCompat.MediaItem> internalMediaItems = mGetSharedStorageMedia(mSharedStorageInternalUri);
                if(internalMediaItems != null){
                    mSSMInternal.clear();
                    mSSMInternal.addAll(internalMediaItems);
                    mediaItems.addAll(internalMediaItems);
                } else {
                    //TODO : notify error
                }
                mUpdateSharedStorageMediaObserver(mediaItems, 0);
            }).start();
        }
    }
    
    private void mUpdateSharedStorageMediaObserver(List<MediaBrowserCompat.MediaItem> mediaItems, int changeType){
        synchronized(SHARED_STORAGE_UPDATE_LOCK){
            if(mSharedStorageMediaObserver != null){
                mSharedStorageMediaObserver.onDataChanged(mediaItems, changeType);
            }
        }
    }
    
    void setSharedStorageMediaObserver(@NonNull Repositary.Observer observer){
        if(observer == null) throw new IllegalArgumentException("argument observer can't be null");
        mSharedStorageMediaObserver = observer;
    }
  
    void setPlaylistObserver(@NonNull Repositary.Observer observer){
        if(observer == null) throw new IllegalArgumentException("argument observer can't be null");
    }
    
    void refresh(){
        //TODO : use content reslover refresh also
    }
    //_______________
    
    void loadRepository(){
        mDatabase.playlistDao().getAllPlaylists().observeForever(mDatabaseObserver);

    }

    //TODO : return operation result
    void addPlaylists(@NonNull/*@NonEmpty*/ String playlistName,
                      @Nullable String description,
                      @Nullable String displayPictureUri,
                      @NonNull MediaBrowserServiceCompat.Result<Bundle> result)
            throws IllegalArgumentException{
        //TODO : check how long this method takes to calculate MAX_NUMBER_OF_PLAYLIST_MEMBER entities
        if(playlistName == null || result == null) throw new IllegalArgumentException();
        if(playlistName.isEmpty()) { result.sendError(null); return; }//dont allow empty name

        if(mPlaylistCount > MAX_NUMBER_OF_PLAYLIST) { result.sendError(null); return; }

        if(mIsNameAlreadyExist(playlistName, mAllPlaylistEntityMap.values())) {
            result.sendError(null); return;
        }

        final PlaylistEntity playlistEntity =
                new PlaylistEntity(playlistName, description, displayPictureUri, 0, "");
        result.detach();
        final Runnable runnable = new Runnable() {
            @Override public void run() {
                mDatabase.playlistDao().insertPlaylist(playlistEntity);
                result.sendResult(null);
            }
        };
        //TODO : add thread pool(use high level java concurrency classes)
        synchronized (Repositary.this){ new Thread(runnable).start(); }
    }

    void removePlaylist(int playlistId, @NonNull MediaBrowserServiceCompat.Result<Bundle> result)
            throws IllegalArgumentException {
        if(result == null) throw new IllegalArgumentException();

        final PlaylistEntity playlistEntity = mAllPlaylistEntityMap.get(playlistId);
        if(playlistEntity!=null){
            result.detach();
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    mDatabase.playlistDao().removePlaylist(playlistEntity);
                    result.sendResult(null);
                }
            };
            synchronized (Repositary.this){ new Thread(runnable).start(); }
        } else {
            //TODO : notify error
            Log.w(TAG, "removePlaylist, cant find playlist");
            result.sendError(null);
            return;
        }
    }

    void updatePlaylist(String playlistName, String description, String displayPictureUri){
        //make sure playlistName is unique (use mIsNameAlreadyExist())
        throw new RuntimeException("Under Construction");
    }

    void addPlaylistMember(int playlistId, String memberUri,
                           @NonNull MediaBrowserServiceCompat.Result<Bundle> result)
            throws IllegalArgumentException{
        if(result == null) throw new IllegalArgumentException("argument result can't be null");

        final PlaylistEntity entity = mAllPlaylistEntityMap.get(playlistId);
        if(entity == null){//playlist doesn't exist
            result.sendError(null); return;
        } else {
            if(!mIsValidUri(memberUri)){ result.sendError(null); return; }
            if(mMemberExist(entity.memberUris, memberUri)){ result.sendError(null); return; }
            if(entity.numberOfSongs >= MAX_NUMBER_OF_PLAYLIST_MEMBER) { result.sendResult(null); return; }
            entity.memberUris = mAppendPlaylistMember(entity.memberUris, memberUri);
            result.detach();
            synchronized (Repositary.this){
                final Runnable runnable = new Runnable() {
                    @Override public void run() {
                        mDatabase.playlistDao().updatePlaylist(entity);
                        entity.numberOfSongs += 1;
                        result.sendResult(null);
                    }
                };
                new Thread(runnable).start();
            }
        }
    }

    /**  warning doesn't verify input ;
     * if argument append is null replaces it with empty string **/
    private String mAppendPlaylistMember(@Nullable String main, @NonNull/*add @NonEmpty*/ String append)
            throws IllegalArgumentException{
        if(append == null) throw new IllegalArgumentException();
        return (main==null ? "" : main)+append+" "; //space is important
    }

    void removePlaylistMember(int playlistId, @NonNull String memberUri,
                              @NonNull MediaBrowserServiceCompat.Result<Bundle> result)
            throws IllegalArgumentException{
        if(result == null || memberUri == null) throw new IllegalArgumentException();
        if(!mIsValidUri(memberUri)){ result.sendError(null); return; }
        final PlaylistEntity entity = mAllPlaylistEntityMap.get(playlistId);
        if(entity == null) { result.sendError(null); return; }
        //TODO : check if member exist
        entity.memberUris = entity.memberUris.replace(memberUri+" ", "");//removes all entries
        result.detach();
        synchronized (Repositary.this){
            final Runnable runnable = new Runnable() {
                @Override public void run() {
                    mDatabase.playlistDao().updatePlaylist(entity);
                    entity.numberOfSongs -= 1;
                    result.sendResult(null);
                }
            };
            new Thread(runnable).start();
        }
    }

    private boolean mMemberExist(@NonNull String members, @NonNull String member){
        //TODO : WRITE pattern searching algorithem
        Log.w(TAG, "mMemberExist,"+LT.UNIMPLEMENTED);
        return false;
    }

    private boolean mIsValidUri(@Nullable String s){
        //TODO : check if has spaces and check if its a content uri, check if empty, check null
        if(s == null || s.isEmpty()) throw new IllegalArgumentException();
        char[] chars = s.toCharArray();
        for(char c : chars){ if(c == ' ') return true; }
        return false;
    }

    private <T extends Collection<PlaylistEntity>> boolean mIsNameAlreadyExist(
            @NonNull String playlistName, @NonNull T t) throws IllegalArgumentException{
        if(playlistName == null || t == null) throw new IllegalArgumentException();
        for(PlaylistEntity entity : t){
            String s = entity.playlistName;
            if(playlistName.equals(s)) {
                Log.e(TAG, LT.UNIMPLEMENTED+"can't add playlist name already exist");
                return true;
            }
        }
        return false;
    }

    private <T extends Collection<Observer>>void mAddObserver(@NonNull Observer observer, @NonNull T t)
            throws IllegalArgumentException {
        if(observer == null) throw new IllegalArgumentException("observer is null");
        int i = 0;
        for(Observer o : t){
            if(o == observer){//replace
                t.remove(i);
                t.add(observer);
                return;
            }
            i++;
        }
        t.add(observer);
    }

    void stop(){
        //TODO :
        
        mContentResolver.unregisterContentObserver(mExternalContentObserver);
        mContentResolver.unregisterContentObserver(mInternalContentObserver);
        mContentResolver = null;
        mContext = null;
        
        mDatabase.playlistDao().getAllPlaylists().removeObserver(mDatabaseObserver);
        mDatabaseObserver = null;
        mDatabase = null;
    }
    
    /* util methods */
    private boolean mIsStringEmpty(@Nullable String s){
        return s==null || s.isEmpty();
    }
    
    //TODO : this is moved from ServiceMediaPlayback ,check 
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
            mediaMetadataRetriever.setDataSource(mContext, uri);
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
        extras.putString(ServiceMediaPlayback.MEDIA_DESCRIPTION_KEY_ARTIST, stringArtist);
        extras.putString(ServiceMediaPlayback.MEDIA_DESCRIPTION_KEY_DURATION, durationString);
        extras.putString(ServiceMediaPlayback.MEDIA_DESCRIPTION_KEY_GENRE, stringGenre);

        Bitmap bitMapThumbNail = null;
        //Note : Extend it to support various screen sizes
        //Make Sure this doesnt take too much memory
        Log.w(LT.IP, "ThumbNail Loader code");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bitMapThumbNail = mContext.getContentResolver()
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
}
