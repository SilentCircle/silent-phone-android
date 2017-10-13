/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2017, Silent Circle, LLC.  All rights reserved.

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
#define T_C_STR 3

#define T_DST(x,y) p[x*T_C_STR+stride*y]
#define T_DEF_T4 unsigned int t0,t1,t2,t3;
#define T_DEF_T7 unsigned int t0,t1,t2,t3,t4,t5,t6,t7;
#define T_DEF_T8 unsigned int t0,t1,t2,t3,t4,t5,t6,t7;
#define T_DEF_L4 unsigned int l0,l1,l2,l3;
#define T_DEF_L8 unsigned int l0,l1,l2,l3,l4,l5,l6,l7;
enum {
   T_4FT=1,
   T_4FL=2,
   T_4FB=4,
   T_4FR=8,
   T_4FX=T_4FL|T_4FT,
   T_4FTEST=16
//   T_TBIT=0,
  // T_LBIT=0

};
#define T_LT1 if(f&T_4FT){t0=(p[-stride]);t1=(p[-stride+T_C_STR]);t2=(p[-stride+T_C_STR*2]);t3=(p[-stride+T_C_STR*3]);}\
                    else {t0=t1=t2=t3=(f&T_4FL?p[-T_C_STR]:128);} 

#define T_LT2 if((f&(T_4FT|T_4FR))==(T_4FT|T_4FR)){t4=(p[-stride+T_C_STR*4]);t5=(p[-stride+T_C_STR*5]);t6=(p[-stride+T_C_STR*6]);t7=(p[-stride+T_C_STR*7]);}\
                    else t4=t5=t6=t7=t3;

#define T_LL1 if(f&T_4FL){l0=(p[-T_C_STR]);l1=(p[-T_C_STR+stride]);l2=(p[-T_C_STR+stride*2]);l3=(p[-T_C_STR+stride*3]);}\
                    else {l0=l1=l2=l3=(f&T_4FT?p[-stride]:128);} 

#define T_LL2 if((f&(T_4FL|T_4FB))==(T_4FL|T_4FB)){l4=(p[-T_C_STR+stride*4]);l5=(p[-T_C_STR+stride*5]);l6=(p[-T_C_STR+stride*6]);l7=(p[-T_C_STR+stride*7]);}\
                        else l4=l5=l6=l7=l3;

static int predDCX(unsigned char *p, int stride, unsigned int dc){
   int j;
   for(j=0;j<4;j++){
      p[0]=p[T_C_STR*1]=p[T_C_STR*2]=p[T_C_STR*3]=dc;
      
      p+=stride;
   }
   return 1;
}
static int predDC(unsigned char *p, int stride, int f){
   if(!(f&T_4FT))return 0;
   if(!(f&T_4FL))return 0;
   if(f&T_4FTEST)return 1;
   int i;
   unsigned char *o=p;
   unsigned int dc=0;
   p-=stride;
   for(i=0;i<4*T_C_STR;i+=T_C_STR)dc+=p[i];
      
   p-=T_C_STR;
   for(i=0;i<4;i++){
      p+=stride;
      dc+=p[0];
   }
   dc=(dc+4)>>3;
   predDCX(o,stride,dc);
   return 1;
   
}

/* EA: unused function warnings
static int predDCT(unsigned char *p, int stride, int f){
  // return 0;
   if(!(f&T_4FT))return 0;
   if(f&T_4FTEST)return 1;
   unsigned int i,dc=0;
   
   for(i=0;i<4*T_C_STR;i+=T_C_STR)dc+=p[i-stride];
   dc=(dc+2)>>2;
   predDCX(p,stride,dc);
   return 1;
} 
static int predDCL(unsigned char *p, int stride, int f){
   unsigned int i,dc=0;
  // return 0;
   if(!(f&T_4FL))return 0;
   if(f&T_4FTEST)return 1;
   
   for(i=0;i<4;i++){
      dc+=p[-T_C_STR];
      p+=stride;
   }
   p-=stride*4;
   dc=(dc+2)>>2;
   predDCX(p,stride,dc);
   return 1;
}      
*/

static int predTR(unsigned char *p, int stride, int f){
   if(!(f&T_4FT) || !(f&T_4FR))return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_T8
   T_LT1
   T_LT2
   t4=(t4*5+p[-stride*2+5*3]+4+t3+t5)>>3;
   t3=(t3*3+p[-stride*2+4*3]+2)>>2;
   t5=(t5*3+p[-stride*2+6*3]+2)>>2;

   T_DST(0,0)=t1;T_DST(1,0)=t2;T_DST(2,0)=t3;T_DST(3,0)=t4;
   T_DST(0,1)=t2;T_DST(1,1)=t3;T_DST(2,1)=t4;T_DST(3,1)=t5;
   T_DST(0,2)=t3;T_DST(1,2)=t4;T_DST(2,2)=t5;T_DST(3,2)=t6;
   T_DST(0,3)=t4;T_DST(1,3)=t5;T_DST(2,3)=t6;T_DST(3,3)=t7;
   return 1;
} 
static int predTTR(unsigned char *p, int stride, int f){
   if(!(f&T_4FT) || !(f&T_4FR) )return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_T7
   T_LT1
   T_LT2

#define T(_x,_y) ((t##_x+t##_y+1)>>1);

   int t5o=t5;
   t5=(t4+t5*2+t6+2)>>2;
   t4=(t4*2+t5o+t3+2)>>2;

   int t01=T(0,1);
   int t12=T(1,2);
   int t23=T(2,3);
   int t34=T(3,4);
   int t45=T(5,4);
   //int t34=T(3,4);
   //0 1 2 3
   //1223
   //   0  1  2  3
   //a  01 12 23
   //b 
   //c 
   //d b
   
   T_DST(0,0)=t01;T_DST(1,0)=t12;T_DST(2,0)=t23;T_DST(3,0)=t34;
   T_DST(0,1)=t1;T_DST(1,1)=t2;T_DST(2,1)=t3;T_DST(3,1)=t4;
   T_DST(0,2)=t12;T_DST(1,2)=t23;T_DST(2,2)=t34;T_DST(3,2)=t45;
   T_DST(0,3)=t2;T_DST(1,3)=t3;T_DST(2,3)=t4;T_DST(3,3)=t5;
   return 1;
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-variable" // ignore warning

static int predTTL(unsigned char *p, int stride, int f){
   if(!(f&T_4FT) || !(f&T_4FL) )return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_T7
   T_DEF_L4
   T_LT1
   T_LT2
   T_LL1


   #define L(_x,_y) ((l##_x+l##_y+1)>>1);

   int t01=T(0,1);
   int t12=T(1,2);
   int t23=T(2,3);

   int tl=p[-stride-T_C_STR];
   //int t45=T(5,4);
   int x1=(t0*3+l0+tl*4+4)>>3;
   int x0=(tl*6+t0+l0+4)>>3;
   x1=(x1*2+x0+t0+2)>>2;
   int l01=L(0,1);

   //int t34=T(3,4);
   //   0  1  2  3
   //a x0 01 12 23
   //b 
   //c 
   //d b
   T_DST(0,0)=x1;T_DST(1,0)=t01;T_DST(2,0)=t12;T_DST(3,0)=t23;
   T_DST(0,1)=x0;T_DST(1,1)=t0;T_DST(2,1)=t1;T_DST(3,1)=t2;
   T_DST(0,2)=l0;T_DST(1,2)=x1; T_DST(2,2)=t01;T_DST(3,2)=t12;
   T_DST(0,3)=l1;T_DST(1,3)=x0;T_DST(2,3)=t0;T_DST(3,3)=t1;   
   return 1;
}      

#pragma clang diagnostic pop

static int predTLL(unsigned char *p, int stride, int f){
   if(!(f&T_4FT) || !(f&T_4FL) )return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_T4
   T_DEF_L4
   T_LT1
   T_LL1



  // int t01=T(0,1);
   //int t12=T(1,2);
   //int t23=T(2,3);


   int l01=L(0,1);
   int l12=L(1,2);
   int l23=L(2,3);
   int tl=p[-stride-T_C_STR];
   int x0=(t0+6*tl+l0+4)>>3;
   int x1=(l0*4+tl*3+t0+4)>>3;
   x1=(x0+l0+x1*2+2)>>2;

   //int t34=T(3,4);
   //x  0  1  2  3
   //a xx x  0 01
   //b ab a  xx x
   //c 
   //d b
   T_DST(0,0)=x1; T_DST(1,0)=x0;T_DST(2,0)=t0; T_DST(3,0)=t1;
   T_DST(0,1)=l01;T_DST(1,1)=l0;T_DST(2,1)=x1; T_DST(3,1)=x0;
   T_DST(0,2)=l12;T_DST(1,2)=l1;T_DST(2,2)=l01;T_DST(3,2)=l0;
   T_DST(0,3)=l23;T_DST(1,3)=l2;T_DST(2,3)=l12;T_DST(3,3)=l1;   
   return 1;
}
static int predT(unsigned char *p, int stride, int f){
   if(!(f&T_4FT) )return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_T4;
   T_LT1;
//--   if(t0==t1 && t0==t2 && t0==t3 )return 0;
   int i;
   for(i=0;i<4;i++){
      p[0]=t0;p[T_C_STR]=t1;p[T_C_STR*2]=t2;p[T_C_STR*3]=t3;
      p+=stride;
   }
   return 1;
}      
static int predTL(unsigned char *p, int stride, int f){
   if(!(f&T_4FT) || !(f&T_4FL) )return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_T4
   T_DEF_L4
   T_LT1
   T_LL1
   int tl=p[-stride-T_C_STR];
//   int x0=(t0+l0+(int)tl+2)>>2;
   //int x0=(t0+l0+1)>>1;
   int x0=((t0+l0)*2+tl*3+4+p[-stride*2-T_C_STR*2])>>3;

   
   
   T_DST(0,0)=x0;T_DST(1,0)=t0;T_DST(2,0)=t1;T_DST(3,0)=t2;
   T_DST(0,1)=l0;T_DST(1,1)=x0;T_DST(2,1)=t0;T_DST(3,1)=t1;
   T_DST(0,2)=l1;T_DST(1,2)=l0;T_DST(2,2)=x0;T_DST(3,2)=t0;
   T_DST(0,3)=l2;T_DST(1,3)=l1;T_DST(2,3)=l0;T_DST(3,3)=x0;   
   /*
   T_DST(0,0)=x0;T_DST(1,0)=t1;T_DST(2,0)=t2;T_DST(3,0)=t3;
   T_DST(0,1)=l1;T_DST(1,1)=x0;T_DST(2,1)=t1;T_DST(3,1)=t2;
   T_DST(0,2)=l2;T_DST(1,2)=l1;T_DST(2,2)=x0;T_DST(3,2)=t1;
   T_DST(0,3)=l3;T_DST(1,3)=l2;T_DST(2,3)=l1;T_DST(3,3)=x0;   
*/  
  return 1;
}

static int predL(unsigned char *p, int stride, int f){
   if(!(f&T_4FL) )return 0;
   if(f&T_4FTEST)return 1;
  // T_DEF_L;
  // T_LL1;
  // if(l0==l1 && l0==l2 && l0==l3 )return 0;
   p[0]=p[T_C_STR]=p[T_C_STR*2]=p[T_C_STR*3]=p[-T_C_STR];
   p+=stride;
   p[0]=p[T_C_STR]=p[T_C_STR*2]=p[T_C_STR*3]=p[-T_C_STR];
   p+=stride;
   p[0]=p[T_C_STR]=p[T_C_STR*2]=p[T_C_STR*3]=p[-T_C_STR];
   p+=stride;
   p[0]=p[T_C_STR]=p[T_C_STR*2]=p[T_C_STR*3]=p[-T_C_STR];
   return 1;
}      
static int predBL(unsigned char *p, int stride, int f){
   if(!(f&T_4FL))return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_L8;
   T_LL1;
   T_LL2;
   int l4o=l4;
   l4=(((l2+l6)>>1)+((l3+l5))+l4*5+4)>>3;
   l5=(((l3+l7)>>1)+((l4o+l6))+l5*5+4)>>3;
   T_DST(0,0)=l1;T_DST(1,0)=l2;T_DST(2,0)=l3;T_DST(3,0)=l4;
   T_DST(0,1)=l2;T_DST(1,1)=l3;T_DST(2,1)=l4;T_DST(3,1)=l5;
   T_DST(0,2)=l3;T_DST(1,2)=l4;T_DST(2,2)=l5;T_DST(3,2)=l6;
   T_DST(0,3)=l4;T_DST(1,3)=l5;T_DST(2,3)=l6;T_DST(3,3)=l7;      
   return 1;
}
static int predBLL(unsigned char *p, int stride, int f){
   
   if(!(f&T_4FL) )return 0;
   if(f&T_4FTEST)return 1;
   T_DEF_L8;
   T_LL1;
   T_LL2;
   //   0  1  2  3
   //a x0 01 12 23
   //b 
   //c 
   //d b
   int l01=L(0,1);
   int l12=L(2,1);
   int l23=L(2,3);
   int l34=L(4,3);
   int l45=L(4,5);
   
   T_DST(0,0)=l01;T_DST(1,0)=l1;T_DST(2,0)=l12;T_DST(3,0)=l2;
   T_DST(0,1)=l12;T_DST(1,1)=l2;T_DST(2,1)=l23;T_DST(3,1)=l3;
   T_DST(0,2)=l23;T_DST(1,2)=l3;T_DST(2,2)=l34;T_DST(3,2)=l4;
   T_DST(0,3)=l34;T_DST(1,3)=l4;T_DST(2,3)=l45;T_DST(3,3)=l5;      
   return 1;
}
static int predDC128(unsigned char *p, int stride, int f){
   if(f&T_4FTEST)return 1;
   return predDCX(p,stride,128);
}


typedef int (fintPRED )(unsigned char *src, int stride, int f);
#define _T_DC_128 10
fintPRED *fncPred4x4[_T_DC_128+1];
static void loadPred4x4(){
/*
   fncPred4x4[0]=&predDC;
   fncPred4x4[1]=&predT;
   fncPred4x4[2]=&predTR;
   fncPred4x4[3]=&predDCT;
   fncPred4x4[4]=&predTL;
   fncPred4x4[5]=&predDCL;
   fncPred4x4[6]=&predBL;
   fncPred4x4[7]=&predL;
#define _T_DC_128 4
return;
*/
   
   fncPred4x4[0]=&predDC;
   //fncPred4x4[1]=&predDCL;
   //fncPred4x4[2]=&predDCT;
#if  _T_DC_128==10
   fncPred4x4[1]=&predT;
   fncPred4x4[2]=&predL;
   fncPred4x4[3]=&predTR;
   fncPred4x4[4]=&predTTR;
   fncPred4x4[5]=&predTTL;
   fncPred4x4[6]=&predTL;
   fncPred4x4[7]=&predTLL;
   fncPred4x4[8]=&predBLL;
   fncPred4x4[9]=&predBL;
   fncPred4x4[10]=&predDC128;
#else
   fncPred4x4[1]=&predTR;
   fncPred4x4[2]=&predTL;
   fncPred4x4[3]=&predDC128;
#endif
/*
   fncPred4x4[0]=&predDC;
   fncPred4x4[1]=&predTR;
   fncPred4x4[2]=&predT;
   fncPred4x4[3]=&predTL;
   fncPred4x4[4]=&predL;
   fncPred4x4[5]=&predDC128;
   */
   return;
}

static int get4x4PicPred(int f, int i, int *p ){
   int a=-1;
   int b=-1;
   int iPred;
   if((i&3))a=p[i-1];else if(i>3)a=p[i-3];
   if((i>3))b=p[i-4];else if(i)b=0;

   //if((a==1 && b==2) || (a==2 && b==1))iPred=0;else
   if(a!=-1 && b!=-1)iPred=a==b?a:0;
   else if(a!=-1)iPred=a;
   else if(b!=-1)iPred=b;
   else iPred=0;
   //const int to=f?8:9;
   if(iPred>_T_DC_128)iPred=0;

   int iAddCnt=0;
   while(1){

      if(iPred-iAddCnt>=0 && fncPred4x4[iPred-iAddCnt](NULL,0,f|T_4FTEST))return iPred-iAddCnt;
      if(iAddCnt && iPred+iAddCnt<=_T_DC_128 && fncPred4x4[iPred+iAddCnt](NULL,0,f|T_4FTEST))return iPred+iAddCnt;
      iAddCnt++;

   }
   return iPred;
}
/*
0
11
1010
1011
100100
100101
100110
100111
   
0001001
   
   */
