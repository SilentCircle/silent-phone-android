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

#ifndef _VIDEO_INTERFACE_C

#define _VIDEO_INTERFACE_C

//If you have one video call at a time then you can ignore the "void *vo".
typedef struct _S_TVIDEO_OUT{
   
   int (*init)(struct _S_TVIDEO_OUT *ctx, void *vo);//create screen
   int (*release)(struct _S_TVIDEO_OUT *ctx, void *vo);
   
   //shoud show video screen
   int (*start)(struct _S_TVIDEO_OUT *ctx, void *vo);
   
   //should hide video screen
   void (*stop)(struct _S_TVIDEO_OUT *ctx, void *vo);
   
   //should allocate YUV frame memmory x-width y-height if supprted
   //must return 1 if you support YUV colorspace,
   int (*setOutputPictureSizeYuv)(struct _S_TVIDEO_OUT *ctx, void *vo, int x, int y);
   
   //should allocate frame memmory x-width y-height
   void (*setOutputPictureSize)(struct _S_TVIDEO_OUT *ctx, void *vo, int x, int y);
   
   //lock should not change,update,delete video buffer
   void (*lockFrame)(struct _S_TVIDEO_OUT *ctx, void *vo);
   
   //unlock, could change,update,delete video buffer but shoud wait for drawFrame
   void (*unLockFrame)(struct _S_TVIDEO_OUT *ctx, void *vo);
   
   //should draw on screen when see this fnc
   int (*drawFrameNow)(struct _S_TVIDEO_OUT *ctx, void *vo);
   
   //if(getImgParams can not return direct buffer)will call this fnc  after lockFrame
   //RGB24 only, if(setOutputPictureSizeYuv returns >1) will pass YUV888
   void (*setScanLine)(struct _S_TVIDEO_OUT *ctx, void *vo, int iLine, int iXOff, unsigned char *p, int iLen, int iBits);
   
   //will call this fnc after lockFrame
   int (*getImgParams)(struct _S_TVIDEO_OUT *ctx, void *vo, int &iYuv, int &iBpp, int &stride, int &iIsBRG, void **p);
   
   void *pUserData;

}S_TVIDEO_OUT;

//setup callback of video output
int setVO_CB(S_TVIDEO_OUT *v);

//pass camera data as BRG32 without alpha
void onNewVideoData(int *d, unsigned char *yuv, int w, int h, int angle);


/*
init();
start();
setOutputPictureSize(); - we allocate memory for a frame

while (...)
{
   int iCanIGetDirectBuffer=0;
   //I get video buffer pointer and format
   iCanIGetDirectBuffer = getImageParams(); - here you get a pointer to allocated memory via void **p
   
   lockFrame();
   
   if(iCanIGetDirectBuffer){
      //copy Directly into void **p;
   }
   else{
      //here I pass you RGB888 scan line, you convert it
      for(...)
         setScanLine(); - here you fill video frame buffer
   }
   unlockFrame(); - buffer is filled
   drawFrameNow() - we pass video frame to UI.
}
stop();
release();

*/


#if 0
class CTMain_demo{
public:
   void setUPVideoCallBack(){
      S_TVIDEO_OUT v;
      v.start = startV;
      //..........
      v.pUserData=this;
      setVO_CB(&v);
   }
   
#ifdef ONE_VIDEO_CALL
   void *screen;
#else
   typedef struct{
      void *id;
      void *ret;//screen or videoCTX;
   }SCR;
   
   SCR scr[8];
   void *findVideoCtx(void *vo){
      for(int i=0;i<8;i++){
         if(scr[i].id==vo)return scr[i].ret;
      }
      return NULL;
   }
   void *addVideoCtx(void *vo){
      for(int i=0;i<8;i++){
         if(scr[i].id==NULL){
            scr[i].id = vo;
            //scr[i].ret = createVideoOutputObject();
            return scr[i].ret;
         }
      }
      return NULL;
   }
#endif
};


static int startV(struct _S_TVIDEO_OUT *ctx, void *vo){
   CTMain_demo *pClass = (CTMain_demo*)ctx->pUserData;
#ifdef ONE_VIDEO_CALL
   void *videoScreen = pClass->screen;
#else
   void *videoScreen = pClass->findVideoCtx(vo);
   if(!videoScreen)videoScreen = pClass->addVideoCtx(vo);
#endif
   // videoScreen->show();
   return 0;
};

#endif


#endif
