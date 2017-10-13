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
#include "SipTransport.h"

#include <thread>
#include <condition_variable>
#include <cstdlib>

#ifndef MAX_TIME_WAIT_FOR_SLOTS
#define MAX_TIME_WAIT_FOR_SLOTS 1500
#endif

// In silentphone, tiviandroid/t_a_main, at/around line 1280: ph = new CTiViPhone(this,&strings,30);
// #define MAX_AVAILABLE_SLOTS     30
#define KEEP_SLOTS              10

using namespace std;
using namespace zina;

static int32_t getNumOfSlots()
{
#if defined (EMBEDDED)
    void *getAccountByID(int id);
    int getInfo(void *pEng, const char *key, char *p, int iMax);

    char tmp[10] = {0};

    void *pEngine = getAccountByID(0);
    if (getInfo(pEngine, "getFreeSesCnt", tmp, 8) <= 0) {
        LOGGER(ERROR, __func__, " Get free sessions returned <= 0");
        return -1;
    }

    return atoi(tmp);
#else
    return 1000;
#endif
}

typedef struct SendMsgInfo_ {
    string recipient;
    string deviceId;
    string envelope;
    uint64_t transportMsgId = 0;
} SendMsgInfo;

static mutex sendListLock;
static list<shared_ptr<SendMsgInfo> > sendMessageList;

static mutex threadLock;
static mutex runLock;
static condition_variable sendCv;
static thread sendThread;
static bool runSend;
static bool sendingActive;

static string Zeros("00000000000000000000000000000000");
static map<string, string> seenIdStringsForName;

// Send queued messages, one at a time, if SIP slots are available
// Don't use every available slot, leave some for other processing, if too few slots
// available wait some time and check again until the queue is empty
static void runSendQueue(SEND_DATA_FUNC sendAxoData, SipTransport* transport)
{
    int64_t sleepTime = 500;
    LOGGER(DEBUGGING, __func__, " -->");

    unique_lock<mutex> run(runLock);
    while (sendingActive) {
        while (!runSend) sendCv.wait(run);

        unique_lock<mutex> listLock(sendListLock);
        for (; !sendMessageList.empty(); sendMessageList.pop_front()) {
            for (int32_t slots = getNumOfSlots(); slots < KEEP_SLOTS;) {
                listLock.unlock();
                std::this_thread::sleep_for (std::chrono::milliseconds(sleepTime));
                listLock.lock();
                slots = getNumOfSlots();
            }
            shared_ptr<SendMsgInfo>& sendInfo = sendMessageList.front();
            bool result = sendAxoData((uint8_t*)sendInfo->recipient.c_str(), (uint8_t*)sendInfo->deviceId.c_str(),
                                       (uint8_t*)sendInfo->envelope.data(), sendInfo->envelope.size(), sendInfo->transportMsgId);
            if (!result) {
                LOGGER(ERROR, "Transport sendAxoData returned false, message not sent.");
                transport->stateReportAxo(sendInfo->transportMsgId, 503, (uint8_t*)sendInfo->recipient.c_str(), sendInfo->recipient.size());
            }
        }
        runSend = false;
        listLock.unlock();
    }
}

SipTransport::~SipTransport() {
    seenIdStringsForName.clear();
}

void SipTransport::sendAxoMessage(const CmdQueueInfo &info, const string& envelope)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (!sendThread.joinable()) {
        unique_lock<mutex> lck(threadLock);
        if (!sendThread.joinable()) {
            sendingActive = true;
            sendThread = thread(runSendQueue, sendAxoData_, this);
        }
        lck.unlock();
    }
    unique_lock<mutex> listLock(sendListLock);

    // Store all relevant data to send a message in a structure, queue the message
    // info structure.
    shared_ptr<SendMsgInfo> msgInfo = make_shared<SendMsgInfo>();
    msgInfo->recipient = info.queueInfo_recipient;
    msgInfo->deviceId = info.queueInfo_deviceId;
    msgInfo->envelope = envelope;
    uint64_t typeMask = (info.queueInfo_transportMsgId & MSG_TYPE_MASK) >= GROUP_MSG_NORMAL ? GROUP_TRANSPORT : 0;
    msgInfo->transportMsgId = info.queueInfo_transportMsgId | typeMask;
    sendMessageList.push_back(msgInfo);

    runSend = true;
    sendCv.notify_one();
    listLock.unlock();

    LOGGER(DEBUGGING, __func__, " <--");
}

int32_t SipTransport::receiveAxoMessage(uint8_t* data, size_t length)
{
    LOGGER(DEBUGGING, __func__, " -->");
    int32_t result = receiveAxoMessage(data, length, nullptr, 0, nullptr, 0);
    LOGGER(DEBUGGING, __func__, " <--", result);

    return result;
}

int32_t SipTransport::receiveAxoMessage(uint8_t* data, size_t length, uint8_t* uid,  size_t uidLen,
                                        uint8_t* displayName, size_t dpNameLen) {
    LOGGER(DEBUGGING, __func__, " -->");

    if (length > MAX_ENCODED_MSG_LENGTH) {
        LOGGER(ERROR, __func__, " Ignore a too long message: ", length);
        return OK;                  // Silently ignore, server should drop it as well
    }
    string envelope((const char *) data, length);

    string uidString;
    if (uid != NULL && uidLen > 0) {
        uidString.assign((const char *) uid, uidLen);

        std::size_t found = uidString.find(scSipDomain);
        if (found != string::npos) {
            uidString = uidString.substr(0, found);
        }
    }
    string displayNameString;
    if (displayName != NULL && dpNameLen > 0) {
        displayNameString.assign((const char *) displayName, dpNameLen);

        size_t found = displayNameString.find(scSipDomain);
        if (found != string::npos) {
            displayNameString = displayNameString.substr(0, found);
        }
    }
    LOGGER(DEBUGGING, __func__, " <-- ");
    return appInterface_->receiveMessage(envelope, uidString, displayNameString);
}

void SipTransport::stateReportAxo(int64_t messageIdentifier, int32_t stateCode, uint8_t* data, size_t length)
{
    LOGGER(DEBUGGING, __func__, " -->");
    std::string info;
    if (data != NULL) {
        info.assign((const char*)data, length);
    }
    if ((messageIdentifier & GROUP_TRANSPORT) == GROUP_TRANSPORT)
        appInterface_->groupStateReportCallback_(stateCode, info);
    else
        appInterface_->stateReportCallback_(messageIdentifier, stateCode, info);
    LOGGER(DEBUGGING, __func__, " <--");
}

void SipTransport::notifyAxo(const uint8_t* data, size_t length)
{
    LOGGER(DEBUGGING, __func__, " -->");
    string info((const char*)data, length);
    /*
     * notify call back from SIP:
     *   - parse data from SIP, get name and devices
     *   - check for new devices (store->hasConversation() )
     *   - if a new device was found call appInterface_->notifyCallback(...)
     *     NOTE: the notifyCallback function in app should return ASAP, queue/trigger actions only
     *   - done
     */

    size_t found = info.find(':');
    if (found == string::npos)        // No colon? No name -> return
        return;

    string name = info.substr(0, found);
    size_t foundAt = name.find('@');
    if (foundAt != string::npos) {
        name = name.substr(0, foundAt);
    }

    string devIds = info.substr(found + 1);
    string devIdsSave(devIds);

    // This is a check if the SIP server already sent the same notify string for a name
    map<string, string>::iterator it;
    it = seenIdStringsForName.find(name);
    if (it != seenIdStringsForName.end()) {
        // Found an entry, check if device ids match, if yes -> return, already processed,
        // if no -> delete the entry, continue processing which will add the new entry.
        if (it->second == devIdsSave) {
            return;
        }
        else {
            seenIdStringsForName.erase(it);
        }
    }
    pair<map<string, string>::iterator, bool> ret;
    ret = seenIdStringsForName.insert(pair<string, string>(name, devIdsSave));
    if (!ret.second) {
        LOGGER(ERROR, "Caching of notified device ids failed: ", name, ", ", devIdsSave);
    }

    const bool isSibling = appInterface_->getOwnUser() == name;

    size_t pos = 0;
    string devId;
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();

    size_t numReportedDevices = 0;
    bool newDevice = false;
    while ((pos = devIds.find(';')) != string::npos) {
        devId = devIds.substr(0, pos);
        devIds.erase(0, pos + 1);
        if (Zeros.compare(0, devId.size(), devId) == 0) {
            continue;
        }
        if (isSibling && appInterface_->getOwnDeviceId() == devId) {
            continue;
        }
        numReportedDevices++;
        if (!store->hasConversation(name, devId, appInterface_->getOwnUser())) {
            newDevice = true;
            break;
        }
    }
    list<StringUnique> devicesDb;
    store->getLongDeviceIds(name, appInterface_->getOwnUser(), devicesDb);
    size_t numKnownDevices = devicesDb.size();

    // If we saw a new device or the number of reported and known devices differs the user
    // added or removed a device, re-scan devices
    if (newDevice || numKnownDevices != numReportedDevices) {
        LOGGER(INFO, __func__, " Calling notify callback for: ", name, ", device: ", devIdsSave);
        appInterface_->notifyCallback_(AppInterface::DEVICE_SCAN, name, devIdsSave);
    }
    LOGGER(DEBUGGING, __func__, " <--");
}

