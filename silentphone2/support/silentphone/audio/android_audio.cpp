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

#include <stdio.h>
#include <android/log.h>
#include <unistd.h>
#include <math.h>
#include <stdarg.h>

#include <pthread.h>

#include <string.h>
#include <malloc.h>

#include <jni.h>

#define ENCODING_PCM_16BIT 0x00000002

#define CHANNEL_IN_MONO 0x00000010
#define CHANNEL_OUT_MONO 0x04
#define CHANNEL_IN_STEREO 0x0000000c

#define STREAM_VOICE_CALL 0
#define STREAM_MUSIC 3

//SAMPLE_WR must be mult of 160
//320 = 20ms 16khz
#define SAMPLE_WR 320

static jobject oTrack=NULL;
static jclass cAudioTrack=NULL;

static jmethodID mAudioTrack;

static jmethodID mPlay;
static jmethodID mWrite;
static jmethodID mStop;
static jmethodID mGetMinBufferSize;


static int iBufferSizeInBytes=100;
static int iBufferSizeInBytesReal=100;
static int iBufferSizeInBytesRealRec=100;
static int iResult=0;

static int iGPlaying=0;
static int iRecorderState = 0;


typedef struct {
    int (*cbFnc) (void *callback_data, short *samples, int frames, int iRestarted);
    void *cb;
    int iRate;
} TNX;

static TNX tx;

int getEchoEnableState();
int getAPILevel();
JavaVM *t_getJavaVM();
int getGainReduction();

void updatePlayback(void *p, short *play);
void updateAndFixMic(void *p, short *rec);

void initAudioClassHelper(JNIEnv *env);
int androidLog(char const *format, ...);

static void t_nativesound_play(JNIEnv *env){
    if (oTrack == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "oTrack = NULL");
        iResult = 2;
    }
    else
        env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mPlay);
}

static int t_nativesound_write_arr(JNIEnv *env, jshortArray samples, int ofs, int n) {
    if (oTrack)
        return (env)->CallNonvirtualIntMethod(oTrack, cAudioTrack, mWrite, samples, ofs, n);
    return 0;
}


#define A_O_P_THREAD_PRIORITY_AUDIO -16

void setThHighPriority(JNIEnv *env){
    jclass c;
    jmethodID m;
    jthrowable exc;

    c = (jclass)env->NewGlobalRef(env->FindClass("android/os/Process"));
    if (!c) 
        return;

    m = env->GetStaticMethodID(c, "setThreadPriority", "(I)V");
    if (!m) 
        goto err;

    env->CallStaticVoidMethod(c, m, A_O_P_THREAD_PRIORITY_AUDIO);

    exc = env->ExceptionOccurred();
    if (exc) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
err:
    if (c)
        env->DeleteGlobalRef(c);
}


static int iCurrentHWSampleRate=0;
static int iNextHWSampleRate=0;

static void t_setCurrentHWSampleRate(int iRate){
   iCurrentHWSampleRate=iRate;
   iNextHWSampleRate=0;
}

int t_setNextHWSampleRate(int iRate){
    if (iCurrentHWSampleRate == iRate)
        return 0;
    if(iRate != 16000 && iRate != 48000){
        __android_log_print(ANDROID_LOG_WARN, "native", "set_hw_rate() fail - iRate!=16000 && iRate!=48000");
        return -1;
    }
    iNextHWSampleRate=iRate;
    return 0;
}

int t_getSampleRate(){
    return iCurrentHWSampleRate;
}

static jobject audioRecordSp = NULL;
static jobject audioTrackSp = NULL;


/* We call this helper class during JNI_OnLoad (refer to jni_glue2.cpp) to lookup and prepare the
 * Java audio helper classes. It works as follows:
 *
 * We need some way to cache a reference to a class, because native threads do not have access 
 * to a functional classloader. We can't cache the class references themselves, as it makes JVM unhappy.
 * Instead we cache instances of these classes (objects), so that we can later retrieve class references 
 * using GetObjectClass() JNI function. 
 * One thing to remember is that these objects must be protected from garbage-collecting using 
 * NewGlobalRef(), as that guarantees that they will remain available to different threads during 
 * JVM lifetime. Creating the instances and storing them in the global variables is the job for 
 * the initAudioClassHelper() function.
 * 
 * Because JNI_onload call this the funtion returns to the jvm and we don't need to explicitly
 * delete local references.
 */
void initAudioClassHelper(JNIEnv *env, const char* path, bool record)
{
    // Get the audio recorder helper class
    jclass cls = env->FindClass(path);
    if (!cls) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "initAudioClassHelper: failed to get %s class reference", path);
        return;
    }
    jmethodID constr = env->GetMethodID(cls, "<init>", "()V");
    if (!constr) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "initAudioClassHelper: failed to get %s constructor", path);
        return;
    }
    jobject obj = env->NewObject(cls, constr);
    if (!obj) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "initAudioClassHelper: failed to create a %s object", path);
        return;
    }
    if (record)
        audioRecordSp = env->NewGlobalRef(obj);
    else
        audioTrackSp = env->NewGlobalRef(obj);
}

static int t_nativesound_open(JNIEnv *env, int iInitRate){
    oTrack=NULL;
    if (!cAudioTrack) {

        jclass jcls = env->GetObjectClass(audioTrackSp);
        cAudioTrack = (jclass)env->NewGlobalRef(jcls);
        env->DeleteLocalRef(jcls);

        if (!cAudioTrack) {
            iResult=1;
            __android_log_print(ANDROID_LOG_ERROR, "native", "cAudioTrack = NULL");
            return -1;
        }
        mAudioTrack = env->GetMethodID(cAudioTrack, "<init>", "(IIII)V");
        mPlay = env->GetMethodID(cAudioTrack, "play", "()V");
        mWrite = env->GetMethodID(cAudioTrack, "write", "([SII)I");
        mStop = env->GetMethodID(cAudioTrack, "stop", "()V");
        mGetMinBufferSize = env->GetStaticMethodID(cAudioTrack, "getMinBufferSize", "(III)I");
    }
    int sampleRateInHz = iInitRate; //16000 did not work on some devices

    iBufferSizeInBytes = env->CallStaticIntMethod(cAudioTrack, mGetMinBufferSize, sampleRateInHz, CHANNEL_OUT_MONO, ENCODING_PCM_16BIT);
    iBufferSizeInBytesReal = iBufferSizeInBytes;

    if(iBufferSizeInBytes<SAMPLE_WR*12)iBufferSizeInBytes=SAMPLE_WR*12;

    androidLog("iBufferSizeInBytes r=%d c=%d", iBufferSizeInBytesReal,iBufferSizeInBytes);

    jobject loc = env->NewObject(cAudioTrack, mAudioTrack, sampleRateInHz, CHANNEL_OUT_MONO, ENCODING_PCM_16BIT, iBufferSizeInBytes);
    if(!loc)return -1;

    oTrack = env->NewGlobalRef(loc);
    env->DeleteLocalRef(loc);

    t_setCurrentHWSampleRate(iInitRate);

    return 0;
}

static void t_nativesound_close(JNIEnv *env){
    if (!oTrack)
        return;
    env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mStop);
    env->DeleteGlobalRef(oTrack);
    oTrack=NULL;
}


class CTPlayBackDataQ{
    enum{eSize=32};
    short data[SAMPLE_WR * eSize];
    int iPlayPos;
    int iReadPos;

public:
    CTPlayBackDataQ(){reset();}
    void reset(){iPlayPos=1;iReadPos=0;}
    void add(short *p){
        if(iPlayPos<iReadPos)iPlayPos=iReadPos+1;
        int w=iPlayPos&(eSize-1);
        memcpy(&data[w*SAMPLE_WR],p,(SAMPLE_WR)*sizeof(short));
        iPlayPos++;
    }
    int in(){return iPlayPos-iReadPos;}
    short *get(){int r=iReadPos&(eSize-1);iReadPos++;return &data[r*SAMPLE_WR];}
};

CTPlayBackDataQ echoList;

static void t_native_startCBX(int iRate, int (*cbFnc) (void *callback_data, short *samples, int frames, int iRestarted), void *cbData){
    JNIEnv *env=NULL;
    if(!iGPlaying)
        return;

    int iPlaying=0;
    int iSamplesInBuf=0;
    int iReset=1;
    int iIsRestarted=1;
    int iRateMult;

    int prev=0;
    short inb[SAMPLE_WR];
    int iHWSampleRateToUse = 0;
    int gainShift = getGainReduction();

    jshort *buf = NULL;
    jshortArray bytearray = NULL;

    int iWriteBufSize = SAMPLE_WR;

    short *rp;

    JavaVM *vm = t_getJavaVM();
    androidLog("th play ok");

    vm->GetEnv((void**)&env,JNI_VERSION_1_6);
    vm->AttachCurrentThread(&env, NULL);

    bytearray = env->NewShortArray(SAMPLE_WR * 6);
    if(bytearray)
        buf = env->GetShortArrayElements(bytearray, NULL);

    if (!buf)
        goto exit_th;

    setThHighPriority(env);

    while(iGPlaying){
        iReset=1;
        iIsRestarted=1;
        iPlaying=0;

        if ((iRate==16000 && iNextHWSampleRate != 16000)|| iNextHWSampleRate==48000){
            iWriteBufSize = SAMPLE_WR * 3;
            iRateMult=3;
            rp=&inb[0];
            iHWSampleRateToUse=48000;
        }
        else {
            iWriteBufSize = SAMPLE_WR;
            iRateMult=1;
            rp=&buf[0];
            iHWSampleRateToUse = iRate;
        }

        {
            int e = t_nativesound_open(env, iHWSampleRateToUse);
            androidLog("%d=native_play_open(hw=%d r=%d)", e, iHWSampleRateToUse,iRate);

            if(e)
                goto exit_th;
        }
        while(iGPlaying){
            int w;
            int d;

            if (iNextHWSampleRate)
                break;
            if (cbFnc(cbData, rp ,SAMPLE_WR, iIsRestarted) < 0) {
                iGPlaying = 0;
                break;

            }
            iIsRestarted = 0;

            if(iRateMult == 3) {
                for(int z = 0; z < SAMPLE_WR; z++){
                    int cur = rp[z] >> gainShift;
                    buf[z*3] = ((prev*11+cur*5+8) >> 4);
                    buf[z*3+1] = ((prev*5+cur*11+8) >> 4);
                    buf[z*3+2] = cur;
                    prev = cur;
                }
            }
            d=0;

            env->SetShortArrayRegion(bytearray, 0, iWriteBufSize, buf);
            w = t_nativesound_write_arr(env, bytearray, 0, iWriteBufSize);

            if (w > 0) {
                iSamplesInBuf += w;
                d += w;
            }

            if (d < iWriteBufSize){
                do {
                    usleep(2000);
                    w = t_nativesound_write_arr(env, bytearray ,d,iWriteBufSize-d);
                    if(w < 0){
                        __android_log_print(ANDROID_LOG_WARN, "native", "write()=%d failed, th play", w);
                        break;
                    }
                    iSamplesInBuf += w;
                    d += w;
                } while(0);
            }
            if (iReset)
                echoList.reset();
            iReset=0;
            echoList.add((short*)rp);

            if (!iPlaying){
                if(/*iSamplesInBuf*2<iBufferSizeInBytesReal &&*/ w < iWriteBufSize){
                    continue;
                }
                androidLog("th try play");
                t_nativesound_play(env);
                iIsRestarted=1;
                iPlaying=1;
            }
        }
        androidLog("th ap try stop");
        t_nativesound_close(env);

        if(!iNextHWSampleRate)
          break;
   }
   if (bytearray && buf)
       env->ReleaseShortArrayElements(bytearray, buf, 0);
   if (bytearray)
       env->DeleteLocalRef(bytearray);

exit_th:
    vm->DetachCurrentThread();
    androidLog("th ap exit");
    t_setCurrentHWSampleRate(0);
}

static int thFncA(void *a) {
    t_native_startCBX(tx.iRate, tx.cbFnc,tx.cb);
    return 0;
}

void t_onOverFlow(void *ctx){}

void startThX(int (cbFnc)(void *p),void *data);

void *t_native_PstartCB(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
    return NULL;
}

void *t_native_startCB_play(void *ctx, int iRate, int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData){
    iGPlaying = 1;
    tx.cbFnc = cbFnc;
    tx.cb = cbData;
    tx.iRate = iRate;
    startThX(thFncA,&tx);
    return (void*)1;
}

void t_native_stop() {
    iGPlaying = 0;
}

void t_native_stop(void *ctx, int iPlay){
    void t_native_stop_rec();
    if (iPlay)
        t_native_stop();
    else 
        t_native_stop_rec();
}

void t_native_rel(void *ctx){
   return;
}

void *getAudioCtx(int iRate, int iRel){
   return (void*)1;
}

void *getPAudioCtx(int iRate){
   return (void*)1;
}

void t_native_Prel(void *ctx){
   return;
}

void t_native_Pstop(void *ctx){
   return;
}

int t_native_PRate(void *ctx){
   return 8000;
}

/* *************************************************
 * Recording functions
 ***************************************************/

static jclass cAudioRecord=NULL;

static jobject mrec=NULL;

static jmethodID mAudioRecord;
static jmethodID mGetMinBufferSizeRec;
static jmethodID mStartRecording;
static jmethodID mStopRec;
static jmethodID mReleaseRec;
static jmethodID mRead;

static jmethodID mGetAudioSessionId=NULL;

static int iRecBufSizeInBytes = 100;

static int iGRecording = 0;

typedef struct {
    int (*cbFnc) (void *callback_data, short *samples, int frames);
    void *cb;
    int iRate;
} TNXR;

static TNXR txRec;

void *initAEC(int iRate);
int playbackBufAEC(void *aec, short *p, int iSamples);
int recordProcessAEC(void *aec, short *p, short *outBuf, int iSamples, int delayMS);


#define CHECK_CLR_EXC \
if(env->ExceptionOccurred() != NULL) {\
env->ExceptionDescribe();\
env->ExceptionClear();\
}

#define SAMPLE_REC_CNT  SAMPLE_WR

static void t_nativesound_rec(JNIEnv *env){
    env->CallNonvirtualVoidMethod(mrec, cAudioRecord, mStartRecording);
}

static int t_nativesound_read_arr(JNIEnv *env, jshortArray samples, int ofs, int n) {
    if (mrec)
        return (env)->CallNonvirtualIntMethod(mrec, cAudioRecord, mRead, samples, ofs, n);
    return 0;
}

static void t_nativesound_close_rec(JNIEnv *env){
    if (mrec){
        env->CallNonvirtualVoidMethod(mrec, cAudioRecord, mStopRec);
        env->DeleteGlobalRef(mrec);
        mrec=NULL;
    }
}

static int t_nativesound_open_rec(JNIEnv *env, int iRate){
    if (!cAudioRecord) {
        jclass jcls = env->GetObjectClass(audioRecordSp);
        cAudioRecord = (jclass)env->NewGlobalRef(jcls);
        env->DeleteLocalRef(jcls);

        if (!cAudioRecord) {
            __android_log_print(ANDROID_LOG_ERROR, "native", "cAudioRecord = NULL");
            return -1;
        }
        mAudioRecord = env->GetMethodID(cAudioRecord, "<init>", "(IIII)V");
        mStartRecording = env->GetMethodID(cAudioRecord, "startRecording", "()V");
        mRead = env->GetMethodID(cAudioRecord, "read", "([SII)I");
        mStopRec = env->GetMethodID(cAudioRecord, "stop", "()V");
        mGetMinBufferSizeRec = env->GetStaticMethodID(cAudioRecord, "getMinBufferSize", "(III)I");
        CHECK_CLR_EXC;
    }
    int sampleRateInHz = iRate;
    iBufferSizeInBytesRealRec = iRecBufSizeInBytes = env->CallStaticIntMethod(cAudioRecord, mGetMinBufferSizeRec, sampleRateInHz, CHANNEL_IN_MONO, ENCODING_PCM_16BIT);

    androidLog("iRecBufSizeInBytes=%d",iRecBufSizeInBytes);

    if (iRecBufSizeInBytes < SAMPLE_REC_CNT*4)
        iRecBufSizeInBytes = SAMPLE_REC_CNT*4;

    if (!mrec){
        jobject loc = env->NewObject(cAudioRecord, mAudioRecord, sampleRateInHz, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, iRecBufSizeInBytes);

        mrec = env->NewGlobalRef(loc);
        env->DeleteLocalRef(loc);
    }
    return 0;
}

void t_native_stop_rec() {
    iGRecording=0;
}

static void t_native_startCBX_rec(int iRate, int (*cbFnc) (void *callback_data, short *samples, int frames), void *cbData){

    if(!iGRecording)
        return;

    JNIEnv *env=NULL;
    int iReset=1;
    int iSamplesInBuf=0;
    iRecorderState=0;

    jshort *buf = NULL;
    jshortArray bytearray = NULL;
    void *aec;

    androidLog("th rec ok");

    JavaVM *vm = t_getJavaVM();

    vm->GetEnv((void**)&env,JNI_VERSION_1_4);
    vm->AttachCurrentThread(&env, NULL);

    bytearray = env->NewShortArray( SAMPLE_REC_CNT * 2);

    if(bytearray)
        buf = env->GetShortArrayElements( bytearray, NULL);

    if (!buf) {
        goto exit_th;
    }

    aec=initAEC(iRate);

    t_nativesound_open_rec(env, iRate);
    androidLog("rec open ok");
    setThHighPriority(env);

    t_nativesound_rec(env);
    iRecorderState = 1;

    while(iGRecording) {
        int samplesRead;
        samplesRead = t_nativesound_read_arr(env, bytearray, 0, SAMPLE_REC_CNT);

        //  __android_log_print(ANDROID_LOG_DEBUG, "tivi", "rec_read=%d",samplesRead);
        if (samplesRead <= 0) {
            usleep(5000);                 //5ms
            continue;
        }
        env->GetShortArrayRegion(bytearray, 0, samplesRead, buf);

        if (getEchoEnableState() > 2){
            if(iReset)echoList.reset();
            iReset=0;
            short tmp[640];
            //sho vajag obligaati savaadaak buus echo
            while (echoList.in() > 1) {
                short *b = echoList.get();
                playbackBufAEC(aec, b, 160);
                playbackBufAEC(aec, b+160, 160);
            }

            short *pb=echoList.in()>0?echoList.get():NULL;
            if (pb)
                playbackBufAEC(aec, pb, 160);
            int d=iBufferSizeInBytesRealRec + iBufferSizeInBytesReal;
            d>>=5;
            d=30;

            recordProcessAEC(aec, (short*)buf, &tmp[0], 160, d);
            d=20;
            if(pb)playbackBufAEC(aec, pb+160, 160);

            recordProcessAEC(aec, (short*)buf+160, &tmp[160], 160, d);
            memcpy(buf,tmp,samplesRead*2);

        }
        if (cbFnc(cbData,buf,samplesRead) < 0)
            break;
        // if(w!=d)__android_log_print(ANDROID_LOG_DEBUG, "tivi",  "(rec=%d need=%d)",d,SAMPLE_REC_CNT);
        iSamplesInBuf+=samplesRead;
    }
    iRecorderState=0;
    androidLog("th rec try stop sb=%d", iSamplesInBuf);

    t_native_stop_rec();
    t_nativesound_close_rec(env);

    if (buf && bytearray)
        env->ReleaseShortArrayElements(bytearray, buf, 0);
    if (bytearray)
        env->DeleteLocalRef(bytearray);
exit_th:
    vm->DetachCurrentThread();
    androidLog("th rec exit");
}

static int thFncA_rec(void *a){

    t_native_startCBX_rec(txRec.iRate, txRec.cbFnc,txRec.cb);
    return 0;
}


void *t_native_startCB_rec(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
    iGRecording=1;
    txRec.iRate=iRate;
    txRec.cbFnc=cbFnc;
    txRec.cb=cbData;
    startThX(thFncA_rec, &txRec);
    return (void*)1;
}




