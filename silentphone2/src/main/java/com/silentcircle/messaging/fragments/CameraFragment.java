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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import com.silentcircle.common.util.Size;
import com.silentcircle.logs.Log;


import android.support.design.widget.Snackbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.media.CameraHelper;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.utils.BitmapUtil;
import com.silentcircle.messaging.views.TextView;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.DragGestureDetector;
import com.silentcircle.silentphone2.views.DragScaleGestureDetector;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.silentcircle.silentphone2.R.id.container;

public class CameraFragment extends Fragment{

    public interface CameraFragmentListener {
        void shouldDismissFragment(boolean error);
        void onVideoRecorded(Uri uri);
        void onPhotoCaptured(Uri uri);

        void onMiniModeHeightChanged(int height);
        void onDisplayModeChanged(boolean isMiniMode);

        /**
         * Called after the hiding animation, which occurs after call to
         * {@link #hide()}, is finished.
         *
         * Listener should detach the fragment in the implementation.
         */
        void onFragmentHidden();
    }

    public static final String TAG_CAMERA_FRAGMENT = "com.silentcircle.messaging.camera";
    public static final String URI_KEY = "URI";
    public static final String TYPE_KEY = "TYPE";
    public static final int TYPE_PHOTO = 0;
    public static final int TYPE_VIDEO = 1;

    private static final String TAG = CameraFragment.class.getSimpleName();

    private static final String FILE_DESCRIPTOR_MODE = "rwt";

    private static final int MAX_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private static final String BLACKPHONE_BP1 = "BP1";

    private static final int[] DESIRED_QUALITY_RANKING = {
//            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_CIF,
            CamcorderProfile.QUALITY_QVGA,
            CamcorderProfile.QUALITY_QCIF,
            CamcorderProfile.QUALITY_LOW
    };

    private static final float PHOTO_TARGET_AR = 16.f / 9; // 4.f / 3 // the actual AR is the inverted one
    private static final float PHOTO_MINI_AR = 4.f / 3;
    private static final int JPEG_QUALITY = 90;

    int mType;
    CameraFragmentListener mListener;

    Handler mMainHandler;

    Uri mOutputUri;
    FileDescriptor mOutputFileDescriptor;
    ParcelFileDescriptor mOutputParcelFleDescriptor;

    boolean mIsMiniMode;
    int mFullOffset;
    Matrix mFullModeMatrix;
    Size mFullPhotoFrame;
    int mFullColor;
    int mMiniOffset;
    Matrix mMiniModeMatrix;
    Size mMiniPhotoFrame;
    int mMiniColor;
    ValueAnimator mMatrixAnimator;
    ObjectAnimator mColorAnimator;
    ObjectAnimator mTranslationAnimator;
    FloatEvaluator mTranslationEvaluator = new FloatEvaluator();
    AnimUtils.MatrixEvaluator mMatrixEvaluator = new AnimUtils.MatrixEvaluator();
    ArgbEvaluator mColorEvaluator = new ArgbEvaluator();
    DragScaleGestureDetector mGestureDetector = null;
    private boolean mDragging;
    private float mDragSpeedY;

    FrameLayout mVideoPreviewFrame;
    TextureView mVideoPreviewTextureView;
    Camera.Size mPictureSize;
    Camera.Size mPreviewSize;
    boolean mSurfaceReady;
    boolean mCameraShouldBeEnabled;
    boolean mPlayerShouldBeEnabled;
    boolean mPreviewingPhoto;
    int mPreviewWidth;
    int mPreviewHeight;

    View mRootLayout;
    ImageView mPhotoView;
    View mBlinkLayer;
    View mBottomButtonsLayout;
    int mBackgroundColor;
    ImageButton mRecordStartStopButton;
    ImageButton mCapturePhotoButton;
    ImageButton mCameraFlipButton;
    ImageButton mCameraFlashToggleButton;
    LinearLayout mRecordInfoLayout;
    TextView mCountDownTextView;
    View mRecDot;

    ImageButton mShareButton;
    View mShareButtonFrame;

    MediaRecorder mMediaRecorder;
    RecordingState mCurrentRecordingState = RecordingState.NOT_STARTED;

    CaptureState mCurrentCaptureState = CaptureState.IDLE;

    Camera mCamera;
    int mCameraId;
    CamcorderProfile mCamcorderProfile;
    private int mCameraRotation;


    boolean mMultipleCameras;
    CameraFacing mCameraFacing = CameraFacing.BACK;
    CameraFacing mRequestedFacing = CameraFacing.BACK;
    boolean mCameraSupportsZoom;
    CameraFlash mCameraFlash = CameraFlash.OFF;
    PreviewState mCurrentPreviewState = PreviewState.NOT_STARTED;

    Timer mCountdownTimer;
    int mCountDown;

    boolean mShouldAppearWithAnimation;

    enum CameraFlash {
        OFF(R.drawable.ic_flash_off_dark),
        ON(R.drawable.ic_flash_on_dark),
        AUTO(R.drawable.ic_flash_auto_dark);

        int drawableResourceId;

        CameraFlash(int id) {
            this.drawableResourceId = id;
        }
    }

    enum CameraFacing {
        BACK(0, R.drawable.ic_camera_rear_dark),
        FRONT(1, R.drawable.ic_camera_front_dark);

        int value;
        int drawableResourceId;

        CameraFacing(int value, int id) {
            this.value = value;
            this.drawableResourceId = id;
        }
    }

    enum PreviewState {
        NOT_STARTED,
        STARTED,
        STOPPED
    }

    enum RecordingState {
        NOT_STARTED,
        STARTED,
        STOPPED
    }

    enum CaptureState {
        IDLE,
        CAPTURING
    }

    private MediaPlayer mPlayer;

    // ------------------- Fragment Lifecycle ---------------------------

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        commonOnAttach(getActivity());
    }

    /*
     * Deprecated on API 23
     * Use onAttachToContext instead
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            commonOnAttach(activity);
        }
    }

    private void commonOnAttach(Activity activity) {
        try {
            mListener = (CameraFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if (savedInstanceState != null) {
            // If the fragment was recreated, the appear animation won't run. So, open the camera.
            mCameraShouldBeEnabled = true;
        }
        mType = arguments.getInt(TYPE_KEY);
        Uri outputUri = arguments.getParcelable(URI_KEY);

        if(outputUri == null) {
            Log.e(TAG, "Output URI is null");

            mListener.shouldDismissFragment(true);
            return null;
        }

        mOutputUri = outputUri;

        mMultipleCameras = Camera.getNumberOfCameras() > 1;

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.messaging_camera_fragment, container, false);
        setupViews(view);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        startVideo();
        if (mShouldAppearWithAnimation) {
            mShouldAppearWithAnimation = false;
            startAppearAnimation();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mType == TYPE_VIDEO) {
            if (mCurrentRecordingState == RecordingState.STARTED) {
                completeRecording();
            }
        }
        tearDown();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recycleViewDrawable(mPhotoView);
//        System.out.println("onDestroyView");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mColorAnimator != null) {
            mColorAnimator.cancel();
            mTranslationAnimator.cancel();
            mMatrixAnimator.cancel();
        }
//        System.out.println("onDestroy");
    }

    @Override
    public void onDetach() {
        super.onDetach();
//        System.out.println("onDetach");
    }

    // ------------------- Public interface ---------------------------
    public void appearWithAnimation(boolean animated) {
        mShouldAppearWithAnimation = animated;
        mCameraShouldBeEnabled = !animated;
    }

    public void hide(boolean animated) {
        if (animated) {
            startDisappearAnimation();
        }
        else {
            mListener.onFragmentHidden();
        }
    }

    public boolean isMiniMode() {
        return mIsMiniMode;
    }

    public void onBackPressed() {
        if (mCurrentCaptureState == CaptureState.CAPTURING) {
            // don't handle back while capturing
            return;
        }
        else if (mDragging) {
            return;
        }
        else if (mPlayerShouldBeEnabled) {
            mPlayerShouldBeEnabled = false;
            mCameraShouldBeEnabled = true;
            tearDown();
            transitionToRecordingUI();
            setCurrentRecordingState(RecordingState.NOT_STARTED);
            startVideo();
        }
        else if (mPreviewingPhoto) {
            mPreviewingPhoto = false;
            mCameraShouldBeEnabled = true;
            transitionToRecordingUI();
            startVideo();
        }
        else if (mType == TYPE_PHOTO && mCamera != null && !mIsMiniMode) {
                transitionToMode(true);
        }
        else {
                mListener.shouldDismissFragment(false);
        }
    }

    // ------------------- View methods ---------------------------

    private void startVideo() {
        if (!mSurfaceReady) {
            return;
        }
        if (mCameraShouldBeEnabled) {
            setup();
        }
        else if(mPlayerShouldBeEnabled) {
            setupPlayer();
        }
    }

    private void setupViews(View view) {
        mRootLayout = view.findViewById(R.id.camera_fragment_root);

        mVideoPreviewFrame = (FrameLayout) view.findViewById(R.id.video_preview_frame);
        mVideoPreviewFrame.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mGestureDetector != null) {
                    mGestureDetector.onTouchEvent(event);
                }
                return true;
            }
        });

        mVideoPreviewTextureView = (TextureView) view.findViewById(R.id.video_preview_texture_view);
        mVideoPreviewTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mSurfaceReady = true;
                startVideo();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mSurfaceReady = false;
                tearDown();

                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                //updateCameraIfNecessary();
            }
        });

        mPhotoView = (ImageView) view.findViewById(R.id.photo_playback);
        mBlinkLayer = view.findViewById(R.id.blink_layer);
        mBottomButtonsLayout = view.findViewById(R.id.bottom_buttons_layout);
        mBackgroundColor = ((ColorDrawable) mBottomButtonsLayout.getBackground()).getColor();
        mRecordStartStopButton = (ImageButton) view.findViewById(R.id.video_record_start_stop_button);
        mCapturePhotoButton = (ImageButton) view.findViewById(R.id.video_capture_photo);
        mCameraFlipButton = (ImageButton) view.findViewById(R.id.video_record_flip_button);
        mCameraFlashToggleButton = (ImageButton) view.findViewById(R.id.video_flash_toggle_button);
        mRecordInfoLayout = (LinearLayout) view.findViewById(R.id.video_record_info_layout);
        mCountDownTextView = (TextView) view.findViewById(R.id.video_countdown_text_view);
        mShareButtonFrame = view.findViewById(R.id.share_button_frame);
        mShareButton = (ImageButton) view.findViewById(R.id.share_button);
        mRecDot = view.findViewById(R.id.recording_dot);

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDragging ) {
                    return;
                }
                switch(v.getId()) {
                    case R.id.video_record_start_stop_button:
                        switch(mCurrentRecordingState) {
                            case NOT_STARTED:
                                startRecording();
                                break;
                            case STARTED:
                                completeRecording();
                                break;
                            case STOPPED:
                                completeRecording();
                                break;
                        }
                        break;
                    case R.id.video_capture_photo:
                        switch (mCurrentCaptureState) {
                            case IDLE:
                                capturePhoto();
                                break;
                        }
                        break;

                    case R.id.video_record_flip_button:
                        if (mCurrentCaptureState == CaptureState.CAPTURING) {
                            return;
                        }
                        if (mType == TYPE_VIDEO) {
                            if (mCurrentRecordingState == RecordingState.NOT_STARTED) {
                                flipCamera();
                            }
                        }
                        else {
                            if (mCurrentCaptureState == CaptureState.IDLE) {
                                flipCamera();
                            }
                        }
                        break;

                    case R.id.video_flash_toggle_button:
                        toggleFlash();
                        break;
                    case R.id.share_button:
                        completeShare();
                        break;
                }
            }
        };

        if (mType == TYPE_VIDEO) {
            mCapturePhotoButton.setVisibility(View.GONE);
        }
        else {
            mRecordStartStopButton.setVisibility(View.GONE);
        }
        getActionButton().setOnClickListener(onClickListener);
        getActionButton().setVisibility(View.INVISIBLE);
        getActionButton().setAlpha(0.f);

        mCameraFlashToggleButton.setVisibility(View.INVISIBLE);
        mCameraFlashToggleButton.setAlpha(0.f);
        mCameraFlashToggleButton.setOnClickListener(onClickListener);

        if (mMultipleCameras) {
            mCameraFlipButton.setOnClickListener(onClickListener);
            mCameraFlipButton.setVisibility(View.INVISIBLE);
            mCameraFlipButton.setAlpha(0.f);
        }
        else {
            mCameraFlipButton.setVisibility(View.GONE);
        }

        mShareButton.setOnClickListener(onClickListener);
    }

    private ImageButton getActionButton() {
        return (mType == TYPE_VIDEO) ? mRecordStartStopButton : mCapturePhotoButton;
    }

    private void startAppearAnimation() {
        Rect rect = new Rect();
        mRootLayout.getWindowVisibleDisplayFrame(rect);
        mRootLayout.setTranslationY(rect.height());
        mRootLayout.animate().translationY(0.f).setDuration(350).
                setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                mRootLayout.setTranslationY(0.f);
                mCameraShouldBeEnabled = true;
                // run delayed so that the animation is not paused before its end
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mSurfaceReady) {
                            startVideo();
                        }
                    }
                }, 10);
                animation.removeAllListeners();
            }
        }).start();
    }

    private void startDisappearAnimation() {
        mCameraShouldBeEnabled = false;

        Rect rect = new Rect();
        mRootLayout.getWindowVisibleDisplayFrame(rect);
        int verticalTranslation = rect.bottom - (int) mVideoPreviewFrame.getTranslationY();
        long duration = (mIsMiniMode) ? 250 : 300;
        mRootLayout.animate().translationY(verticalTranslation).setDuration(duration).
                setInterpolator(new DecelerateInterpolator(0.8f)).setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                mListener.onFragmentHidden();
                animation.removeAllListeners();
            }
        }).start();
    }

    private void fadeInBottomButtons() {
        fadeInView(mBottomButtonsLayout);
        fadeInView(getActionButton());
        if (mMultipleCameras) {
            fadeInView(mCameraFlipButton);
        }
    }

    private void fadeOutBottomButtons() {
        fadeOutView(mBottomButtonsLayout);
        fadeOutView(getActionButton());
        if (mMultipleCameras) {
            fadeOutView(mCameraFlipButton);
        }
    }

    private void transitionToPlaybackUI() {
        fadeOutBottomButtons();
        fadeOutView(mCameraFlashToggleButton);
        mShareButtonFrame.setVisibility(View.VISIBLE);
        ViewUtil.setViewHeight(mShareButtonFrame, mBottomButtonsLayout.getHeight());
        mShareButtonFrame.setTranslationY(getActionButton().getHeight());
        mShareButtonFrame.animate().translationY(0.f).setDuration(300).
                setInterpolator(new OvershootInterpolator(2.0f));
        if (mType == TYPE_PHOTO) {
            Size photoFrame = (mIsMiniMode) ? mMiniPhotoFrame : mFullPhotoFrame;
            // set photo view size
            ViewUtil.setViewWidthHeight(mPhotoView, photoFrame.getWidth(), photoFrame.getHeight());
            fadeInView(mPhotoView);
        }
    }

    private void transitionToRecordingUI() {
        mShareButtonFrame.animate().translationY(mBottomButtonsLayout.getHeight())
                .setDuration(300).setInterpolator(new AnticipateInterpolator(2.0f))
                .setListener(new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mShareButtonFrame.setVisibility(View.INVISIBLE);
                        mShareButtonFrame.animate().setListener(null);
                    }

                });

        fadeInBottomButtons();

        if (mType == TYPE_VIDEO) {
            mRecordStartStopButton.setImageResource(R.drawable.ic_videocam_dark);
        } else {
            fadeOutView(mPhotoView);
        }
    }

    private void transitionToMode(boolean isMiniMode) {
        Matrix matrix = (isMiniMode) ? mMiniModeMatrix : mFullModeMatrix;
        float offset = (isMiniMode) ? mMiniOffset : mFullOffset;
        int color = (isMiniMode) ? mMiniColor : mFullColor;

        // animate matrix
        if (mMatrixAnimator != null) {
            mMatrixAnimator.cancel();
        }
        Matrix startMatrix = new Matrix();
        mVideoPreviewTextureView.getTransform(startMatrix);
        mMatrixAnimator = ValueAnimator.ofObject(new AnimUtils.MatrixEvaluator(), startMatrix, matrix);
        mMatrixAnimator.setDuration(300);
        mMatrixAnimator.setInterpolator(new DecelerateInterpolator());
        mMatrixAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mVideoPreviewTextureView.setTransform((Matrix) animation.getAnimatedValue());
                mVideoPreviewTextureView.invalidate();
            }
        });
        mMatrixAnimator.start();


        // animate translation
        if (mTranslationAnimator != null) {
            mTranslationAnimator.cancel();
        }
        mTranslationAnimator = ObjectAnimator.ofFloat(mVideoPreviewFrame, "translationY", offset)
                .setDuration(300);
        mTranslationAnimator.setInterpolator(new DecelerateInterpolator());
        mTranslationAnimator.start();

        // animate color
        if (mColorAnimator != null) {
            mColorAnimator.cancel();
        }
        int startingColor = ((ColorDrawable) mBottomButtonsLayout.getBackground()).getColor();
        mColorAnimator = ObjectAnimator.ofObject(mBottomButtonsLayout, "backgroundColor", new ArgbEvaluator(), startingColor, color)
                .setDuration(300);
        mColorAnimator.start();

        //TODO: this should happen when we actually get to miniMode
        mIsMiniMode = isMiniMode;
        mListener.onDisplayModeChanged(isMiniMode);
    }

    private void cancelAnimators() {
        if (mColorAnimator != null) {
            mColorAnimator.cancel();
            mColorAnimator = null;
            mTranslationAnimator.cancel();
            mTranslationAnimator = null;
            mMatrixAnimator.cancel();
            mMatrixAnimator = null;
        }
    }

    private float getFullToMiniProgressFraction(float distanceY) {
        return (mVideoPreviewFrame.getTranslationY() + distanceY) / mMiniOffset;
    }

    private class MyOnDragListener implements DragGestureDetector.OnDragListener {

        @Override
        public void onDragBegin() {
            if (mCurrentCaptureState != CaptureState.IDLE || mPreviewingPhoto) {
                return;
            }
            mDragging = true;
            cancelAnimators();
        }

        @Override
        public void onDrag(float relativeDistanceX, float relativeDistanceY, float distanceX, float distanceY, float speedX, float speedY) {
            if (mCurrentCaptureState != CaptureState.IDLE || mPreviewingPhoto) {
                return;
            }
            mDragging = true;
            mDragSpeedY = speedY;
            cancelAnimators();
            float fraction = getFullToMiniProgressFraction(relativeDistanceY);
            float fractionLimited = Math.min(Math.max(fraction, 0), 1.f);
            mVideoPreviewTextureView.setTransform(
                    mMatrixEvaluator.evaluate(fractionLimited, mFullModeMatrix, mMiniModeMatrix));
            mVideoPreviewTextureView.invalidate();
            mVideoPreviewFrame.setTranslationY(
                    mTranslationEvaluator.evaluate(fraction, mFullOffset, mMiniOffset));
            mBottomButtonsLayout.setBackgroundColor(
                    (int)mColorEvaluator.evaluate(fractionLimited, mFullColor, mMiniColor)
            );
        }

        @Override
        public void onDragEnd() {
            cancelAnimators();

            mDragging = false;
            float pixelsPerMM = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1,
                    getResources().getDisplayMetrics());
            float speedY = mDragSpeedY / pixelsPerMM; // convert to mm/sec
            // If user flicks, we use the speed to determine the mode. Otherwise, we use the position
            // and snap to the nearest mode.
            boolean toMini = false;
            if (Math.abs(speedY) > 10) {
                toMini = Math.signum(speedY) > 0.f;
            }
            else {
                float fraction = getFullToMiniProgressFraction(0);
                toMini = (fraction > 0.5);
            }
            transitionToMode(toMini);
        }
    }

    private void recycleViewDrawable(ImageView view) {
        Drawable drawable = view.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) {
                mPhotoView.setImageDrawable(null);
                bitmap.recycle();
            }
        }
    }

    private void fadeInView(View view) {
        AnimUtils.fadeIn(view, 400, false);
    }

    private void fadeOutView(final View view) {
        fadeOutView(view, false);
    }

    private void fadeOutView(final View view, boolean resetStart) {
        if (resetStart) {
            view.setAlpha(1.f);
        }
        view.animate().alpha(0.f).setDuration(350).
                setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.INVISIBLE);
                animation.removeAllListeners();
            }
        }).start();
    }

    private void calculateFullModeValues(float cameraAR) {
        /*
         * We want to to render the camera frames inside a frame that fits inside the available
         * preview area  (mVideoPreviewTextureView), which in general has different AR than the
         * camera. Depending on those 2 AR, the frame may fit horizontally or vertically in the available
         * area. If it fits vertically, the buttons will be overlayed. Otherwise they will sit below
         * the preview.
         */
        int frameWidth;
        int frameHeight;
        int buttonHeight;

        float previewAR = (float) mPreviewWidth / mPreviewHeight;

        if (previewAR >= cameraAR) {
            // Frame fills the preview's height. Black bars on the sides. Buttons are overlayed.
            frameHeight = mPreviewHeight;
            frameWidth = (int) (cameraAR * frameHeight);

            buttonHeight = getResources().getDimensionPixelSize(R.dimen.camera_start_stop_button_height);
            mFullColor = mBackgroundColor;
        }
        else {
            // Frame will be placed on the area on top of the buttons. Button height is adjusted.
            frameWidth = mPreviewWidth;
            frameHeight = (int) ((float) mPreviewWidth /cameraAR);

            buttonHeight= mPreviewHeight - frameHeight;
            int minButtonHeight = getResources().getDimensionPixelSize(R.dimen.camera_start_stop_button_min_height);
            if (buttonHeight < minButtonHeight) {
                // If the button gets shorter than what we want, we'll set it to minButtonHeight and
                // adjust the frame size to fit on the area on top of it.
                buttonHeight = minButtonHeight;

                int availableWidth = mPreviewWidth;
                int availableHeight = mPreviewHeight - buttonHeight;
                previewAR = (float) availableWidth/availableHeight;
                if (previewAR >= cameraAR) {
                    frameHeight = availableHeight;
                    frameWidth = (int) (cameraAR * availableHeight);
                }
                else {
                    frameWidth = availableWidth;
                    frameHeight = (int) ((float) availableWidth /cameraAR);
                }
            }
            mFullColor = 0;
        }
        mFullModeMatrix = TextureViewTransform(mVideoPreviewTextureView, frameWidth, frameHeight, frameHeight / 2.f);
        // Adjust the button height. It's height depends on the full mode.
        ViewUtil.setViewHeight(mBottomButtonsLayout, buttonHeight);

        mFullOffset = 0;
        mFullPhotoFrame = new Size(frameWidth, frameHeight);
    }

    private void calculateMiniModeValues(float cameraAR) {
        // The preview AR will match the AR for photos in mini mode.
        float previewAR = PHOTO_MINI_AR;
        int previewWidth = mPreviewWidth;
        int previewHeight = (int) ((float) previewWidth / previewAR);

        int frameWidth;
        int frameHeight;
        // we want the frame to fill the preview and get cropped
        if (previewAR >= cameraAR) {
            frameWidth = mPreviewWidth;
            frameHeight = (int) ((float) mPreviewWidth /cameraAR);
        }
        else {
            frameHeight = previewHeight;
            frameWidth = (int) (cameraAR * frameHeight);
        }
        mMiniModeMatrix = TextureViewTransform(mVideoPreviewTextureView, frameWidth, frameHeight, previewHeight / 2.f);

        mMiniOffset = mPreviewHeight - previewHeight;
        mMiniColor = 0;

        mMiniPhotoFrame = new Size(previewWidth, previewHeight);
    }

    private void updateInterfaceForCameraSize(int width, int height) {
        float cameraAR = (float) width/height;
        // Invert the camera's AR because the activity is is portrait and the preview is rotated.
        cameraAR = 1/cameraAR;

        if (mPreviewHeight == 0) {
            mPreviewWidth = mVideoPreviewTextureView.getWidth();
            mPreviewHeight = mVideoPreviewTextureView.getHeight();
            if (mPreviewHeight == 0) {
                // We still don't know the dimensions of the maximum available area
                return;
            }
        }

        // We want to precalculate some variables for both modes, so that they can be used when
        // transitioning between them.
        calculateFullModeValues(cameraAR);
        calculateMiniModeValues(cameraAR);

        // Apply values in layout according to current mode (mini or full)
        if (mIsMiniMode) {
            mVideoPreviewFrame.setTranslationY(mMiniOffset);
            mVideoPreviewTextureView.setTransform(mMiniModeMatrix);
            mBottomButtonsLayout.setBackgroundColor(mMiniColor);
            ViewUtil.setViewWidthHeight(mPhotoView, mMiniPhotoFrame.getWidth(), mMiniPhotoFrame.getHeight());
        }
        else {
            mVideoPreviewFrame.setTranslationY(mFullOffset);
            mVideoPreviewTextureView.setTransform(mFullModeMatrix);
            mBottomButtonsLayout.setBackgroundColor(mFullColor);
            ViewUtil.setViewWidthHeight(mPhotoView, mFullPhotoFrame.getWidth(), mFullPhotoFrame.getHeight());
        }

        if (getActionButton().getVisibility() != View.VISIBLE) {
            fadeInBottomButtons();
        }

        // Inform listener of the mini-mode height
        if (mType == TYPE_PHOTO) {
            mListener.onMiniModeHeightChanged(mMiniPhotoFrame.getHeight());
        }
    }

    /**
     * Calculates the textureView's transform so that it renders its content on a frame of dimensions
     * [width, height], which will be horizontally centered and translated vertically so that the
     * frame's center is at centerY.
     */
    private Matrix TextureViewTransform(TextureView view, float width, float height, float centerY) {
        float viewWidth = (float) view.getWidth();
        float viewHeight = (float) view.getHeight();
        Matrix matrix = new Matrix();
        matrix.setScale(width/viewWidth, height/viewHeight, viewWidth/2, 0.f);
        float verticalTranslate = centerY - height / 2;
        matrix.postTranslate(0, verticalTranslate);
        return matrix;
    }

    // ------------------- Camera methods ---------------------------

    private void setup() {
        setupCamera();
        if (mType == TYPE_VIDEO) {
            setupRecorder();
        }
        else {
            setupPhotoCapture();
        }
    }

    private void setupCamera() {
        if (mCamera != null) {
            return;
        }
        Map<Integer, Camera.CameraInfo> cameraMap = CameraHelper.getCameras();

        try {
            for (Map.Entry<Integer, Camera.CameraInfo> entry : cameraMap.entrySet()) {
                if (entry.getValue().facing == mCameraFacing.value) {
                    mCameraId = entry.getKey();
                    mCamera = Camera.open(mCameraId);

                    break;
                }
            }

            if (mCamera == null) {
                mCamera = CameraHelper.getDefaultCameraInstance();
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Error when trying to open the camera (probably in use by another application)", exception);

            onError();
            return;
        }

        if (mCamera == null) {
            Log.e(TAG, "Error when trying to open the camera (probably in use by another application)");

            onError();
            return;
        }

        Camera.Parameters parameters = mCamera.getParameters();

        if (mType == TYPE_VIDEO) {
            int quality = 0;
            for (int i = 0; i < DESIRED_QUALITY_RANKING.length; i++) {
                if (CamcorderProfile.hasProfile(mCameraId, DESIRED_QUALITY_RANKING[i])) {
                    quality = DESIRED_QUALITY_RANKING[i];

                    break;
                }
            }
            mCamcorderProfile = CamcorderProfile.get(mCameraId, quality);

            List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize2(supportedPreviewSizes, (double) mCamcorderProfile.videoFrameWidth / mCamcorderProfile.videoFrameHeight);
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            Log.i(TAG, "Preview resolution is "
                    + String.format("%dx%d (ar=%.2f)", optimalSize.width,
                    optimalSize.height,
                    (float) optimalSize.width / optimalSize.height)
                    + " for camera resolution "
                    + String.format("%dx%d (ar=%.2f)", mCamcorderProfile.videoFrameWidth,
                    mCamcorderProfile.videoFrameHeight,
                    (float) mCamcorderProfile.videoFrameWidth / mCamcorderProfile.videoFrameHeight));
            updateInterfaceForCameraSize(optimalSize.width, optimalSize.height);
            mPreviewSize = optimalSize;
        }
        else { // TYPE_PHOTO
            List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();
            List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            float targetAspectRatio = PHOTO_TARGET_AR;
            Camera.Size pictureSize = CameraHelper.getOptimalPictureSize(pictureSizes, targetAspectRatio);
            Camera.Size previewSize = CameraHelper.getOptimalPreviewSize2(supportedPreviewSizes, (float)pictureSize.width/pictureSize.height);
            parameters.setPictureSize(pictureSize.width, pictureSize.height);
            parameters.setPreviewSize(previewSize.width, previewSize.height);
            Log.i(TAG, "Preview resolution is "
                    + String.format("%dx%d (ar=%.2f)", previewSize.width,
                    previewSize.height,
                    (float) previewSize.width / previewSize.height)
                    + " for picture resolution "
                    + String.format("%dx%d (ar=%.2f)", pictureSize.width,
                    pictureSize.height,
                    (float) pictureSize.width / pictureSize.height));
            updateInterfaceForCameraSize(previewSize.width, previewSize.height);

            parameters.setRotation(CameraHelper.getOrientationHint(getActivity(), mCameraId));
            parameters.setJpegQuality(JPEG_QUALITY);

            mPictureSize = pictureSize;
            mPreviewSize = previewSize;
        }

        // Zoom
        mCameraSupportsZoom = parameters.isZoomSupported();
        ScaleGestureDetector.SimpleOnScaleGestureListener scaleListener =  null;
        if (mCameraSupportsZoom) {
            MySimpleOnScaleGestureListener myScaleListener = new MySimpleOnScaleGestureListener();
            myScaleListener.mMaxZoom = parameters.getMaxZoom();
            scaleListener = myScaleListener;
        }
        else {
            scaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener();
        }
        MyOnDragListener dragListener = (mType == TYPE_PHOTO) ? new MyOnDragListener() : null;
        mGestureDetector = new DragScaleGestureDetector(getActivity(), dragListener, scaleListener);

        // Flash
        List<String> flashModes = parameters.getSupportedFlashModes();
        CameraFlash requiredFlashMode = (mType == TYPE_VIDEO) ? CameraFlash.ON : CameraFlash.AUTO;
        if (flashModes != null && flashModes.contains(getFlashParameter(requiredFlashMode))) {
            fadeInView(mCameraFlashToggleButton);
            setCameraFlash(CameraFlash.OFF, false);
        }
        else {
            fadeOutView(mCameraFlashToggleButton);
        }
        // Focus
        List<String> focusModes = parameters.getSupportedFocusModes();
        String desiredFocusMode = (mType == TYPE_VIDEO) ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO :
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
        if (focusModes != null && focusModes.contains(desiredFocusMode)) {
            parameters.setFocusMode(desiredFocusMode);
        }

        mCamera.setParameters(parameters);

        try {
            CameraHelper.setCameraDisplayOrientation(getActivity(), mCameraId, mCamera);
        } catch (RuntimeException exception) {
            Log.i(TAG, "Camera set orientation exception (ignoring)", exception);
        }

        try {
            mCamera.setPreviewTexture(mVideoPreviewTextureView.getSurfaceTexture());
            startPreview();
        } catch (IOException exception) {
            Log.e(TAG, "Set preview exception", exception);

            onError();
        }
    }

    private void setupRecorder() {
        if (mMediaRecorder != null) {
            return;
        }
        mMediaRecorder = new MediaRecorder();

        try {
            mOutputParcelFleDescriptor = getActivity().getContentResolver()
                    .openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE);
            mOutputFileDescriptor = mOutputParcelFleDescriptor.getFileDescriptor();
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Output URI is an invalid file", exception);
        }

        if(mOutputFileDescriptor == null || !mOutputFileDescriptor.valid()) {
            mListener.shouldDismissFragment(true);
        }

        try {
            if(mCamera == null) {
                Log.e(TAG, "Recorder setup fail - no camera");

                tearDown();

                mListener.shouldDismissFragment(true);
                return;
            }

            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);
        } catch(RuntimeException exception) {
            throw exception;
        }

        mCamcorderProfile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
        mCamcorderProfile.audioCodec = MediaRecorder.AudioEncoder.AAC;
        mCamcorderProfile.audioChannels = 1;
        mCamcorderProfile.audioBitRate = Math.min(32000, mCamcorderProfile.audioBitRate);
        mCamcorderProfile.audioSampleRate = Math.min(48000, mCamcorderProfile.audioSampleRate);

        mCamcorderProfile.videoCodec = MediaRecorder.VideoEncoder.H264;
        mCamcorderProfile.videoBitRate = Math.min(500000, mCamcorderProfile.videoBitRate);

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFile(mOutputFileDescriptor);

        mMediaRecorder.setProfile(mCamcorderProfile);

        mMediaRecorder.setOrientationHint(CameraHelper.getOrientationHint(getActivity(), mCameraId));

        try {
            mMediaRecorder.prepare();
            // Lock the camera, so that we can access it (e.g. zooming)
            mCamera.lock();
        } catch(Throwable exception) {
            Log.e(TAG, "Recorder setup exception", exception);

            onError();
        }
    }

    private void setupPhotoCapture() {

    }


    private void startPreview() {
        if(!mCurrentPreviewState.equals(PreviewState.STARTED) && (mCamera != null)) {
            mCamera.startPreview();

            setCurrentPreviewState(PreviewState.STARTED);
        }
    }

    private void stopPreview() {
        if(mCurrentPreviewState.equals(PreviewState.STARTED) && (mCamera != null)) {
            mCamera.stopPreview();

            setCurrentPreviewState(PreviewState.STOPPED);
        }
    }

    private void flipCamera() {
        if(mCameraFacing.equals(CameraFacing.BACK)) {
            mRequestedFacing = CameraFacing.FRONT;
        } else if(mCameraFacing.equals(CameraFacing.FRONT)) {
            mRequestedFacing = CameraFacing.BACK;
        } else {
            // Just in case.
            mRequestedFacing = CameraFacing.BACK;
        }

        mCameraFlipButton.setImageResource(mRequestedFacing.drawableResourceId);

        updateCameraIfNecessary();
    }

    private String getFlashParameter(CameraFlash flash) {
        switch (flash) {
            case OFF:
                return Camera.Parameters.FLASH_MODE_OFF;
            case ON:
                return Camera.Parameters.FLASH_MODE_TORCH;
            case AUTO:
                return Camera.Parameters.FLASH_MODE_AUTO;
            default:
                return null;
        }
    }

    private void toggleFlash() {
        CameraFlash requestedFlash;
        if (mCameraFlash.equals(mCameraFlash.OFF)) {
            requestedFlash = (mType == TYPE_VIDEO)  ? CameraFlash.ON : CameraFlash.AUTO;
        }
        else if(mCameraFlash.equals(mCameraFlash.ON)) {
            requestedFlash = CameraFlash.OFF;
        }
        else if(mCameraFlash.equals(mCameraFlash.AUTO)) {
            requestedFlash = CameraFlash.OFF;
        }
        else {
            requestedFlash = CameraFlash.OFF;
        }
        setCameraFlash(requestedFlash, true);
    }

    private void setCameraFlash(CameraFlash flash, boolean updateCameraParams) {
        if (mCamera == null) {
            return;
        }

        mCameraFlash = flash;
        mCameraFlashToggleButton.setImageResource(mCameraFlash.drawableResourceId);

        if (updateCameraParams) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(getFlashParameter(flash));
            mCamera.setParameters(parameters);
        }
    }

    private void restart() {
        tearDown();
        if (mSurfaceReady) {
            setup();
        }
    }

    private void updateCameraIfNecessary() {
        if(!mCameraFacing.equals(mRequestedFacing)) {
            mCameraFacing = mRequestedFacing;

            restart();
        }
    }

    private void startRecording() {
        try {
            // balance the lock() after MediaRecorder's prepare()
            mCamera.unlock();
            mMediaRecorder.start();
        } catch(Throwable exception) {
            Log.e(TAG, "Recorder setup exception", exception);

            onError();
        }

        Log.i(TAG, "Recording started");

        setCurrentRecordingState(RecordingState.STARTED);

        fadeInView(mRecordInfoLayout);
        Animation recAnimation = AnimUtils.createFlashingAnimation();
        mRecDot.startAnimation(recAnimation);
        mRecordStartStopButton.setImageResource(R.drawable.ic_stop_dark);
        if (mMultipleCameras) {
            fadeOutView(mCameraFlipButton);
        }

        startCountdownTimer();
    }

    private boolean stopRecording() {
        boolean success = true;
        try {
            if(mMediaRecorder != null) {
                mMediaRecorder.stop();
            }

        } catch(RuntimeException exception) {
            Log.e(TAG, "Exception when stopping recording (ignoring)", exception);
            success = false;
        }

        Log.i(TAG, "Recording stopped");

        setCurrentRecordingState(RecordingState.STOPPED);

        stopCountdownTimer();
        fadeOutView(mRecordInfoLayout);
        final Animation endingRecAnimation = mRecDot.getAnimation();
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (endingRecAnimation != null) {
                    endingRecAnimation.cancel();
                }
            }
        }, 350);
        return success;
    }

    private void completeCapturePhoto(boolean success) {
        setCurrentCaptureState(CaptureState.IDLE);
        if (success) {
            mPreviewingPhoto = true;
            tearDown();
            mCameraShouldBeEnabled = false;
            preparePhotoView();
            transitionToPlaybackUI();
        }
        else {
            mCameraShouldBeEnabled = true;
            if (mMultipleCameras) {
                fadeInView(mCameraFlipButton);
            }
        }
    }

    private void capturePhoto() {
        if (mCamera != null) {
            setCurrentCaptureState(CaptureState.CAPTURING);
            Camera.Parameters parameters = mCamera.getParameters();
            if (mIsMiniMode) {
                // In mini mode we handle the orientation in post processing and we re-compress the
                // image
                mCameraRotation = CameraHelper.getOrientationHint(getActivity(), mCameraId);
                parameters.setRotation(0);
                parameters.setJpegQuality(98);
                mCamera.setParameters(parameters);
            }
            else {
                parameters.setRotation(CameraHelper.getOrientationHint(getActivity(), mCameraId));
                parameters.setJpegQuality(JPEG_QUALITY);
                mCamera.setParameters(parameters);
            }
            try {
                mCamera.takePicture(new Camera.ShutterCallback() {

                    @Override
                    public void onShutter() {
                        AnimUtils.blinkView(mBlinkLayer);
//                    MediaActionSound sound = new MediaActionSound();
//                    sound.play(MediaActionSound.SHUTTER_CLICK);
                    }
                }, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(final byte[] data, Camera camera) {
                        // We process the picture after a while, so that we don't block the blink
                        // animation. This is a workaround instead of having the camera on a background
                        // thread
                        mMainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                processPictureData(data);
                            }
                        }, 300);
                    }
                });
            }
            catch (RuntimeException ex) {
                Log.e(TAG, "Failed to take a picture: ", ex);
                final Activity parent = getActivity();
                if (parent != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Snackbar.make(parent.findViewById(R.id.camera_fragment_root), R.string.capture_picture_error,
                                    Snackbar.LENGTH_LONG)
                                    .show();
                        }
                    });
                }
                completeCapturePhoto(false);
            }
        }
    }

    private void processPictureData(byte[] data) {
        boolean success = false;
        if (data != null) {
            try {
                OutputStream stream = getActivity().getContentResolver().openOutputStream(mOutputUri);
                if (mIsMiniMode) {
                    Bitmap source = BitmapUtil.decodeBitmapFromBytes(data);
                    // Swap source width and source height because the photo is rotated 90 or 270 degrees
                    float sourceWidth = source.getHeight();
                    float sourceHeight = source.getWidth();
                    float sAR = sourceWidth / sourceHeight;
                    float outWidth;
                    float outHeight;
                    if (PHOTO_MINI_AR >= sAR) {
                        outWidth = sourceWidth;
                        outHeight = Math.round(sourceWidth / PHOTO_MINI_AR);
                    }
                    else {
                        outHeight = sourceHeight;
                        outWidth = Math.round(PHOTO_MINI_AR * sourceHeight);
                    }

                    Bitmap output = Bitmap.createBitmap((int) outWidth, (int) outHeight, source.getConfig());
                    Canvas canvas = new Canvas(output);

                    Matrix matrix = new Matrix();
                    // rotate
                    matrix.postTranslate(-source.getWidth() / 2, -source.getHeight() / 2);
                    matrix.postRotate(mCameraRotation);
                    matrix.postTranslate(sourceWidth / 2, sourceHeight / 2);
                    // re-center
                    matrix.postTranslate(- (sourceWidth - outWidth) / 2,
                            - (sourceHeight - outHeight) / 2);

                    Paint paint = new Paint();
                    paint.setFilterBitmap(false);
                    canvas.drawBitmap(source, matrix, paint);
                    source.recycle();

                    output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream);
                    output.recycle();
                }
                else {
                    stream.write(data, 0, data.length);
                }

//                            BitmapFactory.Options options = new BitmapFactory.Options();
//                            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
//                            bitmap.compress(Bitmap.CompressFormat.JPEG, 91, stream);
//                            bitmap.recycle();

                stream.flush();
                stream.close();
                success = true;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        completeCapturePhoto(success);
    }

    private void tearDownCamera() {
        if(mCamera != null) {
            Log.i(TAG, "Camera teardown");

            try {
                mCamera.release();
            } catch(RuntimeException exception) {
                Log.i(TAG, "Camera teardown exception (ignoring)", exception);
            }

            mCamera = null;
            mCameraSupportsZoom = false;
            mGestureDetector = null;
        }
    }

    private boolean tearDownRecording() {
        Log.i(TAG, "Recording teardown");
        boolean success = false;

        if(mCurrentRecordingState == RecordingState.STARTED) {
            success = stopRecording();
        }

        if(mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();

            mMediaRecorder = null;

            if(mCamera != null) {
                try {
                    mCamera.lock();
                } catch (RuntimeException exception) {
                    Log.i(TAG, "Recording teardown exception (ignoring)", exception);
                }
            }

            try {
                mOutputParcelFleDescriptor.close();
            } catch (IOException exception) {
                Log.i(TAG, "Recording teardown exception (ignoring)", exception);
            }
        }
        return success;
    }

    private void completeRecording() {
        mCameraShouldBeEnabled = false;
        boolean success = tearDownRecording();

        if (success) {
            tearDown();
            transitionToPlaybackUI();
            mPlayerShouldBeEnabled = true;
            startVideo();
        }
        else {
            mCameraShouldBeEnabled = true;
            setupRecorder();
            setCurrentRecordingState(RecordingState.NOT_STARTED);
            mRecordStartStopButton.setImageResource(R.drawable.ic_videocam_dark);
            if (mMultipleCameras) {
                fadeInView(mCameraFlipButton);
            }
        }
    }

    private void tearDown() {
        tearDownRecording();
        stopPreview();
        tearDownCamera();

        tearDownPlayer();
    }

    private void onError() {
        tearDown();

        mListener.shouldDismissFragment(true);
        return;
    }

    private void setCurrentPreviewState(PreviewState previewState) {
        Log.i(TAG, "Preview state changed: " + previewState);

        mCurrentPreviewState = previewState;
    }

    private void setCurrentRecordingState(RecordingState recordingState) {
        Log.i(TAG, "Recording state changed: " + recordingState);

        mCurrentRecordingState = recordingState;
    }

    private void setCurrentCaptureState(CaptureState captureState) {
        Log.i(TAG, "Capture state changed: " + captureState);

        mCurrentCaptureState = captureState;
    }

    private void setupProgressTimer() {
        mCountdownTimer = new Timer();
    }

    private void startCountdownTimer() {
        if(mCountdownTimer == null) {
            setupProgressTimer();
        }
        mCountDown = 0;
        mCountdownTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCountDown == MAX_DURATION_MS) {
                            completeRecording();
                        } else if (mCountDown < MAX_DURATION_MS) {
                            mCountDown += 1000;

                            mCountDownTextView.setText(Utilities.getTimeString(mCountDown));

                            Log.i(TAG, "Recording progress " + mCountDown);
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    private void stopCountdownTimer() {
        if(mCountdownTimer != null) {
            mCountdownTimer.cancel();
            mCountdownTimer.purge();
        }

        mCountdownTimer = null;
    }

    private Camera.Size getPreviewSizeFromView(View view, List<Camera.Size> sizes) {
        view.requestLayout();
        double ratio =
                (double) Math.max(view.getWidth(), view.getHeight())
                        / Math.min(view.getWidth(), view.getHeight());
        Camera.Size size = CameraHelper.getOptimalPreviewSize(getActivity(), sizes, ratio);
        return size;
    }

    private class MySimpleOnScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        public int mMaxZoom;

        int mZoomWhenScaleBegan;
        int mCurrentZoom;
        int mLastZoom;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (mCamera == null || !mCameraSupportsZoom) {
                return false;
            }
            float scaleFactor = detector.getScaleFactor();
            float ratio = (detector.getScaleFactor() >= 1.0f) ? (scaleFactor - 1.f) : -(1.f / scaleFactor - 1.f);
            mCurrentZoom = (int) (mZoomWhenScaleBegan + (mMaxZoom * ratio));
            mCurrentZoom = Math.min(mCurrentZoom, mMaxZoom);
            mCurrentZoom = Math.max(0, mCurrentZoom);
            mLastZoom = mCurrentZoom;

            // TODO: instead of setting the parameters for every touch event, we could create a timer
            // that updates them for every video frame
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setZoom(mCurrentZoom);
            mCamera.setParameters(parameters);

            return false;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (mCamera == null || !mCameraSupportsZoom) {
                return false;
            }
            mZoomWhenScaleBegan = mLastZoom;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    }

    // ------------------- Playback methods ---------------------------
    private void setupPlayer() {
        if (mPlayer != null) {
            return;
        }
        mPlayer = new MediaPlayer();
        mPlayer.setSurface(new Surface(mVideoPreviewTextureView.getSurfaceTexture()));
        try {
            mPlayer.setDataSource(getActivity(), mOutputUri);
            mPlayer.prepare();
            mPlayer.setLooping(true);
            mPlayer.start();
        } catch (IOException e) {
            onError();
        }
    }

    private void tearDownPlayer() {
        if (mPlayer == null) {
            return;
        }
        if (mPlayer.isPlaying()) {
            mPlayer.stop();
        }
        mPlayer.release();
        mPlayer = null;
    }

    private void preparePhotoView() {
        recycleViewDrawable(mPhotoView);
        InputStream stream = null;
        try {
            // We read the image and scale it down for the screen. We may need to rotate it.
            stream = getActivity().getContentResolver().openInputStream(mOutputUri);
            // Calculate inSampleSize
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, null, options);
            stream.close();
            int srcWidth = options.outWidth;
            int srcHeight = options.outHeight;
            Matrix matrix = ViewUtil.getRotationMatrixFromExif(getActivity(), mOutputUri);
            if (matrix != null) {
                Matrix reverseMatrix = new Matrix();
                reverseMatrix.postRotate(180);
                // If the image is rotated by 90 or 270 degrees, swap source width and height
                if (!matrix.equals(reverseMatrix)) {
                    srcWidth = options.outHeight;
                    srcHeight = options.outWidth;
                }
            }
            int inSampleSize = BitmapUtil.findOptimalSampleSize(srcWidth, srcHeight,
                    mPhotoView.getMeasuredWidth(), mPhotoView.getMeasuredHeight());
            // Decode file and scale down
            options = new BitmapFactory.Options();
            options.inSampleSize = inSampleSize;
            stream = getActivity().getContentResolver().openInputStream(mOutputUri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            // Rotate according to orientation flag
            if (matrix != null && bitmap != null) {
                Bitmap recycleBitmap = bitmap;
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
                recycleBitmap.recycle();
            }
            if (bitmap != null) {
                mPhotoView.setImageBitmap(bitmap);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void completeShare() {
        if (mType == TYPE_VIDEO) {
            mListener.onVideoRecorded(mOutputUri);
        }
        else {
            mListener.onPhotoCaptured(mOutputUri);
        }
    }
}
