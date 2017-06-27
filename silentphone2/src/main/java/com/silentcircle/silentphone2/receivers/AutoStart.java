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
package com.silentcircle.silentphone2.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.logs.Log;

import com.silentcircle.messaging.services.SCloudCleanupService;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

public class AutoStart extends BroadcastReceiver {
    private static final String TAG = AutoStart.class.getSimpleName();

    public static final String ON_BOOT = "start_on_boot";

    private static boolean mDisableAutoStart;

    public AutoStart() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action))
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Run cleanup for any possible leftover attachment files (rare)
        runScloudCleanup(context);

        boolean startOnBoot = prefs.getBoolean(SettingsFragment.START_ON_BOOT, false);
        boolean fromUserSwitch = Intent.ACTION_USER_PRESENT.equals(action)
                && SilentPhoneApplication.userBackgrounded();

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Auto start action: " + action
                + ", startOnBoot: " + startOnBoot
                + ", mDisableAutoStart: " + mDisableAutoStart
                + ", fromUserSwitch: " + fromUserSwitch);

        if ((startOnBoot && !mDisableAutoStart && Intent.ACTION_BOOT_COMPLETED.equals(action))
                || fromUserSwitch) {
            if (fromUserSwitch) {
                SilentPhoneApplication.setUserBackgrounded(false);
            }
            Intent i = new Intent(context, DialerActivity.class);
            i.setAction(ON_BOOT);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    public static void setDisableAutoStart(boolean disable) {
        mDisableAutoStart = disable;
    }

    private void runScloudCleanup(Context context) {
        Intent cleanupIntent = Action.PURGE_ATTACHMENTS.intent(context, SCloudCleanupService.class);
        context.startService(cleanupIntent);
    }
}
