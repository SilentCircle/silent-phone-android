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

#define DEBUG 1

#include "scloud.h"
#include "scloudPriv.h"
#include "../utilities.h"
#include "../../ratchet/crypto/HKDF.h"
#include "../../logging/ZinaLogging.h"

#ifndef roundup
#define roundup(x, y)   ((((x) % (y)) == 0) ? \
(x) : ((x) + ((y) - ((x) % (y)))))
#endif
 
#ifndef MIN
#define MIN(x, y) ( ((x)<(y))?(x):(y) )
#endif


#define SCLOUD_HEADER_PAD   20


// the SCLOUD_HEADER_SIZE needs to be a multiple of 16
#define SCLOUD_HEADER_SIZE  (sizeof(uint32_t) + sizeof(uint32_t) +  sizeof(uint32_t) + SCLOUD_HEADER_PAD)


/*____________________________________________________________________________
 validity test  
 ____________________________________________________________________________*/

static bool scloudContextIsValid(const SCloudContext * ref)
{
    return IsntNull(ref) && ref->magic == kSCloudContextMagic;
}

/*____________________________________________________________________________
 Public Functions
 ____________________________________________________________________________*/



/**
 <A short one line description>
 
 <Longer description>
 <May span multiple lines or paragraphs as needed>
 
 @param  Description of method's or function's input parameter
 @param  ...
 @return Description of the return value
 */

SCLError SCloudEncryptNew(void *contextStr,     size_t contextStrLen,
                          void *data,           size_t dataLen,
                          void *metaData,       size_t metaDataLen,
                          SCloudEventHandler    handler,
                          void*                 userValue,
                          SCloudContextRef      *cloudRefOut)
{
    SCLError               err          = kSCLError_NoErr;
    SCloudContext*         ctx          = kInvalidSCloudContextRef;

    ValidateParam(cloudRefOut);

    ctx = (SCloudContext*)XMALLOC(sizeof (SCloudContext)); CKNULL(ctx);

    ZERO(ctx, sizeof(SCloudContext));
    ctx->magic          = kSCloudContextMagic;
    ctx->state          = kSCloudState_Init;
    ctx->bEncrypting    = true;

    ctx->contextStr     = (uint8_t*)contextStr;
    ctx->contextStrLen  = contextStrLen;
    ctx->dataBuffer     = (uint8_t*)data;
    ctx->dataLen        = dataLen;
    ctx->metaBuffer     = (uint8_t*)metaData;
    ctx->metaLen        = metaDataLen;

    ctx->handler        = handler;
    ctx->userValue      = userValue;

    ctx->key.keySuite   = kSCloudKeySuite_AES128;

    *cloudRefOut = ctx; 

done:
     return err;
}

size_t  SCloudEncryptBufferSize(SCloudContextRef scloudRef)
{
    size_t required = SCLOUD_HEADER_SIZE + scloudRef->dataLen + scloudRef->metaLen;
    size_t pad = required % scloudRef->key.blockLength;

    required += (pad == 0) ? scloudRef->key.blockLength : scloudRef->key.blockLength - pad;
    return required;
}

static const char* scCloudKeyLabel = "ScloudDerivedKeyIvLocator";
SCLError SCloudCalculateKey(SCloudContextRef ctx, size_t blocksize)
{
    SCLError err = kSCLError_NoErr;
    uint8_t                 hash[SKEIN256_DIGEST_LENGTH];
    uint8_t                 derivedData[128];
    size_t                  symKeyLen = 0;
    int32_t                 blockLen = 0;

    size_t                  totalBytes = ctx->metaLen + ctx->dataLen;
    size_t                  bytesProcessed = 0;

    uint8_t                 *p;
    size_t                  bytes_left;

    uint8_t* data[3];      // 3 data pointers for HMAC data plus terminating NULL
    uint32_t dataLen[3];
    int32_t  dataIdx = 0;

    validateSCloudContext(ctx);

    if(ctx->metaBuffer && ctx->metaLen > 0) {
        data[dataIdx] = ctx->metaBuffer;  dataLen[dataIdx++] = ctx->metaLen;
    }
    bytesProcessed += ctx->metaLen;

    data[dataIdx] = ctx->dataBuffer; dataLen[dataIdx++] = ctx->dataLen;
    bytesProcessed += ctx->dataLen;

    data[dataIdx] = NULL; dataLen[dataIdx] = 0;

    skein256(data, dataLen, hash);

    if (ctx->key.keySuite == kSCloudKeySuite_AES128) {
        symKeyLen = 16;
        blockLen = AES_BLOCK_SIZE;
    }
    ctx->key.symKeyLen = symKeyLen;
    ctx->key.blockLength = blockLen;

    int32_t numBytes = symKeyLen + blockLen + SCLOUD_LOCATOR_LEN;          // blockLen is the IV we need

    if(ctx->contextStr == NULL || ctx->contextStrLen == 0) {
        RETERR(kSCLError_ImproperInitialization);
    }

    // Use HDKF with 2 input parameters: ikm, info
    zina::HKDF::deriveSecrets(hash, SKEIN256_DIGEST_LENGTH,             // hash as input key material to HASH KDF
                                 ctx->contextStr, ctx->contextStrLen, // secret salt to avoid attacks on convergent encryption
                                 (uint8_t*)scCloudKeyLabel, strlen(scCloudKeyLabel), // fixed string "ScloudDerivedKeyIvLocator" as info
                                 derivedData, numBytes);

    // Store the derive key _and_ the derive IV in the key structure
    COPY(derivedData, ctx->key.symKey, symKeyLen+blockLen);

    // make a copy of the IV which gets modified during encryption
    COPY((ctx->key.symKey+symKeyLen), ctx->iv, blockLen);

    // Copy the locator
    COPY((derivedData+symKeyLen+blockLen), ctx->locator, SCLOUD_LOCATOR_LEN);

    if (ctx->key.keySuite == kSCloudKeySuite_AES128) {
        aes_encrypt_key128(ctx->key.symKey, &ctx->aes_enc);
    }
    ctx->state = kSCloudState_Hashed;

done:
    ZERO(hash, sizeof(hash));
    return err;
}

void SCloudFree(SCloudContextRef ctx, int freeBuffers) 
{
    if(scloudContextIsValid(ctx)) {
        if (freeBuffers) {
            if (ctx->contextStr != NULL) {
                ZERO(ctx->contextStr, ctx->contextStrLen);
                XFREE(ctx->contextStr);
            }
            if (ctx->metaBuffer != NULL) {
                ZERO(ctx->metaBuffer, ctx->metaLen);
                XFREE(ctx->metaBuffer);
            }
            if (ctx->dataBuffer != NULL) {
                ZERO(ctx->dataBuffer, ctx->dataLen);
                XFREE(ctx->dataBuffer);
            }
        }
        ZERO(ctx, sizeof(SCloudContext));
        XFREE(ctx);
    }
}


SCLError SCloudEncryptGetLocator(SCloudContextRef ctx, uint8_t * buffer, size_t *bufferSize)
{
    SCLError err = kSCLError_NoErr;

    validateSCloudContext(ctx);
    ValidateParam(buffer);
    ValidateParam(bufferSize);
    ValidateParam(*bufferSize  >= (TRUNCATED_LOCATOR_BITS >>3))

    if (ctx->state == kSCloudState_Init)
        RETERR(kSCLError_ImproperInitialization);

    if (*bufferSize < sizeof(ctx->locator))
        RETERR(kSCLError_BadParams);

    COPY(ctx->locator, buffer, TRUNCATED_LOCATOR_BITS >>3);
    *bufferSize = TRUNCATED_LOCATOR_BITS >>3;

done:
    return err;
}


SCLError SCloudEncryptGetLocatorREST(SCloudContextRef ctx, uint8_t* buffer, size_t* bufferSize)
{
    SCLError err = kSCLError_NoErr;
    size_t      outlen = 0;

    validateSCloudContext(ctx);
    ValidateParam(buffer);
    ValidateParam(bufferSize);

    outlen = URL64_encodeLength(TRUNCATED_LOCATOR_BITS >>3);

    ValidateParam(*bufferSize  >= outlen)

    if (ctx->state == kSCloudState_Init)
        RETERR(kSCLError_ImproperInitialization);

    err = URL64_encode(ctx->locator, TRUNCATED_LOCATOR_BITS >>3, buffer, &outlen);

    *bufferSize = outlen;

done:
    return err;
}

static void computeHash(SCloudContextRef ctx, uint8_t *buffer, size_t bufferSize)
{
    LOGGER(INFO, __func__, " -->");
    skein256(buffer, static_cast<unsigned int>(bufferSize), ctx->key.hash);
    LOGGER(INFO, __func__, " <--");
}

static SCLError sCloudEncryptNextInternal (SCloudContextRef ctx, uint8_t *buffer, size_t *bufferSize) 
{
    SCLError err = kSCLError_NoErr;
    int32_t blockLen = ctx->key.blockLength;

    uint8_t *p         = buffer;
    size_t  bufferLeft = *bufferSize;
    size_t  bytes2Copy = 0;
    size_t  bytesUsed  = 0;
    size_t  bytes2Pad  = 0; 
 
    do {
        switch(ctx->state)
        {
            case kSCloudState_Hashed:

                sStore32(ctx->magic, &p);
                sStore32((uint32_t)ctx->metaLen, &p);
                sStore32((uint32_t)ctx->dataLen, &p);

                sStorePad(SCLOUD_HEADER_PAD, SCLOUD_HEADER_PAD, &p);

                bufferLeft -= (p - buffer);

                if( ctx->metaLen) {
                    ctx->buffPtr = ctx->metaBuffer;
                    ctx->byteCount = ctx->metaLen;
                    ctx->state = kSCloudState_Meta;
                }
                else if(ctx->dataLen) {
                    ctx->buffPtr = ctx->dataBuffer;
                    ctx->byteCount = ctx->dataLen;
                    ctx->state = kSCloudState_Data;
                }
                else {
                    ctx->state = kSCloudState_Pad;
                }

                break;

            case kSCloudState_Meta:

                if (ctx->byteCount == 0) {
                    ctx->buffPtr = ctx->dataBuffer;
                    ctx->byteCount = ctx->dataLen;
                    ctx->state = kSCloudState_Data;
                }

                break;

            case kSCloudState_Data:

                if(ctx->byteCount == 0) {
                    ctx->state = kSCloudState_Pad;
                }
                break;

            case kSCloudState_Pad:

                bytesUsed = *bufferSize - bufferLeft;
                bytes2Pad = roundup(bytesUsed, blockLen) - bytesUsed;

                if (bytes2Pad) {
                    // we need to pad to end of block
                    memset(p, bytes2Pad, bytes2Pad);
                    bytesUsed += bytes2Pad;
                    ctx->state = kSCloudState_Done;
                }
                else {
                    // we need to pad an entire block.
                    memset(p, blockLen, blockLen);
                    p+= blockLen;
                    bufferLeft -= blockLen;
                    bytesUsed += blockLen;
                    ctx->state = kSCloudState_Done;
                }
                break;

            case kSCloudState_Done:
                 err = kSCLError_EndOfIteration;
                break;

            default:
                err = kSCLError_ImproperInitialization;
                break;
        }
        bytes2Copy = MIN(bufferLeft, ctx->byteCount );
 
        if (bytes2Copy) {
            COPY(ctx->buffPtr, p, bytes2Copy );
            ctx->byteCount -= bytes2Copy;
            ctx->buffPtr += bytes2Copy;
            p += bytes2Copy;
            bufferLeft -= bytes2Copy;
            bytesUsed = *bufferSize - bufferLeft;
         }

    } while (bufferLeft && ctx->state != kSCloudState_Done);

    if(IsntSCLError(err) && bytesUsed) {
        aes_cbc_encrypt(buffer, buffer, static_cast<int>(bytesUsed), ctx->iv, &ctx->aes_enc);
    }
    computeHash(ctx, buffer, bytesUsed);
    *bufferSize = bytesUsed;

    return err;
}

SCLError SCloudEncryptNext(SCloudContextRef ctx, uint8_t *buffer, size_t *bufferSize)
{
    SCLError err = kSCLError_NoErr;
    int32_t blockLen = ctx->key.blockLength;

    validateSCloudContext(ctx);
    ValidateParam(buffer);
    ValidateParam(bufferSize);

    if (ctx->state == kSCloudState_Init)
        RETERR(kSCLError_ImproperInitialization);

    if (*bufferSize < SCLOUD_MIN_BUF_SIZE)
        RETERR(kSCLError_BadParams);

    if (*bufferSize % (blockLen) != 0)
        RETERR(kSCLError_BadParams);

    err = sCloudEncryptNextInternal(ctx, buffer, bufferSize);

done:
    return err;
}

static SCLError checkHash(SCloudContextRef scloudRef, uint8_t* in, size_t inSize) {
    SCLError err = kSCLError_NoErr;

    LOGGER(INFO, __func__, " --> ", scloudRef->key.keyVersion);

    // Key versions <=2 don't have hash check data, thus assume success
    if (scloudRef->key.keyVersion <= 2)
        return err;

    uint8_t hash[SKEIN256_DIGEST_LENGTH];

    skein256(in, static_cast<unsigned int>(inSize), hash);
    if (memcmp(hash, scloudRef->key.hash, SKEIN256_DIGEST_LENGTH) == 0) {
        LOGGER(INFO, __func__, " <--");
        return err;
    }
    LOGGER(ERROR, __func__, " <-- failed");
    return kSCLError_BadIntegrity;
}


SCLError SCloudDecryptNewMetaDataOnly(uint8_t * key, size_t keyLen,
                                      SCloudEventHandler    handler,
                                      void*                 userValue,
                                      SCloudContextRef      *scloudRefOut)
{
    SCLError  err = kSCLError_NoErr;

    err = SCloudDecryptNew(key, keyLen, handler, userValue, scloudRefOut);

    if (IsntSCLError(err)) {
        SCloudContext* ctx = *scloudRefOut;
        ctx->bJustDecryptMetaData = true;
    }
    return err;
}


SCLError SCloudDecryptNew(uint8_t* key, size_t keyLen,
                          SCloudEventHandler    handler, 
                          void*                 userValue,
                          SCloudContextRef      *cloudRefOut)

{
    SCLError        err = kSCLError_NoErr;
    SCloudContext*  ctx = kInvalidSCloudContextRef;

    uint8_t   keyData[128] = {0};
    size_t    keyDataLen = sizeof(keyData);

    size_t    symKeyLen = 0;
    int32_t   blockLen = 0;

    ValidateParam(cloudRefOut);
    ValidateParam(key);
    ValidateParam(keyLen > 31);

    ctx = (SCloudContext*)XMALLOC(sizeof (SCloudContext)); CKNULL(ctx);

    ZERO(ctx, sizeof(SCloudContext));
    ctx->magic          = kSCloudContextMagic;
    ctx->state          = kSCloudState_Init;
    ctx->bEncrypting    = false;

    ctx->tmpCnt      = 0;

    ctx->handler        = handler;
    ctx->userValue      = userValue;
    ctx->metaBuffer = ctx->dataBuffer = NULL;

    err =  scloudDeserializeKey(key, keyLen, &ctx->key); CKERR;

    if (ctx->key.keySuite == kSCloudKeySuite_AES128) {
        symKeyLen = 16;
        blockLen = AES_BLOCK_SIZE;
    }

    COPY((ctx->key.symKey+symKeyLen), ctx->iv, blockLen);

    if (ctx->key.keySuite == kSCloudKeySuite_AES128)
        aes_decrypt_key128(ctx->key.symKey, &ctx->aes_dec);

    *cloudRefOut = ctx; 

done:
    ZERO(keyData, sizeof(keyData));
    return err;

}

#define SCLOUD_DECRYPT_BUF_SIZE 4096

SCLError SCloudDecryptNext(SCloudContextRef scloudRef, uint8_t* in, size_t inSize)
{
    SCLError err  = kSCLError_NoErr;
    int32_t blockLen = scloudRef->key.blockLength;

    uint8_t *p          = in;
    size_t  bytesLeft   = inSize;

    uint8_t ptBuf[SCLOUD_DECRYPT_BUF_SIZE];
    size_t  ptBufLen    = 0;
    size_t  metaDataTotalLen = scloudRef->metaBufferOffset ;

    validateSCloudContext(scloudRef);
    ValidateParam(in);

    if (scloudRef->bEncrypting)
        RETERR(kSCLError_BadParams);

    if( inSize == 0 && scloudRef->state == kSCloudState_Done) {
        RETERR(kSCLError_EndOfIteration);
    }

    if (checkHash(scloudRef, in, inSize) != kSCLError_NoErr)
        return kSCLError_BadIntegrity;

    while (true) {
        uint8_t *p1 = ptBuf + ptBufLen;

        if ((scloudRef->tmpCnt > 0) || ((bytesLeft > 0) && (bytesLeft < SCLOUD_HEADER_SIZE))) {
            size_t bytes2Store = (scloudRef->state == kSCloudState_Init)? SCLOUD_HEADER_SIZE : blockLen;
            size_t bytes2copy = MIN(bytes2Store - scloudRef->tmpCnt, bytesLeft);

            if (bytes2copy) {
                COPY(p, scloudRef->tmpBuf + scloudRef->tmpCnt, bytes2copy);
                p += bytes2copy;
                bytesLeft -= bytes2copy;
                scloudRef->tmpCnt += bytes2copy;
            }

            if (scloudRef->tmpCnt == bytes2Store) {
                if (scloudRef->key.keySuite == kSCloudKeySuite_AES128)
                    aes_cbc_decrypt(scloudRef->tmpBuf, ptBuf, bytes2Store, scloudRef->iv, &scloudRef->aes_dec);
                ptBufLen += bytes2Store;
                scloudRef->tmpCnt = 0;
            }
        }

        if (bytesLeft) {
            size_t bytes2copy = MIN(SCLOUD_DECRYPT_BUF_SIZE - ptBufLen, bytesLeft);
            bytes2copy = (bytes2copy / blockLen) * blockLen;

            if (scloudRef->key.keySuite == kSCloudKeySuite_AES128) {
                aes_cbc_decrypt(p, ptBuf+ptBufLen, bytes2copy, scloudRef->iv, &scloudRef->aes_dec);
            }
            ptBufLen += bytes2copy;
            p += bytes2copy;
            bytesLeft -= bytes2copy;
        }

        if (ptBufLen == 0 )
             break;

        while (ptBufLen) {
             switch( scloudRef->state) {
                 case kSCloudState_Init:

                     scloudRef->state = kSCloudState_Header;
                     break;

                 case kSCloudState_Header:

                     if (sLoad32(&p1) != kSCloudContextMagic) {
                         RETERR(kSCLError_CorruptData);
                     }
                     scloudRef->metaLen = scloudRef->metaDecryptLen = sLoad32(&p1);
                     scloudRef->metaBuffer = (uint8_t*)XMALLOC(scloudRef->metaLen);

                     scloudRef->dataLen = scloudRef->dataDecryptLen = sLoad32(&p1);
                     scloudRef->dataBuffer = scloudRef->dataDecryptBuffer = (uint8_t*)XMALLOC(scloudRef->dataLen);

                     p1 += SCLOUD_HEADER_PAD;
                     ptBufLen -= SCLOUD_HEADER_SIZE;
                     scloudRef->state  = kSCloudState_Meta;
                     break;

                 case kSCloudState_Meta:

                     if (scloudRef->metaDecryptLen) {
                         size_t metaBytes = MIN(ptBufLen, scloudRef->metaDecryptLen);
                         COPY(p1, scloudRef->metaBuffer+metaDataTotalLen, metaBytes);
                         p1 += metaBytes;
                         metaDataTotalLen += metaBytes;
                         scloudRef->metaBufferOffset = metaDataTotalLen;
                         scloudRef->metaDecryptLen -= metaBytes;
                         ptBufLen -= metaBytes;
                     }
                     else {
                         scloudRef->state = kSCloudState_Data;
                     }
                     break;

                 case kSCloudState_Data:

                     if( scloudRef->bJustDecryptMetaData) {
                         RETERR( kSCLError_EndOfIteration);
                     }

                     if (scloudRef->dataDecryptLen) {
                         size_t dataBytes = MIN(ptBufLen, scloudRef->dataDecryptLen);
                         COPY(p1, scloudRef->dataDecryptBuffer, dataBytes);
                         scloudRef->dataDecryptBuffer += dataBytes;
                         p1 += dataBytes;
                         scloudRef->dataDecryptLen -= dataBytes;
                         ptBufLen -= dataBytes;
                     }
                     else {
                         scloudRef->state = kSCloudState_Pad;
                         scloudRef->padLen  = ptBufLen;
                         if (scloudRef->padLen == 0) scloudRef->padLen = blockLen;
                     }
                     break;

                 case kSCloudState_Pad:

                     if (scloudRef->padLen) {
                         size_t padBytes = MIN(ptBufLen, scloudRef->padLen);
                         p1 += padBytes;
                         scloudRef->padLen -= padBytes;
                         ptBufLen -= padBytes;
                     }
                     if( scloudRef->padLen == 0) {
                         scloudRef->state  = kSCloudState_Done;
                     }
                     break;

                 default:
                     err = kSCLError_UnknownError;
                     CKERR;
                     break;
             }
         }
    }

done:
    return err;
}


void SCloudDecryptGetData(SCloudContextRef scloudRef, uint8_t** data, size_t* dataSize, uint8_t** meta, size_t* metaSize )
{
    *data = scloudRef->dataBuffer;
    *dataSize = scloudRef->dataLen;

    *meta = scloudRef->metaBuffer;
    *metaSize = scloudRef->metaLen;
}


SCLError  SCloudGetVersionString(size_t bufSize, char *outString)
{
    SCLError                 err = kSCLError_NoErr;

    ValidateParam(outString);
    *outString = 0;

    char version_string[32];

    snprintf(version_string, sizeof(version_string), "%s (%03d)", SCLOUD_SHORT_VERSION_STRING, SCLOUD_BUILD_NUMBER);

    if(strlen(version_string) +1 > bufSize)
        RETERR (kSCLError_BufferTooSmall);

    strcpy(outString, version_string);

done:
    return err;
}



