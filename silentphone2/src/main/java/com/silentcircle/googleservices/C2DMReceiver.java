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

package com.silentcircle.googleservices;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

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

        if (TiviPhoneService.isInitialized())
            AsyncTasks.asyncCommand(":reg");
        if (ConfigurationUtilities.mTrace)
            sendNotification(message);
    }

    /**
     * Create and show a simple notification containing the received GCM message.
     *
     * Only called for debug and develop builds.
     *
     * @param message GCM message received.
     */
    private void sendNotification(String message) {
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher_sp)
                .setContentTitle("GCM Message (debug)")
                .setContentText(message)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFY_ID, notification);
    }
}
