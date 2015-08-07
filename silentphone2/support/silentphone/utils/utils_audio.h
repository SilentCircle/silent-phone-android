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

#ifndef _C_AT_AUDO_UT_H
#define _C_AT_AUDO_UT_H

#include <string.h>
//TODO inline memcpy


class CVADetection{//TODO CVADetection
   int iRandom;
   
   int iPrevValue;
   int iSilenceVal;
   int iCurVal;
   
   int iTmp, iTmp2;
   int i;
   int iPrevIsSileceCounter;
   int id;
   int iNoSileceCouter;
   /*
    char *prevBuf;
    char *tmpBuf;

    */
   char prevBuf[4096];
   char tmpBuf[4096];
   
public: 
   int iSendNextNPackets;
   int iDelta;
   int iIsZRTPActive;
   int iCanReplace;
   int iIsPlaybackVad;
   CVADetection(){
      iSendNextNPackets=10;
      iIsPlaybackVad=0;
      iCanReplace=1;
      //prevBuf
      //prevBuf=new char [2048];
     // tmpBuf=new char [2048];
      memset(prevBuf,0,4096);
     // Mem::FillZ(tmpBuf,2048);
      iIsZRTPActive=0;
      resetAll();
      iRandom=0;
   }
   ~CVADetection(){/*delete prevBuf;delete tmpBuf;prevBuf=NULL;*/}
   void resetAll()
   {
      iTmp2=iCurValX=iPrevValue=iMaxValue=iPrevIsSileceCounter=iPrevMax=iNoSileceCouter=id=iSilenceVal=0;
      //iCoef=25;
   //   iNoiseLevel=100;
   }
   void reset(){iMaxValue=iTmp2=iPrevIsSileceCounter=iNoSileceCouter=id=iPrevMax=iSilenceVal=iCurValX=0;;}
   inline int getCurVal() {return iCurValX/1000;}
#ifndef max
#define max(A,B)(A)>(B)?(A):(B)
#endif
   inline void replace(char *p, int iInLen, int iBytes)
   {
      if(iInLen<=iBytes)
      {
         memcpy(tmpBuf,prevBuf,iInLen);//varbuut kopeet 1x un iedot veco pointeri uz buferi
         memcpy(prevBuf,p,iInLen);
         memcpy(p,tmpBuf,iInLen);
         return;
      }

      memcpy(tmpBuf,p+iInLen-iBytes,iBytes);//salabaa pedeejos bytes
      memmove(p+iBytes,p,iInLen-iBytes);

      memcpy(p,prevBuf,iBytes);
      memcpy(prevBuf,tmpBuf,iBytes);//ieliek prev no temp;
   }
   void dbg(int a, int b)
   {/*
      HDC h=GetDC(0);
      char buf[32];
      int l=sprintf(buf,"--%5d--%5d--",a,b);
      TextOutA(h,5,5,buf,l);

      ReleaseDC(0,h);
      */
   }

   inline int isSilence2(char *buf, int iLen)//pcm16
   {
      iCurVal=0;//iCurValX;
      iPrevMax=iCurVal;
      if(prevBuf==NULL)return 1;
      //iCurVal=0;
      iTmp2=iTmp;
      int iC;
      
      for(i=0;i<iLen;i+=2)
      {
         iTmp=*(short *)(buf+i);
//         iCurVal += (labs(65536 * iTmp) - iCurVal) / 10000;//skaluma liimenis
         iC=iTmp+iTmp2;
         iTmp2=iTmp;//abs(iC+abs(iTmp));
         iC*=iC;
         iC>>=15;
         iCurVal += iC;//abs(iC)+abs(iTmp);//*iTmp;//
      }
      if(!iIsPlaybackVad && iCanReplace)replace(buf,iLen,400);//400 = 25 ms

      iCurValX=iCurVal;///iLen*2;

      //int iCoef=iPrevIsSileceCounter?32:8;//symb
#define COEF_A1 150  //jo lielaaks jo vairaak juutiigs tad kad nerunaa  lai paartraukut klusumu bija 60
#define COEF_A2 2 //4 old param
      int iCoef=iPrevIsSileceCounter?COEF_A1:COEF_A2;//symb
#ifndef absx
#define absx(AAA) ((AAA)>=0?(AAA):-(AAA))
#endif
      iDelta=absx(iPrevMax-iCurVal);
      
      if(iDelta>iMaxValue)
      {
         iMaxValue=(iMaxValue+iDelta)/4;
         iCurIsSilence=0;
      }
      else
      {
         iCurIsSilence=(iMaxValue-iDelta*iCoef);
         /*
         if(iDelta<2)
            iDelta=2;
         if(iMaxValue>iDelta*COEF_A1*20)
            iMaxValue=iDelta*COEF_A1*20;*/
         //iMaxValue += (iDelta*150 - iMaxValue) / 10;
         
      }
      dbg(iMaxValue,iDelta);

      if(iCurIsSilence>0)
      {
         iPrevIsSileceCounter++;
         iNoSileceCouter=0;
         //if(iMaxValue>iDelta*3)
           // iMaxValue+=((iDelta*10000)-iMaxValue)/100;
      }
      else
      {
         iNoSileceCouter++;
         iPrevIsSileceCounter=0;
         if(iNoSileceCouter>200)iRandom=0;
         else if(iIsZRTPActive)iRandom=(absx(iTmp+iC))&31;
      }
      if(iIsPlaybackVad){
         return 2-iPrevIsSileceCounter;
      }
      if(iSendNextNPackets>0){iSendNextNPackets--;return 1;}
      if(iPrevIsSileceCounter>10 && (iPrevIsSileceCounter&15)==1)return 0;
         
      return iRandom+4-iPrevIsSileceCounter;
   }

 //  int iCoef;
public:
   int iCurIsSilence;
   int iMaxValue,iPrevMax;
   int iCurValX;


};

#endif //_C_AT_AUDO_UT_H