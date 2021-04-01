package com.example.mediasession;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediasession.databinding.PlaylistItemBinding;
import com.example.mediasession.databinding.AddPlaylistItemBinding;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.PlaylistItemHolder> {
    @NonNull private List<MediaDescriptionCompat> mDataset = new ArrayList<>();
    private Drawable mDefaultThumb;
    private PlaylistsAdapterInterface mClickListener;

    public PlaylistsAdapter(@NonNull List<MediaBrowserCompat.MediaItem> dataset,
                            @NonNull PlaylistsAdapterInterface listener, @Nullable Drawable defaultThumb) {
        if(dataset == null || listener == null) throw new IllegalArgumentException();
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
    
    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 1 : 0;
    }

    @NonNull
    @Override
    public PlaylistItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if(viewType == 0){
            PlaylistItemBinding binding = PlaylistItemBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false);

            View view = binding.getRoot();
            view.setOnClickListener(v -> mClickListener.onItemClick(v));
            binding.play.setOnClickListener(view1 -> mClickListener.onPlayButtonClick(view));
            return new PlaylistItemHolder(view, binding);
        } else /* if 1 */ {
            AddPlaylistItemBinding addPlaylistBinding = AddPlaylistItemBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false);
            View view = addPlaylistBinding.getRoot();
            view.setOnClickListener(v -> mClickListener.onAddPlaylistClick(v));
            return new PlaylistItemHolder(view, null);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistItemHolder holder, int position) {
        final int newPosition = position-1;
        Log.d("TAG", "position:"+Integer.valueOf(position)+" newPosition:"+Integer.valueOf(newPosition));
        //TODO : ineffecient
        if(newPosition == -1) /* Add Playlist Item */return;
        
        MediaDescriptionCompat md = mDataset.get(newPosition);
        holder.setMediaDescription(md);
        holder.mLayoutBinding.displayName.setText(md.getTitle());
        Bitmap thumb = md.getIconBitmap();
        if(thumb != null) holder.mLayoutBinding.displayIcon.setImageBitmap(thumb);
        else holder.mLayoutBinding.displayIcon.setImageDrawable(mDefaultThumb);
    }

    @Override
    public int getItemCount() {
        return mDataset.size()+1;
    }
}
