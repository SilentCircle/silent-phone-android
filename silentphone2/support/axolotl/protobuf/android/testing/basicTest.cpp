/*
Copyright 2016 Silent Circle, LLC

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
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdio.h>

#include <android/log.h>

#include "testmessage.pb.h"
#include "com_example_werner_NativeTester.h"

using namespace std;
static void androidLog(char const *format, va_list arg) {
    __android_log_vprint(ANDROID_LOG_DEBUG, "prototest", format, arg);
}

static void androidLog(const char* format, ...)
{
    va_list arg;
    va_start(arg, format);
    androidLog(format, arg);
    va_end( arg );
}


void simpleTest(const void* buffer, int length)
{
    char printbuf[200];
    if (buffer == NULL) {
        androidLog("Supplied buffer is NULL");
        return;
    }
    if (length <= 0) {
        androidLog("Supplied length is to small: %d", length);
        return;
    }

    test::protobuf::Textmessage inputMessage;
    inputMessage.ParseFromArray(buffer, length);

    if (inputMessage.standardint32() != 815) {
        androidLog("standardint32 does not match: expected 815, actual: %d", inputMessage.standardint32());
    }
    else {
        androidLog("Checked standardint32: 815 == %d, OK", inputMessage.standardint32());
    }

    if (inputMessage.longint64() != 4711471147114711L) {
        androidLog("longint64 does not match: expected 4711471147114711, actual: %ld", inputMessage.longint64());
    }
    else {
        // Using sprintf here: the compiler checks the format and spits out warnings on different platforms/CPU architecures
        sprintf(printbuf, "Checked longint64: 4711471147114711 == %lld, OK", inputMessage.longint64());
        androidLog("%s", printbuf);
    }
    string inputString = inputMessage.sometext();
    if (strcmp(inputString.c_str(), "test message") != 0) {
        androidLog("sometext does not match: expected 'test message', actual: '%s'", inputString.c_str());
    }
    else {
        androidLog("Checked sometext: 'test message' == '%s', OK", inputString.c_str());
    }

    test::protobuf::Textmessage checkMessage;
    checkMessage.set_longint64(4711471147114711L);
    checkMessage.set_standardint32(815);
    checkMessage.set_sometext("test message");

    string checkString;
    checkMessage.SerializeToString(&checkString);
    size_t checkLength = checkString.length();
    unsigned char* checkBuffer = new unsigned char[checkLength];
    checkString.copy((char*)checkBuffer, string::npos);

    if (memcmp((void*)buffer, (void*)checkBuffer, checkLength) != 0) {
        androidLog("Serialized buffers do not match");
    }
    else {
        androidLog("Serialized buffers match, OK");
    }
}

/*
 * Class:     com_example_werner_NativeTester
 * Method:    testProtoBuffer
 * Signature: ([B)Z
 */
JNIEXPORT jboolean JNICALL Java_com_example_werner_NativeTester_testProtoBuffer(JNIEnv *env, jclass thizz, jbyteArray byteArray)
{
    unsigned char *buffer = (unsigned char *)env->GetByteArrayElements(byteArray, 0);
    jsize length = env->GetArrayLength(byteArray);

    androidLog("JNI buffer length: %d", length);
    simpleTest(buffer, length);
    env->ReleaseByteArrayElements(byteArray, (jbyte*)buffer, 0);
    return JNI_TRUE;
}

