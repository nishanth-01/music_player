package com.example.mediasession;

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

import com.example.mediasession.databinding.PlaylistItemBinding;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.PlaylistItemHolder> {
    @NonNull private List<MediaDescriptionCompat> mDataset = new ArrayList<>();
    private Drawable mDefaultThumb;
    private PlaylistsAdapterInterface mClickListener;

    public PlaylistsAdapter(@NonNull List<MediaBrowserCompat.MediaItem> dataset,
                            @NonNull PlaylistsAdapterInterface listener, @Nullable Drawable defaultThumb) {
        if(dataset==null || listener == null) throw new IllegalArgumentException();
        final List<MediaDescriptionCompat> mediaDescriptionList = new ArrayList<>();
        for(MediaBrowserCompat.MediaItem mediaItem : dataset){
            mediaDescriptionList.add(mediaItem.getDescription());
        }
        mDataset.clear(); mDataset.addAll(mediaDescriptionList);
        mDefaultThumb = defaultThumb;
        mClickListener = listener;
    }

    public static class PlaylistItemHolder extends RecyclerView.ViewHolder{
        private PlaylistItemBinding mLayoutBinding;
        private MediaDescriptionCompat mMediaDescription;
        private void setMediaDescription(@NonNull MediaDescriptionCompat mediaDescription){
            mMediaDescription = mediaDescription;
        }

        MediaDescriptionCompat getMediaDescription(){ return mMediaDescription; }

        public PlaylistItemHolder(@NonNull View itemView, PlaylistItemBinding mLayoutBinding) {
            super(itemView); this.mLayoutBinding = mLayoutBinding;
        }
    }

    @NonNull
    @Override
    public PlaylistItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PlaylistItemBinding binding = PlaylistItemBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);

        View view = binding.getRoot();
        view.setOnClickListener(v -> mClickListener.onItemClick(v));
        return new PlaylistItemHolder(view, binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistItemHolder holder, int position) {
        //TODO : ineffecient
        MediaDescriptionCompat md = mDataset.get(position);
        holder.setMediaDescription(md);
        holder.mLayoutBinding.displayName.setText(md.getTitle());
        String noOfSongs = Integer
                .toString(md.getExtras().getInt(Repositary.PEK_NO_OF_SONGS, 0));
        holder.mLayoutBinding.noOfSongs.setText(noOfSongs);
        Bitmap thumb = md.getIconBitmap();
        if(thumb != null) holder.mLayoutBinding.displayIcon.setImageBitmap(thumb);
        else holder.mLayoutBinding.displayIcon.setImageDrawable(mDefaultThumb);
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
