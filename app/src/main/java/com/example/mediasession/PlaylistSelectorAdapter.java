package com.example.mediasession;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaBrowserCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.ArrayList;

import com.example.mediasession.databinding.PlaylistItemBinding;

public class PlaylistSelectorAdapter extends RecyclerView.Adapter<PlaylistSelectorAdapter.ViewHolder> {
    
    @NonNull private List<MediaDescriptionCompat> mDataset = new ArrayList<>();
    private ClickListener mClickListener;
    @Nullable private Drawable mDefaultThumb;
    
    public PlaylistSelectorAdapter(@NonNull List<MediaBrowserCompat.MediaItem> dataset,
                            @NonNull ClickListener listener, @Nullable Drawable defaultThumb) {
        if(dataset == null || listener == null) throw new IllegalArgumentException();
        final List<MediaDescriptionCompat> mediaDescriptionList = new ArrayList<>();
        for(MediaBrowserCompat.MediaItem mediaItem : dataset){
            mediaDescriptionList.add(mediaItem.getDescription());
        }
        mDataset.clear(); mDataset.addAll(mediaDescriptionList);
        mClickListener = listener;
        mDefaultThumb = defaultThumb;
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder{
        private PlaylistItemBinding mLayoutBinding;
        private MediaDescriptionCompat mMediaDescription;

        MediaDescriptionCompat getMediaDescription(){ return mMediaDescription; }

        public ViewHolder(@NonNull View itemView, PlaylistItemBinding mLayoutBinding) {
            super(itemView); this.mLayoutBinding = mLayoutBinding;
        }
    }
    
    @NonNull
    @Override
    public PlaylistSelectorAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PlaylistItemBinding binding = PlaylistItemBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);

        View view = binding.getRoot();
        view.setOnClickListener(v -> mClickListener.onItemClick(v));
        return new PlaylistSelectorAdapter.ViewHolder(view, binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaDescriptionCompat md = mDataset.get(position);
        holder.mMediaDescription = md;
        holder.mLayoutBinding.displayName.setText(md.getTitle());
        Bitmap thumb = md.getIconBitmap();
        if(thumb != null) holder.mLayoutBinding.displayIcon.setImageBitmap(thumb);
        else holder.mLayoutBinding.displayIcon.setImageDrawable(mDefaultThumb);
    }
    
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
