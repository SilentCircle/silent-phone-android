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

// Functions to handle received messages.
//
// Created by werner on 29.08.16.
//

#include "AppInterfaceImpl.h"
#include "MessageEnvelope.pb.h"
#include "../ratchet/ratchet/ZinaRatchet.h"
#include "../storage/MessageCapture.h"
#include "../util/b64helper.h"
#include "../util/Utilities.h"
#include "JsonStrings.h"
#include "../dataRetention/ScDataRetention.h"

#include <zrtp/crypto/sha256.h>

using namespace std;
using namespace zina;

static string receiveErrorJson(const string& sender, const string& senderScClientDevId, const string& msgId,
                               const char* other, int32_t errorCode, const string& sentToId, int32_t sqlCode,
                               int32_t msgType, const string &groupId = Empty)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();
    cJSON_AddNumberToObject(root, "version", 1);

    cJSON* details;
    cJSON_AddItemToObject(root, "details", details = cJSON_CreateObject());

    cJSON_AddStringToObject(details, "name", sender.c_str());
    cJSON_AddStringToObject(details, MSG_DEVICE_ID, senderScClientDevId.c_str());
    cJSON_AddStringToObject(details, "otherInfo", other);
    cJSON_AddStringToObject(details, MSG_ID, msgId.c_str());         // May help to diagnose the issue
    cJSON_AddNumberToObject(details, "errorCode", errorCode);
    cJSON_AddStringToObject(details, "sentToId", sentToId.c_str());
    cJSON_AddNumberToObject(root, MSG_TYPE, msgType);
    if (!groupId.empty()) {
        cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    }
    if (errorCode == DATABASE_ERROR)
        cJSON_AddNumberToObject(details, "sqlErrorCode", sqlCode);

    CharUnique out(cJSON_PrintUnformatted(root));
    string retVal(out.get());

    return retVal;
}

static string receiveErrorDescriptor(const string& messageDescriptor, int32_t result, const string &groupId = Empty)
{
    JsonUnique sharedRoot(cJSON_Parse(messageDescriptor.c_str()));
    cJSON* root = sharedRoot.get();

    string sender(Utilities::getJsonString(root, MSG_SENDER, ""));
    string deviceId(Utilities::getJsonString(root, MSG_DEVICE_ID, ""));
    string msgId(Utilities::getJsonString(root, MSG_ID, ""));

    return receiveErrorJson(sender, deviceId, msgId, "Error processing plain text message", result, "", 0, -1, groupId);
}

bool AppInterfaceImpl::isCommand(int32_t msgType, const string& attributes)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (msgType == GROUP_MSG_CMD || msgType == MSG_CMD)
        return true;

    if (attributes.empty())
        return false;

    shared_ptr<cJSON> attributesJson(cJSON_Parse(attributes.c_str()), cJSON_deleter);
    cJSON* attributesRoot = attributesJson.get();

    if (attributesRoot == nullptr)
        return false;

    string possibleCmd = Utilities::getJsonString(attributesRoot, MSG_COMMAND, "");
    if (!possibleCmd.empty())
        return true;

    possibleCmd = Utilities::getJsonString(attributesRoot, MSG_SYNC_COMMAND, "");
    if (!possibleCmd.empty())
        return true;

    possibleCmd = Utilities::getJsonString(attributesRoot, GROUP_COMMAND, "");
    return !possibleCmd.empty();
}

bool AppInterfaceImpl::isCommand(const CmdQueueInfo& plainMsgInfo)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (plainMsgInfo.queueInfo_supplement.empty())
        return false;

    JsonUnique sharedRoot(cJSON_Parse(plainMsgInfo.queueInfo_supplement.c_str()));
    cJSON* jsSupplement = sharedRoot.get();

    string attributes =  Utilities::getJsonString(jsSupplement, "m", "");

    return isCommand(plainMsgInfo.queueInfo_msgType, attributes);
}

int32_t AppInterfaceImpl::receiveMessage(const string& envelope, const string& uidString, const string& displayName)
{
    int64_t sequence;
    int32_t sqlResult = store_->insertReceivedRawData(envelope, uidString, displayName, &sequence);

    if (SQL_FAIL(sqlResult)) {
        return DATABASE_ERROR;
    }

    auto msgInfo = new CmdQueueInfo;
    msgInfo->command = ReceivedRawData;
    msgInfo->queueInfo_envelope = envelope;
    msgInfo->queueInfo_uid = uidString;
    msgInfo->queueInfo_displayName = displayName;
    msgInfo->queueInfo_sequence = sequence;

    addMsgInfoToRunQueue(unique_ptr<CmdQueueInfo>(msgInfo));
    return OK;
}

// Take a message envelope (see sendMessage above), parse it, and process the embedded data. Then
// forward the data to the UI layer.
static int32_t duplicates = 0;

void AppInterfaceImpl::processMessageRaw(const CmdQueueInfo &msgInfo) {
    LOGGER(DEBUGGING, __func__, " -->");

    const string &messageEnvelope = msgInfo.queueInfo_envelope;
    const string &uid = msgInfo.queueInfo_uid;
    const string &displayName = msgInfo.queueInfo_displayName;

    uint8_t hash[SHA256_DIGEST_LENGTH];
    sha256((uint8_t *) messageEnvelope.data(), (uint32_t) messageEnvelope.size(), hash);

    string msgHash;
    msgHash.assign((const char *) hash, SHA256_DIGEST_LENGTH);

    int32_t sqlResult = store_->hasMsgHash(msgHash);

    // If we found a duplicate, log and silently ignore it. Remove from DB queue if it is still available
    if (sqlResult == SQLITE_ROW) {
        LOGGER(WARNING, __func__, " Duplicate messages detected so far: ", ++duplicates);
        store_->deleteReceivedRawData(msgInfo.queueInfo_sequence);
        return;
    }

    // Cleanup old message hashes
    time_t timestamp = time(0) - MK_STORE_TIME;
    store_->deleteMsgHashes(timestamp);

    if (messageEnvelope.size() > tempBufferSize_) {
        delete[] tempBuffer_;
        tempBuffer_ = new char[messageEnvelope.size()];
        tempBufferSize_ = messageEnvelope.size();
    }
    size_t binLength = b64Decode(messageEnvelope.data(), messageEnvelope.size(), (uint8_t *) tempBuffer_,
                                 tempBufferSize_);
    if (binLength == 0) {
        LOGGER(ERROR, __func__, "Base64 decoding of received message failed.");
        store_->deleteReceivedRawData(msgInfo.queueInfo_sequence);
        return;
    }

    MessageEnvelope envelope;
    if (!envelope.ParseFromArray(tempBuffer_, static_cast<int32_t>(binLength))) {
        LOGGER(ERROR, __func__, "ProtoBuffer decoding of received message failed.");
        store_->deleteReceivedRawData(msgInfo.queueInfo_sequence);
        return;
    }

    // backward compatibility or in case the message Transport does not support
    // UID. Then fallback to data in the message envelope.
    const string &sender = uid.empty() ? envelope.name() : uid;

    // Seems we have a valid message envelope
    // Get the sender's device id and the message id and the message type
    const string &senderScClientDevId = envelope.scclientdevid();
    const string &msgId = envelope.msgid();
    int32_t msgType = envelope.has_msgtype() ? envelope.msgtype() : MSG_NORMAL;

    errorCode_ = SUCCESS;       // Be optimistic and assume success

    // Define / initialize some variables at this point because in case of an error
    // we use goto and the compiler does not like cross-initialization of variables.
    char receiverDevId[16] = {0};
    string sentToId;

    string supplementsPlain;
    shared_ptr<const string> messagePlain;
    cJSON *convJson = nullptr;
    unique_ptr<CmdQueueInfo> plainMsgInfo;
    unique_ptr<ZinaConversation> primaryConv;
    unique_ptr<ZinaConversation> secondaryConv;
    string msgDescriptor;

    // The msg id is a time based UUID, parse it and check if the message is too old
    uuid_t uu = {0};
    time_t msgTime, currentTime, timeDiff = 0;
    if (uuid_parse(msgId.c_str(), uu) != 0) {
        errorCode_ = CORRUPT_DATA;
        goto errorMessage_;
    }
    msgTime = uuid_time(uu, NULL);
    currentTime = time(NULL);
    timeDiff = currentTime - msgTime;

    // We can't process very old messages, the keys are already gone
    if (timeDiff > 0 && timeDiff >= MK_STORE_TIME) {
        errorCode_ = OLD_MESSAGE;
        goto errorMessage_;
    }

    // Check if this message was really intended to this client. If the sender added a short id to the
    // envelope that we can use to check this
    if (envelope.has_recvdevidbin()) {
        sentToId = envelope.recvdevidbin();
    }

    if (!sentToId.empty()) {
        uint8_t binDevId[20];
        hex2bin(scClientDevId_.c_str(), binDevId);

        bool wrongDeviceId = memcmp((void *) sentToId.data(), binDevId, sentToId.size()) != 0;

        if (wrongDeviceId) {
            size_t len;
            bin2hex((const uint8_t *) sentToId.data(), sentToId.size(), receiverDevId, &len);
            errorCode_ = WRONG_RECV_DEV_ID;
            LOGGER(ERROR, __func__, "Message is for device id: ", receiverDevId, ", my device id: ", scClientDevId_);
            goto errorMessage_;
        }
    }

    primaryConv = ZinaConversation::loadConversation(ownUser_, sender, senderScClientDevId, *store_);
    errorCode_ = primaryConv->getErrorCode();
    if (errorCode_ != SUCCESS) {
        goto errorMessage_;
    }
    // Prepare some data for debugging if we have a develop build and debugging is enabled
    LOGGER_BEGIN(INFO)
        convJson = primaryConv->prepareForCapture(nullptr, true);
    LOGGER_END

    // OK, do the real decryption here
    messagePlain = ZinaRatchet::decrypt(primaryConv.get(), envelope, *store_, &supplementsPlain, secondaryConv);
    if (!messagePlain) {
        errorCode_ = primaryConv->getErrorCode();
        goto errorMessage_;
    }

    // At this point we have a valid decrypted message

    // Prepare some data for debugging if we have a develop build and debugging is enabled
    // We don't capture the message itself but only some relevant, public context data
    LOGGER_BEGIN(INFO)
        convJson = primaryConv->prepareForCapture(convJson, false);

        CharUnique out(cJSON_PrintUnformatted(convJson));
        string convState(out.get());
        cJSON_Delete(convJson);
        MessageCapture::captureReceivedMessage(sender, msgId, senderScClientDevId, convState,
                                               string("{\"cmd\":\"dummy\"}"), false, *store_);
    LOGGER_END
    {
        /*
         * Message descriptor for received message:
         {
             "version":    <int32_t>,            # Version of JSON send message descriptor, 1 for the first implementation
             "sender":     <string>,             # for SC this is either the user's name or the user's DID
             "scClientDevId" : <string>,         # the sender's long device id
             "message":    <string>              # the actual plain text message, UTF-8 encoded (Java programmers beware!)
        }
        */
        JsonUnique uniqueRoot(cJSON_CreateObject());
        cJSON *root = uniqueRoot.get();
        cJSON_AddNumberToObject(root, "version", 1);
        cJSON_AddStringToObject(root, MSG_SENDER, sender.c_str());        // sender is the UUID string

        // backward compatibility or in case the message Transport does not support
        // alias handling. Then fallback to data in the message envelope.
        cJSON_AddStringToObject(root, MSG_DISPLAY_NAME,
                                displayName.empty() ? envelope.name().c_str() : displayName.c_str());
        cJSON_AddStringToObject(root, MSG_DEVICE_ID, senderScClientDevId.c_str());
        cJSON_AddStringToObject(root, MSG_ID, msgId.c_str());
        cJSON_AddStringToObject(root, MSG_MESSAGE, messagePlain->c_str());
        cJSON_AddBoolToObject(root, MSG_ID_KEY_CHANGED, primaryConv->isIdentityKeyChanged());

        cJSON_AddNumberToObject(root, MSG_TYPE, msgType);
        messagePlain.reset();

        CharUnique msg(cJSON_PrintUnformatted(root));
        msgDescriptor = msg.get();
    }

    plainMsgInfo = unique_ptr<CmdQueueInfo>(new CmdQueueInfo);
    plainMsgInfo->command = ReceivedTempMsg;
    plainMsgInfo->queueInfo_message_desc = msgDescriptor;
    plainMsgInfo->queueInfo_supplement = supplementsPlain;
    plainMsgInfo->queueInfo_msgType = msgType;

    // At this point, in one DB transaction:
    // - save msgDescriptor and supplements plain data in DB,
    // - store msgHash to detect duplicate messages,
    // - store staged message keys,
    // - save conversation (ratchet context),
    // - delete raw message data because it's processed
    int64_t sequence;
    int32_t result;
//    bool processPlaintext = false;
    {
        store_->beginTransaction();

        result = store_->insertMsgHash(msgHash);
        if (SQL_FAIL(result))
            goto error_;

        if (secondaryConv) {
            result = secondaryConv->storeStagedMks(*store_);
            if (SQL_FAIL(result))
                goto error_;

            result = secondaryConv->storeConversation(*store_);
            if (SQL_FAIL(result))
                goto error_;
        }
        result = primaryConv->storeStagedMks(*store_);
        if (SQL_FAIL(result))
            goto error_;

        result = primaryConv->storeConversation(*store_);
        if (SQL_FAIL(result))
            goto error_;

#if !defined (UNITTESTS) && defined(SC_ENABLE_DR_RECV)
        // If this function returns false then don't store the plaintext in the plaintext
        // message queue, however commit the transaction to delete the raw data and save
        // the ratchet context
        if (!dataRetentionReceive(plainMsgInfo)) {
            goto success_;
        }
#endif
        result = store_->insertTempMsg(msgDescriptor, plainMsgInfo->queueInfo_supplement, msgType, &sequence);
//        processPlaintext = true;
        if (!SQL_FAIL(result))
            goto success_;

        error_:
            store_->rollbackTransaction();
            if (msgType >= GROUP_MSG_NORMAL) {
                groupStateReportCallback_(DATABASE_ERROR,
                                          receiveErrorJson(sender, senderScClientDevId, msgId, "Error while storing state data",
                                                           DATABASE_ERROR, sentToId, store_->getExtendedErrorCode(), msgType));
            }
            else {
                stateReportCallback_(0, DATABASE_ERROR,
                                 receiveErrorJson(sender, senderScClientDevId, msgId, "Error while storing state data",
                                                  DATABASE_ERROR, sentToId, store_->getExtendedErrorCode(), msgType));
            }
            return;

        success_:
           store_->deleteReceivedRawData(msgInfo.queueInfo_sequence);
           store_->commitTransaction();
    }
//    if (!processPlaintext) {
//        LOGGER(DEBUGGING, __func__, " <-- don't process plaintext, DR policy");
//        return;
//    }
    plainMsgInfo->queueInfo_sequence = sequence;

#ifndef UNITTESTS
    sendDeliveryReceipt(*plainMsgInfo);
#endif

    processMessagePlain(*plainMsgInfo);
    LOGGER(DEBUGGING, __func__, " <--");
    return;

    // Come here if something went wrong after parsing of the input data (proto buffer parsing)
    // was OK. The errorCode_ must contain the reason of the problem
  errorMessage_:
    {
        // Remove raw message data, we can't process it anyway
        store_->deleteReceivedRawData(msgInfo.queueInfo_sequence);

        // In case of error we capture some additional data, prepared above. This additional data
        // does not reveal any security relevant data. We do this for builds which are able to log
        // INFO and if log level is INFO or higher
        LOGGER_BEGIN(INFO)
            CharUnique out(cJSON_PrintUnformatted(convJson));
            if (out) {
                string convState(out.get());
                cJSON_Delete(convJson);

                MessageCapture::captureReceivedMessage(sender, msgId, senderScClientDevId, convState,
                                                       string("{\"cmd\":\"failed\"}"), false, *store_);
            }
        LOGGER_END
        if (msgType >= GROUP_MSG_NORMAL) {
            groupStateReportCallback_(errorCode_,
                                      receiveErrorJson(sender, senderScClientDevId, msgId, "Message processing failed.",
                                                       errorCode_, receiverDevId, store_->getExtendedErrorCode(), msgType));
        } else {
            stateReportCallback_(0, errorCode_,
                                 receiveErrorJson(sender, senderScClientDevId, msgId, "Message processing failed.",
                                                  errorCode_, receiverDevId, store_->getExtendedErrorCode(), msgType));
        }

        LOGGER(ERROR, __func__ , " Message processing failed: ", errorCode_, ", sender: ", sender, ", device: ", senderScClientDevId );
        if (errorCode_ == DATABASE_ERROR) {
            LOGGER(ERROR, __func__, " Database error: ", store_->getExtendedErrorCode(), ", SQL message: ", *store_->getLastError());
        }
        // Don't report processing failures on command messages
        if (msgType < MSG_CMD) {
            if (errorCode_ == MAC_CHECK_FAILED || errorCode_ == MSG_PADDING_FAILED || errorCode_ == SUP_PADDING_FAILED
                    || errorCode_ == WRONG_BLK_SIZE || errorCode_ ==  UNSUPPORTED_KEY_SIZE) {
                sendErrorCommand(DECRYPTION_FAILED, sender, msgId);
            }
            // TODO: check if we should inform sender about non critical processing failures that lead to a non-visible message
        }
    }
}

void AppInterfaceImpl::processMessagePlain(const CmdQueueInfo &msgInfo)
{
    LOGGER(DEBUGGING, __func__, " -->");

    int32_t result;

    string attachmentDescr;
    string attributesDescr;

    const string& supplementsPlain = msgInfo.queueInfo_supplement;
    if (!supplementsPlain.empty()) {
        JsonUnique sharedRoot(cJSON_Parse(supplementsPlain.c_str()));
        cJSON* jsSupplement = sharedRoot.get();

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
    }

    if (msgInfo.queueInfo_msgType >= GROUP_MSG_NORMAL) {
        result = processGroupMessage(msgInfo.queueInfo_msgType, msgInfo.queueInfo_message_desc, attachmentDescr, &attributesDescr);
        if (result != SUCCESS) {
            JsonUnique sharedRoot(cJSON_Parse(attributesDescr.c_str()));
            cJSON* root = sharedRoot.get();
            string groupId(Utilities::getJsonString(root, GROUP_ID, ""));

            groupStateReportCallback_(result, receiveErrorDescriptor(msgInfo.queueInfo_message_desc, result, groupId));
            return;
        }
    }
    else {
        result = receiveCallback_(msgInfo.queueInfo_message_desc, attachmentDescr, attributesDescr);
        if (!(result == OK || result == SUCCESS)) {
            stateReportCallback_(0, result, receiveErrorDescriptor(msgInfo.queueInfo_message_desc, result));
            return;
        }
    }
    store_->deleteTempMsg(msgInfo.queueInfo_sequence);
    LOGGER(DEBUGGING, __func__, " <--");
}

#ifdef SC_ENABLE_DR_RECV
bool AppInterfaceImpl::dataRetentionReceive(shared_ptr<CmdQueueInfo> plainMsgInfo)
{
    LOGGER(DEBUGGING, __func__, " -->");
    string sender;
    string msgId;
    string message;

    if (isCommand(plainMsgInfo)) {
        // Forward command messages to the app but don't retain them yet
        LOGGER(INFO, __func__, " Don't retain command messages yet");
        return true;
    }


    // Parse a msg descriptor that's always correct because it was constructed above :-)
    parseMsgDescriptor(plainMsgInfo->queueInfo_message_desc, &sender, &msgId, &message, true);

//    LOGGER(WARNING, " ++++ DR receive flags, local ", drLrmp_, ", ", drLrmm_, ", block flags: ", drBrdr_, ", ", drBrmr_);

    // The user blocks local data retention, thus vetos setting of retention policy of the organization
    if ((drBldr_ && drLrmp_) || (drBlmr_ && drLrmm_)) {
        LOGGER(INFO, __func__, " <-- Reject data retention.");
        sendErrorCommand(COMM_BLOCKED, sender, msgId);
        return false;
    }

    if (plainMsgInfo->queueInfo_supplement.empty()) {   // No attributes -> no RAP, no RAM -> default false
        if (drLrmp_) {                                  // local client requires to retain plaintext data -> reject
            sendErrorCommand(DR_DATA_REQUIRED, sender, msgId);
            return false;
        }
        if (drLrmm_) {                                  // local client requires to retain meta data -> reject
            sendErrorCommand(DR_META_REQUIRED, sender, msgId);
            return false;
        }
    }

    shared_ptr<cJSON> sharedRoot(cJSON_Parse(plainMsgInfo->queueInfo_supplement.c_str()), cJSON_deleter);
    cJSON* jsSupplement = sharedRoot.get();
    string attributes =  Utilities::getJsonString(jsSupplement, "m", "");
    if (attributes.empty()) {                           // No attributes -> no RAP, no RAM -> default false
        if (drLrmp_) {                                  // local client requires to retain plaintext data -> reject
            sendErrorCommand(DR_DATA_REQUIRED, sender, msgId);
            return false;
        }
        if (drLrmm_) {                                  // local client requires to retain meta data -> reject
            sendErrorCommand(DR_META_REQUIRED, sender, msgId);
            return false;
        }
    }
    string attachmentDescr = Utilities::getJsonString(jsSupplement, "a", "");

    shared_ptr<cJSON> attributesJson(cJSON_Parse(attributes.c_str()), cJSON_deleter);
    cJSON* attributesRoot = attributesJson.get();

    int32_t drFlagsMask = 0;

    bool msgRap = Utilities::getJsonBool(attributesRoot, RAP, false);   // Does remote party accept plaintext retention?
    if (msgRap)
        drFlagsMask |= RAP_BIT;

    bool msgRam = Utilities::getJsonBool(attributesRoot, RAM, false);   // Does remote party accept meta data retention?
    if (msgRam)
        drFlagsMask |= RAM_BIT;

    if (msgRap && !msgRam) {
        LOGGER(WARNING, __func__, " Data retention accept flags inconsistent, RAP is true, force RAM to true");
        msgRam = true;
        drFlagsMask |= RAM_BIT;
    }
    if (drLrmp_ && !msgRap) {                               // Remote party doesn't accept retaining plaintext data
        sendErrorCommand(DR_DATA_REQUIRED, sender, msgId);  // local client requires to retain plaintext data -> reject
        return false;
    }
    if (drLrmm_ && !msgRam) {                               // Remote party doesn't accept retaining meta data
        sendErrorCommand(DR_META_REQUIRED, sender, msgId);  // local client requires to retain plaintext data -> reject
        return false;
    }

    bool msgRop = Utilities::getJsonBool(attributesRoot, ROP, false);   // Remote party retained plaintext
    if (msgRop)
        drFlagsMask |= ROP_BIT;

    bool msgRom = Utilities::getJsonBool(attributesRoot, ROM, false);   // Remote party retained meta data
    if (msgRom)
        drFlagsMask |= ROM_BIT;

//    LOGGER(WARNING, " ++++ DR receive attribute flags: ", msgRop, ", ", msgRom);

    // If ROP and/or ROM are true then this shows the remote party did some DR. If the
    // local flags BRDR or BRMR are set then reject the message
    if ((msgRop && drBrdr_) || (msgRom && drBrmr_)) {
        if (msgRop) {                                           // Remote party retained plaintext
            sendErrorCommand(DR_DATA_REJECTED, sender, msgId);  // local client blocks retaining plaintext data -> reject
            return false;
        }
        if (msgRam) {                                           // Remote party retained meta data
            sendErrorCommand(DR_META_REJECTED, sender, msgId);  // local client blocks retaining meta data -> reject
            return false;
        }
    }
    NameLookup* nameLookup = NameLookup::getInstance();
    auto remoteUserInfo = nameLookup->getUserInfo(sender, authorization_, false);
    if (!remoteUserInfo) {
        return false;        // No info for remote user??
    }

//    LOGGER(WARNING, " ++++ DR receive flags, remote: ", remoteUserInfo->drRrmp, ", ", remoteUserInfo->drRrmm);

    // If the cache information about the sender's data retention status does not match
    // the info in the message attribute then refresh the remote user info
    if ((remoteUserInfo->drRrmm != msgRom) || (remoteUserInfo->drRrmp != msgRop)) {
        nameLookup->refreshUserData(sender, authorization_);
    }
    uuid_t uu = {0};
    uuid_parse(msgId.c_str(), uu);
    time_t composeTime = uuid_time(uu, NULL);

    time_t currentTime = time(NULL);

    DrLocationData location(attributesRoot, msgRap);

    shared_ptr<cJSON> attachmentsJson(cJSON_Parse(attachmentDescr.c_str()), cJSON_deleter);
    DrAttachmentData attachment(attachmentsJson.get(), msgRap);

    if (msgRap) {
        ScDataRetention::sendMessageMetadata("", "received", location, attachment, sender, composeTime, currentTime);
        ScDataRetention::sendMessageData("", "received", sender, composeTime, currentTime, message);
    } else if (msgRam) {
        ScDataRetention::sendMessageMetadata("", "received", location, attachment, sender, composeTime, currentTime);
    }
    cJSON_DeleteItemFromObject(attributesRoot, RAP);
    cJSON_DeleteItemFromObject(attributesRoot, RAM);
    cJSON_DeleteItemFromObject(attributesRoot, ROP);
    cJSON_DeleteItemFromObject(attributesRoot, ROM);
    cJSON_AddNumberToObject(attributesRoot, DR_STATUS_BITS, drFlagsMask);

    char *out = cJSON_PrintUnformatted(attributesRoot);
    string messageAttrib(out);
    free(out);

    plainMsgInfo->queueInfo_supplement = createSupplementString(attachmentDescr, messageAttrib);
    LOGGER(DEBUGGING, __func__, " <--");
    return true;
}
#endif // SC_ENABLE_DR_RECV

void AppInterfaceImpl::sendDeliveryReceipt(const CmdQueueInfo &plainMsgInfo)
{
    LOGGER(DEBUGGING, __func__, " -->");
    // don't send delivery receipt group messages, group commands, normal commands, only for real messages
    if (plainMsgInfo.queueInfo_msgType >= GROUP_MSG_NORMAL || isCommand(plainMsgInfo)) {
        LOGGER(DEBUGGING, __func__, " <-- no delivery receipt");
        return;
    }
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* attributeJson = sharedRoot.get();

    cJSON_AddStringToObject(attributeJson, MSG_COMMAND, DELIVERY_RECEIPT);
    cJSON_AddStringToObject(attributeJson, DELIVERY_TIME, Utilities::currentTimeISO8601().c_str());
    char *out = cJSON_PrintUnformatted(attributeJson);

    string command(out);
    free(out);

    string sender;
    string msgId;
    string message;
    // Parse a msg descriptor that's always correct because it was constructed above :-)
    parseMsgDescriptor(plainMsgInfo.queueInfo_message, &sender, &msgId, &message, true);
    Utilities::wipeString(message);

    int32_t result;
    auto preparedMsgData = prepareMessageInternal(createMessageDescriptor(sender, msgId), Empty, command, false, MSG_CMD, &result);

    if (result != SUCCESS) {
        LOGGER(ERROR, __func__, " <-- Error: ", result);
        return;
    }
    doSendMessages(extractTransportIds(preparedMsgData.get()));
    LOGGER(DEBUGGING, __func__, " <--");
}

void AppInterfaceImpl::sendErrorCommand(const string& error, const string& sender, const string& msgId)
{
    LOGGER(DEBUGGING, __func__, " -->");
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* attributeJson = sharedRoot.get();

    cJSON_AddStringToObject(attributeJson, MSG_COMMAND, error.c_str());
    cJSON_AddStringToObject(attributeJson, COMMAND_TIME, Utilities::currentTimeISO8601().c_str());
//    cJSON_AddBoolToObject(attributeJson, ROP, false);
//    cJSON_AddBoolToObject(attributeJson, ROM, false);
//    cJSON_AddBoolToObject(attributeJson, RAP, true);
//    cJSON_AddBoolToObject(attributeJson, RAM, true);

    char *out = cJSON_PrintUnformatted(attributeJson);

    string command(out);
    free(out);

    int32_t result;
    auto preparedMsgData = prepareMessageInternal(createMessageDescriptor(sender, msgId), Empty, command, false, MSG_DEC_FAILED, &result);

    if (result != SUCCESS) {
        LOGGER(ERROR, __func__, " <-- Error: ", result);
        return;
    }
    doSendMessages(extractTransportIds(preparedMsgData.get()));
    LOGGER(DEBUGGING, __func__, " <-- ", command);
}
