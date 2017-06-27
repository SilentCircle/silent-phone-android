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
#ifndef _ENG_CB_H
#define _ENG_CB_H

class CT_cb_msg{
public:
   
#define T_CB_ENUM eReg,eError,eRinging, eSIPMsg, eCalling,eIncomCall,eNewMedia,eEndCall,eZRTPMsgA,eZRTPMsgV,eZRTP_sas,eZRTPErrA,eZRTPErrV,eZRTPWarn,eStartCall,eEnrroll,eZRTP_peer,eZRTP_peer_not_verifed,eMsg
   
#define T_CB_ENUM_STR  "eReg,eError,eRinging, eSIPMsg, eCalling,eIncomCall,eNewMedia,eEndCall,eZRTPMsgA,eZRTPMsgV,eZRTP_sas,eZRTPErrA,eZRTPErrV,eZRTPWarn,eStartCall,eEnrroll,eZRTP_peer,eZRTP_peer_not_verifed,eMsg"
   
   enum{T_CB_ENUM};
   
   static const char *toString(int msg){
      if(msg<0 || msg>=eMsg)return "Warn: Unknown msg";
      
      static char p[] = T_CB_ENUM_STR;
      char *tmp = p;
      static int iInit=1;
      if(iInit){
         iInit=0;
         while(*tmp){if(*tmp==',')*tmp=0; tmp++; }
         tmp = p;//restore
      }
      
      while(msg){if(tmp[0]==0)msg--;tmp++;}
      
      return tmp;
   }
};

#define T_EXP //
// __declspec( dllexport )

typedef int (fnc_cb_ph)(void *ret, void *ph, int iCallID, int msgid, const char *psz, int iSZLen);
void setPhoneCB(fnc_cb_ph *fnc, void *pRet);


int z_main_init(int argc=0, const  char* argv[]=0);
char* z_main(int iResp, int argc, const char* argv[]);;

const char* sendEngMsg(void *pEng, const char *p);
int getCallInfo(int iCallID, const char *key, char *p, int iMax);

//doCmd(":c number|username");
//doCmd("*a123");//answer callid=123
//doCmd("*e123");//end callid=123
//doCmd(":mute 1");//mute
//doCmd(":mute 0");//unmute
//doCmd(":reg");//go online
/*
 case '+':g_conf.addCall(ses);return 0;
 case '-':g_conf.remCall(ses);return 0;
 case 'a':CTEntropyCollector::onNewCall(); ph->answerCall(cid);return 0;
 case 'e':ph->endCall(cid);return 0;
 case 'h':ph->hold(1,cid);return 0;
 case 'u':ph->hold(0,cid);return 0;
 
 case 'r':return ringSecondary(ses);//call this one call is active
 
 case 'c':return ph->reInvite(cid,"audio");
 case 'C':return ph->reInvite(cid,"video");
 
 doCmd("*z123 zrtp_peername");
 case 'V': set zrtp verify flag
 case 'V': remove zrtp verify flag
 */

int doCmd(const char *p, void *pEng=NULL);
int doCmd(const char *cmd, int iCallID, void *pEng=NULL);

int isProvisioned(int iCheckNow);
int checkProv(const char *pUserCode, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet);
int checkProvUserPass(const char *pUN, const char *pPWD, void(*cb)(void *p, int ok, const char *pMsg), void *cbRet);


void initCC(char *p, int iLen);//set country code file name
int fixNR(const char *in, char *out, int iLenMax); //format number 12223334444->1(222) 333-4444

void setAudioRecordingDeviceName(const char *pname);//windows only
void setAudioPlaybackDeviceName(const char *pname);//windows only

void stopRingTone();
void* playDefaultRingTone(int iIsInBack);

void setAudioRouteChangeCB(void(*fncCBOnRouteChange)(void *self, void *ptrUserData), void *ptrUserData);//iOS only

void* getCurrentDOut();//get current dial out engine
void *getAccountByID(int idx);//doCmd(":c echotest", getAccountByID(0));
int getPhoneState();//return 2 if online, 1 if connecting, 0 if not online

//const char *p = sendEngMsg( getAccountByID(0), "cfg.un");//return username
//const char *p = sendEngMsg( getAccountByID(0), "cfg.nr");//returns DID (number)
//const char *p = sendEngMsg( getAccountByID(0), "isON");//returns yes,no,connecting
void t_onEndApp();

const char* getCurrentProvSrv(); //returns current provisioning server
int getPathW(short *p, int iMaxLen); // engine function for getting path in user's home directory

#endif
