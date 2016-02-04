

## Java call mappings (first draft) ##

This shows how we can map the C attachment crypto primitives to Java calls.
More detailed description of the parameters and return data follows. Usually
the byte[] return data are UTF-8 strings and the Java call can convert this
to a Java String. The main exceptions are the encrypt and decrypt functions
with return data that is not necessarily a Java String :-) 


### SCloudEncryptNew ###

Java call:
    long cloudEncryptNew (byte[] context,     // can be null in case no salted locator names       
                          byte[] data,        // raw data
                          byte[] metaData     // metadata, byte encoded string
                          // SCloudEventHandler handler, no callback handler for the crypto primitives
                          // void*              userValue,  // no user data (because no callback handler
                          int[] errorCode);   // to return error code, int array with min length 1

The JNI implementation internally maintaines the SCloudContextRef and returns
an id to it (the `long`). Use this as follows:

    int[] errorCode = new int[1]
    long cloudRef = cloudEncryptNew(..., errorCode);

The `cloudRef` is opaque data for the caller and the caller should not modify
it, only after `clodFree(...)`

C call:
    SCLError SCloudEncryptNew (void *contextStr,     size_t contextStrLen,
                               void *data,           size_t dataLen,
                               void *metaData,       size_t metaDataLen,
                               SCloudEventHandler        handler,
                               void*                     userValue,
                               SCloudContextRef          *scloudRefOut); 


### SCloudCalculateKey ###
Java Call:
    int cloudCalculateKey (long scloudRef);  // blocksize not required

C call:
    SCLError SCloudCalculateKey (SCloudContextRef scloudRef, size_t blocksize) ;


### SCloudEncryptGetKeyBLOB ###
Java call:
    byte[] cloudEncryptGetKeyBLOB(long scloudRef, int[] errorCode);

C call:
    SCLError SCloudEncryptGetKeyBLOB(SCloudContextRef ctx,
                                     uint8_t **outData, size_t *outSize);

### Not used functions - deprecated in C ###
    __attribute__((deprecated))
    SCLError    SCloudEncryptGetKey(SCloudContextRef scloudRef,
                                    uint8_t * buffer, size_t *bufferSize);
    __attribute__((deprecated))
    SCLError    SCloudEncryptGetKeyREST(SCloudContextRef ctx,
                                        uint8_t * buffer, size_t *bufferSize);


### SCloudEncryptGetLocator ###
Java call:
    byte[] cloudEncryptGetLocator(long scloudRef, int[] errorCode);

C call:
    SCLError SCloudEncryptGetLocator(SCloudContextRef scloudRef,
                                     uint8_t * buffer, size_t *bufferSize);


### SCloudEncryptGetLocatorREST ###
Java Call:
    byte[] cloudEncryptGetLocatorREST(long scloudRef, int[] errorCode);

C call:
    SCLError SCloudEncryptGetLocatorREST(SCloudContextRef ctx, 
                                         uint8_t * buffer, size_t *bufferSize);


### SCloudEncryptNext ###
Java call:
    byte[] cloudEncryptNext(long scloudRef,
                            byte[] inBuffer, int[] errorCode);

Returns a byte array with the encrypted segment, including header, meta data etc

C call:
    SCLError SCloudEncryptNext(SCloudContextRef cloudRef,
                               uint8_t *buffer, size_t *bufferSize);


### SCloudDecryptNew ###
Java call:
    long cloudDecryptNew (byte[] key
                          // SCloudEventHandler    handler, 
                          // void*                 userValue,
                          int[] errorCode); 

No callback handler for the crypto primitive, returns a reference similar to
`cloudEncryptNew(...)`

C call:
    SCLError    SCloudDecryptNew(uint8_t * key, size_t keyLen,
                                 SCloudEventHandler    handler, 
                                 void*                 userValue,
                                 SCloudContextRef      *scloudRefOut); 


### SCloudDecryptNewMetaDataOnly ###
Java call:
    long cloudDecryptNewMetaDataOnly(byte[] key
                                     // SCloudEventHandler    handler, 
                                     // void*                 userValue,
                                     int[] errorCode); 

C call:
    SCLError SCloudDecryptNewMetaDataOnly(uint8_t * key, size_t keyLen,
                                          SCloudEventHandler    handler,
                                          void*                 userValue,
                                          SCloudContextRef      *scloudRefOut);


### SCloudDecryptNext ###
Java call:
    byte[] SCloudDecryptNext(long scloudRef,
                             byte[] in, int[] errorCode);

C call:
    SCLError SCloudDecryptNext(SCloudContextRef scloudRef,
                               uint8_t *in, size_t inSize);


### SCloudGetVersionString ###
Java call:
    String cloudGetVersionString();

C call:
    SCLError SCloudGetVersionString(size_t bufSize, char *outString);


#### SCloudFree ###
Java call:
    void cloudFree (long scloudRef);

C call:
    void SCloudFree (SCloudContextRef scloudRef  );


#### SCloudEncryptGetSegmentBLOB ###
Java call:
    byte[] cloudEncryptGetSegmentBLOB(long scloudRef, int segNum, int[] errorCode);

C call:
    SCLError SCloudEncryptGetSegmentBLOB(SCloudContextRef ctx, int segNum, 
                                         uint8_t **outData, size_t *outSize );
