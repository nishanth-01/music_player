package com.example.mediasession;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediasession.databinding.FragmentAddPlaylistDialougeBinding;

//TODO : add ways to add add picture and description

public class AddPlaylistDialougeFragment extends Fragment {
    static final String TAG = "AddPlaylistDialougeFragment";

    private FragmentAddPlaylistDialougeBinding mLayoutBinding;
    private MainSharedViewModel mSharedVM;

    public AddPlaylistDialougeFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedVM = new ViewModelProvider(getActivity())
                .get(MainActivity.MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mLayoutBinding = FragmentAddPlaylistDialougeBinding.inflate(inflater, container, false);
        //TODO  AddPlaylistDialougeFragment onCreateView, configure edit textview dont allow new line char
        mLayoutBinding.main.setOnClickListener(view -> /*do nothing*/{;});
        mLayoutBinding.getRoot().setOnClickListener(v -> mRemoveMe());
        mLayoutBinding.createPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CharSequence playlistName = mLayoutBinding.editText.getText();
                mSharedVM.addPlaylist(playlistName.toString());
                Log.w(TAG, LT.IP+ "show loading animation and remove this dialouge when "+
                        "playlist is added");
                mRemoveMe();
            }
        });
        return mLayoutBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() { super.onDestroy(); mSharedVM = null; }

    private void mRemoveMe(){
        final Context context = getContext();
        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        //TODO : check which view should be used to getWindowToken
        imm.hideSoftInputFromWindow(mLayoutBinding.editText.getWindowToken(), 0);

        getParentFragmentManager().beginTransaction().setReorderingAllowed(true)
                .remove(this).commitNow();
    }
}
