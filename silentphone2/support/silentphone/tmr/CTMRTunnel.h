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

#ifndef C_TMR_TUNNEL_H
#define C_TMR_TUNNEL_H


//class ADDR;
#include "../baseclasses/ADDR.h"

class CTRtpCallBack{
public:
   
   virtual int onRcvRTP(void *pThis, unsigned char *pack, int iLen, ADDR *aFrom, int iIsVideo)=0;
};

class CTMRConnectionInfo{
public:
   
   enum{eHdrSize=5,EPingSize=eHdrSize+4,eMaxPackDelay_ms=70,eMaxChunkSize=1000,eMaxVideoChunkSize=(10*1024)};
   
   CTMRConnectionInfo(){
      my_ssrc=0;peer_ssrc=0;
      iStopping=0;
      iReallyMinRT= iMinRoundtripTime=0xffff;
      iPingsReceived=0;
      iPrevRoundtripTime=0;
      iPeerInfoSent=0;
      mediaID=0;
      rtPos=0;
      iMedianRT = 400;
      peerSDPAddr.clear();
      iLastMaxRT=100;
      iWeHaveChanges=0;
      iIsVideo=0;
      iShouldSkipVideoFrame=0;
   }
   
   void setMySSRC(unsigned int ssrc){my_ssrc=ssrc;}
   
   void setPeerSDP(ADDR &a, unsigned int ssrc ){
      peer_ssrc = ssrc;
      peerSDPAddr = a;
      iPeerInfoSent = 0;
   }
   
   void setPeerCandidate(ADDR &a){
      peerSDPp2pAddr = a;
   }
   int iWeHaveChanges;
   
   int iStopping;
   
   int iPeerInfoSent;
   
   int iServRespReceived;
   
   int iIsVideo;
   int iShouldSkipVideoFrame;
   
   //TODO median and min roundtrip for a last 100 pack
   
   int iPrevRoundtripTime;
   int iPingsReceived;
   
   unsigned long long mediaID;//received_from_serv
   
   unsigned int my_ssrc;
   unsigned int peer_ssrc;
   ADDR peerSDPAddr;//received from a client
   ADDR peerSDPp2pAddr;
   
   //getter getServSDPAddr
   ADDR addrWitchWeInsertIntoSDP;
   inline int getRT(){return iMinRoundtripTime<10000?iMinRoundtripTime:500;}
   inline int getMedRT(){return iMedianRT<10000?iMedianRT:500;}
   
   int optimalSocketCnt();
   
   int maxAllowedSockCount();
   
   void updateRoundtrip(int n_rt){
      
      iPrevRoundtripTime = iMinRoundtripTime;
      if(n_rt<iMinRoundtripTime)iMinRoundtripTime= n_rt;
      if(n_rt<iReallyMinRT)iReallyMinRT = n_rt;
      iPingsReceived++;
      
      updateMedianRT(n_rt);
      
   }
private:
   int iLastMaxRT;
   int iReallyMinRT;
   int iMinRoundtripTime;
   int lastRoundtrips[128];
   int rtPos;
   int iMedianRT;
   
   void updateMedianRT(int n_rt);
};

class CTRtpSendBuf{
   int iInitSize;
public:
   inline int initSize(){return iInitSize;}
   CTRtpSendBuf(){iInitSize=0;buf=NULL;sentAt=0;clear();}
   ~CTRtpSendBuf(){delete buf;}
   int addHdr(char ptype, int iSize);
   int addData(void *p, int iSize);
   int clear(){iLen=0;iPacketsIn=0;return 0;}
   int init(int iIsVideo){
      if(buf)return 0;
      iInitSize =(iIsVideo? CTMRConnectionInfo::eMaxVideoChunkSize : CTMRConnectionInfo::eMaxChunkSize)+512;
      buf=new char [iInitSize];
      return 1;
   }
   
   int getLen(){return iLen;}
   
   int iPacketsIn;
   
   unsigned long long sentAt;
   
   char *buf;
private:
   
   
   int iLen;
   
};

class CTMRConnector{
   CTRtpCallBack *cb;
   int iInThread,iWasInThread;
   ADDR dst;
public:
   
   enum{eNull,eConnecting=0x01,eConnected=0x02,eUpdateSent=0x04,eCanSendMedia=0x08,eStoped=0x10};

   int eStatusFlag;
   
   
   CTMRConnector(CTMRConnectionInfo *_tmr_info, CTRtpCallBack *_cb, ADDR *dstAddr, int isTLS=0);
   
   int connect();
   int update();
   inline int isInThread(){return iInThread;}
   inline int shouldDelete(){return !iInThread && iWasInThread;}
   
   int sendRTP(CTRtpSendBuf *sb, int iAddPing);
   int sendFreePack();
   
   //TODO onNewIP
   
   int stop(int iCloseSock=0);
   
   int setLostPackets(int iLostCnt, int iReceived);//vai resetot visus kounterus?
   int setAudioJit(int ms);
   
   unsigned long long createdAt;
   unsigned long long receivedAt;
   unsigned long long sentAt;
   unsigned long long stopedAt;
   
   unsigned long long susspendUntil;
   unsigned long long pingSentAt;//if no resp susspend
   int roundtrip;
   int prev_roundtrip;
   int lost;
   
   long long rtpPacketsSent;
   
   int iIsMaster;
   
   int iReleaseAfterWeSeePingAndIgnorePing;//the serv should think that sock is jammed and should not try this sock
   
private:
   void reset();
 
   int onRcv(unsigned char *pack, int iLen);
   
   int sendAndAddPing(CTRtpSendBuf *sb, int iAddPing);
   int sendLost();
   
   int createPack(int iIsUpdate, char *dst, int iMaxSize);
   
   int _send(void *p, int iLen);
   
   static int thRecvS(void *pThis);
   int workerThread();
   
   void *sock;
   
  // char respBuf[128];
   //int iRespLen;;
   
   CTMRConnectionInfo *tmr_info;
   
   static int canRecSend(void *p);
   long long rtpPacketsReceived;
   
   int iRecreateCnt;
   
};



class CTMutex;

class CTMRTunnel: public CTRtpCallBack{
   enum{eMaxConnections=1<<5};
   CTRtpCallBack *callback;
public:
   CTMRTunnel(CTRtpCallBack *cb, int iIsVideo);
   void setCB(CTRtpCallBack *cb);

   int connect(ADDR &a, int tls=0);
   int isConnected();
   int isConnecting();
   
   int sendUpdate();
   
   int getServSDPAddr(ADDR &a);//if !conn then returns -1;
   
   int sendRTP(unsigned char *p, int iLen, int iVideo);
   int stop();
   
   
   CTMRConnectionInfo tmr_info;
   
  // void onNewIp();// not required will automaticaly reconnect
   
   int stats[8];
   
   
   ~CTMRTunnel();
private:
   int sendRTP_PR(unsigned char *p, int iLen, int iVideo);
   
   CTRtpSendBuf sendBuf;
   CTRtpSendBuf sendData;
   
   CTMutex *mutexRecv;
   CTMutex *mutexSend;
   
   long long iDidNotSeeZRTP;
   
   int sendPacketNow(CTRtpSendBuf *sb, int iAddPing);
   
   int nonJammedSockCnt();
   
   int createHelperSockets(int cnt);
   
   virtual int onRcvRTP(void *pThis, unsigned char *pack, int iLen, ADDR *aFrom, int iIsVideo);
   
   int iSuspended;
   int iConnectionsActive;
   int iConnectionsStoped;

   CTMRConnector *c[eMaxConnections];
   
   int lastUsedIdx;
   
   ADDR dstTcpServAddr;
};

class CTMRTunnelAV{
public:
   CTMRTunnel audio;
   CTMRTunnel video;
   
   
   CTMRTunnelAV(CTRtpCallBack *cb):audio(cb,0),video(cb,1){
      videoFound=0;
   }
   
   int videoFound;
   
   int connect(ADDR &a, int tls=0){
      audio.connect(a,tls);
      if(videoFound)video.connect(a,tls);
      return 0;
   }
   
   int sendUpdate(){
      audio.sendUpdate();
      if(videoFound)video.sendUpdate();
      return 0;
   }
   
   int sendRTP(unsigned char *p, int iLen, int iVideo){
      if(iVideo){videoFound=1;return video.sendRTP(p, iLen, iVideo);}
      return audio.sendRTP(p, iLen, iVideo);
   }
   int stop(){
      audio.stop();
      video.stop();
      return 0;
   }
   
   int isConnected(){
      return audio.isConnected() && (!videoFound || video.isConnected());
   }
   
   static void deleteAfter5Sec(CTMRTunnelAV *t);
private:
   ~CTMRTunnelAV();
   static int deleteTh(void *t);
   

   
};


#endif
