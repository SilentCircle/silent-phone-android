/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2016, Silent Circle, LLC.  All rights reserved.

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

#include <string.h>
#include <math.h>



#define drawVec //

//#define INRTPI(D,S1,S2) {int _t_=(S1)+(S2);(D)=(_t_>>1)|(_t_&1);}
#define INRTPI(D,S1,S2) D=((S1)+(S2)+1)>>1;
//#define INRTP4 {*dst=((int)*src1+(int)*src2+(int)*src3+(int)*src4+2)>>2;src1++;src2++;dst++;src3++;src4++;}
#define INRTP4 *dst=(*src1+*src2+*src3+*src4+2)>>2;src1++;src2++;dst++;src3++;src4++;
#ifndef INRTP
#define INRTP INRTPI
#endif

#define T_ROT_AND 31
#define T_ROT_ADD 32
#define T_ROT_MULT 16
#define T_ROT_DSH 5
#define T_ROT_DSH_ADD_XY 6
#define T_ROT_AVG 16
#define T_ROT_AVG2 32


static int iUseRgb=1;
void useRGBinput(int f){
   iUseRgb=f;

}
//inline int mgabs(int a){return a>=0?a:-a;}
#if defined(__SYMBIAN32__) //|| defined(ANDROID_NDK)
//#include <libc\stdlib.h>
//#include <e32cmn.h> //abs
inline int abs(int a) {
   int mask = (a >> 31);//(sizeof(int) * CHAR_BIT - 1));
   return (a + mask) ^ mask;
}
#endif

#define mgabs(_a) abs(_a)

void filterX(unsigned char *p, int stride, int idx, int bl_size){}

#define clip255(_A_) {if((_A_)>255)(_A_)=255;else if((_A_)<0)(_A_)=0;}


inline void yuv2rgb(int y,int u,int v, int &r, int &g, int &b)
{
   y-=16;
   u-=128;
   v-=128;

   const int yc=298 * y+128;


   r = (( yc           + 409 * v ) >> 8);
   g = (( yc - 100 * u - 208 * v ) >> 8);
   b = (( yc + 516 * u           ) >> 8);

   clip255(r);
   clip255(g);
   clip255(b);
}

void toRgb(unsigned char *p, int w, int h)
{
   unsigned char *pEnd=w*h*3+p;
   int r,g,b;

   while(p<pEnd)
   {
      //yuv2rgb(*(p+0),*(p+1),*(p+2),r,g,b);
      yuv2rgb(*(p+0),*(p+1),*(p+2),b,g,r);
      //g=p[0];b=p[1];r=p[2]; //if(b<0)b=0;if(r<0)r=0;if(b>255)b=255;if(r>255)r=255;
      *(p+2)=r;
      *(p+1)=g;
      *(p+0)=b;
      p+=3;

   }
}

int isValidVector(int x, int y ,int pos1, int pos2,int iMBSize, int w ,int h)
{
   if(pos1+(x>>1)+iMBSize>w)return 0;
   if(pos2+(y>>1)+iMBSize>h)return 0;
   if(pos1+(x>>1)<0)return 0;
   if(pos2+(y>>1)<0)return 0;
   return 1;
}

#if 0
void filter_X_4(unsigned char *p, int xstride, int ystride, int l, int iCalc, int *c)
{
    int d;
    if(!iCalc){
       for( d = 0; d < l; d++ ) {
          if(c[d]){
            const int p0 = p[-1*xstride];
            const int p1 = p[-2*xstride];
            const int q0 = p[0];
            const int q1 = p[1*xstride];
            p[-xstride] = ( 2*p1 + p0 + q1 + 2 ) >> 2;   /* p0' */
            p[0]        = ( 2*q1 + q0 + p1 + 2 ) >> 2;   /* q0' */
          }

          p += ystride;
       }
    }
    else{
    for( d = 0; d < l; d++ ) {
        const int p0 = p[-1*xstride];
        const int p1 = p[-2*xstride];
        const int q0 = p[0];
        const int q1 = p[1*xstride];
        const int aDif=mgabs( p0 - q0 );

        if(aDif >2 &&  aDif < 20 &&
            mgabs(p1 - p0 ) < 5 &&
            mgabs( q1 - q0 ) < 5 ) {
            c[d]=1;
        }
        else c[d]=0;
        p += ystride;
    }
    }
}

void filter_4x4_center(unsigned char *p, int stride){
   int z=0;
   int r[(8+8)*3];
   for(z=0;z<3;z++){
      filter_X_4(p+12,3,stride,4,1,&r[0]);
      filter_X_4(p+stride*4,stride,3,8,1,&r[24]);
      filter_X_4(p+12+stride*4,3,stride,4,1,&r[36]);
      p++;
   }
   for(z=0;z<3;z++){
      filter_X_4(p+12,3,stride,4,0,&r[0]);
      filter_X_4(p+stride*4,stride,3,8,0,&r[24]);
      filter_X_4(p+12+stride*4,3,stride,4,0,&r[36]);
      p++;
   }
}
#endif

void toRgb(unsigned char *p, int w, int h, unsigned char *out)
{
   unsigned char *pTmp=p,*pEnd=w*h*3+p;
  // int r,g,b;
   unsigned char *getCropTbl();
   unsigned char *tab=getCropTbl();
   //static int tab

   while(p<pEnd)
   {
     int u = p[1]-128;
     int v = p[2]-128;

      const int yc=298 * ((int)p[0]-16)+128;


      out[0] = tab[(( yc           + 409 * v ) >> 8)];
      out[1] = tab[(( yc - 100 * u - 208 * v ) >> 8)];
      out[2] = tab[(( yc + 516 * u           ) >> 8)];

      //clip255(r);
      //clip255(g);
      //clip255(b);
      /*
      yuv2rgb(*(p+0),*(p+1),*(p+2),b,g,r);
      *(out+2)=r;
      *(out+1)=g;
      *(out+0)=b;
*/
      p+=3;
      out+=3;
   }
}






#define GET_MED3(_A,_B,_C) (min(max(_A, _B), min(max(_B, _C), max(_A, _C))))

static inline int median4(int a, int b, int c, int d)
{
   int ma,mi;
   ma = mi = a;
   if (b > ma)ma = b; else if (b < mi) mi = b;
   if (c > ma)ma = c; else if (c < mi) mi = c;
   if (d > ma)ma = d; else if (d < mi) mi = d;
   return  (a + b + c + d - ma - mi) / 2;
}


void getV_cntX(int *rx,int *x, int cnt)
{
   if(cnt==4){
      *rx=median4(x[0],x[1],x[2],x[3]);
      
   }
   else if(cnt==3){
      *rx=GET_MED3(x[0],x[1],x[2]);
      
   }else if(cnt==2){
      if(((x[0]>0 && x[1]<0) || (x[0]<0 && x[1]>0)))// && ((y[0]<0 && y[1]>0) || (y[0]>0 && y[1]<0)))
      {
         *rx=0;//GET_MED3(x[0],x[1],0);
         //*ry=0;//GET_MED3(y[0],y[1],0);
      }
      else{

      int sx=x[0]+x[1];if(sx<0)sx++;
      //int sy=y[0]+y[1];if(sy<0)sy+=2;

      *rx=(sx)>>1;
      //*ry=(sy)>>1;
      }
   }
   else if(cnt==1){
      *rx=x[0];
      //*ry=y[0];
   }
   else{
      *rx=0;//*ry=0;
   }
   //#define EVEN(A)		(((A)<0?(A)+1:(A)) & ~1)
   //rx[0]=EVEN(rx[0]);
   //ry[0]=EVEN(ry[0]);
}



static const int rotTabD8[]={//38
   2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,
   4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
   6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
 // 1,3,5,7,9,11,13,15,17,19,21,23,25,27,29,31,
  //8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,
 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,
   8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
   16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,
//23,22,21,20,19,18,17,16,16,15,14,13,12,11,10,9,8,
//31,29,27,25, 23,21,19,17, 15,13,11,9, 7,5,3,1,
10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,
//   12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,12,
 //  14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,
  // 16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,
  // 18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,18,
  // 20,20,20,20,20,20,20,20,20,20,20,20,20,20,20,20,
  // 22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,
  // 24,24,24,24,24,24,24,24,24,24,24,24,24,24,24,24,
  // 26,26,26,26,26,26,26,26,26,26,26,26,26,26,26,26,
  //17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,
  //24,24,24,24,24,24,24,24,24,24,24,24,24,24,24,24,
  //32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,
//   -1,-1,-1,-1,0,0,0,0, 0,0,0,0,1,1,1,1,
   //-2,-2,-1,-1,-1,-1, 0,0,0,0,1,1,1,1,2,2,
   //-4,-3,-3,-2,-2,-1,-1,0,0,1,1,2,2,3,3,4,
   //-59,-46,-33,-20,-7,6,-3, -1, 1,3,5,7,9,11,13,15,
   -15,-13,-11,-9,-7,-5,-3, -1, 1,3,5,7,9,11,13,15,
   -7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,
   -23,-20,-17,-14,-11, -8, -5,-2,1, 4, 7,10,13,16,19,22,
   -30,-26,-22,-18,-14,-10, -6,-2,2, 6,10,14,18,22,26,30,
   -37,-32,-26,-21,-17,-12, -7,-3,2, 7,12,17,21,26,32,37,
   -45,-39,-33,-27,-21,-15, -9,-3,3, 9,15,21,27,33,39,45,
   -52,-45,-38,-31,-24,-17,-10,-3,4,11,18,25,32,39,46,53,
   //-51,-45,-38,-30,-24,-18,-10,-3,3,10,18,24,30,38,45,51,
   -58,-50,-42,-34,-28,-20,-12,-4,4,12,20,28,34,42,50,58,
   -75,-65,-55,-45,-35,-25,-15,-5,5,15,25,35,45,55,65,75,
   -81,-72,-61,-50,-42,-30,-18,-6,6,18,30,42,50,61,72,81,
   -98,-85,-72,-59,-46,-33,-20,-7,6,19,32,45,58,71,84,96,
   -105,-91,-77,-63,-49,-35,-21,-7,7,21,35,49,63,77,91,105,
   
              0};

static const int rotTabD32[]={
2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,
4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13,14,14,15,15,16,16,17,
8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
16,16,15,15,14,14,13,13,12,12,11,11,10,10,9,9,8,8,7,7,6,6,5,5,4,4,3,3,2,2,1,1,
10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,
-15,-14,-13,-12,-11,-10,-9,-8,-7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,
-7,-6,-6,-5,-5,-4,-4,-3,-3,-2,-2,-1,-1,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,
-23,-21,-20,-18,-17,-15,-14,-12,-11,-9,-8,-6,-5,-3,-2,0,1,3,4,6,7,9,10,12,13,15,16,18,19,21,22,24,
-30,-28,-26,-24,-22,-20,-18,-16,-14,-12,-10,-8,-6,-4,-2,0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,32,
-37,-34,-32,-29,-26,-23,-21,-19,-17,-14,-12,-9,-7,-5,-3,0,2,5,7,10,12,15,17,19,21,24,26,29,32,35,37,40,
-45,-42,-39,-36,-33,-30,-27,-24,-21,-18,-15,-12,-9,-6,-3,0,3,6,9,12,15,18,21,24,27,30,33,36,39,42,45,48,
-52,-48,-45,-41,-38,-34,-31,-27,-24,-20,-17,-13,-10,-6,-3,1,4,8,11,15,18,22,25,29,32,36,39,43,46,50,53,57,
-58,-54,-50,-46,-42,-38,-34,-31,-28,-24,-20,-16,-12,-8,-4,0,4,8,12,16,20,24,28,31,34,38,42,46,50,54,58,62,
-75,-70,-65,-60,-55,-50,-45,-40,-35,-30,-25,-20,-15,-10,-5,0,5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,
-81,-76,-72,-66,-61,-55,-50,-46,-42,-36,-30,-24,-18,-12,-6,0,6,12,18,24,30,36,42,46,50,56,61,67,72,77,81,86,
-98,-91,-85,-78,-72,-65,-59,-52,-46,-39,-33,-26,-20,-13,-7,0,6,13,19,26,32,39,45,52,58,65,71,78,84,90,96,102,
};

static void moveMBRotJava(unsigned char *p, int ofsp, unsigned char *pOld,  int stride, int iSize, int r1, int r2, int mx, int my)
{

   const static int vx[]={32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0,8,7,6,5,4,3,2,1,8,7,6,5,4,3,2,1};
   const static int vy[]={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,0,1,2,3,4,5,6,7,0,1,2,3,4,5,6,7};
//   const static int vx[]={32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0,8,7,6,5,4,3,2,1,8,7,6,5,4,3,2,1};
  // const static int vy[]={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,0,1,2,3,4,5,6,7,0,1,2,3,4,5,6,7};
//   const static int vx[]={16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,8,7,6,5,4,3,2,1,8,7,6,5,4,3,2,1};
  // const static int vy[]={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,0,1,2,3,4,5,6,7,0,1,2,3,4,5,6,7};
//   const static int vx[]={8,7,6,5,4,3,2,1,8,7,6,5,4,3,2,1,8,7,6,5,4,3,2,1};
  // const static int vy[]={0,1,2,3,4,5,6,7,0,1,2,3,4,5,6,7,0,1,2,3,4,5,6,7};
   
   int mxd2=mx>>1;
   int myd2=my>>1;
   mx*=T_ROT_MULT;my*=T_ROT_MULT;
   int ofsox,ofsox_next;

   //pOld=pOld+mxd2*iStrideCol+myd2*stride;

   int *tabMov=iSize<17?(int*)&rotTabD8[(mgabs(r1|r2)-1)<<4]:(int*)&rotTabD32[(mgabs(r1|r2)-1)<<5];
   if(iSize==8)tabMov+=4;
   else if(iSize==12)tabMov+=2;
   int iSign=(r1|r2)<0?-1:1;
   const int iStrideCol=3;
   const int iSize3=iSize*iStrideCol;
   int CO,CN,CR;
   int COS,CNS;

   int i,j,v;
   if(r1)
   {
      int yadd=my&T_ROT_AND;
      for(j=0;j<iSize;j++)
      {
         v=tabMov[j]*iSign;
//         ofsox=((v+mx)>>T_ROT_DSH)*iStrideCol+myd2*stride;
         ofsox=((v+mx)>>T_ROT_DSH)*iStrideCol+myd2*stride;
         ofsox_next=ofsox+iStrideCol;

         int mv=(mx+v+16000)&T_ROT_AND;
         int vxmv=vx[mv];
         int vymv=vy[mv];
         int ofs=j*stride+ofsp;//+i;
         for(i=0;i<iSize3;i++,ofs++)//=iStrideCol)
         {
            CO=pOld[ofs+ofsox];
            CN=pOld[ofs+ofsox_next];
            if(yadd)
            {
               COS=pOld[ofs+ofsox+stride];
               CNS=pOld[ofs+ofsox_next+stride];
               CR=((CO+COS)*vxmv+(CN+CNS)*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY;
               //p[ofs]=((((pOld[ofs+ofsox]+pOld[ofs+ofsox+stride])*vxmv+(pOld[ofs+ofsox_next]+pOld[ofsox_next+ofs+stride])*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY));
            }
            else
            {
               CR=((CO)*vxmv+(CN)*vymv+T_ROT_AVG)>>T_ROT_DSH;
               //p[ofs]=(pOld[ofs+ofsox]*vxmv+pOld[ofs+ofsox_next]*vymv+T_ROT_AVG)>>T_ROT_DSH;
            }
            p[ofs]=CR;
         }
      }
   }
   else //if(0)//if(hor)
   {
      int xadd=mx&T_ROT_AND;
         int ofsInc=stride-2;
      for(j=0;j<iSize;j++)
      {
         v=tabMov[j]*iSign;
         ofsox=mxd2*iStrideCol+((my+v)>>T_ROT_DSH)*stride;
         ofsox_next=ofsox+stride;
         int mv=(my+v+16000)&T_ROT_AND;
         int vxmv=vx[mv];
         int vymv=vy[mv];
       // while(mv<0)mv+=T_ROT_ADD;
        // while(mv>T_ROT_AND)mv-=T_ROT_ADD;

         int ofs=j*3+ofsp;
         for(i=0;i<iSize;i++,ofs+=ofsInc)
         {
            if(xadd)
            {

               CO=pOld[ofs+ofsox]; CN=pOld[ofs+ofsox_next];
               COS=pOld[ofs+ofsox+iStrideCol];CNS=pOld[ofs+ofsox_next+iStrideCol];
               CR=((CO+COS)*vxmv+(CN+CNS)*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY;
               p[ofs]=CR;ofs++;
               CO=pOld[ofs+ofsox]; CN=pOld[ofs+ofsox_next];
               COS=pOld[ofs+ofsox+iStrideCol];CNS=pOld[ofs+ofsox_next+iStrideCol];
               CR=((CO+COS)*vxmv+(CN+CNS)*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY;
               p[ofs]=CR;ofs++;
               CO=pOld[ofs+ofsox]; CN=pOld[ofs+ofsox_next];
               COS=pOld[ofs+ofsox+iStrideCol];CNS=pOld[ofs+ofsox_next+iStrideCol];
               CR=((CO+COS)*vxmv+(CN+CNS)*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY;
               p[ofs]=CR;

               /*
               p[ofs]=((((pOld[ofs+ofsox]+pOld[ofs+iStrideCol+ofsox])*vxmv+(pOld[ofs+ofsox_next]+pOld[ofs+iStrideCol+ofsox_next])*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY));
               ofs++;
               p[ofs]=((((pOld[ofs+ofsox]+pOld[ofs+iStrideCol+ofsox])*vxmv+(pOld[ofs+ofsox_next]+pOld[ofs+iStrideCol+ofsox_next])*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY));
               ofs++;
               p[ofs]=((((pOld[ofs+ofsox]+pOld[ofs+iStrideCol+ofsox])*vxmv+(pOld[ofs+ofsox_next]+pOld[ofs+iStrideCol+ofsox_next])*vymv+T_ROT_AVG)>>T_ROT_DSH_ADD_XY));
               */
            }
            else
            {
               CO=pOld[ofs+ofsox]; CN=pOld[ofs+ofsox_next];
               CR=((CO)*vxmv+(CN)*vymv+T_ROT_AVG)>>T_ROT_DSH;
               p[ofs]=CR;ofs++;
               CO=pOld[ofs+ofsox]; CN=pOld[ofs+ofsox_next];
               CR=((CO)*vxmv+(CN)*vymv+T_ROT_AVG)>>T_ROT_DSH;
               p[ofs]=CR;ofs++;
               CO=pOld[ofs+ofsox]; CN=pOld[ofs+ofsox_next];
               CR=((CO)*vxmv+(CN)*vymv+T_ROT_AVG)>>T_ROT_DSH;
               p[ofs]=CR;
               /*
               p[ofs]=(pOld[ofs+ofsox]*vxmv+pOld[ofs+ofsox_next]*vymv+T_ROT_AVG)>>T_ROT_DSH;
               ofs++;
               p[ofs]=(pOld[ofs+ofsox]*vxmv+pOld[ofs+ofsox_next]*vymv+T_ROT_AVG)>>T_ROT_DSH;
               ofs++;
               p[ofs]=(pOld[ofs+ofsox]*vxmv+pOld[ofs+ofsox_next]*vymv+T_ROT_AVG)>>T_ROT_DSH;
               */
            }
         }
      }
   }

}

inline void memcpyIHV(unsigned char *dst
                      , unsigned char *src1, unsigned char *src2
                      , unsigned char *src3, unsigned char *src4
                      , int iLen) 
{
   register unsigned char *pEnd=dst+iLen;
   while(dst<pEnd)
   {
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
      INRTP4
   }
}

inline void memcpyI(unsigned char *dst, unsigned char *src1, unsigned char *src2, int iLen) 
{

   //register unsigned char *src1=src1i;
   //register unsigned char *src2=src2i;
   //register unsigned char *dst=dsti;
/*
   int iDelta=(int)(src2-src1);
   register unsigned char *m1=src1-iDelta;
   register unsigned char *p1=src2+iDelta;
   register unsigned char *m2=m1-iDelta;
   register unsigned char *p2=p1+iDelta;
*/
   register unsigned char *pEnd=dst+iLen;
   while(dst<pEnd)
   {
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;

      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;

      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;

      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;


//      INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
  //    INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
    //  INRTP(*dst,*src1,*src2);src1++;src2++;dst++;
   }
}


static inline void copy8V2(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs)
{

      while(hs)
      {
         memcpy(p,pOld,ws);
        // for(i=0;i<ws;i++)((int*)p)[i]=((int*)pOld)[i];
         pOld+=stride;
         p+=stride;
         hs--;
      }
}
static inline void copy8VX(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs)
{
   while(hs)
   {
      memcpyI(p,pOld,pOld+3,ws);
      pOld+=stride;
      p+=stride;
      hs--;
   }
}
static inline void copy8VY(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs)
{
   while(hs)
   {
      memcpyI(p,pOld,pOld+stride,ws);
      pOld+=stride;
      p+=stride;
      hs--;
   }
}
static inline void copy8VXY(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs)
{
   //int i=hs;
   while(hs)
   {
      memcpyIHV(p,pOld,pOld+3,pOld+stride,pOld+3+stride,ws);
      pOld+=stride;
      p+=stride;
      hs--;
   }
}

static inline void copy8V2_str2(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs, int strideDst)
{
   while(hs)
   {
      memcpy(p,pOld,ws);
     // for(i=0;i<ws;i++)((int*)p)[i]=((int*)pOld)[i];
      pOld+=stride;
      p+=strideDst;
      hs--;
   }
}
static inline void copy8VX_str2(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs, int strideDst)
{
   while(hs)
   {
      memcpyI(p,pOld,pOld+3,ws);
      pOld+=stride;
      p+=strideDst;
      hs--;
   }
}
static inline void copy8VY_str2(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs, int strideDst)
{
   while(hs)
   {
      memcpyI(p,pOld,pOld+stride,ws);
      pOld+=stride;
      p+=strideDst;
      hs--;
   }
}
static inline void copy8VXY_str2(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs, int strideDst)
{
   //int i=hs;
   while(hs)
   {
      memcpyIHV(p,pOld,pOld+3,pOld+stride,pOld+3+stride,ws);
      pOld+=stride;
      p+=strideDst;
      hs--;
   }
}



static inline void copyV(unsigned char *p, unsigned char *pOld, int stride, int avc1, int avc2, int ws, int hs,int iFilter=0, int strideDst=0)
{
   typedef void (*f)(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs); 
   typedef void (*f_s)(unsigned char *p, unsigned char *pOld, int stride, int ws, int hs, int strideDst); 

   const static f fn[]={copy8V2,copy8VX,copy8VY,copy8VXY};
   
   const static f_s fn_s[]={copy8V2_str2,copy8VX_str2,copy8VY_str2,copy8VXY_str2};
   

   const int v1d2=(avc1)&1;
   const int v2d2=(avc2)&1;
#define FAST_DIV2_T(_A) {_A>>=1;}
   FAST_DIV2_T(avc1);
   FAST_DIV2_T(avc2);
   //pOld=pOld+avc1*3+stride*avc2;
   if(strideDst)
      fn_s[v1d2|(v2d2<<1)](p,&pOld[avc1*3+stride*avc2],stride, ws,hs,strideDst);
   else 
      fn[v1d2|(v2d2<<1)](p,&pOld[avc1*3+stride*avc2],stride, ws,hs);
}



const int tabMode[]={2,4,8,16};
inline void moveBi(unsigned char *p, unsigned char *ref1, T_CUR_VECTOR *v1, unsigned char *ref2, T_CUR_VECTOR *v2, int iSize , int stride){
   //int v1x=
   //unsigned char p1[48*16*3];
   //unsigned char p2[48*16*3];
   static unsigned char *p1=NULL;
   static unsigned char *p2=NULL;
   if(p1==NULL){
      p1=new unsigned char [32*32*3*4+64];
      p2=new unsigned char [32*32*3*4+64];
   }
   
   unsigned char *pr1;
   unsigned char *pr2;
   int strider1;
   int strider2;

   

   /*if(r1|r2){
      pr1=&p1[0];
      strider1=iSize*3;
      moveMBRotJava(p-ofs,ofs,ref1-ofs,stride,iSize,r1,r2,v1->x,v1->y);
      for(int add=0;add<iSize;add++)memcpy(&p1[add*strider1],p+add*stride,strider1);
   }
   else
*/
   if(((v1->x|v1->y)&1)==0){
      pr1=ref1+(v1->x>>1)*3+(v1->y>>1)*stride;
      strider1=stride;
   }
   else{
      pr1=&p1[0];
      strider1=iSize*3;
      copyV(pr1,ref1,stride,v1->x,v1->y,strider1,iSize,0,strider1);
   }
   if(((v2->x|v2->y)&1)==0){
      pr2=ref2+(v2->x>>1)*3+(v2->y>>1)*stride;
      strider2=stride;
   }
   else{
      pr2=&p2[0];
      strider2=iSize*3;
      copyV(pr2,ref2,stride,v2->x,v2->y,strider2,iSize,0,strider2);
   }
      //TODO if (p1 && p2 used)memcpyI32
//   int l=iSize==16?6:(iSize==8?3:12);//64

   for(int add=0;add<iSize;add++)
      memcpyI(p+add*stride,&pr1[add*strider1],&pr2[add*strider2],iSize*3);


}
#if 0

void filter_X_4(unsigned char *p, int xstride, int ystride, int l, int iCalc, int *c)
{
    int d;
    if(!iCalc){
       for( d = 0; d < l; d++ ) {
          if(c[d]){
            const int p0 = p[-1*xstride];
            const int p1 = p[-2*xstride];
            const int q0 = p[0];
            const int q1 = p[1*xstride];
            p[-xstride] = ( 2*p1 + p0 + q1 + 2 ) >> 2;   /* p0' */
            p[0]        = ( 2*q1 + q0 + p1 + 2 ) >> 2;   /* q0' */
          }

          p += ystride;
       }
    }
    else{
       for( d = 0; d < l; d++ ) {
           const int p0 = p[-1*xstride];
           const int p1 = p[-2*xstride];
           const int q0 = p[0];
           const int q1 = p[1*xstride];
           const int aDif=mgabs( p0 - q0 );

           if(aDif >2 &&  aDif < 20 &&
               mgabs(p1 - p0 ) < 5 &&
               mgabs( q1 - q0 ) < 5 ) {
               c[d]=1;
           }
           else c[d]=0;
           p += ystride;
       }
    }

}
void filter_4x4_center(unsigned char *p, int stride){
   int z=0;
   int r[(8+8)*3];
/*
   for(z=0;z<3;z++){
      filter_X_4(p+12,3,stride,4,1,&r[0]);
      filter_X_4(p+stride*4,stride,3,8,1,&r[24]);
      filter_X_4(p+12+stride*4,3,stride,4,1,&r[36]);

      p++;
   }
   for(z=0;z<3;z++){
      filter_X_4(p+12,3,stride,4,0,&r[0]);
      filter_X_4(p+stride*4,stride,3,8,0,&r[24]);
      filter_X_4(p+12+stride*4,3,stride,4,0,&r[36]);
      p++;
   }
   */
   return;
   for(z=0;z<3;z++){
      filter_X_4(p+12      ,3,stride,8,1,&r[0]);
      filter_X_4(p+stride*4,stride,3,8,1,&r[24]);

      filter_X_4(p+12      ,3,stride,8,0,&r[0]);
      filter_X_4(p+stride*4,stride,3,8,0,&r[24]);
      p++;
   }
}
#endif

#define iDrawVec 0

char *pFlagsDeblock[2]={0,0};
char *getDeblFlags(int iDec, int w, int h){

   static int xcxyc[2]={0,0};
   if(pFlagsDeblock[iDec]==NULL || xcxyc[iDec]!=w*h){
      xcxyc[iDec]=w*h;//(h*w+128)>>8;
      delete pFlagsDeblock[iDec];
      pFlagsDeblock[iDec]=new char [((h+16)*(w+16)+128)>>8];
      memset(pFlagsDeblock[iDec],0,((h+16)*(w+16)+128)>>8);
   }
   return pFlagsDeblock[iDec];
}

int amoveVectorsRefMB4(int iMBSize2, MB_4 *mb4, unsigned char *p, REFPIC *pic, int w, int h, int iIsB, int iDec){
//   int iIsB=iCur+1!=iGrSize;
   //iMBSize2=16;

//   initMoveBl(&move_blck);

   const int xc=w/iMBSize2;
   const int yc=h/iMBSize2;
   int i,j;


   char *dbl=getDeblFlags(iDec,w,h);
   
   const int stride=w*3;
   const int iMBSize2x3=iMBSize2*3;
   const int iMBSizex3=iMBSize2x3>>1;
   const int iMBSizeDx3=iMBSize2x3>>2;
   const int iMBSize=iMBSize2>>1;
   const int iMBSizeD=iMBSize2>>2;
   
   unsigned char *r;
   int iMVMode;
   int m2,m4;
   int m4To;
    int ofs;
   int ofs2x;
   int ofs4x;
 
   const int xy2x2[]={0, iMBSizex3,  iMBSize * stride, iMBSizex3 + iMBSize * stride};
   const int xy4x4[]={0,iMBSizeDx3, iMBSizeD * stride, iMBSizeDx3 + iMBSizeD * stride};

/*
   const int dx2x2[]={0,iMBSize,0,iMBSize};
   const int dy2x2[]={0,0,iMBSize,iMBSize};

   const int dx4x4[]={0,iMBSizeD,0,iMBSizeD};
   const int dy4x4[]={0,0,iMBSizeD,iMBSizeD};
  */
   MB_4 *mb4o=mb4;
   iIsB=!!iIsB;
   
//static int xsk=1;xsk=!xsk;if(xsk==1)return 0;

   void filter_4x4_16_ab(unsigned char *p, int stride, int idx);
   void filter_8x8_16_ab(unsigned char *p, int stride, int idx);
   void filter_4x4_8_ab(unsigned char *p, int stride, int idx);
   void filter_borders_16(unsigned char *p, int stride, int idx);


   for(i=0;i<xc+1;i++)dbl[i]=0;
   for(j=0;j<yc;j++)
   {
      ofs=j*iMBSize2*stride;
      for(i=0;i<xc;i++,mb4++,ofs+=iMBSize2x3,dbl++){
       dbl[xc+1]=0;
       const int m=mb4->iMVMode;
       if(iMBSize2==16){

       if(i){
          MB_4 *mbTL=mb4-1;
          int bi=mb4->iIsBi==mbTL->iIsBi;
          if(!bi || m || (!mb4->iIsBi && (mbTL->refId!=mb4->refId || mbTL->mv2[0]!=mb4->mv2[0] || (mgabs(mbTL->r.s[0]-mb4->r.s[0])>5 || mgabs(mbTL->r.s[1]-mb4->r.s[1])>5))) || 
             mbTL->iMVMode ||
              (bi && mb4->iIsBi && ((iIsB && mb4->vrefs[2]!=mbTL->vrefs[2]) || mb4->vrefs[1]!=mbTL->vrefs[1] || (!iIsB && mb4->vrefs[0]!=mbTL->vrefs[0])))
             )
               dbl[0]|=2;
         }
       if(j){
          MB_4 *mbTL=mb4-xc;
          int bi=mb4->iIsBi==mbTL->iIsBi;
          if(!bi || m || (!mb4->iIsBi && (mbTL->refId!=mb4->refId || mbTL->mv2[0]!=mb4->mv2[0] || (mgabs(mbTL->r.s[0]-mb4->r.s[0])>5 || mgabs(mbTL->r.s[1]-mb4->r.s[1])>5))) || 
             mbTL->iMVMode ||
              (bi && mb4->iIsBi && ((iIsB && mb4->vrefs[2]!=mbTL->vrefs[2]) || mb4->vrefs[1]!=mbTL->vrefs[1] || (!iIsB && mb4->vrefs[0]!=mbTL->vrefs[0])))
             )
                dbl[0]|=1;
         }
         if(m==1)dbl[0]|=16;  
       }
         
         
        // const int iFilterBl= 0;//i && j;
//   void filterX(unsigned char *p, int stride, int idx, int bl_size);
         //iUseTap6=mb4->refId==-2;
         


         if(mb4->iIsBi)// && (m==0||iDec))// && iIsB && mb4->refId!=-2)
         {
            int _rid;
            if(iIsB){
               _rid=mb4->refId==-1?mb4->eNext:mb4->eM1;
               moveBi(p+ofs,//mb4->r1,mb4->r2,ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?1:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
            }
            else{
            
               _rid=mb4->refId==-1?mb4->eM2:mb4->eM1;
               moveBi(p+ofs,//mb4->r1,mb4->r2,ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?-2:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
            }

               if(iDrawVec&1){
                  drawVec(i*iMBSize2,j*iMBSize2+1,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,800);
                  drawVec(i*iMBSize2,j*iMBSize2-1,mb4->vrefs[_rid].x,mb4->vrefs[_rid].y,iMBSize2,w,h,800);
               }
               //if(i && j) filter_borders_16(p+ofs,  stride, 64);

            continue;

         }

         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         if(m==0)
         {
            
            //if(!mb4->mv2[0].iSad)mb4->r2=12;
            if(mb4->r.i){
               moveMBRotJava(p,ofs,r,stride,iMBSize2,mb4->r.s[0],mb4->r.s[1],mb4->mv2[0].x,mb4->mv2[0].y);

               //if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,mb4->r1?(1000+mb4->r1):(2000+mb4->r2));
            }
            else 
            {

               copyV(p+ofs,r+ofs,stride,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2x3,iMBSize2);
               //if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,mb4->refId);
            }
            //if(i && j) filter_borders_16(p+ofs,  stride, 64);
            
         }
         else {
            
            iMVMode=(m&~1);

            for(m2=0;m2<4;m2++)
            {
               ofs2x=ofs+xy2x2[m2];
               if((tabMode[m2] & iMVMode)==0){
                  copyV(p+ofs2x,r+ofs2x,stride,mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSizex3,iMBSize);
                 // if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2],j*iMBSize2+dy2x2[m2],mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSize,w,h,mb4->refId);
               }
               else{
                  m4To=(m2+1)<<2;
                  
                  for(m4=m2<<2;m4<m4To;m4++){
                     ofs4x=ofs2x+xy4x4[m4&3];
                     copyV(p+ofs4x,r+ofs4x,stride,mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeDx3,iMBSizeD);
//if(iFilterBl)filterX(p+ofs,stride,30,iMBSizeD);
                   //  if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2]+dx4x4[m4&3],j*iMBSize2+dy2x2[m2]+dy4x4[m4&3],mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeD,w,h,mb4->refId);
                  }
                  //void filter_4x4_center(unsigned char *p, int stride, int idx);
                  //--filter_4x4_8_ab(p+ofs2x,stride,12<<iIsB);
                  //filter_4x4_8_ab(p+ofs2x+1,stride,18);
                 // filter_4x4_8_ab(p+ofs2x+2,stride,18);
               }
            }
            
         }            
         //--filter_8x8_16_ab(p+ofs,stride,16<<iIsB);
         //if(i && j) filter_borders_16(p+ofs,  stride, 64);
     //    filter_8x8_16_ab(p+ofs+1,stride,17);
       //  filter_8x8_16_ab(p+ofs+2,stride,17);
         
      }
   }
   
   int wm=w-xc*iMBSize2;
   int w1=0;
   if(wm &&(wm==16 || wm==8 || wm==4 || wm==24 || wm==12 || wm==48)){
      w1=1;
      mb4=mb4o+xc-1;
      ofs=xc*iMBSize2x3;
      int wm3=wm*3;
      for(i=0;i<h-iMBSize2+1;i+=iMBSize2,ofs+=stride*iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[5]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vx>0)vx=0;
         if(i+iMBSize2+(vy>>1)>h)vy=(h-(i+iMBSize2))>>1;
         copyV(p+ofs,r+ofs,stride,vx,vy,wm3,iMBSize2);
         mb4+=xc;
      }
   }
   wm=h-yc*iMBSize2;
   if(wm){// &&(wm==16 || wm==8 || wm==4 || wm==24)){
      mb4=mb4o+xc*(yc-1);
      ofs=yc*stride*iMBSize2;
      //int wm3=wm*3;
      for(i=0;i<w-iMBSize2+1;i+=iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(i+iMBSize2+(vx>>1)>w)vx=(w-(i+iMBSize2))>>1;

         copyV(p+ofs,r+ofs,stride,vx,vy,iMBSize2x3,wm);
         mb4++;
         ofs+=iMBSize2x3;
      }
      if(w1){
         mb4--;
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(vx>0)vy=0;
         copyV(p+ofs,r+ofs,stride,vx,vy,(w-xc*iMBSize2)*3,wm);
      }
   }

   return 0;
}
#include "block.h"
T_MOVE_PEL move_blck;

static inline int getVOfs(int x, int y, int stride){
   return (x>>1)*3+(y>>1)*stride;
}
#define PEL_ID(_X,_Y) ((_X&1)|((_Y&1)<<1))

static inline void moveB(t_move_pel_fnc *fnc, int x, int y, unsigned char *dst, unsigned char *p, int stride){
   const int o2=(x>>1)*3+(y>>1)*stride;
   fnc[PEL_ID(x,y)](dst,p+o2,stride,stride);
}

static inline void moveBIFNC(t_move_bi_fnc bi_fnc, t_move_pel_fnc *fnc
                             , int x, int y, int x1, int y1, unsigned char *dst, unsigned char *p,unsigned char *p2,const int stride){
   const int o=(x>>1)*3+(y>>1)*stride;
   const int o2=(x1>>1)*3+(y1>>1)*stride;
//typedef void (*t_move_bi_fnc)(unsigned char *dst, unsigned char *src1, t_move_pel_fnc *fnc1,
  //                                                           unsigned char *src2, t_move_pel_fnc *fnc2, const int stride);
   const int id=PEL_ID(x,y);
   const int id2=PEL_ID(x1,y1);
   
   t_move_pel_fnc *f1;
   t_move_pel_fnc *f2;

   if(id)f1=&fnc[id];else f1=NULL;
   if(id2)f2=&fnc[id2];else f2=NULL;
   bi_fnc(dst,p+o,f1,p2+o2,f2,stride);
}



int moveVectorsRefMB4(int iMBSize2, MB_4 *mb4, unsigned char *p, REFPIC *pic, int w, int h, int iIsB, int iDec){
//   int iIsB=iCur+1!=iGrSize;
   //iMBSize2=16;


   if(iMBSize2!=32 && iMBSize2!=16){
      return amoveVectorsRefMB4(iMBSize2,mb4,p,pic,w,h,iIsB, iDec);
   }
   initMoveBl(&move_blck);

   int move_add=iMBSize2==32;

   t_move_bi_fnc mbi=move_blck.mbi[T_MOVE_PEL::eWH16+move_add];
   t_move_pel_fnc *mo1=move_blck.m[T_MOVE_PEL::eWH16+move_add];
   t_move_pel_fnc *mo2=move_blck.m[T_MOVE_PEL::eWH8+move_add];
   t_move_pel_fnc *mo4=move_blck.m[T_MOVE_PEL::eWH4+move_add];
/*
   if(iDec){
   mbi=move_blck.mbit[T_MOVE_PEL::eWH16+move_add];

   mo1=move_blck.mt[T_MOVE_PEL::eWH16+move_add];
   mo2=move_blck.mt[T_MOVE_PEL::eWH8+move_add];
   mo4=move_blck.mt[T_MOVE_PEL::eWH4+move_add];
   }
*/


   const int xc=w/iMBSize2;
   const int yc=h/iMBSize2;
   int i,j;


   char *dbl=getDeblFlags(iDec,w,h);

   const int stride=w*3;
   const int iMBSize2x3=iMBSize2*3;
   const int iMBSizex3=iMBSize2x3>>1;
   const int iMBSizeDx3=iMBSize2x3>>2;
   const int iMBSize=iMBSize2>>1;
   const int iMBSizeD=iMBSize2>>2;
   
   unsigned char *r;
   int iMVMode;
   int m2,m4;
   int m4To;
    int ofs;
   int ofs2x;
   int ofs4x;
 
   const int xy2x2[]={0, iMBSizex3,  iMBSize * stride, iMBSizex3 + iMBSize * stride};
   const int xy4x4[]={0,iMBSizeDx3, iMBSizeD * stride, iMBSizeDx3 + iMBSizeD * stride};

   const int dx2x2[]={0,iMBSize,0,iMBSize};
   const int dy2x2[]={0,0,iMBSize,iMBSize};

   const int dx4x4[]={0,iMBSizeD,0,iMBSizeD};
   const int dy4x4[]={0,0,iMBSizeD,iMBSizeD};
   MB_4 *mb4o=mb4;
   iIsB=!!iIsB;
   
//static int xsk=1;xsk=!xsk;if(xsk==1)return 0;

   void filter_4x4_16_ab(unsigned char *p, int stride, int idx);
   void filter_8x8_16_ab(unsigned char *p, int stride, int idx);
   void filter_4x4_8_ab(unsigned char *p, int stride, int idx);
   void filter_borders_16(unsigned char *p, int stride, int idx);


   for(i=0;i<xc+1;i++)dbl[i]=0;
   for(j=0;j<yc;j++)
   {
      ofs=j*iMBSize2*stride;
      for(i=0;i<xc;i++,mb4++,ofs+=iMBSize2x3,dbl++){
       dbl[xc+1]=0;
       //if(iDec)continue;
       const int m=mb4->iMVMode;
       if(iMBSize2==16){

       if(i){
          MB_4 *mbTL=mb4-1;
          int bi=mb4->iIsBi==mbTL->iIsBi;
          if(!bi || m || (!mb4->iIsBi && (mbTL->refId!=mb4->refId || mbTL->mv2[0]!=mb4->mv2[0] || (mgabs(mbTL->r.s[0]-mb4->r.s[0])>5 || mgabs(mbTL->r.s[1]-mb4->r.s[1])>5))) || 
             mbTL->iMVMode ||
              (bi && mb4->iIsBi && ((iIsB && mb4->vrefs[2]!=mbTL->vrefs[2]) || mb4->vrefs[1]!=mbTL->vrefs[1] || (!iIsB && mb4->vrefs[0]!=mbTL->vrefs[0])))
             )
               dbl[0]|=2;
         }
       if(j){
          MB_4 *mbTL=mb4-xc;
          int bi=mb4->iIsBi==mbTL->iIsBi;
          if(!bi || m || (!mb4->iIsBi && (mbTL->refId!=mb4->refId || mbTL->mv2[0]!=mb4->mv2[0] || (mgabs(mbTL->r.s[0]-mb4->r.s[0])>5 || mgabs(mbTL->r.s[1]-mb4->r.s[1])>5))) || 
             mbTL->iMVMode ||
              (bi && mb4->iIsBi && ((iIsB && mb4->vrefs[2]!=mbTL->vrefs[2]) || mb4->vrefs[1]!=mbTL->vrefs[1] || (!iIsB && mb4->vrefs[0]!=mbTL->vrefs[0])))
             )
                dbl[0]|=1;
         }
         if(m==1)dbl[0]|=16;  
       }
         
         
        // const int iFilterBl= 0;//i && j;
//   void filterX(unsigned char *p, int stride, int idx, int bl_size);
         //iUseTap6=mb4->refId==-2;
         


         if(mb4->iIsBi)// && (m==0||iDec))// && iIsB && mb4->refId!=-2)
         {
            int _rid;
            if(iIsB){
               _rid=mb4->refId==-1?mb4->eNext:mb4->eM1;
#if 1
               moveBIFNC(mbi,mo1
                  ,mb4->mv2[0].x,mb4->mv2[0].y,mb4->vrefs[_rid].x,mb4->vrefs[_rid].y
                  ,p+ofs,pic[mb4->refId>0?1:mb4->refId].pPic + ofs,pic[mb4->refId==-1?1:-1].pPic + ofs,stride);
#else
               moveBi(p+ofs,//mb4->r.s[0],mb4->r.s[1],ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?1:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
                  
#endif
            }
            else{
            
               _rid=mb4->refId==-1?mb4->eM2:mb4->eM1;
#if 1
               moveBIFNC(mbi,mo1
                  ,mb4->mv2[0].x,mb4->mv2[0].y,mb4->vrefs[_rid].x,mb4->vrefs[_rid].y
                  ,p+ofs,pic[mb4->refId>0?1:mb4->refId].pPic + ofs,pic[mb4->refId==-1?-2:-1].pPic + ofs,stride);
#else
               moveBi(p+ofs,//mb4->r.s[0],mb4->r.s[1],ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?-2:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
#endif
            }

               if(iDrawVec&1){
               //   drawVec(i*iMBSize2,j*iMBSize2+1,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,800);
                 // drawVec(i*iMBSize2,j*iMBSize2-1,mb4->vrefs[_rid].x,mb4->vrefs[_rid].y,iMBSize2,w,h,800);
               }
               //if(i && j) filter_borders_16(p+ofs,  stride, 64);

            continue;

         }

         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         if(m==0)
         {
            
            //if(!mb4->mv2[0].iSad)mb4->r.s[1]=12;
            if(mb4->r.i){
               moveMBRotJava(p,ofs,r,stride,iMBSize2,mb4->r.s[0],mb4->r.s[1],mb4->mv2[0].x,mb4->mv2[0].y);

        //       if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,mb4->r.s[0]?(1000+mb4->r.s[0]):(2000+mb4->r.s[1]));
            }
            else 
            {
#if  0
               copyV(p+ofs,r+ofs,stride,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2x3,iMBSize2);
               
               //--mo1[PEL_ID(mb4->mv2[0].x,mb4->mv2[0].y)](p+ofs,r+ofs+getVOfs(mb4->mv2[0].x,mb4->mv2[0].y,stride),stride,stride);
#else
               moveB(mo1,mb4->mv2[0].x,mb4->mv2[0].y,p+ofs,r+ofs,stride);
#endif
          //     if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,mb4->refId);
            }
            //if(i && j) filter_borders_16(p+ofs,  stride, 64);
            
         }
         else {
            
            iMVMode=(m&~1);

            for(m2=0;m2<4;m2++)
            {
               ofs2x=ofs+xy2x2[m2];
               if((tabMode[m2] & iMVMode)==0){
#if  0
                copyV(p+ofs2x,r+ofs2x,stride,mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSizex3,iMBSize);
#else
//--               mo2[PEL_ID(mb4->mv2[m2].x,mb4->mv2[m2].y)](p+ofs2x,r+ofs2x+getVOfs(mb4->mv2[m2].x,mb4->mv2[m2].y,stride),stride,stride);
                  moveB(mo2,mb4->mv2[m2].x,mb4->mv2[m2].y,p+ofs2x,r+ofs2x,stride);
#endif
//
  //                if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2],j*iMBSize2+dy2x2[m2],mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSize,w,h,mb4->refId);
               }
               else{
                  m4To=(m2+1)<<2;
                  
                  for(m4=m2<<2;m4<m4To;m4++){
                     ofs4x=ofs2x+xy4x4[m4&3];
#if  0
                     copyV(p+ofs4x,r+ofs4x,stride,mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeDx3,iMBSizeD);
#else
//--mo4[PEL_ID(mb4->mv4[m4].x,mb4->mv4[m4].y)](p+ofs4x,r+ofs4x+getVOfs(mb4->mv4[m4].x,mb4->mv4[m4].y,stride),stride,stride);
                     moveB(mo4,mb4->mv4[m4].x,mb4->mv4[m4].y,p+ofs4x,r+ofs4x,stride);
#endif
    //                 if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2]+dx4x4[m4&3],j*iMBSize2+dy2x2[m2]+dy4x4[m4&3],mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeD,w,h,mb4->refId);
                  }
                  //void filter_4x4_center(unsigned char *p, int stride, int idx);
                  //--filter_4x4_8_ab(p+ofs2x,stride,12<<iIsB);
                  //filter_4x4_8_ab(p+ofs2x+1,stride,18);
                 // filter_4x4_8_ab(p+ofs2x+2,stride,18);
               }
            }
            
         }            
         //--filter_8x8_16_ab(p+ofs,stride,16<<iIsB);
         //if(i && j) filter_borders_16(p+ofs,  stride, 64);
     //    filter_8x8_16_ab(p+ofs+1,stride,17);
       //  filter_8x8_16_ab(p+ofs+2,stride,17);
         
      }
   }
   
   int wm=w-xc*iMBSize2;
   int w1=0;
   if(wm &&(wm==16 || wm==8 || wm==4 || wm==24 || wm==12 || wm==48)){
      w1=1;
      mb4=mb4o+xc-1;
      ofs=xc*iMBSize2x3;
      int wm3=wm*3;
      for(i=0;i<h-iMBSize2+1;i+=iMBSize2,ofs+=stride*iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[5]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vx>0)vx=0;
         if(i+iMBSize2+(vy>>1)>h)vy=(h-(i+iMBSize2))>>1;
         copyV(p+ofs,r+ofs,stride,vx,vy,wm3,iMBSize2);
         mb4+=xc;
      }
   }
   wm=h-yc*iMBSize2;
   if(wm){// &&(wm==16 || wm==8 || wm==4 || wm==24)){
      mb4=mb4o+xc*(yc-1);
      ofs=yc*stride*iMBSize2;
      //int wm3=wm*3;
      for(i=0;i<w-iMBSize2+1;i+=iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(i+iMBSize2+(vx>>1)>w)vx=(w-(i+iMBSize2))>>1;

         copyV(p+ofs,r+ofs,stride,vx,vy,iMBSize2x3,wm);
         mb4++;
         ofs+=iMBSize2x3;
      }
      if(w1){
         mb4--;
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(vx>0)vy=0;
         copyV(p+ofs,r+ofs,stride,vx,vy,(w-xc*iMBSize2)*3,wm);
      }
   }

   return 0;
}
#if 0
int moveVectorsRefMB4(int iMBSize2, MB_4 *mb4, unsigned char *p, REFPIC *pic, int w, int h, int iIsB, int iDec){
//   int iIsB=iCur+1!=iGrSize;
   //iMBSize2=16;
   int xc=w/iMBSize2;
   int yc=h/iMBSize2;
   int i,j;


   char *dbl=getDeblFlags(iDec,w,h);
   
   const int stride=w*3;
   const int iMBSize2x3=iMBSize2*3;
   const int iMBSizex3=iMBSize2x3>>1;
   const int iMBSizeDx3=iMBSize2x3>>2;
   const int iMBSize=iMBSize2>>1;
   const int iMBSizeD=iMBSize2>>2;
   
   unsigned char *r;
   int iMVMode;
   int m2,m4;
   int m4To;
    int ofs;
   int ofs2x;
   int ofs4x;
 
   const int xy2x2[]={0, iMBSizex3,  iMBSize * stride, iMBSizex3 + iMBSize * stride};
   const int xy4x4[]={0,iMBSizeDx3, iMBSizeD * stride, iMBSizeDx3 + iMBSizeD * stride};

   const int dx2x2[]={0,iMBSize,0,iMBSize};
   const int dy2x2[]={0,0,iMBSize,iMBSize};

   const int dx4x4[]={0,iMBSizeD,0,iMBSizeD};
   const int dy4x4[]={0,0,iMBSizeD,iMBSizeD};
   MB_4 *mb4o=mb4;
   iIsB=!!iIsB;
   
//static int xsk=1;xsk=!xsk;if(xsk==1)return 0;
/*
   void filter_4x4_16_ab(unsigned char *p, int stride, int idx);
   void filter_8x8_16_ab(unsigned char *p, int stride, int idx);
   void filter_4x4_8_ab(unsigned char *p, int stride, int idx);
   void filter_borders_16(unsigned char *p, int stride, int idx);
*/

   for(i=0;i<xc+1;i++)dbl[i]=0;

   for(j=0;j<yc;j++)
   {
      ofs=j*iMBSize2*stride;
      for(i=0;i<xc;i++,mb4++,ofs+=iMBSize2x3,dbl++){
       dbl[xc+1]=0;
       const int m=mb4->iMVMode;
       if(iMBSize2==16){

       if(i){
          MB_4 *mbTL=mb4-1;
          int bi=mb4->iIsBi==mbTL->iIsBi;
          if(!bi || m || (!mb4->iIsBi && (mbTL->refId!=mb4->refId || mbTL->mv2[0]!=mb4->mv2[0] || (mgabs(mbTL->r1-mb4->r1)>5 || mgabs(mbTL->r2-mb4->r2)>5))) || 
             mbTL->iMVMode ||
              (bi && mb4->iIsBi && ((iIsB && mb4->vrefs[2]!=mbTL->vrefs[2]) || mb4->vrefs[1]!=mbTL->vrefs[1] || (!iIsB && mb4->vrefs[0]!=mbTL->vrefs[0])))
             )
               dbl[0]|=2;
         }
       if(j){
          MB_4 *mbTL=mb4-xc;
          int bi=mb4->iIsBi==mbTL->iIsBi;
          if(!bi || m || (!mb4->iIsBi && (mbTL->refId!=mb4->refId || mbTL->mv2[0]!=mb4->mv2[0] || (mgabs(mbTL->r1-mb4->r1)>5 || mgabs(mbTL->r2-mb4->r2)>5))) || 
             mbTL->iMVMode ||
              (bi && mb4->iIsBi && ((iIsB && mb4->vrefs[2]!=mbTL->vrefs[2]) || mb4->vrefs[1]!=mbTL->vrefs[1] || (!iIsB && mb4->vrefs[0]!=mbTL->vrefs[0])))
             )
                dbl[0]|=1;
         }
         if(m==1)dbl[0]|=16;  
       }
         
         
        // const int iFilterBl= 0;//i && j;
//   void filterX(unsigned char *p, int stride, int idx, int bl_size);
         //iUseTap6=mb4->refId==-2;
         


         if(mb4->iIsBi)// && (m==0||iDec))// && iIsB && mb4->refId!=-2)
         {
            int _rid;
            if(iIsB){
               _rid=mb4->refId==-1?mb4->eNext:mb4->eM1;
               moveBi(p+ofs,//mb4->r1,mb4->r2,ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?1:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
            }
            else{
            
               _rid=mb4->refId==-1?mb4->eM2:mb4->eM1;
               moveBi(p+ofs,//mb4->r1,mb4->r2,ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?-2:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
            }

               if(iDrawVec&1){
                 //-- drawVec(i*iMBSize2,j*iMBSize2+1,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,800);
                 //-- drawVec(i*iMBSize2,j*iMBSize2-1,mb4->vrefs[_rid].x,mb4->vrefs[_rid].y,iMBSize2,w,h,800);
               }
               //if(i && j) filter_borders_16(p+ofs,  stride, 64);

            continue;

         }

         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         if(m==0)
         {
            
            //if(!mb4->mv2[0].iSad)mb4->r2=12;
            if(mb4->r1 || mb4->r2){
               moveMBRotJava(p,ofs,r,stride,iMBSize2,mb4->r1,mb4->r2,mb4->mv2[0].x,mb4->mv2[0].y);

             //--  if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,mb4->r1?(1000+mb4->r1):(2000+mb4->r2));
            }
            else 
            {

               copyV(p+ofs,r+ofs,stride,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2x3,iMBSize2);
               //--if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,mb4->refId);
            }
            //if(i && j) filter_borders_16(p+ofs,  stride, 64);
            
         }
         else {
            
            iMVMode=(m&~1);

            for(m2=0;m2<4;m2++)
            {
               ofs2x=ofs+xy2x2[m2];
               if((tabMode[m2] & iMVMode)==0){
                  copyV(p+ofs2x,r+ofs2x,stride,mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSizex3,iMBSize);
                  //--if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2],j*iMBSize2+dy2x2[m2],mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSize,w,h,mb4->refId);
               }
               else{
                  m4To=(m2+1)<<2;
                  
                  for(m4=m2<<2;m4<m4To;m4++){
                     ofs4x=ofs2x+xy4x4[m4&3];
                     copyV(p+ofs4x,r+ofs4x,stride,mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeDx3,iMBSizeD);
//if(iFilterBl)filterX(p+ofs,stride,30,iMBSizeD);
                     //--if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2]+dx4x4[m4&3],j*iMBSize2+dy2x2[m2]+dy4x4[m4&3],mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeD,w,h,mb4->refId);
                  }
                  //void filter_4x4_center(unsigned char *p, int stride, int idx);
                  //--filter_4x4_8_ab(p+ofs2x,stride,12<<iIsB);
                  //filter_4x4_8_ab(p+ofs2x+1,stride,18);
                 // filter_4x4_8_ab(p+ofs2x+2,stride,18);
               }
            }
            
         }            
         //--filter_8x8_16_ab(p+ofs,stride,16<<iIsB);
         //if(i && j) filter_borders_16(p+ofs,  stride, 64);
     //    filter_8x8_16_ab(p+ofs+1,stride,17);
       //  filter_8x8_16_ab(p+ofs+2,stride,17);
         
      }
   }
   
   int wm=w-xc*iMBSize2;
   int w1=0;
   if(wm &&(wm==16 || wm==8 || wm==4 || wm==24 || wm==12 || wm==48)){
      w1=1;
      mb4=mb4o+xc-1;
      ofs=xc*iMBSize2x3;
      int wm3=wm*3;
      for(i=0;i<h-iMBSize2+1;i+=iMBSize2,ofs+=stride*iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[5]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vx>0)vx=0;
         if(i+iMBSize2+(vy>>1)>h)vy=(h-(i+iMBSize2))>>1;
         copyV(p+ofs,r+ofs,stride,vx,vy,wm3,iMBSize2);
         mb4+=xc;
      }
   }
   wm=h-yc*iMBSize2;
   if(wm){// &&(wm==16 || wm==8 || wm==4 || wm==24)){
      mb4=mb4o+xc*(yc-1);
      ofs=yc*stride*iMBSize2;
      //int wm3=wm*3;
      for(i=0;i<w-iMBSize2+1;i+=iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(i+iMBSize2+(vx>>1)>w)vx=(w-(i+iMBSize2))>>1;

         copyV(p+ofs,r+ofs,stride,vx,vy,iMBSize2x3,wm);
         mb4++;
         ofs+=iMBSize2x3;
      }
      if(w1){
         mb4--;
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(vx>0)vy=0;
         copyV(p+ofs,r+ofs,stride,vx,vy,(w-xc*iMBSize2)*3,wm);
      }
   }

   return 0;
}
#endif

#if 0
int moveVectorsRefMB4(int iMBSize2, MB_4 *mb4, unsigned char *p, REFPIC *pic, int w, int h, int iIsB, int iDec){
//   int iIsB=iCur+1!=iGrSize;
   //iMBSize2=16;
   int xc=w/iMBSize2;
   int yc=h/iMBSize2;
   int i,j;
   
   int stride=w*3;
   const int iMBSize2x3=iMBSize2*3;
   const int iMBSizex3=iMBSize2x3>>1;
   const int iMBSizeDx3=iMBSize2x3>>2;
   const int iMBSize=iMBSize2>>1;
   const int iMBSizeD=iMBSize2>>2;
   
   unsigned char *r;
   int iMVMode;
   int m2,m4;
   int m4To;
    int ofs;
   int ofs2x;
   int ofs4x;
 
   const int xy2x2[]={0, iMBSizex3,  iMBSize * stride, iMBSizex3 + iMBSize * stride};
   const int xy4x4[]={0,iMBSizeDx3, iMBSizeD * stride, iMBSizeDx3 + iMBSizeD * stride};

   const int dx2x2[]={0,iMBSize,0,iMBSize};
   const int dy2x2[]={0,0,iMBSize,iMBSize};

   const int dx4x4[]={0,iMBSizeD,0,iMBSizeD};
   const int dy4x4[]={0,0,iMBSizeD,iMBSizeD};
   MB_4 *mb4o=mb4;
   iIsB=!!iIsB;
   
   for(j=0;j<yc;j++)
   {
      ofs=j*iMBSize2*stride;
      for(i=0;i<xc;i++,mb4++,ofs+=iMBSize2x3){
         
         const int iFilterBl= 0;//i && j;
//   void filterX(unsigned char *p, int stride, int idx, int bl_size);

         if(mb4->iIsBi && mb4->iMVMode==0)// && iIsB && mb4->refId!=-2)
         {
            int _rid;
            if(iIsB){
               _rid=mb4->refId==-1?mb4->eNext:mb4->eM1;
               moveBi(p+ofs,//mb4->r1,mb4->r2,ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?1:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
            }
            else{
            
               _rid=mb4->refId==-1?mb4->eM2:mb4->eM1;
               moveBi(p+ofs,//mb4->r1,mb4->r2,ofs,
                  pic[mb4->refId>0?1:mb4->refId].pPic + ofs,&mb4->mv2[0],
                  pic[mb4->refId==-1?-2:-1].pPic + ofs      ,&mb4->vrefs[_rid],
                  iMBSize2,stride);
            }
               if(iDrawVec&1){
                  drawVec(i*iMBSize2,j*iMBSize2+1,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,1000);
                  drawVec(i*iMBSize2,j*iMBSize2-1,mb4->vrefs[_rid].x,mb4->vrefs[_rid].y,iMBSize2,w,h,1000);
               }
            continue;

         }
         /*
         if(mb4->iMVMode==0 && iIsB){
            unsigned char p1[48*16];
            unsigned char p2[48*16];
            
            int vx,vy;
            r=pic[-1].pPic;
            if(mb4->refId==-1){
               if(mb4->r1 || mb4->r2)
                  moveMBRotJava(p,ofs,r,stride,iMBSize2,mb4->r1,mb4->r2,mb4->mv2[0].x,mb4->mv2[0].y);
               else
                  copyV(p+ofs,r+ofs,stride,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2x3,iMBSize2,iFilterBl);
            }
            else{
               vx=mb4->mv_eM1_gr.x*(iCur+1)/iGrSize;
               vy=mb4->mv_eM1_gr.y*(iCur+1)/iGrSize;
               copyV(p+ofs,r+ofs,stride,vx,vy,iMBSize2x3,iMBSize2);
            }

            for(int add=0;add<16;add++)memcpy(&p1[add*48],p+ofs+add*stride,48);

            r=pic[1].pPic;
            if(mb4->refId>0){
               if(mb4->r1 || mb4->r2)
                  moveMBRotJava(p,ofs,r,stride,iMBSize2,mb4->r1,mb4->r2,mb4->mv2[0].x,mb4->mv2[0].y);
               else
                  copyV(p+ofs,r+ofs,stride,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2x3,iMBSize2,iFilterBl);
            }
            else{
               vx=-mb4->mv_eM1_gr.x*(iGrSize-iCur)/iGrSize;
               vy=-mb4->mv_eM1_gr.y*(iGrSize-iCur)/iGrSize;
               copyV(p+ofs,r+ofs,stride,vx,vy,iMBSize2x3,iMBSize2);
            }

            for(int add=0;add<16;add++)memcpy(&p2[add*48],p+ofs+add*stride,48);

            for(int add=0;add<16;add++)memcpyI(p+ofs+add*stride,&p1[add*48],&p2[add*48],48);

            continue;
         }
         */
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         if(mb4->iMVMode==0)
         {
            
            //if(!mb4->mv2[0].iSad)mb4->r2=12;
            if(mb4->r1 || mb4->r2){
               moveMBRotJava(p,ofs,r,stride,iMBSize2,mb4->r1,mb4->r2,mb4->mv2[0].x,mb4->mv2[0].y);
//if(iFilterBl)filterX(p+ofs,stride,30,iMBSize2);

               //if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,1000+mgabs(mb4->r1|mb4->r2));
            }
            else 
            {
               //if(!iIsB && mb4->mv2[0].x==0 && mb4->mv2[0].y==0)continue;

               copyV(p+ofs,r+ofs,stride,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2x3,iMBSize2,iFilterBl);
             //  if(iDrawVec&1)drawVec(i*iMBSize2,j*iMBSize2,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2,w,h,mb4->refId);
            }
           //    copyV(p+ofs,r+ofs,stride,mb4->mv2[0].x,mb4->mv2[0].y,iMBSize2x3,iMBSize2);
            
         }
         else {
            iMVMode=(mb4->iMVMode&~1);

            for(m2=0;m2<4;m2++)
            {
               ofs2x=ofs+xy2x2[m2];
               if((tabMode[m2] & iMVMode)==0){
                  copyV(p+ofs2x,r+ofs2x,stride,mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSizex3,iMBSize,iFilterBl);
//if(iFilterBl)filterX(p+ofs,stride,30,iMBSize);
                  //if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2],j*iMBSize2+dy2x2[m2],mb4->mv2[m2].x,mb4->mv2[m2].y,iMBSize,w,h,mb4->refId);
               }
               else{
                  m4To=(m2+1)<<2;
                  
                  for(m4=m2<<2;m4<m4To;m4++){
                     ofs4x=ofs2x+xy4x4[m4&3];
                     copyV(p+ofs4x,r+ofs4x,stride,mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeDx3,iMBSizeD,iFilterBl);
//if(iFilterBl)filterX(p+ofs,stride,30,iMBSizeD);
                    // if(iDrawVec&1)drawVec(i*iMBSize2+dx2x2[m2]+dx4x4[m4&3],j*iMBSize2+dy2x2[m2]+dy4x4[m4&3],mb4->mv4[m4].x,mb4->mv4[m4].y,iMBSizeD,w,h,mb4->refId);
                  }
                  //void filter_4x4_center(unsigned char *p, int stride, int idx);
                  int _TODO_TEST_filter;
                  //if(iMBSizeD==4)filter_4x4_center(p+ofs2x,stride);
               }
            }
            
         }            
         
      }
   }
   
   int wm=w-xc*iMBSize2;
   int w1=0;
   if(wm &&(wm==16 || wm==8 || wm==4 || wm==24 || wm==12)){
      w1=1;
      mb4=mb4o+xc-1;
      ofs=xc*iMBSize2x3;
      int wm3=wm*3;
      for(i=0;i<h-iMBSize2+1;i+=iMBSize2,ofs+=stride*iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[5]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vx>0)vx=0;
         if(i+iMBSize2+(vy>>1)>h)vy=(h-(i+iMBSize2))>>1;
         copyV(p+ofs,r+ofs,stride,vx,vy,wm3,iMBSize2);
         mb4+=xc;
      }
   }
   wm=h-yc*iMBSize2;
   if(wm){// &&(wm==16 || wm==8 || wm==4 || wm==24)){
      mb4=mb4o+xc*(yc-1);
      ofs=yc*stride*iMBSize2;
      //int wm3=wm*3;
      for(i=0;i<w-iMBSize2+1;i+=iMBSize2){
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(i+iMBSize2+(vx>>1)>w)vx=(w-(i+iMBSize2))>>1;

         copyV(p+ofs,r+ofs,stride,vx,vy,iMBSize2x3,wm);
         mb4++;
         ofs+=iMBSize2x3;
      }
      if(w1){
         mb4--;
         r=pic[mb4->refId>0?1:mb4->refId].pPic;
         T_CUR_VECTOR *v=mb4->iMVMode?&mb4->mv4[15]:&mb4->mv2[0];
         int vx=v->x;
         int vy=v->y;
         if(vy>0)vy=0;
         if(vx>0)vy=0;
         copyV(p+ofs,r+ofs,stride,vx,vy,(w-xc*iMBSize2)*3,wm);
      }
   }
   return 0;
}
#endif

