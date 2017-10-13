/*
 * Copyright 2016-2017 Silent Circle, LLC

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

//
// Implementation of group chat
//
// Created by werner on 22.05.16.
//

#include <cryptcommon/ZrtpRandom.h>
#include "AppInterfaceImpl.h"
#include "GroupProtocol.pb.h"
#include "JsonStrings.h"
#include "../util/Utilities.h"
#include "../util/b64helper.h"
#include "../vectorclock/VectorClock.h"
#include "../vectorclock/VectorHelper.h"

using namespace std;
using namespace zina;
using namespace vectorclock;

static void fillMemberArray(cJSON* root, const list<string> &members)
{
    cJSON* memberArray;
    cJSON_AddItemToObject(root, MEMBERS, memberArray = cJSON_CreateArray());

    for (const auto &member : members) {
        cJSON_AddItemToArray(memberArray, cJSON_CreateString(member.c_str()));
    }
}

static string prepareMemberList(const string &groupId, const list<string> &members, const char *command, const struct timeval&  stamp) {
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();

    cJSON_AddStringToObject(root, GROUP_COMMAND, command);
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    cJSON_AddNumberToObject(root, COMMAND_TIME, stamp.tv_sec);
    cJSON_AddNumberToObject(root, COMMAND_TIME_U, stamp.tv_usec);

    fillMemberArray(root, members);

    CharUnique out(cJSON_PrintUnformatted(root));
    string listCommand(out.get());

    return listCommand;
}

static string leaveCommand(const string& groupId, const string& memberId, const struct timeval&  stamp)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();
    cJSON_AddStringToObject(root, GROUP_COMMAND, LEAVE);
    cJSON_AddStringToObject(root, MEMBER_ID, memberId.c_str());
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    cJSON_AddNumberToObject(root, COMMAND_TIME, stamp.tv_sec);
    cJSON_AddNumberToObject(root, COMMAND_TIME_U, stamp.tv_usec);

    CharUnique out(cJSON_PrintUnformatted(root));
    string command(out.get());

    return command;
}

static string newGroupCommand(const string& groupId, int32_t maxMembers, const struct timeval& stamp)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();
    cJSON_AddStringToObject(root, GROUP_COMMAND, NEW_GROUP);
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    cJSON_AddNumberToObject(root, GROUP_MAX_MEMBERS, maxMembers);
    cJSON_AddNumberToObject(root, COMMAND_TIME, stamp.tv_sec);
    cJSON_AddNumberToObject(root, COMMAND_TIME_U, stamp.tv_usec);

    CharUnique out(cJSON_PrintUnformatted(root));
    string command(out.get());

    return command;
}

static string newGroupNameCommand(const string& groupId, const string& groupName, const struct timeval&  stamp)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();
    cJSON_AddStringToObject(root, GROUP_COMMAND, NEW_NAME);
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    cJSON_AddStringToObject(root, GROUP_NAME, groupName.c_str());
    cJSON_AddNumberToObject(root, COMMAND_TIME, stamp.tv_sec);
    cJSON_AddNumberToObject(root, COMMAND_TIME_U, stamp.tv_usec);

    CharUnique out(cJSON_PrintUnformatted(root));
    string command(out.get());

    return command;
}

static string newGroupAvatarCommand(const string& groupId, const string& groupAvatar, const struct timeval&  stamp)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();
    cJSON_AddStringToObject(root, GROUP_COMMAND, NEW_AVATAR);
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    cJSON_AddStringToObject(root, GROUP_AVATAR, groupAvatar.c_str());
    cJSON_AddNumberToObject(root, COMMAND_TIME, stamp.tv_sec);
    cJSON_AddNumberToObject(root, COMMAND_TIME_U, stamp.tv_usec);

    CharUnique out(cJSON_PrintUnformatted(root));
    string command(out.get());

    return command;
}

static string newGroupBurnCommand(const string& groupId, int64_t burnTime, int32_t burnMode, const struct timeval&  stamp)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();
    cJSON_AddStringToObject(root, GROUP_COMMAND, NEW_BURN);
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    cJSON_AddNumberToObject(root, GROUP_BURN_SEC, burnTime);
    cJSON_AddNumberToObject(root, GROUP_BURN_MODE, burnMode);
    cJSON_AddNumberToObject(root, COMMAND_TIME, stamp.tv_sec);
    cJSON_AddNumberToObject(root, COMMAND_TIME_U, stamp.tv_usec);

    CharUnique out(cJSON_PrintUnformatted(root));
    string command(out.get());

    return command;
}

static string groupBurnMsgCommand(const string& groupId, const ::google::protobuf::RepeatedPtrField< ::std::string>& msgIds,
                                  const string& member, const struct timeval& stamp)
{
    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();
    cJSON_AddStringToObject(root, GROUP_COMMAND, REMOVE_MSG);
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());

    cJSON* idArray;
    cJSON_AddItemToObject(root, MSG_IDS, idArray = cJSON_CreateArray());

    for (auto const &id : msgIds) {
        cJSON_AddItemToArray(idArray, cJSON_CreateString(id.c_str()));
    }
    cJSON_AddStringToObject(root, MEMBER_ID, member.c_str());
    cJSON_AddNumberToObject(root, COMMAND_TIME, stamp.tv_sec);
    cJSON_AddNumberToObject(root, COMMAND_TIME_U, stamp.tv_usec);

    CharUnique out(cJSON_PrintUnformatted(root));
    string command(out.get());

    return command;
}

static int32_t serializeChangeSet(const GroupChangeSet& changeSet, string *attributes)
{

    string serialized;
    if (!changeSet.SerializeToString(&serialized)) {
        return GENERIC_ERROR;
    }
    auto b64Size = static_cast<size_t>(serialized.size() * 2);
    unique_ptr<char[]> b64Buffer(new char[b64Size]);
    if (b64Encode(reinterpret_cast<const uint8_t *>(serialized.data()), serialized.size(), b64Buffer.get(), b64Size) == 0) {
        return GENERIC_ERROR;
    }

    JsonUnique sharedRoot(cJSON_CreateObject());
    cJSON* root = sharedRoot.get();

    string serializedSet;
    serializedSet.assign(b64Buffer.get());

    if (!serializedSet.empty()) {
        cJSON_AddStringToObject(root, GROUP_CHANGE_SET, serializedSet.c_str());
    }
    CharUnique out(cJSON_PrintUnformatted(root));
    attributes->assign(out.get());
    return SUCCESS;
}

// ****** Public instance functions
// *******************************************************

bool AppInterfaceImpl::modifyGroupSize(const string& groupId, int32_t newSize)
{
    LOGGER(DEBUGGING, __func__, " -->");
    if (!store_->isReady()) {
        errorInfo_ = " Conversation store not ready.";
        LOGGER(ERROR, __func__, errorInfo_);
        return false;
    }
    int32_t result;
    shared_ptr<cJSON> group = store_->listGroup(groupId, &result);
    if (!group || SQL_FAIL(result)) {
        errorInfo_ = " Cannot get group data: ";
        errorInfo_.append(groupId);
        LOGGER(ERROR, __func__, errorInfo_);
        return false;
    }
    cJSON* root = group.get();
    string groupOwner(Utilities::getJsonString(root, GROUP_OWNER, ""));

    if (ownUser_ != groupOwner) {
        errorInfo_ = " Only owner can modify group member size";
        LOGGER(ERROR, __func__, errorInfo_);
        return false;
    }
    int32_t members = Utilities::getJsonInt(root, GROUP_MEMBER_COUNT, -1);
    if (members == -1 || members > newSize) {
        errorInfo_ = " Already more members in group than requested.";
        LOGGER(ERROR, __func__, errorInfo_, members);
        return false;

    }
    LOGGER(DEBUGGING, __func__, " <--");
    return true;
}

int32_t AppInterfaceImpl::sendGroupMessage(const string &messageDescriptor, const string &attachmentDescriptor,
                                           const string &messageAttributes) {
    string groupId;
    string msgId;
    string message;

    LOGGER(DEBUGGING, __func__, " -->");
    int32_t result = parseMsgDescriptor(messageDescriptor, &groupId, &msgId, &message);
    if (result < 0) {
        errorCode_ = result;
        LOGGER(ERROR, __func__, " Wrong JSON data to send group message, error code: ", result);
        return result;
    }
    JsonUnique sharedRoot(!messageAttributes.empty() ? cJSON_Parse(messageAttributes.c_str()) : cJSON_CreateObject());
    cJSON* root = sharedRoot.get();

    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());

    char *out = cJSON_PrintUnformatted(root);
    string newAttributes(out);
    free(out);

    result = prepareChangeSetSend(groupId);
    if (result < 0) {
        errorCode_ = result;
        errorInfo_ = "Error preparing group change set";
        LOGGER(ERROR, __func__, " Error preparing group change set, error code: ", result);
        return result;
    }
    if (!store_->hasGroup(groupId) || ((store_->getGroupAttribute(groupId).first & ACTIVE) != ACTIVE)) {
        return NO_SUCH_ACTIVE_GROUP;
    }

    list<JsonUnique> members;
    result = store_->getAllGroupMembers(groupId, members);
    size_t membersFound = members.size();
    int32_t errorResult = OK;
    for (auto& member: members) {
        string recipient(Utilities::getJsonString(member.get(), MEMBER_ID, ""));
        bool toSibling = recipient == ownUser_;
        auto preparedMsgData = prepareMessageInternal(messageDescriptor, attachmentDescriptor, newAttributes,
                                                      toSibling, GROUP_MSG_NORMAL, &result, recipient, groupId);
        if (result != SUCCESS) {
            LOGGER(ERROR, __func__, " Error sending group message to: ", recipient);
            errorResult = result;
        }
        if (!preparedMsgData->empty()) {
            doSendMessages(extractTransportIds(preparedMsgData.get()));
        }
    }
    groupUpdateSendDone(groupId);
    LOGGER(DEBUGGING, __func__, " <--, ", membersFound);
    return errorResult;
}

int32_t AppInterfaceImpl::sendGroupMessageToMember(const string &messageDescriptor, const string &attachmentDescriptor,
                                                   const string &messageAttributes, const string &recipient,
                                                   const string &deviceId)
{
    string groupId;
    string msgId;
    string message;

    LOGGER(DEBUGGING, __func__, " -->");
    int32_t result = parseMsgDescriptor(messageDescriptor, &groupId, &msgId, &message);
    if (result < 0) {
        errorCode_ = result;
        LOGGER(ERROR, __func__, " Wrong JSON data to send group message, error code: ", result);
        return result;
    }
    JsonUnique uniqueRoot(!messageAttributes.empty() ? cJSON_Parse(messageAttributes.c_str()) : cJSON_CreateObject());
    cJSON* root = uniqueRoot.get();

    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());

    char *out = cJSON_PrintUnformatted(root);
    string newAttributes(out);
    free(out);

    result = prepareChangeSetSend(groupId);
    if (result < 0) {
        errorCode_ = result;
        errorInfo_ = "Error preparing group change set";
        LOGGER(ERROR, __func__, " Error preparing group change set, error code: ", result);
        return result;
    }
    if (!store_->hasGroup(groupId) || ((store_->getGroupAttribute(groupId).first & ACTIVE) != ACTIVE)) {
        return NO_SUCH_ACTIVE_GROUP;
    }

    int32_t errorResult = OK;
    if (!deviceId.empty()) {
        queueMessageToSingleUserDevice(recipient, msgId, deviceId, Empty, newAttributes, attachmentDescriptor,
                                       message, GROUP_MSG_NORMAL, false, NoAction);
    }
    else {
        const bool toSibling = recipient == ownUser_;

        auto preparedMsgData = prepareMessageInternal(messageDescriptor, attachmentDescriptor, newAttributes,
                                                      toSibling, GROUP_MSG_NORMAL, &result, recipient, groupId);
        if (result != SUCCESS) {
            LOGGER(ERROR, __func__, " Error sending group message to: ", recipient);
            errorResult = result;
        }
        if (!preparedMsgData->empty()) {
            doSendMessages(extractTransportIds(preparedMsgData.get()));
        }
    }
    groupUpdateSendDone(groupId);
    LOGGER(DEBUGGING, __func__, " <--");
    return errorResult;
}

int32_t AppInterfaceImpl::sendGroupCommandToMember(const string& groupId, const string &member, const string &msgId, const string &command)
{
    LOGGER(DEBUGGING, __func__, " --> ", member, ", ", ownUser_);

    bool toSibling = member == ownUser_;
    int32_t result;
    auto preparedMsgData = prepareMessageInternal(createMessageDescriptor(member, msgId.empty() ? generateMsgIdTime() : msgId),
                                                  Empty, command, toSibling, GROUP_MSG_CMD, &result, member, groupId);
    if (result != SUCCESS) {
        LOGGER(ERROR, __func__, " <-- Error: ", result);
        return result;
    }
    doSendMessages(extractTransportIds(preparedMsgData.get()));

    LOGGER(DEBUGGING, __func__, " <--");
    return OK;
}

// ****** Non public instance functions and helpers
// ******************************************************

int32_t AppInterfaceImpl::processGroupMessage(int32_t msgType, const string &msgDescriptor,
                                              const string &attachmentDescr, string *attributesDescr)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (msgType == GROUP_MSG_CMD) {
        return processGroupCommand(msgDescriptor, attributesDescr);
    }
    if (msgType == GROUP_MSG_NORMAL && msgDescriptor.empty()) {
        return GROUP_MSG_DATA_INCONSISTENT;
    }
    if (checkAndProcessChangeSet(msgDescriptor, attributesDescr) == SUCCESS) {
        groupMsgCallback_(msgDescriptor, attachmentDescr, *attributesDescr);
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::processGroupCommand(const string &msgDescriptor, string *commandIn)
{
    LOGGER(DEBUGGING, __func__, " --> ", *commandIn);

    if (commandIn->empty()) {
        return GROUP_CMD_MISSING_DATA;
    }
    JsonUnique sharedRoot(cJSON_Parse(commandIn->c_str()));
    cJSON* root = sharedRoot.get();

    string groupCommand(Utilities::getJsonString(root, GROUP_COMMAND, ""));

    // A command message may have a change set, process it
    int32_t result = checkAndProcessChangeSet(msgDescriptor, commandIn);
    if (result != SUCCESS) {
        return result;
    }

    // Now handle commands as usual, maybe forward them to UI for further processing.
    // Change set was deleted from JSON data by the check and process change set function
    // Forward unknown commands to UI and let UI decide what to do.
    if (groupCommand == HELLO) {
        LOGGER(INFO, __func__, "HELLO group command");
    }
    else {
        LOGGER(INFO, __func__, "Unknown group command: ", *commandIn, "msg descriptor: ", msgDescriptor);
        groupCmdCallback_(*commandIn);
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::checkAndProcessChangeSet(const string &msgDescriptor, string *messageAttributes)
{
    LOGGER(DEBUGGING, __func__, " -->");

    string changeSetString;

    JsonUnique sharedRoot(cJSON_Parse(messageAttributes->c_str()));
    cJSON* root = sharedRoot.get();
    if (Utilities::hasJsonKey(root, GROUP_CHANGE_SET)) {
        changeSetString = Utilities::getJsonString(root, GROUP_CHANGE_SET, "");

        // Remove the change set b64 data
        cJSON_DeleteItemFromObject(root, GROUP_CHANGE_SET);
        CharUnique out(cJSON_PrintUnformatted(root));
        messageAttributes->assign(out.get());
    }
    string groupId(Utilities::getJsonString(root, GROUP_ID, ""));
    bool hasGroup = store_->hasGroup(groupId);

    // An early check to avoid JSON parsing. No change set to process and we know this group: just return SUCCESS
    if (changeSetString.empty() && hasGroup) {
        return SUCCESS;
    }

    // Get the message sender info
    sharedRoot = JsonUnique(cJSON_Parse(msgDescriptor.c_str()));
    root = sharedRoot.get();
    string sender(Utilities::getJsonString(root, MSG_SENDER, ""));
    string deviceId(Utilities::getJsonString(root, MSG_DEVICE_ID, ""));

    uuid_t uu = {0};
    uuid_parse(Utilities::getJsonString(root, MSG_ID, ""), uu);
    struct timeval msgTime {0};
    uuid_time(uu, &msgTime);

    // We have an empty CS here and no group: an unsolicited message, tell sender to remove me from group
    if (changeSetString.empty()) {
        GroupChangeSet rmSet;
        GroupUpdateRmMember *updateRmMember = rmSet.mutable_updatermmember();
        Member *member = updateRmMember->add_rmmember();
        member->set_user_id(getOwnUser());

        string attributes;
        int32_t result = serializeChangeSet(rmSet, &attributes);
        if (result != SUCCESS) {
            return result;
        }
        // message from unknown group, ask sender to remove me
        // It's a non-user visible message, thus send it as type command. This prevents callback to UI etc.
        return sendGroupMessageToSingleUserDeviceNoCS(groupId, sender, deviceId, attributes, Empty, GROUP_MSG_CMD);
    }
    if (changeSetString.size() > tempBufferSize_) {
        delete[] tempBuffer_;
        tempBuffer_ = new char[changeSetString.size()];
        tempBufferSize_ = changeSetString.size();
    }
    size_t binLength = b64Decode(changeSetString.data(), changeSetString.size(), (uint8_t *) tempBuffer_,
                                 tempBufferSize_);
    if (binLength == 0) {
        LOGGER(ERROR, __func__, "Base64 decoding of group change set failed.");
        return CORRUPT_DATA;
    }

    GroupChangeSet changeSet;
    if (!changeSet.ParseFromArray(tempBuffer_, static_cast<int32_t>(binLength))) {
        LOGGER(ERROR, __func__, "ProtoBuffer decoding of group change set failed.");
        return CORRUPT_DATA;
    }

    GroupChangeSet ackRmSet;              // Gathers all ACKs and a remove on unexpected group change sets
    int32_t result = processReceivedChangeSet(changeSet, groupId, sender, deviceId, hasGroup, msgTime, &ackRmSet);
    if (result != SUCCESS) {
        return result;
    }

    if (ackRmSet.acks_size() > 0 || ackRmSet.has_updatermmember()) {
        string attributes;
        result = serializeChangeSet(ackRmSet, &attributes);
        if (result != SUCCESS) {
            return result;
        }
#ifndef UNITTESTS
        // It's a non-user visible message, thus send it as type command. This prevents callback to UI etc.
        result = sendGroupMessageToSingleUserDeviceNoCS(groupId, sender, deviceId, attributes, Empty, GROUP_MSG_CMD);
        if (result != SUCCESS) {
            return result;
        }
#else
        groupCmdCallback_(attributes);      // for unit testing only
#endif
    }
    LOGGER(DEBUGGING, __func__, " <-- ");
    return result;
}

static list<string> sentRemoveTo;

int32_t AppInterfaceImpl::processReceivedChangeSet(const GroupChangeSet &changeSet, const string &groupId,
                                                   const string &sender, const string &deviceId, bool hasGroup,
                                                   struct timeval& stamp, GroupChangeSet *ackRmSet)
{
    LOGGER(DEBUGGING, __func__, " -->");

    bool fromSibling = sender == getOwnUser();

    // If all this is true then our user left the group, triggered it on a sibling device
    if (fromSibling && hasGroup &&
            changeSet.has_updatermmember() &&
            changeSet.updatermmember().rmmember_size() == 1 &&
            changeSet.updatermmember().rmmember(0).user_id() == getOwnUser()) {

        const int32_t result = processLeaveGroup(groupId, getOwnUser());
        if (result != SUCCESS) {
            errorCode_ = result;
            errorInfo_ = "Sibling: cannot remove group.";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        groupCmdCallback_(leaveCommand(groupId, getOwnUser(), stamp));
        return result;
    }

    // Implicitly create a new group if it does not exist yet and we should add a member to it
    // Works the same for sibling devices and other member devices
    //
    // Note to handling of time stamp: a change set may contain more than one changes. To have
    // different time stamps for each change ZINA increments the micro second part by one after
    // processing of a change. Thus each callback data has a different time stamp. The drawback
    // of this solution is that a sender of change set must not send two or more message
    // with a change set during one clock tick

    if (!hasGroup && changeSet.has_updateaddmember() && changeSet.updateaddmember().addmember_size() > 0) {
        string callbackCmd;
        const int32_t result = insertNewGroup(groupId, changeSet, stamp, &callbackCmd);
        if (result != SUCCESS) {
            errorCode_ = result;
            errorInfo_ = "Cannot add new group.";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        stamp.tv_usec++;
        hasGroup = true;                // Now we have a group :-)
        groupCmdCallback_(callbackCmd);
    }

    // No such group but some group related update for it? Inform sender that we don't have
    // this group and ask to remove me. To send a "remove" when receiving an ACK for an unknown
    // group, leads to message loops
    if (!hasGroup ) {
        if (changeSet.has_updateavatar() || changeSet.has_updateburn() || changeSet.has_updatename()
            || changeSet.has_updatermmember()) {

            // If we don't know this group and asked to remove ourselves from it, just ignore it. It's
            // a loop breaker in case of unusual race conditions.
            if (changeSet.updatermmember().rmmember_size() == 1 &&
                changeSet.updatermmember().rmmember(0).user_id() == getOwnUser()) {
                return SUCCESS;
            }
            string sentTo = groupId + deviceId;
            for (const string &sent : sentRemoveTo) {
                if (sent == sentTo) {
                    LOGGER(INFO, __func__, " <-- unexpected group change set, already sent remove");
                    return SUCCESS;
                }
            }

            GroupUpdateRmMember *updateRmMember = ackRmSet->mutable_updatermmember();
            Member *member = updateRmMember->add_rmmember();
            member->set_user_id(getOwnUser());
            if (sentRemoveTo.size() > 30) {
                sentRemoveTo.pop_front();
            }
            sentRemoveTo.push_back(sentTo);
            LOGGER(INFO, __func__, " <-- unexpected group change set");
        }
    }
    else {
        string binDeviceId;
        makeBinaryDeviceId(deviceId, &binDeviceId);

        if (changeSet.acks_size() > 0) {
            // Process ACKs from partners and siblings
            const int32_t result = processAcks(changeSet, groupId, binDeviceId);
            if (result != SUCCESS) {
                return result;
            }
        }

        if (changeSet.has_updatename()) {
            // Update the group's name
            stamp.tv_usec++;
            const int32_t result = processUpdateName(changeSet.updatename(), groupId, stamp, ackRmSet);
            if (result != SUCCESS) {
                return result;
            }
        }
        if (changeSet.has_updateavatar()) {
            // Update the group's avatar info
            stamp.tv_usec++;
            const int32_t result = processUpdateAvatar(changeSet.updateavatar(), groupId, stamp, ackRmSet);
            if (result != SUCCESS) {
                return result;
            }
        }
        if (changeSet.has_updateburn()) {
            // Update the group's burn timer info
            stamp.tv_usec++;
            const int32_t result = processUpdateBurn(changeSet.updateburn(), groupId, stamp, ackRmSet);
            if (result != SUCCESS) {
                return result;
            }
        }
        if (changeSet.has_burnmessage()) {
            // burn a group's message (manual burn)
            const int32_t result = processBurnMessage(changeSet.burnmessage(), groupId, stamp, ackRmSet);
            if (result != SUCCESS) {
                return result;
            }
        }
        if ((changeSet.has_updateaddmember() && changeSet.updateaddmember().addmember_size() > 0) ||
            (changeSet.has_updatermmember() && changeSet.updatermmember().rmmember_size() > 0)) {

            stamp.tv_usec++;
            const int32_t result = processUpdateMembers(changeSet, groupId, stamp, ackRmSet);
            if (result != SUCCESS) {
                return result;
            }
        }
    }
    LOGGER(DEBUGGING, __func__, " <-- ");
    return SUCCESS;
}

int32_t AppInterfaceImpl::processAcks(const GroupChangeSet &changeSet, const string &groupId, const string &binDeviceId)
{
    LOGGER(DEBUGGING, __func__, " -->");

    // Clean old wait-for-ack records before processing ACKs. This enables proper cleanup of pending change sets
    time_t timestamp = time(nullptr) - MK_STORE_TIME;
    store_->cleanWaitAck(timestamp);

    const int32_t numAcks = changeSet.acks_size();

    for (int32_t i = 0; i < numAcks; i++) {
        const GroupUpdateAck &ack = changeSet.acks(i);

        const GroupUpdateType type = ack.type();
        const string &updateId = ack.update_id();
        store_->removeWaitAck(groupId, binDeviceId, updateId, type);

        // After removing an wait-for-ack record check if we still have an record for the (groupId, updateId)
        // tuple. If not then all devices sent an ack for the update types and we can remove the pending change set.
        int32_t result;
        const bool moreChangeSets = store_->hasWaitAckGroupUpdate(groupId, updateId, &result);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Error checking remaining group change sets";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        if (!moreChangeSets) {
            string key;
            key.assign(updateId).append(groupId);
            bool removed = removeFromPendingChangeSets(key);
            LOGGER(INFO, __func__, "Remove groupid from pending change set: ", groupId, ": ", removed, ": ", key);
        }
    }
    LOGGER(DEBUGGING, __func__, " <-- ");
    return SUCCESS;
}

static Ordering resolveConflict(const VectorClock<string> &remoteVc, const VectorClock<string> &localVc,
                         const string &updateIdRemote, const string &updateIdLocal)
{
    const int64_t remoteSum = remoteVc.sumOfValues();
    const int64_t localSum = localVc.sumOfValues();

    if (remoteSum == localSum) {
        return updateIdRemote > updateIdLocal ? After : Before;
    }
    return remoteSum > localSum ? After : Before;
}

int32_t AppInterfaceImpl::processUpdateName(const GroupUpdateSetName &changeSet, const string &groupId,
                                            const struct timeval& stamp, GroupChangeSet *ackSet)
{
    const string &updateIdRemote = changeSet.update_id();

    LOGGER(DEBUGGING, __func__, " --> ", updateIdRemote);

    VectorClock<string> remoteVc;
    deserializeVectorClock(changeSet.vclock(), &remoteVc);

    LocalVClock lvc;                    // the serialized proto-buffer representation
    VectorClock<string> localVc;

    int32_t result = readLocalVectorClock(*store_, groupId, GROUP_SET_NAME, &lvc);
    if (result == SUCCESS) {        // we may not yet have a vector clock for this group update type, thus deserialize on SUCCESS only
        deserializeVectorClock(lvc.vclock(), &localVc);
    }

    bool hasConflict = false;
    Ordering order = remoteVc.compare(localVc);
    if (order == Concurrent) {
        hasConflict = true;
        const string &updateIdLocal = lvc.update_id();
        order = resolveConflict(remoteVc, localVc, updateIdRemote, updateIdLocal);
    }

    // Remote clock is bigger than local, thus remote data is more recent than local data. Update our group
    // data, our local vector clock and return an ACK
    // In case of a conflict the conflict resolution favoured the remote data
    if (order == After) {
        const string &groupName = changeSet.name();
        result = store_->setGroupName(groupId, groupName);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Cannot update group name";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        // Serialize and store the remote vector clock as our new local vector clock because the remote clock
        // reflects the latest changes.
        lvc.set_update_id(updateIdRemote.data(), UPDATE_ID_LENGTH);
        serializeVectorClock(remoteVc, lvc.mutable_vclock());

        result = storeLocalVectorClock(*store_, groupId, GROUP_SET_NAME, lvc);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Group set name: Cannot store new local vector clock";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        GroupUpdateAck *ack = ackSet->add_acks();
        ack->set_update_id(updateIdRemote);
        ack->set_type(GROUP_SET_NAME);
        ack->set_result(hasConflict ? ACCEPTED_CONFLICT : ACCEPTED_OK);

        groupCmdCallback_(newGroupNameCommand(groupId, groupName, stamp));
        return SUCCESS;
    }
    GroupUpdateAck *ack = ackSet->add_acks();
    ack->set_update_id(updateIdRemote);
    ack->set_type(GROUP_SET_NAME);

    // The local data is more recent than the remote data. No need to change any data, just return an ACK
    // In case of a conflict the conflict resolution favoured the local data
    if (order == Before) {
        ack->set_result(hasConflict ? REJECTED_CONFLICT : REJECTED_PAST);
        return SUCCESS;
    }
    // The local data and the remote data are equal. No need to change any data, just return an ACK
    if (order == Equal) {
        ack->set_result(REJECTED_NOP);
        return SUCCESS;
    }
    return GENERIC_ERROR;
}

int32_t AppInterfaceImpl::processUpdateAvatar(const GroupUpdateSetAvatar &changeSet, const string &groupId,
                                              const struct timeval& stamp, GroupChangeSet *ackSet)
{
    LOGGER(DEBUGGING, __func__, " -->");

    const string &updateIdRemote = changeSet.update_id();

    VectorClock<string> remoteVc;
    deserializeVectorClock(changeSet.vclock(), &remoteVc);

    LocalVClock lvc;                    // the serialized proto-buffer representation
    VectorClock<string> localVc;

    int32_t result = readLocalVectorClock(*store_, groupId, GROUP_SET_AVATAR, &lvc);
    if (result == SUCCESS) {        // we may not yet have a vector clock for this group update type, thus deserialize on SUCCESS only
        deserializeVectorClock(lvc.vclock(), &localVc);
    }

    bool hasConflict = false;
    Ordering order = remoteVc.compare(localVc);
    if (order == Concurrent) {
        hasConflict = true;
        const string &updateIdLocal = lvc.update_id();
        order = resolveConflict(remoteVc, localVc, updateIdRemote, updateIdLocal);
    }

    // Remote clock is bigger than local, thus remote data is more recent than local data. Update our group
    // data, our local vector clock and return an ACK
    // In case of a conflict the conflict resolution favoured the remote data
    if (order == After) {
        const string &groupAvatar = changeSet.avatar();
        result = store_->setGroupAvatarInfo(groupId, groupAvatar);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Cannot update group avatar info";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        // Serialize and store the remote vector clock as our new local vector clock because the remote clock
        // reflects the latest changes.
        lvc.set_update_id(updateIdRemote.data(), UPDATE_ID_LENGTH);
        serializeVectorClock(remoteVc, lvc.mutable_vclock());

        result = storeLocalVectorClock(*store_, groupId, GROUP_SET_AVATAR, lvc);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Group set avatar: Cannot store new local vector clock";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        GroupUpdateAck *ack = ackSet->add_acks();
        ack->set_update_id(updateIdRemote);
        ack->set_type(GROUP_SET_AVATAR);
        ack->set_result(hasConflict ? ACCEPTED_CONFLICT : ACCEPTED_OK);

        groupCmdCallback_(newGroupAvatarCommand(groupId, groupAvatar, stamp));
        return SUCCESS;
    }
    GroupUpdateAck *ack = ackSet->add_acks();
    ack->set_update_id(updateIdRemote);
    ack->set_type(GROUP_SET_AVATAR);

    // The local data is more recent than the remote data. No need to change any data, just return an ACK
    // In case of a conflict the conflict resolution favoured the local data
    if (order == Before) {
        ack->set_result(hasConflict ? REJECTED_CONFLICT : REJECTED_PAST);
        return SUCCESS;
    }
    // The local data is more recent than the remote data (remote change was _before_ ours). No need to
    // change any data, just return an ACK
    if (order == Equal) {
        ack->set_result(REJECTED_NOP);
        return SUCCESS;
    }
    return GENERIC_ERROR;
}

int32_t AppInterfaceImpl::processUpdateBurn(const GroupUpdateSetBurn &changeSet, const string &groupId,
                                            const struct timeval& stamp, GroupChangeSet *ackSet)
{
    LOGGER(DEBUGGING, __func__, " -->");

    const string &updateIdRemote = changeSet.update_id();

    VectorClock<string> remoteVc;
    deserializeVectorClock(changeSet.vclock(), &remoteVc);

    LocalVClock lvc;                    // the serialized proto-buffer representation
    VectorClock<string> localVc;

    int32_t result = readLocalVectorClock(*store_, groupId, GROUP_SET_BURN, &lvc);
    if (result == SUCCESS) {        // we may not yet have a vector clock for this group update type, thus deserialize on SUCCESS only
        deserializeVectorClock(lvc.vclock(), &localVc);
    }

    bool hasConflict = false;
    Ordering order = remoteVc.compare(localVc);

    // The vector clocks are siblings, not descendent, thus we need to resolve the conflict
    if (order == Concurrent) {
        hasConflict = true;
        const string &updateIdLocal = lvc.update_id();
        order = resolveConflict(remoteVc, localVc, updateIdRemote, updateIdLocal);
    }

    // Remote clock is bigger than local, thus remote data is more recent than local data. Update our group
    // data, our local vector clock and return an ACK
    // In case of a conflict the conflict resolution favoured the remote data
    if (order == After) {
        const int64_t burnTime = changeSet.burn_ttl_sec();
        const int32_t burnMode = changeSet.burn_mode();
        result = store_->setGroupBurnTime(groupId, burnTime, burnMode);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Cannot update group avatar info";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        // Serialize and store the remote vector clock as our new local vector clock because the remote clock
        // reflects the latest changes.
        lvc.set_update_id(updateIdRemote.data(), UPDATE_ID_LENGTH);
        serializeVectorClock(remoteVc, lvc.mutable_vclock());

        result = storeLocalVectorClock(*store_, groupId, GROUP_SET_BURN, lvc);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Group set avatar: Cannot store new local vector clock";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        GroupUpdateAck *ack = ackSet->add_acks();
        ack->set_update_id(updateIdRemote);
        ack->set_type(GROUP_SET_BURN);
        ack->set_result(hasConflict ? ACCEPTED_CONFLICT : ACCEPTED_OK);

        groupCmdCallback_(newGroupBurnCommand(groupId, burnTime, burnMode, stamp));
        return SUCCESS;
    }
    GroupUpdateAck *ack = ackSet->add_acks();
    ack->set_update_id(updateIdRemote);
    ack->set_type(GROUP_SET_BURN);

    // The local data is more recent than the remote data. No need to change any data, just return an ACK
    // In case of a conflict the conflict resolution favoured the local data
    if (order == Before) {
        ack->set_result(hasConflict ? REJECTED_CONFLICT : REJECTED_PAST);
        return SUCCESS;
    }
    // The local data is more recent than the remote data (remote change was _before_ ours). No need to
    // change any data, just return an ACK
    if (order == Equal) {
        ack->set_result(REJECTED_NOP);
        return SUCCESS;
    }
    return GENERIC_ERROR;
}

int32_t AppInterfaceImpl::processBurnMessage(const GroupBurnMessage &changeSet, const string &groupId,
                                             const struct timeval& stamp, GroupChangeSet *ackSet)
{
    LOGGER(DEBUGGING, __func__, " -->");

    const ::google::protobuf::RepeatedPtrField< ::std::string>& msgIds = changeSet.msgid();
    const string& member = changeSet.member().user_id();
    const string &updateIdRemote = changeSet.update_id();

    GroupUpdateAck *ack = ackSet->add_acks();
    ack->set_update_id(updateIdRemote);
    ack->set_type(GROUP_BURN_MESSSAGE);

    groupCmdCallback_(groupBurnMsgCommand(groupId, msgIds, member, stamp));

    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::processUpdateMembers(const GroupChangeSet &changeSet, const string &groupId,
                                               const struct timeval& stamp, GroupChangeSet *ackSet) {

    // The function first processes the add member update, then remove member. It removes members
    // from the add member list if a member is also in the remove member update.
    list<string> addMembers;
    if (changeSet.has_updateaddmember()) {
        // We always send back an ACK
        GroupUpdateAck *ack = ackSet->add_acks();
        ack->set_update_id(changeSet.updateaddmember().update_id());
        ack->set_type(GROUP_ADD_MEMBER);
        ack->set_result(ACCEPTED_OK);

        const int32_t size = changeSet.updateaddmember().addmember_size();
        for (int32_t i = 0; i < size; i++) {
            const string &name = changeSet.updateaddmember().addmember(i).user_id();
            addMembers.push_back(name);
        }
    }

    list<string> rmMembers;
    if (changeSet.has_updatermmember()) {
        // We always send back an ACK
        GroupUpdateAck *ack = ackSet->add_acks();
        ack->set_update_id(changeSet.updatermmember().update_id());
        ack->set_type(GROUP_REMOVE_MEMBER);
        ack->set_result(ACCEPTED_OK);

        const int32_t size = changeSet.updatermmember().rmmember_size();
        for (int32_t i = 0; i < size; i++) {
            const string &name = changeSet.updatermmember().rmmember(i).user_id();
            rmMembers.push_back(name);

            if (addMembers.empty()) {
                continue;
            }
            auto end = addMembers.end();
            for (auto it = addMembers.begin(); it != end; ++it) {
                if (*it == name) {
                    addMembers.erase(it);
                    break;
                }
            }
        }
    }

    // Now iterate over the add member list, check existence of the member. If we already
    // know the member, remove it from the add member list. Otherwise add it to the member table
    auto end = addMembers.end();
    for (auto it = addMembers.begin(); it != end; ) {
        int32_t result;
        bool isMember = store_->isMemberOfGroup(groupId, *it, &result);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Cannot check group membership";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        // already a member, remove from list that's being prepared for UI callback
        if (isMember) {
            it = addMembers.erase(it);
            continue;
        }
        result = store_->insertMember(groupId, *it);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Cannot add new group member";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        ++it;
    }

    // Now iterate over the remove member list, check existence of the member. If we don't
    // know the member, remove it from the remove member list. Otherwise remove it from the member table
    end = rmMembers.end();
    for (auto it = rmMembers.begin(); it != end; ) {
        int32_t result;
        bool isMember = store_->isMemberOfGroup(groupId, *it, &result);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Cannot check group membership";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        // Unknown member, no need to remove it from member table, also remove from list that's
        // being prepared for UI callback
        if (!isMember) {
            it = rmMembers.erase(it);
            continue;
        }
        result = store_->deleteMember(groupId, *it);
        if (SQL_FAIL(result)) {
            errorCode_ = result;
            errorInfo_ = "Cannot remove group member";
            LOGGER(ERROR, __func__, errorInfo_, "code: ", result);
            return result;
        }
        ++it;
    }
    if (!addMembers.empty()) {
        groupCmdCallback_(prepareMemberList(groupId, addMembers, ADD_MEMBERS, stamp));
    }

    if (!rmMembers.empty()) {
        groupCmdCallback_(prepareMemberList(groupId, rmMembers, RM_MEMBERS, stamp));
    }
    return SUCCESS;
}

int32_t AppInterfaceImpl::sendGroupMessageToSingleUserDeviceNoCS(const string &groupId, const string &userId,
                                                                 const string &deviceId, const string &attributes,
                                                                 const string &msg, int32_t msgType)
{
    LOGGER(DEBUGGING, __func__, " -->");

    JsonUnique sharedRoot(!attributes.empty() ? cJSON_Parse(attributes.c_str()) : cJSON_CreateObject());
    cJSON* root = sharedRoot.get();

    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    CharUnique out(cJSON_PrintUnformatted(root));
    string newAttributes(out.get());

    const string msgId = generateMsgIdTime();

    queueMessageToSingleUserDevice(userId, msgId, deviceId, Empty, newAttributes, msg, Empty, msgType, false, NoAction);

    LOGGER(DEBUGGING, __func__, " <-- ");
    return SUCCESS;
}

void AppInterfaceImpl::clearGroupData()
{
    LOGGER(DEBUGGING, __func__, " --> ");
    list<JsonUnique> groups;
    store_->listAllGroups(groups);

    for (auto& group : groups) {
        string groupId(Utilities::getJsonString(group.get(), GROUP_ID, ""));
        store_->deleteAllMembers(groupId);
        store_->deleteGroup(groupId);
        store_->deleteVectorClocks(groupId);
    }
    LOGGER(DEBUGGING, __func__, " <-- ");
}


int32_t AppInterfaceImpl::deleteGroupAndMembers(string const& groupId)
{
    LOGGER(DEBUGGING, __func__, " --> ");

    int32_t result = store_->deleteAllMembers(groupId);
    if (SQL_FAIL(result)) {
        LOGGER(ERROR, __func__, "Could not delete all members of group: ", groupId, ", SQL code: ", result);
        // Try to deactivate group at least
        store_->clearGroupAttribute(groupId, ACTIVE);
        store_->setGroupAttribute(groupId, INACTIVE);
        return GROUP_ERROR_BASE + result;
    }
    result = store_->deleteGroup(groupId);
    if (SQL_FAIL(result)) {
        LOGGER(ERROR, __func__, "Could not delete group: ", groupId, ", SQL code: ", result);
        // Try to deactivate group at least
        store_->clearGroupAttribute(groupId, ACTIVE);
        store_->setGroupAttribute(groupId, INACTIVE);
        return GROUP_ERROR_BASE + result;
    }
    LOGGER(DEBUGGING, __func__, " <-- ");
    return SUCCESS;
}

// Insert data of a new group into the database. This function also adds myself as a member to the
// new group.
int32_t AppInterfaceImpl::insertNewGroup(const string &groupId, const GroupChangeSet &changeSet, const struct timeval& stamp, string *callbackCmd)
{
    LOGGER(DEBUGGING, __func__, " --> ");
    const string &groupName = changeSet.has_updatename() ? changeSet.updatename().name() : Empty;

    int32_t sqlResult = store_->insertGroup(groupId, groupName, getOwnUser(), Empty, MAXIMUM_GROUP_SIZE);
    if (SQL_FAIL(sqlResult)) {
        return GROUP_ERROR_BASE + sqlResult;
    }

    // Add myself to the new group, this saves us a "send to sibling" group function.
    sqlResult = store_->insertMember(groupId, getOwnUser());
    if (SQL_FAIL(sqlResult)) {
        return GROUP_ERROR_BASE + sqlResult;
    }
    if (callbackCmd != nullptr) {
        callbackCmd->assign(newGroupCommand(groupId, MAXIMUM_GROUP_SIZE, stamp));
    }

    LOGGER(DEBUGGING, __func__, " <-- ");
    return SUCCESS;
}
