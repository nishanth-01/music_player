package com.example.mediasession.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.example.mediasession.room.PlaylistDao;

@Database(entities = {PlaylistEntity.class}, version = 2)
public abstract class PlaylistsDatabase extends RoomDatabase {
    public abstract PlaylistDao playlistDao();
}
