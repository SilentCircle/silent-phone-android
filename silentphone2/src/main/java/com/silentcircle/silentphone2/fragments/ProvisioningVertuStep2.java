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

package com.silentcircle.silentphone2.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Constants;
import com.silentcircle.silentphone2.util.DeviceDetectionVertu;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class ProvisioningVertuStep2 extends Fragment implements View.OnClickListener{

    private static String TAG = "ProvisioningVertuStep2";
    private static final int MIN_CONTENT_LENGTH = 10;

//    private static final String testData = "{\n" +
//            "    \"partner\": \"Vertu\",\n" +
//            "    \"partner_greeting\": \"Welcome to Silent Circle\",\n" +
//            "    \"required\": [\"username\", \"password\", \"psn\"],\n" +
//            "    \"optional\": [\"first_name\", \"last_name\", \"email_address\"]\n" +
//            "}";

    private URL requestUrl;

    private String deviceProvisioningData[];

    private StringBuilder mContent = new StringBuilder();

    private JSONObject mCustomerData = new JSONObject();

    private EditText usernameInput;
    private TextView pwStrength;
    private EditText passwordInput;
//    private EditText passwordInput2;

    private EditText firstNameInput;

    private EditText lastNameInput;

    private EditText emailInput;

    private ScrollView mScroll;
    private ProgressBar mProgress;
    private LinearLayout mButtons;

    private ProvisioningActivity mParent;

    private JSONArray mRequiredArray;


    public static ProvisioningVertuStep2 newInstance() {
        return new ProvisioningVertuStep2();
    }

    public ProvisioningVertuStep2() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deviceProvisioningData = DeviceDetectionVertu.getDeviceData(mParent);
        try {
            requestUrl = new URL(deviceProvisioningData[0]);
        } catch (MalformedURLException e) {
            mParent.finish();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (ProvisioningActivity) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View vertuStepView = inflater.inflate(R.layout.provisioning_vertu_s2, container, false);
        if (vertuStepView == null)
            return null;

        ProvisioningActivity.FilterEnter filterEnter = new ProvisioningActivity.FilterEnter();
        PasswordFilter pwFilter = new PasswordFilter();

        pwStrength = (TextView) vertuStepView.findViewById(R.id.ProvisioningPasswordStrength);

        usernameInput = (EditText) vertuStepView.findViewById(R.id.ProvisioningUsernameInput);
        usernameInput.addTextChangedListener(filterEnter);

        passwordInput = (EditText) vertuStepView.findViewById(R.id.ProvisioningPasswordInput);
        passwordInput.addTextChangedListener(pwFilter);

        firstNameInput = (EditText) vertuStepView.findViewById(R.id.ProvisioningFirstNameInput);
        firstNameInput.addTextChangedListener(filterEnter);

        lastNameInput = (EditText) vertuStepView.findViewById(R.id.ProvisioningLastNameInput);
        lastNameInput.addTextChangedListener(filterEnter);

        emailInput = (EditText) vertuStepView.findViewById(R.id.ProvisioningEmailInput);
        emailInput.addTextChangedListener(filterEnter);

        mProgress = (ProgressBar) vertuStepView.findViewById(R.id.ProgressBar);
        mScroll = (ScrollView) vertuStepView.findViewById(R.id.Scroll);
        mButtons = (LinearLayout) vertuStepView.findViewById(R.id.ProvisioningButtons);

        vertuStepView.findViewById(R.id.back).setOnClickListener(this);
        vertuStepView.findViewById(R.id.next).setOnClickListener(this);
        vertuStepView.findViewById(R.id.ShowPassword).setOnClickListener(this);

        startLoading();
        return vertuStepView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.back: {
                mParent.backStep();
                break;
            }
            case R.id.next: {
                provisioningOk();
                break;
            }
            case R.id.ShowPassword: {
                CheckBox cb = (CheckBox)view;
                mParent.showPasswordCheck(passwordInput, cb.isChecked());
                break;
            }
        }
    }

    private void startLoading() {
        LoaderTask mLoaderTask = new LoaderTask();
        mLoaderTask.execute();
    }

    private void parseProvisioningData() {

        /*
         * OK, here we got a positive answer, pre-provisioned account was found. Check if we have some content,
         * switch on the fields for extra provisioning info
         */
//        String greet = null;
//        String partner = null;

        if (mContent.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(mContent.toString());
//                greet = jsonObj.getString("partner_greeting");
//                partner = jsonObj.getString("partner");
                mRequiredArray = jsonObj.getJSONArray("required");
//                optionalArray = jsonObj.getJSONArray("optional");
            } catch (JSONException e) {
                // Check how to inform user and then restart?
                Log.w(TAG, "JSON exception: " + e);
            }
        }
//        if (greet != null)
//            infoText.setText(greet);
    }

    private void showInputScreen() {
        mProgress.setVisibility(View.INVISIBLE);
        mScroll.setVisibility(View.VISIBLE);
        mButtons.setVisibility(View.VISIBLE);
    }

    /**
     * Check if an input field is marked as required.
     *
     * @param field name of the field.
     * @return <code>true</code> if the field is marked as required
     */
    private boolean isRequired(String field) {
        if (mRequiredArray == null)
            return false;
        for (int idx = 0; idx < mRequiredArray.length(); idx++) {
            try {
                String reqField = mRequiredArray.getString(idx);
                if (reqField == null)
                    return false;
                if (reqField.equals(field))
                    return true;
            }
            catch (JSONException ignored) {}
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
        if (!checkInputAndCopy("psn", deviceProvisioningData[1])) {
            mParent.showInputInfo(getString(R.string.provisioning_psn_invalid));
            mParent.provisioningCancel(null);
            return;
        }
        if (!checkInputAndCopy((String) usernameInput.getTag(), usernameInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_user_req));
            return;
        }
        if (!checkInputAndCopy((String) passwordInput.getTag(), passwordInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_password_req));
            return;
        }
        // if (!checkPassword(passwordInput.getText().toString(), passwordInput2.getText().toString())) {
        // showInputInfo(getString(R.string.provisioning_password_match));
        // enableButtons();
        // return;
        // }
        if (!checkInputAndCopy((String) firstNameInput.getTag(), firstNameInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_firstname_req));
            return;
        }
        if (!checkInputAndCopy((String) lastNameInput.getTag(), lastNameInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_lastname_req));
            return;
        }
        if (!checkInputAndCopy((String) emailInput.getTag(), emailInput.getText())) {
            mParent.showInputInfo(getString(R.string.provisioning_email_req));
            return;
        }
        mParent.vertuStep3(mCustomerData);
    }

    private class LoaderTask extends AsyncTask<URL, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;

        private void showDialog(String title, String msg, int positiveBtnLabel, int negativeBtnLabel) {
            com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment infoMsg =
                    com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment.newInstance(title, msg, positiveBtnLabel, negativeBtnLabel);
            FragmentManager fragmentManager = mParent.getFragmentManager();
            infoMsg.show(fragmentManager,TAG );
        }

        @Override
        protected Integer doInBackground(URL... params) {
//            if (testData != null) {
//                Utilities.Sleep(1000);
//                try {
//                    mParent.readStream(new BufferedInputStream(new ByteArrayInputStream(testData.getBytes("UTF-8"))), mContent);
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                }
//                return HttpsURLConnection.HTTP_OK;
//            }
            try {
                urlConnection = (HttpsURLConnection) requestUrl.openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

                if (ret == HttpsURLConnection.HTTP_OK) {
                    ProvisioningActivity.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
                }
                else {
                    ProvisioningActivity.readStream(new BufferedInputStream(urlConnection.getErrorStream()), mContent);
                }
                return ret;
            }
            catch (IOException e) {
                if(!Utilities.isNetworkConnected(mParent)){
                    return Constants.NO_NETWORK_CONNECTION;
                }
                mParent.showErrorInfo(getString(R.string.provisioning_no_network) + e.getLocalizedMessage());
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } catch (Exception e) {
                mParent.showInputInfo(getString(R.string.provisioning_error) + e.getLocalizedMessage());
                Log.e(TAG, "Network connection problem: " + e.getMessage());
                return -1;
            }finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
        }
//
//        protected void onProgressUpdate(Integer... progress) {
//            setProgressPercent(progress[0]);
//        }
//
        @Override
        protected void onCancelled(Integer result) {
            cleanUp();
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                parseProvisioningData();
                showInputScreen();
                return;
            }
            switch (result) {
                case Constants.NO_NETWORK_CONNECTION:
                    showDialog(mParent.getString(R.string.information_dialog), mParent.getString(R.string.connected_to_network), android.R.string.ok, -1);
//                    mParent.clearBackStack();
//                    mParent.usernamePassword();
                    break;
                case HttpsURLConnection.HTTP_NOT_FOUND:
                    mParent.showInputInfo(getString(R.string.provisioning_error) + "\n" + mContent.toString());
//                    mParent.clearBackStack();
//                    mParent.usernamePassword();             // TODO: agree with Vertu about handling. step back?
                    break;

                case HttpsURLConnection.HTTP_FORBIDDEN:
                    mParent.showInputInfo(getString(R.string.provisioning_already_registered));
//                    mParent.clearBackStack();
//                    mParent.usernamePassword();
                    break;

                default:
                    mParent.showInputInfo(getString(R.string.provisioning_error) + "\n" + mContent.toString());
//                    mParent.clearBackStack();
//                    mParent.usernamePassword();
                    break;
            }
            mParent.clearBackStack();
            mParent.usernamePassword();
//            cleanUp();
        }

        private void cleanUp() {
            mParent.backStep();
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
                pwStrength.setText(null);
                return;
            }
            switch (strength) {
                case 0:
                    pwStrength.setText(getString(R.string.pwstrength_weak));
                    break;
                case 1:
                    pwStrength.setText(getString(R.string.pwstrength_good));
                    break;
                case 2:
                    pwStrength.setText(getString(R.string.pwstrength_strong));
                    break;
            }
        }
    }
}
