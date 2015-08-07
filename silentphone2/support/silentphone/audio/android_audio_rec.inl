/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2015, Silent Circle, LLC.  All rights reserved.

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

// Copied into android_audio.cpp
#if 0
//-----------

static jclass cAudioRecord=NULL;

static jobject mrec=NULL;

static jmethodID mAudioRecord;
static jmethodID mGetMinBufferSizeRec;
static jmethodID mStartRecording;
static jmethodID mStopRec;
static jmethodID mReleaseRec;
static jmethodID mRead;
static jmethodID mSwitchInternal;

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
//


#define SAMPLE_REC_CNT  SAMPLE_WR

static void t_nativesound_rec(JNIEnv *env){
    env->CallNonvirtualVoidMethod(mrec, cAudioRecord, mStartRecording);
}

static int t_nativesound_read_arr(JNIEnv *env, jshortArray samples, int ofs, int n){
    int w=(env)->CallNonvirtualIntMethod(mrec, cAudioRecord, mRead, samples, ofs, n);
    return w;
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
            debug_logx("cAudioRecord = NULL");
            return -1;
        }

        mAudioRecord = env->GetMethodID(cAudioRecord, "<init>", "(IIII)V");
        mStartRecording = env->GetMethodID(cAudioRecord, "startRecording", "()V");
        mRead = env->GetMethodID(cAudioRecord, "read", "([SII)I");
        mStopRec = env->GetMethodID(cAudioRecord, "stop", "()V");
        mGetMinBufferSizeRec = env->GetStaticMethodID(cAudioRecord, "getMinBufferSize", "(III)I");
        mSwitchInternal = env->GetStaticMethodID(cAudioRecord, "setUseInternalAec", "(Z)V");
        CHECK_CLR_EXC;
    }
    int sampleRateInHz = iRate;
    iBufferSizeInBytesRealRec = iRecBufSizeInBytes = env->CallStaticIntMethod(cAudioRecord, mGetMinBufferSizeRec, sampleRateInHz, CHANNEL_IN_MONO, ENCODING_PCM_16BIT);

    __android_log_print(ANDROID_LOG_DEBUG, "native", "iRecBufSizeInBytes=%d",iRecBufSizeInBytes);

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

    __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th rec ok");

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
    __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "rec open ok");
    setThHighPriority(env);

    t_nativesound_rec(env);
    iRecorderState = 1;

    while(iGRecording) {
        int samplesRead;
        samplesRead = t_nativesound_read_arr(env,bytearray,0,SAMPLE_REC_CNT);

        //  __android_log_print(ANDROID_LOG_DEBUG, "tivi", "rec_read=%d",samplesRead);
        if (samplesRead <= 0) {
            usleep(5000);//5ms
            continue;
        }

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
        //mutex
        if (cbFnc(cbData,buf,samplesRead) < 0)
            break;
        // if(w!=d)__android_log_print(ANDROID_LOG_DEBUG, "tivi",  "(rec=%d need=%d)",d,SAMPLE_REC_CNT);
        iSamplesInBuf+=samplesRead;
    }
    iRecorderState=0;
    __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th rec try stop sb=%d", iSamplesInBuf);
   
    t_native_stop_rec();
    t_nativesound_close_rec(env);
   
    if (buf && bytearray)
        env->ReleaseShortArrayElements(bytearray, buf, 0);
    if (bytearray)
        env->DeleteLocalRef(bytearray);
exit_th:
    vm->DetachCurrentThread();
    __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th rec exit");
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

#endif

