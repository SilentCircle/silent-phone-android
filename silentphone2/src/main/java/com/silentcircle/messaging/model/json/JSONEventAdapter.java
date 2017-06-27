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

import android.text.TextUtils;

import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.RetentionInfo;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.EventDeviceInfo;
import com.silentcircle.messaging.model.event.EventType;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.MessageStateEvent;
import com.silentcircle.messaging.model.event.OutgoingMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONEventAdapter extends JSONAdapter {

    // TODO gson
    private static final JSONRetentionInfoAdapter sRetentionInfo = new JSONRetentionInfoAdapter();

    public static final String TAG_EVENT_CONVERSATION_ID = "conversation_id";
    public static final String TAG_EVENT_ID = "id";
    public static final String TAG_EVENT_TEXT = "text";
    public static final String TAG_EVENT_TIME = "time";
    public static final String TAG_EVENT_TYPE = "type";

    public static final String TAG_ERROR_EVENT_ERROR = "error";
    public static final String TAG_ERROR_EVENT_SENDER = "sender";
    public static final String TAG_ERROR_EVENT_MESSAGE_TEXT = "message_text";
    public static final String TAG_ERROR_EVENT_DEVICE_ID = "device_id";
    public static final String TAG_ERROR_EVENT_MESSAGE_ID = "message_id";
    public static final String TAG_ERROR_EVENT_SENT_TO_DEV = "sent_to_dev_id";

    public static final String TAG_MESSAGE_BURN_NOTICE = "burn_notice";
    public static final String TAG_MESSAGE_CIPHERTEXT = "cipherText";
    public static final String TAG_MESSAGE_SENDER = "sender";
    public static final String TAG_MESSAGE_STATE = "state";
    public static final String TAG_MESSAGE_EXPIRATION_TIME = "expiration_time";
    public static final String TAG_MESSAGE_READ_TIME = "read_time";
    public static final String TAG_MESSAGE_DELIVERY_TIME = "delivery_time";
    public static final String TAG_MESSAGE_REQUEST_RECEIPT = "request_receipt";
    public static final String TAG_MESSAGE_LOCATION = "location";
    public static final String TAG_MESSAGE_ATTACHMENT = "attachment";
    public static final String TAG_MESSAGE_METADATA = "metaData";
    public static final String TAG_MESSAGE_FAILURE_FLAGS = "failureFlags";
    public static final String TAG_MESSAGE_DATA_RETENTION_STATE = "data_retention_state";
    public static final String TAG_MESSAGE_DATA_RETENTION_INFO = "retention_info";

    public static final String TAG_CALL_TYPE = "call_type";
    public static final String TAG_CALL_DURATION = "call_duration";
    public static final String TAG_CALL_TIME = "call_time";
    public static final String TAG_CALL_ERROR_MESSAGE = "error_message";

    public static final String TAG_DEVICE_NAME = "event_device_name";
    public static final String TAG_DEVICE_ID = "event_device_id";
    public static final String TAG_DEVICE_MSG_STATE = "event_device_msg_state";
    public static final String TAG_DEVICE_MSG_TRANSPORT = "event_device_msg_transport";
    public static final String TAG_DEVICE_INFO_ARRAY = "event_device_info_array";

    public static final String TAG_INFO_EVENT_TAG = "info_tag";
    public static final String TAG_INFO_EVENT_DETAILS = "info_details";

    private static Event adapt(JSONObject from, ErrorEvent to) {
        adapt(from, (Event) to);

        //noinspection ResourceType
        to.setError(getInt(from, TAG_ERROR_EVENT_ERROR));
        to.setSender(getString(from, TAG_ERROR_EVENT_SENDER));
        to.setMessageText(getString(from, TAG_ERROR_EVENT_MESSAGE_TEXT));
        to.setDeviceId(getString(from, TAG_ERROR_EVENT_DEVICE_ID));
        to.setMessageId(from.optString(TAG_ERROR_EVENT_MESSAGE_ID, null));
        to.setSentToDevId(from.optString(TAG_ERROR_EVENT_SENT_TO_DEV, null));

        return to;
    }

    private static Event adapt(JSONObject from, InfoEvent to) {
        adapt(from, (Event) to);

        //noinspection ResourceType
        to.setTag(getInt(from, TAG_INFO_EVENT_TAG, InfoEvent.TAG_NOT_SET));
        to.setDetails(getString(from, TAG_INFO_EVENT_DETAILS));

        if (from.has(TAG_MESSAGE_ATTACHMENT)) {
            to.setAttachment(getString(from, TAG_MESSAGE_ATTACHMENT));
        }

        return to;
    }

    private static Event adapt(JSONObject from, Event to) {
        to.setConversationID(getString(from, TAG_EVENT_CONVERSATION_ID));
        to.setId(getString(from, TAG_EVENT_ID));
        to.setText(getString(from, TAG_EVENT_TEXT));
        to.setTime(getLong(from, TAG_EVENT_TIME));

        return to;
    }

    private static Event adapt(JSONObject from, Message to) {

        adapt(from, (Event) to);

        to.setBurnNotice(getInt(from, TAG_MESSAGE_BURN_NOTICE, -1));
        to.setCiphertext(getString(from, TAG_MESSAGE_CIPHERTEXT));
        to.setSender(getString(from, TAG_MESSAGE_SENDER));

        //noinspection ResourceType
        to.setState(getInt(from, TAG_MESSAGE_STATE, MessageStates.UNKNOWN));
        to.setExpirationTime(getLong(from, TAG_MESSAGE_EXPIRATION_TIME, Message.DEFAULT_EXPIRATION_TIME));
        to.setDeliveryTime(getLong(from, TAG_MESSAGE_DELIVERY_TIME, 0));
        to.setRequestReceipt(from.optBoolean(TAG_MESSAGE_REQUEST_RECEIPT, false));
        to.setLocation(LocationUtils.stringToMessageLocation(from.optString(TAG_MESSAGE_LOCATION)));

        if (from.has(TAG_MESSAGE_ATTACHMENT)) {
            to.setAttachment(getString(from, TAG_MESSAGE_ATTACHMENT));
        }

        if (from.has(TAG_MESSAGE_METADATA)) {
            to.setMetaData(getString(from, TAG_MESSAGE_METADATA));
        }

        if (from.has(TAG_DEVICE_INFO_ARRAY)) {
            JSONArray infoArray = from.optJSONArray(TAG_DEVICE_INFO_ARRAY);
            int length = infoArray.length();
            EventDeviceInfo[] eventDeviceInfo = new EventDeviceInfo[length];
            for (int i = 0; i < length; i++) {
                JSONObject infoJson = infoArray.optJSONObject(i);
                EventDeviceInfo info = new EventDeviceInfo();
                info.deviceName = infoJson.optString(TAG_DEVICE_NAME);
                info.deviceId = infoJson.optString(TAG_DEVICE_ID);
                info.state = infoJson.optInt(TAG_DEVICE_MSG_STATE, 0);
                info.transportId = infoJson.optLong(TAG_DEVICE_MSG_TRANSPORT, 0L);
                eventDeviceInfo[i] = info;
            }
            to.setEventDeviceInfo(eventDeviceInfo);
        }

        if (from.has(TAG_DEVICE_INFO_ARRAY)) {
            JSONArray infoArray = from.optJSONArray(TAG_DEVICE_INFO_ARRAY);
            int length = infoArray.length();
            EventDeviceInfo[] eventDeviceInfo = new EventDeviceInfo[length];
            for (int i = 0; i < length; i++) {
                JSONObject infoJson = infoArray.optJSONObject(i);
                EventDeviceInfo info = new EventDeviceInfo();
                info.deviceName = infoJson.optString(TAG_DEVICE_NAME);
                info.deviceId = infoJson.optString(TAG_DEVICE_ID);
                info.state = infoJson.optInt(TAG_DEVICE_MSG_STATE, 0);
                info.transportId = infoJson.optLong(TAG_DEVICE_MSG_TRANSPORT, 0L);
                eventDeviceInfo[i] = info;
            }
            to.setEventDeviceInfo(eventDeviceInfo);
        }

        to.setFailureFlags(fromArrayOfLongs(getJSONArray(from, TAG_MESSAGE_FAILURE_FLAGS)));

        to.setRetained(from.optBoolean(TAG_MESSAGE_DATA_RETENTION_STATE, false));

        RetentionInfo info = sRetentionInfo.adapt(from.optJSONObject(TAG_MESSAGE_DATA_RETENTION_INFO));
        to.setRetentionInfo(info);

        return to;
    }

    private static Event adapt(JSONObject from, MessageStateEvent to) {
        adapt(from, (Event) to);

        //noinspection ResourceType
        to.setState(getInt(from, TAG_MESSAGE_STATE, MessageStates.UNKNOWN));
        to.setDeliveryTime(getLong(from, TAG_MESSAGE_DELIVERY_TIME, 0));
        to.setDeliveryTime(getLong(from, TAG_MESSAGE_READ_TIME, 0));

        return to;
    }

    private static Event adapt(JSONObject from, CallMessage to) {
        adapt(from, (Message) to);

        to.setCallType(getInt(from, TAG_CALL_TYPE));
        to.setCallDuration(getInt(from, TAG_CALL_DURATION));
        to.setCallTime(getLong(from, TAG_CALL_TIME));
        to.setErrorMessage(getString(from, TAG_CALL_ERROR_MESSAGE, null));

        return to;
    }

    public JSONObject adapt(Event event) {

        JSONObject json = new JSONObject();

        try {

            EventType evt = EventType.forClass(event.getClass());
            json.put(TAG_EVENT_TYPE, (evt == null ? "" : evt.name()));

            json.put(TAG_EVENT_CONVERSATION_ID, event.getConversationID());
            json.put(TAG_EVENT_ID, event.getId());
            json.put(TAG_EVENT_TEXT, event.getText());
            json.put(TAG_EVENT_TIME, event.getTime());
            json.put(TAG_MESSAGE_ATTACHMENT, event.getAttachment());

            EventDeviceInfo[] devInfo = event.getEventDeviceInfo();
            if (devInfo != null && devInfo.length > 0) {
                JSONArray infoArray = new JSONArray();
                for (EventDeviceInfo info : devInfo) {
                    if (info == null)
                        continue;
                    JSONObject infoJson = new JSONObject();
                    infoJson.put(TAG_DEVICE_NAME, info.deviceName);
                    infoJson.put(TAG_DEVICE_ID, info.deviceId);
                    infoJson.put(TAG_DEVICE_MSG_STATE, info.state);
                    infoJson.put(TAG_DEVICE_MSG_TRANSPORT, info.transportId);
                    infoArray.put(infoJson);
                }
                json.put(TAG_DEVICE_INFO_ARRAY, infoArray);
            }

            if (event instanceof Message) {
                Message message = (Message) event;

                json.put(TAG_MESSAGE_BURN_NOTICE, message.getBurnNotice());
                json.put(TAG_MESSAGE_CIPHERTEXT, message.getCiphertext());
                json.put(TAG_MESSAGE_SENDER, message.getSender());
                json.put(TAG_MESSAGE_STATE, message.getState());
                json.put(TAG_MESSAGE_EXPIRATION_TIME, message.getExpirationTime());
                json.put(TAG_MESSAGE_DELIVERY_TIME, message.getDeliveryTime());
                json.put(TAG_MESSAGE_REQUEST_RECEIPT, message.isRequestReceipt());
                json.put(TAG_MESSAGE_LOCATION, LocationUtils.messageLocationToJSON(message.getLocation()));
                json.put(TAG_MESSAGE_METADATA, message.getMetaData());
                json.put(TAG_MESSAGE_FAILURE_FLAGS, toArray(message.getFailureFlags()));
                json.put(TAG_MESSAGE_DATA_RETENTION_STATE, message.isRetained());

                RetentionInfo info = message.getRetentionInfo();
                if (info != null) {
                    json.put(TAG_MESSAGE_DATA_RETENTION_INFO, sRetentionInfo.adapt(info));
                }
            }

            if (event instanceof MessageStateEvent) {
                MessageStateEvent messageStateEvent = (MessageStateEvent) event;

                json.put(TAG_MESSAGE_STATE, messageStateEvent.getState());
                json.put(TAG_MESSAGE_DELIVERY_TIME, messageStateEvent.getDeliveryTime());
                json.put(TAG_MESSAGE_READ_TIME, messageStateEvent.getReadReceiptTime());
            }

            if (event instanceof CallMessage) {
                CallMessage callMessage = (CallMessage) event;

                json.put(TAG_CALL_TYPE, callMessage.getCallType());
                json.put(TAG_CALL_DURATION, callMessage.getCallDuration());
                json.put(TAG_CALL_TIME, callMessage.getCallTime());
                String errorMessage = callMessage.getErrorMessage();
                if (!TextUtils.isEmpty(errorMessage)) {
                    json.put(TAG_CALL_ERROR_MESSAGE, errorMessage);
                }
            }

            if (event instanceof ErrorEvent) {
                ErrorEvent error = (ErrorEvent) event;
                json.put(TAG_ERROR_EVENT_ERROR, error.getError());
                json.put(TAG_ERROR_EVENT_SENDER, error.getSender());
                json.put(TAG_ERROR_EVENT_MESSAGE_TEXT, error.getMessageText());
                json.put(TAG_ERROR_EVENT_DEVICE_ID, error.getDeviceId());
                String messageId = error.getMessageId();
                if (!TextUtils.isEmpty(messageId)) {
                    json.put(TAG_ERROR_EVENT_MESSAGE_ID, messageId);
                }
                json.put(TAG_ERROR_EVENT_SENT_TO_DEV, error.getSentToDevId());
            }

            if (event instanceof InfoEvent) {
                InfoEvent info = (InfoEvent) event;
                json.put(TAG_INFO_EVENT_TAG, info.getTag());
                json.put(TAG_INFO_EVENT_DETAILS, info.getDetails());
            }

        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }

        return json;
    }

    public Event adapt(JSONObject json) {
        try {
            switch (EventType.valueOf(getString(json, TAG_EVENT_TYPE))) {
                case MESSAGE:
                    return adapt(json, new Message());
                case MESSAGE_STATE_EVENT:
                    return adapt(json, new MessageStateEvent());
                case ERROR_EVENT:
                    return adapt(json, new ErrorEvent());
                case INFO_EVENT:
                    return adapt(json, new InfoEvent());
                case INCOMING_MESSAGE:
                    return adapt(json, new IncomingMessage());
                case OUTGOING_MESSAGE:
                    return adapt(json, new OutgoingMessage());
                case PHONE_MESSAGE:
                    return adapt(json, new CallMessage());
                case EVENT:
                default:
                    return adapt(json, new Event());
            }
        } catch (Throwable t) {
            // try to match json to plain {@link com.silentcircle.messaging.model.event.Event}
            return adapt(json, new Event());
        }
    }
}
