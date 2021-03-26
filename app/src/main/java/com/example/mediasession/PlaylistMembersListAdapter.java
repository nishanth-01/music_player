package com.example.mediasession;

import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediasession.databinding.PlaylistMemberItemBinding;

import java.util.ArrayList;
import java.util.List;

public class PlaylistMembersListAdapter extends RecyclerView.Adapter<PlaylistMembersListAdapter.Holder> {
    private final List<MediaDescriptionCompat> mDataSet = new ArrayList<>();
    private ClickInterface mInputListener;
    private Drawable mDefaultThumb;

    /* optionally remove file formate in display name */
    /* make this flexible*/

    public PlaylistMembersListAdapter(
            @NonNull List<MediaBrowserCompat.MediaItem> dataSet,
            @NonNull ClickInterface inputListener,
            @Nullable Drawable defaultThumnail) {
        if(dataSet == null) throw new IllegalArgumentException("dataSet can't be null");
        if(inputListener == null) throw new IllegalArgumentException("inputListener can't be null");

        List<MediaDescriptionCompat> mdList = new ArrayList<>();
        for(MediaBrowserCompat.MediaItem mediaItem : dataSet){
            mdList.add(mediaItem.getDescription());
        }
        this.mDataSet.addAll(mdList); this.mInputListener = inputListener;

        mDefaultThumb = defaultThumnail;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private PlaylistMemberItemBinding mLayoutBinding;
        private MediaDescriptionCompat mMediaDescription;
        
        MediaDescriptionCompat getMediaDescription(){
            return mMediaDescription;
        }

        public Holder(@NonNull View itemView, PlaylistMemberItemBinding mLayoutBinding) {
            super(itemView); this.mLayoutBinding = mLayoutBinding;
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PlaylistMemberItemBinding binding = PlaylistMemberItemBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        final View root = binding.getRoot();
        root.setOnClickListener((v) -> mInputListener.onClick(v));
        binding.options.setOnClickListener((v) -> mInputListener.onOptionsClick(root, v));
        return new Holder(root, binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaDescriptionCompat md = mDataSet.get(position);
        holder.mMediaDescription = md;
        
        holder.mLayoutBinding.title.setText(md.getTitle());
        
        String artist = md.getExtras().getString(ServiceMediaPlayback.MDEK_ARTIST);
        holder.mLayoutBinding.textviewArtist.setText(artist);
        
        Bitmap thumb = md.getIconBitmap();
        if(thumb != null) holder.mLayoutBinding.displayIcon.setImageBitmap(thumb);
        else holder.mLayoutBinding.displayIcon.setImageDrawable(mDefaultThumb);
    }

    @Override
    public int getItemCount() { return mDataSet.size(); }
}
