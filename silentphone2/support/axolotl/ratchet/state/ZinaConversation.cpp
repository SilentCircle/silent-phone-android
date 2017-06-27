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
#include "ZinaConversation.h"
#include "../../util/b64helper.h"
#include "../crypto/EcCurve.h"
#include "../../util/Utilities.h"

using namespace zina;
using namespace std;

void Log(const char* format, ...);

unique_ptr<ZinaConversation>
ZinaConversation::loadConversation(const string& localUser, const string& user, const string& deviceId, SQLiteStoreConv &store)
{
    LOGGER(DEBUGGING, __func__, " -->");
    int32_t result;

    // Create new conversation object
    auto conv = unique_ptr<ZinaConversation>(new ZinaConversation(localUser, user, deviceId));
    conv->setErrorCode(SUCCESS);

    bool found = store.hasConversation(user, deviceId, localUser, &result);
    if (SQL_FAIL(result)) {
        conv->errorCode_ = DATABASE_ERROR;
        conv->sqlErrorCode_ = result;
        return conv;
    }
    if (!found) {
        LOGGER(INFO, __func__, " <-- No such conversation, return empty conversation: ", user, ", device: ", deviceId);
        return conv;            // SUCCESS, however return an empty conversation
    }

    string* data = store.loadConversation(user, deviceId, localUser, &result);
    if (SQL_FAIL(result)) {
        conv->errorCode_ = DATABASE_ERROR;
        return conv;
    }
    if (data == NULL || data->empty()) {   // Illegal state, should not happen
        LOGGER(ERROR, __func__, " <-- Cannot load conversation data: ", user, ", ", deviceId);
        conv->errorCode_ = NO_SESSION_DATA;
        return conv;
    }

    conv->deserialize(*data);
    delete data;
    conv->valid_ = true;
    LOGGER(DEBUGGING, __func__, " <--");
    return conv;
}

int32_t ZinaConversation::storeConversation(SQLiteStoreConv &store)
{
    LOGGER(DEBUGGING, __func__, " -->");

    const string* data = serialize();

    int32_t result= store.storeConversation(partner_.getName(), deviceId_, localUser_, *data);
    Utilities::wipeMemory((void*)data->data(), data->size());

    delete data;
    errorCode_ = SUCCESS;
    if (SQL_FAIL(result)) {
        errorCode_ = DATABASE_ERROR;
        sqlErrorCode_ = result;
        LOGGER(ERROR, __func__, " <--, error: ");
        return result;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

// Currently not used, maybe we need to re-enable it, depending on new user UID (canonical name) design
#if 0
int32_t ZinaConversation::renameConversation(const string& localUserOld, const string& localUserNew,
                                            const string& userOld, const string& userNew, const string& deviceId)
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    if (!store->hasConversation(userOld, deviceId, localUserOld)) {
        return SQLITE_ERROR;
    }

    string* data = store->loadConversation(userOld, deviceId, localUserOld);
    if (data == NULL || data->empty()) {   // Illegal state, should not happen
        return SQLITE_ERROR;
    }

    // Create conversation object with the new names. Then deserialize() the old data
    // into the new object. This does not overwrite the new names set in the 
    // ZinaConversation object.
    ZinaConversation*  conv = new ZinaConversation(localUserNew, userNew, deviceId);
    conv->deserialize(*data);
    delete data;

    // Store the conversation with new name and the old data, only name and partner
    // are changed in the data object.
    conv->storeConversation();
    delete conv;

    // Now remove the old conversation
    int32_t sqlCode;
    store->deleteConversation(userOld, deviceId, localUserOld, &sqlCode);
    return sqlCode;
}
#endif

int32_t ZinaConversation::storeStagedMks(SQLiteStoreConv &store) {
    LOGGER(DEBUGGING, __func__, " -->");

    errorCode_ = SUCCESS;

    for (; !stagedMk.empty(); stagedMk.pop_front()) {
        string& mkIvMac = stagedMk.front();
        if (!mkIvMac.empty()) {
            int32_t result = store.insertStagedMk(partner_.getName(), deviceId_, localUser_, mkIvMac);
            if (SQL_FAIL(result)) {
                errorCode_ = DATABASE_ERROR;
                sqlErrorCode_ = result;
                LOGGER(ERROR, __func__, " <--, error: ", result);
                return result;
            }
            Utilities::wipeString(mkIvMac);
        }
    }
    clearStagedMks(stagedMk, store);
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

void ZinaConversation::clearStagedMks(list<string> &keys, SQLiteStoreConv &store)
{
    LOGGER(DEBUGGING, __func__, " -->");

    for (; !keys.empty(); keys.pop_front()) {
        string& mkIvMac = keys.front();
        // This actually clears the memory of the string inside the list
        Utilities::wipeString(mkIvMac);
    }

    // Cleanup old MKs, no harm if this DB function fails due to DB problems
    time_t timestamp = time(0) - MK_STORE_TIME;
    store.deleteStagedMk(timestamp);
    LOGGER(DEBUGGING, __func__, " <--");
}

int32_t ZinaConversation::loadStagedMks(list<string> &keys, SQLiteStoreConv &store)
{
    LOGGER(DEBUGGING, __func__, " -->");

    int32_t result = store.loadStagedMks(partner_.getName(), deviceId_, localUser_, keys);

    if (SQL_FAIL(result)) {
        return DATABASE_ERROR;
    }
    LOGGER(INFO, __func__, " Number of loaded pre-keys: ", keys.size());
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

void ZinaConversation::deleteStagedMk(string& mkiv, SQLiteStoreConv &store)
{
    LOGGER(DEBUGGING, __func__, " -->");
    store.deleteStagedMk(partner_.getName(), deviceId_, localUser_, mkiv);
    LOGGER(DEBUGGING, __func__, " <--");
}

string ZinaConversation::lookupSecondaryDevId(int32_t prekeyId)
{
    for (auto& secInfo : secondaryRatchets) {
        if (secInfo->preKeyId == prekeyId) {
            return secInfo->deviceId;
        }
    }
    return Empty;
}

void ZinaConversation::saveSecondaryAddress(const std::string& secondaryDevId, int32_t preKeyId)
{
    unique_ptr<SecondaryInfo> secInfo(new SecondaryInfo);

    secInfo->deviceId = secondaryDevId;
    secInfo->preKeyId = preKeyId;
    secInfo->creationTime = time(nullptr);

    secondaryRatchets.push_back(move(secInfo));
}

unique_ptr<ZinaConversation>
ZinaConversation::getSecondaryRatchet(int32_t index, SQLiteStoreConv &store)
{
    if (index < 0 || index >= secondaryRatchets.size()) {
        return unique_ptr<ZinaConversation>();
    }
    auto conv = loadConversation(getLocalUser(), getPartner().getName(), secondaryRatchets[index]->deviceId, store);

    if (!conv->isValid()) {
        return unique_ptr<ZinaConversation>();
    }
    return conv;
}

void ZinaConversation::deleteSecondaryRatchets(SQLiteStoreConv &store)
{
    for (auto& secInfo : secondaryRatchets) {
        store.deleteConversation(getPartner().getName(), secInfo->deviceId, getLocalUser());
    }

}
/* *****************************************************************************
 * Private functions
 ***************************************************************************** */

// No need to parse name, localName, partner name and device id. Already set
// with constructor.
void ZinaConversation::deserialize(const std::string& data)
{
    LOGGER(DEBUGGING, __func__, " -->");
    JsonUnique uniqueRoot(cJSON_Parse(data.c_str()));
    cJSON* root = uniqueRoot.get();

    cJSON* jsonItem = cJSON_GetObjectItem(root, "partner");
    string alias(cJSON_GetObjectItem(jsonItem, "alias")->valuestring);
    partner_.setAlias(alias);

    jsonItem = cJSON_GetObjectItem(root, "deviceName");
    if (jsonItem != NULL)
        deviceName_ = jsonItem->valuestring;

    char b64Buffer[MAX_KEY_BYTES_ENCODED*2] = {0};  // Twice the max. size on binary data - b64 is times 1.5
    uint8_t binBuffer[MAX_KEY_BYTES_ENCODED];       // max. size on binary data

    // Get RK b64 string, decode and store
    size_t binLength;
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "RK")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    size_t b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        RK.assign((const char*)binBuffer, binLength);
    }

    // Get the DHRs key pair
    jsonItem = cJSON_GetObjectItem(root, "DHRs");
    strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "public")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        const PublicKeyUnique pubKey = EcCurve::decodePoint(binBuffer);

        // Here we may check the public curve type and do some code to support different curves and
        // create to correct private key. The serilaized public key data contain a curve type id. For
        // the time being use Ec255 (DJB's curve 25519).
        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "private")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
        binLength = b64Decode(b64Buffer, strlen(b64Buffer), binBuffer, MAX_KEY_BYTES_ENCODED);
        const PrivateKeyUnique privKey = EcCurve::decodePrivatePoint(binBuffer, binLength);

        DHRs = KeyPairUnique(new DhKeyPair(*pubKey, *privKey));
    }

    strncpy(b64Buffer, cJSON_GetObjectItem(root, "DHRr")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        DHRr = EcCurve::decodePoint(binBuffer);
    }

    // Get the DHIs key pair
    jsonItem = cJSON_GetObjectItem(root, "DHIs");
    strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "public")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        const PublicKeyUnique pubKey = EcCurve::decodePoint(binBuffer);

        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "private")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
        binLength = b64Decode(b64Buffer, strlen(b64Buffer), binBuffer, MAX_KEY_BYTES_ENCODED);
        const PrivateKeyUnique privKey = EcCurve::decodePrivatePoint(binBuffer, binLength);

        DHIs = KeyPairUnique(new DhKeyPair(*pubKey, *privKey));
    }
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "DHIr")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    b64Length = strlen(b64Buffer);
    if (b64Length > 0) {
        b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        DHIr = EcCurve::decodePoint(binBuffer);
    }

    // Get the A0 key pair
    jsonItem = cJSON_GetObjectItem(root, "A0");
    b64Length = strlen(cJSON_GetObjectItem(jsonItem, "public")->valuestring);
    if (b64Length > 0) {
        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "public")->valuestring, b64Length+1);
        b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
        const PublicKeyUnique pubKey = EcCurve::decodePoint(binBuffer);

        strncpy(b64Buffer, cJSON_GetObjectItem(jsonItem, "private")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
        binLength = b64Decode(b64Buffer, strlen(b64Buffer), binBuffer, MAX_KEY_BYTES_ENCODED);
        const PrivateKeyUnique privKey = EcCurve::decodePrivatePoint(binBuffer, binLength);

        A0 = KeyPairUnique(new DhKeyPair(*pubKey, *privKey));
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
    }
    Ns = cJSON_GetObjectItem(root, "Ns")->valueint;
    Nr = cJSON_GetObjectItem(root, "Nr")->valueint;
    PNs = cJSON_GetObjectItem(root, "PNs")->valueint;
    preKeyId = cJSON_GetObjectItem(root, "preKyId")->valueint;
    ratchetFlag = cJSON_GetObjectItem(root, "ratchet")->valueint != 0;

    jsonItem = cJSON_GetObjectItem(root, "zrtpState");
    if (jsonItem != NULL)
        zrtpVerifyState = jsonItem->valueint;

    contextId = (Utilities::getJsonInt(root, "contextId", 0) & 0xFFFFFFFF);
    versionNumber = Utilities::getJsonInt(root, "versionNumber", 0);
    identityKeyChanged = Utilities::getJsonBool(root, "identityKeyChanged", true);
    if (zrtpVerifyState > 0) {
        identityKeyChanged = false;
    }
    if (Utilities::hasJsonKey(root, "secondaries")) {
        cJSON* secondaries = cJSON_GetObjectItem(root, "secondaries");
        int32_t numSecondaries = cJSON_GetArraySize(secondaries);

        for (int i = 0; i < numSecondaries; i++) {
            cJSON* arrayItem = cJSON_GetArrayItem(secondaries, i);
            unique_ptr<SecondaryInfo> secInfo(new SecondaryInfo);
            secInfo->preKeyId = Utilities::getJsonInt(arrayItem, "prekeyid", 0);
            secInfo->deviceId = Utilities::getJsonString(arrayItem, "deviceid", "");
            secInfo->creationTime = Utilities::getJsonInt(arrayItem, "timestamp", 0);
            secondaryRatchets.push_back(move(secInfo));
        }
    }

    LOGGER(DEBUGGING, __func__, " <--");
}

const string* ZinaConversation::serialize() const
{
    LOGGER(DEBUGGING, __func__, " -->");
    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5

    JsonUnique uniqueJson(cJSON_CreateObject());
    cJSON *root = uniqueJson.get();

    cJSON* jsonItem;
    cJSON_AddItemToObject(root, "partner", jsonItem = cJSON_CreateObject());
    cJSON_AddStringToObject(jsonItem, "name", partner_.getName().c_str());
    cJSON_AddStringToObject(jsonItem, "alias", partner_.getAlias().c_str());

    cJSON_AddStringToObject(root, "deviceId", deviceId_.c_str());
    cJSON_AddStringToObject(root, "localUser", localUser_.c_str());
    cJSON_AddStringToObject(root, "deviceName", deviceName_.c_str());

    // b64Encode terminates the B64 string with a nul byte
    b64Encode((const uint8_t*)RK.data(), RK.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(root, "RK", b64Buffer);


    // DHRs key pair, private, public
    cJSON_AddItemToObject(root, "DHRs", jsonItem = cJSON_CreateObject());
    if (DHRs) {
        b64Encode(DHRs->getPrivateKey().privateData(), DHRs->getPrivateKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "private", b64Buffer);

        b64Encode((const uint8_t*)DHRs->getPublicKey().serialize().data(), DHRs->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "public", b64Buffer);
    }
    else {
        cJSON_AddStringToObject(jsonItem, "private", "");
        cJSON_AddStringToObject(jsonItem, "public", "");
    }

    // DHRr key, public
    if (DHRr) {
        b64Encode((const uint8_t*)DHRr->serialize().data(), DHRr->getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(root, "DHRr", b64Buffer);
    }
    else
        cJSON_AddStringToObject(root, "DHRr", "");

    // DHIs key pair, private, public
    cJSON_AddItemToObject(root, "DHIs", jsonItem = cJSON_CreateObject());
    if (DHIs) {
        b64Encode(DHIs->getPrivateKey().privateData(), DHIs->getPrivateKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "private", b64Buffer);

        b64Encode((const uint8_t*)DHIs->getPublicKey().serialize().data(), DHIs->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "public", b64Buffer);
    }
    else {
        cJSON_AddStringToObject(jsonItem, "private", "");
        cJSON_AddStringToObject(jsonItem, "public", "");
    }

    // DHIr key, public
    if (DHIr) {
        b64Encode((const uint8_t*)DHIr->serialize().data(), DHIr->getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(root, "DHIr", b64Buffer);
    }
    else
        cJSON_AddStringToObject(root, "DHIr", "");


    // A0 key pair, private, public
    cJSON_AddItemToObject(root, "A0", jsonItem = cJSON_CreateObject());
    if (A0) {
        b64Encode(A0->getPrivateKey().privateData(), A0->getPrivateKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "private", b64Buffer);

        b64Encode((const uint8_t*)A0->getPublicKey().serialize().data(), A0->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "public", b64Buffer);
    }
    else {
        cJSON_AddStringToObject(jsonItem, "private", "");
        cJSON_AddStringToObject(jsonItem, "public", "");
    }

    // The two chain keys
    b64Encode((const uint8_t*)CKs.data(), CKs.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(root, "CKs", b64Buffer);

    b64Encode((const uint8_t*)CKr.data(), CKr.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(root, "CKr", b64Buffer);

    cJSON_AddNumberToObject(root, "Ns", Ns);
    cJSON_AddNumberToObject(root, "Nr", Nr);
    cJSON_AddNumberToObject(root, "PNs", PNs);
    cJSON_AddNumberToObject(root, "preKyId", preKeyId);
    cJSON_AddNumberToObject(root, "ratchet", (ratchetFlag) ? 1 : 0);
    cJSON_AddNumberToObject(root, "zrtpState", zrtpVerifyState);

    cJSON_AddNumberToObject(root, "contextId", contextId);
    cJSON_AddNumberToObject(root, "versionNumber", versionNumber);
    cJSON_AddBoolToObject(root, "identityKeyChanged", identityKeyChanged);

    if (secondaryRatchets.size()> 0) {
        // Create and add JSON array
        cJSON* secondaries = cJSON_CreateArray();
        cJSON_AddItemToObject(root, "secondaries", secondaries);

        for (auto &secInfo : secondaryRatchets) {
            cJSON* secJson = cJSON_CreateObject();
            cJSON_AddNumberToObject(secJson, "prekeyid", secInfo->preKeyId);
            cJSON_AddStringToObject(secJson, "deviceid", secInfo->deviceId.c_str());
            cJSON_AddNumberToObject(secJson, "timestamp", secInfo->creationTime);
            cJSON_AddItemToArray(secondaries, secJson);
        }
    }

    CharUnique out(cJSON_PrintUnformatted(root));
    string* data = new string(out.get());

    LOGGER(DEBUGGING, __func__, " <--");
    return data;
}

void ZinaConversation::reset()
{
    LOGGER(DEBUGGING, __func__, " -->");
    DHRs.reset();
    DHRr.reset();
    DHIs.reset();
// Keep it to detect changes of the long-term identity key    delete DHIr; DHIr = NULL;
    A0.reset();

    if (!CKr.empty())
        Utilities::wipeMemory((void*)CKr.data(), CKr.size());
    CKr.clear();

    if (!CKs.empty())
        Utilities::wipeMemory((void*)CKs.data(), CKs.size());
    CKs.clear();

    if (!RK.empty())
        Utilities::wipeMemory((void*)RK.data(), RK.size());
    RK.clear();
    Nr = Ns = PNs = preKeyId = versionNumber = 0;
    ratchetFlag = false;

    // Don't reset the context id, we use its sequence number part to count re-syncs
    LOGGER(DEBUGGING, __func__, " <--");
}


cJSON *ZinaConversation::prepareForCapture(cJSON *existingRoot, bool beforeAction) {
    LOGGER(DEBUGGING, __func__, " -->");
    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5

    cJSON* root = (existingRoot == nullptr) ? cJSON_CreateObject() : existingRoot;

    cJSON* jsonItem;
    cJSON_AddItemToObject(root, beforeAction ? "before" : "after", jsonItem = cJSON_CreateObject());

    cJSON_AddStringToObject(jsonItem, "name", partner_.getName().c_str());
    cJSON_AddStringToObject(jsonItem, "alias", partner_.getAlias().c_str());

    cJSON_AddStringToObject(jsonItem, "deviceId", deviceId_.c_str());
    cJSON_AddStringToObject(jsonItem, "localUser", localUser_.c_str());
    cJSON_AddStringToObject(jsonItem, "deviceName", deviceName_.c_str());

    if (DHRs != NULL) {
        b64Encode((const uint8_t*)DHRs->getPublicKey().serialize().data(), DHRs->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "DHRs", b64Buffer);
    }
    else
        cJSON_AddStringToObject(jsonItem, "DHRs", "");

    // DHRr key, public
    if (DHRr != NULL) {
        b64Encode((const uint8_t*)DHRr->serialize().data(), DHRr->getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "DHRr", b64Buffer);
    }
    else
        cJSON_AddStringToObject(jsonItem, "DHRr", "");

    // DHIs key, public
    if (DHIs != NULL) {
        b64Encode((const uint8_t*)DHIs->getPublicKey().serialize().data(), DHIs->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "DHIs", b64Buffer);
    }
    else
        cJSON_AddStringToObject(jsonItem, "DHIs", "");

    // DHIr key, public
    if (DHIr != NULL) {
        b64Encode((const uint8_t*)DHIr->serialize().data(), DHIr->getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "DHIr", b64Buffer);
    }
    else
        cJSON_AddStringToObject(jsonItem, "DHIr", "");


    // A0 key, public
    if (A0 != NULL) {
        b64Encode((const uint8_t*)A0->getPublicKey().serialize().data(), A0->getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(jsonItem, "A0", b64Buffer);
    }
    else
        cJSON_AddStringToObject(jsonItem, "A0", "");

    // The two chain keys, enable only if needed to do error analysis
//    b64Encode((const uint8_t*)CKs.data(), CKs.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
//    cJSON_AddStringToObject(root, "CKs", b64Buffer);
//
//    b64Encode((const uint8_t*)CKr.data(), CKr.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
//    cJSON_AddStringToObject(root, "CKr", b64Buffer);

    cJSON_AddNumberToObject(jsonItem, "Ns", Ns);
    cJSON_AddNumberToObject(jsonItem, "Nr", Nr);
    cJSON_AddNumberToObject(jsonItem, "PNs", PNs);
    cJSON_AddNumberToObject(jsonItem, "ratchet", (ratchetFlag) ? 1 : 0);
    cJSON_AddNumberToObject(jsonItem, "zrtpState", zrtpVerifyState);

    LOGGER(DEBUGGING, __func__, " <--");
    return root;
}
