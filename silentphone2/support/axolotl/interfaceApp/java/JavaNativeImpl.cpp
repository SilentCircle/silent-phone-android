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

#ifdef ANDROID
#include <android/log.h>
#else
#include <stdio.h>
#endif

#include "zina_ZinaNative.h"
#include "../AppInterfaceImpl.h"
#include "../../provisioning/Provisioning.h"
#include "../../appRepository/AppRepository.h"
#include "../../interfaceTransport/sip/SipTransport.h"
#include "../../ratchet/crypto/EcCurve.h"
#include "../../attachments/fileHandler/scloud.h"
#include "../../storage/NameLookup.h"
#include "../../dataRetention/ScDataRetention.h"
#include "../JsonStrings.h"
#include "../../util/Utilities.h"

using namespace zina;
using namespace std;

/**
 * Define -DPACKAGE_NAME="Java_some_package_name_" to define another package 
 * name during compilation
 */
#ifndef PACKAGE_NAME
#define PACKAGE_NAME Java_zina_ZinaNative_
#endif

#define CONCATx(a,b) a##b
#define CONCAT(a,b) CONCATx(a,b)

#define JNI_FUNCTION(FUNC_NAME)  CONCAT(PACKAGE_NAME, FUNC_NAME)

#ifndef JNIEXPORT
#error "JNIEXPORT not defined"
#endif

#define LOGGING
#ifdef LOGGING
#define LOG(deb)   deb
#else
#define LOG(deb)
#endif

#ifdef EMBEDDED
JavaVM *t_getJavaVM();
#endif

static AppInterfaceImpl* zinaAppInterface = NULL;
static JavaVM* javaVM = NULL;

// Set in doInit(...)
static jobject zinaCallbackObject = NULL;
static jmethodID receiveMessageCallback = NULL;
static jmethodID stateReportCallback = NULL;
static jmethodID httpHelperCallback = NULL;
static jmethodID javaNotifyCallback = NULL;
static jmethodID groupMsgReceiveCallback = NULL;
static jmethodID groupCmdReceiveCallback = NULL;
static jmethodID groupStateCallback = NULL;

static jclass preparedMessageDataClass = NULL;
static jmethodID preparedMessageDataConsID = NULL;
jfieldID transportIdID = NULL;
jfieldID receiverInfoID = NULL;

static int32_t debugLevel = 1;

// Plain public API without a class
AppInterfaceImpl* j_getAxoAppInterface() { return zinaAppInterface; }

void Log(char const *format, ...) {
    va_list arg;
    va_start(arg, format);
#ifdef ANDROID
    LOG(if (debugLevel > 0) __android_log_vprint(ANDROID_LOG_DEBUG, "libzina", format, arg);)
#else
    LOG(if (debugLevel > 0){ vfprintf(stderr, format, arg); fprintf(stderr, "\n");})
#endif
    va_end(arg);
}

// typedef void (*SEND_DATA_FUNC)(uint8_t* [], uint8_t* [], uint8_t* [], size_t [], uint64_t []);
#ifdef UNITTESTS
// names, devIds, envelopes, sizes, msgIds
static bool sendDataFuncTesting(uint8_t* names, uint8_t* devIds, uint8_t* envelopes, size_t sizes, uint64_t msgIds)
{
    (void)msgIds;

    Log("sendData: %s - %s - %s\n", names, devIds, envelopes);

    string fName((const char*)names);
    fName.append((const char*)devIds).append(".msg");

    FILE* msgFile = fopen(fName.c_str(), "w");

    size_t num = fwrite(envelopes, 1, sizes, msgFile);
    Log("Message file written: %d bytes\n", num);
    fclose(msgFile);
    return true;
}

static void receiveData(const string &msgFileName)
{
    uint8_t msgData[2000];
    FILE* msgFile = fopen(msgFileName.c_str(), "r");
    if (msgFile == NULL) {
        Log("Message file %s not found\n", msgFileName.c_str());
        return;
    }

    size_t num = fread(msgData, 1, 2000, msgFile);
    Log("Message file read: %d bytes\n", num);
    zinaAppInterface->getTransport()->receiveAxoMessage(msgData, num);
    fclose(msgFile);

}
#endif

static bool arrayToString(JNIEnv* env, jbyteArray array, string* output)
{
    if (array == NULL)
        return false;

    size_t dataLen = static_cast<size_t>(env->GetArrayLength(array));
    if (dataLen == 0)
        return false;

    const uint8_t* tmp = (uint8_t*)env->GetByteArrayElements(array, 0);
    if (tmp == NULL)
        return false;

    output->assign((const char*)tmp, dataLen);
    env->ReleaseByteArrayElements(array, (jbyte*)tmp, 0);
    return true;
}

static jbyteArray stringToArray(JNIEnv* env, const string& input)
{
    if (input.size() == 0)
        return NULL;

    jbyteArray data = env->NewByteArray(static_cast<jsize>(input.size()));
    if (data == NULL)
        return NULL;
    env->SetByteArrayRegion(data, 0, static_cast<jsize>(input.size()), (jbyte*)input.data());
    return data;
}

static void setReturnCode(JNIEnv* env, jintArray codeArray, int32_t result, int32_t data = 0)
{
    jint* code = env->GetIntArrayElements(codeArray, 0);
    code[0] = result;
    if (data != 0)
        code[1] = data;
    env->ReleaseIntArrayElements(codeArray, code, 0);
}


/**
 * Local helper class to keep track of thread attach / thread detach
 */
class CTJNIEnv {
    JNIEnv *env;
    bool attached;
public:
    CTJNIEnv() : attached(false), env(NULL) {

#ifdef EMBEDDED
        if (!javaVM)
            javaVM = t_getJavaVM();
#endif

        if (!javaVM)
            return;

        int s = javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (s != JNI_OK){
#ifdef ANDROID
            s = javaVM->AttachCurrentThread(&env, NULL);
#else
            s = javaVM->AttachCurrentThread((void**)&env, NULL);
#endif
            if (!env || s < 0) {
                env = NULL;
                return;
            }
            attached = true;
        }
    }

    ~CTJNIEnv() {
        if (attached && javaVM)
            javaVM->DetachCurrentThread();
    }

    JNIEnv *getEnv() {
        return env;
    }
};

// A global symbol to force loading of the object in case of embedded usage
void loadAxolotl() 
{
}

/*
 * Receive message callback for AppInterfaceImpl.
 * 
 * "([B[B[B)I"
 */
static int32_t receiveMessage(const string& messageDescriptor, const string& attachmentDescriptor = string(), const string& messageAttributes = string())
{
    if (zinaCallbackObject == NULL)
        return -1;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return -2;

    jbyteArray message = stringToArray(env, messageDescriptor);
    Log("receiveMessage - message: '%s' - length: %d", messageDescriptor.c_str(), messageDescriptor.size());

    jbyteArray attachment = NULL;
    if (!attachmentDescriptor.empty()) {
        attachment = stringToArray(env, attachmentDescriptor);
        if (attachment == NULL) {
            return -4;
        }
    }
    jbyteArray attributes = NULL;
    if (!messageAttributes.empty()) {
        attributes = stringToArray(env, messageAttributes);
        if (attributes == NULL) {
            return -4;
        }
    }
    int32_t result = env->CallIntMethod(zinaCallbackObject, receiveMessageCallback, message, attachment, attributes);

    env->DeleteLocalRef(message);
    if (attachment != NULL)
        env->DeleteLocalRef(attachment);
    if (attributes != NULL)
        env->DeleteLocalRef(attributes);

    return result;
}

/*
 * Receive message callback for AppInterfaceImpl.
 *
 * "([B[B[B)I"
 */
static int32_t receiveGroupMessage(const string& messageDescriptor, const string& attachmentDescriptor = string(), const string& messageAttributes = string())
{
    if (zinaCallbackObject == NULL)
        return -1;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return -2;

    jbyteArray message = stringToArray(env, messageDescriptor);
    Log("receiveGroupMessage: '%s' - length: %d", messageDescriptor.c_str(), messageDescriptor.size());

    jbyteArray attachment = NULL;
    if (!attachmentDescriptor.empty()) {
        attachment = stringToArray(env, attachmentDescriptor);
        if (attachment == NULL) {
            return -4;
        }
    }
    jbyteArray attributes = NULL;
    if (!messageAttributes.empty()) {
        attributes = stringToArray(env, messageAttributes);
        if (attributes == NULL) {
            return -4;
        }
    }
    int32_t result = env->CallIntMethod(zinaCallbackObject, groupMsgReceiveCallback, message, attachment, attributes);

    env->DeleteLocalRef(message);
    if (attachment != NULL)
        env->DeleteLocalRef(attachment);
    if (attributes != NULL)
        env->DeleteLocalRef(attributes);

    return result;
}

/*
 * Receive message callback for AppInterfaceImpl.
 *
 * "([B[B[B)I"
 */
static int32_t receiveGroupCommand(const string& commandMessage)
{
    if (zinaCallbackObject == NULL)
        return -1;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return -2;

    jbyteArray message = stringToArray(env, commandMessage);
    Log("receiveGroupCommand: '%s' - length: %d", commandMessage.c_str(), commandMessage.size());

    int32_t result = env->CallIntMethod(zinaCallbackObject, groupCmdReceiveCallback, message);

    env->DeleteLocalRef(message);

    return result;
}

/*
 * State change callback for AppInterfaceImpl.
 * 
 * "(J[B)V"
 */
static void messageStateReport(int64_t messageIdentifier, int32_t statusCode, const string& stateInformation)
{
    if (zinaCallbackObject == NULL)
        return;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return;

    jbyteArray information = NULL;
    if (!stateInformation.empty()) {
        information = stringToArray(env, stateInformation);
    }
    env->CallVoidMethod(zinaCallbackObject, stateReportCallback, messageIdentifier, statusCode, information);
    if (information != NULL)
        env->DeleteLocalRef(information);
}

/*
 * State change callback for AppInterfaceImpl.
 *
 * "(J[B)V"
 */
static void groupStateReport(int32_t statusCode, const string& stateInformation)
{
    if (zinaCallbackObject == NULL)
        return;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return;

    jbyteArray information = NULL;
    if (!stateInformation.empty()) {
        information = stringToArray(env, stateInformation);
    }
    env->CallVoidMethod(zinaCallbackObject, groupStateCallback, statusCode, information);
    if (information != NULL)
        env->DeleteLocalRef(information);
}

/*
 * Notify callback for AppInterfaceImpl.
 * 
 * "(I[B[B)V"
 */
static void notifyCallback(int32_t notifyAction, const string& actionInformation, const string& devId)
{
    if (zinaCallbackObject == NULL)
        return;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();
    if (!env)
        return;

    jbyteArray information = NULL;
    if (!actionInformation.empty()) {
        information = stringToArray(env, actionInformation);
    }
    jbyteArray deviceId = NULL;
    if (!devId.empty()) {
        deviceId = stringToArray(env, devId);
    }
    env->CallVoidMethod(zinaCallbackObject, javaNotifyCallback, notifyAction, information, deviceId);

    if (information != NULL)
        env->DeleteLocalRef(information);

    if (deviceId != NULL)
        env->DeleteLocalRef(deviceId);
}

/*
 * Class:     ZinaNative
 * Method:    httpHelper
 * Signature: ([BLjava/lang/String;[B[I)[B
 */
/*
 * HTTP request helper callback for provisioning etc.
 */
#define JAVA_HELPER
#if defined JAVA_HELPER || defined UNITTESTS
static int32_t httpHelper(const string& requestUri, const string& method, const string& requestData, string* response)
{
    if (zinaCallbackObject == NULL)
        return -1;

    CTJNIEnv jni;
    JNIEnv *env = jni.getEnv();

    if (!env) {
        return -2;
    }

    jbyteArray uri = NULL;
    uri = env->NewByteArray(static_cast<jsize>(requestUri.size()));
    if (uri == NULL)
        return -3;
    env->SetByteArrayRegion(uri, 0, static_cast<jsize>(requestUri.size()), (jbyte*)requestUri.data());

    jbyteArray reqData = NULL;
    if (!requestData.empty()) {
        reqData = stringToArray(env, requestData);
    }
    jstring mthod = env->NewStringUTF(method.c_str());

    jintArray code = env->NewIntArray(1);

    jbyteArray data = (jbyteArray)env->CallObjectMethod(zinaCallbackObject, httpHelperCallback, uri, mthod, reqData, code);
     if (data != NULL) {
        arrayToString(env, data, response);
    }
    int32_t result = -1;
    env->GetIntArrayRegion(code, 0, 1, &result);

    env->DeleteLocalRef(uri);
    if (reqData != NULL)
        env->DeleteLocalRef(reqData);
    env->DeleteLocalRef(mthod);
    env->DeleteLocalRef(code);

    return result;
}
#else
static int32_t httpHelper(const string& requestUri, const string& method, const string& requestData, string* response)
{

    char* t_send_http_json(const char *url, const char *meth,  char *bufResp, int iMaxLen, int &iRespContentLen, const char *pContent);

    Log("httpHelper request, method: '%s', url '%s'", method.c_str(), requestUri.c_str());
    if (requestData.size() > 0) {
        Log("httpHelper request, data: '%s'", requestData.c_str());
    }

    int iSizeOfRet = 4 * 1024;
    char *retBuf = new char [iSizeOfRet];
    int iContentLen = 0;

    int code = 0;
    char *content = t_send_http_json (requestUri.c_str(), method.c_str(), retBuf, iSizeOfRet - 1, iContentLen, requestData.c_str());

    Log("httpHelper response data: '%s'", content ? content : "No response data");

    if(content && iContentLen > 0 && response)
        response->assign((const char*)content, iContentLen);

    delete retBuf;

    if(iContentLen < 1)
        return -1;
   return 200;
}
#endif

// In Java implementation the HTTP Helper and S3 Helper use the same Java method.
// In iOS implementation they are seperate for ease of certificate pinning implementation.
int32_t s3Helper(const string& requestUri, const string& requestData, string* response)
{
  return httpHelper(requestUri, "PUT", requestData, response);
}

#ifndef EMBEDDED
jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    (void)reserved;
    javaVM = vm;
    return JNI_VERSION_1_6;
}
#endif

/*
 * Class:     zina_ZinaNative
 * Method:    doInit
 * Signature: (ILjava/lang/String;[B[B[B[BLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(doInit)(JNIEnv* env, jobject thiz, jint flags, jstring dbName, jbyteArray dbPassphrase, jbyteArray userName,
                    jbyteArray authorization, jbyteArray scClientDeviceId, jstring retentionFlags)
{
    debugLevel = flags & 0xf;
//    int32_t flagsInternal = flags >> 4;

    if (zinaCallbackObject == NULL) {
        zinaCallbackObject = env->NewGlobalRef(thiz);
        if (zinaCallbackObject == NULL) {
            return -1;
        }
        jclass callbackClass = NULL;
        callbackClass = env->GetObjectClass(zinaCallbackObject);
        if (callbackClass == NULL) {
            return -2;
        }
        receiveMessageCallback = env->GetMethodID(callbackClass, "receiveMessage", "([B[B[B)I");
        if (receiveMessageCallback == NULL) {
            return -3;
        }
        stateReportCallback = env->GetMethodID(callbackClass, "messageStateReport", "(JI[B)V");
        if (stateReportCallback == NULL) {
            return -4;
        }
        httpHelperCallback = env->GetMethodID(callbackClass, "httpHelper", "([BLjava/lang/String;[B[I)[B");
        if (httpHelperCallback == NULL) {
            return -5;
        }
        javaNotifyCallback = env->GetMethodID(callbackClass, "notifyCallback", "(I[B[B)V");
        if (javaNotifyCallback == NULL) {
            return -6;
        }
        groupMsgReceiveCallback = env->GetMethodID(callbackClass, "groupMsgReceive", "([B[B[B)I");
        if (groupMsgReceiveCallback == NULL) {
            return -20;
        }
        groupCmdReceiveCallback = env->GetMethodID(callbackClass, "groupCmdReceive", "([B)I");
        if (groupCmdReceiveCallback == NULL) {
            return -21;
        }
        groupStateCallback = env->GetMethodID(callbackClass, "groupStateCallback", "(I[B)V");
        if (groupStateCallback == NULL) {
            return -22;
        }
    }
    // Prepare access to the PreparedMessageData Java class inside ZinaNative.
    jclass tempClassRef = env->FindClass( "zina/ZinaNative$PreparedMessageData" );
    if (tempClassRef == NULL)
        return -24;
    preparedMessageDataClass = reinterpret_cast<jclass>(env->NewGlobalRef(tempClassRef));

    transportIdID = env->GetFieldID(preparedMessageDataClass, "transportId", "J");
    if (transportIdID == NULL)
        return -25;
    receiverInfoID = env->GetFieldID(preparedMessageDataClass, "receiverInfo", "Ljava/lang/String;");
    if (receiverInfoID == NULL)
        return -26;
    preparedMessageDataConsID = env->GetMethodID(preparedMessageDataClass, "<init>", "()V");
    if (preparedMessageDataConsID == NULL)
        return -27;

    string name;
    if (!arrayToString(env, userName, &name)) {
        return -10;
    }

    string auth;
    if (!arrayToString(env, authorization, &auth)) {
        return -11;
    }

    string devId;
    if (!arrayToString(env, scClientDeviceId, &devId))
        return -12;

    if (retentionFlags == nullptr)
        return -28;

    const char* tmp = env->GetStringUTFChars(retentionFlags, 0);
    string retentionString(tmp);
    env->ReleaseStringUTFChars(retentionFlags, tmp);

    const uint8_t* pw = (uint8_t*)env->GetByteArrayElements(dbPassphrase, 0);
    size_t pwLen = static_cast<size_t>(env->GetArrayLength(dbPassphrase));
    if (pw == NULL)
        return -14;

    if (pwLen != 32) {
        env->ReleaseByteArrayElements(dbPassphrase, (jbyte*)pw, 0);
        return -15;
    }

    if (dbName == NULL)
        return -16;

    string dbPw((const char*)pw, pwLen);

    Utilities::wipeMemory((void*)pw, pwLen);
    env->ReleaseByteArrayElements(dbPassphrase, (jbyte*)pw, 0);

    // initialize and open the persistent store singleton instance
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    store->setKey(dbPw);

    const char* db = env->GetStringUTFChars(dbName, 0);
    store->openStore(string (db));
    env->ReleaseStringUTFChars(dbName, db);

    Utilities::wipeMemory((void*)dbPw.data(), dbPw.size());

    int32_t retVal = 1;
    auto ownZinaConv = ZinaConversation::loadLocalConversation(name, *store);
    if (!ownZinaConv->isValid()) {  // no yet available, create one. An own conversation has the same local and remote name, empty device id
        KeyPairUnique idKeyPair = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);
        ownZinaConv->setDHIs(move(idKeyPair));
        ownZinaConv->storeConversation(*store);
        retVal = 2;
    }

    zinaAppInterface = new AppInterfaceImpl(name, auth, devId, receiveMessage, messageStateReport,
                                           notifyCallback, receiveGroupMessage, receiveGroupCommand, groupStateReport);

    zinaAppInterface->setDataRetentionFlags(retentionString);
    Transport* sipTransport = new SipTransport(zinaAppInterface);

    /* ***********************************************************************************
     * Initialize pointers/callback to the send/receive SIP data functions (network layer) 
     */
#ifdef UNITTESTS
    sipTransport->setSendDataFunction(sendDataFuncTesting);
#elif defined (EMBEDDED)
    // Functions defined in t_a_main module of silentphone library, this sends the data
    // via SIP message
    bool g_sendDataFuncAxoNew(uint8_t* names, uint8_t* devId, uint8_t* envelope, size_t size, uint64_t msgIds);
    void t_setAxoTransport(Transport *transport);

    sipTransport->setSendDataFunction(g_sendDataFuncAxoNew);
    t_setAxoTransport(sipTransport);
#else
#error "***** Missing initialization."
#endif
    /* *********************************************************************************
     * set sipTransport class to SIP network handler, sipTransport contains callback
     * functions 'receiveAxoData' and 'stateReportAxo'
     *********************************************************************************** */
    zinaAppInterface->setHttpHelper(httpHelper);
    zinaAppInterface->setS3Helper(s3Helper);
    zinaAppInterface->setTransport(sipTransport);

    return retVal;
}

static jobjectArray fillPrepMsgDataToJava(JNIEnv* env, unique_ptr<list<unique_ptr<PreparedMessageData> > > prepMessageData)
{
    size_t size = prepMessageData->size();

    jobjectArray result = env->NewObjectArray(static_cast<jsize>(size), preparedMessageDataClass, NULL);

    int32_t i = 0;
    while (!prepMessageData->empty()) {
        auto& msgData = prepMessageData->front();
        jobject msgDataJava = env->NewObject(preparedMessageDataClass, preparedMessageDataConsID);
        env->SetLongField(msgDataJava, transportIdID, static_cast<jlong>(msgData->transportId));

        jstring tmpString = env->NewStringUTF(msgData->receiverInfo.c_str());
        env->SetObjectField(msgDataJava, receiverInfoID, tmpString);
        env->DeleteLocalRef(tmpString);

        env->SetObjectArrayElement(result, i++, msgDataJava);
        env->DeleteLocalRef(msgDataJava);
        prepMessageData->pop_front();
    }
    return result;
}

/*
 * Class:     zina_ZinaNative
 * Method:    prepareMessageNormal
 * Signature: ([B[B[BZ[I)[Lzina/ZinaNative/PreparedMessageData;
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(prepareMessageNormal)(JNIEnv* env, jclass clazz, jbyteArray messageDescriptor,
                                   jbyteArray attachmentDescriptor, jbyteArray messageAttributes,
                                   jboolean normalMsg, jintArray code)
{
    (void)clazz;

    if (code == NULL || env->GetArrayLength(code) < 1 || messageDescriptor == NULL || zinaAppInterface == NULL)
        return NULL;

    string message;
    if (!arrayToString(env, messageDescriptor, &message)) {
        setReturnCode(env, code, DATA_MISSING);
        return NULL;
    }
    Log("prepareMessage - message: '%s' - length: %d", message.c_str(), message.size());

    string attachment;
    if (attachmentDescriptor != NULL) {
        arrayToString(env, attachmentDescriptor, &attachment);
        Log("prepareMessage - attachment: '%s' - length: %d", attachment.c_str(), attachment.size());
    }
    string attributes;
    if (messageAttributes != NULL) {
        arrayToString(env, messageAttributes, &attributes);
        Log("prepareMessage - attributes: '%s' - length: %d", attributes.c_str(), attributes.size());
    }
    int32_t error;
    auto prepMessageData = zinaAppInterface->prepareMessageNormal(message, attachment, attributes,
                                                                  static_cast<bool>(normalMsg), &error);
    if (error != SUCCESS) {
        setReturnCode(env, code, error);
        return NULL;
    }
    return fillPrepMsgDataToJava(env, move(prepMessageData));
}

/*
 * Class:     zina_ZinaNative
 * Method:    prepareMessageSiblings
 * Signature: ([B[B[BZ[I)[Lzina/ZinaNative/PreparedMessageData;
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(prepareMessageSiblings)(JNIEnv* env, jclass clazz, jbyteArray messageDescriptor,
                                     jbyteArray attachmentDescriptor, jbyteArray messageAttributes,
                                     jboolean normalMsg, jintArray code)
{
    (void)clazz;

    if (code == NULL || env->GetArrayLength(code) < 1 || messageDescriptor == NULL || zinaAppInterface == NULL)
        return NULL;

    string message;
    if (!arrayToString(env, messageDescriptor, &message)) {
        setReturnCode(env, code, DATA_MISSING);
        return NULL;
    }
    Log("prepareMessageToSiblings - message: '%s' - length: %d", message.c_str(), message.size());

    string attachment;
    if (attachmentDescriptor != NULL) {
        arrayToString(env, attachmentDescriptor, &attachment);
        Log("prepareMessageToSiblings - attachment: '%s' - length: %d", attachment.c_str(), attachment.size());
    }
    string attributes;
    if (messageAttributes != NULL) {
        arrayToString(env, messageAttributes, &attributes);
        Log("prepareMessageToSiblings - attributes: '%s' - length: %d", attributes.c_str(), attributes.size());
    }
    int32_t error;

    auto prepMessageData = zinaAppInterface->prepareMessageSiblings(message, attachment, attributes,
                                                                    static_cast<bool>(normalMsg), &error);
    if (error != SUCCESS) {
        setReturnCode(env, code, error);
        return NULL;
    }
    return fillPrepMsgDataToJava(env, move(prepMessageData));
}

/*
 * Class:     zina_ZinaNative
 * Method:    doSendMessages
 * Signature: ([J)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(doSendMessages)(JNIEnv* env, jclass clazz, jlongArray ids)
{
    (void)clazz;

    if (ids == NULL)
        return DATA_MISSING;

    size_t dataLen = static_cast<size_t>(env->GetArrayLength(ids));
    if (dataLen < 1)
        return DATA_MISSING;

    const uint64_t* tmp = (uint64_t*)env->GetLongArrayElements(ids, 0);
    if (tmp == NULL)
        return DATA_MISSING;

    auto idVector = make_shared<vector<uint64_t> >();

    for (size_t i = 0; i < dataLen; i++) {
        idVector->push_back(tmp[i]);
    }
    env->ReleaseLongArrayElements(ids, (jlong *)tmp, 0);
    return zinaAppInterface->doSendMessages(idVector);
}

/*
 * Class:     zina_ZinaNative
 * Method:    removePreparedMessages
 * Signature: ([J)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(removePreparedMessages)(JNIEnv* env, jclass clazz, jlongArray ids)
{
    (void)clazz;

    if (ids == NULL)
        return DATA_MISSING;

    size_t dataLen = static_cast<size_t>(env->GetArrayLength(ids));
    if (dataLen < 1)
        return DATA_MISSING;

    const uint64_t* tmp = (uint64_t*)env->GetLongArrayElements(ids, 0);
    if (tmp == NULL)
        return DATA_MISSING;

    auto idVector = make_shared<vector<uint64_t> >();

    for (size_t i = 0; i < dataLen; i++) {
        idVector->push_back(tmp[i]);
    }
    env->ReleaseLongArrayElements(ids, (jlong *)tmp, 0);
    return zinaAppInterface->removePreparedMessages(idVector);
}


/*
 * Class:     ZinaNative
 * Method:    getKnownUsers
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL 
JNI_FUNCTION(getKnownUsers)(JNIEnv* env, jclass clazz)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    string* jsonNames = zinaAppInterface->getKnownUsers();
    if (jsonNames == NULL)
        return NULL;

    size_t size = jsonNames->size();
    jbyteArray names = NULL;
    names = env->NewByteArray(static_cast<jsize>(size));
    if (names != NULL) {
        env->SetByteArrayRegion(names, 0, static_cast<jsize>(size), (jbyte*)jsonNames->data());
    }
    delete jsonNames;
    return names;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getOwnIdentityKey
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(getOwnIdentityKey) (JNIEnv* env, jclass clazz)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    string idKey = zinaAppInterface->getOwnIdentityKey();
    jbyteArray key = stringToArray(env, idKey);
    return key;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getIdentityKeys
 * Signature: ([B)[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(getIdentityKeys) (JNIEnv* env, jclass clazz, jbyteArray userName)
{
    (void)clazz;

    string name;
    if (!arrayToString(env, userName, &name) || zinaAppInterface == NULL)
        return NULL;

    shared_ptr<list<string> > idKeys = zinaAppInterface->getIdentityKeys(name);

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(idKeys->size()), byteArrayClass, NULL);

    int32_t index = 0;
    for (; !idKeys->empty(); idKeys->pop_front()) {
        const string& s = idKeys->front();
        jbyteArray retData = stringToArray(env, s);
        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
    }
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getZinaDevicesUser
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(getZinaDevicesUser) (JNIEnv* env, jclass clazz, jbyteArray userName)
{
    (void)clazz;

    string name;
    if (!arrayToString(env, userName, &name) || zinaAppInterface == NULL)
        return NULL;

    list<pair<string, string> > devices;
    Provisioning::getZinaDeviceIds(name, zinaAppInterface->getOwnAuthrization(), devices);

    if (devices.empty()) {
        return NULL;
    }

    cJSON *root,*devArray, *devInfo;
    root = cJSON_CreateObject();
    cJSON_AddItemToObject(root, "version", cJSON_CreateNumber(1));
    cJSON_AddItemToObject(root, "devices", devArray = cJSON_CreateArray());

    for (auto &idName : devices) {
        devInfo = cJSON_CreateObject();
        cJSON_AddStringToObject(devInfo, "id", idName.first.c_str());
        cJSON_AddStringToObject(devInfo, "device_name", idName.second.c_str());
        cJSON_AddItemToArray(devArray, devInfo);
    }

    char *out = cJSON_Print(root);
    string json(out);
    cJSON_Delete(root); free(out);

    jbyteArray retData = stringToArray(env, json);
    return retData;
}


/*
 * Class:     ZinaNative
 * Method:    registerZinaDevice
 * Signature: ([B[I)[B
 */
JNIEXPORT jbyteArray JNICALL 
JNI_FUNCTION(registerZinaDevice)(JNIEnv* env, jclass clazz, jintArray code)
{
    (void)clazz;

    string info;
    if (code == NULL || env->GetArrayLength(code) < 1 || zinaAppInterface == NULL)
        return NULL;

    int32_t result = zinaAppInterface->registerZinaDevice(&info);

    setReturnCode(env, code, result);

    jbyteArray infoBytes = NULL;
    if (!info.empty()) {
        size_t size = info.size();
        infoBytes = env->NewByteArray(static_cast<jsize>(size));
        if (infoBytes != NULL) {
            env->SetByteArrayRegion(infoBytes, 0, static_cast<jsize>(size), (jbyte*)info.data());
        }
    }
    return infoBytes;
}

/*
 * Class:     zina_ZinaNative
 * Method:    removeZinaDevice
 * Signature: ([B[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(removeZinaDevice) (JNIEnv* env, jclass clazz, jbyteArray deviceId, jintArray code)
{
    (void)clazz;

    string info;
    if (code == NULL || env->GetArrayLength(code) < 1 || zinaAppInterface == NULL)
        return NULL;

    string devId;
    if (!arrayToString(env, deviceId, &devId))
        return NULL;


    int32_t result = zinaAppInterface->removeZinaDevice(devId, &info);

    setReturnCode(env, code, result);

    jbyteArray infoBytes = NULL;
    if (!info.empty()) {
        size_t size = info.size();
        infoBytes = env->NewByteArray(static_cast<jsize>(size));
        if (infoBytes != NULL) {
            env->SetByteArrayRegion(infoBytes, 0, static_cast<jsize>(size), (jbyte*)info.data());
        }
    }
    return infoBytes;
}


/*
 * Class:     zina_ZinaNative
 * Method:    newPreKeys
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL 
JNI_FUNCTION(newPreKeys)(JNIEnv* env, jclass clazz, jint numbers)
{
    (void)clazz;
    (void)env;
    if (zinaAppInterface == NULL)
        return -1;

    return zinaAppInterface->newPreKeys(numbers);
}

/*
 * Class:     zina_ZinaNative
 * Method:    getNumPreKeys
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(getNumPreKeys) (JNIEnv* env, jclass clazz)
{
    (void)clazz;
    (void)env;
    if (zinaAppInterface == NULL)
        return -1;

    return zinaAppInterface->getNumPreKeys();
}

/*
 * Class:     ZinaNative
 * Method:    getErrorCode
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(getErrorCode)(JNIEnv* env, jclass clazz)
{
    (void)clazz;
    (void)env;
    if (zinaAppInterface == NULL)
        return -1;

    return zinaAppInterface->getErrorCode();
}

/*
 * Class:     ZinaNative
 * Method:    getErrorInfo
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
JNI_FUNCTION(getErrorInfo)(JNIEnv* env, jclass clazz)
{
    (void)clazz;
    if (zinaAppInterface == NULL)
        return NULL;

    const string info = zinaAppInterface->getErrorInfo();
    jstring errInfo = env->NewStringUTF(info.c_str());
    return errInfo;
}

/*
 * Class:     ZinaNative
 * Method:    testCommand
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(testCommand)(JNIEnv* env, jclass clazz, jstring command, jbyteArray data)
{
    (void)clazz;

    int32_t result = 0;
    const char* cmd = env->GetStringUTFChars(command, 0);

    string dataContainer;
    if (data != NULL) {
        size_t dataLen = static_cast<size_t>(env->GetArrayLength(data));
        if (dataLen > 0) {
            const uint8_t* tmp = (uint8_t*)env->GetByteArrayElements(data, 0);
            if (tmp != NULL) {
                dataContainer.assign((const char*)tmp, dataLen);
                env->ReleaseByteArrayElements(data, (jbyte*)tmp, 0);
            }
        }
    }
    Log("testCommand - command: '%s' - data: '%s'", cmd, dataContainer.c_str());

#ifdef UNITTESTS
    if (strcmp("http", cmd) == 0) {
        string resultData;
        result = httpHelper(string("/some/request"), dataContainer, string("MTH"), &resultData);
        Log("httpHelper - code: %d, resultData: %s", result, resultData.c_str());
    }

    if (strcmp("read", cmd) == 0) {
        receiveData(dataContainer);
    }
#endif
    if (strcmp("resetaxodb", cmd) == 0) {
        SQLiteStoreConv* store = SQLiteStoreConv::getStore();
        store->resetStore();
        Log("Resetted Axolotl store");
    }

    env->ReleaseStringUTFChars(command, cmd);
    return result;
}

/*
 * Class:     zina_ZinaNative
 * Method:    zinaCommand
 * Signature: (Ljava/lang/String;[B[I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
JNI_FUNCTION(zinaCommand) (JNIEnv* env, jclass clazz, jstring command, jbyteArray data, jintArray code)
{
    (void)clazz;

    if (command == nullptr || zinaAppInterface == nullptr || code == nullptr ||  env->GetArrayLength(code) < 1)
        return nullptr;
    const char* cmd = env->GetStringUTFChars(command, 0);

    jstring result = NULL;
    setReturnCode(env, code, SUCCESS);

    string dataContainer;
    arrayToString(env, data, &dataContainer);

    if (strcmp("removeAxoConversation", cmd) == 0 && !dataContainer.empty()) {
        Log("Removing Axolotl conversation data for '%s'\n", dataContainer.c_str());

        SQLiteStoreConv* store = zinaAppInterface->getStore();
        int32_t sqlResult = store->deleteConversationsName(dataContainer, zinaAppInterface->getOwnUser());

        Log("Removing Zina conversation data for '%s' returned %d\n", dataContainer.c_str(), sqlResult);
        if (SQL_FAIL(sqlResult)) {
            result = env->NewStringUTF(store->getLastError());
            setReturnCode(env, code, sqlResult);
        }
    }
    else if (strcmp("rescanUserDevices", cmd) == 0 && !dataContainer.empty()) {
        zinaAppInterface->rescanUserDevices(dataContainer);
    }
    else if (strcmp("reKeyAllDevices", cmd) == 0 && !dataContainer.empty()) {
        zinaAppInterface->reKeyAllDevices(dataContainer);
    }
    else if (strcmp("reSyncConversation", cmd) == 0 && !dataContainer.empty()) {
        cJSON* root = cJSON_Parse(dataContainer.c_str());
        cJSON* details = NULL;
        if (root != NULL) {
            details = cJSON_GetObjectItem(root, "details");
        }
        if (details != NULL) {

            string userName;
            cJSON *jsonItem = cJSON_GetObjectItem(details, "name");
            if (jsonItem != NULL) {
                userName.assign(jsonItem->valuestring);
            }
            string deviceId;
            jsonItem = cJSON_GetObjectItem(details, "scClientDevId");
            if (jsonItem != NULL) {
                deviceId.assign(jsonItem->valuestring);
            }
            if (!userName.empty() && !deviceId.empty()) {
                zinaAppInterface->reKeyDevice(userName, deviceId);
            }
        }
        cJSON_Delete(root);
    }
    else if (strcmp("clearGroupData", cmd) == 0) {
        zinaAppInterface->clearGroupData();
    }
    else if (strcmp("runRetry", cmd) == 0) {
        // Check for left-over messages in persistent queues, can happen if app crashes in
        // the middle of receive message processing.
        zinaAppInterface->retryReceivedMessages();
    }
    else if (strcmp("setIdKeyVerified", cmd) == 0 && !dataContainer.empty()) {
        cJSON* root = cJSON_Parse(dataContainer.c_str());
        if (root != nullptr) {
            string userName;
            cJSON *jsonItem = cJSON_GetObjectItem(root, "name");
            if (jsonItem != NULL) {
                userName.assign(jsonItem->valuestring);
            }
            string deviceId;
            jsonItem = cJSON_GetObjectItem(root, "scClientDevId");
            if (jsonItem != NULL) {
                deviceId.assign(jsonItem->valuestring);
            }
            bool flag = Utilities::getJsonBool(root, "flag", true);
            if (!userName.empty() && !deviceId.empty()) {
                zinaAppInterface->setIdKeyVerified(userName, deviceId, flag);
            }
        }
    }
    env->ReleaseStringUTFChars(command, cmd);
    return result;
}

/*
 * **************************************************************
 * Below the native functions for group chat
 * *************************************************************
 */

/*
 * Class:     zina_ZinaNative
 * Method:    createNewGroup
 * Signature: ([B[B)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
JNI_FUNCTION(createNewGroup)(JNIEnv *env, jclass clazz, jbyteArray groupName, jbyteArray groupDescription)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    string group;
    arrayToString(env, groupName, &group);

    string description;
    arrayToString(env, groupDescription, &description);

    string groupUuid = zinaAppInterface->createNewGroup(group, description);
    if (groupUuid.empty())
        return NULL;
    jstring uuidJava = env->NewStringUTF(groupUuid.c_str());
    return uuidJava;
}

/*
 * Class:     zina_ZinaNative
 * Method:    modifyGroupSize
 * Signature: (Ljava/lang/String;I)Z
 */
JNIEXPORT jboolean
JNICALL JNI_FUNCTION(modifyGroupSize)(JNIEnv *env, jclass clazz, jstring groupUuid, jint newSize)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return JNI_FALSE;

    if (groupUuid == NULL)
        return JNI_FALSE;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);
    bool result = zinaAppInterface->modifyGroupSize(group, newSize);
    return result ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     zina_ZinaNative
 * Method:    setGroupName
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(setGroupName)(JNIEnv *env, jclass clazz, jstring groupUuid, jbyteArray name)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    string nm;
    if (name != NULL) {
        arrayToString(env, name, &nm);
    }
    return zinaAppInterface->setGroupName(group, name == NULL? nullptr : &nm);
}

/*
 * Class:     zina_ZinaNative
 * Method:    setGroupBurnTime
 * Signature: (Ljava/lang/String;JI)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(setGroupBurnTime)(JNIEnv *env, jclass clazz, jstring groupUuid, jlong duration, jint mode)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);
    return zinaAppInterface->setGroupBurnTime(group, static_cast<uint64_t>(duration), mode);
}

/*
 * Class:     zina_ZinaNative
 * Method:    setGroupAvatar
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(setGroupAvatar)(JNIEnv *env, jclass clazz, jstring groupUuid, jbyteArray avatar)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    string av;
    if (avatar != NULL) {
        arrayToString(env, avatar, &av);
    }
    return zinaAppInterface->setGroupAvatar(group, avatar == NULL? nullptr : &av);
}

/*
 * Class:     zina_ZinaNative
 * Method:    listAllGroups
 * Signature: ([I)[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(listAllGroups)(JNIEnv *env, jclass clazz, jintArray code)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    list<JsonUnique> groups;
    int32_t result = zinaAppInterface->getStore()->listAllGroups(groups);
    setReturnCode(env, code, result);

    size_t size = groups.size();
    if (size == 0)
        return NULL;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(size), byteArrayClass, NULL);

    int32_t index = 0;
    for (auto& group : groups) {
        CharUnique out(cJSON_PrintUnformatted(group.get()));
        jbyteArray retData = stringToArray(env, out.get());

        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
    }
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    listAllGroupsWithMember
 * Signature: ([I)[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(listAllGroupsWithMember)(JNIEnv *env, jclass clazz, jstring participantUuid, jintArray code)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    if (participantUuid == NULL)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    string participant;
    const char* temp = env->GetStringUTFChars(participantUuid, 0);
    participant = temp;
    env->ReleaseStringUTFChars(participantUuid, temp);


    list<JsonUnique> groups;
    int32_t result = zinaAppInterface->getStore()->listAllGroupsWithMember(participant, groups);
    setReturnCode(env, code, result);

    size_t size = groups.size();
    if (size == 0)
        return NULL;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(size), byteArrayClass, NULL);

    int32_t index = 0;
    for (auto& group : groups) {
        CharUnique out(cJSON_PrintUnformatted(group.get()));
        jbyteArray retData = stringToArray(env, out.get());

        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
    }
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getGroup
 * Signature: (Ljava/lang/String;[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(getGroup)(JNIEnv *env, jclass clazz, jstring groupUuid, jintArray code)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    if (groupUuid == NULL)
        return NULL;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    int32_t result;
    shared_ptr<cJSON> groupJson = zinaAppInterface->getStore()->listGroup(group, &result);

    setReturnCode(env, code, result);
    char *out = cJSON_PrintUnformatted(groupJson.get());
    jbyteArray retArray = NULL;
    if (out != NULL) {
        retArray = stringToArray(env, out);
        free(out);
    }
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getAllGroupMembers
 * Signature: (Ljava/lang/String;[I)[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(getAllGroupMembers)(JNIEnv *env, jclass clazz, jstring groupUuid, jintArray code)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    if (groupUuid == NULL)
        return NULL;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    list<JsonUnique> members;
    int32_t result = zinaAppInterface->getStore()->getAllGroupMembers(group, members);
    setReturnCode(env, code, result);

    size_t size = members.size();
    if (size == 0)
        return NULL;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(size), byteArrayClass, NULL);

    int32_t index = 0;
    for (auto& it : members) {
        CharUnique out(cJSON_PrintUnformatted(it.get()));
        jbyteArray retData = stringToArray(env, out.get());

        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
    }
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getGroupMember
 * Signature: (Ljava/lang/String;[B[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(getGroupMember) (JNIEnv *env, jclass clazz, jstring groupUuid, jbyteArray memberUuid, jintArray code)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    if (groupUuid == NULL)
        return NULL;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    string memberId;
    if (!arrayToString(env, memberUuid, &memberId)) {
        return NULL;
    }

    int32_t result;
    shared_ptr<cJSON> memberJson = zinaAppInterface->getStore()->getGroupMember(group, memberId, &result);
    setReturnCode(env, code, result);

    char *out = cJSON_PrintUnformatted(memberJson.get());
    jbyteArray retArray = stringToArray(env, out);
    free(out);
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    addUser
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(addUser)(JNIEnv *env, jclass clazz, jstring groupUuid, jbyteArray userId)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    string usr;
    if (!arrayToString(env, userId, &usr)) {
        return GROUP_MSG_DATA_INCONSISTENT;
    }
    return zinaAppInterface->addUser(group, usr);
}

/*
 * Class:     zina_ZinaNative
 * Method:    removeUserFromAddUpdate
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(removeUserFromAddUpdate)(JNIEnv *env, jclass clazz, jstring groupUuid, jbyteArray userId)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    string usr;
    if (!arrayToString(env, userId, &usr)) {
        return GROUP_MSG_DATA_INCONSISTENT;
    }
    return zinaAppInterface->removeUserFromAddUpdate(group, usr);
}

/*
 * Class:     zina_ZinaNative
 * Method:    cancelGroupChangeSet
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(cancelGroupChangeSet)(JNIEnv *env, jclass clazz, jstring groupUuid)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);
    return zinaAppInterface->cancelGroupChangeSet(group);
}

/*
 * Class:     zina_ZinaNative
 * Method:    applyGroupChangeSet
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(applyGroupChangeSet)(JNIEnv *env, jclass clazz, jstring groupUuid)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);
    return zinaAppInterface->applyGroupChangeSet(group);
}

/*
 * Class:     zina_ZinaNative
 * Method:    sendGroupMessage
 * Signature: ([B[B[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(sendGroupMessage)(JNIEnv *env, jclass clazz, jbyteArray messageDescriptor, jbyteArray attachmentDescriptor, jbyteArray messageAttributes)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    string message;
    if (!arrayToString(env, messageDescriptor, &message)) {
        return GROUP_MSG_DATA_INCONSISTENT;
    }
    Log("sendGroupMessage - message: '%s' - length: %d", message.c_str(), message.size());

    string attachment;
    if (attachmentDescriptor != NULL) {
        arrayToString(env, attachmentDescriptor, &attachment);
        Log("sendGroupMessage - attachment: '%s' - length: %d", attachment.c_str(), attachment.size());
    }
    string attributes;
    if (messageAttributes != NULL) {
        arrayToString(env, messageAttributes, &attributes);
        Log("sendGroupMessage - attributes: '%s' - length: %d", attributes.c_str(), attributes.size());
    }
    return zinaAppInterface->sendGroupMessage(message, attachment, attributes);
}

/*
 * Class:     zina_ZinaNative
 * Method:    sendGroupMessageToMember
 * Signature: ([B[B[B[BLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(sendGroupMessageToMember)(JNIEnv *env, jclass clazz, jbyteArray messageDescriptor, jbyteArray attachmentDescriptor,
                                       jbyteArray messageAttributes, jbyteArray recipient, jstring deviceId)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    string message;
    if (!arrayToString(env, messageDescriptor, &message)) {
        return GROUP_MSG_DATA_INCONSISTENT;
    }
    string receiver;
    if (!arrayToString(env, recipient, &receiver)) {
        return ILLEGAL_ARGUMENT;
    }
    Log("sendGroupMessageToMember - message: '%s' - length: %d", message.c_str(), message.size());

    string attachment;
    if (attachmentDescriptor != NULL) {
        arrayToString(env, attachmentDescriptor, &attachment);
        Log("sendGroupMessageToMember - attachment: '%s' - length: %d", attachment.c_str(), attachment.size());
    }
    string attributes;
    if (messageAttributes != NULL) {
        arrayToString(env, messageAttributes, &attributes);
        Log("sendGroupMessageToMember - attributes: '%s' - length: %d", attributes.c_str(), attributes.size());
    }
    string devId;
    if (deviceId != nullptr) {
        const char *temp = env->GetStringUTFChars(deviceId, 0);
        devId = temp;
        env->ReleaseStringUTFChars(deviceId, temp);
    }
    return zinaAppInterface->sendGroupMessageToMember(message, attachment, attributes, receiver, devId);
}


/*
 * Class:     zina_ZinaNative
 * Method:    sendGroupCommandToMember
 * Signature: (Ljava/lang/String;[BLjava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(sendGroupCommandToMember)(JNIEnv *env, jclass clazz, jstring groupId, jbyteArray member, jstring msgId, jbyteArray command)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupId == nullptr) {
        return ILLEGAL_ARGUMENT;
    }
    const char *temp = env->GetStringUTFChars(groupId, 0);
    string group(temp);
    env->ReleaseStringUTFChars(groupId, temp);
    if (group.empty()) {
        return DATA_MISSING;
    }

    string recipient;
    if (!arrayToString(env, member, &recipient)) {
        return ILLEGAL_ARGUMENT;
    }
    string id;
    if (msgId != nullptr) {
        temp = env->GetStringUTFChars(msgId, 0);
        id = temp;
        env->ReleaseStringUTFChars(msgId, temp);
    }
    string cmd;
    if (!arrayToString(env, command, &cmd)) {
        return ILLEGAL_ARGUMENT;
    }
    return zinaAppInterface->sendGroupCommandToMember(group, recipient, id, cmd);
}


/*
 * Class:     zina_ZinaNative
 * Method:    leaveGroup
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(leaveGroup)(JNIEnv *env, jclass clazz, jstring groupUuid)
{
    (void)clazz;

    if (zinaAppInterface == nullptr)
        return GENERIC_ERROR;

    if (groupUuid == nullptr)
        return DATA_MISSING;

    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    string group(temp);
    env->ReleaseStringUTFChars(groupUuid, temp);

    return zinaAppInterface->leaveGroup(group);
}

/*
 * Class:     zina_ZinaNative
 * Method:    removeUser
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(removeUser)(JNIEnv *env, jclass clazz, jstring groupUuid, jbyteArray userId)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char* temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    string usr;
    if (!arrayToString(env, userId, &usr)) {
        return GROUP_MSG_DATA_INCONSISTENT;
    }
    return zinaAppInterface->removeUser(group, usr);
}

/*
 * Class:     zina_ZinaNative
 * Method:    removeUserFromRemoveUpdate
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(removeUserFromRemoveUpdate)(JNIEnv *env, jclass clazz, jstring groupUuid, jbyteArray userId)
{
    (void) clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupUuid == NULL)
        return DATA_MISSING;

    string group;
    const char *temp = env->GetStringUTFChars(groupUuid, 0);
    group = temp;
    env->ReleaseStringUTFChars(groupUuid, temp);

    string usr;
    if (!arrayToString(env, userId, &usr)) {
        return GROUP_MSG_DATA_INCONSISTENT;
    }
    return zinaAppInterface->removeUserFromRemoveUpdate(group, usr);
}

/*
 * Class:     zina_ZinaNative
 * Method:    burnGroupMessage
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(burnGroupMessage)(JNIEnv* env, jclass clazz, jstring groupId, jobjectArray messageIds)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return GENERIC_ERROR;

    if (groupId == nullptr || messageIds == nullptr || env->GetArrayLength(messageIds) < 1)
        return DATA_MISSING;

    const char* temp = env->GetStringUTFChars(groupId, 0);
    string group(temp);
    env->ReleaseStringUTFChars(groupId, temp);

    jsize elements =  env->GetArrayLength(messageIds);
    vector<string> msgIds(static_cast<size_t>(elements));

    for (jsize i = 0; i < elements; i++) {
        jstring msgId = (jstring)env->GetObjectArrayElement(messageIds, i);

        temp = env->GetStringUTFChars(msgId, 0);
        string id(temp);
        env->ReleaseStringUTFChars(msgId, temp);
        msgIds.push_back(id);

        env->DeleteLocalRef(msgId);
    }

    int32_t result = zinaAppInterface->burnGroupMessage(group, msgIds);
    return result == SUCCESS ? OK : result;
}


/*
 * **************************************************************
 * Below the native functions for the repository database
 * *************************************************************
 */

static AppRepository* appRepository = NULL;


/*
 * Class:     zina_ZinaNative
 * Method:    repoOpenDatabase
 * Signature: (Ljava/lang/String;[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(repoOpenDatabase) (JNIEnv* env, jclass clazz, jstring dbName, jbyteArray keyData)
{
    (void)clazz;

    string nameString;
    if (dbName != NULL) {
        const char* name = env->GetStringUTFChars(dbName, 0);
        nameString = name;
        env->ReleaseStringUTFChars(dbName, name);
    }
    const uint8_t* pw = (uint8_t*)env->GetByteArrayElements(keyData, 0);
    size_t pwLen = static_cast<size_t>(env->GetArrayLength(keyData));
    if (pw == NULL)
        return -2;
    if (pwLen != 32)
        return -3;

    string dbPw((const char*)pw, pwLen);

    Utilities::wipeMemory((void*)pw, pwLen);
    env->ReleaseByteArrayElements(keyData, (jbyte*)pw, 0);

    appRepository = AppRepository::getStore();
    appRepository->setKey(dbPw);
    appRepository->openStore(nameString);

    Utilities::wipeMemory((void*)dbPw.data(), dbPw.size());

    return appRepository->getSqlCode();
}

/*
 * Class:     zina_ZinaNative
 * Method:    repoCloseDatabase
 * Signature: ()V
 */
JNIEXPORT void JNICALL
JNI_FUNCTION(repoCloseDatabase) (JNIEnv* env, jclass clazz) {
    (void)clazz;
    (void)env;

    if (appRepository != NULL)
        AppRepository::closeStore();
    appRepository = NULL;
}

#define IS_APP_REPO_OPEN    (appRepository != NULL && appRepository->isReady())
/*
 * Class:     zina_ZinaNative
 * Method:    repoIsOpen
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
JNI_FUNCTION(repoIsOpen) (JNIEnv* env, jclass clazz)
{
    (void)clazz;
    (void)env;

    return static_cast<jboolean>(IS_APP_REPO_OPEN);
}


/*
 * Class:     zina_ZinaNative
 * Method:    existConversation
 * Signature: ([B)Z
 */
JNIEXPORT jboolean JNICALL
JNI_FUNCTION(existConversation) (JNIEnv* env, jclass clazz, jbyteArray namePattern)
{
    (void)clazz;

    string name;
    if (!arrayToString(env, namePattern, &name))
        return static_cast<jboolean>(false);

    if (!IS_APP_REPO_OPEN)
        return static_cast<jboolean>(false);

    bool result = appRepository->existConversation(name);
    return static_cast<jboolean>(result);
}

/*
 * Class:     zina_ZinaNative
 * Method:    storeConversation
 * Signature: ([B[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(storeConversation) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray convData)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return -1;

    string name;
    if (!arrayToString(env, inName, &name))
        return -1;

    string data;
    arrayToString(env, convData, &data);
    return appRepository->storeConversation(name, data);
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadConversation
 * Signature: ([B[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(loadConversation) (JNIEnv* env, jclass clazz, jbyteArray inName, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    string name;
    if (!arrayToString(env, inName, &name)) {
        setReturnCode(env, code, -1);
        return NULL;
    }

    string data;
    int32_t result = appRepository->loadConversation(name, &data);
    if (SQL_FAIL(result)) {
        setReturnCode(env, code, result);
        return NULL;
    }

    setReturnCode(env, code, result);
    jbyteArray retData = stringToArray(env, data);
    return retData;
}

/*
 * Class:     zina_ZinaNative
 * Method:    deleteConversation
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL 
JNI_FUNCTION(deleteConversation) (JNIEnv* env, jclass clazz, jbyteArray inName)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return -1;

    string name;
    if (!arrayToString(env, inName, &name)) {
        return -1;
    }
    return appRepository->deleteConversation(name);
}

/*
 * Class:     zina_ZinaNative
 * Method:    listConversations
 * Signature: ()[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(listConversations) (JNIEnv* env, jclass clazz)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    list<string>* convNames = appRepository->listConversations();

    if (convNames == NULL)
        return NULL;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(convNames->size()), byteArrayClass, NULL);

    int32_t index = 0;
    for (; !convNames->empty(); convNames->pop_front()) {
        const string& s = convNames->front();
        jbyteArray retData = stringToArray(env, s);
        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
    }
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    insertEvent
 * Signature: ([B[B[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(insertEvent) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId, jbyteArray eventData)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return -3;

    string name;
    if (!arrayToString(env, inName, &name)) {
        return -1;
    }
    string id;
    if (!arrayToString(env, eventId, &id)) {
        return -2;
    }
    string data;
    arrayToString(env, eventData, &data);
    return appRepository->insertEvent(name, id, data);
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadEvent
 * Signature: ([B[B[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(loadEvent) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 2)
        return NULL;

    string name;
    if (!arrayToString(env, inName, &name)) {
        setReturnCode(env, code, -1);
        return NULL;
    }
    string id;
    if (!arrayToString(env, eventId, &id)) {
        setReturnCode(env, code, -1);
        return NULL;
    }
    int32_t msgNumber = 0;
    string data;
    int32_t result = appRepository->loadEvent(name, id, &data, &msgNumber);
    if (SQL_FAIL(result)) {
        setReturnCode(env, code, result);
        return NULL;
    }
    setReturnCode(env, code, result, msgNumber);
    jbyteArray retData = stringToArray(env, data);
    return retData;
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadEventWithMsgId
 * Signature: ([B[I)[B
 */
JNIEXPORT jbyteArray JNICALL 
JNI_FUNCTION(loadEventWithMsgId) (JNIEnv* env, jclass clazz, jbyteArray eventId, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    string id;
    if (!arrayToString(env, eventId, &id)) {
        setReturnCode(env, code, -1);
        return NULL;
    }
    string data;
    int32_t result = appRepository->loadEventWithMsgId(id, &data);
    if (SQL_FAIL(result)) {
        setReturnCode(env, code, result);
        return NULL;
    }
    setReturnCode(env, code, result);
    jbyteArray retData = stringToArray(env, data);
    return retData;
}


/*
 * Class:     zina_ZinaNative
 * Method:    existEvent
 * Signature: ([B[B)Z
 */
JNIEXPORT jboolean JNICALL
JNI_FUNCTION(existEvent) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return static_cast<jboolean>(false);

    string name;
    if (!arrayToString(env, inName, &name)) {
        return static_cast<jboolean>(false);
    }
    string id;
    if (!arrayToString(env, eventId, &id)) {
        return static_cast<jboolean>(false);
    }
    bool result = appRepository->existEvent(name, id);
    return static_cast<jboolean>(result);
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadEvents
 * Signature: ([BII[I)[[B
 */
JNIEXPORT jobjectArray JNICALL 
JNI_FUNCTION(loadEvents) (JNIEnv* env, jclass clazz, jbyteArray inName, jint offset, jint number, jint direction, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 2)
        return NULL;

    string name;
    if (!arrayToString(env, inName, &name)) {
        setReturnCode(env, code, -1);
        return NULL;
    }

    int32_t msgNumber = 0;
    list<string*> events;
    int32_t result = appRepository->loadEvents(name, offset, number, direction, &events, &msgNumber);

    if (SQL_FAIL(result)) {
        setReturnCode(env, code, result);
        while (!events.empty()) {
            string* s = events.front();
            events.pop_front();
            delete s;
        }
        return NULL;
    }
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(events.size()), byteArrayClass, NULL);

    int32_t index = 0;
    while (!events.empty()) {
        string* s = events.front();
        events.pop_front();
        jbyteArray retData = stringToArray(env, *s);
        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
        delete s;
    }
    setReturnCode(env, code, result, msgNumber);
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    deleteEvent
 * Signature: ([B[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(deleteEvent) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return -1;

    string name;
    if (!arrayToString(env, inName, &name)) {
        return -1;
    }
    string id;
    if (!arrayToString(env, eventId, &id)) {
        return -1;
    }
    return appRepository->deleteEvent(name, id);
}

/*
 * Class:     zina_ZinaNative
 * Method:    deleteAllEvents
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(deleteAllEvents) (JNIEnv* env, jclass clazz, jbyteArray inName)
{
    (void)clazz;

    string name;
    if (!arrayToString(env, inName, &name) || name.empty()) {
        return -1;
    }

    // try to delete all associated objects first otherwise there can be constraint violation
    int rc = appRepository->deleteAttachmentStatusWithName(name);
    Log("deleteAllEvents: after removing attachment status: %d\n", rc);

    rc = appRepository->deleteObjectName(name);
    Log("deleteAllEvents: after removing attachment objects: %d\n", rc);

    rc = appRepository->deleteEventName(name);
    Log("deleteAllEvents: after removing events: %d\n", rc);

    return rc;
}

/*
 * Class:     zina_ZinaNative
 * Method:    insertObject
 * Signature: ([B[B[B[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(insertObject) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId, jbyteArray objId, jbyteArray objData)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return -1;

    string name;
    if (!arrayToString(env, inName, &name)) {
        return -1;
    }
    string event;
    if (!arrayToString(env, eventId, &event)) {
        return -1;
    }
    string id;
    if (!arrayToString(env, objId, &id)) {
        return -1;
    }
    string data;
    arrayToString(env, objData, &data);
    return appRepository->insertObject(name, event, id, data);
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadObject
 * Signature: ([B[B[B[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(loadObject) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId, jbyteArray objId, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    string name;
    if (!arrayToString(env, inName, &name) || name.empty()) {
        setReturnCode(env, code, -1);
        return NULL;
    }
    string event;
    if (!arrayToString(env, eventId, &event) || event.empty()) {
        setReturnCode(env, code, -1);
        return NULL;
    }
    string id;
    if (!arrayToString(env, objId, &id) || id.empty()) {
        return NULL;
    }
    string data;
    int32_t result = appRepository->loadObject(name, event, id, &data);
    if (SQL_FAIL(result)) {
        setReturnCode(env, code, result);
        return NULL;
    }
    setReturnCode(env, code, result);
    jbyteArray retData = stringToArray(env, data);
    return retData;
}

/*
 * Class:     zina_ZinaNative
 * Method:    existObject
 * Signature: ([B[B[B)Z
 */
JNIEXPORT jboolean JNICALL
JNI_FUNCTION(existObject) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId, jbyteArray objId)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return static_cast<jboolean>(false);

    string name;
    if (!arrayToString(env, inName, &name) || name.empty()) {
        return static_cast<jboolean>(false);
    }
    string event;
    if (!arrayToString(env, eventId, &event) || event.empty()) {
        return static_cast<jboolean>(false);
    }
    string id;
    if (!arrayToString(env, objId, &id) || id.empty()) {
        return static_cast<jboolean>(false);
    }
    return static_cast<jboolean>(appRepository->existObject(name, event, id));
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadObjects
 * Signature: ([B[B[I)[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(loadObjects) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    string name;
    if (!arrayToString(env, inName, &name) || name.empty()) {
        setReturnCode(env, code, -1);
        return NULL;
    }
    string event;
    if (!arrayToString(env, eventId, &event) || event.empty()) {
        setReturnCode(env, code, -1);
        return NULL;
    }
    list<string*> objects;
    int32_t result = appRepository->loadObjects(name, event, &objects);

    if (SQL_FAIL(result)) {
        setReturnCode(env, code, result);
        while (!objects.empty()) {
            string* s = objects.front();
            objects.pop_front();
            delete s;
        }
        return NULL;
    }
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(objects.size()), byteArrayClass, NULL);

    int32_t index = 0;
    while (!objects.empty()) {
        string* s = objects.front();
        objects.pop_front();
        jbyteArray retData = stringToArray(env, *s);
        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
        delete s;
    }
    setReturnCode(env, code, result);
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    deleteObject
 * Signature: ([B[B[B)I
 */
JNIEXPORT jint JNICALL 
JNI_FUNCTION(deleteObject) (JNIEnv* env, jclass clazz, jbyteArray inName, jbyteArray eventId, jbyteArray objId)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return -1;

    string name;
    if (!arrayToString(env, inName, &name) || name.empty()) {
        return -1;
    }
    string event;
    if (!arrayToString(env, eventId, &event) || event.empty()) {
        return -1;
    }
    string id;
    if (!arrayToString(env, objId, &id) || id.empty()) {
        return -1;
    }
    return appRepository->deleteObject(name, event, id);
}

/*
 * Class:     zina_ZinaNative
 * Method:    storeAttachmentStatus
 * Signature: ([B[BI)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(storeAttachmentStatus) (JNIEnv* env, jclass clazz, jbyteArray msgId, jbyteArray partnerName, jint status)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return 1;

    string messageId;
    if (!arrayToString(env, msgId, &messageId) || messageId.empty()) {
        return 1;    // 1 is the generic SQL error code
    }
    string pn;
    if (partnerName != NULL) {
        arrayToString(env, partnerName, &pn);
    }
    return appRepository->storeAttachmentStatus(messageId, pn, status);
}

/*
 * Class:     zina_ZinaNative
 * Method:    deleteAttachmentStatus
 * Signature: ([B[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(deleteAttachmentStatus) (JNIEnv* env, jclass clazz, jbyteArray msgId, jbyteArray partnerName)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return 1;

    string messageId;
    if (!arrayToString(env, msgId, &messageId) || messageId.empty()) {
        return 1;    // 1 is the generic SQL error code
    }
    string pn;
    if (partnerName != NULL) {
        arrayToString(env, partnerName, &pn);
    }
    return appRepository->deleteAttachmentStatus(messageId, pn);
}

/*
 * Class:     zina_ZinaNative
 * Method:    deleteWithAttachmentStatus
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(deleteWithAttachmentStatus) (JNIEnv* env, jclass clazz, jint status)
{
    (void)clazz;
    (void)env;

    if (!IS_APP_REPO_OPEN)
        return 1;

    return appRepository->deleteWithAttachmentStatus(status);
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadAttachmentStatus
 * Signature: ([B[B[I)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(loadAttachmentStatus) (JNIEnv* env, jclass clazz, jbyteArray msgId, jbyteArray partnerName, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return -1;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return -1;

    string messageId;
    if (!arrayToString(env, msgId, &messageId) || messageId.empty()) {
        setReturnCode(env, code, 1);   // 1 is the generic SQL error code
        return -1;
    }
    string pn;
    if (partnerName != NULL) {
        arrayToString(env, partnerName, &pn);
    }
    int32_t status;
    int32_t result = appRepository->loadAttachmentStatus(messageId, pn, &status);
    setReturnCode(env, code, result);
    return status;
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadMsgsIdsWithAttachmentStatus
 * Signature: (I[I)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(loadMsgsIdsWithAttachmentStatus) (JNIEnv* env, jclass clazz, jint status, jintArray code)
{
    (void)clazz;

    if (!IS_APP_REPO_OPEN)
        return NULL;

    if (code == NULL || env->GetArrayLength(code) < 1)
        return NULL;

    list<string> msgIds;
    int32_t result = appRepository->loadMsgsIdsWithAttachmentStatus(status, &msgIds);

    jclass stringArrayClass = env->FindClass("java/lang/String");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(msgIds.size()), stringArrayClass, NULL);

    int32_t index = 0;
    for (; !msgIds.empty(); msgIds.pop_front()) {
        const string& s = msgIds.front();
        jstring stringData = env->NewStringUTF(s.c_str());
        env->SetObjectArrayElement(retArray, index++, stringData);
        env->DeleteLocalRef(stringData);
    }
    setReturnCode(env, code, result);
    return retArray;
}


static uint8_t* jarrayToCarray(JNIEnv* env, jbyteArray array, size_t* len)
{
    *len = 0;
    if (array == NULL)
        return NULL;

    int tmpLen = env->GetArrayLength(array);
    if (tmpLen <= 0)
        return NULL;

    size_t dataLen = static_cast<size_t>(tmpLen);
    const uint8_t* tmp = (uint8_t*)env->GetByteArrayElements(array, 0);
    if (tmp == NULL)
        return NULL;

    uint8_t* buffer = (uint8_t*)malloc(dataLen);
    if (buffer == NULL)
        return NULL;

    *len = dataLen;
    memcpy(buffer, tmp, dataLen);
    env->ReleaseByteArrayElements(array, (jbyte*)tmp, 0);
    return buffer;
}


/*
 * Class:     zina_ZinaNative
 * Method:    cloudEncryptNew
 * Signature: ([B[B[B[I)J
 
 byte[] context, byte[] data, byte[] metaData, int[] errorCode
 */
JNIEXPORT jlong JNICALL
JNI_FUNCTION(cloudEncryptNew) (JNIEnv* env, jclass clazz, jbyteArray context, jbyteArray data, jbyteArray metaData, jintArray code)
{
    (void)clazz;

    SCloudContextRef scCtxEnc;

    setReturnCode(env, code, kSCLError_NoErr);

    // cloudFree calls free() to return the malloc'd data buffers created by jarrayToCarray
    size_t ctxLen;
    uint8_t* ctx = jarrayToCarray(env, context, &ctxLen);

    size_t dataLen;
    uint8_t* inData = jarrayToCarray(env, data, &dataLen);
    if (inData == NULL || dataLen == 0) {
        setReturnCode(env, code, kSCLError_BadParams);
        return 0L;
    }
    size_t metaLen;
    uint8_t* inMetaData = jarrayToCarray(env, metaData, &metaLen);
    if (inMetaData == NULL || metaLen == 0) {
        setReturnCode(env, code, kSCLError_BadParams);
        return 0L;
    }
    SCLError err = SCloudEncryptNew(ctx, ctxLen, (void*)inData, dataLen, (void*)inMetaData, metaLen,
                                    NULL, NULL, &scCtxEnc);
    if (err != kSCLError_NoErr) {
        setReturnCode(env, code, err);
        return 0L;
    }
    jlong retval = (jlong)scCtxEnc;
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudCalculateKey
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(cloudCalculateKey) (JNIEnv* env, jclass clazz, jlong cloudRef)
{
    (void)clazz;
    (void)env;

    SCloudContextRef scCtxEnc = (SCloudContextRef)cloudRef;

    SCLError err = SCloudCalculateKey(scCtxEnc, 0);
    return err;
}

static jbyteArray cArrayToJArray(JNIEnv* env, const uint8_t* input, size_t len)
{
    if (len == 0)
        return NULL;

    jbyteArray data = env->NewByteArray(static_cast<jsize>(len));
    if (data == NULL)
        return NULL;
    env->SetByteArrayRegion(data, 0, static_cast<jsize>(len), (jbyte*)input);
    return data;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudEncryptGetKeyBLOB
 * Signature: (J[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(cloudEncryptGetKeyBLOB) (JNIEnv* env, jclass clazz, jlong cloudRef, jintArray code)
{
    (void)clazz;

    SCLError err;
    uint8_t* blob = NULL;
    size_t blobSize = 0;

    setReturnCode(env, code, kSCLError_NoErr);

    SCloudContextRef scCtxEnc = (SCloudContextRef)cloudRef;

    err = SCloudEncryptGetKeyBLOB( scCtxEnc, &blob, &blobSize);

    if (err != kSCLError_NoErr) {
        setReturnCode(env, code, err);
        if (blob != NULL)
            free(blob);
        return NULL;
    }
    jbyteArray retval = cArrayToJArray(env, blob, blobSize);
    free(blob);
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudEncryptGetSegmentBLOB
 * Signature: (JI[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(cloudEncryptGetSegmentBLOB) (JNIEnv* env, jclass clazz, jlong cloudRef, jint segNum, jintArray code)
{
    (void)clazz;

    SCLError err;
    uint8_t* blob = NULL;
    size_t blobSize = 0;

    setReturnCode(env, code, kSCLError_NoErr);

    SCloudContextRef scCtxEnc = (SCloudContextRef)cloudRef;

    err = SCloudEncryptGetSegmentBLOB(scCtxEnc, segNum, &blob, &blobSize);

    if (err != kSCLError_NoErr) {
        setReturnCode(env, code, err);
        if (blob != NULL)
            free(blob);
        return NULL;
    }
    jbyteArray retval = cArrayToJArray(env, blob, blobSize);
    free(blob);
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudEncryptGetLocator
 * Signature: (J[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(cloudEncryptGetLocator) (JNIEnv* env, jclass clazz, jlong cloudRef, jintArray code)
{
    (void)clazz;

    SCLError err;
    uint8_t buffer[1024];
    size_t bufSize = 1024;

    setReturnCode(env, code, kSCLError_NoErr);

    SCloudContextRef scCtxEnc = (SCloudContextRef)cloudRef;

    err = SCloudEncryptGetLocator(scCtxEnc, buffer, &bufSize);
    if (err != kSCLError_NoErr) {
        setReturnCode(env, code, err);
        return NULL;
    }
    jbyteArray retval = cArrayToJArray(env, buffer, bufSize);
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudEncryptGetLocatorREST
 * Signature: (J[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(cloudEncryptGetLocatorREST) (JNIEnv* env, jclass clazz, jlong cloudRef, jintArray code)
{
    (void)clazz;

    SCLError err;
    uint8_t buffer[1024];
    size_t bufSize = 1024;

    setReturnCode(env, code, kSCLError_NoErr);

    SCloudContextRef scCtxEnc = (SCloudContextRef)cloudRef;

    err = SCloudEncryptGetLocatorREST(scCtxEnc, buffer, &bufSize);
    if (err != kSCLError_NoErr) {
        setReturnCode(env, code, err);
        return NULL;
    }
    jbyteArray retval = cArrayToJArray(env, buffer, bufSize);
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudEncryptNext
 * Signature: (J[I)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(cloudEncryptNext) (JNIEnv* env, jclass clazz, jlong cloudRef, jintArray code)
{
    (void)clazz;

    SCLError err;

    SCloudContextRef scCtxEnc = (SCloudContextRef)cloudRef;

    size_t required = SCloudEncryptBufferSize(scCtxEnc);
    jbyteArray data = env->NewByteArray(static_cast<jsize>(required));
    if (data == NULL) {
        setReturnCode(env, code, kSCLError_OutOfMemory);
        return NULL;
    }

    uint8_t* bigBuffer = (uint8_t*)env->GetByteArrayElements(data, 0);
    err = SCloudEncryptNext(scCtxEnc, bigBuffer, &required);
    setReturnCode(env, code, err);

    env->ReleaseByteArrayElements(data, (jbyte*)bigBuffer, 0);
    return data;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudDecryptNew
 * Signature: ([B[I)J
 */
JNIEXPORT jlong JNICALL
JNI_FUNCTION(cloudDecryptNew) (JNIEnv* env, jclass clazz, jbyteArray key, jintArray code)
{
    (void)clazz;
    (void)code;

    SCloudContextRef scCtxDec;

    string keyIn;
    if (!arrayToString(env, key, &keyIn))
        return 0L;

    SCloudDecryptNew((uint8_t*)keyIn.data(), keyIn.size(), NULL, NULL, &scCtxDec);
    jlong retval = (jlong)scCtxDec;
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudDecryptNext
 * Signature: (J[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(cloudDecryptNext) (JNIEnv* env, jclass clazz, jlong cloudRef, jbyteArray in)
{
    (void)clazz;

    SCLError err;
    SCloudContextRef scCtxDec = (SCloudContextRef)cloudRef;

    int tmpLen = env->GetArrayLength(in);
    if (tmpLen <= 0)
        return kSCLError_BadParams;

    size_t dataLen = static_cast<size_t>(tmpLen);
    uint8_t* data = (uint8_t*)env->GetByteArrayElements(in, 0);
    if (data == NULL) {
        return kSCLError_OutOfMemory;
    }
    err = SCloudDecryptNext(scCtxDec, data, dataLen);
    env->ReleaseByteArrayElements(in, (jbyte*)data, 0);
    return err;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudGetDecryptedData
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(cloudGetDecryptedData) (JNIEnv* env, jclass clazz, jlong cloudRef)
{
    (void)clazz;

    SCloudContextRef scCtxDec = (SCloudContextRef)cloudRef;

    uint8_t* dataBuffer = NULL;
    uint8_t* metaBuffer = NULL;
    size_t dataLen;
    size_t metaLen;

    SCloudDecryptGetData(scCtxDec, &dataBuffer, &dataLen, &metaBuffer, &metaLen);

    jbyteArray retval = cArrayToJArray(env, dataBuffer, dataLen);
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudGetDecryptedMetaData
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(cloudGetDecryptedMetaData) (JNIEnv* env, jclass clazz, jlong cloudRef)
{
    (void)clazz;

    SCloudContextRef scCtxDec = (SCloudContextRef)cloudRef;

    uint8_t* dataBuffer = NULL;
    uint8_t* metaBuffer = NULL;
    size_t dataLen;
    size_t metaLen;

    SCloudDecryptGetData(scCtxDec, &dataBuffer, &dataLen, &metaBuffer, &metaLen);

    jbyteArray retval = cArrayToJArray(env, metaBuffer, metaLen);
    return retval;
}

/*
 * Class:     zina_ZinaNative
 * Method:    cloudFree
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
JNI_FUNCTION(cloudFree) (JNIEnv* env, jclass clazz, jlong cloudRef)
{
    (void)clazz;
    (void)env;

    SCloudContextRef scCtx = (SCloudContextRef)cloudRef;
    SCloudFree(scCtx, 1);
}

/*
 * Class:     zina_ZinaNative
 * Method:    getUid
 * Signature: (Ljava/lang/String;[B)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
JNI_FUNCTION(getUid)(JNIEnv* env, jclass clazz, jstring alias, jbyteArray authorization)
{
    (void)clazz;

    string auth;
    if (!arrayToString(env, authorization, &auth) || auth.empty()) {
        if (zinaAppInterface == NULL)
            return NULL;
        auth = zinaAppInterface->getOwnAuthrization();
    }
    if (alias == NULL) {
        return NULL;
    }
    const char* aliasTmp = env->GetStringUTFChars(alias, 0);
    string aliasString(aliasTmp);
    env->ReleaseStringUTFChars(alias, aliasTmp);
    if (aliasString.empty())
        return NULL;

    NameLookup* nameCache = NameLookup::getInstance();
    string uid = nameCache->getUid(aliasString, auth);

    if (uid.empty())
        return NULL;

    jstring uidJava = env->NewStringUTF(uid.c_str());
    return uidJava;
}

static string  createUserInfoJson(shared_ptr<UserInfo> userInfo)
{
    cJSON* root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "uid", userInfo->uniqueId.c_str());
    cJSON_AddStringToObject(root, "display_name", userInfo->displayName.c_str());
    cJSON_AddStringToObject(root, "alias0", userInfo->alias0.c_str());
    cJSON_AddStringToObject(root, "lookup_uri", userInfo->contactLookupUri.c_str());
    cJSON_AddStringToObject(root, "avatar_url", userInfo->avatarUrl.c_str());
    cJSON_AddStringToObject(root, "display_organization", userInfo->organization.c_str());
    cJSON_AddBoolToObject(root, "same_organization", userInfo->inSameOrganization);
    cJSON_AddStringToObject(root, RETENTION_ORG, userInfo->retainForOrg.c_str());
    cJSON_AddBoolToObject(root, "dr_enabled", userInfo->drEnabled);

    cJSON_AddBoolToObject(root, RRMM, userInfo->drRrmm);
    cJSON_AddBoolToObject(root, RRMP, userInfo->drRrmp);
    cJSON_AddBoolToObject(root, RRCM, userInfo->drRrcm);
    cJSON_AddBoolToObject(root, RRCP, userInfo->drRrcp);
    cJSON_AddBoolToObject(root, RRAP, userInfo->drRrap);

    char *out = cJSON_PrintUnformatted(root);
    string json(out);
    cJSON_Delete(root); free(out);
    return json;
}

static jbyteArray getUserInfoInternal(JNIEnv* env, jstring alias, jbyteArray authorization, bool cacheOnly, int32_t* errorCode)
{
    string auth;
    if (!arrayToString(env, authorization, &auth) || auth.empty()) {
        if (zinaAppInterface == NULL) {
            *errorCode = GENERIC_ERROR;
            return NULL;
        }
        auth = zinaAppInterface->getOwnAuthrization();
    }
    if (alias == NULL) {
        *errorCode = GENERIC_ERROR;
        return NULL;
    }
    const char* aliasTmp = env->GetStringUTFChars(alias, 0);
    string aliasString(aliasTmp);
    env->ReleaseStringUTFChars(alias, aliasTmp);
    if (aliasString.empty()) {
        *errorCode = GENERIC_ERROR;
        return NULL;
    }

    NameLookup* nameCache = NameLookup::getInstance();
    shared_ptr<UserInfo> userInfo = nameCache->getUserInfo(aliasString, auth, cacheOnly, errorCode);

    if (!userInfo)
        return NULL;

    jbyteArray retData = stringToArray(env, createUserInfoJson(userInfo));
    return retData;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getUserInfo
 * Signature: (Ljava/lang/String;[B)Ljava/lang/String;
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(getUserInfo)(JNIEnv* env, jclass clazz, jstring alias, jbyteArray authorization, jintArray code)
{
    (void)clazz;
    int32_t errorCode = 0;

    jbyteArray retData = getUserInfoInternal(env, alias, authorization, false, &errorCode);

    if (code != NULL && env->GetArrayLength(code) >= 1) {
        setReturnCode(env, code, errorCode);
    }
    return retData;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getUserInfoFromCache
 * Signature: (Ljava/lang/String;[B)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(getUserInfoFromCache)(JNIEnv* env, jclass clazz, jstring alias)
{
    (void)clazz;
    int32_t errorCode = 0;
    return getUserInfoInternal(env, alias, NULL, true, &errorCode);
}

/*
 * Class:     zina_ZinaNative
 * Method:    refreshUserData
 * Signature: (Ljava/lang/String;[B)[B
 */
JNIEXPORT jbyteArray
JNICALL JNI_FUNCTION(refreshUserData)(JNIEnv* env, jclass clazz, jstring alias, jbyteArray authorization)
{
    (void)clazz;

    string auth;
    if (!arrayToString(env, authorization, &auth) || auth.empty()) {
        if (zinaAppInterface == NULL)
            return NULL;
        auth = zinaAppInterface->getOwnAuthrization();
    }
    if (alias == NULL) {
        return NULL;
    }
    const char* aliasTmp = env->GetStringUTFChars(alias, 0);
    string aliasString(aliasTmp);
    env->ReleaseStringUTFChars(alias, aliasTmp);
    if (aliasString.empty())
        return NULL;

    NameLookup* nameCache = NameLookup::getInstance();
    shared_ptr<UserInfo> userInfo = nameCache->refreshUserData(aliasString, auth);

    if (!userInfo)
        return NULL;

    jbyteArray retData = stringToArray(env, createUserInfoJson(userInfo));
    return retData;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getAliases
 * Signature: (Ljava/lang/String;[B)[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(getAliases)(JNIEnv* env, jclass clazz, jstring uuid)
{
    (void)clazz;

    if (uuid == NULL) {
        return NULL;
    }
    const char* uuidTemp = env->GetStringUTFChars(uuid, 0);
    string uuidString(uuidTemp);
    env->ReleaseStringUTFChars(uuid, uuidTemp);
    if (uuidString.empty())
        return NULL;

    NameLookup* nameCache = NameLookup::getInstance();
    shared_ptr<list<string> > aliases = nameCache->getAliases(uuidString);
    if (!aliases)
        return NULL;
    size_t size = aliases->size();
    if (size == 0)
        return NULL;

    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(size), byteArrayClass, NULL);

    int32_t index = 0;
    for (; !aliases->empty(); aliases->pop_front()) {
        const string& s = aliases->front();
        ;
        jbyteArray retData = stringToArray(env, s);
        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
    }
    return retArray;
}

/*
 * Class:     zina_ZinaNative
 * Method:    addAliasToUuid
 * Signature: (Ljava/lang/String;Ljava/lang/String;[B[B)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(addAliasToUuid)(JNIEnv* env, jclass clazz, jstring alias, jstring uuid, jbyteArray userData)
{
    (void)clazz;

    if (uuid == NULL) {
        return NameLookup::MissingParameter;
    }
    const char* uuidTemp = env->GetStringUTFChars(uuid, 0);
    string uuidString(uuidTemp);
    env->ReleaseStringUTFChars(uuid, uuidTemp);
    if (uuidString.empty())
        return NameLookup::MissingParameter;

    if (alias == NULL) {
        return NameLookup::MissingParameter;
    }
    const char* aliasTmp = env->GetStringUTFChars(alias, 0);
    string aliasString(aliasTmp);
    env->ReleaseStringUTFChars(alias, aliasTmp);
    if (aliasString.empty())
        return NameLookup::MissingParameter;

    string data;
    if (!arrayToString(env, userData, &data))
        return NameLookup::MissingParameter;

    NameLookup* nameCache = NameLookup::getInstance();
    NameLookup::AliasAdd ret = nameCache->addAliasToUuid(aliasString, uuidString, data);
    return ret;
}

/*
 * Class:     zina_ZinaNative
 * Method:    getDisplayName
 * Signature: (Ljava/lang/String;[B)[B
 */
JNIEXPORT jbyteArray JNICALL
JNI_FUNCTION(getDisplayName)(JNIEnv* env, jclass clazz, jstring uuid)
{
    (void)clazz;

    if (uuid == NULL) {
        return NULL;
    }
    const char* uuidTemp = env->GetStringUTFChars(uuid, 0);
    string uuidString(uuidTemp);
    env->ReleaseStringUTFChars(uuid, uuidTemp);
    if (uuidString.empty())
        return NULL;

    NameLookup* nameCache = NameLookup::getInstance();
    shared_ptr<string> displayName = nameCache->getDisplayName(uuidString);
    if (!displayName)
        return NULL;
    jbyteArray retData = stringToArray(env, *displayName);
    return retData;
}

/*
 * Class:     zina_ZinaNative
 * Method:    loadCapturedMsgs
 * Signature: ([B[B[B[I)[[B
 */
JNIEXPORT jobjectArray JNICALL
JNI_FUNCTION(loadCapturedMsgs)(JNIEnv* env, jclass clazz, jbyteArray name, jbyteArray msgId, jbyteArray devId, jintArray code)
{
    (void)clazz;

    if (zinaAppInterface == NULL) {
        if (code != NULL && env->GetArrayLength(code) >= 1) {
            setReturnCode(env, code, GENERIC_ERROR);
        }
        return NULL;
    }

    string nameString;
    arrayToString(env, name, &nameString);

    string msgIdString;
    arrayToString(env, msgId, &msgIdString);

    string devIdString;
    arrayToString(env, devId, &devIdString);

    SQLiteStoreConv &store = *zinaAppInterface->getStore();
    list<StringUnique> records;
    int32_t errorCode = store.loadMsgTrace(nameString, msgIdString, devIdString, records);

    if (code != NULL && env->GetArrayLength(code) >= 1) {
        setReturnCode(env, code, errorCode);
    }
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray retArray = env->NewObjectArray(static_cast<jsize>(records.size()), byteArrayClass, NULL);

    int32_t index = 0;
    for (; !records.empty(); records.pop_front()) {
        const string& s = *records.front();
        jbyteArray retData = stringToArray(env, s);
        env->SetObjectArrayElement(retArray, index++, retData);
        env->DeleteLocalRef(retData);
    }
    return retArray;
}

/*
 * Class:     axolotl_AxolotlNative
 * Method:    sendDrMessageData
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JJLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
JNI_FUNCTION(sendDrMessageData)(JNIEnv* env, jclass clazz, jstring callid, jstring direction, jstring recipient, jlong composedTime, jlong sentTime, jstring message)
{
    (void)clazz;

    if (callid == NULL || direction == NULL || recipient == NULL || message == NULL) {
        return;
    }

    const char* callidTemp = env->GetStringUTFChars(callid, 0);
    string callidString(callidTemp);
    env->ReleaseStringUTFChars(callid, callidTemp);

    const char* directionTemp = env->GetStringUTFChars(direction, 0);
    string directionString(directionTemp);
    env->ReleaseStringUTFChars(direction, directionTemp);
    if (directionString.empty())
        return;

    const char* recipientTemp = env->GetStringUTFChars(recipient, 0);
    string recipientString(recipientTemp);
    env->ReleaseStringUTFChars(recipient, recipientTemp);
    if (recipientString.empty())
        return;

    const char* messageTemp = env->GetStringUTFChars(message, 0);
    string messageString(messageTemp);
    env->ReleaseStringUTFChars(message, messageTemp);
    if (messageString.empty())
        return;

    ScDataRetention::sendMessageData(callidString, directionString, recipientString, static_cast<long>(composedTime / 1000), static_cast<long>(sentTime / 1000), messageString);
}

/*
 * Class:     axolotl_AxolotlNative
 * Method:    sendDrMessageMetadata
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JJ)V
 */
JNIEXPORT void JNICALL
JNI_FUNCTION(sendDrMessageMetadata)(JNIEnv* env, jclass clazz, jstring callid, jstring direction, jstring recipient, jlong composedTime, jlong sentTime)
{
    (void)clazz;

    if (callid == NULL || direction == NULL || recipient == NULL) {
        return;
    }

    const char* callidTemp = env->GetStringUTFChars(callid, 0);
    string callidString(callidTemp);
    env->ReleaseStringUTFChars(callid, callidTemp);

    const char* directionTemp = env->GetStringUTFChars(direction, 0);
    string directionString(directionTemp);
    env->ReleaseStringUTFChars(direction, directionTemp);
    if (directionString.empty())
        return;

    const char* recipientTemp = env->GetStringUTFChars(recipient, 0);
    string recipientString(recipientTemp);
    env->ReleaseStringUTFChars(recipient, recipientTemp);
    if (recipientString.empty())
        return;

    ScDataRetention::sendMessageMetadata(callidString, directionString, DrLocationData(), DrAttachmentData(), recipientString, static_cast<long>(composedTime / 1000), static_cast<long>(sentTime / 1000));
}

/*
 * Class:     axolotl_AxolotlNative
 * Method:    sendDrInCircleCallMetadata
 * Signature: (Ljava/lang/String;ZLjava/lang/String;JJ)V
 */
JNIEXPORT void JNICALL
JNI_FUNCTION(sendDrInCircleCallMetadata)(JNIEnv * env, jclass clazz, jstring callid, jboolean isIncoming, jstring recipient, jlong start, jlong end)
{
    (void)clazz;

    if (callid == NULL || recipient == NULL) {
        return;
    }

    const char* callidTemp = env->GetStringUTFChars(callid, 0);
    string callidString(callidTemp);
    env->ReleaseStringUTFChars(callid, callidTemp);

    const char* recipientTemp = env->GetStringUTFChars(recipient, 0);
    string recipientString(recipientTemp);
    env->ReleaseStringUTFChars(recipient, recipientTemp);
    if (recipientString.empty())
        return;

    ScDataRetention::sendInCircleCallMetadata(callidString, static_cast<bool>(isIncoming) ? "received" : "placed", recipientString, static_cast<long>(start / 1000), static_cast<long>(end / 1000));
}

/*
 * Class:     axolotl_AxolotlNative
 * Method:    sendDrSilentWorldCallMetadata
 * Signature: (Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;JJ)V
 */
JNIEXPORT void JNICALL
JNI_FUNCTION(sendDrSilentWorldCallMetadata)(JNIEnv * env, jclass clazz, jstring callid, jboolean isIncoming, jstring srcTn, jstring dstTn, jlong start, jlong end)
{
    (void)clazz;

    if (callid == NULL || srcTn == NULL || dstTn == NULL) {
        return;
    }

    const char* callidTemp = env->GetStringUTFChars(callid, 0);
    string callidString(callidTemp);
    env->ReleaseStringUTFChars(callid, callidTemp);

    const char* srcTnTemp = env->GetStringUTFChars(srcTn, 0);
    string srcTnString(srcTnTemp);
    env->ReleaseStringUTFChars(srcTn, srcTnTemp);
    if (srcTnString.empty())
        return;

    const char* dstTnTemp = env->GetStringUTFChars(dstTn, 0);
    string dstTnString(dstTnTemp);
    env->ReleaseStringUTFChars(dstTn, dstTnTemp);
    if (dstTnString.empty())
        return;

    ScDataRetention::sendSilentWorldCallMetadata(callidString, static_cast<bool>(isIncoming) ? "received" : "placed", srcTnString, dstTnString, static_cast<long>(start / 1000), static_cast<long>(end / 1000));
}

/*
 * Class:     axolotl_AxolotlNative
 * Method:    processPendingDrRequests
 * Signature: ()V
 */
JNIEXPORT void JNICALL
JNI_FUNCTION(processPendingDrRequests)(JNIEnv * env, jclass clazz)
{
    (void)env;
    (void)clazz;

    ScDataRetention::processRequests();
}

/*
 * Class:     axolotl_AxolotlNative
 * Method:    isDrEnabled
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
JNI_FUNCTION(isDrEnabled)(JNIEnv * env, jclass clazz)
{
    (void)env;
    (void)clazz;

    bool enabled = false;
    ScDataRetention::isEnabled(&enabled);
    return static_cast<jboolean>(enabled);
}

/*
 * Class:     axolotl_AxolotlNative
 * Method:    isDrEnabledForUser
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
JNI_FUNCTION(isDrEnabledForUser)(JNIEnv * env, jclass clazz, jstring user)
{
    (void)clazz;

    bool enabled = false;

    const char* userTemp = env->GetStringUTFChars(user, 0);
    string userString(userTemp);
    env->ReleaseStringUTFChars(user, userTemp);

    ScDataRetention::isEnabled(userString, &enabled);
    return static_cast<jboolean>(enabled);
}

/*
 * Class:     zina_ZinaNative
 * Method:    setDataRetentionFlags
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
JNI_FUNCTION(setDataRetentionFlags)(JNIEnv* env, jclass clazz, jstring flags)
{
    (void)clazz;

    if (zinaAppInterface == NULL)
        return -1;

    if (flags == nullptr) {
        return DATA_MISSING;
    }
    const char* flagsTemp = env->GetStringUTFChars(flags, 0);
    string flagsString(flagsTemp);
    env->ReleaseStringUTFChars(flags, flagsTemp);

    return zinaAppInterface->setDataRetentionFlags(flagsString);
}
