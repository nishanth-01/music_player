package com.example.mediasession.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity
public class PlaylistEntity {
    @PrimaryKey(autoGenerate = true)
    public int uid;

    //TODO : check index annotation argument

    @ColumnInfo(name = "playlist_name", typeAffinity = ColumnInfo.TEXT)
    public String playlistName;//should be unique for each playlists and cant be empty

    @ColumnInfo(name = "description", typeAffinity = ColumnInfo.TEXT)
    public String description;//optional

    @ColumnInfo(name = "display_picture_uri", typeAffinity = ColumnInfo.TEXT)
    public String displayPictureUri;//optional

    @ColumnInfo(name = "no_of_songs", typeAffinity = ColumnInfo.INTEGER)
    public int numberOfSongs = 0;//read only by user

    @NonNull
    @ColumnInfo(name = "member_uris", typeAffinity = ColumnInfo.TEXT)
    public String memberUris = "";

    //constructors

    public PlaylistEntity() {}

    @Ignore
    public PlaylistEntity(@NonNull String playlistName, String description, String displayPictureUri,
                          int numberOfSongs, @NonNull String memberUris) throws IllegalArgumentException {
        if(playlistName == null || playlistName.isEmpty())
            throw new IllegalArgumentException("playlistName can't be null/empty");
        if(memberUris == null)
            throw new IllegalArgumentException("memberUris can't be null");
        this.playlistName = playlistName;
        this.description = description;
        this.displayPictureUri = displayPictureUri;
        this.numberOfSongs = numberOfSongs;
        this.memberUris = memberUris;
    }
}
