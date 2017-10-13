/*
Copyright 2016-2017 Silent Circle, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#include <cryptcommon/ZrtpRandom.h>
#include <map>
#include "AppInterfaceImpl.h"

#include "../util/Utilities.h"
#include "../provisioning/Provisioning.h"
#include "../util/b64helper.h"
#include "MessageEnvelope.pb.h"
#include "../storage/MessageCapture.h"
#include "../ratchet/ratchet/ZinaRatchet.h"
#include "../ratchet/ZinaPreKeyConnector.h"
#include "JsonStrings.h"
#include "../dataRetention/ScDataRetention.h"

using namespace std;
using namespace zina;

shared_ptr<list<shared_ptr<PreparedMessageData> > >
AppInterfaceImpl::prepareMessage(const string& messageDescriptor,
                                 const string& attachmentDescriptor,
                                 const string& messageAttributes, bool normalMsg, int32_t* result)
{
    auto resultList = prepareMessageInternal(messageDescriptor, attachmentDescriptor, messageAttributes, false,
                                             normalMsg ? MSG_NORMAL : MSG_CMD, result);

    auto returnList = make_shared<list<shared_ptr<PreparedMessageData> > >();
    for (; !resultList->empty(); resultList->pop_front()) {
        auto msgData = shared_ptr<PreparedMessageData>(resultList->front().release());
        returnList->push_back(msgData);
    }
    return returnList;
}

shared_ptr<list<shared_ptr<PreparedMessageData> > >
AppInterfaceImpl::prepareMessageToSiblings(const string &messageDescriptor,
                                           const string &attachmentDescriptor,
                                           const string &messageAttributes, bool normalMsg, int32_t *result)
{
    auto resultList = prepareMessageInternal(messageDescriptor, attachmentDescriptor, messageAttributes, true,
                                             normalMsg ? MSG_NORMAL : MSG_CMD, result);

    auto returnList = make_shared<list<shared_ptr<PreparedMessageData> > >();
    for (; !resultList->empty(); resultList->pop_front()) {
        auto msgData = shared_ptr<PreparedMessageData>(resultList->front().release());
        returnList->push_back(msgData);
    }
    return returnList;
}

unique_ptr<list<unique_ptr<PreparedMessageData> > >
AppInterfaceImpl::prepareMessageNormal(const string &messageDescriptor,
                                       const string &attachmentDescriptor,
                                       const string &messageAttributes, bool normalMsg, int32_t *result)
{
    return prepareMessageInternal(messageDescriptor, attachmentDescriptor, messageAttributes, false,
                                  normalMsg ? MSG_NORMAL : MSG_CMD, result);
}

unique_ptr<list<unique_ptr<PreparedMessageData> > >
AppInterfaceImpl::prepareMessageSiblings(const string &messageDescriptor,
                                         const string &attachmentDescriptor,
                                         const string &messageAttributes, bool normalMsg, int32_t *result)
{
    return prepareMessageInternal(messageDescriptor, attachmentDescriptor, messageAttributes, true,
                                  normalMsg ? MSG_NORMAL : MSG_CMD, result);
}

static int32_t
getDevicesNewUser(string& recipient, string& authorization, list<pair<string, string> > &devices)
{

    int32_t result = Provisioning::getZinaDeviceIds(recipient, authorization, devices);

    if (result != SUCCESS) {
        return result;
    }

    if (devices.empty()) {
        return NO_DEVS_FOUND;
    }
    return SUCCESS;
}

static string
createIdDevInfo(const pair<string, string>& newDev)
{
    string newDevInfo(string("<NOT_YET_AVAILABLE>:"));
    newDevInfo.append(newDev.second).append(":").append(newDev.first).append(":0");
    return newDevInfo;
}

/**
 * @brief Create an id key and device info string as returned by @c getIdentityKeys().
 *
 * Because it's a new user we don't know its long-term identity key yet. Thus fill it
 * with a appropriate description.
 *
 * @param newDevList Device information from provisioning server for a new user
 * @return List of key id, device info string
 */
static shared_ptr<list<string> >
createIdDevInfo(list<pair<string, string> > &newDevList) {

    auto devInfoList = make_shared<list<string> >();

    for (auto &devInfo : newDevList) {
        devInfoList->push_back(createIdDevInfo(devInfo));
    }
    return devInfoList;
}

shared_ptr<list<string> >
AppInterfaceImpl::addSiblingDevices(shared_ptr<list<string> > idDevInfos)
{
    auto newSiblingDevices = make_shared<list<string> >();

    list<pair<string, string> > siblingDevices;
    int32_t errorCode = Provisioning::getZinaDeviceIds(ownUser_, authorization_, siblingDevices);

    // The provisioning server reported an error or both lists are empty: no new siblings known yet
    if (errorCode != SUCCESS || (idDevInfos->empty() && siblingDevices.empty()))
        return newSiblingDevices;

    if (idDevInfos->empty()) {
        // Add all devices known to server to id key list
        for (auto siblingDevice : siblingDevices) {
            // Don't add own device to unknown siblings
            if (siblingDevice.first == scClientDevId_)
                continue;
            newSiblingDevices->push_back(createIdDevInfo(siblingDevice));
        }
        return newSiblingDevices;
    }

    // This is a nested loop. We could optimize the inner loop an remove
    // found devices. However, we are talking about max 5 entries in each
    // list, thus this optimization is not really necessary.
    for (auto siblingDevice : siblingDevices) {
        // Don't add own device to unknown siblings
        if (siblingDevice.first == scClientDevId_)
            continue;

        bool found = false;
        for (auto &idDevInfo : *idDevInfos) {
            auto idParts = Utilities::splitString(idDevInfo, ":");

            // idDevInfo has the format:
            //       0           1         2        3
            // 'identityKey:deviceName:deviceId:verifyState', deviceName may be empty
            if (siblingDevice.first == idParts->at(2)) {
                found = true;
                break;
            }
        }
        if (!found) {
            newSiblingDevices->push_back(createIdDevInfo(siblingDevice));
        }
    }
    return newSiblingDevices;
}

static mutex preparedMessagesLock;
static map<uint64_t, unique_ptr<CmdQueueInfo> > preparedMessages;

#ifdef SC_ENABLE_DR_SEND
static mutex retainInfoLock;
static map<uint64_t, uint32_t > retainInfoMap;
#endif

void AppInterfaceImpl::queuePreparedMessage(unique_ptr<CmdQueueInfo> msgInfo)
{
    unique_lock<mutex> listLock(preparedMessagesLock);
    preparedMessages.insert(pair<uint64_t, unique_ptr<CmdQueueInfo> >(msgInfo->queueInfo_transportMsgId, move(msgInfo)));
}

// Check if we have a retain info entry for this id.
//
// If not just return 0.
//
// If we have an info then check if we should remove the info data from the
// map. Either because the processed count goes to zero or if the remove
// flag is set. in this case the function returns the local retain flags
// RETAIN_LOCAL_DATA and RETAIN_LOCAL_META
//
// The function 'sendMessageExisting' calls this function with 'remove' true
// once it sent a message and checks for data retention.
//
// Other functions call this with 'remove' false. This only decrements the
// processed message counter and only if it reaches 0 the function removes
// the entry. This way we can handle error conditions when sending messages
// to a device of if the application deletes a subset on prepared message data.
//
//
static uint32_t getAndMaintainRetainInfo(uint64_t id, bool remove)
{
#ifdef SC_ENABLE_DR_SEND
    LOGGER(DEBUGGING, __func__, " --> ", id);

    unique_lock<mutex> listLock(retainInfoLock);
    auto it = retainInfoMap.find(id);

    // If no info with this id - don't perform data retention
    if (it == retainInfoMap.end()) {
        return 0;
    }
    const uint32_t retainedInfo = it->second;
    const uint32_t retainFlags = retainedInfo & 0xff;
    uint32_t processedMsgs =  retainedInfo >> 8;

    if (remove || --processedMsgs == 0) {
        retainInfoMap.erase(it);
    }
    else {
        it ->second = processedMsgs << 8 | retainFlags;
    }
    LOGGER(DEBUGGING, __func__, " <-- ", retainFlags);
    return retainFlags;
#else
    return 0;
#endif
}

unique_ptr<list<unique_ptr<PreparedMessageData> > >
AppInterfaceImpl::prepareMessageInternal(const string& messageDescriptor,
                                         const string& attachmentDescriptor,
                                         const string& messageAttributes,
                                         bool toSibling, uint32_t messageType, int32_t* result,
                                         const string& grpRecipient, const string &groupId)
{

    string recipient;
    string msgId;
    string message;

    LOGGER(DEBUGGING, __func__, " -->");

    unique_ptr<list<unique_ptr<PreparedMessageData> > > messageData(new list<unique_ptr<PreparedMessageData> >);

    if (result != nullptr) {
        *result = SUCCESS;
        errorCode_ = SUCCESS;
    }
    int32_t returnCode = parseMsgDescriptor(messageDescriptor, &recipient, &msgId, &message);
    if (returnCode < 0) {
        if (result != nullptr) {
            *result = returnCode;
        }
        errorCode_ = returnCode;
        errorInfo_ = "Wrong JSON data to send message";
        LOGGER(ERROR, __func__, " Wrong JSON data to send message, error code: ", returnCode);
        return messageData;
    }
    if (!grpRecipient.empty()) {
        recipient = grpRecipient;
    }

    if (recipient == ownUser_ && !toSibling) {
        LOGGER(WARNING, "Sending message to own recipient but toSibling not set, forcing toSibling.");
        toSibling = true;
    }
//    uint8_t localRetentionFlags = 0;
    string msgAttributes(messageAttributes);
    if (toSibling) {
        recipient = ownUser_;
    }
#ifdef SC_ENABLE_DR_SEND
    else if (!isCommand(messageType, messageAttributes)) {      // No data retention for commands yet
        auto newAttributes = make_shared<string>();
        if ((errorCode_ = checkDataRetentionSend(recipient, msgAttributes, newAttributes, &localRetentionFlags)) != OK) {
            return messageData;
        }
        msgAttributes = *newAttributes;
    }
#endif // SC_ENABLE_DR_SEND

    // When sending to sibling devices getIdentityKeys(...) returns an empty list if the user
    // has no sibling devices.
    auto idKeys = getIdentityKeys(recipient);

    // No known sibling devices, just return empty data and SUCCESS. This is a valid case
    if (toSibling && idKeys->empty()) {
        LOGGER(DEBUGGING, __func__, " <-- no sibling device");
        if (result != nullptr) {
            *result = SUCCESS;
        }
        errorCode_ = SUCCESS;
        return messageData;
    }

    bool newUser = false;

    // If we got no identity keys/no device information we need to handle this as
    // a new user, thus get some data from provisioning server. However, do this
    // only if not sending data to a sibling device
    if (!toSibling && idKeys->empty()) {
        list<pair<string, string> > devicesNewUser;
        int32_t errorCode = getDevicesNewUser(recipient, authorization_, devicesNewUser);
        if (errorCode != SUCCESS) {
            if (result != nullptr) {
                *result = errorCode;
            }
            errorCode_ = errorCode;
            errorInfo_ = "Cannot get device info for new user";
            return messageData;     // Still an empty list
        }
        idKeys = createIdDevInfo(devicesNewUser);
        newUser = true;
    }

    // If idKeys is empty:
    // - happens if a new user has no ZINA devices registered
    if (idKeys->empty()) {
        if (result != nullptr) {
            *result = NO_DEVS_FOUND;
        }
        errorCode_ = NO_DEVS_FOUND;
        errorInfo_ = "No device available for this user.";
        return messageData;
    }
    uint64_t transportMsgId;
    ZrtpRandom::getRandomData(reinterpret_cast<uint8_t*>(&transportMsgId), 8);

    // The transport id is structured: bits 0..3 are type bits, bits 4..7 is a counter, bits 8..63 random data
    transportMsgId &= ~0xff;

    uint64_t counter = 0;

    for (const auto& idDevInfo: *idKeys) {
        // idDevInfo has the format:
        //       0           1         2        3
        // 'identityKey:deviceName:deviceId:verifyState', deviceName may be empty
        auto info = Utilities::splitString(idDevInfo, ":");

        // don't send to myself
        string& deviceId = info->at(2);
        if (toSibling && deviceId == scClientDevId_) {
            continue;
        }

        // Setup and queue the prepared message info data
        auto msgInfo = new CmdQueueInfo;
        msgInfo->command = SendMessage;

        // Specific handling to group messages: each device may have its own update change set, depending on its ACK status.
        if (messageType >= GROUP_MSG_NORMAL && !groupId.empty()) {
            string newAttributes;
            returnCode = createChangeSetDevice(groupId, deviceId, msgAttributes, &newAttributes);
            if (returnCode < 0) {
                if (result != nullptr) {
                    *result = returnCode;
                }
                errorCode_ = returnCode;
                errorInfo_ = "Cannot create and store group update records for a device";
                LOGGER(ERROR, __func__, " Cannot create and store group update records, error code: ", returnCode);
                return messageData;
            }
            msgInfo->queueInfo_attributes = newAttributes.empty() ? msgAttributes : newAttributes;
        }
        else {
            msgInfo->queueInfo_attributes = msgAttributes;
        }

        msgInfo->queueInfo_recipient = recipient;
        msgInfo->queueInfo_deviceName = info->at(1);
        msgInfo->queueInfo_deviceId = deviceId;
        msgInfo->queueInfo_msgId = msgId;
        msgInfo->queueInfo_message = message;
        msgInfo->queueInfo_attachment = attachmentDescriptor;
        msgInfo->queueInfo_transportMsgId = transportMsgId | (counter << 4) | messageType;
        msgInfo->queueInfo_toSibling = toSibling;
        msgInfo->queueInfo_newUserDevice = newUser;
        msgInfo->queueInfo_callbackAction = NoAction;
        counter++;

        // Prepare the return data structure and fill into list
        auto resultData = unique_ptr<PreparedMessageData>(new PreparedMessageData);
        resultData->transportId = msgInfo->queueInfo_transportMsgId;
        resultData->receiverInfo = idDevInfo;
        messageData->push_back(move(resultData));

        queuePreparedMessage(unique_ptr<CmdQueueInfo>(msgInfo));
    }

#ifdef SC_ENABLE_DR_SEND
    if (localRetentionFlags != 0) {
        uint8_t numPreparedMsgs = static_cast<uint8_t>(counter);
        // retainInfo stores: number of prepared msg data and the local retention flags
        // Store this info in the retainInfo map, indexed by the raw transportId
        uint32_t retainInfo = numPreparedMsgs << 8 | localRetentionFlags;
        unique_lock<mutex> listLock(retainInfoLock);
        retainInfoMap.insert(pair<uint64_t, uint32_t>(transportMsgId, retainInfo));
    }
#endif
    idKeys->clear();
    LOGGER(DEBUGGING, __func__, " <-- ", messageData->size());
    return messageData;
}

string  AppInterfaceImpl::createSendErrorJson(const CmdQueueInfo& info, int32_t errorCode)
{
    cJSON* root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "version", 1);

    cJSON* details;
    cJSON_AddItemToObject(root, "details", details = cJSON_CreateObject());

    cJSON_AddStringToObject(details, "name", info.queueInfo_recipient.c_str());
    cJSON_AddStringToObject(details, "scClientDevId", info.queueInfo_deviceId.c_str());
    cJSON_AddStringToObject(details, "msgId", info.queueInfo_msgId.c_str());   // May help to diagnose the issue
    cJSON_AddNumberToObject(details, "errorCode", errorCode);

    char *out = cJSON_PrintUnformatted(root);
    string retVal(out);
    cJSON_Delete(root); free(out);

    return retVal;
}

int32_t AppInterfaceImpl::doSendMessages(shared_ptr<vector<uint64_t> > transportIds)
{
    LOGGER(DEBUGGING, __func__, " -->");

    size_t numOfIds = transportIds->size();

    if (numOfIds == 0)
        return 0;

    list<unique_ptr<CmdQueueInfo> > messagesToProcess;
    int32_t counter = 0;

    unique_lock<mutex> prepareLock(preparedMessagesLock);
    for (size_t sz = 0; sz < numOfIds; sz++) {
        uint64_t id = transportIds->at(sz);
        auto it = preparedMessages.find(id);
        if (it != preparedMessages.end()) {
            // Found a prepared message
            messagesToProcess.push_back(move(it->second));
            preparedMessages.erase(it);
            counter++;
        }
    }
    prepareLock.unlock();

    addMsgInfosToRunQueue(messagesToProcess);

    LOGGER(DEBUGGING, __func__, " <--, ", counter);
    return counter;
}

int32_t AppInterfaceImpl::removePreparedMessages(shared_ptr<vector<uint64_t> > transportIds)
{
    LOGGER(DEBUGGING, __func__, " -->");

    size_t numOfIds = transportIds->size();
    int32_t counter = 0;

    unique_lock<mutex> prepareLock(preparedMessagesLock);
    for (size_t sz = 0; sz < numOfIds; sz++) {
        uint64_t id = transportIds->at(sz);
        auto it = preparedMessages.find(id);
        if (it != preparedMessages.end()) {
            // Found a prepared message
            preparedMessages.erase(it);
            getAndMaintainRetainInfo(id & 0xff, false); // retain info map uses the base id only without counter
            counter++;
        }
    }
    prepareLock.unlock();

    LOGGER(DEBUGGING, __func__, " <--, ", counter);
    return counter;
}

int32_t
AppInterfaceImpl::sendMessageExisting(const CmdQueueInfo &sendInfo, unique_ptr<ZinaConversation> zinaConversation)
{
    LOGGER(DEBUGGING, __func__, " -->");

    errorCode_ = SUCCESS;

    // Don't send this to my own device
    if (sendInfo.queueInfo_toSibling && sendInfo.queueInfo_deviceId == scClientDevId_) {
        return SUCCESS;
    }

    string supplements = createSupplementString(sendInfo.queueInfo_attachment, sendInfo.queueInfo_attributes);

    if (zinaConversation == nullptr) {
        zinaConversation = ZinaConversation::loadConversation(ownUser_, sendInfo.queueInfo_recipient, sendInfo.queueInfo_deviceId, *store_);
        if (!zinaConversation->isValid()) {
            LOGGER(ERROR, "ZINA conversation is not valid. Owner: ", ownUser_, ", recipient: ", sendInfo.queueInfo_recipient,
                   ", recipientDeviceId: ", sendInfo.queueInfo_deviceId);
            errorCode_ = zinaConversation->getErrorCode();
            errorInfo_ = sendInfo.queueInfo_deviceId;
            getAndMaintainRetainInfo(sendInfo.queueInfo_transportMsgId  & ~0xff, false);
            Utilities::wipeString(const_cast<string&>(sendInfo.queueInfo_attachment));
            Utilities::wipeString(const_cast<string&>(sendInfo.queueInfo_attributes));
            return errorCode_;
        }
    }

    cJSON* convJson = nullptr;
    LOGGER_BEGIN(INFO)
        convJson = zinaConversation->prepareForCapture(nullptr, true);
    LOGGER_END

    // Encrypt the user's message and the supplementary data if necessary
    MessageEnvelope envelope;
    int32_t result = ZinaRatchet::encrypt(*zinaConversation, sendInfo.queueInfo_message, envelope, supplements, *store_);

    Utilities::wipeString(const_cast<string&>(sendInfo.queueInfo_message));
    Utilities::wipeString(supplements);

    LOGGER_BEGIN(INFO)
        convJson = zinaConversation->prepareForCapture(convJson, false);

        char* out = cJSON_PrintUnformatted(convJson);
        string convState(out);
        cJSON_Delete(convJson); free(out);

        MessageCapture::captureSendMessage(sendInfo.queueInfo_recipient, sendInfo.queueInfo_msgId, sendInfo.queueInfo_deviceId, convState,
                                           sendInfo.queueInfo_attributes, !sendInfo.queueInfo_attachment.empty(), *store_);
    LOGGER_END

    Utilities::wipeString(const_cast<string&>(sendInfo.queueInfo_attachment));
    Utilities::wipeString(const_cast<string&>(sendInfo.queueInfo_attributes));

    // If encrypt does not return encrypted data then report an error, code was set by the encrypt function
    if (result != SUCCESS) {
        LOGGER(ERROR, "Encryption failed, no wire message created, device id: ", sendInfo.queueInfo_deviceId);
        LOGGER(INFO, __func__, " <-- Encryption failed.");
        getAndMaintainRetainInfo(sendInfo.queueInfo_transportMsgId  & ~0xff, false);
        return result;
    }
    result = zinaConversation->storeConversation(*store_);
    if (result != SUCCESS) {
        LOGGER(ERROR, "Storing ratchet data failed after encryption, device id: ", sendInfo.queueInfo_deviceId);
        LOGGER(INFO, __func__, " <-- Encryption failed.");
        return result;
    }
    /*
     * Create the message envelope:
     {
         "name":           <string>         # sender's name
         "scClientDevId":  <string>         # sender's long device id
         "supplement":     <string>         # supplementary data, encrypted, B64
         "message":        <string>         # message, encrypted, B64
     }
    */

    envelope.set_name(ownUser_);
    envelope.set_scclientdevid(scClientDevId_);
    envelope.set_msgid(sendInfo.queueInfo_msgId);
    envelope.set_msgtype(static_cast<uint32_t>(sendInfo.queueInfo_transportMsgId & MSG_TYPE_MASK));

    uint8_t binDevId[20];
    size_t res = hex2bin(sendInfo.queueInfo_deviceId.c_str(), binDevId);
    if (res == 0)
        envelope.set_recvdevidbin(binDevId, 4);

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

#ifdef SC_ENABLE_DR_SEND
    uint32_t retainInfo = getAndMaintainRetainInfo(sendInfo.queueInfo_transportMsgId  & ~0xff, true);
    if (retainInfo != 0) {
        doSendDataRetention(retainInfo, sendInfo);
    }
#endif
    transport_->sendAxoMessage(sendInfo, serialized);
    LOGGER(DEBUGGING, __func__, " <--");

    return SUCCESS;
}

int32_t
AppInterfaceImpl::sendMessageNewUser(const CmdQueueInfo &sendInfo)
{
    LOGGER(DEBUGGING, __func__, " -->");

    errorCode_ = SUCCESS;

    // Don't send this to my own device
    if (sendInfo.queueInfo_toSibling && sendInfo.queueInfo_deviceId == scClientDevId_) {
        return SUCCESS;
    }

    // Check if conversation/user really not known. On new users the 'reScan' triggered by
    // SIP NOTIFY may have already created the conversation. In this case skip further
    // processing and just handle it as an existing user.
    auto zinaConversation = ZinaConversation::loadConversation(ownUser_, sendInfo.queueInfo_recipient, sendInfo.queueInfo_deviceId, *store_);
    if (zinaConversation->isValid() && !zinaConversation->getRK().empty()) {
        return sendMessageExisting(sendInfo, move(zinaConversation));
    }

    pair<PublicKeyUnique, PublicKeyUnique> preIdKeys;
    int32_t preKeyId = Provisioning::getPreKeyBundle(sendInfo.queueInfo_recipient, sendInfo.queueInfo_deviceId, authorization_, &preIdKeys);
    if (preKeyId == 0) {
        LOGGER(ERROR, "No pre-key bundle available for recipient ", sendInfo.queueInfo_recipient, ", device id: ", sendInfo.queueInfo_deviceId);
        LOGGER(INFO, __func__, " <-- No pre-key bundle");
        getAndMaintainRetainInfo(sendInfo.queueInfo_transportMsgId  & ~0xff, false);
        return NO_PRE_KEY_FOUND;
    }

    int32_t buildResult = ZinaPreKeyConnector::setupConversationAlice(ownUser_, sendInfo.queueInfo_recipient,
                                                                      sendInfo.queueInfo_deviceId, preKeyId, preIdKeys, *store_);

    // This is always a security issue: return immediately, don't process and send a message
    if (buildResult != SUCCESS) {
        errorCode_ = buildResult;
        errorInfo_ = sendInfo.queueInfo_deviceId;
        getAndMaintainRetainInfo(sendInfo.queueInfo_transportMsgId  & ~0xff, false);
        return errorCode_;
    }
    // Read the conversation again and store the device name of the new user's device. Now the user/device
    // is known and we can handle it as an existing user.
    zinaConversation = ZinaConversation::loadConversation(ownUser_, sendInfo.queueInfo_recipient, sendInfo.queueInfo_deviceId, *store_);
    if (!zinaConversation->isValid()) {
        errorCode_ = zinaConversation->getErrorCode();
        errorInfo_ = sendInfo.queueInfo_deviceId;
        getAndMaintainRetainInfo(sendInfo.queueInfo_transportMsgId  & ~0xff, false);
        return errorCode_;
    }
    zinaConversation->setDeviceName(sendInfo.queueInfo_deviceName);
    LOGGER(DEBUGGING, __func__, " <--");

    return sendMessageExisting(sendInfo, move(zinaConversation));
}

string AppInterfaceImpl::createMessageDescriptor(const string& recipient, const string& msgId, const string& msg)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();

    cJSON_AddStringToObject(root, MSG_VERSION, "1");
    cJSON_AddStringToObject(root, MSG_RECIPIENT, recipient.c_str());
    cJSON_AddStringToObject(root, MSG_ID, msgId.c_str());
    cJSON_AddStringToObject(root, MSG_DEVICE_ID, scClientDevId_.c_str());
    cJSON_AddStringToObject(root, MSG_MESSAGE, msg.empty() ? "" : msg.c_str());

    CharUnique out(cJSON_PrintUnformatted(root));
    string result(out.get());
    return result;
}

#ifdef SC_ENABLE_DR_SEND
int32_t
AppInterfaceImpl::checkDataRetentionSend(const string &recipient, const string &msgAttributes,
                                         shared_ptr<string> newMsgAttributes, uint8_t *localRetentionFlags) {
    LOGGER(DEBUGGING, __func__, " -->");

    cJSON *root = !msgAttributes.empty() ? cJSON_Parse(msgAttributes.c_str()) : cJSON_CreateObject();
    shared_ptr<cJSON> sharedRoot(root, cJSON_deleter);

    string command = Utilities::getJsonString(root, MSG_COMMAND, "");

    // Don't block sending of error commands, send them without any modifications
    if (!command.empty() && command.compare(0, 3, "err") == 0) {
        newMsgAttributes->assign(msgAttributes);
        LOGGER(INFO, __func__, " <-- Sending error command: ", command);
        return OK;
    }
    // The user blocks local data retention, thus vetos setting of retention policy of the organization
    if ((drBldr_ && drLrmp_) || (drBlmr_ && drLrmm_)) {
        LOGGER(INFO, __func__, " <-- Reject data retention - 1.");
        return REJECT_DATA_RETENTION;
    }

    NameLookup *nameLookup = NameLookup::getInstance();
    auto remoteUserInfo = nameLookup->getUserInfo(recipient, authorization_, false);
    if (!remoteUserInfo) {
        LOGGER(INFO, __func__, " <-- Data missing.");
        return DATA_MISSING;        // No info for remote user??
    }

    // User blocks remote data retention and remote party retains data - reject sending
    if ((drBrdr_ && remoteUserInfo->drRrmp) || (drBrmr_ && remoteUserInfo->drRrmm)) {
        LOGGER(INFO, __func__, " <-- Reject data retention - 2.");
        return REJECT_DATA_RETENTION;
    }

//    LOGGER(WARNING, " ++++ DR send flags, local ", drLrmp_, ", ", drLrmm_, ", remote: ", remoteUserInfo->drRrmp, ", ", remoteUserInfo->drRrmm);
    // No local or remote data retention policy is active, just return OK and unchanged attributes
    if (!drLrmp_ && !drLrmm_ && !remoteUserInfo->drRrmp && !remoteUserInfo->drRrmm) {
        newMsgAttributes->assign(msgAttributes);
        LOGGER(INFO, __func__, " <-- No DR policy active.");
        return OK;
    }

    // At this point at least the local or the remote party has an active DR policy. Thus we
    // need to modify the message attribute and prepare to call DR functions when actually
    // sending the message.
    if (drLrmp_) {
        cJSON_AddBoolToObject(root, ROP, true);
        *localRetentionFlags |= RETAIN_LOCAL_DATA;
    }
    if (drLrmm_) {
        cJSON_AddBoolToObject(root, ROM, true);
        *localRetentionFlags |= RETAIN_LOCAL_META;
    }
    if (remoteUserInfo->drRrmm) {
        cJSON_AddBoolToObject(root, RAM, true);
    }
    if (remoteUserInfo->drRrmp) {
        cJSON_AddBoolToObject(root, RAP, true);
    }
    char *out = cJSON_PrintUnformatted(root);
    newMsgAttributes->assign(out);
    free(out);

    LOGGER(DEBUGGING, __func__, " <-- ", *newMsgAttributes);
    return OK;
}

int32_t AppInterfaceImpl::doSendDataRetention(uint32_t retainInfo, shared_ptr<CmdQueueInfo> sendInfo)
{
    LOGGER(DEBUGGING, __func__, " -->");
    uuid_t uu = {0};
    uuid_parse(sendInfo->queueInfo_msgId.c_str(), uu);
    time_t composeTime = uuid_time(uu, NULL);

    time_t currentTime = time(NULL);

    cJSON *attr = !sendInfo->queueInfo_attributes.empty() ? cJSON_Parse(sendInfo->queueInfo_attributes.c_str()) : cJSON_CreateObject();
    shared_ptr<cJSON> sharedAttr(attr, cJSON_deleter);
    DrLocationData location(attr, (retainInfo & RETAIN_LOCAL_DATA) == RETAIN_LOCAL_DATA);

    cJSON *attachmentAttr = !sendInfo->queueInfo_attachment.empty() ? cJSON_Parse(sendInfo->queueInfo_attachment.c_str()) : cJSON_CreateObject();
    shared_ptr<cJSON> sharedAttachmentAttr(attachmentAttr, cJSON_deleter);
    DrAttachmentData attachment(attachmentAttr, (retainInfo & RETAIN_LOCAL_DATA) == RETAIN_LOCAL_DATA);

    if ((retainInfo & RETAIN_LOCAL_DATA) == RETAIN_LOCAL_DATA) {
        ScDataRetention::sendMessageMetadata("", "sent", location, attachment, sendInfo->queueInfo_recipient, composeTime, currentTime);
        ScDataRetention::sendMessageData("", "sent", sendInfo->queueInfo_recipient, composeTime, currentTime,
                                         sendInfo->queueInfo_message);
    }
    else if ((retainInfo & RETAIN_LOCAL_META) == RETAIN_LOCAL_META) {
        ScDataRetention::sendMessageMetadata("", "sent", location, attachment, sendInfo->queueInfo_recipient, composeTime, currentTime);
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}
#endif // SC_ENABLE_DR_SEND

