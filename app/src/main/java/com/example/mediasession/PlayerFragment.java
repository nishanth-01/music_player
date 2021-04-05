package com.example.mediasession;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediasession.databinding.FragmentPlayerBinding;

public class PlayerFragment extends Fragment {
    static final String TAG = PlayerFragment.class.getCanonicalName();

    private final int ENABLED_BUTTON_ALPHA = 255;
    private final int DISABLED_BUTTON_ALPHA = 255/2;

    private FragmentPlayerBinding mLayoutBinding ;
    private MainSharedViewModel mMainSharedVM;

    private Drawable mPlayIcon;
    private Drawable mPauseIcon;

    private final ConstraintSet mParentConstraintSet = new ConstraintSet();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setSharedElementEnterTransition(new AutoTransition());
        final Resources resource = getResources();
        mPlayIcon = ResourcesCompat.getDrawable(resource, R.drawable.exo_controls_play, null);
        mPauseIcon = ResourcesCompat.getDrawable(resource, R.drawable.exo_controls_pause, null);

        mMainSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);

        mMainSharedVM.getMetadataLD().observe(this, mediaMetadata -> {
            String displayName = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
            String artistName = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            Bitmap albumArt = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);

            if(displayName == null || displayName.isEmpty())
                displayName = getString(R.string.mediadescription_default_display_name);
            if(artistName == null || artistName.isEmpty())
                artistName = getString(R.string.mediadescription_default_artist_name);
            if(albumArt == null) {
                mLayoutBinding.albumart.setImageDrawable(ResourcesCompat
                        .getDrawable(getResources(), R.drawable.ic_default_albumart, null));
            } else mLayoutBinding.albumart.setImageBitmap(albumArt);

            mLayoutBinding.title.setText(displayName); mLayoutBinding.artist.setText(artistName);
        });

        mMainSharedVM.getPlaybackStateLD().observe(this, playbackState -> {
            if(playbackState.getState() == PlaybackStateCompat.STATE_PLAYING){
                mLayoutBinding.playPause.setImageDrawable(mPauseIcon);
            } else if(playbackState.getState() == PlaybackStateCompat.STATE_BUFFERING){
                //TODO
            } else {
                mLayoutBinding.playPause.setImageDrawable(mPlayIcon);
            }
        });

        mMainSharedVM.getExtrasLD().observe(PlayerFragment.this, bundle -> {
            final boolean hasNext = bundle.getBoolean(ServiceMediaPlayback.EXTRAS_KEY_HAS_NEXT, false);
            final boolean hasPrevious = bundle.getBoolean(ServiceMediaPlayback.EXTRAS_KEY_HAS_PREVIOUS, false);

            if(hasNext == true) mLayoutBinding.playNext.setImageAlpha(ENABLED_BUTTON_ALPHA);
            else mLayoutBinding.playNext.setImageAlpha(DISABLED_BUTTON_ALPHA);
            if(hasPrevious == true) mLayoutBinding.playPrevious.setImageAlpha(ENABLED_BUTTON_ALPHA);
            else mLayoutBinding.playPrevious.setImageAlpha(DISABLED_BUTTON_ALPHA);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mLayoutBinding = FragmentPlayerBinding.inflate(inflater, container, false);//check and change to true
        ViewCompat.setTransitionName(mLayoutBinding.albumart, getString(R.string.transisition_name_albumart));
        return mLayoutBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mLayoutBinding.getRoot().setOnClickListener(null);
        mLayoutBinding.title.setSelected(true); mLayoutBinding.artist.setSelected(true);
        mLayoutBinding.queue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                QueueFragment queueFragment = new QueueFragment();
                getParentFragmentManager().beginTransaction().setReorderingAllowed(true)
                        .addToBackStack(null)
                        .add(android.R.id.content, queueFragment, null)
                        .commit();
            }
        });

        mLayoutBinding.playPause.setOnClickListener(v -> mMainSharedVM.togglePlayPause());
        mLayoutBinding.playNext.setOnClickListener(v -> mMainSharedVM.playNext());
        mLayoutBinding.playPrevious.setOnClickListener(v -> mMainSharedVM.playPrevious());
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        mMainSharedVM.getMetadataLD().removeObservers(this);
        mMainSharedVM.getPlaybackStateLD().removeObservers(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mLayoutBinding.getRoot().setOnClickListener(null);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mMainSharedVM = null;
        super.onDestroy();
    }
}
