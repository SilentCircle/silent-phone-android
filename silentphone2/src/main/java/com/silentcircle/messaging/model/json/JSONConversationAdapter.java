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
package com.silentcircle.messaging.model.json;

import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.util.BurnDelay;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONConversationAdapter extends JSONAdapter {

    public static final String TAG_CONVERSATION_BURN_DELAY = "burn_delay";
    public static final String TAG_CONVERSATION_BURN_NOTICE = "burn_notice";
    public static final String TAG_CONVERSATION_ID = "id";
    public static final String TAG_CONVERSATION_IS_LOCATION_ENABLED = "is_location_enabled";
    public static final String TAG_CONVERSATION_SEND_RECEIPTS = "send_receipts";
    public static final String TAG_CONVERSATION_PARTNER = "partner";
    public static final String TAG_CONVERSATION_UNREAD_MESSAGE_COUNT = "unread_messages";
    public static final String TAG_CONVERSATION_UNREAD_CALL_COUNT = "unread_call_messages";
    public static final String TAG_CONVERSATION_PREVIEW_EVENT_ID = "preview_event_id";
    public static final String TAG_CONVERSATION_LAST_MODIFIED = "last_modified";
    public static final String TAG_CONVERSATION_FAILURES = "failures";
    public static final String TAG_CONVERSATION_DRAFT_TEXT = "unsent_text";
    public static final String TAG_CONVERSATION_AVATAR = "avatar";
    public static final String TAG_CONVERSATION_AVATAR_URL = "avatar_url";
    public static final String TAG_CONVERSATION_AVATAR_IV = "avatar_iv";

    private final JSONContactAdapter contactAdapter = new JSONContactAdapter();

    public JSONObject adapt(Conversation conversation) {

        JSONObject json = new JSONObject();

        try {

            json.put(TAG_CONVERSATION_BURN_DELAY, conversation.getBurnDelay());
            json.put(TAG_CONVERSATION_BURN_NOTICE, conversation.hasBurnNotice());
            json.put(TAG_CONVERSATION_ID, conversation.getId());
            json.put(TAG_CONVERSATION_IS_LOCATION_ENABLED, conversation.isLocationEnabled());
            json.put(TAG_CONVERSATION_SEND_RECEIPTS, conversation.shouldSendReadReceipts());
            json.put(TAG_CONVERSATION_PARTNER, contactAdapter.adapt(conversation.getPartner()));
            json.put(TAG_CONVERSATION_UNREAD_MESSAGE_COUNT, conversation.getUnreadMessageCount());
            json.put(TAG_CONVERSATION_UNREAD_CALL_COUNT, conversation.getUnreadCallMessageCount());
            json.put(TAG_CONVERSATION_PREVIEW_EVENT_ID, conversation.getPreviewEventID());
            json.put(TAG_CONVERSATION_LAST_MODIFIED, conversation.getLastModified());
            json.put(TAG_CONVERSATION_FAILURES, conversation.getFailures());
            json.put(TAG_CONVERSATION_DRAFT_TEXT, conversation.getUnsentText());
            json.put(TAG_CONVERSATION_AVATAR, conversation.getAvatar());
            json.put(TAG_CONVERSATION_AVATAR_URL, conversation.getAvatarUrl());
            json.put(TAG_CONVERSATION_AVATAR_IV, conversation.getAvatarIv());

        } catch (JSONException exception) {
            // This should never happen because we are hard-coding all of the keys.
        }

        return json;

    }

    public Conversation adapt(JSONObject json) {

        Conversation conversation =
                new Conversation(contactAdapter.adapt(getJSONObject(json, TAG_CONVERSATION_PARTNER)));

        conversation.setBurnDelay(getLong(json, TAG_CONVERSATION_BURN_DELAY, BurnDelay.getDefaultDelay()));
        conversation.setBurnNotice(getBoolean(json, TAG_CONVERSATION_BURN_NOTICE));
        conversation.setId(getString(json, TAG_CONVERSATION_ID));
        conversation.setLocationEnabled(getBoolean(json, TAG_CONVERSATION_IS_LOCATION_ENABLED));
        conversation.setSendReadReceipts(getBoolean(json, TAG_CONVERSATION_SEND_RECEIPTS));
        conversation.setUnreadMessageCount(getInt(json, TAG_CONVERSATION_UNREAD_MESSAGE_COUNT));
        conversation.setUnreadCallMessageCount(getInt(json, TAG_CONVERSATION_UNREAD_CALL_COUNT));
        conversation.setPreviewEventID(getString(json, TAG_CONVERSATION_PREVIEW_EVENT_ID));
        conversation.setLastModified(getLong(json, TAG_CONVERSATION_LAST_MODIFIED));
        conversation.setFailures(getInt(json, TAG_CONVERSATION_FAILURES));
        conversation.setUnsentText(getString(json, TAG_CONVERSATION_DRAFT_TEXT, null));
        conversation.setAvatar(getString(json, TAG_CONVERSATION_AVATAR, null));
        conversation.setAvatarUrl(getString(json, TAG_CONVERSATION_AVATAR_URL, null));
        conversation.setAvatarIv(getString(json, TAG_CONVERSATION_AVATAR_IV, null));

        return conversation;

    }

}
