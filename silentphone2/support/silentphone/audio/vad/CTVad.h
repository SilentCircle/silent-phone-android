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

#ifndef _C_T_VAD_H
#define _C_T_VAD_H


#include "../../utils/CTFifo.h"

#define  VAD1
extern "C"{
#include "vad.h"
};
#include <string.h>

#ifndef absx
#define absx(AAA) (AAA)>0?(AAA):-(AAA)
#endif

class CT_CRC{
   int t_crc_table[256];
   int crc;

   void init(){
      crc=0xffff;
      int i;
      for(i = 0; i <= 255; i++) {
        int k = i << 8;
        int j;
        for(j = 0; j < 8; j++)
            if((k & 0x8000) != 0)
                k = k << 1 ^ 0x1021;
            else
                k <<= 1;

        t_crc_table[i] = k & 0xffff;
      }      
   }
public:
   CT_CRC(){
      init();
   }

   int update(unsigned char *p, int iLen){
      int iCrc=crc,k,c,l;
      for(k=0;k<iLen;k++,p++)
      {
         c = (*p) & 0xff;
         l = (iCrc >> 8 ^ c) & 0xff;
         iCrc = (iCrc << 8 & 0xffff) ^ t_crc_table[l];
      }
      crc=~iCrc & 0xffff;
      return crc;      
   }
   inline int getLastCRC(){return crc;}
};


#if 0
//def VAD2
class CTVadDetection2{
   CT_CRC crc;
   vadState2 *st;
   int iIsSilenCounter;
   char buf[320];
   char tmpBuf[4096];
   char prevBuf[4096];
   int iCanReplace;
   inline void replace(char *p, int iInLen, int iBytes)
   {
      if(iInLen<=iBytes)
      {
         memcpy(tmpBuf,prevBuf,iInLen);//varbuut kopeet 1x un iedot veco pointeri uz buferi
         memcpy(prevBuf,p,iInLen);
         memcpy(p,tmpBuf,iInLen);
         return;
      }

      memcpy(tmpBuf,p+iInLen-iBytes,iBytes);//salabaa pedeejos bytes
      memmove(p+iBytes,p,iInLen-iBytes);

      memcpy(p,prevBuf,iBytes);
      memcpy(prevBuf,tmpBuf,iBytes);//ieliek prev no temp;
   }
public:
   CTVadDetection2(){ vad2_init (&st); iIsSilenCounter=0;}

   ~CTVadDetection2() { if(st)vad2_exit(&st);}

   void reset(){if(st)vad2_reset(st);iIsSilenCounter=0;}
   int isSilence(char *p ,int iLen)
   {
     // return vad_detect((Word16*)p,st);
      int i;
#if 0
      int hasVoice=0;
      int iShortLen=iLen/2;
      for(i=0;i<iShortLen;i+=80)
      {
         //if(!vad2((Word16*)p+i,st))hasVoice=0;
         hasVoice|=vad2((Word16*)p+i,st);
      }
#else
      int hasVoice=1;
      int iShortLen=iLen/2;
      for(i=0;i<iShortLen;i+=80)
      {
         if(!vad2((Word16*)p+i,st))hasVoice=0;
      }
#endif

//
     // replace(p,iLen,160);


      if(hasVoice==0)
      {
         iIsSilenCounter--;
      }
      else iIsSilenCounter=2;

      return iIsSilenCounter;
   }

};
#else
class CTVadDetection2{
   vadState1 *st;
   
   CTFiFo<1280> fifo;
   int iIsSilenCounter;
   //int iRandomCnt;
   CT_CRC crc;
   
   
   
   int iPacketsWOSilence;
   
   
   char tmpBuf[2048];
   char prevBuf[2048];
   inline void replace(char *p, int iInLen, int iBytes)
   {
      if(iInLen>sizeof(tmpBuf))return;
      if(iInLen<=iBytes)
      {
         memcpy(tmpBuf,prevBuf,iInLen);//varbuut kopeet 1x un iedot veco pointeri uz buferi
         memcpy(prevBuf,p,iInLen);
         memcpy(p,tmpBuf,iInLen);
         return;
      }

      
      memcpy(tmpBuf,p+iInLen-iBytes,iBytes);//salabaa pedeejos bytes
      memmove(p+iBytes,p,iInLen-iBytes);

      memcpy(p,prevBuf,iBytes);
      memcpy(prevBuf,tmpBuf,iBytes);//ieliek prev no temp;
   }
   char bufOld[640];
   char *getPtr(char *p)
   {
     // return p;

      memcpy(bufOld,bufOld+320,320);
      memcpy(bufOld+320,p,320);

      return (char *)&bufOld+320;
   }
   int hasVoice;
   int iReset;
   int iHasOutputData;
   enum{eHasNotOutputData=100000};
   
    int iRateIn;
   
   int iWasVoice;
   int iWasGoodVoice;
   
      short tmpRateConv[4096];
   
public:
   int iVadIsOff;
   
   int iHasEcho;
   int iIsZRTPActive;
   int iIsPlaybackVad,iCanReplace;
   int iSendNextNPackets;

   CTVadDetection2(){ 
      memset(this,0,sizeof(*this));vad1_init (&st);iHasOutputData=eHasNotOutputData; 
      iSendNextNPackets=10;
      iPacketsWOSilence=0;
      iHasEcho=0;
      iRateIn=8000;
      iVadIsOff=0;
  
   }

   ~CTVadDetection2() { if(st)vad1_exit(&st);}

   void inline resetAll(){reset();}
   void reset(){iCanReplace=1;iIsPlaybackVad=0;iReset=1;iHasOutputData=eHasNotOutputData;iMinVol=2000;iMaxVol=10000;}
   //neko nevajag noklusinaat ja nekas neienaak
   //TODO varbuut tam arii iet cauri shim silencerim
   //lai zinaatu vai vajai vai ne noklusinaat
   void hasOutputData(int iLen)
   {
      iHasOutputData=iLen+1280;
   }
  
   int iPrevMax;
   int iVolMax;
   int iPrevHangCnt;
   int iCurentHangCnt;
   unsigned char ucFlag;
   int iAvgHC;
   int iAdjustRandom;
   int iPrevResult;

   
   inline int wasVoice(){return iWasVoice;}
   inline int wasGoodVoice(){return iWasGoodVoice;}
   inline int getVol(int _max){
      
      int nv = (iVolMax*_max + 0x4000)/0x7fff;
   
      return nv;
   }
   

   void onResetPrevResult(){iPrevResult=100;}

   
   
   int isSilence2(short *p ,int iSamples, int iRate){
      iRateIn=iRate;
      int iSVad=iSamples;
      short *pIn=p;
      
      if(iRate>8000){
         iSVad=iSamples*8000/iRate;
         int iStep=iRate/8000;
         //TODO fix if !16000 24000 32000
         int iPout=0;
         int iV=0;
         for(int i=0;i<iSVad;i++){
            tmpRateConv[i]=p[iPout];
            iPout+=iStep;
            
            if(tmpRateConv[i]>iV)iV = tmpRateConv[i] ;
         }
         iVolMax = iV;
         p=&tmpRateConv[0];
      }
      iPrevResult=isSilence2_priv((char*)p, iSVad*2);
      
      iWasVoice = hasVoice;
      iWasGoodVoice = hasVoice && ucFlag==255 && iAvgHC>4;
      
      if(iCanReplace && !iIsPlaybackVad)replace((char*)pIn,iSamples*2,160*iRate/8000);

      return iPrevResult;
   }
   int prevResult(){
      return iPrevResult;
   }
   int isSilence2_priv(char *p ,int iLen)
   {
      //works with 8KSamples
      int iSamplesIn=iLen/2;
      if(iReset)
      {
         iReset=0;
         if(st)vad1_reset(st);iIsSilenCounter=10;
      }
      int iRand=crc.getLastCRC();
      
      int i;
      int iHC=0;
      int iP2=0;
#if 1

      iAvgHC=0;
      hasVoice=0;
      int iVoice=0;
      int iShortLen=iLen/2;
      if(iIsPlaybackVad && iLen>0){
         fifo.add(p,iLen);
         iShortLen=fifo.bytesIn()/2;
      }
      if(iShortLen<160)return iIsSilenCounter;
      for(i=0;i<iShortLen;i+=160)
      {
         //if(!vad2((Word16*)p+i,st))hasVoice=0;
         if(iIsPlaybackVad){
            int bi=fifo.bytesIn();
            if(bi>=320){
               iVoice=vad1(st,(Word16*)getPtr(fifo.get(320)));//vajag jo vads skaataas atpakalj
            }
         }
         else {
            iVoice=vad1(st,(Word16*)getPtr(p+i));
            iRand=crc.update((unsigned char *)p+i,16);
         }
         
         ucFlag<<=1;
         if(iVoice)ucFlag|=1;else ucFlag&=~(1);
         
         hasVoice+=iVoice;
         iHC+=st->hang_count;
        // printf("(%d, %d) --",st->hang_count,st->best_corr_hp);
         iP2+=st->burst_count;
         //if(hasVoice)break;
      }
      if(iShortLen)
         iAvgHC=iHC*160/iShortLen;
      else
         iAvgHC=iHC;
      iCurentHangCnt=iHC;

      if(hasVoice)
      {
         switch(ucFlag&0x0f)
         {
         case 1://0000 0001
         case 2://0000 0010
         case 4://0000 0110
         case 6://0000 0100
            hasVoice=0;
         }
         if(hasVoice)
         {
            switch(ucFlag)
            {
            case   8://00001000
            case  12://00001100
            case  24://00011000
            case  40://00101000
            case  56://00111000
            case 136://10001000
            case 140://10001100
            case 152://10011000
            case 200://11001000
               hasVoice=0;
            }
         }
      }
#if (defined (_CONSOLE))
     printf("%3d ",iHC);
#endif
      if(hasVoice)
      {
         if(iHasOutputData>0 && iHC*3<iPrevHangCnt)hasVoice=0;
         if(ucFlag==255)
            iPrevHangCnt=(iPrevHangCnt+iHC)/2;
      }

      if(iHasOutputData!=eHasNotOutputData)iHasOutputData-=iLen;
      //------------------

#else
      hasVoice=1;
#endif 
      iPrevMax=iVolMax;
      
      if(iHasEcho)//1)//iHasEcho)
      {
         hasVoice=isSilenceVoiceVol(1,p,iLen,hasVoice,20,100,15); // if echo 
      }
      else   hasVoice=isSilenceVoiceVol(0,p,iLen,hasVoice,20,100,3); // no echo if 
      
//--move--      if(iCanReplace && !iIsPlaybackVad)replace(p,iLen,160);
      
      iRand+=(int)ucFlag;
      
      int sec2=16000/(iSamplesIn+1);
      int sec3=24000/(iSamplesIn+1);
      if(iAdjustRandom)sec3=0;;
      if(hasVoice==0)
      {
         iIsSilenCounter--;
         iPacketsWOSilence=0;
      }
      else {
         iPacketsWOSilence++;
         
         if(iIsPlaybackVad || iPacketsWOSilence>50*8)//8 sec
            iIsSilenCounter=(iLen<160)?8:((iLen<320)?4:2);
         else{
            iIsSilenCounter=iLen<641?6:4; 
            if(iIsZRTPActive){
               iRand=((iRand>>6)+iRand)>>1;
               iIsSilenCounter+=((iRand+abs(p[0]+p[1]))&127);
               iIsSilenCounter+=4;
               if(iIsSilenCounter>sec2)iIsSilenCounter=sec2;
               if(iLen<641)iIsSilenCounter+=4;
               
            }
         }
         
      }
      if(iSendNextNPackets>0){iSendNextNPackets--;return 1;}
      if(!iIsPlaybackVad && iIsSilenCounter<0 && ((-iIsSilenCounter)&3)==3)return 0;//sends comfortnoice packet every 3 pack

      
      return iIsSilenCounter+(iIsZRTPActive?sec3:0);
   }
   int iMaxVol,iMinVol;
   int iPrevWasVoice;

   
   int isSilenceVoiceVol(int iUseEchoCancel, char *p, int iLen, int iHasVoice, int iTimesBelowMax=20, int iTimesOverMin=40, int iPX=10)
   {
      int iShorts=iLen/2;
      short *s=(short *)p;
      int iVol=0;
      int iVal;
#if defined (_CONSOLE)
      printf("%d ",iHasVoice);
#endif
      while(iShorts>0)
      {
         iVal=absx(*s);
         iVol=max(iVal,iVol);
         iShorts--;
         s++;
      }
      if(iTimesBelowMax<14 || iTimesBelowMax>40)iTimesBelowMax=20;
      if(iTimesOverMin<12 || iTimesOverMin>100)iTimesOverMin=40;

      int  iP=iMaxVol-iMinVol;
      if(iP==0)iP=1;
      iP=(iVol-iMinVol)*100/iP;
      

      if(iHasVoice)
      {
  
         iHasVoice=iP>iPX;
         if(iHasVoice)
         {
            iPrevWasVoice=2;
         }
         else if(iPrevWasVoice>0)
         {
            iHasVoice=1;
         }
//         else iPrevWasVoice=0;
      }
      else if(iUseEchoCancel==0 && iVol>iMinVol*4 && iVol*5>iMaxVol)
      {
         //iPrevWasVoice=2;
         iHasVoice=1;
      }
      //test---<<
      iPrevWasVoice--;
      if(iPrevWasVoice==1)
      {
         iHasVoice=1;
      }
      //!iHasVoiceIn
      
      if(iMinVol==0)iMinVol=1;
      int iD=iMaxVol/iMinVol;
      if(iD>50)iD=50;

      if(iVol>iMaxVol)
      {
         iMaxVol=(iMaxVol+iVol+1)>>1;
      }

      if(iVol<iMinVol)
         iMinVol += (( iVol) - iMinVol) / 2;//skaluma liimenis
      else if(iD>3)
         iMinVol += (( iVol) - iMinVol) / 40;//100;// 30 bij ok


      if(!iHasVoice && 
         iD>25)
      {
         iMaxVol += (( iVol) - iMaxVol) / 100;//5
         //iMaxVol=iMaxVol*(150-iD)/(151-iD);//(iVol+iMaxVol*20)/11;
      }
      else
         iMaxVol += (( iVol) - iMaxVol) / 1000;//5
      if(iD>25)
      {
         iMinVol += (( iVol) - iMinVol) / 200;//skaluma liimenis
      }
#if defined (_CONSOLE)
      printf("v-%06d p-%3d ",iVol,iP);
#endif
      return iHasVoice;
   }
#if defined (_CONSOLE)
   void tobin(int x)
   {
      int i;
      char buf[9];
      for(i=0;i<8;i++)
      {
         //printf(x&1?"1":"0");
         buf[7-i]=x&1?'1':'0';
         x>>=1;

      }
      buf[8]=0;
  //    printf(buf);
   }
#endif
};
#endif //vads
#endif //_C_T_VAD_H

