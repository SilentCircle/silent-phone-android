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

package com.silentcircle.silentphone2.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class ProvisioningVertuStep3 extends Fragment implements View.OnClickListener,
        ProvisioningActivity.ProvisioningCallback {

    private static final String TAG = "ProvisioningVertuStep3";
    private static final int MIN_CONTENT_LENGTH = 10;

//    private final static String testData = "{\n" +
//            "    \"provisioning_code\": \"TESTING1\"\n" +
//            "}";

    private ProvisioningActivity mParent;
    private StringBuilder mContent = new StringBuilder();
    private String mProvisioningCode;

    private CheckBox mTcCheckbox;
    private ScrollView mScroll;
    private ProgressBar mProgress;
    private LinearLayout mButtons;

    private URL requestUrl;

    public static ProvisioningVertuStep3 newInstance() {
        return new ProvisioningVertuStep3();
    }

    public ProvisioningVertuStep3() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] deviceProvisioningData = DeviceDetectionVertu.getDeviceData(mParent);
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
        View vertuStepView = inflater.inflate(R.layout.provisioning_vertu_s3, container, false);
        if (vertuStepView == null)
            return null;

        mTcCheckbox = (CheckBox) vertuStepView.findViewById(R.id.CheckBoxTC);
        mProgress = (ProgressBar) vertuStepView.findViewById(R.id.ProgressBar);
        mScroll = (ScrollView) vertuStepView.findViewById(R.id.Scroll);
        mButtons = (LinearLayout) vertuStepView.findViewById(R.id.ProvisioningButtons);

        ((TextView) vertuStepView.findViewById(R.id.CheckBoxTCText)).setMovementMethod(LinkMovementMethod.getInstance());

        vertuStepView.findViewById(R.id.back).setOnClickListener(this);
        vertuStepView.findViewById(R.id.create).setOnClickListener(this);

        return vertuStepView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.back: {
                mParent.backStep();
                break;
            }
            case R.id.create: {
                createAccount();
                break;
            }
            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    public void provisioningDone(int provisioningResult, String result) {
        if (provisioningResult >= 0) {
            mParent.setResult(Activity.RESULT_OK);
            mParent.finish();
        }
        else {
            mParent.showErrorInfo(result);      // Show Error terminates activity after user confirmed message
        }
    }

    private void showProgressBar() {
        mProgress.setVisibility(View.VISIBLE);
        mScroll.setVisibility(View.INVISIBLE);
        mButtons.setVisibility(View.INVISIBLE);
    }

    private void createAccount() {
//        SPA-683: removed the T&S and the check box
//        if (!mTcCheckbox.isChecked()) {
//            mParent.showInputInfo(getString(R.string.provisioning_check_tc));
//            return;
//        }
        startLoading();
    }

    private String parseProvisioningData() {
        String retMsg = null;
        if (mContent.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(mContent.toString());
                if (jsonObj.has("provisioning_code")) {
                    mProvisioningCode = jsonObj.getString("provisioning_code");
                }
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


    private void startLoading() {
        LoaderTask mLoaderTask = new LoaderTask(mParent.getJsonHolder());
        showProgressBar();
        mLoaderTask.execute();
    }

    // Loader task calls this to re-enable UI fields.
    private void cleanup() {
        mProgress.setVisibility(View.INVISIBLE);
        mScroll.setVisibility(View.VISIBLE);
        mButtons.setVisibility(View.VISIBLE);
    }

    private void showDialog(String title, String msg, int positiveBtnLabel, int nagetiveBtnLabel) {
        com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment infoMsg = com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment.newInstance(title, msg, positiveBtnLabel, nagetiveBtnLabel);
        FragmentManager fragmentManager = mParent.getFragmentManager();
        infoMsg.show(fragmentManager,TAG );
    }

    private class LoaderTask extends AsyncTask<URL, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;
        private JSONObject customerData;

        LoaderTask(JSONObject data) {
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
                mParent.showErrorInfo(getString(R.string.provisioning_wrong_format));
                return -1;
            }
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
                urlConnection.setRequestMethod("POST");
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
                    ProvisioningActivity.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
                }
                else {
                    ProvisioningActivity.readStream(new BufferedInputStream(urlConnection.getErrorStream()), mContent);
                }
                return ret;
            } catch (IOException e) {
                if(!Utilities.isNetworkConnected(mParent)){
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
            String errorMessage = parseProvisioningData();
            if (result == HttpsURLConnection.HTTP_OK && errorMessage == null) {
                if (mProvisioningCode != null) {
//                    if (testData != null) {
//                        Log.d(TAG, "Activation code: " + mProvisioningCode);
//                        provisioningDone(-1, "Test message");
//                    }
//                    else
                        mParent.provisionWithActivationCode(mProvisioningCode, ProvisioningVertuStep3.this);
                }
                return;
            }
            switch (result) {
                case Constants.NO_NETWORK_CONNECTION:
                    showDialog(mParent.getString(R.string.information_dialog), mParent.getString(R.string.connected_to_network), android.R.string.ok, -1);
                    break;
                case HttpsURLConnection.HTTP_NOT_FOUND:
                    String msg = getString(R.string.provisioning_no_data);
                    Log.w(TAG, "No provisioning data available" + result);
                    mParent.showInputInfo(msg);
                    // TODO: agree with Vertu about handling. step back?
                    break;

                case HttpsURLConnection.HTTP_FORBIDDEN:
                    mParent.showInputInfo(getString(R.string.provisioning_already_registered));
                    break;

                default:                        // Covers all other HTTP codes
                    mParent.showInputInfo(errorMessage);
                    break;
            }
            cleanup();
        }
    }
}
