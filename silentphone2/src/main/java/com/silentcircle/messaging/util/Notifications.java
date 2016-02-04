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
package com.silentcircle.messaging.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;

/**
 * Utilities for messaging notifications.
 */
public final class Notifications {

    private static final String TAG = Notifications.class.getSimpleName();

    private Notifications() {
    }

    /*
     * Show a notification with chat message content
     */
    public static void sendMessageNotification(Context context, Intent messagingIntent,
            String conversationPartner, String messageText, Bitmap notificationImage) {

        int conversationsWithUnreadMessages = ConversationUtils.getConversationsWithUnreadMessages(context);
        int unreadMessageCount = ConversationUtils.getUnreadMessageCount(context);

        if (conversationsWithUnreadMessages <= 0) {
            Log.e(TAG, "Trying to show a notification when there are no unread messages.");
            return;
        }

        Resources resources = context.getResources();
        String title = resources.getQuantityString(R.plurals.notify_new_messages_title,
                unreadMessageCount, Integer.valueOf(unreadMessageCount));
        String subtitle = resources.getQuantityString(R.plurals.notify_new_messages_subtitle,
                conversationsWithUnreadMessages, Integer.valueOf(conversationsWithUnreadMessages),
                resources.getQuantityString(R.plurals.n_messages, unreadMessageCount,
                        Integer.valueOf(unreadMessageCount)));

        Intent activityIntent = messagingIntent;
        if (conversationsWithUnreadMessages > 1) {
            activityIntent = Action.VIEW_CONVERSATIONS.intent(context, DialerActivity.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        int notificationFlags = Notification.DEFAULT_LIGHTS;
        if (TiviPhoneService.calls.getCallCount() == 0) {
            Log.d(TAG, "No ongoing calls detected, adding vibration and sound to notification");
            notificationFlags |= Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;
        }

        Notification notification = new NotificationCompat.Builder(context)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setSmallIcon(R.drawable.ic_chat_notification)
                .setLargeIcon(notificationImage)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(notificationFlags)
                .setAutoCancel(true)
                .build();

        hideSmallIcon(notification);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(R.id.messaging_notification, notification);
    }

    public static void cancelMessageNotification(Context context, String conversationPartner) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(R.id.messaging_notification);
    }

    private static void hideSmallIcon(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int iconId = Resources.getSystem().getIdentifier("right_icon", "id", "android");
            notification.contentView.setViewVisibility(iconId, View.GONE);
            if (notification.bigContentView != null) {
                notification.bigContentView.setViewVisibility(iconId, View.GONE);
            }
        }
    }
}
