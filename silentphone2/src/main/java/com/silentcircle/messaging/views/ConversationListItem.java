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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.CallUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.messaging.model.CallData;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Widget to house information for single conversation in conversations view.
 */
public class ConversationListItem extends LinearLayout implements View.OnClickListener {

    private static final String TAG = "ConversationListItem";

    /** Limit after which unread message count is displayed with greater than sign */
    public static final int UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT = 10;

    public static final String CONVERSATION_UNREAD_MESSAGES_MORE_THAN = ">";
    public static final String CONVERSATION_ATTENTION_REQUIRED = "!";

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

        void onCallClick(final ConversationListItem view);
    }

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

    private OnConversationItemClickListener mOnConversationItemClickListener;

    private TextView mMessageText;
    private TextView mNameView;
    private TextView mMessageTimeView;
    private TextView mStatusMessageCount;
    private View mStatusDataRetention;
    private QuickContactBadge mContactButton;
    private FrameLayout mStatusIcon;
    private View mConversationButton;
    private View mCallButton;
    private View mDeleteButton;
    private SwipeRevealLayout mContainer;

    private String mJustNow;
    private String mPrefixYou;
    private String mPrefixDraft;
    private String mInvitationPending;

    private int mTextColor;
    private int mPrefixColor;

    private static Typeface sDefaultTypeFace = Typeface.create("sans-serif", Typeface.NORMAL);
    private static Typeface sSemiboldTypeFace =
            Typeface.create(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? "sans-serif-light"
                : "sans-serif-medium", Typeface.BOLD);

    public ConversationListItem(Context context) {
        this(context, null);
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textStyle);
    }

    public ConversationListItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mJustNow = context.getString(R.string.just_now);
        mPrefixYou = context.getString(R.string.messaging_conversation_list_prefix_you);
        mPrefixDraft = context.getString(R.string.messaging_conversation_list_prefix_draft);
        mInvitationPending = context.getString(R.string.group_messaging_invitation_pending);

        mTextColor = ViewUtil.getColorFromAttributeId(context, R.attr.sp_activity_secondary_text_color);
        mPrefixColor = ViewUtil.getColorFromAttributeId(context, R.attr.sp_activity_primary_text_color);

        inflate(context, R.layout.messaging_log_list_item_new, this);

        mMessageText = (TextView) findViewById(R.id.message_text);
        mNameView = (TextView) findViewById(R.id.name);
        mContactButton =
                (QuickContactBadge) findViewById(R.id.quick_contact_photo);
        mMessageTimeView =
                (TextView) findViewById(R.id.message_time);
        mStatusIcon = (FrameLayout) findViewById(R.id.unread_message_notification);
        mStatusDataRetention = findViewById(R.id.data_retention_status);
        mStatusMessageCount = (TextView) findViewById(R.id.unread_message_count);
        mConversationButton = findViewById(R.id.conversation_log_row);
        mCallButton = findViewById(R.id.conversation_log_call);
        mDeleteButton = findViewById(R.id.conversation_log_delete);

        mContainer = (SwipeRevealLayout) findViewById(R.id.conversation_log_list_item);

        mConversationButton.setOnClickListener(this);
        /*
         * Do not set on click listener for quick contact view to preserve Android's default behaviour.
         * With this callback OnConversationItemClickListener#onConversationContactClick will not get
         * called.
         *
        mContactButton.setOnClickListener(this);
         */
        mCallButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
        findViewById(R.id.call_layout).setOnClickListener(this);
        findViewById(R.id.delete_layout).setOnClickListener(this);
    }

    public void setOnConversationItemClickListener(final OnConversationItemClickListener listener) {
        mOnConversationItemClickListener = listener;
    }

    public void setConversation(@NonNull final Conversation conversation) {
        long now = System.currentTimeMillis();
        CharSequence timeText;
        if (Math.abs(now - conversation.getLastModified()) < TimeUnit.MINUTES.toMillis(1)) {
            timeText = mJustNow;
        } else {
            timeText = android.text.format.DateUtils.getRelativeTimeSpanString(
                    conversation.getLastModified(),
                    now,
                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE);
        }
        mMessageTimeView.setText(timeText);

        // if there are any unread messages in conversation, show status icon
        // TODO: wrap this in a widget?
        boolean statusVisible = conversation.containsUnreadMessages()
                || conversation.containsUnreadCallMessages();
        mStatusIcon.setVisibility(statusVisible ? View.VISIBLE : View.GONE);
        Typeface typeFace = statusVisible ? sSemiboldTypeFace : sDefaultTypeFace;
        mMessageText.setTypeface(typeFace);
        mNameView.setTypeface(typeFace);
        int unreadMessageCount = conversation.getUnreadMessageCount()
                + conversation.getUnreadCallMessageCount();
        String statusText = (unreadMessageCount > UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                ? CONVERSATION_UNREAD_MESSAGES_MORE_THAN + UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                : String.valueOf(unreadMessageCount));
        mStatusMessageCount.setText(statusText);
        // draft text overrides last message text
        if (!TextUtils.isEmpty(conversation.getUnsentText())) {
            setMessageDecoration(0);
            setMessageText(mPrefixDraft, conversation.getUnsentText());
        }

        mConversationButton.setOnClickListener(this);
        /*
         * Do not set on click listener for quick contact view to preserve Android's default behaviour.
         * With this callback OnConversationItemClickListener#onConversationContactClick will not get
         * called.
         *
        mContactButton.setOnClickListener(mConversationContactClickListener);
         */
        mCallButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.conversation_log_call:
            case R.id.call_layout:
                if (mOnConversationItemClickListener != null) {
                    mOnConversationItemClickListener.onCallClick(this);
                }
                break;
            case R.id.conversation_log_delete:
            case R.id.delete_layout:
                if (mOnConversationItemClickListener != null) {
                    mOnConversationItemClickListener.onConversationDeleteClick(this);
                }
                break;
            case R.id.conversation_log_row:
                if (mOnConversationItemClickListener != null) {
                    mOnConversationItemClickListener.onConversationClick(this);
                }
                break;
        }
    }

    public void setAvatar(@NonNull final ContactPhotoManagerNew photoManager,
            @NonNull final Conversation conversation, @Nullable final ContactEntry contactEntry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mContactButton.setOverlay(null);
        }

        if (contactEntry != null) {
            AvatarUtils.setPhoto(photoManager, mContactButton, contactEntry,
                /* isCircular */ !AvatarProvider.AVATAR_TYPE_GENERATED.equals(conversation.getAvatarUrl()));
        }
        else {
            AvatarUtils.setPhoto(photoManager, mContactButton,
                AvatarUtils.getAvatarProviderUri(conversation.getAvatarUrl(), conversation.getPartner().getUserId()),
                ContactPhotoManagerNew.TYPE_DEFAULT,
                /* isCircular */ !AvatarProvider.AVATAR_TYPE_GENERATED.equals(conversation.getAvatarUrl()));
        }
    }

    @SuppressWarnings("deprecation")
    public void setContactEntry(@Nullable final ContactEntry contactEntry,
            @NonNull final Conversation conversation) {

        if (contactEntry == null) {
            mNameView.setText(R.string.loading);
            return;
        }

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

        Contact partner = conversation.getPartner();
        if (partner.isGroup()) {
            displayName = conversation.getPartner().getDisplayName();
            if (TextUtils.isEmpty(displayName)) {
                // FIXME this may affect conversation loading a bit
                ConversationUtils.GroupData groupData =
                        ConversationUtils.getGroup(partner.getUserId());
                displayName = groupData != null ? groupData.getGroupName() : partner.getUserId();
                if (groupData != null) {
                    ConversationUtils.verifyConversationAvatar(getContext(), partner.getUserId(),
                            groupData.getAvatarInfo());
                }
            }
        }

        mNameView.setText(displayName);
    }

    public void setMessageText(final String prefix, final String text) {

        SpannableStringBuilder builder = new SpannableStringBuilder();

        if (!TextUtils.isEmpty(prefix)) {
            SpannableString prefixSpan = new SpannableString(prefix);
            prefixSpan.setSpan(new ForegroundColorSpan(mPrefixColor), 0, prefixSpan.length(), 0);
            builder.append(prefixSpan);
        }

        SpannableString textSpan = new SpannableString(text);
        textSpan.setSpan(new ForegroundColorSpan(mTextColor), 0, textSpan.length(), 0);
        builder.append(textSpan);

        mMessageText.setText(builder, TextView.BufferType.SPANNABLE);
    }

    public void setMessageDecoration(final int drawableId) {
        mMessageText.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0);
    }

    public void setEvent(final Event event) {
        String messageText;
        String messagePrefix = null;
        int messageDecoration = 0;
        if (event != null) {
            messageText = event.getText();
            if (event instanceof IncomingMessage) {
                messageText = getAttachmentDescription((Message) event);
            } else if (event instanceof OutgoingMessage) {
                messageText = getAttachmentDescription((Message) event);
                if (!((Message) event).hasAttachment()) {
                    messagePrefix = mPrefixYou;
                }
            } else if (event instanceof CallMessage) {
                CallMessage callMessage = ((CallMessage) event);
                int callType = callMessage.getCallType();
                int callDuration = callMessage.callDuration;
                callMessage.getErrorMessage();

                messageText = CallUtils.formatCallData(getContext(), callType, callDuration,
                        CallData.translateSipErrorMsg(getContext(), callMessage.getErrorMessage()));
                messageDecoration = CallUtils.getTypeDrawableResId(callMessage.getCallType());
            } else if (event instanceof InfoEvent) {
                messageText = InfoEventView.getLocalizedText(getContext(), (InfoEvent) event).toString();
            }
        }
        else {
            messageText = getResources().getString(R.string.messaging_conversation_list_no_messages);
        }
        setMessageDecoration(messageDecoration);
        setMessageText(messagePrefix, messageText);
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

    public void setIsCallable(boolean enabled) {
        mContainer.setSectLeftEnabled(enabled);
    }

    public void setIsDataRetained(boolean retained) {
        mStatusDataRetention.setVisibility(retained ? View.VISIBLE : View.GONE);
    }
}
