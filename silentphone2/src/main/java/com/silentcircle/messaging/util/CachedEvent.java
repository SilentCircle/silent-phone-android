/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.CallUtils;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.messaging.model.CallData;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.views.InfoEventView;
import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

import static com.silentcircle.messaging.services.SCloudService.SCLOUD_METADATA_MIMETYPE;

/**
 * Cached event struct to hold info about last displayable event in conversations list.
 */
public class CachedEvent {

    public CharSequence prefix;
    public CharSequence text;
    public int decoration;
    public boolean isGroup;
    public Event event;

    private long mTimestamp;

    public CachedEvent() {
    }

    public CachedEvent(Context context, Conversation conversation, Event event) {
        isGroup = conversation.getPartner().isGroup();
        setEvent(context, event);
        mTimestamp = System.currentTimeMillis();
    }


    public CachedEvent(Context context, Event event) {
        isGroup = false;
        setEvent(context, event);
        mTimestamp = System.currentTimeMillis();
    }

    public String getId() {
        return event == null ? null : event.getId() + "-" + String.valueOf(mTimestamp);
    }

    public void update(Context context) {
        setEvent(context, event);
    }

    public void setPrefix(final @Nullable CharSequence prefix) {
        this.prefix = prefix;
        /*
         * Skip prefix if it is not desired to have prefix for messages with attachments.
         * SPi has prefix for attachment messages as well.
         *
        if (event instanceof Message && !((Message) event).hasAttachment()) {
            this.prefix = prefix;
        }
         */
    }

    public void setEvent(final @NonNull Context context, final @Nullable Event event) {
        this.event = event;
        decoration = 0;
        if (event != null) {
            if (event instanceof IncomingMessage) {
                text = getAttachmentDescription(context, (Message) event);
                String eventUserId = ((IncomingMessage) event).getSender();
                ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(eventUserId);
                if (contactEntry != null) {
                    CharSequence displayName = ContactsCache.getDisplayName("", contactEntry);
                    prefix = TextUtils.isEmpty(displayName) ? null : StringUtils.formatShortName(displayName.toString()) + ": ";
                }
            } else if (event instanceof OutgoingMessage) {
                text = getAttachmentDescription(context, (Message) event);
            } else if (event instanceof CallMessage) {
                CallMessage callMessage = ((CallMessage) event);
                int callType = callMessage.getCallType();
                int callDuration = callMessage.callDuration;

                text = CallUtils.formatCallData(context, callType, callDuration,
                        CallData.translateSipErrorMsg(context, callMessage.getErrorMessage()));
                decoration = CallUtils.getTypeDrawableResId(callMessage.getCallType());
            } else if (event instanceof InfoEvent) {
                text = InfoEventView.getLocalizedText(context, (InfoEvent) event).toString();
            } else {
                text = event.getText();
            }
        }
    }

    private String getAttachmentDescription(final @NonNull Context context, final @NonNull Message message) {
        String result = message.getText();

        if (message.hasAttachment()) {
            Resources resources = context.getResources();
            SCloudService.AttachmentState state = MessageUtils.getAttachmentState(message);
            if (state == SCloudService.AttachmentState.NOT_AVAILABLE
                    || state == SCloudService.AttachmentState.DOWNLOADING_ERROR
                    || state == SCloudService.AttachmentState.UPLOADING_ERROR) {
                result = resources.getString(R.string.messaging_conversation_list_failed_attachment);
            } else {
                int descriptionPrefixId = getAttachmentMimeDescriptionId(message.getMetaData());
                int description = R.string.messaging_conversation_list_file_received;
                if (message instanceof OutgoingMessage) {
                    description = R.string.messaging_conversation_list_file_sent;
                }
                String descriptionPrefix = resources.getString(descriptionPrefixId);

                result = resources.getString(description, descriptionPrefix);
            }
        }

        return result;
    }

    private int getAttachmentMimeDescriptionId(final String attachmentMetaData) {
        int descriptionPrefixId = R.string.messaging_conversation_list_type_file;
        try {
            if (!TextUtils.isEmpty(attachmentMetaData)) {
                JSONObject attachmentMetaDataJson = new JSONObject(attachmentMetaData);
                String mimeType = attachmentMetaDataJson.getString(SCLOUD_METADATA_MIMETYPE);
                if (MIME.isPdf(mimeType)) {
                    descriptionPrefixId = R.string.messaging_conversation_list_type_pdf;
                } else if (MIME.isAudio(mimeType)) {
                    descriptionPrefixId = R.string.messaging_conversation_list_type_audio;
                } else if (MIME.isVideo(mimeType)) {
                    descriptionPrefixId = R.string.messaging_conversation_list_type_video;
                } else if (MIME.isVisual(mimeType)) {
                    descriptionPrefixId = R.string.messaging_conversation_list_type_image;
                } else if (MIME.isContact(mimeType)) {
                    descriptionPrefixId = R.string.messaging_conversation_list_type_contact;
                }
            }
        } catch (JSONException exception) {
            // Leave descriptionPrefixId set to default value
        }
        return descriptionPrefixId;
    }
}
