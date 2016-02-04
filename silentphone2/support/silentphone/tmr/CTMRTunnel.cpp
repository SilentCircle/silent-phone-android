//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#include "CTMRTunnel.h"
#include "tmr_create_pack.h"

#include "../os/CTiViSock.h"
#include "../os/CTThread.h"
#include "../os/CTTcp.h"
#include "../os/CTMutex.h"

#include <stdlib.h>//qsort

#include "../tiviengine/tivi_log.h"

//#define T_TMR_TEST
#ifdef _WIN32
static unsigned long long getTC(){
 unsigned long long getTickCountLL();
 return getTickCountLL();
}
#else
#include <sys/time.h>
static unsigned long long getTC(){

   struct timeval v;
   if(gettimeofday(&v,0)==0)
      return v.tv_sec*1000+
      v.tv_usec/1000;//*1000;
   return 0;
}
#endif

static int q_compareInt (const void * a, const void * b)
{
   return ( *(int*)a - *(int*)b );
}

void CTMRConnectionInfo::updateMedianRT(int n_rt){
   
   lastRoundtrips[rtPos&127] = n_rt;
   rtPos++;
   if(rtPos == 127){
      rtPos = 0;
      qsort(&lastRoundtrips[0],127, sizeof(int),q_compareInt);
      iMedianRT = lastRoundtrips[63];
      t_logf(log_audio_stats, __FUNCTION__,"TMR iMedianRT=%d mi%d ma%d",iMedianRT, lastRoundtrips[0], lastRoundtrips[126]);
      iMinRoundtripTime = (lastRoundtrips[0]+iMinRoundtripTime+1)>>1;
      iLastMaxRT = lastRoundtrips[120];
      //TODO mark we can update to recomended socket count 
   }
}

int CTMRConnectionInfo::optimalSocketCnt(){
   
   int rt = getRT();
   int max_rt = rt +getMedRT()+100;
   if(max_rt>10000)return 10;//start with 10 sockets
   if(iIsVideo){
      int r = rt*2/120+2;
      return r;
   }
   
   int ps = CTMRConnectionInfo::eMaxPackDelay_ms;
   if(ps>60)ps = 60; else if(ps<20) ps = 20;
   
   //TODO test
   // 1) set packsize  = roudtrip/2
   // 2) use 5 sock
   //TODO if (max_rt<ps)ps = max_rt;
   
   int r = max_rt/ps + 5; // 80 packsize_in ms
   int r2 = iLastMaxRT/ps+2;
   if(r<r2)r=r2;
   if(r>31)r=31;
   return r;
}

int CTMRConnectionInfo::maxAllowedSockCount(){
   
   int ps = CTMRConnectionInfo::eMaxPackDelay_ms;
   if(ps>60)ps = 60; else if(ps<20) ps = 20;
   
   //
   //--int r = iMedianRT*2;
   //--if(r<iLastMaxRT)r=iLastMaxRT;
   
   return iLastMaxRT*3/ps+5;
}


CTMRConnector::CTMRConnector(CTMRConnectionInfo *_tmr_info, CTRtpCallBack *_cb, ADDR *dstAddr, int isTLS){
   cb=_cb;
   dst=*dstAddr;
   tmr_info = _tmr_info;
   

   iIsMaster=0;
   reset();
   
}
void CTMRConnector::reset(){
   eStatusFlag=eNull;
   receivedAt=0;
   stopedAt=0;
   susspendUntil=0;
   pingSentAt=0;
   roundtrip=0;
   prev_roundtrip=0;
   lost=0;
   sock=NULL;
   iInThread=0;
   iWasInThread=0;
   
   rtpPacketsSent=0;
   rtpPacketsReceived=0;
   
   iRecreateCnt=0;
   iReleaseAfterWeSeePingAndIgnorePing=0;
   
}

int CTRtpSendBuf::addHdr(char ptype, int iDataLen){
   if(iLen+CTMRConnectionInfo::eHdrSize>=iInitSize)return -1;
   
   int pack_size = iDataLen + CTMRConnectionInfo::eHdrSize;
   
   
   memcpy(&buf[iLen],&pack_size, 4);
   iLen+=4;

   buf[iLen]=ptype;
   iLen++;
   
   iPacketsIn++;
   
   return 0;
}

int CTRtpSendBuf::addData(void *p, int iSize){
   if(iLen+iSize>=iInitSize)return -1;
   memcpy(&buf[iLen],p, iSize);
   iLen+=iSize;
   return 0;
}
//TODO clean out buf, or take and move to diferent sock
int CTMRConnector::sendRTP(CTRtpSendBuf *sb, int iAddPing){

   if(!(eStatusFlag & eCanSendMedia) || (eStatusFlag & eStoped))return -1;
   
   sendAndAddPing(sb, iAddPing);
   
   return 0;
}


int CTMRConnector::stop(int iCloseSock){
   eStatusFlag |= eStoped;
   eStatusFlag &=~ (eCanSendMedia|eConnected|eConnecting);
   if(iCloseSock && sock)((CTSockTcp *)sock)->closeSocket();
   return 0;
}

int CTMRConnector::setLostPackets(int iLostCnt, int iReceived){return 0;}
int CTMRConnector::setAudioJit(int ms){return 0;}

int CTMRConnector::sendAndAddPing(CTRtpSendBuf *sb, int iAddPing){
   unsigned long long t = getTC();
   
   if(!pingSentAt && iAddPing){
   //   printf("[add ping]");
      sb->addHdr('P',4);//ping hdr
      sb->addData(&t, 4);//ping packet
      pingSentAt = t;
   }
   
   _send(&sb->buf[0],sb->getLen());
   
   sb->sentAt = t;
   
   sb->clear();
   
   rtpPacketsSent++;
   
   return 0;
}
int CTMRConnector::sendLost(){return 0;}
   
   
int CTMRConnector::_send(void *p, int iLen){
   if(iLen<=0)return 0;
   if(!iInThread || (eStatusFlag & eStoped))return -2;
   CTSockTcp *s = (CTSockTcp *)sock;
   sentAt = getTC();
   int r = s ? s->_send((char*)p, iLen):-3;
   if(((char *)p)[4]!='R' && ((char *)p)[4]!='p')
      t_logf(log_audio, __FUNCTION__,"[_send %c %d]", ((char *)p)[4],r);
   return r;
}

int CTMRConnector::connect(){
//createThread
   if(iInThread)return 0;
   iInThread=1;
   createdAt = getTC();
   eStatusFlag |= eConnecting;
   
   CTThread *th= new CTThread();
   th->destroyAfterExit();
   th->create(thRecvS,this);
   return 0;
}

int CTMRConnector::update(){
   if(tmr_info->iPeerInfoSent && tmr_info->iWeHaveChanges==0){
      eStatusFlag |= eCanSendMedia;
      return 0;
   }
   eStatusFlag |= eCanSendMedia;
   tmr_info->iPeerInfoSent|=1;
   char tmp[2048];
   int iPackLen=createPack(1,tmp, sizeof(tmp)-1);;
   
   return _send((unsigned char *)tmp, iPackLen);
}

int CTMRConnector::sendFreePack(){
   char buf[64];
   
   int iPackLen = 0;
   buf[4]='F';iPackLen=5;
   iPackLen += addField<unsigned long long>(&buf[iPackLen], sizeof(buf),TMR_PACK_STR::eMEDIA_ID, tmr_info->mediaID);
   memcpy(buf,&iPackLen,4);
   
   _send(buf,iPackLen);
   
   return 0;
}

static unsigned int rot32(unsigned int x){
   //return x;
   x = (x & 0x0000FFFF) << 16 | (x & 0xFFFF0000) >> 16;
   x = (x & 0x00FF00FF) << 8 | (x & 0xFF00FF00) >> 8;
   return x;
}

int CTMRConnector::createPack(int iIsUpdate, char *dst, int iMaxSize){
   int iPackLen = 0;

   dst[4]=iIsUpdate? 'U' : 'C';iPackLen=5;
   
   iPackLen += addField(dst+iPackLen, iMaxSize-iPackLen,TMR_PACK_STR::eCREATOR_SSRC, rot32(tmr_info->my_ssrc));

   
   if(tmr_info->mediaID)
      iPackLen += addField(dst+iPackLen, iMaxSize-iPackLen,TMR_PACK_STR::eMEDIA_ID, tmr_info->mediaID);
   
   if(tmr_info->peerSDPAddr.ip){
      if(!iIsUpdate)tmr_info->iPeerInfoSent|=2;
      
      iPackLen += addField(dst+iPackLen, iMaxSize-iPackLen,TMR_PACK_STR::ePEER_IP4, tmr_info->peerSDPAddr.ip);
      
      short port  = tmr_info->peerSDPAddr.getPort();
      iPackLen += addField(dst+iPackLen, iMaxSize-iPackLen,TMR_PACK_STR::ePEER_PORT, port);
      
      if(tmr_info->peer_ssrc)
         iPackLen += addField(dst+iPackLen, iMaxSize-iPackLen,TMR_PACK_STR::ePEER_SSRC, rot32(tmr_info->peer_ssrc));
      
      if(tmr_info->peerSDPp2pAddr.ip){
         iPackLen += addField(dst+iPackLen, iMaxSize-iPackLen,TMR_PACK_STR::ePEER_P2P_IP4, tmr_info->peerSDPp2pAddr.ip);
         
         port  = tmr_info->peerSDPp2pAddr.getPort();
         
         iPackLen += addField(dst+iPackLen, iMaxSize-iPackLen,TMR_PACK_STR::ePEER_P2P_PORT, port);
      }
      eStatusFlag |= eUpdateSent;
      tmr_info->iWeHaveChanges=0;
   }
   
   printf("[send my_ssrc %x %d]",tmr_info->my_ssrc,eStatusFlag);

   
   memcpy(dst,&iPackLen,4);
   
   return iPackLen;
}

int CTMRConnector::thRecvS(void *pThis){
    ((CTMRConnector*)pThis)->workerThread();
   //should i delete here?? NO - i have to set c[idx]=NULL;
   return 0;
}

typedef struct {
   SOCKET sock;
   int iCanSendReceive;
   int iBytesInTmpBuf;
   int iWarnings;
   
   int (*canRecSend)(void *p);
   void *pUser;
}TCP_MEDIA_SOCKET;

#include "tmr_split.h"//inline funticon


int CTMRConnector::canRecSend(void *p){
   if(!p)return 0;
   
   CTMRConnector *t = (CTMRConnector*)p;
   
   return t->eStatusFlag && !(t->eStatusFlag & CTMRConnector::eStoped) &&  !t->tmr_info->iStopping;
}



int CTMRConnector::workerThread(){
   char tmp[2048];
   char ret_buf[2048];
   int iPackLen=0;
   eStatusFlag |= eConnecting;
   iWasInThread=1;
   

   CTSockCB scb;
   CTSockTcp *s = new CTSockTcp(scb);
   s->iIsRTPSock=1;
   s->createSock();
   sock = s;
   s->_connect(&dst);
   netBlock(s->sock);
   
   iPackLen=createPack(0,tmp, sizeof(tmp)-1);
   
   if(iRecreateCnt)Sleep((iRecreateCnt&7)*100);
   
   _send((unsigned char *)tmp, iPackLen);

   TCP_MEDIA_SOCKET ms;
   memset(&ms, 0, sizeof(TCP_MEDIA_SOCKET));

   ms.sock = s->sock;
   ms.pUser = this;
   ms.canRecSend = canRecSend;

//#define T_TEST_STOP_SOCK
#ifdef T_TEST_STOP_SOCK
  int iMaxPacketsViaSocket=15+(getTC()&0x0f);
#endif
   while(!(eStatusFlag & eStoped) && !tmr_info->iStopping){
      
      //split
      int rec = recvTMRPack(&ms,tmp, sizeof(tmp), ret_buf, sizeof(ret_buf));//recv
      
      if(rec<0){
         printf("[Warn: recvTMRPack=%d]\n",rec);
         break;
      }
      if(rec==0 || (eStatusFlag & eStoped) || tmr_info->iStopping){
         break;//server closed the sock
      }
      
     // printf("[recvTMRPack=%d %c]\n",rec,ret_buf[4]);

      onRcv((unsigned char*)&ret_buf[0],rec);
#ifdef T_TEST_STOP_SOCK
      iMaxPacketsViaSocket--;
      if(iMaxPacketsViaSocket<0)break;//test
#endif

   }
   stop();
   Sleep(200);
   sock = NULL;
   
   int iResetInTh=1;
   if(iIsMaster && !tmr_info->iStopping){
      reset();
      Sleep(100);
      iInThread=0;
      iRecreateCnt++;
      connect();
      iResetInTh=0;
   }
   
   Sleep(100);
   s->closeSocket();
   Sleep(200);
   delete s;
   t_logf(log_audio_stats, __FUNCTION__,"exit th st[S%lld R%lld RT%d v%d sentat%llu mid%llu]",rtpPacketsSent,rtpPacketsReceived, roundtrip, tmr_info->iIsVideo, pingSentAt,tmr_info->mediaID);
   if(iResetInTh)iInThread=0;
   return 0;
}

int CTMRConnector::onRcv(unsigned char *pack, int iLen){
   if(iLen<5)return -1;
   
   
   int packSize = *(int*)pack;
   
   
   if(packSize>iLen)return 0;
   
   pack+=4; iLen-=4; //packsize;
   
   unsigned long long t = getTC();
   receivedAt = t;
   
   //printf("[rec tmr %c]\n", pack[0]);
   
   if(pack[0]!='R'){
      
      if(pack[0]=='P'){
         if(iReleaseAfterWeSeePingAndIgnorePing){

            stop();
            return 0;
         }
         pack[0]='p';
         _send(pack-4, iLen+4);
         return 0;
      }
      
      if(pack[0]=='p'){
         unsigned int tFromPack;
         memcpy(&tFromPack, pack+1, 4);
         
         unsigned int tt = t;
         
         int rtR = tt - tFromPack;
         if(!rtR)rtR=1;

         prev_roundtrip = roundtrip;
         roundtrip = rtR;
         pingSentAt = 0;

         tmr_info->updateRoundtrip(roundtrip);
         
         return 0;
      }
      
      if(pack[0]!='u' && pack[0]!='c'){
         t_logf(log_events, __FUNCTION__,"[Warn: TMR first byte = (%c %d)]",pack[0], pack[0]);
         return 0;
      }
      
      TMR_PACK_STR tmr;
      int r = parseTMR(&tmr, pack+1, iLen-1);
      if(r<0){
         t_logf(log_events, __FUNCTION__,"[err parseTMR()=%d]\n",r);
         return r;
      }
      
      
      
      int code = -1;
      tmr.getValue(TMR_PACK_STR::eRESP_CODE,&code);
      if(code!=200){
         t_logf(log_events, __FUNCTION__,"[err TMR_PACK_IDS::eRESP_CODE %d]\n",code);
         stop();
         return 0;
      }
      unsigned short port=0;
      int ip=0;
      
      if(tmr.getValue(TMR_PACK_STR::eSERV_PORT, &port)<0 ||
              tmr.getValue(TMR_PACK_STR::eSERV_IP4, &ip)<0){

         t_logf(log_events, __FUNCTION__,"[err TMR_PACK_IDS::eSERV_IP4 || TMR_PACK_IDS::eSERV_PORT]\n");
      }
      
      tmr.getValue(TMR_PACK_STR::eMEDIA_ID, &tmr_info->mediaID);
      
      tmr_info->addrWitchWeInsertIntoSDP.ip=ip;
      tmr_info->addrWitchWeInsertIntoSDP.setPort(port);
      
      eStatusFlag |= eConnected;
      
      if(tmr_info->iPeerInfoSent){
         eStatusFlag |= eCanSendMedia;
         puts("[TMR eCanSendMedia]");
      }
      else puts("[TMR connected]");

      
      if(cb)return cb->onRcvRTP(this, NULL,NULL,NULL,tmr_info->iIsVideo);
      return r;
   }
   rtpPacketsReceived++;
   if(cb)return cb->onRcvRTP(this, pack+1,iLen-1,&dst,tmr_info->iIsVideo);
   return 0;
}

int CTMRTunnelAV::deleteTh(void *p){
   if(!p)return 0;
   CTMRTunnelAV *t = (CTMRTunnelAV*)p;
   t->audio.setCB(NULL);
   t->audio.stop();
   t->video.setCB(NULL);
   t->video.stop();
   puts("wait_deleteT");
   Sleep(5000);
   delete t;
   puts("deleteT ok");
   return 0;
}

CTMRTunnelAV::~CTMRTunnelAV(){}

void CTMRTunnelAV::deleteAfter5Sec(CTMRTunnelAV* t){
   if(!t)return;
   CTThread *th= new CTThread();
   th->destroyAfterExit();
   th->create(deleteTh, t);
}


CTMRTunnel::CTMRTunnel(CTRtpCallBack *cb, int _iIsVideo){
   callback = cb;
   this->tmr_info.iIsVideo = _iIsVideo;
   sendBuf.init(_iIsVideo);
   sendData.init(0);
   for(int i=0;i<eMaxConnections;i++)c[i]=NULL;
   iConnectionsActive = 0;
   iConnectionsStoped = 0;
   lastUsedIdx = -1;
   mutexRecv=new CTMutex();
   mutexSend=new CTMutex();
   memset(stats,0,sizeof(stats));
   iSuspended=0;
   iDidNotSeeZRTP=0;
   
}
CTMRTunnel::~CTMRTunnel(){
   for(int i=0;i<eMaxConnections;i++){
      if(c[i]){
         CTMRConnector *s = c[i];
         c[i]=NULL;
         for(int z=0;z<20 && s->isInThread();z++){if(z==0)s->stop();Sleep(100);}
         delete s;
         
      }
   }
   delete mutexRecv;
   delete mutexSend;
}

void CTMRTunnel::setCB(CTRtpCallBack *cb){
   callback = cb;
}

int CTMRTunnel::createHelperSockets(int cnt){

  // return 0;
   int ca=0;
   int ma = tmr_info.iIsVideo? 5 : eMaxConnections;

   for(int i=0;i<ma;i++){
      if(c[i]){ca++;continue;}
      
      if(cnt <= 0)continue;
 
      c[i] = new CTMRConnector(&tmr_info, this, &dstTcpServAddr);
      c[i]->connect();
      ca++;
      cnt--;
   }
   iConnectionsActive = ca;
   return cnt;
}

int CTMRTunnel::onRcvRTP(void *pThis, unsigned char *pack, int iLen, ADDR *aFrom, int iIsVideo){
   //pThis type - CTMRConnector

   if(pack==NULL || aFrom == NULL){
      //check connection state
      return 0;
   }
   CTRtpCallBack *callback_L = callback;
   if(callback_L && pack && !tmr_info.iStopping){
      mutexRecv->lock();
      if(callback && !tmr_info.iStopping){
      //   printf("[recm %d %d]\n",iLen,iIsVideo);
         callback_L->onRcvRTP(this, pack, iLen, aFrom,iIsVideo);
      }
      mutexRecv->unLock();
   }
   return 0;
}

int CTMRTunnel::connect(ADDR &a, int tls){
   if(c[0] || dstTcpServAddr.ip)return 0;
   dstTcpServAddr = a;
   c[0] = new CTMRConnector(&tmr_info, this, &a);
   c[0]->iIsMaster=1;
   c[0]->connect();
   
   lastUsedIdx = 0;
   iConnectionsActive++;
   return 0;
}

int CTMRTunnel::isConnecting(){
   CTMRConnector *c0 = c[0];
   return c0 && (c0->eStatusFlag & CTMRConnector::eConnecting);
}
int CTMRTunnel::isConnected(){
   CTMRConnector *c0 = c[0];
   return c0 && (c0->eStatusFlag & CTMRConnector::eConnected);
}

int CTMRTunnel::sendUpdate(){//call this afer we have a peer SDP
   if(tmr_info.iPeerInfoSent && tmr_info.iWeHaveChanges==0){
      return 0;
   }
   

   if(c[0])c[0]->update();
   
   return 0;
}

//if !conn then returns -1;
int CTMRTunnel::getServSDPAddr(ADDR &a){
   if(!tmr_info.addrWitchWeInsertIntoSDP.ip || !isConnected())return -1;
   a = tmr_info.addrWitchWeInsertIntoSDP;
   return 0;
}


int CTMRTunnel::nonJammedSockCnt(){
   int r=0;
   for(int ii=0; ii<eMaxConnections;ii++){
      CTMRConnector *s  = c[ii];
      if(!s || s->shouldDelete())continue;
      if (s->pingSentAt)continue;
      if(s->iReleaseAfterWeSeePingAndIgnorePing)continue;//could restore if we have to
      r++;
   }
   return r;
}

int CTMRTunnel::sendPacketNow(CTRtpSendBuf *sb, int iAddPing){
 
   unsigned long long tc = getTC();
   
   int susp=0;
   int nonJ = 1;
   
   //find the best socket
   if(tmr_info.iPingsReceived){
      lastUsedIdx++;
      lastUsedIdx &= (eMaxConnections-1);
      
      int iUseThis = -1;
      int iSecondOption = -1;
      int iLastOption = -1;
      
      int medRT = tmr_info.getMedRT();
      
      int iSockIsGood = medRT+10;
      int iSockIsOk = medRT*2+500;
      int iSockIsBad = medRT*3+5000;
      int iSocketsSkiped=0;
      int iChecked=0;
      int iSuspendedInThisLoop=0;
      
      nonJ = nonJammedSockCnt();
      
      int max_allowed_sock = tmr_info.maxAllowedSockCount();
      
      //checkSockets

      for(int ii=0; ii<eMaxConnections;ii++){
         
         int idx = (lastUsedIdx+ii)&(eMaxConnections-1);
         
         CTMRConnector *s = c[idx];
         
         if(s && s->shouldDelete()){
            if(s->iIsMaster || idx==0){//it will recreate itself
            }
            else {
               c[idx]=NULL;delete s;
               iConnectionsActive--;
            }
         }
      }
      
      int iCanRelease1Sock  = iConnectionsActive>max_allowed_sock && nonJ>5;
      
      //TODO if(iCanRelease1Sock){s = findSockJammedorWithLongesRT();if(s)s->iReleaseAfterWeSeePingAndIgnorePing=1;}
      
      //TODO if(optimalSocketCnt()>iConnectionsActive)relese sockets which are rt !good or delayed
      //but do not kill that sock imidiatly mark as not used, dont replay to serv pings
      //and release it after 1 sec
      
      for(int ii=0; ii<eMaxConnections;ii++){
         
         int idx = (lastUsedIdx+ii)&(eMaxConnections-1);
         
         CTMRConnector *s = c[idx];
         
         if(!s|| (s->eStatusFlag & CTMRConnector::eCanSendMedia)==0 || s->iReleaseAfterWeSeePingAndIgnorePing)continue;
         iChecked++;
         
         if(s->shouldDelete())continue;
         
         
         
         int wTimeIsOk = s->pingSentAt==0 || (iChecked*3<iConnectionsActive && (tc - s->pingSentAt < iSockIsOk));
         
         if(idx!=0 && !s->iIsMaster && iCanRelease1Sock && s->rtpPacketsSent>5 && ((s->pingSentAt && (int)(tc - s->pingSentAt) < medRT) || s->roundtrip<medRT)){
         
            iCanRelease1Sock=0;
            
            s->iReleaseAfterWeSeePingAndIgnorePing=1;
            
            //I am not sure that we can release, because server could send the media via this sock
            //We should tell the serv that it should not send me the media
            //Should I ignore pings, the serv would not send the media via this sock
            continue;
         }
         ///TODO get best 100 roundtrips
         
         if(s->rtpPacketsSent==0 || //select unused socket first
            (s->roundtrip && s->roundtrip<=iSockIsOk && wTimeIsOk)){
            
            if(s->rtpPacketsSent==0 ||
               ((s->pingSentAt==0) && s->roundtrip<iSockIsGood)){
               iUseThis = idx;
               break;
            }
            if(iSecondOption==-1 )iSecondOption = idx;
            
         }
         if(!s->pingSentAt && s->susspendUntil<tc && (iSecondOption==-1 || c[idx]->roundtrip<c[iSecondOption]->roundtrip)){
            iSecondOption = idx;//move this to SERV
         }
         else if(!s->pingSentAt){//we have response socket should be clean
            if(iLastOption==-1 || s->roundtrip<c[iLastOption]->roundtrip || c[iLastOption]->pingSentAt)
                iLastOption = idx;
         }
         else if(iLastOption == -1 &&  s->susspendUntil < tc){
            iLastOption = idx;
         }
         if(!s->pingSentAt && iChecked*3<iConnectionsActive+1 && (s->sentAt-tc)>5000){//dont forget about sock and give it a second try
            //this works
            iSecondOption = idx;//move it to serv
            break;
         }
         if(!s->susspendUntil && s->pingSentAt && !wTimeIsOk && (tc - s->pingSentAt)<iSockIsBad && iSuspended*2<iConnectionsActive){
            s->susspendUntil = tc + 100;
            susp++;
            stats[4]++;
            iSuspendedInThisLoop++;
         }
         else if(s->susspendUntil>tc)susp++;
         else s->susspendUntil=0;
         iSocketsSkiped++;
         
      }
      iSuspended=susp;
      if(iSuspended*4>iConnectionsActive)printf("iSocketsSkiped=%d iSuspended=%d \n",iSocketsSkiped,iSuspended); //TODO
      
      int iCreated=0;
      
      
      if(tmr_info.getRT()>0 && tmr_info.getRT()<10000 && iUseThis==-1 && nonJ<7){
         int cnt  = tmr_info.optimalSocketCnt() - iConnectionsActive+(iSuspended>>1); //80 is eMaxPackDelay_ms;
         if(cnt>1){createHelperSockets(cnt>>1);iCreated=1;}
      }
      
      if (!iCreated && iSuspended+6>iConnectionsActive && iSuspendedInThisLoop && nonJ<7){
         createHelperSockets(3);
      }
      else if(!iCreated && nonJ<3){
         createHelperSockets(3-nonJ);
      }
      
      if(iUseThis >= 0) {lastUsedIdx = iUseThis;stats[0]++;}
      else if(iSecondOption>=0) {lastUsedIdx=iSecondOption;stats[1]++;}
      else if(iLastOption>=0){lastUsedIdx = iLastOption;stats[2]++;}
      else {lastUsedIdx=0;stats[3]++;}
      
      static int cc=0;cc++; if((cc&15)==1)printf("[ca-v%d %d opt=%d max=%d nonJ=%d,idx=%d stats %d %d %d %d %d]",tmr_info.iIsVideo, iConnectionsActive,tmr_info.optimalSocketCnt(),max_allowed_sock,nonJ,lastUsedIdx, stats[0],stats[1],stats[2],stats[3],stats[4]);
   }

   if(sb==&sendData && c[0]){
      lastUsedIdx=0;//TODO use this one for ZRTP
   }
   else if(nonJ==0 && tmr_info.iIsVideo){
     // tmr_info.iShouldSkipVideoFrame=1;
      //TODO reduce video quality
      sb->clear();
      return 0;
   }
   else tmr_info.iShouldSkipVideoFrame=0;
   
   //TODO if (sock is susspended  && c[idx]->pingSentAt
   //      && (tc - c[idx]->pingSentAt) > iMinRoundTripTime*5+2000)recreateSocket()
   
   //TODO if( video )add_ping to the last frame packet
   
   CTMRConnector *lastUsed = c[lastUsedIdx];
   lastUsed->susspendUntil=0;
   if(tmr_info.iWeHaveChanges)lastUsed->update();
   
   return lastUsed->sendRTP(sb,iAddPing);
}


int CTMRTunnel::sendRTP(unsigned char *p, int iLen, int iVideo){
   if(tmr_info.iStopping)return 0;
   
   if(!isConnected()){
      return 0;
   }
   return sendRTP_PR(p, iLen, iVideo);
}

int CTMRTunnel::sendRTP_PR(unsigned char *p, int iLen, int iVideo){
   //createHelperSockets();
   
   CTMutexAutoLock m(*mutexSend);
   
   if(tmr_info.iStopping)return 0;
   
   if(!isConnected()){
      return 0;
   }
   
   
   if(iDidNotSeeZRTP>100 && iConnectionsActive<tmr_info.optimalSocketCnt())
      createHelperSockets(tmr_info.optimalSocketCnt()-iConnectionsActive);
   
   CTRtpSendBuf *sb = &sendBuf;
   
   if (p[0]!=0x80){ //not RTP
      sb = &sendData;
      iDidNotSeeZRTP=0;
   }
   else iDidNotSeeZRTP++;

   unsigned long long tc = getTC();
   
   int ml = iLen+sb->getLen()+CTMRConnectionInfo::eHdrSize+CTMRConnectionInfo::EPingSize;
    //iVideo!=1 - audio packet or last chunk of video frame
   if(ml>sb->initSize()){
       sendPacketNow(sb,iVideo!=1);
    }
   sb->addHdr('R', iLen);
   sb->addData(p, iLen);
   
  // printf("[%x %x %x]\n",*(int*)&sb->buf[5],*(int*)&sb->buf[4+5],*(int*)&sb->buf[8+5]);

  // if (max_rt<ps)ps = max_rt;
   //TODO eMaxPackDelay_ms =  min(120,max_roundtrip/2+20)
  // printf("[vid=%d l=%d i=%d]\n",iVideo,sb->getLen(),sb->initSize());
   if(iVideo==2 || sb == &sendData || sb->getLen()+100>sb->initSize() ||
      sb->iPacketsIn>(iVideo?12:4) ||  tc - sb->sentAt > CTMRConnectionInfo::eMaxPackDelay_ms){
     // printf("[sb->iPacketsIn=%d]",sb->iPacketsIn);
      int r = sendPacketNow(sb,iVideo!=1);
      if(r<0)return r;
   }
  // else printf("[else sb->iPacketsIn=%d]",sb->iPacketsIn);
   return iLen;
   //send
   
}
int CTMRTunnel::stop(){
   if(tmr_info.iStopping)return 0;
   tmr_info.iStopping = 1;
   
   if(c[0])c[0]->sendFreePack();
   
   for(int ii=0; ii<eMaxConnections;ii++){
      if(c[ii] && !(c[ii]->eStatusFlag & c[ii]->eStoped)){
         c[ii]->stop(1);
      }
   }
   
   return 0;
}


#ifdef T_TMR_TEST

typedef struct {
   unsigned int cc:4;        /* CSRC count */
   unsigned int x:1;         /* header extension flag */
   unsigned int p:1;         /* padding flag */
   unsigned int version:2;   /* protocol version */
   unsigned int pt:7;        /* payload type */
   unsigned int m:1;         /* marker bit */
   unsigned int seq:16;      /* sequence number */
   unsigned int ts;               /* timestamp */
   unsigned int ssrc;             /* synchronization source */
   unsigned char data[1028];
} RTP_PACK;


class CTTestTmr: public CTRtpCallBack{
public:
   CTMRTunnel *tunnel;
   unsigned int my_ssrc;
   
   CTTestTmr():tunnel(NULL){
      tunnel = new CTMRTunnel(this);
   }
   ~CTTestTmr(){CTMRTunnel::deleteAfter5Sec(tunnel);}
   int onRcvRTP(void *pThis, unsigned char *pack, int iLen, ADDR *aFrom){
      if(iLen<20)return 0;
      RTP_PACK *p=(RTP_PACK*)pack;
      printf("rec [%p][ssrc=%x][seq=%d][%d,%s]\n",this,p->ssrc,p->seq, iLen, p->data);
      return 0;
   }
   void doConnect(ADDR *a){
      
      static unsigned int ssrc_add=0;ssrc_add++;
      my_ssrc = 0x11220000+((ssrc_add<<12)&0xf000)+(getTC()&0xfff);
      
      printf("[my_ssrc=%x]",my_ssrc);
      tunnel->tmr_info.setMySSRC(my_ssrc);
      tunnel->connect(*a);
      
      while (!tunnel->isConnected()) {
         Sleep(40);
      }
      
   }
   
};



int mainT(int argc, const char * argv[]){
   
   const char *addr1="127.0.0.1:44060";
   const char *addr2="127.0.0.1:44060";
//  const char *addr2="92.240.74.121:443";
   if(argc>=3){
      addr1=argv[1];
      addr2=argv[2];
    //  return -1;
   }
   
   CTTestTmr t1;
   CTTestTmr t2;
   
   ADDR a1 = addr1;//serv1 addr
   ADDR a2 = addr2;//serv2 addr
   
   t1.doConnect(&a1);printf("%s connected %p\n", addr1,&t1);
   t2.doConnect(&a2);printf("%s connected %p\n", addr2,&t1);
   
   ADDR a1Serv;
   ADDR a2Serv;
   
   t1.tunnel->getServSDPAddr(a1Serv);
   t2.tunnel->getServSDPAddr(a2Serv);
   
   t1.tunnel->tmr_info.setPeerSDP(a2Serv, t2.my_ssrc);
   t2.tunnel->tmr_info.setPeerSDP(a1Serv, t1.my_ssrc);
   
   t1.tunnel->sendUpdate();
   t2.tunnel->sendUpdate();
   
   RTP_PACK rtp;
   memset(&rtp, 0, sizeof(RTP_PACK));
   
   rtp.version = 2;
   rtp.cc = 0;
   rtp.x = 0;
   rtp.p = 0;
   Sleep(50);
   int cnt=0;
   for(int i=0;i<500;i++){
      
      rtp.seq = cnt;
      rtp.ssrc=rot32(t1.my_ssrc);
      
      sprintf((char*)&rtp.data[0], "pack_t1_%7d_%x",cnt,t1.my_ssrc);cnt++;
      t1.tunnel->sendRTP((unsigned char*)&rtp,sizeof(rtp)>>2,0);

      rtp.seq = cnt;
      rtp.ssrc=rot32(t2.my_ssrc);
      
      sprintf((char*)&rtp.data[0],"pack_t2_%7d_%x",cnt,t2.my_ssrc);cnt++;
      t2.tunnel->sendRTP((unsigned char*)&rtp,sizeof(rtp)>>2,0);
      
      Sleep(20);
      if((i&63)==63){
         //printStats
      }
   }
   
   puts("Stopping");
   Sleep(200);
   puts("Stopping 1");
   t1.tunnel->stop();
   t2.tunnel->stop();
   
   Sleep(200);
   puts("Stoped");
   Sleep(500);
   printf("Pass %p and %p\n",&t1,&t2);
   
   
   return 0;
}
typedef struct{
   int c;
   const char *argv[100];
}_TEST_TMR_S;

int test_tmr_thFnc(void *p){
   _TEST_TMR_S *t=(_TEST_TMR_S*)p;
   mainT(t->c, t->argv);
   return 0;
}

int main(int argc, const char * argv[]){
   const int n=300;
   _TEST_TMR_S t;
   t.c = argc;
   for(int i=0;i<argc;i++){
      t.argv[i] = argv[i];
   }
   
   
   CTThread *th[n];
   for(int i=0;i<n;i++){
      th[i] = new CTThread();
     // th->destroyAfterExit();
      th[i]->create(test_tmr_thFnc, &t);
      
   }
   Sleep(100);
   int iInTh=0;
   do{
      iInTh=0;
      for(int i=0;i<n;i++){
         iInTh+=!!th[i]->iInThread;
      }
      printf("th_cnt=%d\n",iInTh);
      Sleep(500);
   }while(iInTh>0);
   
   Sleep(5000);
   return 0;
}


void *initARPool(){return (void*)1;}
void relARPool(void *p){};
void *prepareTcpSocketForBg(int s){return NULL;}
void relTcpBGSock(void *ptr){}

#endif


