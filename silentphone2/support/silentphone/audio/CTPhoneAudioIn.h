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

#ifndef _C_T_PHONE_AUDIO_IN_O_H
#define _C_T_PHONE_AUDIO_IN_O_H

void uSleep(int usec);
/*
static void uSleep(int usec)//todo utils
{
#ifdef __SYMBIAN32__
   User::AfterHighRes(usec);
#elif defined(_WIN32)
   if(usec<1000)usec=1000;
   Sleep(usec/1000);
#else //mac linux
   usleep(usec);
#endif   
}
*/
/*
static unsigned int getTickCount()
{
#ifdef __SYMBIAN32__
   return User::NTickCount();
#elif define(_WIN32)
   return GetTickCount();
#else //mac linux
   #include <sys/times.h>
   struct tms t;
   return times(&t);
#endif   
}
*/
unsigned int getTickCount();
#include "../utils/CTFifo.h"

class CTPhoneAuidoIn: public CTAudioIn,public CTAudioCallBack{
   CTThread th;
   CTFiFo<320*36> fifo;
   int iBlocksToSend;
   int iBytesToSend;
   int iRecording;
   int iBytesSent;
   CTAudioCallBack *cb2;
   int iFakeFifoBytes;
#ifdef T_USE_NOKIA_G711
   CTG711Alaw gUlaw;   char bufTmpX[10000];
   #endif
public:
   CTPhoneAuidoIn(CTAudioCallBack *cb2, int iBitsPerSample=16, int iRate=8000, int iCh=1)
     :CTAudioIn(this,iBitsPerSample,iRate,iCh),cb2(cb2)
   {
      iBytesSent=0;
      iRecording=0;
      setPayload20msX(6);

      iFakeFifoBytes=0;
   }
   void setPayload20msX(int i)
   {
      iBlocksToSend=i;
      iBytesToSend=320*iBlocksToSend;
   }
   int record(void *p)
   {
      iFakeFifoBytes=0;
      iRecording=1;
      th.create(&th2fnc,this);
      fifo.reset();
      return CTAudioIn::record(p);
   }
   void stop(void *p)
   {
      int _TODO_TEST_ENRTOPY;
      if(!iRecording)return;
      iRecording=0;
      th.close();
      return CTAudioIn::stop(p);
   }
  // char bufx[32000];
   virtual void audioIn(char *p, int iLen, unsigned int uiPos)
   {
      //TODO if(iLen>0 && iLen<iBytesToSend){do not createth ; if(fifo.bytesIn()>=iBytesToSend)send};

      if(0)//p && iLen)
      {
         FILE *f=fopen("e:\\aa.pcm","a++");
         if(f)
         {
            fwrite(p,1,iLen,f);
            fclose(f);
         }
      }
      if(p==NULL)
      {
         cb2->audioIn(NULL,0,(unsigned int)iBytesSent);
      }
      else
      {
#ifdef T_USE_NOKIA_G711
         int iSilence=*(p)!=1;

         int i;
         int val=*(p+20);
         p+=2;iLen-=2;
         for(i=20;i<iLen;i++)
         {
            if(val!=p[i]){iSilence=0;break;}
         }
         if(!iSilence)
         {
         iLen=gUlaw.decode((unsigned char *)p,(short*)&bufTmpX[0],iLen);
         p=(char*)&bufTmpX[0];
         }
         else
         {
            iLen*=2;//=(iLen-2)*2;
            memset(&bufTmpX[0],0,iLen);
            p=(char*)&bufTmpX[0];
         }
#endif

         fifo.add(p,iLen);
      }
   }
   static int th2fnc(void *p)
   {
      ((CTPhoneAuidoIn*)p)->thfnc();
      return 0;
   }
   //iespeejams lai vienkarshaak ieviest sho visiem os
   int iDbg1,iDbg2,iDbg3;
   void thfnc()
   {
//#define T_A_USE_1
#define T_A_USE_TICKS //straadaa
//#define T_A_USE_TIME //straadaa
      unsigned int uiEncodeSendTimeMs=10;
     // TTime now;
      //TTime tOld;
      int iTTs;
      int iCnt;
#ifdef T_A_USE_TIME
      TTime tNow;
      TTime tOld;
      TTimeIntervalMicroSeconds interv;
#endif
     // printf("rec ,",iTTs, iBlocksToSend, uiEncodeSendTimeMs);
      while(iRecording)
      {
         //10 ir encoded un send laiks
         ///TODO if(fifo.bytesIn()>maxIn*1.2) fifo.get(fifo.bytesIn()-maxIn);dropot dalju
         
         iCnt=0;
//printf("f=%d,b=%d, ",fifo.bytesIn(),iBytesToSend);
         while(iCnt<1000 && iRecording && fifo.bytesIn()<iBytesToSend)
         {
            iCnt++;
            //Sleep(1);
            //User::AfterHighRes(500);
            uSleep(500);
            
         }
         if(!iRecording)break;
#ifdef T_A_USE_TIME
         tNow.HomeTime();
        // interv=tNow.Int64();
#endif
#ifdef T_A_USE_TICKS
         uiEncodeSendTimeMs=(unsigned int )getTickCount();
#endif
         cb2->audioIn(fifo.get(iBytesToSend),iBytesToSend,(unsigned int)iBytesSent);
         iBytesSent+=iBytesToSend;
         if(!iRecording)break;
#ifdef T_A_USE_TIME
         tNow.HomeTime();
        // interv=tNow.Int64()-interv;

         interv=tNow.MicroSecondsFrom(tOld);
         uiEncodeSendTimeMs=(unsigned int)interv.Int64();
         uiEncodeSendTimeMs/=1000;

         if(uiEncodeSendTimeMs>1000)uiEncodeSendTimeMs=5;
         iTTs=(iBlocksToSend*20-(int)uiEncodeSendTimeMs)-2; //
#endif

#ifdef T_A_USE_TICKS
         uiEncodeSendTimeMs=getTickCount()-uiEncodeSendTimeMs;
         if(uiEncodeSendTimeMs>1000)uiEncodeSendTimeMs=5;
         iTTs=(iBlocksToSend*20-(int)uiEncodeSendTimeMs)-2; //bija -1
       //  printf("%d %d %d ,",iTTs, iBlocksToSend, uiEncodeSendTimeMs);
        // iDbg1=iTTs;
       //  iDbg2=iBlocksToSend;
         //iDbg3=(int)uiEncodeSendTimeMs;

#endif

#ifdef T_A_USE_1
         iTTs=(20-(int)uiEncodeSendTimeMs)*iBlocksToSend-2;
#endif
         if(fifo.bytesIn()>4000)uSleep(1000);
         else if(iTTs>20 && fifo.bytesIn()>6000)uSleep(5000);
         else if(iTTs>0)uSleep(iTTs*1000);


            //Sleep(iTTs);
         //}

      }
   }

};
#endif
