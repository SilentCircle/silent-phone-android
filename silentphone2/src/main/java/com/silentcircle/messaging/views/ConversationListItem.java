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
package com.silentcircle.messaging.views;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.CallUtils;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ScCallLog;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Widget to house information for single conversation in conversations view.
 */
public class ConversationListItem extends LinearLayout {

    private static final String TAG = "ConversationListItem";

    /** Limit after which unread message count is displayed with greater than sign */
    public static final int UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT = 10;

    /**
     *
     *  Listener interface for {@link ConversationListItem} events.
     */
    public interface OnConversationItemClickListener {

        /* Callback for click event on main conversation list item body */
        void onConversationClick(final ConversationListItem view);

        /* Callback for click event on delete button */
        void onConversationDeleteClick(final ConversationListItem view);

        /* Callback for click event on conversation contact */
        void onConversationContactClick(final ConversationListItem view);
    }

    private OnConversationItemClickListener mOnConversationItemClickListener;

    private View.OnClickListener mConversationClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mOnConversationItemClickListener != null) {
                mOnConversationItemClickListener.onConversationClick(ConversationListItem.this);
            }
        }
    };

    private View.OnLongClickListener mConversationDeleteClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            boolean result = false;
            if (mOnConversationItemClickListener != null) {
                result = true;
                mOnConversationItemClickListener.onConversationDeleteClick(ConversationListItem.this);
            }
            return result;
        }
    };

    private View.OnClickListener mConversationContactClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mOnConversationItemClickListener != null) {
                mOnConversationItemClickListener.onConversationContactClick(ConversationListItem.this);
            }
        }
    };

    private TextView mMessageText;
    private TextView mNameView;
    private TextView mMessageTimeView;
    private TextView mStatusMessageCount;
    private QuickContactBadge mContactButton;
    private FrameLayout mStatusIcon;
    private View mConversationButton;
    private Context mContext;

    public ConversationListItem(Activity context) {
        this(context, null);
    }

    public ConversationListItem(Activity context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textStyle);
    }

    public ConversationListItem(Activity context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflate(context, R.layout.messaging_log_list_item_new, this);

        mMessageText = (TextView) findViewById(R.id.message_text);
        mNameView = (TextView) findViewById(R.id.name);
        mContactButton =
                (QuickContactBadge) findViewById(R.id.quick_contact_photo);
        mMessageTimeView =
                (TextView) findViewById(R.id.message_time);
        mStatusIcon = (FrameLayout) findViewById(R.id.unread_message_notification);
        mStatusMessageCount = (TextView) findViewById(R.id.unread_message_count);
        mConversationButton = findViewById(R.id.conversation_action_view);
        mContext = context.getApplicationContext();
    }

    public void setOnConversationItemClickListener(final OnConversationItemClickListener listener) {
        mOnConversationItemClickListener = listener;
    }

    @SuppressWarnings("deprecation")
    public void setConversation(@NonNull final Conversation conversation,
            final ContactPhotoManagerNew photoManager) {

        final String userId = conversation.getPartner().getUserId();
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(userId);

        String displayName = ConversationUtils.resolveDisplayName(contactEntry, conversation);
//        if (contactEntry != null) {
//            displayName = contactEntry.name;        // Use name on contact entry if available
//        }
//        if (TextUtils.isEmpty(displayName)) {       // If empty try to get it from name lookup cache
//            byte[] dpName = AxoMessaging.getDisplayName(userId);
//            if (dpName != null) {
//                displayName = new String(dpName);
//            }
//            else {
//                // Here we may fall back to a save "display name" we got from the SIP
//                // stack when receiving a message
//                displayName = conversation.getPartner().getDisplayName();
//            }
//        }

        mNameView.setText(displayName);

        AvatarUtils.setPhoto(photoManager, mContactButton, contactEntry);

        mMessageTimeView.setText(
                android.text.format.DateUtils.getRelativeTimeSpanString(
                        conversation.getLastModified(),
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS,
                        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE));

        // if there are any unread messages in conversation, show status icon
        // TODO: wrap this in a widget?
        mStatusIcon.setVisibility(
                conversation.containsUnreadMessages() || conversation.containsUnreadCallMessages()
                        ? View.VISIBLE : View.GONE);
        int unreadMessageCount = conversation.getUnreadMessageCount()
                + conversation.getUnreadCallMessageCount();
        mStatusMessageCount.setText(unreadMessageCount > UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                ? ">" + UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                : String.valueOf(unreadMessageCount));

        mConversationButton.setOnLongClickListener(mConversationDeleteClickListener);
        mConversationButton.setOnClickListener(mConversationClickListener);
        /*
         * Do not set on click listener for quick contact view to preserve Android's default behaviour.
         * With this callback OnConversationItemClickListener#onConversationContactClick will not get
         * called.
         *
        mContactButton.setOnClickListener(mConversationContactClickListener);
         */
    }

    public void setMessageText(final String text) {
        mMessageText.setText(text);
    }

    public void setEvent(final Event event) {
        String messageText = null;
        if (event != null) {
            messageText = event.getText();
            if (event instanceof IncomingMessage) {
                messageText = getAttachmentDescription((Message) event);
            } else if (event instanceof OutgoingMessage) {
                messageText = getAttachmentDescription((Message) event);
            } else if (event instanceof CallMessage) {
                int callType = ((CallMessage) event).getCallType();
                int callDuration = ((CallMessage) event).callDuration;

                messageText = CallUtils.formatCallData(mContext, callType, callDuration);
            }
        }
        else {
            messageText = getResources().getString(R.string.messaging_conversation_list_no_messages);
        }
        setMessageText(messageText);
    }

    private String getAttachmentDescription(Message message) {
        String result = message.getText();

        if (message.hasAttachment()) {
            Resources resources = getResources();
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
                String mimeType = attachmentMetaDataJson.getString("MimeType");
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

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        mMessageText.setSelected(selected);
    }

}
