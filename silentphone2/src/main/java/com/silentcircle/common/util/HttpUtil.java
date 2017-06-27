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

import android.webkit.URLUtil;

import com.google.common.net.HttpHeaders;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.util.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

// TODO: WIP
/**
 * Created by Sam on 4/29/2016.
 */
public class HttpUtil {
    private static final String TAG = HttpUtil.class.getSimpleName();

    public static final int DEFAULT_TIMEOUT_MS = 5000;

    static final Map<String, String> DEFAULT_HEADER_MAP = new HashMap<String , String>() {{
        put(HttpHeaders.ACCEPT_LANGUAGE, Locale.getDefault().getLanguage());
    }};

    public enum RequestMethod {
        GET, POST, PUT, DELETE, PATCH, OPTIONS, TRACE, HEAD;
    }

    public static class HttpResponse {
        public int responseCode;
        public StringBuilder response;
        public StringBuilder error;

        public HttpResponse(int responseCode) {
            this.responseCode = responseCode;
            this.response = null;
            this.error = null;
        }

        public boolean hasError() {
            return this.error == null;
        }
    }

    public static HttpResponse get(String requestUrl, SSLContext sslContext, Map<String, String> headerMap, int timeoutMs) throws IOException {
        return request(requestUrl, RequestMethod.GET, sslContext, headerMap, null, timeoutMs, null);
    }

    public static HttpResponse get(String requestUrl, SSLContext sslContext, Map<String, String> headerMap, int timeoutMs, OutputStream os) throws IOException {
        return request(requestUrl, RequestMethod.GET, sslContext, headerMap, null, timeoutMs, os);
    }

    public static HttpResponse post(String requestUrl, Map<String, Object> paramMap, SSLContext sslContext, Map<String, String> headerMap, int timeoutMs) throws IOException {
        return request(requestUrl, RequestMethod.POST, sslContext, headerMap, paramMap, timeoutMs, null);
    }

    public static HttpResponse put(String requestUrl, Map<String, Object> paramMap, SSLContext sslContext, Map<String, String> headerMap, int timeoutMs) throws IOException {
        return request(requestUrl, RequestMethod.PUT, sslContext, headerMap, paramMap, timeoutMs, null);
    }

    public static HttpResponse delete(String requestUrl, Map<String, Object> paramMap, SSLContext sslContext, Map<String, String> headerMap, int timeoutMs) throws IOException {
        return request(requestUrl, RequestMethod.DELETE, sslContext, headerMap, paramMap, timeoutMs, null);
    }

    public static HttpResponse request(String requestUrl, RequestMethod requestMethod, SSLContext sslContext, Map<String, String> headerMap, Map<String, Object> paramMap, int timeoutMs, OutputStream os) throws IOException {
        if (!URLUtil.isNetworkUrl(requestUrl)) {
            return null;
        }

        URL url;
        try {
            url = new URL(requestUrl);
        } catch (MalformedURLException exception) {
            return null;
        }

        HttpURLConnection con = null;
        InputStream is = null;
        OutputStreamWriter writer = null;
        try {
            con = (HttpURLConnection) url.openConnection();
            if (con instanceof HttpsURLConnection && sslContext != null) {
                ((HttpsURLConnection) con).setSSLSocketFactory(sslContext.getSocketFactory());
            }

            con.setRequestMethod(requestMethod.toString());
            if (timeoutMs < 0) {
                timeoutMs = DEFAULT_TIMEOUT_MS;
            }
            con.setConnectTimeout(timeoutMs);
            con.setReadTimeout(timeoutMs);

            for (Map.Entry<String, String> entry : DEFAULT_HEADER_MAP.entrySet()) {
                con.addRequestProperty(entry.getKey(), entry.getValue());
            }

            if (headerMap != null) {
                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    con.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (paramMap != null) {
                con.setDoInput(true);

                StringBuffer requestParams = new StringBuffer();
                for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                    requestParams.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    requestParams.append("=").append(
                            URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
                    requestParams.append("&");
                }

                writer = new OutputStreamWriter(con.getOutputStream());
                writer.write(requestParams.toString());
                writer.flush();
            }

            int ret = con.getResponseCode();
            HttpResponse httpResponse = new HttpResponse(ret);

            if (ret == HttpsURLConnection.HTTP_OK) {
                is = con.getInputStream();

                if (os == null) {
                    httpResponse.response = new StringBuilder();
                    AsyncTasks.readStream(new BufferedInputStream(is), httpResponse.response);
                } else {
                    IOUtils.pipe(is, os);
                }
            } else {
                is = con.getErrorStream();

                httpResponse.error = new StringBuilder();
                AsyncTasks.readStream(new BufferedInputStream(is), httpResponse.error);
            }

//            Log.d(TAG, "request - %s url: %s, response: %d", requestMethod.toString(), requestUrl, httpResponse.responseCode);
            Log.d(TAG, "request - "+requestMethod.toString()+", url: "+requestUrl+", response: "+httpResponse.responseCode);

            return httpResponse;
        } catch (IOException exception) {
//            Log.d(TAG, "request error - %s url: %s, exception - %s", requestMethod.toString(), requestUrl, exception.getClass().getSimpleName());
            Log.d(TAG, "request error - "+requestMethod.toString()+", url: "+requestUrl+", exception - "+ exception.getClass().getSimpleName());
            throw exception;
        } finally {
            if (con != null) {
                con.disconnect();
            }

            IOUtils.close(is, os, writer);
        }
    }
}