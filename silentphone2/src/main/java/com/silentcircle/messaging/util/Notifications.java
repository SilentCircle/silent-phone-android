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
package com.silentcircle.messaging.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.util.concurrent.TimeUnit;

import static com.silentcircle.messaging.model.event.ErrorEvent.POLICY_ERROR_MESSAGE_BLOCKED;
import static com.silentcircle.messaging.model.event.ErrorEvent.POLICY_ERROR_MESSAGE_REJECTED;
import static com.silentcircle.messaging.model.event.ErrorEvent.POLICY_ERROR_RETENTION_REQUIRED;

/**
 * Utilities for messaging notifications.
 */
public final class Notifications {

    private static final String TAG = Notifications.class.getSimpleName();

    private static long sMinimumTimeOfNextNotification = Long.MIN_VALUE;
    private static final long NOTIFICATION_WINDOW = TimeUnit.SECONDS.toMillis(2);

    private static final long[] VIBRATE_LONG = new long[] {1500, 1500};
    private static final long[] VIBRATE_SHORT = new long[] {500, 500};

    private static final int LIGHTS_INTERVAL_ON = 500;
    private static final int LIGHTS_INTERVAL_OFF = 1000;

    private Notifications() {
    }

    /*
     * Show a notification with chat message content
     */
    public static void sendMessageNotification(Context context, Intent messagingIntent) {

        int conversationsWithUnreadMessages = ConversationUtils.getConversationsWithUnreadMessages();
        int conversationsWithUnreadCallMessages = ConversationUtils.getConversationsWithUnreadCallMessages();
        int groupConversationsWithUnreadMessages = ConversationUtils.getGroupConversationsWithUnreadMessages();

        int unreadMessageCount = ConversationUtils.getUnreadMessageCount();
        int unreadCallMessageCount = ConversationUtils.getUnreadCallMessageCount();

        if (conversationsWithUnreadMessages <= 0 && conversationsWithUnreadCallMessages <= 0) {
            Log.e(TAG, "Trying to show a notification when there are no unread messages.");
            return;
        }

        Message unreadMessage = ConversationUtils.getLastUnreadMessage();

        Resources resources = context.getResources();
        String title = "";
        String subtitle = "";

        Bitmap largeIcon = null;
        int smallIconResId;

        if (unreadCallMessageCount > 0 && unreadMessage instanceof CallMessage) {
            title = resources.getQuantityString(R.plurals.number_missed_calls,
                    unreadCallMessageCount, unreadCallMessageCount);
            subtitle = resources.getQuantityString(R.plurals.notify_new_calls_subtitle,
                    conversationsWithUnreadCallMessages, conversationsWithUnreadCallMessages,
                    resources.getQuantityString(R.plurals.n_calls, unreadCallMessageCount,
                            unreadCallMessageCount));

            largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_sp);;
            smallIconResId = R.drawable.stat_notify_missed_call;
        } else if (unreadMessageCount > 0 && unreadMessage instanceof IncomingMessage) {
            title = resources.getQuantityString(R.plurals.notify_new_messages_title,
                    unreadMessageCount, unreadMessageCount);
            if (groupConversationsWithUnreadMessages > 0) {
                subtitle = resources.getQuantityString(R.plurals.notify_generic_new_messages_subtitle,
                        conversationsWithUnreadMessages, conversationsWithUnreadMessages,
                        resources.getQuantityString(R.plurals.generic_n_messages, unreadMessageCount, unreadMessageCount));
            }
            else {
                subtitle = resources.getQuantityString(R.plurals.notify_new_messages_subtitle,
                        conversationsWithUnreadMessages, conversationsWithUnreadMessages,
                        resources.getQuantityString(R.plurals.n_messages, unreadMessageCount, unreadMessageCount));
            }

            largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_st);
            smallIconResId = R.drawable.ic_chat_notification;
        } else {
            Log.e(TAG, "Trying to show a notification when there are no unread messages.");
            return;
        }

        Intent activityIntent = messagingIntent;
        if (conversationsWithUnreadMessages > 1 || conversationsWithUnreadCallMessages > 1) {
            // open conversation list view instead of specific conversation
            activityIntent = Action.VIEW_CONVERSATIONS.intent(context, DialerActivity.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setSmallIcon(smallIconResId)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentIntent)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true);

        postNotification(context, builder, R.id.messaging_notification);
    }

    public static void sendPolicyNotification(Context context, Intent messagingIntent, String reason) {
        String title = context.getString(R.string.data_retention_message_not_delivered);

        int subtitleId = 0;
        if (POLICY_ERROR_RETENTION_REQUIRED.equals(reason)) {
            subtitleId = R.string.data_retention_communication_dr_required;
        } else if (POLICY_ERROR_MESSAGE_REJECTED.equals(reason)) {
            subtitleId = R.string.data_retention_communication_dr_blocked;
        } else if (POLICY_ERROR_MESSAGE_BLOCKED.equals(reason)) {
            subtitleId = R.string.data_retention_communication_blocked_remote;
        }
        String subtitle = subtitleId == 0 ? "" : context.getString(subtitleId);

        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_st);
        int smallIconResId = R.drawable.ic_chat_notification;

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, messagingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setSmallIcon(smallIconResId)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentIntent)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true);

        postNotification(context, builder, R.id.policy_notification);
    }

    public static void sendInviteNotification(Context context, Intent messagingIntent) {
        Resources resources = context.getResources();
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_st);
        int smallIconResId = R.drawable.ic_chat_notification;

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, messagingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle(resources.getString(R.string.group_messaging_invite_notification))
                .setContentText("")
                .setSmallIcon(smallIconResId)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentIntent)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true);

        Notification notification = builder.build();

        hideSmallIcon(notification);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(R.id.invite_notification, notification);
    }

    public static void cancelMessageNotification(@Nullable Context context, String conversationPartner) {
        cancelNotification(context, R.id.messaging_notification);
    }

    public static void cancelGroupCommandNotification(@Nullable Context context) {
        cancelNotification(context, R.id.invite_notification);
    }

    private static void cancelNotification(@Nullable Context context, int notificationId) {
        if (context != null) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }
    }

    @SuppressWarnings("deprecation")
    private static void hideSmallIcon(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int iconId = Resources.getSystem().getIdentifier("right_icon", "id", "android");
            if (notification.contentView != null) {
                notification.contentView.setViewVisibility(iconId, View.GONE);
                if (notification.bigContentView != null) {
                    notification.bigContentView.setViewVisibility(iconId, View.GONE);
                }
            }
        }
    }

    private static void postNotification(Context context, Notification.Builder builder,
            int messaging_notification) {
        configureNotification(context, builder);

        Notification notification = builder.build();

        hideSmallIcon(notification);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            notificationManager.notify(messaging_notification, notification);
        } catch (Throwable ignore) {
            // @see <a href="https://sentry.silentcircle.org/sentry/spa/issues/6280/">this crash</a>
            // TODO: Why?
        }
    }

    public static boolean isReadyForNextNotification() {
        long now = SystemClock.elapsedRealtime();
        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "isReadyForNextNotification " + (now >= sMinimumTimeOfNextNotification)
                    + " (" + (now - sMinimumTimeOfNextNotification) + ")");
        }

        return now >= sMinimumTimeOfNextNotification;
    }

    private static void setDelayForNextNotification() {
        sMinimumTimeOfNextNotification = SystemClock.elapsedRealtime() + NOTIFICATION_WINDOW;
    }

    private static Notification.Builder configureNotification(@NonNull Context context,
            @NonNull Notification.Builder builder) {
        Resources resources = context.getResources();

        MessagingPreferences preferences = MessagingPreferences.getInstance(context);
        boolean soundsEnabled = preferences.getMessageSoundsEnabled();

        int light = preferences.getMessageLight();
        int vibrate = preferences.getMessageVibrate();
        Uri sound = preferences.getMessageRingtone();
        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "Notification settings: light: " + light + ", vibrate pattern: " + vibrate
                    + " sound: " + sound);
        }

        // calculate notification default values
        int defaultLightFlag =
                light == MessagingPreferences.INDEX_LIGHT_DEFAULT ? Notification.DEFAULT_LIGHTS : 0;
        int defaultVibrateFlag =
                vibrate == MessagingPreferences.INDEX_VIBRATE_DEFAULT ? Notification.DEFAULT_VIBRATE : 0;
        // use system's default sound if sounds enabled and sound notification not set
        int defaultSoundFlag = soundsEnabled && sound == null ? Notification.DEFAULT_SOUND : 0;

        int notificationFlags = defaultLightFlag;
        builder.setDefaults(notificationFlags);

        if (TiviPhoneService.calls.getCallCount() == 0 && isReadyForNextNotification()) {
            if (ConfigurationUtilities.mTrace) {
                Log.d(TAG, "No ongoing calls detected and enough time elapsed since last sound "
                        + "notification, adding vibration and sound to upcoming notification");
            }
            notificationFlags |= defaultSoundFlag | defaultVibrateFlag;
            builder.setDefaults(notificationFlags);

            // set vibrate and sound for notification here where it is known that notification is
            // allowed to use vibrate and play sound (no calls active and a timeout is observed)
            if (sound != null && soundsEnabled) {
                builder.setSound(sound);
            }

            if (vibrate != MessagingPreferences.INDEX_VIBRATE_DEFAULT
                    && vibrate != MessagingPreferences.INDEX_VIBRATE_OFF) {
                long[] vibratePattern = vibrate == MessagingPreferences.INDEX_VIBRATE_LONG
                        ? VIBRATE_LONG : VIBRATE_SHORT;
                builder.setVibrate(vibratePattern);
            }

            setDelayForNextNotification();
        }

        if (light != MessagingPreferences.INDEX_LIGHT_DEFAULT
                && light != MessagingPreferences.INDEX_LIGHT_OFF) {
            // compensate for configuration values 'default' and 'off' when calculating index
            int index = light - 2;
            int[] colors = resources.getIntArray(R.array.message_lights);
            int color = (index < colors.length && (index >= 0)) ? colors[index] : colors[0];
            if (ConfigurationUtilities.mTrace) {
                Log.d(TAG, "Using notification color " + Integer.toHexString(color) + " (" + light + ")");
            }
            builder.setLights(color, LIGHTS_INTERVAL_ON, LIGHTS_INTERVAL_OFF);
        }

        return builder;
    }

}
