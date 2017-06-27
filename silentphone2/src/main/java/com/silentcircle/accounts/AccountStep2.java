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

package com.silentcircle.accounts;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
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
    private TextInputLayout mUsernameLayout;
    private TextView mPwStrength;
    private EditText mPasswordInput;
    private TextInputLayout mPasswordLayout;
//    private EditText passwordInput2;
    private TextView mEmailInfo;
    private EditText mFirstNameInput;
    private TextInputLayout mFirstNameLayout;
    private EditText mLastNameInput;
    private TextInputLayout mLastNameLayout;
    private EditText mEmailInput;
    private TextInputLayout mEmailLayout;
    private CheckBox mShowPassword;
    private TextView mBack;
    private TextView mNext;
    private TextView mHeaderText;
    private ScrollView mScrollView;

    private boolean mUseExistingAccount = true;
    private boolean mUserValid = false;
    private boolean mPassValid = false;
    private boolean mEmailValid = false;
    private boolean mFirstNameValid = false;
    private boolean mLastNameValid = false;

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
//            final String roninCode = args.getString(AuthenticatorActivity.ARG_RONIN_CODE, null);
        }
        mRequiredArray = mUseExistingAccount ? USERNAME_PASSWORD_ONLY : ALL_FIELDS_REQUIRED;
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
            mParent = (AuthenticatorActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must be AuthenticatorActivity.");
        }
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
        mNext = (TextView)stepView.findViewById(R.id.next);

        // setup username field
        mUsernameInput = (EditText) stepView.findViewById(R.id.ProvisioningUsernameInput);
        mUsernameInput.addTextChangedListener(filterEnter);
        mUsernameInput.setFilters(new InputFilter[]{SearchUtil.USERNAME_INPUT_FILTER, SearchUtil.LOWER_CASE_INPUT_FILTER});
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mUsernameInput.setBackground(mUsernameInput.getBackground().getConstantState().newDrawable());

        mUsernameLayout = (TextInputLayout) stepView.findViewById(R.id.ProvisioningUsernameLayout);
        mUsernameLayout.setErrorEnabled(true);

        mUsernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mUserValid = checkValid(mUsernameInput, mUsernameLayout, null, false);
                updateNextButton();
            }
        });

        mUsernameInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    checkValid(mUsernameInput, mUsernameLayout, getString(R.string.provisioning_user_req), false);
                }
            }
        });

        mUsernameInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return isAdded() && actionId == EditorInfo.IME_ACTION_NEXT && !checkValid(mUsernameInput, mUsernameLayout, getString(R.string.provisioning_user_req), true);
            }
        });

        if (!TextUtils.isEmpty(args.getString(ProvisioningActivity.USERNAME, null)))
            mUsernameInput.setText(args.getString(ProvisioningActivity.USERNAME, null));

        // setup password field
        mPasswordInput = (EditText) stepView.findViewById(R.id.ProvisioningPasswordInput);
        mPasswordInput.addTextChangedListener(pwFilter);

        mPasswordLayout = (TextInputLayout) stepView.findViewById(R.id.ProvisioningPasswordLayout);
        mPasswordLayout.setErrorEnabled(true);

        mPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mPassValid = checkValid(mPasswordInput, mPasswordLayout, null, false);
                updateNextButton();
            }
        });

        mPasswordInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    checkValid(mPasswordInput, mPasswordLayout, getString(R.string.provisioning_password_req), false);
                }
            }
        });

        mPasswordInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return isAdded() && actionId == EditorInfo.IME_ACTION_NEXT && !checkValid(mPasswordInput, mPasswordLayout, getString(R.string.provisioning_password_req), true);
            }
        });

        if (!TextUtils.isEmpty(args.getString(AuthenticatorActivity.ARG_STEP1, null)))
            mPasswordInput.setText(args.getString(AuthenticatorActivity.ARG_STEP1, null));

        // setup first name field
        mFirstNameInput = (EditText) stepView.findViewById(R.id.ProvisioningFirstNameInput);
        mFirstNameInput.addTextChangedListener(filterEnter);

        mFirstNameLayout = (TextInputLayout) stepView.findViewById(R.id.ProvisioningFirstNameLayout);
        mFirstNameLayout.setErrorEnabled(true);

        mFirstNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mFirstNameValid = checkValid(mFirstNameInput, mFirstNameLayout, null, false);
                updateNextButton();
            }
        });

        mFirstNameInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    checkValid(mFirstNameInput, mFirstNameLayout, getString(R.string.provisioning_firstname_req), false);
                }
            }
        });

        mFirstNameInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return isAdded() && actionId == EditorInfo.IME_ACTION_NEXT && !checkValid(mFirstNameInput, mFirstNameLayout, getString(R.string.provisioning_firstname_req), true);
            }
        });

        // setup last name field
        mLastNameInput = (EditText) stepView.findViewById(R.id.ProvisioningLastNameInput);
        mLastNameInput.addTextChangedListener(filterEnter);

        mLastNameLayout = (TextInputLayout) stepView.findViewById(R.id.ProvisioningLastNameLayout);
        mLastNameLayout.setErrorEnabled(true);

        mLastNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mLastNameValid = checkValid(mLastNameInput, mLastNameLayout, null ,false);
                updateNextButton();
            }
        });

        mLastNameInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    checkValid(mLastNameInput, mLastNameLayout, getString(R.string.provisioning_lastname_req), false);
                }
            }
        });

        mLastNameInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return isAdded() && actionId == EditorInfo.IME_ACTION_DONE && !checkValid(mLastNameInput, mLastNameLayout, getString(R.string.provisioning_lastname_req), true);
            }
        });

        // setup email field
        mEmailInput = (EditText) stepView.findViewById(R.id.ProvisioningEmailInput);
        mEmailInput.addTextChangedListener(filterEnter);

        mEmailLayout = (TextInputLayout) stepView.findViewById(R.id.ProvisioningEmailLayout);
        mEmailLayout.setErrorEnabled(true);

        mEmailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mEmailValid = checkValid(mEmailInput, mEmailLayout, null, false);
                updateNextButton();
            }
        });

        mEmailInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    checkValid(mEmailInput, mEmailLayout, getString(R.string.provisioning_email_req), false);
                }
            }
        });

        mEmailInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return isAdded() && actionId == EditorInfo.IME_ACTION_NEXT && !checkValid(mEmailInput, mEmailLayout, getString(R.string.provisioning_email_req), true);
            }
        });

        mScrollView = (ScrollView) stepView.findViewById(R.id.Scroll);
        mShowPassword = (CheckBox) stepView.findViewById(R.id.ShowPassword);
        mEmailInfo = (TextView) stepView.findViewById(R.id.ProvisioningEmailInfo);
        mHeaderText = (TextView) stepView.findViewById(R.id.HeaderText);
        mBack = (TextView)stepView.findViewById(R.id.back);

        stepView.findViewById(R.id.back).setOnClickListener(this);
        stepView.findViewById(R.id.next).setOnClickListener(this);
        stepView.findViewById(R.id.ShowPassword).setOnClickListener(this);

        stepView.setBackgroundColor(ContextCompat.getColor(mParent, R.color.auth_background_grey));

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
        updateNextButton();
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
                else {
                    InputMethodManager imm = (InputMethodManager)mParent.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    mParent.backStep();
                }
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

        if (!checkInputAndCopy((String) mEmailInput.getTag(), mEmailInput.getText())) {
            setLayoutError(mEmailLayout, getString(R.string.provisioning_email_req), true);
        } else if (isRequired((String)mEmailInput.getTag()) && !isValidEmail(mEmailInput.getText())) {
            setLayoutError(mEmailLayout, getString(R.string.provisioning_email_invalid), true);
        }

        if (!checkInputAndCopy((String) mLastNameInput.getTag(), mLastNameInput.getText())) {
            setLayoutError(mLastNameLayout, getString(R.string.provisioning_lastname_req), true);
        }

        if (!checkInputAndCopy((String) mFirstNameInput.getTag(), mFirstNameInput.getText())) {
            setLayoutError(mFirstNameLayout, getString(R.string.provisioning_firstname_req), true);
        }

        if (!checkInputAndCopy((String) mPasswordInput.getTag(), mPasswordInput.getText())) {
            setLayoutError(mPasswordLayout, getString(R.string.provisioning_password_req), true);
        }

        if (!checkInputAndCopy((String) mUsernameInput.getTag(), mUsernameInput.getText().toString().trim())) {
            setLayoutError(mUsernameLayout, getString(R.string.provisioning_user_req), true);
        } else if (isRequired((String)mUsernameInput.getTag()) && !isValidUser(mUsernameInput.getText().toString().trim())) {
            setLayoutError(mUsernameLayout, getString(R.string.provisioning_user_invalid), true);
        }

        if (TextUtils.isEmpty(mUsernameLayout.getError())
                && TextUtils.isEmpty(mPasswordLayout.getError())
                && TextUtils.isEmpty(mFirstNameLayout.getError())
                && TextUtils.isEmpty(mLastNameLayout.getError())
                && TextUtils.isEmpty(mEmailLayout.getError()))
        {
            CharSequence nameInput = mUsernameInput.getText();
            String username = null;
            if (nameInput != null)
                username = nameInput.toString().trim();
            mParent.accountStep3(mCustomerData, username, mUseExistingAccount);
        }
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

    public static boolean isValidEmail(CharSequence target) {
        return target != null && android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();

    }

    public static boolean isValidUser(CharSequence target) {
        if (target == null)
            return false;

        String regex = "^[a-z][a-z0-9]*$";

        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(target).matches();
    }

    private void updateNextButton() {
        if (mUserValid && mPassValid && mEmailValid && mFirstNameValid && mLastNameValid) {
            mNext.setAlpha(1.0f);
        } else {
            mNext.setAlpha(0.5f);
        }
    }

    private boolean checkValid(EditText editText, TextInputLayout textInputLayout, String error, boolean doScroll) {
        boolean valid = true;

        if (isRequired((String)editText.getTag()) && TextUtils.isEmpty(editText.getText().toString())) {
            valid = false;
            setLayoutError(textInputLayout, error, doScroll);
        } else {
            textInputLayout.setError(null);
        }
        return valid;
    }

    private void setLayoutError(TextInputLayout textInputLayout, String error, boolean doScroll) {
        // set error message if one is passed in
        // also make sure error portion of field is displayed
        if (!TextUtils.isEmpty(error))
            textInputLayout.setError(error);
        if (doScroll)
            scrollViewToVisibleArea(textInputLayout);
    }

    // is view (textinputlayout) completely visible in scrollable area ?
    // -1 = view is scrolled off top of visible area
    // 0 = view is 100% visible
    // 1 = view is scrolled off bottom of visible area
    private int VIEW_SCROLLED_OFF_TOP = -1;
    private int VIEW_VISIBLE = 0;
    private int VIEW_SCROLLED_OFF_BOTTOM = 1;
    private int isViewVisible(View view) {
        Rect scrollBounds = new Rect();
        Rect scrollHit = new Rect();
        mScrollView.getDrawingRect(scrollBounds);
        mScrollView.getHitRect(scrollHit);
        if (scrollBounds.top <= view.getTop() && scrollBounds.bottom >= view.getBottom()) {
            return VIEW_VISIBLE;
        } else if (scrollBounds.top > view.getTop()) {
            return VIEW_SCROLLED_OFF_TOP;
        } else if (scrollBounds.bottom < view.getBottom()) {
            return VIEW_SCROLLED_OFF_BOTTOM;
        }
        return VIEW_VISIBLE;
    }

    // scroll a view to top of visible portion of screen
    private void scrollViewToTop(final View view) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                mScrollView.smoothScrollTo(0, view.getTop());
            }
        });
    }

    // scroll a view to bottom of visible portion of screen
    private void scrollViewToBottom(final View view) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                mScrollView.smoothScrollTo(0, view.getBottom() - mScrollView.getHeight());
            }
        });
    }

    private void scrollViewToVisibleArea(final View view) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                int viewVisibility = isViewVisible(view);
                if (viewVisibility == VIEW_SCROLLED_OFF_TOP)
                    scrollViewToTop(view);
                else if (viewVisibility == VIEW_SCROLLED_OFF_BOTTOM)
                    scrollViewToBottom(view);
            }
        });
    }

}
