/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.AxoRegisterActivity;
import com.silentcircle.messaging.group.GroupMessaging;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.EventDeviceInfo;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.MessageStateEvent;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.DbRepository.DbConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.Manifest;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import org.acra.sender.SentrySender;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import zina.JsonStrings;
import zina.ZinaNative;

import static com.silentcircle.messaging.model.MessageErrorCodes.OK;
import static com.silentcircle.messaging.model.MessageStates.DELIVERED;
import static zina.JsonStrings.BURN;
import static zina.JsonStrings.DECRYPTION_FAILED;
import static zina.JsonStrings.DELIVERY_RECEIPT;
import static zina.JsonStrings.DELIVERY_TIME;
import static zina.JsonStrings.GROUP_ID;
import static zina.JsonStrings.MSG_COMMAND;
import static zina.JsonStrings.MSG_DEVICE_ID;
import static zina.JsonStrings.MSG_DISPLAY_NAME;
import static zina.JsonStrings.MSG_ID;
import static zina.JsonStrings.MSG_IDS;
import static zina.JsonStrings.MSG_MESSAGE;
import static zina.JsonStrings.MSG_RECIPIENT;
import static zina.JsonStrings.MSG_SENDER;
import static zina.JsonStrings.MSG_SYNC_COMMAND;
import static zina.JsonStrings.MSG_VERSION;
import static zina.JsonStrings.ORIGINAL_MESSAGE;
import static zina.JsonStrings.ORIGINAL_RECEIVER;
import static zina.JsonStrings.READ_RECEIPT;
import static zina.JsonStrings.READ_TIME;

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
public class ZinaMessaging extends ZinaNative {
    public static final String TAG = "ZinaMessaging";

    private static final String AXOLOTL_STORE_KEY = "axolotl_store_key";
    private static final String APP_CONV_REPO_KEY = "app_conv_repo_key";
    private static final String AXOLOTL_REGISTERED = "axolotl_device_registered";
    private static final String AXOLOTL_ASK_REGISTER = "axolotl_ask_register";

    public static final int SQLITE_OK = 0;
    public static final int SQLITE_ROW = 100;

    public static final int UPDATE_ACTION_MESSAGE_STATE_CHANGE = 1;
    public static final int UPDATE_ACTION_MESSAGE_BURNED = 2;
    public static final int UPDATE_ACTION_MESSAGE_SEND = 3;

    public static final int MIN_NUM_PRE_KEYS = 30;
    public static final int CREATE_NEW_PRE_KEYS = 100;

    public static final int SIP_OK = 200;
    public static final int SIP_ACCEPTED = 202;
    public static final int SIP_BAD_REQUEST = 400;
    public static final int SIP_FORBIDDEN = 403;
    public static final int SIP_NOT_FOUND = 404;
    public static final int SIP_PAYLOAD_TOO_LARGE = 413;
    public static final int SERVICE_NOT_AVAILABLE = 503;
    public static final int SIP_NETWORK_TIMEOUT = 599;

    public static final int TIVI_TIMEOUT = -1;
    public static final int TIVI_SLOW_NETWORK = -2;

    private static final int RE_SYNC_RATCHET = 1;

    // Delay re-sync command to avoid to many calls if getting many re-sync requests
    private static final int RE_SYNC_DELAY = 5000;

    // If clocks or parsed times are too much out of sync then restrict to max 60s in the future.
    // Times in the past are no problem.
    private static final int TIME_SKEW_FUTURE = 60 * 1000;

    /** Several modules use this format to define a common UTC date */
    private static final SimpleDateFormat ISO8601;
    private static final SimpleDateFormat ISO8601_WITH_MILLIS;
    static {
        ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
        ISO8601_WITH_MILLIS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ISO8601_WITH_MILLIS.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static class PendingNotification {

        public static final int TYPE_READ_NOTIFICATION = 0;
        public static final int TYPE_BURN_NOTIFICATION = 1;

        int notificationType;
        public Message message;

        public PendingNotification(Message message, int notificationType) {
            this.message = message;
            this.notificationType = notificationType;
        }
    }

    public static boolean mSuppressErrorView;
    private static ZinaMessaging instance;

    private static int mReSyncCounter;

    private boolean mIsReady;

    private String mName;

    private byte[] mScDeviceId;

    private boolean mAlreadyAsked;

    private static DbConversationRepository mConversationRepository;
    private static boolean mSiblingsSynced;

    private final Object eventStateSync = new Object();
    private final Object notificationStateSync = new Object();

    @SuppressLint("UseSparseArrays")
    private Map<Long, Message> eventState = new HashMap<>();
    private Map<Long, PendingNotification> notificationState = new HashMap<>();

    private String mRegisteredKey;
    private String mAskRegisterKey;

    private final Object syncObj = new Object();
    private final Collection<AxoMessagingStateCallback> msgStateListeners = new LinkedList<>();

    private final GroupMessaging mGroupMessaging = new GroupMessaging();

    public interface AxoMessagingStateCallback {
        void axoRegistrationStateChange(boolean registered);
    }

    /**
     * Internal handler to start ratchet re-sync.
     */
    private final ReSyncHandler mHandler = new ReSyncHandler(this);

    @NonNull
    public static synchronized ZinaMessaging getInstance() {
        if (instance == null)
            instance = new ZinaMessaging();
        return instance;
    }

    // No public constructor
    private ZinaMessaging() { }

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

        final Context ctx = SilentPhoneApplication.getAppContext();

        if (TextUtils.isEmpty(mName))
            LoadUserInfo.loadPreferences(ctx);
            mName = LoadUserInfo.getUuid();

        if (TextUtils.isEmpty(mName)) {
            return;
        }

        String computedDevId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(ctx, false));
        mScDeviceId = IOUtils.encode(computedDevId);
        byte[] apiKey = KeyManagerSupport.getSharedKeyData(ctx.getContentResolver(), ConfigurationUtilities.getShardAuthTag());


        // The application stores the conversations and their data already based on a user name.
        String dbName = ConfigurationUtilities.getRepoDbName();
        File dbFile = ctx.getDatabasePath(dbName);
        byte[] keyData = getRepositoryKey();
        if (keyData == null) {
            throw new IllegalStateException("No key data for repository database.");
        }

        int sqlCode = ZinaNative.repoOpenDatabase(dbFile.getAbsolutePath(), keyData);
        Arrays.fill(keyData, (byte) 0);
        if (sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW) {
            throw new IllegalStateException("Cannot open repository database, error code: " + sqlCode);
        }

        // Different Axolotl stores, one for each user on this device.
        keyData = KeyManagerSupport.getPrivateKeyData(ctx.getContentResolver(), AXOLOTL_STORE_KEY);
        // No key yet - generate one and store it
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(ctx.getContentResolver(), AXOLOTL_STORE_KEY, 32);
        }
        if (keyData == null) {
            throw new IllegalStateException("No key data for Axolotl database.");
        }
        dbName = mName + ConfigurationUtilities.getConversationDbName();
        dbFile = ctx.getDatabasePath(dbName);
        int initFlags = BuildConfig.DEBUG ? 1 : 0;   // set debug level
        if (isRegistered())
            initFlags |= (1 << 4);
        // Initialize the Axolotl environment
        int initResult = doInit(initFlags, dbFile.getAbsolutePath(), keyData, IOUtils.encode(mName), apiKey, mScDeviceId,
                LoadUserInfo.getLocalRetentionFlags());
        Arrays.fill(keyData, (byte) 0);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "ZINA initialization result: " + initResult);

        if (initResult <= 0)
            throw new IllegalStateException("Initialization of ZINA library failed - result " + initResult);

        mRegisteredKey = AXOLOTL_REGISTERED + "_" + mName;
        mAskRegisterKey = AXOLOTL_ASK_REGISTER + "_" + mName;

        mIsReady = true;
        // askAxolotlRegistration();
        registerDeviceMessaging(false);
        registerStateChanged(isRegistered());

        // TODO: Put this in a better place? I'm not sure where - Sam S.
        // Run handler for dealing with failed attachment operations
        Intent serviceIntent = Action.RUN_ATTACHMENT_HANDLER.intent(ctx, SCloudService.class);
        serviceIntent.putExtra("FROM_BOOT", true);
        ctx.startService(serviceIntent);
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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SilentPhoneApplication.getAppContext());
        return prefs.getBoolean(mRegisteredKey, false);
    }

    public void setRegistered(boolean isRegistered) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SilentPhoneApplication.getAppContext());
        prefs.edit().putBoolean(mRegisteredKey, isRegistered).apply();
    }

    @SuppressWarnings("unused")
    public void askAxolotlRegistration() {
        if (isRegistered() || mAlreadyAsked || !mIsReady)
            return;

        mAlreadyAsked = true;
        final Context ctx = SilentPhoneApplication.getAppContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        if (!prefs.getBoolean(mAskRegisterKey, true))
            return;
        final Intent intent = new Intent(ctx, AxoRegisterActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(AxoRegisterActivity.ACTION_REGISTER);
        ctx.startActivity(intent);
    }

    public void setAskToRegister(final boolean ask) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SilentPhoneApplication.getAppContext());
        prefs.edit().putBoolean(mAskRegisterKey, ask).apply();
    }

    public void registerDeviceMessaging(final boolean force) {
        final Context ctx = SilentPhoneApplication.getAppContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final boolean registered = prefs.getBoolean(mRegisteredKey, false);
        if (!registered || force) {
            RegisterInBackground registerBackground = new RegisterInBackground(ctx);
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
                    new DbConversationRepository(mName);
        }
        return mConversationRepository;
    }

    /**
     * Return key data for the repository database.
     *
     * @return a 32byte (256 bit) key or {@code null} if none is available
     */
    @Nullable
    private byte[] getRepositoryKey() {
        final Context ctx = SilentPhoneApplication.getAppContext();
        byte[] keyData = KeyManagerSupport.getPrivateKeyData(ctx.getContentResolver(), APP_CONV_REPO_KEY);

        // No key yet - generate one and store it
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(ctx.getContentResolver(), APP_CONV_REPO_KEY, 32);
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
    @WorkerThread
    public boolean sendMessage(final Message msg, Conversation conversation) {
        if (!isReady()) {
            initialize();
            if (!isReady())
                return false;
        }
        final byte[] messageBytes = createMsgDescriptor(msg, msg.getText());

        // if message recipient is sender himself sync message to devices only
        if (MessageUtils.isNoteToUser(msg, getUserName())) {
            Log.d(TAG, "Synchronizing message to the devices of " + msg.getConversationID());
            boolean result = createSendSyncOutgoing(messageBytes, msg, new PreparedMessageData[0]);

            MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(),
                    msg.getConversationID(), false, UPDATE_ACTION_MESSAGE_STATE_CHANGE, msg.getId());

            return result;
        }

        int[] code = new int[1];
        PreparedMessageData[] preparedMessageData = prepareMessageNormal(messageBytes, msg.getAttachmentAsByteArray(), msg.getAttributesAsByteArray(), true, code);
        if (preparedMessageData == null) {
            Log.w(TAG, "Message to '" + msg.getConversationID() + "' could not be send, code: " + code[0] + ", info: " + getErrorInfo());
            return false;
        }
        Contact contact = conversation.getPartner();
        boolean hasHadDeviceInfos = contact.numDeviceInfos() >= 0;

        // NOTE: we could enhance the app/UI to offer a selection of devices before sending out the message, together
        // with above enhancement of Message class.
        long[] transportIds = new long[preparedMessageData.length];
        EventDeviceInfo[] deviceInfos = new EventDeviceInfo[preparedMessageData.length];

        int idx = 0;
        for (PreparedMessageData msgD : preparedMessageData) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Message prepared and queued: " + Long.toHexString(msgD.transportId));
            markMessageQueued(msgD, msg);
            deviceInfos[idx] = createEventDeviceInfo(msgD);
            transportIds[idx++] = msgD.transportId;
            // This conversation had no device info, fill in the first set. Usually happens when we send the first
            // message to a new conversation (or fill in on old conversation -> migration)
            ConversationUtils.fillDeviceData(getConversations(), contact, msgD.receiverInfo, hasHadDeviceInfos);
        }
        msg.setEventDeviceInfo(deviceInfos);
        createSendSyncOutgoing(messageBytes, msg, preparedMessageData);

        doSendMessages(transportIds);
        MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(), msg.getConversationID(), false,
                UPDATE_ACTION_MESSAGE_STATE_CHANGE, msg.getId());

        return true;
    }

    @WorkerThread
    public int sendGroupMessage(final Message msg, @Nullable final String user,
            @Nullable final Collection<String> devices) {
        if (!isReady()) {
            initialize();
            if (!isReady())
                return MessageErrorCodes.GENERIC_ERROR;
        }
        final byte[] messageBytes = createGroupMessageDescriptor(msg);
        int result;
        if (TextUtils.isEmpty(user)) {
            result = ZinaMessaging.sendGroupMessage(messageBytes, msg.getAttachmentAsByteArray(),
                    msg.getAttributesAsByteArray());
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Group message sent: " + result);
        }
        else {
            if (devices == null || devices.isEmpty()) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Device list empty, nothing to send");
                // no devices to send to, success
                result = MessageErrorCodes.OK;
            }
            else {
                int deviceResult;
                result = MessageErrorCodes.GENERIC_ERROR;
                byte[] encodedUser = IOUtils.encode(user);
                for (String device : devices) {
                    deviceResult = ZinaMessaging.sendGroupMessageToMember(messageBytes,
                            msg.getAttachmentAsByteArray(),
                            msg.getAttributesAsByteArray(), encodedUser, device);
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Group message to " + user
                            + ", device: " + device
                            + " sent: " + deviceResult);
                    // consider sending successful if message sent to at least one device
                    if (result != MessageErrorCodes.OK) {
                        result = deviceResult;
                    }
                }
            }
        }

        return result;
    }

    /* *********************************************************
     * Callback functions from the ZINA and network layer
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
        String senderDeviceId;
        final String messageData;
        String aliasName = "";         // Initialize to Empty, used to check in async task
        String displayName;
        String msgId;
        try {
            // Parse and get information from message descriptor
            obj = new JSONObject(msg);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got a message: " + obj.toString());
            sender = obj.getString(MSG_SENDER);               // Sender is the UUID
            displayName = obj.getString(MSG_DISPLAY_NAME);    // Got this name from SIP PAI or From header
            senderDeviceId = obj.getString(MSG_DEVICE_ID);
            msgId = obj.getString(MSG_ID);
            messageData = obj.getString(MSG_MESSAGE);
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }

        if (TextUtils.isEmpty(msgId)) {
            Log.w(TAG, "Received message with empty id");
            msgId = UUIDGen.makeType1UUID().toString();
        }

        if (sender.equalsIgnoreCase(getUserName())) {
            processSyncMessage(messageData, msgId, attachmentDescriptor, messageAttributes, senderDeviceId);
            return OK;
        }

        final ConversationRepository conversations = getConversations();
        final Conversation conversation = getOrCreateConversation(sender);

        final EventRepository events = conversations.historyOf(conversation);

        // Try to get one of the sender's alias name, try cached data first.
        byte[][] senderAliases = ZinaMessaging.getAliases(sender);
        if (senderAliases == null || senderAliases.length == 0) {   // None available, ask the server to get an alias
            AsyncTasks.UserDataBackgroundTaskNotMain getAliasTask = new AsyncTasks.UserDataBackgroundTaskNotMain(sender) {
                @Override
                @WorkerThread
                public void onPostRun() {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "onPostRun from receive");
                    if (events != null && TextUtils.isEmpty(conversation.getPartner().getAlias())
                            && mUserInfo != null) {
                        synchronized (syncObj) {
                            conversation.getPartner().setAlias(mUserInfo.mAlias);
                            conversation.getPartner().setValidated(true);
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
                if (!TextUtils.isEmpty(attributes) && processSpecialAttributes(conversation, events, msgId, attributes, senderDeviceId)) {
                    return OK;
                }
                if (!events.exists(msgId)) {
                    final IncomingMessage message = new IncomingMessage(conversation.getPartner().getUserId(), msgId, messageData);
                    final Context ctx = SilentPhoneApplication.getAppContext();

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
                        message.setText(ctx.getString(R.string.attachment));
                    }
                    if (message.isRetained()) {
                        MessageUtils.updateRetentionInfo(message);
                    }
                    events.save(message);

                    conversation.offsetUnreadMessageCount(1);
                    conversation.setLastModified(System.currentTimeMillis());
                    conversations.save(conversation);

                    MessageUtils.notifyMessageReceived(ctx, sender, aliasName,
                            message.isRequestReceipt(),
                            conversation.isMuted(),
                            message.getId());

                    if (attachmentDescriptor != null) {
                        // Do an initial request to download the thumbnail
                        if (!TextUtils.isEmpty(message.getAttachment()) && !AttachmentUtils.hasThumbnail(message)) {
                            ctx.startService(SCloudService.getDownloadThumbnailIntent(message, conversation.getPartner().getUserId(), ctx));
                        }
                    }
                }
            }
        }
        return OK;
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
            }
            getConversations().save(conversation);
        }
        return conversation;
    }

    private boolean isSelf(String username) {
        String self = mName;
        if (username == null) {
            return self == null;
        }
        return self != null && self.equalsIgnoreCase(username);
    }

    @Override
    public void messageStateReport(long networkMessageId, final int statusCode, final byte[] stateInformation) {
        if (ConfigurationUtilities.mTrace)
            Log.d(TAG, "Message state report, msgId: " + Long.toHexString(networkMessageId) + ", code: " + statusCode);
        long statusBits = networkMessageId & 0xf;
        if (networkMessageId != 0) {
            switch (statusCode) {
                case SIP_OK:
                    markMessageSent(networkMessageId, DELIVERED);
                    markNotificationSent(networkMessageId);
                    return;
                case SIP_ACCEPTED:
                    markMessageSent(networkMessageId, MessageStates.SENT_TO_SERVER);
                    markNotificationSent(networkMessageId);
                    return;
                case TIVI_TIMEOUT:
                case TIVI_SLOW_NETWORK:
                    Log.w(TAG, "Message state report with status code: " + statusCode);
                    markMessageFailed(networkMessageId, statusCode, MessageStates.FAILED);
                    return;
                default:
                    if (statusCode >= SIP_BAD_REQUEST && statusCode <= SIP_NETWORK_TIMEOUT) {
                        Log.w(TAG, "Message state report with status code: " + statusCode);
                        markMessageFailed(networkMessageId, statusCode, MessageStates.FAILED);
                    }
                    else {
                        Log.e(TAG, "Message state report, unknown status code: " + statusCode);
                        Event event = getMessageFromEventStates(networkMessageId);
                        if (event != null) {
                            SentrySender.sendMessageStateReport(event, statusCode,
                                    MessageErrorCodes.GENERIC_ERROR);
                        }
                    }
                    return;
            }
        }
        // A messageIdentifier of 0 means that the Axolotl lib received data that could
        // not be processed. The status code contains the Axolotl lib error code.
        final String info = (stateInformation != null) ? new String(stateInformation) : "{}";

        if (!ZinaMessaging.mSuppressErrorView && statusCode != MessageErrorCodes.SUCCESS) {
            createErrorEvent(statusCode, info);
        }
        // Check MessageErrorCodes.MAC_CHECK_FAILED and re-sync after a delay.
        // To avoid too many re-syncs the code first removes a possible pending re-sync
        // message and then schedules a new message with the delay timer.
        if (statusCode == MessageErrorCodes.MAC_CHECK_FAILED) {
            mHandler.removeMessages(RE_SYNC_RATCHET);
            android.os.Message msg = mHandler.obtainMessage(RE_SYNC_RATCHET, info);
            mHandler.sendMessageDelayed(msg, RE_SYNC_DELAY);
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Receive error: " + statusCode + ", info: " + info);
    }

    @Override
    @Nullable
    @WorkerThread
    public byte[] httpHelper(final byte[] requestUri, final String method, final byte[] requestData, final int[] code) {
        final StringBuilder content = new StringBuilder();

        String uri = new String(requestUri);
        final Context ctx = SilentPhoneApplication.getAppContext();

        // If the URI starts with http:// or https:// then it's a request for an external HTTP resource, not from
        // the API server. This is from the axolotl library for POSTing data retention data to Amazon S3 bucket URIs.
        final boolean external = uri.startsWith("http://") || uri.startsWith("https://");
        final String resourceUrl = external ? uri : ConfigurationUtilities.getProvisioningBaseUrl(ctx) + uri.substring(1);
        URL requestUrl;
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
            if (!external) {
                final SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
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
            if (!Utilities.isNetworkConnected(ctx))
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
        if (notifyActionCode == ZinaNative.DEVICE_SCAN && actionInformation != null) { // DEVICE_SCAN
            final long now = System.currentTimeMillis();

            ZinaCommandInBackground aib = new ZinaCommandInBackground("rescanUserDevices", actionInfo) {
                @Override
                @WorkerThread
                public void onPostRun() {
                    // Because partner devices changed, we resend the last outgoing message
                    // (heuristic taken from Janis)
                    final Context ctx = SilentPhoneApplication.getAppContext();
                    final ConversationRepository repository = getConversations();
                    final List<String> conversations = ConversationUtils.getConversationsWithParticipant(actionInfo);
                    final Collection<String> newDevices = ConversationUtils.getNewDeviceIds(
                            repository, actionInformation);

                    Handler uiHandler = null;
                    for (String conversationId : conversations) {
                        final Collection<Message> messagesToResend =
                                MessageUtils.getOutgoingMessagesYoungerThan(conversationId,
                                        now - 60000L);

                        for (final Message messageToResend : messagesToResend) {
                            if (messageToResend.getState() == MessageStates.BURNED
                                    || messageToResend.getState() == MessageStates.SYNC) {
                                continue;
                            }

                            if (newDevices.isEmpty()) {
                                /*
                                 * If there are no devices for recipient, mark message as failed.
                                 * This is done only for one-to-one conversations.
                                 */
                                final Conversation conversation =
                                        repository.findByPartner(conversationId);
                                if (conversation != null
                                        && !conversation.getPartner().isGroup()
                                        && !(messageToResend.getState() == MessageStates.DELIVERED
                                            || messageToResend.getState() == MessageStates.READ)) {
                                    messageToResend.setState(MessageStates.FAILED);
                                    repository.historyOf(conversation).save(messageToResend);
                                }
                                continue;
                            }

                            MessageUtils.setAttributes(messageToResend);

                            android.util.Log.d(TAG, "conversationId " + conversationId
                                    + " resending message " + messageToResend.getId());
                            // SendMessageTask extends AsyncTask and the execute should run from the main thread,
                            // thus queue it with the MainLooper.
                            Runnable msgSend = new Runnable() {
                                @Override
                                public void run() {
                                    SendMessageTask task =
                                            new SendMessageTask(ctx.getApplicationContext(),
                                                    actionInfo, newDevices);
                                    AsyncUtils.execute(task, messageToResend);
                                }
                            };

                            if (uiHandler == null) {
                                uiHandler = new Handler(Looper.getMainLooper());
                            }
                            uiHandler.post(msgSend);
                        }
                    }

                    /*
                     * Update device list in all conversations where user is present.
                     * It may be desirable to add info events to conversation which had last message
                     * which was re-sent.
                     */
                    ConversationUtils.updateDeviceData(repository, conversations,
                            actionInformation);
                }
            };
            AsyncUtils.execute(aib);
        }
    }

    /* ***************************************
     * Callback functions for group chat.
     *
     * Entry points implemented here in the generic messaging class to simplify initialization in C++
     * code. The callbacks just forward to the real implementation in the new GroupMessaging class (tbd).
     ***************************************** */
    public int groupMsgReceive(byte[] messageDescriptor, byte[] attachmentDescriptor, byte[] messageAttributes) {
        return mGroupMessaging.groupMsgReceive(SilentPhoneApplication.getAppContext(),
                messageDescriptor, attachmentDescriptor, messageAttributes);
    }

    public int groupCmdReceive(byte[] commandMessage) {
        return mGroupMessaging.groupCmdReceive(SilentPhoneApplication.getAppContext(),
                commandMessage);
    }

    public void groupStateCallback(int errorCode, byte[] stateInformation) {
        mGroupMessaging.groupStateCallback(SilentPhoneApplication.getAppContext(),
                errorCode, stateInformation);
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
            obj.put(MSG_MESSAGE, "");

            // inform about the time the client got the message
            attributeJson.put(MSG_COMMAND, READ_RECEIPT);
            attributeJson.put(READ_TIME, ISO8601.format(new Date()));
        } catch (JSONException ignore) {}

        final byte[] msgBytes = IOUtils.encode(obj.toString());
        final byte[] attributeBytes = IOUtils.encode(attributeJson.toString());

        createSendSyncReadReceipt(msgBytes, msg);

        int[] code = new int[1];
        PreparedMessageData[] msgData = prepareMessageNormal(msgBytes, null, attributeBytes, false, code);
        if (msgData == null) {
            Log.w(TAG, "ReadNotification to '" + msg.getSender() + "' could not be sent, code: " + getErrorCode() + ", info: " + getErrorInfo());
            msg.setFailureFlag(Message.FAILURE_READ_NOTIFICATION);
            return ;
        }
        long[] transportIds = new long[msgData.length];
        int idx = 0;
        for (PreparedMessageData msgD : msgData) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "ReadNotification prepared and queued: " + Long.toHexString(msgD.transportId));
            // NOTE: We could enhance Message class to contain an array of the PreparedMessageData to keep track of message status per device
            markMessageQueued(msgD, null);
            markNotificationQueued(msgD, msg, PendingNotification.TYPE_READ_NOTIFICATION);
            transportIds[idx++] = msgD.transportId;
        }
        doSendMessages(transportIds);
    }

    /**
     * Send a read notification for messages.
     *
     * Don't call from UI thread.
     *
     * @param groupId Group to notify.
     * @param messageIds Array of message ids.
     */
    @WorkerThread
    public void sendGroupReadNotification(@NonNull final String groupId,
            @NonNull final CharSequence... messageIds) {
        final JSONObject attributeJson = new JSONObject();
        final JSONArray messageIdsArray = new JSONArray();
        try {
            for (CharSequence messageId : messageIds) {
                if (!TextUtils.isEmpty(messageId)) {
                    messageIdsArray.put(messageId.toString());
                }
            }

            // inform about the time the client got the message
            attributeJson.put(JsonStrings.GROUP_COMMAND, READ_RECEIPT);
            attributeJson.put(READ_TIME, ISO8601.format(new Date()));
            attributeJson.put(MSG_IDS, messageIdsArray);
            attributeJson.put(GROUP_ID, groupId);

            // in group conversation read notification is synced with siblings only
            ZinaNative.sendGroupCommandToMember(groupId, IOUtils.encode(getUserName()), null,
                    IOUtils.encode(attributeJson.toString()));
        } catch (JSONException ignore) {}
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
            obj.put(MSG_MESSAGE, "");
            attributeJson.put(MSG_COMMAND, BURN);
        } catch (JSONException ignore) {}
        final byte[] msgBytes = IOUtils.encode(obj.toString());
        final byte[] attributeBytes = IOUtils.encode(attributeJson.toString());

        createSendSyncBurnNotice(msgBytes, msg);

        int[] code = new int[1];
        PreparedMessageData[] msgData = prepareMessageNormal(msgBytes, null, attributeBytes, false, code);
        if (msgData == null) {
            Log.w(TAG, "BurnNotice request to '" + msg.getConversationID() + "' could not be sent, code: " + getErrorCode() + ", info: " + getErrorInfo());
            return;
        }
        long[] transportIds = new long[msgData.length];
        int idx = 0;
        for (PreparedMessageData msgD : msgData) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "BurnNotice request prepared and queued: " + Long.toHexString(msgD.transportId));
            // NOTE: We could enhance Message class to contain an array of the PreparedMessageData to keep track of message status per device
            markMessageQueued(msgD, null);
            markNotificationQueued(msgD, msg, PendingNotification.TYPE_BURN_NOTIFICATION);
            transportIds[idx++] = msgD.transportId;
        }
        doSendMessages(transportIds);
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
        OutgoingMessage message = new OutgoingMessage(getUserName(), "");
        message.setId(IOUtils.encode(msgId));
        message.setConversationID(getUserName());

        JSONObject obj =  createMsgDescriptorJson(message, null);

        sendCommandInBackground(obj, "sye");

    }

    public void runMessageRetry() {
        ZinaCommandInBackground aib = new ZinaCommandInBackground("runRetry", null);
        AsyncUtils.execute(aib);
    }

    private void sendCommandInBackground(JSONObject obj, String command) {
        JSONObject attributeJson = new JSONObject();

        // an empty message string and the request burn notice request command
        try {
            obj.put(MSG_MESSAGE, "");
            attributeJson.put(MSG_COMMAND, command);
        } catch (JSONException ignore) {}

        byte[] msgBytes = IOUtils.encode(obj.toString());
        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());
        SendCommandInBackground sndBackground = new SendCommandInBackground(msgBytes, null, attributeBytes, true);
        AsyncUtils.execute(sndBackground);

    }

    /**
     * Helper function to start a rescan of devices on own account to learn new sibling devices.
     */
    public void rescanSiblingDevices() {
        ZinaCommandInBackground aib = new ZinaCommandInBackground("rescanUserDevices", mName) {
            @Override
            @WorkerThread
            public void onPostRun() {
            }
        };
        AsyncUtils.execute(aib);
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
            obj.put(MSG_MESSAGE, text);
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
            obj.put(MSG_VERSION, 1);
            obj.put(MSG_RECIPIENT, recipient);
            obj.put(MSG_DEVICE_ID, new String(mScDeviceId));
            obj.put(MSG_ID, msg.getId());
        } catch (JSONException ignore) {
            // In practice, this superfluous exception can never actually happen.
        }
        return obj;
    }

    @Nullable
    private EventDeviceInfo createEventDeviceInfo(PreparedMessageData msgData) {
        //identityKey:device name:device id:verify state
        String elements[] = msgData.receiverInfo.split(":");
        if (elements.length != 4) {
            return null;
        }
        return new EventDeviceInfo(elements[1], elements[2], 0, msgData.transportId);
    }

    private byte[] createGroupMessageDescriptor(@NonNull final Message msg) {

        String msgId = msg.getConversationID();
        final JSONObject obj = new JSONObject();
        try {
            obj.put(MSG_VERSION, 1);
            obj.put(MSG_RECIPIENT, msg.getConversationID());
            obj.put(MSG_ID, msg.getId());
            obj.put(MSG_MESSAGE, msg.getText());
        } catch (JSONException ignore) {
            // In practice, this superfluous exception can never actually happen.
        }
        return IOUtils.encode(obj.toString());
    }

    // Function to check receipt ack, burn, etc - see consumePacket(...) in DefaultPacketOutput class in SilentText
    @WorkerThread
    private boolean processSpecialAttributes(final Conversation conv, final EventRepository events, final String msgId,
                                             final String attributes, final String senderDeviceId) {
        try {
            // Check for a command - if yes, process it, return true, message done.
            final JSONObject attributeJson = new JSONObject(attributes);
            final String command = attributeJson.optString("cmd", null);
            if (TextUtils.isEmpty(command))
                return false;
            switch (command) {
                case READ_RECEIPT: {
                    final String dateTime = attributeJson.getString(READ_TIME);
                    final long dt = parseDate(dateTime);
                    markPacketAsRead(conv, events, msgId, dt, senderDeviceId);
                    return true;
                }

                case BURN:
                    burnPacket(conv, events, msgId, true);
                    return true;

                case "bnc":
                    // TODO - do the "burn confirmation handling" if necessary
                    return true;

                case DELIVERY_RECEIPT: {
                    final String dateTime = attributeJson.getString(DELIVERY_TIME);
                    final long dt = parseDate(dateTime);
                    markPacketAsDelivered(conv, events, msgId, dt, senderDeviceId);
                    return true;
                }
//                case DR_DATA_REQUIRED:       //!< Local client requires to retain plaintext data, remote party does not accept this policy
//                case DR_META_REQUIRED: {     //!< Local client requires to retain meta data, remote party does not accept this policy
//                    Log.d(TAG, "Local client requires to retain meta data or plaintext, remote party does not accept this policy");
//                    handleRejectedMessage(conv, events, msgId, senderDeviceId,
//                            attributeJson.getString(COMMAND_TIME), ErrorEvent.POLICY_ERROR_RETENTION_REQUIRED );
//                    return true;
//                }
//                case DR_DATA_REJECTED:       //!< Remote party retained plaintext data, local client blocks this policy
//                case DR_META_REJECTED: {     //!< Remote party retained meta data, local client blocks this policy
//                    Log.d(TAG, "Remote party retained meta data or plaintext, local client blocks this policy");
//                    handleRejectedMessage(conv, events, msgId, senderDeviceId,
//                            attributeJson.getString(COMMAND_TIME), ErrorEvent.POLICY_ERROR_MESSAGE_REJECTED);
//                    return true;
//                }
                case DECRYPTION_FAILED:         // Receiver could not decrypt the message
                    Log.w(TAG, "Remote party could not process received message");
                    handleMessageDecryptFailureNotification(conv, events, msgId, senderDeviceId);
                    return true;

//                case COMM_BLOCKED:       //!< Local client requires to retain plaintext data, remote party does not accept this policy
//                    Log.d(TAG, "Remote client has local DR policy enabled by organisation but blocked it locally.");
//                    handleRejectedMessage(conv, events, msgId, senderDeviceId,
//                            attributeJson.getString(COMMAND_TIME), ErrorEvent.POLICY_ERROR_MESSAGE_BLOCKED );
//                    return true;

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

//    private void handleRejectedMessage(@NonNull Conversation conversation,
//            @NonNull EventRepository events, @NonNull String msgId, String senderDeviceId,
//            @NonNull String dateTime, String text) {
//
//        final long time = parseDate(dateTime);
//        markPacketAsFailed(events, msgId, time, senderDeviceId);
//
//        Event event = events.findById(msgId);
//        if (event != null) {
//            final String sender = conversation.getPartner().getUserId();
//            ErrorEvent errorEvent = new ErrorEvent();
//            errorEvent.setSender(conversation.getPartner().getUserId());
//            errorEvent.setSentToDevId(senderDeviceId);
//            errorEvent.setText(text);
//            errorEvent.setId(UUIDGen.makeType1UUID().toString());
//            errorEvent.setTime(System.currentTimeMillis());
//            errorEvent.setMessageId(msgId);
//            events.save(errorEvent);
//
//            final Context ctx = SilentPhoneApplication.getAppContext();
//            MessageUtils.notifyConversationUpdated(ctx, sender,
//                    false, UPDATE_ACTION_MESSAGE_SEND, errorEvent.getId());
//
//            MessageUtils.notifyConversationDrEvent(ctx, conversation.getPartner().getUserId(),
//                    text, msgId);
//        }
//    }

    @Nullable
    private Message getMessageFromEventStates(long netMsgId) {
        Message message;
        try {
            synchronized (eventStateSync) {
                netMsgId >>>= 4;            // Shift away status bits
                message = eventState.get(netMsgId);
            }
        } catch (Exception e) {
            message = null;
        }
        return message;
    }

    private void handleMessageDecryptFailureNotification(@NonNull Conversation conversation,
            @NonNull EventRepository events, String msgId, String senderDeviceId) {
        final Context ctx = SilentPhoneApplication.getAppContext();
        Event event = events.findById(msgId);
        final String sender = conversation.getPartner().getUserId();
        if (event != null) {
            updateEventDeviceInfoState(event, senderDeviceId, MessageStates.FAILED);
            if (event instanceof OutgoingMessage) {
                Message message = (OutgoingMessage) event;
                int state = message.getState();
                if (state == MessageStates.SENT || state == MessageStates.SENT_TO_SERVER
                        || state == MessageStates.SYNC) {
                    message.setState(MessageStates.FAILED);
                    events.save(event);
                    MessageUtils.notifyConversationUpdated(ctx, sender, false,
                            UPDATE_ACTION_MESSAGE_STATE_CHANGE, msgId);
                }
            }
            SentrySender.sendMessageStateReport(event, 0, MessageErrorCodes.NOT_DECRYPTABLE);
        }
        ErrorEvent errorEvent = new ErrorEvent();
        errorEvent.setSender(sender);
        errorEvent.setError(MessageErrorCodes.NOT_DECRYPTABLE);
        errorEvent.setSentToDevId(senderDeviceId);
        errorEvent.setText(ErrorEvent.DECRYPTION_ERROR_MESSAGE_UNDECRYPTABLE);
        errorEvent.setId(UUIDGen.makeType1UUID().toString());
        errorEvent.setTime(System.currentTimeMillis());
        errorEvent.setMessageId(msgId);
        events.save(errorEvent);

        MessageUtils.notifyConversationUpdated(ctx, sender,
                false, UPDATE_ACTION_MESSAGE_SEND, errorEvent.getId());
    }

    private void markMessageFailed(long netMsgId, final int statusCode, @MessageStates.MessageState final int messageState) {
        synchronized (eventStateSync) {
            final long originalNetMsgId = netMsgId;
            netMsgId >>>= 4;            // Shift away status bits
            final Message msg = eventState.get(netMsgId);
            // client may send one message to multiple devices
            // - handle real state changes only
            // - if the client recorded at least one DELIVERED (200) or SENT_TO_SERVER (202) state then don't overwrite
            //   it with a weaker state (FAILED (for -1 or -2))
            if (msg != null) {
                msg.setFailureFlag(Message.FAILURE_NOT_SENT);
                int currentState = msg.getState();
                if (currentState != messageState && currentState != DELIVERED
                        && currentState != MessageStates.SENT_TO_SERVER) {
                    updateMessageState(messageState, msg, false /* saveOnly */);

                    Log.w(TAG, "Message sending failed (mms): " + Long.toHexString(netMsgId)
                            + ", msg id: " + msg.getId() + ", status code: " + statusCode);
                }
                // error code to be ignored in this case, status code is important
                SentrySender.sendMessageStateReport(msg, statusCode, MessageErrorCodes.SUCCESS);
            }
            eventState.remove(netMsgId);
        }
    }

    // stateReport calls this to mark the message as sent.
    private void markMessageSent(long netMsgId, @MessageStates.MessageState final int messageState) {
        synchronized (eventStateSync) {
            final long originalNetMsgId = netMsgId;
            netMsgId >>>= 4;            // Shift away status bits
            final Message msg = eventState.get(netMsgId);
            // client may send one message to multiple devices
            // - handle real state changes only
            // - if the client recorded at least one DELIVERED state then don't overwrite
            //   it with a weaker state (DELIVERED (200) is stronger than SENT_TO_SERVER (202))
            if (msg != null) {
                // Message successfully sent, clear failure flag
                msg.clearFailureFlag(Message.FAILURE_NOT_SENT);
                // Always update device specific message state, for message in general don't overwrite stronger states
                updateEventDeviceInfoState(msg, originalNetMsgId, messageState);
                if (msg.getState() < DELIVERED)
                    updateMessageState(messageState, msg, false  /*saveOnly */);
                else
                    updateMessageState(messageState, msg, true /*saveOnly */);

                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Message sent (mms): " + Long.toHexString(netMsgId));
            }
            eventState.remove(netMsgId);
        }
    }

    private void markNotificationSent(long netMsgId) {
        Log.d(TAG, "markNotificationSent: " + Long.toHexString(netMsgId));
        synchronized (notificationStateSync) {
            netMsgId >>>= 4;            // Shift away status bits
            final PendingNotification notification = notificationState.get(netMsgId);
            if (notification == null) {
                Log.d(TAG, "markNotificationSent: no notification pending for " + Long.toHexString(netMsgId));
                return;
            }

            final Message msg = notification.message;
            if (notification.notificationType == PendingNotification.TYPE_READ_NOTIFICATION) {
                if (msg instanceof IncomingMessage) {
                    msg.clearFailureFlag(Message.FAILURE_READ_NOTIFICATION);
                    updateMessageState(msg.getState(), msg, true /* saveOnly */);
                }
            }
            else if (notification.notificationType == PendingNotification.TYPE_BURN_NOTIFICATION) {
                if (msg.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
                    msg.clearFailureFlag(Message.FAILURE_BURN_NOTIFICATION);

                    ConversationRepository repository = getConversations();
                    Conversation conversation = repository.findById(MessageUtils.getConversationId(msg));
                    MessageUtils.deleteEvent(SilentPhoneApplication.getAppContext(), repository,
                            conversation, msg);
                }
            }

            if (ConfigurationUtilities.mTrace) {
                Log.d(TAG, "Message notification sent (mms): " + Long.toHexString(netMsgId));
            }
            notificationState.remove(netMsgId);
        }
    }

    // SendMessage calls this to mark the message as queued for sending.
    private void markMessageQueued(PreparedMessageData msgData, final Message message) {
        synchronized (eventStateSync) {
            long netMsgId = msgData.transportId;
            netMsgId >>>= 4;                    // Shift away status bits
            eventState.put(netMsgId, message);  // mark it as queued to send
        }
    }

    private void markNotificationQueued(final PreparedMessageData msgData, @NonNull final Message message, int notificationType) {
        synchronized (notificationStateSync) {
            long netMsgId = msgData.transportId;
            netMsgId >>>= 4;                    // Shift away status bits
            notificationState.put(netMsgId, new PendingNotification(message, notificationType));  // mark it as queued to send
            Log.d(TAG, "Message notification queued (mms): " + Long.toHexString(netMsgId) + " for " + message.getId());
        }
    }

    private void updateEventDeviceInfoState(@NonNull Event msg, long transportId, int state) {
        if (transportId == 0L)
            return;

        EventDeviceInfo[] infos = msg.getEventDeviceInfo();
        if (infos == null)
            return;

        for (EventDeviceInfo deviceInfo : infos) {
            if (deviceInfo == null) {
                continue;
            }
            if (deviceInfo.transportId == transportId) {
                deviceInfo.state = state;
                break;
            }
        }
    }

    private void updateEventDeviceInfoState(@NonNull Event msg, String devId, int state) {
        if (devId == null)
            return;

        EventDeviceInfo[] infos = msg.getEventDeviceInfo();
        if (infos == null)
            return;

        for (EventDeviceInfo deviceInfo : infos) {
            if (deviceInfo == null) {
                continue;
            }
            if (deviceInfo.deviceId.equals(devId)) {
                deviceInfo.state = state;
            }
        }
    }

    private void updateMessageState(@MessageStates.MessageState int messageState, @NonNull Message msg, boolean saveOnly) {
        final String recipient = Utilities.removeSipParts(MessageUtils.getConversationId(msg));
        final Conversation conversation = getConversations().findById(recipient);
        final EventRepository events = getConversations().historyOf(conversation);
        if (events == null) {
            return;
        }
        if (!saveOnly)
            msg.setState(messageState);
        events.save(msg);

        if (!saveOnly)
            MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(), recipient, false,
                UPDATE_ACTION_MESSAGE_STATE_CHANGE, msg.getId());
    }

    private void markPacketAsRead(final Conversation conv, final EventRepository events,
            final String deliveredPacketID, final long readReceiptTime, final String deviceId) {
        final Event event = events.findById(deliveredPacketID);
        if (event instanceof Message) {
            final Message message = (Message)event;
            int state = message.getState();

            /*
             * This function is called for incoming and outgoing messages, message state can be
             * RECEIVED or DELIVERED.
             *
             * Avoid updating state and read time for already read messages by checking whether
             * message is already in READ state
             */
            if (state != MessageStates.READ) {
                // decrement counters only for incoming messages which would have RECEIVED state
                if (state == MessageStates.RECEIVED) {
                    if (!(message instanceof CallMessage)) {
                        decrementUnreadMessages(message);
                    } else {
                        decrementUnreadCallMessages(message);
                    }
                }
                // if message is already marked for burning, do not update its state to a weaker one
                if (state != MessageStates.BURNED) {
                    state = MessageStates.READ;
                }
                message.setState(state);
                if (message.expires()) {
                    final long rrTime = readReceiptTime <= 0 ? System.currentTimeMillis() : readReceiptTime;
                    message.setExpirationTime(rrTime + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
//                message.setExpirationTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
                }
                updateEventDeviceInfoState(event, deviceId, state);
                events.save(message);

                MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(),
                        MessageUtils.getConversationId(message), false,
                        UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
            }
            else {
                Log.w(TAG, "markPacketAsRead " + deliveredPacketID + " already has state READ");
            }
        } else {
            Log.d(TAG, "event not found for read receipt " + deliveredPacketID);
            /*
             * Could not find event to update. Create a placeholder with received state to update
             * event when it arrives.
             */
            createOrUpdateStateEvent(event, events, conv.getPartner().getUserId(), deliveredPacketID, 0,
                    readReceiptTime, MessageStates.READ);
        }
    }

    private void markPacketAsDelivered(final Conversation conv, final EventRepository events,
            final String deliveredPacketID, final long deliveryTime, final String deviceId) {
        final Event event = events.findById(deliveredPacketID);
        if (event instanceof OutgoingMessage) {
            final OutgoingMessage message = (OutgoingMessage)event;
            final int state = message.getState();
            /* message can be marked as delivered when state report 200 is received */
            if (state == MessageStates.SENT || state == MessageStates.SENT_TO_SERVER
                    || state == DELIVERED
                    || state == MessageStates.SYNC) {
                message.setState(DELIVERED);
                message.setDeliveryTime(deliveryTime);
                updateEventDeviceInfoState(event, deviceId, MessageStates.DELIVERED);
                events.save(message);

                MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(),
                        message.getConversationID(), false, UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
            }
        } else {
            Log.d(TAG, "event not found for delivery receipt: " + deliveredPacketID);
            /*
             * Could not find event to update. Create a placeholder with received state to update
             * event when it arrives.
             */
            createOrUpdateStateEvent(event, events, conv.getPartner().getUserId(), deliveredPacketID,
                    deliveryTime, 0, MessageStates.DELIVERED);
        }
    }

    private void createOrUpdateStateEvent(@Nullable Event event, @NonNull EventRepository events,
            @NonNull final String conversationId, String deliveredPacketID,
            long deliveryTime, long readReceiptTime, @MessageStates.MessageState int newState) {
        MessageStateEvent stateEvent = null;
        int state = newState;
        if (event == null) {
            stateEvent = createMessageStateEvent(conversationId, deliveredPacketID);
        } else if (event instanceof MessageStateEvent) {
            stateEvent = (MessageStateEvent) event;
            state = stateEvent.getState();
            /*
             * avoid having already present state overwritten by a 'weaker' state
             *
             * Expression state = newState > state ? newState : state; could be used if there
             * would be no additions to state enum in the future (it's not possible to insert
             * new states in between existing ones).
             */
            if (newState == MessageStates.READ) {
                if (state != MessageStates.BURNED) {
                    state = newState;
                }
            }
            if (newState == MessageStates.DELIVERED) {
                if (!(state == MessageStates.BURNED || state == MessageStates.READ)) {
                    state = newState;
                }
            }
        }
        if (stateEvent != null) {
            stateEvent.setState(state);
            if (deliveryTime != 0) {
                stateEvent.setDeliveryTime(deliveryTime);
            }
            if (readReceiptTime != 0) {
                stateEvent.setReadReceiptTime(readReceiptTime);
            }
            events.save(stateEvent);
        }
    }

    private MessageStateEvent createMessageStateEvent(@NonNull final String conversationId, @NonNull final String deliveredPacketID) {
        MessageStateEvent stateEvent = new MessageStateEvent();
        stateEvent.setConversationID(conversationId);
        stateEvent.setId(deliveredPacketID);
        return stateEvent;
    }

//    private void markPacketAsFailed(final EventRepository events, final String deliveredPacketID,
//            final long deliveryTime, final String deviceId) {
//        final Event event = events.findById(deliveredPacketID);
//        if (event instanceof OutgoingMessage) {
//            final OutgoingMessage message = (OutgoingMessage)event;
//            message.setState(MessageStates.FAILED);
//            message.setDeliveryTime(deliveryTime);
//            updateEventDeviceInfoState(event, deviceId, MessageStates.FAILED);
//            events.save(message);
//
//            MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(), message.getConversationID(), false,
//                    UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
//        }
//    }

    private void createErrorEvent(final int errorCode, final String info) {
        final ErrorEvent event = MessageUtils.parseErrorMessage(new ErrorEvent(errorCode), info);
        final String sender = Utilities.removeSipParts(event.getSender());

        if (TextUtils.isEmpty(sender)) {
            Log.e(TAG, "Failed to determine sender for error [" + info + "]");
            return;
        }

        final Conversation conversation = getOrCreateConversation(sender);
        final EventRepository events = getConversations().historyOf(conversation);
        final long now = System.currentTimeMillis();

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
        event.setTime(now);
        events.save(event);

        conversation.setLastModified(now);
        getConversations().save(conversation);

        MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(), sender,
                true, UPDATE_ACTION_MESSAGE_SEND, event.getId());
    }

    private void burnPacket(final Conversation conv, final EventRepository events, final String burnedPacketId,
                            final boolean confirm) {
        final Event event = events.findById(burnedPacketId);

        if (event == null) {
            Log.w(TAG, "Burn notice requested for unknown event");
            MessageStateEvent stateEvent = new MessageStateEvent();
            stateEvent.setConversationID(conv.getPartner().getUserId());
            stateEvent.setId(burnedPacketId);
            stateEvent.setState(MessageStates.BURNED);
            events.save(stateEvent);
            return;
        }

        if (!(event instanceof Message)) {
            Log.w(TAG, "Burn notice requested for wrong event type");
            if (event instanceof MessageStateEvent) {
                MessageStateEvent stateEvent = (MessageStateEvent) event;
                stateEvent.setState(MessageStates.BURNED);
                events.save(stateEvent);
            }
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

        final Context ctx = SilentPhoneApplication.getAppContext();
        MessageUtils.notifyConversationUpdated(ctx, conversationPartner, false,
                UPDATE_ACTION_MESSAGE_BURNED, event.getId());

        if(((Message) event).hasAttachment()) {
            /** Remove a possible temporary attachment file (rare)
             *  At this point, all encrypted chunks have already been removed by {@link com.silentcircle.messaging.repository.DbRepository.DbEventRepository#remove(Event)}
            */
            Intent cleanupIntent = Action.PURGE_ATTACHMENTS.intent(ctx, SCloudCleanupService.class);
            cleanupIntent.putExtra("KEEP_STATUS", false);
            Extra.PARTNER.to(cleanupIntent, conversationPartner);
            Extra.ID.to(cleanupIntent, event.getId());
            ctx.startService(cleanupIntent);
        }
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
        sendCommandInBackground(obj, "bnc");

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

    private static long parseDate(final String dateTime) {
        long parsedTime;
        try {
            parsedTime = ISO8601.parse(dateTime).getTime();
        } catch( Throwable exception ) {
            // SPi may send time with milliseconds, retry parsing
            try {
                parsedTime = ISO8601_WITH_MILLIS.parse(dateTime).getTime();
            } catch (Throwable exception1) {
                return 0;
            }
        }
        final long timeWithSkew = System.currentTimeMillis() + TIME_SKEW_FUTURE; // allow a one minute skew in the future
        if (parsedTime > timeWithSkew) {
            return timeWithSkew;                // Use this time, we could also return 0 to force usage of current time
        }
        return parsedTime;
    }

    /**
     * Send a burn request for outgoing message to user's other devices.
     *
     * @param attributeJson The JSON object that already contains the normal burn command
     * @param msgBytes The message to attach to the attribute command data
     */
    private void createSendSyncBurnNotice(final byte[] msgBytes, final Message msg) {
        final JSONObject attributeJson = new JSONObject();
        try {
            attributeJson.put(MSG_SYNC_COMMAND, BURN);
            attributeJson.put(ORIGINAL_RECEIVER, MessageUtils.getConversationId(msg));
        } catch (JSONException ignore) {}

        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());

        SendCommandInBackground sndBackground = new SendCommandInBackground(msgBytes, null, attributeBytes, true);
        AsyncUtils.execute(sndBackground);

    }

    /**
     * Send a read receipt for incoming message to user's other devices.
     *
     * @param attributeJson The JSON object that already contains the normal burn command
     * @param msgBytes The message to attach to the attribute command data
     */
    private void createSendSyncReadReceipt(final byte[] msgBytes, final Message msg) {
        final JSONObject attributeJson = new JSONObject();
        try {
            attributeJson.put(MSG_SYNC_COMMAND, READ_RECEIPT);
            attributeJson.put(ORIGINAL_RECEIVER, MessageUtils.getConversationId(msg));
            attributeJson.put(READ_TIME, ISO8601.format(new Date()));
        } catch (JSONException ignore) {}

        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());

        SendCommandInBackground sndBackground = new SendCommandInBackground(msgBytes, null, attributeBytes, true);
        AsyncUtils.execute(sndBackground);

    }

    /**
     * Send an outgoing message to user's other devices.
     *
     * @param message The message descriptor data
     * @param msg  The outgoing message data structure
     * @return {@code true} if sync was successful
     */
    @WorkerThread
    private boolean createSendSyncOutgoing(final byte[] message, final Message msg, PreparedMessageData[] preparedMessageData) {
        final Conversation conversation = getOrCreateConversation(msg.getConversationID());
        String attributes = msg.getAttributes();
        JSONObject attributeJson;
        try {
            // Add special sync command
            attributeJson =  new JSONObject(TextUtils.isEmpty(attributes) ? "{}" : attributes);
            attributeJson.put(MSG_SYNC_COMMAND, ORIGINAL_MESSAGE);
            attributeJson.put(ORIGINAL_RECEIVER, msg.getConversationID());
            attributeJson.put("dpn", conversation.getPartner().getDisplayName());

            JSONArray infoArray = new JSONArray();
            for (PreparedMessageData msgD : preparedMessageData)
                infoArray.put(msgD.receiverInfo);
            attributeJson.put("rcvInfo", infoArray);

        } catch (JSONException ignore) {
            return false;
        }
        byte[] attributeBytes = IOUtils.encode(attributeJson.toString());

        SendCommandInBackground sndBackground = new SendCommandInBackground(message, msg.getAttachmentAsByteArray(), attributeBytes, true);
        AsyncUtils.execute(sndBackground);

        return true;
    }

    @WorkerThread
    private void processSyncMessage(final String messageData, final String msgId, final byte[] attachmentDescriptor,
                                   final byte[] messageAttributes, final String senderDeviceId) {

        if (messageAttributes == null || messageAttributes.length == 0) {
            return;
        }
        final String attributes = new String(messageAttributes);

        final String attachment = (attachmentDescriptor != null) ? new String(attachmentDescriptor) : null;
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got message attributes (sync): " + attributes);
        try {
            // Check for a command - if yes, process it, return true, message done.
            final JSONObject attributeJson = new JSONObject(attributes);
            final String command = attributeJson.optString(MSG_SYNC_COMMAND, null);
            if (TextUtils.isEmpty(command))
                return;
            final String displayName = attributeJson.optString("dpn", null);
            final String originalReceiver = attributeJson.getString(ORIGINAL_RECEIVER);  // The 'or' is a UUID
            JSONArray receiverDevInfo = attributeJson.optJSONArray("rcvInfo");
            if (receiverDevInfo == null) {
                receiverDevInfo = new JSONArray();
            }
            int length = receiverDevInfo.length();
            final PreparedMessageData[] preparedMessageData = new PreparedMessageData[length];
            for (int i = 0; i < length; i++) {
                preparedMessageData[i] = new PreparedMessageData(receiverDevInfo.optString(i), 0L);
            }
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
                            conversation.getPartner().setValidated(true);
                            conversations.save(conversation);
                        }
                        switch (command) {
                            case ORIGINAL_MESSAGE:
                                processSyncOutgoingMessage(conversation, messageData, msgId, attachment, attributes, preparedMessageData);
                                break;

                            case BURN:
                                processSyncOutgoingBurn(conversation, msgId);
                                break;

                            case READ_RECEIPT:
                                final String dateTime = attributeJson.optString(READ_TIME);
                                final long dt = parseDate(dateTime);
                                processSyncReadReceipt(conversation, msgId, dt, senderDeviceId);
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
                    case ORIGINAL_MESSAGE:
                        processSyncOutgoingMessage(conversation, messageData, msgId, attachment, attributes, preparedMessageData);
                        break;

                    case BURN:
                        processSyncOutgoingBurn(conversation, msgId);
                        break;

                    case READ_RECEIPT:
                        final String dateTime = attributeJson.getString(READ_TIME);
                        final long dt = parseDate(dateTime);
                        processSyncReadReceipt(conversation, msgId, dt, senderDeviceId);
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
                                           final String attributes,
                                            PreparedMessageData[] preparedMessageData) {

        ConversationRepository conversations = getConversations();
        EventRepository events = conversations.historyOf(conversation);

        int state = MessageStates.SYNC;
        long readReceiptTime = System.currentTimeMillis();
        long deliveryTime = System.currentTimeMillis();

        /* Check whether state report has already been received for message */
        Event stateEvent = events.findById(msgId);
        if (stateEvent instanceof MessageStateEvent) {
            state = ((MessageStateEvent) stateEvent).getState();
            deliveryTime = ((MessageStateEvent) stateEvent).getDeliveryTime();
            readReceiptTime = ((MessageStateEvent) stateEvent).getReadReceiptTime();
        }

        Message message = new OutgoingMessage(getUserName(), messageData);
        MessageUtils.parseAttributes(attributes, message);
        if(attachment != null) {
            message.setAttachment(attachment);
        }
        message.setState(state);

        /*
         * If state report has already been received, update relevant timestamps for message.
         */
        if ((state == MessageStates.DELIVERED || state == MessageStates.READ)
                && message.getDeliveryTime() == 0) {
            message.setDeliveryTime(deliveryTime);
        }
        if (state == MessageStates.READ) {
            if (message.expires()) {
                final long rrTime = readReceiptTime <= 0 ? System.currentTimeMillis() : readReceiptTime;
                message.setExpirationTime(rrTime + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
            }
        }
        message.setConversationID(conversation.getPartner().getUserId());
        message.setId(msgId);

        EventDeviceInfo[] deviceInfos = new EventDeviceInfo[preparedMessageData.length];

        int idx = 0;
        for (PreparedMessageData msgD : preparedMessageData) {
            deviceInfos[idx++] = createEventDeviceInfo(msgD);
        }
        message.setEventDeviceInfo(deviceInfos);

        events.save(message);
        conversation.setLastModified(System.currentTimeMillis());
        conversations.save(conversation);

        final Context ctx = SilentPhoneApplication.getAppContext();
        MessageUtils.notifyConversationUpdated(ctx, message.getConversationID(), true,
                UPDATE_ACTION_MESSAGE_SEND, message.getId());

        // Do an initial request to download the thumbnail
        if(attachment != null && !TextUtils.isEmpty(message.getAttachment()) && !AttachmentUtils.hasThumbnail(message)) {
            ctx.startService(SCloudService.getDownloadThumbnailIntent(message, conversation.getPartner().getUserId(), ctx));
        }
    }

    @WorkerThread
    private void processSyncOutgoingBurn(final Conversation conversation, final String msgId) {

        ConversationRepository conversations = getConversations();
        EventRepository events = conversations.historyOf(conversation);

        burnPacket(conversation, events, msgId, false);
    }

    @WorkerThread
    private void processSyncReadReceipt(final Conversation conversation, final String msgId, final long dt, final String senderDeviceId) {
        ConversationRepository conversations = getConversations();
        EventRepository events = conversations.historyOf(conversation);

        markPacketAsRead(conversation, events, msgId, dt, senderDeviceId);
        Notifications.updateMessageNotification(SilentPhoneApplication.getAppContext());
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
            errorMsg = registerZinaDevice(code);
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
        PreparedMessageData[] msgData;
        final boolean mToSiblings;

        SendCommandInBackground(byte[] msgDescriptor, byte[] attachmentDescriptor, byte[] attributeDescriptor, boolean toSiblings) {
            mMsgDescriptor = msgDescriptor;
            mAttachmentDescriptor = attachmentDescriptor;
            mAttributeDescriptor = attributeDescriptor;
            mToSiblings = toSiblings;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            int[] code = new int[1];
            if (!mToSiblings)
                msgData = prepareMessageNormal(mMsgDescriptor, mAttachmentDescriptor, mAttributeDescriptor, false, code);
            else
                msgData = prepareMessageSiblings(mMsgDescriptor, mAttachmentDescriptor, mAttributeDescriptor, false, code);

            if (msgData == null) {
                if (getErrorCode() == MessageErrorCodes.NO_DEVS_FOUND && mToSiblings) {
                    Log.w(TAG, "No sibling devices");
                }
                else {
                    Log.w(TAG, "Async command, no network ids '" + new String(mAttributeDescriptor) +
                            "', code: " + getErrorCode() + ", info: " + getErrorInfo());
                }
            }
            else if (msgData.length > 0) {
                long[] transportIds = new long[msgData.length];
                int idx = 0;
                for (PreparedMessageData msgD : msgData) {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Async command prepared and queued: " + Long.toHexString(msgD.transportId));
                    // NOTE: We could enhance Message class to contain an array of the PreparedMessageData to keep track of message status per device
                    markMessageQueued(msgD, null);
                    transportIds[idx++] = msgD.transportId;
                }
                doSendMessages(transportIds);
            }
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command send: " + (System.currentTimeMillis() - startTime));
        }
    }

    private static class ZinaCommandInBackground implements Runnable {

        private String mCommand;
        private byte[] mData;

        ZinaCommandInBackground(String... commands) {
            mCommand = commands[0];
            if (commands.length >= 1)
                mData = IOUtils.encode(commands[1]);
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            int[] code = new int[1];
            zinaCommand(mCommand, mData, code);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command '" + mCommand + "': " + (System.currentTimeMillis() - startTime));
            onPostRun();
        }

        public void onPostRun() {}
    }

    private static class ReSyncHandler extends Handler {
        private final WeakReference<ZinaMessaging> mTarget;

        ReSyncHandler(ZinaMessaging parent) {
            super(Looper.getMainLooper());
            mTarget = new WeakReference<>(parent);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            ZinaMessaging parent = mTarget.get();
            if (parent == null)
                return;

            if (msg.what == RE_SYNC_RATCHET) {
                String info = (String)msg.obj;
                ZinaCommandInBackground aib = new ZinaCommandInBackground("reSyncConversation", info);
                AsyncUtils.execute(aib);
                Log.w(TAG, "Execute a ratchet re-sync after decryption failure, number of attempts: " + ++mReSyncCounter);
            }
        }
    }
}
