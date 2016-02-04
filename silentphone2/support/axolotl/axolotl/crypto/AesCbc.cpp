#include "AesCbc.h"

#include <cryptcommon/aescpp.h>
#include "AesCbc.h"
#include "../Constants.h"

#include <iostream>

using namespace axolotl;
using namespace std;

int32_t axolotl::aesCbcEncrypt(const std::string& key, const std::string& IV, const std::string& plainText, std::string* cryptText)
{
    if (IV.size() != AES_BLOCK_SIZE)
        return WRONG_BLK_SIZE;

    int32_t padlen = (AES_BLOCK_SIZE - plainText.size() % AES_BLOCK_SIZE);
//    data.append(padlen, padlen);

    uint8_t* outBuffer = new uint8_t[plainText.size() + padlen];
    memcpy(outBuffer, plainText.data(), plainText.size());
    memset(outBuffer + plainText.size(), padlen&0xff, padlen);  // pad to full blocksize

    uint8_t ivTemp[AES_BLOCK_SIZE];                             // copy IV, AES code modifies IV buffer
    memcpy(ivTemp, IV.data(), AES_BLOCK_SIZE);

    AESencrypt aes;
    if (key.size() == 16)
        aes.key128((const uint8_t*)key.data());
    else if (key.size() == 32)
        aes.key256((const uint8_t*)key.data());
    else
        return UNSUPPORTED_KEY_SIZE;

    // Encrypt in place
    aes.cbc_encrypt(outBuffer, outBuffer, plainText.size() + padlen, ivTemp);
    cryptText->assign((const char*)outBuffer, plainText.size() + padlen);

    delete[] outBuffer;
    return SUCCESS;
}


int32_t axolotl::aesCbcDecrypt(const std::string& key, const std::string& IV, const std::string& cryptText, std::string* plainText)
{
    if (IV.size() != AES_BLOCK_SIZE)
        return WRONG_BLK_SIZE;

    uint8_t* outBuffer = new uint8_t[cryptText.size()];
    memcpy(outBuffer, cryptText.data(), cryptText.size());

    uint8_t ivTemp[AES_BLOCK_SIZE];                             // copy IV, AES code modifies IV buffer
    memcpy(ivTemp, IV.data(), AES_BLOCK_SIZE);

    AESdecrypt aes;
    if (key.size() == 16)
        aes.key128((const uint8_t*)key.data());
    else if (key.size() == 32)
        aes.key256((const uint8_t*)key.data());
    else
        return UNSUPPORTED_KEY_SIZE;

    aes.cbc_decrypt(outBuffer, outBuffer, cryptText.size(), ivTemp);
    plainText->assign((const char*)outBuffer, cryptText.size());

    delete[] outBuffer;
    return SUCCESS;
}

bool axolotl::checkAndRemovePadding(std::string& data)
{
    int32_t length = data.size();
    int32_t padCount = data[length-1] & 0xff;

   if (padCount == 0 || padCount > AES_BLOCK_SIZE || padCount > length)
        return false;

    for (int32_t i = 1; i <= padCount; i++)  {
        if (data[length - i] != padCount)
            return false;
    }
    data.erase(length - padCount);
    return true;
}

