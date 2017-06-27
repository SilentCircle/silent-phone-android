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
#ifndef _C_T_THREAD_H
#define _C_T_THREAD_H


//#include "windows.h"
//#include "CTiViSock.h"

#ifndef CLOSE_TH
#ifdef _WIN32
  // #define close closesocket
   #define sleep Sleep
   #define THREAD_FNC DWORD WINAPI
   #define SHUT_RDWR SD_BOTH
   #define TH_HANDLE HANDLE
   #define CREATE_TH(H,FNC,PARAM,RET) H=CreateThread(NULL,0,FNC,(void *)PARAM,0,(DWORD *)&(RET))
   #define snprintf _snprintf
   #define CLOSE_TH  CloseHandle

#else
#ifndef __SYMBIAN32__
   #define closesocket close
//   #define Sleep(U) usleep(U*1000);
#define Sleep(U) {if(U>=1000){sleep((U)/1000);unsigned int _t_us_=(U)-(U)/1000*1000;if(_t_us_)usleep(_t_us_);}else usleep((U)*1000);}
   #define THREAD_FNC void *
   #define SOCKET int
   #define UINT unsigned int
   #define USHORT unsigned short
   #define BOOL unsigned int
   #define TH_HANDLE pthread_t
   #define TRUE  1
   #define FALSE 0
   #define SD_BOTH SHUT_RDWR
   #define SetThreadPriority  //
   #define CLOSE_TH  pthread_detach
   #define socklen_t int
   //pthread_create(&thComDataReader, NULL, (void*)thfnComDataReader, NULL);
   #define CREATE_TH(H,FNC,PARAM,RET)  RET=pthread_create(&(H), NULL, FNC, PARAM);
#endif
#endif
#endif

#ifdef __SYMBIAN32__
#include "../utils/utils.h"
//#include <aknappui.h>
//#include <coecntrl.h>
#include <e32std.h>
#include <stdio.h>

void convert8_16(char *c, int iLen);
typedef int (TH_FNC)(void *param);
class CTThread{
   int iDestroyAfterExit;
public:
   void *param;   
   int iInThread,iLastErr;
   char thName[64];

   CTThread():param(NULL),iInThread(0),fnc(NULL),iIsCreated(0),iThRetParam(0),
      thname(0,0),iThCnt(0){thName[0]=0;iLastErr=iDestroyAfterExit=0;}
   ~CTThread(){
      //TODO kill
      close();
   }
   void destroyAfterExit()
   {
      //closeHandle();
      iDestroyAfterExit=1;
   }
   void close()
   {
      
      if(iIsCreated && iInThread){
         wait();
         iInThread=0;
         iIsCreated=0;
         h.Close();
         //CloseHandle(h);h=0;
         //h
      }
   }


   int create(TH_FNC *fn, void *p)
   {
      if(iIsCreated)return -1;
      fnc=fn;
      if(fnc==NULL)return -5;
      param=p;

     // static int aa=0;
      
     // aa++;
      iThCnt++;
      
      
      int iLen=sprintf(sthname,"pth%u%u", User::TickCount()+(int)this, iThCnt+(int)param);
      if(thName[0])iLen+=sprintf(&sthname[iLen],"-%s",thName);
      convert8_16(sthname,iLen);


      thname.Set((unsigned short *)&sthname,iLen);
      //h=CreateThread(NULL,0,CTThread::thFnc,this,0	,&ret); 
#ifndef __SERIES60_30__J
    h.Create(thname,
		   	CTThread::thFnc,
             KDefaultStackSize*2,
             50000,
             2000000,this) ;
// works #error "test thread 2 edition"
#else
    h.Create(thname,
		   	CTThread::thFnc,
             KDefaultStackSize*4,
             NULL,this) ;
#endif
      h.Resume();
    //  h=createThread(&CTThread::thFnc,this);
      iIsCreated=1;//h && ret?1:0;
      return 0;//iIsCreated?0:-3;
   }

   void wait()
   {
      int i=0;
      while(iInThread && i<2000){User::After(1000);i++;}
   }
   RThread &thread(){return h;}

private:
   TH_FNC *fnc; 
   int iIsCreated;
   int iThRetParam;
   RThread h;
   TPtrC16 thname;
   char sthname[128];
   int iThCnt;
   static TInt thFnc(TAny* aPtr)
   {
      CTrapCleanup* cleanupStack = CTrapCleanup::New();
      CTThread *th=(CTThread *)aPtr;
      th->iInThread=1;
      th->iThRetParam=th->fnc(th->param);
      th->iInThread=0;
      th->iIsCreated=0;
      if(th->iDestroyAfterExit)delete th;
      delete cleanupStack;
      return 0;
   }

   //DWORD ret;

};
#else
unsigned int getTickCount();

#if  defined(_WIN32_WCE) ||  defined(_WIN32)
//#include "winbase.h"
#include <windows.h>
#endif

#if defined(__linux__) ||  defined(__APPLE__)
#include <pthread.h>   
#endif
/*
 size_t stacksize;
 pthread_attr_t attr;
 pthread_attr_init(&attr);
 pthread_attr_getstacksize (&attr, &stacksize);
 printf("Default stack size = %li\n", stacksize);
 stacksize = sizeof(double)*N*N+MEGEXTRA;
 printf("Amount of stack needed per thread = %li\n",stacksize);
 pthread_attr_setstacksize (&attr, stacksize);
 */

typedef int (TH_FNC)(void *param);
class CTThread{
   int iDestroyAfterExit;
   int iHasAttr;
#if  defined(_WIN32_WCE) ||  defined(_WIN32) 
#else
    pthread_attr_t attr;
#endif
public:
   int iLastErr;
   void *param;   

   char thName[64];
   CTThread():iInThread(0),iIsCreated(0),iThRetParam(0),ret(0),
   fnc(NULL),h(0),param(NULL){iDestroyAfterExit=0;thName[0]=0;iLastErr=0;iHasAttr=0;}
   ~CTThread(){
      //TODO kill
      close();
   }
   void destroyAfterExit()
   {
      closeHandle();
      iDestroyAfterExit=1;
   }
   void closeHandle()
   {
      if(h)
      {
         CLOSE_TH(h);
         h=0;
      }
   }
   void close()
   {
      
      if(iInThread){
         wait();
         closeHandle();
         iIsCreated=0;
         iInThread=0;
      }
   }
   int setPrior(int i)
   {
    //  if(h)
      //GetCurrentThread()
      return 0;

   }
   int setStackSize(int size){
#if  defined(_WIN32_WCE) ||  defined(_WIN32) 
      return 1;
#else
      size_t stacksize=size;
      //pthread_attr_t attr;
      pthread_attr_init(&attr);
      pthread_attr_getstacksize (&attr, &stacksize);
      printf("Default stack size = %lu\n", (unsigned long int)stacksize);
      stacksize = size;//sizeof(double)*N*N+MEGEXTRA;
      printf("Amount of stack needed per thread = %lu\n", (unsigned long int)stacksize);
      pthread_attr_setstacksize (&attr, stacksize);
      
      iHasAttr=1;
#endif
      
      return 0;
   }


   int create(TH_FNC *fn, void *p)
   {
      if(iIsCreated)return -1;
      fnc=fn;
      if(fnc==NULL)return -5;
      param=p;
      iInThread=0;
#if  defined(_WIN32_WCE) ||  defined(_WIN32)     
      h=CreateThread(NULL,1024*1024,CTThread::thFnc,this,STACK_SIZE_PARAM_IS_A_RESERVATION	,&ret);
      if(!h)iLastErr=GetLastError();
#else
      
      ret=!pthread_create(&h,iHasAttr?&attr:NULL,CTThread::thFnc,(void *)this);
#endif
      iIsCreated=h && ret?1:0;
      
      if(h && iDestroyAfterExit){
         //if we do not call this it creates thread leak and
         //it was happening if the destroyAfterExit() was called before create()
         
         closeHandle();
      }
      
      return iIsCreated?0:-3;
   }
   int iInThread;
   void wait()
   {
     /// while(iInThread)Sleep(1);
      int i=0;
      while(iInThread && i<400){Sleep(5);i++;}	   
   }
private:
#if  defined(_WIN32_WCE) ||  defined(_WIN32)     
   static DWORD WINAPI thFnc(void *p)
#else
   static void *thFnc(void *p)
#endif
   {
#ifdef __APPLE__
      extern void *initARPool();
      void *pool=initARPool();
#endif
      CTThread *th=(CTThread *)p;
      th->iInThread=1;
      th->iThRetParam=th->fnc(th->param);
      th->iInThread=0;
      th->iIsCreated=0;
      if(th->iDestroyAfterExit)delete th;
#ifdef __APPLE__
      void relARPool(void *p);
      relARPool(pool);
      
#endif
      
#if  defined(_WIN32_WCE) ||  defined(_WIN32) 
#else
#endif

      return 0;
   }

   TH_FNC *fnc; 
   int iIsCreated;
   int iThRetParam;
#if  defined(_WIN32_WCE) ||  defined(_WIN32)  
   DWORD ret;
   HANDLE h;
#else
   int ret;
   pthread_t h;
   
#endif
};
#endif //#ifdef __SYMBIAN32__
#endif //_C_T_THREAD_H
