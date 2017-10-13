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

package com.silentcircle.silentphone2.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialogHelperActivity;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
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

public class ProvisioningUserPassword extends Fragment implements ProvisioningActivity.ProvisioningCallback {

    private static final String TAG = "ProvisioningUserPW";
    private static final int MIN_CONTENT_LENGTH = 10;
//    private static final String testData ="{\n" +
//            "    \"api_key\": \"31d357fb07d1abedc78f9320cf68344600bf15c43ad7d173c08d8cd9\",\n" +
//            "    \"result\": \"success\"\n" +
//            "}";


    private URL requestUrl;

    private StringBuilder mContent = new StringBuilder();

    private String mApiKey;
    private JSONObject customerData = new JSONObject();

    private View userPasswordView;
    private RelativeLayout inputFields;
    private ProgressBar progress;
    private LinearLayout buttons;
    private EditText passwordInput;

    private ProvisioningActivity mParent;

    public static ProvisioningUserPassword newInstance(Bundle args) {
        ProvisioningUserPassword f = new ProvisioningUserPassword();
        f.setArguments(args);
        return f;
    }

    public ProvisioningUserPassword() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            mParent.finish();
            return;
        }
        String deviceId = args.getString(ProvisioningActivity.DEVICE_ID);
        try {
            // https://sccps.silentcircle.com//v1/me/device/{device_id}/  (PUT)
            String resourceSec = ConfigurationUtilities.getProvisioningBaseUrl(mParent.getBaseContext()) +
                    ConfigurationUtilities.getDeviceManagementBase(mParent.getBaseContext());
            requestUrl = new URL(resourceSec + Uri.encode(deviceId) + "/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            mParent.finish();
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            commonOnAttach(activity);
        }
    }

    private void commonOnAttach(Activity activity) {
        try {
            mParent = (ProvisioningActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must be ProvisioningActivity.");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userPasswordView = inflater.inflate(R.layout.provisioning_user_password, container, false);
        if (userPasswordView == null)
            return null;
        inputFields = (RelativeLayout)userPasswordView.findViewById(R.id.UsernamePasswordFields);
        progress = (ProgressBar)userPasswordView.findViewById(R.id.ProgressBar);
        buttons = (LinearLayout)userPasswordView.findViewById(R.id.UsernamePasswordButtons);
        passwordInput = (EditText)userPasswordView.findViewById(R.id.PasswordInput);

        if (DeviceDetectionVertu.isVertu()) {
            ((TextView)userPasswordView.findViewById(R.id.UsernamePasswordInfoText)).setText(getString(R.string.provisioning_vertu_welcome));
        }

        ((TextView)userPasswordView.findViewById(R.id.UsernamePasswordCheckBoxTCText)).setMovementMethod(
                new ViewUtil.MovementCheck(mParent, userPasswordView, R.string.toast_no_browser_found));
        inputFields.setVisibility(View.VISIBLE);
        userPasswordView.findViewById(R.id.UsernamePasswordOK).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!prepareJsonData())
                    return;
                inputFields.setVisibility(View.INVISIBLE);
                buttons.setVisibility(View.INVISIBLE);
                progress.setVisibility(View.VISIBLE);
                startLoading();
            }
        });

        userPasswordView.findViewById(R.id.UsernamePasswordCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mParent != null) {
                    mParent.provisioningCancel();
                }
            }
        });

        userPasswordView.findViewById(R.id.PasswordShow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBox cbv = (CheckBox)v;
                mParent.showPasswordCheck(passwordInput, cbv.isChecked());
            }
        });
        return userPasswordView;
    }

    public void provisioningDone(int provisioningResult, String result) {
        if (provisioningResult >= 0) {
            mParent.setResult(Activity.RESULT_OK);
            mParent.finish();
        }
        else {
            DialogHelperActivity.showDialog(R.string.provisioning_error, result, android.R.string.ok, -1);
            mParent.provisioningCancel();
        }
    }

    private boolean prepareJsonData() {
        CharSequence username = ((EditText)userPasswordView.findViewById(R.id.UsernameInput)).getText();
        CharSequence password = passwordInput.getText();
//        CheckBox ctv = (CheckBox)userPasswordView.findViewById(R.id.UsernamePasswordCheckBoxTC);

        if (TextUtils.isEmpty(username)) {
            DialogHelperActivity.showDialog(R.string.information_dialog, R.string.provisioning_user_req, android.R.string.ok, -1);
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            DialogHelperActivity.showDialog(R.string.information_dialog, R.string.provisioning_password_req, android.R.string.ok, -1);
            return false;
        }

//        SPA-683: removed the T&S and the check box
//        if (!ctv.isChecked()) {
//            mParent.showInputInfo(getString(R.string.provisioning_check_tc));
//            return false;
//        }
        String hwDeviceId = Utilities.hashMd5(TiviPhoneService.getHwDeviceId(mParent));
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Hardware device id: " + hwDeviceId );
        try {
            customerData.put("username", username);
            customerData.put("password", password);
            customerData.put("persistent_device_id", hwDeviceId);
            customerData.put("device_name", Build.MODEL);
            customerData.put("app", "silent_phone");
            customerData.put("device_class", "android");
            customerData.put("version", BuildConfig.SPA_BUILD_NUMBER);
        }
        catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void startLoading() {
        LoaderTask mLoaderTask = new LoaderTask();
        mLoaderTask.execute();
    }

    // Returns 'null' on success, an error message otherwise.
    private String parseContent() {
        String retMsg = null;
        if (mContent.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(mContent.toString());
                String result = jsonObj.getString("result");
                if ("success".equals(result))
                    mApiKey = jsonObj.getString("api_key");
                else {
                    String error = jsonObj.getString("error_msg");
                    retMsg = getString(R.string.provisioning_error) + ": " + error;
                    Log.w(TAG, "Provisioning error: " + error);
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

    private class LoaderTask extends AsyncTask<URL, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;
        private String errorMessage = null;

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
//            if (testData != null) {
//                Utilities.Sleep(1000);
//                try {
//                    mParent.readStream(new BufferedInputStream(new ByteArrayInputStream(testData.getBytes("UTF-8"))), mContent);
//                    errorMessage = parseContent();
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                }
//                return HttpsURLConnection.HTTP_OK;
//            }
            OutputStream out = null;
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
                urlConnection.setRequestMethod("PUT");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                urlConnection.setFixedLengthStreamingMode(contentLength);

                out = new BufferedOutputStream(urlConnection.getOutputStream());
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
                if(!Utilities.isNetworkConnected(mParent)){
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
                try {
                    if (out != null)
                        out.close();
                } catch (IOException ignore) { }
                if (urlConnection != null)
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
            String message = parseContent();
            if (result == HttpsURLConnection.HTTP_OK && message == null && mApiKey != null) {
//                    if (testData != null) {
//                        Log.d(TAG, "Authorization code: " + mApiKey);
//                        provisioningDone(-1, "Test message username password");
//                    }
//                    else
                mParent.usernamePasswordDone(mApiKey, ProvisioningUserPassword.this);
            }
            else if (result == Constants.NO_NETWORK_CONNECTION) {
                DialogHelperActivity.showDialog(R.string.provisioning_error, R.string.connected_to_network, android.R.string.ok, -1);
                cleanUp();
            }
            else {
                message = errorMessage != null ? errorMessage : message;
                DialogHelperActivity.showDialog(R.string.provisioning_error, message, android.R.string.ok, -1);
                cleanUp();
            }
        }

        private void cleanUp() {
            progress.setVisibility(View.INVISIBLE);
            inputFields.setVisibility(View.VISIBLE);
            buttons.setVisibility(View.VISIBLE);
        }
    }

}
