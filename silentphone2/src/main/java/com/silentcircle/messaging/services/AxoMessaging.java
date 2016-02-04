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

package com.silentcircle.messaging.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.messaging.activities.AxoRegisterActivity;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.DbRepository.DbConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.Manifest;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import axolotl.AxolotlNative;

/**
 * The main class that provides the Axolotl services.
 *
 * The service class sets up ans stores the repositories, databases, etc. The
 * AxoMessaging service is a singleton.
 *
 * The initialization of this class (the initialize function) must be called
 * only after the key manager is ready. Functions of this class require access
 * to the key manager to get the pass-phrases for the various repositories
 * and databases.
 *
 * It's also mandatory that the phone service is up and running before
 * initializing this class. The Axolotl service uses the phone infrastructure
 * to get the user name, user phone number, and uses it to send data.
 *
 * Created by werner on 26.05.15.
 */
public class AxoMessaging extends AxolotlNative {
    public static final String TAG = "AxoMessaging";

    public static final String AXOLOTL_STORE_KEY = "axolotl_store_key";
    public static final String APP_CONV_REPO_KEY = "app_conv_repo_key";
    public static final String AXOLOTL_REGISTERED = "axolotl_device_registered";
    public static final String AXOLOTL_ASK_REGISTER = "axolotl_ask_register";

    public static final int SQLITE_OK = 0;
    public static final int SQLITE_ROW = 100;

    public static boolean mSuppressErrorView;
    private static AxoMessaging instance;

    private boolean mIsReady;

    private String mNumber;
    private String mName;

    private byte[] mScDeviceId;
    private final Context mContext;

    private boolean mAlreadyAsked;

    private static DbConversationRepository mConversationRepository;
    private static boolean mSiblingsSynced;

    private final Object eventStateSync = new Object();
    private Map<Long, Message> eventState = new HashMap<>();

    private String mRegisteredKey;
    private String mAskRegisterKey;

    private final Collection<AxoMessagingStateCallback> msgStateListeners = new LinkedList<>();

    public interface AxoMessagingStateCallback {
        void axoRegistrationStateChange(boolean registered);
    }

    public static synchronized AxoMessaging getInstance(Context ctx) {
        if (instance == null)
            instance = new AxoMessaging(ctx);
        return instance;
    }

    // No public constructor
    private AxoMessaging(Context ctx) {
        mContext = ctx;
    }

    /**
     * Initialize Axolotl environment and open the application's repository database.
     *
     * Multiple accounts on one device are supported.
     */
    public synchronized void initialize() {
        if (mIsReady)
            return;

        if (!KeyStoreHelper.isReady())
            return;

        if (TiviPhoneService.phoneService == null || !TiviPhoneService.phoneService.isReady())
            return;

        setupNameNumber();
        if (TextUtils.isEmpty(mName))
            return;

        String computedDevId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(mContext, false));
        mScDeviceId = IOUtils.encode(computedDevId);
        byte[] apiKey = KeyManagerSupport.getSharedKeyData(mContext.getContentResolver(), ConfigurationUtilities.getShardAuthTag());


        // The application stores the conversations and their data already based on a user name.
        String dbName = ConfigurationUtilities.getRepoDbName();
        File dbFile = mContext.getDatabasePath(dbName);
        byte[] keyData = getRepositoryKey();
        int sqlCode = AxolotlNative.repoOpenDatabase(dbFile.getAbsolutePath(), keyData);
        Arrays.fill(keyData, (byte) 0);
        if (sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW) {
            throw new IllegalStateException("Cannot open repository database, error code: " + sqlCode);
        }

        // Different Axolotl stores, one for each user on this device.
        keyData = KeyManagerSupport.getPrivateKeyData(mContext.getContentResolver(), AXOLOTL_STORE_KEY);
        // No key yet - generate one and store it
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(mContext.getContentResolver(), AXOLOTL_STORE_KEY, 32);
        }
        dbName = mName + ConfigurationUtilities.getConversationDbName();
        dbFile = mContext.getDatabasePath(dbName);
        int initFlags = ConfigurationUtilities.mTrace ? 1 : 0;   // set debug level
        if (isRegistered())
            initFlags |= (1 << 4);
        // Initialize the Axolotl environment
        int initResult = doInit(initFlags, dbFile.getAbsolutePath(), keyData, IOUtils.encode(mName), apiKey, mScDeviceId);
        Arrays.fill(keyData, (byte) 0);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Axolotl initialization result: " + initResult);

        if (initResult <= 0)
            throw new IllegalStateException("Initialization of Axolotl library failed");

        mRegisteredKey = AXOLOTL_REGISTERED + "_" + mName;
        mAskRegisterKey = AXOLOTL_ASK_REGISTER + "_" + mName;

        mIsReady = true;
        // askAxolotlRegistration();
        registerDeviceMessaging(false);
        registerStateChanged(isRegistered());

        // TODO: Put this in a better place? I'm not sure where - Sam S.
        // Run handler for dealing with failed attachment operations
        Intent serviceIntent = Action.RUN_ATTACHMENT_HANDLER.intent(mContext, SCloudService.class);
        serviceIntent.putExtra("FROM_BOOT", true);
        mContext.startService(serviceIntent);
    }

    public boolean isReady() {
        return mIsReady;
    }

    public String getUserName() {
        return mName;
    }

    public boolean isRegistered() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getBoolean(mRegisteredKey, false);
    }

    @SuppressWarnings("unused")
    public void askAxolotlRegistration() {
        if (isRegistered() || mAlreadyAsked || !mIsReady)
            return;

        mAlreadyAsked = true;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        if (!prefs.getBoolean(mAskRegisterKey, true))
            return;
        final Intent intent = new Intent(mContext, AxoRegisterActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(AxoRegisterActivity.ACTION_REGISTER);
        mContext.startActivity(intent);
    }

    public void setAskToRegister(final boolean ask) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putBoolean(mAskRegisterKey, ask).apply();
    }

    public void registerDeviceMessaging(final boolean force) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        final boolean registered = prefs.getBoolean(mRegisteredKey, false);
        if (!registered || force) {
            RegisterInBackground registerBackground = new RegisterInBackground(mContext);
            registerBackground.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Return the conversation singleton of the current active user.
     * <p/>
     * Returns the ConversationRepository of the current active user.
     *
     * @return
     */
    public ConversationRepository getConversations() {
        if (mConversationRepository == null) {
            mConversationRepository =
                    new DbConversationRepository(mContext, mName);
        }
        return mConversationRepository;
    }

    /**
     * Return key data for the repository database.
     *
     * @return a 32byte (256 bit) key or {@code null} if none is available.
     */
    public byte[] getRepositoryKey() {
        byte[] keyData = KeyManagerSupport.getPrivateKeyData(mContext.getContentResolver(), APP_CONV_REPO_KEY);

        // No key yet - generate one and store it
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(mContext.getContentResolver(), APP_CONV_REPO_KEY, 32);
        }
        return keyData;
    }

    /**
     * Send message to the network.
     *
     * The function expects a fully composed message. Based on this information it creates a
     * message descriptor and sends the message.
     *
     * @param msg the composed message.
     */
    public boolean sendMessage(final Message msg) {
        if (!isReady()) {
            initialize();
            if (!isReady())
                return false;
        }
        final byte[] messageBytes = createMsgDescriptor(msg, msg.getText());

        createSendSyncOutgoing(messageBytes, msg);

        long[] netMsgIds = sendMessage(messageBytes, msg.getAttachmentAsByteArray(), msg.getAttributesAsByteArray());
        if (netMsgIds == null) {
            int errorCode = getErrorCode();
            if (errorCode == MessageErrorCodes.AXO_CONV_EXISTS) {
                Log.w(TAG, "Retry message to '" + msg.getConversationID() + "', got code: " + getErrorCode());
                netMsgIds = sendMessage(messageBytes, msg.getAttachmentAsByteArray(), msg.getAttributesAsByteArray());
            }
            if (netMsgIds == null) {
                Log.w(TAG, "Message to '" + msg.getConversationID() + "' could not be send, code: " + getErrorCode() + ", info: " + getErrorInfo());
                return false;
            }
        }
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Message sent, mark queued: " + Long.toHexString(netMsgId));
            markMessageQueued(netMsgId, msg);
        }
        notifyConversationUpdated(msg.getConversationID());
        return true;
    }

    /* *********************************************************
     * Callback functions from the Axolotl and network layer
     ********************************************************* */

    /*
     * After decoding the message descriptor the receive functions saves the message in the event repository of the
     * recipient, calls or queues processing.
     */
    @Override
    public int receiveMessage(final byte[] messageDescriptor, final byte[] attachmentDescriptor,
                              final byte[] messageAttributes) {
        final String msg = new String(messageDescriptor);
        final JSONObject obj;
        final String sender;
//        String scDevId;
        final String messageData;
        String msgId;
        try {
            // Parse and get information from message descriptor
            obj = new JSONObject(msg);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got a message: " + obj.toString());
            sender = obj.getString("sender");
//            scDevId = obj.getString("scClientDevId");
            msgId = obj.getString("msgId");
            messageData = obj.getString("message");
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }

        if (TextUtils.isEmpty(msgId)) {
            Log.w(TAG, "Received message with empty id");
            msgId = UUIDGen.makeType1UUID().toString();
        }

        if (sender.equalsIgnoreCase(getUserName())) {
            return processSyncMessage(messageData, msgId, attachmentDescriptor, messageAttributes);
        }

        final ConversationRepository conversations = getConversations();
        final Conversation conversation = getOrCreateConversation(sender);
        final EventRepository events = conversations.historyOf(conversation);

        String attributes = null;
        if (messageAttributes != null && messageAttributes.length > 0) {
            attributes = new String(messageAttributes);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got message attributes: " + attributes);
        }
        if (events != null) {
            // Check and process special attributes first, such a receipt acknowledgment, etc
            // Dismiss other message data if such special attributes are available.
            if (!TextUtils.isEmpty(attributes) && processSpecialAttributes(conversation, events, msgId, attributes)) {
                return 0;
            }
            if (!events.exists(msgId)) {
                final IncomingMessage message = new IncomingMessage(conversation.getPartner().getUsername(), msgId, messageData);
                message.setState(MessageStates.RECEIVED);
                message.setTime(System.currentTimeMillis());
                // Check for location, request receive receipt, burn, etc
                if (!TextUtils.isEmpty(attributes))
                    MessageUtils.parseAttributes(attributes, message);
                String attachment;
                if(attachmentDescriptor != null) {
                    attachment = new String(attachmentDescriptor);
                    message.setAttachment(attachment);
                    // Make sure the word "Attachment" is localized
                    message.setText(mContext.getString(R.string.attachment));
                }
                events.save(message);

                conversation.offsetUnreadMessageCount(1);
                conversation.setLastModified(System.currentTimeMillis());
                conversations.save(conversation);

                final Intent intent = Action.RECEIVE_MESSAGE.intent();
                Extra.PARTNER.to(intent, sender);
                if (message.isRequestReceipt())
                    intent.putExtra("FORCE_REFRESH", true);
                mContext.sendOrderedBroadcast(intent, Manifest.permission.READ);

                if(attachmentDescriptor != null) {
                    // Do an initial request to download the thumbnail
                    if(!TextUtils.isEmpty(message.getAttachment()) && !message.hasMetaData()) {
                        mContext.startService(SCloudService.getDownloadThumbnailIntent(message, conversation.getPartner().getUsername(), mContext));
                    }
                }
            }
        }
        return 0;
    }

    public Conversation getOrCreateConversation(final String sender) {
        if (sender == null) {
            return null;
        }
        Conversation conversation = getConversations().findById(sender);

        if (conversation == null) {
            conversation = new Conversation();
            conversation.setPartner(new Contact(sender));
//            conversation.getPartner().setAlias( getDisplayName( user ) );
            /* by default enable burn notice for conversation and set burn delay to 3 days */
            conversation.setBurnNotice(true);
            conversation.setBurnDelay(TimeUnit.DAYS.toSeconds(3));

            if (isSelf(sender)) {
                conversation.getPartner().setDevice(mScDeviceId);
                getConversations().save(conversation);
            }
            else {
                getConversations().save(conversation);
            }
        }
        return conversation;
    }

    public boolean isSelf(String username) {
        String self = mName;
        if (username == null) {
            return self == null;
        }
        return self != null && self.equalsIgnoreCase(username);
    }

    @Override
    public void messageStateReport(final long networkMessageId, final int statusCode, final byte[] stateInformation) {
        if (ConfigurationUtilities.mTrace)
            Log.d(TAG, "Message state report, msgId: " + Long.toHexString(networkMessageId) + ", code: " + statusCode);
        if (networkMessageId != 0) {
            switch (statusCode) {
                case 200:
                    markMessageSent(networkMessageId, MessageStates.DELIVERED);
                    return;
                case 202:
                    markMessageSent(networkMessageId, MessageStates.SENT_TO_SERVER);
                    return;
                default:
                    Log.e(TAG, "Message state report, unknown status code: " + statusCode);
                    return;
            }
        }
        // A messageIdentifier of 0 means that the Axolotl lib received data that could
        // not be processed. The status code contains the Axolotl lib error code.
        final String info = (stateInformation != null) ? new String(stateInformation) : "{}";

        if (!AxoMessaging.mSuppressErrorView) {
            createErrorEvent(statusCode, info);
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Receive error: " + statusCode + ", info: " + info);
    }

    @Override
    public byte[] httpHelper(final byte[] requestUri, final String method, final byte[] requestData, final int[] code) {
        final StringBuilder content = new StringBuilder();

        String uri = new String(requestUri);
//        String data = null;
//        if (requestData != null)
//            data = new String(requestData);
//        Log.d(TAG, "++++ method: " + method);
//        Log.d(TAG, "++++ data: " + data);

        URL requestUrl;
        final String resourceUrl = ConfigurationUtilities.getProvisioningBaseUrl(mContext) + uri.substring(1);
        try {
            requestUrl = new URL(resourceUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            code[0] = 400;
            return null;
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "request url: " + requestUrl);
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) requestUrl.openConnection();
            final SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
            if (context != null) {
                urlConnection.setSSLSocketFactory(context.getSocketFactory());
            }
            else
                Log.e(TAG, "Cannot get a trusted/pinned SSL context, use normal SSL socket factory");

            urlConnection.setRequestMethod(method);
            urlConnection.setDoInput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
            urlConnection.setConnectTimeout(2000);

            if (requestData != null && requestData.length > 0) {
                urlConnection.setDoOutput(true);
                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(requestData);
                out.flush();
            }

            final int ret = urlConnection.getResponseCode();
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code httpHelper: " + ret);
            if (ret != HttpsURLConnection.HTTP_OK) {
                IOUtils.readStream(new BufferedInputStream(urlConnection.getErrorStream()), content);
            }
            else {
                IOUtils.readStream(new BufferedInputStream(urlConnection.getInputStream()), content);
            }
            code[0] = ret;
            return IOUtils.encode(content.toString());

        } catch (IOException e) {
            code[0] = 418;
            if (!Utilities.isNetworkConnected(mContext))
                code[0] = 444;
            Log.e(TAG, "Network not available: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Network connection problem: " + e);
            code[0] = 418;
            return null;
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }
    }

    public void notifyCallback(final int notifyActionCode, final byte[] actionInformation, final byte[] deviceId) {
        String actionInfo = null;
        if (actionInformation != null)
            actionInfo = new String(actionInformation);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Notify action: " + notifyActionCode + ", info: " + actionInfo);
        AxoCommandInBackground aib = new AxoCommandInBackground();
        aib.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "rescanUserDevices", actionInfo);
    }


    /**
     * Send a delivery notification for a message.
     *
     * The message in this case is an INCOMING message, thus use the "sender" to
     * create the message descriptor.
     *
     * Don't call from UI thread.
     *
     * @param msg Send delivery notification for this message
     */
    public void sendDeliveryNotification(final Message msg) {
        // Create a message descriptor, use the message id of the received message
        final JSONObject obj =  createMsgDescriptorJson(msg, msg.getSender());
        final JSONObject attributeJson = new JSONObject();

        // an empty message string
        try {
            obj.put("message", "");

            // inform about the time the client got the message
            attributeJson.put("cmd", "rr");
            attributeJson.put("rr_time", ConversationActivity.ISO8601.format(new Date()));
        } catch (JSONException ignore) {}

        final byte[] msgBytes = IOUtils.encode(obj.toString());
        final byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        final long[] netMsgIds = sendMessage(msgBytes, null, attributeBytes);
        if (netMsgIds == null) {
            Log.w(TAG, "DeliverNotification to '" + msg.getConversationID() + "' could not be send, code: " + getErrorCode() + ", info: " + getErrorInfo());
            return;
        }
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "DeliverNotification sent, mark queued: " + Long.toHexString(netMsgId));
            markMessageQueued(netMsgId, null);
        }
    }

    /**
     * Send a burn notice request to the other party to force removal of a message.
     *
     * The message in this case is an OUTGOING message, thus use normal setup to
     * create the message descriptor.
     *
     * Don't call from UI thread.
     *
     * @param msg Send burn notice request for this message.
     * @param recipient Recipient for burn notice request. If null, message's conversation
     *                  id will be used.
     */
    public void sendBurnNoticeRequest(final Message msg, final String recipient) {
        // Create a message descriptor, use the message id of the message to burn
        final JSONObject obj =  createMsgDescriptorJson(msg, recipient);
        final JSONObject attributeJson = new JSONObject();

        // an empty message string and the request burn notice request command
        try {
            obj.put("message", "");
            attributeJson.put("cmd", "bn");
        } catch (JSONException ignore) {}
        final byte[] msgBytes = IOUtils.encode(obj.toString());
        final byte[] attributeBytes = IOUtils.encode(attributeJson.toString());

        createSendSyncBurnNotice(attributeJson, msgBytes, msg);
        final long[] netMsgIds = sendMessage(msgBytes, null, attributeBytes);
        if (netMsgIds == null) {
            Log.w(TAG, "BurnNotice request to '" + msg.getConversationID() + "' could not be send, code: " + getErrorCode() + ", info: " + getErrorInfo());
            return;
        }
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "BurnNotice request sent, mark queued: " + Long.toHexString(netMsgId));
            markMessageQueued(netMsgId, null);
        }
    }

    /**
     * Adds the given <tt>AxeMessagingStateCallback</tt> to the list of call state change listeners.
     *
     * @param l
     *            the <tt>AxeMessagingStateCallback</tt> to add
     */
    public void addStateChangeListener(final AxoMessagingStateCallback l) {
        synchronized (msgStateListeners) {
            if (msgStateListeners.contains(l))     // don't add twice
                return;
            msgStateListeners.add(l);
        }
    }

    /**
     * Removes the given <tt>AxeMessagingStateCallback</tt> from the list of call state change listeners.
     *
     * @param l
     *            the <tt>AxeMessagingStateCallback</tt> to remove
     */
    public void removeStateChangeListener(final AxoMessagingStateCallback l) {
        synchronized (msgStateListeners) {
            msgStateListeners.remove(l);
        }
    }

    public void sendEmptySyncToSiblings() {
        if (!isReady()) {
            initialize();
            if (!isReady())
                return;
        }

        if (mSiblingsSynced)
            return;
        mSiblingsSynced = true;

        // Construct a dummy outgoing message
        String msgId = UUIDGen.makeType1UUID().toString();
        OutgoingMessage message = new OutgoingMessage(IOUtils.encode(getUserName()), IOUtils.encode(""));
        message.setId(IOUtils.encode(msgId));
        message.setConversationID(getUserName());

        JSONObject obj =  createMsgDescriptorJson(message, null);
        JSONObject attributeJson = new JSONObject();

        // an empty message string and the request burn notice request command
        try {
            obj.put("message", "");
            attributeJson.put("cmd", "sye");
        } catch (JSONException ignore) {}

        byte[] msgBytes = IOUtils.encode(obj.toString());
        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        SendCommandInBackground sndBackground = new SendCommandInBackground(msgBytes, null, attributeBytes, true);
        sndBackground.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /* **********************************************************
     * Private functions
     ********************************************************** */

    private void setupNameNumber() {
        if (TextUtils.isEmpty(mNumber))
            mNumber = TiviPhoneService.getInfo(0, -1, "cfg.nr");
        if (!TextUtils.isEmpty(mNumber)) {
            String number = PhoneNumberHelper.formatNumber(mNumber, Locale.getDefault().getCountry());
            mNumber = TextUtils.isEmpty(number) ? mNumber : number;  // if format number eats all data show raw number
        }
        if (TextUtils.isEmpty(mName))
            mName = TiviPhoneService.getInfo(0, -1, "cfg.un");
    }

    /*
     * The message descriptor JSON data
     {
        "version":    <int32_t>,            # Version of JSON send message descriptor, 1 for the first implementation
        "recipient":  <string>,             # for SC this is either the user's name of the user's DID
        "scClientDevId" : <string>,         # the sender's device id, same as used to register the device (v1/me/device/{device_id}/)
        "msgId" :     <string>,             # the global (UUID) message id
        "message":    <string>              # the actual plain text message, UTF-8 encoded (Java programmers beware!)
     }
     */
    @NonNull
    private byte[] createMsgDescriptor(final Message msg, final String text) {
        final JSONObject obj =  createMsgDescriptorJson(msg, null);
        try {
            obj.put("message", text);
        } catch (JSONException ignore) {
            // In practice, this superfluous exception can never actually happen.
        }
        return IOUtils.encode(obj.toString());
    }

    @NonNull
    private JSONObject createMsgDescriptorJson(final Message msg, String recipient) {
        if (recipient == null)
            recipient = Utilities.getUsernameFromUriNumber(msg.getConversationID());
        final JSONObject obj = new JSONObject();
        try {
            obj.put("version", 1);
            obj.put("recipient", recipient);
            obj.put("scClientDevId", new String(mScDeviceId));
            obj.put("msgId", msg.getId());
        } catch (JSONException ignore) {
            // In practice, this superfluous exception can never actually happen.
        }
        return obj;
    }

    // Function to check receipt ack, burn, etc - see consumePacket(...) in DefaultPacketOutput class in SilentText
    private boolean processSpecialAttributes(final Conversation conv, final EventRepository events, final String msgId,
                                             final String attributes) {
        try {
            // Check for a command - if yes, process it, return true, message done.
            final JSONObject attributeJson = new JSONObject(attributes);
            final String command = attributeJson.optString("cmd", null);
            if (TextUtils.isEmpty(command))
                return false;
            switch (command) {
                case "rr":
                    final String dateTime = attributeJson.getString("rr_time");
                    final long dt = parseDate(dateTime);
                    markPacketAsRead(events, msgId, dt);
                    return true;

                case "bn":
                    burnPacket(conv, events, msgId, true);
                    return true;

                case "bnc":
                    // TODO - do the "burn confirmation handling" if necessary
                    return true;

                case "ping":
                    return true;

                case "pong":
                    return true;

                case "sye":
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got empty sync");
                    return true;

                // Check for others, ignore unknown commands, however don't process message any more
                default:
                    return true;
            }
        } catch (JSONException ignore) {
            // In practice, this superfluous exception should never actually happen.
        }
        return false;
    }

    // stateReport calls this to mark the message as sent. If stateReport happens before
    // sendMessage returned then just inset the netMsgId with a null
    private void markMessageSent(final long netMsgId, @MessageStates.MessageState final int messageState) {
        synchronized (eventStateSync) {
            if (eventState.containsKey(netMsgId)) {
                final Message msg = eventState.get(netMsgId);
                // client may send one message to multiple devices
                // - handle real state changes only
                // - if the client recorded at least one DELIVERED state then don't overwrite
                //   it with a weaker state (DELIVERED (200) is stronger than SENT_TO_SERVER (202))
                if (msg != null && msg.getState() != messageState && msg.getState() != MessageStates.DELIVERED) {
                    msg.setState(messageState);
                    final String recipient = Utilities.getUsernameFromUriNumber(msg.getConversationID());
                    final Conversation conversation = getConversations().findById(recipient);
                    final EventRepository events = getConversations().historyOf(conversation);
                    msg.addNetMessageId(netMsgId);
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Message sent (mms): " + Long.toHexString(netMsgId));
                    events.save(msg);

                    notifyConversationUpdated(msg.getConversationID());
                }
                eventState.remove(netMsgId);
            }
            else {
                eventState.put(netMsgId, null);     // just to mark this netMsg id as seen and sent
            }
        }
    }

    // SendMessage calls this to mark the message as queued for sending.
    // if stateReport was earlier then the eventsState list contains the netMsgId
    // and thus the message was sent.
    // If message is 'null' then just handle the eventState list entries.
    private void markMessageQueued(final long netMsgId, final Message message) {
        synchronized (eventStateSync) {
            if (eventState.containsKey(netMsgId)) { // Message state report already available for this msg
                if (message != null) {
                    message.setState(MessageStates.SENT);
                    final String recipient = Utilities.getUsernameFromUriNumber(message.getConversationID());
                    final Conversation conversation = getConversations().findById(recipient);
                    final EventRepository events = getConversations().historyOf(conversation);
                    message.addNetMessageId(netMsgId);
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Message sent (mmq): " + Long.toHexString(netMsgId));
                    events.save(message);

                    notifyConversationUpdated(message.getConversationID());
                }
                eventState.remove(netMsgId);
            }
            else {
                eventState.put(netMsgId, message);  // mark it as queued to send
            }
        }
    }

    private void markPacketAsRead(final EventRepository events, final String deliveredPacketID, final long deliveryTime) {
        final Event event = events.findById(deliveredPacketID);
        if( event instanceof OutgoingMessage) {
            final OutgoingMessage message = (OutgoingMessage)event;
            message.setState(MessageStates.READ);
            if (message.expires())
                message.setExpirationTime(System.currentTimeMillis()
                        + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));

            message.setDeliveryTime(deliveryTime);
            events.save(message);

            final Intent intent = Action.UPDATE_CONVERSATION.intent();
            Extra.PARTNER.to(intent, message.getConversationID());
            intent.putExtra("FORCE_REFRESH", true);
            mContext.sendOrderedBroadcast(intent, Manifest.permission.READ);
        }
    }

    private void createErrorEvent(final int errorCode, final String info) {
        final ErrorEvent event = MessageUtils.parseErrorMessage(new ErrorEvent(errorCode), info);
        final String sender = Utilities.removeSipParts(event.getSender());

        if (TextUtils.isEmpty(sender)) {
            Log.e(TAG, "Failed to determine sender for error [" + info + "]");
            return;
        }

        final Conversation conversation = getOrCreateConversation(sender);
        final EventRepository events = getConversations().historyOf(conversation);

        // Check if the message with event.getMessageId() already exists. if yes and if error code is
        // -13 or -23 the this a error because of "duplicate" message decryption. This is not possible
        // in Axolotl. It also indicates that the server sent a message twice.
        String messageId = event.getMessageId();        // Id of erroneous message as set by the sender
        if (!TextUtils.isEmpty(messageId) && (errorCode == -13 || errorCode == -23) && events.exists(messageId)) {
            event.setDuplicate(true);
        }
        event.setId(UUIDGen.makeType1UUID().toString());
        event.setTime(System.currentTimeMillis());
        events.save(event);

        notifyConversationUpdated(sender);
    }

    private void burnPacket(final Conversation conv, final EventRepository events, final String burnedPacketId,
                            final boolean confirm) {
        final Event event = events.findById(burnedPacketId);

        if (event == null) {
            Log.w(TAG, "Burn notice requested for unknown event");
            return;
        }

        if (!(event instanceof IncomingMessage) && !(event instanceof  OutgoingMessage)) {
            Log.w(TAG, "Burn notice requested for wrong event type");
            return;
        }

        /*
         * Determine whether correct event is being removed, whether conversation partner
         * for message does not match the conversation itself.
         *
         * For incoming message conversation partner is sender, for outgoing message it is
         * in conversation id.
         */
        String conversationPartner;
        if (event instanceof  IncomingMessage) {
            IncomingMessage message = (IncomingMessage) event;
            conversationPartner = message.getSender();
        }
        else {
            OutgoingMessage message = (OutgoingMessage) event;
            conversationPartner = message.getConversationID();
        }

        if (!conv.getPartner().getUsername().equals(conversationPartner)) {
            Log.w(TAG, "Burn notice request - wrong caller");
            return;
        }

        events.remove(event);
        if (confirm)
            sendBurnNoticeConfirmation((Message)event, conversationPartner);

        notifyConversationUpdated(conversationPartner);

        if(((Message) event).hasAttachment()) {
            /** Remove a possible temporary attachment file (rare)
             *  At this point, all encrypted chunks have already been removed by {@link com.silentcircle.messaging.repository.DbRepository.DbEventRepository#remove(Event)}
            */
            Intent cleanupIntent = Action.PURGE_ATTACHMENTS.intent(mContext, SCloudCleanupService.class);
            cleanupIntent.putExtra("KEEP_STATUS", false);
            Extra.PARTNER.to(cleanupIntent, conversationPartner);
            Extra.ID.to(cleanupIntent, event.getId());
            mContext.startService(cleanupIntent);
        }

//        if( event.getId().equals(conversation.getPreviewEventID())) {
//
//            if (MessageState.RECEIVED.equals(((IncomingMessage)event).getState())) {
//                conversation.offsetUnreadMessageCount( -1 );
//            }
//
//            List<Event> history = events.list();
//            int count = history.size();
//
//            if( count > 0 ) {
//                conversation.setPreviewEventID( history.get( count - 1 ).getId() );
//            } else {
//                conversation.setPreviewEventID( (byte []) null );
//            }
//            conversations.save( conversation );
//        }
    }

    /**
     * Send a burn notice confirmation to the request sender.
     **
     * @param msg Send burn notice confirmation for this message.
     * @param recipient Recipient for burn notice confirmation.
     */
    private void sendBurnNoticeConfirmation(final Message msg, final String recipient) {
        // Create a message descriptor, use the message id of the message to burn
        JSONObject obj =  createMsgDescriptorJson(msg, recipient);
        JSONObject attributeJson = new JSONObject();

        // an empty message string and the request burn notice request command
        try {
            obj.put("message", "");
            attributeJson.put("cmd", "bnc");
        } catch (JSONException ignore) {}

        byte[] msgBytes = IOUtils.encode(obj.toString());
        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        SendCommandInBackground sndBackground = new SendCommandInBackground(msgBytes, null, attributeBytes);
        sndBackground.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Notifies all registered {@link AxoMessagingStateCallback} that registration state changed.
     *
     * @param call  the  CallState
     * @param msg the new ZRTP call state
     */
    private void registerStateChanged(final boolean registered) {
        synchronized (msgStateListeners) {
            for (AxoMessagingStateCallback l : msgStateListeners)
                l.axoRegistrationStateChange(registered);
        }
    }

    public static long parseDate(final String dateTime) {
        try {
            return ConversationActivity.ISO8601.parse(dateTime).getTime();
        } catch( Throwable exception ) {
            return 0;
        }
    }

    /**
     * Send a burn request for outgoing message to user's other devices.
     *
     * @param attributeJson The JSON object that already contains the normal burn command
     * @param msgBytes The message to attach to the attribute command data
     */
    private void createSendSyncBurnNotice(final JSONObject attributeJson, final byte[] msgBytes, final Message msg) {
        try {
            attributeJson.put("syc", "bn");
            attributeJson.put("or", MessageUtils.getConversationId(msg));
        } catch (JSONException ignore) {}

        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        long[] netMsgIds = sendMessageToSiblings(msgBytes, null, attributeBytes);
        if (netMsgIds == null) {
            Log.w(TAG, "BurnNotice sync request could not be sent, code: " + getErrorCode() + ", info: " + getErrorInfo());
            return;
        }
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "BurnNotice sync request sent, mark queued: " + Long.toHexString(netMsgId));
            markMessageQueued(netMsgId, null);
        }
    }

    /**
     * Send an outgoing message to user's other devices.
     *
     * @param message The message descriptor data
     * @param msg  The outgoing message data structure
     * @return {@code true} if sync was successful
     */
    private boolean createSendSyncOutgoing(final byte[] message, final Message msg) {
        String attributes = msg.getAttributes();
        JSONObject attributeJson;
        try {
            // Add special sync command
            attributeJson =  new JSONObject(TextUtils.isEmpty(attributes) ? "{}" : attributes);
            attributeJson.put("syc", "om");
            attributeJson.put("or", msg.getConversationID());
        } catch (JSONException ignore) {
            return false;
        }
        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        long[] netMsgIds = sendMessageToSiblings(message, msg.getAttachmentAsByteArray(), attributeBytes);
        int errorCode = getErrorCode();
        if (netMsgIds == null) {
            if (errorCode != MessageErrorCodes.NO_DEVS_FOUND) {
                Log.w(TAG, "Sync message for '" + msg.getConversationID() + "' could not be sent, code: " + errorCode + ", info: " + getErrorInfo());
            }
            return false;
        }
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Sync message sent, mark queued: " + Long.toHexString(netMsgId));
            markMessageQueued(netMsgId, null);
        }
        return true;
    }

    private int processSyncMessage(final String messageData, final String msgId, final byte[] attachmentDescriptor,
                                   final byte[] messageAttributes) {

        if (messageAttributes == null || messageAttributes.length == 0) {
            return -2;
        }
        String attributes = new String(messageAttributes);

        String attachment = null;
        if(attachmentDescriptor != null)
            attachment = new String(attachmentDescriptor);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got message attributes (sync): " + attributes);
        try {
            // Check for a command - if yes, process it, return true, message done.
            JSONObject attributeJson = new JSONObject(attributes);
            String command = attributeJson.optString("syc", null);
            if (TextUtils.isEmpty(command))
                return -2;
            final String originalReceiver = attributeJson.getString("or");
            switch (command) {
                case "om":
                    return processSyncOutgoingMessage(originalReceiver, messageData, msgId, attachment, attributes);

                case "bn":
                    return processSyncOutgoingBurn(originalReceiver, msgId);

                // Check for others, ignore unknown commands, however don't process message any more
                default:
                    return 0;
            }
        } catch (JSONException ignore) {
            return -3;
        }
    }

    private int processSyncOutgoingMessage(final String receiver, final String messageData, final String msgId,
                                           final String attachment,
                                           final String attributes) {

        ConversationRepository conversations = getConversations();
        Conversation conversation = getOrCreateConversation(receiver);
        EventRepository events = conversations.historyOf(conversation);

        Message message = new OutgoingMessage(getUserName(), messageData);
        MessageUtils.parseAttributes(attributes, message);
        if(attachment != null) {
            message.setAttachment(attachment);
        }
        message.setState(MessageStates.SYNC);
        message.setConversationID(conversation.getPartner().getUsername());
        message.setId(msgId);

        events.save(message);
        conversation.setLastModified(System.currentTimeMillis());
        conversations.save(conversation);

        notifyConversationUpdated(message.getConversationID());

        // Do an initial request to download the thumbnail
        if(attachment != null && !TextUtils.isEmpty(message.getAttachment()) && !message.hasMetaData()) {
            mContext.startService(SCloudService.getDownloadThumbnailIntent(message, conversation.getPartner().getUsername(), mContext));
        }

        return 0;
    }

    private int processSyncOutgoingBurn(final String receiver, final String msgId) {

        ConversationRepository conversations = getConversations();
        Conversation conversation = getOrCreateConversation(receiver);
        EventRepository events = conversations.historyOf(conversation);

        burnPacket(conversation, events, msgId, false);

        return 0;
    }

    private void notifyConversationUpdated(final String conversationId) {
        final Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, conversationId);
        mContext.sendOrderedBroadcast(intent, Manifest.permission.READ);
    }

    private class RegisterInBackground extends AsyncTask<Void, Void, Integer> {
        final int[] code = new int[1];
        byte[] errorMsg;
        final Context mCtx;

        RegisterInBackground(Context ctx) {
            mCtx = ctx;
        }

        @Override
        protected Integer doInBackground(Void... commands) {
            long startTime = System.currentTimeMillis();
            errorMsg = registerAxolotlDevice(code);
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for AxoRegistration: " + time);

            if (code[0] < 0 || code[0] >= HttpURLConnection.HTTP_NOT_FOUND) {
                Log.e(TAG, "Axolotl registration failure: " + (errorMsg != null ? new String(errorMsg) : "null") + ", code: " + code[0]);
                return;
            }
            mSiblingsSynced = false;
            sendEmptySyncToSiblings();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
            prefs.edit().putBoolean(mRegisteredKey, true).apply();
            registerStateChanged(true);
        }
    }

    private class SendCommandInBackground extends AsyncTask<Void, Void, Integer> {
        final byte[] mMsgDescriptor;
        final byte []mAttachmentDescriptor;
        final byte[] mAttributeDescriptor;
        long[] mNetMsgIds;
        final boolean mToSiblings;

        SendCommandInBackground(byte[] msgDescriptor, byte[] attachmentDescriptor, byte[] attributeDescriptor) {
            this(msgDescriptor, attachmentDescriptor, attributeDescriptor, false);
        }

        SendCommandInBackground(byte[] msgDescriptor, byte[] attachmentDescriptor, byte[] attributeDescriptor, boolean toSiblings) {
            mMsgDescriptor = msgDescriptor;
            mAttachmentDescriptor = attachmentDescriptor;
            mAttributeDescriptor = attributeDescriptor;
            mToSiblings = toSiblings;
        }

        @Override
        protected Integer doInBackground(Void... commands) {
            long startTime = System.currentTimeMillis();
            if (!mToSiblings)
                mNetMsgIds = sendMessage(mMsgDescriptor, mAttachmentDescriptor, mAttributeDescriptor);
            else
                mNetMsgIds = sendMessageToSiblings(mMsgDescriptor, mAttachmentDescriptor, mAttributeDescriptor);

            if (mNetMsgIds == null) {
                if (getErrorCode() == MessageErrorCodes.NO_PRE_KEY_FOUND && mToSiblings) {
                    Log.w(TAG, "No sibling devices");
                }
                else {
                    Log.w(TAG, "Async command, no network ids '" + new String(mAttributeDescriptor) +
                            "', code: " + getErrorCode() + ", info: " + getErrorInfo());
                }
            }
            else {
                for (long netMsgId : mNetMsgIds) {
                    if (ConfigurationUtilities.mTrace)
                        Log.d(TAG, "Async command sent, mark queued: " + Long.toHexString(netMsgId));
                    markMessageQueued(netMsgId, null);
                }
            }
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command send: " + time);
        }
    }

    private class AxoCommandInBackground extends AsyncTask<String, Void, Integer> {

        private String mCommand;

        @Override
        protected Integer doInBackground(String... commands) {
            long startTime = System.currentTimeMillis();
            byte[] data = null;
            if (commands.length >= 1)
                data = IOUtils.encode(commands[1]);
            mCommand = commands[0];
            axoCommand(mCommand, data);
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command '" + mCommand + "': " + time);
        }

    }
}
