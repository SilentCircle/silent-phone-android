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

#ifndef _C_T_MAC_AUDIO_IOS_H
#define _C_T_MAC_AUDIO_IOS_H

static void uSleep(int a){usleep(a);}

#include "CTPhoneAuidoInNew.h"
#include "CTAudioINSes.h"
#include "CTAudioUtils.h"

void *getAudioCtx(int iRate, int iRel);
void *getPAudioCtx(int iRate);

void *t_native_PstartCB(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData);
void t_native_Pstop(void *ctx);
void t_native_Prel(void *ctx);
void *t_native_startCB_play(void *ctx, int iRate, int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData);
void t_native_stop(void *ctx, int iPlay);


class CTAudioIn: public CTAudioInBase, public CTAudioINSes{
   
   int iRec;
   int iRate;
   
   CTPhoneAudioInBufControl ac;
   void *ctx;
public:
   
   CTAudioCallBack *cb;
   
   CTAudioIn(CTAudioCallBack *cb, int iBitsPerSample=16, int iRate=8000, int iCh=1)
   :CTAudioINSes(),cb(cb),iRate(iRate),ac(iRate)
   {
      ctx=NULL;
      pos=0;
      iRec=0;
      ac.setPayload20msX(2);
      ctx=getAudioCtx(iRate,0);
   }
   
   void setVolume(int i){}

   int packetTime(int v){
      ac.setPayload20msX(v/20);
      return 0;
   }
   int init(void *g=NULL)
   {
#ifdef __APPLE__
      char *iosLoadFile(const char *fn, int &iLen );
      int iLen;
      char *p=iosLoadFile("audio_interrupt16k.raw",iLen);
      if(p && iLen>0)ac.warningSound.set(p, iLen, 1);
#endif
      
      return 0;
   }
   ~CTAudioIn()
   {
      getAudioCtx(0,1);//TODO fix-- release only once
   }
   unsigned int pos;
   int sendAudioFrame(short *samples, int frames){
      if(!iRec)return -1;
      frames<<=1;
      //cb->audioIn((char*)samples,frames,pos);
      ac.audioIn((char*)samples,frames,pos);
      pos+=frames;
      return 0;
   }
   static int cbAudioIn(void *callback_data, short *samples, int frames) 
   {

      return ((CTAudioIn *)callback_data)->sendAudioFrame(samples,frames);
   }
   
   inline int getType(){return CTAudioInBase::ePCM16;}
   inline int  getRate(){return iRate;}
   int record(void *p)
   {
      addSes(p);
      if(iRec)return 0;
      iRec=1;
      void *t_native_startCB_rec(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData);
      ctx=t_native_startCB_rec(ctx, iRate, &cbAudioIn,this);
      
      
      ac.cb2=cb;
      ac.record(p);
      
      return 0;
   }
   void stop(void *p)
   {
      remSes(p);
      if(hasSesActive())return;
         
//      if(!iRec)return;
      iRec=0;
      ac.stop(p);
      t_native_stop(ctx,0);
   }
};


class CTAudioOut: public CTAudioOutBase{
   int iMSecPlay,iNeedStop;
   int iMachineRate;
   int iInitOk;
   int iRate;
   int iBufSize;


   void *ao;

   short sbuf[2048];
   
   CTAudioOutList al;
   unsigned int uiPlayPosSamples;
public:
   int getBufSize(){return iBufSize;}
   CTAudioCallBack *cbUtil;
   int iIsUtilPlay;
   void *ctx;
   CTFiFo<64000> fifoBuf;
   void setVolume(int i){}
   CTAudioOut(int iBitsPerSample=16, int iRate=8000, int iCh=1, int id=0,int flag=0)
   :al(),iRate(iRate),iMachineRate(iRate)//44100)
   {
      cbUtil=NULL;
      iIsUtilPlay=0;
      ctx=0;
      uiPlayPosSamples=0;
  

      iMSecPlay=0;
      iNeedStop=1;
      //uiLastGetPos=0;
      
      iInitOk=0;ao=NULL;
      init(NULL);
      iBufSize=sizeof(sbuf);//iMachineRate*2*6;//6sec
      
   }
   int getLastPlayBuf(short *p, int iSamples, int &iLeftInBuf){
      return 0;
   }
   void setIsUtilPlay(CTAudioCallBack *cb){
      cbUtil=cb;
      iIsUtilPlay=1;
      ctx=getPAudioCtx(iRate);
      
   }
   
   int addCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab){
      al.addCB(cb,ab);
      this->CTAudioOutBase::cbAudioDataGet=cb;
      this->CTAudioOutBase::cAudioOut=ab;
      //add to list
      return 0;
      
   }
   int remCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab){
      
      al.remCB(cb,ab);
      
      this->CTAudioOutBase::cbAudioDataGet=NULL;
      this->CTAudioOutBase::cAudioOut=NULL;
      //rem from list
      return 0;
   }

   ~CTAudioOut()
   {
      rel();
      //--if(sbuf)delete sbuf; 
      
      ///if(bufTmp)delete bufTmp;
   }   
   static int cbAudioU(void *callback_data, short *samples, int frames) 
   {
      //  printf("get Next ");
      int ret=((CTAudioOut *)callback_data)->getNextBuf(samples,frames,0);
      if(ret<0)return ret;
      return frames;
   }

   static int cbAudio(void *callback_data, short *samples, int frames, int iRestarted) 
   {
      int ret=((CTAudioOut *)callback_data)->getNextBuf(samples,frames,iRestarted);
      if(ret<0)return ret;
      ((CTAudioOut *)callback_data)->fifoBuf.add((char*)samples,frames*2);//echo cancel

      return frames;
   }
   
   unsigned int getSamplePlayPos(){return uiPlayPosSamples;}
   
   int getNextBuf(short *samples, int frames, int iIsFirstPack) {
      
      uiPlayPosSamples+=frames;

      if(iMSecPlay!=-1)
      {
         iMSecPlay-=frames;
         if(iMSecPlay<0){puts("stop");stop();return -1;}
      }
      if(cbUtil){
         cbUtil->audioIn((char*)samples, frames*2, 0);
         return 0;
      }
      if(al.activeCnt()){
         al.getAudioData(samples,frames, iRate, CTAudioGetDataCB::eShort, 2, iIsFirstPack);
      }
      else if(cbAudioDataGet){
         cbAudioDataGet->getAudioData(samples,frames, iRate, CTAudioGetDataCB::eShort, 2, iIsFirstPack);
         
         //--
         //convert to machine rate, type
         //addEcho
         return 0;
      }
      else printf("[noAO]");
      
      return 0;
      

   }
   
   virtual int msg(const char *pid, int iLen, void *p, int iSizeofP){
      if(iLen==8 && strcmp(pid,"overFlow")==0){
         void t_onOverFlow(void *ctx);
         t_onOverFlow(ctx);
         return 0;
      }
      return -1;
   }
   // unsigned int uiWPosLast;
   void rel()
   {
      iMSecPlay=-1;
      if(iIsUtilPlay){
         t_native_Pstop(ctx);
         t_native_Prel(ctx);
      }else{
      //if(ao)audio_close(ao);
         t_native_stop(ctx,1);
      }
      ao=NULL;
      iInitOk=0;
   }
   int init(void *p)
   {
      iInitOk=1;//ao?1:0;
      return 0;
      
   }
   // int uiFPos;
   // short sbufF[10000];
   void constr()
   {
   }

   //  unsigned int uiWPosLast;
   
   inline int getRate(){return iRate;}
  // inline int getDirectRate(){return iMachineRate*2;}
   
   // CTMutex mu;
   void stopAfter(int iMs=-1)
   {
      if(iMs!=-1)
      {
         iMSecPlay=iMs*iRate*2/1000;
      }
      else
      {
         iMSecPlay=-1;
      }
   }
 
   int play()
   {
      iMSecPlay=-1;
      
      if(iNeedStop)
      {
         iNeedStop=0;
         init(NULL);
         fifoBuf.reset();
         //puts("play2");
         //audio_play(&cbAudio,ao,this);    
        
         if(iIsUtilPlay)
            ctx=t_native_PstartCB(ctx, iRate, &cbAudioU,this);
         else{
            ctx=getAudioCtx(iRate,0);
            ctx=t_native_startCB_play(ctx, iRate, &cbAudio,this);
         }
      }
      return 0;
   }
   int isPlaying(){return iNeedStop==0;}
   void stop()
   {
      int ac=al.activeCnt();
      if(ac){
         printf("AO active cnt= %d\n",ac);
         return;
      }
      if(iMSecPlay>0)return;//TODO test
      if(iNeedStop==0)
      {
         iNeedStop=1;
         if(iIsUtilPlay){
            t_native_Pstop(ctx);
         }
         else rel();
      }
   }

   inline int update(char *p, int iLen, unsigned int uiPos)
   {
      return update(p, iLen,  uiPos, 1);
   }

   int update(char *p, int iLen, unsigned int uiPos, int iCanResetPos)
   {
      
      return 0;//updateFreqOk(p,iLen,uiPos,iCanResetPos);
   }
};
#endif
