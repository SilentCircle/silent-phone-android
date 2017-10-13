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
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.CardView;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DRUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.DataRetentionBanner;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.fragments.BaseFragment;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.InCallCallback;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ManageCallStates;
import com.silentcircle.silentphone2.util.RedrawHandler;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.CircleImageSelectable;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

import java.util.ArrayList;

import zina.ZinaNative;

/**
 * The fragment handles the call manager UI and related actions.
 *
 * Created by werner on 28.02.14.
 */
public class CallManagerFragment extends BaseFragment implements TiviPhoneService.ServiceStateChangeListener {

    private static final String TAG = CallManagerFragment.class.getSimpleName();

    /* Priority for this view to handle message broadcasts. */
    private static final int BROADCAST_PRIORITY = 1;

    private Activity mParent;
    private InCallCallback mCallback;

    private ArrayList<CallState> mPrivateArray = new ArrayList<>(5);
    private ArrayList<CallState> mConfArray = new ArrayList<>(5);
    private ArrayList<CallState> mInOutArray = new ArrayList<>(5);

    // Holds the icons that we change depending on state
    private Drawable mMicOpen;
    private Drawable mMicMute;

    private Drawable mSpeakerOn;
    private Drawable mSpeakerOff;

    private LinearLayout mMainLayout;
    private ScrollView mScrollView;
    private DataRetentionBanner mDataRetentionBanner;

    private int mSasVerifiedColor;

    private int mSecurityTextColorNormal;
    private int mSecurityTextColorGreen;
    private int mSecurityTextColorYellow;

    /** If not null this is the last call that call manager set to un-hold, could be a conference call */
    private CallState mLastCallUnHold;
    private boolean mConferenceOnHold = true;

    private int mNormalColor;
    private int mActiveColor;
    private int mDragActiveColor;
    private int mDragEnteredColor;

    private boolean mIsSelfDrEnabled;
    private RedrawHandler mHandler;

    private MessagingBroadcastReceiver mViewUpdater;

    private final View.OnDragListener mCallViewDragListener = new View.OnDragListener() {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            return handleDragEventCallView(v, event);
        }
    };

    public CallManagerFragment(){}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Indicate that this fragment would like to influence the set of actions in the action bar.
        setHasOptionsMenu(true);
        mHandler = new RedrawHandler(new RedrawHandler.RedrawRunnable() {

            @Override
            public void run() {
                if (mDataRetentionBanner != null) {
                    mDataRetentionBanner.setVisibility(getFlags() == View.VISIBLE ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Resources.Theme theme = mParent.getTheme();

        mNormalColor = ContextCompat.getColor(mParent, R.color.incall_call_banner_background_color);
        mActiveColor = ContextCompat.getColor(mParent, R.color.black_blue);
        mDragActiveColor = ContextCompat.getColor(mParent, R.color.dialpad_secondary_text_color_dark);
        mDragEnteredColor = ContextCompat.getColor(mParent, R.color.black_green);

        final TypedArray array = theme != null ? theme.obtainStyledAttributes(R.styleable.SpaStyle) : null;

        if (array != null) {
            mSasVerifiedColor = array.getColor(R.styleable.SpaStyle_sp_sas_verified_color, ContextCompat.getColor(mParent, R.color.white_translucent));
            mSecurityTextColorNormal = ContextCompat.getColor(mParent, android.R.color.white);
            mSecurityTextColorGreen = array.getColor(R.styleable.SpaStyle_sp_sec_info_green, ContextCompat.getColor(mParent, R.color.black_green));
            mSecurityTextColorYellow = array.getColor(R.styleable.SpaStyle_sp_sec_info_yellow, ContextCompat.getColor(mParent, R.color.black_yellow));

            mMicOpen = array.getDrawable(R.styleable.SpaStyle_sp_ic_mic_menu);
            mMicMute = array.getDrawable(R.styleable.SpaStyle_sp_ic_mic_muted_menu);
            mSpeakerOn = array.getDrawable(R.styleable.SpaStyle_sp_ic_volume_on_menu);
            mSpeakerOff = array.getDrawable(R.styleable.SpaStyle_sp_ic_volume_muted_menu);
            array.recycle();
        }
        else {
            mSasVerifiedColor = ContextCompat.getColor(mParent, R.color.white_translucent);
            mSecurityTextColorNormal = ContextCompat.getColor(mParent, android.R.color.white);
            mSecurityTextColorGreen = ContextCompat.getColor(mParent, R.color.black_green);
            mSecurityTextColorYellow = ContextCompat.getColor(mParent, R.color.black_yellow);

            mMicOpen = ContextCompat.getDrawable(mParent, R.drawable.ic_action_mic_light);
            mMicMute = ContextCompat.getDrawable(mParent, R.drawable.ic_action_mic_muted_light);
            mSpeakerOn = ContextCompat.getDrawable(mParent, R.drawable.ic_action_volume_on_light);
            mSpeakerOff = ContextCompat.getDrawable(mParent, R.drawable.ic_action_volume_muted_light);
        }

        mMicOpen = DrawableCompat.wrap(mMicOpen);
        DrawableCompat.setTint(mMicOpen, ViewUtil.getColorFromAttributeId(mParent, R.attr.sp_actionbar_title_text_color));
        mMicMute = DrawableCompat.wrap(mMicMute);
        DrawableCompat.setTint(mMicMute, ViewUtil.getColorFromAttributeId(mParent, R.attr.sp_actionbar_title_text_color));
        mSpeakerOn = DrawableCompat.wrap(mSpeakerOn);
        DrawableCompat.setTint(mSpeakerOn, ViewUtil.getColorFromAttributeId(mParent, R.attr.sp_actionbar_title_text_color));
        mSpeakerOff = DrawableCompat.wrap(mSpeakerOff);
        DrawableCompat.setTint(mSpeakerOff, ViewUtil.getColorFromAttributeId(mParent, R.attr.sp_actionbar_title_text_color));

        View view = inflater.inflate(R.layout.call_manager_fragment, container, false);
        mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);
        mDataRetentionBanner = (DataRetentionBanner) view.findViewById(R.id.data_retention_status);
        mMainLayout = (LinearLayout)view.findViewById(R.id.main_layout);
        if (mMainLayout == null)
            return null;

        View dragView = mMainLayout.findViewById(R.id.conf_header_explanation);
        dragView.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                return handleDragEvent(v, event, true /* to conference */);
            }
        });
        dragView = mMainLayout.findViewById(R.id.private_header_explanation);
        dragView.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                return handleDragEvent(v, event, false /* remove from conference */);
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupCallLists();
        createAndBindViews(R.id.conf_header, mConfArray);
        createAndBindViews(R.id.private_header, mPrivateArray);
        createAndBindViews(R.id.in_out_header, mInOutArray);
//        updateDataRetentionBanner();
        registerViewUpdater();
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterMessagingReceiver(mViewUpdater);
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.callmanager, menu);
        super.onCreateOptionsMenu(menu, inflater);
        Context context = getActivity();
        if (context != null) {
            ViewUtil.tintMenuIcons(context, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mCallback != null) {
            final boolean muted = mCallback.getMuteStateCb();
            MenuItem item = menu.findItem(R.id.call_manager_mic);
            item.setIcon(muted ? mMicMute : mMicOpen);

            final boolean speaker = Utilities.isSpeakerOn(mParent);
            item = menu.findItem(R.id.call_manager_speaker);
            item.setIcon(speaker ? mSpeakerOn : mSpeakerOff);

            updateCallMuteStatus();
        }
    }

        // InCallActivity handles the Menu selection
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        if (!isAdded()) {
            return;
        }
//        updateDataRetentionBanner();
    }

    @Nullable
    private CardView createCallView(CallState call, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mParent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final CardView rowView = (CardView)inflater.inflate(R.layout.call_manager_line, parent, false);
        if (rowView == null)
            return null;
        rowView.setOnDragListener(mCallViewDragListener);
        return bindCallView(rowView, call);
    }

    final private View.OnClickListener mRowClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            CallState call = (CallState) v.getTag();
            toggleHoldState(call);
        }
    };

    final private View.OnLongClickListener mRowLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            CallState call = (CallState)v.getTag();
            if (mPrivateArray.contains(call)) {
                startDraggingToConference((CallState) v.getTag(), (CardView) v);
                return true;
            }
            else if (mConfArray.contains(call)) {
                startDraggingToPrivate((CallState) v.getTag(), (CardView) v);
                return true;
            }
            else
                return false;
        }
    };

    private CardView bindCallView(CardView rowView, CallState call) {
        if (rowView == null) {
            return null;
        }

        rowView.setTag(call);

        TextView textView = (TextView) rowView.findViewById(R.id.caller_text);
        String name = call.getNameFromAB();
        if (TextUtils.isEmpty(name))
            name = call.bufPeer.toString();
        textView.setText(!TextUtils.isEmpty(name) ? name : Contact.UNKNOWN_DISPLAY_NAME);
        textView.setSelected(true);

        TextView securityText = (TextView) rowView.findViewById(R.id.sec_info);
        securityText.setTextColor(mSecurityTextColorYellow);
        securityText.setVisibility(View.VISIBLE);

        TextView verifyLabel = (TextView) rowView.findViewById(R.id.verify_label);
        verifyLabel.setVisibility(View.VISIBLE);
        if ("SECURE SDES".equals(call.bufSecureMsg.toString())) {
            call.sdesActive = true;
            securityText.setText(getString(R.string.secstate_secure));
            verifyLabel.setText(getString(R.string.to_server_only));
        }
        else {
            // Keep the indicator on if we have SDES security
            if (call.sdesActive)
                securityText.setVisibility(View.VISIBLE);
            verifyLabel.setText(Utilities.translateZrtpStateMsg(mParent, call));
        }
        if (!call.sdesActive && !call.mustShowAnswerBT())
            securityText.setText(getString(R.string.secstate_not_secure));

        textView = (TextView) rowView.findViewById(R.id.sas_text);
        if (!call.bufSAS.toString().isEmpty()) {            // we have a SAS code
            textView.setText(call.bufSAS.toString());
            textView.setVisibility(View.VISIBLE);
            textView.setTag(call);
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    verifySas((CallState) v.getTag());
                }
            });
            // Show two security indicators because we have a ZRTP SAS. Switch on indicator1 as
            // well in case we had no SDES security before (should not happen on SC infrastructure)
            if (call.iShowVerifySas) {
                verifyLabel.setText(getString(R.string.verify_label));
                securityText.setText(getString(R.string.secstate_secure_question));
                securityText.setTextColor(mSecurityTextColorNormal);
            }
            else {
                verifyLabel.setVisibility(View.GONE);
                securityText.setText(getString(R.string.secstate_secure));
                securityText.setTextColor(mSecurityTextColorGreen);
                textView.setTextColor(mSasVerifiedColor);

            }
        }
        ImageButton btn = (ImageButton)rowView.findViewById(R.id.CallMngEndCall);
        CircleImageSelectable image = (CircleImageSelectable) rowView.findViewById(R.id.caller_image);
        Utilities.setCallerImage(call, image);
        btn.setTag(call);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                endCall(true, (CallState) view.getTag());
            }
        });

        btn = (ImageButton) rowView.findViewById(R.id.CallMngAnswerCall);
        // if must show answer button then it's an incoming call
        if (call.mustShowAnswerBT()) {
            btn.setTag(call);
            btn.setVisibility(View.VISIBLE);
            btn.setEnabled(true);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    answerCall(true, (CallState) view.getTag());
                }
            });
        }
        else {
            btn.setVisibility(View.INVISIBLE);
            btn.setEnabled(false);

            if (call.uiStartTime > 0) {
                rowView.setCardBackgroundColor(call.iIsOnHold ? mNormalColor : mActiveColor);
                rowView.setOnClickListener(mRowClick);
                rowView.setOnLongClickListener(mRowLongClick);
            }
        }
        View dataRetentionStatus = rowView.findViewById(R.id.data_retention_status);
        boolean retentionState = /**getRetentionState(Utilities.getPeerName(call), false,
                call.mPeerDisclosureFlag);**/false;
        dataRetentionStatus.setVisibility(retentionState ? View.VISIBLE : View.GONE);
        // if peer disclosure flag does not match partner's retention state, try to refresh user
        // information
//        // TODO: Remove this when the peer disclosure bug is resolved
//        if (retentionState != call.mPeerDisclosureFlag) {
//            updateDataRetentionBanner();
//        }

        updateCallMuteStatus(rowView, call, mCallback.getMuteStateCb());

        return rowView;
    }

    private void updateCallMuteStatus() {
        final boolean micMuteStatus = mCallback.getMuteStateCb();
        for (CallState call : mConfArray) {
            final CardView rowView = findViewForCall(call);
            if (rowView != null) {
                updateCallMuteStatus(rowView, call, micMuteStatus);
            }
        }
        for (CallState call : mPrivateArray) {
            final CardView rowView = findViewForCall(call);
            if (rowView != null) {
                updateCallMuteStatus(rowView, call, micMuteStatus);
            }
        }
        for (CallState call : mInOutArray) {
            final CardView rowView = findViewForCall(call);
            if (rowView != null) {
                updateCallMuteStatus(rowView, call, micMuteStatus);
            }
        }
    }

    private void updateCallMuteStatus(final @NonNull CardView rowView,
            final @NonNull CallState call, boolean micMuteStatus) {
        ImageView callStatusMuted = (ImageView) rowView.findViewById(R.id.call_status_muted);
        callStatusMuted.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        callStatusMuted.setImageResource(micMuteStatus || call.iMuted || call.iIsOnHold
                ? R.drawable.ic_mic_off_white_24dp : R.drawable.ic_mic_white_24dp);
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
        // mFullAdapter.notifyDataSetChanged();
        CardView callView = findViewForCall(call);
        if (callView != null)
            bindCallView(callView, call);
    }

    /**
     * The Call state change listener.
     *
     * @param call the call that changed its state.
     * @param msg  the message id of the status change.
     */
    public void callStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
        if (call == null) {
            Log.e(TAG, "Call state is null, call action: " + msg);
            return;
        }
        switch (msg) {
            case eStartCall:
                answerCall(false, call);
                break;

            case eEndCall:
                endCall(false, call);
                return;

            case eIncomingCall:
                if (!mInOutArray.contains(call)) {
                    mInOutArray.add(call);
                    mMainLayout.findViewById(R.id.in_out_header).setVisibility(View.VISIBLE);
                    final int sectionIndex = getIndexOfSection(R.id.in_out_header) + 1;
                    CardView callView = createCallView(call, mMainLayout);
                    if (callView != null) {
                        mMainLayout.addView(callView, sectionIndex);
                    }
                }
//                updateDataRetentionBanner();
                return;

            case eCalling:
                break;

            default:
                Log.d(TAG, "Unexpected call state change event: " + msg);
                break;
        }
        final CardView callView = findViewForCall(call);
        if (callView != null)
            bindCallView(callView, call);
    }

    public void setLastCallUnHold(CallState call) {
        mLastCallUnHold = call;
    }

    private void verifySas(CallState call) {
        if (call.iIsOnHold)
            return;
        mCallback.verifySasCb(call.bufSAS.toString(), call.iCallId);
    }

    /* *********************************************************************
     * Call handling functions
     * ******************************************************************* */

    private synchronized void answerCall(boolean userPressed, @NonNull CallState call) {

        if (userPressed) {
            TiviPhoneService.doCmd("*a" + call.iCallId);
            return;
        }

        // If users answers a call and another call is selected then put that
        // call on hold and set answered call as selected call.
        if (call != TiviPhoneService.calls.selectedCall) {
            CallState otherCall = TiviPhoneService.calls.selectedCall;
            if (otherCall != null) {
                setCallToHold(otherCall, true);
            }
            final CardView callView = findViewForCall(otherCall);

            // can be null if call list not yet populated
            if (callView != null) {
                bindCallView(callView, otherCall);
                callView.invalidate();
            }

            mCallback.activeCallChangedCb(null, call, false);
            setCallToHold(call, false);
        }

        mInOutArray.remove(call);
        mPrivateArray.add(call);
        setConferenceOnHold();

        final CardView callView = findViewForCall(call);
        // can be null if call list not yet populated
        if (callView != null && mMainLayout != null) {
            mMainLayout.removeView(callView);

            final int sectionIndex = getIndexOfSection(R.id.private_header) + 1;
            mMainLayout.addView(callView, sectionIndex);

            mMainLayout.findViewById(R.id.conf_header_explanation).setVisibility(View.VISIBLE);
            if (mInOutArray.size() <= 0) {
                mMainLayout.findViewById(R.id.in_out_header).setVisibility(View.GONE);
            }
            bindCallView(callView, call);
        }
    }


    /**
     * End a call.
     *
     * This functions may be called twice: first if user presses the end-call button and
     * a second time it SIP signals the real call termination via call state change.
     *
     * If the remote party terminates the call the function gets called once only via
     * call state change.
     *
     * @param userPressed {@code true} if called via user actions, {@code false} if called
     *                    via SIP call state change
     * @param call the call state object
     */
    private synchronized void endCall(boolean userPressed, CallState call) {
        if (call == null) {
            Log.w(TAG, "Call Manager: Null call during end call processing: " + userPressed);
            return;
        }
        // Get the total number of active calls before we remove the terminating call
        // from its list.
        int totalCount = mConfArray.size() + mPrivateArray.size() + mInOutArray.size();

//        updateDataRetentionBanner();

        // Now remove call from its list and remove section if it's empty now
        if (mInOutArray.contains(call)) {
            mInOutArray.remove(call);
            if (mInOutArray.size() <= 0) {
                mMainLayout.findViewById(R.id.in_out_header).setVisibility(View.GONE);
            }
        }
        else if (mPrivateArray.contains(call)) {
            mPrivateArray.remove(call);
        }
        else if (mConfArray.contains(call)) {
            mConfArray.remove(call);
        }
        else {
            // This could happen: if the SIP state engine calls us if the call was really
            // terminated from SIP point of view. We removed the call already when we called
            // this function after the user pressed the end call button.
            return;
        }
        final View callView = findViewForCall(call);
        if (callView != null)
            callView.setOnDragListener(null);
        mMainLayout.removeView(callView);
        // Terminate the call and select another call as active (selected) call.
        call.callEnded = true;
        if (userPressed)
            call.callEndedByUser = true;
        switchToNextCall(call, userPressed);
        totalCount--;
        // After terminating the call only one left
        // - reset some variables to their initial values in case we re-use the call manager fragment instance
        // - hide call manager, show main call screen
        if (totalCount == 1) {
            mLastCallUnHold = null;
            mConferenceOnHold = true;
            mCallback.hideCallManagerCb();
        }
    }

    /**
     * Terminate call and lookup a next call to set as active call.
     *
     * @param call the call to terminate
     * @param userPressed {@code true} if called via user actions, {@code false} if called
     *                    via SIP call state change.
     */
    private void switchToNextCall(CallState call, boolean userPressed) {
        CallState nextCall = null;

        // If the call to terminate is not the active call then just terminate it,
        // no need to select another active call.
        if (call != TiviPhoneService.calls.selectedCall) {
            if (userPressed) {
                TiviPhoneService.doCmd("*e" + call.iCallId);
            }
            return;
        }

        // The active (selected) call terminates. Need to find a new active call to
        // display in the main call window.
        // If we have a call which is not on hold then select it. If it is in conference
        // then set conference to no-hold mode.
        //
        // Otherwise select a call from out lists. The order of the if-statements implement
        // the lookup priority. First check if we have an in/out, then private, then conference.
        if (mLastCallUnHold == null) {
            if (mInOutArray.size() >= 1) {
                nextCall = mInOutArray.get(0);
            }
            else if (mPrivateArray.size() >= 1) {
                nextCall = mPrivateArray.get(0);
            }
            else if (mConfArray.size() >= 1) {
                nextCall = mConfArray.get(0);
            }
            // nextCall null may happen is SIP signaling calls this functions via call state change.
            // In this case it was the last call and our user pressed end-call button.
            if (nextCall != null && nextCall.iIsOnHold) {
                if (!mConfArray.contains(nextCall)) {
                    setCallToHold(nextCall, false);
                }
            }
        }
        else {
            nextCall = mLastCallUnHold;
            if (mConfArray.contains(nextCall))
                setConferenceUnHold();
            else
                setCallToHold(nextCall, false);     // Is in private list. in/out call cannot go on hold
        }

        // switch the calls: Terminate current call if user pressed the button, switch to next.
        // If we have no next call then this is similar to: terminate the last active call.
        mCallback.activeCallChangedCb(call, nextCall, userPressed);
        if (nextCall == null)
            return;

        // Having a null call view is OK because of we may have removed a last call already from the layout
        final CardView callView = findViewForCall(nextCall);
        if (callView != null)
            bindCallView(callView, nextCall);
    }

    private void setupCallLists() {
        fillInCallArray(mConfArray, ManageCallStates.eConfCall);
        fillInCallArray(mPrivateArray, ManageCallStates.ePrivateCall);
        fillInCallArray(mInOutArray, ManageCallStates.eStartupCall);
    }

    private void fillInCallArray(ArrayList<CallState>array, int callType) {
        CallState call;
        int cnt = TiviPhoneService.calls.getCallCnt(callType);
        int idx = 0;
        if (cnt > 0) {
            for (int i = 0; i < ManageCallStates.MAX_GUI_CALLS; i++) {
                call = TiviPhoneService.calls.getCall(callType, i);
                if (call != null && !array.contains(call)) {
                    array.add(idx, call);
                    idx++;
                }
            }
        }
    }

    private void setCallToHold(@NonNull CallState call, boolean setToHold) {
        if (setToHold) {
            TiviPhoneService.doCmd("*h" + call.iCallId);
        }
        else {
            TiviPhoneService.doCmd("*u" + call.iCallId);
            mLastCallUnHold = call;
        }
        call.iIsOnHold = setToHold;
        // if call is not on hold, call is also not muted
        if (!call.iIsOnHold) {
            call.iMuted = false;
        }
    }

    private void toggleHoldState(CallState call) {
        CardView callView;
        if (mConfArray.size() > 0) {
            /* A conference is active and the call which triggered the toggle ("this call") is
               not a private call:
               - check if some other call in the private list is in un-hold state, this implies
                 that whole conference is on hold
               - if a private call un-hold found:
                 - set new active call to "this call", set the other private call to hold
                 - un-hold conference
               - else no private call in un-hold state
                 - just toggle call state of the conference
             */
            if (!mPrivateArray.contains(call)) {
                CallState privateCallUnHold =  lookupPrivateCallUnHold();
                if (privateCallUnHold != null) {
                    mCallback.activeCallChangedCb(null, call, false);
                    setCallToHold(privateCallUnHold, true);
                    callView = findViewForCall(privateCallUnHold);
                    bindCallView(callView, privateCallUnHold);
                    setConferenceUnHold();
                }
                else {
                    if (call.iIsOnHold) {
                        setConferenceUnHold();
                    }
                    else {
                        setConferenceOnHold();
                    }
                }
            }
            else {
                /* the call to process is a private call and a conference is active and if private
                 * call is on hold then this call will go to un-hold, thus set the whole conference
                 * on hold. An active private call disables participation on the conference.
                 *
                 * Further processing see below.
                 */
                if (call.iIsOnHold) {
                    setConferenceOnHold();
                }
            }
        }

        /*
         * Call to process on private list:
         * - get old active call
         * - if not the same as the call to process:
         *   - set old active to hold
         *   - set this call to new active call
         * - set this call to un-hold
         *
         * and vice versa
         *
         * The whole processing makes sure that only one call on the private list
         * is in un-hold mode.
         */
        if (mPrivateArray.contains(call)) {
            CallState oldActive = TiviPhoneService.calls.selectedCall;
            if (call.iIsOnHold) {
                if (call != oldActive) {            // toggle with another all
                    if (oldActive != null) {
                        setCallToHold(oldActive, true);
                        callView = findViewForCall(oldActive);
                        bindCallView(callView, oldActive);
                    }
                    mCallback.activeCallChangedCb(null, call, false);
                }
                setCallToHold(call, false);
            }
            else {
                if (call != oldActive) {
                    if (oldActive != null) {
                        setCallToHold(oldActive, false);
                        callView = findViewForCall(oldActive);
                        bindCallView(callView, oldActive);
                    }
                    mCallback.activeCallChangedCb(null, call, false);
                }
                setCallToHold(call, true);
            }
        }
        callView = findViewForCall(call);
        bindCallView(callView, call);
    }

    /**
     * Set all calls in conference list to hold mode.
     */
    public void setConferenceOnHold() {
        if (mConferenceOnHold)
            return;
        for (CallState c : mConfArray) {
            if (c.iIsOnHold)
                continue;
            setCallToHold(c, true);
            final CardView callView = findViewForCall(c);
            if (callView == null)
                continue;
            bindCallView(callView, c);
            callView.invalidate();
        }
        mConferenceOnHold = true;
    }

    /**
     * Set all calls in conference list to non-hold mode.
     */
    private void setConferenceUnHold() {
        if (!mConferenceOnHold)
            return;
        for (CallState c : mConfArray) {
            if (!c.iIsOnHold)
                continue;
            setCallToHold(c, false);
            final CardView callView = findViewForCall(c);
            if (callView == null)
                continue;
            bindCallView(callView, c);
            callView.invalidate();
        }
        mConferenceOnHold = false;
    }

    /**
     * Check if a private call is in un-hold mode.
     *
     * @return The call that's in un-hold mode, {@code null} null otherwise
     */
    private CallState lookupPrivateCallUnHold() {
        for (CallState c : mPrivateArray) {
            if (!c.iIsOnHold)
                return c;
        }
        return null;
    }

    private void addRemoveConference(CallState call) {
        final CardView callView = findViewForCall(call);
        if (callView == null)
            return;
        if (mConfArray.contains(call)) {
            /* Move call to private list. If call is in un-hold mode then set conference to
             * hold and set moved call as active call.
             */
            mConfArray.remove(call);
            mPrivateArray.add(call);

            call.isInConference = false;
            TiviPhoneService.doCmd("*-" + call.iCallId);
            if (!call.iIsOnHold) {
                mCallback.activeCallChangedCb(null, call, false);
                setConferenceOnHold();
            }
        }
        else {
            /*
             * This code relies on the fact that only one call can be active on
             * the private list. Thus if the user moves the active (un-hold) call
             * from the private list to conference then set whole conference to
             * un-hold.
             *
             * Otherwise, if the moved call is on-hold then set it to un-hold if
             * the conference is not on hold.
             */
            mPrivateArray.remove(call);
            mConfArray.add(call);

            call.isInConference = true;
            TiviPhoneService.doCmd("*+" + call.iCallId);
            if (!call.iIsOnHold) {
                setConferenceUnHold();
            }
            else if (!mConferenceOnHold) {
                setCallToHold(call, false);
                bindCallView(callView, call);
            }
        }
        // maybe: re-bind view if some conference icons change
    }

    /**
     * Returns the index of a section header in the main linear layout
     * @param id Id of the section header
     * @return the index or -1 if not such id
     */
    private int getIndexOfSection(int id) {
        final int count = mMainLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = mMainLayout.getChildAt(i);
            if (id == v.getId())
                return i;
        }
        return -1;
    }

    @Nullable
    private CardView findViewForCall(CallState call) {
        if (mMainLayout == null) {
            return null;
        }

        final int count = mMainLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = mMainLayout.getChildAt(i);
            if (call == v.getTag() && v instanceof CardView)
                return (CardView)v;
        }
        return null;
    }

    /**
     * Returns the index of a view in the main linear layout
     * @param view the view
     * @return the index or -1 if not such view
     */
    private int getIndexOfView(View view) {
        final int count = mMainLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = mMainLayout.getChildAt(i);
            if (view == v)
                return i;
        }
        return -1;
    }
    /**
     * Create and bind call views and add it to section in layout
     * @param id Section id
     * @param calls calls that belong to the section
     */
    private void createAndBindViews(int id, ArrayList<CallState> calls) {
        if (calls.size() == 0)
            return;
        View v = mMainLayout.findViewById(id);
        if (!(v.getVisibility() == View.VISIBLE))
            v.setVisibility(View.VISIBLE);

        int sectionIndex = getIndexOfSection(id);
        for (CallState call : calls) {
            CardView callView = findViewForCall(call);
            if (callView == null) {
                callView = createCallView(call, mMainLayout);
                if (callView != null) {
                    mMainLayout.addView(callView, ++sectionIndex);
                }
            }
            else {
                bindCallView(callView, call);
            }
        }
    }

    private void startDraggingToConference(CallState call, CardView view) {
        ClipData dragData = ClipData.newPlainText("Conference", "to conference");
        view.startDrag(dragData, new View.DragShadowBuilder(view), call, 0);
    }

    private void startDraggingToPrivate(CallState call, CardView view) {
        ClipData dragData = ClipData.newPlainText("Private", "to private");
        view.startDrag(dragData, new View.DragShadowBuilder(view), call, 0);
    }

    private boolean handleDragEvent(View v, DragEvent event, boolean toConference) {
        final int action = event.getAction();
        final CallState call = (CallState)event.getLocalState();
        final CardView callView = findViewForCall(call);
        final int index = getIndexOfSection(R.id.private_header);

        switch(action) {
            case DragEvent.ACTION_DRAG_STARTED:

                // Determines if this View can accept the dragged data
                if (event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    if (toConference && call.isInConference)
                        return false;
                    if (!toConference && !call.isInConference)
                        return false;
                    setDragElementBackground(call, mDragActiveColor);
                    (mScrollView).smoothScrollTo(0, (int)mMainLayout.findViewById(R.id.private_header).getY());
                    return true;
                }
                return false;

            case DragEvent.ACTION_DRAG_ENTERED:
                setDragElementBackground(call, mDragEnteredColor);
                if (callView != null) {
                    mMainLayout.removeView(callView);
                    mMainLayout.addView(callView, index);
                }
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                return true;

            case DragEvent.ACTION_DROP:
                setDragElementBackground(call, mNormalColor);
                addRemoveConference((CallState) event.getLocalState());

                if (mConfArray.size() == 0) {
                    mMainLayout.findViewById(R.id.private_header_explanation).setVisibility(View.GONE);
                }
                else {
                    mMainLayout.findViewById(R.id.private_header_explanation).setVisibility(View.VISIBLE);
                }
                if (mPrivateArray.size() == 0) {
                    mMainLayout.findViewById(R.id.conf_header_explanation).setVisibility(View.GONE);
                }
                else {
                    mMainLayout.findViewById(R.id.conf_header_explanation).setVisibility(View.VISIBLE);
                }
                // Returns true. DragEvent.getResult() will return true.
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                setDragElementBackground(call, mNormalColor);
                return true;

            // An unknown action type was received.
            default:
                Log.e(TAG,"Unknown action type received by OnDragListener.");
                break;
        }
        return false;
    }

    private boolean handleDragEventCallView(View v, DragEvent event) {
        final int action = event.getAction();
        final CallState call = (CallState)event.getLocalState();
        final CardView callView = findViewForCall(call);
        final int index = getIndexOfView(v);
        final int privateSectionIndex = getIndexOfSection(R.id.private_header);
        boolean move = false;

        switch (action) {
            case DragEvent.ACTION_DRAG_STARTED:
                // Determines if this View can accept the dragged data
                return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !call.mustShowAnswerBT();

            case DragEvent.ACTION_DRAG_ENTERED:
                mMainLayout.removeView(callView);
                final int inoutIndex = getIndexOfSection(R.id.in_out_header);
                mMainLayout.addView(callView, index > inoutIndex ? inoutIndex : index);
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                return true;

            // We may get an ACTION_DROP
            case DragEvent.ACTION_DROP:
                if (v == callView) {
                    if (!call.isInConference && index < privateSectionIndex)
                        move = true;
                    if (call.isInConference && index >= privateSectionIndex)
                        move = true;
                }
                // move call only if it was dropped in another section
                if (move) {
                    moveCall(call);
                }
                // report success only if call was moved to another section
                return move;

            // We get ACTION_DRAG_ENDED for _every_ drag target, thus check if we need to take action
            case DragEvent.ACTION_DRAG_ENDED:
                setDragElementBackground(call, mNormalColor);
                // if we have not moved the call check if we need to move it based on its current state
                if (!event.getResult() && v == callView) {
                    if (!call.isInConference && index < privateSectionIndex)
                        move = true;
                    if (call.isInConference && index >= privateSectionIndex)
                        move = true;
                    if (move)
                        moveCall(call);
                }
                return true;

            // An unknown action type was received.
            default:
                Log.e(TAG,"Unknown action type received by OnDragListener.");
                break;
        }
        return false;
    }

    private void moveCall(final CallState call) {
        setDragElementBackground(call, mNormalColor);
        addRemoveConference(call);

        if (mConfArray.size() == 0) {
            mMainLayout.findViewById(R.id.private_header_explanation).setVisibility(View.GONE);
        }
        else {
            mMainLayout.findViewById(R.id.private_header_explanation).setVisibility(View.VISIBLE);
        }
        if (mPrivateArray.size() == 0) {
            mMainLayout.findViewById(R.id.conf_header_explanation).setVisibility(View.GONE);
        }
        else {
            mMainLayout.findViewById(R.id.conf_header_explanation).setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @SuppressWarnings("deprecation")
    private void setDragElementBackground(final CallState call, final int color) {
        View childView;
        if (!call.isInConference) {
            childView = mMainLayout.findViewById(R.id.conf_header_explanation);
            if (childView == null)   // wrong parent view, is OK because every registered view receives drag_ended
                return;
            if (color != mNormalColor) {
                childView.setBackgroundColor(color);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                    ((TextView)childView).setTextAppearance(mParent, android.R.style.TextAppearance_Large);
                else
                    ((TextView)childView).setTextAppearance(android.R.style.TextAppearance_Large);
            }
            else {
                childView.setBackgroundResource(0);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                    ((TextView)childView).setTextAppearance(mParent, android.R.style.TextAppearance_Small);
                else
                    ((TextView)childView).setTextAppearance(android.R.style.TextAppearance_Small);
            }
            childView.invalidate();
        }
        else {
            childView = mMainLayout.findViewById(R.id.private_header_explanation);
            if (childView == null)
                return;
            if (color != mNormalColor) {
                childView.setBackgroundColor(color);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                    ((TextView)childView).setTextAppearance(mParent, android.R.style.TextAppearance_Large);
                else
                    ((TextView)childView).setTextAppearance(android.R.style.TextAppearance_Large);
            }
            else {
                childView.setBackgroundResource(0);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                    ((TextView)childView).setTextAppearance(mParent, android.R.style.TextAppearance_Small);
                else
                    ((TextView)childView).setTextAppearance(android.R.style.TextAppearance_Small);
            }
            childView.invalidate();
        }
    }

    private boolean getRetentionState(String peerName, boolean canRefreshUserInfo, boolean peerDisclosureFlag) {
        boolean retentionState = false;
        if (Utilities.canMessage(peerName)) {
            byte[] partnerUserInfo = ZinaNative.getUserInfoFromCache(peerName);
            if (partnerUserInfo != null) {
                AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(partnerUserInfo);
                retentionState = DRUtils.isAnyDataRetainedForUser(userInfo);
            }
            // TODO: Remove this when the peer disclosure bug is resolved
            if (retentionState != peerDisclosureFlag && canRefreshUserInfo) {
                partnerUserInfo = ZinaNative.refreshUserData(peerName, null);
                if (partnerUserInfo != null) {
                    AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(partnerUserInfo);
                    retentionState = DRUtils.isAnyDataRetainedForUser(userInfo);
                }
            }
        }
        return retentionState;
    }

    private void updateDataRetentionBanner() {
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                mIsSelfDrEnabled = LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp();
                boolean isDrEnabled = LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp()
                        // TODO show banner during call when messaging DR is active as well?
                        | LoadUserInfo.isLrmp() | LoadUserInfo.isLrmp() | LoadUserInfo.isLrap();

                mDataRetentionBanner.clearConversationPartners();
                mDataRetentionBanner.post(new Runnable() {
                    @Override
                    public void run() {
                        mDataRetentionBanner.refreshBannerTitle();
                    }
                });

                String peerName;
                for (CallState call : mConfArray) {
                    peerName = Utilities.getPeerName(call);
                    mDataRetentionBanner.addConversationPartner(peerName);
                    isDrEnabled |= getRetentionState(peerName, true, call.mPeerDisclosureFlag);
                }
                for (CallState call : mPrivateArray) {
                    peerName = Utilities.getPeerName(call);
                    mDataRetentionBanner.addConversationPartner(peerName);
                    isDrEnabled |= getRetentionState(peerName, true, call.mPeerDisclosureFlag);
                }
                for (CallState call : mInOutArray) {
                    peerName = Utilities.getPeerName(call);
                    mDataRetentionBanner.addConversationPartner(peerName);
                    isDrEnabled |= getRetentionState(peerName, true, call.mPeerDisclosureFlag);
                }

                android.os.Message message = android.os.Message.obtain();
                message.arg1 = isDrEnabled ? View.VISIBLE : View.GONE;
                message.what = RedrawHandler.REFRESH_DATA_RETENTION_BANNER;
                mHandler.sendMessage(message);
            }
        });
    }

    private void registerViewUpdater() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        mViewUpdater = new MessagingBroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isAdded()) {
                    return;
                }
                setupCallLists();
                createAndBindViews(R.id.conf_header, mConfArray);
                createAndBindViews(R.id.private_header, mPrivateArray);
                createAndBindViews(R.id.in_out_header, mInOutArray);
            }
        };

        IntentFilter filter = Action.filter(Action.REFRESH_SELF);
        registerMessagingReceiver(activity, mViewUpdater, filter, BROADCAST_PRIORITY);
    }

}
