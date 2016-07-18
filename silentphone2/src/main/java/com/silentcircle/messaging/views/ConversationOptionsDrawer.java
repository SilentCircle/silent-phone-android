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
package com.silentcircle.messaging.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.activities.ShowRemoteDevicesActivity;
import com.silentcircle.messaging.listener.DismissDialogOnClick;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.task.ComposeMessageTask;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.SettingsFragment;

public class ConversationOptionsDrawer extends ScrollView  {

    private final static String TAG = "ConversationOptions";
    private String mPartner;
    private Activity mParent;

    public interface ConversationOptionsChangeListener {

        void onBurnNoticeChanged(boolean enabled);
        void onLocationSharingChanged(boolean enabled);
        void onBurnDelayChanged(long burnDelay);
        void onSendReceiptChanged(boolean enabled);

        void onClearConversation();
        void onSaveConversation();

        void onMessageVerification();
        void onResetKeys();
    }

    private ConversationOptionsChangeListener mConversationOptionsChangeListener;

    private OnTouchListener mSeekerTouchListener = new OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            int action = event.getAction();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }

            v.onTouchEvent(event);
            return true;
        }
    };

    SeekBar.OnSeekBarChangeListener mSeekerChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // update burn delay information label
            OptionsItem burnNotice = (OptionsItem) findViewById(R.id.burn_notice);
            burnNotice.setDescription(BurnDelay.Defaults.getLabel(getContext(), progress));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Ignore
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int progress = seekBar.getProgress();
            OptionsItem burnNotice = (OptionsItem) findViewById(R.id.burn_notice);
            burnNotice.setChecked(BurnDelay.Defaults.getDelay(progress) > 0);

            changeBurnDelay(BurnDelay.Defaults.getDelay(progress));
        }
    };

    OptionsItem.OnCheckedChangeListener mLocationSharingChangedListener =
            new OptionsItem.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(OptionsItem buttonView, boolean isChecked) {
                    boolean locationSharingAvailable =
                            LocationUtils.isLocationSharingAvailable(getContext());
                    if (!locationSharingAvailable && isChecked) {
                        buttonView.setChecked(false);
                        new AlertDialog.Builder(getContext())
                                .setTitle(R.string.dialog_title_cannot_enable_location_sharing)
                                .setMessage(R.string.dialog_message_enable_device_location)
                                .setNegativeButton(R.string.dialog_button_ok, new DismissDialogOnClick())
                                .show();
                        return;
                    }

                    toggleLocationSharing(isChecked);
                }
            };

    @SuppressWarnings("unused")
    OptionsItem.OnCheckedChangeListener mBurnNoticeChangeListener =
            new OptionsItem.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(OptionsItem buttonView, boolean isChecked) {
                    toggleBurnNotice(isChecked);

                    // when enabling checkbox, restore seeker to last known burn delay value
                    long burnDelay = mLastKnownBurnDelay;
                    SeekBar seeker = (SeekBar) findViewById(R.id.burn_delay_value);
                    seeker.setProgress(isChecked
                            ? BurnDelay.Defaults.getLevel(burnDelay) : 0);
                    mLastKnownBurnDelay = burnDelay;
                }
            };

    CompoundButton.OnCheckedChangeListener mSendReceiptsChangeListener = new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            toggleSendReceipts(isChecked);
        }
    };

    OnClickListener mClearConversationClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            clearConversation();
        }
    };

    OnClickListener mSaveConversationClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            saveConversation();
        }
    };

    OnClickListener mRemoveDevicesClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            showRemoteDeviceStatue();
        }
    };

    private long mLastKnownBurnDelay;

    public ConversationOptionsDrawer(Context context) {
        this(context, null);
    }

    public ConversationOptionsDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.scrollViewStyle);
    }

    public ConversationOptionsDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inflate(context, R.layout.drawer_conversation_options, this);
        initializeViews();
    }

    public void setConversationOptionsChangeListener(ConversationOptionsChangeListener listener) {
        mConversationOptionsChangeListener = listener;
    }

    private boolean isTalkingToSelf() {
        // TODO: anru
        return true;
    }

    public void onVerificationOptionsChanged() {
/*
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        SilentTextApplication application = SilentTextApplication.from(activity);
        if (application == null) {
            return;
        }
        ConversationRepository conversations = application.getConversations();
        if (conversations == null || !conversations.exists()) {
            return;
        }
        Conversation conversation = conversations.findByPartner(partner);
        if (conversation == null) {
            return;
        }
        ResourceStateRepository states = conversations.contextOf(conversation);
        ResourceState state = states == null ? null : states.findById(conversation.getPartner().getDevice());
        boolean secured = state != null && state.isSecure();
        boolean verified = secured && states != null && state != null && states.isVerified(state);
        setSecured(secured);
        setVerified(verified, state == null ? null : state.getVerifyCode());
 */
    }

    public void setConversationOptions(final Activity parent, final String partner, boolean isConversationEmpty,
            boolean isLocationSharingEnabled, boolean isSendReceiptsEnabled, boolean hasBurnNotice,
            long burnDelay) {

        mPartner = partner;
        mParent = parent;
        SeekBar seeker = (SeekBar) findViewById(R.id.burn_delay_value);
        OptionsItem burnNotice = (OptionsItem) findViewById(R.id.burn_notice);
        OptionsItem locationSharing = (OptionsItem) findViewById(R.id.location_sharing);
        CheckBox sendReceipts = (CheckBox) findViewById(R.id.send_receipts);
        boolean locationSharingAvailable = LocationUtils.isLocationSharingAvailable(getContext());

        setClearButtonEnabled(!isConversationEmpty);
        setEnabledIf(!isConversationEmpty, R.id.save);
        setEnabledIf(locationSharingAvailable, R.id.location_sharing);

        seeker.setMax(BurnDelay.Defaults.numLevels() - 1);
        seeker.setProgress(hasBurnNotice ? BurnDelay.Defaults.getLevel(burnDelay) : 0);

        burnNotice.setChecked(hasBurnNotice);
        locationSharing.setChecked(locationSharingAvailable && isLocationSharingEnabled);
        sendReceipts.setChecked(isSendReceiptsEnabled);

        burnNotice.setDescription(BurnDelay.Defaults.getLabel(getContext(), seeker.getProgress()));

        String locationModeDescription = "";
        Pair<Integer, String> locationMode = LocationUtils.getCurrentLocationMode(getContext());
        if (locationMode.first != Settings.Secure.LOCATION_MODE_OFF
                && !TextUtils.isEmpty(locationMode.second)) {
            locationModeDescription =
                    getResources().getString(R.string.dialog_message_current_location_mode,
                            locationMode.second);
        }
        locationSharing.setDescription(getResources().getString(R.string.dialog_message_enable_device_location)
                + " " + locationModeDescription);

        initializeViews();
    }

    public void prepareVerificationOptions() {
/*
        View resetKeysView = findViewById(R.id.reset_keys);

        resetKeysView.setVisibility(isTalkingToSelf() ? GONE : VISIBLE);
        resetKeysView.setOnClickListener(new LaunchConfirmDialogOnClick(R.string.are_you_sure, R.string.reset_keys_warning, getActivity(), new ResetKeysOnConfirm(resetKeysView, partner)));

        findViewById(R.id.verify_rating).setOnClickListener(new InformationalDialog(R.string.security_rating, R.layout.security_rating));

        CheckBox verify = (CheckBox) findViewById(R.id.verified);

        verify.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setVerified(isChecked);
            }

        });

        onVerificationOptionsChanged();
 */
    }

    protected void initializeViews() {
        SeekBar seeker = (SeekBar) findViewById(R.id.burn_delay_value);
        OptionsItem burnNotice = (OptionsItem) findViewById(R.id.burn_notice);
        OptionsItem locationSharing = (OptionsItem) findViewById(R.id.location_sharing);
        CheckBox sendReceipts = (CheckBox) findViewById(R.id.send_receipts);
        OptionsItem clearButton = (OptionsItem) findViewById(R.id.clear);
        OptionsItem saveButton = (OptionsItem) findViewById(R.id.save);

        seeker.setOnTouchListener(mSeekerTouchListener);
        seeker.setOnSeekBarChangeListener(mSeekerChangeListener);

        locationSharing.setOnCheckedChangeListener(mLocationSharingChangedListener);

        burnNotice.setOnCheckedChangeListener(null /*mBurnNoticeChangeListener*/);

        sendReceipts.setOnCheckedChangeListener(mSendReceiptsChangeListener);

        clearButton.setOnClickListener(mClearConversationClickListener);
        saveButton.setOnClickListener(mSaveConversationClickListener);

        OptionsItem remoteDeviceButton = (OptionsItem) findViewById(R.id.remote_devices);
        remoteDeviceButton.setOnClickListener(mRemoveDevicesClickListener);

        boolean developer = false;

        if(mParent != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
            developer = prefs.getBoolean(SettingsFragment.DEVELOPER, false);
        }

        if(developer) {
            LinearLayout spamLayout = (LinearLayout) findViewById(R.id.spam_options);
            final EditText spamNumEditText = (EditText) findViewById(R.id.spam_num);
            final EditText spamDelayEditText = (EditText) findViewById(R.id.spam_delay);

            final OptionsItem spam = (OptionsItem) findViewById(R.id.spam);
            spam.setVisibility(VISIBLE);
            spamLayout.setVisibility(VISIBLE);

            if(!TextUtils.isEmpty(spamNumEditText.getText())) {
                spamNumEditText.setSelection(spamNumEditText.getText().length());
            }

            spam.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String spamNumString = spamNumEditText.getText().toString();
                    String spamDelayString = spamDelayEditText.getText().toString();

                    int spamNum = spamNumString != null ? Integer.valueOf(spamNumString) : 0;
                    final int spamDelay = spamDelayString != null ? Integer.valueOf(spamDelayString) : 0;

                    final ConversationActivity activity = (ConversationActivity) mParent;
                    String partner = activity.getPartner();
                    ConversationRepository repository = ConversationUtils.getConversations(mParent.getApplicationContext());
                    Conversation conversation = activity.getConversation();

                    for (int i = 0; i < spamNum; i++) {
                        final ComposeMessageTask composeTask = new ComposeMessageTask(partner, conversation, repository,
                                null, true) {

                            @Override
                            protected void onPostExecute(Message message) {
                                SendMessageTask sendTask = new SendMessageTask(mParent.getApplicationContext()) {

                                    @Override
                                    protected void onPostExecute(Message message) {
                                        if (!getResultStatus() && getResultCode() < 0) {
                                            Toast.makeText(activity, "A message failed to send, consider increasing the delay", Toast.LENGTH_SHORT).show();
                                        }

                                        activity.updateConversation();
                                    }
                                };

                                AsyncUtils.execute(sendTask, message);
                            }
                        };

                         Handler handler = new Handler();
                         Runnable spamRunnable = new SpamRunnable(i + 1) {
                             @Override
                             public void run() {
                                AsyncUtils.execute(composeTask, "Test Message " + this.num);
                             }
                        };

                        handler.postDelayed(spamRunnable, i * spamDelay);
                    }
                }
            });
        }
    }

    public class SpamRunnable implements Runnable {
        public int num;

        public SpamRunnable(int num) {
            this.num = num;
        }

        public void run() {}
    }

    protected void setCheckedIf(boolean condition, int... viewResourceIDs) {
        for (int i = 0; i < viewResourceIDs.length; i++) {
            int id = viewResourceIDs[i];
            View view = findViewById(id);
            if (view instanceof Checkable) {
                ((Checkable) view).setChecked(condition);
            }
        }
    }

    private void setClearButtonEnabled(boolean enabled) {
        findViewById(R.id.clear).setEnabled(enabled);
    }

    protected void setEnabledIf(boolean condition, int... viewResourceIDs) {
        for (int i = 0; i < viewResourceIDs.length; i++) {
            int viewResourceID = viewResourceIDs[i];
            View view = findViewById(viewResourceID);
            if (view != null) {
                view.setEnabled(condition);
            }
        }
    }

    private void setSecured(boolean secured) {
        setVisibleIf(secured, R.id.verify);
        View resetButton = findViewById(R.id.reset_keys);
        Object pending = resetButton.getTag(R.id.pending);
        if (secured && pending == null) {
            setEnabledIf(true, R.id.reset_keys);
        }
        pending = null;
        resetButton.setTag(R.id.pending, pending);
    }

    protected void setText(int viewResourceID, CharSequence text) {
        ((android.widget.TextView) findViewById(viewResourceID)).setText(text);
    }

    protected void setText(int viewResourceID, int stringResourceID) {
        ((android.widget.TextView) findViewById(viewResourceID)).setText(stringResourceID);
    }

    protected void setVerified(boolean verified) {
/*
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        SilentTextApplication application = SilentTextApplication.from(getActivity());
        ConversationRepository conversations = application.getConversations();
        if (conversations == null || !conversations.exists()) {
            return;
        }
        Conversation conversation = conversations.findByPartner(partner);
        if (conversation == null) {
            return;
        }
        ResourceStateRepository states = conversations.contextOf(conversation);
        if (states == null) {
            return;
        }
        ResourceState state = states.findById(conversation.getPartner().getDevice());
        if (state == null) {
            return;
        }
        states.setVerified(state, verified);
        setVerified(verified, state.getVerifyCode());

        Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, partner);
        getActivity().sendBroadcast(intent, Manifest.permission.READ);
 */
    }

    private void setVerified(boolean verified, String sasPhrase) {

        android.widget.TextView label = (android.widget.TextView) findViewById(R.id.verify_label);
        label.setText(verified
                ? R.string.verify_description_verified
                : R.string.verify_description_unverified);

        CheckBox verify = (CheckBox) findViewById(R.id.verified);

        verify.setChecked(verified);
        verify.setText(sasPhrase);
    }

    protected void setVisibleIf(boolean condition, int... viewResourceIDs) {
        int visibility = condition ? View.VISIBLE : View.GONE;
        for (int i = 0; i < viewResourceIDs.length; i++) {
            int viewResourceID = viewResourceIDs[i];
            View view = findViewById(viewResourceID);
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    protected void toggleBurnNotice(boolean isChecked) {
        if (mConversationOptionsChangeListener != null) {
            mConversationOptionsChangeListener.onBurnNoticeChanged(isChecked);
        }
    }

    protected void changeBurnDelay(long burnDelay) {
        mLastKnownBurnDelay = burnDelay;
        if (mConversationOptionsChangeListener != null) {
            mConversationOptionsChangeListener.onBurnDelayChanged(burnDelay);
        }
    }

    protected void toggleLocationSharing(boolean isChecked) {
        if (mConversationOptionsChangeListener != null) {
            mConversationOptionsChangeListener.onLocationSharingChanged(isChecked);
        }
    }

    protected void toggleSendReceipts(boolean isChecked) {
        if (mConversationOptionsChangeListener != null) {
            mConversationOptionsChangeListener.onSendReceiptChanged(isChecked);
        }
    }

    protected void clearConversation() {
        if (mConversationOptionsChangeListener != null) {
            mConversationOptionsChangeListener.onClearConversation();
        }
    }

    protected void saveConversation() {
        if (mConversationOptionsChangeListener != null) {
            mConversationOptionsChangeListener.onSaveConversation();
        }
    }

    private void showRemoteDeviceStatue() {
        Intent intent = new Intent(mParent, ShowRemoteDevicesActivity.class);
        intent.putExtra(ShowRemoteDevicesActivity.PARTNER, mPartner);
        mParent.startActivity(intent);
    }
}
