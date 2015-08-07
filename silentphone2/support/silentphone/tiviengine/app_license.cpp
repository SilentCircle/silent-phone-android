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


#include "main.h"
#include "../encrypt/md5/md5.h"


int getDaysFromNYear(int x);
const char *getPwdById(int id);
const char *getTrialKey();
void getLicnecePwd(char *p, const char *user);
char *t_getDevUniqID(char *buf);


int createKey2(char *imei, const char *pwd, char *param, char *resp){
   
   CTMd5 md51;
   md51.update((unsigned char *)pwd, (unsigned int)3);
   md51.update( (unsigned char *)":", 1);
   md51.update((unsigned char *)imei, (unsigned int)strlen(imei));
   md51.update( (unsigned char *)":", 1);
   md51.update((unsigned char *)"MD\x15\rSESS", 8);
   md51.update( (unsigned char *)":", 1);
   md51.update((unsigned char *)param, (unsigned int)strlen(param));
   int i51=md51.final();
   if(i51<0)i51=-i51;
   
   return sprintf(resp,"%u",i51);
}

int createKey1(char *imei, const char *pwd, char *resp){
   
   CTMd5 md51;
   md51.update( (unsigned char *)pwd, strlen(pwd));
   md51.update( (unsigned char *)":", 1);
   md51.update((unsigned char *)"INVITE", 6);
   md51.update( (unsigned char *)":", 1);
   md51.update((unsigned char *)imei, (unsigned int)strlen(imei));
   int i51=md51.final();
   if(i51<0)i51=-i51;
   
   return sprintf(resp,"%u",i51);
}

int isLicenceOkX(PHONE_CFG *cfg, const char *p)
{
   char imei[64];
   char resp[64];
   char pwd[64];
   
   getLicnecePwd((char*)&pwd[0],p);
   
   t_getDevUniqID(&imei[0]);
   int l=createKey1(&imei[0],&pwd[0],&resp[0]);
   createKey2(&imei[0],&pwd[0],&resp[0],&resp[l]);
   int ok=strcmp(&resp[0],cfg->szLicenceKey)==0;
   cfg->iIsLKeyValid=ok;
   return ok;
   //
}




int isTrialKeyValid(PHONE_CFG *cfg)
{
   
   if(!cfg->szLicenceKey[0]){
      cfg->iIsValidZRTPKey=0;
      if(cfg->iCanUseZRTP>1)cfg->iCanUseZRTP=1;
      cfg->iIsLKeyValid=0;
      
      return 0;
   }
   
   const int tabTrDays[10]={1,3,10,30,90,7,3,10,1,90};
   int d=getDaysFromNYear(2000);
   
   char resp[32];
   //   createTrialKey(&cfg->szLicenceKey[0],"123",3);
   
   
   int dValid=tabTrDays[cfg->szLicenceKey[0]-0x30];
   
   int i;
   int dPwd=0;
   int rev[]={0,5,9,2,4,8,1,7,6,3};
   for (i=1;i<6;i++)
   {
      dPwd*=10;//cfg->szLicenceKey[i]*=10;
      dPwd+=rev[(cfg->szLicenceKey[i]-0x30)];
   }
   int dMax=dValid+dPwd+1;
   int dMin=dPwd-1;
   if(dMax<d || d<dMin)return 0;
   //if(dPwd+dValid+1<d || dPwd+1<d)return 0;
   
   char imei[64];
   t_getDevUniqID(&imei[0]);
   
   
   
   strncpy(resp,&cfg->szLicenceKey[0],6);
   resp[6]=0;
   createKey2(&imei[0],getTrialKey(),&resp[0],&resp[6]);
   
   int ok=strcmp(resp,cfg->szLicenceKey)==0;
   if(ok){
      cfg->iIsValidZRTPKey=2;
      cfg->iCanUseZRTP=3;
      cfg->iIsLKeyValid=1;
   }
   return ok;
}


int isLicenceOk(PHONE_CFG *cfg)
{
   if(!cfg->szLicenceKey[0]){
      cfg->iIsValidZRTPKey=0;
      if(cfg->iCanUseZRTP>1)cfg->iCanUseZRTP=1;
      cfg->iIsLKeyValid=0;
      return 0;
   }
   int zrtpOk=isLicenceOkX(cfg,getPwdById(2))?2:0;
   if(!zrtpOk)zrtpOk=isLicenceOkX(cfg,getPwdById(1))?1:0;
   
   //zrtpOk=2;
   
   cfg->iIsValidZRTPKey=zrtpOk;
   
   if(zrtpOk)cfg->iCanUseZRTP=zrtpOk==2?3:2;else if(cfg->iCanUseZRTP>1)cfg->iCanUseZRTP=1;
   
   int ok=zrtpOk;
#if defined(_WIN32_WCE) || defined(__SYMBIAN32__)
   if(!ok)ok=isLicenceOkX(cfg,getPwdById(0));
   if(!ok)ok=isTrialKeyValid(cfg);
#else
   if(!ok)ok=isTrialKeyValid(cfg);
#endif
   
   return ok;
}

int xcheckLicenceSecondPart(PHONE_CFG &p_cfg, const char *xp)
{
   int ok=0;
   if(p_cfg.iFirstLicencePartIsOk)
   {
      
      char buf[64];
      char pwd[64];
      getLicnecePwd((char*)&pwd[0],xp);
      strcpy(buf,p_cfg.szLicenceKey);
      //if(p_cfg.iFirstLicencePartIsOk>1000)p_cfg.iFirstLicencePartIsOk-=1000;
      
      int iFirstLen=p_cfg.iFirstLicencePartIsOk&0xff;
      
      buf[iFirstLen]=0;
      //      int _TODO_GET_IMEI;
      char bufImei[64];
      char *t_getDevUniqID(char *buf);
      t_getDevUniqID(&bufImei[0]);
      createKey2(&bufImei[0],&pwd[0],&buf[0] , &buf[iFirstLen]);
      int nok=strcmp(buf,p_cfg.szLicenceKey) && p_cfg.szLicenceKey[0];
      if(!nok)
      {  
         p_cfg.iIsLKeyValid=1;
         ok=1;
      }
      else p_cfg.iIsLKeyValid=0;
   }
   return ok;
}



void checkLicenceSecondPart(PHONE_CFG &p_cfg){
   // up_Checker_chkStatus(NULL,500);
   
   p_cfg.iIsValidZRTPKey=(p_cfg.iFirstLicencePartIsOk&0x1000) && xcheckLicenceSecondPart(p_cfg,getPwdById(2))?2:0;
   if(!p_cfg.iIsValidZRTPKey)
   {
      p_cfg.iIsValidZRTPKey=(p_cfg.iFirstLicencePartIsOk&0x100) && xcheckLicenceSecondPart(p_cfg,getPwdById(1))?1:0;
   }
   if(!p_cfg.iIsValidZRTPKey)xcheckLicenceSecondPart(p_cfg,getPwdById(0));
   
   if(p_cfg.iIsLKeyValid==0){
      isTrialKeyValid(&p_cfg);
   }
   if(p_cfg.iSkipXMLCfgServAddr)p_cfg.tmpServ[0]=0;
   //#if defined(_WIN32) && !defined(_WIN32_WCE)
   p_cfg.iIsLKeyValid=1;
   //#endif
#ifndef _T_SOLD_REL
   if(p_cfg.iIsLKeyValid && p_cfg.tmpServ[0])
   {
      p_cfg.str32GWaddr.uiLen=strlen(p_cfg.tmpServ);
      strcpy(p_cfg.str32GWaddr.strVal,p_cfg.tmpServ);
   }
   else p_cfg.tmpServ[0]=0;
#else
   p_cfg.iIsLKeyValid=0;
#endif
   
}

int checkFirstPart(PHONE_CFG *cfg, char* pwd_glob , int mask)
{
   int ok=0;
   int createKey1(char *imei, const char *pwd, char *resp);
   char buf[64];
   char pwd[64];
   //tivi_log("lk1");
   getLicnecePwd((char*)&pwd[0],pwd_glob);
   char bufImei[32];
   char *t_getDevUniqID(char *buf);

   t_getDevUniqID(&bufImei[0]);
   
   char *imei=&bufImei[0];
   
   int l1;
   cfg->iIsValidZRTPKey=0;
   cfg->iIsLKeyValid=0;
   
   l1=createKey1(imei,&pwd[0],&buf[0]);
   //tivi_log("lk3");
   cfg->iFirstLicencePartIsOk=strlen(&buf[0]);
   ok=strncmp(&buf[0],cfg->szLicenceKey,cfg->iFirstLicencePartIsOk)==0 && cfg->szLicenceKey[0]?1:0;
   //tivi_log("lk4");
   if(!ok){cfg->iFirstLicencePartIsOk=0; }
   else cfg->iFirstLicencePartIsOk|=mask;
   //tivi_log("lk5");
   return ok;
}

int checkFirstPart3(PHONE_CFG *cfg){
   int ok=0;
#ifndef _T_SOLD_REL
   
   cfg->iIsValidZRTPKey=0;
   cfg->iIsLKeyValid=0;
   //tivi_log("log12");
   char *pwd2=(char*)getPwdById(2);
   //tivi_log("log1221");
   
   ok=checkFirstPart(cfg,pwd2,0x1000);
   //tivi_log("log121");
   if(!ok)ok=checkFirstPart(cfg,(char*)getPwdById(1),0x100);
   //tivi_log("log122");
   if(!ok)ok=checkFirstPart(cfg,(char*)getPwdById(0),0);
   
#else
   cfg->tmpServ[0]=0;
#endif

   return 0;
}
