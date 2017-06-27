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
package com.silentcircle.googleservices;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.silentcircle.logs.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.receivers.AutoStart;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Handle GCM messages.
 *
 * Created by rli on 6/30/15.
 * Re-written by wernerd, align to GCM 3
 */
public class C2DMReceiver extends GcmListenerService {

    private static final String TAG = "C2DMReceiver";
    private static final int NOTIFY_ID = 471108153; // Random

    @Override
    public void onMessageReceived(String from, Bundle data) {
        String message = data.getString("alert") + ", from " + from;
        Log.d(TAG, "Message: " + message);
        Log.d(TAG, "bundle: " + data);

        // For now, we only have register pushes
        onRegisterPush(this, message);
    }

    public static void onRegisterPush(Context context, String message) {
        // Try to either send a register or get back into a working state
        if (!TiviPhoneService.isInitialized()) {
            Intent i = new Intent(context, DialerActivity.class);
            i.setAction(AutoStart.ON_BOOT); // So it is visibly silent
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } else {
            if (Utilities.isNetworkConnected(context)) {
                // perform on-push-notification specific code
                // spelling "onPushNotifcation" is intentional
                if (TiviPhoneService.isInitialized()) {
                    TiviPhoneService.getInfo(-1, -1, ":onPushNotifcation");
                }

                int phoneState = TiviPhoneService.getPhoneState();
                Log.d(TAG, "onRegisterPush phoneState: " + phoneState);
                if (phoneState == 2) { // Online
                    TiviPhoneService.doRegister();
                } else if (phoneState == 1) { // Connecting
                    // Do nothing
                } else if (phoneState == 0) { // Offline
                    // Reset engine
                    if (TiviPhoneService.phoneService != null) {
                        TiviPhoneService.phoneService.setReady(false);
                    }
                    TiviPhoneService.setInitialized(false);
                    TiviPhoneService.doCmd(".exit");
                    context.startService(new Intent(context, TiviPhoneService.class));
                }
            } else if (Utilities.isNetworkConnectedOrConnecting(context)) {
                // Do nothing
            }
        }

        if (BuildConfig.DEBUG) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean developer = prefs.getBoolean(SettingsFragment.DEVELOPER, false);

            if (developer) {
                sendNotification(context, message);
            }
        }
    }

    /**
     * Create and show a simple notification containing the received GCM message.
     *
     * Only called for debug and develop builds.
     *
     * @param message GCM message received.
     */
    private static void sendNotification(Context context, String message) {
        Notification notification = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_launcher_sp)
                .setContentTitle("GCM Message (dev)")
                .setContentText(message)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFY_ID, notification);
    }
}
