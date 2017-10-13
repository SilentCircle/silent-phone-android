/*
Copyright (C) 2015-2017, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
#include "interfaceApp/AppInterfaceImpl.h"
#include "interfaceTransport/sip/SipTransport.h"

#include "ratchet/crypto/AesCbc.h"
#include "ratchet/crypto/EcCurve.h"
#include "Constants.h"
#include "ratchet/ZinaPreKeyConnector.h"
#include "ratchet/state/ZinaConversation.h"
#include "ratchet/ratchet/ZinaRatchet.h"
#include "keymanagment/PreKeys.h"
#include "util/UUID.h"

//#include "../util/cJSON.h"
//#include "../util/b64helper.h"
//#include "../provisioning/Provisioning.h"
//#include "../provisioning/ScProvisioning.h"
#include "storage/sqlite/SQLiteStoreConv.h"

#include "../tiviengine/tivi_log.h"


#include "axolotl_glue.h"

using namespace zina;

static bool zinaHttpHelperSet = false;
static int storeDBsopened = 0;
static AppInterfaceImpl* axoAppInterface = NULL;
static Transport *sipTransport = NULL;

static STATE_FUNC _messageStateReport = NULL;
static RECV_FUNC _receiveMessage = NULL;
static NOTIFY_FUNC _notifyCallback = NULL;

static GROUP_CMD_RECV_FUNC _groupReceiveCommand = NULL;
static GROUP_MSG_RECV_FUNC _groupReceiveMessage = NULL;
static GROUP_STATE_FUNC _groupStateCallback = NULL;

static SEND_DATA_FUNC _sendDataFuncAxo;

static int mustAddPrekeyCnt = 0;

void t_setAxoTransport(Transport *transport){
   sipTransport = transport;
   //sipTransport->setSendDataFunction(sendDataFuncAxo);
}

/**
 * Minimum number of zina sqlite files required to be ready for access.
 */
#define MIN_DB_FILES_COUNT 2


/* Begin #if defined(__APPLE__) */
#if defined(__APPLE__)
#include "../os/CTMutex.h"
#import "appRepository/AppRepository.h"
#import "SCSPLog_private.h"

const char *getAPIKey(void);
char * getFileStorePath(void);
unsigned char *get32ByteAxoKey(void);
const char *t_getDevID_md5(void);

void setFileBackgroundReadable(const char *fn);
void exitWithFatalErrorMsg(const char *msg);
int32_t s3Helper(const std::string& region, const std::string& requestData, std::string* response);
int32_t httpHelper(const std::string& requestUri, const std::string& method, const std::string& requestData, std::string* response);

// Plain public API without a class
AppInterface* t_getAxoAppInterface() { return axoAppInterface; }
bool isZinaHttpHelperSet() { return zinaHttpHelperSet; }
bool areZinaDatabasesOpen() { return (storeDBsopened >= MIN_DB_FILES_COUNT); }
void zinaDatabaseWasOpened() { storeDBsopened++; }

const char *CTAxoInterfaceBase::getErrorMsg(int id){
   
   switch(id){
      case GENERIC_ERROR        : return "Generic error code, unspecified error";
      case VERSION_NOT_SUPPORTED: return "Unsupported protocol version";
      case BUFFER_TOO_SMALL     : return "Buffer too small to store some data";
      case NOT_DECRYPTABLE      : return "Could not decrypt received message";
      case NO_OWN_ID            : return "Found no own identity for registration";
      case JS_FIELD_MISSING     : return "Missing a required JSON field";
      case NO_DEVS_FOUND        : return "No registered ZINA devices found for a user";
      case NO_PRE_KEY_FOUND     : return "No more pre-keys for user's devices";
      case NO_SESSION_DATA      : return "No session for this user found";
      case SESSION_NOT_INITED   : return "Session not initialized";
      case OLD_MESSAGE          : return "Message too old to decrypt";
      case CORRUPT_DATA         : return "Incoming data CORRUPT_DATA";
      case AXO_CONV_EXISTS      : return "ZINA conversation exists while trying to setup new one";
      case MAC_CHECK_FAILED     : return "HMAC check of encrypted message failed";
      case MSG_PADDING_FAILED   : return "Incorrect padding of decrypted message";
      case SUP_PADDING_FAILED   : return "Incorrect padding of decrypted supplementary data";
      case NO_STAGED_KEYS       : return "No staged keys available (not an error)";
      case RECEIVE_ID_WRONG     : return "Receiver's long term id key hash mismatch";
      case SENDER_ID_WRONG      : return "Sender's long term id key hash mismatch";
      case RECV_DATA_LENGTH     : return "Expected length of data does not match received length";
      case WRONG_RECV_DEV_ID    : return "Expected device id does not match actual device id";
      case NETWORK_ERROR        : return "The HTTP request returned code 400 or SIP failed";
      case DATA_MISSING         : return "Some data for a function is missing";
      case DATABASE_ERROR       : return "SQLCipher/SQLite returned an error code";
      case REJECT_DATA_RETENTION: return "Reject data retention when sending a message";
      case PRE_KEY_HASH_WRONG   : return "Pre-key check failed during setup of new conversation or re-keying";
      case ILLEGAL_ARGUMENT     : return "Value of an argument is illegal/out of range";
      case CONTEXT_ID_MISMATCH  : return "ZINA ratchet data is probably out of sync";
   }

   static char err[64];
   snprintf(err,sizeof(err)-10, "Unknown Error - Code: %d", id);
   return err;
}

using namespace std;

static string drFlags;
static int drISOn = 0;

const char *t_getDRJsonFlagFN(int drOnOffFlag){
    static char buffn0[512];
    static char buffn1[512];
    char *p = drOnOffFlag?&buffn0[0]:&buffn1[0];
    if(p[0])return &p[0];
    int createFN(char *p, const char *fn);
    createFN(p, drOnOffFlag? "drOnOff.text":"drJsonFlags.json");
    return p;
}


void zina_setDataRetentionFlags(const string &jsonFlags, int isOn){
    drFlags = jsonFlags;
    if(axoAppInterface){
        axoAppInterface->setDataRetentionFlags(jsonFlags);
        axoAppInterface->setS3Helper(isOn? s3Helper: NULL);
    }
    drISOn = isOn;
    
    void saveFile(const char *fn,void *p, int iLen);
    saveFile(t_getDRJsonFlagFN(0), (void *)jsonFlags.c_str(), (int)jsonFlags.size());
    saveFile(t_getDRJsonFlagFN(1), isOn? (void*)"1":(void*)"0", 1);
    setFileBackgroundReadable(t_getDRJsonFlagFN(0));
    setFileBackgroundReadable(t_getDRJsonFlagFN(1));

}

void *CTAxoInterfaceBase::getAxoAppInterface(){return axoAppInterface;}

static bool sendDataFuncAxo(uint8_t* name, uint8_t* devId, uint8_t* envelope, size_t size, uint64_t msgId){
   
   return _sendDataFuncAxo(name, devId, envelope, size, msgId);
}

/*
 * Receive message callback for AppInterfaceImpl.
 */
//UI
//from axolotl
static int32_t receiveMessage(const std::string& messageDescriptor, const std::string& attachementDescriptor = std::string(), const std::string& messageAttributes = std::string())
{
   if(!_receiveMessage){
      printf("!receiveMessage=%s\n", messageDescriptor.c_str());
      return CTAxoInterfaceBase::eErrorNoFunctionPointers;
   }
   
   return _receiveMessage(messageDescriptor, attachementDescriptor, messageAttributes);
}

/*
 * State change callback for AppInterfaceImpl.
 */
static void messageStateReport(int64_t messageIdentfier, int32_t statusCode, const std::string& stateInformation)
{
   if(!_messageStateReport){
      printf("!messageStateReport %lld code=%d stateInformation=%s\n", messageIdentfier, statusCode, stateInformation.c_str());
      return;
   }
   
   _messageStateReport(messageIdentfier, statusCode, stateInformation);
}

static void notifyCallback(int32_t notifyAction, const string& actionInformation, const string& devId){
   
    if(!_notifyCallback) {
        printf("!_notifyCallback notifyAction:%d actionInformation=%s devId=%s\n", notifyAction, actionInformation.c_str(), devId.c_str());
        return;
    }
        
    _notifyCallback(notifyAction, actionInformation.c_str(), devId.c_str());
}

static int32_t receiveGroupMessage(const string& messageDescriptor, const string& attachmentDescriptor = string(), const string& messageAttributes = string())
{
   if(!_groupReceiveMessage){
      printf("!_groupReceiveMessage=%s\n", messageDescriptor.c_str());
       return CTAxoInterfaceBase::eErrorNoFunctionPointers;
   }

   return _groupReceiveMessage(messageDescriptor, attachmentDescriptor, messageAttributes);
}

static int32_t receiveGroupCommand(const string& commandMessage)
{
   if(!_groupReceiveCommand){
      printf("!_groupReceiveCommand=%s\n", commandMessage.c_str());
       return CTAxoInterfaceBase::eErrorNoFunctionPointers;
   }

   return _groupReceiveCommand(commandMessage);
}

static void groupStateReport(int32_t statusCode, const string& stateInformation)
{
   if(!_groupStateCallback){
      printf("!_groupStateCallback code=%d stateInformation=%s\n", statusCode, stateInformation.c_str());
      return;
   }
   _groupStateCallback(statusCode, stateInformation);

}

static void verifyAxolotlResult(std::string &info){
    char result[256];
    char msg[256];
    
    int findJSonToken(const char *p, int iPLen, const char *key, char *resp, int iMaxLen);
    
    int l=findJSonToken(info.c_str(),(int)info.length(),"result",&result[0],sizeof(result));
    if(l>0)printf("result=[%.*s]\n",l,result);
    
    //{"error_code": -1, "result": "error", "error_msg": "Please provide a valid API key."}
    //{"result": "error", "error_msg": "Device not found."}
    
    if(strcmp(result, "error")==0){
        
        l=findJSonToken(info.c_str(),(int)info.length(),"error_msg",&msg[0],sizeof(msg));
        if(l>0)printf("msg=[%.*s]\n",l,msg);
        
        if(strcmp(msg, "Please provide a valid API key.")==0
           || strcmp(msg,"Device not found.")==0){//TODO verify code
            
            //TODO delete all app data and restart SP
            exitWithFatalErrorMsg("Please, uninstall Silent Phone, and then install again.");
        }
    }
}

void tryRegisterAxolotl(int firstStart, int iForce){
    
    if(!axoAppInterface)return;
    
    int isFileExists(const char *fn);
    
    char buffn[512];
    int createFN(char *p, const char *fn);
    createFN(buffn, "axoreg.txt");
    
    int rep = 0;
    do{
        if(firstStart || rep){
            std::string info;
            axoAppInterface->registerZinaDevice(&info);
            t_logf(log_events, __FUNCTION__,"registerAxolotlDevice=[%s]",info.c_str());
            
            printf("registerAxolotlDevice=[%s]",info.c_str());
            
            rep = 0;
            
            verifyAxolotlResult(info);
            mustAddPrekeyCnt = 0;
            //if this fails i have to reset this using different apiKey()
        }
        else{
            //  int cnt2 =  axoAppInterface->getNumPreKeys();
            //axoAppInterface->newPreKeys(5);
            // printf("cnt2=%d\n",cnt2);
            if(iForce || !isFileExists(buffn)){
                int cnt =  axoAppInterface->getNumPreKeys();
                if(cnt < 1){
                    rep = !rep;
                    void deleteFile(const char *fn);
                    deleteFile(buffn);
                }
                else {
                    rep = 0;
                    void saveFile(const char *fn,void *p, int iLen);
                    saveFile(buffn, (void*)"ok", 2);
                }
            }
            else{
                rep = 0;
            }
        }
    }while(rep);
}

/**
 * iOS-specific CTAxoInterfaceBase::sharedInstance main initializer.
 *
 * Called by the t_initAxolotl initializer helper function on first
 * on first access of the CTAxoInterfaceBase::sharedInstance, this 
 * function initializes the local AppInterfaceImpl axoAppInterface instance.
 */
static int _initAxolotl(const char *db, const char *pw, int pwLen, const char *name, const char *api_key, const char *devid){

   if(axoAppInterface)
       return 0;

   if (name == NULL)
      return -10;

   if (api_key == NULL)
      return -12;
   
   int nameLen = (int)strlen(name);
   
   if (nameLen == 0)
      return -11;

   int authLen = (int)strlen(api_key);
   
   if (authLen == 0)
      return -13;
   
   if (pw == NULL)
      return -14;
    
   if (pwLen != 32)
      return -15;
    
    // It is an error for SPi to attempt to initialize 
    // the axo instance with a null/empty db path string.
    if (db == NULL || strcmp(db,"") == 0) {
        t_logf(log_events, __FUNCTION__,"Error: store->openStore called "
               "with empty or nil database path");        
        exitWithFatalErrorMsg("Unable to open database:\n Path not specified");
        return -1;
    }
    
   int isFileExists(const char *fn);
   int firstStart = !isFileExists(db);

   std::string dbPw((const char*)pw, pwLen);
   
   // initialize and open the persitent store singleton instance   
   SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    
   store->setKey(dbPw);
       
    // Log file protection attributes on the db path
    void log_file_protection_prop(const char *fn);
    log_file_protection_prop(db);

    // Always call to set NSFileProtectionNone on db path
    // (this is a no-op if already set)
    setFileBackgroundReadable(db);
    
    int sql_code = store->openStore(std::string (db));
    
    if(sql_code == 0)
        zinaDatabaseWasOpened();
    else {
        t_logf(log_events, __FUNCTION__,"Error: store->openStore failed: %d", sql_code);
        
        char msg[100];
        sprintf(msg, "%s %d", "Unable to open database.\nError: ", sql_code);
        exitWithFatalErrorMsg(msg);
        
        return -1;
    }          
   
  // store->resetStore();
    
   auto ownAxoConv = ZinaConversation::loadLocalConversation(name,*store);

   if (!ownAxoConv->isValid()) {  // no yet available, create one. An own conversation has the same local and remote name, empty device id

       KeyPairUnique idKeyPair = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);
       ownAxoConv->setDHIs(move(idKeyPair));
       ownAxoConv->storeConversation(*store);
   }
   
   axoAppInterface = new AppInterfaceImpl(std::string((const char*)name, nameLen),
                                          std::string((const char*)api_key, authLen),
                                          std::string(devid),
                                          receiveMessage,   messageStateReport,notifyCallback,
                                          receiveGroupMessage, receiveGroupCommand, groupStateReport);
    

    if(drFlags.length()>0){
        axoAppInterface->setDataRetentionFlags(drFlags);
        axoAppInterface->setS3Helper(drISOn ? s3Helper: NULL);
    }
    else{
        char *loadFile(const  char *fn, int &iLen);
        
        int drl=0,drOFl=0;
        char * drF = loadFile(t_getDRJsonFlagFN(0), drl);
        char * drOnOff = loadFile(t_getDRJsonFlagFN(1), drOFl);
        
        if(drl && drF)axoAppInterface->setDataRetentionFlags(string(drF,drl));
        if(drOnOff)axoAppInterface->setS3Helper(drOnOff[0]=='1' ? s3Helper: NULL);
        
        if(drF)delete drF;
        if(drOnOff)delete drOnOff;
    
    }
    
   sipTransport = new SipTransport(axoAppInterface);
   sipTransport->setSendDataFunction(sendDataFuncAxo);
   
   axoAppInterface->setTransport(sipTransport);
   axoAppInterface->setHttpHelper(httpHelper);

   zinaHttpHelperSet = true;

   void tryRegisterAxolotl(int firstStart, int iForce);

   tryRegisterAxolotl(firstStart, 0);
   
   if(!firstStart && mustAddPrekeyCnt){
      axoAppInterface->newPreKeys(mustAddPrekeyCnt);
      mustAddPrekeyCnt = 0;
   }
   
   return 1;
}

/**
 * iOS-specific main initializer helper function.
 *
 * Called by the CTAxoInterfaceBase::sharedInstance accessor on first
 * access, this function calls the _initAxolotl main initializer with the
 * SQLiteStoreConv db filepath, axoKey, axoKey length, and local username.
 */
static int t_initAxolotl(const char *myUsername)
{
//   const char *getAPIKey(void);
//   char * getFileStorePath(void);
//   unsigned char *get32ByteAxoKey(void);
//   const char *t_getDevID_md5(void);
   
   static char fn[1024];
    
    snprintf(&fn[0],sizeof(fn)-1,"%s/axo_%s_sql.db",getFileStorePath(), myUsername);
    
    int isFileExists(const char *fn);
    static int iOldApp = 0;
    if(iOldApp || isFileExists(fn)){
        iOldApp = 1;

        exitWithFatalErrorMsg("Please, uninstall Silent Phone, and then install again.");
        return -1;
    }
   
   snprintf(&fn[0],sizeof(fn)-1,"%s/axo_%s_secure_sql.db",getFileStorePath(), myUsername);
   fn[sizeof(fn)-1]=0;
   
   return _initAxolotl(fn,(const char *)get32ByteAxoKey(), 32, myUsername,
                      getAPIKey(),
                     //  "abcd1234abcd1234abcd1234abcd1234");//
                       t_getDevID_md5());
}

AppRepository * getChatDb(const char *dbPath) {   
    
    // It is an error for SPi to attempt to open the 
    // zina chat store with a null/empty db path string.
    if (dbPath == NULL || strcmp(dbPath,"") == 0) {
        t_logf(log_events, __FUNCTION__,"Error: store->openStore called "
               "with empty or nil database path");        
        exitWithFatalErrorMsg("Unable to open database:\n Path not specified");
        return NULL;
    }
    
    // Log file protection attributes on the db path
    void log_file_protection_prop(const char *fn);
    log_file_protection_prop(dbPath);
    
    // Always call to set NSFileProtectionNone on db path
    // (this is a no-op if already set)
    setFileBackgroundReadable(dbPath);
    
    AppRepository *db = AppRepository::getStore();
        
    unsigned char *key = get32ByteAxoKey();    
    std::string dbPw((const char*)key, 32);
    db->setKey(dbPw);
    
    int sql_code = db->openStore(dbPath);
    
    if(sql_code != 0) {       
        t_logf(log_events, __FUNCTION__,"Error: store->openStore failed: %d", sql_code);

        char msg[100];
        sprintf(msg, "%s %d", "Unable to open database.\nError: ", sql_code);
        exitWithFatalErrorMsg(msg);
        
        return NULL;
    }       
    
    // See SCSPLog_private class
    set_zina_log_cb((void *)1, ios_log_zina);

    zinaDatabaseWasOpened();
    
    return db;
}

 /*
 {
 "version":    <int32_t>,            # Version of JSON send message descriptor, 1 for the first implementation
 "recipient":  <string>,             # for SC this is either the user's name of the user's DID
 "deviceId" :  <int32_t>,            # optional, if we support multi-device, defaults to 1 if missing
 # set to 0 to send the message to each registered device
 # of the user
 "scClientDevId" : <string>,         # the sender's device id, same as used to register the device (v1/me/device/{device_id}/)
 "message":    <string>              # the actual plain text message, UTF-8 encoded (Java programmers beware!)
 }
 */

time_t CTAxoInterfaceBase::uuid_sz_time(const char * szUUID, struct timeval *ret_tv){
    uuid_t uuid;
    struct timeval tv;
    if(!ret_tv)ret_tv = &tv;
    uuid_parse(szUUID, uuid);
    time_t tm = uuid_time(uuid, ret_tv);
    return tm;
}

const char *CTAxoInterfaceBase::generateMsgID(const char *msgToSend, char *dst, int iDstSize){
   
   uuid_t pingUuid;

   if(iDstSize < sizeof(uuid_string_t)) return "iDstSize < sizeof(uuid_string_t)";

   uuid_generate_time(pingUuid);
   uuid_unparse(pingUuid, dst);
   
   return dst;
   
   /*
#define MD5_HASH_SIZE 32
   if(iDstSize < MD5_HASH_SIZE + 1) return "ERR: iDstSize < MD5_HASH_SIZE + 1";
   time_t  t = time(NULL);
   static int b=0;
   b++;
   char msgidX[MD5_HASH_SIZE*3+3];
   char msgid[MD5_HASH_SIZE+1];
   
   int calcMD5(unsigned char *p, int iLen, char *out);
   calcMD5((unsigned char *)msgToSend,strlen(msgToSend),&msgidX[0]);
   calcMD5((unsigned char *)&t,sizeof(time_t),&msgidX[32]);
   calcMD5((unsigned char *)&b,sizeof(int),&msgidX[64]);
   calcMD5((unsigned char *)&msgidX[0], sizeof(msgidX), &dst[0]);
   dst[MD5_HASH_SIZE]=0;
    return dst;
    */
   
}

static CTMutex _sendMutex;
/*
static int64_t t_sendAxoMessage(const char *dstUsername, const char *msg){
   const char *t_getDevID_md5(void);
   char buf[4096];
   char j[4096-1024];
   
    char szUNWithoutDomain[64];
    strncpy(szUNWithoutDomain, dstUsername, sizeof(szUNWithoutDomain));
    szUNWithoutDomain[sizeof(szUNWithoutDomain)-1]=0;
    for(int i=0;;i++){
        if(!szUNWithoutDomain[i])break;
        if(szUNWithoutDomain[i]=='@'){szUNWithoutDomain[i]=0;break;}
    }
    dstUsername=&szUNWithoutDomain[0];
    
   int t_encode_json_string(char *out, int iMaxOut, const char *in);
   t_encode_json_string(j, sizeof(j) - 1, msg);

   char msgid[32+1];
   
   int l = snprintf(buf, sizeof(buf)-1,
                    "{"
                    "\"version\": 1,"
                    "\"recipient\": \"%s\","
                    "\"deviceId\": 1,"
                    "\"scClientDevId\":\"%s\","
                    "\"msgId\":\"%s\","
                    "\"message\":\"%s\""
                    "}"
                    ,dstUsername
                    ,t_getDevID_md5()
                    ,CTAxoInterfaceBase::generateMsgID(j, msgid, sizeof(msgid))
                    ,j);
   
   std::string message((const char*)buf, l);
   
   std::string attachment;
   std::string attributes;
   
   CTMutexAutoLock _a(_sendMutex);
   
   std::vector<int64_t>* msgIds = axoAppInterface->sendMessage(message, attachment, attributes);
   
   if (msgIds == NULL || msgIds->empty()) {
      delete msgIds;
      return 0;
   }
   
   
   return msgIds->at(0);
}
*/
static int64_t t_sendMessageToEveryDevice(unique_ptr<list<unique_ptr<PreparedMessageData> > > prepMessageData)
{
   auto idVector = make_shared<vector<uint64_t> >();
   
   int64_t retCode = 0;
   

   while (!prepMessageData->empty()) {
      auto& msgData = prepMessageData->front();
      
      printf("sendto: %s\n",msgData->receiverInfo.c_str());

      idVector->push_back(msgData->transportId);
      
      if(!retCode && msgData->transportId){
         retCode = msgData->transportId;
      }

      prepMessageData->pop_front();
   }
   
  if(axoAppInterface->doSendMessages(idVector)<0)return 0;

   return retCode;
}

static int64_t t_sendJSONMessage( const char *msg,  bool toSibling, bool normalMsg, const char *attachment, const char *attributes){
   
   char bufValue[1024];
   int findJSonToken(const char *p, int iPLen, const char *key, char *resp, int iMaxLen);
   int l=findJSonToken(msg,(int)strlen(msg),"message",&bufValue[0],sizeof(bufValue)-1);
   if(l>0 && strcmp(bufValue, "*##*delKey*")==0){
      std::string own = axoAppInterface->getOwnUser();
      SQLiteStoreConv* store = SQLiteStoreConv::getStore();
      l = findJSonToken(msg,(int)strlen(msg),"recipient",&bufValue[0],sizeof(bufValue)-1);
      if(l>0)
         store->deleteConversationsName(std::string(bufValue,l) , own);
      return 0;
   }

   std::string message(msg);
   
   std::string s_attachment;
   std::string s_attributes;
   
   if(attachment){
      s_attachment.assign(attachment);
   }
   
   if(attributes){
      s_attributes.assign(attributes);
   }
   
   CTMutexAutoLock _a(_sendMutex);

#if 1
   //this is tmp shortcut to support old API from UI
   int32_t result = 0;
    
    unique_ptr<list<unique_ptr<PreparedMessageData> > > deviceList;
    
    if (toSibling)
    {
       deviceList = axoAppInterface->prepareMessageSiblings(message, s_attachment, s_attributes, normalMsg, &result);
    } else
    {
        deviceList = axoAppInterface->prepareMessageNormal(message, s_attachment, s_attributes, normalMsg, &result);
    }
   
   if(result != SUCCESS ){
      
      if(result != NO_DEVS_FOUND){//this not fail
         return 0;
      }
      return 0;
   }
   return t_sendMessageToEveryDevice(move(deviceList));
   

#else
   
   std::vector<int64_t>* msgIds = axoAppInterface->sendMessage(message, s_attachment, s_attributes);
   
   if (msgIds == NULL || msgIds->empty()) {
      delete msgIds;
      return 0;
   }
   
   int64_t ret = 0;
   for(int i = 0; !ret && i < msgIds->size(); i++){
      ret = msgIds->at(i);
   }
   
   delete msgIds;
   
   return ret;
#endif


}
#endif
/* End #if defined(__APPLE__) */

int CTAxoInterfaceBase::isAxolotlReady(){return sipTransport ? 1 : 0;}

int CTAxoInterfaceBase::addPrekeys(int cnt){
   if(!axoAppInterface){
      mustAddPrekeyCnt = cnt;
      return 0;
   }
   int ok = 200 == axoAppInterface->newPreKeys(cnt);
   if(!ok){
      mustAddPrekeyCnt = cnt;
   }
   
   return 1;
}

class CTAxoInterface:public CTAxoInterfaceBase{
   //TODO store transport and AppInterfaceImpl here
public:
   CTAxoInterface(const char *un){

#if defined(__APPLE__)
      t_initAxolotl(un);
#endif
   }
   
#if defined(__APPLE__)
   int64_t sendJSONMessage( const char *msg, bool toSibling, bool normalMsg, const char *attachment, const char *attributes){
      return t_sendJSONMessage( msg, toSibling, normalMsg, attachment, attributes);
   }
#endif
    
   //from transport
   int32_t receiveMessage(uint8_t* data, size_t length){
      if(!sipTransport)return eErrorNoTransportReady;
      return sipTransport->receiveAxoMessage(data, length);
   }
   
   int32_t receiveMessage(uint8_t* data, size_t length, uint8_t* uid, size_t ulength, uint8_t* alias, size_t alength){
      if(!sipTransport)return eErrorNoTransportReady;
      return ((SipTransport*) sipTransport)->receiveAxoMessage(data, length, uid, ulength, alias, alength);
   }
   //from transport
   void stateReport(int64_t messageIdentifier, int32_t stateCode, uint8_t* data, size_t length){
      if(!sipTransport)return;
      sipTransport->stateReportAxo(messageIdentifier, stateCode, data, length);
   }
    //from transport
   void notifyAxo(uint8_t* data, size_t length){
      if(!sipTransport)return;
      sipTransport->notifyAxo(data, length);
   }
};

#if defined(__APPLE__)
void CTAxoInterfaceBase::setCallbacks(STATE_FUNC state, RECV_FUNC msgRec, NOTIFY_FUNC notify){//should call this from UI before init
    _messageStateReport = state;
    _receiveMessage = msgRec;
    _notifyCallback = notify;
}

void CTAxoInterfaceBase::setGroupCallbacks(GROUP_STATE_FUNC state, GROUP_MSG_RECV_FUNC msgRec, GROUP_CMD_RECV_FUNC cmd){
   _groupReceiveCommand = cmd;
   _groupReceiveMessage = msgRec;
   _groupStateCallback = state;
}
#endif

void CTAxoInterfaceBase::setSendCallback(SEND_DATA_FUNC p){//should call this from transport side
   _sendDataFuncAxo = p;
}

int CTAxoInterfaceBase::isDBFailCode(int v){return v==zina::DATABASE_ERROR;}

CTAxoInterfaceBase *CTAxoInterfaceBase::sharedInstance(const char *un){
   static CTAxoInterface *p = NULL;
    
#if defined(__APPLE__)
   static CTMutex _m;
   CTMutexAutoLock _a(_m);
#endif
   
   if(!p && !un){
      return NULL;
   }
    if(!p){

#if defined(__APPLE__)
        t_logf(log_events, "CTAxoInterfaceBase::sharedInstance", " INITIALIZED with un: %s",un);
#endif
        
        p = new CTAxoInterface(un);
    }
   return p;
}


