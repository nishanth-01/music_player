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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mediasession.databinding.FragmentLocalMediaBinding;

import java.util.ArrayList;
import java.util.List;

public class LocalMediaFragment extends Fragment implements LocalMediaFragmentInterface{
    //TODO : notify user is list is empty
    private static final String TAG = "LocalMediaFragment";
    private FragmentLocalMediaBinding mLayout;
    private MainSharedViewModel mMainSharedVM;
    private final List<MediaBrowserCompat.MediaItem> mDataSet = new ArrayList<>();

    public LocalMediaFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
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
                        Log.w(LT.IP, LT.TEMP_IMPLEMENTATION+
                                "handle empty media properly");

                        Log.e(LT.IP , LT.TEMP_IMPLEMENTATION+ "handle data change properly," +
                                "currently resets the adapter if data changed");
                        mDataSet.clear(); mDataSet.addAll(mediaItems);
                        mLayout.mainList.setAdapter(new LocalMediaListAdapter(mDataSet,
                                (LocalMediaFragmentInterface) LocalMediaFragment.this,
                                CompatMethods.getDrawable(getResources(), R.drawable.ic_default_albumart_thumb, null)));
                    }
                });
        return mLayout.getRoot();
    }

    @Override
    public void onDestroyView() {
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

        String mediaId = mGetDescriptionFromView(v).getMediaId();
        if(mMainSharedVM!=null) mMainSharedVM.playFromMediaId(mediaId , null);
    }

    @Override
    public void onOptionsClick(View view) {
        final MediaDescriptionCompat mediaDescription = mGetDescriptionFromView(view);
        final View optionsView = view.findViewById(R.id.options);
        PopupMenu popup = new PopupMenu(getContext(), optionsView);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.music_item, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.music_item_menu_add_to_queue:
                        mMainSharedVM.addToQueue(mediaDescription);
                        return true;
                    case R.id.music_item_menu_add_to_playlist:
                        mMainSharedVM.addPlaylistMemeber(mediaDescription);
                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    @Nullable
    private MediaDescriptionCompat mGetDescriptionFromView(View v){
        LocalMediaListAdapter.ThisViewHolder viewHolder =
                (LocalMediaListAdapter.ThisViewHolder) mLayout.mainList.getChildViewHolder(v);
        return viewHolder.getMediaDescription();
    }
}
