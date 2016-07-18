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
package com.silentcircle.messaging.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.services.SCloudCleanupService;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.views.MessageInformationView;
import com.silentcircle.silentphone2.Manifest;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Utilities to handle conversation message saving.
 */
public class MessageUtils {

    private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance();
    private static final SimpleDateFormat DEBUG_DATE_FORMAT =
            new SimpleDateFormat("dd/MM/yy hh:mm:ss.SSS", Locale.getDefault());

    private MessageUtils() {
    }

    public static Message composeMessage(final String sender, String text,
            boolean shouldRequestDeliveryNotification, Conversation conversation,
            Location location) {
        Message message = new OutgoingMessage(sender, text);
        message.setConversationID(conversation.getPartner().getUserId());
        message.setId(UUIDGen.makeType1UUID().toString());

        if (conversation.hasBurnNotice()) {
            message.setBurnNotice(conversation.getBurnDelay());
        }

        try {
            message.removeAttribute();
            message.removeAttachment();
            JSONObject attributeJson = new JSONObject();
            if (shouldRequestDeliveryNotification) {
                attributeJson.put("r", true);                          // "request_receipt"
            }

            if (conversation.hasBurnNotice()) {
                attributeJson.put("s", conversation.getBurnDelay());  // "shred_after"
            }

            LocationUtils.locationToJSON(attributeJson, location);

            if (attributeJson.length() > 0) {
                message.setAttributes(attributeJson.toString());
                message.setLocation(LocationUtils.jsonToMessageLocation(attributeJson));
            }

        } catch (JSONException exception) {
            // Failed to set message attributes
        }

        return message;
    }

    public static Message setMessageLocation(final Message message, final Location location) {
        String attributes = message.getAttributes();
        try {
            JSONObject attributeJson = new JSONObject(TextUtils.isEmpty(attributes) ? "{}" : attributes);
            LocationUtils.locationToJSON(attributeJson, location);

            if (attributeJson.length() > 0) {
                message.setAttributes(attributeJson.toString());
                message.setLocation(LocationUtils.jsonToMessageLocation(attributeJson));
            }
        } catch (JSONException exception) {
            // return message unchanged
        }
        return message;
    }

    @Nullable
    public static String getMessageAttributesJSON(long burnDelay,
            boolean shouldRequestDeliveryNotification,
            @Nullable com.silentcircle.messaging.model.Location location) {
        String result = "{}";
        try {
            JSONObject attributeJson = new JSONObject();

            if (shouldRequestDeliveryNotification) {
                attributeJson.put("r", true);       // "request_receipt"
            }

            if (burnDelay != 0) {
                attributeJson.put("s", burnDelay);  // "shred_after"
            }

            LocationUtils.messageLocationToJSON(attributeJson, location);

            result = attributeJson.toString();
        } catch (JSONException exception) {
            // Failed to prepare message attributes, return empty json
        }
        return result;
    }

    public static void forwardMessage(final Context context, final String sender,
            final String recipient, final Message forwardedMessage) {

        AxoMessaging msgService = AxoMessaging.getInstance(context);
        ConversationRepository repository = msgService.getConversations();

        Conversation conversation = msgService.getOrCreateConversation(recipient);
        Message message = MessageUtils.composeMessage(sender, "",
                true, conversation, null);
        EventRepository events = repository.historyOf(conversation);

        if(forwardedMessage.hasAttachment()) {
            message.setAttachment(forwardedMessage.getAttachment());
        }

        if(forwardedMessage.hasMetaData()) {
            message.setMetaData(forwardedMessage.getMetaData());
        }

        message.setState(MessageStates.COMPOSING);
        message.setText(forwardedMessage.getText());

        events.save(message);

        SendMessageTask task = new SendMessageTask(context.getApplicationContext());
        AsyncUtils.execute(task, message);
    }

    public static OutgoingMessage getLastOutgoingMessage(final Context context, final String partner) {
        EventRepository eventRepository = getEventRepository(context, partner);

        if (eventRepository == null) {
            return null;
        }

        List<Event> events = sortEventsById(eventRepository.list());

        for (Event event : events) {
            if (event instanceof OutgoingMessage) {
                return (OutgoingMessage) event;
            }
        }

        return null;
    }

    public static String getPrintableHistory(final List<Event> events) {
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event instanceof Message && (((Message) event).expires())) {
                continue;
            }
            history.append("[").append(DATE_FORMAT.format(new Date(event.getTime()))).append("] ");
            if (event instanceof Message) {
                Message message = (Message) event;
                history.append(Utilities.removeSipParts(message.getSender())).append(": ");
                try {
                    JSONObject json = new JSONObject(message.getText());
                    if (json.has("message")) {
                        history.append(json.getString("message"));
                    } else {
                        history.append("(attachment)");
                    }
                } catch (JSONException exception) {
                    history.append(message.getText());
                }
            } else {
                history.append(event.getText());
            }
            history.append("\n");
        }
        return history.toString();
    }

    public static void sendMessages(final Context context, final String partner,
            final String messages) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, messages);
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.conversation_with,
                Utilities.removeSipParts(partner)));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignore) {
        }
    }

    public static void saveMessagesToFile(Context context, String messages) {
        String baseFolder;
        FileOutputStream outputStream = null;
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                baseFolder = Environment.getExternalStorageDirectory().getAbsolutePath();
            } else {
                baseFolder = context.getFilesDir().getAbsolutePath();
            }

            File folder = new File(baseFolder + "/SilentCircle");
            if (!folder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                folder.mkdir();
            }
            File file = new File(folder, "saved_messages.txt");
            outputStream = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            writer.append(messages);
            writer.close();
            outputStream.close();
        } catch (IOException e) {
            android.util.Log.e("MessageUtils", "Unable to save message to file: " + e.getMessage());
        } finally {
            IOUtils.close(outputStream);
        }
    }

    /**
     * Retrieve an event from a conversation with specified partner by message id.
     *
     * @param context Context to access {@link AxoMessaging} instance.
     * @param partner Conversation partner
     * @param messageId Message identifier (UUID string)
     *
     * @return event which matches passed messageId or null if not found or conversation for partner
     *         not started,
     */
    public static Event getEventById(final Context context, final String partner, final String messageId) {
        Event event = null;

        ConversationRepository conversations =
                AxoMessaging.getInstance(context.getApplicationContext()).getConversations();
        Conversation conversation = conversations.findByPartner(partner);
        if (conversation != null) {
            event = conversations.historyOf(conversation).findById(messageId);
        }

        return event;
    }

    public static EventRepository getEventRepository(Context context, String partner) {
        if(context == null || partner == null) {
            return null;
        }

        AxoMessaging axoMessaging = AxoMessaging.getInstance(context);
        if (axoMessaging == null) {
            return null;
        }

        ConversationRepository conversationRepository = axoMessaging.getConversations();
        if (conversationRepository == null) {
            return null;
        }

        Conversation conversation = conversationRepository.findByPartner(partner);
        if (conversation == null) {
            return null;
        }

        EventRepository eventRepository = conversationRepository.historyOf(conversation);
        if(eventRepository == null || !eventRepository.exists()) {
            return null;
        }

        return eventRepository;
    }

    public static void parseAttributes(String attributes, Message msg) {
        try {
            JSONObject attributeJson = new JSONObject(attributes);
            msg.setRequestReceipt(attributeJson.optBoolean("r", false));
            if (attributeJson.has("s")) {
                msg.setBurnNotice(attributeJson.getLong("s"));
                msg.setRequestReceipt(true);
            }
            msg.setLocation(LocationUtils.jsonToMessageLocation(attributeJson));
        } catch (JSONException exception) {
            // In practice, this superfluous exception can never actually happen.
        }
    }

    public static void setAttributes(Message msg) {
        try {
            JSONObject attributeJson = new JSONObject();

            if (msg.hasLocation()) {
                attributeJson = LocationUtils.messageLocationToJSON(msg.getLocation());
            }

            if (msg.isRequestReceipt()) {
                attributeJson.put("r", true);
            }

            if (msg.hasBurnNotice()) {
                attributeJson.put("s", msg.getBurnNotice());
            }

            if (attributeJson.length() > 0) {
                msg.setAttributes(attributeJson.toString());
            }

        } catch (JSONException exception) {
            // Failed to set message attributes
        }
    }

    /**
     * Parse error message JSON and populate fields of passed event. Passed JSON is in following
     * format:
     * <p>
     *     {"version":1,"details":{"name":"sender","scClientDevId":"deviceId","otherInfo":"info"}}
     * </p>
     *
     * @param event {@link ErrorEvent} for which to populate fields.
     * @param errorString JSON representation of error.
     *
     * @return Passed {@link ErrorEvent} instance. In case of failure its fields are not populated.
     *         Caller has to check whether necessary fields are set.
     */
    public static ErrorEvent parseErrorMessage(final ErrorEvent event, final String errorString) {
        /*
         * Function expects following JSON:
         * {"version":1,"details":{"name":"sender","scClientDevId":"deviceId","otherInfo":"info", "sentToId": "deviceId"}}
         *
         * Fields name and scClientDevId are parsed and set to passed event.
         */
        try {
            JSONObject json = new JSONObject(errorString);
            JSONObject details = json.getJSONObject("details");
            event.setSender(Utilities.removeSipParts(details.getString("name")));
            event.setDeviceId(details.getString("scClientDevId"));
            event.setMessageText(errorString);
            event.setSentToDevId(details.getString("sentToId"));

            String messageId = details.getString("msgId");
            if (!TextUtils.isEmpty(messageId)) {
                event.setMessageId(messageId);
                UUID uuid = UUID.fromString(messageId);
                event.setTime(uuid.timestamp());
            }
        } catch (JSONException exception) {
            // failure to populate event fields, caller has to handle it
        }
        return event;
    }

    /**
     * Mark passed messages as read and send read receipt if message requires that.
     *
     * @param events Array of events to remove from conversation. Assumption is that all events are
     *               from one and the same conversation.
     */

    public static void removeMessage(@Nullable final Event... events) {
        AsyncUtils.execute(new AsyncTask<Event, String, String>() {

            @Override
            protected String doInBackground(Event... events) {

                if (events == null || events.length < 1) {
                    return null;
                }

                AxoMessaging axoMessaging = AxoMessaging.getInstance(SilentPhoneApplication.getAppContext());
                if (!axoMessaging.isRegistered()) {
                    return null;
                }

                String conversationId = null;
                ConversationRepository repository = axoMessaging.getConversations();
                Conversation conversation = null;
                EventRepository history = null;

                for (Event event : events) {
                    if (conversation == null) {
                        conversationId = MessageUtils.getConversationId(event);
                        conversation = repository.findByPartner(conversationId);
                        history = repository.historyOf(conversation);
                    }

                    if (event instanceof Message) {
                        Message message = (Message) event;
                        deleteEvent(SilentPhoneApplication.getAppContext(), repository, conversation, message);
                    }
                    else {
                        if (history != null) {
                            history.remove(event);
                        }
                    }
                }
                return conversationId;
            }

            @Override
            protected void onPostExecute(String conversationId) {
                /*
                 * TODO: instead of updating all conversation pass event ids that were removed to caller
                 * If an animation is running during such full update, it is cancelled. Granular approach
                 * would allow for smoother animations in chat fragment.
                 */
                if (!TextUtils.isEmpty(conversationId)) {
                    notifyConversationUpdated(SilentPhoneApplication.getAppContext(), conversationId, true);
                }
            }

        }, events);
    }

    public static void burnMessage(final Context context, final Event event) {

        // Recipient for burn notice is sender for incoming message and
        // conversation partner for outgoing message.
        final String conversationId = getConversationId(event);

        if (!TextUtils.isEmpty(conversationId)) {

            AsyncUtils.execute(new AsyncTask<Event, String, Event>() {

                @Override
                protected Event doInBackground(Event... msg) {

                    final ConversationRepository repository =
                            AxoMessaging.getInstance(context.getApplicationContext()).getConversations();
                    final Conversation conversation = repository.findByPartner(conversationId);
                    EventRepository events = repository.historyOf(conversation);

                    // We may need so send some request to the SIP server in case it has
                    // the message still in its store-forward-queue
                    Event event = msg[0];
                    if (event instanceof Message) {
                        Message message = (Message) event;
                        if (isBurnable(message)) {
                            AxoMessaging.getInstance(context).sendBurnNoticeRequest(
                                    (Message) event, conversationId);
                        }

                        // if message is already expired, it can be already removed
                        if (events != null) {
                            if (!message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
                                deleteEvent(context, repository, conversation, message);
                            }
                            else {
                                message.setState(MessageStates.BURNED);
                                events.save(message);
                            }
                        }
                    }
                    else {
                        events.remove(event);
                    }

                    return event;
                }

                @Override
                protected void onPostExecute(Event event) {
                    if (event != null) {
                        notifyConversationUpdated(context, conversationId, false, event.getId(),
                                AxoMessaging.UPDATE_ACTION_MESSAGE_BURNED);
                    }
                    else {
                        notifyConversationUpdated(context, conversationId, true);
                    }
                }

            }, event);
        }
    }

    /**
     * Returns conversation partner for a {@link Message}.
     *
     * @return Conversation partner for message or null if passed message is null or not of type
     *         IncomingMessage or OutgoingMessage.
     */
    @Nullable
    public static String getConversationId(final Event message) {
        String conversationId = null;
        if (message != null) {
            if (message instanceof OutgoingMessage) {
                conversationId = message.getConversationID();
            } else if (message instanceof IncomingMessage) {
                conversationId = ((Message) message).getSender();
            } else if (message instanceof  ErrorEvent) {
                conversationId = ((ErrorEvent) message).getSender();
            }
        }
        return conversationId;

    }

    /**
     * Send an UPDATE_CONVERSATION broadcast for given conversationId.
     *
     */
    public static void notifyConversationUpdated(final Context context, final String conversationId, boolean forceRefresh) {
        final Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, conversationId);
        Extra.FORCE.to(intent, forceRefresh);
        context.sendOrderedBroadcast(intent, Manifest.permission.READ);
    }

    /**
     * Send an UPDATE_CONVERSATION broadcast for given conversationId with details which message
     * is affected by what action.
     */
    public static void notifyConversationUpdated(final Context context, final String conversationId,
            boolean forceRefresh, final String messageId, int updateAction) {
        final Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, conversationId);
        Extra.FORCE.to(intent, forceRefresh);
        Extra.ID.to(intent, messageId);
        Extra.REASON.to(intent, updateAction);
        context.sendOrderedBroadcast(intent, Manifest.permission.READ);
    }


    /**
     * Delete event from repository.
     */
    public static void deleteEvent(final Context context, final ConversationRepository repository,
            final Conversation conversation, final Event event) {

        if (repository != null && conversation != null) {
            repository.historyOf(conversation).remove(event);

            if (event instanceof Message) {
                startAttachmentCleanUp(context, conversation.getPartner().getUserId(), ((Message) event));
            }
        }
    }

    /**
     * Remove a possible temporary attachment files (rare).
     */
    public static void startAttachmentCleanUp(Context context, String conversationId, Message message) {
        if (message.hasAttachment()) {
            Intent cleanupIntent = Action.PURGE_ATTACHMENTS.intent(context, SCloudCleanupService.class);
            cleanupIntent.putExtra("KEEP_STATUS", false);
            Extra.PARTNER.to(cleanupIntent, conversationId);
            Extra.ID.to(cleanupIntent, message.getId());
            context.startService(cleanupIntent);
        }
    }

    public static boolean isBurnable(final Message message) {
        int messageState = message.getState();
        return (MessageStates.DELIVERED == messageState
                || MessageStates.READ == messageState
                || MessageStates.SENT_TO_SERVER == messageState
                || MessageStates.SYNC == messageState);
    }

    public static void showEventInfoDialog(final Context context, final Event event) {

        JSONObject json = new JSONEventAdapter().adapt(event);

        if (json.has("metaData")) {
            JSONObject metaDataJson;

            try {
                metaDataJson = new JSONObject(json.get("metaData").toString());

                if (metaDataJson.has("preview")) {
                    metaDataJson.remove("preview");
                    metaDataJson.put("preview", "<removed>");

                    json.remove("metaData");
                    json.put("metaData", metaDataJson);
                }
            } catch(JSONException ignore) {}
        }

        String formattedInfo = event.toFormattedString();

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.messaging_message_information_title);
        alert.setView(new MessageInformationView(context, formattedInfo, json));
        alert.show();
    }

    public static SCloudService.AttachmentState getAttachmentState(final Message message) {
        SCloudService.AttachmentState state =
                SCloudService.DB.getAttachmentState(message.getId(),
                        message.getConversationID() != null
                                ? message.getConversationID() : message.getSender());
        return state;
    }

    public static List<Event> filter(List<Event> events, boolean keepErrorEvents) {
        Iterator<Event> iterator = events.iterator();
        Set<String> messageIds = new HashSet<>();
        List<ErrorEvent> errorEvents = new ArrayList<>();
        while (iterator.hasNext()) {
            Event event = iterator.next();
            if (event instanceof Message) {
                Message message = (Message) event;
                if (message instanceof IncomingMessage) {
                    messageIds.add(message.getId());
                }
                switch (message.getState()) {
                    case MessageStates.BURNED:
                        iterator.remove();
                        break;
                    case MessageStates.COMPOSED:
                        // TODO: remove if 'isOutgoingResendRequest'
                        break;
                    default:
                        if (message.isExpired()) {
                            iterator.remove();
                        }
                        break;
                }

                // TODO: remove incoming messages which are not chat messages
                // or not messages with attachment
            }
            else if (event instanceof ErrorEvent) {
                // to avoid showing errors for existing incoming/outgoing messages
                // keep track of error events.
                ErrorEvent errorEvent = (ErrorEvent) event;
                int errorId = errorEvent.getError();
                // show error events only if allowed or a critical error
                if (keepErrorEvents
                        || errorId == MessageErrorCodes.RECEIVE_ID_WRONG
                        || errorId == MessageErrorCodes.SENDER_ID_WRONG) {
                    String messageId = errorEvent.getMessageId();
                    if (!TextUtils.isEmpty(messageId)) {
                        errorEvents.add(errorEvent);
                    }
                }
                else {
                    iterator.remove();
                }
            }
        }

        // clear those error events for which a message has been received
        for (ErrorEvent errorEvent : errorEvents) {
            String messageId = errorEvent.getMessageId();
            if (messageId != null && messageIds.contains(messageId)) {
                events.remove(errorEvent);
            }
        }

        Collections.sort(events);

        /*
         * at this point messages are sorted by the time set when arrived on local device
         * sort incoming messages in order they have been created on sender's device
         */
        return sortEventsById(events);
    }

    public static List<Event> sortEventsById(List<Event> events) {
        List<Event> result = new ArrayList<>();
        List<Event> incomingEventsGroup = new ArrayList<>();
        for (Event event : events) {
            if (event instanceof IncomingMessage) {
                incomingEventsGroup.add(event);
            }
            else {
                Collections.sort(incomingEventsGroup, Event.EVENT_ID_COMPARATOR);
                result.addAll(incomingEventsGroup);
                incomingEventsGroup.clear();

                result.add(event);
            }
        }
        Collections.sort(incomingEventsGroup, Event.EVENT_ID_COMPARATOR);
        result.addAll(incomingEventsGroup);

        return result;
    }

    /**
     * Mark passed messages as read and send read receipt if message requires that.
     *
     * @param messages Array of messages to mark as read. Assumption is that all messages are from
     *                 one and the same conversation.
     */
    public static void markMessagesAsRead(@Nullable Message... messages) {

        AsyncUtils.execute(new AsyncTask<Message, String, String>() {

            @Override
            protected String doInBackground(Message... messages) {
                if (messages == null || messages.length < 1) {
                    return null;
                }

                AxoMessaging axoMessaging = AxoMessaging.getInstance(SilentPhoneApplication.getAppContext());
                if (!axoMessaging.isRegistered()) {
                    return null;
                }

                String conversationId = null;
                ConversationRepository repository = axoMessaging.getConversations();
                Conversation conversation = null;
                EventRepository history = null;

                long currentMillis = System.currentTimeMillis();
                for (Message message : messages) {
                    if (message.getState() != MessageStates.RECEIVED) {
                        continue;
                    }
                    message.setState(MessageStates.READ);
                    if (message.expires() && message.getExpirationTime() == Message.DEFAULT_EXPIRATION_TIME) {
                        message.setExpirationTime(currentMillis + message.getBurnNotice() * 1000L);
                    }
                    if (message.isRequestReceipt()) {
                        axoMessaging.sendReadNotification(message);
                    }

                    if (conversation == null) {
                        conversationId = MessageUtils.getConversationId(message);
                        conversation = repository.findByPartner(conversationId);
                        history = repository.historyOf(conversation);
                    }

                    if (history != null) {
                        history.save(message);
                    }
                }

                /* update unread message count - decrement by messages read */
                int unreadMessageCount = conversation != null ? conversation.getUnreadMessageCount() : 0;
                if (unreadMessageCount > 0) {
                    unreadMessageCount = Math.max(0, unreadMessageCount - messages.length);
                    conversation.setUnreadMessageCount(unreadMessageCount);
                    repository.save(conversation);
                }

                return conversationId;
            }

            @Override
            protected void onPostExecute(String conversationId) {
                /*
                 * TODO: instead of updating all conversation pass event ids that were removed to caller
                 * If an animation is running during such full update, it is cancelled. Granular approach
                 * would allow for smoother animations in chat fragment.
                 */
                if (!TextUtils.isEmpty(conversationId)) {
                    notifyConversationUpdated(SilentPhoneApplication.getAppContext(), conversationId, true);
                }
            }

        }, messages);
    }
}
