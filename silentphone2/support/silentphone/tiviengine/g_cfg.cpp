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
#include <stdlib.h>
#include <string.h>
#include "../baseclasses/CTEditBase.h"
#include "main.h"
#include "../utils/CTDataBuf.h"

#include "tivi_log.h"


void setCfgFN(CTEditBase &b, int iIndex);
void setFileBackgroundReadable(CTEditBase &b);

void setAudioInputVolume(int);
void setAudioOutputVolume(int);
void setDefaultDialingPref();


int t_snprintf(char *buf, int iMaxSize, const char *format, ...);


//TODO int getGlobInts(char *pkeys[], int *values[], int iMax);
typedef struct{
   
   int iExitingAndDoSaveNothingOnDisk;
   //zrtp

   
   int iClearZRTPCaches;
   int iClearZRTP_ZID;
   
   int iPreferDH2K;
   
   int iDisableAES256; //rename to iDisable256keySize
   int iDisableDH2K;
   int iDisable256SAS;
   
   int iDisableECDH384;
   int iDisableECDH256;
   int iEnableSHA384;//rename to iDisable384hashSize
   
   int iDisableSkein;//auth
   int iDisableTwofish;

   int iEnableDisclosure; //must not be saved

   int iHideCfg;
   
   int iEnableDialHelper;
   
   int iDontSimplifyVideoUI;
   //display unsolicited video
   int iDisplayUnsolicitedVideo;
   
   int iAudioUnderflow;
   int iShowRXLed;
   int iShowGeekStrip;
   
   int iKeepScreenOnIfBatOk;
   
   int iEnableAirplay;//must not be saved , it is not safe to send audio to somewhere
   
   int iPreferNIST;
   int iDisableBernsteinCurve3617;
   int iDisableBernsteinCurve25519;
   int iDisableSkeinHash;
   
   int iSASConfirmClickCount;//TODO remove
   
   int iRetroRingtone;//TODO remove
   
   int iShowAxoErrorMessages;
   int iDontSendDeliveryNotifications;
   
   int iShowMessageNotifications;
   
   int iBlockLocalDataRetention;
   int iBlockRemoteDataRetention;
    
   int iDisableCallKit;
   int iEnableNativeRingtone;

   int iForceFWTraversal;
   int iEnableFWTraversal;
   
   int ao_volume;//windows only
   int ai_volume;//windows only
   
   char szLastUsedAccount[128];
   
   char szRingTone[64];
   char szTextTone[64];
    
   char szOnHoldMusic[64];
   char szPutOnHoldSound[64];
   char szWelcomeSound[64];
   
   char szDialingPrefCountry[64];
   
   char szRecentsMaxHistory[32];
   char szMessageNotifcations[64];
   
    // Passocde
    int iPasscodeEnableWipe;
    int iPasscodeEnableTouchID;
    char szPasscodeTimeout[32];
    
    //DebugLogging:
    int iEnableDebugLogging;
    char szDebugLoggingSetting[32];
   
}TG_SETTINS;

/*
 ecdh-384(enable-sha384), ecdh-256, dh-3072,dh-2048,
 enable-sha384
 */

//the g_Settings must be a singleton
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
      
      CTEditBase b(4096*2);
      setCfgFN(b,G_CFG_FILE_ID);

      g_Settings.iEnableSHA384=1;
      g_Settings.iSASConfirmClickCount=10;
      g_Settings.iAudioUnderflow = 1;
      g_Settings.iShowRXLed = 1;
#ifdef __APPLE__
      int isTablet(void);
      g_Settings.iKeepScreenOnIfBatOk = isTablet();//ipad, not iphone
#endif
      
      int iCfgLen=0;
      char *p=loadFileW(b.getText(),iCfgLen);
      if(!p){iInitOk=0;return;}
      setFileBackgroundReadable(b);
     // puts(p);
      
      
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
      
      M_FNC_INT_T(g_Settings.iShowAxoErrorMessages,iShowAxoErrorMessages);
      M_FNC_INT_T(g_Settings.iDontSendDeliveryNotifications,iDontSendDeliveryNotifications);
      
      g_Settings.iShowMessageNotifications = 1;
      M_FNC_INT_T(g_Settings.iShowMessageNotifications,iShowMessageNotifications);

     // M_FNC_INT_T(g_Settings.iBlockLocalDataRetention,iBlockLocalDataRetention);
      //M_FNC_INT_T(g_Settings.iBlockRemoteDataRetention,iBlockRemoteDataRetention);
   
      g_Settings.iBlockLocalDataRetention = 1;//block DR
      g_Settings.iBlockRemoteDataRetention = 1;//block DR 
      
      g_Settings.iDisableCallKit = 0;
      M_FNC_INT_T(g_Settings.iDisableCallKit, iDisableCallKit);
       
      g_Settings.iEnableNativeRingtone = 0;
      M_FNC_INT_T(g_Settings.iEnableNativeRingtone, iEnableNativeRingtone);
       
      int r = M_FNC_INT_T(g_Settings.iPreferNIST,iPreferNIST);
      
      if( r<0 ){//iPreferNIST is not detected 
         g_Settings.iPreferNIST = 0;
         g_Settings.iDisableTwofish = 0;//must override here it was previously 1 
         g_Settings.iDisableSkein = 0;
      }
      
      M_FNC_INT_T(g_Settings.iDisableBernsteinCurve25519,iDisableBernsteinCurve25519);
      M_FNC_INT_T(g_Settings.iDisableBernsteinCurve3617,iDisableBernsteinCurve3617);

      
      if(g_Settings.iDisableECDH384==0){
         g_Settings.iEnableSHA384=1;
         g_Settings.iDisableAES256=0;
      }
      g_Settings.iEnableDialHelper=1;
      
      M_FNC_INT_T(g_Settings.iHideCfg,iHideCfg);
      M_FNC_INT_T(g_Settings.iEnableDialHelper,iEnableDialHelper);
      
      
      M_FNC_INT_T(g_Settings.iDontSimplifyVideoUI,iDontSimplifyVideoUI);
      M_FNC_INT_T(g_Settings.iDisplayUnsolicitedVideo,iDisplayUnsolicitedVideo);
      
      M_FNC_INT_T(g_Settings.iAudioUnderflow,iAudioUnderflow);
      M_FNC_INT_T(g_Settings.iShowGeekStrip,iShowGeekStrip);
      
      g_Settings.iDontSimplifyVideoUI=1;
      
      
      M_FNC_INT_T(g_Settings.iKeepScreenOnIfBatOk,iKeepScreenOnIfBatOk);
      M_FNC_INT_T(g_Settings.iShowRXLed,iShowRXLed);
      M_FNC_INT_T(g_Settings.iDisableSkeinHash,iDisableSkeinHash);
      
      M_FNC_INT_T(g_Settings.iForceFWTraversal,iForceFWTraversal);
      
      g_Settings.iEnableFWTraversal = 1;
      M_FNC_INT_T(g_Settings.iEnableFWTraversal,iEnableFWTraversal);
      
      M_FNC_INT_T(g_Settings.iRetroRingtone,iRetroRingtone);
      
      M_FNC_INT_T(g_Settings.iSASConfirmClickCount,iSASConfirmClickCount);

      g_Settings.ao_volume=100;//defaults
      g_Settings.ai_volume=100;
      
      M_FNC_INT_T(g_Settings.ao_volume,ao_volume);
      M_FNC_INT_T(g_Settings.ai_volume,ai_volume);
      
      setAudioOutputVolume(g_Settings.ao_volume);
      setAudioInputVolume(g_Settings.ai_volume);
      
      
      
      g_Settings.iSASConfirmClickCount=10;
      
      getCFGItemSz(g_Settings.szLastUsedAccount,sizeof(g_Settings.szLastUsedAccount),p,iCfgLen,"szLastUsedAccount");
      
      strcpy(g_Settings.szRingTone,"Default");
      getCFGItemSz(g_Settings.szRingTone,sizeof(g_Settings.szRingTone),p,iCfgLen,"szRingTone");

      strcpy(g_Settings.szTextTone,"Default");
      getCFGItemSz(g_Settings.szTextTone,sizeof(g_Settings.szTextTone),p,iCfgLen,"szTextTone");

      getCFGItemSz(g_Settings.szDialingPrefCountry,sizeof(g_Settings.szDialingPrefCountry),p,iCfgLen,"szDialingPrefCountry");
      
      strcpy(g_Settings.szRecentsMaxHistory,"1 month");
      strcpy(g_Settings.szMessageNotifcations,"Message and Sender");

      getCFGItemSz(g_Settings.szRecentsMaxHistory,sizeof(g_Settings.szRecentsMaxHistory),p,iCfgLen,"szRecentsMaxHistory");
      getCFGItemSz(g_Settings.szMessageNotifcations,sizeof(g_Settings.szMessageNotifcations),p,iCfgLen,"szMessageNotifcations");
      
       //DebugLogging:
       g_Settings.iEnableDebugLogging = 0;
       M_FNC_INT_T(g_Settings.iEnableDebugLogging, iEnableDebugLogging);
       strcpy(g_Settings.szDebugLoggingSetting,"1 day");
       getCFGItemSz(g_Settings.szDebugLoggingSetting,sizeof(g_Settings.szDebugLoggingSetting),p,iCfgLen,"szDebugLoggingSetting");
       
       // Passcode
       g_Settings.iPasscodeEnableWipe = 0;
       M_FNC_INT_T(g_Settings.iPasscodeEnableWipe, iPasscodeEnableWipe);
       
       g_Settings.iPasscodeEnableTouchID = 1;
       M_FNC_INT_T(g_Settings.iPasscodeEnableTouchID, iPasscodeEnableTouchID);
       
       strcpy(g_Settings.szPasscodeTimeout, "1 minute");
       getCFGItemSz(g_Settings.szPasscodeTimeout, sizeof(g_Settings.szPasscodeTimeout), p, iCfgLen, "szPasscodeTimeout");
       
      //strncmp(g_Settings.szRecentsMaxHistory, "szRec",5)==0 old bug fix, where it was storeing key: value with value as ""
      if(!g_Settings.szDialingPrefCountry[0] || strncmp(g_Settings.szDialingPrefCountry, "szRec",5)==0){
         setDefaultDialingPref();
      }
      
      memcpy(&prevSettings,&g_Settings,sizeof(TG_SETTINS));
      
      delete p;
   }
   void save(){
      if(!iInitOk)return;
      

      if(g_Settings.iExitingAndDoSaveNothingOnDisk)return;
      
      if(memcmp(&prevSettings,&g_Settings,sizeof(TG_SETTINS))==0)return;
      memcpy(&prevSettings,&g_Settings,sizeof(TG_SETTINS));
      char dst[4096*2];
      CTEditBase b(4096*2);
      setCfgFN(b,G_CFG_FILE_ID);
      
      int l=0;
#define SAVE_G_CFG_I(_V,_K)     l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %d\n",#_K,_V);
      
      SAVE_G_CFG_I(g_Settings.iPreferDH2K,iPreferDH2K);
      SAVE_G_CFG_I(g_Settings.iDisableAES256,iDisableAES256);
      SAVE_G_CFG_I(g_Settings.iDisable256SAS,iDisable256SAS);
      SAVE_G_CFG_I(g_Settings.iDisableDH2K,iDisableDH2K);
      
      SAVE_G_CFG_I(g_Settings.iDisableECDH384,iDisableECDH384);
      SAVE_G_CFG_I(g_Settings.iDisableECDH256,iDisableECDH256);
      SAVE_G_CFG_I(g_Settings.iEnableSHA384,iEnableSHA384);
      
      SAVE_G_CFG_I(g_Settings.iDisableSkein,iDisableSkein);
      SAVE_G_CFG_I(g_Settings.iDisableTwofish,iDisableTwofish);
      
      SAVE_G_CFG_I(g_Settings.iPreferNIST,iPreferNIST);
      SAVE_G_CFG_I(g_Settings.iDisableBernsteinCurve3617,iDisableBernsteinCurve3617);
      SAVE_G_CFG_I(g_Settings.iDisableBernsteinCurve25519,iDisableBernsteinCurve25519);

      SAVE_G_CFG_I(g_Settings.iHideCfg,iHideCfg);
      
      SAVE_G_CFG_I(g_Settings.iShowAxoErrorMessages,iShowAxoErrorMessages);
      SAVE_G_CFG_I(g_Settings.iShowMessageNotifications,iShowMessageNotifications);
      SAVE_G_CFG_I(g_Settings.iDontSendDeliveryNotifications,iDontSendDeliveryNotifications);
      
      SAVE_G_CFG_I(g_Settings.iBlockLocalDataRetention,iBlockLocalDataRetention);
      SAVE_G_CFG_I(g_Settings.iBlockRemoteDataRetention,iBlockRemoteDataRetention);
      
      SAVE_G_CFG_I(g_Settings.iDisableCallKit, iDisableCallKit);
      SAVE_G_CFG_I(g_Settings.iEnableNativeRingtone, iEnableNativeRingtone);
       
      SAVE_G_CFG_I(g_Settings.iEnableDialHelper,iEnableDialHelper);
      
      SAVE_G_CFG_I(g_Settings.iDontSimplifyVideoUI,iDontSimplifyVideoUI);
      SAVE_G_CFG_I(g_Settings.iDisplayUnsolicitedVideo,iDisplayUnsolicitedVideo);
      SAVE_G_CFG_I(g_Settings.iAudioUnderflow,iAudioUnderflow);
      SAVE_G_CFG_I(g_Settings.iShowGeekStrip,iShowGeekStrip);
      
      SAVE_G_CFG_I(g_Settings.iRetroRingtone,iRetroRingtone);

      
      SAVE_G_CFG_I(g_Settings.iForceFWTraversal,iForceFWTraversal);
      SAVE_G_CFG_I(g_Settings.iEnableFWTraversal,iEnableFWTraversal);
      
      
      SAVE_G_CFG_I(g_Settings.iSASConfirmClickCount,iSASConfirmClickCount);//TODO remove
      SAVE_G_CFG_I(g_Settings.iShowRXLed,iShowRXLed);
      SAVE_G_CFG_I(g_Settings.iKeepScreenOnIfBatOk,iKeepScreenOnIfBatOk);
      SAVE_G_CFG_I(g_Settings.iDisableSkeinHash, iDisableSkeinHash);
      
      l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szLastUsedAccount",g_Settings.szLastUsedAccount);
      
      l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szRingTone",g_Settings.szRingTone);

      l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szTextTone",g_Settings.szTextTone);

      if(!g_Settings.szDialingPrefCountry[0]){
         setDefaultDialingPref();
      }
      
      l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szDialingPrefCountry",g_Settings.szDialingPrefCountry);

      l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szRecentsMaxHistory",g_Settings.szRecentsMaxHistory);
      
      l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szMessageNotifcations",g_Settings.szMessageNotifcations);
      
       //DebugLogging
       SAVE_G_CFG_I(g_Settings.iEnableDebugLogging,iEnableDebugLogging);
       l+=t_snprintf(&dst[l],sizeof(dst)-1-l,"%s: %s\n","szDebugLoggingSetting",g_Settings.szDebugLoggingSetting);
      
       // Passcode
       SAVE_G_CFG_I(g_Settings.iPasscodeEnableWipe, iPasscodeEnableWipe);
       SAVE_G_CFG_I(g_Settings.iPasscodeEnableTouchID, iPasscodeEnableTouchID);
       l+=t_snprintf(&dst[l], sizeof(dst)-1-l, "%s: %s\n", "szPasscodeTimeout", g_Settings.szPasscodeTimeout);
       
      saveFileW(b.getText(),&dst[0],l);
      
      setFileBackgroundReadable(b);
   }
   
   ~CTG(){
      save();
   }
   
};
CTG gs;

static char szTextToneNames[1024] = "Default";

struct{
    const char *disp_name;
    const char *file_name;
}tableTT[]={
    {"Default","default"},
    {"Aurora","sms_alert_aurora"},
    {"Bamboo","sms_alert_bamboo"},
    {"Circles","sms_alert_circles"},
    {"Complete","sms_alert_complete"},
    {"Hello","sms_alert_hello"},
    {"Input","sms_alert_input"},
    {"Keys","sms_alert_keys"},
    {"Note","sms_alert_note"},
    {"Popcorn","sms_alert_popcorn"},
    {"Synth","sms_alert_synth"},
    {NULL,NULL}
};

void initTTList(){
    
    int l=0;
    
    for(int i=0;;i++) {
        
        if(!tableTT[i].disp_name)
            break;
        
        l += t_snprintf(&szTextToneNames[l], sizeof(szTextToneNames),"%s,",tableTT[i].disp_name);
    }
    
    if(l)
        szTextToneNames[l-1]=0;
    
    puts(szTextToneNames);
}

static char szRingToneNames[1024] = "Default";

struct{
   const char *disp_name;
   const char *file_name;
}tableRT[]={
   {"Default","ring"},
   {"Retro","ring_retro"},
   {"On Site","cisco"},
   {"Take a Memo","trimline"},
   {"The Victorian","european_major_third"},
   {"Touch Base","v120"},
   {"Bright Idea","piano_arpeg"},
   {"Coronation","fanfare"},
   {"Delta","jazz_sax_in_the_subway"},
   {"Intuition","dance_synth"},
   {"Seafarer's Call","foghorn"},
   {"Titania", "flute_with_echo"},
   {"Two Way Street","oboe"},
   {"WhisperZ","piccolo_flutter"},
   {"Whole In Time","whole_in_time"},
   {NULL,NULL}
};

void initRTList(){
   
   int l=0;
    
   for(int i=0;;i++) {
       
      if(!tableRT[i].disp_name)
          break;
       
      l += t_snprintf(&szRingToneNames[l], sizeof(szRingToneNames),"%s,",tableRT[i].disp_name);
   }
    
   if(l)
       szRingToneNames[l-1]=0;
    
   puts(szRingToneNames);
}

void t_save_glob(){
   gs.save();
}

void t_init_glob(){
   gs.init();
   initRTList();
   initTTList();
}

void setDefaultDialingPref(){
   const char *getPrefLang(void);
   const char *getCountryByID(const char *);
#ifdef __APPLE__
   const char *getSystemCountryCode(void);
   const char *lang = getSystemCountryCode();
#else
   const char *lang = getPrefLang();
#endif
   strcpy(g_Settings.szDialingPrefCountry, getCountryByID(lang));
   if(!g_Settings.szDialingPrefCountry[0]){
      strcpy(g_Settings.szDialingPrefCountry, "USA");
   }
}


void *findGlobalCfgKey(char *key, int iKeyLen, int &iSize, char **opt, int *type){
   
   if(key && key[0]=='*')return NULL;
   
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
#define GLOB_SZ_CHK_O(_K,_O) \
if(iKeyLen+1==sizeof(#_K) &&  t_isEqual(key,#_K,iKeyLen)){\
if(type)*type=PHONE_CFG::e_char;\
if(opt)*opt=(char *)_O;\
iSize=sizeof(g_Settings._K);\
return &g_Settings._K;\
}
   
   //
   GLOB_I_CHK(iExitingAndDoSaveNothingOnDisk);
   
   
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
   
   GLOB_I_CHK(iShowAxoErrorMessages);
   GLOB_I_CHK(iDontSendDeliveryNotifications);
   GLOB_I_CHK(iShowMessageNotifications);
   
   GLOB_I_CHK(iBlockLocalDataRetention);
   GLOB_I_CHK(iBlockRemoteDataRetention);
   
   GLOB_I_CHK(iDisableCallKit);
   GLOB_I_CHK(iEnableNativeRingtone);
    
   GLOB_I_CHK(iDontSimplifyVideoUI);
   GLOB_I_CHK(iDisplayUnsolicitedVideo);
   
   GLOB_I_CHK(iAudioUnderflow);
   GLOB_I_CHK(iShowGeekStrip);
   

   GLOB_I_CHK(iForceFWTraversal);
   GLOB_I_CHK(iEnableFWTraversal);
   
   GLOB_I_CHK(iRetroRingtone);
   
   GLOB_I_CHK(iPreferNIST);
   GLOB_I_CHK(iDisableSkeinHash);
   GLOB_I_CHK(iDisableBernsteinCurve3617);
   GLOB_I_CHK(iDisableBernsteinCurve25519);
   GLOB_I_CHK(iEnableDisclosure);

   GLOB_I_CHK(iSASConfirmClickCount);
   
   GLOB_I_CHK(iShowRXLed);
   GLOB_I_CHK(iKeepScreenOnIfBatOk);
   
   GLOB_I_CHK(iEnableDialHelper);
   
   GLOB_I_CHK(iEnableAirplay);

   GLOB_I_CHK(ai_volume);
   GLOB_I_CHK(ao_volume);
   
   GLOB_SZ_CHK(szLastUsedAccount);

   GLOB_SZ_CHK_O(szTextTone,&szTextToneNames[0]);

   GLOB_SZ_CHK_O(szRingTone,&szRingToneNames[0]);
   
   //TODO if (this is changing) fix recent list and save 
   GLOB_SZ_CHK_O(szRecentsMaxHistory, "5 minutes,1 hour,12 hours,1 day,1 week,1 month,1 year");
   GLOB_SZ_CHK_O(szMessageNotifcations, "Notification only,Sender only,Message and Sender");
  
    //DbugLogging:
    GLOB_I_CHK(iEnableDebugLogging);
    GLOB_SZ_CHK_O(szDebugLoggingSetting, "1 day,2 day,3 day");
   
    // Passcode
    GLOB_I_CHK(iPasscodeEnableWipe);
    GLOB_I_CHK(iPasscodeEnableTouchID);
    GLOB_SZ_CHK_O(szPasscodeTimeout, "Immediately,10 seconds,30 seconds,1 minute,2 minutes,5 minutes,15 minutes,30 minutes");
    
   /*
    5 minutes
    1 hour
    12 hours
    1 day
    1 week
    1 month
    1 year
    */
   
#ifndef _WIN32
   const char *getDialingPrefCountryList(void);
   
   static int once=0;
   if(!once && (g_Settings.szDialingPrefCountry[0]==0 ||
      g_Settings.szDialingPrefCountry[2]==0) &&
      strcmp(key, "szDialingPrefCountry")==0){
      once=1;
      setDefaultDialingPref();
   }

   GLOB_SZ_CHK_O(szDialingPrefCountry,getDialingPrefCountryList());
#endif
   
   GLOB_SZ_CHK(szPutOnHoldSound);
   GLOB_SZ_CHK(szOnHoldMusic);
   GLOB_SZ_CHK(szWelcomeSound);
   
   return NULL;
}
//TODO new file
class CTSounds: public CTDataBuf{
   int iTested;
public:
   CTSounds():CTDataBuf(){iTested=0;}
   void load(const char *fn){
      if(iTested)return;
      iTested=1;
      int _iLen;
#ifdef __APPLE__
      char *iosLoadFile(const char *fn, int &iLen );
      char *p=iosLoadFile(fn,_iLen);
      if(!p)p=loadFile(fn,_iLen);
      if(p){
         set(p,_iLen,1);
      }
      printf("sound loaded (%s res=%d len=%d)\n",fn,!!p,_iLen);
#else
     // char *p=loadFile(fn, <#int &iLen#>)
#endif
   }
};

CTSounds welcomeSnd;
CTSounds onHoldSnd;
CTSounds putOnHoldSnd;

static void loadSounds(){
   welcomeSnd.load(g_Settings.szWelcomeSound);
   onHoldSnd.load(g_Settings.szOnHoldMusic);
   putOnHoldSnd.load(g_Settings.szPutOnHoldSound);
}

CTDataBuf *g_getSound(const char *name){
   
   loadSounds();
   
   if(strcmp(name,"welcomeSnd")==0){return &welcomeSnd;}
   if(strcmp(name,"onHoldSnd")==0){ return &onHoldSnd;}
   if(strcmp(name,"putOnHoldSnd")==0){ return &putOnHoldSnd;}
 
   return 0;
}

int forceFWTraversal(){return g_Settings.iForceFWTraversal && g_Settings.iEnableFWTraversal;}

void *findGlobalCfgKey(const char *key){
   int iSize;
   char *opt;
   int type;
   return findGlobalCfgKey((char*)key,(int)strlen(key),iSize,&opt,&type);
}

const char * getTexttone(const char *p) {
    
    if(!p)
        p = (char*)findGlobalCfgKey("szTextTone");
    
    if(!p || !p[0])
        return tableTT[0].file_name;
    
    for(int i=0;;i++) {
        
        if(!tableTT[i].disp_name)
            break;
        
        if(strcmp(tableTT[i].disp_name, p) == 0)
            return tableTT[i].file_name;
    }
    
    return tableTT[0].file_name;
}

const char * getEmergencyRingtone(){return "emergency";}
const char * getRingtone(const char *p) {
    
   if(!p)
       p = (char*)findGlobalCfgKey("szRingTone");
   
   if(!p || !p[0])
       return tableRT[0].file_name;
   
   for(int i=0;;i++) {
       
      if(!tableRT[i].disp_name)
          break;
       
      if(strcmp(tableRT[i].disp_name, p)==0)
          return tableRT[i].file_name;
   }
   
   return tableRT[0].file_name;
}


void checkGlobalChange(void *pVariable){
   if(pVariable==(void*)&g_Settings.iEnableFWTraversal){
      
      void enableSIPTLS443forSC(int iEnable);
      
      enableSIPTLS443forSC(g_Settings.iEnableFWTraversal);
      
   }
   else if(pVariable==(void*)&g_Settings.ai_volume){
      setAudioInputVolume(g_Settings.ai_volume);
      
   }
   else if(pVariable==(void*)&g_Settings.ao_volume){
      setAudioOutputVolume(g_Settings.ao_volume);
   }
}


int setGlobalValueByKey(const char *key, int iKeyLen, char *sz){

   int iSize;
   char *opt;
   int type;
   char *p = (char*)findGlobalCfgKey((char*)key,iKeyLen,iSize,&opt,&type);
   
   if(!p || iSize<1)return -1;
   
   if(type==PHONE_CFG::e_char){
      strncpy(p,sz,iSize);
      p[iSize-1]=0;
   }
   else{
      *(int*)p=atoi(sz);

      checkGlobalChange(p);
   }
   
   
   return 1;
}

int setGlobalValueByKey(const char *key,  char *sz){
   return setGlobalValueByKey(key,(int)strlen(key),sz);
}

int hasActiveCalls();

void checkGlobalSettings(void *g_zrtp){
   
   if(g_Settings.iClearZRTPCaches && !hasActiveCalls()){
      g_Settings.iClearZRTPCaches=0;
      
      void clearZrtpCachesG(void *pZrtpGlobals);
      clearZrtpCachesG(g_zrtp);
      
      log_events( __FUNCTION__, "caches cleared");
   }
}
