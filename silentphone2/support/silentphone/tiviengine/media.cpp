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

#ifdef __SYMBIAN32__
#include  <libc\ctype.h> //isalpha
#endif

#if defined(ANDROID_NDK) || defined(__APPLE__) ||  defined(__linux__)
#include  <ctype.h> //isalpha
#include  <math.h>
#else
void adbg(char *p, int v);

#endif

#include "CTPhMedia.h"
#include "../stun/CTStun.h"
#include "../encrypt/md5/md5.h"

#include "tivi_log.h"


#define T_MY_CN_PT_ID   13
#define T_MY_DTMF_PT_ID 101

#define T_CAN_USE_T_ICE 1

int get_time();
void tivi_log(const char* format, ...);
void tivi_log_scr(const char* format, ...);
int parseSDP(SDP *psdp, char *buf, int iLen);
int parseRTP(RTP_PACKET_P *recPacket, char *p);
int hasAttrib(SDP &sdp, const char *attr, int iType);
int t_snprintf(char *buf, int iMaxSize, const char *format, ...);
void log_zrtp(const char *tag, const char *buf);

#define IS_CN(_PT) (_PT==T_MY_CN_PT_ID || _PT==19)



static int iUseRtpQueue=1;
void setRtpQueue(int f){iUseRtpQueue=f;puts(f?"[setRtpQueue on]":"[setRtpQueue off]");}
int useRtpQueue(){return iUseRtpQueue;}

/*
 http://tools.ietf.org/html/rfc4571
 
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 ---------------------------------------------------------------
 |             LENGTH            |  RTP or RTCP packet ...       |
 ---------------------------------------------------------------
 
 v=0
 o=first 2520644554 2838152170 IN IP4 first.example.net
 s=Example
 t=0 0
 c=IN IP4 192.0.2.105
 m=audio 9 TCP/RTP/AVP 11
 a=setup:active
 a=connection:new
 
 Figure 3: TCP session description for the first participant
 
 v=0
 o=second 2520644554 2838152170 IN IP4 second.example.net
 s=Example
 t=0 0
 c=IN IP4 192.0.2.94
 m=audio 16112 TCP/RTP/AVP 10 11
 a=setup:passive
 a=connection:new
 */


/*
 INVITE sip:j@reg.tivi.lv SIP/2.0
 Via: SIP/2.0/UDP 192.168.199.x:4876;branch=z9hG4bKifbrChP;rport
 Contact: <sip:x@192.168.199.x:4876>
 Max-Forwards: 70
 From: <sip:x@reg.tivi.lv>;tag=1
 To: <sip:j@reg.tivi.lv>
 Content-Type: application/sdp
 Call-ID: abc_cid
 CSeq: 1 INVITE
 Content-Length: 668
 
 v=0
 o=- 09912 36673 IN IP4 46.109.y.x
 s=aa
 c=IN IP4 46.109.y.x
 t=0 0
 m=audio 32118 RTP/AVP 9 3 8 101
 a=rtpmap:101 telephone-event/8000
 a=ice-ufrag:B103D0
 a=ice-pwd:xxxxxxxxx
 a=candidate:tcRtefufbJZxcyrrS0pnAA== 1 UDP 2130706431 192.168.199.x 32118 typ host
 a=candidate:gc2tmF4oBILY4b2hJNu5gw== 1 UDP 1694498815 46.109.y.x 32118 typ srflx raddr 192.168.199.x rport 32118
 a=candidate:tcRtefufbJZxcyrrS0pnAA== 2 UDP 213070643
 */

#ifndef _WIN32
void add_tz_random_TC(){

   unsigned int ui=getTickCount();
   add_tz_random(&ui,4);
}
#endif


class CTLedCap{
   enum{ePack=10,eLim=1800/(50/ePack)};
   
   int iNPackets;
   unsigned int uiNPack;
   int brst;
   int iReset;
   int prev;
   int iPrevAuthFail;
   int iShowFailedAuth;
   
   void resetPackCounter(){
      iReset=iReset>0?(iReset-1):3;
      uiNPack=getTickCount();
      brst=0; iNPackets=0;
      iShowFailedAuth=0;
      if(iPrevAuthFail){
         iPrevAuthFail=0;
         iShowFailedAuth=1;
      }
   }
public:
   CTLedCap(){brst=0;iNPackets=0;prev=0;iPrevAuthFail=0;}
   void onPacketRecv(int iIsCN){
      if(iNPackets<ePack)iNPackets++;
      
      if(iNPackets>=ePack) {
         resetPackCounter();
      }
   }
   inline int getAuthFail(){return iShowFailedAuth;}
   void onRecvAuthFail(){
      iPrevAuthFail++;
   }
   int getCap(){
      prev=_getCap();
      return prev;
   }
private:
   int _getCap(){
      if(iReset>1){iReset--;return iReset==2?(prev/2):(prev/3);}
      if(iReset){iReset=0;return 90;}
      unsigned int tc=getTickCount();
      int d = tc-uiNPack;
      int iOk=iNPackets*20+30;
      if(!brst && iNPackets*8<ePack*5 && d<iOk)return 100;
      brst=1;
      
      if(d>eLim)return 0;
      
      int e = d - iOk ;
      if(e>0){e>>=1;if(e>96)return 0;return 96-e;}
      
      return (eLim+20-d)>>1;
   }
};

CTLedCap ledCap;

int g_getCap(int &iIsCN, int &iIsVoice, int &iPrevAuthFail){
   iIsCN=0;
   iIsVoice=1;
   iPrevAuthFail=ledCap.getAuthFail();
   return ledCap.getCap();
}


typedef struct{
   const char *p;
   int id;
}T_C;

static const T_C p_clist[]={
   {"GSM",3},{"G.711a",8},{"G.711u",0},{"G.722",9},
   {"G.729",18},{"CN",13},{"CN",19},{"H263",34},
   {NULL,-1}
};

const char *codecID_to_sz(int id){
   
   for(int i=0;;i++){
      if(p_clist[i].id==-1)return NULL;
      if(p_clist[i].id==id)
         return p_clist[i].p;
   }
   
   return NULL;
}

const char *codecID_to_sz_safe(int id){
   const char *r=codecID_to_sz(id);
   return r?r:"unkn";
}
int codecSZ_to_ID(const char *p){
   
   for(int i=0;;i++){
      if(p_clist[i].id==-1)return -1;
      if(strcmp(p_clist[i].p,p)==0)
         return p_clist[i].id;
   }
   
   return -1;
}

int createTIceCandidates(ADDR *priv, ADDR *publ, char *pOut, int iMaxLen, const char *clinetType){
   char b1[ADDR::eMaxDNS_SIZE];
   char b2[ADDR::eMaxDNS_SIZE];
   if(iMaxLen<50)return 0;
   
   publ->toStr(b1,1);
   const char *natType = "unkn";//TODO get type
   if(*publ == *priv){
      return snprintf(pOut, iMaxLen-1, "a=t-sc-ice:v1.1 %s %s udp4 %s\r\n",clinetType, natType, b1);
   }
   priv->toStr(b2,1);
   return snprintf(pOut, iMaxLen-1, "a=t-sc-ice:v1.1 %s %s udp4 %s %s\r\n",clinetType, natType, b1,b2);
}


CTAudioOutBase *findAOByMB(CTSesMediaBase *mb){
   if(mb->getMediaType()&CTSesMediaBase::eVideo){
      CRTPV *v=(CRTPV *)mb;
      return v->cAO;
   }
   CRTPA *a=(CRTPA *)mb;
   return a->cAO;
}
//TODO fix
//TODO define g_max_audio_calls
//CTMediaIDS mediaIDS[20];
//mutex medids
void releaseMediaIDS(CSessionsBase *sb, CTMediaIDS *p, void *pThis){
   

   CTMediaIDS *mf=&sb->mediaIDS[0];
   for(int i=0;i<sb->eMaxMediaIDSCnt;i++,mf++){
      if(p==mf){
         if(p->pOwner==pThis){
            p->release();
            break;
         }
         else{
            //TODO log
            log_events( __FUNCTION__,"[ERR: releaseMediaIDS]");
         }
      }
   }
}

CTMediaIDS* initMediaIDS(void *pThis, int iCallId, CSessionsBase *sb, int iCaller){
   CTMediaIDS *p=NULL;
   CTMediaIDS *mf=&sb->mediaIDS[0];
   
   for(int i=0;i<CSessionsBase::eMaxMediaIDSCnt;i++,mf++){
      if(!mf->pOwner){
         p=mf;
         p->pOwner=pThis;
         ////if you ask why md5 ??? read (RFC 3550)
         CTMd5 md5;
         int t=(int)getTickCount();
         int x=(size_t)pThis;
         
         md5.update((unsigned char *)&x,4);
         md5.update((unsigned char *)&sb->p_cfg,sizeof(sb->p_cfg));
         md5.update((unsigned char *)&sb->uiGT,sizeof(sb->uiGT));
         md5.update((unsigned char *)&t,4);
         
         md5.update((unsigned char *)&sb->extAddr.ip,4);
         md5.update((unsigned char *)&sb->ipBinded,4);
         md5.update((unsigned char *)&sb->iRandomCounter,4);
         unsigned int res[4];
         md5.final((unsigned char*)&res[0]);//generate RTP SSRC
         sb->iRandomCounter+=res[0];
         sb->iRandomCounter^=res[1];
         
         if(!res[2])res[2]^=res[0];
         if(!res[3])res[3]^=res[1];
         
         p->init(res[2],res[3]);
      
         p->initZRTP(sb->zrtpCB,sb->pZrtpGlob);
         if(p->pzrtp){
            p->pzrtp->init_zrtp(iCaller,sb->p_cfg.szZID_base16,iCallId,1,1);
            p->pzrtp->pSes=pThis;
         }

         CTDataBuf *g_getSound(const char *name);
         
         p->holdSounds.init(g_getSound("putOnHoldSnd"), g_getSound("onHoldSnd"));
         
         break;
      }
   }
   return p;
}

int CTRecSendUDP::start(const CTSockRecvCB &sockCBRecv, ADDR *addrToBind)
{
   iRun=1;
   int iTrys=10;
   do{
      int cr=createSock();
      if(!cr){
         perror("create media sock failed");
         Sleep(20);
         iTrys--;
         if(iTrys>0)continue;
      }
   }while(0);
   
   Bind(addrToBind,1);
   CTSockRecvCB old(NULL,NULL);
   setCB(sockCBRecv,old);
   strcpy(this->thName,"_media");
   create(&CTRecSendUDP::thFnc,this);
   
   return 0;
}

int forceFWTraversal();
int CTRecSendUDP::thFnc(void *p1)
{

   CTRecSendUDP *p=(CTRecSendUDP *)p1;
   p->iClosed=0;

   ADDR a;
#ifdef __SYMBIAN32__
   RThread().SetPriority(EPriorityRealTime);
#endif

#if (defined(_WIN32_WCE) ||  defined(_WIN32) ||  defined(_WIN64)) && !defined(__SYMBIAN32__)
   SetThreadPriority(GetCurrentThread(),THREAD_PRIORITY_ABOVE_NORMAL);
#endif
   
   int iRecvBufSize=4096;
   char *bufx=new char[iRecvBufSize];

   while(p->iRun)
   {
    //  printf("[listen %x:%d %d]",p->addr.ip,p->addr.getPort(),i);
      //p->recvFrom(bufx, iRecvBufSize, &a);
      if(forceFWTraversal())Sleep(500);else
      p->recvFrom(bufx, iRecvBufSize, &a);
   }
   
   delete bufx;
   
   return 0;

}

int CRTPX::parseRtp(char *p, int iLen){
   if(iLen<12)return -1;
   rtpRec.allPack.len=iLen;
   return parseRTP(&rtpRec,p);
}
int CRTPX::makeRTP(int iPt, CTMediaIDS::MEDIA *m){
   
   RTP_PACKET_S* sendPacket=&rtpSend;
   
   rtpSend.ssrc=m->uiSSRC;

   int iPrevPTSend=sendPacket->pt;

   if(iPt)
      sendPacket->pt=iPt;

   m->seq++;
   sendPacket->seqNr=m->seq;
   sendPacket->marker=(sendPacket->pt!=uiPrevSendRTPpt)?1:0;
  
   sendPacket->dataBuf[0]=((char *)sendPacket)[0];
   sendPacket->dataBuf[1]=((char *)sendPacket)[1];
   sendPacket->dataBuf[2]=((char *)sendPacket)[3];
   sendPacket->dataBuf[3]=((char *)sendPacket)[2];
   sendPacket->dataBuf[4]=((char *)sendPacket)[7];
   sendPacket->dataBuf[5]=((char *)sendPacket)[6];
   sendPacket->dataBuf[6]=((char *)sendPacket)[5];
   sendPacket->dataBuf[7]=((char *)sendPacket)[4];
   sendPacket->dataBuf[8]=((char *)sendPacket)[11];
   sendPacket->dataBuf[9]=((char *)sendPacket)[10];
   sendPacket->dataBuf[10]=((char *)sendPacket)[9];
   sendPacket->dataBuf[11]=((char *)sendPacket)[8];

   uiPrevSendRTPpt=rtpSend.pt;

   sendPacket->pt=iPrevPTSend;

   return 0;
}

int CRTPX::setSendCodec(int eType, CSessionsBase &eng)
{
   //rtpSend.pt=3;
   int i,j;
   //TODO if is one on slow net , then reduse video bps
   int iMyFirstIsLowBitrate = eng.sSdp.u.codec[0].uiID==3 || eng.sSdp.u.codec[0].uiID==18;
   int iHisFirstIsLowBitrate = codec[0].uiID==3 || codec[0].uiID==18;
   int iIsVideo = eType!=SDP::eAudio;
   iCodecsMatching[iIsVideo]=0;
   
   if(iMyFirstIsLowBitrate || !iHisFirstIsLowBitrate){
      
      for (j=0;j<eng.sSdp.codecCount;j++)
      {
         if(eType!=eng.sSdp.u.codec[j].eType)
            continue;
         
         for (i=0;i<iCodecCnt;i++)
         {
            if(eType!=codec[i].eType)
               continue;
            
            if(codec[i].uiID==T_MY_DTMF_PT_ID)continue;
            if(IS_CN(codec[i].uiID)){
               iCNFound=1;
               continue;//DTMF
            }
            
            if (codec[i].uiID==eng.sSdp.u.codec[j].uiID){

               codecMatches[iIsVideo][iCodecsMatching[iIsVideo]]=codec[i].uiID;
               iCodecsMatching[iIsVideo]++;
            }
         }
      } 
   }
   else{//iHisFirstIsLowBitrate
      for (i=0;i<iCodecCnt;i++)
      {

         if(eType!=codec[i].eType)
            continue;         
         for (j=0;j<eng.sSdp.codecCount;j++)
         {
            if(eType!=eng.sSdp.u.codec[j].eType)
               continue;
            
            if(codec[i].uiID==T_MY_DTMF_PT_ID)continue;
            if(IS_CN(codec[i].uiID)){
               iCNFound=1;
               continue;//DTMF
            }

            
            
            if (codec[i].uiID==eng.sSdp.u.codec[j].uiID)
            {
             //  if(iCodecsMatching==0)rtpSend.pt=codec[i].uiID;
               codecMatches[iIsVideo][iCodecsMatching[iIsVideo]]=codec[i].uiID;
               iCodecsMatching[iIsVideo]++;

            }
         }
      } 
   }
   rtpSend.pt=codecMatches[iIsVideo][0];
   printf("[set send pt=%d]",rtpSend.pt);
   
   //add CN and DTMF 
   if(!iIsVideo && iCodecsMatching[iIsVideo]>0){
      for (i=0;i<iCodecCnt;i++)
      {
         for (j=0;j<eng.sSdp.codecCount;j++)
         {
            if(eType!=eng.sSdp.u.codec[j].eType)
               continue;
            
            if(!IS_CN(eng.sSdp.u.codec[j].uiID) && eng.sSdp.u.codec[j].uiID!=T_MY_DTMF_PT_ID)continue;
            
            if (codec[i].uiID==eng.sSdp.u.codec[j].uiID)
            {
               codecMatches[iIsVideo][iCodecsMatching[iIsVideo]]=codec[i].uiID;
               iCodecsMatching[iIsVideo]++;
            }
         }
      }
   }
   
   return iCodecsMatching[iIsVideo]>0?0:-1;
}


void CRTPX::setHdr(unsigned int uiSSRC)
{
   if(rtpSend.ssrc==0)
   {

      rtpSend.ssrc=uiSSRC;
      
      rtpSend.ts=0;
      rtpSend.version=2;
      rtpSend.hdrLen=12;
      rtpSend.csrcCount=0;
      rtpSend.padding=0;
      rtpSend.marker=0;
      rtpSend.extension=0;
   }
}

int CRTPX::onSdp(SDP &sdp, int eType, CSessionsBase &eng)
{

   int media_id=findMediaId(&sdp,eType,0);

   if(media_id==-1)return -10;

   if(!sdp.media[media_id].ipConnect)
   {
      sdp.media[media_id].ipConnect=sdp.ipOrigin;
   }
   if(eng.extAddr.ip==sdp.media[media_id].ipConnect)
   {
      addrDst.ip=sdp.ipOrigin;
      uiIP2=sdp.media[media_id].ipConnect;
   }
   else
   {
      addrDst.ip=sdp.media[media_id].ipConnect;
      uiIP2=sdp.ipOrigin;
   }
   
   addrDst.setPort(sdp.media[media_id].port);

   uiIP2=sdp.ipOrigin;

   uiSSRC=sdp.media[media_id].uiSSRC;
   iCodecCnt=0;//sdp.codecCount;
   int i;//=sdp.codecCount;
   
   //TODO get media ports media_id=findMediaId(&sdp,eType,1);
   
   for(i=0;i<sdp.codecCount;i++)
   {
      if(sdp.u.codec[i].eType==eType)
      {
         memcpy(&codec[iCodecCnt],&sdp.u.codec[i],sizeof(struct CODECS));
         iCodecCnt++;
      }

   }

   return setSendCodec(eType, eng);
}

static int getInfoRTPX(CRTPX *rtpx, const char *key, int l, char *p, int iMax){
   
#define CMP_CSZ(_V) l+1==sizeof(_V) && strcmp(key,_V)==0
   if(iMax>1)iMax--;
   
   if(CMP_CSZ( "peer_ssrc")){
      return snprintf(p, iMax, "%u", rtpx->uiSSRC);
   }
   if(CMP_CSZ("p2p_addr")){
      if(iMax<63)return -1;
      ADDR *p2p = rtpx->ice.getBestCandidateForTMR();
      if(!p2p)return -2;
      p2p->toStr(p,1);
      return strlen(p);
   }
   
   if(CMP_CSZ ("peer_sdp_addr")){
      if(iMax<63)return -1;
      rtpx->addrDst.toStr(p,1);
      return strlen(p);
   }
   
   return 0;
}

int CRTPV::getInfo(const char *key, char *p, int iMax){
   if(!key)return 0;
   
   int l = strlen(key);
   
   if(l>6){
      if(strncmp(key,"video.", 6)==0){
         printf("[getvideo=%s]\n",key);
         int r = getInfoRTPX(&rtpV, key+6, l-6, p, iMax);
         if(r)return r;
      }
   }
   

   return CRTPA::getInfo(key, p, iMax);
}

int CRTPA::getInfo(const char *key, char *p, int iMax){
   p[0]=0;
   int ret=0;
   
   if(!key)return 0; //or get all
   int iIsVideo=0;
   int l=strlen(key);
   if(iMax>1)iMax--;
   if(l>6){
      if(strncmp(key,"video.", 6)==0){
         key+=6;l-=6;
         iIsVideo = 1;
      }
      else if(strncmp(key,"audio.", 6)==0){
         key+=6;l-=6;
         iIsVideo = 0;
      }
   }
#define CMP_CSZ(_V) l+1==sizeof(_V) && strcmp(key,_V)==0
   
   if(!iIsVideo){
      int r = getInfoRTPX(&rtp, key, l, p, iMax);
      if(r)return r;
   }
   
   if(!iStarted)return 0;
   
   if(l>5 && memcmp(key,"zrtp.",5)==0){
      return pzrtp?pzrtp->getInfoX(key+5,p,iMax,iIsVideo):0;
   }
   
   
   
   if(l==6 && strcmp(key,"codecs")==0){
      CTAudioOutBase *a=cAO;
      ret=0;
      if(a){
         static int infoSwich;//debuging SRTP roc
         static int sw;
         sw++;
         if(pMediaIDS->tmrTunnel && sw&1){
            infoSwich++;
         }
         if(pMediaIDS->tmrTunnel){
            /*
            if((infoSwich&3)==1)
               ret+=sprintf(p+ret,"r%04x ",rtp.rtpRec.seqNr);
            else if((infoSwich&3)==2)
               ret+=sprintf(p+ret,"s%04x ",rtp.rtpSend.seqNr);
            else */
               ret+=sprintf(p+ret,"FW%d ", pMediaIDS->tmrTunnel->audio.tmr_info.getMedRT());
            
         }else if(rtp.ice.canUseP2PNow()){
            ret+=sprintf(p+ret,"%d ", rtp.ice.pingTime());
         }
         ret+=a->msg("delay",5, (char*)p+ret, iMax-ret);
         ret+=sprintf(p+ret," ");
         int rPrev=ret;
         ret+=a->msg("lost",4, (char*)p+ret, iMax-ret);
         if(rPrev!=ret)ret+=sprintf(p+ret," ");
      }
      
      if(rtp.iPrevPT==rtp.rtpSend.pt && iPacketsDecoded){         
         ret+=snprintf(p+ret,iMax-ret,"%s",codecID_to_sz_safe(rtp.rtpSend.pt));
      }
      else {
         ret+=snprintf(p+ret,iMax-ret,"%s",codecID_to_sz_safe(rtp.rtpSend.pt));
         if(!iPacketReceived){
            if(iCantDecodePrevPack)
               ret+=snprintf(p+ret,iMax-ret,"/e%d%d",iCantDecodePrevPack,!!iPacketsDecoded);
            else
               ret+=snprintf(p+ret,iMax-ret,"/noRX");
         }
         else
            ret+=snprintf(p+ret,iMax-ret,"/%s",codecID_to_sz_safe(rtp.iPrevPT));
      }
      
      if(pzrtp && pzrtp->iAuthFailCnt)
         ret+=snprintf(p+ret,iMax-ret," %d",pzrtp->iAuthFailCnt);
      
      p[iMax]=0;
      
      unsigned int uiTCNow = getTickCount();
      static unsigned int _uiT = 0;
      int d = (int)(uiTCNow - _uiT);
      
      
      if(d > 6000 || d<0){ //6000 = 6 seconds
         
         t_logf(log_audio_stats,__FUNCTION__,"%p %s",getEncryptedPtr_debug(this), p);
         _uiT = uiTCNow;
      }
      
      //p_cfg.iIndex
      
      return ret;
   }
   else if(strcmp(key, "P-RTP-Stat")==0){
      CTAudioOutBase *a=cAO;
      ret = t_snprintf(p, iMax, "PS=%u,PR=%u,PL=%u",rtp.packetsSent,rtp.packetsRec, rtp.rtpPackLost);
   
      if(rtp.ice.canUseP2PNow()){
         ret+=t_snprintf(p+ret, iMax-ret, ",LA=%d",rtp.ice.pingTime());
      }
      
      if(a){
         ret+=t_snprintf(p+ret, iMax-ret, ",JI=");
         ret+=a->msg("jit",3, (char*)p+ret, iMax-ret);
      }
      return ret;
   }
   else {
      CTAudioOutBase *a=cAO;
      if(a){
         ret=a->msg(key,l, (char*)p, iMax);
         p[iMax]=0;
         return ret;
      }
   }
   
   return -1;
}


int CRTPA::onSend(char *p, int iLen, int iCurType, void* pMediaParam, int iIsVoice)
{
   if((iCurType&(eAudio|eDTMF))==0)return -1;
 
   if(rtp.addrDst.ip==0)return 0;
   if(rtp.rtpSend.ssrc==0)return 0; //??
   
   int sz=cbEng->p_cfg.payloadSize();
   
   //payloadSize can be msec or 20msec packets per block
   if(sz>20)sz/=20;
   sz*=20;
   if(sz>20)sz=20;
   
   int r=cAI.getRate();
    
   const int iPartSize=320*r/8000;//20 ms in bytes
   
   char tmpAudioBuf[1400];
   
   if(!rtp.iCNFound && !p){
      p=&tmpAudioBuf[0];
      iLen=iPartSize;
      memset(p, 0, iLen);
      iIsVoice=-1;
   }
   
   //dont split if packsize is less then 1K
   if(iLen<=iPartSize || iLen<=640*r/8000 || p==NULL ||(iCurType&eDTMF) || iIsVoice<=0 || (rtp.rtpSend.pt!=0 && rtp.rtpSend.pt!=8 && rtp.rtpSend.pt!=9))
      return onSendUAlawSpliter(p,iLen,iLen?iLen:iPartSize,iCurType,pMediaParam,iIsVoice);
   
   cAI.packetTime(sz);

   unsigned int ts=(size_t)pMediaParam;
   int i,iBSent=0;
   for(i=0;i<iLen;i+=iPartSize,p+=iPartSize,ts+=(unsigned int)iPartSize)
   {
      iBSent+=onSendUAlawSpliter(p,iPartSize,iPartSize,iCurType,(void*)ts,iIsVoice);
   }
   return iBSent;
}

char *CRTPA::getOnHoldSound(int &iLen, int iPartSize, unsigned int ts, int &iStaysOnHold){
   //check hold init beep sound
   //check hold repeat sound
   //TODO use encode sound
   if(pMediaIDS->holdSounds.isEmpty())return NULL;
   //TODO wait for secure
   char *p = pMediaIDS->holdSounds.get(iPartSize);
   
   if(p){iStaysOnHold=0;iLen = iPartSize;}
   
   return p;
}

int CRTPA::onSendUAlawSpliter(char *p, int iLen, int iPartSize, int iCurType, void* pMediaParam, int iIsVoice)
{
   if(iWillStop)return 0;
   
   CCodecBase *c=NULL;
   int iRet=0;
   switch(rtp.rtpSend.pt)
   {
      case 0:  c=&cb.gUlaw; break;
      case 3:
         c=&gEnc;
         cbEng->setNeedsVad(this);
         break;
      case 8:  c=&cb.gAlaw; break;
      case 9:  c=&g722enc;  break;
      case 18: c=&g729Enc;  break;
      default:
         c=&cb.gUlaw;
         iRet=1;
   }
   
   c->setEncoderInSRate(cAI.getRate());
   
   int dTS;
   
   if(pMediaParam)
   {
      int ts=c->getTSMult()*2;
      iMediaSent=1;
      unsigned int nTS =(size_t)pMediaParam/ts+uiTimeStampOffset;
      dTS = (int)(nTS-rtp.rtpSend.ts);
      rtp.rtpSend.ts=nTS;
   }
   else
   {
      uiTimeStampOffset=(getTickCount()/10)*80*c->getTSMult();
      dTS =  (int)(uiTimeStampOffset-rtp.rtpSend.ts);
      rtp.rtpSend.ts=uiTimeStampOffset;
   }
   const int iIsSmartVad = 1;

   int iOnHold=pCallStatus && pCallStatus->iIsOnHold && !pCallStatus->iIsInConference;
   
   CTAudioOutBase *cAOTmp=cAO;
   
   int iMute = 0;//TODO read a mute state for a call - pCallStatus->iMuted;
   
   pMediaIDS->holdSounds.callState(iOnHold, pzrtp->getStatus(0)==pzrtp->eSecure);
   
   if(iOnHold){
      p = getOnHoldSound(iLen, iPartSize, rtp.rtpSend.ts, iOnHold);
      if(p) {iIsVoice = 1;}
   }
   //printf("[send media = %.3f]\n",(float)getTickCount()/1000.);
   
   if(iOnHold || iMute){//
      //if cn supported
      iCNSendCounter++;
      if(iCNSendCounter>4 && dTS<2000) { if(iCNSendCounter>8)iCNSendCounter=3;return 0;}
      
      rtp.rtpSend.allPack.len=13;
      rtp.rtpSend.dataBuf[12]=50;
      rtp.makeRTP(T_MY_CN_PT_ID,&pMediaIDS->m[0]);//&rtpSend,13,spSes);

   }
   else if(iCurType & eDTMF)
   {
      iCNSendCounter=0;
      
      rtp.rtpSend.allPack.len=12+4;
      rtp.rtpSend.dataBuf[12]=p[0];
      rtp.rtpSend.dataBuf[13]=p[1];
      rtp.rtpSend.dataBuf[14]=p[2];
      rtp.rtpSend.dataBuf[15]=p[3];
      rtp.makeRTP(T_MY_DTMF_PT_ID,&pMediaIDS->m[0]);
   }
   else if( p==NULL || iLen==0 || (iIsVoice<=0 && (!iIsSmartVad || !cAOTmp || cAOTmp->isPrevPackSpeech()>-10)))
   {
      if(iIsVoice==0 || (p==NULL || iLen==0)){
         
         iCNSendCounter++;
         if(iCNSendCounter>4 && dTS<2000) { if(iCNSendCounter>8)iCNSendCounter=3;return 0;}
         
         rtp.rtpSend.allPack.len=13;
         rtp.rtpSend.dataBuf[12]=60;
         rtp.makeRTP(T_MY_CN_PT_ID,&pMediaIDS->m[0]);//&rtpSend,13,spSes);
      }
      else return 0;
      //puts("send ")
   }
   else
   {
      if(iRet)return 0;
      iCNSendCounter=0;

      static const unsigned char bz_729[]={ 120, 82, 128, 160, 0, 250, 208, 0, 0, 86, 120, 82, 128, 160, 0, 250, 208, 0, 0, 86};
      
      if(rtp.iCanResetMedia && p==NULL)
      {
         char bufx[320];//TODO in rtp audio
         iLen=320;
         p=(char*)&bufx;
         memset(p,0,320);

         if(rtp.rtpSend.pt==18)
         {

            memcpy(&rtp.rtpSend.dataBuf[rtp.rtpSend.hdrLen],bz_729,20);
            rtp.rtpSend.data.len=20;
         }
         else
         {
            rtp.rtpSend.data.len=c->encode((short *)p,rtp.rtpSend.dataBuf+rtp.rtpSend.hdrLen,320*c->getTSMult());//iLen);//i*33;
         }
         int i;
         int a=0;
         for(i=320;i<iLen;i+=320)
         {
            memcpy(&rtp.rtpSend.dataBuf[rtp.rtpSend.hdrLen+rtp.rtpSend.data.len*(a+1)]
                ,&rtp.rtpSend.dataBuf[rtp.rtpSend.hdrLen],rtp.rtpSend.data.len);
            a++;
         }
         rtp.rtpSend.data.len*=(a+1);
      }
      else
      {
         if(rtp.rtpSend.pt==18 && p[159]=='9' && p[158]=='2'  && p[157]=='7'&& p[156]=='G')//hardware encoder
         {
            int i;
            int packs=iLen/160;
            for(i=0;i<packs;i++)
            {
               if(p[160*i+0]==0 && p[160*i+1]==0 && p[160*i+2]==0 && p[160*i+3]==0 && p[160*i+4]==0 && p[160*i+5]==0)
               {
                  memcpy(&rtp.rtpSend.dataBuf[10*i+rtp.rtpSend.hdrLen],bz_729,20);
               }
               else
               {
                  memcpy(&rtp.rtpSend.dataBuf[10*i+rtp.rtpSend.hdrLen],&p[160*i],10);
               }
            }
            rtp.rtpSend.data.len=packs*10;
         }
         else 
           rtp.rtpSend.data.len=c->encode((short *)p,rtp.rtpSend.dataBuf+rtp.rtpSend.hdrLen,iLen);//i*33;
      }
      rtp.rtpSend.allPack.len=rtp.rtpSend.data.len+rtp.rtpSend.hdrLen;

      rtp.makeRTP(0,&pMediaIDS->m[0]);
   }

   return tryEncryptSendPack(&rtp, sockth, 0);
}

int CRTPA::sendPacket(CTSock *s, char *p, unsigned int uiLen, ADDR *a, int iIsVideo){

   rtp.packetsSent++;
   if(pCallStatus->eTMRState && pMediaIDS && pMediaIDS->tmrTunnel){
      //TODO video
      return pMediaIDS->tmrTunnel->sendRTP((unsigned char *)p, (int)uiLen, iIsVideo);
   }
   s->sendTo(p,uiLen,a);
   return 0;
}


void CRTPA::sendRtp(CtZrtpSession const *session, uint8_t* packet, size_t length, CtZrtpSession::streamName streamNm){
   
    t_logf(log_zrtp,  __FUNCTION__,"zrtp CRTPA send cb %d %d l=%ld]", iStarted, streamNm, (long int)length);
  ////zrtp-error-tester
  // if(length<120 && length>60){puts("DROP");return;}
   if(!iStarted)return;
  // static int x;x++;if((x&3)==1){puts("drop zrtp");return;}
   
   if(streamNm!=CtZrtpSession::AudioStream){
      puts("send streamNm!=CtZrtpSession::AudioStream");
      return;
   }
   
   sendPacket(sockth, (char*)packet, (int)length, &rtp.addrDst,0);
   
}

void CRTPV::sendRtp(CtZrtpSession const *session, uint8_t* packet, size_t length, CtZrtpSession::streamName streamNm){
   
   
   if(!iStarted)return;
   
   if(streamNm==CtZrtpSession::AudioStream){
      CRTPA::sendPacket(sockth, (char*)packet, (int)length,&rtp.addrDst,0);
      return;
   }
   //zrtp-error-tester
   //if(length<120 && length>60){puts("DROP");return;}
   
   ADDR *a=&rtpV.addrDst;
   int r = sendPacket(sockth, (char*)packet, (int)length,a,1);
  // char b[64];a->toStr(b); printf("[zrtp CRTPV send cb %d %d %s [%x l=%lu=%d]]",iStarted,streamNm, b, packet[0],length,r );
}



int CRTPA::onRcvRTP(void *pThis, unsigned char *pack, int iLen, ADDR *aFrom, int iIsVideo){
  // printf("[vv0=%d l=%d]\n",iIsVideo, iLen);
   return onData((char *)pack, iLen, aFrom);
}

int CRTPV::onRcvRTP(void *pThis, unsigned char *pack, int iLen, ADDR *aFrom, int iIsVideo){
  // printf("[v=%d l=%d]\n",iIsVideo, iLen);
   if(iIsVideo)return onData((char *)pack, iLen, aFrom);
   
   int r = CRTPA::onData((char *)pack, iLen, aFrom);
   if(r<0 || r==CRTPX::eRtpNotMine)return onData((char *)pack, iLen, aFrom);
   return r;
}

int CRTPA::onData(char *p, int iLen, ADDR *a)
{
   int ret;
  
   if(!iSdpSent || !iSdpParsed)return CRTPX::eRtpNotMine;
   
   ret=rtp.onRtp(p,iLen,a, cbEng->p_cfg.iPermitSSRCChange); // ?? iPermitSSRCChange - some sip servers require it
   if(ret==CRTPX::eRtpIce)return CRTPX::eRtpOk;
   if(ret<0)return ret;

   CCodecBase *codec=NULL;
   ret=0;
   
   switch(rtp.rtpRec.pt)
   {
      //opus
      case 0:  codec=&uLawDec; break;
      case 3:
         codec=&gDec;
         cbEng->setNeedsVad(this);
         break;
      case 8:  codec=&aLawDec; break;
      case 9:  codec=&g722dec; break;   
      case 18: codec=&g729Dec; break;

      case T_MY_CN_PT_ID:
      case 19:
         ret=1;
         break ;
      case T_MY_DTMF_PT_ID:
         return 0;
      default:
         ret=CRTPX::eRtpNotMine;
   }
   if(ret<0)
      return ret;
 
 // printf("v%d pt%d ts%u seq%d %x\n",rtp.rtpRec.version, rtp.rtpRec.pt, rtp.rtpRec.ts, rtp.rtpRec.seqNr, rtp.rtpRec.ssrc);

   CTAudioOutBase *cAOtmp=cAO;//Important: cAO can be set to NULL in other threads, but released later
   
   if(!cAOtmp || !iIsActive)return CRTPX::eRtpNotMine;

   if(cbEng->p_cfg.iIsValidZRTPKey==1 && cbEng->p_cfg.iCanUseZRTP==3)cbEng->p_cfg.iCanUseZRTP=2;
   if(!cbEng->p_cfg.iIsValidZRTPKey && cbEng->p_cfg.iCanUseZRTP>1)cbEng->p_cfg.iCanUseZRTP=1;
   pzrtp->iCanUseZRTP=cbEng->p_cfg.iCanUseZRTP;
   
   
   //if (FS sends media to my ip port from prev call) ignore it
   if(rtp.uiSSRC_toIgnore && rtp.uiSSRC_toIgnore==rtp.rtpRec.ssrc && !rtp.uiSSRC){
      return CRTPX::eRtpNotMine;
   }
   /*
   
   if(p[3]!=p[iLen-1]){
      printf("[err seq=%d prev_ok_seq=%d %d %d %d %d]\n",rtp.rtpRec.seqNr,rtp.iPrevId,p[3]!=p[iLen-2],p[3]!=p[iLen-3],p[3]!=p[iLen-4],p[3]!=p[iLen-5]);
      
   }
   else {
      printf("[ok seq=%d prev_ok_seq=%d]\n",rtp.rtpRec.seqNr,rtp.iPrevId);
   }
    */
 
   {
      if(iWillStop)return 0;
      
   //   printf("[seq=%d prev_ok_seq=%d]\n",rtp.rtpRec.seqNr,rtp.iPrevId);
      
      pzrtp->setZrtpEnabled(!!pzrtp->iCanUseZRTP);
      
      pzrtp->startIfNotStarted(rtp.rtpSend.ssrc,0);
      int iLenNew=iLen;
      int rr=pzrtp->decrypt(p,iLenNew,0);
      if(rr==CTZRTP::eIsProtocol){
         return 0;//protcol
      }
      if(rr==CTZRTP::eDropPacket || rr==CTZRTP::eAuthFailPacket){
         
         if(1)
         {
            char b[64];
            sprintf(b, "a-srtp-fail pt=%d seq=%d prev_ok_seq=%d",rtp.rtpRec.pt, rtp.rtpRec.seqNr, rtp.iPrevId);
            log_zrtp("t_zrtp",b);//5394 5463
         }
         
         if(iPacketsDecoded==0){
            rtp.uiSSRC_toIgnore=rtp.rtpRec.ssrc;
            iPacketReceived=0;
            rtp.iCanResetMedia=10;
         }
         if(rr==CTZRTP::eAuthFailPacket){
            ledCap.onPacketRecv(IS_CN(rtp.rtpRec.pt));
            ledCap.onRecvAuthFail();
         }
         return 0;//?? CRTPX::eRtpNotMine
      }
      if(rr==CTZRTP::ePacketError)return CRTPX::eRtpNotMine;
      
      rtp.rtpRec.data.len+=(unsigned int)(iLenNew-iLen);
      iLen=iLenNew;
   }
   if(rtp.rtpRec.version!=2)return CRTPX::eRtpBad;
   
   if(codec)prevRecCodec=codec;

   CCodecBase *c=codec?codec:(prevRecCodec?prevRecCodec:&uLawDec);
   
   c->setDecoderOutSRate(cAOtmp->getRate());
   
   
   ledCap.onPacketRecv(IS_CN(rtp.rtpRec.pt));
   
   //--------------------
   
   if(iPacketReceived && (unsigned short)rtp.iPrevId==(unsigned short)rtp.rtpRec.seqNr)return 0;//move it up?
   
   
   if(pzrtp->getStatus(0)==pzrtp->eSecure && rtp.ice.active()){
      //if (we are not sending data )set_ice_params
      rtp.ice.setPeerData(sockth, rtp.rtpSend.ssrc, rtp.rtpRec.ssrc);
   }


   unsigned int uiPos=rtp.rtpRec.ts*2*c->getTSMult(); // *2 conv to bytes,

   unsigned int uiPrevSSRC = rtp.uiSSRC;
   unsigned int uiPrevIP2 = rtp.uiIP2;
   
   rtp.uiSSRC = rtp.rtpRec.ssrc;
   ADDR aPrev = rtp.addrDst;
   
   int isP2PAddr=rtp.ice.isP2PAddr(a);

   rtp.uiIP2=a->ip;
   if(rtp.addrDst!=*a){
     if(rtp.iCanResetMedia>0)rtp.iCanResetMedia--;
   }
   else rtp.iCanResetMedia=0;
   

   
   if(!isP2PAddr && rtp.iCanResetMedia>0){
      //what if rtp.addrDst!=*a 
      rtp.addrDst=*a;
   }
   

   int iResetPlayPos = IS_CN(rtp.iPrevPT) && !IS_CN(rtp.rtpRec.pt);


   rtp.iPrevPT=rtp.rtpRec.pt;
   rtp.iLost=iPacketReceived && (unsigned short)rtp.iPrevId+1!=(unsigned short)rtp.rtpRec.seqNr;//seqErr
   int iReceivedPrev=(unsigned short)rtp.iPrevId>(unsigned short)rtp.rtpRec.seqNr;
   int iLostCnt=0;
   if(rtp.iLost){
      iLostCnt=rtp.rtpRec.seqNr-rtp.iPrevId;
      if(iLostCnt<0 || iLostCnt>500)iLostCnt=1;
   }
   
   if(iReceivedPrev){
      printf("[order err %d %d]",rtp.iPrevId,rtp.rtpRec.seqNr);
   }
   
   iPacketReceived=1;


   rtp.iPrevId=rtp.rtpRec.seqNr;
   //--------------------
   
   cAOtmp->receivedTS(uiPos,iLostCnt,IS_CN(rtp.rtpRec.pt));
   
   int iOnHold = pCallStatus && pCallStatus->iIsOnHold && !pCallStatus->iIsInConference;
   int iOnConfAndHold=pCallStatus && pCallStatus->iIsOnHold && pCallStatus->iIsInConference;
   
   cAOtmp->onlyInConference(iOnConfAndHold);
   
   int iTryRtpQ=useRtpQueue();
   
   if(iTryRtpQ){
      
      if(codec && !codec->canDecode(rtp.rtpRec.data.s,rtp.rtpRec.data.len,uiMaxDecodeBufSize)){
         iCantDecodePrevPack=1;
         
         if(iPacketsDecoded==0){
            rtp.uiSSRC_toIgnore=rtp.rtpRec.ssrc;
            rtp.uiSSRC=uiPrevSSRC;
            iPacketReceived=0;
            rtp.iCanResetMedia=10;
            rtp.uiIP2=uiPrevIP2;
            rtp.addrDst=aPrev;
         }
         return CRTPX::eRtpBad;
      }
      
      if(iOnHold)codec=NULL;//null means CN
      
    //  printf("[ts=%u seq=%u l=%d  %p]\n",rtp.rtpRec.ts, rtp.rtpRec.seqNr,rtp.rtpRec.data.len,codec);
      
      int r = cAOtmp->addPack(rtp.rtpRec.ts*c->getTSMult(), rtp.rtpRec.seqNr, rtp.rtpRec.data.s,rtp.rtpRec.data.len, codec);
      if(r==0){
         rtp.uiPrevRecTS=uiPos;
         onReceiveAudio(rtp.rtpRec.ts*c->getTSMult());
         uiStopMediaAt=cbEng->uiGT+T_GT_SECOND*3;
         rtp.packetsRec++;
         iPacketsDecoded++;
         iCantDecodePrevPack=0;
         return CRTPX::eRtpOk;
      }
   }
   
   //TODO detect packet repeats,old code only
   //int pack[64];memset(pack,255,sizeof(pack));//will set pack[n] to -1
   //if(pack[rtp.rtpRec.seqNr&63]==rtp.rtpRec.seqNr)return 0;//repeat detected
   //pack[rtp.rtpRec.seqNr&63]=rtp.rtpRec.seqNr;

   if(iResetPlayPos)
   {
      //reset play position to recomended delay based on jitter calc
      cAOtmp->update(NULL,0,uiPos);//reset must be done only here
   }

   
   if(ret==1 || IS_CN(rtp.rtpRec.pt))
   {

      cAOtmp->update(NULL,cAOtmp->getBufSize()/4,uiPos);//4>>>3 TEST
      rtp.uiPrevRecTS=uiPos;
      onReceiveAudio(rtp.rtpRec.ts*c->getTSMult());
      return CRTPX::eRtpOk;
   }
   
   
   
   //TODO put audio on hold, conf related
   
   if(iOnHold){
      int d=(int)(uiPos-rtp.uiPrevRecTS);
      if(d>2048)d=2048;
      d<<=1;//G722 fix
      cAOtmp->update(NULL,d,uiPos);
      rtp.uiPrevRecTS=uiPos;
      return CRTPX::eRtpOk;
   }
   rtp.uiPrevRecTS=uiPos;
   
   //TODO check padding

   if(!codec->canDecode(rtp.rtpRec.data.s,rtp.rtpRec.data.len,uiMaxDecodeBufSize)){
      //dec
      iCantDecodePrevPack=1;
      
      if(iPacketsDecoded==0){
         rtp.uiSSRC_toIgnore=rtp.rtpRec.ssrc;
         rtp.uiSSRC=uiPrevSSRC;
         iPacketReceived=0;
         rtp.iCanResetMedia=10;
         rtp.uiIP2=uiPrevIP2;
         rtp.addrDst=aPrev;
      }
      return CRTPX::eRtpBad;
   }
   iPacketsDecoded++;
   
   onReceiveAudio(rtp.rtpRec.ts*c->getTSMult());

#if T_HAS_HARDWARE_729_E_D
   //hardware or from os g729 encoder decoder
   if(rtp.rtpRec.pt==18 && cAOtmp->getType()==cAOtmp->eG729)
   {
      int i;
      int packs=rtp.rtpRec.data.len/10;//10 - g729 frame size
      char *pPcm=bufPcm;
      for(i=0;i<packs;i++)
      {
         memset(pPcm,0,156);
         pPcm[156]='G';pPcm[157]='7';pPcm[158]='2';pPcm[159]='9';
         memcpy(pPcm,&rtp.rtpRec.data.s[i*10],10);
         pPcm+=160;
      }
      iLen=packs*160;

   }
   else
   {
      iLen=codec->decode(rtp.rtpRec.data.s,(short *)bufPcm,rtp.rtpRec.data.len);
   }
#else
   //TODO should decode in corect order
   iLen=codec->decode(rtp.rtpRec.data.s,(short *)bufPcm,rtp.rtpRec.data.len);
#endif


   uiStopMediaAt=cbEng->uiGT+T_GT_SECOND*3;
   if(iLen<80){
      iCantDecodePrevPack=2;
      return CRTPX::eRtpOk;
   }
   iCantDecodePrevPack=0;

 
   float x=1;
   
   int X=0;
#if defined(_WIN32_WCE) || defined (__SYMBIAN32__)
   X=1;
#endif

   if(X==1 || (cAOtmp && cAOtmp->msg("canSetVolume",12,NULL,0)==0)){
      x=((float)21-((float)cbEng->p_cfg.aPlayCfg.iVolume/10));
   }

#define mabs(A)((A)>=0)?(A):-(A)

//disable software volume control
#if 0
   //!defined(ANDROID_NDK) && !defined(__APPLE__)
   
   if(//iIs729==0 && 
      x>1.1)
   {
      int i;
      int a;
      x=pow(1.2f,(x));
      
      short *p=(short *)bufPcm;
      int iDivC=(int)((float)1022/x); //5/(x)==5*(256/x)/256;
      for(i=0;i<iLen;i+=2,p++)
      {
         a=mabs(*p);
         cbEng->iCurMaxVolume=max(a,cbEng->iCurMaxVolume);

         int _c=(*p);
         _c=(_c*iDivC+512)>>10;
         *p=(short)_c;//(*p)/x;
      }
   }
   else
#endif
   {
      int i;
      int a;
#define _t_max(_A,_B) ((_A)>=(_B)?(_A):(_B))
      short *p=(short *)bufPcm;
      for(i=0;i<iLen;i+=2,p++)
      {
         a=mabs(*p);
         cbEng->iCurMaxVolume=_t_max(a,cbEng->iCurMaxVolume);
      }
   }
   
   if(!iReceivedPrev && cbEng->p_cfg.iDontRepeatAudio==0 && !rtp.iLost && uiMaxDecodeBufSize >=(unsigned int)iLen*2 && iLen<1281*2)
   {
      memcpy(bufPcm+iLen,bufPcm,iLen);//TODO align, or PLC
      iLen*=2;
   }

   cAOtmp->update((char *)bufPcm,iLen,uiPos);
   

#if defined(__SYMBIAN32__)
   if(!rtp.iLost)//clears buffer
   {
      uiPos+=(unsigned int)iLen;
      cAOtmp->update(NULL, (cAOtmp->getBufSize()/4)&~1, uiPos);
   }
#endif


   return CRTPX::eRtpOk;
}


int CRTPV::onData(char *p, int iLen, ADDR *a)
{

   if(!iSdpSent || !iSdpParsed)return CRTPX::eRtpNotMine;
   int ret=CRTPA::onData(p, iLen, a);
//TODO
//
   if(ret==CRTPX::eRtpOk || ret==CRTPX::eRtpBad)return ret;
  
   ret = rtpV.onRtp(p,iLen,a);
   if(ret==CRTPX::eRtpIce)return CRTPX::eRtpOk;
   
   
   if(ret<0)return ret;
   
   CVCodecBase *c=NULL;
   switch(rtpV.rtpRec.pt)
   {
#ifdef USE_JPG_VID

      case 123:
         
         c=iDestHasTina==0?(CVCodecBase*)&jpgDec:(CVCodecBase*)&tinaDec;
         break;
         
#endif
      case 122:
         c=&cb.vidCodec;
         break;
      default :
         if(rtpV.rtpRec.version==2){
            if(cbEng->p_cfg.iTinaFakeID && cbEng->p_cfg.iTinaFakeID==rtpV.rtpRec.pt){
               c=iDestHasTina==0?(CVCodecBase*)&jpgDec:(CVCodecBase*)&tinaDec;
            }
            else return CRTPX::eRtpNotMine;
         }
         else if(rtpV.rtpRec.version!=0)return CRTPX::eRtpNotMine;
   }
   
   
   CTVideoOutBase *cVOtmp=cVO;//Important: cVO can be set to NULL in other threads, but released later
   if(!cVOtmp)return CRTPX::eRtpNotMine;

   rtpV.iPrevPT=rtpV.rtpRec.pt;
   rtpV.iLost=(unsigned short)rtpV.iPrevId+1!=(unsigned short)rtpV.rtpRec.seqNr;
   rtpV.iPrevId=rtpV.rtpRec.seqNr;
   
   if(rtpV.iLost)iAskKey++;

   {
      if(iWillStop)return 0;

      pzrtp->setZrtpEnabled(!!pzrtp->iCanUseZRTP);
     
      pzrtp->startIfNotStarted(rtpV.rtpSend.ssrc,1);
      
      int iLenNew=iLen;
      int rr=pzrtp->decrypt(p,iLenNew,1);
      
      if(rr==CTZRTP::eIsProtocol){
        // rtpV.rtpRec.ssrc = rtp.uiSSRC;
         return 0;//protcol
      }
      if(rr==CTZRTP::ePacketError)return rr==-100?-100:-1;
      if(rr==CTZRTP::eDropPacket)return 0;
      rtpV.rtpRec.data.len+=(unsigned int)(iLenNew-iLen);
      //rtpV.rtpRec.data.len-=4;
      iLen=iLenNew;

   }
   if(rtpV.rtpRec.version!=2)return -1;

   iPacketReceived=1;

   rtpV.uiSSRC=rtpV.rtpRec.ssrc;
   
   if(pzrtp->getStatus(1)==pzrtp->eSecure && rtpV.ice.active()){
      //if (we are not sending data )set_ice_params
      rtpV.ice.setPeerData(sockth, rtpV.rtpSend.ssrc, rtpV.uiSSRC, 1);
   }
   
   int isP2PAddr=rtpV.ice.isP2PAddr(a);
   
   if(!isP2PAddr)rtpV.addrDst=*a;
   
   rtpV.iCanResetMedia=0;
   if(rtpV.rtpRec.data.len!=1)
   {
      int iOnHold=pCallStatus && pCallStatus->iIsOnHold;
      if(iOnHold)return 0;
      
      
      videoPlayer.setCallbacks(cAO, cVOtmp);
      
      cVOtmp->setTimeStamp(rtpV.rtpRec.ts);
      
      
      if(c==&tinaDec){
         
         //TODO c->canDecode
         //jpgDec.isHeaderOK(pI,iLen);
         if(!tinaDec.isHeaderOK(rtpV.rtpRec.data.s,rtpV.rtpRec.data.len)){
            c=(CVCodecBase*)&jpgDec;
            if(!jpgDec.isHeaderOK(rtpV.rtpRec.data.s,rtpV.rtpRec.data.len))return 0;
         }
         else{
            //TODO log
            if(jpgDec.isHeaderOK(rtpV.rtpRec.data.s,rtpV.rtpRec.data.len))
               return 0;
         }
      }
      else{
         if(!jpgDec.isHeaderOK(rtpV.rtpRec.data.s,rtpV.rtpRec.data.len)){
            
            if(!tinaDec.isHeaderOK(rtpV.rtpRec.data.s,rtpV.rtpRec.data.len))
               return 0;
            
            c=&tinaDec;
         }
      }

      videoPlayer.onReceiveRTP(c,rtpV.rtpRec.seqNr,rtpV.rtpRec.ts , rtpV.rtpRec.data.s,rtpV.rtpRec.data.len);

   }
   else {
      if(rtpV.rtpRec.data.s[0]>=61 && rtpV.rtpRec.data.s[0]<=80)
         iDestHasTina=(int)rtpV.rtpRec.data.s[0];
      else 
         iDestHasTina=0;

   }
   return 0;
}
int CRTPV::onVideoSend(char *p, int iLen, int iType, void* pMediaParam)
{
   if((iType&eVideo)==0)return -1;

   if(iWillStop)return 0;
   if(rtp.addrDst.ip==0)return 0;

   if(rtpV.rtpSend.ssrc==0)return 0;
   
   rtpV.rtpSend.ts=(size_t)pMediaParam;
   
   int iTinaSendSilenceVal=61;
#if defined (_WIN32_WCE)  || defined(__SYMBIAN32__)
   iTinaSendSilenceVal=62;
#endif
    int iOnHold=pCallStatus && pCallStatus->iIsOnHold;
   
//   printf("[%p %d %d %d]",p,iLen,iType,rtpV.rtpSend.ts);
   
   
   if(cbEng->p_cfg.iCanUseZRTP && pzrtp->getStatus(1)!=pzrtp->eSecure)
      iOnHold=1;

   if(p==NULL || iLen==0 || iIsActive==0 || iOnHold)
   {
      rtpV.rtpSend.allPack.len=13;
      rtpV.rtpSend.dataBuf[12]=iTinaSendSilenceVal+(iAskKey?2:0);
      if(iAskKey)iAskKey=0;

      if(cbEng->p_cfg.iTinaFakeID)
         rtpV.makeRTP(34,&pMediaIDS->m[1]);//&rtpSend,13,spSes);
      else
         rtpV.makeRTP(123,&pMediaIDS->m[1]);//&rtpSend,13,spSes);
//#endif
   }
   else
   {
      iVideoPacketSent=1;
      
      if(pMediaIDS->tmrTunnel && pMediaIDS->tmrTunnel->video.tmr_info.iShouldSkipVideoFrame){
         pMediaIDS->tmrTunnel->video.tmr_info.iShouldSkipVideoFrame=0;
         return 0;
      }
  
      CVCodecBase *c=NULL;
      switch(rtpV.rtpSend.pt)
      {
#ifdef USE_JPG_VID

         case 34:
            if(cbEng->p_cfg.iTinaFakeID!=rtpV.rtpSend.pt)return 0;
//#endif   
         case 123:
            cb.jpgEnc.iMaxQuality=60;
            c=&cb.jpgEnc;
#if !defined (_WIN32_WCE) && !defined(__SYMBIAN32__) && !defined(__APPLE__)
            if(iDestHasTina!=0)c=&cb.tinaEnc;
#endif
            break;
#endif
         case 122:c=&cb.vidCodec;break;
         default:
            return 0;
      }
      int x,y;
      //TODO get last yuv img - cVI.getYuvBytes()
      cVI.getXY(x,y);
      c->setXY(x,y);
      rtpV.rtpSend.data.iTest_lin_Rem=1;
      
      unsigned char *pO=&rtpV.rtpSend.dataBuf[12];
      
      rtpV.rtpSend.data.len=c->encode((unsigned char *)p,pO,iLen);
      
      if(rtpV.rtpSend.data.len<0 && c==&cb.tinaEnc){
         c=&cb.jpgEnc;
         c->setXY(x,y);

         rtpV.rtpSend.data.len=c->encode((unsigned char *)p,pO,iLen);

      }
      cb.iDecFPS=c!=&cb.tinaEnc;

      if(rtpV.rtpSend.data.len>1)
      {
         while(rtpV.rtpSend.data.len>0)
         {
            rtpV.rtpSend.allPack.len=rtpV.rtpSend.data.len+12;
            rtpV.makeRTP(rtpV.rtpSend.pt,&pMediaIDS->m[1]);

            int iIsLastPacketOfFrame = c->bytesEncodedLeft()==0;
            
            tryEncryptSendPack(&rtpV, sockth, iIsLastPacketOfFrame ? 2 : 1);
            
            rtpV.rtpSend.data.len = c->encode((unsigned char *)p,pO,iLen);
            
            //-- printf("[rtpV.rtpSend.data.len=%d enc]",rtpV.rtpSend.data.len);
         }
         
         if(iAskKey && p){//TODO && c!=&cb.jpgEnc
            onVideoSend(NULL,iLen,iType,pMediaParam);
         }
         
         return 0;

      }
      else
      {
         rtpV.rtpSend.allPack.len=13;
         rtpV.rtpSend.dataBuf[12]=iTinaSendSilenceVal;
         
         if(cbEng->p_cfg.iTinaFakeID)
            rtpV.makeRTP(34,&pMediaIDS->m[1]);//&rtpSend,13,spSes);
         else
            rtpV.makeRTP(123,&pMediaIDS->m[1]);//&rtpSend,13,spSes);
      }

   }

   int r=tryEncryptSendPack(&rtpV, sockth, 2);
   
   if(iAskKey && p){
      onVideoSend(NULL,iLen,iType,pMediaParam);
   }
   return r;
}

int CRTPA::tryEncryptSendPack(CRTPX *r, CTSock *s, int iIsVideo){
   
   int iIsLastVideoFrame = iIsVideo;
   if(iIsVideo==2){
      iIsVideo = 1;
   }

   CtZrtpSession::streamName n = iIsVideo ? pzrtp->VideoStream : pzrtp->AudioStream;
   
#if 0
   
    if(r->rtpSend.allPack.len>100){
       unsigned char *p =&r->rtpSend.dataBuf[0];
    memset(p+12,p[3],r->rtpSend.allPack.len-12);
    short seq=*(short*)&p[2];
    for(int i=12;i<160;i+=2)
    *(short*)(&p[i])=seq;
    }
   
#endif
   
   if(pzrtp->isStarted(n) || pzrtp->isSdesActive(n))
   {
    //  int encryptTest(unsigned char *binIn, int iLen,  int iIndex);
      //encryptTest(&r->rtpSend.dataBuf[12],r->rtpSend.allPack.len-12, r->rtpSend.seqNr);
      
      int iLenE=(int )r->rtpSend.allPack.len;
      if(pzrtp->encrypt((char *)&r->rtpSend.dataBuf[0],iLenE, iIsVideo)<0)return 0;
      r->rtpSend.allPack.len=(unsigned int)iLenE;
   }
   
   if(iPacketReceived==0 && !iIsVideo)
   {
      
      if(rtp.addrDst.ip!=rtp.uiIP2 && hasNetworkConnect(rtp.uiIP2)){
         ADDR a2=rtp.addrDst;
         a2.ip=rtp.uiIP2;
         sendPacket(s, (char *)&rtp.rtpSend.dataBuf[0], rtp.rtpSend.allPack.len,&a2,iIsVideo);
      }
   }
   
   ADDR aNewDst=r->addrDst;
   

   
   if(iPacketReceived && pzrtp->getStatus(iIsVideo)==pzrtp->eSecure && r->uiSSRC && r->ice.active()){
      r->ice.setPeerData(sockth, r->rtpSend.ssrc, r->uiSSRC);
      r->ice.onSendRTP(1);
      int ret = r->ice.trySetP2PAddr(&aNewDst);
    //  printf("[ice=%d v=%d]",ret, iIsVideo);
      /*
       //did not help ,should we check rtp ext ip ? and send reinvite
      if(ret == -1 && !r->ice.testing()){//p2p not used
         
         int c = pCallStatus && pCallStatus->iCaller;
         
         if((c && (r->rtpSend.seqNr & 2047)==1000) || (!c && (r->iPrevId & 2047)==997)){
            r->ice.startTestAgainAndSendNow();
            puts("[restart ice]");
         }
      }
       */
   }
 // char b[64];printf("[iIsVideo=%d dst=%s l=%d]\n", iIsVideo, aNewDst.toStr(b,1), r->rtpSend.allPack.len);
#ifdef _T_TEST_AUTH_TAG
   static int ccc;ccc++;
   if((ccc&127)==1 && r->rtpSend.allPack.len>15)r->rtpSend.dataBuf[15]+=10;//test srtp auth tag
#endif
 //  adbg("send e2 ",rtp.rtpSend.allPack.len);
//#define _T_TEST_REPLAY
#ifdef _T_TEST_REPLAY
     static int cc;cc++;
    if((cc&127)==1 && r->rtpSend.allPack.len>15){
   // r->rtpSend.dataBuf[15]+=10;
       for(int i=0;i<25;i++)//burst test
         sendPacket(s, (char *)&r->rtpSend.dataBuf[0], r->rtpSend.allPack.len, &aNewDst);
    }
#endif
   


   return sendPacket(s, (char *)&r->rtpSend.dataBuf[0], r->rtpSend.allPack.len, &aNewDst,iIsLastVideoFrame);
}

static int getRtpSOURCE(SDP *sdp){
   int iRtpSrc=CTSesMediaBase::eSourcePX;
   int i;
   /*
#if defined(_WIN32)
   ADD_0_STR(s,uiLen,"s=A SIP call - WinPC\r\nc=IN IP4 ");
#elif defined(_WIN32_WCE)
   ADD_0_STR(s,uiLen,"s=A SIP call - WinMob\r\nc=IN IP4 ");
#elif defined(__SYMBIAN32__)
   ADD_0_STR(s,uiLen,"s=A SIP call - Symbian\r\nc=IN IP4 ");
#else
   ADD_0_STR(s,uiLen,"s=A SIP call - TIVI\r\nc=IN IP4 ");
#endif
   */
   int iL=sdp->s.iLen-1;
   if(iL<3 || !sdp->s.pSessName)return iRtpSrc;

   while(isalpha(sdp->s.pSessName[iL]))iL--;
   char *p=&sdp->s.pSessName[iL+1];
   int iLen=sdp->s.iLen-iL-1;
   
   if(iLen<1)return iRtpSrc;
   
   struct{
      const char *name;
      int iLen;
      int id;
   }table[]={
      {"iOS",3,CTSesMediaBase::eSourceMob},
      {"TIVI",4,CTSesMediaBase::eSourcePC},
      {"WinPC",5,CTSesMediaBase::eSourcePC},
      {"WinMob",6,CTSesMediaBase::eSourceMob},
      {"Symbian",7,CTSesMediaBase::eSourceMob},
      {"TiviMob",7,CTSesMediaBase::eSourceMob},
      {"Android",7,CTSesMediaBase::eSourceMob},
      {"TiviApple",9,CTSesMediaBase::eSourcePC},
   };
   
   const int sz=sizeof(table)/sizeof(*table);
   
   for(i=0;i<sz;i++){
      if(iLen==table[i].iLen && strncmp(table[i].name,p,iLen)==0)
      {iRtpSrc=table[i].id;break;}
   }

   return iRtpSrc;
}

int trySetIceData(SDP &sdp, CTXIce *ice, int iType){
   int i;
   ice->onSDPRecv();
   for(i=0;i<sdp.attribs.iAttribCnt;i++){
      
      if(sdp.attribs.n[i].iMediaType==iType){
         ice->addDstCandidate(sdp.attribs.n[i].p, sdp.attribs.n[i].iLen);
      }
   }
   return 0;
}

int trySetZRTP_hash(SDP &sdp, CTZRTP *zrtp, int iType){
   int i;
   const static char ph[]="zrtp-hash:";
   int cnt=0;

   for(i=0;i<sdp.attribs.iAttribCnt;i++){

      if(sdp.attribs.n[i].iMediaType==iType  && sdp.attribs.n[i].iLen>64+sizeof(ph) && strncmp(sdp.attribs.n[i].p,ph,sizeof(ph)-1)==0){
         int l=sdp.attribs.n[i].iLen-(sizeof(ph)-1);
         zrtp->setDstHash(sdp.attribs.n[i].p+sizeof(ph)-1+5,l-5,iType==SDP::eVideo);//skip verss nr
         printf("[setting sdp hash %.*s]",sdp.attribs.n[i].iLen,sdp.attribs.n[i].p);
         cnt++;
      }
   }
   if(!cnt)puts("[sdp hash is not found]");
   return cnt?0:-1;
}

int trySetSDES_info(SDP &sdp, CTZRTP *zrtp, int iType, int iInviter){
#if 1
   const static char ph[]="crypto:";
   const static char ph_mix[]="crypto-mix:";
   
   //  Adjusted to SDES mix attribut string length?
   // The crypto-mix attribut string shall be null terminated: writing a \0 - is this ok? Assumption is
   // the \0 overwrites a \r.
   //
   //   crypto-mix _must_ be set before parseSdes, otherwise the SRTP setup will not be correct.
   //

   
   for(int i=0;i<sdp.attribs.iAttribCnt;i++){
      
      if(sdp.attribs.n[i].iMediaType==iType  && sdp.attribs.n[i].iLen>sizeof(ph_mix)+10 &&
         strncmp(sdp.attribs.n[i].p,ph_mix ,sizeof(ph_mix)-1)==0){
         
         char c=sdp.attribs.n[i].p[sdp.attribs.n[i].iLen];
         sdp.attribs.n[i].p[sdp.attribs.n[i].iLen] = '\0';
         bool b = zrtp->setCryptoMixAttribute(sdp.attribs.n[i].p+(sizeof(ph_mix)-1),
                                     iType==SDP::eVideo?CtZrtpSession::VideoStream:CtZrtpSession::AudioStream);
/*
         void tmp_log(const char *p);
         char bu[128];
         sprintf(bu,"[%s][%s][b=%d]\n",sdp.attribs.n[i].p, sdp.attribs.n[i].p+(sizeof(ph_mix)-1),b);
         tmp_log(bu);
  */       
         sdp.attribs.n[i].p[sdp.attribs.n[i].iLen]=c;//restore
         
         log_zrtp("t_zrtp",b?"setCryptoMixAttribute()=ok":"setCryptoMixAttribute()=false");
         break;
      }
   }
   
   for(int i=0;i<sdp.attribs.iAttribCnt;i++){
      
      if(sdp.attribs.n[i].iMediaType==iType  && sdp.attribs.n[i].iLen>32 && strncmp(sdp.attribs.n[i].p,ph,sizeof(ph)-1)==0){
         int l=sdp.attribs.n[i].iLen-(sizeof(ph)-1);
         bool b=zrtp->parseSdes(sdp.attribs.n[i].p+(sizeof(ph)-1), l, NULL, NULL, iInviter,
                         iType==SDP::eVideo?CtZrtpSession::VideoStream:CtZrtpSession::AudioStream);
         log_zrtp("t_zrtp",b?"parseSdes()=ok":"parseSdes()=false");
         
         return 0;
      }
   }
#endif
   log_zrtp("t_zrtp","sdes is not found");
   printf("[sdes is not found,type=%d]",iType);
   return -1;
}

int trySetZRTP_encap(SDP &sdp, CTZRTP *zrtp, int iType){
   int i;
   const static char ph[]="zrtp-encap:";
   int cnt = 0;
   
   CtZrtpSession::streamName sn = iType == SDP::eVideo ? CtZrtpSession::VideoStream : CtZrtpSession::AudioStream;
   
   for (i = 0; i < sdp.attribs.iAttribCnt; i++) {
      if (sdp.attribs.n[i].iMediaType == iType  && sdp.attribs.n[i].iLen > sizeof(ph) && strncmp(sdp.attribs.n[i].p, ph, sizeof(ph)-1)==0) {
         int l = sdp.attribs.n[i].iLen - (sizeof(ph) - 1);
         zrtp->setZrtpEncapAttribute(sdp.attribs.n[i].p + sizeof(ph)-1, sn);
         log_zrtp("t_zrtp","setZrtpEncapAttribute ok");
         cnt=1;
         break;
      }
   }
  // char b[64];sprintf(b, "a-srtp-fail pt=%d seq=%d prev_ok_seq=%d",rtp.rtpRec.pt, rtp.rtpRec.seqNr, rtp.iPrevId);
   
   
   if (!cnt){
      log_zrtp("t_zrtp","setZrtpEncapAttribute not ok");
   }
   return cnt? 0: -1;
}


void CRTPX::onSDPAttribs(SDP &sdp, int eType, PHONE_CFG &p_cfg, int iInviter, CTZRTP *pzrtp){
   
   if(T_CAN_USE_T_ICE && p_cfg.iCanUseP2Pmedia)
      trySetIceData(sdp, &ice, eType);
   
   iCanResetMedia=60;
   
   if(!pzrtp)return;
   
   trySetZRTP_hash(sdp,pzrtp,eType);
   
   pzrtp->setSdesEnabled(!!(p_cfg.iSDES_On));
   
   if(p_cfg.iSDES_On){
      trySetSDES_info(sdp, pzrtp, eType, iInviter);
      
      if(p_cfg.iZRTPTunnel_On)
         trySetZRTP_encap(sdp, pzrtp, eType);
   }
   
}

int CRTPA::onSdp(char *pSdp, int iLen, int iIsReq, int iForceMedia)//uu CRTPX
{

   SDP sdp;
   memset(&sdp,0,sizeof(SDP));
   int not_ok=parseSDP(&sdp,pSdp, iLen);
   if(not_ok)return not_ok;

   int media_id = findMediaId(&sdp,SDP::eVideo,0);
   
   if(!iForceMedia){//if (onhold) dont accept video calls
      if(media_id>=0)return -100;
   }
   
   

   not_ok=rtp.onSdp(sdp,SDP::eAudio, *cbEng);
   if(not_ok)
   {
      rtp.clear();
      return not_ok;
   }
   //


   uiIPOrig=sdp.ipOrigin;
   uiIPConn=sdp.media[0].ipConnect;
   iRTPSource = getRtpSOURCE(&sdp);//sdp.s.pSessName
   
   int iInviter=iSdpSent;//iIsReq?????

   rtp.onSDPAttribs(sdp, SDP::eAudio, cbEng->p_cfg, iInviter, pzrtp);
   
   if(cbEng->p_cfg.iSDES_On && pzrtp)pzrtp->clearSdesString();
   

   if(iSdpSent || !cAO)
   {
      
      if(!cAO)cAO = mediaMngr.getAO(16000);
      if(cAO){
         cAO->update(NULL,0,0);
         if(iSdpSent)cAO->play();
      }
   }

   log_events( __FUNCTION__, "[SDP audio parsed]");
   iSdpParsed=1;


   return 0;
}

int CRTPA::initTunneling(){
   if(pMediaIDS && pMediaIDS->tmrTunnel)
      pMediaIDS->tmrTunnel->audio.setCB(this);
   return 0;
}

int CRTPA::onStart()
{
   if(iStarted)return 0;
   iStarted=1;

   if(pzrtp){
      pzrtp->pRet[0]=this;
      pzrtp->setSendCallback(this, pzrtp->AudioStream);
      pzrtp->setSdesEnabled(!!(cbEng->p_cfg.iSDES_On));
      pzrtp->setZrtpEnabled(!!(cbEng->p_cfg.iCanUseZRTP || cbEng->p_cfg.iZRTP_On));
   }
   
   if(pMediaIDS && pMediaIDS->tmrTunnel)
      pMediaIDS->tmrTunnel->audio.setCB(this);

   {
      if(!cbEng->p_cfg.iZRTP_On)
         cbEng->p_cfg.iCanUseZRTP=0;
      else if(cbEng->p_cfg.uiZZRTP_bus_lic_exp_at){
         
         if(cbEng->p_cfg.uiZZRTP_bus_lic_exp_at>get_time()){
            cbEng->p_cfg.iCanUseZRTP=3;
            if(!cbEng->p_cfg.iIsValidZRTPKey) cbEng->p_cfg.iIsValidZRTPKey=3;
         }
         else{
            if(cbEng->p_cfg.iIsValidZRTPKey==3){
               cbEng->p_cfg.iCanUseZRTP=1;
               cbEng->p_cfg.iIsValidZRTPKey=0;
            }
         }

      }
   }

   if(!cAO)
      cAO = mediaMngr.getAO(16000);
   iIsActive=1;
   
   
   cAI.packetTime(cbEng->p_cfg.iPayloadSizeSend);
#ifndef _WIN32
   //unsigned char tos=184;
 //  setsockopt(sockth->sock, IPPROTO_IP, IP_TOS, (char *)&tos, sizeof(tos));
   //http://www.unix.com/man-page/freebsd/4/ip/
   int tos = 184;
   setsockopt(sockth->sock, IPPROTO_IP, IP_TOS, (const void*)&tos, sizeof(tos));
   //TODO test setsockopt(sockth->sock,SOL_SOCKET, SO_PRIORITY,&6 ,...
#endif

   cAI.record(this);

   if(cAO){
      cAO->update(NULL,0,0);
      cAO->play();
   }
   return 0;
}
int CRTPA::onStop()//old
{
   if(!iStarted)return 0;


   if(iIsActive==1)
   {
      iIsActive=0;
      if(cAO)cAO->stop();
      cAI.stop(this);
      rtp.clear();
   }
   iStarted=0;
   return 0;
}

void CRTPA::onRel(){
   mediaMngr.relAO(cAO);
   cAO=NULL;
}

void CRTPV::onRel(){
   puts("[rel VO]");
   CRTPA::onRel();
   mediaMngr.relVO(cVO);
   cVO=NULL;
}


int CRTPV::onStart()
{
   if(iStarted)return 0;
   CRTPA::onStart();
   pzrtp->pRet[1]=this;
   pzrtp->setSendCallback(this, pzrtp->AudioStream);
   pzrtp->setSendCallback(this, pzrtp->VideoStream);
   rtpV.iCanSend=1;


   if(pMediaIDS && pMediaIDS->tmrTunnel){
     // pMediaIDS->tmrTunnel->audio.setCB(this);
      pMediaIDS->tmrTunnel->video.setCB(this);
   }
   
   tinaDec.reset();
   
   if(!cVO)
      cVO = mediaMngr.getVO();
   
   if(mediaMngr.VOInUse()==1){
      cb.jpgEnc.reset();//TODO check - how many video sesions we have
      cb.tinaEnc.reset();//TODO check - how many video sesions we have
   }
   
   if(rtpV.addrDst.ip)
   {
      cVO->start();
      cVI.start(this);
   }
   videoPlayer.start();
   return 0;
}
int CRTPV::onStop()
{
   rtpV.iCanSend=0;
   if(!iStarted)return 0;
   CRTPA::onStop();
   if(cVO)cVO->stop();
   cVI.stop(this);
   videoPlayer.stop();
   iStarted=0;
   return 0;
}
//TODO x

int CRTPV::onSdp(char *pSdp, int iLen, int iIsReq, int iForceMedia)
{
   SDP sdp;
   memset(&sdp,0,sizeof(SDP));
   int not_ok=parseSDP(&sdp,pSdp,iLen);
   if(not_ok)return not_ok;
 
   not_ok=rtpV.onSdp(sdp,SDP::eVideo, *cbEng);
   if(not_ok)
   {
      rtpV.clear();
      return not_ok;
   }
   
   //TODO ?? FS
   if(!rtpV.addrDst.getPort() || hasAttrib(sdp,"inactive",SDP::eVideo)){//FS
      rtpV.clear();
      return -10;
   }
   
   not_ok=rtp.onSdp(sdp,SDP::eAudio, *cbEng);
   if(not_ok)
   {
      rtp.clear();
      return not_ok;
   }
   
   int iInviter=!iIsReq;//TODO 200 OK, or SIP REQ,
   
   //reinvite 491 req pending
   int iSDPSentPrev=iSdpSent;
   if(cbEng->p_cfg.iSDES_On && pzrtp && !pzrtp->isSecure(1) && !iSdpParsed && iSdpSent && iIsReq){
      iSdpSent=0;
      pzrtp->resetSdesContext(pzrtp->VideoStream);
      log_zrtp("t_zrtp","resetSdesContext(video)");
   }

   rtp.onSDPAttribs(sdp, SDP::eAudio, cbEng->p_cfg, iInviter, pzrtp);
   rtpV.onSDPAttribs(sdp, SDP::eVideo, cbEng->p_cfg, iInviter, pzrtp);
   if(cbEng->p_cfg.iSDES_On && pzrtp) pzrtp->clearSdesString();
   
   iRTPSource=getRtpSOURCE(&sdp);
   uiIPOrig=sdp.ipOrigin;
   uiIPConn=sdp.media[0].ipConnect;



   if(iSdpSent || !cAO)
   {
      if(!cAO)cAO = mediaMngr.getAO(16000);
      cAO->update(NULL,0,0);
      if(iSdpSent)cAO->play();
   }

   if(rtpV.iCanSend)
   {
      cVI.start(this);
   }
   
   if(iSdpSent || iSDPSentPrev)
   {
       if(!cVO)cVO = mediaMngr.getVO();
       cVO->start();
   }

   iSdpParsed=1;
   puts("[SDP video parsed]");


   return 0;
}

int CTSesMediaBase::addNonMediaAttribs(char *p, int iMaxLen, int iIsVideo 
                       , CRTPX *rtp, ADDR *addrPubl, ADDR *addrPriv, int iAddIce){
   
   int iLen=0;
   
   CtZrtpSession::streamName sn = iIsVideo ? CtZrtpSession::VideoStream : CtZrtpSession::AudioStream;
   
   if(cbEng->p_cfg.iZRTP_On && pzrtp){
      int cnt=pzrtp->getNumberSupportedVersions(sn);
      for(int i=0;i<cnt;i++){
         char tmp[128];
         int l=pzrtp->getSignalingHelloHash(tmp,iIsVideo,i);
         if(l>0)iLen+=t_snprintf(p+iLen,iMaxLen-iLen,"a=zrtp-hash:%.*s\r\n",l,&tmp[0]);
      }
   }

   if(cbEng->p_cfg.iSDES_On && pzrtp){
      char tmp[128];
      char tmp1[128];
      
      size_t l = sizeof(tmp)-1;
      size_t l1 = sizeof(tmp1)-1;
      size_t mixLen;
      //rtp->iSdpParsed
      
      int iInviter=!iSdpParsed;//pCallStatus && pCallStatus->iCaller - fails on reinivte, dont use pCallStatus
      
      if(iInviter){
         bool b=pzrtp->t_createSdes(&tmp[0], &l, sn);
         log_zrtp("t_zrtp", b?"t_createSdes()=ok":"t_createSdes()=false");
      }
      else{
         bool b=pzrtp->getSavedSdes(&tmp[0], &l, sn);
         log_zrtp("t_zrtp", b?"getSavedSdes()=ok":"getSavedSdes()=false");
         if(!b)l=0;
      }
      
      if(cbEng->p_cfg.iZRTP_On && cbEng->p_cfg.iZRTPTunnel_On){
         const char *encap = pzrtp->getZrtpEncapAttribute(sn);
         if (encap != NULL) {
            iLen += t_snprintf(p+iLen,iMaxLen-iLen, "a=zrtp-encap:%s\r\n",  encap);
            log_zrtp("t_zrtp", "add zrtp-encap:");
         }
      }
      
      
      if(l>0 && l<sizeof(tmp)){
         
         mixLen = pzrtp->getCryptoMixAttribute(tmp1, l1, sn);
         if (mixLen > 0){
            iLen += t_snprintf(p+iLen,iMaxLen-iLen, "a=crypto-mix:%.*s\r\n", (int)mixLen, tmp1);
            log_zrtp("t_zrtp", "add crypto-mix:");
         }
         iLen+=t_snprintf(p+iLen,iMaxLen-iLen,"a=crypto:%.*s\r\n",(int)l,&tmp[0]);
         log_zrtp("t_zrtp", "add crypto:");
      }
      
   }
   
   if(T_CAN_USE_T_ICE && iAddIce && cbEng->p_cfg.iCanUseP2Pmedia){
      iLen+=rtp->ice.createCandidates(addrPriv, addrPubl, p+iLen, iMaxLen-iLen);
   }
   
   return iLen;
}

class CTNetFWCheck{
   int iPrevNetType;
   ADDR stunDefaultAddr;
   char bufAddrStun[ADDR::eMaxDNS_SIZE];
   int iPingTime;
   CTSock *sock;
   int iIpBinded;
   int iFWDetected;
   
   
public:
   CTNetFWCheck( int iIpBinded, const char *aStun, ADDR &stunDefault, int iPingTime, int iPrevNetType, CTSock *s)
   :iIpBinded(iIpBinded),sock(s),iPingTime(iPingTime),iPrevNetType(iPrevNetType){
      
      stunDefaultAddr = stunDefault;
      strncpy(bufAddrStun,aStun, sizeof(bufAddrStun)-1);
      bufAddrStun[sizeof(bufAddrStun)-1]=0;
      
      iFWDetected=0;
   }
   
   int hasFW(){
      return iFWDetected;
   }
   
   int getExtAddr(ADDR *addr){
      
      if(forceFWTraversal()){
         
         tivi_log("WARN: forceFWTraversal-on");
         return 0;
      }
      
      sock->recreateIfNeededNow();//iOS kills UDP sockets in background
      
      int found  = 0;
      
      do{
         {
           CTStun st(iIpBinded, sock);
           found = testStun(st, stunDefaultAddr, addr, 0);
           if(found)break;
         }
         
         {
            tivi_log("WARN: default stun serv is not responding, trying to detect a false-positive");
            
            int ips[5];
            CTSock::getHostByName(bufAddrStun,0,&ips[0], sizeof(ips)/sizeof(int));
            ADDR aNew;
            //I can not create a different thread for this, I have the same socket
            for(int i = 0; !found; i++){
               if(!ips[i])break;
               
               if(stunDefaultAddr.ip==ips[i])continue;
               
               aNew.setPort(stunDefaultAddr.getPort());
               aNew.ip = ips[i];
               
               CTStun st2(iIpBinded, sock);
               found = testStun(st2, aNew, addr, 1+i);
            }
            //Sould I create the spearete thread for this
            //and skip the part ???
            if(CTStun::isNatWithoutFW(iPrevNetType)){
               
               tivi_log("WARN: isNatWithoutFW()=true but stun serv is not responding");
               CTSockCB ss;
               //trying to use a new socket
               CTStun st3(iIpBinded, ss);
               found = testStun(st3, stunDefaultAddr, addr, 100, 1);
               
               if(!found && aNew.ip){
                  CTStun st4(iIpBinded, ss);
                  found = testStun(st4, aNew, addr, 101, 2);
               }
               
            }
            
         }
         
      }while(0);
      
      iFWDetected=!found;
    
      return found;
   }
private:
   
   int testStun( CTStun &st, ADDR &a, ADDR *ext, int repeats, int iListen=0){
      
      unsigned int stT=getTickCount();

      st.addrStun = a;
      st.iPingTime = iPingTime;
      
      if(CTStun::isNatWithoutFW(iPrevNetType))
         st.iPingTime=iPingTime+2500;
      else if(iPrevNetType == CTStun::FIREWALL)
         st.iPingTime=1000;
      else if(st.iPingTime>3000)
         st.iPingTime=3000;
      else if(st.iPingTime<2000)st.iPingTime=2000;
      
      if(repeats>1)st.iPingTime=1500;
      
      if(iListen){
         st.getExtAddrListen(1);
         Sleep(20);
      }
      else {
         st.getExtAddr();
      }
      
      printf("[stun sp=%dms p=%d fw-off repeats=%d]\n",getTickCount()-stT,st.addrExt1.getPort(),repeats);
      
      if(!st.addrExt1.getPort())return 0;
      
      *ext = st.addrExt1;
      return 1;
   }
   
};

int CRTPA::checkNet(){
   
   
 //  ADDR addrPriv = sockth->addr;
   
   
   if(rtp.addrPublic.getPort()==0 || rtp.addrPublic.ip!=cbEng->extAddr.ip)
   {
#if 1
      CTNetFWCheck nc(cbEng->ipBinded, cbEng->p_cfg.bufStun, cbEng->addrStun, cbEng->iPingTime, cbEng->p_cfg.iNet, sockth);
      
      ADDR eA;
      int ok = nc.getExtAddr(&eA);
      if(ok){
         iFWDetected=0;
         rtp.addrPublic=eA;
      }
      else{
         rtp.addrPublic=sockth->addr;
         iFWDetected=1;
         return 0;
         
      }
#else
//CTNetFWCheck(int iIpBinded, const char *aStun, ADDR &stunDefault, int iPingTime, int iPrevNetType, CTSock *s)
      
      unsigned int stT=getTickCount();
      
      CTStun st(cbEng->ipBinded,sockth);//TODO exit flag if session down
      //TODO force udp recreate
      st.addrStun=cbEng->addrStun;
      st.iPingTime=cbEng->iPingTime;
      
      
      if(cbEng->p_cfg.iNet == CTStun::FIREWALL)
         st.iPingTime=1000;
      else if(st.iPingTime>3000)
         st.iPingTime=3000;
      else if(st.iPingTime<2000)st.iPingTime=2000;
      
      st.getExtAddr();
      
      printf("[stun sp=%dms p=%d fwon=%d]\n",getTickCount()-stT,st.addrExt1.getPort(),forceFWTraversal());
      
      if(st.addrExt1.getPort() && !forceFWTraversal()){
         iFWDetected=0;
         rtp.addrPublic=st.addrExt1;
      }
      else{
         rtp.addrPublic=sockth->addr;
         iFWDetected=1;
         return 0;
      }
#endif
   }
   
   return iFWDetected ? 0 : 1;
}

int CRTPA::makeSdp(char *p, int iMaxLen,  int fUseExtPort)
{
   if(rtp.iCodecCnt && pCallStatus && pCallStatus->iCaller){ //reset codecs on reinvite
      rtp.iCodecCnt=0;
   }
   
   //TODO if(!role){role=iSdpParsed?responder:inviter;}
   
   unsigned short port=sockth->addr.getPort();
   
   ADDR addrPublic=sockth->addr;
   ADDR addrPriv = sockth->addr;
   
   addrPriv.ip = cbEng->ipBinded;
   
   if(rtp.addrPublic.getPort()){
      addrPublic=rtp.addrPublic;
      port=addrPublic.getPort();
   }
   /*
   
   addrPriv.ip = cbEng->ipBinded;
   addrPubl=addrPriv;
   
   if(fUseExtPort)
   {
      //    printf("publ port %d\n",rtp.addrPublic.getPort());
      //printf("stun dns=%s\n",cbEng->addrStun.bufAddr);
      //  char b[64];printf("stun %s\n",cbEng->addrStun.toStr(b,1));
      
      if(rtp.addrPublic.getPort()==0 || rtp.addrPublic.ip!=cbEng->extAddr.ip)
      {
         unsigned int stT=getTickCount();
         
         CTStun st(cbEng->ipBinded,sockth);
         st.addrStun=cbEng->addrStun;
         st.iPingTime=cbEng->iPingTime;
         if(st.iPingTime>3000)st.iPingTime=3000;
         st.getExtAddr();
         
         printf("[stun sp=%dms]\n",getTickCount()-stT);
         
         if(st.addrExt1.getPort()){
            rtp.addrPublic=st.addrExt1;
            addrPubl=st.addrExt1;
         }
         else{
            addrPubl=addrPriv;
         }
      }
      else{
         addrPubl=rtp.addrPublic;
         if(!addrPubl.ip)addrPubl.ip=cbEng->extAddr.ip;
      }
      //  char b[64];printf("addrPubl %s\n",addrPubl.toStr(b,1));
      
      if(rtp.addrPublic.getPort())
         port=rtp.addrPublic.getPort();
   }
    */
   
   int iLen=rtp.makeSdp(p,
                        iMaxLen,
                        port,
                        "audio",
                        SDP::eAudio,
                        *cbEng, pMediaIDS->m[0].uiSSRC);
   
   if(pzrtp)pzrtp->setSdesEnabled(!!(cbEng->p_cfg.iSDES_On));

   iLen += addNonMediaAttribs(p+iLen, iMaxLen-iLen, 0, &rtp, &rtp.addrPublic, &addrPriv, fUseExtPort);

   /*
    int iFSHack=1;
    
    //TODO FS
    if(iFSHack && getMediaType()==eAudio){
    iLen+=sprintf(p+iLen,"m=video %d RTP/AVP 34\r\n"
    "a=rtpmap:34 H263/90000\r\n"
    "a=inactive\r\n"
    ,rtp.uiExtMediaPort);
    if(cbEng->p_cfg.iZRTP_On)
    iLen+=sprintf(p+iLen,"a=zrtp-hash:1.10 %.*s\r\n",64,&pzrtp->szHash_hex[1][0]);
    }
    */

   
   iSdpSent=1;
   
   return iLen;
}


int CRTPV::makeSdp(char *p, int iMaxLen,  int fUseExtPort)
{
   unsigned short port=sockth->addr.getPort();
 

   ADDR addrPublic;
   ADDR addrPriv = sockth->addr;
   addrPriv.ip = cbEng->ipBinded;
   
   int iLen=CRTPA::makeSdp(p,iMaxLen, fUseExtPort);
   
   if(rtp.addrPublic.getPort()){
      rtpV.addrPublic=rtp.addrPublic;
      addrPublic=rtp.addrPublic;
      port=addrPublic.getPort();
   }
   
   iLen+=rtpV.makeSdp(p+iLen,
                      iMaxLen-iLen,
                      port,
                      "video",
                      SDP::eVideo,
                      *cbEng,pMediaIDS->m[1].uiSSRC);

   iLen+=addNonMediaAttribs(p+iLen,iMaxLen-iLen,1,&rtpV,&addrPublic, &addrPriv, fUseExtPort);
     
   return iLen;
}

static int addSDPCodec(char *p, int iMaxLen, CSessionsBase &eng, int id, int eType){
   int iLen=0;
   if(iMaxLen<200)return 0;
   switch(id)
   {
      case 0:
         ADD_STR(p,iLen,"a=rtpmap:0 PCMU/8000\r\n");
         break;
      case 3:
         ADD_STR(p,iLen,"a=rtpmap:3 GSM/8000\r\n");
         break;
      case 8:
         ADD_STR(p,iLen,"a=rtpmap:8 PCMA/8000\r\n");
         break;
      case 9:
         ADD_STR(p,iLen,"a=rtpmap:9 G722/8000\r\n");
         break;
         
      case 18: ADD_STR(p,iLen,"a=rtpmap:18 G729/8000\r\n");
         //ADD_STR(p,iLen,"a=fmtp:18 annexb=yes\r\n");
         break;
         //#endif
      case 13:
         ADD_STR(p,iLen,"a=rtpmap:13 CN/8000\r\n");
         break;
      case 101:ADD_STR(p,iLen,"a=rtpmap:101 telephone-event/8000\r\n"
                       "a=fmtp:101 0-11\r\n");
         
         break;
      case 123:ADD_STR(p,iLen,"a=rtpmap:123 TINA/1000\r\n");break;//TiNa 
      default:
         if(id==34 && eng.p_cfg.iTinaFakeID)
         {
            ADD_STR(p,iLen,"a=rtpmap:34 H263/90000\r\n");break;
         }
   }
   
   return iLen;
}

int CRTPX::makeSdp(char *p, int iMaxLen, unsigned int uiPort,
                   const char *media, int eType, CSessionsBase &eng, unsigned uiSSRC)
{
   if(rtpSend.ssrc==0) setHdr(uiSSRC);
   int iShowAll;
   
   int i;
   int iCodecCount;//=rtp.iCodecCnt?rtp.iCodecCnt:1
   
   if(iCodecCnt)
   {
      iShowAll=0;
      iCodecCount=iCodecCnt;
   }
   else{
      iShowAll=1;
      iCodecCount=1;
      
   }
   
   //this->rtpSend.pt
   //rtpSend
   int iResponseOnlyWithOneCodecIn200Ok=eng.p_cfg.iResponseOnlyWithOneCodecIn200Ok && iShowAll==0;
   
   int iLen=t_snprintf(p,iMaxLen,eng.p_cfg.iSDES_On?"m=%s %u RTP/SAVP":"m=%s %u RTP/AVP",media,uiPort);//if SDES  use SAVP ??
   
   struct CODECS *cmy=&eng.sSdp.u.codec[0];
   int iMyCC=eng.sSdp.codecCount;
   
   int iIsVideo=SDP::eAudio!=eType;
   
#if 1
   if(iShowAll){
      for (i=0;i<iMyCC;i++){
         if(cmy[i].eType!=eType)continue;
         iLen+=t_snprintf(p+iLen, iMaxLen-iLen," %u",cmy[i].uiID);
      }
   }
   else{
      for (i=0;i<iCodecsMatching[iIsVideo];i++){
         int id=codecMatches[iIsVideo][i];
         if(i && iResponseOnlyWithOneCodecIn200Ok && id!=T_MY_DTMF_PT_ID && !IS_CN(id))continue;
         iLen+=snprintf(p+iLen, iMaxLen-iLen," %u",id);
      }
   }
   
   
   
#endif
   
   ADD_CRLF(p,iLen);
   
#if 1
   if(iShowAll){
      for (i=0;i<iMyCC;i++){
         if(cmy[i].eType!=eType)continue;
         iLen+=addSDPCodec(p+iLen,iMaxLen-iLen,eng, (int)cmy[i].uiID,eType);
      }
   }
   else{
      for (i=0;i<iCodecsMatching[iIsVideo];i++){
         int id=codecMatches[iIsVideo][i];
         if(i && iResponseOnlyWithOneCodecIn200Ok && id!=T_MY_DTMF_PT_ID && !IS_CN(id))continue;
         iLen+=addSDPCodec(p+iLen,iMaxLen-iLen,eng, (int)id,eType);
      }
   }
   
#endif
   /*
    
    When I do
    a=audio 20000 AVP/RTP 0
    a=video 0 AVP/RTP 34
    a=rtpmap:34 H263/90000
    or
    a=audio 20000 AVP/RTP 0
    a=video 0 AVP/RTP 34
    a=rtpmap:34 H263/90000
    a=inactive
    or
    a=audio 20000 AVP/RTP 0
    a=video 20002 AVP/RTP 34
    a=rtpmap:34 H263/90000
    a=inactive
    
    it fails
    */
   //   ADD_STR(p,iLen,"a=sendrecv\r\n");
   //-- return iLen;
   return t_snprintf(p+iLen, iMaxLen-iLen,"a=x-ssrc:%08x\r\n",uiSSRC)+iLen;
}




