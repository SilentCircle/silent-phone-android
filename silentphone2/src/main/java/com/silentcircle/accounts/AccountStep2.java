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

package com.silentcircle.accounts;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;

public class AccountStep2 extends Fragment implements View.OnClickListener{

    private static final String[] ALL_FIELDS_REQUIRED = {"username", "password", "email", "first_name", "last_name" };
    private static final String[] USERNAME_PASSWORD_ONLY = {"username", "current_password"};
    private JSONObject mCustomerData = new JSONObject();

    private EditText mUsernameInput;
    private TextView mPwStrength;
    private EditText mPasswordInput;
//    private EditText passwordInput2;
    private TextView mEmailInfo;
    private EditText mFirstNameInput;
    private EditText mLastNameInput;
    private EditText mEmailInput;
    private CheckBox mShowPassword;
    private TextView mBack;
    private TextView mHeaderText;

    private boolean mUseExistingAccount = true;

    private AuthenticatorActivity mParent;

    // These map to the tag string in the layout fields
    private String[] mRequiredArray;

    public static AccountStep2 newInstance(Bundle args) {
        AccountStep2 f = new AccountStep2();
        f.setArguments(args);
        return f;
    }

    public AccountStep2() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mUseExistingAccount = args.getBoolean(ProvisioningActivity.USE_EXISTING, true);
            final String roninCode = args.getString(AuthenticatorActivity.ARG_RONIN_CODE, null);
        }
        mRequiredArray = mUseExistingAccount ? USERNAME_PASSWORD_ONLY : ALL_FIELDS_REQUIRED;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (AuthenticatorActivity) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View stepView = inflater.inflate(R.layout.provisioning_bp_s2, container, false);
        if (stepView == null)
            return null;

        Bundle args = getArguments();

        ProvisioningActivity.FilterEnter filterEnter = new ProvisioningActivity.FilterEnter();
        PasswordFilter pwFilter = new PasswordFilter();

        mPwStrength = (TextView) stepView.findViewById(R.id.ProvisioningPasswordStrength);

        mUsernameInput = (EditText) stepView.findViewById(R.id.ProvisioningUsernameInput);
        mUsernameInput.addTextChangedListener(filterEnter);
        mUsernameInput.setFilters(new InputFilter[]{SearchUtil.USERNAME_INPUT_FILTER, SearchUtil.LOWER_CASE_INPUT_FILTER});
        mUsernameInput.setText(args.getString(ProvisioningActivity.USERNAME, null));

        mPasswordInput = (EditText) stepView.findViewById(R.id.ProvisioningPasswordInput);
        mPasswordInput.addTextChangedListener(pwFilter);
        mPasswordInput.setText(args.getString(AuthenticatorActivity.ARG_STEP1, null));

        mFirstNameInput = (EditText) stepView.findViewById(R.id.ProvisioningFirstNameInput);
        mFirstNameInput.addTextChangedListener(filterEnter);

        mLastNameInput = (EditText) stepView.findViewById(R.id.ProvisioningLastNameInput);
        mLastNameInput.addTextChangedListener(filterEnter);

        mEmailInput = (EditText) stepView.findViewById(R.id.ProvisioningEmailInput);
        mEmailInput.addTextChangedListener(filterEnter);

        mShowPassword = (CheckBox) stepView.findViewById(R.id.ShowPassword);
        mEmailInfo = (TextView) stepView.findViewById(R.id.ProvisioningEmailInfo);
        mHeaderText = (TextView) stepView.findViewById(R.id.HeaderText);
        mBack = (TextView)stepView.findViewById(R.id.back);

        stepView.findViewById(R.id.back).setOnClickListener(this);
        stepView.findViewById(R.id.next).setOnClickListener(this);
        stepView.findViewById(R.id.ShowPassword).setOnClickListener(this);

        stepView.setBackgroundColor(getResources().getColor(R.color.auth_background_grey));

        if (mUseExistingAccount) {
            mHeaderText.setText(getString(R.string.sign_in_to_sc));
            mBack.setText(R.string.cancel_dialog);
            mPasswordInput.setTag("current_password");
            mPasswordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            mEmailInfo.setVisibility(View.GONE);
            mFirstNameInput.setVisibility(View.GONE);
            mLastNameInput.setVisibility(View.GONE);
            mEmailInput.setVisibility(View.GONE);
        }

        return stepView;
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshFields();
    }

    private void refreshFields() {
        mParent.showPasswordCheck(mPasswordInput, mShowPassword.isChecked());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.back: {
                if (mUseExistingAccount)
                    mParent.provisioningCancel();
                else
                    mParent.backStep();
                break;
            }
            case R.id.next: {
                provisioningOk();
                break;
            }
            case R.id.ShowPassword: {
                CheckBox cb = (CheckBox)view;
                mParent.showPasswordCheck(mPasswordInput, cb.isChecked());
                break;
            }
        }
    }

    /**
     * Check if an input field is marked as required.
     *
     * @param field name of the field.
     * @return {@code true} if the field is marked as required
     */
    private boolean isRequired(String field) {
        if (mRequiredArray == null)
            return false;
        for (String reqField : mRequiredArray) {
            if (reqField == null)
                return false;
            if (reqField.equals(field))
                return true;
        }
        return false;
    }

    private boolean checkInputAndCopy(String field, CharSequence input) {
        if (isRequired(field) && TextUtils.isEmpty(input))
            return false;

        if (input == null || TextUtils.isEmpty(input))
            return true;
        try {
            mCustomerData.put(field, input);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * User confirmed the data.
     *
     * This method checks user input.
     *
     */
    public void provisioningOk() {
        mCustomerData = new JSONObject();
        if (!checkInputAndCopy((String) mUsernameInput.getTag(), mUsernameInput.getText().toString().trim())) {
            mParent.showInputInfo(getString(R.string.provisioning_user_req));
            return;
        }
        if (isRequired((String)mUsernameInput.getTag()) && !isValidUser(mUsernameInput.getText().toString().trim())) {
            mParent.showInputInfo(getString(R.string.provisioning_user_invalid));
            return;
        }
        if (!checkInputAndCopy((String) mPasswordInput.getTag(), mPasswordInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_password_req));
            return;
        }
        if (!checkInputAndCopy((String) mEmailInput.getTag(), mEmailInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_email_req));
            return;
        }
        if (isRequired((String)mEmailInput.getTag()) && !isValidEmail(mEmailInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_email_invalid));
            return;
        }
        if (!checkInputAndCopy((String) mFirstNameInput.getTag(), mFirstNameInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_firstname_req));
            return;
        }
        if (!checkInputAndCopy((String) mLastNameInput.getTag(), mLastNameInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_lastname_req));
            return;
        }
        CharSequence nameInput = mUsernameInput.getText();
        String username = null;
        if (nameInput != null)
            username = nameInput.toString().trim();
        mParent.accountStep3(mCustomerData, username, mUseExistingAccount);
    }

    /**
     * A simple password strength check.
     * <p/>
     * This check just looks for length and some different characters to give an indication
     * about a password strength.
     *
     * @author werner
     */
    private class PasswordFilter implements TextWatcher {
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void afterTextChanged(Editable s) {
            int strength = ProvisioningActivity.passwordStrength(s);
            if (strength < 0) {
                mPwStrength.setText(null);
                return;
            }
            switch (strength) {
                case 0:
                    mPwStrength.setText(getString(R.string.pwstrength_weak));
                    break;
                case 1:
                    mPwStrength.setText(getString(R.string.pwstrength_good));
                    break;
                case 2:
                    mPwStrength.setText(getString(R.string.pwstrength_strong));
                    break;
            }
        }
    }

    public final static boolean isValidEmail(CharSequence target) {
        if (target == null)
            return false;

        return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    public final static boolean isValidUser(CharSequence target) {
        if (target == null)
            return false;

        String regex = "^[a-z][a-z0-9]*$";

        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(target).matches();
    }
}
