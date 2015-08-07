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

//#include "CSessions.h"
#include "../tiviengine/main.h"
#include <stdlib.h>
#include <time.h>

#include "../baseclasses/CTEditBase.h"
#include "../encrypt/md5/md5.h"



#if defined(__linux__)
#include <wchar.h>
#endif





char *loadFileW(const  short *fn, int &iLen);
char* getCfgLocation();

void dbgWMI(const char *p, char *p2);
#if !defined(_WIN32)  && !defined(_WIN32_WCE)
int isDevIDUSB(){return 0;}
#endif


#if defined(_WIN32) && !defined(_T_CONSOLE_ ) && !defined(__SYMBIAN32__)
#ifndef _WIN32_WCE
#include <windows.h>
#else
#include <aygshell.h>
#endif
#endif//os

#ifdef _WIN32
#if !defined(_WINDOWS_)
#include <windows.h>
#endif


//void tivi_sleep_ms(unsigned int ms){Sleep(ms);}

/*
 class CTTIMERW{
 public:
 CTTIMERW(){
 timeBeginPeriod (1);
 }
 ~CTTIMERW(){
 timeEndPeriod (1);
 }
 };
 
 CTTIMERW timer;
 unsigned int getTickCnt(){return timeGetTime();}
 */


unsigned long long getTickCountLL()
{
   return ::GetTickCount64();
}
unsigned int getTickCount()
{
   static LARGE_INTEGER fr;
   LARGE_INTEGER now;
   static int iFreqOk=0;
   if(!iFreqOk && !::QueryPerformanceFrequency(&fr))return GetTickCount();
   if(!QueryPerformanceCounter(&now))return GetTickCount();
   iFreqOk=0;
   static unsigned long long frd=fr.QuadPart/1000;
   if(!frd)return GetTickCount();
   unsigned long long r=now.QuadPart/frd;
   
   return (long)(r);
}
void tivi_sleep_ms(unsigned int ms ){
   Sleep(ms);
}


void uSleep( int usec )
{
   int msec=usec>>10;
   Sleep(msec);
}
#else
//
#include <unistd.h>//sleep
#include <sys/time.h>

//#if defined(__APPLE__) || defined(ANDROID_NDK)|| defined(__linux__) 

unsigned int getTickCount()
{
   struct timeval v;
   if(gettimeofday(&v,0)==0)
      return v.tv_sec*1000+
      (v.tv_usec>>10); 
   return 0;
}

void uSleep(int usec ){
   usleep(usec); 
}

void tivi_sleep_ms(unsigned int ms ){
   unsigned int s=ms/1000;
   if(s){
      sleep(s);
      ms-=s*1000;
   }
   usleep(ms*1000); 
}

static char bufCfgPath[1024]={0};

void setFileStorePath(const char *p){
   strncpy(bufCfgPath, p, sizeof(bufCfgPath)-1);
   bufCfgPath[sizeof(bufCfgPath)-1]=0;
}

char *getFileStorePath(){
   return &bufCfgPath[0];
}


#endif


int get_time()//TODO add to os globals
{
#ifdef _WIN32_WCE
   return getDaysFromNYear(1970)*24*60*60;;
#else
   return time(NULL);
#endif
}



void tivi_log_au(const char* format, ...)
{
   /*
   {
         va_list arg;
   va_start(arg, format);
   vprintf(format,arg);
   printf("\r\n");
   va_end( arg );

   }
   */
   static int iOk=0;
   if(iOk==-1)return;
#if defined(_WIN32) 
   //|| defined(__linux__) 
#define T_TLOG_FNX "audio_log.txt"
   if(iOk==0){
      FILE *fx=fopen(T_TLOG_FNX,"rb");
      if(!fx){
         iOk=-1;
         return;
      }
      iOk=1;
      fclose(fx);
   }
   FILE *f=fopen(T_TLOG_FNX,"a+");
   if(!f){iOk=-1;return;}
   va_list arg;
   va_start(arg, format);
   vfprintf(f,format,arg);
   fprintf(f,"\r\n");
   fclose(f);
   va_end( arg );
#endif
   
}

int exeExists(){
#if defined(_WIN32) 
   wchar_t buf[1024];
   int iLen=GetModuleFileNameW(NULL,&buf[0],1023);
   FILE *f=_wfopen(buf,L"rb");
   
   if(f){
      int e=fseek (f , 0 , SEEK_END);
      if(!e){
         iLen = ftell (f);
         if(iLen<0)e=1;
      }
      
      fclose(f);
      return !e;
   }
   return 0;
#else
   return 1;
#endif
}

#if !defined(ANDROID_NDK)
void wakeCallback(int iLock){}
#endif

int getPathW(short *p, int iMaxLen)//TODO rename exec path
{
#if defined(ANDROID_NDK) || defined(__APPLE__)
   int iLen=0;
   char * getFileStorePath();;
   char *path= getFileStorePath();
   while(path[iLen]){
      p[iLen]=path[iLen];
      iLen++;
   }
   p[iLen]='/';
   iLen++;
   p[iLen]=0;
   return iLen;
#else
   int iLen=0,iLen2=0;
   p[0]=0;
#if (defined(_WIN32) && !defined(__SYMBIAN32__))
   iLen=GetModuleFileNameW(NULL,(LPWSTR)p,iMaxLen-1);
   //  convert16_8((short *)p, iLen);
#endif
   int i;
   for (i=0;i<iLen;i++)
   {
      if(p[i]=='\\')
         iLen2=i+1;
   }
   p[iLen2]=0;
   
   return iLen2;
#endif
}

int getPath(char *p, int iMaxLen)//TODO rename exec path
{
   
   int iLen=getPathW((short*)p,iMaxLen/2);
   convert16_8((short *)p, iLen);
   p[iLen]=0;
   return iLen;
}

#ifdef __SYMBIAN32__
char *getAdvCfgPath(char *v){strcpy(v,"c:\\System\\Data\\");return v;}
#else
char *getAdvCfgPath(char *v){getPath(v,128);return v;}
#endif

#if  1//!(defined(_WIN32) &&  !defined(_WIN32_WCE) &&  !defined(__SYMBIAN32__))



int getCfgO(PHONE_CFG *cfg)
{
   return -1;
}

#ifndef __SYMBIAN32__
int saveCfgO(void *cfg)
{
   return -1;
}
#endif
#endif

#ifdef _WIN32_WCE
#include <GetDeviceUniqueId.h>
#endif

int getSysParam(CTEditBase *e,int i);
char *t_createDevId(char *p, void *p1, int iLen1, void *p2, int iLen2)
{
   int i,i1=1,i2=2;
   int ii1[10];
   int ii2[10];
   memset(ii1,0,sizeof(ii1));
   memset(ii2,0,sizeof(ii2));
   if(iLen1>0)memcpy(ii1,p1,iLen1);
   if(iLen2>0)memcpy(ii2,p2,iLen2);
   for(i=0;i<10;i++)
   {
      i1^=ii1[i];
      i2^=ii2[i];
      i1+=0x3131;
      i2+=0x5353;
   }
   if(i1<0)i1=-i1;
   if(i2<0)i2=-i2;
   int l=sprintf(p,"%d%d",i1,i2);
   if(l<20)
   {
      for(;l<20;l++)p[l]='0';
      p[l]=0;
   }
   return p;
}


char *t_createDevId_PC(char *p, void *p1, int iLen1)
{
   int isDevIDUSB();
   int iIsUSB=isDevIDUSB();
   if(iLen1<11){
      
      if(iIsUSB){
         strcpy(p,"USB is not found");
      }
      else{
         strcpy(p,"DevID is not valid");
      }
      return p;
      
   }
   //  int i;//,i1=1,i2=2,i3=3;
   
   int resp[4]={0,0,0,0};
   CTMd5 md;
   md.update((unsigned char*)p1,iLen1);
   md.update((unsigned char*)":",1);
   md.final((unsigned char*)&resp[0]);
   if(resp[0]<0)resp[0]=-resp[0];
   if(resp[1]<0)resp[1]=-resp[1];
   if(resp[2]<0)resp[2]=-resp[2];
   if(resp[3]<0)resp[3]=-resp[3];
   int l;
   if(iIsUSB)
      l=sprintf(p,"%d%d%d%d",resp[0],resp[1],resp[2],resp[3]);
   else 
      l=sprintf(p,"%d%d%d",resp[0],resp[1],resp[2]);//???,resp[3]);
   
   
   
   if(l<(iIsUSB?28:24))
   {
      for(;l<(iIsUSB?28:24);l++)p[l]='0';
   }
   int cs=1;
   for(int i=0;i<(iIsUSB?26:22);i+=2){
      cs*=(p[i]-'0'+1);
      cs+=(p[i+1]-'0');
   }
   if(cs<0)cs=-cs;
   p[(iIsUSB?26:22)]=((cs%100)/10)+'0';
   p[(iIsUSB?27:23)]=(cs%10)+'0';
   p[(iIsUSB?28:24)]=0;
   
   return p;
}
static char bufAndroidImei[]="1234567890123";
char *getImeiBuf(){
   return &bufAndroidImei[0];
}
void setImei(char *p){
   strncpy(bufAndroidImei, p ,sizeof(bufAndroidImei)-1);
   bufAndroidImei[sizeof(bufAndroidImei)-1]=0;
}



char *t_get_dev_id(char *p)
{
#ifdef ANDROID_NDK
   strcpy(p,getImeiBuf());
#elif _WIN32_WCE
#define DEVICE_ID_LENGTH            20
#define APPLICATION_DATA            "0123456789abcdef"
#define APPLICATION_DATA_LENGTH     (sizeof(APPLICATION_DATA)-1)
   
   HRESULT         hr              = NOERROR;
   BYTE            rgDeviceId[DEVICE_ID_LENGTH];
   DWORD           cbDeviceId      = 21;//sizeof(rgDeviceId);
   
   hr = GetDeviceUniqueID(reinterpret_cast<PBYTE>(APPLICATION_DATA),
                          APPLICATION_DATA_LENGTH,
                          GETDEVICEUNIQUEID_V1,
                          rgDeviceId,
                          &cbDeviceId);
   //CHR(hr);
   CTEditBuf<64> b;
   getSysParam(&b,SPI_GETOEMINFO);
   if(cbDeviceId>30)cbDeviceId=20;
   else if(cbDeviceId<0)cbDeviceId=0;
   
   t_createDevId(p,rgDeviceId,cbDeviceId,b.getText(),b.getLen()*2);
   
#elif defined(_WIN32) && !defined(_WIN32_WCE)
   
   int getUiqID(unsigned char *p, int iMaxLen);
   unsigned char bufx[512];
   int l=100;
   
   void getPathCTE(CTEditBase &t_path);
   CTEditBase t_path(2048);
   
  // getPathCTE(t_path);
   int _len=getPathW(t_path.getText(),2048);
   t_path.setLen(_len);


   int len=t_path.getLen();
   t_path.addText("isserv");
   int fl=0;
   char *pf=loadFileW(t_path.getText(),fl);
   if(pf){
      delete pf;
      PHONE_CFG *cfg=(PHONE_CFG*)T_getSometing1(NULL,"p_cfg");
      
      if(cfg){
         int l;
         cfg->iLicIsUserServ=1;
         if(!cfg->user.un[0] || (cfg->tmpServ[0] && strcmp(cfg->tmpServ,cfg->str32GWaddr.strVal)))
         {
            int getCfg(PHONE_CFG *cfg,int iCheckImei, int iIndex);
            if(!cfg->user.un[0])getCfg(cfg,0,cfg->iIndex);
            if(cfg->tmpServ[0] && strcmp(cfg->tmpServ,cfg->str32GWaddr.strVal))
               l=sprintf(p,"%s@%s",cfg->user.un,cfg->tmpServ);
            else
               l=sprintf(p,"%s@%.*s",cfg->user.un,cfg->str32GWaddr.uiLen,cfg->str32GWaddr.strVal);
         }
         else{
            l=sprintf(p,"%s@%.*s",cfg->user.un,cfg->str32GWaddr.uiLen,cfg->str32GWaddr.strVal);
         }
         
         
         l--;
         while(l>0){
            if(p[l]=='@')break;
            if(isupper(p[l]))p[l]|=0x20;
            l--;
         }
      }
      else strcpy(p,"ID is not valid.");
      return p;
   }
   else{
      t_path.setLen(len);
      t_path.addText("isusb");//devid_user_serv");
      char *pf=loadFileW(t_path.getText(),fl);
      if(pf){
         
         void setDevIDUSB(int f);
         int ch=t_path.getChar(0);
         if(ch>='a')ch-=0x20;
         setDevIDUSB(ch);
         delete pf;
      }
   }
   l=getUiqID(&bufx[0],500);
   
   t_createDevId_PC(p,(void*)&bufx[0],l);
   
   
#endif
   return p;
}

#ifdef _WIN32
static char szDev_id[128]={0};
char const * t_getDev_id(void){
   if(szDev_id[0]){return &szDev_id[0];}
   char *t_get_dev_id(char *p);
   return t_get_dev_id(&szDev_id[0]);

}

char const * t_getDev_name(void){
   return "PC";
}


char const * t_getDevID_md5(void){
    int calcMD5(unsigned char *p, int iLen, char *out);
   const char *pn=t_getDev_id();
   static char md5[64];
   calcMD5((unsigned char*)pn,strlen(pn),&md5[0]);
   return &md5[0];
}
#endif

static const char str_qwerty[]="23456789qwmrty8pbsdfghjkzxcvbnm98765";

int getNBits(unsigned char *p, register int iPos, int iBits);

char* bits_to_base_qwerty(unsigned char *p ,int iBits, char *o, int *iLen){
   
   int l=0;
   int iBitPos=0;
   while(1){
      int iBitsLeft=iBits-iBitPos;
      int r=getNBits(p,iBitPos,iBitsLeft>5?5:iBitsLeft);
      // printf("r=%d\n",r);
      o[l]=str_qwerty[r];l++;
      iBitPos+=5;
      if(iBitPos>=iBits)break;
   }
   o[l]=0;
   
   
   
   if(iLen)*iLen=l;
   
   return o;
}
char *usbID_toBaseQwerty(int *i, char *o, int *iLen){
   CTMd5 md5;
   i[0]+=i[3];i[1]+=i[2];
   md5.update((unsigned char*)&i[0],sizeof(int)*4);
   unsigned char buf[16];
   md5.final(buf);
   
   bits_to_base_qwerty(&buf[0],60,o,iLen);
   int sc=91;
   int l=0;
   while(o[l] && o[l+1]){
      sc*=(o[l+1]+97);
      sc+=o[l];
      l+=2;
   }
   o[l]=str_qwerty[sc&31];   l++;
   o[l]=str_qwerty[(sc>>5)&31];   l++;
   o[l]=0;
   if(iLen)*iLen=l;
   
   return o;
}


char *t_getDevUniqID(char *buf)
{
#ifdef __SYMBIAN32__
   FILE *f=fopen("c:\\System\\Data\\t_m_c_id.txt","rb"); 
   
   if(f){
      fclose(f);
      
      int getCardId(char *buf);
      int resb[16];
      resb[3]=resb[2]=resb[1]=resb[0]=0;
      int r=getCardId((char*)&resb[0]);
      if(r>0){
         usbID_toBaseQwerty(&resb[0],buf,NULL);
         return buf;
      }
   }
   
   char *t_getImei(char *p);
   t_getImei(buf);
   //buf[0]='1';
   //buf[1]=0;
#else
   t_get_dev_id(buf);
#endif
   
   return buf;
}


int getSysParam(CTEditBase *e,int i)
{
#ifdef _WIN32
   TCHAR tszPlatform[64];
   
   //SPI_GETPLATFORMTYPE
   if (TRUE == SystemParametersInfo(i,
                                    sizeof(tszPlatform)/sizeof(*tszPlatform),tszPlatform,0))
   {
      /*
       if (0 == _tcsicmp(TEXT("Smartphone"), tszPlatform)) 
       {
       return TRUE;
       }
       */
      e->addText((char*)tszPlatform,0,1);
      return 0;
      
   }
#endif
   return -1;
}

int isWinSmartphone()
{
#ifdef _WIN32_WCE
   static int iRes=-1;
   if(iRes!=-1)return iRes;
   CTEditBuf<64> b;
   getSysParam(&b,SPI_GETPLATFORMTYPE);
   iRes=(cmpU(&b, &CTEditBase("SMARTPHONE"))==0);
   return iRes;
   
#else 
   return 0;
#endif
}

int isTyTn()
{
#ifdef _WIN32_WCE
   static int iRes=-1;
   if(iRes!=-1)return iRes;
   CTEditBuf<64> b;
   getSysParam(&b,SPI_GETOEMINFO);
   iRes=(cmpU(&b, &CTEditBase("HERM200"))==0);
   return iRes;
   
#else 
   return 0;
#endif
}

int stopAudioBeforSwitching()
{
   return isTyTn();
}

int needReverseRows()
{
   return isTyTn();
}


int isWinPocketPC()
{
#ifdef _WIN32_WCE
   static int iRes=-1;
   if(iRes!=-1)return iRes;
   CTEditBuf<64> b;
   getSysParam(&b,SPI_GETPLATFORMTYPE);
   iRes=(cmpU(&b, &CTEditBase("POCKETPC"))==0);
   return iRes;
#else 
   return 0;
#endif
}

#include "../os/CTThread.h"

void startThX(int (cbFnc)(void *p),void *data){
   CTThread *th=new CTThread();
   th->destroyAfterExit();
   th->create(cbFnc,data);
}

