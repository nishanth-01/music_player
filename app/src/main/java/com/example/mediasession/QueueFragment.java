package com.example.mediasession;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mediasession.databinding.FragmentQueueBinding;

/*this should be singleton .if not setQueueObserver wont work properly*/
public class QueueFragment extends Fragment implements QueueListAdapterListener {
    public static final String TAG = QueueFragment.class.getCanonicalName();

    private final int ENABLED_BUTTON_ALPHA = 255;
    private final int DISABLED_BUTTON_ALPHA = 255/2;

    private Drawable mPlayIcon;
    private Drawable mPauseIcon;

    private FragmentQueueBinding mLayoutBinding;
    private MainSharedViewModel mMainSharedVM;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Resources resources = getResources();
        mPlayIcon = ResourcesCompat.getDrawable(resources, R.drawable.exo_ic_play_circle_filled, null);
        mPauseIcon = ResourcesCompat.getDrawable(resources, R.drawable.exo_ic_pause_circle_filled, null);

        mMainSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);

        mMainSharedVM.getExtrasLD().observe(QueueFragment.this, bundle -> {
            final Bundle b = bundle.getBundle(ServiceMediaPlayback.EXTRAS_KEY_TRANSPORTS_CONTROLS_BUNDLE);

            boolean canPlayNext = false;
            boolean canPlayPrevious = false;
            boolean canPlay = false;
            if(b != null){
                canPlayNext     = b.getBoolean(ServiceMediaPlayback.TRANSPORTS_CONTROLS_BUNDLE_KEY_HAS_NEXT, false);
                canPlayPrevious = b.getBoolean(ServiceMediaPlayback.TRANSPORTS_CONTROLS_BUNDLE_KEY_HAS_PREVIOUS, false);
                canPlay         = b.getBoolean(ServiceMediaPlayback.TRANSPORTS_CONTROLS_BUNDLE_KEY_CAN_PLAY, false);
            }

            if(canPlay) mLayoutBinding.playPause.setImageAlpha(ENABLED_BUTTON_ALPHA);
            else mLayoutBinding.playPause.setImageAlpha(DISABLED_BUTTON_ALPHA);
            if(canPlayNext) mLayoutBinding.playNext.setImageAlpha(ENABLED_BUTTON_ALPHA);
            else mLayoutBinding.playNext.setImageAlpha(DISABLED_BUTTON_ALPHA);
            if(canPlayPrevious) mLayoutBinding.playPrevious.setImageAlpha(ENABLED_BUTTON_ALPHA);
            else mLayoutBinding.playPrevious.setImageAlpha(DISABLED_BUTTON_ALPHA);
        });
        mMainSharedVM.getQueueLD().observe(this, queueItems -> {
            //TODO : handle data change properly without changing adapter
            mLayoutBinding.list.setAdapter(new QueueListAdapter(queueItems, QueueFragment.this,
                    ResourcesCompat.getDrawable(getResources(), R.drawable.ic_default_albumart_thumb, null)));
        });
        mMainSharedVM.getPlaybackStateLD().observe(this, stateCompat -> {
            if(stateCompat.getState() == PlaybackStateCompat.STATE_PLAYING){
                mLayoutBinding.playPause.setImageDrawable(mPauseIcon);
            } else if(stateCompat.getState() == PlaybackStateCompat.STATE_BUFFERING){
                //TODO : show buffer ring
            } else {
                mLayoutBinding.playPause.setImageDrawable(mPlayIcon);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mLayoutBinding = FragmentQueueBinding.inflate(inflater, container, false);
        mLayoutBinding.playPause.setOnClickListener(v -> mMainSharedVM.togglePlayPause());
        mLayoutBinding.playNext.setOnClickListener(v -> mMainSharedVM.playNext());
        mLayoutBinding.playPrevious.setOnClickListener(v -> mMainSharedVM.playPrevious());
        mLayoutBinding.list.setLayoutManager(new LinearLayoutManager(container.getContext()));
        return mLayoutBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMainSharedVM.getPlaybackStateLD().removeObservers(this);
        mMainSharedVM.getQueueLD().removeObservers(this);
        mMainSharedVM.getExtrasLD().removeObservers(this);
        mMainSharedVM = null;
    }

    @Override
    public void onRemoveClicked(MediaSessionCompat.QueueItem queueItem) {
        mMainSharedVM.removeQueueItem(queueItem);
    }

    @Override
    public void onItemClicked(MediaSessionCompat.QueueItem queueItem) {
        mMainSharedVM.skipToQueueItem(queueItem);
    }
}
