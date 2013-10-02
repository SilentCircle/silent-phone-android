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


#include <math.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>


#if defined(_WIN32_WCE) || defined(__SYMBIAN32__) || defined(ARM)  || defined(ANDROID_NDK) 
//|| defined(__APPLE__) slow on IPHONE
#define USE_FAST_SIN
#endif

//#define USE_FAST_SIN

#ifdef USE_FAST_SIN
#include "sinX10000.h" // slow on IPHONE
#endif


void tolower(char *p)
{
   while(*p)
   {
      if(isupper(*p))
         (*p)|=0x20;
      p++;
   }
}


#define MY_PI 3.1415f
#define MY_2PI (2*MY_PI)

#ifdef USE_FAST_SIN
class CMySin{
   #define SIN_COUNT 1000
   #define SIN_D_COUNT 100.0f
   #define MY_MAX_S_INT 10000.0f
public:
   CMySin()
   {
#ifndef _SIN_DATA_0_10000_H
      int i;
      float f;
      for(i=0;i<SIN_COUNT;i++)
      {
         f=(float)((float)i/(float)SIN_D_COUNT);
         sin_data[i]=(int)(sin(f)*MY_MAX_S_INT);
      }
#endif
      /*
      FILE *a=fopen("u.uh","wb+");
      fputs("const short *sind_data={\n",a);
      for(i=0;i<SIN_COUNT;i++)
      {
         
         fprintf(a,"%s",i!=0?",":" ");
         fprintf(a," %7d", sin_data[i]);
         if((i%10)==9)
            fprintf(a,"\n   ");
      }
      fputs("\n};",a);
      fclose(a);
      */
   }


   static inline float mySin(float f)
   {
#define SIN_VAR float
#define SIN_T_MAX MY_2PI
      f=normalize2Pi2(f); //f%=MY_2PI;
      //while(f>MY_2PI)f-=MY_2PI;//veelams datus ieedot no 0 liidz 2Pi 
      //while(f<0)f+=MY_2PI;
      f*=SIN_D_COUNT;
      return (float)sin_data[(int)f]/MY_MAX_S_INT; //MY_MAX_S_INT =max(sin_data[n],MY_MAX_S_INT)
   }
   static inline SIN_VAR normalize2Pi2(SIN_VAR f)
   {
      SIN_VAR cur;
      //int i=0;
      cur=SIN_T_MAX;
      SIN_VAR fn;
      int ok=0;
      while(f>SIN_T_MAX)
      {
         if(ok==0)
         {
            fn=cur*2.0f;
            if(f>fn) 
               cur=fn;
            else 
            {
               f-=cur;
               ok=1;
            }
         }
         else
         {
            cur/=2.0f;
            if(f>cur)
               f-=cur;
         }
      }
      cur=-SIN_T_MAX;
      while(f<0)
      {
         if(!ok)
         {
            fn=cur*2.0f;
            if(f<fn) cur=fn;else {f-=cur;ok=1;}
         }
         else
         {
            if(cur<-SIN_T_MAX)
               cur/=2.0f;
            if(f<cur)
               f-=cur;
            else if(f>-SIN_T_MAX)
            {
               f-=cur;
               break;
            }

         }


      }
      return f;
   }
   ~CMySin(){}
#ifndef _SIN_DATA_0_10000_H
private:
   int sin_data[SIN_COUNT+1];
#endif
};

#ifdef _SIN_DATA_0_10000_H
  #define SIN(A) CMySin::mySin(A)
 // #pragma  message("_SIN_DATA_0_10000_H defined")
#else
  CMySin cMSin;
  #define SIN(A) cMSin.mySin(A)
  //#pragma  message("_SIN_DATA_0_10000_H ! defined")
#endif

#else 
//USE_FAST_SIN
  //#pragma  message("SIN")
  #define SIN sin
   
#endif 
float mySinFnc(float f)
{
   return SIN(f);
}

double  mySinFncd(double f)
{
   return (double)SIN((double)f);
}

#if 1
const double dt_001[]={
-2.555555,-2.000000,-1.698970,-1.522879,-1.397940,-1.301030,-1.221849,-1.154902,
-1.096910,-1.045757,-1.000000,-0.958607,-0.920819,-0.886057,-0.853872,-0.823909,
-0.795880,-0.769551,-0.744727,-0.721246,-0.698970,-0.677781,-0.657577,-0.638272,
-0.619789,-0.602060,-0.585027,-0.568636,-0.552842,-0.537602,-0.522879,-0.508638,
-0.494850,-0.481486,-0.468521,-0.455932,-0.443697,-0.431798,-0.420216,-0.408935,
-0.397940,-0.387216,-0.376751,-0.366532,-0.356547,-0.346787,-0.337242,-0.327902,
-0.318759,-0.309804,-0.301030,-0.292430,-0.283997,-0.275724,-0.267606,-0.259637,
-0.251812,-0.244125,-0.236572,-0.229148,-0.221849,-0.214670,-0.207608,-0.200659,
-0.193820,-0.187087,-0.180456,-0.173925,-0.167491,-0.161151,-0.154902,-0.148742,
-0.142668,-0.136677,-0.130768,-0.124939,-0.119186,-0.113509,-0.107905,-0.102373,
-0.096910,-0.091515,-0.086186,-0.080922,-0.075721,-0.070581,-0.065502,-0.060481,
-0.055517,-0.050610,-0.045757,-0.040959,-0.036212,-0.031517,-0.026872,-0.022276,
-0.017729,-0.013228,-0.008774,-0.004365,0.000000,};

const double dt_120[]={
-2.55555,0.000000,0.301030,0.477121,0.602060,0.698970,0.778151,0.845098,
0.903090,0.954243,1.000000,1.041393,1.079181,1.113943,1.146128,1.176091,
1.204120,1.230449,1.255273,1.278754,1.301030,1.322219,1.342423,1.361728,
1.380211,1.397940,1.414973,1.431364,1.447158,1.462398,1.477121,1.491362,
1.505150,1.518514,1.531479,1.544068,1.556303,1.568202,1.579784,1.591065,
1.602060,1.612784,1.623249,1.633468,1.643453,1.653213,1.662758,1.672098,
1.681241,1.690196,1.698970,1.707570,1.716003,1.724276,1.732394,1.740363,
1.748188,1.755875,1.763428,1.770852,1.778151,1.785330,1.792392,1.799341,
1.806180,1.812913,1.819544,1.826075,1.832509,1.838849,1.845098,1.851258,
1.857332,1.863323,1.869232,1.875061,1.880814,1.886491,1.892095,1.897627,
1.903090,1.908485,1.913814,1.919078,1.924279,1.929419,1.934498,1.939519,
1.944483,1.949390,1.954243,1.959041,1.963788,1.968483,1.973128,1.977724,
1.982271,1.986772,1.991226,1.995635,2.000000,2.004321,2.008600,2.012837,
2.017033,2.021189,2.025306,2.029384,2.033424,2.037426,2.041393,2.045323,
2.049218,2.053078,2.056905,2.060698,2.064458,2.068186,2.071882,2.075547,
2.079181,};

inline double t_fast_log10(double d){
 //  d*=1000;
   //return 0;
   int dm;
   if(d<=1.0001){
      if(d<0.1001){
         if(d<0.0101){
            dm=(int)(d*10000);
            return dt_001[dm]-2;
         }
         dm=(int)(d*1000);
         return dt_001[dm]-1;
      }
      dm=(int)(d*100);
      return dt_001[dm];

   }

   if(d<12){
      dm=(int)(d*10);
      return dt_120[dm]-1;
   }



   dm=(int)d;
   if(d<121)return dt_120[dm];

   int r=0;
   while(dm>120){dm/=10;r++;}
   
   return r+dt_120[dm];
}
#define PSNR_MAX 	(481.65f)
int getBlockPSNR(unsigned char *p ,unsigned char *o, int w, int h, int stride, int step){
   double psnr;
   int i,j,d,iDif=0;
   w*=step;
   for(j=0;j<h;j++,p+=stride,o+=stride)
      for(i=0;i<w;i+=step)
      {
         d=p[i]-o[i];d*=d;
         iDif+=d;
      }
   if(iDif==0)return 8000;

   double mse=(double)iDif/(double)(w*h);
   //t_fast_log10
   if ( mse < 0.0001 ) psnr = 800.0;
   else psnr = (PSNR_MAX - 100*t_fast_log10(mse));
   return (int)(psnr*10.f);
}

int getBlockPSNR_UV(unsigned char *p ,unsigned char *o, int w, int h, int stride, int step){
   double psnr;
   int i,j,d,d1,d2,iDif=0;
   const int iCnt=w*h;
   w*=step;

   const int stepuv=step<<1;
   const int struv= stride+step;
   for(j=0;j<h;j+=2,p+=stride*2,o+=stride*2)
      for(i=0;i<w;i+=stepuv)
      {
         d1=p[i+step];d2=o[i+step];
         d1+=p[i+stride];d2+=o[i+stride];
         d1+=p[i+struv];d2+=o[i+struv];
         d1+=p[i];d2+=o[i];
         d1+=2;d2+=2;
         d1>>=2;
         d2>>=2;
         d=d1-d2;
         iDif+=d*d;
      }
   if(iDif==0)return 10000;

   double mse=(double)iDif/(double)(iCnt>>2);
   //t_fast_log10
   if ( mse < 0.0001 ) psnr = 1000.0;
   else psnr = (PSNR_MAX - 100*t_fast_log10(mse));
   return (int)(psnr*10);
}
#define _IGNORE_PSNR_DC
int getBlockPSNR_SQ(unsigned char *p ,unsigned char *o, int w, int h, int stride, int step, int &iDif){
   double psnr;
   int i,j,d;//,d1,d2,d3,d4;
   iDif=0;
   const int iCnt=w*h;
   w*=step;
   int dc1=0;
   int dc2=0;
   for(j=0;j<h;j++,p+=stride,o+=stride)
      for(i=0;i<w;i+=step)
      {
         const int d1=p[i];
         const int d2=o[i];
         d=d1-d2;//p[i]-o[i];
         dc1+=d1;//p[i];
         dc2+=d2;//o[i];
         iDif+=d*d;
      }
   if(iDif==0)return 10000;
#ifndef _IGNORE_PSNR_DC
   iDif-=abs(dc1-dc2)>>2;

   dc1=(dc1+4)>>3;dc2=(dc2+4)>>3;
   //dc1=(dc1+8)>>4;dc2=(dc2+8)>>4;

   //iDif+=((dc1-dc2)*(dc1-dc2)*3)>>2;
   iDif+=((dc1-dc2)*(dc1-dc2));//*3)>>2;
#endif
   double mse=(double)iDif/(double)(iCnt+1);
   //t_fast_log10
   if ( mse < 0.004 ) psnr = 1000.0;
   else psnr = (PSNR_MAX - 100*t_fast_log10(mse));
   return (int)(psnr*10);
}
int getBlock_SQ_4x4(unsigned char *p ,unsigned char *o, int stride){
   int i,j,d;//,d1,d2,d3,d4;

   int iDif=0;

   for(j=0;j<4;j++,p+=stride,o+=stride)
      for(i=0;i<12;i+=3)
      {
         const int d1=p[i];
         const int d2=o[i];
         d=d1-d2;//p[i]-o[i];
         iDif+=d*d;
      }
   return iDif;
}

void getBlock_SQ(unsigned char *p ,unsigned char *o, int stride,  int &iDif){
   
   int i,j,d;//,d1,d2,d3,d4;
   iDif=0;
   
   int dc1=0;
   int dc2=0;
   for(j=0;j<8;j++,p+=stride,o+=stride)
      for(i=0;i<24;i+=3)
      {
         const int d1=p[i];
         const int d2=o[i];
         d=d1-d2;//p[i]-o[i];
         dc1+=d1;//p[i];
         dc2+=d2;//o[i];
         iDif+=d*d;
      }
#ifndef _IGNORE_PSNR_DC
   iDif-=abs(dc1-dc2);

   dc1=(dc1+1)>>1;dc2=(dc2+1)>>1;
   //dc1=(dc1+8)>>4;dc2=(dc2+8)>>4;

   //iDif+=((dc1-dc2)*(dc1-dc2)*3)>>2;
   iDif+=((dc1-dc2)*(dc1-dc2));//*3)>>2;
#endif
   return;
}
static inline void getBlock_SQ_DC(unsigned char *p ,unsigned char *o,  int stride,  int &iDif, int &dc1, int &dc2){
   
   int i,j,d;//,d1,d2,d3,d4;
   iDif=0;

   for(j=0;j<8;j++,p+=stride,o+=stride)
      for(i=0;i<24;i+=3)
      {
         const int d1=p[i];
         const int d2=o[i];
         d=d1-d2;//p[i]-o[i];
         dc1+=d1;//p[i];
         dc2+=d2;//o[i];
         iDif+=d*d;
      }

}
int getBlockPSNR_SQ_F(unsigned char *p ,unsigned char *o, int stride, int step, int &iDif, int &f, const int iLim){
   const int tx[]={0,24,stride*8,24+stride*8};
   int i;
   iDif=0;
   int dc1=0,dc2=0;
   for(i=0;i<4;i++){
      int d=0;
      getBlock_SQ_DC(p+tx[i],o+tx[i],stride,d,dc1,dc2);
//      getBlock_SQ(p+tx[i],o+tx[i],stride,d);
      iDif+=d;
      if(d<iLim)f|=(1<<i);
   }

#ifndef _IGNORE_PSNR_DC
   iDif-=abs(dc1-dc2)>>2;
   dc1=(dc1+4)>>3;dc2=(dc2+4)>>3;
   iDif+=((dc1-dc2)*(dc1-dc2));//*3)>>2;
#endif
if(iDif<2)return 1000;
   double psnr;
   double mse=(double)iDif/(double)(256+1);
   //t_fast_log10
   if ( mse < 0.004 ) psnr = 1000.0;
   else psnr = (PSNR_MAX - 100*t_fast_log10(mse));
   return (int)(psnr*10);
}

int getBlockPSNR_UV_SQ(unsigned char *p ,unsigned char *o, int w, int h, int stride, int step, int &iDif){
   double psnr;
   int i,j,d,d1,d2;
   iDif=0;
   w*=step;
   int dc1=0,dc2=0;


   const int stepuv=step<<1;
   const int struv= stride+step;
   for(j=0;j<h;j+=2,p+=stride*2,o+=stride*2)
      for(i=0;i<w;i+=stepuv)
      {
         d1=p[i+step];d2=o[i+step];
         d1+=p[i+stride];d2+=o[i+stride];
         d1+=p[i+struv];d2+=o[i+struv];
         d1+=p[i];d2+=o[i];
         dc1+=d1;//(d1+2)>>2;
         dc2+=d2;//(d2+2)>>2;
         d=(d1-d2+2)>>2;
         iDif+=d*d;
      }
   if(iDif==0)return 10000;

   //dc1=(dc1+16)>>5;dc2=(dc2+16)>>5; +300 dc cor
   //dc1=(dc1+4)>>3;dc2=(dc2+4)>>3;
  // dc1=(dc1+8)>>4;dc2=(dc2+8)>>4;
#ifndef _IGNORE_PSNR_DC
   iDif-=abs(dc1-dc2)>>4;
   dc1=(dc1+4)>>3;dc2=(dc2+4)>>3;iDif+=(((dc1-dc2)*(dc1-dc2)));
   iDif+=(((dc1-dc2)*(dc1-dc2)+127)>>8);
#endif
   double mse=(double)iDif/(double)(((w*h)>>2)+1);//dc +1
   //t_fast_log10
   if ( mse < 0.01 ) psnr = 1000.0;
   else psnr = (PSNR_MAX - 100*t_fast_log10(mse));
   return (int)(psnr*10.f);
}
int getPSNRFromSQ(int iSq, int cnt){
   double psnr;
   double mse=(double)iSq/(double)(cnt);//dc +1
   //t_fast_log10
   if ( mse < 0.0001 ) psnr = 1000.0;
   else psnr = (PSNR_MAX - 100*t_fast_log10(mse));
   return (int)(psnr*10);
}


#endif


#define CRIPT1(a,b)    ((a)+(b-7)*(b+1))
#define DECRIPT1(a,b)  ((a)-(b-7)*(b+1))

#define CRIPT2(a,b)    ((a)-(b)*(17+(b)))
#define DECRIPT2(a,b)  ((a)+(b)*(17+(b)))
int isPhone(const char * sz,int len)
{
 //  BOOL ret=TRUE;
   int iIsP=0,i;
   for (i=0;i<len;i++)
   {
      switch(sz[i])
      {
      case '(':
      case ')':
         return 1;
      case '@':
      case ':':
      case ';':
         return 0;
      case '.':
         iIsP++;
         if(iIsP>1)
            return 0;
         break;
      }
   }
   return 1;
}

static const unsigned int crc32_tab[] = {
   0x00000000, 0x1db71064, 0x3b6e20c8, 0x26d930ac,
   0x76dc4190, 0x6b6b51f4, 0x4db26158, 0x5005713c,
   0xedb88320, 0xf00f9344, 0xd6d6a3e8, 0xcb61b38c, 
   0x9b64c2b0, 0x86d3d2d4, 0xa00ae278, 0xbdbdf21c
};

/* crc-16 polynomial:
 * x^16 + x^12 + x^5 + 1
 *
 * entries are padded to a word size to make for faster lookups
 */
static const unsigned short crc16_tab[] = {
	0x0000, 0x1081, 0x2102, 0x3183, 0x4204, 0x5285, 0x6306, 0x7387,
	0x8408, 0x9489, 0xa50a, 0xb58b, 0xc60c, 0xd68d, 0xe70e, 0xf78f
};

/* calculates crc32 on buffer 'ptr' of 'cnt' bytes. 
 * crc is the initial crc to use. use ~0U initially, or pass the result of
 *  the previous crc32_calc invocation.
 * returns the crc32 of the buffer plus the crc passed */
unsigned int crc32_calc(const void *ptr, size_t cnt, unsigned int crc)
{
   const unsigned char *p=(unsigned char *)ptr;
   while(cnt--) {
      crc = (crc >> 4) ^ crc32_tab[(crc & 0xf) ^ (*p & 0xf)];
      crc = (crc >> 4) ^ crc32_tab[(crc & 0xf) ^ (*p++ >> 4)];
   }
   
   return crc;
}

unsigned int crc32_calc_video(const void *ptr, size_t cnt)
{
   unsigned int crc=0xEDB88320;
   const unsigned char *p=(unsigned char *)ptr;
   const unsigned char *pEnd=p+cnt-32;
   while(p<pEnd) {
      crc = (crc >> 4) ^ crc32_tab[(crc & 0xf) ^ (*p & 0xf)];
      crc = (crc >> 4) ^ crc32_tab[(crc & 0xf) ^ (*p >> 4)];
      p+=17;
   }
   
   return crc;
}


unsigned int crc32(const void *ptr, size_t cnt){
   return crc32_calc(ptr,cnt,0xEDB88320);
}

void safeStrCpy(char *dst, const char *name, int iMaxSize){
   if(!name){
      if(dst)dst[0]=0;
      return;
   }

   strncpy(dst,name,iMaxSize);
   dst[iMaxSize]=0;
}

int stripDotsIfNumber(char *p, int iLen){
   
   int i;
   for(i=0;i<iLen;i++)if(!isdigit(p[i]) && p[i]!='.')return iLen;
   
   int iNewLen=0;
   for(i=0;i<iLen;i++){
      if(p[i]=='.')continue;
      p[iNewLen]=p[i];
      iNewLen++;
   }
   p[iNewLen]=0;
   return iNewLen;
}


char *trim(char *sz)
{
   //char * ret=sz;
   int i,x=0;//strlen(sz);
   for (i=0;sz[i]!=0;i+=1)
   {
      if (sz[i]=='\t' || sz[i]=='\n' || sz[i]==' ' || sz[i]=='\r')
      {
         x++;
      }
      else break;
   }
   if (x)
   {
      for (i=0;sz[i]!=0;i+=1)
         sz[i]=sz[i+x];
   }
   else
      i=strlen(sz);
   i-=x;
   while(i>0)
   {
      if (sz[i-1]=='\t' || sz[i-1]=='\n' || sz[i-1]==' ' || sz[i-1]=='\r')
         sz[--i]=0;
      else break;
   }
   return sz;
}

char *stripText(char *dst, int iMax, const char *src, const char *cmp){
   
   int i;
   int l_src=strlen(src);
   int l_cmp=strlen(cmp);
   
   if(iMax<=0)return dst;
   char *ret=dst;
   
   if(l_src>iMax)l_src=iMax;
   
   for(i=0;i<l_src;i++){
      if(strncmp(src, cmp,l_cmp)==0){
         src+=l_cmp;
         continue;
      }
      *dst=*src;
      src++;
      dst++;
   }
   *dst=0;
   return ret;
}


void convert16to8(char *dst, const short *src, int iLen)
{
   if(iLen)
   {
      while(iLen>0)
      {
         *dst=(char)*src;
         src++;
         dst++;
         iLen--;
      }
   }
   else
   {
      while(*src)
      {
         *dst=(char)*src;
         src++;
         dst++;
      }
   }
   *dst=0;
}

void convert16to8S(char *dst, int iMaxDstSize, const short *src, int iLen){
   
   if(iLen)
   {
      while(iLen>0 && iMaxDstSize>0)
      {
         *dst=(char)*src;
         src++;
         dst++;
         iLen--;
         iMaxDstSize--;
      }
   }
   else
   {
      while(*src && iMaxDstSize>0)
      {
         *dst=(char)*src;
         src++;
         dst++;
         iMaxDstSize--;
      }
   }
   *dst=0;
}

void convert16_8(short *src, int iLen)
{
   char *dst=(char*)src;
   if(iLen)
   {
      while(iLen>0)
      {
         *dst=(char)*src;
         src++;
         dst++;
         iLen--;
       }
   }
   else
   {
      while(*src)
      {
         *dst=(char)*src;
         src++;
         dst++;
      }
   }
   *dst=0;
}
void convert8_16(char *c, int iLen)
{
   short *p=(short *)c;
   while(iLen>=0)
   {
      p[iLen]=c[iLen];
      iLen--;
   }

}
void convert8_16(unsigned char *c, unsigned  short *s, int iLen ,int fZeroTerminate)
{

   if(iLen)
   {
      while(*c && iLen>0)
      {
         *s=*c;
         c++;
         s++;
         iLen--;
      }
   }
   else
   {
      while(*c)
      {
         *s=*c;
         c++;
         s++;
      }

   }
   if(fZeroTerminate)
      *s=0;
}

const char *intToIPStr(unsigned int ip, char *ipStr, int iMaxIPStrSize)
{
  // static char buf[16];
   if(ipStr==NULL || iMaxIPStrSize<16)return "0.0.0.0";//TODO panic
      //ipStr=(char *)&buf;
   unsigned int a1=((unsigned char *)&ip)[0];
   unsigned int a2=((unsigned char *)&ip)[1];
   unsigned int a3=((unsigned char *)&ip)[2];
   unsigned int a4=((unsigned char *)&ip)[3];

   sprintf(ipStr, "%u.%u.%u.%u",a1,a2,a3,a4);
   return ipStr;
}


void bin2Hex(unsigned char *Bin, char * Hex ,int iBinLen)
{
    unsigned short i;
    unsigned char j;
    
    const static char h[]="0123456789abcdef";

    for (i = 0; i < iBinLen; i++) {
#if 1
       j = (Bin[i] >> 4) & 0xf;
       Hex[i*2] = h[j];
       
       j = Bin[i] & 0xf;
       Hex[i*2+1] = h[j];
       
#else
        j = (Bin[i] >> 4) & 0xf;
        if (j <= 9)
           Hex[i*2] = (j + '0');
         else
            Hex[i*2] = (j + 'a' - 10);
        j = Bin[i] & 0xf;
        if (j <= 9)
            Hex[i*2+1] = (j + '0');
         else
            Hex[i*2+1] = (j + 'a' - 10);
#endif
    };
    Hex[iBinLen*2] = '\0';
};


int hex2BinL(unsigned char *Bin, char *Hex, int iLen)
{
   int i=0;
   //char * tmp=Hex;
   if (iLen==0 || (iLen&1)) return -1;
   iLen>>=1;
   
   unsigned int res;
   
   for (i=0;i<iLen;i++)
   {
      int v1=Hex[0];
      int v2=Hex[1];
      if(!isxdigit(v1) || !isxdigit(v2)){memset(Bin,0,iLen);return -2;}
      
      res=0;
      if(isdigit(v1))res=v1-'0';
      else res=toupper(v1)-'A'+10;
      
      res<<=4;
      
      if(isdigit(v2))res+=(v2-'0');
      else res+=(toupper(v2)-'A'+10);
      
      //   sscanf(Hex, "%02x", &res);
      Bin[i]=res;
      Hex+=2;
   }
  return 0;
}
int hex2Bin(unsigned char *Bin, char * Hex)
{
   int iLen;
   //char * tmp=Hex;
   iLen=strlen(Hex);
   return hex2BinL(Bin,Hex,iLen);
}  


#define CRIPT1(a,b)    ((a)+(b-7)*(b+1))
#define DECRIPT1(a,b)  ((a)-(b-7)*(b+1))

#define CRIPT2(a,b)    ((a)-(b)*(17+(b)))
#define DECRIPT2(a,b)  ((a)+(b)*(17+(b)))

void  encryptPwd(const char *szPwd, char *szDst, int iMaxLen)
{
   //memcpy(szDst,szPwd,strlen(szPwd)+1);
   int i,iLen=strlen(szPwd);
   unsigned  char tmp[256];

   iMaxLen-=2;
   int iDestLen=2*iLen;//TODO TEST LEN
   if(iDestLen>iMaxLen)iDestLen=iMaxLen;
   for (i=0;i<iDestLen;i+=2)
   {
      if ((i/2)&1)
         tmp[i]=CRIPT1(szPwd[i/2],i);
      else
         tmp[i]=CRIPT2(szPwd[i/2],i);
      tmp[i+1]=tmp[i]+(szPwd[i/2]*(iDestLen+3-i));
   }

   bin2Hex((unsigned char*)&tmp,szDst,iDestLen);
}

int decodePwd(char *szSrc,char *szPwd, int iMaxLen)
{
   //memcpy(szDst,szPwd,strlen(szPwd)+1);
   int i,iLen=strlen(szSrc);
   unsigned char tmp[256];
   int iPwdLen;
   szPwd[0]=0;
   if ((iLen%4)!=0 || iLen>=sizeof(tmp)*2-1)return -1;
   
   
   iPwdLen=iLen/4;
   
   iMaxLen--;
   
   if(iPwdLen>=iMaxLen)return -1;
   
   hex2Bin((unsigned char *)&tmp,szSrc);

   iMaxLen-=2;
   
   for (i=0;i<iLen/2;i+=2)
   {
      if ((i/2)&1)
         szPwd[i/2]=DECRIPT1(tmp[i],i);
      else
         szPwd[i/2]=DECRIPT2(tmp[i],i);
   }
   szPwd[iPwdLen]=0;
   return 0;
}
#define MAX_IP_LEN 15
#define MIN_IP_LEN 7

unsigned int ipstr2long(int iLen,char *p)//TODO (char *p, int iLen=0)
{
	char	buf[6];
	char	bufIp[MAX_IP_LEN+1];
   char * ip_str;
	char	*ptr;
	int	i;
	int	count;
	unsigned int	ipaddr;
	int	cur_byte;

   if (iLen==0)
      iLen=strlen(p);
   if (iLen<MIN_IP_LEN || iLen>MAX_IP_LEN) return 0;
   memcpy(bufIp,p,iLen);
   bufIp[iLen]=0;
   trim(bufIp);
   //isdigit()
   ip_str=bufIp;

	ipaddr = (unsigned int)0;
	for(i = 0;i < 4;i++) {
		ptr = buf;
		count = 0;
		*ptr = '\0';
		while(*ip_str != '.' && *ip_str != '\0' && count < 4) {
			if(!isdigit(*ip_str)) {
				return((unsigned int)0);
			}
			*ptr++ = *ip_str++;
			count++;
		}
		if(count >= 4 || count == 0) 
      {
			return((unsigned int)0);
		}
		*ptr = '\0';
		cur_byte = atoi(buf);
		if(cur_byte < 0 || cur_byte > 255) {
			return((unsigned int)0);
		}
		ip_str++;
		ipaddr = ipaddr << 8 | (unsigned int)cur_byte;
	}
	return(ipaddr);
}
int isNatIP(unsigned int ip)
{
      unsigned char ch1=((unsigned char *)&ip)[0];
      unsigned char ch2=((unsigned char *)&ip)[1];

      return  
         ((ch1==192 && ch2==168) ||
         (ch1==172 && ch2>=16 && ch2<=31) ||
          ch1==10 
         );
}
unsigned int reverseIP(unsigned int ip)
{
   unsigned int uiIP=0;
   ((char *)&uiIP)[0]=((char *)&ip)[3];
   ((char *)&uiIP)[1]=((char *)&ip)[2];
   ((char *)&uiIP)[2]=((char *)&ip)[1];
   ((char *)&uiIP)[3]=((char *)&ip)[0];
   return uiIP;
}

unsigned int strToUint(char* p)
{
   unsigned int res=0;
   int i=11;
   if (p==NULL) return res;
   
   while (isspace(*p)) p++;

   while (i--) 
   {
      if (!isdigit(*p)) 
         return res;
      res*=10;
      res+=(unsigned int)*p-0x30 ;
      p++;
   }
   return res;
}

unsigned int strToUint(unsigned short *p)
{
   unsigned int res=0;
   int i=11;
   if (p==NULL) return res;
   
   while (*p==32 || *p=='\t') p++;

   while (i--) 
   {
      if (*p<0x30 || *p>0x39) 
         return res;
      res*=10;
      res+=(unsigned int)*p-0x30 ;
      p++;
   }
   return res;
}



int createDtmf(int iTone,int iVol,char * data,unsigned int uiDataLen,bool bEnding,int iFreq)
{
      unsigned int i;
      float f1u,f2u;
      float fVol=1;
      float f1=0,f2=0;
      int iLimit=iFreq/50;//20 ms

      switch(iTone)
      {
      case 0:  f2u=937;f1u=1333;break;
      case 1:  f2u=692;f1u=1206;break;
      case 2:  f2u=692;f1u=1333;break;
      case 3:  f2u=692;f1u=1474;break;
      case 4:  f2u=768;f1u=1206;break;
      case 5:  f2u=768;f1u=1333;break;
      case 6:  f2u=768;f1u=1474;break;
      case 7:  f2u=851;f1u=1206;break;
      case 8:  f2u=851;f1u=1333;break;
      case 9:  f2u=851;f1u=1474;break;
      case 10: f2u=937;f1u=1206;break;
      case 11: f2u=937;f1u=1474;break;
      default:
         return -1;
      }
         
      for (i=0;i<uiDataLen;i+=2)
      {  
         f2=f2+MY_2PI/(float)iFreq*f2u;
         f1=f1+MY_2PI/(float)iFreq*f1u;
         if ((int)i<iLimit)
         {
            if (fVol<(float)iVol)
               fVol=fVol*1.04f;
         }
         else
         if (bEnding && (int)(uiDataLen-i)<iLimit)
         {
            fVol=fVol/1.04f;
         }
         else
            fVol=(float)iVol;
         //data[i]=0;
         //data[i+1]=(char)((sin(f2)+sin(f1))*fVol);
         if(f2>MY_2PI)f2-=MY_2PI;
         if(f1>MY_2PI)f1-=MY_2PI;
         *(short *)(data+i)=(short)((SIN(f2)+SIN(f1))*fVol*256);
      }
      return 0;
}

int isEqual(char * src, char * dst, int iLen)
{
   int t_isEqual(const char *src, const char *dst,int iLen);
   return t_isEqual(src, dst, iLen);
}

int isEqualCase(const char * src, const char * dst, int iLen)
{
   int ret=1;
   while(iLen>0)
   {
      ret=tolower(*src)==tolower(*dst);
      if(ret)
      {
         src++;
         dst++;
         iLen--;
      }
      else return ret;
   }
   return ret;
}

int fillInts(char *p, int *i, int iMaxInts){
   int cnt=0;
   while(p[0]){
       while(p[0]<'0' || p[0]>'9')p++;
       if(!p[0])break;
       i[cnt]=atoi(&p[0]);
       cnt++;
       if(cnt+1>=iMaxInts)break;
       while(p[0]>='0' && p[0]<='9')p++;

   }
   return cnt;

}

int hasThisInt(int *il, int iCnt, int id){
   int i;
   for(i=0;i<iCnt;i++){if(id==il[i])return 1;}
   return 0;
}

int containChar(char * sz,char * szChars)
{
   int iLen,iCLen,i,j;
   int iPos=-1;
   if(szChars==NULL || sz==NULL)return 1;
   iLen=strlen(sz);
   iCLen=strlen(szChars);

   if(iLen==0 || iCLen==0)return iPos;
   for(i=0;i<iLen && iPos!=1 ;i++)
   {
      for(j=0;j<iCLen  && iPos!=1;j++)
      {
         if (sz[i]==szChars[j])
            iPos=i;
      }
   }

   return iPos;//TODO return char pos
}

void setBitF(unsigned char *p, int iBit, int &iPos)
{
   unsigned char *c;
   int bd8=iPos>>3;
   c=&p[bd8];

   if(iBit)
      *c|=(1<<(7-(iPos-(bd8<<3))));
   else
   {
      *c&=(~(1<<(7-(iPos-(bd8<<3)))));
   }
   iPos++;
}

void addBit(unsigned char *p, int iBit, int &iPos)
{

   if(iBit){
      p[iPos>>3]|=(128>>(iPos&7));
        //p[iPos>>3] <<= 1;
        //p[iPos>>3] |= 1;
   }
   else
   {
     p[iPos>>3]&=~(128>>(iPos&7));
   }
   iPos++;
   /*
        p[iPos>>3] <<= 1;
        p[iPos>>3] |= iBit;
        iPos++;
*/
}

  // #include <intrin.h>

int zgetBit(register unsigned char *p, register int iPos)
{
   //register const int c=p[iPos>>3];
   int c=(p[iPos>>3]&(128>>(iPos&7)));
   return !!c;
 //  return !!_bittest(( long*)&p[iPos>>3],(iPos&7)+24);

}
int getBit(register unsigned char *p, register int iPos)
{
   //register const int c=p[iPos>>3];
 //  unsigned int b;
   //p+=(iPos>>3);
  // b=p[1]|(p[0]<<8);//|(p[1]<<16)|(p[0]<<24);
   int c=p[iPos>>3]>>(7-(iPos&7));;
   
   //int c=(p[iPos>>3]&(128>>(iPos&7)));
   return c&1;
 //  return !!_bittest(( long*)&p[iPos>>3],(iPos&7)+24);

}
int uugetBit(register unsigned char *p, register int iPos)
{
   //register const int c=p[iPos>>3];
 //  unsigned int b;
  // b=p[1]|(p[0]<<8);//|(p[1]<<16)|(p[0]<<24);
   unsigned int c=(255&((unsigned int)p[iPos>>3]<<(iPos&7)))>>7;
   
   //int c=(p[iPos>>3]&(128>>(iPos&7)));
   return c;
 //  return !!_bittest(( long*)&p[iPos>>3],(iPos&7)+24);

}

int getZeroBits(register unsigned char *p, register int iPos){

   int iPosIn=iPos;
   while(!(p[iPos>>3]&(128>>(iPos&7)))){
      iPos++;
   }
   return iPos-iPosIn;
}
int getNBits(register unsigned char *p, register int iPos, int iBits)
{
#if 0
   register int c=0;
   int iLast=iPos+iBits;
   while(iPos<iLast){
      c<<=1;
     // register const int b=p[iPos>>3];
      if((p[iPos>>3]&(128>>(iPos&7))))c|=1;
      iPos++;

   }
   
   return c;
#else
   register unsigned char c;
   int res=0;
   /*
   int iBitsLeft=8-(iPos&7);
   c=p[iPos>>3];
   c<<=(iPos&7);
   c>>=(iPos&7);
   iCnt|=c;
   iPos>>=3;
   iBits-=iBitsLeft;
   */
   int iBitsShift=(iPos&7);
   iPos>>=3;
   c=p[iPos];
   c<<=(iBitsShift);
   c>>=(iBitsShift);
   res|=c;
   iBits-=(8-iBitsShift);
   if(iBits<0){
      res>>=(-iBits);
      return res;
   }
   while(iBits>0){
      iPos++;
      res<<=8;
      res|=p[iPos];
      if(iBits<8){
         res>>=(8-iBits);
         break;
      }
      iBits-=8;
   }

   return res;
#endif
}


int isValidSz(char *sz,char c,char *szValidChars)
{
   int  j,i,iLen=0,iVCLen=strlen(szValidChars);
   int bValidFound=0;
   iLen=strlen(sz);

   if (iLen==0)return bValidFound;


   for (i=0;i<iLen; i+=1)
   {
      bValidFound=0;
      for (j=0;j<iVCLen;j+=1)
      {
         if (sz[i]==szValidChars[j] || c==sz[i])
         {
            bValidFound=1;
            break;
         }
      }
      if (bValidFound==0)return 0;
   }
   return 1;
}
int parseColor(char *buf, int iLen = 0)
{
   int iR=0;
   int iG=0;
   int iB=0;
   int i;
   int id=1;
   if(iLen==0)
      iLen=strlen(buf);

   iR=atoi(buf);
   
   for(i=0;i<iLen;i++)
   {
      if(i+1!=iLen && (buf[i]<'0' || buf[i]>'9') && (buf[i+1]>='0' && buf[i+1]<='9'))
      {
         if(id==1)
         {
            id++;
            iG=atoi(buf+i+1);
         }
         else
         {
            iB=atoi(buf+i+1);
         }
      }
   }

   return iR+iG*256+iB*256*256;
}
//#include <windows.h>
//#include  "cdtmacros.h"
#define CMP_4(_sp,_s) ((*(unsigned int*)(_sp))| 0x20202020)== ((*(unsigned int*)(_s))|0x20202020)

int t_isEqual_case(const char *src, const char *dst,int iLen)
{

   if(((((long long)src)|((long long)dst))&3)==0){
      int r=0;
      while(iLen>3){
         r=CMP_4(src,dst);
         if(!r)return 0;
         src+=4;
         dst+=4;
         iLen-=4;
      }
   }
   else {
      if((src[iLen>>1]|0x20)!=(dst[iLen>>1]|0x20))return 0;
      if((src[iLen-1]|0x20)!=(dst[iLen-1]|0x20))return 0;
      if((src[0]|0x20)!=(dst[0]|0x20))return 0;
      src++;
      dst++;
      iLen--;if(iLen>0)iLen--;
   }

   while(iLen>0)
   {
      if(((*src)|0x20)!=((*dst)|0x20))
         return 0;
      src++;
      dst++;
      iLen--;
   }
   return 1;
}

int t_isEqual(const char *src, const char *dst, int iLen)
{
   
#define CMP1_C(sp,s) ((*(unsigned int*)(sp))& 0x000000ff)==((*(unsigned int*)(s))& 0x000000ff)
#define CMP2_C(sp,s) ((*(unsigned int*)(sp))& 0x0000ffff)==((*(unsigned int*)(s))& 0x0000ffff)
#define CMP3_C(sp,s) ((*(unsigned int*)(sp))& 0x00ffffff)==((*(unsigned int*)(s))& 0x00ffffff)
#define CMP4_C(sp,s) (*(unsigned int*)(sp))== (*(unsigned int*)(s))
   
   if(((((long long)src)|((long long)dst))&3)==0){
      int r=0;
      while(iLen>3){
         r=CMP4_C(src,dst);
         if(!r)return 0;
         src+=4;
         dst+=4;
         iLen-=4;
      }
      r=1;
      switch(iLen){
         case 3:r=CMP3_C(src,dst);break;
         case 2:r=CMP2_C(src,dst);break;
         case 1:r=CMP1_C(src,dst);break;
      }
      return r;
   }
   if((src[iLen>>1])!=dst[iLen>>1])return 0;
   if((src[iLen-1])!=dst[iLen-1])return 0;
   if((src[0])!=dst[0])return 0;
   
   src++;
   dst++;
   if(iLen>0)iLen--;iLen--;
   // Sleep(40);
   while(iLen>0)
   {
      if((*src)!=(*dst))
         return 0;
      src++;
      dst++;
      iLen--;
   }
   return 1;
}


int cmpmyUnicode(short *src,short *dst,int iShorts)
{
   while(iShorts>0)
   {
      if(((*src)&0xdf)!=((*dst)&0xdf))
         return 0;
      src++;
      dst++;
      iShorts--;
   }
   return 1;
}

void incVol(int iVol,int iMax, char * data, int iBufSize)
{
  if(iMax==0 || iVol==0 ||iVol==iMax)return ;

  float coef=(float)iVol/(float)iMax;
  float f;
 // int i;
  int iLen=iBufSize/2;
  short *p=(short *)data;
  
  coef=1.0f/coef;

  coef*=0.90f;//lietot 90% no max

  while(iLen>0)
  {
     f=(float)*p;
     f*=coef;
     *p=(short)f;
     p++;
     iLen--;
  }
}



void createToneMix(int iBitRate, int iTone,int iVol,char * data,unsigned int uiDataLen,bool bEnding)
{

      unsigned int i;
      float f1u;
      float fVol=1;
      float f1=0,tmp;//,tmp2;

      f1u=(440.0f/32.0f)*(float)pow(2.0f,((float)iTone-9.0f)/12.0f)*3.1415926f*2.0f/(float)iBitRate;
 
      for (i=0;i<uiDataLen;i+=2)
      {  
         f1=f1+f1u;
         if (i<320/4)
         {
            if (fVol<(float)iVol)
               fVol=fVol*1.1f;
         }
         else
         if (bEnding && uiDataLen-i<5000)
         {
            fVol=fVol/1.002f;
         }
         else
            fVol=(float)iVol;

         tmp=(float)(*(short *)(data+i));

         if(f1>MY_2PI)f1-=MY_2PI;
         tmp+=((SIN(f1))*fVol*256);
         
         //if(tmp=tmp2>)

         if(tmp>0x7fff)
            tmp=0x7fff;
         else if(tmp<-32766)
            tmp=-32766;

        *(short *)(data+i)=(short)tmp;
      }
}

int genereteRecTone(char* data,int iMaxLen,char * ds, int iBitRate)
{
     int i,delta;      

     bool bNew=true;
     bool bOctChanege=true;
     int iTone=-1,iOct=4+2,iVol=40;
     int iTakts=120;
     int iOffset=0;
     int iLen=0;
     iLen=strlen(ds);
     if (iLen==0)return 0;
     delta=iBitRate*2*60/iTakts;

     i=0;
     if (ds[i]=='*')
     {
        int j;
        j=strtoul(&ds[i+1],NULL,0);
        if (j>39 && j<201)
        {
           iTakts=j;
        }
        i++;
        while(ds[i]!='*')
        {
           if (ds[i]==0)return 0;
           i++;
        }
        i++;
     }
     //mlD:4C(2)[70],F(1),C(2),D(4),E(1),3A,A,4D,C,P
     for(;i<iLen;i++)
     {
        switch(ds[i])
        {
           case 'C':case 'D':case 'E':case 'F':case 'G': case 'A': case 'B':
             if (i==0) 
                iOct=4+1;
              i--;
              bOctChanege=false;

           case '1':case '2':case '3':case '4':case '5': case '6': case '7':case '8':case '9':

              if (bNew)
              {
                 if (bOctChanege)
                     iOct=((unsigned int)ds[i]-0x30)+1;
                 bOctChanege=true;
                 switch(ds[i+1])
                 {
                 case 'C':iTone=iOct*12+0; break;
                 case 'D':iTone=iOct*12+2; break;
                 case 'E':iTone=iOct*12+4; break;
                 case 'F':iTone=iOct*12+5; break;
                 case 'G':iTone=iOct*12+7; break;
                 case 'A':iTone=iOct*12+9; break;
                 case 'B':iTone=iOct*12+11; break;
                 default:return 0;
                 }
                 i+=2;
                 if (ds[i]=='b')
                 {
                    iTone--;
                    i++;
                 }
                 if (ds[i]=='(')
                 {
                    
                    int j=0;
                    j=strtoul(&ds[i+1],NULL,0);
                    if (j==0)return 0;
                    i++;
                    delta=iBitRate*2*60*4/iTakts/j;
                    delta+=(delta&1)?1:0;
                    while(ds[i]!=',' && ds[i]!='[')
                    {
                       if (ds[i]==0)break;
                       i++;
                    }
                 }
                 if (ds[i]=='[')
                 {
                    int j=0;
                    j=strtoul(&ds[i+1],NULL,0);
                    i++;
                    if (j>0 ||j<128)
                    {
                      iVol=j;
                    }
                    while(ds[i]!=',' && ds[i]!='(')
                    {
                       if (ds[i]==0)break;
                       i++;
                    }
                 }
                 if (delta+iOffset+5000>=iMaxLen)return 0;
                 createToneMix(iBitRate, iTone,iVol,(char*)data+iOffset,delta+5000,true);
                 iOffset+=delta;
                 bNew=true;
                 iTone=-1;
              }
              break;
            case 'P':
               {
               int prevDelta=delta;
               if (ds[i+1]=='(')
               {
                  int j=0;
                  j=strtoul(&ds[i+2],NULL,0);
                  if (j==0)return 0;
                  i++;
                  delta=iBitRate*2*60*4/iTakts/j;
                  delta+=(delta&1)?1:0;
               }
               else
                  delta=iBitRate*2*60/iTakts/4;
               while(ds[i]!=',')
               {
                 if (ds[i]==0)
                    break;
                 i++;
               }
               if (delta+iOffset+5000>=iMaxLen)return 0;
               memset((char*)data+iOffset+5000,0,delta);
               iOffset+=delta;
               delta=prevDelta;
               }
               break;
           default :
              return 0;
        }
     }
     /*
   FILE *f=fopen("r.raw","wb");
   fwrite(data,1,iMaxLen,f);
   fclose(f);
   */
     return iOffset+5000;

}

char * findImgData(char * fn,int &iSize, char *img_data, int iAllSize)
{
   int iLen=strlen(fn);
   char *p=NULL;
   char * tmp=img_data;
   int iSizeOfObj,x=0;
   iSize=0;
   while(1)
   {
    
      memcpy(&iSizeOfObj,tmp,sizeof(int));
      
    //  char dbg[256];
     // snprintf(dbg,sizeof(dbg),"len=%d fn=[%.*s]",iSizeOfObj-iLen-5, tmp[4], tmp+5);
     // tmp_log(dbg);
      
      if(iSizeOfObj<5+1)return NULL;//err

      
      if(iLen==tmp[4] && isEqual(fn,tmp+5,iLen))
      {
         p=tmp+5+iLen;
         iSize=iSizeOfObj-iLen-5;
         return p;
      }
      tmp+=iSizeOfObj;
      x+=iSizeOfObj;
      if(x+iLen+5>=iAllSize)
         break;
   }
   return NULL;
}
 

void genereteTone(float iFreq,float iFreq2,int iBitRate, int iVol, int iPlay,int iWait,char * data, int iBufSize)
{
   int i,j;
   float fVol=2.0f;//(float)iVol;
   float f1=0.0f,f2=0.0f;
   float fUp;//=MY_2PI/(float)iBitRate*iFreq;
   float fUp2;//=MY_2PI/(float)iBitRate*iFreq2;
   int tmp;
   if(iVol>150)iVol=150; 
   float  fTmpVol=iVol*128;


   int iMaxVol=0;
   int iVolCnt=iBitRate/2/40;//25 ms inc to max
   if(iVolCnt>iPlay)
      iVolCnt=iPlay/3;

   if (iWait==0)iPlay=iBufSize;

   

   for (j=0;j<iBufSize;j+=(iWait+iPlay))
   {
      fVol=2.0f;
      fUp=MY_2PI*iFreq/(float)iBitRate;
      fUp2=MY_2PI*iFreq2/(float)iBitRate;
      f1=0.0f;f2=MY_PI;

      for (i=0;(int)i<iPlay;i+=2)
      {  
         if (i<iVolCnt)
         {fVol=fVol*1.1f;}
         else if ((int)i>iPlay-iVolCnt)
         {fVol=fVol/1.1f;}
         else
            fVol=fTmpVol;

         if (fVol>fTmpVol)
            fVol=fTmpVol;

         f1=f1+fUp;
         f2=f2+fUp2;
         if (j+i+1>=iBufSize)break;

         if(f1>MY_2PI)f1-=MY_2PI;
         if(f2>MY_2PI)f2-=MY_2PI;

         if(iFreq!=iFreq2)
            //tmp=(int)((sin(f1)*fVol*256)+(sin(f2)*fVol*256))/2;
            tmp=(int)((SIN(f1)+SIN(f2))*fVol);//*128.0f);
         else
            tmp=(int)(SIN(f1)*fVol);//*256);inc vol visu izlabo




         if(tmp>32766)
            tmp=32766;
         else if(tmp<-32766)
            tmp=-32766;

         *(short *)(data+j+i)=(short)tmp;

         if(tmp<0)tmp=-tmp;//abs
         iMaxVol=iMaxVol>tmp?iMaxVol:tmp;
      }
   }

   //incVol(iMaxVol,0x7fff,data,iBufSize);
   //
   //aatra 1% no augshas
   //incVol(iMaxVol,iVol*256,data,iBufSize);
   /*
   FILE *f=fopen("t.raw","wb");
   fwrite(data,1,iBufSize,f);
   fclose(f);
   */
}
//const static int i=131235;
#ifdef __SYMBIAN32__

#include <f32file.h>
#define _wfopen wfopen

#include "../os/CTThread.h"
class CTTCancel{
   const RThread &th;
   TRequestStatus *st;
   int iAfter;
   int iStop;
  // CTThread tth;
public:
   CTTCancel( TRequestStatus *st, int iAfter):th(RThread()),st(st),iAfter(iAfter){iStop=0;} 
   static int thFCancel(void *p){return ((CTTCancel*)p)->cancelfnc();}
   int start()
   {
      CTThread *prevth=new CTThread();
      prevth->create(&CTTCancel::thFCancel,this);
      prevth->destroyAfterExit();
      return 0;
   }
   void cancel(){iStop=1;User::After(20000);}
   int cancelfnc()
   {
      while(!iStop)//(*st).Int()==KRequestPending)
      {

         User::After(10000);
         iAfter-=10;
         if(iAfter<0)
         {
            if(!iStop)
            {
            TRequestStatus *s=st;
            th.RequestComplete(s,KErrNotFound);
            }

            break;
         }

      }

      return 0;
   }

};
void waitForRequestMSec(TRequestStatus *st, int iMSec){
   CTTCancel c(st,iMSec);
   c.start();
   User::WaitForRequest(*st);//TODO _PRI_HI timer
   c.cancel();
}

#endif

int fixPostEncoding(char *dst, const char *p, int iLen){
   
   int i,iLenOut=0;
   for(i=0;i<iLen;i++){
      //TODO fix all chars
      int f=p[i]=='@' || isspace(p[i]);
      if(f){
         int v=p[i];
         dst[iLenOut]='%';iLenOut++;
         dst[iLenOut]='0'+(v>>4);iLenOut++;
         dst[iLenOut]='0'+(v&15);iLenOut++;
      }
      else{
         dst[iLenOut]=p[i];
         iLenOut++;
      }
   }
   dst[iLenOut]=0;
   return iLenOut;
}

int fixPostEncodingToken(char *dst, int iMaxLen, const char *p, int iLen){
   
   int i,iLenOut=0;
   for(i=0;i<iLen;i++){
      //TODO fix all chars
      if(iLenOut+4>iMaxLen)break;
      int f=p[i]=='@' || p[i]=='/' || p[i]=='?' || p[i]==':' || p[i]=='&' || isspace(p[i]);
      if(f){
         int v=p[i];
         dst[iLenOut]='%';iLenOut++;
         dst[iLenOut]='0'+(v>>4);iLenOut++;
         dst[iLenOut]='0'+(v&15);iLenOut++;
      }
      else{
         dst[iLenOut]=p[i];
         iLenOut++;
      }
   }
   dst[iLenOut]=0;
   return iLenOut;
}

#define SECURITY_COOKIE_SIZE 50

char *loadFileW(const  short *fn, int &iLen)
{
   char *p;
#if defined(__APPLE__) || defined(__linux__) 
   char bufFn[1024];
   convert16to8S(&bufFn[0],sizeof(bufFn)-1,(short*)fn,0);
 //  convert16to8(&bufFn[0],(short*)fn,0);
   FILE *f=fopen(bufFn,"rb");
#else
   wchar_t rb[]={'r','b',0};
   FILE *f=_wfopen((wchar_t*)fn,&rb[0]);//"rb");
#endif
   
   if(f==NULL)return NULL;

   fseek (f , 0 , SEEK_END);
   iLen = ftell (f);
   fseek(f,0,SEEK_SET);
   //rewind (f);

   p = (char*) new char[iLen+SECURITY_COOKIE_SIZE];
   if(p)
   {
      if(iLen)fread (p,1,iLen,f);
      memset(p+iLen,0,SECURITY_COOKIE_SIZE);
   }
   fclose(f);

   return p;
}
char *loadFile(const  char *fn, int &iLen)
{
   char *p;
#ifdef __SYMBIAN32__
   RFs fs;
   fs.Connect();
   RFile f;
   TBuf<128> b;
   b.PtrZ();
   int l=strlen(fn);
   convert8_16((unsigned char *)fn, (unsigned  short *)b.PtrZ(), l,1);
   b.SetLength(l);


 

   if(KErrNone!=f.Open(fs,b,EFileRead ))
   {
      fs.Close();
      return NULL;;
   }


   //if file

   f.Size(iLen);
   if(iLen<=0)return NULL;
   p = (char*) new char[iLen+SECURITY_COOKIE_SIZE];
   if(p)
   {
      TPtr8 bd((unsigned char *)p,0,iLen);

      TRequestStatus aStatus;
      f.Read(bd,aStatus);
      User::WaitForRequest(aStatus);
       memset(p+iLen,0,SECURITY_COOKIE_SIZE);

   }
   f.Close();
   fs.Close();
   //f.

#else
   
   FILE *f=fopen(fn,"rb");
   
   if(f==NULL){
      puts("fopen NULL");
      perror(fn);
      return NULL;
   }
   iLen=0;
   fseek (f , 0 , SEEK_END);
   iLen = ftell (f);
   fseek(f,0,SEEK_SET);
   //rewind (f);


   p = (char*) new char[iLen+SECURITY_COOKIE_SIZE];
   if(p)
   {
      fread (p,1,iLen,f);

      memset(p+iLen,0,SECURITY_COOKIE_SIZE);
   }
   fclose(f);
#endif

   return p;
}
#if  defined(__APPLE__) || defined(ANDROID_NDK)
#include <unistd.h>
#include <sys/stat.h>
#endif

int isFileExists(const char *fn){
   
   FILE *f=fopen(fn,"rb");
   
   if(f){
      fclose(f);
      return 1;
   }
//   perror("isFileExists()");
   /*
    struct stat st;
    int result = stat(fn, &st);
    return result == 0;
   */
   return 0;
}
int isFileExistsW(const short *fn){
   char bufD[1024];
   convert16to8S(&bufD[0],sizeof(bufD)-1,(short*)fn,0);
   return isFileExists(&bufD[0]);
}


void deleteFile(const char *fn)
{
#ifdef __SYMBIAN32__
   RFs fs;
   TBuf<128> b;
   b.PtrZ();
   int l=strlen(fn);
   convert8_16((unsigned char *)fn, (unsigned  short *)b.PtrZ(), l,1);
   b.SetLength(l);

   fs.Connect();
   fs.Delete(b);
   fs.Close();
#else 
#if  !defined(_WIN32_WCE) && defined(_WIN32)
   
   unlink(fn);
#endif
#if   defined(__APPLE__) || defined(ANDROID_NDK)
   
   unlink(fn);
#endif

#if  defined(_WIN32_WCE)

//   DeleteFileA(fn);
#endif
#endif
}
void deleteFileW(const short *fn){
   char bufD[1024];
   convert16to8S(&bufD[0],sizeof(bufD)-1,(short*)fn,0);
   deleteFile(&bufD[0]);
}
void saveFileW(const short *fn,void *p, int iLen)
{
#if defined(__APPLE__) || defined(__linux__) 
   char bufFn[1024];
   convert16to8S(&bufFn[0],sizeof(bufFn)-1,(short*)fn,0);//TODO utf
   FILE *f=fopen(bufFn,"wb+");
#else
   wchar_t wbp[]={'w','b','+',0};
   FILE *f=_wfopen((wchar_t *)fn,&wbp[0]);//"wb+");
#endif
if(!f)return;
   
   fwrite(p,1,iLen,f);
   fclose(f);
}
void saveFile(const char *fn,void *p, int iLen)
{
#if 0 //def __SYMBIAN32__
   RFs fs;
   fs.Connect();

   RFile f;
   TBuf<256> b;
   b.PtrZ();
   int l=strlen(fn);
   convert8_16((unsigned char *)fn, (unsigned  short *)b.PtrZ(), l,1);
   b.SetLength(l);

   if(KErrNone!=f.Open(fs,b,EFileWrite ))
   {
      fs.Close();
      return ;
   }
   f.Write(TPtrC8((unsigned char *)p,iLen));
   f.Close();


   fs.Close();
#else
   FILE *f=fopen(fn,"wb+");
   if(!f)return;
   fwrite(p,1,iLen,f);
   fclose(f);
#endif
}
#if defined(_WIN32_WCE)
#include <windows.h>
HBITMAP SHLoadImage(char *p, int iLen)
{
   return 0;
}
#endif
#if (defined(_WIN32_WCE))
HBITMAP SHLoadImageFilePng(void* fn)
{
   HBITMAP hBmp=NULL;
   int iLen=0;
   char szFNnew[1024];
   convert16to8S(&szFNnew[0],sizeof(szFNnew)-1,(short*)fn,0);
   int iFNLen=strlen(szFNnew);
   
   if(iFNLen>4 && ((szFNnew[iFNLen-1]=='g' && szFNnew[iFNLen-2]=='n' && szFNnew[iFNLen-3]=='p')
      ||(szFNnew[iFNLen-1]=='G' && szFNnew[iFNLen-2]=='N' && szFNnew[iFNLen-3]=='P'))&& 
      szFNnew[iFNLen-4]=='.'){
         HBITMAP LoadPng2HBMP(const char fn[]);
      return LoadPng2HBMP(szFNnew);
   }
   return NULL;
}
#endif

#if (!defined(_WIN32_WCE) && defined(_WIN32) && !defined(__SYMBIAN32__))
#pragma message("!win32_ce && win32")
#include <olectl.h>
#include <shlobj.h>
#include <shlguid.h>

HBITMAP SHLoadImage(char *p, int iLen)
{
   HBITMAP hBmp=NULL;
   LPPICTURE  gpPicture;
   LPSTREAM   pstm;
   HGLOBAL    hGlobal=NULL;
   void *pvData=NULL;
   pvData=NULL;
   DWORD dwFileSize=(DWORD)iLen;

   hGlobal=GlobalAlloc(GMEM_MOVEABLE,dwFileSize);
   pvData=GlobalLock(hGlobal);
   memcpy(pvData,p,iLen);
   GlobalUnlock(hGlobal);
   if(pvData)
   {
      //OleSavePictureFile
      CreateStreamOnHGlobal(hGlobal,TRUE,&pstm);
      OleLoadPicture(pstm,0,TRUE,IID_IPicture,(LPVOID*)&gpPicture);

      pstm->Release();
      if(gpPicture)
      {
         gpPicture->get_Handle((OLE_HANDLE*)&hBmp);
      }
      
   }
   return hBmp;

}
#include <shlwapi.h>
int saveBitmap(HBITMAP hBmp,char *fn)
{
   int w=100,  h=100;
   HBITMAP hbmRet = (HBITMAP)CopyImage((HANDLE)hBmp, IMAGE_BITMAP, w, h, LR_CREATEDIBSECTION);
   if(hbmRet!=NULL)
   {
      PICTDESC pictd;
      LPPICTURE  gpPicture;

      pictd.cbSizeofstruct=sizeof(PICTDESC);
      pictd.picType=PICTYPE_BITMAP;
      pictd.bmp.hbitmap=hbmRet;


      OleCreatePictureIndirect(&pictd,IID_IPicture,TRUE,(LPVOID*)&gpPicture);
      IStream * pStream = NULL;
      //SHCreateStreamOnFile( fn, STGM_WRITE | STGM_SHARE_EXCLUSIVE | STGM_CREATE | STGM_DIRECT, &pStream );
      
      HGLOBAL    hGlobal=NULL;
      void *pvData=NULL;
      pvData=NULL;

     
      CreateStreamOnHGlobal(NULL,TRUE,&pStream);
      LONG      lSize;
      HRESULT h=gpPicture->SaveAsFile( pStream, TRUE, &lSize);

      GetHGlobalFromStream(pStream, &hGlobal);
      void *pData = GlobalLock(hGlobal);
      saveFile(fn,(char *)pData,(int)lSize);
      //d=GlobalSize(hGlobal);
      GlobalUnlock(hGlobal);

      pStream->Release();
      gpPicture->Release();

   //call IPicture->SaveAsFile(...)
   }
   return 0;
}
#if !defined(_CONSOLE)
HBITMAP LoadPng2HBMP(const char fn[]);
HBITMAP SHLoadImageFile(void* fn)
{
   HBITMAP hBmp=NULL;
   int iLen=0;
   char szFNnew[1024];
   convert16to8S(&szFNnew[0],sizeof(szFNnew)-1,(short*)fn,0);
   int iFNLen=strlen(szFNnew);
   if(iFNLen>4 && ((szFNnew[iFNLen-1]=='g' && szFNnew[iFNLen-2]=='n' && szFNnew[iFNLen-3]=='p')
      ||(szFNnew[iFNLen-1]=='G' && szFNnew[iFNLen-2]=='N' && szFNnew[iFNLen-3]=='P'))&& 
      szFNnew[iFNLen-4]=='.'){
      hBmp= LoadPng2HBMP(szFNnew);
      if(hBmp)return hBmp;
   }
   char *p=loadFile(szFNnew,iLen);
   if(!p)return NULL;
   hBmp=SHLoadImage(p,iLen);
   delete p;
   //strcat(szFNnew,".jpg");
   //saveBitmap(hBmp,szFNnew);
   return hBmp;
   
}
#endif


#endif //

#if  !defined(_WIN32_WCE) && defined(_WIN32)

void t_setFileAttributes(short *fn, unsigned int atrib){
   if(atrib==0)atrib=FILE_ATTRIBUTE_NORMAL;
   ::SetFileAttributesW((LPCWSTR)fn,atrib);
}
unsigned int t_getFileAttributes(short *fn){
   //SetFileAttribute
   return GetFileAttributesW((LPCWSTR)fn);
}
#else
void t_setFileAttributes(short *fn, unsigned int atrib){
   
}
unsigned int t_getFileAttributes(short *fn){
   return 0;
}

#endif