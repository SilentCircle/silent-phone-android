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

#ifndef Included_scloudpriv_h /* [ */
#define Included_scloudpriv_h


#include "scloud.h"
#include <cryptcommon/aes.h>
#include <zrtp/crypto/skein256.h>

#ifdef __clang__
#pragma mark
#pragma mark SCloud Private Defines
#endif

#define DEBUG_PROTOCOL 0
#define DEBUG_PACKETS 0
 

#define kSCloudMinProtocolVersion       2
#define kSCloudCurrentProtocolVersion   3


#define validateSCloudContext( s )      \
ValidateParam( scloudContextIsValid( s ) )



enum SCloudEncryptState_
{
    kSCloudState_Init        = 0,
    kSCloudState_Hashed,
    kSCloudState_Header,
    kSCloudState_Meta,
    kSCloudState_Data,
    kSCloudState_Pad,
    kSCloudState_Done,

    ENUM_FORCE( SCloudEncryptState_ )
};

ENUM_TYPEDEF( SCloudEncryptState_, SCloudEncryptState   );


enum SCloudKeySuite_
{
    kSCloudKeySuite_AES128           = 0,
    kSCloudKeySuite_AES256           = 1,
    kSCloudKeySuite_2FISH128         = 2,
    kSCloudKeySuite_2FISH256         = 3,
    ENUM_FORCE( SCloudKeySuite_ )
};

ENUM_TYPEDEF( SCloudKeySuite_, SCloudKeySuite);

typedef struct SCloudKey    SCloudKey;
struct SCloudKey
{
    SCloudKeySuite keySuite;
    int32_t        keyVersion;
    size_t         symKeyLen;
    int32_t        blockLength;
    uint8_t        symKey[128];
    uint8_t        hash[SKEIN256_DIGEST_LENGTH];
};


#define kSCloudContextMagic     0x53436C64
typedef struct SCloudContext        SCloudContext;
 
#define SCLOUD_TEMPBUF_LEN  128
struct SCloudContext 
{
    uint32_t                magic;
    SCloudEncryptState      state;
    aes_encrypt_ctx         aes_enc;
    aes_decrypt_ctx         aes_dec;
    uint8_t                 iv[AES_BLOCK_SIZE];
    
    bool                    bEncrypting;
    bool                    bJustDecryptMetaData;
    
    SCloudKey               key;
    uint8_t                 locator[SCLOUD_LOCATOR_LEN];

    uint8_t                 *contextStr;
    size_t                  contextStrLen;

    uint8_t                 *dataBuffer;
    uint8_t                 *dataDecryptBuffer;
    size_t                  dataLen;
    size_t                  dataDecryptLen;
    uint8_t                 *metaBuffer;
    size_t                  metaLen;
    size_t                  metaDecryptLen;
    size_t                  metaBufferOffset;

    /* for encrypting */
    uint8_t                 *buffPtr;
    size_t                  byteCount;

    /* for decrypting */
    uint8_t                 tmpBuf[SCLOUD_TEMPBUF_LEN];    /* for decrypting */
    size_t                  tmpCnt;
    size_t                  padLen;

    SCloudEventHandler      handler;        /* event callback handler */
    void*                   userValue;
};

SCLError scloudDeserializeKey( uint8_t *inData, size_t inLen, SCloudKey *keyOut);

#endif       /* ] */

