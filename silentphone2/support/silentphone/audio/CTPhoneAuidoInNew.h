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

#ifndef _C_T_PHONE_AIN
#define _C_T_PHONE_AIN

#include "../utils/CTFifo.h"
#include "../os/CTThread.h"
#include "../utils/CTDataBuf.h"

unsigned int getTickCount();
void uSleep(int usec);

#ifdef ANDROID_NDK
int androidLog(char const *format, ...);
#endif


class CTPhoneAudioInBufControl{
   CTThread th;
   CTFiFo<320*100*2> fifo;//2sec
   int iBytesToSend;
   int iRecording;
   unsigned int uiBytesSent;
   unsigned int uiTimeToWait;
   unsigned int uiLastRecAt;
   unsigned int uiTC;
   int iRate;
   int iSendingWarning;
   char *bufEmpty;
   int iBufEmptySize;
   int iHWBufSize;
public:
   int iErr;
   CTDataBuf warningSound;
   
   CTAudioCallBack *cb2;
   CTPhoneAudioInBufControl(int iRate)
     :cb2(NULL),iRate(iRate)
   {
      iErr=0;
      iHWBufSize=0;
      bufEmpty=NULL;iBufEmptySize=0;
      uiBytesSent=0;
      iRecording=0;
      setPayload20msX(2);
      uiLastRecAt=0;
      uiTC=0;
   }
   ~CTPhoneAudioInBufControl(){stop(NULL);if(bufEmpty)delete bufEmpty;}
   void setPayload20msX(int i)
   {
      if(i>=20){
         i=i/20;
      }
      if(i<1)i=1;
      packetTime(i*20);
   }
   
   void packetTime(int ms){
      if(ms<1)return;
      
      uiTimeToWait=(unsigned int)ms;//  /4;
      iBytesToSend=2*iRate*(int)uiTimeToWait/1000;
      
      if(iBufEmptySize<iBytesToSend){
         if(bufEmpty)delete bufEmpty;
         bufEmpty=NULL;
      }
      if(!bufEmpty){
         iBufEmptySize=iBytesToSend;
         bufEmpty= new char [iBytesToSend];
         memset(bufEmpty,0,iBytesToSend);
      }
   }
   
   int record(void *p)
   {
      if(!iRecording)
      {
         iRecording=1;
         uiLastRecAt=0;
         uiBytesSent>>=3;
         uiBytesSent&=~1;
         th.create(&th2fnc,this);
         fifo.reset();
      }
      
      return 0;
   }
   
   void stop(void *p)
   {
       int t_snprintf(char *buf, int iMaxSize, const char *format, ...);

      if(!iRecording)return;
      iRecording=0;
      th.close();
      uiLastRecAt=0;
//#define DBG_ARR_SZ 250
#ifdef DBG_ARR_SZ
      int iLen=0;
      char b[4000];
      iLen+=sprintf(&b[iLen]," pack-diffs");
      for(int z=0;z<DBG_ARR_SZ;z++){
         iLen+=sprintf(&b[iLen],"%d,",uiTC_Arr[z]);
         if((z&15)==15)iLen+=sprintf(&b[iLen],"\n");
      }
      iLen+=sprintf(&b[iLen],"\n enctime");
      for(int z=0;z<DBG_ARR_SZ;z++){
         iLen+=sprintf(&b[iLen],"%d,",uiEncTimeArr[z]);
         if((z&15)==15)iLen+=sprintf(&b[iLen],"\n");
      }
      void tmp_log(const char *p);
      tmp_log(&b[0]);
#endif
      return ;
   }

   void audioIn(char *p, int iLen, unsigned int uiPos)
   {
/*
      if(p && iLen)
      {
         FILE *f=fopen("c:\\aa.pcm","a++");
         if(f)
         {
            fwrite(p,1,iLen,f);
            fclose(f);
         }
      }
      */
      if(p==NULL)
      {
         if(cb2)cb2->audioIn(NULL,0,(unsigned int)uiBytesSent);
      }
      else
      {
         if(!iHWBufSize)iHWBufSize=iLen;else iHWBufSize=(iHWBufSize+iLen+1)>>1;
         fifo.add(p,iLen);
      }
      uiLastRecAt=uiTC;
      iSendingWarning=0;
      
#ifdef DBG_ARR_SZ
      static int prevtc=0;
      unsigned int tc=getTickCount();
      if(posA>=DBG_ARR_SZ)posA=0;
      uiTC_Arr[posA]=tc-prevtc;prevtc=tc;posA++;
#endif
      
   }
#ifdef DBG_ARR_SZ
   int posA,posB;
   unsigned int uiTC_Arr[DBG_ARR_SZ];
   unsigned int uiEncTimeArr[DBG_ARR_SZ];
#endif
   
private:
   static int th2fnc(void *p)
   {
      ((CTPhoneAudioInBufControl*)p)->thfnc();
      return 0;
   }

   int iDbg1,iDbg2,iDbg3;
   
   void thfnc()
   {

      int iTTs;
      int iCnt;
#if defined (__SYMBIAN32)
      RThread().SetPriority(EPriorityRealTime);
#endif
      
//       pthread_t th=pthread_self();
  //     pthread_setschedprio(th, 10);
      
      unsigned int uiStartTime=getTickCount();
      unsigned int uiNextFrameAt=uiStartTime+uiTimeToWait;
      unsigned int uiNoDataStart;
      
      int iWasData=0;
      int iEncTime=0,iMaxEncTime=0,iEncJit=0;
      
      iSendingWarning=0;
      int iSendSilence=0;
      
      uiTC=uiStartTime;
      iErr=0;
      fifo.reset();
      uSleep(2*1000);
      
      while(iRecording)
      {
         iCnt=0;
         while(iRecording && !iSendingWarning && fifo.bytesIn()<iBytesToSend)
         {
            iCnt++;
#ifdef _WIN32
            uSleep(2000);//no real usleep in win32
#else
            uSleep(800);
#endif
            uiTC=uiStartTime=getTickCount();
            if(iCnt==1)uiNoDataStart=uiTC;
            uiNextFrameAt=uiStartTime+uiTimeToWait;
            if(iCnt>10){
               int d=(int)(uiTC-uiLastRecAt);
               if(d>1000 && d<100000 && iWasData && warningSound.getLen()){
                  iWasData=0;
                  iSendingWarning = 1;
                  warningSound.reset();
                  iSendSilence=iRate;
                  //FILL data with some info
                  //TODO clock, do send someting that audio was interupted
               }
            }
            
         }

         unsigned int uiEncStart=getTickCount();
         if(iCnt){
            int d=(int)(uiEncStart-uiNoDataStart);
            if(d>1000){
               d-=iBytesToSend;
               uiBytesSent+=((iRate/1000*d)*2);
            }
            uiNextFrameAt+=2;//???????????
         }
         if(!iRecording)break;

         char *data = NULL;
         if(!iSendingWarning){
            data = fifo.get(iBytesToSend);
            iWasData=1;
            iSendSilence=0;
         }
         else{
            if(iSendSilence>iBytesToSend){
               iSendSilence-=iBytesToSend;
               data=bufEmpty;
               
            }
            else data = warningSound.get(iBytesToSend);
            if(!data){
                iSendingWarning=0;
                uSleep(15000);
                continue;
            }
         }

         int idbg=0;
#if defined(__APPLE__)
         {
            static int ix;
            ix++;
             if((ix&255)==1){idbg=1; printf("[fifo.bytesIn()=%d err=%d enc=%d max_enc=%d j=%d]\n",fifo.bytesIn(),iErr,
                                            iEncTime, iMaxEncTime, iEncJit);}
//             if ((ix&255)==1) {
//                 idbg=1;
//                  androidLog("++++ [fifo.bytesIn()=%d err=%d enc=%d max_enc=%d j=%d]",fifo.bytesIn(),iErr, iEncTime, iMaxEncTime, iEncJit);
//                  androidLog("++++ iSendingWarning: %d iBytesToSend: %d, uiBytesSent: %d, iTTs: %d", iSendingWarning, iBytesToSend, uiBytesSent, iTTs);
//             }
         }
#endif
         if(cb2) {
             cb2->audioIn(data,iBytesToSend,uiBytesSent);
//             diffCounter++;
         }
         
         uiBytesSent+=(unsigned int)iBytesToSend;
         if(!iRecording)break;
         uiTC=getTickCount();

#if 0
         iTTs=(int)(uiNextFrameAt-uiTC);
         uiNextFrameAt+=uiTimeToWait;
         if(iTTs>0)uSleep(iTTs*1000-300);
#else
         iEncTime= (int)(uiTC-uiEncStart);//enc send time

#ifdef DBG_ARR_SZ
         if(posB>=DBG_ARR_SZ)posB=0;
         uiEncTimeArr[posB]=iEncTime+idbg*1000;posB++;
#endif
         if(!idbg && iEncTime>iMaxEncTime)iMaxEncTime=iEncTime;
         if(iEncTime>iEncJit) iEncJit = (iEncJit*3+iEncTime+2)>>2; else iEncJit = (iEncJit*31+iEncTime+16)>>5;
         
         iTTs=(int)uiTimeToWait-iEncTime-1;//calc sleep time ms
         
#if 1
         //TODO if(fifo.bytesIn()>iHWBufSize+iBytesToSend*2)removeSomeSilence(fifo);
            
         if(fifo.bytesIn()>iRate*3) {
             fifo.reset();
             iErr|=4;
             printf("[ai err4]");

         }
         
         //iPod touch fix - sometimes encode time > uiTimeToWait
         iTTs>>=1;//wait 50% time
         
         //why ????? incoming calls only ????????
         //sometimes on Android 5.0 the encoding time is more than 20ms up to 80ms
         //and we shoud send data out as fast as we can
         //Android 5.0 incoming calls - sometimes encode time > uiTimeToWait*4 ???
         
         // If the buffer does not contain enough data wait some time. iTTs can go negativ if 
         // the encoding takes too long. In this case the fifo usually contains enough data and
         // we don't enter the while, however, just in case, wait some defined time.
         int z = 0;
         while (fifo.bytesIn() < iBytesToSend && iRecording && z < uiTimeToWait) {
             if (iTTs > 0) {
                 uSleep(iTTs*1000-300);
                 z += iTTs;
             }
             else {
                 uSleep(((int)uiTimeToWait >> 1) * 1000 - 300);
                 z += (int)uiTimeToWait >> 1;
             }
         }
#else
         int bi=fifo.bytesIn();
         //send data faster if we have to many bytes in queue
         if(bi*4>iHWBufSize*5 && iTTs>0){
            if(bi>iBytesToSend*4){iTTs--;iTTs*=3;iTTs>>=2;iErr|=2;}
            else if(bi>iBytesToSend*2){iTTs--;iTTs*=7;iTTs>>=3;iErr|=1;}
            
            if(bi>iRate*2){fifo.reset();iErr|=4;}
         }
         if(iTTs>0)uSleep(iTTs*1000-300);
#endif

#endif


      }
   }
};
#endif
