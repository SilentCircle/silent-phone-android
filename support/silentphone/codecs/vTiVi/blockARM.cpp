/*
Created by Janis Narbuts
Copyright © 2004-2012 Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

#if 0
#include "block.h"



template<int w, int h>
static inline void move_bl(unsigned char *dst, unsigned char *src, const int stride, const int stridedst){
   int i,j;
   for(j=0;j<h;j++){
      for(i=0;i<w;i++)dst[i]=src[i];
      dst+=stridedst;
      src+=stride;
   }
}

template<int w, int h, int str>
static inline void move_HV(unsigned char *dst, unsigned char *src
, const int stride, const int stridedst){
   unsigned char *src2=src+str+(!str)*stride;
   int i,j;
   for(j=0;j<h;j++){
     for(i=0;i<w;i++){
        dst[i]=(src[i]+src2[i]+1)>>1;
     }
      dst+=stridedst;
      src+=stride;
      src2+=stride;
   }
}


template<int w, int h>
static inline void move_bl_2_s3(unsigned char *dst, unsigned char *src, unsigned char *src2
, const int stride, const int stride2, const int stridedst){
   int i,j;
   for(j=0;j<h;j++){

     for(i=0;i<w;i++){
        dst[i]=(src[i]+src2[i]+1)>>1;
     }

      dst+=stridedst;
      src+=stride;
      src2+=stride2;
   }
}


template<int w, int h, int str>
static inline void move_XY(unsigned char *dst, 
        unsigned char *src,const int stride, const int stridedst){

  int i,j;
  unsigned char *src2=src+str;
  unsigned char *src3=src+stride;
  unsigned char *src4=src+stride+str;
  for(j=0;j<h;j++){
     for(i=0;i<w;i++){
        dst[i]=(src[i]+src2[i]+src3[i]+src4[i]+2)>>2;
     }
     dst+=stridedst;
     src+=stride;
     src2+=stride;
     src3+=stride;
     src4+=stride;
  }
}



template<int w, int h>
static void t_move_bi(unsigned char *dst, unsigned char *src1, t_move_pel_fnc *fnc1,
                                                             unsigned char *src2, t_move_pel_fnc *fnc2, const int stride){
   static unsigned char m1[w*h+w];
   static unsigned char m2[w*h+w];
   unsigned char *p1;
   unsigned char *p2;
   int stride1,stride2;
   
   if(fnc1){ fnc1[0](&m1[0],src1,stride,w);p1=&m1[0]; stride1=w;}else {p1=src1;stride1=stride;}
   if(fnc2){ fnc2[0](&m2[0],src2,stride,w);p2=&m2[0]; stride2=w;}else {p2=src2;stride2=stride;}
   
   
   move_bl_2_s3<w,h>(dst,p1,p2,stride1,stride2,stride);
   
}

void initMoveBl(T_MOVE_PEL *p){
#if 0
   p->m[T_MOVE_PEL::eWH4][0]=move_4;
   p->m[T_MOVE_PEL::eWH8][0]=move_8;
   p->m[T_MOVE_PEL::eWH16][0]=move_16;

   p->m[T_MOVE_PEL::eWH4][1]=move_hv_4_3;
   p->m[T_MOVE_PEL::eWH8][1]=move_hv_8_3;
   p->m[T_MOVE_PEL::eWH16][1]=move_hv_16_3;
   
   p->m[T_MOVE_PEL::eWH4][2]=move_hv_4_0;
   p->m[T_MOVE_PEL::eWH8][2]=move_hv_8_0;
   p->m[T_MOVE_PEL::eWH16][2]=move_hv_16_0;

   p->m[T_MOVE_PEL::eWH4][3]=move4_4;
   p->m[T_MOVE_PEL::eWH8][3]=move4_8;
   p->m[T_MOVE_PEL::eWH16][3]=move4_16;
#else
   p->m[T_MOVE_PEL::eWH4][0]=move_bl<12,4>;
   p->m[T_MOVE_PEL::eWH8][0]=move_bl<24,8>;
   p->m[T_MOVE_PEL::eWH16][0]=move_bl<48,16>;
   p->m[T_MOVE_PEL::eWH32][0]=move_bl<96,32>;

   p->m[T_MOVE_PEL::eWH4][1]=move_HV<12,4,3>;
   p->m[T_MOVE_PEL::eWH8][1]=move_HV<24,8,3>;
   p->m[T_MOVE_PEL::eWH16][1]=move_HV<48,16,3>;
   p->m[T_MOVE_PEL::eWH32][1]=move_HV<96,32,3>;

   p->m[T_MOVE_PEL::eWH4][2]=move_HV<12,4,0>;
   p->m[T_MOVE_PEL::eWH8][2]=move_HV<24,8,0>;
   p->m[T_MOVE_PEL::eWH16][2]=move_HV<48,16,0>;
   p->m[T_MOVE_PEL::eWH32][2]=move_HV<96,32,0>;


   p->m[T_MOVE_PEL::eWH4][3]=move_XY<12,4,3>;
   p->m[T_MOVE_PEL::eWH8][3]=move_XY<24,8,3>;
   p->m[T_MOVE_PEL::eWH16][3]=move_XY<48,16,3>;
   p->m[T_MOVE_PEL::eWH32][3]=move_XY<96,32,3>;
#endif

   p->mbi[T_MOVE_PEL::eWH16]=t_move_bi<48,16>;
   p->mbi[T_MOVE_PEL::eWH32]=t_move_bi<96,32>;
}
#endif
