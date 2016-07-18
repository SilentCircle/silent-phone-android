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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.messaging.activities.AxoRegisterActivity;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.DbRepository.DbConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
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
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
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
@SuppressLint("SimpleDateFormat")
public class AxoMessaging extends AxolotlNative {
    public static final String TAG = "AxoMessaging";

    public static final String AXOLOTL_STORE_KEY = "axolotl_store_key";
    public static final String APP_CONV_REPO_KEY = "app_conv_repo_key";
    public static final String AXOLOTL_REGISTERED = "axolotl_device_registered";
    public static final String AXOLOTL_ASK_REGISTER = "axolotl_ask_register";

    public static final int SQLITE_OK = 0;
    public static final int SQLITE_ROW = 100;

    public static final int UPDATE_ACTION_MESSAGE_STATE_CHANGE = 1;
    public static final int UPDATE_ACTION_MESSAGE_BURNED = 2;
    public static final int UPDATE_ACTION_MESSAGE_SEND = 3;

    public static final int MIN_NUM_PRE_KEYS = 30;
    public static final int CREATE_NEW_PRE_KEYS = 100;

    /** Several modules use this format to define a common UTC date */
    public static final SimpleDateFormat ISO8601;
    static {
        ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static boolean mSuppressErrorView;
    private static AxoMessaging instance;

    private boolean mIsReady;

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

    private final Object syncObj = new Object();
    private final Collection<AxoMessagingStateCallback> msgStateListeners = new LinkedList<>();

    public interface AxoMessagingStateCallback {
        void axoRegistrationStateChange(boolean registered);
    }

    @NonNull
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

        if (!KeyStoreHelper.isReady()) {
            return;
        }

        if (TiviPhoneService.phoneService == null)
            return;

        if (TextUtils.isEmpty(mName))
            LoadUserInfo.loadPreferences(mContext);
            mName = LoadUserInfo.getUuid();

        if (TextUtils.isEmpty(mName)) {
            return;
        }

        String computedDevId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(mContext, false));
        mScDeviceId = IOUtils.encode(computedDevId);
        byte[] apiKey = KeyManagerSupport.getSharedKeyData(mContext.getContentResolver(), ConfigurationUtilities.getShardAuthTag());


        // The application stores the conversations and their data already based on a user name.
        String dbName = ConfigurationUtilities.getRepoDbName();
        File dbFile = mContext.getDatabasePath(dbName);
        byte[] keyData = getRepositoryKey();
        if (keyData == null) {
            throw new IllegalStateException("No key data for repository database.");
        }

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
        if (keyData == null) {
            throw new IllegalStateException("No key data for Axolotl database.");
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

    public void setReady(boolean isReady) {
        mIsReady = isReady;
    }

    public String getUserName() {
        return mName;
    }

    public boolean isRegistered() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getBoolean(mRegisteredKey, false);
    }

    public void setRegistered(boolean isRegistered) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putBoolean(mRegisteredKey, isRegistered).apply();
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
            AsyncUtils.execute(registerBackground);
        }
    }

    /**
     * Return the conversation singleton of the current active user.
     * <p/>
     * Returns the ConversationRepository of the current active user.
     *
     * @return the ConversationRepository of the current active user.
     */
    @NonNull
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
     * @return a 32byte (256 bit) key or {@code null} if none is available
     */
    @Nullable
    public byte[] getRepositoryKey() {
        byte[] keyData = KeyManagerSupport.getPrivateKeyData(mContext.getContentResolver(), APP_CONV_REPO_KEY);

        // No key yet - generate one and store it
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(mContext.getContentResolver(), APP_CONV_REPO_KEY, 32);
        }
        return keyData;
    }

    @WorkerThread
    public boolean sendMessage(final Message msg) {
        return sendMessage(msg, false);
    }

    /**
     * Send message to the network.
     *
     * The function expects a fully composed message. Based on this information it creates a
     * message descriptor and sends the message.
     *
     * @param msg the composed message.
     */
    @WorkerThread
    public boolean sendMessage(final Message msg, final boolean siblingsOnly) {
        if (!isReady()) {
            initialize();
            if (!isReady())
                return false;
        }
        final byte[] messageBytes = createMsgDescriptor(msg, msg.getText());

        boolean syncResult = createSendSyncOutgoing(messageBytes, msg);

        if (siblingsOnly) {
            return syncResult;
        }

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

        MessageUtils.notifyConversationUpdated(mContext, msg.getConversationID(), false,
                UPDATE_ACTION_MESSAGE_STATE_CHANGE, msg.getId());
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
        String aliasName = "";         // Initialize to Empty, used to check in async task
        String displayName;
        String msgId;
        try {
            // Parse and get information from message descriptor
            obj = new JSONObject(msg);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got a message: " + obj.toString());
            sender = obj.getString("sender");               // Sender is the UUID
            displayName = obj.getString("display_name");    // Got this name from SIP PAI or From header
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
            processSyncMessage(messageData, msgId, attachmentDescriptor, messageAttributes);
            return 0;
        }

        final ConversationRepository conversations = getConversations();
        final Conversation conversation = getOrCreateConversation(sender);

        final EventRepository events = conversations.historyOf(conversation);

        // Try to get one of the sender's alias name, try cached data first.
        byte[][] senderAliases = AxoMessaging.getAliases(sender);
        if (senderAliases == null || senderAliases.length == 0) {   // None available, ask the server to get an alias
            AsyncTasks.UserDataBackgroundTaskNotMain getAliasTask = new AsyncTasks.UserDataBackgroundTaskNotMain(sender) {
                @Override
                @WorkerThread
                public void onPostRun() {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "onPostRun from receive");
                    if (events != null && TextUtils.isEmpty(conversation.getPartner().getAlias())) {
                        synchronized (syncObj) {
                            conversation.getPartner().setAlias(mUserInfo.mAlias);
                            conversations.save(conversation);
                            final Intent intent = Action.RECEIVE_MESSAGE.intent();
                            Extra.PARTNER.to(intent, sender);
                        }
                    }
                }
            };
            AsyncUtils.execute(getAliasTask);
        }
        else {
            aliasName = new String(senderAliases[0]);
        }

        String attributes = null;
        if (messageAttributes != null && messageAttributes.length > 0) {
            attributes = new String(messageAttributes);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got message attributes: " + attributes);
        }
        if (events != null) {

            synchronized (syncObj) {
                // We need to do this here to be backward compatible with existing conversation
                // and this also works for new conversations.
                if (!TextUtils.isEmpty(aliasName))
                    conversation.getPartner().setAlias(aliasName);
                conversation.getPartner().setDisplayName(displayName);

                // Check and process special attributes first, such a receipt acknowledgment, etc
                // Dismiss other message data if such special attributes are available.
                if (!TextUtils.isEmpty(attributes) && processSpecialAttributes(conversation, events, msgId, attributes)) {
                    return 0;
                }
                if (!events.exists(msgId)) {
                    final IncomingMessage message = new IncomingMessage(conversation.getPartner().getUserId(), msgId, messageData);
                    message.setState(MessageStates.RECEIVED);
                    message.setTime(System.currentTimeMillis());
                    // Check for location, request receive receipt, burn, etc
                    if (!TextUtils.isEmpty(attributes))
                        MessageUtils.parseAttributes(attributes, message);
                    String attachment;
                    if (attachmentDescriptor != null) {
                        attachment = new String(attachmentDescriptor);
                        message.setAttachment(attachment);
                        // Make sure the word "Attachment" is localized
                        message.setText(mContext.getString(R.string.attachment));
                    }
                    events.save(message);

                    conversation.offsetUnreadMessageCount(1);
                    conversation.setLastModified(System.currentTimeMillis());
                    conversations.save(conversation);

                    // Run the sendDeliveryNotification not in the same thread as SIP receive
                    // processing. sendDeliveryNotification may take a long time and we should
                    // not block the SIP receive thread.
                    Runnable sendDeliveryAsync = new Runnable() {
                        @Override
                        @WorkerThread
                        public void run () {
                            long startTime = System.currentTimeMillis();
                            sendDeliveryNotification(message);
                            if (ConfigurationUtilities.mTrace)
                                Log.d(TAG, "Processing time for sendDeliveryNotification: " + (System.currentTimeMillis() - startTime));
                        }
                    };
                    AsyncUtils.execute(sendDeliveryAsync);

                    final Intent intent = Action.RECEIVE_MESSAGE.intent();
                    Extra.PARTNER.to(intent, sender);
                    Extra.ALIAS.to(intent, aliasName);
                    if (message.isRequestReceipt()) {
                        Extra.FORCE.to(intent, true);
                    }
                    Extra.ID.to(intent, message.getId());
                    mContext.sendOrderedBroadcast(intent, Manifest.permission.READ);

                    if (attachmentDescriptor != null) {
                        // Do an initial request to download the thumbnail
                        if (!TextUtils.isEmpty(message.getAttachment()) && !message.hasMetaData()) {
                            mContext.startService(SCloudService.getDownloadThumbnailIntent(message, conversation.getPartner().getUserId(), mContext));
                        }
                    }
                }
            }
        }
        return 0;
    }

    @NonNull
    public Conversation getOrCreateConversation(@NonNull final String senderUUID) {
        Conversation conversation = getConversations().findById(senderUUID);

        if (conversation == null) {
            conversation = new Conversation(senderUUID);
            /* by default enable burn notice for conversation and set burn delay to 3 days */
            if (Utilities.canMessage(senderUUID)) {
                conversation.setBurnNotice(true);
                conversation.setBurnDelay(TimeUnit.DAYS.toSeconds(3));
            }

            // The sender's UUID is valid due to the SearchFragment#validateUser(String) function
            // which also sets user's the display name in the name lookup cache.
            byte[] displayName = getDisplayName(senderUUID);
            if (displayName != null)
                conversation.getPartner().setDisplayName(new String(displayName));

            if (isSelf(senderUUID)) {
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
    @Nullable
    @WorkerThread
    public byte[] httpHelper(final byte[] requestUri, final String method, final byte[] requestData, final int[] code) {
        final StringBuilder content = new StringBuilder();

        String uri = new String(requestUri);

        URL requestUrl;
        final String resourceUrl = ConfigurationUtilities.getProvisioningBaseUrl(mContext) + uri.substring(1);
        try {
            requestUrl = new URL(resourceUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            code[0] = 400;
            return null;
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "request url: " + method + " " + requestUrl);
        HttpsURLConnection urlConnection = null;
        OutputStream out = null;
        try {
            urlConnection = (HttpsURLConnection) requestUrl.openConnection();
            final SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
            if (context != null) {
                urlConnection.setSSLSocketFactory(context.getSocketFactory());
            }
            else {
                Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                throw new AssertionError("Failed to get pinned SSL context");
            }

            urlConnection.setRequestMethod(method);
            urlConnection.setDoInput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
            urlConnection.setConnectTimeout(2000);

            if (requestData != null && requestData.length > 0) {
                urlConnection.setDoOutput(true);
                out = new BufferedOutputStream(urlConnection.getOutputStream());
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
            try {
                if (out != null)
                    out.close();
            } catch (IOException ignore) { }
            if (urlConnection != null)
                urlConnection.disconnect();
        }
    }

    public void notifyCallback(final int notifyActionCode, final byte[] actionInformation, final byte[] deviceId) {
        final String actionInfo = (actionInformation != null) ? new String(actionInformation) : null;
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Notify action: " + notifyActionCode + ", info: " + actionInfo);

        // actionInformation of DEVICE_SCAN is the name of the message receiver. Rescan devices for
        // this partner and then resend the last message to the partner's devices.
        if (notifyActionCode == AxolotlNative.DEVICE_SCAN && actionInformation != null) { // DEVICE_SCAN
            AxoCommandInBackground aib = new AxoCommandInBackground("rescanUserDevices", actionInfo) {
                @Override
                @WorkerThread
                public void onPostRun() {
                    // Because partner devices changed, we resend the last outgoing message
                    // (heuristic taken from Janis)
                    final Message messageToResend = MessageUtils.getLastOutgoingMessage(mContext, actionInfo);

                    if (messageToResend == null) {
                        return;
                    }

                    if (messageToResend.getState() == MessageStates.BURNED
                            || messageToResend.getState() == MessageStates.SYNC) {
                        return;
                    }

                    Date now = new Date();
                    Date messageComposePlus = new Date(messageToResend.getComposeTime() + 60000); // 60 seconds

                    // Make sure the message being resent is not more than a minute old from composition
                    if (now.after(messageComposePlus)) {
                        return;
                    }

                    MessageUtils.setAttributes(messageToResend);

                    // SendMessageTask extends AsyncTask and the execute should run from the main thread,
                    // thus queue it with the MainLooper.
                    Runnable msgSend = new Runnable() {
                        @Override
                        public void run() {
                            SendMessageTask task = new SendMessageTask(mContext.getApplicationContext());
                            AsyncUtils.execute(task, messageToResend);
                        }
                    };
                    Handler uiHandler = new Handler(Looper.getMainLooper());
                    uiHandler.post(msgSend);
                }
            };
            AsyncUtils.execute(aib);
        }
    }


    /**
     * Send a read notification for a message.
     *
     * The message in this case is an INCOMING message, thus use the "sender" to
     * create the message descriptor.
     *
     * Don't call from UI thread.
     *
     * @param msg Send read notification for this message
     */
    @WorkerThread
    public void sendReadNotification(final Message msg) {
        // Create a message descriptor, use the message id of the received message
        final JSONObject obj =  createMsgDescriptorJson(msg, msg.getSender());
        final JSONObject attributeJson = new JSONObject();

        // an empty message string
        try {
            obj.put("message", "");

            // inform about the time the client got the message
            attributeJson.put("cmd", "rr");
            attributeJson.put("rr_time", ISO8601.format(new Date()));
        } catch (JSONException ignore) {}

        final byte[] msgBytes = IOUtils.encode(obj.toString());
        final byte[] attributeBytes = IOUtils.encode(attributeJson.toString());

        createSendSyncReadReceipt(msgBytes, msg);
        final long[] netMsgIds = sendMessage(msgBytes, null, attributeBytes);
        if (netMsgIds == null) {
            Log.w(TAG, "ReadNotification to '" + msg.getSender() + "' could not be sent, code: " + getErrorCode() + ", info: " + getErrorInfo());
            msg.setFailureFlag(Message.FAILURE_READ_NOTIFICATION);
            return;
        }
        msg.clearFailureFlag(Message.FAILURE_READ_NOTIFICATION);
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "ReadNotification sent, mark queued: " + Long.toHexString(netMsgId));
            markMessageQueued(netMsgId, null);
        }
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
    @WorkerThread
    public void sendDeliveryNotification(final Message msg) {
        // Create a message descriptor, use the message id of the received message
        final JSONObject obj =  createMsgDescriptorJson(msg, msg.getSender());
        final JSONObject attributeJson = new JSONObject();

        // an empty message string
        try {
            obj.put("message", "");

            // inform about the time the client got the message
            attributeJson.put("cmd", "dr");
            attributeJson.put("dr_time", ISO8601.format(new Date()));
        } catch (JSONException ignore) {}

        final byte[] msgBytes = IOUtils.encode(obj.toString());
        final byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        final long[] netMsgIds = sendMessage(msgBytes, null, attributeBytes);
        if (netMsgIds == null) {
            Log.w(TAG, "DeliverNotification to '" + msg.getSender() + "' could not be send, code: " + getErrorCode() + ", info: " + getErrorInfo());
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
    @WorkerThread
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

        createSendSyncBurnNotice(msgBytes, msg);
        final long[] netMsgIds = sendMessage(msgBytes, null, attributeBytes);
        if (netMsgIds == null) {
            Log.w(TAG, "BurnNotice request to '" + msg.getConversationID() + "' could not be sent, code: " + getErrorCode() + ", info: " + getErrorInfo());
            msg.setFailureFlag(Message.FAILURE_BURN_NOTIFICATION);
            return;
        }
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "BurnNotice request sent, mark queued: " + Long.toHexString(netMsgId));
            markMessageQueued(netMsgId, null);
            msg.clearFailureFlag(Message.FAILURE_BURN_NOTIFICATION);
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

    @WorkerThread
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
        AsyncUtils.execute(sndBackground);
    }

    /* **********************************************************
     * Private functions
     ********************************************************** */

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

    // The recipient has to be the real "recipient", i.e. the UUID, not an alias name
    @NonNull
    private JSONObject createMsgDescriptorJson(final Message msg, String recipient) {
        if (recipient == null)
            recipient = Utilities.removeUriPartsSelective(msg.getConversationID());

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
    @WorkerThread
    private boolean processSpecialAttributes(final Conversation conv, final EventRepository events, final String msgId,
                                             final String attributes) {
        try {
            // Check for a command - if yes, process it, return true, message done.
            final JSONObject attributeJson = new JSONObject(attributes);
            final String command = attributeJson.optString("cmd", null);
            if (TextUtils.isEmpty(command))
                return false;
            switch (command) {
                case "rr": {
                    final String dateTime = attributeJson.getString("rr_time");
                    final long dt = parseDate(dateTime);
                    markPacketAsRead(events, msgId, dt);
                    return true;
                }

                case "bn":
                    burnPacket(conv, events, msgId, true);
                    return true;

                case "bnc":
                    // TODO - do the "burn confirmation handling" if necessary
                    return true;

                case "dr": {
                    final String dateTime = attributeJson.getString("dr_time");
                    final long dt = parseDate(dateTime);
                    markPacketAsDelivered(events, msgId, dt);
                    return true;
                }

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
                    final String recipient = Utilities.removeSipParts(msg.getConversationID());
                    final Conversation conversation = getConversations().findById(recipient);
                    final EventRepository events = getConversations().historyOf(conversation);
                    msg.addNetMessageId(netMsgId);
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Message sent (mms): " + Long.toHexString(netMsgId));
                    events.save(msg);

                    MessageUtils.notifyConversationUpdated(mContext, recipient, false,
                            UPDATE_ACTION_MESSAGE_STATE_CHANGE, msg.getId());
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
                    final String recipient = Utilities.removeSipParts(message.getConversationID());
                    final Conversation conversation = getConversations().findById(recipient);
                    final EventRepository events = getConversations().historyOf(conversation);
                    message.addNetMessageId(netMsgId);
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Message sent (mmq): " + Long.toHexString(netMsgId));
                    events.save(message);

                    MessageUtils.notifyConversationUpdated(mContext, recipient,
                            false, UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
                }
                eventState.remove(netMsgId);
            }
            else {
                eventState.put(netMsgId, message);  // mark it as queued to send
            }
        }
    }

    private void markPacketAsRead(final EventRepository events, final String deliveredPacketID, final long readReceiptTime) {
        final Event event = events.findById(deliveredPacketID);
        if (event instanceof Message) {
            final Message message = (Message)event;
            if (message.getState() == MessageStates.RECEIVED && !(message instanceof CallMessage)) {
                decrementUnreadMessages(message);
            } else if (message instanceof CallMessage && message.getState() != MessageStates.READ) {
                decrementUnreadCallMessages(message);
            }
            message.setState(MessageStates.READ);
            if (message.expires()) {
                final long rrTime = readReceiptTime <= 0 ? System.currentTimeMillis() : readReceiptTime;
                message.setExpirationTime(rrTime + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
//                message.setExpirationTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
            }
            events.save(message);

            MessageUtils.notifyConversationUpdated(mContext, MessageUtils.getConversationId(message),
                    false, UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
        }
    }

    private void markPacketAsDelivered(final EventRepository events, final String deliveredPacketID, final long deliveryTime) {
        final Event event = events.findById(deliveredPacketID);
        if (event instanceof OutgoingMessage) {
            final OutgoingMessage message = (OutgoingMessage)event;
            final int state = message.getState();
            /* message can be marked as delivered when state report 200 is received */
            if (state == MessageStates.SENT || state == MessageStates.SENT_TO_SERVER
                    || state == MessageStates.DELIVERED) {
                message.setState(MessageStates.DELIVERED);
                message.setDeliveryTime(deliveryTime);
                events.save(message);

                MessageUtils.notifyConversationUpdated(mContext, message.getConversationID(), false,
                        UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
            }
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
        // NOT_DECRYPTABLE (-13) or MAC_CHECK_FAILED (-23) the this a error because of "duplicate"
        // message decryption. This is not possible in Axolotl. It also indicates that the server
        // sent a message twice.
        String messageId = event.getMessageId();        // Id of erroneous message as set by the sender
        if (!TextUtils.isEmpty(messageId) && events.exists(messageId)
                && (errorCode == MessageErrorCodes.NOT_DECRYPTABLE
                    || errorCode == MessageErrorCodes.MAC_CHECK_FAILED)) {
            event.setDuplicate(true);
        }
        event.setId(UUIDGen.makeType1UUID().toString());
        event.setTime(System.currentTimeMillis());
        events.save(event);

        MessageUtils.notifyConversationUpdated(mContext, sender,
                true, UPDATE_ACTION_MESSAGE_SEND, event.getId());
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

        boolean isMessageUnread = false;
        boolean isCallMessageUnread = false;
        /*
         * Determine whether correct event is being removed, whether conversation partner
         * for message does not match the conversation itself.
         *
         * For incoming message conversation partner is sender, for outgoing message it is
         * in conversation id.
         */
        String conversationPartner;
        if (event instanceof IncomingMessage) {
            IncomingMessage message = (IncomingMessage) event;
            conversationPartner = message.getSender();
            if (message.getState() == MessageStates.RECEIVED) {
                isMessageUnread = true;
            }
        }
        else {
            Message message = (Message) event;
            conversationPartner = message.getConversationID();

            if (message instanceof CallMessage && message.getState() != MessageStates.READ) {
                isCallMessageUnread = true;
            }
        }

        if (!conv.getPartner().getUserId().equals(conversationPartner)) {
            Log.w(TAG, "Burn notice request - wrong caller");
            return;
        }

        /*
         * If a received message is burned and it is unread, decrement unread message count for
         * conversation.
         */
        if (isMessageUnread) {
            decrementUnreadMessages((Message) event);
        }

        if (isCallMessageUnread) {
            decrementUnreadCallMessages((Message) event);
        }

        events.remove(event);
        if (confirm)
            sendBurnNoticeConfirmation((Message)event, conversationPartner);

        MessageUtils.notifyConversationUpdated(mContext, conversationPartner, false,
                UPDATE_ACTION_MESSAGE_BURNED, event.getId());

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
    }

    /**
     * Send a burn notice confirmation to the request sender.
     **
     * @param msg Send burn notice confirmation for this message.
     * @param recipient Recipient for burn notice confirmation.
     */
    @WorkerThread
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
        AsyncUtils.execute(sndBackground);
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
            return ISO8601.parse(dateTime).getTime();
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
    @WorkerThread
    private void createSendSyncBurnNotice(final byte[] msgBytes, final Message msg) {
        final JSONObject attributeJson = new JSONObject();
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
     * Send a read receipt for incoming message to user's other devices.
     *
     * @param attributeJson The JSON object that already contains the normal burn command
     * @param msgBytes The message to attach to the attribute command data
     */
    @WorkerThread
    private void createSendSyncReadReceipt(final byte[] msgBytes, final Message msg) {
        final JSONObject attributeJson = new JSONObject();
        try {
            attributeJson.put("syc", "rr");
            attributeJson.put("or", MessageUtils.getConversationId(msg));
            attributeJson.put("rr_time", ISO8601.format(new Date()));
        } catch (JSONException ignore) {}

        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        long[] netMsgIds = sendMessageToSiblings(msgBytes, null, attributeBytes);
        if (netMsgIds == null) {
            Log.w(TAG, "ReadReceipt sync request could not be sent, code: " + getErrorCode() + ", info: " + getErrorInfo());
            return;
        }
        for (long netMsgId : netMsgIds) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "ReadReceipt sync request sent, mark queued: " + Long.toHexString(netMsgId));
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
    @WorkerThread
    private boolean createSendSyncOutgoing(final byte[] message, final Message msg) {
        final Conversation conversation = getOrCreateConversation(msg.getConversationID());
        String attributes = msg.getAttributes();
        JSONObject attributeJson;
        try {
            // Add special sync command
            attributeJson =  new JSONObject(TextUtils.isEmpty(attributes) ? "{}" : attributes);
            attributeJson.put("syc", "om");
            attributeJson.put("or", msg.getConversationID());
            attributeJson.put("dpn", conversation.getPartner().getDisplayName());
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

    @WorkerThread
    private void processSyncMessage(final String messageData, final String msgId, final byte[] attachmentDescriptor,
                                   final byte[] messageAttributes) {

        if (messageAttributes == null || messageAttributes.length == 0) {
            return;
        }
        final String attributes = new String(messageAttributes);

        final String attachment = (attachmentDescriptor != null) ? new String(attachmentDescriptor) : null;
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got message attributes (sync): " + attributes);
        try {
            // Check for a command - if yes, process it, return true, message done.
            final JSONObject attributeJson = new JSONObject(attributes);
            final String command = attributeJson.optString("syc", null);
            if (TextUtils.isEmpty(command))
                return;
            final String displayName = attributeJson.optString("dpn", null);
            final String originalReceiver = attributeJson.getString("or");  // The 'or' is a UUID
            final Conversation conversation = getOrCreateConversation(originalReceiver);

            if (TextUtils.isEmpty(conversation.getPartner().getDisplayName()) && !TextUtils.isEmpty(displayName)) {
                conversation.getPartner().setDisplayName(displayName);
                ConversationRepository conversations = getConversations();
                conversations.save(conversation);
            }

            // If the sender does not provide a display name for the sync conversation AND
            // if one/both of the fields are empty we got a sync message for a yet unknown
            // conversation. In this case we might need to contact the server, thus perform
            // an async task to get the info, then process the command.
            // If we already know the conversation just go on without the overhead of the
            // async task handling.
            if (TextUtils.isEmpty(displayName) &&
                    (TextUtils.isEmpty(conversation.getPartner().getAlias()) ||
                            TextUtils.isEmpty(conversation.getPartner().getDisplayName()))) {
                AsyncTasks.UserDataBackgroundTaskNotMain getAliasTask = new AsyncTasks.UserDataBackgroundTaskNotMain(originalReceiver) {
                    @Override
                    @WorkerThread
                    public void onPostRun() {
                        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onPostRun from processSyncMessage");
                        if (mUserInfo != null) {
                            ConversationRepository conversations = getConversations();
                            conversation.getPartner().setAlias(mUserInfo.mAlias);
                            conversation.getPartner().setDisplayName(mUserInfo.mDisplayName);
                            conversations.save(conversation);
                        }
                        switch (command) {
                            case "om":
                                processSyncOutgoingMessage(conversation, messageData, msgId, attachment, attributes);
                                break;

                            case "bn":
                                processSyncOutgoingBurn(conversation, msgId);
                                break;

                            case "rr":
                                final String dateTime = attributeJson.optString("rr_time");
                                final long dt = parseDate(dateTime);
                                processSyncReadReceipt(conversation, msgId, dt);
                                break;

                            // Check for others, ignore unknown commands, however don't process message any more
                            default:
                                break;
                        }
                    }
                };
                AsyncUtils.execute(getAliasTask);
            }
            else {
                switch (command) {
                    case "om":
                        processSyncOutgoingMessage(conversation, messageData, msgId, attachment, attributes);
                        break;

                    case "bn":
                        processSyncOutgoingBurn(conversation, msgId);
                        break;

                    case "rr":
                        final String dateTime = attributeJson.getString("rr_time");
                        final long dt = parseDate(dateTime);
                        processSyncReadReceipt(conversation, msgId, dt);
                        break;

                    // Check for others, ignore unknown commands, however don't process message any more
                    default:
                        break;
                }
            }
        } catch (JSONException ignore) { }
    }

    @WorkerThread
    private void processSyncOutgoingMessage(final Conversation conversation, final String messageData, final String msgId,
                                           final String attachment,
                                           final String attributes) {

        ConversationRepository conversations = getConversations();
        EventRepository events = conversations.historyOf(conversation);

        Message message = new OutgoingMessage(getUserName(), messageData);
        MessageUtils.parseAttributes(attributes, message);
        if(attachment != null) {
            message.setAttachment(attachment);
        }
        message.setState(MessageStates.SYNC);
        message.setConversationID(conversation.getPartner().getUserId());
        message.setId(msgId);

        events.save(message);
        conversation.setLastModified(System.currentTimeMillis());
        conversations.save(conversation);

        MessageUtils.notifyConversationUpdated(mContext, message.getConversationID(), true,
                UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());

        // Do an initial request to download the thumbnail
        if(attachment != null && !TextUtils.isEmpty(message.getAttachment()) && !message.hasMetaData()) {
            mContext.startService(SCloudService.getDownloadThumbnailIntent(message, conversation.getPartner().getUserId(), mContext));
        }
    }

    @WorkerThread
    private void processSyncOutgoingBurn(final Conversation conversation, final String msgId) {

        ConversationRepository conversations = getConversations();
        EventRepository events = conversations.historyOf(conversation);

        burnPacket(conversation, events, msgId, false);
    }

    @WorkerThread
    private void processSyncReadReceipt(final Conversation conversation, final String msgId, final long dt) {
        ConversationRepository conversations = getConversations();
        EventRepository events = conversations.historyOf(conversation);

        markPacketAsRead(events, msgId, dt);
    }

    /*
     * Decrement unread message count for a conversation
     */
    private void decrementUnreadMessages(Message message) {
        Conversation conversation = getConversations().findById(MessageUtils.getConversationId(message));
        if (conversation != null) {
            int unreadMessageCount = conversation.getUnreadMessageCount();
            conversation.setUnreadMessageCount(unreadMessageCount > 0 ? (unreadMessageCount - 1) : 0);
            getConversations().save(conversation);
        }
    }

    private void decrementUnreadCallMessages(Message message) {
        Conversation conversation = getConversations().findById(MessageUtils.getConversationId(message));
        if (conversation != null) {
            int unreadCallMessageCount = conversation.getUnreadCallMessageCount();
            conversation.setUnreadCallMessageCount(unreadCallMessageCount > 0 ? (unreadCallMessageCount - 1) : 0);
            getConversations().save(conversation);
        }
    }

    private class RegisterInBackground implements Runnable {
        final int[] code = new int[1];
        byte[] errorMsg;
        final Context mCtx;

        RegisterInBackground(Context ctx) {
            mCtx = ctx;
        }

        public void run() {
            long startTime = System.currentTimeMillis();
            errorMsg = registerAxolotlDevice(code);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for AxoRegistration: " + (System.currentTimeMillis() - startTime));

            if (code[0] < 0 || code[0] >= HttpURLConnection.HTTP_NOT_FOUND) {
                Log.e(TAG, "Axolotl registration failure: " + (errorMsg != null ? new String(errorMsg) : "null") + ", code: " + code[0]);
                return;
            }
            mSiblingsSynced = false;
            sendEmptySyncToSiblings();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
            prefs.edit().putBoolean(mRegisteredKey, true).apply();

            Runnable stateChange = new Runnable() {
                @Override
                public void run() {
                    registerStateChanged(true);
                }
            };
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(stateChange);
        }
    }

    private class SendCommandInBackground implements Runnable {
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
        public void run() {
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
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command send: " + (System.currentTimeMillis() - startTime));
        }
    }

    private class AxoCommandInBackground implements Runnable {

        private String mCommand;
        private byte[] mData;

        AxoCommandInBackground(String... commands) {
            if (commands.length >= 1)
                mData = IOUtils.encode(commands[1]);
            mCommand = commands[0];
        }
        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            axoCommand(mCommand, mData);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command '" + mCommand + "': " + (System.currentTimeMillis() - startTime));
            onPostRun();
        }

        public void onPostRun() {}
    }
}
