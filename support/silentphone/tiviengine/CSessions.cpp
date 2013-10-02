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

#include "CSessions.h"
#include "../encrypt/md5/md5.h"

int CSessionsBase::iRandomCounter=0;

const char CPhSesions::szTagChars[80]="0123456789qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNMabcdef";

int isVideoCall(int iCallID){
   CSesBase * s=g_getSesByCallID(iCallID);
   if(!s)return 0;
   CTSesMediaBase *m=s? s->mBase:NULL;
   return (m && m->getMediaType()&CTSesMediaBase::eVideo)?1:0;
}

int getMediaInfo(int iCallID, const char *key, char *p, int iMax){

   CSesBase * s=g_getSesByCallID(iCallID);
   if(!s)return 0;
   //if zrtp. //Take it from CTMediaIDS
   CTSesMediaBase *m=s?s->mBase:NULL;
   if(m){
      return m->getInfo(key,p,iMax);
   }
   return -2;
}

static int getMediaInfo(CSesBase *s, const char *key, char *p, int iMax){
   
   if(!s)return 0;
   //if zrtp. //Take it from CTMediaIDS
   CTSesMediaBase *m=s?s->mBase:NULL;
   if(m){
      return m->getInfo(key,p,iMax);
   }
   return -2;
}



int getCallInfo(int iCallID, const char *key, char *p, int iMax){
   CSesBase * s=g_getSesByCallID(iCallID);
   if(!s)return 0;
   
   if(strncmp(key,"media.",6)==0)return getMediaInfo(s,key+6,p,iMax);
   if(strncmp(key,"zrtp.",5)==0)return getMediaInfo(s,key+5,p,iMax);//?? why +5? rem and test
  // if(strncmp(key,"zrtp.",5)==0){CTZRTP *z=findSessionZRTP(s);if(z)return z->getInfoX(key+5, p, iMax);else return 0;}//test
   
   
   return s->getInfo(key, p, iMax);;
}

int getCallInfo(int iCallID, const char *key, int *v){
   
   char buf[16];
   int l=::getCallInfo(iCallID, key, buf,sizeof(buf)-1);
   if(l>0 && v){*v=atoi(buf);return 0;}
   return -1;
}



void CPhSesions::createCallId(CSesBase *spSes, int iIsReg)
{
   #define OUR_CALL_ID_LEN     (16)

   int i,y;
   unsigned int res[4];


   char *szTag=spSes->sSIPMsg.rawDataBuffer+spSes->sSIPMsg.uiOffset;
   spSes->sSIPMsg.dstrCallID.strVal=szTag;
   spSes->sSIPMsg.dstrCallID.uiLen=OUR_CALL_ID_LEN;
   spSes->sSIPMsg.uiOffset+=spSes->sSIPMsg.dstrCallID.uiLen;
   
   int sp=(int)spSes;
   
   CTMd5 md5;
   CTMd5 md5pwd;
   md5pwd.update((unsigned char *)"salt",4);
   md5pwd.update((unsigned char *)&p_cfg.user.pwd[0], strlen(p_cfg.user.pwd));
   unsigned int pwd_res = md5pwd.final();
   
   
   int iDevIDLen=0;
#if defined(ANDROID_NDK) || defined(__APPLE__)  
   const char *t_getDevID(int &l);
   //TODO save random token when app starts 1st time and use it
   const char *p = t_getDevID(iDevIDLen);
   if(iDevIDLen>0)
      md5.update((unsigned char *)p,iDevIDLen);
#endif   
   
   if(iDevIDLen<=0)
      md5.update((unsigned char *)&uiRegistarationCallIdRandom,sizeof(uiRegistarationCallIdRandom));
   
   md5.update((unsigned char *)&p_cfg.user.un[0] , strlen(p_cfg.user.un));
   ////if user has changed password then call-id should be different 
   md5.update((unsigned char *)&pwd_res, 1);//must be 1 byte
   md5.update((unsigned char *)&p_cfg.user.nr[0] , strlen(p_cfg.user.nr));
   if(!iIsReg){
      md5.update((unsigned char *)&iRandomCounter, 4);
      md5.update((unsigned char *)&extAddr.ip, 4);
      md5.update((unsigned char *)&ipBinded, 4);
      md5.update((unsigned char *)&sp, 4);
      md5.update((unsigned char *)&p_cfg.iNet, 4);
      md5.update((unsigned char *)&uiGT, sizeof(uiGT));
   }
   md5.final((unsigned char*)&res[0]);
   
   
   
   iRandomCounter+=(int)uiGT;
   iRandomCounter+=(int)res[3];


   for(i=0;i<OUR_CALL_ID_LEN/3;i++)
   { 
      y=res[0]&63;//TAG_CHAR_CNT;
      res[0]>>=6;
      szTag[i]=szTagChars[y];
   }
   for(;i<OUR_CALL_ID_LEN/3*2;i++)
   {
      y=res[1]&63;//%TAG_CHAR_CNT;
      szTag[i]=szTagChars[y];
      res[1]>>=6;
   }
   for(;i<OUR_CALL_ID_LEN;i++)
   {
      y=res[2]&63;//%TAG_CHAR_CNT;
      szTag[i]=szTagChars[y];
      res[2]>>=6;
   }

}

void CPhSesions::createTag(CSesBase *spSes)
{

   unsigned int i,x,y;
#define OUR_TAG_LEN     4

   iRandomCounter+=70000007;
   x=(int)spSes;
   
   x*=(int)getTickCount();
   x+=iRandomCounter;

   for(i=0;i<OUR_TAG_LEN;i++)
   {
      y=x&63;
      spSes->str16Tag.strVal[i]=szTagChars[y];
      x>>=6;
   }
   spSes->str16Tag.uiLen=OUR_TAG_LEN;
}

void CPhSesions::handleSessions()
{
   CSesBase *spSes=NULL;;
   int i;

   LOCK_MUTEX_SES
   for (i=0;i<iMaxSesions;i++)
   {
      if(pSessionArray[i].cs.iBusy==FALSE)continue;
      spSes=&pSessionArray[i];
      if(spSes->mBase && 
         (spSes->cs.iCallStat==CALL_STAT::EInit || spSes->cs.iCallStat==CALL_STAT::EOk)
         )
      {
         spSes->mBase->onTimer();
      }


      if(spSes->uiClearMemAt && spSes->uiClearMemAt<uiGT)
      {
         onKillSes(spSes,0,NULL,spSes->cs.iSendS);

      }
      else
      {
         if(spSes->cs.iWaitS && (spSes->cs.iSendS || spSes->cs.iCallStat==CALL_STAT::ESendError || spSes->cs.iWaitS==METHOD_ACK))
         {
            if(spSes->sSendTo.iRetransmitions<0)
            {
               onKillSes(spSes, -1, NULL, spSes->cs.iSendS);
            }
            else if(spSes->sSendTo.uiNextRetransmit>0 && spSes->sSendTo.uiNextRetransmit<uiGT)
            {
               if(spSes->sSendTo.iRetransmitions>0)
                  sendSip(sockSip,spSes);
               else
                  spSes->sSendTo.updateRetransmit(uiGT);
            }
         }
      }

   }
   UNLOCK_MUTEX_SES
}


CTAudioOutBase *findAOByMB(CTSesMediaBase *mb);

CSesBase *CPhSesions::findSessionByZRTP(CTZRTP *z){
   CTSesMediaBase *mb;
   CSesBase *spSes;
   
   if(!z)return NULL;
   //z->pSes==spSes
   
   for(int i=0;i<iMaxSesions;i++)
   {
      spSes=&pSessionArray[i];
      if(!spSes->cs.iBusy || spSes->cs.iCallStat>spSes->cs.EOk)continue;
      mb=spSes->mBase;
      CTMediaIDS *mids=spSes->pMediaIDS;
      if(!spSes->isSession() || !mb || !mids)continue;
//      if(z==mb->getZRTP() && z->pSes==spSes )return spSes;
      if(z==mids->pzrtp && z->pSes==spSes )return spSes;
      
   }
   return NULL;
}

CTZRTP *CPhSesions::findSessionZRTP(CSesBase *ses){
   CTSesMediaBase *mb;
   CSesBase *spSes;
   CTZRTP *z=NULL;
   
   for(int i=0;i<iMaxSesions;i++)
   {
      spSes=&pSessionArray[i];
      if(spSes!=ses || !spSes->cs.iBusy || spSes->cs.iCallStat>spSes->cs.EOk)continue;
      mb=spSes->mBase;
      CTMediaIDS *mids=spSes->pMediaIDS;
      if(!spSes->isSession() || !mb || !mids)break;
      z=mids->pzrtp;
      if(z && z->pSes==spSes){
         return z;
      }
      break;
   }
   return NULL;
}


CTAudioOutBase *CPhSesions::findSessionAO(CSesBase *ses){

   CTSesMediaBase *mb;
   
   CSesBase *spSes;
   
   if(!ses)return NULL;
   
   for(int i=0;i<iMaxSesions;i++)
   {
      spSes=&pSessionArray[i];
      if(!spSes->cs.iBusy || spSes!=ses)continue;
      mb=spSes->mBase;
      if(!spSes->isSession() || !mb)break;
      
      return findAOByMB(mb);
      
   }
   return NULL;
   
}

void CPhSesions::onDataSend(char *buf, int iLen, unsigned int uiPos, int iDataType, int iIsVoice)
{
    int i;
    if(bRun!=1)return;
    CSesBase *spSes;
#define dd_log //

    if(iLen>3 && buf){iRandomCounter*=((int)buf[1]+1000); iRandomCounter+=(9999+(int)buf[0])*29; iRandomCounter+=buf[2];}

    CTSesMediaBase *mb;
   
   for(i=0;i<iMaxSesions;i++)
   {
      spSes=&pSessionArray[i];
      if(!spSes->cs.iBusy ||
         (iDataType == CTSesMediaBase::eAudio && spSes->cs.iIsInConference) ||
         spSes->cs.iCallStat!=CALL_STAT::EOk)continue ;
      
      mb=spSes->mBase;
      
      if(!mb || !spSes->isSession() || !spSes->isSesActive()  )continue;
      
      if(mb->isSesActive())mb->onSend(buf,iLen,iDataType,(void*)uiPos, iIsVoice);
   }
}

int makeSDP(CPhSesions &ph, CSesBase *spSes, CMakeSip &ms)
{

   STR_64 *vis=spSes->pIPVisble;
   int fUseExtPort=1;


   if(!spSes->mBase)return -1;//TODO ???

   int iTrue=(
      (spSes->mBase->uiIPConn && ph.extAddr.ip==spSes->mBase->uiIPConn) ||
      (spSes->mBase->uiIPConn && isNatIP(spSes->mBase->uiIPConn) && isNatIP(ph.ipBinded) &&
      (spSes->mBase->uiIPConn&0xffff)==((unsigned int)ph.ipBinded&0xffff)) ||
      (spSes->mBase->uiIPConn==0 && isNatIP(ph.ipBinded) && isNatIP(spSes->dstConAddr.ip))
      );
   /*
   if(sip && iTrue)
   {
      iTrue=iTrue && 
   }
   */
   if(iTrue && spSes->iDestIsNotInSameNet){
      iTrue=0;
   }

   if(iTrue)
   {
      //|| (spSes->mBase->uiIPConn==0 && natip))
      fUseExtPort=0;
      vis=&ph.str64BindedAddr;
   }
   else
   {
      if(spSes->cs.iSendS==METHOD_ACK)return 0;
      fUseExtPort=0;
//#ifdef USE_STUN
      if(ph.p_cfg.iUseStun &&  ph.p_cfg.iNet && (ph.p_cfg.iNet & (CTStun::FULL_CONE|CTStun::PORT_REST)))
      {
         fUseExtPort=1;
      }
//#endif
   }
   ms.makeSdpHdr(ph.str64BindedAddr.strVal,ph.str64BindedAddr.uiLen);


   if(ph.p_cfg.iUseStun)
      ms.addSdpConnectIP(vis->strVal,(int)vis->uiLen,&ph.p_cfg);
   else 
      ms.addSdpConnectIP(ph.str64BindedAddr.strVal,(int)ph.str64BindedAddr.uiLen,&ph.p_cfg);
   
   ms.makeSDPMedia(*spSes->mBase, ph.p_cfg.iUseStun &&  ph.p_cfg.iUseOnlyNatIp==0);// && fUseExtPort);
   
   return 0;
}
int CPhSesions::send200Sdp(CSesBase *spSes, SIP_MSG *sMsg)
{
   spSes->cs.iSendS=0;
   spSes->cs.iWaitS=METHOD_ACK;
   spSes->sSendTo.setRetransmit();


   CMakeSip ms(sockSip,spSes,sMsg);
   ms.makeResp(200,&p_cfg);
   makeSDP(*this,spSes,ms);
   ms.addContent();
   
   spSes->sSendTo.updateRetransmit(uiGT);

   if(sMsg && sMsg->hdrCSeq.uiMethodID!=METHOD_INVITE){
      spSes->cs.iWaitS=0;
      spSes->sSendTo.stopRetransmit();    
   }
   return sendSip(sockSip,spSes);
}

void CPhSesions::notifyIncomingCall(CSesBase *spSes){
   
   if(spSes->iOnIncmingCalled || spSes->cs.iCaller)return;
   spSes->iOnIncmingCalled=1;
   
   SIP_MSG *sMsg = &spSes->sSIPMsg;
   
   DSTR *dstr=&sMsg->hdrFrom.sipUri.dstrSipAddr;
   if(p_cfg.iHideIP && sMsg->hdrFrom.sipUri.dstrUserName.uiLen){
      dstr=&sMsg->hdrFrom.sipUri.dstrUserName;
   }
   cPhoneCallback->onIncomCall(&spSes->dstConAddr,dstr->strVal,dstr->uiLen ,(int)spSes);
}

int CPhSesions::onSipMsgSes(CSesBase *spSes, SIP_MSG *sMsg)
{
   int iIsReq=sMsg->sipHdr.uiMethodID;
   int iMeth=sMsg->hdrCSeq.uiMethodID;
   

   if(sMsg->sipHdr.dstrStatusCode.uiVal>299 && sMsg->sipHdr.dstrStatusCode.uiVal!=491)//491 req pending
   {

      CMakeSip ms(sockSip,spSes,sMsg);

      if(sMsg->sipHdr.dstrStatusCode.uiVal<400)
      {
         ms.makeReq(iMeth,&p_cfg);
         if(iMeth==METHOD_INVITE)
         {
            makeSDP(*this,spSes,ms);
         }
         /*
         if(sMsg->sipHdr.dstrStatusCode.uiVal==305)//use px
         {
            if(sMsg->hldContact.x[0].sipUri.dstrHost.uiVal)
               spSes->dstConAddr.ip=sMsg->hldContact.x[0].sipUri.dstrHost.uiVal;
            spSes->dstConAddr.setPort(sMsg->hldContact.x[0].sipUri.dstrPort.uiVal);

         }
          */
         ms.addContent();
         sendSip(sockSip,spSes);
         return 0;

      }
      CTSesMediaBase *mb=spSes->mBase;
      if(sMsg->sipHdr.dstrStatusCode.uiVal!=481)
      {
         if(sMsg->sipHdr.dstrStatusCode.uiVal==400 && 
            //spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.uiLen==0 &&
            mb && 
            mb->getMediaType() & CTSesMediaBase::eVideo
           )
         {
            int iStart=mb->iIsActive;
            if(mb)
            {
              spSes->setMBase(NULL);
              cPhoneCallback->mediaFinder->release(mb);
            }
            spSes->setMBase(cPhoneCallback->tryGetMedia("audio"));//TODO find bysdp
            if(spSes->mBase)
            {
               
               ms.makeReq(iMeth,&p_cfg);
               if(iStart)spSes->mBase->onStart();
               makeSDP(*this,spSes,ms);
               ms.addContent();
               sendSip(sockSip,spSes);

               return 0;
            }
            
         }
         ms.makeReq(METHOD_ACK,&p_cfg);
         ms.addContent();
         sendSip(sockSip,spSes);
      }
      if(iMeth==METHOD_INVITE)
      {
         onKillSes(spSes,-2,sMsg,iMeth);
      }


      return 0;
   }
   
  // spSes->
   
   if(spSes->cs.iSendS==METHOD_INVITE && sMsg->sipHdr.dstrStatusCode.uiVal==0 && iMeth==METHOD_INVITE &&  spSes->cs.iCallStat==CALL_STAT::EInit){
      /*
       //we should not start a call if we recveive reinvite in init state
      keepAlive.sendEverySec(0);
      spSes->cs.iCallStat=CALL_STAT::EOk;
      
      notifyIncomingCall(spSes);
      
      spSes->mBase->onStart();
      cPhoneCallback->onStartCall((int)spSes);
      send200Sdp(spSes,sMsg);
       */
      CMakeSip ms(sockSip,491,sMsg);
      
      return 0;//???
   }
   
   if(spSes->cs.iCallStat==CALL_STAT::EEnding && iMeth==METHOD_INVITE)
   {
      if(iIsReq)
         CMakeSip ms(sockSip,603,sMsg);
      else
      {

         CMakeSip ms(sockSip, spSes);
         ms.makeReq(METHOD_BYE,&p_cfg);
         ms.addContent();
         sendSip(sockSip,spSes);
      }
      return 0;
   }
   /*
    A UAS that receives an INVITE on a dialog while an INVITE it had sent
    on that dialog is in progress MUST return a 491 (Request Pending)
    response to the received INVITE.
    */
   if(iIsReq && iMeth==METHOD_INVITE && !spSes->cs.iCaller && spSes->iSendingReinvite){
      CMakeSip ms(sockSip,491,sMsg);
      return 0;
   }

   int iSdpRet = 0;
   
   if(!spSes->sSIPMsg.hdrCSeq.dstrID.uiVal ||
      spSes->sSIPMsg.hdrCSeq.dstrID.uiVal!=sMsg->hdrCSeq.dstrID.uiVal ||
      spSes->sSIPMsg.hdrCSeq.uiMethodID!=sMsg->hdrCSeq.uiMethodID ||
      spSes->sSIPMsg.sipHdr.dstrStatusCode.uiVal!=sMsg->sipHdr.dstrStatusCode.uiVal){
      
      iSdpRet = sdpRec(sMsg,spSes);
      printf("[SDP ret %d]",iSdpRet);
      
      if(iSdpRet<0)return -1;
      
      if(spSes->mBase && iSdpRet==1 && iMeth != METHOD_INVITE){
         cPhoneCallback->onSdpMedia((int)spSes,spSes->mBase->getMediaName());
      }
   }

   

   switch(iMeth)
   {
   case METHOD_UPDATE:
         if(iIsReq){

            if(spSes->cs.iCallStat==CALL_STAT::EOk){
               send200Sdp(spSes,sMsg);
            }
            else {
               CMakeSip ms(sockSip,200,sMsg);
            }
         }
         else if(spSes->cs.iSendS==METHOD_UPDATE){
            spSes->sSendTo.stopRetransmit();
            spSes->cs.iSendS=0;
            spSes->cs.iWaitS=0;
         }
         break;
         
   case METHOD_INFO:
      if(iIsReq)
      {
         CMakeSip ms(sockSip,200,sMsg);
      }
      break;
   case METHOD_ACK:
      if (spSes->cs.iCallStat==CALL_STAT::ESendError && spSes->cs.iWaitS==METHOD_ACK)
      {
         onKillSes(spSes,0,NULL,0);
         return 0;
      }
      if (spSes->cs.iWaitS!=METHOD_ACK) 
      {
         DEBUG_T(0, "REc METHOD_ACK without waiting");
         return -1;
      }
      if (sMsg->sipHdr.uiMethodID!=METHOD_ACK)DEBUG_T(0, "sip hdr not ack");
      spSes->cs.iSendS=0;
      spSes->cs.iWaitS=0;
      spSes->sSendTo.stopRetransmit();
         
    //  spSes->dstConAddr=sMsg->addrSipMsgRec;
      break;

   case METHOD_BYE:
   case METHOD_CANCEL:
      if(iIsReq)
      {

         spSes->sSendTo.stopRetransmit();
         CMakeSip ms1(sockSip,200,sMsg);
         if(iMeth==METHOD_CANCEL){
            CMakeSip ms(sockSip,spSes,&spSes->sSIPMsg);
            ms.makeResp(487,&p_cfg);
            ms.addContent();
            sendSip(sockSip,spSes);
         }

         
      }

      onKillSes(spSes,0,NULL,0);

      return 0;
   case METHOD_INVITE:
      spSes->iSendingReinvite=0;

      //TODO new get contact
      spSes->dstConAddr=sMsg->addrSipMsgRec;


      if (spSes->sSIPMsg.hdrCSeq.uiMethodID==0 || spSes->sSIPMsg.hdrCSeq.dstrID.uiVal!=sMsg->hdrCSeq.dstrID.uiVal
           
           //if contact addr does not match save it
          ||  (sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen &&
            (sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen!=spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.uiLen ||
            memcmp(sMsg->hldContact.x[0].sipUri.dstrSipAddr.strVal, spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.strVal, sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen)))
           
           || (spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.uiLen==0 && sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen)
          ||(spSes->sSIPMsg.hdrCSeq.uiMethodID!=sMsg->hdrCSeq.uiMethodID && sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen)
          ||(spSes->sSIPMsg.hldRecRoute.uiCount!=sMsg->hldRecRoute.uiCount
             || spSes->sSIPMsg.hldRecRoute.x[0].dstrFullRow.uiLen!=sMsg->hldRecRoute.x[0].dstrFullRow.uiLen)
          )
      {
         memset(&spSes->sSIPMsg,0,sizeof(SIP_MSG));
         //parsing again, because we use pointers in spSes->sSIPMsg.rawDataBuffer
         cSip.parseSip(sMsg->rawDataBuffer, (int)(sMsg->uiOffset-MSG_BUFFER_TAIL_SIZE), &spSes->sSIPMsg);
         
      }
         
      switch(sMsg->sipHdr.dstrStatusCode.uiVal)
      {
         case 0:
            {
               if(spSes->cs.iCallStat==CALL_STAT::EOk)
               {
                  send200Sdp(spSes,sMsg);
                  break;
               }
               
               CPY_DSTR(spSes->dstSipAddr,sMsg->hdrFrom.sipUri.dstrSipAddr,120);
               
               spSes->cs.iSendS=0;
               spSes->cs.iWaitS=0;
               
               spSes->sSendTo.stopRetransmit();
               if(p_cfg.iAutoAnswer)
               {
                  keepAlive.sendEverySec(0);
                  spSes->cs.iCallStat=CALL_STAT::EOk;
                  
                  notifyIncomingCall(spSes);
                  
                  spSes->mBase->onStart();
                  cPhoneCallback->onStartCall((int)spSes);
                  
                  send200Sdp(spSes,sMsg);
                  break;
               }
               keepAlive.sendEverySec(1);
               
               CMakeSip ms(sockSip,spSes,sMsg);//TODO use new
               ms.makeResp(180,&p_cfg);
               ms.addContent();
               sendSip(sockSip,spSes);
               
            }
            if(spSes->cs.iCallSubStat!=CALL_STAT::EWaitUserAnswer)
            {
               spSes->cs.iCallSubStat=CALL_STAT::EWaitUserAnswer;
               
               notifyIncomingCall(spSes);
               
               spSes->uiSend480AfterTicks=50;//sends sip 480 after ~1min

            }

            break;
         case 200:
            {
               spSes->iSendingReinvite = 0;
               keepAlive.sendEverySec(0);
               
               spSes->sSendTo.stopRetransmit();
               
               spSes->cs.iSendS=METHOD_ACK;
               spSes->cs.iWaitS=0;
               
               CMakeSip ms(sockSip,spSes,sMsg);
               ms.makeReq(METHOD_ACK,&p_cfg);
               ms.addContent();
               sendSip(sockSip,spSes);
               
               if(spSes->cs.iCallStat!=CALL_STAT::EOk)
               {
                  spSes->cs.iCallStat=CALL_STAT::EOk;
                  cPhoneCallback->onStartCall((int)spSes);
               }
               if(spSes->mBase)spSes->mBase->onRecMsg(200);
            }
            break;
         case 491:
            //TODO inc retransmit timer
            {
               CMakeSip ms(sockSip,spSes,sMsg);
               ms.makeReq(METHOD_ACK,&p_cfg);
               ms.addContent();
               sendSip(sockSip,spSes);
            }
         case 100:
         case 180:
         case 183:
            if(sMsg->sipHdr.dstrStatusCode.uiVal==183 ){
               cPhoneCallback->onRecMsg(183,(int)spSes,NULL,0);
            }
            else if(sMsg->sipHdr.dstrStatusCode.uiVal==180 )//TODO && no sdp
            {
               if(spSes->mBase)spSes->mBase->onRecMsg(180);
               cPhoneCallback->onRecMsg(180,(int)spSes,NULL,0);
            }
            else{
               cPhoneCallback->onRecMsg((int)sMsg->sipHdr.dstrStatusCode.uiVal,(int)spSes,sMsg->sipHdr.dstrReasonPhrase.strVal,(int)sMsg->sipHdr.dstrReasonPhrase.uiLen);
            }
         default:
            if((sMsg->sipHdr.dstrStatusCode.uiVal>=100 && sMsg->sipHdr.dstrStatusCode.uiVal<200) || (sMsg->sipHdr.dstrStatusCode.uiVal==491))
            {
               spSes->cs.iSendS=0;
               spSes->cs.iWaitS=sMsg->sipHdr.dstrStatusCode.uiVal==491?0:200;
               spSes->sSendTo.stopRetransmit();
            }
            else{
               cPhoneCallback->onRecMsg((int)sMsg->sipHdr.dstrStatusCode.uiVal,(int)spSes,sMsg->sipHdr.dstrReasonPhrase.strVal,(int)sMsg->sipHdr.dstrReasonPhrase.uiLen);
            }
      } 
         if(spSes->mBase && iSdpRet==1 && iMeth == METHOD_INVITE){
            cPhoneCallback->onSdpMedia((int)spSes,spSes->mBase->getMediaName());
         }

      break;
   default:
      return -1;
   }


   return 0;
}
int CPhSesions::onSipMsg(CSesBase *spSes, SIP_MSG *sMsg)
{
   int iMeth=sMsg->hdrCSeq.uiMethodID;
   int iSipCode=sMsg->sipHdr.dstrStatusCode.uiVal;

   if (iMeth & (METHOD_MESSAGE|METHOD_OPTIONS|METHOD_SUBSCRIBE|METHOD_PUBLISH))
   {

      if(iSipCode!=100){
         setStopReason(spSes, iSipCode, NULL, &sMsg->sipHdr.dstrReasonPhrase);
      }

      SEND_TO *st=&spSes->sSendTo;
      if(iSipCode>299 && iMeth==METHOD_MESSAGE && iSipCode<400 
         && sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen 
         &&  st->pContentType && st->pContent && st->iContentLen)
      {
         int i;
         st=new SEND_TO;
         //varbuut sesijaam pashaam sadaliities
         //taisiit memcpy
         //TODO pazinjot sho galvenajam
         //var lietot arii memmove
         memcpy(st,&spSes->sSendTo,sizeof(SEND_TO));
         st->pContent-=((int)&spSes->sSendTo-(int)st);

         for(i=0;i<(int)sMsg->hldContact.uiCount;i++)
         {
            CMakeSip ms(sockSip,spSes,sMsg);
            ms.iContactId=i;
            ms.makeReq(iMeth,&p_cfg);
            ms.addContent(st->pContentType, st->pContent, st->iContentLen);
            //
            spSes->sSendTo.iRetransmitPeriod=st->iRetransmitPeriod;
            spSes->sSendTo.iRetransmitions=st->iRetransmitions;
            sendSip(sockSip,spSes);
         }
         delete st;
         return 0;
      }
      spSes->cs.iWaitS=0;
      if (iSipCode==100)
      {
         spSes->sSendTo.stopRetransmit();
         return 0;
      }
      if(iSipCode>299 && iMeth==METHOD_MESSAGE)
      {

         onKillSes(spSes,-2,sMsg,METHOD_MESSAGE);

      }
      if(iSipCode>299 && iMeth==METHOD_SUBSCRIBE)
      {
         onKillSes(spSes,-2,sMsg,METHOD_SUBSCRIBE);
      }
      //if 200 set new contact info
      if(iMeth==METHOD_OPTIONS)
         iOptionsSent=2;
      freeSes(spSes);
      return 0;
   }
   if (iMeth==METHOD_REGISTER)
   {
      if (iSipCode==100)
      {
         spSes->cs.iWaitS=0;
         return 0;
      }
      if (spSes->cs.iSendS!=METHOD_REGISTER)// || spSes->cs.iWaitS!=200)
         return -1;
      if (iSipCode==200)
      {
         unsigned int i;
         
         if(!p_cfg.reg.bUnReg)
         {
            
            int uiExpTime=p_cfg.uiExpires;
            int ok=0;
            int b=0;         
            spSes->cs.iWaitS=0;
            if (sMsg->dstrExpires.uiVal>0)
               uiExpTime=sMsg->dstrExpires.uiVal;
            else 
            {
               ok=1;
               
               for(i=0;i<sMsg->hldContact.uiCount;i++)
               {
                  if (sMsg->hldContact.x[i].dstrExpires.uiVal>0)
                     b=sMsg->hldContact.x[i].dstrExpires.uiVal;
                  
               //   printf("[hosts (%.*s)(%.*s) %d]",spSes->pIPVisble->uiLen,spSes->pIPVisble->strVal, sMsg->hldContact.x[i].sipUri.dstrHost.uiLen, sMsg->hldContact.x[i].sipUri.dstrHost.strVal,spSes->uiUserVisibleSipPort==sMsg->hldContact.x[i].sipUri.dstrPort.uiVal);
                  
#define IS_EQUAL_DSTR(a,b) ((a).uiLen==(b).uiLen && memcmp((a).strVal,(b).strVal,(b).uiLen)==0)
                  if(IS_EQUAL_DSTR(sMsg->hldContact.x[i].sipUri.dstrHost,
                                   *spSes->pIPVisble) && 
                     spSes->uiUserVisibleSipPort==sMsg->hldContact.x[i].sipUri.dstrPort.uiVal)
                  {
                     if (sMsg->hldContact.x[i].dstrExpires.uiVal>0)
                     {
                        ok=2;
                        uiExpTime=b;//sMsg->hldContact.x[i].dstrExpires.uiVal;
                        DEBUG_T(0,"reg-cont-found =ok")
                     }
                     break;
                  }
               }
            }
            if(ok==1 && b)
               uiExpTime=b;
            
            p_cfg.iReRegisterNow=0;
            
            uiNextRegTry = uiGT+((uiExpTime*10*T_GT_SECOND)>>4);
            p_cfg.reg.uiRegUntil=uiGT+(uiExpTime*15*T_GT_SECOND>>4);
            printf("[exp %d,%d,%lld,%d %s]",uiExpTime,b,(p_cfg.reg.uiRegUntil-uiGT)/T_GT_SECOND,p_cfg.uiExpires, p_cfg.str32GWaddr.strVal);
            p_cfg.iCanRegister=1;
            iRegTryParam=0;
            reinviteMonitor.onOnline(&uiGT);
            
            cPhoneCallback->registrationInfo(&strings.lRegSucc,cPhoneCallback->eOnline);
            
            //TODO if(prev_contact_addr!=curr_contact_addr)reInvite(); here
            int _TODO_if_contact_addr_changed_reinvite_here;
         }
         else
         {
            iRegTryParam=0;
            //TODO rem addr
            CTEditBuf<16>  b;
            b.setText("---Offline---");
            cPhoneCallback->registrationInfo(&b, cPhoneCallback->eOffline);
            
            if(p_cfg.iUserRegister){
               uiNextRegTry=uiGT+T_GT_SECOND*10;//10sec
            }
            
            p_cfg.reg.uiRegUntil=0;
            if(p_cfg.changeUserParams.iSeq==2)
               p_cfg.changeUserParams.iSeq=3;
         }
         p_cfg.reg.bReReg=0;
         p_cfg.reg.bRegistring=0;
         p_cfg.reg.bUnReg=0;
         if(!p_cfg.iUserRegister)uiNextRegTry=0;
         
         
         spSes->cs.iWaitS=0;
         freeSes(spSes);
         return 0;
      }
      if (iSipCode>200)
      {
         spSes->cs.iWaitS=0;
         CMakeSip ms(sockSip,spSes,sMsg);
         ms.makeReq(METHOD_ACK,&p_cfg);
         ms.addContent();

         sendSip(sockSip,spSes);
         onKillSes(spSes,-2,sMsg,METHOD_REGISTER);

         return 0;
      }
      else
      {
         spSes->cs.iWaitS=0;
      }
   }

   return 0;
}
//int parseSDP(SDP *pRecSDP, char *pStart, int iLen);
int CPhSesions::sdpRec(SIP_MSG *sMsg,CSesBase *spSes)
{
   int ret=0;
   if (sMsg->dstrContLen.uiVal==0 || spSes->uiClearMemAt ||
       sMsg->hdrContType.uiTypeID!=CONTENT_TYPE_APPLICATION || 
      !(CMP(sMsg->hdrContType.dstrSubType,"SDP",3)))
      return 0;
   
   int iIsReq=sMsg->sipHdr.uiMethodID;
   
//should i reset use or reset diferent mBase if sdp is diferent ? -reinvite with diferent sdp
   if(spSes->mBase)
   {
      int iPrevIsAudioOnly=spSes->mBase->getMediaType()==spSes->mBase->eAudio;
      //TODO parse SDP once, spSes->mBase->onSdp(sdp);
      int not_ok=spSes->mBase->onSdp(sMsg->rawDataBuffer+sMsg->uiBytesParsed+1,(int) sMsg->dstrContLen.uiVal, iIsReq, 0);

      //TODO error codes
      CTSesMediaBase *mb=NULL;
      if(not_ok)//mainaas sdp media tips 
      {
         int iStart=spSes->mBase->iIsActive;
         
         mb=spSes->mBase;
         if(mb)
         {
            spSes->setMBase(NULL);
         }
         int iForce=0;
         if(spSes->cs.iIsOnHold && not_ok==-100 && iPrevIsAudioOnly){
            iForce=1;
            spSes->iReceivedVideoSDP=1;//why is not working
         }
         
         int v=iPrevIsAudioOnly && !iForce;
         if(!v)iForce=1;
         
         CTSesMediaBase *nb=cPhoneCallback->tryGetMedia(v?"video":"audio");
         
//TODO find by sdp
         if(!nb && iPrevIsAudioOnly){nb=cPhoneCallback->tryGetMedia("audio");iForce=1;}
         spSes->setMBase(nb);//TODO find bysdp
         if(spSes->mBase)
         {

            not_ok=spSes->mBase->onSdp(sMsg->rawDataBuffer+sMsg->uiBytesParsed+1,(int) sMsg->dstrContLen.uiVal, iIsReq, iForce);
            if(not_ok)
            {
               CTSesMediaBase *mbx=spSes->mBase;
               if(mbx)
               {
                  spSes->setMBase(NULL);
                  cPhoneCallback->mediaFinder->release(mbx);
               }
            }
            else if(iStart)spSes->mBase->onStart(); 
         }
      }
      
      if(!not_ok && spSes->mBase){
         ret=1;
      }
      
      if(mb)cPhoneCallback->mediaFinder->release(mb);


      if(not_ok)
      {
         ret = not_ok;
         if(spSes->sSIPMsg.uiOffset)
         {
            spSes->cs.iSendS=415;
            spSes->cs.iWaitS=METHOD_ACK;

            spSes->cs.iCallStat=CALL_STAT::ESendError;

            CMakeSip ms(sockSip,spSes,sMsg);
            ms.makeResp(spSes->cs.iSendS,&p_cfg);
            ms.addContent();
            sendSip(sockSip,spSes);
         }
         else
         {
            CMakeSip ms(sockSip);
            ms.cPhoneCallback=cPhoneCallback;
            ms.sendResp(sockSip,415,sMsg);
            onKillSes(spSes,0,NULL,0);
         }
      }
   }
   return ret;
}


void CPhSesions::setStopReason(CSesBase *spSes, int code, const char *msg, DSTR *dstr, CTEditBase *e){
   
   if(spSes->iStopReasonSet)return;
   spSes->iStopReasonSet=1;
   
   
   if(spSes->retMsg){
      if(dstr)spSes->retMsg->setText(dstr->strVal, dstr->uiLen);
      else if(e)spSes->retMsg->setText(*e);
      else if(msg)spSes->retMsg->setText(msg);
   }
   
   if(spSes->ptrResp)*spSes->ptrResp=code;
}


void CPhSesions::onKillSes(CSesBase *spSes, int iReason, SIP_MSG *sMsg ,int iMeth)
{
   if(!spSes || !spSes->cs.iBusy)return;
   
   int iCanClearNow=1;
   
   if(spSes->iKillCalled==0)
   {
      spSes->iKillCalled=1;
      
      setStopReason(spSes, spSes->iRespReceived==0 ? -1 : -2, NULL);
      
      if(spSes->isSession())//TODO
      {
         keepAlive.sendEverySec(0);
         if(spSes->iSessionStopedByCaller==0 && spSes->sSIPMsg.uiOffset)
         {
            //tivi_log("kill s 2");
            cPhoneCallback->onRecMsg(METHOD_BYE,(int)spSes,NULL,0);
            if(spSes->mBase)
            {
               spSes->mBase->onRecMsg(METHOD_BYE);
            }
            cPhoneCallback->info(&strings.lCallEnded,0,(int)spSes);
         }
         iCanClearNow=0;
      }
      spSes->stopMediaIDS();
      
      CTSesMediaBase *mb=spSes->mBase;
      if(mb)
      {
         iCanClearNow=0;
         //mb->onStop();
         spSes->mBase=NULL;
         cPhoneCallback->mediaFinder->release(mb);
      }
      
      
      int _TODO_SUCRIBE_PUBLISH_KILL_STATUS;
      if(spSes->cs.iSendS==METHOD_SUBSCRIBE)
      {
         CTEditBuf<150> buf;
         buf.setText("SUBSCRIBE ERROR: ");
         if(sMsg && sMsg->sipHdr.dstrFullRow.strVal)buf.addText(sMsg->sipHdr.dstrFullRow.strVal,(int)sMsg->sipHdr.dstrFullRow.uiLen);
         else buf.addText(strings.lConTimeOut);
         cPhoneCallback->info(&buf,*(int *)"BERR",(int)spSes);
      }
      else if(spSes->cs.iSendS==METHOD_OPTIONS)
      {
         
         //TODO if last received is long time ago
         //TODO reCreate if this is not first sock
         /*
          if(sockSip.isTCP()){
          sockSip.reCreate();
          puts("options: reCreate tcp ?? ");
          }
          */
         //iOptionsSent=0;;//(int)mBase;//iIsSession;
         //Try different sock type
         //TODO check send state
      }
      else
         if(iReason && spSes->cs.iCallStat==CALL_STAT::EInit)
         {
            
            int iUpdate=1;
            CTEditBuf<150> buf;
            CTStrBase *pErrStr=NULL;
            int iAddReasonPh=0;
            CTEditBase strReason(256);
            strReason.setText(strings.lError);
            switch(iReason)
            {
               case -1://timeout
                  if(spSes->iRespReceived==0){
                     //TODO translate
                     if(iMeth==METHOD_INVITE)
                        strReason.setText(strings.lRemoteOutOfReach);//("Please Check your Internet Connection");
                     else
                        strReason.setText("Please Check your Internet Connection");
                     
                        
                     if(iMeth==METHOD_REGISTER && (sockSip.isTCP() || sockSip.isTLS())){
                        this->sockSip.reCreate();//new 08032012,TODO do it if data is not received for more than 5 sec
                        //TODO send options, reregister
                        
                     }
                  }
                  else{
                     if(iMeth==METHOD_REGISTER){
                        strReason.setText("Please Check your username and password");
                     }
                     else{
                        strReason.setText("SIP server did not respond, try again later.");
                     }
                     
                  }
                  
                  break;
               case -2://unknown error
               default:
                  if(sMsg)
                  {
                     iAddReasonPh=1;
                     //strReason.set(&sMsg->sipHdr.dstrReasonPhrase);
                  }
                  
                  
            }
            
            
            if(iMeth==METHOD_REGISTER)
            {
               if(iRegTryParam==0)iUpdate=0;
               p_cfg.reg.bReReg=0;
               p_cfg.reg.bRegistring=0;
               if(p_cfg.reg.bUnReg)
               {
                  p_cfg.iCanRegister=0;
                  p_cfg.reg.uiRegUntil=0;
                  
                  uiNextRegTry=0;
                  //TODO rem address later if timeout
                  CTEditBuf<16>  b;
                  b.setText("--Offline--");
                  cPhoneCallback->registrationInfo(&b,cPhoneCallback->eOffline);
                  iRegTryParam=0;
                  
                  iUpdate=0;
                  if(p_cfg.changeUserParams.iSeq==2)
                  {
                     p_cfg.changeUserParams.iSeq=3;
                  }
               }
               else
               {
                  if(p_cfg.reg.uiRegUntil<uiGT || spSes->iRespReceived){
                     p_cfg.iCanRegister=0;
                     p_cfg.reg.uiRegUntil=0;
                  }
                  
                  pErrStr=(CTStrBase *)&strings.lCannotReg;
               }
               p_cfg.reg.bUnReg=0;
               if(p_cfg.iUserRegister){
                  uiNextRegTry=uiGT+T_GT_SECOND*30;
               }
               
               //cannot register
            }
            else if(iMeth==METHOD_MESSAGE)
            {
               pErrStr=(CTStrBase *)&strings.lCannotDeliv;
               
            }
            else
            {
               pErrStr=(CTStrBase *)&strings.lCannotCon;
            }
            if(iUpdate && strReason.getLen())
            {
               
               //            buf.addText("\r\n",2);
               buf.addChar(' ');//color
               if(pErrStr)
                  buf.addText(*pErrStr);
               
               buf.addText(strings.lReason);
               if(iAddReasonPh)//if sip
               {
                  buf.addText(sMsg->sipHdr.dstrReasonPhrase.strVal,sMsg->sipHdr.dstrReasonPhrase.uiLen);
               }
               else
                  buf.addText(strReason);
            }
            
            int  tryTranslateMsg(CPhSesions *ph, SIP_MSG *sMsg,CTEditBase &b);
            
            tryTranslateMsg(this,sMsg,buf);
            
            if(iUpdate )
            {
               if(iMeth==METHOD_MESSAGE)
               {
                  CHAT_MSG cm(&buf);
                  cPhoneCallback->message(&cm,*(int *)"ERRO",spSes->iReturnParam,NULL,0);
               }
               else {
                  if(iMeth==METHOD_REGISTER){
                     cPhoneCallback->registrationInfo(&buf,cPhoneCallback->eErrorAndOffline);
                  }
                  else cPhoneCallback->info(&buf,(*(int *)"ERRO"),(int)spSes);
                  
               }
            }
         }
      if(!spSes->iOnEndCalled)
      {
         if(spSes->isSession())cPhoneCallback->onEndCall((int)spSes);
         spSes->iOnEndCalled=1;
      }
      
      
      spSes->destroy();
   }
   // iCanClearNow lieto lai ar audio dalju nebuutu prol 
   if(iCanClearNow && spSes->canDestroy())
      freeSes(spSes);
   else
   {
      spSes->uiClearMemAt=uiGT+T_GT_SECOND;
   }
   //tivi_log("kill s exit  ");
   
}

