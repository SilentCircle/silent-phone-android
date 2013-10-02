/*
Created by Janis Narbuts
Copyright © 2004-2012 Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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
public:
   CTMakeDtmf()
   {
      start(0);
      iFirst=0;
      iSendNext=0;
      p=(char*)&bufDtmf;

   }
   int iNeedSend;
   int iCnt;
   int iSendNext;
   int id;
   unsigned int uiStartTS;
   char bufDtmf[10];
   char *p;
   int iFirst;

   void start(int iDtmf)
   {
//         int keys;
  //       if(key=='*')keys=10;
    //     else if(key=='#')keys=11;
     //    else keys=key-0x30;
      if(iDtmf>11){
         if(iDtmf=='*')iDtmf=10;
         else if(iDtmf=='#')iDtmf=11;
         else iDtmf-=0x30;
      }

      iFirst=1;
      iCnt=0;
      id=iDtmf;
      iSendNext=1;
   }
   inline int mustSendNext(){
      return iSendNext;
   }
   //TODO stop
   int mustSend(unsigned int uiTS)
   {
      if(!iSendNext)return 0;
      if(iFirst)
      {
         iFirst=0;
         uiStartTS=uiTS;
      }
      
      iCnt++;

      p[0]=id;
      unsigned short uiDtmfTs=(unsigned short)(uiTS-uiStartTS);
      uiDtmfTs/=2;

      if(uiDtmfTs>=320*9)
      {
         p[1]=(unsigned char)0x8a;
         iCnt=0;
         iSendNext=0;
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