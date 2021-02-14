package com.example.mediasession;

import android.graphics.Bitmap;
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
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediasession.databinding.FragmentPlayerBinding;

public class PlayerFragment extends Fragment {
    private static final String TAG = "PlayerFragment";

    private FragmentPlayerBinding mLayoutBinding ;
    private MainSharedViewModel mMainSharedVM;

    private final ConstraintSet mParentConstraintSet = new ConstraintSet();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setSharedElementEnterTransition(new AutoTransition());

        mMainSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
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
        mLayoutBinding.displayName.setSelected(true);mLayoutBinding.artist.setSelected(true);
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

        mLayoutBinding.controllsPlayPauseButton.setOnClickListener(v -> mMainSharedVM.togglePlayPause());
        mLayoutBinding.controllsNextButton.setOnClickListener(v -> mMainSharedVM.playNext());
        mLayoutBinding.controllsPreviousButton.setOnClickListener(v -> mMainSharedVM.playPrevious());
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.w(LT.IP, "Try using tags to get the right view model");
        mMainSharedVM.getMetadataLD().observe(this, new Observer<MediaMetadataCompat>() {
            @Override
            public void onChanged(MediaMetadataCompat mediaMetadataCompat) {
                String displayName = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
                String artistName = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
                Bitmap albumArt = mediaMetadataCompat.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                if(TextUtils.isEmpty(displayName)) displayName = getString(R.string.mediadescription_default_display_name);
                if(TextUtils.isEmpty(artistName)) artistName = getString(R.string.mediadescription_default_artist_name);
                if(albumArt == null) {
                    mLayoutBinding.albumart.setImageDrawable(CompatMethods
                            .getDrawable(getResources(), R.drawable.ic_default_albumart, null));
                } else mLayoutBinding.albumart.setImageBitmap(albumArt);

                mLayoutBinding.displayName.setText(displayName);mLayoutBinding.artist.setText(artistName);
            }
        });
        mMainSharedVM.getPlaybackStateLD().observe(this, new Observer<PlaybackStateCompat>() {
            @Override
            public void onChanged(PlaybackStateCompat stateCompat) {
                //dont check for null
                final Bundle extras = stateCompat.getExtras();
                boolean playWhenReady = false;
                if(extras!=null){
                    playWhenReady = extras.getBoolean(ServiceMediaPlayback.EXTRAS_KEY_PLAY_WHEN_READY, false);
                }
                int resId = playWhenReady ? R.drawable.exo_controls_pause : R.drawable.exo_controls_play;
                mLayoutBinding.controllsPlayPauseButton
                        .setImageDrawable(CompatMethods.getDrawable(getResources(), resId, null));
            }
        });
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
