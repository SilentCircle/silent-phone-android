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
#ifndef _C_T_AUDIO_ANDROID_H
#define _C_T_AUDIO_ANDROID_H

#include<sys/times.h>
#include "../baseclasses/CTBase.h"


static unsigned int getTickCountOsn()
{
   struct timeval v;
   if(gettimeofday(&v,0)==0)
   return v.tv_sec*1000+
   (v.tv_usec>>10);//*1000; 
   return 0;
}
#define getTickCount getTickCountOsn
static void uSleep(int a){usleep(a);}
#include "CTPhoneAuidoInNew.h"

class CTAudioIn: public CTAudioInBase{
   
   int iRec;
   CTPhoneAudioInBufControl ac;
public:
   
   CTAudioCallBack *cb;

   CTAudioIn(CTAudioCallBack *cb, int iBitsPerSample=16, int iRate=8000, int iCh=1)
      :cb(cb)
   {
      pos=0;
      iRec=0;
      ac.setPayload20msX(4);
   }
   int init(void *g=NULL)
   {
      return 0;
   }
   ~CTAudioIn()
   {
   }
   unsigned int pos;
   int sendAudioFrame(short *samples, int frames){
      frames<<=1;
      if(!iRec)return -1;
      //cb->audioIn((char*)samples,frames,pos);
      ac.audioIn((char*)samples,frames,pos);
      pos+=frames;
      return 0;
   }
   static int cbAudioIn(void *callback_data, short *samples, int frames) 
   {
    //  printf("get Next ");
      //int ret=((CTAudioIn *)callback_data)->getNextBuf(samples,frames);
//      if(((CTAudioOut *)callback_data)->iNeedStop)return -1;
      //((CTAudioOutM *)callback_data)->getNextBuf(samples+1,frames);
      return ((CTAudioIn *)callback_data)->sendAudioFrame(samples,frames);
   }

   inline int getType(){return CTAudioInBase::ePCM16;}
   inline int  getRate(){return 8000;}
   int record(void *p)
   {
      if(iRec)return 0;
      iRec=1;
      void t_native_startCB_rec(int (*cbFnc)(void *, short *, int ), void *cbData);
      t_native_startCB_rec(&cbAudioIn,this);

      ac.cb2=cb;
      ac.record(p);

      return 0;
   }
   void stop(void *p)
   {
      iRec=0;
      ac.stop(p);
      void t_native_stop_rec();
      t_native_stop_rec();
   }
};

class CTJit{
public:
   unsigned int uiPrevPackTs;
   unsigned int uiPrevPos;
   unsigned int uiLastJitTS;
   int iJit;
   double dJit;
   int iMaxJit;
   int iBytesToSetBuf;

   void resetJit()
   {
      //uiLastTS=0;
      iBytesToSetBuf=1200;
      uiPrevPos=uiPrevPackTs=0;
      iMaxJit=iJit=20;
      dJit=0.0;
   }
   inline int mabs(int a){return a>=0?a:-a;}
   void checkJit(char *p, int iLen, unsigned int uiPos, int iCanSetPos, int iRate, int &iMaxBuf, int &iMinBuf, int &iResetPosNow)
   {
      
      if(iLen>8000 || !iCanSetPos || !uiPos)return ;
      uiPos/=2;
      unsigned int uiCurTs=uiLastJitTS=(unsigned int )getTickCountOsn();//User::NTickCount();//GetTickCount();
      if(uiPrevPackTs && p)
      {
         
         int iDelta;
         if(iRate==8000)
            iDelta=(int)(uiPos>>3)-(int)(uiPrevPos>>3);
         else 
            iDelta=((int)uiPos-(int)uiPrevPos)*1000/iRate;
         int iDeltaTime=(int)(uiCurTs-uiPrevPackTs);
         if(iDeltaTime>=2000 || iDeltaTime<-5000)
         {
            iResetPosNow=1;
         }
         if(iDelta<500 && iDeltaTime<2000 && iDeltaTime>=0 && iDelta>0)
         {

          //s->jitter += d - ((s->jitter + 8) >> 4);
          //s->jitter += (1./16.) * ((double)d - s->jitter);
        // iJit+=abs(iDeltaTime-iDelta)-((iJit+8)>>4);
         //iJit+=(((abs(iDeltaTime-iDelta)<<4)-((iJit)))>>4);
         int ix=mabs(iDeltaTime-iDelta);
         if(ix>iJit)
         {
            iJit=(ix+iJit)>>1;
         }
         else
            iJit-=mabs(ix-iJit)>>5;
         //dJit+=((1./16.) *((double)mabs(iDeltaTime-iDelta)-dJit));

         int res=iJit*2;//>>4;///4;

         if(res>iMaxJit)
            iMaxJit=res;
         else
            iMaxJit-=(iMaxJit+res)/32;
         if(iMaxJit>1500)
            iMaxJit=1500;

         res=iMaxJit;
        //res+=iDelta;
         //iMaxBufferSize=((int)res+80)*iRate/500;//iDelta*2;
         iMaxBuf=iRate*7/4;//((int)res+80)*iRate/500;//iDelta*2;

         iBytesToSetBuf=((int)res+20)*iRate/500;;
        // iMaxBufferSize=((int)res)*iRate/500+iDelta*2;
         iMinBuf=iRate/40;//iMaxBufferSize/8;//0;//iRate/10;//iJit*iRate/1000;//*2;
         //if(iMaxBufferSize>iRate*3/2)iMaxBufferSize=iRate*3/2;

//         char buf[128];sprintf(buf," %5u %5u jd=%4u ji=%4u m=%4u",iDeltaTime,iDelta,(int)dJit, res ,iMaxJit);deb((char*)buf);
#if 0//_CONSOLE
         printf(" %5u--%5u %5u  jit=%4u  jit=%4u  ",iDeltaTime,iDelta, res, (int)dJit, iJit);
#endif
         }
      }
      uiPrevPos=uiPos;
      uiPrevPackTs=uiCurTs;

   }

};

class CTAudioOut: public CTAudioOutBase,public CTJit{
   int iMSecPlay,iNeedStop;
   int iMachineRate;
  // AUDIO_OUT 
   void *ao;
   int iInitOk;
   int iRate;
   int iBufSize;
   unsigned int uiPlayPos,uiLastGetMS;
   short *sbuf, *bufTmp;  
   //CTIncDecFreq incDecFreq; 
public:
   CTFiFo<320*2*3*4> fifoBuf;
   void setVolume(int i){}
   CTAudioOut(int iBitsPerSample=16, int iRate=8000, int iCh=1, int id=0,int flag=0)
   :iRate(iRate),iMachineRate(8000)//44100)
   {
   
      uiLastGetMS=0;
      iMSecPlay=0;
      iNeedStop=1;
      //uiLastGetPos=0;
      
      iInitOk=0;ao=NULL;
      init(NULL);
      iBufSize=iMachineRate*2*6;//6sec
      uiFPos=0;
      uiPlayPos=0;//(unsigned int)(iBufSize-iMachineRate*2);
      sbuf=new short[iBufSize];
      bufTmp=new short[iBufSize];
      //iBufSize*=2;      
   }
   static int cbAudio(void *callback_data, short *samples, int frames) 
   {
    //  printf("get Next ");
      int ret=((CTAudioOut *)callback_data)->getNextBuf(samples,frames);
      ((CTAudioOut *)callback_data)->fifoBuf.add((char*)samples,frames*2);
//      if(((CTAudioOut *)callback_data)->iNeedStop)return -1;
      //((CTAudioOutM *)callback_data)->getNextBuf(samples+1,frames);
      if(ret<0)ret=0;
      return frames;
   }
   int getNextBuf(short *samples, int frames) 
   {
     if(iMSecPlay!=-1)
     {
       iMSecPlay-=frames;
       if(iMSecPlay<0){stop();return -1;}
       
     }
     //int iBufPos=(int)uiPlayPos;

//-----------
/*
      incDecFreq.setFreq(iRate,iMachineRate*2);//stereo
      
      
         iLen =incDecFreq.doJob((short*)p,&bufTmp[0],iLen/2);
         p=(char*)&bufTmp[0];
         iLen*=2;
         //pos to convert
         //converted bytes;
         

*/
//------------     
     short *p=sbuf+uiPlayPos/2;
     short *pEnd=sbuf+iBufSize/2;
     unsigned int uiNewPos=uiPlayPos;
     uiLastGetMS=getTickCountOsn();
     uiNewPos+=(unsigned int)(frames*2);
     uiNewPos%=(unsigned int)iBufSize;
     uiPlayPos=uiNewPos;
     //uiLastGetPos=uiNewPos;
     int iDraw=0;
     while(frames>0)
     {
        frames-=1;
        //float pin=(float)*p;
        //float res=pin/32768.0f;
        *samples=*p;//*(samples+1)=res;
        if(p<pEnd)p++;else {p=sbuf;iDraw=1;}
        samples+=1;


     }
     //static int ui
     
     //static int x;x++;if((x%4)==0)printf("(play pos %d,%d wpos %d  %d)\n", uiPlayPos/44/4,of/2,uiWPosLast/44/4,iDraw);
     
     
     return 0;
   }   
  // unsigned int uiWPosLast;
   void rel()
   {
      iMSecPlay=-1;
      //if(ao)audio_close(ao);
      void t_native_stop();t_native_stop();
      ao=NULL;
      iInitOk=0;
   }
   int init(void *p)
   {
      if(iRate==16000)return 0;
      if(iInitOk)return 0;
      //ao=audio_open(2,iMachineRate);
      iInitOk=1;//ao?1:0;
      return 0;
      
   }
   int uiFPos;
   short sbufF[10000];
   void constr()
   {
   }
   ~CTAudioOut()
   {
      rel();if(sbuf)delete sbuf; if(bufTmp)delete bufTmp;
   }
 //  unsigned int uiWPosLast;

   inline int getRate(){return 8000;}
   inline int getDirectRate(){return iMachineRate*2;}

  // CTMutex mu;
   void stopAfter(int iMs=-1)
   {
      if(iMs!=-1)
      {
         iMSecPlay=iMs*getDirectRate()/1000;
      }
      else
      {
         iMSecPlay=-1;
      }
   }
   int iSetPosNow;
   int play()
   {
     iIncDecFreq=0;
   //   if(iRate==16000)return 0;
//     int i;
     iMSecPlay=-1;

     if(iNeedStop)
     {
        iNeedStop=0;
        resetJit();
         init(NULL);
         fifoBuf.reset();
       //puts("play2");
         //audio_play(&cbAudio,ao,this);        
         void t_native_startCB(int (*cbFnc) (void *callback_data, short *samples, int frames), void *cbData);
         t_native_startCB(&cbAudio,this);
     }
      return 0;
   }
   inline int playing(){return iNeedStop==0;}
   void stop()
   {
      if(iMSecPlay>0)return;//TODO test
      if(iNeedStop==0)
      {
         iNeedStop=1;
         rel();
      }
   }
   int waitBuffers()
   {
      return 0;
   }
//#include <math.h>
   inline int update(char *p, int iLen, unsigned int uiPos)
   {
//   printf("data\n");
      return update(p, iLen,  uiPos, 1);
   }
    //  checkJit(p,iLen,uiPos,iCanSetPos,iRate,iMaxBufferSize,iMinBufferSize,iSetPosNow);
   unsigned int uiWPosLast;
   int bufBytes()
   {
      //--test--int dif=uiLastJitTS-uiLastGetMS;dif*=16;
      //--test--unsigned int uiPP=uiPlayPos+dif;

      if(uiWPosLast>uiPlayPos)return uiWPosLast-uiPlayPos;
      return uiWPosLast +(unsigned int)iBufSize-uiPlayPos;
     // return 0;
   }
   
   int update(char *p, int iLen, unsigned int uiPos, int iCanResetPos)
   {
      return updateFreqOk(p,iLen,uiPos,iCanResetPos);
      /*
     
     //iMachineRate=4000;
     //iRate,iMachineRate*2
     int iOutRate=iRate+iIncDecFreq;
      //incDecFreq.setFreq(iOutRate,iMachineRate*2);//stereo
     
      
      double d=(double)iMachineRate*2/(double)iOutRate;
      //d=(double)uiPos*d;
     // d+=0.5f;
      d*=100.f;
      
      if(uiPos<1000)
         uiPos=(int)d*uiPos/100;
      else
         uiPos=(int)d/100*uiPos;
         
         
      
      if(iLen && p)
      {
        // int iLen0=iLen;
         iLen =incDecFreq.doJob((short*)p,&bufTmp[0],iLen/2);
         p=(char*)&bufTmp[0];
         iLen*=2;
         if(uiPrevFreqPos+iLen>uiPos-2 && uiPrevFreqPos+iLen<uiPos+2)
         {
           uiPos=uiPrevFreqPos+iLen;
         }
         uiPrevFreqPos=uiPos;
      }
      */
      //return updateFreqOk(p,iLen,uiPos,iCanResetPos);
   }
   unsigned int uiPrevFreqPos;
     int iMaxBufferSize;//=iMachineRate*4;
     int iMinBufferSize;//=iMachineRate/10;
   //  int iSetPosNow;
   int updateFreqOk(char *p, int iLen, unsigned int uiPos, int iCanResetPos)
   {
   //puts("a");
     //uiPos=uiWPosLast+(unsigned int )iLen;
     //int iSetPosNow=0;
     if(iCanResetPos)checkJit(p,iLen,uiPos,iCanResetPos,iMachineRate,iMaxBufferSize,iMinBufferSize,iSetPosNow);
     uiPos%=(unsigned int)iBufSize;
     short *s=(short *)p;
    // printf("%p,%d,%u\n",p,iLen,uiPos);
    // puts("b");
//     if(iLen==0)iLen=iBufSize;
     if(iLen>iBufSize)iLen=iBufSize;
  //   printf("update %d %d\n", iLen, uiPos);
     //iLen/=2;
     short *pWrite=sbuf+uiPos/2;
     short *pEnd=sbuf+iBufSize/2;
     int i=iLen?(iLen/2):(iBufSize/2);

     if(!p){
        while(i>0)
        {
           i--;
           *pWrite=0;
           if(pWrite<pEnd)pWrite++;else pWrite=sbuf;
        }
     }
     else{
        while(i>0)
        {
           i--;
           *pWrite=p?*s:0;
           if(pWrite<pEnd)pWrite++;else pWrite=sbuf;
           s++;
        }
        if(p && iLen)
          uiWPosLast=uiPos;
     }
     static int x;x++;
     int ibuf=bufBytes();
     
       // printf("buf %d, %d %d (bs=%d,p=%u %p)\n",ibuf, iMinBufferSize, iMaxBufferSize,iBufSize,uiPos,p);
     if(iCanResetPos && (iSetPosNow || (p && iLen && ( iMinBufferSize>ibuf || ibuf>iMaxBufferSize))) || (p==NULL && iLen==0 && uiPos))
     {
        iSetPosNow=0;
        //uiPlayPos-=(unsigned int)iBytesToSetBuf;
        //--printf("buf %d,set %d %d (bs=%d,pp=%u,p=%u %p %d %d s=%d)\n",ibuf, iMinBufferSize, iMaxBufferSize,iBufSize,uiPlayPos,uiPos,p, iLen, uiPos,iBytesToSetBuf);
        
        if(uiPos>=(unsigned int)iBytesToSetBuf)
           uiPlayPos=uiPos-(unsigned int)iBytesToSetBuf;
        else
           uiPlayPos=uiPos+(unsigned int)iBufSize-(unsigned int)iBytesToSetBuf;
           
     }
     if(0)//p && uiPos && iLen)
     {
     printf("buf %7d -> %7d ", ibuf, iBytesToSetBuf);
     iIncDecFreq=(ibuf-iBytesToSetBuf)/4;
     if(iIncDecFreq<-300)iIncDecFreq=-300;
     else if(iIncDecFreq>300)iIncDecFreq=300; 
     /*
     if(ibuf<iBytesToSetBuf){
       //eat silence
       iIncDecFreq-=40;
       if(iIncDecFreq<-100)iIncDecFreq=-100;
     }else if(ibuf>iBytesToSetBuf)
     {
       iIncDecFreq+=(ibuf-iBytesToSetBuf)/4;
       //insert silece
       //if(iIncDecFreq>200)iIncDecFreq=200;
     }
     */
     }

    // printf("(w pos %d) \n", uiPos/44/4);
     
     return 0;
   }
   int iIncDecFreq;
   inline int getBufSize(){return iBufSize;}
   //MMTIME mmt;
   inline int getPlayPos()
   {
      return uiPlayPos;
   }
   inline char *getBuf(){return (char *)sbuf;}
   inline void setPlayPos(int iPos)
   {
    uiPlayPos=(unsigned int)iPos;
   }
   inline short *getLastPlayBuf()
   {
      return sbuf;
   }
protected:
  // int iBufSize;
  /*
   int iCurentPlayPos;
   inline int calcBuffered(unsigned int uiPos)
   {

      int ret;
      if(uiPos>(unsigned int)iBufSize)
         uiPos%=(unsigned int)iBufSize;

     // PatBlt(hdc,10,35,220,10,WHITENESS);
      if(uiPos>=(unsigned int)iCurentPlayPos)
         ret=(int)uiPos-iCurentPlayPos;
      else
      {
         ret=(int)uiPos+iBufSize-iCurentPlayPos;
      }


      return ret; 
   }
//   FIR_HP13 filter;
   //(*p2)=(short)ir1.highpass((short)*p2);
   inline void memCpyF(short *s1, short *s2, int iSamples)
   {
      if(iSamples>100000)return;
      while(iSamples>=0)
      {
//         *s1=*s2;//(short)filter.highpass(*s2);
         *s1=*s2;//(short)filter.highpass(*s2);
         s1++;
         s2++;
         iSamples--;
      }
   }

*/
};
//#endif //os

#include "../utils/utils.h"
static void beepAudio(CTAudioOut  & ao, int iFreq, char *szRawFn)
{

      unsigned int uiBufSize=ao.getBufSize();
      int iLen;

      if(szRawFn)
      {

         char *pd=loadFile(szRawFn,iLen);
         if(pd && iLen>0)
         {
         
            //memcpy(p,pd,min((int)uiBufSize,iLen));
            
            
            if(iLen<(int)uiBufSize)
            {
               //memset(p+iLen,0,(int)uiBufSize-iLen);
               ao.update(NULL,0,0,0);
            }
            ao.update(pd,iLen,0,0);
            delete pd;
            ao.setPlayPos(0);
            ao.setVolume(100);
            ao.play();

            ao.stopAfter(iLen*500/ao.getRate()+200);
       
         }
         else szRawFn=NULL;

         
      }

      if(szRawFn==NULL)
      {
         ///memset(p,0,uiBufSize/6);
         uiBufSize=ao.getDirectRate()*2;
         char *p=ao.getBuf();//new char[uiBufSize];
         memset(p,0,uiBufSize);
         genereteTone((float)iFreq,(float)iFreq+50.0f,ao.getDirectRate(), 120, uiBufSize/60,(uiBufSize/6),p,(uiBufSize/6));
//         genereteTone((float)iFreq,(float)iFreq+50.0f,ao.getRate(), 127, uiBufSize/60,(uiBufSize/6),p,(uiBufSize/2));
         //ao.update(NULL,0,0,0);//clear
         //ao.update(p,uiBufSize,0,0);
         //delete p;
         ao.setPlayPos(0);
         ao.setVolume(100);
         ao.play();
         ao.stopAfter(400);
      }

}

#endif
//_C_T_AUDIO_ANDROID_H
