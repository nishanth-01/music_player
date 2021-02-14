package com.example.mediasession;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mediasession.databinding.FragmentPlaylistsBinding;

import java.util.List;

public class PlaylistsFragment extends Fragment implements PlaylistsAdapterInterface {
    static final String TAG = "PlaylistsFragment";

    private FragmentPlaylistsBinding mLayoutBinding;
    private MainSharedViewModel mSharedViewModel;

    public PlaylistsFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedViewModel = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mLayoutBinding = FragmentPlaylistsBinding.inflate(inflater, container, false);
        mLayoutBinding.addPlaylistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowAddPlaylistDialouge();
            }
        });
        mLayoutBinding.mainList.setLayoutManager(new LinearLayoutManager(getContext()));
        mSharedViewModel.getPlaylistsLD().observe(this,
                new Observer<List<MediaBrowserCompat.MediaItem>>() {
                    @Override
                    public void onChanged(List<MediaBrowserCompat.MediaItem> mediaItems) {
                        mLayoutBinding.mainList.setAdapter(new PlaylistsAdapter(mediaItems,
                                PlaylistsFragment.this,
                                CompatMethods.getDrawable(getResources(),
                                        R.drawable.ic_default_albumart_thumb, null)));
                    }
                });
        return mLayoutBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        mSharedViewModel.getPlaylistsLD().removeObservers(this);
        mLayoutBinding.mainList.setAdapter(null);
        mLayoutBinding.mainList.setLayoutManager(null);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() { mSharedViewModel = null; super.onDestroy(); }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
    }

    @Override
    public void onItemClick(View view) {
        //TODO : make sure this not called more than once
        PlaylistsAdapter.PlaylistItemHolder holder =
                (PlaylistsAdapter.PlaylistItemHolder)mLayoutBinding.mainList.getChildViewHolder(view);
        final String mediaId = holder.getMediaDescription().getMediaId();
        final PlaylistMembersFragment playlistFragment = new PlaylistMembersFragment();
        final String playlistFragTag = PlaylistMembersFragment.class.getCanonicalName();
        FragmentManager fm = getParentFragmentManager();
        fm.beginTransaction().addToBackStack(TAG)
                .add(R.id.main_fragment_container, new PlaylistMembersFragment(), playlistFragTag)
                .commit();
        fm.executePendingTransactions();
    }

    private void mShowAddPlaylistDialouge(){
        //TODO : try commit and commitNow
        getParentFragmentManager().beginTransaction()
                .addToBackStack(null)
                .add(android.R.id.content, new AddPlaylistDialougeFragment(),
                        MainActivity.FRAGMENT_TAG_ADD_PLAYLIST)
                .commit();
    }
}
