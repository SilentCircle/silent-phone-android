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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.Conversation;
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
import com.silentcircle.messaging.task.SendMessageTask;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
        message.setConversationID(conversation.getPartner().getUsername());
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
                history.append(Utilities.getUsernameFromUriNumber(message.getSender())).append(": ");
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
                Utilities.getUsernameFromUriNumber(partner)));
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

    public static void burnMessage(final Context context, final Message message) {

        // Recipient for burn notice is sender for incoming message and
        // conversation partner for outgoing message.
        final String conversationId = getConversationId(message);

        if (!TextUtils.isEmpty(conversationId)) {

            AsyncUtils.execute(new AsyncTask<Message, String, Message[]>() {

                @Override
                protected Message[] doInBackground(Message... msg) {

                    final ConversationRepository repository =
                            AxoMessaging.getInstance(context.getApplicationContext()).getConversations();
                    final Conversation conversation = repository.findByPartner(conversationId);

                    // We may need so send some request to the SIP server in case it has
                    // the message still in its store-forward-queue
                    Message message = msg[0];
                    if (isBurnable(message)) {
                        AxoMessaging.getInstance(context).sendBurnNoticeRequest(
                                message, conversationId);
                    }


                    // if message is already expired, it can be already removed
                    EventRepository events = repository.historyOf(conversation);
                    if (events != null) {
                        events.remove(message);
                    }
                    return msg;
                }

                @Override
                protected void onPostExecute(Message[] result) {
                    notifyConversationUpdated(context, conversationId);
                }

            }, message);
        }
    }

    /**
     * Returns conversation partner for a {@link Message}.
     *
     * @return Conversation partner for message or null if passed message is null or not of type
     *         IncomingMessage or OutgoingMessage.
     */
    public static String getConversationId(final Message message) {
        String conversationId = null;
        if (message != null) {
            if (message instanceof OutgoingMessage) {
                conversationId = message.getConversationID();
            } else if (message instanceof IncomingMessage) {
                conversationId = message.getSender();
            }
        }
        return conversationId;

    }

    /**
     * Send an UPDATE_CONVERSATION broadcast for given conversationId.
     *
     */
    public static void notifyConversationUpdated(final Context context, final String conversationId) {
        final Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, conversationId);
        context.sendOrderedBroadcast(intent, Manifest.permission.READ);
    }

    public static boolean isBurnable(final Message message) {
        int messageState = message.getState();
        return (MessageStates.DELIVERED == messageState
                || MessageStates.READ == messageState
                || MessageStates.SENT_TO_SERVER == messageState
                || MessageStates.SYNC == messageState);
    }


    public static void showEventInfoDialog(final Context context, final Event event) {
        TextView textView = new TextView(context);
        JSONObject json = new JSONEventAdapter().adapt(event);

        if(json.has("metaData")) {
            JSONObject metaDataJson;

            try {
                metaDataJson = new JSONObject(json.get("metaData").toString());

                if(metaDataJson.has("preview")) {
                    metaDataJson.remove("preview");
                    metaDataJson.put("preview", "<removed>");

                    json.remove("metaData");
                    json.put("metaData", metaDataJson);
                }
            } catch(JSONException ignore) {}
        }

        try {
            textView.setText(json.toString(4));
        }
        catch (Exception e) {
            textView.setText(json.toString());
        }
        textView.setTextIsSelectable(true);
        textView.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                ClipboardManager manager =
                        (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText(null, ((TextView) v).getText()));
                Toast.makeText(v.getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        AlertDialog.Builder alert = new AlertDialog.Builder(context);

        alert.setTitle(event.getTime() == 0
                ? "" : DEBUG_DATE_FORMAT.format(event.getTime()));

        alert.setView(textView);
        alert.show();
    }
}
