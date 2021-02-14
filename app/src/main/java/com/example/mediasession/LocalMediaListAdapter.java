package com.example.mediasession;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediasession.databinding.MusicItemBinding;

import java.util.ArrayList;
import java.util.List;

public class LocalMediaListAdapter extends RecyclerView.Adapter<LocalMediaListAdapter.ThisViewHolder> {
    private final List<MediaDescriptionCompat> mDataSet = new ArrayList<>();
    private LocalMediaFragmentInterface mInputListener;
    private Drawable mDefaultThumb;

    /*optionally remove file formate in display name*/
    /* make this flexible*/

    public LocalMediaListAdapter(
            @NonNull List<MediaBrowserCompat.MediaItem> dataSet,
            @NonNull LocalMediaFragmentInterface inputListener, @Nullable Drawable defaultThumnail) {
        if(dataSet ==null) throw new IllegalArgumentException("dataSet can't be null");
        if(inputListener==null) throw new IllegalArgumentException("inputListener can't be null");

        List<MediaDescriptionCompat> mediaDescriptionList = new ArrayList<>();
        for(MediaBrowserCompat.MediaItem mediaItem : dataSet){
            mediaDescriptionList.add(mediaItem.getDescription());
        }
        this.mDataSet.addAll(mediaDescriptionList); this.mInputListener = inputListener;

        mDefaultThumb = defaultThumnail;
    }

    public static class ThisViewHolder extends RecyclerView.ViewHolder {
        private MusicItemBinding mMusicItemBinding;
        private MediaDescriptionCompat mMediaDescription;

        public MediaDescriptionCompat getMediaDescription() {
            return mMediaDescription;
        }

        public ThisViewHolder(@NonNull View itemView, @NonNull MusicItemBinding musicItemBinding) {
            super(itemView);
            if(musicItemBinding==null) throw new IllegalArgumentException("musicItemBinding can't be null");
            mMusicItemBinding = musicItemBinding;
        }
    }

    @NonNull
    @Override
    public ThisViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context parentContext = parent.getContext();
        MusicItemBinding musicItemBinding =
                MusicItemBinding.inflate(LayoutInflater.from(parentContext), parent, false);
        final View root = musicItemBinding.getRoot();
        root.setOnClickListener(v -> mInputListener.onClick(v));
        musicItemBinding.options.setOnClickListener(v -> mInputListener.onOptionsClick(root));
        return new ThisViewHolder(root, musicItemBinding);
    }

    /*  calculate time taken by this methode    */
    @Override
    public void onBindViewHolder(@NonNull ThisViewHolder holder , int position) {
        MediaDescriptionCompat mediaDescription = (MediaDescriptionCompat) mDataSet.get(position);

        holder.mMusicItemBinding.textviewTitle.setText((String) mediaDescription.getTitle());
        String artist = mediaDescription.getExtras()
                .getString(ServiceMediaPlayback.MEDIA_DESCRIPTION_KEY_ARTIST);
        holder.mMusicItemBinding.textviewArtist.setText(artist);
        Bitmap thumb = mediaDescription.getIconBitmap();
        if(thumb != null) holder.mMusicItemBinding.displayIcon.setImageBitmap(thumb);
        else holder.mMusicItemBinding.displayIcon.setImageDrawable(mDefaultThumb);
        holder.mMediaDescription = mediaDescription;
    }

    private boolean mIsEmpty(@Nullable String s){
        return (s==null || s.isEmpty());
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }
}
