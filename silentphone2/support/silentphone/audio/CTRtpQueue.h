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
#ifndef _C_T_RTP_QUEUE_H
#define _C_T_RTP_QUEUE_H

#define MAX_A_RTP_PACK_SIZE 1024

void log_audio(const char *tag, const char *buf);

class CTMemBuf{
   long long iAlocated,iReleased;
   int iPos;
   int iRelPos;
   enum{eSize=160*MAX_A_RTP_PACK_SIZE};
   unsigned char buf[eSize+16];
   
public:
   CTMemBuf(){reset();}
   int bytesUsed(){return (int)(iAlocated-iReleased);}
   void reset(){iPos=0;iAlocated=0;iReleased=0;}
   unsigned char *getBuf(int iBytes){
      if(iBytes>eSize || iBytes<=0)return NULL;
      int np=iPos+iBytes;
      if(np>eSize){iPos=0;np=iBytes;}
      iAlocated+=iBytes;
      unsigned char *p = &buf[iPos];
      iPos=np;
      return p;
   }
   void relBytes(unsigned char *p, int iBytes){
      iReleased+=iBytes;
      iRelPos = (int)(p - &buf[0]);
      if(iRelPos<0 || iRelPos+iBytes>eSize) puts("ERR relBytes");}
};

//#define RTP_BUF_DATA_SIZE 320

class CTRoc{
   int iIncRoc;
   unsigned int uiPrevRolSeq;
   unsigned int uiRollover;
   unsigned short usPrevSeq;
public:
   
   CTRoc(){uiRollover=0; reset();}
   void reset(){
      uiRollover++;
      if(uiRollover>80)uiRollover=1;
      uiPrevRolSeq=(uiRollover)<<16;
      usPrevSeq=0;
      iIncRoc=0;
   }
   
   unsigned int rolSeq(unsigned short seq){
      
      iIncRoc=0;
      
      unsigned int tsG = ((uiRollover)<<16) | seq;
      unsigned int tsGm1 = ((uiRollover-1)<<16) | seq;
      unsigned int tsGp1 = ((uiRollover+1)<<16) | seq;
      
      int d=(int)(uiPrevRolSeq-tsG);if(d<0)d=-d;
      int dp=(int)(uiPrevRolSeq-tsGp1);if(dp<0)dp=-dp;
      int dm=(int)(uiPrevRolSeq-tsGm1);if(dm<0)dm=-dm;
      
      if(dm<d){d=dm;tsG=tsGm1;}
      if(dp<d){d=dp;tsG=tsGp1;if(d>((1<<15)+1)){iIncRoc=1;tsG+=(1<<16);}}
      
      
      return tsG;
   }
   
   void updateRocSeq(unsigned int tsG, unsigned short seq){
      uiPrevRolSeq = tsG;
      usPrevSeq=seq;
      if(iIncRoc){
         uiRollover++;
         iIncRoc=0;
      }
   }
};


class CTRtpQueue{
   
   CTMemBuf mem;

   CTRoc roc;
   
   typedef struct{
      int iInUse;
      
      unsigned int uiReceiveTimeStamp;
      
      unsigned int uiTS;//rtp
      unsigned int seq;//rtp inc rollover
      
      unsigned int seqRec;//16bit value
      
      CCodecBase *c;//decoder
      int iDataLen;
      unsigned char *pData;//[RTP_BUF_DATA_SIZE];


      inline int isOK(){return iInUse && c;}
      
   }QPack;
   
   enum {
      eMaxLost=300,
      eMaxPackInQueue=1023//must be (1<<n)-1
   };  
   
   QPack qPack[eMaxPackInQueue+1];
   
   QPack *qPrevRecv;
   
   short tmpSamples[16000];//decoded samples
   int iSamplesInTmpQueueLeft;
   
   int iPacketsAdded;
   int iPacketsRemoved;
   
   unsigned int iSeqPlayDecodePackInQueue;//decoding,playing
   
   unsigned int uiLastReceiveSeq;
   unsigned int uiLastPlaySeq;
   
   unsigned int uiPlayPos;
   unsigned int uiBufferedTS;
   
   int iNextRolloverAfter;
   
   int iRate;
   
   int iPrevDecodedSamples;
   
   int iBurstCnt,iMaxBurstCnt,iPrevBurstCnt,iTooLate,iMaxBurstCall;
   int iBurstingResetCnt;
   
   double dJit;
   
   int iWaitForIncomingData;
   int iCanPlay;
   
   int iResetPos;
   
   
   int iHWReadSamples;
   
   int iUnderFlowDetected;
   int iUnderFlowCounter;
   
   int iPrevWasCN;
   int iPrevWasOK;
   
   int iLostCnt;
   
   //>>debug
   int iMaxTSDiff;
   int iErrFlag,iErrs;
   //<<debug
   


   int iPlaySlowerFlag;
   int iRealDataReceived;
   
public:
   int iReducePlayDelayCnt;
   
   CTRtpQueue(){iPacketsAdded=iLostCnt=0;iErrFlag=0;reset();iHWReadSamples=300;iRate=16000;dJit=0;memset(qPack,0,sizeof(qPack));}
   ~CTRtpQueue(){reset();}
   
   void setRate(int rate){iRate=rate;}
   void setHWReadSamples(int s){iHWReadSamples=s;}
   inline int maxBurst(){return iMaxBurstCnt;}
   inline int lost(){return iLostCnt;}
   inline int mustPlaySlower(){return iPlaySlowerFlag;}
   
   inline int underflow(int iThresh=0){
      if(iUnderFlowDetected)
         iUnderFlowCounter++;
      else
         iUnderFlowCounter=0;
      return !iPrevWasCN && iUnderFlowCounter>iThresh;
   }
   
   void log(){
      if(iPacketsAdded<1)return;
      const char *tag="CTRtpQueue:";
      char b[128];
      if(iErrFlag){
         sprintf(b,"Error_Flag=%d",iErrFlag);
         log_audio(tag, b);
      }
      if(iLostCnt){
         sprintf(b,"Lost=%d  %.2f %% ",iLostCnt, (float)iLostCnt*100.f/(float)(iPacketsAdded));
         log_audio(tag, b);
      }
      if(1)
      {
         sprintf(b,"Max burst=%d pf=%d end",iMaxBurstCall==1?0:iMaxBurstCall,iReducePlayDelayCnt);
         log_audio(tag, b);
      }
      
   }
   
   void reset(){
      
      log();
      iMaxBurstCall=0;
      
      roc.reset();
      
      iReducePlayDelayCnt=0;
      iPrevWasOK=0;
      iRealDataReceived=0;
      iPlaySlowerFlag=0;
      iErrFlag=0;iErrs=0;
      iTooLate=0;
      iCanPlay=0;
      iLostCnt=0;
      iMaxTSDiff=0;
      iUnderFlowCounter=0;
      iPrevWasCN=0;
      iUnderFlowDetected=0;
      iBurstingResetCnt=0;
      uiBufferedTS=0;
      iResetPos=1;
      iWaitForIncomingData=2;
      dJit=0.01;
      iBurstCnt=iMaxBurstCnt=iPrevBurstCnt=0;
      uiPlayPos=0;iPacketsAdded=0;iPacketsRemoved=0;iSamplesInTmpQueueLeft=0;
      uiLastPlaySeq=0;
      uiLastReceiveSeq=0;
      iNextRolloverAfter=-1;
      iPrevDecodedSamples=0;
      iPrevPackLen=0;

      int bu = mem.bytesUsed();
      
      for(int i=0;i<eMaxPackInQueue;i++){
         if(qPack[i].iInUse){
            relPack(&qPack[i]);
         }
         qPack[i].seq=-1; //must be set here else "if(q->seq == tsG){iErrFlag|=256;return 0;}" will fail
      }
      if(mem.bytesUsed())
         t_logf(log_events, __FUNCTION__,"[rel rtpQ mem=[%d must be 0] was=%d]",mem.bytesUsed(), bu);
      
      qPrevRecv=NULL;
      mem.reset();
   }

   int getRecomendedSampleCnt(){
      
      int iNeedJ = (int)(dJit * (double)iRate);
      int iNeedSamplesInBuf = iNeedJ;
      
      iNeedSamplesInBuf += (iHWReadSamples>>2)+(iPrevDecodedSamples>>1); //add min buff size
      
      int b=((iPrevBurstCnt*3+iMaxBurstCnt)+1)>>2;
      
      if(b && iPrevDecodedSamples){
         int iNeedB = b * iPrevDecodedSamples;
         //iNeedJ
         if(iNeedJ > iNeedB)iNeedB = iNeedJ>>1;//iNeedB>>=1;
         
         iNeedSamplesInBuf += iNeedB;
         
      }
      if(iTooLate)iNeedSamplesInBuf+=iPrevDecodedSamples;
      
      if(iNeedSamplesInBuf>iRate*2)iNeedSamplesInBuf=iRate*2;//2sec max
      
      if(iPrevDecodedSamples && iNeedSamplesInBuf<iPrevDecodedSamples)
         iNeedSamplesInBuf=iPrevDecodedSamples;
      
      if(iNeedSamplesInBuf<iHWReadSamples+80)iNeedSamplesInBuf=iHWReadSamples+80;
      
      return iNeedSamplesInBuf;
   }
   double getJit(){return dJit;}
   
   unsigned int getPlayPos(){
      return uiPlayPos;
   }
   
   inline int samplesIn(){
      if(iWaitForIncomingData==2 || !iRealDataReceived)return 0;
           
      int r = (int)(qPack[uiLastReceiveSeq & eMaxPackInQueue].uiTS - uiPlayPos);
      r += iSamplesInTmpQueueLeft;
   
      if(r<-iRate*4)r=-iRate*4;
      
      if(abs(r)>iRate*2)
         printf("[si=%d] ",r);
      return r;
   }
   
   int addPack(unsigned int ts, unsigned short seq, unsigned char *data, int iDataLen, CCodecBase *c){
      
      QPack *q = &qPack[seq & eMaxPackInQueue];
      
      //pack repeats check 1
      if(q->iInUse && q->seqRec == seq){iErrFlag|=512;return 0;}//could fail if pack is played and then received again.
      
      unsigned int tsG = roc.rolSeq(seq);
      
      //pack repeats check 2
      if(q->seq == tsG){iErrFlag|=256;return 0;}
      
      if(q->iInUse){
         relPack(q);
         err("Warn: q->iInUse", 1);//if was not deleted - probably was too late
      }
      

      
      if(c){
         if(iDataLen>MAX_A_RTP_PACK_SIZE){err("ERR: bug or attack iDataLen>MAX_A_RTP_PACK_SIZE",128);return -1;}
         if(iDataLen<1){err("ERR: imposible iDataLen<1",1024); return -1;}
         q->pData = mem.getBuf(iDataLen);
         if(!q->pData){err("ERR no-mem !q->pData", 4); return -1;}
         
         q->iDataLen = iDataLen;
         memcpy(q->pData, data, iDataLen);
         if(!iRealDataReceived)puts("[rec first audio pack]");
         iRealDataReceived=1;
      }
      else {q->iDataLen=0;}
      
      roc.updateRocSeq(tsG, seq);
      
      q->c=c;
      q->seqRec = seq;
      q->seq = tsG;
      q->uiTS = ts;
      q->uiReceiveTimeStamp = getTickCount();
      q->iInUse=1;
      //What to do if seq nr is off by 1k ?
      //uiLastReceiveSeq = tsG; //
      if(tsG>uiLastReceiveSeq)uiLastReceiveSeq = tsG;// ??

      if(uiLastReceiveSeq<uiLastPlaySeq){
         if(iTooLate<5000)iTooLate+=500;
         err("[Warn TOO LATE]",2);
      }
      else if(uiLastReceiveSeq==uiLastPlaySeq){
         if(iTooLate<1000)iTooLate+=100;
         err("[Warn TOO LATE]",2);
      }
      if(iTooLate>0)iTooLate--;
      
//#define T_CALC_JIT_IN_PLAYBACK

#ifndef T_CALC_JIT_IN_PLAYBACK
      if(qPrevRecv)
         calcJit(q, qPrevRecv);
      

      qPrevRecv=q;
      
      if(qPrevRecv && iWaitForIncomingData){iWaitForIncomingData=0;}

#endif

      iPacketsAdded++;
      
      if(iPacketsAdded<250){//tmp reset counters after zrtp is finished
         iLostCnt=0;
         iMaxBurstCall=0;
         iMaxBurstCnt=0;
         iPrevBurstCnt=0;
         if(dJit>.5)dJit=.5;
      }
      
      return 0;
   }
   
   int getData(short *s, int iSamples){
      
      int iNeedSamplesInBuf = getRecomendedSampleCnt();
      
      if(!iCanPlay){
         iResetPos=1;
         iCanPlay=findBestDecodePack(iNeedSamplesInBuf);
         if(iCanPlay){
            iResetPos=1;
         }
      }

      int si = samplesIn();
      iPlaySlowerFlag = si*2<iNeedSamplesInBuf;
      
      {
         static int dbg;
         if((dbg&127)==1){
            

#pragma mark audio stats log
            if(si<100 || dJit>0.2 || (dbg&((1<<12)-1))==1){
               t_logf(log_audio_stats, __FUNCTION__,"%p [bp=%u pp=%u dJit=%.3f ns=%d si=%d tq=%d  mb=%d pb=%d ef=%x errs=%d lr=%u spdq=%u mem=%d pf=%d]"
                      ,getEncryptedPtr_debug(this)
                   ,uiBufferedTS,uiPlayPos, dJit,iNeedSamplesInBuf,si,iSamplesInTmpQueueLeft
                   ,iMaxBurstCnt,iPrevBurstCnt
                   ,iErrFlag,iErrs,uiLastReceiveSeq,iSeqPlayDecodePackInQueue
                   ,mem.bytesUsed(),iReducePlayDelayCnt);
            }
         }
         dbg++;
      }
 
      if(si < -iRate){
         iCanPlay = findBestDecodePack(iNeedSamplesInBuf);
         iResetPos=1;
      }
      
      if(iResetPos || iWaitForIncomingData || !iCanPlay){
         
         if(si < iNeedSamplesInBuf || !iCanPlay){//if no data available in buffers
#if 1
            copySamplesFromTmpBuf(s, iSamples);//uiPlayPos+=iSamplesInTmpQueueLeft;
#else
            memset(s, 0, iSamples * sizeof(short));
#endif
           // uiPlayPos+=iSamples;
            return 0;
         }
         
         iResetPos=0;
         iWaitForIncomingData=0;
      }
      //remove 
      if(si > 5*iRate || si>4*iRate+iNeedSamplesInBuf){
         findBestDecodePack(iNeedSamplesInBuf);
      }
      
      while(iSamplesInTmpQueueLeft<iSamples){
         decodeNextPacket(iSamples);
      }
      burstingResetTest(iSamples);
      
      copySamplesFromTmpBuf(s, iSamples);
      uiPlayPos+=iSamples;

      return 0;
   }
private:

   
   void err(const char *p, int flag){
      iErrs++;
      if(!(iErrFlag&flag))log_audio("a-err", p);
      iErrFlag|=flag;
      puts(p);
   }
   
   void copySamplesFromTmpBuf(short *s, int iSamples){
      
      int cnt = iSamples;
      int zeros = 0;
      
      if(cnt > iSamplesInTmpQueueLeft){
         zeros = cnt - iSamplesInTmpQueueLeft;
         cnt = iSamplesInTmpQueueLeft;

         memset(&s[cnt], 0, zeros * sizeof(short));
         
      }
      if(cnt>0){
         memcpy(s, tmpSamples, cnt * sizeof(short));
         iSamplesInTmpQueueLeft -= cnt;
#if !defined(ANDROID_NDK) && !defined(__APPLE__)
         //volume control
#endif
      }
      if(iSamplesInTmpQueueLeft>0)
         memmove(&tmpSamples[0], &tmpSamples[iSamples], iSamplesInTmpQueueLeft * sizeof(short));
   }
   
   void burstingResetTest(int iSamples){
      if(iMaxBurstCnt<1)return;
      iBurstingResetCnt+=iSamples;
      if(iBurstingResetCnt>iRate*10 || iBurstingResetCnt<0){
         
         if(iBurstCnt*2<iMaxBurstCnt)
            iMaxBurstCnt>>=1;
         
         if(iBurstCnt*2<iPrevBurstCnt)
            iPrevBurstCnt>>=1;
         
         iBurstingResetCnt=0;
      }
   }
   
   inline void calcJit(QPack *q, QPack *prev){
      
      unsigned int uiP_TS=prev->uiTS;
      unsigned int ui_TS=q->uiTS;

      int d;
      
      if(iRate==16000){
         d = 16;
      }
      else if(iRate==8000){
         d = 8;
      }
      else{
         d=iRate/1000;//TODO fix44100
      }

      
      int dTS=(int)(ui_TS - uiP_TS);
      int dTC=(int)(q->uiReceiveTimeStamp - prev->uiReceiveTimeStamp); dTC*=d;
      
      if(q->c && prev->c){
         if(dTC>iMaxTSDiff && dTC<8*iRate)iMaxTSDiff=dTC;
      }
      
      int j = ( dTC - dTS ); if(j < 0)j = -j;

      double dN=(double)j / (1000*d);

      if(dN > 2.)dN = 2.;
      
      if(dN>dJit){
         dJit=dN;
      }
      else {
         dJit = (dJit * 255. + dN) / 256.;
      }
      //it is better to calc burst when we do playback
//burst detector
      if(dTC*2<dTS || dTS<0){//burstDetected
         iBurstCnt++;
         if(iBurstCnt*2>iMaxBurstCnt)iBurstingResetCnt=0;
         
         if(iBurstCnt>iMaxBurstCnt)
            iMaxBurstCnt=iBurstCnt;
         
         if(iBurstCnt>iPrevBurstCnt)
            iPrevBurstCnt=iBurstCnt;
         
         if(iBurstCnt>iMaxBurstCall){
            iMaxBurstCall=iBurstCnt;
         }
         
      }
      else {
         if(iBurstCnt){
            iPrevBurstCnt=iBurstCnt;
            if(iBurstCnt*2>iMaxBurstCnt)iBurstingResetCnt=0;
            
         }
         iBurstCnt=0;
      }
      
   }
   
   int findBestDecodePack(int  iNeedSamplesInBuf){

      unsigned int uiTS_recv = qPack[uiLastReceiveSeq & eMaxPackInQueue].uiTS;
      
      int cnt = (!qPack[uiLastReceiveSeq & eMaxPackInQueue].uiTS || !iPrevDecodedSamples) ?
                20 : ((iNeedSamplesInBuf/iPrevDecodedSamples)+10);
      
      unsigned int uiPos = uiLastReceiveSeq - cnt;
      int iR=0;
      unsigned int uiBestPos=uiPos;
      int bd=iNeedSamplesInBuf*2+2000;

      for(int i=0;i<cnt;i++){
         if(!qPack[uiPos & eMaxPackInQueue].iInUse){ //|| qPack[uiPos & eMaxPackInQueue].seq!=uiPos
            uiPos++;
            continue;
         }
         int d = (int)(uiTS_recv - qPack[uiPos & eMaxPackInQueue].uiTS);
         int cd=abs(d - iNeedSamplesInBuf);
#pragma mark cd= bd= ns= log
//         printf("[cd=%d bd=%d ns=%d]\n",cd,bd,iNeedSamplesInBuf);
         
         if(d>0 && cd<bd){
            bd=cd;
            uiBestPos=uiPos;
            iR=1;
         }
         uiPos++;
      }
      
      if(iR){
         iSeqPlayDecodePackInQueue=uiBestPos;
         iResetPos = 1;
         printf("[reset d=%d sbn=%d buf_pack=%d ]",bd, iNeedSamplesInBuf, uiLastReceiveSeq-iSeqPlayDecodePackInQueue);
         uiPlayPos = qPack[iSeqPlayDecodePackInQueue & eMaxPackInQueue].uiTS;
         iSamplesInTmpQueueLeft=0;
         uiPos=iSeqPlayDecodePackInQueue;
         
         for(int i=0;i<(eMaxPackInQueue>>2);i++){
            uiPos--;
            QPack *q = &qPack[uiPos & eMaxPackInQueue];
            if(q->iInUse)
               relPack(q);
         }
      }
      else {
#pragma mark reset failed try log
//         printf("[reset failed try later %d]\n",cnt);
         iResetPos = 1;
         if(!uiPlayPos)
            uiPlayPos = uiTS_recv -  (unsigned int)iNeedSamplesInBuf;
         iSeqPlayDecodePackInQueue=uiLastReceiveSeq-5;
         
      }
      //should i play from uiLastPlaySeq+1
      //TODO rem
      return iR;
      
   }
   unsigned char prevPackD[MAX_A_RTP_PACK_SIZE+512];
   int iPrevPackLen;
   CCodecBase *prevCodec;

   void decodeNextPacket(int iSamples){
      
      QPack *qNext = findNext(iSeqPlayDecodePackInQueue, eMaxLost);
      
      QPack *q = &qPack[iSeqPlayDecodePackInQueue & eMaxPackInQueue];
      QPack *qN = &qPack[(iSeqPlayDecodePackInQueue + 1) & eMaxPackInQueue];
      
      
      if(q->iInUse && q->seq!=iSeqPlayDecodePackInQueue){
         char b[128];
         sprintf(b, "[seq q %d %d, qn %d %d]",q->seq,iSeqPlayDecodePackInQueue, qN->iInUse, qN->seq);
         relPack(q);
         err(b, 8);
      }
      if(qN->iInUse && qN->seq!=iSeqPlayDecodePackInQueue+1){
         //if !q->iInUse && qPack[(iSeqPlayDecodePackInQueue + 2) & eMaxPackInQueue].seq==qN->seq+1
            //then dont_clear_this and reset iSeqPlayDecodePackInQueue
         char b[128];
         sprintf(b, "[seqN qn %d %d, q %d %d]",qN->seq,iSeqPlayDecodePackInQueue+1,q->iInUse,q->seq);
         relPack(qN);
         err(b, 16);
      }
      
#ifdef T_CALC_JIT_IN_PLAYBACK
      if(qPrevRecv && q->iInUse){
         int d=(int)(q->seq - qPrevRecv->seq);
         //if d<0 - error
         //if d>1 - lost packets
      //--   if(d>0 && d<10) //10 - lost 9 packets
            calcJit(q, qPrevRecv);
      }
      
   //   if(q->iInUse)qPrevRecv = q;
      qPrevRecv = q->iInUse ? q : NULL;

#endif
      
      
      int iUnderFlow=0;
      int iLost=0;
      
      if(!q->iInUse){
         iLost=1;
         iLostCnt++;
         if(!qNext)iUnderFlow=1;
      }
      else uiLastPlaySeq = q->seq;
      
      iUnderFlowDetected = iUnderFlow;
      
      if(q->isOK()){
         //TODO if(iPrevWasCN)resetPos()
         iPrevWasCN=0;
         
         //check - do we have space?
         if(q->c->canDecode(q->pData, q->iDataLen, sizeof(short)*(sizeof(tmpSamples)-iSamplesInTmpQueueLeft-100))){
            int iBytes = q->c->decode(q->pData, &tmpSamples[iSamplesInTmpQueueLeft], q->iDataLen) ;
            iPrevDecodedSamples = iBytes>>1;
            iPrevWasOK=1;
            
            iSamplesInTmpQueueLeft += iPrevDecodedSamples;
            
            iPrevPackLen = q->iDataLen;memcpy(prevPackD,q->pData,iPrevPackLen);prevCodec=q->c;//PLC

         }
         else iPrevWasOK=0;
         
      }
      else{
         if(q->iInUse)iPrevWasCN=1;else iPrevWasCN=0;
         
         //TODO prevCodec->plc(&tmpSamples[iSamplesInTmpQueueLeft],iPrevDecodedSamples)
         if(iPrevWasOK && iLost && iPrevPackLen && qNext && prevCodec && prevCodec->hasPLC()){
            int iBytes = prevCodec->decode(prevPackD, &tmpSamples[iSamplesInTmpQueueLeft], iPrevPackLen) ;//PLC
            iPrevDecodedSamples = iBytes>>1;
            iSamplesInTmpQueueLeft += iPrevDecodedSamples;
            
         }
         
         else if(!qNext || iLost){

            if((iLost || qN->iInUse) && iPrevDecodedSamples){
               memset(&tmpSamples[iSamplesInTmpQueueLeft], 0,  iPrevDecodedSamples * sizeof(short));//TODO PLC
               iSamplesInTmpQueueLeft += iPrevDecodedSamples;
            }
            else{
               //iUnderFlow
               if(iSamples>iSamplesInTmpQueueLeft)
                  memset(&tmpSamples[iSamplesInTmpQueueLeft],0, (iSamples-iSamplesInTmpQueueLeft) * sizeof(short));
               iSamplesInTmpQueueLeft=iSamples;
            }
            if( !qNext){iWaitForIncomingData=1;iResetPos=1;iCanPlay=0;}//cn or lost - reset pos
         }
         else{

            int b = (int)(qNext->uiTS - q->uiTS);//cur is CN - insert silece until next pack
            if(b<0)b=iPrevDecodedSamples;
            if((b + iSamplesInTmpQueueLeft) * sizeof(short) > sizeof(tmpSamples))
               b = sizeof(tmpSamples)/sizeof(short) - iSamplesInTmpQueueLeft;
            
            memset(&tmpSamples[iSamplesInTmpQueueLeft],0,b * sizeof(short));
            iSamplesInTmpQueueLeft += b;
            
        //    puts("qNext");
         }
         iPrevWasOK=0;
         iPrevPackLen=0;
      }
      
      if(q->iInUse){
         uiBufferedTS = qN->iInUse ? qN->uiTS : (q->uiTS + iPrevDecodedSamples);
         uiPlayPos = q->uiTS - (unsigned int)iSamplesInTmpQueueLeft;
         relPack(q);
         iPacketsRemoved++;
      
      }
      iSeqPlayDecodePackInQueue++;
   }

   
   QPack *findNext(int iFrom, int iMaxSkip){
      
      unsigned int t = getTickCount();
      
      for(int i=1;i<=iMaxSkip;i++){
         QPack &q = qPack[(iFrom+i)&eMaxPackInQueue];
         if(!q.iInUse)continue;
         int d=(int)(t - q.uiReceiveTimeStamp);
         if(d>6000){relPack(&q);continue;}
         return &q;
      }
      return NULL;
   }
   
   void relPack(QPack *q){
      if(!q->iInUse)return;
      q->iInUse=0;
      if(q->pData){
         mem.relBytes(q->pData, q->iDataLen);
      }
   }
   
   
   
};

#endif

