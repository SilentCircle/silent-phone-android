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
package com.silentcircle.keystore;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

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

    private boolean storeCreation;
    private boolean lockedDuringPwChange;

    private PasswordFilter pwFilter = new PasswordFilter();

    private KeyStoreActivity mParent;

    /**
     * Use this factory method to create a new instance of this fragment using the provided parameters.
     *
     * @param action whic password action to perform.
     * @return A new instance of fragment PasswordFragment.
     */
    public static PasswordFragment newInstance(String action, boolean usePin) {
        Log.d(TAG, "+++ action: " + action);
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View fragmentView = inflater.inflate(R.layout.ks_fragment_password, container, false);
        if (fragmentView == null)
            return null;

        passwordInput = (EditText)fragmentView.findViewById(R.id.passwordInput);
        passwordShow = (CheckBox)fragmentView.findViewById((R.id.passwordShow));
        passwordShow.setOnClickListener(this);
        passwordInput2 = (EditText)fragmentView.findViewById(R.id.passwordInput2);
        pwStrength = (TextView)fragmentView.findViewById(R.id.passwordStrength);
        mExplanation = (TextView)fragmentView.findViewById(R.id.explanation);
        passwordInputOld = (EditText)fragmentView.findViewById(R.id.oldPasswordInput);

        final int pinType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        if (mUsePin) {
            passwordShow.setText(R.string.show_pin);
            passwordInput.setInputType(pinType);
            passwordInput.setHint(R.string.pin_hint);
            passwordInput2.setInputType(pinType);
            passwordInput2.setHint(R.string.pin_hint2);
            passwordInputOld.setInputType(pinType);
            passwordInputOld.setHint(R.string.pin_hint_old);
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

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
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
        mExplanation.setText(getString(mUsePin ? R.string.key_store_set_pin_explanation : R.string.key_store_set_pw_explanation));
        passwordInput.requestFocus();
        passwordInput2.setVisibility(View.VISIBLE);
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
                        passwordInput2.setVisibility(View.GONE);
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
                        mParent.showInputInfo(getString(R.string.cannot_change_password));
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
        mExplanation.setText(getString(mUsePin ? R.string.key_store_change_pin_explanation : R.string.key_store_change_pw_explanation));
        passwordInputOld.setVisibility(View.VISIBLE);
        passwordInputOld.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        passwordInputOld.requestFocus();

        passwordInput.setHint(mUsePin ? R.string.pin_hint_new : R.string.password_hint_new); // ask user for a new password
        passwordInput.setVisibility(View.VISIBLE);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);

        passwordInput2.setVisibility(View.VISIBLE);
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
                        mParent.showInputInfo(getString(R.string.old_password_wrong));
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
                        passwordInput.setHint(mUsePin ? R.string.pin_hint : R.string.password_hint);
                        passwordInput2.setVisibility(View.GONE);
                        passwordInputOld.setVisibility(View.GONE);
                        passwordInputOld.setText(null);
                        pwStrength.setVisibility(View.GONE);
                        storeCreation = false;
                        mParent.mHandler.sendEmptyMessage(KeyStoreActivity.KEY_STORE_READY);
                    }
                    else {
                        mParent.showInputInfo(getString(R.string.cannot_change_password));
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
        mExplanation.setText(getString(mUsePin ? R.string.key_store_reset_pin_explanation : R.string.key_store_reset_pw_explanation));
        passwordInputOld.setVisibility(View.VISIBLE);
        passwordInputOld.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInputOld.requestFocus();
        passwordInput.setVisibility(View.INVISIBLE);
        passwordShow.setVisibility(View.VISIBLE);

        passwordInputOld.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId != EditorInfo.IME_ACTION_DONE)
                    return false;

                KeyStoreHelper.closeDatabase();
                if (!KeyStoreHelper.openOrCreateDatabase(KeyStoreActivity.toCharArray(passwordInputOld), mParent)) {
                    mParent.showInputInfo(getString(R.string.old_password_wrong));
                    passwordInputOld.setText(null);
                    passwordInputOld.requestFocus();
                    if (!lockedDuringPwChange) {
                        ProviderDbBackend.sendLockRequests();
                        lockedDuringPwChange = true;
                    }
                }
                else if (KeyStoreHelper.changePassword(new String(KeyStoreHelper.getDefaultPassword(mParent)))) {
                    if (lockedDuringPwChange) {
                        ProviderDbBackend.sendUnlockRequests();
                        lockedDuringPwChange = false;
                    }
                    passwordInputOld.setVisibility(View.GONE);
                    passwordInputOld.setText(null);
                    passwordInput.setVisibility(View.VISIBLE);
                    KeyStoreHelper.setUserPasswordType(mParent, KeyStoreHelper.USER_PW_TYPE_NONE);
                    storeCreation = false;
                    mParent.mHandler.sendEmptyMessage(KeyStoreActivity.KEY_STORE_READY);
                }
                else {
                    mParent.showInputInfo(getString(R.string.cannot_change_password));
                }
                return true;
            }
        });
    }

    private void passwordReady() {
        if (passwordInput.getText() == null || passwordInput.getText().length() == 0) {
            mParent.showInputInfo(getString(R.string.no_password));
            return;
        }
        if (KeyStoreHelper.openOrCreateDatabase(KeyStoreActivity.toCharArray(passwordInput), mParent)) {
            showNormalScreen();
            ProviderDbBackend.sendUnlockRequests();
            mParent.mHandler.sendEmptyMessage(KeyStoreActivity.KEY_STORE_READY);
        }
        else {
            KeyStoreHelper.closeDatabase();         // close DB, but do not stop service
            mParent.showInputInfo(getString(R.string.cannot_load_store));
            passwordInput.requestFocus();
        }
        passwordInput.setText(null);
    }

    // Shows the normal screen, depending on key store state.
    private void showNormalScreen() {
        if (storeCreation)
            return;
        passwordInput.setVisibility(View.VISIBLE);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.requestFocus();
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
        mExplanation.setText(getString(mUsePin ? R.string.key_store_enter_pin_explanation : R.string.key_store_enter_pw_explanation));
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
            mParent.showInputInfo(getString(mUsePin ? R.string.no_pin : R.string.no_password));
            return false;
        }
        if (mUsePin && pw1.length() < MIN_PIN_LENGTH) {
            mParent.showInputInfo(getString(R.string.pin_short, MIN_PIN_LENGTH));
            return false;
        }
        if (pw1.length() != pw2.length()) {
            mParent.showInputInfo(getString(mUsePin ? R.string.pin_match: R.string.password_match));
            return false;
        }
        int len = pw1.length();
        for (int i = 0; i < len; i++) {
            if (pw1.charAt(i) != pw2.charAt(i)) {
                mParent.showInputInfo(getString(mUsePin ? R.string.pin_match: R.string.password_match));
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

            pwStrength.setText(getString(R.string.pwstrength_weak));
            if (((strength >= 2 && strLength >= 7) || (strength >= 3 && strLength >= 6)) || strLength > 8) {
                pwStrength.setText(getString(R.string.pwstrength_good));
            }
            if ((strength >= 3 && strLength >= 7) || strLength > 10) {
                pwStrength.setText(getString(R.string.pwstrength_strong));
            }
        }
    }

}
