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


#include <string.h>
#include "../baseclasses/CTEditBase.h"
#include "main.h"

#ifdef _WIN32
#define snprintf _snprintf
#endif


void setCfgFN(CTEditBase &b, int iIndex);
void setFileBackgroundReadable(CTEditBase &b);


//TODO int getGlobInts(char *pkeys[], int *values[], int iMax);
typedef struct{
   
   //zrtp
   
   int iClearZRTPCaches;
   int iClearZRTP_ZID;
   
   int iPreferDH2K;
   
   int iDisableAES256;
   int iDisableDH2K;
   int iDisable256SAS;
   
   int iDisableECDH384;
   int iDisableECDH256;
   int iEnableSHA384;
   
   int iDisableSkein;
   int iDisableTwofish;
   
   int iHideCfg;
   
   int iDontSimplifyVideoUI;
   //display unsolicited video
   int iDisplayUnsolicitedVideo;
   
   int iAudioUnderflow;
   int iShowRXLed;
   
   int iKeepScreenOnIfBatOk;
   
   
   int iSASConfirmClickCount;//TODO remove
   
   int iRetroRingtone;
   
   char szLastUsedAccount[128];
   
}TG_SETTINS;

/*
 ecdh-384(enable-sha384), ecdh-256, dh-3072,dh-2048,
 enable-sha384
 */

TG_SETTINS g_Settings;

#define G_CFG_FILE_ID 10555

int getGCfgFileID(){return G_CFG_FILE_ID;}


class CTG{
   int iInitOk;
   TG_SETTINS prevSettings;
public:
   CTG(){iInitOk=0;memset(&g_Settings,0,sizeof(TG_SETTINS));memset(&prevSettings,0,sizeof(TG_SETTINS));}
   void init(){
      if(iInitOk)return;
      iInitOk=1;
      
      CTEditBase b(1024);
      setCfgFN(b,G_CFG_FILE_ID);

      g_Settings.iEnableSHA384=1;
      g_Settings.iSASConfirmClickCount=10;

      
      int iCfgLen=0;
      char *p=loadFileW(b.getText(),iCfgLen);
      if(!p){iInitOk=0;return;}
      setFileBackgroundReadable(b);
      
      
      int getCFGItemSz(char *ret, int iMaxSize, char *p, int iCfgLen, const char *key);
      int getCFGItemI(int *ret, char *p, int iCfgLen, const char *key);
      
      
#define  M_FNC_INT_T(_DST,_K)    getCFGItemI(&(_DST),p,iCfgLen,#_K)
      
      M_FNC_INT_T(g_Settings.iDisableDH2K,iDisableDH2K);
      M_FNC_INT_T(g_Settings.iPreferDH2K,iPreferDH2K);
      M_FNC_INT_T(g_Settings.iDisableAES256,iDisableAES256);
      M_FNC_INT_T(g_Settings.iDisable256SAS,iDisable256SAS);
      
      M_FNC_INT_T(g_Settings.iDisableECDH384,iDisableECDH384);
      M_FNC_INT_T(g_Settings.iDisableECDH256,iDisableECDH256);
      M_FNC_INT_T(g_Settings.iEnableSHA384,iEnableSHA384);
      
      M_FNC_INT_T(g_Settings.iDisableSkein,iDisableSkein);
      M_FNC_INT_T(g_Settings.iDisableTwofish,iDisableTwofish);
      
      
      
      
      
      if(g_Settings.iDisableECDH384==0){
         g_Settings.iEnableSHA384=1;
         g_Settings.iDisableAES256=0;
      }
      
      M_FNC_INT_T(g_Settings.iHideCfg,iHideCfg);
      
      M_FNC_INT_T(g_Settings.iDontSimplifyVideoUI,iDontSimplifyVideoUI);
      M_FNC_INT_T(g_Settings.iDisplayUnsolicitedVideo,iDisplayUnsolicitedVideo);
      
      M_FNC_INT_T(g_Settings.iAudioUnderflow,iAudioUnderflow);
      g_Settings.iDontSimplifyVideoUI=1;
      
      
      M_FNC_INT_T(g_Settings.iKeepScreenOnIfBatOk,iKeepScreenOnIfBatOk);
      M_FNC_INT_T(g_Settings.iShowRXLed,iShowRXLed);
      M_FNC_INT_T(g_Settings.iRetroRingtone,iRetroRingtone);
      
      
      
      
      M_FNC_INT_T(g_Settings.iSASConfirmClickCount,iSASConfirmClickCount);
      
      g_Settings.iSASConfirmClickCount=10;
      
   getCFGItemSz(g_Settings.szLastUsedAccount,sizeof(g_Settings.szLastUsedAccount),p,iCfgLen,"szLastUsedAccount");
      
      
      memcpy(&prevSettings,&g_Settings,sizeof(TG_SETTINS));
      
      delete p;
   }
   void save(){
      if(!iInitOk)return;
      
      if(memcmp(&prevSettings,&g_Settings,sizeof(TG_SETTINS))==0)return;
      memcpy(&prevSettings,&g_Settings,sizeof(TG_SETTINS));
      char dst[2048];
      CTEditBase b(2048);
      setCfgFN(b,G_CFG_FILE_ID);
      
      int l=0;
#define SAVE_G_CFG_I(_V,_K)     l+=snprintf(&dst[l],sizeof(dst)-1-l,"%s: %d\n",#_K,_V);
      
      SAVE_G_CFG_I(g_Settings.iPreferDH2K,iPreferDH2K);
      SAVE_G_CFG_I(g_Settings.iDisableAES256,iDisableAES256);
      SAVE_G_CFG_I(g_Settings.iDisable256SAS,iDisable256SAS);
      SAVE_G_CFG_I(g_Settings.iDisableDH2K,iDisableDH2K);
      
      SAVE_G_CFG_I(g_Settings.iDisableECDH384,iDisableECDH384);
      SAVE_G_CFG_I(g_Settings.iDisableECDH256,iDisableECDH256);
      SAVE_G_CFG_I(g_Settings.iEnableSHA384,iEnableSHA384);
      
      SAVE_G_CFG_I(g_Settings.iDisableSkein,iDisableSkein);
      SAVE_G_CFG_I(g_Settings.iDisableTwofish,iDisableTwofish);
      
      SAVE_G_CFG_I(g_Settings.iHideCfg,iHideCfg);
      
      SAVE_G_CFG_I(g_Settings.iDontSimplifyVideoUI,iDontSimplifyVideoUI);
      SAVE_G_CFG_I(g_Settings.iDisplayUnsolicitedVideo,iDisplayUnsolicitedVideo);
      SAVE_G_CFG_I(g_Settings.iAudioUnderflow,iAudioUnderflow);
      SAVE_G_CFG_I(g_Settings.iRetroRingtone,iRetroRingtone);
      
      
      
      
      
      SAVE_G_CFG_I(g_Settings.iSASConfirmClickCount,iSASConfirmClickCount);//TODO remove
      SAVE_G_CFG_I(g_Settings.iShowRXLed,iShowRXLed);
      SAVE_G_CFG_I(g_Settings.iKeepScreenOnIfBatOk,iKeepScreenOnIfBatOk);
      
      
      
      
      
      l+=snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szLastUsedAccount",g_Settings.szLastUsedAccount);
      
      
      saveFileW(b.getText(),&dst[0],l);
      
      
      setFileBackgroundReadable(b);
      
      
   }
   ~CTG(){
      save();
      
   }
   
};
CTG gs;

void t_save_glob(){
   gs.save();
}

void t_init_glob(){
   gs.init();
}

void *findGlobalCfgKey(char *key, int iKeyLen, int &iSize, char **opt, int *type){
   
#define GLOB_I_CHK(_K) \
if(iKeyLen+1==sizeof(#_K) &&  t_isEqual(key,#_K,iKeyLen)){\
if(type)*type=PHONE_CFG::e_tbint;\
if(opt)*opt=NULL;\
iSize=sizeof(g_Settings._K);\
return &g_Settings._K;\
}
   
#define GLOB_SZ_CHK(_K) \
if(iKeyLen+1==sizeof(#_K) &&  t_isEqual(key,#_K,iKeyLen)){\
if(type)*type=PHONE_CFG::e_char;\
if(opt)*opt=NULL;\
iSize=sizeof(g_Settings._K);\
return &g_Settings._K;\
}
   
   //
   
   GLOB_I_CHK(iDisableTwofish);
   GLOB_I_CHK(iDisableSkein);
   
   GLOB_I_CHK(iDisableECDH256);
   GLOB_I_CHK(iDisableECDH384);
   GLOB_I_CHK(iEnableSHA384);
   
   GLOB_I_CHK(iDisableAES256);
   GLOB_I_CHK(iDisableDH2K);
   GLOB_I_CHK(iDisable256SAS);
   GLOB_I_CHK(iClearZRTPCaches);
   GLOB_I_CHK(iClearZRTP_ZID);
   GLOB_I_CHK(iPreferDH2K);
   
   GLOB_I_CHK(iHideCfg);
   
   
   GLOB_I_CHK(iDontSimplifyVideoUI);
   GLOB_I_CHK(iDisplayUnsolicitedVideo);
   
   GLOB_I_CHK(iAudioUnderflow);
   GLOB_I_CHK(iRetroRingtone);
   
   

   GLOB_I_CHK(iSASConfirmClickCount);
   
   GLOB_I_CHK(iShowRXLed);
   GLOB_I_CHK(iKeepScreenOnIfBatOk);
   
   
   GLOB_SZ_CHK(szLastUsedAccount);
   
   return NULL;
}



void *findGlobalCfgKey(const char *key){
   int iSize;
   char *opt;
   int type;
   return findGlobalCfgKey((char*)key,strlen(key),iSize,&opt,&type);
}



int setGlobalValueByKey(const char *key, int iKeyLen, char *sz){

#define GLOB_SZ_CHK_P(_K) \
if(iKeyLen+1==sizeof(#_K) &&  t_isEqual(key,#_K,iKeyLen)){\
strcpy(&g_Settings._K[0],sz);\
return 0;\
}
   GLOB_SZ_CHK_P(szLastUsedAccount);
   return 1;
}

int setGlobalValueByKey(const char *key,  char *sz){
   return setGlobalValueByKey(key,strlen(key),sz);
}

int hasActiveCalls();

void checkGlobalSettings(void *g_zrtp){
   
   if(g_Settings.iClearZRTPCaches && !hasActiveCalls()){
      g_Settings.iClearZRTPCaches=0;
      
      void clearZrtpCachesG(void *pZrtpGlobals);
      clearZrtpCachesG(g_zrtp);
      
      puts("caches cleared");
   }
}
