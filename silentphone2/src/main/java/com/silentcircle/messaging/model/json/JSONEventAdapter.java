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

import android.text.TextUtils;

import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.EventType;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class JSONEventAdapter extends JSONAdapter {

    private static Event adapt(JSONObject from, ErrorEvent to) {
        adapt(from, (Event) to);

        //noinspection ResourceType
        to.setError(getInt(from, "error"));
        to.setSender(getString(from, "sender"));
        to.setMessageText(getString(from, "message_text"));
        to.setDeviceId(getString(from, "device_id"));
        to.setMessageId(from.optString("message_id", null));
        to.setSentToDevId(from.optString("sent_to_dev_id", null));

        return to;
    }

    private static Event adapt(JSONObject from, Event to) {
        to.setConversationID(getString(from, "conversation_id"));
        to.setId(getString(from, "id"));
        to.setText(getString(from, "text"));
        to.setTime(getLong(from, "time"));

        return to;
    }

    private static Event adapt(JSONObject from, Message to) {

        adapt(from, (Event) to);

        to.setBurnNotice(getInt(from, "burn_notice", -1));
        to.setCiphertext(getString(from, "cipherText"));
        to.setSender(getString(from, "sender"));

        //noinspection ResourceType
        to.setState(getInt(from, "state", MessageStates.UNKNOWN));
        to.setExpirationTime(getLong(from, "expiration_time", Message.DEFAULT_EXPIRATION_TIME));
        to.setRequestReceipt(from.optBoolean("request_receipt", false));
        to.setLocation(LocationUtils.stringToMessageLocation(from.optString("location")));

        if (from.has("attachment")) {
            to.setAttachment(getString(from, "attachment"));
        }

        if (from.has("metaData")) {
            to.setMetaData(getString(from, "metaData"));
        }

        if (from.has("net_ids")) {
            JSONArray ids = from.optJSONArray("net_ids");
            int length = ids.length();
            for (int i = 0; i < length; i++) {
                long id = ids.optLong(i, 0L);
                to.addNetMessageId(id);
            }
        }
        return to;
    }

    public JSONObject adapt(Event event) {

        JSONObject json = new JSONObject();

        try {

            EventType evt = EventType.forClass(event.getClass());
            json.put("type", (evt == null ? "" : evt.name()));

            json.put("conversation_id", event.getConversationID());
            json.put("id", event.getId());
            json.put("text", event.getText());
            json.put("time", event.getTime());

            if (event instanceof Message) {

                Message message = (Message) event;

                json.put("burn_notice", message.getBurnNotice());
                json.put("cipherText", message.getCiphertext());
                json.put("sender", message.getSender());
                json.put("state", message.getState());
                json.put("expiration_time", message.getExpirationTime());
                json.put("request_receipt", message.isRequestReceipt());
                json.put("location", LocationUtils.messageLocationToJSON(message.getLocation()));
                json.put("attachment", message.getAttachment());
                json.put("metaData", message.getMetaData());
                List<Long> netIds = message.getNetMessageIds();
                if (!netIds.isEmpty()) {
                    JSONArray ids = new JSONArray();
                    for (Long id : netIds)
                        ids.put(id);
                    json.put("net_ids", ids);
                }
            }

            if (event instanceof ErrorEvent) {
                ErrorEvent error = (ErrorEvent) event;
                json.put("error", error.getError());
                json.put("sender", error.getSender());
                json.put("message_text", error.getMessageText());
                json.put("device_id", error.getDeviceId());
                String messageId = error.getMessageId();
                if (!TextUtils.isEmpty(messageId)) {
                    json.put("message_id", messageId);
                }
                json.put("sent_to_dev_id", error.getSentToDevId());
            }

        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }

        return json;
    }

    public Event adapt(JSONObject json) {
        switch (EventType.valueOf(getString(json, "type"))) {
            case MESSAGE:
                return adapt(json, new Message());
            case ERROR_EVENT:
                return adapt(json, new ErrorEvent());
            case INCOMING_MESSAGE:
                return adapt(json, new IncomingMessage());
            case OUTGOING_MESSAGE:
                return adapt(json, new OutgoingMessage());
            case EVENT:
            default:
                return adapt(json, new Event());
        }
    }

}
