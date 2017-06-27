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
package com.silentcircle.keystore;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.SettingsFragment;

import java.util.Arrays;

/**
 * This fragment handle the various password set, change and reset use actions.
 */
public class PasswordFragment extends Fragment implements View.OnClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = PasswordFragment.class.getSimpleName();

    private static final String ACTION = "action";
    private static final String USE_PIN = "use_pin";
    private static final int MIN_PIN_LENGTH = 4;

    private String mAction;
    private boolean mUsePin;

    private EditText passwordInput;
    private EditText passwordInput2;
    private EditText passwordInputOld;
    private TextView pwStrength;
    private TextView mExplanation;
    private CheckBox passwordShow;
    private TextInputLayout oldPasswordInputWrap;
    private TextInputLayout passwordInputWrap;
    private TextInputLayout passwordInput2Wrap;

    private boolean storeCreation;
    private boolean lockedDuringPwChange;

    private PasswordFilter pwFilter = new PasswordFilter();

    private KeyStoreActivity mParent;

    private CharSequence mShowPin;
    private CharSequence mPinHint;
    private CharSequence mPinHint2;
    private CharSequence mPinHintOld;
    private CharSequence mSetPinExplanation;
    private CharSequence mSetPasswordExplanation;
    private String mCannotChangePassword;
    private CharSequence mChangePinExplanation;
    private CharSequence mChangePasswordExplanation;
    private CharSequence mPinHintNew;
    private CharSequence mPasswordHintNew;
    private String mOldPinWrong;
    private String mOldPasswordWrong;
    private CharSequence mPasswordHint;
    private CharSequence mResetPinExplanation;
    private CharSequence mResetPasswordExplanation;
    private String mNoPin;
    private String mNoPassword;
    private String mCannotLoadStore;
    private CharSequence mEnterPinExplanation;
    private CharSequence mEnterPasswordExplanation;
    private String mPinShort;
    private String mPinMatch;
    private String mPasswordMatch;
    private CharSequence mPasswordWeak;
    private CharSequence mPasswordGood;
    private CharSequence mPasswordStrong;

    /**
     * Use this factory method to create a new instance of this fragment using the provided parameters.
     *
     * @param action which password action to perform.
     * @return A new instance of fragment PasswordFragment.
     */
    public static PasswordFragment newInstance(String action, boolean usePin) {
        PasswordFragment fragment = new PasswordFragment();
        Bundle args = new Bundle();
        args.putString(ACTION, action);
        args.putBoolean(USE_PIN, usePin);
        fragment.setArguments(args);
        return fragment;
    }
    public PasswordFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mAction = getArguments().getString(ACTION);
            mUsePin = getArguments().getBoolean(USE_PIN);
        }
        mShowPin = getString(R.string.show_pin);
        mPinHint = getString(R.string.pin_hint);
        mPinHint2 = getString(R.string.pin_hint2);
        mPinHintOld = getString(R.string.pin_hint_old);
        mSetPinExplanation = getString(R.string.key_store_set_pin_explanation);
        mSetPasswordExplanation = getString(R.string.key_store_set_pw_explanation);
        mCannotChangePassword = getString(R.string.cannot_change_password);
        mChangePinExplanation = getString(R.string.key_store_change_pin_explanation);
        mChangePasswordExplanation = getString(R.string.key_store_change_pw_explanation);
        mPinHintNew = getString(R.string.pin_hint_new);
        mPasswordHintNew = getString(R.string.password_hint_new);
        mOldPinWrong = getString(R.string.old_pin_wrong);
        mOldPasswordWrong = getString(R.string.old_password_wrong);
        mPasswordHint = getString(R.string.password_hint);
        mResetPinExplanation = getString(R.string.key_store_reset_pin_explanation);
        mResetPasswordExplanation = getString(R.string.key_store_reset_pw_explanation);
        mNoPin = getString(R.string.no_pin);
        mNoPassword = getString(R.string.no_password);
        mCannotLoadStore = getString(R.string.cannot_load_store);
        mEnterPinExplanation = getString(R.string.key_store_enter_pin_explanation);
        mEnterPasswordExplanation = getString(R.string.key_store_enter_pw_explanation);
        mPinShort = getString(R.string.pin_short, MIN_PIN_LENGTH);
        mPinMatch = getString(R.string.pin_match);
        mPasswordMatch = getString(R.string.password_match);
        mPasswordWeak = getString(R.string.pwstrength_weak);
        mPasswordGood = getString(R.string.pwstrength_good);
        mPasswordStrong = getString(R.string.pwstrength_strong);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View fragmentView = inflater.inflate(R.layout.ks_fragment_password, container, false);
        if (fragmentView == null)
            return null;

        oldPasswordInputWrap = (TextInputLayout) fragmentView.findViewById(R.id.oldPasswordInputWrap);
        passwordInputWrap = (TextInputLayout) fragmentView.findViewById(R.id.passwordInputWrap);
        passwordInput2Wrap = (TextInputLayout) fragmentView.findViewById(R.id.passwordInput2Wrap);
        passwordInput = (EditText)fragmentView.findViewById(R.id.passwordInput);
        passwordShow = (CheckBox)fragmentView.findViewById((R.id.passwordShow));
        passwordShow.setOnClickListener(this);
        passwordInput2 = (EditText)fragmentView.findViewById(R.id.passwordInput2);
        pwStrength = (TextView)fragmentView.findViewById(R.id.passwordStrength);
        mExplanation = (TextView)fragmentView.findViewById(R.id.explanation);
        passwordInputOld = (EditText)fragmentView.findViewById(R.id.oldPasswordInput);

        final int pinType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        if (mUsePin) {
            passwordShow.setText(mShowPin);
            passwordInput.setInputType(pinType);
            passwordInputWrap.setHint(mPinHint);
            passwordInput2.setInputType(pinType);
            passwordInput2Wrap.setHint(mPinHint2);
            passwordInputOld.setInputType(pinType);
            oldPasswordInputWrap.setHint(mPinHintOld);
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        switch (mAction) {
            case "_SET":
                setPasswordKeyStore();
                break;
            case "_CHANGE":
                changePassword();
                break;
            case "_RESET":
                resetToDefaultPassword();
                break;
            default:
                showNormalScreen();
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
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
            mParent = (KeyStoreActivity)activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must be KeyStoreActivity");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.passwordShow:
                showPasswordCheck(view);
                break;

            default:
                break;
        }
    }

    private void setPasswordKeyStore() {
        mExplanation.setText(mUsePin ? mSetPinExplanation : mSetPasswordExplanation);
        passwordInput.requestFocus();
        DialerUtils.showInputMethod(passwordInput);
        passwordInput2Wrap.setVisibility(View.VISIBLE);
        if (!mUsePin) {
            pwStrength.setVisibility(View.VISIBLE);
            passwordInput.addTextChangedListener(pwFilter);
        }
        else
            pwStrength.setVisibility(View.GONE);

        passwordShow.setVisibility(View.VISIBLE);

        passwordInput2.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId != EditorInfo.IME_ACTION_DONE)
                    return false;

                if (checkPassword(passwordInput.getText(), passwordInput2.getText())) {
                    if (KeyStoreHelper.changePassword(passwordInput.getText())) {
                        if (!mUsePin)
                            passwordInput.removeTextChangedListener(pwFilter);
                        passwordInput2Wrap.setVisibility(View.GONE);
                        pwStrength.setVisibility(View.GONE);
                        storeCreation = false;
                        KeyStoreHelper.setUserPasswordType(mParent, mUsePin ?
                                KeyStoreHelper.USER_PW_TYPE_PIN :
                                KeyStoreHelper.USER_PW_TYPE_PW);
                        ProviderDbBackend.sendUnlockRequests();
                        // Message will finish activity
                        mParent.mHandler.sendEmptyMessage(KeyStoreActivity.KEY_STORE_READY);
                    }
                    else {
                        mParent.showInputInfo(mCannotChangePassword);
                    }
                }
                passwordInput2.setText(null);
                passwordInput.setText(null);
                passwordInput.requestFocus();
                return true;
            }
        });
    }

    /*
     * Very similar to create key store, but not the same :-)
     * database.rawExecSQL(String.format("PRAGMA key = '%s'", newPassword);
     */
    private void changePassword() {
        mExplanation.setText(mUsePin ? mChangePinExplanation : mChangePasswordExplanation);
        oldPasswordInputWrap.setVisibility(View.VISIBLE);
        passwordInputOld.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        passwordInputOld.requestFocus();
        DialerUtils.showInputMethod(passwordInputOld);

        passwordInputWrap.setHint(mUsePin ? mPinHintNew : mPasswordHintNew); // ask user for a new password
        passwordInputWrap.setVisibility(View.VISIBLE);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);

        passwordInput2Wrap.setVisibility(View.VISIBLE);
        if (!mUsePin) {
            pwStrength.setVisibility(View.VISIBLE);
            passwordInput.addTextChangedListener(pwFilter);
        }
        else
            pwStrength.setVisibility(View.GONE);
        passwordShow.setVisibility(View.VISIBLE);

        passwordInput2.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId != EditorInfo.IME_ACTION_DONE)
                    return false;

                if (checkPassword(passwordInput.getText(), passwordInput2.getText())) {
                    KeyStoreHelper.closeDatabase();
                    if (!KeyStoreHelper.openOrCreateDatabase(KeyStoreActivity.toCharArray(passwordInputOld), mParent)) {
                        mParent.showInputInfo(mUsePin ? mOldPinWrong : mOldPasswordWrong);
                        passwordInputOld.setText(null);
                        passwordInputOld.requestFocus();
                        if (!lockedDuringPwChange) {
                            ProviderDbBackend.sendLockRequests();
                            lockedDuringPwChange = true;
                        }
                    }
                    else if (KeyStoreHelper.changePassword(passwordInput.getText())) {
                        if (lockedDuringPwChange) {
                            ProviderDbBackend.sendUnlockRequests();
                            lockedDuringPwChange = false;
                        }
                        if (!mUsePin)
                            passwordInput.removeTextChangedListener(pwFilter);
                        passwordInputWrap.setHint(mUsePin ? mPinHint : mPasswordHint);
                        passwordInput2Wrap.setVisibility(View.GONE);
                        passwordInput2.setVisibility(View.GONE);
                        oldPasswordInputWrap.setVisibility(View.GONE);
                        passwordInputOld.setVisibility(View.GONE);
                        passwordInputOld.setText(null);
                        pwStrength.setVisibility(View.GONE);
                        storeCreation = false;
                        mParent.mHandler.sendEmptyMessage(KeyStoreActivity.KEY_STORE_READY);
                    }
                    else {
                        mParent.showInputInfo(mCannotChangePassword);
                    }
                }
                else {
                    passwordInput.requestFocus();
                }
                passwordInput2.setText(null);
                passwordInput.setText(null);
                return true;
            }
        });
    }

    /*
     * Very similar to change password, but not the same :-)
     * database.rawExecSQL(String.format("PRAGMA key = '%s'", newPassword);
     */
    private void resetToDefaultPassword() {
        mExplanation.setText(mUsePin ? mResetPinExplanation : mResetPasswordExplanation);
        oldPasswordInputWrap.setVisibility(View.VISIBLE);
        passwordInputOld.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInputOld.requestFocus();
        DialerUtils.showInputMethod(passwordInputOld);
        passwordInputWrap.setVisibility(View.INVISIBLE);
        passwordShow.setVisibility(View.VISIBLE);

        passwordInputOld.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId != EditorInfo.IME_ACTION_DONE)
                    return false;

                KeyStoreHelper.closeDatabase();
                if (!KeyStoreHelper.openOrCreateDatabase(KeyStoreActivity.toCharArray(passwordInputOld), mParent)) {
                    mParent.showInputInfo(mUsePin ? mOldPinWrong : mOldPasswordWrong);
                    passwordInputOld.setText(null);
                    passwordInputOld.requestFocus();
                    if (!lockedDuringPwChange) {
                        ProviderDbBackend.sendLockRequests();
                        lockedDuringPwChange = true;
                    }
                }
                else if (changePwToDefaultOrSecure()) {
                    oldPasswordInputWrap.setVisibility(View.GONE);
                    passwordInputOld.setText(null);
                    passwordInputWrap.setVisibility(View.VISIBLE);
                    mParent.mHandler.sendEmptyMessage(KeyStoreActivity.KEY_STORE_READY);
                }
                else {
                    mParent.showInputInfo(mCannotChangePassword);
                }
                return true;
            }
        });
    }

    // First try to get a secure password, generated from a HW backed key. If this
    // fails fall back to the normal default key.
    private boolean changePwToDefaultOrSecure() {
        int pwType = KeyStoreHelper.USER_PW_SECURE;
        char[] pw = KeyStoreHelper.getSecurePassword(mParent);
        boolean changed;
        if (pw == null) {
            pw = KeyStoreHelper.getDefaultPassword(mParent);
            pwType = KeyStoreHelper.USER_PW_TYPE_NONE;
            changed = KeyStoreHelper.changePassword(new String(pw));
        }
        else {
            changed = KeyStoreHelper.changePasswordBin(pw);
        }
        if (changed) {
            if (lockedDuringPwChange) {
                ProviderDbBackend.sendUnlockRequests();
                lockedDuringPwChange = false;
            }

            KeyStoreHelper.setUserPasswordType(mParent, pwType);
            storeCreation = false;
            Arrays.fill(pw, '\0');
        }
        return changed;
    }

    private void passwordReady() {
        if (passwordInput.getText() == null || passwordInput.getText().length() == 0) {
            mParent.showInputInfo(mUsePin ? mNoPin : mNoPassword);
            return;
        }
        if (KeyStoreHelper.openOrCreateDatabase(KeyStoreActivity.toCharArray(passwordInput), mParent)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
            boolean developer = prefs.getBoolean(SettingsFragment.DEVELOPER, false);

            // See https://lab.silentcircle.org/eng/spa/issues/58
            // For developers, we allow this
            if (!developer) {
                changePwToDefaultOrSecure();
            }

            showNormalScreen();
            ProviderDbBackend.sendUnlockRequests();
            mParent.mHandler.sendEmptyMessage(KeyStoreActivity.KEY_STORE_READY);
        }
        else {
            KeyStoreHelper.closeDatabase();         // close DB, but do not stop service
            mParent.showInputInfo(mCannotLoadStore);
            passwordInput.requestFocus();
        }
        passwordInput.setText(null);
    }

    // Shows the normal screen, depending on key store state.
    private void showNormalScreen() {
        if (storeCreation)
            return;
        passwordInputWrap.setVisibility(View.VISIBLE);
        // passwordInput.setVisibility(View.VISIBLE);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.requestFocus();
        DialerUtils.showInputMethod(passwordInput);
        passwordInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    passwordReady();
                    return true;
                }
                return false;
            }
        });
        mExplanation.setText(mUsePin ? mEnterPinExplanation : mEnterPasswordExplanation);
        passwordShow.setVisibility(View.VISIBLE);
    }

    /**
     * Switch between visible and invisible passwords.
     *
     * @param v the Checkbox
     */
    public void showPasswordCheck(View v) {
        CheckBox cbv = (CheckBox)v;

        final int pwVisible = mUsePin ? InputType.TYPE_CLASS_NUMBER :
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

        final int pwInvisible = mUsePin ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD :
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;

        if (cbv.isChecked()) {
            passwordInput.setInputType(pwVisible);
            if (passwordInput2 != null)
                passwordInput2.setInputType(pwVisible);
            if (passwordInputOld != null)
                passwordInputOld.setInputType(pwVisible);
        }
        else {
            passwordInput.setInputType(pwInvisible);
            if (passwordInput2 != null)
                passwordInput2.setInputType(pwInvisible);
            if (passwordInputOld != null)
                passwordInputOld.setInputType(pwInvisible);
        }
        if (!TextUtils.isEmpty(passwordInput.getText()))
            passwordInput.setSelection(passwordInput.getText().length());
        if (passwordInput2 != null && !TextUtils.isEmpty(passwordInput.getText()))
            passwordInput2.setSelection(passwordInput2.getText().length());
        if (passwordInputOld != null && !TextUtils.isEmpty(passwordInput.getText()))
            passwordInputOld.setSelection(passwordInputOld.getText().length());
    }

    /**
     * A simple check if two passwords are equal.
     *
     * Returns false if one or both passwords are null or empty or if the passwords
     * don't match.
     *
     * @param pw1 first password
     * @param pw2 second password
     * @return true if both passwords match and are not empty.
     */
    private boolean checkPassword(CharSequence pw1, CharSequence pw2) {
        if (pw1 == null || pw2 == null || pw1.length() == 0 || pw2.length() == 0) {
            mParent.showInputInfo(mUsePin ? mNoPin : mNoPassword);
            return false;
        }
        if (mUsePin && pw1.length() < MIN_PIN_LENGTH) {
            mParent.showInputInfo(mPinShort);
            return false;
        }
        if (pw1.length() != pw2.length()) {
            mParent.showInputInfo(mUsePin ? mPinMatch : mPasswordMatch);
            return false;
        }
        int len = pw1.length();
        for (int i = 0; i < len; i++) {
            if (pw1.charAt(i) != pw2.charAt(i)) {
                mParent.showInputInfo(mUsePin ? mPinMatch : mPasswordMatch);
                return false;
            }
        }
        return true;
    }


    /**
     * A simple password strength check.
     *
     * This check just looks for length and some different characters to give an indication
     * about a password strength.
     *
     * @author werner
     *
     */
    private class PasswordFilter implements TextWatcher {

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void afterTextChanged(Editable s) {
            String str = s.toString();
            int strLength = str.length();
            int strength = 0;

            if (strLength == 0) {
                pwStrength.setText(null);
                return;
            }
            boolean digit = false;
            boolean lower = false;
            boolean upper = false;
            boolean other = false;

            for (int i = 0; i < strLength; i++) {
                char chr = str.charAt(i);
                if (Character.isDigit(chr))
                    digit = true;
                else if (Character.isLowerCase(chr))
                    lower = true;
                else if (Character.isUpperCase(chr))
                    upper = true;
                else
                    other = true;
            }
            strength += (digit) ? 1 : 0;
            strength += (lower) ? 1 : 0;
            strength += (upper) ? 1 : 0;
            strength += (other) ? 1 : 0;

            CharSequence strengthText = mPasswordWeak;
            if (((strength >= 2 && strLength >= 7) || (strength >= 3 && strLength >= 6)) || strLength > 8) {
                strengthText = mPasswordGood;
            }
            if ((strength >= 3 && strLength >= 7) || strLength > 10) {
                strengthText = mPasswordStrong;
            }
            pwStrength.setText(strengthText);
        }
    }

}
