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

#include "CTZrtp.h"

#ifdef _WIN32
#define snprintf _snprintf
#endif

#ifdef _WIN32

#pragma comment(lib, "../../libs/wern_zrtp_vs/Release/wern_zrtp.lib")
#endif

#ifdef _C_T_ZRTP_V_H


void clearZrtpCachesG(void *pZrtpGlobals){}
char *getEntropyFromZRTP_tmp(unsigned char *p, int iBytes){
   return (char*)p; 
}

#else

#include "../../baseclasses/CTEditBase.h"
#include <cryptcommon/ZrtpRandom.h>

char * getFileStorePath();
void log_file_protection_prop(const char *fn);
void setFileBackgroundReadable(CTEditBase &b);
void setFileBackgroundReadable(const char *fn);


static int ig_InitCNT=0;
static char bufPathCache[2048];
static CTEditBase bufFNEntropy(2048);

#define ZRTP_CACHE_FN "zids_sqlite.db"

#ifdef  __SYMBIAN32__
 #define Sleep(A) User::After((A)*1000)
#else
 void tivi_sleep_ms(unsigned int ms);
 #define Sleep(_A) tivi_sleep_ms(_A)
#endif

int t_has_zrtp(){return 1;}


void add_tz_random(void *p, int iLen){
   ZrtpRandom::addEntropy((unsigned char *)p,iLen);
}

char *getEntropyFromZRTP_tmp(unsigned char *p, int iBytes){
   ZrtpRandom::getRandomData(p,iBytes);
   return (char*)p;
}

void saveEntropy(){
   void saveFileW(const short *fn,void *p, int iLen);
   unsigned char pSave[256];
   
   getEntropyFromZRTP_tmp(pSave,255);
   
   if(bufFNEntropy.getLen()>1){
      saveFileW(bufFNEntropy.getText(),pSave,256);
      setFileBackgroundReadable(bufFNEntropy);
   }
}



static void loadEntropy(){
   static int iEntropyLoaded;
   if(iEntropyLoaded)return;
   iEntropyLoaded=1;
   
#if defined(ANDROID_NDK) || defined(__APPLE__) || defined(_WIN32) || defined(__linux__)
   
   bufFNEntropy.setText(getFileStorePath());
   bufFNEntropy.addText("/");
   
#else
   //symbian
   bufFNEntropy.setText("c:\\System\\Data\\");
#endif
   
   bufFNEntropy.addText("zrtpent.dat");
   
   
   int iLen;
   char *loadFileW(const  short *fn, int &iLen);
   char *p=loadFileW(bufFNEntropy.getText(),iLen);
   
   if(p && iLen>0)
   {
      add_tz_random(p, iLen);// :)
   }
   if(p)delete p;
}


//#include <sqlite3.h>



void *initZrtpG()
{
   ig_InitCNT++;
   if(ig_InitCNT!=1){Sleep(50);return (void*)1;}
   // SQLITE_CONFIG_MUTEX
  // SQLITE_CONFIG_SINGLETHREAD
  // printf("sqlite3_threadsafe()=%d\n",sqlite3_threadsafe());
   
   //http://developer.apple.com/library/ios/#qa/qa1719/_index.html
   
#ifdef __SYMBIAN32__
   strncpy(bufPathCache, "c:\\system\\data\\"ZRTP_CACHE_FN, sizeof(bufPathCache)-1);
#else
#if defined(ANDROID_NDK) || defined(__APPLE__) ||  defined(_WIN32) || defined(__linux__)
   
   snprintf(&bufPathCache[0],sizeof(bufPathCache)-1,"%s/%s",getFileStorePath(), ZRTP_CACHE_FN);
   bufPathCache[sizeof(bufPathCache)-1]=0;
   
#else
   int getPath(char *p, int iMaxLen);
   int iPathLen=getPath(&bufPathCache[0],bufPathCache[0] - sizeof(ZRTP_CACHE_FN) - 2);
   iPathLen+=snprintf(&bufPathCache[iPathLen],sizeof(bufPathCache)-1,"%s",ZRTP_CACHE_FN);
#endif
   
 
#endif


   loadEntropy();
   CtZrtpSession::initCache(&bufPathCache[0]);
   setFileBackgroundReadable(&bufPathCache[0]);
   
   
   
   return (void*)1;
}

int relZrtpG(void *pZrtpGlobals)
{
   if(!pZrtpGlobals)return 0;
   if(ig_InitCNT>0)ig_InitCNT--;
   if(ig_InitCNT>0)return 0;
   

   saveEntropy();

   
   printf("del zrtp gloabal ok");
   
   return 0;
}


#define TO_N (iIsVideo? CtZrtpSession::VideoStream:CtZrtpSession::AudioStream)

//CTMutex mutextTest;

CTZRTP::CTZRTP(void *p):CtZrtpSession(){
   reset();
}

CTZRTP::~CTZRTP(){} 


void CTZRTP::reset(){
   
   iIsStarted[0]=iIsStarted[1]=0;
   bufSAS[0]=0;
   bufPeer[0]=0;;
   bufWarning[0]=0;
   bufSecurePBXMsg[0]=0;
   
   iCachesOk=-1;;
   iSoundPlayed=0;;
   iFailPlayed=0;
   
   iWarnDetected=0;
   iWasZRTPSecure=0;
   iAuthFailCnt=0;
   
   clearSdesString();
   
   iDisabledSent[0]=iDisabledSent[1]=0;

}

bool CTZRTP::t_createSdes(char *cryptoString, size_t *maxLen, streamName streamNm){
   
   int v=isVideo(streamNm);
   if(sdesStrings[v][0]){
      int l=strlen(&sdesStrings[v][0]);
      if(l>*maxLen){
         *maxLen=0;
         return false;
      }
      *maxLen=l;
      strcpy(cryptoString, &sdesStrings[v][0]);
      
      return true;
   }
   bool b=createSdes(cryptoString,maxLen,streamNm);
   if(!b){*maxLen=0;return b;}
   
   strncpy(&sdesStrings[v][0],cryptoString,127);
   
   
   return true;
}

void CTZRTP::clearSdesString(){
   memset(sdesStrings,0,sizeof(sdesStrings));
   
}

void CTZRTP::enrollAccepted(const char *mitm_name){
   CtZrtpSession::enrollAccepted((char*)mitm_name);
}

int CTZRTP::setDstHash(char *p, int iLen, int iIsVideo){
   

  // mutextTest.lock();
   if(iLen>100)return -1;
   
   char bufTMP[128];strncpy(bufTMP,p,iLen);bufTMP[iLen]=0;p=&bufTMP[0];
   //memset(bufTMP,'a',32);//test warning
   CtZrtpSession::setSignalingHelloHash((char*)p, TO_N);
   //mutextTest.unLock();
   return 0;
}
int CTZRTP::getSignalingHelloHash(char *helloHash, int iIsVideo, int index){
   

   return CtZrtpSession::getSignalingHelloHash(helloHash, TO_N, index);

}

void CTZRTP::release_zrtp(){
   
   int ws = iWasZRTPSecure;
   
   reset();
   release();

   //why to save? Apple iOS "15 wakes in 300sec" - it kills app
   static int iSave=1;
   if(iSave && ws){iSave=0; saveEntropy();}
}

int CTZRTP::init_zrtp(int iCaller, char *zid_base16, int iInitVideoHash, int iInitAudioHash){
   
  // mutextTest.lock();
   reset();
   
   setClientId("SC WD-zrtp");

   init(1,1);
   
   setUserCallback(this, CtZrtpSession::AudioStream);
   setUserCallback(this, CtZrtpSession::VideoStream);
//mutextTest.unLock();
   
   return 0;
}


int CTZRTP::clearCaches(){
   /*
    - make sure no calls are active
    - then release all streams with CtZrtpSession::release() function
    - then call CtZrtpSession::cleanCache()
    */
   CtZrtpSession::cleanCache();
   return 0;
}

int CTZRTP::isSecure(int iIsVideo){
   
  if(!iIsStarted[iIsVideo])return 0;
   return CtZrtpSession::isSecure(TO_N);
}

int CTZRTP::getStatus(int iIsVideo){
   if(!isZrtpEnabled() && !isSdesEnabled())return eSecurityDisabled;
   if(!iIsStarted[iIsVideo])return eNoPeer;
   return this->getCurrentState(TO_N);
}


int CTZRTP::encrypt(char *p, int &iLen, int iIsVideo){
   
   
   if(!p || iLen<1)return -1;
   
   if(!iIsStarted[iIsVideo] && !isSdesActive(TO_N))return 0;
   if(iIsVideo && !isSecure(0))return 0;
   
   size_t ret=0;
   
   bool r=processOutoingRtp((uint8_t *)p, iLen, &ret, TO_N);

   iLen=ret;
   return r?0:-1;
}

int CTZRTP::decrypt(char *p, int &iLen, int iIsVideo){

 //  * @return 1: success, 0: not an error but drop packet, -1: SRTP authentication failed,
 //  *            -2: SRTP replay check failed
   if(!p || iLen<1)return eDropPacket;
   
   if(!isZrtpEnabled() && !isSdesEnabled()){
      if(!iDisabledSent[iIsVideo]){
         iDisabledSent[iIsVideo]=1;
         zrtpcb->onNewZrtpStatus(this, NULL, iIsVideo);
      }
      return 0;
   }
   
   if(!iIsStarted[iIsVideo] && !isSdesActive(TO_N))return 0;
   if((iIsVideo && !isSecure(0)))return ePacketOk;
   /*
   if(iIsVideo && iIsStarted[iIsVideo]<50){
      iIsStarted[iIsVideo]++;
      return 0;
   }
   */
   size_t ret=0;
   int r=processIncomingRtp((uint8_t *)p, iLen, &ret, TO_N);
   iLen=ret;
   
   if(r==0)return eIsProtocol;
   if(r>0)return ePacketOk;
   
   printf("[err processIncomingRtp()=%d]\n",r);
   if(r==-1)return eAuthFailPacket;
   iAuthFailCnt++;
   return eDropPacket;

}

int CTZRTP::getInfoX(const char *key, char *p, int iMax, int iIsVideo){
  // mutextTest.lock();
   if(iMax<1)return 0;
   
   if(strcmp(key,"nomitm")==0){
      p[0]='0'+ (getStatus(iIsVideo)==eSecure);
      p[1]=0;
      return 1;
   }
   int r=getInfo(key,(uint8_t*)p,(size_t)iMax, TO_N);
  // mutextTest.unLock();
   return r;
}

void clearZrtpCachesG(void *pZrtpGlobals){
   CTZRTP::clearCaches();
}

#endif


