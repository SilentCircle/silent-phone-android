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
#include "AesCbc.h"

#include <cryptcommon/aescpp.h>
#include "../../Constants.h"
#include "../../logging/ZinaLogging.h"

using namespace std;
using namespace zina;

int32_t zina::aesCbcEncrypt(const string& key, const string& IV, const string& plainText, string* cryptText)
{
    LOGGER(DEBUGGING, __func__, " -->");
    if (IV.size() != AES_BLOCK_SIZE) {
        LOGGER(ERROR, __func__, " <-- IV wrong block size.");
        return WRONG_BLK_SIZE;
    }

    size_t padlen = (AES_BLOCK_SIZE - plainText.size() % AES_BLOCK_SIZE);
//    data.append(padlen, padlen);

    uint8_t* outBuffer = new uint8_t[plainText.size() + padlen];
    memcpy(outBuffer, plainText.data(), plainText.size());
    memset(outBuffer + plainText.size(), static_cast<int>(padlen&0xff), padlen);  // pad to full blocksize

    uint8_t ivTemp[AES_BLOCK_SIZE];                             // copy IV, AES code modifies IV buffer
    memcpy(ivTemp, IV.data(), AES_BLOCK_SIZE);

    AESencrypt aes;
    if (key.size() == 16)
        aes.key128((const uint8_t*)key.data());
    else if (key.size() == 32)
        aes.key256((const uint8_t*)key.data());
    else {
        LOGGER(ERROR, __func__, " <-- Unsupported key size: ", key.size());
        delete[] outBuffer;
        return UNSUPPORTED_KEY_SIZE;
    }

    // Encrypt in place
    aes.cbc_encrypt(outBuffer, outBuffer, static_cast<int>(plainText.size() + padlen), ivTemp);
    cryptText->assign((const char*)outBuffer, plainText.size() + padlen);

    delete[] outBuffer;
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}


int32_t zina::aesCbcDecrypt(const string& key, const string& IV, const string& cryptText, string* plainText)
{
    LOGGER(DEBUGGING, __func__, " -->");
    if (IV.size() != AES_BLOCK_SIZE) {
        LOGGER(ERROR, __func__, " <-- IV wrong block size.");
        return WRONG_BLK_SIZE;
    }

    uint8_t* outBuffer = new uint8_t[cryptText.size()];
    memcpy(outBuffer, cryptText.data(), cryptText.size());

    uint8_t ivTemp[AES_BLOCK_SIZE];                             // copy IV, AES code modifies IV buffer
    memcpy(ivTemp, IV.data(), AES_BLOCK_SIZE);

    AESdecrypt aes;
    if (key.size() == 16)
        aes.key128((const uint8_t*)key.data());
    else if (key.size() == 32)
        aes.key256((const uint8_t*)key.data());
    else {
        LOGGER(ERROR, __func__, " <-- Unsupported key size: ", key.size());
        delete[] outBuffer;
        return UNSUPPORTED_KEY_SIZE;
    }

    aes.cbc_decrypt(outBuffer, outBuffer, static_cast<int>(cryptText.size()), ivTemp);
    plainText->assign((const char*)outBuffer, cryptText.size());

    delete[] outBuffer;
    LOGGER(DEBUGGING, __func__, " <--");
    return SUCCESS;
}

bool zina::checkAndRemovePadding(string* data)
{
    LOGGER(DEBUGGING, __func__, " -->");
    size_t length = data->size();
    size_t padCount = (*data)[length-1] & 0xffU;

   if (padCount == 0 || padCount > AES_BLOCK_SIZE || padCount > length) {
       LOGGER(ERROR, __func__, " <-- Wrong pad count: ", padCount);
       return false;
   }

    for (int32_t i = 1; i <= padCount; i++)  {
        if ((*data)[length - i] != padCount) {
            LOGGER(ERROR, __func__, " <-- Wrong pad data: ", (*data)[length - i]);
            return false;
        }
    }
    data->erase(length - padCount);
    LOGGER(DEBUGGING, __func__, " <--");
    return true;
}

