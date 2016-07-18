#include "AppInterfaceImpl.h"
#include "MessageEnvelope.pb.h"

#include "../axolotl/Constants.h"
#include "../axolotl/AxoPreKeyConnector.h"
#include "../axolotl/ratchet/AxoRatchet.h"

#include "../keymanagment/PreKeys.h"
#include "../util/cJSON.h"
#include "../util/b64helper.h"
#include "../util/UUID.h"
#include "../provisioning/Provisioning.h"
#include "../provisioning/ScProvisioning.h"
#include "../logging/AxoLogging.h"

#include <zrtp/crypto/sha256.h>

#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCDFAInspection"
static mutex convLock;

using namespace axolotl;

void Log(const char* format, ...);

AppInterfaceImpl::AppInterfaceImpl(const string& ownUser, const string& authorization, const string& scClientDevId,
                                   RECV_FUNC receiveCallback, STATE_FUNC stateReportCallback, NOTIFY_FUNC notifyCallback):
                                   AppInterface(receiveCallback, stateReportCallback, notifyCallback), tempBuffer_(NULL), tempBufferSize_(0),
                                   ownUser_(ownUser), authorization_(authorization), scClientDevId_(scClientDevId),
                                   errorCode_(0), transport_(NULL), flags_(0), ownChecked_(false)
{
    store_ = SQLiteStoreConv::getStore();
}

AppInterfaceImpl::~AppInterfaceImpl()
{
    LOGGER(INFO, __func__, " -->");
    tempBufferSize_ = 0; delete tempBuffer_; tempBuffer_ = NULL;
    delete transport_; transport_ = NULL;
    LOGGER(INFO, __func__, " <--");
}

static void createSupplementString(const string& attachementDesc, const string& messageAttrib, string* supplement)
{
    LOGGER(INFO, __func__, " -->");
    if (!attachementDesc.empty() || !messageAttrib.empty()) {
        cJSON* msgSupplement = cJSON_CreateObject();

        if (!attachementDesc.empty()) {
            LOGGER(DEBUGGING, "Adding an attachment descriptor supplement");
            cJSON_AddStringToObject(msgSupplement, "a", attachementDesc.c_str());
        }

        if (!messageAttrib.empty()) {
            LOGGER(DEBUGGING, "Adding an message attribute supplement");
            cJSON_AddStringToObject(msgSupplement, "m", messageAttrib.c_str());
        }
        char *out = cJSON_PrintUnformatted(msgSupplement);

        supplement->append(out);
        cJSON_Delete(msgSupplement); free(out);
    }
    LOGGER(INFO, __func__, " <--");
}

/*
 {
    "version":    <int32_t>,            # Version of JSON send message descriptor, 1 for the first implementation
    "recipient":  <string>,             # for SC this is either the user's name of the user's DID
    "deviceId" :  <int32_t>,            # optional, if we support multi-device, defaults to 1 if missing
                                        # set to 0 to send the message to each registered device 
                                        # of the user
    "message":    <string>              # the actual plain text message, UTF-8 encoded (Java programmers beware!)
 }
 */
vector<int64_t>* AppInterfaceImpl::sendMessage(const string& messageDescriptor, const string& attachementDescriptor, const string& messageAttributes)
{

    string recipient;
    string msgId;
    string message;
    int32_t parseResult = parseMsgDescriptor(messageDescriptor, &recipient, &msgId, &message);

    LOGGER(INFO, __func__, " -->");
    if (parseResult < 0) {
        errorCode_ = parseResult;
        LOGGER(ERROR, __func__, " Wrong JSON data to send message, error code: ", parseResult);
        return NULL;
    }
    return sendMessageInternal(recipient, msgId, message, attachementDescriptor, messageAttributes);
}

vector<int64_t>* AppInterfaceImpl::sendMessageToSiblings(const string& messageDescriptor, const string& attachementDescriptor, 
                                                         const string& messageAttributes)
{
    string recipient;
    string msgId;
    string message;
    int32_t parseResult = parseMsgDescriptor(messageDescriptor, &recipient, &msgId, &message);

    LOGGER(INFO, __func__, " -->");
    if (parseResult < 0) {
        errorCode_ = parseResult;
        LOGGER(ERROR, __func__, " Wrong JSON data to send message, error code: ", parseResult);
        return NULL;
    }
    return sendMessageInternal(ownUser_, msgId, message, attachementDescriptor, messageAttributes);
}

static string receiveErrorJson(const string& sender, const string& senderScClientDevId, const string& msgId, 
                               const char* msgHex, int32_t errorCode, const string& sentToId)
{
    cJSON* root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "version", 1);

    cJSON* details;
    cJSON_AddItemToObject(root, "details", details = cJSON_CreateObject());

    cJSON_AddStringToObject(details, "name", sender.c_str());
    cJSON_AddStringToObject(details, "scClientDevId", senderScClientDevId.c_str());
    cJSON_AddStringToObject(details, "otherInfo", msgHex);            // App may use this to retry after fixing the problem
    cJSON_AddStringToObject(details, "msgId", msgId.c_str());         // May help to diganose the issue
    cJSON_AddNumberToObject(details, "errorCode", errorCode);
    cJSON_AddStringToObject(details, "sentToId", sentToId.c_str());

    char *out = cJSON_PrintUnformatted(root);
    string retVal(out);
    cJSON_Delete(root); free(out);

    return retVal;
}

// Take a message envelope (see sendMessage above), parse it, and process the embedded data. Then
// forward the data to the UI layer.
static int32_t duplicates = 0;

int32_t AppInterfaceImpl::receiveMessage(const string& messageEnvelope)
{
    return receiveMessage(messageEnvelope, Empty, Empty);
}


int32_t AppInterfaceImpl::receiveMessage(const string& messageEnvelope, const string& uid, const string& displayName)
{
    LOGGER(INFO, __func__, " -->");

    uint8_t hash[SHA256_DIGEST_LENGTH];
    sha256((uint8_t*)messageEnvelope.data(), (uint32_t)messageEnvelope.size(), hash);

    string msgHash;
    msgHash.assign((const char*)hash, SHA256_DIGEST_LENGTH);

    unique_lock<mutex> lck(convLock);
    int32_t sqlResult = store_->hasMsgHash(msgHash);

    // If we found a duplicate, log and silently ignore it.
    if (sqlResult == SQLITE_ROW) {
        LOGGER(DEBUGGING, __func__, " Duplicate messages detected so far: ", ++duplicates);
        return OK;
    }
    store_->insertMsgHash(msgHash);
    lck.unlock();

    // Cleanup old message hashes
    time_t timestamp = time(0) - MK_STORE_TIME;
    store_->deleteMsgHashes(timestamp);

    if (messageEnvelope.size() > tempBufferSize_) {
        delete tempBuffer_;
        tempBuffer_ = new char[messageEnvelope.size()];
        tempBufferSize_ = messageEnvelope.size();
    }
    size_t binLength = b64Decode(messageEnvelope.data(), messageEnvelope.size(), (uint8_t*)tempBuffer_, tempBufferSize_);
    string envelopeBin((const char*)tempBuffer_, binLength);

    MessageEnvelope envelope;
    envelope.ParseFromString(envelopeBin);

    // backward compatibility or in case the message Transport does not support
    // UID. Then fallback to data in the message envelope.
    const string& sender = uid.empty() ? envelope.name() : uid;

    const string& senderScClientDevId = envelope.scclientdevid();
    const string& supplements = envelope.has_supplement() ? envelope.supplement() : Empty;
    const string& message = envelope.message();
    const string& msgId = envelope.msgid();

    string sentToId;
    if (envelope.has_recvdevidbin())
        sentToId = envelope.recvdevidbin();

    bool wrongDeviceId = false; 
    if (!sentToId.empty()) {
        uint8_t binDevId[20];
        hex2bin(scClientDevId_.c_str(), binDevId);

        wrongDeviceId = memcmp((void*)sentToId.data(), binDevId, sentToId.size()) != 0;

        char receiverId[16] = {0};
        size_t len;
        bin2hex((const uint8_t*)sentToId.data(), sentToId.size(), receiverId, &len);
        if (wrongDeviceId) {
            LOGGER(ERROR, __func__, "Message is for device id: ", receiverId, ", my device id: ", scClientDevId_);
        }
    }
    uuid_t uu = {0};
    uuid_parse(msgId.c_str(), uu);
    time_t msgTime = uuid_time(uu, NULL);
    time_t currentTime = time(NULL);
    time_t timeDiff = currentTime - msgTime;

    bool oldMessage = (timeDiff > 0 && timeDiff >= MK_STORE_TIME);

//     Log("Message send time: %d, current receiver time: %d, difference: %d, oldMessge: %s",
//         msgTime, currentTime, timeDiff, oldMessage? "TRUE": "FALSE");

    pair<string, string> idHashes;
    bool hasIdHashes = false;
    if (envelope.has_recvidhash() && envelope.has_senderidhash()) {
        hasIdHashes = true;
        const string& recvIdHash = envelope.recvidhash();
        const string& senderIdHash = envelope.senderidhash();
        idHashes.first = recvIdHash;
        idHashes.second = senderIdHash;
    }
    lck.lock();
    AxoConversation* axoConv = AxoConversation::loadConversation(ownUser_, sender, senderScClientDevId);

    // This is a not yet seen user. Set up a basic Conversation structure. Decrypt uses it and fills
    // in the other data based on the received message.
    if (axoConv == NULL) {
        axoConv = new AxoConversation(ownUser_, sender, senderScClientDevId);
    }
    shared_ptr<string> supplementsPlain = make_shared<string>();
    shared_ptr<const string> messagePlain;

    messagePlain = AxoRatchet::decrypt(axoConv, message, supplements, supplementsPlain, hasIdHashes ? &idHashes : NULL);
    errorCode_ = axoConv->getErrorCode();
    delete axoConv;
    lck.unlock();

    //    Log("After decrypt: %s", messagePlain ? messagePlain->c_str() : "NULL");
    if (!messagePlain) {
        char b2hexBuffer[1004] = {0};

        if (oldMessage)
            errorCode_ = OLD_MESSAGE;
        if (wrongDeviceId)
            errorCode_ = WRONG_RECV_DEV_ID;
        size_t msgLen = min(message.size(), (size_t)500);
        size_t outLen;
        bin2hex((const uint8_t*)message.data(), msgLen, b2hexBuffer, &outLen);
        messageStateReport(0, errorCode_, receiveErrorJson(sender, senderScClientDevId, msgId, b2hexBuffer, errorCode_, sentToId));
        LOGGER(ERROR, __func__ , " Decryption failed: ", errorCode_, ", sender: ", sender, ", device: ", senderScClientDevId );
        return errorCode_;
    }

    /*
     * Message descriptor for received message:
     {
         "version":    <int32_t>,            # Version of JSON send message descriptor, 1 for the first implementation
         "sender":     <string>,             # for SC this is either the user's name or the user's DID
         "scClientDevId" : <string>,         # the sender's long device id
         "message":    <string>              # the actual plain text message, UTF-8 encoded (Java programmers beware!)
    }
    */
    cJSON* root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "version", 1);
    cJSON_AddStringToObject(root, "sender", sender.c_str());        // sender is the UUID string

    // backward compatibility or in case the message Transport does not support
    // alias handling. Then fallback to data in the message envelope.
    cJSON_AddStringToObject(root, "display_name", displayName.empty() ? envelope.name().c_str() : displayName.c_str());
    cJSON_AddStringToObject(root, "scClientDevId", senderScClientDevId.c_str());
    cJSON_AddStringToObject(root, "msgId", msgId.c_str());
    cJSON_AddStringToObject(root, "message", messagePlain->c_str());
    messagePlain.reset();

    char *out = cJSON_PrintUnformatted(root);
    string msgDescriptor(out);

    cJSON_Delete(root); free(out);

    string attachmentDescr;
    string attributesDescr;
    if (!supplementsPlain->empty()) {
        cJSON* jsSupplement = cJSON_Parse(supplementsPlain->c_str());

        cJSON* cjTemp = cJSON_GetObjectItem(jsSupplement, "a");
        char* jsString = (cjTemp != NULL) ? cjTemp->valuestring : NULL;
        if (jsString != NULL) {
            attachmentDescr = jsString;
        }

        cjTemp = cJSON_GetObjectItem(jsSupplement, "m");
        jsString = (cjTemp != NULL) ? cjTemp->valuestring : NULL;
        if (jsString != NULL) {
            attributesDescr = jsString;
        }
        cJSON_Delete(jsSupplement);
    }
    receiveCallback_(msgDescriptor, attachmentDescr, attributesDescr);
    LOGGER(INFO, __func__, " <--");
    return OK;
}

/*
JSON state information block:
{   
    "version":    <int32_t>,            # Version of the JSON known users structure,
                                        # 1 for the first implementation
    "code":       <int32_t>,            # success, error codes (code values to be 
                                        # defined)
    "details": {                        # optional, in case of error code it includes
                                        # detail information
        "name":      <string>,          # optional, name of sender/recipient of message,
                                        # if known
        "scClientDevId" : <string>,     # the same string as used to register the 
                                        # device (v1/me/device/{device_id}/)
        "otherInfo": <string>           # optional, additional info, e.g. SIP server 
                                        # messages
    }
}
*/
void AppInterfaceImpl::messageStateReport(int64_t messageIdentfier, int32_t statusCode, const string& stateInformation)
{
    LOGGER(INFO, __func__, " -->");
    stateReportCallback_(messageIdentfier, statusCode, stateInformation);
    LOGGER(INFO, __func__, " <--");
}

string* AppInterfaceImpl::getKnownUsers()
{
    int32_t sqlCode;

    LOGGER(INFO, __func__, " -->");
    if (!store_->isReady()) {
        LOGGER(ERROR, __func__, " Axolotl conversation DB not ready.");
        return NULL;
    }

    shared_ptr<list<string> > names = store_->getKnownConversations(ownUser_, &sqlCode);

    if (SQL_FAIL(sqlCode) || !names) {
        LOGGER(INFO, __func__, " No known Axolotl conversations.");
        return NULL;
    }
    size_t size = names->size();

    cJSON *root,*nameArray;
    root=cJSON_CreateObject();
    cJSON_AddItemToObject(root, "version", cJSON_CreateNumber(1));
    cJSON_AddItemToObject(root, "users", nameArray = cJSON_CreateArray());

    for (int32_t i = 0; i < size; i++) {
        string name = names->front();
        cJSON_AddItemToArray(nameArray, cJSON_CreateString(name.c_str()));
        names->pop_front();
    }
    char *out = cJSON_PrintUnformatted(root);
    string* retVal = new string(out);
    cJSON_Delete(root); free(out);

    LOGGER(INFO, __func__, " <--");
    return retVal;
}

/*
 * JSON data for a registration request:
{
    "version" :        <int32_t>,        # Version of JSON registration, 1 for the first implementation
    "identity_key" :    <string>,         # public part encoded base64 data 
    "prekeys" : [{
        "id" :     <int32_t>,         # The key id of the signed pre key
        "key" :       <string>,          # public part encoded base64 data
    },
    ....
    {
        "id" :     <int32_t>,         # The key id of the signed pre key
        "key" :       <string>,          # public part encoded base64 data
    }]
}
 */
int32_t AppInterfaceImpl::registerAxolotlDevice(string* result)
{
    cJSON *root;
    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5

    LOGGER(INFO, __func__, " -->");

    root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "version", 1);
//    cJSON_AddStringToObject(root, "scClientDevId", scClientDevId_.c_str());

    AxoConversation* ownConv = AxoConversation::loadLocalConversation(ownUser_);
    if (ownConv == NULL) {
        cJSON_Delete(root);
        LOGGER(ERROR, __func__, " No own conversation in database.");
        return NO_OWN_ID;
    }
    const DhKeyPair* myIdPair = ownConv->getDHIs();
    if (myIdPair == NULL) {
        cJSON_Delete(root);
        delete ownConv;
        LOGGER(ERROR, __func__, " Own conversation not correctly initialized.");
        return NO_OWN_ID;
    }

    string data = myIdPair->getPublicKey().serialize();

    delete ownConv;

    b64Encode((const uint8_t*)data.data(), data.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(root, "identity_key", b64Buffer);

    cJSON* jsonPkrArray;
    cJSON_AddItemToObject(root, "prekeys", jsonPkrArray = cJSON_CreateArray());

    list<pair<int32_t, const DhKeyPair* > >* preList = PreKeys::generatePreKeys(store_);

    int32_t size = static_cast<int32_t>(preList->size());

    for (int32_t i = 0; i < size; i++) {
        pair< int32_t, const DhKeyPair* >pkPair = preList->front();
        preList->pop_front();

        cJSON* pkrObject;
        cJSON_AddItemToArray(jsonPkrArray, pkrObject = cJSON_CreateObject());
        cJSON_AddNumberToObject(pkrObject, "id", pkPair.first);

        // Get pre-key's public key data, serialized
        const DhKeyPair* ecPair = pkPair.second;
        const string keyData = ecPair->getPublicKey().serialize();

        b64Encode((const uint8_t*) keyData.data(), keyData.size(), b64Buffer, MAX_KEY_BYTES_ENCODED * 2);
        cJSON_AddStringToObject(pkrObject, "key", b64Buffer);
        delete ecPair;
    }
    delete preList;

    char *out = cJSON_Print(root);
    string registerRequest(out);
    cJSON_Delete(root); free(out);

    int32_t code = Provisioning::registerAxoDevice(registerRequest, authorization_, scClientDevId_, result);

    LOGGER(INFO, __func__, " <-- ", code);
    return code;
}

int32_t AppInterfaceImpl::removeAxolotlDevice(string& devId, string* result)
{
    LOGGER(INFO, __func__, " <-->");
    return ScProvisioning::removeAxoDevice(devId, authorization_, result);
}

int32_t AppInterfaceImpl::newPreKeys(int32_t number)
{
    LOGGER(INFO, __func__, " -->");
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    string result;
    return ScProvisioning::newPreKeys(store, scClientDevId_, authorization_, number, &result);
}

int32_t AppInterfaceImpl::getNumPreKeys() const
{
    LOGGER(INFO, __func__, " <-->");
    return Provisioning::getNumPreKeys(scClientDevId_, authorization_);
}

// Get known Axolotl device from provisioning server, check if we have a new one
// and if yes send a "ping" message to the new devices to create an Axolotl conversation
// for the new devices.

// This is the ping command the code sends to new devices to create an Axolotl setup
static string ping("{\"cmd\":\"ping\"}");

void AppInterfaceImpl::rescanUserDevices(string& userName)
{
    LOGGER(INFO, __func__, " -->");
    list<pair<string, string> >* devices = Provisioning::getAxoDeviceIds(userName, authorization_);
    if (devices == NULL || devices->empty()) {
        delete devices;
        return;
    }

    // Get known devices from DB, compare with devices from provisioning server
    // and remove old devices in DB, i.e. devices not longer known to provisioning server
    //
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();

    shared_ptr<list<string> > devicesDb = store_->getLongDeviceIds(userName, ownUser_);

    if (devicesDb) {
        while (!devicesDb->empty()) {
            string devIdDb = devicesDb->front();
            devicesDb->pop_front();
            bool found = false;

            for (list<pair<string, string> >::iterator devIterator = devices->begin();
                 devIterator != devices->end(); ++devIterator) {
                string devId = (*devIterator).first;
                if (devIdDb == devId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                store->deleteConversation(userName, devIdDb, ownUser_);
                LOGGER(DEBUGGING, "Remove device from database: ", devIdDb);
            }
        }
    }

    // Prepare and send this to the new learned device:
    // - an Empty message
    // - a message command attribute with a ping command
    // For each Ping message the code generates a new UUID
    string supplements;
    createSupplementString(Empty, ping, &supplements);

    // Prepare the messages for all known new devices of this user
    vector<pair<string, string> >* msgPairs = new vector<pair<string, string> >;

    unique_lock<mutex> lck(convLock);
    while (!devices->empty()) {
        string deviceId = devices->front().first;
        string deviceName = devices->front().second;
        devices->pop_front();

        // If we already have a conversation for this device skip further processing
        // after storing a user defined device name. The use may change a device's name
        // using the Web interface of the provisioning server
        if (store->hasConversation(userName, deviceId, ownUser_)) {
            AxoConversation* conv = AxoConversation::loadConversation(ownUser_, userName, deviceId);
            if (conv != NULL) {
                const string& convDevName = conv->getDeviceName();
                if (!deviceName.empty()) {
                    conv->setDeviceName(deviceName);
                    conv->storeConversation();
                }
                delete conv;
            }
            continue;
        }
        uuid_t pingUuid = {0};
        uuid_string_t uuidString = {0};

        uuid_generate_time(pingUuid);
        uuid_unparse(pingUuid, uuidString);
        string msgId(uuidString);

        LOGGER(DEBUGGING, "Send Ping to new found device: ", deviceId);
        int32_t result = createPreKeyMsg(userName, deviceId, deviceName, Empty, supplements, msgId, msgPairs);
        if (result == 0)   // no pre-key bundle available for name/device-id combination
            continue;

        // This is always a security issue: return immediately, don't process and send a message
        if (result < 0) {
            delete msgPairs;
            delete devices;
            return;
        }
    }
    lck.unlock();
    delete devices;

    if (msgPairs->empty()) {
        delete msgPairs;
        return;
    }
    vector<int64_t>* returnMsgIds = transport_->sendAxoMessage(userName, msgPairs);
    LOGGER(DEBUGGING, "Found new devices: ", returnMsgIds->size());

    delete msgPairs;
    delete returnMsgIds;
    LOGGER(INFO, __func__, " <--");
    return;
}

void AppInterfaceImpl::setHttpHelper(HTTP_FUNC httpHelper)
{
    ScProvisioning::setHttpHelper(httpHelper);
}

// ***** Private functions 
// *******************************

vector<int64_t>* AppInterfaceImpl::sendMessageInternal(const string& recipient, const string& msgId, const string& message,
                                                       const string& attachementDescriptor, const string& messageAttributes)
{
    LOGGER(INFO, __func__, " -->");

    errorCode_ = OK;

    bool toSibling = recipient == ownUser_;

    shared_ptr<list<string> > devices = store_->getLongDeviceIds(recipient, ownUser_);
    size_t numDevices = devices->size();

    if (numDevices == 0) {
        vector<pair<string, string> >* msgPairs = sendMessagePreKeys(recipient, msgId, message, attachementDescriptor, messageAttributes);

        // Either no device registered for recipient or some error occurred.
        if (msgPairs == NULL) {
            LOGGER(DEBUGGING, "Cannot send initial pre-key message to: ", recipient, ", code: ", errorCode_);
            LOGGER(INFO, __func__, " <--");
            return NULL;
        }
        unique_lock<mutex> lck(convLock);
        vector<int64_t>* returnMsgIds = transport_->sendAxoMessage(recipient, msgPairs);
        lck.unlock();
        LOGGER(DEBUGGING, "Sent initial pre-key messages to # devices: ", returnMsgIds->size());
        delete msgPairs;

        LOGGER(INFO, __func__, " <-- Initial pre-key message sent.");
        return returnMsgIds;
    }

    string supplements;
    createSupplementString(attachementDescriptor, messageAttributes, &supplements);

    // Prepare the messages for all known device of this user
    vector<pair<string, string> >* msgPairs = new vector<pair<string, string> >;

    unique_lock<mutex> lck(convLock);
    while (!devices->empty()) {
        string recipientDeviceId = devices->front();
        devices->pop_front();

        // Don't send this to sender device, even when sending to my sibbling devices
        if (toSibling && recipientDeviceId == scClientDevId_) {
            continue;
        }

        AxoConversation* axoConv = AxoConversation::loadConversation(ownUser_, recipient, recipientDeviceId);
        if (axoConv == NULL) {
            LOGGER(DEBUGGING, "Axolotl Conversation is NULL. Owner: ", ownUser_, ", recipient: ", recipient, ", recipientDeviceId: ",
                   recipientDeviceId);
            continue;
        }

        shared_ptr<string> supplementsEncrypted = make_shared<string>();

        // Encrypt the user's message and the supplementary data if necessary
        pair<string, string> idHashes;
        shared_ptr<const string> wireMessage = AxoRatchet::encrypt(*axoConv, message, supplements, supplementsEncrypted, &idHashes);
        axoConv->storeConversation();
        delete axoConv;
        if (!wireMessage)
            continue;
        bool hasIdHashes = !idHashes.first.empty() && !idHashes.second.empty();
        /*
         * Create the message envelope:
         {
             "name":           <string>         # sender's name
             "scClientDevId":  <string>         # sender's long device id
             "supplement":     <string>         # suplementary data, encrypted, B64
             "message":        <string>         # message, encrypted, B64
         }
        */

        MessageEnvelope envelope;
        envelope.set_name(ownUser_);
        envelope.set_scclientdevid(scClientDevId_);
        envelope.set_msgid(msgId);
        if (!supplementsEncrypted->empty())
            envelope.set_supplement(*supplementsEncrypted);
        envelope.set_message(*wireMessage);
        if (hasIdHashes) {
            envelope.set_recvidhash(idHashes.first.data(), 4);
            envelope.set_senderidhash(idHashes.second.data(), 4);
        }
        wireMessage.reset();

        uint8_t binDevId[20];
        size_t res = hex2bin(recipientDeviceId.c_str(), binDevId);
        if (res != 0)
            envelope.set_recvdevidbin(binDevId, 4);
//        envelope.set_recvdeviceid(recipientDeviceId);

        string serialized = envelope.SerializeAsString();

        // We need to have them in b64 encoding, check if buffer is large enough. Allocate twice
        // the size of binary data, this is big enough to hold B64 plus paddling and terminator
        if (serialized.size() * 2 > tempBufferSize_) {
            delete tempBuffer_;
            tempBuffer_ = new char[serialized.size()*2];
            tempBufferSize_ = serialized.size()*2;
        }
        size_t b64Len = b64Encode((const uint8_t*)serialized.data(), serialized.size(), tempBuffer_, tempBufferSize_);

        // replace the binary data with B64 representation
        serialized.assign(tempBuffer_, b64Len);

        pair<string, string> msgPair(recipientDeviceId, serialized);
        msgPairs->push_back(msgPair);

        supplementsEncrypted->clear();
    }

    vector<int64_t>* returnMsgIds = NULL;
    if (!msgPairs->empty()) {
        returnMsgIds = transport_->sendAxoMessage(recipient, msgPairs);
        LOGGER(DEBUGGING, "Sent messages to # devices: ", returnMsgIds->size());
    }
    lck.unlock();
    delete msgPairs;
    LOGGER(INFO, __func__, " <--");

    return returnMsgIds;
}

vector<pair<string, string> >* AppInterfaceImpl::sendMessagePreKeys(const string& recipient, const string& msgId, const string& message,
                                                                    const string& attachementDescriptor, const string& messageAttributes)
{
    LOGGER(INFO, __func__, " -->");

    string supplements;
    createSupplementString(attachementDescriptor, messageAttributes, &supplements);

    bool toSibling = recipient == ownUser_;

    list<pair<string, string> >* devices = NULL;
    int32_t errorCode = 0;
    if (!toSibling || !ownChecked_) {
        devices = Provisioning::getAxoDeviceIds(recipient, authorization_, &errorCode);
    }
    if (devices == NULL) {
        char tmpBuff[20];
        snprintf(tmpBuff, 10, "%d", errorCode);
        string errorString(tmpBuff);

        errorCode_ = NETWORK_ERROR;
        errorInfo_ = errorString;
        LOGGER(INFO, __func__, " <-- Network error: ", errorCode);
        return NULL;
    }

    if (devices->empty()) {
        errorCode_ = NO_DEVS_FOUND;
        errorInfo_ = recipient;
        delete devices;
        LOGGER(DEBUGGING, "No device registered for recipient: ", recipient);
        LOGGER(INFO, __func__, " <-- No device.");
        return NULL;
    }

    // Prepare the messages for all known devices of this user
    vector<pair<string, string> >* msgPairs = new vector<pair<string, string> >;

    unique_lock<mutex> lck(convLock);
    while (!devices->empty()) {
        string recipientDeviceId = devices->front().first;
        string recipientDeviceName = devices->front().second;
        devices->pop_front();

        // Don't send this to sender device, even when sending to my sibling devices
        if (toSibling && recipientDeviceId == scClientDevId_) {
            continue;
        }

        int32_t result = createPreKeyMsg(recipient, recipientDeviceId, recipientDeviceName, message, supplements, msgId, msgPairs);
        if (result == 0) {  // no pre-key bundle available for name/device-id combination
            LOGGER(DEBUGGING, "No pre-key bundle available for recipient ", recipient, ", device id: ", recipientDeviceId);
            continue;
        }

        // This is always a security issue: return immediately, don't process and send a message
        if (result < 0) {
            delete msgPairs;
            delete devices;
            errorCode_ = result;
            errorInfo_ = recipientDeviceId;
            LOGGER(ERROR, "Failed to create pre-key message, code ", result, ", recipient: ", recipient,
                   ", device id: ", recipientDeviceId);
            LOGGER(INFO, __func__, " <-- No pre-key message.");
            return NULL;
        }
    }
    lck.unlock();
    delete devices;

    if (msgPairs->empty()) {
        delete msgPairs;
        if (!toSibling) {
            errorCode_ = NO_PRE_KEY_FOUND;
            errorInfo_ = recipient;
        }
        else {
            ownChecked_ = true;
        }
        LOGGER(DEBUGGING, "No pre-key message sent to: ", recipient);
        LOGGER(INFO, __func__, " <-- No pre-key message sent.");
        return NULL;
    }
    LOGGER(INFO, __func__, " <--");
    return msgPairs;
}


int32_t AppInterfaceImpl::parseMsgDescriptor(const string& messageDescriptor, string* recipient, string* msgId, string* message)
{
    LOGGER(INFO, __func__, " -->");
    cJSON* cjTemp;
    char* jsString;

    cJSON* root = cJSON_Parse(messageDescriptor.c_str());
    if (root == NULL) {
        errorInfo_ = "root";
        goto cleanup;
    }
    cjTemp = cJSON_GetObjectItem(root, "recipient");
    jsString = (cjTemp != NULL) ? cjTemp->valuestring : NULL;
    if (jsString == NULL) {
        errorInfo_ = "recipient";
        goto cleanup;
    }
    recipient->assign(jsString);

    // Get the message id
    cjTemp = cJSON_GetObjectItem(root, "msgId");
    jsString = (cjTemp != NULL) ? cjTemp->valuestring : NULL;
    if (jsString == NULL) {
        errorInfo_ = "msgId";
        goto cleanup;
    }
    msgId->assign(jsString);

    // Get the message
    cjTemp = cJSON_GetObjectItem(root, "message");
    jsString = (cjTemp != NULL) ? cjTemp->valuestring : NULL;
    if (jsString == NULL) {
        errorInfo_ = "message";
        goto cleanup;
    }
    message->assign(jsString);
    cJSON_Delete(root);    // Done with JSON root for message data
//    LOGGER(INFO, __func__, " <--");
    return OK;

cleanup:
    cJSON_Delete(root);    // Done with JSON root for message data
    LOGGER(INFO, __func__, " <-- with error: ", errorInfo_);
    return JS_FIELD_MISSING;
}


int32_t AppInterfaceImpl::createPreKeyMsg(const string& recipient,  const string& recipientDeviceId, const string& recipientDeviceName,
                                          const string& message, const string& supplements,
                                          const string& msgId, vector<pair<string, string> >* msgPairs)
{
    LOGGER(INFO, __func__, " -->");

    pair<const DhPublicKey*, const DhPublicKey*> preIdKeys;
    int32_t preKeyId = Provisioning::getPreKeyBundle(recipient, recipientDeviceId, authorization_, &preIdKeys);
    if (preKeyId == 0) {
        LOGGER(DEBUGGING, "No pre-key bundle available for recipient ", recipient, ", device id: ", recipientDeviceId);
        LOGGER(INFO, __func__, " <-- No pre-key bundle");
        return 0;
    }

    int32_t buildResult = AxoPreKeyConnector::setupConversationAlice(ownUser_, recipient, recipientDeviceId, preKeyId, preIdKeys);

    // This is always a security issue: return immediately, don't process and send a message
    if (buildResult < 0) {
        errorCode_ = buildResult;
        errorInfo_ = recipientDeviceId;
        return buildResult;
    }
    AxoConversation* axoConv = AxoConversation::loadConversation(ownUser_, recipient, recipientDeviceId);
    axoConv->setDeviceName(recipientDeviceName);

    shared_ptr<string> supplementsEncrypted = make_shared<string>();

    // Encrypt the user's message and the supplementary data if necessary
    pair<string, string> idHashes;
    shared_ptr<const string> wireMessage = AxoRatchet::encrypt(*axoConv, message, supplements, supplementsEncrypted, &idHashes);
    axoConv->storeConversation();
    delete axoConv;

    if (!wireMessage) {
        LOGGER(WARNING, "Encryption failed, no wire message created.");
        LOGGER(INFO, __func__, " <-- Encryption failed.");
        return 0;
    }
    bool hasIdHashes = !idHashes.first.empty() && !idHashes.second.empty();
    /*
     * Create the message envelope:
     {
         "name":           <string>         # sender's name
         "scClientDevId":  <string>         # sender's long device id
         "supplement":     <string>         # suplementary data, encrypted
         "message":        <string>         # message, encrypted
      }
      */
    MessageEnvelope envelope;
    envelope.set_name(ownUser_);
    envelope.set_scclientdevid(scClientDevId_);
    envelope.set_msgid(msgId);
    if (!supplementsEncrypted->empty())
        envelope.set_supplement(*supplementsEncrypted);
    envelope.set_message(*wireMessage);
    if (hasIdHashes) {
        envelope.set_recvidhash(idHashes.first.data(), 4);
        envelope.set_senderidhash(idHashes.second.data(), 4);
    }

    uint8_t binDevId[20];
    size_t res = hex2bin(recipientDeviceId.c_str(), binDevId);
    if (res != 0)
        envelope.set_recvdevidbin(binDevId, 4);
//    envelope.set_recvdeviceid(recipientDeviceId);
    wireMessage.reset();

    string serialized = envelope.SerializeAsString();

    // We need to have them in b64 encoding, check if buffer is large enough. Allocate twice
    // the size of binary data, this is big enough to hold B64 plus padding and terminator
    if (serialized.size() * 2 > tempBufferSize_) {
        delete tempBuffer_;
        tempBuffer_ = new char[serialized.size()*2];
        tempBufferSize_ = serialized.size()*2;
    }
    size_t b64Len = b64Encode((const uint8_t*)serialized.data(), serialized.size(), tempBuffer_, tempBufferSize_);

    // replace the binary data with B64 representation
    serialized.assign(tempBuffer_, b64Len);

    pair<string, string> msgPair(recipientDeviceId, serialized);
    msgPairs->push_back(msgPair);

    LOGGER(INFO, __func__, " <--");
    return OK;
}

string AppInterfaceImpl::getOwnIdentityKey() const
{
    LOGGER(INFO, __func__, " -->");

    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5
    AxoConversation* axoConv = AxoConversation::loadLocalConversation(ownUser_);
    if (axoConv == NULL) {
        LOGGER(ERROR, "No own conversation, ignore.")
        LOGGER(INFO, __func__, " <-- No own conversation.");
        return Empty;
    }

    const DhKeyPair* keyPair = axoConv->getDHIs();
    const DhPublicKey& pubKey = keyPair->getPublicKey();

    b64Encode(pubKey.getPublicKeyPointer(), pubKey.getSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);

    string idKey((const char*)b64Buffer);
    if (!axoConv->getDeviceName().empty()) {
        idKey.append(":").append(axoConv->getDeviceName());
    }
    delete axoConv;
    LOGGER(INFO, __func__, " <--");
    return idKey;
}

list<string>* AppInterfaceImpl::getIdentityKeys(string& user) const
{
    LOGGER(INFO, __func__, " -->");

    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5
    list<string>* idKeys = new list<string>;

    shared_ptr<list<string> > devices = store_->getLongDeviceIds(user, ownUser_);

    while (!devices->empty()) {
        string recipientDeviceId = devices->front();
        devices->pop_front();
        AxoConversation* axoConv = AxoConversation::loadConversation(ownUser_, user, recipientDeviceId);
        const DhPublicKey* idKey = axoConv->getDHIr();

        b64Encode(idKey->getPublicKeyPointer(), idKey->getSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);

        string id((const char*)b64Buffer);
        id.append(":");
        if (!axoConv->getDeviceName().empty()) {
            id.append(axoConv->getDeviceName());
        }
        id.append(":").append(recipientDeviceId);
        snprintf(b64Buffer, 5, ":%d", axoConv->getZrtpVerifyState());
        b64Buffer[4] = '\0';          // make sure it's terminated
        id.append(b64Buffer);

        idKeys->push_back(id);
        delete axoConv;
    }
    LOGGER(INFO, __func__, " <--");
    return idKeys;
}
#pragma clang diagnostic pop