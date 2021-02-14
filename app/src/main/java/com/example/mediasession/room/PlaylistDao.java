package com.example.mediasession.room;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Query("SELECT * FROM PlaylistEntity")
    LiveData<List<PlaylistEntity>> getAllPlaylists();

    //TODO : add onConflict
    //@Insert(onConflict = )
    @Insert
    void insertPlaylist(PlaylistEntity playlist);

    @Update
    void updatePlaylist(PlaylistEntity playlistEntity);

    @Delete
    void removePlaylist(PlaylistEntity playlist);

    @Query("SELECT * FROM PlaylistEntity WHERE uid=:playlistId")
    LiveData<PlaylistEntity> getPlaylistById(int playlistId);
}
