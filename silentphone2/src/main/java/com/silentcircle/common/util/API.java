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
package com.silentcircle.common.util;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;

import java.io.UnsupportedEncodingException;

import javax.net.ssl.SSLContext;

// TODO: WIP
/**
 * Created by Sam on 4/29/2016.
 */
public class API {
    private static final String TAG = API.class.getSimpleName();

    public static final String HOSTNAME_PRODUCTION = "sccps.silentcircle.com";
    public static final String HOSTNAME_DEVELOP = "sccps-dev.silentcircle.com";
    public static final String HOSTNAME_QA = "sccps-qa.silentcircle.com";

    public static final int DEFAULT_TIMEOUT_MS = 7000;

    static class Path {
        static String V1 = "v1";
        static String V2 = "v2";

        static String ME = "me";
        static String USER = "user";
        static String PEOPLE = "people";

        static String DEVICE = "device";
    }

    static class Query {
        static String API_KEY = "api_key";
    }

    static class ApiKeyInvalidException extends Exception {}

    public interface Callback {
        void onComplete(HttpUtil.HttpResponse httpResponse, Exception exception);
    }

    public static class V1 {
        public static class Me {
            public static void get(Context context, Callback callback) {
                try {
                    String apiKey = getApiKey(context);

                    String url = buildUrl()
                            .appendPath(Path.V1)
                            .appendPath(Path.ME)
                            .appendEncodedPath("/") // Important so we do not hit a 301
                            .appendQueryParameter(Query.API_KEY, apiKey).toString();

                    API.get(url, callback);
                } catch (Exception exception) {
                    callback.onComplete(null, exception);
                }
            }

            public static class Device {
                public static void delete(Context context, String deviceId, Callback callback) {
                    try {
                        String apiKey = getApiKey(context);

                        String url = buildUrl()
                                .appendPath(Path.V1)
                                .appendPath(Path.ME)
                                .appendPath(Path.DEVICE)
                                .appendPath(deviceId)
                                .appendEncodedPath("/")
                                .appendQueryParameter(Query.API_KEY, apiKey).toString();

                        API.delete(url, callback);
                    } catch (Exception exception) {
                        callback.onComplete(null, exception);
                    }
                }
            }
        }
    }

    static String getHostname() {
        if (ConfigurationUtilities.mUseDevelopConfiguration) {
            return HOSTNAME_DEVELOP;
        } else {
            return HOSTNAME_PRODUCTION;
        }
    }

    static SSLContext getSslContext() {
        return PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
    }

    static String getApiKey(Context context) throws ApiKeyInvalidException {
        byte[] apiKeyData = KeyManagerSupport.getSharedKeyData(context.getContentResolver(), ConfigurationUtilities.getShardAuthTag());

        if (apiKeyData == null) {
            throw new ApiKeyInvalidException();
        }

        try {
            return new String(apiKeyData, "UTF-8").trim();
        } catch (UnsupportedEncodingException exception) {
            throw new ApiKeyInvalidException();
        }
    }

    static Uri.Builder buildUrl() {
        Uri.Builder builder = new Uri.Builder();

        return builder.scheme("https").authority(getHostname());
    }

    private static void get(String requestUrl, Callback callback) {
        request(requestUrl, HttpUtil.RequestMethod.GET, callback);
    }

    private static void delete(String requestUrl, Callback callback) {
        request(requestUrl, HttpUtil.RequestMethod.DELETE, callback);
    }

    private static void request(final String requestUrl, final HttpUtil.RequestMethod requestMethod, final Callback callback) {
        AsyncUtils.execute(new AsyncTask<Void, Void, Void>() {
            HttpUtil.HttpResponse mHttpResponse = null;
            Exception mException = null;

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    SSLContext sslContext = getSslContext();

                    if (sslContext == null) {
                        throw new SecurityException("Unable to get a valid SSL context");
                    }

                    HttpUtil.HttpResponse httpResponse = null;
                    switch (requestMethod) {
                        case GET:
                            httpResponse = HttpUtil.get(requestUrl, sslContext, null, DEFAULT_TIMEOUT_MS);
                            break;
                        case DELETE:
                            httpResponse = HttpUtil.delete(requestUrl, null, sslContext, null, DEFAULT_TIMEOUT_MS);
                            break;
                        default:
                            throw new NoSuchMethodException();
                    }

                    mHttpResponse = httpResponse;
                } catch (Exception exception) {
                    mException = exception;
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                callback.onComplete(mHttpResponse, mException);
            }
        });
    }
}
