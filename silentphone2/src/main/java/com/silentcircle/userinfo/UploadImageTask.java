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
package com.silentcircle.userinfo;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Async task to upload an avatar image to server or delete it.
 */
public class UploadImageTask extends AsyncTask<String, Void, Integer> {

    private static final String TAG = UploadImageTask.class.getSimpleName();

    public static final int REQUEST_TIMEOUT = 2 * 1000;

    private final Context mContext;
    private String mBaseUrl;

    public UploadImageTask(Context context) {
        mContext = context;
        mBaseUrl = ConfigurationUtilities.getProvisioningBaseUrl(context);
    }

    // /v1/me/avatar/?api_key={api_key}
    protected Integer doInBackground(String... params) {
        String image = null;
        if (params != null && params.length > 0) {
            image = params[0];
        }

        byte[] data = KeyManagerSupport.getSharedKeyData(mContext.getContentResolver(),
                ConfigurationUtilities.getShardAuthTag());
        if (data == null) {
            Log.w(TAG, "No API key data available");
            return -1;
        }
        String devAuthorization = null;
        try {
            devAuthorization = new String(data, "UTF-8");
        }  catch (UnsupportedEncodingException ignore) {
        }

        HttpsURLConnection urlConnection = null;
        try {
            URL url = new URL(mBaseUrl
                    + ConfigurationUtilities.getAvatarAction(mContext)
                    + "?api_key=" + devAuthorization);
            Log.d(TAG, "Avatar action URL: " + url);

            urlConnection = (HttpsURLConnection) url.openConnection();
            SSLContext context = PinnedCertificateHandling.getPinnedSslContext(
                    ConfigurationUtilities.mNetworkConfiguration);
            if (context != null) {
                urlConnection.setSSLSocketFactory(context.getSocketFactory());
            }
            else {
                Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                throw new AssertionError("Failed to get pinned SSL context");
            }

            urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
            urlConnection.setConnectTimeout(REQUEST_TIMEOUT);
            if (!TextUtils.isEmpty(image)) {
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);

                OutputStream os = urlConnection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write("{\"image\":\"" + image + "\"}");
                writer.flush();
                writer.close();
                os.close();
            } else {
                urlConnection.setRequestMethod("DELETE");
            }

            urlConnection.connect();

            int ret = urlConnection.getResponseCode();
            Log.d(TAG, "HTTP code: " + ret);

            // here we receive updated user information (avatar url is th updated part)
            // but we will also receive sip notify which will trigger loaduserinfo reload
            // so we don't update user data here
            /*
            if (ret == HttpsURLConnection.HTTP_OK) {
                StringBuilder content = new StringBuilder();
                AsyncTasks.readStream(new BufferedInputStream(urlConnection.getInputStream()),
                    content);
            }
             */

            return ret;
        } catch (IOException e) {
            Log.e(TAG, "Network not available: " + e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return -1;
    }
}

