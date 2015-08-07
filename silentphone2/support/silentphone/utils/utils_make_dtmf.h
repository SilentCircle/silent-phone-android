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

#ifndef _UTILS_MAKE_DTMF
#define _UTILS_MAKE_DTMF

class CTMakeDtmf{
   enum {eMaxDtmfs=20};
   
   CTMutex m;
   
   int iDtmfsInQ;
   int dtmfQ[eMaxDtmfs+1];
   
   int iNeedSend;
   int iCnt;
   int iSendNext;
   int id;
   char bufDtmf[10];
   int iFirst;
   
   int iSendAudio;
   
   unsigned int uiStartSendRealtime;

   
   void priv_start(int iDtmf){
      iFirst=1;
      iCnt=0;
      id=iDtmf;
      iSendNext=1;
      uiStartSendRealtime = getTickCount();
      printf("[start to send %d %u]\n",id, uiStartSendRealtime);
   }
   
public:
   unsigned int uiStartTS;
   char *p;
   
   CTMakeDtmf()
   {
      p=(char*)&bufDtmf;
      clean();

   }
   
   void clean(){
      iCnt=0;
      iFirst=0;
      iSendNext=0;
      iDtmfsInQ=0;
      iSendAudio=0;
      memset(dtmfQ, 0, sizeof(dtmfQ));
   }
   
   void start(int iDtmf)
   {
      if(iDtmf>11){
         if(iDtmf=='*')iDtmf=10;
         else if(iDtmf=='#')iDtmf=11;
         else iDtmf-=0x30;
      }
      CTMutexAutoLock am(m);
      
      if(iDtmfsInQ+1>=eMaxDtmfs)return;
      
      dtmfQ[iDtmfsInQ]=iDtmf;iDtmfsInQ++;

   }
   
   inline int mustSendNext(){
      return iSendNext;
   }
   //TODO stop
   int mustSend(unsigned int uiTS, int *iDtmf, int *iIsNewDtmf)
   {
      if(!iSendNext){
         if(iDtmfsInQ<=0)return 0;
         if(iSendAudio>0){iSendAudio--;return 0;}
         
         CTMutexAutoLock am(m);
 
         if(iDtmfsInQ<=0)return 0;
         
         priv_start(dtmfQ[0]);iDtmfsInQ--;
         for(int i=0;i<iDtmfsInQ;i++)dtmfQ[i]=dtmfQ[i+1];
      }
      
      if(iFirst)
      {
         if(iIsNewDtmf)*iIsNewDtmf=1;
         iFirst=0;
         uiStartTS=uiTS;
      }
      else{
         if(iIsNewDtmf)*iIsNewDtmf=0;
      }
      //rfc4733, 2.3.5.  Duration Field
      uiTS+=8000/50;
      
      iCnt++;

      if(iDtmf)*iDtmf=id;
      p[0]=id;
      
      unsigned short uiDtmfTs=(unsigned short)(uiTS-uiStartTS);
      uiDtmfTs/=4;//if 16khz->4 8khz->2
      
      int d = (int)(getTickCount() - uiStartSendRealtime );

      if(d>=350)
      {
         p[1]=(unsigned char)0x8a;
         iCnt=0;
         iSendNext=0;
         if(iDtmfsInQ>0)iSendAudio=10;
         printf("[stop to send %d %u q=%d]\n",id,d,iDtmfsInQ);
      }
      else
      {
         p[1]=(unsigned char)0x0a;
      }
      

      p[2]=((unsigned char *)&uiDtmfTs)[1];
      p[3]=((unsigned char *)&uiDtmfTs)[0];

      
      
      return 4;

   }
};

#endif