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

package com.silentcircle.userinfo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

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
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import zina.ZinaNative;

import static zina.JsonStrings.BLDR;
import static zina.JsonStrings.BLMR;
import static zina.JsonStrings.BRDR;
import static zina.JsonStrings.BRMR;
import static zina.JsonStrings.LRAP;
import static zina.JsonStrings.LRMM;
import static zina.JsonStrings.LRMP;

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

    public static final String USER_ID = "user_id";
    public static final String DISPLAY_ALIAS = "display_alias";
    public static final String DISPLAY_NAME = "display_name";
    private static final String DISPLAY_TN = "display_tn";
    private static final String DISPLAY_PLAN = "display_plan";
    private static final String DISPLAY_ORG = "display_org";
    private static final String AVATAR_URL = "avatar_url";

    private static final String SUBS_EXPIRATION_DATE = "SUBS_EXPIRATION_DATE";
    private static final String SUBS_STATE = "SUBS_STATE";
    private static final String SUBS_MODEL = "SUBS_MODEL";

    private static final String BASE_MINUTES = "BASE_MINUTES";
    private static final String REMAINING_MINUTES = "REMAINING_MINUTES";

    private static final String REMAINING_CREDIT = "REMAINING_USER_CREDIT";
    private static final String CREDIT_UNIT = "CREDIT_UNIT";

    private static final String OUTBOUND_CALLING_PERMISSION = "OUTBOUND_CALLING_PERMISSION";
    private static final String OUTBOUND_CALLING_OCA_PERMISSION = "OUTBOUND_CALLING_OCA_PERMISSION";
    private static final String OUTBOUND_MESSAGING_PERMISSION = "OUTBOUND_MESSAGING_PERMISSION";
    private static final String CONFERENCE_PERMISSION = "CONFERENCE_PERMISSION";
    private static final String SEND_ATTACHMENT_PERMISSION = "SEND_ATTACHMENT_PERMISSION";
    private static final String INITIATE_VIDEO_PERMISSION = "INITIATE_VIDEO_PERMISSION";
    private static final String AVAILABLE_PRE_KEYS = "AVAILABLE_PRE_KEYS";

    private static final String DATA_RETENTION_BLDR = "DATA_RETENTION_BLDR";
    private static final String DATA_RETENTION_BLMR = "DATA_RETENTION_BLMR";
    private static final String DATA_RETENTION_BRDR = "DATA_RETENTION_BRDR";
    private static final String DATA_RETENTION_BRMR = "DATA_RETENTION_BRMR";
    private static final String DATA_RETENTION_BLDRS = "DATA_RETENTION_BLDRS";
    private static final String DATA_RETENTION_BLMRS = "DATA_RETENTION_BLMRS";
    private static final String DATA_RETENTION_BRDRS = "DATA_RETENTION_BRDRS";
    private static final String DATA_RETENTION_BRMRS = "DATA_RETENTION_BRMRS";
    private static final String DATA_RETENTION_LRMM = "DATA_RETENTION_LRMM";
    private static final String DATA_RETENTION_LRMP = "DATA_RETENTION_LRMP";
    private static final String DATA_RETENTION_LRCM = "DATA_RETENTION_LRCM";
    private static final String DATA_RETENTION_LRCP = "DATA_RETENTION_LRCP";
    private static final String DATA_RETENTION_LRAP = "DATA_RETENTION_LRAP";
    private static final String DATA_RETENTION_ORG = "DATA_RETENTION_ORG";

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
    private static String sCreditUnit;

    private static String sBaseMinutes;
    private static String sRemainingMinutes;

    private static String sUuid;
    private static String sDisplayAlias;
    private static String sDisplayName;
    private static String sDisplayTn;
    private static String sDisplayPlan;
    private static String sDisplayOrg;

    // url to retrieve avatar image from web for current user
    private static String sAvatarUrl;

    private static boolean sBldr;           // block local data retention, set by UI
    private static boolean sBlmr;           // block local meta data retention, set by UI
    private static boolean sBrdr;           // block remote data retention, set by UI
    private static boolean sBrmr;           // block remote meta data retention, set by UI

    private static boolean sBldrs;           // block local data retention, set by server
    private static boolean sBlmrs;           // block local meta data retention, set by server
    private static boolean sBrdrs;           // block remote data retention, set by server
    private static boolean sBrmrs;           // block remote meta data retention, set by server

    private static boolean sLrmm;           // Local retains message meta data
    private static boolean sLrmp;           // Local retains message plaintext data
    private static boolean sLrcm;           // Local retains call meta data
    private static boolean sLrcp;           // Local retains call plaintext data
    private static boolean sLrap;           // Local retains attachment plaintext data

    private static String retentionOrg;     // Name of the organization which set the DR policy

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

    public LoadUserInfo(boolean silent) {
        mContext = SilentPhoneApplication.getAppContext();
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
        loadPreferences(mContext);
    }

    /**
     * Refresh user account info.
     *
     * The SIP NOTIFY with a "x-sc-refresh-provisioning" tag calls this function
     * to refresh the provisioned user data.
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
        sCreditUnit = prefs.getString(CREDIT_UNIT, null);
        sUuid = prefs.getString(USER_ID, "");
        sDisplayAlias = prefs.getString(DISPLAY_ALIAS, null);
        sDisplayName = prefs.getString(DISPLAY_NAME, null);
        sDisplayTn = prefs.getString(DISPLAY_TN, null);
        sDisplayPlan = prefs.getString(DISPLAY_PLAN, null);
        sDisplayOrg = prefs.getString(DISPLAY_ORG, null);
        sAvatarUrl = prefs.getString(AVATAR_URL, null);
        sAvailablePreKeys = prefs.getInt(AVAILABLE_PRE_KEYS, -1);   // -1 : Never stored this value before. >=0 are a valid values
        sBldr = prefs.getBoolean(DATA_RETENTION_BLDR, false);
        sBlmr = prefs.getBoolean(DATA_RETENTION_BLMR, false);
        sBrdr = prefs.getBoolean(DATA_RETENTION_BRDR, false);
        sBrmr = prefs.getBoolean(DATA_RETENTION_BRMR, false);
        sBldrs = prefs.getBoolean(DATA_RETENTION_BLDRS, false);
        sBlmrs = prefs.getBoolean(DATA_RETENTION_BLMRS, false);
        sBrdrs = prefs.getBoolean(DATA_RETENTION_BRDRS, false);
        sBrmrs = prefs.getBoolean(DATA_RETENTION_BRMRS, false);
        sLrmm = prefs.getBoolean(DATA_RETENTION_LRMM, false);
        sLrmp = prefs.getBoolean(DATA_RETENTION_LRMP, false);
        sLrcm = prefs.getBoolean(DATA_RETENTION_LRCM, false);
        sLrcp = prefs.getBoolean(DATA_RETENTION_LRCP, false);
        sLrap = prefs.getBoolean(DATA_RETENTION_LRAP, false);
        retentionOrg = prefs.getString(DATA_RETENTION_ORG, "");
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
        return prefs.getBoolean(OUTBOUND_CALLING_PERMISSION, false);
    }

    public static boolean canCallOutboundOca(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        return prefs.getBoolean(OUTBOUND_CALLING_OCA_PERMISSION, false);
    }

    public static boolean canMessageOutbound(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        return prefs.getBoolean(OUTBOUND_MESSAGING_PERMISSION, false);
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

    public static int getDenialStringResId() {
        if (LoadUserInfo.checkIfExpired() == LoadUserInfo.VALID) {
            return R.string.expired_account_info;
        } else if (LoadUserInfo.checkIfFree() == LoadUserInfo.VALID) {
            return R.string.basic_account_info_free;
        } else {
            return R.string.basic_account_info;
        }
    }

    public static String getUuid() {
        return sUuid;
    }

    public static String getDisplayAlias() {
        return sDisplayAlias;
    }

    public static String getDisplayName() {
        return sDisplayName;
    }

    public static String getDisplayTn() {
        return sDisplayTn;
    }

    public static String getDisplayPlan() {
        return sDisplayPlan;
    }

    public static String getBaseMinutes() {
        return sBaseMinutes;
    }

    public static String getRemainingMinutes() {
        return sRemainingMinutes;
    }

    public static String getRemainingCredit() {
        return sRemainingCredit;
    }

    public static String getCreditUnit() {
        return sCreditUnit;
    }

    public static String getDisplayOrg() {
        return sDisplayOrg;
    }

    public static String getAvatarUrl() {
        return sAvatarUrl;
    }

    public static String getRetentionOrganization() {
        return retentionOrg;
    }

    public static boolean isLrcm() {
//        return sLrcm;
        return false;
    }

    public static boolean isLrap() {
//        return sLrap;
        return false;
    }

    public static boolean isLrcp() {
//        return sLrcp;
        return false;
    }

    public static boolean isLrmp() {
//        return sLrmp;
        return false;
    }

    public static boolean isLrmm() {
//        return sLrmm;
        return false;
    }

    public static boolean isBrmr() {
//        return sBrmr;
        return false;
    }

    public static boolean isBrdr() {
//        return sBrdr;
        return false;
    }

    public static boolean isBlmr() {
//        return sBlmr;
        return false;
    }

    public static boolean isBldr() {
//        return sBldr;
        return false;
    }

    public static boolean isBrmrs() {
//        return sBrmrs;
        return false;
    }

    public static boolean isBrdrs() {
//        return sBrdrs;
        return false;
    }

    public static boolean isBlmrs() {
//        return sBlmrs;
        return false;
    }

    public static boolean isBldrs() {
//        return sBldrs;
        return false;
    }

    public static void setBrmr(boolean sBrmr) {
        LoadUserInfo.sBrmr = /**sBrmr**/false;
        safeLocalRetentionFlag(DATA_RETENTION_BRMR, /**sBrmr**/false);
    }

    public static void setBrdr(boolean sBrdr) {
        LoadUserInfo.sBrdr = /**sBrdr**/false;
        safeLocalRetentionFlag(DATA_RETENTION_BRDR, /**sBrdr**/false);
    }

    public static void setBlmr(boolean sBlmr) {
        LoadUserInfo.sBlmr = /**sBlmr**/false;
        safeLocalRetentionFlag(DATA_RETENTION_BLMR, /**sBlmr**/false);
    }

    public static void setBldr(boolean sBldr) {
        LoadUserInfo.sBldr = /**sBldr**/false;
        safeLocalRetentionFlag(DATA_RETENTION_BLDR, /**sBldr**/false);
    }

    /**
     * Return the local messaging retention flags as JSON string.
     *
     * The JSON string
     *<pre>
     * {
     * "lrmr": "true" | "false",
     * "lrmp": "true" | "false",
     * "lrap": "true" | "false",
     * "bldr": "true" | "false",
     * "blmr": "true" | "false",
     * "brdr": "true" | "false",
     * "brmr": "true" | "false"
     * }
     *</pre>
     * @return JSON formatted string
     */
    public static String getLocalRetentionFlags() {
        loadPreferences(SilentPhoneApplication.getAppContext());
        JSONObject flags = new JSONObject();
        try {
            flags.put(LRMM, isLrmm());
            flags.put(LRMP, isLrmp());
            flags.put(LRAP, isLrap());
            flags.put(BLDR, isBldr());
            flags.put(BLMR, isBlmr());
            flags.put(BRDR, isBrdr());
            flags.put(BRMR, isBrmr());
        } catch (JSONException ignore) { }
        return flags.toString();
    }

    private static void safeLocalRetentionFlag(String key, boolean value) {
        SharedPreferences prefs =  SilentPhoneApplication.getAppContext().getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(key, value).apply();
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

    private synchronized void parseUserInfoData() {
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
        sCreditUnit = info.getSubscription().getBalance().getUnit();

        sUuid = info.getUuid();
        sDisplayAlias = info.getDisplayAlias();
        sDisplayName = info.getDisplayName();
        sDisplayTn = info.getDisplayTn();
        sDisplayPlan = info.getDisplayPlan();
        sDisplayOrg = info.getDisplayOrg();

        sAvatarUrl = info.getAvatarUrl();
        if (URL_AVATAR_NOT_SET.equals(sAvatarUrl)) {
            sAvatarUrl = null;
        }

        sLrmm = false;
        sLrmp = false;
        sLrcm = false;
        sLrcp = false;
        sLrap = false;

        final UserInfo.DataRetention dataRetention = info.getDataRetention();
        if (dataRetention != null) {
            retentionOrg = dataRetention.getForOrgName();

            UserInfo.DataRetention.RetentionFlags drFlags = dataRetention.getRetentionFlags();
            if (drFlags != null) {
                sLrmm = drFlags.isMessageMetadata();
                sLrmp = drFlags.isMessagePlaintext();
                sLrcm = drFlags.isCallMetadata();
                sLrcp = drFlags.isCallPlaintext();
                sLrap = drFlags.isAttachmentPlaintext();
            }

            UserInfo.DataRetention.BlockRetentionFlags brFlags = dataRetention.getBlockRetentionFlags();
            if (brFlags != null) {
                sBldrs = brFlags.isLocalDataBlocked();
                sBlmrs = brFlags.isLocalMetadataBlocked();
                sBrdrs = brFlags.isRemoteDataBlocked();
                sBrmrs = brFlags.isRemoteMetadataBlocked();

                setBldr(isBldr() || isBldrs());
                setBlmr(isBlmr() || isBlmrs());
                setBrdr(isBrdr() || isBrdrs());
                setBrmr(isBrmr() || isBrmrs());
            }
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
                .putString(CREDIT_UNIT, sCreditUnit)
                .putString(USER_ID, sUuid)
                .putString(DISPLAY_ALIAS, sDisplayAlias)
                .putString(DISPLAY_NAME, sDisplayName)
                .putString(DISPLAY_TN, sDisplayTn)
                .putString(DISPLAY_PLAN, sDisplayPlan)
                .putString(DISPLAY_ORG, sDisplayOrg)
                .putString(AVATAR_URL, sAvatarUrl)
                .putBoolean(OUTBOUND_CALLING_PERMISSION, info.getPermissions().isOutboundCalling())
                .putBoolean(OUTBOUND_CALLING_OCA_PERMISSION, info.getPermissions().isOutboundCallingPstn())
                .putBoolean(OUTBOUND_MESSAGING_PERMISSION, info.getPermissions().isOutboundMessaging())
                .putBoolean(CONFERENCE_PERMISSION, info.getPermissions().isCreateConference())
                .putBoolean(SEND_ATTACHMENT_PERMISSION, info.getPermissions().isSendAttachment())
                .putBoolean(INITIATE_VIDEO_PERMISSION, info.getPermissions().isInitiateVideo())
                .putInt(AVAILABLE_PRE_KEYS, sAvailablePreKeys)
                .putBoolean(DATA_RETENTION_BLDR, sBldr)
                .putBoolean(DATA_RETENTION_BLMR, sBlmr)
                .putBoolean(DATA_RETENTION_BRDR, sBrdr)
                .putBoolean(DATA_RETENTION_BRMR, sBrmr)
                .putBoolean(DATA_RETENTION_BLDRS, sBldrs)
                .putBoolean(DATA_RETENTION_BLMRS, sBlmrs)
                .putBoolean(DATA_RETENTION_BRDRS, sBrdrs)
                .putBoolean(DATA_RETENTION_BRMRS, sBrmrs)
                .putBoolean(DATA_RETENTION_LRMM, sLrmm)
                .putBoolean(DATA_RETENTION_LRMP, sLrmp)
                .putBoolean(DATA_RETENTION_LRCM, sLrcm)
                .putBoolean(DATA_RETENTION_LRCP, sLrcp)
                .putBoolean(DATA_RETENTION_LRAP, sLrap)
                .putString(DATA_RETENTION_ORG, retentionOrg)
                .apply();

        BurnDelay.Defaults.setMaxDelay(info.getPermissions().getMaximumBurnSec());
        setExpirationDate();
        ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
        if (zinaMessaging.isReady())
            ZinaNative.setDataRetentionFlags(getLocalRetentionFlags());
        TiviPhoneService.setDataRetention(/** LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp(),
                LoadUserInfo.isBldr() | LoadUserInfo.isBlmr() | LoadUserInfo.isBrdr() | LoadUserInfo.isBrmr()**/ false, false);
    }

    @Nullable
    private ErrorInfo parseErrorInfoData() {
        if (mContent.length() < 1)
            return null;

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        return gson.fromJson(mContent.toString(), ErrorInfo.class);
    }

    private static void checkPreKeys(Context ctx) {
        if (!ZinaMessaging.getInstance().isReady())
            return;
        if (sAvailablePreKeys > 0 && sAvailablePreKeys < ZinaMessaging.MIN_NUM_PRE_KEYS) {
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    ZinaMessaging.newPreKeys(ZinaMessaging.CREATE_NEW_PRE_KEYS);
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

                int ret = -1;
                try {
                    ret = urlConnection.getResponseCode();
                } catch (SecurityException exception) {
                    boolean isConnected = Utilities.isNetworkConnected(mContext);
                    boolean hasInternetPermission = false;
                    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.INTERNET)
                            == PackageManager.PERMISSION_GRANTED) {
                        hasInternetPermission = true;
                    }

                    Log.d(TAG, "Got a SecurityException - isConnected: " + isConnected
                            + ", hasInternetPermission: " + hasInternetPermission, exception);
                }

                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

                if (ret == HttpsURLConnection.HTTP_OK) {
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
                } else if (ret == HttpsURLConnection.HTTP_FORBIDDEN) {
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getErrorStream()), mContent);
                }
                return ret;

            } catch (IOException e) {
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } finally {
                if (urlConnection != null)
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
            Log.d(TAG, "Received "+result+" on user info request.");
            if (result == HttpsURLConnection.HTTP_OK) {
                parseUserInfoData();
            }
            else {
                boolean handled = false;
                /*
                 * Restrict automatic wipe to debug builds for now
                 */
                if (result == HttpsURLConnection.HTTP_FORBIDDEN) {
                    ErrorInfo errorInfo = parseErrorInfoData();
                    if (errorInfo != null && ErrorInfo.API_KEY_INVALID.equals(errorInfo.errorId)) {
                        Log.d(TAG, "Received "+errorInfo.errorId+" on user info request.");
                        handled = true;
                        callUserInfo(null, errorInfo.errorId, false);

                        final Intent intent = new Intent(Action.WIPE.getName());
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        ComponentName component = new ComponentName(
                                SilentPhoneApplication.getAppContext().getPackageName(),
                                DialerActivity.class.getName());
                        intent.setComponent(component);
                        SilentPhoneApplication.getAppContext().startActivity(intent);
                    }
                }
                if (!handled) {

                    Log.w(TAG, "Reading OCA minutes info failed, code: " + result);

                    callUserInfo(null, mContext.getString(R.string.remaining_oca_minutes_fail), mSilent);
                }
            }
        }

        private void cleanUp() {
        }
    }
}
