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
#ifndef C_T_VIDEO_OUT_C_H
#define C_T_VIDEO_OUT_C_H

#include "../baseclasses/CTBase.h"

class CTVideoOut: public CTVideoOutBase{
   unsigned int uiPosTs;//TODO:remove this not used
   int iLastFrameID;
   int iStarted;
public:
   CTVideoOut(){iStarted=0;iLastFrameID=0;}
   ~CTVideoOut(){iStarted=0;}
   
   void setQWview(void *p){}
   
   inline int getLastFrameID(){return iLastFrameID;}
   inline int isStarted(){return iStarted;}
   
   int rel();
   int init();
   
   int start();
   virtual void stop();
   
   virtual void startDraw();//lock ??
   virtual void endDraw();
   
   virtual int onData(unsigned char *p, int iLen, unsigned int uiPos){return 0;}//depricated
   virtual void setTimeStamp(unsigned int uiPos){uiPosTs=uiPos;}//TODO:remove this not used
   virtual void setScanLine(int iLine, unsigned char *p, int iLen, int iBits){setScanLine(iLine,0,p,iLen,iBits);}
   
   virtual int setOutputPictureSizeYuv(int cx, int cy);
   virtual void setOutputPictureSize(int cx, int cy);
   virtual void setScanLine(int iLine, int iXOff, unsigned char *p, int iLen, int iBits);
   
   virtual int getImgParams(int &iYuv, int &iBpp, int &stride, int &iIsBRG, void **p);
   
   
   virtual int drawFrame();
   
};
#endif

