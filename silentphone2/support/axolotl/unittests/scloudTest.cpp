#include <limits.h>

#include "../attachments/fileHandler/scloud.h"

#include "gtest/gtest.h"
#include <iostream>
#include <string>
#include <utility>

using namespace std;

static const uint8_t inData[] =   {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
static const uint8_t inData_1[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,32,1,2,3,4,5,6,7,8};

static const string metadata("This is metadata");  // This is 16 bytes, thus forces a full padding block


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

TEST(SCloud, Basic)
{
    SCloudContextRef scCtxEnc;
    SCloudContextRef scCtxDec;

    SCLError err;
    uint8_t buffer[1024];
    size_t bufSize = 1024;

    uint8_t* blob = NULL;
    size_t blobSize = 0;


    err = SCloudEncryptNew(NULL, 0, (void*)inData, sizeof(inData), (void*)metadata.data(), metadata.size(),
                           NULL, NULL, &scCtxEnc );
    ASSERT_EQ(kSCLError_NoErr, err);

    err = SCloudCalculateKey( scCtxEnc, 0);
    ASSERT_EQ(kSCLError_NoErr, err);

    err = SCloudEncryptGetLocator( scCtxEnc, buffer, &bufSize);
    ASSERT_EQ(kSCLError_NoErr, err);

    bufSize = 1024;
    err = SCloudEncryptGetLocatorREST( scCtxEnc, buffer, &bufSize);
    ASSERT_EQ(kSCLError_NoErr, err);

    string locator((char*)buffer, bufSize);
    cerr << "Locator: " << locator << ", length: " << bufSize << endl;

    err = SCloudEncryptGetKeyBLOB( scCtxEnc, &blob, &blobSize);
    ASSERT_EQ(kSCLError_NoErr, err);
    ASSERT_FALSE(blob == NULL);

    string key((char*)blob, blobSize);
    free(blob);
    cerr << "Key: " << key << ", length: " << blobSize << endl;

    err = SCloudEncryptGetSegmentBLOB( scCtxEnc, 1, &blob, &blobSize);
    ASSERT_EQ(kSCLError_NoErr, err);
    ASSERT_FALSE(blob == NULL);

    string segment((char*)blob, blobSize);
    free(blob);
    cerr << "segment: " << segment << ", length: " << blobSize << endl;

    bufSize = 1024;
    SCloudEncryptNext(scCtxEnc, buffer, &bufSize);
    ASSERT_EQ(kSCLError_NoErr, err);
//     cerr << "length: " << bufSize << endl;
//     hexdump("Encrypted", buffer, bufSize);

    err = SCloudDecryptNew((uint8_t*)key.data(), key.size(), NULL, NULL, &scCtxDec);
    ASSERT_EQ(kSCLError_NoErr, err);

    SCloudDecryptNext(scCtxDec, buffer, bufSize);

    uint8_t* dataBuffer = NULL;
    uint8_t* metaBuffer = NULL;

    size_t dataLen;
    size_t metaLen;

    SCloudDecryptGetData(scCtxDec, &dataBuffer, &dataLen, &metaBuffer, &metaLen);
    ASSERT_FALSE(dataBuffer == NULL);
    ASSERT_FALSE(metaBuffer == NULL);

    string metaDecrypt((char*)metaBuffer, metaLen);
    ASSERT_EQ(metadata, metaDecrypt);
//    cerr << "metaDecrypt: " << metaDecrypt << ", length: " << metaLen << endl;

    int cmpResult = memcmp(dataBuffer, inData, dataLen);
    ASSERT_EQ(0, cmpResult);

//    hexdump("Decrypted", dataBuffer, dataLen);

    SCloudFree(scCtxEnc, 0);
    SCloudFree(scCtxDec, 1);
}

// typical chunk size
static const uint8_t bigData[64*1024] = {0};
static const string metadataBig("This is bigger metadata");

TEST(SCloud, BigBuffer)
{
    SCloudContextRef scCtxEnc;
    SCloudContextRef scCtxDec;

    SCLError err;
    uint8_t buffer[1024];
    size_t bufSize = 1024;
    uint8_t* bigBuffer;

    uint8_t* blob = NULL;
    size_t blobSize = 0;

    err = SCloudEncryptNew(NULL, 0, (void*)bigData, 64*1024, (void*)metadataBig.data(), metadataBig.size(),
                           NULL, NULL, &scCtxEnc );
    ASSERT_EQ(kSCLError_NoErr, err);

    err = SCloudCalculateKey(scCtxEnc, 0);
    ASSERT_EQ(kSCLError_NoErr, err);

    err = SCloudEncryptGetLocator(scCtxEnc, buffer, &bufSize);
    ASSERT_EQ(kSCLError_NoErr, err);

    bufSize = 1024;
    err = SCloudEncryptGetLocatorREST(scCtxEnc, buffer, &bufSize);
    ASSERT_EQ(kSCLError_NoErr, err);

    string locator((char*)buffer, bufSize);

    err = SCloudEncryptGetKeyBLOB( scCtxEnc, &blob, &blobSize);
    ASSERT_EQ(kSCLError_NoErr, err);
    ASSERT_FALSE(blob == NULL);

    string key((char*)blob, blobSize);
    free(blob);

    size_t expectedSize = 32 + metadataBig.size() + (64*1024);
    size_t pad = expectedSize % 16;
    expectedSize += (pad == 0) ? 16 : 16 - pad;

    size_t required = SCloudEncryptBufferSize(scCtxEnc);
    ASSERT_EQ(expectedSize, required);

    size_t actual = required;
    bigBuffer = (uint8_t*)malloc(required);

    err = SCloudEncryptNext(scCtxEnc, bigBuffer, &actual);
    ASSERT_EQ(kSCLError_NoErr, err);
    ASSERT_EQ(required, actual);

    err = SCloudDecryptNew((uint8_t*)key.data(), key.size(), NULL, NULL, &scCtxDec);
    ASSERT_EQ(kSCLError_NoErr, err);

    err = SCloudDecryptNext(scCtxDec, bigBuffer, actual);
    ASSERT_EQ(kSCLError_NoErr, err);

    uint8_t* dataBuffer = NULL;
    uint8_t* metaBuffer = NULL;

    size_t dataLen;
    size_t metaLen;

    SCloudDecryptGetData(scCtxDec, &dataBuffer, &dataLen, &metaBuffer, &metaLen);
    ASSERT_FALSE(dataBuffer == NULL);
    ASSERT_FALSE(metaBuffer == NULL);

    string metaDecrypt((char*)metaBuffer, metaLen);
    ASSERT_EQ(metadataBig, metaDecrypt);
    cerr << "metaDecrypt: " << metaDecrypt << ", length: " << metaLen << endl;

    int cmpResult = memcmp(dataBuffer, bigData, dataLen);
    ASSERT_EQ(0, cmpResult);

//    hexdump("Decrypted", dataBuffer, dataLen);

    SCloudFree(scCtxEnc, 0);
    SCloudFree(scCtxDec, 1);

}