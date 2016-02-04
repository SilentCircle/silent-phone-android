//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.


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
   int whispering(int iStart, int SesId);
   
   int isDstOnline(const char *dst, int *resp, CTEditBase *retMsg=NULL);
   void removeRetMsg(int SesId);

   int sendMsg(int ises, char *uri, const char *szCType, CTStrBase *e)
   {
      return sendSipMsg(ises, "MESSAGE",uri,NULL, szCType?szCType:"text/plain",e);
   }
   
   int canPlayRingtone();
   
   int sendSipKA(int iForce=0, int *respCode=NULL);
   
   int sendSipMsg(int ises, const char *szMeth, char *uri, const char *uriAdd, const char *szCType,  CTStrBase *e,char *pSipParams=0, int iSipParamLen=0);
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
   
   void goOnline(int iForce=0);

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
   
   void endAllCalls();
   
   void setZrtpCB(CTZrtpCb *p)
   {
      if(p)zrtpCB=p;
   }
   
protected:
   int recMsg(SIP_MSG *sMsg, int rec, ADDR &addr);
   int checkAddr(ADDR *addr,int iIsRegResp);

   void restoreServ();
   int verifyDomainAddress();//re-resolves server dns name or proxy dns name
   
   int reRegSeq(int iStart);
public:
   int reInvite();
   static int parseUri(CTEngineCallBack *cPhoneCallback, PHONE_CFG& p_cfg,char *szUri, URI *uri, int iCheckDomain=1);
private:
   
   void updateExtAddr();
   
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
