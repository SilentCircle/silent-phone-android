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
#ifndef _C_T_VIDEO_IN_IOS_H
#define _C_T_VIDEO_IN_IOS_H

#include "../baseclasses/CTBase.h"

template<int TMaxCnt>
class CTIdCheck{ //TODO fix name, create file, use for audioIn
   void *idArray[TMaxCnt];
public:
   
   CTIdCheck(){
      memset(idArray,0,sizeof(idArray));
   }
   
   int add(void *id)
   {
      if(!id){
         return 0;
      }
      for(int i=0;i<TMaxCnt;i++){
         if(idArray[i]==id)return 0;//TODO test new
      }
      
      for(int i=0;i<TMaxCnt;i++){
         if(!idArray[i])
         {
            idArray[i]=id;
            return 0;
         }
      }
      return -1;
   }
   
   int rem(void *id)
   {
      for(int i=0;i<TMaxCnt;i++){
         if(idArray[i]==id)
         {
            idArray[i]=0;
            return 0;
         }
      }
      return -1;
   }
   
   inline int isEmpty()
   {
      for(int i=0;i<TMaxCnt;i++)
         if(idArray[i]){return 1;}
      return 0;
   }
};



class CTVideoInIOS: public CTVideoInBase{
   void *ptr;
   int w,h;
   unsigned char *buf;
   int iStarted,iEnding;
   int iVideoFrameEveryMs;
   
   CTIdCheck<12> idArray;
   
public:
   CTVideoCallBack *cb;
   CTVideoInIOS(){iEnding=0;iVideoFrameEveryMs=80;buf=NULL;iStarted=0;w=h=0;cb=NULL;ptr=NULL;}
   ~CTVideoInIOS();

   int init(void *hParent);

   void setQWview(void *p){
      ptr=p;
      init(NULL);
   }

   
   int start(void *pData);
   void stop(void *pData);
   void setXY(int x, int y);   

   unsigned int onNewVideoData(int *d, unsigned char *yuv, int nw, int nh, int iRotDeg);
   void sendBuf(unsigned int uiPos);
  
   
   void setVideoEveryMS(int i){iVideoFrameEveryMs=i;}
   
   int getXY(int &x , int &y){
      x=w;y=h;
      return 0;
   }
private:
   void setXY_priv(int x, int y, int iRot);
   
};

#endif
