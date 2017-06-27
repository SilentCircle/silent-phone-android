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

#include <zrtp/crypto/sha256.h>
#include <zrtp/crypto/sha2.h>
#include "../storage/sqlite/SQLiteStoreConv.h"

#include "../ratchet/crypto/DhKeyPair.h"
#include "../ratchet/crypto/Ec255PrivateKey.h"
#include "../ratchet/crypto/Ec255PublicKey.h"
#include "../util/b64helper.h"

#include "gtest/gtest.h"
#include "../provisioning/ScProvisioning.h"
#include "../storage/NameLookup.h"
#include "../interfaceApp/JsonStrings.h"
#include "../Constants.h"
#include "../keymanagment/PreKeys.h"

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
static const uint8_t keyInData_1[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,32};
static const uint8_t keyInData_2[] = "ZZZZZzzzzzYYYYYyyyyyXXXXXxxxxxW";  // 32 bytes

using namespace std;
using namespace zina;

static string empty;

static string* preKeyJson(const DhKeyPair& preKeyPair)
{
    cJSON *root;
    char b64Buffer[280];   // Twice the max. size on binary data - b64 is times 1.5

    root = cJSON_CreateObject();

    b64Encode(preKeyPair.getPrivateKey().privateData(), preKeyPair.getPrivateKey().getEncodedSize(), b64Buffer, 270);
    cJSON_AddStringToObject(root, "private", b64Buffer);

    b64Encode((const uint8_t*)preKeyPair.getPublicKey().serialize().data(), preKeyPair.getPublicKey().getEncodedSize(), b64Buffer, 270);
    cJSON_AddStringToObject(root, "public", b64Buffer);

    char *out = cJSON_Print(root);
    std::string* data = new std::string(out);
//    cerr << "PreKey data to store: " << *data << endl;
    cJSON_Delete(root); free(out);

    return data;
}

class StoreTestFixture: public ::testing::Test {
public:
    StoreTestFixture( ) {
        // initialization code here
    }

    void SetUp() {
        // code here will execute just before the test ensues
        LOGGER_INSTANCE setLogLevel(ERROR);
        pks = SQLiteStoreConv::getStore();
        pks->setKey(std::string((const char*)keyInData, 32));
        pks->openStore(std::string());
    }

    void TearDown( ) {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
        SQLiteStoreConv::closeStore();
    }

    ~StoreTestFixture( )  {
        // cleanup any pending stuff, but no exceptions allowed
        LOGGER_INSTANCE setLogLevel(VERBOSE);
    }

    // put in any custom data members that you need
    SQLiteStoreConv* pks;
};

TEST_F(StoreTestFixture, PreKeyStore)
{
    // Need a key pair here
    const Ec255PublicKey baseKey_1(keyInData_1);
    const Ec255PrivateKey basePriv_1(keyInData_2);
    const DhKeyPair basePair(baseKey_1, basePriv_1);

    string* pk = preKeyJson(basePair);

    string pk_1;
    int32_t result = pks->loadPreKey(3, pk_1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(pk_1.empty()) <<  "Some data in an empty store?";

    result = pks->storePreKey(3, *pk);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(pks->containsPreKey(3));

    result = pks->storePreKey(3, *pk);
    ASSERT_TRUE(result == SQLITE_CONSTRAINT) << pks->getLastError();

    pk_1.clear();
    result = pks->loadPreKey(3, pk_1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(*pk, pk_1);

    pks->removePreKey(3);
    ASSERT_FALSE(pks->containsPreKey(3));
}

TEST_F(StoreTestFixture, PreKeyGenerate)
{
    // generate and store a key pair
    PreKeys::PreKeyData preKeyBundle = PreKeys::generatePreKey(pks);

    string pk_1;
    int32_t result = pks->loadPreKey(preKeyBundle.keyId, pk_1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_FALSE(pk_1.empty()) <<  "Generated key not found, " << pk_1 << endl;
    auto parsedKey = PreKeys::parsePreKeyData(pk_1);
    ASSERT_EQ(preKeyBundle.keyPair->getPublicKey(), parsedKey->getPublicKey());
}

TEST_F(StoreTestFixture, PreKeyGenerateList)
{
    // generate and store a key pair
    auto* preKeyList = PreKeys::generatePreKeys(pks);

    string pk_1;
    for (auto &preKey : *preKeyList ) {
        int32_t result = pks->loadPreKey(preKey.keyId, pk_1);
        ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
        ASSERT_FALSE(pk_1.empty()) << "Generated key not found, " << pk_1 << endl;
        auto parsedKey = PreKeys::parsePreKeyData(pk_1);
        ASSERT_EQ(preKey.keyPair->getPublicKey(), parsedKey->getPublicKey());
    }
}

TEST_F(StoreTestFixture, MsgHashStore)
{
    string msgHash_1("abcdefghijkl");
    string msgHash_2("123456789012");

    int32_t result = pks->hasMsgHash(msgHash_1);
    ASSERT_NE(SQLITE_ROW, result) <<  "Some msgHash in an empty store?";

    result = pks->insertMsgHash(msgHash_1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->hasMsgHash(msgHash_1);
    ASSERT_EQ(SQLITE_ROW, result) <<  "Inserted msgHash not found";

    // Insert a second time must fail
    result = pks->insertMsgHash(msgHash_1);
    ASSERT_TRUE(SQL_FAIL(result)) << pks->getLastError();

    // Insert second message hash an test
    result = pks->insertMsgHash(msgHash_2);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->hasMsgHash(msgHash_2);
    ASSERT_EQ(SQLITE_ROW, result) <<  "Inserted msgHash not found";

    sqlite3_sleep(2000);
    time_t now_1 = time(0) - 1;
    pks->deleteMsgHashes(now_1);

    result = pks->hasMsgHash(msgHash_1);
    ASSERT_NE(SQLITE_ROW, result) <<  "msgHash_1 found after delete";

    result = pks->hasMsgHash(msgHash_2);
    ASSERT_NE(SQLITE_ROW, result) <<  "msgHash_2 found after delete";
}

static string name("uabcdefghijklmnoprstvwxy");
static string msgId("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
static string devId("a_device");
static string attrib("{\"cmd\":\"ping\"}");
static string convState("Some ratchet state");

static string rawData("Some raw data bla blub");
static string displayName("John Doe");

TEST_F(StoreTestFixture, MsgTraceStore)
{
    // Fresh DB, trace must be empty
    list<StringUnique> records;
    int32_t result = pks->loadMsgTrace(name, empty, empty, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(records.empty());

    // Fresh DB, trace must be empty
    result = pks->loadMsgTrace(empty, msgId, empty, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(records.empty());

    // Fresh DB, trace must be empty
    result = pks->loadMsgTrace(empty, empty, devId, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(records.empty());

    // Insert a message trace record
    result = pks->insertMsgTrace(name, msgId, devId, convState, attrib, false, false);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->loadMsgTrace(name, empty, empty, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, records.size());

    records.clear();
    result = pks->loadMsgTrace(empty, msgId, empty, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, records.size());

    records.clear();
    result = pks->loadMsgTrace(empty, empty, devId, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, records.size());

    records.clear();
    result = pks->loadMsgTrace(empty, msgId, devId, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, records.size());

    time_t nowMinus1 = time(NULL)-1;
    pks->deleteMsgTrace(nowMinus1);

    records.clear();
    result = pks->loadMsgTrace(name, empty, empty, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_FALSE(records.empty());

    sqlite3_sleep(2000);
    nowMinus1 = time(NULL) - 1;
    // clear old records
    result = pks->deleteMsgTrace(nowMinus1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    records.clear();
    result = pks->loadMsgTrace(name, empty, empty, records);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(records.empty());
}

TEST_F(StoreTestFixture, ReceivedRawData)
{
    // Fresh DB, no received raw records
    list<unique_ptr<StoredMsgInfo> >  rawMessageData;
    int32_t result = pks->loadReceivedRawData(&rawMessageData);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(rawMessageData.empty());

    // Insert a raw message record
    int64_t sequence = 0;
    result = pks->insertReceivedRawData(rawData, name, displayName, &sequence);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, sequence);

    rawMessageData.clear();
    result = pks->loadReceivedRawData(&rawMessageData);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, rawMessageData.size());

    unique_ptr<StoredMsgInfo> &msgInfo = rawMessageData.front();
    ASSERT_EQ(rawData, msgInfo->info_rawMsgData);
    ASSERT_EQ(name, msgInfo->info_uid);
    ASSERT_EQ(displayName, msgInfo->info_displayName);
    ASSERT_EQ(sequence, msgInfo->sequence);

    // Delete the record
    result = pks->deleteReceivedRawData(msgInfo->sequence);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // Now list must be empty
    rawMessageData.clear();
    result = pks->loadReceivedRawData(&rawMessageData);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(rawMessageData.empty());

    // insert a second record (using same data), now sequence number must be 2
    result = pks->insertReceivedRawData(rawData, name, displayName, &sequence);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(2, sequence);

    // Don't clean it yet
    time_t nowMinus1 = time(NULL)-1;
    result = pks->cleanReceivedRawData(nowMinus1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->loadReceivedRawData(&rawMessageData);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_FALSE(rawMessageData.empty());
    rawMessageData.clear();

    sqlite3_sleep(2000);
    nowMinus1 = time(NULL) - 1;

    // clear old records
    result = pks->cleanReceivedRawData(nowMinus1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->loadReceivedRawData(&rawMessageData);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(rawMessageData.empty());
}

TEST_F(StoreTestFixture, TempMsg)
{
    // Fresh DB, no received raw records
    list<unique_ptr<StoredMsgInfo> > tempMsg;
    int32_t result = pks->loadTempMsg(&tempMsg);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(tempMsg.empty());

    // Insert a message record, use some data
    int64_t sequence = 0;
    result = pks->insertTempMsg(rawData, name, 0, &sequence);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, sequence);

    tempMsg.clear();
    result = pks->loadTempMsg(&tempMsg);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, tempMsg.size());

    unique_ptr<StoredMsgInfo> &msgInfo = tempMsg.front();
    ASSERT_EQ(rawData, msgInfo->info_msgDescriptor);
    ASSERT_EQ(name, msgInfo->info_supplementary);
    ASSERT_EQ(sequence, msgInfo->sequence);

    // Delete the record
    result = pks->deleteTempMsg(msgInfo->sequence);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // Now list must be empty
    tempMsg.clear();
    result = pks->loadReceivedRawData(&tempMsg);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(tempMsg.empty());

    // insert a second record (using same data), now sequence number must be 2
    result = pks->insertTempMsg(rawData, name, 0, &sequence);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(2, sequence);

    // Don't clean it yet
    time_t nowMinus1 = time(NULL)-1;
    result = pks->cleanTempMsg(nowMinus1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->loadTempMsg(&tempMsg);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_FALSE(tempMsg.empty());
    tempMsg.clear();

    sqlite3_sleep(2000);
    nowMinus1 = time(NULL) - 1;

    // clear old records
    result = pks->cleanTempMsg(nowMinus1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->loadTempMsg(&tempMsg);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(tempMsg.empty());
}

static string groupId_1("6ba7b810-9dad-11d1-80b4-00c04fd43001");
static string groupId_2("6ba7b810-9dad-11d1-80b4-00c04fd43002");

static string groupName_1("group1");
static string groupName_2("group2");

static string ownName("ulocalowner");
static string groupOwner("ugroupowner");

static string groupDescription("This is a description");

static string memberId_1("6ba7b810-9dad-11d1-80b4-00c04fd43101");
static string memberId_2("6ba7b810-9dad-11d1-80b4-00c04fd43102");

static string deviceId_1("device_1");
static string deviceId_2("device_2");

static int32_t getJsonInt(cJSON* root, const char* tag, int32_t error)
{
    cJSON* jsonItem = cJSON_GetObjectItem(root, tag);
    if (jsonItem == NULL)
        return error;
    return jsonItem->valueint;
}

static const char* getJsonString(cJSON* root, const char* tag, const char* error)
{
    cJSON* jsonItem = cJSON_GetObjectItem(root, tag);
    if (jsonItem == NULL)
        return error;
    return jsonItem->valuestring;
}

TEST_F(StoreTestFixture, GroupChatStore)
{
    uint8_t hash_1[SHA256_DIGEST_LENGTH];
    sha256((uint8_t*)memberId_1.c_str(), static_cast<uint32_t >(memberId_1.length()), hash_1);

    uint8_t hash_2[SHA256_DIGEST_LENGTH];
    sha256_ctx *ctx = reinterpret_cast<sha256_ctx*>(createSha256Context());
    sha256Ctx(ctx, (uint8_t*)memberId_1.c_str(), static_cast<uint32_t >(memberId_1.length()));
    sha256Ctx(ctx, (uint8_t*)memberId_2.c_str(), static_cast<uint32_t >(memberId_2.length()));
    closeSha256Context(ctx, hash_2);

    // Fresh DB, groups must be empty
    list<JsonUnique> groups;
    int32_t result = pks->listAllGroups(groups);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(groups.empty());

    shared_ptr<cJSON> group = pks->listGroup(groupId_1);
    ASSERT_FALSE((bool)group);

    // Fresh DB, members must be empty
    list<JsonUnique> members;
    result = pks->getAllGroupMembers(groupId_1, members);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(members.empty());

    shared_ptr<cJSON> member = pks->getGroupMember(groupId_1, memberId_1);
    ASSERT_FALSE((bool)member);

    bool has = pks->hasGroup(groupId_1);
    ASSERT_FALSE(has);

    // Test with empty group name and group decsription
    result = pks->insertGroup(groupId_1, Empty, groupOwner, Empty, 10);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    has = pks->hasGroup(groupId_1);
    ASSERT_TRUE(has);

    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    cJSON* root = group.get();

    ASSERT_EQ(10, getJsonInt(root, GROUP_MAX_MEMBERS, -1));
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));
    ASSERT_TRUE(Empty == string(getJsonString(root, GROUP_NAME, "no empty")));
    ASSERT_TRUE(Empty == string(getJsonString(root, GROUP_DESC, "no empty")));

    // Delete the group
    result = pks->deleteGroup(groupId_1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError() << ", code: " << result;

    result = pks->insertGroup(groupId_1, groupName_1, groupOwner, groupDescription, 10);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    has = pks->hasGroup(groupId_1);
    ASSERT_TRUE(has);

    // Group attributes are initialized to 0
    pair<int32_t, time_t> attrTime = pks->getGroupAttribute(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, attrTime.first);

    // Set two attribute bits
    result = pks->setGroupAttribute(groupId_1, 3);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // Clear lowest bit
    result = pks->clearGroupAttribute(groupId_1, 1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    attrTime = pks->getGroupAttribute(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(2, attrTime.first);

    groups.clear();
    result = pks->listAllGroups(groups);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, groups.size());
    root = groups.front().get();
    ASSERT_EQ(10, getJsonInt(root, GROUP_MAX_MEMBERS, -1));
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));

    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = group.get();
    ASSERT_EQ(10, getJsonInt(root, GROUP_MAX_MEMBERS, -1));
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));

    result = pks->modifyGroupMaxMembers(groupId_1, 30);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = group.get();
    ASSERT_EQ(30, getJsonInt(root, GROUP_MAX_MEMBERS, -1));
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));

    result = pks->setGroupName(groupId_1, rawData);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = group.get();
    ASSERT_EQ(rawData, getJsonString(root, GROUP_NAME, ""));
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));

    result = pks->setGroupBurnTime(groupId_1, 4711, 1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = group.get();
    ASSERT_EQ(4711, getJsonInt(root, GROUP_BURN_SEC, -1));
    ASSERT_EQ(1, getJsonInt(root, GROUP_BURN_MODE, -1));
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));

    result = pks->setGroupAvatarInfo(groupId_1, rawData);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = group.get();
    ASSERT_EQ(rawData, string(getJsonString(root, GROUP_AVATAR, "")));
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));

    // Add a ratchet conversation for the member, use some dummy data. Keys are
    // important here
    result = pks->storeConversation(memberId_1, deviceId_1, ownName, attrib);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // Add ratchet conversation for a second member
    result = pks->storeConversation(memberId_2, deviceId_2, ownName, attrib);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // Add a group member.
    result = pks->insertMember(groupId_1, memberId_1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    uint8_t hash_db[SHA256_DIGEST_LENGTH];
    result = pks->memberListHash(groupId_1, hash_db);
    ASSERT_EQ(0, memcmp(hash_db, hash_1, SHA256_DIGEST_LENGTH));

    // Member attributes are initialized to ACTIVE
    attrTime = pks->getMemberAttribute(groupId_1, memberId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(ACTIVE, attrTime.first);

    // Set two attribute bits
    result = pks->setMemberAttribute(groupId_1, memberId_1, 3);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // Clear lowest bit - the ACTIVE bit
    result = pks->clearMemberAttribute(groupId_1, memberId_1, ACTIVE);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    attrTime = pks->getMemberAttribute(groupId_1, memberId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(2, attrTime.first);

    // Get group again, check member count - must be 1
    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = group.get();
    ASSERT_EQ(1, getJsonInt(root, GROUP_MEMBER_COUNT, -1));

    // List all members of a group, should return a list with size 1 and the correct data
    members.clear();
    result = pks->getAllGroupMembers(groupId_1, members);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_EQ(1, members.size());
    root = members.front().get();
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(memberId_1, string(getJsonString(root, MEMBER_ID, "")));

    // Add a second group member.
    result = pks->insertMember(groupId_1, memberId_2);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // Because the first member has its ACTIVE bit cleared above that record
    // is not part of the has, thus this must fail
    result = pks->memberListHash(groupId_1, hash_db);
    ASSERT_NE(0, memcmp(hash_db, hash_2, SHA256_DIGEST_LENGTH));

    // set the member's ACTIVE bit, repeat hash check, this time it must
    // succeed
    result = pks->setMemberAttribute(groupId_1, memberId_1, ACTIVE);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    result = pks->memberListHash(groupId_1, hash_db);
    ASSERT_EQ(0, memcmp(hash_db, hash_2, SHA256_DIGEST_LENGTH));

    bool isAMember = pks->isMemberOfGroup(groupId_1, memberId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(isAMember);

    isAMember = pks->isGroupMember(memberId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(isAMember);

    // List one member of a group
    member = pks->getGroupMember(groupId_1, memberId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = member.get();
    ASSERT_EQ(groupId_1, string(getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(memberId_1, string(getJsonString(root, MEMBER_ID, "")));

    // Try to delete the group with existing members, must fail with code 19 (SQLITE_CONSTRAINT)
    result = pks->deleteGroup(groupId_1);
    ASSERT_TRUE(SQL_FAIL(result));
    ASSERT_EQ(SQLITE_CONSTRAINT, result)  << pks->getLastError() << ", code: " << result;

    // Try to delete conversation of the group member, must fail with code 19 (SQLITE_CONSTRAINT)
    // This was remove from DB (references, need to check it manually)
//    pks->deleteConversation(memberId_1, deviceId_1, ownName, &result);
//    ASSERT_TRUE(SQL_FAIL(result));
//    ASSERT_EQ(SQLITE_CONSTRAINT, result)  << pks->getLastError() << ", code: " << result;

    // Delete the first member
    result = pks->deleteMember(groupId_1, memberId_1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // The member list operations must not return an empty list
    members.clear();
    result = pks->getAllGroupMembers(groupId_1, members);
    ASSERT_FALSE(members.empty());

    member = pks->getGroupMember(groupId_1, memberId_1);
    ASSERT_FALSE((bool)member);

    // Delete the second member
    result = pks->deleteMember(groupId_1, memberId_2);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    // The member list operations must return empty list or data now
    members.clear();
    result = pks->getAllGroupMembers(groupId_1, members);
    ASSERT_TRUE(members.empty());

    member = pks->getGroupMember(groupId_1, memberId_2);
    ASSERT_FALSE((bool)member);

    // Get group again, check member count - must be 0
    group = pks->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE((bool)group);
    root = group.get();
    ASSERT_EQ(0, getJsonInt(root, GROUP_MEMBER_COUNT, -1));

    // Delete the group, must succeed now
    result = pks->deleteGroup(groupId_1);
    ASSERT_FALSE(SQL_FAIL(result));

    // The group list operations must return empty list or data
    groups.clear();
    result = pks->listAllGroups(groups);
    ASSERT_TRUE(groups.empty());

    group = pks->listGroup(groupId_1);
    ASSERT_FALSE((bool)group);

    // Delete conversation of the group member, must succeed now
    result = pks->deleteConversation(memberId_1, deviceId_1, ownName);
    ASSERT_FALSE(SQL_FAIL(result));
}

static string updateId_1("update-id-1");

TEST_F(StoreTestFixture, WaitForAck)
{
    // No record stored yet, thus return false
    ASSERT_FALSE(pks->hasWaitAck(groupId_1, deviceId_1, updateId_1, 1, nullptr));

    // insert a record and check
    int32_t result = pks->insertWaitAck(groupId_1, deviceId_1, updateId_1, 1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(pks->hasWaitAck(groupId_1, deviceId_1, updateId_1, 1, nullptr));
    ASSERT_TRUE(pks->hasWaitAckGroupUpdate(groupId_1, updateId_1, nullptr));

    // Remove this specific record and check
    result = pks->removeWaitAck(groupId_1, deviceId_1, updateId_1, 1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_FALSE(pks->hasWaitAck(groupId_1, deviceId_1, updateId_1, 1, nullptr));
    ASSERT_FALSE(pks->hasWaitAckGroupUpdate(groupId_1, updateId_1, nullptr));

    // insert record again
    result = pks->insertWaitAck(groupId_1, deviceId_1, updateId_1, 1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(pks->hasWaitAck(groupId_1, deviceId_1, updateId_1, 1, nullptr));
    ASSERT_TRUE(pks->hasWaitAckGroupUpdate(groupId_1, updateId_1, nullptr));

    // Remove every group/device record with give type, check
    result = pks->removeWaitAckWithType(groupId_1, deviceId_1, 1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_FALSE(pks->hasWaitAck(groupId_1, deviceId_1, updateId_1, 1, nullptr));
    ASSERT_FALSE(pks->hasWaitAckGroupUpdate(groupId_1, updateId_1, nullptr));

    // insert again, then check clean
    result = pks->insertWaitAck(groupId_1, deviceId_1, updateId_1, 1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_TRUE(pks->hasWaitAck(groupId_1, deviceId_1, updateId_1, 1, nullptr));
    ASSERT_TRUE(pks->hasWaitAckGroupUpdate(groupId_1, updateId_1, nullptr));

    // Don't clean it yet
    time_t nowMinus1 = time(NULL)-1;
    result = pks->cleanWaitAck(nowMinus1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();

    sqlite3_sleep(2000);
    nowMinus1 = time(NULL) - 1;

    // wipe old records
    result = pks->cleanWaitAck(nowMinus1);
    ASSERT_FALSE(SQL_FAIL(result)) << pks->getLastError();
    ASSERT_FALSE(pks->hasWaitAck(groupId_1, deviceId_1, updateId_1, 1, nullptr));
    ASSERT_FALSE(pks->hasWaitAckGroupUpdate(groupId_1, updateId_1, nullptr));
}

class NameLookTestFixture: public ::testing::Test {
public:
    NameLookTestFixture( ) {
        // initialization code here
    }

    void SetUp() {
        // code here will execute just before the test ensues
        LOGGER_INSTANCE setLogLevel(ERROR);
    }

    void TearDown( ) {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
    }

    ~NameLookTestFixture( )  {
        // cleanup any pending stuff, but no exceptions allowed
        LOGGER_INSTANCE setLogLevel(VERBOSE);
        NameLookup::getInstance()->clearNameCache();
    }

    // put in any custom data members that you need
};

static const char* userInfoData =
        {
                "{\n"
                        "\"silent_text\": true,\n"
                        "\"first_name\": \"Radagast\",\n"
                        "\"last_name\": \"the Brown\",\n"
                        "\"display_name\": \"Radagast the Brown\",\n"
                        "\"uuid\": \"uvv9h7fbldqpfp82ed33dqv4lh\",\n"
                        "\"default_alias\": \"radagast\",\n"
                        "\"avatar_url\": \"/avatar/8boiwz3jwA987m3gTPA6eb/AQy6/\",\n"
                        "\"keys\": [],\n"
                        "\"country_code\": \"RU\",\n"
                        "\"silent_phone\": true,\n"
                        "\"organization\": \"StavrosCorp\",\n"
                        "\"subscription\": {\n"
                        "    \"expires\": \"1900-01-01T00:00:00Z\",\n"
                        "    \"autorenew\": true,\n"
                        "    \"state\": \"free\",\n"
                        "    \"handles_own_billing\": true \n"
                        "},\n"
                        "\"permissions\": {\n"
                        "    \"silent_desktop\": false,\n"
                        "    \"silent_text\": false,\n"
                        "    \"silent_phone\": false,\n"
                        "    \"can_send_media\": true,\n"
                        "    \"has_oca\": false\n"
                        "},\n"
                        "\"active_st_device\": \"04e662a9-6899-4999-92a5-e369a077c8cb\",\n"
                        "\"jid_resource\": \"52c043f6-7fe5-47b1-a829-8c6ea5b7bd85\"\n"
                        "}"

};
// This simulates an answer from the provisioning server repsonding user info request
// If necessary check for correctness of request data
//
static int32_t helper0(const std::string& requestUrl, const std::string& method, const std::string& data, std::string* response)
{
//     cerr << method << " helper 0 " << requestUrl << '\n';
//     cerr << data;
    response->assign(userInfoData);
    return 200;
}

static int32_t helper1(const std::string& requestUrl, const std::string& method, const std::string& data, std::string* response)
{
//     cerr << method << " helper 1 " << requestUrl << '\n';
//     cerr << data;
    response->assign(userInfoData);
    return 404;
}

TEST_F(NameLookTestFixture, NameLookUpBasic)
{
    ScProvisioning::setHttpHelper(helper0);

    NameLookup* nameCache = NameLookup::getInstance();

    string expectedUid("uvv9h7fbldqpfp82ed33dqv4lh");
    string alias("checker");
    string auth("_DUMMY_");
    string uid = nameCache->getUid(alias, auth);
    ASSERT_EQ(expectedUid, uid) << "First added UID wrong";

    string alias1("checker");
    string uid1 = nameCache->getUid(alias1, auth);
    ASSERT_EQ(expectedUid, uid1) << "UID lookup for existing alias name failed";

    string alias2("checker12");
    string uid2 = nameCache->getUid(alias2, auth);
    ASSERT_EQ(expectedUid, uid2) << "UID lookup for other alias name failed";

    shared_ptr<list<string> > aliases = nameCache->getAliases(expectedUid);
    size_t size = aliases->size();
    ASSERT_EQ(3, size);

    EXPECT_EQ(alias, aliases->front());
    aliases->pop_front();

    EXPECT_EQ(alias2, aliases->front());
    aliases->pop_front();
}

TEST_F(NameLookTestFixture, NameLookupBasicInfo)
{
    ScProvisioning::setHttpHelper(helper0);

    NameLookup* nameCache = NameLookup::getInstance();

    string expectedUid("uvv9h7fbldqpfp82ed33dqv4lh");
    string alias("checker");
    string auth("_DUMMY_");
    const shared_ptr<UserInfo> uid = nameCache->getUserInfo(alias, auth);
    ASSERT_EQ(expectedUid, uid->uniqueId) << "First added UID wrong";
    /* the shared pointer has a count of 3 at this point:
     * 2 times in the map (uid and alias name point to the same userInfo data
     * 1 is the above uid
     */
    ASSERT_EQ(3, uid.use_count()) << "First added UID wrong";

    string alias1("checker");
    const shared_ptr<UserInfo> uid1 = nameCache->getUserInfo(alias1, auth);
    ASSERT_EQ(expectedUid, uid1->uniqueId) << "UID lookup for existing alias name failed";

    /* the shared pointer has a count of 4 at this point:
     * 2 times in the map (uid and alias name point to the same userInfo data
     * 1 is the above uid
     * 1 is the above uid1
     */
    ASSERT_EQ(4, uid1.use_count()) << "First added UID wrong";

    string alias2("checker12");
    const shared_ptr<UserInfo> uid2 = nameCache->getUserInfo(alias2, auth);
    ASSERT_EQ(expectedUid, uid2->uniqueId) << "UID lookup for other alias name failed";

    /* the shared pointer has a count of 4 at this point:
     * 2 times in the map (uid and alias name "checker" point to the same userInfo data
     * 1 is the above uid
     * 1 is the above uid1
     * 1 is the above uid2
     * 1 additional entry in the map, alias name "checker12" points to the same userInfo data
     */
    ASSERT_EQ(6, uid2.use_count()) << "First added UID wrong";
}

TEST_F(NameLookTestFixture, NameLookupBasicError)
{
    // Helper1 return a "server error", thus no call succeeds
    ScProvisioning::setHttpHelper(helper1);

    NameLookup* nameCache = NameLookup::getInstance();

    string expectedUid;
    string alias("checker");
    string auth("_DUMMY_");
    string uid = nameCache->getUid(alias, auth);
    ASSERT_EQ(expectedUid, uid) << "First added UID wrong";

    string alias1("checker");
    string uid1 = nameCache->getUid(alias1, auth);
    ASSERT_EQ(expectedUid, uid1) << "UID lookup for existing alias name failed";

    string alias2("checker12");
    string uid2 = nameCache->getUid(alias2, auth);
    ASSERT_EQ(expectedUid, uid2) << "UID lookup for other alias name failed";

}

TEST_F(NameLookTestFixture, NameLookupBasicInfoError)
{
    ScProvisioning::setHttpHelper(helper1);

    NameLookup* nameCache = NameLookup::getInstance();

    string expectedUid("uvv9h7fbldqpfp82ed33dqv4lh");
    string alias("checker");
    string auth("_DUMMY_");
    const shared_ptr<UserInfo> uid = nameCache->getUserInfo(alias, auth);
    ASSERT_FALSE(uid) << "First added UID wrong";

    string alias1("checker");
    const shared_ptr<UserInfo> uid1 = nameCache->getUserInfo(alias1, auth);
    ASSERT_FALSE(uid1) << "UID lookup for existing alias name failed";

    string alias2("checker12");
    const shared_ptr<UserInfo> uid2 = nameCache->getUserInfo(alias2, auth);
    ASSERT_FALSE(uid2) << "UID lookup for other alias name failed";
}

static const char* userData =
        {
                "{\n"
                        "\"display_name\": \"Radagast the Brown\",\n"
                        "\"uuid\": \"uvv9h7fbldqpfp82ed33dqv4lh\",\n"
                        "\"display_alias\": \"radagast\"\n"
                        "}"

        };

static const char* userDataWithLookup =
        {
                "{\n"
                        "\"display_name\": \"Radagast the Brown\",\n"
                        "\"uuid\": \"uvv9h7fbldqpfp82ed33dqv4lh\",\n"
                        "\"lookup_uri\": \"uri_uri_uri\",\n"
                        "\"display_alias\": \"radagast\"\n"
                        "}"

        };

TEST_F(NameLookTestFixture, NameLookupAddAlias)
{
    NameLookup* nameCache = NameLookup::getInstance();

    string uuid("uvv9h7fbldqpfp82ed33dqv4lh");
    string alias("checker");
    string auth("_DUMMY_");
    string data(userData);
    string dataWithUri(userDataWithLookup);

    NameLookup::AliasAdd ret = nameCache->addAliasToUuid(alias, uuid, data);
    ASSERT_EQ(NameLookup::UuidAdded, ret);

    // Lookup again. this time with lookup URI, amend this to existing user info
    ret = nameCache->addAliasToUuid(alias, uuid, dataWithUri);
    ASSERT_EQ(NameLookup::AliasExisted, ret);

    const shared_ptr<UserInfo> uid = nameCache->getUserInfo(alias, auth);
    ASSERT_FALSE(!uid) << "Failed to get user info after amending lookup uri";
    ASSERT_TRUE(uid->contactLookupUri == "uri_uri_uri");

    string alias1("checker1");
    ret = nameCache->addAliasToUuid(alias1, uuid, data);
    ASSERT_EQ(NameLookup::AliasAdded, ret);

    shared_ptr<list<string> > aliases = nameCache->getAliases(uuid);
    size_t size = aliases->size();
    ASSERT_EQ(3, size);

    EXPECT_EQ(alias, aliases->front());
    aliases->pop_front();

    EXPECT_EQ(alias1, aliases->front());
    aliases->pop_front();
}
