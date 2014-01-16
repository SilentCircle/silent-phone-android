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

#ifndef _T_CFG_H
#define _T_CFG_H

/*
 #include "cfg_test.h"
 #define T_GET_CFG_FNC
 #undef _CFG_TEST_H
 #include "cfg_test.h"

 void *findCfgItemKey(void *pCfg, char * key, int iKeyLen, int &iLen){
 P_CFG *cfg=(P_CFG*)pCfg;
 if(iKeyLen==3 && strncmp(key,"PWD",3)==0){
 iLen=sizeof(cfg->pwd);
 return &cfg->pwd[0];
 }
 return cfg;;
 }
 */
#undef STR_CVG_ADD
#define STR_CVG_ADD

#define tbint int
#define tenum_int int

#ifndef T_GET_CFG_FNC
#define T_C_ITEM(T,ID,SZ,OPT) T ID 
#define T_C_ITEMI(ID, T_DEF) int ID 
#define T_C_ITEMIO(ID, T_DEF,OPT) int ID 
#define T_C_ITEME(ID, T_DEF,OPT) int ID 
#define T_C_ITEMB(ID, T_DEF) int ID 
#define T_C_ITEMC(ID,SZ, OPT) char ID [SZ]
#define T_C_ITEMPWD(ID,SZ) char ID[SZ]

typedef struct{
   char szName[256];
   int id;
   int iVolume;
}TAUDIO_CFG;

//PHONE_CFG  = user_cfg + phone some states

typedef struct {
   int iIndex;//file id
   
   void *pCTiViPhone;
   
   int iPlainPasswordDetected;//used only durring loading cfg
   
   int iCfgVers;
   int iCfgChangesDetected;
   int iNeedSave;
   int iDetectedNewServDomain;
   
   void setDefaults()
   {
      iPlainPasswordDetected=0;

      iShowDiscl=1;
      iSwitchCodecsNetwork=0;
      uiExpires=800;//800;//3600;
      iWarnNetworkUsage=iWarnCamMicUsage=1;
      iAccessPointID=-1;
      iAutoRegister=1;
      iAutoStart=1;
#ifdef __SYMBIAN32__
      iAutoStart=0;
#endif
      iAPPrio=eWifi3G;
#ifdef __APPLE__
      iPayloadSizeSend=40;
#else
      iPayloadSizeSend=2;
#endif
      iUseVAD=1;
      iUseAGC=1;
      iAutoShowApi=0;
      iUserRegister=iCanRegister=iAutoRegister;
      iHasAutoShowInCfg=0;
#ifdef _WIN32
      iSipPortToBind=5060;
#else
      iSipPortToBind=32040;
#endif
      iVCallMaxCpu=50;
      iRtpPort=25500;//25500;
      iVideoFrameEveryMs=120;
      iBeepAtMsg=1;
      iInsertTimeInMsg=1;
      aRingCfg.iVolume=100;
      aMicCfg.iVolume=100;
      aPlayCfg.iVolume=200;
      iCanUseZRTP=0;
      iShowDevID=1;
      iPermitSSRCChange=0;
      iLicIsUserServ=0;
      iDontSavePwd=0;
     // iUseTLS=0;
      strcpy(szSipTransport,"UDP");
      iUseAEC=1;
      
      iPayloadSizeSend3G=80;
      iUseVAD3G=1;
   }
   
   int isOnline()
   {
      return (((int)reg.uiRegUntil>0 || reg.bReReg) && reg.bUnReg==0 && iHasNetwork);
   }
   
   int useVAD(){
      return iNetworkIsMobile?iUseVAD3G:iUseVAD;
   }
   
   int payloadSize(){
      return iNetworkIsMobile?iPayloadSizeSend3G:iPayloadSizeSend;
   }
   
   char *getCodecs(){
      if(szACodecs3G[0] && iNetworkIsMobile)
      {
         return &szACodecs3G[0];
      }
      return &szACodecs[0];
   }
   
   struct{
      //unsigned int uiTryReRegAT;//??? rem
      t_ph_tick uiRegUntil;
      unsigned int bNeedReg:1;
      unsigned int bReReg:1;
      unsigned int bUnReg:1;
      unsigned int bRegistring:1;
   }reg;

   int iSkipXMLCfgServAddr;//xml config override system
   
   ADDR GW;//system
   
   STR_64 str32GWaddr;//system
   int iSignedCfgRead;
   
   
   TAUDIO_CFG aRingCfg;//global
   TAUDIO_CFG aPlayCfg;//global
   TAUDIO_CFG aMicCfg;//global
   
   enum{eSFConvertDnsToIp=0x01,eSFHideDemo=0x02};
   int iSettingsFlag;//system
   
   int iCheckDnsAfter;//dns cache ttl system
   char szSkipAdvCfgID[128];//system
   int iAddCodecSwitch;//system
   int iHasAutoShowInCfg;//system
   int iCfgHideNumberField;//system
   int iDontRepeatAudio;//system, TODO PLC
   int iResetStun;//system dont save
   int iShowDevID;//system global
   int iNet;//global,system, NAT-type detected by stun 
   int iReRegisterNow;
   int iIsInForeground;//system
   unsigned int uiPrevExpires;//APPLE ios bacgrounds
   
   int iIsValidZRTPKey;//global
   int iCanUseZRTP;//lic mode 0 off, 1 pasive, 2 eco, 3 business
   unsigned int uiZZRTP_bus_lic_exp_at;//system global
   int iAccessPointID;//global
   char szLicServName[64];//global system
   int iWarnNetworkUsage;//sytsem global 
   int iWarnCamMicUsage;//sytsem global
   
   char szLangFN[256];//global system
   char szSkinFolder[256];//global  system
   char szCountryListFN[256];//global  system
   
   unsigned int uiSipCSeq;//system
   char szZID_base16[32];//global system // 24bytes used, global ??? can user edit it - Victor's ZRTPlib only
   char szSupportMail[64];//global system
   int iShowDiscl;//sytsem global 
   char szDevidAbout[64];//system
   int iEnable729;//system
   int iFirstLicencePartIsOk;//system
   int iIsLKeyValid;//system
   char szUNLabel[32];//system symbian
   char szPWDLabel[32];//system symbian
   int iFWInUse53;//system, info
   
   int iCanRegister;//system
   int iUserRegister;//system user wants to go online, info
   int iHasNetwork;//system
   int iNetworkIsMobile;
   
   int iTinaFakeID;
   
   int iDisableAdvCfg;
   int iDisablePhonebook;
   int iDisableBuddys;
   
   int iSwitchCodecsNetwork;//dont use depricated - use szACodecs3G and szACodecs
   
   char szPwdEndAdd[8];//will append this to pwd, system
   char szApiShortName[32];
   
   char szMsgTranslateFN[256];//system, translates sip resp meg to user friendly
   
   int iGSMActive;//set this if gsm call is active

   
   
   
   enum {e_first, e_tbint, e_char, e_int, e_tenum_int, e_secure ,e_last};
   
#else 

#define T_C_ITEMIO(V_ID, T_DEF,OPT) T_C_ITEM(int, V_ID, 0,OPT) 
#define T_C_ITEMI(V_ID, T_DEF) T_C_ITEM(int, V_ID, 0,NULL) 
#define T_C_ITEMB(V_ID, T_DEF) T_C_ITEM(tbint, V_ID, 0,NULL)  
#define T_C_ITEMC(V_ID,SZ, OPT) T_C_ITEM(char, V_ID, SZ,OPT)
#define T_C_ITEME(V_ID, T_DEF,OPT) T_C_ITEM(tenum_int ,V_ID,0,OPT) 
#define T_C_ITEMPWD(V_ID,SZ) T_C_ITEM(secure ,V_ID,SZ,NULL)    
   
#define T_C_ITEM(T,V_ID,SZ, OPT) \
if(iKeyLen+1==sizeof(#V_ID) && t_isEqual(key,#V_ID,iKeyLen)){\
iLen=sizeof(cfg->STR_CVG_ADD V_ID );\
if(opt)*opt=(char*)OPT;\
if(type)*type=PHONE_CFG::e_##T;\
return &cfg->STR_CVG_ADD V_ID;\
}
   
   void *findCfgItemKey(void *pCfg, char *key, int iKeyLen, int &iLen, char **opt, int *type){
      PHONE_CFG *cfg=(PHONE_CFG*)pCfg;
      iLen=0;
      //TODO
      //addFncCB(cbList, pCfg, "tempServ", onNewServ, eOnSet);
      if(iKeyLen==7 && strcmp(key,"tmpServ")==0){
         if(opt)*opt=NULL;
         if(type)*type=PHONE_CFG::e_char;
         iLen=sizeof(cfg->tmpServ);
         if(!cfg->tmpServ[0])strcpy(&cfg->tmpServ[0],cfg->str32GWaddr.strVal);//TODO fnc
         return &cfg->tmpServ[0];
      }
#endif
      
#undef STR_CVG_ADD
#define STR_CVG_ADD user.
#ifndef T_GET_CFG_FNC
      struct _SIP_USER{
#endif
         T_C_ITEMC(un,64,"");
         T_C_ITEMPWD(pwd,64);
         T_C_ITEMC(nr,64,"");
         T_C_ITEMC(nick,64,"");//SIP display name
         T_C_ITEMC(country,64,"");
#ifndef T_GET_CFG_FNC
      }user;
      enum eAPPrio{eWifi3G,e3GOnly,eWifiOnly,eWifi3GRoam,e3GRoam};
#endif
#undef STR_CVG_ADD
#define STR_CVG_ADD 
      
      
      T_C_ITEMC(szTitle,64,"");//service name
      T_C_ITEMB(iAccountIsDisabled,0);
      T_C_ITEMC(szLicenceKey,32,"");
      
      T_C_ITEMB(iUseAEC,1);
      T_C_ITEMB(iUseAGC,1);
      T_C_ITEMB(iAutoStart,1);     
      T_C_ITEME(iAPPrio,1,"eWifi3G,e3GOnly,eWifiOnly,eWifi3GRoam,e3GRoam");     
      T_C_ITEMB(iAutoShowApi,1);     
      T_C_ITEMI(iSipPortToBind,5060);     
      T_C_ITEMI(iRtpPort,25500);     
      
      T_C_ITEMC(tmpServ,64,"");
      T_C_ITEMC(bufpxifnat,64,"");
      T_C_ITEMC(bufpxifFW53,64,"");
      
      T_C_ITEMB(iAutoRegister,1);     
      T_C_ITEMB(iAutoAnswer,0);     
      T_C_ITEMIO(iVideoFrameEveryMs,60,"33,66,85,120,200,300,500,1000");     
      
      T_C_ITEMI(iVideoKbps,200);//if zero use soft pref
      T_C_ITEMIO(iVCallMaxCpu,70,"20,30,40,50,60,70,80,90,95");//if zero use soft pref
      
      
      

      T_C_ITEMC(bufStun,64,"");
      T_C_ITEMB(iUseStun,1);

      T_C_ITEMC(szACodecs,64,"");
      T_C_ITEMC(szACodecsDisabled,64,"");

 
      T_C_ITEMC(szACodecs3G,64,"");
      T_C_ITEMC(szACodecsDisabled3G,64,"");
      //TODO szACodecsALL
      T_C_ITEMC(szUA,64,"");
      T_C_ITEMC(szUASDP,64,"");
      T_C_ITEMB(iResponseOnlyWithOneCodecIn200Ok,0);
      
      T_C_ITEMB(iSipKeepAlive,1);
      T_C_ITEMB(iIsTiViServFlag,1);
      T_C_ITEMC(szSipTransport,32,"UDP,TCP,TLS,(TODO) UDP or TCP");
      //T_C_ITEMB(iUseTLS,0);
      T_C_ITEMB(iDebug,0);
      T_C_ITEMB(iBeepAtMsg,0);
      T_C_ITEMB(iHideIP,0);//hides serv ip in incoming call
      T_C_ITEMB(iInsertTimeInMsg,1);
      T_C_ITEMB(iUseOnlyNatIp,0);
      T_C_ITEMB(fToTagIgnore,0);
      T_C_ITEMB(iDisableChat,0);
      T_C_ITEMB(iDisableVideo,0);
      T_C_ITEMB(iCfgPwdAsNumber,0);
      T_C_ITEMB(iUseSipBuddys,0);
      T_C_ITEMB(iDefaultNumberInput,0);//phones with keypads

      
      T_C_ITEMIO(iPayloadSizeSend,40,"20,40,60,80");
      T_C_ITEMIO(iPayloadSizeSend3G,80,"20,40,60,80");
      //T_C_ITEMB(iSendEachRTPPacketTwice,1);//if bad net 
      T_C_ITEMB(iUseVAD,1);
      T_C_ITEMB(iUseVAD3G,1);
      
      T_C_ITEMB(iCfgUserNameAsNumber,1);
      T_C_ITEMB(iZRTP_On,1);
      T_C_ITEMB(iSDES_On,0);
      T_C_ITEMB(iZRTPTunnel_On,1);
      
      T_C_ITEMB(iCanUseP2Pmedia,1);
      
      T_C_ITEMB(iUseTiViBuddys,1);
      
      T_C_ITEMC(szPrefix,32,"");
      //void *pTransMsgList;
      T_C_ITEMB(iLicIsUserServ,1);
      T_C_ITEMB(iPermitSSRCChange,1);
      T_C_ITEMB(iDontSavePwd,1);
      T_C_ITEMB(iDontReadSaveCfg,1);
      
      T_C_ITEMI(uiExpires,650);
      T_C_ITEMI(iCameraID,0);
      
      T_C_ITEMB(iCanAttachDetachVideo,0);//reinvite audio video
      
      T_C_ITEMB(bCreatedByUser,0);
      
      
      
#ifndef T_GET_CFG_FNC
   struct{
      struct _SIP_USER tmpUser;
      
      int iSeq;//0 doNothing; 1 logout flag;2 start logout, 3 copy tmpUser To user,register, set to 0
   }changeUserParams;
      
   }PHONE_CFG;
   
   void *findCfgItemKey(void *pCfg, char *key, int iKeyLen, int &iLen, char **opt, int *type);
   int setCfgValue(char *pSet, int iSize, void *pCfg, char *key, int iKeyLen);
#else
   return NULL;
}//fnc end
void *findGlobalCfgKey(char *key, int iKeyLen, int &iSize, char **opt, int *type);

int setCfgValue(char *pSet, int iSize, void *pCfg, char *key, int iKeyLen){
   int iLen=0;
   void *ofs=NULL;
   
   if(!pCfg){
      ofs= findGlobalCfgKey(key,iKeyLen, iLen,NULL,NULL);
   }
   else {
     ofs = findCfgItemKey(pCfg,key,iKeyLen, iLen,NULL,NULL);
      if(!ofs) ofs=findGlobalCfgKey(key,iKeyLen, iLen,NULL,NULL);
   }
   if(iLen==0 || !ofs)return -2;
   if(iSize>iLen)iSize=iLen;
   
   PHONE_CFG *c=(PHONE_CFG*)pCfg;
   if(c && ((iKeyLen==10 && t_isEqual("bufpxifnat",key,7)) || 
            (iKeyLen==7  && t_isEqual("tmpServ",key,7)) )
      ){
      c->iDetectedNewServDomain=1;
    //  puts("serv");
   }
   
  // printf("[kl=%d,%s]",iKeyLen,key);

   if(iLen!=iSize)memset(ofs,0,iLen);
   memcpy(ofs,pSet,iSize);
   //TODO cmp
   if(c){
      c->iCfgChangesDetected++;
      c->iNeedSave=1;
   }
   //c->fncOnArgSet(pUserData, key);
   return 0;
}

int setCfgValueSZ(char *sz, void *pCfg, char *key, int iKeyLen){
   int iLen=0;
   void *ofs=NULL;
   int type;
   if(!pCfg){
      ofs= findGlobalCfgKey(key,iKeyLen, iLen,NULL,&type);
   }
   else {
      ofs = findCfgItemKey(pCfg,key,iKeyLen, iLen,NULL,&type);
      if(!ofs) ofs=findGlobalCfgKey(key,iKeyLen, iLen,NULL,&type);
   }
   
   if(iLen==0 || !ofs)return -2;

   int i=0;
   void *pSet;
   int iSize;
   if(type == PHONE_CFG::e_char){
      pSet=sz;
      iSize=strlen(sz);
   }
   else{
      iSize=4;
      pSet=&i;
      i=atoi(sz);
   }

   
   if(iSize>iLen)iSize=iLen;
   
   PHONE_CFG *c=(PHONE_CFG*)pCfg;
   if(c && ((iKeyLen==10 && t_isEqual("bufpxifnat",key,10)) ||
            (iKeyLen==7  && t_isEqual("tmpServ",key,7)) )
      ){
      c->iDetectedNewServDomain=1;
      //  puts("serv");
   }
   
   // printf("[kl=%d,%s]",iKeyLen,key);
   
   if(iLen!=iSize)memset(ofs,0,iLen);
   memcpy(ofs,pSet,iSize);
   //TODO cmp
   if(c){
      c->iCfgChangesDetected++;
      c->iNeedSave=1;
   }
   //c->fncOnArgSet(pUserData, key);
   return 0;
   
}


#endif

#undef T_C_ITEMI
#undef T_C_ITEMB
#undef T_C_ITEMC
#undef T_C_ITEME
#undef T_C_ITEMIO
#undef T_C_ITEM
#undef T_C_ITEMPWD
#endif
