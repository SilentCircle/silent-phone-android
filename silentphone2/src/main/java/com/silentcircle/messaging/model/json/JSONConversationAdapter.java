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
package com.silentcircle.messaging.model.json;

import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.util.BurnDelay;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONConversationAdapter extends JSONAdapter {

    private final JSONContactAdapter contactAdapter = new JSONContactAdapter();

    public JSONObject adapt(Conversation conversation) {

        JSONObject json = new JSONObject();

        try {

            json.put("burn_delay", conversation.getBurnDelay());
            json.put("burn_notice", conversation.hasBurnNotice());
            json.put("id", conversation.getId());
            json.put("is_location_enabled", conversation.isLocationEnabled());
            json.put("send_receipts", conversation.shouldSendReadReceipts());
            json.put("partner", contactAdapter.adapt(conversation.getPartner()));
            json.put("unread_messages", conversation.getUnreadMessageCount());
            json.put("unread_call_messages", conversation.getUnreadCallMessageCount());
            json.put("preview_event_id", conversation.getPreviewEventID());
            json.put("last_modified", conversation.getLastModified());
            json.put("failures", conversation.getFailures());
            json.put("unsent_text", conversation.getUnsentText());

        } catch (JSONException exception) {
            // This should never happen because we are hard-coding all of the keys.
        }

        return json;

    }

    public Conversation adapt(JSONObject json) {

        Conversation conversation = new Conversation(contactAdapter.adapt(getJSONObject(json, "partner")));

        conversation.setBurnDelay(getLong(json, "burn_delay", BurnDelay.getDefaultDelay()));
        conversation.setBurnNotice(getBoolean(json, "burn_notice"));
        conversation.setId(getString(json, "id"));
        conversation.setLocationEnabled(getBoolean(json, "is_location_enabled"));
        conversation.setSendReadReceipts(getBoolean(json, "send_receipts"));
        conversation.setUnreadMessageCount(getInt(json, "unread_messages"));
        conversation.setUnreadCallMessageCount(getInt(json, "unread_call_messages"));
        conversation.setPreviewEventID(getString(json, "preview_event_id"));
        conversation.setLastModified(getLong(json, "last_modified"));
        conversation.setFailures(getInt(json, "failures"));
        conversation.setUnsentText(getString(json, "unsent_text", null));

        return conversation;

    }

}
