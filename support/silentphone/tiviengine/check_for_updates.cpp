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
#include <stdlib.h>

static char gszUrl[512]; 
static const int iUpdateCheckFreq=3600*24;//1day
static const int iUpdateCheckFreqIfFail=3600*1;//1hour

char *getBuildNr();

void startThX(int (cbFnc)(void *p),void *data);
char* download_page2(const char *url, char *buf, int iMaxLen, int &iRespContentLen, 
                     void (*cb)(void *p, int ok, const char *pMsg), void *cbRet);

int ask(const char *msg, const char *title);
void openWeb(const char *url);

void tivi_sleep_ms(unsigned int ms );

int getCFGItemSz(char *ret, int iMaxSize, char *p, int iCfgLen, const char *key);
int getCFGItemI(int *ret, char *p, int iCfgLen, const char *key);

static void cbD(void *p, int ok, const char *pMsg){
//   void adbg(char *p, int v);
  // adbg(pMsg?pMsg:"cbD",ok);

}

//returs 1 if ok, 0 if cancel
int showPopupMsg(const char *msg){

   return ask(msg, "Silent Eyes Update");
}

void cfu_log(const char *msg, int code){
  // void adbg(char *p, int v);
  // adbg((char*)msg,code);
}

static int check_for_updates(){
   char buf[4096];
   int iRespContentLen=0;
   char *p=download_page2(gszUrl, buf, sizeof(buf)-1, iRespContentLen, cbD, NULL);
   if(!p || iRespContentLen<sizeof("build: 1")){cfu_log("download_page2 err ",(int)p);return -1;}
   //build: 12000
   //web: www.silentcircle.com
   //download_url: https://silentcircle.com/downloads/b.exe
   //force: 0
   //description: 0

   char description[1024]="";
   char download_url[1024]="";
   char web[1024]="";
   int force=-1,build=-1;
 
   getCFGItemI(&force, p, iRespContentLen, "force");
   getCFGItemI(&build, p, iRespContentLen, "build");

#define PARSE_UP_SZ(_A)\
   getCFGItemSz(&_A[0],sizeof(_A)-1, p, iRespContentLen, #_A);

   PARSE_UP_SZ(description);
  // PARSE_UP_SZ(download_url);
   //PARSE_UP_SZ(web);

   strcpy(download_url, "https://silentcircle.com/downloads/SilentEyes_install.exe");

   cfu_log("cl=",iRespContentLen);
   cfu_log("build=",build);
   cfu_log(description, force);

   if(atoi(getBuildNr()) < build){
      const char *msg="An update is available.";
      const char *msgF="An update is required.";
      int r = showPopupMsg(description[0] ? description:(force? msgF : msg));
      if(r>0){
         openWeb(download_url[0]?download_url:(web[0]?web:"https://www.silentcircle.com"));
      }
      if(force>0)exit(1);
   }

   return 0;
}

static int th_update_check(void *p){
   int iNextCheckAfter=30;
   while(1){//TODO while app is runing
      tivi_sleep_ms(2000);
      iNextCheckAfter-=2;
      if(iNextCheckAfter>0)continue;
      int r=check_for_updates();
      iNextCheckAfter = r<0 ?iUpdateCheckFreqIfFail : iUpdateCheckFreq;
   }
   return 0;
}

void start_check_for_updates(const char *url){
   strncpy(gszUrl, url, sizeof(gszUrl));
   gszUrl[sizeof(gszUrl)-1]=0;
   startThX(th_update_check,NULL);
}
