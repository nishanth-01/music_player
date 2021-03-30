package com.example.mediasession;

import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mediasession.databinding.FragmentQueueBinding;

import java.util.List;

/*this should be singleton .if not setQueueObserver wont work properly*/
public class QueueFragment extends Fragment {
    private FragmentQueueBinding mLayoutBinding;
    private MainSharedViewModel mMainSharedVM;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
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
    public void onStart() {
        super.onStart();
        mMainSharedVM.getQueueLD().observe(this, new Observer<List<MediaSessionCompat.QueueItem>>() {
            @Override
            public void onChanged(List<MediaSessionCompat.QueueItem> queueItems) {
                Log.w("QueueFragment", LT.TEMP_IMPLEMENTATION);
                mLayoutBinding.list.setAdapter(new QueueListAdapter(queueItems, new QueueListAdapterListener() {
                    @Override
                    public void onRemoveClicked(MediaSessionCompat.QueueItem queueItem) {
                        mMainSharedVM.removeQueueItem(queueItem);
                    }

                    @Override
                    public void onItemClicked(MediaSessionCompat.QueueItem queueItem) {
                        mMainSharedVM.skipToQueueItem(queueItem);
                    }
                }, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_default_albumart_thumb, null)));
            }
        });
        mMainSharedVM.getPlaybackStateLD().observe(this, new Observer<PlaybackStateCompat>() {
            @Override
            public void onChanged(PlaybackStateCompat stateCompat) {
                //dont check for null
                final Bundle extras = stateCompat.getExtras();
                boolean playWhenReady = false;
                if(extras!=null){
                    playWhenReady =
                            extras.getBoolean(ServiceMediaPlayback.EXTRAS_KEY_PLAY_WHEN_READY, false);
                }
                int resId = playWhenReady ? R.drawable.exo_ic_pause_circle_filled
                        : R.drawable.exo_ic_play_circle_filled;
                mLayoutBinding.playPause
                        .setImageDrawable(ResourcesCompat.getDrawable(getResources(), resId, null));
            }
        });
    }

    @Override
    public void onStop() {
        mMainSharedVM.getPlaybackStateLD().removeObservers(this);
        mMainSharedVM.getQueueLD().removeObservers(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        mMainSharedVM = null;
        super.onDestroy();
    }
}
