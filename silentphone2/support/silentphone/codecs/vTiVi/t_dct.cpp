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

//#include "fdct.h"
#define T_CAN_TEST_V

#include <stdlib.h>


#include <string.h>
#define DCT_TYPE short 
//short
void convHaar(DCT_TYPE* input, DCT_TYPE *output, int l)
{
   int i2,i,length;
   DCT_TYPE a,b;
 

    for (length = l >> 1; ; length >>= 1)
   {
        //length=2^n, WITH DECREASING n
        for (i = 0; i < length; i++) {
            i2=i<<1;
            //int sum = input[i2]+input[i2+1];
            //int difference = input[i2]-input[i2+1];
            a=input[i2];
            b=input[i2+1];
            output[i] = a+b;
            output[length+i] = a-b;
        //printf("  %d,  %d\n",length,i);
        }
        if (length == 1) 
            return ;//output;
        memcpy(input,output,(length<<1)*sizeof(DCT_TYPE));
    }
}
void rotCF(DCT_TYPE *in, DCT_TYPE *out, int l)
{
   int i,j;
   for(j=0;j<l;j++)
      for(i=0;i<l;i++)
      {
         out[i*l+j]=in[i+l*j];
      }
}
void fdct_int32haar(const DCT_TYPE *in)
{
   DCT_TYPE out[64];
   DCT_TYPE out2[64];
   int i;
   const int l=4;
   for(i=0;i<l;i++)convHaar((DCT_TYPE*)in+i*l,(DCT_TYPE*)out2+i*l,l);
   rotCF(out2,out,l);
   for(i=0;i<l;i++)convHaar((DCT_TYPE*)out+i*l,(DCT_TYPE*)in+i*l,l);

}

/* private data
 * Initialized by idct_int32_init so it's mostly RO data,
 * doesn't hurt thread safety */
//static short xiclip[1024];		/* clipping table */
//static short *xiclp;

#if  !defined(_WIN32_WCE) && !defined(__SYMBIAN32__) && !defined(ARM) && !defined(ANDROID_NDK) 

static short xiclip_0_256[4096];		/* clipping table */
static short *xiclp_0_256=NULL;

static unsigned char xiclip_0_256x[4096];		/* clipping table */
static unsigned char *xiclp_0_256x=NULL;
#else

static short *xiclip_0_256=NULL;		/* clipping table */
static short *xiclp_0_256=NULL;

static unsigned char *xiclip_0_256x=NULL;		/* clipping table */
static unsigned char *xiclp_0_256x=NULL;

#endif



static void dct2x2dc_s_d(unsigned char *src,unsigned char *src2, int stride, DCT_TYPE *d )
{
    int tmp[2][2];
    
    DCT_TYPE a00,a01,a10,a11;
    a00=src[0]-src2[0];
    a01=src[3]-src2[3];
    src+=stride;src2+=stride;
    a10=src[0]-src2[0];
    a11=src[3]-src2[3];

    tmp[0][0] = a00 + a01;
    tmp[1][0] = a00 - a01;
    tmp[0][1] = a10 + a11;
    tmp[1][1] = a10 - a11;

    d[0] = tmp[0][0] + tmp[0][1];
    d[1] = (tmp[1][0] + tmp[1][1]+1)>>1;
    d[2] = (tmp[0][0] - tmp[0][1]+1)>>1;
    d[3] = (tmp[1][0] - tmp[1][1]+1)>>1;

    //d[0]+=2;d[1]+=2;d[2]+=2;d[3]+=2;
    //d[0]>>=2;d[1]>>=2;d[2]>>=2;d[3]>>=2;
}
void t_fdc2_4(DCT_TYPE *x){
   int r01,r02,r03,r04;
   r01 = x[0];
   r02 = x[1];
   r03 = x[2];
   r04 = x[3];

   x[0] = (+ r01 + r02 + r03 + r04+1)>>1; ;
   x[1] = (- r01 + r02 - r03 + r04+1)>>1;  ;
   x[2] = (- r01 - r02 + r03 + r04+1)>>1;  ;
   x[3] = (+ r01 - r02 - r03 + r04+1)>>1;  ;
}
void t_idc2_4(DCT_TYPE *x){
   int r01,r02,r03,r04;
   r01 = x[0];
   r02 = x[1];
   r03 = x[2];
   r04 = x[3];

  x[0] = (r01 - r02 - r03 + r04+1)>>1;
  x[1] = (r01 + r02 - r03 - r04+1)>>1;
  x[2] = (r01 - r02 + r03 - r04+1)>>1;
  x[3] = (r01 + r02 + r03 + r04+1)>>1;
}

void t_fdc2(DCT_TYPE *x){
   int r01,r02,r03,r04;
   r01 = x[0];
   r02 = x[16];
   r03 = x[32];
   r04 = x[48];

   x[0] = (+ r01 + r02 + r03 + r04+1)>>1; ;
   x[16] = (- r01 + r02 - r03 + r04+1)>>1;  ;
   x[32] = (- r01 - r02 + r03 + r04+1)>>1;  ;
   x[48] = (+ r01 - r02 - r03 + r04+1)>>1;  ;
}
void t_idc2(DCT_TYPE *x){
   int r01,r02,r03,r04;
   r01 = x[0];
   r02 = x[16];
   r03 = x[32];
   r04 = x[48];

  x[0] = (r01 - r02 - r03 + r04+1)>>1;
  x[16] = (r01 + r02 - r03 - r04+1)>>1;
  x[32] = (r01 - r02 + r03 - r04+1)>>1;
  x[48] = (r01 + r02 + r03 + r04+1)>>1;
}

static void dct2x2dc_s_p(unsigned char *src, int stride, DCT_TYPE *d )
{
    int tmp[2][2];
    
    tmp[0][0] = src[0] + src[3];
    tmp[1][0] = src[0] - src[3];
    src+=stride;
    tmp[0][1] = src[0] + src[3];
    tmp[1][1] = src[0] - src[3];

    d[0] = tmp[0][0] + tmp[0][1];
    d[1] = tmp[1][0] + tmp[1][1];
    d[2] = tmp[0][0] - tmp[0][1];
    d[3] = tmp[1][0] - tmp[1][1];
}

static void idct2x2dc_s_d( DCT_TYPE *d, unsigned char *dst, int stride)
{
    int tmp[2][2];
    
    d[1]<<=1;
    d[2]<<=1;

    int d3=(int)d[3]<<1;
    d[0]+=2;

    tmp[0][0] = d[0] + d[1];
    tmp[1][0] = d[0]- d[1];
    tmp[0][1] = d[2] + d3;
    tmp[1][1] = d[2] - d3;

    dst[0] = xiclp_0_256x[dst[0]+((tmp[0][0] + tmp[0][1])>>2)];
    dst[3] = xiclp_0_256x[dst[3]+((tmp[1][0] + tmp[1][1])>>2)];
    dst+=stride;
    dst[0] = xiclp_0_256x[dst[0]+((tmp[0][0] - tmp[0][1])>>2)];
    dst[3] = xiclp_0_256x[dst[3]+((tmp[1][0] - tmp[1][1])>>2)];
}
#if 0
void fdct4x4dc_s( int *d){
   int r01,r02,r03,r04;
   int i;
   const int t[]={0,2,8,10};
   for(i=0;i<4;i++){
      d+=t[i];
      r01 = d[0];
      r02 = d[1];
      r03 = d[4];
      r04 = d[5];

      d[0] = (+ r01 + r02 + r03 + r04) ;
      d[1] = (- r01 + r02 - r03 + r04)<<1 ;
      d[4] = (- r01 - r02 + r03 + r04)<<1 ;
      d[5] = (+ r01 - r02 - r03 + r04)<<1 ;

      d-=t[i];
   }

   r01 = d[0];
   r02 = d[2];
   r03 = d[8];
   r04 = d[10];

   d[0] = (+ r01 + r02 + r03 + r04);
   d[2] = (- r01 + r02 - r03 + r04);
   d[8] = (- r01 - r02 + r03 + r04);
   d[10] = (+ r01 - r02 - r03 + r04);

   //swap
#define SWAP(_A,_B) i=_A;_A=_B;_B=i;

   SWAP(d[1],d[2]);
   SWAP(d[4],d[8]);
   SWAP(d[5],d[10]);
   //for(i=0;i<16;i++)d[i]<<=1;
}
void idct4x4dc_s( int *d){
   int r01,r02,r03,r04;
   int i;
   const int t[]={0,2,8,10};
  // for(i=0;i<16;i++)d[i]>>=2;
   
   
   SWAP(d[1],d[2]);
   SWAP(d[4],d[8]);
   SWAP(d[5],d[10]);

   r01 = d[0];
   r02 = d[2];
   r03 = d[8];
   r04 = d[10];

   d[0] = r01 - r02 - r03 + r04;
   d[2] = r01 + r02 - r03 - r04;
   d[8] = r01 - r02 + r03 - r04;
   d[10] = r01 + r02 + r03 + r04;
   

  for(i=0;i<4;i++){
      d+=t[i];
      
      r01 = d[0];;
      r02 = d[1]<<1;
      r03 = d[4]<<1;
      r04 = d[5]<<1;

      d[0] = (r01 - r02 - r03 + r04);//>>1;
      d[1] = (r01 + r02 - r03 - r04);//>>1;
      d[4] = (r01 - r02 + r03 - r04);//>>1;
      d[5] = (r01 + r02 + r03 + r04);//>>1;
      
      /*
      r01 = d[0]+4;
      r02 = d[1]<<2;
      r03 = d[4]<<2;
      r04 = d[5]<<2;

      d[0] = (r01 - r02 - r03 + r04)>>3;
      d[1] = (r01 + r02 - r03 - r04)>>3;
      d[4] = (r01 - r02 + r03 - r04)>>3;
      d[5] = (r01 + r02 + r03 + r04)>>3;
      */
      d-=t[i];
   }
  
}

#else

void fdct4x4dc_s( int *d)
{
    int tmp[4][4];
//    int s01, s23;
  //  int d01, d23;
    int i;

    for( i = 0; i < 4; i++ )
    {
        const int s01 = d[0] + d[1];
        const int d01 = d[0] - d[1];
        const int s23 = d[2] + d[3];
        const int d23 = d[2] - d[3];

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
        d+=4;
    }
    d-=16;

    for( i = 0; i < 4; i++ )
    {
        const int s01 = tmp[i][0] + tmp[i][1];
        const int d01 = tmp[i][0] - tmp[i][1];
        const int s23 = tmp[i][2] + tmp[i][3];
        const int d23 = tmp[i][2] - tmp[i][3];

        d[0] = ( s01 + s23 );
        d[4] = ( s01 - s23 );
        d[8] = ( d01 - d23 );
        d[12] = ( d01 + d23 );
        d++;
    }
}
#if 1
void idct4x4dc_s( int *d)
{
    int tmp[4][4];
    int s01, s23;
    int d01, d23;
    int i;

  //  d[0]+=4;//rounding at end
#if 0
    for( i = 0; i < 4; i++ )
    {
        s01 = d[0] + d[4];
        d01 = d[0] - d[4];
        s23 = d[8] + d[12];
        d23 = d[8] - d[12];

        d++;
        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
    }
    d-=4;

    for( i = 0; i < 4; i++ )
    {
        s01 = tmp[i][0] + tmp[i][1];
        d01 = tmp[i][0] - tmp[i][1];
        s23 = tmp[i][2] + tmp[i][3];
        d23 = tmp[i][2] - tmp[i][3];

        d[0] = (s01 + s23);//>>3;
        d[1] = (s01 - s23);//>>3;
        d[2] = (d01 - d23);//>>3;
        d[3] = (d01 + d23);//>>3;
        d+=4;
    }
#else
    for( i = 0; i < 4; i++ )
    {
        s01 = d[0] + d[4];
        d01 = d[0] - d[4];
        s23 = d[8] + d[12];
        d23 = d[8] - d[12];

        d++;
        tmp[i][0] = s01 + s23;
        tmp[i][1] = s01 - s23;
        tmp[i][2] = d01 - d23;
        tmp[i][3] = d01 + d23;
        /*
#define STAB(_A) if(_A>110*8)_A=110*8;else if(_A<-110*8)_A=-110*8;
        STAB(tmp[i][0]);
        STAB(tmp[i][1]);
        STAB(tmp[i][2]);
        STAB(tmp[i][3]);
*/
    }
    d-=4;

    for( i = 0; i < 4; i++ )
    {
        s01 = tmp[0][i] + tmp[1][i];
        d01 = tmp[0][i] - tmp[1][i];
        s23 = tmp[2][i] + tmp[3][i];
        d23 = tmp[2][i] - tmp[3][i];

        d[0] = (s01 + s23);//>>3;
        d[1] = (s01 - s23);//>>3;
        d[2] = (d01 - d23);//>>3;
        d[3] = (d01 + d23);//>>3;
/*
        STAB(d[0]);
        STAB(d[1]);
        STAB(d[2]);
        STAB(d[3]);
        */
        d+=4;
    }
#endif
}
#else
void idct4x4dc_s(int *d)
{
    int i;
    int a1, b1, c1, d1;
    int a2, b2, c2, d2;
    int output[16];
    int *ip = d;
    int *op = &output[0];

    for (i = 0; i < 4; i++)
    {
        a1 = ip[0] + ip[12];
        b1 = ip[4] + ip[8];
        c1 = ip[4] - ip[8];
        d1 = ip[0] - ip[12];

        op[0] = a1 + b1;
        op[4] = c1 + d1;
        op[8] = a1 - b1;
        op[12] = d1 - c1;
        ip++;
        op++;
    }

    ip = &output[0];
    op = &d[0];

    for (i = 0; i < 4; i++)
    {
        a1 = ip[0] + ip[3];
        b1 = ip[1] + ip[2];
        c1 = ip[1] - ip[2];
        d1 = ip[0] - ip[3];

        a2 = a1 + b1;
        b2 = c1 + d1;
        c2 = a1 - b1;
        d2 = d1 - c1;

        op[0] = a2;//(a2 + 3) >> 3;
        op[1] = b2;//(b2 + 3) >> 3;
        op[2] = c2;//(c2 + 3) >> 3;
        op[3] = d2;//(d2 + 3) >> 3;

        ip += 4;
        op += 4;
    }
}
#endif

void idct4x4dc_s2( int *d)
{
    int tmp[4][4];
    int s01, s23;
    int d01, d23;
    int i;

    

    for( i = 0; i < 4; i++ )
    {
        s01 = d[0] + d[4];
        d01 = d[0] - d[4];
        s23 = d[8] + d[12];
        d23 = d[8] - d[12];

        d++;
        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
    }
    d-=4;

    for( i = 0; i < 4; i++ )
    {
        s01 = tmp[i][0] + tmp[i][1];
        d01 = tmp[i][0] - tmp[i][1];
        s23 = tmp[i][2] + tmp[i][3];
        d23 = tmp[i][2] - tmp[i][3];

        d[0] = (s01 + s23);
        d[1] = (s01 - s23);
        d[2] = (d01 - d23);
        d[3] = (d01 + d23);
        d+=4;
    }
}

#endif
 static void dct4x4dc( DCT_TYPE d[4][4] )
{
    int tmp[4][4];
//    int s01, s23;
  //  int d01, d23;
    int i;
#define DCT_U
#ifdef DCT_U

    for( i = 0; i < 4; i++ )
    {
        const int s01 = d[i][0] + d[i][1];
        const int d01 = d[i][0] - d[i][1];
        const int s23 = d[i][2] + d[i][3];
        const int d23 = d[i][2] - d[i][3];

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
    }

    for( i = 0; i < 4; i++ )
    {
        const int s01 = tmp[i][0] + tmp[i][1];
        const int d01 = tmp[i][0] - tmp[i][1];
        const int s23 = tmp[i][2] + tmp[i][3];
        const int d23 = tmp[i][2] - tmp[i][3];

        d[0][i] = ( s01 + s23 + 1 ) >> 1;
        d[1][i] = ( s01 - s23 + 1 ) >> 1;
        d[2][i] = ( d01 - d23 + 1 ) >> 1;
        d[3][i] = ( d01 + d23 + 1 ) >> 1;
    }
#else    
    for( i = 0; i < 4; i++ )
    {
        const int s03 = d[i][0] + d[i][3];
        const int s12 = d[i][1] + d[i][2];
        const int d03 = d[i][0] - d[i][3];
        const int d12 = d[i][1] - d[i][2];

        tmp[0][i] =   s03 +   s12;
        tmp[1][i] = 2*d03 +   d12;
        tmp[2][i] =   s03 -   s12;
        tmp[3][i] =   d03 - 2*d12;
    }

    for( i = 0; i < 4; i++ )
    {
        const int s03 = tmp[i][0] + tmp[i][3];
        const int s12 = tmp[i][1] + tmp[i][2];
        const int d03 = tmp[i][0] - tmp[i][3];
        const int d12 = tmp[i][1] - tmp[i][2];

        d[i][0] = (  s03 +   s12+1)>>1;
        d[i][1] = (2*d03 +   d12+1)>>1;
        d[i][2] = (  s03 -   s12+1)>>1;
        d[i][3] = (  d03 - 2*d12+1)>>1;
    }
#endif
#define T_FDIV4(_V) (_V<0?-((2-_V)>>2):(_V>0?((2+_V)>>2):0))
#define T_FDIV8(_V) (_V<0?-((4-_V)>>3):(_V>0?((4+_V)>>3):0))
    //d[0][0]++;d[0][0]>>=1;
    //d[0][0]=T_FDIV4(d[0][0]);
}


void t_fdct_2x2_8_s_d(unsigned char *src,unsigned char *src2, int stride, DCT_TYPE *d ){
   int i;
   for(i=0;i<4;i++,src+=stride*2,src2+=stride*2){
      dct2x2dc_s_d(src,src2,stride,d);
      dct2x2dc_s_d(src+6,src2+6,stride,d+4);
      dct2x2dc_s_d(src+12,src2+12,stride,d+8);
      dct2x2dc_s_d(src+18,src2+18,stride,d+12);
      d+=16;
   }
}



void t_idct_2x2_8_s_d(DCT_TYPE *d, unsigned char *dst, int stride, int iX ){
   int i;
   iX>>=6;
   int v=1<<16;
   for(i=0;i<4;i++,dst+=stride*2){
      if(iX&v)idct2x2dc_s_d(d,dst,stride);v>>=1;
      if(iX&v)idct2x2dc_s_d(d+4,dst+6,stride);v>>=1;
      if(iX&v)idct2x2dc_s_d(d+8,dst+12,stride);v>>=1;
      if(iX&v)idct2x2dc_s_d(d+12,dst+18,stride);v>>=1;
      d+=16;
   }
}
void t_fdct_2x2_8_s_p(unsigned char *src, int stride, DCT_TYPE *d ){
   int i;
   for(i=0;i<4;i++,src+=stride*2){
      dct2x2dc_s_p(src,stride,d);
      dct2x2dc_s_p(src+6,stride,d+4);
      dct2x2dc_s_p(src+12,stride,d+8);
      dct2x2dc_s_p(src+18,stride,d+12);
      d+=16;
   }
   DCT_TYPE tmp[4][4];
   d-=64;
   for(i=0;i<16;i++)tmp[0][i]=d[i<<2];
   dct4x4dc(tmp);
   for(i=0;i<16;i++)d[i<<2]=tmp[0][i];
}
void test_53w(DCT_TYPE *d){
#if 0
   int L[16];
   int H[16];
   int LL[8];
   int HL[8];
   int LH[8];
   int HH[8];
   int bytes=4;
   int i,j;
   int w=2,h=2,height=4,width=4;
    /* horizontal transform */
    for (i = 0; i < height; i++) {
        for (j = 0; j < width; j += 2) {
            if (j == 0) {
                *(H + j/2 + i*w) = *(data + j+1 + i*bytes) - (*(data + j + i*bytes) + *(data + j+2 + i*bytes) - 1)/2 + 128;
                *(L + j/2 + i*w) = *(data + j + i*bytes) + (*(H + j/2 + i*w) - 127)/2;
            } else if (j == width - 2) {
                *(H + j/2 + i*w) = *(data + j+1 + i*bytes) - (*(data + j + i*bytes)*2 - 1)/2 + 128;
                *(L + j/2 + i*w) = *(data + j + i*bytes) + (*(H + j/2-1 + i*w) + *(H + j/2 + i*w) - 254)/4;
            } else {
                *(H + j/2 + i*w) = *(data + j+1 + i*bytes) - (*(data + j + i*bytes) + *(data + j+2 + i*bytes) - 1)/2 + 128;
                *(L + j/2 + i*w) = *(data + j + i*bytes) + (*(H + j/2-1 + i*w) + *(H + j/2 + i*w) - 254)/4;
            }
        }
    }

    /* vertical transform */
    for (i = 0; i < w; i++) {
        for (j = 0; j < height; j += 2) {
            if (j == 0) {
                *(HH + j*w/2 + i) = *(H + (j+1)*w + i) - (*(H + j*w + i) + *(H + (j+2)*w + i) - 1)/2 + 128;
                *(LH + j*w/2 + i) = *(H + j*w + i) + (*(HH + j*w/2 + i) - 127)/2;

                *(HL + j*w/2 + i) = *(L + (j+1)*w + i) - (*(L + j*w + i) + *(L + (j+2)*w + i) - 1)/2 + 128;
                *(LL + j*w/2 + i) = *(L + j*w + i) + (*(HL + j*w/2 + i) - 127)/2;
            } else if (j == height - 2) {
                *(HH + j*w/2 + i) = *(H + (j+1)*w + i) - (*(H + j*w + i)*2 - 1)/2 + 128;
                *(LH + j*w/2 + i) = *(H + j*w + i) + (*(HH + j*w/2-w + i) + *(HH + j*w/2 + i) - 254)/4;

                *(HL + j*w/2 + i) = *(L + (j+1)*w + i) - (*(L + j*w + i)*2 - 1)/2 + 128;
                *(LL + j*w/2 + i) = *(L + j*w + i) + (*(HL + j*w/2-w + i) + *(HL + j*w/2 + i) - 254)/4;
            } else {
                *(HH + j*w/2 + i) = *(H + (j+1)*w + i) - (*(H + j*w + i) + *(H + (j+2)*w + i) - 1)/2 + 128;
                *(LH + j*w/2 + i) = *(H + j*w + i) + (*(HH + j*w/2-w + i) + *(HH + j*w/2 + i) - 254)/4;

                *(HL + j*w/2 + i) = *(L + (j+1)*w + i) - (*(L + j*w + i) + *(L + (j+2)*w + i) - 1)/2 + 128;
                *(LL + j*w/2 + i) = *(L + j*w + i) + (*(HL + j*w/2-w + i) + *(HL + j*w/2 + i) - 254)/4;
            }
        }
    }

    /* pack back into original data array */
    for (i = 0; i < h; i++) {
        for (j = 0; j < w; j++) {
            *(data + j + i*bytes) = *(LL + j + i*w);
            *(data + j + w + i*bytes) = *(LH + j + i*w)-128;
            *(data + j + i*bytes + h*bytes) = *(HL + j + i*w)-128;
            *(data + j + w + i*bytes + h*bytes) = *(HH + j + i*w)-128;
        }
    }
#endif
/*
        x01 = (+ r01 + r02 + r03 + r04) ;
        x02 = (- r01 + r02 - r03 + r04) ;
        x03 = (- r01 - r02 + r03 + r04) ;
        x04 = (+ r01 - r02 - r03 + r04) ;

*/

//                *(H + j/2 + i*w) = *(data + j+1 + i*bytes) - (*(data + j + i*bytes) + *(data + j+2 + i*bytes) - 1)/2 + 128;
  //              *(L + j/2 + i*w) = *(data + j + i*bytes) + (*(H + j/2-1 + i*w) + *(H + j/2 + i*w) - 254)/4;

    int tmp[4][4];
    int i;
    for( i = 0; i < 4; i++ )
    {
        const int s01 = d[0] + d[1];
        const int d01 = d[0] - d[1];
        const int s23 = d[2] + d[3];
        const int d23 = d[2] - d[3];
//#define _SSQ(_A) _A=((_A)*184)>>8
        //_SSQ(s01);_SSQ(d01);_SSQ(s23);_SSQ(d23);

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;

    //    _SSQ(tmp[0][i]);_SSQ(tmp[1][i]);_SSQ(tmp[2][i]);_SSQ(tmp[3][i]);
        d+=4;
    }
    d-=16;
    for( i = 0; i < 4; i++ )
    {
        const int s01 = tmp[i][0] + tmp[i][1];
        const int d01 = tmp[i][0] - tmp[i][1];
        const int s23 = tmp[i][2] + tmp[i][3];
        const int d23 = tmp[i][2] - tmp[i][3];

      //  _SSQ(s01);_SSQ(d01);_SSQ(s23);_SSQ(d23);

        d[0] = ( s01 + s23+1)>>1;
        d[4] = ( s01 - s23+1)>>1;
        d[8] = ( d01 - d23+1)>>1;
        d[12] = ( d01 + d23+1)>>1;
        d++;
    }
    
//    for(i=0;i<16;i++)d[i]=tmp[0][i];

    
    /*
   for(i=0;i<4;i++){
      for(j=0;j<4;j+=2){
         H[j>>1][i]=d[1+j]-((d[0+j]+d[2+j])>>1);
         L[j>>1][i]=d[0+j]+((d[(i>>1)-1]+d[1])>>2);

       // *(H + j/2 + i*w) = *(d + j+1 + i*stride) - ((*(d + j + i*stride) + *(d + j+2 + i*stride) - 1)>>1) + 128;
       // *(L + j/2 + i*w) = *(d + j + i*stride) + ((*(H + j/2-1 + i*w) + *(H + j/2 + i*w) - 254)>>2);
      }
      d+=4;
   }
   */
   /*
    int tmp[4][4];
//    int s01, s23;
  //  int d01, d23;
    int i;

    for( i = 0; i < 4; i++ )
    {
        const int s01 = d[i][0] + d[i][1];
        const int d01 = d[i][0] - d[i][1];
        const int s23 = d[i][2] + d[i][3];
        const int d23 = d[i][2] - d[i][3];

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
    }

    for( i = 0; i < 4; i++ )
    {
        const int s01 = tmp[i][0] + tmp[i][1];
        const int d01 = tmp[i][0] - tmp[i][1];
        const int s23 = tmp[i][2] + tmp[i][3];
        const int d23 = tmp[i][2] - tmp[i][3];

        d[0][i] = ( s01 + s23 + 1 ) >> 1;
        d[1][i] = ( s01 - s23 + 1 ) >> 1;
        d[2][i] = ( d01 - d23 + 1 ) >> 1;
        d[3][i] = ( d01 + d23 + 1 ) >> 1;
    }

*/
}



static void dct4x4dc_s_p(unsigned char *src, int stride, DCT_TYPE *d )
{
    int tmp[4][4];
//    int s01, s23;
  //  int d01, d23;
    int i;
    for( i = 0; i < 4; i++ )
    {
        const int s01 = src[0] + src[3];// -256;
        const int d01 = src[0] - src[3];
        const int s23 = src[6] + src[9];// -256;
        const int d23 = src[6] - src[9];

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
        src+=stride;
    }

    for( i = 0; i < 4; i++ )
    {
        const int s01 = tmp[i][0] + tmp[i][1];
        const int d01 = tmp[i][0] - tmp[i][1];
        const int s23 = tmp[i][2] + tmp[i][3];
        const int d23 = tmp[i][2] - tmp[i][3];

        d[0] = ( s01 + s23);// + 1 ) >> 1;
        d[4] = ( s01 - s23);// + 1 ) >> 1;
        d[8] = ( d01 - d23);// + 1 ) >> 1;
        d[12] = ( d01 + d23);// + 1 ) >> 1;
        d++;
    }
    //
    d[-4]<<=1;
    d[-4]-=128*32;
}

static void dct4x4dc_s_p_d(unsigned char *src,unsigned char *src2, int stride, DCT_TYPE *d )
{
    int tmp[4][4];
//    int s01, s23;
  //  int d01, d23;
    int i;
    for( i = 0; i < 4; i++ )
    {
        const int s01 = src[0] + src[3] -src2[0]-src2[3];// -256;
        const int d01 = src[0] - src[3] -src2[0]+src2[3];
        const int s23 = src[6] + src[9] -src2[6]-src2[9];// -256;
        const int d23 = src[6] - src[9] -src2[6]+src2[9];;

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
        
        src+=stride;
        src2+=stride;
    }

    for( i = 0; i < 4; i++ )
    {
        const int s01 = tmp[i][0] + tmp[i][1];
        const int d01 = tmp[i][0] - tmp[i][1];
        const int s23 = tmp[i][2] + tmp[i][3];
        const int d23 = tmp[i][2] - tmp[i][3];

        d[0] = ( s01 + s23 + 1 ) >> 1;
        d[4] = ( s01 - s23 + 1 ) >> 1;
        d[8] = ( d01 - d23 + 1 ) >> 1;
        d[12] = ( d01 + d23 + 1 ) >> 1;
        d++;
    }
}

int sadHadaX(unsigned char *src,unsigned char *src2, int stride, int *s)
{
    int tmp[4][4];
    int i;
    int iSad=0;
    const int zz4[]={
      0, 4, 1, 5, 2, 8,12, 9,
      6, 3, 7,10,13,14,11,15,
    };
    int iBits=0;
    int r[16];
    for( i = 0; i < 4; i++ )
    {
        const int s03 = src[0] + src[3] -(src2[0] + src2[3]);
        const int s12 = src[1] + src[2] -(src2[1] + src2[2]);
        const int d03 = src[0] - src[3]- (src2[0] - src2[3]);
        const int d12 = src[1] - src[2]- (src2[1] - src2[2]);

        tmp[0][i] =   s03 +   s12;
        tmp[1][i] = d03 +   d12;
        tmp[2][i] =   s03 -   s12;
        tmp[3][i] =   d03 - d12;
    }

    for( i = 0; i < 4; i++ )
    {
        const int s03 = tmp[i][0] + tmp[i][3];
        const int s12 = tmp[i][1] + tmp[i][2];
        const int d03 = tmp[i][0] - tmp[i][3];
        const int d12 = tmp[i][1] - tmp[i][2];
/*
        d[i][0] = (  s03 +   s12+1)>>1;
        d[i][1] = (2*d03 +   d12+1)>>1;
        d[i][2] = (  s03 -   s12+1)>>1;
        d[i][3] = (  d03 - 2*d12+1)>>1;
        */
        r[0+i]=( s03 + s12+2)>>2;
        r[4+i]=( s03 + s12+2)>>2;
        r[8+i]=( d03 - d12+2)>>2;
        r[12+i]=( d03 - d12+4)>>3;
        

/*
        iSad+=testVlcLens(0,( s01 + s23+2)>>2);
        iSad+=testVlcLens(0,( s01 - s23+2)>>2);
        iSad+=testVlcLens(0,( d01 - d23+2)>>2);
        iSad+=testVlcLens(0,( d01 + d23+4)>>3);
        */
        /*
        iSad+=s[( s01 + s23+2)>>2];//ja meklee >>2,>>1,>>1,>>2
        iSad+=s[ (s01 - s23+2)>>2];
        iSad+=s[ (d01 - d23+2)>>2];
        iSad+=s[ (d01 + d23+4)>>3];
        */
    }
        //int testVlcLens(int iReset, int val);
        r[0]<<=1;
    for(i=0;i<16;i++){
       int val=r[zz4[i]];
       if(val){
          iBits+=((val*val)>>1);//testVlcLens(0, val);
          iSad=iBits;
       }
       else iBits++;
       
    }
    return iSad;//(iSad*2)>>2;
}

static inline int sadHada2(unsigned char *src,unsigned char *src2, int stride, int *s){
   int tmp[2][2];
   tmp[0][0] = src[0] + src[1]-(src2[0] + src2[1]);
   tmp[1][0] = src[0] - src[1]-(src2[0] + src2[1]);;
   src+=stride;
   tmp[0][1] = src[0] + src[1]-(src2[0] + src2[1]);
   tmp[1][1] = src[0] - src[1]-(src2[0] - src2[1]);

   int iSad=1;
    iSad += s[(tmp[0][0] + tmp[0][1]+1)>>1];
    iSad += s[(tmp[1][0] + tmp[1][1]+2)>>2];
    iSad += s[(tmp[0][0] - tmp[0][1]+2)>>2];
    iSad += s[(tmp[1][0] - tmp[1][1]+8)>>4];
    return iSad;

}
int __sadHada(unsigned char *src,unsigned char *src2, int stride, int *s)
{
    int iSad=0;
#if 0
    iSad+=sadHada2(src,src2,stride,s);
    iSad+=sadHada2(src+2,src2+2,stride,s);
    iSad+=sadHada2(src+stride*2,src2+stride*2,stride,s);
    iSad+=sadHada2(src+2,src2+2+stride*2,stride,s);
    return iSad;
#endif


    int i;
   int tmp[4][4];
   for( i = 0; i < 4; i++ )
   {
      /*
      const int s03 = d[i][0] + d[i][3];
      const int s12 = d[i][1] + d[i][2];
      const int d03 = d[i][0] - d[i][3];
      const int d12 = d[i][1] - d[i][2];
      */
      /*
      int s03=src[0]+src[9];
      int s12=src[3]+src[6];
      int d03=src[0]-src[9];
      int d12=src[3]-src[6];
      if(iDif){

         s03-=(src2[0]+src2[9]);
         s12-=(src2[3]+src2[6]);
         d03-=(src2[0]-src2[9]);
         d12-=(src2[3]-src2[6]);
         src2+=stride;
      }
      */
      int s03=src[0]+src[9];//9
      int s12=src[3]+src[6];//6
      int d03=src[0]-src[9];
      int d12=src[3]-src[6];
      {

         s03-=(src2[0]+src2[9]);
         s12-=(src2[3]+src2[6]);
         d03-=(src2[0]-src2[9]);
         d12-=(src2[3]-src2[6]);
         src2+=stride;
      }

      src+=stride;





      tmp[0][i] =   s03 +   s12;
      tmp[1][i] = ( d03 <<1)+   d12;
      tmp[2][i] =   s03 -   s12;
      tmp[3][i] =   d03 - ( d12 <<1);
   }

   for( i = 0; i < 4; i++ )
   {
      const int s03 = tmp[i][0] + tmp[i][3];//3
      const int s12 = tmp[i][1] + tmp[i][2];//2
      const int d03 = tmp[i][0] - tmp[i][3];
      const int d12 = tmp[i][1] - tmp[i][2];
/*
      d[0] = (  s03 +   s12);//+1)>>1;
      d[1] = (  d03  +  d12);//+1)>>1;
      d[2] = (  s03 -   s12);//+1)>>1;
      d[3] = (  d03 -   d12 ));//+1)>>1;
      d+=4;
      */

        iSad+=s[ s03 + s12];
        iSad+=s[ (d03<<1) + d12];
        iSad+=s[ d03 - (d12<<1)];
        iSad+=s[ s03 - s12];

   }
   return ((iSad-64)>>2)+16;
   //return ((iSad-128)>>3)+64;



    for( i = 0; i < 4; i++ )
    {
        const int s01 = src[0] + src[1] -src2[0]-src2[1];// -256;
        const int d01 = src[0] - src[1] -src2[0]+src2[1];
        const int s23 = src[2] + src[3] -src2[2]-src2[3];// -256;
        const int d23 = src[2] - src[3] -src2[2]+src2[3];;

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
        
        src+=stride;
        src2+=stride;
    }
    const int zz4[]={
      0, 4, 1, 5, 2, 8,12, 9,
      6, 3, 7,10,13,14,11,15,
    };
    int iBits=0;
    int r[16];
    const int tQ[]={
       1,2,1,2,
       2,2,2,2,
       1,2,1,2,
       2,2,2,2,
    };
    const int tQA[]={
       1,2,1,2,
       2,2,2,2,
       1,2,1,2,
       2,2,2,2,
    };
    int z=0;

    for( i = 0; i < 4; i++ ,z++)
    {
        const int s01 = tmp[i][0] + tmp[i][1];
        const int d01 = tmp[i][0] - tmp[i][1];
        const int s23 = tmp[i][2] + tmp[i][3];
        const int d23 = tmp[i][2] - tmp[i][3];
        /*
        r[0+i]=( s01 + s23+8)>>4;
        r[4+i]=( s01 - s23+8)>>4;
        r[8+i]=( d01 - d23+8)>>4;
        r[12+i]=( d01 + d23+8)>>4;
        */

/*
        iSad+=testVlcLens(0,( s01 + s23+2)>>2);
        iSad+=testVlcLens(0,( s01 - s23+2)>>2);
        iSad+=testVlcLens(0,( d01 - d23+2)>>2);
        iSad+=testVlcLens(0,( d01 + d23+4)>>3);
        */
/*        
        iSad+=s[( s01 + s23+tQA[z])>>tQ[z]];
        iSad+=s[ (s01 - s23+tQA[z])>>tQ[z]];
        iSad+=s[ (d01 - d23+tQA[z])>>tQ[z]];
        iSad+=s[ (d01 + d23+tQA[z])>>tQ[z]];
  */
        iSad+=s[ s01 + s23];
        iSad+=s[ s01 - s23];
        iSad+=s[ d01 - d23];
        iSad+=s[ d01 + d23];
    }
    return (iSad*5)>>4;
    /*
        int testVlcLens(int iReset, int val);
       // r[0]<<=1;
       // r[1]<<=1;
        //r[4]<<=1;
        testVlcLens(1,0);
        int sum=0;
    for(i=0;i<16;i++){
       int val=r[zz4[i]];

       if(val){
          if(val<0)val=-val;
          sum+=val;
          iSad=testVlcLens(0, val);
       }
       else iBits++;
       
    }
    if(sum<4)return 0;
    return iSad+iBits;//(iSad*2)>>2;
    */
}


static inline  int xabs(int a){return a>=0?a:-a;}

int sadHada4(unsigned char *src,unsigned char *src2, int stride1, int stride2, const int qadd, const int qsh )
{
    int iSad=0;



    int i;
   int tmp[4][4];
   for( i = 0; i < 4; i++ )
   {
      int s03=src[0]+src[9];//9
      int s12=src[3]+src[6];//6
      int d03=src[0]-src[9];
      int d12=src[3]-src[6];
      {

         s03-=(src2[0]+src2[9]);
         s12-=(src2[3]+src2[6]);
         d03-=(src2[0]-src2[9]);
         d12-=(src2[3]-src2[6]);
         src2+=stride2;
      }
      src+=stride1;





      tmp[0][i] =   s03 +   s12;
      tmp[1][i] = ( d03 )+   d12;
      tmp[2][i] =   s03 -   s12;
      tmp[3][i] =   d03 - ( d12 );
   }
   
   const static int pow_mult[]={
      //1,2,3,4,
      //2,3,4,5,
      3,4,5,6,
      4,5,6,7,
      5,6,7,8,
      6,7,8,9,
   };
   int *m=(int*)&pow_mult[0];

   int x;
   i=1;
   {
      const int s03 = tmp[i][0] + tmp[i][3];//3
      const int s12 = tmp[i][1] + tmp[i][2];//2
      const int d03 = tmp[i][0] - tmp[i][3];
      const int d12 = tmp[i][1] - tmp[i][2];

      iSad+=xabs((s03 + s12+qadd)>>qsh )*8;// *m[0];
      iSad+=xabs( ((d03) + d12+qadd)>>qsh);// *m[1];
      iSad+=xabs( (d03 - (d12)+qadd)>>qsh);// *m[2];
      iSad+=xabs( (s03 - s12+qadd)>>qsh);// *m[3];
      m+=4;

   }
   for( i = 1; i < 4; i++ )
   {
      const int s03 = tmp[i][0] + tmp[i][3];//3
      const int s12 = tmp[i][1] + tmp[i][2];//2
      const int d03 = tmp[i][0] - tmp[i][3];
      const int d12 = tmp[i][1] - tmp[i][2];

      iSad+=xabs((s03 + s12+qadd)>>qsh );// *m[0];
      iSad+=xabs( ((d03) + d12+qadd)>>qsh);// *m[1];
      iSad+=xabs( (d03 - (d12)+qadd)>>qsh);// *m[2];
      iSad+=xabs( (s03 - s12+qadd)>>qsh);// *m[3];
      m+=4;

   }
  
   return iSad>>4;// >>3;//((iSad-64)>>2)+16;
}
int sadHada4_t(unsigned char *src,unsigned char *src2, int stride)
{
   return sadHada4(src,src2,stride,stride,0,0);;// >>3;//((iSad-64)>>2)+16;
}
static inline int sadHada8(unsigned char *src,unsigned char *src2, int stride1, int stride2, int qadd, int qsh ){
   int s=sadHada4(src,src2,stride1,stride2,qadd,qsh);
   s+=sadHada4(src+4,src2+4,stride1,stride2,qadd,qsh);
   s+=sadHada4(src+4*stride1,src2+4*stride2,stride1,stride2,qadd,qsh);
   s+=sadHada4(src+4*stride1+4,src2+4*stride2+4,stride1,stride2,qadd,qsh);
   return s;
}

int sadHada16(unsigned char *src,unsigned char *src2, int stride1, int stride2, int qadd, int qsh, int *costs ){
   costs[0]=sadHada8(src,src2,stride1,stride2,qadd,qsh);
   costs[1]=sadHada8(src+8,src2+8,stride1,stride2,qadd,qsh);
   costs[2]=sadHada8(src+8*stride1,src2+8*stride2,stride1,stride2,qadd,qsh);
   costs[3]=sadHada8(src+8*stride1+8,src2+8*stride2+8,stride1,stride2,qadd,qsh);
   return costs[0]+costs[1]+costs[2]+costs[3];
}



static void idct4x4dc( DCT_TYPE d[4][4] )
{
    int tmp[4][4];
    int s01, s23;
    int d01, d23;
    int i;
    //d[0][0]<<=1;
#ifdef DCT_U
    for( i = 0; i < 4; i++ )
    {
        s01 = d[0][i] + d[1][i];
        d01 = d[0][i] - d[1][i];
        s23 = d[2][i] + d[3][i];
        d23 = d[2][i] - d[3][i];

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
    }

    for( i = 0; i < 4; i++ )
    {
        s01 = tmp[i][0] + tmp[i][1];
        d01 = tmp[i][0] - tmp[i][1];
        s23 = tmp[i][2] + tmp[i][3];
        d23 = tmp[i][2] - tmp[i][3];

        d[i][0] = (s01 + s23+4)>>3;
        d[i][1] = (s01 - s23+4)>>3;
        d[i][2] = (d01 - d23+4)>>3;
        d[i][3] = (d01 + d23+4)>>3;
    }
#else    
    for( i = 0; i < 4; i++ )
    {
        const int s02 =  d[0][i]     +  d[2][i];
        const int d02 =  d[0][i]     -  d[2][i];
        const int s13 =  d[1][i]     + (d[3][i]>>1);
        const int d13 = (d[1][i]>>1) -  d[3][i];

        tmp[i][0] = s02 + s13;
        tmp[i][1] = d02 + d13;
        tmp[i][2] = d02 - d13;
        tmp[i][3] = s02 - s13;
    }

    for( i = 0; i < 4; i++ )
    {
        const int s02 =  tmp[0][i]     +  tmp[2][i];
        const int d02 =  tmp[0][i]     -  tmp[2][i];
        const int s13 =  tmp[1][i]     + (tmp[3][i]>>1);
        const int d13 = (tmp[1][i]>>1) -  tmp[3][i];

        d[0][i] = ( s02 + s13 + 4 ) >> 3;
        d[1][i] = ( d02 + d13 + 4 ) >> 3;
        d[2][i] = ( d02 - d13 + 4 ) >> 3;
        d[3][i] = ( s02 - s13 + 4 ) >> 3;
    }
#endif
}

void t_idct8(DCT_TYPE *t)
{
   DCT_TYPE c1[4][4];
   DCT_TYPE c2[4][4];
   DCT_TYPE c3[4][4];
   DCT_TYPE c4[4][4];
   int z=0,i,j;

   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         c1[i][j]=t[z+0];
         c2[i][j]=t[z+16];
         c3[i][j]=t[z+32];
         c4[i][j]=t[z+48];
         z++;
      }
   }

   //c1[0][0]<<=2;
   //c2[0][0]<<=2;
   //c3[0][0]<<=2;
   //c4[0][0]<<=2;
   idct4x4dc(c1);
   idct4x4dc(c2);
   idct4x4dc(c3);
   idct4x4dc(c4);


   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         t[i*8+j]=c1[i][j];
         t[i*8+j+4]=c2[i][j];
         t[(i+4)*8+j]=c3[i][j];
         t[(i+4)*8+(j+4)]=c4[i][j];
      }
   }
}

static void idct4x4dc_s_p_put( DCT_TYPE *d, unsigned char *dst, int stride )
{
    int tmp[4][4];
    int s01, s23;
    int d01, d23;
    int i;

    d[0]++;
    d[0]>>=1;
    for( i = 0; i < 4; i++ )
    {
        s01 = d[0] + d[4];
        d01 = d[0] - d[4];
        s23 = d[8] + d[12];
        d23 = d[8] - d[12];

        d++;

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
    }

    for( i = 0; i < 4; i++ )
    {
        s01 = tmp[i][0] + tmp[i][1];
        d01 = tmp[i][0] - tmp[i][1];
        s23 = tmp[i][2] + tmp[i][3];
        d23 = tmp[i][2] - tmp[i][3];
#define _SHAD_1 8
#define _SH_1 4

        dst[0*3] = xiclp_0_256[((s01 + s23+_SHAD_1)>>_SH_1)];
        dst[1*3] = xiclp_0_256[((s01 - s23+_SHAD_1)>>_SH_1)];
        dst[2*3] = xiclp_0_256[((d01 - d23+_SHAD_1)>>_SH_1)];
        dst[3*3] = xiclp_0_256[((d01 + d23+_SHAD_1)>>_SH_1)];
        dst+=stride;
    }
}



static void idct4x4dc_s_p( DCT_TYPE *d, unsigned char *dst, int stride )
{
    int tmp[4][4];
    int s01, s23;
    int d01, d23;
    int i;
    //0
    for( i = 0; i < 4; i++ )
    {
        s01 = d[0] + d[4];
        d01 = d[0] - d[4];
        s23 = d[8] + d[12];
        d23 = d[8] - d[12];

        tmp[0][i] = s01 + s23;
        tmp[1][i] = s01 - s23;
        tmp[2][i] = d01 - d23;
        tmp[3][i] = d01 + d23;
        d++;
    }

    for( i = 0; i < 4; i++ )
    {
        s01 = tmp[i][0] + tmp[i][1];
        d01 = tmp[i][0] - tmp[i][1];
        s23 = tmp[i][2] + tmp[i][3];
        d23 = tmp[i][2] - tmp[i][3];

        dst[0*3] = xiclp_0_256x[((s01 + s23+4)>>3)+dst[0*3]];
        dst[1*3] = xiclp_0_256x[((s01 - s23+4)>>3)+dst[1*3]];
        dst[2*3] = xiclp_0_256x[((d01 - d23+4)>>3)+dst[2*3]];
        dst[3*3] = xiclp_0_256x[((d01 + d23+4)>>3)+dst[3*3]];
        dst+=stride;
    }
}
template<int iAdd>
static void t_ivc1_x(DCT_TYPE *p, unsigned char *dstp, int stride)
{
   int tmp[4*4];
   int i;
   register int t1,t2,t3,t4,t5,t6;
   int *dst = tmp;
   //p[0]>>=1;
   //p[0]++;p[0]>>=1;
   if(iAdd==0){p[0]+=904;p[0]++;p[0]>>=1;}

	for(i = 0; i < 4; i++){
	    t1 = 17*(p[0] + p[2]);
	    t2 = 17*(p[0] - p[2]);
	    t3 = 22 * p[1];
	    t4 = 22 * p[3];
	    t5 = 10* p[1];
	    t6 = 10* p[3];

	    dst[0] = (t1 + t3 + t6 +4)>>3;
	    dst[1] = (t2 - t4 + t5 +4)>>3;
	    dst[2] = (t2 + t4 - t5 +4)>>3;
	    dst[3] = (t1 - t3 - t6 +4)>>3;

       p+=4;
	    dst+=4;
	}
   int *src2=tmp;
	//dst = pR;
	for(i = 0; i < 4; i++){
	    t1 = 17* (src2[ 0] + src2[8]);
	    t2 = 17* (src2[ 0] - src2[8]);
	    t3 = 22 * src2[ 4];
	    t4 = 22 * src2[12];
	    t5 = 10* src2[ 4];
	    t6 = 10* src2[12];

	    dstp[ 0*3] = xiclp_0_256x[((t1 + t3 + t6 + 128) >> 8)+iAdd*dstp[ 0*3]];
	    dstp[ 1*3] = xiclp_0_256x[((t2 - t4 + t5 + 128) >> 8)+iAdd*dstp[ 1*3]];
	    dstp[ 2*3] = xiclp_0_256x[((t2 + t4 - t5 + 128) >> 8)+iAdd*dstp[ 2*3]];
	    dstp[ 3*3] = xiclp_0_256x[((t1 - t3 - t6 + 128) >> 8)+iAdd*dstp[ 3*3]];

	    src2++;
	    dstp+=stride;
       //break;
	}
}
template<int iDif>
static void t_fvc1_x(unsigned char *src,unsigned char *src2, int stride,DCT_TYPE *p){
   int i;
   int tmp[16];
   int *t=&tmp[0];
   int z=50;
   int z2=z;
#define _FR(A1,B1,C1,D1,A,B,C,D)\
A1 = (17 * A + 17 * B + 17 * C + 17 * D) ;\
B1 = (22 * A + 10 * B - 10 * C - 22 * D) ;\
C1 = (17 * A - 17 * B - 17 * C + 17 * D) ;\
D1 = (10 * A - 22 * B + 22 * C - 10 * D) ;
   int a,b,c,d;
   for(i=0;i<4;i++){
      a=src[0];
      b=src[3];
      c=src[6];
      d=src[9];
      if(iDif){
         a-=src2[0];
         b-=src2[3];
         c-=src2[6];
         d-=src2[9];
      }
      _FR(t[0],t[1],t[2],t[3],a,b,c,d);
      src+=stride;
      if(iDif)src2+=stride;
      t+=4;
   }
#undef _FR

#define _FR(A1,B1,C1,D1,A,B,C,D)\
A1 = (17 * A + 17 * B + 17 * C + 17 * D) * 128 /(289* 289);\
B1 = (22 * A + 10 * B - 10 * C - 22 * D) * 128 /(292* 292);\
C1 = (17 * A - 17 * B - 17 * C + 17 * D) * 128 /(289* 289);\
D1 = (10 * A - 22 * B + 22 * C - 10 * D) * 128 /(292* 292);

#define _FR2x(A1,B1,C1,D1,A,B,C,D)\
A1 = ((17 * A + 17 * B + 17 * C + 17 * D) * 50) >>15;\
B1 = ((22* A + 10 * B - 10 * C - 22 * D) * 49)>>15;\
C1 = ((17 * A - 17 * B - 17 * C + 17 * D) * 50) >>15;\
D1 = ((10 * A - 22 * B + 22 * C - 10 * D) * 49) >>15;
   t-=16;
   for(i=0;i<4;i++){
      _FR2x(p[0],p[1],p[2],p[3],t[0],t[4],t[8],t[12]);
      t++;
      p+=4;
   }
   if(!iDif){
      p[-16]<<=1;
      p[-16]-=904;
   }
   //p[-16]*=2;
#undef _FR2x
#undef _FR
}

template<int iDif>
static void t_f264_ax(unsigned char *src,unsigned char *src2, int stride,DCT_TYPE *d){
   int i;
   int tmp[4][4];
   for( i = 0; i < 4; i++ )
   {
      /*
      const int s03 = d[i][0] + d[i][3];
      const int s12 = d[i][1] + d[i][2];
      const int d03 = d[i][0] - d[i][3];
      const int d12 = d[i][1] - d[i][2];
      */
      /*
      int s03=src[0]+src[9];
      int s12=src[3]+src[6];
      int d03=src[0]-src[9];
      int d12=src[3]-src[6];
      if(iDif){

         s03-=(src2[0]+src2[9]);
         s12-=(src2[3]+src2[6]);
         d03-=(src2[0]-src2[9]);
         d12-=(src2[3]-src2[6]);
         src2+=stride;
      }
      */
      int s03=src[0]+src[9];//9
      int s12=src[3]+src[6];//6
      int d03=src[0]-src[9];
      int d12=src[3]-src[6];
      if(iDif){

         s03-=(src2[0]+src2[9]);
         s12-=(src2[3]+src2[6]);
         d03-=(src2[0]-src2[9]);
         d12-=(src2[3]-src2[6]);
         src2+=stride;
      }

      src+=stride;

#if 0
#define MULT2 2*
#define SH1 >>1

#define _ADH 8
#define _ADSH 4
//#define _ADH 4
//#define _ADSH 3

#else

#define MULT2
#define SH1
#define _ADH 8
#define _ADSH 4

#endif




      tmp[0][i] =   s03 +   s12;
      tmp[1][i] = (MULT2 d03 )+   d12;
      tmp[2][i] =   s03 -   s12;
      tmp[3][i] =   d03 - (MULT2 d12 );
   }

   for( i = 0; i < 4; i++ )
   {
      const int s03 = tmp[i][0] + tmp[i][3];//3
      const int s12 = tmp[i][1] + tmp[i][2];//2
      const int d03 = tmp[i][0] - tmp[i][3];
      const int d12 = tmp[i][1] - tmp[i][2];

      d[0] = (  s03 +   s12);//+1)>>1;
      d[1] = ((MULT2 d03 ) +   d12);//+1)>>1;
      d[2] = (  s03 -   s12);//+1)>>1;
      d[3] = (  d03 - (MULT2 d12 ));//+1)>>1;
      d+=4;
   }
#undef MULT2
#define MULT2
   if(!iDif){
      d[-16]*=2;
      d[-16]-=128*16*2;
   }

}
#define TR4(step)\
       int tmp0 = p[0*step] + (p[3*step]);\
       int tmp1 = p[1*step] + (p[2*step]);\
       int tmp10 = (p[0*step]) - (p[3*step]);\
       int tmp11 = (p[1*step]) - (p[2*step]);\
       p[0*step] = (tmp0 + tmp1);\
       p[2*step] = (tmp0 - tmp1);\
       p[1*step] =tmp10*3 + tmp11;\
       p[3*step] =tmp10 - tmp11*3;

#define T_USE_T_TR
template<int step,  class T>
inline static void trR4(T *p){
   
    T tmp0 = p[0*step] + (p[3*step]);
    T tmp1 = p[1*step] + (p[2*step]);

    T tmp10 = (p[0*step]) - (p[3*step]);
    T tmp11 = (p[1*step]) - (p[2*step]);

    /* Apply unsigned->signed conversion */
    p[0*step] = (tmp0 + tmp1);// << (PASS1_BITS+2));
    p[2*step] = (tmp0 - tmp1);// << (PASS1_BITS+2));

    /* Odd part */
/*
    tmp0 = (tmp10 + tmp11+1)>>1;   

    p[1*step] = tmp0 + ((tmp10*3+2)>>2);
    p[3*step] = tmp0 - ((tmp11*7+2)>>2);
*/
    //tmp0 = (tmp10 + tmp11);   
//    p[1*step] =(tmp0*2 + ((tmp10*2+2)))>>2;
//    p[3*step] =(tmp0*2 - ((tmp11*7+2)))>>2;

//--    p[1*step] =tmp10*3 + tmp11;//(tmp0*2 + ((tmp10*2+2)))>>2;
//--    p[3*step] =tmp10 - tmp11*3;
#ifdef T_USE_T_TR
//    p[1*step] =(tmp10*3 + tmp11);//(tmp0*2 + ((tmp10*2+2)))>>2;
  //  p[3*step] =(tmp10 - tmp11*3);
    p[1*step] =(tmp10*3 + tmp11);//(tmp0*2 + ((tmp10*2+2)))>>2;
    p[3*step] =(tmp10 - tmp11*3);

    //a=3y+x;
    //b=y-3x;
    //x=a-3y;x=(y-b)/3;3a-9y=y-b; 3a+b=10y
    //a-x=3y;b+3x=y;a-x=3b+9x;a-3b=10x
#else
#define T_X_TR_D 2
    p[1*step] =(tmp10*T_X_TR_D + tmp11);//(tmp0*2 + ((tmp10*2+2)))>>2;
    p[3*step] =(tmp10 - tmp11*T_X_TR_D);
#endif
    /*
    if(step==4){
       p[1*step]+=1;p[0*step]+=1;p[3*step]+=1;p[2*step]+=1;
       p[1*step]>>=1;p[0*step]>>=1;p[3*step]>>=1;p[2*step]>>=1;
    }
    */
  
}

template<int step>
static void itrR4(short *p){
    int tmp0 = (int) p[0*step];
    int tmp2 = (int) p[2*step];
    
    int tmp10 = (tmp0 + tmp2);
    int tmp12 = (tmp0 - tmp2);
    
    /* Odd part */
    /* Same rotation as in the even part of the 8x8 LL&M IDCT */

    int z2 = (int) p[1*step];
    int z3 = (int) p[3*step];

//    int z1 = (z2 + z3+1)>>1;
    //tmp0 = (z1 - ((z3*7+1)>>1)+1)>>1;
    //tmp2 = (z1 + ((z2*3+1)>>1)+1)>>1;

    //int z1 = z2 + z3;
//    tmp0 = (z1*2 - z3*7+2)>>2;
  //  tmp2 = (z1*2 + z2*3+2)>>2;
   
//    tmp0 = (z2 - z3*5+2)>>2;
  //  tmp2 = (z3*2 + z2*3+2)>>2;
    
//    tmp0 = (z2 - ((z3*5+1)>>1)+2)>>2;
    //tmp0 = (z2*3 - z3*6+4)>>3;
    //126000,930
    //115 93
    //tmp0 = (z2*13 - z3*26+16)>>5;
    //tmp2 = (z3*13 + z2*26+16)>>5;

    //tmp0 = (z2*10 - z3*31+16)>>5; //3,2
    //tmp2 = (z3*24 + z2*16+16)>>5;


    //120000 127KB 3,3
//    tmp0 = (z2*14 - z3*40+32)>>6;
  //  tmp2 = (z3*14 + z2*40+32)>>6;
    //118800 108KB 3,3 z2/2,z3/2
    //tmp0 = (z2*14 - z3*40+16)>>5;
    //tmp0 = (z2*14 + z3*40+16)>>5;

    //119000 111KB (tmp10*3 + tmp11);(tmp10 - tmp11*3+1)>>1; ,q 22
    //tmp0 = (z2*13 - z3*78+32)>>6;
    //tmp2 = (z3*26 + z2*39+32)>>6;
//20
//110800 862  (tmp10*3 + tmp11);(tmp10 - tmp11);qe=22
    //tmp0 = (z2 - z3*3+1)>>1;
    //tmp2 = (z3 + z2+1)>>1;
//117000,858 (tmp10*3 + tmp11);(tmp10 - tmp11*2);qe=11 q>>=4
    //tmp0 = ((z2 - z3*3)*9+16)>>5;
    //tmp2 = ((2*z2+z3)*9+16)>>5;
    //(tmp10*4 + tmp11);(tmp10 - tmp11*3);
    //tmp0 = ((z2-   z3*4)+4)>>3;//*10+32)>>6;
    //tmp2 = ((z2*3 +z2 )+4)>>3;//*10+32)>>6; // div 7

    //126000 893KB (tmp10*3 + tmp11);(tmp10 - tmp11*3);qe=11
//    tmp0 = ((z2 - z3*3)*205+512)>>10;
  //  tmp2 = ((z3 + z2*3)*205+512)>>10; // div 5
//    tmp0 = ((z2 - z3*3)*206)>>12<<2;
  //  tmp2 = ((z3 + z2*3)*205)>>10; // div 5
    //z2++;z3++;
    tmp0 = (((z2 - z3*3)*205)>>10);
    tmp2 = (((z3 + z2*3)*205)>>10); // div 5
    //a=3x+y;b=x-3y
    //(a-y)/3=3y+b; a-y=9y+3b; 10y=a-3b
    //3a-9x=x-b; 3a+b=10x

    //tmp0=(z2>>1)-z3;
    //tmp2=(z3>>1)+z2;
//(5x+2y)/2;(2x-5y)/2;
    //b-2.5a=2.5y
    //a+2.5b=2.5x
//    tmp0 = ((2*z2 - 5*z3)*140+512)>>10;
  //  tmp2 = ((2*z3 + 5*z2)*140+512)>>10; // div 5


    //tmp2 = (z3*6 + z2*13+8)>>4;
    //tmp2 = (z3*3 + z2*6+4)>>3;
    //tmp0 = (z2*2 - z3*3+2)>>2;
    //tmp2 = (z3*2 + z2*3+2)>>2;


    //tmp0 = (z2-z3);
    //tmp2 = (z2+z3);

    p[0*step]=tmp10 + tmp2;
    p[1*step]=tmp12 + tmp0;
    p[2*step]=tmp12 - tmp0;
    p[3*step]=tmp10 - tmp2;
    
   /*
   int p0=p[0];
   int p1=p[1*step];
 //  p1++;p1>>=1;
   //p0++;p0>>=1;
   int p3In=p[3*step];
   
   
   //int r1=p[2*step]+p[3*step];
   //int r2=r1-p[3*step]*2;
   int r1=p[2*step]+p[3*step];
   int r2=p[2*step]-p[3*step];

   p[0]=(p0+p1);
   p[3*step]=(p0-p1);//p[0]-p1*2;
  
   int a1=(p[0]*3+p[3*step]);//+2)>>2);
   int a2=(p[0]+p[3*step]*3);//+2)>>2);
   p[1*step]=((a1+2)>>2)+r1;
   p[2*step]=((a2+2)>>2)+r2;
   //p[1*step]=(a1+r1*8+2)>>2;
   //p[2*step]=(a2+r2*8+2)>>2;
   */
}

int testFStrenght(int a,int b, int c, int d){
   short s[4];s[0]=a;s[1]=b;s[2]=c;s[3]=d;  //={a,b,c,d};
   itrR4<1>(&s[0]);
   int r=(s[0]*3+s[1]+2)>>2;//-s[3];
   //int r=(s[0]*5+s[1]*3+4)>>3;//-s[3];
   return (((r>=0?r:-r)+3)>>2);
}

void trxF(short *p){
   int i;
   
   for( i = 0; i < 16; i+=4 )
   {

      TR4(1);p+=4;

  //    trR4<1>(&d[i]);
   }
   p-=16;
   for( i = 0; i < 4; i++ )
   {
    //  trR4<4>(&d[i]);
      TR4(4);
      p++;
   }

}
template<int step, class T>
static void itr2R4(T *p){
    int tmp0 = (int) p[0*step];
    int tmp2 = (int) p[2*step];
    
    int tmp10 = (tmp0 + tmp2)*5;
    int tmp12 = (tmp0 - tmp2)*5;
    
    /* Odd part */
    /* Same rotation as in the even part of the 8x8 LL&M IDCT */

    int z2 = (int) p[1*step];
    int z3 = (int) p[3*step];
    tmp0 = (((z2 - z3*3)));
    tmp2 = (((z3 + z2*3))); // div 5

    p[0*step]=tmp10 + tmp2;
    p[1*step]=tmp12 + tmp0;
    p[2*step]=tmp12 - tmp0;
    p[3*step]=tmp10 - tmp2;
    
}

void itrx2F(short *p){
   int i;
 //  d[0]+=16;
   
   for( i = 0; i < 4; i++ )
   {
      itr2R4<4>(&p[i]);

   }
//   p-=4;
   
   for( i = 0; i < 16; i+=4 )
   {
      itr2R4<1>(&p[i]);
      //TR4(1);p+=4;
   }

}


void itrxF(short *p){
   int i;
 //  d[0]+=16;
   for( i = 0; i < 4; i++ )
   {
      itrR4<4>(&p[i]);

   }
//   p-=4;
   
   for( i = 0; i < 16; i+=4 )
   {
      itrR4<1>(&p[i]);
      //TR4(1);p+=4;
   }

}
void itrxF_str8(short *p){
   int i;
 //  d[0]+=16;
   for( i = 0; i < 4; i++ )
   {
      itrR4<8>(&p[i]);

   }
//   p-=4;
   
   for( i = 0; i < 64; i+=8 )
   {
      itrR4<1>(&p[i]);
      //TR4(1);p+=4;
   }

}

       

template<int iDif>
static void t_fTina_x(unsigned char *src,unsigned char *src2, int stride,DCT_TYPE *d){
   int i;
   
   for( i = 0; i < 16; i+=4 )
   {
      
      d[i+0]=src[0];d[i+1]=src[3];
      d[i+2]=src[6];d[i+3]=src[9];
      if(iDif){

         d[i+0]-=src2[0];d[i+1]-=src2[3];
         d[i+2]-=src2[6];d[i+3]-=src2[9];
        // d[i+0]+=256;d[i+3]+=256;d[i+2]+=256;d[i+1]+=256;
         src2+=stride;
      }
      src+=stride;

   }
   trR4<1>(&d[0]);
   trR4<1>(&d[4]);
   trR4<1>(&d[8]);
   trR4<1>(&d[12]);

   trR4<4>(&d[0]);
   trR4<4>(&d[1]);
   trR4<4>(&d[2]);
   trR4<4>(&d[3]);
   /*

   for( i = 0; i < 4; i++ )
   {
      trR4<4>(&d[i]);
   }
   */

   //for(i=0;i<16;i++){d[i]>>=1;}

   if(!iDif){
      d[0]-=128*16;
      d[0]*=2;
   }
 
}

#if 0
template<int iAdd>
static void t_iTina_x_test(DCT_TYPE *d, unsigned char *dstp, int stride){
   int i;
   
   if(!iAdd){
      d[0]+=128*16*2+1;
      d[0]>>=1;
   }
   d[0]+=8;


    for( i = 0; i < 4; i++ )
    {
       //itrR4<4>(&d[i]);
       /*
       int tmp0 = (int) d[0*4];
       int tmp2 = (int) d[2*4];
       
       const int tmp10 = (tmp0 + tmp2);
       const int tmp12 = (tmp0 - tmp2);
       const int z2 = (int) d[1*4];
       const int z3 = (int) d[3*4];
       tmp0 = ((z2 - z3*3)*205+512)>>10;
       tmp2 = ((z3 + z2*3)*205+512)>>10; // div 5
       d[0*4]=tmp10 + tmp2;
       d[3*4]=tmp10 - tmp2;
       d[1*4]=tmp12 + tmp0;
       d[2*4]=tmp12 - tmp0;
       */
          int tmp0 = (int) d[0];
          int tmp2 = (int) d[8];
          
          const int tmp10 = (tmp0 + tmp2);
          const int tmp12 = (tmp0 - tmp2);
          const int z2 = (int) d[4]/5;
          const int z3 = (int) d[12]/5;
#define ITRX(_R1,_R2,_A,_B) _R1=(_A - _B*3);_R2=(_B + _A*3)

          ITRX(tmp0,tmp2,z2,z3);
          d[0]=tmp10 + tmp2;
          d[12]=tmp10 - tmp2;
          d[4]=tmp12 + tmp0;
          d[8]=tmp12 - tmp0;
       

       d++;
       

    }
    d-=4;

    for( i = 0; i < 16; i+=4 )
    {
   
   
//#define t_clipf(_A) xiclp_0_256x[_A]
//#define t_clipf(_A) (_A)

          int tmp0 = (int) d[0*1];
          int tmp2 = (int) d[2*1];
          
          const int tmp10 = (tmp0 + tmp2);
          const int tmp12 = (tmp0 - tmp2);
          const int z2 = (int) d[1*1]/5;
          const int z3 = (int) d[3*1]/5;
    //tmp0 = ((z2 - z3*3)*9+16)>>5;
    //tmp2 = ((2*z2+z3)*9+16)>>5;

#ifdef T_USE_T_TR
//         tmp0 = ((z2 - z3*3)*205+512)>>10;
  //       tmp2 = ((z3 + z2*3)*205+512)>>10; // div 5
          ITRX(tmp0,tmp2,z2,z3);

//         tmp0 = ((z2 - (int)(z3*1.5))*625+512)>>10;
  //       tmp2 = ((z3 + (int)(z2*1.5))*625+512)>>10; // div 5

//         tmp0 = (z2/3 - z3);
  //       tmp2 = (z3/3 + z2); // div 5,3
#else
         tmp0 = (((z2>>1) - z3)*51)>>6;
         tmp2 = (((z3>>1) + z2)*51)>>6;
#endif
   //#define t_absf(_A) (((_A)>=255)?255:((_A)<=0)?0:(_A))
          /*
	          dstp[ 0] = t_absf(((tmp10 + tmp2+8)>>4)+iAdd*dstp[ 0]);
	          dstp[ 3] = t_absf(((tmp12 + tmp0+8)>>4)+iAdd*dstp[ 3]);
	          dstp[ 6] = t_absf(((tmp12 - tmp0+8)>>4)+iAdd*dstp[ 6]);
	          dstp[ 9] = t_absf(((tmp10 - tmp2+8)>>4)+iAdd*dstp[ 9]);

          */
//#define _ADD_R +8 
          if(iAdd){
	          dstp[ 0] = t_clipf(((tmp10 + tmp2 )>>4)+dstp[ 0]);
	          dstp[ 3] = t_clipf(((tmp12 + tmp0 )>>4)+dstp[ 3]);
	          dstp[ 6] = t_clipf(((tmp12 - tmp0 )>>4)+dstp[ 6]);
	          dstp[ 9] = t_clipf(((tmp10 - tmp2 )>>4)+dstp[ 9]);
          }
          else{
	          dstp[ 0] = t_clipf(((tmp10 + tmp2 )>>4));
	          dstp[ 3] = t_clipf(((tmp12 + tmp0 )>>4));
	          dstp[ 6] = t_clipf(((tmp12 - tmp0 )>>4));
	          dstp[ 9] = t_clipf(((tmp10 - tmp2 )>>4));
          }
       d+=4;
       dstp+=stride;
    }
}
#endif

#if 0
//def __SYMBIAN32__
static int iB;void debugsi(char *c, int a){}
#else
static int iB;void debugsi(char *c, int a);
#endif
template<int iAdd>
static void t_iTina_x(DCT_TYPE *d, unsigned char *dstp, int stride){
   int i;
   int cl;
#define DCT_TYPE_CALC int
   DCT_TYPE_CALC tmp10,tmp12,tmp0,tmp2,z2,z3;
   /*
   if(!iAdd){
      d[0]+=128*16*2;
      //d[0]++;d[0]>>=1;
      d[0]<<=1;
   }
   else d[0]*=4;
   */
 // d[2];
  //d[8]*=7;d[8]>>=3;
  //d[2]*=7;d[2]>>=3;
   
   if(!iAdd){
      d[0]+=128*16*2+1;      d[0]>>=1;
     // d[0]<<=1;
   }
  // else d[0]*=4;
//   d[0]+=256*16;

   d[0]+=8;
    for( i = 0; i < 4; i++ )
    {
       //itrR4<4>(&d[i]);
       /*
       int tmp0 = (int) d[0*4];
       int tmp2 = (int) d[2*4];
       
       const int tmp10 = (tmp0 + tmp2);
       const int tmp12 = (tmp0 - tmp2);
       const int z2 = (int) d[1*4];
       const int z3 = (int) d[3*4];
       tmp0 = ((z2 - z3*3)*205+512)>>10;
       tmp2 = ((z3 + z2*3)*205+512)>>10; // div 5
       d[0*4]=tmp10 + tmp2;
       d[3*4]=tmp10 - tmp2;
       d[1*4]=tmp12 + tmp0;
       d[2*4]=tmp12 - tmp0;
       */
       //d[0]<<=2;d[4]<<=2;d[12]<<=2;d[8]<<=2;
       if(1)//d[4]|d[8]|d[12])
       {
          tmp0 = (DCT_TYPE_CALC) d[0];
          tmp2 = (DCT_TYPE_CALC) d[8];
          
          tmp10 = (tmp0 + tmp2);
          tmp12 = (tmp0 - tmp2);
          z2 = (DCT_TYPE_CALC) d[4];
          z3 = (DCT_TYPE_CALC) d[12];
          //3,2
//    tmp0 = ((z2 - z3*3)*9+16)>>5;
  //  tmp2 = ((2*z2+z3)*9+16)>>5;
    //3.3
#ifdef T_USE_T_TR
          //205
        //-- tmp0 = ((z2 - z3*3)*205+512)>>10;
        //-- tmp2 = ((z3 + z2*3)*205+512)>>10; // div 5
#undef ITRX
//---
//    tmp0 = (((z2 - z3*3)*41+512)>>10)*5;
  //  tmp2 = (((z3 + z2*3)*41+512)>>10)*5; // div 5

//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)*205)+512)>>10;_R2=(((_B + _A*3)*205)+512)>>10
#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)*205))>>10;_R2=(((_B + _A*3)*205))>>10
//#define ITRX2(_R1,_R2,_A,_B) _R1=(((_A - _B*3)*205))>>10;_R2=(((_B + _A*3)*205))>>10
//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)));_R2=(((_B + _A*3)))
//#define ITRX2 ITRX

//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)))>>1;_R2=(((_B + _A*3)))>>1
#define ITRX2 ITRX
//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)*3))>>1;_R2=(((_B + _A*3)*3))>>1
//#define ITRX(_R1,_R2,_A,_B) _R1=((_A*204 - _B*615))>>10;_R2=((_B*204 + _A*615))>>10
//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)>>1)*3);_R2=(((_B + _A*3)>>1)*3)
//#define ITRX2 ITRX

//#define ITRX(_R1,_R2,_A,_B) _R1=((((_A - _B*3)*41+512)>>11)*5);_R2=((((_B + _A*3)*41+512)>>10)*5)
//#define ITRX2(_R1,_R2,_A,_B) _R1=((((_A - _B*3)*41+512)>>11)*5);_R2=((((_B + _A*3)*41+512)>>10)*5)

//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)>>1));_R2=(((_B + _A*3)>>1))
//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A>>1) - _B));_R2=((((_B>>1) + _A)))
//#define ITRX(_R1,_R2,_A,_B) _R1=(_A - _B*3)/5;_R2=(_B + _A*3)/5
//#define ITRX(_R1,_R2,_A,_B) _R1=(((_A - _B*3)*222))>>10;_R2=(((_B + _A*3)*222))>>10
//#define ITRX(_R1,_R2,_A,_B) _R1=((_A - _B*3));_R2=((_B + _A*3))
//#define ITRX(_R1,_R2,_A,_B) _R1=((_A - _B*3)*205)>>10;_R2=((_B + _A*3)*205)>>10
//#define ITRX(_R1,_R2,_A,_B) _R1=((_A - _B*3)*215)>>10;_R2=((_B + _A*3)*215)>>10
//#define ITRX(_R1,_R2,_A,_B) _R1=((_A - _B*2)*51)>>7;_R2=((_B + _A*2)*51)>>7
          

//#define ITRX(_R1,_R2,_A,_B) _R1=((_A - _B*3)*819)>>12;_R2=((_B + _A*3)*819)>>12
      //tmp0 = (z2*13 - z3*78+32)>>6;
    //tmp2 = (z3*26 + z2*39+32)>>6;    
//#define ITRX(_R1,_R2,_A,_B) _R1=(_A - _B*3);_R2=(_B + _A*3)
//#define ITRX(_R1,_R2,_A,_B) _R1=(_A - _B*3)/5;_R2=(_B + _A*3)/5

          ITRX(tmp0,tmp2,z2,z3);

         //tmp0 = ((z2 - (int)(z3*1.5))*625+512)>>10;
         //tmp2 = ((z3 + (int)(z2*1.5))*625+512)>>10; // div 5
        // tmp0 = ((z2*3 - z3)*205+512)>>10;
     //    tmp2 = ((z3*3 + z2)*205+512)>>10; // div 5
         //tmp0 = (z2 - z3*3)>>2; tmp2 = (z3 + z2*3)>>2; // div 5
//         tmp0 = (z2/3 - z3);
  //       tmp2 = (z3/3 + z2); // div 5,3
/*
               static const DCT_TYPE tt[]={
                  64,51,64,51,
                  51,41,51,41,
                  64,51,64,51,
                  51,41,51,41,
               };
*/
#else
#undef ITRX
//---
#define ITRX(_R1,_R2,_A,_B) _R1=((_A - _B*2)*51)>>7;_R2=((_B + _A*2)*51)>>7
#define ITRX2 ITRX
          ITRX(tmp0,tmp2,z2,z3);
#endif
          d[0]=(tmp10 + tmp2);
          d[12]=(tmp10 - tmp2);
          d[4]=(tmp12 + tmp0);
          d[8]=(tmp12 - tmp0);
       }
       else {
          d[8]=d[12]=d[4]=d[0];//=d[0];
       }

       d++;
       

    }
#define _SH_X >>4
//#define _SH_X /400
    d-=4;

    for( i = 0; i < 16; i+=4 )
    {
       
 //#define CLIP_RESULT(_x)      if((unsigned int)_x > 255){_x = 255 & (~(_x>>31));}
//#define t_clipf(_A) xiclp_0_256x[_A]
//#define t_clipf(_A) _A
//#define t_clipf2(_R,_A) _R=xiclp_0_256x[_A]
//#define t_clipf2(_R,_A) _R=_A;
//---------
#define t_clipf2(_R,_A) cl=_A;if((unsigned int)cl > 255){_R = 255 & (~(cl>>31));}else _R=cl;

       
       //#define t_clipf2(_R,_A) cl=_A;if((unsigned int)cl > 255){cl = 255 & (~(cl>>31));iB++;debugsi(#_A #_R,iB);}_R=cl;
//#define t_clipf2(_R,_A) cl=_A;if((unsigned int)cl > 255){cl = 255 & (~(cl>>31));}_R=cl;
//#define t_clipf2(_R,_A) _R=_A;//besis 0.001% tikai hito
//#define t_clipf(_A) (_A)

       if(1){//d[1]|d[2]|d[3]){
          tmp0 = (DCT_TYPE_CALC) d[0];
          tmp2 = (DCT_TYPE_CALC) d[2];
          
          tmp10 = (tmp0 + tmp2);
          tmp12 = (tmp0 - tmp2);
          z2 = (DCT_TYPE_CALC) d[1];
           z3 = (DCT_TYPE_CALC) d[3];
          ITRX2(tmp0,tmp2,z2,z3);
   //#define t_absf(_A) (((_A)>=255)?255:((_A)<=0)?0:(_A))
          /*
	          dstp[ 0] = t_absf(((tmp10 + tmp2+8)>>4)+iAdd*dstp[ 0]);
	          dstp[ 3] = t_absf(((tmp12 + tmp0+8)>>4)+iAdd*dstp[ 3]);
	          dstp[ 6] = t_absf(((tmp12 - tmp0+8)>>4)+iAdd*dstp[ 6]);
	          dstp[ 9] = t_absf(((tmp10 - tmp2+8)>>4)+iAdd*dstp[ 9]);

          */

//#define _ADD_R +8 
          if(iAdd){
             //5<<1
	          t_clipf2(dstp[ 0],((tmp10 + tmp2 )_SH_X)+dstp[ 0]);
	          t_clipf2(dstp[ 3],((tmp12 + tmp0 )_SH_X)+dstp[ 3]);
	          t_clipf2(dstp[ 6],((tmp12 - tmp0 )_SH_X)+dstp[ 6]);
	          t_clipf2(dstp[ 9],((tmp10 - tmp2 )_SH_X)+dstp[ 9]);
          }
          else{
	          t_clipf2(dstp[ 0],((tmp10 + tmp2 )_SH_X));
	          t_clipf2(dstp[ 3],((tmp12 + tmp0 )_SH_X));
	          t_clipf2(dstp[ 6],((tmp12 - tmp0 )_SH_X));
	          t_clipf2(dstp[ 9],((tmp10 - tmp2 )_SH_X));
          }
       }
       else{
          
          if(iAdd){
             const DCT_TYPE_CALC dc=(d[0] _SH_X);
	           t_clipf2(dstp[ 0],dc+dstp[ 0]);
	           t_clipf2(dstp[ 3],dc+dstp[ 3]);
	           t_clipf2(dstp[ 6],dc+dstp[ 6]);
	           t_clipf2(dstp[ 9],dc+dstp[ 9]);
          }
          else{
  	         t_clipf2(dstp[ 0] = dstp[ 3] =  dstp[ 6] = dstp[ 9],(d[0] _SH_X));
       //       unsigned int re;t_clipf2(re,(d[0]>>4));*(unsigned int*)dstp=0x01010101*re;
             //t_clipf2(dstp[ 0] = dstp[ 3] =  dstp[ 6] = dstp[ 9],(d[0]>>_SH_X));
//	          dstp[ 0] = dstp[ 3] =  dstp[ 6] = dstp[ 9] = t_clipf((d[0]>>4));
          }
       }

       d+=4;
       dstp+=stride;
    }
}
#if 0
template<int iAdd>
static void t_iTina_x_n(DCT_TYPE *d, unsigned char *dstp, int stride){
   int i;
   
   if(!iAdd){
      d[0]+=128*16*2+1;
      d[0]>>=1;
   }
//   d[0]+=16384/82;
   //d[0]+=16;
   //for(i=0;i<16;i++){d[i]<<=2;}
   //5*5*16=400
   //400/128=3.125 1024/3.125=328
//#define _D5(_V)_V=((_V*328+512)>>10);
   //128/3.125=40.96
   //#define _D_3_125(_V)_V=((_V*41+64)>>7);
   #define _D_3_125(_V)_V=(_V>>7);
   //d[0]+=64;
    int r[20]; 
    for( i = 0; i < 4; i++ )
    {

      int tmp0 = (int) d[0];
      int tmp2 = (int) d[8];

      const int tmp10 = (tmp0 + tmp2)*5*41+64;
      const int tmp12 = (tmp0 - tmp2)*5*41+64;
      const int z2 = (int) d[4];
      const int z3 = (int) d[12];
      tmp0 = (z2 - z3*3)*41;
      tmp2 = (z3 + z2*3)*41; // div 5
      r[0+i] = (tmp10 + tmp2)>>7;
      r[12+i]= (tmp10 - tmp2)>>7;
      r[4+i] = (tmp12 + tmp0)>>7;
      r[8+i] = (tmp12 - tmp0)>>7;
     // _D_3_125(r[0+i]);_D_3_125(r[4+i]);_D_3_125(r[12+i]);_D_3_125(r[8+i]);//div 128, if not div 400

      d++;
       

    }
    d-=4;

    for( i = 0; i < 16; i+=4 )
    {
       /*
       itrR4<1>(&d[i]);

       
	    dstp[ 0] = xiclp_0_256x[((d[0+i]+8)>>4)+iAdd*dstp[ 0]];
	    dstp[ 3] = xiclp_0_256x[((d[1+i]+8)>>4)+iAdd*dstp[ 3]];
	    dstp[ 6] = xiclp_0_256x[((d[2+i]+8)>>4)+iAdd*dstp[ 6]];
	    dstp[ 9] = xiclp_0_256x[((d[3+i]+8)>>4)+iAdd*dstp[ 9]];

*/       
   
//#define t_clipf(_A) xiclp_0_256x[_A]

       //if(d[1]||d[2]||d[3]){
          int tmp0 = (int) r[0+i];
          int tmp2 = (int) r[2+i];
          
          const int tmp10 = (tmp0 + tmp2)*5+64;
          const int tmp12 = (tmp0 - tmp2)*5+64;
          const int z2 = (int) r[1+i];
          const int z3 = (int) r[3+i];
   // tmp0 = ((z2 - z3*3)*9+16)>>5;
    //tmp2 = ((2*z2+z3)*9+16)>>5;

#ifdef T_USE_T_TR
//         tmp0 = ((z2 - z3*3)*205+512)>>10;
  //       tmp2 = ((z3 + z2*3)*205+512)>>10; // div 5
         tmp0 = (z2 - z3*3);
         tmp2 = (z3 + z2*3); // div 5
//         tmp0 = ((z2 - (int)(z3*1.5))*625+512)>>10;
  //       tmp2 = ((z3 + (int)(z2*1.5))*625+512)>>10; // div 5

//         tmp0 = (z2/3 - z3);
  //       tmp2 = (z3/3 + z2); // div 5,3
#else
         tmp0 = (((z2>>1) - z3)*51)>>6;
         tmp2 = (((z3>>1) + z2)*51)>>6;
#endif
   //#define t_absf(_A) (((_A)>=255)?255:((_A)<=0)?0:(_A))
          /*
	          dstp[ 0] = t_absf(((tmp10 + tmp2+8)>>4)+iAdd*dstp[ 0]);
	          dstp[ 3] = t_absf(((tmp12 + tmp0+8)>>4)+iAdd*dstp[ 3]);
	          dstp[ 6] = t_absf(((tmp12 - tmp0+8)>>4)+iAdd*dstp[ 6]);
	          dstp[ 9] = t_absf(((tmp10 - tmp2+8)>>4)+iAdd*dstp[ 9]);

          */

          if(iAdd){
	          dstp[ 0] = t_clipf(((tmp10 + tmp2 )>>7)+dstp[ 0]);
	          dstp[ 3] = t_clipf(((tmp12 + tmp0 )>>7)+dstp[ 3]);
	          dstp[ 6] = t_clipf(((tmp12 - tmp0 )>>7)+dstp[ 6]);
	          dstp[ 9] = t_clipf(((tmp10 - tmp2 )>>7)+dstp[ 9]);
          }
          else{
	          dstp[ 0] = t_clipf(((tmp10 + tmp2 )>>7));
	          dstp[ 3] = t_clipf(((tmp12 + tmp0 )>>7));
	          dstp[ 6] = t_clipf(((tmp12 - tmp0 )>>7));
	          dstp[ 9] = t_clipf(((tmp10 - tmp2 )>>7));
          }

          /*
       }
       else{
          int dc=(d[0]+8)>>4;
          if(iAdd){
             dstp[ 0] = t_absf(dc+dstp[ 0]);
             dstp[ 3] = t_absf(dc+dstp[ 3]);
             dstp[ 6] = t_absf(dc+dstp[ 6]);
             dstp[ 9] = t_absf(dc+dstp[ 9]);
          }
          else{
             dc=xiclp_0_256x[dc];
             dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
          }
       }
       */

       d+=4;
       dstp+=stride;
    }
}
#endif
inline void t_idct4_dc(DCT_TYPE *dct, unsigned char *dstp, int stride){

//dct[0]>>=2;
#if 1
   int cl,dc;//=(dct[0]+8)>>4;
   if(dct[0]<-10)dc=-((7-dct[0])>>4);
   else if(dct[0]>10)dc=((7+dct[0])>>4);
   else return;
#else
   int dc=0;//=(dct[0]+8)>>4;
   if(dct[0]<-31)dc=-((31-dct[0])>>6);
   else if(dct[0]>31)dc=((31+dct[0])>>6);
   else return;
#endif
 //dc>>=1;
   //TODO add *(int *)dstp+=dc*0x01010101;
    for(int i = 0; i < 4; i++ )
    {
       /*
	    dstp[ 0] = xiclp_0_256x[dc+dstp[ 0]];
	    dstp[ 3] = xiclp_0_256x[dc+dstp[ 3]];
	    dstp[ 6] = xiclp_0_256x[dc+dstp[ 6]];
	    dstp[ 9] = xiclp_0_256x[dc+dstp[ 9]];
*/
#if 1
	          t_clipf2(dstp[ 0],dc+dstp[ 0]);
	          t_clipf2(dstp[ 3],dc+dstp[ 3]);
	          t_clipf2(dstp[ 6],dc+dstp[ 6]);
	          t_clipf2(dstp[ 9],dc+dstp[ 9]);

#else
       //TODO bit is safe w/o crop
       /*
       static int iEC;
       if(((int)dstp[0]+dc)>255 || ((int)dstp[0]+dc)<0 || 
          ((int)dstp[3]+dc)>255 || ((int)dstp[3]+dc)<0 || 
          ((int)dstp[6]+dc)>255 || ((int)dstp[6]+dc)<0 ||
          ((int)dstp[9]+dc)>255 || ((int)dstp[9]+dc)<0 
          )
          iEC++;
*/

       dstp[0]+=dc;
       dstp[3]+=dc;
       dstp[6]+=dc;
       dstp[9]+=dc;
#endif
	    dstp+=stride;
    }
}
void putDC16x16(DCT_TYPE dc_c, unsigned char *dstp, int stride){
#if 1
#if 0
   //332 144
   int dc=dc_c+128*2*16+1;
   dc>>=1;
   dc+=8;dc>>=4;
#else
   int dc=dc_c+128*2*16+16; dc>>=5;
  // dc+=128*2*16+16;   dc>>=5;
 //  int dc=dc_c+128*16+8;   dc>>=4;

#endif 
#else
   int dc=dc_c;//=(dct[0]+8)>>4;
   dc+=128*8*16+64;
   dc>>=7;
#endif
   //if(dc_c<0)dc=-((8-dc_c)>>4);
   //else if(dc_c>0)dc=((8+dc_c)>>4);
//   dc+=128;
   if(dc<1)dc=1;else if(dc>254)dc=254;
//    dc*=0x01010101;

    stride-=12*3;
    for(int i = 0; i < 16; i++ )
    {
       
       dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
       dstp+=12;
       dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
       dstp+=12;
       dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
       dstp+=12;
       dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
/*
    *(unsigned int*)dstp=dc;dstp+=12;
    *(unsigned int*)dstp=dc;dstp+=12;
    *(unsigned int*)dstp=dc;dstp+=12;
    *(unsigned int*)dstp=dc;
*/
       dstp+=stride;
    }

}

void addDC16x16(DCT_TYPE dc_c, unsigned char *dstp, int stride){
  // dc_c>>=2;
   int dc;//=(dct[0]+8)>>4;
   if(dc_c<-10)dc=-((7-dc_c)>>4);
   else if(dc_c>10)dc=((7+dc_c)>>4);
   else return;
   //dc>>=1;
//74 
#define _T_MADD
#ifdef _T_MADD
#if 1
    for(int i = 0; i < 16; i++ )
    {
       dstp[0]+=dc;
       dstp[3]+=dc;
       dstp[6]+=dc;
       dstp[9]+=dc;

       dstp[12]+=dc;
       dstp[15]+=dc;
       dstp[18]+=dc;
       dstp[21]+=dc;

       dstp[24]+=dc;
       dstp[27]+=dc;
       dstp[30]+=dc;
       dstp[33]+=dc;

       dstp[36]+=dc;
       dstp[39]+=dc;
       dstp[42]+=dc;
       dstp[45]+=dc;
       dstp+=stride;
    }
#else



   if(dc<0){
      dc=-dc;
      unsigned int dc1=(unsigned int)dc*0x01000001;
      unsigned int dc2=(unsigned int)dc*0x00010000;
      unsigned int dc3=(unsigned int)dc*0x00000100;
      for(int i = 0; i < 16; i++ )
      {
         *(unsigned int*)dstp-=dc1;
         *(unsigned int*)&dstp[4]-=dc2;
         *(unsigned int*)&dstp[8]-=dc3;
         *(unsigned int*)&dstp[12]-=dc1;
         *(unsigned int*)&dstp[16]-=dc2;
         *(unsigned int*)&dstp[20]-=dc3;
         *(unsigned int*)&dstp[24]-=dc1;
         *(unsigned int*)&dstp[28]-=dc2;
         *(unsigned int*)&dstp[32]-=dc3;
         *(unsigned int*)&dstp[36]-=dc1;
         *(unsigned int*)&dstp[40]-=dc2;
         *(unsigned int*)&dstp[44]-=dc3;
         dstp+=stride;
      }
   }
   else{
      unsigned int dc1=(unsigned int)dc*0x01000001;
      unsigned int dc2=(unsigned int)dc*0x00010000;
      unsigned int dc3=(unsigned int)dc*0x00000100;
      for(int i = 0; i < 16; i++ )
      {
         *(unsigned int*)dstp+=dc1;
         *(unsigned int*)&dstp[4]+=dc2;
         *(unsigned int*)&dstp[8]+=dc3;
         *(unsigned int*)&dstp[12]+=dc1;
         *(unsigned int*)&dstp[16]+=dc2;
         *(unsigned int*)&dstp[20]+=dc3;
         *(unsigned int*)&dstp[24]+=dc1;
         *(unsigned int*)&dstp[28]+=dc2;
         *(unsigned int*)&dstp[32]+=dc3;
         *(unsigned int*)&dstp[36]+=dc1;
         *(unsigned int*)&dstp[40]+=dc2;
         *(unsigned int*)&dstp[44]+=dc3;
         dstp+=stride;
      }
   }
#endif

#else
    for(int i = 0; i < 16; i++ )
    {
       
	    dstp[ 0] = xiclp_0_256x[dc+dstp[ 0]];
	    dstp[ 3] = xiclp_0_256x[dc+dstp[ 3]];
	    dstp[ 6] = xiclp_0_256x[dc+dstp[ 6]];
	    dstp[ 9] = xiclp_0_256x[dc+dstp[ 9]];

	    dstp[ 0+12] = xiclp_0_256x[dc+dstp[ 0+12]];
	    dstp[ 3+12] = xiclp_0_256x[dc+dstp[ 3+12]];
	    dstp[ 6+12] = xiclp_0_256x[dc+dstp[ 6+12]];
	    dstp[ 9+12] = xiclp_0_256x[dc+dstp[ 9+12]];

	    dstp[ 0+24] = xiclp_0_256x[dc+dstp[ 0+24]];
	    dstp[ 3+24] = xiclp_0_256x[dc+dstp[ 3+24]];
	    dstp[ 6+24] = xiclp_0_256x[dc+dstp[ 6+24]];
	    dstp[ 9+24] = xiclp_0_256x[dc+dstp[ 9+24]];

	    dstp[ 0+12+24] = xiclp_0_256x[dc+dstp[ 0+12+24]];
	    dstp[ 3+12+24] = xiclp_0_256x[dc+dstp[ 3+12+24]];
	    dstp[ 6+12+24] = xiclp_0_256x[dc+dstp[ 6+12+24]];
	    dstp[ 9+12+24] = xiclp_0_256x[dc+dstp[ 9+12+24]];
       dstp+=stride;
    }
#endif

}
inline void t_idct4_dc_p(DCT_TYPE *dct, unsigned char *dstp, int stride){

   int dc;//=(dct[0]+8)>>4;
   //dct[0]+=128*32;
#if 1
  // dct[0]>>=2;
   /*
   dct[0]+=1;dct[0]>>=1;
   if(dct[0]<0)dc=-((8-dct[0])>>4)+128;
   else if(dct[0]>0)dc=((8+dct[0])>>4)+128;
   else dc=128;
   */
#if 0
   dc=dct[0];//=(dct[0]+8)>>4;
   dc+=128*2*16+1;
   dc>>=1;
   dc+=8;dc>>=4;
#else
   dc=dct[0];//=(dct[0]+8)>>4;
   dc+=128*2*16+16;   dc>>=5;
   //dc+=128*16+8;   dc>>=4;
#endif
   //dc+=128;
#else
   if(dct[0]<0)dc=-((64-dct[0])>>7);
   else if(dct[0]>0)dc=((64+dct[0])>>7);
   dc+=128;


#endif
   //dc>>=2;
   
//   if(dc<1)dc=1;else if(dc>254)dc=254;
   if(dc<1)dc=1;else if(dc>254)dc=254;
#if 1
   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
   dstp+=stride;
   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
   dstp+=stride;
   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
   dstp+=stride;
   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
#else
    dc*=0x01010101;
    *(unsigned int*)dstp=dc;
   dstp+=stride;
    *(unsigned int*)dstp=dc;
   dstp+=stride;
    *(unsigned int*)dstp=dc;
   dstp+=stride;
    *(unsigned int*)dstp=dc;
#endif
//   dstp+=stride;
}
#define T_USE264t 1
#define T_USE264t2 1
void t_idct8_s(DCT_TYPE *t, unsigned char *dst, int stride, int iX)
{
   //iX=0xff;
#if T_USE264t
   if(T_USE264t2){
      if((iX&1))t_iTina_x<1>(t,dst,stride);                  else if(t[0])t_idct4_dc(t,dst,stride);
      if((iX&2))t_iTina_x<1>(t+16,dst+4*3,stride);           else if(t[16])t_idct4_dc(t+16,dst+4*3,stride);
      if((iX&4))t_iTina_x<1>(t+32,dst+stride*4,stride);      else if(t[32])t_idct4_dc(t+32,dst+stride*4,stride);
      if((iX&8))t_iTina_x<1>(t+48,dst+4*3+stride*4,stride);  else if(t[48])t_idct4_dc(t+48,dst+4*3+stride*4,stride);
   }
   else{
      if(iX&1)idct4x4dc_s_p(t,dst,stride);
      if(iX&2)idct4x4dc_s_p(t+16,dst+4*3,stride);
      if(iX&4)idct4x4dc_s_p(t+32,dst+stride*4,stride);
      if(iX&8)idct4x4dc_s_p(t+48,dst+4*3+stride*4,stride);
   }
#else
   if(iX&1)t_ivc1_x<1>(t,dst,stride);
   if(iX&2)t_ivc1_x<1>(t+16,dst+4*3,stride);
   if(iX&4)t_ivc1_x<1>(t+32,dst+stride*4,stride);
   if(iX&8)t_ivc1_x<1>(t+48,dst+4*3+stride*4,stride);
#endif

}
void t_idct8_s_put_f(DCT_TYPE *t, unsigned char *dst, int stride, int iX){
   if(T_USE264t){
   if((iX&1))t_iTina_x<0>(t,dst,stride);                  else t_idct4_dc_p(t,dst,stride);
   if((iX&2))t_iTina_x<0>(t+16,dst+4*3,stride);           else t_idct4_dc_p(t+16,dst+4*3,stride);
   if((iX&4))t_iTina_x<0>(t+32,dst+stride*4,stride);      else t_idct4_dc_p(t+32,dst+stride*4,stride);
   if((iX&8))t_iTina_x<0>(t+48,dst+4*3+stride*4,stride);  else t_idct4_dc_p(t+48,dst+4*3+stride*4,stride);
   }
   else{
      //t_idct4_dc_p_vc1

   }
}
void t_idct8_s_put(DCT_TYPE *t, unsigned char *dst, int stride){
#if T_USE264t
   if(T_USE264t2){
   t_iTina_x<0>(t,dst,stride);
   t_iTina_x<0>(t+16,dst+4*3,stride);
   t_iTina_x<0>(t+32,dst+stride*4,stride);
   t_iTina_x<0>(t+48,dst+4*3+stride*4,stride);
   }
   else{
   idct4x4dc_s_p_put(t,dst,stride);
   idct4x4dc_s_p_put(t+16,dst+4*3,stride);
   idct4x4dc_s_p_put(t+32,dst+stride*4,stride);
   idct4x4dc_s_p_put(t+48,dst+4*3+stride*4,stride);
   }

#else
   t_ivc1_x<0>(t,dst,stride);
   t_ivc1_x<0>(t+16,dst+4*3,stride);
   t_ivc1_x<0>(t+32,dst+stride*4,stride);
   t_ivc1_x<0>(t+48,dst+4*3+stride*4,stride);
#endif
}

void t_fdct8_s(unsigned char *src, int stride, DCT_TYPE *d ){
#if T_USE264t
   if(T_USE264t2){
   t_fTina_x<0>(src,NULL,stride,d);
   t_fTina_x<0>(src+12,NULL,stride,d+16);
   t_fTina_x<0>(src+stride*4,NULL,stride,d+32);
   t_fTina_x<0>(src+stride*4+12,NULL,stride,d+48);
   }else{
   dct4x4dc_s_p(src,stride,d);
   dct4x4dc_s_p(src+12,stride,d+16);
   dct4x4dc_s_p(src+stride*4,stride,d+32);
   dct4x4dc_s_p(src+stride*4+12,stride,d+48);
   }
#else
   t_fvc1_x<0>(src,NULL,stride,d);
   t_fvc1_x<0>(src+12,NULL,stride,d+16);
   t_fvc1_x<0>(src+stride*4,NULL,stride,d+32);
   t_fvc1_x<0>(src+stride*4+12,NULL,stride,d+48);
#endif
}

void t_fdct8_s_d(unsigned char *src,unsigned char *src2, int stride, DCT_TYPE *d ){
#if T_USE264t
   if(T_USE264t2){
   t_fTina_x<1>(src,src2,stride,d);
   t_fTina_x<1>(src+12,src2+12,stride,d+16);
   t_fTina_x<1>(src+stride*4,src2+stride*4,stride,d+32);
   t_fTina_x<1>(src+stride*4+12,src2+stride*4+12,stride,d+48);
   }
   else{
   dct4x4dc_s_p_d(src,src2,stride,d);
   dct4x4dc_s_p_d(src+12,src2+12,stride,d+16);
   dct4x4dc_s_p_d(src+stride*4,src2+stride*4,stride,d+32);
   dct4x4dc_s_p_d(src+stride*4+12,src2+stride*4+12,stride,d+48);
   }
#else
   t_fvc1_x<1>(src,src2,stride,d);
   t_fvc1_x<1>(src+12,src2+12,stride,d+16);
   t_fvc1_x<1>(src+stride*4,src2+stride*4,stride,d+32);
   t_fvc1_x<1>(src+stride*4+12,src2+stride*4+12,stride,d+48);
#endif
}
void t_fdct4_s_d(unsigned char *src,unsigned char *src2, int stride, DCT_TYPE *d ){
   t_fTina_x<1>(src,src2,stride,d);
}

inline void t_idct4_dc_p_vc1(DCT_TYPE *dct, unsigned char *dstp, int stride){

   int dc=0;//=(dct[0]+8)>>4;
//p[0]+=904;p[0]++;p[0]>>=1;
   dct[0]+=1;dct[0]>>=1;
   if(dct[0]<0)dc=-((8-dct[0])>>4);
   else if(dct[0]>0)dc=((8+dct[0])>>4);
   //dc=xiclp_0_256x[dc+128];
   dc+=128;
   if(dc<0)dc=0;else if(dc>255)dc=255;

   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
   dstp+=stride;
   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
   dstp+=stride;
   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
   dstp+=stride;
   dstp[ 9] =dstp[ 6] =dstp[ 3] =dstp[ 0] = dc;
//   dstp+=stride;
}


void t_idct4_s(DCT_TYPE *t, unsigned char *dst, int stride, int iX){
   if((iX&1))t_iTina_x<1>(t,dst,stride);                  else if(t[0])t_idct4_dc(t,dst,stride);
}
void i_vc1_4x4(DCT_TYPE *t, unsigned char *dst, int stride){
  t_ivc1_x<0>(t,dst,stride);
}

void trHadaBL(short *bl, int stride, int *d){
   int i;
   for(i = 0; i < 16; i+=4 )
   {
      
      d[i+0]=bl[0];d[i+1]=bl[1];
      d[i+2]=bl[2];d[i+3]=bl[3];

      bl+=stride;
      trR4<1>(&d[i]);

   }

   for( i = 0; i < 4; i++ )
   {
      trR4<4>(&d[i]);
   }

}
void trHada(unsigned char *src,unsigned char *src2, int stride, int *d)
{
   int i;
   
   for( i = 0; i < 16; i+=4 )
   {
      
      d[i+0]=src[0];d[i+1]=src[1];
      d[i+2]=src[2];d[i+3]=src[3];

      d[i+0]-=src2[0];d[i+1]-=src2[1];
      d[i+2]-=src2[2];d[i+3]-=src2[3];
      src2+=stride;
      src+=stride;
//      trR4<1>(&d[i]);

   }
/*
   for( i = 0; i < 4; i++ )
   {
      trR4<4>(&d[i]);
   }
   */
   trR4<1>(&d[0]);
   trR4<1>(&d[4]);
   trR4<1>(&d[8]);
   trR4<1>(&d[12]);
   trR4<4>(&d[0]);
   trR4<4>(&d[1]);
   trR4<4>(&d[2]);
   trR4<4>(&d[3]);

   
}
void trHada2r(unsigned char *src,unsigned char *src2,unsigned char *src3, int stride, int *d)
{
   int i;
   
   for( i = 0; i < 16; i+=4 )
   {
      
      d[i+0]=src[0]<<1;d[i+1]=src[1]<<1;
      d[i+2]=src[2]<<1;d[i+3]=src[3]<<1;

      d[i+0]-=(src2[0]+src3[0]);d[i+1]-=(src2[1]+src3[1]);
      d[i+2]-=(src2[2]+src3[2]);d[i+3]-=(src2[3]+src3[3]);
      src2+=stride;
      src3+=stride;
      src+=stride;
//      trR4<1>(&d[i]);

   }
   trR4<1>(&d[0]);
   trR4<1>(&d[4]);
   trR4<1>(&d[8]);
   trR4<1>(&d[12]);
   trR4<4>(&d[0]);
   trR4<4>(&d[1]);
   trR4<4>(&d[2]);
   trR4<4>(&d[3]);
/*
   for( i = 0; i < 4; i++ )
   {
      trR4<4>(&d[i]);
   }
   */
   
}

#include <math.h>
int sadHada(unsigned char *src,unsigned char *src2, int stride, int *s)
{
    int iSad=0;


   //
   //t_fTina_x<1>(src,src2,stride,d);
   int i;
   int d[16];
   
   for( i = 0; i < 16; i+=4 )
   {
      
      d[i+0]=src[0];d[i+1]=src[1];
      d[i+2]=src[2];d[i+3]=src[3];

      d[i+0]-=src2[0];d[i+1]-=src2[1];
      d[i+2]-=src2[2];d[i+3]-=src2[3];
      src2+=stride;
      src+=stride;
//      trR4<1>(&d[i]);

   }

  // for( i = 0; i < 4; i++ )trR4<4>(&d[i]);
   
   
fdct4x4dc_s(&d[0]);
/*
//d[0]+=2;d[0]>>=2;
   static const int sh[]={
      0,1,0,1,
      1,2,1,3,
      0,1,2,2,
      1,3,2,3,
   };
for(i=0;i<16;i++){
   iSad+=s[(d[i]+sh[i])>>sh[i]];
}
*/
for(i=0;i<16;i++)iSad+=abs(d[i]);

     return iSad;// >>3;//((iSad-64)>>2)+16;
}

int sadHada_dev(unsigned char *src, int stride, int *s, int *dc)
{
    int iSad;


   //
   //t_fTina_x<1>(src,src2,stride,d);
   int i;
   int dx[16];
   int *d; 
   if(!src && dc){
      d=dc;
   }
   else{
      d=&dx[0];
   
   for( i = 0; i < 16; i+=4 )
   {
      
      d[i+0]=src[0];d[i+1]=src[1];
      d[i+2]=src[2];d[i+3]=src[3];

      src+=stride;
//      trR4<1>(&d[i]);

   }

  // for( i = 0; i < 4; i++ )trR4<4>(&d[i]);
   
   }
fdct4x4dc_s(&d[0]);
   
/*
//d[0]+=2;d[0]>>=2;
   static const int sh[]={
      0,1,0,1,
      1,2,1,3,
      0,1,2,2,
      1,3,2,3,
   };
for(i=0;i<16;i++){
   iSad+=s[(d[i]+sh[i])>>sh[i]];
}
*/
//if(d[0]<0)iSad=-d[0];else iSad=d[0];
iSad=0;
if(dc)*dc=(d[0]);
for(i=1;i<16;i++)iSad+=abs(d[i]);
iSad*=3;
iSad>>=4;
     return iSad;// >>3;//((iSad-64)>>2)+16;
}


void dTransWH4(int *p);
void dITransWH_4_add(int *p, unsigned char *dst, int stride);

void t_idct8_s_d(DCT_TYPE *t, unsigned char *dst, int stride)
{
   int c1[16];
   int c2[16];
   int c3[16];
   int c4[16];
   int z=0,i,j;

   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         c1[z]=t[z+0];
         c2[z]=t[z+16];
         c3[z]=t[z+32];
         c4[z]=t[z+48];
         z++;
      }
   }

   //c1[0][0]<<=2;
   //c2[0][0]<<=2;
   //c3[0][0]<<=2;
   //c4[0][0]<<=2;
   /*
   dITransWH_4_add(&c1[0],dst,stride);
   dITransWH_4_add(&c2[0],dst+4*3,stride);
   dITransWH_4_add(&c3[0],dst+stride*4,stride);
   dITransWH_4_add(&c4[0],dst+4*3+stride*4,stride);
*/

}
void t_fdct8_d(DCT_TYPE *t)
{
   int c1[16];
   int c2[16];
   int c3[16];
   int c4[16];
   int i,j;
   int z=0;
   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         c1[z]=t[i*8+j];
         c2[z]=t[i*8+j+4];
         c3[z]=t[(i+4)*8+j];
         c4[z]=t[(i+4)*8+(j+4)];
      }
   }
   z=0;
   /*
   dTransWH4(&c1[0]);
   dTransWH4(&c2[0]);
   dTransWH4(&c3[0]);
   dTransWH4(&c4[0]);
*/
   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         t[z+0 ]=c1[z];
         t[z+16]=c2[z];
         t[z+32]=c3[z];
         t[z+48]=c4[z];
         z++;
      }
   }

}



void t_fdct8(DCT_TYPE *t)
{
   DCT_TYPE c1[4][4];
   DCT_TYPE c2[4][4];
   DCT_TYPE c3[4][4];
   DCT_TYPE c4[4][4];
   int i,j;
   int z=0;
   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         c1[i][j]=t[i*8+j];
         c2[i][j]=t[i*8+j+4];
         c3[i][j]=t[(i+4)*8+j];
         c4[i][j]=t[(i+4)*8+(j+4)];
      }
   }
   dct4x4dc(c1);
   dct4x4dc(c2);
   dct4x4dc(c3);
   dct4x4dc(c4);

   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         t[z+0 ]=c1[i][j];
         t[z+16]=c2[i][j];
         t[z+32]=c3[i][j];
         t[z+48]=c4[i][j];
         z++;
      }
   }

}

void t_fdct4(DCT_TYPE *t)
{
   DCT_TYPE c1[4][4];
   int i,j;
   int z=0;
   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         c1[i][j]=t[z];z++;
      }
   }

   dct4x4dc(c1);

   z=0;
   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         t[z+0 ]=c1[i][j];
         z++;
      }
   }

}
void t_fdct4_i(int *t)
{
   DCT_TYPE c1[4][4];
   int i;
   for(i=0;i<16;i++)c1[0][i]=t[i];
      
   

   idct4x4dc(c1);

   for(i=0;i<16;i++)t[i]=c1[0][i];
}

void t_idct4x(DCT_TYPE *t)
{
   DCT_TYPE c1[4][4];
   int i;
   for(i=0;i<16;i++)c1[0][i]=t[i];
      
   

   idct4x4dc(c1);

   for(i=0;i<16;i++)t[i]=c1[0][i];
}

#undef T_FDIV8
#define T_FDIV8(_V) (_V<0?-((4-_V)>>3):(_V>0?((4+_V)>>3):0))


void t_idct4(DCT_TYPE *t)
{
   DCT_TYPE c1[4][4];
   int i,j;
   int z=0;
   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         c1[i][j]=t[z+0];
         z++;
      }
   }
   dct4x4dc(c1);

   for(i=0;i<4;i++){
      for(j=0;j<4;j++)
      {
         t[i*4+j]=c1[i][j]>>3;
      }
   }

}
void t_idct4_i(int *t)
{
   DCT_TYPE c1[4][4];
   int i;
   for(i=0;i<16;i++)c1[0][i]=t[i];
      
   

   dct4x4dc(c1);

   for(i=0;i<16;i++)t[i]=c1[0][i];
}
//#include<stdio.h>
void t_fhaar01(short *ps)
{
   int x01,x02,x03,x04;
   int r01,r02,r03,r04;
   
   int ofs=1;
   int pos;
#define P(_A,_B) (_A)+8*(_B)
   int x,y,a,b;
   int step=1;
   int p[64];
   //p[0]+=512;
   for(x=0;x<64;x++)p[x]=ps[x]<<7;
   int q=1;
   //int tr=0;

   for(step=2;step<=8;step<<=1,q<<=1){
      ofs=step>>1;
//      show(p);
      for(b=0;b<ofs;b++)for(a=0;a<ofs;a++)
      //a=0;b=0;
      for(y=b;y<8;y+=step)for(x=a;x<8;x+=step){
      //   tr+=4;
      r01=p[P(x,y)];
      r02=p[P(ofs+x,y)];
      r03=p[P(x,ofs+y)];
      r04=p[P(ofs+x,ofs+y)];

      x01 = (+ r01 + r02 + r03 + r04) ;
      x02 = (- r01 + r02 - r03 + r04) ;
      x03 = (- r01 - r02 + r03 + r04) ;
      x04 = (+ r01 - r02 - r03 + r04) ;
#define DIVQ(v) if(q>1)v=(v<0?-((q>>1)-v)/q:(v>0?((q>>1)+v)/q:0))
    //  DIVQ(x04);DIVQ(x02);DIVQ(x03);

     // x01/=2;
#define T_FDIV1(_V) _V
#define T_FDIV2(_V) (_V<0?-((1-_V)>>1):(_V>0?((1+_V)>>1):0))
#define T_FDIV16(_V) (_V<0?-((8-_V)>>4):(_V>0?((8+_V)>>4):0))
#define T_FDIV32(_V) (_V<0?-((16-_V)>>5):(_V>0?((16+_V)>>5):0))
#define T_FDIV64(_V) (_V<0?-((32-_V)>>6):(_V>0?((32+_V)>>6):0))
#define T_FDIV128(_V) (_V<0?-((64-_V)>>7):(_V>0?((64+_V)>>7):0))
#define T_FDIV48(_V) (_V<0?-((24-_V)/48):(_V>0?((24+_V)/48):0))
#define T_FDIV256(_V) (_V<0?-((128-_V)>>8):(_V>0?((128+_V)>>8):0))
#define T_FDIV512(_V) (_V<0?-((256-_V)>>9):(_V>0?((256+_V)>>9):0))
#define T_FDIV1024(_V) (_V<0?-((512-_V)>>10):(_V>0?((512+_V)>>10):0))
#define T_FDIV2048(_V) (_V<0?-((1024-_V)>>11):(_V>0?((1024+_V)>>11):0))

      p[P(x,y)]=T_FDIV1(x01);
      p[P(ofs+x,y)]=T_FDIV1(x02);
      p[P(x,ofs+y)]=T_FDIV1(x03);
      p[P(ofs+x,ofs+y)]=T_FDIV1(x04);//T_FDIV32(x04);
      }
   }
  // printf("tr=%d\n",tr);
   
  // ps[x]=T_FDIV1024(p[x]);
   for(x=0;x<64;x++){
      ps[x]=T_FDIV1024(p[x]);
   }
   ps[0]=T_FDIV4(ps[0]);
}
void t_fhaar0(short *p)
{
   int x01,x02,x03,x04;
   int r01,r02,r03,r04;
   
   int ofs=1;
   int pos;
#define P(_A,_B) (_A)+8*(_B)
   int x,y;
   int step=1;
   //p[0]+=512;
   for(x=0;x<64;x++)p[x]<<=7;
   int q=4;


   for(step=2;step<=8;step<<=1,q>>=1){
      ofs=step>>1;
//      show(p);
      for(y=0;y<8;y+=step)for(x=0;x<8;x+=step){
      r01=p[P(x,y)];
      r02=p[P(ofs+x,y)];
      r03=p[P(x,ofs+y)];
      r04=p[P(ofs+x,ofs+y)];

      x01 = (+ r01 + r02 + r03 + r04) ;
      x02 = (- r01 + r02 - r03 + r04) ;
      x03 = (- r01 - r02 + r03 + r04) ;
      x04 = (+ r01 - r02 - r03 + r04) ;
#define DIVQ(v) if(q>1)v=(v<0?-((q>>1)-v)/q:(v>0?((q>>1)+v)/q:0))
   //   DIVQ(x01);DIVQ(x04);DIVQ(x02);DIVQ(x03);

     // x01/=2;
#ifndef T_FDIV2
#define T_FDIV2(_V) (_V<0?-((1-_V)>>1):(_V>0?((1+_V)>>1):0))
#define T_FDIV4(_V) (_V<0?-((2-_V)>>2):(_V>0?((2+_V)>>2):0))
#define T_FDIV8(_V) (_V<0?-((4-_V)>>3):(_V>0?((4+_V)>>3):0))
#define T_FDIV16(_V) (_V<0?-((8-_V)>>4):(_V>0?((8+_V)>>4):0))
#define T_FDIV32(_V) (_V<0?-((16-_V)>>5):(_V>0?((16+_V)>>5):0))
#define T_FDIV64(_V) (_V<0?-((32-_V)>>6):(_V>0?((32+_V)>>6):0))
#define T_FDIV128(_V) (_V<0?-((64-_V)>>7):(_V>0?((64+_V)>>7):0))
#define T_FDIV48(_V) (_V<0?-((24-_V)/48):(_V>0?((24+_V)/48):0))
#endif

      p[P(x,y)]=T_FDIV4(x01);
      p[P(ofs+x,y)]=T_FDIV8(x02);
      p[P(x,ofs+y)]=T_FDIV8(x03);
      p[P(ofs+x,ofs+y)]=T_FDIV8(x04);//T_FDIV32(x04);
      }
   }
   
   for(x=0;x<64;x++){
      p[x]=T_FDIV32(p[x]);
   }
   
}

void t_fhaar(short *pin)
{
  int x01, x02, x03, x04;
  int r01, r02, r03, r04;
  int i, j, k, mi, mj, qi, qj, p;

  const int w=8;
  const int scales=1;//3;
  int x[8][8];
  for(i=0;i<64;i++)x[0][i]=pin[i]<<6;
  //double **x = c->coeff;
//int a=0;


  for (k = 1; k <= scales; k++) {
    p = 1 << (k - 1);
    mi = w >> k;
    mj = w >> k;

    for (i = 0; i < mi; i++) {
      qi = i << k;

      for (j = 0; j < mj; j++) {
        qj = j << k;
//a++;
        r01 = x[qi][qj];
        r02 = x[qi][qj + p];
        r03 = x[qi + p][qj];
        r04 = x[qi + p][qj + p];

        x01 = (+ r01 + r02 + r03 + r04) ;
        x02 = (- r01 + r02 - r03 + r04) ;
        x03 = (- r01 - r02 + r03 + r04) ;
        x04 = (+ r01 - r02 - r03 + r04) ;

        //x01*=k;x02*=k;x03*=k;x04*=k;

        x01+=2;x02+=2;x03+=2;x04+=2;
        x01>>=2;x02>>=2;x03>>=2;x04>>=2;
        x[qi][qj] = x01;
        x[qi][qj + p] = x02;
        x[qi + p][qj] = x03;
        x[qi + p][qj + p] = x04;
      }
    }
  }
  
  /*
  x[0][0]*=2;

  x[0][4]*=2;
  x[4][0]*=2;
  x[4][4]*=2;


  x[2][4]*=2;
  x[6][4]*=2;

  x[2][0]*=2;
  x[6][0]*=2;

  for(i=0;i<8;i+=2){
     x[i][2]*=2;
     x[i][6]*=2;
  }
  */
  
  //printf("tr=%d\n",a);
   //x[0][0]+=128;pin[0]=x[0][0]>>8;
   for(i=0;i<64;i++){
      x[0][i]+=8;
      pin[i]=x[0][i]>>4;
   }
}
void t_ihaar(short *pin)
{
  int x01, x02, x03, x04;
  int r01, r02, r03, r04;
  int i, j, k, mi, mj, qi, qj, p;
  int x[8][8];
  const int w=8;
  const int scales=1;//3;
  for(i=0;i<64;i++){x[0][i]=pin[i];}
  //x[0][0]*=4;
/*
  x[0][0]=T_FDIV2(x[0][0]);

  x[4][0]=T_FDIV2(x[4][0]);
  x[0][4]=T_FDIV2(x[0][4]);
  x[4][4]=T_FDIV2(x[4][4]);
  */
/*
  x[2][4]=T_FDIV2(x[2][4]);
  x[6][4]=T_FDIV2(x[6][4]);

  x[2][0]=T_FDIV2(x[2][0]);
  x[6][0]=T_FDIV2(x[6][0]);
  for(i=0;i<8;i+=2){
     x[i][2]=T_FDIV2(x[i][2]);
     x[i][6]=T_FDIV2(x[i][6]);
  }
*/


  for (k = scales; k >= 1; k--) {
    p = 1 << (k - 1);
    mi = w >> k;
    mj = w >> k;

    for (i = 0; i < mi; i++) {
      qi = i << k;

      for (j = 0; j < mj; j++) {
        qj = j << k;

        r01 = x[qi][qj];
        r02 = x[qi][qj + p];
        r03 = x[qi + p][qj];
        r04 = x[qi + p][qj + p];

        //r01/=k;r02/=k;r03/=k;r04/=k;

        x01 = r01 - r02 - r03 + r04;
        x02 = r01 + r02 - r03 - r04;
        x03 = r01 - r02 + r03 - r04;
        x04 = r01 + r02 + r03 + r04;

        x[qi][qj] = x01;
        x[qi][qj + p] = x02;
        x[qi + p][qj] = x03;
        x[qi + p][qj + p] = x04;
      }
    }
  }
  for(i=0;i<64;i++){x[0][i]+=2;pin[i]=x[0][i]>>2;}
  //for(i=0;i<64;i++){pin[i]=x[0][i];}


}

void t_ihaar01(short *p)
{
   int x01,x02,x03,x04;
   int r01,r02,r03,r04;
   
   int ofs=1;
//   int pos;
#define P(_A,_B) (_A)+8*(_B)
   int x,y,a,b;
   int step=1;
   int q=4;
   //0101
   //1111
   //0101
   //1111
  // p[0]*=4;
   p[0]*=4;;//p[0]-=4;
   
   for(step=8;step>=2;step>>=1,q>>=1){
      ofs=step>>1;
      
//      show(p);
      for(b=0;b<ofs;b++)for(a=0;a<ofs;a++)
      for(y=b;y<8;y+=step)for(x=a;x<8;x+=step){
      //for(y=0;y<8;y+=step)for(x=0;x<8;x+=step){
      r01=p[P(x,y)];//r01=T_FDIV2(r01);
      r02=p[P(ofs+x,y)];
      r03=p[P(x,ofs+y)];
      r04=p[P(ofs+x,ofs+y)];

        x01 = r01 - r02 - r03 + r04;
        x02 = r01 + r02 - r03 - r04;
        x03 = r01 - r02 + r03 - r04;
        x04 = r01 + r02 + r03 + r04;
        //(_V<0?-((2-_V)>>2):(_V>0?((2+_V)>>2):0))

//      p[P(x,y)]=x01>>2;
  //    p[P(ofs+x,y)]=x02>>2;
    //  p[P(x,ofs+y)]=x03>>2;
     // p[P(ofs+x,ofs+y)]=x04>>2;
      p[P(x,y)]=x01;
      p[P(ofs+x,y)]=x02;
      p[P(x,ofs+y)]=x03;
      p[P(ofs+x,ofs+y)]=x04;
      }
   }
   for(x=0;x<64;x++){
      p[x]=T_FDIV8(p[x]);
     // p[x]*=4;//=T_FDIV2(p[x]);
   }
   
}




void t_xdct(DCT_TYPE *z)
{
//#define G(_A)
   int x01,x02,x03,x04;
   int r01,r02,r03,r04;
   int t[8];

   int i,j=0;
   //for(i=0;i<8;i++)z[i]<<=2;//=T_FDIV8(t[i]);
   
   for(j=0;j<2;j++)
   {
   for(i=0;i<5;i+=4){
      r01=z[0+i];r02=z[1+i];r03=z[3+i];r04=z[4+i];

      x01 = (+ r01 + r02 + r03 + r04) ;
      x02 = (- r01 + r02 - r03 + r04) ;
      x03 = (- r01 - r02 + r03 + r04) ;
      x04 = (+ r01 - r02 - r03 + r04) ;

      t[0+i]=x01;t[1+i]=x02;t[2+i]=x03;t[3+i]=x04;
   }
//   for(i=0;i<8;i++)z[i]=t[i];//T_FDIV4(t[i]);
/*
   z[0]=t[0];
   z[1]=t[4];
   z[2]=t[1];
   z[3]=t[5];
   z[4]=t[2];
   z[5]=t[6];
   z[6]=t[3];
   z[7]=t[7];
   */
   
   z[0]=t[0];
   z[1]=t[4];
   z[2]=t[2];
   z[3]=t[6];
   z[4]=t[1];
   z[5]=t[5];
   z[6]=t[3];
   z[7]=t[7];
   
   }
   
}
/*
#define F(_R,_R1,_A,_B) {_R=(_A+_B +1)>>1;_R1=(_A)-(_B)+128;}
#define I(_R1,_R2,_A,_B){_R1=((_A*2+_B )>>1);_R2=(_R1-_B);}

//#define F(_R,_R1,_A,_B) {_R=(_A+_B );_R1=(_A)-(_B);}
//#define I(_R1,_R2,_A,_B){_R1=((_A+_B )>>1);_R2=(_R1-_B);}
*/

#define F(_A,_B,_C,_D) {_A=_C+_D;_B=_C-_D;}
#define I(_R1,_R2,_A,_B){_R1=((_A+_B )>>1);_R2=(_R1-_B);}
//#define F(_R,_R1,_A,_B) {_R=(_A+_B )>>1;_R1=(_A)-(_B);}
//#define I(_R1,_R2,_A,_B){_R1=((_A*2+_B )>>1);_R2=(_R1-_B);}

void fdwt_r(short *p)
{
   int s1,s2,s3,s4,d1,d2,d3,d4;
#undef P
#define P(_W) p[_W]

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));
   F(s3,d3,P(4),P(5));
   F(s4,d4,P(6),P(7));


   P(0)=s1;P(1)=s2;P(2)=s3;P(3)=s4;
   P(4)=d1;P(5)=d2;P(6)=d3;P(7)=d4;

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));

   P(0)=s1;P(1)=s2;
   P(2)=d1;P(3)=d2;

   F(s1,d1,P(0),P(1));
   P(0)=s1;P(1)=d1;

}
void idwt_r(short *p)
{
   int a1,a2,a3,a4,a5,a6,a7,a8;
//#undef P
//#define P(_W) p[_W]
   I(a1,a2,P(0),P(1));
   P(0)=a1;P(1)=a2;

   I(a1,a2,P(0),P(2));
   I(a3,a4,P(1),P(3));
   P(0)=a1;P(1)=a2;
   P(2)=a3;P(3)=a4;

   I(a1,a2,P(0),P(4));
   I(a3,a4,P(1),P(5));
   I(a5,a6,P(2),P(6));
   I(a7,a8,P(3),P(7));

   P(0)=a1;P(1)=a2;P(2)=a3;P(3)=a4;
   P(4)=a5;P(5)=a6;P(6)=a7;P(7)=a8;


}
/*
void fdwt_c(short *p)
{
   int s1,s2,s3,s4,d1,d2,d3,d4;
#undef P
#define P(_W) p[_W<<3]

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));
   F(s3,d3,P(4),P(5));
   F(s4,d4,P(6),P(7));

   P(0)=s1;P(1)=s2;P(2)=s3;P(3)=s4;
   P(4)=d1;P(5)=d2;P(6)=d3;P(7)=d4;

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));

   P(0)=s1;P(1)=s2;P(2)=d1;P(3)=d2;

   F(s1,d1,P(0),P(1));
   P(0)=s1;P(1)=d1;

}
void idwt_c(short *p)
{
   int a1,a2,a3,a4,a5,a6,a7,a8;
//#undef P
//#define P(_W) p[_W]
   I(a1,a2,P(0),P(1));
   P(0)=a1;P(1)=a2;

   I(a1,a2,P(0),P(2));
   I(a3,a4,P(1),P(3));

   P(0)=a1;P(1)=a2;
   P(2)=a3;P(3)=a4;

   I(a1,a2,P(0),P(4));
   I(a3,a4,P(1),P(5));
   I(a5,a6,P(2),P(6));
   I(a7,a8,P(3),P(7));

   P(0)=a1;P(1)=a2;P(2)=a3;P(3)=a4;
   P(4)=a5;P(5)=a6;P(6)=a7;P(7)=a8;


}


void fdwt(short *p){
   int i;
   for(i=0;i<64;i+=8)fdwt_r(p+i);
   for(i=0;i<8;i++)fdwt_c(p+i);
}
void idwt(short *p){
   int i;
   for(i=0;i<8;i++)idwt_c(p+i);
   for(i=0;i<64;i+=8)idwt_r(p+i);
}

*/

void fdwt_c(int *p)
{
   int s1,s2,s3,s4,d1,d2,d3,d4;
#undef P
#define P(_W) p[_W<<2]

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));

   P(0)=s1;P(1)=s2;
   P(2)=d1;P(3)=d2;

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));

   P(0)=s1;P(1)=s2;
   P(2)=d1;P(3)=d2;
  // F(s1,d1,P(0),P(1));
  // P(0)=s1;P(1)=d1;


   //F(s1,d1,P(0),P(1));
   //P(0)=s1;P(1)=d1;

}
void idwt_c(int *p)
{
   int a1,a2,a3,a4,a5,a6,a7,a8;

 //  I(a1,a2,P(0),P(1));
  // P(0)=a1;P(1)=a2;

   I(a1,a2,P(0),P(2));
   I(a3,a4,P(1),P(3));
   P(0)=a1;P(1)=a2;
   P(2)=a3;P(3)=a4;

   I(a1,a2,P(0),P(2));
   I(a3,a4,P(1),P(3));
   P(0)=a1;P(1)=a2;
   P(2)=a3;P(3)=a4;

   //P(4)=a5;P(5)=a6;P(6)=a7;P(7)=a8;


}
void fdwt_r(int *p)
{
   int s1,s2,s3,s4,d1,d2,d3,d4;
#undef P
#define P(_W) p[_W]

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));

   P(0)=s1;P(1)=s2;
   P(2)=d1;P(3)=d2;

   F(s1,d1,P(0),P(1));
   F(s2,d2,P(2),P(3));

   P(0)=s1;P(1)=s2;
   P(2)=d1;P(3)=d2;


 //  F(s1,d1,P(0),P(1));
//   P(0)=s1;P(1)=d1;


   //F(s1,d1,P(0),P(1));
   //P(0)=s1;P(1)=d1;

}
void idwt_r(int *p)
{
   int a1,a2,a3,a4,a5,a6,a7,a8;
//   I(a1,a2,P(0),P(1));
 //  P(0)=a1;P(1)=a2;

 //  I(a1,a2,P(0),P(1));
  // P(0)=a1;P(1)=a2;

   I(a1,a2,P(0),P(2));
   I(a3,a4,P(1),P(3));
   P(0)=a1;P(1)=a2;
   P(2)=a3;P(3)=a4;

   I(a1,a2,P(0),P(2));
   I(a3,a4,P(1),P(3));
   P(0)=a1;P(1)=a2;
   P(2)=a3;P(3)=a4;

}



void fdwt(int *p){
   int i;
   for(i=0;i<16;i++)p[i]<<=4;

   for(i=0;i<16;i+=4)fdwt_r(p+i);
   for(i=0;i<4;i++)fdwt_c(p+i);
}
void idwt(int *p){
   int i;
  // p[0]*=16;
   for(i=0;i<4;i++)idwt_c(p+i);
   for(i=0;i<16;i+=4)idwt_r(p+i);
}

#define  FAST_FLOAT double
#define DCTSIZE 8
void t_jpeg_fdct_float (FAST_FLOAT * data)
{
  FAST_FLOAT tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7;
  FAST_FLOAT tmp10, tmp11, tmp12, tmp13;
  FAST_FLOAT z1, z2, z3, z4, z5, z11, z13;
  FAST_FLOAT *dataptr;
  int ctr;

  /* Pass 1: process rows. */

  dataptr = data;
  for (ctr = DCTSIZE-1; ctr >= 0; ctr--) {
    tmp0 = dataptr[0] + dataptr[7];
    tmp7 = dataptr[0] - dataptr[7];
    tmp1 = dataptr[1] + dataptr[6];
    tmp6 = dataptr[1] - dataptr[6];
    tmp2 = dataptr[2] + dataptr[5];
    tmp5 = dataptr[2] - dataptr[5];
    tmp3 = dataptr[3] + dataptr[4];
    tmp4 = dataptr[3] - dataptr[4];
    
    /* Even part */
    
    tmp10 = tmp0 + tmp3;	/* phase 2 */
    tmp13 = tmp0 - tmp3;
    tmp11 = tmp1 + tmp2;
    tmp12 = tmp1 - tmp2;
    
    dataptr[0] = tmp10 + tmp11; /* phase 3 */
    dataptr[4] = tmp10 - tmp11;
    
    z1 = (tmp12 + tmp13) * ((FAST_FLOAT) 0.707106781); /* c4 */
    dataptr[2] = tmp13 + z1;	/* phase 5 */
    dataptr[6] = tmp13 - z1;
    
    /* Odd part */

    tmp10 = tmp4 + tmp5;	/* phase 2 */
    tmp11 = tmp5 + tmp6;
    tmp12 = tmp6 + tmp7;

    /* The rotator is modified from fig 4-8 to avoid extra negations. */
    z5 = (tmp10 - tmp12) * ((FAST_FLOAT) 0.382683433); /* c6 */
    z2 = ((FAST_FLOAT) 0.541196100) * tmp10 + z5; /* c2-c6 */
    z4 = ((FAST_FLOAT) 1.306562965) * tmp12 + z5; /* c2+c6 */
    z3 = tmp11 * ((FAST_FLOAT) 0.707106781); /* c4 */

    z11 = tmp7 + z3;		/* phase 5 */
    z13 = tmp7 - z3;

    dataptr[5] = z13 + z2;	/* phase 6 */
    dataptr[3] = z13 - z2;
    dataptr[1] = z11 + z4;
    dataptr[7] = z11 - z4;

    dataptr += DCTSIZE;		/* advance pointer to next row */
  }

  /* Pass 2: process columns. */

  dataptr = data;
  for (ctr = DCTSIZE-1; ctr >= 0; ctr--) {
    tmp0 = dataptr[DCTSIZE*0] + dataptr[DCTSIZE*7];
    tmp7 = dataptr[DCTSIZE*0] - dataptr[DCTSIZE*7];
    tmp1 = dataptr[DCTSIZE*1] + dataptr[DCTSIZE*6];
    tmp6 = dataptr[DCTSIZE*1] - dataptr[DCTSIZE*6];
    tmp2 = dataptr[DCTSIZE*2] + dataptr[DCTSIZE*5];
    tmp5 = dataptr[DCTSIZE*2] - dataptr[DCTSIZE*5];
    tmp3 = dataptr[DCTSIZE*3] + dataptr[DCTSIZE*4];
    tmp4 = dataptr[DCTSIZE*3] - dataptr[DCTSIZE*4];
    
    /* Even part */
    
    tmp10 = tmp0 + tmp3;	/* phase 2 */
    tmp13 = tmp0 - tmp3;
    tmp11 = tmp1 + tmp2;
    tmp12 = tmp1 - tmp2;
    
    dataptr[DCTSIZE*0] = tmp10 + tmp11; /* phase 3 */
    dataptr[DCTSIZE*4] = tmp10 - tmp11;
    
    z1 = (tmp12 + tmp13) * ((FAST_FLOAT) 0.707106781); /* c4 */
    dataptr[DCTSIZE*2] = tmp13 + z1; /* phase 5 */
    dataptr[DCTSIZE*6] = tmp13 - z1;
    
    /* Odd part */

    tmp10 = tmp4 + tmp5;	/* phase 2 */
    tmp11 = tmp5 + tmp6;
    tmp12 = tmp6 + tmp7;

    /* The rotator is modified from fig 4-8 to avoid extra negations. */
    z5 = (tmp10 - tmp12) * ((FAST_FLOAT) 0.382683433); /* c6 */
    z2 = ((FAST_FLOAT) 0.541196100) * tmp10 + z5; /* c2-c6 */
    z4 = ((FAST_FLOAT) 1.306562965) * tmp12 + z5; /* c2+c6 */
    z3 = tmp11 * ((FAST_FLOAT) 0.707106781); /* c4 */

    z11 = tmp7 + z3;		/* phase 5 */
    z13 = tmp7 - z3;

    dataptr[DCTSIZE*5] = z13 + z2; /* phase 6 */
    dataptr[DCTSIZE*3] = z13 - z2;
    dataptr[DCTSIZE*1] = z11 + z4;
    dataptr[DCTSIZE*7] = z11 - z4;

    dataptr++;			/* advance pointer to next column */
  }
}
void t_i_jpeg_fdct_float (short * data)
{
   FAST_FLOAT f[64];
   int i;
   for(i=0;i<64;i++)f[i]=(FAST_FLOAT)data[i];
   t_jpeg_fdct_float(&f[0]);
   for(i=0;i<64;i++){
      data[i]=f[i]/8.0f;// *2.0f;
      //if(data[i]<0)data[i]=-(((-data[i])+4)>>3);
      //else data[i]=(((data[i])+4)>>3);
      

   }


}
#define t__clp(_A) _A

void t_idct_int32(short *const block)
{
}

void t_idct_int32_s(short *const block, register unsigned char *dst,const int stride)
{
}
void idct_int32_dc128(short *const block)
{
}								

void
t_idct_int32_init(void)
{
	int i;
   if(xiclp_0_256)return;
   //limit stack size define
#if  defined(_WIN32_WCE) || defined(__SYMBIAN32__)  || defined(ARM) || defined(ANDROID_NDK) 
      xiclip_0_256=new  short[4096];
      xiclip_0_256x=new unsigned  char[4096];
#endif

//	xiclp = xiclip + 512;
   xiclp_0_256x=&xiclip_0_256x[1024];
#define T_CMI_X 1
#define T_CMA_X 254

   for (i = -1024; i < 1024*3; i++){
      xiclp_0_256x[i]=(i < T_CMI_X) ? T_CMI_X : ((i > T_CMA_X) ? T_CMA_X : (i));
   }
   xiclp_0_256=&xiclip_0_256[2048];
   for (i = -2048; i < 2048; i++){
      xiclp_0_256[i]=(i < -128) ? 0 : ((i > 127) ? 255 : (i+128));
   }
}
