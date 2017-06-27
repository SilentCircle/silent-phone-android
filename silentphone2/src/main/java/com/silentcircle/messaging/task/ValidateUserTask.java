/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.task;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import com.silentcircle.logs.Log;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class ValidateUserTask extends AsyncTask<String, Void, ValidationState> {

    public static final String RESPONSE_TAG_RESULT = "result";
    public static final String RESPONSE_VALUE_ERROR = "error";

    private static final String TAG = ValidateUserTask.class.getSimpleName();

    private final Context mContext;

    public ValidateUserTask(final Context context) {
        mContext = context;
    }

    @Override
    protected ValidationState doInBackground(String... params) {
        ValidationState result = ValidationState.VALIDATION_ERROR;

        String userName = params[0];
        if (TextUtils.isEmpty(userName)) {
            return ValidationState.USERNAME_EMPTY;
        }

        /* check presence of internet */
        if (!isOnline(mContext)) {
            Log.e(TAG, "No internet connection. Cannot validate user name.");
            return ValidationState.NETWORK_NOT_AVAILABLE;
        }


        try {
            HttpsURLConnection urlConnection = executeRequest(userName);
            int responseCode = urlConnection.getResponseCode();
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + responseCode);

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                /*
                 * Parse obtained json.
                 * Assume error, if response is empty.
                 * Check for error response JSON if response is not empty.
                 *
                 * Return true (user exists) if response is not an error.
                 * TODO: check for inbound messaging permission?
                 */
                String requestResult = getRequestResult(urlConnection.getInputStream());
                if (!(TextUtils.isEmpty(requestResult) || isErrorResponse(requestResult))) {
                    result = ValidationState.USERNAME_VALID;
                }
            }
        } catch (IOException exception) {
            // on request errors will return generic VALIDATION_ERROR
        }
        return result;
    }

    private boolean isOnline(final Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private HttpsURLConnection executeRequest(final String userName) throws IOException {

        /*
         * To determine validity of a user use API call
         * https://sccps.silentcircle.com/v1/user/<user name>/?api_key
         *
         */

        URL accountInfoURL = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mContext) +
                ConfigurationUtilities.getUserManagementBaseV1User(mContext) +
                Uri.encode(userName) + "/" + "?api_key=" + getApiKey());

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Executing " + accountInfoURL);

        HttpsURLConnection urlConnection = (HttpsURLConnection) accountInfoURL.openConnection();
        SSLContext context = PinnedCertificateHandling.getPinnedSslContext(
                ConfigurationUtilities.mNetworkConfiguration);
        if (context != null) {
            urlConnection.setSSLSocketFactory(context.getSocketFactory());
        }
        else {
            Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
            throw new AssertionError("Failed to get pinned SSL context");
        }
        urlConnection.setRequestMethod("GET");
        urlConnection.setDoInput(true);
        urlConnection.setRequestProperty("Content-Type", "application/json");
        urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

        return urlConnection;
    }

    private String getApiKey() {
        String apiKey = null;
        byte[] apiKeyData = KeyManagerSupport.getSharedKeyData(mContext.getContentResolver(),
                ConfigurationUtilities.getShardAuthTag());
        if (apiKeyData != null) {
            try {
                apiKey = new String(apiKeyData, "UTF-8").trim();
            } catch (UnsupportedEncodingException ignored) {
                // Will return null in case of failure
            }
        }
        return apiKey;
    }

    private String getRequestResult(final InputStream is) {
        final char[] buffer = new char[1024 * 4];
        final StringBuilder out = new StringBuilder();
        try {
            Reader in = new InputStreamReader(is, "UTF-8");
            for (;;) {
                int read = in.read(buffer, 0, buffer.length);
                if (read < 0) {
                    break;
                }
                out.append(buffer, 0, read);
            }
        }
        catch (IOException exception) {
            // Ignore. Turning empty string to JSON will be handled.
        }

        if (ConfigurationUtilities.mTrace) Log.d(TAG, out.toString());

        return out.toString();
    }

    private boolean isErrorResponse(final String response) {
        boolean result = false;
        try {
            JSONObject json = new JSONObject(response);
            if (json.has(RESPONSE_TAG_RESULT)) {
                if (RESPONSE_VALUE_ERROR.equals(json.getString(RESPONSE_TAG_RESULT))) {
                    result = true;
                }
            }
        } catch (JSONException exception) {
            // HTML response can be received, usually request on errors
            result = true;
        }
        return result;
    }
}


