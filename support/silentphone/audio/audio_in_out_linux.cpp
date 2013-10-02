/*
Created by Janis Narbuts
Copyright © 2004-2012, Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC. All rights reserved.

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
#include <sys/ioctl.h>
#include <sys/soundcard.h>
#include "../os/CTThread.h"


static int t_sync_audio(int f, int ms, int iIsRecording){
   int status=-1;
   
   // status=ioctl(f, SNDCTL_DSP_SYNC);
   //  status = ioctl(f, SOUND_PCM_SYNC, 0);
   
   if(status<0){
      //  perror(iIsRecording?"t_sync_audio_rec":"t_sync_audio_play");
      usleep(ms*1000);
   }
   return status;  
}

//int fx=0;

static int init_sound(char *dev,int iIn, int iBits, int iCh, int iRate)
{
   
   int fd;
   puts("1");
   /*
    if(iIn)
    fd = open(dev,O_RDONLY,0);//O_RDWR);//O_RDONLY);//O_RDWR);// O_RDONLY, 1);
    else
    fd = open(dev, O_WRONLY,0);//, 1);
    if( fd < 0 )
    return(-1);
    */
   int fx=0;
   if(fx==0)
   {
      fd = open(dev,iIn?O_RDONLY:O_WRONLY);//O_RDWR);
      fx=fd;
   }
   
   puts("2");
   int status,arg;
   
   arg = iBits;//speed;//SIZE;      /* sample size */
   status = ioctl(fd, SOUND_PCM_WRITE_BITS, &arg);
   //status = ioctl(fd, SOUND_PCM_READ_BITS, &arg);
   if (status == -1)
      perror("SOUND_PCM_WRITE_BITS ioctl failed");
   if (arg != iBits)
      perror("unable to set sample size");
   
   arg = iCh;//CHANNELS;  /* mono or stereo */
   status = ioctl(fd, SOUND_PCM_WRITE_CHANNELS, &arg);
   //status = ioctl(fd, SOUND_PCM_READ_CHANNELS, &arg);
   if (status == -1)
      perror("SOUND_PCM_WRITE_CHANNELS ioctl failed");
   
   if (arg != iCh)
      perror("unable to set number of channels");
   
   arg = iRate;      /* sampling rate */
   status = ioctl(fd, SOUND_PCM_WRITE_RATE, &arg);
   // status = ioctl(fd, SOUND_PCM_READ_RATE, &arg);
   if (status == -1)
      perror("SOUND_PCM_WRITE_WRITE ioctl failed");
   
   
   /*
    
    if(iIn)
    {
    
    // int setting=640;
    int frag_size = ((int) 12 << 16) | 10;
    if ( ioctl(fd, SNDCTL_DSP_SETFRAGMENT, &frag_size) == -1 ) {
    perror("ioctl set fragment");
    return -1;
    }
    }
    
    
    
    if (ioctl(fd, SNDCTL_DSP_SETFMT, &sampling) == -1)
    return(-1);
    
    
    
    if (ioctl(fd, SNDCTL_DSP_STEREO, &stereo) == -1)
    return(-1);
    
    
    
    if (ioctl(fd, SNDCTL_DSP_SPEED, &speed) == -1)
    return(-1);
    */
   
   return fd;
}

class CTAudioIn{
   
   void *cbRecData;
   int (*cbRecFnc)(void *, short *, int );
   
   int iRate;
   
   int iRecording;
   int fd;
   
   char *bufIn;
   int iBlockSize;
   CTThread th;
   
public:
   CTAudioIn(int iRateIn)	
   :iRate(iRateIn)
   ,iRecording(0)
   ,fd(0)
   ,iBlockSize(640)
   {
      //int init_sound(char *dev,int iIn, int iBits, int iCh, int iRate)
      //puts("init rec audio");
      bufIn=new char [iBlockSize];
      int iBitsPerSample=16;
      fd=init_sound("/dev/dsp",1,iBitsPerSample,1,iRate);
      /*
       if(fd<=0)
       perror("CTAudioIn");
       perror("CTAudioIn");
       perror("CTAudioIn");
       */	
      
   }
   ~CTAudioIn(){stop();if(fd)close(fd);delete bufIn;}
   int record(int (*cbFnc)(void *, short *, int ), void *cbData)
   {
      cbRecFnc=cbFnc;
      cbRecData=cbData;
      
      if(!iRecording && fd)
      {
         puts("record");
         iRecording=1;
         th.create(&CTAudioIn::threadFnc,(void *)this);
      }
      return 0;
   }
   int thRec()
   {
      int iSize;
      char *bufSend=new char [iBlockSize];
      int iSleepMS=1000/(iRate*2/iBlockSize);
      puts("record ok");
      
      while(iRecording)
      {
         iSize=read(fd,bufIn,iBlockSize);
         // usleep(40000);
         
         if(iSize==iBlockSize)
         {
            memcpy(bufSend,bufIn,iBlockSize);
            cbRecFnc(cbRecData,(short*) bufSend, iBlockSize>>1);
            
            t_sync_audio(fd,iSleepMS,1);
         }
         else
         {
            perror("read");
            usleep(100000);
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

class CTAudioOut{
   
   char *buf;
   
   int iRate;
   int iRestarted;
   
   int fd;
   
   int iPlayBlockSize;
   int iBufSize;
   int iPlaying;
   
   CTThread th;
   
   
   void *cbPlayData;
   int (*cbFncPlay)(void *, short *, int , int iRestarted);
   
public:
   CTAudioOut(int iRate)
   :iBufSize(iRate*16/8*3)
   ,iRate(iRate)
   ,iPlaying(0)
   ,iPlayBlockSize(640)
   
   {
      int iBitsPerSample=16;
      int iCh=1;
      iRestarted=0;
      fd=0;
      buf=new char [iBufSize];
      puts("init CTAuidoOut");
      
      //	fd=init_sound("/dev/dsp",0,AFMT_S16_LE,iCh==2?1:0,8000);//iRate);//AFMT_U8 AFMT_S16_LE
      fd=init_sound("/dev/dsp",0,iBitsPerSample,iCh,iRate);
      
      if(fd<0)
         perror("CTAuidoOut");
   }
   
   ~CTAudioOut(){stop();if(fd)close(fd);delete buf;}
   
   
   
   int play(int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData)
   {
      cbPlayData=cbData;
      cbFncPlay=cbFnc;
      
      iRestarted=1;
      
      if(!iPlaying)
      {
         puts("create play");
         iPlaying=1;
         
         int i=th.create(&CTAudioOut::threadFnc,(void *)this);
         printf("th create %d\n",i);
      }	
      return 0;
   }
   int thPlay()
   {
      puts("th play ok");
      int iTmp=0;
      int iSleepMS=1000/(iRate*2/iPlayBlockSize);
      while(iPlaying)
      {
         //memcpy(bufPlay,buf+iCurentPlayPos,iPlayBlockSize);
         
         cbFncPlay(cbPlayData,(short*)buf,iPlayBlockSize>>1,iRestarted);
         iRestarted=0;      
         
         iTmp=write(fd,buf,iPlayBlockSize);
         if(iTmp<0)
         {
            perror("write");
            usleep(100000);
            continue;
         }
         
         t_sync_audio(fd,iSleepMS,0);
         
         
         
         //puts("write");
         //TODO test
         /*
          if (ioctl(fd, SNDCTL_DSP_SYNC) == -1)
          {
          perror("SNDCTL_DSP_SYNC");
          usleep(100000);
          continue;
          }
          */		
         
         
      }
      iPlaying=0;
      //delete bufPlay;
      return 0;
   }
   static int threadFnc(void *f)
   {
      return ((CTAudioOut *)f)->thPlay();
   }
   
   void stop()
   {
      
      puts("stop play");
      iPlaying=0;
      th.close();
      //return 0;
   }
   
   
   
};



void *t_native_startCB_play(void *ctx, int iRate, int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData){
   if(((int)ctx)<2)ctx=NULL;
   CTAudioOut *a=(CTAudioOut*)ctx; 
   if(!a){
      a=new CTAudioOut(iRate);
   }
   a->play(cbFnc,cbData);
   return a;
   
}

void *t_native_startCB_rec(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
   if(((int)ctx)<2)ctx=NULL;
   CTAudioIn *a=(CTAudioIn*)ctx; 
   if(!a){
      a=new CTAudioIn(iRate);
   }
   
   a->record(cbFnc,cbData);
   return a;
}

void *t_native_PstartCB(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
   
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

void t_onOverFlow(void *ctx){
   
   
}

void t_native_stop(void *ctx, int iPlay){
   if(((int)ctx)<2 || !ctx)return ;
   
   if(iPlay){
      CTAudioOut *a=(CTAudioOut*)ctx; 
      if(a){
         a->stop();
      }
   }
   else{
      CTAudioIn *a=(CTAudioIn*)ctx; 
      if(a){
         a->stop();
      }
   }
   
   return;
}

void t_native_rel(void *ctx){
   //TODO
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
