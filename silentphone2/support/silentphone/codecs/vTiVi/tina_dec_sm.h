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

#ifndef _CT_TINA_SM_DEC
#define _CT_TINA_SM_DEC

#define T_CAN_TEST_V

void toRgb(unsigned char *p, int w, int h);
void toRgb(unsigned char *p, int w, int h, unsigned char *out);
int decodeDctX2N(unsigned char *pBin, int iLen, unsigned char *pCur, int iIsB, int iKey, int w, int h, int iFilter);
#include "t_rgb_yuv.h"
class CTVidDecoderN_SM{
   int iCanDrawPic;
public:
   CTRgb_Yuv rgbCnv;
   
   int iSize;
   int iEncX,iEncY;
   
   int iCurentToDecode;
   int iCurentToShow;
   int iCanShow;
   int iResetFlag;
   int iDeblockB;
   CTBmpBase *cVO;
   CTPixDecoder picd;
   CTPixDecoder picdB;
   CTImgData imgTmp;
   int iBadNonB;
   
   //M2,M1,Next
   VEC_IMG_STRUCT referncesX[3];
   VEC_IMG_STRUCT decRef;
   void *tinaMVDec;
   int iIsUDP;
   int iFirstFramePack;
   
   
   CTVidDecoderN_SM():picd(3),picdB(4),rgbCnv(){
      iFirstFramePack=1;
      iIsUDP=1;
      iDeblockB=0;
      iBadNonB=1;
      iEncX=iEncY=0;iCurentToDecode=0;iCurentToShow=0;iCanShow=0;
      tinaMVDec=initMotion();
      cVO=NULL;
      imgToShow=NULL;
      iPrevVectID=0;
      iPrevKeyID=0;
      iResetFlag=0;
      
   }
   virtual ~CTVidDecoderN_SM(){relMotion(tinaMVDec);}
   void reset(){
      iResetFlag=0;
      if(decRef.img.getBuf())memset(decRef.img.getBuf(),128,iSize);
      for(int i=0;i<3;i++)if(referncesX[i].img.getBuf())memset(referncesX[i].img.getBuf(),128,iSize);
   }
   
   void init(int cx, int cy)
   {
      if(cx!=iEncX  || cy!=iEncY)
      {
         iPrevDrawed=1;
         imgToShow=NULL;
         iPrevDecodedGroup=-1;
         iSize=cx*cy*3;
         iEncX=cx;
         iEncY=cy;
         decRef.img.setOutputPictureSize(cx,cy);
         memset(decRef.img.getBuf(),128,iSize);
         int i;
         for(i=0;i<3;i++)
         {
            referncesX[i].img.setOutputPictureSize(cx,cy);
            memset(referncesX[i].img.getBuf(),128,iSize);
            referncesX[i].iRefCreated=0;
            referncesX[i].id=0;
         }
      }
   }
private:
   static int canDecodeP(unsigned char *pI, int iLen){
      TVHDR_PCK pck;
      TVHDR_PCK *pack=&pck;
      
      memset(pack,0,sizeof(TVHDR_PCK));
      
#ifndef T_CAN_TEST_V
      decodePckHdr(pI,pack);
      if(pack->vers!='3')return -400;
#else
      if(pI[0]!='T' || pI[1]!='4')return -400;
      decodePckHdr(pI,pack);
#endif
      if(pack->decpos>8)return -300;
      if(pack->w>2600 || pack->h>1600)return -500;
      if(pack->w<16 || pack->h<16)return -501;
      if((pack->w|pack->h)&15)return -502;
      if(pack->iDecSize>3)return -600;
#ifndef T_CAN_TEST_V
      if(pack->sizeofHdr>70 || pack->sizeofHdr<8)return -700;
#else
      if(pack->sizeofHdr>40 || pack->sizeofHdr<4)return -700;
#endif
      if(pack->iIsJpg && pack->iIsDCT)return -800;
      if(pack->iIsParts && pack->iIsVect)return -900;
      if(pack->iIsJpg>1 || pack->iIsKey>2 || pack->iIsDCT>1)return -950;
      if(pack->iIsParts>1 || pack->iIsVect>2)return -960;
      return 0;
   }
public:
   static int canDecode(unsigned char *pI, int iLen){
      return canDecodeP(pI, iLen)==0;
   }
   
   int decode(unsigned char *pI, int iLen, unsigned char *pOut){
      int ret=0;
      TVHDR_PCK pck;
      TVHDR_PCK *pack=&pck;
      iCanDrawPic=0;
      
#ifndef T_CAN_TEST_V
      while(iLen>=15)
#else
         while(iLen>=9)
#endif
         {
            //TVHDR_PCK *pack=(TVHDR_PCK*)pI;
            memset(pack,0,sizeof(TVHDR_PCK));
            
#ifndef T_CAN_TEST_V
            decodePckHdr(pI,pack);
            if(pack->vers!='3')return -400;
#else
            if(pI[0]!='T')return -400;
            if(pI[1]!='4')return -400;
            decodePckHdr(pI,pack);
#endif
            if(pack->decpos>8)return -300;
            if(pack->w>2600 || pack->h>1600)return -500;
            if(pack->w<16 || pack->h<16)return -501;
            if(pack->iDecSize>3)return -600;
#ifndef T_CAN_TEST_V
            if(pack->sizeofHdr>70 || pack->sizeofHdr<8)return -700;
#else
            if(pack->sizeofHdr>40 || pack->sizeofHdr<4)return -700;
#endif
            if(pack->iIsJpg && pack->iIsDCT)return -800;
            if(pack->iIsParts && pack->iIsVect)return -900;
            if(pack->iIsJpg>1 || pack->iIsKey>2 || pack->iIsDCT>1)return -950;
            if(pack->iIsParts>1 || pack->iIsVect>2)return -960;
            
            init(pack->w,pack->h);
            pI+=pack->sizeofHdr;
            //         static int dr2;dr2+=pack->iSizeOfChunk;static int drop;drop++;if(!(drop%(((dr2)&7)+3))){drop=0;}else 
            //static int drop;drop++;if(!(drop&(7{drop=0;}else 
            ret|=onPacket(pack,pI, pack->iSizeOfChunk, pOut);   
            //  if(ret<0)return -10;
            //debugcc("x9999999ok2");
            pI+=pack->iSizeOfChunk;
            iLen-=pack->iSizeOfChunk;
            iLen-=pack->sizeofHdr;
            // if(pack->iCanDraw){drawImg(pOut);}
         }
      if(iCanDrawPic)return 1;
      
      return ret;
   }
   unsigned char pRow[2048*3];
   void draw(unsigned char *dst)
   {
      int iStep=8;//pack->iDecSize?16:8;
      int i,j;
      ///unsigned char *dst=dec->img.getBuf();
      static int col=30;
      if(col>200)col-=255;
      if(col<30)col=30;
      
      //for(i=0;i<sz;i++,dst+=3)*(int*)dst=0x7f7f00;
      
      //return ;
      memset(dst,127,this->iSize);
      for(i=0;i<iEncY;i+=iStep)
      {
         //memset(dst+(i*iEncX)*3,0,iEncX*3);
         memsetC(dst+(i*iEncX)*3,0x7f7f00,iEncX*3);
         //memset(dst+(i*iEncX)*3+iEncX,127,iEncX*2);
      }
      
      for(j=0;j<iEncX;j+=iStep)
         for(i=0;i<iEncY;i++)
         {
            dst[(i*iEncX+j)*3]=0;
            dst[(i*iEncX+j)*3+1]=127;
            dst[(i*iEncX+j)*3+2]=127;
         }
   }
   int iPrevDecoded;
   int iPrevDecodedGroup;
   VEC_IMG_STRUCT *imgToShow;
   int iPrevDrawed;
   void drawImg(unsigned char *pOut,int testDr=0){
      
      if(!imgToShow || iPrevDrawed)return;
      if(!pOut  && !cVO)return;
      
      //return;
      unsigned char *ps=imgToShow->img.getBuf();
      int cx=iEncX;
      int cy=iEncY;
      int iToRgb=1;
      {
         //TODO arm neon 
         //https://code.ros.org/trac/opencv/browser/trunk/opencv/android/android-jni/jni/yuv2rgb_neon.c?rev=4083
         if(cVO)
         {
            // debugss("draw",0,0);
            iToRgb=0;
            int i;
            int str=cx*3;
            int iIsYuv=cVO->setOutputPictureSizeYuv(cx,cy)>=0;
            //if(!iIsYuv)cVO->setOutputPictureSize(cx,cy);
            cVO->startDraw();
            int iFastCalc=0;
            int iBpp,iYuv=iIsYuv,stride,iIsBRG;
            void *idata=NULL;
            iFastCalc=!iIsYuv && cVO->getImgParams(iYuv, iBpp, stride, iIsBRG, &idata)!=-1;
            iFastCalc=iFastCalc && ((iBpp==16 || iBpp==32) && !iYuv && idata);
            
            if(iFastCalc){
               if(iBpp==16 ){
                  //int cxD=stride>((cx*iBpp)>>3)?((stride*iBpp)>>3):cx;
                  unsigned short *sd=(unsigned short *)idata;
                  for(i=0;i<cy;i++){
                     if(iIsBRG)
                        rgbCnv.toRgb16BRG(ps,cx,1,sd);
                     else
                        rgbCnv.toRgb16(ps,cx,1,sd);
                     //sd[i]=55;
                     sd+=(stride>>1);// *4;
                     ps+=str;// *4;
                  }
               }
               else
               {
                  unsigned int *sd=(unsigned int *)idata;
                  for(i=0;i<cy;i++){
                     if(iIsBRG)
#if 0
     //                   def __APPLE__
                        rgbCnv.toRgb32BRG(ps,cx,1,&((unsigned int *)idata)[cx*(cy-i-1)]);
#else
                     rgbCnv.toRgb32BRG(ps,cx,1,sd);
#endif
                     else
                        rgbCnv.toRgb32(ps,cx,1,sd);
                     //sd[i]=55;
                     sd+=(stride>>2);// *4;
                     ps+=str;// *4;
                  }
               }
            }
            else{
#if  !defined(_WIN32_WCE) && !defined(__SYMBIAN32__) && !defined(ARM) && !defined(ANDROID_NDK)
               unsigned char xrow[2048*3];
               unsigned char *row=&xrow[0];
#else
               unsigned char xrow[1024];
               unsigned char *row=(str>1024)?&pRow[0]:&xrow[0];
#endif
               for(i=0;i<cy;i++){
                  if(!iIsYuv){
                     rgbCnv.toRgb(ps,cx,1,&row[0]);
                     cVO->setScanLine(i,&row[0],str,24);
                     //cVO->setScanLine(i,ps,str,24);
                  }
                  else {
                     //for(int z=0;z<str;z+=3){row[z]=128;row[z+1]=ps[z+1];row[z+2]=ps[z+2];}   cVO->setScanLine(i,&row[0],str,24);
                     cVO->setScanLine(i,ps,str,24);
                  }
                  ps+=str;
               }
            }
            cVO->endDraw();
         }
         else {
            //rgbCnv.toRgb(ps,cx,cy,pOut);
            iToRgb=0;
            //  memcpy(pOut,ps,iSize);
            
         }
         
         
      }
      if(T_USE_YUV && iToRgb){
         //  rgbCnv.toRgb(pOut,cx,cy,pOut);
      }
      if(testDr==0){
         iCanDrawPic=1;
         iPrevDrawed=1;
      }
      
   }
   int iPrevVectID;
   int iPrevKeyID;
   int iDecFlag;
   int onPacket(TVHDR_PCK *pack, unsigned char *pI, int iLen, unsigned char *pOut)
   {
      //static int sk;sk++;if((sk&3)==0)return 0;
      if(iResetFlag)reset();
      void debugsi(char *c, int a);
      //debugsi(pack->iIsKey?"key":(pack->iIsB?"b-fr":"d-fr"),pack->iCanDraw+pack->iIsVect*10+1000*pack->iFrNr);
      
      
      //suutiit tikai max3 koefus ar otro pack
      if(pack->iIsKey){
         if(iPrevKeyID==1 && pack->iIsKey==2){
            iPrevKeyID=2;
            return 0;
         }
         iPrevKeyID=pack->iIsKey;
      }
      else iPrevKeyID=0;
      
      int iPosToDecode=(int)pack->decpos;
      if(pack->iIsVect){
         if(iPrevVectID==1 && pack->iIsVect==2 && (iPosToDecode==iPrevDecoded && pack->iFrNr==iPrevDecodedGroup)){
            iPrevVectID=2;
            return 0;
         }
         iPrevVectID=pack->iIsVect;
      }
      else iPrevVectID=0;
      
      int ret=0;
      iCanShow=0;
      
      //pack->iFrNr=iGroupId;
      int iIsB=pack->iIsB;
      VEC_IMG_STRUCT *dec=NULL;//&decRef;
      //if(!iIsB)dec=&referncesX[2];
      if(pack->iFrNr!=iPrevDecodedGroup || iPrevDecoded!=iPosToDecode){
         if(!iPrevDrawed)drawImg(pOut);
         iPrevDrawed=0;
         iFirstFramePack=1;
         //  void debugsi(char *c, int a);
         //debugsi("fp",1);
      }
      
      
      
      if(!iIsB)//(pack->iIsKey || pack->iIsVect))
      {
         if(pack->iFrNr!=iPrevDecodedGroup || iPrevDecoded!=iPosToDecode){
            
            int iLastIsKey;
            if(0)//iPrevDrawed==0)//TODO
            {
               imgToShow=&referncesX[2];
               drawImg(pOut);
            }
            else decRef.img.copy(&referncesX[2].img);//iespejams var zimet pa tiesho
            
            imgToShow=&decRef;
            
            
            if(referncesX[2].iIsKey==2){
               CTImgData::swap(&referncesX[0].img,&referncesX[2].img);
               referncesX[2].iIsKey=0;   
               referncesX[0].iIsKey=2;   
            }
            else{
               iLastIsKey=referncesX[2].iIsKey;
               
               if(referncesX[1].iIsKey){
                  CTImgData::swap(&referncesX[0].img,&referncesX[1].img);
                  //   memset(referncesX[0].img.getBuf(),70,iSize);
               }
               CTImgData::swap(&referncesX[1].img,&referncesX[2].img);
               
               referncesX[1].iIsKey=iLastIsKey;
               referncesX[2].iIsKey=0;   
            }
            //referncesX[2].iIsKey=pack->iIsKRef;
            
            /*
             decRef.img.copy(&referncesX[2].img);
             imgToShow=&decRef;
             
             if(referncesX[1].iIsKey)CTImgData::swap(&referncesX[0].img,&referncesX[1].img);
             referncesX[1].img.copy(&referncesX[2].img);
             referncesX[1].iIsKey=referncesX[2].iIsKey;
             referncesX[2].iIsKey=0;
             */
         }
         dec=&referncesX[2];
      }
      if(dec==NULL)dec=&decRef;
      if(pack->iIsParts && pack->iIsKey==0 && (pack->iFrNr!=iPrevDecodedGroup || iPrevDecoded!=iPosToDecode)){
         tryToRestoreVectors(dec->img.getBuf(),iIsB);
         //debugss("vecrest"
      }
      
      //dec=&referncesX[2];
      iPrevDecoded=iPosToDecode;
      
      //dec=&preferncesNext[iPosToDecode];
      /*
       if(pack->iIsVect || pack->iIsKey)
       dec->iIsKey=(pack->iIsKRef)?1:0;
       else if(pack->iIsKRef)
       dec->iIsKey=1;
       */
      //dec.iIsKey=pack->iIsKRef!=0?1:0;
      dec->iIsKey=pack->iIsKRef?pack->iIsKRef:0;
      
      if(iFirstFramePack && !pack->iIsVect){
         void resetMV(void *pCtx, int iMBSize2, int w, int h);
         resetMV(tinaMVDec,16,  iEncX,iEncY);
      }
      iFirstFramePack=0;
      
      /*
       if(0)//pack->iIsZiped)
       {
       unsigned long dstlen=40000;
       //uncompress(&bufZip[0],&dstlen,pI,iLen);
       pI=&bufZip[0];
       iLen=(int)dstlen;
       }
       */
      if(pack->iIsVect){ret=decodeVectors(pI,iLen,dec->img.getBuf(), pack);/*memset(dec->img.getBuf(),0,this->iEncX*iEncY*3);*/}
      else if(pack->iIsKey)
      {
         if(0)draw(dec->img.getBuf());
         else decodeImg(dec->img.getBuf(),pI,iLen,pack);
      }
      else
      {
         
         
         if(pack->iIsParts){ret=decodeParts(pI,iLen,dec->img.getBuf(),pack);}
         else if(pack->iIsDif)
         {
            //dec->iIsKey=1;
            /*
             if(0)
             draw(imgTmp.getBuf());
             else
             ret=decodeImg(imgTmp.getBuf(),pI,iLen,pack);
             updateDif(imgTmp.getBuf(),dec->img.getBuf(),0,0,iEncX,iEncY,iEncX,iEncY);
             */
            
         }
      }
      int stride=iEncX*3;
      iCanShow=pack->iCanDraw&1;
      //drawImg(pOut,1);if(cVO)cVO-(0,0,0);Sleep(500);
      
      void deblock_yuv(unsigned char *pCur ,int w, int h, int stride, int iEnc, int iB);
      
      if((pack->iDeblock || (pack->iIsB &&  iDeblockB))&& iCanShow)
      {
         //debugsi("debl",pack->iIsB+pack->iDeblock*100);
         deblock_yuv(dec->img.getBuf() ,iEncX, iEncY, iEncX*3,0,!!pack->iIsB);
         
      }
      //if(iCanShow){drawImg(pOut);iPrevDrawed=1;}
      
      if(iCanShow){
         if(pack->iIsKRef && pack->iIsB){
            referncesX[pack->iIsKRef==1?1:0].img.copy(&dec->img);//img);
            
         }
         memcpy(dec->img.getBuf()-stride,dec->img.getBuf(),stride);
         memcpy(dec->img.getBuf()+iEncX*iEncY*3,dec->img.getBuf()+iEncX*(iEncY-1)*3,stride);
      }
      /*
       if(pack->iCanDraw&1){
       if(pack->iIsKRef && pack->iIsB){
       referncesX[pack->iIsKRef==1?1:0].img.copy(&dec->img);//img);
       
       }
       memcpy(dec->img.getBuf()-stride,dec->img.getBuf(),stride);
       memcpy(dec->img.getBuf()+iEncX*iEncY*3,dec->img.getBuf()+iEncX*(iEncY-1)*3,stride);
       }
       iCanShow=pack->iCanDraw&1;
       //    if(iCanShow){
       //  }
       
       if((pack->iDeblock || (pack->iIsB &&  iDeblockB))&& iCanShow)
       {
       //         deblock(dec->img.getBuf(),(int)pack->iDeblock);
       void deblock_yuv(unsigned char *pCur ,int w, int h, int stride, int iEnc, int iB);
       deblock_yuv(dec->img.getBuf() ,iEncX, iEncY, iEncX*3,0,!!pack->iIsB);
       //imgToShow=dec;
       
       }
       if(iCanShow){drawImg(pOut);iPrevDrawed=1;}
       */
      iPrevDecodedGroup=pack->iFrNr;
      iDecFlag=pack->iDecSize;
      return 0;
   }
   /*
    void deblock(unsigned char *p, int iDeblock)
    {
    iDeblock=abs(iDeblock);
    if(iDeblock==1)
    tblock(p,iEncX,iEncY,32);
    else if(iDeblock==2)
    tblock(p,iEncX,iEncY,60);
    else if(iDeblock==3)
    tblock(p,iEncX,iEncY,90);
    }
    */
   int decodeParts(unsigned char *p, int iLen, unsigned char *dst, TVHDR_PCK *pack)
   {
      //int iMBSize=pack->ckunkh;
      if(pack->iIsDCT)
      {
         if(0 &&pack->iIsKRef)draw(dst);
         //int decodeDctX2N(unsigned char *pBin, int iLen, unsigned char *pCur, int iIsB, int iKey, int w, int h, int iFilter)
         else return decodeDctX2N(p,iLen,dst,pack->iIsB,pack->iIsKey,pack->w,pack->h,pack->iDeblock);
         return 0;
      }
      return 0;
      /*
       int iBytes=ac.decode(p,0,partId);
       
       p+=iBytes;
       iLen-=iBytes;
       decodeImgParts(p,iLen,&partId[0],dst,pack);
       */
      return 0;
   }
   static inline void memsetC(unsigned char *p, int c, int iSize){
#if defined(_WIN32_WCE) || defined(__SYMBIAN32__)  || defined(ARM) || defined(ANDROID_NDK)
      int y=0;
      int u=0x80;
      int v=0x80;
      while(iSize>2)
      {
         p[0]=y;
         p[1]=u;
         p[2]=v;
         p+=3;
         iSize-=3;
      }
#else
      while(iSize>2)
      {
         *(int*)p=c;
         p+=3;
         iSize-=3;
      }
#endif
   }
   static void decFakeImg(unsigned char *p, int w, int h,int *pParts, int iIsB)
   {
      static int c=0x77ee55;
      //int w=pack->ckunkw;
      int iS=w*w*3;
      int hdw=h/w;
      if(pParts)
      {
         for(int i=0;i<hdw;i++)
         {
            while(!*pParts)pParts++;
            int cc=*pParts;
            *pParts=1;
            pParts++;
            if(cc==2 || cc>3)c=0x7f007f;
            else c=iIsB?0x7f997f:0x7faa7f;
            memsetC(p+i*iS,c,iS);
         }
      }
      else
      {
         for(int i=0;i<hdw;i++)
         {
            memsetC(p+i*iS,c,iS);
            c|=0x101010;
            c+=4;
            c&=0xffffff;
         }
      }
      
      c|=0x101010;
      c+=0x70;
      c&=0xffffff;
   }
   inline void decodeImgParts(unsigned char *p, int iLen,int *pParts,unsigned char *dst, TVHDR_PCK *pack, int iDifs=0)
   {
      //if(!iIsEncoderPart)return;
      pack->ckunkw=p[0];
      //pack->ckunkh=(p[3]<<16)|(p[2]<<8)|(p[1]);
      pack->ckunkh=p[3];pack->ckunkh<<=8;
      pack->ckunkh|=p[2];pack->ckunkh<<=8;
      pack->ckunkh|=p[1];
      pack->ckunkh*=pack->ckunkw;
      p+=4;
      iLen-=4;
      imgTmp.setOutputPictureSize(iEncX,iEncY);
      if(1)// || pack->ckunkw!=16)
         decodeImg(imgTmp.getBuf(),p,iLen,pack);//crasho,dct
      else
      {
         decFakeImg(imgTmp.getBuf(),pack->ckunkw,pack->ckunkh,NULL,pack->iIsB);
      }
      CTPixDecoder::decodeParts(dst,imgTmp.getBuf(),pParts,pack->ckunkw,pack->ckunkh,iEncX,iEncY,pack->iIsB?picdB:picd,iDifs);
   }
   //  unsigned char tmpJpgBuf[512000];
   int decodeImg(unsigned char *pTo, unsigned char *pBin, int iBinLen, TVHDR_PCK *pack)
   {
      int ret=0;
      //int testx=
      if(iBinLen<=0)return 0;
#if 0
      int w=pack->iIsParts?pack->ckunkw:iEncX;
      int h=pack->iIsParts?pack->ckunkh:iEncY;
      if(0)//pack->iIsWavelet)
      {
         //debugcc("x-dwav");
         //wCoder.decode(pBin, iBinLen, pTo, &w, &h);
         unsigned long dstlen=400000;
         //uncompress(&tmpJpgBuf[0],&dstlen,pBin,iBinLen);
         int i,j;
         int sz=w*h;
         
         for(i=0;i<sz;i++)
         {
            *pTo=(unsigned char)(((int)tmpJpgBuf[i]<<1));pTo++;
            *pTo=(unsigned char)(((int)tmpJpgBuf[i+sz]<<2));pTo++;
            *pTo=(unsigned char)(((int)tmpJpgBuf[i+sz+sz]<<2));pTo++;
            //pTo+=3;
         }
         //pI=&bufZip[0];
         //iLen=(int)dstlen;
         
         //testWaveletD(
      }
      else if(pack->iIsJpg)
      {
         //debugcc("x-djpg");
         if(T_USE_YUV) jdec.iOutColorSpace=JCS_YCbCr;  
         
         jdec.testx=w;
         jdec.testy=h;
         jdec.start();
#define T_JPG_HDR_SZ 162
         if(pBin[0]==0)
         {
            //iBinLen+=
            memcpy(&tmpJpgBuf[0],&pJpgHdrTab[(pBin[1])*T_JPG_HDR_SZ],T_JPG_HDR_SZ);
            memcpy(&tmpJpgBuf[T_JPG_HDR_SZ],pBin+2,iBinLen);
            iBinLen+=(T_JPG_HDR_SZ-2);
            pBin=&tmpJpgBuf[0];
         }
         jdec.decode(pBin,pTo,iBinLen);
         jdec.stop();
         
      }
      else
#endif         
         if(pack->iIsDCT)
         {
            //decode_tdct(pBin,iBinLen,pTo,w,h);
            ret=decodeDctX2N(pBin,iBinLen,pTo,pack->iIsB,pack->iIsKey,pack->w,pack->h,pack->iDeblock);
            //ret=decodeDctX2(pBin,iBinLen,pTo,pack->iIsB);
         }
      return ret;
   }
   void tryToRestoreVectors(unsigned char *dst, int iIsB){
      REFPIC ref[2+3];
      ref[0].pPic=referncesX[0].img.getBuf();
      ref[1].pPic=referncesX[1].img.getBuf();
      ref[2].pPic=referncesX[2].img.getBuf();
      ref[3].pPic=referncesX[2].img.getBuf();
      ref[4].pPic=referncesX[2].img.getBuf();
      CMB4 *pv=getPtr_CMB4(tinaMVDec);
      if(!pv || pv->iPrevMBSize<4)return;
      if(!pv->mb || iEncX/pv->iPrevMBSize!=pv->xc || iEncY/pv->iPrevMBSize!=pv->yc )return;
      int i;
      MB_4 *mb4=pv->mb;
      int to=pv->xc*pv->yc;
      //TODO wait first_b_frame then =m1-eNext
      int isValidVector(int x, int y ,int pos1, int pos2,int iMBSize, int w ,int h);
      int iMBSize=pv->iPrevMBSize;
      int j;
      for(i=0;i<to;i++,mb4++){
         mb4->iMVMode=0;
         mb4->r.i=0;
         mb4->refId=-1;
         if(iIsB){
            mb4->vrefs[mb4->eM1].x=mb4->mv_eM1_dec_enc.x/2;
            mb4->vrefs[mb4->eM1].y=mb4->mv_eM1_dec_enc.y/2;
            mb4->vrefs[mb4->eM2].x=-mb4->vrefs[mb4->eM1].x;
            mb4->vrefs[mb4->eM2].y=-mb4->vrefs[mb4->eM1].y;
            mb4->iIsBi=1;
            if(!isValidVector(
                              mb4->vrefs[mb4->eM2].x,mb4->vrefs[mb4->eM2].y,
                              mb4->i*iMBSize,mb4->j*iMBSize,iMBSize, iEncX,iEncY)
               || !isValidVector(
                                 mb4->vrefs[mb4->eM1].x,mb4->vrefs[mb4->eM1].y,
                                 mb4->i*iMBSize,mb4->j*iMBSize,iMBSize, iEncX,iEncY))
            {
               mb4->vrefs[mb4->eM2].x=mb4->vrefs[mb4->eM2].y=0;
               mb4->vrefs[mb4->eM1].y=mb4->vrefs[mb4->eM1].y=0;
               //TODO if m1 is not valid use m2
            }
         }
         else{
            if(mb4->iIsBi){
               mb4->vrefs[mb4->eM1].x-=mb4->vrefs[mb4->eM2].x;
               mb4->vrefs[mb4->eM1].y-=mb4->vrefs[mb4->eM2].y;
            }
            else mb4->vrefs[mb4->eM1]=mb4->mv_eM1_gr;
            mb4->iIsBi=0;
         }
         if(!isValidVector(
                           mb4->vrefs[mb4->eM1].x,mb4->vrefs[mb4->eM1].y,
                           mb4->i*iMBSize,mb4->j*iMBSize,iMBSize, iEncX,iEncY))
         {
            mb4->vrefs[mb4->eM1].x=mb4->vrefs[mb4->eM1].y=0;
         }
         for(j=0;j<16;j++)mb4->mv4[j]=mb4->vrefs[mb4->eM1];
         for(j=0;j<4;j++)mb4->mv2[j]=mb4->vrefs[mb4->eM1];
         
      }
      
      
      
      moveVectorsRefMB4(pv->iPrevMBSize,pv->mb,dst,&ref[2], iEncX,iEncY,iIsB,1);
   }
   // unsigned char bufZip[40000];
   int decodeVectors(unsigned char *p, int iLen, unsigned char *dst,TVHDR_PCK *pack)
   {
      
      //pack->vers
      //      int i;
      int iMBSize;//=p[0];
      
      
      REFPIC ref[2+3];
      ref[0].pPic=referncesX[0].img.getBuf();
      ref[1].pPic=referncesX[1].img.getBuf();
      ref[2].pPic=referncesX[2].img.getBuf();
      ref[3].pPic=referncesX[2].img.getBuf();
      ref[4].pPic=referncesX[2].img.getBuf();
      /*
       for(i=0;i<eMaxReferences;i++)ref[i+2].pPic=preferncesNext[i].img.getBuf();
       ref[0].pPic=referncesM2.img.getBuf();
       ref[1].pPic=refernces[0].img.getBuf();
       */
      int iPos=0;
      /*
       8 ms decmotion
       28 ms move blocks
       8 ms dec bframe
       
       1920x1080
       
       */
      
      
      int iErr=0;
      if(1){
         void debugsi(char *c, int a);;
         if(pack->iIsB && iBadNonB){
            iBadNonB=0;
            return 0;
         }
         //   unsigned  int t_timeGetTime();
         // unsigned int ui=t_timeGetTime();
         iErr=decodeTinaMotion(tinaMVDec,iMBSize, pack->decpos, pack->iIsB, iEncX,iEncY,p,iPos, iLen);
         if(iLen!=iPos || iErr){
            printf("[e-------------++++ %d l=%d w%d h%d b%d sz%d]",iErr,iLen,iEncX,iEncY,pack->iIsB,iMBSize);
            printf("[h%d c%d k%d kr%d p%d debl%d d%d]\n",pack->sizeofHdr,pack->iSizeOfChunk,pack->iIsKey,pack->iIsKRef,pack->iIsParts,pack->iDeblock,pack->iCanDraw);
            
            if(!pack->iIsB)iBadNonB=1;
            
            /*
             static int iSaved=0;
             if(!iSaved){
             void debugVectors(void *ctx, int MVSize,int iCur, int iIsB,int w, int h);
             debugVectors(tinaMVDec,iMBSize, pack->decpos, pack->iIsB, iEncX,iEncY);
             iSaved=1;
             }
             */
            return -1;
         }
         CMB4 *pv=getPtr_CMB4(tinaMVDec);
         //  unsigned int uiT=t_timeGetTime();
         // debugsi("mvd",uiT-ui);
         if(iIsUDP || !pack->iIsKey)
            moveVectorsRefMB4(iMBSize,pv->mb,dst,&ref[2], iEncX,iEncY,pack->iIsB,1);
         //debugsi("mvp",t_timeGetTime()-uiT);
         pv->iPrevMBSize=iMBSize;
      }
      else{
         /*
          iMBSize=p[0];
          p++;
          iLen--;
          iPos+=ac.decode(p,0,&vec1[0]);
          iPos+=ac.decode(p+iPos,0,&vec2[0]);
          iPos+=ac.decode(p+iPos,0,&vecRefId[0]);
          if(iLen!=iPos)debugss("err-dec-decodeVectors",0,0);
          moveVectorsRefD2(iMBSize,dst,&ref[2],&vec1[0],&vec2[0],&vecRefId[0],iEncX,iEncY);
          */
      }
      
      
      
      
      
      return 0;
   }
   
   //#endif
};
#endif
