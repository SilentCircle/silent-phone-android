/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2016, Silent Circle, LLC.  All rights reserved.

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
// Modified by Werner Dittmann

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>


#include <zrtp/libzrtpcpp/ZIDCache.h>
#include <common/icuUtf.h>

#include <android/log.h>
#include <polarssl/debug.h>

#include "com_silentcircle_silentphone2_services_PhoneServiceNative.h"

#ifdef __linux__
#include <fcntl.h>
#include <sys/stat.h>
#endif

#include "engcb.h"

//ls -1 *.raw *.txt | ../tools/t_arcMac phone_res.h
#include "phone_res.h"

#define LOG_ANDROID
#ifdef LOG_ANDROID
#define LOG(deb)   deb
#else
#define LOG(deb)
#endif

/**
 * Define -DPACKAGE_NAME="Java_some_package_name" to define another package 
 * name during compilation
 */
#ifndef PACKAGE_NAME
#define PACKAGE_NAME Java_com_silentcircle_silentphone2_services_PhoneServiceNative_
#endif

#define CONCATx(a,b) a##b
#define CONCAT(a,b) CONCATx(a,b)

#define JNI_FUNCTION(FUNC_NAME)  CONCAT(PACKAGE_NAME, FUNC_NAME)

/*
 * Declare external methods
 */
int calcMD5(unsigned char *p, int iLen, char *out);
int checkProvNoCallBack(const char *pUserCode);
int checkProvAPIKeyNoCallBack(const char *pApiKey);
int checkIPNow();
void convertNV21toRGB(unsigned char *b, int w, int h, int *idata, unsigned short *sdata);

int fixNR(const char *in, char *out, int iLenMax);
int findCSC_C_S_java(const char *nr, char *ret, int iMaxLen);
char *findImgData(char *fn,int &iSize, char *img_data, int iAllSize);

char *getImeiBuf();
int getSetCfgVal(int iGet, char *k, int iKeyLen, char *v, int iMaxVLen);
int getVidFrame(int prevf, int *i,int *sxy);
int getEngIDByPtr(void *p);
int getReqTimeToLive();
int getCallInfo(int iCallID, const char *key, char *p, int iMax);
void *getAccountByID(int id);
int getPhoneState();
const char *getAPIKey();
const char* getProvNoCallBackResp();

int isProvisioned(int iCheckNow);
void initCC(char *p, int iLen);

void onNewVideoData(int *d, unsigned char *yuv, int w, int h, int angle);

int provClearAPIKey();

void setFileStorePath(const char *p);
void setImei(char *p);
void setPWDKey(unsigned char *k, int iLen);

void setProvisioningToDevelop();
void setProvisioningToProduction();

void setSipToDevelop();
void setSipToProduction();

const char* sendEngMsg(void *pEng, const char *p);

int t_isEqual(const char *src, const char *dst, int iLen);
int t_setNextHWSampleRate(int iRate);
int t_getSampleRate();
void t_init_log();

void initAudioClassHelper(JNIEnv *env, const char* path, bool record);

int z_main_init(int argc, const  char* argv[]);
char* z_main(int iResp, int argc, const char* argv[]);

int getNumberOfCountersZrtp(int iCallID);            // Implemented in tiviengine/CSessions
int getCountersZrtp(int iCallID, int* counters);     // Implemented in tiviengine/CSessions

/*
 * Empty functions, used in other parts, to avoid undefined references
 */
void debugsi(char *c, int a){}

int isBackgroundReadable(const char *fn){}
void setFileAttributes(const char *fn, int iProtect){}
void log_file_protection_prop(const char *fn){}
void relARPool(void *p){}


char *getResFile(const char *fn, int &iLen) {
    iLen = 0;
    LOG(__android_log_print(ANDROID_LOG_DEBUG, "tivi", "try find [%s] sz = %d", fn, sizeof(res_data));)
    return findImgData((char*)fn, iLen, (char*)res_data, sizeof(res_data));
}

const char *findFilePath(const char *fn){
    return fn;
}

//---------------------------------------------------------

static JavaVM *g_JavaVM = NULL;

static jobject thisTiviPhoneService = NULL;
static jmethodID wakupCallBackMethod = NULL;
static jmethodID stateCallBackMethod = NULL;


static int iIs3G = 0;
static int iAPILevel = 5;
static int iDebugable = 0;

static int iFirst = 1;

static int gainReduction = 0;     // shift the audio data (play) by this number of places

// preferred language, used in HTTP Accept-language
static char g_prefLang[16] = "en";

// Version name of SPA
static char g_versionName[16] = {'\0'};

JavaVM *t_getJavaVM() {
    return g_JavaVM;
}

int g_is3G() {
    return iIs3G;
}

int getAPILevel() {
    return iAPILevel;
}

void *initARPool() {
    return (void*)1;
}

int getGainReduction() {
    return gainReduction;
}

const char *getPrefLang() {
    return g_prefLang;
}

const char *getVersionName() {
    return g_versionName;
}

void initCC() {
    static int iInitOk = 0;
    if (iInitOk)
        return;
    iInitOk = 1;

    int iLen = 0;

    char *p = getResFile("Country.txt", iLen);
    if (!p)
        return;

    initCC(p ,iLen);
}

int showSSLErrorMsg(void *ret, const char *p){
   LOG(__android_log_print(ANDROID_LOG_DEBUG,"tivi", "tls err --exiting %s",p);)
   exit(1);
   return 0;
}

int androidLog(char const *format, va_list arg) {
    LOG(if (iDebugable)__android_log_vprint(ANDROID_LOG_DEBUG, "native", format, arg);)
}

void androidLog(const char* format, ...)
{
    va_list arg;
    va_start(arg, format);
    androidLog(format, arg);
    va_end( arg );
}

void tmp_log(const char *p) {
    androidLog("%s", p);
}

void tivi_log_tag(const char *tag, const char *val){
    LOG(if (iDebugable) __android_log_print(ANDROID_LOG_DEBUG, tag, "%s", val);)
}

void tivi_log1(const char *p, int val){
    androidLog("%s = %d", p, val);
}


static char devID[128] = "";
static char devIDmd5[64]="";

const char *t_getDevID(int &l) {
    l = strlen(devID);
    return devID;                  //"12345678901234567890aa12345678901234567890aa12345678901234567890aa";
}

const char *t_getDevID_md5() {
    return devIDmd5;
}

const char *t_getDev_name(){
    return "Android";
}

float cpu_usage()
{
    return 0.1;
}

#ifdef WITH_AXOLOTL
extern void loadAxolotl();

void axoLoad()
{
    androidLog("Dummy function to force linking of Axolotl static lib\n");
    loadAxolotl();
}
#endif
//TODO make the same for iOS and Android
static char push_token[256]={0};
const char *push_fn = "push-token.txt";
char * getFileStorePath(void);
char *loadFile(const  char *fn, int &iLen);
void saveFile(const char *fn,void *p, int iLen);

void setPushToken(const char *p){
   int l = snprintf(push_token, sizeof(push_token),"%s",p);
   char fn[2048];
   snprintf(fn,sizeof(fn)-1, "%s/%s", getFileStorePath(),push_fn);
   
   saveFile(fn, (void*)p, l);
   
   void *getAccountByID(int id);
   void * ph = getAccountByID(0);
   if(!ph)return;
   
   const char* sendEngMsg(void *pEng, const char *p);
   
   const char*res =  sendEngMsg(NULL, "all_online");
   if(res && strcmp(res,"true")==0){
      sendEngMsg(NULL,":rereg");
   }

}


const char *getPushToken(){
   if(push_token[0]) return &push_token[0];
   static int iTestet=0;
   if(!iTestet){
      iTestet=1;
      char fn[2048];
      snprintf(fn,sizeof(fn)-1, "%s/%s", getFileStorePath(), push_fn);
      
      int l=0;
      char *p = loadFile(fn, l);
      if(p && l>0){
         snprintf(push_token, sizeof(push_token),"%s",p);
         delete p;
      }
      
   }
   
   return &push_token[0];
}


const char *getAppID(){
   static char appid[256]={0};
   if(appid[0])return appid;
   
   
   const char *p = "put.correct.app_name.here";
   
#if defined(DEBUG)
   snprintf(appid, sizeof(appid), "%s--DEV", p);
#else
   snprintf(appid, sizeof(appid), "%s", p);
#endif
   
   
   return appid;
   
}

const char *createZeroTerminated(char *out, int iMaxOutSize, const char *in, int iInLen){
    const char *p = in;

    if (iInLen < 0)
        return "";

    if (p && p[iInLen]) {                            // non zero terminated string
        if (iInLen >= iMaxOutSize)
            iInLen = iMaxOutSize;
        strncpy(out, p, iInLen);
        out[iInLen] = 0;
        p = out;
    }
    return p;
}


/**
 * Local helper class to keep track of thread attach / thread detach
 */
class CTJNIEnv {
    JavaVM *vm;
    JNIEnv *env;

    int iAttached;
public:
    CTJNIEnv() {
        env = NULL;
        iAttached = 0;

        vm = t_getJavaVM();
        if (!vm)
            return;

        int s = vm->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (s != JNI_OK){
            s = vm->AttachCurrentThread(&env, NULL);
            if (!env || s < 0) {
                env = NULL;
                return;
            }
            iAttached = 1;
        }
    }

    ~CTJNIEnv() {
        if (iAttached && vm)
            vm->DetachCurrentThread();
    }

    JNIEnv *getEnv() {
        return env;
    }
};

/*
 * The Tivi C and C++ code uses the following functions to call back to Java code.
 * The Java code *must* call the initialization method 'doInit' beforehand. The
 * Java code *must not* declare the doInit method as 'static' because the 'doInit'
 * method use the 'thiz' object pointer and stores it.
 */
void wakeCallback(int iLock) {

    if (!thisTiviPhoneService)
        return;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return;

    env->CallVoidMethod(thisTiviPhoneService, wakupCallBackMethod, iLock);
}

static jbyteArray stringToArray(JNIEnv* env, const uint8_t* input, size_t length)
{
    if (length == 0 || input == NULL)
        return NULL;

    jbyteArray data = env->NewByteArray(static_cast<jsize>(length));
    if (data == NULL)
        return NULL;
    env->SetByteArrayRegion(data, 0, static_cast<jsize>(length), (jbyte*)input);
    return data;
}

static jobject sipNotifyHandler = NULL;
static jmethodID onGenericSipNotify = NULL;

// Create and store (with global reference) a SipNotifyHandler Java object,
// Create a method id to 'onGenericSipNotify(byte[] content, byte[] event, byte[] contentType)'
// in SipNotifyHandler
//
void initSipNotifyClassHelper(JNIEnv *env, const char* path)
{
    // Get the SipNotifyHandler class
    jclass cls = env->FindClass(path);
    if (!cls) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "initSipNotifyClassHelper: failed to get %s class reference", path);
        return;
    }
    jmethodID constr = env->GetMethodID(cls, "<init>", "()V");
    if (!constr) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "initSipNotifyClassHelper: failed to get %s empty constructor", path);
        return;
    }
    jobject obj = env->NewObject(cls, constr);
    if (!obj) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "initSipNotifyClassHelper: failed to create a %s object", path);
        return;
    }
    sipNotifyHandler = env->NewGlobalRef(obj);
    onGenericSipNotify = env->GetMethodID(cls, "onGenericSipNotify", "([B[B[B)V");
    if (onGenericSipNotify == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "initSipNotifyClassHelper: failed to get onGenericSipNotify method id");

    }
}

/**
 * @brief Callback for SIP stack if it receives a SIP NOTIFY event
 *
 * This function checks the parameters and prepares the data for a callback
 * to a Java functions that handles the call. The Java function should be
 * as short as possible to avoid blocking of the SIP thread.
 *
 * @param content The content of the NOTIFY packet or @c NULL if no content
 *                is available. The content data should be UNICODE
 * @param contentLength the length of the content data in bytes, not UNICODE
 *                      characters
 * @param event The event type of the NOTIFY packet or @c NULL if no event type
 *              is available. The event data should be ASCII
 * @param eventLength the length of the event data in bytes
 * @param contentType The content type of the NOTIFY packet or @c NULL if no
 *                    content type is available. The content data should be ASCII
 * @param typeLength the length of the conten type data in bytes
 */
void notifyGeneric(const uint8_t* content, size_t contentLength,
                   const uint8_t* event, size_t eventLength,
                   const uint8_t* contentType, size_t typeLength)
{
    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return;

    jbyteArray jContent = stringToArray(env, content, contentLength);
    jbyteArray jEvent = stringToArray(env, event, eventLength);
    jbyteArray jContentType = stringToArray(env, contentType, typeLength);

    env->CallVoidMethod(sipNotifyHandler, onGenericSipNotify, jContent, jEvent, jContentType);

    if (jContent != NULL)
        env->DeleteLocalRef(jContent);
    if (jEvent != NULL)
        env->DeleteLocalRef(jEvent);
    if (jContentType != NULL)
        env->DeleteLocalRef(jContentType);
}


#define min(_A,_B) ((_A) < (_B) ? (_A) : (_B))

static int fncCBRet(void *ret, void *ph, int iCallID, int msgid, const char *psz, int iSZLen) {

    if (!thisTiviPhoneService)
        return 0;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();

    if (!env)
        return 0;
    if (iSZLen < 0)
        iSZLen = 0;

    if(psz && !iSZLen)
        iSZLen = strlen(psz);

    char bufTmp[256];
    const char *p = createZeroTerminated(bufTmp, sizeof(bufTmp)-1, psz, iSZLen);
    
    jstring msg;
    if (p) {
        UChar tmpUtf16[256];
        UChar* utf16;
        UErrorCode error = U_ZERO_ERROR;
        int32_t length = 0;
        utf16 = u_strFromUTF8(tmpUtf16, 256, &length, p, strlen(p), &error);
        androidLog("UTF16 length: %d, error: %d", length, error);
        
        msg = (error == U_ZERO_ERROR) ? env->NewString(tmpUtf16, length) : NULL;
    }
    else
        msg = NULL;
    
    int iEngID = getEngIDByPtr(ph);

    env->CallVoidMethod(thisTiviPhoneService, stateCallBackMethod, iEngID, iCallID, msgid, msg);
    if (msg != NULL)
        env->DeleteLocalRef(msg);

    return 0;
}

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
JNI_FUNCTION(getVFrame)(JNIEnv* env, jclass thiz, jint iPrevID, jintArray intArray, jintArray sxyArr) 
{
    jint *idata = intArray ? env->GetIntArrayElements(intArray, 0) : NULL;
    jint *sxy = env->GetIntArrayElements(sxyArr, 0);

    int r = getVidFrame(iPrevID, idata, sxy);

    env->ReleaseIntArrayElements(sxyArr, sxy, 0);
    if (intArray)
        env->ReleaseIntArrayElements(intArray, idata, 0);
    return r;
}


JNIEXPORT void JNICALL
JNI_FUNCTION(nv21ToRGB32)(JNIEnv* env, jclass thiz, jbyteArray byteArray, jintArray intArray, jshortArray shortArray,
                          jint w, jint h, jint angle) 
{
    //TODO check send FPS and skip some frames
    //int willSendThisFrame(); if(!willSendThisFrame())return;
    unsigned char *b = byteArray ? (unsigned char *)env->GetByteArrayElements(byteArray, 0) : NULL;
    jint *idata = intArray ? env->GetIntArrayElements(intArray, 0) : NULL;
    unsigned short *sdata = shortArray ? (unsigned short *)env->GetShortArrayElements(shortArray, 0) : NULL;

    if (b != NULL)
        convertNV21toRGB(b, w, h, idata, sdata);

    if (idata)
        // Actually the function does not use 'b' aka yuv buffer
        onNewVideoData((int*)idata, (unsigned char *)b, w, h, angle);

    if (shortArray)
        env->ReleaseShortArrayElements(shortArray, (jshort*)sdata, 0);
    if (intArray)
        env->ReleaseIntArrayElements(intArray, idata, 0);
    if (byteArray != NULL)
        env->ReleaseByteArrayElements(byteArray, (jbyte*)b, 0);

    return;
}

JNIEXPORT jint JNICALL 
JNI_FUNCTION(getSetCfgVal)(JNIEnv* env, jclass thiz, int iGet, jbyteArray byteKey, int iKeyLen, jbyteArray byteValue)
{
    char *k = (char *)env->GetByteArrayElements(byteKey, 0);
    char *v = (char *)env->GetByteArrayElements(byteValue, 0);
    int maxLen = (int)env->GetArrayLength(byteValue);
    int l = iKeyLen; //env->GetArrayLength(byteKey);

    int r=getSetCfgVal(iGet,k,l,v,maxLen);

    env->ReleaseByteArrayElements(byteKey, (jbyte*)k, 0);
    env->ReleaseByteArrayElements(byteValue, (jbyte*)v, 0);
    return r;
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(getPhoneState)(JNIEnv* env, jclass thiz) 
{
    return getPhoneState();
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(saveImei)(JNIEnv* env, jclass thiz, jstring str)
{

    const char *b = (const char *)env->GetStringUTFChars(str, 0);

    strncpy(devID, b, sizeof(devID)-1);
    devID[sizeof(devID)-1] = 0;

    int l = 0;
    l = calcMD5((unsigned char *)devID, strlen(devID), devIDmd5);
    devIDmd5[l] = 0;

    setImei((char*)b);
    env->ReleaseStringUTFChars(str, b);
    return 0;
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(checkNetState)(JNIEnv* env, jclass thiz, jint iIsWifi, jint IP, jint iIsIPValid)
{
    iIs3G = !iIsWifi;
    checkIPNow();
    return 0;
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(savePath)(JNIEnv* env, jclass thiz, jstring str)
{
    const char *b = (const char *)env->GetStringUTFChars(str, 0);
    setFileStorePath(b);
    env->ReleaseStringUTFChars(str, b);
    return 0;
}

#define DEBUG_OPTION_SSL  "ssl_level:"
#define DEBUG_OPTION_AXO  "axo_level:"

static void handleDebugOptions(const char *options)
{
    void setAxoLogLevel(int32_t level);

    debug_set_log_mode(POLARSSL_DEBUG_LOG_RAW);
    if (t_isEqual(options, DEBUG_OPTION_SSL, sizeof(DEBUG_OPTION_SSL)-1 )) {
        int ret = atoi(options + sizeof(DEBUG_OPTION_SSL)-1);
        if (ret >= 0 && ret <= 4)
            debug_set_threshold(ret);
        else
            debug_set_threshold(0);
    }
    if (t_isEqual(options, DEBUG_OPTION_AXO, sizeof(DEBUG_OPTION_AXO)-1 )) {
        int ret = atoi(options + sizeof(DEBUG_OPTION_AXO)-1);
        setAxoLogLevel(ret);
    }
}


JNIEXPORT jint JNICALL
JNI_FUNCTION(doInit)( JNIEnv* env, jobject thiz, jint iDebugFlag)
{
    jclass phoneServiceClass = NULL;
    iDebugable = iDebugFlag;

    if (thisTiviPhoneService == NULL) {
        thisTiviPhoneService = env->NewGlobalRef(thiz);
        if (thisTiviPhoneService == NULL) {
            return com_silentcircle_silentphone2_services_PhoneServiceNative_NO_GLOBAL_REF;
        }
        phoneServiceClass = env->GetObjectClass(thisTiviPhoneService);
        if (phoneServiceClass == NULL) {
            return com_silentcircle_silentphone2_services_PhoneServiceNative_NO_CLASS;
        }
        wakupCallBackMethod = env->GetMethodID(phoneServiceClass, "wakeCallback", "(I)V");
        if (wakupCallBackMethod == NULL) {
            return com_silentcircle_silentphone2_services_PhoneServiceNative_NO_WAKE_UP_CALLBACK;
        }
        stateCallBackMethod = env->GetMethodID(phoneServiceClass, "stateChangeCallback", "(IIILjava/lang/String;)V");
        if (stateCallBackMethod == NULL) {
            return com_silentcircle_silentphone2_services_PhoneServiceNative_NO_STATE_CALLBACK;
        }
    }
    debug_set_log_mode(POLARSSL_DEBUG_LOG_RAW);
    if (iDebugable > 0 && iDebugable <= 4)
        debug_set_threshold(iDebugable);
    return 0;
}

#define T_SET_LEVEL          "g.setLevel "
#define T_SET_SAMPLERATE     "set.samplerate="
#define T_PROV_START_API_STR "prov.start.apikey="
#define T_PROV_CLEAR_API_STR "prov.clear.apikey"
#define T_PROV_START_STR     "prov.start="
#define T_SET_GAIN_REDUCTION "set.gainReduction="
#define T_SET_LANGUAGE       "set.language="
#define T_END_ENGINE         ".exit"
#define T_SET_DEBUG_OPTIONS  "debug.option="


JNIEXPORT jint JNICALL
JNI_FUNCTION(doCmd)(JNIEnv* env, jclass thiz, jstring command)
{
    const char *b = (const char *)env->GetStringUTFChars(command, 0);

    LOG(if (iDebugable) __android_log_print(ANDROID_LOG_DEBUG,"jni_glue2", "cmd [%s]", b);)

    int ret = 0;

    if (strcmp(b, "getint.ReqTimeToLive") == 0) {
        ret = getReqTimeToLive();
    }
    else if (t_isEqual(b, T_SET_LEVEL, sizeof(T_SET_LEVEL)-1)) {
        iAPILevel = atoi(b + sizeof(T_SET_LEVEL)-1);
    }
    else if (strcmp(b, "isProv") == 0) {
        ret = isProvisioned(1);
    }
    else if (strcmp(b, T_END_ENGINE) == 0) {
        t_onEndApp();
        iFirst = 1;
        env->DeleteGlobalRef(thisTiviPhoneService);
        thisTiviPhoneService = NULL;
    }
    else if (t_isEqual(b, T_SET_SAMPLERATE, sizeof(T_SET_SAMPLERATE)-1 )) {
        ret = t_setNextHWSampleRate(atoi(b + sizeof(T_SET_SAMPLERATE)-1));
    }
    else if (t_isEqual(b, T_PROV_START_API_STR, sizeof(T_PROV_START_API_STR)-1 )) {
        ret = checkProvAPIKeyNoCallBack(b + sizeof(T_PROV_START_API_STR)-1);
    }
    else if (t_isEqual(b, T_PROV_START_API_STR, sizeof(T_PROV_CLEAR_API_STR)-1 )) {
        ret = provClearAPIKey();
    }
    else if (t_isEqual(b, T_PROV_START_STR, sizeof(T_PROV_START_STR)-1 )) {
        ret = checkProvNoCallBack(b + sizeof(T_PROV_START_STR)-1);
    }
    else if (t_isEqual(b, T_SET_DEBUG_OPTIONS, sizeof(T_SET_DEBUG_OPTIONS)-1 )) {
        handleDebugOptions(b + sizeof(T_SET_DEBUG_OPTIONS)-1);
    }
    else if (t_isEqual(b, T_SET_GAIN_REDUCTION, sizeof(T_SET_GAIN_REDUCTION)-1 )) {
        ret = atoi(b + sizeof(T_SET_GAIN_REDUCTION)-1);
        gainReduction = ret;
    }
    else if (t_isEqual(b, T_SET_LANGUAGE, sizeof(T_SET_LANGUAGE)-1 )) {
        strncpy(g_prefLang, b + sizeof(T_SET_LANGUAGE)-1, sizeof(g_prefLang)-1);
    }
    else {
        const char* argv[2];
        argv[0] = "";
        argv[1] = b;
        z_main(0, 2, argv);
    }
    env->ReleaseStringUTFChars(command, b);
    return ret;
}


#define T_PROV_TEST_STR "prov.tryGetResult"

JNIEXPORT jstring JNICALL
JNI_FUNCTION(getInfo)(JNIEnv* env, jclass thiz, jint iEngineID, jint iCallID, jstring command)
{
    const char *b = (const char *)env->GetStringUTFChars(command, 0);
    char *p = NULL;
    char ret[256]="";

    if (iCallID != 0 && iCallID != -1) {
        int r = getCallInfo(iCallID, b,  ret, sizeof(ret)-1);
        if (r <= 0)
            r = 0;
        p = ret;
        ret[r] = 0;
    }
    else {
        if (iEngineID == -1) {
            if (iCallID == -1  && t_isEqual(b, "get.samplerate", 14)) {
                snprintf(ret, sizeof(ret), "%d", t_getSampleRate());
                p = ret;
            }
            else if (iCallID == -1 && t_isEqual(b, "format.nr=", 10)) {
                initCC();
                fixNR((const char *)b+10, ret, sizeof(ret)-1);
                p = ret;
            }
            else if (iCallID == -1 &&  t_isEqual(b,"get.flag=", 9)) {
                initCC();
                p = ret;
                if (!findCSC_C_S_java(b+9, ret, sizeof(ret)-1))
                    p = NULL;
            }
            else if (iCallID ==-1 && t_isEqual(b,"prov.getAPIKey", 14)) {
                p=(char *)getAPIKey();
            }
            else if (iCallID == -1 && t_isEqual(b, T_PROV_TEST_STR, sizeof(T_PROV_TEST_STR)-1)) {
                p = (char*)getProvNoCallBackResp();
            }
            else{
                p=(char*)sendEngMsg(NULL, b);
            }
        }
        else {
            void *pEng = getAccountByID(iEngineID);
            if (!pEng)
                pEng = getAccountByID(iEngineID ? 0 : 1);

            if (pEng)
                p = (char*)sendEngMsg(pEng, b);
        }
    }
    env->ReleaseStringUTFChars(command, b);
    if (!p)
        return NULL;
    return env->NewStringUTF( p );
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(setKeyData)(JNIEnv* env, jclass thiz, jbyteArray byteArray)
{
    unsigned char *k = (unsigned char *)env->GetByteArrayElements(byteArray, 0);
    int iLen = env->GetArrayLength(byteArray);
    setPWDKey(k, iLen);
    env->ReleaseByteArrayElements(byteArray, (jbyte*)k, 0);
    return 0;
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(initPhone)(JNIEnv* env, jclass thiz, jint configuration, jint iDebugFlag, jstring versionName)
{
    iDebugable = iDebugFlag;

    if (iFirst) {
        iFirst = 0;
        t_init_log();
        switch (configuration) {
            case 0:
            default:
                setProvisioningToProduction();
                setSipToProduction();
                break;
            case 1:
                setProvisioningToDevelop();
                setSipToDevelop();
                break;
                // Case 2 is special: provisioning to development net, SIP to production.
            case 2:
                setProvisioningToDevelop();
                break;
        }
        if (g_versionName[0] == '\0') {
            const char *b = (const char *)env->GetStringUTFChars(versionName, 0);
            strncpy(g_versionName, b, sizeof(g_versionName)-1);
            // store to some global data or get it via 'char* getVersionName()'
            env->ReleaseStringUTFChars(versionName, b);
        }
        z_main_init(0, NULL);
        setPhoneCB(&fncCBRet, (void*)"a");
    }
    return 0;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv *env;

    g_JavaVM = vm;
    LOG(__android_log_write(ANDROID_LOG_VERBOSE, "jni_glue2", "JNI_OnLoad called");)
    umask(077);

    vm->GetEnv((void**)&env, JNI_VERSION_1_6);

    // The function is in audio/android_audio.cpp
    initAudioClassHelper(env, "com/silentcircle/silentphone2/audio/AudioRecordSp", true);
    initAudioClassHelper(env, "com/silentcircle/silentphone2/audio/AudioTrackSp", false);

    // Create/get and store an object of Java SipNotifyHandler
    initSipNotifyClassHelper(env, "com/silentcircle/userinfo/SipNotifyHandler");

    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(readAllZid)(JNIEnv* env, jclass thiz, jobject arrayList)
{
    #ifndef ZRTP_ENABLE_ZID_READ
    return com_silentcircle_silentphone2_services_PhoneServiceNative_ZID_READ_NOT_IMPLEMENTED;
    #else
    if (arrayList == NULL)
        return com_silentcircle_silentphone2_services_PhoneServiceNative_ZID_READ_NO_LIST;

    ZIDCache* zf = getZidCacheInstance();
    if (zf == NULL || !zf->isOpen())
        return com_silentcircle_silentphone2_services_PhoneServiceNative_ZID_READ_OPEN_FAILED;

    jclass clazz = env->FindClass("java/util/ArrayList");
    if (clazz == NULL)
        return com_silentcircle_silentphone2_services_PhoneServiceNative_ZID_READ_NO_CLASS;

    jmethodID mid = env->GetMethodID(clazz, "add", "(Ljava/lang/Object;)Z");
    if (mid == NULL)
        return com_silentcircle_silentphone2_services_PhoneServiceNative_ZID_READ_NO_METHOD;

    void *stmnt = zf->prepareReadAll();
    int32_t numRecords = 0;
    if (stmnt != NULL) {
        std::string output;
        while (true) {
            stmnt = zf->readNextRecord(stmnt, &output);
            if (stmnt == NULL)
                break;
            jstring str = env->NewStringUTF(output.c_str());
            if (str == NULL)
                continue;
            jboolean ret = env->CallBooleanMethod(arrayList, mid, str);
            if (!ret) {
                zf->closeOpenStatment(stmnt);
                break;
            }
            numRecords++;
        }
        return numRecords;
    }
    else {
        return com_silentcircle_silentphone2_services_PhoneServiceNative_ZID_READ_DB_FAILURE;
    }
    #endif
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(getOptionFlags)(JNIEnv* env, jclass thiz)
{
    jint flags = 0;
#ifdef ZRTP_ENABLE_ZID_READ
    flags |= com_silentcircle_silentphone2_services_PhoneServiceNative_ZID_READ_OPTION;
#endif
    return flags;
}

JNIEXPORT jintArray JNICALL
JNI_FUNCTION(getZrtpCounters)(JNIEnv *env, jclass thizz, jint iCallID)
{
    int32_t numCounters = getNumberOfCountersZrtp(iCallID);
    if (numCounters < 0)
        return NULL;

    jintArray intArray = env->NewIntArray(numCounters);

    if (intArray == NULL)
        return NULL;
    jint *counters = env->GetIntArrayElements(intArray, NULL);

    numCounters = getCountersZrtp(iCallID, counters);
    env->ReleaseIntArrayElements(intArray, counters, 0);

    if (numCounters < 0)
        return NULL;

    return intArray;
}

JNIEXPORT jint JNICALL
JNI_FUNCTION(getNumAccounts)(JNIEnv* env, jclass thiz)
{
    int cnt=0;

    for(int i=0;i<20;i++){
        void *pCurService=getAccountByID(i);
        if(pCurService){
            cnt++;
        }
    }

    return cnt;
}

JNIEXPORT void JNICALL
JNI_FUNCTION(setPushToken)(JNIEnv* env, jclass thiz, jstring str)
{
 __android_log_write(ANDROID_LOG_VERBOSE,"tivi","TiviPhoneService_setPushToken");
 if(str == NULL){
     return;
 }
 const char *regId = (const char *)env->GetStringUTFChars(str, 0);
 setPushToken(regId);
 env->ReleaseStringUTFChars(str, regId);
 return;
}

#ifdef __cplusplus
}
#endif

