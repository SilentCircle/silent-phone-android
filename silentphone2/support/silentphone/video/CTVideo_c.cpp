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
#include "CTVideoOutC.h"
#include "video_interface_c.h"


static int iIsSet=0;
static S_TVIDEO_OUT g_videoCB;

int setVO_CB(S_TVIDEO_OUT *v){
   g_videoCB = *v;
   iIsSet=1;
   return 0;
   //TODO verify
}

int CTVideoOut::init(){
   if(iIsSet)return g_videoCB.init(&g_videoCB, this);
   return -1;
};

int CTVideoOut::rel(){
   if(iIsSet)return g_videoCB.release(&g_videoCB, this);
   return -1;
};


int CTVideoOut::start(){
   iStarted=1;
   if(iIsSet)return g_videoCB.start(&g_videoCB, this);
   return -1;
};
void CTVideoOut::stop(){
   iStarted=0;
   if(iIsSet)return g_videoCB.stop(&g_videoCB, this);
}
void CTVideoOut::startDraw(){
   if(iIsSet)return g_videoCB.lockFrame(&g_videoCB, this);
}
void CTVideoOut::endDraw(){
   iLastFrameID++;
   if(iIsSet)return g_videoCB.unLockFrame(&g_videoCB, this);
}

int CTVideoOut::setOutputPictureSizeYuv(int cx, int cy){
   if(iIsSet && g_videoCB.setOutputPictureSizeYuv)
      return g_videoCB.setOutputPictureSizeYuv(&g_videoCB, this,cx,cy);
   return -1;
}

void CTVideoOut::setOutputPictureSize(int cx, int cy){
   if(iIsSet)return g_videoCB.setOutputPictureSize(&g_videoCB, this,cx,cy);
}
void CTVideoOut::setScanLine(int iLine, int iXOff, unsigned char *p, int iLen, int iBits){
   if(iIsSet)return g_videoCB.setScanLine(&g_videoCB, this, iLine,iXOff, p, iLen, iBits);
}
int CTVideoOut::getImgParams(int &iYuv, int &iBpp, int &stride, int &iIsBRG, void **p){
   if(iIsSet)return g_videoCB.getImgParams(&g_videoCB, this,iYuv, iBpp, stride, iIsBRG, p);
   return -1;
}
int CTVideoOut::drawFrame(){
   if(iIsSet)return g_videoCB.drawFrameNow(&g_videoCB, this);
   return 0;
}

