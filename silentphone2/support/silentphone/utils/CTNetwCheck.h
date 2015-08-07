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

#ifndef _C_T_NETW_CHECK_H
#define _C_T_NETW_CHECK_H
class CTCheckNetwork{
   CTSock sock;
   CTThread th;
   int iInitOk;
   int iWorks;
   int iTestting;
   unsigned int uiStopTime,uiTime;
   void sendTo(ADDR *a)
   {
      char buf[10];
      memset(buf,0,10);
      sock.sendTo(&buf[0],10,a);
   }
   int thFnc2()
   {
      sendTo(&addrToCheck);
      char buf[64];
      ADDR a;
      while(iTestting)
      {
         int iLen=sock.recvFrom(&buf[0],63,&a);
         if(iLen==10)
         {
            iWorks=1;
         }
         break;
      }
      if(iWorks==0)iWorks=-1;
      //printf("works=%d\n",iWorks);
      iTestting=0;
      stop();
      uiStopTime=0;
      return 0;
   }
   static int thFnc(void *p)
   {
      return ((CTCheckNetwork*)p)->thFnc2();
   }
public:
   //CTSockCB scb;
   ADDR addrToCheck;
   CTCheckNetwork(CTSockCB &scb):sock(scb){iInitOk=0;iWorks=0;iTestting=0;uiStopTime=0;uiTime=0;}
   ~CTCheckNetwork(){stop();Sleep(20);}
   void stop(){uiStopTime=0;if(iTestting){iTestting=0;sock.sendTo("",0,&sock.addr);Sleep(20);}sock.closeSocket();iInitOk=0;}
   void  init()
   {
      if(iInitOk)return;
      iInitOk=1;
      ADDR a="127.0.0.1:5056";
      sock.createSock(&a,1);

   }
   inline int works(){return  iWorks==1;}
   inline int testing(){return iTestting;}
   void onTimer(int iDelta)
   {
      uiTime+=(unsigned int)iDelta;
      //printf("%d %d\n", uiTime,uiStopTime);
      if(uiStopTime && uiStopTime<uiTime)
      {
         stop();
        // puts("stop");
         uiStopTime=0;
         iWorks=-1;
         iTestting=0;
      }
      
   }
   void test()
   {
      if(iTestting)return;
      init();
      uiStopTime=uiTime+7000;
      iTestting=1;
      iWorks=0;
      sendTo(&addrToCheck);
      th.create(&CTCheckNetwork::thFnc,this);;
   }
   
   
      
};
#endif
//_C_T_NETW_CHECK_H
