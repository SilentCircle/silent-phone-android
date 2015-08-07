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
#ifndef _C_T_TIMER_H
#define _C_T_TIMER_H

#include "../os/CTThread.h"
#include<string.h>

class CTTimer;
   
class CTTimerCB{
   public:
   virtual int onTimerClick(CTTimer *cTimer)=0;//if return 0 stop
};

unsigned int getTickCount();
void uSleep(int usec);

class CTTimer{
   int iStarted;
   //CTThread th;
   unsigned int uiStep;
   unsigned int uiUpdateUntil;
   int iPriority;
   int iInThread;
   int iUpdateFlag;
public:
   void *pUserData;
   CTTimerCB *cb;
   CTTimer(){
      uiUpdateUntil=0;
      iUpdateFlag=1;
      iStarted=0;
      iInThread=0;
      cb=NULL;
      uiStep=10;
      pUserData=0;
   }
   ~CTTimer(){stopT();int i=50;while(iInThread && i>0){uSleep(30000);i--;}}
   inline unsigned int uiGetStep(){return uiStep;}
   inline void setStep(unsigned int ui){uiStep=ui;}
   void setPrio(int iPrio)
   {
      iPriority=iPrio;
   }
   int startT()
   {
      if(!iStarted)
      {
         iStarted=1;
         CTThread *th=new CTThread();
         th->destroyAfterExit();
         strcpy(th->thName,"_CTTimer");
         th->create(&th2fnc,this);
         iInThread=1;
      }
      return 0;
   }
   void updateOrStart()
   {
      if(iInThread && iStarted){
         uiUpdateUntil=getTickCount()+uiStep;
         iUpdateFlag=1;
      }
      else  startT();
   }
   void stopT()
   {
     // if(!iStarted)return;
      iStarted=0;
   }
   static int th2fnc(void *p)
   {
      ((CTTimer*)p)->thfnc();
      return 0;
   }
   void thfnc()
   {
      unsigned int uiT;
      unsigned int ui=getTickCount()+uiStep;
      unsigned int uiTTSleep;
      #ifdef __SYMBIAN32__
      if(iPriority>0)
      {
         RThread().SetPriority(EPriorityAbsoluteForeground);
      }
      #endif
      int iFirst=1;
      if(uiStep>2000)uSleep(1800*1000);else uSleep(uiStep*900);
      
      while(iStarted)
      {
         uiT=getTickCount();
         
         
         uiTTSleep=(ui-uiT); uiTTSleep-=2;
         if(uiTTSleep>0 && uiTTSleep<1000000){
            if(uiTTSleep>1500){
               uSleep(1200*1000);
               continue;
            }
            else uSleep((int)uiTTSleep*1000);
         }
         if(iUpdateFlag){
            iUpdateFlag=0;
            ui=uiUpdateUntil;
            continue;
         }
         //if(cb && iFirst==0)
         if(cb && cb->onTimerClick(this)!=1){iStarted=0;break;}
         //iFirst=0;
         ui+=uiStep;

         /*
         if(cb && iFirst==0)if(cb->onTimerClick(this)!=1){iStarted=0;break;}
         iFirst=0;
         uiTTSleep=uiStep-(getTickCount()-uiT); uiTTSleep-=3;
         if(uiTTSleep>0 && uiTTSleep<1000000) uSleep((int)uiTTSleep*1000);
         */
      }
      iInThread=0;
   }   
   
   
};

#endif 
