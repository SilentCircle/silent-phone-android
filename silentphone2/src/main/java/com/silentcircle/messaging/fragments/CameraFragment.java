/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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
import android.app.Activity;
import android.app.Fragment;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.media.CameraHelper;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.messaging.util.UTI;
import com.silentcircle.messaging.views.TextView;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class CameraFragment extends Fragment{

    public interface CameraFragmentListener {
        void shouldDismissFragment(boolean error);
        void onVideoRecorded(Uri uri);

        /**
         * Called after the hiding animation, triggered by a call to
         * {@link #hide()} is finished.
         *
         * Listener should detach the fragment in the implementation.
         */
        void onFragmentHidden();
    }

    public static final String TAG_CAMERA_FRAGMENT = "com.silentcircle.messaging.camera";
    public static final String URI_KEY = "URI";

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

    CameraFragmentListener mListener;

    Uri mOutputUri;
    FileDescriptor mOutputFileDescriptor;

    FrameLayout mVideoPreviewFrame;
    TextureView mVideoPreviewTextureView;
    boolean mSurfaceReady;
    boolean mCameraShouldBeEnabled;
    boolean mPlayerShouldBeEnabled;
    int mPreviewMaxWidth;
    int mPreviewMaxHeight;

    ImageButton mRecordStartStopButton;
    ImageButton mCameraFlipButton;
    ImageButton mCameraFlashToggleButton;
    LinearLayout mRecordInfoLayout;
    TextView mCountDownTextView;

    ImageButton mShareButton;
    View mShareButtonFrame;

    MediaRecorder mMediaRecorder;
    RecordingState mCurrentRecordingState = RecordingState.NOT_STARTED;

    Camera mCamera;
    int mCameraId;
    CamcorderProfile mCamcorderProfile;

    boolean mMultipleCameras;
    CameraFacing mCameraFacing = CameraFacing.BACK;
    CameraFacing mRequestedFacing = CameraFacing.BACK;
    boolean mCameraSupportsZoom;
    CameraFlash mCameraFlash = CameraFlash.OFF;
    PreviewState mCurrentPreviewState = PreviewState.NOT_STARTED;

    Timer mCountdownTimer;
    int mCountDown;

    ScaleGestureDetector mScaleGestureDetector;

    enum CameraFlash {
        OFF(false, R.drawable.ic_flash_off_dark, Camera.Parameters.FLASH_MODE_OFF),
        ON(true, R.drawable.ic_flash_on_dark, Camera.Parameters.FLASH_MODE_TORCH);

        boolean value;
        String cameraParameter;
        int drawableResourceId;

        CameraFlash(boolean value, int id, String cameraParameter) {
            this.value = value;
            this.drawableResourceId = id;
            this.cameraParameter = cameraParameter;
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

    private MediaPlayer mPlayer;

    // ------------------- Fragment Lifecycle ---------------------------

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        System.out.println("onAttach");

        try {
            mListener = (CameraFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        System.out.println("onCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        System.out.println("onCreateView");

        Bundle arguments = getArguments();
        if (arguments != null) {
            onSaveInstanceState(arguments);
        }
        else {
            arguments = savedInstanceState;
        }
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
        System.out.println("onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        System.out.println("onResume");
        startVideo();
    }

    @Override
    public void onPause() {
        super.onPause();
        System.out.println("onPause");
        if(mCurrentRecordingState == RecordingState.STARTED) {
            completeRecording();
        }
        tearDown();
    }

    @Override
    public void onStop() {
        super.onStop();
        System.out.println("onStop");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        System.out.println("onDestroyView");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.out.println("onDestroy");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        System.out.println("onDetach");
    }

    // ------------------- Public interface ---------------------------
    public void hide() {
        startDisappearAnimation();
    }

    public void onBackPressed() {
        if (mPlayerShouldBeEnabled) {
            mPlayerShouldBeEnabled = false;
            mCameraShouldBeEnabled = true;
            tearDown();
            transitionToRecordingUI();
            setCurrentRecordingState(RecordingState.NOT_STARTED);
            startVideo();
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
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mScaleGestureDetector != null) {
                    mScaleGestureDetector.onTouchEvent(event);
                }
                return true;
            }
        });

        mVideoPreviewFrame = (FrameLayout) view.findViewById(R.id.video_preview_frame);
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

        mRecordStartStopButton = (ImageButton) view.findViewById(R.id.video_record_start_stop_button);
        mCameraFlipButton = (ImageButton) view.findViewById(R.id.video_record_flip_button);
        mCameraFlashToggleButton = (ImageButton) view.findViewById(R.id.video_flash_toggle_button);
        mRecordInfoLayout = (LinearLayout) view.findViewById(R.id.video_record_info_layout);
        mCountDownTextView = (TextView) view.findViewById(R.id.video_countdown_text_view);
        mShareButtonFrame = view.findViewById(R.id.share_button_frame);
        mShareButton = (ImageButton) view.findViewById(R.id.share_button);

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

                    case R.id.video_record_flip_button:
                        switch(mCurrentRecordingState) {
                            case NOT_STARTED:
                                flipCamera();
                                break;
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

        mRecordStartStopButton.setOnClickListener(onClickListener);
        mRecordStartStopButton.setVisibility(View.INVISIBLE);

        mCameraFlashToggleButton.setVisibility(View.INVISIBLE);
        mCameraFlashToggleButton.setOnClickListener(onClickListener);

        if (mMultipleCameras) {
            mCameraFlipButton.setOnClickListener(onClickListener);
            mCameraFlipButton.setVisibility(View.INVISIBLE);
        }
        else {
            mCameraFlipButton.setVisibility(View.GONE);
        }

        mShareButton.setOnClickListener(onClickListener);

        startAppearAnimation();
    }

    private void startAppearAnimation() {
        Rect rect = new Rect();
        mVideoPreviewFrame.getWindowVisibleDisplayFrame(rect);
        mVideoPreviewFrame.setTranslationY(rect.height());
        mVideoPreviewFrame.animate().translationY(0.f).setDuration(350).
                setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                mVideoPreviewFrame.setTranslationY(0.f);
                mCameraShouldBeEnabled = true;
                // run delayed so that the animation is not paused before its end
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.postDelayed(new Runnable() {
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

        fadeOutView(mCameraFlashToggleButton);
        fadeOutView(mRecordStartStopButton);
        fadeOutView(mCameraFlipButton);

        Rect rect = new Rect();
        mVideoPreviewFrame.getWindowVisibleDisplayFrame(rect);
        mVideoPreviewFrame.animate().translationY(rect.height()).setDuration(450).
                setInterpolator(new AnticipateInterpolator()).setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                mListener.onFragmentHidden();
                animation.removeAllListeners();
            }
        }).start();
    }

    private void fadeInBottomButtons() {
        if (mRecordStartStopButton.getVisibility() == View.VISIBLE) {
            return;
        }
        fadeInView(mRecordStartStopButton);
        if (mMultipleCameras) {
            fadeInView(mCameraFlipButton);
        }
    }

    private void fadeOutBottomButtons() {
        fadeOutView(mRecordStartStopButton);
        if (mMultipleCameras) {
            fadeOutView(mCameraFlipButton);
        }
    }

    private void transitionToPlaybackUI() {
        fadeOutBottomButtons();
        fadeOutView(mCameraFlashToggleButton);
        mShareButtonFrame.setVisibility(View.VISIBLE);
        ViewUtil.setViewHeight(mShareButtonFrame, mRecordStartStopButton.getHeight());
        mShareButtonFrame.setTranslationY(mRecordStartStopButton.getHeight());
        mShareButtonFrame.animate().translationY(0.f).setDuration(300).
                setInterpolator(new OvershootInterpolator(2.0f)).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mShareButtonFrame.setTranslationY(0);
                animation.removeAllListeners();
            }
        }).start();
    }

    private void transitionToRecordingUI() {
        mShareButtonFrame.animate().translationY(mRecordStartStopButton.getHeight())
                .setDuration(300).setInterpolator(new AnticipateInterpolator(2.0f))
                .setListener(new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mShareButtonFrame.setVisibility(View.INVISIBLE);
                        animation.removeAllListeners();
                    }

                }).start();

        mRecordStartStopButton.setImageResource(R.drawable.ic_videocam_dark);
    }

    private void fadeInView(View view) {
        AnimUtils.fadeIn(view, 400);
    }

    private void fadeOutView(final View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        view.setAlpha(1.f);
        view.animate().alpha(0.f).setDuration(350).
                setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.INVISIBLE);
                animation.removeAllListeners();
            }
        }).start();
    }

    private void updateInterfaceForCameraSize(int width, int height) {
        float cameraAR = (float) width/height;
        // Invert the camera's AR because the activity is is portrait and the preview is rotated.
        cameraAR = 1/cameraAR;

        if (mPreviewMaxHeight == 0) {
            mPreviewMaxWidth = mVideoPreviewTextureView.getWidth();
            mPreviewMaxHeight = mVideoPreviewTextureView.getHeight();
            if (mPreviewMaxHeight == 0) {
                // We still don't know the dimensions of the maximum available area
                return;
            }
        }
        /*
         * The preview's aspect ratio (AR) will be set the same as the camera's AR, or else
         * the preview will be stretched. The preview can fit in a rectangle with size
         * [mPreviewMaxWidth, mPreviewMaxHeight], which in general has different AR than the camera.
         * Depending on those 2 AR, the preview may fit horizontally or vertically in the available
         * area. If it fits vertically, the buttons will be overlayed. Otherwise they will sit below
         * the preview.
         */
        int previewWidth;
        int previewHeight;
        int buttonHeight;

        float previewAR = (float) mPreviewMaxWidth / mPreviewMaxHeight;

        if (previewAR >= cameraAR) {
            // Preview fills the screen's height. Black bars on the sides. Buttons are overlayed.
            previewHeight = mPreviewMaxHeight;
            previewWidth = (int) (cameraAR * previewHeight);

            buttonHeight = getResources().getDimensionPixelSize(R.dimen.camera_start_stop_button_height);
        }
        else {
            // Preview will be placed on the area on top of the buttons. Button height is adjusted.
            previewWidth = mPreviewMaxWidth;
            previewHeight = (int) ((float) mPreviewMaxWidth /cameraAR);

            buttonHeight= mPreviewMaxHeight - previewHeight;
            int minButtonHeight = getResources().getDimensionPixelSize(R.dimen.camera_start_stop_button_min_height);
            if (buttonHeight < minButtonHeight) {
                // If the button gets shorter than what we want, we'll set it to minButtonHeight and
                // adjust the preview size to fit on the area on top of it.
                buttonHeight = minButtonHeight;

                int availableWidth = mPreviewMaxWidth;
                int availableHeight = mPreviewMaxHeight - buttonHeight;
                previewAR = (float) availableWidth/availableHeight;
                if (previewAR >= cameraAR) {
                    previewHeight = availableHeight;
                    previewWidth = (int) (cameraAR * availableHeight);
                }
                else {
                    previewWidth = availableWidth;
                    previewHeight = (int) ((float) availableWidth /cameraAR);
                }
            }

        }
        ViewUtil.setViewWidthHeight(mVideoPreviewTextureView, previewWidth, previewHeight);
        ViewUtil.setViewHeight(mRecordStartStopButton, buttonHeight);

        fadeInBottomButtons();
    }

    // ------------------- Camera methods ---------------------------

    private void setup() {
        setupCamera();
        setupRecorder();
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

        int quality = 0;

        for(int i = 0; i < DESIRED_QUALITY_RANKING.length; i++) {
            if(CamcorderProfile.hasProfile(mCameraId, DESIRED_QUALITY_RANKING[i])) {
                quality = DESIRED_QUALITY_RANKING[i];

                break;
            }
        }

        mCamcorderProfile = CamcorderProfile.get(mCameraId, quality);

        Camera.Parameters parameters = mCamera.getParameters();

        mCameraSupportsZoom = parameters.isZoomSupported();
        if (mCameraSupportsZoom) {
            MySimpleOnScaleGestureListener scaleListener = new MySimpleOnScaleGestureListener();
            scaleListener.mMaxZoom = parameters.getMaxZoom();
            mScaleGestureDetector = new ScaleGestureDetector(getActivity(), scaleListener);
        }

        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            fadeInView(mCameraFlashToggleButton);
            setCameraFlash(CameraFlash.OFF, false);
        }
        else {
            fadeOutView(mCameraFlashToggleButton);
        }

        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize2(mSupportedPreviewSizes, (double) mCamcorderProfile.videoFrameWidth / mCamcorderProfile.videoFrameHeight);
        parameters.setPreviewSize(optimalSize.width, optimalSize.height);
        Log.i(TAG, "Preview resolution is "
                + String.format("%dx%d (ar=%.2f)", optimalSize.width,
                optimalSize.height,
                (float) optimalSize.width / optimalSize.height)
                + " for camera resolution "
                + String.format("%dx%d (ar=%.2f)", mCamcorderProfile.videoFrameWidth,
                mCamcorderProfile.videoFrameHeight,
                (float) mCamcorderProfile.videoFrameWidth / mCamcorderProfile.videoFrameHeight));
        updateInterfaceForCameraSize(mCamcorderProfile.videoFrameWidth, mCamcorderProfile.videoFrameHeight);
        //parameters.setPreviewFrameRate(mCamcorderProfile.videoFrameRate);

//        /*
//         * Hack!
//         *
//         * Workaround for stretched video preview on BP1 devices.
//         * Choose a preview size that closely matches texture view dimensions.
//         *
//         * Approach works on all devices if this is done on surface view updates but is
//         * unnecessary (only BP1 exhibits stretched camera preview). Using code below in this place
//         * for other devices will stretch preview on them.
//         */
//
//        if (Build.MODEL.equals(BLACKPHONE_BP1)) {
//            List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
//            Camera.Size previewSize = getPreviewSizeFromView(mVideoPreviewTextureView, supportedPreviewSizes);
//            parameters.setPreviewSize(previewSize.width, previewSize.height);
//        }

        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
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
            // TODO: keep reference to ParcelFileDescriptor?
            mOutputFileDescriptor = getActivity().getContentResolver()
                    .openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE).getFileDescriptor();
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Output URI is an invalid file", exception);
        }

        if(mOutputFileDescriptor == null || !mOutputFileDescriptor.valid()) {
            mListener.shouldDismissFragment(true);
            return;
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
        mMediaRecorder.setMaxDuration(MAX_DURATION_MS);

        mMediaRecorder.setProfile(mCamcorderProfile);

        mMediaRecorder.setOrientationHint(CameraHelper.getOrientationHint(getActivity(), mCameraId, mCamera));

        try {
            mMediaRecorder.prepare();
            // Lock the camera, so that we can access it (e.g. zooming)
            mCamera.lock();
        } catch(Throwable exception) {
            Log.e(TAG, "Recorder setup exception", exception);

            onError();
        }
    }

    public void startPreview() {
        if(!mCurrentPreviewState.equals(PreviewState.STARTED) && (mCamera != null)) {
            mCamera.startPreview();

            setCurrentPreviewState(PreviewState.STARTED);
        }
    }

    public void stopPreview() {
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

    private void toggleFlash() {
        CameraFlash requestedFlash;
        if (mCameraFlash.equals(mCameraFlash.OFF)) {
            requestedFlash = CameraFlash.ON;
        }
        else if(mCameraFlash.equals(mCameraFlash.ON)) {
            requestedFlash = CameraFlash.OFF;
        }
        else {
            requestedFlash = CameraFlash.OFF;
        }
        setCameraFlash(requestedFlash, true);
    }

    private void setCameraFlash(CameraFlash flash, boolean updateCameraParams) {
        mCameraFlash = flash;
        mCameraFlashToggleButton.setImageResource(mCameraFlash.drawableResourceId);

        if (updateCameraParams) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(flash.cameraParameter);
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

    public void startRecording() {
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
        return success;
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
            mScaleGestureDetector = null;
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
                getActivity().getContentResolver().openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE).close();
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

        if (mOutputUri != null) {
            try {
                getActivity().getContentResolver()
                        .openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE).close();
            } catch (IOException exception) {
                Log.e(TAG, "Teardown exception (ignoring)", exception);
            }
        }
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

                            mCountDownTextView.setText(getTimeString(mCountDown));

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

    private String getTimeString(int miliSeconds) {
        int minutes = (int) Math.floor((miliSeconds / 1000.0) / 60);
        int seconds = miliSeconds / 1000 - minutes * 60;

        return String.format("%02d:%02d", minutes, seconds);
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
        public void onScaleEnd(ScaleGestureDetector detector) {}
    }

    // ------------------- Playback methods ---------------------------
    private void setupPlayer() {
        if (mPlayer != null) {
            return;
        }
        mPlayer = new MediaPlayer();
        //TODO: stop the camera and detach it from the view first
        mPlayer.setSurface(new Surface(mVideoPreviewTextureView.getSurfaceTexture()));
        try {
            FileDescriptor fileDescriptor = getActivity().getContentResolver()
                .openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE).getFileDescriptor();
            mPlayer.setDataSource(fileDescriptor);
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

    private void completeShare() {
        mListener.onVideoRecorded(mOutputUri);
    }
}
