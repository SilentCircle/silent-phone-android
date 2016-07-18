/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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

#include "axolotl/crypto/AesCbc.h"
#include "axolotl/crypto/EcCurve.h"
#include "axolotl/Constants.h"
#include "axolotl/AxoPreKeyConnector.h"
#include "axolotl/state/AxoConversation.h"
#include "axolotl/ratchet/AxoRatchet.h"
#include "keymanagment/PreKeys.h"
#include "util/UUID.h"

//#include "../util/cJSON.h"
//#include "../util/b64helper.h"
//#include "../provisioning/Provisioning.h"
//#include "../provisioning/ScProvisioning.h"
#include "storage/sqlite/SQLiteStoreConv.h"

#include "../tiviengine/tivi_log.h"


#include "axolotl_glue.h"

using namespace axolotl;

static AppInterfaceImpl* axoAppInterface = NULL;
static Transport *sipTransport = NULL;

static STATE_FUNC _messageStateReport = NULL;
static RECV_FUNC _receiveMessage = NULL;
static SEND_DATA_FUNC _sendDataFuncAxo;

void t_setAxoTransport(Transport *transport){
   sipTransport = transport;
   //sipTransport->setSendDataFunction(sendDataFuncAxo);
}

#ifndef ANDROID_NDK
#include "../os/CTMutex.h"

const char *CTAxoInterfaceBase::getErrorMsg(int id){
   
   switch(id){
      case GENERIC_ERROR        : return "Generic error code, unspecified error";
      case VERSION_NO_SUPPORTED : return "Unsupported protocol version";
      case BUFFER_TOO_SMALL     : return "Buffer too small to store some data";
      case NOT_DECRYPTABLE      : return "Could not decrypt received message";
      case NO_OWN_ID            : return "Found no own identity for registration";
      case JS_FIELD_MISSING     : return "Missing a required JSON field";
      case NO_DEVS_FOUND        : return "No registered Axolotl devices found for a user";
      case NO_PRE_KEY_FOUND     : return "No more pre-keys for user's devices";
      case NO_SESSION_USER      : return "No session for this user found";
      case SESSION_NOT_INITED   : return "Session not initialized";
      case OLD_MESSAGE          : return "Message too old to decrypt";
      case CORRUPT_DATA         : return "Incoming data CORRUPT_DATA";
      case AXO_CONV_EXISTS      : return "Axolotl conversation exists while trying to setup new one";
      case MAC_CHECK_FAILED     : return "HMAC check of encrypted message failed";
      case MSG_PADDING_FAILED   : return "Incorrect padding of decrypted message";
      case SUP_PADDING_FAILED   : return "Incorrect padding of decrypted supplementary data";
      case NO_STAGED_KEYS       : return "Not a real error, just to report that no staged keys available";
      case RECEIVE_ID_WRONG     : return "Receiver's long term id key hash mismatch";
      case SENDER_ID_WRONG      : return "Sender''s long term id key hash mismatch";
      case RECV_DATA_LENGTH     : return "Expected length of data does not match received length";
      case WRONG_RECV_DEV_ID    : return "Expected device id does not match actual device id";
   }

   static char err[64];
   snprintf(err,sizeof(err)-10, "Unknow - code: %d", id);
   return err;
}
// Plain public API without a class
AppInterface* t_getAxoAppInterface() { return axoAppInterface; }

void *CTAxoInterfaceBase::getAxoAppInterface(){return axoAppInterface;}

static void sendDataFuncAxo(uint8_t* names[], uint8_t* devIds[], uint8_t* envelopes[], size_t sizes[], uint64_t msgIds[]){
   
   _sendDataFuncAxo(names, devIds, envelopes, sizes, msgIds);
}


/*
 * Receive message callback for AppInterfaceImpl.
 */
//UI
static int32_t receiveMessage(const std::string& messageDescriptor, const std::string& attachementDescriptor = std::string(), const std::string& messageAttributes = std::string())
{

   if(!_receiveMessage){
      printf("!receiveMessage=%s\n", messageDescriptor.c_str());
      return 0;
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

static int asyncRescanTh(void *p){
   
   string *str = (string*)p;
   axoAppInterface->rescanUserDevices(*str);
   
   //we detected a new device we have to resend last message
   //TODO resend last message to this user
   
   if(_messageStateReport){
      _messageStateReport(0, CTAxoInterfaceBase::eMustResendLastMessage, *str);
   }

   delete str;
   
   return 0;
}

static void notifyCallback(int32_t notifyAction, const string& actionInformation, const string& devId){
   
   if(notifyAction == AppInterface::DEVICE_SCAN){
      
      void startThX(int (cbFnc)(void *p),void *data);
      
      string *str = new string(actionInformation);
      startThX(asyncRescanTh, str);
   }
}


/*
 * Class:     AxolotlNative
 * Method:    httpHelper
 */
/*
 * HTTP request helper callback for provisioning etc.
 */
static int32_t httpHelper(const std::string& requestUri, const std::string& method, const std::string& requestData, std::string* response)
{

   char* t_send_http_json(const char *url, const char *meth,  char *bufResp, int iMaxLen, int &iRespContentLen, const char *pContent);
   
   int iSizeOfRet = 128 * 1024;
   char *retBuf = new char [iSizeOfRet];
   int iContentLen = 0;
   
   int code = 0;
   char *content = t_send_http_json (requestUri.c_str(), method.c_str(),
                                     retBuf ,iSizeOfRet - 1,
                                     iContentLen, requestData.c_str());
   
   if(content && iContentLen>0 && response)
      response->assign((const char*)content, iContentLen);
   
   delete retBuf;
    //puts(response->c_str());
   
   if(iContentLen<1)return -1;
   
   return 200;
}

static int _initAxolotl(const char *db, const char *pw, int pwLen, const char *name, const char *api_key, const char *devid){

   if(axoAppInterface)return 0;

   if (name == NULL)
      return -10;

   if (api_key == NULL)
      return -12;
   
   int nameLen = strlen(name);
   
   if (nameLen == 0)
      return -11;
   
   int authLen = strlen(api_key);
   
   if (authLen == 0)
      return -13;
   
   if (pw == NULL)
      return -14;
   if (pwLen != 32)
      return -15;
 //  void deleteFile(const char *fn);
 //  deleteFile(db);
   int isFileExists(const char *fn);
   int firstStart = !isFileExists(db);
   //ET SS (07/22/15) 89435b40e4f2ab41ace43cc46a4b1b6c
   axoAppInterface = new AppInterfaceImpl(std::string((const char*)name, nameLen),
                                          std::string((const char*)api_key, authLen),
                                          std::string(devid),
                                          receiveMessage, messageStateReport,notifyCallback);
   
   sipTransport = new SipTransport(axoAppInterface);
   
   sipTransport->setSendDataFunction(sendDataFuncAxo);
   
   axoAppInterface->setTransport(sipTransport);
   axoAppInterface->setHttpHelper(httpHelper);
   
   
   std::string dbPw((const char*)pw, pwLen);
   
   // initialize and open the persitent store singleton instance
   
   SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    
   store->setKey(dbPw);
   store->openStore(std::string (db));
   
  // store->resetStore();
    
   AxoConversation* ownAxoConv = AxoConversation::loadLocalConversation(name);
   if (ownAxoConv == NULL) {  // no yet available, create one. An own conversation has the same local and remote name, empty device id
      ownAxoConv = new AxoConversation(name, name, string());
      const DhKeyPair* idKeyPair = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);
      ownAxoConv->setDHIs(idKeyPair);
      ownAxoConv->storeConversation();
   }
   delete ownAxoConv;    // Not needed anymore here
   
   void tryRegisterAxolotl(int firstStart, int iForce);

   tryRegisterAxolotl(firstStart, 0);
   
   return 1;
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
         void exitShowApp(const char *msg);
         //TODO delete all app data and restart SP
         exitShowApp("Please, uninstall Silent Phone, and then install again.");
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
         axoAppInterface->registerAxolotlDevice(&info);
         t_logf(log_events, __FUNCTION__,"registerAxolotlDevice=[%s]",info.c_str());
       
         printf("registerAxolotlDevice=[%s]",info.c_str());

         rep = 0;
         
         verifyAxolotlResult(info);
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

static int t_initAxolotl(const char *myUsername)
{
   const char *getAPIKey(void);
   char * getFileStorePath(void);
   unsigned char *get32ByteAxoKey(void);
   const char *t_getDevID_md5(void);
   
   static char fn[1024];
    
    snprintf(&fn[0],sizeof(fn)-1,"%s/axo_%s_sql.db",getFileStorePath(), myUsername);
    
    int isFileExists(const char *fn);
    static int iOldApp = 0;
    if(iOldApp || isFileExists(fn)){
        iOldApp = 1;
        void exitShowApp(const char *msg);
        exitShowApp("Please, uninstall Silent Phone, and then install again.");
        return -1;
    }
   
   snprintf(&fn[0],sizeof(fn)-1,"%s/axo_%s_secure_sql.db",getFileStorePath(), myUsername);
   fn[sizeof(fn)-1]=0;
   
   return _initAxolotl(fn,(const char *)get32ByteAxoKey(), 32, myUsername,
                      getAPIKey(),
                     //  "abcd1234abcd1234abcd1234abcd1234");//
                       t_getDevID_md5());

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
   
   std::vector<int64_t>* msgIds = axoAppInterface->sendMessage(message, attachment, attributes);
   
   if (msgIds == NULL || msgIds->empty()) {
      delete msgIds;
      return 0;
   }
   
   
   return msgIds->at(0);
}


static int64_t t_sendJSONMessage( const char *msg, const char *attachment, const char *attributes){
   
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
}
#endif

int CTAxoInterfaceBase::isAxolotlReady(){return sipTransport ? 1 : 0;}

class CTAxoInterface:public CTAxoInterfaceBase{
   //TODO store transport and AppInterfaceImpl here
public:
   CTAxoInterface(const char *un){
#ifndef ANDROID_NDK
      t_initAxolotl(un);
#endif
   }
   
#ifndef ANDROID_NDK
   

   int64_t sendJSONMessage( const char *msg, const char *attachment, const char *attributes){
      return t_sendJSONMessage( msg, attachment, attributes);
   }
   
   int64_t sendMessage(const char *dstUsername, const char *msg){
      return t_sendAxoMessage(dstUsername, msg);
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

#ifndef ANDROID_NDK
void CTAxoInterfaceBase::setCallbacks(STATE_FUNC state, RECV_FUNC msgRec){//should call this from UI before init
   _messageStateReport = state;
   _receiveMessage = msgRec;
}


#endif

void CTAxoInterfaceBase::setSendCallback(SEND_DATA_FUNC p){//should call this from transport side
   _sendDataFuncAxo = p;
}

CTAxoInterfaceBase *CTAxoInterfaceBase::sharedInstance(const char *un){
   static CTAxoInterface *p = NULL;
#ifndef ANDROID_NDK
   static CTMutex _m;
   CTMutexAutoLock _a(_m);
#endif
   
   if(!p && !un){
      return NULL;
   }
   if(!p)p = new CTAxoInterface(un);
   return p;
}


