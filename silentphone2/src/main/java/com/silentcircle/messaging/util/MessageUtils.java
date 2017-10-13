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
package com.silentcircle.messaging.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.messaging.group.GroupMessaging;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.CallData;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.RetentionInfo;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.EventDeviceInfo;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.MessageStateEvent;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.SCloudCleanupService;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.views.DebugInformationView;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import zina.JsonStrings;
import zina.ZinaNative;

import static com.silentcircle.messaging.model.MessageStates.messageStateToStringId;
import static com.silentcircle.messaging.model.json.JSONEventAdapter.TAG_MESSAGE_METADATA;
import static com.silentcircle.messaging.services.SCloudService.SCLOUD_METADATA_THUMBNAIL;
import static zina.JsonStrings.ADD_MEMBERS;
import static zina.JsonStrings.ATTRIBUTE_CALL_DURATION;
import static zina.JsonStrings.ATTRIBUTE_CALL_ERROR;
import static zina.JsonStrings.ATTRIBUTE_CALL_TYPE;
import static zina.JsonStrings.ATTRIBUTE_READ_RECEIPT;
import static zina.JsonStrings.ATTRIBUTE_SHRED_AFTER;
import static zina.JsonStrings.DR_STATUS_BITS;
import static zina.JsonStrings.HELLO;
import static zina.JsonStrings.LEAVE;
import static zina.JsonStrings.NEW_BURN;
import static zina.JsonStrings.NEW_NAME;
import static zina.JsonStrings.RM_MEMBERS;

/**
 * Utilities to handle conversation message saving.
 */
public class MessageUtils {

    private static final String TAG = MessageUtils.class.getSimpleName();

    private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance();
    private static final SimpleDateFormat DEBUG_DATE_FORMAT =
            new SimpleDateFormat("dd/MM/yy hh:mm:ss.SSS", Locale.getDefault());

    private static final int REQUEST_IDS_MAX = 50;

    private MessageUtils() {
    }

    public static Message composeMessage(final String sender, String text,
                                         boolean shouldRequestDeliveryNotification, Conversation conversation,
                                         Location location, boolean isRetained) {
        return composeMessage(sender, text,
                shouldRequestDeliveryNotification, conversation, location, null, null, isRetained);
    }

    public static Message composeMessage(final String sender, String text,
            boolean shouldRequestDeliveryNotification, Conversation conversation,
            Location location, String attachment, CallData callData, boolean isRetained) {
        Message message = callData == null ? new OutgoingMessage(sender, text)
                : new CallMessage(sender, callData);
        message.setConversationID(conversation.getPartner().getUserId());
        message.setId(UUIDGen.makeType1UUID().toString());
        message.setRetained(isRetained);
        if (isRetained) {
            updateRetentionInfo(message);
        }

        if (conversation.hasBurnNotice()) {
            message.setBurnNotice(conversation.getBurnDelay());
        }

        try {
            message.removeAttribute();
            message.removeAttachment();
            JSONObject attributeJson = new JSONObject();
            if (shouldRequestDeliveryNotification) {
                attributeJson.put(ATTRIBUTE_READ_RECEIPT, true);
            }

            if (conversation.hasBurnNotice()) {
                attributeJson.put(ATTRIBUTE_SHRED_AFTER, conversation.getBurnDelay());
            }

            LocationUtils.locationToJSON(attributeJson, location);

            if (!TextUtils.isEmpty(attachment)) {
                message.setAttachment(attachment);
            }

            if (callData != null) {
                attributeJson.put(ATTRIBUTE_CALL_TYPE, callData.type);
                attributeJson.put(ATTRIBUTE_CALL_DURATION, callData.duration);
                if (!TextUtils.isEmpty(callData.errorMessage)) {
                    attributeJson.put(ATTRIBUTE_CALL_ERROR, callData.errorMessage);
                }
            }

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
                attributeJson.put(ATTRIBUTE_READ_RECEIPT, true);
            }

            if (burnDelay != 0) {
                attributeJson.put(ATTRIBUTE_SHRED_AFTER, burnDelay);
            }

            LocationUtils.messageLocationToJSON(attributeJson, location);

            result = attributeJson.toString();
        } catch (JSONException exception) {
            // Failed to prepare message attributes, return empty json
        }
        return result;
    }

    public static void forwardMessage(final Context context, final String sender,
            final String recipient, final Message forwardedMessage, final boolean isRetained) {

        ZinaMessaging msgService = ZinaMessaging.getInstance();
        ConversationRepository repository = msgService.getConversations();

        Conversation conversation = msgService.getOrCreateConversation(recipient);
        Message message = MessageUtils.composeMessage(sender, "",
                true, conversation, null, isRetained);
        EventRepository events = repository.historyOf(conversation);

        if (forwardedMessage.hasAttachment()) {
            message.setAttachment(forwardedMessage.getAttachment());
        }

        if (forwardedMessage.hasMetaData()) {
            message.setMetaData(forwardedMessage.getMetaData());
        }

        message.setState(MessageStates.COMPOSING);
        message.setText(forwardedMessage.getText());

        events.save(message);

        SendMessageTask task = new SendMessageTask(context.getApplicationContext());
        AsyncUtils.execute(task, message);
    }

    public static OutgoingMessage getLastOutgoingMessage(final String partner) {
        EventRepository eventRepository = getEventRepository(partner);

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

    @NonNull
    public static List<Message> getOutgoingMessagesYoungerThan(@Nullable final String partner,
            final long timeStamp) {
        List<Message> result = new ArrayList<>();
        EventRepository eventRepository = getEventRepository(partner);

        if (eventRepository == null) {
            return result;
        }

        final List<Event> events = sortEventsById(eventRepository.list());
        // TODO: use list iterator and do not loop through all events
        // ListIterator<Event> iterator = events.listIterator(events.size());
        for (Event event : events) {
            if (event instanceof OutgoingMessage && event.getComposeTime() >= timeStamp) {
                result.add((Message) event);
            }
        }

        return result;
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
            android.util.Log.e(TAG, "Unable to save message to file: " + e.getMessage());
        } finally {
            IOUtils.close(outputStream);
        }
    }

    /**
     * Retrieve an event from a conversation with specified partner by message id.
     *
     * @param partner Conversation partner
     * @param messageId Message identifier (UUID string)
     *
     * @return event which matches passed messageId or null if not found or conversation for partner
     *         not started,
     */
    @Nullable
    public static Event getEventById( @Nullable final String partner,
            @Nullable final String messageId) {
        Event event = null;

        ConversationRepository conversations =
                ZinaMessaging.getInstance().getConversations();
        Conversation conversation = conversations.findByPartner(partner);
        if (conversation != null) {
            event = conversations.historyOf(conversation).findById(messageId);
        }

        return event;
    }

    public static EventRepository getEventRepository(@Nullable String partner) {
        if (partner == null) {
            return null;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
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
            msg.setRequestReceipt(attributeJson.optBoolean(ATTRIBUTE_READ_RECEIPT, false));
            if (attributeJson.has(ATTRIBUTE_SHRED_AFTER)) {
                msg.setBurnNotice(attributeJson.getLong(ATTRIBUTE_SHRED_AFTER));
                msg.setRequestReceipt(true);
            }
            msg.setRetained(attributeJson.optInt(DR_STATUS_BITS, 0) != 0);
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
                attributeJson.put(ATTRIBUTE_READ_RECEIPT, true);
            }

            if (msg.hasBurnNotice()) {
                attributeJson.put(ATTRIBUTE_SHRED_AFTER, msg.getBurnNotice());
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
        Log.d(TAG, "parseErrorMessage: Parsing error: " + errorString);
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
            // for regular conversations conversation id is partner's name
            // for group conversations conversation id is group id
            String groupId = json.optString("grpId", null);
            event.setConversationID(TextUtils.isEmpty(groupId)
                    ? Utilities.removeSipParts(details.getString("name"))
                    : groupId);

            String messageId = details.getString("msgId");
            if (!TextUtils.isEmpty(messageId)) {
                event.setMessageId(messageId);
                UUID uuid = UUID.fromString(messageId);
                event.setTime(uuid.timestamp());
            }
        } catch (IllegalArgumentException | JSONException exception) {
            // failure to populate event fields, caller has to handle it
        }
        return event;
    }

    /**
     * Remove passed events from conversation's event repository.
     *
     * @param events Array of events to remove from conversation. Assumption is that all events are
     *               from one and the same conversation.
     */
    public static void removeMessage(@Nullable final Event... events) {
        if (events == null || events.length <= 0) {
            return;
        }

        removeMessage(getConversationId(events[0]), events);
    }

    /**
     * Remove passed events from conversation's event repository.
     *
     * @param conversationId Identifier of conversation events belong to.
     * @param events Array of events to remove from conversation.
     */
    public static void removeMessage(@Nullable final String conversationId, @Nullable final Event... events) {
        if (TextUtils.isEmpty(conversationId) || events == null || events.length <= 0) {
            return;
        }

        AsyncUtils.execute(new AsyncTask<Event, String, String>() {

            @Override
            protected String doInBackground(Event... events) {

                Context context = SilentPhoneApplication.getAppContext();
                ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
                if (!axoMessaging.isRegistered()) {
                    return null;
                }

                int position = 0;
                CharSequence[] ids = new CharSequence[events.length];

                ConversationRepository repository = axoMessaging.getConversations();
                final Conversation conversation = repository.findByPartner(conversationId);
                if (conversation == null) {
                    // conversation is not known, return.
                    return null;
                }

                final EventRepository history = repository.historyOf(conversation);

                for (Event event : events) {

                    ids[position++] = event.getId();
                    if (event instanceof Message) {
                        Message message = (Message) event;
                        deleteEvent(context, repository, conversation, message);
                    }
                    else {
                        if (history != null) {
                            history.remove(event);
                        }
                    }
                }

                notifyConversationUpdated(context, conversationId, false,
                        ZinaMessaging.UPDATE_ACTION_MESSAGE_BURNED, ids);

                return conversationId;
            }

        }, events);
    }

    /**
     *  Remove passed events from conversation's event repository.
     *
     * @param conversationId Conversation identifier.
     * @param eventIds Array of event ids to remove from conversation.
     */
    public static void removeMessage(@Nullable final String conversationId,
            @Nullable final String... eventIds) {
        if (TextUtils.isEmpty(conversationId) || eventIds == null || eventIds.length <= 0) {
            return;
        }

        AsyncUtils.execute(new AsyncTask<String, String, Void>() {

            @Override
            protected Void doInBackground(String... eventIds) {

                Context context = SilentPhoneApplication.getAppContext();

                ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
                if (!axoMessaging.isRegistered()) {
                    return null;
                }

                int position = 0;
                CharSequence[] ids = new CharSequence[eventIds.length];

                final ConversationRepository repository = axoMessaging.getConversations();
                final Conversation conversation = repository.findByPartner(conversationId);
                if (conversation == null) {
                    // conversation is not known, return.
                    return null;
                }

                final EventRepository history = repository.historyOf(conversation);

                int unreadMessageCount = 0;
                for (String eventId : eventIds) {
                    ids[position++] = eventId;
                    Event event = MessageUtils.getEventById(conversationId, eventId);
                    if (event instanceof Message) {
                        Message message = (Message) event;
                        if (message.getState() == MessageStates.RECEIVED) {
                            unreadMessageCount++;
                        }

                        // TODO use ZinaMessaging#burnPacket (?)
                        deleteEvent(context, repository, conversation, message);
                    }
                    else {
                        if (history != null) {
                            history.remove(eventId);
                        }
                    }
                }
                if (unreadMessageCount > 0) {
                    unreadMessageCount = Math.max(0, conversation.getUnreadMessageCount() - unreadMessageCount);
                    conversation.setUnreadMessageCount(unreadMessageCount);
                    repository.save(conversation);
                }

                notifyConversationUpdated(context, conversationId, false,
                        ZinaMessaging.UPDATE_ACTION_MESSAGE_BURNED, ids);
                return null;
            }

        }, eventIds);
    }

    public static void burnMessage(final Context context, final Event... events) {
        if (events == null || events.length < 1) {
            return;
        }
        // Recipient for burn notice is sender for incoming message and
        // conversation partner for outgoing message.
        final String conversationId = getConversationId(events[0]);

        if (!TextUtils.isEmpty(conversationId)) {

            AsyncUtils.execute(new AsyncTask<Event, String, String>() {

                @Override
                protected String doInBackground(Event... burnableEvents) {

                    final ZinaMessaging axoMessaging =
                            ZinaMessaging.getInstance();
                    final ConversationRepository repository =
                            axoMessaging.getConversations();
                    final Conversation conversation = repository.findByPartner(conversationId);
                    final EventRepository eventRepository =
                            conversation == null ? null : repository.historyOf(conversation);
                    final boolean isGroup = conversation != null && conversation.getPartner().isGroup();

                    // We may need so send some request to the SIP server in case it has
                    // the message still in its store-forward-queue

                    int position = 0;
                    CharSequence[] ids = new CharSequence[burnableEvents.length];
                    List<Message> burnableMessages = new ArrayList<>();

                    for (Event event : burnableEvents) {
                        if (event instanceof IncomingMessage || event instanceof OutgoingMessage) {
                            final Message message = (Message) event;
                            int messageState = message.getState();
                            message.setState(MessageStates.BURNED);

                            if ((MessageStates.DELIVERED == messageState
                                    || MessageStates.READ == messageState
                                    || MessageStates.SENT_TO_SERVER == messageState
                                    || MessageStates.SYNC == messageState)
                                    /*
                                     * Avoid sending burn requests to phone numbers
                                     *
                                     * FIXME: use this check when call events can be burned on remote devices
                                     */
                                    /* && Utilities.canMessage(conversationId) */

                                    /*
                                     * There is no burn notification for incoming group messages.
                                     * Burn request can be sent only for one's own sent messages.
                                     */
                                    && (!isGroup || ((event instanceof OutgoingMessage)))) {
                                burnableMessages.add(message);
                                message.setText("");
                                message.setFailureFlag(Message.FAILURE_BURN_NOTIFICATION);
                            } else {
                                deleteEvent(context, eventRepository, conversationId, message);
                            }

                            if (eventRepository != null) {
                                eventRepository.save(message);
                            }
                        } else {
                            if (eventRepository != null) {
                                eventRepository.remove(event);
                            }
                        }
                        ids[position++] = event.getId();
                    }

                    notifyConversationUpdated(context, conversationId, false,
                            ZinaMessaging.UPDATE_ACTION_MESSAGE_BURNED, ids);

                    // sending out burn request takes most of the time, especially if the queue
                    // is long.
                    // do it after notifying UI about changes
                    List<String> burnableMessageIds = new ArrayList<>(burnableMessages.size());
                    for (Message message : burnableMessages) {
                        if (isGroup) {
                            burnableMessageIds.add(message.getId());
                        }
                        else {
                            axoMessaging.sendBurnNoticeRequest(message, conversationId);
                        }
                    }
                    if (isGroup && burnableMessageIds.size() > 0) {
                        /*
                         * Workaround:
                         *
                         * When network is not available, Zina will accept
                         * command successfully and notify about issue in group state change callback.
                         * At that point it currently is not possible to tie which command or message
                         * failed.
                         *
                         * Do not burn messages, if network is not available. Messages
                         * will be marked as burned and burn request will be sent when network
                         * is available again.
                         */
                        final boolean isNetworkAvailable = Utilities.isNetworkConnected(context);
                        int result = ZinaNative.burnGroupMessage(conversationId,
                                burnableMessageIds.toArray(new String[burnableMessageIds.size()]));
                        if (result == MessageErrorCodes.SUCCESS || result == MessageErrorCodes.OK) {
                            result = ZinaMessaging.applyGroupChangeSet(conversationId);
                        }
                        if (result != MessageErrorCodes.SUCCESS || !isNetworkAvailable) {
                            Log.w(TAG, "Could not burn group messages "
                                    + "network available: " + isNetworkAvailable
                                    + ", " + result
                                    + ", " + ZinaMessaging.getErrorInfo()
                                    + " (" + ZinaMessaging.getErrorCode() + ")");
                        }
                        else {
                            // sending was successful, clear failure flag so messages get deleted
                            for (Message message : burnableMessages) {
                                message.clearFailureFlag(Message.FAILURE_BURN_NOTIFICATION);
                            }
                        }
                    }
                    for (Message message : burnableMessages) {
                        // if message is already expired, it can be already removed
                        if (!(message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION))) {
                            deleteEvent(context, eventRepository, conversationId, message);
                        }
                    }
                    return conversationId;
                }
            }, events);
        }
    }

    /**
     * Returns conversation partner for a {@link Message}.
     *
     * @return Conversation partner for message or null if passed message is null or not of type
     *         IncomingMessage or OutgoingMessage.
     */
    @Nullable
    public static String getConversationId(final @Nullable Event message) {
        String conversationId = null;
        if (message != null) {
            if (message instanceof OutgoingMessage) {
                conversationId = message.getConversationID();
            } else if (message instanceof IncomingMessage) {
                // TODO conversation id field of incoming message should reflect conversation id
                // sender should be actual sender
                conversationId = message.getConversationID();
                if (TextUtils.isEmpty(conversationId)) {
                    conversationId = ((Message) message).getSender();
                }
            } else if (message instanceof CallMessage) {
                conversationId = message.getConversationID();
            } else if (message instanceof  ErrorEvent) {
                conversationId = ((ErrorEvent) message).getSender();
            } else if (message instanceof MessageStateEvent) {
                conversationId = message.getConversationID();
            } else {
                conversationId = message.getConversationID();
            }
        }
        return conversationId;
    }

    /**
     * Create a UPDATE_CONVERSATION intent for given conversationId.
     */
    public static Intent getNotifyConversationUpdatedIntent(@Nullable final String conversationId,
            boolean forceRefresh) {
        final Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, conversationId);
        Extra.FORCE.to(intent, forceRefresh);
        return intent;
    }

    /**
     * Send an UPDATE_CONVERSATION broadcast for given conversationId.
     */
    public static void notifyConversationUpdated(@NonNull final Context context,
            @Nullable final String conversationId, boolean forceRefresh) {

        final Intent intent = getNotifyConversationUpdatedIntent(conversationId, forceRefresh);
        MessagingBroadcastManager.getInstance(context).sendOrderedBroadcast(intent);
    }

    /**
     * Send an UPDATE_CONVERSATION broadcast for given conversationId with action which caused
     * update and ids of messages which were updated.
     */
    public static void notifyConversationUpdated(final Context context, final String conversationId,
            boolean forceRefresh, int updateAction, final CharSequence... messageIds) {
        if (messageIds == null || messageIds.length == 0) {
            return;
        }

        final Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, conversationId);
        Extra.FORCE.to(intent, forceRefresh);
        Extra.IDS.to(intent, messageIds);
        Extra.REASON.to(intent, updateAction);
        MessagingBroadcastManager.getInstance(context).sendOrderedBroadcast(intent);
    }

    public static void notifyConversationDrEvent(final Context context, final String conversationId,
            String reason, final CharSequence... messageIds) {
        if (messageIds == null || messageIds.length == 0) {
            return;
        }
        final Intent intent = Action.DATA_RETENTION_EVENT.intent();
        Extra.PARTNER.to(intent, conversationId);
        Extra.IDS.to(intent, messageIds);
        Extra.REASON.to(intent, reason);
        MessagingBroadcastManager.getInstance(context).sendOrderedBroadcast(intent);
    }

    public static void notifyMessageReceived(final Context context, final String conversationId,
            final String alias, boolean forceRefresh, boolean mute, final CharSequence... messageIds) {
        if (messageIds == null || messageIds.length == 0) {
            return;
        }

        final Intent intent = Action.RECEIVE_MESSAGE.intent();
        Extra.PARTNER.to(intent, conversationId);
        if (!TextUtils.isEmpty(alias)) {
            Extra.ALIAS.to(intent, alias);
        }
        Extra.FORCE.to(intent, forceRefresh);
        Extra.IDS.to(intent, messageIds);
        if (mute) {
            Extra.MUTE.to(intent, true);
        }
        MessagingBroadcastManager.getInstance(context).sendOrderedBroadcast(intent);
    }

    public static void notifyContactUpdated(final Context context, final String conversationId) {
        final Intent intent = Action.REFRESH_CONTACT.intent();
        Extra.PARTNER.to(intent, conversationId);
        MessagingBroadcastManager.getInstance(context).sendOrderedBroadcast(intent);
    }

    /**
     * Delete event from repository.
     */
    public static void deleteEvent(@NonNull final Context context,
            @Nullable final ConversationRepository repository,
            @Nullable final Conversation conversation, @NonNull final Event event) {

        if (repository != null && conversation != null) {
            deleteEvent(context, repository.historyOf(conversation),
                    conversation.getPartner().getUserId(), event);
        }
    }

    /**
     * Delete event from repository.
     */
    public static void deleteEvent(@NonNull final Context context,
            @Nullable final EventRepository eventRepository, @Nullable final String conversationId,
            @NonNull final Event event) {

        if (eventRepository != null) {
            eventRepository.remove(event);

            if (event instanceof Message) {
                startAttachmentCleanUp(context, conversationId, ((Message) event));
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

    public static void showPartnerInfoDialog(final Context context,
                                             final String displayName, final String uuid) {
        StringBuilder aliasSb = new StringBuilder();
        JSONArray aliasJson = new JSONArray();
        byte[][] aliases = ZinaNative.getAliases(uuid);
        if (aliases != null) {
            for (int i = 0; i < aliases.length; i++) {
                try {
                    String alias = new String(aliases[i], "UTF-8");
                    aliasSb.append(alias).append(',');
                    aliasJson.put(alias);
                } catch (Exception ignore) {
                }
            }
        }

        JSONObject json = new JSONObject();
        try {
            json.put("display_name", displayName != null ? displayName : "N/A");
            json.put("uuid", uuid);
            json.put("aliases", aliasJson);
        } catch(JSONException ignore) {}

        String formattedInfo = "display_name: " + displayName + "\n"
                + "uuid: " + uuid + "\n"
                + "aliases: " + StringUtils.rtrim(aliasSb.toString(), ',') + "\n";

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.debug_information_title);
        alert.setView(new DebugInformationView(context, formattedInfo, json));
        alert.show();
    }

    public static void showEventInfoDialog(final Context context, final Event event) {

        JSONObject json = new JSONEventAdapter().adapt(event);

        // The following code just shows how to use the loadCapturedMsgs function. Here
        // we select all messages for one message Id: the message itself and related messages
        // like sync command, delivery and read receipts etc. The just log it, no fancy UI yet :-)
        if (BuildConfig.DEBUG) {
            int[] code = new int[1];
            String id = (event instanceof ErrorEvent) ? ((ErrorEvent) event).getMessageId() : event.getId();
            byte[][] trace = ZinaNative.loadCapturedMsgs(null, IOUtils.encode(id), null, code);

            for (byte[] data : trace) {
                Log.d(TAG, "showEventInfoDialog: " + new String(data));
            }
        }
        if (json.has(TAG_MESSAGE_METADATA)) {
            JSONObject metaDataJson;

            try {
                metaDataJson = new JSONObject(json.get(TAG_MESSAGE_METADATA).toString());

                if (metaDataJson.has(SCLOUD_METADATA_THUMBNAIL)) {
                    metaDataJson.remove(SCLOUD_METADATA_THUMBNAIL);
                    metaDataJson.put(SCLOUD_METADATA_THUMBNAIL, "<removed>");

                    json.remove(TAG_MESSAGE_METADATA);
                    json.put(TAG_MESSAGE_METADATA, metaDataJson);
                }
            } catch(JSONException ignore) {}
        }

        String formattedInfo = event.toFormattedString();
        EventDeviceInfo[] eventDeviceInfo = event.getEventDeviceInfo();
        StringBuilder sb = new StringBuilder();
        if (eventDeviceInfo != null) {
            int numDev = eventDeviceInfo.length;
            sb.append("\nMessage sent to ").append(numDev).append((numDev > 1 ? " devices:\n" : " device:\n"));
            for (EventDeviceInfo devInfo : eventDeviceInfo) {
                String readableState = context.getString(messageStateToStringId(devInfo.state));
                sb.append(devInfo.deviceName).append(": ").append(readableState).append('\n');
            }
        }
        formattedInfo += sb.toString();

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.debug_information_title);
        alert.setView(new DebugInformationView(context, formattedInfo, json));
        alert.show();
    }

    public static SCloudService.AttachmentState getAttachmentState(final Message message) {
        SCloudService.AttachmentState state =
                SCloudService.DB.getAttachmentState(message.getId(),
                        message.getConversationID() != null
                                ? message.getConversationID() : message.getSender());
        return state;
    }

    @NonNull
    public static List<Event> filter(List<Event> events, boolean keepErrorEvents) {
        Iterator<Event> iterator = events.iterator();
        while (iterator.hasNext()) {
            Event event = iterator.next();
            if (event == null) {
                iterator.remove();
            }
            else if (event instanceof Message) {
                Message message = (Message) event;
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
                if (!(keepErrorEvents
                        || errorId == MessageErrorCodes.RECEIVE_ID_WRONG
                        || errorId == MessageErrorCodes.SENDER_ID_WRONG
                        /**|| errorId == 0**/)) {
                    iterator.remove();
                }
            }
            else if (event instanceof MessageStateEvent) {
                // never show placeholder event, it will be replaced by message when it will arrive
                iterator.remove();
            }
        }

        Collections.sort(events);

        /*
         * at this point messages are sorted by the time set when arrived on local device
         * sort incoming messages in order they have been created on sender's device
         */
        return sortEventsById(events);
    }

    @NonNull
    public static List<Event> sortEventsById(@NonNull List<Event> events) {
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
    public static void markMessagesAsRead(@Nullable final String conversationId, @Nullable Message... messages) {
        if (TextUtils.isEmpty(conversationId) || messages == null || messages.length <= 0) {
            return;
        }

        AsyncUtils.execute(new AsyncTask<Message, String, String>() {

            @Override
            protected String doInBackground(Message... messages) {

                ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
                if (!axoMessaging.isRegistered()) {
                    return null;
                }

                int position = 0;
                CharSequence[] ids = new CharSequence[messages.length];
                Collection<Message> messagesToNotify = new ArrayList<>();

                ConversationRepository repository = axoMessaging.getConversations();
                Conversation conversation = null;
                EventRepository history = null;
                boolean isGroup = false;

                long currentMillis = System.currentTimeMillis();
                for (Message message : messages) {
                    if (message.getState() != MessageStates.RECEIVED
                            && (!(message instanceof CallMessage
                            && message.getState() != MessageStates.READ))) {
                        continue;
                    }
                    message.setState(MessageStates.READ);
                    if (message.expires() && message.getExpirationTime() == Message.DEFAULT_EXPIRATION_TIME) {
                        long burnStartTime = currentMillis;
                        if (conversation != null && conversation.getPartner().isGroup()) {
                            burnStartTime = message.getComposeTime();
                        }
                        message.setExpirationTime(burnStartTime + message.getBurnNotice() * 1000L);
                    }

                    if (conversation == null) {
                        conversation = repository.findByPartner(conversationId);
                        history = conversation == null ? null : repository.historyOf(conversation);
                        isGroup = conversation != null && conversation.getPartner().isGroup();
                    }

                    if (message.isRequestReceipt()) {
                        messagesToNotify.add(message);
                        message.setFailureFlag(Message.FAILURE_READ_NOTIFICATION);
                    }

                    ids[position++] = message.getId();
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

                int unreadCallMessageCount = conversation != null ? conversation.getUnreadCallMessageCount() : 0;
                if (unreadCallMessageCount > 0) {
                    unreadCallMessageCount = Math.max(0, unreadCallMessageCount - messages.length);
                    conversation.setUnreadCallMessageCount(unreadCallMessageCount);
                    repository.save(conversation);
                }

                notifyConversationUpdated(SilentPhoneApplication.getAppContext(), conversationId,
                        true, ZinaMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE, ids);

                if (isGroup) {
                    for (int i = 0; i < ids.length; i += REQUEST_IDS_MAX) {
                        axoMessaging.sendGroupReadNotification(conversationId,
                                Arrays.copyOfRange(ids, i, Math.min(i + REQUEST_IDS_MAX, ids.length)));
                    }
                }
                else {
                    for (Message message : messagesToNotify) {
                        axoMessaging.sendReadNotification(message);
                    }
                }

                return conversationId;
            }

        }, messages);
    }

    public static void markMessageAsRetained(@NonNull Message message) {
        message.setRetained(true);

        String conversationId = getConversationId(message);
        EventRepository history = getEventRepository(conversationId);
        if (history != null) {
            history.save(message);
        }

        notifyConversationUpdated(SilentPhoneApplication.getAppContext(),
                conversationId, false, ZinaMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE,
                message.getId());
    }

    public static void requestRefresh() {
        requestRefresh(null);
    }

    public static void requestRefresh(@Nullable String conversationId) {
        final Intent intent = Action.REFRESH_SELF.intent();
        if (conversationId != null) {
            Extra.PARTNER.to(intent, conversationId);
        }
        MessagingBroadcastManager.getInstance(SilentPhoneApplication.getAppContext())
                .sendOrderedBroadcast(intent);
    }

    public static void updateRetentionInfo(@Nullable Message message) {
        if (message == null || !message.isRetained()) {
            return;
        }
        RetentionInfo retentionData = new RetentionInfo();
        if (LoadUserInfo.isLrmm() | LoadUserInfo.isLrmp() | LoadUserInfo.isLrcm()
                | LoadUserInfo.isLrcp() | LoadUserInfo.isLrap()) {
            retentionData.localRetentionData = new RetentionInfo.DataRetention();
            retentionData.localRetentionData.setForOrgName(LoadUserInfo.getRetentionOrganization());
            retentionData.localRetentionData.setRetentionFlags(LoadUserInfo.isLrmm(),
                    LoadUserInfo.isLrmp(), LoadUserInfo.isLrcm(), LoadUserInfo.isLrcp(),
                    LoadUserInfo.isLrap());
        }

        String conversationId = getConversationId(message);
        byte[] partnerUserInfo = TextUtils.isEmpty(conversationId)
                ? null : ZinaNative.getUserInfoFromCache(conversationId);
        /*
         * TODO handle case where cached information is not available
         * although with data retention checks in Zina this is improbable
         */
        if (partnerUserInfo != null) {
            AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(partnerUserInfo);
            if (userInfo != null && (userInfo.rrmm | userInfo.rrmp | userInfo.rrcm | userInfo.rrcp
                    | userInfo.rrap)) {
                retentionData.remoteRetentionData = new RetentionInfo.DataRetention[1];
                retentionData.remoteRetentionData[0] = new RetentionInfo.DataRetention();
                retentionData.remoteRetentionData[0].setForOrgName(userInfo.retentionOrganization);
                retentionData.remoteRetentionData[0].setRetentionFlags(userInfo.rrmm, userInfo.rrmp,
                        userInfo.rrcm, userInfo.rrcp, userInfo.rrap);
            }
        }
        message.setRetentionInfo(retentionData);
    }

    /**
     * Determine whether message is intended for specific user.
     *
     * Intended use of this function is to check whether message is intended for the sender himself.
     */
    public static boolean isNoteToUser(Message message, String userName) {
        if (message == null || TextUtils.isEmpty(userName)) {
            return false;
        }
        String conversationId = MessageUtils.getConversationId(message);
        if (BuildConfig.DEBUG) Log.d(TAG, "isNoteToSelf: userName: " + userName + ", conversation id: " + conversationId);
        return !TextUtils.isEmpty(conversationId) && conversationId.equals(userName);
    }

    @Nullable
    @WorkerThread
    public static List<Event> createInfoEvent(@Nullable Context ctx,
            @Nullable GroupMessaging.GroupCommand cmd, @NonNull String selfUserName) {
        if (ctx == null || cmd == null) {
            return null;
        }

        final ConversationRepository repository = ConversationUtils.getConversations();
        if (repository == null) {
            com.silentcircle.logs.Log.e("GroupMessaging", "Could not save group command event");
            return null;
        }

        final Conversation conversation =
                ConversationUtils.getConversation(cmd.getGroupId());
        final EventRepository events = conversation == null ? null : repository.historyOf(conversation);
        if (conversation == null || events == null) {
            com.silentcircle.logs.Log.e("GroupMessaging", "Could not save group command event");
            return null;
        }

        Resources resources = ctx.getResources();
        List<Event> eventsToSave = new ArrayList<>();

        CharSequence displayName = cmd.getMemberId();
        if (TextUtils.equals(selfUserName, cmd.getMemberId())) {
            displayName = resources.getString(R.string.you);
        }
        else if (!TextUtils.isEmpty(displayName)) {
            displayName = ContactsCache.getDisplayName(displayName,
                    ContactsCache.getContactEntry(cmd.getMemberId()));
        }

        int tag;
        String text;
        String details;
        long time = cmd.getTimestamp();

        // TODO code duplication
        switch (cmd.getCommand()) {
            case HELLO:
                text = resources.getString(R.string.group_messaging_hello_received, displayName);
                tag = InfoEvent.INFO_RESPONSE_HELLO;
                details = StringUtils.jsonFromPairs(
                        new Pair<String, Object>(JsonStrings.MSG_USER_ID, displayName),
                        new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                eventsToSave.add(createInfoEvent(cmd.getGroupId(), tag, time, text, details));
            break;
            case LEAVE:
                text = resources.getString(R.string.group_messaging_leave_notification, displayName);
                tag = InfoEvent.INFO_USER_LEFT;
                details = StringUtils.jsonFromPairs(
                        new Pair<String, Object>(JsonStrings.MSG_USER_ID, cmd.getMemberId()),
                        new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                eventsToSave.add(createInfoEvent(cmd.getGroupId(), tag, time, text, details));
                break;
            case RM_MEMBERS:
                List<String> members = cmd.getMembers();
                tag = InfoEvent.INFO_USER_LEFT;
                if (members != null && members.size() > 0) {
                    for (String member : members) {
                        displayName = ContactsCache.getDisplayName(member,
                                ContactsCache.getContactEntry(member));
                        text = resources.getString(R.string.group_messaging_leave_notification, displayName);
                        details = StringUtils.jsonFromPairs(
                                new Pair<String, Object>(JsonStrings.MSG_USER_ID, member),
                                new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                        eventsToSave.add(createInfoEvent(cmd.getGroupId(), tag,
                                time == 0 ? time : time + System.currentTimeMillis() % 1000, text, details));
                    }
                }
                break;
            case ADD_MEMBERS:
                members = cmd.getMembers();
                tag = InfoEvent.INFO_RESPONSE_HELLO;
                if (members != null && members.size() > 0) {
                    for (String member : members) {
                        displayName = ContactsCache.getDisplayName(member,
                                ContactsCache.getContactEntry(member));
                        text = resources.getString(R.string.group_messaging_hello_received, displayName);
                        details = StringUtils.jsonFromPairs(
                                new Pair<String, Object>(JsonStrings.MSG_USER_ID, member),
                                new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                        eventsToSave.add(createInfoEvent(cmd.getGroupId(), tag,
                                time == 0 ? time : time + System.currentTimeMillis() % 1000, text, details));
                    }
                }
                break;
            case NEW_BURN:
                String burnTime = BurnDelay.Defaults.getAlternateLabel(ctx,
                        BurnDelay.Defaults.getLevel(cmd.getBurnTime()));
                if (TextUtils.isEmpty(displayName)) {
                    text = resources.getString(R.string.group_messaging_new_burn, burnTime);
                }
                else {
                    text = resources.getString(R.string.group_messaging_new_burn_by, displayName,
                            burnTime);
                }
                tag = InfoEvent.INFO_NEW_BURN;
                details = StringUtils.jsonFromPairs(
                        new Pair<String, Object>(JsonStrings.GROUP_BURN_SEC, cmd.getBurnTime()),
                        new Pair<String, Object>(JsonStrings.MEMBER_ID, cmd.getMemberId()),
                        new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                eventsToSave.add(createInfoEvent(cmd.getGroupId(), tag, time, text, details));
                break;
            case NEW_NAME:
                if (TextUtils.isEmpty(displayName)) {
                    text = resources.getString(R.string.group_messaging_new_name, cmd.getGroupName());
                }
                else {
                    text = resources.getString(R.string.group_messaging_new_name_by, displayName,
                            cmd.getGroupName());
                }
                tag = InfoEvent.INFO_NEW_GROUP_NAME;
                details = StringUtils.jsonFromPairs(
                        new Pair<String, Object>(JsonStrings.GROUP_NAME, cmd.getGroupName()),
                        new Pair<String, Object>(JsonStrings.MEMBER_ID, cmd.getMemberId()),
                        new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                eventsToSave.add(createInfoEvent(cmd.getGroupId(), tag, time, text, details));
                break;

            default:
                if (BuildConfig.DEBUG) {
                    eventsToSave.add(createInfoEvent(cmd.getGroupId(), InfoEvent.TAG_NOT_SET,
                            cmd.getCommand() + " not processed!", ""));
                }
                break;
        }

        for (Event eventToSave : eventsToSave) {
            events.save(eventToSave);
            MessageUtils.notifyConversationUpdated(ctx, cmd.getGroupId(), true,
                    ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, eventToSave.getId());
        }

        conversation.setLastModified(System.currentTimeMillis());
        repository.save(conversation);

        return eventsToSave;
    }

    public static InfoEvent createInfoEvent(@NonNull String conversationId, int tag, String text,
            String details) {
        return createInfoEvent(conversationId, tag, System.currentTimeMillis(),
                UUIDGen.makeType1UUID().toString(), text, details);
    }

    @SuppressWarnings("WeakerAccess")
    public static InfoEvent createInfoEvent(@NonNull String conversationId, int tag, long time,
            String text, String details) {
        UUID id = time == 0 ? UUIDGen.makeType1UUID() : UUIDGen.makeType1UUID(time);
        return createInfoEvent(conversationId, tag, time, id.toString(), text, details);
    }

    @SuppressWarnings("WeakerAccess")
    public static InfoEvent createInfoEvent(@NonNull String conversationId, int tag, long time,
            String id, String text, String details) {
        InfoEvent event = new InfoEvent();
        event.setConversationID(conversationId);
        event.setId(id);
        event.setTime(time == 0 ? System.currentTimeMillis() : time);
        event.setTag(tag);
        event.setDetails(details);
        event.setText(text);
        return event;
    }

    @NonNull
    @SuppressWarnings("WeakerAccess")
    public static ErrorEvent createErrorEvent(@NonNull String conversationId, int errorCode,
            @Nullable String errorDescription) {
        ErrorEvent event = new ErrorEvent(errorCode);
        event.setConversationID(conversationId);
        event.setId(UUIDGen.makeType1UUID().toString());
        event.setTime(System.currentTimeMillis());
        event.setText(errorDescription);
        return event;
    }

    @Nullable
    public static CharSequence getDisplayName(@Nullable final CharSequence uuid) {
        if (TextUtils.isEmpty(uuid)) {
            return uuid;
        }
        CharSequence result = uuid;
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(uuid.toString());
        if (contactEntry != null && !TextUtils.isEmpty(contactEntry.name)) {
            result = contactEntry.name;
        }

        if (result == uuid && ZinaMessaging.getInstance().isReady()) {
            byte[] dpName = ZinaMessaging.getDisplayName(uuid.toString());
            if (dpName != null) {
                result = new String(dpName);
            }
        }

        return result;
    }

    @Nullable
    public static CharSequence getDisplayNameFromCache(@Nullable final CharSequence uuid) {
        if (TextUtils.isEmpty(uuid)) {
            return uuid;
        }
        CharSequence result = uuid;
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(uuid.toString());
        if (contactEntry != null && !TextUtils.isEmpty(contactEntry.name)) {
            result = contactEntry.name;
        }

        return result;
    }

    /**
     * Return trace information for an event by its id or null if event is null or
     * there is no trace information available.
     *
     * @param event Event for which to retrieve trace information.
     */
    @Nullable
    public static String getMessageTraceInfo(@Nullable final Event event) {
        if (event == null) {
            return null;
        }

        int[] code = new int[1];
        String id = (event instanceof ErrorEvent) ? ((ErrorEvent) event).getMessageId() : event.getId();
        byte[][] trace = ZinaNative.loadCapturedMsgs(null, IOUtils.encode(id), null, code);

        String result = null;
        if (trace != null && trace.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (byte[] data : trace) {
                sb.append(new String(data)).append("\n");
            }
            result = sb.toString();
        }
        return result;
    }

}
