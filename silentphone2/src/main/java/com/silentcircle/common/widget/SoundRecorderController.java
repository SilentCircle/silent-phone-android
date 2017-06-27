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
package com.silentcircle.common.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;


import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.providers.AudioProvider;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

import static com.silentcircle.common.widget.SoundRecorderController.RecordingState.NOT_STARTED;
import static com.silentcircle.common.widget.SoundRecorderController.RecordingState.STARTED;
import static com.silentcircle.common.widget.SoundRecorderController.RecordingState.STARTING;
import static com.silentcircle.common.widget.SoundRecorderController.RecordingState.STOPPING;

/**
 * A class that handles sound recording in ConversationActivity and is responsible for driving
 * MediaRecorder, the interface and the transitions between different states.
 *
 * ConversationActivity calls the public interface methods of the controller and implements the
 * {@link SoundRecorderListener} to get the results back.
 */
public class SoundRecorderController {

    /*
     * The controller uses a "Recorder" thread that uses MediaRecorder and runs all the recording
     * logic and transitions between the different recording states.
     *
     * The public interface methods are called in the main thread and the calls are sent as messages
     * to mRecorderHandler and get handled in the Recorder thread.  The results are sent back to the
     * main thread as messages via mMainHandler.
     */

    public interface SoundRecorderListener {
        void onSoundRecorded(Uri uri);
        void onShortRecording();
        void onRecordingCanceled();
        void onError();
    }

    private static final String TAG = SoundRecorderController.class.getSimpleName();

    private static final String FILE_DESCRIPTOR_MODE = "rwt";

    private final Uri mOutputUri = AudioProvider.CONTENT_URI;

    private static final int MIN_DURATION_MS = 500; // 0.5 seconds

    private static final int MAX_DURATION_MS = 3 * 60 * 1000; // 3 minutes

    private static final int VIBRATION_DURTION_MS = 100;

    private static final int FADE_DURATION_MS = 200;

    private static final float FILTER_RC = 1.f / 30;

    private static final float DB_CIRCLE_MIN_SCALE = 2.7f;

    private static final int DRAG_OFFSET_DP = 60;

    private Context mContext;
    private SoundRecorderListener mListener;
    private Handler mMainHandler;
    private Handler mTimerHandler;
    private Choreographer mChoreographer;

    // Views for recording
    private ImageView mRecordButton;
    private ImageView mRecDot;
    private TextView mRecordingTimer;
    private ImageView mDBCircle;
    private View mCancelText;
    // Other views
    private EditText mComposeText;
    private View mAttachmentButton;
    // Animation
    private Animation mRecDotAnimation;
    private boolean mIsDBCircleAnimationRunning;
    // DB and Low pass filter
    private int mPreviousAmplitudeValue;
    private long mPreviewsTimestampNano;
    private float mPreviousValue;
    // Drag to cancel
    private int[] tempIntArray = new int[2];
    private int mDragOffsetX; // the offset in pixels for estimating if we need to cancel due to dragging
    private boolean mIsCancelledByDragging; // if recording was cancelled due to dragging
    private float mDragTranslationX; // the translation due to dragging
    private float mSlideInAnimationTranslationX; // the translation due to slide in animation
    private Animator mCancelTextSlideInAnimator; // the slide-in animator for CancelText

    // Variables accessed in recorder thread
    private HandlerThread mRecorderThread;
    private Handler mRecorderHandler;
    private Handler mDelayHandler;
    private FileDescriptor mOutputFileDescriptor;
    private RecordingState mCurrentRecordingState;
    private long mRecordingStartedTimestamp;
    private Vibrator mVibrator;

    // Variables accessed in main thread
    private boolean mIsRecordingUIActive;
    private boolean mIsRecordingMain;
    private long mRecordingStartedTimestampMain;

    // Variables accessed by both threads
    private final Object mRecorderGuard = new Object(); // makes sure we don't access mMediaRecorder
                                                        // while changing recording state
    private boolean mIsRecording;
    private MediaRecorder mMediaRecorder;

    enum RecordingState {
        NOT_STARTED,
        STARTING,
        STARTED,
        STOPPING
    }

    public SoundRecorderController(Context context, Activity activity, SoundRecorderListener listener) {
        mContext = context;
        mListener = listener;
        mRecordButton = (ImageView) activity.findViewById(R.id.compose_send);
        mComposeText = (EditText) activity.findViewById(R.id.compose_text);
        mAttachmentButton = activity.findViewById(R.id.compose_attach);
        mRecDot = (ImageView) activity.findViewById(R.id.rec_dot);
        mRecDot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mRecordingTimer = (TextView) activity.findViewById(R.id.sound_recording_timer);
        mDBCircle = (ImageView) activity.findViewById(R.id.db_circle);
        mDragOffsetX = ViewUtil.dp(DRAG_OFFSET_DP, context);
        mCancelText = activity.findViewById(R.id.sound_recording_cancel);
        mCancelTextSlideInAnimator = createCancelTextSlideInAnimator();

        mMainHandler = new MainHandler();
        mTimerHandler = new Handler();
        mVibrator =  (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mChoreographer = Choreographer.getInstance();

        // Recorder thread setup
        mRecorderThread = new HandlerThread("Recorder");
        mRecorderThread.start();
        mRecorderHandler = new RecorderHandler(mRecorderThread.getLooper());
        mDelayHandler = new Handler(mRecorderThread.getLooper());

        mCurrentRecordingState = NOT_STARTED;
    }

    private boolean prepareFile() {
        FileDescriptor fileDescriptor = null;
        try {
            ParcelFileDescriptor pfd = mContext.getContentResolver()
                    .openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE);
            if (pfd != null) {
                fileDescriptor = pfd.getFileDescriptor();
            }
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Output URI is an invalid file", exception);
        }

        if(fileDescriptor == null || !fileDescriptor.valid()) {
            return false;
        }

        mOutputFileDescriptor = fileDescriptor;

        return true;
    }

    //region Recorder methods
    //----------------------------------------------------

    private boolean setupRecorder() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioChannels(1);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setAudioEncodingBitRate(32000);
        mMediaRecorder.setAudioSamplingRate(48000);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mOutputFileDescriptor);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            mMediaRecorder.prepare();
        } catch (IOException exception) {
            Log.e(TAG, "Recording preparation failed", exception);
            return false;
        }
        return true;
    }

    private boolean startRecording() {
        try {
            mMediaRecorder.start();
        } catch(IllegalStateException exception) {
            Log.e(TAG, "Bad state when starting recording", exception);
            return false;
        }
        return true;
    }

    private boolean stopRecording() {
        try {
            mMediaRecorder.stop();
            return true;
        } catch(IllegalStateException exception) {
            Log.e(TAG, "Bad state when stopping recording (ignoring)", exception);
        } catch(RuntimeException exception) {
            Log.e(TAG, "Exception when stopping recording (ignoring)", exception);
        }
        return false;
    }

    private void tearDownRecording() {
        if(mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
            try {
                ParcelFileDescriptor pfd = mContext.getContentResolver().openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE);
                if (pfd != null) {
                    pfd.close();
                }
            } catch (IOException exception) {
                Log.i(TAG, "Recording teardown exception (ignoring)", exception);
            }
        }
    }

    //endregion
    //----------------------------------------------------


    //region Recorder State handling - Runs on Recorder thread
    //----------------------------------------------------

    private class RecorderHandler extends Handler {

        static final int MSG_ON_LONG_PRESS_DOWN = 0;
        static final int MSG_ON_LONG_PRESS_UP = 1;
        static final int MSG_ON_LONG_PRESS_CANCELLED = 2;
        static final int MSG_ON_PAUSE = 3;
        static final int MSG_ON_DESTROY = 4;

        RecorderHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_LONG_PRESS_DOWN:
                    handleOnLongPressDown();
                    break;
                case MSG_ON_LONG_PRESS_UP:
                    handleOnLongPressUp();
                    break;
                case MSG_ON_LONG_PRESS_CANCELLED:
                    handleCancel();
                    break;
                case MSG_ON_PAUSE:
                    handleCancel();
                    break;
                case MSG_ON_DESTROY:
                    handleOnDestroy();
                    break;
            }
        }
    }

    private void handleOnLongPressDown() {
        if (mCurrentRecordingState != NOT_STARTED) {
            return;
        }
        boolean success = prepareFile();
        if (success)
            success = setupRecorder();

        if (success) {
            mCurrentRecordingState = STARTING;
            mVibrator.vibrate(VIBRATION_DURTION_MS);
            mMainHandler.sendEmptyMessage(MainHandler.MSG_ENTER_RECORDING_UI);
            // delay recording so that we don't record the vibration
            dispatchStartRecording(VIBRATION_DURTION_MS);
        }
        if (!success) {
            mCurrentRecordingState = NOT_STARTED;
            tearDownRecording();
            mMainHandler.sendEmptyMessage(MainHandler.MSG_ERROR_SETTING_UP_RECORDER);
        }
    }

    private void handleOnLongPressUp() {
        if (!(mCurrentRecordingState == STARTED || mCurrentRecordingState == STARTING)) {
            return;
        }

        // The startRecording runnable hasn't run yet
        if (!mIsRecording) {
            mCurrentRecordingState = NOT_STARTED;
            doStopRecording();
            mMainHandler.sendEmptyMessage(MainHandler.MSG_SHORT_RECORDING);
        }
        // Stop recording a bit later if the touch was lifted too soon
        else if (mIsRecording &&
                SystemClock.uptimeMillis() - mRecordingStartedTimestamp < MIN_DURATION_MS) {
            mCurrentRecordingState = STOPPING;
            dispatchStopRecording(MIN_DURATION_MS - (SystemClock.uptimeMillis() - mRecordingStartedTimestamp));
            mMainHandler.sendEmptyMessage(MainHandler.MSG_SHORT_RECORDING);
        }
        // Recording has started a while ago
        else {
            boolean success = doStopRecording();
            if (success) {
                // delay callback to avoid bad animation
                mCurrentRecordingState = STOPPING;
                dispatchListenerOnSoundRecorded(350);
                mMainHandler.sendEmptyMessage(MainHandler.MSG_RECORDING_STOPPED);
            } else {
                mCurrentRecordingState = NOT_STARTED;
                mMainHandler.sendEmptyMessage(MainHandler.MSG_ERROR_STOP_RECORDING);
            }
        }
    }

    private void handleCancel() {
        if (!(mCurrentRecordingState == STARTED || mCurrentRecordingState == STARTING)) {
            return;
        }
        mMainHandler.sendEmptyMessage(MainHandler.MSG_RECORDING_CANCELLED);
        // Stop recording a bit later if it was cancelled to soon
        if (mIsRecording &&
                SystemClock.uptimeMillis() - mRecordingStartedTimestamp < MIN_DURATION_MS) {
            mCurrentRecordingState = STOPPING;
            dispatchStopRecording(MIN_DURATION_MS - (SystemClock.uptimeMillis() - mRecordingStartedTimestamp));
        }
        else {
            mCurrentRecordingState = NOT_STARTED;
            boolean success = doStopRecording();
        }
    }

    private void handleOnDestroy() {
        mDelayHandler.removeCallbacksAndMessages(null);
        mRecorderHandler.removeCallbacksAndMessages(null);
        if (mCurrentRecordingState == STARTED || mCurrentRecordingState == STARTING) {
            doStopRecording();
        }
        else {
            tearDownRecording();
        }
        mCurrentRecordingState = NOT_STARTED;
        mRecorderThread.quit();
    }

    private void dispatchReachedMaxRecordingTime() {
        mDelayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long recordingDurationMS = (mIsRecording) ? SystemClock.uptimeMillis() - mRecordingStartedTimestamp : 0;
                if (recordingDurationMS >= MAX_DURATION_MS) {
                    mVibrator.vibrate(VIBRATION_DURTION_MS * 2);
                    handleOnLongPressUp();
                }
            }
        }, MAX_DURATION_MS);
    }

    private void dispatchStartRecording(final long delayMS) {
        mDelayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean success = startRecording();
                if (success) {
                    synchronized (mRecorderGuard) {
                        mIsRecording = true;
                    }
                    mRecordingStartedTimestamp = SystemClock.uptimeMillis();
                    mCurrentRecordingState = STARTED;
                    dispatchReachedMaxRecordingTime();
                    mMainHandler.sendMessage(mMainHandler.obtainMessage(
                            MainHandler.MSG_RECORDING_STARTED, mRecordingStartedTimestamp));
                }
                if (!success) {
                    mCurrentRecordingState = NOT_STARTED;
                    tearDownRecording();
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_ERROR_STARTING_RECORDING);
                }
            }
        }, delayMS);
    }

    private void dispatchStopRecording(final long delayMS) {
        mDelayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mDelayHandler.removeCallbacksAndMessages(null);
                mCurrentRecordingState = NOT_STARTED;
                doStopRecording();
            }
        }, delayMS);
    }

    private void dispatchListenerOnSoundRecorded(final long delayMS) {
        mDelayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCurrentRecordingState = NOT_STARTED;
                mMainHandler.sendMessage(mMainHandler.obtainMessage(
                        MainHandler.MSG_SOUND_RECORDED, mOutputUri));
            }
        }, delayMS);
    }

    private boolean doStopRecording() {
        mDelayHandler.removeCallbacksAndMessages(null);
        boolean success = true;
        synchronized (mRecorderGuard) {
            if (mIsRecording) {
                mIsRecording = false;
                success = stopRecording();
            }
        }
        tearDownRecording();
        return success;
    }

    //endregion
    //----------------------------------------------------

    //region Public Interface
    //----------------------------------------------------

    public void onLongPressDown() {
        mRecorderHandler.sendEmptyMessage(RecorderHandler.MSG_ON_LONG_PRESS_DOWN);
    }

    public void onLongPressUp() {
        mRecorderHandler.sendEmptyMessage(RecorderHandler.MSG_ON_LONG_PRESS_UP);
    }

    public void onLongPressCancelled() {
        mRecorderHandler.sendEmptyMessage(RecorderHandler.MSG_ON_LONG_PRESS_CANCELLED);
    }


    public void onDrag(float distanceX, float distanceY) {
        if (!mIsRecordingUIActive) {
            return;
        }
        updateUIForDrag(distanceX);

        // Cancel recording if dragging too much to the left
        if (mIsCancelledByDragging) {
            return;
        }
        mRecDot.getLocationOnScreen(tempIntArray);
        int minPositionX = tempIntArray[0];
        mRecordButton.getLocationOnScreen(tempIntArray);
        int positionX = tempIntArray[0];
        if (positionX < minPositionX + mDragOffsetX) {
            mIsCancelledByDragging = true;
            mVibrator.vibrate(VIBRATION_DURTION_MS);
            mRecorderHandler.sendEmptyMessage(RecorderHandler.MSG_ON_LONG_PRESS_CANCELLED);
        }
    }

    public void onPause() {
        mRecorderHandler.sendEmptyMessage(RecorderHandler.MSG_ON_PAUSE);
    }

    public void onDestroy() {
        mRecorderHandler.sendEmptyMessage(RecorderHandler.MSG_ON_DESTROY);
    }

    //endregion
    //----------------------------------------------------


    //region UI methods
    //----------------------------------------------------

    private class MainHandler extends Handler {

        static final int MSG_ENTER_RECORDING_UI = 0;
        static final int MSG_ERROR_SETTING_UP_RECORDER = 1;
        static final int MSG_RECORDING_STARTED = 2;
        static final int MSG_ERROR_STARTING_RECORDING = 3;
        static final int MSG_RECORDING_STOPPED = 4;
        static final int MSG_SHORT_RECORDING = 5;
        static final int MSG_ERROR_STOP_RECORDING = 6;
        static final int MSG_RECORDING_CANCELLED = 7;
        static final int MSG_SOUND_RECORDED = 8;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENTER_RECORDING_UI:
                    enterRecordingUI();
                    break;
                case MSG_ERROR_SETTING_UP_RECORDER:
                    mListener.onError();
                    break;
                case MSG_RECORDING_STARTED:
                    mRecordingStartedTimestampMain = (long) msg.obj;
                    mIsRecordingMain = true;
                    break;
                case MSG_ERROR_STARTING_RECORDING:
                    exitRecordingUI();
                    mListener.onError();
                    break;
                case MSG_RECORDING_STOPPED:
                    mIsRecordingMain = false;
                    exitRecordingUI();
                    break;
                case MSG_SHORT_RECORDING:
                    mIsRecordingMain = false;
                    exitRecordingUI();
                    mListener.onShortRecording();
                    break;
                case MSG_ERROR_STOP_RECORDING:
                    mIsRecordingMain = false;
                    exitRecordingUI();
                    mListener.onError();
                    break;
                case MSG_RECORDING_CANCELLED:
                    mIsRecordingMain = false;
                    exitRecordingUI();
                    mListener.onRecordingCanceled();
                    break;
                case MSG_SOUND_RECORDED:
                    mIsRecordingMain = false;
                    Uri uri = (Uri) msg.obj;
                    mListener.onSoundRecorded(uri);
                    break;
            }
        }
    }

    private void enterRecordingUI() {
        mIsRecordingUIActive = true;
        mIsCancelledByDragging = false;

        startRecordButtonScaleUpAnimation();
        startTimer();
        startDBMonitoring();

        mRecDotAnimation = AnimUtils.createFlashingAnimation();
        mRecDot.setVisibility(View.VISIBLE);
        mRecDot.setAlpha(1.f);
        mRecDot.startAnimation(mRecDotAnimation);

        mCancelText.setVisibility(View.VISIBLE);
        mCancelText.setAlpha(1.f);
        mDragTranslationX = 0;
        mSlideInAnimationTranslationX = 0;
        mCancelTextSlideInAnimator.start();

        AnimUtils.fadeOut(mAttachmentButton, FADE_DURATION_MS / 3, null, false);
        AnimUtils.fadeOut(mComposeText, FADE_DURATION_MS, null, false);
        AnimUtils.fadeIn(mRecordingTimer, FADE_DURATION_MS);
    }

    private void exitRecordingUI() {
        mIsRecordingUIActive = false;

        startRecordButtonScaleDownAnimation();
        stopTimer();
        stopDBMonitoring();

        startResetButtonTranslationAnimation();

        if (mRecDotAnimation != null) {
            final Animation endingDotAnimation = mRecDotAnimation;
            mRecDotAnimation = null;
            AnimUtils.fadeOut(mRecDot, FADE_DURATION_MS, new AnimUtils.AnimationCallback() {
                public void onAnimationEnd() {
                    endingDotAnimation.cancel();
                }

                public void onAnimationCancel() {
                    endingDotAnimation.cancel();
                }
            }, false);
        }

        AnimUtils.fadeOut(mCancelText, FADE_DURATION_MS, null, false);

        AnimUtils.fadeIn(mAttachmentButton, FADE_DURATION_MS);
        AnimUtils.fadeIn(mComposeText, FADE_DURATION_MS);
        AnimUtils.fadeOut(mRecordingTimer, FADE_DURATION_MS, null, false);
    }

    private Animator createCancelTextSlideInAnimator() {
        ValueAnimator animator = ValueAnimator.ofFloat(ViewUtil.dp(100, mContext), 0);
        animator.setDuration(250);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mSlideInAnimationTranslationX = (float) animation.getAnimatedValue();
                mCancelText.setTranslationX(mSlideInAnimationTranslationX + mDragTranslationX);
            }
        });
        return animator;
    }

    /*
     * Translates the appropriate views due to the user dragging his/her finger.
     */
    private void updateUIForDrag(float distanceX) {
        if (distanceX > 0) {
            distanceX = 0;
        }
        mRecordButton.setTranslationX(distanceX);
        mDBCircle.setTranslationX(distanceX);

        // The final translation is affected by both user dragging and slide in animation.
        mDragTranslationX = distanceX;
        mCancelText.setTranslationX(mSlideInAnimationTranslationX + mDragTranslationX);
    }

    /*
     * Resets the translation of the views that were moved due to user dragging.
     */
    private void startResetButtonTranslationAnimation() {
        if (mRecordButton.getTranslationX() == 0) {
            return;
        }
        ViewPropertyAnimator animator = mRecordButton.animate().translationX(0.f)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .setStartDelay(0)
                .setDuration(350);
        animator.start();
        ViewPropertyAnimator animator2 = mDBCircle.animate().translationX(0.f)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .setStartDelay(0)
                .setDuration(350);
        animator2.start();
    }

    private void startRecordButtonScaleUpAnimation() {
        mRecordButton.setImageResource(R.drawable.action_button_voice_recording_big);
        ViewPropertyAnimator animator = mRecordButton.animate().scaleX(2.25f).scaleY(2.25f)
                .setInterpolator(new DecelerateInterpolator())
                .setStartDelay(50)
                .setDuration(200)
                .setListener(null);
        animator.withLayer().start();
    }

    private void startRecordButtonScaleDownAnimation() {
        final ViewPropertyAnimator animator = mRecordButton.animate().scaleX(1).scaleY(1)
                .setInterpolator(new OvershootInterpolator(2.f))
                .setStartDelay(0)
                .setDuration(250);
        animator.setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mRecordButton.setImageResource(R.drawable.action_button_voice_recording);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
        animator.withLayer().start();
    }

    private void startDBMonitoring() {
        mDBCircle.setVisibility(View.VISIBLE);
        ViewPropertyAnimator animator = mDBCircle.animate().scaleX(DB_CIRCLE_MIN_SCALE)
                .scaleY(DB_CIRCLE_MIN_SCALE)
                .setInterpolator(new LinearInterpolator())
                .setDuration(150)
                .setStartDelay(0)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {}

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mIsDBCircleAnimationRunning = false;
                        mPreviousValue = mDBCircle.getScaleX();
                        mPreviewsTimestampNano = System.nanoTime();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {}

                    @Override
                    public void onAnimationRepeat(Animator animation) {}
                });
        mIsDBCircleAnimationRunning = true;
        animator.withLayer().start();

        mPreviewsTimestampNano = 0;
        mPreviousAmplitudeValue = 1;
        mPreviousAmplitudeValue = 0;
        mChoreographer.postFrameCallback(mDBMonitoringCallback);
    }

    private void stopDBMonitoring() {
        mChoreographer.removeFrameCallback(mDBMonitoringCallback);

        ViewPropertyAnimator animator = mDBCircle.animate().scaleX(0.5f).scaleY(0.5f)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(250)
                .setStartDelay(50)
                .setListener(new Animator.AnimatorListener() {
                    boolean isCancelled;
                    @Override
                    public void onAnimationStart(Animator animation) {}

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!isCancelled) {
                            mDBCircle.setVisibility(View.INVISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        isCancelled = true;
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {}
                });
        animator.withLayer().start();
    }

    // Runs every frame and adjusts the mDBCircle's scale according to the current db level
    private Choreographer.FrameCallback mDBMonitoringCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            mChoreographer.postFrameCallback(this);

            if (mIsDBCircleAnimationRunning) {
                return;
            }

            int maxAmpl;
            synchronized (mRecorderGuard) {
                if (!mIsRecording) {
                    return;
                }
                maxAmpl = mMediaRecorder.getMaxAmplitude();
            }

            // Use the previous value. It will look better due to the low pass filter than doing
            // nothing
            if (maxAmpl == 0) {
                maxAmpl = mPreviousAmplitudeValue;
            }
            mPreviousAmplitudeValue = maxAmpl;
            if (maxAmpl == 0) {
                maxAmpl = 1; // leaving to 0 will result in -inf db
            }

            // Convert to db
            float scale = (float) Math.log10((double) maxAmpl);

            // Adjust
            scale = scale * 3.5f - 9.5f;
            scale = Math.max(scale, DB_CIRCLE_MIN_SCALE);
            scale = Math.min(scale, 7.f);

            // Low pass filter
            double dt = (frameTimeNanos - mPreviewsTimestampNano) / 1000000000.;
            mPreviewsTimestampNano = frameTimeNanos;
            double a = dt / (dt + FILTER_RC);
            scale = (float) ((scale * a) + ((1. - a) * mPreviousValue));
            mPreviousValue = scale;

            // Apply
            mDBCircle.setScaleY(scale);
            mDBCircle.setScaleX(scale);

//            Log.d(TAG, dt + " maxAmpl: " + " " + maxAmpl + " " + scale );
        }

    };

    private void startTimer() {
        mUpdateTimerRunnable.run();
    }

    private void stopTimer() {
        mTimerHandler.removeCallbacksAndMessages(null);
    }

    private Runnable mUpdateTimerRunnable = new Runnable() {

        @Override
        public void run() {
            mTimerHandler.postDelayed(this, 100);
            long recordingDurationMS = (mIsRecordingMain) ? SystemClock.uptimeMillis() - mRecordingStartedTimestampMain : 0;

            String durationString = Utilities.getTimeString(recordingDurationMS);
            if (!mRecordingTimer.getText().equals(durationString)) {
                mRecordingTimer.setText(durationString);
            }
        }
    };

    //endregion
    //----------------------------------------------------

}
