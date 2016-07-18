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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class AccountCorpEmailEntry3 extends Fragment {

    private final static String TAG = "AccountCorpEmailEntry3";
    private static final int MIN_CONTENT_LENGTH = 10;

    private AuthenticatorActivity mParent;
    private StringBuilder mContent = new StringBuilder();

    private String mApiKey;
    private String mAuthCode;
    private String mUsername;
    private String hwDeviceId;

    private Fragment mFragment;

    private URL mRequestUrlProvisionDevice;

    public static AccountCorpEmailEntry3 newInstance(Bundle args) {
        AccountCorpEmailEntry3 f = new AccountCorpEmailEntry3();
        f.setArguments(args);
        return f;
    }

    public AccountCorpEmailEntry3() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFragment = this;

        String deviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(mParent, false));
        hwDeviceId = Utilities.hashMd5(TiviPhoneService.getHwDeviceId(mParent));

        Bundle args = getArguments();
        if (args != null) {
            mUsername = args.getString(ProvisioningActivity.USERNAME);
            mAuthCode = args.getString(AccountCorpEmailEntry2.AUTH_CODE);
        }


        try {
            // https://sccps.silentcircle.com/v1/me/device/{device_id}/  (PUT)
            mRequestUrlProvisionDevice = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mParent.getBaseContext()) +
                    ConfigurationUtilities.getDeviceManagementBase(mParent.getBaseContext()) +
                    Uri.encode(deviceId) + "/");
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
        View stepView = inflater.inflate(R.layout.provisioning_corp_account_email_entry3, container, false);
        if (stepView == null)
            return null;
        return stepView;
    }


    @Override
    public void onStart() {
        super.onStart();

        startLoadingRegisterDevice();
    }

    // The loader tasks use it to switch on UI fields. If running for an existing account
    // go back to username/password fragment
    private void cleanUp() {
        mParent.backStep();
    }

    /* *********************************************************************************
     * Register the device with the ADFS provided auth_code.
     * ******************************************************************************* */
    private void startLoadingRegisterDevice() {
        JSONObject data = null;
        // Setup other JSON and fill it with data we need for device provisioning
        if (mParent == null)
            return;
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Hardware device id: " + hwDeviceId );
        try {
            final String deviceName = Build.MODEL;
            data = new JSONObject();
            data.put("username", mUsername);
            data.put("auth_cookie", mAuthCode);
            data.put("auth_type", "adfs");
            data.put("device_name", deviceName);
            data.put("persistent_device_id", hwDeviceId);
            data.put("app", "silent_phone");
            data.put("device_class", "android");
            data.put("version", BuildConfig.SPA_BUILD_NUMBER);
        } catch (JSONException ignore) {
        }

        Log.d(TAG, "Posted path: "+mRequestUrlProvisionDevice.toString());
        Log.d(TAG, "Posted JSON: "+data.toString());

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

    private void showDialog(String title, String msg, int positiveBtnLabel, int nagetiveBtnLabel) {
        com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment infoMsg = com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment.newInstance(title, msg, positiveBtnLabel, nagetiveBtnLabel);
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
            OutputStream out = null;
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
            String message = parseRegisterResultData();
            if (result == HttpsURLConnection.HTTP_OK && message == null && mApiKey != null) {
                mParent.usernamePasswordDone(mApiKey);
            }
            else if (result == Constants.NO_NETWORK_CONNECTION) {
                showDialog(mFragment.getActivity().getString(R.string.information_dialog), mFragment.getActivity().getString(R.string.connected_to_network), android.R.string.ok, -1);
                cleanUp();
            }
            else {
                message = errorMessage != null ? errorMessage : message;
                mParent.showInputInfo(message);
                cleanUp();
            }
        }
    }
}
