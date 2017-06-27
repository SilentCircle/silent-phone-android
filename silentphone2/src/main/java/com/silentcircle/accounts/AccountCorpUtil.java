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
package com.silentcircle.accounts;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Create and handle request to get SSO authorization JSON data.
 *
 * Created by anrp on 9/4/15.
 * Modified by Werner, 2016-02-12
 */
public class AccountCorpUtil {
    private static final String TAG = "AccountCorpUtil";

    public static JSONObject httpsGet(SSLContext sslc, String authUrl, int timeout) throws SocketTimeoutException {
        HttpsURLConnection hr = null;
        try {

            URL url = new URL(authUrl);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Making authorization (JSON) request to: " + url.toString());

            hr = (HttpsURLConnection) url.openConnection();

            hr.setSSLSocketFactory(sslc.getSocketFactory());
            hr.setRequestProperty("Content-Type", "application/json");
            hr.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
            hr.setRequestProperty("Connection","close");

            if (timeout > 0) {
                hr.setReadTimeout(timeout * 1000);
            }

            int ret = hr.getResponseCode();
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

            if (hr.getHeaderField("Location") != null) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP redirect to: " + hr.getHeaderField("Location"));
            }
            StringBuilder content = new StringBuilder();
            if (ret == HttpsURLConnection.HTTP_OK) {
                AsyncTasks.readStream(new BufferedInputStream(hr.getInputStream()), content);
            }
            else {
                AsyncTasks.readStream(new BufferedInputStream(hr.getErrorStream()), content);
            }
            JSONObject jsonObj = new JSONObject(content.toString());
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP request returned JSON: " + jsonObj.toString());

            return jsonObj;
        } catch(SocketTimeoutException e) {
            throw e;
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (hr != null)
                hr.disconnect();
        }
    }
}
