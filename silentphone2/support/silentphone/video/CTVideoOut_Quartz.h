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

#ifndef _C_T_VIDEO_OUT_Quartz_H
#define _C_T_VIDEO_OUT_Quartz_H

#include "../baseclasses/CTBase.h"

class CTVideoOut: public CTVideoOutBase{
   int w,h;
   unsigned uiPosTs;
   int iStarted;
   int iLastFrameID;
   int iNeedsClear;
   
   int *dataLocked;
   
   void *qiw;
   
public:
   CTVideoOut(){dataLocked=NULL;iNeedsClear=1;w=h=0;iStarted=0;iLastFrameID=0;qiw=NULL;}
   ~CTVideoOut(){iStarted=0;;;}
   
   int rel(){return 0;}
   int init(){return 0;}

   void setQWview(void *p);

   inline int getLastFrameID(){return iLastFrameID;}
   inline int isStarted(){return iStarted;}
   
   int getW(){return w;}
   int getH(){return h;}

   
   int start();//{iStarted=1;uiPosTs=0;return 0;}
   virtual void stop();
   
   virtual void startDraw();//lock ??
   virtual void endDraw();
   
   virtual int onData(unsigned char *p, int iLen, unsigned int uiPos){return 0;}
   virtual void setTimeStamp(unsigned int uiPos){uiPosTs=uiPos;}
   virtual void setScanLine(int iLine, unsigned char *p, int iLen, int iBits){setScanLine(iLine,0,p,iLen,iBits);}


   virtual void setOutputPictureSize(int cx, int cy);
   virtual void setScanLine(int iLine, int iXOff, unsigned char *p, int iLen, int iBits);
   
   virtual int getImgParams(int &iYuv, int &iBpp, int &stride, int &iIsBRG, void **p);
   
   
   virtual int drawFrame();
   
};
#endif
