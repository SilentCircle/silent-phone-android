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

#ifndef _CT_JPG_E_D_H
#define _CT_JPG_E_D_H


#include <stdio.h>
#include <string.h>

extern "C"{
#include "lib/jpeglib.h"
   void t_freeJpg(j_common_ptr cinfo);
   void t_onNewFrame(j_common_ptr cinfo);
};
#include "../CTVidCodec.h"


class CTJpgEnc{
   struct jpeg_compress_struct cinfo;
   struct jpeg_error_mgr jerr;
   T_JPG_OPT_STRUCT optJpg;
   int iOptCoding;
   
public:
   inline int getOptCoding(){return iOptCoding;}
   
   int iXOffset;
   int iYOffset;
   int x,y;
   int iFullImgW;
   int iFullImgH;
   
   int iQulaity;
   int iColorSpace;
   
   int iIsDif;
   int iFlagRot;
   
   unsigned int bufQuantYTable[64];
   unsigned char rowTmp[2048*3];
   
   CTJpgEnc(int x, int y):x(x),y(y){
      iFlagRot=0;
      iFullImgH=0;
      iFullImgW=0;
      iOptCoding=1;
      memset(&optJpg,0,sizeof(T_JPG_OPT_STRUCT));
      cinfo.client_data=&optJpg;
      //  memset(&cinfo,0,sizeof(struct jpeg_compress_struct));
      //#ifndef linux
      cinfo.err = jpeg_std_error(&jerr);
      iQulaity=50;
      iColorSpace=JCS_RGB;//JCS_RGB;//JCS_RGB;//JCS_GRAYSCALE,JCS_RGB,JCS_YCbCr
      iXOffset=iYOffset=0;
      iIsDif=0;
      //#endif
   }
   
   void start()
   {
      optJpg.iIsDif=iIsDif;
      optJpg.iHasYTable=0;
      optJpg.table=&bufQuantYTable[0];
      t_onNewFrame((j_common_ptr)&cinfo);
      //TODO varbuut nokopeet statusu peec init un tad uzkopeet virsuu
      //tad ja nekas nav mainiijies
      
      //#ifndef linux
      // iColorSpace=JCS_RGB;
      cinfo.in_color_space = (J_COLOR_SPACE)iColorSpace; 	/* colorspace of input image */
      jpeg_create_compress(&cinfo);
      //cinfo.jpeg_color_space=
      cinfo.in_color_space = (J_COLOR_SPACE)iColorSpace; 	/* colorspace of input image */
      cinfo.image_width = x;
      cinfo.image_height = y;
      cinfo.input_components = JCS_GRAYSCALE==iColorSpace?1:3;//3;		/* # of color components per pixel */
      jpeg_set_defaults(&cinfo,iQulaity);
      
      cinfo.dct_method = JDCT_DEFAULT;
      
      cinfo.optimize_coding=iOptCoding;//test
   }
   void optCoding(int iTrue)
   {
      iOptCoding=iTrue;
   }
   int encode(unsigned char *bufIn, unsigned char *bufOut)
   {
      //#ifndef linux
      if(iFullImgH==0)iFullImgH=y;
      if(iFullImgW==0)iFullImgW=x;
      jpeg_data_dest(&cinfo, bufOut);
      jpeg_start_compress(&cinfo, TRUE);
      
      unsigned char *p;
      
      //int row_stride=cinfo.image_width*cinfo.input_components;
      int row_stride=iFullImgW*cinfo.input_components;
      int iXOxC=iXOffset*cinfo.input_components;
      
      while (cinfo.next_scanline < cinfo.image_height)
      {
         if(iFlagRot){
            //z=cinfo.image_width*3;
            p=&bufIn[(cinfo.image_height-(cinfo.next_scanline +iYOffset+1)) * row_stride+iXOxC];
            
            (void) jpeg_write_scanlines(&cinfo, &p, 1);
         }
         else {
            p=&bufIn[(cinfo.next_scanline +iYOffset) * row_stride+iXOxC];
            (void) jpeg_write_scanlines(&cinfo, &p, 1);
         }
      }
      
      jpeg_finish_compress(&cinfo);
      //#endif
      return cinfo.dest->iBytesInFile;
   }
   void stop()
   {
      //#ifndef linux
      jpeg_destroy_compress(&cinfo);
      //#endif
   }
   virtual ~CTJpgEnc(){t_freeJpg((j_common_ptr)&cinfo);}
   
};

class CTJpgDec{
   struct jpeg_decompress_struct cinfo;
   struct jpeg_error_mgr jerr;
   int row_stride;
   JSAMPARRAY buffer;
   T_JPG_OPT_STRUCT optJpg;
   int iBytesInRowBuf;
   
public:
   int testx;
   int testy;
   int iXOffset;
   int iYOffset;
   int iFullImgW;
   int iFullImgH;
   int iOutColorSpace;
   int iFlagRot;
   
   int iPrevQ0;
   CTJpgDec(){
      iPrevQ0=16;
      iFlagRot=0;
      memset(&optJpg,0,sizeof(T_JPG_OPT_STRUCT));
      cinfo.client_data=&optJpg;
      iOutColorSpace=-1;
      
      
      //#ifndef linux
      cinfo.err = jpeg_std_error(&jerr);
      //#endif
      buffer=(JSAMPARRAY)new JSAMPARRAY[2];
      buffer[0]=NULL;
      buffer[1]=NULL;
      bmpBase=NULL;
      testx=testy=0;
      iFullImgW=iFullImgH=0;
      iXOffset=iYOffset=0;
      iBytesInRowBuf=0;
      
      // input_file=fopen("99.jpg","rb");
   }
   virtual ~CTJpgDec()
   {
      if(buffer)
      {
         if(buffer[0])delete buffer[0];
         if(buffer[1])delete buffer[1];
         delete buffer;
      }
      t_freeJpg((j_common_ptr)&cinfo);
      // if(input_file)
      // fclose(input_file);
   }
   CTBmpBase *bmpBase;
   void start()
   {
      t_onNewFrame((j_common_ptr)&cinfo);
      jpeg_create_decompress(&cinfo);
   }
   
   int decode(unsigned char *bufIn, unsigned char *bufOut, int iInLen)
   {
      int i=0;
      //#ifndef linux
      jpeg_data_src(&cinfo,bufIn, iInLen);
      
      (void) jpeg_read_header(&cinfo, TRUE);
      if(iOutColorSpace!=-1)cinfo.out_color_space=JCS_YCbCr;
      (void) jpeg_start_decompress(&cinfo);
      if(iOutColorSpace!=-1)cinfo.out_color_space=JCS_YCbCr;
      //sheit var saglabaat veco cinfo un tad atjaunot , ja memory ir tas pats
      if(testx && testx!=(int)cinfo.output_width)return -5;
      if(testy && testy!=(int)cinfo.output_height)return -5;
      testx=(int)cinfo.output_width;
      testy=(int)cinfo.output_height;
      
      
      
      //if(iFullImgW==0)iFullImgW=testx;
      //if(iFullImgH==0)iFullImgH=testy;
      
      //??? cinfo.quant_tbl_ptrs[0]->quantval[0]
      if(cinfo.quant_tbl_ptrs[0]){
         iPrevQ0=(cinfo.quant_tbl_ptrs[0]->quantval[0]*3+cinfo.quant_tbl_ptrs[0]->quantval[1])>>1;
      }
      if(iPrevQ0<8)iPrevQ0=8;else if(iPrevQ0>100)iPrevQ0=100;
      row_stride = cinfo.output_width * cinfo.output_components;
      if(iBytesInRowBuf<row_stride)
      {
         iBytesInRowBuf=row_stride;
         if(buffer[0])delete buffer[0];
         if(buffer[1])delete buffer[1];
         buffer[0]=new unsigned char[row_stride*2];
         buffer[1]=new unsigned char[row_stride*2];
      }
      
      
      if(bmpBase)
      {
         bmpBase->startDraw();
         
         while (cinfo.output_scanline < cinfo.output_height) {
            (void) jpeg_read_scanlines(&cinfo, buffer, 1);
            
            if(iFlagRot){
               bmpBase->setScanLine(cinfo.output_height-(i+iYOffset+1),iXOffset,(unsigned char*) buffer[0],row_stride,24);
            }
            else bmpBase->setScanLine(i+iYOffset,iXOffset,(unsigned char*) buffer[0],row_stride,24);
            i++;
         }
         bmpBase->endDraw();
      }
      else if(bufOut)
      {
         int iXOxC=0;//iXOffset* cinfo.output_components;
         while (cinfo.output_scanline < cinfo.output_height) {
            (void) jpeg_read_scanlines(&cinfo, buffer, 1);
            if(iFlagRot)
               memcpy(bufOut+row_stride*(cinfo.output_height-i-1)+iXOxC,buffer[0], row_stride);
            else
               memcpy(bufOut+row_stride*(i)+iXOxC,buffer[0], row_stride);
            i++;
         }
      }
      return row_stride*i;
   };
   void stop()
   {
      //#ifndef linux
      (void) jpeg_finish_decompress(&cinfo);
      jpeg_destroy_decompress(&cinfo);
      //#endif
   }
   
};

class CTJpg: public CVCodecBase{
   CTJpgEnc e;
public:
   CTJpg():e(0,0){}
   virtual int encode(unsigned char *pI, unsigned char *pO, int iLen)
   {
      e.start();
      iLen=e.encode(pI,pO);
      e.stop();
      return iLen;
   }
   //virtual int encodeNext(){return 0;};
   void setXY(int x, int y)
   {
      e.x=x;
      e.y=y;
      e.iFullImgW=x;
      e.iFullImgH=y;
   }
   int decode(unsigned char *pI, unsigned char *pO, int iLen){return 0;}
   void getDecXY(int &x, int &y){};
   const char *getName(){return "JPG";}
   const char *getShortName(){return getName();}
   int getId(){return 1111;}
   void setQuality(int q)
   {
      q=q>100?100:q<0?0:q;
      e.iQulaity=q;
   }
   
   
   
   
};

class CTJpgED: public CVCodecBase{
   CTJpgDec d;
   int iLast;
   typedef struct{
      short vers;
      short sizeofHdr;
      unsigned short cx;
      unsigned short cy;
      unsigned short offset;
      unsigned short allFrameSize;
   }T_JPG_RTP;
   int iMaxFrameSizeInc;
   int iPrevTmpIsInvalid;
   
   unsigned char *tmpOut;//[40000];
   int iBytesRemainInOutBuf;
   int iCurFrameSize;
   
   unsigned char *tmpIn;//[40000];
   int iTmpInBufSize;
   int iBytesInTmpBuf;
   
public:
   CTJpgEnc e;
   int iEnableLoopFilter;
   int iMaxFrameSize;
   int iSendSileceFrames;
   void setQuality(int q)
   {
      q=q>100?100:q<0?0:q;
      e.iQulaity=q;
      iq=q;
   }
   CTJpgED():e(0,0),d()
   {
      iPrevTmpIsInvalid=1;
      iEnableLoopFilter=1;
      iSendSileceFrames=0;
      iMaxFrameSizeInc=0;
      cVO=NULL;
      iMaxFrameSize=800;
      reset();
      tmpIn=tmpOut=NULL;
      iTmpInBufSize=0;
      setXY(80,60);
      cVO=NULL;
      iqConst=0;
      iSendNext=1;
      
      setQuality(30);
      
      iMaxQuality=75;
      prev_enc_frame_crc=0;
      
      
   }
   
   
   
   
   const char *getName(){return "TiVi-JPG";}
   const char *getShortName(){return getName();}
   int getId(){return 1112;}
   virtual ~CTJpgED(){delete tmpOut;delete tmpIn;}
   virtual int encodeNext()
   {
      return !iLast;
   };
   void reset()
   {
      /*
       iLast=1;
       iSendNext=2;
       */
      iLast=0;
      iSendNext=5;
      iBytesInTmpBuf=iCurFrameSize=iBytesRemainInOutBuf=0;
      iFrameCounter=iFramesPerSec=0;
      uiNextResetTs=0;
      
      
   }
   

   CTisVSilence colc;
   int iSendNext;
   int iq;
   int iqConst;
   int iMaxQuality;
   void setXY(int x, int y)
   {
    //  printf("[setjxy(%d,%d)\n",x,y);
      if(x>1024)x=1024;
      if(y>768)y=768;
      if(x<8)x=8;
      if(y<8)y=8;
      if(x>350)iMaxQuality=55;else iMaxQuality=70;
      if(e.x!=x || e.y!=y)
      {
         iPrevTmpIsInvalid=1;
         if(tmpOut)delete tmpOut;;
         tmpOut=new unsigned char [x*y*4];
      }
      if(iqConst)
         iq=iqConst;
      else
         if(x>160){iq=25;}else{iq=30;}
      
      
      e.x=x;
      e.y=y;
      e.iFullImgW=x;
      e.iFullImgH=y;
   }
   int iFramesPerSec,iFrameCounter;
   unsigned int uiNextResetTs;
private:
   
   int encode_priv(unsigned char *pI, unsigned char *pO, int iLen)
   {
      
      if(iLast)
      {
         iLast=0;
         return 0;
      }
#ifdef _WINDOWS_
      
      
      {
         unsigned int uiTS=GetTickCount();
         if(uiTS>uiNextResetTs)
         {
            uiNextResetTs=uiTS+2000;
            iFramesPerSec=iFrameCounter/2;
            iFrameCounter=0;
         }
         iFrameCounter++;
         
      }
#endif
      
      int l=0;
      T_JPG_RTP *v=(T_JPG_RTP *)pO;
      pO+=sizeof(T_JPG_RTP);
      
      v->vers=1;
      v->sizeofHdr=sizeof(T_JPG_RTP);
      v->cx=e.x;
      v->cy=e.y;
      
      if(iBytesRemainInOutBuf==0)//iLast)
      {
         
         if(iq>70)iq=70;else if(iq<20)iq=20;
         if(iMaxQuality>80)iMaxQuality=80;else if(iMaxQuality<20)iMaxQuality=20;
         e.iQulaity=iq;
         
         unsigned int crc32_calc_video(const void *ptr, size_t cnt);
         unsigned int crc = crc32_calc_video(pI, iLen);
         
         if( prev_enc_frame_crc==crc && iCurFrameSize>0 && !iPrevTmpIsInvalid){//video conf, don't send video frames 2x
            
            iBytesRemainInOutBuf=iCurFrameSize;
         //   printf("[crc=%u %d %d %d %d]",crc,iCurFrameSize,tmpOut[0],tmpOut[5], tmpOut[iCurFrameSize>>1]);
            if(iCurFrameSize==1){iBytesRemainInOutBuf=0; return 1;}
         }
         else{
            prev_enc_frame_crc = crc;
            
            colc.iDifConst=3;
            if(iSendSileceFrames==0 && colc.isVideoSilence(pI,v->cx,v->cy))
            {
               
               e.iQulaity=(iMaxQuality+iq)>>1;
               if(iSendNext<0){
                  iSendNext=0;
                  iCurFrameSize=1;
                  return 1;
               }
            }
            else
            {
               iSendNext=iFramesPerSec;
               if(iSendNext<2)iSendNext=2; else if(iSendNext>10)iSendNext=10;
            }
            iSendNext--;
            
            {
              // e.iQulaity=20;
               e.iFlagRot=1;//1;
               e.start();
               iCurFrameSize=iBytesRemainInOutBuf=e.encode(pI,tmpOut);
               e.stop();
               iPrevTmpIsInvalid=0;
               
           //    printf("[crc=%u %d %d %d %d]",crc,iCurFrameSize,tmpOut[0],tmpOut[5], tmpOut[iCurFrameSize>>1]);
            }
         }
      }
      
      v->offset=(unsigned short)(iCurFrameSize-iBytesRemainInOutBuf);
      v->allFrameSize=(unsigned short)iCurFrameSize;
      
      if(iBytesRemainInOutBuf>iMaxFrameSize+iMaxFrameSizeInc)
      {
         l=iMaxFrameSize+iMaxFrameSizeInc;
         iLast=0;
      }
      else
      {
         l=iBytesRemainInOutBuf;
         iLast=1;
      }
      unsigned char *p=tmpOut;
      p+=v->offset;
      memcpy(pO,p,l);
      iBytesRemainInOutBuf-=l;
      iMaxFrameSizeInc++;
      iMaxFrameSizeInc+=((p[l>>1]&7));
      iMaxFrameSizeInc&=63;
      /*
       iMaxFrameSize++;
       if(iMaxFrameSizeInc>50)
       {
       iMaxFrameSize-=iMaxFrameSizeInc;
       iMaxFrameSizeInc=0;
       }
       */
      //   printf("[jsend=%d]",l);
      return l+sizeof(T_JPG_RTP);
   }
   
   unsigned int prev_enc_frame_crc;
public:
   
   virtual int bytesEncodedLeft(){return iBytesRemainInOutBuf;}
   
   virtual int encode(unsigned char *pI, unsigned char *pO, int iLen)
   {
      int r=encode_priv(pI, pO, iLen);
      return r;
   }
   

   void getDecXY(int &x ,int  &y){}
   
   virtual int canSkipThis(unsigned char *p, int iLen){return 1;}
   virtual int isDrawableFrame(unsigned char *p, int iLen){
      if(iLen<sizeof(T_JPG_RTP))return 0;
      
      T_JPG_RTP *v=(T_JPG_RTP *)p;
     // p+=sizeof(T_JPG_RTP);
      iLen-=sizeof(T_JPG_RTP);
      if(v->sizeofHdr!=sizeof(T_JPG_RTP))return 0;
      
      if(v->offset+iLen==v->allFrameSize)return 1;
      
      return 0;
   }
   virtual int wasDrawableFrame(unsigned char *p, int iLen){
      return isDrawableFrame(p,iLen);
   }
   virtual int isHeaderOK(unsigned char *p, int iLen){
      if(iLen<sizeof(T_JPG_RTP))return 0;
      
      T_JPG_RTP *v=(T_JPG_RTP *)p;
   //   p+=sizeof(T_JPG_RTP);
      iLen-=sizeof(T_JPG_RTP);
      if(v->sizeofHdr!=sizeof(T_JPG_RTP))return 0;
      if(v->vers!=1)return 0;
      
      if(v->offset+iLen>v->allFrameSize)return 0;
      return 1;
   }
   
   
   virtual int decode(unsigned char *pI, unsigned char *pO, int iLen)
   {
      
      
      if(iLen<sizeof(T_JPG_RTP))return -2;
      T_JPG_RTP *v=(T_JPG_RTP *)pI;
      
      // printf("[bi=%d vo=%d l=%d va=%d]",iBytesInTmpBuf, v->offset,iLen, v->allFrameSize);
      
      pI+=sizeof(T_JPG_RTP);
      iLen-=sizeof(T_JPG_RTP);
      if(v->sizeofHdr!=sizeof(T_JPG_RTP))return -2;
      
      if(v->vers!=1)return -3;
      
      
      //cmp iBytesInTmpBuf==v->offset
      if(iBytesInTmpBuf!=v->offset)
      {
         iBytesInTmpBuf=0;
         return 0;
      }
      if(iTmpInBufSize<v->allFrameSize)
      {
         iTmpInBufSize=v->allFrameSize;
         if(iTmpInBufSize<500000)
         {
            iTmpInBufSize*=2;
           // iTmpInBufSize*=10;
         }
         else
            return -1;
         if(tmpIn)delete tmpIn;
         tmpIn=new unsigned char [iTmpInBufSize];
      }
      //jane tad gaidiit kameer offset ==0
      unsigned char *p=tmpIn;
      p+=iBytesInTmpBuf;
      memcpy(p,pI,iLen);
      iBytesInTmpBuf+=iLen;
      //TODO check v->offset+iLen==iBytesInTmpBuf
      int l=0;
      //    printf("vo=%d l=%d vol=%d va=%d\n",v->offset,iLen, v->offset+iLen,v->allFrameSize);
      
      if(v->offset+iLen==v->allFrameSize)
      {
         if(cVO)
         {
            cVO->setOutputPictureSize(v->cx,v->cy);
            
         }
         
         if(cVO==NULL || cVO->rawJpgData(tmpIn,iBytesInTmpBuf)==-1)
         {
            d.testx=v->cx;
            d.testy=v->cy;
            d.iFlagRot=1;
            
            if(!iEnableLoopFilter)d.bmpBase=cVO;
            else d.bmpBase=NULL;
            
            if(!pO)setXY(v->cx,v->cy);//new
         //   printf("[Try jdec (xy%d,%d s%d,%d vh%d,%d)]\n",v->cx,v->cy,v->allFrameSize, v->offset, v->vers, v->sizeofHdr);
            //printf("jpg [%x %x %x %x]\n", tmpIn[0], tmpIn[1], tmpIn[2], tmpIn[3]);//jpg [ff d8 ff e0]
            
            if(tmpIn[0]!=0xff || tmpIn[1]!=0xd8 || tmpIn[2]!=0xff  || tmpIn[3]!=0xe0){
               puts("JPG-ERR:hdr");
            }
            else{
            
               d.start();
               //TODO ???? decoderii paarbaudiit vai tik daduz vietas ir out buferi
               if(pO)l=d.decode(tmpIn,pO,iBytesInTmpBuf);
               else l=d.decode(tmpIn,tmpOut,iBytesInTmpBuf);
               d.stop();
               // printf("[Try jdec ok (xy%d,%d s%d,%d vh%d,%d)]\n",v->cx,v->cy,v->allFrameSize, v->offset, v->vers, v->sizeofHdr);
               
               if(iEnableLoopFilter && l>=0 && cVO && cVO!=d.bmpBase){
                  // printf("iPrevQ0=%d",d.iPrevQ0);
                  int r=v->cy;
                  int str=v->cx*3;
                  void tblockRGB(unsigned char *pic, int w, int h, int q);
                  tblockRGB(tmpOut,v->cx,v->cy,d.iPrevQ0);
                  
                  cVO->startDraw();
                  for(int i=0;i<r;i++){
                     cVO->setScanLine(i,tmpOut+i*str, str, 24);
                  }
                  cVO->endDraw();
               }
            }
         }
         l=1;
         iBytesInTmpBuf=0;
         
      }
      return l;
   }
   
};

static int jpgFilter(unsigned char *p, unsigned char *bufTmp, int wi, int hi, int iLen, int q)
{
   if(q>98)q=98;
   CTJpgEnc e(wi,hi);
   e.iQulaity=q;
   //   e.iIsDif=1;
   e.start();
   //e.iIsDif=1;
   int l=e.encode(p,(unsigned char*)bufTmp);
   e.stop();
   
   // unsigned int uiT=(unsigned int)timeGetTime ();
   CTJpgDec d;
   d.start();
   d.decode((unsigned char*)bufTmp,p,l);
   d.stop();
   return l;// (int)(timeGetTime ()-uiT);
}


#endif// _CT_JPG_E_D_H

