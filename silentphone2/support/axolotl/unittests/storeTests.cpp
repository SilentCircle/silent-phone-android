#include <limits.h>

#include "../storage/sqlite/SQLiteStoreConv.h"

#include "../axolotl/crypto/DhKeyPair.h"
#include "../axolotl/crypto/Ec255PrivateKey.h"
#include "../axolotl/crypto/Ec255PublicKey.h"
#include "../util/cJSON.h"
#include "../util/b64helper.h"

#include "gtest/gtest.h"
#include "../provisioning/ScProvisioning.h"
#include "../storage/NameLookup.h"
#include "../logging/AxoLogging.h"
#include <iostream>
#include <string>

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
static const uint8_t keyInData_1[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,32};
static const uint8_t keyInData_2[] = "ZZZZZzzzzzYYYYYyyyyyXXXXXxxxxxW";  // 32 bytes
static     string empty;

using namespace std;
using namespace axolotl;

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

    string* pk_1 = pks->loadPreKey(3);
    ASSERT_EQ(NULL, pk_1) <<  "Some data in an empty store?";

    pks->storePreKey(3, *pk);
    ASSERT_TRUE(pks->containsPreKey(3));

    pks->storePreKey(3, *pk);
    ASSERT_TRUE(pks->getSqlCode() == SQLITE_CONSTRAINT) << pks->getLastError();

    pk_1 = pks->loadPreKey(3);
    ASSERT_EQ(*pk, *pk_1);
    delete pk_1;

    pks->removePreKey(3);
    ASSERT_FALSE(pks->containsPreKey(3));

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

    // Now wait for 5 seconds, then delete message hashes older than 4 seconds
    sqlite3_sleep(5000);

    time_t now_4 = time(0) - 4;
    pks->deleteMsgHashes(now_4);

    result = pks->hasMsgHash(msgHash_1);
    ASSERT_NE(SQLITE_ROW, result) <<  "msgHash_1 found after delete";

    result = pks->hasMsgHash(msgHash_2);
    ASSERT_NE(SQLITE_ROW, result) <<  "msgHash_2 found after delete";
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

    string aliasFound = aliases->front();
    aliases->pop_front();
    EXPECT_EQ(alias, aliasFound);

    aliasFound = aliases->front();
    aliases->pop_front();
    EXPECT_EQ(alias2, aliasFound);
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

    string aliasFound = aliases->front();
    aliases->pop_front();
    EXPECT_EQ(alias, aliasFound);

    aliasFound = aliases->front();
    aliases->pop_front();
    EXPECT_EQ(alias1, aliasFound);
}
