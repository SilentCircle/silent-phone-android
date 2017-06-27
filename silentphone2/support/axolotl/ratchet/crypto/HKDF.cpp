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
#include <zrtp/crypto/hmac256.h>
#include <string.h>
#include "../../logging/ZinaLogging.h"


#include "HKDF.h"
using namespace zina;

void HKDF::deriveSecrets(uint8_t* inputKeyMaterial, size_t ikmLength, 
                         uint8_t* info, size_t infoLength, 
                         uint8_t* output, size_t outputLength)
{
    LOGGER(DEBUGGING, __func__, " -->");
    uint8_t emptySalt[HASH_OUTPUT_SIZE] = {0};
    deriveSecrets(inputKeyMaterial, ikmLength, emptySalt, HASH_OUTPUT_SIZE, info, infoLength, output, outputLength);
    LOGGER(DEBUGGING, __func__, " <--");
}


void HKDF::deriveSecrets(uint8_t* inputKeyMaterial, size_t ikmLength, 
                         uint8_t* salt, size_t saltLen, 
                         uint8_t* info, size_t infoLength, 
                         uint8_t* output, size_t outputLength)
{
    LOGGER(DEBUGGING, __func__, " -->");
    uint8_t prk[HASH_OUTPUT_SIZE] = {0};
    extract(salt, saltLen, inputKeyMaterial, ikmLength, prk);
    expand(prk, HASH_OUTPUT_SIZE, info, infoLength, output, outputLength);
    LOGGER(DEBUGGING, __func__, " <--");
}

// Extract function according to RFC 5869
void HKDF::extract(uint8_t* salt, size_t saltLen, uint8_t* inputKeyMaterial, size_t ikmLength, uint8_t* prkOut)
{
    uint32_t len;
    hmac_sha256(salt, static_cast<uint32_t>(saltLen), inputKeyMaterial, static_cast<int32_t>(ikmLength), prkOut, &len);
}

void* createSha256HmacContext(uint8_t* key, int32_t keyLength);
void freeSha256HmacContext(void* ctx);
void hmacSha256Ctx(void* ctx, const uint8_t* data[], uint32_t dataLength[], uint8_t* mac, int32_t* macLength );

// Expand funtion according to RFC 5869
void HKDF::expand(uint8_t* prk, size_t prkLen, uint8_t* info, size_t infoLen, uint8_t* output, size_t L)
{
    LOGGER(DEBUGGING, __func__, " -->");
    size_t iterations = (L + (HASH_OUTPUT_SIZE-1)) / HASH_OUTPUT_SIZE;

    void* hmacCtx;
    uint8_t *T;

    const uint8_t* data[4];      // 3 data pointers for HMAC data plus terminating NULL
    uint32_t dataLen[4];
    size_t dataIdx = 0;

    uint8_t counter;
    int32_t macLength;

    hmacCtx = createSha256HmacContext(prk,  static_cast<int32_t>(prkLen));

    // T points to buffer that holds concatenated T(1) || T(2) || ... T(N))
    T = new uint8_t[(iterations * HASH_OUTPUT_SIZE)];

    // Prepare first HMAC. T(0) has zero length, thus we ignore it in first run.
    // After first run use its output (T(1)) as first data in next HMAC run.
    for (size_t i = OFFSET; i <= iterations; i++) {
        if (infoLen > 0 && info != NULL) {
            data[dataIdx] = info;
            dataLen[dataIdx++] = static_cast<uint32_t>(infoLen);
        }
        counter = static_cast<uint8_t>(i & 0xff);
        data[dataIdx] = &counter;
        dataLen[dataIdx++] = 1;

        data[dataIdx] = NULL;
        dataLen[dataIdx] = 0;

        hmacSha256Ctx(hmacCtx, data, dataLen, T + ((i-1) * HASH_OUTPUT_SIZE), &macLength);

        // Use output of previous hash run as first input of next hash run
        dataIdx = 0;
        data[dataIdx] = T + ((i-1) * HASH_OUTPUT_SIZE);
        dataLen[dataIdx++] = HASH_OUTPUT_SIZE;
    }
    freeSha256HmacContext(hmacCtx);
    memcpy(output, T, L);
    delete[] T;
    LOGGER(DEBUGGING, __func__, " <--");
}
