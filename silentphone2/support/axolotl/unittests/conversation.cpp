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
#include <malloc.h>

#include "gtest/gtest.h"

#include "../ratchet/state/ZinaConversation.h"
#include "../storage/sqlite/SQLiteStoreConv.h"
#include "../ratchet/crypto/EcCurve.h"
#include "../logging/ZinaLogging.h"

using namespace zina;
using namespace std;

static std::string aliceName("alice@wonderland.org");
static std::string bobName("bob@milkyway.com");

static std::string aliceDev("aliceDevId");
static std::string bobDev("BobDevId");

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};

/**
 * @brief Storage test fixture class that supports memory leak checking.
 *
 * NOTE: when using this class as a base class for a test fixture,
 *       the derived class should not create any local member variables, as
 *       they can cause memory leaks to be improperly reported.
 */
class StoreTestFixture: public ::testing::Test {
public:
    StoreTestFixture( ) {
        // initialization code here
    }

    // code here will execute just before the test ensues
    void SetUp() {
        // capture the memory state at the beginning of the test
        struct mallinfo minfo = mallinfo();
        beginMemoryState = minfo.uordblks;

        LOGGER_INSTANCE setLogLevel(WARNING);

        store = SQLiteStoreConv::getStore();
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
    }

    void TearDown( ) {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
        SQLiteStoreConv::closeStore();

        // only check for memory leaks if the test did not end in a failure

        // NOTE: the logging via LOGGER statements may give wronge results. Thus either switch of
        // logging (LOGGER_INSTANCE setLogLevel(NONE);) during tests (after debugging :-) ) or make
        // sure that no errors etc are logged. The memory info may be wrong if the LOGGER really
        // prints out some data.
        if (!HasFailure())
        {
            // Gets information about the currently running test.
            // Do NOT delete the returned object - it's managed by the UnitTest class.
            const ::testing::TestInfo* const test_info = ::testing::UnitTest::GetInstance()->current_test_info();

            // if there are differences between the memory state at beginning and end, then there are memory leaks
            struct mallinfo minfo = mallinfo();
            if ( beginMemoryState != minfo.uordblks )
            {
                FAIL() << "Memory Leak(s) detected in " << test_info->name()
                       << ", test case: " << test_info->test_case_name()
                       << ", memory before: " << beginMemoryState << ", after: " << minfo.uordblks
                       << ", difference: " << minfo.uordblks - beginMemoryState
                       << endl;
            }
        }
    }

    ~StoreTestFixture( )  {
        // cleanup any pending stuff, but no exceptions allowed
        LOGGER_INSTANCE setLogLevel(VERBOSE);
    }

    // put in any custom data members that you need
    SQLiteStoreConv* store;
    int beginMemoryState;
};

TEST_F(StoreTestFixture, BasicEmpty)
{

    // localUser, remote user, remote dev id
    ZinaConversation conv(aliceName, bobName, bobDev);
    conv.storeConversation(*store);
    ASSERT_FALSE(SQL_FAIL(store->getSqlCode())) << store->getLastError();    

    auto conv1 = ZinaConversation::loadConversation(aliceName, bobName, bobDev, *store);
    ASSERT_TRUE(conv1 != NULL);
    ASSERT_TRUE(conv1->getRK().empty());
}

TEST_F(StoreTestFixture, TestDHR)
{
    // localUser, remote user, remote dev id
    ZinaConversation conv(aliceName,   bobName,   bobDev);
    conv.setRatchetFlag(true);

    conv.setDHRr(PublicKeyUnique(new Ec255PublicKey(keyInData)));
    PublicKeyUnique pubKey = PublicKeyUnique(new Ec255PublicKey(conv.getDHRr().getPublicKeyPointer()));

    conv.setDHRs(EcCurve::generateKeyPair(EcCurveTypes::Curve25519));
    KeyPairUnique keyPair(new DhKeyPair(conv.getDHRs().getPublicKey(), conv.getDHRs().getPrivateKey()));

    conv.storeConversation(*store);
    auto conv1 = ZinaConversation::loadConversation(aliceName, bobName, bobDev, *store);
    ASSERT_TRUE(conv1 != NULL);
    ASSERT_TRUE(conv1->getRatchetFlag());

    const DhKeyPair& keyPair1 = conv1->getDHRs();
    ASSERT_TRUE(conv1->hasDHRs());
    ASSERT_TRUE(keyPair->getPublicKey() == keyPair1.getPublicKey());

    const DhPublicKey& pubKey1 = conv1->getDHRr();
    ASSERT_TRUE(conv1->hasDHRr());
    ASSERT_TRUE(*pubKey == pubKey1);
}

TEST_F(StoreTestFixture, TestDHI)
{
    // localUser, remote user, remote dev id
    ZinaConversation conv(aliceName, bobName, bobDev);
    conv.setRatchetFlag(true);

    conv.setDHIr(PublicKeyUnique(new Ec255PublicKey(keyInData)));
    PublicKeyUnique pubKey = PublicKeyUnique(new Ec255PublicKey(conv.getDHIr().getPublicKeyPointer()));

    conv.setDHIs(EcCurve::generateKeyPair(EcCurveTypes::Curve25519));
    KeyPairUnique keyPair(new DhKeyPair(conv.getDHIs().getPublicKey(), conv.getDHIs().getPrivateKey()));

    conv.storeConversation(*store);
    auto conv1 = ZinaConversation::loadConversation(aliceName, bobName, bobDev,*store);
    ASSERT_TRUE(conv1 != NULL);
    ASSERT_TRUE(conv1->getRatchetFlag());

    const DhKeyPair& keyPair1 = conv1->getDHIs();
    ASSERT_TRUE(conv1->hasDHIs());
    ASSERT_TRUE(keyPair->getPublicKey() == keyPair1.getPublicKey());

    const DhPublicKey& pubKey1 = conv1->getDHIr();
    ASSERT_TRUE(conv1->hasDHIr());
    ASSERT_TRUE(*pubKey == pubKey1);
}

TEST_F(StoreTestFixture, TestA0)
{
    // localUser, remote user, remote dev id
    ZinaConversation conv(aliceName,   bobName,   bobDev);
    conv.setRatchetFlag(true);

    conv.setA0(EcCurve::generateKeyPair(EcCurveTypes::Curve25519));
    KeyPairUnique keyPair(new DhKeyPair(conv.getA0().getPublicKey(), conv.getA0().getPrivateKey()));

    conv.storeConversation(*store);
    auto conv1 = ZinaConversation::loadConversation(aliceName, bobName, bobDev, *store);
    ASSERT_TRUE(conv1 != NULL);
    ASSERT_TRUE(conv1->getRatchetFlag());

    const DhKeyPair& keyPair1 = conv1->getA0();
    ASSERT_TRUE(conv1->hasA0());
    ASSERT_TRUE(keyPair->getPublicKey() == keyPair1.getPublicKey());
}

TEST_F(StoreTestFixture, SimpleFields)
{
    string RK("RootKey");
    string CKs("ChainKeyS 1");
    string CKr("ChainKeyR 1");

    // localUser, remote user, remote dev id
    ZinaConversation conv(aliceName,   bobName,   bobDev);
    conv.setRK(RK);
    conv.setCKr(CKr);
    conv.setCKs(CKs);

    conv.setNr(3);
    conv.setNs(7);
    conv.setPNs(11);
    conv.setPreKeyId(13);
    string tst("test");
    conv.setDeviceName(tst);

    conv.storeConversation(*store);
    auto conv1 = ZinaConversation::loadConversation(aliceName, bobName, bobDev, *store);

    ASSERT_EQ(RK, conv1->getRK());
    ASSERT_EQ(CKr, conv1->getCKr());
    ASSERT_EQ(CKs, conv1->getCKs());

    ASSERT_EQ(3, conv1->getNr());
    ASSERT_EQ(7, conv1->getNs());
    ASSERT_EQ(11, conv1->getPNs());
    ASSERT_EQ(13, conv1->getPreKeyId());
    ASSERT_TRUE(tst == conv1->getDeviceName());
}

TEST_F(StoreTestFixture, SecondaryRatchet)
{
    string RK("RootKey");
    string CKs("ChainKeyS 1");
    string CKr("ChainKeyR 1");

    // localUser, remote user, remote dev id
    ZinaConversation conv(aliceName,   bobName,   bobDev);
    conv.setRK(RK);
    conv.setCKr(CKr);
    conv.setCKs(CKs);

    conv.saveSecondaryAddress(aliceDev, 4711);

    conv.setNr(3);
    conv.setNs(7);
    conv.setPNs(11);
    conv.setPreKeyId(13);
    string tst("test");
    conv.setDeviceName(tst);

    conv.storeConversation(*store);
    auto conv1 = ZinaConversation::loadConversation(aliceName, bobName, bobDev, *store);

    string devId = conv1->lookupSecondaryDevId(4711);
    ASSERT_EQ(aliceDev, devId);
    ASSERT_EQ(RK, conv1->getRK());
    ASSERT_EQ(CKr, conv1->getCKr());
    ASSERT_EQ(CKs, conv1->getCKs());

    ASSERT_EQ(3, conv1->getNr());
    ASSERT_EQ(7, conv1->getNs());
    ASSERT_EQ(11, conv1->getPNs());
    ASSERT_EQ(13, conv1->getPreKeyId());
    ASSERT_TRUE(tst == conv1->getDeviceName());
}

///**
// * A base GTest (GoogleTest) text fixture class that supports memory leak checking.
// *
// * NOTE: when using this class as a base class for a test fixture,
// *       the derived class should not create any local member variables, as
// *       they can cause memory leaks to be improperly reported.
// */
//
//class BaseTestFixture : public ::testing::Test
//{
//public:
//    virtual void SetUp()
//    {
//        // capture the memory state at the beginning of the test
//        struct mallinfo minfo = mallinfo();
//        beginMemoryState = minfo.uordblks;
//    }
//
//    virtual void TearDown()
//    {
//        // only check for memory leaks if the test did not end in a failure
//        if (!HasFailure())
//        {
//            // Gets information about the currently running test.
//            // Do NOT delete the returned object - it's managed by the UnitTest class.
//            const ::testing::TestInfo* const test_info = ::testing::UnitTest::GetInstance()->current_test_info();
//
//            // if there are differences between the memory state at beginning and end, then there are memory leaks
//            struct mallinfo minfo = mallinfo();
//            if ( beginMemoryState != minfo.uordblks )
//            {
//                FAIL() << "Memory Leak(s) Detected in " << test_info->name() << ", test case: " << test_info->test_case_name() << endl;
//            }
//        }
//    }
//
//private:
//    // memory state at the beginning of the test fixture set up
//    int beginMemoryState;
//};
//
////----------------------------------------------------------------
//// check that allocating nothing doesn't throw a false positive
////----------------------------------------------------------------
//TEST_F(BaseTestFixture, BaseTestFixtureTest)
//{
//    // should always pass an empty test
//}
//
////----------------------------------------------------------------
//// check that malloc()ing something is detected
////----------------------------------------------------------------
//TEST_F(BaseTestFixture, BaseTestFixtureMallocTest)
//{
//    // should always fail
//    void* p = malloc(10);
//}
//
////----------------------------------------------------------------
//// check that new()ing something is detected
////----------------------------------------------------------------
//TEST_F(BaseTestFixture, BaseTestFixtureNewFailTest)
//{
//    // should always fail
//    int* array = new int[10];
//}
//
////----------------------------------------------------------------
////
////----------------------------------------------------------------
//TEST_F(BaseTestFixture, BaseTestFixtureNewTest)
//{
//    void* p = malloc(10);
//    free( p );
//}
