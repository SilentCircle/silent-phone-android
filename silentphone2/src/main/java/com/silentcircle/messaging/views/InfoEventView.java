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
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.content.res.AppCompatResources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;
import com.silentcircle.silentphone2.R;
import com.silentcircle.userinfo.LoadUserInfo;

/**
 * View to display information event in chat.
 */
public class InfoEventView extends CheckableRelativeLayout implements View.OnClickListener, HasChoiceMode {

    private ImageView mIcon;
    private QuickContactBadge mBadge;
    private View mContainer;
    private TextView mText;
    private Event mInfoEvent;
    private boolean mInChoiceMode;

    private static Typeface sSemiboldTypeFace =
            Typeface.create(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? "sans-serif-light"
                    : "sans-serif-medium", Typeface.BOLD);

    public InfoEventView(Context context) {
        this(context, null);
    }

    public InfoEventView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContainer = findViewById(R.id.info_container);
        mIcon = (ImageView) findViewById(R.id.info_icon);
        mBadge = (QuickContactBadge) findViewById(R.id.info_badge);
        mText = (TextView) findViewById(R.id.info_message);
        mText.setTypeface(sSemiboldTypeFace);
        setBackground();
    }

    @Override
    public void onClick(View view) {
        /* show event information on click */
        if (mInfoEvent != null) {
            Context context = getContext();
            Intent intent = ContactsUtils.getMessagingIntent(mInfoEvent.getConversationID(), context);
            Extra.TASK.to(intent, ConversationActivity.TASK_SHOW_EVENT_INFO);
            Extra.ID.to(intent, mInfoEvent.getId());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        }
    }

    @Override
    public boolean isInChoiceMode() {
        return mInChoiceMode;
    }

    @Override
    public void setInChoiceMode(boolean inChoiceMode) {
        if (inChoiceMode != mInChoiceMode) {
            mInChoiceMode = inChoiceMode;
        }
    }

    @Override
    public void setChecked(boolean checked) {
        // allow to change checked state only in action mode
        if (checked && !isInChoiceMode()) {
            return;
        }
        super.setChecked(checked);
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof Event) {
            setEvent((Event) tag);
        }
    }

    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
        mIcon.invalidate();
    }

    public void setEvent(Event infoEvent) {
        mInfoEvent = infoEvent;
        if (mInfoEvent instanceof InfoEvent) {
            setText(getLocalizedText(getContext(), (InfoEvent) mInfoEvent));
            setIcon(getInfoIcon((InfoEvent) mInfoEvent));
        } else {
            setText(infoEvent.getText());
            setIcon(R.drawable.ic_info_group_changed);
        }
    }

    public void setText(CharSequence text) {
        mText.setText(text);
    }

    public void setIcon(int resourceId) {
        if (resourceId != 0) {
            mBadge.setVisibility(View.INVISIBLE);
            mIcon.setVisibility(View.VISIBLE);
            mIcon.setImageResource(resourceId);
        }
    }

    public void setIcon(ContactEntry contactEntry) {
        if (contactEntry != null) {
            mBadge.setVisibility(View.VISIBLE);
            mIcon.setVisibility(View.INVISIBLE);
            AvatarUtils.setPhoto(ContactPhotoManagerNew.getInstance(getContext()), mBadge,
                contactEntry, true);
        }
        else {
            setIcon(R.drawable.ic_info_group_changed);
        }
    }

    private void setBackground() {
        Drawable background = ContextCompat.getDrawable(getContext(), R.drawable.bg_white);
        int backgroundSelector = R.color.info_message_background_selector_light;
        ColorStateList backgroundTintColor = AppCompatResources.getColorStateList(getContext(),
                backgroundSelector);
        background = DrawableCompat.wrap(background);
        DrawableCompat.setTintList(background, backgroundTintColor);
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY);

        mContainer.setBackground(background);
    }

    private int getInfoIcon(final @NonNull InfoEvent infoEvent) {
        int result;
        switch (infoEvent.getTag()) {
            case InfoEvent.INFO_INVITE_USER:
            case InfoEvent.INFO_INVITE_USER_FAILED:
            case InfoEvent.INFO_RESPONSE_HELLO:
            case InfoEvent.INFO_INVITE_RESPONSE_ACCEPTED:
            case InfoEvent.INFO_INVITE_RESPONSE_DECLINED:
                ContactEntry contactEntry = getContactEntry(infoEvent);
                setIcon(contactEntry);
                result = 0;
                break;
            case InfoEvent.INFO_USER_LEFT:
                result = R.drawable.ic_info_users_changed;
                break;
            case InfoEvent.INFO_INVITE_RESPONSE_SELF_ACCEPTED:
            case InfoEvent.INFO_INVITE_RESPONSE_SELF_DECLINED:
            case InfoEvent.INFO_AVATAR_REMOVED:
            case InfoEvent.INFO_NEW_GROUP:
                result = R.drawable.ic_info_group_changed;
                break;
            case InfoEvent.INFO_NEW_AVATAR:
                result = R.drawable.ic_info_avatar_changed;
                break;
            case InfoEvent.INFO_NEW_GROUP_NAME:
                result = R.drawable.ic_info_group_name_changed;
                break;
            case InfoEvent.INFO_DEVICE_ADDED:
            case InfoEvent.INFO_DEVICE_REMOVED:
            case InfoEvent.INFO_NO_DEVICES_FOR_USER:
                result = R.drawable.ic_info_devices_changed;
                break;
            case InfoEvent.INFO_NEW_BURN:
                result = R.drawable.ic_info_burn_changed;
                break;
            default:
                result = R.drawable.ic_info_group_changed;
                break;
        }
        return result;
    }

    @NonNull
    public static CharSequence getLocalizedText(final @NonNull Context context,
            final @NonNull InfoEvent infoEvent) {
        CharSequence result;
        switch (infoEvent.getTag()) {
            case InfoEvent.INFO_INVITE_USER:
                result = getTextWithDisplayName(context, infoEvent, R.string.group_messaging_invite);
                break;
            case InfoEvent.INFO_INVITE_USER_FAILED:
                result = getTextWithDisplayName(context, infoEvent, R.string.group_messaging_invite_failed);
                break;
            case InfoEvent.INFO_RESPONSE_HELLO:
                result = getTextWithDisplayName(context, infoEvent, R.string.group_messaging_hello_received);
                break;
            case InfoEvent.INFO_INVITE_RESPONSE_ACCEPTED:
                result = context.getString(R.string.group_messaging_invite_answer_accepted,
                        infoEvent.getDetails());
                break;
            case InfoEvent.INFO_INVITE_RESPONSE_DECLINED:
                result = context.getString(R.string.group_messaging_invite_answer_declined,
                        infoEvent.getDetails());
                break;
            case InfoEvent.INFO_USER_LEFT:
                result = getTextWithDisplayName(context, infoEvent, R.string.group_messaging_leave_notification);
                break;
            case InfoEvent.INFO_INVITE_RESPONSE_SELF_ACCEPTED:
                result = context.getString(R.string.group_messaging_invite_answer_self_accepted);
                break;
            case InfoEvent.INFO_INVITE_RESPONSE_SELF_DECLINED:
                result = context.getString(R.string.group_messaging_invite_answer_self_declined);
                break;
            case InfoEvent.INFO_NEW_BURN:
                InfoEvent.Details details = infoEvent.getEventDetails();
                if (details != null) {
                    long burnTime = details.burnTime;
                    String name = details.memberId;
                    if (TextUtils.isEmpty(name)) {
                        result = context.getString(R.string.group_messaging_new_burn,
                                BurnDelay.Defaults.getAlternateLabel(context,
                                        BurnDelay.Defaults.getLevel(burnTime)));
                    }
                    else {
                        if (TextUtils.equals(name, LoadUserInfo.getUuid())) {
                            result = context.getString(R.string.group_messaging_new_burn_this,
                                    BurnDelay.Defaults.getAlternateLabel(context,
                                            BurnDelay.Defaults.getLevel(burnTime)));
                        }
                        else {
                            CharSequence displayName = MessageUtils.getDisplayNameFromCache(name);
                            if (TextUtils.isEmpty(displayName) || TextUtils.equals(name, displayName)
                                    || ConversationUtils.UNKNOWN_DISPLAY_NAME.equals(displayName)) {
                                // use what display name was available previously, user id otherwise
                                displayName = details.userDisplayName;
                            }
                            result = context.getString(R.string.group_messaging_new_burn_by, displayName,
                                    BurnDelay.Defaults.getAlternateLabel(context,
                                            BurnDelay.Defaults.getLevel(burnTime)));
                        }
                    }
                }
                else {
                    result = infoEvent.getText();
                }
                break;
            case InfoEvent.INFO_NEW_GROUP_NAME:
                details = infoEvent.getEventDetails();
                if (details != null) {
                    String groupName = details.groupName;
                    String name = details.memberId;
                    if (TextUtils.isEmpty(name)) {
                        result = context.getString(R.string.group_messaging_new_name, groupName);
                    }
                    else {
                        if (TextUtils.equals(name, LoadUserInfo.getUuid())) {
                            result = context.getString(R.string.group_messaging_new_name_this, groupName);
                        }
                        else {
                            CharSequence displayName = MessageUtils.getDisplayNameFromCache(name);
                            if (TextUtils.isEmpty(displayName) || TextUtils.equals(name, displayName)
                                    || ConversationUtils.UNKNOWN_DISPLAY_NAME.equals(displayName)) {
                                // use what display name was available previously, user id otherwise
                                displayName = details.userDisplayName;
                            }
                            result = context.getString(R.string.group_messaging_new_name_by,
                                    displayName, groupName);
                        }
                    }
                }
                else {
                    result = infoEvent.getText();
                }
                break;
            case InfoEvent.INFO_NEW_AVATAR:
                result = getAvatarChangeText(context, infoEvent, R.string.group_messaging_new_avatar,
                        R.string.group_messaging_new_avatar_this,
                        R.string.group_messaging_new_avatar_by);
                break;
            case InfoEvent.INFO_AVATAR_REMOVED:
                result = getAvatarChangeText(context, infoEvent, R.string.group_messaging_avatar_removed,
                        R.string.group_messaging_avatar_removed_this,
                        R.string.group_messaging_avatar_removed_by);
                break;
            case InfoEvent.INFO_NEW_GROUP:
                result = context.getString(R.string.group_messaging_group_created);
                break;
            case InfoEvent.INFO_DEVICE_ADDED:
                result = getDeviceChangeText(context, infoEvent,
                        R.string.message_info_user_added_new_device);
                break;
            case InfoEvent.INFO_DEVICE_REMOVED:
                result = getDeviceChangeText(context, infoEvent,
                        R.string.message_info_user_removed_device);
                break;
            case InfoEvent.INFO_NO_DEVICES_FOR_USER:
                result = getTextWithDisplayName(context, infoEvent,
                        R.string.group_messaging_no_devices_for_user);
                break;
            default:
                result = infoEvent.getText();
                break;
        }
        if (result == null) {
            result = "N/A";
        }
        return result;
    }

    private static CharSequence getDeviceChangeText(@NonNull Context context,
            @NonNull InfoEvent infoEvent, int textId) {
        CharSequence result;
        InfoEvent.Details details = infoEvent.getEventDetails();
        if (details != null) {
            String userId = details.memberId;
            String deviceName = details.deviceName;
            CharSequence displayName = MessageUtils.getDisplayNameFromCache(userId);
            result = context.getString(textId, displayName, deviceName);
        }
        else {
            result = infoEvent.getText();
        }
        return result;
    }

    private static CharSequence getTextWithDisplayName(@NonNull Context context,
            @NonNull InfoEvent infoEvent, int textId) {
        CharSequence result;
        InfoEvent.Details details = infoEvent.getEventDetails();
        if (details != null) {
            String name = details.userId;
            // display name can be changed, try to get current one
            CharSequence displayName = MessageUtils.getDisplayNameFromCache(name);
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(name, displayName)
                    || ConversationUtils.UNKNOWN_DISPLAY_NAME.equals(displayName)) {
                // use what display name was available previously, user id otherwise
                displayName = details.userDisplayName;
            }
            result = context.getString(textId, displayName);
        }
        else {
            result = infoEvent.getText();
        }
        return result;
    }

    private static CharSequence getAvatarChangeText(@NonNull Context context,
            @NonNull InfoEvent infoEvent, int idChangeGeneric, int idChangeByYou, int idChangeBy) {
        CharSequence result;
        InfoEvent.Details details = infoEvent.getEventDetails();
        if (details != null) {
            String name = details.memberId;
            if (TextUtils.isEmpty(name)) {
                result = context.getString(idChangeGeneric);
            }
            else {
                if (TextUtils.equals(name, LoadUserInfo.getUuid())) {
                    result = context.getString(idChangeByYou);
                }
                else {
                    // display name can be changed, try to get current one
                    CharSequence displayName = MessageUtils.getDisplayNameFromCache(name);
                    if (TextUtils.isEmpty(displayName) || TextUtils.equals(name, displayName)
                            || ConversationUtils.UNKNOWN_DISPLAY_NAME.equals(displayName)) {
                        // use what display name was available previously, user id otherwise
                        displayName = details.userDisplayName;
                    }
                    displayName = TextUtils.isEmpty(displayName) ? name : displayName.toString();
                    result = context.getString(idChangeBy, displayName);
                }
            }
        }
        else {
            result = infoEvent.getText();
        }
        return result;
    }

    @Nullable
    private static ContactEntry getContactEntry(final @NonNull InfoEvent infoEvent) {
        ContactEntry result = null;
        InfoEvent.Details details = infoEvent.getEventDetails();
        if (details != null) {
            /*
             * Get member id first. When (at a later time) it will be known who added a participant,
             * the added participant's avatar would still be shown.
             */
            String name = details.memberId;
            if (TextUtils.isEmpty(name)) {
                name = details.userId;
            }
            if (!TextUtils.isEmpty(name)) {
                result = ContactsCache.getContactEntryFromCache(name);
            }
        }
        return result;
    }


}
