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
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.waveform.SoundAttachmentPreview;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

import java.text.SimpleDateFormat;
import java.util.Locale;

public abstract class MessageEventView extends BaseMessageEventView implements OnClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = MessageEventView.class.getSimpleName();
    private static final SimpleDateFormat sDebugDateFormatter =
            new SimpleDateFormat("dd/MM/yy hh:mm:ss.SSS", Locale.getDefault());

    /*
     * Map of {@link com.silentcircle.messaging.model.MessageStates} to custom drawable states
     * defined in attr.xml file.
     */
    private static final int[] STATE_TO_ATTRIBUTE = new int[]{
            R.attr.state_message_unknown,
            R.attr.state_message_resend_requested,
            R.attr.state_message_composing,
            R.attr.state_message_composed,
            R.attr.state_message_sent,
            R.attr.state_message_sent_to_server,
            R.attr.state_message_delivered,
            R.attr.state_message_received,
            R.attr.state_message_read,
            R.attr.state_message_burned,
            /* take into account unused value */
            R.attr.state_message_unknown,
            R.attr.state_message_sync,
            R.attr.state_message_failed
    };

    static class Views {

        public final LinearLayout card;

        public final TextView text;
        public final TextView time;
        public final TextView burn_notice;
        public final View retention_notice;
        public final View action_location;
        public final View action_burn;
        public final View action_send;
        public final TextView message_state;
        public final RelativeLayout message_actions;
        public final TextView message_avatar_name;
        public final TextView attachment_text;

        private ViewStub attachmentViewStub;
        public boolean isAttachmentViewStubLoaded = false;
        public AttachmentViews attachmentViews = null;

        public final View[] clickable_views;

        public Views(MessageEventView parent) {
            card = (LinearLayout) parent.findViewById(R.id.message_card);
            text = (TextView) parent.findViewById(R.id.message_body);
            time = (TextView) parent.findViewById(R.id.message_time);
            burn_notice = (TextView) parent.findViewById(R.id.message_burn_notice);
            retention_notice = parent.findViewById(R.id.message_retained_notice);
            attachment_text = (TextView) parent.findViewById(R.id.message_attachment_text);
            attachmentViewStub = (ViewStub) parent.findViewById(R.id.attachment_stub_import);

            message_actions = (RelativeLayout) parent.findViewById(R.id.message_actions);
            action_location = parent.findViewById(R.id.message_action_location);
            action_location.setOnClickListener(parent);
            action_burn = parent.findViewById(R.id.message_action_burn);
            action_burn.setOnClickListener(parent);
            action_burn.setVisibility(View.GONE);

            action_send = parent.findViewById(R.id.message_action_send);
            if (action_send != null) {
                action_send.setOnClickListener(parent);
            }

            message_state = (TextView) parent.findViewById(R.id.message_state);
            message_avatar_name = (TextView) parent.findViewById(R.id.message_avatar_name);

            clickable_views = new View[]{card, action_burn, action_location, action_send};
        }

        public void loadAttachmentViews() {
            if (isAttachmentViewStubLoaded) {
                return;
            }
            isAttachmentViewStubLoaded = true;

            attachmentViews = new AttachmentViews(attachmentViewStub);
            attachmentViewStub = null;
        }

        static class AttachmentViews {
            public  final ImageView attachment_status;
            public  final SoundAttachmentPreview sound_preview;
            public  final BoundedImageView preview;
            public  final ImageView preview_icon;
            public  final View preview_play;
            public  final ProgressBar preview_progress;

            public AttachmentViews(ViewStub viewStub) {
                View parent = viewStub.inflate();

                attachment_status = (ImageView) parent.findViewById(R.id.message_attachment_status);
                sound_preview = (SoundAttachmentPreview) parent.findViewById(R.id.message_sound_preview);
                preview = (BoundedImageView) parent.findViewById(R.id.message_preview);
                preview_icon = (ImageView) parent.findViewById(R.id.message_icon);
                preview_progress = (ProgressBar) parent.findViewById(R.id.message_preview_progress);
                preview_play = parent.findViewById(R.id.message_preview_play);
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

    protected ColorStateList mColorStateListIncoming;
    protected ColorStateList mColorStateListOutgoing;
    private ColorStateList mColorStateList;

    private Views views;

    private Location mLocation;

    protected int mIncomingGroupMessageMarginStart;
    protected int mIncomingMessageMarginStart;
    protected int mIncomingGroupMessageAvatarStart;
    protected int mIncomingMessageAvatarStart;

    protected int mLoadedBackgroundId;

    /* array to hold drawable state change */
    private final int[] mStateChange = new int[2];

    public MessageEventView(Context context) {
        this(context, null);
    }

    public MessageEventView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MessageEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources resources = getResources();
        mIncomingGroupMessageMarginStart = (int) resources.getDimension(R.dimen.messaging_group_incoming_message_margin_left);
        mIncomingMessageMarginStart = (int) resources.getDimension(R.dimen.messaging_incoming_message_margin_left);
        mIncomingGroupMessageAvatarStart = (int) resources.getDimension(R.dimen.messaging_group_incoming_message_avatar_margin_left);
        mIncomingMessageAvatarStart = (int) resources.getDimension(R.dimen.messaging_incoming_message_avatar_margin_left);

        int backgroundColorSelectorId = R.attr.sp_incoming_message_background_selector;
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(backgroundColorSelectorId, typedValue, true);
        mColorStateListIncoming = ContextCompat.getColorStateList(getContext(), typedValue.resourceId);

        backgroundColorSelectorId = R.attr.sp_outgoing_message_background_selector;
        typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(backgroundColorSelectorId, typedValue, true);
        mColorStateListOutgoing = ContextCompat.getColorStateList(getContext(), typedValue.resourceId);

        mLoadedBackgroundId = (this instanceof IncomingMessageEventView) ?
                R.drawable.bg_card_light_second : R.drawable.bg_my_card_light_second;
    }

    public Message getMessage() {
        Object tag = getTag();
        return tag instanceof Message ? (Message) tag : null;
    }

    @Override
    public void setBurnNotice(@Nullable CharSequence text, int visibility) {
        Views v = getViews();
        if (v.burn_notice.getVisibility() != visibility) {
            v.burn_notice.setVisibility(visibility);
        }
        if (text != null && !text.equals(v.burn_notice.getText())) {
            v.burn_notice.setText(text);
        }
    }

    private Views getViews() {
        if (views == null) {
            views = new Views(this);
        }
        return views;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // setCardBackground(mBackgroundDrawable);
    }

    /* Andris' patch file, modified by Rong
     * These should be accessed only from UI thread.
     * Defined as member variables to avoid extensive garbage collection when many touch events
     * are dispatched.
     */
    private Rect mHitRectangle = new Rect();
    private int[] mScreenLocation = new int[2];

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Views views = getViews();
        boolean shouldDispatch = true;

        /*
         * Only ACTION_DOWN is necessary, skipping other events would lead to
         * drawables not updating to proper state.
         */
        if (MotionEvent.ACTION_DOWN == event.getAction()) {
            if (isInChoiceMode()) {
                shouldDispatch = isEventWithinView(event, views.card);
            } else {
                shouldDispatch = isEventWithinView(event, views.clickable_views);
            }
        }

        // shouldDispatch ? super.dispatchTouchEvent(event) : true;
        return !shouldDispatch || super.dispatchTouchEvent(event);
    }

    @Override
    public void onClick(View view) {
        Views v = getViews();
        final Message message = getMessage();
        Context context = view.getContext();
        if (isInChoiceMode()) {
            return;
        }

        if (message == null) {
            return;
        }

        if (v.action_location == view && mLocation != null) {
            LocationUtils.viewLocation(context, mLocation.getLatitude(), mLocation.getLongitude());
            return;
        }

        if (v.action_burn == view && message.expires()) {
            SoundNotifications.playBurnMessageSound();
            MessageUtils.burnMessage(context.getApplicationContext(), message);
            return;
        }
        /* This functionality is currently not used
        if (v.action_send == view) {
            if (message.getState() == MessageStates.RESEND_REQUESTED) {
                AxoMessaging.getInstance(context.getApplicationContext()).sendMessage(message);
                return;
            }
            Intent intent = Action.TRANSITION.intent();
            Extra.PARTNER.to(intent, message.getConversationID());
            Extra.ID.to(intent, message.getId());
            Extra.STATE.to(intent, MessageStates.COMPOSED);
            context.sendBroadcast(intent, Manifest.permission.WRITE);
            return;
        }
         */
        if (message.getAttachment() != null) {
            Intent intent = Action.DOWNLOAD.intent();
            Extra.PARTNER.to(intent, message.getConversationID() != null ? message.getConversationID() : message.getSender());
            Extra.ID.to(intent, message.getId());
            MessagingBroadcastManager.getInstance(context).sendBroadcast(intent);

            return;
        }

        if (v.card == view || this == view) {
            if (ViewUtil.startActivityForTextLinks(context, v.text)) {
                return;
            }
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        mStateChange[0] = 0;
        mStateChange[1] = 0;
        int position = 0;

        final int[] state = super.onCreateDrawableState(extraSpace + 2);
        if (isInChoiceMode() && isChecked()) {
            mStateChange[position++] = android.R.attr.state_checked;
        }

        /* add custom drawable state from message state */
        Message message = getMessage();
        if (message != null) {
            int messageState = message.getState();
            mStateChange[position] = messageState >= 0 && messageState < STATE_TO_ATTRIBUTE.length
                    ? STATE_TO_ATTRIBUTE[messageState] : R.attr.state_message_unknown;
        }

        mergeDrawableStates(state, mStateChange);
        return state;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        getViews();
    }

    @Override
    public void setInChoiceMode(boolean inChoiceMode) {
        if (inChoiceMode != isInChoiceMode()) {
            super.setInChoiceMode(inChoiceMode);
            Views v = getViews();
            setClickable(!inChoiceMode, v.action_location, v.action_send, v.action_burn);
            v.action_burn.setEnabled(!inChoiceMode);
            v.action_location.setEnabled(!inChoiceMode);
            refreshDrawableState();
        }
    }

    public void setMessage(Message message) {

        if (isInEditMode()) {
            return;
        }

        boolean isExpired =  isExpired(message);
        setVisibility(isExpired ? GONE : VISIBLE);

        if (isExpired) {
            /* Do not try to set up any views. This message should be removed from list. */
            return;
        }

        Views v = getViews();

        int previewVisibility = message.hasAttachment() ? VISIBLE : GONE;
        //v.preview.setVisibility(previewVisibility);
        v.attachment_text.setVisibility(previewVisibility);

        if (views.isAttachmentViewStubLoaded) {
            v.attachmentViews.attachment_status.setVisibility(View.GONE);
        }

        boolean isIncomingMessage = message instanceof IncomingMessage;
        boolean isGroupMessage = isGroupMessage();
        boolean isFirstMessage = isFirstMessage();
        boolean isNewDate = isNewDate();
        restoreViews(isIncomingMessage, isFirstMessage,
                isGroupMessage, isNewDate, message.isRetained());

        setStatusAndTime(message, v, isGroupMessage);
        updateBurnNotice();
        toggleEnabledState(message);

        // set text or attachment preview only if it won't be immediately replaced by burn animation
        if (message.hasAttachment() || message.hasMetaData()) {
            setAttachmentInfo(message.getMetaData());
        } else {
            setText(v, message.getText());
        }
        setSenderName(v, isIncomingMessage && isGroupMessage && isFirstMessage ? getSenderName() : "");
        setLocation(message.getLocation());

        adjustMinimumWidth();

        // necessary so that re-used view won't keep previous message's state
        refreshDrawableState();
    }

    /**
     * Restore visibility of possibly hidden views.
     */
    public void restoreViews(boolean isIncoming, boolean isFirstMessage, boolean isGroupMessage,
            boolean isNewDate, boolean isRetained) {
        Views v = getViews();
        mColorStateList = isIncoming ? mColorStateListIncoming : mColorStateListOutgoing;

        int backgroundId = isIncoming
                ? R.drawable.bg_card_light_second : R.drawable.bg_my_card_light_second;

        if (isFirstMessage) {
            backgroundId = isIncoming
                    ? R.drawable.bg_card_light_default : R.drawable.bg_my_card_light_default;
        }

        Drawable background = (mLoadedBackgroundId == backgroundId) ?
                v.card.getBackground() : ContextCompat.getDrawable(getContext(), backgroundId);
        mLoadedBackgroundId = backgroundId;
        background = DrawableCompat.wrap(background);
        DrawableCompat.setTintList(background, mColorStateList);
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY);
        v.card.setBackground(background);

        setAttachmentIndicator(isIncoming, v);

        if (isIncoming) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) v.card.getLayoutParams();
            params.leftMargin = isGroupMessage ? mIncomingGroupMessageMarginStart : mIncomingMessageMarginStart;

            params = (RelativeLayout.LayoutParams) v.message_avatar_name.getLayoutParams();
            params.leftMargin = isGroupMessage ? mIncomingGroupMessageAvatarStart : mIncomingMessageAvatarStart;
        }
        v.retention_notice.setVisibility(isRetained ? View.VISIBLE : View.GONE);
        v.message_avatar_name.setVisibility((isIncoming && isFirstMessage && isGroupMessage) ? View.VISIBLE : View.GONE);
    }

    public void setText(Views views, final String text) {
        CharSequence previousText = views.text.getText();
        if (text != previousText || (text != null && !text.equals(previousText))) {
            views.text.setText(text);
            try {
                Linkify.addLinks(views.text, Linkify.ALL);
            } catch (Throwable t) {
                Log.w(TAG, "Unable to linkify message text");
                t.printStackTrace();
                /*
                 * Ignore failure to linkify text to avoid crash due to missing WebView component
                 * on some devices.
                 * Could not reproduce the crash from https://sentry.silentcircle.org/sentry/spa/issues/5879/
                 */
                views.text.setText(text);
            }

            views.attachment_text.setVisibility(GONE);

            if (views.isAttachmentViewStubLoaded) {
                views.attachmentViews.preview.setVisibility(GONE);
                views.attachmentViews.preview_icon.setVisibility(GONE);
                views.attachmentViews.preview_progress.setVisibility(GONE);
                views.attachmentViews.preview_play.setVisibility(GONE);
                views.attachmentViews.sound_preview.setVisibility(GONE);
            }

            views.text.setMovementMethod(null);
            views.text.setVisibility(VISIBLE);
            ViewUtil.setDrawableStart(views.text, 0);
        }
    }

    public void setAttachmentInfo(String metaData) {

        if (isInEditMode()) {
            return;
        }

        Views v = getViews();

        if (!v.isAttachmentViewStubLoaded) {
            v.loadAttachmentViews();
        }

        v.attachmentViews.sound_preview.setVisibility(GONE);
        v.attachmentViews.preview.setVisibility(GONE);
        v.attachmentViews.preview.setIsImage(false);
        v.attachmentViews.preview_icon.setVisibility(INVISIBLE);
        v.attachmentViews.preview_progress.setVisibility(VISIBLE);
        v.attachmentViews.preview_play.setVisibility(GONE);
        v.text.setVisibility(GONE);

        if (metaData == null) {
            v.attachmentViews.preview_icon.setVisibility(GONE);
            v.attachment_text.setVisibility(GONE);
        }

        String fileName = null; // Used as the name of the attachment
        String displayName = null; // Sometimes used to replace displaying fileName

        Message message = getMessage();
        SCloudService.AttachmentState state = MessageUtils.getAttachmentState(message);
        boolean isAttachmentFailed = (state == SCloudService.AttachmentState.NOT_AVAILABLE
                || state == SCloudService.AttachmentState.DOWNLOADING_ERROR
                || state == SCloudService.AttachmentState.UPLOADING_ERROR);
        int attachmentStatusVisibility = isAttachmentFailed ? View.VISIBLE : View.GONE;
        boolean isVisualOrAudio = false;

        if (metaData != null) {
            AttachmentUtils.MetaData parsedMetaData = AttachmentUtils.MetaData.parse(metaData);
            String preview = parsedMetaData.preview;
            String mimeType = parsedMetaData.mimeType;
            String waveform = parsedMetaData.waveform;
            String duration = parsedMetaData.duration;

            fileName = parsedMetaData.fileName;
            displayName = parsedMetaData.displayName;

            isVisualOrAudio = (MIME.isAudio(mimeType) || MIME.isVideo(mimeType) || MIME.isImage(mimeType));

            int iconResource = AttachmentUtils.getPreviewIcon(mimeType);
            if (!TextUtils.isEmpty(preview) && MIME.isContact(mimeType)) {
                Bitmap previewBitmap = AttachmentUtils.getPreviewImage("image/jpg", preview, 0);

                v.attachmentViews.preview_icon.setVisibility(VISIBLE);
                v.attachmentViews.preview_icon.setImageBitmap(previewBitmap);
                v.attachmentViews.preview_progress.setVisibility(GONE);
                v.attachmentViews.preview.setVisibility(GONE);
                v.attachmentViews.attachment_status.setVisibility(attachmentStatusVisibility);
            }
            else if (isVisualOrAudio && waveform != null && duration != null) {
                float[] levels = AttachmentUtils.getDBLevelsFromBase64(waveform);
                long durationMS = AttachmentUtils.getDurationFromString(duration);
                v.attachmentViews.sound_preview.setSoundData(levels, durationMS);
                v.attachmentViews.sound_preview.setVisibility(VISIBLE);
                v.attachmentViews.preview.setVisibility(GONE);
                v.attachmentViews.preview_icon.setVisibility(GONE);
                v.attachmentViews.preview_progress.setVisibility(GONE);
                v.attachmentViews.attachment_status.setVisibility(attachmentStatusVisibility);
            }
            else if (iconResource != 0) {
                v.attachmentViews.preview_icon.setImageResource(iconResource);
                v.attachmentViews.preview_icon.setVisibility(VISIBLE);
                v.attachmentViews.preview_progress.setVisibility(GONE);
                v.attachmentViews.attachment_status.setVisibility(attachmentStatusVisibility);
                if (MIME.isAudio(mimeType) || MIME.isVideo(mimeType)) {
                    v.attachmentViews.preview_play.setVisibility(VISIBLE);
                }
            } else if (!TextUtils.isEmpty(preview)) {
                Bitmap previewBitmap = AttachmentUtils.getPreviewImage("image/jpg", preview, 0);

                v.attachmentViews.preview.setVisibility(VISIBLE);
                v.attachmentViews.preview.setImageBitmap(previewBitmap);
                v.attachmentViews.preview.setIsImage(MIME.isImage(mimeType) || MIME.isVideo(mimeType));
                v.attachmentViews.preview.setCornerColor(mColorStateList);
                v.attachmentViews.preview_progress.setVisibility(GONE);
                v.attachmentViews.attachment_status.setVisibility(attachmentStatusVisibility);
                if (MIME.isAudio(mimeType) || MIME.isVideo(mimeType)) {
                    v.attachmentViews.preview_play.setVisibility(VISIBLE);
                }
            } else {
                v.attachmentViews.preview.setVisibility(GONE);
                v.attachmentViews.preview_icon.setVisibility(GONE);
                v.attachmentViews.preview_progress.setVisibility(GONE);
            }
        } else if (isAttachmentFailed) {
            v.attachmentViews.preview_icon.setImageResource((message instanceof IncomingMessage)
                    ? R.drawable.ic_received_attachment_failed
                    : R.drawable.ic_sent_attachment_failed);
            v.attachmentViews.preview_icon.setVisibility(VISIBLE);
            v.attachmentViews.preview_progress.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(displayName)) {
            v.attachment_text.setText(displayName);
        } else if (!TextUtils.isEmpty(fileName)) {
            v.attachment_text.setText(fileName);
        } else {
            v.attachment_text.setText(R.string.attachment);
        }

        if(metaData != null) {
            v.attachment_text.setVisibility(isVisualOrAudio ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof Message) {
            setMessage((Message) tag);
        }
    }

    private void setStatusAndTime(Message message, Views views, boolean isGroupMessage) {
        /*
         * Instead of DateUtils.getRelativeTimeSpanString(getContext(), message.getTime()) show
         * just time for messages as date is visible in header.
         */
        CharSequence statusAndTime = DateUtils.getMessageTimeFormat(message.getComposeTime());
        views.time.setText(statusAndTime);

        /*
         * Status is shown for outgoing messages only in production builds.
         * In debug builds for debug purposes status is shown for incoming messages if
         * "Show all errors" is set in settings.
         *
         * There is no status for group conversation messages.
         */
        views.message_state.setVisibility(isGroupMessage ? View.GONE : View.VISIBLE);
        if (message instanceof OutgoingMessage || (BuildConfig.DEBUG && DialerActivity.mShowErrors)) {
            views.message_state.setText(MessageStates.messageStateToStringId(message.getState()));
        }
        else if (!"".equals(views.message_state.getText())) {
            views.message_state.setText("");
        }
    }

    private void setSenderName(Views views, @Nullable final CharSequence displayName) {
        CharSequence previousName = views.message_avatar_name.getText();
        if (displayName != previousName || (displayName != null && !displayName.equals(previousName))) {
            views.message_avatar_name.setText(displayName);
        }
    }

    private void toggleEnabledState(Message message) {
        int state = message.getState();
        boolean enabled = !(message instanceof OutgoingMessage)
                || MessageStates.SENT_TO_SERVER == state
                || MessageStates.DELIVERED == state
                || MessageStates.READ == state;

        // TODO: handle MessageStates.RESEND_REQUESTED
        ViewUtil.setAlpha(getViews().card, enabled ? 1.0f : 0.65f);
    }

    private void setLocation(final com.silentcircle.messaging.model.Location location) {
        mLocation = LocationUtils.messageLocationToLocation(location);

        Views v = getViews();
        v.action_location.setVisibility(mLocation != null ? VISIBLE : GONE);
    }

    private boolean isEventWithinView(@NonNull MotionEvent event, @NonNull View... views) {
        boolean result = false;
        for (View view : views) {
            if (view == null) {
                continue;
            }
            view.getHitRect(mHitRectangle);
            view.getLocationOnScreen(mScreenLocation);
            mHitRectangle.offset(mScreenLocation[0] - view.getLeft(), mScreenLocation[1] - view.getTop());
            result |= mHitRectangle.contains((int) event.getRawX(), (int) event.getRawY());
        }
        return result;
    }

    private Drawable mAttachmentDrawable = null;

    private void setAttachmentIndicator(boolean incoming, Views v) {
        if (mAttachmentDrawable == null) {
            mAttachmentDrawable = ContextCompat.getDrawable(getContext(),
                    R.drawable.ic_action_attachment_light);
            int textColorSelectorId = incoming
                    ? R.attr.sp_incoming_message_text_selector
                    : R.attr.sp_outgoing_message_text_selector;
            ColorStateList textTintColor;
            TypedValue typedValue = new TypedValue();
            getContext().getTheme().resolveAttribute(textColorSelectorId, typedValue, true);
            textTintColor = ContextCompat.getColorStateList(getContext(), typedValue.resourceId);

            mAttachmentDrawable = DrawableCompat.wrap(mAttachmentDrawable);
            DrawableCompat.setTintList(mAttachmentDrawable, textTintColor);
            DrawableCompat.setTintMode(mAttachmentDrawable, PorterDuff.Mode.MULTIPLY);
        }

        v.attachment_text.setCompoundDrawablesWithIntrinsicBounds(mAttachmentDrawable, null, null, null);
    }

    private boolean isGroupMessage() {
        Boolean isGroupMessage = (Boolean) getTag(R.id.group_conversation_flag);
        return isGroupMessage == null ? false : isGroupMessage;
    }

    private boolean isFirstMessage() {
        Boolean newGroup = (Boolean) getTag(R.id.new_group_flag);
        return newGroup != null && newGroup;
    }

    private boolean isNewDate() {
        Boolean newGroup = (Boolean) getTag(R.id.new_date_flag);
        return newGroup != null && newGroup;
    }

    private CharSequence getSenderName() {
        return (CharSequence) getTag(R.id.sender_display_name);
    }

    private void adjustMinimumWidth() {
        Views v = getViews();
        Resources resources = getResources();
        boolean isIncoming = getMessage() instanceof IncomingMessage;
        int minimumWidth = Math.max((int) resources.getDimension(R.dimen.messaging_message_card_min_width),
                (isIncoming
                        ? (int) resources.getDimension(R.dimen.messaging_message_time_margin_left)
                        : (int) resources.getDimension(R.dimen.messaging_message_time_margin_right))
                + (int) resources.getDimension(R.dimen.spacing_small)
                + v.retention_notice.getMeasuredWidth()
                + v.burn_notice.getMeasuredWidth()
                + v.message_state.getMeasuredWidth());
        if (v.card.getMinimumWidth() != minimumWidth) {
            v.card.setMinimumWidth(minimumWidth);
        }
    }

}
