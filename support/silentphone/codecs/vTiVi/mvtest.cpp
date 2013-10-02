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

/*
100*100=1000

V(20x20(+-20))==1600*400= sads fs
V(20x20(+-20))==4*20*400= sads ds

V(20x20(+-20))->H(5x5(+-5))==100*25 fs
V(20x20(+-20))->H(5x5(+-5))==4*5*25 ds


//20*20=400
#include <string.h>

CTHdc hdcOut(w,h,24)
memcpy(hdcOut.pWinData,p,iLen);
BitBlt(GetDC(0),0,0,w,h,hdcOut.hdc,0,0,SRCCOPY);
*/

#include <stdio.h>
//#include <string.h>
#include <string.h>
#include <math.h>
#include "../ctimgdata.h"
//#include "..\codecs\CTNoiseRem.h"
#include "vec_calc.h"

#define T_SAD_MAX 0x1fffffff
#define T_SAD_MIN (8*getTabSad(12))
void debugss(char *c, int a=-123456789, int b=-123456789);
inline int t_mgabs(int i){return i<0?-i:i;}
int getMedian(int *a, int iCnt);

#define vector T_CUR_VECTOR

typedef struct _T_MB{
   vector cur;
   //vector mv4[4][4];//iespejams shos mekleet pa apli ja zin maleejos 
   
   vector prev;
   vector prevd2;
   vector prevdm2;
   vector prevdmm2;
   vector gForce;
   vector gPrevRefined;
   int iMarkedSad;

   struct _T_MB *mbRecurs;
   
   MOTI_TYPE *pCur;
   MOTI_TYPE *pOld;
   MOTI_TYPE *pRef[5];
   
   unsigned int checkBits[40];//32x32== +-16
   
   int x,y;//mb pos
   int posx,posy;
   
   int iSadFrom;
   int iMbSize;
   int iFncGetSadCalls;
   int iBestMatchUpdates;
   int iNearsChecked;
   int iPrevsChecked;
   int iAvgsChecked;
   int iRefined;
   int iZMatchOnly;
   int iRecursiveChecked;
}T_MB;

typedef struct  {
   int w,h;
   int stride;
   int xc,yc;

   //MOTI_TYPE *ref[3]
   MOTI_TYPE *pCur;
   MOTI_TYPE *pOld;
   MOTI_TYPE *pRef[5];

   vector vRecFirst;
   
   //int xx[100];
   T_MB *mby;
   T_MB *mb;
   T_MB *mbx;
   int iMBlocks;
   int iMVSize;
   int iCurRecurs;
   void setPrevs(int *v1, int *v2, int *pSad, int rem)
   {
      int i,j;

      T_MB *m=mb;
      for(j=0;j<yc;j++)
      for(i=0;i<xc;i++)
      {
          m->prev.x=*v1;
          m->prev.y=*v2;
          m->prev.iSad=*pSad?*pSad:0;
          if(rem)
          {
             m->prevd2.x=*(v1-rem);
             m->prevd2.y=*(v2-rem);
             m->prevd2.iSad=*(pSad-rem)?*(pSad+rem):0;;m->prevd2.iSad>>=3;
             m->prevdm2.x=(*(v1+rem)+m->prevd2.x)>>1;
             m->prevdm2.y=(*(v2+rem)+m->prevd2.y)>>1;
             m->prevdm2.iSad=*(pSad+rem)?*(pSad+rem):0;;m->prevdm2.iSad>>=3;

             m->prevdmm2.x=(*(v1+rem))>>1;
             m->prevdmm2.y=(*(v2+rem))>>1;
             m->prevdmm2.iSad=*(pSad+rem)?*(pSad+rem):0;m->prevdmm2.iSad>>=3;
          }
          
          pSad++;v1++;v2++;
          m++;
      }
   }
   void init(int wi, int hi, int iMVSizeIn, unsigned char *cur,  unsigned char *ref)
   {
      if(wi!=w || hi!=h || iMVSizeIn!=iMVSize){
         if(mb && w)delete mb;
         w=wi;
         h=hi;
         stride=w;
         iMVSize=iMVSizeIn;

         iMBlocks=(w+iMVSize-1)/iMVSize*(h+iMVSize-1)/iMVSize;
         mbx=mby=mb=new T_MB[iMBlocks+3];
         memset(&mb[0],0,sizeof(T_MB)*iMBlocks);

      }
      
      memset(&mb[iMBlocks],200,sizeof(T_MB)*2);
      iCurRecurs=1;
      //pCur.check(w,h);
      //pCur.check(w,h);
      
      
      int i,j;
      xc=w/iMVSize;yc=h/iMVSize;
      int ofs;
      T_MB *m=&mb[0];
      for(j=0;j<yc;j++)
      for(i=0;i<xc;i++)
      {
          ofs=(j*w+i)*iMVSize;
          memset(&m->checkBits[0],0,sizeof(m->checkBits));
          m->iRefined=0;m->iAvgsChecked=0;m->iNearsChecked=0;m->iZMatchOnly=0;m->iSadFrom=0;
          m->prevd2=m->cur;
          m->cur.iSad=m->cur.x=m->cur.y=0;
          m->gForce.y=m->gForce.x=0;
          m->pCur=pCur+ofs;
          m->pOld=pOld+ofs;
          if(pRef[0]){
             m->pRef[0]=pRef[0]+ofs;
             m->pRef[1]=pRef[1]+ofs;
             m->pRef[2]=pRef[2]+ofs;
             m->pRef[3]=cur+ofs*3;
             m->pRef[4]=ref+ofs*3;
          }
          m->iMbSize=iMVSize;
          m->x=i;
          m->y=j;
          m->posx=i*iMVSize;
          m->posy=j*iMVSize;
          m++;
      }

   }
   void rel(){
      if(memcmp(&mb[iMBlocks],&mb[iMBlocks+1],sizeof(T_MB)))debugss("cmpe");
      if(mbx!=mb)debugss("cmpe2");
      delete mb;
      w=h=0;   
   }


}CTX;
//typedef int (getSadFnc )(MOTI_TYPE *pCur, MOTI_TYPE *pOld, int x, int y, int stride, int iOldSad);

//int getSad(MOTI_TYPE *pCur, MOTI_TYPE *pOld, int x, int y, int stride, int iOldSad);
int getTabSad(int id);
int iSadMaxRec=16*16*500;


void createRefh(VEC_IMG_STRUCT *v,MOTI_TYPE *ref[3], MOTI_TYPE *prev, int w, int h);
int getSadCon(unsigned int *mask,MOTI_TYPE *c, MOTI_TYPE *o, MOTI_TYPE *r[], int iSize, int x, int y, int stride, int iBest,int gx ,int gy);
//void diamondSCon(int iSpeed, int iPosX, int iPosY, MOTI_TYPE *c, MOTI_TYPE *o, MOTI_TYPE *r[], int iSize, int &x, int &y, int stride, int w, int h,int iBest);
void diamondSCon(unsigned int *mask,int iSpeed, int iPosX, int iPosY, MOTI_TYPE *c, MOTI_TYPE *o, MOTI_TYPE *r[], int iSize, int &x, int &y, int stride, int w, int h,int &iBestSad,int gx ,int gy);
int diamondSearch(CTX *ctx, T_MB *mb, int x, int y, int iBestSad, int iFastest=0, vector *gf=NULL)
{
   if(iFastest==2)iFastest=0;
   else if(mb->cur.iSad==0){iFastest=-1;}
   int v1=x,v2=y;
   //v1=x;
   //mb->cur.x=x, mb->cur.y=y;mb->cur.iSad=iBestSad;
  
   if(mb->cur.iSad && mb->cur.iSad<T_SAD_MIN)iFastest=2;

   if(iFastest>-1 && mb->cur.iSad && mb->gPrevRefined.iSad && mb->gPrevRefined.x==x && mb->gPrevRefined.y==y)return mb->cur.iSad;
   
   if(gf){
      mb->gForce=*gf;
   }
   else if(mb->x|mb->y){
      mb->gForce.x=0;mb->gForce.y=0;mb->gForce.iSad=0;
      if(mb->x){
         mb->gForce=mb[-1].cur;
      }
      if(mb->y){
         if(mb->x)mb->gForce+=mb[-ctx->xc].cur;else mb->gForce=mb[-ctx->xc].cur;
      }
   }
   else {
      mb->gForce.x=0;mb->gForce.y=0;
      mb->gForce.iSad=0;
   }
   diamondSCon(&mb->checkBits[20],iFastest, mb->posx, mb->posy, mb->pCur, mb->pOld, mb->pRef, mb->iMbSize, v1, v2, ctx->stride, ctx->w, ctx->h,iBestSad,mb->gForce.x,mb->gForce.y);
   if(mb->cur.iSad==0 || mb->cur.iSad>iBestSad){
      mb->cur.x=v1; mb->cur.y=v2;mb->cur.iSad=iBestSad;
   }
   if(iFastest>-1){
      mb->gPrevRefined=mb->cur;
   }
   return iFastest;

//int getSadCon(MOTI_TYPE *c, MOTI_TYPE *o, MOTI_TYPE *r[], int iSize, int x, int y, int stride, int iBest)
}
inline int findZMach(CTX *ctx, T_MB *mb, vector *vPred, vector *gf=NULL)
{
   //MOTI_TYPE *ref=getRef(mb,vPred->x,vPred->y);
   

   int x=0;
   int y=0;
   if(vPred)
   {
      
      x=vPred->x;
      y=vPred->y;
      if(mb->posx+x/2<2 || mb->posy+y/2<0)
         return T_SAD_MAX;
      if((mb->posx+mb->iMbSize)+x/2>=ctx->w+2 || 
         (mb->posy+mb->iMbSize)+y/2>=ctx->h)
         return T_SAD_MAX;
   }
   if(mb->cur.iSad && mb->cur.x==x && mb->cur.y==y)return mb->cur.iSad;
      
   int iBestSad=mb->cur.iSad?mb->cur.iSad:T_SAD_MAX;

   if(gf){
      mb->gForce=*gf;
      mb->gForce.iSad=0;

   }
   else if(mb->x|mb->y){
      mb->gForce.x=0;mb->gForce.y=0;mb->gForce.iSad=0;
      if(mb->x){
         mb->gForce=mb[-1].cur;
      }
      if(mb->y){
         if(mb->x)mb->gForce+=mb[-ctx->xc].cur;else mb->gForce=mb[-ctx->xc].cur;
      }
   }
   else {
      mb->gForce.x=0;mb->gForce.y=0;
      mb->gForce.iSad=0;
   }
   //&mb->checkBits[0]
   int iSad=getSadCon(&mb->checkBits[20],mb->pCur,mb->pOld,mb->pRef,mb->iMbSize,x,y,ctx->stride,iBestSad,mb->gForce.x,mb->gForce.y);
   if(iSad<mb->cur.iSad || mb->cur.iSad==0)
   {
      mb->cur.x=x;
      mb->cur.y=y;
      mb->cur.iSad=iSad;
   }
   return mb->cur.iSad;
}
void setNewGForce(CTX *ctx,T_MB *mb,vector *gF)
{
//   if()
   int iCheck=mb->gForce.iSad==0 || mb->gForce!=*gF;
   if(!mb->gForce.iSad)mb->gForce=*gF;
   else if(mb->gForce>*gF)mb->gForce+=*gF;
   if(iCheck)findZMach(ctx,mb,&mb->gForce);
   //findBestMach(&ctxc,mb->gForce);
}

//static const int tabx[]={-1,0,1,-1,1,-1,0,1};
//static const int taby[]={-1,-1,-1,0,0,1,1,1};
static const int tabx[]={1,-1,0,0, 1,-1,-1, 1};
static const int taby[]={0,0,1,-1, 1, 1,-1,-1};

int checkAvgs(CTX *ctx, T_MB *mbCur)
{
   if(mbCur->iAvgsChecked)return 0;
   mbCur->iAvgsChecked=1;

   return 1;
}
int checkPrevs(CTX *ctx, T_MB *mbCur)
{
   if(mbCur->iPrevsChecked)return 0;//mbCur->iNearsChecked;
   if(mbCur->cur.iSad && mbCur->cur.iSad<T_SAD_MIN)return 0;
   mbCur->iPrevsChecked=1;
   //mbCur->gForce.x=mbCur->prev.x*2;
   //mbCur->gForce.y=mbCur->prev.y*2;
   T_MB *mb;//=&ctx->mb[mbCur->x+ctx->xc*mbCur->y];
   int j1,i1;
   if(1)//(mbCur->prev.iSad<mbCur->cur.iSad ||mbCur->cur.iSad==0))
      findZMach(ctx,mbCur,&mbCur->prev);
   if(mbCur->prevd2.iSad && (mbCur->prevd2.iSad<mbCur->cur.iSad ||mbCur->cur.iSad==0))
      findZMach(ctx,mbCur,&mbCur->prevd2);
   if(mbCur->prevd2.iSad && (mbCur->prevdm2.iSad<mbCur->cur.iSad ||mbCur->cur.iSad==0))
      findZMach(ctx,mbCur,&mbCur->prevdm2);
   if(mbCur->prevd2.iSad && (mbCur->prevdmm2.iSad<mbCur->cur.iSad ||mbCur->cur.iSad==0))
      findZMach(ctx,mbCur,&mbCur->prevdmm2);

   int iSMax=getTabSad(60)*32;
   int iSMin=getTabSad(24)*8;
   int iDiv=2;
   for(int z=1;z<3 && (z==1 || mbCur->cur.iSad==0 || iSMin<mbCur->cur.iSad);z++)
   for(int i=0;i<8;i++)
   {
      i1=tabx[i]*z+mbCur->x;
      j1=taby[i]*z+mbCur->y;
      if(j1<0 || j1>=ctx->yc ||
         i1<0 || i1>=ctx->xc )continue;
      
      mb=&ctx->mb[i1+ctx->xc*j1];
      if((z<3 && mb->prevdmm2.iSad && mb->prevdmm2.iSad*z<mbCur->cur.iSad && iSMax>mb->prevdmm2.iSad) || mbCur->cur.iSad==0)
         findZMach(ctx,mbCur,&mb->prevdmm2);

      if((z<3 && mb->prevd2.iSad && mb->prevd2.iSad*z<mbCur->cur.iSad && iSMax>mb->prevd2.iSad) || mbCur->cur.iSad==0)
         findZMach(ctx,mbCur,&mb->prevd2);

      if((z<3 && mb->prevdm2.iSad && mb->prevdm2.iSad*z<mbCur->cur.iSad && iSMax>mb->prevdm2.iSad) || mbCur->cur.iSad==0)
         findZMach(ctx,mbCur,&mb->prevdm2);

      if((//mb->prev.iSad*z<mbCur->cur.iSad*2 && 
         iSMax>mb->prev.iSad) ||mbCur->cur.iSad==0)
      {
         findZMach(ctx,mbCur,&mb->prev);
         /*
         if(z<3)
         {
            mbCur->gForce.x+=mb->prev.x;
            mbCur->gForce.y+=mb->prev.y;
            iDiv++;
         }
         */
      }
   }
   //mbCur->gForce.x/=iDiv;
   //mbCur->gForce.y/=iDiv;
   //findZMach(ctx,mbCur,&mb->gForce);

   mbCur->iPrevsChecked=1;
   return 1;
}

int checkNears(CTX *ctx, T_MB *mbCur)
{
   if(mbCur->iNearsChecked>6)return mbCur->iNearsChecked;
   if(mbCur->cur.iSad && mbCur->cur.iSad<T_SAD_MIN)return 0;
   mbCur->iNearsChecked=0;
   //mbCur->iNearsChecked++;
   //vector v;
   int iRecMax=getTabSad(32)*64;
   T_MB *mb;
   int j1,i1;
   int cnt=0;
   for(int z=1;z<3;z++)
   for(int i=0;i<8;i++)
   {
      i1=tabx[i]*z+mbCur->x;
      j1=taby[i]*z+mbCur->y;
      if(j1<0 || j1>=ctx->yc ||
         i1<0 || i1>=ctx->xc )continue;
      
      mb=&ctx->mb[i1+ctx->xc*j1];
      if(!mb->cur.iSad //|| mb->cur.iSad<mbCur->cur.iSad//iRecMax/z
         || mb->cur.iSad>iRecMax/z)continue;

      if(z==1)cnt++;

      //v.iSad=iBestSad;
      findZMach(ctx,mbCur,&mb->cur);
   }
   //if(mbCur->iNearsChecked==0 && mbCur->prev.iSad<getTabSad(100)*mb->iMbSize*2)
   mbCur->iNearsChecked+=cnt;
   if(cnt==0)mbCur->iNearsChecked=0;
   return cnt;
}
int findBestMach(CTX *ctx, T_MB *mb, vector *vPred=NULL, int id=99)
{
   if(mb->iRefined)return mb->cur.iSad;
   //debugss("as=",mb->posx,mb->posy);
   //debugss("as=");
   //return 0;
   findZMach(ctx,mb,NULL);
   int iSad=findZMach(ctx,mb,vPred);

   checkPrevs(ctx,mb);
   int cnt=checkNears(ctx,mb);
   if(mb->cur.iSad<16*40)return mb->cur.iSad;

   int x=0;
   int y=0;
   int iBestSad=mb->cur.iSad?mb->cur.iSad:T_SAD_MAX;
   
   if(mb->cur.iSad)
   {
      x=mb->cur.x;y=mb->cur.y;
   }
   
   //if(iSpeed!=1 && mb->cur.iSad && mb->cur.x==x && mb->cur.y==y) 
   {
     // return mb->cur.iSad;
   }
   int iSpeed=0;
   if(cnt<6)iSpeed=-1;
   if(cnt>7)iSpeed=1;
   if(id==200)iSpeed=2;
   if(mb->cur.iSad && mb->cur.iSad<T_SAD_MIN)iSpeed=4;
   iSpeed=diamondSearch(ctx,mb,x,y,iBestSad,iSpeed);
   //dimond
   return mb->cur.iSad;
}


//#define updateBestMach(_A,_B,_C) findZMach(_A,_B,_C)

inline int updateBestMach(CTX *ctx, T_MB *mb, vector *vPred, int iCheckZeroOnly=0, int iRecurs=0)
{
   //MOTI_TYPE *ref=getRef(mb,vPred->x,vPred->y);

   int iSad=mb->cur.iSad;
   int iBestSad=mb->cur.iSad?mb->cur.iSad:T_SAD_MAX;
   if(mb->cur.iSad && *vPred==mb->cur)goto ss;
      //(iRecurs && vPred->iSad && mb->cur.iSad && vPred->iSad>mb->cur.iSad))goto ss;

  // if(mb->cur.iSad && mb->cur.iSad<T_SAD_MIN)goto ss;

   if(mb->posx+vPred->x/2<-2 || mb->posy+vPred->y/2<0)
      return T_SAD_MAX;
   if((mb->posx+mb->iMbSize)+vPred->x/2>=ctx->w+2 || 
      (mb->posy+mb->iMbSize)+vPred->y/2>=ctx->h)
      return T_SAD_MAX;

   iSad=getSadCon(&mb->checkBits[20],mb->pCur,mb->pOld,mb->pRef,mb->iMbSize,vPred->x,vPred->y,ctx->stride,iBestSad,mb->gForce.x,mb->gForce.y);
   if(iSad<mb->cur.iSad || mb->cur.iSad==0)
   {
      mb->cur.x=vPred->x;
      mb->cur.y=vPred->y;
      mb->cur.iSad=iSad;
      if(iCheckZeroOnly==0)diamondSearch(ctx,mb,mb->cur.x,mb->cur.y,iSad,1);
      else mb->iRefined=0;
ss:
      int a1=t_mgabs(ctx->vRecFirst.x-mb->cur.x);
      int a2=t_mgabs(ctx->vRecFirst.y-mb->cur.y);
      //if(a1+a2>8)return  T_SAD_MAX;
//      int ims=(mb->cur.iSad>>3)*(((a1+a2)>>1)+5)*ctx->iCurRecurs;//*(a1+a2+1);
      int ims=(mb->cur.iSad>>1)*(((a1+a2))+1)*ctx->iCurRecurs;//*(a1+a2+1);
      if(ims<mb->iMarkedSad || mb->iMarkedSad==0)
      {
         mb->iMarkedSad=ims;
         return ims;//T_SAD_MAX;
      }
   }
   return T_SAD_MAX;
}
inline int checkZeroAndPrevs(CTX *ctx, T_MB *mb)
{
   int iBestSad=T_SAD_MAX;
   int iSad=getSadCon(&mb->checkBits[20],mb->pCur,mb->pOld,mb->pRef,mb->iMbSize,0,0,ctx->stride,iBestSad,0,0);
   if(iSad<mb->cur.iSad || mb->cur.iSad==0)
   {
      mb->cur.x=mb->cur.y=0;
      mb->cur.iSad=iSad;
      iBestSad=iSad;
   }
   checkPrevs(ctx,mb);
   return iBestSad;
}
inline int getFirstMach(CTX *ctx, T_MB *mb, vector *vPred)
{
   int iBestSad=T_SAD_MAX;
   //
   
   //mb->cur.iSad=iSad;
   //iBestSad=iSad;
   
   do{

      if(mb->posx+vPred->x/2<-2 || mb->posy+vPred->y/2<0)
         break;
      if((mb->posx+mb->iMbSize)+vPred->x/2>=ctx->w+2 || 
         (mb->posy+mb->iMbSize)+vPred->y/2>=ctx->h)
         break;
      checkZeroAndPrevs(ctx,mb);

   
      int iSad=getSadCon(&mb->checkBits[20],mb->pCur,mb->pOld,mb->pRef,mb->iMbSize,vPred->x,vPred->y,ctx->stride,iBestSad,mb->gForce.x,mb->gForce.y);
      if(iSad<mb->cur.iSad || mb->cur.iSad==0)
      {
         mb->cur.x=vPred->x;
         mb->cur.y=vPred->y;
         mb->cur.iSad=iSad;
      }
      diamondSearch(ctx,mb,mb->cur.x,mb->cur.y,mb->cur.iSad,4);
      int a1=t_mgabs(ctx->vRecFirst.x-mb->cur.x);
      int a2=t_mgabs(ctx->vRecFirst.y-mb->cur.y);
      //if(a1+a2>8)return  T_SAD_MAX;

      //int ims=(mb->cur.iSad>>3)*(((a1+a2)>>1)+5)*ctx->iCurRecurs;//*(a1+a2+1);
      int ims=(mb->cur.iSad>>1)*(((a1+a2))+1)*ctx->iCurRecurs;//*(a1+a2+1);
      if(ims<mb->iMarkedSad || mb->iMarkedSad==0)
      {
         mb->iMarkedSad=ims;
         return ims;//T_SAD_MAX;
      }
      return T_SAD_MAX;


   }while(0);
   checkZeroAndPrevs(ctx,mb);

   return T_SAD_MAX;
}

#define BEST_MB(_A,_B)((_A)->cur>(_B)->cur?(_B):(_A))

int checkMidle(CTX *ctx, T_MB *mb)
{
   int iSs=mb->cur.iSad;
   T_MB *mb1,*mb2;
   vector v;
#define T_CH(_M){if(!(_M)->cur.iSad){::findZMach(ctx,_M,NULL); checkPrevs(ctx,_M);}}
   int iF=0;
   if(mb->x+1<ctx->xc && mb->x>=1)
   {
      iF++;
      mb1=mb+1;
      mb2=mb-1;
      T_CH(mb1);
      T_CH(mb2);
      v.iSad=0;
      v.x=(mb1->cur.x+mb2->cur.x+1)>>1;
      v.y=(mb1->cur.y+mb2->cur.y+1)>>1;
      findZMach(ctx,mb,&v);
   }
   if(mb->y+1<ctx->yc && mb->y>=1)
   {
      iF++;
      mb1=mb+ctx->xc;
      mb2=mb-ctx->xc;
      T_CH(mb1);
      T_CH(mb2);
      v.iSad=0;
      v.x=(mb1->cur.x+mb2->cur.x+1)>>1;
      v.y=(mb1->cur.y+mb2->cur.y+1)>>1;
      findZMach(ctx,mb,&v);
   }
   if(iF==2)
   {
      mb1=mb+ctx->xc+1;
      mb2=mb-ctx->xc-1;
      T_CH(mb1);
      T_CH(mb2);
      v.iSad=0;
      v.x=(mb1->cur.x+mb2->cur.x+1)>>1;
      v.y=(mb1->cur.y+mb2->cur.y+1)>>1;
      findZMach(ctx,mb,&v);

      mb1=mb-ctx->xc+1;
      mb2=mb+ctx->xc-1;
      T_CH(mb1);
      T_CH(mb2);
      v.iSad=0;
      v.x=(mb1->cur.x+mb2->cur.x+1)>>1;
      v.y=(mb1->cur.y+mb2->cur.y+1)>>1;
      findZMach(ctx,mb,&v);
   }

   return iSs!=mb->cur.iSad;
}
#include <stdlib.h>

int compare_shorts( const void* a, const void* b ) {
   //int  arg1 = *(int*) a;
   //int  arg2 = *(int*) b;
   if( *(short*)a < *(short*)b ) return -1;
   else if( *(short*)a == *(short*)b ) return 0;
   else return 1;
}  
int getMedianShort(short *a, int iCnt)
{

   int m=iCnt>>1;
   int n=(iCnt-1)>>1;
   qsort( a, iCnt, sizeof(short), compare_shorts ); 

   if(m==n)
   {
      return a[m];
   }
   return (a[m]+a[n]+1)/2;

}

int checkMedian(CTX *ctx, T_MB *mb, int z, vector *vecm=NULL)
{
   int iSs=mb->cur.iSad;
   int xa[8*5];
   int ya[8*5];
   int xc=ctx->xc,yc=ctx->yc;
   int x=mb->x;
   int y=mb->y;
   int iCnt=0;
   int iSetGForce=z;

   T_MB *m;


   int i,j1,i1;
   int zz=z<=0?1:z;
   //int iFrom=z;

   for(i=0;i<8;i++){
      i1=x+tabx[i]*zz;
      j1=y+taby[i]*zz;
      if(j1<0 || j1>=ctx->yc ||
         i1<0 || i1>=ctx->xc )continue;
      m=&ctx->mb[i1+xc*j1];
      if(!m->cur.iSad)continue;
      xa[iCnt]=m->cur.x;
      ya[iCnt]=m->cur.y;
      iCnt++;
   }
   

   if(z<0 && iSetGForce==0)
   {
      z=-z+1;
      for(zz=2;zz<z;zz++)
      {
      for(i=0;i<8;i++){
         i1=x+tabx[i]*zz;
         j1=y+taby[i]*zz;
         if(j1<0 || j1>=ctx->yc ||
            i1<0 || i1>=ctx->xc )continue;
         m=&ctx->mb[i1+xc*j1];
         if(!m->cur.iSad)continue;
         xa[iCnt]=m->cur.x;
         ya[iCnt]=m->cur.y;
         iCnt++;
      }
      }
   }
   if(iCnt<3)return 0;
//   qsort( ya, iCnt, sizeof(int), compare_ints ); 

   int iCntX=iCnt;
   int iCntY=iCnt;
   if(iCnt>11)
   {
      iCntX=0;
      iCntY=0;
      for(z=0;z<iCnt;i++)
      {
         if(xa[z]){xa[iCntX]=xa[z];iCntX++;}
         if(ya[z]){xa[iCntY]=ya[z];iCntY++;}
      }
      if(iCntX!=iCnt ){xa[iCntX]=0;iCntX++;}
      if(iCntY!=iCnt ){ya[iCntY]=0;iCntY++;}
      if(iCntX<3 || iCntY<3)return 0;
   }
   vector v;
   v.x=getMedian(&xa[0],iCntX);
   v.y=getMedian(&ya[0],iCntY);
   /*
   if(iSetGForce)
   {
      if(mb->cur.iSad)
      {
         mb->gForce+=v;
      }
      else
         mb->gForce=v;
      findZMach(ctx, mb, &mb->gForce);
      return 0;
   }
   */
   //if(vecm)mb->gForce=v;
   findZMach(ctx, mb, &v);
   if(vecm)*vecm=v;

   return iSs!=mb->cur.iSad;
}


T_MB *checkMidleVec(CTX *ctx, T_MB *mb1, T_MB *mb2)
{
   if(mb1==mb2)return mb1;
   T_MB *best=BEST_MB(mb1,mb2);
   int dx=mb2->x-mb1->x;
   int dy=mb2->y-mb1->y;
   if(t_mgabs(dx)>1 || t_mgabs(dy)>1)
   {
      dx/=2;
      dy/=2;
      int x=mb1->x+dx;
      int y=mb1->y+dy;
      T_MB *m=&ctx->mb[x+ctx->xc*y];

      vector v;
      v.iSad=0;
      v.x=(mb1->cur.x+mb2->cur.x)/2;
      v.y=(mb1->cur.y+mb2->cur.y)/2;
      findZMach(ctx,m,NULL);
      findZMach(ctx,m,&v);
      checkPrevs(ctx,m);
      int r=m->iPrevsChecked;
      m->iPrevsChecked=1;
      checkNears(ctx,m);
      m->iPrevsChecked=r;

      diamondSearch(ctx,m,v.x,v.y,T_SAD_MAX,3);

      best=BEST_MB(m,best);
      best=BEST_MB(checkMidleVec(ctx,m,mb1),best);
      best=BEST_MB(checkMidleVec(ctx,m,mb2),best);
   }
   //int len=min(dx,dy);

   return best;
}

void refineMB(CTX *ctx, T_MB *mb, int iNears=0)
{
   checkNears(ctx,mb);
   if(mb->gPrevRefined==mb->cur)return;
   mb->iRefined=1;
   diamondSearch(ctx,mb,mb->cur.x, mb->cur.y, mb->cur.iSad, 1);
   
   mb->gPrevRefined=mb->cur;
}
 

void checkNearsX1(CTX *ctx, T_MB *mbCur)
{
   int xc=ctx->xc,yc=ctx->yc;
   int x=mbCur->x;
   int y=mbCur->y;
   int i,i1,j1;
   T_MB *mb;
   int iRefine=0;
   int iSs=mbCur->cur.iSad;
   if(!mbCur->cur.iSad)
   {
      checkMedian(ctx,mbCur,0);

      ::findZMach(ctx,mbCur,NULL);
      checkPrevs(ctx,mbCur);
      iRefine=1;
   }
   
   int iMin=getTabSad(8)*4;
   int iAbsMin=getTabSad(4)*2;
   int iMax=getTabSad(24)*64;//getTabSad(120)*mbCur->iMbSize;//mbCur->iMbSize;

   int iCnt=0;
   int zz=1;

   for(int z=1;z<4 && (iAbsMin<mbCur->cur.iSad || z==1);z++)
   {

      if(z==2){//z==2){
         checkMedian(ctx,mbCur,0);
         //mbCur->gForce+=mbCur->cur;
      }
   for(i=0;i<8;i++){
      i1=x+tabx[i]*z;
      j1=y+taby[i]*z;
      if(j1<0 || j1>=ctx->yc ||
         i1<0 || i1>=ctx->xc )continue;
      
      mb=&ctx->mb[i1+xc*j1];


      //if(mb->iRefined || mb->iRecursiveChecked)
      if(mb->cur.iSad)
      {
         if(mb->iRefined!=2 && mbCur->cur.iSad<iMax && mbCur->cur.iSad*8<mb->cur.iSad && iAbsMin<mb->cur.iSad)
            updateBestMach(ctx,mb,&mbCur->cur,0);
         if(mb->cur.iSad<iMax && mb->cur.iSad*zz<mbCur->cur.iSad && mbCur->cur.iSad>iMin)
         {
            updateBestMach(ctx,mbCur,&mb->cur,1);//bija
            iCnt++;
            iRefine=1;
         }
         if(iCnt>16)zz=8;
         else if(iCnt>8)zz=6;
         else if(iCnt>4)zz=2;
      }
   }
   }

   checkMedian(ctx,mbCur,0);
   
      iRefine+=checkMidle(ctx,mbCur);
      iRefine+=checkMedian(ctx,mbCur,0);
   //if(mbCur->cur.iSad>iMin)
   //iRefine+=checkMedian(ctx,mbCur,2);
   iRefine+=checkMedian(ctx,mbCur,-4);

      
   if(iSs!=mbCur->cur.iSad || iSs==0)
   {
      if(iCnt<2)
         diamondSearch(ctx,mbCur,mbCur->cur.x,mbCur->cur.y,mbCur->cur.iSad,0);
      else 
         refineMB(ctx,mbCur);
   }
   mbCur->iRefined=2;
}

void checkNearsX(CTX *ctx, T_MB *mbCur)
{
   int xc=ctx->xc,yc=ctx->yc;
   int x=mbCur->x;
   int y=mbCur->y;
   int i,i1,j1;
   T_MB *mb;
   int iRefine=0;
   int iSs=mbCur->cur.iSad;
   vector vec;
   if(!mbCur->cur.iSad)
   {
      ::findZMach(ctx,mbCur,NULL);
      vec.x=0;vec.y=0;
      checkMedian(ctx,mbCur,0,&vec);
   }
   
   int iMin=getTabSad(40)*6;
   int iAbsMin=getTabSad(20)*8;
   int iMax=getTabSad(60)*64;//getTabSad(120)*mbCur->iMbSize;//mbCur->iMbSize;

   if(iMin<mbCur->cur.iSad)
      return;
   int iCnt=0;
   int zz=1;

   for(int z=1;z<2 && (iMin<mbCur->cur.iSad || z==1);z++)
   {

      if(z==2){//z==2){
        // checkMedian(ctx,mbCur,0);
         //mbCur->gForce+=mbCur->cur;
      }
   for(i=0;i<8;i++){
      i1=x+tabx[i]*z;
      j1=y+taby[i]*z;
      if(j1<0 || j1>=ctx->yc ||
         i1<0 || i1>=ctx->xc )continue;
      
      mb=&ctx->mb[i1+xc*j1];


      //if(mb->iRefined || mb->iRecursiveChecked)
      if(mb->cur.iSad)
      {
         if(//mb->iRefined!=2 && 
            mbCur->cur.iSad*4<mb->cur.iSad)
            updateBestMach(ctx,mb,&mbCur->cur,z>1);
         int TODO_MB_REFINE;
         if(iMin<mb->cur.iSad && mb->cur.iSad<mbCur->cur.iSad*4 && (z<2 || t_mgabs(mb->cur.x-mbCur->cur.x)>2 || t_mgabs(mb->cur.x-mbCur->cur.y)>2))
         {
            mbCur->gForce=mb->cur;
            int iSadO=mbCur->cur.iSad;
            updateBestMach(ctx,mbCur,&mb->cur,1);//bija
            iCnt++;
            
            if(z<3 && iSadO>mbCur->cur.iSad){vec=mbCur->cur;iRefine=1;}
         }
         if(iCnt>16)zz=8;
         else if(iCnt>8)zz=6;
         else if(iCnt>4)zz=2;

      }
   }
   }
   if(!iRefine){
      checkPrevs(ctx,mbCur);
      mbCur->gForce=mbCur->cur;

   }
   if(iRefine)mbCur->gForce=vec;
   if(mbCur->cur.iSad>1)
   {
      if(!iRefine){
         mbCur->gForce.x=-10000;
         mbCur->gForce.y=-10000;
      }
      
      if(mbCur->cur.iSad>iMin)checkPrevs(ctx,mbCur);   

      checkMedian(ctx,mbCur,0);
      
         //iRefine+=checkMidle(ctx,mbCur);
        // iRefine+=checkMedian(ctx,mbCur,0);
      //if(mbCur->cur.iSad>iMin)
      //iRefine+=checkMedian(ctx,mbCur,2);
    //--  iRefine+=checkMedian(ctx,mbCur,-4);

        
      if(iSs!=mbCur->cur.iSad || iSs==0)
      {
      //   if(iCnt<2)
            if(mbCur->cur.iSad>iMin)diamondSearch(ctx,mbCur,mbCur->cur.x,mbCur->cur.y,mbCur->cur.iSad,2);
         //else 
           // diamondSearch(ctx,mbCur,mbCur->cur.x,mbCur->cur.y,mbCur->cur.iSad,0);
      }
      
   }
   mbCur->iRefined=2;
}

int iRecColor=0;
int findV(CTX *ctx, T_MB *mbCur, T_MB *mbPrev, int iRecursion, int iVecCnt)
{
   int i;
   int xc=ctx->xc,yc=ctx->yc;
   int i1,j1;
   int iSad;
   T_MB *mb;
   //int iBestNe=0x7ffffff;
   // 012
   //34
   //567

   typedef struct{
      int id;
      int iSad;
      T_MB *mb;
   }BEST_NEAR;

   BEST_NEAR bn[]={{-1,T_SAD_MAX,NULL},
                   {-1,T_SAD_MAX,NULL},
                   {-1,T_SAD_MAX,NULL},
                   {-1,T_SAD_MAX,NULL},
                   {-1,T_SAD_MAX,NULL},
                   {-1,T_SAD_MAX,NULL}};
   if(mbCur->iRecursiveChecked)return iVecCnt+1;
   
   //void drawPix(int x, int y, int c);
   if(iRecursion==0){iRecColor+=0x3000;iRecColor&=0xffff00;}
   if(iRecColor>0xcc000000)iRecColor=0;
   //drawPix(mbCur->x,ctx->yc-mbCur->y,iRecColor);
   

   mbCur->iRecursiveChecked=2;

   int iRecMax;//=min(mbCur->cur.iSad*8+getTabSad(20)*16,getTabSad(90)*mbCur->iMbSize);

   int x=mbCur->x;
   int y=mbCur->y;
   int iTODO_TestingBest3;
   int iRefine=0;
   if(iRecursion==0)ctx->vRecFirst=mbCur->cur;else if(iRecursion<3)ctx->vRecFirst+=mbCur->cur;
   mbCur->gForce=ctx->vRecFirst;
   //if(iRecursion>3)ctx->vRecFirst=mbCur->gForce=mbPrev->cur;
   
   //checkNears
   //refineMB(ctx,mbCur);
   int iMin=getTabSad(15)*8;
   
   ctx->iCurRecurs=iRecursion+1;
   
   for(int z=1;z<2;z++)
   for(i=0;i<8;i++){
      i1=x+tabx[i]*z;
      j1=y+taby[i]*z;
      if(j1<0 || j1>=ctx->yc ||
         i1<0 || i1>=ctx->xc )continue;
      
      mb=&ctx->mb[i1+xc*j1];


      if(mbPrev==mb)continue;
      if(mb->iRefined || mb->iRecursiveChecked)
      {
         if(mb->cur.iSad>mbCur->cur.iSad && mb->cur.iSad>iMin)updateBestMach(ctx,mb,&mbCur->cur,1);
         if(mb->cur.iSad<mbCur->cur.iSad && mbCur->cur.iSad>iMin)
         {
            updateBestMach(ctx,mbCur,&mb->cur,1);//bija
            iRefine=1;
         }
         continue;
      }
   }

      
   if(iRefine)
      refineMB(ctx,mbCur);
   iRefine=0;

   //iRecMax=min(mbCur->cur.iSad*8+getTabSad(70)*mbCur->iMbSize,getTabSad(90)*mbCur->iMbSize);
   //iRecMax=(getTabSad(24)*64/((iRecursion+4)>>2)+mbCur->cur.iSad)>>1;
   iRecMax=(getTabSad(16)*64);
   //iRecMax*=2;
   //iRecMax<<=1;

   for(int z=1;z<2 && bn[3].mb==NULL;z++){
      if(iRefine){
         refineMB(ctx,mbCur);//diamondSearch(ctx,mb,mb->cur.x,mb->cur.y,mb->cur.iSad,0);
         iRefine=0;
      }
      //if(z>1){iRecMax*=7;iRecMax>>=3;}
   for(i=0;i<8;i++){
      //if(z==1)continue;
      i1=x+tabx[i]*z;
      j1=y+taby[i]*z;
      if(j1<0 || j1>=ctx->yc ||
         i1<0 || i1>=ctx->xc )continue;
      
      mb=&ctx->mb[i1+xc*j1];


      if(mbPrev==mb)continue;
      if(mb->iRecursiveChecked)//mb->iRefined || mb->cur.iSad)// || mb->iRecursiveChecked)
      {
         /*
         if(z>1)
         {
            iRefine=1;
           updateBestMach(ctx,mbCur,&mb->cur,1);//bija
           if(!mb->iRecursiveChecked)updateBestMach(ctx,mb,&mbCur->cur,0);
         }
         */
         continue;
      }
      /*
      
      if(mb->iRecursiveChecked){

         if(mb->gForce != mbCur->cur && mb->cur != mbCur->cur && mb->gForce.iSad > mbCur->cur.iSad)            {
            mb->gForce=mbCur->cur;//TODO gForceAvg
            //int iCanRef=mb->iRecursiveChecked==2 && mb->iRefined==0;
            updateBestMach(ctx,mb,&mbCur->cur,mb->iRecursiveChecked==1?1:0);
            //updateBestMach(ctx,mb,&mbCur->cur,!iCanRef);
         }
         continue;//???????
      }
      */
     
      if(mb->cur.iSad){// && !mb->iRefined){
         ///if()
         //updateBestMach(ctx,mbCur,&mb->cur,1);
         //checkPrevs(ctx,mb);
         iSad=updateBestMach(ctx,mb,&mbCur->cur,1,1);
         //iRefine=1;
         //continue;//???????????????????
      }
      else
         iSad=getFirstMach(ctx,mb,&mbCur->cur);
      //iSad=findZMach(ctx,mb,&mbCur->cur);
         if(iSad>iRecMax*((iRecursion+3)>>1)+1)continue;
      //if(iSadMaxRec<iSad*2)continue;

      //if(mb->iSadFrom && mb->iSadFrom<iSad)continue;
      //if(iSad>8*8*200)continue;
      if(iSad< bn[0].iSad){
         bn[5]=bn[4];
         bn[4]=bn[3];
         bn[3]=bn[2];
         bn[2]=bn[1];
         bn[1]=bn[0];
         //bn[0].id=i;
         bn[0].iSad=iSad;
         bn[0].mb=mb;
      }
      else if(iSad< bn[1].iSad){
         bn[5]=bn[4];
         bn[4]=bn[3];
         bn[3]=bn[2];
         bn[2]=bn[1];
         //bn[1].id=i;
         bn[1].iSad=iSad;
         bn[1].mb=mb;
      }
      else if(iSad< bn[2].iSad){
         bn[5]=bn[4];
         bn[4]=bn[3];
         bn[3]=bn[2];
         bn[2].iSad=iSad;
         //bn[2].id=i;
         bn[2].mb=mb;
      }
      else if(iSad< bn[3].iSad){
         bn[5]=bn[4];
         bn[4]=bn[3];
         bn[3].iSad=iSad;
         bn[3].mb=mb;
      }
      else if(iSad< bn[4].iSad){
         bn[5]=bn[4];
         bn[4].iSad=iSad;
         bn[4].mb=mb;
      }
      else if(iSad< bn[5].iSad){
         bn[5].iSad=iSad;
         bn[5].mb=mb;
      }
      
   }
   for(i=0;i<6;i++) {
      if(!bn[i].mb) continue;
       
     // mb->iSadFrom=bn[i].iSad;
      mb=bn[i].mb;
      mb->mbRecurs=mbCur;
      
      //orPosiblGF
     // setNewGForce(ctx,mb,&mbCur->cur);
   }
   }
   //if(mbPrev)updateBestMach(ctx,mbCur,&mbPrev->cur,0);
   //vector v=mbCur->cur;
   if(iRefine)refineMB(ctx,mbCur);
   //mbCur->iRefined=0;
   
   iVecCnt++;

   for(i=0;i<6;i++) {
      if(!bn[i].mb) continue;
         
      mb=bn[i].mb;
      if(mb->mbRecurs!=mbCur)continue;
      
      if(!mb->iRecursiveChecked)  {
         //findBestMach(ctx,mb,&mbCur->cur,i);

         //if(bn[i].iSad<8*mbCur->cur.iSad)
     //    {
           // findBestMach(ctx,mb,NULL,i);
            
            iVecCnt=findV(ctx,mb,mbCur,iRecursion+1,iVecCnt);
       //  }
      }
   }
   
   mbCur->iRecursiveChecked=1;
   refineMB(ctx,mbCur,mbCur->cur.iSad>8*getTabSad(40));
   if(mbCur->cur.iSad>mbCur->iMbSize*getTabSad(50)*2)mbCur->iRefined=0;
   return iVecCnt;
}

void getBestStartPoint(CTX *ctx, T_MB *mbs, T_MB *mb, vector *gf)
{
   //mb->prevd2
   const int xc=ctx->xc;

   if(1){
      int tt[]={0,1,xc,xc+1};
      int i;
      for(i=0;i<4;i++)
      {
   //#define F checkZeroAndPrevs
         //if(i>3 && (mb->x==0 || mb->y==0))continue;

         findZMach(ctx,mbs,&mb[tt[i]].prev,gf);
         findZMach(ctx,mbs,&mb[tt[i]].prevd2,gf);
         findZMach(ctx,mbs,&mb[tt[i]].prevdm2,gf);
         findZMach(ctx,mbs,&mb[tt[i]].prevdmm2,gf);
      }
   }
   //0123
   //1
   //2

   if(mb->x>1){
      findZMach(ctx,mbs,&mb[-1-xc].cur,gf);
      findZMach(ctx,mbs,&mb[-1].cur,gf);
      findZMach(ctx,mbs,&mb[-1+xc].cur,gf);
   }
   if(mb->y>1){
      findZMach(ctx,mbs,&mb[-xc].cur,gf);
      findZMach(ctx,mbs,&mb[1-xc].cur,gf);
   }
  // if(mb->x>1 && mb->y>1)findZMach(ctx,mbs,&mb[-xc-1].cur,gf);
   //if(mb->y>1 && mb->x+2<ctx->xc)findZMach(ctx,mbs,&mb[2-xc].cur,gf);


}


int findV2x(CTX *ctx){

   T_MB *mb;//=&ctx->mb;
   int xc=ctx->xc;
   int yc=ctx->yc;
   int x,y;
   T_MB v2x;
   //vector
   int yt=yc-2;
   int xt=xc-2;
   int iFirst=0;
   for(y=1;y<yt;y+=2)
   {
      for(x=1;x<xt;x+=2)
      {
         mb=&ctx->mb[y*xc+x];
         v2x=*mb;
         //v2x.checkBits[]
         v2x.iMbSize*=2;
         vector gf;
         if(mb->x|mb->y){
            gf.x=0;gf.y=0;gf.iSad=0;
            if(mb->x){
               gf=mb[-1].cur;
            }
            if(mb->y){
               if(mb->x)gf+=mb[-ctx->xc].cur;else gf=mb[-ctx->xc].cur;
            }
         }
         else {
            gf.x=0;gf.y=0;
            gf.iSad=0;
         }

         findZMach(ctx,&v2x,NULL, &gf);
         getBestStartPoint(ctx,&v2x,mb, &gf);
         diamondSearch(ctx,&v2x,0,0,v2x.cur.iSad,-3, &gf);
         findZMach(ctx,mb,&v2x.cur);
         if(x+1!=xc)findZMach(ctx,&mb[1],&v2x.cur);
         if(y+1!=yc)findZMach(ctx,&mb[xc],&v2x.cur);
         if(y+1!=yc && x+1!=xc)findZMach(ctx,&mb[xc+1],&v2x.cur);
         //if(y==3 && x==5)debugss("vec",v2x.cur.x,v2x.cur.y);
      }
   }
   return 0;
}



int fncaa(VEC_C_STR2 *ctx, int *v1, int *v2, int *pSads)
{
  // debugss("fnca 1",0,0);
   int w=ctx->w;
   int h=ctx->h;
   //if(w<650 || h<350)return 0;
return 0;
//int int id);
//debugss("me1",0,0);
   MOTI_TYPE *pOld;
   int iCur=ctx->refid;
   VEC_IMG_STRUCT *ref;//=refid==-1?ctx->imgOldMin1:&ctx->imgOld[refid];

   ref=ctx->imgOldMin1;
   
   int iCmpWith=ctx->refs[0];
   if(iCmpWith==-1)
      ref=ctx->imgOldMin1;
   else  if(iCmpWith==-2)
      ref=ctx->imgOldMin2;
   else
      ref=&ctx->imgOld[iCmpWith];

   static CTX ctxc={0};
   //ctxc.w=0;ctxc.h=0;

//debugss("fnca 11",0,(int)ref);
   if(!ref->iRefCreated)
   {
      ref->poi.check(w,h);
      toBW(ref->img.getBuf(),ref->poi.p,w,h); 
   }
   createRefh(ref,ctxc.pRef,ref->poi.p,w,h);

         

   ctxc.pOld=ref->poi.p;
   ctxc.pCur=ctx->mt_pi;//ctx->imgOld[iCur].poi.p;
//debugss("fnca 2",0,(int)ctx->pCurImg);

   ctxc.init(w,h,ctx->iMBSize*2,ctx->pCurImg,ref->img.getBuf());//,piIn.p,piR.p);
   //ctx->pCurImg=

//   if(iCur+1!=ctx->iMaxRefs)return;
  /*
   static CTPIC piIn;
   static CTPIC piR;
   piIn.check(w,h);
   piR.check(w,h);
   void toBW_UV(unsigned char *p, MOTI_TYPE *dst, int w, int h);
   toBW_UV(ctx->pCurImg-w*3,piIn.p-w,w,h+2);
   toBW_UV(ref->img.getBuf()-w*3,piR.p-w,w,h+1);
*/


   //iSadMaxRec=ctx->iMBSize*5*getTabSad(85);

   int xc=ctxc.xc,yc=ctxc.yc;
   int iVecSize=(xc*yc)*(ctx->iMaxRefs+iCur+5);
   v1+=iVecSize;
   v2+=iVecSize;
   pSads+=iVecSize;
   int remv=0;//(iCur)?(xc*yc):0;
   
//debugss("fnca 3",0,0);
   ctxc.setPrevs(v1,v2,pSads,remv);
   //init(&ctx->mb[0],xc,yc);

   //int iVecSize=xc*yc*(iCur+1);
   //iVecStep*ctx->iMaxRefs
//   iSadMaxRec=(ctx->iMBSize)*(ctx->iMBSize)*getTabSad(34);
   iSadMaxRec=(ctx->iMBSize)*(ctx->iMBSize)*getTabSad(12);


   T_MB *mb;

   int i=xc/2;
   int j=yc/2;
   if(0)
   {
      T_MB *mb12,*mb34,*mb13,*mb24;
      T_MB *mbx,*mby;
      T_MB *mba1=&ctxc.mb[1+xc];
      T_MB *mba2=&ctxc.mb[xc*2-2];

      mbx=&ctxc.mb[j*xc+i-1];
      mby=&ctxc.mb[j*xc+i+1];
      findBestMach(&ctxc,mbx);
      findBestMach(&ctxc,mby);
      mb=checkMidleVec(&ctxc,mbx,mby);
      /*
      findBestMach(&ctxc,mba1);
      findBestMach(&ctxc,mba2);
      mb12=checkMidleVec(&ctxc,mba1,mba2);

      T_MB *mba3=&ctxc.mb[1+xc*(yc-2)];
      T_MB *mba4=&ctxc.mb[xc-2+xc*(yc-2)];
      findBestMach(&ctxc,mba3);
      findBestMach(&ctxc,mba4);
      mb34=checkMidleVec(&ctxc,mba3,mba4);

      mb13=checkMidleVec(&ctxc,mba1,mba3);
      mb24=checkMidleVec(&ctxc,mba4,mba2);

      mbx=checkMidleVec(&ctxc,mb12,mb13);
      mby=checkMidleVec(&ctxc,mb34,mb24);

      mb=checkMidleVec(&ctxc,mbx,mby);
*/
      //T_MB *mb3=mb;
      
      for(i=1;i<xc-1;i+=2)
      {
         mba2=&ctxc.mb[i+xc*(yc-2)];
         checkMidleVec(&ctxc,mb,mba2);
         mba2=&ctxc.mb[i+xc];
         checkMidleVec(&ctxc,mb,mba2);
      }
      for(i=1;i<yc-1;i+=2)
      {
         mba2=&ctxc.mb[xc*i+1];
         checkMidleVec(&ctxc,mb,mba2);
         mba2=&ctxc.mb[xc*i+xc-2];
         checkMidleVec(&ctxc,mb,mba2);
      }
      

   }
   int iSad;
#if 0
   if(1)
   {

   T_MB *gmc;
   T_MB *mb1=&ctxc.mb[i-1+xc*j];
   T_MB *mb2=mb1+xc+3;//&ctxc.mb[i+1+xc*j];
   findBestMach(&ctxc,mb1);
   int iVecotrsChecked=0;
   if(mb1!=mb2)
   {
      findBestMach(&ctxc,mb2,NULL);
      gmc=checkMidleVec(&ctxc,mb1,mb2);
   }
   else gmc=mb1;
   int iKeyFrame=0;

   //if(iSad2>iSad)mb-=4;
//int iSadMaxRecd2=iSadMaxRec>>1;
   if(gmc->cur.iSad<iSadMaxRec)
      iVecotrsChecked=findV(&ctxc,gmc,NULL,0,0);
   else
   {
     // return;
      debugss("first=",gmc->cur.iSad);
      iKeyFrame=1;
   }
   int s1=2;//yc/6;if(s1<2)s1=2;
   int s2=1;//xc/6;if(s2<2)s2=2;
   
   for(j=1;j<yc-1;j+=s2)
   for(i=1+((j>>1)&1);i<xc-2;i+=s1)
   {
      if(i==0 || j==0 || j+s2>=yc || i+s1>=xc)continue;
      mb=&ctxc.mb[i+xc*j];
      if(mb->iRefined)continue;
      if(mb->iRecursiveChecked)continue;
      iSad=findBestMach(&ctxc,mb,NULL);
     
      if(!mb->iRecursiveChecked)
      {
         iSad=findBestMach(&ctxc,mb+1,NULL);
         mb=checkMidleVec(&ctxc,mb+1,mb);
      }
      if(mb->cur.iSad<iSadMaxRec || (iKeyFrame==0 && mb->cur.iSad<iSadMaxRec*2))
      {
          iVecotrsChecked+=findV(&ctxc,mb,NULL,0,0);
          iKeyFrame=0;
      }
      //else if(iKeyFrame==1)iKeyFrame=2;
      else if(iKeyFrame)
      {
         iKeyFrame++;
         if(iKeyFrame>6)
         {
         debugss("key found",mb->cur.iSad);
         ctxc.rel();
         return -1;
         }
      }

   }
   
   }
#endif
#if 1
//debugss("fnca 31",0,0);
   findV2x(&ctxc);
//debugss("fnca 311",0,0);
   
   for(j=0;j<yc;j++)
   for(i=0;i<xc;i++)
   {
      if(i && j  && j+1!=yc && i+1!=xc){
        mb=&ctxc.mb[i+xc*j];
        checkNearsX(&ctxc,mb);
      }
   }
//debugss("fnca 32",0,0);
   for(j=0;j<yc;j++)
   for(i=0;i<xc;i++)
   {
      mb=&ctxc.mb[i+xc*j];
      if(i && j  && j+1!=yc && i+1!=xc){}else
      
      //if(mb->iRefined && mb->cur.iSad)continue;
      checkNearsX(&ctxc,mb);
   }
#endif   
   //debugss("b3=",1);
   mb=&ctxc.mb[0];
   //int *v1=ctx->
//debugss("fnca 33",0,0);
   for(j=0;j<yc;j++)
   for(i=0;i<xc;i++,v1++,v2++,mb++,pSads++)
   {
      checkMidle(&ctxc,mb);
      pSads[0]=mb->cur.iSad;
      v1[0]=mb->cur.x;
      v2[0]=mb->cur.y;
   }
//debugss("me- ok",0,0);
   //debugss("b4=",2);
  // ctxc.rel();

//   debugss("fnca ok",0,0);
return 0;
  // debugss("b5=",3);
   
   //find ! refined
   //updateDimonds ??
   
}
/*

#define op_sad_r iSad+=tabSAD[cur[0]-((ref[0]*w1-ref2[0]*w2+r)>>sh)];
#define op_put_r cur[0]=((ref[0]*w1-ref2[0]*w2+r)>>sh);

#define _TR_FNC(_OP, _M_TYPE) \
int fnc_##_OP(_M_TYPE *cur, _M_TYPE *ref, _M_TYPE *ref2, int iSizeW, int iSizeH, int stride,const  int w1,const  int w2,const  int sh,const  int r){\
   int i,j;\
   int iSad=1;\
   stride-=iSizeW;\
   for(j=0;j<iSizeH;j++){\
      for(j=0;j<iSizeW;j++){\
         _OP;  ref++;cur++;  ref2++;\
      }\
      cur+=stride; ref+=stride;   ref2+=stride;\
   }\
   return iSad;\
}

_TR_FNC(op_sad_r, MOTI_TYPE)
_TR_FNC(op_put_r, unsigned char)
*/