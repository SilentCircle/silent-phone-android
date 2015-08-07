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


#ifndef _C_TINA_ENC_DEC
#define _C_TINA_ENC_DEC

#define T_CAN_TEST_V

#include <string.h>
#ifdef ANDROID_NDK
//#include <../../../android/phone/jni/tina_exp.h>
#include "tina_exp.h"

#else
#if 1
int tinaCanDrawThis(unsigned char *pI, int iLen);
int tinaCanSkipThis(unsigned char *pI, int iLen);

int tina_canDecode(unsigned char *pI, int iLen);

void relTinaEnc(void *p);
void relTinaDec(void *p);
void *initTinaEnc();
void *initTinaDec();
int tina_decode(void *ctx, unsigned char *pI, int iLen, CTBmpBase *cVO);
int tina_encode(void *ctx, unsigned char *pI, unsigned char *pOut, int cx, int cy);
int tinaSpliter(unsigned char *pI, int iLen);

int tinaCmdD(void *ctx, const char *cmd);
int tinaCmdE(void *ctx, const char *cmd);
#else
/*
static void relTinaEnc(void *p){}
static void relTinaDec(void *p){}
static void *initTinaEnc(){return NULL;}
static void *initTinaDec(){return NULL;}
static int tina_decode(void *ctx, unsigned char *pI, int iLen, CTBmpBase *cVO){return 0;}
static int tina_encode(void *ctx, unsigned char *pI, unsigned char *pOut, int cx, int cy){return 0;}
static int tinaSpliter(unsigned char *pI, int iLen){return 0;}

static int tinaCmdD(void *ctx, char *cmd){return 0;}
static int tinaCmdE(void *ctx, char *cmd){return 0;}
*/
#endif
#endif

class CTTina_Enc_Dec: public CVCodecBase{
   void *ctxE;
   void *ctxD;
   int iEncX, iEncY;
   unsigned char *tina_BufS;//????????????
   int iBytesEnc;
   int iCurPos;
   
public:
   CTTina_Enc_Dec(){cVO=NULL;iBytesEnc=0;iCurPos=0;ctxE=ctxD=NULL;iEncX=0;iEncY=0;tina_BufS=NULL;}
   ~CTTina_Enc_Dec(){if(ctxE)relTinaEnc(ctxE);if(ctxD)relTinaDec(ctxD);delete tina_BufS;}
   virtual void reset(){

      if(ctxE)tinaCmdE(ctxE, "reset");
      if(ctxD)tinaCmdD(ctxD, "reset");

   }
   int isHeaderOK(unsigned char *pI, int iLen){
       
      return tina_canDecode(pI,iLen);
   }
   int getSamplesPerSec(){return 1000;}
   void configure(char *p){
      if(ctxE)tinaCmdE(ctxE, p);
   }
   void configureDec(char *p){
      if(ctxD)tinaCmdD(ctxD, p);
   }
   int encode(unsigned char *pI, unsigned char *pO, int iLen){
      if(!ctxE){
         ctxE=initTinaEnc();
         if(!ctxE)return -1;
         tina_BufS=new unsigned char [100000];
      }
      if(iBytesEnc==0){
          iBytesEnc=tina_encode(ctxE,pI,&tina_BufS[0],iEncX,iEncY);
      }
      if(iBytesEnc<0)return -1;
      if(iBytesEnc==0)return 0;
      
      int _TODO_use_TH_or_callback;
      
      int iS=tinaSpliter(&tina_BufS[iCurPos],iBytesEnc-iCurPos);
      if(iS==0){iBytesEnc=0;iCurPos=0;return 0;}
      
      memcpy(pO,&tina_BufS[iCurPos],iS);
      iCurPos+=iS;
      return iS;

   }
   //virtual int encodeNext(){return 0;};
   virtual void setXY(int x, int y){iEncX=x;iEncY=y;}
   int videoAhead(){return 0;}
   int decode(unsigned char *pI, unsigned char *pO, int iLen){
      if(!ctxD)ctxD=initTinaDec();
      return tina_decode(ctxD,pI,iLen,cVO);
   }
   
   virtual int canSkipThis(unsigned char *p, int iLen){
      
      
      return tinaCanSkipThis(p,iLen);
   }
   virtual int isDrawableFrame(unsigned char *p, int iLen){
      
      
      return tinaCanDrawThis(p,iLen);
   }
   virtual int wasDrawableFrame(unsigned char *p, int iLen){
      return isDrawableFrame(p,iLen);
   }
   
  // CTBmpBase *cVO;
   virtual void getDecXY(int &x, int &y){};
   virtual void setQuality(int q){};

   virtual const char *getShortName(){return "TINA"; };
   virtual int getId(){return 1215;};

};
#endif
