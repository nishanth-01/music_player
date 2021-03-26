package com.example.mediasession.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity
public class PlaylistEntity {
    @PrimaryKey(autoGenerate = true)
    private int uid;

    //TODO : check index annotation argument

    @ColumnInfo(name = "playlist_name", typeAffinity = ColumnInfo.TEXT)
    private String playlistName;//should be unique for each playlists and cant be empty

    @ColumnInfo(name = "description", typeAffinity = ColumnInfo.TEXT)
    private String description;//optional

    @ColumnInfo(name = "display_picture_uri", typeAffinity = ColumnInfo.TEXT)
    private String displayPictureUri;//optional

    @NonNull
    @ColumnInfo(name = "member_uris", typeAffinity = ColumnInfo.TEXT)
    private String memberUris = "";

    //constructors

    //public PlaylistEntity() {}
    
    /** dont use this, this constructor only for room **/
    public PlaylistEntity(int uid, String playlistName, String description, String displayPictureUri,
                          String memberUris) throws IllegalArgumentException {
        this.uid = uid;
        this.playlistName = playlistName;
        this.description = description;
        this.displayPictureUri = displayPictureUri;
        this.memberUris = memberUris;
    }

    @Ignore
    public PlaylistEntity(@NonNull String playlistName, String description, String displayPictureUri,
                          @NonNull String memberUris) throws IllegalArgumentException {
        if(playlistName == null || playlistName.isEmpty())
            throw new IllegalArgumentException("playlistName can't be null/empty");
        if(memberUris == null)
            throw new IllegalArgumentException("memberUris can't be null");
        this.playlistName = playlistName;
        this.description = description;
        this.displayPictureUri = displayPictureUri;
        this.memberUris = memberUris;
    }
    
    public int getUid(){
        return uid;
    }
    
    public String getPlaylistName(){
        return playlistName;
    }
    
    public String getDescription(){
        return description;
    }
    
    public String getDisplayPictureUri(){
        return displayPictureUri;
    }
    
    public String getMemberUris(){
        return memberUris;
    }
    
    
    //TODO : try making this method package
    public void setMemberUris(@NonNull String uris) throws IllegalArgumentException {
        if(uris == null) throw new IllegalArgumentException();
        memberUris = uris;
    }
}
