#include "../utilities.h"
#include "../../util/cJSON.h"
#include "../../util/b64helper.h"
#include "scloud.h"
#include "scloudPriv.h"


static const char* kVersionStr      = "version";
static const char* kKeySuiteStr     = "keySuite";
static const char* kSymKeyStr       = "symkey";


#define _base(x) ((x >= '0' && x <= '9') ? '0' : \
(x >= 'a' && x <= 'f') ? 'a' - 10 : \
(x >= 'A' && x <= 'F') ? 'A' - 10 : \
'\255')
#define HEXOF(x) (x - _base(x))

SCLError scloudDeserializeKey(uint8_t *inData, size_t inLen, SCloudKey *keyOut)
{
    SCLError  err = kSCLError_NoErr;

    char* in = (char*)XMALLOC(inLen + 1);
    memcpy(in, inData, inLen);
    in[inLen] = '\0';

    cJSON* root = cJSON_Parse(in);
    XFREE(in);

    if (root == NULL) {
        return kSCLError_BadParams;
    }

    cJSON* cjTemp = cJSON_GetObjectItem(root, kVersionStr);
    int32_t version = (cjTemp != NULL) ? cjTemp->valueint : -1;
    if (version != kSCloudProtocolVersion) {
        cJSON_Delete(root);
        return kSCLError_BadParams;
    }

    cjTemp = cJSON_GetObjectItem(root, kKeySuiteStr);
    int32_t suite = (cjTemp != NULL) ? cjTemp->valueint : -1;
    keyOut->keySuite = (SCloudKeySuite)suite;

    cjTemp = cJSON_GetObjectItem(root, kSymKeyStr);
    char* jsString = (cjTemp != NULL) ? cjTemp->valuestring : NULL;
    if (jsString == NULL) {
        cJSON_Delete(root);
        return kSCLError_BadParams;
    }

    int32_t stringLen = strlen(jsString);
    switch (keyOut->keySuite) {
        case kSCloudKeySuite_AES128:
            if(stringLen != (16 + 16) * 2) {   // 128 bit key, 16 bytes block size, as bin2hex
                cJSON_Delete(root);
                return kSCLError_BadParams;
            }
            keyOut->blockLength = 16;
            break;

        case kSCloudKeySuite_AES256:
            if(stringLen != (32 + 16) * 2) {   // 256 bit key, 16 bytes block size, as bin2hex
                cJSON_Delete(root);
                return kSCLError_BadParams;
            }
            keyOut->blockLength = 16;
            break;

        default:
            cJSON_Delete(root);
            return kSCLError_BadParams;
            break;
    }

    uint8_t  *p;
    size_t count;
    for (count = 0, p = (uint8_t*)jsString; count < stringLen && p && *p; p += 2, count += 2) {
            keyOut->symKey[(p - (uint8_t*)jsString) >> 1] = ((HEXOF(*p)) << 4) + HEXOF(*(p+1));
    }
    cJSON_Delete(root);

    keyOut->symKeyLen = count >> 1;
    return (count == stringLen)? kSCLError_NoErr : kSCLError_BadParams;

}

SCLError SCloudEncryptGetKeyBLOB(SCloudContextRef ctx, uint8_t **outData, size_t *outSize)
{
    SCLError            err = kSCLError_NoErr;
    uint8_t             *outBuf = NULL;
    char                tempBuf[1024];
    size_t              tempLen;

    cJSON* root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, kVersionStr, kSCloudProtocolVersion);
    cJSON_AddNumberToObject(root, kKeySuiteStr, ctx->key.keySuite);

    // Convert the symmetric key and the initial IV and store it
    bin2hex(ctx->key.symKey, ctx->key.symKeyLen + ctx->key.blockLength, tempBuf, &tempLen);
    tempBuf[tempLen] = '\0';
    cJSON_AddStringToObject(root, kSymKeyStr, tempBuf);

    outBuf = (uint8_t*)cJSON_PrintUnformatted(root);
    cJSON_Delete(root);

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

    cJSON* root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, kVersionStr, kSCloudProtocolVersion);
    cJSON_AddNumberToObject(root, kKeySuiteStr, ctx->key.keySuite);

    // Convert the symmetric key and the initial IV and store it
    bin2hex(ctx->key.symKey, ctx->key.symKeyLen  + ctx->key.blockLength, tempBuf, &tempLen);
    tempBuf[tempLen] = '\0';
    cJSON_AddStringToObject(root, kSymKeyStr, tempBuf);

    cJSON_AddItemToArray(array, root);

    outBuf = (uint8_t*)cJSON_PrintUnformatted(array);
    cJSON_Delete(array);

    *outData = outBuf;
    *outSize = strlen((const char*)outBuf);

    return err;
}

