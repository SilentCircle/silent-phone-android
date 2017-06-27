/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone2.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.animation.AnimationListenerAdapter;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.InCallActivity;
import com.silentcircle.silentphone2.activities.InCallCallback;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.video.CameraPreviewControl2;
import com.silentcircle.silentphone2.video.CameraPreviewController;
import com.silentcircle.silentphone2.video.CameraPreviewControllerHw;
import com.silentcircle.silentphone2.video.SpVideoViewHw;
import com.silentcircle.silentphone2.video.TouchListener;

/**
 * Handle the video display and control.
 *
 * Created by werner on 22.02.14.
 */
public class InCallVideoFragmentHw extends Fragment implements View.OnClickListener, TouchListener,
        TiviPhoneService.ServiceStateChangeListener {

    private static final String TAG = InCallVideoFragmentHw.class.getSimpleName();

    public static final String CALL_TYPE = "video_type";
    private static final int SHOW_EXPLANATION_TIME = 6000;      // time to show video control explanation in ms

    private static final int INVALID = 0;
    private static final int UPPER_LEFT = 1;
    private static final int LOWER_LEFT = 2;
    private static final int UPPER_RIGHT = 3;
    private static final int LOWER_RIGHT = 4;

    // Which states we need to save in case of rotation
    private static final String ACCEPTED = "videoAccepted";
    private static final String PAUSED = "videoPaused";
    private static final String FRONT = "videoFrontCamera";
    private static final String EXPLANATION_SHOWN = "videoExplanationShown";

    // In which quadrant is the preview window currently
    private int mQuadrant;

    private InCallActivity mParent;
    private InCallCallback mCallback;

    /** Video Control buttons */
    private ImageButton mDecline;
    private ImageButton mAccept;

    private LinearLayout mControlLayoutBottom;
    private LinearLayout mControlLayoutTop;
    private LinearLayout mVerifySas;
    private TextView mVerifySasButton;
    private TextView mNumberName;
    private TextView mControlExplanation;
    private TextView mMainSecureState;
    private TextView mPreviewSecureState;
    private boolean mVideoExplanationShown;
    private boolean mSuppressAutoRemove;

    private SpVideoViewHw mVideoScreen;
    private CameraPreviewController mCamera;

    // Holds the icons that we change depending on state
    private Drawable mMicOpen;
    private Drawable mMicMute;
    private Drawable mEndCall;

    private boolean mPauseVideo;
    public boolean mVideoAccepted;
    private boolean mIsNear;

    private ImageView mPreviewMuteIcon;
    private TextureView mPreviewSurface;
    private FrameLayout mPreviewContainer;

    private ImageButton mMuteButton;
    private ImageButton mSwitchCamera;

    private Button mVideoPause;
    private Button mVideoStop;

    /** We always try to start with the front camera */
    private boolean mFrontCamera = true;

    private int mDegrees;

    // Points that divide the main view in left, right and upper, lower areas
    private int mLeftHalf;
    private int mUpperHalf;

    private float mMargin;

    /**
     * Animation that slides top and bottom controls layouts
     */
    private Animation mSlideInBottom;
    private Animation mSlideInTop;
    private Animation mSlideOutBottom;
    private Animation mSlideOutTop;

    private final AnimationListenerAdapter mSlideOutListenerTop = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            mControlLayoutTop.setVisibility(View.INVISIBLE);
        }
    };

    private final AnimationListenerAdapter mSlideInListenerTop = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            mControlLayoutTop.setVisibility(View.VISIBLE);
        }
    };

    private final AnimationListenerAdapter mSlideOutListenerBottom = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            mControlLayoutBottom.setVisibility(View.INVISIBLE);
        }
    };

    private final AnimationListenerAdapter mSlideInListenerBottom = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            mControlLayoutBottom.setVisibility(View.VISIBLE);
            if (!mSuppressAutoRemove) {
                mControlLayoutBottom.postDelayed(mRemoveControls, SHOW_EXPLANATION_TIME);
            }
            else
                mSuppressAutoRemove = false;
        }
    };

    private Runnable mRemoveControls = new Runnable() {
        @Override
        public void run() {
            toggleButtonsDisplay(true);
        }
    };

    public static InCallVideoFragmentHw newInstance(boolean incoming) {
        InCallVideoFragmentHw f = new InCallVideoFragmentHw();

        Bundle args = new Bundle();
        args.putBoolean(CALL_TYPE, incoming);
        f.setArguments(args);
        return f;
    }

    public InCallVideoFragmentHw() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Resources.Theme theme = mParent.getTheme();
        if (theme == null)
            return;
        TypedArray a = theme.obtainStyledAttributes(R.styleable.SpaStyle);
        if (a != null) {
            mMicOpen = a.getDrawable(R.styleable.SpaStyle_sp_ic_mic);
            mMicMute = a.getDrawable(R.styleable.SpaStyle_sp_ic_mic_muted);
            mEndCall = a.getDrawable(R.styleable.SpaStyle_sp_ic_end_call);
            a.recycle();
        }
        if (savedInstanceState != null) {
            mVideoAccepted = savedInstanceState.getBoolean(ACCEPTED);
            mPauseVideo = savedInstanceState.getBoolean(PAUSED);
            mFrontCamera = savedInstanceState.getBoolean(FRONT);
            mVideoExplanationShown = savedInstanceState.getBoolean(EXPLANATION_SHOWN);
        }
        mMargin = getResources().getDimension(R.dimen.activity_vertical_margin);

        final Display display = ((WindowManager)mParent.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int screenOrientation = display.getRotation();

        mDegrees = 0;
        switch (screenOrientation) {
            case Surface.ROTATION_0: {      // 0 degree rotation (natural orientation)
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "screenOrientation - 0");
                mDegrees = 0;
                break;
            }
            case Surface.ROTATION_90: {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "screenOrientation - 90");
                mDegrees = 90;
                break;
            }
            case Surface.ROTATION_270: {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "screenOrientation - 270");
                mDegrees = 270;
                break;
            }
            case Surface.ROTATION_180: {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "screenOrientation - 180");
                mDegrees = 180;
                break;
            }
        }
        mSlideInBottom = AnimationUtils.loadAnimation(mParent, R.anim.dialpad_slide_in_bottom);
        mSlideInBottom.setInterpolator(AnimUtils.EASE_IN);
        mSlideInBottom.setAnimationListener(mSlideInListenerBottom);

        mSlideOutBottom = AnimationUtils.loadAnimation(mParent, R.anim.dialpad_slide_out_bottom);
        mSlideOutBottom.setInterpolator(AnimUtils.EASE_OUT);
        mSlideOutBottom.setAnimationListener(mSlideOutListenerBottom);

        mSlideInTop = AnimationUtils.loadAnimation(mParent, R.anim.video_slide_in_top);
        mSlideInTop.setInterpolator(AnimUtils.EASE_IN);
        mSlideInTop.setAnimationListener(mSlideInListenerTop);

        mSlideOutTop = AnimationUtils.loadAnimation(mParent, R.anim.video_slide_out_top);
        mSlideOutTop.setInterpolator(AnimUtils.EASE_OUT);
        mSlideOutTop.setAnimationListener(mSlideOutListenerTop);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (!Utilities.isTablet(getActivity())) {
            if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_USER)
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        }

        final View fragmentView = inflater.inflate(R.layout.incall_video_fragment_hw, container, false);

        if (fragmentView == null)
            return null;
        Bundle args = getArguments();
        boolean incoming = true;
        if (args != null)
            incoming = args.getBoolean(CALL_TYPE, true);

        mDecline = (ImageButton)fragmentView.findViewById(R.id.VideoDecline);
        mDecline.setOnClickListener(this);
        mAccept = (ImageButton)fragmentView.findViewById(R.id.VideoAccept);
        mAccept.setOnClickListener(this);

        mMuteButton = (ImageButton)fragmentView.findViewById(R.id.mute);
        mMuteButton.setOnClickListener(this);

        mControlLayoutBottom = (LinearLayout)fragmentView.findViewById(R.id.ControlLayoutBottom);
        mControlLayoutTop = (LinearLayout)fragmentView.findViewById(R.id.ControlLayoutTop);

        mVideoScreen = (SpVideoViewHw)fragmentView.findViewById(R.id.VideoView);
        mVideoScreen.clear();
        mVideoScreen.setOnClickListener(this);

        mSwitchCamera = (ImageButton)fragmentView.findViewById(R.id.switch_camera);
        mSwitchCamera.setOnClickListener(this);

        mVideoPause = (Button)fragmentView.findViewById(R.id.video_pause);
        mVideoPause.setOnClickListener(this);
        /*
         * set text upon startup depending of pause flag.
         * if video is paused, indicate it can be sent and vice versa
         */
        mVideoPause.setText(getString(mPauseVideo ? R.string.send_video : R.string.pause_video));

        mVideoStop = (Button)fragmentView.findViewById(R.id.video_stop);
        mVideoStop.setOnClickListener(this);

        // Preview and state icons inside preview picture
        mPreviewSurface = (TextureView)fragmentView.findViewById(R.id.VideoSurfacePreview);
        mPreviewContainer = (FrameLayout)fragmentView.findViewById(R.id.VideoPreviewContainer);
        mPreviewMuteIcon = (ImageView)mPreviewContainer.findViewById(R.id.previewMuteState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCamera = new CameraPreviewControl2(mParent.getApplicationContext(), mPreviewSurface, mPreviewContainer, this);
            if (!mCamera.isCamera2Usable())
                mCamera = new CameraPreviewControllerHw(mPreviewSurface, mPreviewContainer, this);
        }
        else {
            mCamera = new CameraPreviewControllerHw(mPreviewSurface, mPreviewContainer, this);
        }
        if (mCamera.getNumCameras() <= 1)
            mFrontCamera = false;

        mControlExplanation = (TextView)fragmentView.findViewById(R.id.video_explanation);

        mNumberName = (TextView)fragmentView.findViewById(R.id.number);

        mMainSecureState = (TextView)fragmentView.findViewById(R.id.secure_state);
        mPreviewSecureState = (TextView)fragmentView.findViewById(R.id.previewSecureState);

        mVerifySas = (LinearLayout)fragmentView.findViewById(R.id.verify_sas);
        mVerifySas.setOnClickListener(this);

        mVerifySasButton = (TextView) mVerifySas.findViewById(R.id.verify_label);
        if (mVerifySasButton != null) {
            mVerifySasButton.setOnClickListener(this);
        }

        setCallInformation();

        ViewTreeObserver vto = fragmentView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
                    @SuppressWarnings("deprecation")
                    @Override
                    public void onGlobalLayout() {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
                            fragmentView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }
                        else {
                            fragmentView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        computeQuadrantPoints(fragmentView);
                    }
                });

        // No specific handling if incoming video call and fragment restore
        if (incoming && !mVideoAccepted) {
            switchVideoPause(true);             // not accepted yet - don't display preview
        }
        else if (savedInstanceState == null) {  // New fragment and outgoing call
            mVideoAccepted = true;              // this is implicit on outgoing video
            switchVideoOn(false);
        }
        else if (mVideoAccepted) {              // Restored fragment, active video call
            setControlButtonsActive(false);
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        switchMicMute(mCallback.getMuteStateCb());
        activateVideoScreen();
    }

    @Override
    public void onPause() {
        super.onPause();
        deactivateVideoScreen();
    }

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
            mParent = (InCallActivity)activity;
            mCallback = (InCallCallback)activity;
            mCallback.addStateChangeListener(this);
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement InCallCallback.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback.removeStateChangeListener(this);
        mParent = null;
        mCallback = null;
    }

    @Override
    public void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ACCEPTED, mVideoAccepted);
        outState.putBoolean(PAUSED, mPauseVideo);
        outState.putBoolean(FRONT, mFrontCamera);
        outState.putBoolean(EXPLANATION_SHOWN, mVideoExplanationShown);
    }

    @Override
    public void onClick(View view) {
        if (mIsNear)
            return;

        switch (view.getId()) {
            case R.id.VideoDecline:
                if (!mVideoAccepted)
                    stopVideo();
                else
                    endCall();
                break;

            case R.id.VideoAccept:
                mCallback.activateVideoCb(true);
                break;

            case R.id.VideoView:
                toggleButtonsDisplay(true);
                break;

            case R.id.mute:
                // get global state, may have changed by other actions
                boolean isMute = !mCallback.getMuteStateCb();
                TiviPhoneService.doCmd(isMute ? ":mute 1" : ":mute 0");
                switchMicMute(isMute);
                break;

            case R.id.switch_camera:
                switchCamera();
                break;

            case R.id.video_pause:
                mPauseVideo = !mPauseVideo;
                if (mPauseVideo) {
                    deactivateVideoScreen();
                    mVideoPause.setText(getString(R.string.send_video));
                }
                else {
                    activateVideoScreen();
                    mVideoPause.setText(getString(R.string.pause_video));
                }
                switchVideoPause(mPauseVideo);
                break;

            case R.id.video_stop:
                stopVideo();
                break;

            case R.id.verify_sas:
            case R.id.verify_label:
                verifySas();
                break;

            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    /* ****************************************************************************
     * Public functions used by InCallActivity
     **************************************************************************** */
    /**
     * The ZRTP state change listener.
     *
     * @param call the call that changed its ZRTP state.
     * @param msg  the message id of the ZRTP status change.
     */
    public void zrtpStateChange(@NonNull CallState call, TiviPhoneService.CT_cb_msg msg) {
        // The main call screen shows the current active call only
        if (call != TiviPhoneService.calls.selectedCall)
            return;
        switch (msg) {
            case eZRTP_sas:
                setCallInformation(call);
                break;

            case eZRTP_peer:
            case eZRTP_peer_not_verified:
                setCallInformation(call);
                break;

            case eZRTPMsgV:
            case eZRTPMsgA:
            default:
                setCallInformation(call);
        }
    }

    /**
     * The Call state change listener.
     *
     * @param call the call that changed its state.
     * @param msg  the message id of the status change.
     */
    public void callStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) { }

    public void stopVideo() {
        switchVideoOff();
        if (mCallback != null)
            mCallback.removeVideoCb();
    }

    public void checkVideoData(boolean callIsActive) {
        if (mVideoScreen != null)
            mVideoScreen.check(callIsActive);
    }

    /**
     * Handles Proximity sensor state.
     *
     * @param isNear if true then something is near the proximity sensor.
     */
    public void proximityHandler(boolean isNear) {
        if (!mFrontCamera || mPauseVideo)           // don't handle if user already paused video
            return;
        mIsNear = isNear;
        if (isNear)
            deactivateVideoScreen();
        else
            activateVideoScreen();
    }

    /**
     * Call listener if  the user touches the preview window
     */
    @Override
    public void onTouchDown() {
        // Slide away control views but don't move preview yet, user has taken control
        if (mControlLayoutBottom.getVisibility() == View.VISIBLE)
            toggleButtonsDisplay(false);
    }

    /**
     * Call listener if user releases the preview window.
     *
     * In this implementation the 'view' parameter is always the mPreviewContainer.
     *
     * @param view the view to position to the quadrants.
     */
    @Override
    public void onTouchUp(final View view) {
        View parentView = getView();
        if (parentView == null)
            return;

        setQuadrant(view);

        final int newPosX, newPosY;
        switch (mQuadrant) {
            case UPPER_LEFT:
                newPosX = (int)(parentView.getX() + mMargin);
                newPosY = (int)(parentView.getY() + mMargin);
                break;
            case UPPER_RIGHT:
                newPosX = (int)(parentView.getX() + parentView.getMeasuredWidth() - mMargin - view.getMeasuredWidth());
                newPosY = (int)(parentView.getY() + mMargin);
                break;
            case LOWER_LEFT:
                newPosX = (int)(parentView.getX() + mMargin);
                newPosY = (int)(parentView.getY() + parentView.getMeasuredHeight() - mMargin - view.getMeasuredHeight());
                break;
            case LOWER_RIGHT:
                newPosX = (int)(parentView.getX() + parentView.getMeasuredWidth() - mMargin - view.getMeasuredWidth());
                newPosY = (int)(parentView.getY() + parentView.getMeasuredHeight() - mMargin - view.getMeasuredHeight());
                break;
            default:
                newPosX = newPosY = 0;
        }
        view.animate().x(newPosX).y(newPosY);
    }

    /* ****************************************************************************
     * The following section contains private functions
     ***************************************************************************  */

    private void setCallInformation(CallState call) {
        setCallerId(call);
        setSecureText(call);
    }

    private void setCallInformation() {
        CallState call = TiviPhoneService.calls.selectedCall;

        if(call == null) {
            return;
        }

        setCallInformation(call);
    }

    private void setCallerId(CallState call) {
        String zrtpPeer = call.zrtpPEER.toString();
        String bufPeer = call.bufPeer.toString();
        boolean verifySas = call.iShowVerifySas;

        final String nameNum = call.getNameFromAB();
        if (!TextUtils.isEmpty(zrtpPeer) && !verifySas) {
            if (zrtpPeer.equals(nameNum)) {
                mNumberName.setTextColor(InCallMainFragment.mNameNumberTextColorPeerMatch);
            }
            else {
                mNumberName.setTextColor(InCallMainFragment.mNameNumberTextColorNormal);
            }
            mNumberName.setText(zrtpPeer);
        } else if(!TextUtils.isEmpty(bufPeer)) {
            mNumberName.setTextColor(InCallMainFragment.mNameNumberTextColorNormal);
            mNumberName.setText(nameNum);
        }
    }

    private void setSecureText(CallState call) {
        String bufSas = call.bufSAS.toString();
        boolean verifySas = call.iShowVerifySas;

        int secureTextId = verifySas ? R.string.secstate_secure_question
                : R.string.secstate_secure;
        int secureTextColor = verifySas ? InCallMainFragment.mNameNumberTextColorNormal
                : InCallMainFragment.mNameNumberTextColorPeerMatch;

        mMainSecureState.setText(secureTextId);
        mPreviewSecureState.setText(secureTextId);
        mMainSecureState.setTextColor(secureTextColor);
        mPreviewSecureState.setTextColor(secureTextColor);

        if(!TextUtils.isEmpty(bufSas)) {
            mVerifySas.setVisibility(View.VISIBLE);
            mVerifySasButton.setVisibility(verifySas ? View.VISIBLE : View.GONE);

            ((TextView)mVerifySas.findViewById(R.id.sas_text)).setText(bufSas);
            ((TextView)mVerifySas.findViewById(R.id.sas_text)).setTextColor(verifySas ? InCallMainFragment.mNameNumberTextColorNormal :
                    InCallMainFragment.mSasVerifiedColor);
        } else {
            mVerifySas.setVisibility(View.GONE);
        }
    }

    private void verifySas() {
        CallState call = TiviPhoneService.calls.selectedCall;
        String sas = call.bufSAS.toString();

        if (!TextUtils.isEmpty(sas))
            mCallback.verifySasCb(sas, call.iCallId);
    }

    private void endCall() {
        switchVideoOff();
        hideMySelf();
        mCallback.endCallCb(TiviPhoneService.calls.selectedCall);
    }

    private void hideMySelf() {
        if (!isHidden() && mParent != null) {
            FragmentManager fm = mParent.getFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.hide(this).commit();
        }
    }

    private void setControlButtonsActive(boolean suppressAutoRemove) {
        switchMicMute(mCallback.getMuteStateCb());
        switchVideoPause(mPauseVideo);
        mAccept.setVisibility(View.GONE);

        mSwitchCamera.setVisibility(View.VISIBLE);
        mDecline.setImageDrawable(mEndCall);
        mVideoPause.setVisibility(View.VISIBLE);
        mVideoStop.setVisibility(View.VISIBLE);

        mControlLayoutBottom.startAnimation(mSlideOutBottom);
        mSuppressAutoRemove = suppressAutoRemove;
        mControlLayoutTop.startAnimation(mSlideOutTop);
        if (!mVideoExplanationShown) {
            mControlExplanation.setVisibility(View.VISIBLE);
            mVideoExplanationShown = true;
            mControlExplanation.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mControlExplanation.setVisibility(View.INVISIBLE);
                }
            }, SHOW_EXPLANATION_TIME);
        }
    }

    private void switchVideoPause(boolean isVideoPause) {
        mPreviewSurface.setVisibility(isVideoPause ? View.INVISIBLE : View.VISIBLE);
        mPreviewContainer.setBackgroundResource(isVideoPause ? 0 : R.drawable.preview_background);
        mPreviewSecureState.setVisibility(isVideoPause ? View.INVISIBLE : View.VISIBLE);
    }

    private void switchMicMute(boolean isMute) {
        mCallback.setMuteStatusCb(isMute);
        mMuteButton.setImageDrawable(isMute ? mMicMute : mMicOpen);
        if (isMute)
            mMuteButton.setBackgroundColor(ContextCompat.getColor(mParent, R.color.black_blue));
        else
            mMuteButton.setBackgroundResource(0);
        mPreviewMuteIcon.setVisibility(isMute ? View.VISIBLE : View.INVISIBLE);
    }

    synchronized public void switchVideoOn(boolean suppressAutoRemove) {
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        setControlButtonsActive(suppressAutoRemove);
        activateVideoScreen();
        AsyncTasks.asyncCommand("*C" + call.iCallId); // send re-invite for video on
    }

    synchronized public void switchVideoOff() {
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        AsyncTasks.asyncCommand("*c" + call.iCallId);                   // send re-invite for video off
        deactivateVideoScreen();
    }

    /* *********************************************************************
     * Methods to handle the video switching
     * ******************************************************************* */
    private void activateVideoScreen() {
        if (mCamera == null || mCamera.isCapturing() || mParent == null || mCallback == null)
            return;

        boolean isSpeaker = Utilities.isSpeakerOn(mParent.getBaseContext());
        if (!isSpeaker && !mParent.getPhoneService().isHeadsetPlugged() && !mParent.getPhoneService().btHeadsetScoActive()) {
            Utilities.turnOnSpeaker(mParent.getBaseContext(), true, false);
        }
        if (!mPauseVideo && mVideoAccepted) {
            mCamera.setFrontCamera(mFrontCamera);
            mCamera.start(mDegrees);            // If camera is already active this is a no-op
        }
        mCallback.updateProximityCb(false);
    }

    /**
     * Deactivate (pause) video.
     *
     * The function stops the camera and therefor the client does not send any video data
     * to the other client.
     *
     * Some important notes:
     * Several actions call this functions: the user hits the Pause button, the user leaves
     * the activity (pressing the Home button), or we pause the video because of a proximity
     * sensor trigger.
     *
     * The last case is somewhat special:
     * In this case we enter this function with a not acquired proximity sensor wake-lock, thus
     * it does not react to a sensor change to 'near'. Our parent activity (InCallActivity)
     * got a trigger to 'near' and calls to deactivate (pause) the video. After we stop the
     * camera we update the proximity sensor (acquire it), however, the wake-lock does not
     * blank the screen because we acquired the lock after the sensor triggered.
     */
    private void deactivateVideoScreen() {
        if (mCamera != null) {
            mCamera.stop();
        }
        if (mParent == null || mCallback == null)
            return;
        if (!mParent.getPhoneService().isHeadsetPlugged() && !mParent.getPhoneService().btHeadsetScoActive()) {
            Utilities.restoreSpeakerMode(mParent.getBaseContext());
        }
        mCallback.updateProximityCb(true);
    }

    public void switchCamera() {
        if (mCamera == null) {
            return;
        }

        if (mCamera.getNumCameras() <= 1)
            return;
        if (mPauseVideo)         // Don't switch cameras if video is set to pause
            return;

        mCamera.stop();
        mFrontCamera = !mFrontCamera;
        mCamera.setFrontCamera(mFrontCamera);
        mCamera.start(mDegrees);
    }

    private void toggleButtonsDisplay(boolean movePreview) {
        int topHeight = mControlLayoutTop.getMeasuredHeight();
        int bottomHeight = mControlLayoutBottom.getMeasuredHeight();
        if (mQuadrant == INVALID)
            setQuadrant(mPreviewContainer);
        mControlLayoutBottom.removeCallbacks(mRemoveControls);
        if (mControlLayoutBottom.getVisibility() == View.VISIBLE) {
            mControlLayoutBottom.startAnimation(mSlideOutBottom);
            mControlLayoutTop.startAnimation(mSlideOutTop);
            if (movePreview) {
                if (mQuadrant == UPPER_LEFT || mQuadrant == UPPER_RIGHT) {
                    mPreviewContainer.animate().yBy(-topHeight);
                }
                else {
                    mPreviewContainer.animate().yBy(bottomHeight);
                }
            }
        }
        else {
            mControlLayoutBottom.startAnimation(mSlideInBottom);
            mControlLayoutTop.startAnimation(mSlideInTop);
            if (movePreview) {
                if (mQuadrant == UPPER_LEFT || mQuadrant == UPPER_RIGHT) {
                    mPreviewContainer.animate().yBy(topHeight);
                }
                else {
                    mPreviewContainer.animate().yBy(-bottomHeight);
                }
            }
        }
    }

    private void computeQuadrantPoints(View view) {
        int width  = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();
        mLeftHalf = ((int)view.getX() + width) / 2;
        mUpperHalf = ((int)view.getY() + height) / 2;
    }

    private void setQuadrant(View view) {
        int x = (int)view.getX() + view.getMeasuredWidth() / 2;
        int y = (int)view.getY() + view.getMeasuredHeight() / 2;

        if (x < mLeftHalf) {            // view's position is in the left half of the parent view
            mQuadrant = y < mUpperHalf ? UPPER_LEFT : LOWER_LEFT;
        }
        else {                          // View's position is in the right half of the parent view
            mQuadrant = y < mUpperHalf ? UPPER_RIGHT : LOWER_RIGHT;
        }
    }
}
