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

import android.app.Activity;
import android.app.FragmentManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.contacts.ScCallLog;
import com.silentcircle.contacts.calllognew.IntentProvider;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.services.InsertCallLogHelper;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.userinfo.LoadUserInfo;

/**
 * Created by Sam on 2/18/2016.
 */
public class CallUtils {
    public static int getTypeStringResId(int callType) {
        switch (callType) {
            case ScCallLog.ScCalls.INCOMING_TYPE:
                return R.string.type_incoming;

            case ScCallLog.ScCalls.OUTGOING_TYPE:
                return R.string.type_outgoing;

            case ScCallLog.ScCalls.MISSED_TYPE:
                return R.string.type_missed;

            default:
                return -1;
        }
    }

    public static int getTypeDrawableResId(int callType) {
        switch (callType) {
            case ScCallLog.ScCalls.INCOMING_TYPE:
                return R.drawable.ic_call_incoming_holo_dark;

            case ScCallLog.ScCalls.OUTGOING_TYPE:
                return R.drawable.ic_call_outgoing_holo_dark;

            case ScCallLog.ScCalls.MISSED_TYPE:
                return R.drawable.ic_call_missed_holo_dark;

            default:
                return -1;
        }
    }

    public static String formatCallData(Context context, int callType, int callDuration) {
        int stringResId = CallUtils.getTypeStringResId(callType);

        String duration = android.text.format.DateUtils.formatElapsedTime(callDuration);

        return String.format("%s (%s)", context.getString(stringResId), duration);
    }

    public static String formatCallData(Context context, int callType, int callDuration,
            String errorMessage) {
        String result;
        if (!TextUtils.isEmpty(errorMessage)) {
            result = errorMessage;
        } else {
            result = formatCallData(context, callType, callDuration);
        }
        return result;
    }

    public static void removeMissedCallNotifications(Context ctx) {
        if (ctx != null) {
            NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(InsertCallLogHelper.MISSED_CALL_NOTIFICATION_ID);
        }
    }

    public static void checkAndLaunchSilentPhoneCall(@Nullable Activity activity,
            @Nullable String partner) {
        if (activity == null || TextUtils.isEmpty(partner)) {
            return;
        }

        /* Check whether it is possible for user to have a conference call and show message if not */
        if (TiviPhoneService.calls.getCallCount() >= 1
                && !LoadUserInfo.canStartConference(activity)) {
            InfoMsgDialogFragment infoMsg =
                    InfoMsgDialogFragment.newInstance(R.string.information_dialog,
                            LoadUserInfo.getDenialStringResId(), android.R.string.ok, -1);
            FragmentManager fragmentManager = activity.getFragmentManager();
            if (fragmentManager != null) {
                fragmentManager.beginTransaction()
                        .add(infoMsg, InfoMsgDialogFragment.TAG_INFO_MESSAGE_DIALOG)
                        .commitAllowingStateLoss();
            }
            return;
        }

        /*
        if (LoadUserInfo.isBrmr() || LoadUserInfo.isBrdr()) {
            // always ensure that user info is up to date before the call
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    ZinaMessaging.refreshUserData(userId, null);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            launchSilentPhoneCall(userId);
                        }
                    });
                }
            });
        } else {
            launchSilentPhoneCall(userId);
        }
         */
        launchSilentPhoneCall(activity, partner);
    }

    public static void launchSilentPhoneCall(@NonNull Activity activity, @NonNull String partner) {

        /*
        if (DRUtils.isCallingDrBlocked(activity, partner)) {
            return;
        }
         */

        final IntentProvider intentProvider = IntentProvider.getReturnCallIntentProvider(partner);
        final Intent intent = intentProvider.getIntent(SilentPhoneApplication.getAppContext());
        activity.startActivity(intent);
    }
}
