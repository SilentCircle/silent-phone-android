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

// tiviCons.cpp : Defines the entry point for the console application.
//
#ifndef UNICODE
#define UNICODE
#endif

#ifndef _T_CONSOLE_
#define _T_CONSOLE_
#endif

#pragma warning( disable : 4996 )

#if  defined(_WIN32_WCE) ||  defined(_WIN32)
#include <winsock2.h>
#define socklen_t int
#define CLOSE_TH  CloseHandle
#else
#endif

#if defined(ANDROID_NDK)
#include <android/log.h>
#endif

#include <stdio.h>
#include "../tiviengine/CPhone.h"
#include "../tiviengine/CTPhMedia.h"
#include "../utils/utils_audio.h"
#include "../utils/utils_make_dtmf.h"


#if defined(ANDROID_NDK) || defined(__APPLE__) || defined(__linux__)

#include "../audio/CTAudioMacOSX_IOS.h"//TODO rename - apple and android

#endif

#ifdef _WIN32

#ifndef _WIN32_WCE
//#define T_USE_DX
 #define T_USE_DX_IN
#endif

#define T_USE_C_VIDEO_INTERFACE

#include "../utils/utils_ms_audio_noUI.h"

#ifdef T_USE_DX
 #include "../audio/CAudioInDirectx.h"
 #include "../audio/CAudioOutDirectx.h"
 #define CTAOut CTAudioOut
#else
 #ifdef T_USE_DX_IN
 #include "../audio/CAudioInDirectx.h"
#endif
//#include "../audio/CTAudioINSes.h"
//#include "../audio/CTPhoneAuidoInNew.h"
#include "../audio/CTAudioInOut.h"
#if 0
  #include "../audio/CTAudioOut_xaudio2.h"
  #define CTAOut CTAudioOutXA2
#else
  #define CTAOut CTAudioOut
#endif
#endif
#endif

#include "../audio/CTAudioOutVOIP.h"

//next line: should be disabled - test only
//#define T_USE_C_VIDEO_INTERFACE

#ifdef T_USE_C_VIDEO_INTERFACE

#include "../video/CTVideoOutC.h"
#include "../video/CTVideoInC.h"

#define CTServVideoOut CTVideoOut

#elif defined(__APPLE__)

#include "../video/CTVideoOut_Quartz.h"
#include "../video/CTVideoInIOS.h"

#define CTServVideoIn CTVideoInIOS
#define CTServVideoOut CTVideoOut


#elif defined(ANDROID_NDK)  || defined(__linux__)

#include "../video/CTVideoInOutAndroid.h"

#endif

#include <signal.h>

#ifdef __APPLE__
#include <ifaddrs.h>
#include <arpa/inet.h>
#endif

void tivi_log1(const char *p, int val);
void tmp_log(const char *p);
void add_tz_random(void *p, int iLen);
const char *findFilePath(const char *fn);
void t_save_glob();
void t_init_glob();
int isFileExistsW(const short *fn);
void setCfgFN(CTEditBase &b, int iIndex);

#if defined(__linux__) && !defined(ANDROID_NDK)
void tivi_slog(const char* format, ...);  // this function is in CTSipSock.cpp
void tivi_log1(const char *p, int val)
{
    tivi_slog("%s = %d", p, val);
}
#endif 

#define INFO_MSG_ROW_CNT 20

class CTBMsg{
   CTEditBuf<128> m[INFO_MSG_ROW_CNT];
   int iLast;
   CTEditBuf<4096> msg;
   int iHasNew;
public:
   inline int hasNewInfo(){return iHasNew;}
   CTBMsg(){iHasNew=0;iLast=0;}
   int getUtf8(char *dst, int iMax){
      msg.getTextUtf8(dst, &iMax);
      return iMax;
   }
  // CTStrBase *getMsg(){iHasNew=0;return &msg;}
   CTStrBase* create(char *pTitle, CTStrBase *e, int iType)
   {
      iHasNew++;
      msg.setLen(0);//.reset();
      msg.addText(pTitle);
      msg.addChar('\n');
      
      void  insertTime(CTEditBase *e);
      m[iLast].setLen(0);
      insertTime(&m[iLast]);
      m[iLast].addChar(' ');
      m[iLast].addText(*e);
      iLast++;
      int i,iMsg=INFO_MSG_ROW_CNT;;
      int iAdded=0;
      if(iLast>=iMsg){iLast=0;}
      for(i=iLast;i<iMsg;i++){
         iAdded++;
         if(m[i].getLen()){
            msg.addText(m[i]);
            msg.addText("\n",1);
         }
      }
      for(i=0;i<iLast;i++){
         iAdded++;
         if(m[i].getLen()){
            msg.addText(m[i]);
            msg.addText("\n",1);
         }
      }
      
      
      return &msg;
   }
   
};


CTMutex mutexResp;




/*
 - (BOOL) activeWLAN
 {
 return ([self localWiFiIPAddress] != nil);
 }
 
 */
//TODO move to sock
class CTGetIP{
public:
   /*
    
    if ((s = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    ... error handling ...
    }
    
    memset(&ifr, 0, sizeof(ifr));
    snprintf(ifr.ifr_name, sizeof(ifr.ifr_name), "eth0");
    if (setsockopt(s, SOL_SOCKET, SO_BINDTODEVICE, (void *)&ifr, sizeof(ifr)) < 0) {
    ... error handling ...
    }
    
    */
   
   static int getIPLoc(int iNow, int *piIs3G)
   {
      static int ip=-1;
      
#ifdef __APPLE__
      static int i3g=-1;
      
      static unsigned int uiPrevG;
      unsigned int uiCT=getTickCount();
      if(iNow){
         if(iNow!=2 && uiPrevG+1000<uiCT)
            iNow=0;
      }
      //if call is active check ip every 1 sec
      if(piIs3G)*piIs3G=i3g;
      if(uiPrevG+50000>uiCT && !iNow && ip!=-1)return ip;
      if(iNow!=2 && uiPrevG+500>uiCT && ip!=-1)return ip;
      uiPrevG=uiCT;
      if(iNow==2)uiPrevG=uiCT-45000;
         
      if(ip==-1)ip=0;
      
      
      //if(!ip && (x&1))return ip;
      
      struct ifaddrs *interfaces = NULL;
      struct ifaddrs *temp_addr = NULL;
      int success = 0;
      int iNewIp=0;
      // retrieve the current interfaces - returns 0 on success
      success = getifaddrs(&interfaces);
      if (success == 0)
      {
         // Loop through linked list of interfaces
         temp_addr = interfaces;
         while(temp_addr != NULL)
         {
            if(temp_addr->ifa_addr->sa_family == AF_INET)// &&  (temp_addr->ifa_flags & IFF_LOOPBACK) == 0)
            {
               // Check if interface is en0 which is the wifi connection on the iPhone
               
               if(strcmp(temp_addr->ifa_name,"en0")==0 //wifi
                  || strcmp(temp_addr->ifa_name,"en1")==0)
               {
                  tivi_log1(temp_addr->ifa_name,0);
                  memcpy(&iNewIp,&((struct sockaddr_in *)temp_addr->ifa_addr)->sin_addr,sizeof(iNewIp));
                  i3g=0;
                  break;
               }
            }
            temp_addr = temp_addr->ifa_next;
         }
         temp_addr = interfaces;
         while(temp_addr != NULL && !iNewIp)
         {
            if(temp_addr->ifa_addr->sa_family == AF_INET)// &&  (temp_addr->ifa_flags & IFF_LOOPBACK) == 0)
            {
               
               tivi_log1(temp_addr->ifa_name,0);
               if(strcmp(temp_addr->ifa_name,"pdp_ip0")==0)
               {
                  i3g=1;
                  memcpy(&iNewIp,&((struct sockaddr_in *)temp_addr->ifa_addr)->sin_addr,sizeof(iNewIp));
                  break;
               }
            }
            temp_addr = temp_addr->ifa_next;
         }
      }
      ip=iNewIp;
      
      // Free memory
      freeifaddrs(interfaces);
      
      if(piIs3G)*piIs3G=i3g;
      
      
      //TODO check ip
      //if new ip check it with stun
      return ip;
#else
      
#if 0
      //def __linux__
      ip=CTSock::getLocalIp();;
      return ip;
#else
      static int iPrevIS3G=-1;
      
#if defined(ANDROID_NDK)
      const int iRecheckAddrEveryNTicks=200;
      int g_is3G();
      if(piIs3G){
         int i3g=g_is3G();
         *piIs3G=i3g;
         if(iPrevIS3G!=i3g){
            iNow=1;
            iPrevIS3G=i3g;
         }
      }
#else
      const int iRecheckAddrEveryNTicks=10;
#endif
      static SOCKET s;
      static int x;
      if(ip==-1)iNow=1;
      
      x++;
      if(x==iRecheckAddrEveryNTicks || iNow){
         if (s)closesocket(s);s=0;
         x=0;
      }
      else return ip;
      //http://stackoverflow.com/questions/7975473/detect-wifi-ip-address-on-android
      
      if(s==0)s=socket(AF_INET,SOCK_DGRAM,0);
      if(s==0)return 0;
      struct sockaddr_in sa;
      ADDR a="10.0.0.1:5060";//"64.236.91.21:5060";
      
      memset(&sa,0,sizeof(sa));
      sa.sin_addr.s_addr=a.ip;
      sa.sin_port=(unsigned short)a.getPortNF();
      sa.sin_family = AF_INET;
      connect(s,(struct sockaddr *)&sa,  sizeof(sa));
      int l=sizeof(sockaddr_in);
      getsockname(s,(struct sockaddr *)&sa, (socklen_t*) &l);
      ip=sa.sin_addr.s_addr;;
      
      char b[64];
      a.ip=ip;
      tivi_log1(a.toStr(b), iPrevIS3G);
      return ip;
#endif
      
#endif
   }
};

int hasIP(){
   int i3g=0,ip;
   ip=CTGetIP::getIPLoc(1,&i3g);
   return hasNetworkConnect(ip);
}

int checkIPNow(){
   int i3g=0,ip;
   ip=CTGetIP::getIPLoc(2,&i3g);
   return hasNetworkConnect(ip);
}

//-------------->>>>>>>>>>>>---------------------move to audio utils-------

template<class T>
void createSineS(double &fCtx, T *p, int iSamples, float freq, int iRate, int iVol, int iIsLast){
   double f=freq*3.1415*2/(double)iRate;
   double  mySinFncd(double f);//slow on iphone
   double dV=(double)iVol;
   double dRem=dV*.95*(double)iIsLast/(double)(iSamples+1);
   for(int i=0;i<iSamples;i++){
      p[i]=(T)((sin(fCtx))*(dV-dRem*i));
      fCtx+=f;
   }
}

void createSine(double &ctx, short *s, int iSamples, float freq, int iRate, int iVol, int iIsLast){
   createSineS<short>(ctx,s,iSamples,freq,iRate,iVol,iIsLast);
}


template<class T>
void createSineL(double &fCtx, double &fCtx2, T *p, int iSamples, float freq, float freq2, int iRate, int iVol, int iIsLast){
   double f2=freq2*3.1415*2/(double)iRate;
   double f=freq*3.1415*2/(double)iRate;
   double  mySinFncd(double f);//slow on iphone
   double dV=(double)iVol;
   double dRem=dV*.95*(double)iIsLast/(double)(iSamples+1);
   for(int i=0;i<iSamples;i++){
      p[i]=(T)((sin(fCtx)+sin(fCtx2))*(dV-dRem*i));
      fCtx+=f;
      fCtx2+=f2;
   }
}




class CTMicMuter{
   int ig_MuteMic;
   unsigned int  uiPlayUntil;
public:
   int mute_mic(){
      if(!ig_MuteMic)return 0;
      int d=(int)(uiPlayUntil-getTickCount());
      if(d<10000 && d>0)return 1;
      uiPlayUntil=0;
      ig_MuteMic=0;
      return 1;
   }
   void add(void *p, int iMaxDurMs){
      ig_MuteMic++;
      uiPlayUntil=getTickCount()+(unsigned int)iMaxDurMs;
   }
   void rem(void *p){
      if(ig_MuteMic>0)ig_MuteMic--;
   }
};


static CTMicMuter micMuter;


class CTUtilDataPlayer: public CTAudioGetDataCB{
   int iCanDelete;
   unsigned int uiStartTime;
   int iPos;
   int iMaxDurMs;
   int iPlayAfterPrev;
   int iDelayBetweenSounds;
   int iRemNext;
   int iRate;
   int iPrevHasHalfSample;
   
   int iLen;
   unsigned char *pBuf;
   
   CTAudioGetDataCB *prev;
   CTAudioOutVOIP *ao;
   CTResampler resampler;
   
public:
   
   static int mute_mic(){
      
      return micMuter.mute_mic();
   }
   
   CTUtilDataPlayer(int iRate, int iPlayAfterPrev, CTAudioOutVOIP *ao, unsigned char *pBuf, int iLen, int iCanDelete, int iMaxDurMs=5000)
   :iRate(iRate),iPlayAfterPrev(iPlayAfterPrev),ao(ao),pBuf(pBuf),iLen(iLen),iCanDelete(iCanDelete),iMaxDurMs(iMaxDurMs){
      
      resampler.reset();
      iPos=0;
      iDelayBetweenSounds=4000;
      uiStartTime=getTickCount();
      prev=ao->cbUtil;
      iRemNext=0;
      if(prev && iPlayAfterPrev){prev=ao->cbUtilNext;ao->cbUtilNext=this;iRemNext=1;}
      else ao->cbUtil=this;
      iPrevHasHalfSample=0;
      
      micMuter.add(this,iMaxDurMs);
      
      
      
   }
   virtual ~CTUtilDataPlayer(){
      if(iRemNext)ao->cbUtilNext=NULL;
      else ao->cbUtil=prev;
      
      if(iCanDelete && pBuf)
         delete pBuf;
      pBuf=NULL;
      
      micMuter.rem(this);
      
      
   }
   
   int getAudioData(void *p, int iSamples, int iDevRate, int iSampleTypeEnum, int iOneSampleSize, int iIsFirstPack){
      
      resampler.setRates(iRate,iDevRate);
      
      if(iPlayAfterPrev && iDelayBetweenSounds>0){
         iDelayBetweenSounds-=iSamples*2;
         memset(p,0,iSamples*2);
         return iSamples*2;
      }
      
      int iLeft=iLen-iPos;
      int td=(getTickCount()-uiStartTime);
      if(iLeft<=iSamples*2 || td>iMaxDurMs){
         memset(p,0,iSamples*2);
         if(iLeft>iSamples*2)iLeft=iSamples*2;
         if(iLeft>0 && pBuf){
            getSamples((short*)p,iLeft/2);
            //memcpy(p,pBuf+iPos,iLeft);
         }
         delete this;
         return -2;
      }
      getSamples((short*)p,iSamples);
      return iSamples*2;
   }
protected:
   int getSamples(short *pDst, int iSamples){
      
      int iUsed=0;
      resampler.doJob((short*)(pBuf+iPos),0,pDst,iSamples,&iUsed);
      iPos+=iUsed*2;
      
      return 0;
   }
   
};



//--------------<<<<<<<<<<<<<---------------------move to audio utils-------end



static void signalFncOnStop(int id){
   tivi_log1("signalOnStop",id);
}

static int iVideoConfEnabled=0;

int isVideoConfEnabled(){
#if defined(ANDROID_NDK)  || defined(__linux__)
   return 0;
#endif
   return iVideoConfEnabled;
}

class CTMediaMngr: public CTMediaMngrBase{
public:
   enum{eMaxAOCnt=16};//TODO global define
   
   char bufACRec[512];
   char bufACPlayback[512];
   
   CTAudioOut  cAO;
   
   typedef struct{
      int iInUse;
      int iRate;
      unsigned int uiReleasedAT;
      CTAudioOutVOIP *voipAudioOut;
   }T_AO;
   
   typedef struct{
      int iInUse;
      unsigned int uiReleasedAT;
      CTServVideoOut *vOut;
   }T_VO;
   
   T_AO ao[eMaxAOCnt];
   T_VO vo[eMaxAOCnt];
   
   void *pQWView;
   
   void setACName(int iIsRecording, const char *pname){
      
      char *p = iIsRecording ? &bufACRec[0] : &bufACPlayback[0];
      strncpy(p, pname,511);
      p[511]=0;
      
      
#ifdef _WIN32
      if(iInitOk){
         void *getGUIDByName(int iRec, const char *pname);
         int getAC_IDByName(int iRec, const char *pname);
      
        if(iIsRecording)cAI.init((GUID*)getGUIDByName(1, pname));
        if(!iIsRecording)cAO.init(NULL, getAC_IDByName(0, pname));
      }
      
#endif
   }
   
   
   CTMediaListItem *getMediaListItem(int iVideo){
      CTMediaListItem *i;
      CTList *lr = iVideo ? &listRemV : &listRemA ;
      mutexListRem.lock();
      
      i=(CTMediaListItem*)lr->getLRoot();
      if(i)lr->remove(i,0);
      
      mutexListRem.unLock();
      if(!i)i=new CTMediaListItem();
      //getListItem
      return i;
   }
   void relMediaListItem(CTMediaListItem *i, int iIsVideo){
      if(!i)return;
      mutexListRem.lock();
      if(iIsVideo)
         listRemV.addToTail(i);
      else
         listRemA.addToTail(i);
      mutexListRem.unLock();
   }
   CTList listRemV;
   CTList listRemA;
   CTMutex mutexListRem;
   
   CTServVideoOut *getVOPriv(int iSecondPass){
      CTServVideoOut *o=NULL;
      
      unsigned int uiTC=getTickCount();
      
      int iMaxCnt=maxVOCnt();
      
      for(int i=0;i<iMaxCnt;i++){
         int d = (int)(uiTC-vo[i].uiReleasedAT);
         if(!vo[i].iInUse && (iSecondPass || (vo[i].uiReleasedAT==0 && vo[i].vOut) || d>3000)){
            vo[i].iInUse=3;
            if(!vo[i].vOut)
               vo[i].vOut = new CTServVideoOut();
            o=vo[i].vOut;
            o->setQWview(pQWView);
            o->init();
            vo[i].uiReleasedAT=0;
            vo[i].iInUse=1;
            
            break;
         }
      }
      return o;
   }
   
   
   CTAudioOutVOIP *getAOPriv(int iRate, int iSecondPass){
      
      CTAudioOutVOIP *o=NULL;
      
      unsigned int uiTC=getTickCount();
      
      for(int i=0;i<eMaxAOCnt;i++){
         int d = (int)(uiTC-ao[i].uiReleasedAT);//#SP-263 getTickCount can overlap 
         if(!ao[i].iInUse && (iSecondPass || (ao[i].uiReleasedAT==0 && ao[i].voipAudioOut) || d>5000)){
            ao[i].iInUse=3;
            ao[i].iRate=iRate;
            if(!ao[i].voipAudioOut)
               ao[i].voipAudioOut = new CTAudioOutVOIP(iRate,1,&cAO);
            o=ao[i].voipAudioOut;
            ao[i].uiReleasedAT=0;
            ao[i].iInUse=1;
            
            break;
         }
      }
      return o;
   }
   int iInitOk;
   CTServVideoOut *lastVO;
   
public:
   
   CTVadDetection2 vad;
   CTAudioIn  cAI;
   CTServVideoIn cVI;
   
   inline int maxVOCnt(){
      return isVideoConfEnabled()?eMaxAOCnt:1;
   }
   
   
   void setQWview(void *p){
      pQWView=p;
      for(int i=0;i<eMaxAOCnt;i++){if(vo[i].vOut)vo[i].vOut->setQWview(p);}
   }

public:

   CTMediaMngr():cAO(16,16000),cAI(NULL,16,16000){
      iInitOk=0;
      bufACPlayback[0]=0;
      bufACRec[0]=0;
      pQWView=NULL;
      lastVO=NULL;
   }
   int availableAO(){
      for(int i=0;i<eMaxAOCnt;i++){
         if(!ao[i].iInUse){
            return 1;
         }
      }
      return 0;
   }
   void init(){
      if(iInitOk)return;
      iInitOk=2;
#ifdef _WIN32
      void *getGUIDByName(int iRec, const char *pname);
      int getAC_IDByName(int iRec, const char *pname);

      cAI.init((GUID*)getGUIDByName(1, &bufACRec[0]));
      cAO.init(NULL, getAC_IDByName(0, &bufACPlayback[0]));
#else
      cAI.init();

      cAO.init(NULL);
#endif

      int iInitCnt=0;
      memset(vo, 0 ,sizeof(vo));
      
      int iRateO=16000;
      
      for(int i=0;i<eMaxAOCnt;i++){
         ao[i].iInUse=1;
         iInitCnt++;
         
         if(iInitCnt<2)
            ao[i].voipAudioOut=new CTAudioOutVOIP(iRateO,1,&cAO);
         else{
            ao[i].voipAudioOut=NULL;
         }
         ao[i].iRate=iRateO;
         ao[i].iInUse=0;
      }
      iInitOk=1;
   }
   ~CTMediaMngr(){
      for(int i=0;i<eMaxAOCnt;i++){
         ao[i].iInUse=1;
         if(ao[i].voipAudioOut)
            delete ao[i].voipAudioOut;
      }
      for(int i=0;i<eMaxAOCnt;i++){
         vo[i].iInUse=1;
         if(vo[i].vOut)
            delete vo[i].vOut;
      }
   }
   
   CTAudioOutBase *getAO(int iRate){
      
      CTAudioOutVOIP *o=getAOPriv(iRate,0);
      if(!o)o=getAOPriv(iRate,1);
      printf("[getAO ptr %p]",o);
      return (CTAudioOutBase*)o;
   }
   void relAO(CTAudioOutBase *p){
      CTAudioOutVOIP *v=(CTAudioOutVOIP*)p;
      printf("[relAO ptr %p]",v);
      if(!v)return;
      for(int i=0;i<eMaxAOCnt;i++){
         //TODO if (uiReleasedAT && uiReleasedAT+10000<getTickCount())delete ao[i].voipAudioOut
         if(ao[i].voipAudioOut==v){
            ao[i].uiReleasedAT=getTickCount();
            v->stop();
            puts("relAO ok");
            ao[i].iInUse=0;

            break;
         }
      }
   }
#if defined(ANDROID_NDK)  || defined(__linux__) 
   int getVidFrame(int prevf, int *i,int *sxy){
      CTServVideoOut *o=lastVO;
      if(!o)return -1;
      
      return o->getFrame(prevf, i, sxy);//it is safe to use - we are deleting a released objects later
   }
#else
   int getVidFrame(int prevf, int *i,int *sxy){
      return -2;
   }
#endif
   
   CTVideoOutBase *getVO(){
      CTServVideoOut *o=getVOPriv(0);
      if(!o)o=getVOPriv(1);
      printf("[getVO ptr %p]",o);
      if(o) lastVO=o;
      return (CTVideoOutBase*)o;
   }
   void relVO(CTVideoOutBase *p){
      CTServVideoOut *v=(CTServVideoOut*)p;
      if(lastVO==v)lastVO=NULL;;
      printf("[relVO ptr %p]",v);
      if(!v)return;
      for(int i=0;i<maxVOCnt();i++){
         //TODO if (uiReleasedAT && uiReleasedAT+10000<getTickCount())delete ao[i].voipAudioOut
         if(vo[i].vOut==v){
            vo[i].uiReleasedAT=getTickCount();
            v->stop();
            v->rel();
            puts("relVO ok");
            vo[i].iInUse=0;
            break;
         }
         
      }
   }
   int VOInUse(){
      int iCnt=0;
      for(int i=0;i<maxVOCnt();i++){
         if(vo[i].iInUse)iCnt++;
      }
      return iCnt;
   }
   int availableVO(){return maxVOCnt()-VOInUse();}

   
   CTVideoInBase &getVI(){return cVI;}
   CTAudioInBase &getAI(){return cAI;}
};

#if defined(__APPLE__) || defined(__linux__)
#define T_ENABLE_MULTI_CALLS
#endif

#include "../audio/CTDtmfPlayer.h"
#include "../tiviengine/CTConference.h"

CTDtmfPlayer dtmfPlay;//TODO move to global class
CTMediaMngr audioMngr;//TODO move to global class, rename to mediaMngr
CTConference g_conf;//TODO move to global class
CTLangStrings strings;

CTLangStrings *g_getLang(){return &strings;}

void setAudioInputVolume(int i){
    audioMngr.cAI.setVolume(i);
}
void setAudioOutputVolume(int i){
   audioMngr.cAO.setVolume(i);
}

#include "../tiviengine/CTEntropyCollector.h"
int CTEntropyCollector::iEntropyColectorCounter=0;

void initLang(const char *pDef);

void initGlobConstr(){
   static int iInitOk=0;
   if(iInitOk)return;
   iInitOk = 1;
   t_init_glob();
   if(!strings.lCouldNotReachServer.getLen())
      initLang(NULL);
}

void setDtmfEnable(int iEnable){
   dtmfPlay.enable(iEnable);
}

static int iIsInBackground=0;
int isInBackGround(){return iIsInBackground;}


void checkGlobalSettings(void *p);
void initGlobals(){
   static int iInitOk=0;
   if(iInitOk){while(iInitOk==2)Sleep(20);return;}
   iInitOk=2;
#ifndef _WIN32
   signal(SIGPIPE, SIG_IGN);//when peer tcp closes,then SIGPIPE is rised,TODO check connections
   signal(SIGSTOP, signalFncOnStop);//when peer tcp closes,then SIGPIPE is rised,TODO check connections
#endif
   puts("InitGlobalAudio");
   audioMngr.init();
   dtmfPlay.init();
   
   
   iInitOk=1;
}

#include "engcb.h"

class CTEngUtils{
public:
   static char *tryRemovePlus1IfNot11Digits(char *sz, int &iLen){//PRZ
      //some carriers prepended +1 even call originates outside US
      if(iLen<6)return sz;//number is too small
      if(sz[0]!='+' && sz[1]!='1')return sz;
      if(sz[0]=='+' && sz[1]!='1')return sz;//
      int iDigits=0;
      for(int i=0;i<iLen;i++){
         if(isalpha(sz[i]))return sz;
         if(sz[i]=='@'){
            break;
         }
         if(isdigit(sz[i]))iDigits++;
      }
      if(iDigits==11)return sz;
      iLen--;
      sz[1]='+';
      return sz+1;
   }
   static char *tryCleanNR(char *sz, int &iLen){
      int iDots=0,iAtFound=0;
      int iMustFix=0;
      for(int i=0;i<iLen;i++){
         
         if(isalpha(sz[i]))return sz;
         if(sz[i]=='@'){iAtFound=1;break;}
         
         if(isdigit(sz[i]) || (i==0 && sz[i]=='+'))continue;
         if(sz[i]=='.')iDots++;
         iMustFix=1;
         
      }
      if(!iAtFound || iDots==3)return sz;
      int iNewLen=0;
      int wasAt=0;
      for(int i=0;i<iLen;i++){
         
         if(sz[i]==0){break;}
         if(sz[i]=='@')wasAt=1;
         
         int d = wasAt || isdigit(sz[i]) || (i==0 && sz[i]=='+');
         
         if(!d)continue;
         
         sz[iNewLen]=sz[i];
         
         iNewLen++;
         
      }
      iLen=iNewLen;
      sz[iLen]=0;
      
      return sz;
   }
   //TODO move to eng utils
   static char *checkAddr(PHONE_CFG &p_cfg, char *bufTmpIncomAddr, int iMaxLen, char *sz, int &iLen){
      bufTmpIncomAddr[0]=0;
      iMaxLen--;
      
      if(iLen>=iMaxLen){
         strncpy(bufTmpIncomAddr,sz,iMaxLen);
         bufTmpIncomAddr[iMaxLen]=0;
         sz=&bufTmpIncomAddr[0];
         iLen=iMaxLen;
      }
      else{
         strncpy(bufTmpIncomAddr,sz,iLen);
         bufTmpIncomAddr[iLen]=0;
         sz=&bufTmpIncomAddr[0];
      }
      
      
      //  puts(sz);
      
      if(p_cfg.str32GWaddr.strVal[0]){
         ADDR a;
         for(int i=0;i<iLen;i++){
            if(sz[i]=='@'){
               a=sz+i+1;
               if(a.ip && a.ip==p_cfg.GW.ip){//if ip addr is servers replace to serv addr
                  strcpy(&sz[i+1],&p_cfg.str32GWaddr.strVal[0]);
                  iLen=strlen(&sz[0]);
               }
               else{
                  
                  if(strcmp(&sz[i+1], &p_cfg.str32GWaddr.strVal[0])){//if is no serv name
                     int iAtPos=i;
                     for(;i<iLen;i++){
                        if(sz[i]!='.')continue;
                        int l=strlen(p_cfg.str32GWaddr.strVal);
                        int z=l-(iLen-i);
                        if(z>0){
                           //if pbx.example.com replace to sip.example.com where sip.example.com is  serv addr
                           puts(&sz[i]);
                           puts(&p_cfg.str32GWaddr.strVal[z]);
                           if(strcmp(&sz[i],&p_cfg.str32GWaddr.strVal[z])==0){
                              strcpy(&sz[iAtPos+1],&p_cfg.str32GWaddr.strVal[0]);
                              iLen=strlen(&sz[0]);
                           }
                        }
                        i=iLen;
                        break;
                        
                     }
                  }
               }
            }
         }
      }
      return sz;
   }
};

#ifdef _WIN32
#include "../../libs/aec/src/aec.h"
#endif


static int iEchoCancellerOn = 3;//0 - off, 1-speex, 2-google ,3 eng_speex, 4 eng_speex+goog
int getEchoEnableState(){return iEchoCancellerOn;}

class CPhoneCons: public CTEngineCallBack, public CTZrtpCb
{
   class CTMute{
      int iMute;
      int iOnMuteCnt;
      int iSR;
   public:
      CTMute(){iSR=16000;iMute=0;iOnMuteCnt=0;}
      int isMuted(){return iMute;}
      void setMute(int onOff){iMute=onOff;iOnMuteCnt=0;}
      int shouldSendCN(int iSamples){
         iOnMuteCnt++;
         int iAnd=7;
         if(iSamples>320){
            int iPacketsPerSecond=iSR/iSamples;
            if(iPacketsPerSecond>26)iAnd=7;
            else if(iPacketsPerSecond>14)iAnd=3;
            else if(iPacketsPerSecond>8)iAnd=1;
            else iAnd=0;//send every pack
         }
         return iOnMuteCnt<8 || (iOnMuteCnt&iAnd)==0;
      }
   };
   CTMute mute;
   
   int iEngStarted;
   int iDialing;
   
   int iFlagResetEC;

   
   CTMakeDtmf dtmf;
   CTZRTP *_thisZRTP;
   
   
   fnc_cb_ph *fncCB;
   void *pCBRet;
   
   int iEndDialingCall;
   
   int iCallId;
   
   void playSound(int iRate, const char *fn, int iSesId, int iPlayAfterPrev){

      CTAudioOutVOIP *ao=(CTAudioOutVOIP*)ph->findSessionAO(ph->findSessionByID(iSesId));
      
      int iCanDelete=1;
      if(!ao)return;
      int iLen=0;
#ifdef __APPLE__
      char *iosLoadFile(const char *fn, int &iLen );
      char *p=iosLoadFile(fn,iLen);
#else
#if defined(ANDROID_NDK)
      iCanDelete=0;
      //TODO if this fails  try loadFile
      char *getResFile(const char *fn, int &iLen);
      char *p = getResFile(fn, iLen);
      
#else
      
      char *p=loadFile(fn,iLen);
#endif
#endif
      if(!p)return;
#ifndef _WIN32
#warning fix, can be mem leak if audio is stoped before this ends
#endif
      new CTUtilDataPlayer(iRate,iPlayAfterPrev,ao,(unsigned char*)p,iLen,iCanDelete);
   }
   
public:
   int iRun;
   
   char bufLastRegErr[128];
   char bufLastErrMsg[128];
   
   
   CTiViPhone *ph;
   
   void sendCB(int iMsgId, int cid=0, const char *szMsg=NULL, int iLen=0){
      if(fncCB){
         if(!iLen && szMsg)iLen=strlen(szMsg);
         fncCB(pCBRet, this, cid , iMsgId, szMsg, iLen);
      }
   }
   
   void setCB(fnc_cb_ph *fnc, void *pRet){
      pCBRet=pRet;
      fncCB=fnc;
      
   }
   
   void onPeer(CTZRTP *zrtp, char *name, int iIsVerified){
      _thisZRTP=zrtp;
      
#ifdef T_ENABLE_MULTI_CALLS
      CSesBase *sb = ph->findSessionByZRTP(zrtp);
      int cid=sb ? sb->ses_id():0;
#else
      int cid=iCallId;
#endif
      if(!iIsVerified && (!name || name[0]==0)){//if there is no ZRTP cache name create a auto name as ?SIP_URI?
         char _buf[128]="";
         CSesBase *ses=ph->findSessionByID(cid);
         snprintf(_buf,sizeof(_buf),"?%.*s?",ses->dstSipAddr.uiLen, ses->dstSipAddr.strVal);
         
         zrtp->setLastPeerNameVerify(_buf ,0);
         zrtp->setVerify(0);//must remove verify flag because the setLastPeerNameVerify sets it.
//         puts(_buf);
         name = NULL;
      }
  //    else if(name)puts(name);
      
      if(name && name[0]=='?' && name[strlen(name)-1]=='?')name = NULL;// dont send a auto cache name to UI
      
      sendCB(iIsVerified?CT_cb_msg::eZRTP_peer:CT_cb_msg::eZRTP_peer_not_verifed,cid,name);
      
   }
   void onZrtpWarning(CTZRTP *zrtp, char *p, int iIsVideo){
      _thisZRTP=zrtp;
      
#ifdef T_ENABLE_MULTI_CALLS
      CSesBase *sb = ph->findSessionByZRTP(zrtp);
      int cid=sb ? sb->ses_id():0;
#else
      int cid=iCallId;
#endif
      if(p){
         if(strncmp(p, "s2_c007:",8)==0 || strncmp(p, "s2_c051:",8)==0)
            return;
         
         int v;
         int iSDES=0;
         
         if(getCallInfo(cid,iIsVideo ? "media.video.zrtp.sec_state" : "media.zrtp.sec_state", &v)==0 && v & 0x100)
            iSDES=1;
         
         // Display SRTP authentication failure in any case
         if(iSDES && strncmp(p, "s2_c006:",8)!=0)
            return;
      }

      
      if(!zrtp->iFailPlayed && zrtp->isSecure(0) && zrtp->iSoundPlayed){
         zrtp->iFailPlayed=2;
         playSound(8000,"failed8k.raw",cid,1);
         zrtp->iSoundPlayed=1;
      }
      /*
      //--------------------
      printf("zrtp w=%s\n",p);
      static char bPrev[128];
      if(strcmp(bPrev, p)){
         strncpy(bPrev,p,sizeof(bPrev)-1);bPrev[sizeof(bPrev)-1]=0;
         CTEditBuf<256> b;
         b.setText(iIsVideo?"zrtp-w-v:":"zrtp-w-a:");
         b.addText(p);
         info(&b,0,cid);
      }
      //-------------
       */
      sendCB(CT_cb_msg::eZRTPWarn,cid,p);
   }
   
   void onNewZrtpStatus(CTZRTP *zrtp, char *p, int iIsVideo){
      /*
       //TODO if this  called from libzrtp thread
       void *initARPool(){
       return (void*)[[NSAutoreleasePool alloc] init];
       }
       void relARPool(void *p){
       NSAutoreleasePool *pool=(NSAutoreleasePool*)p;
       [pool release];
       }
       */
      
      _thisZRTP=zrtp;
      
      int stat=zrtp->getStatus(iIsVideo);
#ifdef T_ENABLE_MULTI_CALLS
      CSesBase *sb = ph->findSessionByZRTP(zrtp);
      int cid=sb ? sb->ses_id():0;
#else
      int cid=iCallId;
#endif
      sendCB(iIsVideo?CT_cb_msg::eZRTPMsgV:CT_cb_msg::eZRTPMsgA,cid,zrtp->getZRTP_msg(stat));
      
      if(zrtp->isSecure(0)){
         if(!zrtp->iSoundPlayed && stat!=zrtp->eSecureSdes){
            zrtp->iSoundPlayed=2;
            playSound(8000,"securemode.raw",cid,0);
            zrtp->iSoundPlayed=1;
         }
         
      }
      
      
      
      if(zrtp->isSecure(0) && p && !iIsVideo){
         sendCB(CT_cb_msg::eZRTP_sas,cid,p);
      }
      else if((stat==zrtp->eError) && p){
         /*
         CTEditBuf<256> b;
         b.setText(iIsVideo?"zrtp-err-v:":"zrtp-err-a:");
         b.addText(p);
         info(&b,0,cid);
         */
         sendCB(iIsVideo?CT_cb_msg::eZRTPErrV:CT_cb_msg::eZRTPErrA,cid,p);
      }
      if(!zrtp->iFailPlayed && (stat==zrtp->eError || (zrtp->iWarnDetected && zrtp->isSecure(0))) ){
         zrtp->iFailPlayed=2;
         playSound(8000,"failed8k.raw",cid,1);
         zrtp->iSoundPlayed=1;
         
      }
      
      
   }
   void onNeedEnroll(CTZRTP *zrtp){
      _thisZRTP=zrtp;

#ifdef T_ENABLE_MULTI_CALLS
      CSesBase *sb = ph->findSessionByZRTP(zrtp);
      int cid=sb ? sb->ses_id():0;
#else
      int cid=iCallId;
#endif
      sendCB(CT_cb_msg::eEnrroll,cid);
   }
   
   
   
   CPhoneCons(int iEngIndex)
   :CTEngineCallBack()
   {
    
      initGlobConstr();
      iEngineIndex=iEngIndex;
      
      fncCB=NULL;
      pCBRet=NULL;
      ph=NULL;
      _thisZRTP=NULL;
      
      iPrevCfgChanges=0;
      
      bufLastRegErr[0]=0;
      bufLastErrMsg[0]=0;
      
      iEndDialingCall=0;
      
      iFlagResetEC=1;
      
      iEngStarted=0;
      
      iCallId=0;
      iDialing=0;
      
      int getCfg(PHONE_CFG *cfg,int iCheckImei, int iIndex);
      getCfg(&p_cfg,1,iEngineIndex);
      
      //CTAxoInterfaceBase::sharedInstance(p_cfg.user.un);
      void g_sendDataFuncAxo(uint8_t* names[], uint8_t* devIds[], uint8_t* envelopes[], size_t sizes[], uint64_t msgIds[]);
      CTAxoInterfaceBase::setSendCallback(g_sendDataFuncAxo);
      
      p_cfg.iAutoRegister=1;
      
      int readSignedCfg(PHONE_CFG &cfg, CTLangStrings *strings);
      if(!p_cfg.iCfgVers)readSignedCfg(p_cfg,&strings);
      
      iRun=2;
      
   }
   void startEngine(){
      if(p_cfg.iAccountIsDisabled)return;
      
      getLocalIpNow();
      
      if(iEngStarted){ return;}
      
      initGlobals();
      
      
      iRun=1;
      iEngStarted=1;
      
      ph = new CTiViPhone(this,&strings,30);
      ph->setZrtpCB(this);
      
      p_cfg.iAutoRegister=1;
      p_cfg.iDontRepeatAudio=0;
      
#if defined(__linux__) && !defined(ANDROID_NDK)
      //used only for testing engines
      static CTMedia *m=new CTMedia(*ph,this, audioMngr);
#else
      
      CTMedia *m=new CTMedia(*ph,this, audioMngr);
#endif
      
      
      mediaFinder =(CTMediaBase *)m;
      
      ph->start();
      
      static int a=0;
      a++;
      if(a>=10){
         a=0;
         Sleep(500);
      }
   }
   int stopEngine(){
      puts("TODO stop eng");
      return 0;
   }
   int canRing(){
      if(!ph || !iRun)return 0;
      
      return ph->canPlayRingtone();
   }
   
   int command(const char *p, int iLen=-1)
   {
      //if(!ph)return 0;
#define T_TRY_OTHER_ENG -10001
      if(iRun==0){
         return T_TRY_OTHER_ENG;
      }
#ifndef __APPLE__
      p_cfg.iIsInForeground=1;
#endif
      if(!p)return 0;
      
      if(iLen<0)iLen=p?strlen(p):0;
      
      printf("[(1)msg %s l=%d]",p,iLen);
      
      if(iLen>5 && strncmp(p,":c .+",5)==0){
         static int r;
         r=0;
         ph->isDstOnline(p+5,&r);
         return 0;
      }
      
      if(*p=='*'  && iLen>1){
         int cid=atoi(p+2);
         if(!cid){
            if(iDialing && p[1]=='e')iEndDialingCall=1;//TODO make something better
            return -100;
         }
         if(!ph)return T_TRY_OTHER_ENG;
         
         CSesBase *ses=ph->findSessionByID(cid);
         if(!ses)return T_TRY_OTHER_ENG;
         
         switch(p[1]){
            case '+':g_conf.addCall(ses);return 0;
            case '-':g_conf.remCall(ses);return 0;
            case 'a':CTEntropyCollector::onNewCall(); ph->answerCall(cid);return 0;
            case 'e':
             {
#ifdef __APPLE__
                 // Same code is executed when we receive a remote end call event
                 // (METHOD_BYE)
                 // This code makes sure that the t_setActiveAS method will be called last
                 // so that we won't have any issues with the enabled VoiceOver.
                 CTAudioOutVOIP *ao=(CTAudioOutVOIP*)ph->findSessionAO(ses);
                 
                 if(ao) {
                 
                     unsigned int uiBufSize=(unsigned int)ao->getBufSize();
                     char *buf=new  char [uiBufSize+2];
                     memset(buf,0,uiBufSize);
                     
                     ao->setIgnoreIncomingRtp(1);
                     ao->setPlayPos(0);
                     ao->update(buf,(int)uiBufSize,0,0);
                     ao->stopAfter(1800);
                 }
#endif
                 
                 ph->endCall(cid);
                 return 0;
             }
            case 'm':puts("TODO mute");return 0;
            case 'h':ph->hold(1,cid);return 0;
            case 'u':ph->hold(0,cid);return 0;
               
            case 'c':return ph->reInvite(cid,"audio");
            case 'C':return ph->reInvite(cid,"video");
               
            case 'r':return ringSecondary(ses);
               
            case 'W':ph->whispering(1,cid);return 0;
            case 'w':ph->whispering(0,cid);return 0;
               
            case 'V':
            {
               CTZRTP *z=ph->findSessionZRTP(ses);
               if(z)z->setVerify(1);
               return 0;
            }
            case 'v':
            {
               CTZRTP *z=ph->findSessionZRTP(ses);
               if(z)z->setVerify(0);
               return 0;
            }
            case 't':
            {
               CTZRTP *z=ph->findSessionZRTP(ses);
               if(z)z->enrollAccepted(p_cfg.str32GWaddr.strVal);
               return 0;
            }
            case 'z':
            {
               CTZRTP *z=ph->findSessionZRTP(ses);
               if(z)
               {
                  while(*p && !isspace(*p))p++;
                  if(*p && p[1]){
                     char buf[128];
                     safeStrCpy(&buf[0],p+1,sizeof(buf)-1);
                     trim(buf);
                     
                     if(buf[0])
                        z->setLastPeerNameVerify(&buf[0],0);
                  }
                  
                  
               }
               return 0;
            }
         }
         
         return -1;
      }
      
      
      if(iLen==3 && *p==':'){
         p++;
         switch(*p){
            case 'D':
               // printf("[dtmf start %d]\n",p[1]);
               dtmf.start(p[1]);
               //--dtmfPlay.play(p[1]);
               return T_TRY_OTHER_ENG;
            case 'd':
               dtmfPlay.play(p[1]);
               
               break;
            case 's':
               if(p[2]=='c'){
                  if(p_cfg.iNeedSave){
                     saveCfg(&p_cfg,iEngineIndex);
                  }
                  
                  return 0;
               }
         }
         return 0;
      }
      if(iLen==2 && *p==':')
      {
         p++;
         switch(*p)
         {
            case 'b':
               dtmfPlay.play(' ');
               return 0;
            case 'h': case 'H':
               usage();
               return 0;
               
            case 'D':
               //stopDTMF
               break;
            case 'd':
               dtmfPlay.stop();
               
               return 0;
            case 'Q': case 'q':
               iRun=0;
               return 0;
            case 's':
               saveCfg(&p_cfg,iEngineIndex);
               return T_TRY_OTHER_ENG;
            case 'A': case 'a':
               if(iCallId)
               {
                  CTEntropyCollector::onNewCall();
                  ph->answerCall(iCallId);
               }
               return 0;
            case 'E':
               ph->endAllCalls();
               break;
            case 'e':
               if(iCallId)
               {
                  int cc=iCallId;
                  iCallId=0;
                  ph->endCall(cc);
               }
               return 0;
#ifdef T_TEST_SYNC_TIMER
            case 'X':
            {
               int rc = 0;
               if (!ph) {
                   return T_TRY_OTHER_ENG;
               }

               pthread_mutex_lock(&ph->timerDoneMutex);
               pthread_cond_signal(&ph->timerConditional);
               rc = pthread_cond_timeout_np(&ph->timerDoneConditional, &ph->timerDoneMutex, 1000);
               if (rc == ETIMEDOUT) {
                  __android_log_print(ANDROID_LOG_DEBUG,"TIMERNEW", "X command conditional time out, ph: %p", ph);
               }
               pthread_mutex_unlock(&ph->timerDoneMutex);
               return T_TRY_OTHER_ENG;
            }
#endif
            default:
            {
               puts("Unknown command");
               usage();
               return 0;
            }
         }
      }
      
      if(iLen>2)
      {
         if(*p==':')
         {
            if(p[2]==' '){
               switch(p[1]){
                  case 'w':case 'W':if(iLen>3)Sleep(atoi(p+2)*1000);else Sleep(1000);break;
                  case 'u':case 'U':strcpy(p_cfg.user.un,p+3);break;
                  case 'p':case 'P':strcpy(p_cfg.user.pwd,p+3);break;
                  case 'z':
                     _thisZRTP->setLastPeerNameVerify(p+3,0);
                     break;
                  case 'c':case 'C':
                  case 'v':case 'V':
                     {
                        startEngine();
                        CTEntropyCollector::onNewCall();
                        char buf[128];
                        strcpy(buf,p+2);
                        iEndDialingCall=0;
                        iDialing=1;
                        CTSesMediaBase *mb = (*(p+1)!='v')?NULL:tryGetMedia("video");
                        iCallId=ph->call(buf, mb);
                        if(!iCallId && mb){
                           mediaFinder->release(mb);
                        }
                        if(iEndDialingCall){
                           iDialing=0;
                           iEndDialingCall=0;
                           ph->endCall(iCallId);
                        }
                        else iDialing=0;
                        
                     }
                     break;
                  case 'm':case 'M':
                  {
                      if(!ph)return T_TRY_OTHER_ENG;
                     startEngine();
                     iLen-=3;//rem ":m "
                     char buf[2048];
                     
                     if(iLen>2000) iLen=2000;
                     memcpy(buf,p+3,iLen);
                     buf[iLen]=0;
                     
                     char *pUn=buf;
                     char *pMsg=pUn;
                     
                     while(*pMsg && *pMsg!=' ')  pMsg++;
                     if(*pMsg!=' ')return -1;
                     
                     *pMsg=0;//z-terminte UN
                     pMsg++;
                     //puts(pUn);puts(pMsg);
                     CTEditBase e(pMsg);
                     ph->sendMsg(0, pUn, NULL, &e);
                     
                     break;
                  }
                     
                     
               }
               return 0;
            }
            else if(iLen==6 && strcmp(p+1,"rereg")==0){
               //p_cfg.iIsInForeground=0;
               if(p_cfg.iAccountIsDisabled)return T_TRY_OTHER_ENG;

               
               getLocalIpNow();
               
               p_cfg.iCanRegister=1;
               p_cfg.iUserRegister=1;
               p_cfg.iReRegisterNow=1;
               
               puts("rereg");
               return T_TRY_OTHER_ENG;
               
            }
            else if(iLen==5 && strcmp(p+1,"onka")==0){
               //p_cfg.iIsInForeground=0;
               if(p_cfg.iAccountIsDisabled)return T_TRY_OTHER_ENG;
#ifdef __APPLE__
               iIsInBackground=1;
#endif
               
               getLocalIpNow();
               
               p_cfg.iCanRegister=1;
               p_cfg.iUserRegister=1;
               p_cfg.iReRegisterNow=1;
               dtmfPlay.stop();
               
               puts("!foreground");
               return T_TRY_OTHER_ENG;
               
            }
            else if(iLen == sizeof("force.network_reset") && strcmp(p+1,"force.network_reset")==0){
               if(ph){
                  ph->onNewIp(0,0);
                  ph->onNewIp(getLocalIpNow(), 1);//will recreate the SIP socket
               }
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==14 && strcmp(p+1,"sock.recreate")==0){
               t_logf(log_events, __FUNCTION__, "sockdebug: SPA code forcing socket recreation");
               if(ph)ph->sockSip.forceRecreate();
               return T_TRY_OTHER_ENG;
            }
            /*
            else if(iLen==13 && strcmp(p+1,"sip.recreate")==0){
               if(ph)ph->sockSip.forceRecreate();
               return T_TRY_OTHER_ENG;
            }
             */
            else if(iLen==12 && strcmp(p+1,"waitOffline")==0){
               if(p_cfg.iAccountIsDisabled)return T_TRY_OTHER_ENG;
               
               if(ph)ph->waitOffline();
               return T_TRY_OTHER_ENG;
               
            }
            else if(iLen==16 && strcmp(p+1,"beforeCfgUpdate")==0){
               
               iSipPort=p_cfg.iSipPortToBind;
               iRtpPort=p_cfg.iRtpPort;
               //TODO detect changes
               p_cfg.iUserRegister=0;
               p_cfg.iCanRegister=0;
               if(ph && p_cfg.isOnline())
               {
                  ph->remRegister();
               }
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==15 && strcmp(p+1,"afterCfgUpdate")==0){
 
               if(p_cfg.iNeedSave){
                  if(!p_cfg.iAccountIsDisabled)checkCfgChanges(0);
                  if(!p_cfg.iAccountIsDisabled)p_cfg.iReRegisterNow=1;
                  saveCfg(&p_cfg,iEngineIndex);
               }
 
               if(p_cfg.iAccountIsDisabled)return T_TRY_OTHER_ENG;
               p_cfg.iCanRegister=1;
               p_cfg.iUserRegister=1;
               startEngine();
               return T_TRY_OTHER_ENG;
               
            }
            else if(iLen==13 && strcmp(p+1,"onbackground")==0){
               p_cfg.iIsInForeground=0;
            }
            else if(iLen==13 && strcmp(p+1,"onforeground")==0){
               p_cfg.iIsInForeground=1;
               iIsInBackground=0;
               if(p_cfg.uiPrevExpires<800 && p_cfg.uiPrevExpires>0)p_cfg.uiExpires=p_cfg.uiPrevExpires;
               p_cfg.uiPrevExpires=0;
               static int iCollected=0;
               if(!iCollected){
                  iCollected=1;
                  //--test-- CTEntropyColector::colect();
               }
               
               if(ph && !p_cfg.isOnline())ph->goOnline(1);
               
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==4 && strcmp(p+1,"reg")==0)
            {
               
               //rem old reg
               if(p_cfg.iAccountIsDisabled)return T_TRY_OTHER_ENG;
               startEngine();
               
               p_cfg.iCanRegister=1;
               p_cfg.iUserRegister=1;
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==6 && strcmp(p+1,"unreg")==0)
            {
               
               p_cfg.iUserRegister=0;
               p_cfg.iCanRegister=0;
               puts("rem reg on exit 1");
               
               if(ph && p_cfg.isOnline())
               {
                  puts("rem reg on exit 2");
                  ph->remRegister();
               }
               if(p_cfg.iAccountIsDisabled){stopEngine();}
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==7 && strcmp(p+1,"mute 1")==0)
            {
               mute.setMute(1);puts("mute mic");
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==7 && strcmp(p+1,"mute 0")==0)
            {
               mute.setMute(0);puts("unmute mic");
               return T_TRY_OTHER_ENG;
            }
#if defined(ANDROID_NDK)
            else if(iLen==6 && isdigit(p[5]) && strncmp(p+1,"aec ",4)==4){
               void setSpkrModeAEC(void *aec, int echoMode);
               
               int m=p[5]-'0';
               tivi_log1("set aec mode:", m);

               setSpkrModeAEC(NULL, m);
               
            }
#endif
            else if(iLen==12 && strcmp(p+1,"GSMactive 1")==0)
            {
               p_cfg.iGSMActive=1;
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==12 && strcmp(p+1,"GSMactive 0")==0)
            {
               p_cfg.iGSMActive=0;
               return T_TRY_OTHER_ENG;
            }
            else if(iLen==5 && strcmp(p+1,"stat")==0)
            {
#define MAX_LOADSTRING 500
               unsigned short szHello[MAX_LOADSTRING];
               iLen=ph->getRegInfo((char *)&szHello,MAX_LOADSTRING/2);
               if(iLen)
               {
                  convert8_16((char *)&szHello,iLen);
                  CTStr b(szHello,iLen);
                  info(&b,0,0);
               }
            }
            else{puts("Unknown command");usage();}
         }
         else{puts("Unknown command");usage();}
      }
      return 0;
   }
   int iSipPort;
   int iRtpPort;
   int iPrevCfgChanges;
   char bufPrevCodecs[128];
   char prevTransport[16];
   
   void dbg(char *p, int iLen){
      printf("[%.*s]",iLen,p);
   }
   
   void checkCfgChanges(int iReg=1){
      
      if(p_cfg.iAccountIsDisabled)return;
      
      
      if(ph && (p_cfg.iCfgChangesDetected!=iPrevCfgChanges) && iRun){
         iPrevCfgChanges=p_cfg.iCfgChangesDetected;
         p_cfg.iResetStun=1;
         //TODO  move to eng
         int ch=0;
         //TODO savePrevCfg
         //TODO addCfgParamCB(char *key, int iLen, void *ret, void *cfg
         if(p_cfg.iSipPortToBind!=iSipPort && iSipPort)
         {
            ph->sockSip.setNewPort(p_cfg.iSipPortToBind);
            ch=1;
         }
         iSipPort=p_cfg.iSipPortToBind;
         
         if(p_cfg.iRtpPort!=iRtpPort)
         {
            ph->cPhoneCallback->mediaFinder->setNewPort(p_cfg.iRtpPort);
            ch=1;
         }
         iRtpPort=p_cfg.iRtpPort;
         if(strcmp(p_cfg.szACodecs,bufPrevCodecs)!=0){
            strcpy(bufPrevCodecs,p_cfg.szACodecs);
            ph->setCodecs(&p_cfg.szACodecs[0]);
         }
         if(strcmp(p_cfg.szSipTransport,prevTransport)!=0){
            strcpy(prevTransport,p_cfg.szSipTransport);
            ph->checkSIPTransport();
         }
         if(!p_cfg.iZRTP_On)p_cfg.iCanUseZRTP=0;
         else if(p_cfg.iZRTP_On && !p_cfg.iCanUseZRTP){
            p_cfg.iCanUseZRTP=1;//TODO check lic here
         }
         
         if(p_cfg.tmpServ[0] && p_cfg.iDetectedNewServDomain){//should be offline
            p_cfg.iDetectedNewServDomain=0;
            
            if(strcmp(p_cfg.tmpServ,p_cfg.str32GWaddr.strVal)!=0){
               
               strcpy(p_cfg.str32GWaddr.strVal,p_cfg.tmpServ);
               p_cfg.str32GWaddr.uiLen=strlen(p_cfg.str32GWaddr.strVal);
               
               
               ph->checkServDomainName(0,1);
               
               printf("[gw=%s]",p_cfg.str32GWaddr.strVal);
               
               if(iReg) command(":reg");
               
            }
         }
         if(ch)ph->reInvite();
         
      }
   }
   void onTimer(){
      
#ifdef __APPLE__
      p_cfg.uiZZRTP_bus_lic_exp_at=1334659232+60*60*24*30*120;
#endif
      static int iCnt=0;
      iCnt++;
      if((iCnt&31)==1|| (ph->iActiveCallCnt)){
         
         float cpu_usage();
         float f=cpu_usage();
         if(f>.2){
            //g_iWarning_CPU_is_too_hot=(f>70);
            int _TODO_if_cpu_is_hot_set_warningFlag;//skip video b frame decode,//skip b frame loopfilter
            printf("cpu=%f\n",f);
         }
         
      }
      
      if(ph)
         checkGlobalSettings(ph->pZrtpGlob);
      //--  checkCfgChanges();
      
      /*
       //cpu tester
       static int x;
       x++;
       if(x==10){
       int z=0;
       for(int i=0;i<100000000;i++){
       z*=z;
       z++;
       if(i+1==100000000){
       printf("mcpu=%f %d\n",cpu_usage(),z);
       }
       }
       x=0;
       }
       */
      
   }
   
   ~CPhoneCons()
   {
      iRun=0;
      puts("Exiting...");
      if(!ph)return ;
      int r=ph->closeEngine();
      Sleep(r>0?20:100);
      
      delete ph;
   }
   CTBMsg bubbleMsg;
   
   int info(CTStrBase *e, int iType, int SesId){
      if(iRun==0)return 0;
      
      mutexResp.lock();
      bubbleMsg.create(p_cfg.szTitle[0]?p_cfg.szTitle:p_cfg.str32GWaddr.strVal,e,iType);
      mutexResp.unLock();
      
      if(iType=='ORRE'){
         getText(&bufLastErrMsg[0],sizeof(bufLastErrMsg)-1,e);
         if(iDialing && !SesId){
            sendCB(CT_cb_msg::eError,0,&bufLastErrMsg[0]);
         }
         else if(SesId){
            sendCB(CT_cb_msg::eError,SesId,&bufLastErrMsg[0]);
         }
         else sendCB(CT_cb_msg::eError,SesId);
         
      }
      return 0;
   }
   int onCalling(ADDR *addr, char *sz, int iLen, int SesId){
      
      iCallId=SesId;
      resetDataOnCallStart();
   
      sendCB(CT_cb_msg::eCalling,SesId,sz,iLen);
      dtmf.clean();
      
      
      return 0;
   }
   
   int onEndCall(int SesId, int iDontShowMissed){
#ifndef T_ENABLE_MULTI_CALLS
      if(iCallId==SesId)
#endif
      {
         g_conf.remCall((CSesBase*)SesId);
         iFlagResetEC=0;//if no calls left// are 0
         if(iCallId==SesId)iCallId=0;
         
         if(iDontShowMissed){
            sendCB(CT_cb_msg::eEndCall,SesId, "Call completed elsewhere");
         }
         else{
            sendCB(CT_cb_msg::eEndCall,SesId);
         }
         
      }
      return 0;
   }
protected:
   void usage()
   {
      puts("----------help ");
      puts("   :h - show this");
      puts("   :q - quit");
      puts("   :reg - Login");
      puts("   :unreg - Logout");
      puts("   :c [Username | phone number | ip | sip uri] - new call");
      puts("   :m [Username | phone number | ip | sip uri] [message]  - send meesage");
      puts("   :a - answer call");
      puts("   :e - end call");
      puts("   :t - test audio");
      puts("   :w [seconds]- wait");
   }
   void beep(int iFreq=3500){}
   int message(CHAT_MSG *msg, int iType, int SesId, char *from, int iFromLen)
   {
      
      CTEditBase b(msg->strMsg.iLen+msg->strUN.iLen+20);
      b.setText("Msg ");
      b.addText(msg->strUN.p,msg->strUN.iLen);
      b.addText(": ");
      
      if(msg->strMsg.eType==CHAT_MSG::EUtf16)
         b.addText(msg->strMsg.p,msg->strMsg.iLen/2,1);
      else
         b.addText(msg->strMsg.p,msg->strMsg.iLen);
      
      info(&b,0,0);
      beep(2000);
       char out[2048];
       int ll=sizeof(out)-1;
       sendCB(CT_cb_msg::eMsg,0, b.getTextUtf8(out, &ll));
      return 0;
   }//or fnc ptr
   
   int registrationInfo(CTStrBase *e, int iType){
      if(iRun==0)return 0;
      
      switch(iType){
            
         case eOffline:
         case eErrorAndOffline:
            getText(&bufLastRegErr[0],sizeof(bufLastRegErr)-1,e);
            break;
            
         case eOnline:
         case eConnecting:
         case eInfo:
            bufLastRegErr[0]=0;
            break;
      }
      
      info(e,iType,0);
      
      t_logf(log_events, __FUNCTION__, "registrationInfo=%d idx=%d msg:%s", iType,p_cfg.iIndex, &bufLastRegErr[0]);
      /*
      char buf[128];
      snprintf(buf, sizeof(buf), "p=%p ison=%d unreg=%d net=%d u=%llu",this, p_cfg.isOnline(),p_cfg.reg.bUnReg, p_cfg.iHasNetwork,p_cfg.reg.uiRegUntil);
      tmp_log(buf);
      */
      sendCB(CT_cb_msg::eReg,0,&bufLastRegErr[0]);
      
      return 0;
   }
   
   CTSesMediaBase* tryGetMedia(const char *name){
      if(strcmp(name,"video")==0 && audioMngr.availableVO())return mediaFinder->findMedia("video",5);
      if(audioMngr.availableAO())return mediaFinder->findMedia("audio",5);
      
      return NULL;
   }
   
   int canAccept(ADDR *addr, char *sz, int iLen, int iSesId, CTSesMediaBase **media)
   {
#ifdef T_ENABLE_MULTI_CALLS
      if(audioMngr.availableAO())
#else
         if(iCallId==0 && audioMngr.availableAO())
#endif
         {
            CTSesMediaBase *m=tryGetMedia("audio");
            *media = m;
            if(m)return 1;
            
         }
      
#ifndef _WIN32      
#warning TODO notify gui about  missed call
#endif
      info(&ph->strings.lMissCall,0,0);
      return 0;
   }
   
   void resetDataOnCallStart(){
      
      if(ph->getCallCnt()!=1)return;//TODO checkOther cfgs
      
      audioMngr.cAI.packetTime(p_cfg.payloadSize());//TODO useSmallestCallPS
      
      dtmfPlay.stop();
      
      audioMngr.cVI.setVideoEveryMS(p_cfg.iVideoFrameEveryMs);
      
   }
   
   
   

   int onSdpMedia(int SesId, const char *media){
      sendCB(CT_cb_msg::eNewMedia,SesId,media,strlen(media));
      return 0;
   }
   
   int onIncomCall(ADDR *addr, char *sz, int iLen, int SesId){
      
      iCallId=SesId;
      resetDataOnCallStart();
      
      char out[256];
      char *pOut=&out[0];
      int iSipLen=0;
      
      if(iLen>4 && strncmp(sz,"sip:",4)==0){iSipLen=4;}
      else if(iLen>5 && strncmp(sz,"sips:",5)==0){iSipLen=5;}
      
      if(iSipLen){
        // strncpy(out,sz,iSipLen);
         sz+=iSipLen;iLen-=iSipLen;
      }
      
      pOut=CTEngUtils::checkAddr(p_cfg, &out[iSipLen], sizeof(out)-iSipLen-1,sz,iLen);
      pOut=CTEngUtils::tryCleanNR(pOut, iLen);
      pOut=CTEngUtils::tryRemovePlus1IfNot11Digits(pOut, iLen);//PRZ
      
      //iLen+=iSipLen;pOut=&out[0];//TODO use if(iSipLen){iLen+=iSipLen;strncpy(pOut-iSipLen,sz-iSipLen,iSipLen);pOut-=iSipLen;}
      
      CTEditBuf<128> b;
      b.addText(ph->strings.lIncomCall);
      b.addText(" ");
      b.addText(pOut,iLen);
      
      info(&b,0,0);
      
      sendCB(CT_cb_msg::eIncomCall,SesId,pOut,iLen);
      
      return 0;
   }
   
   int onStartCall(int SesId)
   {
      audioMngr.vad.iSendNextNPackets=50*5;
      CTEditBuf<128> b;
      b.setText("Connected,cid=");
      CSesBase *ses=ph->findSessionByID(SesId);
      if(ses){
         char *p=ses->sSIPMsg.dstrCallID.strVal;
         int l=ses->sSIPMsg.dstrCallID.uiLen;
         if(p && l>0 && l<100)b.addText(p, l);//cid //2o2mxeOYAdlfO9F1
      }
      info(&b,0,0);
      sendCB(CT_cb_msg::eStartCall,SesId);
      return 0;
   }
   
   
   //TODO call utils
   int ringSecondary(CSesBase *ses){
      if(!ph || !ses)return -1;
      
      CTAudioOutVOIP *ao=(CTAudioOutVOIP*)ph->findSessionAO(ses);
      if(!ao)return -1;
      
      unsigned int uiBufSize=(unsigned int)ao->getBufSize();
      int iRate=ao->getRate();
      char *buf=new  char [uiBufSize+2];
      
      memset(buf,0,uiBufSize);
      unsigned int m=iRate*3;
      if(m>uiBufSize)m=uiBufSize;
      
      genereteTone(420,iRate, 30, iRate*2/10*2,iRate*2/10*6,buf, m);//2 short beeps
      if(uiBufSize>iRate*6*2)memcpy(buf+uiBufSize/2, buf, uiBufSize/2);
      
      ao->setPlayPos(0);
      ao->update(buf,(int)uiBufSize,0,0);
      ao->play();
      
      delete buf;
      
      return 0;
      
   }
   
   int onRecMsg(int id,int SesId, char *sz, int iLen){
      if(p_cfg.iAutoAnswer && id==METHOD_BYE)return 0;
      if(id==183 || id==180)
         sendCB(CT_cb_msg::eRinging,SesId);
      else{
         if(id>=100 && id<200)sendCB(CT_cb_msg::eSIPMsg, SesId,sz,iLen);
      }
      if(id==180 || id==METHOD_BYE)
      {
         CTAudioOutVOIP *ao=(CTAudioOutVOIP*)ph->findSessionAO(ph->findSessionByID(SesId));
         if(!ao)return 0;
         
         unsigned int uiBufSize=(unsigned int)ao->getBufSize();
         int iRate=ao->getRate();
         char *buf=new  char [uiBufSize+2];
         memset(buf,0,uiBufSize);
         if(id==180)
         {
            genereteTone(420,iRate, 20, iRate*2,iRate*4,buf, uiBufSize);
         }
         else
         {
            genereteTone(420,iRate, 20, iRate,iRate,buf, uiBufSize);
         }
         if(id==METHOD_BYE){
            ao->setIgnoreIncomingRtp(1);
         }
         ao->setPlayPos(0);
         ao->update(buf,(int)uiBufSize,0,0);
         
         if(id!=METHOD_BYE || ao->lastPlayBufSinceMS()<3000){
            if(id!=METHOD_BYE  || p_cfg.iIsInForeground){
               ao->play();
            }
            
            if(id==METHOD_BYE)
            {
               ao->stopAfter(1800);
            }
         }
         delete buf;
         
      }
      
      return 0;
   }
#if 1
  // char bufETmp[640*32];
//   
   void doEC(char *p, int iLen, int iIsMicSilent){//TODO put it into lower level
      if(iLen>80){

#ifdef _WIN32
         void *initAEC(int iRate);
         int playbackBufAEC(void *aec, short *p, int iSamples);
         int recordProcessAEC(void *aec, short *p, short *outBuf, int iSamples, int delayMS);
static char bufETmp[640*32];
         CTAOut &cAO=audioMngr.cAO;
         int iRate=audioMngr.cAI.getRate();
         static void *aec = initAEC(iRate);

         if(iFlagResetEC){
         }
         
         if(!p){
            p=&bufETmp[0];memset(p,0,min(iLen,640*16));
         }
         int bl=iLen/320;

         int bi=cAO.fifoBuf.bytesIn();
         
         if(bi>1280*4){
            cAO.fifoBuf.remBytes(bi-1280*2);
            bi=cAO.fifoBuf.bytesIn();
         }
         int bli = bi/320;

         while(bi>=320 && bli>bl){
            playbackBufAEC(aec, (short *)cAO.fifoBuf.get(320),160);
            bi=cAO.fifoBuf.bytesIn();
            bli--;
         }

         short out[8000];
         int d=30+bl*10;

         int iOut=0;
         for(int z=0;z<iLen;z+=320){
            bi=cAO.fifoBuf.bytesIn();
            if(bi>=320)
               playbackBufAEC(aec, (short *)cAO.fifoBuf.get(320),160);
            recordProcessAEC(aec, (short*)(p+z),&out[iOut],160,d);d-=10;
            iOut+=160;
         } 
         memcpy(p,out,iLen);
      //printf("ec2\n");
#endif
      }
   }
#endif
   

   
public:
   void videoIn(unsigned char *p, int iLen, unsigned int uiPos){
      if(!p_cfg.iIsInForeground){
         
         p=NULL;iLen=0;
         puts("[warn: videoIn !p_cfg.iIsInForeground]");
      }
      
      if(ph) ph->onDataSend((char *)p,iLen,uiPos,CTSesMediaBase::eVideo,0,0);
      
   }
   void audioInFromMultiAccounts(char *p, int iLen, unsigned int uiPos, int iIsFirst, int iWhisperingDetected){
      
      int iTmpMute=mute.isMuted() || CTUtilDataPlayer::mute_mic();
      
      
      if(iIsFirst){
         
         CTEntropyCollector::tryAddEntropy(p,iLen);
#ifdef _WIN32
         //ndef __APPLE__
         if(iEchoCancellerOn){
#ifdef _WIN32
            iEchoCancellerOn=1;//windows
#endif
            if(iEchoCancellerOn==1)
               doEC(p,iLen,0);
         }
#endif
         if(g_conf.callsInConf()){
            g_conf.onAudioData(p, iLen,uiPos, 1,iTmpMute, iWhisperingDetected);
         }
      }
      
      int iDtmf=-1;
      int iIsNewDtmf=0;
      if(dtmf.mustSend(uiPos,&iDtmf,&iIsNewDtmf)){
         
         int iLast  = !dtmf.mustSendNext();
         if(iLast){
            //resend last
            ph->onDataSend(dtmf.p,4,dtmf.uiStartTS,CTSesMediaBase::eDTMF,0,0);
            ph->onDataSend(dtmf.p,4,dtmf.uiStartTS,CTSesMediaBase::eDTMF,0,0);
            
            dtmfPlay.stop();
         }
         return ph->onDataSend(dtmf.p,4,dtmf.uiStartTS,CTSesMediaBase::eDTMF,0,0);
      }
      
      
      if(iTmpMute){
         //the next line must not be enabled
         //if(mute.isMuted() && !mute.shouldSendCN(iLen>>1))return; //// don't interupt music onhold
         //printf("[send CN %u]",getTickCount()/20);
         return ph->onDataSend(NULL,iLen,uiPos,CTSesMediaBase::eAudio,0,0);//send CN
      }
      
      int iIsVoice=1;
      if(ph->vadMustBeOn())//cn is on
      {
         iIsVoice = audioMngr.vad.prevResult();
      }
      
      ph->onDataSend((char *)p,iLen,uiPos,CTSesMediaBase::eAudio,iIsVoice, iWhisperingDetected);
      
      
   }
   
   
public:
   int getLocalIpNow(){return getLocalIP_F(1);}
   
   int getLocalIp(){return getLocalIP_F(0);}
   
   int getIpByHost(char *sz, int iLen)
   {
      int r= CTSock::getHostByName(sz,iLen);
      return r;
   }
private:
   inline int getLocalIP_F(int iNow){
      int iIs3G=-1;
      int r=CTGetIP::getIPLoc(iNow,&iIs3G);
      if(iIs3G!=-1)p_cfg.iNetworkIsMobile=iIs3G;
      return r;
   }
   
   
};

void adbg(char *p, int v){
   //  printf("%s %d",p,v);
}

#ifdef _WIN32
class CC{
public:
   CC(){
      WSADATA	wsaData;
      WSAStartup(MAKEWORD( 2, 2), &wsaData);
   }
   ~CC(){
      WSACleanup();
   }
   
};
CC cc;

#endif

typedef struct{
   
   CPhoneCons *ph;
   int iInUse;
   
}T_ACCOUNT;

#define _BUF_X_SIZE 2047

CPhoneCons * cPhone=NULL;


void *findGlobalCfgKey(char *key, int iKeyLen, int &iSize, char **opt, int *type);
void *findGlobalCfgKey(const char *key);

class CTPhoneMain: public CTAudioCallBack, public CTVideoCallBack{
   int iStarted;
   int iExiting;
   int iLastTmpCallIDToStop;
   int iTmpCallID;
public:
#if defined(__linux__) && !defined(ANDROID_NDK)
   enum{eAccountCount=1001};
#else
   enum{eAccountCount=11};
#endif
   T_ACCOUNT account[eAccountCount];
   int iCurrentDialOutIndex;
   
   
   
   void videoIn(unsigned char *p, int iLen, unsigned int uiPos){
      if(iExiting)return;
      for(int i=0;i<eAccountCount;i++){
         if(account[i].iInUse==1 && account[i].ph && !account[i].ph->p_cfg.iAccountIsDisabled && account[i].ph->ph){
            account[i].ph->videoIn(p,iLen,uiPos);
         }
      }
      
   }
   
   void audioIn(char *p, int iLen, unsigned int uiPos){
      
      if(iExiting)return;
      
      CPhoneCons *ph[eAccountCount];
      int iVad=0;
      int iCnt=0;
      int iWhisperingDetected=0;
  
      for(int i=0;i<eAccountCount;i++){
         if(account[i].iInUse==1 && account[i].ph && !account[i].ph->p_cfg.iAccountIsDisabled
            && account[i].ph->ph && account[i].ph->ph->iActiveCallCnt){
            
            ph[iCnt]=account[i].ph;
            if(!iVad && (ph[iCnt]->p_cfg.useVAD() || ph[iCnt]->ph->vadMustBeOn()))iVad++;
            if(!iWhisperingDetected)iWhisperingDetected = account[i].ph->ph->whisperingDeteced();
            iCnt++;
         }
      }
      
      if(iVad){
         audioMngr.vad.iIsZRTPActive=1;
         audioMngr.vad.iAdjustRandom=1;
         audioMngr.vad.isSilence2((short*)p,iLen/2,audioMngr.cAI.getRate());
      }
      else
         audioMngr.vad.onResetPrevResult();
      
      int iIsFirst=1;
      
      for(int i=0;i<iCnt;i++){
         ph[i]->audioInFromMultiAccounts(p,iLen,uiPos,iIsFirst,iWhisperingDetected);
         iIsFirst=0;
      }
      
   }
   
   
   CTPhoneMain(){
      iExiting=0;
      iStarted=0;
      iCurrentDialOutIndex=0;
      for(int i=0;i<eAccountCount;i++)memset(&account[i],0,sizeof(T_ACCOUNT));
      //memset(this,0,sizeof(*this));
      pQViewVO=pQViewVI=NULL;
      fncCB=NULL;pRetCB=NULL;
      empty=NULL;
      iLastTmpCallIDToStop=0;
      iTmpCallID=0;
   }
public:
   ~CTPhoneMain(){
      destroy();
      
   }
private:
   void destroy(){
      if(iExiting)return;
      iExiting=1;
      iLastTmpCallIDToStop=0x7fffffff;
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(ret){
            puts("rem reg on exit");
            ret->command(":unreg");
         }
      }
      for(int i=0;i<eAccountCount;i++){
         if(!account[i].iInUse)continue;
         account[i].iInUse=0;
         CPhoneCons *ret=account[i].ph;
         account[i].ph=NULL;
         if(ret){
            
            delete ret;
         }
      }
      if(empty)delete empty;
      empty=NULL;
      //?? checkGlobalSettings(NULL);
   }
public:

   
   void stopDeleteDisabled(){
      int iDeleted=0;
      CPhoneCons *a[eAccountCount];
      
      for(int i=0;i<eAccountCount;i++)
         a[i]=getAccountByID(i,0);
      
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=a[i];
         if(ret){
            
            deleteAccount(ret);
            
            iDeleted++;
            if(iDeleted>eAccountCount)break;
         }
      }
   }
   
   void start(){
      if(iStarted ){for(int i=0;i<10 && iStarted==2;i++)Sleep(20);return;}
      iStarted=2;
      audioMngr.cAI.cb=this;
      audioMngr.cVI.cb=this;
      
      CTEditBase b(1024);
      
      for(int i=0;i<eAccountCount;i++){
         setCfgFN(b,i);
         if(isFileExistsW(b.getText())){
            load(i);
         }
      }
      setCurDOFromCfg();
      cPhone=getAccountByID(0,1);
      iStarted=1;
      
      //load_ph_cfg
   }
   void stop(){
      
   }

   CPhoneCons *isThisEngine(CPhoneCons *ret, const char *p, int iPLen, const char *name){
      if(ret){
         if(name){
            if(strcmp(ret->p_cfg.szTitle,name)==0)return ret;
            if(strcmp(ret->p_cfg.str32GWaddr.strVal,name)==0)return ret;
         }
         else{
            if(p && iPLen>0){
               char *ps=ret->p_cfg.str32GWaddr.strVal;
               int iSLen=(int)ret->p_cfg.str32GWaddr.uiLen;
               if(iPLen>iSLen && strncmp(p+iPLen-iSLen,ps,iSLen)==0)return ret;
            }
         }
      }
      return NULL;
   }
   void *findBestEng(const char *p, const char *name){
      int iLen=p?strlen(p):0;
      void *pRet=isThisEngine(getAccountByID(iCurrentDialOutIndex,1),p,iLen,name);
      if(pRet)return pRet;
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(!ret)break;
         ret=isThisEngine(ret,p,iLen,name);
         if(ret)return ret;
         
      }
      return NULL;
   }
   
   typedef struct{
      enum {eMaxAccounts=5};
      
      int iTmpCallID;
      
      CTPhoneMain *self;
      
      int iCnt;
      
      struct{
         CPhoneCons *eng;
         int iOptionsCallID;
         int iIsOnline;
      }eng[eMaxAccounts];
      
      char cmd[128];
 
   }T_ENG_LIST_CALL_TO;
   
   void callTo(void *pEng, const char *cmd){
      
      CPhoneCons *eng=(CPhoneCons*)pEng;
      if(!eng){
         eng=getAccountByID(iCurrentDialOutIndex,1);
      }
      
      T_ENG_LIST_CALL_TO *pl=new T_ENG_LIST_CALL_TO;
      
      if(!pl)return;
      
      T_ENG_LIST_CALL_TO &l =*pl;
      
      memset(pl, 0, sizeof(T_ENG_LIST_CALL_TO));
      l.eng[0].eng=eng;
      l.iCnt=1;
      int iAccounts=0;
      
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(!ret)continue;
         iAccounts++;
         // tivi_log1("ret->ph",(int)ret->ph);
         // tivi_log1(ret->p_cfg.GW.bufAddr,0);
         // tivi_log1(ret->p_cfg.szTitle,0);
         if(ret!=eng && ret->ph && isSameAccount(ret,eng)
            //ret->p_cfg.szTitle[0] && strcmp(ret->p_cfg.szTitle,eng->p_cfg.szTitle)==0
            ){
            l.eng[l.iCnt].eng=ret;l.iCnt++;
            if(l.iCnt>=T_ENG_LIST_CALL_TO::eMaxAccounts)break;
         }
      }
      if(l.iCnt <= 1){
         tivi_log1("l.iCnt=1 accounts=", iAccounts);
         if(eng)eng->command(cmd);
         delete pl;
         return;
      }
      
      iTmpCallID++;
      l.iTmpCallID=iTmpCallID;
      l.self=this;
      
      strncpy(l.cmd, cmd, sizeof(l.cmd)-1);
      l.cmd[sizeof(l.cmd)-1]=0;
      
      CTThread *th=new CTThread();
      th->destroyAfterExit();
      th->create(makeCallTh,pl);
      //   tivi_log1("l.iCnt",l.iCnt);
      
      //TODO set TmpID
   }
   
   static int waitOnlineCT(T_ENG_LIST_CALL_TO *pl, int iMaxWaitMS){
      int iOnlineCnt=0;
      int ip=hasIP();
      for(int t=0;t<iMaxWaitMS;t++){
         
         if(!hasIP()){t+=100;Sleep(100);continue;}
         if(!ip){ip=1;Sleep(100);t+=100;}
         
         iOnlineCnt=0;
         for(int i=0;i<pl->iCnt;i++){
            int v = !!pl->eng[i].eng->p_cfg.isOnline();;
            iOnlineCnt+=v;
            pl->eng[i].iIsOnline=v;
         }
         if(iOnlineCnt==pl->iCnt){break;}
         if(iOnlineCnt && t>1000){break;}
         Sleep(20);t+=20;
         //TODO force go online
         //if(iFOn)
      }
      return iOnlineCnt;
   }
   
   static void notifyEndCall(T_ENG_LIST_CALL_TO *pl, const char *un, const char *msg, CTEditBase *e=NULL){
      CTEditBuf<128> b;
      if(!e){
         e=&b;
         b.setText(msg);
      }
      
      //fake call id
      int cid=15+(pl->iTmpCallID&15);
      pl->eng[0].eng->onCalling(&pl->eng[0].eng->p_cfg.GW, (char*)un, strlen(un), cid);
      pl->eng[0].eng->info(e,*(int *)"ERRO",cid);
      pl->eng[0].eng->onEndCall(cid,0);
   }
   
   static int waitResp(T_ENG_LIST_CALL_TO *pl, int *resp){
      int iFirstOk=-1;
      int iFailCnt=0;
      int e=3,j;
      
      Sleep(100);
      
      int iRespCnt;
      int iOkCnt=0;
      
      for(j=0;pl->iTmpCallID>pl->self->iLastTmpCallIDToStop;j++){//|| not canceled
         
         if(j>=299){
            //send msg to user
            //check network
            e=1;
            break;
         }
         
         Sleep(100);
         
         iFailCnt=0;
         iRespCnt=0;
         
         for(int i=0;i<pl->iCnt;i++){
            //!pl->eng[i].iIsOnline  - we did not send options to this account
            if(resp[i] || !pl->eng[i].iOptionsCallID)iRespCnt++;
            
            if(resp[i]==200 || (resp[i]>=300 && resp[i]<400)){if(iFirstOk==-1)iFirstOk=i;iOkCnt++;}
            else if(resp[i]<0 || resp[i]>=400)iFailCnt++;
         }
         
         if(iFailCnt==pl->iCnt){e=2;break;}//fail
         if(iRespCnt==pl->iCnt)break;
         //iOkCnt >= 3 - push notifs
         if(iFirstOk>=0 && iOkCnt >= 3){

            break;
         }
      }
      if(iFirstOk<0)iFirstOk=-e;
      return iFirstOk;
   }
   
   static int makeCallTh(void *p){
      
      int resp[T_ENG_LIST_CALL_TO::eMaxAccounts];
      CTEditBuf<128> respMsg[T_ENG_LIST_CALL_TO::eMaxAccounts];
      
      T_ENG_LIST_CALL_TO *pl=(T_ENG_LIST_CALL_TO *)p;
      
      int iOnlineCnt = waitOnlineCT(pl, 3000);
      
      if(!iOnlineCnt){
         for(int i=0;i<pl->iCnt;i++){
            pl->eng[i].eng->command(":reg");
         }
         iOnlineCnt = waitOnlineCT(pl, 2000);
      }
      
      char *un=&pl->cmd[3];//":c "
      
      if(!iOnlineCnt && !hasIP()){
         notifyEndCall(pl, un, "No network");
         delete pl;
         return 0;
      }
      if(!iOnlineCnt){
         //should i show last reg error ??
         
         notifyEndCall(pl, un, NULL, &strings.lCouldNotReachServer);
         delete pl;
         return 0;
      }
      
      CPhoneCons *firstOnlineEng=NULL;
      
      for(int i=0;i<pl->iCnt;i++){
         resp[i]=0;
         respMsg[i].reset();
         pl->eng[i].iOptionsCallID = 0;
         if(pl->eng[i].iIsOnline){
            pl->eng[i].iOptionsCallID = pl->eng[i].eng->ph->isDstOnline(un, &resp[i], &respMsg[i]);
            if(!firstOnlineEng && pl->eng[i].iOptionsCallID)firstOnlineEng=pl->eng[i].eng;
         }
         else resp[i]=-1;
      }
      if(!firstOnlineEng){
         notifyEndCall(pl, un, NULL, &strings.lCouldNotReachServer);
         delete pl;
         return 0;
      }
#if 1

      int iFirstOk=waitResp(pl,resp);

#endif
   
      for(int i=0;i<pl->iCnt;i++){
         if(pl->eng[i].eng->ph && pl->eng[i].iOptionsCallID)
            pl->eng[i].eng->ph->removeRetMsg(pl->eng[i].iOptionsCallID);
      }
      
      if(pl->iTmpCallID>pl->self->iLastTmpCallIDToStop){//check if not canceled by caller
      
         if(iFirstOk>=0){
            int iTryReset = (respMsg[iFirstOk] == "OK -waking up");
            if(iTryReset){
               //wait here for responce from second account, we should try to switch
               Sleep(200);
               for(int i=0;i<pl->iCnt;i++){
                  
                  if(i==iFirstOk)continue;
                  
                  if(resp[i]==200 && respMsg[i]=="OK"){
                     iFirstOk=i;
                     break;
                  }
               }
            }
            
            
            pl->eng[iFirstOk].eng->command(pl->cmd);
            
            if(pl->iTmpCallID<=pl->self->iLastTmpCallIDToStop){
               //TODO make something better
               pl->eng[iFirstOk].eng->command(":e");//end last call
            }
            
         }
         else{
            //TODO wait 3 sec try Again
            CTEditBase *servMsg=NULL;
            
            for(int i=0;i<pl->iCnt;i++){
               if(respMsg[i].getLen()>0){servMsg=&respMsg[i];break;}
            }
            
            if(servMsg){
               //number,user does not exist or user is not online
               //[Not Found -user not online]
               if(servMsg->getLen()>12 && servMsg->cmpN(CTEditBuf<32>("Not Found -"), 11)){
                  servMsg->remCharsFrom(11,11);
                  //??  if(servMsg->getChar(0)=='u')servMsg->pData[0]='U';
               }
               
               notifyEndCall(pl,un,NULL, servMsg);
            }
            else{
               // const char *msg=(e==1?"Error: slow network":(e==2?"Could not call":(e==3?"User is not online":"Error-0")));
               const char *msg="Could not reach server";
               CTEditBase *trMsg=&strings.lCouldNotReachServer;
               
               //>>check can we reach server?
               int servResp=0;
               int ses = 0; if(firstOnlineEng)ses = firstOnlineEng->ph->sendSipKA(1,&servResp);
               for(int j=0;j<100 && ses && pl->iTmpCallID>pl->self->iLastTmpCallIDToStop;j++){
                  Sleep(50);
                  if(servResp)break;
               }
               if(firstOnlineEng)
                  firstOnlineEng->ph->removeRetMsg(ses);
               //<<check can we reach server?
               
               if(servResp>0){
                  msg="Remote party is out of coverage";
                  trMsg=&strings.lRemoteOutOfReach;
               }
               else if(servResp==-2){msg="Slow network";trMsg=NULL;}
               
               notifyEndCall(pl,un,msg,trMsg);
            }
            
            
         }
      }
      
      delete pl;
      
      return 0;
   }
   int isSameAccount(CPhoneCons *e1, CPhoneCons *e2){
      if(!e1 || !e2)return 0;
      if(e1==e2)return 1;
      if(e1->p_cfg.szTitle[0] && e2->p_cfg.szTitle[0] &&  strcmp(e1->p_cfg.szTitle,e2->p_cfg.szTitle)==0){
         return 1;
      }
      
      if(strcmp(e1->p_cfg.user.un, e2->p_cfg.user.un)==0 &&
         strcmp(e1->p_cfg.user.nr, e2->p_cfg.user.nr)==0 &&
         e1->p_cfg.str32GWaddr.uiLen==e2->p_cfg.str32GWaddr.uiLen &&
         strcmp(e1->p_cfg.str32GWaddr.strVal, e2->p_cfg.str32GWaddr.strVal)==0){
         return 1;
      }
      return 0;
   }
   
   CSesBase *getSesByCallID(int iCallID){
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(ret && ret->ph){
            CSesBase *s = ret->ph->findSessionByID(iCallID);
            if(s)return s;
         }
      }
      return NULL;
   }
   
   int isAccountOnline(void *pEng){
      
      if(iStarted!=1)return 0;
      CPhoneCons *eng=(CPhoneCons*)pEng;
      if(!eng)eng=getAccountByID(0,1);
      if(!eng)return 0;
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(!ret || !ret->ph)continue;
         
         if(ret==eng || isSameAccount(eng,ret)){
            if(ret->p_cfg.isOnline() && (int)ret->p_cfg.reg.uiRegUntil>0){
               return 2;
            }
         }
      }
      
      return 0;
      
   }
   int getPhoneState(){
      CPhoneCons *eng = getAccountByID(0,1);
      if(!eng)return 0;
      //TODO find SC account
      if(!eng->p_cfg.user.un[0]){eng = getAccountByID(1,1);if(!cPhone)cPhone=eng;}
      if(!eng)return 0;
      if(isAccountOnline(eng))return 2;
      if(eng->p_cfg.reg.bRegistring)return 1;
      return 0;
      
   }
   int getReqTimeToLive()//TODO check do we have new tasks within next 1min
   {
      int iCalls=0;
      int iSessions=0;
      getCallSessions(&iCalls,&iSessions);
      return iCalls?20:(iSessions?4:0);
      
   }
   int getCallSessions(int *c, int *s){
      int iCalls=0;
      int iSessions=0;
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(!ret)break;
         if(!ret->ph)continue;
         
         iCalls += ret->ph->getMediaSessionsCnt();
         if(iCalls)break;
         
         iSessions += ret->ph->getSessionsCnt();
      }
      if(iCalls && !iSessions)iSessions++;
      
      if(c)*c=iCalls;
      if(s)*s=iSessions;
      
      return 0;
   }
   int hasActiveCalls(){
      int iCalls=0;
      int iSessions=0;
      getCallSessions(&iCalls,&iSessions);
      return iCalls;
   }
   
   void *findEngByServ(CTEditBase *b){
      int l=b->getLen();
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(!ret)continue;
         if(ret->p_cfg.str32GWaddr.uiLen!=l)continue;
         if(*b==(const char *)ret->p_cfg.str32GWaddr.strVal)return ret;
         continue;
      }
      return NULL;
   }
   
   #define CMP_SZ(_P,_SZ) (l+1==sizeof(_SZ) && p && strcmp(_P,_SZ)==0)
   
   int getInfo(void *pEng, const char *key, char *p, int iMax){
      if(p) p[0] = 0;
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i);
         if(ret == pEng){
            if(!ret->ph){
               return 0;
            }
            return ret->ph->getInfo(key, p, iMax);
         }
      }
      return 0;
   }
   
   const char* sendEngMsg(void *pEng, const char *p){
      
      if(!iStarted || iExiting)return "";
      
      int l=p?strlen(p):0;
      
      if(CMP_SZ(p,"title")){
         if(!pEng)return "";
         for(int i=0;i<eAccountCount;i++){
            CPhoneCons *ret=getAccountByID(i);
            if(ret==pEng){
               const char *getAccountTitle(void *pS);
               return getAccountTitle(pEng);
            }
         }
         return "";
      }
      
      
      if(!pEng){
         if(!p)return "";
         
         if(l>4 && strncmp(p,"cfg.",4)==0){
            static char buf[256];
            char *findByServKey(void *pEng, const char *key, char *buf, int iMax);
            char *res=findByServKey(NULL, p+4, &buf[0], sizeof(buf)-1);
            return res?res:"";
         }
         
         if(CMP_SZ(p,"all_online")){
            for(int i=0;i<eAccountCount;i++){
               CPhoneCons *ret=getAccountByID(i,1);
               if(!ret)break;
               //TODO check is valid ip
               if(!ret->ph || !ret->p_cfg.isOnline())return "false";
            }
            return "true";
         }
         if(l>4 && strncmp(p,".t ",3)==0){
            for(int i=0;i<eAccountCount;i++){
               CPhoneCons *ret=getAccountByID(i);
               if(ret && l==3+ret->p_cfg.str32GWaddr.uiLen &&  strcmp(p+3,ret->p_cfg.str32GWaddr.strVal)==0)
                  return &ret->p_cfg.szTitle[0];
               
            }
            return "";
         }
         sendCommandToAllEngines(p);//TODO test
         return "";
      }
      
      
      if( pEng && CMP_SZ(p,"delete")){
         deleteAccount((CPhoneCons*)pEng);
         return "ok";
      }
      
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i);

         if(ret==pEng){
            //TODO fix static buf, pass return buf or use new
            if(!p || l==0){
               static char bufX[_BUF_X_SIZE+1];
               mutexResp.lock();
               ret->bubbleMsg.getUtf8(&bufX[0],_BUF_X_SIZE);
               mutexResp.unLock();
               return &bufX[0];
            }
            if(p){
               
               if(l>9 && strncmp(p,"set ",4)==0){
                  
                  if(strncmp(p+4,"cfg.",4)==0){
                     int setCfgValueSZ(char *sz, void *pCfg, char *key, int iKeyLen);
                     
                     int findChar(const char *p, char c);
                     int pos=findChar(p+8,'=');
                     if(pos<1)return "";
                     
                     if(setCfgValueSZ((char *)p+9+pos, &ret->p_cfg,(char*)p+8,pos)<0)return "err";
                     
                     return "ok";
                  }
               }
               
               if(l>4 && strncmp(p,"cfg.",4)==0){
                  static char buf[256];
                  char *findByServKey(void *pEng, const char *key, char *buf, int iMax);
                  char *res=findByServKey(ret, p+4, &buf[0], sizeof(buf)-1);
                  return res?res:"";
               }

               if(CMP_SZ(p,"name")){
                  static char buf[256];
                  if(ret->p_cfg.szTitle[0])strcpy(&buf[0],&ret->p_cfg.szTitle[0]);
                  else strcpy(&buf[0],ret->p_cfg.str32GWaddr.strVal);
                  return &buf[0];
               }
               if(CMP_SZ(p,"name_stat")){
                  static char buf[256];
                  if(ret->p_cfg.szTitle[0])strcpy(&buf[0],&ret->p_cfg.szTitle[0]);
                  else strcpy(&buf[0],ret->p_cfg.str32GWaddr.strVal);
                  if(ret->p_cfg.isOnline() && (int)ret->p_cfg.reg.uiRegUntil>0)
                     strcat(&buf[0]," Online");
                  return &buf[0];
               }
               if(CMP_SZ(p,"regErr")){
                  if(!ret || !ret->ph)return "";
                  return &ret->bufLastRegErr[0];
               }
               if(CMP_SZ(p,"isON")){
                  
                  if(isAccountOnline(ret)){
                     return "yes";//&buf[0];
                  }
                  
                  if(ret->p_cfg.isOnline()){
                     if((int)ret->p_cfg.reg.uiRegUntil>0)
                        return "yes";
                     else
                        return "connecting";
                  }
                  
                  return "no";
               }
               
               if(CMP_SZ(p,".sock")){
                  static char buf[256];
                  ret->ph->sockSip.getInfo("sock",&buf[0], sizeof(buf)-1);
                  return &buf[0];
               }
               if(CMP_SZ(p,".isTLS")){
                  static char buf[16];
                  sprintf(buf,"%d",ret->ph->sockSip.isTLS());
                  return &buf[0];
               }
               if(CMP_SZ(p,".lastErrMsg")){
                  static char buf[256];
                  strncpy(buf,ret->bufLastErrMsg,sizeof(buf)-1);
                  buf[sizeof(buf)-1]=0;
                  ret->bufLastErrMsg[0]=0;
                  return &buf[0];
               }
               if(ret->ph){
                  static char buf[1024];
                  int r = ret->ph->getInfo(p, buf, sizeof(buf)-1);
                  if(r)return &buf[0];
               }
               
               //return "";
            }
            
            if(p && p[0]==':' && ( p[1]=='c' || p[1]=='v'))
               callTo(ret,p);
            else {
               
               int r=ret->command(p,l);
               if(r==-100 && p[0]=='*' && p[1]=='e')
                  iLastTmpCallIDToStop=iTmpCallID;//stop tmp calls
            }
            break;
         }
      }
      return "";
      
   }
  
   void setCurDOFromCfg(){
      
      int i;
      int iSet=0;
      for( i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(!ret)break;
         const char *getAccountTitle(void *pS);
         char *sz=(char*)findGlobalCfgKey("szLastUsedAccount");
         
         if(sz && strcmp(sz,getAccountTitle(ret))==0){
            iCurrentDialOutIndex=i;
            iSet=1;
            break;
         }
      }
      if(!iSet)iCurrentDialOutIndex=0;
      
   }
   
   int setCurrentDOut(void *p){
      if(iExiting || !p)return -1;
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(ret==p){
 
            const char *getAccountTitle(void *pS);
            int setGlobalValueByKey(const char *key,  char *sz);

            setGlobalValueByKey("szLastUsedAccount",(char*)getAccountTitle(p));
            t_save_glob();
            iCurrentDialOutIndex=i;
            return 0;

         }
      }
      iCurrentDialOutIndex=0;
      //
      return -1;
      
   }
   
   int setCurrentDOut(int idx, const char *sz=NULL){
      if(iExiting)return -1;
      CPhoneCons *p=getAccountByID(idx,1);
      if(p){
         return setCurrentDOut(p);
      }
      return -1;
   }
   void* getCurrentDOut(){
      if(iExiting)return NULL;
      CPhoneCons *p=getAccountByID(iCurrentDialOutIndex,1);
      if(!p){
         iCurrentDialOutIndex=0;
         p=getAccountByID(iCurrentDialOutIndex,1);
         if(p){
            setCurrentDOut(iCurrentDialOutIndex);
         }
      }
      return p;
   }
   void enableSIPTLS443forSC(int iEnable){
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i,1);
         if(ret && ret->p_cfg.bufpxifnat[0]){
            ADDR a = ret->p_cfg.bufpxifnat;
            
            int iHave443 = a.getPort()==443; //HTTPS port
            int iRestart=0;
            
            if(iEnable && !iHave443 && !a.getPort()){
               strcat(ret->p_cfg.bufpxifnat, ":443");
               iRestart=1;
            }
            else if(!iEnable && iHave443){
               int l = strlen(ret->p_cfg.bufpxifnat);
               if(l>5)ret->p_cfg.bufpxifnat[l-4]=0;//rem 443
               iRestart=1;
            }
            printf("[new_proxy=%s]",ret->p_cfg.bufpxifnat);
            if(iRestart && ret->ph){
               ret->p_cfg.iCfgChangesDetected++;
               ret->ph->save();
               ret->ph->checkServDomainName(0,1);
               ret->command(":force.network_reset");
               ret->command(":reg");
            }
            
            
         }
      }
   }
   
   void switchToTest443(){
      const char *testServAddr = "t-b9k4xe6hb.silentcircle.net:443";
      ADDR aNew = testServAddr;
      int isSet = 0;
      static int *g_enable = (int *)findGlobalCfgKey("iEnableFWTraversal");
      if(g_enable){
         *g_enable = 1;
         t_save_glob();
      }
      
      for(int i=0;i<eAccountCount;i++){
         CPhoneCons *ret=getAccountByID(i);
         if(ret){
            if(!ret->p_cfg.bufTMRAddr[0] && ret->ph){
               strcpy(ret->p_cfg.bufTMRAddr,"tmr01t-fsyyz.silentcircle.net:443");//92.240.74.121:443");//44060");//
               ret->ph->save();
            }
         }
         
         if(!isSet && ret && ret->p_cfg.bufpxifnat[0]){
            isSet=1;
            ADDR aOld=ret->p_cfg.bufpxifnat;
            
            if(!aNew.ip)aNew.ip = CTSock::getHostByName(testServAddr);
            
            if(!aOld.ip){aOld.ip = ret->p_cfg.GW.ip;
               char b[64];aOld.toStr(b);puts(b);}
            
            
            
            printf("[old_%x new_%x old=%s new=%s %d]\n",aOld.ip, aNew.ip, ret->p_cfg.bufpxifnat,testServAddr,(aOld.ip&0xffffff) == (aNew.ip&0xffffff));
            
            if( (aOld.ip&0xffffff) == (aNew.ip&0xffffff)){
               if(ret->ph)ret->ph->waitOffline();
               puts("offline");
               strcpy(ret->p_cfg.bufpxifnat,testServAddr);
               if(ret->ph)ret->ph->save();
               if(ret->ph)ret->ph->checkServDomainName(0,1);
               ret->command(":reg");
               //break;
               
            }
         }
      }
   }
   
   int sendCommandToAllEngines(const char *p){
      if(!iStarted || iExiting || !p)return 0;
      
      audioMngr.cAI.cb=this;
      audioMngr.cVI.cb=this;
      CPhoneCons *ret;
      
      if(p && strncmp(p,"set ",4)==0){
         
         if(strncmp(p+4,"cfg.",4)==0){
            int setGlobalValueByKey(const char *key, int iKeyLen, char *sz);
            int setCfgValueSZ(char *sz, void *pCfg, char *key, int iKeyLen);
            
            int findChar(const char *p, char c);
            int pos=findChar(p+8,'=');
            if(pos<1)return 0;
            int r=setGlobalValueByKey(p+8,pos,(char *)p+9+pos);
            if(r>0){
               t_save_glob();
               return 0;
            }
            
            for(int iEnabled=1;iEnabled>=0;iEnabled--){
               for(int i=0;i<eAccountCount;i++){
                  ret=getAccountByID(i,iEnabled);
                  if(ret){
                     setCfgValueSZ((char *)p+9+pos, &ret->p_cfg,(char*)p+8,pos);
                  }
               }
            }
            
            return 0;
         }
      }

      if(p && strncmp(p,"*##*",4)==0){
         
         if(strcmp(p+4,"3948*")==0){//exit
            exit(0);
            return 0;
         }
         /*
         if(strcmp(p+4,"3357768*")==0){//delprov
            int created_by_user[eAccountCount];
            int iLast=0;
            for(int i=0;i<eAccountCount;i++){
               ret=getAccountByID(i);
               if(ret){
                  iLast=i+1;
                  created_by_user[i]=ret->p_cfg.bCreatedByUser?1:0;
               }
               else
                  created_by_user[i]=-1;
            }
            destroy();//test;
            void delProvFiles(const int *p, int iCnt);
            delProvFiles(&created_by_user[0],iLast);
#if !defined(ANDROID_NDK)
            exit(1);
#endif
            return 0;
         }
          */
         if(strcmp(p+4,"9787257*")==0){
            int *cc=(int*)findGlobalCfgKey("iClearZRTPCaches");
            if(cc)*cc=1;
            return 0;
         }
         if(strcmp(p+4,"501*")==0){
            puts("del unused accounts");
            stopDeleteDisabled();
            return 0;
         }
         if(strcmp(p+4,"908*")==0){
            void crashAfter5sec();
            crashAfter5sec();
            return 0;
         }
         if(strcmp(p+4,"907*")==0){
            puts("crash now, testing app autorestart");
            char *p2=(char*)"crash_test";
            memset(p2,10,100);
            return 0;
         }
         if(strcmp(p+4,"367*")==0){//dns
            ret=(CPhoneCons*)getCurrentDOut();
            if(!ret || !ret->ph)return -1;
            ret->ph->checkServDomainName(0,1);
            // ret->info(<#CTStrBase *e#>, <#int iType#>, <#int SesId#>)
            
            return 0;
         }
         if(strcmp(p+4,"367734*")==0){//dns
            ret=(CPhoneCons*)getCurrentDOut();
            if(!ret || !ret->ph)return -1;
            ret->ph->checkServDomainName(0,1);
            ret->p_cfg.iReRegisterNow=1;
            // ret->info(<#CTStrBase *e#>, <#int iType#>, <#int SesId#>)
            
            return 0;
         }
         void setRtpQueue(int f);
         if(strcmp(p+4,"41*")==0){
            setRtpQueue(1);
            return 0;
         }
         if(strcmp(p+4,"40*")==0){
            setRtpQueue(0);
            return 0;
         }
         
         if(strcmp(p+4,"34*")==0){
            iEchoCancellerOn=4;
            return 0;
         }
         if(strcmp(p+4,"33*")==0){
            iEchoCancellerOn=3;
            return 0;
         }
         if(strcmp(p+4,"31*")==0){
            iEchoCancellerOn=1;
            return 0;
         }
         if(strcmp(p+4,"32*")==0){
            iEchoCancellerOn=2;
            return 0;
         }
         if(strcmp(p+4,"30*")==0){
            iEchoCancellerOn=0;
            return 0;
         }
         if(strncmp(p+4,"33#",3)==0 && isdigit(p[7]) && p[8]=='*'){
            iEchoCancellerOn=3;
#if  defined(ANDROID_NDK)
            void setSpkrModeAEC(void *aec, int echoMode);
            int m=p[7]-'0';
            tivi_log1("set aec mode:", m);
            setSpkrModeAEC(NULL, m);
#endif
            
            return 0;
         }
         if(strcmp(p+4, "2663*")==0){
            iVideoConfEnabled=1;
            return 0;
         }
         
         if(strcmp(p+4,"73738638*")==0){
            p=":force.network_reset";
         }
         else
           return -1;
      }
      
#define T_IS_OPTIONS(_P) (_P && _P[0]==':' && _P[1]=='c' && _P[2] && _P[3] && _P[3]=='.' && _P[4]=='+')

      if(T_IS_OPTIONS(p)){
      
         for(int i=0;i<eAccountCount;i++){
            CPhoneCons *ret=getAccountByID(i,1);
         
            if(ret)ret->command(p);
            continue;
         }
         return 0;
      }
      
      if(p && (p[1]=='c' || p[1]=='v') && p[0]==':'){
         
         ret=getAccountByID(iCurrentDialOutIndex,1);
         
         if(ret){
            callTo(ret,p);
            //--ret->command(p);
            return 0;
         }
      }
      
      if(p && p[0]==':' && p[2]==0 && (p[1]=='S' || p[1]=='s')){
         t_save_glob();
      }
      
      
      
      for(int iEnabled=1;iEnabled>=0;iEnabled--){
         for(int i=0;i<eAccountCount;i++){
            ret=getAccountByID(i,iEnabled);
            if(ret){
               int r=ret->command(p);
               if(r==-100 && p[0]=='*' && p[1]=='e'){
                  iLastTmpCallIDToStop=iTmpCallID;
                  continue;
               }
               
               if(r!=T_TRY_OTHER_ENG)break;
            }
            else break;
         }
      }
      return 0;
   }
   
   char *getCurrentAccountLog(){
      CPhoneCons *p=getAccountByID(iCurrentDialOutIndex,1);
      if(p){
         static char bufX[_BUF_X_SIZE+1];
         mutexResp.lock();
         p->bubbleMsg.getUtf8(&bufX[0],_BUF_X_SIZE);
         mutexResp.unLock();
         return &bufX[0];
      }
      return (char*)"";
   }
   
   
   CPhoneCons *getAccountByID(int iId){
      if(iId>=eAccountCount || iId<0 || !account[iId].iInUse)return NULL;
      return account[iId].ph;
   }
   int getEngIDByPtr(void *p){
      if(!p)return 0;
      for(int i=0;i<eAccountCount;i++){
         if(p==account[i].ph){
            return i;
         }
      }
      
      return 0;
   }
   
   
   int deleteAccount(CPhoneCons *ph){
      for(int i=0;i<eAccountCount;i++){
         if(ph && ph==account[i].ph){
            account[i].iInUse=0;
            account[i].ph->iRun=0;
            Sleep(50);
            account[i].ph=NULL;
            delete ph;
            deleteCfg(NULL,i);
            
            return 0;
         }
      }
      return -1;
   }
   CPhoneCons *getEmptyAccount(){
      if(!empty){
         empty=new CPhoneCons(10000);
      }
      empty->p_cfg.iAccountIsDisabled=0;
      empty->p_cfg.iZRTP_On=1;
      empty->p_cfg.iCanUseZRTP=1;
      empty->p_cfg.bCreatedByUser=1;
      //loadCodecDefaults
      return empty;
   }
#define i j
   CPhoneCons *empty;
   void clickOnEmpty(){
      if(!empty)return;
      for(int i=0;i<eAccountCount;i++){
         if(!account[i].ph){
            deleteCfg(NULL,i);
            account[i].ph=empty;
            account[i].ph->iEngineIndex=i;
            account[i].ph->p_cfg.iIndex=i;
            account[i].iInUse=1;
            updateAccount(account[i].ph);
            account[i].ph->p_cfg.bCreatedByUser=1;
            //      empty->startEngine();
            
            
            empty=NULL;
            break;
         }
      }
      
   }
   
   int canRing(){
      CPhoneCons *ret;
      for(int i=0;i<eAccountCount;i++){
         ret=getAccountByID(i,1);
         if(ret){
            if(ret->canRing())return 1;
         }
      }
      return 0;
   }
   
   char bufI[1024];
   const char *info(const char *cmd){
      if(!cmd){
         int l=0;
         CPhoneCons *ret=getAccountByID(0,1);
         if(!ret || !ret->ph)return "";
         l+=snprintf(&bufI[l], sizeof(bufI)-l,"\n");
         l+=snprintf(&bufI[l], sizeof(bufI)-l, "IP: %.*s\n", ret->ph->str64BindedAddr.uiLen,ret->ph->str64BindedAddr.strVal);
         l+=snprintf(&bufI[l], sizeof(bufI)-l, "EXT-IP: %.*s\n", ret->ph->str64ExternalAddr.uiLen,ret->ph->str64ExternalAddr.strVal);
         l+=snprintf(&bufI[l], sizeof(bufI)-l, "NAT type: %s\n", CTStun::getNatName(ret->p_cfg.iNet));
         l+=snprintf(&bufI[l], sizeof(bufI)-l, "STUN ping: %dms\n", ret->ph->iPingTime);

         ret=getAccountByID(1,1);
         if(ret){
           l+=snprintf(&bufI[l], sizeof(bufI)-l, "NAT2 type: %s\n", CTStun::getNatName(ret->p_cfg.iNet));
           l+=snprintf(&bufI[l], sizeof(bufI)-l, "STUN2 ping: %dms\n", ret->ph->iPingTime);
         }
         return &bufI[0];
      }
      return 0;
   }
   
   
   CPhoneCons *createNew(char *name){
      for(int i=0;i<eAccountCount;i++){
         if(!account[i].ph){
            deleteCfg(NULL,i);
            account[i].ph=load(i);
            strcpy(account[i].ph->p_cfg.szTitle,name);
            return account[i].ph;
         }
      }
      return NULL;
   }
   CPhoneCons *getAccountByID(int id, int iIsEnabled){
      CPhoneCons *ret;
      for(int i=0;i<eAccountCount;i++){
         ret=getAccountByID(i);
         if(ret){
            if(ret->p_cfg.iAccountIsDisabled==!iIsEnabled){
               if(!id)return ret;
               id--;
            }
         }
      }
      return NULL;
   }
   void setQWiewVI(void *p){
      pQViewVO=p;
      updateAccountData();
   }
   void setQWiewVO(void *p){
      pQViewVI=p;
      updateAccountData();
   }
   void setPhoneCB(fnc_cb_ph *fnc, void *pRet){
      puts("set setPhoneCB");
      fncCB=fnc;
      pRetCB=pRet;
      updateAccountData();
   }
private:
   static int hasPrefix(const char *name, const char *prefix){
      int l = (int)strlen(prefix);
      return strncmp(name, prefix, l) == 0;
   }
   CPhoneCons *load(int i){
     // char buf[128];snprintf(buf, sizeof(buf),"load=%d",i);tmp_log(buf);
      account[i].ph=new CPhoneCons(i);
     // account[i].iSTLen=strlen(account[i].ph->p_cfg.szTitle);
      
      CPhoneCons *p = account[i].ph;
      
      int isPrimary = p && (hasPrefix(p->p_cfg.bufpxifnat, "b9k4xe6hb.") || hasPrefix(p->p_cfg.bufpxifnat, "r0s67xypvq."));
      int isSecondary = !isPrimary && p && (hasPrefix(p->p_cfg.bufpxifnat, "ka2o10im8.") || hasPrefix(p->p_cfg.bufpxifnat, "aopobib2b."));
      
      //disable old jtymq
      if(isSecondary){ //
         p->p_cfg.iAccountIsDisabled = 1;
      }
      else if(isPrimary){ //
         ADDR a = p->p_cfg.bufpxifnat;
         if(a.getPort()){
            t_snprintf(p->p_cfg.bufpxifnat, sizeof(p->p_cfg.bufpxifnat),  "sep.silentcircle-inc.net:%d", a.getPort());
         }
         else {
            strcpy(p->p_cfg.bufpxifnat, "sep.silentcircle-inc.net");
         }
         t_snprintf(p->p_cfg.bufTMRAddr, sizeof(p->p_cfg.bufTMRAddr), "%s", "tmr-sep.silentcircle-inc.net:443");
      }
      
      account[i].iInUse=1;
      updateAccount(account[i].ph);
      return account[i].ph;
   }
   void updateAccount(CPhoneCons *ph){
      if(pQViewVO)audioMngr.setQWview(pQViewVO);
      if(pQViewVI)audioMngr.cVI.setQWview(pQViewVI);
      if(!ph)return ;
      if(fncCB)ph->setCB(fncCB,pRetCB);
   }
   void updateAccountData(){
      CPhoneCons *ret;
      for(int i=0;i<eAccountCount;i++){
         ret=getAccountByID(i);
         if(ret){
            updateAccount(ret);
         }
      }
   }
   
   void *pQViewVI;
   void *pQViewVO;
   
   fnc_cb_ph *fncCB;
   void *pRetCB;
   
   
   
};
CTPhoneMain *engMain=NULL;

void g_sendDataFuncAxo(uint8_t* names[], uint8_t* devIds[], uint8_t* envelopes[], size_t sizes[], uint64_t msgIds[]){
   int i;
   
   char *getEntropyFromZRTP_tmp(unsigned char *p, int iBytes);
   void bin2Hex(unsigned char *Bin, char * Hex ,int iBinLen);
   
   uint64_t msg_id;
   CTEditBuf<3*1024+128> b;//should be less then 4K - sizeof(SIP MESSAGE)
   
   getEntropyFromZRTP_tmp((unsigned char *)&msg_id, sizeof(msg_id));
   
   uint64_t aa  = (1<<4) - 1;
   msg_id &= ~aa; //clean last 4 bits
   
   char bufMsgId[64];
   char hex[32];
   char bufDevIdAdd[64];
   
   for(i = 0; i < aa + 1; i++){
      if(!names[i] || !devIds[i] || !envelopes[i] || !sizes[i])break;
      
      uint64_t curid = msg_id + i;
      msgIds[i] = curid; //set network transport id
      
      bin2Hex((unsigned char *)&curid, hex, sizeof(curid));
      
      snprintf(bufMsgId,sizeof(bufMsgId), "X-SC-Message-Meta: id64=%s\r\n", hex);
      snprintf(bufDevIdAdd,sizeof(bufDevIdAdd), "xscdevid=%s", devIds[i]);
      
      //we have to send messages for one device via the same server, to avoid messages out of order
      int iServerID = 0;// (devIds[i][4]>>2) & 1 ;//(msg_id>>10) & 1;
      //iServerID = engMain->getEngIDByPtr(engMain->getAccountByID(engMain->iCurrentDialOutIndex,1));
      //TODO check how healty is connection
      CPhoneCons *ret = (CPhoneCons*)engMain->getAccountByID(iServerID);// check online
      if(!ret || !ret->p_cfg.isOnline()) ret = (CPhoneCons*)engMain->getAccountByID(!iServerID); //twin account
      
      if(!ret || !ret->p_cfg.isOnline()){
         log_events(__FUNCTION__,"FAIL to send: !ret || !ret->p_cfg.isOnline()");
        
         msgIds[i] = 0;
         continue;//we have to set all msg id's to 0
      }
      //TODO Q:should i suspend sending out SIP packets here ??
      //A: If not , then UI could remember the last network transport ids,and then update messages later
      //            stateReport.remeberState(msgid) , when this fnc returns stateReport.findRememberedState(msgid);
      b.setText((const char *)envelopes[i],(int)sizes[i] ,0);
      
      int ses_id = ret->ph->sendSipMsg(0, "MESSAGE",(char *)names[i], &bufDevIdAdd[0], "application/x-sc-axolotl", &b, &bufMsgId[0]);
       t_logf(log_events, __FUNCTION__, "Called sendSipMsg [name: %s devid: %s return: %d]", (char *)names[i], bufDevIdAdd, ses_id);
//      int ses_id = ret->ph->sendSipMsg(0, "MESSAGE",(char *)names[i], &bufDevIdAdd[0], "text/plain", &b, &bufMsgId[0]);
      CSesBase *spSes = ses_id ? ret->ph->findSessionByID(ses_id) : NULL;
      if(spSes){
         spSes->sentSimpleMsgId64bit = curid;
      }else {
        // TODO notify fail
         msgIds[i] = 0;
      }
   }
}

int hasActiveCalls(){return  engMain->hasActiveCalls();}

CSesBase *g_getSesByCallID(int iCallID){
   return engMain->getSesByCallID(iCallID);
}

const char *g_getInfo(const char *cmd){
   return engMain->info(cmd);
}

int getReqTimeToLive(){
   
   if(!engMain)return -1;
   return engMain->getReqTimeToLive();
}

void *getAccountByID(int id){
   if(!engMain)return NULL;
   return engMain->getAccountByID(id);
}

int getEngIDByPtr(void *p){
   if(!engMain)return 0;
   return engMain->getEngIDByPtr(p);
}

void* getCurrentDOut(){
   return engMain->getCurrentDOut();
}

int setCurrentDOut(int idx, const char *sz){
   return engMain->setCurrentDOut(idx, sz);
}

int setCurrentDOut(void *eng){
   return engMain->setCurrentDOut(eng);
}

void *findEngByServ(CTEditBase *b){
   return engMain->findEngByServ(b);
}

void *findBestEng(const char *p, const char *name){
   return engMain->findBestEng(p, name);
}

const char* sendEngMsg(void *pEng, const char *p){
   return engMain->sendEngMsg(pEng, p);
}

int getInfo(void *pEng, const char *key, char *p, int iMax){
   return engMain->getInfo(pEng, key, p, iMax);
}

void g_setQWview(void *p){
   audioMngr.setQWview(p);
}

void g_setQWview_vi(void *p){
   audioMngr.cVI.setQWview(p);
}

void setPhoneCB(fnc_cb_ph *fnc, void *pRet){
   if(!engMain)return ;
   engMain->setPhoneCB(fnc, pRet);
}

void *getEmptyAccount(){
   return engMain->getEmptyAccount();
}

int createNewAccount(void *pSelf, void *pRet){
   engMain->clickOnEmpty();
   //TODO moveItemFromListT
   return 0;
}


void* getAccountCfg(void *eng){
   if(!eng)return NULL;
   CPhoneCons *p=(CPhoneCons*)eng;
   return &p->p_cfg;
}

void *getAccountByID(int id, int iIsEnabled){
   return engMain->getAccountByID(id,iIsEnabled);
}

int g_canRing(){
   return engMain->canRing();
}

void* findCfgItemByServiceKey(void *ph, char *key, int &iSize, char **opt, int *type){
   
   if(key && key[0]=='*')return NULL;
   
   if(!ph){
      return findGlobalCfgKey(key,strlen(key),iSize,opt,type);
   }
   if(!ph)return NULL;
   CPhoneCons *p=(CPhoneCons*)ph;
   //TODO check is servIs valid
   void *ret=findCfgItemKey((void*)&p->p_cfg,key,strlen(key),iSize,opt,type);
   if(ret)return ret;
   return findGlobalCfgKey(key,strlen(key),iSize,opt,type);
   
}

int findIntByServKey(void *pEng, const char *key, int *ret){
   int sz=0;
   int t=0;
   char *opt;
   char *pRet=(char*)findCfgItemByServiceKey(pEng, (char*)key, sz, &opt,&t);
   if(pRet && sz && ret){
      *ret=*(int*)pRet;
      return 0;
   }
   return -1;
}

int* findIntByServKey(void *pEng, const char *key){
   int sz=0;
   int t=0;
   char *opt;
   return (int*)findCfgItemByServiceKey(pEng, (char*)key, sz, &opt,&t);
}

char* findSZByServKey(void *pEng, const char *key){
   int sz=0;
   int t=0;
   char *opt;
   char *pRet=(char*)findCfgItemByServiceKey(pEng, (char*)key, sz, &opt,&t);
   if(pRet && sz>0){
      return (char*)pRet;
   }
   return NULL;
}

char* findByServKey(void *pEng, const char *key, char *buf, int iMax){
   int sz=0;
   int t=0;
   char *opt;
   char *pRet=(char*)findCfgItemByServiceKey(pEng, (char*)key, sz, &opt,&t);
   if(pRet && sz>0){
      if(t==PHONE_CFG::e_char){
         strncpy(buf, pRet, iMax);buf[iMax-1]=0;
         return buf;
      }
      
      int v=*(int*)pRet;
      snprintf(buf,iMax,"%d",v);
      return buf;
   }
   return NULL;
}


const char *getAccountTitle(void *pS){
   int iSize=0;
   char *opt;
   int iType=0;
   void *ret=findCfgItemByServiceKey(pS, (char*)"szTitle", iSize, &opt, &iType);
   if(ret && iSize>0 && ((char*)ret)[0])return (const char*)ret;
   
   ret=findCfgItemByServiceKey(pS, (char*)"tmpServ", iSize, &opt, &iType);
   if(ret && iSize>0 && ((char*)ret)[0])return (const char*)ret;
   
   return "empty";
}



void t_onEndApp(){
   if(engMain){
      delete engMain;
      engMain=NULL;
   }
}


static void signalFnc(int id)
{
   puts("signal...");
   //fputs(":q\n",stdin);
   
   delete cPhone;
   cPhone=NULL;
}



int z_main_init(int argc, const  char* argv[]){
   
   if(!engMain) engMain = new CTPhoneMain();
   if(argc>1)
   {
      engMain->start();
      
      for(int i=1;i<argc;i++)
      {
         engMain->sendCommandToAllEngines(argv[i]);
      }
   }
   return 0;
}

int doCmd(const char *p, void *pEng){
   if(!engMain)return -1;
   engMain->start();
   sendEngMsg(pEng, p);
   return 0;
}

int doCmd(const char *cmd, int iCallID, void *pEng){
   
   if(!engMain)return -1;
   engMain->start();
   
   char buf[32];
   snprintf(buf, sizeof(buf),"%s%d",cmd, iCallID);
   sendEngMsg(pEng, &buf[0]);
   return 0;
}


char* z_main(int iResp,int argc, const char* argv[])
{
   int i=0;
   
   if(!engMain)return (char*)"";
   engMain->start();
   if(argc>1)
   {
      if(strcmp(argv[1],":cr")==0){
         puts("create");
         engMain->getEmptyAccount();
         engMain->clickOnEmpty();
         return (char*)"ok";
      }
      for(i=1;i<argc;i++)
      {
         engMain->sendCommandToAllEngines(argv[i]);
      }
   }
   if(!iResp)return NULL;
   //if(iResp==2)
   
   
   char *pRet=engMain->getCurrentAccountLog();
   return pRet;
   
}

int canEnableDialHelper(){
   static int *hlp = (int *)findGlobalCfgKey("iEnableDialHelper");
   const char *p = sendEngMsg(getCurrentDOut(), "cfg.partnerID");
   if(p && p[0] && (strcmp(p,"KPN")==0 ||  strcmp(p,"kpn")==0)){
      if(hlp)*hlp=0;
      return 0;
   }
   return 1;
}

int canModifyNumber(){
   static int *hlp = (int *)findGlobalCfgKey("iEnableDialHelper");
   if(hlp && *hlp==0)return 0;
   if(hlp && *hlp==1){

      return canEnableDialHelper();
   }
   return 0;
}

void startThX(int (cbFnc)(void *p),void *data);

static int chApp5(void*p){
   Sleep(20000);
   puts("crash now, testing app autorestart");
   char *p2=(char*)"crash_test";
   memset(p2,10,100);
   return 0;
}

void crashAfter5sec(){
   startThX(chApp5,NULL);   
}


void *  t_getSetSometing(void *,char *key, int iKeyLen, char *param, int iParamLen,void *,void *){
   
   
   if(iKeyLen==4 && t_isEqual(key,"path",4)){
      return (char*)findFilePath(param);
   }
   
   return NULL;
}

void  dbgWMI(char const *,char *){}
int getUiqID(unsigned char *,int){return 0;}
void  setDevIDUSB(int){}

int getSetCfgVal(int iGet, char *k, int iKeyLen, char *v, int iMaxVLen){
   
   cPhone=engMain->getAccountByID(0, 1);
   if(cPhone && !cPhone->p_cfg.szTitle[0]){
      //TODO find SC account
      CPhoneCons *p=engMain->getAccountByID(1, 1);//get a valid account
      if(p)cPhone=p;
   }
   if(!cPhone){
      return 0;//android does not support multiple accounts or cfg
      
      engMain->getEmptyAccount();
      engMain->clickOnEmpty();
      cPhone=engMain->getAccountByID(0, 1);
      cPhone->command(":reg");
   }
   if(!cPhone)return 0;
   PHONE_CFG *cfg=&cPhone->p_cfg;
   if(iGet==2){
      saveCfg(cfg,cPhone->iEngineIndex);
      
      return 0;
   }
   if(!iGet)trim(v);
   
#define GET_CFG_SZ(_A,_R) if(iKeyLen+1==sizeof(_A) && strncmp(k,_A,iKeyLen)==0){if(iGet){strncpy(v,_R, iMaxVLen);_R[iMaxVLen]=0;}else {strncpy(_R,v,sizeof(_R)-1);_R[sizeof(_R)-1]=0;}break;}
#define GET_CFG_SZm(_A,_R,_M) if(iKeyLen+1==sizeof(_A) && strncmp(k,_A,iKeyLen)==0){if(iGet){strncpy(v,_R, iMaxVLen);_R[iMaxVLen]=0;}else {strncpy(_R,v,sizeof(_R)-1);_R[sizeof(_R)-1]=0;_M}break;}
#define GET_CFG_SZs(_A,_R) if(iKeyLen+1==sizeof(_A) && strncmp(k,_A,iKeyLen)==0){if(iGet){if(_R[0])strncpy(v,_R, iMaxVLen);else strncpy(v,cfg->str32GWaddr.strVal,iMaxVLen);_R[iMaxVLen]=0;}else {strncpy(_R,v,sizeof(_R)-1);_R[sizeof(_R)-1]=0;}break;}
#define GET_CFG_I(_A,_R) if(iKeyLen+1==sizeof(_A) && strncmp(k,_A,iKeyLen)==0){if(iGet){sprintf(v,"%d",_R);}else _R=atoi(v);break;}
#define GET_CFG_X(_A,_R) if(iKeyLen+1==sizeof(_A) && strncmp(k,_A,iKeyLen)==0){if(iGet){v[1]=0;v[0]=_R+'0';}else _R=atoi(v);break;}
   
   int iSipPort=cfg->iSipPortToBind;
   int iRtpPort=cfg->iRtpPort;
   do{
      GET_CFG_SZ("edUN",cfg->user.un);
      GET_CFG_SZ("edPWD",cfg->user.pwd);
      GET_CFG_SZ("edNR",cfg->user.nr);
      GET_CFG_SZs("edGW",cfg->tmpServ);
      GET_CFG_SZ("edNatPX",cfg->bufpxifnat);
      GET_CFG_SZm("szACodecs",cfg->szACodecs,if(cPhone->ph)cPhone->ph->setCodecs(&cfg->szACodecs[0]););
      GET_CFG_SZ("LKEY",cfg->szLicenceKey);
      GET_CFG_I("edREREG",cfg->uiExpires);
      GET_CFG_SZ("edSTUN",cfg->bufStun);
      GET_CFG_X("iUseVAD",cfg->iUseVAD);
      GET_CFG_X("iCanUseZRTP",cfg->iCanUseZRTP);
      GET_CFG_X("stun.iUse",cfg->iUseStun);
      GET_CFG_X("iUseOnlyNatIp",cfg->iUseOnlyNatIp);
      GET_CFG_I("iSipPort",cfg->iSipPortToBind);
      GET_CFG_I("iRtpPort",cfg->iRtpPort);
      
      /*
       //TODO
       void *findGlobalCfgKey(char *key, int iKeyLen, int &iSize, char **opt, int *type);
       void* findCfgItemByServiceKey(void *ph, char *key, int &iSize, char **opt, int *type)
       */
      //      GET_CFG_X("iUseOnlyNatIp",cfg->iUseOnlyNatIp);
      
      
   }while(0);
   int ch=0;
   
   
   if(!iGet && cPhone->ph){
      if(cfg->iSipPortToBind!=iSipPort && iSipPort>0 && iSipPort<65535)
      {
         cPhone->ph->sockSip.setNewPort(cfg->iSipPortToBind);
         ch=1;
      }
      
      if(cfg->iRtpPort!=iRtpPort && iRtpPort>0 && iRtpPort<65535)
      {
         cPhone->ph->cPhoneCallback->mediaFinder->setNewPort(cfg->iRtpPort);
         
         
         if(ch==0)cPhone->ph->reInvite();
      }
   }
   return 0;
}

int getPhoneState(){
   if(engMain) return engMain->getPhoneState();
   
   return 0;
}


//void setACName(int iIsRecording, const char *pname)
void setAudioRecordingDeviceName(const char *pname){
   audioMngr.setACName(1, pname);
}

void setAudioPlaybackDeviceName(const char *pname){
   audioMngr.setACName(0, pname);
}


#ifndef __APPLE__
//ANDROID
int getVidFrame(int prevf, int *i,int *sxy){
   if(!cPhone)return -1;
   return audioMngr.getVidFrame(prevf,i,sxy);
}

void onNewVideoData(int *d, unsigned char *yuv, int w, int h, int angle){
   if(!cPhone)return ;
   audioMngr.cVI.onNewVideoData(d,yuv,w,h, angle);
}
#endif

int disableABookLookup(){return 0;}

static int test_th_send_opt(void *pv){
   /*
   char name[64];
   char *p=(char*)pv;
   if(!p || !p[0])return 0;
   
   
   int id=(((char*)p)[0]-'0');p++;
   if(!p[0])return 0;
   CPhoneCons *eng = engMain->getAccountByID(id,1);
   
   
   int dur=(((char*)p)[0]-'0');p++;
   if(!p[0])return 0;
   dur*=30;
   
   strncpy(name, (char*)p, 63);
   
   if(!name[0] || dur<1)return 0;
   
   name[63]=0;
   name[strlen(name)-1]=0;//rem star
   
   CTEditBuf<1024> b;
   b.setText("Hi, Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive KeapaliveKeapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive KeapaliveKeapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive KeapaliveKeapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive KeapaliveKeapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive KeapaliveKeapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive KeapaliveKeapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive Keapalive");
   
   while(name[0]){
      printf("[send options %s dur=%dsec]", name, dur);
      
      eng->ph->sendMsg(0, name, NULL, &b);
      Sleep(dur*1*1000);
   }
    */
   return 0;
}

void enableSIPTLS443forSC(int iEnable){
   engMain->enableSIPTLS443forSC(iEnable);
}

void switchToTest443(){
   engMain->switchToTest443();
}

void test_send_options(const char *name){
   
   startThX(test_th_send_opt, (void*)name);
}

#ifndef __APPLE__
const char *t_getVersion(){return "1.8";}
#endif

