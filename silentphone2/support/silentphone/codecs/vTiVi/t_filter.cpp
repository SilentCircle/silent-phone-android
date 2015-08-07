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
#include <math.h>
#include <stdlib.h>
#ifndef max
#define max(A,B) ((A)>(B)?(A):(B))
#define min(A,B) ((A)<(B)?(A):(B))
#endif
inline int clipZ(int a,  int v){if(v<=-a)return -a; if(v>=a)return a;return v;}

//#define __SYMBIAN32__
#ifdef __SYMBIAN32__ 
inline int abs(int a) {
   int mask = (a >> 31);
   return (a + mask) ^ mask;
}
//#define abs(_A) 
#endif
#ifndef __SYMBIAN32__ 


//static int iClipValues=0;

//#define  BLK_SIZE 8
#define u_int8 unsigned char 
#define int8 char
static inline int clip(int a, int b, int c){if(c<a)return a; if(c>b)return b;return c;}
static inline unsigned char _clip_uint8(int a){if(a<=0)return 0; if(a>=255)return 255;return a;}




#endif
static const unsigned char t_l2[]=  {0,0,0,0, 0,0,0,0, 1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,3,3,3,3,3,
3,3,4,4,4,4,4,4,5,5,5,5,6,6,6,6,7,7,7,7,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
};

//0,1,1,1, 1,2,2,3 ,3,3,3,3,4,4,4,4,5,5,5,6,6,6,7,7,7,8,8,8,9,9,9,10,10,10,11,11,11,12,12,12,13,13,13,14,14,14,15,15,15,
static const unsigned char t_l1[]=  {0,1,1,1, 1,2,2,2 ,3,3,3,3,4,4,4,5,5,5,6,6,6,7,7,7,8,8,8,9,9,9,10,10,10,11,11,11,12,12,12,13,13,13,14,14,14,15,15,15,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
};
/*
const unsigned char t_s0l2[]={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,3,3,3,3,3,3,3,3,3,3, 4, 4, 4, 4, 4, 4, 4, 4,
5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,};
const unsigned char t_s1l2[]={0,0,0,0,0,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,2,2,3,3,3,3,3,3,3,3,3,3,3,3,3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,};
*/
//static inline int t_abs(int i){return i<0?-i:i;}
static int fsTab[300];
//static int fsTabR[300];
//static int fsTab[300];
int getOneBitCost(int t0, int *sadDif){
   short bl[16]={0,0,t0,0, 
      0,0,0,0, 
      0,0,0,0, 
      0,0,0,0, };
   void itrxF(short *p);
   itrxF(&bl[0]);
   int i;
   int iCost=0;
   for(i=0;i<16;i++)iCost+=sadDif[(abs(bl[i])+8)>>4];
   return (iCost>>3)+1;

}
int setFSTabVal(int a, int t0, int t1, int fs){
   if(a>299)a=299;
   if(!fsTab[a]){
      fsTab[a]=fs;return fs;


      short bl[16]={0,(t1+t0*2)>>1,0,0,
         //short bl[16]={-t0,t1,0,0, 
         0,0,0,0, 
         0,0,0,0, 
         0,0,0,0, };
      void itrxF(short *p);
      itrxF(&bl[0]);
      void setFSTabVal(int a, int str);
      //      int str=((abs(bl[0])+16)>>5);
      //bl[1]*=7;bl[1]/=8;
      //      int str=((abs(bl[1])+8)>>4);
#if 0
      int str=((abs(bl[0])+20)>>5);
      fsTab[a]=((abs(bl[1]))>>4);
#else
      //      int str=((abs(bl[0])*25+512)>>10);
      int str=(abs(bl[5]))>>(4);

      //fsTabR[a]=(abs(bl[0])*6)>>(4+3);
      //if(fsTabR[a]>8){fsTabR[a]+=8;fsTabR[a]>>=1;}
      // int str=(abs(bl[5]-bl[0])+12)>>5;

      // fsTab[a]=((abs(bl[5])*3)>>6);

#endif
      if(str>8){str+=8;str>>=1;} if(str>15){str=15;}
      if(str<fsTab[a])fsTab[a]=str;
      fsTab[a]=str;
      //fsTab[a]=(str*3)>>2;
   }
   return fsTab[a];
}
int setFSTabValx( int t0, int t1){
   static short r[256];
   static int iInit=1;
   if(iInit){
      iInit=0;
      for(int i=0;i<256;i++)r[i]=-1;
   }
   if(t0>1023)t0=1023;
   const int t0sh2=t0>>2;
   if(r[t0sh2]>=0 && t0sh2!=255)
      return r[t0sh2];

   //short bl[16]={0,(t1+t0*2)>>1,0,0,
   short bl[16]={0,(t1+t0*2)>>1,0,0, 
      //short bl[16]={0,0,(t1+t0*2)>>2,0, 
      0,0,0,0, 
      0,0,0,0, 
      0,0,0,0, };
   void itrxF(short *p);
   itrxF(&bl[0]);
   //  r[t0sh2]=(abs(bl[0])+12)>>4;//((abs(bl[0])*7+128)>>8);
   r[t0sh2]=(abs(bl[5])+8)>>4;//--(abs(bl[0])*7+2)>>4;//((abs(bl[0])*7+128)>>8);
   if(r[t0sh2]>8){r[t0sh2]+=8;r[t0sh2]>>=1;}//if(r[t0sh2]>15)r[t0sh2]=15;
   //if(r[t0sh2]>12){r[t0sh2]+=12*3;r[t0sh2]>>=2;}//if(r[t0sh2]>15)r[t0sh2]=15;
   return r[t0sh2];
}
//TODO each thread should have dif value
#if 1
static int iFSX=5;
static int iFSD2=2;
static int T2Stat=5;
static int T2StatX=5;
#else
__thread int iFSX=5;
__thread int iFSD2=2;
__thread int T2Stat=5;
__thread int T2StatX=5;
#endif
//static int iFSL=5;
//--prev--tested--
#define GETFS iFSX
//#define GETFS fsTab[a<300?a:299]

//static int tfa=51;
static int tfb=15;
void setTestFilterAB(int a,int b){
   tfb=b;
  // tfa=a;
}


int setFTabA(int a, int iPic){

   static int pa[2];

   if(a==pa[0])return iFSX;
   pa[0]=a;
   short bl[16]={0,0,a,0, 
      0,0,0,0, 
      0,0,0,0, 
      0,0,0,0, };
   void itrxF(short *p);
   itrxF(&bl[0]);

   int absb0=abs(bl[0]);
   a=(absb0*7+64)>>(4+3);//3449

   T2Stat=(absb0*9+16)>>(6);
   T2StatX=(abs(bl[0]-bl[5])*3)>>(4+2);
   if(T2Stat>10){T2Stat+=30;T2Stat>>=2;}
   if(a>6){a+=6*3;a>>=2;}
   iFSX=a;
   iFSD2=(iFSX+2)>>1;
   return iFSX;
}




#define t_abs(_a) abs(_a)
static inline int t_lim_2(int v, int lim1, int lim2){
   return lim1<v && v<lim2;
}
static inline int t_lim_m(int v, int lim1){
   return (unsigned )(v+lim1)<(lim1<<1);
   //return -lim1<v && v<lim1
}

#define t_filter_L t_filter_LZ
//#define t_filter_L t_filter_LxZ

//#define t_filter_L t_filter_L_TL
#ifdef T_CAN_TEST_V

int testF(unsigned char *p, int step, int L ){
   int tmp0,tmp1,tmp10,tmp11;
   int r1[4];
   int r2[4];
   tmp0 = p[0*step] + (p[3*step]);
   tmp1 = p[1*step] + (p[2*step]);
   tmp10 = (p[0*step]) - (p[3*step]);
   tmp11 = (p[1*step]) - (p[2*step]);
   r1[0] = (tmp0 + tmp1);
   r1[1] =(tmp10*3 + tmp11);
   r1[2] = (tmp0 - tmp1);
   r1[3] =(tmp10 - tmp11*3);

   if(abs(r1[2])+abs(r1[3])>L)return 0;

   tmp0 = p[-1*step] + (p[-4*step]);
   tmp1 = p[-2*step] + (p[-3*step]);
   tmp10 = (p[-1*step]) - (p[-4*step]);
   tmp11 = (p[-2*step]) - (p[-3*step]);
   r2[0] = (tmp0 + tmp1);
   r2[1] =(tmp10*3 + tmp11);
   r2[2] = (tmp0 - tmp1);
   r2[3] =(tmp10 - tmp11*3);

   if(abs(r1[2])+abs(r1[3])>L)return 0;


   return abs(r1[1]-r2[1])<L;// && abs(r1[0]-r2[0])<L*8;

}
inline int testTL(unsigned char *p, int step, int L ){

   return abs(p[0*step]-p[1*step])<L && abs(p[-1*step]-p[-2*step])<L
      && abs(p[2*step]-p[1*step])<L && abs(p[-2*step]-p[-2*step])<L
      && abs(p[2*step]-p[3*step])<L && abs(p[-3*step]-p[-2*step])<L;
}

static  void t_filter_LZ(unsigned char *p, const int stridex, const int stridey, const int L, int a, int iPass, char *pC){
   int i,_r,dif; 




   //if(_r*4<=a+12){pC[i+_O]=_d;}

   //


   /*
   #define CMPF_d(_O) \
   _d=t_abs(p[-stridex]-p[0]); \
   if(_d>1 && _d<a){ \
   _d1=p[0]-p[stridex];\
   _d2=p[-2*stridex]-p[-stridex];\
   _r=_d1-_d2;_s=_r>>31;_r^=_s;\
   if(_r*2<=a){pC[i+_O]=_d;} else pC[i+_O]=0;\
   }else pC[i+_O]=0;
   */
   //      if((_O==1 || _O==2 )){if(abs(p[0]-p[stridex*2])*4<pC[i+_O])p[stridex]+=((p[0]+p[-stridex])-p[stridex]*3+p[stridex*2]+2)>>2;if(abs(p[-stridex]-p[-stridex*3])*4<pC[i+_O])p[-stridex*2]+=((p[0]+p[-stridex])-3*p[-stridex*2]+p[-stridex*3]+2)>>2;}\
   //      
   //- r01 + r02 - r03 + r04
   //
   //&& abs(p[0]-p[stridex])<=fsTab[a] && abs(p[-stridex]-p[-2*stridex])<=fsTab[a]
   //_r=((n1-p1)>>1)-(n0-p0);1234 (3>>1)-1  
   //  _r=(p[0]+p[-stridex])-(p[stridex]+p[-2*stridex]);\
   && abs((p[0]+p[-stridex])-(p[stridex]+p[-2*stridex]))<=fsTab[a] \
   _r=((p[-2*stridex]-p[stridex]))-dif;\
   //if(_r<(_d>>2)+fs1 && _r+(_d>>3)<=T2 ){\
   if(_r*2+_d<a+fs1 && dif){\
   _r=(2*(p[-2*stridex] - p[ 1*stridex]) - 5*(p[-1*stridex] - p[ 0]) + 4) >> 3;\

#define _dctCMPF_d(_O) \
   dif=p[-stridex]-p[0];\
   if(dif){\
   _d=abs(dif);\
   if(_d<a && testF(p,stridex,fs1*16)){\
   pC[i+_O]=_d;\
   } else pC[i+_O]=0;\
   }
   //(((n0-p0)*3) + (p1-n1) + 4) >> (3)  ((p[0]-p[-stridex])*3+(p[-2*stridex]-p[stridex])+4)>>3

   //_d=abs(dif);

#define _CMPF_d_wod(_O) \
   dif=p[-stridex]-p[0];\
   _s=dif>>31;_d=(dif+_s)^_s;\
   _r=(p[0]+p[-stridex])-(p[stridex]+p[-2*stridex]);\
   _s=_r>>31;_r^=_s;\
   if(_r*2+_d<=a){\
   pC[i+_O]=_d;\
   }\

   //8 4 12 8, 1 2 5 6
   //d=8 8-8 -(4-12)
   //8 8 0 0

#define _CMPF_d(_O) \
   dif=p[-stridex]-p[0];\
   if(dif){\
   _s=dif>>31;_d=(dif+_s)^_s;\
   _r=(p[0]-p[-2*stridex])-(p[stridex]-p[-stridex]);\
   _s=_r>>31;_r^=_s;\
   if(_r*2+_d<=a){\
   pC[i+_O]=_d;\
   }\
   }

#define aa_CMPF_d(_O) \
   dif=p[-stridex]-p[0];\
   _d=t_abs(dif); \
   if((_d>1 && _d<a)){ \
   _r=((p[-2*stridex]-p[stridex]))-(dif);\
   _s=_r>>31;_r^=_s;\
   if(_r*2+_d<=fs1+a){\
   pC[i+_O]=_d;\
   } else pC[i+_O]=0;\
   }else pC[i+_O]=0;

#define _oCMPF_d(_O) \
   _d=t_abs(p[-stridex]-p[0]); \
   if(_d>1 && _d<a){ \
   if(abs((p[stridex]*3+p[stridex-stridey]+p[stridex+stridey]+p[stridex*2])-p[0]*6)<=a &&\
   abs((p[-stridex*2]*3+p[-stridex*2-stridey]+p[-stridex*2+stridey]+p[-stridex*3])-p[-stridex]*6)<=a\
   ){\
   pC[i+_O]=_d;\
   } else pC[i+_O]=0;\
   }else pC[i+_O]=0;



   //if(((p[stridex]+p[-stridex]+1)>>1)!=p[0])
   //if(((p[0]+p[-2*stridex]+1)>>1)!=p[-stridex])
   //if(((p[stridex]+p[-stridex]+1)>>1)!=p[0])
   //if(((p[0]+p[-2*stridex]+1)>>1)!=p[-stridex])
   //if(((t_abs(_d1+_d2)<<1)<_d && (t_abs(_d1)+t_abs(_d2))<_d))
   //1234
   /*
   diff = (((orig[4]-orig[3])<<2) + (orig[2]-orig[5]) + 4) >> 3;
   //if(diff)
   {
   diff = clip(-c, c, diff);

   dest[-dir] = (u_int8)clip(0, 255, orig[3]+diff);
   dest[   0] = (u_int8)clip(0, 255, orig[4]-diff);
   }
   const int fc=p[stridex*2]==p[stridex] && p[-stridex]==p[-stridex*2] && d<7 ?(d>>1): t_l1[d];
   */
   // 1  3  5  7     
   //   const int diff =  clip(-t_l1[pC[i+_O]], t_l1[pC[i+_O]], (((p0-n0)*3) + (n1-p1) + 4) >> 3);
   //n1 n0 p0 p1   n0=n0-n1+n1-n2
   //16 81  -3 
   //2244 (2-4)*3+(4-2)=0

   //   const int diff =  clipZ(t_l1[pC[i+_O]], (((p0-n0)*3) + (n1-p1) + 4) >> 3);\
   const int diff =  clipZ(pC[i+_O]>>1, (((p0-n0)*3) + (n1-p1) + 2) >> 2);\
   //min((a>>2),pC[i+_O])

#define _FzL(_O) {\
   iFC++;\
   const int p0=p[0];\
   const int p1=p[stridex];\
   const int n0=p[-stridex];\
   const int n1=p[-2*stridex];\
   p[0]=(2*n0-n1+p0*2+2+p1)>>2;\
   p[-stridex]=(2*p0-p1+n0*2+2+n1)>>2;\
   }
   //      

   //      && pC[i]&& pC[i+3]&& pC[i+1]&& pC[i+2] 
   //      if((_O==1 || _O==2 )){if(abs(p0-p[stridex*2])*4<pC[i+_O])p[stridex]+=clipZ((pC[i+_O]+6)>>3,((p0+n0)-p1*3+p[stridex*2]+2)>>2);if(abs(n0-p[-stridex*3])*4<pC[i+_O])p[-stridex*2]+=clipZ((pC[i+_O]+6)>>3,((p0+n0)-3*n1+p[-stridex*3]+2)>>2);}\
   //   iFC++;\

   //   
   //((_O==1 || _O==2) && (iF&~0x07070707))
   //t_l1[pC[i+_O]]
   //-4*3+4-8
   //d=min(dif>>1,((al+3)>>2));
   //fsTab
   //min(pC[i+_O]>>1,al)

#define _FL_okzz(_O,_F) {\
   const int fc=min(pC[i+_O]>>1,al);\
   if(p[-stridex]>p[0]){p[0]+=fc;p[-stridex]-=fc;}else {p[0]-=fc;p[-stridex]+=fc;}\
   }

   //   iFC++;\
   //(_d+28+a)>>5
   //#define _FLLL _FL
   //   const int diff =  clipZ(iFS, (((n0-p0)*8) + (p1+p2-n1-n2) + 3+_F) >> (3));\
   const int diff2 = clipZ(min((pC[i+_O]+6)>>3,(fs1+3)>>2),(((n0-p0)*4) + (p1+p2-n1-n2) + 8-_F) >> (4));\
   const int p2=p[2*stridex];\
   const int n2=p[-3*stridex];\
   ab cd
   //234
   //b+b-a=c;
   //c+c-d=b;


   //0000 8888 64+
#define _vvFL(_O,_F) {\
   const int p0=p[0];\
   const int p1=p[stridex];\
   const int n0=p[-stridex];\
   const int n1=p[-2*stridex];\
   const int diff =  clipZ(fs1, (((n0-p0)*3) + (p1-n1) + 4) >> (3));\
   p[0]=(p0+diff);\
   p[-stridex]=(n0-diff);\
   }
   //(bx-2*cx+dx-cx+2*bx-ax),(3*cx+3*bx+dx-ax)
   //const int diff=clipZ(fs1,((bx-(cx+cx-dx))-(cx-(bx+bx-ax))+4)>>3);\

#define _FL(_O,_F) {\
   const int ax=p[stridex];\
   const int bx=p[0];\
   const int cx=p[-stridex];\
   const int dx=p[-2*stridex];\
   const int diff=clipZ(fs1,(3*(bx-cx)+dx-ax+4)>>3);\
   p[0]=(bx-diff);\
   p[-stridex]=(cx+diff);\
   }

#define _FLLL(_O,_F) {\
   const int p0=p[0];\
   const int p1=p[stridex];\
   const int n0=p[-stridex];\
   const int n1=p[-2*stridex];\
   const int spn1=p1-n1;\
   const int diff2 = clipZ(fs2,(spn1 + 4 - _F) >> 3);\
   const int diff =  clipZ(min(pC[i+_O],fs1+!!diff2), (((n0-p0)*3) + (spn1) + 3+_F) >> (3));\
   if(diff){\
   p[stridex]=(p1-diff2);\
   p[-stridex*2]=(n1+diff2);\
   p[0]=_clip_uint8(p0+diff);\
   p[-stridex]=_clip_uint8(n0-diff);\
   }}
   /*
   #define _FLLL(_O,_F) {\
   const int p0=p[0];\
   const int p1=p[stridex];\
   const int p2=p[2*stridex];\
   const int n0=p[-stridex];\
   const int n1=p[-2*stridex];\
   const int n2=p[-3*stridex];\
   _d=pC[i+_O];\
   int iFS=fs1;\
   if(abs(p1+p0-p2*2)<=al){p[stridex]+=clipZ(1,((p0+n0)-p1*3+p2+2-_F)>>2);iFS=fs1+1;}\
   if(abs(n1+n0-n2*2)<=al){p[-stridex*2]+=clipZ(1,((p0+n0)-3*n1+n2+2-_F)>>2);iFS=fs1+1;}\
   const int diff =  clipZ(iFS, (((n0-p0)*3) + (p1-n1) + 3+_F) >> (3));\
   if(diff){\
   p[0]+=diff;\
   p[-stridex]-=diff;\
   }}

   */

#define _FLzz(_O) {\
   const int fc=t_l1[pC[i+_O]];\
   iFC++;if(p[-stridex]>p[0]){p[0]+=fc;p[-stridex]-=fc;}else {p[0]-=fc;p[-stridex]+=fc;}\
   }
   //p[-stridex]++;p[0]--;p[0]++;p[-stridex]--;

#define _FL_LO(_O) {\
   const int fc=((t_l1[pC[i+_O]])>>2);\
   if(p[stridex]<p[-stridex*2]){p[stridex]+=fc;p[-stridex*2]-=fc;}else {p[stridex]-=fc;p[-stridex*2]+=fc;}\
   }

#define _FL_L(_O) {\
   if(abs(p[0]-p[stridex*2])*4<=pC[i+_O])p[stridex]+=clipZ((pC[i+_O]+6)>>3,((p[0]+p[-stridex])-p[stridex]*3+p[stridex*2]+2)>>2);\
   if(abs(p[-stridex]-p[-stridex*3])*4<=pC[i+_O])p[-stridex*2]+=clipZ((pC[i+_O]+6)>>3,((p[0]+p[-stridex])-3*p[-stridex*2]+p[-stridex*3]+2)>>2);\
   }   
   /*
   orig[2] = dest[-2*dir];
   orig[3] = dest[  -dir];
   orig[4] = dest[     0];
   orig[5] = dest[   dir];
   dest[  -dir] = (u_int8)((orig[3] + orig[5] + 2*orig[2] + 2) >> 2);
   dest[     0] = (u_int8)((orig[2] + orig[4] + 2*orig[5] + 2) >> 2);

   #define _FL(_O) {\
   p[-stridex]=(p[-stridex]+p[stridex]+p[-stridex*2]*2+2)>>2;\
   p[0]=(p[-2*stridex]+p[stridex]*2+p[0]+2)>>2;iFC++;\
   }*/




   int _d,_d1,_d2,_s,iF,iFC;

   if(iPass==1)
   {
      //      int a1,a2,a3;
      const int fs1=fsTab[a];//(a+6)>>3;
      const int T2=min((a+2)>>2,fs1*3);

      //alphaTab
      //int ad3=((a*80)>>8)+1;      if(ad3>9){ad3+=27;ad3>>=2;}
      //if(ad3>8){ad3+=56;ad3>>=3;}
      // for(int k=8;k<52;k++)if(alphaTab[k]==a)ad3=betaTab[k];
      for(i=0;i<L;i+=4){

         p+=stridey;
         *(int*)&pC[i]=0;
         _CMPF_d(1);
         p+=stridey;
         _CMPF_d(2);
         if(*(int*)&pC[i]){
            p+=stridey;
            _CMPF_d(3);
            p-=stridey*3;
            _CMPF_d(0);
            p+=stridey*4;
         }
         else{
            p+=stridey*2;
         }
      }
   }
   else {
    //  const int al=(a+12)>>3;
      const int fs1=(a+6)>>3;
      const int fs2=(fs1+5)>>3;
      const unsigned int iLS=(127-(a>>4)-3)*0x01010101;
      for(i=0;i<L;i+=4){
         iF=*(int*)&pC[i];
         if(iF){
            // unsigned y = ((iF & 0x7F7F7F7Fu)+0x7F7F7F7F)|iF;
            //unsigned y = iF + 0x7F7F7F7F;
            if (//(pC[i]|pC[i+1]|pC[i+2]|pC[i+3])>=(a>>2) || 
               ((((unsigned)iF + iLS) & 0x80808080u) ) || 
               (((unsigned)iF + 0x7F7F7F7F) & 0x80808080u) != 0x80808080u){
                  if(pC[i])_FL(0,0);p+=stridey;
                  if(pC[i+1]){_FL(1,1);}p+=stridey;
                  if(pC[i+2]){_FL(2,0);}p+=stridey;
                  if(pC[i+3])_FL(3,1);p+=stridey;
            }
            else{
               _FL(0,0);p+=stridey;
               _FLLL(1,1);p+=stridey;
               _FLLL(2,0);p+=stridey;
               _FL(3,1);p+=stridey;
            }
            /*
            iFC=0;
            if(pC[i])_FL(0);p+=stridey;
            if(pC[i+1]){_FL(1);}p+=stridey;
            if(pC[i+2]){_FL(2);}p+=stridey;
            if(pC[i+3])_FL(3);
            if(iFC==4 && (iF&~0x07070707))
            {
            p-=stridey*2;
            if(pC[i]+pC[i+2]<=pC[i+1]*2){_FL_L(1);}p+=stridey;
            if(pC[i+1]+pC[i+3]<=pC[i+2]*2){_FL_L(2);}p+=stridey*2;
            }
            else p+=stridey;
            */        
         }
         else p+=stridey*4;

         //p+=stridey;
      }
   }
}


#define FR10(tmp,ST1,ST2,ST3,ST4) {\
   tmp0 = ST1 + (ST4);\
   tmp1 = ST2 + (ST3);\
   tmp10 = (ST1) - (ST4);\
   tmp11 = (ST2) - (ST3);\
   tmp[0] = (tmp0 + tmp1);\
   tmp[1] =tmp10*3 + tmp11;}

#define FR(tmp,ST1,ST2,ST3,ST4) {\
   tmp0 = ST1 + (ST4);\
   tmp1 = ST2 + (ST3);\
   tmp10 = (ST1) - (ST4);\
   tmp11 = (ST2) - (ST3);\
   tmp[0] = (tmp0 + tmp1);\
   tmp[2] = (tmp0 - tmp1);\
   tmp[1] =(tmp10*3 + tmp11)>>1;\
   tmp[3] =(tmp10 - tmp11*3)>>1;}


template < int iCheckA, int iShort, int iClip>//TODO clip param
static inline int fr2(unsigned char *p, const int stridex, const int stridey, const int a,  const int L, const int T2){//, const int 
   const int p0=p[0];
   const int n0=p[-stridex];
   const int dif=p0-n0;
   int d1,d2,d3;
   int _s;
   int aDif;
   int _s2,_r;
   //if((unsigned int)(dif+1)<=2)return 1;
   //int iShort=0,iCheckA=1;
   if(iCheckA || iShort!=2){
      _s=dif>>31;
      aDif=(dif+_s)^_s;
   
      if(aDif<2 || aDif>a 
         )return 0;
   }

  //--
   int iFS=GETFS;//fsTab[a];
   //int T2=(20*9+(iFS*23))>>5;
   //int iFSR=fsTabR[a];
   //_s=(_v)>>31;
   //_s=(_v)>>31;_v=((_v)+_s)^_s;
   //--const int _s=dif>>31;
   //--const int d=(dif+_s)^_s;
   if(iShort==2){

#define T_FIX(_v,_l){if((_v)>(_l))_v=((_l)+_s)^_s;else _v=((_v)+_s)^_s;}
      //#define T_FIX(_v,_l){_v=((_v)+_s)^_s;}
#define T_FIX2S(_v,_v2,_l){if((_v)>(_l)){(_v)=(_l);} _v2=((_v+6)>>3);_v=((_v)+_s)^_s;_v2=((_v2)+_s)^_s;}
      /*
      #define T_FIX2S(_v,_v2,_l){if((_v)>(_l)){(_v)=(_l);_v2=0;}else {_v2=((_v+2)>>2);_v2=((_v2)+_s)^_s;}_v=((_v)+_s)^_s;}

      */

      //#define T_FIX2(_v,_v2,_l){_s=(_v)>>31;_v=((_v)+_s)^_s;_v=((_v*(5-L*2)+12)>>(4));if((_v)>(_l))(_v)=(_l);_v=((_v)+_s)^_s;_v2=((1)+_s)^_s;}
#define T_FIX2(_v,_v2,_l){_v=((_v*(5+L)+8)>>(4));if((_v)>(_l))(_v)=(_l);_v=((_v)+_s)^_s;_v2=((1)+_s)^_s;}

      _s=(dif)>>31;aDif=((dif)+_s)^_s;
      //#define T_FIX2(_v,_v2,_l){_s=(_v)>>31;_v=((_v)+_s)^_s;_v=((_v*(5-L*2)+12)>>(4));if((_v)>(_l)){(_v)=(_l);_v2=0;}else {_v2=((1)+_s)^_s;}_v=((_v)+_s)^_s;}
      d2=d1=aDif;T_FIX2(d1,d2,iFS);
      p[0]=(p0-d1);//_clip_uint8
      p[-stridex]=(n0+d1);
      //  d2*=(aDif*4<a);
      p[stridex]-=d2;
      p[stridex*-2]+=d2;

      return 1;
   }


   //if(d>1 && d<a)
   //{
   //int ret= (d<a)*-1;
   //const int p0=p[0];
   const int p1=p[stridex];
   //const int n0=p[-stridex];
   const int n1=p[-2*stridex];
   //r01 - r02 - r03 + r04
   //- r01 - r02 + r03 + r04
   //- r01 + r02 - r03 + r04
   //if(L)_r=(((p0-n1))-(p1-n0));//p0-n1-p1+n0
   //else _r=p0+n0-(p1+n1);
   //-- _r+=_s2
   _r=p0+n0-(p1+n1);

   //if(!_r && n1+n0==p0)return 0;

   _s2=_r>>31;_r=(_r+_s2)^_s2;
   //      if(_r>tfb)return 0;

   //  if(((iCheckA && aDif>T2) || (!iCheckA && abs(dif)>T2)) && _r>tfb)return 0;

#if 0
   const int vp8_test_filter=0;
   if(vp8_test_filter){
      {
         if( _r>T2)return 0;
         const int dp1n1=(p1-n1);
         d1=((dif)*(3+L) - (dp1n1));
         _s=d1>>31;d1=(d1+_s)^_s;//d1=abs(d1); //if(d1<0)d1=-d1;
         d1+=4;d1>>=3;

         if(d1<=iFS && abs(p1-p[2*stridex])<=iFS && abs(n1-p[-3*stridex])<=iFS
            && abs(p1-p[3*stridex])<=iFS && abs(n1-p[-4*stridex])<=iFS){
               int fv=d1;
               d1+=((fv*27+63)>>7);
               d2=(fv*18+63)>>7;
               d3=(fv*9+63)>>7;
               d3=(d3+_s)^_s;
               d2=(d2+_s)^_s;
               p[stridex]-=d2;//clipZ(_clip_uint8(p1+clipZ(d2,1));//(iFS+1)>>1));
               p[stridex*-2]+=d2;//_clip_uint8(n1+clipZ(d2,1));//(iFS+1)>>1));
               p[stridex*2]-=d3;//clipZ(_clip_uint8(p1+clipZ(d2,1));//(iFS+1)>>1));
               p[stridex*-3]+=d3;//_clip_uint8(n1+clipZ(d2,1));//(iFS+1)>>1));
         }
         d1=(d1+_s)^_s;
         p[0]-=d1;//_clip_uint8
         p[-stridex]+=d1;

         return 1;
      }
      return 0; 
   }
#endif
   //if(!_r && p1!=p0)return 0;
   //TODO iShortF if(iFS<3)
   if(iCheckA==2){
      if( _r>T2)return 0;


      const int iLF= aDif<iFS*3 
         && abs(p0-p[2*stridex])<iFS && abs(n0-p[-3*stridex])<iFS
         && abs(p[2*stridex]-p[3*stridex])<=iFS && abs(p[-3*stridex]-p[-4*stridex])<=iFS;

      if(!iLF){//T2){
         d1=((dif)*(3) - ((p1-n1))+4)>>3;
         
          if(iClip){
            p[0]=_clip_uint8(p0-d1);p[-stridex]=_clip_uint8(n0+d1);
          }
          else{
             p[0]=(p0-d1);p[-stridex]=(n0+d1);
             
          }
      }
      else {

         d1=(aDif*3+4)>>3;//3478
         d2=(aDif+2)>>(2);
         d3=1;



         d1=(d1+_s)^_s;
         d2=(d2+_s)^_s;
         //--d3=(d3+_s)^_s;
         if(iClip){
            //8by8 dont aplly long filter
           //-- p[stridex*2]=_clip_uint8(p[stridex*2]-d3);
           //-- p[stridex*-3]=_clip_uint8(p[stridex*-3]+d3);
            
            p[0]=_clip_uint8(p0-d1);//_clip_uint8
            p[-stridex]=_clip_uint8(n0+d1);
            
            p[stridex]=_clip_uint8(p1-d2);//clipZ(_clip_uint8(p1+clipZ(d2,1));//(iFS+1)>>1));
            p[stridex*-2]=_clip_uint8(n1+d2);//_clip_uint8(n1+clipZ(d2,1));//(iFS+1)>>1));
         }
         else{
            p[stridex*2]-=d3;//clipZ(_clip_uint8(p1+clipZ(d2,1));//(iFS+1)>>1));
            p[stridex*-3]+=d3;//_clip_uint8(n1+clipZ(d2,1));//(iFS+1)>>1));
            
            p[0]=(p0-d1);//_clip_uint8
            p[-stridex]=(n0+d1);
            
            p[stridex]=p1-d2;//clipZ(_clip_uint8(p1+clipZ(d2,1));//(iFS+1)>>1));
            p[stridex*-2]=n1+d2;//_clip_uint8(n1+clipZ(d2,1));//(iFS+1)>>1));
         }

         /*            d4=(1+_s)^_s;
         p[stridex*3]-=d4;//clipZ(_clip_uint8(p1+clipZ(d2,1));//(iFS+1)>>1));
         p[stridex*-4]+=d4;//_clip_uint8(n1+clipZ(d2,1));//(iFS+1)>>1));
         */
      }
      //--d4=(1+_s)^_s;

      //d4=(d4+_s)^_s;
      //((aDif+6)>>3)
      return 1;
   }

   //if(!iShort && (p0==p[2*stridex] || n0-p[-3*stridex]))_r>>=1;
   /*
   int r2;
   r2=p0+p1-(p[stridex*2]+n0);_s2=r2>>31;r2^=_s2;_r|=(r2>>1);
   r2=n0+n0-(p[stridex*-3]+p0);_s2=r2>>31;r2^=_s2;_r|=(r2>>1);
   */
   //      if(  (d1=(((((dif)*(3+L) - (dp1n1))) +4)>>3)) && abs(d1)+_r<=T2*2){
#if 1
   // static int ft;
   // ft*=29;ft+=19;ft&=7;
   {// && (iShort!=2 || abs(p1-p[2*stridex])<T2 && abs(n1-p[-3*stridex])<T2)){// && abs((((p0-n1))-(p1-n0)))<=T2 ){// && abs(d1)<=T2){//2888 155

#else
   if ((abs(p0 - n0)*2 + abs(p1-n1)/2) <= a
      && abs(p[stridex*-4] - p[stridex*-3]) <= T2 && abs(p[stridex*-3] - p1) <= T2 && abs(p1 - p0) <= T2
      && abs(p[stridex*3] - p[stridex*2]) <= T2 && abs(p[stridex*2] - n1) <= T2 && abs(n1 - n0) <= T2){
#endif

         if(_r*4>=T2){//28.27 || abs(p0-p[2*stridex])>T2 || abs(n0-p[-3*stridex])>T2){
            //_r>=iFS+2){
            //((tfa&1)&& _r*4>=T2)||//
            //(!(tfa&1) && _r >= iFS)){
            d1=((dif)*(3));
            int dp1n1=(p1-n1);//&((_r*2 > T2) * -1);
            d1-=dp1n1;
            d1+=4;d1>>=3;
            _s=d1>>31;
            d1=(d1+_s)^_s;
            T_FIX(d1,iFSD2);
            if(iClip){
               p[0]=_clip_uint8(p0-d1);//_clip_uint8
               p[-stridex]=_clip_uint8(n0+d1);
            }
            else{
               p[0]=(p0-d1);//_clip_uint8
               p[-stridex]=(n0+d1);
            }
            return 1;
         }
         //if(iCheckA)d1&=hev;
         if(iShort){//   || dif*dp1n1<4){// (!hev && !iCheckA)){//|| iFS<3){
               int dp1n1=(p1-n1);//&((_r*2 > T2) * -1);
               // if(iShort && dif*dp1n1<0)return 0;
               d1=((dif)*(3));
               d1-=dp1n1;
               //    _s=d1>>31;d1=(d1+_s)^_s;
               d1+=4;d1>>=3;
               //  _s=d1>>31;d1=(d1+_s)^_s;
               if(iShort==3 ){_s=d1>>31;d1=(d1+_s)^_s;T_FIX(d1,iFS);}
            if(iClip){
               p[0]=_clip_uint8(p0-d1);//_clip_uint8
               p[-stridex]=_clip_uint8(n0+d1);
            }
            else{
               p[0]=(p0-d1);//_clip_uint8
               p[-stridex]=(n0+d1);
            }

            return 1;//ret;  
         }

         {
            d1=((aDif)*(3));
            d1+=3;d1>>=3;
              
            d1=(d1+_s)^_s;
            if(iClip){
               p[0]=_clip_uint8(p0-d1);//_clip_uint8
               p[-stridex]=_clip_uint8(n0+d1);
            }
            else{
               p[0]=(p0-d1);//_clip_uint8
               p[-stridex]=(n0+d1);
            }

            //p[-stridex*3]=p[stridex*2]=240;
         }
         return 1;//ret;
   }
   //}
   return 0;
}
static inline int frT(unsigned char *p, const int stridex, const int a, const int T2){
   const int p0=p[0];
   const int n0=p[-stridex];
   //
   int _s;
   //   return (abs(p0 - n0)*2 + abs(p[stridex]-p[-2*stridex])/2)<a;

   const int dif=p0-n0;_s=dif>>31;const int aDif=(dif+_s)^_s;  if(aDif<2 || aDif>a)return 0;
   //  return 1;
   const int p1=p[stridex];
   const int n1=p[-2*stridex];
   int _r=p0+n0-(p1+n1);
   _s=_r>>31;_r=(_r+_s)^_s;

   return _r<=(T2+18);//((tfa&1)==0 &&  _r<=T2) || ((tfa&1) && testF(p,stridex,T2StatX*3));
}

   
static  void t_filter_LZ_s(unsigned char *p, const int stridex, const int stridey, const int L, int a,const int iCoefs, char *pC){
   int i,dif,f1; 
   //return ;
//   int _d,_d1,_d2,_s,iF,iFC,f1;
#if 1
   // const int T2=(a+fs1*10+12)>>4;//
   //   const int T2=fs1*2;//(a+fs1*8+12)>>3;//
   const int iFS=GETFS;
   int T2;//=iFS+3;//(*3+8)>>2;//(3*GETFS)>>1;//(a+fs1*8+12)>>3;//3215
   // const int T2=((GETFS*3)>>1);//(*3+8)>>2;//(3*GETFS)>>1;//(a+fs1*8+12)>>3;//3215
   //if(iCoefs==1) {}//a*=2;T2=iFS+2;

   //   T2=(20*9+iFS*23)>>5;
   //   T2=(((10+iFS*4)>>1));
   T2=T2Stat;//(((8+iFS*3)>>1));
   if(iCoefs!=3){T2+=iFS;a+=iFS*2;}//1+((iFS*3)>>1);
#if 1
   int T2B=T2Stat+2+iFS;
   int na=a+(T2Stat);//(a*3)>>1;
   if(tfb&1)
   {
      //T2=(T2Stat*5)>>3;
      T2=T2Stat+iFS;T2Stat=T2StatX;

      for(i=0;i<L;i+=4){
         if(iCoefs==0){
            dif=abs(p[0]-p[-stridex]);
            if((dif>1 && dif<a))
            {
               fr2<0,2,0>(p,stridex,stridey,a,0,T2);p+=stridey;
               fr2<0,2,0>(p,stridex,stridey,a,1,T2);p+=stridey;
               fr2<0,2,0>(p,stridex,stridey,a,1,T2);p+=stridey;
               fr2<0,2,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            }
            else p+=stridey*4;
         }else if(iCoefs==1){
            if(iFS<3){
               fr2<1,3,0>(p,stridex,stridey,a,0,T2Stat);p+=stridey;
               fr2<1,3,0>(p,stridex,stridey,a,1,T2Stat);p+=stridey;
               fr2<1,3,0>(p,stridex,stridey,a,1,T2Stat);p+=stridey;
               fr2<1,3,0>(p,stridex,stridey,a,0,T2Stat);p+=stridey;
            }
            else{
               fr2<0,0,0>(p,stridex,stridey,a,0,T2Stat);p+=stridey;
               fr2<0,0,0>(p,stridex,stridey,a,1,T2Stat);p+=stridey;
               fr2<0,0,0>(p,stridex,stridey,a,1,T2Stat);p+=stridey;
               fr2<0,0,0>(p,stridex,stridey,a,0,T2Stat);p+=stridey;
            }
         }else{
            //int ax=((a*3)>>2)+2;//+T2Stat;
            f1=frT(p,stridex,a,T2);
            f1+=frT(p+stridey,stridex,a,T2);
            //      f1=fc[0]+fc[1];
            if(f1<2){
               f1+=frT(p+stridey*2,stridex,a,T2);
               f1+=frT(p+stridey*3,stridex,a,T2);
            }
            if(f1>=2)
               /*
               int fc[4]={1,1,1,1};
               fc[0]=frT(p,stridex,a,T2);
               fc[1]=frT(p+stridey,stridex,a,T2);
               fc[2]=frT(p+2*stridey,stridex,a,T2);
               fc[3]=frT(p+3*stridey,stridex,a,T2);
               if((fc[0] && fc[3]) || (fc[1] && fc[3]) || (fc[0] && fc[2]))
               */
            {
               if(iFS<3){
                  fr2<1,3,0>(p,stridex,stridey,na,0,T2B);p+=stridey;
                  fr2<1,3,0>(p,stridex,stridey,na,1,T2B);p+=stridey;
                  fr2<1,3,0>(p,stridex,stridey,na,1,T2B);p+=stridey;
                  fr2<1,3,0>(p,stridex,stridey,na,0,T2B);p+=stridey;
               }
               else{
                  fr2<0,1,0>(p,stridex,stridey,na,0,T2B);p+=stridey;
                  fr2<0,1,0>(p,stridex,stridey,na,1,T2B);p+=stridey;
                  fr2<0,1,0>(p,stridex,stridey,na,1,T2B);p+=stridey;
                  fr2<0,1,0>(p,stridex,stridey,na,0,T2B);p+=stridey;



               }
            }
            else p+=stridey*4;
         }
      }

      return;
   }
#endif
   //if(T2>18)T2=18;
   //T2=iFSL;

   // T2+=8*3;T2>>=2;
   //if(T2>6){T2+=18;T2>>=2;}
   //T2=4;
   //a=80;
   // T2=16;
   // if(T2>12){T2+=12*3;T2>>=2;}
   /*
   for(i=0;i<L;i+=4){
   if(iCoefs==0){
   dif=abs(p[0]-p[-stridex]);
   if((dif>1 && dif<a))
   {
   fr2<0,2,0>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<0,2,0>(p,stridex,stridey,a,1,T2);p+=stridey;
   fr2<0,2,0>(p,stridex,stridey,a,1,T2);p+=stridey;
   fr2<0,2,0>(p,stridex,stridey,a,0,T2);p+=stridey;
   }
   else p+=stridey*4;
   }else if(iCoefs==2){
   int f1=fr2<1,1>(p,stridex,stridey,a,1,T2);p+=stridey;
   f1|=fr2<1,1>(p,stridex,stridey,a,0,T2);p+=stridey;
   if(f1){
   fr2<1,1>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,1>(p,stridex,stridey,a,1,T2);p+=stridey;
   }
   else p+=stridey*2;
   }
   else{
   int f1=fr2<1,0>(p,stridex,stridey,a,1,T2);p+=stridey;
   f1|=fr2<1,0>(p,stridex,stridey,a,0,T2);p+=stridey;
   if(f1){
   fr2<1,0>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,0>(p,stridex,stridey,a,1,T2);p+=stridey;
   }
   else p+=stridey*2;
   }
   }
   return;
   */
   /*
   for(i=0;i<L;i+=4){
   if(iCoefs==0){
   fr2<1,2>(p,stridex,stridey,a,1,T2);p+=stridey;
   fr2<1,2>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,2>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,2>(p,stridex,stridey,a,1,T2);p+=stridey;
   }else if(iCoefs==2 && dif){
   fr2<1,1>(p,stridex,stridey,a,1,T2);p+=stridey;
   fr2<1,1>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,1>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,1>(p,stridex,stridey,a,1,T2);p+=stridey;
   }
   else{
   fr2<1,0>(p,stridex,stridey,a,1,T2);p+=stridey;
   fr2<1,0>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,0>(p,stridex,stridey,a,0,T2);p+=stridey;
   fr2<1,0>(p,stridex,stridey,a,1,T2);p+=stridey;
   }
   }
   return;
   */
   for(i=0;i<L;i+=4){
      if(iCoefs==0){
         dif=abs(p[0]-p[-stridex]);
         if((dif>1 && dif<a))
         {
            fr2<0,2,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            fr2<0,2,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            fr2<0,2,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            fr2<0,2,0>(p,stridex,stridey,a,0,T2);p+=stridey;
         }
         else p+=stridey*4;

      }else if(iCoefs==1){

         if(iFS<3){
            fr2<1,3,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            fr2<1,3,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            fr2<1,3,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            fr2<1,3,0>(p,stridex,stridey,a,0,T2);p+=stridey;
         }
         else{
            fr2<0,0,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            fr2<0,0,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            fr2<0,0,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            fr2<0,0,0>(p,stridex,stridey,a,0,T2);p+=stridey;
         }
      }else if(iCoefs==2){

         if(iFS<3){
            f1=fr2<1,3,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            f1|=fr2<1,3,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            if(f1){
               fr2<1,3,0>(p,stridex,stridey,a,1,T2);p+=stridey;
               fr2<1,3,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            }
         }
         else{
            f1=fr2<0,1,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            f1|=fr2<0,1,0>(p,stridex,stridey,a,1,T2);p+=stridey;
            if(f1){
               fr2<0,1,0>(p,stridex,stridey,a,1,T2);p+=stridey;
               fr2<0,1,0>(p,stridex,stridey,a,0,T2);p+=stridey;
            }else p+=stridey*2;
         }
      }
      else {



         int px=(p[0]+p[stridey]+p[stridey*2]+p[stridey*3]);//>>2;
         p-=stridex;
         int nx=(p[0]+p[stridey]+p[stridey*2]+p[stridey*3]);//>>2;
         p+=stridex;
         //(s=x>>31;x=(x+s)^s;)*4  s4 x4 s4  if=4
         //sums=10 if 3 sh=1 

         dif=px==nx?(p[0]!=p[-stridex] || p[stridey]!=p[stridey-stridex]):(abs(px-nx)>>1);

         //dif=abs(p[0]-p[-stridex])+abs(p[stridey]-p[stridey-stridex])
         //      +abs(p[stridey*2]-p[stridey*2-stridex])+abs(p[stridey*3]-p[stridey*3-stridex]);
         ///dif>>=2;

         // dif=((p[0]-p[-stridex])|1)*((p[0+stridey*2]-p[-stridex+stridey*2])|1)*((p[stridey*3]-p[-stridex+stridey*3])|1)*((p[0+stridey*1]-p[-stridex+stridey*1])|1);
         // dif=sqrt(sqrt((double)dif));

         /*      //   dif=abs(p[0]-p[-stridex])+abs(p[stridey]-p[stridey-stridex])
         //    +abs(p[stridey*2]-p[stridey*2-stridex])+abs(p[stridey*3]-p[stridey*3-stridex]);
         unsigned int px=(unsigned int)p[0]|((unsigned int)p[stridey]<<8)|((unsigned int)p[stridey*2]<<16)((unsigned int)p[stridey*3]<<24);p-=stridex;
         unsigned int nx=(unsigned int)p[0]|((unsigned int)p[stridey]<<8)|((unsigned int)p[stridey*2]<<16)((unsigned int)p[stridey*3]<<24);p+=stridex;

         //#define OP_AVG_BIN(a, b, dst) dst = ( ((a)|(b)) - ((((a)^(b))&0xFEFEFEFE)>>1) )
         unsigned int dx=(px|nx)-(((px^nx)&0xFEFEFEFE)>>1);
         //dif>>=3;
         */
         //TODO wo dif check
         //dif=2;//3542
         //if(dif && dif<a)
         if(dif && dif<a){// && abs(nb1x-nb2x)<a && abs(pb1x-pb2x)<a){
            //      void debugsi(char*,int);debugsi("dif",dif);
            if(iFS<3){
               f1=fr2<0,3,0>(p,stridex,stridey,a,1,T2);p+=stridey*2;
               f1+=fr2<0,3,0>(p,stridex,stridey,a,0,T2);
               //    void debugsi(char*,int);debugsi("f1+f2=",f1+f2);
               if((f1==2)){
                  p-=stridey;
                  fr2<0,3,0>(p,stridex,stridey,a,0,T2);p+=stridey*2;
                  fr2<0,3,0>(p,stridex,stridey,a,1,T2);p+=stridey;//,iCoefs!=1
               }
               else p+=stridey*2;
            }
            else {
               f1=fr2<0,1,0>(p,stridex,stridey,a,1,T2);p+=stridey*2;
               f1|=fr2<0,1,0>(p,stridex,stridey,a,0,T2);
               //    void debugsi(char*,int);debugsi("f1+f2=",f1+f2);
               if((f1)){
                  p-=stridey;
                  fr2<0,1,0>(p,stridex,stridey,a,0,T2);p+=stridey*2;
                  fr2<0,1,0>(p,stridex,stridey,a,1,T2);p+=stridey;//,iCoefs!=1
               }
               else p+=stridey*2;
            }
         } 
         else {//p[stridex*-3]=p[stridex*2]=215;
            p+=stridey*4;}
      }

   }
   return;
#else
   for(i=0;i<L;i+=4){

      p+=stridey;
      *(int*)&pC[i]=0;
      _CMPF_d(1);
      p+=stridey;
      _CMPF_d(2);
      if(*(int*)&pC[i]){
         p+=stridey;
         _CMPF_d_wod(3);
         p-=stridey*3;
         _CMPF_d_wod(0);
         p+=stridey*4;
      }
      else{
         p+=stridey*2;
      }
   }
   //d=min(dif>>1,((al+3)>>2));
   p-=stridey*L;
   {
      const unsigned int iLS=(127-(a>>4)-3)*0x01010101;
      //const int al=t_l1[a];//((a+15)>>);
      //const int al=(a+12)>>3;
      const int fs2=(fs1+6)>>3;
      for(i=0;i<L;i+=4){

         iF=*(int*)&pC[i];
         if(iF){
            //  (*(int*)&pC[i])|=0x02020202;
            // unsigned y = ((iF & 0x7F7F7F7Fu)+0x7F7F7F7F)|iF;
            //unsigned y = iF + 0x7F7F7F7F;
            if(iCoefs==1 && fs2 && (((unsigned)iF + 0x7F7F7F7F) & 0x80808080u) == 0x80808080u){
               //int fso=fs1;fs1++;fs1>>=1;
               if(pC[i])_FLLL(0,0);p+=stridey;
               if(pC[i+1])_FLLL(1,1);p+=stridey;
               if(pC[i+2])_FLLL(2,0);p+=stridey;
               if(pC[i+3])_FLLL(3,1);p+=stridey;
               //fs1=fso;

            }
            else 
               if (!fs2 || iCoefs==2 ||
                  ((((unsigned)iF + iLS) & 0x80808080u) ) || 
                  (((unsigned)iF + 0x7F7F7F7F) & 0x80808080u) != 0x80808080u){
                     if(pC[i])_FL(0,0);p+=stridey;
                     if(pC[i+1]){_FL(1,1);}p+=stridey;
                     if(pC[i+2]){_FL(2,0);}p+=stridey;
                     if(pC[i+3])_FL(3,1);p+=stridey;
               }
               else{

                  _FL(0,0);p+=stridey;
                  _FLLL(1,1);p+=stridey;
                  _FLLL(2,0);p+=stridey;
                  _FL(3,1);p+=stridey;
               }
         }
      }
   }
#endif
}

#undef _FL


#else
#define t_filter_LZ_s t_filter_LZ

static  void t_filter_LZ(unsigned char *p, const int stridex, const int stridey, const int L, int a, int iPass, char *pC){
   int i; 
   /*
   >>TODO 
   if(*(int*)p==*(int*)&p[-stridey]){return;}
   if(*(int*)p==*(int*)&p[stridex] && *(int*)&p[-stridex]==*(int*)&p[-2*stridex] && abs(p[0]-p[-stridex])>1 && abs(p[0]-p[-stridex])<a)){
   int r=( ((a)|(b)) - ((((a)^(b))&0xFEFEFEFE)>>1) );//fast avg
   *(int*)p=*(int*)&p[-stridex]=r;
   }
   <<TODO
   */
   //const int ad3=(a>>2)+2;//((a*80)>>4)+1;

   /*
   a = src[-2*stride];
   b = src[-stride];
   c = src[0];
   d = src[stride];

   dif=abs(b-c);
   if(dif<al) {
   if(abs(d-c)<be && abs(a-b)<be){
   d1 = (a - d + 3 + rnd) >> 3;
   d2 = (a - d + b - c + 4 - rnd) >> 3;

   src[-2*stride] = a - d1;
   src[-stride] = _clip_uint8(b - d2);
   src[0] = _clip_uint8(c + d2);
   src[stride] = d + d1;
   }
   }
   src+=3;

   */
   //11 45
   //1739

#define _CMPFz p[-stridex]!=p[0] && (p[0]==p[2*stridex] || p[-stridex]==p[-3*stridex])  &&\
   (p[0]==p[stridex] || t_abs(p[stridex]-p[0])<=ad3) &&  \
   (p[-stridex]==p[-2*stridex] || t_abs(p[-stridex]-p[-2*stridex])<=ad3) \
   &&  t_abs(p[-stridex]-p[0])<=a  

#define _CMPF_t p[-stridex]!=p[0] && ((a1=t_abs(p[stridex]-p[0]))<=ad3 && (a2=t_abs(p[-stridex]-p[-2*stridex]))<=ad3 \
   &&  (a3=t_abs(p[-stridex]-p[0]))<=a && a3>a1+a2)    

#define _CMPF ((p[-stridex]!=p[0] && (t_abs((p[-stridex]+p[0])-(p[stridex]+p[-2*stridex]))<ad3 && t_abs(p[stridex]-p[0])<=ad3 && t_abs(p[-stridex]-p[-2*stridex])<=ad3 \
   &&  (t_abs(p[-stridex]-p[0]))<=a)))

   if(iPass==1){
      int a1,a2,a3;
      //alphaTab
      int ad3=((a*80)>>8)+1;
      if(ad3>9){ad3+=27;ad3>>=2;}
      //if(ad3>8){ad3+=56;ad3>>=3;}
      // for(int k=8;k<52;k++)if(alphaTab[k]==a)ad3=betaTab[k];
      for(i=0;i<L;i+=4){
#define _CF(_O) \
   pC[i+_O]=_CMPF;

         p+=stridey;
         *(int*)&pC[i]=0;
         _CF(1);
         p+=stridey;
         _CF(2);
         if(*(int*)&pC[i]){
            p+=stridey;
            _CF(3);
            p-=stridey*3;
            _CF(0);
            p+=stridey*4;
         }
         else{
            p+=stridey*2;
         }
      }
   }
   else{
      int r=0;
      for(i=0;i<L;i+=4){
         //a1 a0 b0 b1
         //                  if(p[-stridex]<p[0]){p[1]+=r;p[-2*stridex]-=r;}else{p[1]-=r;p[-2*stridex]+=r;} 
         //         if(p[-stridex]+7<p[0]){p[stridex]++;p[-2*stridex]--;}else if(p[-stridex]>7+p[0]){p[stridex]--;p[-2*stridex]++;} \
         //57384

#define _FLd {\
   const int p0=p[0];\
   p[0]=((p0*12+p[-stridex]*5-p[stridex]+8-r))>>4;\
   p[-stridex]=((p[-stridex]*12+p0*5-p[-2*stridex]+7+r))>>4;  \
   r=!r;}

#define _FL__ {\
   const int d=((p[0]-p[-stridex])*4+(p[-2*stridex]-p[stridex])+3+r)>>3;\
   p[0]-=d;\
   p[-stridex]+=d;\
   r=!r;}

#define _FL000 {\
   const int p0=p[0];\
   p[0]=((p0*5+p[-stridex]*4-p[-2*stridex]+4-r))>>3;\
   p[-stridex]=((p[-stridex]*5+p0*4-p[stridex]+3+r))>>3;  \
   r=!r;}

#define _FLaa {\
   const int p0=p[0];\
   p[0]=((p0*2+p[-stridex]+p[stridex]+2-r))>>2;\
   p[-stridex]=((p[-stridex]*2+p0+p[-2*stridex]+1+r))>>2;  \
   r=!r;}

#define _FLg {\
   const int p0=p[0];\
   p[0]=((p0+p[-stridex]+p[stridex]*2+2-r))>>2;\
   p[-stridex]=((p[-stridex]+p0+p[-2*stridex]*2+1+r))>>2;  \
   r=!r;}


#define _FLZ {\
   const int d=abs(p[-stridex]-p[0]); \
   if(d>1){\
   if(p[-stridex]>p[0]){p[0]+=t_l1[d];p[-stridex]-=t_l1[d];p[stridex]+=1;p[-stridex*2]-=1;}\
else {p[0]-=t_l1[d];p[-stridex]+=t_l1[d];p[stridex]-=1;p[-stridex*2]+=1;}\
   }\
      }

         /*
         const int p0=p[0];\
         const int p1=p[stridex];\
         const int n0=p[-stridex];\
         const int n1=p[-2*stridex];\
         p[0]=(n1+n0+(p1<<1)+(p0<<2)+4)>>3;\
         p[-stridex]=((n0<<2)+(n1<<1)+p1+p0+4)>>3;\

         *///;
#if 0
         //def T_CAN_TEST_V

#define _FL {\
   const int d=t_abs(p[-stridex]-p[0]); \
   if(d>1){\
   const int fc=t_l1[d];\
   if(p[-stridex]>p[0]){p[0]+=fc;p[-stridex]-=fc;}else {p[0]-=fc;p[-stridex]+=fc;}\
   }\
      }
#else
#define _FL {\
   const int d=t_abs(p[-stridex]-p[0]); \
   if(d>1){\
   const int fc=p[stridex*2]==p[stridex] && p[-stridex]==p[-stridex*2] ?(d>>1): t_l1[d];\
   if(p[-stridex]>p[0]){p[0]+=fc;p[-stridex]-=fc;}else {p[0]-=fc;p[-stridex]+=fc;}\
   }\
      }

#endif
         //ab cd
         // (a+c+b*2)-b*4+(b+d+c*2)-c*4=a-b+d-c

         //diff = (((orig[4]-orig[3])<<2) + (orig[2]-orig[5]) + 4) >> 3;
         //diff = (((orig[4]-orig[3])<<2) + (orig[2]-orig[5]) + 4) >> 3;


         int iF=*(int*)&pC[i];
         if(iF){
            /*
            if(_CMPF)_FL;p+=stridey;
            if(pC[i+1])_FL;p+=stridey;
            if(pC[i+2])_FL;p+=stridey;
            if(_CMPF)_FL;p+=stridey;
            */
            /*
            #define _FLXSTR(_I) if(pC[i+_I])_FL(pC[i+_I]);p+=stridey;
            _FLXSTR(0);
            _FLXSTR(1);
            _FLXSTR(2);
            _FLXSTR(3);
            */

            if(pC[i])_FL;p+=stridey;
            if(pC[i+1])_FL;p+=stridey;
            if(pC[i+2])_FL;p+=stridey;
            if(pC[i+3])_FL;p+=stridey;

         }
         else p+=stridey*4;

         //p+=stridey;
      }
   }
}
#endif
static  void t_filter_L_D(unsigned char *p, const int stridex,const int stridey, const int L, int a, int rem){
   int i; 
   // int ad3=(a>>2)+1;
   ///if(ad3>9){ad3+=27;ad3>>=2;}
   for(i=0;i<L;i++){
      //1256  12 56 
      //2547
      const int dif=p[-stridex]-p[0];
      const int d=t_abs(dif); 
      if(d>1 && d<a){
         //int mi=min(p[0],min(p[-stridex],min(p[-2*stridex],p[stridex])));
         //int ma=max(p[0],max(p[-stridex],max(p[-2*stridex],p[stridex])));
         //if(t_lim_2(dif+(p[-2*stridex])-(p[-stridex]),-1,ad3))
         //if(ad3>t_abs(p[-stridex]-p[-stridex*2]) && ad3>t_abs(p[stridex]-p[0]))
         //if(1<t_abs(dif+p[-stridex*2]-p[stridex]))
         if(1<t_abs(dif+p[-stridex*2]-p[stridex]))
         {
            if(dif>0){p[0]+=rem;p[-stridex]-=rem;}
            else {p[0]-=rem;p[-stridex]+=rem;}
         }
      }

      p+=stridey;
   }
}
/*
if(_d>1 && _d<a){ \
_r=p[0]-p[stridex]-p[-2*stridex]+p[-stridex];\
_s=_r>>31;_r^=_s;\
if(_r*2<=a){pC[i+_O]=_d;} else pC[i+_O]=0;\
}else pC[i+_O]=0;

*/
#define _F2x2_1(c1,c0,f0,f1) if((unsigned)(d+b)<(b<<1) && (unsigned)(c0-f0+a)<(a<<1) )f0+=(c0-f0+2)>>2;
#define _F2x2_2(c1,c0,f0,f1) if((unsigned)(d+b)<(b<<1) && (unsigned)(c0-f0+a)<(a<<1) )c0-=(c0-f0+4)>>3;
//#define _F2x2_1(c1,c0,f0,f1) if((unsigned)(d+b)<(b<<1) && (unsigned)(c0-f0+a)<(a<<1) )f0+=(c0-f0+2)>>2;
//#define _F2x2_2(c1,c0,f0,f1) if((unsigned)(d+b)<(b<<1) && (unsigned)(c0-f0+a)<(a<<1) )c0-=(c0-f0+4)>>3;

//#define _F2x2_1(c1,c0,f0,f1) if((unsigned)(d+(b>>1))<b && (unsigned)(c0-f0+(a>>1))<a)f0+=(c0-f0+2)>>2;
//#define _F2x2_2(c1,c0,f0,f1) if((unsigned)(d+(b>>1))<b && (unsigned)(c0-f0+(a>>1))<a)c0-=(c0-f0+4)>>3;
//#define _F2x2_1(c1,c0,f0,f1) if((unsigned)(d+(b>>1))<b && (unsigned)(f1-f0+(b>>1))<b && (unsigned)(c0-f0+(a>>1))<a)f0+=(c0-f0+2)>>2;
//#define _F2x2_2(c1,c0,f0,f1) if((unsigned)(d+(b>>1))<b && (unsigned)(f1-f0+(b>>1))<b &&  (unsigned)(c0-f0+(a>>1))<a)c0-=(c0-f0+4)>>3;
//#define _F2x2_1(c1,c0,f0,f1) if((unsigned)(d+(b>>1))<b && (unsigned)(f1-f0+(b>>1))<b && (unsigned)(c0-f0+(a>>1))<a)f0+=  clipZ(a>>2, (((c0-f0)*3) + (f1-c1) + 4) >> 3);;
//#define _F2x2_2(c1,c0,f0,f1) if((unsigned)(d+(b>>1))<b && (unsigned)(f1-f0+(b>>1))<b &&  (unsigned)(c0-f0+(a>>1))<a)c0-= clipZ(a>>2, (((c0-f0)*3) + (f1-c1) + 4) >> 3);;
//#define _F2x2_1(c1,c0,f0,f1) if((unsigned)(c0-f0+a)<(a<<1) && abs(f0-f1-c1+c0)*2<=a)f0+=  clipZ(b, (((c0-f0)*3) + (f1-c1) + 4) >> 3);;
//#define _F2x2_2(c1,c0,f0,f1) if((unsigned)(c0-f0+a)<(a<<1) && abs(f0-f1-c1+c0)*2<=a)c0-=  clipZ(b, (((c0-f0)*3) + (f1-c1) + 4) >> 3);;
//#define _F2x2_1(c1,c0,f0,f1) if((unsigned)(d+b)<(b<<1) && (unsigned)(c0-f0+a)<(a<<1) )f0+=(((c0-f0)*3) + (f1-c1) + 4) >> 3;
//#define _F2x2_2(c1,c0,f0,f1) if((unsigned)(d+b)<(b<<1) && (unsigned)(c0-f0+a)<(a<<1) )c0-=(((c0-f0)*3) + (f1-c1) + 4) >> 3;
void flXL(unsigned char *dst, int stride, int a, int b){
   int d;
   d=dst[0]-dst[3];

   //if((unsigned)(d+(b>>1))<b && (unsigned )(dst[3]-dst[6]+(a>>1))<a)dst[6]+=(dst[3]-dst[6]+2)>>2;//(dst[3]>dst[6]?1:-1);
   _F2x2_1(dst[0],dst[3],dst[6],dst[9]);
   dst+=stride;

   d=dst[0]-dst[3];
   //if((unsigned)(d+(b>>1))<b && (unsigned )(dst[3]-dst[6]+(a>>1))<a)dst[3]-=(dst[3]-dst[6]+2)>>2;//(dst[3]>dst[6]?1:-1);
   _F2x2_2(dst[3],dst[0],dst[-3],dst[-6]);
}

void flXR(unsigned char *dst, int stride, int a, int b){
   int d;
   d=dst[0]-dst[3];
   _F2x2_1(dst[3],dst[0],dst[-3],dst[-6]);
   dst+=stride;

   d=dst[0]-dst[3];
   _F2x2_2(dst[3],dst[0],dst[-3],dst[-6]);
}
void flXT(unsigned char *dst, int stride, int a, int b){
   int d;
   d=dst[0]-dst[stride];
   _F2x2_1(dst[stride],dst[0],dst[-stride],dst[-2*stride]);
   dst+=stride;

   d=dst[0]-dst[stride];
   _F2x2_2(dst[stride],dst[0],dst[-stride],dst[-2*stride]);
}
void flXB(unsigned char *dst, int stride, int a, int b){
   int d;
   d=dst[0]-dst[stride];
   _F2x2_1(dst[0],dst[stride],dst[2*stride],dst[3*stride]);
   dst+=stride;

   d=dst[0]-dst[stride];
   _F2x2_2(dst[0],dst[stride],dst[2*stride],dst[3*stride]);
}

void t_filter2x2(unsigned char *dst, int stride, int iX, int a, int b){
   int i;
   if(a<4)return;
   int v=1<<16;
   for(i=0;i<4;i++,dst+=stride*2){
      //     if(i!=3 && !(iX&(v>>4)) && (iX&(v)))flXB(dst,stride,a,b);
      //   if(i && !(iX&(v<<4)) && (iX&(v)))flXT(dst,stride,a,b);
      v>>=1;
      if((iX&(v<<1)) && !(iX&(v)))flXL(dst,stride,a,b);
      // if(i!=3 && !(iX&(v>>4)) && (iX&(v)))flXB(dst,stride,a,b);
      //if(i && !(iX&(v<<4)) && (iX&(v)))flXT(dst,stride,a,b);

      ;v>>=1;

      if(!(iX&(v<<2)) && (iX&(v<<1)))flXR(dst+6,stride,a,b);
      if((iX&(v<<1)) && !(iX&(v)))flXL(dst+6,stride,a,b);
      //if(i!=3 && !(iX&(v>>4)) && (iX&(v)))flXB(dst,stride,a,b);
      //if(i && !(iX&(v<<4)) && (iX&(v)))flXT(dst,stride,a,b);

      v>>=1;
      if(!(iX&(v<<2)) && (iX&(v<<1)))flXR(dst+12,stride,a,b);
      if((iX&(v<<1)) && !(iX&(v)))flXL(dst+12,stride,a,b);
      //if(i!=3 && !(iX&(v>>4)) && (iX&(v)))flXB(dst,stride,a,b);
      //if(i && !(iX&(v<<4)) && (iX&(v)))flXT(dst,stride,a,b);

      v>>=1;
      if(!(iX&(v<<2)) && (iX&(v<<1)))flXR(dst+18,stride,a,b);
   }

}

//#define t_filter_L_TL t_filter_L_TLUV
#if 0
int frn(unsigned char *p, const int stridex, const int stridey, const int a,  const int L){
   const int dif=p[0]-p[-stridex];
   const int _s=dif>>31;
   const int d=(dif+_s)^_s; 

   if(d>1 && d<a){
      int i,_s2,_r; 
      const int p0=p[0];
      const int p1=p[stridex];
      const int n0=p[-stridex];
      const int n1=p[-2*stridex];
      _r=p0+n0-(p1+n1);
      _s2=_r>>31;_r^=_s2;
      int fs=(((n0-p0)*3) + (p1-n1) + 2) >> 2;
      if(_r+1<=d && fs &&  _r+(d>>1)<=a)
      {


         //int st=p[stridex*z];z++;
         int iLC=0;

         const int a2=((a+8)>>2)>(d>>1)?(d>>1):((a+8)>>2);
         if(d<=a2+4){
            int z=1;
            const int pn0=p0+n0+1;
            int fss=(d+(a>>3)+2)>>2;
            int fs2=fss;
            // int ss=dif<0?-1:1;
            for(;z<(L) && abs(p0-p[stridex*(z+1)])<=a2;z++){
               //               p[stridex*z]+=clipZ(fs2,((pn0-p[stridex*z]*4+p1+p[stridex*(z+1)])>>2));
               p[stridex*z]+=clipZ(fs2,((pn0-p[stridex*z]*3+p[stridex*(z+1)])>>2));
               //fs2+=2;fs2>>=2;if(!fs2){fs2=1;z++;}
               //fs2>>=1;if(!fs2){fs2=1;z++;}
               fs2++;fs2>>=1;
               iLC|=1;

            }
            fs2=fss;
            z=2;
            // st=p[-stridex*z];z++;
            //         for(;z<(L+1) && (abs(n1-p[-stridex*(z)])+abs(p[-stridex*(z)]-p[-stridex*(z+1)])<=a2);z++){
            //         for(;z<(L+1) && (abs(n1+p[-stridex*(z+1)]-(n0+p[-stridex*(z)]))<=a2);z++){
            for(;z<(L+1) && abs(n0-p[-stridex*(z+1)])<=a2;z++){
               p[-stridex*z]+=clipZ(fs2,(pn0-p[-stridex*z]*3+p[-stridex*(z+1)])>>2);
               //int difx=(pn0-p[-stridex*z]*3+p[-stridex*(z+1)])>>2;
               //p[stridex*z]+=difx;
               //fs2+=2;fs2>>=2;if(!fs2){fs2=1;z++;}
               //fs2>>=1;if(!fs2){fs2=1;z++;}
               fs2++;fs2>>=1;
               iLC|=2;
            }
            //444466666 10-24+6+6+2
         }

         if(iLC==0){
            const int diff =  clipZ((a>>3)+1, fs);\
               if(diff){\
                  p[0]=(p0+diff);\
                  p[-stridex]=(n0-diff);\
               }
         }
         else{
            const int diff =  clipZ(d>>1, fs);\
               if(diff){\
                  p[0]=(p0+diff);\
                  p[-stridex]=(n0-diff);\
               }
         }         

         return 1;
      }
   }
   return 0;
}
#endif
/*
void inc4x_s3(unsigned char *pCur, int iAdd, int stride,  int *dc){
void __incBlock4x(int *pCur, int stride);
int i,j;
int p[256];
int dc_v,r;
// int c=40;

for(j=0;j<16;j+=4){
for(i=0;i<16;i+=4){
if(iAdd){
if(dc[0]<-10)dc_v=-((7-dc[0])>>4);
else if(dc[0]>10)dc_v=((7+dc[0])>>4);
else dc_v=0;
dc_v+=128;
}else{
dc_v=dc[0]+128*2*16+16; dc_v>>=5;

}


//p[j*16+i]=c;c+=10;//dc_v;
p[i*16+j]=dc_v;
dc++;
}
}
__incBlock4x(p,16);

for(j=0;j<16;j++){
for(i=0;i<16;i++){

if(iAdd){r=pCur[i*3]+p[j*16+i]-128;}else r=p[j*16+i];
if(r<0)r=0;else if(r>255)r=255;
pCur[i*3]=r;
}
pCur+=stride;
}

}
*/
//static  
template<int iClip>
void t_filter_L_TL(unsigned char *p, const int stridex, const int stridey, const int L, const int a,const  int iLongF){
   int i,_s2,_r; 

 //  int tf=0;
#ifdef T_CAN_TEST_V

   const int str=GETFS;//fsTab[a];//(a+2)>>2;//a div 6
   if(!str)return ;
   //   const int T2=(a+str*12+12)>>3;//t2?t2:1;
   //== 
   //t_filter_LZ_s(p,stridex,stridey,L,a,3,0);return;
   int T2=str+3;
   //int T2=(str*7+20)>>3;//(a+str*12+12)>>3;//t2?t2:1;
   //int T2=(str*20+20*12)>>5;//(a+str*12+12)>>3;//t2?t2:1;
   //if(T2>12){T2+=12*3;T2>>=2;}
   int FF=0,f1,f2=0;;
  // int T2B=str*2;
   for(i=0;i<L;i+=2){
#if 1

      if(str<3){
         f1=fr2<1,3,iClip>(p,stridex,stridey,a,FF,T2);p+=stridey;
         f2=fr2<1,3,iClip>(p,stridex,stridey,a,!FF,T2);p+=stridey;
      }
      else{
         f1=fr2<2,0,iClip>(p,stridex,stridey,a,FF,T2);p+=stridey;
         f2=fr2<2,0,iClip>(p,stridex,stridey,a,!FF,T2);p+=stridey;
      }
      if(!f1 && !f2){i+=2;p+=stridey*2;FF=!FF;}
#else
      p+=stridey;
      int f1=fr2<1,0>(p,stridex,stridey,a,0,T2);p+=stridey;
      int f2=fr2<1,0>(p,stridex,stridey,a,1,T2);
      if(f1 || f2){
         p-=stridey*2;
         f1=fr2<1,0>(p,stridex,stridey,a,0,T2);p+=stridey*3;
         f2=fr2<1,0>(p,stridex,stridey,a,1,T2);p+=stridey;
      }
      else p+=stridey*2;
      i+=2;
#endif
   }

   return;
#endif
   /*
   frn(p,stridex,stridey,a,2);p+=stridey;
   frn(p,stridex,stridey,a,3);p+=stridey;
   frn(p,stridex,stridey,a,3);p+=stridey;
   frn(p,stridex,stridey,a,4);p+=stridey;
   frn(p,stridex,stridey,a,4);p+=stridey;
   frn(p,stridex,stridey,a,5);p+=stridey;
   frn(p,stridex,stridey,a,5);p+=stridey;
   return;
   */
#ifdef T_CAN_TEST_V
   int iPrevFiltred=1,tmp0,tmp1,tmp10,tmp11;
   int freqP[4];
   int freqN[4];


#else
   int ad3=(a>>2)+2;
   if(ad3>9){ad3+=27;ad3>>=2;}
   const int ad3x8=ad3*6;
#endif

   for(i=0;i<L;i++){

#ifdef T_CAN_TEST_V
      const int dif=p[0]-p[-stridex];
#else
      const int dif=p[-stridex]-p[0];
#endif
      const int _s=dif>>31;
      const int d=(dif+_s)^_s; 
      //4853
      /*
      int f;

      if(d>1 && d<a){ \
      const int _d1=p[0]-p[stridex];\
      const int _d2=p[-stridex]-p[-2*stridex];\
      if((t_abs(_d1+_d2)<<1)<d && (t_abs(_d1)+t_abs(_d2))<d)\
      {f=1;} else f=0;\
      }else f=0;
      */
#ifdef T_CAN_TEST_V
      if(d>1 && d<a)
#else
      if(d>1 && d<a)
#endif
      {

         const int p0=p[0];
         const int p1=p[stridex];
         const int n0=p[-stridex];
         const int n1=p[-2*stridex];
#ifdef T_CAN_TEST_V



         //n1+p1 -(n0+p0)
         //         FR(freqP,n1,n0,p0,p1);
         //1 3 4 5, 22 33    1919
         //const int ad3x=d;//ad3+iPrevFiltred;
         //const int ad3x=(a>>2)+2+iPrevFiltred-d;//-d;
         /*
         */
         //--

         //_r=((n1-p1))+dif;_s2=_r>>31;_r^=_s2;
         _r=(p0+n0)-(p1+n1);_s2=_r>>31;_r^=_s2;
         int fr;;
         //+4 ) >> 3
         //_r<=d+str && _r+(d>>1)<=T2 &&
         //_r*2+(d>>2)<=a &&
         //testF(p,stridex,str*2) &&
         if(_r<=T2 && 
            ((fr=((dif)*(3) + ((n1)-(p1)))) +2 )>>2 
            //&&
            //testTL(p,stridex,str*2)
            //&&   abs(p0+n0-(p1+n1))<=fsTab[a]
            )
            //if((((n0-p0)*3) + (p1-n1) + 3) >> 3 && t_lim_m(((p1+n1)-(p0+n0))*2,a))
         {
            //fr=((dif)*3) + ((n1)-(p1));

            //1234 

            //tmp[2]=(a1+a4)-(a2+a3)



            //TODO test freq n0+n1 n0-n1 n2+n3 n2-n3 
            //if(adif_f1<a adif_f2<ad3 && (af3_2|af3_1|af4_1|af4_2)<ad3)
            const int p2=p[2*stridex];
            const int p3=p[3*stridex];
            //const int p4=p[4*stridex];
            const int n2=p[-3*stridex];
            const int n3=p[-4*stridex];
            //const int n4=p[-5*stridex];

            //const int p5=p[5*stridex];
            //const int n5=p[-6*stridex];

            //            const int ca=(a-d);
            int iLF=0;
            //

            //int iFSkip=0;
            // if(ca>2 && iPrevFiltred){
            if(iPrevFiltred && d<=T2){
               FR10(freqP,p0,p1,p2,p3);
               FR10(freqN,n0,n1,n2,n3);
               //&& abs(freqP[2]+freqN[2])*2<ca
               //if(abs(freqP[1]-freqN[1])*4<a+8 ){
               if(freqN[0]!=freqP[0] && abs(freqP[1]-freqN[1])<=T2*2  ){
                  /*
                  int iFR;

                  iFR=(freqP[3]^(freqP[3]>>31));
                  iFR|=(freqP[2]^(freqP[2]>>31));
                  iFR|=(freqN[3]^(freqN[3]>>31));
                  iFR|=(freqN[2]^(freqN[2]>>31));
                  */
                  iLF=1;//(iFR)<T2;
                  //       iLF=1;
                  //iLF=(sx2|sx4)<ca;
                  //10 -10 20 -10
                  // iFSkip=(freqN[1]-freqN[0]-freqP[1] +2)>>2;
               }
            }

            //            
            //          
            //const int ap=t_abs(p01-p2-p4);
            //const int an=t_abs(n01-n2-n4);
            //if(!iFSkip)

            if(iLF){
               // iFR>>=1;
#if 1
               //int fm=d>>1;//(d*25+20)>>6;//               fm=d>>1;
               int iFS=min(d,str);//((fsTab[a]+1)>>1));

               //               const int diff2 =   (((n0+n1-p1-p0)*8) + (p3+p2-n3-n2) + 16) >> (5);

               //const int diff3 =   (((p0-n0)*8) + (n1+n2-p1-p2) + 4) >> (3);
               //--
               //const int diff2 =   (((p0-n0)*20) + (freqN[0]-freqP[0]) + 16-(i&1)) >> (5);
               const int diff2 =   ( (p1-n1) + 4-(i&1)) >> (3);
               //const int diff2 =   ( (freqP[0]-freqN[0]) + 16-(i&1)) >> (5);
               //const int diff1 =   (((p0-n0)*8) + (n1+n2-p1-p2) + 4) >> (3);
               //const int diff2 =   (((p0+p2-n2-n0)*8) + (n3+n1-p3-p1) + 7) >> (4);
               //const int diff2 =   (((p0+p1-n1-n0)*8) + (n3+n2-p3-p2) + 8) >> (4);
               const int diff1 =  (fr + 3+(i&1)) >> 3;
               /*
               const int spn1=p1-n1;\
               const int diff2 = clipZ((fs1+3)>>2,(spn1 + 4 - _F) >> 3);\
               */
               //  const int diff=(diff1+diff2+1)>>1;


               const int fa2=clipZ((iFS+7)>>3,diff2);
               //                  const int fa1=clipZ(iFS+!!fa2,(diff2+diff1*7+4)>>3);
               const int fa1=clipZ(iFS+!!fa2,diff1);


               p[stridex]=(p1-fa2);
               p[0]=(p0-fa1);//_clip_uint8
               p[-stridex]=(n0+fa1);
               p[-stridex*2]=(n1+fa2);
#else         

               const int iT=freqN[0]>freqP[0];
               freqP[0]+=iT*(5);
               int p01=p0+p1+2;//iT*2;
               int n01=n0+n1+2;// -iT*2;
               p[0]=(n1+(n0+p01)*2+p2)>>3;
               //p[stridex]=(n0+(p01+p2)*2+p3)>>3;
               p[stridex]=(freqP[0]*5+p1*7+n0+freqN[0])>>5;

               //-------p[stridex*3]=(freqP[0]+p3*11+n0)>>4;
               //if(iLongF)p[stridex*2]=(freqP[0]+p2*4)>>3;
               //p[stridex*2]=(p0+(p1+p2+p3)*2+p4+1)>>3;
               // if(iLongF)p[stridex*2]=(p01+(p2+p3)*3)>>3;//p[stridex*2]=(freqP[0]+p2*4)>>3;

               //if(freqN[0]<freqP[0])freqN[0]-=8;
               freqN[0]+=(!iT)*5;
               p[-stridex]=(p1+(p0+n01)*2+n2)>>3;

               p[-stridex*2]=(freqP[0]+n1*7+p0+freqN[0]*5)>>5;
               if(iPrevFiltred==2){
                  p[stridex*2]=(freqP[0]+p2*4)>>3;
                  p[-stridex*3]=(freqN[0]+n2*4)>>3;
               }

#endif                 
               //                  p[stridex*2]+=clipZ(1,((n0+p0)-p2*3+p3+2)>>2);
               //                p[-stridex*3]+=clipZ(1,((n0+p0)-n2*3+n3+2)>>2);

               //-------p[-stridex*4]=(freqN[0]+n3*11+p0)>>4;
               //p[-stridex*2]=(p0+(n01+n2)*2+n3)>>3;
               //p[-stridex*3]=(n0+(n1+n2+n3)*2+n4+1)>>3;
               //if(iLongF)p[-stridex*3]=(freqN[0]+n2*4)>>3;
               // if(iLongF)p[-stridex*3]=(n01+(n2+n3)*3)>>3;
               //--if(iLongF)p[-stridex*3]=(freqN[0]+n2*4)>>3;
               //p[stridex*3]=240;            p[-stridex*4]=240;
               iPrevFiltred=2;
               //8+2*4-4
               //p[-stridex*4]=1;   p[stridex*3]=1;

            }
            else{
               if(1){
                  int iFS=min(d>>1,str);
                  //p[-stridex*4]=200;p[stridex*3]=200;

                  const int diff =  clipZ(iFS,(fr + 4) >> 3);
                  p[0]=(p0-diff);
                  p[-stridex]=(n0+diff);
               }
               else{
                  //               p[-stridex]=((p0+n0*2+n1)+(p0-p1)+2)>>2;
                  //             p[0]=((n0+p0*2+p1)+(n0-n1)+2)>>2;
                  const int s00=(p0+n0)*2+2;
                  int dd=n1-p1;
                  //dd=clipZ(d-str,dd);
                  //if(dd*2-str<-d)dd=(d-str)>>;1
                  p[-stridex]=(s00+dd)>>2;
                  p[0]=(s00-dd)>>2;
               }
               iPrevFiltred=1;

            }

            //p[stridex*3]=iLF?10:240;            p[-stridex*4]=iLF?10:240;




         }
         else {
            if(!iPrevFiltred){
               i++;p+=stridey;
               iPrevFiltred=1;
            }else iPrevFiltred=0;
         }

#else

         int bx;//=(p1+n1)-(p0+n0);
         //1278
         //if(bx<d)
         if(((n0+p1)>>1)!=p0 && ((p0+n1)>>1)!=n0 && t_lim_2(bx=(p1+n1)-(p0+n0),-d,ad3))//23938,151192
            //if(t_lim_2(bx,-d,d))//23908
         {


            //TODO get blockDC if key
            const int dcp=p0+p1+p[2*stridex];
            const int dcn=n0+n1+p[-3*stridex];

            const int b12=t_abs(dcp-dcn);
            if(b12<ad3x8)
            {
               //bx=t_abs(bx);
#ifdef T_CAN_TEST_V
               //res=((iCur*7 + iPrev*3)+4-iPrevPrev-iNext)>>3;
               //res=((iCur*7 + iNext*3)+4-iPrev-iNextNext)>>3;
               // if(bx<0)bx=-bx;

               const int fc=(bx+t_l1[d]*4)>>2;//t_l1[d];
               //const int fc=t_l1[d];
               const int fc2=(fc+1)>>1;//
               const int fc3=(fc2+1)>>1;
               if(dif>0){p[0]+=fc;p[-stridex]-=fc;  p[stridex]+=fc2;p[-stridex*2]-=fc2; p[stridex*2]+=fc3;p[-stridex*3]-=fc3;}//p[stridex*2]+=fc3;   p[-stridex*3]-=fc3;}
               else {    p[0]-=fc;p[-stridex]+=fc;  p[stridex]-=fc2;p[-stridex*2]+=fc2; p[stridex*2]-=fc3;p[-stridex*3]+=fc3;}//p[stridex*2]-=fc3;   p[-stridex*3]+=fc3;}
               //                if(dif>0){ p[stridex]+=fc2;p[-stridex*2]-=fc2; p[stridex*2]+=fc3;p[-stridex*3]-=fc3;}//p[stridex*2]+=fc3;   p[-stridex*3]-=fc3;}
               //              else {     p[stridex]-=fc2;p[-stridex*2]+=fc2; p[stridex*2]-=fc3;p[-stridex*3]+=fc3;}//p[stridex*2]-=fc3;   p[-stridex*3]+=fc3;}

#else
               const int fc=t_l1[d];
               const int fc2=(bx+8)>>3;
               if(dif>0){p[0]+=fc;p[-stridex]-=fc;  p[stridex]+=fc2;p[-stridex*2]-=fc2;}
               else {    p[0]-=fc;p[-stridex]+=fc;  p[stridex]-=fc2;p[-stridex*2]+=fc2;}
#endif
            }
            else{
               const int fc=t_l1[d];
               if(dif>0){p[0]+=fc;p[-stridex]-=fc;  }//p[stridex*2]+=fc3;   p[-stridex*3]-=fc3;}
               else {    p[0]-=fc;p[-stridex]+=fc;  }//p[stridex*2]-=fc3;   p[-stridex*3]+=fc3;}
            }


         }

#endif
      }
#ifdef T_CAN_TEST_V
      else {
         if(!iPrevFiltred){
            i++;p+=stridey;
            iPrevFiltred=1;
         }else iPrevFiltred=0;
      }

#endif
      p+=stridey;
   }
}
void filterHx(unsigned char *img, const int stridex, const int stridey, int wh, const int coef, const int iCoef2);
void filterVx(unsigned char *img, const int stridex, const int stridey, int wh, const int coef, const int iCoef2);
static  void t_filter_L_TLUV(unsigned char *p, const int stridex, const int stridey, const int L,const int a){
   //   a+=4;
   int i,_r; 
#ifdef T_CAN_TEST_V
   const int str=GETFS;//fsTab[a];//(a+2)>>2;//a div 6
   if(!str)return ;
   const int T2=str*3;//(min((a+6)>>2,str*2));
   // const int T2=t2?t2:1;
   int f1;
   for(i=0;i<L;i++){
      f1=fr2<2,0,0>(p,stridex,stridey,a,0,T2);p+=stridey;
      if(!f1){i++;p+=stridey;}
   }

   return;
#endif


   //   const int ad3=(a>>2)+2;

   //if(ad3>9){ad3+=27;ad3>>=2;}
   //const int str=fsTab[a];//(a+2)>>2;//a div 6
   //if(!str)return ;
   //const int T2=min((a+2)>>2,str*2);


   for(i=0;i<L;i++){

      //const int dif=p[-stridex]-p[0]; 
      //      const int d=t_abs(dif); 
      const int dif=p[-stridex]-p[0];
      const int _s=dif>>31;
      const int d=(dif+_s)^_s; 
      //4853
      if(d>1 && d<a)
      {
         const int p0=p[0];
         const int p1=p[stridex]+p[stridex*2];
         const int n0=p[-stridex];
         const int n1=p[-2*stridex]+p[-3*stridex];
         //if(t_lim_2(((p1+n1+1)>>1)-(p0+n0),-d,d))// && abs(p1-p[2*stridex])<ad3 && abs(n1-p[-3*stridex])<ad3)
         /*
         if((((p0-n0)<<3)+(n1-p1)+8)>>4 && 
         t_lim_m((((p1+n1+1)>>1)-(p0+n0))*2,a-d))
         */
         //-- _r=p[0]-p[stridex]-p[-2*stridex]+p[-stridex];
         //--_r=p0+n0-((p1+n1+1)>>1);
         //--
         _r=((n1-p1)>>2)-(n0-p0);

         //--_s2=_r>>31;_r^=_s2;////
         if(_r+1<d &&  _r*2+(d>>1)<=a && 
            (((dif)<<3)+(p1-n1)+8)>>4){
               //p1 p1 p0 n0 n1 n1
               //1   2 3  4   5 6
               //3-11>>2 -8 (4-3)*-8
               const int pn0=p0+n0+3+(_s*2);
               const int d0=(pn0+p1)>>2;
               const int d1=(pn0+n1)>>2;

               int di1=(d0-p0);
               int di2=(d1-n0);

#define F_X(_X0,_XC) if(iFN && abs(_X0-p[_XC])*2<d)p[_XC]= (p[_XC-stridex]+p[_XC]+p[_XC+stridex]*2+2)>>2;else iFN=0;
#define F_N(_X0,_XC) if(iFN && abs(_X0-p[_XC])*2<d)p[_XC]= (p[_XC-stridex]*2+p[_XC]+p[_XC+stridex]+2)>>2;else iFN=0;
               const int iFN = (p[stridex]==p[stridex*2] && p[stridex]==p[0])
               || (p[-stridex*2]==p[-stridex*3] && p[-stridex*2]==p[-stridex]);
               if(di1){
                  //if(di1>0)di1=(di1+2)>>1;else di1=(di1-1)>>1;
                  //p[stridex]=(pn0+p1+(di1>>1))>>2;

                  //if(di1>=0)di1=(di1+1)>>1;else di1=(di1-1)>>1;
                  //-- p[stridex*2]=(p0+p1+(int)p[stridex*2]+((di1+5)>>1))>>2;
                  int z=0;
                  if(p[stridex]!=p[2*stridex]){
                     p[0]=d0;
                     p[stridex]=(p0+p[stridex]+_s+1+p1+(di1>>1))>>2;
                     z++;
                  }
                  else p[0]=(pn0*3+p1)>>3;//+=(_s*2+1)*t_l1[d];
                  //       p[stridex*z]=((st+p[stridex*z]*3+2)>>2)-_s;
                  //         p[-stridex*z]=((st+p[-stridex*z]*3)>>2)+_s;
                  //
                  if(iFN){
                     int st=p[stridex*z];
                     z++;
                     for(;z<5 && (z<3 ||p[stridex*(z)]==p[stridex*(z+1)]);z++){
                        p[stridex*z]=(st+p[stridex*z]+1+_s)>>1;
                        //p[stridex*z]+=(pn0-p[stridex*z]*3+p[stridex*(z+1)])>>2;
                        //p[stridex*z]=((st+p[stridex*z]*3+2)>>2)-_s;
                        st=p[stridex*z];
                     }
                  }
                  //else p[stridex]=(p0+p[stridex]+_s+1+p1+(di1>>1))>>2;

                  //21 001234 56
                  /*
                  int iFN=2;//((n1+(p[stridex*6]+p[stridex*5])>>2)-(p[stridex*2]>>1));
                  F_X(p[0],stridex*3);
                  F_X(p[1],stridex*4);
                  F_X(p[2],stridex*5);
                  F_X(p[3],stridex*6);
                  */

               }

               if(di2){


                  //p[-stridex*2]=(pn0+n1+(di2>>1))>>2;

                  //--p[-stridex*3]=(n0+n1+(int)p[-stridex*3]+((di2+5)>>1))>>2;
                  int z=1;
                  if(p[-2*stridex]!=p[-3*stridex]){
                     p[-stridex]=d1;
                     p[-2*stridex]=(n0+p[-2*stridex]-_s+1+n1+(di2>>1))>>2;
                     z++;
                  }else p[-stridex]=(pn0*3+n1)>>3;// p[-stridex]-=(_s*2+1)*t_l1[d];

                  if(iFN){
                     int st=p[-stridex*z];
                     z++;
                     for(;z<6 && (z<4 ||p[-stridex*(z)]==p[-stridex*(z+1)]);z++){
                        p[-stridex*z]=((st+p[-stridex*z])>>1)-_s;
                        //p[-stridex*z]+=pn0-p[-stridex*z]*3+p[-stridex*(z+1)])>>2;
                        //p[-stridex*z]=((st+p[-stridex*z]*3)>>2)+_s;
                        st=p[-stridex*z];
                     }
                  }


                  /*
                  int iFN=2;//((p1+(p[stridex*-6]+p[stridex*-7])>>2)-(p[stridex*-3]>>1));

                  F_N(p[-stridex],-stridex*4);
                  F_N(p[-stridex*2],-stridex*5);
                  F_N(p[-stridex*3],-stridex*6);
                  F_N(p[-stridex*4],-stridex*7);
                  */
               }
               /*
               if(di1){
               p[0]=d0;
               if(di1>0)di1=(di1+2)>>1;else di1=(di1-1)>>1;
               p[stridex]+=di1;
               if(di1>=0)di1=(di1+1)>>1;else di1=(di1-1)>>1;
               p[stridex*2]+=di1;
               }
               if(di2){
               p[-stridex]=d1;

               if(di2>0)di2=(di2+2)>>1;else di2=(di2-1)>>1;
               p[-stridex*2]+=di2;
               if(di2>=0)di2=(di2+1)>>1;else di2=(di2-1)>>1;
               p[-stridex*3]+=di2;
               }
               */


         }
      }
      else{
         p+=stridey;
         i++;
      }
      p+=stridey;
   }
}

void filter_16_topUV(unsigned char *p, int stride, int a){
   if(a<2 || !fsTab[a])return ;

   //a+=2;
   t_filter_L_TLUV(p,stride,3,16,a);
}
void filter_16_leftUV(unsigned char *p, int stride, int a){
   if(a<2 || !fsTab[a])return ;

   //a+=2;
   t_filter_L_TLUV(p,3,stride,16,a);
}
void filter_16_top(unsigned char *p, int stride, int a){

   if(a<2 || !fsTab[a])return ;
#if 1
   t_filter_L_TL<0>(p,stride,3,16,a,1);
#endif
}

void filter_16_left(unsigned char *p, int stride, int a){

   if(a<2 || !fsTab[a])return ;
   //a+=2;
   //
#if 1
   t_filter_L_TL<0>(p,3,stride,16,a,1);
#endif

}

void filter_4x4_16_dif(unsigned char *p, int stride, int a, int rem){
   if(a<3|| !fsTab[a])return ;
   //char pC[16*6];
   //a+=2;
   //   a+=2;
#ifndef T_CAN_TEST_V
   t_filter_L_D(p+stride*4,stride,3,16,a,rem);
   t_filter_L_D(p+stride*8,stride,3,16,a,rem);
   t_filter_L_D(p+stride*12,stride,3,16,a,rem);

   t_filter_L_D(p+12,3,stride,16,a,rem);
   t_filter_L_D(p+24,3,stride,16,a,rem);
   t_filter_L_D(p+36,3,stride,16,a,rem);
#endif
}
void filter_8x8_16_v(unsigned char *p, int stride, int a){
#ifdef T_CAN_TEST_V
   t_filter_L_TL<0>(p+stride*8,stride,3,16,a,0);
   t_filter_L_TL<0>(p+24,3,stride,16,a,0);
#else
   t_filter_L_D(p+stride*8,stride,3,16,a,1);
   t_filter_L_D(p+24,3,stride,16,a,1);
#endif
}
void filter_8x8_16_ab(unsigned char *p, int stride, int a){
   //idx=44;
   //idx>>=1;//327 125600
   /*
   vc1_loop_filter(p+24,stride,3,16,idx>>1);
   vc1_loop_filter(p+stride*8,3,stride,16,idx>>1);
   */
   int ipC[32];
   char *pC=(char*)&ipC[0];
   //a+=2;

   t_filter_L(p+stride*8,stride,3,16,a,1,&pC[0]);
   t_filter_L(p+24,3,stride,16,a,1,&pC[16]);

   t_filter_L(p+stride*8,stride,3,16,a,0,&pC[0]);
   t_filter_L(p+24,3,stride,16,a,0,&pC[16]);

}
static  void t_filter_LZ_test_16(unsigned char *p, const int stridex, const int stridey,  int a,  int  *pC, int *f, int *dc, int id){
   int iCo=0;
   const static int t_mult[]={12,42,24};

   //#define _C_DC(_A,_B,_F1,_F2,_FN,_FNE) iCo=dc?((!!(_F1))+(!!(_F2 ))):2; if((iCo || (dc&&  _A!=_B))){_FN;}
#define _C_DC(_A,_B,_F1,_F2,_FN,_FNE) iCo=dc?((!!(_F1))+(!!(_F2 ))):2; if((iCo || (dc&& _A!=_B))&& abs(_A-_B)<(a+4)*t_mult[iCo]){_FN;}
   //dc1!=dc2 && abs(_A-_B)<a*2
   //taisiit 2 pass.1)ar maziem dc 2)lielakiem dc ,abs(_A-_B)<x*pass
   //pC[3]=pC[2]=pC[1]=pC[0]=0;   return;
   //const int iDCA=(a<fsTab[a]*8?((a<<4)):(((a+fsTab[a]*8)<<3))+64);
   if(id<4){
      // t_filter_L(p+stride*4,stride,3,16,a,1,(char *)&pC[0]);
      //1----
      //2----
      //3----

      if(id==2){
         _C_DC(dc[8],dc[4], (f[2]&1) , (f[0]&4),t_filter_LZ_s(p,stridex,stridey,4,a,iCo,(char *)&pC[0]),pC[0]=0)
            _C_DC(dc[9],dc[5], (f[2]&2) , (f[0]&8),t_filter_LZ_s(p+12,stridex,stridey,4,a,iCo,(char *)&pC[1]), pC[1]=0)

            _C_DC(dc[10],dc[6] , (f[3]&1) , (f[1]&4),t_filter_LZ_s(p+24,stridex,stridey,4,a,iCo,(char *)&pC[2]), pC[2]=0)
            _C_DC(dc[11],dc[7] , (f[3]&2) , (f[1]&8),t_filter_LZ_s(p+36,stridex,stridey,4,a,iCo,(char *)&pC[3]), pC[3]=0)
      }
      else{
         if(id==3){if(dc)dc+=8;f+=2;}
         _C_DC(dc[0],dc[4], (f[0]&1) , (f[0]&4),t_filter_LZ_s(p,stridex,stridey,4,a,iCo,(char *)&pC[0]), pC[0]=0)
            _C_DC(dc[1],dc[5], (f[0]&2) , (f[0]&8),t_filter_LZ_s(p+12,stridex,stridey,4,a,iCo,(char *)&pC[1]), pC[1]=0)
            _C_DC(dc[2],dc[6], (f[1]&1) , (f[1]&4),t_filter_LZ_s(p+24,stridex,stridey,4,a,iCo,(char *)&pC[2]), pC[2]=0)
            _C_DC(dc[3],dc[7], (f[1]&2) , (f[1]&8),t_filter_LZ_s(p+36,stridex,stridey,4,a,iCo,(char *)&pC[3]), pC[3]=0)
      }
   }
   else
   {
      //456
      //0123
      //|||
      //|||
      //|||

      if(id==5){
         _C_DC(dc[1],dc[2], (f[0]&2) , (f[1]&1),t_filter_LZ_s(p,stridex,stridey,4,a,iCo,(char *)&pC[0]), pC[0]=0)
            _C_DC(dc[5],dc[6], (f[0]&8) , (f[1]&4),t_filter_LZ_s(p+stridey*4,stridex,stridey,4,a,iCo,(char *)&pC[1]), pC[1]=0)
            _C_DC(dc[9],dc[10], (f[2]&2) , (f[3]&1),t_filter_LZ_s(p+stridey*8,stridex,stridey,4,a,iCo,(char *)&pC[2]), pC[2]=0)
            _C_DC(dc[13],dc[14], (f[2]&8) , (f[3]&4),t_filter_LZ_s(p+stridey*12,stridex,stridey,4,a,iCo,(char *)&pC[3]), pC[3]=0)

      }else{
         if(id==6){if(dc)dc+=2;f++;}
         _C_DC(dc[0],dc[1], (f[0]&1) , (f[0]&2),t_filter_LZ_s(p,stridex,stridey,4,a,iCo,(char *)&pC[0]), pC[0]=0)
            _C_DC(dc[4],dc[5], (f[0]&4) , (f[0]&8),t_filter_LZ_s(p+stridey*4,stridex,stridey,4,a,iCo,(char *)&pC[1]), pC[1]=0)
            _C_DC(dc[8],dc[9], (f[2]&1) , (f[2]&2),t_filter_LZ_s(p+stridey*8,stridex,stridey,4,a,iCo,(char *)&pC[2]), pC[2]=0)
            _C_DC(dc[12],dc[13],(f[2]&4) , (f[2]&8),t_filter_LZ_s(p+stridey*12,stridex,stridey,4,a,iCo,(char *)&pC[3]), pC[3]=0)
      }

   }
}
void fxMB(unsigned char *p, const int stride, int al){
   /*
   static const int mm[5][5]={
   {0,1,2,3,4},
   {5,6,7,8,9},
   {10,11,12,11,10},
   {9,8,7,6,5},
   {4,3,2,1,0}
   };
   */

   static const int mm[5][5]={
      {0,1,2,1,0},
      {1,-5,7,-5,1},
      {2,7,20,7,2},
      {1,-5,7,-5,1},
      {0,1,2,1,0}
   };

   /*
   static const int mm[5][5]={
   {0,1,2,1,0},
   {1,-1,-5,-1,1},
   {2,-5,28,-5,2},
   {1,-1,-5,-1,1},
   {0,1,2,1,0}
   };
   */
   int mres[256];
   int res;
   int i,j,n,m;
   const int fs=fsTab[al];

   for(i=0;i<16;i++){
      for(j=0;j<16;j++){
         res=0;
         int d=0;
         for(m=-2;m<3 && m+i<16;m++){
            for(n=-2;n<3&& n+j<16;n++){

               //res+=p[(n)*3+(m)*stride]*mm[n+2][m+2];
               res+=p[(n+j)*3+(m)*stride]*mm[n+2][m+2];
               d+=mm[n+2][m+2];
               //d+=mm[m+1][n+1];
            }
         }
         if(d){
            int d2=clipZ(fs,((res+(d>>1))/d)-(int)p[j*3]);mres[j+i*16]=(int)p[j*3]+d2;
            // mres[j+i*16]=((res+(d>>1))/d);//p[0]+d2;
            if(mres[j+i*16]<1)mres[j+i*16]=1;
            else if(mres[j+i*16]>254)mres[j+i*16]=254;
         }
         else{
            mres[j+i*16]=p[j*3];
         }

         //         mres[j+i*16]=d?(res+(d>>1))/d:p[0];
         //p[j*3]=(res+(d>>1))/d;//(66*2+12);//(25*6.6);

      }
      p+=stride;
   }

   p-=stride*16;
   for(i=0;i<16;i++){
      for(j=0;j<16;j++){
         p[j*3]=mres[j+i*16];
      }
      p+=stride;
   }

}


void filter_4x4_16_a_dcf(unsigned char *p, int stride, int a, int *f, int *dc){
   if(a<2)return ;
   // return;
   //char pC[16*6];
   int ipC[4*6];
   char *pC=(char*)&ipC[0];
#ifdef T_CAN_TEST_V
   if(!fsTab[a])return;
   //fxMB(p,stride,a);return;
#if 1
   if(dc){
      // p[0]=20;
      t_filter_LZ_test_16(p+stride*4,stride,3,a,(int*)(&pC[0]),f,dc,1);
      t_filter_LZ_test_16(p+stride*8,stride,3,a,(int*)(&pC[16]),f,dc,2);
      t_filter_LZ_test_16(p+stride*12,stride,3,a,(int*)(&pC[32]),f,dc,3);
      //a-=1;

      t_filter_LZ_test_16(p+12,3,stride,a,(int*)(&pC[48]),f,dc,4);
      t_filter_LZ_test_16(p+24,3,stride,a,(int*)(&pC[64]),f,dc,5);
      t_filter_LZ_test_16(p+36,3,stride,a,(int*)(&pC[80]),f,dc,6);
   }
   else{

      t_filter_LZ_s(p+12,3,stride,16,a,3,&pC[48]);
      t_filter_LZ_s(p+24,3,stride,16,a,3,&pC[64]);
      t_filter_LZ_s(p+36,3,stride,16,a,3,&pC[80]);

      t_filter_LZ_s(p+stride*4,stride,3,16,a,3,&pC[0]);
      t_filter_LZ_s(p+stride*8,stride,3,16,a,3,&pC[16]);
      t_filter_LZ_s(p+stride*12,stride,3,16,a,3,&pC[32]);

   }
#else

   t_filter_LZ_s(p+stride*4,stride,3,16,a,1,&pC[0]);
   t_filter_LZ_s(p+stride*8,stride,3,16,a,1,&pC[16]);
   t_filter_LZ_s(p+stride*12,stride,3,16,a,1,&pC[32]);
   a--;
   t_filter_LZ_s(p+12,3,stride,16,a,1,&pC[48]);
   t_filter_LZ_s(p+24,3,stride,16,a,1,&pC[64]);
   t_filter_LZ_s(p+36,3,stride,16,a,1,&pC[80]);
#endif

#else
   /*
   t_filter_LZ_test_16(p+stride*4,stride,3,a,(int*)(&pC[0]),f,dc,1);
   t_filter_LZ_test_16(p+stride*8,stride,3,a,(int*)(&pC[16]),f,dc,2);
   t_filter_LZ_test_16(p+stride*12,stride,3,a,(int*)(&pC[32]),f,dc,3);

   t_filter_LZ_test_16(p+12,3,stride,a,(int*)(&pC[48]),f,dc,4);
   t_filter_LZ_test_16(p+24,3,stride,a,(int*)(&pC[64]),f,dc,5);
   t_filter_LZ_test_16(p+36,3,stride,a,(int*)(&pC[80]),f,dc,6);
   */

   t_filter_L(p+stride*4,stride,3,16,a,0,&pC[0]);
   t_filter_L(p+stride*8,stride,3,16,a,0,&pC[16]);
   t_filter_L(p+stride*12,stride,3,16,a,0,&pC[32]);

   t_filter_L(p+12,3,stride,16,a,0,&pC[48]);
   t_filter_L(p+24,3,stride,16,a,0,&pC[64]);
   t_filter_L(p+36,3,stride,16,a,0,&pC[80]);
#endif
}

void filter_4x4_16_a(unsigned char *p, int stride, int a){
   if(a<2)return ;
   //char pC[16*6];
   int ipC[4*6];
  // char *pC=(char*)&ipC[0];
   //a+=2;
   //   a+=2;
#ifndef T_CAN_TEST_V
   t_filter_L(p+stride*4,stride,3,16,a,1,&pC[0]);
   t_filter_L(p+stride*8,stride,3,16,a,1,&pC[16]);
   t_filter_L(p+stride*12,stride,3,16,a,1,&pC[32]);

   t_filter_L(p+12,3,stride,16,a,1,&pC[48]);
   t_filter_L(p+24,3,stride,16,a,1,&pC[64]);
   t_filter_L(p+36,3,stride,16,a,1,&pC[80]);


   t_filter_L(p+stride*4,stride,3,16,a,0,&pC[0]);
   t_filter_L(p+stride*8,stride,3,16,a,0,&pC[16]);
   t_filter_L(p+stride*12,stride,3,16,a,0,&pC[32]);

   t_filter_L(p+12,3,stride,16,a,0,&pC[48]);
   t_filter_L(p+24,3,stride,16,a,0,&pC[64]);
   t_filter_L(p+36,3,stride,16,a,0,&pC[80]);

#endif
}
#ifndef __SYMBIAN32__


void debugss(char*,int ,int);

#endif

#if 1
template<int L, int iCheckLong>
static void dc_f_h(unsigned char *src,const  int stride, const int a)
{
   //   return;
   int i;
   int b, c,dif,d,d2,d3,d4,iLong;
   src+=stride;
   int sw;
   int iFS=GETFS;
   //int al=tfa;

   for(i = 0; i < L; i+=4) {
      b = src[-1*3];
      c = src[0*3];
      if(b<c)dif=c-b;else dif=b-c;//dif=b-c;if(dif<0)dif=-dif;//dif=abs(b-c);
      if(dif>1 && dif<a) 
      {
         src-=stride;
         //#define _FDC1 src[-3]+=d;src[0]-=d;src += stride;
         //#define _FDC2 src[-3]-=d;src[0]+=d;src += stride;
#define _FDC_1 src[-3]+=d;src[0]-=d2;src += stride;sw=d;d=d2;d2=sw;
#define _FDC_2 src[-3]-=d;src[0]+=d2;src += stride;sw=d;d=d2;d2=sw;
         // fast DCFilter  abcd, check b only then filter all abcd

#ifdef T_CAN_TEST_V
         /*
         #undef _FDC_1
         #undef _FDC_2
         #define _FDC_1 src[-3]+=d;src[0]-=d;src += stride;;
         #define _FDC_2 src[-3]-=d;src[0]+=d;src += stride;;
         //95555
         //95555
         //96555
         //97667
         //99999 

         d=t_l1[dif-1];
         //           int d2=t_l1[dif];//(dif>>1);
         //TODO *(int*)src+=(d*0x01010101);
         d3=(d)>>1;
         #define _FDC_1x src[-6]+=d3;src[3]-=d3;
         #define _FDC_2x src[-6]-=d3;src[3]+=d3;
         */
#undef _FDC_1
#undef _FDC_2
         /*

         d=min(dif>>1,fsTab[al]);//t_l1[dif+2];
         d2=(d+1)>>1;
         d3=(d*2+12+abs(src[-12]-src[9]))>>4;
         */    
         //src[-4*_S]+=1;src[3*_S]-=1;
         //  src[-4*_S]-=1;src[3*_S]+=1;
#define _FDC_1L(_S,_S2)  src[_S*-2]+=d2;src[_S]-=d2;src[_S*-1]+=d;src[0]-=d; src[-3*_S]+=d3;src[2*_S]-=d3;; src += _S2;
#define _FDC_2L(_S,_S2)  src[_S*-2]-=d2;src[_S]+=d2;src[_S*-1]-=d;src[0]+=d; src[-3*_S]-=d3;src[2*_S]+=d3;; src += _S2;

#define _TFDC 1
         d=(dif*3+4)>>3;

         if(iCheckLong && d<=iFS && ( src[-12]==src[-18] && src[9]==src[15])
            //|| (!iCheckLong && src[-12]==src[-9] && src[9]==src[6])
            ){
               /*
               d2=(aDif)>>1;
               d4=(((d2+1)>>1)+_s)^_s;
               d2=(d2+_s)^_s;
               d1=(d1+_s)^_s;

               */
               if(_TFDC){
                  // d=(dif)>>1;
                  //                 d=(dif*3+3)>>3;
                  //d2=(d*5+3)>>3;
                  //d2=(dif*5+8)>>(4);
                  d2=(dif+3)>>2;
                  //d2=(d*9+16)>>(2+3);
                  d3=1;//(d2+3)>>2;



                  if(b<c){
                     _FDC_1L(3,stride);_FDC_1L(3,stride);_FDC_1L(3,stride);_FDC_1L(3,stride);
                  }else {
                     _FDC_2L(3,stride);_FDC_2L(3,stride);_FDC_2L(3,stride);_FDC_2L(3,stride);
                  }

                  src += stride;
                  continue;
               }
               else{
                  iLong=1;
                  d2=d=min(dif>>1,fsTab[a]);
                  d3=(d+1)>>1;
                  d4=(d+2)>>2;//(d3+1)>>1;

               }

         }
         else{
            iLong=0;
            //--d=min(dif>>1,iFS);//t_l1[dif+2];
            // d=(dif*5+6)>>4;
            //d=(dif>>1);
            if(d>iFS){d=iFS;d3=0;}else
               //--d2=(d+1)>>1;
               //              d3=(d*2+12+dif)>>4;
               //              d3=(d*5+iFS)>>4;
               d3=(dif+4)>>3;
            //     d3*=(dif<iFS);
            // d3=(d*3+8)>>4;
            //if(dif>iFS*2){d3=0;}
            // d3=(d*4+12+iFS)>>4;
            //d3=(d+fsTab[al])>>1;
         }
         //           d3=(d+6+abs(src[-12]-src[9]))>>3;//(n1)-(p1)
         //d3=min((d+3)>>2,(abs(src[-12]-src[9])+7)>>3);
         //d3=(al+62)>>6;
         // d3=min((dif+2)>>2,(al+31)>>5);
         //if(d<4)d3=1;else d3=(al+dif+28)>>5;
#define _FDC_1x src[-6]+=d3;src[3]-=d3;src[-3]+=d;src[0]-=d;src += stride;
#define _FDC_2x src[-6]-=d3;src[3]+=d3;src[-3]-=d;src[0]+=d;src += stride;;
#define _FDC_1 src[-3]+=d2;src[0]-=d2;src += stride;
#define _FDC_2 src[-3]-=d2;src[0]+=d2;src += stride;

         //           d3=0;
         if(b<c){
            _FDC_1x;_FDC_1x;_FDC_1x;_FDC_1x;
         }else {
            _FDC_2x;_FDC_2x;_FDC_2x;_FDC_2x;
         }
#else
         d=t_l1[dif-1];
         d2=t_l1[dif+1];//(dif>>1);
         if(b<c){
            _FDC_1;_FDC_1;_FDC_1;_FDC_1;
         }else {
            _FDC_2;_FDC_2;_FDC_2;_FDC_2;
         }
#endif
         src += stride;
         //#undef _FDC1
         //#undef _FDC2
#undef _FDC_1
#undef _FDC_2
      }
      else src += stride*4;
   }
}

template<int L, int iCheckLong>
static void dc_f_v(unsigned char *src, const int stride, const int a)
{
   //   return;
   int i,d,d2,d3,d4,iLong;
   int b, c,dif,rnd=0;
   src+=3;
   int sw;
   int iFS=GETFS;
   //int al=tfa;

   for(i = 0; i < L; i+=4) {
      b = src[-stride];
      c = src[0];
      if(c>b)dif=c-b;else dif=b-c;//
      //dif=b-c;if(dif<0)dif=-dif;//dif=abs(b-c);
      if(dif>1 && dif<a) {
         //if(!d)d=1;
         src-=3;
         //#define _FDC1 src[-stride]+=d;src[0]-=d;src+=3;
         //#define _FDC2 src[-stride]-=d;src[0]+=d;src+=3;
#define _FDC_1 src[-stride]+=d;src[0]-=d2;src += 3;sw=d;d=d2;d2=sw;
#define _FDC_2 src[-stride]-=d;src[0]+=d2;src += 3;sw=d;d=d2;d2=sw;

#ifdef T_CAN_TEST_V
         /*
         d=t_l1[dif-1];
         d2=t_l1[dif];
         d3=(d)>>1;
         #undef _FDC_1x
         #undef _FDC_2x
         #define _FDC_1x src[-stride*2]+=d3;src[stride]-=d3;
         #define _FDC_2x src[-stride*2]-=d3;src[stride]+=d3;
         */
#undef _FDC_1
#undef _FDC_2
         d=(dif*3+4)>>3;

         if((iCheckLong && d<=iFS &&  src[-5*stride]==src[stride*-3] && src[stride*4]==src[stride*2])
            //|| (!iCheckLong && src[-3*stride]==src[-4*stride] && src[2*stride]==src[3*stride])
            ){
               if(_TFDC){
                  //--d=(dif)>>1;
                  //d=(dif*3+4)>>3;
                  //d2=(d*5+3)>>3;
                  //d2=(dif*5+8)>>(4);
                  d2=(dif+3)>>2;
                  //d2=(d*9+16)>>(2+3);
                  d3=1;//(d2+3)>>2;

                  if(b<c){
                     _FDC_1L(stride,3);_FDC_1L(stride,3);_FDC_1L(stride,3);_FDC_1L(stride,3);
                  }else {
                     _FDC_2L(stride,3);_FDC_2L(stride,3);_FDC_2L(stride,3);_FDC_2L(stride,3);
                  }

                  src += 3;
                  continue;
               }
               else{
                  iLong=1;
                  d2=d=min(dif>>1,fsTab[a]);
                  d3=(d+1)>>1;
                  d4=(d+2)>>2;//(d3+1)>>1;
               }

         }
         else{
            //d=(dif>>1);

            if(d>iFS){d=iFS;d3=0;}else
               //--d2=(d+1)>>1;
               //              d3=(d*2+12+dif)>>4;

               //d3=(d*5+iFS)>>4;

               d3=(dif+3)>>3;

            //if(dif>iFS*2){d3=0;}
            //d3=(d*3+dif)>>4;
            //d3=(d*3+5)>>3;
            iLong=0;
         }
         //    d3=0;


#undef _FDC_1x
#undef _FDC_2x
#define _FDC_1x src[stride*-2]+=d3;src[stride]-=d3;src[-stride]+=d;src[0]-=d;src += 3;
#define _FDC_2x src[stride*-2]-=d3;src[stride]+=d3;src[-stride]-=d;src[0]+=d;src += 3;;
#define _FDC_1 src[-stride]+=d2;src[0]-=d2;src += 3;
#define _FDC_2 src[-stride]-=d2;src[0]+=d2;src += 3;
         if(b<c){
            _FDC_1x;_FDC_1x;_FDC_1x;_FDC_1x;
         }else {
            _FDC_2x;_FDC_2x;_FDC_2x;_FDC_2x;
         }
#else
         d=t_l1[dif-1];
         d2=t_l1[dif+1];//(dif>>1);
         // fast DCFilter  abcd, check b only then filter all abcd

         if(b<c){
            _FDC_1;_FDC_1;_FDC_1;_FDC_1;
         }else {
            _FDC_2;_FDC_2;_FDC_2;_FDC_2;
         }
#endif

         src+=3;
         //#undef _FDC1
         //#undef _FDC2
#undef _FDC_1
#undef _FDC_2
      }
      else src += 12;
   }
}


#endif

void filter_4x4_16_dc(unsigned char *p, int stride, int al){

   //debugss("al",al,(int)p);
   //al*=3;al++;al>>=2;
   if(al<2)return;
   if(!fsTab[al])return;

   //fxMB(p,stride,al);return;



   dc_f_h<16,1>(p+24,stride,al);
   dc_f_v<16,1>(p+stride*8,stride,al);

   dc_f_h<16,0>(p+12,stride,al);
   dc_f_h<16,0>(p+36,stride,al);
   dc_f_v<16,0>(p+stride*4,stride,al);
   dc_f_v<16,0>(p+stride*12,stride,al);



}
void filter_4x4_8_dc(unsigned char *p, int stride, int al){

   //debugss("al",al,(int)p);
   if(al<2)return;
   dc_f_h<8,0>(p+12,stride,al);
   dc_f_v<8,0>(p+stride*4,stride,al);
}

void filter_4x4_8_a(unsigned char *p, int stride, int a){
   int ipC[4*6];
   char *pC=(char*)&ipC[0];
   /*
   t_filter_L(p+stride*4,stride,3,16,a,1,&pC[0]);
   t_filter_L(p+stride*8,stride,3,16,a,1,&pC[16]);
   t_filter_L(p+stride*12,stride,3,16,a,1,&pC[32]);

   t_filter_L(p+12,3,stride,16,a,1,&pC[48]);
   t_filter_L(p+24,3,stride,16,a,1,&pC[64]);
   t_filter_L(p+36,3,stride,16,a,1,&pC[80]);


   t_filter_L(p+stride*4,stride,3,16,a,0,&pC[0]);
   t_filter_L(p+stride*8,stride,3,16,a,0,&pC[16]);
   t_filter_L(p+stride*12,stride,3,16,a,0,&pC[32]);

   t_filter_L(p+12,3,stride,16,a,0,&pC[48]);
   t_filter_L(p+24,3,stride,16,a,0,&pC[64]);
   t_filter_L(p+36,3,stride,16,a,0,&pC[80]);
   */
   t_filter_L(p+stride*4,stride,3,8,a,1,&pC[0]);
   t_filter_L(p+12,3,stride,8,a,1,&pC[48]);

   t_filter_L(p+stride*4,stride,3,8,a,0,&pC[0]);
   t_filter_L(p+12,3,stride,8,a,0,&pC[48]);
}
void filter_4x4_16_f(unsigned char *p, int stride, int a, int *f){
   if(a<2)return;

   int ipC[4*6];
   char *pC=(char*)&ipC[0];


   if(f[0])filter_4x4_8_a(p,stride,a);else filter_4x4_8_dc(p,stride,a);
   if(f[1])filter_4x4_8_a(p+24,stride,a);else filter_4x4_8_dc(p+24,stride,a);
   if(f[2])filter_4x4_8_a(p+stride*8,stride,a);else filter_4x4_8_dc(p+stride*8,stride,a);
   if(f[3])filter_4x4_8_a(p+stride*8+24,stride,a);else filter_4x4_8_dc(p+stride*8+24,stride,a);

   t_filter_L(p+stride*8,stride,3,16,a,1,&pC[16]);
   t_filter_L(p+24,3,stride,16,a,1,&pC[64]);
   t_filter_L(p+stride*8,stride,3,16,a,0,&pC[16]);
   t_filter_L(p+24,3,stride,16,a,0,&pC[64]);

}
void drawL(unsigned char *dst, int x,int y, int x1, int y1, int c1, int c2, int c3, int dstStride){
   int l1=t_abs(x-x1);
   int l2=t_abs(y-y1);
   int l=max(l1,l2);
   int i;
   dst[dstStride*y+x*3]=c1;
   dst[dstStride*y+x*3+1]=c2;
   dst[dstStride*y+x*3+2]=c3;
   if(!l){
      return;
   }
   /*
   l++;
   int lh=(l)>>1;
   for(i=0;i<l;i++){
   dst[(x*i+(l-i)*x1+lh)*3/l+(y*i+(l-i)*y1+lh)*dstStride/l]=c;
   }
   */
   //l++;
   for(i=1;i<l;i++){
      const int o=(x1*i+(l-i)*x)/l*3+(y1*i+(l-i)*y)/l*dstStride;
      dst[o]=c1;
      dst[o+1]=c2;
      dst[o+2]=c3;
   }
   dst[dstStride*y1+x1*3]=c1;
   dst[dstStride*y1+x1*3+1]=c2;
   dst[dstStride*y1+x1*3+2]=c3;
   /*
   if(l1>=l2){
   dstStride+=x*3;
   for(i=0;i<l;i++){
   int p=(y*(l-i)+y1*(i))/l;
   dst[dstStride*p+i*3]=c;
   }
   }
   else{
   for(i=0;i<l;i++){
   int p=(x*(l-i)+x1*(i))/l;
   dst[dstStride*(i+p*3]=c;
   }
   }
   */

}
int toZ(int r, int g, int b){
   int z;
   z = ( (  66 * r + 129 * g +  25 * b + 128) >> 8) +  16;
   //y = ( (  71 * r + 139 * g +  27 * b + 128) >> 8) ;
   // z = ( ( -38 * r -  74 * g + 112 * b + 128) >> 8) + 128;
   // z = ( ( 112 * r -  94 * g -  18 * b + 128) >> 8) + 128;
   return (z+3)>>2;
}
void to3D(unsigned char *src, int w, int h, unsigned char *dst, int dstStride){
   int i,j;
   int stride=w*3;
   unsigned int m_lastRow[2048];
   src+=stride*(h-1);
   //for(i=0;i<w;i++)m_lastRow[0][i]=m_lastRow[1][i]=i;

   //drawL(dst,5,5, 100, 200,255,dstStride);
   //drawL(dst,100, 200, 105,300,255,dstStride);

   //return;

   for(j=h-1;j>=0;j--){
      if(j==h-1){
         for(i=0;i<w;i++){
            int z=toZ(src[i*3+2],src[i*3+1],src[i*3]);
            int x1=i-j;
            int y1=j+h-z;
            if(y1<0)y1=0;else if(y1>=h*2)y1=h*2-1;
            if(x1<0)x1=0;else if(x1>=w*2)x1=w*2-1;
            y1=h*2-y1;

            //  drawL(dst,x1,y1, m_lastRow[0][i], m_lastRow[1][i],z,dstStride);

            m_lastRow[i]=y1;
            // m_lastRow[1][i]=y1;
         }
      }
      else{
         for(i=0;i<w;i++){
            int z=toZ(src[i*3+2],src[i*3+1],src[i*3]);
            int x1=i-j;
            int y1=j+h-z;
            if(y1<0)y1=0;else if(y1>=h*2)y1=h*2-1;
            if(x1<0)x1=0;else if(x1>=w*2)x1=w*2-1;
            y1=h*2-y1;

            drawL(dst,x1,y1, x1, m_lastRow[i],src[i*3],src[i*3+1],src[i*3+2],dstStride);

            ///m_lastRow[0][i]=x1;
            m_lastRow[i]=y1;
         }
      }
      src-=stride;
   }


}

int getLoopIDX(int idx){

   return 10;
}
#ifndef __SYMBIAN32__
 
void filter_A3(unsigned char *p , int stride, int l, int idx){
      
      
      // void t_filter_L_TL<0>(unsigned char *p, const int stridex, const int stridey, const int L, const int a,const  int iLongF);//
      t_filter_L_TL<1>(p,stride,3,l,idx,0);
      t_filter_L_TL<1>(p,3,stride,l,idx,0);
      
      t_filter_L_TL<1>(p+1,stride,3,l,idx,0);
      t_filter_L_TL<1>(p+1,3,stride,l,idx,0);
      
      t_filter_L_TL<1>(p+2,stride,3,l,idx,0);
      t_filter_L_TL<1>(p+2,3,stride,l,idx,0);
      
   }
void tblockRGB(unsigned char *pic, int w, int h, int q)
{
   ////



   int i,j;
   int hm4=h-7;
   int wm4=w-7;
   int a=q;
   int b=q/7;
   if(a>150)a=150;
   if(a<5)a=5;
   if(b>25)b=25;
   if(b<2)b=2;
   
#define T_USE_F
   
#ifdef T_USE_F
   int idx=q*3>>1;
   void initFSTab();
   initFSTab();
   setFTabA(q*3,1);
#else
   int idx=getLoopIDX(q);
#endif
   int stride=w*3;
   unsigned char *p;   



   // a*=2;
   // b*=2;

   //a=16;
   //b=5;

   //void toYuv(unsigned char *p, int w, int h);
   //toYuv(pic,w,h);

   for(j=8;j<hm4;j+=8)
   {
      p=pic+(8+j*w)*3;
      for(i=8;i<wm4;i+=8)
      {
#ifdef T_USE_F
         filter_A3(p,  stride, 8,idx);
#else
         filter_A2(p,  stride, 8,idx);
#endif
         p+=24;

      }
   }


}

#endif
#endif
