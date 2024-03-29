package com.example.mediasession;

import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mediasession.databinding.FragmentPlaylistMembersBinding;

import java.util.List;

public class PlaylistMembersFragment extends Fragment implements ClickInterface {
    public static final String TAG = PlaylistMembersFragment.class.getCanonicalName();
    private static final String KEY_PLAYLIST_ID = "key_playlist_id";

    private String mPlaylistId;
    private com.example.mediasession.databinding.FragmentPlaylistMembersBinding mLayoutBinding;
    private MainSharedViewModel mSharedVM;
    private Observer<List<MediaBrowserCompat.MediaItem>> mPlaylistObserver;
    
    private PopupMenu mPopup;

    private MediaBrowserCompat.SubscriptionCallback mSubCallback;

    /** dont use this **/
    public PlaylistMembersFragment() {
        super();
    }

    public static PlaylistMembersFragment instanceFor(@NonNull String playlistId){
        if(TextUtils.isEmpty(playlistId)) throw new IllegalArgumentException();
        final PlaylistMembersFragment f = new PlaylistMembersFragment();
        f.mPlaylistId = playlistId;
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            String id;
            id = savedInstanceState.getString(KEY_PLAYLIST_ID, "");
            if(id.equals("")) throw new RuntimeException(); //TODO - IMP : pop this fragment
            else mPlaylistId = id;
        }
        mSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);

        mPlaylistObserver = new Observer<List<MediaBrowserCompat.MediaItem>>() {
            @Override
            public void onChanged(List<MediaBrowserCompat.MediaItem> mediaItems) {
                MediaDescriptionCompat mediaDescription = null;
                for(MediaBrowserCompat.MediaItem mediaItem : mediaItems){
                    MediaDescriptionCompat md = mediaItem.getDescription();
                    if(md.getMediaId().equals(mPlaylistId)) {
                        mediaDescription = md;
                        break;
                    }
                }
                if(mediaDescription != null){
                    mLayoutBinding.playlistName.setText(mediaDescription.getTitle());
                    mSharedVM.subscribePlaylistMember(mediaDescription.getMediaId(), mSubCallback);
                } else {
                    //TODO : notify user the error
                    getParentFragmentManager().popBackStack();
                }
            }
        };
        mSharedVM.getPlaylistsLD().observe(this, mPlaylistObserver);

        mSubCallback = new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId,
                                         @NonNull List<MediaBrowserCompat.MediaItem> children,
                                         @NonNull Bundle options) {
                //TODO : handle data change properly
                if(children.isEmpty()) mLayoutBinding.playPlaylist.setVisibility(View.GONE);
                else mLayoutBinding.playPlaylist.setVisibility(View.VISIBLE);

                mLayoutBinding.mainList.setAdapter(new PlaylistMembersListAdapter(children,
                        PlaylistMembersFragment.this,
                        ResourcesCompat.getDrawable(getResources(),
                                R.drawable.ic_default_albumart_thumb, null)));
            }

            @Override
            public void onError(@NonNull String parentId, @NonNull Bundle options) {
                //TODO : handle properly
                Toast.makeText(getContext(), "Can't load playlist", Toast.LENGTH_SHORT).show();
                getParentFragmentManager().popBackStack();
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mLayoutBinding = FragmentPlaylistMembersBinding.inflate(inflater, container, false);
        mLayoutBinding.playPlaylist.setVisibility(View.GONE);
        mLayoutBinding.mainList.setLayoutManager(new LinearLayoutManager(getContext()));

        mPopup = new PopupMenu(getContext(), mLayoutBinding.options);
        MenuInflater menuInflater = mPopup.getMenuInflater();
        menuInflater.inflate(R.menu.fragment_playlist_members_options, mPopup.getMenu());
        mPopup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.fragment_playlist_members_options_remove_playlist:
                        mSharedVM.removePlaylist(mPlaylistId);
                        return true;
                    default: return false;
                }
            }
        });
        
        mLayoutBinding.options.setOnClickListener(v -> mPopup.show());
        mLayoutBinding.playPlaylist.setOnClickListener(v -> mSharedVM.playPlaylist(mPlaylistId));

        return mLayoutBinding.getRoot();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            mSharedVM.unsubscribePlaylistMember(mPlaylistId, mSubCallback);
        } catch (Exception e){ /* do nothing */ }
    }

    @Override
    public void onDestroy(){
        mSharedVM.getPlaylistsLD().removeObservers(this);
        super.onDestroy(); mSubCallback = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState); outState.putString(KEY_PLAYLIST_ID, mPlaylistId);
    }

    @Override
    public void onClick(View view) {
        PlaylistMembersListAdapter.Holder viewHolder =
                (PlaylistMembersListAdapter.Holder) mLayoutBinding.mainList.getChildViewHolder(view);
        mSharedVM.playFromMediaId(viewHolder.getMediaDescription().getMediaId(), Bundle.EMPTY);
    }

    //TODO : remove
    @Override
    public void onOptionsClick(View rootView, View anchor) {
        PopupMenu membersOptions = new PopupMenu(getContext(), anchor);
        MenuInflater menuInflater = membersOptions.getMenuInflater();
        menuInflater.inflate(R.menu.playlist_member_item_options, membersOptions.getMenu());
        membersOptions.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.playlist_member_item_options_remove:
                        PlaylistMembersListAdapter.Holder viewHolder =
                                (PlaylistMembersListAdapter.Holder)
                                        mLayoutBinding.mainList.getChildViewHolder(rootView);
                        MediaDescriptionCompat md = viewHolder.getMediaDescription();
                        if(md != null)
                            mSharedVM.removePlaylistMemeber(
                                    Integer.valueOf(mPlaylistId), md.getMediaId());
                        return true;
                    default: return false;
                }
            }
        });
        membersOptions.show();
    }
}
