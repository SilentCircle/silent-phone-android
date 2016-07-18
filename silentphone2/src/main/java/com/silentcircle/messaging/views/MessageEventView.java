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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.thread.Updater;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.messaging.util.Updatable;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;
import com.silentcircle.silentphone2.Manifest;
import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MessageEventView extends RelativeLayout implements Updatable, Checkable, HasChoiceMode, OnClickListener {

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

        public final QuickContactBadge avatar;
        public final LinearLayout card;
        public final TextView attachment_text;
        public final ImageView attachment_status;
        public final ImageView preview;
        public final ImageView preview_icon;
        public final ProgressBar preview_progress;
        public final TextView text;
        public final TextView time;
        public final TextView burn_notice;
        public final View action_location;
        public final ViewGroup action_burn;
        public final View action_send;
        public final TextView message_state;
        public final RelativeLayout message_actions;

        public final View[] clickable_views;

        public Views(MessageEventView parent) {

            avatar = (QuickContactBadge) parent.findViewById(R.id.message_avatar);
            card = (LinearLayout) parent.findViewById(R.id.message_card);
            attachment_text = (TextView) parent.findViewById(R.id.message_attachment_text);
            attachment_status = (ImageView) parent.findViewById(R.id.message_attachment_status);
            preview = (ImageView) parent.findViewById(R.id.message_preview);
            preview_icon = (ImageView) parent.findViewById(R.id.message_icon);
            preview_progress = (ProgressBar) parent.findViewById(R.id.message_preview_progress);
            text = (TextView) parent.findViewById(R.id.message_body);
            time = (TextView) parent.findViewById(R.id.message_time);
            burn_notice = (TextView) parent.findViewById(R.id.message_burn_notice);

            message_actions = (RelativeLayout) parent.findViewById(R.id.message_actions);
            action_location = parent.findViewById(R.id.message_action_location);
            action_location.setOnClickListener(parent);
            action_burn = (ViewGroup) parent.findViewById(R.id.message_action_burn);
            action_burn.setOnClickListener(parent);
            action_send = parent.findViewById(R.id.message_action_send);
            if (action_send != null) {
                action_send.setOnClickListener(parent);
            }

            message_state = (TextView) parent.findViewById(R.id.message_state);

            clickable_views = new View[]{card, action_burn, action_location, action_send};
        }

        public void setInformationViewVisibility(int visibility) {
            preview.setVisibility(visibility);
            preview_icon.setVisibility(visibility);
            preview_progress.setVisibility(visibility);
            text.setVisibility(visibility);
            time.setVisibility(visibility);
            burn_notice.setVisibility(visibility);
            message_state.setVisibility(visibility);
            action_location.setVisibility(visibility);
            action_burn.setVisibility(View.GONE);
            if (visibility == View.GONE) {
                text.setText(null);
                time.setText(null);
                burn_notice.setText(null);
                attachment_text.setVisibility(visibility);
            } else {
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

    private static ContactPhotoManagerNew sContactPhotoManager;

    static {
        sContactPhotoManager = ContactPhotoManagerNew.getInstance(SilentPhoneApplication.getAppContext());
    }

    private Views views;

    private boolean checked;

    private boolean inChoiceMode;

    private final Updater updater;

    private Location mLocation;

    private Drawable mBackgroundDrawable;

    private boolean mIsBurned = true;

    /* array to hold drawable state change */
    private final int[] mStateChange = new int[2];

    public MessageEventView(Context context) {
        super(context);
        updater = new Updater(this);
    }

    public MessageEventView(Context context, AttributeSet attrs) {
        super(context, attrs);
        updater = new Updater(this);
    }

    public MessageEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
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
        boolean shouldDispatch;

        if (inChoiceMode) {
            shouldDispatch = isEventWithinView(event, views.card);
        } else {
            shouldDispatch = isEventWithinView(event, views.clickable_views);
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
            context.sendBroadcast(intent, Manifest.permission.WRITE);

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
        if (inChoiceMode && checked) {
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

    private void scheduleNextUpdate() {
        Handler handler = getHandler();
        if (handler != null) {
            handler.removeCallbacks(updater);
            handler.postDelayed(updater, getNextUpdateTime());
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

        /* TODO: calling this very often causes OOM.
         * Avatar is not shown, do not try to set image for it.
        v.avatar.setContact(message.getSender());
         */

        int previewVisibility = message.hasAttachment() ? VISIBLE : GONE;
        //v.preview.setVisibility(previewVisibility);
        v.attachment_text.setVisibility(previewVisibility);
        v.attachment_status.setVisibility(View.GONE);

        restoreViews(message instanceof IncomingMessage);

        setStatusAndTime(message);
        updateBurnNotice();
        toggleEnabledState(message);
        toggleSendActionVisibility(message, v.action_send);

        // set text or attachment preview only if it won't be immediately replaced by burn animation
        if (message.hasAttachment() || message.hasMetaData()) {
            setAttachmentInfo(message.getMetaData());
        } else {
            setText(message.getText());
        }

        setLocation(message.getLocation());
        refreshDrawableState();
    }

    /**
     * Restore visibility of possibly hidden views.
     */
    public void restoreViews(boolean incoming) {
        Views v = getViews();
        v.setInformationViewVisibility(View.VISIBLE);
        v.avatar.setVisibility(incoming ? View.INVISIBLE : View.GONE);
        Drawable background = ContextCompat.getDrawable(getContext(),
                incoming ? R.drawable.bg_card_light_second : R.drawable.bg_my_card_light_second);

        Boolean newGroup = (Boolean) getTag(R.id.new_group_flag);
        if (newGroup != null && newGroup) {
            background = ContextCompat.getDrawable(getContext(),
                    incoming ? R.drawable.bg_card_light_default : R.drawable.bg_my_card_light_default);
        }

        int backgroundColorSelectorId = incoming
            ? R.attr.sp_incoming_message_background_selector
            : R.attr.sp_outgoing_message_background_selector;

        ColorStateList backgroundTintColor;
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(backgroundColorSelectorId, typedValue, true);
        backgroundTintColor = ContextCompat.getColorStateList(getContext(), typedValue.resourceId);

        background = DrawableCompat.wrap(background);
        DrawableCompat.setTintList(background, backgroundTintColor);
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY);
        v.card.setBackground(background);
        mBackgroundDrawable = background;
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

        if (isInEditMode()) {
            return;
        }

        Views v = getViews();

        v.preview.setVisibility(INVISIBLE);
        v.preview_icon.setVisibility(INVISIBLE);
        v.preview_progress.setVisibility(VISIBLE);
        v.text.setVisibility(GONE);

        String fileName = null; // Used as the name of the attachment
        String displayName = null; // Sometimes used to replace displaying fileName

        Message message = getMessage();
        SCloudService.AttachmentState state = MessageUtils.getAttachmentState(message);
        boolean isAttachmentFailed = (state == SCloudService.AttachmentState.NOT_AVAILABLE
                || state == SCloudService.AttachmentState.DOWNLOADING_ERROR
                || state == SCloudService.AttachmentState.UPLOADING_ERROR);
        int attachmentStatusVisibility = isAttachmentFailed ? View.VISIBLE : View.GONE;

        if (metaData != null) {
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
                v.attachment_status.setVisibility(attachmentStatusVisibility);
            } else if (iconResource != 0) {
                v.preview_icon.setImageResource(iconResource);
                v.preview_icon.setVisibility(VISIBLE);
                v.preview_progress.setVisibility(GONE);
                v.attachment_status.setVisibility(attachmentStatusVisibility);
            } else if (!TextUtils.isEmpty(preview)) {
                Bitmap previewBitmap = AttachmentUtils.getPreviewImage("image/jpg", preview, 0);

                v.preview.setVisibility(VISIBLE);
                v.preview.setImageBitmap(previewBitmap);
                v.preview_progress.setVisibility(GONE);
                v.attachment_status.setVisibility(attachmentStatusVisibility);
            } else {
                v.preview.setVisibility(GONE);
                v.preview_icon.setVisibility(GONE);
                v.preview_progress.setVisibility(GONE);
            }
        } else if (isAttachmentFailed) {
            v.preview_icon.setImageResource((message instanceof IncomingMessage)
                    ? R.drawable.ic_received_attachment_failed
                    : R.drawable.ic_sent_attachment_failed);
            v.preview_icon.setVisibility(VISIBLE);
            v.preview_progress.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(displayName)) {
            v.attachment_text.setText(displayName);
        } else if (!TextUtils.isEmpty(fileName)) {
            v.attachment_text.setText(fileName);
        } else {
            v.attachment_text.setText(R.string.attachment);
        }

        v.attachment_text.setVisibility(VISIBLE);
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof Message) {
            setMessage((Message) tag);
        }
    }

    private void setStatusAndTime(Message message) {
        Views v = getViews();

        /*
         * Instead of DateUtils.getRelativeTimeSpanString(getContext(), message.getTime()) show
         * just time for messages as date is visible in header
         */
        CharSequence statusAndTime = DateUtils.getMessageTimeFormat(message.getComposeTime());
        v.time.setText(statusAndTime);
        v.message_state.setText("");
        if (message instanceof OutgoingMessage) {
            v.message_state.setText(MessageStates.messageStateToStringId(message.getState()));
        }

    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }


    @Override
    public void update() {
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
        // mark view as transient to avoid its reuse while animation runs,
        // only do this if it will be re-set
        if (handler != null) {
            setHasTransientState(true);
        }

        // set background to animated drawable
        v.card.setBackgroundResource(R.drawable.poof);
        AnimationDrawable drawable = (AnimationDrawable) v.card.getBackground();
        drawable.setOneShot(true);

        // get duration of the animation
        int duration = 0;
        for (int i = 0; i < drawable.getNumberOfFrames(); i++, duration += drawable.getDuration(i))
            ;

        // schedule actions to be performed when it finishes
        Runnable postAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                setHasTransientState(false);
                if (runnable != null) {
                    runnable.run();
                }
            }
        };
        if (handler != null) {
            handler.postDelayed(postAnimationRunnable, duration);
        } else {
            // run the passed runnable immediately, it won't be run otherwise
            if (runnable != null) {
                runnable.run();
            }
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

        // TODO: handle MessageStates.RESEND_REQUESTED
        ViewUtil.setAlpha(getViews().card, enabled ? 1.0f : 0.5f);
    }

    private void updateBurnNotice() {

        final Message message = getMessage();
        final Context context = getContext();
        Views v = getViews();

        if (message == null || v == null || context == null) {
            return;
        }

        if (message.expires()) {
            v.burn_notice.setVisibility(VISIBLE);
            v.action_burn.setVisibility(GONE);
            ViewUtil.setEnabled(v.action_burn, !isInChoiceMode());

            if (message.getState() == MessageStates.READ) {
                long millisecondsToExpiry = message.getExpirationTime() - System.currentTimeMillis();
                CharSequence newText = DateUtils.getShortTimeString(context, millisecondsToExpiry);
                if (!newText.equals(v.burn_notice.getText())) {
                    v.burn_notice.setText(newText);
                }
                /*
                 * Show burn animation for message if it is expired
                 */
                if (isExpired(message)) {
                    animateBurn(new Runnable() {
                        @Override
                        public void run() {
                            mIsBurned = true;
                            MessageUtils.removeMessage(message);
                        }
                    });
                    /* cancel updates as well to avoid burning message multiple times */
                    cancelUpdates();
                    /* forget about the message */
                    setTag(null);
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

        Views v = getViews();
        v.action_location.setVisibility(mLocation != null ? VISIBLE : GONE);
    }

    @SuppressWarnings("deprecation")
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

    private boolean isExpired(@NonNull final Message message) {
        long millisecondsToExpiry = message.getExpirationTime() - System.currentTimeMillis();
        return (millisecondsToExpiry <= TimeUnit.SECONDS.toMillis(1));
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

    private long getNextUpdateTime() {
        long nextUpdateTime = TimeUnit.DAYS.toMillis(1);
        Message message = getMessage();
        if (message != null) {
            nextUpdateTime = message.getExpirationTime() - System.currentTimeMillis();
            if (nextUpdateTime > TimeUnit.DAYS.toMillis(1)) {
                nextUpdateTime = Math.min(nextUpdateTime % TimeUnit.HOURS.toMillis(1), TimeUnit.HOURS.toMillis(1));
            }
            else if (nextUpdateTime > TimeUnit.HOURS.toMillis(1)) {
                nextUpdateTime = Math.min(nextUpdateTime % TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(1));
            }
            else {
                nextUpdateTime = TimeUnit.SECONDS.toMillis(1);
            }
        }
        return nextUpdateTime;
    }

}
