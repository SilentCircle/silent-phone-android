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
// Created by werner on 26.01.17.
//

#include <string>

#include "gtest/gtest.h"
#include "../vectorclock/VectorClock.h"
#include "../vectorclock/VectorHelper.h"
#include "../storage/sqlite/SQLiteStoreConv.h"

using namespace std;
using namespace vectorclock;
using namespace zina;

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
static const void *inData = keyInData;

string groupId_1("group_id_1");
string groupId_2("group_id_2");

// Make each id 8 characters/bytes
string node_1("node_1--");
string node_2("node_2--");
string node_3("node_3--");
string node_4("node_4--");
string node_5("node_5--");
string node_6("node_6--");

string updateId_1("update_id_1");
string updateId_2("update_id_2");

class VectorClocksTestsFixture: public ::testing::Test {
public:
    VectorClocksTestsFixture( ) {
        // initialization code here
    }

    void SetUp() {
        // code here will execute just before the test ensues
        LOGGER_INSTANCE setLogLevel(ERROR);
        pks = SQLiteStoreConv::getStore();
        pks->setKey(std::string((const char*)keyInData, 32));
        pks->openStore(std::string());
    }

    void TearDown() {
        // code here will be called just after the test completes
        // ok to through exceptions from here if need be
        SQLiteStoreConv::closeStore();
    }

    ~VectorClocksTestsFixture()  {
        // cleanup any pending stuff, but no exceptions allowed
        LOGGER_INSTANCE setLogLevel(VERBOSE);
    }

    // put in any custom data members that you need
    SQLiteStoreConv* pks;
};


TEST_F(VectorClocksTestsFixture, EmptyTests) {
    VectorClock<string> vc;

    // An empty vector clock has no nodes, thus return 0 when reading a node's clock
    ASSERT_EQ(0, vc.getNodeClock(node_1));

    // increment adds it with value 1 if not yet available
    ASSERT_TRUE(vc.incrementNodeClock(node_1));
}

TEST_F(VectorClocksTestsFixture, InsertTests) {
    VectorClock<string> vc;

    ASSERT_TRUE(vc.insertNodeWithValue(node_1, 4711));
    ASSERT_EQ(4711, vc.getNodeClock(node_1));

    ASSERT_TRUE(vc.incrementNodeClock(node_1));
    ASSERT_EQ(4712, vc.getNodeClock(node_1));

    // Cannot insert a node a second time
    ASSERT_FALSE(vc.insertNodeWithValue(node_1, 4711));
    ASSERT_EQ(4712, vc.getNodeClock(node_1));
}

TEST_F(VectorClocksTestsFixture, MergeTests) {
    VectorClock<string> vc_1;

    auto vc_merged = vc_1.merge(vc_1);
    ASSERT_EQ(0, vc_merged->size());

    ASSERT_TRUE(vc_1.insertNodeWithValue(node_1, 4711));
    ASSERT_TRUE(vc_1.insertNodeWithValue(node_2, 4712));

    // Merged is the same as vc_1, same length
    vc_merged = vc_1.merge(vc_1);
    ASSERT_EQ(2, vc_merged->size());
    ASSERT_EQ(4711, vc_merged->getNodeClock(node_1));
    ASSERT_EQ(4712, vc_merged->getNodeClock(node_2));

    VectorClock<string> vc_2;
    ASSERT_TRUE(vc_2.insertNodeWithValue(node_3, 815));
    ASSERT_TRUE(vc_2.insertNodeWithValue(node_4, 816));

    // Merge vc_1 and vc_2, length must be 4 now, check content
    vc_merged = vc_1.merge(vc_2);
    ASSERT_EQ(4, vc_merged->size());
    ASSERT_EQ(4711, vc_merged->getNodeClock(node_1));
    ASSERT_EQ(4712, vc_merged->getNodeClock(node_2));
    ASSERT_EQ(815, vc_merged->getNodeClock(node_3));
    ASSERT_EQ(816, vc_merged->getNodeClock(node_4));

    // Assume we somehow have now got new event from node_1, thus increment
    // merge again with vc_merged, should have new value of node_1
    ASSERT_TRUE(vc_1.incrementNodeClock(node_1));   // node_1's clock is now 4712

    vc_merged = vc_merged->merge(vc_1);
    ASSERT_EQ(4, vc_merged->size());
    ASSERT_EQ(4712, vc_merged->getNodeClock(node_1));
    ASSERT_EQ(4712, vc_merged->getNodeClock(node_2));
    ASSERT_EQ(815, vc_merged->getNodeClock(node_3));
    ASSERT_EQ(816, vc_merged->getNodeClock(node_4));

    // Merge with empty vector clock
    VectorClock<string> vc_3;
    vc_merged = vc_3.merge(*vc_merged);
    ASSERT_EQ(4, vc_merged->size());
    ASSERT_EQ(4712, vc_merged->getNodeClock(node_1));
    ASSERT_EQ(4712, vc_merged->getNodeClock(node_2));
    ASSERT_EQ(815, vc_merged->getNodeClock(node_3));
    ASSERT_EQ(816, vc_merged->getNodeClock(node_4));
}

TEST_F(VectorClocksTestsFixture, CompareTests) {
    VectorClock<string> vc_1;

    // Compare empty clock with itself
    ASSERT_EQ(Equal, vc_1.compare(vc_1));

    ASSERT_TRUE(vc_1.insertNodeWithValue(node_1, 4711));
    ASSERT_TRUE(vc_1.insertNodeWithValue(node_2, 4712));
    ASSERT_EQ(Equal, vc_1.compare(vc_1));

    VectorClock<string> vc_2;

    // vc_2's node_1 is greater than in vc_1, thus vc_1 is smaller: Before
    ASSERT_TRUE(vc_2.insertNodeWithValue(node_1, 4712));
    ASSERT_TRUE(vc_2.insertNodeWithValue(node_2, 4712));
    ASSERT_EQ(Before, vc_1.compare(vc_2));

    // Just reverse the test
    ASSERT_EQ(After, vc_2.compare(vc_1));

    // Test concurrency, each vector clock has a value on different nodes greater than the other
    ASSERT_TRUE(vc_1.incrementNodeClock(node_2));   // node_2's clock is now 4713
    ASSERT_EQ(Concurrent,vc_2.compare(vc_1));

    // Compare with empty vector clock
    VectorClock<string> vc_3;
    ASSERT_EQ(Before, vc_3.compare(vc_2));

    // Reverse
    ASSERT_EQ(After, vc_2.compare(vc_3));
}

TEST_F(VectorClocksTestsFixture, PersitenceTests) {
    string someData_1("some data_1\1\2");
    string someData_2;
    someData_2.assign(static_cast<const char*>(inData), sizeof(keyInData));

    // Store and check some data
    int32_t result = pks->insertReplaceVectorClock(node_1, 1, someData_1);
    ASSERT_FALSE(SQL_FAIL(result));

    string read_1;
    result = pks->loadVectorClock(node_1, 1, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_EQ(someData_1, read_1);

    // Replace existing with some other data and check
    result = pks->insertReplaceVectorClock(node_1, 1, someData_2);
    ASSERT_FALSE(SQL_FAIL(result));

    result = pks->loadVectorClock(node_1, 1, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_EQ(someData_2, read_1);
    ASSERT_EQ(someData_2.size(), read_1.size());

    // Delete this record, check
    result = pks->deleteVectorClock(node_1, 1);
    ASSERT_FALSE(SQL_FAIL(result));

    result = pks->loadVectorClock(node_1, 1, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_TRUE(read_1.empty());

    // Insert a few records for different nodes, different types, check
    result = pks->insertReplaceVectorClock(node_1, 1, someData_1);
    ASSERT_FALSE(SQL_FAIL(result));

    result = pks->insertReplaceVectorClock(node_1, 2, someData_2);
    ASSERT_FALSE(SQL_FAIL(result));

    result = pks->insertReplaceVectorClock(node_2, 1, someData_2);
    ASSERT_FALSE(SQL_FAIL(result));

    result = pks->insertReplaceVectorClock(node_2, 2, someData_1);
    ASSERT_FALSE(SQL_FAIL(result));

    result = pks->loadVectorClock(node_1, 1, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_EQ(someData_1, read_1);
    ASSERT_EQ(someData_1.size(), read_1.size());

    result = pks->loadVectorClock(node_1, 2, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_EQ(someData_2, read_1);
    ASSERT_EQ(someData_2.size(), read_1.size());

    result = pks->loadVectorClock(node_2, 1, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_EQ(someData_2, read_1);
    ASSERT_EQ(someData_2.size(), read_1.size());

    result = pks->loadVectorClock(node_2, 2, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_EQ(someData_1, read_1);
    ASSERT_EQ(someData_1.size(), read_1.size());

    // Delete a whole vector clock group, check
    result = pks->deleteVectorClocks(node_1);
    ASSERT_FALSE(SQL_FAIL(result));

    // Both must return an empty string
    result = pks->loadVectorClock(node_1, 1, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_TRUE(read_1.empty());

    result = pks->loadVectorClock(node_1, 2, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_TRUE(read_1.empty());

    // Delete event type 1 record of node_2 group, check
    result = pks->deleteVectorClock(node_2, 1);
    ASSERT_FALSE(SQL_FAIL(result));

    // Event type 1 vector clock gone
    result = pks->loadVectorClock(node_2, 1, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_TRUE(read_1.empty());

    // Event type 2 vector clock still available
    result = pks->loadVectorClock(node_2, 2, &read_1);
    ASSERT_FALSE(SQL_FAIL(result));
    ASSERT_EQ(someData_1, read_1);
    ASSERT_EQ(someData_1.size(), read_1.size());
}

TEST_F(VectorClocksTestsFixture, HelperLocalTests) {

    LocalVClock lvc;
    
    lvc.set_update_id(updateId_1);
    
    VClock *vc = lvc.add_vclock();
    vc->set_device_id(node_1);
    vc->set_value(1);

    vc = lvc.add_vclock();
    vc->set_device_id(node_2);
    vc->set_value(2);

    LocalVClock read_lvc;

    // Fail to store, illegal update type
    int32_t result = storeLocalVectorClock(*pks, groupId_1, TYPE_NONE, lvc);
    ASSERT_EQ(WRONG_UPDATE_TYPE, result);

    // Fail to store, illegal update type
    result = storeLocalVectorClock(*pks, groupId_1, (GroupUpdateType)20, lvc);
    ASSERT_EQ(WRONG_UPDATE_TYPE, result);

    result = storeLocalVectorClock(*pks, groupId_1, GROUP_SET_NAME, lvc);
    ASSERT_EQ(SUCCESS, result);

    // Fail to read, wrong group id
    result = readLocalVectorClock(*pks, groupId_2, GROUP_SET_NAME, &read_lvc);
    ASSERT_EQ(NO_VECTOR_CLOCK, result);

    // Fail to read, wrong update type
    result = readLocalVectorClock(*pks, groupId_1, GROUP_SET_BURN, &read_lvc);
    ASSERT_EQ(NO_VECTOR_CLOCK, result);

    // Read must succeed, we have 2 VClocks, in same order as added before
    result = readLocalVectorClock(*pks, groupId_1, GROUP_SET_NAME, &read_lvc);
    ASSERT_EQ(SUCCESS, result);
    ASSERT_EQ(2, read_lvc.vclock_size());
    
    ASSERT_EQ(updateId_1, read_lvc.update_id());

    ASSERT_EQ(node_1, read_lvc.vclock(0).device_id());
    ASSERT_EQ(1, read_lvc.vclock(0).value());

    ASSERT_EQ(node_2, read_lvc.vclock(1).device_id());
    ASSERT_EQ(2, read_lvc.vclock(1).value());
}

TEST_F(VectorClocksTestsFixture, CopyTests) {

    LocalVClock lvc;

    lvc.set_update_id(updateId_1);

    VClock *vc = lvc.add_vclock();
    vc->set_device_id(node_1);
    vc->set_value(1);

    vc = lvc.add_vclock();
    vc->set_device_id(node_2);
    vc->set_value(2);

    LocalVClock copied_lvc;

    copied_lvc.mutable_vclock()->CopyFrom(lvc.vclock());

    ASSERT_EQ(2, copied_lvc.vclock_size());

    ASSERT_EQ(node_1, copied_lvc.vclock(0).device_id());
    ASSERT_EQ(1, copied_lvc.vclock(0).value());

    ASSERT_EQ(node_2, copied_lvc.vclock(1).device_id());
    ASSERT_EQ(2, copied_lvc.vclock(1).value());
}