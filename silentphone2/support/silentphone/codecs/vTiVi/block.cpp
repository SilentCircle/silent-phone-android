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

#if 1

#define T_CAN_TEST_V

#include "block.h"
#include <string.h>




#if defined(_WIN32_WCE) || defined(__SYMBIAN32__) || defined(ARM) || defined(ANDROID_NDK) || defined(__APPLE__)
#define T_ALIGN_4
#endif



#define t_INL inline 

#if 0
#include <emmintrin.h>

/*
   MOTI_TYPE* end=dsti+w*h;
   __m128i m1;//,mRes;
   while(dsti<end)
   {
      m1=_mm_loadu_si128((__m128i*)src2i);
      //mRes=_mm_avg_epu16(*(__m128i*)src1i,m1);_mm_storeu_si128((__m128i*)dsti,mRes);
      *(__m128i*)dsti=_mm_avg_epu16(*(__m128i*)src1i,m1);
     dsti+=8;
     src2i+=8;
     src1i+=8;

   }

*/

static t_INL void doRow16(unsigned char *dst, unsigned char *src, unsigned char *src2, int iLen){
   __m128i m1;//,mRes;
   __m128i m2;//,mRes;
   while(iLen)
   {
      m1=_mm_loadu_si128((__m128i*)src);
      m2=_mm_loadu_si128((__m128i*)src2);
      //mRes=_mm_avg_epu16(*(__m128i*)src1i,m1);_mm_storeu_si128((__m128i*)dsti,mRes);
      *(__m128i*)dst=_mm_avg_epu8(m2,m1);
     dst+=16;
     src+=16;
     src2+=16;
     iLen--;
   }

}
#endif


template<int w, int h>
static t_INL void move_bl(unsigned char *dst, unsigned char *src, const int stride, const int stridedst){
   int i,j;
   
#ifndef T_ALIGN_4
#define PX(_X)*(int*)&dst[_X]=*(int*)&src[_X]
#define PX8(_X)*(long long*)&dst[_X]=*(long long*)&src[_X]

   if(w==12){
      for(j=0;j<h;j++){PX(0);PX(4);PX(8);dst+=stridedst;      src+=stride;}
      //for(j=0;j<h;j++){PX8(0);PX(8);dst+=stridedst;      src+=stride;}
   }
   else if(w==24){
      for(j=0;j<h;j++){PX(0);PX(4);PX(8);PX(12);PX(16);PX(20);dst+=stridedst;      src+=stride;}
//      for(j=0;j<h;j++){PX8(0);PX8(8);PX8(16);dst+=stridedst;      src+=stride;}
   }
   else if(w==48){
      for(j=0;j<h;j++){
         PX(0);PX(4);PX(8);PX(12);PX(16);PX(20);
         PX(24);PX(28);PX(32);PX(36);PX(40);PX(44);
      dst+=stridedst;      src+=stride;
      }
//      for(j=0;j<h;j++){PX8(0);PX8(8);PX8(16);PX8(24);PX8(32);PX8(40);dst+=stridedst;      src+=stride;}
   }
   else if(w==16){
      for(j=0;j<h;j++){PX(0);PX(4);PX(8);PX(12);dst+=stridedst;      src+=stride;}
   }
   else if(w==8){
      for(j=0;j<h;j++){PX(0);PX(4);dst+=stridedst;      src+=stride;}
   }
   else if(w==4){for(j=0;j<h;j++){PX(0);}dst+=stridedst;      src+=stride;}
   else 
#endif
   {
      for(j=0;j<h;j++){
         for(i=0;i<w;i++)dst[i]=src[i];
         dst+=stridedst;      src+=stride;
      }
   }
}

template<int w, int h, int str>
static t_INL  void move_HV(unsigned char *dst, unsigned char *src
, const int stride, const int stridedst){
   unsigned char *src2=src+str+(!str)*stride;
   int i,j;

   
   for(j=0;j<h;j++){
      for(i=0;i<w;i+=4){
         dst[i]=(src[i]+src2[i]+1)>>1;
         dst[i+1]=(src[i+1]+src2[i+1]+1)>>1;
         dst[i+2]=(src[i+2]+src2[i+2]+1)>>1;
         dst[i+3]=(src[i+3]+src2[i+3]+1)>>1;
      }
      dst+=stridedst;
      src+=stride;
      src2+=stride;

   }
}

template<int w, int h, int str>
static t_INL  void move_XY(unsigned char *dst, unsigned char *src,const int stride, const int stridedst){

   int i,j;
   
   
   
   unsigned char *src2=src+str;
   unsigned char *src3=src+stride;
   unsigned char *src4=src+stride+str;
   for(j=0;j<h;j++){
      for(i=0;i<w;i+=4){
         dst[i]=(src[i]+src2[i]+src3[i]+src4[i]+2)>>2;
         dst[i+1]=(src[i+1]+src2[i+1]+src3[i+1]+src4[i+1]+2)>>2;
         dst[i+2]=(src[i+2]+src2[i+2]+src3[i+2]+src4[i+2]+2)>>2;
         dst[i+3]=(src[i+3]+src2[i+3]+src3[i+3]+src4[i+3]+2)>>2;
      }
      dst+=stridedst;
      src+=stride;
      src2+=stride;
      src3+=stride;
      src4+=stride;
   }
   
}




/*
M_FNC4(12,4,3)
M_FNC4(24,8,3)
M_FNC4(48,16,3)
*/



template<int w, int h>
static t_INL void t_move_bi(unsigned char *dst, unsigned char *src1, t_move_pel_fnc *fnc1,
                                                             unsigned char *src2, t_move_pel_fnc *fnc2, const int stride){
#ifdef T_ALIGN_4
   static unsigned char *m1=NULL;
   static unsigned char *m2=NULL;
   if(m1==NULL){
      m1=new unsigned char [w*h+w+32];
      m2=new unsigned char [w*h+w+32];
      m1=(unsigned char*)(((size_t) m1+31)&(~31));
      m2=(unsigned char*)(((size_t) m2+31)&(~31));

   }
#else
#ifdef _MSC_VER
#define DECLARE_ALIGNED( var, n ) __declspec(align(n)) var
#else
#define DECLARE_ALIGNED( var, n ) var __attribute__((aligned(n)))
#endif   

   DECLARE_ALIGNED(unsigned char m1[w*h+w],64);
   DECLARE_ALIGNED(unsigned char m2[w*h+w],64);

   //static unsigned char m1[w*h+w];
   //static unsigned char m2[w*h+w];
#endif
   unsigned char *p1;
   unsigned char *p2;

   int stride1,stride2;
   int i,j;
   
   if(fnc1){ fnc1[0](&m1[0],src1,stride,w);p1=&m1[0]; stride1=w;}else {p1=src1;stride1=stride;}
   if(fnc2){ fnc2[0](&m2[0],src2,stride,w);p2=&m2[0]; stride2=w;}else {p2=src2;stride2=stride;}
   
   
//--   move_bi2<w,h>(dst,p1,p2,stride1,stride2,stride);
   
   for(j=0;j<h;j++){

      for(i=0;i<w;i+=4){
         dst[i]=(p1[i]+p2[i]+1)>>1;
         dst[i+1]=(p1[i+1]+p2[i+1]+1)>>1;
         dst[i+2]=(p1[i+2]+p2[i+2]+1)>>1;
         dst[i+3]=(p1[i+3]+p2[i+3]+1)>>1;
      }
      
      dst+=stride;
      p1+=stride1;
      p2+=stride2;
   }
}


template<int w, int h>
static t_INL void move_bl1(unsigned char *dst, unsigned char *src, const int stride, const int stridedst){
   int j;
   for(j=0;j<h;j++){
      memcpy(dst,src,w<<1);
      src+=stride;
      dst+=stridedst;
   }
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

   p->mt[T_MOVE_PEL::eWH4][0]=move_bl1<12,4>;
   p->mt[T_MOVE_PEL::eWH8][0]=move_bl1<24,8>;
   p->mt[T_MOVE_PEL::eWH16][0]=move_bl1<48,16>;
   p->mt[T_MOVE_PEL::eWH32][0]=move_bl1<96,32>;

   p->mt[T_MOVE_PEL::eWH4][1]=move_bl1<12,4>;
   p->mt[T_MOVE_PEL::eWH8][1]=move_bl1<24,8>;
   p->mt[T_MOVE_PEL::eWH16][1]=move_bl1<48,16>;
   p->mt[T_MOVE_PEL::eWH32][1]=move_bl1<96,32>;

   p->mt[T_MOVE_PEL::eWH4][2]=move_bl1<12,4>;
   p->mt[T_MOVE_PEL::eWH8][2]=move_bl1<24,8>;
   p->mt[T_MOVE_PEL::eWH16][2]=move_bl1<48,16>;
   p->mt[T_MOVE_PEL::eWH32][2]=move_bl1<96,32>;

   p->mt[T_MOVE_PEL::eWH4][3]=move_bl1<12,4>;
   p->mt[T_MOVE_PEL::eWH8][3]=move_bl1<24,8>;
   p->mt[T_MOVE_PEL::eWH16][3]=move_bl1<48,16>;
   p->mt[T_MOVE_PEL::eWH32][3]=move_bl1<96,32>;

   p->mbit[T_MOVE_PEL::eWH16]=t_move_bi<48,16>;
   p->mbit[T_MOVE_PEL::eWH32]=t_move_bi<96,32>;

   
}

#endif
