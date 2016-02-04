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
package com.silentcircle.messaging.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.silentcircle.common.media.CameraHelper;
import com.silentcircle.messaging.views.TextView;
import com.silentcircle.silentphone2.R;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This is made solely because calling the system camera via intents does not expose the options we need
 * for video that is reasonably sized. The current size on a Nexus 6 is around 300 MB per 1 minute of video.
 */
@SuppressWarnings("deprecation")
public class VideoCaptureActivity extends Activity {

    private static final String TAG = VideoCaptureActivity.class.getSimpleName();

    private static final int MAX_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private static final int[] DESIRED_QUALITY_RANKING = {
        CamcorderProfile.QUALITY_480P,
        CamcorderProfile.QUALITY_720P,
        CamcorderProfile.QUALITY_CIF,
        CamcorderProfile.QUALITY_QVGA,
        CamcorderProfile.QUALITY_QCIF,
        CamcorderProfile.QUALITY_LOW
    };

    Uri mOutputUri;
    FileDescriptor mOutputFileDescriptor;

    TextureView mVideoPreviewTextureView;

    ImageButton mRecordStartStopButton;
    ImageButton mCameraFlipButton;
    LinearLayout mRecordInfoLayout;
    TextView mCountDownTextView;

    MediaRecorder mMediaRecorder;
    RecordingState mCurrentRecordingState = RecordingState.NOT_STARTED;

    Camera mCamera;
    int mCameraId;
    CamcorderProfile mCamcorderProfile;

    CameraFacing mCameraFacing = CameraFacing.BACK;
    CameraFacing mRequestedFacing = CameraFacing.BACK;
    PreviewState mCurrentPreviewState = PreviewState.NOT_STARTED;

    Timer mCountdownTimer;
    int mCountDown;

    View.OnClickListener mOnClickListener;

    enum CameraFacing {
        BACK(0, R.drawable.ic_camera_rear_white_24dp),
        FRONT(1, R.drawable.ic_camera_front_white_24dp);

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_recorder);

        Intent recordIntent = getIntent();

        Uri outputUri;

        if(recordIntent == null) {
            Log.e(TAG, "Intent is null");

            finish();
            return;
        }

        outputUri = recordIntent.getData();

        if(outputUri == null) {
            Log.e(TAG, "Output URI is null");

            finish();
            return;
        }

        mOutputUri = outputUri;

        FileDescriptor fileDescriptor = null;
        try {
            fileDescriptor = getContentResolver().openFileDescriptor(mOutputUri, null).getFileDescriptor();
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Output URI is an invalid file", exception);
        }

        if(fileDescriptor == null || !fileDescriptor.valid()) {
            finish();
            return;
        }

        mOutputFileDescriptor = fileDescriptor;

        setupViews();
    }

    private void setupViews() {
        mVideoPreviewTextureView = (TextureView) findViewById(R.id.video_preview_texture_view);

        mVideoPreviewTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setup();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                tearDown();

                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                //updateCameraIfNecessary();
            }
        });

        mRecordStartStopButton = (ImageButton) findViewById(R.id.video_record_start_stop_button);
        mCameraFlipButton = (ImageButton) findViewById(R.id.video_record_flip_button);
        mRecordInfoLayout = (LinearLayout) findViewById(R.id.video_record_info_layout);
        mCountDownTextView = (TextView) findViewById(R.id.video_countdown_text_view);

        mOnClickListener = new View.OnClickListener() {
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
                }
            }
        };

        mRecordStartStopButton.setOnClickListener(mOnClickListener);
        mCameraFlipButton.setOnClickListener(mOnClickListener);
    }

    private void setup() {
        setupCamera();
        setupRecorder();
    }

    private void setupCamera() {
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

            tearDown();

            finish();
            return;
        }

        int quality = 0;

        for(int i = 0; i < DESIRED_QUALITY_RANKING.length; i++) {
            if(CamcorderProfile.hasProfile(mCameraId, DESIRED_QUALITY_RANKING[i])) {
                quality = i;

                break;
            }
        }

        mCamcorderProfile = CamcorderProfile.get(mCameraId, quality);

//        Camera.Parameters parameters = mCamera.getParameters();
//        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
//        Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize(this, mSupportedPreviewSizes, (double) mCamcorderProfile.videoFrameWidth / mCamcorderProfile.videoFrameHeight);
//
//        parameters.setPreviewSize(optimalSize.width, optimalSize.height);
//        parameters.setPreviewFrameRate(mCamcorderProfile.videoFrameRate);
//
//        mCamera.setParameters(parameters);

        try {
            CameraHelper.setCameraDisplayOrientation(this, mCameraId, mCamera);
        } catch (RuntimeException exception) {
            Log.i(TAG, "Camera set orientation exception (ignoring)", exception);
        }

        try {
            mCamera.setPreviewTexture(mVideoPreviewTextureView.getSurfaceTexture());
            startPreview();
        } catch (IOException exception) {
            Log.e(TAG, "Set preview exception", exception);

            onCancel();
        }
    }

    private void setupRecorder() {
        mMediaRecorder = new MediaRecorder();

        try {
            if(mCamera == null) {
                Log.e(TAG, "Recorder setup fail - no camera");

                tearDown();

                finish();
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
        mCamcorderProfile.audioBitRate = 32000;
        mCamcorderProfile.audioSampleRate = 48000;

        mCamcorderProfile.videoCodec = MediaRecorder.VideoEncoder.H264;
        mCamcorderProfile.videoBitRate = 500000;

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFile(mOutputFileDescriptor);
        mMediaRecorder.setMaxDuration(MAX_DURATION_MS);

        mMediaRecorder.setProfile(mCamcorderProfile);

        mMediaRecorder.setOrientationHint(90);

        try {
            mMediaRecorder.prepare();
        } catch(Throwable exception) {
            Log.e(TAG, "Recorder setup exception", exception);

            onCancel();
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

    private void restart() {
        tearDown();
        setup();
    }

    private void updateCameraIfNecessary() {
        if(!mCameraFacing.equals(mRequestedFacing)) {
            mCameraFacing = mRequestedFacing;

            restart();
        }
    }

    public void startRecording() {
        try {
            mMediaRecorder.start();
        } catch(Throwable exception) {
            Log.e(TAG, "Recorder setup exception", exception);

            onCancel();
        }

        Log.i(TAG, "Recording started");

        setCurrentRecordingState(RecordingState.STARTED);

        mRecordInfoLayout.setVisibility(View.VISIBLE);
        mRecordStartStopButton.setImageResource(R.drawable.ic_stop_white_24dp);
        mCameraFlipButton.setVisibility(View.INVISIBLE);

        startCountdownTimer();
    }

    private void stopRecording() {
        try {
            if(mMediaRecorder != null) {
                mMediaRecorder.stop();
            }

        } catch(RuntimeException exception) {
            Log.e(TAG, "Exception when stopping recording (ignoring)", exception);
        }

        Log.i(TAG, "Recording stopped");

        setCurrentRecordingState(RecordingState.STOPPED);

        stopCountdownTimer();
    }

    private void tearDownCamera() {
        Log.i(TAG, "Camera teardown");

        if(mCamera != null){
            try {
                mCamera.release();
            } catch(RuntimeException exception) {
                Log.i(TAG, "Camera teardown exception (ignoring)", exception);
            }

            mCamera = null;
        }
    }

    private void tearDownRecording() {
        Log.i(TAG, "Recording teardown");

        if(mCurrentRecordingState == RecordingState.STARTED) {
            stopRecording();
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
                getContentResolver().openFileDescriptor(mOutputUri, null).close();
            } catch (IOException exception) {
                Log.i(TAG, "Recording teardown exception (ignoring)", exception);
            }
        }
    }

    private void completeRecording() {
        tearDown();
        Intent returnIntent = new Intent();
        returnIntent.setData(mOutputUri);
        setResult(RESULT_OK, returnIntent);

        finish();
        return;
    }

    private void tearDown() {
        tearDownRecording();
        stopPreview();
        tearDownCamera();

        try {
            getContentResolver().openFileDescriptor(mOutputUri, null).close();
        } catch(IOException exception) {
            Log.e(TAG, "Teardown exception (ignoring)", exception);
        }
    }

    private void onCancel() {
        tearDown();

        finish();
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

        mCountdownTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(mCountDown == MAX_DURATION_MS) {
                            completeRecording();
                        } else if(mCountDown < MAX_DURATION_MS) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        onCancel();
    }
}
