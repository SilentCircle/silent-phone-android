#include <limits.h>
#include "gtest/gtest.h"

#include "../axolotl/state/AxoConversation.h"
#include "../storage/sqlite/SQLiteStoreConv.h"
#include "../axolotl/crypto/EcCurve.h"
#include "../axolotl/crypto/EcCurveTypes.h"
#include "../axolotl/crypto/Ec255PublicKey.h"
#include "../util/UUID.h"
#include "../logging/AxoLogging.h"

#include <iostream>
using namespace axolotl;
using namespace std;

static std::string aliceName("alice@wonderland.org");
static std::string bobName("bob@milkyway.com");

static std::string aliceDev("aliceDevId");
static std::string bobDev("BobDevId");

static const char* keyInDataC = "1234567890098765432112345678901"; // 32 bytes (incl. '\0')
static const char* keyInDataD = "aaaaaaaaaabbbbbbbbbbccccccccccd";
static const char* keyInDataE = "AAAAAAAAAABBBBBBBBBBCCCCCCCCCCD";
static const char* keyInDataF = "ZZZZZZZZZZYYYYYYYYYYXXXXXXXXXXW";

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};

class StoreTestFixture: public ::testing::Test {
public:
    StoreTestFixture( ) {
        // initialization code here
    }

    void SetUp() {
        // code here will execute just before the test ensues
        LOGGER_INSTANCE setLogLevel(ERROR);
        store = SQLiteStoreConv::getStore();
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
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
    SQLiteStoreConv* store;
};

TEST_F(StoreTestFixture, Basic)
{
//    prepareStore();
//    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    string mkiv((const char*)keyInData, 32);

    store->insertStagedMk(bobName, bobDev, aliceName, mkiv);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();

    shared_ptr<list<string> > keys = store->loadStagedMks(bobName, bobDev, aliceName);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();
    ASSERT_TRUE(keys.get() != NULL);
    ASSERT_EQ(1, keys->size());
    string both = keys->front();
    keys->pop_front();
    ASSERT_EQ(mkiv, both);

    store->deleteStagedMk(bobName, bobDev, aliceName, both);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();

    keys = store->loadStagedMks(bobName, bobDev, aliceName);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();
    ASSERT_TRUE(keys.get() == NULL);
}

TEST_F(StoreTestFixture, TimeDelete)
{
//    prepareStore();
//    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    string mkiv(keyInDataC, 32);
    string mkiv_1(keyInDataD, 32);
    string mkiv_2(keyInDataE, 32);
    string mkiv_3(keyInDataF, 32);

    store->insertStagedMk(bobName, bobDev, aliceName, mkiv);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();

    store->insertStagedMk(bobName, bobDev, aliceName, mkiv_1);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();

    shared_ptr<list<string> > keys = store->loadStagedMks(bobName, bobDev, aliceName);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();
    ASSERT_TRUE(keys.get() != NULL);
    ASSERT_EQ(2, keys->size());

    sqlite3_sleep(5000);

    store->insertStagedMk(bobName, bobDev, aliceName, mkiv_2);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();

    store->insertStagedMk(bobName, bobDev, aliceName, mkiv_3);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();

    keys = store->loadStagedMks(bobName, bobDev, aliceName);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();
    ASSERT_TRUE(keys.get() != NULL);
    ASSERT_EQ(4, keys->size());

    time_t now_4 = time(0) - 4;
    store->deleteStagedMk(now_4);
    keys = store->loadStagedMks(bobName, bobDev, aliceName);

    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();
    ASSERT_TRUE(keys.get() != NULL);
    ASSERT_EQ(2, keys->size());
}

TEST(UUID, Basic)
{
    uuid_t uuid1;
    struct timeval tv;

    time_t tm = time(0);
    uuid_generate_time(uuid1);
    time_t tm1 = uuid_time(uuid1, &tv);

    uuid_string_t uuidString;
    uuid_unparse(uuid1, uuidString);

    EXPECT_NEAR(tm, tm1, 1) << "tm1 may be off by 1";
}

