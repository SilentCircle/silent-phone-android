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


#ifndef _C_TIVI_PHONE_H
#define _C_TIVI_PHONE_H


#if defined(ANDROID_NDK)
#define T_TEST_SYNC_TIMER
#endif

#include <ctype.h>
#include "main.h"
#include "CSessions.h"
#include "../utils/utils_chat.h"




char *getDomain(char *);
int saveCfg(void *ph, int iIndex);
void deleteCfg(void *p, int iIndex);



class CTEngineCallBack; 
class CTLangStrings;
class CTSesMediaBase;
class CTStrBase;
/*
//TODO >>>>>

CTiViPhone *p = new CTiViPhone();
int iCallID = CTiViPhone::getUniqueCID();
//outgoing
p->call(iCallID, "123", ...);
...
p->endCall(iCallID);

//incoiming
CSesBase *ses = p->getFreeSes(iCallID);
...
ses->answer(iCallID);

TODO <<<<
*/

class CTiViPhone:public CPhSesions{
public:

   void save();

   static int thSipRec(void *f);
   static int TimerProc(void *f);

   CTiViPhone(CTEngineCallBack *cPhoneCallback, CTLangStrings *strings ,int iMaxSesions=8);
   ~CTiViPhone();

   int call(char *uri, CTSesMediaBase *media=NULL, const char *contactUri=NULL, int icontactUriLen=0);

   int answerCall(int SesId);
   int endCall(int SesId, int iReasonCode = 0);
   int reInvite(int SesId, const char *media);
   int hold(int iPutOnHold, int SesId);
   
   int isDstOnline(const char *dst, int *resp, CTEditBase *retMsg=NULL);
   void removeRetMsg(int SesId);

   int sendMsg(int ises, char *uri, const char *szCType, CTStrBase *e)
   {
      return sendSipMsg(ises, "MESSAGE",uri, szCType?szCType:"text/plain",e);
   }
   
   int canPlayRingtone();
   
   int sendSipKA(int iForce=0, int *respCode=NULL);
   
   int sendSipMsg(int ises, const char *szMeth, char * uri, const char *szCType,  CTStrBase *e,char *pSipParams=0, int iSipParamLen=0);
   int addRegister(char * uri=NULL);
   int remRegister(char * uri=NULL);
   void start();
   void checkSIPTransport();

   static int cleanNumber(char *p, int iLen);//makes 12340000 from 1(234)-0000 
   static int setNewGW(CTiViPhone *ph, PHONE_CFG *cfg, char *szAddr, ADDR *a=NULL);
   static int isValidAddr(char *szAddr,CTEngineCallBack *cPhoneCallback, ADDR *ret)
   {
      *ret=szAddr;
      if(ret->ip==0)
      {
        // ADDR aa;
         //aa=szAddr;
         char bufStr[128];
         strcpy(bufStr,szAddr);
         trim(&bufStr[0]);
         int i;
         for(i=0;bufStr[i];i++)if(bufStr[i]==':'){bufStr[i]=0;break;}
         //aa.toStr(&bufStr[0],0)
         ret->ip=cPhoneCallback->getIpByHost(&bufStr[0],0);
      }
      if(ret->ip && ret->getPort()==0)
      {
         ret->setPort(5060);
      }
      return ret->ip;
   }
  
   int getInfo(char *buf, int iMaxLen);
   void onTimer();
   void onNewIp(unsigned int ip, int iCanRecreate);
   int checkUri( char *pUriIn,int iInLen, char *pUriOut, int iMaxOutSize, URI *pUri, int iCheckDomain=1);
   
   void checkServDomainName(int iHasNewIP=0, int iNow=0);

   int waitOffline(int iMSec=2000)
   {
      if(p_cfg.isOnline())
      {
         p_cfg.iUserRegister=p_cfg.iCanRegister=0;
         remRegister();
         while(p_cfg.reg.uiRegUntil>0 && iMSec>0)
         {Sleep(50);iMSec-=50;}
      }
      return 0;
   }
   int closeEngine();
   
protected:
   int recMsg(SIP_MSG *sMsg, int rec, ADDR &addr);
   int checkAddr(ADDR *addr,int iIsRegResp);

   void restoreServ();
   
   int reRegSeq(int iStart);
public:
   int reInvite();
   static int parseUri(CTEngineCallBack *cPhoneCallback, PHONE_CFG& p_cfg,char *szUri, URI *uri, int iCheckDomain=1);
private:
   
   int iNextReRegSeq;
   
   void onNetCheckTimer();
   void chechRereg();
   
   int addMsgToWin(SIP_MSG *sMsg, char *p);
   CMsgMem cChatMem;
   
public:
   CTThread thTimer;
   
#ifdef T_TEST_SYNC_TIMER
   pthread_mutex_t timerMutex;
   pthread_cond_t  timerConditional;
   
   pthread_mutex_t timerDoneMutex;
   pthread_cond_t  timerDoneConditional;
#endif
   
  
};
#endif //_C_TIVI_PHONE_H
