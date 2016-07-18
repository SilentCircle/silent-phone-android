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

#ifndef C_T_VIDEO_IN_C_H
#define C_T_VIDEO_IN_C_H

unsigned int getTickCount();


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
      uiPosPrev = (getTickCount()&~1);

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

