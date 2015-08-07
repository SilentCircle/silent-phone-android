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

#ifndef _C_T_JIT_H
#define _C_T_JIT_H

#include <math.h>

class CTJit{
   int iBurstCnt;
   unsigned int uiPrevPackTs;
   unsigned int uiPrevPos;
   unsigned int uiPrevPosOk;
   
   int iMaxBurstReduced;
   int iMaxBurstReduceAfter;
   
   unsigned int uiUpdatedPackLostAt;
   int iMaxBufferedSizeInMS;
   
public:
   int iAudioCardReadBufSize;
   
   int setMaxBufSize(int ms){
      
      int ret=0;
      if(ms==0)ms=2000;//default
      
      if(ms<500){ms=1500;ret=-1;}
      else if(ms>3500){ms=3500;ret=-2;}
      
      iMaxBufferedSizeInMS=ms;
      
      return ret;
   }
   
   unsigned int uiPLost[4];
   int iFramesPlayed;
   CTJit(){resetJit();iMaxBurst=0;iMaxJit=20;iBytesToSetBuf=1280;}
   int iJit;
   // double dJit;
   int iMaxJit;
   double dMaxJit;
   int iBytesToSetBuf;
   int iPrevWasSetPos;
   int iLostCnt;
   
   
   int iPrevBurst;
   
   
   unsigned int uiPrevBigBurstAT;//TODO remeber prev big burst >10
   int iIncBufWarning;
   int iIncBuf;
   
   int iPackWO_CN;
   int iDetectedPackBytes;
   int iBurstDetected;
   int iMaxBurst;
   void resetJit()
   {
      
      iAudioCardReadBufSize=400;
      iMaxBufferedSizeInMS=1800;
      
      iMaxBurstReduceAfter=0;
      uiUpdatedPackLostAt=0;
      uiPLost[0]=uiPLost[1]=uiPLost[2]=uiPLost[3]=0;
      iMaxBurstReduced=0;
      uiPrevBigBurstAT=0;
      iPrevBurst=0;
      iFramesPlayed=0;
      iMaxBurst>>=2;
      iBurstCnt=0;
      iBurstDetected=0;
      iDetectedPackBytes=640;
      iIncBufWarning=0;
      iIncBuf=0;
      iLostCnt=0;
      iPackWO_CN=0;
      uiPrevPos=uiPrevPackTs=0;
      iJit=8;
      iMaxJit=80;//320;iMaxJit/=8;
      dMaxJit=double(iMaxJit);
      iBytesToSetBuf=2000;// *7;iBytesToSetBuf>>=3;
      //dJit=0.0;
      iPrevWasSetPos=0;
      
      setMaxBufSize(iMaxBufferedSizeInMS);
   }
   void checkJit(char *p, int iLen, unsigned int uiPos, int iCanSetPos, int iRate, int &iMaxBuf, int &iMinBuf, int &iResetPosNow)
   {
      //TODO reset
      iMaxBuf=iRate*8;//2sec//((int)res+80)*iRate/500;//iDelta*2;
      if(!p && iLen && uiPos){
         iPackWO_CN=0;
      }
      if(!p && !iLen && uiPos)iResetPosNow=1;
      
      if(iLen>iRate*2 || !iCanSetPos || !uiPos){uiPrevPosOk=0; return ;}
      uiPos/=2;
      unsigned int uiCurTs=getTickCount();//GetTickCount();
      if((int)uiCurTs-(int)uiPrevPackTs>3000)iResetPosNow=1;
      
      
      
      if(uiPrevPackTs && p)
      {
         iPackWO_CN++;
         
         
         int iDelta=0;
         if(iRate==16000)
            iDelta=(int)(uiPos>>4)-(int)(uiPrevPos>>4);
         else if(iRate==8000)
            iDelta=(int)(uiPos>>3)-(int)(uiPrevPos>>3);
         else
            iDelta=((int)uiPos-(int)uiPrevPos)*1000/iRate;
         
         
         int iDeltaTime=(int)(uiCurTs-uiPrevPackTs);
         //  printf("[%d %d ms]",iDelta,iDeltaTime);
         
         if(iDeltaTime>=2000 || iPrevWasSetPos)
         {
            iPackWO_CN=0;
            iResetPosNow=1;
         }
         /*
          else if(iPackWO_CN>50*60){
          iPackWO_CN=0;
          iResetPosNow=2;
          }
          */
         //if(iDelta<0)detected pack seq err
         if(iDelta<800 && iDeltaTime<1500 && iDeltaTime>=0 && iDelta>-200)
         {
            
            //s->jitter += d - ((s->jitter + 8) >> 4);
            int iDetectedMult2=iLen>((uiPos-uiPrevPosOk)*2);
            int iLost=0;
            if(uiPrevPosOk && uiPos>uiPrevPos && (((uiPos-uiPrevPosOk)<<1)<<iDetectedMult2)!=iLen)iLost=1;//mult 4 rtp2
            if(uiPos<uiPrevPos)iLost++;
            if(!iLost && iLen<8000)iDetectedPackBytes=iLen>>iDetectedMult2;
            
            //packetSZ msec
            int ps=iDetectedPackBytes*8000*20/iRate/320;
            // printf("[packet size %d]",ps);
            if(ps<20)ps=20;
            else if(ps>100)ps=100;
            
            
            //    if(iLost)printf("--%d-sz=%d-[ %d %d %d]",iLost,iDetectedPackBytes,iLen,uiPos-uiPrevPos,uiPos-uiPrevPosOk);
            
            uiPrevPosOk=uiPos;
            
#define _JIT_SH 8
            int ix=abs(iDeltaTime-iDelta)<<_JIT_SH;
            
            int tmpJ=iJit<<_JIT_SH;
            if(ix>iJit)
            {
               tmpJ=((ix)+tmpJ*3+2)>>2;
            }
            else
               tmpJ-=(abs(ix-tmpJ)+16)>>5;
            
            tmpJ+=(1<<(_JIT_SH-1));
            iJit=tmpJ>>_JIT_SH;
            
            //iFramesPlayed ios slow audio startup
#define T_BIG_BURST_THRESHOLD 20
            
            if(!iLost && iDeltaTime*2<iDelta && iFramesPlayed>iRate*2){
               
               if(iBurstDetected>8)iBurstDetected=2;
               else if(iBurstCnt>2)iBurstDetected=1;
               iBurstCnt++;
               
               if(iBurstCnt*3>iMaxBurst && iBurstCnt>T_BIG_BURST_THRESHOLD)
                  iMaxBurstReduceAfter=1000*20/ps;//reduce after 20 sec
               
               if(iBurstCnt>1 && iBurstCnt>iMaxBurst)iMaxBurst=iBurstCnt;
               if(iMaxBurst>200)iMaxBurst=200;
               
            }
            else {
               if(!iLost){
                  if(iBurstCnt*3>=iMaxBurst*2){
                     iPrevBurst=iBurstCnt;
                     uiPrevBigBurstAT=uiCurTs;
                  }
                  if(iBurstCnt>T_BIG_BURST_THRESHOLD && uiPrevBigBurstAT+30000<uiCurTs){
                     iMaxBurst=(iMaxBurst+iBurstCnt)>>1;
                  }
                  
                  iBurstCnt=0;
               }
            }
            
            double dj=double(iJit);
            
            if(dj>dMaxJit)
               dMaxJit=1+dj;
            else{
               dMaxJit-=(fabs(dj-dMaxJit)/(256+512*iBurstDetected));//was 512+1024*iBurstDetected
            }
            
            if(iLen>0){
               if(iIncBufWarning)dMaxJit*=1.1;
               if(iIncBuf)dMaxJit*=1.2;
               iIncBufWarning=0;
               iIncBuf=0;
            }
            //   printf("%f %f\n",dj,dMaxJit);
            
            if(uiUpdatedPackLostAt+5000<uiCurTs){
               uiPLost[3]=uiPLost[2];
               uiPLost[2]=uiPLost[1];
               uiPLost[1]=uiPLost[0];
               uiPLost[0]=0;
               uiUpdatedPackLostAt=uiCurTs;
            }
            if(iLost){
               uiPLost[0]++;
            }
            
            iMaxBurstReduceAfter--;
            
            if(iMaxBurstReduceAfter<0 && !iLost){
#if 0
               int d=((int)uiCurTs-(int)uiPrevBigBurstAT);
               if(d<0)d=-d;
               if(d>60*1000 && d<60*60*1000){
#else
                  if(iMaxBurst>2 && iBurstCnt<2){
#endif
                     if(iMaxBurst<4)iBurstDetected=0;
                     
                     iMaxBurst>>=1;
                     //if(iPrevBurst>iMaxBurst)iMaxBurst=iPrevBurst;
                     //iPrevBurst>>=1;
                     
                     iMaxBurstReduceAfter=1000*20/ps;//reduce after 20 sec
                     
                     double nj=(1+((iMaxBurst*5)/8)*ps);
                     if(nj<dMaxJit){dMaxJit+=nj;dMaxJit/=2;}
                     
                  }
               }
               
               int mb=iBurstCnt?(1+((iMaxBurst*6)>>3)*ps):(((iMaxBurst*5)>>3)*ps+20);
               if(mb>2000)mb=2000;//2s
               //TODO if loosing pack every sec use<ps*2
               
               if(iLost && dMaxJit<ps*2+40)dMaxJit=ps*(2+(uiPos>uiPrevPos))+40;//prev lost
               if(iBurstDetected && dMaxJit<mb)dMaxJit=mb;//
               
               
               
               if(dMaxJit>(double)iMaxBufferedSizeInMS)
                  dMaxJit=(double)iMaxBufferedSizeInMS;
               //printf(" %f ",dMaxJit);
               
               
               iMaxJit=int(dMaxJit);
               
               int iAdd=iDetectedPackBytes*3/4;
               iAdd&=~1;
#if defined(ANDROID_NDK) || defined(__APPLE__) || defined(__linux__) || defined(linux)
               iMinBuf=(iAudioCardReadBufSize&~1)+iAdd;
               iBytesToSetBuf=(((iMaxJit*5)>>2)+10)*iRate/500+iMinBuf;
               //  iMinBuf=320*iRate/8000+iAdd+200;
#else
               iMinBuf=320*3*iRate/8000+200+iAdd;//iMaxBufferSize/8;//0;//iRate/10;//iJit*iRate/1000;//*2;
               iBytesToSetBuf=(((iMaxJit*3)>>1)+30)*iRate/500+iRate/40+iMinBuf;;
               
#endif
               
               //iBytesToSetBuf=(10+(iMaxJit*320/iLen+10)*iLen/320)*iRate/500+iRate/40;;
               if(iBytesToSetBuf*4>iMaxBuf*3)iBytesToSetBuf=(iMaxBuf*3)>>2;
               if(iBytesToSetBuf<iMinBuf)iBytesToSetBuf=iMinBuf;
               
               int b=iRate*2*iMaxBufferedSizeInMS/1000;
               if(iBytesToSetBuf>b)iBytesToSetBuf=b;
               
               iBytesToSetBuf&=~1;
               
            }
            else uiPrevPosOk=0;
         }
         else uiPrevPosOk=0;
         uiPrevPos=uiPos;
         uiPrevPackTs=uiCurTs;
         iPrevWasSetPos= p==NULL && iLen==0;
         
      }
      
   };
#endif
