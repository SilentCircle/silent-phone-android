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

#if 1
//-----------

static jclass cAudioRecord=NULL;


static jobject mrec=NULL;
static jobject go_aec=NULL;

static jmethodID mAudioRecord;
static jmethodID mGetMinBufferSizeRec;
static jmethodID mStartRecording;
static jmethodID mStopRec;
static jmethodID mReleaseRec;
static jmethodID mRead;

static jmethodID mGetAudioSessionId=NULL;


static int iRecBufSizeInBytes=100;

static int iGRecording=0;

typedef struct {
   int (*cbFnc) (void *callback_data, short *samples, int frames);
   void *cb;
   int iRate;
}TNXR;

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
   if(mrec){
      env->CallNonvirtualVoidMethod(mrec, cAudioRecord, mStopRec);
      env->CallNonvirtualVoidMethod(mrec, cAudioRecord, mReleaseRec);
      env->DeleteGlobalRef(mrec);
      mrec=NULL;
   }
   if(go_aec){
      env->DeleteGlobalRef(go_aec);
      go_aec=NULL;
   }
}

static int t_init_android_aec(JNIEnv *env, int lev){
   if(lev<16 || !mGetAudioSessionId)return -1;
   
   
   
   //we need this on android 4.1.1
   jclass aec = env->FindClass("android/media/audiofx/AcousticEchoCanceler");
   CHECK_CLR_EXC;
   
   if(!aec){
      return -2;
   }
   //TODO check isAvailable
   
   
   jmethodID mCreateAEC = env->GetStaticMethodID(aec, "create", "(I)Landroid/media/audiofx/AcousticEchoCanceler;");
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "mCreateAEC=%p",mCreateAEC);
   
   CHECK_CLR_EXC;
   
   
   if(!mCreateAEC){
      return -3;
   }
   
   int r=(env)->CallNonvirtualIntMethod(mrec, cAudioRecord, mGetAudioSessionId);
   CHECK_CLR_EXC;
   
   jobject loc = env->CallStaticObjectMethod(aec, mCreateAEC, r);
   CHECK_CLR_EXC;
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "call mCreateAEC=%p",loc);
   
   if(loc){
      go_aec=env->NewGlobalRef(loc);
      env->DeleteLocalRef(loc);
      
      __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "NewGlobalRef=%p",go_aec);
      
      jmethodID mSetEnabled = env->GetMethodID(aec, "setEnabled", "(Z)I");
      __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "mSetEnabled=%p", mSetEnabled);
      CHECK_CLR_EXC
      
      if(mSetEnabled && go_aec){
         (env)->CallNonvirtualIntMethod(go_aec, aec, mSetEnabled, 1);
         CHECK_CLR_EXC;
      }
      __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "mSetEnabled ok");
   }
}

static int t_nativesound_open_rec(JNIEnv *env, int iRate){
   
   int lev = getAPILevel();
   
   if (!cAudioRecord)
   {
      
      jclass jcls = env->FindClass("android/media/AudioRecord");
      cAudioRecord = (jclass)env->NewGlobalRef(jcls);
      env->DeleteLocalRef(jcls);
      
      
      if (!cAudioRecord) {
         debug_logx("cAudioRecord = NULL");
         return -1;
      }
      
      mAudioRecord = env->GetMethodID(cAudioRecord, "<init>", "(IIIII)V");
      mStartRecording = env->GetMethodID(cAudioRecord, "startRecording", "()V");
      mRead = env->GetMethodID(cAudioRecord, "read", "([SII)I");
      mStopRec = env->GetMethodID(cAudioRecord, "stop", "()V");
      mReleaseRec = env->GetMethodID(cAudioRecord, "release", "()V");
      mGetMinBufferSizeRec = env->GetStaticMethodID(cAudioRecord, "getMinBufferSize", "(III)I");
      
      if(lev>=16)
         mGetAudioSessionId=env->GetMethodID(cAudioRecord, "getAudioSessionId", "()I");//lev
      
      CHECK_CLR_EXC;
      //  msetPlaybackPositionUpdateListener = env->GetMethodID(cAudioRecord, "setPlaybackPositionUpdateListener", "([BII)I");
   }
   
   
   //--javaVM->AttachCurrentThread(&env, NULL);
   /*
    static JNINativeMethod gMethods[] = {
    // name,               signature,  funcPtr
    {"native_start",         "()V",    (void *)android_media_AudioRecord_start},
    {"native_stop",          "()V",    (void *)android_media_AudioRecord_stop},
    {"native_setup",         "(Ljava/lang/Object;IIIII)I",
    (void *)android_media_AudioRecord_setup},
    {"native_finalize",      "()V",    (void *)android_media_AudioRecord_finalize},
    {"native_release",       "()V",    (void *)android_media_AudioRecord_release},
    {"native_read_in_byte_array",
    "([BII)I", (void *)android_media_AudioRecord_readInByteArray},
    {"native_read_in_short_array",
    "([SII)I", (void *)android_media_AudioRecord_readInShortArray},
    {"native_read_in_direct_buffer","(Ljava/lang/Object;I)I",
    (void *)android_media_AudioRecord_readInDirectBuffer},
    {"native_set_marker_pos","(I)I",   (void *)android_media_AudioRecord_set_marker_pos},
    {"native_get_marker_pos","()I",    (void *)android_media_AudioRecord_get_marker_pos},
    {"native_set_pos_update_period",
    "(I)I",   (void *)android_media_AudioRecord_set_pos_update_period},
    {"native_get_pos_update_period",
    "()I",    (void *)android_media_AudioRecord_get_pos_update_period},
    {"native_get_min_buff_size",
    "(III)I",   (void *)android_media_AudioRecord_get_min_buff_size},
    };
    */
   
   //env->PushLocalFrame(2);
   
  // iRate=44100;
   int sampleRateInHz = iRate;
   int channelConfig = CHANNEL_IN_MONO; //AudioFormat.CHANNEL_OUT_MONO
   int audioFormat = ENCODING_PCM_16BIT; //AudioFormat.ENCODING_PCM_16BIT
   
   iBufferSizeInBytesRealRec = iRecBufSizeInBytes = env->CallStaticIntMethod(cAudioRecord, mGetMinBufferSizeRec, sampleRateInHz, channelConfig, audioFormat);
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "iRecBufSizeInBytes=%d",iRecBufSizeInBytes);
   

   if(iRecBufSizeInBytes<SAMPLE_REC_CNT*4)iRecBufSizeInBytes=SAMPLE_REC_CNT*4;
   
   
//#define VOICE_CALL 4
#define AS_DEFAULT 0
#define MIC 1
#define VOICE_COMMUNICATION 7
   /*
    public static final int MIC
    
    Added in API level 1
    Microphone audio source
    
    Constant Value: 1 (0x00000001)
    -------------------------
    public static final int VOICE_CALL
    
    Added in API level 4
    Voice call uplink + downlink audio source
    
    Constant Value: 4 (0x00000004)
    --------------------
    public static final int VOICE_COMMUNICATION
    
    Added in API level 11
    Microphone audio source tuned for voice communications such as VoIP. It will for instance take advantage of echo cancellation or automatic gain control if available. It otherwise behaves like DEFAULT if no voice processing is applied.
    
    Constant Value: 7 (0x00000007)
    
    */
   
   
   // if(lev>=11)iSource=VOICE_CALL;//does not work
   if(!mrec){
      __android_log_print(ANDROID_LOG_DEBUG, "tivi", "getAPILevel()=%d",lev);
      
      int iSource= lev>=11 ? VOICE_COMMUNICATION : MIC;
      //public AudioRecord (int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes)
      
      jobject loc = env->NewObject(cAudioRecord, mAudioRecord, iSource, sampleRateInHz, channelConfig, audioFormat, iRecBufSizeInBytes); //AudioTrack.MODE_STREAM
      
      mrec = env->NewGlobalRef( loc);
      env->DeleteLocalRef(loc);
      
#if 1
      CHECK_CLR_EXC;
      if(getEchoEnableState()==2)
         t_init_android_aec(env,lev);
#endif
   }
   
   
   
   return 0;
}

void t_native_stop_rec(){iGRecording=0;}

///thread


#if 1
static void t_native_startCBX_rec(int iRate, int (*cbFnc) (void *callback_data, short *samples, int frames), void *cbData){
   
   if(!iGRecording)return;
   
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
   
   
#if 0
   aec = getSpeexEC(iRate);
   
   void resetSpeexAec(void *p );
   resetSpeexAec(aec);
#else
   aec=initAEC(iRate);
#endif
   
   
   t_nativesound_open_rec(env, iRate);
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "rec open ok");
   
   setThHighPriority(env);

   t_nativesound_rec(env);
   iRecorderState=1;
   
   
   
   while(iGRecording){
      
      int samplesRead;
      
      samplesRead = t_nativesound_read_arr(env,bytearray,0,SAMPLE_REC_CNT);
      
    //  __android_log_print(ANDROID_LOG_DEBUG, "tivi", "rec_read=%d",samplesRead);
      
      if(samplesRead<=0){
         usleep(5000);//5ms
         continue;
      }
      
      if(getEchoEnableState()>2){
         
         if(iReset)echoList.reset();iReset=0;
         /*
          void remEchoSpeexAec(void *p,short *rec, short *play);
          remEchoSpeexAec(aec,  (short*)buf, echoList.get());
          */
         short tmp[640];
         //sho vajag obligaati savaadaak buus echo
         while(echoList.in()>1){
            short *b=echoList.get();
            playbackBufAEC(aec, b, 160);
            playbackBufAEC(aec, b+160, 160);
         }
         
         short *pb=echoList.in()>0?echoList.get():NULL;
         
         if(pb)
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
      if(cbFnc(cbData,buf,samplesRead)<0)break;
      
      // if(w!=d)__android_log_print(ANDROID_LOG_DEBUG, "tivi",  "(rec=%d need=%d)",d,SAMPLE_REC_CNT);
      iSamplesInBuf+=samplesRead;
      
      
   }
   iRecorderState=0;
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th rec try stop sb=%d", iSamplesInBuf);
   
   t_native_stop_rec();
   t_nativesound_close_rec(env);
   
   if(buf && bytearray)env->ReleaseShortArrayElements(bytearray, buf, 0);
   if(bytearray)env->DeleteLocalRef(bytearray);
   
   
exit_th:
   vm->DetachCurrentThread();
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th rec exit");
}
static int thFncA_rec(void *a){
   
   t_native_startCBX_rec(txRec.iRate, txRec.cbFnc,txRec.cb);
   
   return 0;
}

#endif

void *t_native_startCB_rec(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
   
   iGRecording=1;
   txRec.iRate=iRate;
   txRec.cbFnc=cbFnc;
   txRec.cb=cbData;
   // t_native_startCBX(tx.cbFnc,tx.cb);
   startThX(thFncA_rec,&txRec);
   return (void*)1;
   //  sleep(3);
}
/*
 //test-----------
 int ssaaa(void *callback_data, short *samples, int frames){
 
 return 0;
 }
 
 void testFncAR(){
 
 // return;
 t_native_startCB_rec(&ssaaa,NULL);
 sleep(2);
 t_native_stop_rec();
 
 }
 */


#endif

