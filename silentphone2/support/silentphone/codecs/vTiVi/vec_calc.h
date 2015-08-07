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

#ifndef _VEC_CALC_H
#define _VEC_CALC_H

#define T_CAN_TEST_V

#include <math.h>
#if defined(__SYMBIAN32__) || defined(ANDROID_NDK) || defined(__APPLE__)
#include <stdlib.h>//abs
#endif

#ifndef max
#define max(A,B) ((A)>(B)?(A):(B))
#define min(A,B) ((A)<(B)?(A):(B))
#endif


typedef struct{
   int *vb1;
   int *vb2;
   int iLenBig;
   int *vs1;
   int *vs2;
   int iLenSmall;
   int *v1;
   int *v2;
   int iLenR;
   int iCalcBig;
   int iVecDiv;
   int *pVecRes;
   int *pRotRes;
   int iCanRotate;
   int iVecSearchFlag;
   int *bufVRefPicId;
  // char bufVRefPicId[32000];
  // int iDiv;
}V_CALC_STR;

#define MOTI_TYPE unsigned char
#define sizeof_MOTI_TYPE 1
//short
//#define SIZE_OF_MT 1
class CTPIC{
   int iMustDelete;
   MOTI_TYPE *pDel;
   int iInitSize;
public:
   CTPIC(){iInitSize=0;iMustDelete=0;pDel=p=NULL;w=h=0;}
   ~CTPIC(){if(iMustDelete && pDel)delete pDel;}

   
   MOTI_TYPE *p;
   int w;
   int h;

   void setNewBuf(CTPIC *img)
   {
      int iWas=iMustDelete;
      iMustDelete=img->iMustDelete;
      img->iMustDelete=iWas;
      w=img->w;
      h=img->h;
      p=img->p;
      if(p)
      {
         pDel=img->pDel;
      //pDel=p-w*2-16;
      }
      else 
      {
         pDel=p;
      }
         iInitSize=img->iInitSize;
   }
   static void swap(CTPIC *p1,CTPIC *p2)
   {
      CTPIC tmp;
      tmp.setNewBuf(p1);
      p1->setNewBuf(p2);
      p2->setNewBuf(&tmp);
   }

   inline void check(int x, int y)
   {
      if(x!=w || y!=h)
      {
         w=x;
         h=y;

         if(pDel){delete pDel;}
         iMustDelete=1;
         iInitSize=w*(h+16)*sizeof(MOTI_TYPE);
         pDel=new MOTI_TYPE [iInitSize];
         p=pDel+(w)*8*sizeof(MOTI_TYPE);
         p=(MOTI_TYPE*)(((size_t)p+15)&(~15));
      }
   }
   inline void clear(int b)
   {
      if(w>0 && h>0)
      {
         memset(pDel,b,iInitSize);
      }
   }
   void copy(void *pNew)
   {
      if(w>0 && h>0){
         memcpy(p,pNew,w*h*sizeof(MOTI_TYPE));
         
         memcpy((MOTI_TYPE*)p-w,pNew,w*sizeof(MOTI_TYPE));
         memcpy((MOTI_TYPE*)p+w*h,(MOTI_TYPE*)pNew+w*(h-1),w*sizeof(MOTI_TYPE));
      }
   }
};
#define T_USE_IMGR 0
typedef struct _VEC_IMG_STRUCT{
   int id;//izmers short
   int iQVal;// 0 key, 1 dif, 2 b
   //int w, h;
   int iRefCreated;
   int iCanUseAsPred;
   int iIsKey;
   unsigned int TS;
#if T_USE_IMGR
   CTImgData imgR;
#endif
   CTImgData img;
   CTPIC poi4;//encoder
   CTPIC poi;//encoder
   CTPIC ref_H_V_VH[5];//encoder
   void init(int idIn=0)
   {
      iIsKey=0;
      id=idIn;
      iQVal=0;
      iRefCreated=0;
   }
   void copy(_VEC_IMG_STRUCT *src)
   {
      int x,y;
      if(!src || !src->img.getBuf())return;
      src->img.getXY(x,y);

      //if(img.getBuf() && src->img.getBuf())
      img.setOutputPictureSize(x,y);
      //memcpy(img.getBuf(),src->img.getBuf(),x*y*3);
      img.copy(&src->img);
#if T_USE_IMGR
      imgR.setOutputPictureSize(x,y);
      imgR.copy(&src->imgR);
#endif
      if(!src->poi.p || !src->ref_H_V_VH[0].p)return;

      if(src->poi.w){
      poi.check(x,y);
      ref_H_V_VH[0].check(x,y);
      ref_H_V_VH[1].check(x,y);
      ref_H_V_VH[2].check(x,y);

      TS=src->TS;
      poi.copy(src->poi.p);
      ref_H_V_VH[0].copy(src->ref_H_V_VH[0].p);
      ref_H_V_VH[1].copy(src->ref_H_V_VH[1].p);
      ref_H_V_VH[2].copy(src->ref_H_V_VH[2].p);
      }
   }

   
}VEC_IMG_STRUCT;

static void swapVecStr(VEC_IMG_STRUCT *p1, VEC_IMG_STRUCT *p2)
{
   int v=p2->iRefCreated;p2->iRefCreated=p1->iRefCreated;p1->iRefCreated=v;
   v=p2->iQVal;p2->iQVal=p1->iQVal;p1->iQVal=v;
   v=p2->id;p2->id=p1->id;p1->id=v;
   v=(int)p2->TS;p2->TS=p1->TS;p1->TS=(unsigned int)v;

   CTImgData::swap(&p1->img,&p2->img);
   CTPIC::swap(&p1->poi,&p2->poi);
   CTPIC::swap(&p1->poi4,&p2->poi4);
   CTPIC::swap(&p1->ref_H_V_VH[0],&p2->ref_H_V_VH[0]);
   CTPIC::swap(&p1->ref_H_V_VH[1],&p2->ref_H_V_VH[1]);
   CTPIC::swap(&p1->ref_H_V_VH[2],&p2->ref_H_V_VH[2]);

}

typedef struct{
   unsigned char *pImg;
   int w, h;
   
   int iCreated;
   CTPIC pi;
   CTPIC pi4;
   
   int refid;
   VEC_IMG_STRUCT *imgOld;
   
   V_CALC_STR vec_calc_struct;
   
}VEC_C_STR;

typedef struct{
   int w, h;
   
   int iMBSize;

   int iCreated;
   //??CTPIC pic_in[16];

   CTPIC pi;
   CTPIC pi4;

   MOTI_TYPE *mt_pi;
   
   int refid;

   int iVectorsSaved;
   unsigned char *pCurImg;

   int *refs;
   VEC_IMG_STRUCT *imgOld;
   VEC_IMG_STRUCT *imgCur;
   //VEC_IMG_STRUCT *imgOldMin[3];//-1 -2 -3
   VEC_IMG_STRUCT *imgOldMin1;
   VEC_IMG_STRUCT *imgOldMin2;
   int iMaxRefs;
   int iSendingBigDifs;
   int iCanRotate;

   int *absTbl;
   int *tmp;
   //void *mbData;
   void *tinaMVEnc;

   //int mb_mode[8000];
   //int mb_rot[8000];
   //int mb_ref[8000];
   int iRefsM2; //%
   int iSkipCnt; //if not tested -1
   
   
}VEC_C_STR2;

typedef struct _T_CUR_VECTOR{
     int x;
     int y;
     int iSad;//if() not calc; if(1) dif==0   ,,,,varbuut vajag apkarteejos iSad[9]
     
     inline void operator +=(struct _T_CUR_VECTOR &v){
        x+=v.x; x>>=1; y+=v.y; y>>=1;
       // iSad+=v.iSad;
     //   iSad/=2;
     }
     inline int operator !=(struct _T_CUR_VECTOR &v){
        return  v.x!=x || v.y!=y;
     }
#if defined(__SYMBIAN32__) || defined(ANDROID_NDK) || defined(__APPLE__)
     inline int operator ==(struct _T_CUR_VECTOR v){
        return v.x==x && v.y==y;
     }
#else
     inline int operator ==(struct _T_CUR_VECTOR &v){
        return v.x==x && v.y==y;
     }
#endif
     inline int operator < (struct _T_CUR_VECTOR &v){
        return iSad < v.iSad;
     }
     inline int operator > (struct _T_CUR_VECTOR &v){
        return iSad > v.iSad;
     }
     inline int operator - (struct _T_CUR_VECTOR &v){
        return abs(x-v.x)+abs(y-v.y);
     }
}T_CUR_VECTOR;

typedef struct{
   //enum {eT,eL,eR,eB,eLast};
   //enum {eLU,eRB,eLURBLAST};
   //vect mv4pred[eLast][eLURBLAST];
   
   
   T_CUR_VECTOR v;
   T_CUR_VECTOR gF;
   T_CUR_VECTOR mv4[16];
   //0145
   //2367
   //8923
   //0145
   int iMode;
   int r1,r2;
   int iSadWOgF;
   int iSadMV2;
   int iSadMV4;
   int iRotSad;
   inline int hasRot(){return r1||r2;}

   int posx, posy;
   int mbx, mby;
   int ofs;
   int refId;
   
   MOTI_TYPE *pCur;
   MOTI_TYPE *pOld;
   MOTI_TYPE *pRef[3];
   
   unsigned char *pImgCur;
   unsigned char *pImgOld;
   
   unsigned int checkBits[40];//32x32== +-16
   
   
}T_MB2;

class T_MB2_CTX {
public:
   int xc,yc;
   int w,h;
   T_MB2 *mb;
   T_MB2_CTX(){xc=0;yc=0;mb=NULL;}
   void init( int  w, int h, int vs){
      if(w/vs==xc && h/vs==yc) return;
      xc=w/vs;yc=h/vs;
      if(mb)delete mb;
      mb=new T_MB2[(xc+1)*(yc+1)];
      memset(mb,0,sizeof(T_MB2)*(xc+1)*(yc+1));
   }
};
#define PREV_MB 1
#define UP_MB 2
#define NEXT_MB 3
#define UPP_MB 4
#define PREV_BI_MB 5
#define UP_BI_MB 6

#define NEXT_BI_MB 7
#define UPP_BI_MB 8

#define CUR_MB 0


typedef struct _MB_4{
   enum{eM2,eM1,eNext,eLast};
   enum{eRF_m2=1,eRF_m1=2, eRF_next=4};
   //bi,ref,refN,refM2,ref-r,refN-r,refM2-r,ref-m,refN-m,refM2-m
   enum{eBi,eRefM2,eRef,eRefN,eRefM2_m,eRef_m,eRefN_m,eRefM2_r,eRef_r,eRefN_r,eRMR_last};
   int iBlockMode;
   int refId;
   int iMVMode;
   int iIsBi;
   //short r1,r2;
   union {
      short s[2];
      int i;

   }r;
   int rot_a;
   
   int iDev;
   int iPosibleMinSad;


   int iBestSadUV;
   int iSadBiGain;
   int iSadRotGain;
   int iSadModeGain;
   int iTryMode;///dbgs
   int iSadWOCost[eLast];

   int iBlockDC;

   T_CUR_VECTOR vrefs[eLast];
   T_CUR_VECTOR mv2[4];
   T_CUR_VECTOR mv_eM1_gr;
   T_CUR_VECTOR mv4[16];
   T_CUR_VECTOR mv_eM1_gr_enc_only;
   T_CUR_VECTOR mv_eM1_dec_enc;
   T_CUR_VECTOR vecHadaTest[eLast];

   struct _MB_4 *mb4L;
   short iHadaFlagNotOk[eLast];//enc mode =1
   short iHada[eLast];
   short iUpdateed;
   short refIdPrev;

   char iExtMVSearchXF;
   char iExtMVSearch;
   char iRefsValid;
   char iIsSameBlockBiCheck;
   short xc,yc;
   short i,j;
   inline int getRefID(){return getID(refId);}
   static inline int getID(int id){
      static const int tab_mb4_r[]={eM2,eM1,eNext,eNext,eNext,eNext,eNext,eNext,eNext,eNext,eNext,eNext,eNext,eNext};
      //if(id>0)return eNext;return id==-2?eM2:eM1;
      return tab_mb4_r[id+2];
   }
}MB_4;
class CMB4{
public:
   int iPrevMBSize;
   int xc,yc;
   CMB4(){iPrevMBSize=xc=yc=0;mb=NULL;}
   ~CMB4(){if(mb)delete []mb;}
   void init(int w, int h, int mbsize)
   {
      if(w/mbsize==xc && h/mbsize==yc && iPrevMBSize==mbsize) return;
      iPrevMBSize=mbsize;
      xc=w/mbsize;yc=h/mbsize;
      if(mb)delete []mb;
      mb=new MB_4[(xc+1)*(yc+1)];
      memset(mb,0,sizeof(MB_4)*(xc+1)*(yc+1));
      int i,j;
      for(j=0;j<yc;j++)for(i=0;i<xc;i++)
      {
        MB_4 *m=&mb[j*xc+i];
        memset(m,0,sizeof(MB_4));
        m->i=i;m->j=j;
        m->xc=xc;m->yc=yc;
        m->iIsBi=0;
        m->mv_eM1_dec_enc.x=0;
        m->mv_eM1_dec_enc.y=0;
        m->mv_eM1_dec_enc.iSad=0;
      }
   }
   MB_4 *mb;

};


typedef struct{
   unsigned char *pPic;
}REFPIC;

CMB4 *getPtr_CMB4(void *ctx);
void *initMotion();
void relMotion(void *p);
int encodeTinaMotion(void *pCtx, int iMBSize2, int iCur, int iMaxGroup, int w, int h, unsigned char *pBin, int &iLen);
int decodeTinaMotion(void *pCtx, int &iMBSize2, int iCur, int iMaxGroup, int w, int h, unsigned char *pBin, int &iLen, int iMaxBytesIn);


int calcPred1(VEC_C_STR2 *ctx, int *v1, int *v2);
void toBW(unsigned char *p, MOTI_TYPE *dst, int w, int h);
int detectMotionM(VEC_C_STR2 *ctx, int *v1, int *v2, int *pRefid, int *res);
int detectMotion(VEC_C_STR2 *ctx, int iFirst, int *v1, int *v2, int *pRefid, int *res);
int detectMotionX(VEC_C_STR2 *ctx, int iFirst, int *v1, int *v2, int *pRefid, int *res);

int detectMotion2(VEC_C_STR2 *ctx, int *v1, int *v2, int *vi1, int *vi2, int *pRefid, int *res);
int moveVectorsRefD2(int iCnt, unsigned char *p, REFPIC *pic, int *v1, int *v2, int *ptrRefId, int w, int h);
int moveVectorsRefMB4(int iMBSize2, MB_4 *mb4, unsigned char *p, REFPIC *pic, int w, int h, int iIsB, int iDec);

int moveVectorsRef(int iCnt, unsigned char *p, REFPIC *pic, int *v1, int *v2, int *ptrRefId, int w, int h);
int doVecotrsRefNot0(void *ctx,int iCnt, int iMaxRes, unsigned char *pODec, unsigned char *pTmp,int w,int  h, int *v1, int *v2);
int doVecotrs(void *ctx,int iCnt, unsigned char *p, int iRefId,
              //unsigned char *pO, unsigned char *pODec, unsigned char *pTmp,
              int w,int  h,int *v1, int *v2);

#endif
