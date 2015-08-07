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
#ifdef __linux__

//#define _TEST_AUDIO
/*
 //TODO try to use
 http://svn.xiph.org/trunk/speex/speexclient/
 http://svn.xiph.org/trunk/speex/speexclient/alsa_device.c
 http://svn.xiph.org/trunk/speex/speexclient/alsa_device.h
 */

#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <pulse/simple.h>
#include <pulse/error.h>

#include "../os/CTThread.h"


static int t_sync_audio(int ms, int iIsRecording){
    int status=-1;
    if(status<0){
        //  perror(iIsRecording?"t_sync_audio_rec":"t_sync_audio_play");
        usleep(ms*1000);
    }
    return status;  
}

/* The Sample format to use for PulseAudio */
static pa_sample_spec ss = {
    .format = PA_SAMPLE_S16LE,
    .rate = 16000,
    .channels = 1
};

static pa_simple *init_sound(int in, int iBits, int iCh, int iRate)
{
    pa_simple *s = NULL;
    int error;

    ss.rate = iRate;
    ss.channels = iCh;
    if (iBits == 16)
        ss.format = PA_SAMPLE_S16LE;

    /* Create a new playback stream */
    if (!(s = pa_simple_new(NULL, "SilentPhone" , in ? PA_STREAM_RECORD : PA_STREAM_PLAYBACK, NULL, (in ? "record" : "playback"), &ss, NULL, NULL, &error))) {
        fprintf(stderr, __FILE__": pa_simple_new() failed: %s\n", pa_strerror(error));
    }

    return s;
}

class CTAudioIn {

    void *cbRecData;
    int (*cbRecFnc)(void *, short *, int );
    int iRate;
    int iRecording;
    pa_simple *paSimple;
    char *bufIn;
    int iBlockSize;
    CTThread th;

public:
    CTAudioIn(int iRateIn): iRate(iRateIn), iRecording(0), paSimple(NULL), iBlockSize(640)
    {
        //int init_sound(char *dev,int iIn, int iBits, int iCh, int iRate)
        //puts("init rec audio");
        bufIn = new char[iBlockSize];
        int iBitsPerSample=16;
        paSimple = init_sound(1, iBitsPerSample, 1, iRate);
    }
    ~CTAudioIn() {
        stop();
        if (paSimple) {
            pa_simple_free(paSimple);
            paSimple = NULL;
        }
        delete bufIn;
    }

    int record(int (*cbFnc)(void *, short *, int ), void *cbData)
    {
        cbRecFnc=cbFnc;
        cbRecData=cbData;

        if (!iRecording && paSimple) {
            iRecording=1;
            th.create(&CTAudioIn::threadFnc,(void *)this);
        }
        return 0;
    }

    int thRec()
    {
        int iSize = 0;
        int error;
        char *bufSend=new char[iBlockSize];
        int iSleepMS = 1000/(iRate*2/iBlockSize);

        while (iRecording) {
            // Synchronous call. Waits until buffer is full - 640 bytes at 16000, 16bit sample -> 320 samples, 20ms
            if (pa_simple_read(paSimple, bufIn, iBlockSize, &error) < 0) {
                fprintf(stderr, __FILE__": pa_simple_read() failed: %s\n", pa_strerror(error));
                usleep(100000);
            }
            else {
                memcpy(bufSend, bufIn, iBlockSize);
                cbRecFnc(cbRecData, (short*)bufSend, iBlockSize>>1);
//                t_sync_audio(iSleepMS, 1);
            }
        }
        delete bufSend;
        return 0;
    }

    static int threadFnc(void *f)
    {
        return ((CTAudioIn *)f)->thRec();
    }

    void stop()
    {
        iRecording=0;
        th.close();
        puts("stop rec");
    }
};

class CTAudioOut {

    char *buf;

    int iRate;
    int iRestarted;

    pa_simple *paSimple;

    int iPlayBlockSize;
    int iBufSize;
    int iPlaying;
    CTThread th;
    void *cbPlayData;
    int (*cbFncPlay)(void *, short *, int , int iRestarted);

public:
    CTAudioOut(int iRate): iBufSize(iRate*16/8*3), iRate(iRate), iPlaying(0), iPlayBlockSize(640)
    {
        int iBitsPerSample=16;
        int iCh=1;
        iRestarted=0;
        paSimple = NULL;
        buf=new char [iBufSize];
        puts("init CTAuidoOut");

        paSimple = init_sound(0, iBitsPerSample, 1, iRate);
    }

    ~CTAudioOut() {
        stop();
        if (paSimple) {
            pa_simple_free(paSimple);
            paSimple = NULL;
        }
        delete buf;
    }

    int play(int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData)
    {
        cbPlayData=cbData;
        cbFncPlay=cbFnc;
        iRestarted = 1;

        if (!iPlaying) {
            iPlaying = 1;
            th.create(&CTAudioOut::threadFnc, (void *)this);
        }
        return 0;
    }

    int thPlay()
    {
        int error;
        int iSleepMS = 1000/(iRate*2/iPlayBlockSize);  // results to 20ms

        while (iPlaying) {
            cbFncPlay(cbPlayData, (short*)buf, iPlayBlockSize>>1, iRestarted);
            iRestarted = 0;

            /* ... and play it */
            if (pa_simple_write(paSimple, buf, (size_t)iPlayBlockSize , &error) < 0) {
                fprintf(stderr, __FILE__": pa_simple_write() failed: %s\n", pa_strerror(error));
                usleep(100000);
                continue;
            }
            t_sync_audio(iSleepMS, 0);
        }
        iPlaying = 0;
        return 0;
    }

    static int threadFnc(void *f)
    {
        return ((CTAudioOut *)f)->thPlay();
    }

    void stop()
    {
        iPlaying = 0;
        th.close();
    }
};


void *t_native_startCB_play(void *ctx, int iRate, int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData)
{
    if (((int)ctx) < 2)
        ctx = NULL;
    CTAudioOut *a = (CTAudioOut*)ctx; 
    if (!a) {
        a=new CTAudioOut(iRate);
    }
    a->play(cbFnc, cbData);
    return a;
}

void *t_native_startCB_rec(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData)
{
    if (((int)ctx) < 2)
        ctx = NULL;
    CTAudioIn *a = (CTAudioIn*)ctx; 
    if (!a) {
        a=new CTAudioIn(iRate);
    }
    a->record(cbFnc,cbData);
    return a;
}

void *t_native_PstartCB(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData)
{
    return NULL;
}

void t_onOverFlow(void *ctx){}

void t_native_stop(void *ctx, int iPlay)
{
    if (((int)ctx) < 2 || !ctx)
        return;

    if (iPlay) {
        CTAudioOut *a = (CTAudioOut*)ctx; 
        if(a) {
            a->stop();
        }
    }
    else {
        CTAudioIn *a=(CTAudioIn*)ctx; 
        if (a) {
            a->stop();
        }
    }
    return;
}

void t_native_rel(void *ctx)
{
   //TODO
   return;
}

void *getAudioCtx(int iRate, int iRel)
{
    return (void*)1;
}

void *getPAudioCtx(int iRate)
{
    return (void*)1;
}

void t_native_Prel(void *ctx)
{
    return;
}

void t_native_Pstop(void *ctx)
{
    return;
}

int t_native_PRate(void *ctx)
{
    return 8000;
}


#ifdef _TEST_AUDIO

short tmp[100000];
int iRecPos=0;
int iPlayPos=0;

int cbFncPlay(void *, short *p, int iSamples, int iRestarted){
   static int cnt=0;cnt++;
   printf("play %d %p %d pp=%d\n",cnt,p,iSamples,iPlayPos);
   int i=0;
   if(iRecPos<iSamples*2)return 0;
   
   if((iPlayPos+iSamples)*2<sizeof(tmp)){
      for(i=0;i<iSamples;i++){
         p[i]=tmp[iPlayPos];iPlayPos++;
      }
   }
   
   return 0;
}

int cbFncRec(void *, short *p, int iSamples){
   static int cnt=0;cnt++;
   printf("rec %d %p %d rp=%d\n",cnt,p,iSamples,iRecPos);
   if((iRecPos+iSamples)*2>=sizeof(tmp)){
      return -1;
   }  
   
   for(int i=0;i<4;i++){printf("%04x ",p[i]);}
   memcpy(&tmp[iRecPos],p,iSamples*sizeof(short));
   iRecPos+=iSamples;
   return 0;
}
int main(){
   void *rec=t_native_startCB_rec(NULL,16000,cbFncRec,NULL);
   void *play=t_native_startCB_play(NULL,16000,cbFncPlay,NULL);
   sleep(3);
   t_native_stop(rec,1);
   t_native_stop(play,0);
   sleep(3);
   return 0;
}
#endif



#endif
