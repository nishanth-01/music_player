package com.example.mediasession;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mediasession.databinding.FragmentPlaylistsBinding;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsFragment extends Fragment implements PlaylistsAdapterInterface {
    static final String TAG = "PlaylistsFragment";

    private FragmentPlaylistsBinding mLayoutBinding;
    private MainSharedViewModel mSharedViewModel;
    private Drawable mDefaultDisplayIcon;

    public PlaylistsFragment() { super(); }
    

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedViewModel = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);

        mDefaultDisplayIcon = ResourcesCompat.getDrawable(getResources(),
                R.drawable.ic_default_albumart_thumb, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mLayoutBinding = FragmentPlaylistsBinding.inflate(inflater, container, false);
        mLayoutBinding.mainList.setLayoutManager(new LinearLayoutManager(getContext()));
        mLayoutBinding.mainList.setAdapter(new PlaylistsAdapter(new ArrayList<>(0),
                PlaylistsFragment.this, mDefaultDisplayIcon));
        mSharedViewModel.getPlaylistsLD().observe(this,
                new Observer<List<MediaBrowserCompat.MediaItem>>() {
                    @Override
                    public void onChanged(List<MediaBrowserCompat.MediaItem> mediaItems) {
                        //TODO : handle data change without setting a new adapter
                        mLayoutBinding.mainList.setAdapter(new PlaylistsAdapter(mediaItems,
                                PlaylistsFragment.this, mDefaultDisplayIcon));
                    }
                });
        return mLayoutBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLayoutBinding.mainList.setAdapter(null);
        mLayoutBinding.mainList.setLayoutManager(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSharedViewModel.getPlaylistsLD().removeObservers(this);
        mSharedViewModel = null;
    }


    @Override
    public void onItemClick(View view) {
        //TODO : make sure this not called more than once
        PlaylistsAdapter.PlaylistItemHolder holder =
                (PlaylistsAdapter.PlaylistItemHolder)mLayoutBinding.mainList.getChildViewHolder(view);
        final String mediaId = holder.getMediaDescription().getMediaId();
        FragmentManager fm = getParentFragmentManager();
        fm.executePendingTransactions();
        fm.beginTransaction().addToBackStack(TAG)
                .add(R.id.main_fragment_container,
                        PlaylistMembersFragment.instanceFor(mediaId), PlaylistMembersFragment.TAG)
                .commit();
    }
    
    @Override
    public void onAddPlaylistClick(View view){
        mShowAddPlaylistDialouge();
    }

    private void mShowAddPlaylistDialouge(){
        final FragmentManager fm = getParentFragmentManager();
        fm.executePendingTransactions();
        //TODO : try commit and commitNow
        fm.beginTransaction()
                .addToBackStack(null)
                .add(android.R.id.content, new AddPlaylistDialougeFragment(),
                        MainActivity.FRAGMENT_TAG_ADD_PLAYLIST)
                .commit();
    }
}
