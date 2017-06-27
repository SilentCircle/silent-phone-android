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
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.method.TransformationMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DRUtils;
import com.silentcircle.common.widget.DataRetentionBanner;
import com.silentcircle.common.widget.SignalQualityIndicator;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.fragments.UserInfoListenerFragment;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.InCallCallback;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.DeviceHandling;
import com.silentcircle.silentphone2.util.RedrawHandler;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.BlurrableImageView;
import com.silentcircle.silentphone2.views.VerifySasWidget;
import com.silentcircle.silentphone2.views.VolumeIndicatorLayout;
import com.silentcircle.silentphone2.views.multiwaveview.GlowPadView;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import zina.ZinaNative;

import static com.silentcircle.silentphone2.R.id.call;

/**
 * This Fragment handles the in call screen and triggers actions.
 *
 * Created by werner on 16.02.14.
 */
public class InCallMainFragment extends UserInfoListenerFragment implements View.OnClickListener,
        TiviPhoneService.ServiceStateChangeListener, TiviPhoneService.DeviceStateChangeListener, 
        GlowPadView.OnTriggerListener, VerifySasWidget.OnSasVerifyClickListener {

    private static final String TAG = InCallMainFragment.class.getSimpleName();

    private Activity mParent;
    private InCallCallback mCallback;
    private boolean mStarted;
    
    /** Call Control buttons */
    private ImageButton mEndCall;
    private GlowPadView mGlowPadView;
    private VolumeIndicatorLayout mVolumeIndicator;

    /** Call state info */
    private TextView mStateLabel;
    private TextView mDuration;

    /** Option buttons on main in-call screen */
    private ImageButton mMute;
    private ImageButton mVideo;
    private ImageButton mAddCall;
    private ImageButton mAudioOptions;

    // The caller/callee avatar and name/number
    private TextView mCallerNumberName;
    private BlurrableImageView mCallerImage;
    private View mRxLed;
    private SignalQualityIndicator mSignalQualityIndicator;

    private Handler mRxLedHandler;
    private Runnable mRxLedUpdater = new Runnable() {
        @Override
        public void run() {
            boolean shouldContinue = updateCallRxLed();
            mRxLedHandler.removeCallbacks(mRxLedUpdater);
            if (shouldContinue) {
                mRxLedHandler.postDelayed(mRxLedUpdater, 25);
            }
        }
    };
    private int pv = -1;
    private int prevPrevAuthFail = -1;

    private Handler mSignalQualityHandler;
    private Runnable mSignalQualityUpdater = new Runnable() {
        @Override
        public void run() {
            boolean shouldContinue = updateCallSignalQualityIndicator();
            mSignalQualityHandler.removeCallbacks(mSignalQualityUpdater);
            if (shouldContinue) {
                mSignalQualityHandler.postDelayed(mSignalQualityUpdater, TimeUnit.SECONDS.toMillis(1));
            }
        }
    };

    private Handler mVolumeIndicatorHandler;
    private Runnable mVolumeIndicatorUpdater = new Runnable() {
        @Override
        public void run() {
            boolean shouldContinue = updateVolumeIndicator();
            mVolumeIndicatorHandler.removeCallbacks(mVolumeIndicatorUpdater);
            if (shouldContinue) {
                mVolumeIndicatorHandler.postDelayed(mVolumeIndicatorUpdater, 100);
            }
        }
    };

    private Handler mUnderflowIndicatorHandler;
    private Runnable mUnderflowIndicatorUpdater = new Runnable() {
        @Override
        public void run() {
            boolean shouldContinue = updateUnderflowIndicator();
            mUnderflowIndicatorHandler.removeCallbacks(mUnderflowIndicatorUpdater);
            if (shouldContinue) {
                mUnderflowIndicatorHandler.postDelayed(mUnderflowIndicatorUpdater, 100);
            }
        }
    };
    private Handler mUnderflowExplainIndicatorHideHandler;
    private Runnable mUnderflowExplainIndicatorHideUpdater = new Runnable() {
        @Override
        public void run() {
            if (mUnderflowExplainIndicator != null) {
                mUnderflowExplainIndicator.setVisibility(View.GONE);
            }
        }
    };

    private RedrawHandler mHandler;

    // The security information area
    private TextView mSasText;
    public static int mSasVerifiedColor;

    private VerifySasWidget mVerifySas;
    private DataRetentionBanner mDataRetentionBanner;

    private View mUnderflowIndicator;
    private View mUnderflowExplainIndicator;

    public static int mNameNumberTextColorNormal;
    public static int mNameNumberTextColorPeerMatch;

    private TextView mSecurityText;
    private static int mSecurityTextColorNormal;
    private static int mSecurityTextColorGreen;
    private static int mSecurityTextColorYellow;

    // Holds the icons that we change depending on state
    private Drawable mMicOpen;
    private Drawable mMicMute;

    private Drawable mSpeakerOn;
    private Drawable mSpeakerOff;
    private Drawable mSpeakerBt;
    private Drawable mSpeakerBtOff;

    private boolean mSpeakerOnly;
    private boolean mIsSelfDrEnabled;
    private boolean mIsPartnerDrEnabled;

    private String mNoVideoAvailableError;
    private String mSecurityStateSecure;
    private String mSecurityStateSecureToServer;
    private String mSecurityStateNotSecure;
    private String mSecurityStateNotVerified;
    private String mCallTypeIncoming;
    private String mCallTypeOutgoing;
    private String mCallTypeUrgent;
    private String mCallTypeEmergency;

    private class RefreshDrStateRunnable implements Runnable {

        private final String mPeerName;
        private final boolean mRefreshUserInfo;

        public RefreshDrStateRunnable(String peerName) {
            this(peerName, false);
        }

        public RefreshDrStateRunnable(String peerName, boolean refreshUserInfo) {
            mPeerName = peerName;
            mRefreshUserInfo = refreshUserInfo;
        }

        @Override
        public void run() {
            boolean isDrEnabled = LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp()
                    // TODO during call show banner when messaging DR is active as well?
                    | LoadUserInfo.isLrmp() | LoadUserInfo.isLrmp() | LoadUserInfo.isLrap();
            mIsSelfDrEnabled = isDrEnabled;

            boolean retentionState = false;
            byte[] partnerUserInfo = mRefreshUserInfo
                    ? ZinaNative.refreshUserData(mPeerName, null)
                    : ZinaNative.getUserInfoFromCache(mPeerName);
            if (partnerUserInfo == null) {
                int[] errorCode = new int[1];
                partnerUserInfo = ZinaNative.getUserInfo(mPeerName, null, errorCode);
            }
            if (partnerUserInfo != null) {
                AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(partnerUserInfo);
                retentionState = DRUtils.isAnyDataRetainedForUser(userInfo);
            }

            mIsPartnerDrEnabled = retentionState;
            isDrEnabled |= retentionState;

            android.os.Message message = android.os.Message.obtain();
            message.arg1 = isDrEnabled ? View.VISIBLE : View.GONE;
            message.what = RedrawHandler.REFRESH_DATA_RETENTION_BANNER;
            mHandler.sendMessage(message);
        }
    };

    private static final TransformationMethod LOWER_CASE_TRANSFORMATION_METHOD = new TransformationMethod() {

        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            return TextUtils.isEmpty(source) ? source : source.toString().toLowerCase();
        }

        @Override
        public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
            int direction, Rect previouslyFocusedRect) {
            // not used
        }
    };

    public InCallMainFragment() {
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mRxLedHandler = new Handler();
        mSignalQualityHandler = new Handler();
        mVolumeIndicatorHandler = new Handler();
        mUnderflowIndicatorHandler = new Handler();
        mUnderflowExplainIndicatorHideHandler = new Handler();
        mHandler = new RedrawHandler(new RedrawHandler.RedrawRunnable() {
            @Override
            public void run() {
                if (mDataRetentionBanner != null) {
                    CallState call = TiviPhoneService.calls.selectedCall;
                    int visibility = getFlags() == View.VISIBLE ? View.VISIBLE : View.GONE;
//                    if (call != null && call.mPeerDisclosureFlag) {
//                        visibility = View.VISIBLE;
//                    }
                    mDataRetentionBanner.setVisibility(visibility);
                }
            }
        });

        mNoVideoAvailableError = getString(R.string.no_video_available);
        mSecurityStateSecure = getString(R.string.secstate_secure);
        mSecurityStateSecureToServer = getString(R.string.to_server_only);
        mSecurityStateNotSecure = getString(R.string.secstate_not_secure);
        mSecurityStateNotVerified = getString(R.string.secstate_secure_question);
        mCallTypeIncoming = getString(R.string.type_incoming);
        mCallTypeOutgoing = getString(R.string.type_outgoing);
        mCallTypeUrgent = getString(R.string.urgent_call);
        mCallTypeEmergency = getString(R.string.emergency_call);
    }

    @Override
    @SuppressWarnings("ResourceType")       // Otherwise AS report some issue with array.getXxxx functions
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final Resources.Theme theme = mParent.getTheme();
        final TypedArray array = theme != null ? theme.obtainStyledAttributes(new int[]{
                R.attr.sp_ic_mic,
                R.attr.sp_ic_mic_muted,
                R.attr.sp_ic_volume_on,
                R.attr.sp_ic_volume_muted,
                R.attr.sp_ic_volume_bt,
                R.attr.sp_ic_volume_bt_muted,
                R.attr.sp_sas_verified_color,
                R.attr.sp_dial_text_color,
                R.attr.sp_name_text_peer_match,
                R.attr.sp_sec_info_green,
                R.attr.sp_sec_info_yellow
        }) : null;

        if (array != null) {
            mMicOpen = array.getDrawable(0);
            mMicMute = array.getDrawable(1);
            mSpeakerOn = array.getDrawable(2);
            mSpeakerOff = array.getDrawable(3);
            mSpeakerBt = array.getDrawable(4);
            mSpeakerBtOff = array.getDrawable(5);
            mSasVerifiedColor = array.getColor(6, ContextCompat.getColor(mParent, R.color.white_translucent));
            mNameNumberTextColorNormal = ContextCompat.getColor(mParent, android.R.color.white);
            mNameNumberTextColorPeerMatch = array.getColor(8, ContextCompat.getColor(mParent, R.color.black_green));

            mSecurityTextColorGreen = array.getColor(9, ContextCompat.getColor(mParent, R.color.black_green));
            mSecurityTextColorYellow = array.getColor(10, ContextCompat.getColor(mParent, R.color.black_yellow));
            mSecurityTextColorNormal = mNameNumberTextColorNormal;
            array.recycle();
        }
        else {
            // The deprecated version of the functions set the theme to null
            mMicOpen = ContextCompat.getDrawable(mParent, R.drawable.ic_action_mic_dark);
            mMicMute = ContextCompat.getDrawable(mParent, R.drawable.ic_action_mic_muted_dark);
            mSpeakerOn = ContextCompat.getDrawable(mParent, R.drawable.ic_action_volume_on_dark);
            mSpeakerOff = ContextCompat.getDrawable(mParent, R.drawable.ic_action_volume_muted_dark);
            mSpeakerBt = ContextCompat.getDrawable(mParent, R.drawable.ic_action_volume_bt_dark);
            mSpeakerBtOff = ContextCompat.getDrawable(mParent, R.drawable.ic_action_volume_bt_muted_dark);
            mSasVerifiedColor = ContextCompat.getColor(mParent, R.color.white_translucent);
            mNameNumberTextColorNormal = ContextCompat.getColor(mParent, android.R.color.white);
            mNameNumberTextColorPeerMatch = ContextCompat.getColor(mParent, android.R.color.holo_green_dark);

            mSecurityTextColorGreen = ContextCompat.getColor(mParent, R.color.black_green);
            mSecurityTextColorYellow = ContextCompat.getColor(mParent, R.color.black_yellow);
            mSecurityTextColorNormal = mNameNumberTextColorNormal;
        }

        final View fragmentView = inflater.inflate(R.layout.incall_main_fragment, container, false);
        if (fragmentView == null)
            return null;

        mSpeakerOnly = getResources().getBoolean(R.bool.has_speaker_only);

        mEndCall = (ImageButton)fragmentView.findViewById(R.id.hangup);
        mEndCall.setOnClickListener(this);
        mEndCall.bringToFront();

        mGlowPadView = (GlowPadView)fragmentView.findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(this);

        mVolumeIndicator = (VolumeIndicatorLayout) fragmentView.findViewById(R.id.volume_indicator);

        mMute = (ImageButton)fragmentView.findViewById(R.id.mute_image);

        mVideo = (ImageButton) fragmentView.findViewById(R.id.video_image);
        mVideo.setOnClickListener(this);

        mAddCall = (ImageButton) fragmentView.findViewById(R.id.add_image);
        mAddCall.setOnClickListener(this);

        ImageButton chat = (ImageButton) fragmentView.findViewById(R.id.start_chat_image);
        chat.setOnClickListener(this);

        mAudioOptions = (ImageButton)fragmentView.findViewById(R.id.audio_image);

        // Mute and audio option buttons have touch listeners, no OnClick listeners
        setTouchListeners();

        mStateLabel = (TextView)fragmentView.findViewById(R.id.callStateLabel);
        mDuration = (TextView)fragmentView.findViewById(R.id.elapsedTime);
        
        mCallerNumberName = (TextView)fragmentView.findViewById(R.id.caller_text);
        mCallerNumberName.setSelected(true);    // Necessary to enable horizontal scrolling on long names/numbers

        mCallerImage = (BlurrableImageView) fragmentView.findViewById(R.id.caller_image);

        mRxLed = fragmentView.findViewById(R.id.rx_led);
        mSignalQualityIndicator = (SignalQualityIndicator) fragmentView.findViewById(R.id.callStateIcon);

        mVerifySas = (VerifySasWidget) fragmentView.findViewById(R.id.sas_widget);
        mDataRetentionBanner = (DataRetentionBanner) fragmentView.findViewById(R.id.data_retention_status);

        mUnderflowIndicator = fragmentView.findViewById(R.id.underflow_indicator);
        mUnderflowExplainIndicator = fragmentView.findViewById(R.id.underflow_explain_widget);
        mUnderflowExplainIndicator.setOnClickListener(this);

        mSecurityText = (TextView) fragmentView.findViewById(R.id.sec_info1);
        mSecurityText.setTransformationMethod(LOWER_CASE_TRANSFORMATION_METHOD);
        mSasText = (TextView) fragmentView.findViewById(R.id.sas_text1);
        mSasText.setOnClickListener(this);

        setInitialInCallScreen(savedState);
        return fragmentView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mStarted = true;
        if (mSpeakerOnly && mCallback != null) {
            switchSpeaker(true, true);
        }
        showCall(TiviPhoneService.calls.selectedCall);
        processTesting();
    }

    @Override
    public void onStop() {
        super.onStop();
        mStarted = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshScreen();
        refreshSecurityFields(TiviPhoneService.calls.selectedCall);
        if (TiviPhoneService.calls.selectedCall != null) {
            mRxLedHandler.removeCallbacks(mRxLedUpdater);
            mRxLedHandler.postDelayed(mRxLedUpdater, 25);

            mSignalQualityHandler.removeCallbacks(mSignalQualityUpdater);
            mSignalQualityHandler.postDelayed(mSignalQualityUpdater, TimeUnit.SECONDS.toMillis(1));

            mVolumeIndicatorHandler.removeCallbacks(mVolumeIndicatorUpdater);
            mVolumeIndicatorHandler.postDelayed(mVolumeIndicatorUpdater, 100);

            mUnderflowIndicatorHandler.removeCallbacks(mUnderflowIndicatorUpdater);
            mUnderflowIndicatorHandler.postDelayed(mUnderflowIndicatorUpdater, 100);
        }
        if (TiviPhoneService.calls.selectedCall != null) {
            mDataRetentionBanner.setVisibility(mIsSelfDrEnabled ? View.VISIBLE : View.GONE);
            updateDataRetentionBanner(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mRxLedHandler.removeCallbacks(mRxLedUpdater);
        mSignalQualityHandler.removeCallbacks(mSignalQualityUpdater);
        mVolumeIndicatorHandler.removeCallbacks(mVolumeIndicatorUpdater);
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
        mParent = activity;
        try {
            mCallback = (InCallCallback)activity;
            mCallback.addStateChangeListener(this);
            mCallback.addDeviceChangeListener(this);
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement InCallCallback.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback.removeStateChangeListener(this);
        mCallback.removeDeviceChangeListener(this);
        mCallback = null;
        mParent = null;
    }

    @Override
    public void onClick(View view) {
        CallState call;
        switch (view.getId()) {
            case R.id.hangup:
                endCall();
                break;

            case R.id.video_image:
                if(!LoadUserInfo.canInitiateVideo(mParent)) {
                    showDialog(R.string.information_dialog, LoadUserInfo.getDenialStringResId(),
                            android.R.string.ok, -1);
                    return;
                }

                call = TiviPhoneService.calls.selectedCall;
                if (call != null && !call.sdesActive && !TextUtils.isEmpty(call.bufSAS.toString()))
                    activateVideo();
                else {
                    Toast.makeText(mParent, mNoVideoAvailableError, Toast.LENGTH_LONG).show();
                }
                break;

            case R.id.add_image:            // Add another call. Clear some fields and buttons
                if(!LoadUserInfo.canStartConference(mParent)) {
                    showDialog(R.string.information_dialog, LoadUserInfo.getDenialStringResId(),
                            android.R.string.ok, -1);
                    return;
                }

                call = TiviPhoneService.calls.selectedCall;
                if (call == null)
                    return;
                if (mCallback != null) {
                    doAddCall(call);
                }
                break;

            case R.id.audio_image:
                break;

            case R.id.start_chat_image:
                Intent intent = Action.VIEW_CONVERSATIONS.intent(getActivity(), DialerActivity.class);
                intent.putExtra(DialerActivity.EXTRA_FROM_CALL, true);
                startActivity(intent);
                break;

            case R.id.sas_text:
            case R.id.verify_label:
            case R.id.btn_verify_sas:
            case R.id.sas_text1:
                call = TiviPhoneService.calls.selectedCall;
                if (call == null)
                    return;
                verifySas(call.iCallId);
                break;

            case R.id.underflow_explain_widget:
                showDialog(R.string.network_trouble_title, R.string.network_trouble_underflow_description,
                        android.R.string.ok, -1);
                break;

            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    @Override
    public void onSasVerifyClick(View view) {
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null) {
            return;
        }
        verifySas(call.iCallId);
    }

    // GlowView state listener
    @Override
    public void onGrabbed(View v, int handle) { }

    @Override
    public void onReleased(View v, int handle) {
        mGlowPadView.ping();
    }

    @Override
    public void onTrigger(View v, int target) {
        final int resId = mGlowPadView.getResourceIdForTarget(target);
        switch (resId) {
            case R.drawable.ic_lockscreen_answer:
                mCallback.answerCallCb();
                mGlowPadView.setVisibility(View.GONE);
                break;

            case R.drawable.ic_lockscreen_decline:
                endCall();
                mGlowPadView.setVisibility(View.GONE);
                break;
            default:
                // Code should never reach here.
        }
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) { }

    @Override
    public void onFinishFinalAnimation() { }

    @Override
    public void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        if (!isAdded()) {
            return;
        }
        updateDataRetentionBanner(false);
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
                call.sdesActive = false;
                showSecurityFields(call);
                // TODO: Enable this when the peer disclosure bug is resolved
                /*
                   Don't show banner immediately, update user info if disclosure flag
                   does not match known retention state for remote party.

                   This would avoid showing DR banner when only peer disclosure flag indicates data
                   retention without having this information in Silent Manager.

                if (call.mPeerDisclosureFlag && mDataRetentionBanner != null) {
                    mDataRetentionBanner.setVisibility(View.VISIBLE);
                }
                 */
                break;

            case eZRTP_peer:
            case eZRTP_peer_not_verified:
                showSecurityFields(call);
                break;

            case eZRTPMsgV:
            case eZRTPMsgA:
            default:
                showSecurityFields(call);
        }
    }

    /**
     * The Call state change listener.
     *
     * @param call the call that changed its state.
     * @param msg  the message id of the status change.
     */
    public void callStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
        // The main call screen shows the current active call only
        if (call != TiviPhoneService.calls.selectedCall)
            return;
        switch (msg) {
            case eStartCall:
                setAudioOptionsButton();
                break;

            case eEndCall:
                mVerifySas.setVisibility(View.INVISIBLE);
                mEndCall.setVisibility(View.GONE);
                mEndCall.setEnabled(false);
                mVolumeIndicator.setVisibility(View.GONE);
                return;

            case eIncomingCall:
                zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTPMsgA); // check for possible SDES

                updateDataRetentionBanner(false);
                break;

            default:
                break;
        }
        showCall(call);
    }

    // Restore buttons that depend on global states
    public void refreshScreen() {
        if (mCallback == null)
            return;
        final boolean isMute = mCallback.getMuteStateCb();
        mMute.setImageDrawable(isMute ? mMicMute : mMicOpen);
        mMute.setPressed(isMute);

        setAudioOptionsButton();
    }

    /**
     * Refresh the security information for this, possibly different call.
     *
     * The function first hides data from previous call state, then shows the state for the call.
     *
     * @param call refresh security state fields for this call
     */
    public void refreshSecurityFields(CallState call) {
        hideSecurityFields();
        showSecurityFields(call);
    }

    public void showSecurityFields(CallState call) {
        // No need to update the view if this is not the selected call
        if (call == null || call != TiviPhoneService.calls.selectedCall)
            return;

        mSecurityText.setVisibility(View.VISIBLE);
        mSecurityText.setTextColor(mSecurityTextColorNormal);

        mCallerNumberName.setTextColor(mNameNumberTextColorNormal);

        if ("SECURE SDES".equals(call.bufSecureMsg.toString())) {
            call.sdesActive = true;
            mSecurityText.setText(mSecurityStateSecure + " " + mSecurityStateSecureToServer);
            mSecurityText.setTextColor(mSecurityTextColorYellow);
            mCallerImage.setBlurred(false);
            return;
        }

        mSecurityText.setText(Utilities.translateZrtpStateMsg(mParent, call));

        if (!call.sdesActive && call.iActive) {
            mSecurityText.setText(mSecurityStateNotSecure);
        }

        String sas = call.bufSAS.toString();
        if (!sas.isEmpty()) {
            mSasText.setText(sas);
            mVerifySas.setSasPhrase(sas);
            boolean remoteRetentionState = getRetentionState(Utilities.getPeerName(call));
            boolean retentionState = mIsSelfDrEnabled | remoteRetentionState;
            /*
             * Check whether current know DR state matches ZRTP peer disclosure flag
             * and update DR info if it does not.
             *
             * This will also help to ensure that up-to-date user information is available for
             * remote party.
             */
//            // TODO: Remove this when the peer disclosure bug is resolved
//            if (remoteRetentionState != call.mPeerDisclosureFlag) {
//                retentionState |= call.mPeerDisclosureFlag;
//                updateDataRetentionBanner(true);
//            }
            mVerifySas.setDataRetentionState(retentionState);

            mVideo.setVisibility(View.VISIBLE);

            // Show two security indicators because we have a ZRTP SAS. Switch on indicator1 as
            // well in case we had no SDES security before (should not happen on SC infrastructure)
            if (call.iShowVerifySas) {
                mVerifySas.setVisibility(View.VISIBLE);
                mVerifySas.setOnSasVerifyClickListener(this);

                mSecurityText.setTextColor(mSecurityTextColorNormal);
                mSecurityText.setText(mSecurityStateNotVerified);
                if (Character.isLetter(sas.charAt(0)) || Character.isDigit(sas.charAt(0))) {
                    mSasText.setTextColor(mNameNumberTextColorNormal);
                }
                mCallerImage.setBlurred(true);
            }
            else {
                mSasText.setText(sas);
                mSasText.setVisibility(View.VISIBLE);
                // At this point: SAS verified.
                // If we have a ZRTP peer string then use it and display it in numberName field.
                //
                // If ZRTP peer name is equal to name in call state then change the color.
                //
                // When the user verifies SAS he/she may edit the proposed ZRTP peername. The proposed
                // name is either the SIP display name or the name we got from contacts (getNameFromAB())
                // If the user did not edit the proposed name then switch color to green to display the
                // fact the ZRTP peername and SIP display/contact name are the same.
                // If the user edited the ZRTP peer name then use normal color
                String zrtpPeer = call.zrtpPEER.toString();
                if (!TextUtils.isEmpty(zrtpPeer)) {
                    String nameNum = call.getNameFromAB();
                    /* For now don't colour the peer name green on match
                    if (zrtpPeer.equals(nameNum)) {
                        mCallerNumberName.setTextColor(mNameNumberTextColorPeerMatch);
                    }
                    else {
                        mCallerNumberName.setTextColor(mNameNumberTextColorNormal);
                    }
                     */
                    mCallerNumberName.setText(call.zrtpPEER.toString());
                    mVerifySas.setUserName(call.zrtpPEER.toString());
                }
                mVerifySas.setVisibility(View.INVISIBLE);
                mSecurityText.setText(R.string.secstate_secure);
                mSecurityText.setTextColor(mSecurityTextColorGreen);

                if (Character.isLetter(sas.charAt(0)) || Character.isDigit(sas.charAt(0))) {
                    mSasText.setTextColor(mSasVerifiedColor);
                }

                mCallerImage.setBlurred(false);
            }
        }
    }

    /**
     * Set or change fields to display the active (selected) call.
     *
     * Called from call state change handling or from InCall activity. The call manager
     * may change the active (selected) call. Call manager reports this to InCall activity
     * which takes appropriate actions.
     */
    public void showCall(CallState call) {
        if (!isAdded()) {
            return;
        }

        // No need to update the view if this is not the selected call
        if (call == null || call != TiviPhoneService.calls.selectedCall)
            return;

        showFunctionButtons(call.iActive, !TextUtils.isEmpty(call.bufSAS.toString()));  // set the functions button (video, add call)
        setAnswerEndCallButton(call);

        Utilities.setCallerImage(call, mCallerImage);
        if (!call.iActive) {
            mSecurityText.setTextColor(mSecurityTextColorNormal);
            mSecurityText.setText(call.bufMsg.toString());
        }
        String stateLabel = call.iIsIncoming ? mCallTypeIncoming : mCallTypeOutgoing;
        View view = getView();
        if (call.iIsIncoming && view != null) {
            TextView priority = (TextView)view.findViewById(R.id.call_priority);
            switch (call.mPriority) {
                case CallState.NORMAL:
                    priority.setVisibility(View.GONE);
                    mStateLabel.setTextColor(ContextCompat.getColor(mParent, R.color.incall_accent_color));
                    break;
                case CallState.URGENT:
                    priority.setVisibility(View.VISIBLE);
                    priority.setTextColor(ContextCompat.getColor(mParent, R.color.black_yellow));
                    priority.setText(mCallTypeUrgent);
                    break;
                case CallState.EMERGENCY:
                    priority.setVisibility(View.VISIBLE);
                    priority.setTextColor(ContextCompat.getColor(mParent, R.color.q_orange));
                    priority.setText(mCallTypeEmergency);
                    break;
            }
        }
        mStateLabel.setText(stateLabel);
        if (mStarted)
            mDuration.postDelayed(mUpdate, 1000);

        // Get the SIP display name if not already done, may overwrite previous display (raw number)
        String s = call.getNameFromAB();
        if (!TextUtils.isEmpty(s)) {
            setCallNumberField(s);
        }
        if (call.secExceptionMsg != null && mParent != null) {
            Toast.makeText(mParent, call.secExceptionMsg, Toast.LENGTH_LONG).show();
            call.secExceptionMsg = null;
        }

        mRxLedHandler.removeCallbacks(mRxLedUpdater);
        mSignalQualityHandler.removeCallbacks(mSignalQualityUpdater);
        mVolumeIndicatorHandler.removeCallbacks(mVolumeIndicatorUpdater);
        mUnderflowIndicatorHandler.removeCallbacks(mUnderflowIndicatorUpdater);

        mRxLedHandler.post(mRxLedUpdater);
        mSignalQualityHandler.post(mSignalQualityUpdater);
        mVolumeIndicatorHandler.post(mVolumeIndicatorUpdater);
        mUnderflowIndicatorHandler.post(mUnderflowIndicatorUpdater);
    }

    /**
     * Device state change listener.
     *
     * Currently handles wired head set and bluetooth handling, later we add docking.
     */
    public void deviceStateChange(int state) {
        switch (state) {

            case TiviPhoneService.EVENT_WIRED_HEADSET_PLUG:
                // inCall dialer manages speaker state if it is visible, thus don't restore but switch on
                // without storing state
                if (!mCallback.getPhoneService().isHeadsetPlugged()) {
                    // if the state is "not connected", restore the speaker state.
                    Utilities.restoreSpeakerMode(mParent);
                    setAudioOptionsButton();
                    mCallback.updateProximityCb(true);
                }
                else {
                    // if the state is "connected", force the speaker off without storing the state.
                    switchSpeaker(false, false);
                }
                break;

            case TiviPhoneService.EVENT_BT_HEADSET_SCO_OFF:
                int v = TiviPhoneService.doCmd("set.samplerate=48000"); // no-op if already on 48kHz
                if(ConfigurationUtilities.mTrace) Log.d(TAG, "BT off event - set_rate 48000: " + v);
                setAudioOptionsButton();
                break;

            case TiviPhoneService.EVENT_BT_HEADSET_REMOVED:
            case TiviPhoneService.EVENT_BT_HEADSET_ADDED:
            case TiviPhoneService.EVENT_BT_HEADSET_SCO_ON:
                setAudioOptionsButton();
                break;

            default:
                break;
        }
    }

    /**
     * Clear some buttons and fields and then start the in-call dialer.
     */
    public void doAddCall(CallState call) {
        hideSecurityFields();
        showFunctionButtons(false, false);
        mCallerNumberName.setText(null);
        mCallback.addCallCb(call);
    }

    private Runnable mUpdate = new Runnable() {
        @Override
        public void run() {
            if (mStarted) {
                mDuration.setText(Utilities.getDurationString(TiviPhoneService.calls.selectedCall));
                mDuration.postDelayed(mUpdate, 1000);
            }
        }
    };

    private void setInitialInCallScreen(Bundle savedState) {

        Bundle bundle = getArguments();
        // No bundle? Probably an unknown call state - strange - some error handling here
        if (bundle == null)
            return;

        int callCnt = TiviPhoneService.calls.getCallCount();
        int callType = bundle.getInt(TiviPhoneService.CALL_TYPE);

        boolean isOcaCall = bundle.getBoolean(TiviPhoneService.IS_OCA_CALL);

        if(isOcaCall) {
            DialerActivity.mShowCreditDialog = true;
        }

        // On outgoing call, we may not yet have a selected call, we take care of this later
        CallState call = TiviPhoneService.calls.selectedCall;

        // Remove restart state handling: InCallActivity blocks back button (adds own handling) thus
        // we don't have a real restart anymore (Notification Intent also points to InCallActivity)
        if (savedState == null &&
                ((callType == TiviPhoneService.CALL_TYPE_INCOMING && callCnt == 1) ||
                (callType == TiviPhoneService.CALL_TYPE_OUTGOING && callCnt <= 1))) {
            mMute.setImageDrawable(mMicOpen);
            TiviPhoneService.doCmd(":mute 0");
            mCallback.setMuteStatusCb(false);
            showFunctionButtons(false, false);          // Disable functions buttons video, add call
        }
        if (callType != TiviPhoneService.CALL_TYPE_OUTGOING) {
            mParent.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
        if (call != null && callType == TiviPhoneService.CALL_TYPE_INCOMING && call.mustShowAnswerBT()) {
            mCallback.getPhoneService().onIncomingCall(mParent);
            mCallback.setActiveCallNotificationCb(call);
        }
        final boolean speakerOn = Utilities.isSpeakerOn(mParent.getBaseContext());
        DeviceHandling.setAecMode(mParent.getBaseContext(), speakerOn);
        mAudioOptions.setPressed(speakerOn);

        // Because phone service starts this activity it may happen that 'call' is null,
        // depending on thread scheduling. In any case try to get some information to show on screen.
        String caller = null;
        if (call != null) {
            caller = call.getNameFromAB();
        }
        if (caller == null) {
            caller = bundle.getString(TiviPhoneService.CALL_NAME);
        }
        caller = Utilities.removeUriPartsSelective(caller);

        // These two functions set the UI in case of outgoing calls. May be overwritten during
        // onResume for incoming calls.
        setCallNumberField(caller);
        setAnswerEndCallButton(call);
    }

    private void endCall() {
        mEndCall.setVisibility(View.GONE);
        mEndCall.setEnabled(false);
        mVolumeIndicator.setVisibility(View.GONE);
        mCallback.endCallCb(TiviPhoneService.calls.selectedCall);

        updateDataRetentionBanner(false);
    }

    private void verifySas(int callId) {
        CharSequence text = mVerifySas.getSasPhrase();
        if (text != null)
            mCallback.verifySasCb(text.toString(), callId);
    }

    public void activateVideo() {
        mCallback.activateVideoCb(false);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    private void setCallNumberField(String number) {
        if (TextUtils.isEmpty(number))
            number = Contact.UNKNOWN_DISPLAY_NAME;

        String formatted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            formatted = PhoneNumberUtils.formatNumber(number, Locale.getDefault().getCountry());
        else
            formatted = PhoneNumberUtils.formatNumber(number);
        formatted = (TextUtils.isEmpty(formatted) ? number : formatted);
        mCallerNumberName.setText(formatted);
        mVerifySas.setUserName(formatted);
    }

    private void setAnswerEndCallButton(CallState call) {
        if (call != null && call.mustShowAnswerBT()) {
            mGlowPadView.setVisibility(View.VISIBLE);
            mEndCall.setVisibility(View.GONE);
            mEndCall.setEnabled(false);
            mVolumeIndicator.setVisibility(View.GONE);

        }
        else {
            mGlowPadView.setVisibility(View.GONE);
            mEndCall.setVisibility(View.VISIBLE);
            mVolumeIndicator.setVisibility(View.VISIBLE);
            mEndCall.setEnabled(true);
        }
    }

    private void showFunctionButtons(boolean on, boolean videoOn) {
        if (!on) {
            mAddCall.setVisibility(View.GONE);
            mVideo.setVisibility(View.GONE);
        }
        else {
            mAddCall.setVisibility(View.VISIBLE);
            if (videoOn)
                mVideo.setVisibility(View.VISIBLE);
            else
                mVideo.setVisibility(View.GONE);
        }
    }

    /*
     * Simplify handling of speaker switching
     */
    private void switchSpeaker(boolean onOff, boolean storeState) {
        switchSpeaker(onOff, storeState, false);
    }

    private void switchSpeaker(boolean onOff, boolean storeState, boolean force) {
        if (!force && mCallback.getPhoneService().btHeadsetScoActive())    // Don't switch to speaker if BT is active
            return;
        Utilities.turnOnSpeaker(mParent.getBaseContext(), onOff, storeState);
        setAudioOptionsButton();
        mCallback.updateProximityCb(true);
    }

    /*
     * Simplify handling of Bluetooth headset switching.
     *
     * Because of Android BT limitations w must switch back to 16kHz sample rate. The normal
     * audio on Android uses 48kHz because this is guaranteed on all Android devices, 16kHz not
     * always for speaker, only on BT.
     */
    private void switchBtHeadset(boolean on) {

        if (!mCallback.getPhoneService().hasBtHeadSet())
            return;

        if (on) {
            switchSpeaker(false, true);         // Switch off speaker and keep it off on restoreSpeaker
            int v = TiviPhoneService.doCmd("set.samplerate=16000");
            if(ConfigurationUtilities.mTrace) Log.d(TAG, "BT on - set_rate 16000: " + v);
            mCallback.getPhoneService().bluetoothHeadset(true);
        }
        else {
            mCallback.getPhoneService().bluetoothHeadset(false);
            int v = TiviPhoneService.doCmd("set.samplerate=48000");
            if(ConfigurationUtilities.mTrace)Log.d(TAG, "BT off - set_rate 48000: " + v);
            Utilities.restoreSpeakerMode(mParent.getBaseContext());
            setAudioOptionsButton();
        }
        mCallback.updateProximityCb(true);
    }

    private void setAudioOptionsButton() {
        boolean isSpeaker = Utilities.isSpeakerOn(mParent.getBaseContext());

        if (mCallback != null && mCallback.getPhoneService() != null
                && mCallback.getPhoneService().hasBtHeadSet()) {
            mAudioOptions.setImageDrawable(isSpeaker ? mSpeakerBt : mSpeakerBtOff);
        }
        else{
            mAudioOptions.setImageDrawable(isSpeaker ? mSpeakerOn : mSpeakerOff);
        }
        mAudioOptions.setPressed(isSpeaker);

        boolean isMute = mCallback != null && mCallback.getMuteStateCb();
        mMute.setPressed(isMute);
        mMute.setImageDrawable(isMute ? mMicMute : mMicOpen);

    }

    private PopupMenu mPopupMenu;
    private void selectAudioMenu() {
        if (mPopupMenu == null) {
            mPopupMenu = new PopupMenu(mParent, mAudioOptions);
            mPopupMenu.inflate(R.menu.audio_options);       // Radio button menu, only one option active at a time

            Menu menu = mPopupMenu.getMenu();

            boolean earPhone = !mCallback.getPhoneService().btHeadsetScoActive() && !Utilities.isSpeakerOn(mParent.getBaseContext());
            if (mSpeakerOnly) {
                earPhone = false;
                menu.findItem(R.id.audio_ear_phone).setEnabled(false);
            }
            boolean btHeadset = mCallback.getPhoneService().btHeadsetScoActive() && !Utilities.isSpeakerOn(mParent.getBaseContext());
            boolean speaker = !mCallback.getPhoneService().btHeadsetScoActive() && Utilities.isSpeakerOn(mParent.getBaseContext());

            if (earPhone && btHeadset && speaker) {
                Log.w(TAG, "Inconsistent state of audio options, defaulting to earphone");
                btHeadset = speaker = false;
            }
            MenuItem menuItem;
            if (btHeadset) {
                menuItem = menu.findItem(R.id.audio_bt);
            }
            else if (speaker) {
                menuItem = menu.findItem(R.id.audio_speaker);
            }
            else {
                menuItem = menu.findItem(R.id.audio_ear_phone);
            }
            if (menuItem != null)
                menuItem.setChecked(true);

            mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {

                    refreshScreen();
                    switch (item.getItemId()) {
                        case R.id.audio_bt:
                            if (!item.isChecked()) {
                                if (!mCallback.getPhoneService().btHeadsetScoActive())
                                    switchBtHeadset(true);                  // checks and switches speaker too
                                item.setChecked(true);
                            }
                            return true;
                        case R.id.audio_speaker:
                            if (!item.isChecked()) {
                                if (mCallback.getPhoneService().btHeadsetScoActive())
                                    switchBtHeadset(false);
                                switchSpeaker(true, true, true);            // force speaker switch
                                item.setChecked(true);
                            }
                            return true;
                        case R.id.audio_ear_phone:
                            if (!item.isChecked()) {
                                if (mCallback.getPhoneService().btHeadsetScoActive())
                                    switchBtHeadset(false);
                                switchSpeaker(false, true);
                                item.setChecked(true);
                            }
                            return true;
                        default:
                            return onOptionsItemSelected(item);
                    }
                }
            });
            mPopupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
                @Override
                public void onDismiss(PopupMenu menu) {
                    setAudioOptionsButton();
                }
            });
        }
        mPopupMenu.show();
    }

    private void hideSecurityFields() {
        mVerifySas.setVisibility(View.INVISIBLE);
        mSecurityText.setVisibility(View.GONE);
        mSasText.setVisibility(View.GONE);
    }

    /* *********************************************************************
     * Methods to setup and handle call window buttons. Some buttons need
     * specific touch handler.
     * ******************************************************************* */

    private void setTouchListeners() {
        mMute.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    return true;
                if (event.getAction() != MotionEvent.ACTION_UP)
                    return false;

                // get global state, may have changed by other actions
                boolean isMute = !mCallback.getMuteStateCb();
                mMute.setPressed(isMute);
                TiviPhoneService.doCmd(isMute ? ":mute 1" : ":mute 0");
                mMute.setImageDrawable(isMute ? mMicMute : mMicOpen);
                mCallback.setMuteStatusCb(isMute);
                return true;
            }
        });

        mAudioOptions.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    return true;
                if (event.getAction() != MotionEvent.ACTION_UP)
                    return false;

                boolean isSpeaker = Utilities.isSpeakerOn(mParent.getBaseContext());
                if (mCallback.getPhoneService().hasBtHeadSet()) {
                    selectAudioMenu();
                    return true;
                }
                isSpeaker = !isSpeaker;
                if (mSpeakerOnly)
                    isSpeaker = true;
                Utilities.turnOnSpeaker(mParent.getBaseContext(), isSpeaker, true);
                setAudioOptionsButton();
                return true;
            }
        });
    }

    private Runnable mAnswerer = new Runnable() {
        @Override
        public void run() {
            if (mCallback != null) {
                mCallback.answerCallCb();
                DialerActivity.mAutoAnsweredTesting++;
                mCallerNumberName.postDelayed(mTerminator, 15 * 1000);
            }
        }
    };

    private Runnable mTerminator = new Runnable() {
        @Override
        public void run() {
            if (mCallback != null)
                mCallback.endCallCb(TiviPhoneService.calls.selectedCall);
        }
    };

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                    .add(infoMsg, TAG)
                    .commitAllowingStateLoss();
        }
    }

    private void processTesting() {
        if (!ConfigurationUtilities.mEnableDevDebOptions)
            return;
        // Check for auto-answer on incoming call
        if (DialerActivity.mAutoAnswerForTesting && TiviPhoneService.calls.selectedCall != null &&
                TiviPhoneService.calls.selectedCall.mustShowAnswerBT()) {
                mCallerNumberName.postDelayed(mAnswerer, 1000);
        }
    }

    private boolean updateCallRxLed() {
        boolean result = false;
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null || call.uiStartTime == 0) {
            if (mRxLed != null) {
                mRxLed.setVisibility(View.GONE);
            }
        } else {
            String info = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.rxLed");
            if (!TextUtils.isEmpty(info)) {
                String[] data = info.split(":");

                if (data.length >= 2) {
                    int v = Integer.valueOf(data[0]);
                    int prevAuthFail = Integer.valueOf(data[1]);

                    if(prevPrevAuthFail != prevAuthFail || pv != v) {
                        float fv = v * 0.005f + .35f; // Maps [0, 130] to [0.35, 1] for alpha
                        if (mRxLed != null) {
                            mRxLed.setVisibility(View.VISIBLE);
                            mRxLed.setAlpha(fv);
                        }
                    }

                    pv = v;
                    prevPrevAuthFail = prevAuthFail;
                }
            }

            result = true;
        }

        return result;
    }

    private boolean updateCallSignalQualityIndicator() {
        boolean result = false;
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null || call.uiStartTime == 0) {
            if (mSignalQualityIndicator != null) {
                mSignalQualityIndicator.setVisibility(View.GONE);
            }
        } else {
            String info = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.bars");
            if (!TextUtils.isEmpty(info)) {
                char antenna = info.charAt(0);
                if (mSignalQualityIndicator != null) {
                    mSignalQualityIndicator.setVisibility(View.VISIBLE);
                    mSignalQualityIndicator.setQuality(antenna);
                }
            }
            result = true;
        }
        return result;
    }

    private boolean updateVolumeIndicator() {
        boolean result = false;
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null || call.uiStartTime == 0) {
            if (mVolumeIndicator != null) {
                mVolumeIndicator.setVisibility(View.GONE);
            }
        } else {
            String info = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.audio.volume_voice");
            if (!TextUtils.isEmpty(info)) {
                String[] data = info.split("_");

                if (data.length >= 2) {
                    int volume = Integer.valueOf(data[0]); // 0 to 9
                    // int voiceQuality = Integer.valueOf(data[1]); // 2 - Good, 1 - Normal, 0 - Not voice

                    // TODO: Do we only care about voice?
                    // if (voiceQuality > 0) {
                        mVolumeIndicator.setVolume(volume);
                    // }
                }
            }

            result = true;
        }

        return result;
    }

    private boolean updateUnderflowIndicator() {
        boolean result = false;
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null || call.uiStartTime == 0) {
            if (mUnderflowIndicator != null) {
                mUnderflowIndicator.setVisibility(View.GONE);
            }
        } else {
            String info = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.underflow");
            if (!TextUtils.isEmpty(info)) {
                int isUnderflow = info.charAt(0);

                if (isUnderflow == '1') {
                    if (mUnderflowIndicator != null) {
                        mUnderflowIndicator.setVisibility(View.VISIBLE);
                    }

                    if (mUnderflowExplainIndicator != null) {
                        mUnderflowExplainIndicator.setVisibility(View.VISIBLE);
                        mUnderflowExplainIndicatorHideHandler.removeCallbacks(mUnderflowExplainIndicatorHideUpdater);
                        mUnderflowExplainIndicatorHideHandler.postDelayed(mUnderflowExplainIndicatorHideUpdater, 60000);
                    }
                } else {
                    if (mUnderflowIndicator != null) {
                        mUnderflowIndicator.setVisibility(View.GONE);
                    }
                }
            }

            result = true;
        }

        return result;
    }

    private boolean getRetentionState(String peerName) {
        boolean retentionState = false;
        if (Utilities.canMessage(peerName)) {
            byte[] mPartnerUserInfo = ZinaNative.getUserInfoFromCache(peerName);
            if (mPartnerUserInfo != null) {
                AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(mPartnerUserInfo);
                retentionState = DRUtils.isAnyDataRetainedForUser(userInfo);
                mIsPartnerDrEnabled = retentionState;
            }
        }
        return retentionState;
    }

    private void updateDataRetentionBanner(boolean refreshUserInfo) {
        String peerName = Utilities.getPeerName(TiviPhoneService.calls.selectedCall);
        mDataRetentionBanner.clearConversationPartners();
        mDataRetentionBanner.addConversationPartner(peerName);
        mDataRetentionBanner.refreshBannerTitle();
        AsyncUtils.execute(new RefreshDrStateRunnable(peerName, refreshUserInfo));
    }

}
