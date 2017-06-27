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
// Created by werner on 09.02.17.
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

static const string groupName_1("group_Name_1");
static const string groupName_2("group_Name_2");

static const string groupId_1("group_id_1");

// The group change set data in attrib_1 contains:
/*
    // user 'AOwnName' creates group, thus implicitly has 'AOwnName' as group member
    // We set the group_id to 'groupId_1'

    groupId = createNewGroup("group_Name_1", Empty);

    setGroupAvatar(groupId, avatar_1--)
    setGroupBurnTime(groupId, 500, 1);

    addUser(groupId, "BGroupMember1");          // Add memberId_1 (BGroupMember1)
    addUser(groupId, "CAnOtherGroupMember1");   // Add otherMemberId_1 (CAnOtherGroupMember1)


    // User 'BGroupMember1' receives and processes the change set for this test
EiYKCDh79s7x4I1eEgwKCN7xH+3e8R/tEAIaDGdyb3VwX05hbWVfMRokCgg4e/bO8eCNXhIMCgje8R/t3vEf7RACGgphdmF0YXJfMS0tIh0KCDh79s7x4I1eEgwKCN7xH+3e8R/tEAIYASD0AypNEhYKFERBbk90aGVyR3JvdXBNZW1iZXIyEgoKCEFPd25OYW1lEg8KDUJHcm91cE1lbWJlcjESFgoUQ0FuT3RoZXJHcm91cE1lbWJlcjEyGBIWChRDQW5PdGhlckdyb3VwTWVtYmVyMQ==
 */
//static const char *attrib_1 = "{\"grpId\":\"group_id_1\", "
//        "\"grpChg\":\"EiYKCH3pHkEOSgwgEgwKCN7xH+3e8R/tEAEaDGdyb3VwX05hbWVfMRokCgh96R5BDkoMIBIMCgje8R/t3vEf7RABGgphdmF0YXJfMS0tIh0KCH3pHkEOSgwgEgwKCN7xH+3e8R/tEAEYASD0Ayo1EgoKCEFPd25OYW1lEg8KDUJHcm91cE1lbWJlcjESFgoUQ0FuT3RoZXJHcm91cE1lbWJlcjE=\"}\n";

static const char *attrib_1 = "{\"grpId\":\"group_id_1\", "
        "\"grpChg\":\"EiYKCNsw1sfxKp7vEgwKCN7xH+3e8R/tEAEaDGdyb3VwX05hbWVfMRokCgjbMNbH8Sqe7xIMCgje8R/t3vEf7RABGgphdmF0YXJfMS0tIh0KCNsw1sfxKp7vEgwKCN7xH+3e8R/tEAEYASD0Ayo1EgoKCEFPd25OYW1lEg8KDUJHcm91cE1lbWJlcjESFgoUQ0FuT3RoZXJHcm91cE1lbWJlcjE=\"}\n";

//static const char *attrib_2 = "{\"grpId\":\"group_id_1\", "
//        "\"grpChg\":\"EiYKCHdGfzGN13lIEgwKCN7xH+3e8R/tEAIaDGdyb3VwX05hbWVfMhokCgh3Rn8xjdd5SBIMCgje8R/t3vEf7RACGgphdmF0YXJfMi0tIh0KCHdGfzGN13lIEgwKCN7xH+3e8R/tEAIYASDYBCpNEhYKFERBbk90aGVyR3JvdXBNZW1iZXIyEgoKCEFPd25OYW1lEg8KDUJHcm91cE1lbWJlcjESFgoUQ0FuT3RoZXJHcm91cE1lbWJlcjEyGBIWChRDQW5PdGhlckdyb3VwTWVtYmVyMQ==\"}";

//static const char *attrib_2 = "{\"grpId\":\"group_id_1\", "
//        "\"grpChg\":\"Kk0SFgoUREFuT3RoZXJHcm91cE1lbWJlcjISCgoIQU93bk5hbWUSDwoNQkdyb3VwTWVtYmVyMRIWChRDQW5PdGhlckdyb3VwTWVtYmVyMTIYEhYKFENBbk90aGVyR3JvdXBNZW1iZXIx\"}";

static const char *attrib_2 = "{\"grpId\":\"group_id_1\", "
        "\"grpChg\":\"EiYKCDh79s7x4I1eEgwKCN7xH+3e8R/tEAIaDGdyb3VwX05hbWVfMRokCgg4e/bO8eCNXhIMCgje8R/t3vEf7RACGgphdmF0YXJfMS0tIh0KCDh79s7x4I1eEgwKCN7xH+3e8R/tEAIYASD0AypNEhYKFERBbk90aGVyR3JvdXBNZW1iZXIyEgoKCEFPd25OYW1lEg8KDUJHcm91cE1lbWJlcjESFgoUQ0FuT3RoZXJHcm91cE1lbWJlcjEyGBIWChRDQW5PdGhlckdyb3VwTWVtYmVyMQ==\"}";

static const string attribute_1(attrib_1);
static const string attribute_2(attrib_2);

// keep member names sortable, alphabetically, simplifies testing
static string ownName("AOwnName");
static string memberId_1("BGroupMember1");
static string otherMemberId_1("CAnOtherGroupMember1");
static string otherMemberId_2("DAnOtherGroupMember2");

static string longDevId_1("def11feddef11feddef11feddef11fed");
static string longDevId_2("def22feddef22feddef22feddef22fed");

string avatar_1("avatar_1--");
string avatar_2("avatar_2--");

static const string apiKey_1("api_key_1");
AppInterfaceImpl* appInterface_1;


static string createRecvMessageDescriptor(AppInterfaceImpl* appInterface)
{
    shared_ptr<cJSON> sharedRoot(cJSON_CreateObject(), cJSON_deleter);
    cJSON* root = sharedRoot.get();

    cJSON_AddNumberToObject(root, "version", 1);
    cJSON_AddStringToObject(root, MSG_SENDER, ownName.c_str());  // the 'ownUser' sends the message

    cJSON_AddStringToObject(root, MSG_DEVICE_ID, longDevId_1.c_str());
    cJSON_AddStringToObject(root, MSG_ID, appInterface->generateMsgIdTime().c_str());
    cJSON_AddStringToObject(root, MSG_MESSAGE, "Group test message.");

    cJSON_AddNumberToObject(root, MSG_TYPE, GROUP_MSG_NORMAL);

    char* out = cJSON_Print(root);
    string response(out);
    free(out);
    return response;
}

static int32_t groupCmdCallback(const string& command)
{
    LOGGER(INFO, __func__, " -->");
//    callbackCommand = command;
    LOGGER(INFO, command);
    LOGGER(INFO, __func__, " <--");
    return OK;
}

class ChangeSetTestsFixture: public ::testing::Test {
public:
    ChangeSetTestsFixture( ) {
        LOGGER_INSTANCE setLogLevel(ERROR);
        store = SQLiteStoreConv::getStore();
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
        appInterface_1 = new AppInterfaceImpl(store, memberId_1, apiKey_1, longDevId_2);
        appInterface_1->setGroupCmdCallback(groupCmdCallback);
        messageDescr.assign(createRecvMessageDescriptor(appInterface_1));
    }

    void SetUp() {
        // code here will execute just before the test ensues
    }

    void TearDown() {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
    }

    ~ChangeSetTestsFixture()  {
        // cleanup any pending stuff, but no exceptions allowed
        store->closeStore();
        delete appInterface_1;
        LOGGER_INSTANCE setLogLevel(VERBOSE);
    }

    // put in any custom data members that you need
    SQLiteStoreConv *store;
    string messageDescr;
};

TEST_F(ChangeSetTestsFixture, ChangeSetAdd) {
    string attributeCopy(attribute_1);
    ASSERT_EQ(SUCCESS, appInterface_1->checkAndProcessChangeSet(messageDescr, &attributeCopy));
    ASSERT_TRUE(attributeCopy.size() < attribute_1.size()) << "copy: "<< attributeCopy;

    int32_t result;
    shared_ptr<cJSON> group = store->listGroup(groupId_1, &result);
    ASSERT_FALSE(SQL_FAIL(result)) << store->getLastError();
    ASSERT_TRUE((bool)group);

    cJSON *root = group.get();
    ASSERT_EQ(groupId_1, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(avatar_1, string(Utilities::getJsonString(root, GROUP_AVATAR, "")));
    ASSERT_EQ(500, Utilities::getJsonInt(root, GROUP_BURN_SEC, -1));
    ASSERT_EQ(1, Utilities::getJsonInt(root, GROUP_BURN_MODE, -1));

    // List all members of a group, should return a list with size 3 and the correct data
    list<JsonUnique> members;
    result = store->getAllGroupMembers(groupId_1, members);
    ASSERT_FALSE(SQL_FAIL(result)) << store->getLastError();
    ASSERT_EQ(3, members.size());
    root = members.front().get();
    ASSERT_EQ(groupId_1, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(ownName, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    members.pop_front();
    root = members.front().get();
    ASSERT_EQ(groupId_1, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(memberId_1, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    members.pop_front();
    root = members.front().get();
    ASSERT_EQ(groupId_1, string(Utilities::getJsonString(root, GROUP_ID, "")));
    ASSERT_EQ(otherMemberId_1, string(Utilities::getJsonString(root, MEMBER_ID, "")));

    string attributeCopy2(attribute_2);
    ASSERT_EQ(SUCCESS, appInterface_1->checkAndProcessChangeSet(messageDescr, &attributeCopy2));
    ASSERT_TRUE(attributeCopy2.size() < attribute_1.size()) << "copy2: "<< attributeCopy2;

}
