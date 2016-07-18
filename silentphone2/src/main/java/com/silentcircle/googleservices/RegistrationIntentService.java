/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.googleservices;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.io.IOException;

public class RegistrationIntentService extends IntentService {

    private static final String TAG = "RegIntentService";

    private static final int BACKOFF_TIME = 5000;       // 5 seconds
    private static final int MAX_BACKOFF_COUNTER = 10;

    public static final String SENT_TOKEN_TO_SERVER = "sentTokenToServer";

    private static boolean mSetToPhoneService;
    private static int mBackOffCounter = 1;

    public RegistrationIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String action = intent.getAction();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.edit().putBoolean(SENT_TOKEN_TO_SERVER, false).apply();

        if ("delete".equals(action)) {
            deleteToken();

            return;
        }

        try {
            // Initially this call goes out to the network to retrieve the token, subsequent calls
            // are local.
            InstanceID instanceID = InstanceID.getInstance(this);
            // See "google-services.json" for detailed information
            final String token = instanceID.getToken(getString(R.string.gcm_defaultSenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

            if (ConfigurationUtilities.mTrace) {
                Log.d(TAG, "GCM Registration Token: " + token.substring(0, 4) + "..., sender id: " +
                        getString(R.string.gcm_defaultSenderId).substring(0, 4) + "...");
            }

            sendRegistrationToServer(token);

            // TODO: Subscribe to topic channels - useful in the future (?)
            // subscribeTopics(token);

            // You should store a boolean that indicates whether the generated token has been
            // sent to your server. If the boolean is false, send the token to your server,
            // otherwise your server should have already received the token.
            sharedPreferences.edit().putBoolean(SENT_TOKEN_TO_SERVER, true).apply();
            mBackOffCounter = 1;
        } catch (Exception e) {
            Log.w(TAG, "Failed to complete token refresh", e);

            // If an exception happens while fetching the new token or updating our registration data
            // on a third-party server, this ensures that we'll attempt the update at a later time.
            mBackOffCounter = (mBackOffCounter < MAX_BACKOFF_COUNTER) ? mBackOffCounter++ : MAX_BACKOFF_COUNTER;
            TiviPhoneService.reScheduleGcmRegistration(mBackOffCounter * BACKOFF_TIME);
        }
    }

    private void deleteToken() {
        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "Delete Google project tokens.");
        }

        InstanceID instanceID = InstanceID.getInstance(this);
        try {
            instanceID.deleteToken(getString(R.string.gcm_defaultSenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE);
        } catch (Exception e) {
            Log.w(TAG, "Failed to complete token delete", e);
        }
    }


    /**
     * Persist registration to third-party servers.
     *
     * The TiviPhoneService.setPushToken saves the token in an internal file.
     *
     * @param token The new token.
     */
    private void sendRegistrationToServer(String token) {
        TiviPhoneService.setPushToken(token);
        mSetToPhoneService = true;
    }

    public static boolean isSetToPhoneService() {
        return mSetToPhoneService;
    }

    // TODO: Useful in the future (?)
    /**
     * Subscribe to any GCM topics of interest, as defined by the TOPICS constant.
     *
     * @param token GCM token
     * @throws IOException if unable to reach the GCM PubSub service
     */
//    private void subscribeTopics(String token) throws IOException {
//        GcmPubSub pubSub = GcmPubSub.getInstance(this);
//        for (String topic : TOPICS) {
//            pubSub.subscribe(token, "/topics/" + topic, null);
//        }
//    }
}
