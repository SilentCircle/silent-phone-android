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


//#define _T_SOLD_REL


#define T_USE_YUV 1

#define T_CAN_TEST_V

#include "tina_exp.h"

#if defined(__linux__) && !defined(ANDROID_NDK) 

#define _T_DISABLE_TINA_ENC
#define _T_DISABLE_TINA_DEC
#endif

#ifdef  __SYMBIAN32__
void filter_x16_pic(unsigned char*, int, int, int){}
void filter_x_pic(unsigned char*, int, int, int){}
#endif
#ifdef _WIN32


#else
unsigned int t_timeGetTime(){return 0;}
#endif
#if  defined(__SYMBIAN32__) || defined(_WIN32_WCE) || defined(ANDROID_NDK) || defined(__APPLE__)
void debugss(char *,int, int){}
int t_dev_w_4x4_16_3(unsigned char *,int){return -1;};
#define _T_DISABLE_TINA_ENC
//#define _T_DISABLE_TINA_DEC
#else
void debugss(char *c, int a, int b);

#endif



#include <string.h>
#include "../../baseclasses/CTBase.h"

#if ! defined(_T_DISABLE_TINA_ENC)    || !defined(_T_DISABLE_TINA_DEC)
#include "../jpg/CTJpg.h"
#else

#endif

int decodeDctX2(unsigned char *pBin, int iLen, unsigned char *pCur, int  iIsB=0);
void useRGBinput(int f);


#if defined(_T_DISABLE_TINA_DEC)

//#include "../../baseclasses/CTBase.h"
//void relTinaDec(void *p){}
//void *initTinaDec(){return 0;}
int tina_decode(void *ctx, unsigned char *pI, int iLen, CTBmpBase *cVO){return -1;}
int tinaSpliter(unsigned char *pI, int iLen){return -1;}
int tinaCmdD(void *ctx, const char *cmd){return -1;}
//#error "Tina disabled"
#else
//#error "Tina enabled"
void tblock(unsigned char *pic, int w, int h, int q);
#include <math.h>
#include <stdlib.h>

#include "../CTImgData.h"
#include "vec_calc.h"
#include "CTPicDecoder.h"

#include "pack_hdr.h"
#include "tina_dec_sm.h"


int tina_canDecode(unsigned char *pI, int iLen){
   return CTVidDecoderN_SM::canDecode(pI,iLen);
}

int tina_decode(void *ctx, unsigned char *pI, int iLen, CTBmpBase *cVO){
   CTVidDecoderN_SM *d=(CTVidDecoderN_SM *)ctx;
   d->cVO=cVO;
#if  defined(__SYMBIAN32__) || defined(_WIN32_WCE) || defined(ANDROID_NDK)  || defined(__APPLE__)
#else
   d->iDeblockB=1;
#endif
   int ret=d->decode(pI,iLen,NULL);
   if(ret<0)debugss("dec ret inlen",ret,iLen);
   return ret;
}

int tinaCmdD(void *ctx,const char *cmd){
   if(!ctx)return -1;
   CTVidDecoderN_SM *d=(CTVidDecoderN_SM *)ctx;
   if(strcmp(cmd,"reset")==0)d->iResetFlag=1;
   if(strcmp(cmd,"noudp")==0)d->iIsUDP=0;
   return 0;
}

#endif 

#if  defined(_T_DISABLE_TINA_ENC)   
//void relTinaEnc(void *p){}
//void *initTinaEnc(){return 0;}
//int tina_encode(void *ctx, unsigned char *pI, unsigned char *pOut, int cx, int cy){return -1;}
void tblock(unsigned char *pic, int w, int h, int q);
int tinaCmdE(void *ctx, const char *cmd){return -1;}
void setEnableDisableTina(int f){}
#if defined(_WIN32_WCE) || defined(__SYMBIAN32__) || defined(ANDROID_NDK) || defined(__APPLE__)
int useFastBlockType(){return 0;}
void  debugsi(char *,int){}
#endif
#else

#ifndef _T_DISABLE_TINA_DEC
#include <math.h>
#include <stdlib.h>

#include "../ctimgdata.h"
#include "vec_calc.h"
#include "CTPicDecoder.h"

#include "pack_hdr.h"

void tblock(unsigned char *pic, int w, int h, int q);
#include "CTVideoV3n.h"
#endif

static int iTinaEnabled=1;
void setEnableDisableTina(int f){
   iTinaEnabled=f;
}
int tinaCmdE(void *ctx, const char *cmd){
   if(!ctx)return -1;
   CTVidEncoderN *e=(CTVidEncoderN *)ctx;
   if(strcmp(cmd,"reset")==0)e->iResetFlag=1;
   if(strcmp(cmd,"need_key")==0)e->iNeedKeyPacketLost=1;
   return 0;
}



#endif

#if 1
//defined(_T_DISABLE_TINA_ENC) || defined (_T_DISABLE_TINA_DEC)
#if ! defined(_T_DISABLE_TINA_ENC)    || !defined(_T_DISABLE_TINA_DEC)



int tinaSpliter(unsigned char *pI, int iLen){
      TVHDR_PCK pck;
      TVHDR_PCK *pack=&pck;
   
      
      if(iLen<10)return 0;
      decodePckHdr(pI,pack);
      //debugss("tina split-------",pack->sizeofHdr+pack->iSizeOfChunk,iLen);
      return pack->sizeofHdr+pack->iSizeOfChunk;
}
int tinaCanSkipThis(unsigned char *pI, int iLen){
      TVHDR_PCK pck;
      TVHDR_PCK *pack=&pck;
   
      
      if(iLen<10)return 0;
      decodePckHdr(pI,pack);
      return pack->iIsB?1:0;
}
int tinaCanDrawThis(unsigned char *pI, int iLen){
   TVHDR_PCK pck;
   TVHDR_PCK *pack=&pck;

   if(iLen<10)return 0;
   decodePckHdr(pI,pack);
   return pack->iCanDraw?1:0;
}
#endif
#endif

double  getPSNRUV(unsigned char *,unsigned char *,int,int,int){return 100;}

static int iKBsOverride=0;
void setVideoKbpsOverride(int l){
   iKBsOverride=l;
}

int tina_encode(void *ctx, unsigned char *pI, unsigned char *pOut, int cx, int cy){
#ifdef _T_DISABLE_TINA_ENC
   return -1;
#else
  // iTinaEnabled=0;
   if(!iTinaEnabled)return -1;

   CTVidEncoderN *e=(CTVidEncoderN *)ctx;
   e->iCanRotate=1;
   
   e->iGRectCnst=98;e->iForceVecSize=8;

   if(cx<200)e->qVal=72;
   else if(cx<=320)e->qVal=60;
   else if(cx==640)e->qVal=52;
   else  e->qVal=56;

   void setVideoKbps(int l);
   if(!iKBsOverride)setVideoKbps(600);
   if(cy>480 || cx>640){
      e->iForceVecSize=16;
      e->qVal=56;//60;
      if(!iKBsOverride)setVideoKbps(600);
      
   }
   else{
      e->iForceVecSize=8;
      e->qVal=64;
   }
   if(cx==320 && cy==240){
      e->qVal=64;
      e->iForceVecSize=8;
      if(!iKBsOverride)setVideoKbps(350);
   }
   if(cx==352 && cy==288){
      e->qVal=60;//64;
      e->iForceVecSize=8;
      if(!iKBsOverride)setVideoKbps(400);
   }

   if(cx==176 && cy==144){
      e->qVal=72;
      e->iForceVecSize=8;
      if(!iKBsOverride)setVideoKbps(220);
   }
   else if (cx<200 && cy<160){
      e->qVal=72;
      e->iForceVecSize=8;
      if(!iKBsOverride)setVideoKbps(220);
   }
   e->iKeyFrameRateMax=60;
   void setMinMaxQval(int mi, int ma);
   setMinMaxQval(2,24);


   
   e->iCalcPSNR=1;
   if(iKBsOverride)setVideoKbps(iKBsOverride);
//setVideoKbps(450);
 //  e->qVal=12;
   //e->iForceVecSize=8;
  // e->qVal=12;
      
   int eLen=e->encode(pI,pOut,cx,cy);
   //decodeDctX2(unsigned char *pBin, int iLen, unsigned char *pCur);
   //decodeDctX2(pOut,eLen,
   return eLen;
#endif
}


void *initTinaEnc(){
#ifndef _T_DISABLE_TINA_ENC
   useRGBinput(!T_USE_YUV);
   return new CTVidEncoderN();
#else
   return NULL;
#endif
}
   
void *initTinaDec(){
#ifndef _T_DISABLE_TINA_DEC
   useRGBinput(!T_USE_YUV);
   return new CTVidDecoderN_SM();
#else
   return NULL;
#endif
}

void relTinaEnc(void *p){
#ifndef _T_DISABLE_TINA_ENC
   CTVidEncoderN *e=(CTVidEncoderN *)p;if(e)delete e;
#endif
}
void relTinaDec(void *p){
#ifndef _T_DISABLE_TINA_DEC
   CTVidDecoderN_SM *d=(CTVidDecoderN_SM *)p;if(d)delete d;
#endif
}
