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

package com.silentcircle.userinfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * This class loads user and account information from provisioning server.
 *
 * Created by werner on 18.04.14.
 */
public class LoadUserInfo {
    @SuppressWarnings("unused")
    private static final String TAG = LoadUserInfo.class.getSimpleName();
    private static final int MIN_CONTENT_LENGTH = 10;

    public static final int UNKNOWN = -1;
    public static final int VALID = 1;
    public static final int INVALID = 0;

    public static final int DEFAULT_LOW_MINUTES_THRESHHOLD = 5;
    public static final double DEFAULT_LOW_CREDIT_THRESHHOLD = 5.0;

    private static final String DISPLAY_TN = "display_tn";
    private static final String DISPLAY_ALIAS = "display_alias";
    private static final String DISPLAY_NAME = "display_name";
    private static final String USER_ID = "user_id";
    private static final String AVATAR_URL = "avatar_url";

    private static final String SUBS_EXPIRATION_DATE = "SUBS_EXPIRATION_DATE";
    private static final String SUBS_STATE = "SUBS_STATE";
    private static final String SUBS_MODEL = "SUBS_MODEL";

    private static final String BASE_MINUTES = "BASE_MINUTES";
    private static final String REMAINING_MINUTES = "REMAINING_MINUTES";

    private static final String REMAINING_CREDIT = "REMAINING_USER_CREDIT";

    private static final String OUTBOUND_PERMISSION = "OUT_BOUND_PERMISSION";
    private static final String OUTBOUND_OCA_PERMISSION = "OUTBOUND_OCA_PERMISSION";
    private static final String CONFERENCE_PERMISSION = "CONFERENCE_PERMISSION";
    private static final String SEND_ATTACHMENT_PERMISSION = "SEND_ATTACHMENT_PERMISSION";
    private static final String INITIATE_VIDEO_PERMISSION = "INITIATE_VIDEO_PERMISSION";
    private static final String AVAILABLE_PRE_KEYS = "AVAILABLE_PRE_KEYS";

    public static final String URL_AVATAR_NOT_SET = "null";

    /*
        2100-01-01T00:00:00Z    4102444800000   used for enterprise users
        2100-12-31T00:00:00Z    4133894400000   used for regular users
     */
    public static final Date VALID_FOR_LIFETIME = new Date(4102444800000L); // use sooner date

    private static final Collection<Listener> userInfoListeners = new LinkedList<>();

    private URL mRequestUrl;

    private StringBuilder mContent = new StringBuilder();

    private final Context mContext;

    // When to perform the user info load/check
//    private long mNextCheck;
    private boolean mLoaderActive;

    // Should the user see anything?
    private final boolean mSilent;

    private static String sExpirationString;
    private static String sExpirationStringLocal;
    private static Date sExpirationDate;

    private static String sState;
    private static String sModel;

    private static String sRemainingCredit;

    private static String sBaseMinutes;
    private static String sRemainingMinutes;

    // url to retrieve avatar image from web for current user
    private static String sAvatarUrl;

    private static String sDisplayTn;
    private static String sDisplayAlias;
    private static String sDisplayName;
    private static String sUuid;

    private static int sAvailablePreKeys;

    /**
     * Listener interface to report if data loading is complete.
     */
    public interface Listener {
        /**
         *
         * @param userInfo A {@link UserInfo} object
         * @param errorInfo   If not {@code null} then an error occurred, minutes data invalid,
         *                    this shows the error reason
         * @param silent      Should the user see anything?
         */
        void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent);
    }

    public void addUserInfoListener(final Listener l) {
        synchronized (userInfoListeners) {
            if (userInfoListeners.contains(l)) {
                return;
            }

            userInfoListeners.add(l);
        }
    }

    public void removeUserInfoListener(final Listener l) {
        synchronized (userInfoListeners) {
            userInfoListeners.remove(l);
        }
    }

    private void callUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        synchronized (userInfoListeners) {
            for (Listener l : userInfoListeners)
                l.onUserInfo(userInfo, errorInfo, silent);
        }
    }

    public LoadUserInfo(Context context, boolean silent) {
        mContext = context;
        mSilent = silent;

        byte[] data = KeyManagerSupport.getSharedKeyData(mContext.getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (data == null) {
            Log.w(TAG, "No API key data available");
            return;
        }
        try {
            String devAuthorization = new String(data, "UTF-8");
            String resourceSec = ConfigurationUtilities.getProvisioningBaseUrl(mContext) +
                    ConfigurationUtilities.getUserManagementBaseV1Me(mContext);
            mRequestUrl = new URL(resourceSec + "?api_key="+devAuthorization);
        } catch (UnsupportedEncodingException ignore) {
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        loadPreferences(context);
    }

    /**
     * Load user account info if necessary or if check interval expired.
     */
    public synchronized void refreshUserInfo() {
        if (mLoaderActive || mRequestUrl == null)
            return;

        mLoaderActive = true;
        LoaderTask mLoaderTask = new LoaderTask();
        mLoaderTask.execute();
    }

    public static synchronized void loadPreferences(Context ctx) {
        SharedPreferences prefs =  ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        sExpirationString = prefs.getString(SUBS_EXPIRATION_DATE, null);
        sState = prefs.getString(SUBS_STATE, null);
        sModel = prefs.getString(SUBS_MODEL, null);
        sBaseMinutes = prefs.getString(BASE_MINUTES, null);
        sRemainingMinutes = prefs.getString(REMAINING_MINUTES, null);
        sRemainingCredit = prefs.getString(REMAINING_CREDIT, null);
        sDisplayTn = prefs.getString(DISPLAY_TN, null);
        sDisplayAlias = prefs.getString(DISPLAY_ALIAS, null);
        sDisplayName = prefs.getString(DISPLAY_NAME, null);
        sAvatarUrl = prefs.getString(AVATAR_URL, null);
        sUuid = prefs.getString(USER_ID, "");
        sAvailablePreKeys = prefs.getInt(AVAILABLE_PRE_KEYS, -1);   // -1 : Never stored this value before. >=0 are a valid values

        if (sExpirationDate == null) {
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
        return sExpirationDate;
    }

    /**
     * Get localized expiration date if available.
     *
     * @return Localized expiration date or the ISO formatted expiration date string
     */
    public static String getExpirationDateString() {
        return (sExpirationStringLocal == null) ? sExpirationString : sExpirationStringLocal;
    }

    public void loadOcaMinutesInfo() {
        if (mLoaderActive || mRequestUrl == null)
            return;

        mLoaderActive = true;
        LoaderTask mLoaderTask = new LoaderTask();
        mLoaderTask.execute();
    }

    public static int checkIfExpired() {
        if (TextUtils.isEmpty(sState))
            return UNKNOWN;

        return sState.equals("expired") ? VALID : INVALID;
    }

    public static int checkIfUsesMinutes() {
        if (TextUtils.isEmpty(sModel))
            return UNKNOWN;

        return sModel.equals("plan") ? VALID : INVALID;
    }

    public static int checkIfUsesCredit() {
        if (TextUtils.isEmpty(sModel))
            return UNKNOWN;

        return sModel.equals("credit") ? VALID : INVALID;
    }

    public static int checkIfLowMinutes(int threshhold) {
        if (TextUtils.isEmpty(sRemainingMinutes)) {
            return UNKNOWN;
        }

        return Integer.valueOf(sRemainingMinutes) <= threshhold ? VALID : INVALID;
    }

    public static int checkIfLowCredit(double threshhold) {
        if (TextUtils.isEmpty(sRemainingCredit)) {
            return UNKNOWN;
        }

        return Double.valueOf(sRemainingCredit) <= threshhold ? VALID : INVALID;
    }

    // This is an odd state - it exists but is it used?
    public static int checkIfFree() {
        if (TextUtils.isEmpty(sState))
            return UNKNOWN;

        return sState.equals("free") ? VALID : INVALID;
    }

    @SuppressLint("SimpleDateFormat")
    public static boolean checkExpirationDateValid(String expirationString) {
        if (!TextUtils.isEmpty(expirationString)) {
            try {
                // any expiration dates before 2000 are presumed invalid
                // the server appears to use 1-1-1900 for premium accounts, but checking prior to 2000
                // also covers any other bogus/default dates such as 1-1-1970 that might be in the system
                Date expirationDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(sExpirationString);
                Calendar noSubscriptionDate = Calendar.getInstance();
                noSubscriptionDate.set(2000, 1, 1);
                Calendar expiry = Calendar.getInstance();
                expiry.setTime(expirationDate);
                return expiry.after(noSubscriptionDate);
            } catch (ParseException e) {
                Log.e(TAG, "Date parsing failed: " + e.getMessage());
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean canCallOutbound(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        return prefs.getBoolean(OUTBOUND_PERMISSION, false);
    }

    public static boolean canCallOca(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        return prefs.getBoolean(OUTBOUND_OCA_PERMISSION, false);
    }

    public static boolean canStartConference(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        return prefs.getBoolean(CONFERENCE_PERMISSION, false);
    }

    public static boolean canSendAttachments(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        return prefs.getBoolean(SEND_ATTACHMENT_PERMISSION, false);
    }

    public static boolean canInitiateVideo(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        return prefs.getBoolean(INITIATE_VIDEO_PERMISSION, false);
    }

    public static String getAvatarUrl() {
        return sAvatarUrl;
    }

    public static String getDisplayTn() {
        return sDisplayTn;
    }

    public static String getDisplayAlias() {
        return sDisplayAlias;
    }

    public static String getDisplayName() {
        return sDisplayName;
    }

    public static String getUuid() {
        return sUuid;
    }

    @SuppressLint("SimpleDateFormat")           // date/time is a computer generated string
    private static void setExpirationDate() {
        if (!TextUtils.isEmpty(sExpirationString)) {
            try {
                sExpirationDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(sExpirationString);
            } catch (ParseException e) {
                Log.e(TAG, "Date parsing failed: " + e.getMessage());
                return;
            }
            DateFormat df = DateFormat.getDateInstance();
            sExpirationStringLocal = df.format(sExpirationDate);
        }
    }

    private void parseUserInfoData() {
        /*
         * OK, here we got a positive answer, parse the user data we got.
         */
        if (mContent.length() < MIN_CONTENT_LENGTH)
            return;

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(DeviceInfoMe.class, new UserInfo.DevicesDeserializer(mContext));
        Gson gson = gsonBuilder.create();
        UserInfo info = gson.fromJson(mContent.toString(), UserInfo.class);

        sExpirationString = info.getSubscription().getExpires();

        sState = info.getSubscription().getState();
        sModel = info.getSubscription().getModel();

        sRemainingMinutes = String.valueOf(info.getSubscription().getUsageDetails().getMinutesLeft());
        sBaseMinutes = String.valueOf(info.getSubscription().getUsageDetails().getBaseMinutes());

        sRemainingCredit = String.valueOf(info.getSubscription().getBalance().getAmount());

        sUuid = info.getUuid();
        sDisplayTn = info.getDisplayTn();
        sDisplayAlias = info.getDisplayAlias();
        sDisplayName = info.getDisplayName();

        sAvatarUrl = info.getAvatarUrl();
        if (URL_AVATAR_NOT_SET.equals(sAvatarUrl)) {
            sAvatarUrl = null;
        }

        DeviceInfoMe deviceInfo = info.getDevices();
        sAvailablePreKeys = (deviceInfo != null) ? deviceInfo.getPreKeys() : -2;

        callUserInfo(info, null, mSilent);

        checkPreKeys(mContext);

        SharedPreferences prefs =  mContext.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString(SUBS_EXPIRATION_DATE, sExpirationString)
            .putString(SUBS_STATE, sState)
            .putString(SUBS_MODEL, sModel)
            .putString(BASE_MINUTES, sBaseMinutes)
            .putString(REMAINING_MINUTES, sRemainingMinutes)
            .putString(REMAINING_CREDIT, sRemainingCredit)
            .putString(DISPLAY_TN, sDisplayTn)
            .putString(DISPLAY_ALIAS, sDisplayAlias)
            .putString(DISPLAY_NAME, sDisplayName)
            .putString(AVATAR_URL, sAvatarUrl)
            .putString(USER_ID, sUuid)
            .putBoolean(OUTBOUND_PERMISSION, info.getPermissions().isOutboundCalling())
            .putBoolean(OUTBOUND_OCA_PERMISSION, info.getPermissions().isOutboundCallingPstn())
            .putBoolean(CONFERENCE_PERMISSION, info.getPermissions().isCreateConference())
            .putBoolean(SEND_ATTACHMENT_PERMISSION, info.getPermissions().isSendAttachment())
            .putBoolean(INITIATE_VIDEO_PERMISSION, info.getPermissions().isInitiateVideo())
            .putInt(AVAILABLE_PRE_KEYS, sAvailablePreKeys)
            .apply();

        BurnDelay.Defaults.setMaxDelay(info.getPermissions().getMaximumBurnSec());
        setExpirationDate();
    }

    private static void checkPreKeys(Context ctx) {
        if (!AxoMessaging.getInstance(ctx).isReady())
            return;
        if (sAvailablePreKeys > 0 && sAvailablePreKeys < AxoMessaging.MIN_NUM_PRE_KEYS) {
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    AxoMessaging.newPreKeys(AxoMessaging.CREATE_NEW_PRE_KEYS);
                }
            });
        }
    }

    private class LoaderTask extends AsyncTask<Void, Integer, Integer> {
        private HttpsURLConnection urlConnection = null;

        @Override
        protected Integer doInBackground(Void... params) {
            long startTime = System.currentTimeMillis();
            try {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "User info URL: " + mRequestUrl);
                urlConnection = (HttpsURLConnection)mRequestUrl.openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                urlConnection.setConnectTimeout(2000);

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

                if (ret == HttpsURLConnection.HTTP_OK) {
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
                }
                return ret;

            } catch (IOException e) {
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } finally {
                urlConnection.disconnect();
                if (ConfigurationUtilities.mTrace)
                    Log.d(TAG, String.format("Loading user info took %d ms", System.currentTimeMillis() - startTime));
            }
        }

        protected void onProgressUpdate(Integer... progress) { }

        @Override
        protected void onCancelled(Integer result) {
            cleanUp();
        }

        @Override
        protected void onPostExecute(Integer result) {
            handleSubscription(result);

            mLoaderActive = false;
        }

        private void handleSubscription(int result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                parseUserInfoData();
            }
            else {
                Log.w(TAG, "Reading OCA minutes info failed, code: " + result);

                callUserInfo(null, mContext.getString(R.string.remaining_oca_minutes_fail), mSilent);
            }
        }

        private void cleanUp() {
        }
    }
}
