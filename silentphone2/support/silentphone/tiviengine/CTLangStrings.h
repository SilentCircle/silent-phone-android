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

#ifdef T_LOAD_STRINGS
#undef _C_T_LANG_STRINGS_H
#undef T_LABEL
#endif
#ifndef _C_T_LANG_STRINGS_H
#define _C_T_LANG_STRINGS_H

#ifndef T_LOAD_STRINGS

#include "../baseclasses/CTListBase.h"
#include "../baseclasses/CTIndexList.h"
#include "../baseclasses/CTEditBase.h"

const char *tg_translate(const char *key, int iKeyLen=0);

unsigned int crc32_calc_text(const void *ptr, size_t cnt);

class CListItemLangVal: public CListItem{
public:
   CTEditBuf<512> e;
   char szUtf8[1024];//utf8
   char bufItemN[512];//key
   int iBufNLen;
   CListItemLangVal(const char *name, int iNameLen):CListItem(0){
    //  strcpy(bufItemN,name);
      iBufNLen=0;

      for(iBufNLen=0;*name && iBufNLen<iNameLen && iBufNLen<sizeof(bufItemN);iBufNLen++)
      {

         bufItemN[iBufNLen]=*name;
         name++;
      }
      //while(*name){iBufNLen++;name++;}
      uiCrc = getCRC(&bufItemN[0],iBufNLen);

      szUtf8[0]=0;
      bufItemN[iBufNLen]=0;
   }
   static unsigned int getCRC(const void *p, int iLen){
      unsigned int r = crc32_calc_text(p, iLen);
      if(!r)r=1;
      return r;
   }
   
   void setText(CTStrBase &val){
      e.setText(val);
      szUtf8[0]=0;
   }
   
   virtual int isItem(void *p, int iSize){
      int theSameCharSeq(const char * src, const char * dst, int iLen);
      int r = theSameCharSeq((const char*)p,&bufItemN[0],iSize);
      if(r && szUtf8[0]==0){
         getText(szUtf8, 511, &e);
      }
      return r;
   }
   
};



class CTLangStrings : public CTStringsBase{
   
   //10 is ok we would have 1024 lists
   CTIndexList<10> list; //warning: 1<<n would use lot of memmory,
   
public:
   CTLangStrings():list(){iZRTPInitOk=0;list.crcFnc = &CListItemLangVal::getCRC;}
   ~CTLangStrings(){}
   
   void clearList(){list.removeAll();}


   int loadLang(CTEditBase *langFile);
   int loadLang(char *p, int iLen);
   
   void initZRTPMsgs();
   int iZRTPInitOk;
   
   void constrEng();
   void constrLatv();
   void constrJap();
   void constrItalian();
   
   CListItemLangVal *findByKey(const char *key, int iKeyLen=0){
      if(!iKeyLen)iKeyLen = strlen(key);
      return (CListItemLangVal*)list.findByKey(key,  iKeyLen);
   }
   
   const char *findByKeyUtf8(const char *key, int iKeyLen=0){
      CListItemLangVal *it =  findByKey(key,  iKeyLen);
     // printf("[f=%s res=%p %s]",key, it,it?&it->szUtf8[0]:"");
      if(!it)return key;
      return &it->szUtf8[0];
   }
   
   void addToList(CTEditBase &val, const char *id, int idLen){
      CListItemLangVal *it = findByKey(id,  idLen);
      if(it) list.remove(it,0); //remove and dont  delete - reuse
      if(!it)it = new CListItemLangVal(id,  idLen);
      it->setText(val);
      list.add(it);
   }
   private:
   void tryToTranslate(CTEditBase &e){//this should be called only once after loading a lang file
      char b[256];
      int iLen=255;
      e.getTextUtf8(b, &iLen);
      const char *tr = findByKeyUtf8(b, iLen);//use value as key
      if(tr==&b[0] || !tr)return;//can not find
      e.setText(tr);//set translated text
      printf("[translate------%s->%s]\n",b, tr);
   }
   public:

   
/*
#define T_LABEL(_A) 
T_LABEL(lApiShortName);

void setTextById(char *id, int idLen, CTEditBase &val){
   do{
      if(idLen+1==sizeof(_A) && strncmp(id,#_A,idLen)==0) { _A.setText(val);break;}
   }while(0);
}
*/
   int setGetTextById(const char *id, int idLen, CTEditBase &val, int iSet){
      return setGetTextById(id, idLen, val, iSet, 0);
   }
   
   void translateRes(){
      CTEditBuf<16> b;
      setGetTextById(NULL, 0, b, 0,1);//translate resources
   }

   #define T_LABEL(_A) CTEditBase _A
   int setGetTextById(const char *id, int idLen, CTEditBase &val, int iSet,int iTranslateAll);
#else

#define T_LABEL(_A) {if(iTranslateAll){tryToTranslate(_A);ret=1;}\
   else if(idLen+1==sizeof(#_A) && strncmp(id,#_A,idLen)==0) {if(iSet){ _A.setText(val);addToList(val, #_A, idLen);}else val.setText(_A);ret=1;break;}}

int CTLangStrings::setGetTextById(const char *id, int idLen, CTEditBase &val, int iSet, int iTranslateAll){
   int ret=0;
   do{
#endif

   T_LABEL(lRestartLang);
   T_LABEL(lApiShortName);
   T_LABEL(lLogin);
   T_LABEL(lLogout);
   T_LABEL(lAbout);
   T_LABEL(lAboutText);
   T_LABEL(lExit);
   T_LABEL(lExitText);

   T_LABEL(lInvPhNr);
   T_LABEL(lAllSesBusy);
   T_LABEL(lError);
   T_LABEL(lConTimeOut);
   T_LABEL(lCannotDeliv);
   T_LABEL(lCannotReg);
   T_LABEL(lCannotCon);
      
   T_LABEL(lCouldNotReachServer);
   T_LABEL(lRemoteOutOfReach);
      
      
   T_LABEL(lReason);
   T_LABEL(lNoConn);
   T_LABEL(lRegist);
   T_LABEL(lRegSucc);
   T_LABEL(lCalling);
   T_LABEL(lCallEnded);
   T_LABEL(lMissCall);
   T_LABEL(lIncomCall);
   T_LABEL(lMyUN);
   T_LABEL(lMyPwd);
   T_LABEL(lMyPhNr);
   T_LABEL(lFind);
   T_LABEL(lConnecting);
   T_LABEL(lConfig);
   T_LABEL(lPhoneBook);
   T_LABEL(lAdd);
   T_LABEL(lEdit);
   T_LABEL(lRemove);
   T_LABEL(lDialledNumbers);
   T_LABEL(lReceivedCalls);
   T_LABEL(lMissedCalls);
   T_LABEL(lToEnterCfgLogout);
   T_LABEL(lDeleteEntryFromList);
   T_LABEL(lEnterUN_PWD);
   T_LABEL(lRunAtStartup);
   T_LABEL(lUsingAP);

   T_LABEL(lVideoCall);

   T_LABEL(lCall);
   T_LABEL(lHangUp);
   T_LABEL(lEnterNumberChatWith);

   T_LABEL(lOptions);
   T_LABEL(lOk);
   T_LABEL(lCancel);
   T_LABEL(lChat);
   T_LABEL(lNetworkConfiguration);
   T_LABEL(lDefault);
   T_LABEL(lSoundAlertMsg);
   T_LABEL(lShowTSFrontMsg);
   T_LABEL(lOutputSpeakersHead);
   T_LABEL(lInputMicHead);
   T_LABEL(lNRingsDings);
   T_LABEL(lAudio);
   T_LABEL(lCalls);
   T_LABEL(lEnterNumberHere);
   T_LABEL(lSend);
   T_LABEL(lMyScreenName);
   T_LABEL(lDoUWantSelectNewAp);
   T_LABEL(lDontShowMsgAgain);
   T_LABEL(lForYourInfo);
   T_LABEL(lNotifyMicCameraUsage);
   T_LABEL(lBillableEvent);
   T_LABEL(lAllowApiConnect);
   T_LABEL(lNoBusKey);
   T_LABEL(lNoEcoKey);
   T_LABEL(lKeyValid);
   T_LABEL(lKeyInvalid);
   
   T_LABEL(lKeyInvalidUnlimited);
   T_LABEL(lKeyInvalidActive);
   
      T_LABEL(zrtp_common_cmp);
      T_LABEL(zrtp_common_cls);
      T_LABEL(zrtp_common_upd);
      T_LABEL(zrtp_common_app);
      
      T_LABEL(zrtp_s2_c004_explanation);
      T_LABEL(zrtp_s2_c005_explanation);
      T_LABEL(zrtp_s2_c006_explanation);
      T_LABEL(zrtp_s2_c007_explanation);
      T_LABEL(zrtp_s2_c008_explanation);
      T_LABEL(zrtp_s2_c050_explanation);
      
      T_LABEL(zrtp_s3_c001_explanation);
      T_LABEL(zrtp_s3_c002_explanation);
      T_LABEL(zrtp_s3_c003_explanation);
      T_LABEL(zrtp_s3_c004_explanation);
      T_LABEL(zrtp_s3_c005_explanation);
      T_LABEL(zrtp_s3_c006_explanation);
      T_LABEL(zrtp_s3_c007_explanation);
      T_LABEL(zrtp_s3_c008_explanation);
      
      T_LABEL(zrtp_s4_c016_explanation);
      T_LABEL(zrtp_s4_c020_explanation);
      T_LABEL(zrtp_s4_c048_explanation);
      T_LABEL(zrtp_s4_c081_explanation);
      T_LABEL(zrtp_s4_c082_explanation);
      T_LABEL(zrtp_s4_c083_explanation);
      T_LABEL(zrtp_s4_c084_explanation);
      T_LABEL(zrtp_s4_c085_explanation);
      T_LABEL(zrtp_s4_c097_explanation);
      T_LABEL(zrtp_s4_c098_explanation);
      T_LABEL(zrtp_s4_c099_explanation);
      T_LABEL(zrtp_s4_c112_explanation);
      T_LABEL(zrtp_s4_c144_explanation);
      


#ifdef T_LOAD_STRINGS
      }while(0);
   if(ret==0 && iSet){
      addToList(val, id,  idLen);
      ret=1;
   }
   else if(ret==0 && !iSet){
      CListItemLangVal *it = findByKey(id,  idLen);
      if(it){
         val.setText(it->e);
         return 1;
      }
   }
   return ret;
   }//end fnc
#else

};

#endif 
   //T_LOAD_STRINGS
#endif 
   //_C_T_LANG_STRINGS_H
