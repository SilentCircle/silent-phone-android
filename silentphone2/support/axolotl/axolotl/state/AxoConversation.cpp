#include "AxoConversation.h"
#include "../../storage/sqlite/SQLiteStoreConv.h"
#include "../../util/cJSON.h"
#include "../../util/b64helper.h"
#include "../Constants.h"
#include "../crypto/EcCurve.h"

#include <stdlib.h>
#include <iostream>
#include <time.h>

using namespace axolotl;
using namespace std;

void Log(const char* format, ...);

AxoConversation* AxoConversation::loadConversation(const string& localUser, const string& user, const string& deviceId)
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    if (!store->hasConversation(user, deviceId, localUser)) {
//        cerr << "No conversation: " << localUser << ", user: " << user << endl;
        return NULL;
    }

    // Create and lock a new conversation object _before_ loading data from database
    AxoConversation*  conv = new AxoConversation(localUser, user, deviceId);

    string* data = store->loadConversation(user, deviceId, localUser);
    if (data == NULL || data->empty()) {   // Illegal state, should not happen
//        cerr << "cannot load conversation" << endl;
        delete conv;
        return NULL;
    }
    conv->deserialize(*data);
    delete data;
    return conv;
}

void AxoConversation::storeConversation()
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();

    const string* data = serialize();

    store->storeConversation(partner_.getName(), deviceId_, localUser_, *data);
    memset_volatile((void*)data->data(), 0, data->size());

    delete data;
}

void AxoConversation::storeStagedMks()
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    while (!stagedMk->empty()) {
        string mkivmac = stagedMk->front();
        stagedMk->pop_front();
        store->insertStagedMk(partner_.getName(), deviceId_, localUser_, mkivmac);
    }
    delete stagedMk; stagedMk = NULL;

    // Cleanup old MKs
    time_t timestamp = time(0) - MK_STORE_TIME;
    store->deleteStagedMk(timestamp);
}

list<string>* AxoConversation::loadStagedMks()
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    list<string>* mks = store->loadStagedMks(partner_.getName(), deviceId_, localUser_);
    return mks;
}

void AxoConversation::deleteStagedMk(string& mkiv)
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    store->deleteStagedMk(partner_.getName(), deviceId_, localUser_, mkiv);
}

/* *****************************************************************************
 * Private functions
 ***************************************************************************** */

// No need to parse name, localName, partner name and device id. Already set
// with constructor.
void AxoConversation::deserialize(const std::string& data)
{
    cJSON* root = cJSON_Parse(data.c_str());

    cJSON* jsonItem = cJSON_GetObjectItem(root, "partner");
    string alias(cJSON_GetObjectItem(jsonItem, "alias")->valuestring);
    partner_.setAlias(alias);

    jsonItem = jsonItem = cJSON_GetObjectItem(root, "deviceName");
    if (jsonItem != NULL)
        deviceName_ = jsonItem->valuestring;

    char b64Buffer[MAX_KEY_BYTES_ENCODED*2] = {0};   // Twice the max. size on binary data - b64 is times 1.5
    uint8_t binBuffer[MAX_KEY_BYTES_ENCODED];  // Twice the max. size on binary data - b64 is times 1.5

    // Get RK b64 string, decode and store
    int32_t binLength;
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "RK")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    size_t b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
//        Log("++++ deserialize RK: b64length: %d, binLength: %d", b64Length, binLength);
        RK.assign((const char*)binBuffer, binLength);
    }

    // Get the DHRs key pair
    jsonItem = cJSON_GetObjectItem(root, "DHRs");
    strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "public")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        const DhPublicKey* pubKey = EcCurve::decodePoint(binBuffer);

        // Here we may check the public curve type and do some code to support different curves and
        // create to correct private key. The serilaized public key data contain a curve type id. For
        // the time being use Ec255 (DJB's curve 25519).
        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "private")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
        binLength = b64Decode(b64Buffer, strlen(b64Buffer), binBuffer, MAX_KEY_BYTES_ENCODED);
        const DhPrivateKey* privKey = EcCurve::decodePrivatePoint(binBuffer, binLength);

        DHRs = new DhKeyPair(*pubKey, *privKey);
        delete pubKey;
        delete privKey;
    }

    strncpy(b64Buffer, cJSON_GetObjectItem(root, "DHRr")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        DHRr = EcCurve::decodePoint(binBuffer);
    }

    // Get the DHIs key pair
    jsonItem = cJSON_GetObjectItem(root, "DHIs");
    strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "public")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        const DhPublicKey* pubKey = EcCurve::decodePoint(binBuffer);

        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "private")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
        binLength = b64Decode(b64Buffer, strlen(b64Buffer), binBuffer, MAX_KEY_BYTES_ENCODED);
        const DhPrivateKey* privKey = EcCurve::decodePrivatePoint(binBuffer, binLength);

        DHIs = new DhKeyPair(*pubKey, *privKey);
        delete pubKey;
        delete privKey;
    }
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "DHIr")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        DHIr = EcCurve::decodePoint(binBuffer);
    }

    // Get the A0 key pair
    jsonItem = cJSON_GetObjectItem(root, "A0");
    b64Length = strlen(cJSON_GetObjectItem(jsonItem, "public")->valuestring);
    if (b64Length > 0) {
        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "public")->valuestring, b64Length+1);
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        const DhPublicKey* pubKey = EcCurve::decodePoint(binBuffer);

        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "private")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
        binLength = b64Decode(b64Buffer, strlen(b64Buffer), binBuffer, MAX_KEY_BYTES_ENCODED);
        const DhPrivateKey* privKey = EcCurve::decodePrivatePoint(binBuffer, binLength);

        A0 = new DhKeyPair(*pubKey, *privKey);
        delete pubKey;
        delete privKey;
    }

    // Get CKs b64 string, decode and store
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "CKs")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        CKs.assign((const char*)binBuffer, binLength);
    }

    // Get CKr b64 string, decode and store
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "CKr")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        CKr.assign((const char*)binBuffer, binLength);
//        Log("++++ deserialize CKr: b64length: %d, binLength: %d", b64Length, binLength);
    }
    Ns = cJSON_GetObjectItem(root, "Ns")->valueint;
    Nr = cJSON_GetObjectItem(root, "Nr")->valueint;
    PNs = cJSON_GetObjectItem(root, "PNs")->valueint;
    preKeyId = cJSON_GetObjectItem(root, "preKyId")->valueint;
    ratchetFlag = (cJSON_GetObjectItem(root, "ratchet")->valueint == 0) ? false : true;

    jsonItem = cJSON_GetObjectItem(root, "zrtpState");
    if (jsonItem != NULL)
        zrtpVerifyState = jsonItem->valueint;

    jsonItem = cJSON_GetObjectItem(root, "preKeysAvail");
    if (jsonItem != NULL)
        availablePreKeys = jsonItem->valueint;
    cJSON_Delete(root); 
}

const std::string* AxoConversation::serialize() const
{
    cJSON *root = NULL;
    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5

    root = cJSON_CreateObject();

    cJSON* jsonItem;
    cJSON_AddItemToObject(root, "partner", jsonItem = cJSON_CreateObject());
    cJSON_AddStringToObject(jsonItem, "name", partner_.getName().c_str());
    cJSON_AddStringToObject(jsonItem, "alias", partner_.getAlias().c_str());

    cJSON_AddStringToObject(root, "deviceId", deviceId_.c_str());
    cJSON_AddStringToObject(root, "localUser", localUser_.c_str());
    cJSON_AddStringToObject(root, "deviceName", deviceName_.c_str());

    int32_t b64Len = b64Encode((const uint8_t*)RK.data(), RK.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
//    Log("++++ serialize RK: b64length: %d, inLength: %d - %s", b64Len, RK.size(), b64Buffer);
    cJSON_AddStringToObject(root, "RK", b64Buffer);


    // DHRs key pair, private, public
    cJSON_AddItemToObject(root, "DHRs", jsonItem = cJSON_CreateObject());
    if (DHRs != NULL) {
        b64Len = b64Encode((const uint8_t*)DHRs->getPrivateKey().privateData(), DHRs->getPrivateKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "private", b64Buffer);

        b64Len = b64Encode((const uint8_t*)DHRs->getPublicKey().serialize().data(), DHRs->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "public", b64Buffer);
    }
    else {
        cJSON_AddStringToObject(jsonItem, "private", "");
        cJSON_AddStringToObject(jsonItem, "public", "");
    }

    // DHRr key, public
    if (DHRr != NULL) {
        b64Len = b64Encode((const uint8_t*)DHRr->serialize().data(), DHRr->getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(root, "DHRr", b64Buffer);
    }
    else
        cJSON_AddStringToObject(root, "DHRr", "");

    // DHIs key pair, private, public
    cJSON_AddItemToObject(root, "DHIs", jsonItem = cJSON_CreateObject());
    if (DHIs != NULL) {
        b64Len = b64Encode((const uint8_t*)DHIs->getPrivateKey().privateData(), DHIs->getPrivateKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "private", b64Buffer);

        b64Len = b64Encode((const uint8_t*)DHIs->getPublicKey().serialize().data(), DHIs->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "public", b64Buffer);
    }
    else {
        cJSON_AddStringToObject(jsonItem, "private", "");
        cJSON_AddStringToObject(jsonItem, "public", "");
    }

    // DHIr key, public
    if (DHIr != NULL) {
        b64Len = b64Encode((const uint8_t*)DHIr->serialize().data(), DHIr->getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(root, "DHIr", b64Buffer);
    }
    else
        cJSON_AddStringToObject(root, "DHIr", "");


    // A0 key pair, private, public
    cJSON_AddItemToObject(root, "A0", jsonItem = cJSON_CreateObject());
    if (A0 != NULL) {
        b64Len = b64Encode((const uint8_t*)A0->getPrivateKey().privateData(), A0->getPrivateKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "private", b64Buffer);

        b64Len = b64Encode((const uint8_t*)A0->getPublicKey().serialize().data(), A0->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "public", b64Buffer);
    }
    else {
        cJSON_AddStringToObject(jsonItem, "private", "");
        cJSON_AddStringToObject(jsonItem, "public", "");
    }

    // The two chain keys
    b64Len = b64Encode((const uint8_t*)CKs.data(), CKs.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
//    Log("++++ serialize CKs: b64length: %d, inLength: %d", b64Len, CKs.size());
    cJSON_AddStringToObject(root, "CKs", b64Buffer);

    b64Len = b64Encode((const uint8_t*)CKr.data(), CKr.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
//    Log("++++ serialize CKr: b64length: %d, inLength: %d", b64Len, CKr.size());
    cJSON_AddStringToObject(root, "CKr", b64Buffer);

    cJSON_AddNumberToObject(root, "Ns", Ns);
    cJSON_AddNumberToObject(root, "Nr", Nr);
    cJSON_AddNumberToObject(root, "PNs", PNs);
    cJSON_AddNumberToObject(root, "preKyId", preKeyId);
    cJSON_AddNumberToObject(root, "ratchet", (ratchetFlag) ? 1 : 0);
    cJSON_AddNumberToObject(root, "zrtpState", zrtpVerifyState);
    cJSON_AddNumberToObject(root, "preKeysAvail", availablePreKeys);

    char *out = cJSON_Print(root);
    std::string* data = new std::string(out);
//    Log("%s", data->c_str());
    cJSON_Delete(root); free(out);

    return data;
}

void AxoConversation::reset()
{
    delete DHRs; DHRs = NULL;
    delete DHRr; DHRr = NULL;
    delete DHIs; DHIs = NULL;
    delete DHIr; DHIr = NULL; 
    delete A0; A0 = NULL;

    if (!CKr.empty())
        memset_volatile((void*)CKr.data(), 0 , CKr.size());
    CKr.clear();

    if (!CKs.empty())
        memset_volatile((void*)CKs.data(), 0 , CKs.size());
    CKs.clear();

    if (!RK.empty())
        memset_volatile((void*)RK.data(), 0 , RK.size());
    RK.clear();
    Nr = Ns = PNs = preKeyId = 0;
    ratchetFlag = false;
}
