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
// Created by werner on 02.02.17.
//

#include <string>

#include "gtest/gtest.h"
#include "../interfaceApp/AppInterfaceImpl.h"
#include "../interfaceApp/GroupProtocol.pb.h"
#include "../interfaceApp/JsonStrings.h"
#include "../util/Utilities.h"
#include "../util/b64helper.h"

using namespace std;
using namespace zina;

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};

string groupName_1("group_Name_1");
string groupName_2("group_Name_2");

string groupId_1("group_id_1");

// Make each id 8 characters/bytes
string node_1("node_1--");
string node_2("node_2--");
string node_3("node_3--");
string node_4("node_4--");

string updateId_1("update_id_1");

// keep member names sortable, alphabetically, simplifies testing
static string ownName("AOwnName");
static string memberId_1("BGroupMember1");
static string otherMemberId_1("CAnOtherGroupMember1");
static string otherMemberId_2("DAnOtherGroupMember2");

static string apiKey_1("api_key_1");
AppInterfaceImpl* appInterface_1;

static string longDevId_1("def11feddef11feddef11feddef11fed");
static string longDevId_2("def22feddef22feddef22feddef22fed");
static string longDevId_3("def33feddef33feddef33feddef33fed");
static string longDevId_4("def44feddef44feddef44feddef44fed");

static unsigned char updateIdBin_1[] = {1,2,3,4,5,6,7,8};
static unsigned char updateIdBin_2[] = {8,7,6,5,4,3,2,1};

string avatar_1("avatar_1--");
string avatar_2("avatar_2--");

typedef shared_ptr<GroupChangeSet> PtrChangeSet;
PtrChangeSet getCurrentGroupChangeSet(const string &groupId, SQLiteStoreConv &store);
PtrChangeSet getPendingGroupChangeSet(const string &groupId);

extern void setTestIfObj_(AppInterfaceImpl* obj);

class ChangeSetTestsFixtureSimple: public ::testing::Test {
public:
    ChangeSetTestsFixtureSimple( ) {
        LOGGER_INSTANCE setLogLevel(ERROR);
        store = SQLiteStoreConv::getStore();
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
        appInterface_1 = new AppInterfaceImpl(store, memberId_1, apiKey_1, longDevId_1);

    }

    void SetUp() {
        // code here will execute just before the test ensues
    }

    void TearDown() {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
    }

    ~ChangeSetTestsFixtureSimple()  {
        // cleanup any pending stuff, but no exceptions allowed
        store->closeStore();
        delete appInterface_1;
        LOGGER_INSTANCE setLogLevel(VERBOSE);
    }

    // put in any custom data members that you need
    SQLiteStoreConv *store;
};

TEST_F(ChangeSetTestsFixtureSimple, NewGroupTestsEmpty) {
    string groupId = appInterface_1->createNewGroup(Empty, Empty);
    ASSERT_FALSE(groupId.empty());

    PtrChangeSet changeSet = getCurrentGroupChangeSet(groupId, *store);
    ASSERT_TRUE((bool)changeSet);

    ASSERT_FALSE(changeSet->has_updatename());
}

TEST_F(ChangeSetTestsFixtureSimple, NewGroupTests) {
    string groupId = appInterface_1->createNewGroup(groupName_1, Empty);
    ASSERT_FALSE(groupId.empty());

    PtrChangeSet changeSet = getCurrentGroupChangeSet(groupId, *store);
    ASSERT_TRUE((bool)changeSet);

    ASSERT_TRUE(changeSet->has_updatename());
    ASSERT_EQ(groupName_1, changeSet->updatename().name());

    // cancel and remove the changes
    appInterface_1->cancelGroupChangeSet(groupId);
    changeSet = getCurrentGroupChangeSet(groupId, *store);
    ASSERT_FALSE((bool)changeSet);

}

TEST_F(ChangeSetTestsFixtureSimple, ExistingGroupTests) {
    int32_t result = store->insertGroup(groupId_1, groupName_1, appInterface_1->getOwnUser(), Empty, 0);
    ASSERT_FALSE(SQL_FAIL(result));

    PtrChangeSet changeSet = getCurrentGroupChangeSet(groupId_1, *store);
    ASSERT_TRUE((bool)changeSet);

    ASSERT_EQ(SUCCESS, appInterface_1->setGroupName(groupId_1, &groupName_1));
    ASSERT_TRUE(changeSet->has_updatename());
    ASSERT_EQ(groupName_1, changeSet->updatename().name());

    ASSERT_EQ(SUCCESS, appInterface_1->setGroupName(groupId_1, &groupName_2));
    ASSERT_TRUE(changeSet->has_updatename());
    ASSERT_EQ(groupName_2, changeSet->updatename().name());

    // Empty group name remove the name update
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupName(groupId_1, nullptr));
    ASSERT_FALSE(changeSet->has_updatename());

    // Use some data to set avatar info
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupAvatar(groupId_1, &groupName_2));
    ASSERT_TRUE(changeSet->has_updateavatar());
    ASSERT_EQ(groupName_2, changeSet->updateavatar().avatar());

    // change the data
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupAvatar(groupId_1, &groupName_1));
    ASSERT_TRUE(changeSet->has_updateavatar());
    ASSERT_EQ(groupName_1, changeSet->updateavatar().avatar());

    // Empty avatar info
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupAvatar(groupId_1, nullptr));
    ASSERT_FALSE(changeSet->has_updateavatar());

    // Burn time updates
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupBurnTime(groupId_1, 500, 1));
    ASSERT_TRUE(changeSet->has_updateburn());
    ASSERT_EQ(500, changeSet->updateburn().burn_ttl_sec());
    ASSERT_EQ(1, changeSet->updateburn().burn_mode());

    ASSERT_EQ(SUCCESS, appInterface_1->setGroupBurnTime(groupId_1, 1000, 1));
    ASSERT_TRUE(changeSet->has_updateburn());
    ASSERT_EQ(1000, changeSet->updateburn().burn_ttl_sec());
    ASSERT_EQ(1, changeSet->updateburn().burn_mode());

    ASSERT_EQ(ILLEGAL_ARGUMENT, appInterface_1->setGroupBurnTime(groupId_1, 500, 0));
}

class ChangeSetTestsFixtureMembers: public ::testing::Test {
public:
    ChangeSetTestsFixtureMembers( ) {
        LOGGER_INSTANCE setLogLevel(ERROR);
        // initialization code here
        store = SQLiteStoreConv::getStore();
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
        appInterface_1 = new AppInterfaceImpl(store, ownName, apiKey_1, longDevId_1);
        groupId = appInterface_1->createNewGroup(groupName_1, Empty);
    }

    void SetUp() {
        // code here will execute just before the test ensues
    }

    void TearDown() {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
    }

    ~ChangeSetTestsFixtureMembers()  {
        // cleanup any pending stuff, but no exceptions allowed
        appInterface_1->cancelGroupChangeSet(groupName_1);
        store->closeStore();
        delete appInterface_1;
        LOGGER_INSTANCE setLogLevel(VERBOSE);
    }

    // put in any custom data members that you need
    string groupId;
    SQLiteStoreConv *store;

};

TEST_F(ChangeSetTestsFixtureMembers, AddMemberTests) {
    // Test for illegal parameters
    ASSERT_EQ(DATA_MISSING, appInterface_1->addUser(Empty, Empty));
    ASSERT_EQ(DATA_MISSING, appInterface_1->addUser(groupId, Empty));
    ASSERT_EQ(DATA_MISSING, appInterface_1->addUser(Empty, memberId_1));

    // Invite a user (addUser), own user is already in the change set, added while creating the group, has index 0
    PtrChangeSet changeSet = getCurrentGroupChangeSet(groupId, *store);
    ASSERT_TRUE((bool)changeSet);

    ASSERT_EQ(SUCCESS, appInterface_1->addUser(groupId, memberId_1));
    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(2, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(memberId_1, changeSet->updateaddmember().addmember(1).user_id());

    ASSERT_EQ(SUCCESS, appInterface_1->addUser(groupId, otherMemberId_1));
    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(3, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(otherMemberId_1, changeSet->updateaddmember().addmember(2).user_id());

    ASSERT_EQ(SUCCESS, appInterface_1->addUser(groupId, otherMemberId_2));
    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(4, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(otherMemberId_2, changeSet->updateaddmember().addmember(3).user_id());

    // adding a name a second time, ignore silently, no changes in change set
    ASSERT_EQ(SUCCESS, appInterface_1->addUser(groupId, otherMemberId_2));
    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(4, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(otherMemberId_2, changeSet->updateaddmember().addmember(3).user_id());
}

TEST_F(ChangeSetTestsFixtureMembers, RemoveMemberTests) {

    PtrChangeSet changeSet = getCurrentGroupChangeSet(groupId, *store);
    ASSERT_TRUE((bool)changeSet);
    ASSERT_FALSE(changeSet->has_updatermmember());

    ASSERT_EQ(DATA_MISSING, appInterface_1->removeUser(Empty, Empty));
    ASSERT_EQ(DATA_MISSING, appInterface_1->removeUser(groupId, Empty));
    ASSERT_EQ(DATA_MISSING, appInterface_1->removeUser(Empty, otherMemberId_1));

    ASSERT_EQ(SUCCESS, appInterface_1->removeUser(groupId, otherMemberId_1));
    ASSERT_TRUE(changeSet->has_updatermmember());
    ASSERT_EQ(1, changeSet->updatermmember().rmmember_size());
    ASSERT_EQ(otherMemberId_1, changeSet->updatermmember().rmmember(0).user_id());

    ASSERT_EQ(SUCCESS, appInterface_1->removeUser(groupId, otherMemberId_2));
    ASSERT_TRUE(changeSet->has_updatermmember());
    ASSERT_EQ(2, changeSet->updatermmember().rmmember_size());
    ASSERT_EQ(otherMemberId_2, changeSet->updatermmember().rmmember(1).user_id());

    // removing a name a second time, ignore silently, no changes in change set
    ASSERT_EQ(SUCCESS, appInterface_1->removeUser(groupId, otherMemberId_2));
    ASSERT_TRUE(changeSet->has_updatermmember());
    ASSERT_EQ(2, changeSet->updatermmember().rmmember_size());
    ASSERT_EQ(otherMemberId_2, changeSet->updatermmember().rmmember(1).user_id());
}

TEST_F(ChangeSetTestsFixtureMembers, AddRemoveMemberTests) {

    // Own user is already in the change set, added while creating the group, has index 0

    PtrChangeSet changeSet = getCurrentGroupChangeSet(groupId, *store);

    // Own user is already in the change set, added while creating the group, has index 0
    // At first add a member, check data
    ASSERT_EQ(SUCCESS, appInterface_1->addUser(groupId, memberId_1));
    ASSERT_EQ(2, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(memberId_1, changeSet->updateaddmember().addmember(1).user_id());

    // add a second member
    ASSERT_EQ(SUCCESS, appInterface_1->addUser(groupId, otherMemberId_1));
    ASSERT_EQ(3, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(otherMemberId_1, changeSet->updateaddmember().addmember(2).user_id());

    // Now remove the first added member
    // expect that it is in remove update, and removed from add update thus it is down to 2
    ASSERT_EQ(SUCCESS, appInterface_1->removeUser(groupId, memberId_1));
    ASSERT_TRUE(changeSet->has_updatermmember());
    ASSERT_EQ(1, changeSet->updatermmember().rmmember_size());
    ASSERT_EQ(memberId_1, changeSet->updatermmember().rmmember(0).user_id());

    // check the add update data
    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(2, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(otherMemberId_1, changeSet->updateaddmember().addmember(1).user_id());

    // Now remove another member
    ASSERT_EQ(SUCCESS, appInterface_1->removeUser(groupId, otherMemberId_2));
    ASSERT_TRUE(changeSet->has_updatermmember());
    ASSERT_EQ(2, changeSet->updatermmember().rmmember_size());
    ASSERT_EQ(otherMemberId_2, changeSet->updatermmember().rmmember(1).user_id());

    // now re-add the first member. It should be re-added to add update, removed from
    // remove update
    ASSERT_EQ(SUCCESS, appInterface_1->addUser(groupId, memberId_1));
    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(3, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(memberId_1, changeSet->updateaddmember().addmember(2).user_id());

    // remove update down to one
    ASSERT_TRUE(changeSet->has_updatermmember());
    ASSERT_EQ(1, changeSet->updatermmember().rmmember_size());
    ASSERT_EQ(otherMemberId_2, changeSet->updatermmember().rmmember(0).user_id());
}

// The device_id inside then change set and vector clocks consists of the first 8 binary bytes
// of the unique device id (16 binary bytes)
static void makeBinaryDeviceId(const string &deviceId, string *binaryId)
{
    unique_ptr<uint8_t[]> binBuffer(new uint8_t[deviceId.size()]);
    hex2bin(deviceId.c_str(), binBuffer.get());
    string vecDeviceId;
    binaryId->assign(reinterpret_cast<const char*>(binBuffer.get()), VC_ID_LENGTH);
}

// This is a fairly complex test case. I runs thru a complete cycle:
// - add new members, set group data to create a change set
// - prepare the generic part of change set, update the database
// - prepare the device specific change set
//
// - add other new members, remove a member, change some group data
// - prepare the generic part of the new change set, update the database
// - prepare the device specific change set, this time it should also have data from the
//   previous change set because we don't ACK the data
TEST_F(ChangeSetTestsFixtureMembers, CreateChangeSetTests) {
    // Own user is already in the change set, added while creating the group,
    // has index 0 in add member update

    PtrChangeSet changeSet = getCurrentGroupChangeSet(groupId, *store);

    string binDeviceId;
    makeBinaryDeviceId(appInterface_1->getOwnDeviceId(), &binDeviceId);

    // At first add more members
    appInterface_1->addUser(groupId, memberId_1);
    appInterface_1->addUser(groupId, otherMemberId_1);

    // At this point we have a new group with three members: ownName, memberId_1, otherMemberId_1
    // in this order (alphabetically)

    // Set some group meta data
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupName(groupId, &groupName_1));
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupBurnTime(groupId, 500, 1));
    ASSERT_EQ(SUCCESS, appInterface_1->setGroupAvatar(groupId, &avatar_1));

    // Prepare the change set to apply updates and create non-device specific change set, check data
    ASSERT_EQ(SUCCESS, appInterface_1->prepareChangeSetSend(groupId));

    ASSERT_TRUE(changeSet->has_updatename());
    ASSERT_EQ(1, changeSet->updatename().vclock_size());
    ASSERT_EQ(1, changeSet->updatename().vclock(0).value());
    ASSERT_EQ(binDeviceId, changeSet->updatename().vclock(0).device_id());

    ASSERT_TRUE(changeSet->has_updateavatar());
    ASSERT_EQ(1, changeSet->updateavatar().vclock_size());
    ASSERT_EQ(1, changeSet->updateavatar().vclock(0).value());
    ASSERT_EQ(binDeviceId, changeSet->updateavatar().vclock(0).device_id());

    ASSERT_TRUE(changeSet->has_updateburn());
    ASSERT_EQ(1, changeSet->updateburn().vclock_size());
    ASSERT_EQ(1, changeSet->updateburn().vclock(0).value());
    ASSERT_EQ(binDeviceId, changeSet->updateburn().vclock(0).device_id());

    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(3, changeSet->updateaddmember().addmember_size());

    // Preparing the change set also updates the database, adding group data and members
    ASSERT_TRUE(store->hasGroup(groupId, nullptr));

    int32_t result;
    shared_ptr<cJSON> group = store->listGroup(groupId, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << store->getLastError();
    ASSERT_TRUE((bool)group);

    cJSON *root = group.get();
    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(avatar_1, string(Utilities::getJsonString(root, GROUP_AVATAR, "")));
    ASSERT_EQ(500, Utilities::getJsonInt(root, GROUP_BURN_SEC, -1));
    ASSERT_EQ(1, Utilities::getJsonInt(root, GROUP_BURN_MODE, -1));

    // List all members of a group, should return a list with size 3 and the correct data
    list<JsonUnique> members;
    result = store->getAllGroupMembers(groupId, members);
    ASSERT_FALSE(SQL_FAIL(result)) << store->getLastError();
    ASSERT_EQ(3, members.size());
    root = members.front().get();
    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(ownName, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    members.pop_front();
    root = members.front().get();
    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(memberId_1, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    members.pop_front();
    root = members.front().get();
    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(otherMemberId_1, string(Utilities::getJsonString(root, MEMBER_ID, "")));


    // Now we have a prepare change set, tested, database looks good
    // Create the device specific change set. Because we do not have another older change set
    // yet, the current 'old' change does not change. There is no data to collapse.

    string attributes;
    string newAttributes;
    appInterface_1->createChangeSetDevice(groupId, longDevId_2, attributes, &newAttributes);
    ASSERT_FALSE(newAttributes.empty());

//    cerr << "new attributes: " << newAttributes << endl;

    appInterface_1->groupUpdateSendDone(groupId);

    // Get the now pending change set, check if it has the expected data
    // Return no change set if no change set for a group id.
    PtrChangeSet pendingChangeSet = getPendingGroupChangeSet(groupId_1);
    ASSERT_FALSE((bool)pendingChangeSet);

    pendingChangeSet = getPendingGroupChangeSet(groupId);
    ASSERT_TRUE((bool)pendingChangeSet);

    ASSERT_TRUE(pendingChangeSet->has_updatename());
    ASSERT_EQ(1, pendingChangeSet->updatename().vclock_size());
    ASSERT_EQ(1, pendingChangeSet->updatename().vclock(0).value());
    ASSERT_EQ(binDeviceId, pendingChangeSet->updatename().vclock(0).device_id());

    ASSERT_TRUE(pendingChangeSet->has_updateavatar());
    ASSERT_EQ(1, pendingChangeSet->updateavatar().vclock_size());
    ASSERT_EQ(1, pendingChangeSet->updateavatar().vclock(0).value());
    ASSERT_EQ(binDeviceId, pendingChangeSet->updateavatar().vclock(0).device_id());

    ASSERT_TRUE(pendingChangeSet->has_updateburn());
    ASSERT_EQ(1, pendingChangeSet->updateburn().vclock_size());
    ASSERT_EQ(1, pendingChangeSet->updateburn().vclock(0).value());
    ASSERT_EQ(binDeviceId, pendingChangeSet->updateburn().vclock(0).device_id());

    ASSERT_TRUE(pendingChangeSet->has_updateaddmember());
    ASSERT_EQ(3, pendingChangeSet->updateaddmember().addmember_size());


    // ********************************************
    // OK, ready for the second part
    // ********************************************

    // At first one more member
    appInterface_1->addUser(groupId, otherMemberId_2);

    changeSet = getCurrentGroupChangeSet(groupId, *store);
    ASSERT_TRUE((bool)changeSet);
    ASSERT_TRUE(changeSet->has_updateaddmember());
    ASSERT_EQ(1, changeSet->updateaddmember().addmember_size());
    ASSERT_EQ(otherMemberId_2, changeSet->updateaddmember().addmember(0).user_id());

    // Now remove a known member (not myself)
    appInterface_1->removeUser(groupId, otherMemberId_1);
    ASSERT_TRUE(changeSet->has_updatermmember());
    ASSERT_EQ(1, changeSet->updatermmember().rmmember_size());
    ASSERT_EQ(otherMemberId_1, changeSet->updatermmember().rmmember(0).user_id());

    // At this point we have a new group with three members: ownName, memberId_1, otherMemberId_1, otherMemberId_2
    // in this order (alphabetically)

//    // Set some group meta data - don't set, done automatically by the prepare change set function
    // in case we have an add member
//    ASSERT_EQ(SUCCESS, appInterface_1->setGroupName(groupId, &groupName_2));
//    ASSERT_EQ(SUCCESS, appInterface_1->setGroupBurnTime(groupId, 600, 1));
//    ASSERT_EQ(SUCCESS, appInterface_1->setGroupAvatar(groupId, &avatar_2));
//
//    // Prepare the change set to apply updates and create non-device specific change set, check data
    ASSERT_EQ(SUCCESS, appInterface_1->prepareChangeSetSend(groupId));
//
//    // The vector clock now must have a value of 2
//    ASSERT_TRUE(changeSet->has_updatename());
//    ASSERT_EQ(1, changeSet->updatename().vclock_size());
//    ASSERT_EQ(2, changeSet->updatename().vclock(0).value());
//    ASSERT_EQ(binDeviceId, changeSet->updatename().vclock(0).device_id());
//
//    ASSERT_TRUE(changeSet->has_updateavatar());
//    ASSERT_EQ(1, changeSet->updateavatar().vclock_size());
//    ASSERT_EQ(2, changeSet->updateavatar().vclock(0).value());
//    ASSERT_EQ(binDeviceId, changeSet->updateavatar().vclock(0).device_id());
//
//    ASSERT_TRUE(changeSet->has_updateburn());
//    ASSERT_EQ(1, changeSet->updateburn().vclock_size());
//    ASSERT_EQ(2, changeSet->updateburn().vclock(0).value());
//    ASSERT_EQ(binDeviceId, changeSet->updateburn().vclock(0).device_id());
//
//    ASSERT_TRUE(changeSet->has_updateaddmember());
//    ASSERT_EQ(1, changeSet->updateaddmember().addmember_size());
//
//    ASSERT_TRUE(changeSet->has_updateaddmember());
//    ASSERT_EQ(1, changeSet->updatermmember().rmmember_size());
//
//    // Preparing the change set also updates the database, adding/removing group data and members
//    ASSERT_TRUE(store->hasGroup(groupId, nullptr));
//
//    group = store->listGroup(groupId, &result);
//    ASSERT_FALSE(SQL_FAIL(result)) << store->getLastError();
//    ASSERT_TRUE((bool)group);
//
//    root = group.get();
//    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
//    ASSERT_EQ(avatar_2, string(Utilities::getJsonString(root, GROUP_AVATAR, "")));
//    ASSERT_EQ(600, Utilities::getJsonInt(root, GROUP_BURN_SEC, -1));
//    ASSERT_EQ(1, Utilities::getJsonInt(root, GROUP_BURN_MODE, -1));

    // List all members of a group, should return a list with size 3 and the correct data
    members.clear();
    result = store->getAllGroupMembers(groupId, members);
    ASSERT_FALSE(SQL_FAIL(result)) << store->getLastError();
    ASSERT_EQ(3, members.size());

    root = members.front().get();
    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(ownName, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    members.pop_front();
    root = members.front().get();
    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(memberId_1, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    members.pop_front();
    root = members.front().get();
    ASSERT_EQ(groupId, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(otherMemberId_2, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    appInterface_1->createChangeSetDevice(groupId, longDevId_2, attributes, &newAttributes);
    ASSERT_FALSE(newAttributes.empty());

//    cerr << "new attributes: " << newAttributes << endl;

    // Finish creation of change set, manage pending change sets
    appInterface_1->groupUpdateSendDone(groupId);


    // Get the pending change set, check if it has the expected data
    pendingChangeSet = getPendingGroupChangeSet(groupId);
    ASSERT_TRUE((bool)pendingChangeSet);

    // Because we haven't changed the group's metadata the clocks still have value 1
    ASSERT_TRUE(pendingChangeSet->has_updatename());
    ASSERT_EQ(1, pendingChangeSet->updatename().vclock_size());
    ASSERT_EQ(1, pendingChangeSet->updatename().vclock(0).value());
    ASSERT_EQ(binDeviceId, pendingChangeSet->updatename().vclock(0).device_id());

    ASSERT_TRUE(pendingChangeSet->has_updateavatar());
    ASSERT_EQ(1, pendingChangeSet->updateavatar().vclock_size());
    ASSERT_EQ(1, pendingChangeSet->updateavatar().vclock(0).value());
    ASSERT_EQ(binDeviceId, pendingChangeSet->updateavatar().vclock(0).device_id());

    ASSERT_TRUE(pendingChangeSet->has_updateburn());
    ASSERT_EQ(1, pendingChangeSet->updateburn().vclock_size());
    ASSERT_EQ(1, pendingChangeSet->updateburn().vclock(0).value());
    ASSERT_EQ(binDeviceId, pendingChangeSet->updateburn().vclock(0).device_id());

    ASSERT_TRUE(pendingChangeSet->has_updateaddmember());
    ASSERT_EQ(4, pendingChangeSet->updateaddmember().addmember_size());

    ASSERT_TRUE(pendingChangeSet->has_updatermmember());
    ASSERT_EQ(1, pendingChangeSet->updatermmember().rmmember_size());

}
