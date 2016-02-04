#include <limits.h>

#include "../storage/sqlite/SQLiteStoreConv.h"

#include "../axolotl/crypto/Ec255PrivateKey.h"
#include "../axolotl/crypto/Ec255PublicKey.h"
#include "../axolotl/crypto/EcCurve.h"
#include "../axolotl/state/AxoConversation.h"

#include "../interfaceApp/AppInterfaceImpl.h"
#include "../util/cJSON.h"
#include "../util/b64helper.h"
#include "../axolotl/Constants.h"
#include "../keymanagment/PreKeys.h"
#include "../provisioning/ScProvisioning.h"
#include "../axolotl/crypto/DhKeyPair.h"

#include "gtest/gtest.h"
#include <iostream>
#include <string>
#include <utility>

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
static const uint8_t keyInData_1[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,32};
static const uint8_t keyInData_2[] = {"ZZZZZzzzzzYYYYYyyyyyXXXXXxxxxxW"};  // 32 bytes
static     std::string empty;

using namespace axolotl;
using namespace std;

SQLiteStoreConv* store;

static const std::string bob("bob");
static const std::string bobDevId("bobsidentifier");
static const std::string bobAuth("bobsauthorization");
static const int32_t bobRegisterId = 4711;

static const Ec255PublicKey bobIdpublicKey(keyInData);

static pair<int32_t, const DhKeyPair*> bobPreKey;

#ifdef UNITTESTS
// Used in testing and debugging to do in-depth checks
static void hexdump(const char* title, const unsigned char *s, int l) {
    int n=0;

    if (s == NULL) return;

    fprintf(stderr, "%s",title);
    for( ; n < l ; ++n)
    {
        if((n%16) == 0)
            fprintf(stderr, "\n%04x",n);
        fprintf(stderr, " %02x",s[n]);
    }
    fprintf(stderr, "\n");
}
static void hexdump(const char* title, const std::string& in)
{
    hexdump(title, (uint8_t*)in.data(), in.size());
}
#endif

// This simulates an answer from the provisioning server repsoning to register a device
// If necessary check for correctness of request data
//
static int32_t helper0(const std::string& requestUrl, const std::string& method, const std::string& data, std::string* response)
{
//     cerr << method << " " << requestUrl << '\n';
//     cerr << data;
    return 200;
}

TEST(RegisterRequest, Basic)
{
    store = SQLiteStoreConv::getStore();
    if (!store->isReady()) {
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
    }
    ScProvisioning::setHttpHelper(helper0);

    string name("wernerd");
    string devId("myDev-id");
    AppInterfaceImpl uiIf(store, name, string("myAPI-key"), devId);
    AxoConversation* ownAxoConv = AxoConversation::loadLocalConversation(name);

    if (ownAxoConv == NULL) {  // no yet available, create one. An own conversation has the same local and remote name
        ownAxoConv = new AxoConversation(name, name, empty);
        const DhKeyPair* idKeyPair = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);
        ownAxoConv->setDHIs(idKeyPair);
        ownAxoConv->storeConversation();
    }

    std::string result;
    int32_t ret = uiIf.registerAxolotlDevice(&result);

    ASSERT_TRUE(ret > 0) << "Actual return value: " << ret;
}


// This simulates an answer from the provisioning server repsoning to a get pre key request
//
static int32_t helper1(const std::string& requestUrl, const std::string& method, const std::string& reqData, std::string* response)
{
/*
{
    "version" :        <int32_t>,        # Version of JSON get pre-key, 1 for the first implementation
    "username" :       <string>,         # the user name for this account, enables mapping from optional E.164 number to name
    "scClientDevId"  : <string>,         # optional, the same string as used to register the device (v1/me/device/{device_id}/)
    "registrationId" : <int32_t>,        # the client's Axolotl registration id
    "identityKey" :    <string>,         # public part encoded base64 data
    "deviceId" :       <int32_t>,        # the TextSecure (Axolotl) device id if available, default 1
    "domain":          <string>,         # optional, domain identifier, in set then 'scClientDevId' my be missing (federation support)
    "signedPreKey" :
    {
        "keyId" :     <int32_t>,         # The key id of the signed pre key
        "key" :       <string>,          # public part encoded base64 data
        "signature" : <string>           # base64 encoded signature data"
    }
    "preKey" : 
    {
        "keyId" :     <int32_t>,         # The key id of the signed pre key
        "key" :       <string>,          # public part encoded base64 data
    }
}
*/

//    cerr << method << " " << requestUrl << '\n';

    cJSON *root;
    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5

    root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "version", 1);
    cJSON_AddStringToObject(root, "username", bob.c_str());
    cJSON_AddStringToObject(root, "scClientDevId", bobDevId.c_str());
//    cJSON_AddNumberToObject(root, "registrationId", bobRegisterId);

    std::string data = bobIdpublicKey.serialize();
    int32_t b64Len = b64Encode((const uint8_t*)data.data(), data.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(root, "identityKey", b64Buffer);
//    cJSON_AddNumberToObject(root, "deviceId", 1);

    bobPreKey = PreKeys::generatePreKey(store);

    cJSON* jsonPkr;
    cJSON_AddItemToObject(root, "preKey", jsonPkr = cJSON_CreateObject());
    cJSON_AddNumberToObject(jsonPkr, "keyId", bobPreKey.first);

    // Get pre-key's public key data, serialized and add it to JSON
    const DhKeyPair* ecPair = bobPreKey.second;
    data = ecPair->getPublicKey().serialize();
    b64Len = b64Encode((const uint8_t*)data.data(), data.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(jsonPkr, "key", b64Buffer);
    delete ecPair;

    char* out = cJSON_Print(root);
    response->append(out);
    cJSON_Delete(root); free(out);
//    cerr << *response;
    return 200;
}

TEST(PreKeyBundle, Basic)
{
    store = SQLiteStoreConv::getStore();
    if (!store->isReady()) {
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
    }
    ScProvisioning::setHttpHelper(helper1);


    pair< const axolotl::DhPublicKey*, const axolotl::DhPublicKey* > preIdKeys;
    int32_t preKeyId = Provisioning::getPreKeyBundle(bob, bobDevId, bobAuth, &preIdKeys);
    
    ASSERT_EQ(bobPreKey.first, preKeyId);
    ASSERT_TRUE(bobIdpublicKey == *(preIdKeys.first));

}

// This simulates an answer from the provisioning server repsonding number of availabe pre-keys
//
/*
 /*
 {
    "version" :        <int32_t>,        # Version of JSON new pre-keys, 1 for the first implementation
    "scClientDevId"  : <string>,         # the same string as used to register the device (v1/me/device/{device_id}/)
    "registrationId" : <int32_t>,        # the client's Axolotl registration id
    "availablePreKeys" : <int32_t>       # number of available pre-keys on the server
 }
 */

static int32_t helper2(const std::string& requestUrl, const std::string& method, const std::string& data, std::string* response)
{
//   std::cerr << method << " " << requestUrl << '\n';

    cJSON *root;

    root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "version", 1);
    cJSON_AddStringToObject(root, "scClientDevId", bobDevId.c_str());
    cJSON_AddNumberToObject(root, "registrationId", bobRegisterId);
    cJSON_AddNumberToObject(root, "availablePreKeys", 10);

    char* out = cJSON_Print(root);
    response->append(out);
    cJSON_Delete(root); free(out);

//    std::cerr << *response;
    return 200;
}

// TEST(AvailabePreKeys, Basic)
// {
//     store = SQLiteStoreConv::getStore();
//     if (!store->isReady()) {
//         store->setKey(std::string((const char*)keyInData, 32));
//         store->openStore(std::string());
//     }
//     ScProvisioning::setHttpHelper(helper2);
// 
//     ASSERT_EQ(10, Provisioning::getNumPreKeys(bobDevId, bobAuth));
// }


// This simulates an answer from the provisioning server repsonding a user's available Axolotl devices
//
/*
 {
    "version" :        <int32_t>,        # Version of JSON new pre-keys, 1 for the first implementation
    "scClientDevIds" : [<string>, ..., <string>]   # array of known Axolotl ScClientDevIds for this user/account
 }
 */
static int32_t helper3(const std::string& requestUrl, const std::string& method, const std::string& data, std::string* response)
{
//     std::cerr << method << " " << requestUrl << '\n';

    cJSON *root;

    root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "version", 1);

    cJSON* devArray;
    cJSON_AddItemToObject(root, "scClientDevIds", devArray = cJSON_CreateArray());
    cJSON_AddItemToArray(devArray, cJSON_CreateString("longDevId_1"));
    cJSON_AddItemToArray(devArray, cJSON_CreateString("longDevId_2"));
    cJSON_AddItemToArray(devArray, cJSON_CreateString("longDevId_3"));

    char* out = cJSON_Print(root);
    response->append(out);
    cJSON_Delete(root); free(out);

//     std::cerr << *response;
    return 200;
}

TEST(GetDeviceIds, Basic)
{
    store = SQLiteStoreConv::getStore();
    if (!store->isReady()) {
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
    }
    ScProvisioning::setHttpHelper(helper3);
    list<pair<string, string> >* devIds = Provisioning::getAxoDeviceIds(bob, bobAuth);

    ASSERT_TRUE(devIds != NULL);

    ASSERT_EQ(3, devIds->size());
    string id = devIds->front().first;
    devIds->pop_front();
    string id1("longDevId_1");
    ASSERT_EQ(id1, id);

    id = devIds->front().first;
    devIds->pop_front();
    std::string id2("longDevId_2");
    ASSERT_EQ(id2, id);

    id = devIds->front().first;
    devIds->pop_front();
    std::string id3("longDevId_3");
    ASSERT_EQ(id3, id);

    delete devIds;
}

// This simulates an answer from the provisioning server responding to set a new signed pre-key
// If necessary check for correctness of request data
//
static int32_t helper4(const std::string& requestUrl, const std::string& method, const std::string& data, std::string* response)
{
//     std::cerr << method << " " << requestUrl << '\n';
//     std::cerr << data;

    return 200;
}


// This simulates an answer from the provisioning server responding to add new prekeys
// If necessary check for correctness of request data
//
static int32_t helper5(const std::string& requestUrl, const std::string& method, const std::string& data, std::string* response)
{
//     std::cerr << method << " " << requestUrl << '\n';
//     std::cerr << data;

    return 200;
}

TEST(newPreKeys, Basic)
{
    store = SQLiteStoreConv::getStore();
    if (!store->isReady()) {
        store->setKey(std::string((const char*)keyInData, 32));
        store->openStore(std::string());
    }
    ScProvisioning::setHttpHelper(helper5);

    std::string result;
    int32_t ret = Provisioning::newPreKeys(store, bobDevId, bobAuth, 10, &result);
    ASSERT_TRUE(ret > 0) << "Actual return value: " << ret;
}
