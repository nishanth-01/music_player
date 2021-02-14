package com.example.mediasession;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mediasession.databinding.FragmentPlaylistMembersBinding;

import java.util.List;

public class PlaylistMembersFragment extends Fragment implements PlaylistMembersListAdapterInterface {
    static final String TAG = PlaylistMembersFragment.class.getSimpleName();
    private static final String KEY_PLAYLIST_ID = "key_playlist_id";

    private String mPlaylistId;
    private FragmentPlaylistMembersBinding mLayoutBinding;
    private MainSharedViewModel mSharedVM;

    private MediaBrowserCompat.SubscriptionCallback mSubCallback;

    public PlaylistMembersFragment() { super(); }

    public PlaylistMembersFragment(@NonNull String playlistId) {
        super();
        if(TextUtils.isEmpty(playlistId)) throw new IllegalArgumentException();
        this.mPlaylistId = playlistId;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            String id;
            id = savedInstanceState.getString(KEY_PLAYLIST_ID, "");
            if(id.equals("")) throw new RuntimeException(getClass().getCanonicalName()+
                    " onCreate, savedInstanceState bundle doesn't contain the playlist id");
            else mPlaylistId = id;
        }
        mSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mLayoutBinding = FragmentPlaylistMembersBinding.inflate(inflater, container, false);
        mLayoutBinding.mainList.setLayoutManager(new LinearLayoutManager(getContext()));

        return mLayoutBinding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(mSubCallback == null){
            mSubCallback = new MediaBrowserCompat.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(@NonNull String parentId,
                                             @NonNull List<MediaBrowserCompat.MediaItem> children,
                                             @NonNull Bundle options) {
                    super.onChildrenLoaded(parentId, children, options);

                    //TODO :
                    Log.w(TAG, "onCreateView "+LT.TEMP_IMPLEMENTATION+"handle data change properly");
                    mLayoutBinding.mainList.setAdapter(new PlaylistMembersListAdapter(children,
                            PlaylistMembersFragment.this,
                            CompatMethods.getDrawable(getResources(), R.drawable.ic_default_albumart_thumb, null)));
                }

                @Override
                public void onError(@NonNull String parentId, @NonNull Bundle options) {
                    super.onError(parentId, options);
                }
            };
        }
        mSharedVM.mSubscribePlaylistMember(mPlaylistId, mSubCallback);
    }

    @Override
    public void onStop() {
        mSharedVM.mUnsubscribePlaylistMember(mPlaylistId, mSubCallback);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(KEY_PLAYLIST_ID, mPlaylistId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View view) {

    }

    @Override
    public void onOptionsClick(View view) {

    }
}
