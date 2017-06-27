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
#ifndef _C_T_AUDIO_OUT_VOIP_H
#define _C_T_AUDIO_OUT_VOIP_H

//#define T_TEST_RTP_QUEUE

#include "../baseclasses/CTBase.h"
#include "../utils/CTFifo.h"
#include "vad/CTVad.h"
#include "CTJit.h"
#include "CTRtpQueue.h"

#include <algorithm>

#ifdef __APPLE__
int AudioManager_AudioIsInterrupted();
#else
static int AudioManager_AudioIsInterrupted(void) { return 0; }
#endif

int useRtpQueue();
#define T_TEST_RTP_QUEUE useRtpQueue()



class CTAudioOutVOIP: public CTAudioGetDataCB, public CTAudioOutBase{
   int iPrevPackIsSpeech;
   int iCanClearPlayedData;
   int iPrevCanClearPlayedData;
   int iResetNextRecPack;
   
   int iPrevLostC;
   
   int iPackLostInfo[3];
   int iPackLostPrevV,iPackLostPrevPrevV;
   int iAudioIsWorking;
   int iIgnoreIncomingRTP;
public:
   int iDebugUnderFlow;
   int iUnderflowDetected;
   int iAudioGetDataCounter;// 
   CTJit jit;
   
   CTAudioGetDataCB *cbUtil;
   CTAudioGetDataCB *cbUtilNext;
   int iCNReceived;
   
   CTFiFo<32000> fifoBufConferece;
   
   CTAudioOutBase *ao;
   CTAudioOutVOIP(int iRate, int iCh, CTAudioOutBase *ao):iRate(iRate),ao(ao){
      iIgnoreIncomingRTP=0;
      iResetNextRecPack=0;
      iDebugUnderFlow=0;
      iUnderflowDetected=0;
      iAudioGetDataCounter=0;
      iPrevPackIsSpeech=1;
      iPrevCanClearPlayedData=0;
      iCNReceived=0;
      
      sbuf=NULL;
      cbUtilNext=cbUtil=NULL;
      
      intiPriv();
      
      iAudioGetDataCounter=0;
      iCanClearPlayedData=0;
   }
   ~CTAudioOutVOIP(){
      
      if(ao)ao->remCB(this,this);
      relPriv();
   }
   int iOnlyInConference;
   int canUseAudioData(){return !iOnlyInConference;}
   void onlyInConference(int ok){iOnlyInConference=ok;}
   
   double dbgUnderFlowCTX;
   int getAudioData(void *p, int iSamples, int iDevRate, int iSampleTypeEnum, int iOneSampleSize, int iIsFirstPack){
      
      if(iIsFirstPack && iCanClearPlayedData){
         iResetNextRecPack=1;  
      }
      /*
       FILE *f=fopen("d.txt","a+");
       fprintf(f,"%p s=%d r=%d e=%d ss=%d f=%d\n",p,iSamples,iDevRate,iSampleTypeEnum,iOneSampleSize,iIsFirstPack);
       fclose(f);
       */
      switch(iSampleTypeEnum){
         case CTAudioGetDataCB::eShort:
            if(iOneSampleSize!=2)return -1;
            break;
         case CTAudioGetDataCB::eFloat32:
            if(iOneSampleSize!=4)return -2;
            break;
         default:
            return -3;
            
      }
      
      if(iSamples*2>=sizeof(tmpGetBuf)){
         puts("Warning audio tmp buf is too small");
         if(p)memset(p,0,iSamples*iOneSampleSize);
         return -4;
      }
      
      iAudioGetDataCounter++;
      
      int iIsSameRateType=iSampleTypeEnum==CTAudioGetDataCB::eShort && iDevRate==iRate;
      
      short *tmp=&tmpGetBuf[0];
      
      
      if(cbUtil || cbUtilNext){
         
         //can be used for tmp sounds
         //CTAudioCallBack *prev_cb=ao->cbUtil
         //ao->cbUtil=this;
         //Sleep(2000);//play  sound for 2sec
         //ao->cbUtil=prev_cb;
         if(iIsSameRateType)tmp=(short*)p;
         CTAudioGetDataCB *c=cbUtil?cbUtil:cbUtilNext;
         
         c->getAudioData(tmp, iSamples,iRate,CTAudioGetDataCB::eShort,2,0);
         
         uiPlayPos+=iSamples*2;
      }
      else{
         
         if(iIsSameRateType)tmp=(short*)p;
         
         jit.iAudioCardReadBufSize = iSamples * iOneSampleSize;
         
         getNextBuf(tmp,iSamples);
         
         void *findGlobalCfgKey(const char *key);
         
         //the global cfg(iAudioUnderflow) is the singleton and we can store the result into a static int
         static const int *r = (const int *)findGlobalCfgKey("iAudioUnderflow");
         
         //should i disable underflow tone if call is not active?
         if(!iIgnoreIncomingRTP && (iDebugUnderFlow>0 || (r && *r))){
            
            int iUnderflow = T_TEST_RTP_QUEUE ? rtp_q.underflow(iRate/(iSamples+1)/2) : (bufBytes()<10);
            
            if(iUnderflow && iCanClearPlayedData && !iCNReceived && isPlaying()){
               void createSine(double &ctx, short *s, int iSamples, float freq, int iRate, int iVol, int iIsLast);
               createSine(dbgUnderFlowCTX,tmp,iSamples,330., iRate, 500, 0);
               iUnderflowDetected=1;
            }
            else {dbgUnderFlowCTX=0;iUnderflowDetected=0;}
         }
      }
      if(iDebugUnderFlow>0)iDebugUnderFlow-=iSamples;
      
      fifoBufConferece.add((char*)tmp,iSamples*2);
      
      if(iIsSameRateType)return 0;// iSamples;
      
      //convert rate
      
      if(iSampleTypeEnum!=CTAudioGetDataCB::eShort){
         //convert type
      }
      
      return 0;
   }
   virtual int addCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab){return -1;}
   virtual int remCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab){return -1;}
   void setPlayPos(unsigned int uiPP){
      // uiPlayPos=0;
      // uiPlayPos=
      uiNextPP=uiPP;
   }
   
   CTRtpQueue rtp_q;
   int addPack(unsigned int ts, unsigned short seq, unsigned char *data, int iDataLen, CCodecBase *c){

      if(!T_TEST_RTP_QUEUE)return -1;
      
      if(iIgnoreIncomingRTP)return 0;
      
      if(iCanClearPlayedData && bufBytes()>iRate*8 && isPlaying() && iMSecPlay==-1){
         if(ao)ao->msg("overFlow",8, this,4);
         //puts("Posible audio playback is stoped");
      };
      iCanRemoveNext=40; //can modify the next 40 playback blocks

      iCanClearPlayedData=1;
      rtp_q.addPack(ts, seq, data, iDataLen, c);
      return 0;
   }
   
   int update(char *p, int iLen, unsigned int uiPos, int iCanResetPos)
   {
      return updateFreqOk(p,iLen,uiPos,iCanResetPos);
   }
   
   virtual int update(char *p, int iLen, unsigned int uiPos){
      return updateFreqOk(p,iLen,uiPos,1);
   }
   int receivedTS(unsigned int uiPos, int iPrevLost, int iIsCN){

      if(iNeedStop)return 0;
      uiWPosLast=uiPos;
      //lost
      if(T_TEST_RTP_QUEUE){
         int l = rtp_q.lost();
         if(iPrevLost){
            iPrevLost = l - iPrevLostC;
            if(iPrevLost>100)iPrevLost=0;
            jit.iLostCnt = l;
         }
         iPrevLostC = l;
      }
      else jit.iLostCnt+=iPrevLost;
      iCanClearPlayedData=1;
      iCNReceived=iIsCN;
      
      iPackLostInfo[2]+=(iPrevLost+1);
      int over=0;
      
#define LOST_DIV 4
      
      iPackLostInfo[1]+=iPrevLost;
      if(iPackLostInfo[2]>=(100*LOST_DIV) || iPackLostInfo[1]>(100*LOST_DIV)){
         iPackLostInfo[1]-=over;
         if(iPackLostInfo[1]<0)iPackLostInfo[1]=1;
         over=iPackLostInfo[2]-(100*LOST_DIV);
   
         jit.uiPLost[0]=iPackLostInfo[0];
         jit.uiPLost[1]=iPackLostInfo[1];
         jit.uiPLost[2]=iPackLostPrevV;
         jit.uiPLost[3]=iPackLostPrevPrevV;
         
         iPackLostPrevPrevV=iPackLostPrevV;
         iPackLostPrevV=iPackLostInfo[0];
         iPackLostInfo[0]=iPackLostInfo[1];
         iPackLostInfo[1]=over;
         iPackLostInfo[2]=over;
         
      }
      if(iPackLostInfo[0]<iPackLostInfo[1])iPackLostInfo[0]=iPackLostInfo[1];
      
      
      //iPackLostInfo[]
      
      //TODO if lost realign audio data
      return 0;
   }
   
   virtual int msg(const char *pid, int iLen, void *p, int iSizeofP){
      
      if(iLen==12 && strcmp(pid, "volume_voice")==0){
         int voi = vad.wasGoodVoice()? 2 : (vad.wasVoice() ? 1 : 0);
         int vol = vad.getVol(9);
         
         //return snprintf((char*)p, iSizeofP, "%d_%d", vol, voi);
         if(iSizeofP<4)return 0;
         
         char *pb = (char*)p;
         pb[0]=vol+'0';
         pb[1]='_';
         pb[2]=voi+'0';
         
         pb[3]=0;
         
         return 3;
      }
      
      if(iLen==2 && strcmp(pid,"qd")==0){
         
         
         int bs=bufBytes()>>1;
         if(bs<0)bs=0;
         
         int rs= rtp_q.getRecomendedSampleCnt();
         if(rs<1)rs=1;
         
         int qd=bs*100/(rs);
         
         return sprintf((char*)p,"%d",qd);
      }
      if(iLen==4 && strcmp(pid,"bars")==0){
         
         int v=4;
         
         if(jit.uiPLost[0] || jit.uiPLost[1] || jit.uiPLost[2]>1  || jit.iBytesToSetBuf*2>iRate)v=3;
         if(jit.uiPLost[0]>4 || jit.uiPLost[1]>4)v=2;
         if(jit.iMaxBurst>30 && jit.iPrevBurst>10)v=2;
         if(jit.uiPLost[1]>5 && jit.uiPLost[2]>2 && jit.uiPLost[3])v=1;
         if(jit.uiPLost[0]>10 && jit.uiPLost[1]>10)v=1;
         if(jit.uiPLost[0]>20)v=1;
         if(iUnderflowDetected && v>2)v=2;
         if(!iCanClearPlayedData)v=0;
         
         return sprintf((char*)p,"%d",v);
      }
      if(iLen>5 && strncmp(pid,"bufms",5)==0){
         
         int r=jit.setMaxBufSize(atoi(pid+5));
         if(r<0)r=9;else r/=500;
         ((char*)p)[0]='0'+r;
         ((char*)p)[1]=0;
         return 1;
      }
      
      if(iLen==5 && strcmp(pid,"delay")==0){
         
         iDebugUnderFlow=iRate*4;
         
         float f=(float)bufBytes()/(float)(iRate*2);
         if(T_TEST_RTP_QUEUE){
            jit.iMaxBurst=rtp_q.maxBurst();
            jit.iBytesToSetBuf=rtp_q.getRecomendedSampleCnt()*2;
         }
         
         if(f<-1)f=-1;else if(f>8)f=0;
         const char *pB="";
         if(jit.iMaxBurst>80)pB="BB";
         else if(jit.iMaxBurst>50)pB="bb";
         else if(jit.iMaxBurst>10)pB="b ";
         float fR=(float)jit.iBytesToSetBuf/(float)(iRate*2);
         float fd=f-fR;
         
         return sprintf((char*)p,"%s%1.1f%s"//  %1.1f"
                        ,pB
                        ,f
                        ,fd>.1f?"+": (fd<-.1f?"-":"") //net is better or worse
                        );

      }
      if(iLen==4 && strcmp(pid,"lost")==0){
         return sprintf((char*)p,"%1.1f%% Loss",(float)(iPackLostInfo[0]*5+iPackLostPrevV*2+iPackLostPrevPrevV)/(8*LOST_DIV));
         
      }
      
      if(iLen==3 && strcmp(pid,"jit")==0){
         int j = (int)(rtp_q.getJit()*1000.);
         return sprintf((char*)p, "%d", j);
      }

	  if(iLen==9 && strcmp(pid,"underflow")==0){
         return sprintf((char*)p, "%d", iUnderflowDetected);
      }
      
      return ao->msg(pid,iLen,p,iSizeofP);
   }
   void setIgnoreIncomingRtp(int bFlag){
      iIgnoreIncomingRTP = bFlag;
   }
   int isPlaying(){return iNeedStop==0 || iMSecPlay>0;}
   int play(){
      iDebugUnderFlow=0;
      iMSecPlay = -1;
      if(iNeedStop){
         reset();
         
         iNeedStop=0;
         
          iAudioIsWorking=0;

         if(ao){
            puts("[play]");
            ao->addCB(this,this);
         }
         iIgnoreIncomingRTP=0;
         
         iPrevCanClearPlayedData=0;
         iCanClearPlayedData=0;

         rtp_q.setRate(getRate());
         int r=ao->play();
         iMSecPlay=-1;
         return r;
      }
      return 1;
   }
   unsigned int getSamplePlayPos(){
      if(T_TEST_RTP_QUEUE)
         return rtp_q.getPlayPos();

      return uiPlayPos>>1;
   }
   int getRate(){return iRate;}
   int getBufSize(){return iBufSizeInBytes;}
   void stopAfter(int iMs=-1)
   {
      if(!iAudioIsWorking)return;
      iDebugUnderFlow=0;

      if(iMs>0 && !AudioManager_AudioIsInterrupted())
      {
         iMSecPlay=iMs*getRate()*sizeof(short)/1000;
      }
      else
      {
         iMSecPlay=-1;
      }
   }
   void stop()
   {
      if(iMSecPlay>0 && !AudioManager_AudioIsInterrupted())return;
      if(iNeedStop==0)
      {
         iMSecPlay=-1;
         //cbUtil=NULL;
         iNeedStop=1;
         if(ao)
            ao->remCB(this,this);
         ao->stop();
         iCanClearPlayedData=0;
         iPrevCanClearPlayedData=0;
         jit.iFramesPlayed=0;
         rtp_q.reset();
      }
   }

   int isPrevPackSpeech(){//if isPrevPackSpeech()>0 is speech
      return iPrevPackIsSpeech;
   }
   
   int isNotPrevPackSpeech(){
      return iPrevPackIsSpeech<=0;
   }
   int lastPlayBufSinceMS(){
      return (int)(getTickCount()-uiLastGetMS);
   }
   
   int getLastPlayBuf(short *p, int iSamples, int &iLeftInBuf){
      int bi=fifoBufConferece.bytesIn();
      bi>>=1;
      iLeftInBuf=bi;
      if(!p){
         if(iSamples==0){
            if(iLeftInBuf>iSamples*2+512){
               int irem=iLeftInBuf-(iSamples*2+512);
               fifoBufConferece.remBytes(irem<<1);
               bi=fifoBufConferece.bytesIn()>>1;
            }
            iLeftInBuf=bi;
         }
         return bi;
      }
      if(iLeftInBuf>iSamples*3+2048){
         int irem=iLeftInBuf-(iSamples+1024);
         fifoBufConferece.remBytes(irem<<1);
         bi=fifoBufConferece.bytesIn()>>1;
      }
      
      if(bi<iSamples){
         memset(p,0,iSamples*2);
         fifoBufConferece.add(NULL,(iSamples>>2)<<1);
         return iSamples;
      }
      
      memcpy(p,fifoBufConferece.get(iSamples*2),iSamples*2);
      iLeftInBuf=bi-iSamples;
      return iSamples;
   }
private:
   int iRate;
   short *sbuf;
   int iBufSizeInBytes;
   
   int iMinBufferSize,iMaxBufferSize;
   int iNeedStop,iMSecPlay;
   unsigned int uiNextPP,uiPlayPos,uiWPosLast,uiLastGetMS;
   int iCanRemoveNext;
   
   short tmpGetBuf[8000];
   //CVADetection vad;
   CTVadDetection2 vad;
   int iPrevRemoved;
   int iPrevAdded;
   unsigned int  uiPrevWP;
   int iPrevBB;
   int iMarkPlaySlower;
   int iPlaySlowerNext;
   int iPlaySlowerMarker;
   
   //CTIncDecFreq aaf;
#if defined(T_USE_PLAY_SLOWER) || !defined(T_USE_PLAY_FASTER_REMOVE_BLOCKS)
   short tmpDecF[4000];
#endif
   int iPlayFasterNext;
   
   int iOverPlay;
   
   
   
   void relPriv(){
      if(sbuf)delete sbuf;
      sbuf=NULL;
      iBufSizeInBytes=0;
   }
   void intiPriv(){
//      int TODO_convert_bytes_to_shorts;
      iBufSizeInBytes=2*(iRate*6);
      if(sbuf)delete sbuf;
      sbuf=new short[(iBufSizeInBytes>>1)+512];
      iMinBufferSize=iRate>>4;
      
      iMaxBufferSize=iRate*10;
      iMSecPlay=-1;
      
      jit.iBytesToSetBuf=iRate>>3;
      
      reset();
      
      iNeedStop=1;
   }
public:
   void reset(){
      iOnlyInConference=0;
      iAudioIsWorking=0;
      iPrevLostC=0;
      uiPlayPos=uiWPosLast=0;
      uiNextPP=0;
      uiLastGetMS=0;
      iCanRemoveNext=0;
      iPlaySlowerNext=0;
      iPlaySlowerMarker=0;
      iMSecPlay=-1;iNeedStop=1;

      iOverPlay=0;
      iPackLostInfo[0]=iPackLostInfo[2]=iPackLostInfo[1]=0;iPackLostPrevPrevV=iPackLostPrevV=0;
      
      jit.resetJit();
      fifoBufConferece.reset();
      rtp_q.reset();
      
   }
   
   int bufBytes()
   {
      if(T_TEST_RTP_QUEUE)
         return rtp_q.samplesIn()*2;
      
      unsigned int wp=uiWPosLast>>2;
      unsigned int pp=uiPlayPos>>2;
      return ((int)wp-(int)pp)*4;
   }
   
   int updateFreqOk(char *p, int iLen, unsigned int uiPos, int iCanResetPos)
   {
      
      int iSetPosNow=0;
      
      if(iCanResetPos)jit.checkJit(p,iLen,uiPos,iCanResetPos,iRate,iMaxBufferSize,iMinBufferSize,iSetPosNow);

      //--next line  lipsync-test-only
      // jit.iBytesToSetBuf=6*iRate;
      
      unsigned int uiWPtmp=uiPos%(unsigned int)iBufSizeInBytes;
      
      short *s=(short *)p;
      if(iLen>iBufSizeInBytes)iLen=iBufSizeInBytes;
      short *pWrite=sbuf+uiWPtmp/2;
      short *pEnd=sbuf+iBufSizeInBytes/2;
      int i=(iLen>0 && (uiPos || p))?(iLen/2):(iBufSizeInBytes/2);
      
      if(p && iLen){
         uiWPosLast=uiPos+40;//(uiPos+80)%iBufSizeInBytes;
         iCanRemoveNext=40;
      }
      
      int ibuf=bufBytes();
      if(p && iLen && iLen*4<iBufSizeInBytes && jit.iFramesPlayed>iRate){
         jit.iIncBuf=(ibuf<iMinBufferSize);
         jit.iIncBufWarning=(ibuf*7<iMinBufferSize*8);
         iMarkPlaySlower=jit.iIncBufWarning || ibuf*3<jit.iBytesToSetBuf*2;
      }
      
      if((p==NULL && iLen==0 && uiPos) || 
         (iCanResetPos && (iSetPosNow==1 ||
                           iResetNextRecPack ||
                           (p && iLen && (
                                          iMinBufferSize>ibuf ||
                                          ibuf>iMaxBufferSize ||
                                          (iSetPosNow==2 && (iMinBufferSize>ibuf || ibuf>jit.iBytesToSetBuf*4+(iRate)))))))
         )
      {
         iSetPosNow=0;
         iResetNextRecPack=0;
         
         if(ibuf>iMaxBufferSize && isPlaying() && iMSecPlay==-1){
            if(ao)ao->msg("overFlow",8, this,4);
            //puts("Posible audio playback is stoped");
         };
         printf("buf %d,set %d \n",ibuf,jit.iBytesToSetBuf);
         unsigned int uiNP = (uiPos-(unsigned int)jit.iBytesToSetBuf)&~1;
         uiNextPP=uiNP;
         
      }
      if(!p){
         while(i>0)
         {
            i--;
            if(pWrite>=pEnd)pWrite=sbuf;
            *pWrite=0;
            pWrite++;
         }
      }
      else{
         while(i>0)
         {
            i--;
            if(pWrite>=pEnd){pWrite=sbuf;}
            //     printf("[%d]",*s);
            *pWrite=*s;
            pWrite++;
            s++;
         }
         
      }
      
      
      return 0;
   }
   


   int getNextBuf(short *samples, int frames) {
      
      //TODO minbuf size frames+iRTPPackFrames/2
      int iCanReset=iCanClearPlayedData && iMSecPlay==-1 && (uiNextPP==1 || T_TEST_RTP_QUEUE);
      
      int iReqPlayFaster=!iPrevAdded&&  iCanReset && iPlayFasterNext;
      int iReqPlaySlower= !iReqPlayFaster && iCanReset  &&(iPlaySlowerNext==2 || (iMarkPlaySlower && iPlaySlowerNext)) ;
      if(iReqPlaySlower)iReqPlayFaster=0;
      
      int r;
#define T_USE_PLAY_FASTER_REMOVE_BLOCKS
      //#define T_USE_PLAY_SLOWER
      
#if !defined(T_USE_PLAY_FASTER_REMOVE_BLOCKS)
      disabled :) - was not ok
      if(iReqPlayFaster){
         int iFrin=(frames*17)>>4;
         aaf.iPrev=lastVoiceSample;
         r=getNextBufR(&tmpDecF[0],iFrin);
         aaf.setFreq(8000*iFrin,8000*frames);
         int ret=aaf.doJob2(&tmpDecF[0], samples, iFrin,frames);
         if(ret!=frames){
            printf("errInc-fr=%d %d\n",ret,frames);
            if(ret<frames){
               int m=frames-ret;
               getNextBufR(samples+frames-m,m);
            }
         }
         printf("[ play faster]");
         r=frames;
      }
      else 
#endif 
#if defined(T_USE_PLAY_SLOWER)
         disabled :) - was not ok
         if(iReqPlaySlower){
            //TODO playSlower
            int iFrin=(frames*(15))>>4;
            r=getNextBufR(&tmpDecF[0],iFrin);
            aaf.setFreq(8000*iFrin,8000*frames);
            int ret=aaf.doJob2(&tmpDecF[0], samples, iFrin,frames);
            if(ret!=frames){
               printf("errDec-fr=%d %d\n",ret,frames);
               if(ret<frames){
                  int m=frames-ret;
                  //samples[frames-1]=samples[frames-2];
                  getNextBufR(samples+frames-m,m);
               }
            }
            printf("[ play slower]");
            r=frames;
            
            
         }
         else
#endif
         {
#if defined(T_USE_PLAY_SLOWER) || !defined(T_USE_PLAY_FASTER_REMOVE_BLOCKS)
            disabled :) 
            r=getNextBufR(&tmpDecF[0],frames); 
            aaf.doJob2(&tmpDecF[0], samples, frames,frames);//TODO use small fifo
#else
            if(!iPrevAdded && (
                               (iReqPlaySlower &&  iPrevPackIsSpeech<-20) ||
                               (iPlaySlowerNext==2) ||
                               (iPrevPackIsSpeech<0 && iPlaySlowerMarker>100))){

               r=getNextBufR(samples,frames/2);
               for(int z=frames-2;;z-=2){
                  if(z<0)break;
                  samples[z]=samples[z/2];
                  samples[z+1]=samples[z];
               }
               printf("[play slower]");
               r*=2;
               iPrevAdded=!iPrevAdded;
               
            }
            else r=getNextBufR(samples,frames);
#endif
         }
      
      iPlayFasterNext=0;
      iPlaySlowerNext=0;
      vad.iCanReplace=0;;
      vad.iIsPlaybackVad=1;
      
      int iCBB=bufBytes();
      
      if( T_TEST_RTP_QUEUE){
         rtp_q.setHWReadSamples(frames);
         jit.iMaxBurst=rtp_q.maxBurst();
         jit.iBytesToSetBuf=rtp_q.getRecomendedSampleCnt()*2;
         if(jit.iBytesToSetBuf<frames*2)jit.iBytesToSetBuf=frames*2;
         iMinBufferSize = 480*iRate/8000+frames*2;
         iMaxBufferSize = iRate*8;
#ifndef __APPLE__
         iMinBufferSize+=iRate>>4;
#endif
         iMarkPlaySlower = rtp_q.mustPlaySlower();
      }

      //if buf is ok skip check
      iPrevPackIsSpeech=vad.isSilence2(samples,frames,getRate());
      // jit.
      if(!T_TEST_RTP_QUEUE){
         static int iX=0;iX++;
         if((iX&127)==1 || iPrevPackIsSpeech==0)printf("%p %dpvad=%d b=%d pb=%d buf=%d ss=%d j=%d [lost=%d]\n",this,iMSecPlay==-1,iPrevPackIsSpeech,jit.iMaxBurst,jit.iPrevBurst,bufBytes(),jit.iBytesToSetBuf,jit.iMaxJit, jit.iLostCnt);
      }
   //  printf("[rtp nr=(%d,%d)b(%d %d)%d sp=%d r=%d %dms pp%u f=%d]\n",iCanRemoveNext,iCanReset,iMinBufferSize, iMaxBufferSize, jit.iBytesToSetBuf,iPrevPackIsSpeech,iPrevRemoved ,iMSecPlay , uiNextPP,uiWPosLast!=uiPrevWP);
      
      if(iCanReset && (uiWPosLast!=uiPrevWP || iCanRemoveNext>0)){

         
         iCanRemoveNext--;
         
         if(iCBB<iMinBufferSize){
            iPlaySlowerNext=2;
            iMarkPlaySlower=1000;
            iPrevRemoved=1;
            iOverPlay=0;
         }
         else if((iPrevPackIsSpeech<-10 && jit.iBytesToSetBuf>((iCBB+iMinBufferSize)>>1) && iCBB*2>iMinBufferSize)
                 || jit.iBytesToSetBuf+iMinBufferSize*2<iCBB){
            
            int iLim=jit.iBytesToSetBuf*2+iMinBufferSize;
            int iLim2=jit.iBytesToSetBuf*3;
            int iLim4=jit.iBytesToSetBuf+iRate/4;
             iLim=std::min(iLim2,iLim);
             iLim=std::min(iLim4,iLim);
            
            int ml=iMinBufferSize+iRate/8;
            if(iLim<ml)iLim=ml;
            
            if(iCBB>iLim)iOverPlay++;else iOverPlay=0;
            
            if(iCBB*4<jit.iBytesToSetBuf*3){
               iPlaySlowerMarker++;
               if(iPrevPackIsSpeech<0){
                  if(iMarkPlaySlower && iPlaySlowerMarker>20){
                     iPlaySlowerNext=1;
                  }
                  iPrevRemoved=1;
               }
            }
            else {
               
               if (iCBB*3<jit.iBytesToSetBuf*2 || (iCBB*5<jit.iBytesToSetBuf*4 && iCBB*2<iMinBufferSize*3)){
                  //  play slower 
                  iPlaySlowerMarker++;
                  if(iMarkPlaySlower && iPlaySlowerMarker>20){
                     iPlaySlowerNext=1;
                  }
                  iPrevRemoved=1;
               }
               else if(iPrevPackIsSpeech<0 && iOverPlay>8 &&  iCBB>iLim){
                  iPlayFasterNext=1;
                  iPlaySlowerMarker=0;
                  //TODO canPlaySlowerAfterNSeconds
#ifdef   T_USE_PLAY_FASTER_REMOVE_BLOCKS
                  if(iCBB>iLim+frames*2 && (iPrevPackIsSpeech<-20 || iCBB*2>iRate*3 || iOverPlay>100)){
                     if(!iPrevRemoved){
                         int mf=std::max(std::min(80,frames),160);
                        if(!T_TEST_RTP_QUEUE){
                           unsigned int uiPrevP=uiPlayPos;
                           uiPlayPos=findBestAlign(uiPlayPos,mf);
#pragma mark reducing play delay log
                           printf("reducing play delay %d\n",(int)uiPlayPos-(int)uiPrevP+frames*2);
                           rtp_q.iReducePlayDelayCnt++;
                           
                           r=getNextBufR(samples,frames);
                           iPrevPackIsSpeech=vad.isSilence2(samples,frames,getRate());
                        }
                        else{
                           //TODO try align rtp_q, (iPrevPackIsSpeech<0  || )
                           //rtp_q.realign(lastVoiceSample,last2VoiceSampleMult)
                           int pr=samples[frames-1];
                           r=getNextBufR(samples,frames);
                           int skip=findBestAlign(pr,samples,mf);
                           if(skip>0 && skip<=frames){
                              int more=frames-skip;
                              memcpy(samples,&samples[skip],more*sizeof(short));
                              getNextBufR(&samples[more],skip);
                           }
#pragma mark reducing play delay log                            
//                           printf("reducing play delay %d+%d\n",frames,skip);
                           rtp_q.iReducePlayDelayCnt++;
                           iPrevPackIsSpeech=vad.isSilence2(samples,frames,getRate());
                        }
                     }
                     iPrevRemoved=!iPrevRemoved;
                  }
#endif
                  
               }
            }
         }
         else iMarkPlaySlower=0;
         uiPrevWP=uiWPosLast;
         iPrevBB=iCBB;
         //rem someSilentPack
         //printf("[rem %d]",i);
         
      } 
      if(frames>1){
         int last2VoiceSample=samples[frames-2];
         lastVoiceSample=samples[frames-1];
         last2VoiceSampleMult=(int)lastVoiceSample*(int)last2VoiceSample;
      }
      
      
      return r;
   }
   
   int lastVoiceSample,last2VoiceSampleMult;
   unsigned int findBestAlign(int pr, short *p, int iSamples){
      int z;

      int iBestPos=0;

      int iMin=10000;
      double dMin=4000000000.f;
 
      int iPd=((int)p[0]-lastVoiceSample);
      
      for(z=0;z<iSamples;z++){

         int iCd=p[z]-pr;
         int c=abs(iCd-iPd);
         double sqd=fabs((double)last2VoiceSampleMult-(double)((int)p[z]*pr));
         //pr2=pr;
         
         if(c<iMin*2 && sqd<=dMin){dMin=sqd;iMin=c;iBestPos=z;if(sqd<8)break;};
         pr=(int)p[z];
         iPd=pr-lastVoiceSample;
         
      }
      
      return iBestPos;
   }

   unsigned int findBestAlign(unsigned int i, int iMaxCnt){
      int z;
      unsigned  int iPrevI=i;
      i%=(unsigned int)iBufSizeInBytes;
      short *p=(short*)sbuf+i/2;
      unsigned int iBestPos=i;
      unsigned int iCp=iBestPos;
      int iMin=10000;
      double dMin=4000000000.f;
      int pr=10;
      //int pr2=10;
      int iFrom=i?-1:0;
      if(i){
         pr=p[iFrom-1];
         //pr2=p[-2];
      }
      else{
         pr=sbuf[iBufSizeInBytes/2-1];
         //pr2=sbuf[iBufSizeInBytes/2-2];
      }
      int iPd=((int)p[0]-lastVoiceSample);
      int iAdd=0;
      int iBestPosAdd=0;
      
      for(z=iFrom;z<iMaxCnt;z++){
         //int c=abs((int)p[z]-(int)lastVoiceSample);
         int iCd=pr-p[z];
         int c=abs(iCd-iPd);
         double sqd=fabs((double)last2VoiceSampleMult-(double)((int)p[z]*pr));
         //pr2=pr;
         pr=(int)p[z];
         if(c<iMin*2 && sqd<=dMin){dMin=sqd;iMin=c;iBestPos=iCp;iBestPosAdd=iAdd;if(sqd<8)break;}
         iCp+=2;
         iAdd+=2;
         if(iCp>=(unsigned int)iBufSizeInBytes){
            p=(short*)sbuf;
            iCp=0;
         }
      }
      
      //--    if(iBestPos>=iBufSizeInBytes)iBestPos-=iBufSizeInBytes;
      // i=iBestPos;
      iBestPos=iBestPosAdd+iPrevI;
      
      return iBestPos&~1;
   }
   
   int getNextBufR(short *samples, int frames) 
   {
      iAudioIsWorking=1;
      uiLastGetMS=getTickCount();
      
      if(iNeedStop){
         memset(samples,0,frames*2);
         return 0;
      }
      if(iMSecPlay!=-1)
      {
         iMSecPlay-=frames*2;
         if(iMSecPlay<0){stop();return -1;}
      }
      else if(T_TEST_RTP_QUEUE && iCanClearPlayedData){
         rtp_q.getData(samples, frames);
         return 0;
      }
      
      if(uiNextPP!=1){
         uiPlayPos=findBestAlign(uiNextPP,80);
         uiNextPP=1;
      }
      if(iCanClearPlayedData && !iPrevCanClearPlayedData){
         iPrevCanClearPlayedData=iCanClearPlayedData;
         jit.iMaxBurst=0;
      }
      
      if(iCanClearPlayedData)jit.iFramesPlayed+=frames;
      
      unsigned int uiPPLoop=uiPlayPos%(unsigned int)iBufSizeInBytes;
      
      
      short *p=sbuf+uiPPLoop/2;
      short *pEnd=sbuf+iBufSizeInBytes/2;
      unsigned int uiNewPos=uiPlayPos;
      
      uiNewPos+=(unsigned int)(frames*2);
      uiPlayPos=uiNewPos;

      
      while(frames>0)
      {
         frames--;
         if(p>=pEnd){p=sbuf;}
         *samples=*p;
         if(iCanClearPlayedData)*p=0;// cleans played data if rtp media is active,
         p++;
         samples++;
      }
      return 0;
   }   
   
   
};
#endif
