/*
Created by Janis Narbuts
Copyright © 2004-2012, Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC. All rights reserved.

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

#ifndef _C_T_DTMFPLAYER_H
#define _C_T_DTMFPLAYER_H

class CTDtmfPlayer: public CTAudioCallBack{
   int iInitOk;
   
   double fCtx,fCtx2;
   int iSamplesPlayed;
   int iPlaySilence;
   int iPlaySilenceSampleCnt;
   int iPrevRate;
   int iLast;
   unsigned int uiStopAT;
   int iEnabled;
public:
   CTAudioOut ao;
   int iPlayDtmf;
   CTDtmfPlayer():ao(16,8000){
      uiStopAT=0;
      iLast=0;
      iPrevRate=0;
      
      iPlayDtmf=0;
      iPlaySilenceSampleCnt=0;
      iInitOk=0;
      fCtx=fCtx2=0;
      iSamplesPlayed=0;
      iPlaySilence=0;
      iEnabled=0;
      ao.iIsUtilPlay=1;
   }
   void enable(int fEnable){
      iEnabled=fEnable;
      if(iEnabled)init();else if(iInitOk)ao.rel();
   }
   void audioIn(char *p, int iLen, unsigned int uiPos){
      if(!p)return;
      int t_native_PRate(void *ctx);
      if(iPlaySilence){
         /*
          iPlaySilenceSampleCnt-=(iLen/2);
          if(!iPlaySilence && iPlaySilenceSampleCnt<0){
          ao.stop();
          }
          */
         memset(p,0,iLen);
         iSamplesPlayed+=iLen/2;
         return;
      }
      int iRate=t_native_PRate(ao.ctx);
      if(iRate==0)iRate=44100;
      iPrevRate=iRate;
      
      /*
       1209 Hz	1336 Hz	1477 Hz	1633 Hz
       697 Hz	1	2	3	A
       770 Hz	4	5	6	B
       852 Hz	7	8	9	C
       941 Hz	*	0	#	D
       */
      
      
      float f2u,f1u;
      switch(iPlayDtmf){
         case 0:  f2u=937;f1u=1333;break;
         case 1:  f2u=692;f1u=1206;break;
         case 2:  f2u=692;f1u=1333;break;
         case 3:  f2u=692;f1u=1474;break;
         case 4:  f2u=768;f1u=1206;break;
         case 5:  f2u=768;f1u=1333;break;
         case 6:  f2u=768;f1u=1474;break;
         case 7:  f2u=851;f1u=1206;break;
         case 8:  f2u=851;f1u=1333;break;
         case 9:  f2u=851;f1u=1474;break;
         case 10: f2u=937;f1u=1206;break;
         default:
         case 11: f2u=937;f1u=1474;break;
      }
      
      createSineL<short>(fCtx,fCtx2,(short*)p,iLen/2,f1u, f2u, iRate, 400,iLast);
      if(iLast)iPlaySilence=1;
      
      iSamplesPlayed+=iLen/2;
   }
   void init(){
      if(!iEnabled)return;
      if(!iInitOk){
         iInitOk=2;
         ao.setIsUtilPlay(this);
         iInitOk=1;
      }
      /*
       play('1');
       Sleep(2000);
       stop();
       */
   }
   void play(int id){
      if(!iEnabled)return;
      if(!iInitOk){
         iInitOk=2;
         ao.setIsUtilPlay(this);
         iInitOk=1;
      }
      if(iInitOk==2)Sleep(50);
      iLast=0;
      iPlaySilence=0;
      if(id==' ')iPlaySilence=1;
      fCtx=fCtx2=0;
      if(id>='0' && id<='9'){
         id-='0';
         
      }
      else if(id=='*')id=10;
      else if(id=='#')id=11;
      uiStopAT=getTickCount()+8000;
      
      iPlayDtmf=id;
      iLast=0;
      ao.play();
      iSamplesPlayed=0;
      if(id!=' ')iPlaySilence=0;
   }
   void stop(){
       if(!iEnabled)return;
      
      for(int i=0;iSamplesPlayed*40<(4000+iPrevRate) && i<10;i++)
         Sleep(20);
      iLast=1;
      Sleep(20);
      iPlaySilence=1;
      if(iSamplesPlayed<10||(uiStopAT && uiStopAT<getTickCount())){uiStopAT=0;ao.stop();}
      //  iPlaySilenceSampleCnt=iPrevRate*4;
      ao.stopAfter(4000);//TODO use other thread
      
   }
   void stopz(){
      iPlaySilence=1;
      
   }
};

#endif
