package com.example.mediasession;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.mediasession.annotations.Unsatisfied;
import com.example.mediasession.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    /*  warning if value null throws exception  */

    /* replace icons with own icons and make sure icons width and height are set appropriatly for its use */

    /* try making the queue a seperate activity */

    /* TODO : check system volume if muted notify user */

    private static final String TAG = "MainActivity";

    static final String MAIN_SHARED_VIEW_MODEL_KEY = "main_shared_view_model_key";

    @NonNull private final MutableLiveData<Byte>
            mServiceConnectionStatusLD =new MutableLiveData<Byte>(null);

    //TODO : use TAG field of respective classes
    static final String FRAGMENT_TAG_LOCAL_MEDIA = "frag_local_media";
    static final String FRAGMENT_TAG_PLAYLISTS = "frag_playlists";
    static final String FRAGMENT_TAG_PLAYER = "frag_player";
    static final String FRAGMENT_TAG_QUEUE = "frag_queue";
    static final String FRAGMENT_TAG_ADD_PLAYLIST = "frag_add_playlist";

    /*variables used for state status these variables have to be properly updated*/
    private Fragment mCurrentBNFrag;
    /* _________________ */

    private final int PERMISSION_REQUEST_CODE = 100;

    private ActivityMainBinding mLayoutBinding;

    //Fragments
    private FragmentManager mFragmentManager;

    @Nullable private Fragment.SavedState mLocalMediaFragSavedState;
    @Nullable private Fragment.SavedState mPlaylistsFragSavedState;

    private final ConstraintSet mMainConstraintSet = new ConstraintSet();

    private List<MediaSessionCompat.QueueItem> mCurrentQueue = new ArrayList<>();

    private MainSharedViewModel mMainSharedVM;

    private boolean mIsSavedInstanceStateNull = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIsSavedInstanceStateNull = savedInstanceState == null ? true : false;
        final View view = getLayoutInflater().inflate(R.layout.app_start_screen, null, false);
        //to make sure main layout is not clickable when this is visible
        view.setOnClickListener(v -> {/* do nothin */;});
        setContentView(view);//app initial loading screen

        mFragmentManager = getSupportFragmentManager();

        mMainSharedVM = new ViewModelProvider(this)
                .get(MAIN_SHARED_VIEW_MODEL_KEY, MainSharedViewModel.class);

        mLayoutBinding = ActivityMainBinding.inflate(getLayoutInflater());
        mMainConstraintSet.clone(mLayoutBinding.getRoot());

        // TODO : add default layout

        //to make sure that mini controller is not clickable when its hidden
        mLayoutBinding.bottomNavBg.setOnClickListener(v -> /* do nothing */{;});
        mLayoutBinding.miniPlayerContainer.setOnClickListener(v -> /* do nothing */{;});

        //Bottom Nav
        mLayoutBinding.mainBnHome.setOnClickListener(v -> {
            Toast.makeText(this, "Not added yet :(", Toast.LENGTH_SHORT).show();
        });
        mLayoutBinding.mainBnBrowse.setOnClickListener(v -> {
            Log.e(TAG, "make this a method");

            if(mCurrentBNFrag instanceof LocalMediaFragment) return;
            else mSwitchMainBNFrags(new LocalMediaFragment());
        });

        mLayoutBinding.mainBnCollections.setOnClickListener(v -> {
            if(mCurrentBNFrag instanceof PlaylistsFragment) return;
            else mSwitchMainBNFrags(new PlaylistsFragment());
        });

        //Permission
        //TODO : test by changing the permission at runtime(both activity and service)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            mObserveService(); mSetUpFragment(); setContentView(mLayoutBinding.getRoot());
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else {
                //TODO : check how permissions work before runtime permissions introduced
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_CODE) {
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mObserveService(); mSetUpFragment(); setContentView(mLayoutBinding.getRoot());
            } else {
                Toast.makeText(this, "Need Permission", Toast.LENGTH_LONG).show();
                Log.w(TAG, LT.UNIMPLEMENTED+"Permission denied");
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC); //TODO : check
        /*TODO : Test what happens if app permission changes at runtime*/
    }

    @Override
    protected void onPause() {
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE); //TODO : check
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        //mMainSharedVM.getPlaybackStateLD().removeObservers(this);
        mMainSharedVM.getQueueLD().removeObservers(this);
        //mMainSharedVM.getMetadataLD().removeObservers(this);
        mMainSharedVM.getServiceConnectionLD().removeObservers(this);

        super.onDestroy();
    }

    // ___________________________________________________________________________________________

    void mShowBN(boolean show){
        final boolean onScreen = mMainConstraintSet.getConstraint(R.id.main_bn_collections)
                    .layout.bottomToBottom == ConstraintSet.PARENT_ID;
        if(show){
            if(onScreen) return;
            mMainConstraintSet.connect(R.id.main_bn_collections, ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            mMainConstraintSet.clear(R.id.main_bn_collections, ConstraintSet.TOP);
            mMainConstraintSet.applyTo(mLayoutBinding.getRoot());
            
            Toast.makeText(this, "mShowBN(true)", Toast.LENGTH_LONG);
        } else {
            if(!onScreen) return;
            mMainConstraintSet.connect(R.id.main_bn_collections, ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            mMainConstraintSet.clear(R.id.main_bn_collections, ConstraintSet.BOTTOM);
            mMainConstraintSet.applyTo(mLayoutBinding.getRoot());
            
            Toast.makeText(this, "mShowBN(true)", Toast.LENGTH_LONG);
        }
    }

    /** used to handle bottom nav clicks **/
    @Unsatisfied
    private void mSwitchMainBNFrags(@NonNull Fragment fragment){
        if(fragment == null) throw new IllegalArgumentException();

        // - - - - - - - - - saving state  - - - - - - - - -
        if (mCurrentBNFrag == null) {
            Log.e(TAG, "cant save fragment state ,fragment is null");
        } else if(mCurrentBNFrag instanceof PlaylistsFragment){
            mPlaylistsFragSavedState = mFragmentManager.saveFragmentInstanceState(mCurrentBNFrag);
        } else if(mCurrentBNFrag instanceof LocalMediaFragment){
            mLocalMediaFragSavedState = mFragmentManager.saveFragmentInstanceState(mCurrentBNFrag);
        } else {
            Log.e(TAG, "mSwitchMainBNFrags, cant save fragment state invalid fragment");
        }
        //- - - - - - - - - - - - - - - - - - - - - - - - - -

        //TODO : restore fragment stack

        final FragmentTransaction ft = mFragmentManager.beginTransaction().setReorderingAllowed(true);
        if(fragment instanceof PlaylistsFragment){
            final PlaylistsFragment playlistsFragment = new PlaylistsFragment();
            playlistsFragment.setInitialSavedState(mPlaylistsFragSavedState);
            ft.replace(R.id.main_fragment_container, playlistsFragment, null)
                    .runOnCommit(() -> mUpdateBottomNavActiveElement(PlaylistsFragment.TAG))
                    .commit();
            mCurrentBNFrag = playlistsFragment;
        } else if(fragment instanceof LocalMediaFragment){
            final LocalMediaFragment localMediaFragment = new LocalMediaFragment();
            localMediaFragment.setInitialSavedState(mLocalMediaFragSavedState);
            ft.replace(R.id.main_fragment_container, localMediaFragment, null)
                    .runOnCommit(() -> mUpdateBottomNavActiveElement(LocalMediaFragment.TAG))
                    .commit();
            mCurrentBNFrag = localMediaFragment;
        } else {
            Log.e(TAG, "mSwitchMainBNFrags called with invalid fragment");
        }
    }

    private void mObserveService() throws IllegalStateException{
        if(mMainSharedVM == null) throw new IllegalStateException("ViewModel is null");
        mMainSharedVM.getServiceConnectionLD().observe(this, new Observer<Byte>() {
            @Override public void onChanged(Byte status) {
                switch (status){
                    case 0 /*idle*/:{

                        break;
                    }
                    case ServiceMediaPlayback.SERVICE_CONNECTED:{
                        mMainSharedVM.getQueueLD().observe(MainActivity.this, new Observer<List<MediaSessionCompat.QueueItem>>() {
                            @Override
                            public void onChanged(List<MediaSessionCompat.QueueItem> queueItems) {
                                //Show hide mini player
                                /*
                                TODO : try different implementation with only one instance of MiniPlayerFragment
                                        ,where miniplayer hides/shows itself
                                 */
                                Fragment miniPlayerFrag = mFragmentManager.findFragmentByTag(MiniPlayerFragment.TAG);
                                if(queueItems.isEmpty()){
                                    if(miniPlayerFrag != null){
                                        mFragmentManager.beginTransaction().setReorderingAllowed(true)
                                                .remove(miniPlayerFrag)
                                                .commitNow();
                                    }
                                } else {
                                    if(miniPlayerFrag == null){
                                        mFragmentManager.beginTransaction().setReorderingAllowed(true)
                                                .add(R.id.mini_player_container,
                                                        MiniPlayerFragment.class, null, MiniPlayerFragment.TAG)
                                                .commitNow();
                                    }
                                }
                            }
                        });
                        break;
                    }
                    case ServiceMediaPlayback.SERVICE_SUSPENDED: {
                        //TODO : notify user
                        break;
                    }
                    case ServiceMediaPlayback.SERVICE_FAILED: {
                        //TODO : notify user
                        break;
                    }
                }
            }
        });
    }

    private void mUpdateBottomNavActiveElement(@Nullable String tag){
        //TODO : use better algorithm also make it thread safe
        final Resources res = getResources();
        final int defaultColor = ResourcesCompat.getColor(res, R.color.bottom_nav_text_color, null);
        final int activeColor = ResourcesCompat.getColor(res, R.color.selected_bottom_nav_text_color, null);

        if(tag == null){ tag = ""; }//TODO : test this method with null argument
        if(tag.equals(LocalMediaFragment.TAG)){
            try{
                mLayoutBinding.mainBnHome.setTextColor(defaultColor);
                mLayoutBinding.mainBnCollections.setTextColor(defaultColor);
                mLayoutBinding.mainBnBrowse.setTextColor(activeColor);
            } catch (Exception e) {/*do nothing*/;}
        } else if(tag.equals(PlaylistsFragment.TAG)){
            try{
                mLayoutBinding.mainBnHome.setTextColor(defaultColor);
                mLayoutBinding.mainBnCollections.setTextColor(activeColor);
                mLayoutBinding.mainBnBrowse.setTextColor(defaultColor);
            } catch (Exception e) {/*do nothing*/;}
        } else {
            try{
                mLayoutBinding.mainBnHome.setTextColor(defaultColor);
                mLayoutBinding.mainBnCollections.setTextColor(defaultColor);
                mLayoutBinding.mainBnBrowse.setTextColor(defaultColor);
            } catch (Exception e) {/*do nothing*/;}
        }
    }

    private void mSetUpFragment(){
        if(mIsSavedInstanceStateNull) {
            mSwitchMainBNFrags(new PlaylistsFragment());
        } else {
            //TODO : restore the last fragment stack
        }
    }
}
