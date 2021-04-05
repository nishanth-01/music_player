package com.example.mediasession;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediasession.databinding.FragmentMiniPlayerBinding;

public class MiniPlayerFragment extends Fragment {
    public static final String TAG = MiniPlayerFragment.class.getCanonicalName();

    private final int ENABLED_BUTTON_ALPHA = 255;
    private final int DISABLED_BUTTON_ALPHA = 255/2;

    private FragmentMiniPlayerBinding mLayout;
    private MainSharedViewModel mMainViewModel;

    private Drawable mPlayIcon;
    private Drawable mPauseIcon;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainViewModel = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);

        Resources resources = getResources();
        mPlayIcon = ResourcesCompat.getDrawable(resources, R.drawable.exo_controls_play, null);
        mPauseIcon = ResourcesCompat.getDrawable(resources, R.drawable.exo_controls_pause, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        FragmentMiniPlayerBinding binding = FragmentMiniPlayerBinding.inflate(inflater, container, false);
        mLayout = binding;
        final View rootView = binding.getRoot();
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getParentFragmentManager().beginTransaction().setReorderingAllowed(true)
                        .addToBackStack(null)
                        .add(android.R.id.content, PlayerFragment.class, null, PlayerFragment.TAG)
                        .commit();
                //TODO : disable button click and enable it after commited
            }
        });
        mLayout.playPause.setOnClickListener(v -> mMainViewModel.togglePlayPause());
        mLayout.playNext.setOnClickListener(v -> mMainViewModel.playNext());
        mLayout.playPrevious.setOnClickListener(v -> mMainViewModel.playPrevious());

        mMainViewModel.getPlaybackStateLD().observe(MiniPlayerFragment.this, playbackState -> {
            if(playbackState.getState() == PlaybackStateCompat.STATE_PLAYING){
                mLayout.playPause.setImageDrawable(mPauseIcon);
            } else if(playbackState.getState() == PlaybackStateCompat.STATE_BUFFERING){
                //TODO
            } else {
                mLayout.playPause.setImageDrawable(mPlayIcon);
            }
        });

        mMainViewModel.getExtrasLD().observe(MiniPlayerFragment.this, bundle -> {
            final boolean hasNext = bundle.getBoolean(ServiceMediaPlayback.EXTRAS_KEY_HAS_NEXT, false);
            final boolean hasPrevious = bundle.getBoolean(ServiceMediaPlayback.EXTRAS_KEY_HAS_PREVIOUS, false);

            if(hasNext == true) mLayout.playNext.setImageAlpha(ENABLED_BUTTON_ALPHA);
            else mLayout.playNext.setImageAlpha(DISABLED_BUTTON_ALPHA);
            if(hasPrevious == true) mLayout.playPrevious.setImageAlpha(ENABLED_BUTTON_ALPHA);
            else mLayout.playPrevious.setImageAlpha(DISABLED_BUTTON_ALPHA);
        });

        mMainViewModel.getMetadataLD().observe(MiniPlayerFragment.this, mediaMetadata -> {
            if(mediaMetadata == null){
                mLayout.title.setText(null); mLayout.title.setSelected(false);
                mLayout.artist.setText(null); mLayout.artist.setSelected(false);
                mLayout.albumrt.setImageDrawable(null);
                return;
            }

            String displayName = null, artistName = null;
            Bitmap displayIcon = null;

            displayName = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
            artistName = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            displayIcon = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);

            if(displayName == null || displayName.isEmpty()) displayName =
                    getString(R.string.mediadescription_default_display_name);
            if(artistName == null || artistName.isEmpty()) artistName =
                    getString(R.string.mediadescription_default_artist_name);

            mLayout.title.setText(displayName); mLayout.title.setSelected(true);
            mLayout.artist.setText(artistName); mLayout.artist.setSelected(true);
            if(displayIcon == null) {
                mLayout.albumrt.setImageDrawable(ResourcesCompat
                        .getDrawable(getResources(), R.drawable.music_item_menu_def_thumb, null));
            } else mLayout.albumrt.setImageBitmap(displayIcon);
        });

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mMainViewModel.getMetadataLD().removeObservers(this);
        mMainViewModel.getPlaybackStateLD().removeObservers(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMainViewModel = null;
    }
}
