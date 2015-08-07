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

#ifndef _C_T_CONFERENCE_H
#define _C_T_CONFERENCE_H
//#include ""

class CTConference{
   enum{eMaxCC=40, eMaxChToMix};
   CSesBase *s[eMaxCC];
   short dst[4096];
   short dstSilence[4096];
   short tmp[eMaxCC*4096];
   int strFlags[eMaxCC];
   int cnt;
public:
   CTConference(){cnt=0;for(int i=0;i<eMaxCC;i++){s[i]=NULL;}memset(dstSilence,0,sizeof(dstSilence));}
   int callsInConf(){return cnt;}
   
   void onAudioData(char *p, int iLen, unsigned int uiPos,int iIsVoice, int iMuteMe, int iWhisperingDetected){
      
      short *streams[eMaxChToMix];
      CTSesMediaBase *mbl[eMaxChToMix];
      int iWhisperingTo[eMaxChToMix];
      static int iDbg=0;
      iDbg++;
      CTAudioOutBase *cAO;
      int iSamples=iLen>>1;

      
      if(iMuteMe)memset(p,0,iLen);
      
      for(int i=0;i<eMaxChToMix;i++)mbl[i]=NULL;
      
      int iConfIsOnHold=0;

      int iStreamsFound=0;
      for(int i=0;i<eMaxCC;i++){
         CSesBase *cur=s[i];
         //TODO if !cur actvie rem from conf
         if(!cur || !cur->cs.iIsInConference || !cur->isMediaSession() || !cur->isSesActive()  || cur->cs.iCallStat!=CALL_STAT::EOk)continue;
         
         
         CTSesMediaBase *mb=cur->mBase;
         mbl[iStreamsFound]=mb;
         if(mbl[iStreamsFound]){
            
            cAO=mbl[iStreamsFound]->cAO;
            if(cAO){
               
               if(!iConfIsOnHold){
                  iConfIsOnHold=cur->cs.iIsOnHold;
               }
               
               iWhisperingTo[iStreamsFound] = cur->cs.iWhisperingTo;
               
               int iRate=cAO->getRate();
               int iRate4=iRate*4;
               int iRated2=iRate>>1;
               int iRated4=iRate>>2;
               int iLeft=0;
               int si=cAO->getLastPlayBuf(NULL,strFlags[i],iLeft);
               strFlags[i]++;
               
               //if(si<iSamples){printf("[be%d %d=%d]",i,si,iSamples);}else printf("[ok%d %d=%d]",i,si,iSamples); 
               if(si>iSamples*2+iRated4 || strFlags[i]>iRate4){
                  if(strFlags[i]<iRate4)strFlags[i]+=iRated2;//iSamples;
                  streams[iStreamsFound]=&tmp[i*4096];//
                  cAO->getLastPlayBuf(&tmp[i*4096],iSamples,iLeft);
                  
                  if((iDbg&255)==1)printf("[lb %d=%d]",iStreamsFound,iLeft);
               }
               else{
                  if((iDbg&255)==1)printf("[lb er %d=%d]",iStreamsFound,si);
                  streams[iStreamsFound]=&tmp[i*4096];//
                  memset(streams[iStreamsFound],0,2*iSamples);
               }
               iStreamsFound++;
               if(cnt==iStreamsFound)break;
            }
         }
      }
      
      if(iConfIsOnHold){
         iMuteMe=1;
      }
      
      int iSent=0;
      for(int i=0;i<iStreamsFound;i++){
         short *mix[eMaxCC+1];
         int n=0;
         
         
         for(int z=0;z<iStreamsFound;z++){
            if(mbl[i] && mbl[z] && mbl[i]!=mbl[z]){ //dont send back to src
               
               if(iWhisperingTo[z])continue;//dont forward a whispering voice
                  
               mix[n]=streams[z];  n++;
            }
         }
         int iMuteThis = iMuteMe || (iWhisperingDetected && !iWhisperingTo[i]);
         
         if(!iMuteThis || !n){
            if(iMuteThis)
               mix[n]=(short*)&dstSilence[0];
            else
               mix[n]=(short*)&p[0];
            n++;
         }
         
         if(!mbl[i])continue;
         iSent++;
         
         CTAudioUtils::mix<short,int>(n,mix,iSamples,32700,-32700,(short*)&dst[0],1);
         
         if(mbl[i]->isSesActive())mbl[i]->onSend((char*)&dst[0],iLen,CTSesMediaBase::eAudio,(void*)uiPos, iIsVoice);
      }
    //  printf("[cnt=%d,sf=%d,s=%d]",cnt,iStreamsFound,iSent);
   }
   
   int addCalls(int iAll, CSesBase *root, int iMax){
      int c=0;
      for(int i=0;i<iMax;i++){
         if(root[i].cs.iInUse && root[i].isMediaSession() && (root[i].cs.iIsInConference || iAll)){
            if(addCall(&root[i])<0)break; 
            c++;
         }
      }
      return c;
   }
   int addCall(CSesBase *c){
      for(int i=0;i<eMaxCC;i++){
         if(s[i]==c){   c->cs.iIsInConference=1; strFlags[i]=0;return 0;  }
      }
      for(int i=0;i<eMaxCC;i++){
         if( !s[i] || !s[i]->cs.iIsInConference){
            
              c->cs.iIsInConference=1; cnt++; s[i]=c; strFlags[i]=0; return 0;
         }
      }
      c->cs.iIsInConference=0;
      return -1;
   }
   void remCall(CSesBase *c){
      for(int i=0;i<eMaxCC;i++){
         if(c && s[i]==c){c->cs.iIsInConference=0; cnt--;s[i]=NULL;  break;}
      }
      if(c<0)puts("[err conf---------------]");
      
   }
   
   
};

#endif
