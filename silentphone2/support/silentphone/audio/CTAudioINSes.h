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

#ifndef _C_T_AUDIO_IN_SES
#define _C_T_AUDIO_IN_SES

#include "CTAudioUtils.h"

class CTAudioINSes{
   enum {eMaxSess=20};
   void *iAudioInSes[eMaxSess];
public:
   
   CTAudioINSes(){
      memset(iAudioInSes,0,sizeof(iAudioInSes));
   }
   
   int addSes(void *id)
   {
      if(!id){
         return 0;
      }
      for(int i=0;i<eMaxSess;i++){
         if(iAudioInSes[i]==id)return 0;//TODO test new
      }

      for(int i=0;i<eMaxSess;i++){
         if(!iAudioInSes[i])
         {
            iAudioInSes[i]=id;
            return 0;
         }
      }
      return -1;
   }
   
   int remSes(void *id)
   {
      for(int i=0;i<eMaxSess;i++){
         if(iAudioInSes[i]==id)
         {
            iAudioInSes[i]=0;
            return 0;
         }
      }
      return -1;
   }
   
   inline int hasSesActive()
   {
      for(int i=0;i<eMaxSess;i++)
         if(iAudioInSes[i]){return 1;}
      return 0;
   }
};

class CTAudioOutList{
   enum{eMaxL=16};
   typedef struct{
      int iUsed;
      //TODO conf marker
      CTAudioGetDataCB *cb;
      CTAudioOutBase *ab;
      unsigned char bufPrevTMP[4096];
      
   }AL;
   AL al[eMaxL];
   int iActiveCnt;
   int iErrDetected;
   int iCheckAfter;
public:
   
   CTAudioOutList(){
      iCheckAfter=0;
      iActiveCnt=0;
      iErrDetected=0;
      memset(al,0,sizeof(al));
   }
   //TODO sync with active calls, check for missmatch
   int activeCnt(){
      int n=0;
      for(int i=0;i<eMaxL;i++){
         if(al[i].iUsed){
            n++;
         }
      }
      if(iActiveCnt!=n){iErrDetected=1;puts("Err CTAudioOutList iActiveCnt!=n");}
      else iErrDetected=0;
      return n;
   }
   
   int addCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab){
      for(int i=0;i<eMaxL;i++){
         if(al[i].iUsed && al[i].cb==cb && al[i].ab==ab){
             printf("[add= AO list %p %d]",ab,iActiveCnt);
            
            return 0;
         }
      }
      for(int i=0;i<eMaxL;i++){
         if(!al[i].iUsed){
            al[i].cb=cb;al[i].ab=ab;
            
            al[i].iUsed=1;
            iActiveCnt++;
            
            printf("[add+ AO list %p %d]",ab,iActiveCnt);
            return 0;
         }
      }
      //add to list
      return -1;
   }
   
   int remCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab){
      for(int i=0;i<eMaxL;i++){
         if(al[i].iUsed && al[i].cb==cb && al[i].ab==ab){
            al[i].iUsed=0;
            iActiveCnt--;
            printf("[rem AO from list %p %d]",ab,iActiveCnt);
            return 0;
         }
      }
      return -1;
   }
   
   int isAbsoluteSilence(void *p, int sz){
      sz/=sizeof(int);
      sz--;
      long long v=(long long)p;
      long long a=sizeof(long long)-1;
      v+=a;v&=~a;
      int *i=(int*)v;
      while(sz>0){
         sz--;
         if(i[0])return 0;
         i++;
      }
      
      return 1;
   }
   
   int getAudioData(void *p, int iSamples, int iDevRate, int iSampleTypeEnum, int iOneSampleSize, int iIsFirstPack){
      
      int ac=iErrDetected || iCheckAfter?activeCnt():iActiveCnt;

      if(iCheckAfter<0)iCheckAfter=15;
      iCheckAfter--;

      short *toMix[eMaxL];
      int iMixStreams=0;
      
      int iFound=0;
      for(int i=0;i<eMaxL;i++){
         if(iFound==ac)break;
         if(al[i].iUsed){
            al[i].cb->getAudioData(p, iSamples, iDevRate, iSampleTypeEnum, iOneSampleSize, iIsFirstPack);
            iFound++;
            
            if(!al[i].cb->canUseAudioData())continue;
            
            memcpy(al[i].bufPrevTMP, p, iSamples*iOneSampleSize);
            //TODO notify mixer about speech
            
            toMix[iMixStreams]=(short*)&al[i].bufPrevTMP[0];
            iMixStreams++;
            
         }
      }
      if(iFound!=ac){
         if(!iErrDetected || iCheckAfter==1)printf("[err AL]");
         iErrDetected=2;
      }
      
      if(iMixStreams>1){
         CTAudioUtils::mix<short,int>(iMixStreams,toMix,iSamples,32700,-32700,(short*)p,1);
      }
      else if(iMixStreams){
         memcpy(p, toMix[0], iSamples*iOneSampleSize);
      }
      else{
         memset(p, 0, iSamples*iOneSampleSize);
      }
      
      return 0;
   }
   
};

#endif


