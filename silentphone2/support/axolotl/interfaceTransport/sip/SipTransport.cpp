#include "SipTransport.h"
#include "../../storage/sqlite/SQLiteStoreConv.h"
#include "../../logging/AxoLogging.h"
#include <stdlib.h>
#include <map>
#include <thread>
#include <chrono>

#ifndef MAX_TIME_WAIT_FOR_SLOTS
#define MAX_TIME_WAIT_FOR_SLOTS 1500
#endif


using namespace axolotl;

#if defined (EMBEDDED)
static int32_t getNumOfSlots()
{
    void *getAccountByID(int id);
    int getInfo(void *pEng, const char *key, char *p, int iMax);

    char tmp[10] = {0};

    void *pEngine = getAccountByID(0);
    if (getInfo(pEngine, "getFreeSesCnt", tmp, 8) <= 0) {
        LOGGER(ERROR, __func__, " Get free sessions returned <= 0");
        return -1;
    }

    return atoi(tmp);
}
#endif

vector<int64_t>* SipTransport::sendAxoMessage(const string& recipient, vector<pair<string, string> >* msgPairs)
{
    LOGGER(INFO, __func__, " -->");
    int32_t numPairs = static_cast<int32_t>(msgPairs->size());

    vector<int64_t>* msgIdsReturn = new std::vector<int64_t>;

#if defined (EMBEDDED)
    int32_t availableSlots = getNumOfSlots();
    int32_t sumWaitTime = 0;

    LOGGER(INFO, __func__, " Number of session slots: ", availableSlots, ", required: ", numPairs );
    while (availableSlots < numPairs) {
        LOGGER(INFO, __func__, " Wait for session slots, available: ", availableSlots, ", required: ", numPairs );
        if (sumWaitTime > MAX_TIME_WAIT_FOR_SLOTS) {
            msgPairs->clear();
            LOGGER(ERROR, __func__, " Cannot get session slots to send messages: ", availableSlots, ", required: ", numPairs);
            return msgIdsReturn;        // return an empty vector, telling nothing sent
        }
        std::this_thread::sleep_for (std::chrono::milliseconds(150));
        sumWaitTime += 150;
        availableSlots = getNumOfSlots();
    }
#endif

    uint8_t** names = new uint8_t*[numPairs+1];
    uint8_t** devIds = new uint8_t*[numPairs+1];
    uint8_t** envelopes = new uint8_t*[numPairs+1];
    size_t*   sizes = new size_t[numPairs+1];
    uint64_t* msgIds = new uint64_t[numPairs+1];

    size_t index = 0;
    for(; index < numPairs; index++) {
        pair<string, string>& msgPair = msgPairs->at(index);
        names[index] = (uint8_t*)recipient.c_str();
        devIds[index] = (uint8_t*)msgPair.first.c_str();
        envelopes[index] = (uint8_t*)msgPair.second.data();
        sizes[index] = msgPair.second.size();
    }
    names[index] = NULL; devIds[index] = NULL; envelopes[index] = NULL; 

    sendAxoData_(names, devIds, envelopes, sizes, msgIds);

    // This should clear everything because no pointers involved
    msgPairs->clear();
    delete[] names; delete[] devIds; delete[] envelopes; delete[] sizes;

    for (int32_t i = 0; i < numPairs; i++) {
        if (msgIds[i] != 0)
            msgIdsReturn->push_back(msgIds[i]);
    }
    delete[] msgIds;
    LOGGER(INFO, __func__, " <--");
    return msgIdsReturn;
}

int32_t SipTransport::receiveAxoMessage(uint8_t* data, size_t length)
{
    LOGGER(INFO, __func__, " -->");
    string envelope((const char*)data, length);
    int32_t result = appInterface_->receiveMessage(envelope);
    LOGGER(INFO, __func__, " <--", result);

    return result;
}

int32_t SipTransport::receiveAxoMessage(uint8_t* data, size_t length, uint8_t* uid,  size_t uidLen,
                                        uint8_t* displayName, size_t dpNameLen) {
    LOGGER(INFO, __func__, " -->");
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

    int32_t result = appInterface_->receiveMessage(envelope, uidString, displayNameString);
    LOGGER(INFO, __func__, " <-- ", result);

    return result;
}

void SipTransport::stateReportAxo(int64_t messageIdentifier, int32_t stateCode, uint8_t* data, size_t length)
{
    LOGGER(INFO, __func__, " -->");
    std::string info;
    if (data != NULL) {
        info.assign((const char*)data, length);
    }
    appInterface_->stateReportCallback_(messageIdentifier, stateCode, info);
    LOGGER(INFO, __func__, " <--");
}

static string Zeros("00000000000000000000000000000000");
static map<string, string> seenIdStringsForName;

void SipTransport::notifyAxo(uint8_t* data, size_t length)
{
    LOGGER(INFO, __func__, " -->");
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

    size_t pos = 0;
    string devId;
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();

    bool newDevice = false;
    int32_t numReportedDevices = 0;
    while ((pos = devIds.find(';')) != string::npos) {
        devId = devIds.substr(0, pos);
        devIds.erase(0, pos + 1);
        if (Zeros.compare(0, devId.size(), devId) == 0) {
            continue;
        }
        numReportedDevices++;
        if (!store->hasConversation(name, devId, appInterface_->getOwnUser())) {
            newDevice = true;
            LOGGER(DEBUGGING, "New device detected: ", devId);
            break;
        }
    }
//     list<string>* devicesDb = store->getLongDeviceIds(name, appInterface_->getOwnUser());
//     int32_t numKnownDevices = devicesDb->size();
//     delete devicesDb;

//    Log("++++ number of devices: reported: %d, known: %d", numReportedDevices, numKnownDevices);

    if (newDevice /*|| numKnownDevices != numReportedDevices*/) {
//        Log("++++ calling notify callback");
        appInterface_->notifyCallback_(AppInterface::DEVICE_SCAN, name, devIdsSave);
    }
    LOGGER(INFO, __func__, " <--");
}

