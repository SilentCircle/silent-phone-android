/*
Copyright 2017 Silent Circle, LLC

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
// Created by werner on 30.01.17.
//

#include <cryptcommon/ZrtpRandom.h>
#include "AppInterfaceImpl.h"

#include "GroupProtocol.pb.h"
#include "../util/b64helper.h"
#include "../vectorclock/VectorHelper.h"
#include "JsonStrings.h"
#include "../util/Utilities.h"

using namespace std;
using namespace zina;
using namespace vectorclock;

typedef shared_ptr<GroupChangeSet> PtrChangeSet;

static mutex currentChangeSetLock;
static map<string, PtrChangeSet> currentChangeSets;

// The key in this map is: updateId || groupId  (|| means concatenate)
// This map stores change sets (one per group) which are waiting for ACKs
static map<string, PtrChangeSet> pendingChangeSets;

// Update-id is just some random data, guarded by "update in progress" flag
static uint8_t updateIdGlobal[UPDATE_ID_LENGTH];
static bool updateInProgress = false;

/* ***************************************************************************
 * Static helper functions to manipulate change set, etc
 *
 *************************************************************************** */
static bool addNewGroupToChangeSet(const string &groupId)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    auto changeSet = make_shared<GroupChangeSet>();

    return currentChangeSets.insert(pair<string, PtrChangeSet >(groupId, changeSet)).second;
}

#ifdef UNITTESTS
PtrChangeSet getPendingGroupChangeSet(const string &groupId, SQLiteStoreConv &store);
bool removeGroupFromPendingChangeSet(const string &groupId)
{
    return (pendingChangeSets.erase(groupId) == 1);
}
#else
static
#endif
PtrChangeSet getPendingGroupChangeSet(const string &groupId, SQLiteStoreConv &store)
{
    // Check if the group's pending change set is cached
    auto found = pendingChangeSets.find(groupId);
    if (found != pendingChangeSets.end()) {
        return found->second;
    }

    // Not cached, get it from persistent storage and cache it
    string changeSetSerialized;
    store.getGroupChangeSet(groupId, &changeSetSerialized);
    if (!changeSetSerialized.empty()) {
        auto changeSet = make_shared<GroupChangeSet>();
        changeSet->ParseFromString(changeSetSerialized);
        pendingChangeSets.insert(pair<string, PtrChangeSet >(groupId, changeSet));
        return changeSet;
    }

    return PtrChangeSet();
}

static void removeGroupFromChangeSet(const string &groupId)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    currentChangeSets.erase(groupId);
}

// Returns pointer to a group's change set class
//
// Return an empty if the group is not valid.
// Function assumes the lock is set
// make it visible for unittests
#ifdef UNITTESTS
PtrChangeSet getCurrentGroupChangeSet(const string &groupId, SQLiteStoreConv &store);
#else
static
#endif
PtrChangeSet getCurrentGroupChangeSet(const string &groupId, SQLiteStoreConv &store)
{
    auto it = currentChangeSets.find(groupId);
    if (it != currentChangeSets.end()) {
        return it->second;
    }
    // no group change set yet. Check if we really have the group
    if (!store.hasGroup(groupId) || ((store.getGroupAttribute(groupId).first & ACTIVE) != ACTIVE)) {
        return PtrChangeSet();
    }

    // Yes, we have this group, create a change set, insert into map, return the pointer
    auto changeSet = make_shared<GroupChangeSet>();
    if (!currentChangeSets.insert(pair<string, PtrChangeSet >(groupId, changeSet)).second) {
        return PtrChangeSet();
    }
    return changeSet;
}

// Get the current change set for the group, return an empty Ptr in no active set available
static PtrChangeSet getGroupChangeSet(const string &groupId)
{
    auto it = currentChangeSets.find(groupId);
    if (it != currentChangeSets.end()) {
        return it->second;
    }
    return PtrChangeSet();
}

// Sets name only. We add the vector clock later, just before sending out the change
// Overwrites an existing name update
static bool setGroupNameToChangeSet(const string &groupId, const string &name, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    // get mutable pointer
    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    changeSet->mutable_updatename()->set_name(name);

    return true;
}

static bool removeGroupNameFromChangeSet(const string &groupId, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    // get mutable pointer
    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    GroupUpdateSetName *oldName = changeSet->release_updatename();
    delete oldName;

    return true;
}

// Sets avatar info only. We add the vector clock later, just before sending out the change
// Overwrite an existing avatar update
static bool setGroupAvatarToChangeSet(const string &groupId, const string &avatar, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    changeSet->mutable_updateavatar()->set_avatar(avatar);

    return true;
}

static bool removeGroupAvatarFromChangeSet(const string &groupId, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    GroupUpdateSetAvatar *oldAvatar = changeSet->release_updateavatar();
    delete oldAvatar;

    return true;
}

// Sets burn info only. We add the vector clock later, just before sending out the change
// Overwrite an existing avatar update
static bool setGroupBurnToChangeSet(const string &groupId, uint64_t burn, GroupUpdateSetBurn_BurnMode mode, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    // Proto buffer implementation takes ownership of pointer, also releases an already set update message
    changeSet->mutable_updateburn()->set_burn_mode(mode);
    changeSet->mutable_updateburn()->set_burn_ttl_sec(burn);

    return true;
}

// This function removes an remove member from the group update
// Function assumes the change set is locked
static bool removeRmNameFromChangeSet(PtrChangeSet& changeSet, const string &name)
{
    if (!changeSet->has_updatermmember()) {
        return true;
    }
    GroupUpdateRmMember *updateRmMember = changeSet->mutable_updatermmember();
    int32_t numberNames = updateRmMember->rmmember_size();

    // Search for a name and remove it. Because repeated fields do not provide
    // a direct Remove(index) we first swap the found element with the last element
    // and then remove the last element.
    for (int32_t i = 0; i < numberNames; ++i) {
        if (name == updateRmMember->rmmember(i).user_id()) {
            updateRmMember->mutable_rmmember()->SwapElements(i, numberNames-1);
            updateRmMember->mutable_rmmember()->RemoveLast();
            break;
        }
    }
    return true;
}
static bool removeRmNameFromChangeSet(const string &groupId, const string &name, SQLiteStoreConv &store)
{
    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    return removeRmNameFromChangeSet(changeSet, name);
}

// Function checks for duplicates and ignores them, otherwise adds the name to the group update
// assumes the change set is locked
static bool addAddNameToChangeSet(PtrChangeSet& changeSet, const string &name)
{

    GroupUpdateAddMember *updateAddMember = changeSet->mutable_updateaddmember();
    int32_t numberNames = updateAddMember->addmember_size();

    // Check and silently ignore duplicate names
    for (int i = 0; i < numberNames; i++) {
        if (name == updateAddMember->addmember(i).user_id()) {
            return true;
        }
    }
    Member *member = updateAddMember->add_addmember();
    member->set_user_id(name);
    return true;

}

// Thus function adds an add member to the change set, collapsed into a repeated GROUP_ADD_MEMBER update
// message. The function silently ignores duplicate names.
// adding a new member to the change set checks the remove update and removes an entry with the
// same name if found
static bool addAddNameToChangeSet(const string &groupId, const string &name, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    // In case we added this name to the current change set, remove it, don't need to remove
    // it. However, add it the the change set to add names.
    removeRmNameFromChangeSet(changeSet, name);
    return addAddNameToChangeSet(changeSet, name);
}

// Thus function removes an add member from the change set
// Function assumes the change set is locked
static bool removeAddNameFromChangeSet(PtrChangeSet& changeSet, const string &name)
{
    // If update has no add member yet, cannot remove anything return false, done
    if (!changeSet->has_updateaddmember()) {
        return true;
    }
    GroupUpdateAddMember *updateAddMember = changeSet->mutable_updateaddmember();
    int32_t numberNames = updateAddMember->addmember_size();

    // Search for a name and remove it. Because repeated fields do not provide
    // a direct Remove(index) we first swap the found element with the last element
    // and then remove the last element.
    for (int32_t i = 0; i < numberNames; ++i) {
        if (name == updateAddMember->addmember(i).user_id()) {
            updateAddMember->mutable_addmember()->SwapElements(i, numberNames-1);
            updateAddMember->mutable_addmember()->RemoveLast();
            break;
        }
    }
    return true;
}

static bool removeAddNameFromChangeSet(const string &groupId, const string &name, SQLiteStoreConv &store)
{
    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    // In case we added this name to the current changes set, remove it, don't need to add
    // it. However, add it the the change set to remove names.
    return removeAddNameFromChangeSet(changeSet, name);
}

// Function checks for duplicates and ignores them, otherwise adds the name to the group update
// assumes the change set is locked
static bool addRemoveNameToChangeSet(PtrChangeSet& changeSet, const string &name)
{
    GroupUpdateRmMember *updateRmMember = changeSet->mutable_updatermmember();
    int32_t numberNames = updateRmMember->rmmember_size();
    // Check and silently ignore duplicate names
    for (int i = 0; i < numberNames; i++) {
        if (name == updateRmMember->rmmember(i).user_id()) {
            return true;
        }
    }
    Member *member = updateRmMember->add_rmmember();
    member->set_user_id(name);
    return true;
}

// This function adds a remove member, collapsed into a repeated GROUP_REMOVE_MEMBER update message
// The function silently ignores duplicate names.
// adding a new member to the change set checks the add update and removes an entry with the
// same name if found
static bool addRemoveNameToChangeSet(const string &groupId, const string &name, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    removeAddNameFromChangeSet(changeSet, name);
    return addRemoveNameToChangeSet(changeSet, name);
}

static bool setMsgBurnToChangeSet(const string &groupId, const vector<string>& msgIds, const string &name, SQLiteStoreConv &store)
{
    unique_lock<mutex> lck(currentChangeSetLock);

    // get mutable pointer
    auto changeSet = getCurrentGroupChangeSet(groupId, store);
    if (!changeSet) {
        return false;
    }
    for (auto const& msgId : msgIds) {
        changeSet->mutable_burnmessage()->add_msgid(msgId);
    }
    changeSet->mutable_burnmessage()->mutable_member()->set_user_id(name);

    return true;
}

static int32_t prepareChangeSetClocks(const string &groupId, const string &binDeviceId, PtrChangeSet& changeSet,
                                      GroupUpdateType type, const uint8_t *updateId, SQLiteStoreConv &store,
                                      bool updateClocks = true)
{
    LocalVClock lvc;
    VectorClock<string> vc;

    int32_t result = readLocalVectorClock(store, groupId, type, &lvc);
    if (result == SUCCESS) {        // we may not yet have a vector clock for this group update type, thus deserialize on SUCCESS only
        deserializeVectorClock(lvc.vclock(), &vc);
    }

    // In a first step read the local vector clock for this (group id, update type) tuple
    // increment the clock for our device.
    //
    // In the second step set this new clock to the appropriate update change set.
    if (updateClocks) {
        vc.incrementNodeClock(binDeviceId);
    }

    switch (type) {
        case GROUP_SET_NAME:
            changeSet->mutable_updatename()->set_update_id(updateId, UPDATE_ID_LENGTH);
            serializeVectorClock(vc, changeSet->mutable_updatename()->mutable_vclock());
            break;

        case GROUP_SET_AVATAR:
            changeSet->mutable_updateavatar()->set_update_id(updateId, UPDATE_ID_LENGTH);
            serializeVectorClock(vc, changeSet->mutable_updateavatar()->mutable_vclock());
            break;

        case GROUP_SET_BURN:
            changeSet->mutable_updateburn()->set_update_id(updateId, UPDATE_ID_LENGTH);
            serializeVectorClock(vc, changeSet->mutable_updateburn()->mutable_vclock());
            break;

        default:
            return ILLEGAL_ARGUMENT;

    }
    if (updateClocks) {
        // Now update and persist the local vector clock
        lvc.set_update_id(updateId, UPDATE_ID_LENGTH);
        serializeVectorClock(vc, lvc.mutable_vclock());
        return storeLocalVectorClock(store, groupId, type, lvc);
    }
    return SUCCESS;
}

static int32_t serializeChangeSet(PtrChangeSet& changeSet, cJSON *root, string *newAttributes)
{
    string serialized;
    if (!changeSet->SerializeToString(&serialized)) {
        return GENERIC_ERROR;
    }
    auto b64Size = static_cast<size_t>(serialized.size() * 2);
    unique_ptr<char[]> b64Buffer(new char[b64Size]);
    if (b64Encode(reinterpret_cast<const uint8_t *>(serialized.data()), serialized.size(), b64Buffer.get(), b64Size) == 0) {
        return GENERIC_ERROR;
    }
    cJSON_AddStringToObject(root, GROUP_CHANGE_SET, b64Buffer.get());
    CharUnique out(cJSON_PrintUnformatted(root));
    newAttributes->assign(out.get());
    return SUCCESS;
}

static int32_t addMissingMetaData(PtrChangeSet changeSet, const string& groupId, const string& binDeviceId, const uint8_t *updateId, SQLiteStoreConv &store)
{
    int32_t result;
    auto groupShared = store.listGroup(groupId, &result);
    auto group = groupShared.get();

    if (!changeSet->has_updatename()) {
        string name = Utilities::getJsonString(group, GROUP_NAME, "");
        changeSet->mutable_updatename()->set_name(name);
        result = prepareChangeSetClocks(groupId, binDeviceId, changeSet, GROUP_SET_NAME, updateId, store, false);
        if (result < 0) {
            return result;
        }
    }

    if (!changeSet->has_updateavatar()) {
        string avatar = Utilities::getJsonString(group, GROUP_AVATAR, "");
        changeSet->mutable_updateavatar()->set_avatar(avatar);
        result = prepareChangeSetClocks(groupId, binDeviceId, changeSet, GROUP_SET_AVATAR, updateId, store, false);
        if (result < 0) {
            return result;
        }
    }
    if (!changeSet->has_updateburn()) {
        auto sec = static_cast<uint64_t>(Utilities::getJsonInt(group, GROUP_BURN_SEC, 0));
        int32_t mode = Utilities::getJsonInt(group, GROUP_BURN_MODE, 0);
        changeSet->mutable_updateburn()->set_burn_ttl_sec(sec);
        changeSet->mutable_updateburn()->set_burn_mode((GroupUpdateSetBurn_BurnMode)mode);
        result = prepareChangeSetClocks(groupId, binDeviceId, changeSet, GROUP_SET_BURN, updateId, store, false);
        if (result < 0) {
            return result;
        }
    }
    return SUCCESS;
}

static int32_t addExistingMembers(PtrChangeSet changeSet, const string &groupId, SQLiteStoreConv &store)
{
    list<JsonUnique> members;
    int32_t result = store.getAllGroupMembers(groupId, members);
    if (SQL_FAIL(result)) {
        return result;
    }
    for (auto& member: members) {
        string name(Utilities::getJsonString(member.get(), MEMBER_ID, ""));
        addAddNameToChangeSet(changeSet, name);
    }
    return SUCCESS;
}

/* ***************************************************************************
 * Public Instance functions
 *
 *************************************************************************** */

string AppInterfaceImpl::createNewGroup(const string& groupName, string& groupDescription) {
    LOGGER(DEBUGGING, __func__, " -->");

    uuid_t groupUuid = {0};
    uuid_string_t uuidString = {0};

    uuid_generate_time(groupUuid);
    uuid_unparse(groupUuid, uuidString);
    string groupId(uuidString);

    addNewGroupToChangeSet(groupId);
    addAddNameToChangeSet(groupId, getOwnUser(), *store_);
    if (!groupName.empty()) {
        setGroupNameToChangeSet(groupId, groupName, *store_);
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return groupId;
}

int32_t AppInterfaceImpl::addUser(const string& groupUuid, const string& userId)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupUuid.empty() || userId.empty()) {
        return DATA_MISSING;
    }
    if (userId == getOwnUser()) {
        return ILLEGAL_ARGUMENT;
    }
    if (!addAddNameToChangeSet(groupUuid, userId, *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::removeUserFromAddUpdate(const string& groupUuid, const string& userId)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupUuid.empty() || userId.empty()) {
        return DATA_MISSING;
    }

    unique_lock<mutex> lck(currentChangeSetLock);
    if (!removeAddNameFromChangeSet(groupUuid, userId, *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::leaveGroup(const string& groupId) {
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty()) {
        return DATA_MISSING;
    }
    removeGroupFromChangeSet(groupId);          // when leaving group - no other changes allowed
    if (!addRemoveNameToChangeSet(groupId, getOwnUser(), *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    applyGroupChangeSet(groupId);

    processLeaveGroup(groupId, getOwnUser());

    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::processLeaveGroup(const string &groupId, const string &userId) {

    LOGGER(DEBUGGING, __func__, " --> ");

    // The leave/not user command from a sibling, thus remove group completely
    // No wait-for-ack, ignore any ACKs from members of the group, we are gone
    store_->removeWaitAckWithGroup(groupId);

    // Delete all vector clocks of this group
    store_->deleteVectorClocks(groupId);

    // Remove group's pending change set
    auto end = pendingChangeSets.end();
    for (auto it = pendingChangeSets.begin(); it != end; ) {
        string oldGroupId = it->first.substr(UPDATE_ID_LENGTH);
        if (oldGroupId != groupId) {
            ++it;
            continue;
        }
        it = pendingChangeSets.erase(it);
    }
    return deleteGroupAndMembers(groupId);
}

int32_t AppInterfaceImpl::removeUser(const string& groupId, const string& userId, bool allowOwnUser)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty() || userId.empty()) {
        return DATA_MISSING;
    }

    if (!allowOwnUser && userId == getOwnUser()) {
        return ILLEGAL_ARGUMENT;
    }
    if (!addRemoveNameToChangeSet(groupId, userId, *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::removeUserFromRemoveUpdate(const string& groupUuid, const string& userId)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupUuid.empty() || userId.empty()) {
        return DATA_MISSING;
    }

    unique_lock<mutex> lck(currentChangeSetLock);
    if (!removeRmNameFromChangeSet(groupUuid, userId, *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;

}

int32_t AppInterfaceImpl::setGroupName(const string& groupId, const string* groupName)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty()) {
        return DATA_MISSING;
    }

    if (groupName == nullptr) {
        if (!removeGroupNameFromChangeSet(groupId, *store_)) {
            return NO_SUCH_ACTIVE_GROUP;
        }
    }
    else if (!setGroupNameToChangeSet(groupId, *groupName, *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::setGroupBurnTime(const string& groupId, uint64_t burnTime, int32_t mode)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty()) {
        return DATA_MISSING;
    }

    if (mode == 0 || !GroupUpdateSetBurn_BurnMode_IsValid(mode)) {
        return ILLEGAL_ARGUMENT;
    }
    if (!setGroupBurnToChangeSet(groupId, burnTime, (GroupUpdateSetBurn_BurnMode) mode, *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::setGroupAvatar(const string& groupId, const string* avatar)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty()) {
        return DATA_MISSING;
    }

    if (avatar == nullptr) {
        if (!removeGroupAvatarFromChangeSet(groupId, *store_)) {
            return NO_SUCH_ACTIVE_GROUP;
        }
    }
    else if (!setGroupAvatarToChangeSet(groupId, *avatar, *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::burnGroupMessage(const string& groupId, const vector<string>& messageIds)
{
    LOGGER(DEBUGGING, __func__, " -->");
    if (groupId.empty() || messageIds.empty()) {
        return DATA_MISSING;
    }
    if (!setMsgBurnToChangeSet(groupId, messageIds, getOwnUser(), *store_)) {
        return NO_SUCH_ACTIVE_GROUP;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::cancelGroupChangeSet(const string& groupId)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty()) {
        return DATA_MISSING;
    }
    removeGroupFromChangeSet(groupId);
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::applyGroupChangeSet(const string& groupId)
{
    string msgId = generateMsgIdTime();
    int32_t result = sendGroupMessage(createMessageDescriptor(groupId, msgId), Empty, Empty);
    return result == OK ? SUCCESS : result;
}


/* ***************************************************************************
 * Private instance functions, visible for unit tests
 *
 *************************************************************************** */

int32_t AppInterfaceImpl::prepareChangeSetSend(const string &groupId) {
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty()) {
        return DATA_MISSING;
    }
    errorCode_ = SUCCESS;
    unique_lock<mutex> lck(currentChangeSetLock);

    // Get an active change set, if none then nothing to do, return success
    auto changeSet = getGroupChangeSet(groupId);
    if (!changeSet) {
        return SUCCESS;
    }

    // Still creating and sending a previous change set, don't mix data
    if (updateInProgress) {
        return GROUP_UPDATE_RUNNING;
    }
    updateInProgress = true;
    ZrtpRandom::getRandomData(updateIdGlobal, sizeof(updateIdGlobal));

    int32_t returnCode;

    // Check if this change set is for a new group
    if (!store_->hasGroup(groupId)) {
        struct timeval emptyTime {0};
        returnCode = insertNewGroup(groupId, *changeSet, emptyTime, nullptr);
        if (returnCode < 0) {
            errorCode_ = returnCode;
            updateInProgress = true;
            return returnCode;
        }
    }
    string binDeviceId;
    makeBinaryDeviceId(getOwnDeviceId(), &binDeviceId);

    // Now check each update: add vector clocks, update id, then store the new data in group and member tables
    if (changeSet->has_updatename()) {
        returnCode = prepareChangeSetClocks(groupId, binDeviceId, changeSet, GROUP_SET_NAME, updateIdGlobal, *store_);
        if (returnCode < 0) {
            errorCode_ = returnCode;
            updateInProgress = true;
            return returnCode;
        }
        store_->setGroupName(groupId, changeSet->updatename().name());
    }
    if (changeSet->has_updateavatar()) {
        returnCode = prepareChangeSetClocks(groupId, binDeviceId, changeSet, GROUP_SET_AVATAR, updateIdGlobal, *store_);
        if (returnCode < 0) {
            errorCode_ = returnCode;
            updateInProgress = true;
            return returnCode;
        }
        store_->setGroupAvatarInfo(groupId, changeSet->updateavatar().avatar());
    }
    if (changeSet->has_updateburn()) {
        returnCode = prepareChangeSetClocks(groupId, binDeviceId, changeSet, GROUP_SET_BURN, updateIdGlobal, *store_);
        if (returnCode < 0) {
            errorCode_ = returnCode;
            updateInProgress = true;
            return returnCode;
        }
        store_->setGroupBurnTime(groupId, changeSet->updateburn().burn_ttl_sec(), changeSet->updateburn().burn_mode());
    }

    // Burn message change has no clock, we also do not collapse it
    if (changeSet->has_burnmessage()) {
        changeSet->mutable_burnmessage()->set_update_id(updateIdGlobal, UPDATE_ID_LENGTH);
    }

    if (changeSet->has_updateaddmember()) {
        changeSet->mutable_updateaddmember()->set_update_id(updateIdGlobal, UPDATE_ID_LENGTH);
        const int32_t size = changeSet->updateaddmember().addmember_size();
        for (int i = 0; i < size; i++) {
            const string &userId = changeSet->updateaddmember().addmember(i).user_id();
            if (!store_->isMemberOfGroup(groupId, userId)) {
                store_->insertMember(groupId, userId);
            }
        }
        // A new member needs to know the group metadata
        addMissingMetaData(changeSet, groupId, binDeviceId, updateIdGlobal, *store_);

        // A new member also needs knowledge of existing members. The function adds these, filters duplicates.
        addExistingMembers(changeSet, groupId, *store_);
    }
    if (changeSet->has_updatermmember()) {
        changeSet->mutable_updatermmember()->set_update_id(updateIdGlobal, UPDATE_ID_LENGTH);
        const int32_t size = changeSet->updatermmember().rmmember_size();
        for (int i = 0; i < size; i++) {
            const string &userId = changeSet->updatermmember().rmmember(i).user_id();
            // If removing myself then don't update DB, this is part of the leaveGroup function above.
            if (userId == getOwnUser()) {
                continue;
            }
            store_->deleteMember(groupId, userId);
        }
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

int32_t AppInterfaceImpl::createChangeSetDevice(const string &groupId, const string &deviceId, const string &attributes, string *newAttributes)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (groupId.empty() || deviceId.empty()) {
        return DATA_MISSING;
    }

    // The attributes string has a serialized change set already, don't process and add the current change set
    // This may happen if ZINA sends an ACK set back to a device
    JsonUnique sharedRoot(!attributes.empty() ? cJSON_Parse(attributes.c_str()) : cJSON_CreateObject());
    cJSON* root = sharedRoot.get();

    if (Utilities::hasJsonKey(root, GROUP_CHANGE_SET)) {
        return SUCCESS;
    }
    unique_lock<mutex> lck(currentChangeSetLock);

    PtrChangeSet changeSet;

    if (updateInProgress) {
        changeSet = getGroupChangeSet(groupId);
        if (!changeSet) {
            return GROUP_UPDATE_INCONSISTENT;
        }
        // Do we have any updates? If not, remove from current change set map and just return
        if (!changeSet->has_updatename() && !changeSet->has_updateavatar() && !changeSet->has_updateburn()
            && !changeSet->has_updateaddmember() && !changeSet->has_updatermmember() && !changeSet->has_burnmessage()) {
            removeGroupFromChangeSet(groupId);
            return SUCCESS;
        }
    }
    else {
        changeSet = getPendingGroupChangeSet(groupId, *store_);
        if (!changeSet) {
            return SUCCESS;
        }
        // Resend a change set only if a device has pending ACKs for this group.
        return store_->hasWaitAckGroupDevice(groupId, deviceId, nullptr) ?
               serializeChangeSet(changeSet, root, newAttributes) : SUCCESS;
    }

    string binDeviceId;
    makeBinaryDeviceId(deviceId, &binDeviceId);

    string updateIdString(reinterpret_cast<const char*>(updateIdGlobal), UPDATE_ID_LENGTH);

    auto oldChangeSet = getPendingGroupChangeSet(groupId, *store_);
    if (oldChangeSet) {

        // Collapse older add/remove member group updates into the current one.
        // If the old change set has add new member _and_ the device has not ACK'd it, copy
        // the old member into current change set.
        // Thus if at least one device has not ACK'ed the previous changes then the new changes
        // get these changes as well. If all devices ACK'ed the previous changes, then the new
        // change set will not get the old changes.
        if (oldChangeSet->has_updateaddmember() &&
                store_->hasWaitAck(groupId, binDeviceId, oldChangeSet->updateaddmember().update_id(), GROUP_ADD_MEMBER,
                                   nullptr)) {
            // Use the own addAddName function: skips duplicate names, checks the remove member data
            const int32_t size = oldChangeSet->updateaddmember().addmember_size();
            for (int i = 0; i < size; i++) {
                addAddNameToChangeSet(changeSet, oldChangeSet->updateaddmember().addmember(i).user_id());
            }
        }

        if (oldChangeSet->has_updatermmember() &&
                store_->hasWaitAck(groupId, binDeviceId, oldChangeSet->updatermmember().update_id(), GROUP_REMOVE_MEMBER,
                                   nullptr)) {
            // Use the own addRemoveName function: skips duplicate names, checks the add member data
            const int32_t size = oldChangeSet->updatermmember().rmmember_size();
            for (int i = 0; i < size; i++) {
                addRemoveNameToChangeSet(changeSet, oldChangeSet->updatermmember().rmmember(i).user_id());
            }
        }
    }

    // We may now have an add member update: may have added names from old change set, thus add
    // meta data if necessary.
    if (changeSet->has_updateaddmember()) {
        addMissingMetaData(changeSet, groupId, binDeviceId, updateIdGlobal, *store_);
    }

    int32_t result = serializeChangeSet(changeSet, root, newAttributes);
    if (result != SUCCESS) {
        errorCode_ = result;
        return result;
    }

    // Add WaitForAck records for the enw updates, remove old WaitForAck records for
    // attributes and data that's collapsed into the new change set.

    // Because we send a new group update we can remove older group updates from wait-for-ack.
    // The recent update overwrites older updates. ZINA ignores ACKs for the older updates.
    // Then store a new wait-for-ack record with the current update id.
    if (changeSet->has_updatename()) {
        store_->removeWaitAckWithType(groupId, binDeviceId, GROUP_SET_NAME);
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_SET_NAME);
    }
    if (changeSet->has_updateavatar()) {
        store_->removeWaitAckWithType(groupId, binDeviceId, GROUP_SET_AVATAR);
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_SET_AVATAR);
    }
    if (changeSet->has_updateburn()) {
        store_->removeWaitAckWithType(groupId, binDeviceId, GROUP_SET_BURN);
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_SET_BURN);
    }

    // Wait for ACK for each message burn, we don't collapse message burn changes because
    // this does not overwrite older burn message commands
    if (changeSet->has_burnmessage()) {
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_BURN_MESSSAGE);
    }

    // Add wait-for-ack records for add/remove group updates, remove old records.
    // Names are collapsed into new change set.
    if (changeSet->has_updateaddmember()) {
        store_->removeWaitAckWithType(groupId, binDeviceId, GROUP_ADD_MEMBER);
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_ADD_MEMBER);
    }
    if (changeSet->has_updatermmember()) {
        store_->removeWaitAckWithType(groupId, binDeviceId, GROUP_REMOVE_MEMBER);
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_REMOVE_MEMBER);
    }

    LOGGER(DEBUGGING, __func__, " <-- ");
    return result;
}

void AppInterfaceImpl::groupUpdateSendDone(const string& groupId)
{
    LOGGER(DEBUGGING, __func__, " -->");

    unique_lock<mutex> lck(currentChangeSetLock);

    if (!updateInProgress) {
        return;
    }
    memset(updateIdGlobal, 0, sizeof(updateIdGlobal));

    // We've sent out the new change set to all devices. This change set contains
    // the current status of the group attributes (with vector clocks) and the collapsed
    // information of new and removed members. The current change set now becomes the pending
    // change set, waiting for ACKs
    PtrChangeSet changeSet = getGroupChangeSet(groupId);
    if (!changeSet) {
        return;
    }
    // Remove old pending change set, makes sure we have only _one_ pending change
    // set at a time
    removeFromPendingChangeSets(groupId);

    // Add it to pending change set cache map and save in persistent store
    pendingChangeSets.insert(pair<string, PtrChangeSet>(groupId, changeSet));
    changeSet->SerializeAsString();
    store_->insertChangeSet(groupId, changeSet->SerializeAsString());

    // Remove as the the current change set
    currentChangeSets.erase(groupId);

    updateInProgress = false;

    LOGGER(DEBUGGING, __func__, " <-- ", groupId);
}

// The device_id inside then change set and vector clocks consists of the first 8 binary bytes
// of the unique device id (16 binary bytes)
void AppInterfaceImpl::makeBinaryDeviceId(const string &deviceId, string *binaryId)
{
    unique_ptr<uint8_t[]> binBuffer(new uint8_t[deviceId.size()]);
    hex2bin(deviceId.c_str(), binBuffer.get());
    binaryId->assign(reinterpret_cast<const char*>(binBuffer.get()), VC_ID_LENGTH);
}

bool AppInterfaceImpl::removeFromPendingChangeSets(const string &key)
{
    // remove old pending change set
    size_t removed = pendingChangeSets.erase(key);
    store_->removeChangeSet(key);
    return removed > 0;
}

int32_t AppInterfaceImpl::performGroupHellos(const string &userId, const string &deviceId, const string &deviceName)
{
    LOGGER(DEBUGGING, __func__, " --> ");

    if (deviceId.empty() || userId.empty()) {
        return ILLEGAL_ARGUMENT;
    }

    // First check if the user is a member of some group
    int32_t result = 0;
    if (!store_->isGroupMember(userId, &result)) {
        if (SQL_FAIL(result)) {
            return GROUP_ERROR_BASE + result;
        }
        return SUCCESS;
    }

    list<JsonUnique>groups;

    result = store_->listAllGroups(groups);
    if (SQL_FAIL(result)) {
        return GROUP_ERROR_BASE + result;
    }

    // If no groups to sync, just do nothing
    if (groups.empty()) {
        return SUCCESS;
    }

    for (auto& group : groups) {
        const string groupId = Utilities::getJsonString(group.get(), GROUP_ID, "");

        if (store_->isMemberOfGroup(groupId, userId)) {
            result = performGroupHello(groupId, userId, deviceId, deviceName);
            if (result != SUCCESS) {
                return result;
            }
        }
    }
    LOGGER(DEBUGGING, __func__, " <-- ");
    return SUCCESS;
}

int32_t AppInterfaceImpl::performGroupHello(const string &groupId, const string &userId, const string &deviceId, const string &deviceName)
{
    LOGGER(DEBUGGING, __func__, " --> ");

    uint8_t updateId[UPDATE_ID_LENGTH];
    string binDeviceId;
    makeBinaryDeviceId(deviceId, &binDeviceId);

    ZrtpRandom::getRandomData(updateId, sizeof(updateId));

    // Get an empty change set and add the group's current data (meta data, members)
    auto changeSet = make_shared<GroupChangeSet>();
    int32_t result = addMissingMetaData(changeSet, groupId, binDeviceId, updateId, *store_);
    if (result != SUCCESS) {
        return result;
    }
    changeSet->mutable_updateaddmember()->set_update_id(updateId, UPDATE_ID_LENGTH);
    result = addExistingMembers(changeSet, groupId, *store_);
    if (result != SUCCESS) {
        return result;
    }

    JsonUnique jsonUnique(cJSON_CreateObject());
    cJSON* root = jsonUnique.get();
    cJSON_AddStringToObject(root, GROUP_COMMAND, HELLO);
    cJSON_AddStringToObject(root, GROUP_ID, groupId.c_str());
    cJSON_AddStringToObject(root, MSG_DEVICE_ID, getOwnDeviceId().c_str());

    string attributes;
    result = serializeChangeSet(changeSet, root, &attributes);
    if (result != SUCCESS) {
        return result;
    }
    string updateIdString(reinterpret_cast<const char*>(updateId), UPDATE_ID_LENGTH);
    if (changeSet->has_updatename()) {
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_SET_NAME);
    }
    if (changeSet->has_updateavatar()) {
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_SET_AVATAR);
    }
    if (changeSet->has_updateburn()) {
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_SET_AVATAR);
    }
    if (changeSet->has_updateaddmember()) {
        store_->insertWaitAck(groupId, binDeviceId, updateIdString, GROUP_ADD_MEMBER);
    }

    // Queue the hello message, assume the device is new - if a valid ratchet exists the send function handles this
    const string msgId = generateMsgIdTime();
    queueMessageToSingleUserDevice(userId, msgId, deviceId, deviceName, attributes, Empty, Empty, GROUP_MSG_CMD, true, NoAction);

    LOGGER(DEBUGGING, __func__, " <-- ");
    return SUCCESS;
}
