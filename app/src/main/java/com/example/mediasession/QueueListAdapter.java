package com.example.mediasession;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediasession.databinding.QueueItemBinding;

import java.util.ArrayList;
import java.util.List;

public class QueueListAdapter extends RecyclerView.Adapter<QueueListAdapter.QueueItemHolder> {
    private final List<MediaSessionCompat.QueueItem> mDataset = new ArrayList<>();
    private QueueListAdapterListener mClickListener;

    public QueueListAdapter(@NonNull List<MediaSessionCompat.QueueItem> dataset,
                        @NonNull QueueListAdapterListener clickListener) {
        super();
        if(dataset==null || clickListener==null) throw new IllegalArgumentException();
        this.mDataset.addAll(dataset); this.mClickListener = clickListener;
    }

    static class QueueItemHolder extends RecyclerView.ViewHolder {
        private com.example.mediasession.databinding.QueueItemBinding mLayoutBinding;
        private long mQueueId;

        public long getQueueId() {
            return mQueueId;
        }

        public QueueItemHolder(@NonNull View itemView, QueueItemBinding mLayoutBinding) {
            super(itemView);
            this.mLayoutBinding = mLayoutBinding;
        }
    }

    @NonNull
    @Override
    public QueueItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final QueueItemBinding binding =
                QueueItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new QueueListAdapter.QueueItemHolder(binding.getRoot(), binding);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueItemHolder holder, int position) {
        /*study recyclerview and make onBindViewHolder lighter*/
        final MediaSessionCompat.QueueItem queueItem = mDataset.get(position);

        holder.mLayoutBinding.getRoot().setOnClickListener(v -> mClickListener.onItemClicked(queueItem));
        holder.mLayoutBinding.remove.setOnClickListener(v -> mClickListener.onRemoveClicked(queueItem));

        final MediaDescriptionCompat md = queueItem.getDescription();
        holder.mLayoutBinding.displayName.setText(md.getTitle());
        holder.mLayoutBinding.artistName.setText(md.getExtras()
                .getString(ServiceMediaPlayback.MDEK_ARTIST));
        holder.mLayoutBinding.displayIcon.setImageBitmap(md.getIconBitmap());
        final int songsLeft = mDataset.size() - position;
        holder.mLayoutBinding.position.setText(Integer.toString(songsLeft));
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
