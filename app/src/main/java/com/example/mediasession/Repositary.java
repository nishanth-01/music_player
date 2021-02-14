package com.example.mediasession;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContentResolverCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.example.mediasession.annotations.Unsatisfied;
import com.example.mediasession.room.PlaylistEntity;
import com.example.mediasession.room.PlaylistsDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@Unsatisfied(reason = {"threading", "algorithem"})
/* warning make sure to set the updated flag of PlaylistEntity */
//TODO : update the database at end
//TODO : replace livedata with appropriate custom implementation(our implementation doesnt require lifecycle)
//TODO : test the behaviour of room practically (like querying invalid entity etc..)
class Repositary {
    /** PEK - Playlist Extras Key **/
    static final String PEK_NO_OF_SONGS = "pek_no_of_songs";

    static final int MAX_NUMBER_OF_PLAYLIST = 99;
    static final int MAX_NUMBER_OF_PLAYLIST_MEMBER = 200;



    //TODO : switch to executors
    //TODO : inefficient
    private static final String TAG = "Repositary";
    private PlaylistsDatabase mDatabase;
    private androidx.lifecycle.Observer<List<PlaylistEntity>> mDatabaseObserver;
    //TODO : use a map that doesnt allow repeated values
    private final HashMap<Integer, PlaylistEntity> mAllPlaylistEntityMap = new HashMap<>();
    private final MediaDescriptionCompat.Builder mMediaDescriptionBuilder =
            new MediaDescriptionCompat.Builder();
    private int mPlaylistCount;

    private final List<MediaBrowserCompat.MediaItem> mMediaItemPlaylists = new ArrayList<>();
    private final List<Observer> mActivePlaylistObservers = new ArrayList<>();
    private final List<Observer> mActiveSharedStorageObservers = new ArrayList<>();


    public Repositary(@NonNull PlaylistsDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database can't be null");
        }
        this.mDatabase = database;

        // - - - - - - - - - - - playlists from room - - - - - - - - - - - - - -
        /*mDatabaseObserver = new androidx.lifecycle.Observer() {
            @Override public void onChanged(List<PlaylistEntity> playlistEntities) {
                if(playlistEntities == null) throw new RuntimeException("database returned null");

                final List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
                mPlaylistCount = 0;
                for(PlaylistEntity playlistEntity : playlistEntities){
                    mPlaylistCount += 1;
                    mAllPlaylistEntityMap.put(playlistEntity.uid, playlistEntity);
                    Bundle extras = new Bundle(1);
                    extras.putInt(PEK_NO_OF_SONGS, playlistEntity.numberOfSongs);
                    final MediaDescriptionCompat mediaDescription =
                            mMediaDescriptionBuilder
                                    .setTitle(playlistEntity.playlistName)
                                    .setMediaId(Integer.toString(playlistEntity.uid))
                                    .setExtras(extras)
                                    .build();
                    mediaItems.add(new MediaBrowserCompat
                            .MediaItem(mediaDescription, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                }
                mUpdateObservers(mActivePlaylistObservers, mediaItems);
            }
        };*/
    }

    void loadRepository(){
        mDatabase.playlistDao().getAllPlaylists().observeForever(mDatabaseObserver);

    }

    static interface Observer{
        void onDataChanged(List<MediaBrowserCompat.MediaItem> mediaItems, int changeType);
    }

    void subscribeSharedStorageMedia(@NonNull Observer observer) throws IllegalArgumentException {
        try{
            mAddObserver(observer, mActiveSharedStorageObservers);
        } catch (IllegalArgumentException e){
            throw e;
        }
    }

    void unsubscribeSharedStorageMedia(@NonNull Observer observer) throws IllegalArgumentException {
        try{
            mRemoveObserver(observer, mActiveSharedStorageObservers);
        } catch (IllegalArgumentException e){
            throw e;
        }
    }

    void subscribePlaylistData(@NonNull Observer observer) throws IllegalArgumentException {
        try{
            mAddObserver(observer, mActivePlaylistObservers);
        } catch (IllegalArgumentException e){
            throw e;
        }
    }

    void unsubscribePlaylistData(@NonNull Observer observer) throws IllegalArgumentException{
        try{
            mRemoveObserver(observer, mActivePlaylistObservers);
        } catch (IllegalArgumentException e){
            throw e;
        }
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

    /**
     * @param uri the uri to query
     * @param hashMap puts the result into the map if NonNull
     * @return null in case of error
     * **/
    @Nullable
    private List<MediaBrowserCompat.MediaItem> mGetSharedStorageMedia(
            @NonNull Uri uri , @Nullable HashMap<String,MediaBrowserCompat.MediaItem> hashMap)
            throws IllegalArgumentException{
        if(uri==null) throw new IllegalArgumentException("uri can't be null");
        // *** use IS_MUSIC constant
        Cursor localMediaCursor = ContentResolverCompat.query(getContentResolver(),
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

    private <T extends Collection<Observer>> void mRemoveObserver(@NonNull Observer observer, @NonNull T t)
            throws IllegalArgumentException {
        if(observer == null) throw new IllegalArgumentException("observer is null");
        int i = 0;
        while(i < t.size()){
            Observer o = t.get(i);
            if(observer == o){ t.remove(i); return; }
            i++;
        }
    }

    private <T extends Collection<Observer>> void mUpdateObservers
            (@NonNull T observersList, @NonNull List<MediaBrowserCompat.MediaItem> mediaItems) throws IllegalArgumentException{
        if(observersList == null || mediaItems == null) throw new IllegalArgumentException();
        for(Observer observer : observersList){
            observer.onDataChanged(mediaItems, -1);
        }
    }

    void updateDatabaseAndRelease(){
        //TODO :
        mDatabase.playlistDao().getAllPlaylists().removeObserver(mDatabaseObserver);
        mDatabaseObserver = null;
        mDatabase = null;
    }
}
