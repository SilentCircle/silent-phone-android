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

#include <cstdio>
#include "ScProvisioning.h"

#include "../util/b64helper.h"
#include "../Constants.h"
#include "../ratchet/crypto/EcCurve.h"
#include "../keymanagment/PreKeys.h"
#include "../util/Utilities.h"

using namespace zina;
using namespace std;

int32_t (*ScProvisioning::httpHelper_)(const std::string&, const std::string&, const std::string&, std::string*) = NULL;

void ScProvisioning::setHttpHelper(int32_t (*httpHelper)( const std::string&, const std::string&, const std::string&, std::string* ))
{
    httpHelper_ = httpHelper;
}

// Implementation of the Provisioning API: Register a device, re-used to set 
// new signed pre-key and to add pre-keys.
// /v1/me/device/<device_id>/axolotl/keys/?api_key=<API_key>
// Method: PUT

static const char* registerRequest = "/v1/me/device/%s/axolotl/keys/?api_key=%s";

int32_t Provisioning::registerZinaDevice(const std::string& request, const std::string& authorization, const std::string& scClientDevId, std::string* result)
{
    LOGGER(DEBUGGING, __func__, " -->");
    char temp[1000];
    snprintf(temp, 990, registerRequest, scClientDevId.c_str(), authorization.c_str());

    std::string requestUri(temp);
    LOGGER(DEBUGGING, __func__, " <--");

    return ScProvisioning::httpHelper_(requestUri, PUT, request, result);
}

// Implementation of the Provisioning API: remove an Axolotl Device 
// /v1/me/device/<device_id>/axolotl/keys/?api_key=<API_key>
// Method: DELETE

int32_t Provisioning::removeZinaDevice(const string& scClientDevId, const string& authorization, std::string* result)
{
    LOGGER(DEBUGGING, __func__, " -->");
    char temp[1000];
    snprintf(temp, 990, registerRequest, scClientDevId.c_str(), authorization.c_str());
    std::string requestUri(temp);
    LOGGER(DEBUGGING, __func__, " <--");

    return ScProvisioning::httpHelper_(requestUri, DELETE, Empty, result);

}

// Implementation of the Provisioning API: Get Pre-Key
// Request URL: /v1/user/<user>/device/<devid>/?api_key=<apikey>
// Method: GET
/*
 * Server response:
{
  "axolotl": {
     "prekey": {
         "id": 740820098, 
         "key": "AbInUu24ot/07lc4q432zrwd+xbZA8oS1+OB/8j1CKU3"
     },
     "identity_key": "AR2/g2VTSYpqbnRJVi4Wdz8hAnZZmHvknf15qRrClZcs"
  }
}
*/
static const char* getPreKeyRequest = "/v1/user/%s/device/%s/?api_key=%s";

int32_t Provisioning::getPreKeyBundle(const string& name, const string& longDevId, const string& authorization,
                                      pair<PublicKeyUnique, PublicKeyUnique>* preIdKeys)
{
    LOGGER(DEBUGGING, __func__, " -->");

    string encoded = Utilities::urlEncode(name);

    char temp[1000];
    snprintf(temp, 990, getPreKeyRequest, encoded.c_str(), longDevId.c_str(), authorization.c_str());
    std::string requestUri(temp);

    std::string response;
    int32_t code = ScProvisioning::httpHelper_(requestUri, GET, Empty, &response);

    if (code >= 400)
        return 0;

    uint8_t pubKeyBuffer[MAX_KEY_BYTES_ENCODED];

    JsonUnique uniqueJson(cJSON_Parse(response.c_str()));
    cJSON* root = uniqueJson.get();

    if (root == NULL) {
        LOGGER(ERROR, "Wrong pre-key bundle JSON data, ignoring.");
        return 0;
    }

    cJSON* cjKey = cJSON_GetObjectItem(root, "axolotl");
    if (cjKey == NULL) {
        LOGGER(ERROR, "Not a valid pre-key bundle, ignoring.");
        return 0;
    }

    cJSON* cjTemp = cJSON_GetObjectItem(cjKey, "identity_key");
    char* jsString = (cjTemp != NULL) ? cjTemp->valuestring : NULL;
    if (jsString == NULL) {
        LOGGER(ERROR, "Missing identity key in pre-key bundle, ignoring.");
        return 0;
    }
    string identity(jsString);

    cJSON* pky = cJSON_GetObjectItem(cjKey, "preKey");
    int32_t pkyId = cJSON_GetObjectItem(pky, "id")->valueint;
    string pkyPub(cJSON_GetObjectItem(pky, "key")->valuestring);

    b64Decode(pkyPub.data(), pkyPub.size(), pubKeyBuffer, MAX_KEY_BYTES_ENCODED);
    PublicKeyUnique prePublic = EcCurve::decodePoint(pubKeyBuffer);

    b64Decode(identity.data(), identity.size(), pubKeyBuffer, MAX_KEY_BYTES_ENCODED);
    PublicKeyUnique identityKey = EcCurve::decodePoint(pubKeyBuffer);

    preIdKeys->first = move(identityKey);
    preIdKeys->second = move(prePublic);

    LOGGER(DEBUGGING, __func__, " <--");
    return pkyId;
}

// Implementation of the Provisioning API: Available pre-keys
// Request URL: /v1/me/device/<device_id>/"
// Method: GET
/*
 * Server response:
 {
  "silent_text": {
      "username": "xxx@xmpp-dev.silentcircle.net", 
      "password": "badcafe"
     },
  "silent_phone": {
      "username": "xxx", 
      "tns": {
          "+15555555555": {
              "oca_region": "US", "oca_area": "New York", "provider": "Test"
          }
       }, 
       "current_modifier": 0, 
       "services": {
           "global": {
               "minutes_left": 100, "min_tier": 100
           }
       },
       "numbers": [{"region": "US", "number": "+15555555555", "area": "New York"}], 
       "owner": "sc", 
       "password": "topsecret", 
       "tls1": "server1.silentcircle.net", "tls2": "server2.silentcircle.net"
   }, 
   "axolotl": {
       "version": 1, 
       "prekeys": [
           {"id": 4711, "key": "badcafebeafdead"}, 
           {"id": 815, "key":  "cafecafebadbad"}, 
        ], 
        "identity_key": "deadbeaf"
   }, 
   "push_tokens": []}
 */
static const char* getNumberPreKeys = "/v1/me/device/%s/?api_key=%s";

int32_t Provisioning::getNumPreKeys(const string& longDevId,  const string& authorization)
{
    LOGGER(DEBUGGING, __func__, " -->");

    char temp[1000];
    snprintf(temp, 990, getNumberPreKeys, longDevId.c_str(), authorization.c_str());

    std::string response;
    int32_t code = ScProvisioning::httpHelper_(temp, GET, Empty, &response);

    if (code >= 400 || response.empty())
        return -1;

    JsonUnique uniqueJson(cJSON_Parse(response.c_str()));
    cJSON* root = uniqueJson.get();
    if (root == NULL) {
        LOGGER(ERROR, "Wrong pre-key bundle JSON data, ignoring.");
        return -1;
    }

    cJSON* axolotl = cJSON_GetObjectItem(root, "axolotl");
    if (axolotl == NULL) {
        LOGGER(ERROR, "Not a valid pre-key bundle, ignoring.");
        return -1;
    }

    cJSON* keyIds = cJSON_GetObjectItem(axolotl, "prekeys");
    if (keyIds == NULL || keyIds->type != cJSON_Array) {
        LOGGER(ERROR, "No pre-keys array, ignoring.");
        return -1;
    }
    int32_t numIds = cJSON_GetArraySize(keyIds);

    LOGGER(DEBUGGING, __func__, " <--");
    return numIds;
}


// Implementation of the Provisioning API: Get Available Axolotl registered devices of a user
// Request URL: /v1/user/wernerd/devices/?filter=axolotl&api_key=<apikey>
// Method: GET
/*
 {
    "version" :        <int32_t>,        # Version of JSON new pre-keys, 1 for the first implementation
    {"devices": [{"version": 1, "id": <string>, "device_name": <string>}]}  # array of known Axolotl ScClientDevIds for this user/account
 }
 */
static const char* getUserDevicesRequest = "/v1/user/%s/device/?filter=axolotl&api_key=%s";

shared_ptr<list<pair<string, string> > > Provisioning::getZinaDeviceIds(const std::string& name, const std::string& authorization, int32_t* errorCode)
{
    LOGGER(DEBUGGING, __func__, " -->");

    shared_ptr<list<pair<string, string> > > deviceIds = make_shared<list<pair<string, string> > >();
    int32_t code = getZinaDeviceIds(name, authorization, *deviceIds);

    if (errorCode != NULL)
        *errorCode = code;
    return deviceIds;
}

int32_t Provisioning::getZinaDeviceIds(const std::string& name, const std::string& authorization, list<pair<string, string> > &deviceIds)
{
    LOGGER(DEBUGGING, __func__, " -->");

    if (ScProvisioning::httpHelper_ == nullptr) {
        LOGGER(ERROR, __func__,  "ZINA library not correctly initialized");
        return 500;
    }

    string encoded = Utilities::urlEncode(name);

    char temp[1000];
    snprintf(temp, 990, getUserDevicesRequest, encoded.c_str(), authorization.c_str());

    std::string requestUri(temp);

    std::string response;
    int32_t code = ScProvisioning::httpHelper_(requestUri, GET, Empty, &response);

    if (code >= 400) {
        return NETWORK_ERROR;
    }
    if (response.empty()) {
        return NO_DEVS_FOUND;
    }
    JsonUnique uniqueJson(cJSON_Parse(response.c_str()));
    cJSON* root = uniqueJson.get();
    if (root == NULL) {
        LOGGER(ERROR, __func__,  "Wrong device response JSON data, ignoring: ", response);
        return NETWORK_ERROR;
    }

    cJSON* devIds = cJSON_GetObjectItem(root, "devices");
    if (devIds == NULL || devIds->type != cJSON_Array) {
        LOGGER(ERROR, __func__,  "No devices array in response, ignoring.");
        return NO_DEVS_FOUND;
    }
    int32_t numIds = cJSON_GetArraySize(devIds);
    for (int32_t i = 0; i < numIds; i++) {
        cJSON* arrayItem = cJSON_GetArrayItem(devIds, i);
        cJSON* devId = cJSON_GetObjectItem(arrayItem, "id");
        if (devId == NULL) {
            LOGGER(ERROR, __func__,  "Missing device id, ignoring.");
            continue;
        }
        string id(devId->valuestring);
        string nameString;
        cJSON* devName = cJSON_GetObjectItem(arrayItem, "device_name");
        if (devName != NULL)
            nameString.assign(devName->valuestring);
        pair<string, string> idName(id, nameString);
        deviceIds.push_back(idName);
    }

    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}


// Implementation of the Provisioning API: Set new pre-keys
// /v1/me/device/<device_id>/axolotl/keys/?api_key=<API_key>
// Method: PUT
/*
 {
    "prekeys" : [{
        "id" :        <int32_t>,         # The key id of the signed pre key
        "key" :       <string>,          # public part encoded base64 data
    },
....
    {
        "id" :        <int32_t>,         # The key id of the signed pre key
        "key" :       <string>,          # public part encoded base64 data
    }]
 }
*/
int32_t Provisioning::newPreKeys(SQLiteStoreConv* store, const string& longDevId, const string& authorization, int32_t number, string* result )
{
    LOGGER(DEBUGGING, __func__, " -->");

    char temp[1000];
    snprintf(temp, 990, registerRequest, longDevId.c_str(), authorization.c_str());
    std::string requestUri(temp);

    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5

    JsonUnique uniqueJson(cJSON_CreateObject());
    cJSON* root = uniqueJson.get();

    cJSON* jsonPkrArray;
    cJSON_AddItemToObject(root, "prekeys", jsonPkrArray = cJSON_CreateArray());

    auto* preList = PreKeys::generatePreKeys(store, number);
    for (; !preList->empty(); preList->pop_front()) {
        auto& prePair = preList->front();

        cJSON* pkrObject;
        cJSON_AddItemToArray(jsonPkrArray, pkrObject = cJSON_CreateObject());
        cJSON_AddNumberToObject(pkrObject, "id", prePair.keyId);

        // Get pre-key's public key data, serialized
        const std::string data = prePair.keyPair->getPublicKey().serialize();

        b64Encode((const uint8_t*)data.data(), data.size(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
        cJSON_AddStringToObject(pkrObject, "key", b64Buffer);
    }
    delete preList;

    CharUnique out(cJSON_PrintUnformatted(root));
    std::string registerRequest(out.get());

    LOGGER(DEBUGGING, __func__, " <--");

    return ScProvisioning::httpHelper_(requestUri, PUT, registerRequest, result);
}

// Implementation of the Provisioning API: Get available user info from provisioning server
// Request URL: /v1/user/<name>/?api_key=<apikey>
// Method: GET
static const char* getUserInfoRequest = "/v1/user/%s/?api_key=%s";

int32_t Provisioning::getUserInfo(const string& alias,  const string& authorization, string* result)
{
    LOGGER(DEBUGGING, __func__, " -->");

    string encoded = Utilities::urlEncode(alias);
    char temp[1000];
    snprintf(temp, 990, getUserInfoRequest, encoded.c_str(), authorization.c_str());
    string requestUri(temp);

    int32_t code = ScProvisioning::httpHelper_(requestUri, GET, Empty, result);

    LOGGER(DEBUGGING, __func__, " <--");
    return code;
}

