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
#include "../utilities.h"
#include "../../util/cJSON.h"
#include "../../util/b64helper.h"
#include "../../util/Utilities.h"
#include "scloud.h"
#include "scloudPriv.h"
#include "../../storage/sqlite/SQLiteStoreConv.h"


static const char* kVersionStr      = "version";
static const char* kCurrentVersionStr = "current";
static const char* kKeySuiteStr     = "keySuite";
static const char* kSymKeyStr       = "symkey";
static const char* kHashStr         = "hash";


#define _base(x) ((x >= '0' && x <= '9') ? '0' : \
(x >= 'a' && x <= 'f') ? 'a' - 10 : \
(x >= 'A' && x <= 'F') ? 'A' - 10 : \
'\255')
#define HEXOF(x) (x - _base(x))

using namespace std;
using namespace zina;

SCLError scloudDeserializeKey(uint8_t *inData, size_t inLen, SCloudKey *keyOut)
{
    char* in = (char*)XMALLOC(inLen + 1);
    memcpy(in, inData, inLen);
    in[inLen] = '\0';

    JsonUnique sharedRoot(cJSON_Parse(in));
    cJSON* root = sharedRoot.get();

    XFREE(in);

    if (root == nullptr) {
        return kSCLError_BadParams;
    }

    int32_t version = Utilities::getJsonInt(root, kCurrentVersionStr, -1);
    // If no current version then check version, backward compatibility.
    if (version == -1) {
        version = Utilities::getJsonInt(root, kVersionStr, -1);
    }
    if (version < kSCloudMinProtocolVersion) {
        return kSCLError_BadParams;
    }
    keyOut->keyVersion = version;

    int32_t suite = Utilities::getJsonInt(root, kKeySuiteStr, -1);
    keyOut->keySuite = (SCloudKeySuite)suite;

    const char *const jsString = Utilities::getJsonString(root, kSymKeyStr, nullptr);
    if (jsString == nullptr) {
        return kSCLError_BadParams;
    }

    size_t stringLen = strlen(jsString);
    switch (keyOut->keySuite) {
        case kSCloudKeySuite_AES128:
            if(stringLen != (16 + 16) * 2) {   // 128 bit key, 16 bytes block size, as bin2hex
                return kSCLError_BadParams;
            }
            keyOut->blockLength = 16;
            break;

        case kSCloudKeySuite_AES256:
            if(stringLen != (32 + 16) * 2) {   // 256 bit key, 16 bytes block size, as bin2hex
                return kSCLError_BadParams;
            }
            keyOut->blockLength = 16;
            break;

        default:
            return kSCLError_BadParams;
    }

    uint8_t  *p;
    size_t count;
    for (count = 0, p = (uint8_t*)jsString; count < stringLen && p && *p; p += 2, count += 2) {
            keyOut->symKey[(p - (uint8_t*)jsString) >> 1] = static_cast<uint8_t>(((HEXOF(*p)) << 4) + HEXOF(*(p+1)));
    }
    if (version == 3) {
        const char *const hash = Utilities::getJsonString(root, kHashStr, nullptr);
        if (hash == nullptr)
            return kSCLError_BadParams;

        size_t b64Length = strlen(hash);
        if (b64Length > 0)
            b64Decode(hash, b64Length, keyOut->hash, SKEIN256_DIGEST_LENGTH);
    }
    keyOut->symKeyLen = count >> 1;
    return (count == stringLen)? kSCLError_NoErr : kSCLError_BadParams;

}


static void createKeyJson(SCloudContextRef ctx, cJSON* root)
{
    char                tempBuf[1024];
    size_t              tempLen;

    cJSON_AddNumberToObject(root, kVersionStr, kSCloudMinProtocolVersion);
    cJSON_AddNumberToObject(root, kCurrentVersionStr, kSCloudCurrentProtocolVersion);
    cJSON_AddNumberToObject(root, kKeySuiteStr, ctx->key.keySuite);

    // Convert the symmetric key and the initial IV and store it
    bin2hex(ctx->key.symKey, ctx->key.symKeyLen + ctx->key.blockLength, tempBuf, &tempLen);
    tempBuf[tempLen] = '\0';
    cJSON_AddStringToObject(root, kSymKeyStr, tempBuf);

    b64Encode(ctx->key.hash, SKEIN256_DIGEST_LENGTH, tempBuf, SKEIN256_DIGEST_LENGTH*2);
    cJSON_AddStringToObject(root, kHashStr, tempBuf);
}

SCLError SCloudEncryptGetKeyBLOB(SCloudContextRef ctx, uint8_t **outData, size_t *outSize)
{
    SCLError            err = kSCLError_NoErr;
    uint8_t             *outBuf = NULL;

    JsonUnique jsonUnique(cJSON_CreateObject());
    createKeyJson(ctx, jsonUnique.get());

    outBuf = (uint8_t*)cJSON_PrintUnformatted(jsonUnique.get());

    *outData = outBuf;
    *outSize = strlen((const char*)outBuf);

    return err;
}

SCLError SCloudEncryptGetSegmentBLOB(SCloudContextRef ctx, int segNum, uint8_t **outData, size_t *outSize) {

    SCLError            err = kSCLError_NoErr;
    uint8_t             *outBuf = NULL;
    char                tempBuf[1024];
    size_t              tempLen;

    cJSON* array = cJSON_CreateArray();
    cJSON_AddItemToArray(array, cJSON_CreateNumber(segNum));

    URL64_encode(ctx->locator, TRUNCATED_LOCATOR_BITS >>3,  (uint8_t*)tempBuf, &tempLen);
    tempBuf[tempLen] = '\0';
    cJSON_AddItemToArray(array, cJSON_CreateString(tempBuf));

    cJSON* key = cJSON_CreateObject();
    createKeyJson(ctx, key);

    cJSON_AddItemToArray(array, key);

    outBuf = (uint8_t*)cJSON_PrintUnformatted(array);
    cJSON_Delete(array);

    *outData = outBuf;
    *outSize = strlen((const char*)outBuf);

    return err;
}

