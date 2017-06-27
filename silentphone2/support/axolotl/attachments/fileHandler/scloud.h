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

#ifndef Included_scloud_h   /* [ */
#define Included_scloud_h

#include "../scloudPubTypes.h"

#ifdef __clang__
#pragma mark
#pragma mark SCloud Public Defines
#endif

#define SCLOUD_BUILD_NUMBER             3
#define SCLOUD_SHORT_VERSION_STRING     "1.0.0"

// Must be <= the hash size used. Currently we use Skein256 which is also 32 bytes
#define SCLOUD_LOCATOR_LEN          32
#define SCLOUD_MIN_BUF_SIZE         32

#define TRUNCATED_LOCATOR_BITS      160


#ifdef __cplusplus
extern "C"
{
#endif


typedef struct SCloudContext*      SCloudContextRef;

/*____________________________________________________________________________
 Invalid values for each of the "ref" data types. Use these for assignment
 and initialization only. Use the SCXXXRefIsValid macros (below) to test
 for valid/invalid values.
 ____________________________________________________________________________*/

#define kInvalidSCloudContextRef        ((SCloudContextRef) NULL)

/*____________________________________________________________________________
 Macros to test for ref validity. Use these in preference to comparing
 directly with the kInvalidXXXRef values.
 ____________________________________________________________________________*/

#define SCloudContextRefIsValid( ref )      ( (ref) != kInvalidSCloudContextRef )


#ifdef __clang__
#pragma mark
#pragma mark SCloud Callbacks
#endif

enum SCloudEventType_
{
    kSCloudEvent_NULL             = 0,
    kSCloudEvent_Init,
    kSCloudEvent_Progress,
    kSCloudEvent_Error,
    kSCloudEvent_DecryptedData,
    kSCloudEvent_DecryptedMetaData,
    kSCloudEvent_DecryptedMetaDataComplete,
    kSCloudEvent_Done,
    kSCloudEvent_DecryptedHeader,
    
    ENUM_FORCE( SCloudEvent_ )
};
ENUM_TYPEDEF( SCloudEventType_, SCloudEventType  );


// typedef struct SCloudEventDecryptData_
// {
//     uint8_t*            data;
//     size_t              length;
// } SCloudEventDecryptData;
// 
// typedef struct SCloudEventDecryptMetaData_
// {
//     uint8_t*            data;
//     size_t              length;
// } SCloudEventDecryptMetaData;
// 
// 
// typedef struct SCloudEventErrorData_
// {
//     SCLError    error;
// } SCloudEventErrorData;
// 
// 
// typedef struct SCloudEventProgressData_
// {
//     size_t          bytesProcessed;
//     size_t          bytesTotal;
// } SCloudEventProgressData;
// 
// typedef struct SCloudEventDecryptedHeaderData_
// {
//     size_t          metaDataBytes;
//     size_t          dataBytes;
// } SCloudEventDecryptedHeaderData;
// 
// typedef union SCloudEventData
// {
//     SCloudEventErrorData         errorData;
//     SCloudEventDecryptMetaData   metaData;
//     SCloudEventDecryptData       decryptData;
//     SCloudEventProgressData      progress;
//     SCloudEventDecryptedHeaderData        header;
//     
// } SCloudEventData;
// 
// struct SCloudEvent
// {
//     SCloudEventType           type;         /**< Type of event */
//     SCloudEventData          data;          /**< Event specific data */
//     
// };
// typedef struct SCloudEvent SCloudEvent;

typedef int (*SCloudEventHandler)(SCloudContextRef      scloudRef,
void*            event,
void*            uservalue);


#ifdef __clang__
#pragma mark SCloud Public Functions
#endif

SCLError    SCloudEncryptNew (void *contextStr,     size_t contextStrLen,
                              void *data,           size_t dataLen,
                              void *metaData,       size_t metaDataLen,
                              SCloudEventHandler        handler,
                              void*                     userValue,
                              SCloudContextRef          *scloudRefOut); 

size_t  SCloudEncryptBufferSize(SCloudContextRef scloudRef);

SCLError    SCloudCalculateKey ( SCloudContextRef scloudRef, size_t blocksize) ;


SCLError    SCloudEncryptGetKeyBLOB( SCloudContextRef ctx,
                         uint8_t **outData, size_t *outSize);

SCLError    SCloudEncryptGetLocator ( SCloudContextRef scloudRef,
                                     uint8_t * buffer, size_t *bufferSize);

SCLError    SCloudEncryptGetLocatorREST ( SCloudContextRef ctx, 
                                         uint8_t * buffer, size_t *bufferSize);

SCLError    SCloudEncryptNext ( SCloudContextRef cloudRef,
                               uint8_t *buffer, size_t *bufferSize);


SCLError    SCloudDecryptNew (uint8_t * key, size_t keyLen,
                              SCloudEventHandler    handler, 
                              void*                 userValue,
                              SCloudContextRef      *scloudRefOut); 

SCLError    SCloudDecryptNewMetaDataOnly (uint8_t * key, size_t keyLen,
                                          SCloudEventHandler    handler,
                                          void*                 userValue,
                                          SCloudContextRef      *scloudRefOut);


SCLError    SCloudDecryptNext( SCloudContextRef scloudRef, uint8_t* in, size_t inSize );

void    SCloudDecryptGetData( SCloudContextRef scloudRef, uint8_t** data, size_t* dataSize, uint8_t** meta, size_t* metaSize );
 
SCLError  SCloudGetVersionString(size_t bufSize, char *outString);

void      SCloudFree (SCloudContextRef scloudRef, int freeDecryptBuffers);

SCLError SCloudEncryptGetSegmentBLOB( SCloudContextRef ctx, int segNum, uint8_t **outData, size_t *outSize );

#ifdef __cplusplus
}
#endif


#endif /* Included_scloud_h */ /* ] */
