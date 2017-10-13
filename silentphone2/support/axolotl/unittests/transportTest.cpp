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
// Created by werner on 19.07.17.
//

#include <malloc.h>
#include "gtest/gtest.h"

#include "../ratchet/state/ZinaConversation.h"
#include "../interfaceApp/AppInterfaceImpl.h"
#include "../interfaceTransport/sip/SipTransport.h"

using namespace zina;
using namespace std;

static std::string aliceName("alice@wonderland.org");
static std::string bobNameDomain("bob@milkyway.com");
static std::string bobName("bob");

static std::string aliceDev("aliceDevId");
static std::string bobDev("BobDevId");

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};

static bool callBackSeen = false;
static string userIdCallback;
static string infoCallBack;
static void notifyCallback(int32_t notifyActionCode, const std::string& userId, const std::string& actionInformation) {
    LOGGER(INFO, __func__, "callback: ", userId, ", ", actionInformation);
    callBackSeen = true;
    userIdCallback = userId;
    infoCallBack = actionInformation;
}


class TransportTestFixture: public ::testing::Test {
public:
    TransportTestFixture( ) = default;
        // initialization code here
        // cleanup any pending stuff, but no exceptions allowed

    // code here will execute just before the test ensues
    void SetUp() override {
        // capture the memory state at the beginning of the test
        struct mallinfo minfo = mallinfo();
        beginMemoryState = minfo.uordblks;

        LOGGER_INSTANCE setLogLevel(WARNING);

        store = SQLiteStoreConv::getStore();
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());

        appIf = new AppInterfaceImpl(store, aliceName, string("myAPI-key"), aliceDev);
        appIf->notifyCallback_ = notifyCallback;

        Transport* tpIf = new SipTransport(appIf);
        appIf->setTransport(tpIf);

        // setup a local user, remote use and its device id
        ZinaConversation conv(aliceName, bobName, bobDev);
        conv.storeConversation(*store);
    }

    void TearDown( ) override {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
        SQLiteStoreConv::closeStore();

        delete appIf;

        // only check for memory leaks if the test did not end in a failure

        // NOTE: the logging via LOGGER statements may give wrong results. Thus either switch of
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

    ~TransportTestFixture( ) override {
        // cleanup any pending stuff, but no exceptions allowed
        LOGGER_INSTANCE setLogLevel(VERBOSE);
    }

    TransportTestFixture(const TransportTestFixture& other) = delete;
    TransportTestFixture(const TransportTestFixture&& other) = delete;
    TransportTestFixture& operator= ( const TransportTestFixture& other ) = delete;
    TransportTestFixture& operator= ( const TransportTestFixture&& other ) = delete;

    // put in any custom data members that you need
    SQLiteStoreConv* store = nullptr;
    int beginMemoryState = 0;
    AppInterfaceImpl *appIf = nullptr;
};

// Notify data for Bob's known device as setup in the setup function above, no callback expected
static string notifyDataKnown = bobNameDomain + ":" + bobDev + ";" ;
TEST_F(TransportTestFixture, Known) {
    callBackSeen = false;
    appIf->getTransport()->notifyAxo(reinterpret_cast<const uint8_t *>(notifyDataKnown.data()), notifyDataKnown.size());
    ASSERT_FALSE(callBackSeen);
}

static string notifyDataNew = bobNameDomain + ":" + bobDev + "1;" ;
TEST_F(TransportTestFixture, New) {
    callBackSeen = false;
    appIf->getTransport()->notifyAxo(reinterpret_cast<const uint8_t *>(notifyDataNew.data()), notifyDataNew.size());
    ASSERT_TRUE(callBackSeen);
    ASSERT_EQ(bobName, userIdCallback);
    ASSERT_EQ(bobDev + "1;", infoCallBack);
    userIdCallback = string();              // reset with empty string - clear does not return memory
    infoCallBack = string();
}

static string notifyDataNoDev = bobNameDomain + ":" ;
TEST_F(TransportTestFixture, NoDev) {
    callBackSeen = false;
    appIf->getTransport()->notifyAxo(reinterpret_cast<const uint8_t *>(notifyDataNoDev.data()), notifyDataNoDev.size());
    ASSERT_TRUE(callBackSeen);
    ASSERT_EQ(bobName, userIdCallback);
    ASSERT_TRUE(infoCallBack.empty());
    userIdCallback = string();              // reset with empty string - clear does not return memory
    infoCallBack = string();
}

static string notifyDataNoDev_1 = bobNameDomain + ":;" ;
TEST_F(TransportTestFixture, NoDev_1) {
    callBackSeen = false;
    appIf->getTransport()->notifyAxo(reinterpret_cast<const uint8_t *>(notifyDataNoDev_1.data()), notifyDataNoDev_1.size());
    ASSERT_TRUE(callBackSeen);
    ASSERT_EQ(bobName, userIdCallback);
    ASSERT_EQ(";", infoCallBack);
    userIdCallback = string();              // reset with empty string - clear does not return memory
    infoCallBack = string();
}

TEST_F(TransportTestFixture, Combined) {
    callBackSeen = false;
    appIf->getTransport()->notifyAxo(reinterpret_cast<const uint8_t *>(notifyDataKnown.data()), notifyDataKnown.size());
    ASSERT_FALSE(callBackSeen);

    callBackSeen = false;
    appIf->getTransport()->notifyAxo(reinterpret_cast<const uint8_t *>(notifyDataNew.data()), notifyDataNew.size());
    ASSERT_TRUE(callBackSeen);
    ASSERT_EQ(bobName, userIdCallback);
    ASSERT_EQ(bobDev + "1;", infoCallBack);

    callBackSeen = false;
    appIf->getTransport()->notifyAxo(reinterpret_cast<const uint8_t *>(notifyDataNoDev.data()), notifyDataNoDev.size());
    ASSERT_TRUE(callBackSeen);
    ASSERT_EQ(bobName, userIdCallback);
    ASSERT_TRUE(infoCallBack.empty());
    userIdCallback = string();              // reset with empty string - clear does not return memory
    infoCallBack = string();
}
