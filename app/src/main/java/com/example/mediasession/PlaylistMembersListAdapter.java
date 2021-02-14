package com.example.mediasession;

import android.graphics.drawable.Drawable;
import android.support.v4.media.MediaBrowserCompat;
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
    private final List<MediaBrowserCompat.MediaItem> mDataSet = new ArrayList<>();
    private PlaylistMembersListAdapterInterface mInputListener;
    private Drawable mDefaultThumb;

    /*optionally remove file formate in display name*/
    /* make this flexible*/

    public PlaylistMembersListAdapter(
            @NonNull List<MediaBrowserCompat.MediaItem> dataSet,
            @NonNull PlaylistMembersListAdapterInterface inputListener, @Nullable Drawable defaultThumnail) {
        if(dataSet ==null) throw new IllegalArgumentException("dataSet can't be null");
        if(inputListener==null) throw new IllegalArgumentException("inputListener can't be null");

        this.mDataSet.addAll(dataSet); this.mInputListener = inputListener;

        mDefaultThumb = defaultThumnail;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private PlaylistMemberItemBinding mLayoutBinding;

        public Holder(@NonNull View itemView, PlaylistMemberItemBinding mLayoutBinding) {
            super(itemView);
            this.mLayoutBinding = mLayoutBinding;
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PlaylistMemberItemBinding binding = PlaylistMemberItemBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding.getRoot(), binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }
}
