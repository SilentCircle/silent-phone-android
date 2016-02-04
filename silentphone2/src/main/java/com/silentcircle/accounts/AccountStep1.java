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
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class AccountStep1 extends Fragment implements View.OnClickListener {

    private AuthenticatorActivity mParent;

    private static final String[] mRequiredArray = {"username", "current_password"};
    private JSONObject mCustomerData = new JSONObject();

    private EditText mUsernameInput;
    private EditText mPasswordInput;
    private CheckBox mShowPassword;
    private Button mRegisterNew;

    public static AccountStep1 newInstance(Bundle args) {
        AccountStep1 f = new AccountStep1();
        f.setArguments(args);
        return f;
    }

    public AccountStep1() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (AuthenticatorActivity)activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View stepView = inflater.inflate(R.layout.provisioning_bp_s1, container, false);
        if (stepView == null)
            return null;

        ProvisioningActivity.FilterEnter filterEnter = new ProvisioningActivity.FilterEnter();

        mUsernameInput = (EditText) stepView.findViewById(R.id.ProvisioningUsernameInput);
        mUsernameInput.addTextChangedListener(filterEnter);
        mUsernameInput.setFilters(new InputFilter[]{SearchUtil.USERNAME_INPUT_FILTER, SearchUtil.LOWER_CASE_INPUT_FILTER});

        mPasswordInput = (EditText) stepView.findViewById(R.id.ProvisioningPasswordInput);
        mPasswordInput.setTag("current_password");

        mRegisterNew = (Button) stepView.findViewById(R.id.registerNew);
        mRegisterNew.setOnClickListener(this);

        // only allow create account when a license code is present
        // the following three lines should be removed when freemium support is to be included in the product
        Bundle args = getArguments();
        if (args == null || TextUtils.isEmpty(args.getString(AuthenticatorActivity.ARG_RONIN_CODE, null)))
            mRegisterNew.setVisibility(View.GONE);

        stepView.findViewById(R.id.loginExisting).setOnClickListener(this);

        mShowPassword = (CheckBox) stepView.findViewById(R.id.ShowPassword);
        mShowPassword.setOnClickListener(this);

        ((TextView)stepView.findViewById(R.id.ProvisioningVersion)).setText("version " + BuildConfig.VERSION_NAME);

        TextView privacy = (TextView)stepView.findViewById(R.id.ProvisioningPrivacy);
        privacy.setText(Html.fromHtml("<a href=\"https://www.silentcircle.com/privacy\">"
                + getResources().getString(R.string.provisioning_privacy)
                + "</a>"));
        privacy.setMovementMethod(LinkMovementMethod.getInstance());
        stripUnderlines(privacy);

        TextView terms = (TextView)stepView.findViewById(R.id.ProvisioningTerms);
        terms.setText(Html.fromHtml("<a href=\"https://accounts.silentcircle.com/terms\">"
                + getResources().getString(R.string.provisioning_terms)
                + "</a>"));
        terms.setMovementMethod(LinkMovementMethod.getInstance());
        stripUnderlines(terms);

        TextView forgotPassword = (TextView) stepView.findViewById(R.id.ProvisioningForgotPassword);
        forgotPassword.setText(Html.fromHtml("<a href=\"https://accounts.silentcircle.com/account/recover/\">"
                + getResources().getString(R.string.provisioning_forgot_pwd)
                + "</a>"));
        forgotPassword.setMovementMethod(LinkMovementMethod.getInstance());
        stripUnderlines(forgotPassword);

        Typeface tf = Typeface.createFromAsset(getActivity().getAssets(), "fonts/TiemposHeadline-Regular.otf");
        TextView tv = (TextView)stepView.findViewById(R.id.AppText);
        tv.setTypeface(tf);

        stepView.setBackgroundColor(getResources().getColor(R.color.auth_background_grey));

        return stepView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mParent.showPasswordCheck(mPasswordInput, mShowPassword.isChecked());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.registerNew: {
                provisioningNewAccount();
                break;
            }
            case R.id.loginExisting: {
                provisioningOk();
                break;
            }
            case R.id.ShowPassword: {
                CheckBox cb = (CheckBox)view;
                mParent.showPasswordCheck(mPasswordInput, cb.isChecked());
                break;
            }
            default: {
                break;
            }
        }
    }

    public void provisioningNewAccount() {
        mParent.accountStep2(mUsernameInput.getText().toString().trim(), mPasswordInput.getText().toString());
    }

    // stripUnderlines and URLSpanNoUnderline are utilities for removing underlines in links
    private void stripUnderlines(TextView textView) {
        Spannable s = (Spannable)textView.getText();
        URLSpan[] spans = s.getSpans(0, s.length(), URLSpan.class);
        for (URLSpan span: spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);
            s.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL());
            s.setSpan(span, start, end, 0);
        }
        textView.setText(s);
    }

    private class URLSpanNoUnderline extends URLSpan {
        public URLSpanNoUnderline(String url) {
            super(url);
        }
        @Override public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
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
        if (!checkInputAndCopy((String) mUsernameInput.getTag(), mUsernameInput.getText().toString().trim().toLowerCase())) {
            mParent.showInputInfo(getString(R.string.provisioning_user_req));
            return;
        }
        if (!checkInputAndCopy((String) mPasswordInput.getTag(), mPasswordInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_password_req));
            return;
        }

        CharSequence nameInput = mUsernameInput.getText();
        String username = null;
        if (nameInput != null)
            username = nameInput.toString().trim().toLowerCase();
        mParent.accountStep3(mCustomerData, username, true);
    }

}
