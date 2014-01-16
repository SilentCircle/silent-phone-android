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


#define A_O_P_THREAD_PRIORITY_AUDIO -16

void setThHighPriority(JNIEnv *env){
   
   jclass c;
   
   jmethodID m;
   
   jthrowable exc;
   
   c = (jclass)env->NewGlobalRef(env->FindClass("android/os/Process"));
   if (!c) return;
   
   m = env->GetStaticMethodID(c, "setThreadPriority", "(I)V");
   if (!m) goto e;
   
   
   env->CallStaticVoidMethod(c, m, A_O_P_THREAD_PRIORITY_AUDIO);
   
   exc = env->ExceptionOccurred();
   if (exc) {
      env->ExceptionDescribe();
      env->ExceptionClear();
   }
e:
   if(c)env->DeleteGlobalRef(c);
   
}


static int iCurrentHWSampleRate=0;
static int iNextHWSampleRate=0;

static void t_setCurrentHWSampleRate(int iRate){
   iCurrentHWSampleRate=iRate;
   iNextHWSampleRate=0;
}

int t_setNextHWSampleRate(int iRate){
   if(iCurrentHWSampleRate==iRate)return 0;
   if(iRate!=16000 && iRate!=48000){
      __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "set_hw_rate() fail - iRate!=16000 && iRate!=48000");
      return -1;
   }
   iNextHWSampleRate=iRate;
   return 0;
}

int t_getSampleRate(){
   return iCurrentHWSampleRate;
}


static int t_nativesound_open(JNIEnv *env, int iInitRate){
   oTrack=NULL;
   if (!cAudioTrack)
   {
      
      jclass jcls = env->FindClass("android/media/AudioTrack");
      cAudioTrack = (jclass)env->NewGlobalRef(jcls);
      env->DeleteLocalRef(jcls);
      
      
      if (!cAudioTrack) {
         iResult=1;
         debug_logx("cAudioTrack = NULL");
         return -1;
      }
      
      mAudioTrack = env->GetMethodID(cAudioTrack, "<init>", "(IIIIII)V");
      mPlay = env->GetMethodID(cAudioTrack, "play", "()V");
      mWrite = env->GetMethodID(cAudioTrack, "write", "([SII)I");
      mStop = env->GetMethodID(cAudioTrack, "stop", "()V");
      mRelease = env->GetMethodID(cAudioTrack, "release", "()V");
      mGetApos=env->GetMethodID(cAudioTrack, "getPlaybackHeadPosition", "()I");//"getPlaybackHeadPosition","()I");
      mGetMinBufferSize = env->GetStaticMethodID(cAudioTrack, "getMinBufferSize", "(III)I");
   }
   
#define MODE_STREAM 1
   
   int sampleRateInHz = iInitRate; //16000 did not work on some devices
   int channelConfig = CHANNEL_OUT_MONO; //AudioFormat.CHANNEL_OUT_MONO
   int audioFormat = ENCODING_PCM_16BIT; //AudioFormat.ENCODING_PCM_16BIT
   
   iBufferSizeInBytes = env->CallStaticIntMethod(cAudioTrack, mGetMinBufferSize, sampleRateInHz, channelConfig, audioFormat);
   iBufferSizeInBytesReal = iBufferSizeInBytes;
   
   if(iBufferSizeInBytes<SAMPLE_WR*12)iBufferSizeInBytes=SAMPLE_WR*12;
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "iBufferSizeInBytes r=%d c=%d",iBufferSizeInBytesReal,iBufferSizeInBytes);
   
   jobject loc = env->NewObject(cAudioTrack, mAudioTrack,
                                STREAM_VOICE_CALL,
                                // STREAM_MUSIC, //ve .33
                                //1,//STREAM_SYSTEM
                                sampleRateInHz, channelConfig, audioFormat, iBufferSizeInBytes, MODE_STREAM); //AudioTrack.MODE_STREAM
   if(!loc)return -1;
   
   oTrack = env->NewGlobalRef(loc);
   env->DeleteLocalRef(loc);
   
   t_setCurrentHWSampleRate(iInitRate);
   
   return 0;
}

static void t_nativesound_close(JNIEnv *env){
   if(!oTrack)return;
   env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mStop);
   env->CallNonvirtualVoidMethod(oTrack, cAudioTrack, mRelease);
   env->DeleteGlobalRef(oTrack);
   oTrack=NULL;
}


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


static void t_native_startCBX(int iRate, int (*cbFnc) (void *callback_data, short *samples, int frames, int iRestarted), void *cbData){
   
   JNIEnv *env=NULL;
   if(!iGPlaying)return;
   
   int iPlaying=0;
   int iSamplesInBuf=0;
   int iReset=1;
   int iIsRestarted=1;
   
   int iRateMult;
   
   int prev=0;
   short inb[SAMPLE_WR];
   int iHWSampleRateToUse = 0;
   
   
   
   //while(iRecorderState!=1 && iGPlaying)usleep(10000);if(!iGPlaying)return;
   
   jshort *buf = NULL;
   jshortArray bytearray = NULL;
   
   int iWriteBufSize = SAMPLE_WR;
   
   short *rp;
   
   JavaVM *vm = t_getJavaVM();
   
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th play ok");
   
   vm->GetEnv((void**)&env,JNI_VERSION_1_4);
   vm->AttachCurrentThread(&env, NULL);
   
   bytearray = env->NewShortArray(SAMPLE_WR * 6);
   
   if(bytearray)
      buf = env->GetShortArrayElements( bytearray, NULL);
   
   if (!buf)
      goto exit_th;
   
   setThHighPriority(env);
   
   
   while(iGPlaying){
      
      iReset=1;
      iIsRestarted=1;
      iPlaying=0;
      
      if((iRate==16000 && iNextHWSampleRate != 16000)|| iNextHWSampleRate==48000){
         iWriteBufSize = SAMPLE_WR * 3;
         iRateMult=3;
         rp=&inb[0];
         iHWSampleRateToUse=48000;
      }
      else{
         iWriteBufSize = SAMPLE_WR;
         iRateMult=1;
         rp=&buf[0];
         iHWSampleRateToUse = iRate;
      }
      
      
      {
         int e=t_nativesound_open(env, iHWSampleRateToUse);
         
         __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "%d=native_play_open(hw=%d r=%d)",e,iHWSampleRateToUse,iRate);
         
         if(e)
            goto exit_th;
         
      }
      /*
       
       cr = ctx.getContentResolver();
       Settings.System.putInt(cr, Settings.System.WIFI_SLEEP_POLICY,Settings.System.WIFI_SLEEP_POLICY_NEVER);
       
       am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
       am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_OFF);
       am.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,AudioManager.VIBRATE_SETTING_OFF);
       */
      
      // t_nativesound_play(env);iPlaying=1;//not good on older phones
      
      //use 8khz if bluetooth_is_used
      //set.samplerate=48000
      //get.samplerate should respond when current used sr
      
      while(iGPlaying){
         
         int w;
         int d;
         
         if(iNextHWSampleRate)break;
         
         
         if(cbFnc(cbData, rp ,SAMPLE_WR,iIsRestarted)<0){iGPlaying=0;break;}
         iIsRestarted=0;
         
         if(iRateMult==3){
            for(int z=0;z<SAMPLE_WR;z++){
               int cur=rp[z];
               buf[z*3]=((prev*11+cur*5+8)>>4);
               buf[z*3+1]=((prev*5+cur*11+8)>>4);
               buf[z*3+2]=cur;//
               prev=cur;
            }
         }
         
         
         d=0;
         
         w=t_nativesound_write_arr(env,bytearray,0,iWriteBufSize);
         if(w>0){
            iSamplesInBuf+=w;//SAMPLE_WR;
            d+=w;
         }
         
         if(d<iWriteBufSize){
            do{
               usleep(2000);
               w=t_nativesound_write_arr(env,bytearray,d,iWriteBufSize-d);
               if(w<0){
                  __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "write()=%d failed, th play", w);
                  break;
               }
               iSamplesInBuf+=w;//SAMPLE_WR;
               d+=w;
               
            }while(0);
         }
         
         if(iReset)echoList.reset();iReset=0;
         echoList.add((short*)rp);
         
         if(!iPlaying){
            
            if(/*iSamplesInBuf*2<iBufferSizeInBytesReal &&*/ w<iWriteBufSize){continue;}
            __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th try play");
            t_nativesound_play(env);
            iIsRestarted=1;
            iPlaying=1;
         }
         
      }
      __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th ap try stop");
      
      t_nativesound_close(env);
      
      if(!iNextHWSampleRate)break;
         
   }
   
   
   if(bytearray && buf)env->ReleaseShortArrayElements(bytearray, buf, 0);
   if(bytearray)env->DeleteLocalRef(bytearray);
   
exit_th:
   vm->DetachCurrentThread();
   __android_log_print(ANDROID_LOG_DEBUG, "tivi",  "th ap exit");
   
   t_setCurrentHWSampleRate(0);
}
static int thFncA(void *a){
   
  
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
   iGPlaying=1;
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

void t_native_stop(){iGPlaying=0;}

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


