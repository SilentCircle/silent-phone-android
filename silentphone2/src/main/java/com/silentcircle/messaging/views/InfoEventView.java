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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;
import com.silentcircle.silentphone2.R;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import zina.JsonStrings;

/**
 *
 */
public class InfoEventView extends CheckableRelativeLayout implements View.OnClickListener, HasChoiceMode {

    private TextView mText;
    private Event mInfoEvent;
    private boolean mInChoiceMode;

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
        mText = (TextView) findViewById(R.id.info_message);
    }

    @Override
    public void onClick(View view) {
        /* show event information on click*/
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

    public void setEvent(Event infoEvent) {
        mInfoEvent = infoEvent;
        if (mInfoEvent instanceof InfoEvent) {
            setText(getLocalizedText(getContext(), (InfoEvent) mInfoEvent));
        } else {
            setText(infoEvent.getText());
        }
    }

    public void setText(CharSequence text) {
        mText.setText(text);
    }

    @NonNull
    public static CharSequence getLocalizedText(@NonNull Context context, @NonNull InfoEvent infoEvent) {
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
                try {
                    JSONObject json = new JSONObject(infoEvent.getDetails());
                    long burnTime = json.getLong(JsonStrings.GROUP_BURN_SEC);
                    String name = json.optString(JsonStrings.MEMBER_ID, null);
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
                            CharSequence displayName = MessageUtils.getDisplayName(name);
                            name = TextUtils.isEmpty(displayName) ? name : displayName.toString();
                            result = context.getString(R.string.group_messaging_new_burn_by, name,
                                    BurnDelay.Defaults.getAlternateLabel(context,
                                            BurnDelay.Defaults.getLevel(burnTime)));
                        }
                    }
                } catch (JSONException exception) {
                    result = infoEvent.getText();
                }
                break;
            case InfoEvent.INFO_NEW_GROUP_NAME:
                try {
                    JSONObject json = new JSONObject(infoEvent.getDetails());
                    String groupName = json.getString(JsonStrings.GROUP_NAME);
                    String name = json.optString(JsonStrings.MEMBER_ID, null);
                    if (TextUtils.isEmpty(name)) {
                        result = context.getString(R.string.group_messaging_new_name, groupName);
                    }
                    else {
                        if (TextUtils.equals(name, LoadUserInfo.getUuid())) {
                            result = context.getString(R.string.group_messaging_new_name_this, groupName);
                        }
                        else {
                            CharSequence displayName = MessageUtils.getDisplayName(name);
                            name = TextUtils.isEmpty(displayName) ? name : displayName.toString();
                            result = context.getString(R.string.group_messaging_new_name_by, name,
                                    groupName);
                        }
                    }
                } catch (JSONException exception) {
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
        try {
            JSONObject json = new JSONObject(infoEvent.getDetails());
            String userId = json.getString(JsonStrings.MSG_USER_ID);
            String deviceName = json.getString(JsonStrings.MSG_DEVICE_NAME);
            CharSequence displayName = MessageUtils.getDisplayName(userId);
            result = context.getString(textId, displayName, deviceName);
        } catch (NullPointerException | JSONException exception) {
            result = infoEvent.getText();
        }
        return result;
    }

    private static CharSequence getTextWithDisplayName(@NonNull Context context,
            @NonNull InfoEvent infoEvent, int textId) {
        CharSequence result;
        try {
            JSONObject json = new JSONObject(infoEvent.getDetails());
            String name = json.optString(JsonStrings.MSG_USER_ID, null);
            // display name can be changed, try to get current one
            CharSequence displayName = MessageUtils.getDisplayName(name);
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(name, displayName)
                    || ConversationUtils.UNKNOWN_DISPLAY_NAME.equals(displayName)) {
                // use what display name was available previously, user id otherwise
                displayName = json.optString(JsonStrings.MSG_DISPLAY_NAME, name);
            }
            result = context.getString(textId, displayName);
        } catch (NullPointerException | JSONException exception) {
            result = infoEvent.getText();
        }
        return result;
    }

    private static CharSequence getAvatarChangeText(@NonNull Context context,
            @NonNull InfoEvent infoEvent, int idChangeGeneric, int idChangeByYou, int idChangeBy) {
        CharSequence result;
        try {
            JSONObject json = new JSONObject(infoEvent.getDetails());
            String name = json.optString(JsonStrings.MEMBER_ID, null);
            if (TextUtils.isEmpty(name)) {
                result = context.getString(idChangeGeneric);
            }
            else {
                if (TextUtils.equals(name, LoadUserInfo.getUuid())) {
                    result = context.getString(idChangeByYou);
                }
                else {
                    // display name can be changed, try to get current one
                    CharSequence displayName = MessageUtils.getDisplayName(name);
                    if (TextUtils.isEmpty(displayName) || TextUtils.equals(name, displayName)
                            || ConversationUtils.UNKNOWN_DISPLAY_NAME.equals(displayName)) {
                        // use what display name was available previously, user id otherwise
                        displayName = json.optString(JsonStrings.MSG_DISPLAY_NAME, name);
                    }
                    displayName = TextUtils.isEmpty(displayName) ? name : displayName.toString();
                    result = context.getString(idChangeBy, displayName);
                }
            }
        } catch (JSONException exception) {
            result = infoEvent.getText();
        }
        return result;
    }

}
