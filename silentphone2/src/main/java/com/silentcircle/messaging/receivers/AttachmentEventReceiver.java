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
package com.silentcircle.messaging.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.MessageStateEvent;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import zina.ZinaNative;

/**
 * Receiver for attachment events.
 */
public class AttachmentEventReceiver extends BroadcastReceiver implements ZinaMessaging.AxoMessagingStateCallback {

    private static final String TAG = AttachmentEventReceiver.class.getSimpleName();

    private Intent mLastNotificationIntent;
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {

        mLastNotificationIntent = null;
        mContext = null;

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        boolean axoRegistered = axoMessaging.isRegistered();
        if (!axoRegistered) {
            Log.d(TAG, "Axolotl not yet registered, wait for it before showing notification.");
            mLastNotificationIntent = intent;
            mContext = context;
            axoMessaging.addStateChangeListener(this);
        }
        else {
            handleNotificationIntent(context, intent);
        }
    }

    @Override
    public void axoRegistrationStateChange(boolean registered) {
        Log.d(TAG, "Axolotl state: " + registered + ", intent: " + mLastNotificationIntent);
        if (registered && mLastNotificationIntent != null && mContext != null) {
            handleNotificationIntent(mContext, mLastNotificationIntent);

            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
            axoMessaging.removeStateChangeListener(this);
            mLastNotificationIntent = null;
            mContext = null;
        }
    }

    public static void handleNotificationIntent(Context context, Intent intent) {

        String partner = Extra.PARTNER.from(intent);
        String eventId = Extra.ID.from(intent);
        Log.d(TAG, "AttachmentEventReceiver " + Action.from(intent) + ", partner: " + partner
                + ", event id: " + eventId);
        Log.d(TAG, "AttachmentEventReceiver FLAG_GROUP_AVATAR: "
                + intent.getBooleanExtra(SCloudService.FLAG_GROUP_AVATAR, false));

        final ConversationRepository repository = ZinaMessaging.getInstance().getConversations();
        final Conversation conversation = repository.findByPartner(partner);
        final EventRepository events = (conversation != null) ? repository.historyOf(conversation) : null;
        final Event event = events != null ? events.findById(eventId) : null;

        switch (Action.from(intent)) {
            case PROGRESS:
                Log.d(TAG, "AttachmentEventReceiver " + Action.from(intent)
                        + ", partner: " + partner
                        + ", event id: " + eventId
                        + ", progress: " + String.valueOf(Extra.PROGRESS.getInt(intent)));
                break;
            case UPLOAD:
                if (intent.getBooleanExtra(SCloudService.FLAG_GROUP_AVATAR, false)
                        && conversation != null && conversation.getPartner().isGroup()) {
                    String attachment = event != null ? event.getAttachment() : null;
                    if (TextUtils.isEmpty(attachment)) {
                        attachment = intent.getStringExtra(SCloudService.ATTACHMENT_INFO);
                    }

                    int result = ZinaNative.setGroupAvatar(conversation.getPartner().getUserId(),
                            IOUtils.encode(attachment));
                    if (result == MessageErrorCodes.SUCCESS) {
                        result = ZinaMessaging.applyGroupChangeSet(conversation.getPartner().getUserId());
                        if (result != MessageErrorCodes.SUCCESS) {
                            Log.w(TAG, "Failed to send message about group's avatar change.");
                        }
                    } else {
                        Log.w(TAG, "Failed to notify about group's avatar change.");
                    }
                }
                break;
            case RECEIVE_ATTACHMENT:
                if (intent.getBooleanExtra(SCloudService.FLAG_GROUP_AVATAR, false)
                        && conversation != null && conversation.getPartner().isGroup()) {
                    String attachment = event != null ? event.getAttachment() : null;
                    if (TextUtils.isEmpty(attachment)) {
                        attachment = intent.getStringExtra(SCloudService.ATTACHMENT_INFO);
                    }

                    // add cloud_url to avatar info to keep track what is downloaded
                    String cloudUrl = null;
                    try {
                        JSONObject json = new JSONObject(attachment);
                        cloudUrl = json.optString("cloud_url");
                    } catch (JSONException | NullPointerException exception) {
                        // id stays empty
                    }
                    // assume file is a picture if event is InfoEvent
                    final File file = AttachmentUtils.getFile(eventId, context);
                    if (file != null) {
                        try {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                        /*
                         * Update conversation's avatar url so we know that correct attachment is
                         * downloaded
                         */
                            ConversationUtils.setConversationAvatar(context, partner,
                                    AvatarProvider.AVATAR_TYPE_DOWNLOADED + ";" + cloudUrl, bitmap);
                            bitmap.recycle();
                            AttachmentUtils.removeAttachment(partner, eventId, /* keep status */ false, context);
                            ContactPhotoManagerNew.getInstance(context).refreshCache();
                            MessageUtils.requestRefresh(partner);
                        } catch (Throwable t) {
                            // there will be a retry later
                            Log.w(TAG, "Failed to set group avatar for " + partner);
                        }
                    }
                    else {
                        Log.w(TAG, "Avatar downloaded but file is null.");
                    }
                    if (event instanceof MessageStateEvent) {
                        repository.historyOf(conversation).remove(event);
                    }
                };
                break;
            case ERROR:
                if (intent.getBooleanExtra(SCloudService.FLAG_GROUP_AVATAR, false)) {
                    AttachmentUtils.removeAttachment(partner, eventId, /* keep status */ false, context);
                }
                if (event instanceof MessageStateEvent) {
                    repository.historyOf(conversation).remove(event);
                }
                break;
            default:
                break;
        }

    }
}