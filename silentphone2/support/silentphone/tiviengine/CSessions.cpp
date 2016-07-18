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

int getNumberOfCountersZrtp(int iCallID) {
   CSesBase * s = g_getSesByCallID(iCallID);
   if(!s)
       return -1;

   CTSesMediaBase *m = s->mBase;
   if (m) {
      return m->getNumberOfCountersZrtp();
   }
   return -2;
}


int getCountersZrtp(int iCallID, int* counters) {
   CSesBase * s = g_getSesByCallID(iCallID);
   if(!s)
       return -1;

   CTSesMediaBase *m = s->mBase;
   if (m) {
      return m->getCountersZrtp(counters);
   }
   return -2;
}

int CPhSesions::getInfo(const char *key, char *p, int iMax){
   
   int al = sizeof("AssociatedURI") - 1;
   if(strncmp(key, "AssociatedURI", al)==0){

      if(key[al]==0){
         int l = 0;
         for(int i=0;i<sMsg_200okReg.hldAssociated_URI.uiCount;i++){
            l+=t_snprintf(p+l,iMax-l, "%.*s,",
                         sMsg_200okReg.hldAssociated_URI.x[i].sipUri.dstrSipAddr.uiLen,
                         sMsg_200okReg.hldAssociated_URI.x[i].sipUri.dstrSipAddr.strVal);
         }
         if(l)l--;
         p[l] = 0;
         return l;
      }
      else if(key[al]=='='){
         userPreferdSipAddr.set(key+al+1);
         int l = t_snprintf(p, iMax, "%s","ok");
         return l;
      }
   }
   
   if(strcmp(key, "getFreeSesCnt")==0){
      int l = t_snprintf(p, iMax, "%d",getFreeSesCnt());
      return l;
   }
   
   return 0;
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
   
   int sp=*spSes;
   
//collision are not important
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
   x=*spSes;
   
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
      if(pSessionArray[i].cs.iInUse==FALSE)continue;
      spSes=&pSessionArray[i];
      if(spSes->mBase && 
         (spSes->cs.iCallStat==CALL_STAT::EInit || spSes->cs.iCallStat==CALL_STAT::EOk)
         )
      {
         spSes->mBase->onTimer();
      }

//TODO if we have trying on register and if there is nothing to receive resend register
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
      if(!spSes->cs.iInUse || spSes->cs.iCallStat>spSes->cs.EOk)continue;
      mb=spSes->mBase;
      CTMediaIDS *mids=spSes->pMediaIDS;
      if(!spSes->isMediaSession() || !mb || !mids)continue;
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
      if(spSes!=ses || !spSes->cs.iInUse || spSes->cs.iCallStat>spSes->cs.EOk)continue;
      mb=spSes->mBase;
      CTMediaIDS *mids=spSes->pMediaIDS;
      if(!spSes->isMediaSession() || !mb || !mids)break;
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
      if(!spSes->cs.iInUse || spSes!=ses)continue;
      mb=spSes->mBase;
      if(!spSes->isMediaSession() || !mb)break;
      
      return findAOByMB(mb);
      
   }
   return NULL;
   
}

void CPhSesions::onDataSend(char *buf, int iLen, unsigned int uiPos, int iDataType, int iIsVoice, int iWhisperingDetected)
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
      if(!spSes->cs.iInUse ||
         (iDataType == CTSesMediaBase::eAudio && spSes->cs.iIsInConference))continue ;
      
      if(spSes->cs.iCallStat!=CALL_STAT::EOk && spSes->cs.iCallStat!=CALL_STAT::EInit)continue;//EInit: send data if seesion progress
      
      mb=spSes->mBase;
      
      if(!mb || !spSes->isMediaSession() || !spSes->isSesActive()  )continue;
      
      if(mb->isSesActive()){
         int iMuteThis = spSes->cs.iCallStat!=CALL_STAT::EOk || (iWhisperingDetected && !spSes->cs.iWhisperingTo);
         mb->onSend(iMuteThis? NULL : buf, iLen, iDataType, (void*)uiPos, !iMuteThis && iIsVoice);
      }
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
   
   if(spSes->mBase->checkNet()!=1){
      ph.enableTMR(1,spSes);
      
      if(spSes->cs.iCaller && spSes->cs.iCallStat == CALL_STAT::EInit){
         //Probable Firewall
         ph.cPhoneCallback->onRecMsg(101, spSes->ses_id(), (char *)"Connecting.....", sizeof("Connecting.....")-1);
      }
   }
   
   ms.makeSDPMedia(*spSes->mBase, ph.p_cfg.iUseStun &&  ph.p_cfg.iUseOnlyNatIp==0);// && fUseExtPort);
   
   spSes->sSendTo.iHasSDP = 1;
   
   spSes->sSendTo.iHasVideo = spSes->mBase->getMediaType()&CTSesMediaBase::eVideo;
   
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
   cPhoneCallback->onIncomCall(&spSes->dstConAddr,dstr->strVal,dstr->uiLen ,*spSes);
}

static int isSameDSTR(DSTR *a, DSTR *b){
   return a->strVal && b->strVal && a->uiLen == b->uiLen && memcmp(a->strVal, b->strVal, a->uiLen)==0;
}

static DSTR* getRemoteToTag(CSesBase *spSes){
   //if i have request then i have to return from.to
   if(spSes->sSIPMsg.sipHdr.uiMethodID)return &spSes->sSIPMsg.hdrFrom.dstrTag;
   return &spSes->sSIPMsg.hdrTo.dstrTag;
}

static int isCorrectDstTag(CSesBase *spSes, SIP_MSG *sMsg){

   return (!sMsg->sipHdr.uiMethodID && isSameDSTR(&sMsg->hdrTo.dstrTag, getRemoteToTag(spSes)))
           ||
           (sMsg->sipHdr.uiMethodID && isSameDSTR(&sMsg->hdrFrom.dstrTag, getRemoteToTag(spSes))
           );
}

int CPhSesions::onSipMsgSes(CSesBase *spSes, SIP_MSG *sMsg)
{
   int iIsReq=sMsg->sipHdr.uiMethodID;
   int iMeth=sMsg->hdrCSeq.uiMethodID;
   
   if(spSes->cs.iCallStat==CALL_STAT::EOk && !isCorrectDstTag(spSes, sMsg)){
      
      if(sMsg->sipHdr.uiMethodID){
         CMakeSip mm(sockSip);
         mm.cPhoneCallback=cPhoneCallback;
         if(sMsg->sipHdr.uiMethodID & (METHOD_BYE|METHOD_CANCEL)){
            mm.sendResp(sockSip,200,sMsg);
         }
         else{
            mm.sendResp(sockSip,481,sMsg);
         }
      }
      else{
         
         CMakeSip ss(1,sockSip,sMsg);
         DSTR *p;
         if(sMsg->hldContact.uiCount){
            p = &sMsg->hldContact.x[0].sipUri.dstrSipAddr;
         }
         else{
            p = &sMsg->hdrTo.sipUri.dstrSipAddr;
         }
         ss.makeResp(METHOD_ACK,&p_cfg,&p->strVal[0],(int)p->uiLen);
         ss.addContent();
         ss.sendSip(sockSip,&sMsg->addrSipMsgRec);
      }
      return 0;
   }
   
//spSes->cs.iCallStat=CALL_STAT::EOk
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
            CTSesMediaBase *mbToRelase = NULL;
            
            if(mb)
            {
               spSes->setMBase(NULL);
               //we have to release after we are restarting audio (spSes->mBase->onStart())
               //we have lot of CTSesMediaBase obejects and one audio player
               //if active CTSesMediaBase are less then 1 audio player stops
               //at this point the CTSesMediaBase.cnt == 1 ,if we would release here audio player would stop playing
               mbToRelase=mb;
            }
            spSes->setMBase(cPhoneCallback->tryGetMedia("audio"));//TODO find bysdp
            if(spSes->mBase)
            {
               
               ms.makeReq(iMeth,&p_cfg);
               if(iStart)spSes->mBase->onStart();//at this point the CTSesMediaBase.cnt == 2
               makeSDP(*this,spSes,ms);
               ms.addContent();
               sendSip(sockSip,spSes);
               
               if(mbToRelase)cPhoneCallback->mediaFinder->release(mbToRelase);//at this point the CTSesMediaBase.cnt == 1

               return 0;
            }
            if(mbToRelase)cPhoneCallback->mediaFinder->release(mbToRelase);
         }
         ms.makeReq(METHOD_ACK,&p_cfg);
         ms.addContent();
         sendSip(sockSip,spSes);
      }
      if(sMsg->sipHdr.dstrStatusCode.uiVal==400 && sMsg->sipHdr.dstrReasonPhrase.strVal
         && strncmp(sMsg->sipHdr.dstrReasonPhrase.strVal,"Duplicated CallID", sizeof("Duplicated CallID")-1)==0)//SIP/2.0 400 Duplicated CallID
      {
         t_logf(log_events, __FUNCTION__,"Igonred SIP err [%.*s]",sMsg->sipHdr.dstrFullRow.uiLen,sMsg->sipHdr.dstrFullRow.strVal);
         return 0;
      }
      t_logf(log_events, __FUNCTION__,"Proccessing SIP err [%.*s]",sMsg->sipHdr.dstrFullRow.uiLen,sMsg->sipHdr.dstrFullRow.strVal);
      
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
         ms.makeReq(sMsg->sipHdr.dstrStatusCode.uiVal==200 ? METHOD_BYE : METHOD_CANCEL,&p_cfg);
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
      t_logf(log_events, __FUNCTION__,"[SDP ret %d]",iSdpRet);
      
      if(iSdpRet<0)return -1;
      
      if(spSes->mBase && iSdpRet==1 && iMeth != METHOD_INVITE){
         cPhoneCallback->onSdpMedia(*spSes,spSes->mBase->getMediaName());
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
         CMakeSip ms1(sockSip,200,sMsg, spSes);
         if(iMeth==METHOD_CANCEL){
            CMakeSip ms(sockSip,spSes,&spSes->sSIPMsg);
            ms.makeResp(487,&p_cfg);
            ms.addContent();
            sendSip(sockSip,spSes);
            
            if(sMsg->hdrReason.cause.uiVal==200 && sMsg->hdrReason.text.strVal && CMP(sMsg->hdrReason.proto, "SIP", 3)){

               
               spSes->iDontShowMissed = sizeof(CALL_COMPETED_ELSWHERE)-1 == sMsg->hdrReason.text.uiLen &&
               t_isEqual_case(sMsg->hdrReason.text.strVal,CALL_COMPETED_ELSWHERE, sMsg->hdrReason.text.uiLen);
            }
         }

         
      }

      onKillSes(spSes,0,NULL,0);

      return 0;
   case METHOD_INVITE:
      spSes->iSendingReinvite=0;

      //TODO new get contact
      spSes->dstConAddr=sMsg->addrSipMsgRec;


      if (spSes->sSIPMsg.hdrCSeq.uiMethodID==0 || spSes->sSIPMsg.hdrCSeq.dstrID.uiVal!=sMsg->hdrCSeq.dstrID.uiVal || sMsg->sipHdr.dstrStatusCode.uiVal==200
           
           //if contact addr does not match save it
          ||  (sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen &&
               (sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen!=spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.uiLen ||
                memcmp(sMsg->hldContact.x[0].sipUri.dstrSipAddr.strVal, spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.strVal, sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen)))
          
          || (spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.uiLen==0 && sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen)
          ||(spSes->sSIPMsg.hdrCSeq.uiMethodID!=sMsg->hdrCSeq.uiMethodID && sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen)
          ||(spSes->sSIPMsg.hldRecRoute.uiCount!=sMsg->hldRecRoute.uiCount
             || spSes->sSIPMsg.hldRecRoute.x[0].dstrFullRow.uiLen!=sMsg->hldRecRoute.x[0].dstrFullRow.uiLen
             || spSes->sSIPMsg.hldP_Asserted_id.uiCount < sMsg->hldP_Asserted_id.uiCount) //save asserted-id if we see it
          )
      {
         
         if(sMsg->sipHdr.dstrStatusCode.uiVal<=200 && (sMsg->sipHdr.dstrStatusCode.uiVal!=100 || sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen)){
            
            if(sMsg->hldContact.x[0].sipUri.dstrSipAddr.uiLen || !spSes->sSIPMsg.hldContact.x[0].sipUri.dstrSipAddr.uiLen){
               memset(&spSes->sSIPMsg,0,sizeof(SIP_MSG));
               //parsing again, because we use pointers in spSes->sSIPMsg.rawDataBuffer
               cSip.parseSip(sMsg->rawDataBuffer, (int)(sMsg->uiOffset-MSG_BUFFER_TAIL_SIZE), &spSes->sSIPMsg);
            }
         }
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
                  cPhoneCallback->onStartCall(*spSes);
                  
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

               spSes->uiSend480AfterTicks = T_SEND_480_AFTER;//sends sip 480 after ~1min

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
                  cPhoneCallback->onStartCall(*spSes);
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
               cPhoneCallback->onRecMsg(183,*spSes,NULL,0);
            }
            else if(sMsg->sipHdr.dstrStatusCode.uiVal==180 )//TODO && no sdp
            {
               if(spSes->mBase)spSes->mBase->onRecMsg(180);
               cPhoneCallback->onRecMsg(180,*spSes,NULL,0);
            }
            else{
               cPhoneCallback->onRecMsg((int)sMsg->sipHdr.dstrStatusCode.uiVal,*spSes,sMsg->sipHdr.dstrReasonPhrase.strVal,(int)sMsg->sipHdr.dstrReasonPhrase.uiLen);
            }
         default:
            if((sMsg->sipHdr.dstrStatusCode.uiVal>=100 && sMsg->sipHdr.dstrStatusCode.uiVal<200) || (sMsg->sipHdr.dstrStatusCode.uiVal==491))
            {
               spSes->cs.iSendS=0;
               spSes->cs.iWaitS=sMsg->sipHdr.dstrStatusCode.uiVal==491?0:200;
               spSes->sSendTo.stopRetransmit();
            }
            else{
               cPhoneCallback->onRecMsg((int)sMsg->sipHdr.dstrStatusCode.uiVal,*spSes,sMsg->sipHdr.dstrReasonPhrase.strVal,(int)sMsg->sipHdr.dstrReasonPhrase.uiLen);
            }
      } 
         if(spSes->mBase && iSdpRet==1 && iMeth == METHOD_INVITE){
            cPhoneCallback->onSdpMedia(*spSes,spSes->mBase->getMediaName());
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
         setStopReason(spSes, iSipCode, iMeth, NULL, &sMsg->sipHdr.dstrReasonPhrase);
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
         st->pContent-=((size_t)&spSes->sSendTo-(size_t)st);

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
      if (iSipCode==100)
      {
         spSes->sSendTo.recv100REG(uiGT, 30);//wait 30 sec if we have nothing back report failed
         return 0;
      }
      spSes->cs.iWaitS=0;
      
      if(iSipCode>299 && iMeth==METHOD_MESSAGE)
      {

         onKillSes(spSes,-2,sMsg,METHOD_MESSAGE);
         //
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
         spSes->sSendTo.recv100REG(uiGT);
         //spSes->cs.iWaitS=0; //do not set to , it will never timeout here
         return 0;
      }

      
      if (spSes->cs.iSendS!=METHOD_REGISTER){
         log_events( __FUNCTION__,"something is really wrong, spSes->cs.iSendS != METHOD_REGISTER");
         return -1;
      }
      
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
                     }
                     break;
                  }
               }
            }
            if(ok==1 && b)
               uiExpTime=b;
            
            memset(&sMsg_200okReg,0,sizeof(SIP_MSG));
            //parsing again, because we use pointers in spSes->sSIPMsg.rawDataBuffer
            cSip.parseSip(sMsg->rawDataBuffer, (int)(sMsg->uiOffset-MSG_BUFFER_TAIL_SIZE), &sMsg_200okReg);
            
            for(int z=0;z<sMsg_200okReg.hldAssociated_URI.uiCount;z++){
               printf("[%.*s]\n",sMsg_200okReg.hldAssociated_URI.x[z].sipUri.dstrSipAddr.uiLen, sMsg_200okReg.hldAssociated_URI.x[z].sipUri.dstrSipAddr.strVal);
            }
            
            uiNextRegTry = uiGT+((uiExpTime*10*T_GT_SECOND)>>4);
            p_cfg.reg.uiRegUntil=uiGT+(uiExpTime*15*T_GT_SECOND>>4);
            
            t_logf(log_events, __FUNCTION__,"[REG-EXPIRES: ok=%d %d,%d,%lld,%d %s]",ok, uiExpTime,b,(p_cfg.reg.uiRegUntil-uiGT)/T_GT_SECOND,p_cfg.uiExpires, p_cfg.str32GWaddr.strVal);
            
            p_cfg.iCanRegister=1;

            
            if(sMsg->hldContact.uiCount>1){
               CTEditBuf<128> rc;
               rc.setText(strings.lRegSucc);
               rc.addInt(sMsg->hldContact.uiCount, "(%u)");
               registrationInfo(&rc,cPhoneCallback->eOnline);
            }
            else{
               registrationInfo(&strings.lRegSucc,cPhoneCallback->eOnline);
            }
            
            //TODO if(prev_contact_addr!=curr_contact_addr)reInvite(); here
         }
         else
         {
            iRegTryParam=0;
            //TODO rem addr
            CTEditBuf<16>  b;
            b.setText("---Offline---");
            
            registrationInfo(&b, cPhoneCallback->eOffline);
            
            if(p_cfg.iUserRegister){
               uiNextRegTry=uiGT+T_GT_SECOND * 10;
            }
    
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

int CPhSesions::registrationInfo(CTStrBase *e, int iType){
  
   switch (iType) {
      case CTEngineCallBack::eErrorAndOffline:
      case CTEngineCallBack::eOffline:
         
         if(!p_cfg.iUserRegister)uiNextRegTry=0;
         p_cfg.reg.uiRegUntil=0;
         p_cfg.reg.bUnReg=0;
         p_cfg.reg.bRegistring=0;
         
         if(p_cfg.changeUserParams.iSeq==2)
            p_cfg.changeUserParams.iSeq=3;
         
         break;
      case CTEngineCallBack::eOnline:
         iRegTryParam=0;
         reinviteMonitor.onOnline(&uiGT);
         p_cfg.iReRegisterNow=0;
         p_cfg.reg.bRegistring=0;
         
         
      default:
         break;
   }
   return cPhoneCallback->registrationInfo(e, iType);
}

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
         updateTMRState(spSes);
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


void CPhSesions::setStopReason(CSesBase *spSes, int code, int meth, const char *msg, DSTR *dstr, CTEditBase *e){
   
   if(spSes->iStopReasonSet)return;
   spSes->iStopReasonSet=1;
   
   if(meth==METHOD_MESSAGE){
      
      DSTR d;
      if(!dstr){
         
         dstr = &d;
         if(code == -1)d.strVal = (char *)"ERR: timeout";
         else if(code == -2)d.strVal = (char *)"ERR: slow network";
         else d.strVal=(char*)"ERR: unknow";
         d.uiLen = strlen(d.strVal);
      }

      CTAxoInterfaceBase::sharedInstance()->stateReport(spSes->sentSimpleMsgId64bit, code, (u_int8_t*)dstr->strVal, dstr->uiLen);
   }
   
   
   if(spSes->retMsg){
      if(dstr)spSes->retMsg->setText(dstr->strVal, dstr->uiLen);
      else if(e)spSes->retMsg->setText(*e);
      else if(msg)spSes->retMsg->setText(msg);
   }
   
   if(spSes->ptrResp)*spSes->ptrResp=code;
}


void CPhSesions::onKillSes(CSesBase *spSes, int iReason, SIP_MSG *sMsg ,int iMeth)
{
   if(!spSes || !spSes->cs.iInUse)return;
   
   int iCanClearNow=1;
   
   if(spSes->iKillCalled==0)
   {
      spSes->iKillCalled=1;
      
      setStopReason(spSes, spSes->iRespReceived==0 ? -1 : -2, iMeth, NULL);
      
      if(spSes->isMediaSession())//TODO
      {
         keepAlive.sendEverySec(0);
         if(spSes->iSessionStopedByCaller==0 && spSes->sSIPMsg.uiOffset)
         {
            //tivi_log("kill s 2");
            cPhoneCallback->onRecMsg(METHOD_BYE,*spSes,NULL,0);
            if(spSes->mBase)
            {
               spSes->mBase->onRecMsg(METHOD_BYE);
            }
            cPhoneCallback->info(&strings.lCallEnded,0,*spSes);
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
         cPhoneCallback->info(&buf,*(int *)"BERR",*spSes);
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
                        strReason.setText(strings.lRemoteOutOfReach);
                     else
                        strReason.setText("Please Check your Internet Connection");
                     
                        
                     if(iMeth==METHOD_REGISTER && (sockSip.isTCP() || sockSip.isTLS())){
                        t_logf(log_events, __FUNCTION__, "sockdebug: Recreating socket due to timeout");
                        this->sockSip.reCreate();//new 08032012,TODO do it if data is not received for more than 5 sec
                        //TODO send options, reregister
                        
                     }
                  }
                  else{
                     if(iMeth==METHOD_REGISTER){
                        strReason.setText("Please Check your username and password");//sc nevar shis buut
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
                  registrationInfo(&b,cPhoneCallback->eOffline);
                  iRegTryParam=0;
                  
                  iUpdate=0;

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
               if(p_cfg.iUserRegister && !uiNextRegTry){
                  uiNextRegTry=uiGT+T_GT_SECOND*30;
                  iRegTryParam = 1;
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
               
               buf.addChar(' ');//color
               if(pErrStr){
                  buf.addText(*pErrStr);
                  buf.addText(". ",2);
               }
               
              //-- buf.addText(strings.lReason);
               if(iAddReasonPh)//if sip
               {
#define T_U_N_O "user not online"
                  int ofs = sMsg->sipHdr.dstrReasonPhrase.uiLen - (sizeof(T_U_N_O)-1);
                  if(ofs>0 && strncmp(sMsg->sipHdr.dstrReasonPhrase.strVal+ofs, T_U_N_O,sizeof(T_U_N_O)-1 )==0){//Cannot connect. Not Found -user not online
                     buf.setText(T_U_N_O);
#undef T_U_N_O
                  }
                  else{
                     const char *ressiptr = tg_translate(sMsg->sipHdr.dstrReasonPhrase.strVal,sMsg->sipHdr.dstrReasonPhrase.uiLen);
                     if(ressiptr!=sMsg->sipHdr.dstrReasonPhrase.strVal)
                        buf.addText(ressiptr);
                     else
                        buf.addText(sMsg->sipHdr.dstrReasonPhrase.strVal,sMsg->sipHdr.dstrReasonPhrase.uiLen);
                  }
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
                     registrationInfo(&buf,cPhoneCallback->eErrorAndOffline);
                  }
                  else cPhoneCallback->info(&buf,(*(int *)"ERRO"),*spSes);
                  
               }
            }
         }
      if(!spSes->iOnEndCalled)
      {
         
         if(spSes->isMediaSession()){
            log_call_marker(0, spSes->sSIPMsg.dstrCallID.strVal, spSes->sSIPMsg.dstrCallID.uiLen);
            cPhoneCallback->onEndCall(*spSes, spSes->iDontShowMissed);
         }
         spSes->iOnEndCalled=1;
      }
      
      
      spSes->destroy();
   }
   // iCanClearNow lieto lai ar audio dalju nebuutu prol 
   if(iCanClearNow && spSes->canDestroy()){
      log_events(__FUNCTION__, "freeSes");
      freeSes(spSes);
   }
   else
   {
      spSes->uiClearMemAt=uiGT+T_GT_SECOND;
   }
   //tivi_log("kill s exit  ");
   
}

//TMR

typedef struct{
   CSesBase *pSes;
   CPhSesions *eng;
}_TMP_ENG_SES_PTR;

int CPhSesions::sendSip(CTSipSock &sc, CSesBase *spSes){//must call from protected state
   
   if(spSes->cs.eTMRState && spSes->sSendTo.iHasSDP && spSes->pMediaIDS->tmrTunnel){
      
      if(spSes->cs.eTMRState==CALL_STAT::eTMRRequired ||
         (spSes->sSendTo.iHasVideo && !spSes->pMediaIDS->tmrTunnel->video.isConnected()
          && !spSes->pMediaIDS->tmrTunnel->video.isConnecting())){
         
         spSes->cs.eTMRState=CALL_STAT::eTMRSetuping;
         
         CTThread *th = new CTThread();
         th->destroyAfterExit();
         
         _TMP_ENG_SES_PTR *th_params = new _TMP_ENG_SES_PTR;
         th_params->pSes=spSes;
         th_params->eng = this;
         
         th->create(tmrThreadS, th_params);
         return 0;
      }
      
      if(spSes->cs.eTMRState!=CALL_STAT::eTMRActive)return 0;//thread will update and send SDP
      
      updateSDPAddr(spSes);
      return sendSipP(sc,spSes);
   }
   
   return sendSipP(sc,spSes);
}

static int updateSdpMediaAddress(CSesBase *spSes, ADDR &a, char *pStart, const char *sdpSearch = "\r\nm=audio "){
   
   char *pP = strstr(pStart,sdpSearch);
   if(!pP)return 0;
   
   pP+=strlen(sdpSearch);
   
   char bufP[64];int pl=snprintf(bufP,sizeof(bufP),"%u", a.getPort());
   
   char *tmp;
   tmp=pP;
   while(isdigit(tmp[0]))tmp++;
   int iOldPortL=tmp-pP;
   

   int iInc = (pl-iOldPortL);
   //fix port
   if(iInc){
      int ofs = (pP - &spSes->sSendTo.data.rawData[0]);
      memmove(pP+pl,pP+iOldPortL,spSes->sSendTo.data.offset-ofs);
   }
   strncpy(pP, bufP, pl);//overwrite port
   
   //pielikt_sdp_p2p_info
   
   spSes->sSendTo.data.offset+=iInc;
   spSes->sSendTo.iContentLen+=iInc;
   
   char *pIce = strstr(pP,"\r\na=t-sc-ice:");
   
   if(pIce){
      pIce+=2;
      
      char bufIce[256];
      int createTIceCandidates(ADDR *priv, ADDR *publ, char *pOut, int iMaxLen, const char *clinetType);
      int szL=createTIceCandidates(&a,&a, bufIce, sizeof(bufIce),  "relay");
      
      char *pEnd = strstr(pIce+10,"\r\n")+2;
      int iIceLen = pEnd - pIce;
      
      iInc = (szL-iIceLen);
      
      if(iInc){
         int ofs = (pEnd - &spSes->sSendTo.data.rawData[0]);
         memmove(pIce+szL, pIce+iIceLen, spSes->sSendTo.data.offset-ofs);
      }
      memcpy(pIce,bufIce,szL);
      spSes->sSendTo.data.offset+=iInc;
      spSes->sSendTo.iContentLen+=iInc;
      
      //TODO move
   }

   return 0;
}

static char * updateSdpMediaIP(CSesBase *spSes, ADDR &a){
   char *pIP = strstr(&spSes->sSendTo.data.rawData[spSes->sSendTo.iContentLengthOffset+5],"\r\nc=IN IP4 ");
   if(!pIP)return NULL;
   
   pIP+=sizeof("\r\nc=IN IP4 ")-1;
   
   char bufIP[64];
   a.toStr(&bufIP[0],0);int ipl = strlen(bufIP);
   
   char *tmp;
   
   tmp=pIP;
   while(isdigit(tmp[0]) || tmp[0]=='.')tmp++; //calc ip len
   int iOldIpLen = tmp - pIP;
   
   //fix ip
   if(iOldIpLen!=ipl){
      int ofs = (pIP - &spSes->sSendTo.data.rawData[0]);
      memmove(pIP+ipl,pIP+iOldIpLen,spSes->sSendTo.data.offset-ofs);
   }
   
   strncpy(pIP, bufIP, ipl);//overwrite ip
   
   int iInc = (ipl-iOldIpLen);
   
   spSes->sSendTo.data.offset+=iInc;
   spSes->sSendTo.iContentLen+=iInc;
   
   return pIP;
}

void CPhSesions::updateSDPAddr(CSesBase *spSes){//must call from protected state
   
   if(!spSes->cs.eTMRState)return;//must be connected
   
   if(!spSes->sSendTo.iHasSDP)return;
   
   if(spSes->sSendTo.data.offset+150>sizeof(spSes->sSendTo.data.rawData))return;
   
   if(spSes->sSendTo.iContentLen<100)return;
   
   if(spSes->sSendTo.iContentLen>spSes->sSendTo.data.offset)return;
   
   
   ADDR a;
   if(spSes->pMediaIDS->tmrTunnel->audio.getServSDPAddr(a)<0){
      //TODO setup backup, or release a call
      log_events( __FUNCTION__,"[Err: getServSDPAddr()<0]");
      return ;
   }
   ADDR aVid; //TODO spSes->pMediaIDS->tmrTunnel->getServSDPAddr(a,1)
   if(spSes->sSendTo.iHasVideo && spSes->pMediaIDS->tmrTunnel->video.getServSDPAddr(aVid)<0){
      //TODO setup backup, or release a call
      log_events( __FUNCTION__,"[Err: video getServSDPAddr()<0]");
      return ;
   }
   
   spSes->sSendTo.iHasSDP = 0;

   
   char *pIP = updateSdpMediaIP(spSes,a);
   
   updateSdpMediaAddress(spSes, a, pIP);
   
   if(spSes->sSendTo.iHasVideo && aVid.getPort()){
      updateSdpMediaAddress(spSes, aVid, pIP, "\r\nm=video ");
   }
   
   int l = sprintf(&spSes->sSendTo.data.rawData[spSes->sSendTo.iContentLengthOffset],"%u", spSes->sSendTo.iContentLen);
   spSes->sSendTo.data.rawData[spSes->sSendTo.iContentLengthOffset+l]=' ';//removes '\0' at end of sprintf

   //printf("[Out->%.*s]\n",spSes->sSendTo.data.offset, spSes->sSendTo.data.rawData);
   
   return;
}

static void updateTmrState(CSesBase *spSes, CTMRTunnel *t, const char *media){
   if(!t)return;
   
   char buf[128];
   char key[64];
   
   snprintf(key,sizeof(key),"%s.peer_sdp_addr",media);
   int r = getMediaInfo(spSes, key, buf, sizeof(buf)-1);
   
   if(r<=0){
      puts("[BUG: updateTMRState: getCallInfo(media.audio.peer_sdp_addr)<=0 ] ");
      return ;
   }
   
   ADDR peer_sdp_addr;
   peer_sdp_addr = buf;
   
   if(!peer_sdp_addr.ip){
      puts("[if (SDP addr) {BUG: updateTMRState: !peer_sdp_addr.ip }] ");
      return ;
   }
   
   snprintf(key,sizeof(key),"%s.peer_ssrc",media);
   r = getMediaInfo(spSes, key, buf, sizeof(buf)-1);
   unsigned int ssrc_peer = 0;
   
   if(r>0){ // if(r<=0) FS is not sending SSRC in SDP
      ssrc_peer = strtoul(buf,NULL,0);
   }
   
   t->tmr_info.setPeerSDP(peer_sdp_addr, ssrc_peer);
   
   snprintf(key,sizeof(key),"%s.p2p_addr",media);
   r = getMediaInfo(spSes, key, buf, sizeof(buf)-1);
   if(r>0){
      peer_sdp_addr = buf;
      t->tmr_info.setPeerCandidate(peer_sdp_addr);
   }
   t->tmr_info.iWeHaveChanges++;
   
}
//call this when receive peer SDP
void CPhSesions::updateTMRState(CSesBase *spSes){ //must call from protected state
   
   if(!spSes->cs.eTMRState || !spSes->pMediaIDS || !spSes->mBase)return;
   CTMRTunnelAV *t = spSes->pMediaIDS->tmrTunnel;
   if(!t)return;
   
   updateTmrState(spSes, &t->audio, "audio");
   
   if(spSes->mBase->getMediaType() & spSes->mBase->eVideo){
      t->videoFound = 1;
      updateTmrState(spSes, &t->video, "video");
   }
   
   if(t->isConnected() || spSes->cs.iCallStat == CALL_STAT::EOk)
      t->sendUpdate();
   else{
      //Can I setup TMR here ?  No, we have a call forking
   }
   
}

int CPhSesions::tmrThreadS(void *pThis){
   
   
   _TMP_ENG_SES_PTR *p = (_TMP_ENG_SES_PTR *)pThis;
   _TMP_ENG_SES_PTR l = *p;//copy
   delete p;
   return l.eng->tmrThread(l.pSes);
}

int CPhSesions::tmrThread(CSesBase *spSes){
   
   
   LOCK_MUTEX_SES
   CTMRTunnelAV *t = spSes->pMediaIDS->tmrTunnel;
   /*
   if(spSes->cs.iInUse &&  spSes->cs.eTMRState==CALL_STAT::eTMRSetuping){
      
      t->audio.tmr_info.setMySSRC(spSes->pMediaIDS->m[0].uiSSRC);
      t->video.tmr_info.setMySSRC(spSes->pMediaIDS->m[1].uiSSRC);
      
      ADDR a = p_cfg.bufTMRAddr;
      
      //check dns
      if(!a.ip)a.ip = CTSock::getHostByName(a.bufAddr);
      
      spSes->cs.eTMRState = CALL_STAT::eConnecting;
   }
   */
   
   t->videoFound |=spSes->sSendTo.iHasVideo;
   
   if(spSes->cs.iInUse  && !t->isConnected()){
      t->audio.tmr_info.setMySSRC(spSes->pMediaIDS->m[0].uiSSRC);
      t->video.tmr_info.setMySSRC(spSes->pMediaIDS->m[1].uiSSRC);
      ADDR a = p_cfg.bufTMRAddr;
      
      //check dns
      if(!a.ip)a.ip = CTSock::getHostByName(a.bufAddr);//TODO rem from mutex
      
      spSes->cs.eTMRState = CALL_STAT::eConnecting;
      t->connect(a);
   }
   
   UNLOCK_MUTEX_SES
   
   while(1){
      Sleep(50);
      LOCK_MUTEX_SES
      
      t->videoFound |=spSes->sSendTo.iHasVideo;
      
      if( (spSes->cs.iCallStat != CALL_STAT::EInit && spSes->cs.iCallStat != CALL_STAT::EOk)){
         UNLOCK_MUTEX_SES
         return 0;
      }
      
      if(!spSes->cs.iInUse || !spSes->pMediaIDS || !spSes->pMediaIDS->tmrTunnel || !t){
         UNLOCK_MUTEX_SES
         return 0;
      }
      
      if(t && t->isConnected()){
         
         UNLOCK_MUTEX_SES
         break;
      }
      
      UNLOCK_MUTEX_SES
      
   }
   
   LOCK_MUTEX_SES
   
   if(spSes->cs.iInUse &&
      (spSes->cs.iCallStat == CALL_STAT::EInit || spSes->cs.iCallStat == CALL_STAT::EOk)){
      
      spSes->cs.eTMRState=CALL_STAT::eTMRActive;
      updateSDPAddr(spSes);
      sendSipP(sockSip, spSes);
   }
   UNLOCK_MUTEX_SES
   
   return 0;
}


