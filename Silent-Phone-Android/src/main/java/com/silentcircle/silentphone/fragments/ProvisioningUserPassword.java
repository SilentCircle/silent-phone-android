/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.fragments;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.activities.Provisioning;
import com.silentcircle.silentphone.activities.TMActivity;
//import com.silentcircle.silentphone.utils.BuildInfo;
import com.silentcircle.silentphone.utils.DeviceDetectionVertu;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;

public class ProvisioningUserPassword extends SherlockFragment {

    private static String TAG = "ProvisioningUserPassword";
    private static final int MIN_CONTENT_LENGTH = 10;

    private URL requestUrl;

    private StringBuilder content = new StringBuilder();

    private String deviceId;
    private String apiKey;
    private JSONObject customerData = new JSONObject();

    private View userPasswordView;
    private RelativeLayout inputFields;
    private RelativeLayout progress;
    private LinearLayout buttons;
    private EditText passwordInput;

    public ProvisioningUserPassword(String devId) {
        deviceId = devId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            String resourceSec = "https://sccps.silentcircle.com/v1/me/device/";
            requestUrl = new URL(resourceSec + Uri.encode(deviceId) + "/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            getActivity().finish();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userPasswordView = inflater.inflate(R.layout.provisioning_user_password, null);
        inputFields = (RelativeLayout)userPasswordView.findViewById(R.id.UsernamePasswordFields);
        progress = (RelativeLayout)userPasswordView.findViewById(R.id.UsernamePasswordProgressLayout);
        buttons = (LinearLayout)userPasswordView.findViewById(R.id.UsernamePasswordButtons);
        passwordInput = (EditText)userPasswordView.findViewById(R.id.PasswordInput);

        if (DeviceDetectionVertu.isVertu()) {
            ((TextView)userPasswordView.findViewById(R.id.UsernamePasswordInfoText)).setText(getString(R.string.provisioning_vertu_welcome));
        }

        userPasswordView.findViewById(R.id.UsernamePasswordCheckBoxTC).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckedTextView ctv = (CheckedTextView)v;
                ctv.toggle();
            }
        });
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
        userPasswordView.findViewById(R.id.PasswordShow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBox cbv = (CheckBox)v;
                if (cbv.isChecked()) {
                    passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                }
                else {
                    passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                passwordInput.setSelection(passwordInput.getText().length());

            }
        });
        return userPasswordView;
    }

    private boolean prepareJsonData() {
        String username = ((EditText)userPasswordView.findViewById(R.id.UsernameInput)).getText().toString();
        String password = passwordInput.getText().toString();
        CheckedTextView ctv = (CheckedTextView)userPasswordView.findViewById(R.id.UsernamePasswordCheckBoxTC);

        if (TextUtils.isEmpty(username)) {
            ((Provisioning)getActivity()).showInputInfo(getString(R.string.provisioning_user_req));
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            ((Provisioning)getActivity()).showInputInfo(getString(R.string.provisioning_password_req));
            return false;
        }
        if (!ctv.isChecked()) {
            ((Provisioning)getActivity()).showInputInfo(getString(R.string.provisioning_check_tc));
            return false;
        }
        try {
            customerData.put("username", username);
            customerData.put("password", password);
            customerData.put("device_name", Build.MODEL);
            customerData.put("app", "silent_phone");
            customerData.put("device_class", "android");
//            customerData.put("version", BuildInfo.BUILD_VERSION);
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

    private void readStream(InputStream in) {
        BufferedReader reader;
        content.delete(0, content.length()); // remove old content
        reader = new BufferedReader(new InputStreamReader(in));

        try {
            for (String str = reader.readLine(); str != null; str = reader.readLine()) {
                content.append(str).append('\n');
            }
            if (TMActivity.SP_DEBUG) Log.d(TAG, "Result: " + content);
        }
        catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseContent() {
        if (content.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(content.toString());
                String result = jsonObj.getString("result");
                if ("success".equals(result))
                    apiKey = jsonObj.getString("api_key");
                else {
                    String msg = getString(R.string.provisioning_error) + ": " + result;
                    ((Provisioning)getActivity()).showInputInfo(msg);
                }
            } catch (JSONException e) {
                String msg = getString(R.string.provisioning_wrong_format) + e.getMessage();
                Log.w(TAG, "JSON exception: " + e);
                ((Provisioning)getActivity()).showInputInfo(msg);
            }
        }
    }

    private class LoaderTask extends AsyncTask<URL, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;

        @Override
        protected Integer doInBackground(URL... params) {
            int contentLength = 0;
            String body = customerData.toString();
            if (body != null) {
                contentLength = body.getBytes().length;
            }
            try {
                urlConnection = (HttpsURLConnection) requestUrl.openConnection();
                urlConnection.setRequestMethod("PUT");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setFixedLengthStreamingMode(contentLength);

                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(body.getBytes());
                out.flush();

                int ret = urlConnection.getResponseCode();
                if (TMActivity.SP_DEBUG) Log.d(TAG, "HTTP code-2: " + ret);

                if (ret == HttpsURLConnection.HTTP_OK) {
                    InputStream in;
                    try {
                        in = new BufferedInputStream(urlConnection.getInputStream());
                        readStream(in);
                        parseContent();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return ret;

            } catch (IOException e) {
                Log.e(TAG, "Network not available: " + e.getMessage());
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
            if (result == HttpsURLConnection.HTTP_OK) {
                if (apiKey != null)
                    ((Provisioning)getActivity()).usernamePasswordDone(apiKey);
            }
            else if (result == HttpsURLConnection.HTTP_NOT_FOUND) {
                String msg = getString(R.string.provisioning_no_data); // "No provisioning data available";
                Log.w(TAG, "No provisioning data available: " + result);
                ((Provisioning)getActivity()).showInputInfo(msg);
            }
            else if (result == 422) {
                String msg = getString(R.string.provisioning_credentials);
                Log.w(TAG, "Credentials wrong: " + result);
                ((Provisioning)getActivity()).showInputInfo(msg);
            }
            else if (result == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                String msg = getString(R.string.provisioning_already_registered);
                Log.w(TAG, "Device already registered: " + result);
                ((Provisioning)getActivity()).showInputInfo(msg);
            }
            else {
                String msg = getString(R.string.provisioning_error);
                msg += getString(R.string.error_code) + result;
                Log.w(TAG, "Unknown error: " + result);
                ((Provisioning)getActivity()).showInputInfo(msg);
            }
            cleanUp();
        }

        private void cleanUp() {
            progress.setVisibility(View.INVISIBLE);
            inputFields.setVisibility(View.VISIBLE);
            buttons.setVisibility(View.VISIBLE);
        }
    }

}
