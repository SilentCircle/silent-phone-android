/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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
#include <stdarg.h>

#include <stdlib.h>
#include <limits.h>
#include <time.h>

#include "../tiviandroid/sc_logs.h"


#if defined(__APPLE__)
//#include "/Users/eturner/Programming/SilentCircle/SPi3/spi3/Logging/DevLogging/SCSPLog_private.h"
#import "SCSPLog_private.h"
#endif

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

void t_logf(void (*log_fnc)(const char *tag, const char *buf), const char *tag, const char *format, ...){
   
   char buf[2048];
   
   va_list arg;
   va_start(arg, format);
   vsnprintf(buf, sizeof(buf), format, arg);
   va_end( arg );
   
   log_fnc(tag, buf);
   
}

void t_logfp(void (*log_fncp)(void *p, const char *tag, const char *buf),void *p, const char *tag, const char *format, ...){
   
   char buf[2048];
   
   va_list arg;
   va_start(arg, format);
   vsnprintf(buf, sizeof(buf), format, arg);
   va_end( arg );
   
   log_fncp(p, tag, buf);
   
}


static unsigned int log_line_cnt=0;

template<int iMaxLines>
class CTLog{
   enum{eBytesPerLine=128};
   int iNextLine;
   char *buf;
   int iBytesInLine[iMaxLines+1];
public:
   CTLog(){
      iNextLine=0;
      int sz = iMaxLines * eBytesPerLine + eBytesPerLine;
      buf = new char [sz];
      memset(buf, 0, sz);
      memset(iBytesInLine, 0, sizeof(iBytesInLine));
   }
   ~CTLog(){
      delete buf;
   }
   static void log_fnc(void *pRet, const char *tag, const char *buf){
      if(!pRet)return;
      ((CTLog*)pRet)->log(tag, buf);
   }
   
   void log(const char *tag, const char *buf){
       //DebugLogging: save log entry into files
       int debugLoggingEnabled(void);
       if(debugLoggingEnabled()){
           writeToFile(buf);
       }
       
      //split line
      const char *p = buf;
      int iLen=0;
      int iFirst=1;
      for(;;){
         
         if(!p[0] || p[0]=='\n' || p[0]=='\r' || iLen + 16 >= eBytesPerLine){
            
            while(p[0]=='\n' || p[0]=='\r'){p++;iLen++;}
            
            log_line(tag, p-iLen, iLen, iFirst,p[0]==0);
            
            iFirst=0;
            iLen=0;
         }
         if(!p[0])break;
         iLen++;
         p++;
      }
      
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
   
   int maxLines(){return iMaxLines;}
   
   int getLog(int iLastNLines, char *out, int iMaxLen){
      TGL tgl;
      memset(&tgl, 0, sizeof(tgl));
      tgl.out=out;
      tgl.iMaxLen=iMaxLen-1;
      fillLog(iLastNLines, &tgl, tglf);
      return tgl.iBytesIn;
   }
   
private:
   
   void log_line(const char *tag, const char *buf, int iLen, int iFirst, int iLast){
      
      if(iLen<1)return;
      
      int line=iNextLine;
      if(line+1>=iMaxLines)iNextLine=0;else iNextLine=line+1;
      iBytesInLine[iNextLine]=0;
      
      char *p=getLine(line);
      iBytesInLine[line] = 0;
      
      if(iFirst && iLast){
         iBytesInLine[line] = t_snprintf(p, eBytesPerLine-1,"%s,%u,[%.*s]\n",tag,log_line_cnt,iLen, buf);
      }
      else if(iFirst){
         iBytesInLine[line] = t_snprintf(p, eBytesPerLine-1,"%s,%u,[%.*s",tag,log_line_cnt,iLen, buf);
      }else if(iLast){
         iBytesInLine[line] = t_snprintf(p, eBytesPerLine-1,"%.*s]\n",iLen, buf);
      }else{
         iBytesInLine[line] = t_snprintf(p, eBytesPerLine-1,"%.*s",iLen, buf);
      }
#if defined(ANDROID_NDK)
      void androidLog(const char* format, ...);
      androidLog("%.*s", iBytesInLine[line], p);
#endif
      log_line_cnt++;
   }
   
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

static CTLog<100> zrtp_log;
static CTLog<100> audio_log;
static CTLog<600> audio_stats_log;//last 1h
static CTLog<500> sip_log;
static CTLog<1000> event_log;

static void debug_sip_wo_sdes_key(const char *tag, const char *buf){
   
#define MAX_LOG_BUF_SIZE 1800
 
   int iLen = 0;

   int iContentLen = 0;
   
   for(int i=0;;i++){
      if(!buf[i])break;
      iLen++;
      
      if(iContentLen){iContentLen++;continue;}
      
      if(buf[i]=='\r' && buf[i+1]=='\n' && buf[i+2]=='\r' && buf[i+3]=='\n' && buf[i+4]){
         iContentLen=1;
         i+=4;
         buf+=5;
         iLen+=5;
      }
   }
   
   int iMsgLen = iLen - iContentLen;
 
   if(iLen>MAX_LOG_BUF_SIZE){iContentLen-=(iLen-MAX_LOG_BUF_SIZE);iLen=MAX_LOG_BUF_SIZE;}
   
   
   if(iContentLen<60 || iMsgLen<100){
      sip_log.log(tag, buf);
   }
   else{
      
      char bufS[MAX_LOG_BUF_SIZE+100];
      memcpy(bufS,buf,iMsgLen);
      int iNewLen=iMsgLen;
      int iMaxSearch=iLen-10;
      int iKeyFound=0;
      int iKeys=0;
      
      for(int i=iMsgLen;i<iLen;i++){
         //Strips SDES key from Logs
         if(i<iMaxSearch && buf[i+8]==':'&& strncmp(&buf[i],"a=crypto",8)){
            strncpy(&bufS[iNewLen],&buf[i],9);iNewLen+=9;i+=9;
            iKeys++;
            if(iKeys==3)break;
            for(;i<iLen;i++){
               if(i<iMaxSearch && strncmp(&buf[i]," inline:",8)==0){
                  strncpy(&bufS[iNewLen],&buf[i],8);iNewLen+=8;i+=8;
                  strncpy(&bufS[iNewLen],"key\n",4);iNewLen+=4;//TODO print_key_hash
                  iKeyFound=1;i+=3;
                  continue;
                  
               }
               if(iKeyFound){
                  if(buf[i]=='\n'){iKeyFound=0; break;}
                  continue;
               }
               bufS[iNewLen]=buf[i];
               iNewLen++;
            }
            continue;
            
         }
         bufS[iNewLen]=buf[i]; iNewLen++;
      }
      bufS[iNewLen]=0;
      sip_log.log(tag, bufS);
   }
   
}

void log_sip(const char *tag, const char *buf){
#if defined(__APPLE__)
    ios_log_tivi(tivi_log_sip, tag, buf);
#endif

   debug_sip_wo_sdes_key(tag, buf );
   //sip_log.log(tag, buf);
}

void log_audio_stats(const char *tag, const char *buf){
#if defined(__APPLE__)
    ios_log_tivi(tivi_log_audio_stats, tag, buf);
#endif

    audio_stats_log.log(tag, buf);
}

void log_events(const char *tag, const char *buf){
#if defined(__APPLE__)
    ios_log_tivi(tivi_log_events, tag, buf);
#endif

   event_log.log(tag, buf);
}

void log_zrtp(const char *tag, const char *buf){
#if defined(__APPLE__)
    ios_log_tivi(tivi_log_zrtp, tag, buf);
#endif

   zrtp_log.log(tag, buf);
}


void log_audio(const char *tag, const char *buf){
#if defined(__APPLE__)
    ios_log_tivi(tivi_log_audio, tag, buf);
#endif

   audio_log.log(tag, buf);
}

static char szLastCID[64]={0};

typedef struct{
   char *buf;
   int iMaxSize;
   int iCurWritePos;
   char cid[64];
   int cid_len;
   int iStart;
   int iEnd;
   
   void onNew(){
      if(iEnd|iStart){
         iResetPos = iCurWritePos;
      }
      else iCurWritePos = iResetPos;
      
      iEnd=iStart=0;
   }
   int iStartsEnds;
   int iResetPos;
   
}T_GET_LOG;

static void colect_call_log(void *ret, const char *line, int iLen){
   T_GET_LOG *p = (T_GET_LOG*)ret;
   if(p->iEnd)return;
   // + 6 + 6 = sizeof("_call," + "marker" ) 
   if(iLen > p->cid_len + 6 + 6 && strncmp(line, "_call,", 6)==0 && strncmp(p->cid,line + iLen - p->cid_len - 2, p->cid_len)==0){
      p->iEnd = strncmp(line + iLen - p->cid_len - 2 - 3, "=0 ", 3)==0;
      if(!p->iEnd){
         p->iStart = 1;
         p->iCurWritePos = p->iResetPos;
      }
      p->iStartsEnds++;
    //  printf("cid s%d e%d",p->iStart, p->iEnd);
   }
   //+ 10 security cookie
   if(p->iCurWritePos + iLen + 10 >= p->iMaxSize){
      p->iMaxSize = p->iMaxSize * 2 + iLen;
      char *pn = new char [p->iMaxSize];
      memcpy(pn, p->buf, p->iCurWritePos + 1);//append zero
      delete p->buf;
      p->buf = pn;
   }
   
   memcpy(p->buf + p->iCurWritePos, line, iLen + 1); // +1 zero trminate
   p->iCurWritePos += iLen;
 
}

char *getLogForCall(const char *cid, int *iLen, int iAudioStats, int iSips, int iEvents, int iZRTP){
   T_GET_LOG l;
   memset(&l, 0, sizeof(T_GET_LOG));
   
   l.iMaxSize = 2000;
   
   l.buf = new char [l.iMaxSize];
   
   if(cid==NULL)cid = szLastCID;
   l.cid_len = t_snprintf(l.cid, sizeof(l.cid), "%s", cid);
   
   if(iSips){
      l.onNew();
      sip_log.fillLog(sip_log.maxLines(), &l, colect_call_log);
   }

   if(iZRTP){
      l.onNew();
      zrtp_log.fillLog(zrtp_log.maxLines(), &l, colect_call_log);
   }
   
   if(iEvents){
      l.onNew();
      event_log.fillLog(event_log.maxLines(), &l, colect_call_log);
   }
   
   if(iAudioStats){
      l.onNew();
      audio_log.fillLog(audio_log.maxLines(), &l, colect_call_log);
      l.onNew();
      audio_stats_log.fillLog(audio_stats_log.maxLines(), &l, colect_call_log);
   }
   
   
   if(!l.iStartsEnds){
      l.iCurWritePos = 0;
      delete l.buf;
      l.buf=0;
   }
   
   if(iLen)*iLen = l.iCurWritePos;
   
   return l.buf;
}

void log_call_marker(int iStarts, const char *cid, int cid_len){
   char b[64]={0};
   t_snprintf(b, sizeof(b), "marker=%d %.*s",iStarts, cid_len, cid);
   
   if(iStarts==0){
      t_snprintf(szLastCID, sizeof(szLastCID), "%.*s",cid_len, cid);
   }
   
   sip_log.log("_call", b);
   audio_stats_log.log("_call", b);
   event_log.log("_call", b);
   zrtp_log.log("_call", b);
   audio_log.log("_call", b);
}


void t_read_log(int iLastNLines, void *ret, void(*fnc)(void *ret, const char *line, int iLen)){
   
   fnc(ret, ">>>>\n",5);
   sip_log.fillLog(iLastNLines, ret, fnc);
   
   fnc(ret, ">>>>\n",5);
   event_log.fillLog(iLastNLines, ret, fnc);
   
   fnc(ret, ">>>>\n",5);
   zrtp_log.fillLog(iLastNLines, ret, fnc);
   
   fnc(ret, ">>>>\n",5);
   audio_log.fillLog(iLastNLines, ret, fnc);
   
   fnc(ret, ">>>>\n",5);
   audio_stats_log.fillLog(iLastNLines, ret, fnc);
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
