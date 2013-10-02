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

#define TOP_PX 1
#define LEFT_PX 2
#define R_PX 4
#define B_PX 8
#define TOP_R_PX 16
#if 0
#define T_XYL 8
#define T_XLC 24
#else
#define T_XYL 4
#define T_XLC 12
#endif
#define T_LIM (T_XYL*2-1)
static void predV(unsigned char *cur,int stride, int f, int *fl){
   int i,j;
   //unsigned char *s=&cur[-stride];
   unsigned char sx[(T_XYL*3)*3+4];
   
   unsigned char *s=&sx[T_XYL*3];
   if(f&LEFT_PX){
      memcpy(&s[-T_XLC],&cur[-stride-T_XLC],T_XLC*2);
   }
   else{
      memcpy(&s[0],&cur[-stride],T_XLC);
      for(i=-T_XLC;i<0;i+=3)s[i]=s[0];
   }
   if(f&R_PX){
      memcpy(&s[T_XLC],&cur[-stride+T_XLC],T_XLC);
   }
   else{
      for(i=T_XLC;i<T_XLC*2+3;i+=3)s[i]=s[T_XLC-3];
   }
   
   
   int str=0;

   for(i=0;i<T_XYL;i++){
      str+=fl[i];
      if(str<-T_LIM)str=-T_LIM;else if(str>T_LIM)str=T_LIM;
    //??  if(str<0 && !(f&LEFT_PX))str=0;
      const int o=(str>>1)*3;

      if(i+1<T_XYL && str>0 && f&LEFT_PX ){int L=min(o,3*(T_XYL-i)); for(j=-L;j<0;j+=3){s[j]=(cur[-3-(j/3)*stride]+cur[-j]+1)>>1;}}//(cur[j]+cur[j-stride]+1)>>1;} }

      const int oo=str&1;
      if(oo){
         for(j=0;j<T_XLC;j+=3){cur[j]=(s[o+j]+s[o+j+3]+1)>>1;}
      }
      else 
      {for(j=0;j<T_XLC;j+=3){cur[j]=s[o+j];}}
      cur+=stride;
      //s+=stride;
   }
}

#define fnc_abs(_A) abs(_A)
//#define fnc_abs(_A) ((_A)*(_A))
//static inline int fnc_abs(int a){return (a*a)>>2;}


static int sadPredV2(unsigned char *ss,unsigned char *cur, unsigned char *ref,int stride,int f, int *res, int old){
   int iSad=0;
   int i,j;
//   unsigned char *s=&ss[-stride];
   unsigned char sx[(T_XYL*3)*3+4];
   
   unsigned char *s=&sx[T_XYL*3];
   if(f&LEFT_PX){
      memcpy(&s[-T_XLC],&cur[-stride-T_XLC],T_XLC*2);
   }
   else{
      memcpy(&s[0],&cur[-stride],T_XLC);
      for(i=-T_XLC;i<0;i+=3)s[i]=s[0];
   }
   if(f&R_PX){
      memcpy(&s[T_XLC],&cur[-stride+T_XLC],T_XLC);
   }
   else{
      for(i=T_XLC;i<T_XLC*2+3;i+=3)s[i]=s[T_XLC-3];
   }
   int str=0;
   int iRowSad;
   
   
   int x;
   for(i=0;i<T_XYL;i++){
      int oldStr=str;
      int iRowBest=256*256*32;
      int iFlag=0;
      x=0;
      int bx=0;
      int checked[]={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
      //for(x=-7;x<8;x++)
#define _CHK_L(xV) \
         {str+=xV;\
         if(str<-T_LIM)str=-T_LIM;else if(str>T_LIM)str=T_LIM;\
         iRowSad=0;\
         const int o=(str>>1)*3;\
         const int oo=str&1;\
         if(oo){\
            for(j=0;j<T_XLC;j+=3){iRowSad+=fnc_abs(((s[o+j]+s[o+j+3]+1)>>1)-ref[j]);}\
         }\
         else{\
               for(j=0;j<T_XLC;j+=3)iRowSad+=fnc_abs(s[o+j]-ref[j]);\
         }\
         iRowSad+=xV*xV;\
         if(iRowSad<iRowBest){\
            b2=bx;bx=xV;\
            iRowBest=iRowSad;\
         }\
         str=oldStr;}

         int iNextF=3;

         int b2=x;
         _CHK_L(0)
         for(x=-T_LIM;x<T_LIM;x+=3){
            if(x && !checked[x+T_LIM+1]){checked[x+T_LIM+1]=1;_CHK_L(x)}
         }
         
         int x1=bx-2;
         int x2=bx+2;
         int b2o=b2;

         for(x=x1;x<x2;x++){
            if(x && !checked[x+T_LIM+1]){checked[x+T_LIM+1]=1;_CHK_L(x)}
         }

         x1=b2o-3;
         x2=b2o+3;
         for(x=x1;x<x2;x++){
            if(x && !checked[x+T_LIM+1]){checked[x+T_LIM+1]=1;_CHK_L(x)}
         }
         res[i]=bx;

#undef _CHK_L
         
         
      
      iSad+=iRowBest;
      str=oldStr+res[i];
      cur+=stride;
      ref+=stride;
      //s+=stride;
   }   
   return iSad;
}

static void predH(unsigned char *cur,int stride, int f, int *fl){
   int i,j;
   //unsigned char *s=&cur[-3];
   unsigned char sx[(T_XYL*2)+12];
   
   unsigned char *s=&sx[T_XYL];
      
   if(f&TOP_PX){
      for(i=-T_XYL;i<T_XYL;i++)s[i]=cur[stride*i-3];
   }
   else{
      for(i=-T_XYL;i<0;i++)s[i]=cur[-3];
      for(i=0;i<T_XYL;i++)s[i]=cur[stride*i-3];
   }
   if(f&B_PX){
      for(i=T_XYL;i<2*T_XYL+1;i++)s[i]=cur[stride*i-3];
   }
   else{
     for(i=T_XYL;i<2*T_XYL+1;i++)s[i]=s[T_XYL-1];
   }

   

   int str=0;
   for(i=0;i<T_XYL;i++){
      str+=fl[i];
      //if(str<0 && !(f&TOP_PX))str=0;
      if(str<-T_LIM)str=-T_LIM;else if(str>T_LIM)str=T_LIM;
      const int o=(str>>1);
      if(i && str>0 && f&TOP_PX ){ for(j=-o;j<0;j+=3){s[j]=cur[-j-stride];}}\
      if(str&1){
         for(j=0;j<T_XYL;j++)cur[j*stride]=(s[o+j]+s[o+(j+1)]+1)>>1;
      }
      else {
         for(j=0;j<T_XYL;j++)cur[j*stride]=s[o+j];
         
      }
      cur+=3;
      //s+=3;
   }
}
static int sadPredH2(unsigned char *ss, unsigned char *cur, unsigned char *ref,int stride, int f, int *res, int old){
   int iSad=0;
   int i,j;
   //unsigned char *s=&ss[-3];
   unsigned char sx[(T_XYL*2)+12];
   
   unsigned char *s=&sx[T_XYL];
      
   if(f&TOP_PX){
      for(i=-T_XYL;i<T_XYL;i++)s[i]=cur[stride*i-3];
   }
   else{
      for(i=-T_XYL;i<0;i++)s[i]=cur[-3];
      for(i=0;i<T_XYL;i++)s[i]=cur[stride*i-3];
   }
   if(f&B_PX){
      for(i=T_XYL;i<T_XYL+1;i++)s[i]=cur[stride*i-3];
   }
   else{
     for(i=T_XYL;i<T_XYL+1;i++)s[i]=s[T_XYL-1];
   }

   
   int str=0;
   int iRowSad;
   
   
   int x;
   for(i=0;i<T_XYL;i++){
      int oldStr=str;
      int iRowBest=256*256*32;
      for(x=-T_LIM;x<T_LIM;x++){
         str+=x;
         if(str<-T_LIM)str=-T_LIM;else if(str>T_LIM)str=T_LIM;
         iRowSad=0;
         const int o=(str>>1);
         if(str&1){
            //for(j=0;j<T_XYL;j++)cur[j*stride]=(s[o+j*stride]+s[o+(j+1)*stride]+1)>>1;
            for(j=0;j<T_XYL;j++){iRowSad+=fnc_abs(((s[o+j]+s[o+(j+1)]+1)>>1)-ref[j*stride]);}
         }
         else {
            //for(j=0;j<T_XYL;j++)cur[j*stride]=s[o+j*stride];}
         
            for(j=0;j<T_XYL;j++){iRowSad+=fnc_abs(s[o+j]-ref[j*stride]);}
         }
         
         iRowSad+=x*x;

         if(iRowSad<iRowBest){
            res[i]=x;
            iRowBest=iRowSad;
         }
         str=oldStr;
         
      }
      iSad+=iRowBest;
      str=oldStr+res[i];
      cur+=3;
      ref+=3;
      //s+=3;
   }   
   return iSad;
}
static int sadPredH(unsigned char *cur, unsigned char *ref,int stride, int f, int *res, int old){
   return sadPredH2(cur,cur,ref,stride,f,res,old); 
}
static int sadPredV(unsigned char *cur, unsigned char *ref,int stride, int f, int *res, int old){
   return sadPredV2(cur,cur,ref,stride,f,res,old); 
}

static int predHorV(unsigned char *cur, unsigned char *ref,int stride, int f){
   int res[T_XYL];
   int i;
   int iBestSad=256*256*4;
   int iHSad=0;
   int iVSad=0;
   if(f&TOP_PX){
   for(i=0;i<T_XYL;i++){
      const int o=stride*i*T_XYL;
      iHSad+=sadPredH2(ref+o,cur+o,ref+o,stride,f,&res[0],iBestSad); 
   }
   }
   else iHSad=iBestSad;

   if(f&LEFT_PX && iHSad>2){
      for(i=0;i<T_XLC;i+=T_XYL*3){
         iVSad+=sadPredH2(ref+i,cur+i,ref+i,stride,f,&res[0],iHSad); 
      }
   }
   else iVSad=100000;

   return iHSad<=iVSad?TOP_PX:LEFT_PX;
      
   

}