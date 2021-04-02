package com.example.mediasession;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediasession.databinding.FragmentLocalMediaBinding;

import java.util.ArrayList;
import java.util.List;

public class LocalMediaFragment extends Fragment implements ClickInterface {
    //TODO : notify user is list is empty
    public static final String TAG = "LocalMediaFragment";
    static final String PLAYLIST_SELECTOR_RESULT_KEY = "playlist_selector_result_key";
    static final String PLAYLIST_SELECTOR_REQUEST_KEY = "playlist_selector_request_key";
    
    private FragmentManager mFragmentManger;
    private FragmentResultListener mPlaylistSelectorResultListener;
    private FragmentLocalMediaBinding mLayout;
    private MainSharedViewModel mMainSharedVM;
    private final List<MediaBrowserCompat.MediaItem> mDataSet = new ArrayList<>();
    
    private String mSelectedItemId;

    private RecyclerView.OnScrollListener mOnScrollListener;
    private int mListPositionY;

    public LocalMediaFragment() { super(); }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
                
        mFragmentManger = getParentFragmentManager();

        mOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                //Log.d(TAG, "dx:"+Integer.valueOf(dx)+" dy:"+Integer.valueOf(dy));
                //if(dy > -1) mLayout.searchBarHolder.setVisibility(View.GONE);
                //else mLayout.searchBarHolder.setVisibility(View.VISIBLE);
            }
        };
        
        mPlaylistSelectorResultListener = new FragmentResultListener(){
            @Override
            public void onFragmentResult(String requestKey, Bundle result){
                switch(requestKey){
                    case LocalMediaFragment.PLAYLIST_SELECTOR_REQUEST_KEY : {
                        String playlistIdString = result.getString(PLAYLIST_SELECTOR_RESULT_KEY, "");
                        int playlistId = 0;
                        try{
                            playlistId = Integer.valueOf(playlistIdString);
                        } catch (NumberFormatException e){
                            //TODO
                        }
                        
                        if(mSelectedItemId != null){
                            MediaBrowserCompat.CustomActionCallback callBack = new MediaBrowserCompat.CustomActionCallback() {
                                @Override
                                public void onProgressUpdate(String action, Bundle extras, Bundle data) {
                                    Log.w(TAG, " addPlaylistMemeber onProgressUpdate");
                                }
                                @Override
                                public void onResult(String action, Bundle extras, Bundle resultData) {
                                    Log.w(TAG, " addPlaylistMemeber onResult");
                                }
    
                                @Override
                                public void onError(String action, Bundle extras, Bundle data) {
                                    Log.w(TAG, " addPlaylistMemeber onError");
                                }
                            };
                            mMainSharedVM.addPlaylistMemeber(playlistId, mSelectedItemId, callBack);
                        } else {
                            //TODO
                        }
                        
                        mFragmentManger.clearFragmentResult(PLAYLIST_SELECTOR_RESULT_KEY);
                        mFragmentManger.clearFragmentResultListener(PLAYLIST_SELECTOR_RESULT_KEY);
                    }
                    
                    default : return;
                }
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mLayout = FragmentLocalMediaBinding.inflate(inflater, container, false);
        mLayout.mainList.setLayoutManager(new LinearLayoutManager(getContext()));

        mMainSharedVM.getAllLocalMediaLD().observe(this,
                new Observer<List<MediaBrowserCompat.MediaItem>>() {
                    @Override
                    public void onChanged(List<MediaBrowserCompat.MediaItem> mediaItems) {
                        //TODO : handle empty media properly

                        //TODO : handle data change properly currently resets the adapter if data changed
                        mDataSet.clear(); mDataSet.addAll(mediaItems);
                        mLayout.mainList.setAdapter(new LocalMediaListAdapter(mDataSet,
                                LocalMediaFragment.this,
                                ResourcesCompat.getDrawable(getResources(),
                                        R.drawable.ic_default_albumart_thumb, null)));

                        mLayout.mainList.addOnScrollListener(mOnScrollListener);
                    }
                });
        return mLayout.getRoot();
    }

    @Override
    public void onDestroyView() {
        mLayout.mainList.removeOnScrollListener(mOnScrollListener);
        mMainSharedVM.getAllLocalMediaLD().removeObservers(this);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mMainSharedVM = null;
        super.onDestroy();
    }

    //Touch Listener

    @Override
    public void onClick(View v) {
        //add timeout for each click

        LocalMediaListAdapter.ViewHolder viewHolder =
                (LocalMediaListAdapter.ViewHolder) mLayout.mainList.getChildViewHolder(v);
        String mediaId = viewHolder.getMediaDescription().getMediaId();
        if(mMainSharedVM != null) mMainSharedVM.playFromMediaId(mediaId, null);
        //TODO : add else
    }

    @Override
    public void onOptionsClick(View rootView, View anchor) {
        LocalMediaListAdapter.ViewHolder viewHolder =
                (LocalMediaListAdapter.ViewHolder) mLayout.mainList.getChildViewHolder(rootView);
        final MediaDescriptionCompat md = viewHolder.getMediaDescription();
        if(md == null) { /* TODO : notify error */return; }
        mSelectedItemId = md.getMediaId();
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.music_item_options, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.music_item_menu_add_to_queue:
                        mMainSharedVM.addToQueue(md);
                        return true;
                    case R.id.music_item_menu_add_to_playlist:
                        mShowPlaylistSelector();
                        return true;
                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    private void mShowPlaylistSelector(){
        mFragmentManger.setFragmentResultListener(
                    LocalMediaFragment.PLAYLIST_SELECTOR_REQUEST_KEY, 
                            this, mPlaylistSelectorResultListener);
        
        mFragmentManger.executePendingTransactions();
        //TODO : try commit and commitNow
        mFragmentManger.beginTransaction()
                .addToBackStack(null)
                .add(android.R.id.content, new PlaylistSelectorFragment(),
                        PlaylistSelectorFragment.TAG)
                .commit();
    }
}
