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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.PageIndicator;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivityInternal;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import org.json.JSONException;
import org.json.JSONObject;

public class AccountStep1 extends Fragment implements View.OnClickListener {

    private static final String ONBOARDING_PAGE =
            "com.silentcircle.accounts.AccountStep1.onboardingPage";
    private static final String USERNAME =
            "com.silentcircle.accounts.AccountStep1.username";
    private static final String PASSWORD =
            "com.silentcircle.accounts.AccountStep1.password";

    private AuthenticatorActivity mParent;

    private static final String[] mRequiredArray = {"username", "current_password"};
    private JSONObject mCustomerData = new JSONObject();

    private TextInputLayout mUsernameLayout;
    private EditText mUsernameInput;
    private TextInputLayout mPasswordLayout;
    private EditText mPasswordInput;
    private CheckBox mShowPassword;
    private Button mRegisterNew;
    private Button mLoginExisting;
    private CharSequence mLoginSavedText;
    private TextView mForgotPassword;
    private ScrollView mScrollView;
    private RelativeLayout mUserFields;
    private Button mEnvironmentButton;

    private PagerAdapter mOnBoardingPagerAdapter;
    private ViewPager mOnBoardingPager;
    private PageIndicator mPageIndicator;
    private View mIntroPage1;
    private View mIntroPage2;
    private View mIntroPage3;
    private View mIntroLoginPage;

    private boolean loginSso = false;
    private boolean mUserValid = false;
    private boolean mPassValid = false;

    private int mOnBoardingPage = 0;
    private boolean mOnBoardingSeen;
    private CharSequence mSavedUsername;
    private char[] mSavedPassword;

    private float mActivityHorizontalMargin;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ConfigurationUtilities.mUseDevelopConfiguration) {
            getActivity().getWindow().setStatusBarColor(ContextCompat.getColor(getActivity(), android.R.color.holo_red_dark));
        }

        mActivityHorizontalMargin = getResources().getDimension(R.dimen.activity_horizontal_margin);
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

    private void updateVisibility() {
        if(loginSso) {
            mPasswordLayout.setVisibility(View.INVISIBLE);
            mShowPassword.setVisibility(View.INVISIBLE);
            mForgotPassword.setVisibility(View.INVISIBLE);
            mRegisterNew.setVisibility(View.GONE);
            mLoginExisting.setText(R.string.provisioning_sso);
            mUsernameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        } else {
            mPasswordLayout.setVisibility(View.VISIBLE);
            mShowPassword.setVisibility(View.VISIBLE);
            mForgotPassword.setVisibility(View.VISIBLE);
            // FIXME: Uncomment when account creation is enabled
            // mRegisterNew.setVisibility(View.VISIBLE);
            mLoginExisting.setText(mLoginSavedText);
            mUsernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
        // setting input type refreshes keyboard display
        mUsernameInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mIntroPage1 = inflater.inflate(R.layout.fragment_intro, container, false);
        mIntroPage2 = inflater.inflate(R.layout.fragment_intro_1, container, false);
        mIntroPage3 = inflater.inflate(R.layout.fragment_intro_2, container, false);
        mIntroLoginPage = inflater.inflate(R.layout.fragment_login, container, false);
        return inflater.inflate(R.layout.provisioning_bp_s1, container, false);
    }

    @Override
    public void onViewCreated(View stepView, Bundle savedInstanceState) {
        super.onViewCreated(stepView, savedInstanceState);

        CharSequence userName = mSavedUsername;
        char[] password = mSavedPassword;
        if (savedInstanceState != null) {
            mOnBoardingPage = savedInstanceState.getInt(ONBOARDING_PAGE, 0);
            userName = savedInstanceState.getCharSequence(USERNAME, "");
            password = savedInstanceState.getCharArray(PASSWORD);
        }

        Bundle args = getArguments();
        if (args != null) {
            mOnBoardingSeen = args.getBoolean(ProvisioningActivity.ONBOARDING_SEEN, false);
        }

        ProvisioningActivity.FilterEnter filterEnter = new ProvisioningActivity.FilterEnter();

        mPageIndicator = (PageIndicator) stepView.findViewById(R.id.pageIndicator);


        mOnBoardingPager = (ViewPager)  stepView.findViewById(R.id.ProvisioningOnboardingFlipper);

        mUsernameInput = (EditText) mIntroLoginPage.findViewById(R.id.ProvisioningUsernameInput);
        mUsernameInput.addTextChangedListener(filterEnter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mUsernameInput.setBackground(mUsernameInput.getBackground().getConstantState().newDrawable());
        mUsernameInput.setText(userName);

        mUsernameLayout = (TextInputLayout) mIntroLoginPage.findViewById(R.id.ProvisioningUsernameLayout);
        mUsernameLayout.setErrorEnabled(true);

        mPasswordInput = (EditText) mIntroLoginPage.findViewById(R.id.ProvisioningPasswordInput);
        mPasswordInput.setTag("current_password");
        mPasswordInput.setText(password == null ? "" : new String(password));

        mPasswordLayout = (TextInputLayout) mIntroLoginPage.findViewById(R.id.ProvisioningPasswordLayout);
        mPasswordLayout.setErrorEnabled(true);

        mUsernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean newState = s.toString().contains("@");
                if (newState != loginSso) {
                    loginSso = newState;
                    updateVisibility();
                }
                mUserValid = checkValid(mUsernameInput, mUsernameLayout, null, false);
                updateLoginButton();
            }
        });

        mUsernameInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // scroll up to try to make sure password field also visible
                    scrollViewToTop(mUsernameLayout);
                } else {
                    checkValid(mUsernameInput, mUsernameLayout, getString(R.string.provisioning_user_req), false);
                }
            }
        });

        mUsernameInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_NEXT && !checkValid(mUsernameInput, mUsernameLayout, getString(R.string.provisioning_user_req), true);
            }
        });

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
                updateLoginButton();
            }
        });

        mPasswordInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // make sure password field visible
                if (!hasFocus) {
                    checkValid(mPasswordInput, mPasswordLayout, getString(R.string.provisioning_password_req), false);
                }
            }
        });

        mPasswordInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE && !checkValid(mPasswordInput, mPasswordLayout, getString(R.string.provisioning_password_req), true);
            }
        });

        mRegisterNew = (Button) mIntroLoginPage.findViewById(R.id.registerNew);
        mRegisterNew.setOnClickListener(this);

        mLoginExisting = (Button) stepView.findViewById(R.id.loginExisting);
        mLoginExisting.setOnClickListener(this);
        mLoginSavedText = mLoginExisting.getText();

        // FIXME: Remove when account creation is enabled
        // only allow create account when a license code is present
        // the following three lines should be removed when freemium support is to be included in the product
        if (args == null || TextUtils.isEmpty(args.getString(AuthenticatorActivity.ARG_RONIN_CODE, null)))
            mRegisterNew.setVisibility(View.GONE);

        mShowPassword = (CheckBox) mIntroLoginPage.findViewById(R.id.ShowPassword);
        mShowPassword.setOnClickListener(this);

        mUserFields = (RelativeLayout) mIntroLoginPage.findViewById(R.id.ProvisioningUserFields);

        ((TextView) mIntroLoginPage.findViewById(R.id.ProvisioningVersion)).setText(
                getString(R.string.provisioning_version) + " " + BuildConfig.VERSION_NAME);

        String messageBrowserNotAvailable = getString(R.string.toast_no_browser_found);

        TextView privacy = (TextView) mIntroLoginPage.findViewById(R.id.ProvisioningPrivacy);
        privacy.setText(Html.fromHtml("<a href=\"https://accounts.silentcircle.com/privacy-policy\">"
                + getResources().getString(R.string.provisioning_privacy)
                + "</a>"));
        privacy.setMovementMethod(new ViewUtil.MovementCheck(mIntroLoginPage, messageBrowserNotAvailable));
        stripUnderlines(privacy);

        TextView terms = (TextView) mIntroLoginPage.findViewById(R.id.ProvisioningTerms);
        terms.setText(Html.fromHtml("<a href=\"https://accounts.silentcircle.com/terms\">"
                + getResources().getString(R.string.provisioning_terms)
                + "</a>"));
        terms.setMovementMethod(new ViewUtil.MovementCheck(mIntroLoginPage, messageBrowserNotAvailable));
        stripUnderlines(terms);

        mForgotPassword = (TextView) mIntroLoginPage.findViewById(R.id.ProvisioningForgotPassword);
        mForgotPassword.setText(Html.fromHtml("<a href=\"https://accounts.silentcircle.com/account/recover/\">"
                + getResources().getString(R.string.provisioning_forgot_pwd)
                + "</a>"));
        mForgotPassword.setMovementMethod(new ViewUtil.MovementCheck(mIntroLoginPage, messageBrowserNotAvailable));
        stripUnderlines(mForgotPassword);

        mScrollView = (ScrollView) mIntroLoginPage.findViewById(R.id.ProvisioningScrollFrameLayout);

        Typeface tf = Typeface.createFromAsset(getActivity().getAssets(), "fonts/TiemposHeadline-Regular.otf");
        TextView tv = (TextView) mIntroLoginPage.findViewById(R.id.AppText);
        tv.setTypeface(tf);

        stepView.setBackgroundColor(ContextCompat.getColor(mParent, R.color.auth_background_grey));

        if (ConfigurationUtilities.mEnableDevDebOptions) {
            mEnvironmentButton = (Button) mIntroLoginPage.findViewById(R.id.switchConfiguration);
            mEnvironmentButton.setVisibility(View.VISIBLE);
            mEnvironmentButton.setText(ConfigurationUtilities.mUseDevelopConfiguration
                    ? R.string.switch_to_production : R.string.switch_to_develop);
            mEnvironmentButton.setOnClickListener(this);
        }

        TextView manageAccounts = (TextView) mIntroLoginPage.findViewById(R.id.ProvisioningManageAccounts);
        manageAccounts.setText(
                ConfigurationUtilities.mUseDevelopConfiguration
                        ? R.string.provisioning_manage_accounts_dev
                        : R.string.provisioning_manage_accounts);
        manageAccounts.setMovementMethod(new ViewUtil.MovementCheck(mIntroLoginPage, messageBrowserNotAvailable));
        stripUnderlines(manageAccounts);

        initOnboarding();
    }

    @Override
    public void onStart() {
        super.onStart();
        mParent.showPasswordCheck(mPasswordInput, mShowPassword.isChecked());
        updateLoginButton();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.registerNew: {
                provisioningNewAccount();
                break;
            }
            case R.id.loginExisting: {
                if (mOnBoardingPager.getCurrentItem() < (mOnBoardingPagerAdapter.getCount() - 1)) {
                    mOnBoardingPager.setCurrentItem((mOnBoardingPagerAdapter.getCount() - 1));
                } else {
                    provisioningOk();
                }
                break;
            }
            case R.id.ShowPassword: {
                CheckBox cb = (CheckBox)view;
                mParent.showPasswordCheck(mPasswordInput, cb.isChecked());
                break;
            }
            case R.id.switchConfiguration: {
                if (ConfigurationUtilities.mUseDevelopConfiguration) {
                    ConfigurationUtilities.switchToProduction(getActivity());
                } else {
                    ConfigurationUtilities.switchToDevelop(getActivity(), ConfigurationUtilities.DEVELOPMENT_NETWORK);
                }

                // Remove existing provisioning files
                TiviPhoneService.doCmd("*##*3357768*");
                TiviPhoneService.doCmd(".exit");

                // Restart flow
                Intent intent = new Intent(getActivity(), DialerActivityInternal.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

                break;
            }
            default: {
                break;
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ONBOARDING_PAGE, mOnBoardingPage);
        CharSequence username = mUsernameInput == null ? null : mUsernameInput.getText();
        outState.putCharSequence(USERNAME, username);
        CharSequence password = mPasswordInput == null ? null : mPasswordInput.getText();
        if (!TextUtils.isEmpty(password)) {
            outState.putCharArray(PASSWORD, password.toString().toCharArray());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSavedUsername = mUsernameInput.getText();
        CharSequence password = mPasswordInput.getText();
        if (!TextUtils.isEmpty(password)) {
            mSavedPassword = password.toString().toCharArray();
        }
    }

    public void provisioningNewAccount() {
        mParent.accountStep2(mUsernameInput.getText().toString().trim(), mPasswordInput.getText().toString());
    }

    public void setOnBoardingSeen(boolean onBoardingSeen) {
        mOnBoardingSeen = onBoardingSeen;
    }

    private void initOnboarding() {
        mOnBoardingPagerAdapter = new PagerAdapter() {

            View[] mViews = new View[]{mIntroPage1, mIntroPage2, mIntroPage3, mIntroLoginPage};

            @Override
            public int getCount() {
                return mViews.length;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return object == view;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View view = mViews[position];
                container.addView(view);
                return view;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }
        };

        mOnBoardingPager.setAdapter(mOnBoardingPagerAdapter);

        mOnBoardingPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // unused
            }

            @Override
            public void onPageSelected(int position) {
                mPageIndicator.setActive(position);
                mPageIndicator.setVisibility(
                        position == mOnBoardingPagerAdapter.getCount() - 1 ? View.GONE : View.VISIBLE);
                DialerUtils.hideInputMethod(mUsernameInput);
                DialerUtils.hideInputMethod(mPasswordInput);
                mOnBoardingPage = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // unused
            }
        });

        mOnBoardingPage = mOnBoardingSeen ? (mOnBoardingPagerAdapter.getCount() - 1) : mOnBoardingPage;
        mPageIndicator.setVisibility(
                mOnBoardingPage == mOnBoardingPagerAdapter.getCount() - 1 ? View.GONE : View.VISIBLE);
        mOnBoardingPager.setCurrentItem(mOnBoardingPage, false);

        alignDescriptionFieldInPages(R.id.description_text, mIntroPage1, mIntroPage2, mIntroPage3);
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

    @SuppressLint("ParcelCreator")
    private class URLSpanNoUnderline extends URLSpan {
        URLSpanNoUnderline(String url) {
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
        if (mParent != null) {
            mParent.setOnBoardingSeen(true);
        }

        if (!checkInputAndCopy((String) mUsernameInput.getTag(), mUsernameInput.getText().toString().trim().toLowerCase())) {
            setLayoutError(mUsernameLayout, getString(R.string.provisioning_user_req), true);
        }

        if(!loginSso) {
            if (!checkInputAndCopy((String) mPasswordInput.getTag(), mPasswordInput.getText())) {
                setLayoutError(mPasswordLayout, getString(R.string.provisioning_password_req), true);
            }

            if (TextUtils.isEmpty(mUsernameLayout.getError()) && TextUtils.isEmpty(mPasswordLayout.getError())) {
                CharSequence nameInput = mUsernameInput.getText();
                String username = null;
                if (nameInput != null)
                    username = nameInput.toString().trim().toLowerCase();
                mParent.accountStep3(mCustomerData, username, true);
            }
        } else {
            mParent.accountCorpEmailEntry1(mUsernameInput.getText().toString());
        }
    }

    private void updateLoginButton() {
        if (mUserValid && (mPassValid || loginSso)) {
            mLoginExisting.setAlpha(1.0f);
        } else {
            mLoginExisting.setAlpha(0.5f);
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
        // also make sure error portion of field is displayed if requested
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
        if (scrollBounds.top <=  mUserFields.getTop() + view.getTop() && scrollBounds.bottom >= mUserFields.getTop() + view.getBottom()) {
            return VIEW_VISIBLE;
        } else if (scrollBounds.top > mUserFields.getTop() + view.getTop()) {
            return VIEW_SCROLLED_OFF_TOP;
        } else if (scrollBounds.bottom < mUserFields.getTop() + view.getBottom()) {
            return VIEW_SCROLLED_OFF_BOTTOM;
        }
        return VIEW_VISIBLE;
    }

    // scroll a view to top of visible portion of screen
    private void scrollViewToTop(final View view) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                mScrollView.smoothScrollTo(0, mUserFields.getTop() + view.getTop());
            }
        });
    }

    // scroll a view to bottom of visible portion of screen
    private void scrollViewToBottom(final View view) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                mScrollView.smoothScrollTo(0, mUserFields.getTop() + view.getBottom() - mScrollView.getHeight());
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

    private void alignDescriptionFieldInPages(int id, View... views) {
        if (views == null || views.length == 0) {
            return;
        }

        Point point = new Point();
        ViewUtil.getScreenDimensions(mParent, point);

        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(point.x
                - ((int) (2 * mActivityHorizontalMargin)), View.MeasureSpec.AT_MOST);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        int maxHeight = 0;
        for (View view : views) {
            if (view == null) {
                continue;
            }
            view.measure(point.x, point.y);
            View viewById = view.findViewById(id);
            if (viewById != null) {
                viewById.measure(widthMeasureSpec, heightMeasureSpec);
                int height = viewById.getMeasuredHeight();
                maxHeight = Math.max(maxHeight, height);
            }
        }

        for (View view : views) {
            if (view == null) {
                continue;
            }
            View viewById = view.findViewById(id);
            if (viewById != null) {
                viewById.setMinimumHeight(maxHeight);
            }
        }
    }
}

