/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.InCallCallback;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.DeviceHandling;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.multiwaveview.GlowPadView;

import java.util.Locale;


/**
 * This Fragment handles the in call screen and triggers actions.
 *
 * Created by werner on 16.02.14.
 */
public class InCallMainFragment extends Fragment implements View.OnClickListener,
        TiviPhoneService.ServiceStateChangeListener, TiviPhoneService.DeviceStateChangeListener, 
        GlowPadView.OnTriggerListener{

    private static final String TAG = InCallMainFragment.class.getSimpleName();

    private Activity mParent;
    private InCallCallback mCallback;
    private boolean mStarted;
    
    /** Call Control buttons */
    private ImageButton mEndCall;
    private GlowPadView mGlowPadView;

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
    private ImageView mCallerImage;

    // The security information area
    private TextView mSasText;
    private int mSasVerifiedColor;
    private TextView mVerifyLabel;

    public static int mNameNumberTextColorNormal;
    public static int mNameNumberTextColorPeerMatch;

    private TextView mSecurityText;
    private int mSecurityTextColorNormal;
    private int mSecurityTextColorGreen;
    private int mSecurityTextColorYellow;

    // Holds the icons that we change depending on state
    private Drawable mMicOpen;
    private Drawable mMicMute;

    private Drawable mSpeakerOn;
    private Drawable mSpeakerOff;
    private Drawable mSpeakerBt;
    private Drawable mSpeakerBtOff;

    private boolean mSpeakerOnly;

    public InCallMainFragment() {
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        Resources.Theme theme = mParent.getTheme();
        if (theme != null) {
            final TypedArray array = theme.obtainStyledAttributes(new int[]{
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
            });
            if (array != null) {
                mMicOpen = array.getDrawable(0);
                mMicMute = array.getDrawable(1);
                mSpeakerOn = array.getDrawable(2);
                mSpeakerOff = array.getDrawable(3);
                mSpeakerBt = array.getDrawable(4);
                mSpeakerBtOff = array.getDrawable(5);
                mSasVerifiedColor = array.getColor(6, R.color.white_translucent);
                mNameNumberTextColorNormal = array.getColor(7, android.R.color.white);
                mNameNumberTextColorPeerMatch = array.getColor(8, R.color.black_green);

                mSecurityTextColorNormal = mNameNumberTextColorNormal;
                mSecurityTextColorGreen = array.getColor(9, R.color.black_green);
                mSecurityTextColorYellow = array.getColor(10, R.color.black_yellow);
                array.recycle();
            }
        }
        else {
            mMicOpen = getResources().getDrawable(R.drawable.ic_action_mic_dark);
            mMicMute = getResources().getDrawable(R.drawable.ic_action_mic_muted_dark);
            mSpeakerOn = getResources().getDrawable(R.drawable.ic_action_volume_on_dark);
            mSpeakerOff = getResources().getDrawable(R.drawable.ic_action_volume_muted_dark);
            mSpeakerBt = getResources().getDrawable(R.drawable.ic_action_volume_bt_dark);
            mSpeakerBtOff = getResources().getDrawable(R.drawable.ic_action_volume_bt_muted_dark);
            mSasVerifiedColor = getResources().getColor(R.color.white_translucent);
            mNameNumberTextColorNormal = getResources().getColor(android.R.color.white);
            mNameNumberTextColorPeerMatch = getResources().getColor(android.R.color.holo_green_dark);

            mSecurityTextColorNormal = mNameNumberTextColorNormal;
            mSecurityTextColorGreen = getResources().getColor(R.color.black_green);
            mSecurityTextColorYellow = getResources().getColor(R.color.black_yellow);
        }

        final View fragmentView = inflater.inflate(R.layout.incall_main_fragment, container, false);
        if (fragmentView == null)
            return null;

        mSpeakerOnly = getResources().getBoolean(R.bool.has_speaker_only);

        mEndCall = (ImageButton)fragmentView.findViewById(R.id.hangup);
        mEndCall.setOnClickListener(this);

        mGlowPadView = (GlowPadView)fragmentView.findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(this);

        mMute = (ImageButton)fragmentView.findViewById(R.id.mute_image);

        mVideo = (ImageButton) fragmentView.findViewById(R.id.video_image);
        mVideo.setOnClickListener(this);

        mAddCall = (ImageButton) fragmentView.findViewById(R.id.add_image);
        mAddCall.setOnClickListener(this);

        mAudioOptions = (ImageButton)fragmentView.findViewById(R.id.audio_image);

        if (mSpeakerOnly)
            switchSpeaker(true, true);

        // Mute and audio option buttons have touch listeners, no OnClick listeners
        setTouchListeners();

        mStateLabel = (TextView)fragmentView.findViewById(R.id.callStateLabel);
        mDuration = (TextView)fragmentView.findViewById(R.id.elapsedTime);
        
        mCallerNumberName = (TextView)fragmentView.findViewById(R.id.caller_text);
        mCallerNumberName.setSelected(true);    // Necessary to enable horizontal scrolling on long names/numbers

        mCallerImage = (ImageView)fragmentView.findViewById(R.id.caller_image);

        mSasText = (TextView)fragmentView.findViewById(R.id.sas_text);
        mSasText.setOnClickListener(this);
        mVerifyLabel = (TextView)fragmentView.findViewById(R.id.verify_label);

        mSecurityText = (TextView)fragmentView.findViewById(R.id.sec_info);

        setInitialInCallScreen(savedState);
        return fragmentView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mStarted = true;
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
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
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
                call = TiviPhoneService.calls.selectedCall;
                if (call != null && !call.sdesActive && !call.iShowVerifySas)
                    activateVideo();
                else {
                    Toast.makeText(mParent, getString(R.string.no_video_available), Toast.LENGTH_LONG).show();
                }
                break;

            case R.id.add_image:            // Add another call. Clear some fields and buttons
                call = TiviPhoneService.calls.selectedCall;
                if (call == null)
                    return;
                if (mCallback != null) {
                    doAddCall(call);
                }
                break;

            case R.id.audio_image:
                break;

            case R.id.sas_text:
            case R.id.verify_label:
                verifySas();
                break;

            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    // GlowView state listener
    @Override
    public void onGrabbed(View v, int handle) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onReleased(View v, int handle) {
        mGlowPadView.ping();
    }

    @Override
    public void onTrigger(View v, int target) {
        final int resId = mGlowPadView.getResourceIdForTarget(target);
        switch (resId) {
            case R.drawable.ic_call_dark:
                mCallback.answerCallCb();
                mGlowPadView.setVisibility(View.GONE);
                break;

            case R.drawable.ic_end_call_dark:
                endCall();
                mGlowPadView.setVisibility(View.GONE);
                break;
            default:
                // Code should never reach here.
        }
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onFinishFinalAnimation() {
        // TODO Auto-generated method stub

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
    public void zrtpStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
        // The main call screen shows the current active call only
        if (call != TiviPhoneService.calls.selectedCall)
            return;
        switch (msg) {
            case eZRTP_sas:
                call.sdesActive = false;
                showSecurityFields(call);
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
                mEndCall.setVisibility(View.GONE);
                mEndCall.setEnabled(false);
                return;

            case eIncomingCall:
                zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTPMsgA); // check for possible SDES
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

        mSecurityText.setTextColor(mSecurityTextColorYellow);
        mSecurityText.setVisibility(View.VISIBLE);
        mVerifyLabel.setVisibility(View.VISIBLE);

        if ("SECURE SDES".equals(call.bufSecureMsg.toString())) {
            call.sdesActive = true;
            mSecurityText.setText(getString(R.string.secstate_secure));
            mVerifyLabel.setText(getString(R.string.to_server_only));
            return;
        }
        mVerifyLabel.setText(Utilities.translateZrtpStateMsg(mParent, call));
        if (!call.sdesActive && call.iActive)
            mSecurityText.setText(getString(R.string.secstate_not_secure));

        if (!call.bufSAS.toString().isEmpty()) {
            mSasText.setText(call.bufSAS.toString());
            mSasText.setVisibility(View.VISIBLE);

            // Show two security indicators because we have a ZRTP SAS. Switch on indicator1 as
            // well in case we had no SDES security before (should not happen on SC infrastructure)
            if (call.iShowVerifySas) {
                mSecurityText.setText(getString(R.string.secstate_secure_question));
                mSecurityText.setTextColor(mSecurityTextColorNormal);
                mVerifyLabel.setText(getString(R.string.verify_label));
                mVerifyLabel.setVisibility(View.VISIBLE);
                mVerifyLabel.setOnClickListener(this);
                mSasText.setTextColor(mNameNumberTextColorNormal);
            }
            else {
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
                    if (zrtpPeer.equals(nameNum)) {
                        mCallerNumberName.setTextColor(mNameNumberTextColorPeerMatch);
                    }
                    else {
                        mCallerNumberName.setTextColor(mNameNumberTextColorNormal);
                    }
                    mCallerNumberName.setText(call.zrtpPEER.toString());
                }
                mVerifyLabel.setVisibility(View.GONE);
                mSecurityText.setText(getString(R.string.secstate_secure));
                mSecurityText.setTextColor(mSecurityTextColorGreen);
                mVideo.setVisibility(View.VISIBLE);

                mSasText.setTextColor(mSasVerifiedColor);
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
        // No need to update the view if this is not the selected call
        if (call == null || call != TiviPhoneService.calls.selectedCall)
            return;

        showFunctionButtons(call.iActive, !call.iShowVerifySas);  // set the functions button (video, add call)
        setAnswerEndCallButton(call);
        setCallNumberField(call.bufPeer.toString());
        // if call.mContentLoaderActive -> schedule a delayed lookup to possibly overwrite the default avatar
        if (call.mContactsLoaderActive)
            mCallerImage.postDelayed(new SetImageHelper(call), 100);
        Utilities.setCallerImage(call, mCallerImage);
        if (!call.iActive) {
            mVerifyLabel.setVisibility(View.VISIBLE);
            mVerifyLabel.setText(call.bufMsg.toString());
        }
        String stateLabel = getString(call.iIsIncoming ? R.string.type_incoming : R.string.type_outgoing);
        if (call.iIsIncoming) {
            TextView priority = (TextView)getView().findViewById(R.id.call_priority);
            switch (call.mPriority) {
                case CallState.NORMAL:
                    priority.setVisibility(View.GONE);
                    mStateLabel.setTextColor(getResources().getColor(R.color.incall_accent_color));
                    break;
                case CallState.URGENT:
                    priority.setVisibility(View.VISIBLE);
                    priority.setTextColor(getResources().getColor(R.color.black_yellow));
                    priority.setText(getString(R.string.urgent_call));
                    break;
                case CallState.EMERGENCY:
                    priority.setVisibility(View.VISIBLE);
                    priority.setTextColor(getResources().getColor(R.color.q_orange));
                    priority.setText(getString(R.string.emergency_call));
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

    /* ****************************************************************************
     * The following section contains private functions
     **************************************************************************** */

    private class SetImageHelper implements Runnable {
        CallState mCall;

        SetImageHelper(CallState call) {
            mCall = call;
        }
        @Override
        public void run() {
            if (mCall.mContactsLoaderActive)
                mCallerImage.postDelayed(new SetImageHelper(mCall), 100);
            else {
                Utilities.setCallerImage(mCall, mCallerImage);
                if (mCall.mustShowAnswerBT() && mCallback != null)
                    mCallback.setActiveCallNotificationCb(mCall);        // call may have new caller info to show
            }
        }
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
            mCallback.getPhoneService().onIncomingCall();
            mCallback.setActiveCallNotificationCb(call);
        }
        final boolean speakerOn = Utilities.isSpeakerOn(mParent.getBaseContext());
        DeviceHandling.setAecMode(mParent.getBaseContext(), speakerOn);
        mAudioOptions.setPressed(speakerOn);

        // Because phone service starts this activity it may happen that 'call' is null,
        // depending on thread scheduling. In any case try to get some information to show on screen.
        String caller = null;
        if (call != null) {
            if (call.bufDialed.getLen() > 0)
                caller = call.bufDialed.toString();
            else if (call.bufPeer.getLen() > 0)
                caller = call.bufPeer.toString();
        }

        if (caller == null) {
            caller = bundle.getString(TiviPhoneService.CALL_NAME);
        }
        caller = Utilities.removeSipParts(caller);

        // These two functions set the UI in case of outgoing calls. May be overwritten during
        // onResume for incoming calls.
        setCallNumberField(caller);
        setAnswerEndCallButton(call);
    }

    private void endCall() {
        mEndCall.setVisibility(View.GONE);
        mEndCall.setEnabled(false);
        mCallback.endCallCb(TiviPhoneService.calls.selectedCall);
    }

    private void verifySas() {
        CharSequence text = mSasText.getText();
        if (text != null)
            mCallback.verifySasCb(text.toString());
    }

    private void activateVideo() {
        mCallback.activateVideoCb();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    private void setCallNumberField(String number) {
        if (TextUtils.isEmpty(number))
            return;
        String formatted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            formatted = PhoneNumberUtils.formatNumber(number, Locale.getDefault().getCountry());
        else
            formatted = PhoneNumberUtils.formatNumber(number);
        formatted = (TextUtils.isEmpty(formatted) ? number : formatted);
        mCallerNumberName.setText(formatted);
    }

    private void setAnswerEndCallButton(CallState call) {
        if (call != null && call.mustShowAnswerBT()) {
            mGlowPadView.setVisibility(View.VISIBLE);
            mEndCall.setVisibility(View.GONE);
            mEndCall.setEnabled(false);

        }
        else {
            mGlowPadView.setVisibility(View.GONE);
            mEndCall.setVisibility(View.VISIBLE);
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
        if (mCallback.getPhoneService().hasBtHeadSet()) {
            mAudioOptions.setImageDrawable(isSpeaker ? mSpeakerBt : mSpeakerBtOff);
        }
        else{
            mAudioOptions.setImageDrawable(isSpeaker ? mSpeakerOn : mSpeakerOff);
        }
        mAudioOptions.setPressed(isSpeaker);

        boolean isMute = mCallback.getMuteStateCb();
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
        mSecurityText.setVisibility(View.GONE);
        mSasText.setVisibility(View.GONE);
        mVerifyLabel.setVisibility(View.GONE);
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

    private void processTesting() {
        if (!ConfigurationUtilities.mEnableDevDebOptions)
            return;
        // Check for auto-answer on incoming call
        if (DialerActivity.mAutoAnswerForTesting && TiviPhoneService.calls.selectedCall != null &&
                TiviPhoneService.calls.selectedCall.mustShowAnswerBT()) {
                mCallerNumberName.postDelayed(mAnswerer, 1000);
        }
    }
}
