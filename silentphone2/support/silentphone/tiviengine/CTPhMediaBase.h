/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2017, Silent Circle, LLC.  All rights reserved.

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
#ifndef _C_TIVI_PH_MEDIA_B_H
#define _C_TIVI_PH_MEDIA_B_H 

#include "main.h"
#include "../sdp/sdp.h"
#include "../encrypt/md5/md5.h"
#include "../encrypt/zrtp/CTZrtp.h"
#include "../baseclasses/CTBase.h"
#include "../utils/CTDataBuf.h"

#include "../tmr/CTMRTunnel.h"



class CTMakeSdpBase{
public:
   virtual int makeSdp(char *p, int iMaxLen, int fUseExtPort)=0;
};
//TODO fix: rate 16Khz
class CTHoldSounds{
public:
   enum{eSkip500mSec=16000};//secure gong
   
   CTHoldSounds(){iPutOnHoldCnt=0;reset();}
   
   int isEmpty(){return !onHoldMusic.getLen() && !putOnHoldMusic.getLen();}
   
   void init(CTDataBuf *init, CTDataBuf *repeat){ //accepts 16Khz only, todo fix
      if(repeat)onHoldMusic.set(*repeat);
      if(init)putOnHoldMusic.set(*init);
      iPutOnHoldCnt=0;
      reset();
   }
   
   void reset(){
      onHoldMusic.reset();putOnHoldMusic.reset();iMustPlayOnPut=1;iMustPlayRepeat=0;iWeAreOnHold=0;iResetFlag=0;
      iSkipCount =eSkip500mSec;
      iWeAreSecure=0;
   }
   char *get(int iBytes){
      if(iResetFlag){
         int h = iWeAreOnHold;
         reset();
         iWeAreOnHold = h;
         iPutOnHoldCnt++;
      }
      
      if(isEmpty())return 0;
      
      //TODO welcome sound
      
      if(iMustPlayOnPut){
         if(!iWeAreSecure)return 0;//wait about SDES?
         if(iSkipCount>0){iSkipCount-=iBytes; return 0;}//dont play hold sound immediately, wait for secure
         
         char *p = putOnHoldMusic.get(iBytes);
         if(p)return p;
         iMustPlayOnPut=0;
         iMustPlayRepeat=1;
      }
      
      if(iMustPlayRepeat){
         char *p = onHoldMusic.get(iBytes);
         if(p)return p;
         onHoldMusic.reset();
         return onHoldMusic.get(iBytes);
      }
      return 0;
   }
   
   void callState(int iIsOnHold, int iIsSecure){
      iWeAreSecure=iIsSecure;
      if(iWeAreOnHold){
         iWeAreOnHold = iIsOnHold;
         return;
      }
      if(!iIsOnHold)return;
      
      iResetFlag = 1;
      
      iWeAreOnHold = 1;
      
      
   }
private:
   int iMustPlayOnPut;
   int iMustPlayRepeat;
   
   int iWeAreOnHold;
   int iWeAreSecure;
   
   int iResetFlag;
   
   int iSkipCount;

   CTDataBuf onHoldMusic;//will repeat
   CTDataBuf putOnHoldMusic;//will not repeat
   
   int iPutOnHoldCnt;
};


template<class T>
class CListItemRel: public CListItem{
   T *t;
public:
   T *getPtr(){return t;}
   CListItemRel(T *a):CListItem(-1),t(a){}
   ~CListItemRel(){delete t;}
   
};


class CTMediaIDS{
   void resetSeqNumbers(){
      
      extern unsigned int getTickCount();
      unsigned int t = getTickCount();
      
      CTMd5 md5;
      md5.update(t);
      md5.update(m[0].seq);//prev seq number
      md5.update(m[0].uiSSRC);
      md5.update(m[1].uiSSRC);
      
      unsigned int res = md5.final();
      
      //RTP seq numbers should start with random number
      //RFC 3550, Section 5.1
      m[1].seq=(unsigned short)(res&0xffff);
      m[0].seq=(unsigned short)(res>>16);
      
      m[1].seq&=0x7fff;//we dont want to confuse the SRTP rollower counter.
      m[0].seq&=0x7fff;
   }
   
   CTZRTP *pzrtp;
   CTZRTP *sdes_only;
   
   CTList relList;
   
public:
   int iIsSdesOnly;
   
   typedef struct{
      unsigned int uiSSRC;
      unsigned short seq;
   }MEDIA;
   
   MEDIA m[2];
   
   CTMediaIDS():tmrTunnel(NULL){
      pzrtp=NULL;
      sdes_only=NULL;
      
      resetSeqNumbers();
      
      pOwner=NULL;
      holdSounds.reset();
   }
   ~CTMediaIDS(){
      if(pzrtp)delete pzrtp;pzrtp=NULL;
      if(sdes_only)delete sdes_only;sdes_only=NULL;
   }
   void init(unsigned int uiA_SSRC, unsigned int uiV_SSRC){
      
      
      m[0].uiSSRC=uiA_SSRC;
      m[1].uiSSRC=uiV_SSRC;
      
      resetSeqNumbers();
      
      tmrTunnel = NULL;
   }
   
   CTZRTP *getZRTP(){return iIsSdesOnly? sdes_only : pzrtp;}
   int ownZRTP(CTZRTP *z){return sdes_only == z || pzrtp == z; }
   
   
   void initZRTP(CTZrtpCb *cb, void *g, int sdesOnly, int callid, void *pSes){
      initZRTP_priv(cb,g, sdesOnly);

      if(sdes_only){
         sdes_only->init_zrtp(0,(char *)"empty_zid",callid,1,1);
         sdes_only->pSes=pSes;
      }
      if(pzrtp){
         pzrtp->init_zrtp(0,(char *)"empty_zid",callid,1,1);
         pzrtp->pSes=pSes;
      }
      
   }
private:
   void initZRTP_priv(CTZrtpCb *cb, void *g, int sdesOnly){
      
      if(!pzrtp) pzrtp=new CTZRTP(g);
      
      iIsSdesOnly = sdesOnly;
      
      if(sdesOnly){
         if(sdes_only){
            sdes_only->stop(sdes_only->AudioStream);
            sdes_only->stop(sdes_only->VideoStream);

            auto a = new CListItemRel<CTZRTP>(sdes_only);
            relList.addToTail(a);
         }
      }
      sdes_only =new CTZRTP(g);

      
      pzrtp->zrtpcb=cb;
      sdes_only->zrtpcb = cb;
   }
public:
   void stop(){
      
      if(pzrtp){
         pzrtp->stop(pzrtp->AudioStream);
         pzrtp->stop(pzrtp->VideoStream);
      }
      
      if(sdes_only){
         sdes_only->stop(sdes_only->AudioStream);
         sdes_only->stop(sdes_only->VideoStream);
         
      }
      if(tmrTunnel){
         tmrTunnel->stop();
      }
   }
   void release(){
      CTMRTunnelAV::deleteAfter5Sec(tmrTunnel);
      tmrTunnel = NULL;
      if(pzrtp)  pzrtp->release_zrtp();
      if(sdes_only)  sdes_only->release_zrtp();
      delete sdes_only;sdes_only=NULL;
      
      auto a = (CListItemRel<CTZRTP>*)relList.getLRoot();
      while(a){
         
         CTZRTP *z = a->getPtr();
         z->release_zrtp();
         a = (CListItemRel<CTZRTP>*)relList.getNext(a);
      }
      relList.removeAll();
      
      pOwner=NULL;
   }
   
   CTHoldSounds holdSounds;
   
   CTMRTunnelAV *tmrTunnel;
   //TODO video Tunnel
   
   
   void *pOwner;
   
};

#define T_GT_SECOND 1000
#define T_GT_HSECOND (T_GT_SECOND>>1)
#define T_GT_QSECOND (T_GT_SECOND>>2)
#define T_GT_MINUTE (T_GT_SECOND*60)


class CSessionsBase{
   t_ph_tick uiNeedsVadUntil;
public:
   int bRun;

   static int iRandomCounter;//used for call id and sip tag
   
   t_ph_tick uiGT;
   
   void setNeedsVad(void *cRTPA){//TODO add,rem needs vad, and create list<void*> 
      uiNeedsVadUntil = uiGT + T_GT_SECOND * 20;
   }
   
   inline int vadMustBeOn(){
      //Should we check active codec and if GSM then enable vad
      return p_cfg.useVAD() || uiNeedsVadUntil>uiGT;
   }
   
   int iCurMaxVolume;

   int ipBinded;

   ADDR addrStun;
   int iPingTime;
   
   ADDR extAddr;
   
   PHONE_CFG& p_cfg;

   SDP sSdp;//codec storage
   
   void *pZrtpGlob;
   CTZrtpCb *zrtpCB;
   
   enum{eMaxMediaIDSCnt = 40};//TODO global max eng session cnt
   CTMediaIDS mediaIDS[eMaxMediaIDSCnt];
   
   CSessionsBase(PHONE_CFG &cfg):p_cfg(cfg){pZrtpGlob=NULL;zrtpCB=NULL;}
   void setCodecs(char *p, int id=SDP::eAudio);
   
   CTDataBuf onHoldMusic;//will repeat
   CTDataBuf putOnHoldMusic;//will not repeat
};


typedef struct{
   enum ECs{
      EClear=0, EInit=1,EOk=2,EEnding=3,EWaitUserAnswer=4,ESendError=5
   };
   int iInUse;
   
   ECs iCallStat;
   int iCallSubStat;
   
   enum{eTMRNull, eTMRRequired, eTMRSetuping, eConnecting, eTMRActive};
   int eTMRState;

   int iCaller;//if 1 i am inviter
   
   int iSendS;//move it to spSes
   int iWaitS;//move it to spSes
   
   int iIsOnHold;
   int iIsInConference;
   int iWhisperingTo;
   
}CALL_STAT;


class CRTPX;

class CTSesMediaBase: public CTMakeSdpBase{
   
public:
   /*
    inline int rtpHasPX(){return iRtpSource==0 || iRtpSource=='PX';}
    inline int rtpIsPC(){return iRtpSource=='PC' || iRtpSource=='TIVI';}
    inline int rtpIsMob(){return iRtpSource=='MOB';}
    */
   enum{eSourceUnknown, eSourcePX, eSourcePC, eSourceTivi, eSourceMob};
   
   int iIsActive;
   int iStarted;
   int iSdpSent;
   int iRTPSource;//rtppx,mob,pc 'PX','MOB','PC'
   
   
   unsigned int uiIPOrig,uiIPConn;
   int iPacketReceived;
   int iCantDecodePrevPack;
   int iPacketsDecoded;
   
   CSessionsBase *cbEng;//must never be NULL
   
   
   
   CTSesMediaBase(CSessionsBase &cbEng):cbEng(&cbEng){

      pCallStatus=NULL;
      cAO=NULL;
      clear();
   }
 
   void clear()
   {
      iFWDetected=0;
      iSdpParsed=0;
      sdes_or_zrtp=NULL;
      pMediaIDS=NULL;
      iRTPSource=0;
      iStarted=0;
      iSdpSent=0;
      iPacketReceived=0;

      iIsActive=0;
      uiIPOrig=uiIPConn=0;
      iCantDecodePrevPack=0;
      iPacketsDecoded=0;
   }
   enum{eAudio=1,eVideo=2, eFax=4, eDTMF=8, eFileTransf=16 };
   
   CTAudioOutBase *cAO;
   
   virtual const char *getMediaName()=0;
   inline int getMediaType(){return iType;}
   virtual int getInfo(const char *key, char *p, int iMax)=0;

   // Add functions to support reading of counter for statistic puposes
   /**
    * @brief Get required buffer size to get all 32-bit statistic counters of ZRTP
    * 
    * @return number of 32 bit integer elements required, or < 0 to indicate failure.
    */
   virtual int getNumberOfCountersZrtp()=0;

   /**
    * @brief Read statistic counters of ZRTP
    * 
    * @param buffer Pointer to buffer of 32-bit integers. The buffer must be able to
    *         hold at least getNumberOfCountersZrtp() 32-bit integers
    * 
    * @return number of 32-bit counters returned in buffer or -1 to indicate failure.
    */
   virtual int getCountersZrtp(int32_t* counters)=0;
   // End of statistic functions


   virtual  int isSesActive(){return 0;}
   virtual int onSdp(char *pSdp, int iLen, int iIsReq, int iForceMedia)=0;
   virtual int onSend(char *p, int iLen, int iType, void* pMediaParam, int iIsVoiceDetected){return -1;};//
   
   virtual int initTunneling()=0;

   virtual int onStart()=0;
   virtual int onStop()=0;//old
   virtual int onWillStop()=0;
   virtual void onRel()=0;
   virtual void onRecMsg(int id){};//bye ,180 ring,cancel
   virtual void onTimer(){}

   inline int rtpHasPX(){return iRTPSource==eSourceUnknown || iRTPSource==eSourcePX;}
   inline int rtpIsPC(){return iRTPSource==eSourcePC || iRTPSource==eSourceTivi;}
   inline int rtpIsMob(){return iRTPSource==eSourceMob;}
   
   virtual int checkNet()=0;
   
   int addNonMediaAttribs(char *p, int iMaxLen, int iIsVideo,
                           CRTPX *rtp, ADDR *addrPubl, ADDR *addrPriv, int iAddIce);
   
   
   void setBaseData(CTZRTP *z, CALL_STAT *cs, CTMediaIDS *m, int sdesonly){
       iIsSdesOnly=sdesonly;
      pMediaIDS=m;
      sdes_or_zrtp=z;
      pCallStatus=cs;
      iSdpParsed=0;
      
   }
   inline CTZRTP *getZRTP(){return sdes_or_zrtp;}
protected:
   
   int iFWDetected;
   
   int iType;
   CTMediaIDS *pMediaIDS;
   CTZRTP *sdes_or_zrtp;//SDES only, or SDES+ZRTP calls,
    int iIsSdesOnly;
   CALL_STAT *pCallStatus;
   int iSdpParsed;//--//
};
//
class CTSock;
class CTMediaBase{
public:
   //TODO find by sdp
   // virtual int setup(CTSesMediaBase *mb, int sesid);
   virtual CTSesMediaBase* findMedia(const char *p, int iLen)=0;
   virtual void release(CTSesMediaBase * m)=0;
   virtual CTSock *getMediaSocket(int id=0)=0;
   virtual void start()=0;
   virtual void stop()=0;
   virtual ~CTMediaBase(){};
   virtual void setNewPort(int i)=0;
   virtual void startSockets()=0;
   virtual void stopSockets()=0;
   
};

class CTMediaListItem;

class CTMediaMngrBase{
public:
   virtual CTAudioOutBase *getAO(int iRate)=0;
   virtual void relAO(CTAudioOutBase *p)=0;
   
   virtual int VOInUse()=0;
   
   virtual CTVideoOutBase *getVO()=0;
   virtual void relVO(CTVideoOutBase *p)=0;
   
   virtual CTVideoInBase &getVI()=0;
   virtual CTAudioInBase &getAI()=0;
   
   virtual CTMediaListItem *getMediaListItem(int iVideo)=0;
   virtual void relMediaListItem(CTMediaListItem *i, int iIsVideo)=0;
   
   //TODO getVO, relVO, CTAudioInBase
};

#endif //_C_TIVI_SES_H

