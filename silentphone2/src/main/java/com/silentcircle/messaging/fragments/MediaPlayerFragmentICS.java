/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import com.silentcircle.logs.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;

import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.views.MediaPlayerWrapper;
import com.silentcircle.silentphone2.R;

import java.io.File;
import java.io.IOException;

/**
 * Media Player fragment.
 */
public class MediaPlayerFragmentICS extends FileViewerFragment implements
        MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, TextureView.SurfaceTextureListener,
        View.OnClickListener {

    private static final String TAG = MediaPlayerFragmentICS.class.getSimpleName();

    private static final String CURRENT_POSITION =
            "com.silentcircle.messaging.fragments.MediaPlayerFragmentICS.currentPosition";


    public static MediaPlayerFragmentICS create(Uri uri, String mimeType) {
        return instantiate(new MediaPlayerFragmentICS(), uri, mimeType);
    }

    private static String extractMetadata(MediaMetadataRetriever metaData, int metaDataKey) {
        return metaData.extractMetadata(metaDataKey);
    }

    protected static final int CONTROLS_TIMEOUT = 3000;

    private class MediaControllerWithTimeout extends MediaController {

        public MediaControllerWithTimeout(final Context context) {
            super(context);
        }

        @Override
        public void show() {
            show(mControllerTimeout);
        }

        @Override
        public void show(int timeout) {
            super.show(mControllerTimeout);
        }

        @Override
        public void hide() {
            if (mControllerTimeout > 0) {
                super.hide();
            }
        }

        public void forceHide() {
            super.hide();
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                    && event.getAction() == KeyEvent.ACTION_UP) {
                getActivity().onBackPressed();
            }
            return super.dispatchKeyEvent(event);
        }
    }

    private int mControllerTimeout = CONTROLS_TIMEOUT;

    private RelativeLayout mPlayerContainer;
    private TextureView mTextureView;
    private MediaControllerWithTimeout mController;
    private SurfaceTexture mTexture;
    private MediaPlayer mPlayer;
    private MediaPlayerWrapper mPlayerWrapper;
    private int mSavedPosition = 0;

    protected void createController() {
        Log.d(TAG, "createController");
        mController = new MediaControllerWithTimeout(getActivity());
        mController.setAnchorView(getUnwrappedView());
        mController.setMediaPlayer(mPlayerWrapper);
        mController.setEnabled(true);
        mControllerTimeout = MIME.isAudio(getType()) ? 0 : CONTROLS_TIMEOUT;
    }

    protected void createPlayer() {
        Log.d(TAG, "createPlayer");
        mPlayer = new MediaPlayer();
        mPlayerWrapper = new MediaPlayerWrapper(mPlayer);
        mPlayerWrapper.setOnEventListener(new MediaPlayerWrapper.OnEventListener() {
            @Override
            public void onStart() {
                if (MIME.isVideo(getType())) {
                    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }

            @Override
            public void onPause() {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        try {
            AttachmentUtils.makePublic(getActivity(), getFile());

            mPlayer.setDataSource(getFileDescriptor(getActivity()).getFileDescriptor());
            mPlayer.setSurface(new Surface(mTexture));
            mPlayer.setOnBufferingUpdateListener(mPlayerWrapper);
            mPlayer.setOnPreparedListener(this);
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.prepareAsync();
        } catch (IOException exception) {
            dispatchError();
        }
    }

    protected boolean hasActivity() {
        return getActivity() != null;
    }

    protected boolean hasView() {
        return getView() != null;
    }

    protected void hideController() {
        if (mController != null) {
            mController.forceHide();
        }
    }

    protected boolean isChangingConfigurations() {
        return getActivity().isChangingConfigurations();
    }

    protected boolean isFinishing() {
        Activity activity = getActivity();
        return activity == null || activity.isFinishing();
    }

    @Override
    public void onClick(View view) {
        showController();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.messaging_video_player_fragment, container, false);
        if (savedInstanceState != null) {
            mSavedPosition = savedInstanceState.getInt(CURRENT_POSITION);
        }
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPlayerContainer = (RelativeLayout) view.findViewById(R.id.player_container);
    }

    @Override
    public void onDestroyView() {
        mPlayerContainer = null;
        super.onDestroyView();
    }

    @Override
    public boolean onError(MediaPlayer player, int what, int extra) {
        dispatchError();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer player) {
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (mController != null) {
            mController.invalidate();
            mPlayerWrapper.seekTo(0);
            if (MIME.isAudio(getType())) {
                mController.show(mControllerTimeout);
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer player) {

        getUnwrappedView().post(new Runnable() {

            @Override
            public void run() {
                resetViewBounds();
                createController();
                play();
                showController();
            }

        });

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        View rootView = getView();
        if (rootView != null) {
            rootView.setOnClickListener(this);
        }
        configureTextureView(rootView);

        if (mTexture != null) {
            if (mPlayer == null) {
                createPlayer();
            } else {
                resetViewBounds();
                createController();
            }
        }

        if (MIME.isAudio(getType())) {
            showAudioMetaData();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mTexture != null) {
            if (mPlayer != null) {
                ViewTreeObserver observer = mTextureView.getViewTreeObserver();
                observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

                    @Override
                    public void onGlobalLayout() {
                        Log.v(TAG,
                                String.format("new width=%d; new height=%d", mTextureView.getWidth(),
                                        mTextureView.getHeight()));
                        if (mTexture != null) {
                            if (mPlayer != null) {
                                resetViewBounds();
                            }
                        }
                        mTextureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            }
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        // Keep audio playing if the screen turns off
        if (MIME.isAudio(getType()) && !isFinishing()) {
            PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
                if (!pm.isScreenOn()) {
                    return;
                }
            } else {
                if (!pm.isInteractive()) {
                    return;
                }
            }
        }

        tearDown();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        tearDown();
    }

    private void tearDown() {
        hideController();

        if (mController != null) {
            mController.hide();
            mController.setAnchorView(null);
            mController = null;

            View view = getUnwrappedView();
            if (view != null) {
                view.setOnClickListener(null);
            }
        }

        if (!isChangingConfigurations()) {
            if (mPlayer != null) {
                mSavedPosition = mPlayer.getCurrentPosition();
                if (mPlayer.isPlaying()) {
                    mPlayer.stop();
                }
                mPlayer.release();
                mPlayer = null;
            }
        }

        if (mPlayerContainer != null) {
            mPlayerContainer.removeView(mTextureView);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_POSITION, mSavedPosition);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        mTexture = texture;
        if (isResumed()) {
            if (mPlayer == null) {
                createPlayer();
            } else {
                createController();
            }
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        mTexture = texture;
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        mTexture = texture;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        mTexture = texture;
    }

    protected void play() {
        if (mPlayerWrapper != null) {
            if (mSavedPosition == 0) {
                if (MIME.isVideo(getType())) {
                    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }

                mPlayerWrapper.start();
            }
            else {
                mPlayerWrapper.seekTo(mSavedPosition);
            }
        }
    }

    protected void resetViewBounds() {
        if (mPlayer != null) {
            setViewBounds(mPlayer.getVideoWidth(), mPlayer.getVideoHeight());
        }
    }

    private void setViewBounds(int videoWidth, int videoHeight) {
        int viewWidth = mTextureView.getWidth();
        int viewHeight = mTextureView.getHeight();
        if (viewWidth == 0) {
            return;
        }
        double viewAspectRatio = (double) viewWidth / viewHeight;
        double aspectRatio = (double) videoWidth / videoHeight;

        int newWidth, newHeight;
        if (viewAspectRatio < aspectRatio) {
            // limited by narrow width; set width and restrict height; black bars on top/bottom
            newWidth = viewWidth;
            newHeight = (int) (viewWidth / aspectRatio);
        } else {
            // limited by short height; set height and restrict width; black bars on the sides
            newWidth = (int) (viewHeight * aspectRatio);
            newHeight = viewHeight;
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;

        Matrix txform = new Matrix();
        mTextureView.getTransform(txform);
        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
        txform.postTranslate(xoff, yoff);
        mTextureView.setTransform(txform);
    }

    protected void showAudioMetaData() {
        showAudioMetaData(getFile());
    }

    protected void showAudioMetaData(File file) {


            MediaMetadataRetriever metaData = new MediaMetadataRetriever();

            try {
                metaData.setDataSource(file.getAbsolutePath());
            } catch (RuntimeException exception) {
                return;
            }

            String title = extractMetadata(metaData, MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = extractMetadata(metaData, MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = extractMetadata(metaData, MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String subtitle = artist == null && album == null ? null : artist == null ? album : album == null ? artist : String.format("%s - %s", artist, album);


            ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
            if (actionBar != null) {
                if (title != null) {
                    actionBar.setTitle(title);
                }

                if (subtitle != null) {
                    actionBar.setSubtitle(subtitle);
                }
            }
            showEmbeddedPicture(metaData, R.id.artwork);
    }

    protected void showController() {
        if (mController != null) {
            if (isResumed() && isAdded() && isVisible() && hasActivity() && hasView()) {
                if (!(isDetached() || isChangingConfigurations() || isFinishing() || isRemoving())) {
                    mController.show(mControllerTimeout);
                }
            }
        }
    }

    protected void configureTextureView(View rootView) {
        if (rootView != null) {
            mTextureView = new TextureView(getActivity());
            setTextureViewParams(mTextureView);
            mPlayerContainer.addView(mTextureView);
            mTextureView.setSurfaceTextureListener(this);
            updateSurfaceTexture(mTextureView);
        }
    }

    private void setTextureViewParams(TextureView textureView) {
        RelativeLayout.LayoutParams params =
                new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        textureView.setLayoutParams(params);
    }

    private void showEmbeddedPicture(MediaMetadataRetriever metaData, int viewResourceID) {
        ImageView view = (ImageView) findViewById(viewResourceID);
        if (view != null) {
            byte[] rawArtwork = metaData.getEmbeddedPicture();
            if (rawArtwork != null) {
                Bitmap artwork = BitmapFactory.decodeByteArray(rawArtwork, 0, rawArtwork.length);
                view.setImageBitmap(artwork);
            }
        }
    }

    private void updateSurfaceTexture(TextureView view) {
        if (mTexture != null) {
            view.setSurfaceTexture(mTexture);
        }
    }
}

