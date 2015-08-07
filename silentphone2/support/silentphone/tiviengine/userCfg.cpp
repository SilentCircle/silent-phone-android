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

#if  1

#ifdef _WIN32
#define snprintf _snprintf
#endif

void tivi_log1(const char *p, int val);

#include <stdlib.h>

#include "../baseclasses/CTEditBase.h"
#include "../tiviengine/main.h"
#include "../xml/parse_xml.h"
#include "../utils/utils.h"



#define T_GET_CFG_FNC
#undef _T_CFG_H
#include "t_cfg.h"

int getPath(char *p, int iMaxLen);
int getPathW(short *p, int iMaxLen);
int getCfgO(PHONE_CFG *cfg);
int checkFirstPart3(PHONE_CFG *cfg);

char *loadFileW(const  short *fn, int &iLen);

int fillInts(char *p, int *i, int iMaxInts);
int hasThisInt(int *il, int iCnt, int id);


int decodePwd(char *szSrc,char *szPwd, int iMaxLen);
void  encryptPwd(const char *szPwd,char *szDst ,int iMaxLen);

int decryptPWD(const char *hexIn, int iLen, char *outPwd, int iMaxOut, int iIndex);
int encryptPWD(const char *pwd, int iLen, char *hexOut, int iMaxOut, int iIndex);

int isAESKeySet();


int mustUseZRTP();
int mustUseSDES();
int mustUseTLS();


#define CFG_F_FALSE  0
#define CFG_F_TRUE   1
#define CFG_F_USER   2
#define CFG_F_SIP    4
#define CFG_F_PHONE  8
#define CFG_F_AUDIO  16
#define CFG_F_AUTO   32
#define CFG_F_SDP    64
#define CFG_F_CODEC  128
#define CFG_F_MELODY  256
#define CFG_F_SNDDEV  512
#define CFG_F_VOLUME  1024
#define CFG_F_REG    2048
#define CFG_F_CHAT    (2048*2)
#define CFG_F_VIDEO    (2048*4)
#define CFG_F_CODECS    (2048*8)
#define CFG_F_GUI    (2048*16)
#define CFG_F_ZRTP    (2048*32)

#ifndef TRUE
#define TRUE 1
#define FALSE 0
#endif

#define UINT unsigned int
#define BOOL int


#define MAX_UN_PWD_LEN 32
void tivi_log(const char* format, ...);

static int _fillCfg(char *dest, int iMaxSize, STR_XML *src)
{
   char s[256];
   if (src->len < sizeof(s) && src->len < iMaxSize)
   {
      memcpy(s, src->s, src->len);
      s[src->len]=0;
      trim(s);
      strncpy(dest, s, iMaxSize);
   }
   return 0;
}

//dest is char always array
#define fillCfg(dest, src) _fillCfg(dest, sizeof(dest), src)

void FindXMLVal(NODE *node, int level, int cfgFlag, PHONE_CFG &cfg)
{
   NODE *tmpNode;
   tmpNode = node;
   if CMP_XML(tmpNode->name,"CFG",3) cfgFlag|=CFG_F_TRUE;
   // brothers
   while(tmpNode){
      // opentag

      int tmpFlag=cfgFlag;
      if CMP_XML(tmpNode->name,"USER",4)  cfgFlag|=CFG_F_USER;else
      if CMP_XML(tmpNode->name,"SIP",3)   cfgFlag|=CFG_F_SIP;else
      if CMP_XML(tmpNode->name,"PHONE",5) cfgFlag|=CFG_F_PHONE;else
      if CMP_XML(tmpNode->name,"AUDIO",5) cfgFlag|=CFG_F_AUDIO;else
      if CMP_XML(tmpNode->name,"CODECS",6)cfgFlag|=CFG_F_CODECS;else
      if CMP_XML(tmpNode->name,"VIDEO",5) cfgFlag|=CFG_F_VIDEO;else
      if CMP_XML(tmpNode->name,"AUTO",4)  cfgFlag|=CFG_F_AUTO;else
      if CMP_XML(tmpNode->name,"SNDDEV",6)cfgFlag|=CFG_F_SNDDEV;else
      if CMP_XML(tmpNode->name,"VOLUME",6)cfgFlag|=CFG_F_VOLUME;else
      if CMP_XML(tmpNode->name,"REGISTRAR",9)cfgFlag|=CFG_F_REG;else
      if CMP_XML(tmpNode->name,"CHAT",4)  cfgFlag|=CFG_F_CHAT;else
      if CMP_XML(tmpNode->name,"ZRTP",4)  cfgFlag|=CFG_F_ZRTP;else
      if CMP_XML(tmpNode->name,"GUI",3)   cfgFlag|=CFG_F_GUI;else
      if CMP_XML(tmpNode->name,"MELODY",6)cfgFlag|=CFG_F_MELODY;else
      if CMP_XML(tmpNode->name,"SDP",3)   cfgFlag|=CFG_F_SDP;
      if ((cfgFlag==(CFG_F_SDP|CFG_F_AUDIO|CFG_F_TRUE))&&CMP_XML(tmpNode->name,"CODEC",5))
         cfgFlag|=CFG_F_CODEC;


      if (tmpNode->nV){
         nameValue *tmpNV;

         tmpNV=tmpNode->nV;
         // atributes
         while(tmpNV)
         {

            switch (cfgFlag)
            {
            case CFG_F_TRUE:
               if (CMP_XML(tmpNV->name,"VERS",4))
               {  
                  cfg.iCfgVers=atoi(tmpNV->value.s);
               }
               else if (CMP_XML(tmpNV->name,"DISABLED",8))
               {
                     cfg.iAccountIsDisabled=atoi(tmpNV->value.s);
               }
               else if (CMP_XML(tmpNV->name,"CREATEDBYUSER",13))
               {
                  cfg.bCreatedByUser=atoi(tmpNV->value.s);
               }
               break;
            case CFG_F_USER|CFG_F_TRUE:
               if (CMP_XML(tmpNV->name,"LOGINNAME",9))
                  fillCfg(cfg.user.un,&tmpNV->value);
               else if(CMP_XML(tmpNV->name,"PWDP",4))
               {
                  cfg.iPlainPasswordDetected=1;
                  fillCfg(cfg.user.pwd,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"PWD",3))
               {
                  cfg.iPlainPasswordDetected=1;
                  decodePwd(tmpNV->value.s,cfg.user.pwd, sizeof(cfg.user.pwd));
               }
               else if (CMP_XML(tmpNV->name,"PWDA",4))
               {
                  //int decryptPWD(const char *hexIn, int iLen, char *outPwd, int iMaxOut, int iIndex);
                  
                  decryptPWD(tmpNV->value.s,tmpNV->value.len, cfg.user.pwd, sizeof(cfg.user.pwd), cfg.iIndex);
               }
               else
               if (CMP_XML(tmpNV->name,"FROMNAME",8))
                  fillCfg(cfg.user.nick,&tmpNV->value);else
               if (CMP_XML(tmpNV->name,"PHONENR",7))
                  fillCfg(cfg.user.nr,&tmpNV->value);
               else if (CMP_XML(tmpNV->name,"SAVEPWD",7))
                  cfg.iDontSavePwd=!atoi(tmpNV->value.s);
               else if (CMP_XML(tmpNV->name,"COUNTRY",7))
               {
                  fillCfg(cfg.user.country,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"PARTNERID",9))
               {
                  fillCfg(cfg.partnerId,&tmpNV->value);
               }
               break;
               
            case CFG_F_SNDDEV|CFG_F_TRUE:

               if (CMP_XML(tmpNV->name,"CAPTURE",7))
               {  
                  fillCfg(cfg.aMicCfg.szName,&tmpNV->value);
               }else
               if (CMP_XML(tmpNV->name,"PLAYBACK",8))
               {  
                  fillCfg(cfg.aPlayCfg.szName,&tmpNV->value);
               }
               if (CMP_XML(tmpNV->name,"RING",4))
               {  
                  fillCfg(cfg.aRingCfg.szName,&tmpNV->value);
               }
               break;
            case CFG_F_SNDDEV|CFG_F_TRUE|CFG_F_VOLUME:
               if (CMP_XML(tmpNV->name,"INPUT",5))
               {  
                  cfg.aMicCfg.iVolume=atoi(tmpNV->value.s);

               }else if (CMP_XML(tmpNV->name,"OUTPUT",6))
               {  
                  cfg.aPlayCfg.iVolume=atoi(tmpNV->value.s);
               }
               else if (CMP_XML(tmpNV->name,"RING",4))
               {  
                  cfg.aRingCfg.iVolume=atoi(tmpNV->value.s);
               }
               break;
            case CFG_F_SIP|CFG_F_TRUE:
               if (CMP_XML(tmpNV->name,"SIPKA",5))
               {
                     cfg.iSipKeepAlive=(int)strtoul(tmpNV->value.s,NULL,0);
               }
               else if (CMP_XML(tmpNV->name,"SIPPORT",7))
               {
                  cfg.iSipPortToBind=(int)strtoul(tmpNV->value.s,NULL,0);
               }               
               break;

            case CFG_F_SIP|CFG_F_REG|CFG_F_TRUE:

               if(CMP_XML(tmpNV->name,"TITLE",5)){
                  fillCfg(cfg.szTitle,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"PXIFNAT",7))
               {
                  fillCfg(cfg.bufpxifnat,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"STUN",4))
               {
                  fillCfg(cfg.bufStun,&tmpNV->value);

               }
               else if (CMP_XML(tmpNV->name,"USESTUN",7))
               {
                  cfg.iUseStun=tmpNV->value.s[0]!='0';

               }
               else if(CMP_XML(tmpNV->name,"SIPTRANSPORT",12)){
                  
                  fillCfg(cfg.szSipTransport,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"SHOWLOCALIPONLY",15))
               {
                  cfg.iUseOnlyNatIp =(CMP_XML(tmpNV->value,"TRUE",4));
               } 
               
               else if (CMP_XML(tmpNV->name,"LKEY",4))
               {
                  fillCfg(cfg.szLicenceKey,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"SERVER",6))
               {
                  if(cfg.iSkipXMLCfgServAddr)cfg.tmpServ[0]=0;
                  else fillCfg(cfg.tmpServ,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"EXPIRES",7))
               {
                  cfg.uiExpires=strtoul(tmpNV->value.s,NULL,0);
               }
               else if (CMP_XML(tmpNV->name,"AUTO",4))
               {
                  if (CMP_XML(tmpNV->value,"ON",2))
                     cfg.iAutoRegister=1;
                  else
                     cfg.iAutoRegister=0;
               }
               break;
            case CFG_F_PHONE|CFG_F_TRUE:
               if (CMP_XML(tmpNV->name,"AUTOANSWER",10))
               {
                  if (CMP_XML(tmpNV->value,"TRUE",4))
                     cfg.iAutoAnswer=TRUE;
               }
               else  if (CMP_XML(tmpNV->name,"AUTORUN",7))
               {
                  if (CMP_XML(tmpNV->value,"FALSE",5))
                     cfg.iAutoStart=FALSE;
                  else 
                     cfg.iAutoStart=TRUE;

               }
               else if (CMP_XML(tmpNV->name,"LANG",4))
               {
                  fillCfg(cfg.szLangFN,&tmpNV->value);

               }
               else if(CMP_XML(tmpNV->name,"WFLAG",5))
               {
                  int ff=strtoul(tmpNV->value.s,NULL,0);
                  cfg.iShowDiscl=ff&4;
                  cfg.iWarnCamMicUsage=ff&2;
                  cfg.iWarnNetworkUsage=ff&1;
               }
               else if(CMP_XML(tmpNV->name,"APPRIO",6)){
                  cfg.iAPPrio=strtoul(tmpNV->value.s,NULL,0);
               }
               else if(CMP_XML(tmpNV->name,"NODIALERHELPER",14)){//nodialerhelper
                  cfg.iDisableDialingHelper=strtoul(tmpNV->value.s,NULL,0);
               }

               break;

            case CFG_F_PHONE|CFG_F_TRUE|CFG_F_GUI:
               if (CMP_XML(tmpNV->name,"AUTOSHOW",8))
               {
                  if (CMP_XML(tmpNV->value,"FALSE",5))
                     cfg.iAutoShowApi=0;
                  else
                     cfg.iAutoShowApi=1;
                  cfg.iHasAutoShowInCfg=1;

               }
               break;
            case CFG_F_PHONE|CFG_F_TRUE|CFG_F_CHAT:
               if (CMP_XML(tmpNV->name,"ALERTONMSG",10))
               {
                  cfg.iBeepAtMsg=tmpNV->value.s[0]-'0';
               }
               else if (CMP_XML(tmpNV->name,"SHOWTIME",8))
               {
                  cfg.iInsertTimeInMsg=tmpNV->value.s[0]-'0';
               }
               else if (CMP_XML(tmpNV->name,"MSGBELOWNICK",12))
               {
                  //sUserCfg.msgCfg.iMsgBelowNick=tmpNV->value.s[0]-0x30;
               }
               else if (CMP_XML(tmpNV->name,"INMSGCOL",8))
               {
                  //sUserCfg.msgCfg.uiInMsgCol=parseColor(tmpNV->value.s,tmpNV->value.len);
               }
               else if (CMP_XML(tmpNV->name,"OUTMSGCOL",9))
               {
                  //sUserCfg.msgCfg.uiOutMsgCol=parseColor(tmpNV->value.s,tmpNV->value.len);
               }
               break;
               ///sUserCfg.szMelody
            case CFG_F_TRUE|CFG_F_SDP:
                  if (tmpNV->name.len==3 && strncmp(tmpNV->name.s,"p2p",3)==0){
                     cfg.iCanUseP2Pmedia=tmpNV->value.s[0]-'0';
                     printf(cfg.iCanUseP2Pmedia?"[p2p_on]":"[p2p_off]");
                  }
                  if (CMP_XML(tmpNV->name,"TMR",3)){
                     fillCfg(cfg.bufTMRAddr,&tmpNV->value);
                  }
                  break;
            case CFG_F_VIDEO|CFG_F_TRUE|CFG_F_SDP:
               if (CMP_XML(tmpNV->name,"RATE",4))
                  cfg.iVideoFrameEveryMs=(int)strtoul(tmpNV->value.s,NULL,0);
               else if(CMP_XML(tmpNV->name,"CAMERA",6)){
                  cfg.iCameraID=(int)strtoul(tmpNV->value.s,NULL,0);
               }
               else if(CMP_XML(tmpNV->name,"DISABLE",7)){
                  cfg.iDisableVideo=(int)strtoul(tmpNV->value.s,NULL,0);
               } 
               else if(CMP_XML(tmpNV->name,"ADDMEDIAINCALL",14)){
                  cfg.iCanAttachDetachVideo=(int)strtoul(tmpNV->value.s,NULL,0);
                  
               }
               break;

            case CFG_F_TRUE|CFG_F_SDP|CFG_F_ZRTP:
               if (CMP_XML(tmpNV->name,"FLAG",4)){
                  int f=(int)strtoul(tmpNV->value.s,NULL,0);
                  cfg.iCanUseZRTP=f;
                  cfg.iZRTP_On=!!f;
               }
               else if (CMP_XML(tmpNV->name,"ZID",3)){
                  if(tmpNV->value.len>31)tmpNV->value.len=31;
                  fillCfg(cfg.szZID_base16,&tmpNV->value);
               }
               else if (CMP_XML(tmpNV->name,"SDES",4)){
                  int f=(int)strtoul(tmpNV->value.s,NULL,0);
                  cfg.iCanUseZRTP=f;
                  cfg.iSDES_On=!!f;
               }
               else if (CMP_XML(tmpNV->name,"TUNNELING",9)){
                  cfg.iZRTPTunnel_On=!!strtoul(tmpNV->value.s,NULL,0);
               }
               break;

            case CFG_F_AUDIO|CFG_F_TRUE|CFG_F_SDP|CFG_F_CODECS:
                  if (CMP_XML(tmpNV->name,"ENABLEDG",8))
                     fillCfg(cfg.szACodecs3G,&tmpNV->value);
                  else if (CMP_XML(tmpNV->name,"DISABLEDG",9))
                     fillCfg(cfg.szACodecsDisabled3G,&tmpNV->value);
               else if (CMP_XML(tmpNV->name,"ENABLED",7))
                  fillCfg(cfg.szACodecs,&tmpNV->value);
               else if (CMP_XML(tmpNV->name,"DISABLED",8))
                  fillCfg(cfg.szACodecsDisabled,&tmpNV->value);
               else if(CMP_XML(tmpNV->name,"RESPWITHONE",11)){
                  cfg.iResponseOnlyWithOneCodecIn200Ok=strtoul(tmpNV->value.s,NULL,0);
               }
               break;
            case CFG_F_AUDIO|CFG_F_TRUE|CFG_F_SDP:
               if (CMP_XML(tmpNV->name,"RTPPORT",7))
                  cfg.iRtpPort=(int)strtoul(tmpNV->value.s,NULL,0);
               else if (CMP_XML(tmpNV->name,"AGC",3))
                  cfg.iUseAGC=tmpNV->value.s[0]!='0';
               else if (CMP_XML(tmpNV->name,"VAD",3))
                  cfg.iUseVAD=tmpNV->value.s[0]!='0';
               else if (CMP_XML(tmpNV->name,"VADG",4))
                  cfg.iUseVAD3G=tmpNV->value.s[0]!='0';
               else if (CMP_XML(tmpNV->name,"AEC",3))
                  cfg.iUseAEC=tmpNV->value.s[0]!='0';
               else if(CMP_XML(tmpNV->name,"PCKSZ",5))
                  cfg.iPayloadSizeSend=strtoul(tmpNV->value.s,NULL,0);
               else if(CMP_XML(tmpNV->name,"PCKSZG",6))
                  cfg.iPayloadSizeSend3G=strtoul(tmpNV->value.s,NULL,0);
               break;

            

            }
            tmpNV = tmpNV->next;
         }
      }

      if (tmpNode->content.s){
         // content
        // printf("%.*s\n", tmpNode->content.len, tmpNode->content.s);
      } else {
         //printf("\n");
         // children
         if(tmpNode->child){
            FindXMLVal(tmpNode->child, level+1,cfgFlag,cfg);
         }
      }

      cfgFlag=tmpFlag;

      tmpNode = tmpNode->next;
   }
}


void guiSaveUserCfg(PHONE_CFG *p, short *fn)
{

   if(p->iDontReadSaveCfg)return ;


#define T_CRLF "\n"
   p->iNeedSave=0;

   
   if(!p->iZRTP_On){
      p->iCanUseZRTP=0;
   }
   else if(p->iZRTP_On && !p->iCanUseZRTP)
      p->iCanUseZRTP=1;
   


   FILE *f=NULL;
#if !defined(__SYMBIAN32__) && !defined(ANDROID_NDK) && !defined(__APPLE__) && !defined(__linux__)
   wchar_t rb[]={'w',0};
   f = _wfopen((wchar_t *)fn, &rb[0]);
#else
   char bufFn[1024];
   convert16to8S(&bufFn[0],sizeof(bufFn),fn,0);
   f = fopen(&bufFn[0], "w");

#endif
   if (f==NULL)return ; 

   char pwdAES[256];
   
   
   int iAESpwdOK = encryptPWD(p->user.pwd, strlen(p->user.pwd), &pwdAES[0], sizeof(pwdAES), p->iIndex)>=0;
   
   char *pServ=p->tmpServ[0]?&p->tmpServ[0]:&p->str32GWaddr.strVal[0]; 
   
   fprintf(f,"<cfg vers=\"3\" disabled=\"%d\" CreatedByUser=\"%d\">" T_CRLF
           ,p->iAccountIsDisabled,p->bCreatedByUser);
   if(p->iCfgHideNumberField) p->user.nr[0]=0;
   
   fprintf(f,"   <user loginname=\"%s\" ", p->user.un);
   
   if(!p->iDontSavePwd){
      
      if(iAESpwdOK){
         p->iPlainPasswordDetected = 0;
         fprintf(f,"pwda=\"%s\" ",pwdAES);
         tivi_log1("aes pwd ok",0);
      }
      else {
         tivi_log1("ERROR: === !iAESpwdOK ====(!err if no aes key)",iAESpwdOK);
         
         //temp code
         char szEncPwd[256];
         encryptPwd(p->user.pwd, szEncPwd, sizeof(szEncPwd));//must not be used
         fprintf(f,"pwd=\"%s\" " ,szEncPwd );//must not be used
      }
   }
   
    fprintf(f,"fromname=\"%s\"  phoneNr=\"%s\" country=\"%s\" savepwd=\"%d\" partnerId=\"%s\"/>"T_CRLF
                  ,p->user.nick, p->user.nr, p->user.country, !p->iDontSavePwd, p->partnerId);
   

   fprintf(f,"   <sip sipport=\"%d\" sipka=\"%d\" >"T_CRLF,p->iSipPortToBind,p->iSipKeepAlive);
#ifndef __APPLE__
   fprintf(f,"      <registrar  auto=\"%s\" expires=\"%d\" lkey=\"%s\" server=\"%s\" showlocaliponly=\"%s\" pxifnat=\"%s\" stun=\"%s\" usestun=\"%d\" SIPTRANSPORT=\"%s\"/>"T_CRLF
      ,(p->iAutoRegister)?"on":"off",p->uiExpires,p->szLicenceKey,pServ
      ,(p->iUseOnlyNatIp)?"true":"false",
      p->bufpxifnat,p->bufStun,p->iUseStun,p->szSipTransport);
#else
   fprintf(f,"      <registrar title=\"%s\" auto=\"%s\" expires=\"%d\" lkey=\"%s\" server=\"%s\" showlocaliponly=\"%s\" pxifnat=\"%s\" stun=\"%s\" usestun=\"%d\" SIPTRANSPORT=\"%s\"/>"T_CRLF
           ,p->szTitle,(p->iAutoRegister)?"on":"off",p->uiExpires,p->szLicenceKey,pServ
           ,(p->iUseOnlyNatIp)?"true":"false",
           p->bufpxifnat,p->bufStun,p->iUseStun,p->szSipTransport);
   
#endif
   fprintf(f,"   </sip>"T_CRLF);

   
   fprintf(f,"   <phone autoanswer=\"%s\" wflag=\"%d\" " ,
      (p->iAutoAnswer)?"true":"false",p->iWarnCamMicUsage * 2 + p->iWarnNetworkUsage);

   fprintf(f," autorun=\"%s\"",(p->iAutoStart)?"true":"false");
   fprintf(f," lang=\"%s\"",p->szLangFN);
   fprintf(f," apprio=\"%d\"",p->iAPPrio);
   fprintf(f," nodialerhelper=\"%d\"",p->iDisableDialingHelper);
   
   


   fprintf(f,">"T_CRLF);
#define PEN_COL_OUT 0x003482 
#define PEN_COL_IN  0x00d78a10 

#ifndef GetRValue
#define GetRValue(rgb)      ((unsigned char )(rgb))
#define GetGValue(rgb)      ((unsigned char)(((unsigned short)(rgb)) >> 8))
#define GetBValue(rgb)      ((unsigned char)((rgb)>>16))
#endif
   int c2=PEN_COL_OUT;
   int c1=PEN_COL_IN;
   fprintf(f,"       <chat ");
   fprintf(f,"alertOnMsg=\"%u\" ",p->iBeepAtMsg);
   fprintf(f,"showTime=\"%u\" ",p->iInsertTimeInMsg);
   fprintf(f,"msgBelowNick=\"%u\" ",1);
   fprintf(f,"inMsgCol=\"%u,%u,%u\" ",
      GetRValue(c1),
      GetGValue(c1),
      GetBValue(c1)
      );
   fprintf(f,"outMsgCol=\"%u,%u,%u\" ",
      GetRValue(c2),
      GetGValue(c2),
      GetBValue(c2)

      );
   fprintf(f,"/>\r\n");
   fprintf(f,"       <gui autoshow=\"%s\"/>\r\n",(p->iAutoShowApi)?"true":"false");

   fprintf(f,"   </phone>"T_CRLF);
   fprintf(f,"   <snddev capture=\"%s\" playback=\"%s\" ring=\"%s\">"T_CRLF
      ,p->aMicCfg.szName
      ,p->aPlayCfg.szName
      ,p->aRingCfg.szName);
   fprintf(f,"       <volume input=\"%u\" output=\"%u\" ring=\"%u\"/>"T_CRLF
      ,p->aMicCfg.iVolume, p->aPlayCfg.iVolume,p->aRingCfg.iVolume);
   fprintf(f,"   </snddev>"T_CRLF);
   

   fprintf(f,"   <sdp p2p=\"%d\" tmr=\"%s\">"T_CRLF,p->iCanUseP2Pmedia, p->bufTMRAddr);
   fprintf(f,"      <zrtp flag=\"%d\" zid=\"%s\" sdes=\"%d\" tunneling=\"%d\"/>"T_CRLF,p->iCanUseZRTP,&p->szZID_base16[0], p->iSDES_On, p->iZRTPTunnel_On);
   fprintf(f,"      <audio rtpport=\"%d\" agc=\"%d\" vad=\"%d\" vadg=\"%d\" aec=\"%d\" pcksz=\"%d\" pckszg=\"%d\">"T_CRLF,p->iRtpPort,p->iUseAGC ,p->iUseVAD,p->iUseVAD3G,p->iUseAEC,p->iPayloadSizeSend,p->iPayloadSizeSend3G);

   fprintf(f,"         <codecs enabledg=\"%s\" disabledg=\"%s\" enabled=\"%s\" disabled=\"%s\" respwithone=\"%d\"/>"T_CRLF,p->szACodecs3G,p->szACodecsDisabled3G,p->szACodecs,p->szACodecsDisabled,p->iResponseOnlyWithOneCodecIn200Ok);

   fprintf(f,"      </audio>"T_CRLF);
   fprintf(f,"      <video rate=\"%d\" camera=\"%d\" disable=\"%d\" addmediaincall=\"%d\"/>"T_CRLF, p->iVideoFrameEveryMs,p->iCameraID,p->iDisableVideo,p->iCanAttachDetachVideo);
   fprintf(f,"   </sdp>"T_CRLF);

   fprintf(f,"</cfg>"T_CRLF);
   fclose(f);

   return;
}


void setCfgFN(CTEditBase &b, const char *fn){
#if defined(ANDROID_NDK) || defined(__APPLE__) || defined(__linux__)
   
   char * getFileStorePath();
   b.setText(getFileStorePath());
   b.addChar('/');
   b.addText(fn);
   
#elif defined(_WIN32)

	int len = getPathW(b.getText(), 2048);
	b.setLen(len);
	b.addText(fn);

#elif __SYMBIAN32__
   char *getCfgFilename();
   CTEditBase b(1024);
   b.setText("c:\\System\\Data\\");
   b.addText(fn);
#else
   int iPLen=getPathW(b.getText(),1023);
   //#error "TODO test next row cfg index and plen"
   b.setLen(iPLen);
   b.addText(fn);
#endif
   
}

void setCfgFN(CTEditBase &b, int iIndex){
   

#if defined(ANDROID_NDK) || defined(__APPLE__) || defined(__linux__)
 
   char * getFileStorePath();
   b.setText(getFileStorePath());
   if(iIndex)
      b.addInt(iIndex,"/tivi_cfg%d.xml");
   else
      b.addText("/tivi_cfg.xml");

#elif defined(_WIN32)

	int len = getPathW(b.getText(), 2048);
	b.setLen(len);
	if (iIndex)
		b.addInt(iIndex, "tivi_cfg%d.xml");
	else
		b.addText("tivi_cfg.xml");

#elif __SYMBIAN32__
   char *getCfgFilename();
   CTEditBase b(1024);
   b.setText("c:\\System\\Data\\");
   b.addText(getCfgFilename());
   if(iIndex)b.addInt(iIndex);
   b.addText(".xml");
#else
   int iPLen=getPathW(b.getText(),1023);
//#error "TODO test next row cfg index and plen"
   b.setLen(iPLen);
   b.addText("config");
   if(iIndex)b.addInt(iIndex);
   b.addText(".xml");
#endif
}


#ifdef __linux__
#include <fcntl.h>
#include <sys/stat.h>
#endif


void setOwnerAccessOnly(const short *fn){

#ifdef __linux__
   
   char bufFn[1024];
   convert16to8S(&bufFn[0],sizeof(bufFn),fn,0);
   
   chmod(&bufFn[0], (6<<(3+3)));
   
   
#endif
  
}

void setFileAttributes(const char *fn, int iProtect);
int isBackgroundReadable(const char *fn);
void log_file_protection_prop(const char *fn);

void setFileBackgroundReadable(const char *fn){
   
   if(!isBackgroundReadable(fn)){
      setFileAttributes(fn,0);
      
      log_file_protection_prop(fn);
   }
}

void setFileBackgroundReadable(CTEditBase &b){
   
   char buf[1024];
   int l=sizeof(buf)-1;
   
   const char *fn=b.getTextUtf8(buf, &l);
   
   setFileBackgroundReadable(fn);
}

int saveCfg(void *cfg, int iIndex)
{
   if(((PHONE_CFG*)cfg)->iDontReadSaveCfg)return 0;
   
   if(iIndex<0)iIndex=-1;
   
   CTEditBase b(1024);
   setCfgFN(b,iIndex);
   
   ((PHONE_CFG*)cfg)->iIndex=iIndex;

   guiSaveUserCfg((PHONE_CFG*)cfg,b.getText());

   setOwnerAccessOnly(b.getText());
   
   setFileBackgroundReadable(b);
   


   return 0;
}

void remIds(char *pDst, int iMaxSize, char *pRem){
   int ids1[16];
   int ids2[16];
   int l1=fillInts(pDst,&ids1[0],15);
   int l2=fillInts(pRem,&ids2[0],15);

   int i;
   int iResLen=0;
   for(i=0;i<l1;i++){
      
      if(!hasThisInt(&ids2[0],l2,ids1[i]))
         iResLen+=snprintf(pDst+iResLen,iMaxSize-iResLen,"%d,",ids1[i]);
   }
   if(iResLen)iResLen--;
   pDst[iResLen]=0;

}




void deleteCfg(void *p, int iIndex){
   
   CTEditBase b(1024);
   setCfgFN(b,iIndex);
   
   deleteFileW(b.getText());
   
}

static void setCfgDefaults(PHONE_CFG *cfg){
   cfg->setDefaults();
   cfg->bufStun[0]=0;
   cfg->iWarnNetworkUsage=1;
   cfg->iWarnCamMicUsage=1;
   cfg->iUseStun=1;
   cfg->iSDES_On=1;//sdes on by default ??? need for SC
   cfg->iZRTPTunnel_On=1;
   cfg->iCanUseP2Pmedia=1;
   
   cfg->iPlainPasswordDetected=0;
   
   if(cfg->iUseStun && !cfg->bufStun[0]){
      cfg->iUseStun=0;
   }
}

static void checkCfg(PHONE_CFG *cfg){
#ifdef _WIN32
   cfg->iSDES_On=1;//we have only SC accounts on win32.
   cfg->iZRTP_On=1;
#else

   if(mustUseZRTP()) cfg->iZRTP_On=1;
   if(mustUseSDES()) cfg->iSDES_On=1;
#endif
   
   
   if(mustUseTLS()){
      strcpy(cfg->szSipTransport,"TLS");
   }
   
   if(cfg->iUseStun && !cfg->bufStun[0]){
      cfg->iUseStun=0;
   }
   if(&cfg->szACodecsDisabled[0]){
      remIds(&cfg->szACodecs[0],sizeof(cfg->szACodecs),&cfg->szACodecsDisabled[0]);
   }
   if(&cfg->szACodecsDisabled3G[0]){
      remIds(&cfg->szACodecs3G[0],sizeof(cfg->szACodecs3G),&cfg->szACodecsDisabled3G[0]);
   }
   
   if(cfg->bufpxifnat[0]){
      
      ADDR a = cfg->bufTMRAddr;//tmr serv has to have PORT
      if(!a.getPort()){
         
         char bufDns[128];
         ADDR::stripPort(cfg->bufpxifnat, bufDns, sizeof(bufDns));

         t_snprintf(cfg->bufTMRAddr, sizeof(cfg->bufTMRAddr), "tmr-%s:443", bufDns);
      }
   }
}

static int getCfgLoc(PHONE_CFG *cfg,int iCheckImei, int iIndex)
{
   
   if(iCheckImei!=2){
      if(cfg->iDontReadSaveCfg)return 0;

      CTEditBase b(1024);
      setCfgFN(b,iIndex);
      setFileBackgroundReadable(b);

      cfg->iIndex=iIndex;
      setCfgDefaults(cfg);


      CParseXml xml;
      NODE *node=xml.mainXML(b.getText());
      if(node==NULL)getCfgO(cfg);

      if(!node)return -1;

      
      FindXMLVal(node,0,0,*cfg);
      
      checkCfg(cfg);

      if(cfg->iPlainPasswordDetected && isAESKeySet())
         guiSaveUserCfg(cfg,b.getText());

   }

   if(iCheckImei!=2 && cfg->iRtpPort==0 && cfg->iSipPortToBind==0)
   {
      cfg->setDefaults();
   }
   else if(cfg->szLicenceKey[0] && iCheckImei)
   {
      checkFirstPart3(cfg);

   }
   else if(iCheckImei){
      cfg->iIsValidZRTPKey=0;
   }

   return 0;
}

int getCfg(PHONE_CFG *cfg,int iCheckImei, int iIndex){
   int getCfgLoc(PHONE_CFG *cfg,int iCheckImei, int iIndex);
   
   if(iIndex)cfg->iIndex=iIndex;
   int ret=getCfgLoc(cfg,iCheckImei,iIndex);
   if(iIndex)cfg->iIndex=iIndex;
   return ret;
   
}

#endif
