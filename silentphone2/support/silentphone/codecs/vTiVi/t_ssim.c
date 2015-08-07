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

//#include <string.h>

#define T_CAN_TEST_V

#define DIV_SH >>1

#ifndef max
#define max(A,B) ((A)>(B)?(A):(B))
#define min(A,B) ((A)<(B)?(A):(B))
#endif
#if defined(_WIN32 ) && !defined(_WIN32_WCE)

#define INT64 signed __int64
#define INT32 unsigned __int32

typedef union
{       INT64 int64;
        struct {INT32 lo, hi;} int32;
} tsc_counter;

#define RDTSC(cpu_c)   \
{       __asm rdtsc    \
        __asm mov (cpu_c).int32.lo,eax  \
        __asm mov (cpu_c).int32.hi,edx  \
}

#define CPUID(x) \
{ \
    __asm mov eax, x \
    __asm cpuid \
    __asm mov x, eax \
}

signed __int64 rdtsc_works()
{
#ifdef _WIN64
   return 0;
#else

    tsc_counter dummy;
    __try {
	RDTSC(dummy);
    } __except ( 1) {
	return 0;
    }
    return dummy.int64;
#endif 
}
/*
unsigned __int64 inline GetRDTSC() {
   __asm {
      ; Flush the pipeline
      XOR eax, eax
      CPUID
      ; Get RDTSC counter in edx:eax
      RDTSC
   }
}
*/
#endif

/*
#define RDTSC(cpu_c) \
{	asm("rdtsc"); 	\
	asm("mov %%eax, %0" : "=m" ((cpu_c).int32.lo) ); \
	asm("mov %%edx, %0" : "=m" ((cpu_c).int32.hi) ); \
}
*/



static void ssim_4x4x2_core( const unsigned char *pix1, int stride1,
                             const unsigned char *pix2, int stride2,
                             int sums[2][4])
{
    int x, y, z;
    for(z=0; z<2; z++)
    {
        unsigned int s1=0, s2=0, ss=0, s12=0;
        for(y=0; y<4; y++)
            for(x=0; x<12; x+=3)
            {
                int a = pix1[x+y*stride1];
                int b = pix2[x+y*stride2];
                s1  += a;
                s2  += b;
                ss  += a*a;
                ss  += b*b;
                s12 += a*b;
            }
        sums[z][0] = s1;
        sums[z][1] = s2;
        sums[z][2] = ss;
        sums[z][3] = s12;
        pix1 += 4*3;
        pix2 += 4*3;
    }
}

static const int ssim_c1 = (int)(.01*.01*255*255*64 + .5);
static const int ssim_c2 = (int)(.03*.03*255*255*64*63 + .5);

static float ssim_end1( int s1, int s2, int ss, int s12 )
{
    int vars = ss*64 - s1*s1 - s2*s2;
    int covar = s12*64 - s1*s2;
    return (float)(2*s1*s2 + ssim_c1) * (float)(2*covar + ssim_c2)\
           / ((float)(s1*s1 + s2*s2 + ssim_c1) * (float)(vars + ssim_c2));
}

static float ssim_end4( int sum0[5][4], int sum1[5][4], int width, int *d )
{
    int i;
    float ssim = 0.0;
    for( i = 0; i < width; i++ ){
        ssim += ssim_end1( sum0[i][0] + sum0[i+1][0] + sum1[i][0] + sum1[i+1][0],
                           sum0[i][1] + sum0[i+1][1] + sum1[i][1] + sum1[i+1][1],
                           sum0[i][2] + sum0[i+1][2] + sum1[i][2] + sum1[i+1][2],
                           sum0[i][3] + sum0[i+1][3] + sum1[i][3] + sum1[i+1][3] );
        d[0]++;
    }
    return ssim;
}

static float pixel_ssim_wxh(unsigned char *pix1, int stride1,
                           unsigned char *pix2, int stride2,
                           int width, int height )
{
    int d=0;
    int x, y, z;
    float ssim = 0.0;
    //int (*sum0)[4] = (int*)malloc(4 * (width/4+3) * sizeof(int));
    //int (*sum1)[4] = (int*)malloc(4 * (width/4+3) * sizeof(int));
    int ss0[2024];
    int ss1[2024];
    int (*sum0)[4]=&ss0[0];
    int (*sum1)[4]=&ss1[0];;
    width >>= 2;
    height >>= 2;
    z = 0;
    for( y = 1; y < height; y++ )
    {
        for( ; z <= y; z++ )
        {
           #define XCHG(type,a,b) { type t = a; a = b; b = t; }
            XCHG( void*, sum0, sum1 );
            for( x = 0; x < width; x+=2 )
                //pf->
                ssim_4x4x2_core( &pix1[4*(x*3+z*stride1)], stride1, &pix2[4*(x*3+z*stride2)], stride2, &sum0[x] );
        }
        for( x = 0; x < width-1; x += 4 ){
           //d+=2;
            ssim += //pf->
            ssim_end4( sum0+x, sum1+x, min(4,width-x-1),&d );
        }
    }
   // free(sum0);
    //free(sum1);
    
    return ssim/(float)d;
}

static void ssim_axb_core( const unsigned char *pix1, int stride1,
                             const unsigned char *pix2, int stride2,
                             int sums[4], int aa, int bb)
{
   int x, y;
   unsigned int s1=0, s2=0, ss=0, s12=0;
   for(y=0; y<aa; y++)
      for(x=0; x<bb; x+=3)
      {
          int a = pix1[x+y*stride1];
          int b = pix2[x+y*stride2];
          s1  += a;
          s2  += b;
          ss  += a*a;
          ss  += b*b;
          s12 += a*b;
      }
   sums[0] = s1;
   sums[1] = s2;
   sums[2] = ss;
   sums[3] = s12;
}
static float pixel_ssim_x(unsigned char *pix1, int stride1,
                           unsigned char *pix2, int stride2, int iIs16x16)
{

   int sum[4]={0,0,0,0};
   if(iIs16x16)ssim_axb_core(pix1,stride1,pix2,stride2,sum,16,48);
   else ssim_axb_core(pix1,stride1,pix2,stride2,sum,8,24);
   return ssim_end1(sum[0],sum[1],sum[2],sum[3]);
   
}

float get_ssim(unsigned char *cur, unsigned char *ref, int stride, int k){
   if(k){
      unsigned char c[64*3];
      unsigned char r[64*3];
      int i,j,pos=0;
      for(j=0;j<16;j+=2){
         for(i=0;i<48;i+=6){
            int p1=(i+j*stride);
            int p2=p1+3;
            int p3=p1+stride;
            int p4=p3+3;
            c[pos]=(cur[p1]+cur[p2]+cur[p3]+cur[p4]+2)>>2;
            r[pos]=(ref[p1]+ref[p2]+ref[p3]+ref[p4]+2)>>2;
            pos+=3;
         }

      }
      return pixel_ssim_wxh(&c[0],24,&r[0],24,8,8);
   }
   return  pixel_ssim_wxh(cur,stride,ref,stride,16,16);
}
float get_ssim_wh(unsigned char *cur, unsigned char *ref, int stride, int w, int h){
   return pixel_ssim_wxh(cur,stride,ref,stride,w,h);
}
int uncompress (unsigned char *dest, unsigned long *destLen, const unsigned char *source, unsigned long sourceLen){return 0;}
int  compress (char *dest,   unsigned long *destLen,
               const char *source, unsigned long sourceLen){return 0;}
