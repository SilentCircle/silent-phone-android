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

package com.silentcircle.silentphone2.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * This class loads user and account information from provisioning server.
 *
 * Created by werner on 18.04.14.
 */
public class LoadUserInfo {

    @SuppressWarnings("unused")
    private static final String TAG = LoadUserInfo.class.getSimpleName();
    private static final int MIN_CONTENT_LENGTH = 10;

    private static final int PARSE_SUBSCRIPTION_INFO = 0;
    private static final int PARSE_OCA_MINUTE_INFO = 1;

    public static final int UNKNOWN = -1;
    public static final int VALID = 1;
    public static final int INVALID = 0;


    @SuppressWarnings("FieldCanBeLocal")
    private final long CHECK_AFTER = 86400 * 3 * 1000;      // 3 days in ms
    @SuppressWarnings("FieldCanBeLocal")
    private final long CHECK_AFTER_FAIL = 30*60*1000;       // 30 min in ms

    private URL mRequestUrl;

    private StringBuilder mContent = new StringBuilder();

    private Context mContext;

    // When to perform the user info load/check
    private long mNextCheck;
    private boolean mLoaderActive;

    private Listener mListener;

    private static String mExpirationString;
    private static String mExpirationStringLocal;
    private static Date mExpirationDate;

    private static boolean mHasOrganization;

    private static boolean mFirstCallAfterStart = true;

    /**
     * Listener interface to report if data loading is complete.
     */
    public interface Listener {
        /**
         *
         * @param minutesLeft How many minutes left for OCA calls
         * @param baseMinutes Number of minutes available per month
         * @param errorInfo   If not {@code null} then an error occurred, minutes data invalid,
         *                    this shows the error reason
         */
        void ocaMinutes(int minutesLeft, int baseMinutes, String errorInfo);
    }

    public LoadUserInfo(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
        String userName = TiviPhoneService.getInfo(0, -1, "cfg.un");
        if (TextUtils.isEmpty(userName)) {
            Log.w(TAG, "No username available");
            return;
        }
        byte[] data = KeyManagerSupport.getSharedKeyData(mContext.getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (data == null) {
            Log.w(TAG, "No API key data available");
            return;
        }
        try {
            String devAuthorization = new String(data, "UTF-8");
            String resourceSec = ConfigurationUtilities.getProvisioningBaseUrl(mContext) +
                    ConfigurationUtilities.getUserManagementBaseAlt(mContext);
            mRequestUrl = new URL(resourceSec + "?api_key="+devAuthorization);
        } catch (UnsupportedEncodingException ignore) {
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load user account info if necessary or if check interval expired.
     */
    public void loadExpirationInfo() {
        if (mLoaderActive || mRequestUrl == null)
            return;
        long currentTime = System.currentTimeMillis();
        if (!mFirstCallAfterStart && !TextUtils.isEmpty(mExpirationString) && mNextCheck != 0 && currentTime < mNextCheck)
            return;

        SharedPreferences prefs =  mContext.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        mNextCheck = prefs.getLong("NEXT_USER_CHECK", 0);
        mExpirationString = prefs.getString("SUBS_EXPIRATION_DATE", null);

        if (mFirstCallAfterStart || mNextCheck == 0 || currentTime >= mNextCheck || TextUtils.isEmpty(mExpirationString)) {
            mLoaderActive = true;
            mFirstCallAfterStart = false;
            LoaderTask mLoaderTask = new LoaderTask(PARSE_SUBSCRIPTION_INFO);
            mLoaderTask.execute();
        }
        else if (mExpirationDate == null) {
            setExpirationDate();
        }
    }

    /**
     * Get the expiration date as Date.
     *
     * @return the expiration Date
     */
    @SuppressWarnings("unused")
    public static Date getExpirationDate() {
        return mExpirationDate;
    }

    /**
     * Get localized expiration date if available.
     *
     * @return Localized expiration date or the ISO formatted expiration date string
     */
    public static String getExpirationDateString() {
        return (mExpirationStringLocal == null) ? mExpirationString : mExpirationStringLocal;
    }

    public void loadOcaMinutesInfo() {
        if (mLoaderActive || mRequestUrl == null)
            return;

        mLoaderActive = true;
        LoaderTask mLoaderTask = new LoaderTask(PARSE_OCA_MINUTE_INFO);
        mLoaderTask.execute();
    }

    public static int checkIfExpired() {
        if (mExpirationDate == null)
            return UNKNOWN;

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());
        Calendar expiry = Calendar.getInstance();
        expiry.setTime(mExpirationDate);
        return expiry.after(now) ? VALID : INVALID;
    }

    public static boolean hasOrganization() {
        return mHasOrganization;
    }

    @SuppressLint("SimpleDateFormat")           // date/time is a computer generated string
    private void setExpirationDate() {
        if (!TextUtils.isEmpty(mExpirationString)) {
            try {
                mExpirationDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(mExpirationString);
            } catch (ParseException e) {
                Log.e(TAG, "Date parsing failed: " + e.getMessage());
                return;
            }
            DateFormat df = DateFormat.getDateInstance();
            mExpirationStringLocal = df.format(mExpirationDate);
        }
    }

    private void parseSubscriptionData() {
        /*
         * OK, here we got a positive answer, parse the user data we got.
         */
        if (mContent.length() < MIN_CONTENT_LENGTH)
            return;

        try {
            JSONObject jsonObj = new JSONObject(mContent.toString());
            JSONObject subscription = jsonObj.getJSONObject("subscription");
            mExpirationString = subscription.getString("expires");

            // Because we already have the data: check for some other data as well
            checkOrganisation(jsonObj);
        } catch (JSONException e) {
            // Check how to inform user and then restart?
            Log.w(TAG, "JSON exception: " + e);
            return;
        }
        long currentTime = System.currentTimeMillis();
        mNextCheck = currentTime + CHECK_AFTER;
        SharedPreferences prefs =  mContext.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("SUBS_EXPIRATION_DATE", mExpirationString)
                .putLong("NEXT_USER_CHECK", mNextCheck)
                .apply();
        setExpirationDate();
    }

    private void parseOcaMinuteData() {
        /*
         * OK, here we got a positive answer, parse the user data we got.
         */
        if (mContent.length() < MIN_CONTENT_LENGTH)
            return;

        try {
            final JSONObject jsonObj = new JSONObject(mContent.toString());
            final JSONObject subscription = jsonObj.getJSONObject("subscription");
            final JSONObject usageDetails = subscription.getJSONObject("usage_details");
            final int minutesLeft = usageDetails.getInt("minutes_left");
            final int baseMinutes = usageDetails.getInt("base_minutes");
            if (mListener != null)
                mListener.ocaMinutes(minutesLeft, baseMinutes, null);

            // Because we already have the data: check for some other data as well
            checkOrganisation(jsonObj);
        } catch (JSONException e) {
            if (mListener != null)
                mListener.ocaMinutes(-1, 0, mContext.getString(R.string.remaining_oca_minutes_fail));
            // Check how to inform user and then restart?
            Log.w(TAG, "JSON exception: " + e);
        }
    }

    private void checkOrganisation(final JSONObject jsonObj) {
        mHasOrganization = false;
        if (!jsonObj.has("organization")) {
            return;
        }
        try {
            String orgName = jsonObj.getString("organization");
            mHasOrganization = !TextUtils.isEmpty(orgName);
        } catch (JSONException ignore) {}
    }

    private class LoaderTask extends AsyncTask<Void, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;
        private int mParseInfo;

        LoaderTask(int parseInfo) {
            mParseInfo = parseInfo;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "User info URL: " + mRequestUrl);
                urlConnection = (HttpsURLConnection)mRequestUrl.openConnection();
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

                if (ret == HttpsURLConnection.HTTP_OK) {
                    ProvisioningActivity.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
                }
                return ret;

            } catch (IOException e) {
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } finally {
                urlConnection.disconnect();
            }
        }

        protected void onProgressUpdate(Integer... progress) { }

        @Override
        protected void onCancelled(Integer result) {
            cleanUp();
        }

        @Override
        protected void onPostExecute(Integer result) {
            switch (mParseInfo) {
                case PARSE_SUBSCRIPTION_INFO:
                    handleSubscription(result);
                    break;
                case PARSE_OCA_MINUTE_INFO:
                    handleOcaMinutes(result);
                    break;
            }
            mLoaderActive = false;
        }

        private void handleSubscription(int result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                parseSubscriptionData();
            }
            else {
                long currentTime = System.currentTimeMillis();
                mNextCheck = currentTime + CHECK_AFTER_FAIL;
                Log.w(TAG, "Reading expiration date failed, code: " + result +
                        ", next check in " + CHECK_AFTER_FAIL/10 + " seconds");
                SharedPreferences prefs =  mContext.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
                prefs.edit()
                        .putLong("NEXT_USER_CHECK", mNextCheck)
                        .apply();
            }
        }

        private void handleOcaMinutes(int result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                parseOcaMinuteData();
            }
            else {
                Log.w(TAG, "Reading OCA minutes info failed, code: " + result);
                if (mListener != null)
                    mListener.ocaMinutes(-1, 0, mContext.getString(R.string.remaining_oca_minutes_fail));
            }
        }

        private void cleanUp() {
        }
    }
}
