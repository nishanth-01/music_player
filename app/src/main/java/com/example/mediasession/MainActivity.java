package com.example.mediasession;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.pm.PackageManager;
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
    private boolean isSessionActive;
    private boolean mPlayWhenReady;/*state of exoplayer*/
    /**This is not the current state of playerview ,its the required state.
     * true - expanded ; false - minimized ; null - controller not onscreen**/
    private byte mRequiredPlayerViewState;
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

        /*research more and add proper transperent status bar*/
        /*Log.w(TAG ,
                LT.TEMP_IMPLEMENTATION+"research more and add proper transperent status bar");
        View view = getWindow().getDecorView();
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        //window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(0xFF000000);*/

        mLayoutBinding = ActivityMainBinding.inflate(getLayoutInflater());
        mMainConstraintSet.clone(mLayoutBinding.getRoot());

        // TODO : add default layout

        //to make sure that mini controller is not clickable when its hidden
        mLayoutBinding.bnBg.setOnClickListener(v -> /* do nothing */{;});

        //Mini Controller
        mLayoutBinding.miniControllerBg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final PlayerFragment playerFragment = new PlayerFragment();
                mFragmentManager.beginTransaction().setReorderingAllowed(true)
                        .addToBackStack(FRAGMENT_TAG_PLAYER)
                        .add(android.R.id.content, PlayerFragment.class, null, FRAGMENT_TAG_PLAYER)
                        .commit();
                //TODO : disable button click and enable it after commited
            }
        });
        mLayoutBinding.mcPlayPause.setOnClickListener(v -> mMainSharedVM.togglePlayPause());
        mLayoutBinding.mcPlayNext.setOnClickListener(v -> mMainSharedVM.playNext());
        mLayoutBinding.mcPlayPrevious.setOnClickListener(v -> mMainSharedVM.playPrevious());
        //______________

        //Bottom Nav
        mLayoutBinding.mainBnLocalMedia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e(TAG, "make this a method");

                if(mCurrentBNFrag instanceof LocalMediaFragment) return;
                else mSwitchMainBNFrags(new LocalMediaFragment());
            }
        });

        mLayoutBinding.mainBnPlaylists.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCurrentBNFrag instanceof PlaylistsFragment) return;
                else mSwitchMainBNFrags(new PlaylistsFragment());
            }
        });

        //Permission
        Log.w(TAG, LT.UNTESTED+"test by changing the permission at runtime(both activity and service)");
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
        /*Test what happens if app permission changes at runtime*/
    }

    @Override
    protected void onPause() {
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE); //TODO : check
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //TODO : check if call due to orientation change if not UPDATE DATABASE
    }

    @Override
    protected void onDestroy() {
        mMainSharedVM.getPlaybackStateLD().removeObservers(this);
        mMainSharedVM.getQueueLD().removeObservers(this);
        mMainSharedVM.getMetadataLD().removeObservers(this);
        mMainSharedVM.getServiceConnectionLD().removeObservers(this);

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        //TODO : if(mFragmentManager.getBackStackEntryCount() <= 0){ mShowExitConformationDialouge(); return; }
        super.onBackPressed();
    }
// ___________________________________________________________________________________________
    
    private void mUpdateMetadata(@NonNull MediaMetadataCompat metadata)
            throws IllegalArgumentException {
        if(metadata == null) throw new IllegalArgumentException("metadata can't be null");
        String displayName=null,artistName=null;Bitmap displayIcon=null;

        displayName = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
        artistName = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        displayIcon = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);

        if(TextUtils.isEmpty(displayName)) displayName =
                getString(R.string.mediadescription_default_display_name);
        if(TextUtils.isEmpty(artistName)) artistName =
                getString(R.string.mediadescription_default_artist_name);
        mLayoutBinding.mcTitleText.setText(displayName);
        mLayoutBinding.mcArtistText.setText(artistName);
        if(displayIcon == null) {
            mLayoutBinding.mcAlbumrtThumbnail.setImageDrawable(
                    getDrawable(R.drawable.music_item_menu_def_thumb));
        } else mLayoutBinding.mcAlbumrtThumbnail.setImageBitmap(displayIcon);
    }

    private void mShowMiniController(boolean show){
        if(show){
            //mLayoutBinding.miniController.setVisibility(View.VISIBLE);
            mMainConstraintSet.connect(R.id.mini_controller_bg, ConstraintSet.BOTTOM,
                    R.id.main_bn_playlists, ConstraintSet.TOP);
            mMainConstraintSet.clear(R.id.mini_controller_bg, ConstraintSet.TOP);
            mMainConstraintSet.applyTo(mLayoutBinding.getRoot());
            mLayoutBinding.mcTitleText.setSelected(true);
            mLayoutBinding.mcArtistText.setSelected(true);
        } else /*hide*/{
            mMainConstraintSet.connect(R.id.mini_controller_bg, ConstraintSet.TOP,
                    R.id.main_bn_playlists, ConstraintSet.TOP);
            mMainConstraintSet.clear(R.id.mini_controller_bg, ConstraintSet.BOTTOM);
            mMainConstraintSet.applyTo(mLayoutBinding.getRoot());
        }
    }

    void mShowBN(boolean show){
        final boolean onScreen = mMainConstraintSet.getConstraint(R.id.main_bn_playlists)
                .layout.bottomToBottom == ConstraintSet.PARENT_ID;
        if(show){
            if(onScreen) return;
            mMainConstraintSet.connect(R.id.main_bn_playlists, ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            mMainConstraintSet.applyTo(mLayoutBinding.getRoot());
        } else {
            if(!onScreen) return;
            mMainConstraintSet.connect(R.id.main_bn_playlists, ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            mMainConstraintSet.applyTo(mLayoutBinding.getRoot());
        }
    }

    private void mUpdatePlayWhenReady(boolean playWhenReady){
        this.mPlayWhenReady = playWhenReady;
        int resId = playWhenReady?R.drawable.exo_controls_pause:R.drawable.exo_controls_play;
        mLayoutBinding.mcPlayPause.setImageDrawable(getDrawable(resId));
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
            ft.replace(R.id.main_fragment_container, playlistsFragment, null).commitNow();
            mCurrentBNFrag = playlistsFragment;
        } else if(fragment instanceof LocalMediaFragment){
            final LocalMediaFragment localMediaFragment = new LocalMediaFragment();
            localMediaFragment.setInitialSavedState(mLocalMediaFragSavedState);
            ft.replace(R.id.main_fragment_container, localMediaFragment, null).commitNow();
            mCurrentBNFrag = localMediaFragment;
        } else {
            Log.e(TAG, "mSwitchMainBNFrags called with invalid fragment");
        }
    }

    private void mObserveService() throws IllegalStateException{
        if(mMainSharedVM == null) throw new IllegalStateException("ViewModel is null");
        mMainSharedVM.getServiceConnectionLD().observe(this, new Observer<Byte>() {
            @Override public void onChanged(Byte aByte) {
                if(aByte == null) { return; }
                switch (aByte){
                    case ServiceMediaPlayback.SERVICE_CONNECTED:{
                        mMainSharedVM.getPlaybackStateLD().observe(MainActivity.this,
                                new Observer<PlaybackStateCompat>() {
                                    @Override
                                    public void onChanged(PlaybackStateCompat stateCompat) {
                                        //dont check for null
                                        final Bundle extras = stateCompat.getExtras();
                                        boolean playWhenReady = false;
                                        if(extras!=null){
                                            playWhenReady = stateCompat.getExtras().getBoolean(
                                                    ServiceMediaPlayback.EXTRAS_KEY_PLAY_WHEN_READY, false);
                                        }
                                        mUpdatePlayWhenReady(playWhenReady);
                                    }
                                });
                        mMainSharedVM.getMetadataLD().observe(MainActivity.this,
                                new Observer<MediaMetadataCompat>() {
                                    @Override
                                    public void onChanged(MediaMetadataCompat mediaMetadataCompat) {
                                        mUpdateMetadata(mediaMetadataCompat);
                                    }
                                });
                        mMainSharedVM.getQueueLD().observe(MainActivity.this,
                                new Observer<List<MediaSessionCompat.QueueItem>>() {
                                    @Override
                                    public void onChanged(List<MediaSessionCompat.QueueItem> queueItems) {
                                        Log.d(TAG, "minicontroller queue onChanged called");
                                        mCurrentQueue = queueItems;
                                        if(queueItems.isEmpty()){
                                            Log.w(TAG, LT.TEMP_IMPLEMENTATION);
                                            mShowMiniController(false);
                                            mFragmentManager.popBackStackImmediate(FRAGMENT_TAG_PLAYER,
                                                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
                                        } else mShowMiniController(true);
                                    }
                                });
                        break;
                    }
                    case ServiceMediaPlayback.SERVICE_SUSPENDED: {
                        Log.w(TAG, LT.UNIMPLEMENTED + "Service Suspended");
                        mMainSharedVM.getPlaybackStateLD().removeObservers(MainActivity.this);
                        mMainSharedVM.getMetadataLD().removeObservers(MainActivity.this);
                        mMainSharedVM.getQueueLD().removeObservers(MainActivity.this);
                        break;
                    }
                    case ServiceMediaPlayback.SERVICE_FAILED: {
                        Log.w(TAG, LT.UNIMPLEMENTED + "Service Failed");
                        mMainSharedVM.getPlaybackStateLD().removeObservers(MainActivity.this);
                        mMainSharedVM.getMetadataLD().removeObservers(MainActivity.this);
                        mMainSharedVM.getQueueLD().removeObservers(MainActivity.this);
                        break;
                    }
                }
            }
        });
    }

    private void mSetUpFragment(){
        if(mIsSavedInstanceStateNull) {
            mFragmentManager.beginTransaction().setReorderingAllowed(true)
                    .add(R.id.main_fragment_container, new PlaylistsFragment(), null)
                    .commitNow();
        } else {
            //TODO : restore the last fragment stack
        }
    }
}