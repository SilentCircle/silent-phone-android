#include <limits.h>
#include "gtest/gtest.h"

#include "../appRepository/AppRepository.h"
#include <list>

using namespace axolotl;
using namespace std;

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};

TEST(AppRestore, Conversation)
{
    AppRepository* store = AppRepository::getStore();
    store->setKey(std::string((const char*)keyInData, 32));
    store->openStore(string());
    
    ASSERT_TRUE(NULL != store);
    
    std::string data("This is some test data");
    std::string name("partner");
    
    int32_t sqlCode = store->storeConversation(name, data);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    
    std::string readData;
    sqlCode = store->loadConversation(name, &readData);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(data, readData) << "data mistmatch";
    
    list<string>* names = store->listConversations();
    ASSERT_TRUE(names != NULL);
    ASSERT_EQ(1, names->size());
    string ps = names->front();
    ASSERT_EQ(name, ps);
    names->pop_front();
    delete names;
}

TEST(AppRestore, Event)
{
    AppRepository* store = AppRepository::getStore();

    ASSERT_TRUE(NULL != store);

    string data("This is some test data");
    string name("partner");

    string msg("some message data");
    string msgId("first");
    int32_t sqlCode = store->insertEvent(name, msgId, msg);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    int32_t msgNumber;
    string readData;
    sqlCode = store->loadEvent(name, msgId, &readData, &msgNumber);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(msg, readData) << "data mistmatch";

    int32_t msgNum = store->getHighestMsgNum(name);
    ASSERT_EQ(1, msgNum);

    // Read with msg id only
    readData.clear();
    sqlCode = store->loadEventWithMsgId(msgId, &readData);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(msg, readData) << "data mistmatch";

    for (int32_t i = 0; i < 10; i++) {
        char c = i + 0x30;
        std::string id = msgId;
        std::string data = msg;
        data.append(1, c);
        id.append(1, c);
        sqlCode = store->insertEvent(name, id, data);
        ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    }
    msgNum = store->getHighestMsgNum(name);
    ASSERT_EQ(11, msgNum);

    std::list<std::string*> result;
    store->loadEvents(name, -1, -1, &result, &msgNumber);
    ASSERT_EQ(11, result.size());

    while (!result.empty()) {
        std::string* msg = result.front();
        result.pop_front();
//        std::cerr << *msg << std::endl;
        delete msg;
    }

    sqlCode = store->loadEvents(name, -1, 5, &result, &msgNumber);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(5, result.size());

    while (!result.empty()) {
        std::string* msg = result.front();
        result.pop_front();
//        std::cerr << *msg << std::endl;
        delete msg;
    }
    result.clear();

    sqlCode = store->loadEvents(name, 2, 3, &result, &msgNumber);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(3, result.size());

    while (!result.empty()) {
        std::string* msg = result.front();
        result.pop_front();
//        std::cerr << *msg << std::endl;
        delete msg;
    }
    // The delete should fail with a constraint problem.
    sqlCode = store->deleteConversation(name);
    ASSERT_EQ(SQLITE_CONSTRAINT, sqlCode);
    
    sqlCode = store->deleteEvent(name, msgId);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    readData.clear();
    sqlCode = store->loadEvent(name, msgId, &readData, &msgNumber);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_TRUE(readData.empty());

    // Delete all events for this conversation
    sqlCode = store->deleteEventName(name);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // Now the delete of the conversation should succeed.
    sqlCode = store->deleteConversation(name);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    AppRepository::closeStore();
}

TEST(AppRestore, Object)
{
    AppRepository* store = AppRepository::getStore();
    store->setKey(std::string((const char*)keyInData, 32));
    store->openStore(string());
    ASSERT_TRUE(NULL != store);

    std::string data("This is some test data");
    std::string name("partner");
    
    // Insert a conversation
    int32_t sqlCode = store->storeConversation(name, data);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // Inset a event for the conversation
    std::string msg("some message data");
    std::string msgId("first");
    sqlCode = store->insertEvent(name, msgId, msg);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    std::string obj("some object data");
    std::string objId("firstObj");
    sqlCode = store->insertObject(name, msgId, objId, obj);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    std::string readData;
    sqlCode = store->loadObject(name, msgId, objId, &readData);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(obj, readData) << "data mistmatch";

    for (int32_t i = 0; i < 10; i++) {
        char c = i + 0x30;
        std::string id = objId;
        std::string data = obj;
        data.append(1, c);
        id.append(1, c);
        sqlCode = store->insertObject(name, msgId, id, data);
        ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    }

    std::list<std::string*> result;
    store->loadObjects(name, msgId, &result);
    ASSERT_EQ(11, result.size());

    // Delete the event should fail with constraint error
    sqlCode = store->deleteEvent(name, msgId);
    ASSERT_EQ(SQLITE_CONSTRAINT, sqlCode);

    // Delete all events for this event
    sqlCode = store->deleteObjectMsg(name, msgId);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // Delete the event should  succeed.
    sqlCode = store->deleteEvent(name, msgId);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
}

static string Empty;
TEST(AppRestore, AttachmentStatus)
{
    AppRepository* store = AppRepository::getStore();
    store->setKey(string((const char*)keyInData, 32));
    int32_t sqlCode = store->openStore(string());
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_TRUE(NULL != store);

    string msgId_1("msgid_1");
    string msgId_2("msgid_2");
    string msgId_3("msgid_3");
    string msgId_4("msgid_4");
    string msgId_5("msgid_5");

    sqlCode = store->storeAttachmentStatus(msgId_1, Empty, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    int32_t status;
    sqlCode = store->loadAttachmentStatus(msgId_1, Empty, &status);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(1, status) << "status mismatch";

    // Update the message status to 2
    sqlCode = store->storeAttachmentStatus(msgId_1, Empty, 2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // Check the new status
    sqlCode = store->loadAttachmentStatus(msgId_1, Empty, &status);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(2, status) << "status mismatch";

    // Delete the msg id and check, non existent id returns -1 as status
    sqlCode = store->deleteAttachmentStatus(msgId_1, Empty);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    sqlCode = store->loadAttachmentStatus(msgId_1, Empty, &status);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(-1, status) << "status mismatch" << store->getLastError();

    // Add 4 different msg ids with the same status
    sqlCode = store->storeAttachmentStatus(msgId_1, Empty, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_2, Empty, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_3, Empty, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_4, Empty, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    list<string> msgids;
    sqlCode = store->loadMsgsIdsWithAttachmentStatus(1, &msgids);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(4, msgids.size()) << "msg id list not correct size" << store->getLastError();
    msgids.clear();

    // Update 2 msg ids with a new status
    sqlCode = store->storeAttachmentStatus(msgId_3, Empty, 2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_4, Empty, 2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // delete the msg ids with status 2
    sqlCode = store->deleteWithAttachmentStatus(2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // No entries with status 2
    sqlCode = store->loadMsgsIdsWithAttachmentStatus(2, &msgids);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(0, msgids.size()) << "msg id list not correct size after delete (status 2)" << store->getLastError();
    msgids.clear();

    // No entries with status 2
    sqlCode = store->loadMsgsIdsWithAttachmentStatus(1, &msgids);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(2, msgids.size()) << "msg id list not correct size after delete (status 1)" << store->getLastError();
    msgids.clear();
}

static string Partner("partner");
TEST(AppRestore, AttachmentStatusPartner)
{
    AppRepository* store = AppRepository::getStore();
    store->setKey(string((const char*)keyInData, 32));
    int32_t sqlCode = store->openStore(string());
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_TRUE(NULL != store);

    string msgId_1("msgid_1");
    string msgId_2("msgid_2");
    string msgId_3("msgid_3");
    string msgId_4("msgid_4");
    string msgId_5("msgid_5");

    sqlCode = store->storeAttachmentStatus(msgId_1, Partner, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    int32_t status;
    sqlCode = store->loadAttachmentStatus(msgId_1, Partner, &status);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(1, status) << "status mismatch";

    // Update the message status to 2
    sqlCode = store->storeAttachmentStatus(msgId_1, Partner, 2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // Check the new status
    sqlCode = store->loadAttachmentStatus(msgId_1, Partner, &status);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(2, status) << "status mismatch";

    // Delete the msg id and check, non existent id returns -1 as status
    sqlCode = store->deleteAttachmentStatus(msgId_1, Partner);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    sqlCode = store->loadAttachmentStatus(msgId_1, Partner, &status);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(-1, status) << "status mismatch" << store->getLastError();

    // Add 4 different msg ids with the same status
    sqlCode = store->storeAttachmentStatus(msgId_1, Partner, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_2, Partner, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_3, Partner, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_4, Partner, 1);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    list<string> msgids;
    sqlCode = store->loadMsgsIdsWithAttachmentStatus(1, &msgids);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(4, msgids.size()) << "msg id list not correct size" << store->getLastError();
    msgids.clear();

    // Update 2 msg ids with a new status
    sqlCode = store->storeAttachmentStatus(msgId_3, Partner, 2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    sqlCode = store->storeAttachmentStatus(msgId_4, Partner, 2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // delete the msg ids with status 2
    sqlCode = store->deleteWithAttachmentStatus(2);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();

    // No entries with status 2
    sqlCode = store->loadMsgsIdsWithAttachmentStatus(2, &msgids);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(0, msgids.size()) << "msg id list not correct size after delete (status 2)" << store->getLastError();
    msgids.clear();

    // No entries with status 2
    sqlCode = store->loadMsgsIdsWithAttachmentStatus(1, &msgids);
    ASSERT_FALSE(SQL_FAIL(sqlCode)) << store->getLastError();
    ASSERT_EQ(2, msgids.size()) << "msg id list not correct size after delete (status 1)" << store->getLastError();
    msgids.clear();
}