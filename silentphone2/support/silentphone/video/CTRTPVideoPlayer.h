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

#ifndef _C_T_RTP_VIDEO_PLAYER
#define _C_T_RTP_VIDEO_PLAYER
#include <string.h>

#include "../os/CTThread.h"
#include "../os/CTMutex.h"
#include "../baseclasses/CTListBase.h"




class CVCodecBase;
class CTAudioOutBase;
class CTVideoOutBase;

class CTVideoMem{
   unsigned char *pMem;
   int iNextPos;
   int iVB_bytesFree;
   unsigned char *getBuf();
public:
   CTVideoMem();
   ~CTVideoMem(){if(pMem)delete pMem;pMem=NULL;}
   unsigned char *getVidFrameBuf(int sz);
   void relVidFrameBuf(unsigned char *p);
};

class CListItemFrame: public CListItem{
public:

   unsigned int uiTS;

   unsigned short seq;
   unsigned char *pBuf;
   int iLen;
   int iPrevRecFPS;
   int iPosDecoded;
   int iShow;
   
   CVCodecBase *codec;
   CTVideoMem *mem;
   
   static CListItemFrame *newc(CTVideoMem &mem, unsigned char *p, int iPicLen, unsigned short seq, unsigned int uiTimeStamp, int iPrevRecFPS, CVCodecBase *c)
   {
      CListItemFrame *ret=new CListItemFrame(iPicLen,uiTimeStamp, iPrevRecFPS);
      
      if(!ret)return NULL;
      
      ret->codec=c;
      ret->pBuf=mem.getVidFrameBuf(iPicLen+1);//new unsigned char [iPicLen+1];
      if(!ret->pBuf){
         delete ret;
         return NULL;
      }
      memcpy(ret->pBuf,p,iPicLen);
      ret->mem=&mem;
      ret->seq = seq;
      return ret;
   }
   virtual ~CListItemFrame(){
      void relVidFrameBuf(unsigned char *p);
      if(mem)mem->relVidFrameBuf(pBuf);
      pBuf=NULL;
   }
private:
   CListItemFrame(int iLen,unsigned int uiTS, int iPrevRecFPS)
   :CListItem((int)uiTS),iLen(iLen),uiTS(uiTS),iPrevRecFPS(iPrevRecFPS)
   {
      mem=NULL;
      pBuf=NULL;//&pBufZ[0];
      iPosDecoded=0;
      iShow=0;
   }
   
};

class CTFrameList: public CTList{
   int iFramesInList;


   CTVideoMem vmem;
public:
   CTFrameList():CTList(){iFramesInList=0;}
   int addFrame(unsigned char *p, int iLen, unsigned short seq, unsigned int uiPos, int iPrevRecFPS, CVCodecBase *c)
   {
      CListItemFrame *l = CListItemFrame::newc(vmem, p,iLen,seq,uiPos, iPrevRecFPS,c);
      if(l){
         //TODO check how many bytes we can have out of order
         //TODO drop if it is too late to play
         CListItemFrame *tmp = (CListItemFrame*)tail;
         while(tmp){
            short d = (short)(tmp->seq - seq);
            if(d<0)break;
            tmp = (CListItemFrame *)tmp->prev;
         }
         addAfter(tmp, l);

         iFramesInList++;
      }
      return l?0:-1;
   }
   inline int framesIn(){return iFramesInList;}
   virtual void onRemove(CListItem *item){iFramesInList--;}
   
};


class CTRTPVideoPlayer{
   
   CTMutex vmutex;
   
   CTAudioOutBase *ao;
   CTVideoOutBase *vo;
   
   CTThread vthread;
   // CTFiFo<1024*1024> videoBuf;
   CTFrameList videoFrameList;
   // CTFrameList audioFrameList;
   
   unsigned int uiReceivedAudioTS;
   unsigned int uiReceivedAudioAt;
   
   unsigned int uiPrevVideoTS;
   unsigned int uiPrevAudioTSUsed;
   
   unsigned int uiNextFrameTS;
   
   
   int iPrevRecFPS;
   
   int iFramesAfterAudio;
   int iPartsAfterAudio;
   
   int iAudioRate;
   
   int iStarted;
   int iEnding;
   
   int iInVideoThread;
   
   int g_iFrameID;
   int iCurrentPlayFrame;
   
   int iAudioMult;
   
   int reset();
   
public:
   CTRTPVideoPlayer();
   ~CTRTPVideoPlayer();
   
   int start();
   void stop();
   
   void setCallbacks(CTAudioOutBase *a, CTVideoOutBase *v);
   
   void onReceiveAudio(unsigned int  uiAudioTS);
   int onReceiveRTP(CVCodecBase *c, unsigned short seq, unsigned int uiTS, unsigned char *p, int iLen);
   
protected:
   
   unsigned int getCurrentTime();
   
   int decodeShowVideoFrame(CListItemFrame *fr, int iShow=1);
   
   CListItemFrame *getCurFrameSafeRemoveFromList(unsigned int uiTS);
   
   int videoThread();
   
   static int s_videoThread(void *p);
};

#endif
