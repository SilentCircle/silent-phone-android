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

#ifndef C_T_ENROPY_COLLECTOR
#define C_T_ENROPY_COLLECTOR

class CTEntropyCollector: public CTAudioCallBack{
   //audioMngr.cAI.
   
   
   CTEntropyCollector(){
      
   }
   void start(){
      CTThread *th=new CTThread();
      
      th->create(&sthFnc,this);
      th->destroyAfterExit();
   }
   static int sthFnc(void *p){
      ((CTEntropyCollector*)p)->thFnc();
      delete (CTEntropyCollector*)p;
      return 0;
   }
   int thFnc(){
      audioMngr.cAI.init();
      audioMngr.cAI.record(this);
      Sleep(4000);
      audioMngr.cAI.stop(this);
      Sleep(2000);
      return 0;
   }
   
   static int iEntropyColectorCounter;
   
public:
   static void colect(){
      CTEntropyCollector *c=new CTEntropyCollector();
      c->start();
   }
   void audioIn(char *p, int iLen, unsigned int uiPos){
      add_tz_random(p,iLen);
   }
   
   
   static void onNewCall(){
      iEntropyColectorCounter=0;  
   }
   
   static void tryAddEntropy(char *p, int iLen){
      iEntropyColectorCounter++;
      if(iLen>10 && (iEntropyColectorCounter<100 || (iEntropyColectorCounter&127)==0)){
         if(iEntropyColectorCounter>100)
            add_tz_random(p,min(iLen,32));
         else
            add_tz_random(p,min(iLen,256));
      }
   }
   
};
#endif
