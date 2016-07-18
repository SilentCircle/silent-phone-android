/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2016, Silent Circle, LLC.  All rights reserved.

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

#ifndef C_T_VIDEO_IN_OUT_ANDROID_H
#define C_T_VIDEO_IN_OUT_ANDROID_H

unsigned int getTickCount();

class CTServVideoOut: public CTVideoOutBase{
   int w,h;
   unsigned uiPosTs;
   int iStarted;
   unsigned int *buf;
   unsigned int *bufToDelete;
   int iClearBufFlag;
   
public:
   CTServVideoOut(){g_sxy=sxy_store;w=h=0;iStarted=0;bufToDelete=buf=NULL;iLastFrameID=0;iClearBufFlag=0;}
   ~CTServVideoOut(){iStarted=0;delete buf;if(bufToDelete)delete bufToDelete;}
   int iLastFrameID;
   int sxy_store[16];
   int *g_sxy;

   int getFrame(int iPrevID, int *i, int *sxy){
      g_sxy[0] = sxy[0];
      g_sxy[1] = sxy[1];
      g_sxy[2] = sxy[2];
      g_sxy[3] = sxy[3];

      sxy[2]=iLastFrameID;
      
      if(!iStarted || iPrevID==iLastFrameID){
         return iPrevID;
      }
      if(!w || !h)return -1;
      if(sxy[0]!=w || sxy[1]!=h){
         sxy[0]=w;sxy[1]=h;
         return -2;
      }
      
      if(iClearBufFlag){
         iClearBufFlag=0;
         memset(buf,0,w*h*sizeof(int));
      }
      //return iPrevID;
      
      memcpy(i,buf,w*h*sizeof(int));
      return iLastFrameID;
   }
   virtual int getImgParams(int &iYuv, int &iBpp, int &stride, int &iIsBRG, void **p){
      if(!g_sxy || g_sxy[3]!=16)return -1;
      iYuv=0;iBpp=16;stride=w*2;iIsBRG=1;
      //TODO test speed
      *p=&buf[0];
      return 0;
   }
   
   void setQWview(void *p){}
   
   int rel(){return 0;}
   int init(){return 0;}
   
   virtual int start(){iStarted=1;uiPosTs=0;iClearBufFlag=1;return 0;}
   virtual void stop(){iStarted=0;}
   virtual int onData(unsigned char *p, int iLen, unsigned int uiPos){
      //uiPosTs=uiPos;
      //printf(" onData");
      return 0;
   }
   virtual void setTimeStamp(unsigned int uiPos){uiPosTs=uiPos;}
   virtual void setScanLine(int iLine, int iXOff, unsigned char *p, int iLen, int iBits){
      int i;
      if(iLine>h || !buf || iXOff>w)return;
      if(1){//iBpp==16){
         unsigned short *pix=(unsigned short *)buf+iLine*w+iXOff;
         for(i=0;i<iLen;i+=3){
#ifndef RGB888to565
#define RGB888to565(R,G,B) (((R&0xf8)<<8)|((G&0xfC)<<3)|(B>>3))
#endif
            
            *pix=RGB888to565(p[2],p[1],p[0]);
            pix++;p+=3;
         }
         
      }
      else{
         unsigned int *pix=buf+iLine*w+iXOff;
         for(i=0;i<iLen;i+=3){
            *pix=p[0]|((unsigned int)p[1]<<8)|((unsigned int)p[2]<<16);
            pix++;p+=3;
         }
      }
   }

   virtual void setScanLine(int iLine, unsigned char *p, int iLen, int iBits){setScanLine(iLine,0,p,iLen,iBits);}

   virtual void setOutputPictureSize(int cx, int cy){
      if(w==cy && h==cx){
          w=cx;h=cy;
      }
      else if(w!=cx || h!=cy){
           w=cx;h=cy;
           if(bufToDelete) 
               delete bufToDelete;
           bufToDelete=buf;
           buf=new unsigned int[(w+4)*(h+4)];
       }
   }
   virtual void startDraw(){}
   //CTVideoOutFB fb;
   virtual void endDraw(){
      iClearBufFlag=0;
      iLastFrameID++;if(iLastFrameID>1000)iLastFrameID=1;if(g_sxy)g_sxy[2]=iLastFrameID;
   }
};

#if 1
#include "CTVideoInC.h"
#else

class CTServVideoIn: public CTVideoInBase{
   int iStarted;
   unsigned char *buf;
   int w,h;
   unsigned int uiPos;
   unsigned int uiPosPrev;
   int iFrameEveryMS;
public:
   CTVideoCallBack *cb;
   CTServVideoIn(){iFrameEveryMS=120;uiPosPrev=uiPos=0;cb=NULL;iStarted=0;buf=NULL;w=h=0;setXY(176,144);}
   ~CTServVideoIn(){delete buf;buf=NULL;}
   
   virtual int start(void *pData){iStarted=1;return 0;}
   virtual void stop(void *pData){iStarted=0;}
   
   void setVideoEveryMS(int i){iFrameEveryMS*=7;iFrameEveryMS>>=3;}
   void setQWview(void *p){}
   
   virtual int init(void *hParent){return 0;}
   virtual int getXY(int &x , int &y){x=w;y=h;return 0;}
   virtual void setXY(int x, int y){}
   
   void onNewVideoData(int *d, unsigned char *yuv, int nw, int nh, int angle){
      if(!iStarted){
         void tmp_log(const char *p);
         tmp_log("v ! iStarted");
         return;
      }
      
      uiPos=(getTickCount()&~1);
      int dif=(int)(uiPos-uiPosPrev);
      if(dif<0)dif=-dif;
      if(dif<iFrameEveryMS)return;
      
      if(angle == 0 || angle == 180){
         setXY_priv(nw,nh);
         int i,sz=w*h;
         unsigned char *p = buf;
         unsigned char *pi = (unsigned char*)d;
         if (angle == 0) {
             for(i = 0; i < sz; i++){
                 p[0] = pi[0];
                 p[1] = pi[1];
                 p[2] = pi[2];
                 p+=3; pi+=4;
             }
         }
         else {
             p += sz * 3;
             for (i = 0; i < sz; i++) {
                 p -= 3;
                 p[0] = pi[0];
                 p[1] = pi[1];
                 p[2] = pi[2];
                 pi += 4;      // skip last byte, input buffer contains integer, need only lower 3 bytes
             }
         }
      }
      else {

         setXY_priv(nh,nw);
         int i,sz=w*h;
         unsigned char *p=buf;
         unsigned char *pi=(unsigned char*)d;
         const int h4=h*4;
         //

         if(angle==90){
            for(int y=0;y<h;y++){
               //   for(int y=0;y<h;y++){
               //unsigned char *rb=(unsigned char*)(&d[y]);
               const int *od=d+y+w*h;
               unsigned char *rb=(unsigned char*)(&od[0]);
               for(int x=0;x<w;x++){
                  rb-=h4;
                  p[0]=rb[0];
                  p[1]=rb[1];
                  p[2]=rb[2];
                  //
                  p+=3;
               }
            }
         }
         else {
            int line=0;
            //270
            for(int y=h-1;y>=0;y--,line++){
               //   for(int y=0;y<h;y++){
               //unsigned char *rb=(unsigned char*)(&d[y]);
               const int *od=d+y+w*h;
               unsigned char *rb=(unsigned char*)(&od[0]);
               p=buf+(line+1)*w*3;
               for(int x=0;x<w;x++){
                  //unsigned char *rb=(unsigned char*)(&od[x*h]);
                  p-=3;
                  rb-=h4;
                  p[0]=rb[0];
                  p[1]=rb[1];
                  p[2]=rb[2];
                  //
               }
            }
         }
      }

      if(cb)cb->videoIn(buf,w*h*3,uiPos);
   }

private:
    virtual void setXY_priv(int x, int y){
        // if buffer size changed re-allocate
        if (x*y != w*h) {
            unsigned char *nb=new unsigned char [x*y*3];
            unsigned char *ob=buf;
            buf = nb;
            delete ob;
        }
        w = x; h = y;
    }
};
#endif

#endif

