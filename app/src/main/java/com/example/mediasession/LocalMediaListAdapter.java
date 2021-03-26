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

public class LocalMediaListAdapter extends RecyclerView.Adapter<LocalMediaListAdapter.ViewHolder> {
    private final List<MediaDescriptionCompat> mDataSet = new ArrayList<>();
    private ClickInterface mInputListener;
    private Drawable mDefaultThumb;

    /*TODO : optionally remove file formate in display name*/
    /* make this flexible*/

    public LocalMediaListAdapter(@NonNull List<MediaBrowserCompat.MediaItem> dataSet,
                                 @NonNull ClickInterface inputListener, @Nullable Drawable defaultThumnail) {
        if(dataSet == null) throw new IllegalArgumentException("dataSet can't be null");
        if(inputListener == null) throw new IllegalArgumentException("inputListener can't be null");

        List<MediaDescriptionCompat> mediaDescriptionList = new ArrayList<>();
        for(MediaBrowserCompat.MediaItem mediaItem : dataSet){
            mediaDescriptionList.add(mediaItem.getDescription());
        }
        this.mDataSet.addAll(mediaDescriptionList); this.mInputListener = inputListener;

        mDefaultThumb = defaultThumnail;
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder {
        private MusicItemBinding mMusicItemBinding;
        private MediaDescriptionCompat mMediaDescription;

        public MediaDescriptionCompat getMediaDescription() {
            return mMediaDescription;
        }

        public ViewHolder(@NonNull View itemView, @NonNull MusicItemBinding musicItemBinding) {
            super(itemView);
            if(musicItemBinding == null) throw new IllegalArgumentException("musicItemBinding can't be null");
            mMusicItemBinding = musicItemBinding;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context parentContext = parent.getContext();
        MusicItemBinding binding =
                MusicItemBinding.inflate(LayoutInflater.from(parentContext), parent, false);
        final View root = binding.getRoot();
        root.setOnClickListener(v -> mInputListener.onClick(v));
        binding.options.setOnClickListener(v -> mInputListener.onOptionsClick(root, v));
        return new ViewHolder(root, binding);
    }

    /*  calculate time taken by this methode    */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder , int position) {
        MediaDescriptionCompat md = mDataSet.get(position);
        holder.mMediaDescription = md;

        holder.mMusicItemBinding.textviewTitle.setText(md.getTitle());

        String artist = md.getExtras().getString(ServiceMediaPlayback.MDEK_ARTIST);
        holder.mMusicItemBinding.textviewArtist.setText(artist);

        Bitmap thumb = md.getIconBitmap();
        if(thumb != null) holder.mMusicItemBinding.displayIcon.setImageBitmap(thumb);
        else holder.mMusicItemBinding.displayIcon.setImageDrawable(mDefaultThumb);
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }
}
