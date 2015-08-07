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

#ifndef _CT_RGB_YUV
#define _CT_RGB_YUV

//TODO arm neon
//https://code.ros.org/trac/opencv/browser/trunk/opencv/android/android-jni/jni/yuv2rgb_neon.c?rev=4083

class CTRgb_Yuv{
   unsigned char crp_t[1024+512+256];
   unsigned int crp_tR[1024+512+256];
   unsigned int crp_tG[1024+512+256];
   unsigned int crp_tB[1024+512+256];
   unsigned short crp_tR2[1024+512+256];
   unsigned short crp_tG2[1024+512+256];
   unsigned short crp_tB2[1024+512+256];
   int tY[256];
   int tU[256];
   int tV[256];
   int tU2[256];
   int tV2[256];
   unsigned char *crp;
   unsigned int *crpR;
   unsigned int *crpG;
   unsigned int *crpB;
   unsigned short *crpR2;
   unsigned short *crpG2;
   unsigned short *crpB2;
/*
   int YV[256][256];
   int YU[256][256];
  */ 
public:
   /*
     int u = p[1]-128;
     int v = p[2]-128;

      const int yc=298 * ((int)p[0]-16)+128;


      out[0] = tab[(( yc           + 409 * v ) >> 8)];
      out[1] = tab[(( yc - 100 * u - 208 * v ) >> 8)];
      out[2] = tab[(( yc + 516 * u           ) >> 8)];
*/
   CTRgb_Yuv(){
      crp=&crp_t[768];
      crpR=&crp_tR[768];
      crpG=&crp_tG[768];
      crpB=&crp_tB[768];
      crpR2=&crp_tR2[768];
      crpG2=&crp_tG2[768];
      crpB2=&crp_tB2[768];
      int i;
      for(i=0;i<256;i++){
         tV[i]=(i-128)*409;
         tU[i]=(i-128)*516;
         tY[i]=128+298*(i-16);

         tU2[i]=-(i-128)*100;
         tV2[i]=-(i-128)*208;
         
      }
      for(i=-768;i<1023;i++){
         const unsigned int v=i<0?(unsigned int)0:(i>255?(unsigned int)255:(unsigned int)i);
         crp[i]=v;
         crpR[i]=(v)<<16;
         crpG[i]=(v)<<8;
         crpB[i]=(v)|0xff000000;

         crpR2[i]=((v)>>3);crpR2[i]<<=11;//5+6+5
         crpG2[i]=((v)>>2);crpG2[i]<<=5;
         crpB2[i]=((v)>>3);
      }
      /*
      for(j=0;j<256;j++){
      for(i=0;i<256;i++){
         const int yc=298 * ((int)i-16)+128;
         YV[i][j]=(yc+409*j)>>8;
         if(YV[i][j]<0)YV[i][j]=0;else if(YV[i][j]>255)YV[i][j]=255;

         YU[i][j]=(yc+516*j)>>8;
         if(YV[i][j]<0)YV[i][j]=0;else if(YV[i][j]>255)YV[i][j]=255;

      }
      }
      */
   }
   static void toRgb(unsigned char *p, int w, int h, unsigned char *out
                             , unsigned char *crop, int *yT ,int *uT,int *vT 
                              , int *uT2,int *vT2)
  {
#define SH  >>8
      unsigned char *pEnd=p+w*h*3;
      while(pEnd>p){

         //out[0] = tab[(( yc           + 409 * v ) >> 8)];
         //out[1] = tab[(( yc - 100 * u - 208 * v ) >> 8)];
         //out[2] = tab[(( yc + 516 * u           ) >> 8)];         
         const int yc=yT[p[0]];
         const unsigned int u=p[1];
         const unsigned int v=p[2];
         //const int yc=298 * ((int)p[0]-16)+128;
         //>>8
        //TODO out to 32bit ,  *(int*)out=cropR[(( yc          + vT[v] ) SH)]|
//                                        cropG[(( yc          + vT[v] ) SH)]|
  //                                      cropB[(( yc          + vT[v] ) SH)];
        //TODO out to 16bit ,  *(unsigned short*)out=cropR[(( yc          + vT[v] ) SH)]|
//                                        cropG[(( yc          + vT[v] ) SH)]|
  //                                      cropB[(( yc          + vT[v] ) SH)];
         out[2] = crop[(( yc          + vT[v] ) SH)];
         out[1] = crop[(( yc + uT2[u] + vT2[v]  ) SH)];
         out[0] = crop[(( yc + uT[u] ) SH)];         
         p+=3;
         out+=3;         
      }
      
   }
   inline void toRgb(unsigned char *p, unsigned char *out){
      const int yc=tY[p[0]];
      const unsigned int u=p[1];
      const unsigned int v=p[2];

      out[2] = crp[(( yc          + tV[v] ) SH)];
      out[1] = crp[(( yc + tU2[u] + tV2[v]  ) SH)];
      out[0] = crp[(( yc + tU[u] ) SH)];         

   }
   template <class T>
   inline void toRgb(T y,T u,T v, unsigned char *out){
      const int yc=tY[y];

      out[2] = crp[(( yc          + tV[v] ) SH)];
      out[1] = crp[(( yc + tU2[u] + tV2[v]  ) SH)];
      out[0] = crp[(( yc + tU[u] ) SH)];         

   }
  
   inline void toRgb32BRG(unsigned char *p, int w, int h, unsigned int *out){
      //      toRgb(p,w,h,out,crp,&tY[0],&tU[0],&tV[0],&tU2[0],&tV2[0]);
      unsigned char *pEnd=p+w*h*3;
      while(pEnd>p){
         
         const int yc=tY[p[0]];
         const unsigned int u=p[1];
         const unsigned int v=p[2];
         out[0] = crpR[(( yc + tU[u]  ) SH)]
         | crpG[(( yc + tU2[u] + tV2[v]  ) SH)]
         | crpB[((yc          + tV[v] ) SH)] ;        
         p+=3;
         out++;         
      }
   }

   inline void toRgb32(unsigned char *p, int w, int h, unsigned int *out){
//      toRgb(p,w,h,out,crp,&tY[0],&tU[0],&tV[0],&tU2[0],&tV2[0]);
      unsigned char *pEnd=p+w*h*3;
      while(pEnd>p){

         //out[0] = tab[(( yc           + 409 * v ) >> 8)];
         //out[1] = tab[(( yc - 100 * u - 208 * v ) >> 8)];
         //out[2] = tab[(( yc + 516 * u           ) >> 8)];         
         const int yc=tY[p[0]];
         const unsigned int u=p[1];
         const unsigned int v=p[2];
         //const int yc=298 * ((int)p[0]-16)+128;
         //>>8
        //TODO out to 32bit ,  *(int*)out=cropR[(( yc          + vT[v] ) SH)]|
//                                        cropG[(( yc          + vT[v] ) SH)]|
  //                                      cropB[(( yc          + vT[v] ) SH)];
        //TODO out to 16bit ,  *(unsigned short*)out=cropR[(( yc          + vT[v] ) SH)]|
//                                        cropG[(( yc          + vT[v] ) SH)]|
  //                                      cropB[(( yc          + vT[v] ) SH)];
         out[0] = crpR[(( yc          + tV[v] ) SH)]
           | crpG[(( yc + tU2[u] + tV2[v]  ) SH)]
           | crpB[(( yc + tU[u] ) SH)] ;        
         p+=3;
         out++;         
      }
   }
   inline void toRgb16BRG(unsigned char *p, int w, int h, unsigned short *out){
      unsigned char *pEnd=p+w*h*3;
      while(pEnd>p){
         const int yc=tY[p[0]];
         const unsigned int u=p[1];
         const unsigned int v=p[2];
         out[0] = crpR2[(( yc          + tV[v] ) SH)]
           | crpG2[(( yc + tU2[u] + tV2[v]  ) SH)]
           | crpB2[(( yc + tU[u] ) SH)] ;        
         p+=3;
         out++;         
      }
   }
   inline void toRgb16(unsigned char *p, int w, int h, unsigned short *out){
      unsigned char *pEnd=p+w*h*3;
      while(pEnd>p){
         const int yc=tY[p[0]];
         const unsigned int u=p[1];
         const unsigned int v=p[2];
         out[0] = crpR2[(( yc + tU[u] ) SH)]
           | crpG2[(( yc + tU2[u] + tV2[v]  ) SH)]
           | crpB2[(( yc          + tV[v] ) SH)] ;        
         p+=3;
         out++;         
      }
   }
   inline void toRgb(unsigned char *p, int w, int h, unsigned char *out){
    //  unsigned char *getCropTbl();
  //    crp=getCropTbl(); 
      toRgb(p,w,h,out,crp,&tY[0],&tU[0],&tV[0],&tU2[0],&tV2[0]);
   }

   
      
};
#undef SH

#endif


