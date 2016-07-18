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

#ifndef _WIN32
#include <unistd.h>//sleep
#endif

#include <stdio.h>
#include "../baseclasses/CTBase.h"

#include "CTRTPVideoPlayer.h"
#include "../codecs/CTVidCodec.h"
unsigned int getTickCount();


#ifdef _WIN32
#define VID_BUF_ST_SIZE 4*1024*1024
#else

#define VID_BUF_ST_SIZE 500*1024
//test #define VID_BUF_ST_SIZE (20*1024+10000+7000*10)
#endif

#define T_VIDEO_MEM_SAFE_BUF_ADD 1000

unsigned char *CTVideoMem::getBuf(){
   if(!pMem)pMem=new unsigned char[VID_BUF_ST_SIZE+1024*128+T_VIDEO_MEM_SAFE_BUF_ADD];
  // printf("[get:iVB_bytesFree=%d,%d]\n",iVB_bytesFree,VID_BUF_ST_SIZE);
   return pMem;
}

CTVideoMem::CTVideoMem(){pMem=NULL;iNextPos=0;iVB_bytesFree=VID_BUF_ST_SIZE;}


unsigned char *CTVideoMem::getVidFrameBuf(int sz){
   
   //TODO move to CTVideoMem
   unsigned char *vb=getBuf();
   
   //we can handle 7pack out off order
   if(iVB_bytesFree<sz+10000+7000*10)return 0;

   
   int nsz=(((sz+32)>>4)<<4);
   
   if(iNextPos+nsz>VID_BUF_ST_SIZE + T_VIDEO_MEM_SAFE_BUF_ADD)iNextPos=0;

   
   unsigned char *ret=&vb[iNextPos];
   *(int*)ret=nsz;
   ret+=sizeof(int);

//   [reset2:iVB_bytesFree=98592]
  // [reset2:iVB_bytesFree=93248]
   //[reset2:iVB_bytesFree=91408]
   iVB_bytesFree-=nsz;
   iNextPos+=nsz;
   
   return ret;
}

void CTVideoMem::relVidFrameBuf(unsigned char *p){
   
   if(!p || !pMem){puts("[V-ERR:1]");return;}
   if(p<pMem || p>pMem+VID_BUF_ST_SIZE+T_VIDEO_MEM_SAFE_BUF_ADD){puts("[V-ERR:2]");return;}
   int n = *(int*)(&p[-(signed int)sizeof(int)]);
   if(n<1 || n>VID_BUF_ST_SIZE){puts("[V-ERR:3]");return;}
   if(p<pMem || p+n>pMem+VID_BUF_ST_SIZE+T_VIDEO_MEM_SAFE_BUF_ADD){puts("[V-ERR:4]");return;}
   iVB_bytesFree+=n;
   return;
}

int CTRTPVideoPlayer::reset(){
   iCurrentPlayFrame=0;
   iFramesAfterAudio=0;
   iPrevRecFPS=8;
   uiPrevVideoTS=0;
   iAudioRate=8000;
   iInVideoThread=0;
   iAudioMult=16;
//   printf("[reset1:iVB_bytesFree=%d]\n",glob_video_mem_size);
   vmutex.lock();
   videoFrameList.removeAll();
   vmutex.unLock();
 //  printf("[reset2:iVB_bytesFree=%d]\n",glob_video_mem_size);
   return 0;
}

CTRTPVideoPlayer::CTRTPVideoPlayer(){ ao = NULL; vo = NULL; reset(); g_iFrameID = 0; iStarted = 0; iEnding = 0; }
CTRTPVideoPlayer::~CTRTPVideoPlayer(){int iWait=iStarted;stop();if(iWait)Sleep(10);}

int CTRTPVideoPlayer::start(){
   reset();
   if(iStarted)return 0;
   iEnding=0;
   iStarted=2;
   vthread.create(s_videoThread,this);
   vthread.closeHandle();
   return 0;
}

void CTRTPVideoPlayer::stop(){
   iStarted=0;
   iEnding=1;
}

void CTRTPVideoPlayer::setCallbacks(CTAudioOutBase *a, CTVideoOutBase *v){
   ao=a;
   vo=v;
   iAudioRate=a->getRate();
   iAudioMult=iAudioRate/1000;
   //TODO getShifter to sec
}


void CTRTPVideoPlayer::onReceiveAudio(unsigned int  uiAudioTS){

   uiReceivedAudioTS=uiAudioTS/iAudioMult;
   uiReceivedAudioAt=getTickCount();
   
   iFramesAfterAudio=0;
   iPartsAfterAudio=0;
   
}

int CTRTPVideoPlayer::onReceiveRTP(CVCodecBase *c, unsigned short seq, unsigned int uiTS, unsigned char *p, int iLen){
   
   if(!ao || !vo || !iStarted || iEnding){
      return -1;
   }
   
   
   unsigned int uiCurFramePlaybackTS;
   
   if(uiPrevVideoTS!=uiTS){
      uiNextFrameTS=uiReceivedAudioTS+(1000*iFramesAfterAudio/iPrevRecFPS);
      iFramesAfterAudio++;
   }
   

   uiCurFramePlaybackTS=uiNextFrameTS;
   iPartsAfterAudio++;
   
   
   
   uiPrevAudioTSUsed=uiReceivedAudioTS;
   
   
   if(uiPrevVideoTS!=uiTS){
      int iPosibleErr=0;
      int dif=(int)(uiTS-uiPrevVideoTS);
      if(dif<16 || dif>1000){dif=80;iPosibleErr=1;}
      
      int iNextFPS=1000/dif;
      if(iPosibleErr)
         iPrevRecFPS=(iPrevRecFPS*3+iNextFPS+2)>>2;
      else
         iPrevRecFPS=(iPrevRecFPS+iNextFPS+1)>>1;
      
   }
   
   uiPrevVideoTS=uiTS;

   uiCurFramePlaybackTS+=c->videoAhead();
   
   uiCurFramePlaybackTS-=30;//playback delay
   
   vmutex.lock();

   videoFrameList.addFrame(p,iLen,seq, uiCurFramePlaybackTS,iPrevRecFPS,c);
   vmutex.unLock();
   
   g_iFrameID++;
   
   return 0;
   
}

unsigned int CTRTPVideoPlayer::getCurrentTime(){
   return ao->getSamplePlayPos()/iAudioMult;
}

inline void videoSleep(int *pStared, int ms){
   if(ms<20 || ms>3000){Sleep(1);return ;}
   
   unsigned int endT=getTickCount()+ms;
   
   while(*pStared && ms>120){
      Sleep(100);
      ms-=100;
      if(endT<getTickCount()+20)break;
   }
#ifdef _WIN32
   while(*pStared && ms>20){
      Sleep(18);
      ms-=18;
      if(endT<getTickCount()+10)break;
   }
#else
   while(*pStared && ms>15){
      Sleep(12);
      ms-=12;
      if(endT<getTickCount()+10)break;
   }
#endif
}


int CTRTPVideoPlayer::videoThread(){
   iInVideoThread=1;
   iStarted=1;
   
   int iDecSpeed=20;
   CListItemFrame *f=NULL;

#ifdef _WIN32
   unsigned int uiStartTime=getTickCount();
#define ZERO_SLEEP Sleep(0)
   unsigned int uiSleepsPerMSec=0;// no usleep for win32
   unsigned int uiToTest=uiStartTime+(1<<5);//32
   while(uiToTest<getTickCount()){ZERO_SLEEP;uiSleepsPerMSec++;}
   uiSleepsPerMSec>>=5; //div 32
   if(uiSleepsPerMSec<1)uiSleepsPerMSec=1;

#else
#define ZERO_SLEEP usleep(300)
#endif

   
   //   iDecodeTime
   int iMaxSk=1;
   int iSkipNext=0;
   int iDontSkipNext=1;
   int iFPS=8;
   
   
   
   while(iStarted && !iEnding)
   {

      vmutex.lock();

      
      f=(CListItemFrame*)videoFrameList.getNext(NULL);
      if(!f){
         vmutex.unLock();
         Sleep(15);
         continue;
      }
      //  printf("videoFrameList.countVisItems()=%d\n",videoFrameList.countVisItems());
      if(videoFrameList.countVisItems()>30*4*4){//TODO count real frames
         while(videoFrameList.countVisItems()>30*4*4){
            f=(CListItemFrame*)videoFrameList.getNext(NULL);//rem from root
            videoFrameList.remove(f);
         }
         f=(CListItemFrame*)videoFrameList.getNext(NULL);
         if(!f){
            vmutex.unLock();
            Sleep(15);
            continue;
         }
      }
      iFPS=f->iPrevRecFPS;
      
      int uiLastTC=getTickCount();
#define T_USE_PP
#ifdef T_USE_PP
      unsigned int uiPlayPos=getCurrentTime();
#else
      unsigned int uiDifTS=uiLastTC-uiTimeAFStart;if(uiDifTS>6000)uiDifTS=0;
      unsigned int uiPlayPos=uiDifTS+(uiPlayAudioSamplePos>>3);
#endif
      
      int iDif=(int)(f->uiTS-uiPlayPos);

      if((iDontSkipNext && iDif>-50) || (iSkipNext==0 && (iDif>-10 || iMaxSk<=0))){
         
         videoFrameList.remove(f,0);
         vmutex.unLock();
         
         int iDecSpPrev=iDecSpeed;
         iDecSpeed=uiLastTC;//getTickCount();
         decodeShowVideoFrame(f);
         int tcn=getTickCount();
         iDecSpeed=tcn-iDecSpeed;
         int d1000_fps=1000/(iFPS+1);
         
         iSkipNext=(d1000_fps*4)<iDecSpeed*5;
         
         iDecSpeed+=iDecSpPrev;iDecSpeed>>=1;
         iDontSkipNext=(3*d1000_fps)<(iDecSpeed*4);
         
         unsigned int fts=f->uiTS;
         delete f;
         
#if 1
         //!defined(_WIN32)
         uiPlayPos=getCurrentTime();
         iDif=(int)(fts-uiPlayPos);
         if(iDif>10 && iDif<4000){videoSleep(&iStarted,iDif);}else iMaxSk=1;
         vo->drawFrame();
         

#else
         //Sleep(100);
#if 1
#ifdef T_USE_PP
         uiPlayPos=getCurrentTime();
#else
         uiDifTS=tcn-uiTimeAFStart; if(uiDifTS>6000)uiDifTS=0;
         uiPlayPos=uiDifTS+(uiPlayAudioSamplePos>>3);
#endif
         
         //if time start decode next frame here
         //or decode vectors only from next frame
         //int ttS=(int)(fts-uiPlayPos);
         //if(ttS>15){Sleep(ttS>32?32:15);}
         //g_pDD->WaitForVerticalBlank(DDWAITVB_BLOCKEND,NULL);
#if 1
      repx:
         if(fts>uiPlayPos+8){
            int ttS=(int)(fts-uiPlayPos)-5;
            if(ttS>8)Sleep(15);
         }
         
         //else 
         {
#ifdef T_USE_PP
            uiPlayPos=getCurrentTime();
#else
            uiDifTS=tcn-uiTimeAFStart; if(uiDifTS>6000)uiDifTS=0;
            uiPlayPos=uiDifTS+(uiPlayAudioSamplePos>>3);
#endif
            int ttS=(int)(fts-uiPlayPos);
            if(ttS<80 && ttS>0){
               if(ttS>12)goto repx;
               if(ttS<7){
                  int sl_x=(ttS)*uiSleepsPerMSec;;
                 // if(sl_x<uiSleepsPerMSec>>8)sl_x=uiSleepsPerMSec>>8;
                  for(int i=0;i<sl_x;i++)ZERO_SLEEP;
               }
            }
         }

#endif
#endif
         //--cb->onFrame();
         vo->drawFrame();
         iMaxSk=1;
#endif
         
      }
      else{
         
         videoFrameList.remove(f,0);
         vmutex.unLock();

         
         int sp=1000/(iFPS+1);
         if(iMaxSk<=0){
            iMaxSk=sp<iDecSpeed;//iDecSpeed;
            if(iMaxSk)iMaxSk=(iDecSpeed/(sp+1));
         }
         else iMaxSk--;
         if(iSkipNext || sp*2<iDecSpeed || iDif<-(sp>>2)){
            decodeShowVideoFrame(f,0);
            iSkipNext=0;
         }
         else{
            
            decodeShowVideoFrame(f);
            
            iDif=(int)(f->uiTS-getCurrentTime());
            videoSleep(&iStarted,iDif);
            
            vo->drawFrame();

         }
         
         delete f;
         
      }
      
      
   }

   iInVideoThread=0;
   return 0;
}
CListItemFrame *CTRTPVideoPlayer::getCurFrameSafeRemoveFromList(unsigned int uiTS){
   CListItemFrame *fr;
   vmutex.lock();
   fr=(CListItemFrame*)videoFrameList.getNext(NULL);
   if(!fr || fr->uiTS!=uiTS){
      fr=NULL;
      vmutex.unLock();
      return NULL;
   }
   videoFrameList.remove(fr,0);
   vmutex.unLock();
   return fr;
}

int CTRTPVideoPlayer::s_videoThread(void *p)
{
   return ((CTRTPVideoPlayer*)p)->videoThread();
}


int CTRTPVideoPlayer::decodeShowVideoFrame(CListItemFrame *fr, int iShow)
{
   CVCodecBase *c=fr->codec;
   unsigned int uiTS=fr->uiTS;
   
   if(iShow)
   {
      c->cVO=vo;
   }
   else{
      if(c->canSkipThis((unsigned char*)fr->pBuf,fr->iLen)){
         while(fr){
            fr=getCurFrameSafeRemoveFromList(uiTS);
            if(fr)delete fr;
         }
         iCurrentPlayFrame++;
         return 0;
      }
   }

   
   CListItemFrame *frameToDelete=NULL;
   
   while(iStarted && !iEnding){
      
      unsigned char *pFrameData=fr->pBuf;
      int r=c->decode(pFrameData,NULL,fr->iLen);
      if(r<0)break;
      
      int iCanD=c->wasDrawableFrame(pFrameData,fr->iLen);

      if(iCanD)break;
      
      
      fr=getCurFrameSafeRemoveFromList(uiTS);
      if(!fr)break;
      
      if(frameToDelete){delete frameToDelete;}
      frameToDelete=fr;
      
   }
   if(frameToDelete){delete frameToDelete;}
   iCurrentPlayFrame++;

   c->cVO=NULL;
   return 0;
}


