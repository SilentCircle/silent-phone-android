/*
Created by Janis Narbuts
Copyright © 2004-2012 Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include <android/log.h>
#include <jni.h>

#ifdef __linux__
#include <fcntl.h>
#include <sys/stat.h>
#endif

#include "engcb.h"

//ls -1 *.raw *.txt | ../tools/t_arcMac phone_res.h
#include "phone_res.h"


char * findImgData(char *fn,int &iSize, char *img_data, int iAllSize);
char *getResFile(const char *fn, int &iLen){
   iLen=0;
   __android_log_print(ANDROID_LOG_DEBUG,"tivi", "try find [%s] sz=%d", fn, sizeof(res_data));
   return findImgData((char*)fn, iLen, (char*)&res_data[0], sizeof(res_data));
}

const char *findFilePath(const char *fn){
   // NSString *ns=findFilePathNS(fn);
   // if(!ns)return NULL;
   return fn;//[ns UTF8String];
   
}

int t_setNextHWSampleRate(int iRate);
int t_getSampleRate();
void t_init_log();


int findCSC_C_S_java(const char *nr, char *ret, int iMaxLen);

const char* getProvNoCallBackResp();
int checkProvNoCallBack(const char *pUserCode);
int checkProvAPIKeyNoCallBack(const char *pApiKey);
int provClearAPIKey();
int isProvisioned(int iCheckNow);
const char *getAPIKey();

void onNewVideoData(int *d, unsigned char *yuv, int w, int h, int angle);
void setFileStorePath(const char *p);
char *getImeiBuf();
void setImei(char *p);

void setPWDKey(unsigned char *k, int iLen);

int getSetCfgVal(int iGet, char *k, int iKeyLen, char *v, int iMaxVLen);
int getVidFrame(int prevf, int *i,int *sxy);

void debug_log(char *p){}
int getEngIDByPtr(void *p);

int t_isEqual(const char *src, const char *dst, int iLen);
int fixNR(const char *in, char *out, int iLenMax);


int getReqTimeToLive();

int z_main_init(int argc, const  char* argv[]);
char* z_main(int iResp, int argc, const char* argv[]);
const char* sendEngMsg(void *pEng, const char *p);

int getCallInfo(int iCallID, const char *key, char *p, int iMax);

void *getAccountByID(int id);

int getPhoneState();

int checkIPNow();
void convertNV21toRGB(unsigned char *b, int w, int h, int *idata, unsigned short *sdata);


//---------------------------------------------------------

static JavaVM *g_JavaVM = NULL;
char bufPeerName[128];
static jobject thisTiviPhoneService=NULL;



static int iIs3G=0;
static int iAPILevel=5;
static int iDebugable=0;

JavaVM *t_getJavaVM(){
   return g_JavaVM;
}

int g_is3G(){return iIs3G;}

int getAPILevel(){
   return iAPILevel;
}

void debugsi(char *c, int a){}


int isBackgroundReadable(const char *fn){}
void setFileAttributes(const char *fn, int iProtect){}
void log_file_protection_prop(const char *fn){}


void *initARPool(){
   return (void*)1;
}
void relARPool(void *p){}


void initCC(){
   static int iInitOk=0;
   if(iInitOk)return;
   iInitOk=1;
   
   
   int iLen=0;
   char *p = getResFile("Country.txt", iLen);
   if(!p)return;
   
   void initCC(char *p, int iLen);
   initCC(p ,iLen);
   
}





int showSSLErrorMsg(void *ret, const char *p){
   __android_log_print(ANDROID_LOG_DEBUG,"tivi", "tls err --exiting %s",p);
   exit(1);
   return 0;
}


void tmp_log(const char *p){
   if(iDebugable)
      __android_log_print(ANDROID_LOG_DEBUG,"tivi", p);
}

//void log_audio(char const*, char const*){}
//void log_zrtp(char const*, char const*){}
void tivi_log_tag(const char *tag, const char *val){
   if(iDebugable)
      __android_log_print(ANDROID_LOG_DEBUG, tag, val);
}

void tivi_log1(const char *p, int val){
   if(iDebugable)
      __android_log_print(ANDROID_LOG_DEBUG,"tivi", "%s=%d", p, val);
   //printf("%s=%d", p, val);
}

// void log_zrtp(char const *tag, char const *msg){
//   if(iDebugable)
//      __android_log_print(ANDROID_LOG_DEBUG,"tivi", "[%s]%s", tag, msg);
//}
//void log_audio(char const *tag, char const *msg){
//   log_zrtp(tag, msg);
//}


static char devID[128] = "";

const char *t_getDevID(int &l){
   const char *p=&devID[0];
   l=strlen(p);
   return p;//"12345678901234567890aa12345678901234567890aa12345678901234567890aa";
}



const char *t_getDevID_md5(){
   static char buf[64]="";
   
   if(!buf[0]){
      int l=0;
      
      
      int calcMD5(unsigned char *p, int iLen, char *out);
      l=calcMD5((unsigned char *)devID, strlen(devID), &buf[0]);
      buf[l]=0;
   }
   
   return buf;
}

const char *t_getDev_name(){
   return "Android";
}

float cpu_usage()
{
   return 0.1;
}


const char *createZeroTerminated(char *out, int iMaxOutSize, const char *in, int iInLen){
   const char *p=in;
   if(iInLen<0)return "";
   if(p && p[iInLen]){//non zero terminated string
      if(iInLen>=iMaxOutSize)iInLen=iMaxOutSize;
      strncpy(out,p,iInLen);
      out[iInLen]=0;
      p=&out[0];
   }
   return p;
}
/*
 
 int t_AttachCurrentThread(){
 JavaVM *vm;
 int iAttached;
 JNIEnv *env;
 env = NULL;
 iAttached=0;
 vm = t_getJavaVM();
 if(!vm)return -1;
 
 int s=vm->GetEnv((void**)&env,JNI_VERSION_1_4);
 if(s!=JNI_OK){
 s=vm->AttachCurrentThread(&env, NULL);
 if(!env || s<0){env=NULL;return -1; }
 iAttached=1;
 }
 return 1;
 }
 */

class CTJNIEnv{
   JavaVM *vm;
   JNIEnv *env;
   
   int iAttached;
public:
   CTJNIEnv(){
      env = NULL;
      iAttached=0;
      vm = t_getJavaVM();
      if(!vm)return;
      
      int s=vm->GetEnv((void**)&env,JNI_VERSION_1_4);
      if(s!=JNI_OK){
         s=vm->AttachCurrentThread(&env, NULL);
         if(!env || s<0){env=NULL;return; }
         iAttached=1;
      }
   }
   
   ~CTJNIEnv(){
      if(iAttached && vm)vm->DetachCurrentThread();
   }
   
   JNIEnv *getEnv(){
      return env;
   }
};


void wakeCallback(int iLock){
   
   CTJNIEnv jni;
   JNIEnv *env=jni.getEnv();
   if(!env)return;
   
   jclass tps = env->GetObjectClass(thisTiviPhoneService);//env->FindClass("com/tivi/tiviphone/TiviPhoneService");
   if(tps){
      jmethodID midCallBack = env->GetMethodID( tps, "wakeCallback", "(I)V");
      if(midCallBack){
         env->CallVoidMethod(thisTiviPhoneService, midCallBack, iLock);
      }
      else{
         tmp_log("wakeCallback fail");
      }
   }
   else{
      tmp_log("FindClass(TiviPhoneService) fail");
   }
}

int fncCBRet(void *ret, void *ph, int iCallID, int msgid, const char *psz, int iSZLen){
   
   if(!thisTiviPhoneService)return 0;
   
   CTJNIEnv jni;
   JNIEnv *env=jni.getEnv();
   if(!env)return 0;
   if(iSZLen<0)iSZLen=0;
   
   if(psz && !iSZLen)iSZLen=strlen(psz);
   
   
   if(msgid==CT_cb_msg::eIncomCall || msgid==CT_cb_msg::eCalling){//depricated
#define min(_A,_B) ((_A)< (_B) ? (_A) : (_B))
      strncpy(bufPeerName,psz,min(sizeof(bufPeerName),iSZLen));
      bufPeerName[sizeof(bufPeerName)-1]=0;
   }
   
   
   jclass tps = env->GetObjectClass(thisTiviPhoneService);//env->FindClass("com/tivi/tiviphone/TiviPhoneService");
   if(tps){
      jmethodID midCallBack = env->GetMethodID( tps, "callback", "(IIILjava/lang/String;)V");
      if(midCallBack){
         
         char bufTmp[256];
         const char *p = createZeroTerminated(&bufTmp[0], sizeof(bufTmp)-1, psz, iSZLen);
         
         int iEngID = getEngIDByPtr(ph);
         
         env->CallVoidMethod(thisTiviPhoneService, midCallBack, iEngID, iCallID, msgid, p ? env->NewStringUTF(p):NULL);
      }
      else{
         tmp_log("midCallBack fail");
      }
   }
   else{
      tmp_log("FindClass(TiviPhoneService) fail");
   }
   
   return 0;
}

extern "C" {
   
   JNIEXPORT int JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_getVFrame( JNIEnv* env,jobject thiz,jint iPrevID, jintArray intArray, jintArray sxyArr){
      
      jint *idata = env->GetIntArrayElements(intArray, 0);
      jint *sxy = env->GetIntArrayElements(sxyArr, 0);
      
      int r=getVidFrame(iPrevID, idata,sxy);
      
      env->ReleaseIntArrayElements(sxyArr, sxy, 0);
      env->ReleaseIntArrayElements(intArray, idata, 0);
      return r;
   }
   
   
   /*
    //TODO test
    JNIEXPORT int JNICALL AndroidSurface
    Java_com_silentcircle_silentphone_TiviPhoneService_doSurf( JNIEnv* env,
    jobject thiz, jobject jsurface, int iMeth, int w, int h){
    
    void *p;
    switch(iMeth){
    case 0:return AndroidSurface_register(env,  jsurface);
    case 1:return AndroidSurface_getPixels(w,h,&p);
    case 2:return AndroidSurface_updateSurface();
    case 3:return AndroidSurface_unregister();
    }
    
    return 1;
    }
    */
   JNIEXPORT void JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_nv21ToRGB32( JNIEnv* env,
                                                                  jobject thiz, jbyteArray byteArray, jintArray intArray, jshortArray shortArray , jint w, jint h, jint angle){
      //TODO check send FPS and skip some frames
      //int willSendThisFrame(); if(!willSendThisFrame())return;
      unsigned char *b = (unsigned char *)env->GetByteArrayElements(byteArray, 0);
      jint *idata = intArray?env->GetIntArrayElements(intArray, 0):NULL;
      unsigned short *sdata = shortArray?(unsigned short *)env->GetShortArrayElements(shortArray, 0):NULL;
      
      convertNV21toRGB(b, w, h, idata, sdata);
      
      if(idata)onNewVideoData((int*)idata,(unsigned char *)b,w,h,angle);
      
      if(shortArray)env->ReleaseShortArrayElements(shortArray, (jshort*)sdata, 0);
      if(intArray)env->ReleaseIntArrayElements(intArray, idata, 0);
      env->ReleaseByteArrayElements(byteArray, (jbyte*)b, 0);
      
      return ;
   }
   
   JNIEXPORT jint JNICALL Java_com_silentcircle_silentphone_TiviPhoneService_getSetCfgVal( JNIEnv* env,
                                                                                          jobject thiz, int iGet, jbyteArray byteKey, int iKeyLen, jbyteArray byteValue){
      
      char *k =  (char *)env->GetByteArrayElements(byteKey, 0);
      char *v =  (char *)env->GetByteArrayElements(byteValue, 0);
      int maxLen = (int)env->GetArrayLength(byteValue);
      int l=iKeyLen;//env->GetArrayLength(byteKey);
      
      int r=getSetCfgVal(iGet,k,l,v,maxLen);
      
      env->ReleaseByteArrayElements(byteKey, (jbyte*)k, 0);
      env->ReleaseByteArrayElements(byteValue, (jbyte*)v, 0);
      return r;
   }
   
   JNIEXPORT jint JNICALL Java_com_silentcircle_silentphone_TiviPhoneService_getPhoneState( JNIEnv* env,
                                                                                           jobject thiz){
      return getPhoneState();
   }
   
   
   
   
   /*
    Q
    I've got a method:
    
    public native void doSomething(ByteBuffer in, ByteBuffer out);
    Generated by javah C/C++ header of this method is:
    
    JNIEXPORT void JNICALL Java__MyClass_doSomething (JNIEnv *, jobject, jobject, jobject, jint, jint)
    
    A
    Assuming you allocated the ByteBuffer using ByteBuffer.allocateDirect() Use GetDirectByteBufferAddress
    
    jbyte* bbuf_in;  jbyte* bbuf_out;
    
    bbuf_in = (*env)->GetDirectBufferAddress(env, buf1);
    bbuf_out= (*env)->GetDirectBufferAddress(env, buf2);
    */
   JNIEXPORT int JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_saveImei( JNIEnv* env,
                                                               jobject thiz, jstring str)
   {
      
      const char *b = (const char *)env->GetStringUTFChars(str, 0);
      
      strncpy(devID, b, sizeof(devID)-1);
      devID[sizeof(devID)-1]=0;
      
      setImei((char*)b);
      env->ReleaseStringUTFChars(str, b);
      
      return 0;
   }
   
   
   JNIEXPORT int JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_checkNetState( JNIEnv* env,
                                                                    jobject thiz, jint iIsWifi, jint IP, jint iIsIPValid)
   {
      iIs3G=!iIsWifi;
      checkIPNow();
      return 0;
   }
   
   
   
   JNIEXPORT int JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_savePath( JNIEnv* env,
                                                               jobject thiz, jstring str)
   {
      
      const char *b = (const char *)env->GetStringUTFChars(str, 0);
      setFileStorePath(b);
      env->ReleaseStringUTFChars(str, b);
      return 0;
   }
   
   
   JNIEXPORT int JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_doInit( JNIEnv* env, jobject thiz, jint iDebugFlag){
      
      iDebugable = iDebugFlag;
      
      if(!thisTiviPhoneService)
         thisTiviPhoneService = env->NewGlobalRef( thiz);
      
      return 0;
   }
   
   JNIEXPORT int JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_doCmd( JNIEnv* env,
                                                            jobject thiz, jstring command)
   {
      const char *b = (const char *)env->GetStringUTFChars(command, 0);
      
      if(iDebugable)
         __android_log_print(ANDROID_LOG_DEBUG,"tivi", "cmd [%s]", b);
      
      int ret=0;
      
#define T_SET_LEVEL "g.setLevel "
      if(strcmp(b,"getint.ReqTimeToLive")==0){
         
         ret = getReqTimeToLive();
      }
      else if(t_isEqual(b,T_SET_LEVEL, sizeof(T_SET_LEVEL)-1)){
         iAPILevel=atoi(b+sizeof(T_SET_LEVEL)-1);
      }
      else if(strcmp(b, "isProv")==0){
         ret = isProvisioned(1);
      }
#define T_SET_SAMPLERATE "set.samplerate="
      else if(t_isEqual(b, T_SET_SAMPLERATE, sizeof(T_SET_SAMPLERATE)-1 )){
         ret=t_setNextHWSampleRate(atoi(b+sizeof(T_SET_SAMPLERATE)-1));
      }
      
#define T_PROV_START_API_STR "prov.start.apikey="
      else if(t_isEqual(b, T_PROV_START_API_STR, sizeof(T_PROV_START_API_STR)-1 )){
         ret=checkProvAPIKeyNoCallBack(b+sizeof(T_PROV_START_API_STR)-1);
      }
#define T_PROV_CLEAR_API_STR "prov.clear.apikey"
      else if(t_isEqual(b, T_PROV_START_API_STR, sizeof(T_PROV_CLEAR_API_STR)-1 )){
         ret=provClearAPIKey();
      }
      
#define T_PROV_START_STR "prov.start="
      else if(t_isEqual(b, T_PROV_START_STR, sizeof(T_PROV_START_STR)-1 )){
         ret=checkProvNoCallBack(b+sizeof(T_PROV_START_STR)-1);
      }
      else{
         
         
         const char* argv[2];//={""}
         argv[0]="";
         argv[1]=b;
         
         z_main(0,2, argv);
      }
      env->ReleaseStringUTFChars(command, b);
      return ret;
      
   }
   
   JNIEXPORT jstring JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_getInfo( JNIEnv* env,
                                                              jobject thiz, jint iEngineID, jint iCallID, jstring command )
   {
      const char *b = (const char *)env->GetStringUTFChars(command, 0);
      char *p=NULL;
      char ret[256]="";
      
      if(iCallID!=0 && iCallID!=-1){
         int r=getCallInfo(iCallID, b,  ret, sizeof(ret)-1);
         if(r<=0)r=0;
         p=&ret[0];
         ret[r]=0;
      }
      else{
         if(iEngineID==-1){
            if(iCallID==-1  && t_isEqual(b, "get.samplerate", 14)){
               
               snprintf(ret,sizeof(ret),"%d",t_getSampleRate());
               p=&ret[0];
            }
            else if(iCallID==-1 &&  t_isEqual(b, "format.nr=", 10)){
               
               initCC();
               fixNR((const char *)b+10, &ret[0], sizeof(ret)-1);
               p=&ret[0];
            }
            else if(iCallID==-1 &&  t_isEqual(b,"get.flag=",9)){
               
               initCC();
               
               p=&ret[0];
               
               
               if(!findCSC_C_S_java(b+9, ret, sizeof(ret)-1))
                  p=NULL;
            }
            else if(iCallID==-1 && t_isEqual(b,"prov.getAPIKey",14)){
               
               p=(char *)getAPIKey();
            }
            
#define T_PROV_TEST_STR "prov.tryGetResult"
            
            else if(iCallID==-1 && t_isEqual(b, T_PROV_TEST_STR, sizeof(T_PROV_TEST_STR)-1 )){
               
               p=(char*)getProvNoCallBackResp();
               
            }
            else{
               
               p=(char*)sendEngMsg(NULL, b);
            }
         }
         else{
            void *pEng=getAccountByID(iEngineID);
            if(!pEng)
               pEng=getAccountByID(iEngineID?0:1);
            
            p=(char*)sendEngMsg(pEng, b);
         }
      }
      env->ReleaseStringUTFChars(command, b);
      if(!p)return NULL;
      return env->NewStringUTF( p );
      
   }
   
   JNIEXPORT jstring JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_doX( JNIEnv* env,
                                                          jobject thiz, int z, jstring command )//depricated, use getInfo
   {
      
      const char* argv[8];//={""}
      argv[0]="";
      
      char *p=NULL;
      int iArgs=2;
      
      if(z==10){
         unsigned char *b = (unsigned char *)env->GetStringUTFChars(command, 0);
         argv[0]="";
         argv[1]=(const char*)b;
         
         p=z_main(1,iArgs, argv);
         
         env->ReleaseStringUTFChars(command, (char*)b);
         return env->NewStringUTF( p);
         
      }
      else{
         
         switch(z){
            case 9:argv[1]=".about";break;
            case 8:argv[1]=".lastErrMsg";break;
            case 7:argv[1]=".ao.jitter";break;
            case 6:return env->NewStringUTF(bufPeerName);//argv[1]=".caller";break;
            case 5:argv[1]=":unreg";break;
            case 4:argv[1]=":reg";break;
            case 3:argv[1]=":e";break;
            case 2:argv[1]=":a";break;
            default: iArgs=0;
         }
      }
      
      if(!p)p=z_main(1,iArgs, argv);
      return env->NewStringUTF( p);
      
   }
   JNIEXPORT int JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_setKeyData( JNIEnv* env,
                                                                 jobject thiz, jbyteArray byteArray)
   {
      
      unsigned char *k = (unsigned char *)env->GetByteArrayElements(byteArray, 0);
      int iLen = env->GetArrayLength(byteArray);
      tivi_log1("set key  with len", iLen);
      setPWDKey(k, iLen);
      env->ReleaseByteArrayElements(byteArray, (jbyte*)k, 0);
      return 0;
   }
   
   
   JNIEXPORT jint JNICALL
   Java_com_silentcircle_silentphone_TiviPhoneService_initPhone( JNIEnv* env,  jobject thiz){
      
      static int iFirst=1;
      
      if(iFirst){
         iFirst=0;
         z_main_init(0, NULL);
         t_init_log();
         
         setPhoneCB(&fncCBRet,(void*)"a");
      }
      return 0;
   }
   
   jint JNI_OnLoad(JavaVM* vm, void* reserved)
   {
      g_JavaVM = vm;
      __android_log_write(ANDROID_LOG_VERBOSE,"tivi","JNI_OnLoad called");
      umask((1<<6) | (7<<3) | (7<<0));
      return JNI_VERSION_1_6;
   }
   
}





