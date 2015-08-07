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

#include <stdio.h>
#include <string.h>
/*
static void (*_zrtp_log_cb)(void *ret, const char *tag, const char *buf) = NULL;
static void *pLogRet=NULL;

//this fnc must be public
void set_zrtp_log_cb(void *pRet, void (*cb)(void *ret, const char *tag, const char *buf)){
   _zrtp_log_cb=cb;
   pLogRet=pRet;
}

void zrtp_log(const char *tag, const char *buf){
   if(_zrtp_log_cb){
      _zrtp_log_cb(pLogRet, tag, buf);
   }
}
*/

int t_snprintf(char *buf, int iMaxSize, const char *format, ...);

static unsigned int log_line_cnt=0;

template<int iMaxLines>
class CTLog{
   enum{eBytesPerLine=128};
   int iNextLine;
   char buf[iMaxLines*eBytesPerLine+eBytesPerLine];
   int iBytesInLine[iMaxLines+1];
public:
   CTLog(){
      iNextLine=0;
      memset(buf,0, sizeof(buf));
      memset(iBytesInLine,0, sizeof(iBytesInLine));
   }
   static void log_fnc(void *pRet, const char *tag, const char *buf){
      if(!pRet)return;
      ((CTLog*)pRet)->log(tag, buf);
   }
   void log(const char *tag, const char *buf){
      
      int line=iNextLine;
      if(line+1>=iMaxLines)iNextLine=0;else iNextLine=line+1;
      iBytesInLine[iNextLine]=0;
      
      char *p=getLine(line);
      iBytesInLine[line] = 0;
      iBytesInLine[line] = t_snprintf(p, eBytesPerLine-1,"%s,%u,[%s]\n",tag,log_line_cnt,buf);
      log_line_cnt++;
#if defined(ANDROID_NDK)
      void tivi_log_tag(const char *tag, const char *val);
      tivi_log_tag(tag, buf);
#endif
   }
   void fillLog(int iLastNLines, void *ret, void(*fnc)(void *ret, const char *line, int iLen)){
      
      if(!fnc)return;
      
      if(iLastNLines>iMaxLines)iLastNLines=iMaxLines;
      
      int iFromLine=iNextLine-iLastNLines;
      if(iFromLine<0){
         int f=iMaxLines+iFromLine;
         for(int i=f;i<iMaxLines;i++){
            char *p=getLine(i);
            int l=iBytesInLine[i];
            if(l)fnc(ret,p,l);
         }
      }
      for(int i=0;i<iNextLine;i++){
         char *p=getLine(i);
         int l=iBytesInLine[i];
         if(l)fnc(ret,p,l);
      }
      
   }
   
   int getLog(int iLastNLines, char *out, int iMaxLen){
      TGL tgl;
      memset(tgl, 0, sizeof(tgl));
      tgl.out=out;
      tgl.iMaxLen=iMaxLen-1;
      fillLog(iLastNLines, &tgl, tglf);
      return tgl->iBytesIn;
   }
   
private:
   typedef struct{
      char *out;
      int iMaxLen;
      int iBytesIn;
      int e;
   }TGL;
   
   static void tglf(void *ret, const char *line, int iLen){
      TGL *p=(TGL*)ret;
      if(p->iBytesIn+iLen>=p->iMaxLen){p->e=1;return;}
      strncpy(&p->out[p->iBytesIn], line, iLen);
      p->iBytesIn+=iLen;
      p->out[p->iBytesIn]=0;
   }
   
   char *getLine(int line){
      if(line<0 || line>=iMaxLines)return &buf[0];
      return &buf[line*eBytesPerLine];
   }
};



static CTLog<50> zrtp_log;
static CTLog<50> audio_log;

void log_zrtp(const char *tag, const char *buf){
   zrtp_log.log(tag, buf);
}


void log_audio(const char *tag, const char *buf){
   audio_log.log(tag, buf);
}

void t_read_log(int iLastNLines, void *ret, void(*fnc)(void *ret, const char *line, int iLen)){
   zrtp_log.fillLog(iLastNLines, ret, fnc);
   audio_log.fillLog(iLastNLines, ret, fnc);
}

void t_init_log(){
   void set_zrtp_log_cb(void *pRet, void (*cb)(void *ret, const char *tag, const char *buf));
   set_zrtp_log_cb(&zrtp_log, CTLog<50>::log_fnc);
 //  zrtp_log.log("zrtp","t_init_log()");
   /*
   for(int i=0;i<100;i++){
      char b[8];
      sprintf(b,"i=%i",i);
       zrtp_log.log("zrtp",b);
   }
    */
}

void t_rel_log(){
   
}
