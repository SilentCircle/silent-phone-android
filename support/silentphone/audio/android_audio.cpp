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
#if 0
#ifndef _WIN32
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
//#define CHANNEL_OUT_MONO 0x00000002 was ok
//#define CHANNEL_IN_MONO CHANNEL_OUT_MONO

#define CHANNEL_IN_MONO 0x00000010
#define CHANNEL_OUT_MONO 0x04

#define STREAM_VOICE_CALL 0

//#define SAMPLE_WR 256 works on nexus, samsung note

//SAMPLE_WR must be mult of 160
static int SAMPLE_WR  = 320;

static jobject oTrack=NULL;
static jclass cAudioTrack=NULL;

static jmethodID mAudioTrack;

static jmethodID mPlay;
static jmethodID mWrite;
static jmethodID mStop;
static jmethodID mGetMinBufferSize;
static jmethodID mGetApos;
static jmethodID mRelease;


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
}TNX;

static TNX tx;

int getEchoEnableState();
int getAPILevel();
JavaVM *t_getJavaVM();

void updatePlayback(void *p, short *play);
void updateAndFixMic(void *p, short *rec);


static void debug_logx(const char *p){}
void tivi_log_scr(const char* format, ...){
   /*
    //__android_log_print(ANDROID_LOG_DEBUG, "tivi",
    va_list arglist;
    va_start(arglist, format);
    __android_log_vprint(ANDROID_LOG_DEBUG, "tivi", format, arglist);
    va_end(arglist);
    */
}


static int t_nativesound_getPlayPos(JNIEnv *env){
   return env->CallNonvirtualIntMethod(oTrack,cAudioTrack, mGetApos);
}
static void t_nativesound_play(JNIEnv *env){
   if (oTrack == NULL){ debug_logx("oTrack=NULL");iResult=2;}
   else env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mPlay);
}

static int t_nativesound_write_arr(JNIEnv *env, jshortArray samples, int ofs, int n){
   return (env)->CallNonvirtualIntMethod(oTrack, cAudioTrack, mWrite, samples, ofs, n);
}



static int t_nativesound_open(JNIEnv *env, int iRate){
   
   if (!cAudioTrack)
   {

      jclass jcls = env->FindClass("android/media/AudioTrack");
      cAudioTrack = (jclass)env->NewGlobalRef(jcls);
      env->DeleteLocalRef(jcls);
      
      
      if (!cAudioTrack) {
         iResult=1;
         debug_logx("cAudioTrack = NULL");
         return 1;
      }
      
      mAudioTrack = env->GetMethodID(cAudioTrack, "<init>", "(IIIIII)V");
      mPlay = env->GetMethodID(cAudioTrack, "play", "()V");
      mWrite = env->GetMethodID(cAudioTrack, "write", "([SII)I");
      mStop = env->GetMethodID(cAudioTrack, "stop", "()V");
      mRelease = env->GetMethodID(cAudioTrack, "release", "()V");
      mGetApos=env->GetMethodID(cAudioTrack, "getPlaybackHeadPosition", "()I");//"getPlaybackHeadPosition","()I");
      mGetMinBufferSize = env->GetStaticMethodID(cAudioTrack, "getMinBufferSize", "(III)I");
   }
   /*
    public static final int VOICE_CALL
    
    Since: API Level 4
    Voice call uplink + downlink audio source
    Constant Value: 4 (0x00000004)
    */
   //#define STREAM_MUSIC 3
   
   //3
#define MODE_STREAM 1
   
   int sampleRateInHz = iRate;
   int channelConfig = CHANNEL_OUT_MONO; //AudioFormat.CHANNEL_OUT_MONO
   int audioFormat = ENCODING_PCM_16BIT; //AudioFormat.ENCODING_PCM_16BIT
   
   iBufferSizeInBytes = env->CallStaticIntMethod(cAudioTrack, mGetMinBufferSize, sampleRateInHz, channelConfig, audioFormat);
   iBufferSizeInBytesReal = iBufferSizeInBytes;
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "iBufferSizeInBytes=%d",iBufferSizeInBytes);
   
#if 0
   SAMPLE_WR=320;
   if(iBufferSizeInBytes<640*4)iBufferSizeInBytes=640*4;
   SAMPLE_WR=iBufferSizeInBytes>>2;
   if(SAMPLE_WR>640)SAMPLE_WR=640;
#else
   SAMPLE_WR=iBufferSizeInBytes/4;
   
   if(SAMPLE_WR>640)SAMPLE_WR=640;
   else if(SAMPLE_WR<320)SAMPLE_WR=320;
   else SAMPLE_WR=480;
   
   if(iBufferSizeInBytes<SAMPLE_WR*4)iBufferSizeInBytes=SAMPLE_WR*4;
#endif
   
   jobject loc = env->NewObject(cAudioTrack, mAudioTrack,
                                //0x80000000,//
                                STREAM_VOICE_CALL,//STREAM_VOICE_CALL,
                                //3,// STREAM_MUSIC,
                                sampleRateInHz, channelConfig, audioFormat, iBufferSizeInBytes, MODE_STREAM); //AudioTrack.MODE_STREAM
   
   oTrack = env->NewGlobalRef(loc);
   env->DeleteLocalRef(loc);
   
   return 0;
}

static void t_nativesound_close(JNIEnv *env){
   if(!oTrack)return;
   env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mStop);
   env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mRelease);
   env->DeleteGlobalRef(oTrack);
   oTrack=NULL;
   
}



void t_native_stop(){iGPlaying=0;}


#if 1


class CTPlayBackDataQ{
   enum{eSize=64};
   short data[160 * eSize];
   int iPlayPos;
   int iReadPos;
   
public:
   CTPlayBackDataQ(){reset();}
   void reset(){iPlayPos=1;iReadPos=0;}
   void add160(short *p){
      if(iPlayPos<iReadPos)iPlayPos=iReadPos+1;
      int w=iPlayPos&(eSize-1);
      memcpy(&data[w*SAMPLE_WR],p,(SAMPLE_WR)*sizeof(short));
      iPlayPos++;
   }
   int in(){return iPlayPos-iReadPos;}
   short *get160(){int r=iReadPos&(eSize-1);iReadPos++;return &data[r*SAMPLE_WR];}
};

CTPlayBackDataQ echoList;

static void t_native_startCBX(int iRate, int (*cbFnc) (void *callback_data, short *samples, int frames, int iRestarted), void *cbData){
   
   JNIEnv *env=NULL;
   //320 16Khz was ok
   //#define SAMPLE_WR 160//ok on sams note, sams nexus 7, but not ok on sams galaxy 3
   //320 was ok on every dev
   int iPlaying=0;
   int iSamplesInBuf=0;
   int iReset=1;
   int iIsRestarted=1;
   
   jshort *buf = NULL;
   jshortArray bytearray = NULL;
   
   JavaVM *vm = t_getJavaVM();

   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th play ok");
   
   vm->GetEnv((void**)&env,JNI_VERSION_1_4);
   vm->AttachCurrentThread(&env, NULL);
   
   t_nativesound_open(env, iRate);
   
   bytearray = env->NewShortArray(SAMPLE_WR * 2);
   
   if(bytearray)
      buf = env->GetShortArrayElements( bytearray, NULL);
   
   if (!buf) {
      goto exit_th;
   }
   
   // pthread_t th=pthread_self();
   // pthread_setschedprio(th, 10);
   //W/audio_hw_primary(  131): read get_capture_delay(): pcm_htimestamp error
   //W/AudioFlinger(  131): RecordThread: buffer overflow
   //E/CameraHAL(  131): Adapter state switch PREVIEW_ACTIVE Invalid Op! event = 0xf
   
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "native play open");
   /*
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

    cr = ctx.getContentResolver();
    Settings.System.putInt(cr, Settings.System.WIFI_SLEEP_POLICY,Settings.System.WIFI_SLEEP_POLICY_NEVER);

    am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_OFF);
    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,AudioManager.VIBRATE_SETTING_OFF);
    */
   
   
   //  t_nativesound_play(env);//not good
   //testing
   
   
   while(iGPlaying){
      
      int w;
      int d;
      

      if(cbFnc(cbData,&buf[0],SAMPLE_WR,iIsRestarted)<0)break;
      iIsRestarted=0;


      
      d=0;
      
      w=t_nativesound_write_arr(env,bytearray,0,SAMPLE_WR);
      if(w>0){
         iSamplesInBuf+=w;//SAMPLE_WR;
         d+=w;
      }
      
      if(d<SAMPLE_WR){
         while(d<SAMPLE_WR && iGPlaying){
            
            usleep(2000);
            w=t_nativesound_write_arr(env,bytearray,d,SAMPLE_WR-d);
            if(w<0)break;
            iSamplesInBuf+=w;//SAMPLE_WR;
            d+=w;
         }
         if(d!=w)__android_log_print(ANDROID_LOG_DEBUG, "tivi",  "play %d %d",d,w);
      }

      if(!iPlaying){
         
         if(iSamplesInBuf*2<iBufferSizeInBytesReal && w<SAMPLE_WR){continue;}
         __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th try play");
         t_nativesound_play(env);
         iIsRestarted=1;
         iPlaying=1;
      }
      
      if(iReset)echoList.reset();iReset=0;
      
      for(int a=0;a<SAMPLE_WR;a+=160){
         echoList.add160((short*)&buf[a]);
      }
      
   }
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th ap try stop");
   
   t_native_stop();
exit_th:
   t_nativesound_close(env);
   
   if(bytearray && buf)env->ReleaseShortArrayElements(bytearray, buf, 0);
   if(bytearray)env->DeleteLocalRef(bytearray);

   vm->DetachCurrentThread();
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th ap exit");
}
static int thFncA(void *a){
   
   iGPlaying=1;
   t_native_startCBX(tx.iRate, tx.cbFnc,tx.cb);
   
   return 0;
}
void t_onOverFlow(void *ctx){}

#endif
void startThX(int (cbFnc)(void *p),void *data);

void *t_native_PstartCB(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
   //TODO fix util play
   return NULL;
   /*
    CTAudioOut *a=(CTAudioOut*)ctx;
    if(!a){
    a=new CTAudioOut(iRate);
    }
    
    a->play(cbData,cbFnc);
    return a;
    */
}

void *t_native_startCB_play(void *ctx, int iRate, int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData){
   tx.cbFnc = cbFnc;
   tx.cb = cbData;
   tx.iRate = iRate;
   startThX(thFncA,&tx);
   return (void*)1;
   
}

//test-----------
/*
 static short *t_adata;
 static int iDataLen;
 static int iDataPos;
 int aaa(void *callback_data, short *samples, int frames){
 if(callback_data!=(void*)t_adata)return -1;
 memcpy(samples,&t_adata[iDataPos],frames*2);
 iDataPos+=frames;
 if(iDataPos>iDataLen)return -1;
 
 return 0;
 }
 
 void testFncA(short *samples, int iCnt){
 
 // return;
 iDataPos=0;
 t_adata=samples;
 iDataLen=iCnt;
 t_native_startCB(&aaa,samples);
 sleep(2);
 t_native_stop();
 
 }
 */

void t_native_stop(void *ctx, int iPlay){
   void t_native_stop_rec();
   if(iPlay)t_native_stop();
   else t_native_stop_rec();
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

#include "android_audio_rec.inl"


#endif

//------------------
#else
//--------------------

#ifndef _WIN32
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
//#define CHANNEL_OUT_MONO 0x00000002 was ok
//#define CHANNEL_IN_MONO CHANNEL_OUT_MONO

#define CHANNEL_IN_MONO 0x00000010
#define CHANNEL_OUT_MONO 0x04
#define CHANNEL_IN_STEREO 0x0000000c

#define STREAM_VOICE_CALL 0

//#define SAMPLE_WR 256 works on nexus, samsung note

//SAMPLE_WR must be mult of 160
#define SAMPLE_WR 320

static jobject oTrack=NULL;
static jclass cAudioTrack=NULL;

static jmethodID mAudioTrack;

static jmethodID mPlay;
static jmethodID mWrite;
static jmethodID mStop;
static jmethodID mGetMinBufferSize;
static jmethodID mGetApos;
static jmethodID mRelease;


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
}TNX;

static TNX tx;

int getEchoEnableState();
int getAPILevel();
JavaVM *t_getJavaVM();

void updatePlayback(void *p, short *play);
void updateAndFixMic(void *p, short *rec);


static void debug_logx(const char *p){}
void tivi_log_scr(const char* format, ...){
   /*
    //__android_log_print(ANDROID_LOG_DEBUG, "tivi",
    va_list arglist;
    va_start(arglist, format);
    __android_log_vprint(ANDROID_LOG_DEBUG, "tivi", format, arglist);
    va_end(arglist);
    */
}


static int t_nativesound_getPlayPos(JNIEnv *env){
   return env->CallNonvirtualIntMethod(oTrack,cAudioTrack, mGetApos);
}
static void t_nativesound_play(JNIEnv *env){
   if (oTrack == NULL){ debug_logx("oTrack=NULL");iResult=2;}
   else env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mPlay);
}

static int t_nativesound_write_arr(JNIEnv *env, jshortArray samples, int ofs, int n){
   return (env)->CallNonvirtualIntMethod(oTrack, cAudioTrack, mWrite, samples, ofs, n);
}



static int t_nativesound_open(JNIEnv *env, int iRate){
   
   if (!cAudioTrack)
   {
      
      jclass jcls = env->FindClass("android/media/AudioTrack");
      cAudioTrack = (jclass)env->NewGlobalRef(jcls);
      env->DeleteLocalRef(jcls);
      
      
      if (!cAudioTrack) {
         iResult=1;
         debug_logx("cAudioTrack = NULL");
         return 1;
      }
      
      mAudioTrack = env->GetMethodID(cAudioTrack, "<init>", "(IIIIII)V");
      mPlay = env->GetMethodID(cAudioTrack, "play", "()V");
      mWrite = env->GetMethodID(cAudioTrack, "write", "([SII)I");
      mStop = env->GetMethodID(cAudioTrack, "stop", "()V");
      mRelease = env->GetMethodID(cAudioTrack, "release", "()V");
      mGetApos=env->GetMethodID(cAudioTrack, "getPlaybackHeadPosition", "()I");//"getPlaybackHeadPosition","()I");
      mGetMinBufferSize = env->GetStaticMethodID(cAudioTrack, "getMinBufferSize", "(III)I");
   }
   /*
    public static final int VOICE_CALL
    
    Since: API Level 4
    Voice call uplink + downlink audio source
    Constant Value: 4 (0x00000004)
    */
   //#define STREAM_MUSIC 3
   
   //3
#define MODE_STREAM 1
   
   int sampleRateInHz = iRate;
   int channelConfig = CHANNEL_OUT_MONO; //AudioFormat.CHANNEL_OUT_MONO
   int audioFormat = ENCODING_PCM_16BIT; //AudioFormat.ENCODING_PCM_16BIT
   
   iBufferSizeInBytes = env->CallStaticIntMethod(cAudioTrack, mGetMinBufferSize, sampleRateInHz, channelConfig, audioFormat);
   iBufferSizeInBytesReal = iBufferSizeInBytes;
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "iBufferSizeInBytes=%d",iBufferSizeInBytes);
   
   if(iBufferSizeInBytes<SAMPLE_WR*6)iBufferSizeInBytes=SAMPLE_WR*6;
   
   jobject loc = env->NewObject(cAudioTrack, mAudioTrack,
                                STREAM_VOICE_CALL,//STREAM_VOICE_CALL,
                                //3,// STREAM_MUSIC,
                                sampleRateInHz, channelConfig, audioFormat, iBufferSizeInBytes, MODE_STREAM); //AudioTrack.MODE_STREAM
   
   oTrack = env->NewGlobalRef(loc);
   env->DeleteLocalRef(loc);
   
   return 0;
}

static void t_nativesound_close(JNIEnv *env){
   if(!oTrack)return;
   env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mStop);
   env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mRelease);
   env->DeleteGlobalRef(oTrack);
   oTrack=NULL;
   
}



void t_native_stop(){iGPlaying=0;}


#if 1


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

/*
 void *createSpeexAEC(int iRate, int iSamples, int iTailMs);
 
 void *getPtrAEC(int iRate){//TODO mutex
 static void *ec = createSpeexAEC(iRate, SAMPLE_WR, 100);
 return ec;
 }
 
 void *getSpeexEC(int iRate){
 void *ec=getPtrAEC(iRate);
 if(!ec){
 usleep(10*1000);//10ms
 ec=getPtrAEC(iRate);
 }
 return ec;
 }
 */
static void t_native_startCBX(int iRate, int (*cbFnc) (void *callback_data, short *samples, int frames, int iRestarted), void *cbData){
   
   JNIEnv *env=NULL;
   //320 16Khz was ok
   //#define SAMPLE_WR 160//ok on sams note, sams nexus 7, but not ok on sams galaxy 3
   //320 was ok on every dev
   int iPlaying=0;
   int iSamplesInBuf=0;
   int iReset=1;
   int iIsRestarted=1;
   
   //while(iRecorderState!=1 && iGPlaying)usleep(10000);if(!iGPlaying)return;
   
   jshort *buf = NULL;
   jshortArray bytearray = NULL;
   
   JavaVM *vm = t_getJavaVM();
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th play ok");
   
   vm->GetEnv((void**)&env,JNI_VERSION_1_4);
   vm->AttachCurrentThread(&env, NULL);
   
   bytearray = env->NewShortArray(SAMPLE_WR * 2);
   
   if(bytearray)
      buf = env->GetShortArrayElements( bytearray, NULL);
   
   if (!buf) {
      goto exit_th;
   }
   
   // pthread_t th=pthread_self();
   // pthread_setschedprio(th, 10);
   //W/audio_hw_primary(  131): read get_capture_delay(): pcm_htimestamp error
   //W/AudioFlinger(  131): RecordThread: buffer overflow
   //E/CameraHAL(  131): Adapter state switch PREVIEW_ACTIVE Invalid Op! event = 0xf
   
   t_nativesound_open(env, iRate);
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "native play open");
   /*
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
    
    cr = ctx.getContentResolver();
    Settings.System.putInt(cr, Settings.System.WIFI_SLEEP_POLICY,Settings.System.WIFI_SLEEP_POLICY_NEVER);
    
    am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_OFF);
    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,AudioManager.VIBRATE_SETTING_OFF);
    */
   
   
   //  t_nativesound_play(env);//not good
   //testing
   
   
   while(iGPlaying){
      
      int w;
      int d;
      
      
      if(cbFnc(cbData,&buf[0],SAMPLE_WR,iIsRestarted)<0)break;
      iIsRestarted=0;
      
      d=0;
      
      w=t_nativesound_write_arr(env,bytearray,0,SAMPLE_WR);
      if(w>0){
         iSamplesInBuf+=w;//SAMPLE_WR;
         d+=w;
      }
      
      if(d<SAMPLE_WR){
         do{
            usleep(2000);
            w=t_nativesound_write_arr(env,bytearray,d,SAMPLE_WR-d);
            if(w<0)break;
            iSamplesInBuf+=w;//SAMPLE_WR;
            d+=w;
            
         }while(0);
      }
      
      
      if(!iPlaying){
         
         if(iSamplesInBuf*2<iBufferSizeInBytesReal && w<SAMPLE_WR){continue;}
         __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th try play");
         t_nativesound_play(env);
         iIsRestarted=1;
         iPlaying=1;
      }
      
      if(iReset)echoList.reset();iReset=0;
      echoList.add((short*)buf);
      
   }
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th ap try stop");
   
   t_native_stop();
   t_nativesound_close(env);
   
   if(bytearray && buf)env->ReleaseShortArrayElements(bytearray, buf, 0);
   if(bytearray)env->DeleteLocalRef(bytearray);
   
exit_th:
   vm->DetachCurrentThread();
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th ap exit");
}
static int thFncA(void *a){
   
   iGPlaying=1;
   t_native_startCBX(tx.iRate, tx.cbFnc,tx.cb);
   
   return 0;
}
void t_onOverFlow(void *ctx){}

#endif
void startThX(int (cbFnc)(void *p),void *data);

void *t_native_PstartCB(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
   //TODO fix util play
   return NULL;
   /*
    CTAudioOut *a=(CTAudioOut*)ctx;
    if(!a){
    a=new CTAudioOut(iRate);
    }
    
    a->play(cbData,cbFnc);
    return a;
    */
}

void *t_native_startCB_play(void *ctx, int iRate, int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData){
   tx.cbFnc = cbFnc;
   tx.cb = cbData;
   tx.iRate = iRate;
   startThX(thFncA,&tx);
   return (void*)1;
   
}

//test-----------
/*
 static short *t_adata;
 static int iDataLen;
 static int iDataPos;
 int aaa(void *callback_data, short *samples, int frames){
 if(callback_data!=(void*)t_adata)return -1;
 memcpy(samples,&t_adata[iDataPos],frames*2);
 iDataPos+=frames;
 if(iDataPos>iDataLen)return -1;
 
 return 0;
 }
 
 void testFncA(short *samples, int iCnt){
 
 // return;
 iDataPos=0;
 t_adata=samples;
 iDataLen=iCnt;
 t_native_startCB(&aaa,samples);
 sleep(2);
 t_native_stop();
 
 }
 */

void t_native_stop(void *ctx, int iPlay){
   void t_native_stop_rec();
   if(iPlay)t_native_stop();
   else t_native_stop_rec();
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

#include "android_audio_rec.inl"


#endif



#endif


