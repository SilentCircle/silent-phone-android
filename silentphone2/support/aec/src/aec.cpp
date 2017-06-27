/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2017, Silent Circle, LLC.  All rights reserved.

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
#include <string.h>

#include <android/log.h>

#if 0
//#extern 'C'{
#include "include/speex/speex_echo.h"
#include "include/speex/speex_preprocess.h"

extern "C" {
 void speex_warning(const char *str){
    __android_log_print(ANDROID_LOG_DEBUG,"tivi", "speex warn:%s", str);
 }
 void speex_warning_int(const char *str, int val){
    __android_log_print(ANDROID_LOG_DEBUG,"tivi", "speex warn:%s=%d", str, val);
 }
};

/*
 D/tivi    (20928): speex warn:Had to discard a playback frame (your application is buggy and/or got xruns)
 D/tivi    (20928): speex warn:Had to discard a playback frame (your application is buggy and/or got xruns)
 D/tivi    (20928): speex warn:No playback frame available (your application is buggy and/or got xruns)
 D/tivi    (20928): speex warn:Auto-filling the buffer (your application is buggy and/or got xruns)
 D/tivi    (20928): speex warn:Had to discard a playback frame (your application is buggy and/or got xruns)
 */


typedef struct {
   int   iSamples;
   SpeexEchoState *state;
   SpeexPreprocessState *preProcess;
   short		 *sTMP;
} SPEEX_AEC;

//float floor(float f){return f;}

//double _imp__floor(double d){return d;}
void *createSpeexAEC(int iRate, int iSamples, int iTailMs){
   
   SPEEX_AEC *ec=new SPEEX_AEC;
   if(!ec)return NULL;
   memset(ec,0,sizeof(SPEEX_AEC));
   ec->iSamples=iSamples;
   //    ec->options = options;
   int sz=512;
   int iSamplesTail=iRate*iTailMs/1000;
   while(sz<iSamplesTail)sz*=2;
#if 0
   /* Echo canceller with 200 ms tail length */
   SpeexEchoState *echo_state = speex_echo_state_init(FRAME_SIZE, 10*FRAME_SIZE);
   tmp = SAMPLING_RATE;
   speex_echo_ctl(echo_state, SPEEX_ECHO_SET_SAMPLING_RATE, &tmp);
   
   /* Setup preprocessor and associate with echo canceller for residual echo suppression */
   preprocess = speex_preprocess_state_init(FRAME_SIZE, SAMPLING_RATE);
   speex_preprocess_ctl(preprocess, SPEEX_PREPROCESS_SET_ECHO_STATE, echo_state);

#endif
// works   ec->state = speex_echo_state_init(iSamples,  iRate / 1000* iTailMs );
   //TODO use 250ms ,2048samples
   //or  1024 samples
   //sz=1600;
   
   ec->state = speex_echo_state_init(iSamples,sz);// 2048*4);//sz);//  );
   if (!ec->state) {

      delete ec;
      return NULL;
   }

   int sr = iRate;
   speex_echo_ctl(ec->state, SPEEX_ECHO_SET_SAMPLING_RATE, &sr);

   ec->preProcess = speex_preprocess_state_init(iSamples, iRate);
   if (!ec->preProcess) {
      speex_echo_state_destroy(ec->state);
      delete ec;
      return NULL;
   }

   //int i=1;speex_preprocess_ctl(ec->preProcess, SPEEX_PREPROCESS_SET_VAD, &i);
   speex_preprocess_ctl(ec->preProcess, SPEEX_PREPROCESS_SET_ECHO_STATE, ec->state);
   //speex_preprocess_ctl(ec->preProcess, SPEEX_PREPROCESS_SET_DEREVERB_LEVEL, &i);
   
   
   //test this
//   int ns=30;speex_preprocess_ctl(ec->preProcess, SPEEX_PREPROCESS_SET_NOISE_SUPPRESS, &ns);
   
#if 1
#if 1
   int es=55;speex_preprocess_ctl(ec->preProcess, SPEEX_PREPROCESS_SET_ECHO_SUPPRESS, &es);
   int as=55;speex_preprocess_ctl(ec->preProcess, SPEEX_PREPROCESS_SET_ECHO_SUPPRESS_ACTIVE, &as);
#else
   //tested ,works
   int ac=55;speex_preprocess_ctl(ec->preProcess, SPEEX_PREPROCESS_SET_ECHO_SUPPRESS_ACTIVE, &ac);
#endif
#endif


   ec->sTMP = new short[iSamples];
   return ec;
}

void relSpeexAec(void *p )
{
   SPEEX_AEC *ec = (SPEEX_AEC*) p;
   if(!ec)return;

   if (ec->state)speex_echo_state_destroy(ec->state);
   if (ec->preProcess)speex_preprocess_state_destroy(ec->preProcess);
}

void updatePlayback(void *p, short *play){
   SPEEX_AEC *ec = (SPEEX_AEC*) p;
   if(!ec)return;
   speex_echo_playback(ec->state, (const short*)play);
}

void updateAndFixMic(void *p, short *rec){
   SPEEX_AEC *ec = (SPEEX_AEC*) p;
   if(!ec)return;
   short *t=ec->sTMP;
   speex_echo_capture(ec->state, (const short*)rec, t);
   
   memcpy(rec, t, ec->iSamples * sizeof(short));
   speex_preprocess_run(ec->preProcess, rec);
   
}


void remEchoSpeexAec(void *p,short *rec, short *play)
{
   SPEEX_AEC *ec = (SPEEX_AEC*) p;
   if(!ec)return;
   short *t=ec->sTMP;
#if 0
   speex_echo_playback(ec->state, (const short*)play);
   speex_echo_capture(ec->state, (const short*)rec, t);
   
   memcpy(rec, t, ec->iSamples * sizeof(short));
   speex_preprocess_run(ec->preProcess, ( short*)rec);
#else
   //--
   speex_echo_cancellation(ec->state, (const short*)rec, (const short*)play, t);
   memcpy(rec, t, ec->iSamples * sizeof(short));
   speex_preprocess_run(ec->preProcess, (short*)rec);
   
  // memcpy(rec, t, ec->iSamples * sizeof(short));
#endif
}

void resetSpeexAec(void *p )
{
   SPEEX_AEC *ec = (SPEEX_AEC*) p;
   if(!ec)return;
   speex_echo_state_reset(ec->state);
}

#endif


static void *g_aec=NULL;

#define T_USE_MOB_AEC

#if defined(T_USE_MOB_AEC)
#include "webrtc_ec/echo_control_mobile.h"
#else
#include "webrtc_ec/echo_cancellation.h"
#endif

static int iLastEchoMode=3;

void *initAEC(int iRate){
   void *aec=NULL;
   void *aec_to_del=NULL;
   WebRtc_Word32 r;
#if defined(T_USE_MOB_AEC)
   
   __android_log_print(ANDROID_LOG_DEBUG,"tivi", "aecm_init");
   
   if(g_aec){
      aec_to_del=g_aec;
      
   }
   r =  WebRtcAecm_Create(&aec);
   r |= WebRtcAecm_Init(aec, iRate);
   
   
   AecmConfig c;
   c.cngMode=AecmTrue;//AecmFalse;
   c.echoMode=iLastEchoMode;
   
   r|=WebRtcAecm_set_config(aec, c);
#else
   __android_log_print(ANDROID_LOG_DEBUG,"tivi", "aec_init");
   
   r =  WebRtcAec_Create(&aec);
   r |= WebRtcAec_Init(aec, iRate,iRate);
#endif
   
   if(r)puts("err");
   g_aec=aec;
#if defined(T_USE_MOB_AEC)
   if(aec_to_del)
      WebRtcAecm_Free(aec_to_del);
#else
   if(aec_to_del)
      WebRtcAec_Free(aec_to_del);
#endif
   return g_aec;
}
/*
 case EchoControlMobile::kQuietEarpieceOrHeadset:
 return 0;
 case EchoControlMobile::kEarpiece:
 return 1;
 case EchoControlMobile::kLoudEarpiece:
 return 2;
 case EchoControlMobile::kSpeakerphone:
 return 3;
 case EchoControlMobile::kLoudSpeakerphone:
 return 4;
 */

void setSpkrModeAEC(void *aec, int echoMode){
#if defined(T_USE_MOB_AEC)
   iLastEchoMode=echoMode;
   if(!aec){
      aec = g_aec;
   }
   if(!aec)return;
   AecmConfig c;
   c.cngMode=AecmTrue;//AecmFalse;
   c.echoMode=echoMode;
   WebRtcAecm_set_config(aec, c);
#endif
}

int playbackBufAEC(void *aec, short *p, int iSamples){
   
   if(!aec){
      aec = g_aec;
   }
   
   WebRtc_Word32 r=0;
   
#if defined(T_USE_MOB_AEC)
   r|=WebRtcAecm_BufferFarend(aec, (const WebRtc_Word16*) p, 160);
#else
   r|=WebRtcAec_BufferFarend(aec, (const WebRtc_Word16*) p, 160);
   
#endif
   
   return 0;
}
int recordProcessAEC(void *aec, short *p, short *outBuf, int iSamples, int delayMS){
   
   //  short *outBuf=p;
   if(!aec){
      aec = g_aec;
   }
   int iOutS=0;
   WebRtc_Word32 r=0;
#if defined(T_USE_MOB_AEC)

   r |= WebRtcAecm_Process(aec,p, NULL, &outBuf[iOutS],160, delayMS);
#else
   r |= WebRtcAec_Process(aec,
                          p,
                          NULL,
                          &outBuf[iOutS],
                          NULL,
                          160,
                          100,//delayMS,
                          0);//delayMS*16000);
#endif
   return iOutS;
}



