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

package com.silentcircle.silentphone2.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;

/**
 * This class implements functions to support different network configurations.
 *
 * Currently SPA supports two network configurations: production network and
 * development network.
 *
 * If this build is a debug or a development build then the user may switch
 * between these two configurations. All other builds do not support this feature.
 *
 * Created by werner on 26.03.14.
 */
public class ConfigurationUtilities {
    private static final String DEVELOP_CONFIG = "DEVELOP_CONFIG";
    private static final String NETWORK_CONFIG = "NETWORK_CONFIG";

    /** Production network is always configuration 0 */
    public static final int PRODUCTION_NETWORK = 0;

    /** Normal development network is configuration 1 */
    public static final int DEVELOPMENT_NETWORK = 1;

    /** Tag name for the device authorization data, development network. */
    public static final String DEV_AUTH_DATA_TAG_DEV = "device_authorization_dev";

    /** Tag name for the unique device id, development network. */
    public static final String DEV_UNIQUE_ID_TAG_DEV = "device_unique_id_dev";

    public static final String NAME_KEY_PRODUCTION = "provisioning_name_production";
    public static final String NAME_KEY_DEVELOPMENT = "provisioning_name_development";

    public static final boolean mEnableDevDebOptions = BuildConfig.DEBUG;
    public static boolean mUseDevelopConfiguration;
    public static int mNetworkConfiguration;
    public static final boolean mTrace = true;
    public static final boolean mNativeLog = BuildConfig.DEBUG;

    private static boolean misInitialized;

    public static void initializeDebugSettings(Context ctx) {
        if (misInitialized || !mEnableDevDebOptions)
            return;
        misInitialized = true;

        // Build flavors define different configuration parameters
        boolean useDevNet = ctx.getResources().getBoolean(R.bool.use_dev_network);
        int netConf = ctx.getResources().getInteger(R.integer.dev_network_config);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        mUseDevelopConfiguration = prefs.getBoolean(DEVELOP_CONFIG, useDevNet);
        mNetworkConfiguration = prefs.getInt(NETWORK_CONFIG, netConf);
    }

    public static void switchToProduction(Context ctx) {
        if (!mEnableDevDebOptions)
            return;
        mNetworkConfiguration = PRODUCTION_NETWORK;
        mUseDevelopConfiguration = false;
        storePreferences(ctx);
    }

    public static void switchToDevelop(Context ctx, int network) {
        if (!mEnableDevDebOptions)
            return;
        mNetworkConfiguration = network;
        mUseDevelopConfiguration = true;
        storePreferences(ctx);
    }

    private static void storePreferences(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(DEVELOP_CONFIG, mUseDevelopConfiguration);
        e.putInt(NETWORK_CONFIG, mNetworkConfiguration);
        e.apply();
    }

    public static String getProvisioningBaseUrl(Context ctx) {
        if (mUseDevelopConfiguration)
            return ctx.getResources().getString(R.string.sccps_development_base_url);
        return ctx.getResources().getString(R.string.sccps_production_base_url);
    }

    public static String getDeviceManagementBase(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_device_management_base);
    }

    public static String getUserManagementBaseV1User(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_user_management_base);
    }

    public static String getUserManagementBaseV1Me(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_user_management_base_alt);
    }

    public static String getDidSelectionBase(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_did_selection_base);
    }

    public static String getSetDidBase(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_did_set_number);
    }

    public static String getDirectorySearch(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_directory_request);
    }

    public static String getAvatarAction(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_avatar_action_request);
    }

    public static String getAuthBase(Context ctx) {
        return ctx.getResources().getString(R.string.sccps_authorization_request);
    }

    public static String getShardAuthTag() {
        return  mUseDevelopConfiguration ? DEV_AUTH_DATA_TAG_DEV : KeyManagerSupport.DEV_AUTH_DATA_TAG;
    }

    public static String getShardDevIdTag() {
        return mUseDevelopConfiguration ? DEV_UNIQUE_ID_TAG_DEV : KeyManagerSupport.DEV_UNIQUE_ID_TAG;
    }

    public static String getReprovisioningNameKey() {
        return mUseDevelopConfiguration ? NAME_KEY_DEVELOPMENT : NAME_KEY_PRODUCTION;
    }

    public static String getConversationDbName() {
        return mUseDevelopConfiguration ? "_axo_store_dev_enc.db" : "_axo_store_enc.db";
    }
    public static String getRepoDbName() {
        return mUseDevelopConfiguration ? "repo_store_dev_enc.db" : "repo_store_enc.db";
    }

    public static String getDevIdKey() {
        return mUseDevelopConfiguration ? "spa_device_id_dev" : "spa_device_id_prod";
    }

    public static String getHwDevIdSaveKey() {
        return mUseDevelopConfiguration ? "spa_device_hw_id_save_dev" : "spa_device_hw_id_save_prod";
    }

    public static String getInstanceDevIdSaveKey() {
        return mUseDevelopConfiguration ? "spa_device_instance_id_save_dev" : "spa_device_instance_id_save_prod";
    }

    public static String getPublishableKeyUrl(Context ctx){
        return ctx.getString(R.string.sccps_publishable_key_request);
    }

    public static String getStripeTokensUrl(Context ctx){
        return ctx.getString(R.string.sccps_stripe_tokens_request);
    }

    public static String getProcessChargeUrl(Context ctx){
        return ctx.getString(R.string.sccps_process_charge_request);
    }

    public static String getSendLogsUrl(Context ctx){
        return ctx.getString(R.string.sccps_send_logs_request);
    }
}
