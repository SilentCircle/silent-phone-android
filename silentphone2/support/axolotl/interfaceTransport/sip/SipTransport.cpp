#include "SipTransport.h"
#include "../../storage/sqlite/SQLiteStoreConv.h"
#include <iostream>

using namespace axolotl;

void Log(const char* format, ...);

vector< int64_t >* SipTransport::sendAxoMessage(const string& recipient, vector< pair< string, string > >* msgPairs)
{
    int32_t numPairs = msgPairs->size();

    uint8_t** names = new uint8_t*[numPairs+1];
    uint8_t** devIds = new uint8_t*[numPairs+1];
    uint8_t** envelopes = new uint8_t*[numPairs+1];
    size_t*   sizes = new size_t[numPairs+1];
    uint64_t* msgIds = new uint64_t[numPairs+1];

    int32_t index = 0;
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
    delete names; delete devIds; delete envelopes; delete sizes;

    vector<int64_t>* msgIdsReturn = new std::vector<int64_t>;
    for (int32_t i = 0; i < numPairs; i++) {
        if (msgIds[i] != 0)
            msgIdsReturn->push_back(msgIds[i]);
    }
    delete msgIds;
    return msgIdsReturn;
}

int32_t SipTransport::receiveAxoMessage(uint8_t* data, size_t length)
{
    string envelope((const char*)data, length);
    int32_t result = appInterface_->receiveMessage(envelope);

    return result;
}

void SipTransport::stateReportAxo(int64_t messageIdentifier, int32_t stateCode, uint8_t* data, size_t length)
{
    std::string info;
    if (data != NULL) {
        info.assign((const char*)data, length);
    }
    appInterface_->stateReportCallback_(messageIdentifier, stateCode, info);
}

static string Zeros("00000000000000000000000000000000");

void SipTransport::notifyAxo(uint8_t* data, size_t length)
{
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

    size_t pos = 0;
    string devId;
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();

    bool newDevice = false;
    while ((pos = devIds.find(';')) != string::npos) {
        devId = devIds.substr(0, pos);
        devIds.erase(0, pos + 1);
        if (Zeros.compare(0, devId.size(), devId) == 0) {
            continue;
        }
        if (!store->hasConversation(name, devId, appInterface_->getOwnUser())) {
            newDevice = true;
            break;
        }
    }
    if (newDevice)
        appInterface_->notifyCallback_(AppInterface::DEVICE_SCAN, name, devIdsSave);
}

