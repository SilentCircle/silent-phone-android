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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Constants;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class AccountStep3 extends Fragment implements View.OnClickListener {

    private static final String TAG = "ProvisioningBpStep3";
    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int AUTH_ERROR_ERROR_CODE = 4;

    private AuthenticatorActivity mParent;
    private StringBuilder mContent = new StringBuilder();

    private CheckBox mTcCheckbox;
    private ScrollView mScroll;
    private ProgressBar mProgress;
    private View mProgressInner;
    private LinearLayout mButtons;
    private TextView mAuthToken;
    private String mApiKey;

    private URL mRequestUrlCreateAccount;
    private URL mRequestUrlProvisionDevice;

    private boolean mUseExistingAccount;
    private String mRoninCode;

    // the client handle license code errors in a different way
    private int mLicenseErrorCode;
    private String mLicenseErrorString;

    private Fragment mFragment;

    public static AccountStep3 newInstance(Bundle args) {
        AccountStep3 f = new AccountStep3();
        f.setArguments(args);
        return f;
    }

    public AccountStep3() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFragment = this;

        String deviceId = null;
        String username = null;

        Bundle args = getArguments();
        if (args != null) {
            mRoninCode = args.getString(AuthenticatorActivity.ARG_RONIN_CODE);
            deviceId = args.getString(ProvisioningActivity.DEVICE_ID);
            mUseExistingAccount = args.getBoolean(ProvisioningActivity.USE_EXISTING, false);
            username = args.getString(ProvisioningActivity.USERNAME);
        }

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Feature code: '" + mRoninCode + "', instance device id: '" + deviceId +
                "', existing: " + mUseExistingAccount + ", username: " + username);
        if (deviceId == null || username == null) {
            // Report this problem
            mParent.showErrorInfo("Feature code: '" + mRoninCode + "', device id: '" + deviceId + "'" + "username: '" + username + "'");
            return;
        }
        // Add the feature code / license code to JSON data
        // only do this if an account is being created (SPA-930)
        JSONObject data = mParent.getJsonHolder();
        if (mRoninCode != null && !mUseExistingAccount) {
            try {
                data.put("license_code", mRoninCode);
            } catch (JSONException e) {
                mParent.showErrorInfo(e.getLocalizedMessage());
                Log.e(TAG, "JSON problem: ", e);
            }
        }
        try {
            // https://sccps.silentcircle.com/v1/user/  (PUT)
            mRequestUrlCreateAccount = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mParent.getBaseContext()) +
                    ConfigurationUtilities.getUserManagementBaseV1User(mParent.getBaseContext()) +
                    Uri.encode(username) + "/");

            // https://sccps.silentcircle.com/v1/me/device/{device_id}/  (PUT)
            // TODO remove enable_tfa from querystring for production
            mRequestUrlProvisionDevice = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mParent.getBaseContext()) +
                    ConfigurationUtilities.getDeviceManagementBase(mParent.getBaseContext()) +
                    Uri.encode(deviceId) + "/?enable_tfa=1");
        } catch (MalformedURLException e) {
            mParent.provisioningCancel();
        }
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
        commonOnAttach(activity);
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
        View stepView = inflater.inflate(R.layout.provisioning_bp_s3, container, false);
        if (stepView == null)
            return null;

        mTcCheckbox = (CheckBox) stepView.findViewById(R.id.CheckBoxTC);
        mProgress = (ProgressBar) stepView.findViewById(R.id.ProgressBar);
        mProgressInner = stepView.findViewById(R.id.ProvisioningInProgress);
        mScroll = (ScrollView) stepView.findViewById(R.id.Scroll);
        mButtons = (LinearLayout) stepView.findViewById(R.id.ProvisioningButtons);
        mAuthToken = (TextView)stepView.findViewById(R.id.ProvisioningAuthTokenInput);
        mAuthToken.setHint(getString(R.string.provisioning_auth_token_hint));

        ((TextView)stepView.findViewById(R.id.CheckBoxTCText)).setMovementMethod(LinkMovementMethod.getInstance());

        stepView.findViewById(R.id.back).setOnClickListener(this);
        stepView.findViewById(R.id.create).setOnClickListener(this);

        TextView headerText = (TextView)stepView.findViewById(R.id.HeaderText);
        stepView.setBackgroundColor(getResources().getColor(R.color.auth_background_grey));
        if (mUseExistingAccount) {
            headerText.setText(getString(R.string.sign_in));
            ((TextView)stepView.findViewById(R.id.create)).setText(getText(R.string.next));
            startLoadingRegisterDevice();
        } else {
            startLoadingCreateAccount();
        }

        mAuthToken.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Only enable the button if the token is 6 digits long.
                View mButton = mButtons.findViewById(R.id.create);
                Boolean bEnable = s.toString().length() == 6;
                mButton.setEnabled(bEnable);
                if (bEnable)
                    mButton.setAlpha((float) 1);
                else
                    mButton.setAlpha((float) 0.5);
            }
        });

        return stepView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.back: {
                InputMethodManager imm = (InputMethodManager)mParent.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                mParent.backStep();
                break;
            }
            case R.id.create: {
                createAccount();
                break;
            }
            case R.id.license_next: {
                if (mLicenseErrorCode == 1) {
                    mParent.provisioningCancel();
                    return;
                }
                if (mLicenseErrorCode == 2) {       // feature (Ronin) code in use, ask for existing account info
                    mParent.clearBackStack();
                    mParent.accountStep2();
                }
                break;
            }
            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    private void showProgressBar() {
        mProgress.setVisibility(View.VISIBLE);
        mProgressInner.setVisibility(View.VISIBLE);
        mScroll.setVisibility(View.INVISIBLE);
        mButtons.setVisibility(View.INVISIBLE);
    }

    private void createAccount() {
        if (mUseExistingAccount) {
            startLoadingRegisterDevice();
        } else
            startLoadingCreateAccount();
    }

    // The loader tasks use it to switch on UI fields. If running for an existing account
    // go back to username/password fragment
    private void cleanUp() {
        mParent.backStep();
    }

    private void startLoadingCreateAccount() {
        showProgressBar();
        LoaderTaskCreateAccount loaderTask = new LoaderTaskCreateAccount(mParent.getJsonHolder());
        loaderTask.execute();
    }

    /**
     * Parse the JSON data on return of account creation.
     * <p/>
     * the server returns the following result data:
     * Successful Response: HTTP 200
     * <pre>
     * <code>
     * {
     *   "result": "success"
     * }
     * </code>
     * </pre>
     * <p/>
     * Failure Response: HTTP 4xx
     * <pre>
     * <code>
     * {
     *   "result": "error"
     *   "error_number": 402
     *   "error_msg": "...description of the error..."
     *   "error_fields": {
     *       "license_code": {"error_msg": "...description of error on the license_code field...", "error_number": 203}
     *   }
     * }
     * </code>
     * </pre>
     *
     * @return {@code null} if no error found, the error message if we see an error.
     */
    private String parseCreateResultData() {
        String retMsg;
        if (mContent.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(mContent.toString());
                if (!jsonObj.has("result"))         // A missing result field indicates success
                    return null;
                String result = jsonObj.getString("result");  // Otherwise check for success (some APIs report it this way)
                if ("success".equals(result)) {
                    return null;
                }
                else {
                    ArrayList<String> errors = parseErrorFields(jsonObj);
                    StringBuilder errorString = new StringBuilder();
                    for (String err : errors) {
                        errorString.append('\n').append(err);
                    }
                    retMsg = getString(R.string.account_creation_error) + ": " + errorString.toString();
                    Log.w(TAG, "Provisioning error: " + (mLicenseErrorString != null ? mLicenseErrorString : errorString.toString()));
                }
            } catch (JSONException e) {
                retMsg = getString(R.string.account_creation_wrong_format) + e.getMessage();
                Log.w(TAG, "JSON exception: " + e);
            }
        }
        else {
            retMsg = getString(R.string.account_creation_no_data) + " (" + mContent.length() + ")";
        }
        return retMsg;
    }

    private ArrayList<String> parseErrorFields(JSONObject jsonObj) {
        String error;
        ArrayList<String> errorList = new ArrayList<>(6);
        try {
            error = jsonObj.getString("error_msg");
        } catch (JSONException e) {
            errorList.add(getString(R.string.account_creation_wrong_format) + e.getMessage());
            Log.w(TAG, "JSON exception: " + e);
            return errorList;
        }
        if (!jsonObj.has("error_fields")) {
            errorList.add(error);
            return errorList;
        }
        try {
            JSONObject errorFields = jsonObj.getJSONObject("error_fields");
            JSONObject detailError;
            if (errorFields.has("username")) {
                detailError = errorFields.getJSONObject("username");
                errorList.add(detailError.getString("error_msg"));
            }
            if (errorFields.has("email")) {
                detailError = errorFields.getJSONObject("email");
                errorList.add(detailError.getString("error_msg"));
            }
            if (errorFields.has("first_name")) {
                detailError = errorFields.getJSONObject("first_name");
                errorList.add(detailError.getString("error_msg"));
            }
            if (errorFields.has("last_name")) {
                detailError = errorFields.getJSONObject("last_name");
                errorList.add(detailError.getString("error_msg"));
            }
            if (errorFields.has("license_code")) {
                detailError = errorFields.getJSONObject("license_code");
                mLicenseErrorString= detailError.getString("error_msg");
                mLicenseErrorCode = detailError.getInt("error_code");
            }
        } catch (JSONException ignore) {}
        return errorList;
    }

    private void authTokenError() {
        View view = getView();
        if (view == null)
            return;

        // Two-factor authentication token required. Rearrange the UI, the show the error text, input field, and Back and Next buttons
        mProgress.setVisibility(View.INVISIBLE);
        getView().findViewById(R.id.ProvisioningInProgress).setVisibility(View.INVISIBLE);
        mScroll.setVisibility(View.VISIBLE);
        mButtons.setVisibility(View.VISIBLE);
        view.findViewById(R.id.CheckBoxTCText).setVisibility(View.GONE);
        mTcCheckbox.setVisibility(View.GONE);
        mAuthToken.setVisibility(View.VISIBLE);
        mAuthToken.setText("");
        mAuthToken.requestFocus();
        View v = mParent.getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) mParent.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }

        TextView errorText = (TextView)view.findViewById(R.id.license_error);

        errorText.setText(getString(R.string.provisioning_auth_token_text));
        errorText.setVisibility(View.VISIBLE);
    }

    private void licenseError() {
        View view = getView();
        if (view == null)
            return;

        mParent.removeFeatureCode();

        // Serious problem. Rearrange the UI, the show the error text and button
        mProgress.setVisibility(View.INVISIBLE);
        getView().findViewById(R.id.ProvisioningInProgress).setVisibility(View.INVISIBLE);
        mScroll.setVisibility(View.VISIBLE);
        mButtons.setVisibility(View.GONE);
        view.findViewById(R.id.CheckBoxTCText).setVisibility(View.GONE);
        mTcCheckbox.setVisibility(View.GONE);

        TextView licenseErrorText = (TextView)view.findViewById(R.id.license_error);
        Button licenseErrorBtn = (Button)view.findViewById(R.id.license_next);
        licenseErrorText.setVisibility(View.VISIBLE);
        licenseErrorBtn.setVisibility(View.VISIBLE);
        licenseErrorBtn.setOnClickListener(this);

        switch (mLicenseErrorCode) {
            case 1:
                licenseErrorText.setText(mLicenseErrorString + "\n\n" + getString(R.string.license_code_invalid));
                break;
            case 2:
                licenseErrorText.setText(mLicenseErrorString + "\n\n" + getString(R.string.license_code_duplicate));
                break;
        }
    }

    private class LoaderTaskCreateAccount extends AsyncTask<URL, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;
        private JSONObject customerData;

        LoaderTaskCreateAccount(JSONObject data) {
            customerData = data;
        }

        @Override
        protected Integer doInBackground(URL... params) {
            int contentLength;
            String body = customerData.toString();
            if (body != null) {
                contentLength = body.getBytes().length;
            }
            else {
                mParent.showErrorInfo(getString(R.string.account_creation_wrong_format));
                return -1;
            }
            try {
                // For an existing account we add the license code to the account, thus PUT to modify
                // the existing account.
                urlConnection = (HttpsURLConnection) mRequestUrlCreateAccount.openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestMethod("PUT");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                urlConnection.setFixedLengthStreamingMode(contentLength);

                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(body.getBytes());
                out.flush();

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

                if (ret != HttpsURLConnection.HTTP_OK) {
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getErrorStream()), mContent);
                }
                return ret;
            } catch (IOException e) {
                if(!Utilities.isNetworkConnected(mFragment.getActivity())){
                    return Constants.NO_NETWORK_CONNECTION;
                }
                mParent.showInputInfo(getString(R.string.provisioning_no_network) + e.getLocalizedMessage());
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } catch (Exception e) {
                mParent.showInputInfo(getString(R.string.provisioning_error) + e.getLocalizedMessage());
                Log.e(TAG, "Network connection problem: " + e.getMessage());
                return -1;
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                startLoadingRegisterDevice();
            }
            else if (result == Constants.NO_NETWORK_CONNECTION){
                showDialog(R.string.information_dialog, R.string.connected_to_network, android.R.string.ok, -1);
                cleanUp();
            }
            else {
                String creationResult = parseCreateResultData();
                if (mLicenseErrorCode > 0)
                    licenseError();
                else {
                    mParent.showInputInfo(creationResult);
                    cleanUp();
                }
            }
        }
    }

    /* *********************************************************************************
     * Register the device with the now known username and password. Actually this is
     * the same loader as we use it in normal username/password provisioning
     * ******************************************************************************* */
    private void startLoadingRegisterDevice() {
        JSONObject data = null;
        // Setup other JSON and fill it with data we need for device provisioning
        if (mParent == null)
            return;
        String hwDeviceId = Utilities.hashMd5(TiviPhoneService.getHwDeviceId(mParent));
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Hardware device id: " + hwDeviceId );
        try {
            final String deviceName = Build.MODEL;
            data = new JSONObject(mParent.getJsonHolder(), new String[] {"username"});
            data.put("password", mParent.getJsonHolder().getString(mUseExistingAccount ? "current_password" : "password"));
            data.put("device_name", deviceName);
            data.put("persistent_device_id", hwDeviceId);
            data.put("app", "silent_phone");
            data.put("device_class", "android");
            data.put("version", BuildConfig.SPA_BUILD_NUMBER);
            if (!TextUtils.isEmpty(mAuthToken.getText()))
                data.put("tfa_code", mAuthToken.getText());
        } catch (JSONException ignore) {
        }
        showProgressBar();
        LoaderTaskRegisterDevice loaderTask = new LoaderTaskRegisterDevice(data);
        loaderTask.execute();
    }

    /**
     * Parse JSON data on return of device provisioning.
     *
     * The function parses the JSON data and stores the API key if the provisioning
     * was successful. Otherwise it returns the error message sent by the server.
     *
     * The server return the following result data:
     * Successful Response: HTTP 200
     *
     * <pre>
     * <code>
     * {
     *   "api_key": "31d357fb07d1abedc78f9320cf68344600bf15c43ad7d173c08d8cd9",
     *   "result": "success"
     * }
     * </code>
     * </pre>
     *
     * Failure Response: HTTP 4xx
     * <pre>
     * <code>
     * {
     *   "result": "error",
     *   "error_msg": "...description of the error...",
     * }
     * </code>
     * </pre>
     *
     * @return {@code null} if server returned success, the error message otherwise.
     */
    private String parseRegisterResultData() {
        String retMsg = null;
        if (mContent.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(mContent.toString());
                String result = jsonObj.getString("result");
                if ("success".equals(result))
                    mApiKey = jsonObj.getString("api_key");
                else {
                    retMsg = getString(R.string.provisioning_error) + ": " + jsonObj.getString("error_msg");
                    Log.w(TAG, "Provisioning error: " + jsonObj.getString("error_msg"));
                }
            } catch (JSONException e) {
                retMsg = getString(R.string.provisioning_wrong_format) + e.getMessage();
                Log.w(TAG, "JSON exception: " + e);
            }
        }
        else {
            retMsg = getString(R.string.provisioning_no_data) + " (" + mContent.length() + ")";
        }
        return retMsg;
    }

    private boolean shouldRequestTwoFactorAuthToken() {
        boolean isTwoFactorEnabled = false;
        if (mContent.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(mContent.toString());
                String result = jsonObj.getString("result");
                int errorCode = jsonObj.getInt("error_code");
                if ("error".equals(result) && errorCode == AUTH_ERROR_ERROR_CODE) {
                    isTwoFactorEnabled = true;
                }
            } catch (JSONException e) {
                /* ignore and return false */
            }
        }
        return isTwoFactorEnabled;
    }

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int nagetiveBtnLabel) {
        com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment infoMsg = com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, nagetiveBtnLabel);
        FragmentManager fragmentManager = mFragment.getFragmentManager();
        infoMsg.show(fragmentManager,TAG );
    }

    private class LoaderTaskRegisterDevice extends AsyncTask<URL, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;
        private String errorMessage;
        private JSONObject customerData;

        LoaderTaskRegisterDevice(JSONObject data) {
            customerData = data;
        }

        @Override
        protected Integer doInBackground(URL... params) {
            int contentLength;
            String body = customerData.toString();
            if (body != null) {
                contentLength = body.getBytes().length;
            }
            else {
                errorMessage = getString(R.string.provisioning_wrong_format);
                return -1;
            }
            try {
                urlConnection = (HttpsURLConnection) mRequestUrlProvisionDevice.openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestMethod("PUT");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                urlConnection.setFixedLengthStreamingMode(contentLength);

                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(body.getBytes());
                out.flush();

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code-2: " + ret);

                if (ret == HttpsURLConnection.HTTP_OK) {
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
                }
                else {
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getErrorStream()), mContent);
                }
                return ret;

            } catch (IOException e) {
                if(!Utilities.isNetworkConnected(mFragment.getActivity())){
                    return Constants.NO_NETWORK_CONNECTION;
                }
                errorMessage = getString(R.string.provisioning_no_network) + e.getLocalizedMessage();
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } catch (Exception e) {
                errorMessage = getString(R.string.provisioning_error) + e.getLocalizedMessage();
                Log.e(TAG, "Network connection problem: " + e.getMessage());
                return -1;
            } finally {
                urlConnection.disconnect();
            }
        }

        protected void onProgressUpdate(Integer... progress) {
//            setProgressPercent(progress[0]);
        }

        @Override
        protected void onCancelled(Integer result) {
            cleanUp();
        }

        @Override
        protected void onPostExecute(Integer result) {
            String message = parseRegisterResultData();
            if (result == HttpsURLConnection.HTTP_OK && message == null && mApiKey != null) {
                mParent.usernamePasswordDone(mApiKey);
            }
            else if (result == Constants.NO_NETWORK_CONNECTION) {
                showDialog(R.string.information_dialog, R.string.connected_to_network, android.R.string.ok, -1);
                cleanUp();
            }
            else {
                message = errorMessage != null ? errorMessage : message;
                if (shouldRequestTwoFactorAuthToken()) {
                    if (!TextUtils.isEmpty(mAuthToken.getText())) {
                        showDialog(R.string.information_dialog, R.string.provisioning_auth_token_error, android.R.string.ok, -1);
                    }
                    authTokenError();
                } else {
                    mParent.showInputInfo(message);
                    cleanUp();
                }
            }
        }

    }


}
