package com.example.mediasession;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaBrowserCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import com.example.mediasession.databinding.FragmentPlaylistSelectorBinding;

public class PlaylistSelectorFragment extends Fragment implements ClickListener {
    public static final String TAG = "PlaylistSelectorFragment";
    
    private FragmentPlaylistSelectorBinding mLayoutBinding;
    private MainSharedViewModel mSharedViewModel;
    private FragmentManager mFragmentManager;

    public PlaylistSelectorFragment() { super(); }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedViewModel = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
                
        mFragmentManager = getParentFragmentManager();
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mLayoutBinding = FragmentPlaylistSelectorBinding.inflate(inflater, container, false);
        mLayoutBinding.mainList.setLayoutManager(new LinearLayoutManager(getContext()));
        mSharedViewModel.getPlaylistsLD().observe(this,
                new Observer<List<MediaBrowserCompat.MediaItem>>() {
                    @Override
                    public void onChanged(List<MediaBrowserCompat.MediaItem> mediaItems) {
                        //TODO : handle data change without setting a new adapter
                        mLayoutBinding.mainList.setAdapter(new PlaylistSelectorAdapter(mediaItems,
                                PlaylistSelectorFragment.this, 
                                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_default_albumart_thumb, null)));
                    }
                });
        return mLayoutBinding.getRoot();
    }
    
    @Override
    public void onDestroyView() {
        mLayoutBinding.mainList.setAdapter(null);
        mLayoutBinding.mainList.setLayoutManager(null);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mSharedViewModel.getPlaylistsLD().removeObservers(this);
        mSharedViewModel = null;
        super.onDestroy();
    }
    
    @Override
    public void onItemClick(View view){
        //TODO : popbackstach properly
        MediaDescriptionCompat md = mGetDescriptionFromView(view);
        if(md == null){
            //TODO
            return;
        }
        String playlistId = md.getMediaId();
        if(playlistId == null || playlistId.isEmpty()){
            //TODO
        } else {
            final Bundle result = new Bundle(1);
            result.putString(LocalMediaFragment.PLAYLIST_SELECTOR_RESULT_KEY, playlistId);
            mFragmentManager.setFragmentResult(
                        LocalMediaFragment.PLAYLIST_SELECTOR_REQUEST_KEY, result);
        }
        
        mFragmentManager.popBackStack();
    }
    
    @Nullable
    private MediaDescriptionCompat mGetDescriptionFromView(View v){
        PlaylistSelectorAdapter.ViewHolder viewHolder =
                (PlaylistSelectorAdapter.ViewHolder) mLayoutBinding.mainList.getChildViewHolder(v);
        return viewHolder.getMediaDescription();
    }
}
