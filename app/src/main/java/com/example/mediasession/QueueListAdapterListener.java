package com.example.mediasession;

import android.support.v4.media.session.MediaSessionCompat;

public interface QueueListAdapterListener {
    void onRemoveClicked(MediaSessionCompat.QueueItem queueItem);
    void onItemClicked(MediaSessionCompat.QueueItem queueItem);
}
