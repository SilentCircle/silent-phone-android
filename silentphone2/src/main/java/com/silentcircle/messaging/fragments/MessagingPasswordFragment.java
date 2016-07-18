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

import android.app.Fragment;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class
        MessagingPasswordFragment extends Fragment {

    private static final String TAG = MessagingPasswordFragment.class.getSimpleName();

    public static final Timeouts TIMEOUTS = new Timeouts();

    public static final int FLAGS_VISIBLE_PASSWORD = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    public static final int FLAGS_HIDDEN_PASSWORD =
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;

    private static final String IS_AUTHORIZED =
            "com.silentcircle.messaging.fragments.MessagingPasswordFragment.isAuthorized";

    protected ViewGroup mContainerScreenLockOptions;
    protected ViewGroup mContainerPasswordFields;
    protected SwitchCompat mSwitchEnableScreenLock;
    protected CheckBox mCheckBoxShowPassword;
    protected Button mButtonSubmit;
    protected EditText mEditTextPassword;
    protected EditText mEditTextPasswordRepeated;
    protected SeekBar mSeekbarGracePeriod;
    protected TextView mSeekbarGracePeriodDescription;

    protected ViewGroup mDialogPasswordEntryContainer;
    protected EditText mDialogPasswordEntry;

    protected boolean mIsAuthorized = false;
    protected long mLockPeriod;

    protected View.OnClickListener mButtonSubmitListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            checkAndSubmitPassword();
        }
    };

    protected CompoundButton.OnCheckedChangeListener mSwitchListener =
            new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

            mSwitchEnableScreenLock.setText(getString(R.string.messaging_screen_lock_messaging_screen_protection,
                    getString(isChecked ? R.string.status_on : R.string.status_off)));

            if (isChecked) {
                showConfigurationFields();
            } else {
                // disable messaging protection
                if (saveChatPassword(null)) {
                    Toast.makeText(getActivity(), R.string.messaging_screen_lock_password_removed,
                            Toast.LENGTH_SHORT).show();
                    clear();
                    getActivity().finish();
                }
            }
        }
    };

    protected TextView.OnEditorActionListener mOnEditActionListener = new TextView.OnEditorActionListener() {

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                checkAndSubmitPassword();
                return true;
            }
            return false;
        }
    };

    protected CompoundButton.OnCheckedChangeListener mOnShowPasswordListener =
            new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            int flags = isChecked ? FLAGS_VISIBLE_PASSWORD : FLAGS_HIDDEN_PASSWORD;
            mEditTextPassword.setInputType(flags);
            mEditTextPasswordRepeated.setInputType(flags);
        }
    };

    protected SeekBar.OnSeekBarChangeListener mOnSeekbarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            setGracePeriodDescription(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    public static MessagingPasswordFragment newInstance() {
        MessagingPasswordFragment fragment = new MessagingPasswordFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public MessagingPasswordFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mIsAuthorized = savedInstanceState.getBoolean(IS_AUTHORIZED);
        }
        mDialogPasswordEntryContainer = (ViewGroup) inflater.inflate(R.layout.dialog_password_entry, null);
        mDialogPasswordEntry = (EditText) mDialogPasswordEntryContainer.findViewById(R.id.edittext_password);
        return inflater.inflate(R.layout.messaging_lock_configuration, container, false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_AUTHORIZED, mIsAuthorized);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mContainerScreenLockOptions = (ViewGroup) view.findViewById(R.id.messagingScreenLockOptions);
        mContainerPasswordFields = (ViewGroup) view.findViewById(R.id.messagingPasswordFields);
        mSwitchEnableScreenLock = (SwitchCompat) view.findViewById(R.id.enableMessagingScreenLock);
        mEditTextPassword = (EditText) view.findViewById(R.id.messagingPasswordInput1);
        mEditTextPasswordRepeated = (EditText) view.findViewById(R.id.messagingPasswordInput2);
        mCheckBoxShowPassword = (CheckBox) view.findViewById(R.id.messagingShowPassword);
        mSeekbarGracePeriod = (SeekBar) view.findViewById(R.id.messagingPasswordGracePeriod);
        mSeekbarGracePeriodDescription = (TextView) view.findViewById(R.id.messagingPasswordGracePeriodText);

        mButtonSubmit = (Button) view.findViewById(R.id.passwordSubmit);
        mButtonSubmit.setOnClickListener(mButtonSubmitListener);

        mSwitchEnableScreenLock.setOnCheckedChangeListener(mSwitchListener);

        mEditTextPasswordRepeated.setOnEditorActionListener(mOnEditActionListener);

        mCheckBoxShowPassword.setOnCheckedChangeListener(mOnShowPasswordListener);

        mLockPeriod = MessagingPreferences.getInstance(
                getActivity().getApplicationContext()).getMessagingLockPeriod();
        mSeekbarGracePeriod.setMax(TIMEOUTS.numLevels());
        mSeekbarGracePeriod.setOnSeekBarChangeListener(mOnSeekbarChangeListener);
        mSeekbarGracePeriod.setProgress(TIMEOUTS.getLevel(mLockPeriod));
        setGracePeriodDescription(mSeekbarGracePeriod.getProgress());

        // do not show edit box for old password if locking is not enabled
        boolean hasPasswordSet = hasPasswordSet();
        mSwitchEnableScreenLock.setChecked(hasPasswordSet);
        mSwitchEnableScreenLock.setText(getString(R.string.messaging_screen_lock_messaging_screen_protection,
                getString(hasPasswordSet ? R.string.status_on : R.string.status_off)));

        mContainerScreenLockOptions.setVisibility(
                ((hasPasswordSet && !mIsAuthorized) || !hasPasswordSet) ? View.GONE : View.VISIBLE);
        mSwitchEnableScreenLock.setEnabled(!(hasPasswordSet && !mIsAuthorized));

        if (hasPasswordSet && !mIsAuthorized) {
            requestConfiguredPassword();
        }
    }

    private void requestConfiguredPassword() {
        mContainerScreenLockOptions.setVisibility(View.GONE);
        mSwitchEnableScreenLock.setEnabled(false);

        // show message dialog requesting password
        // enable this only when password is entered
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setTitle(R.string.messaging_screen_lock_enter_password);
        alert.setMessage(R.string.messaging_screen_lock_enter_password_description);
        alert.setNegativeButton(R.string.dialog_button_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                getActivity().finish();
            }
        });

        alert.setPositiveButton(R.string.dialog_button_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // check password and exit the view if password not correct
                if (!checkPassword(mDialogPasswordEntry.getText())) {
                    dialog.cancel();
                } else {
                    mIsAuthorized = true;
                    showConfigurationFields();
                }
            }
        });

        alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                getActivity().finish();
            }
        });

        alert.setView(mDialogPasswordEntryContainer);
        final AlertDialog alertDialog = alert.show();
        alertDialog.setCanceledOnTouchOutside(false);

        mDialogPasswordEntry.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mDialogPasswordEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                switch (actionId) {
                    case EditorInfo.IME_ACTION_DONE:
                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();

                        return true;
                    default:
                        return false;
                }
            }
        });

        mDialogPasswordEntry.post(new Runnable() {
            @Override
            public void run() {
                DialerUtils.showInputMethod(mDialogPasswordEntry);
            }
        });
    }

    private void showConfigurationFields() {
        // enable/show configuration fields
        mSwitchEnableScreenLock.setEnabled(true);
        mContainerScreenLockOptions.setVisibility(View.VISIBLE);

        // move focus to first password field
        mEditTextPassword.requestFocus();
        DialerUtils.showInputMethod(mEditTextPassword);
    }

    private boolean hasPasswordSet() {
        byte[] chatKeyData =
                KeyManagerSupport.getPrivateKeyData(getActivity().getContentResolver(),
                        ConfigurationUtilities.getChatProtectionKey());

        return chatKeyData != null;
    }

    protected void clear() {
        if (mEditTextPassword != null) {
            mEditTextPassword.setText(null);
        }
        if (mEditTextPasswordRepeated != null) {
            mEditTextPasswordRepeated.setText(null);
        }
    }

    protected boolean checkPassword(CharSequence password) {
        boolean result = false;

        byte[] chatKeyData =
                KeyManagerSupport.getPrivateKeyData(getActivity().getContentResolver(),
                        ConfigurationUtilities.getChatProtectionKey());
        if (chatKeyData != null) {
            String hashString = Utilities.hashSha256(password.toString());

            if (hashString != null && !Arrays.equals(chatKeyData, hashString.getBytes())) {
                Toast.makeText(getActivity(), R.string.messaging_screen_lock_password_not_correct,
                        Toast.LENGTH_SHORT).show();
            }
            else {
                result = true;
            }
        }
        return result;
    }

    protected void checkAndSubmitPassword() {
        MessagingPreferences preferences = MessagingPreferences.getInstance(
                getActivity().getApplicationContext());

        if (mEditTextPassword.getText().toString().equals(mEditTextPasswordRepeated.getText().toString())) {
            if (TextUtils.isEmpty(mEditTextPassword.getText())) {
                // if passwords are empty, check if timeout has been changed and update that
                if (mLockPeriod != TIMEOUTS.getDelay(mSeekbarGracePeriod.getProgress())) {
                    preferences.setMessagingLockPeriod(
                            TIMEOUTS.getDelay(mSeekbarGracePeriod.getProgress()));
                    preferences.setLastMessagingUnlockTime(0);

                    Toast.makeText(getActivity(), R.string.messaging_screen_lock_timeout_updated,
                            Toast.LENGTH_SHORT).show();
                }
            }
            else {
                String hashString = Utilities.hashSha256(mEditTextPassword.getText().toString());
                if (hashString != null && saveChatPassword(hashString.getBytes())) {
                    preferences.setMessagingLockPeriod(
                            TIMEOUTS.getDelay(mSeekbarGracePeriod.getProgress()));
                    preferences.setLastMessagingUnlockTime(0);

                    Toast.makeText(getActivity(), R.string.messaging_screen_lock_password_enabled,
                            Toast.LENGTH_SHORT).show();
                }
            }

            getActivity().finish();
        } else {
            Toast.makeText(getActivity(), R.string.messaging_screen_lock_passwords_dont_match,
                    Toast.LENGTH_SHORT).show();
        }
    }

    protected boolean saveChatPassword(byte[] data) {
        boolean result = true;
        ContentResolver resolver = getActivity().getContentResolver();

        KeyManagerSupport.deletePrivateKeyData(resolver,
                ConfigurationUtilities.getChatProtectionKey());
        if (data != null && !KeyManagerSupport.storePrivateKeyData(resolver, data,
                ConfigurationUtilities.getChatProtectionKey())) {

            Log.e(TAG, "Cannot store chat protection password.");
            Toast.makeText(getActivity(), R.string.messaging_screen_lock_password_update_failed,
                    Toast.LENGTH_SHORT).show();

            result = false;
        }
        return result;
    }

    protected void setGracePeriodDescription(int progress) {
        CharSequence messageLockScreen = getString(
                R.string.messaging_screen_lock_messaging_after,
                TIMEOUTS.getLabel(progress).toString().toLowerCase(Locale.getDefault()));
        mSeekbarGracePeriodDescription.setText(messageLockScreen);
    }

    public static class Timeouts {

        protected int DEFAULT_LEVEL = 1;
        private final SparseArray<Long> mLevels = new SparseArray<Long>();

        {
            int level = 0;
            put(level++, TimeUnit.SECONDS.toMillis(30));
            put(level++, TimeUnit.SECONDS.toMillis(60));
            put(level++, TimeUnit.MINUTES.toMillis(5));
            put(level++, TimeUnit.MINUTES.toMillis(10));
            put(level++, TimeUnit.MINUTES.toMillis(15));
        }

        public long getDelay(int level) {
            return mLevels.get(level);
        }

        public CharSequence getLabel(int level) {
            long delay = getDelay(level);
            long now = System.currentTimeMillis();
            long time = now + delay;
            return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.SECOND_IN_MILLIS);
            /*
            return DateUtils.getRelativeDateTimeString(context, time, DateUtils.SECOND_IN_MILLIS,
                    DateUtils.DAY_IN_MILLIS, 0);
             */
        }

        public int getLevel(long delay) {
            for (int i = 0; i < mLevels.size(); i++) {
                if (mLevels.valueAt(i) == delay) {
                    return i;
                }
            }
            return DEFAULT_LEVEL;
        }

        public int numLevels() {
            return mLevels.size() - 1;
        }

        public void put(int level, long delay) {
            mLevels.put(level, delay);
        }
    }

}
