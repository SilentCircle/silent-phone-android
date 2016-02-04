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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.thread.Updater;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.messaging.util.Updatable;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;
import com.silentcircle.silentphone2.Manifest;
import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

public class MessageEventView extends RelativeLayout implements Updatable, Checkable, HasChoiceMode, OnClickListener {

    private static final SimpleDateFormat sDebugDateFormatter = new SimpleDateFormat("dd/MM/yy hh:mm:ss.SSS");

    static class Views {

        public final AvatarView avatar;
        public final LinearLayout card;
        public final TextView attachment_text;
        public final ImageView preview;
        public final ImageView preview_icon;
        public final ProgressBar preview_progress;
        public final TextView text;
        public final TextView time;
        public final TextView burn_notice;
        public final View delivered;
        public final View action_location;
        public final ViewGroup action_burn;
        public final View action_send;
        public final TextView message_state;
        public final RelativeLayout message_actions;

        public Views(MessageEventView parent) {

            avatar = (AvatarView) parent.findViewById(R.id.message_avatar);
            card = (LinearLayout) parent.findViewById(R.id.message_card);
            attachment_text = (TextView) parent.findViewById(R.id.message_attachment_text);
            preview = (ImageView) parent.findViewById(R.id.message_preview);
            preview_icon = (ImageView) parent.findViewById(R.id.message_icon);
            preview_progress = (ProgressBar) parent.findViewById(R.id.message_preview_progress);
            text = (TextView) parent.findViewById(R.id.message_body);
            time = (TextView) parent.findViewById(R.id.message_time);
            burn_notice = (TextView) parent.findViewById(R.id.message_burn_notice);
            delivered = parent.findViewById(R.id.message_delivered);

            message_actions = (RelativeLayout) parent.findViewById(R.id.message_actions);

            action_location = parent.findViewById(R.id.message_action_location);
            action_location.setOnClickListener(parent);

            action_burn = (ViewGroup) parent.findViewById(R.id.message_action_burn);
            action_burn.setOnClickListener(parent);

            action_send = parent.findViewById(R.id.message_action_send);

            if (action_send != null) {
                action_send.setOnClickListener(parent);
            }

            // view to debug message state
            message_state = (TextView) parent.findViewById(R.id.message_state);
            // Left for debug: use this to debug message states
            message_state.setVisibility(View.GONE);
        }

        public void setInformationViewVisibility(int visibility) {
            preview.setVisibility(visibility);
            preview_icon.setVisibility(visibility);
            preview_progress.setVisibility(visibility);
            text.setVisibility(visibility);
            time.setVisibility(visibility);
            burn_notice.setVisibility(visibility);
            action_location.setVisibility(visibility);
            action_burn.setVisibility(visibility);
            if (visibility == View.GONE) {
                text.setText(null);
                time.setText(null);
                burn_notice.setText(null);
                attachment_text.setVisibility(visibility);
            }
            else {
                // always show card as well
                card.setVisibility(visibility);
            }
        }
    }

    private static void setClickable(boolean clickable, View... views) {
        for (View view : views) {
            if (view != null) {
                view.setClickable(clickable);
            }
        }
    }

    private static void toggleDeliveredState(Message message, View view) {
        if (view != null) {
            if (MessageStates.READ == message.getState()) {
                view.setVisibility(VISIBLE);
                ViewUtil.setAlpha(view, 0.5f);
            } else {
                view.setVisibility(GONE);
            }
        }
    }

    private static void toggleSendActionVisibility(Message message, View actionView) {
        if (actionView != null) {
            int visibility = GONE;
            if (message instanceof OutgoingMessage) {
                if (MessageStates.RESEND_REQUESTED == message.getState()) {
                    visibility = VISIBLE;
                }
            }
            actionView.setVisibility(visibility);
        }
    }

    public static final int [] STATE_CHECKED = {
            android.R.attr.state_checked
    };

    private Views views;

    private boolean checked;

    private boolean inChoiceMode;

    private final Updater updater;

    private Location mLocation;

    private Drawable mBackgroundDrawable;

    Context context;

    public MessageEventView(Context context) {
        super(context);
        MessageEventView.this.context = context;
        updater = new Updater(this);
    }

    public MessageEventView(Context context, AttributeSet attrs) {
        super(context, attrs);
        MessageEventView.this.context = context;
        updater = new Updater(this);
    }

    public MessageEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        MessageEventView.this.context = context;
        updater = new Updater(this);
    }

    public Message getMessage() {
        Object tag = getTag();
        return tag instanceof Message ? (Message) tag : null;
    }

    private Views getViews() {
        if (views == null) {
            views = new Views(this);
        }
        return views;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public boolean isInChoiceMode() {
        return inChoiceMode;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setCardBackground(mBackgroundDrawable);
        update();
    }

    @Override
    public void onClick(View view) {

        if (isInChoiceMode()) {
            return;
        }

        Views v = getViews();
        final Message message = getMessage();
        Context context = view.getContext();

        if (message == null) {
            return;
        }

        if (v.action_location == view && mLocation != null) {
            LocationUtils.viewLocation(context, mLocation.getLatitude(), mLocation.getLongitude());
            return;
        }

        if (v.action_burn == view && message.expires()) {
            burnMessage(message);
            return;
        }

        if (v.action_send == view) {
            if (message.getState() == MessageStates.RESEND_REQUESTED) {
                AxoMessaging.getInstance(getContext()).sendMessage(message);
                return;
            }
            Intent intent = Action.TRANSITION.intent();
            Extra.PARTNER.to(intent, message.getConversationID());
            Extra.ID.to(intent, message.getId());
            Extra.STATE.to(intent, MessageStates.COMPOSED);
            context.sendBroadcast(intent, Manifest.permission.WRITE);
            return;
        }

        if (message.getAttachment() != null) {
            Intent intent = Action.DOWNLOAD.intent();
            Extra.PARTNER.to(intent, message.getConversationID() != null ? message.getConversationID() : message.getSender());
            Extra.ID.to(intent, message.getId());
            context.sendBroadcast(intent, Manifest.permission.WRITE);

            return;
        }

        if (v.card == view || this == view) {
            Intent links = ViewUtil.createIntentForLinks(v.text);
            if (links != null) {
                context.startActivity(links);
                return;
            }

        }

    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] state = super.onCreateDrawableState(extraSpace + 1);
        if (inChoiceMode && checked) {
            mergeDrawableStates(state, STATE_CHECKED);
        }
        return state;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        getViews();
    }

    private void scheduleNextUpdate() {
        Handler handler = getHandler();
        if (handler != null) {
            handler.postDelayed(updater, TimeUnit.SECONDS.toMillis(1));
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked != this.checked) {
            this.checked = checked;
            refreshDrawableState();
        }
    }

    @Override
    public void setInChoiceMode(boolean inChoiceMode) {
        if (inChoiceMode != this.inChoiceMode) {
            this.inChoiceMode = inChoiceMode;
            Views v = getViews();
            setClickable(!inChoiceMode, v.action_location, v.action_send, v.action_burn);
            ViewUtil.setEnabled(v.action_burn, !inChoiceMode);
            v.action_location.setEnabled(!inChoiceMode);
            refreshDrawableState();
        }
    }

/*    private void setLabelAndPreview(Siren siren, TextView labelView, ImageView imageView) {

        Context context = getContext();

        labelView.setVisibility(GONE);

        Attachment attachment = AttachmentUtils.getAttachment(context, siren);
        Bitmap bitmap = AttachmentUtils.getPreviewImage(context, siren, attachment);
        String contentType = AttachmentUtils.getContentType(siren, attachment);

        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(bitmap != null ? VISIBLE : GONE);

        if (bitmap != null) {
            String duration = AttachmentUtils.getLabelForDuration(siren.getMediaDuration());
            if (duration != null) {
                labelView.setText(duration);
                labelView.setVisibility(VISIBLE);
                ViewUtils.setDrawableStart(labelView, AttachmentUtils.getAttachmentLabelIcon(contentType));
            }
            return;
        }

        labelView.setVisibility(VISIBLE);
        ViewUtils.setDrawableStart(labelView, AttachmentUtils.getAttachmentLabelIcon(contentType));

        String label = AttachmentUtils.getLabelForDuration(siren.getMediaDuration());

        if (label == null && attachment != null && attachment.getName() != null) {
            label = new String(attachment.getName());
        }

        if (label == null && contentType != null) {
            label = contentType;
        }

        if (label == null) {
            labelView.setText(R.string.attachment);
        } else {
            labelView.setText(new String(label));
        }

    }*/

    public void setMessage(Message message) {

        setVisibility(VISIBLE);

        Views v = getViews();

        /* TODO: calling this very often causes OOM.
         * Avatar is not shown, do not try to set image for it.
        v.avatar.setContact(message.getSender());
         */

        int previewVisibility = message.hasAttachment() ? VISIBLE : GONE;
        //v.preview.setVisibility(previewVisibility);
        v.attachment_text.setVisibility(previewVisibility);

        restoreViews(message instanceof IncomingMessage);
        setStatusAndTime(message, v.time);
        updateBurnNotice();
        toggleEnabledState(message);
        // toggleDeliveredState(message, v.delivered);
        toggleSendActionVisibility(message, v.action_send);

        if (message.hasAttachment() || message.hasMetaData()) {
            setAttachmentInfo(message.getMetaData());
        }
        else {
            setText(message.getText());
        }

        setLocation(message.getLocation());

        // debug information for message
        v.message_state.setText(
                getResources().getString(MessageStates.messageStateToStringId(message.getState()))
                        + " " + sDebugDateFormatter.format(message.getTime()));

        updateView();
    }

    /**
     * Restore visibility of possibly hidden views.
     */
    public void restoreViews(boolean incoming) {
        Views v = getViews();
        v.setInformationViewVisibility(View.VISIBLE);
        v.card.setBackgroundResource(incoming ? R.drawable.bg_card_light : R.drawable.bg_my_card_light);
    }

    public void setContact(ContactEntry contactEntry) {
        Views v = getViews();
        v.avatar.setContact(contactEntry);
    }

    public void setText(final String text) {
        Views v = getViews();
        v.text.setText(text);
        v.preview.setVisibility(GONE);
        v.preview_icon.setVisibility(GONE);
        v.preview_progress.setVisibility(GONE);
        v.attachment_text.setVisibility(GONE);
        Linkify.addLinks(v.text, Linkify.ALL);
        v.text.setMovementMethod(null);
        v.text.setVisibility(VISIBLE);
        ViewUtil.setDrawableStart(v.text, 0);
    }

    public void setAttachmentInfo(String metaData) {
        Views v = getViews();

        v.preview.setVisibility(INVISIBLE);
        v.preview_icon.setVisibility(INVISIBLE);
        v.preview_progress.setVisibility(VISIBLE);
        v.text.setVisibility(GONE);

        String fileName = null; // Used as the name of the attachment
        String displayName = null; // Sometimes used to replace displaying fileName

        if(metaData != null) {
            JSONObject metaDataJson;

            try {
                metaDataJson = new JSONObject(metaData);
            } catch (JSONException exception) {
                return;
            }

            String preview = null;
            String mimeType = null;

            try {
                preview = metaDataJson.getString("preview");
                fileName = metaDataJson.getString("FileName");
                mimeType = metaDataJson.getString("MimeType");
                displayName = metaDataJson.getString("DisplayName");
            } catch (JSONException ignore) {
            }

            int iconResource = AttachmentUtils.getPreviewIcon(mimeType);
            if (!TextUtils.isEmpty(preview) && MIME.isContact(mimeType)) {
                Bitmap previewBitmap = AttachmentUtils.getPreviewImage("image/jpg", preview, 0);

                v.preview_icon.setVisibility(VISIBLE);
                v.preview_icon.setImageBitmap(previewBitmap);
                v.preview_progress.setVisibility(GONE);
                v.preview.setVisibility(GONE);
            } else if(iconResource != 0) {
                v.preview_icon.setImageResource(iconResource);
                v.preview_icon.setVisibility(VISIBLE);
                v.preview_progress.setVisibility(GONE);
            } else if (!TextUtils.isEmpty(preview)){
                Bitmap previewBitmap = AttachmentUtils.getPreviewImage("image/jpg", preview, 0);

                v.preview.setVisibility(VISIBLE);
                v.preview.setImageBitmap(previewBitmap);
                v.preview_progress.setVisibility(GONE);
            } else {
                v.preview.setVisibility(GONE);
                v.preview_icon.setVisibility(GONE);
                v.preview_progress.setVisibility(GONE);
            }
        }

        if(!TextUtils.isEmpty(displayName)) {
            v.attachment_text.setText(displayName);
        } else if(!TextUtils.isEmpty(fileName)) {
            v.attachment_text.setText(fileName);
        } else {
            v.attachment_text.setText(R.string.attachment);
        }

        v.attachment_text.setVisibility(VISIBLE);
    }

/*    public void setSiren(Siren siren) {

        if (siren == null) {
            return;
        }

        Views v = getViews();

        v.action_location.setVisibility(siren.getLocation() != null ? VISIBLE : GONE);

        if (siren.hasAttachments()) {
            if (siren.isVoicemail()) {
                setVoicemailLabelAndPreview(siren, v.text, v.preview);
            } else {
                setLabelAndPreview(siren, v.text, v.preview);
            }

            if (Constants.isRTL()) {
                showRTLLanguage(v);
            }
            return;
        }

        String chatMessage = siren.getChatMessage();

        if (chatMessage != null) {
            v.preview.setVisibility(GONE);
            v.text.setText(chatMessage);
            Linkify.addLinks(v.text, Linkify.ALL);
            v.text.setMovementMethod(null);
            v.text.setVisibility(VISIBLE);
            ViewUtils.setDrawableStart(v.text, 0);

            if (Constants.isRTL()) {
                showRTLLanguage(v);
            }
            return;
        }

        setVisibility(GONE);

    }*/

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof Message) {
            setMessage((Message) tag);
        }
    }

    private void setStatusAndTime(Message message, TextView time) {
        String statusAndTime;

        if(message instanceof IncomingMessage) {
            statusAndTime = DateUtils.getRelativeTimeSpanString(getContext(), message.getTime()).toString();
        } else {
            statusAndTime = String.format("%s %s",
                    getResources().getString(MessageStates.messageStateToStringId(message.getState())),
                    DateUtils.getRelativeTimeSpanString(getContext(), message.getTime()).toString());
        }

        time.setText(statusAndTime);
    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }


    @Override
    public void update() {
        updateView();
        updateBurnNotice();
        scheduleNextUpdate();
    }

    public void cancelUpdates() {
        Handler handler = getHandler();
        if (handler != null) {
            handler.removeCallbacks(updater);
        }
    }

    /**
     * Do the burn animation.
     *
     * @return Duration of the animation
     */
    public int animateBurn(final Runnable runnable) {
        final Views v = getViews();
        mBackgroundDrawable = v.card.getBackground();
        final Handler handler = getHandler();
        // hide all fields (except card view)
        v.setInformationViewVisibility(View.GONE);
        // disable view updates
        cancelUpdates();

        // set background to animated drawable
        v.card.setBackgroundResource(R.drawable.poof);
        AnimationDrawable drawable = (AnimationDrawable) v.card.getBackground();
        drawable.setOneShot(true);

        // get duration of the animation
        int duration = 0;
        for (int i = 0; i < drawable.getNumberOfFrames(); i++, duration += drawable.getDuration(i));

        // schedule actions to be performed when it finishes
        if (handler != null && runnable != null) {
            handler.postDelayed(runnable, duration);
        }

        // start the animation
        drawable.start();

        return duration;
    }

    private void toggleEnabledState(Message message) {
        boolean enabled = !(message instanceof OutgoingMessage)
                || MessageStates.SENT_TO_SERVER == message.getState()
                || MessageStates.DELIVERED == message.getState()
                || MessageStates.READ == message.getState();

        // TODO: set alpha according to message status
        // TODO: handle MessageStates.RESEND_REQUESTED
        ViewUtil.setAlpha(getViews().card, enabled ? 1 : 0.5f);
    }

    private void updateView() {
        Message message = getMessage();
        Views v = getViews();

        int textColor = getResources().getColor(R.color.theme_light_text_primary);
        if (message instanceof OutgoingMessage) {
            switch (message.getState()) {
                case MessageStates.UNKNOWN:
                case MessageStates.FAILED:
                case MessageStates.COMPOSING:
                case MessageStates.COMPOSED:
                case MessageStates.SENT:
                case MessageStates.SENT_TO_SERVER:
                    textColor = getResources().getColor(R.color.chat_outgoing_message_disabled_text_color);
                    break;
                case MessageStates.DELIVERED:
                case MessageStates.READ:
                case MessageStates.SYNC:
                    textColor = getResources().getColor(R.color.chat_outgoing_message_text_color);
                    break;
                default:
                    break;
            }
        }
        v.text.setTextColor(textColor);
        v.attachment_text.setTextColor(textColor);

        v.action_location.setVisibility(mLocation != null ? VISIBLE : GONE);
    }

    private void updateBurnNotice() {

        Message message = getMessage();
        Views v = getViews();
        Context context = getContext();

        if (message == null || v == null || context == null) {
            return;
        }

        if (message.expires()) {
            v.burn_notice.setVisibility(VISIBLE);
            v.action_burn.setVisibility(VISIBLE);
            ViewUtil.setEnabled(v.action_burn, !isInChoiceMode());

            if (message.getState() == MessageStates.READ) {
                long millisecondsToExpiry = message.getExpirationTime() - System.currentTimeMillis();
                v.burn_notice.setText(DateUtils.getShortTimeString(context, millisecondsToExpiry));
                /*
                 * show burn animation for message if less than one second is left till expiry
                 */
                if (millisecondsToExpiry <= TimeUnit.SECONDS.toMillis(1)
                        && MessagingPreferences.getInstance(getContext()).getShowBurnAnimation()) {
                    animateBurn(null);
                }
            } else {
                v.burn_notice.setText(DateUtils.getShortTimeString(context,
                        TimeUnit.SECONDS.toMillis(message.getBurnNotice())));
            }
        } else {
            v.burn_notice.setVisibility(GONE);
            v.action_burn.setVisibility(GONE);
        }
    }

    private void setLocation(final com.silentcircle.messaging.model.Location location) {
        mLocation = LocationUtils.messageLocationToLocation(location);
    }

    private void burnMessage(final Message message) {

        Runnable burnRunnable = new Runnable() {
            @Override
            public void run() {
                MessageUtils.burnMessage(getContext().getApplicationContext(), message);
            }
        };

        /*
         * If should show burn animation, show animation and then burn message,
         * otherwise just burn the message.
         *
         * Call MessageUtils.burnMessage will also refresh the list view with messages.
         */
        if (MessagingPreferences.getInstance(getContext()).getShowBurnAnimation()) {
            animateBurn(burnRunnable);
        }
        else {
            MessageUtils.burnMessage(getContext().getApplicationContext(), message);
        }

        /* TODO: using delegate which calls conversation activity's performaAction would be nicer */
    }

    private void setCardBackground(final Drawable drawable) {
        Views v = getViews();
        if (drawable != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                v.card.setBackgroundDrawable(drawable);
            } else {
                v.card.setBackground(drawable);
            }
        }
        v.card.setVisibility(View.VISIBLE);
    }
}
