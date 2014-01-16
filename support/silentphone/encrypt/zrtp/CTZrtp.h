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

#ifndef _C_T_ZRTP_H
#define _C_T_ZRTP_H


//#include "CTZrtpV.h"//comment uncoment this, enable disable Victor libzrtp

#ifndef _C_T_ZRTP_V_H

#include <CtZrtpCallback.h>


class CTZRTP;
class CTZrtpCb{
public:
   virtual void onNewZrtpStatus(CTZRTP *zrtp, char *p, int iIsVideo)=0;
   virtual void onNeedEnroll(CTZRTP *zrtp){}
   virtual void onPeer(CTZRTP *zrtp, char *name, int iIsVerified){}
   virtual void onZrtpWarning(CTZRTP *zrtp, char *p, int iIsVideo)=0;
};

void safeStrCpy(char *dst, const char *name, int iMaxSize);

int t_has_zrtp();//{return 1;}
void add_tz_random(void *p, int iLen);
void *initZrtpG();
int relZrtpG(void *pZrtpGlobals);

class CTZRTP: public CtZrtpSession , public CtZrtpCb{
   void *pZrtpGlobals;
   int iStatus[2];

   void *pCtx;
   char bufSAS[32];
   
   int iWasZRTPSecure;
   
   char sdesStrings[2][128];
   
   int iDisabledSent[2];
   
   void reset();
   
public:
   virtual void onNewZrtpStatus(CtZrtpSession *session, char *p, CtZrtpSession::streamName streamNm){

      int vid=isVideo(streamNm);
      iIsStarted[vid]=1;
      int s=getStatus(0);
      printf("getStatus()=[%d] [%s] %d ",s,getZRTP_msg(s),isSecure(0));
     // if(p)
      if(0)
      {
         FILE *f=fopen("test_zrtp_a.txt","a+");
         if(f){
            fprintf(f,"getStatus()=[%d] [%s] %d p=[%s]\n",s,getZRTP_msg(s),isSecure(0),p);
            fclose(f);
         }
      }
      
      if(!vid && isSecure(0) && p){strncpy(&bufSAS[0],p, sizeof(bufSAS)-1);bufSAS[sizeof(bufSAS)-1]=0; p=&bufSAS[0];iWasZRTPSecure=1;}
      
      zrtpcb->onNewZrtpStatus(this, p, streamNm!=CtZrtpSession::AudioStream);
   }
   virtual void onNeedEnroll(CtZrtpSession *session, CtZrtpSession::streamName streamNm, int32_t info) {
      /*
       enum InfoEnrollment {
       EnrollmentRequest = 0,          //!< Aks user to confirm or deny an Enrollemnt request
       EnrollmentReconfirm,            //!< User already enrolled, ask re-confirmation
       EnrollmentCanceled,             //!< User did not confirm the PBX enrollement
       EnrollmentFailed,               //!< Enrollment process failed, no PBX secret available
       EnrollmentOk                    //!< Enrollment process for this PBX was ok
       };
       */
 //  virtual void onNeedEnroll(CtZrtpSession *session, CtZrtpSession::streamName streamNm){
      if(info==0)// || info==1)
        zrtpcb->onNeedEnroll(this);
   }
   
   virtual void onPeer(CtZrtpSession *session, char *name, int iIsVerified, CtZrtpSession::streamName streamNm){
      
      if(iIsVerified)iCachesOk=1;else iCachesOk=0;
      safeStrCpy(&bufPeer[0],name,sizeof(bufPeer)-1);
      zrtpcb->onPeer(this, name, iIsVerified);//, streamNm!=CtZrtpSession::AudioStream);
   }
   virtual void onZrtpWarning(CtZrtpSession *session, char *p, CtZrtpSession::streamName streamNm){
      
      iWarnDetected=1;
      safeStrCpy(&bufWarning[0],p,sizeof(bufWarning)-1);
      zrtpcb->onZrtpWarning(this, p, streamNm!=CtZrtpSession::AudioStream);
   }
   
   CTZrtpCb *zrtpcb;
   
   int iAuthFailCnt;
   
   int uiZRTPStartTime;
   int iZRTPNegSpeed;
   
   
   void *getZRTP_glob(){return pZrtpGlobals;}
   enum {ePacketOk=0,ePacketError=-10000,eDropPacket,eAuthFailPacket,eIsProtocol};
   
   void start(unsigned int uiSSRC, streamName streamNm);
   
   int iCachesOk;
   int iSoundPlayed;
   
   int iFailPlayed;
   
   int iWarnDetected;
   
   
   char bufPeer[128];
   char bufWarning[512];
   
   char bufSecurePBXMsg[64];
   
   int iCanUseZRTP;
   void *pRet[2];//Victors zrtp 
   void *pSes;
   
   int iIsStarted[2];
   
   static int clearCaches();
   
   CTZRTP(void *pZrtpGlobalsN);
   ~CTZRTP(); 
   int setDstHash(char *p, int iLen, int iIsVideo);
   int getSignalingHelloHash(char *helloHash, int iIsVideo, int index);
   
   bool t_createSdes(char *cryptoString, size_t *maxLen, streamName streamNm);//will save SDES string
   void clearSdesString();//must be called when peer SDP is received
   
   void release_zrtp();
   int init_zrtp(int iCaller, char *zid_base16, int iInitVideoHash, int iInitAudioHash);
   
   int encrypt(char *p, int &iLen, int iIsVideo);
   int decrypt(char *p, int &iLen, int iIsVideo);
   
   inline int isVideo(CtZrtpSession::streamName streamNm){return streamNm == CtZrtpSession::VideoStream;}

   int isSecure(int iIsVideo);
   
   int getStatus(int iIsVideo);
   
   int getInfoX(const char *key, char *p, int iMax, int iIsVideo=0);
   
   void enrollAccepted(const char *mitm_name);
   
   const char *getZRTP_msg(int s);
   
};

inline const char *CTZRTP::getZRTP_msg(int s){
   const char *msg="";
   switch(s){
      case CTZRTP::eLookingPeer:msg="Looking for peer";break;
      case CTZRTP::eNoPeer:msg="Not SECURE";break;
      case CTZRTP::eGoingSecure:msg="Going secure";break;
      case CTZRTP::eSecure:msg="SECURE";break;//end-to-end
      case CTZRTP::eError:msg="ZRTP Error";break;
      case CTZRTP::eSecureMitm:
         if(!bufSecurePBXMsg[0])msg="SECURE between you and server";
         else msg=&bufSecurePBXMsg[0];
         break;
         
      case CTZRTP::eSecureMitmVia:msg="SECURE via PBX";break;
      case CTZRTP::eSecureSdes:msg="SECURE SDES";break;
      case CTZRTP::eSecurityDisabled:msg="Not SECURE no crypto enabled";break;
   };
   return msg;
}

//_T_USE_ZRTP
#endif

#endif
