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

#define T_CAN_TEST_V

#if 0
//ndef T_CAN_TEST_V
#include "mv_old.inl"
//#include "mv.xcpp_23102010"
#else
#include <stdio.h>
//#include <string.h>
#include <string.h>
#include <stdlib.h>

//#undef T_CAN_TEST_V

#include "../CTImgData.h"
#include "vec_calc.h"
#include "t_vlc.h"

extern "C"{
void rci();
void addRacBits(unsigned char *r, int iPredID, int iFrom,int b);
void addRacVal(int v);
void addRacValP( int v, int pos);
int rce();
}


#define mgabs(_A) abs(_A)
//int mgabs(int i){return i>=0?i:-i;}

int isValidVector(int x, int y ,int pos1, int pos2,int iMBSize, int w ,int h);
static int isValidVector16_MB_4(int x, int y, MB_4 *m){
   return isValidVector(x,y,m->i*16,m->j*16,16,m->xc*16,m->yc*16);
}
void getV_cntX(int *rx,int *x, int cnt);
#if  defined(__SYMBIAN32__) || defined(_WIN32_WCE)
//int getMVBitLen(int x, int y){return 1;}
#else
//int getMVBitLen(int x, int y);
#endif
static int iDecErrs=0;
static int iDecErrF=0;

static const int modeLen[]={2,2,10,10,14,14,4,4,4,4,4,4,4};

inline int isValidVectorLim2(int x, int y ,int pos1, int pos2,int iMBSize, int w ,int h, int ml)
{
   x>>=1;y>>=1;
   w<<=1;
   if(pos1+x+iMBSize>w){iDecErrF|=1;;return 0;}
   if(pos2+y+iMBSize>h+ml){iDecErrF|=2;return 0;}
   if(pos1+x<-w){iDecErrF|=4;return 0;}
   if(pos2+y<-ml){iDecErrF|=8;return 0;}
   return 1;
}

static inline int isValidVector16_MB_4_L(int x, int y, MB_4 *m, int ml){
   return isValidVectorLim2(x,y,m->i*16,m->j*16,16,m->xc*16,m->yc*16,ml);
}


void debugss(char *c, int a,int b);

const static int bms[]={1,0,1,1,0,0,0,1,1,1,};

static const int pred_mv_2x2[4][10]={
   {10,PREV_MB,5,7,UP_MB,10,11,UP_MB,14,15},
   {10,CUR_MB,1,3,UP_MB,14,15,NEXT_MB,10,11},
   {10,PREV_MB,13,15,CUR_MB,2,3,CUR_MB,6,7},
   {10,CUR_MB,11,9,CUR_MB,6,7,CUR_MB,3,3}
};

// 0  1  4  5 
// 2  3  6  7 
// 8  9 12 13
//10 11 14 15
static const int pred_mv_4x4[16][7]={
   {7,PREV_MB ,5,  UPP_MB,15, UP_MB,10}, //0
   {7,CUR_MB  ,0,  UP_MB,11, UP_MB, 14}, //1
   {7,PREV_MB ,7,  CUR_MB,0, CUR_MB, 1}, //2
   {7,CUR_MB  ,2,  CUR_MB,1, CUR_MB, 0}, //3

   {7,CUR_MB  ,1,  UP_MB,14, UP_MB, 15}, //4
   {7,CUR_MB  ,4,  UP_MB,15,NEXT_MB,10}, //5
   {7,CUR_MB  ,3,  CUR_MB,4, CUR_MB, 5}, //6
   {7,CUR_MB  ,6,  CUR_MB,5, CUR_MB, 4}, //7

   {7,PREV_MB,13,  CUR_MB,2, CUR_MB, 3}, //8
   {7,CUR_MB  ,8,  CUR_MB,3, CUR_MB, 6}, //9
   {7,PREV_MB,15,  CUR_MB,8, CUR_MB, 9}, //10
   {7,CUR_MB ,10,  CUR_MB,9, CUR_MB, 8}, //11

   {7,CUR_MB  ,9,  CUR_MB,6, CUR_MB, 7}, //12
   {7,CUR_MB ,12,  CUR_MB,7, CUR_MB, 6}, //13
   {7,CUR_MB ,11, CUR_MB,12, CUR_MB,13}, //14
   {7,CUR_MB ,14,  CUR_MB,13,  CUR_MB,12} //15
   //{7,CUR_MB ,14, CUR_MB,12, CUR_MB,13} //15
};
static const int tabMode[]={2,4,8,16};

//const int tabRotMode[]={32,64,128,256};
inline void loadNearMB(MB_4 *v, MB_4 *mbx[]){
   const int i=v->i;
   const int j=v->j;
   const int xc=v->xc;
   mbx[CUR_MB]=v;

   mbx[8]=mbx[7]=mbx[6]=mbx[5]=mbx[4]=mbx[3]=mbx[2]=mbx[1]=0;

   if(i &&  v[-1].refId==v->refId)mbx[PREV_MB]=v-1;
   if(j &&  v[-xc].refId==v->refId)mbx[UP_MB]=v-xc;

   if(i &&  v[-1].iIsBi)mbx[PREV_BI_MB]=v-1;
   if(j &&  v[-xc].iIsBi)mbx[UP_BI_MB]=v-xc;

   //-----------ERR---------- TODO --wrong---i+i < xc  ???? i+1<xc
   //TODO inc vers
   if(i+i<xc && j &&  v[1-xc].iIsBi)mbx[NEXT_BI_MB]=v+1-xc;
//--??   if(i+1<xc && j &&  v[1-xc].iIsBi)mbx[NEXT_BI_MB]=v+1-xc;
   if(j && i &&  v[-xc-1].iIsBi)mbx[UPP_BI_MB]=v-1-xc;

   if(j && i+1<xc &&  v[1-xc].refId==v->refId)mbx[NEXT_MB]=v+1-xc;
   if(j && i &&  v[-1-xc].refId==v->refId)mbx[UPP_MB]=v-1-xc;
}

/*
int rotTabD8[]={//38
   2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,
   4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
   6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
   1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,
   8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,               //5
   16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,
   10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,
   -15,-13,-11,-9,-7,-5,-3, -1, 1,3,5,7,9,11,13,15,
   -7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,
   -23,-20,-17,-14,-11, -8, -5,-2,1, 4, 7,10,13,16,19,22, //10
   -30,-26,-22,-18,-14,-10, -6,-2,2, 6,10,14,18,22,26,30,
   -37,-32,-26,-21,-17,-12, -7,-3,2, 7,12,17,21,26,32,37,
   -45,-39,-33,-27,-21,-15, -9,-3,3, 9,15,21,27,33,39,45,
   -52,-45,-38,-31,-24,-17,-10,-3,4,11,18,25,32,39,46,53,
   -58,-50,-42,-34,-28,-20,-12,-4,4,12,20,28,34,42,50,58,//15
   -75,-65,-55,-45,-35,-25,-15,-5,5,15,25,35,45,55,65,75,
   -81,-72,-61,-50,-42,-30,-18,-6,6,18,30,42,50,61,72,81,
   -98,-85,-72,-59,-46,-33,-20,-7,6,19,32,45,58,71,84,96,
   -105,-91,-77,-63,-49,-35,-21,-7,7,21,35,49,63,77,91,105,
                 0};
*/


const int tab_rotP[]={0,2,0,9,  0,5, //,0,7,
                      1,5, 1,9, 
                      2,8,2,10, 2,5,
                      3,8, 3,11, 4,10,4,12,  3,13, 
                      5,12 ,6,13, 5,10,6,11,  4,15,  5,17,
                      7,14, 7,15,8,15,8,16 ,9,17, 10,17,11,17,
                      7,12, 8, 13, 9, 14,
                      12,17,
                      -1,-1};
inline int getRPred(int d, int *r, int &cnt, int iNeg){
   int i;
   if(d<0){d=-d;}
   for(i=0;;i+=2){
      if(tab_rotP[i]==-1)break;
      if(tab_rotP[i]==d){
         //r[cnt]=iNeg?-tab_rotP[i+1]:tab_rotP[i+1];cnt++;
         
         if(!iNeg){
            if(d>=-2){r[cnt]=-tab_rotP[i+1];cnt++;}
            if(d<=2){r[cnt]=tab_rotP[i+1];cnt++;}
         }
         else{
            if(d<=2){r[cnt]=-tab_rotP[i+1];cnt++;}
            if(d>=-2){r[cnt]=tab_rotP[i+1];cnt++;}
         }
         
       //  r[cnt]=-tab_rotP[i+1];cnt++;
         //r[cnt]=tab_rotP[i+1];cnt++;
      }
   }
   return cnt;
}

int setRotPredMB(MB_4 *v, int &cntR1,int &cntR2, int *r1, int *r2, int iMVSize){
   const int i=v->i;
   const int j=v->j;
   const int xc=v->xc;
   const int yc=v->yc;

   

   int ref=v->getID(v->refId);
   int cx=v->vrefs[ref].x;
   int cy=v->vrefs[ref].y;
   int d;
   int sad=v->vrefs[ref].iSad;//)>>1;
   if(iMVSize>16){sad*=3;sad>>=2;}
   int iIsGoodMB=((sad<1500 || sad*7<v->iPosibleMinSad*4) && (sad*3+250<v->iPosibleMinSad*4 || sad*15<v->iDev || (sad<1000 && sad+100<v->iPosibleMinSad))) && sad*2<v->iPosibleMinSad*3;
   int iIsVeryGood=sad*3<v->iPosibleMinSad*2 && sad<2000;
  // const int g=3;
   
   //int iPredG=((((v->iDev+100)/(iMVSize*2))*8+4)/(sad+100))>>3;

   
   
   if(sad*3<v->iPosibleMinSad || (iIsVeryGood) || v->iHada[ref]==0){
      r1[cntR1]=1;r2[cntR2]=1;cntR1++;cntR2++;
      r1[cntR1]=-1;r2[cntR2]=-1;cntR1++;cntR2++;
      r1[cntR1]=3;r2[cntR2]=3;cntR1++;cntR2++;
      r1[cntR1]=-3;r2[cntR2]=-3;cntR1++;cntR2++;
      r1[cntR1]=5;r2[cntR2]=5;cntR1++;cntR2++;
      r1[cntR1]=-5;r2[cntR2]=-5;cntR1++;cntR2++;
      r1[cntR1]=9;r2[cntR2]=9;cntR1++;cntR2++;
      r1[cntR1]=-9;r2[cntR2]=-9;cntR1++;cntR2++;
      return cntR2+cntR1;
   }
   /*
   else if(v->iBlockDC<(iMVSize*iMVSize)){
     // if(iPredG<5){
      if(v->iPosibleMinSad*2<v->iDev){
         r1[cntR1]=2;r2[cntR2]=2;cntR1++;cntR2++;
         r1[cntR1]=-2;r2[cntR2]=-2;cntR1++;cntR2++;
      }
      //}
   }
   */
   //const int qMult=iMVSize>16?5:3;

#define _CMP_MB ((v->iDev<1000 && mgabs(d)>8) || v->iDev>=1000) && ((v->iExtMVSearch && sad*2>v->iPosibleMinSad) || (iIsGoodMB && mgabs(d)<3) || (!iIsGoodMB && d))


#define V_IS_OK(_ID) (v[_ID].vrefs[ref].iSad*2<v[_ID].iPosibleMinSad*3 || v[_ID].vrefs[ref].iSad*3<v[_ID].iDev)
#define D0_IF_B_X(_ID,_X) {if(v[_ID].r.s[0]>_X){r1[cntR1]=v[_ID].r.s[0]-2; cntR1++;}else if(v[_ID].r.s[0]<-_X){r1[cntR1]=v[_ID].r.s[0]+2; cntR1++;} \
                           if(v[_ID].r.s[1]>_X){r2[cntR2]=v[_ID].r.s[1]-2; cntR2++;}else if(v[_ID].r.s[1]<-_X){r2[cntR2]=v[_ID].r.s[1]+2; cntR2++;}}

   if(j && V_IS_OK(-xc))
   {
      if(v->refId==v[-xc].refId && v[-xc].iMVMode){
         if(mgabs(cy-((v[-xc].mv4[10].y +v[-xc].mv4[11].y+v[-xc].mv4[14].y+v[-xc].mv4[15].y+2)>>2))<4){
            d=cx-((v[-xc].mv4[10].x +v[-xc].mv4[11].x+v[-xc].mv4[14].x+v[-xc].mv4[15].x+2)>>2);
            if(_CMP_MB)getRPred(d,r1,cntR1,1);
         }
      }
      else if(mgabs(cy-v[-xc].vrefs[ref].y)<4){
         d=cx-v[-xc].vrefs[ref].x;
         if(_CMP_MB)getRPred(d,r1,cntR1,1);
      }
      else if(v->refId==-1 && v[-xc].refId==-2){
         D0_IF_B_X(-xc,10);
      }
      else if((v->refId==-1 && v[-xc].refId>=0) || (v->refId>=0 && v[-xc].refId==-1)){
         if(mgabs(v[-xc].r.s[0])>7){r1[cntR1]=-v[-xc].r.s[0]; cntR1++;}
         if(mgabs(v[-xc].r.s[1])>7){r2[cntR2]=-v[-xc].r.s[1]; cntR2++;}
      }
      
   }
   if(j+1<yc && V_IS_OK(xc) && mgabs(cy-v[xc].vrefs[ref].y)<4)
   {
      d=cx-v[xc].vrefs[ref].x;
      if(_CMP_MB)getRPred(d,r1,cntR1,0);
   }
   if(i && V_IS_OK(-1)){
      if(v->refId==v[-1].refId && v[-1].iMVMode){
         if(mgabs(cx-((v[-1].mv4[5].x +v[-1].mv4[7].x+v[-1].mv4[13].x+v[-1].mv4[15].x+2)>>2))<4){
            d=cx-((v[-1].mv4[5].y +v[-1].mv4[7].y+v[-1].mv4[13].y+v[-1].mv4[15].y+2)>>2);
            if(_CMP_MB)getRPred(d,r2,cntR2,1);
         }
      }
      else if(mgabs(cx-v[-1].vrefs[ref].x)<4){
         d=cy-v[-1].vrefs[ref].y;
         if(_CMP_MB)getRPred(d,r2,cntR2,1);
      }
      else if(v->refId==-1 && v[-1].refId==-2){
         D0_IF_B_X(-1,10);
      }
      else if((v->refId==-1 && v[-1].refId>=0) || (v->refId>=0 && v[-1].refId==-1) ){
         if(mgabs(v[-1].r.s[0])>7){r1[cntR1]=-v[-1].r.s[0]; cntR1++;}
         if(mgabs(v[-1].r.s[1])>7){r2[cntR2]=-v[-1].r.s[1]; cntR2++;}
      }
   }
   if(i+1<xc && V_IS_OK(1) && mgabs(cx-v[1].vrefs[ref].x)<4)
   {
      d=cy-v[1].vrefs[ref].y;
      if(_CMP_MB)getRPred(d,r2,cntR2,0);
   }
   if(!iIsGoodMB){
      r1[cntR1]=15;r2[cntR2]=15;cntR1++;cntR2++;
      r1[cntR1]=-15;r2[cntR2]=-15;cntR1++;cntR2++;
   }
   else if(sad<v->iPosibleMinSad){
      if(v->iPosibleMinSad*3<v->iDev*2){
         r1[cntR1]=1;r2[cntR2]=1;cntR1++;cntR2++;
         r1[cntR1]=-1;r2[cntR2]=-1;cntR1++;cntR2++;
      }
      if(v->iPosibleMinSad*5<v->iDev){
         r1[cntR1]=0;r2[cntR2]=0;cntR1++;cntR2++;
         r1[cntR1]=-0;r2[cntR2]=-0;cntR1++;cntR2++;
      }
   }
   if(v->iExtMVSearch && sad>v->iPosibleMinSad){
      r1[cntR1]=7;r2[cntR2]=7;cntR1++;cntR2++;
      r1[cntR1]=-7;r2[cntR2]=-7;cntR1++;cntR2++;
   }
   if(sad*3>v->iPosibleMinSad*4 &&  sad<v->iPosibleMinSad*6){
      r1[cntR1]=11;r2[cntR2]=11;cntR1++;cntR2++;
      r1[cntR1]=-11;r2[cntR2]=-11;cntR1++;cntR2++;
   }
   return cntR2+cntR1;
}

int getPredRot(MB_4 *v, MB_4 *mbx[], int &r1, int &r2){
   r1=0;
   r2=0;

   int x[]={0,0,0,0};
   int y[]={0,0,0,0};
   int rid=v->getID(v->refId);

   int ac1=0;
   int ac2=0;

   if(mbx[UP_MB] &&  mbx[UP_MB]->iMVMode==0 && mbx[UP_MB]->r.s[0] && mgabs(mbx[UP_MB]->r.s[0])<10
      && mbx[UP_MB]->vrefs[rid].x==v->vrefs[rid].x
      && mgabs(mbx[UP_MB]->vrefs[rid].y-v->vrefs[rid].y)){
      r1=mbx[UP_MB]->r.s[0];
   }
   if(mbx[PREV_MB] &&  mbx[PREV_MB]->iMVMode==0 && mbx[PREV_MB]->r.s[1] && mgabs(mbx[PREV_MB]->r.s[1])<10
      && mbx[PREV_MB]->vrefs[rid].y==v->vrefs[rid].y
      && mgabs(mbx[PREV_MB]->vrefs[rid].x-v->vrefs[rid].x)<2){
      r2=mbx[PREV_MB]->r.s[1];
   }
  
   for(int az=1;az<5;az++){
      //mbx[az]->refId=v->refId , nevajag jo mbx ir visi ref vienaadi
      if(mbx[az] && mbx[az]->iMVMode==0){
         if(!r1 && mgabs(mbx[az]->r.s[0])>9){x[ac1]=mbx[az]->r.s[0];ac1++;}
         if(!r2 && mgabs(mbx[az]->r.s[1])>9){y[ac2]=mbx[az]->r.s[1];ac2++;}
      }
   }
   if(!r1 && ac1)getV_cntX(&r1,&x[0],ac1);
   if(!r2 && ac2)getV_cntX(&r2,&y[0],ac2);
   if(r1>17 || r1<-17)
      r1=0;
   if(r2>17 || r2<-17)
      r2=0;
   
   return r1|r2;
}
/*
int getPredMV_4x4(int id, T_CUR_VECTOR *r, MB_4 *mb[]){
   int x[4]={0,0,0,0};
   int y[4]={0,0,0,0};
   
   int cnt=0;
   MB_4 *m;
   int i;
   int to=pred_mv_4x4[id][0];
   for(i=1;i<to;i+=2){
      m=mb[pred_mv_4x4[id][i]];
      if(m)// && mb[0].refId==m->refId)
      {
         x[cnt]=m->mv4[pred_mv_4x4[id][i+1]].x;
         y[cnt]=m->mv4[pred_mv_4x4[id][i+1]].y;
         cnt++;
      }
   }
   //void getV_cntX(int *rx,int *x, int cnt)
   getV_cntX(&r->x,&x[0],cnt);
   getV_cntX(&r->y,&y[0],cnt);
   //getV_cnt(&r->x,&r->y,&x[0],&y[0],cnt);
   return cnt;
}
*/

int getPredMV_4x4_VP(int id,int *x,int *y, MB_4 *mb[]){
   
   int cnt=0;
   MB_4 *m;
   int i;
   int to=pred_mv_4x4[id][0];
   for(i=1;i<to;i+=2){
      m=mb[pred_mv_4x4[id][i]];
      if(m)// && mb[0].refId==m->refId)
      {
         x[cnt]=m->mv4[pred_mv_4x4[id][i+1]].x;
         y[cnt]=m->mv4[pred_mv_4x4[id][i+1]].y;
         cnt++;
      }
   }
   //void getV_cntX(int *rx,int *x, int cnt)
   //getV_cnt(&r->x,&r->y,&x[0],&y[0],cnt);
   return cnt;
}
static inline int getPredMV_4x4S(const int id, T_CUR_VECTOR *r, MB_4 *mb[], int *x, int *y, int iIsB){

   int cnt=0;
   MB_4 *m;

  // int iMark=0;
   int rid=MB_4::getID(mb[0]->refId);
   int iCanUseBi=rid!=0 || !iIsB;
   int i;
   int to=pred_mv_4x4[id][0];
   for(i=1;i<to;i+=2){
      int iV_ID=pred_mv_4x4[id][i];
      m=mb[iV_ID];
      if(m)// && mb[0].refId==m->refId)
      {
         x[cnt]=m->mv4[pred_mv_4x4[id][i+1]].x;
         y[cnt]=m->mv4[pred_mv_4x4[id][i+1]].y;
         cnt++;
         if(cnt==2)break;
      }
      else if(iCanUseBi){
         if(iV_ID==PREV_MB){
            if(mb[PREV_BI_MB]){
               x[cnt]=mb[PREV_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[PREV_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
         else if(iV_ID==UP_MB){
            if(mb[UP_BI_MB]){
               x[cnt]=mb[UP_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[UP_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
         else if(iV_ID==NEXT_MB){
            if(mb[NEXT_BI_MB]){
               x[cnt]=mb[NEXT_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[NEXT_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
      }
    //  else if(i==1){iMark=1;}
   }
   return cnt;
}
int getPredMV_4x4S_pred(const int id, T_CUR_VECTOR *r, MB_4 *mb[],  int iIsB){
   int x[4]={0,0,0,0};
   int y[4]={0,0,0,0};
   int c=getPredMV_4x4S(id,r,mb,&x[0],&y[0],iIsB);
   if((x[0]==x[1] && y[0]==y[1]) || c==1){
      r->x=x[0];
      r->y=y[0];
      return 1;
   }

   return 0;
}
int getPredMV_4x4S_predBI(const int id, int *x, int *y, MB_4 *mb[],  int iIsB){
   T_CUR_VECTOR r;
   int c=getPredMV_4x4S(id,&r,mb,&x[0],&y[0],iIsB);
   return c;
}


int getPredMV_2x2S(const int id, T_CUR_VECTOR *r, MB_4 *mb[], int *x, int *y, int iIsB){
   int cnt=0;
   MB_4 *m;

    
   int i;
   //int iMark=0;
   int rid=MB_4::getID(mb[0]->refId);
   int iCanUseBi=rid!=0 || !iIsB;
   int to=pred_mv_2x2[id][0];
   for(i=1;i<to;i+=3){
      int iV_ID=pred_mv_2x2[id][i];
      m=mb[iV_ID];
      if(m)// && mb[0].refId==m->refId)
      {
//         x[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].x+m->mv4[pred_mv_2x2[id][i+2]].x)/2;
  //       y[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].y+m->mv4[pred_mv_2x2[id][i+2]].y)/2;
         x[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].x+m->mv4[pred_mv_2x2[id][i+2]].x)/2;
         y[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].y+m->mv4[pred_mv_2x2[id][i+2]].y)/2;
         cnt++;
         if(cnt==2)break;
      }
      else if(iCanUseBi){
         if(iV_ID==PREV_MB){
            if(mb[PREV_BI_MB]){
               x[cnt]=mb[PREV_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[PREV_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
         else if(iV_ID==UP_MB){
            if(mb[UP_BI_MB]){
               x[cnt]=mb[UP_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[UP_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
         else if(iV_ID==NEXT_MB){
            if(mb[NEXT_BI_MB]){
               x[cnt]=mb[NEXT_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[NEXT_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
      }
      //else if(i==1){iMark=1;}
   }

   if(id==0 && cnt!=2)
   {
      m=mb[UPP_MB];
      if(m)
      {
         x[cnt]=m->mv4[15].x;
         y[cnt]=m->mv4[15].y;
         cnt++;
      }
   }
   return cnt;
}

int getPredMV_2x2BI(const int id,  MB_4 *mb[], int *x, int *y){

   //MB_4 *m;
   int iIsB=mb[0]->iRefsValid&mb[0]->eRF_next;
   T_CUR_VECTOR r;

   return getPredMV_2x2S(id, &r, mb, x, y, iIsB);
   /*

    
   int i;
   //int iMark=0;
   int iIsB=mb[0]->iRefsValid&mb[0]->eRF_next;

   int rid=MB_4::getID(mb[0]->refId);
   int iCanUseBi=rid!=0 || !iIsB;
   int to=pred_mv_2x2[id][0];
   for(i=1;i<to;i+=3){
      int iV_ID=pred_mv_2x2[id][i];
      m=mb[iV_ID];
      if(m)// && mb[0].refId==m->refId)
      {
//         x[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].x+m->mv4[pred_mv_2x2[id][i+2]].x)/2;
  //       y[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].y+m->mv4[pred_mv_2x2[id][i+2]].y)/2;
         x[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].x+m->mv4[pred_mv_2x2[id][i+2]].x)/2;
         y[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].y+m->mv4[pred_mv_2x2[id][i+2]].y)/2;
         cnt++;
         if(cnt==2)break;
      }
      else if(iCanUseBi){
         if(iV_ID==PREV_MB){
            if(mb[PREV_BI_MB]){
               x[cnt]=mb[PREV_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[PREV_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
         else if(iV_ID==UP_MB){
            if(mb[UP_BI_MB]){
               x[cnt]=mb[UP_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[UP_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
         else if(iV_ID==NEXT_MB){
            if(mb[NEXT_BI_MB]){
               x[cnt]=mb[NEXT_BI_MB]->vrefs[rid].x;
               y[cnt]=mb[NEXT_BI_MB]->vrefs[rid].y;
               cnt++;
            }
         }
      }
      //else if(i==1){iMark=1;}
   }

   if(id==0 && cnt!=2)
   {
      m=mb[UPP_MB];
      if(m)
      {
         x[cnt]=m->mv4[15].x;
         y[cnt]=m->mv4[15].y;
         cnt++;
      }
   }
   return cnt;
   */
}
void getPred4xN(int *x, int *y, MB_4 *m, int a,int b,int c,int d){
   x[0]=(m->mv4[a].x + m->mv4[b].x +m->mv4[c].x+m->mv4[d].x)/4;
   y[0]=(m->mv4[a].y + m->mv4[b].y +m->mv4[c].y+m->mv4[d].y)/4;
}

#ifdef T_CAN_TEST_V

static inline int getPredMV_1x1SN(const int id, MB_4 *mb[], int *x, int *y, int iIsB){
     int cnt=0;
   MB_4 *m;
   int rid=MB_4::getID(mb[0]->refId);
   int brid=rid==2?1:2;
   
   if(iIsB && !mb[0]->iIsBi && rid && mb[0]->mv_eM1_dec_enc.iSad==2){
      x[0]=mb[0]->mv_eM1_dec_enc.x/2;
      y[0]=mb[0]->mv_eM1_dec_enc.y/2;
      if(rid==2){
         x[0]=-x[0];y[0]=-y[0];
      }
      return 1;
//      cnt++;
 //     return 1;
   }

   int iCanUseBi=//mb[0]->iIsBi && 
               (!iIsB || rid!=0);

   /*   
   if(mb[NEXT_MB])
   {
      x[cnt]=mb[NEXT_MB]->mv4[10].x;
      y[cnt]=mb[NEXT_MB]->mv4[10].y;
      cnt++;
   }
   else if(iCanUseBi && mb[NEXT_BI_MB]){
      x[0]=mb[NEXT_BI_MB]->vrefs[rid].x;
      y[0]=mb[NEXT_BI_MB]->vrefs[rid].y;
      cnt++;

   }
   */
   const int iIsBBi=iIsB && mb[0]->iIsBi;

   if(mb[UPP_MB]){
      x[cnt]=mb[UPP_MB]->mv4[15].x;
      y[cnt]=mb[UPP_MB]->mv4[15].y;
      cnt++;
   }
   else if(iCanUseBi && mb[UPP_BI_MB]){
      x[cnt]=mb[UPP_BI_MB]->vrefs[rid].x;
      y[cnt]=mb[UPP_BI_MB]->vrefs[rid].y;
      cnt++;

   }
   
  // int cntS=cnt;cnt=0;

   int iPredC=0;
   int mlpri[4];
   int mlm[4];
/*
   if(iCanUseBi && mb[PREV_BI_MB]){
      mlpri[iPredC]=2;
      mlm=PREV_BI_MB; iPredC++;
   }
   if(iCanUseBi && mb[UP_BI_MB]){
      mlpri[iPredC]=2;
      mlm=UP_BI_MB; iPredC++;
   }
   */
   
#define T_M_MB_PRED(_M,_BI) \
   if(mb[_M] && (mb[_M]->iMVMode || mb[_M]->r.i)){\
      mlpri[iPredC]=4;\
      mlm[iPredC]=_M; iPredC++;\
   }\
   else if(iCanUseBi && mb[_BI]){\
      mlpri[iPredC]=iIsBBi?5:3;\
      mlm[iPredC]=_BI; iPredC++;\
   }\
   else if(mb[_M]){\
      mlpri[iPredC]=iIsBBi?2:3;\
      mlm[iPredC]=_M; iPredC++;\
   }
   // && mb[_M]->iBlockDC!=8

   T_M_MB_PRED(PREV_MB,PREV_BI_MB);
   T_M_MB_PRED(UP_MB,UP_BI_MB);
   T_M_MB_PRED(NEXT_MB,NEXT_BI_MB);
   //T_M_MB_PRED(UPP_MB,UPP_BI_MB);
/*
   if(mb[UP_MB] && (mb[UP_MB]->iMVMode || mb[UP_MB]->r.i)){
      mlpri[iPredC]=4;
      mlm[iPredC]=UP_MB; iPredC++;
   }
   else if(iCanUseBi && mb[UP_BI_MB]){
      mlpri[iPredC]=3;
      mlm[iPredC]=UP_BI_MB; iPredC++;
   }
   else if(mb[UP_MB]){
      mlpri[iPredC]=2;
      mlm[iPredC]=UP_MB; iPredC++;
   }

   if(mb[NEXT_MB] && (mb[NEXT_MB]->iMVMode || mb[NEXT_MB]->r.i)){
      mlpri[iPredC]=3;
      mlm[iPredC]=NEXT_MB; iPredC++;
   }
   else if(iCanUseBi && mb[NEXT_BI_MB]){
      mlpri[iPredC]=2;
      mlm[iPredC]=NEXT_BI_MB; iPredC++;
   }
   else if(mb[NEXT_MB]){
      mlpri[iPredC]=1;
      mlm[iPredC]=NEXT_MB; iPredC++;
   }
   */
   /*
   if(mb[UPP_MB] && (mb[UPP_MB]->iMVMode || mb[UPP_MB]->r.i)){
      mlpri[iPredC]=2;
      mlm[iPredC]=UPP_MB; iPredC++;
   }
   else if(iCanUseBi && mb[UPP_BI_MB]){
      mlpri[iPredC]=1;
      mlm[iPredC]=UPP_BI_MB; iPredC++;
   }
   else if(mb[UPP_MB]){
      mlpri[iPredC]=1;
      mlm[iPredC]=UPP_MB; iPredC++;
   }

cnt=0;
*/
   /*
   if(!cnt && !iIsB){
      x[cnt]=mb[0]->mv_eM1_dec_enc.x;;
      y[cnt]=mb[0]->mv_eM1_dec_enc.y;;
      if(isValidVector16_MB_4(x[cnt],y[cnt],mb[0]))cnt++;
      
      return cnt;
   }
   */
   
   if(!cnt && iIsB && !iPredC && mb[0]->refId!=-2){
      //enum{eBi,eRefM2,eRef,eRefN,eRefM2_m,eRef_m,eRefN_m,eRefM2_r,eRef_r,eRefN_r,eRMR_last};
                             //0,1,2,3,1,2,3,1,2,3
      static const int mrev[]={1,0,1,1,0,0,0,0,1,1,};
      if(mb[0]->i &&  mb[0][-1].refId!=-2 && mrev[mb[0][-1].iBlockMode]){
         x[cnt]=-mb[0][-1].vrefs[brid].x;
         y[cnt]=-mb[0][-1].vrefs[brid].y;

         cnt++;
      }
      if(mb[0]->j &&  mb[0][-mb[0]->xc].refId!=-2  && mrev[mb[0][-mb[0]->xc].iBlockMode]){
         x[cnt]=-mb[0][-mb[0]->xc].vrefs[brid].x;
         y[cnt]=-mb[0][-mb[0]->xc].vrefs[brid].y;
         cnt++;
      }
      return cnt;

   }
   
   
   if(!iPredC)return cnt;
   if(iPredC>1)cnt=0;
   int i,j;
   //int b0=0;
//   for(j=0;j<iPredC && cnt<2;j++){
   for(j=0;j<iPredC && cnt<2;j++){
      int iBestID=j;
      int iF=0;
      
      for(i=0;i<iPredC;i++){
      //   if(j && b0==iBestID)continue;
         if(mlpri[i] && mlpri[i]>mlpri[iBestID]){iBestID=i;iF=1;}
      }
      //if(!iF)break;
      mlpri[iBestID]=0;
      
      //b0=iBestID;
      m=mb[mlm[iBestID]];
      if(m->r.i || (m->iIsBi && iCanUseBi)){
         x[cnt]=m->vrefs[rid].x;
         y[cnt]=m->vrefs[rid].y;
      }
      else{
         switch(mlm[iBestID]){
            case UP_MB:
               x[cnt]=(m->mv4[10].x + m->mv4[11].x +m->mv4[14].x+m->mv4[15].x);
               y[cnt]=(m->mv4[10].y + m->mv4[11].y +m->mv4[14].y+m->mv4[15].y);
               if(x[cnt]>0)x[cnt]+=2;
               if(y[cnt]>0)y[cnt]+=2;
               x[cnt]>>=2;y[cnt]>>=2;
               break;
            case NEXT_MB:
               x[cnt]=m->mv4[10].x;
               y[cnt]=m->mv4[10].y;
               break;
               
            case UPP_MB:
               x[cnt]=m->mv4[15].x;
               y[cnt]=m->mv4[15].y;
               break;
               
            case PREV_MB:
               x[cnt]=(m->mv4[5].x + m->mv4[7].x +m->mv4[13].x+m->mv4[15].x);//if(x[cnt]<0)x[cnt]+=3;x[cnt]>>=2;
               y[cnt]=(m->mv4[5].y + m->mv4[7].y +m->mv4[13].y+m->mv4[15].y);//if(y[cnt]<0)y[cnt]+=3;y[cnt]>>=2;
               if(x[cnt]>0)x[cnt]+=2;
               if(y[cnt]>0)y[cnt]+=2;
               x[cnt]>>=2;y[cnt]>>=2;
               break;
               
            case UP_BI_MB:
            case NEXT_BI_MB:
            case PREV_BI_MB:
               x[cnt]=m->vrefs[rid].x;
               y[cnt]=m->vrefs[rid].y;
               break;
         }
      }
      if(cnt && x[cnt]==x[0] && y[cnt]==y[0]){}else
     // if(isValidVector16_MB_4(x[cnt],y[cnt],mb[0]))
         cnt++;
/*
      if(!iIsB && rid==1 && cnt && mb[0]->mv_eM1_dec_enc.x==x[cnt-1] && mb[0]->mv_eM1_dec_enc.y==y[cnt-1]){
         if(cnt>1){
            x[0]=x[cnt-1];
            y[0]=y[cnt-1];
            cnt=1;
         }
         break;
      }
  */    
//cnt++;

   }


/*
   if(mb[UP_MB]){
      mlpri[iPredC]=mb[UP_MB]->iMVMode || mb[UP_MB]->r.i?3:(mb[UP_BI_MB]?2:1);
      mlm=UP_MB; iPredC++;
   }
   if(mb[NEXT_MB]){
      mlpri[iPredC]=mb[NEXT_MB]->iMVMode || mb[NEXT_MB]->r.i?3:(mb[NEXT_BI_MB]?2:1);
      mlm=NEXT_MB; iPredC++;
   }
   */
   
/*
    
   m=mb[PREV_MB];

   if(m)// && mb[0].refId==m->refId)
   {
      //x[cnt]=(m->mv4[5].x + m->mv4[7].x +m->mv4[13].x+m->mv4[15].x)/4;
//      y[cnt]=(m->mv4[5].y + m->mv4[7].y +m->mv4[13].y+m->mv4[15].y)/4;
      x[cnt]=(m->mv4[5].x + m->mv4[7].x +m->mv4[13].x+m->mv4[15].x)/4;//if(x[cnt]<0)x[cnt]+=3;x[cnt]>>=2;
      y[cnt]=(m->mv4[5].y + m->mv4[7].y +m->mv4[13].y+m->mv4[15].y)/4;//if(y[cnt]<0)y[cnt]+=3;y[cnt]>>=2;
      cnt++;
   }
   else if(mb[PREV_BI_MB] && iCanUseBi){
      x[0]=mb[PREV_BI_MB]->vrefs[rid].x;
      y[0]=mb[PREV_BI_MB]->vrefs[rid].y;
      cnt++;
   }
   m=mb[UP_MB];
   if(m)// && mb[0].refId==m->refId)
   {
      if(0&&m->iMVMode==0){
         x[cnt]=m->mv2[0].x;
         y[cnt]=m->mv2[0].y;
      }
      else{
         x[cnt]=(m->mv4[10].x + m->mv4[11].x +m->mv4[14].x+m->mv4[15].x)/4;
         y[cnt]=(m->mv4[10].y + m->mv4[11].y +m->mv4[14].y+m->mv4[15].y)/4;
      }
      cnt++;
   }
   else if(mb[UP_BI_MB] && iCanUseBi){
      x[0]=mb[UP_BI_MB]->vrefs[rid].x;
      y[0]=mb[UP_BI_MB]->vrefs[rid].y;
      cnt++;
   }
   if(cnt!=2)
   {
      
      m=mb[NEXT_MB];
      if(m)
      {
         if(0&&m->iMVMode==0){
            x[cnt]=m->mv2[0].x;
            y[cnt]=m->mv2[0].y;
         }
         else{
            x[cnt]=m->mv4[10].x;
            y[cnt]=m->mv4[10].y;
         }

         cnt++;
      }
      else if(iCanUseBi && mb[NEXT_BI_MB]){
         x[0]=mb[NEXT_BI_MB]->vrefs[rid].x;
         y[0]=mb[NEXT_BI_MB]->vrefs[rid].y;
         cnt++;

      }
      if(cnt!=2){//else {
         m=mb[UPP_MB];
         if(m){
            if(0&&m->iMVMode==0){
               x[cnt]=m->mv2[0].x;
               y[cnt]=m->mv2[0].y;
            }
            else{
               x[cnt]=m->mv4[15].x;
               y[cnt]=m->mv4[15].y;
            }
            cnt++;
         }
         else if(iCanUseBi && mb[UPP_BI_MB]){
            x[0]=mb[UPP_BI_MB]->vrefs[rid].x;
            y[0]=mb[UPP_BI_MB]->vrefs[rid].y;
            cnt++;

         }
      }
   }
   */
   return cnt;
}

#endif
static inline int getPredMV_1x1S(const int id, T_CUR_VECTOR *r, MB_4 *mb[], int *x, int *y, int iIsB){
     int cnt=0;
   MB_4 *m;
   int rid=MB_4::getID(mb[0]->refId);
   /*
#ifdef T_CAN_TEST_V
   
   if(iIsB && !mb[0]->iIsBi && rid && mb[0]->mv_eM1_dec_enc.iSad==2){
      x[0]=mb[0]->mv_eM1_dec_enc.x/2;
      y[0]=mb[0]->mv_eM1_dec_enc.y/2;
      if(rid==2){
         x[0]=-x[0];y[0]=-y[0];
      }
      return 1;
//      cnt++;
 //     return 1;
   }
   
#endif
*/
    
   m=mb[PREV_MB];
   int iCanUseBi=rid!=0 || !iIsB;

   if(m)// && mb[0].refId==m->refId)
   {
      if(0&& m->iMVMode==0){//0&&m->refId==-1){
         x[cnt]=m->mv2[0].x;
         y[cnt]=m->mv2[0].y;
      }
      else{
      //x[cnt]=(m->mv4[5].x + m->mv4[7].x +m->mv4[13].x+m->mv4[15].x)/4;
//      y[cnt]=(m->mv4[5].y + m->mv4[7].y +m->mv4[13].y+m->mv4[15].y)/4;
         x[cnt]=(m->mv4[5].x + m->mv4[7].x +m->mv4[13].x+m->mv4[15].x);if(x[cnt]<0)x[cnt]+=3;x[cnt]>>=2;
         y[cnt]=(m->mv4[5].y + m->mv4[7].y +m->mv4[13].y+m->mv4[15].y);if(y[cnt]<0)y[cnt]+=3;y[cnt]>>=2;
      }
      cnt++;
   }
   else if(mb[PREV_BI_MB] && iCanUseBi){
      x[0]=mb[PREV_BI_MB]->vrefs[rid].x;
      y[0]=mb[PREV_BI_MB]->vrefs[rid].y;
      cnt++;
   }
   m=mb[UP_MB];
   if(m)// && mb[0].refId==m->refId)
   {
      if(0&&m->iMVMode==0){
         x[cnt]=m->mv2[0].x;
         y[cnt]=m->mv2[0].y;
      }
      else{
         x[cnt]=(m->mv4[10].x + m->mv4[11].x +m->mv4[14].x+m->mv4[15].x)/4;
         y[cnt]=(m->mv4[10].y + m->mv4[11].y +m->mv4[14].y+m->mv4[15].y)/4;
      }
      cnt++;
   }
   else if(mb[UP_BI_MB] && iCanUseBi){
      x[0]=mb[UP_BI_MB]->vrefs[rid].x;
      y[0]=mb[UP_BI_MB]->vrefs[rid].y;
      cnt++;
   }
   if(cnt!=2)
   {
      
      m=mb[NEXT_MB];
      if(m)
      {
         if(0&&m->iMVMode==0){
            x[cnt]=m->mv2[0].x;
            y[cnt]=m->mv2[0].y;
         }
         else{
            x[cnt]=m->mv4[10].x;
            y[cnt]=m->mv4[10].y;
         }

         cnt++;
      }
      else if(iCanUseBi && mb[NEXT_BI_MB]){
         x[0]=mb[NEXT_BI_MB]->vrefs[rid].x;
         y[0]=mb[NEXT_BI_MB]->vrefs[rid].y;
         cnt++;

      }
      if(cnt!=2){//else {
         m=mb[UPP_MB];
         if(m){
            if(0&&m->iMVMode==0){
               x[cnt]=m->mv2[0].x;
               y[cnt]=m->mv2[0].y;
            }
            else{
               x[cnt]=m->mv4[15].x;
               y[cnt]=m->mv4[15].y;
            }
            cnt++;
         }
         else if(iCanUseBi && mb[UPP_BI_MB]){
            x[0]=mb[UPP_BI_MB]->vrefs[rid].x;
            y[0]=mb[UPP_BI_MB]->vrefs[rid].y;
            cnt++;

         }
      }
   }
   return cnt;
}

int getPredMV_1x1S_g( T_CUR_VECTOR *r, MB_4 *mb[],  int iIsB){
   int x[4]={0,0,0,0};
   int y[4]={0,0,0,0}; 
#ifdef T_CAN_TEST_V
   int cnt= getPredMV_1x1SN(0,  mb,  x, y,iIsB);
#else
   int cnt= getPredMV_1x1S(0, r, mb,  x, y,iIsB);
#endif
   if(cnt<2 || (x[0]==x[1] && y[0]==y[1]))
   {
      r->x=x[0];r->y=y[0];
      return cnt;
   }
   return 0;
}
/*
static inline int median4n(int a, int b, int c, int d)
{
   int ma,mi;
   ma = mi = a;
   if (b > ma)ma = b; else if (b < mi) mi = b;
   if (c > ma)ma = c; else if (c < mi) mi = c;
   if (d > ma)ma = d; else if (d < mi) mi = d;
   return  (a + b + c + d - ma - mi) / 2;
}
*/
template <class T>
void getV_cntX2(T *rx,int *x, int cnt, int xpr)
{
     if(cnt==3){
        
        if(x[0]==x[1] || x[0]==x[2])*rx=x[0];
        else if(x[2]==x[1])*rx=x[1];
      //  else if(abs(x[0]==x[1])<2 || abs(x[0]==x[2])<2)*rx=x[0];
        //else if(abs(x[2]==x[1])<2)*rx=x[1];
        else {
            if(x[0]==xpr)*rx=x[0];
            else if(x[1]==xpr)*rx=x[1];
            else if(x[2]==xpr)*rx=x[2];
            else {
               if(x[0]*x[1]<0)// && ((y[0]<0 && y[1]>0) || (y[0]>0 && y[1]<0)))
               {
                  *rx=xpr;//GET_MED3(x[0],x[1],0);
               }
               else{

                  int sx=x[0]+x[1];if(sx<0)sx++;
                  *rx=(sx)>>1;
               //*ry=(sy)>>1;
               }
//               *rx=median4n(x[0],x[1],x[2],xpr);

            }
        }
        
        //else if(!x[0] || !x[1] || !x[2])*rx=0;else {cnt=2;x[1]=x[2];}
//      *rx=GET_MED3(x[0],x[1],x[2]);
        //getV_cntX(rx,x,cnt);
      
   }
    else if(cnt==2){
      if(x[0]==x[1]){
         *rx=x[0];
      }
      else if(x[0]==xpr)*rx=x[0];else if(x[1]==xpr)*rx=x[1];
      else if(x[0]*x[1]<0)// && ((y[0]<0 && y[1]>0) || (y[0]>0 && y[1]<0)))
      {
         *rx=xpr;//GET_MED3(x[0],x[1],0);
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
      *rx=xpr;//*ry=0;
   }
   //#define EVEN(A)		(((A)<0?(A)+1:(A)) & ~1)
   //rx[0]=EVEN(rx[0]);
   //ry[0]=EVEN(ry[0]);
}

static  inline int div2_rem1(int d){
   int s=d>>31;
   d=(d+s)^s;
   if((d&3)==2){
      d-=2;
      d>>=1;
   }
   else if((d&3)==3){
      d++;
      d>>=1;
   }
   else d>>=1;
   d=(d+s)^s;
   return d;
   
   
}

T_CUR_VECTOR  getPred_BBiz(MB_4 *mb, int ref){

   T_CUR_VECTOR v;
   int vx[6]={0,0,0,0,0,0};
   int vy[6]={0,0,0,0,0,0};
   int cnt=0;
   int xc=mb->xc;

   if(mb->i && (mb[-1].iIsBi || (mb->getID(mb[-1].refId)==ref && mb[-1].iMVMode==0))){// 
     
      vx[cnt]=mb[-1].vrefs[ref].x;
      vy[cnt]=mb[-1].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   if(mb->j && (mb[-xc].iIsBi ||( mb->getID(mb[-xc].refId)==ref && mb[-xc].iMVMode==0))){//
      vx[cnt]=mb[-xc].vrefs[ref].x;
      vy[cnt]=mb[-xc].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   if(mb->j && mb->i+1<xc && (mb[1-xc].iIsBi || (mb->getID(mb[1-xc].refId)==ref &&  mb[1-xc].iMVMode==0))){//)
      vx[cnt]=mb[-xc+1].vrefs[ref].x;
      vy[cnt]=mb[-xc+1].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   
   if(mb->j && mb->i && (mb[-1-xc].iIsBi || (mb->getID(mb[-1-xc].refId)==ref &&  mb[-1-xc].iMVMode==0))){//)
      vx[cnt]=mb[-xc-1].vrefs[ref].x;
      vy[cnt]=mb[-xc-1].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   if(cnt<3){
      int r2=ref==1?2:1;
      if(mb->i && !mb[-1].iIsBi && mb->getID(mb[-1].refId)==r2 && mb[-1].iMVMode==0){
         vx[cnt]=-mb[-1].vrefs[r2].x;
         vy[cnt]=-mb[-1].vrefs[r2].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
      }
      if( mb->j &&!mb[-xc].iIsBi && mb->getID(mb[-xc].refId)==r2 && mb[-xc].iMVMode==0){
         vx[cnt]=-mb[-xc].vrefs[r2].x;
         vy[cnt]=-mb[-xc].vrefs[r2].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
      }
   }

   int xa=0;
   int ya=0;
   if(mb->mv_eM1_dec_enc.iSad){
      xa=mb->mv_eM1_dec_enc.x/2;
      ya=mb->mv_eM1_dec_enc.y/2;
      if(ref==2){
         xa*=-1;ya*=-1;
      }
   }
   v.x=xa;
   v.y=ya;
   int i,j;
   int re[6]={0,0,0,0,0,0};
   for(j=0;j<cnt;j++){
      if(mb->mv_eM1_dec_enc.iSad){
        if((vx[j]==xa && vy[j]==ya)){re[j]+=3;}//v.x=vx[i];v.y=vy[i];j=cnt;i=cnt;break;}
       //div2_rem1 if((vx[j]==xa && vy[j]==ya)){re[j]++;}
      }
      for(i=j+1;i<cnt;i++){
         if(i==j)continue;
         if((vx[j]==vx[i] && vy[j]==vy[i])){re[j]+=2;}//v.x=vx[i];v.y=vy[i];j=cnt;i=cnt;break;}
      }
   }
   int iBest=0;
   for(i=1;i<cnt;i++){
      if(re[i]>re[iBest])iBest=i;
   }
   if(re[iBest]){
      v.x=vx[iBest];
      v.y=vy[iBest];
   }
   
   /*
   if(mb->mv_eM1_dec_enc.iSad && cnt==3){
      vx[cnt]=mb->mv_eM1_dec_enc.x;
      vy[cnt]=mb->mv_eM1_dec_enc.y;
      cnt++;

      getV_cntX(&v.x,&vx[0],cnt);
      getV_cntX(&v.y,&vy[0],cnt);
   }
   else{
   */
   //getV_cntX2(&v.x,&vx[0],cnt,xa);
   //getV_cntX2(&v.y,&vy[0],cnt,ya);
   //}
   //v->x=v->y=0;
   return v;
}

T_CUR_VECTOR  getPred_BBi(MB_4 *mb, int ref){

   int vx[4]={0,0,0,0};
   int vy[4]={0,0,0,0};
   int cnt=0;
   int xc=mb->xc;
   T_CUR_VECTOR v;

   v.iSad=0;
   if(mb->i && (mb[-1].iIsBi || (mb->getID(mb[-1].refId)==ref && mb[-1].iMVMode==0))){// 
     
      vx[cnt]=mb[-1].vrefs[ref].x;
      vy[cnt]=mb[-1].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   if(mb->j && (mb[-xc].iIsBi ||( mb->getID(mb[-xc].refId)==ref && mb[-xc].iMVMode==0))){//
      vx[cnt]=mb[-xc].vrefs[ref].x;
      vy[cnt]=mb[-xc].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   if(cnt==2 && vx[0]==vx[1] && vy[0]==vy[1]){
      v.x=vx[0];
      v.y=vy[0];
      return v;
   }
   if(mb->j && mb->i+1<xc && (mb[1-xc].iIsBi || (mb->getID(mb[1-xc].refId)==ref &&  mb[1-xc].iMVMode==0))){//)
      vx[cnt]=mb[-xc+1].vrefs[ref].x;
      vy[cnt]=mb[-xc+1].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   
   if(cnt<2 && mb->j && mb->i && (mb[-1-xc].iIsBi || (mb->getID(mb[-1-xc].refId)==ref &&  mb[-1-xc].iMVMode==0))){//)
      vx[cnt]=mb[-xc-1].vrefs[ref].x;
      vy[cnt]=mb[-xc-1].vrefs[ref].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
   }
   if(cnt!=3){
      int r2=ref==1?2:1;
      if(mb->i && !mb[-1].iIsBi && mb->getID(mb[-1].refId)==r2 && mb[-1].iMVMode==0){
         vx[cnt]=-mb[-1].vrefs[r2].x;
         vy[cnt]=-mb[-1].vrefs[r2].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
      }
      if(cnt<3 && mb->j &&!mb[-xc].iIsBi && mb->getID(mb[-xc].refId)==r2 && mb[-xc].iMVMode==0){
         vx[cnt]=-mb[-xc].vrefs[r2].x;
         vy[cnt]=-mb[-xc].vrefs[r2].y;
 if(isValidVector16_MB_4(vx[cnt],vy[cnt],mb))cnt++;
      }
   }

   int xa=0;
   int ya=0;
   if(mb->mv_eM1_dec_enc.iSad){
      
      xa=div2_rem1(mb->mv_eM1_dec_enc.x);
      ya=div2_rem1(mb->mv_eM1_dec_enc.y);
//      xa=mb->mv_eM1_dec_enc.x/2;
  //    ya=mb->mv_eM1_dec_enc.y/2;
      if(ref==2){
         xa*=-1;ya*=-1;
      }
      //vx[2]=xa;vy[2]=ya;cnt=3;
      //mb->mv_eM1_dec_enc.iSad?mb->mv_eM1_dec_enc.y:0
   }
   getV_cntX2(&v.x,&vx[0],cnt,xa);
   getV_cntX2(&v.y,&vy[0],cnt,ya);
   //}
   //v->x=v->y=0;
   return v;
   
}
int canBeBBi(MB_4 *mb,  int i, int j){
   if(mb->mv_eM1_dec_enc.iSad==2)return 5;
   int xc=mb->xc;

   int bi=mb->mv_eM1_dec_enc.iSad==1;
   int m1=0;
   int m2=0;
#define T_X_BBI(_i,_o) if(_i){bi+=(!!mb[_o].iIsBi);m1+=mb[_o].refId==-1;m2+=mb[_o].refId>0;}
   T_X_BBI(i,-1);
   T_X_BBI(j,-xc);
   T_X_BBI(j && i+1<xc,1-xc);
   T_X_BBI(i && j,-1-xc);
   if(m1 && m2)bi+=2;
   return bi;
}
#if 1
//def T_CAN_TEST_V
int getPredMV_1x1_bbi(T_CUR_VECTOR *r1,T_CUR_VECTOR *r2 , MB_4 *mb,  int i, int j, int iEq){
   


   int m=mb->mv_eM1_dec_enc.iSad!=2;

   if(m){
      if(iEq){
         return *r1==getPred_BBi(mb,1) && *r2==getPred_BBi(mb,2);
      }
      *r1=getPred_BBi(mb,1);
      *r2=getPred_BBi(mb,2);
/*      
      if(mb->mv_eM1_dec_enc.iSad && i && j && *r1==mb[-1].vrefs[1] && *r2==mb[-1].vrefs[2]){

         r1->x=x;
         r1->y=y;
         r2->x=-x;
         r2->y=-y;

      }
  */    
   }
   else{
   int x=mb->mv_eM1_dec_enc.x;
   int y=mb->mv_eM1_dec_enc.y;
   int s=x>>31;x=(x+s)^s;if((x&3)==3)x++;x>>=1;x=(x+s)^s;
       s=y>>31;y=(y+s)^s;if((y&3)==3)y++;y>>=1;y=(y+s)^s;
      if(iEq){
         return r1->x==x && r1->y==y && r2->x==x && r2->y==y; 
      }
      r1->x=x;
      r1->y=y;
      r2->x=-x;
      r2->y=-y;
   }
   return 0;
}
#endif




class CT_ED_MV_SK{
   int iESk[2];
public:
   int iCnt;
   unsigned char buf[1024*16-12];
   CT_ED_MV_SK(){reset();}
   void reset(){iCnt=0;iESk[0]=iESk[1]=0;}
   void encV(){buf[iCnt]=1;iCnt++;iESk[1]++;}
   void skipV(){buf[iCnt]=0;iCnt++;iESk[0]++;}
   int isV(){return buf[iCnt++];}
   int iS1;
   int iS2;
   void encBlocks(CTVLCX *vlc){

      iS1=iS2=0;

      vlc->toVLC_PN(iESk[0],6);
      vlc->toVLC_PN(iESk[1],6);
      int i;

      int iPrevV=iESk[0]<iESk[1];
      int iSkipCnt=0;
      for(i=0;i<iCnt;i++){
         iESk[iPrevV]--;
         if(iPrevV==buf[i]){iSkipCnt++;continue;}
         vlc->toVLC(iSkipCnt);iSkipCnt=0;
         iS1++;
         iPrevV=!iPrevV;
      }
/*      
      for(i=0;i<iCnt && iESk[0] && iESk[1];i++){
         iESk[iPrevV]--;
         if(iPrevV==buf[i]){iSkipCnt++;continue;}
         vlc->toVLC(iSkipCnt);iSkipCnt=0;
         iS1++;
         iPrevV=!iPrevV;
      }
      if(iSkipCnt){vlc->toVLC(iSkipCnt);iS1++;}
*/
     // vlc->iBitPos=iBP;
     // decBlocks(vlc);
   }
   void decBlocks(CTVLCX *vlc){
      iESk[0]=vlc->getVlc_PN(6);
      iESk[1]=vlc->getVlc_PN(6);
      int i=0;
      int iPrevV=iESk[0]<iESk[1];
      iCnt=iESk[0]+iESk[1];
      int iSkipCnt=0;//iESk[0] && iESk[1]?vlc->getVlc():(iESk[1]?iESk[1]:iESk[0]);
      if(iESk[0] && iESk[1])iSkipCnt=vlc->getVlc();
      for(;i<iCnt && iESk[0]>0 && iESk[1]>0;i++){
         
         
         if(iSkipCnt>0){iSkipCnt--;}
         else {iS2++;iSkipCnt=vlc->getVlc();iPrevV=!iPrevV;}
         //if(iS2==iS1)
          //  break;
         if(iSkipCnt>iCnt)
            break;
         buf[i]=iPrevV;
         iESk[iPrevV]--;
      }
      if(iESk[1])
         memset(&buf[i],1,iESk[1]);
      if(iESk[0])
         memset(&buf[i],0,iESk[0]);
      iCnt=0;
   }

};

CT_ED_MV_SK skX;
CT_ED_MV_SK skXDec;
//int iIsBX=1;

template <int iDec>
static int getPredMV_nxn(CTVLCX  * vlc,const int  m, const int id, T_CUR_VECTOR *r, MB_4 *mb[] ,int  px, int py,  int iIsB){
   int x[4]={px,0,0,0};
   int y[4]={py,0,0,0};  
   int cnt=0;
    //  r->x=x[0];r->y=y[0];
      //return 1;   
#ifdef T_CAN_TEST_V
   if(m==1)   cnt=getPredMV_1x1SN(id, mb,  x, y,iIsB);
#else
   if(m==1)   cnt=getPredMV_1x1S(id, r, mb,  x, y,iIsB);
#endif
   else if(m==2)   cnt=getPredMV_2x2S(id, r, mb,  x, y,iIsB); 
   else   cnt=getPredMV_4x4S(id, r, mb,  x, y,iIsB); 

   //int iIsB=1;
   /*
   int cntx=0;
   for(int i=0;i<cnt;i++){
      if(isValidVector16_MB_4(x[i],y[i],mb[0])){x[cntx]=x[i];y[cntx]=y[i];cntx++;}
   //   if(!isValidVector16_MB_4(x[i],y[i],mb[0])){x[i]=-x[i];y[i]=-y[i];}
   }
//   if(cntx==0){x[0]=y[0]=0;}
   cnt=cntx;
*/
   if(cnt<2 || (x[0]==x[1] && y[0]==y[1]))
   {
      r->x=x[0];r->y=y[0];
      return 1;//cnt;
   }


#if 0
   //def T_CAN_TEST_V
   if(iIsB && m==1 && mb[0]->iIsBi && mb[PREV_BI_MB] && mb[UP_BI_MB] && mb[NEXT_BI_MB]){
   
//#define GET_MED3(_A,_B,_C) (_A*3+_B*3+_C*2)/8
#define GET_MED3(_A,_B,_C) (min(max(_A, _B), min(max(_B, _C), max(_A, _C))))
   //r->x=(x[0]+x[1])/2 ; r->y=(y[0]+y[1])/2;
      
//   r->x=x[0];r->y=y[0];
   r->x=GET_MED3(x[0],x[1],x[2]);
   r->y=GET_MED3(y[0],y[1],y[2]);
   return cnt;
   }
#endif
   /*
   void getV_cntX(int *rx,int *x, int cnt);
   if(cnt>3)cnt=3;
   getV_cntX(&r->x,&x[0],cnt);
   getV_cntX(&r->y,&y[0],cnt);
   return cnt;
   */
   //r->x=(x[0]+x[1])/2;r->y=(y[0]+y[1])/2;   return cnt;
   if(iDec){
      if(vlc->getB()){r->x=x[0];r->y=y[0];}else {r->x=x[1];r->y=y[1];}
      return cnt;
   }      
   int a=r->x-x[0];
   int b=r->y-y[0];
  // if(a>63)a=63;
   //if(b>63)b=63;
 //  iGainX++;
   if(!a && !b){
      vlc->addB(1);r->x=x[0];r->y=y[0];
      //skCP++;
      //skX2.encV();iGainX++;
      return 2;//cnt;
   }

   int c=r->x-x[1];
   int d=r->y-y[1];

   if(!c && !d){
      vlc->addB(0);r->x=x[1];r->y=y[1];
     // skX2.skipV();iGainX++;
      //skCP++;
      return 3;//cnt;
   }

   int ab=vlc->toMV_calc(a,b);
   int cd=vlc->toMV_calc(c,d);
//iGainX-=vlc->bitsLen[skCP];skCP=0;
   if(cd<ab){ vlc->addB(0);r->x=x[1];r->y=y[1];/*skX2.skipV();iGainX++;*/}
   else {vlc->addB(1);r->x=x[0];r->y=y[0];/*skX2.encV();iGainX++;*/}   
   return 4;
}
static const int log2_tab_16[16] =  { 0, 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4 };
 
static unsigned int  log2bin(int value)
{
   int s=value>>31;
   value=(value+s)^s;
  int n = 0;
  if (value & 0xff00) {
    value >>= 8;
    n += 8;
  }
  if (value & 0xf0) {
    value >>= 4;
    n += 4;
  }
 return n + log2_tab_16[value];
}

void debugsi(char*,int);

static void encVec(CTVLCX *vlc, int x, int y, int m){
#if 1
   if(!y && x>0)x--;
   //int c=(log2bin(x)+log2bin(y))>>3;
   //debugsi("c",c);
   //if(c>2)c=2;
   //vlc->add000n1(c);
   //iGainX++;


   vlc->toVLC_PNS(x,1);
   vlc->toVLC_PNS(y,1);

   //iGainX+=vlc->iBitPos-bp;addRacVal(SGN_MAP(x));addRacVal(SGN_MAP(y));
#else
   vlc->toVLC_PNS(y,1);
   if(!y){
      CTVLCX::encNonZero(*vlc,x);
   }
   else{
      vlc->toVLC_PNS(x,1);
   }
#endif
   //iPrevB+=(log2bin(x)+log2bin(y)+3)>>2;
  // iPrevB>>=1;
  
}
static void decVec(CTVLCX *vlc, T_CUR_VECTOR *v, int m){

//   int x=CTVLCX::decZero2b(*vlc);
  // int y=CTVLCX::decZero2b(*vlc);
#if 1
   //int c=vlc->get000n1();
   int x=vlc->getVlc_PNS(1);
   int y=vlc->getVlc_PNS(1);
   if(!y && x>=0)x++;
   v->x+=x;
   v->y+=y;
#else
   int y=vlc->getVlc_PNS(1);
   int x;
   if(!y){
      x=CTVLCX::decNonZero(*vlc);
   }
   else{
      x=vlc->getVlc_PNS(1);
   }
   v->x+=x;
   v->y+=y;
#endif
}
#define SUM_SKIP_V    1
#define SUM_SKIP_V_B  0

#ifdef T_CAN_TEST_V
#define saveVec saveVecN
#define getVec getVecN
#else
#define saveVec saveVecO
#define getVec getVecO
#endif

static void saveVecO(CTVLCX  * vlc, int m,int id,  MB_4 *mbx[], T_CUR_VECTOR  * v, int prx, int pry, int iIsB){
   T_CUR_VECTOR va;
   va=*v;//v->x;va.y=v->y;
   //int bp=vlc->iBitPos;
    //  int bp=vlc->iBitPos;      int G=iGainX;
   int cnt=getPredMV_nxn<0>(vlc,m,id,&va,mbx,prx,pry,  iIsB);
   
   int dx=v->x-va.x;
   int dy=v->y-va.y;

   if(dx | dy){
      vlc->addB(1);
      vlc->toMV(dx);
      vlc->toMV(dy);
   }
   else{
      vlc->addB(0);
   }
}


static void saveVecN(CTVLCX  * vlc, int m,int id,  MB_4 *mbx[], T_CUR_VECTOR  * v, int prx, int pry, int iIsB){
   T_CUR_VECTOR va;
   va=*v;//v->x;va.y=v->y;
   //int bp=vlc->iBitPos;
      //int bp=vlc->iBitPos;      int G=iGainX;
   int cnt=getPredMV_nxn<0>(vlc,m,id,&va,mbx,prx,pry,  iIsB);
   
   int dx=v->x-va.x;
   int dy=v->y-va.y;

                   //0,1,2,3,1,2,3,1,2,3;
   int iAddB=bms[mbx[0]->iBlockMode];// && (mbx[0]->i && mbx[0]->i+1<mbx[0]->xc);
   //int iAddB=!iIsB ||  !mbx[0]->iIsBi;// &&  !mbx[0]->iMVMode;
//   if(iIsB && !mbx[0]->iIsBi && (mbx[0]->i || mbx[0]->j) && mbx[0][-1].iIsBi){

   
   if(dx | dy){
      
      if(iAddB){//m==1 && SUM_SKIP_V && mbx[0]->refId!=-2 && (SUM_SKIP_V_B || !mbx[0]->iIsBi)){
         //int mm=0;//MB_4::getID(mbx[0]->refId);
         //iGainX-=vlc->bitsLen[skC[mm]];skC[mm]=0;
         skX.encV();
         //if(!(m==1 && iIsB && mbx[0]->iIsBi)){
         
         //1 3 3 5 5 5 5  
      }
      else vlc->addB(1);

      encVec(vlc,dx,dy,m);
   }
   else{
     // if(iPrevISOk)skC[m]++;iPrevISOk=1;
      //if(m==1 && SUM_SKIP_V && mbx[0]->refId!=-2 && (SUM_SKIP_V_B || !mbx[0]->iIsBi)){
      if(iAddB){
         skX.skipV();
//         skX.skipV();
         //if(!(m==1 && iIsB && mbx[0]->iIsBi))
      }
      else vlc->addB(0);
      
   }
   
   /*
#ifdef T_CAN_TEST_V
   if(mbx[0]->iSadWOCost[1]>mbx[0]->iDev+100){
      iGainX+=3;   
      //iGainX+=(vlc->iBitPos-bp);
      //   iGainX-=(2+2);
      }
#endif
     */ 
   
   
         //if(vlc->getVlc()!=0x5)iDecErrs++;
 //vlc->toVLC(0x5);   
}
/*
static int getPred_nonB(MB_4 *mb, T_CUR_VECTOR * v){
   int vx[3]={0,0,0};
   int vy[3]={0,0,0};
   int cnt=0;
   int xc=mb->xc;
   
   if(mb->i){
      vx[cnt]=mb[-1].mv_eM1_gr.x;
      vy[cnt]=mb[-1].mv_eM1_gr.y;
      cnt++;
   }
   if(mb->j){
      vx[cnt]=mb[-xc].mv_eM1_gr.x;
      vy[cnt]=mb[-xc].mv_eM1_gr.y;
      cnt++;
   }
   if(mb->j && mb->i+1<xc){
      vx[cnt]=mb[-xc+1].mv_eM1_gr.x;
      vy[cnt]=mb[-xc+1].mv_eM1_gr.y;
      cnt++;
   }

   getV_cntX(&v->x,&vx[0],cnt);
   getV_cntX(&v->y,&vy[0],cnt);
   //v->x=v->y=0;
   return 0;
}

*/
template<int iDec>
static int getPred_B(CTVLCX  * vlc,MB_4 *mbx[], T_CUR_VECTOR * v, int id, int iIgnoreTop, int iPx, int iPy, int iIsB){
   int vx[]={iPx,0,0,0};
   int vy[]={iPy,0,0,0};

   MB_4 *mbc=mbx[0];

   int cnt=0;
   int xc=mbc->xc;
   //int iRid=id==1?-1:2;
//#define CMP_BI(_A,_ID) _A &&(mb[_ID].iMVMode==0 && mb[_ID].refId!=-2 && (mb[_ID].iIsBi || (mb[_ID].refId==-1 && id==1) || (mb[_ID].refId>0 && id==2)))
   // && mb[_ID].refId!=mb->refId
#define CMP_BI(_A,_ID) _A &&((mb[_ID].refId!=-2 || iIsB==0) && mb[_ID].iIsBi)
   //&& ((mb[_ID].refId==-1 && id==1) || (mb[_ID].refId>0 && id==2) || (mb[_ID].refId<0 && id==0)))
#define CMP_BIR(_A,_ID) _A &&  MB_4::getID(mbc[_ID].refId)==id
   //}

   
      if(mbx[PREV_BI_MB]){// && (mb[-1].refId!=-2 || iIsB==0)){//mb->i && mb[-1].iMVMode==0 && mb[-1].refId!=-2){
         vx[cnt]=mbc[-1].vrefs[id].x;
         vy[cnt]=mbc[-1].vrefs[id].y;
         cnt++;
      }
      else if(CMP_BIR(mbc[0].i, -1)){
         MB_4 *mb=&mbc[-1];
         vx[cnt]=(mb->mv4[5].x + mb->mv4[7].x +mb->mv4[13].x+mb->mv4[15].x)/4;
         vy[cnt]=(mb->mv4[5].y + mb->mv4[7].y +mb->mv4[13].y+mb->mv4[15].y)/4;
         cnt++;
      }
      
      if(mbx[UP_BI_MB]){// && (mb[-xc].refId!=-2 || iIsB==0)){//mb->j && mb[-xc].iMVMode==0 && mb[-xc].refId!=-2){
         vx[cnt]=mbc[-xc].vrefs[id].x;
         vy[cnt]=mbc[-xc].vrefs[id].y;
         cnt++;
      }
      else if(iIgnoreTop==0 && CMP_BIR(mbc[0].j, -xc)){
         MB_4 *mb=&mbc[-xc];
         vx[cnt]=(mb->mv4[10].x + mb->mv4[11].x +mb->mv4[14].x+mb->mv4[15].x)/4;
         vy[cnt]=(mb->mv4[10].y + mb->mv4[11].y +mb->mv4[14].y+mb->mv4[15].y)/4;
         cnt++;
      }
    
   if(cnt<2 && iIgnoreTop==0){
      if(mbx[NEXT_BI_MB]){
         vx[cnt]=mbx[NEXT_BI_MB]->vrefs[id].x;
         vy[cnt]=mbx[NEXT_BI_MB]->vrefs[id].y;
         cnt++;
      }
      else if(CMP_BIR(mbc[0].i+1<xc && mbc[0].j, 1-xc)){
         vx[cnt]=mbc[1-xc].mv4[10].x;
         vy[cnt]=mbc[1-xc].mv4[10].y;
         cnt++;
      }

      if(mbx[UPP_BI_MB]){
         vx[cnt]=mbx[UPP_BI_MB]->vrefs[id].x;
         vy[cnt]=mbx[UPP_BI_MB]->vrefs[id].y;
         cnt++;
      }
      else if(CMP_BIR(mbc[0].i && mbc[0].j, -1-xc)){
         vx[cnt]=mbc[-1-xc].mv4[15].x;
         vy[cnt]=mbc[-1-xc].mv4[15].y;
         cnt++;
      }
   }
#ifdef T_CAN_TEST_V
   /*
   if(iIsB && cnt>=2){
      vx[0]=(vx[0]+vx[1]+1)>>1;
      vy[0]=(vy[0]+vy[1]+1)>>1;
      cnt=1;
   }
   */
      if(iIsB && (cnt<2)){
         vx[cnt]=-mbx[0]->mv2[0].x;
         vy[cnt]=-mbx[0]->mv2[0].y;
         cnt++;
      }
#endif
   
   if(cnt<2 || (vx[0]==vx[1] && vy[0]==vy[1])){

      v->x=vx[0];
      v->y=vy[0];
      return 0;
   }
   else{

  //    getV_cntX(&v->x,&vx[0],cnt);
    //  getV_cntX(&v->y,&vy[0],cnt);
   }
   
   if(iDec){
      if(vlc->getB()){v->x=vx[0];v->y=vy[0];}else {v->x=vx[1];v->y=vy[1];}
      return cnt;
   }      
   int a=v->x-vx[0];
   int b=v->y-vy[0];
  // if(a>63)a=63;
   //if(b>63)b=63;
   if(!a && !b){
      vlc->addB(1);v->x=vx[0];v->y=vy[0];
      return cnt;
   }

   int c=v->x-vx[1];
   int d=v->y-vy[1];

   if(!c && !d){
      vlc->addB(0);v->x=vx[1];v->y=vy[1];
      //skCP++;
      return cnt;
   }

   //if(c>63)c=63;
   //if(d>63)d=63;
   /*
   int l=(a|b)?vlc->toMV_calc(a)+vlc->toMV_calc(b):2;//getMVBitLen(a,b);
   int l2=(c|d)?vlc->toMV_calc(d)+vlc->toMV_calc(c):2;
*/
   int ab=vlc->toMV_calc(a,b);
   int cd=vlc->toMV_calc(c,d);
//iGainX-=vlc->bitsLen[skCP];skCP=0;
   if(cd<ab){ vlc->addB(0);v->x=vx[1];v->y=vy[1];}
   else {vlc->addB(1);v->x=vx[0];v->y=vy[0];}   

   return cnt;
   //v->x=v->y=0;
//   return 0;
}


static int encMB_B(CTVLCX  * vlc, int *px, int *py, MB_4 *mbx[], int iIsB, int iIgnoreTop){
   MB_4 *v=mbx[0];
   int rid=v->refId==-1?(iIsB?v->eNext:v->eM2):v->eM1;
   T_CUR_VECTOR *c=&v->vrefs[rid];



   T_CUR_VECTOR va;
   //va.x=c->x;va.y=c->y;
   va=*c;
   getPred_B<0>(vlc,mbx,&va, rid,iIgnoreTop, px[rid], py[rid], iIsB);
   //va.x=va.y=0;
   
   

   int dx=c->x-va.x;
   int dy=c->y-va.y;
//debugss("dx dy",dx,dy);
   
   if(dx | dy){
      //if(!dx)iGainX+=vlc->bitsLenAC[abs(dy)]-vlc->bitsLenAC[dy<0?(-dy-1):dy];
      vlc->addB(1);
#ifdef T_CAN_TEST_V
      encVec(vlc,dx,dy,1);
     // if(!dx && dy>0)dy--;
     // CTVLCX::encZero2b(*vlc,dx);
     // CTVLCX::encZero2b(*vlc,dy);
#else
      vlc->toMV(dx);
      vlc->toMV(dy);
#endif
   }
   else {
      vlc->addB(0);
   }

   px[rid]=c->x;py[rid]=c->y;

   return 0;
}


int encMB_MV(CTVLCX  * vlc,MB_4 *mbx[], int *px, int *py, int iHas4x4, int iIsB){
//   T_CUR_VECTOR va;
   
   MB_4 *v=mbx[0];
   int rid=v->getID(v->refId);   

   T_CUR_VECTOR *c;
   int i,j;
   
   if(v->iMVMode==0)
   {
      c=&v->vrefs[rid];
      //if(v->iUpdateed==77)
        // v->iUpdateed=78;
      
      int isValidVector(int x, int y ,int pos1, int pos2,int iMBSize, int w ,int h);
      if(0&&c->iSad>v->iDev+300 && c->iSad>v->iPosibleMinSad+100 && v->iHada[rid]){
      if(mbx[PREV_MB] && mbx[PREV_MB]->iHada[rid]==0   &&  mbx[PREV_MB]->iMVMode==0 
         && isValidVector(mbx[PREV_MB]->vrefs[rid].x,mbx[PREV_MB]->vrefs[rid].y,v->i*16,v->j*16,16,v->xc*16,v->yc*16)){
         *c=mbx[PREV_MB]->vrefs[rid];
         for(j=0;j<4;j++)v->mv2[j]=*c;
         for(j=0;j<16;j++)v->mv4[j]=*c;
      }else if(mbx[UP_MB] && mbx[UP_MB]->iHada[rid]==0  && mbx[UP_MB]->iMVMode==0 
         && isValidVector(mbx[UP_MB]->vrefs[rid].x,mbx[UP_MB]->vrefs[rid].y,v->i*16,v->j*16,16,v->xc*16,v->yc*16)){
         *c=mbx[UP_MB]->vrefs[rid];
         for(j=0;j<4;j++)v->mv2[j]=*c;
         for(j=0;j<16;j++)v->mv4[j]=*c;
      
      }else if(0){
         
         c->x=0;c->y=0;
         for(j=0;j<4;j++)v->mv2[j]=*c;
         for(j=0;j<16;j++)v->mv4[j]=*c;
      }

      }
      
      
      saveVec(vlc,1,0,mbx,c,px[rid],py[rid],  iIsB);
      px[rid]=c->x;py[rid]=c->y;
      v->mv2[0]=*c;
      if(v->r.i)
      {

         int a1=0;
         int a2=0;
         getPredRot(v,mbx,a1,a2);         
#if 0
         //def T_CAN_TEST_V     
         if(v->r.s[0]){
            vlc->addB(1);
            
            if(a1)vlc->toVLC_PNS(v->r.s[0]-a1,3);else
            {
               vlc->toVLC_X(mgabs(v->r.s[0])-1,2);
               vlc->addB(v->r.s[0]<0);
            }
            
            
         }
         else{
            vlc->addB(0);
            
            if(a2)vlc->toVLC_PNS(v->r.s[1]-a2,3);else
            {
               vlc->toVLC_X(mgabs(v->r.s[1])-1,2);
               vlc->addB(v->r.s[1]<0);
            }
         }
#else

         if(v->r.s[0]){
            vlc->addB(1);
            
            if(a1)vlc->toMV(v->r.s[0]-a1);else
            {
               vlc->toVLC(mgabs(v->r.s[0])-1);
               vlc->addB(v->r.s[0]<0);
            }
            
            
         }
         else{
            vlc->addB(0);
            
            if(a2)vlc->toMV(v->r.s[1]-a2);else
            {
               vlc->toVLC(mgabs(v->r.s[1])-1);
               vlc->addB(v->r.s[1]<0);
            }
         }
#endif


         //if(vlc->getVlc()!=0x39)iDecErrs++;
//vlc->toVLC(0x39);
         

      }
      
   }else {
      int m=v->iMVMode&(~1);
      if(iHas4x4){
         vlc->addB(m);
         if(m)for(j=0;j<4;j++){vlc->addB(tabMode[j] & m);}
      
        
         //vlc->toVLC(v->iMVMode>>1);
      }
      for(i=0;i<16;i++){
         j=i>>2;
         if((tabMode[j] & m)==0){
            i+=3;  
            c=&v->mv2[j];
            saveVec(vlc,2,j,mbx,c,px[rid],py[rid],  iIsB);
            px[rid]=c->x;py[rid]=c->y;
            continue;
         }
         c=&v->mv4[i];
         saveVec(vlc,4,i,mbx,c,px[rid],py[rid],  iIsB);   
         px[rid]=c->x;py[rid]=c->y;
      }
      
      
   }
   
   
   return 0;   
}





   
static void getVecO(CTVLCX  * vlc,int m, int id, MB_4 *mbx[], T_CUR_VECTOR  * v,  int prx, int pry,int iIsB){
  
   getPredMV_nxn<1>(vlc,m,id,v,mbx,prx,pry, iIsB);
   
   if(vlc->getB()){
      //if(dx)vlc->toMV(dy);else vlc->toMV(dy>0?(dy-1):dy);
      int vx=vlc->getVlcSM();
      int vy=vlc->getVlcSM();      
      v->x+=vx;
      v->y+=vy;
   }
//if(vlc->getVlc()!=0x5)iDecErrs++;
   
}

static void getVecN(CTVLCX  * vlc,int m, int id, MB_4 *mbx[], T_CUR_VECTOR  * v,  int prx, int pry,int iIsB){
  
   getPredMV_nxn<1>(vlc,m,id,v,mbx,prx,pry, iIsB);
   
   int iGetB=bms[mbx[0]->iBlockMode];
   if(iGetB){
      if(skXDec.isV())decVec(vlc,v,m);
   }
   else {
      if(vlc->getB())decVec(vlc,v,m);
   }

   if(!isValidVector16_MB_4_L(v->x, v->y, mbx[CUR_MB], modeLen[m])){
      v->x=v->y=0;
   }


   
}


int decMB_B(CTVLCX  * vlc, int *px, int *py, MB_4 *mbx[], int iIsB, int iIgnoreTop){

   MB_4 *v=mbx[0];
   T_CUR_VECTOR *c;
   int rid=v->refId==-1?(iIsB?v->eNext:v->eM2):v->eM1;
   c=&v->vrefs[rid];
   
   getPred_B<1>(vlc,mbx,c, rid,iIgnoreTop, px[rid], py[rid],iIsB);
   //c->x=c->y=0;

   if(vlc->getB()){
#ifdef T_CAN_TEST_V
      /*
      int vx=CTVLCX::decZero2b(*vlc);
      int vy=CTVLCX::decZero2b(*vlc);
      if(!vx && vy>=0)vy++;
      c->x+=vx;
      c->y+=vy;
      */
     decVec(vlc,c,1);

#else
      c->x+=vlc->getVlcSM();
      c->y+=vlc->getVlcSM();      
#endif

   }
   if(!isValidVector16_MB_4_L(c->x, c->y, mbx[CUR_MB], modeLen[0])){
      c->x=c->y=0;
   }
   //TODO check range
   px[rid]=c->x;py[rid]=c->y;
   return 0;
}
//int rleStat[5000];
//int iRlePos=0;;

int decMB_MV(CTVLCX  * vlc, int *px, int *py, MB_4 *mbx[], int iHas4x4,int iIsB){
//   T_CUR_VECTOR va;

   MB_4 *v=mbx[0];
   int rid=v->getID(v->refId);   
   T_CUR_VECTOR *c;
//      T_CUR_VECTOR vr;
   int i,j;
   
   if(v->iMVMode==0)
   {
    //  static int idx;
      c=&v->vrefs[rid];
//      if(idx==12111)
      //if(v->iUpdateed==78)
        // v->iUpdateed=79;
  //       idx=20000;
      getVec(vlc,1,0,mbx,c,px[rid],py[rid],iIsB);
      px[rid]=c->x;py[rid]=c->y;
         
      //idx++;

      //if(v->mv2[0]!=*c){v->mv2[0]=*c;iDecErrs++;}
      v->mv2[0]=*c;

      //for(i=0;i<16;i++)v->mv4[i]=*c;
      /*
// 0  1  4  5 
// 2  3  6  7 
// 8  9 12 13
//10 11 14 15
      */
      v->mv4[10]=v->mv4[11]=v->mv4[14]=v->mv4[5]=v->mv4[7]=v->mv4[13]=v->mv4[15]=*c;
      //*c;

      
      if(v->r.i)
      {
         int a1=0;
         int a2=0;
         getPredRot(v,mbx,a1,a2);         

#if 0
         //def T_CAN_TEST_V      
         if(vlc->getB()){
            v->r.s[1]=0;
            if(a1)v->r.s[0]=a1+vlc->getVlc_PNS(3);else
            {
               v->r.s[0]=vlc->getVlcX(2)+1;
               if(vlc->getB())v->r.s[0]=-v->r.s[0];
            }
            
            if(mgabs(v->r.s[0])>18){
               iDecErrs+=100;
               debugss("rot err           ",v->r.s[0],v->r.s[1]);
            }            
         }
         else{
            v->r.s[0]=0;
            //vlc->addB(0);
            if(a2)v->r.s[1]=a2+vlc->getVlc_PNS(3);else
            {
               v->r.s[1]=vlc->getVlcX(2)+1;
               if(vlc->getB())v->r.s[1]=-v->r.s[1];
            }
            if(mgabs(v->r.s[1])>18){
               iDecErrs+=100;
               debugss("rot err           ",v->r.s[0],v->r.s[1]);
            }
         }
#else
         if(vlc->getB()){
            v->r.s[1]=0;
            if(a1)v->r.s[0]=a1+vlc->getVlcSM();else
            {
               v->r.s[0]=vlc->getVlc()+1;
               if(vlc->getB())v->r.s[0]=-v->r.s[0];
            }
            
            if(mgabs(v->r.s[0])>18){
               iDecErrs+=100;
               debugss("rot err           ",v->r.s[0],v->r.s[1]);
            }            
         }
         else{
            v->r.s[0]=0;
            //vlc->addB(0);
            if(a2)v->r.s[1]=a2+vlc->getVlcSM();else
            {
               v->r.s[1]=vlc->getVlc()+1;
               if(vlc->getB())v->r.s[1]=-v->r.s[1];
            }
            if(mgabs(v->r.s[1])>18){
               iDecErrs+=100;
               debugss("rot err           ",v->r.s[0],v->r.s[1]);
            }
         }

#endif
        // static int iRotErr;


//if(vlc->getVlc()!=0x39)iDecErrs++;

         //if(iRotErr)debugss("rererere",iRotErr,0);

      }
      
   }else {
      int m=0;//v->iMVMode&(~1);
      if(iHas4x4){
         if(vlc->getB())for(j=0;j<4;j++){if(vlc->getB())m|=tabMode[j] ;}
         v->iMVMode=m|1;
      }
      for(i=0;i<16;i++){
         j=i>>2;
         if((tabMode[j] & m)==0){
            c=&v->mv2[j];
            //c=&vr;
            getVec(vlc,2,j,mbx,c,px[rid],py[rid],iIsB);
            px[rid]=c->x;py[rid]=c->y;

            //if(*c!=v->mv2[j])  iDecErrs++;v->mv2[j]=*c;
            //if()
            v->mv4[i]=*c;
            v->mv4[i+1]=*c;
            v->mv4[i+2]=*c;
            v->mv4[i+3]=*c;
            i+=3;  
            continue;
         }
         c=&v->mv4[i];
         getVec(vlc,4,i,mbx,c,px[rid],py[rid],iIsB);   
         px[rid]=c->x;py[rid]=c->y;
         //if(*c!=v->mv4[i])       iDecErrs++;
         
        // v->mv4[i]=*c;
      }
   }
   
   
   
   return vlc->isBitPosOk()?0:-5;   
}

#if  0
int decodeTinaRefs(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int i;

   int iCurIsLast=!iIsB;//iCur+1==iMaxGroup;//only 2 refs
   int iCurRef=-1;
   int iMaxGroup=3;
   //const int tab[3][2]={}
 //  const int tab_c0[]={MB_4::eM1,MB_4::eLast,MB_4::eM2};
   //const int tab_cn[]={MB_4::eLast,MB_4::eM1,MB_4::eM2};
   const int tab_c0[]={-1,iMaxGroup-1,-2};
   const int tab_cn[]={iMaxGroup-1,-1,-2};
   const int *t=iCur==0?&tab_c0[0]:&tab_cn[0];

   int to=vlc->getVlc();   

   for(i=0;i<iCnt;i++,mb4++){
      if(mb4->iIsBi){
         mb4->refId=-1;
         continue;
      }

      if(!to){
         if(iCurIsLast){
            iCurRef=iCurRef==-1?-2:-1;
         }
         else {
            if(iCurRef==t[0])iCurRef=t[1+vlc->getB()];
            else if(iCurRef==t[1])iCurRef=t[vlc->getB()<<1];
            else iCurRef=t[vlc->getB()];
         }
          to=vlc->getVlc();//+i+1; 
      }else to--;
      mb4->refId=iCurRef;
   }
   if(to)
      return -1;
   return 0;
}

int encodeTinaRefs(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int i;

   int iCurIsLast=!iIsB;//iCur+1==iMaxGroup;//only 2 refs
   //int iCurRef=MB_4::eM1;
   int iMaxGroup=1;

//   int to;   
   //const int tab[3][2]={}
   const int tab_c0[]={-1,iMaxGroup-1,-2};
   const int tab_cn[]={iMaxGroup-1,-1,-2};
   const int *t=iCur==0?&tab_c0[0]:&tab_cn[0];

  // int rid;//=MB_4::getID(mb4->refId);
   int iCurRef=-1;
   int rle=0;

   for(i=0;i<iCnt;i++,mb4++){
      if(mb4->iIsBi){
         if(mb4->refId!=-1){
            for(int j=0;j<16;j++)mb4->mv4[j]=mb4->vrefs[1];
            for(int j=0;j<4;j++)mb4->mv2[j]=mb4->vrefs[1];
         }
         mb4->iMVMode=0;
         mb4->r.s[0]=mb4->r.s[1]=0;
         mb4->refId=-1;
         continue;
      }
      int r=mb4->refId>0?0:mb4->refId;
      if(iCurRef!=r){
         vlc->toVLC(rle);
         //rleStat[iRlePos]=rle;iRlePos++;
         
         rle=0;

         if(!iCurIsLast){
            if(iCurRef==t[0])vlc->addB(r==t[2]);//iCurRef=t[1+vlc->getB()];
            else if(iCurRef==t[1])vlc->addB(r==t[2]);
            else vlc->addB(r==t[1]);//iCurRef=t[vlc->getB()];
         }
         iCurRef=r;
      }
      else rle++;
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return 0;
}
int encodeTinaBi(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int i;

   int iIsBi=1;
   int rle=0;

   for(i=0;i<iCnt;i++,mb4++){

      if(iIsBi!=mb4->iIsBi){
         vlc->toVLC(rle);
         //rleStat[iRlePos]=rle;iRlePos++;
         rle=0;
         iIsBi=mb4->iIsBi;
      }
      else rle++;
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return 0;
}

int decodeTinaBi(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int iIsBi=1;
   int to=vlc->getVlc();   
   int i;

   for(i=0;i<iCnt;i++,mb4++){
      if(to==0){
         iIsBi=!iIsBi;
         to=vlc->getVlc(); 
      }
      else to--;
     // if(iCurMode!=(mb4->iMVMode&1))
      mb4->iIsBi=iIsBi;
   }
   if(to)//!=iCnt)
      return -2;
   return vlc->isBitPosOk()?0:-5;
}


int decodeTinaMode(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iHasBi){
   int iCurMode=0;
   int to=vlc->getVlc();   
   int i;

   for(i=0;i<iCnt;i++,mb4++){
      if(iHasBi && mb4->iIsBi){mb4->iMVMode=0;continue;}
      if(to==0){
         iCurMode=!iCurMode;
         to=vlc->getVlc();//+i+1; 
      }else to--;
     // if(iCurMode!=(mb4->iMVMode&1))
       mb4->iMVMode=iCurMode;
   }
  if(to)//!=iCnt)
      return -2;
   return vlc->isBitPosOk()?0:-5;
}
int encodeTinaMode(CTVLCX *vlc, MB_4 *mb4, int iCnt){
   int iCurMode=0;

   int rle=0,i;
   int iHas4x4=0;


   for(i=0;i<iCnt;i++,mb4++){
      if(mb4->iIsBi)continue;
      if(iCurMode!=(mb4->iMVMode&1)){
         vlc->toVLC(rle);
         //rleStat[iRlePos]=rle;iRlePos++;
         iCurMode=mb4->iMVMode&1;
         rle=0;
      }
      else rle++;
      if(mb4->iMVMode>1)iHas4x4=1;
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return iHas4x4;
}

int decodeTinaRot(CTVLCX *vlc, MB_4 *mb4, int xc, int yc){
   int iCurRot=0;
   int cnt=vlc->getVlc();
   int i,j;
   int iHasBi=0;

   for(j=0;j<yc;j++){
      for(i=0;i<xc;i++,mb4++){
         if(mb4->iIsBi)iHasBi++;
         if(mb4->iIsBi || mb4->iMVMode || i==0 || j==0 || i+1==xc || j+1==yc){mb4->r.s[0]=mb4->r.s[1]=0;continue;}
         if(cnt==0){
            iCurRot=!iCurRot;
            cnt=vlc->getVlc();
         }
         else cnt--;
         mb4->r.i=iCurRot;
      }
   }
   if(cnt)
      return -2;
   return vlc->isBitPosOk()?0:-5;
}
int encodeTinaRot(CTVLCX *vlc, MB_4 *mb4, int xc, int yc){
   int iCurRot=0;
   int rle=0;
   int i,j;
   int iHasRot=0;

   for(j=0;j<yc;j++){
      for(i=0;i<xc;i++,mb4++){
         if(mb4->iIsBi || mb4->iMVMode || i==0 || j==0 || i+1==xc || j+1==yc){mb4->r.s[0]=mb4->r.s[1]=0;continue;}

         if(iCurRot != (mb4->r.s[0]|| mb4->r.s[1])){
            iCurRot =(mb4->r.s[0]||mb4->r.s[1]);
            vlc->toVLC(rle);
            //rleStat[iRlePos]=rle;iRlePos++;
            rle=0;

            iHasRot=1;
         }
         else rle++;
      }
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return iHasRot;
}



#else
int decodeTinaRefs(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int i;

   //int iCurIsLast=!iIsB;//iCur+1==iMaxGroup;//only 2 refs
   int iCurRef=-1;
   int iMaxGroup=3;
   //const int tab[3][2]={}
 //  const int tab_c0[]={MB_4::eM1,MB_4::eLast,MB_4::eM2};
   //const int tab_cn[]={MB_4::eLast,MB_4::eM1,MB_4::eM2};
   const int tab_c0[]={-1,iMaxGroup-1,-2};
   const int tab_cn[]={iMaxGroup-1,-1,-2};
   const int *t=iCur==0?&tab_c0[0]:&tab_cn[0];

   int to=vlc->getVlc();   

   for(i=0;i<iCnt;i++,mb4++){
      if(i==to){
         if(!iIsB){
            iCurRef=iCurRef==-1?-2:-1;
         }
         else {
            if(iCurRef==t[0])iCurRef=t[1+vlc->getB()];
            else if(iCurRef==t[1])iCurRef=t[vlc->getB()<<1];
            else iCurRef=t[vlc->getB()];
         }
         to=vlc->getVlc()+i+1; 
      }
      //if(mb4->refId!=iCurRef)
         mb4->refId=iCurRef;
   }
   if(to!=iCnt)
      return -1;
   return vlc->isBitPosOk()?0:-1;
}

int encodeTinaRefs(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int i;

   int iCurIsLast=!iIsB;//iCur+1==iMaxGroup;//only 2 refs
   //int iCurRef=MB_4::eM1;
   int iMaxGroup=1;

//   int to;   
   //const int tab[3][2]={}
   const int tab_c0[]={-1,0,-2};
   const int tab_cn[]={0,-1,-2};
   const int *t=iCur==0?&tab_c0[0]:&tab_cn[0];

  // int rid;//=MB_4::getID(mb4->refId);
   int iCurRef=-1;
   int rle=0;

   for(i=0;i<iCnt;i++,mb4++){
      int r=mb4->refId>0?0:mb4->refId;
      if(iCurRef!=r){
         vlc->toVLC(rle);
         //rleStat[iRlePos]=rle;iRlePos++;
         
         rle=0;

         if(!iCurIsLast){
            if(iCurRef==t[0])vlc->addB(r==t[2]);//iCurRef=t[1+vlc->getB()];
            else if(iCurRef==t[1])vlc->addB(r==t[2]);
            else vlc->addB(r==t[1]);//iCurRef=t[vlc->getB()];
         }
         iCurRef=r;
      }
      else rle++;
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return 0;
}
int encodeTinaBi(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int i;

   int iIsBi=1;
   int rle=0;
//T_CAN_TEST_V
   for(i=0;i<iCnt;i++,mb4++){
#ifdef T_CAN_TEST_V
      if(mb4->refId!=-1)continue;
#endif
      if(mb4->refId==-2 && iIsB)
         continue;

      if(iIsBi!=mb4->iIsBi){
         vlc->toVLC(rle);
         //rleStat[iRlePos]=rle;iRlePos++;
         rle=0;
         iIsBi=mb4->iIsBi;
      }
      else rle++;
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return vlc->isBitPosOk()?0:-5;
}

int decodeTinaBi(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int iIsBi=1;
   int to=vlc->getVlc();   
   int i;

   for(i=0;i<iCnt;i++,mb4++){
#ifdef T_CAN_TEST_V
      if(mb4->refId!=-1){mb4->iIsBi=0;continue;}
#endif
      if(mb4->refId==-2 && iIsB)
      {
         mb4->iIsBi=0;
         continue;
      }
      if(to==0){
         iIsBi=!iIsBi;
         to=vlc->getVlc(); 
      }
      else to--;
     // if(iCurMode!=(mb4->iMVMode&1))
       mb4->iIsBi=iIsBi;
   }
   if(to){
      debugss("bi",to,0);
      return -2;
   }
   return vlc->isBitPosOk()?0:-5;
}


int decodeTinaMode(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iHasBi){
   int iCurMode=0;
   int to=vlc->getVlc();   
   int i;

   for(i=0;i<iCnt;i++,mb4++){
      if(iHasBi && mb4->iIsBi){mb4->iMVMode=0;continue;}
      if(to==0){
         iCurMode=!iCurMode;
         to=vlc->getVlc();//+i+1; 
      }else to--;
     // if(iCurMode!=(mb4->iMVMode&1))
       mb4->iMVMode=iCurMode;
   }
   if(to){
      debugss("mode",to,0);
      return -2;
   }
   
   return vlc->isBitPosOk()?0:-5;
}
int encodeTinaMode(CTVLCX *vlc, MB_4 *mb4, int iCnt){
   int iCurMode=0;

   int rle=0,i;
   int iHas4x4=0;


   for(i=0;i<iCnt;i++,mb4++){
      if(mb4->iIsBi)continue;
      if(iCurMode!=(mb4->iMVMode&1)){
         vlc->toVLC(rle);
         //rleStat[iRlePos]=rle;iRlePos++;
         iCurMode=mb4->iMVMode&1;
         rle=0;
      }
      else rle++;
      iHas4x4|=mb4->iMVMode;
      //if(mb4->iMVMode>1)=1;
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return iHas4x4>1;
}

int decodeTinaRot(CTVLCX *vlc, MB_4 *mb4, int xc, int yc){
   int iCurRot=0;
   int cnt=vlc->getVlc();
   int i,j;
 //  int iHasBi=0;if(mb4->iIsBi)iHasBi++;

   for(j=0;j<yc;j++){
      for(i=0;i<xc;i++,mb4++){
         
         if(mb4->iIsBi || mb4->iMVMode || i==0 || j==0 || i+1==xc || j+1==yc){ mb4->r.i=0;continue;}
         if(cnt==0){
            iCurRot=!iCurRot;
            cnt=vlc->getVlc();
         }
         else cnt--;
         mb4->r.i=iCurRot;
      }
   }
   if(cnt){
      debugss("rot",cnt,0);
      return -2;
   }
   return 0;
}
int encodeTinaRot(CTVLCX *vlc, MB_4 *mb4, int xc, int yc){
   int iCurRot=0;
   int rle=0;
   int i,j;
   int iHasRot=0;

   for(j=0;j<yc;j++){
      for(i=0;i<xc;i++,mb4++){
         if(mb4->iIsBi || mb4->iMVMode || i==0 || j==0 || i+1==xc || j+1==yc){mb4->r.i=0;continue;}

         if(iCurRot != (!!mb4->r.i)){
            iCurRot =(!!mb4->r.i);
            vlc->toVLC(rle);
            //rleStat[iRlePos]=rle;iRlePos++;
            rle=0;

            iHasRot=1;
         }
         else rle++;
      }
   }
   vlc->toVLC(rle);
   //rleStat[iRlePos]=rle;iRlePos++;
   return iHasRot;
}


#endif

static inline int getMode(MB_4 *m){
   
   if(m->iIsBi)return 0;
   int r=MB_4::getID(m->refId);
   r++;
   if(m->r.i)r+=6;
   else if(m->iMVMode)r+=3;
   return r;
}


typedef struct {
   CTVLCX *vlc;
   CMB4 *mbCtx;
}TINA_M_STR;

MB_4 *encRMB=NULL;
//#include "t_aritm.inl"
int decodeTinaMotion(void *pCtx, int &iMBSize2, int iCur, int iIsB, int w, int h, unsigned char *pBin, int &iLen, int iMaxBytesIn){
   iDecErrs=0;
   TINA_M_STR *tm=(TINA_M_STR*)pCtx;
   
   CMB4 *mbCtx=tm->mbCtx;

   int i,j;
   CTVLCX &vlc=*tm->vlc;
   vlc.reset();
   vlc.iCalcBitsOnly=0;
   
   vlc.setDataBuf(pBin, iMaxBytesIn);
   
   
   
   iMBSize2=(vlc.getVlc()+1)<<2;
   if(iMBSize2>128 || iMBSize2<4)return -1;
   //int zz=vlc.getVlc();
   int iHasRot=vlc.getB();
   int iHas4x4=vlc.getB();
   int iHasB=vlc.getB();;
   int xc=w/iMBSize2;
   int yc=h/iMBSize2;

   iDecErrs=iDecErrF=0;

   mbCtx->init(w,h,iMBSize2);

   MB_4 *mb4=mbCtx->mb;
   int r;
#if 1
   //if( enc refs nB)vlc (n0_cnt)
   //if( enc refs B)vlc (n0_cnt,n2_cnt)
   do{
      r=decodeTinaRefs(&vlc,mb4,xc*yc,iCur, iIsB);if(r<0)break;
      if(iHasB){
         r=decodeTinaBi(&vlc,mb4,xc*yc,iCur, iIsB);if(r<0)break;}
      else{
         int to=xc*yc;
         for(j=0;j<to;j++)mb4[j].iIsBi=0;
      }
      r=decodeTinaMode(&vlc,mb4,xc*yc,iHasB);if(r<0)break;
      if(iHasRot)
         r=decodeTinaRot(&vlc,mb4,xc,yc);
   }while(0);
   //
//   debugsi("re",vlc.iBitPos*100/(mb4->xc*mb4->yc));
  // debugsi("re",vlc.iBitPos*100/(mb4->xc*mb4->yc));
   if(r || iDecErrs){//            if(iDecErrF)return -101;

      printf("dec err %d bp=%d ok=%d in=%d\n",r,vlc.getBytePos(), vlc.isBitPosOk(), iMaxBytesIn);
      debugss("dec err ", r,iDecErrs);

      return r;
   }
//   return r+1;
#else
   do{
      if(iHasB){
         r=decodeTinaBi(&vlc,mb4,xc*yc,iCur, iIsB);
         if(r<0)break;
      }
      else{
         int to=xc*yc;
         for(j=0;j<to;j++)mb4[j].iIsBi=0;
      }
      r=decodeTinaRefs(&vlc,mb4,xc*yc,iCur, iIsB);if(r<0)break;
      r=decodeTinaMode(&vlc,mb4,xc*yc,iHasB);if(r<0)break;
      if(iHasRot)
         r=decodeTinaRot(&vlc,mb4,xc,yc);
   }while(0);
   if(r){
      debugss("dec err ", r,iDecErrs);

      return r;
   }
#endif
   mb4=mbCtx->mb;

   int px[3]={0,0,0};
   int py[3]={0,0,0};

#ifdef T_CAN_TEST_V
   skXDec.reset();
   skXDec.decBlocks(&vlc);
#endif
   for(j=0;j<yc;j++)
   {
      for(i=0;i<xc;i++,mb4++){
         if(!iHasRot)mb4->r.i=0;

            mb4->iBlockMode=getMode(mb4);
        // mb4->iIsBi=0;
         MB_4 *mbx[9];//={NULL,NULL,NULL,NULL,NULL,NULL,NULL};
         loadNearMB(mb4,mbx);

         int iV=1;
#ifdef T_CAN_TEST_V
         /*
         if(!iIsB && mb4->iBlockMode==mb4->eRef){
            iV=skXDec.isV();
            if(!iV){
               mb4->vrefs[1]=mb4->mv_eM1_dec_enc;
               mb4->mv2[0]=mb4->vrefs[1];
               for(int zz=0;zz<16;zz++)mb4->mv4[zz]=mb4->vrefs[1];
               px[1]=mb4->vrefs[1].x;py[1]=mb4->vrefs[1].y;
            }
         }else 

*/
         if(iIsB && mb4->iIsBi){
            iV=skXDec.isV();
            if(!iV){
               getPredMV_1x1_bbi(&mb4->vrefs[1],&mb4->vrefs[2],mb4,i,j,0);
               mb4->mv2[0]=mb4->vrefs[1];
               for(int zz=0;zz<16;zz++)mb4->mv4[zz]=mb4->vrefs[1];
               px[1]=mb4->vrefs[1].x;py[1]=mb4->vrefs[1].y;
               px[2]=mb4->vrefs[2].x;py[2]=mb4->vrefs[2].y;
               
            }
         }
#endif
         if(iV){
            r=::decMB_MV(&vlc,px,py,mbx,iHas4x4,iIsB);if(r<0)return r;
            if(mb4->iIsBi){
               r=decMB_B(&vlc,px,py,mbx,iIsB,0);if(r<0)return r;
            }
            if(iDecErrF)return -101;
         }

#ifdef T_CAN_TEST_V
         if(iIsB  && encRMB)
         {
            /*
            MB_4 *mec=&encRMB[i+j*xc];
               void debugsi(char *c, int a);
            if(mb4->iIsBi && (mec->vrefs[1]!=mb4->vrefs[1] || mec->vrefs[2]!=mb4->vrefs[2])){
               debugsi("ie1---------",(mec->vrefs[1]==mb4->vrefs[1]));
               debugsi("ie2---------",(mec->vrefs[2]==mb4->vrefs[2]));
            }
            if(mec->iBlockMode!=mb4->iBlockMode){
               debugsi("bm---------",-i);
            }
            else if(mec->iIsBi!=mb4->iIsBi){
               debugsi("bmx1---------",-i);
            }
            else if(mec->iMVMode!=mb4->iMVMode){
               debugsi("bmx2---------",-i);
            }
            else if(MB_4::getID(mec->refId)!=MB_4::getID(mb4->refId)){
               debugsi("bmx3---------",mb4->iBlockMode);
            }
            else if(mec->r.i!=mb4->r.i){
               debugsi("bmx---------",-i);
            }
            */
         }
#endif
         if(!iIsB){
            if(!mb4->iIsBi && mb4->refId!=-1){mb4->mv_eM1_gr.x=mb4->mv_eM1_gr.y=0;mb4->mv_eM1_gr.iSad=5;}
            else{
               if(mb4->iIsBi || mb4->refId==-1)mb4->mv_eM1_gr=mb4->vrefs[mb4->eM1];
               else if(mb4->iMVMode){
                  mb4->mv_eM1_gr.x=(mb4->mv4[0].x+mb4->mv4[4].x+mb4->mv4[8].x+mb4->mv4[12].x+2)>>2;
                  mb4->mv_eM1_gr.y=(mb4->mv4[0].y+mb4->mv4[4].y+mb4->mv4[8].y+mb4->mv4[12].y+2)>>2;
               }
            }
         }
         else if(mb4->iIsBi && mb4->mv_eM1_gr.iSad==5){
            mb4->mv_eM1_gr.x=mb4->vrefs[mb4->eM1].x-mb4->vrefs[mb4->eNext].x;
            mb4->mv_eM1_gr.y=mb4->vrefs[mb4->eM1].y-mb4->vrefs[mb4->eNext].y;
         }
#ifdef T_CAN_TEST_V
         if(!iIsB){
            mb4->mv_eM1_dec_enc.iSad=0;
        //    mb4->iBlockMode=getMode(mb4);
            
            if((mb4->iBlockMode==mb4->eRef_m)){// || mb4->iBlockMode==MB_4::eRMR_last+4)){
               /*
// 0  1  4  5 
// 2  3  6  7 
// 8  9 12 13
//10 11 14 15
               */
               mb4->mv_eM1_dec_enc.iSad=1;
               mb4->mv_eM1_dec_enc.x=(mb4->mv4[0].x+mb4->mv4[5].x+mb4->mv4[15].x+mb4->mv4[10].x+2)>>2;
               mb4->mv_eM1_dec_enc.y=(mb4->mv4[0].y+mb4->mv4[5].y+mb4->mv4[15].y+mb4->mv4[10].y+2)>>2;
            }
            else {
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eM1];
               mb4->mv_eM1_dec_enc.iSad=mb4->iBlockMode==mb4->eRef_r?2:(mb4->iBlockMode==mb4->eRef || mb4->iBlockMode==mb4->eBi);
               if(mb4->mv_eM1_dec_enc.iSad==0)mb4->mv_eM1_dec_enc.y=mb4->mv_eM1_dec_enc.x=0;
            }
//            mb4->mv_eM1_dec_enc.y=mb4->mv_eM1_dec_enc.x=0;
         }
         /*
         else if(iIsB && mb4->iBlockMode<4){
            if(mb4->iBlockMode==mb4->eRef){
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eM1];
               mb4->mv_eM1_dec_enc.x*=2;
               mb4->mv_eM1_dec_enc.y*=2;
            }
            else if(mb4->iBlockMode==mb4->eRefN){
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eNext];
               mb4->mv_eM1_dec_enc.x*=-2;
               mb4->mv_eM1_dec_enc.y*=-2;
            }
            else if(mb4->iBlockMode==mb4->eBi){
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eM1];
               mb4->mv_eM1_dec_enc.x-=mb4->vrefs[mb4->eNext].x;
               mb4->mv_eM1_dec_enc.y-=mb4->vrefs[mb4->eNext].y;
            }

         }
         */

#endif

         
         
         /*
         if(0&&iHasB && iIsB && mb4->refId!=-2 && mb4->iMVMode==0 && mgabs(mb4->r.s[0]|mb4->r.s[1])<9){
            mb4->iIsBi=vlc.getB();
            if(mb4->iIsBi)decMB_B(&vlc,px,py,mb4);
         }
         */
         /*
         if(!iIsB){
            if(mb4->refId!=-1 || mb4->iMVMode)decMB_nonB_m1(&vlc,px,py,mb4);
            mb4->mv_eM1_gr=mb4->vrefs[mb4->eM1];
         }
         */

      }
   }
   int bp=vlc.getBytePos();
   
   if(iLen==0){
      iLen=bp;
   }
   else if(iLen!=bp){
      debugss("dec err  len -------------------------------------------------------", -4,iDecErrs);
      return -4;
   }
   if(iDecErrs){debugss("--------------------------------------",iDecErrs,0);printf("[dec iDecErrs=%d]",iDecErrs);}
/*
   static int iSaved=0;
   if((!iSaved && iDecErrs)||iLen==6760){
   void debugVectors(void *ctx, int MVSize,int iCur, int iIsB,int w, int h);
   debugVectors(pCtx, iMBSize2,  iCur,  iIsB,  w, h);
   iSaved=1;
   }
   */
   return 0;

}
static inline void getModeC(MB_4 *m, int &bi, int &m2, int &m1, int &n, int &mode, int &r, int &m4x4){
   
   if(m->iIsBi){bi++;m1++;}
   else {
      if(m->iMVMode){mode++;if(m->iMVMode>1)m4x4++;}
      else if(m->r.i)r++;
      if(m->refId==-2)m2++;
      else if(m->refId==-1)m1++;
      else n++;
   }
}

int getMvGain(){


   return 0;
   
}
void resetMV(void *pCtx, int iMBSize2, int w, int h){
   //debugsi("rmv",(int)pCtx);
   //return;
   TINA_M_STR *tm=(TINA_M_STR*)pCtx;
   CMB4 *mbCtx=tm->mbCtx;
   mbCtx->init(w,h,iMBSize2);
   int xc=w/iMBSize2;
   int yc=h/iMBSize2;
   int i;
   int c=xc*yc;
   MB_4 *mb4=mbCtx->mb;
   for(i=0;i<c;i++){
      mb4->mv_eM1_dec_enc.y=mb4->mv_eM1_dec_enc.x=0;
      mb4->mv_eM1_dec_enc.iSad=0;
      mb4++;
   }
   
}
int encMB_MV_C(CTVLCX  * vlc,MB_4 *mbx[], int *px, int *py, int iHas4x4, int iIsB){
//   T_CUR_VECTOR va;
   
   MB_4 *v=mbx[0];
   int rid=v->getID(v->refId);   

   T_CUR_VECTOR *c;
   int i,j;
   
   if(v->iMVMode==0)
   {
      c=&v->vrefs[rid];
      //if(v->iUpdateed==77)
        // v->iUpdateed=78;
      
      
      saveVec(vlc,1,0,mbx,c,px[rid],py[rid],  iIsB);
      px[rid]=c->x;py[rid]=c->y;
      if(v->r.i)
      {

         int a1=0;
         int a2=0;
         getPredRot(v,mbx,a1,a2);         
#if 0
         //def T_CAN_TEST_V     
         if(v->r.s[0]){
            vlc->addB(1);
            
            if(a1)vlc->toVLC_PNS(v->r.s[0]-a1,3);else
            {
               vlc->toVLC_X(mgabs(v->r.s[0])-1,2);
               vlc->addB(v->r.s[0]<0);
            }
            
            
         }
         else{
            vlc->addB(0);
            
            if(a2)vlc->toVLC_PNS(v->r.s[1]-a2,3);else
            {
               vlc->toVLC_X(mgabs(v->r.s[1])-1,2);
               vlc->addB(v->r.s[1]<0);
            }
         }
#else

         if(v->r.s[0]){
            vlc->addB(1);
            
            if(a1)vlc->toMV(v->r.s[0]-a1);else
            {
               vlc->toVLC(mgabs(v->r.s[0])-1);
               vlc->addB(v->r.s[0]<0);
            }
            
            
         }
         else{
            vlc->addB(0);
            
            if(a2)vlc->toMV(v->r.s[1]-a2);else
            {
               vlc->toVLC(mgabs(v->r.s[1])-1);
               vlc->addB(v->r.s[1]<0);
            }
         }
#endif


         //if(vlc->getVlc()!=0x39)iDecErrs++;
//vlc->toVLC(0x39);
         

      }
      
   }else {
      int m=v->iMVMode&(~1);
      if(iHas4x4){
         vlc->addB(m);
         if(m)for(j=0;j<4;j++){vlc->addB(tabMode[j] & m);}
      
        
         //vlc->toVLC(v->iMVMode>>1);
      }
      for(i=0;i<16;i++){
         j=i>>2;
         if((tabMode[j] & m)==0){
            i+=3;  
            c=&v->mv2[j];
            saveVec(vlc,2,j,mbx,c,px[rid],py[rid],  iIsB);
            px[rid]=c->x;py[rid]=c->y;
            continue;
         }
         c=&v->mv4[i];
         saveVec(vlc,4,i,mbx,c,px[rid],py[rid],  iIsB);   
         px[rid]=c->x;py[rid]=c->y;
      }
      
      
   }
   
   
   return 0;   
}

int countMVBits(MB_4 *mb4, int iIsB){
   MB_4 *mbx[9];//={NULL,NULL,NULL,NULL,NULL,NULL,NULL};
   int px[3]={0,0,0};
   int py[3]={0,0,0};
   static CTVLCX vlc;
   vlc.reset();
   loadNearMB(mb4,mbx);
   ::encMB_MV_C(&vlc,mbx,px,py,1,iIsB);
   if(mb4->iIsBi)encMB_B(&vlc,px,py,mbx,iIsB,0);

   return vlc.iBitPos+1;
}

int encodeTinaMotion(void *pCtx, int iMBSize2, int iCur, int  iIsB, int w, int h, unsigned char *pBin, int &iLen){
   TINA_M_STR *tm=(TINA_M_STR*)pCtx;
   CMB4 *mbCtx=tm->mbCtx;
//debugss("enc m",0,0);
   int i,j;
   CTVLCX &vlc=*tm->vlc;
   vlc.reset();
   vlc.iCalcBitsOnly=0;
   vlc.pBitBuf=pBin;

   int xc=w/iMBSize2;
   int yc=h/iMBSize2;
   //mbCtx->init(w,h,iMBSize2);

   MB_4 *mb4=mbCtx->mb;
   encRMB=mb4;

   //rci();

   vlc.toVLC((iMBSize2>>2)-1);
   //vlc.toVLC(0);
   int iBitPosR=vlc.iBitPos;
   vlc.addB(0);//has rot
   vlc.addB(0);//has 4x4
   int iHasB=0;
   int iRCnt=0;
   int iMCnt=0;
   //iIsBX=iIsB;
   for(j=0;j<yc;j++)
   {
      for(i=0;i<xc;i++,mb4++){
         /*
         mb4->iIsBi=0;
         mb4->refId=-1;
         mb4->iHada[0]=mb4->iHada[1]=mb4->iHada[2]=-1;
         mb4->vrefs[1].x=mb4->vrefs[1].y=0;
         mb4->iMVMode=0;
         mb4->r.s[0]=mb4->r.s[1]=0;
         for(int z=0;z<16;z++)mb4->mv4[z]= mb4->vrefs[1];
         for(int z=0;z<4;z++)mb4->mv2[z]= mb4->vrefs[1];

         continue;
         */
         if((mb4->r.i)|| mb4->iMVMode)mb4->iIsBi=0;
         
         else if(mb4->iIsBi){
            iHasB++;
#ifdef T_CAN_TEST_V            
            if(mb4->refId!=-1){
               mb4->refId=-1;
            for(int z=0;z<16;z++)mb4->mv4[z]= mb4->vrefs[1];
            for(int z=0;z<4;z++)mb4->mv2[z]= mb4->vrefs[1];
            //TODO fix
            }
#endif
            

         }

         if(mb4->iMVMode)iMCnt++;
         else if((mb4->r.i))iRCnt++;
         //mb4->iBlockMode=getMode(mb4);
         //mb4->iBlockDC=0;
      }
   }
   mb4=mbCtx->mb;
   
   int r;

   vlc.addB(iHasB);//has-b

   int iBitPosDbg=vlc.iBitPos;
   //iRlePos=0;
#if 1
   r=encodeTinaRefs(&vlc,mb4,xc*yc,iCur, iIsB);

//void debugsi(char *c, int a);
   //int bpx=vlc.iBitPos;
   int iBPBi=vlc.iBitPos;
   if(iHasB)encodeTinaBi(&vlc,mb4,xc*yc,iCur, iIsB);
   int iRefBiBits=vlc.iBitPos-iBitPosDbg;
   int iBiBits=vlc.iBitPos-iBPBi;
   //debugsi("b",vlc.iBitPos-bpx);
#else
   if(iHasB)encodeTinaBi(&vlc,mb4,xc*yc,iCur, iIsB);
   r=encodeTinaRefs(&vlc,mb4,xc*yc,iCur, iIsB);
#endif
   int iModeBits=vlc.iBitPos;
   int iHas4x4=encodeTinaMode(&vlc,mb4,xc*yc);
   if(iHas4x4)vlc.setBit(iBitPosR+1,1);



   int iBeforeRot=vlc.iBitPos;
   int iHasRot=encodeTinaRot(&vlc,mb4,xc,yc);
   if(iHasRot)vlc.setBit(iBitPosR,iHasRot);else vlc.iBitPos=iBeforeRot;

   iModeBits=vlc.iBitPos-iModeBits;
   int icm[]={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,};
   int iLeft=xc*yc;
   for(j=0;j<iLeft;j++){mb4[j].iBlockDC=0;mb4[j].iBlockMode=getMode(&mb4[j]);icm[mb4[j].iBlockMode]++;}//if(mb4[j].iBlockMode==mb4->eRef)iRefCnt++;}


#if 0
   //def T_CAN_TEST_V
   void debugs2i(char *c, int a, int b);
   void debugsi(char *c, int a);
   //bi,ref,refN,refM2,ref-r,refN-r,refM2-r,ref-m,refN-m,refM2-m

   int findScoreMode(int val, int tid, int iAdd);
   int iLenN=vlc.iBitPos-iBitPosR;
   int iSkipCnt=0;
   int iBCnt=iHasB;
   if(1||iIsB)
   {
      iLenN=0;
   
   int iLeft=xc*yc;
   iLenN+=vlc.bitsLen[iMCnt];
   iLenN+=vlc.bitsLen[iRCnt];
   iLenN+=vlc.bitsLen[iIsB?(iLeft-iMCnt-iRCnt-iBCnt):iBCnt];
   int iRefCnt=0;
   int iRefNCnt=0;
   int iRefM2Cnt=0;

   iRefCnt=icm[MB_4::eRef];
   iRefNCnt=icm[MB_4::eRefN];
   iRefM2Cnt=icm[MB_4::eRefM2];
#if 1
   iRefCnt=icm[MB_4::eRef]+icm[MB_4::eRef_m]+icm[MB_4::eRef_r];
   iRefNCnt=icm[MB_4::eRefN]+icm[MB_4::eRefN_m]+icm[MB_4::eRefN_r];
   iRefM2Cnt=icm[MB_4::eRefM2]+icm[MB_4::eRefM2_m]+icm[MB_4::eRefM2_r];
   //int iBiRef=(!iIsB)*iBCnt;

   int iLFT=iLeft;
   int iPrevM=0;
   if(1){iLenN=iModeBits+vlc.bitsLen[iIsB?(iLeft-iBCnt):iBCnt];}else
   if(!iIsB ||1)iLenN+=vlc.bitsLen[iLeft-iBCnt-iRefCnt];
   debugsi("mlen",iLenN);
   if((!iIsB ||1) && iRefCnt!=iLeft && iBCnt!=iLeft){
      iSkipCnt=0;
      iPrevM=iIsB?3:1;
      iLenN+=vlc.bitsLen[iLeft-iBCnt-iRefCnt-iRefM2Cnt];
      if(iIsB)iLenN+=vlc.bitsLen[iLeft-iBCnt-iRefCnt-iRefNCnt-iRefM2Cnt];
      //if(!iIsB){iLenN+=iBiBits;iBCnt=0;}
//0 1 2 3  0 xc 1 xc+1
      int iPredM=0;
      //if(iMCnt){iLen+=vlc.bitsLen[iMCnt-icm[MB_4::eRef_m]];iLen+=vlc.bitsLen[iMCnt-icm[MB_4::eRef_m]];iLen+=vlc.bitsLen[iMCnt-icm[MB_4::eRef_m]];}
      for(i=0;i<iLFT && (iRefCnt || iBCnt || iRefNCnt || iRefM2Cnt);i++){
         //int ii=(i+xc<iLFT)?((i&1)*xc+(i>>1)):i;
            MB_4 *mc=&mb4[i];
//mc->iIsBi && iBCnt>0?3:
            if(iRefCnt+i>=iLFT || iRefM2Cnt+i>=iLFT || iRefNCnt+i>=iLFT || iBCnt+i>=iLFT)break;
            int iM=mc->iIsBi?3:mc->getID(mc->refId);
            if(!mc[0].iBlockDC){
         
         //int iTRE=(i>xc && iM!=mc[1-xc].iIsBi?3:mc->getID(mc[1-xc].refId));  
         //if(iPrevM==iM && iTRE && iPredM==iTRE){iLenN++;iSkipCnt=0;}else

         if(iPrevM!=iM){
            //iSkipCnt++;
            int iC=(!!iRefCnt)+(!!iBCnt)+(!!iRefM2Cnt)+(!!iRefNCnt);

          //  iPredM=iTRE;
            //iSkipCnt--;
               
            iLenN+=vlc.bitsLen[iSkipCnt];iSkipCnt=0;//00 01 10 11,1 00 010 011
            if(iC<=2){}else if(iC==3)iLenN++;else{iLenN+=2;}
            if(iC<2 || iRefCnt+i>=iLFT || iRefM2Cnt+i>=iLFT || iRefNCnt+i>=iLFT || iBCnt+i>=iLFT)break;
            iPrevM=iM;
            /*
            if((iIsB && iM==3) || (!iIsB && iM==1)){
            iLenN++;
            
            if(i+xc*3+3<iLeft){
              // int xc=mc->xc;
            const int refx[]={0,1,2,3,1,2,3,1,2,3,};
               if(refx[mc->iBlockMode]==refx[mc[1].iBlockMode] //&& refx[mc->iBlockMode]==refx[mc[2].iBlockMode]
                  && refx[mc->iBlockMode]==refx[mc[xc].iBlockMode] && refx[mc->iBlockMode]==refx[mc[1+xc].iBlockMode]// && refx[mc->iBlockMode]==refx[mc[2+xc].iBlockMode]
//                  && refx[mc->iBlockMode]==refx[mc[xc*2].iBlockMode] && refx[mc->iBlockMode]==refx[mc[1+xc*2].iBlockMode] && refx[mc->iBlockMode]==refx[mc[2+2*xc].iBlockMode]
                  ){
                  mc[0].iBlockDC|=4;mc[1].iBlockDC|=4;//mc[2].iBlockDC|=4;
                  mc[xc].iBlockDC|=4;mc[xc+1].iBlockDC|=4;//mc[xc+2].iBlockDC|=4;;
                  //mc[xc*2].iBlockDC|=4;mc[xc*2+1].iBlockDC|=4;mc[xc*2+2].iBlockDC|=4;;
                  }
            }
            }
            */
            /*
            const int refx[]={0,1,2,3,1,2,3,1,2,3,};
            if(//!iIsB && 
               i+xc*2+2<iLFT  && ((iM==3 && iIsB) || (iM==1 && !iIsB))){
               if(
               refx[mc->iBlockMode]==refx[mc[1].iBlockMode] && refx[mc->iBlockMode]==refx[mc[2].iBlockMode]
                  && refx[mc->iBlockMode]==refx[mc[xc].iBlockMode] && refx[mc->iBlockMode]==refx[mc[1+xc].iBlockMode] && refx[mc->iBlockMode]==refx[mc[2+xc].iBlockMode]
                  && refx[mc->iBlockMode]==refx[mc[xc*2].iBlockMode] && refx[mc->iBlockMode]==refx[mc[1+xc*2].iBlockMode] && refx[mc->iBlockMode]==refx[mc[2+2*xc].iBlockMode]){
            
                  mc[0].iBlockDC=mc[1].iBlockDC=mc[2].iBlockDC=3;
                  mc[xc].iBlockDC=mc[xc+1].iBlockDC=mc[xc+2].iBlockDC=3;
                  mc[xc*2].iBlockDC=mc[xc*2+1].iBlockDC=mc[xc*2+2].iBlockDC=3;

            }
                  if(iC!=4)iLenN++;
            }
            */
         }
         else iSkipCnt++;
            }
         if(iM==3)iBCnt-=1;
         else if(iM==0)iRefM2Cnt-=1;
         else if(iM==1)iRefCnt-=1;
         else if(iM==2)iRefNCnt-=1;

//         if(iBCnt<0 || iRefM2Cnt<0 || iRefCnt<0 || iRefNCnt<0)debugsi("err-----",-1);
      }
      if((iBCnt || iRefM2Cnt || iRefCnt || iRefNCnt) && (iLeft-i)!=(iRefM2Cnt|iRefCnt|iRefNCnt|iBCnt)){
//         debugsi("err-----",iLeft-i);debugsi("err-----",iRefM2Cnt|iRefCnt|iRefNCnt|iBCnt);
      }
   }
   }
   debugsi("iRefBiBits",iRefBiBits);
#else
   iLenN+=vlc.bitsLen[iLeft-iMCnt-iRCnt-iBCnt-iRefCnt];
   for(j=0;j<yc;j++)
   {
      for(i=0;i<xc;i++){
         // || iRefM2Cnt==iLeft || iRefNCnt==iLeft
         if(//!iIsB &&
            (iBCnt==iLeft || iRCnt==iLeft || iMCnt==iLeft || iRefCnt==iLeft)){
               
 //              if(iLeft>xc){int cm=mb4[i+xc*j].iBlockMode;  findScoreMode(cm,(cm+cm*16)+(!!iIsB)*256,xc/2+1);}

               iSkipCnt=0;j=yc;i=xc;break;
         }
         MB_4 *mc=&mb4[i+xc*j];
         if(mc->iBlockDC){
            continue;
         }
         int cm=mc->iBlockMode;
         int iCMS=cm;
         int iLeftR=1;
         int iPredMLeft;
            int tlm=1;
            int trm=1;
            int iMa;
#if 1
         //bi,ref,refN,refM2,ref-r,refN-r,refM2-r,ref-m,refN-m,refM2-m
         int sc;
         /*
         if(0){
            int bi=0,m2=0,m1=0,n=0,mode=0,r=0,m4x4=0;
            int scx=0;
//            getModeC(MB_4 *m, int &bi, int &m2, int &m1, int &n, int &mode, int &r);
            if(i)getModeC(mc-1,bi,m2,m1,n,mode,r,m4x4);else scx++;
            if(j)getModeC(mc-xc,bi,m2,m1,n,mode,r,m4x4);else scx++;
            if(i+1<xc)getModeC(mc+1,bi,m2,m1,n,mode,r,m4x4);else scx++;
            if(j+1<yc)getModeC(mc+xc,bi,m2,m1,n,mode,r,m4x4);else scx++;
            if(bi>3)bi=3;if(m2>3)m2=3;if(m1>3)m1=3;if(n>3)n=3;if(mode>3)mode=3;if(r>3)r=3;if(m4x4>3)m4x4=3;
            //if(scx)
            if(!iIsB)n=m4x4;
            else if(!iHasRot)r=m4x4;
            
            int fl=bi|(mode<<2)|(r<<4)|(n<<6)|(m1<<8)|(m2<<10)|((!!iIsB)<<11)|((!!iHasB)<<12)|((!!iHasRot)<<13);//|((!!iHas4x4)<<14);
            sc=findScoreMode(cm,fl);
            
         }
         else*/
         {
        
            int tm=MB_4::eRMR_last+5;
            int lm=MB_4::eRMR_last+5;
            if(j && i){tm=mc[-xc].iBlockMode;lm=mc[-1].iBlockMode;tlm=mc[-1-xc].iBlockMode>3 && mc[-1-xc].iBlockMode<MB_4::eRMR_last?1:0;}
            else if(j)tm=mc[-xc].iBlockMode;
            else if(i)lm=mc[-1].iBlockMode;
            if(j && i+1<xc)trm=mc[1-xc].iBlockMode>3 && mc[1-xc].iBlockMode<MB_4::eRMR_last?1:0;
            int iCanBeRot=iRCnt>0 && (i && j && i+1!=xc && j+1!=yc);
//            iPredMLeft=(iMCnt+(iCanBeRot?iRCnt:0)+iBCnt*(!iIsB))*2>(iLeft-iRefCnt*(!iIsB));//iRefCnt
            iPredMLeft=iIsB?((iMCnt+(iCanBeRot?iRCnt:0)+iRefCnt)*2>iBCnt):((iMCnt+(iCanBeRot?iRCnt:0)+iBCnt)*2>iRefCnt);
#if 1
            if(//!iIsB && 
               i+2<xc && j+2<yc && mc->iBlockMode<4){  
                  //&& mc->iBlockMode==mc[1].iBlockMode && mc->iBlockMode==mc[xc].iBlockMode && mc->iBlockMode==mc[1+xc].iBlockMode
//iPredMLeft=1;
                  if(mc->iBlockMode==mc[1].iBlockMode && mc->iBlockMode==mc[2].iBlockMode
                  && mc->iBlockMode==mc[xc].iBlockMode && mc->iBlockMode==mc[1+xc].iBlockMode && mc->iBlockMode==mc[2+xc].iBlockMode
                  && mc->iBlockMode==mc[xc*2].iBlockMode && mc->iBlockMode==mc[1+xc*2].iBlockMode && mc->iBlockMode==mc[2+2*xc].iBlockMode){
/*              
                     const int m2=iIsB?2:0;
               if(iIsB && 
                  mc->iIsBi //&& mc[1].iIsBi && mc[xc].iIsBi && mc[1+xc].iIsBi 
                  && mc->vrefs[1]==mc[1].vrefs[1] && mc->vrefs[2]==mc[1].vrefs[2]
                  && mc->vrefs[1]==mc[xc].vrefs[1] && mc->vrefs[2]==mc[xc].vrefs[2]
                  && mc->vrefs[1]==mc[xc+1].vrefs[1] && mc->vrefs[2]==mc[xc+1].vrefs[2]
                  ){
                  mc[1].iBlockDC=mc[xc].iBlockDC=mc[1+xc].iBlockDC=1;
                  cm=MB_4::eRMR_last;
                  mc[xc+1].iBlockMode=mc[xc].iBlockMode=mc[1].iBlockMode=mc[0].iBlockMode=cm;
                  iLeftR=4;
               }
               else //if(iPredMLeft)
               */
               {
                  //iLeftR=4;                  mc[1].iBlockDC=mc[xc].iBlockDC=mc[1+xc].iBlockDC=3;
                  iLeftR=9;
                  mc[0].iBlockDC|=4;mc[1].iBlockDC|=4;mc[2].iBlockDC|=4;
                  mc[xc].iBlockDC|=4;mc[xc+1].iBlockDC|=4;mc[xc+2].iBlockDC|=4;;
                  mc[xc*2].iBlockDC|=4;mc[xc*2+1].iBlockDC|=4;mc[xc*2+2].iBlockDC|=4;;
                  cm+=10;

                  mc[0].iBlockMode=mc[1].iBlockMode=mc[2].iBlockMode=cm;
                  mc[xc].iBlockMode=mc[xc+1].iBlockMode=mc[xc+2].iBlockMode=cm;
                  mc[xc*2].iBlockMode=mc[xc*2+1].iBlockMode=mc[xc*2+2].iBlockMode=cm;

               }
                  }
                  /*
               else if(mc->iBlockMode==MB_4::eRef
                  && mc->vrefs[1]==mc[1].vrefs[1] 
                  && mc->vrefs[1]==mc[xc].vrefs[1] 
                  && mc->vrefs[1]==mc[xc+1].vrefs[1] 
                  ){
                  mc[1].iBlockDC=mc[xc].iBlockDC=mc[1+xc].iBlockDC=1;
                  cm=MB_4::eRMR_last+1;
                  mc[xc+1].iBlockMode=mc[xc].iBlockMode=mc[1].iBlockMode=mc[0].iBlockMode=cm;
                  iLeftR=4;
               }
               else if((mc->iBlockMode==MB_4::eRefN || (!iIsB && mc->iBlockMode==MB_4::eRefM2))
                  && mc->vrefs[m2]==mc[1].vrefs[m2] 
                  && mc->vrefs[m2]==mc[xc].vrefs[m2] 
                  && mc->vrefs[m2]==mc[xc+1].vrefs[m2] 
                  ){
                  mc[1].iBlockDC=mc[xc].iBlockDC=mc[1+xc].iBlockDC=1;
                  cm=MB_4::eRMR_last+2;
                  mc[xc+1].iBlockMode=mc[xc].iBlockMode=mc[1].iBlockMode=mc[0].iBlockMode=cm;
                  iLeftR=4;
               }
               */
                  /*
               else if(iIsB && mc->iIsBi && i && j
                  && ((mc->vrefs[1]==mc[-1].vrefs[1] && mc->vrefs[2]==mc[-1].vrefs[2]) ||  (mc[0].vrefs[1]==mc[-xc].vrefs[1] && mc[0].vrefs[2]==mc[-xc].vrefs[2]))
                  && ((mc->vrefs[1]==mc[1].vrefs[1] && mc->vrefs[2]==mc[1].vrefs[2]) ||  (mc[1-xc].vrefs[1]==mc[1].vrefs[1] && mc[1-xc].vrefs[2]==mc[1].vrefs[2]))
                  && ((mc->vrefs[1]==mc[xc].vrefs[1] && mc->vrefs[2]==mc[xc].vrefs[2]) || (mc[xc-1].vrefs[1]==mc[xc].vrefs[1] && mc[xc-1].vrefs[2]==mc[xc].vrefs[2]))
                  && ((mc[1].vrefs[1]==mc[xc+1].vrefs[1] && mc[1].vrefs[2]==mc[xc+1].vrefs[2])|| (mc[xc].vrefs[1]==mc[xc+1].vrefs[1] && mc[xc].vrefs[2]==mc[xc+1].vrefs[2]))
                  ){
                  mc[1].iBlockDC=mc[xc].iBlockDC=mc[1+xc].iBlockDC=2;
                  iBCnt-=3;
                  cm=MB_4::eRMR_last+3;
                  mc[xc+1].iBlockMode=mc[xc].iBlockMode=mc[1].iBlockMode=mc[0].iBlockMode=cm;
               }
               */
              
                  
            }
            //else iPredMLeft=0;
            /*
            else if(i+1<xc && j+i<yc){
               mc[1].iBlockDC=2;
               mc[1+xc].iBlockDC=2;
               mc[xc].iBlockDC=2;
            }
            */
#endif
            //+(tlm+trm*16)
            //+((iIsB?iRefCnt:iBCnt)||iCanBeRot||iMCnt)*512
            //sc=findScoreMode(cm,(lm+tm*16)+(!!iIsB)*256+(iRefCnt*2>iLeft)*1024*16+(iBCnt*2>iLeft)*512+(!!(iCanBeRot))*1024+(iMCnt*4>iLeft)*2048 +((!!iHas4x4))*4096+iPredMLeft*8192 );//+(!!(iRCnt|iHas4x4))*400
    //        if(iBCnt*2>iMCnt && iBCnt*2>iRefCnt &&  iBCnt*2>iRCnt)tlm|=2;
  //          else if(iMCnt*2>iBCnt && iMCnt*2>iRefCnt &&  iMCnt*2>iRCnt)tlm|=4;
//            else if(iRefCnt*2>iBCnt && iRefCnt*2>iMCnt &&  iRefCnt*2>iRCnt)tlm|=8;
            int iM2NLeft=iLeft-iBCnt-iRefCnt-iRCnt-iMCnt;
            if(iBCnt>3*(iM2NLeft+iMCnt+iRefCnt+iRCnt))tlm|=2;
            else if(iRefCnt>3*(iM2NLeft+iMCnt+iBCnt+iRCnt))tlm|=4;//10 01 11 00
            else if(iM2NLeft>(iRefCnt+iMCnt+iBCnt+iRCnt))tlm|=6;
            /*
            iMa=!!iM2NLeft;
            iMa+=!!iBCnt;
            iMa+=!!iRefCnt;
            iMa+=!!iRCnt;
            iMa+=!!iMCnt;
            */
            /*
            int ff=0;
            if(!iBCnt)ff|=(1<<0);
            if(!iRefCnt)ff|=(1<<1);
            if(!iRCnt)ff|=(1<<2);
            if(!iMCnt)ff|=(1<<3);
            if(!iM2NLeft)ff|=(1<<4);

            tlm=trm=ff;
            */
            //if(iBCnt<iMCnt)tlm|=8;
            //if(iCanBeRot || iHas4x4)tlm|=8;//+(tlm|trm)*512//+(tlm|trm)*1024
            if(mb4->iBlockMode==mb4->eRef_m && iHas4x4){iGainX++;if(mb4->iMVMode>1)cm=mb4->iBlockMode=MB_4::eRMR_last+4;}
            sc=findScoreMode(cm,(lm+tm*16)+(!!iIsB)*256+(i+2<xc && j+2<yc)*512,0);//+(!!iBCnt)*512+(!!(iCanBeRot))*1024+(!!(iMCnt))*2048 +((!!iHas4x4))*4096+iPredMLeft*8192 );//+(!!(iRCnt|iHas4x4))*400
            if(iLeftR>1){
               findScoreMode(iCMS,(iCMS+iCMS*16)+(!!iIsB)*256+(i+2<xc && j+2<yc)*512,2);
            }
            /*
            if(iCMS<4 && iIsB && (mc->mv_eM1_dec_enc.x)/2==(mc->vrefs[1].x-mc->vrefs[2].x)/2 && mc->mv_eM1_dec_enc.y/2==(mc->vrefs[1].y-mc->vrefs[2].y)/2){
               iLeft-=iLeftR;
               if(mc->iIsBi)iBCnt-=iLeftR;
               else if(mc->iMVMode)iMCnt-=iLeftR;
               else if(mc->r.i)iRCnt-=iLeftR;
               else if(cm==mb4->eRef)iRefCnt-=iLeftR;
               mc->iBlockDC=1;
               //else if(cm==mb4->eRefM2)iRefM2Cnt-=iLeftR;
               //else if(cm==mb4->eRefN)iRefNCnt-=iLeftR;
               iSkipCnt++;
               continue;
            }
            if(iIsB)sc++;
            */
            //+(iRefCnt*8>iLeft*7)*1024*16
            /*
            sc=findScoreMode(cm,(lm+tm*10)+(!!iIsB)*100+(!!iBCnt)*200+(!!(iHas4x4))*400
               +(!!(iRCnt))*800+(!!(iMCnt*2<iLeft))*1600
               +(!!(iBCnt*2<iLeft))*3200);//+!!iHasB*200+!!iHasRot*400
               */
            
         }
#else
         int rcnt=0;
         int mcnt=0;
         int bicnt=0;
         int mref[3]={0,0,0};
         
         
#define SET_M_PRED(_O) {\
            if(mc[_O].iIsBi){bicnt++;mref[1]++;}else {\
              mref[mc->getID(mc[_O].refId)]++;\
              if(mc[_O].iMVMode)mcnt++; else   if(mc[_O].r.s[0]|mc[_O].r.s[1])rcnt++;\
            }\
         }
         if(i)SET_M_PRED(-1);
         if(j)SET_M_PRED(-xc);
         if(i && j)SET_M_PRED(-1-xc);
         if(i+1<xc && j)SET_M_PRED(1-xc);

         int refp;
         if(mref[2]>mref[1] && mref[2]>mref[0])refp=2;
         else if(mref[0]>mref[1] && mref[0]>mref[2])refp=0;
         else if(mref[1]==2 && (mref[1]==2 || mref[0]==2))refp=3;
         else refp=1;
         
         

         int sc=findScoreMode(cm,(((rcnt+mcnt*5+bicnt*25)*4)+refp)+!!iIsB*500);
#endif
         
         if(sc==0){
            iSkipCnt++;
            iLeft-=iLeftR;
            if(mc->iIsBi)iBCnt-=iLeftR;
            else if(mc->iMVMode)iMCnt-=iLeftR;
            else if(mc->r.i)iRCnt-=iLeftR;
            else if(iCMS==mb4->eRef)iRefCnt-=iLeftR;
            //else if(cm==mb4->eRefM2)iRefM2Cnt-=iLeftR;
            //else if(cm==mb4->eRefN)iRefNCnt-=iLeftR;
            continue;
         }
         sc--;
        
//         debugsi("sc",sc);         debugsi("iSkipCnt",iSkipCnt);
         iLenN+=vlc.bitsLen[iSkipCnt];
         iSkipCnt=0;
         
         if(iBCnt==iLeft || iRCnt==iLeft || iMCnt==iLeft || iRefCnt==iLeft){continue;}
         /*
         if(mc->iMVMode){
            iLenN--;
            if(mc->iMVMode>1)iLenN-=4;
            int findScoreModeSplit(int val, int tid);
            int tsm=j?mc[-xc].iMVMode:0;
            int lsm=i?mc[-1].iMVMode:0;
            
            int iPred=tsm&(8|16);
            if(lsm&4)iPred|=4;
            if(lsm&16)iPred|=2;
            iPred>>=1;
            
            int sc2=findScoreModeSplit(mc->iMVMode,iPred);
            iLenN+=vlc.bitsLen[sc2]; 
            
         }
         */
#if 1
         //if(iMa==2){}else if(iMa==3)iLen++;else 
         if(1||iPredMLeft){
            iLenN+=vlc.bitsLen[sc];//--if(sc==3 || sc==7)iLenN--; 
         }
         else{
            iLenN+=(sc)+1;//vlc.bitsLen[sc]; 
         }
         iLeft-=iLeftR;
         if(mc->iIsBi)iBCnt-=iLeftR;
         else if(mc->iMVMode)iMCnt-=iLeftR;
         else if(mc->r.i)iRCnt-=iLeftR;
         else if(iCMS==mb4->eRef)iRefCnt-=iLeftR;
       //  else if(cm==mb4->eRefM2)iRefM2Cnt-=iLeftR;
        // else if(cm==mb4->eRefN)iRefNCnt-=iLeftR;
#else
         iLenN+=vlc.bitsLen[sc]; 
         
         if(iIsB){
            //1 3 3 5 5 5 5  ,111000 111001 111010 111011  
            //bi,ref,refN,refM2,ref-r,refN-r,refM2-r,ref-m,refN-m,refM2-m
            if(sc==3)iLenN--;else if(sc>6)iLenN--;
         }
         else{
            //0 100 101 111 1100 1101
            //bi,ref,refN,ref-r,refN-r,ref-m,refN-m
            if(sc==3)iLenN-=2;else if(sc>3)iLenN--;
         }
         
#endif    
      }
   }
   }
   
   if(iSkipCnt)iLenN+=vlc.bitsLen[iSkipCnt];
#endif
   debugs2i("mv",iLenN,vlc.iBitPos-iBitPosR);
   iGainX+=(vlc.iBitPos-iBitPosR)-iLenN;
//   debugsi("g",iGainX>>3);
#endif

#if 0
   unsigned char tabsT[20000];
   int to=xc*yc;
   int res=0;
   for(j=0;j<to;j++){
      tabsT[j]=0;
      if(!iIsB){
         if(mb4[j].iIsBi){}
         else {
           tabsT[j]|=1;
           if(mb4[j].refId==-2)tabsT[j]|=2;//0 =0
           if(mb4[j].iMVMode)     tabsT[j]|=4;
           if(mb4[j].iMVMode==0 && (mb4[j].r.s[0]|mb4[j].r.s[1]))     tabsT[j]|=8;
         }
   //      res+=vlc.bitsLen[tabsT[j]];
      }
      else{
         if(mb4[j].iIsBi){}//tabsT[j]|=1;
         else {
            tabsT[j]|=1;
            if(mb4[j].refId==-1)   tabsT[j]|=2;// 0= next
            if(mb4[j].refId==-2)   tabsT[j]|=4;
            if(mb4[j].iMVMode)     tabsT[j]|=8;
            if(mb4[j].iMVMode==0 && (mb4[j].r.s[0]|mb4[j].r.s[1]))tabsT[j]|=16;
         }
   //      res+=vlc.bitsLen[tabsT[j]];
      }
   }
   res>>=3;

   //int encAritm(unsigned char *out , int *in ,int iCnt);
   int encAritmUC(unsigned char *out , unsigned char *in ,int iCnt);
   unsigned char outTest[40000];
   res=encAritmUC(&outTest[0],&tabsT[0],to)-2-2;
//int t_zipIt(char* pData, int iDataLen, char *pOut);
   debugss("rle ",(vlc.iBitPos-iBitPosDbg+7)>>3,res);

   //int t_zipIt(char* pData, int iDataLen, char *pOut);
   //res=t_zipIt((char* )&tabsT[0], to, (char *)&outTest[0]);
   //debugss("zip ",res,0);
#endif
   //rleStat[iRlePos]=rle;iRlePos++;
   //iHasRot=1;
   /*
int refFlags=0;
int iCntMode=0;
int iFlagX=0;
for(j=0;j<yc*xc;j++){iFlagX|=mb4[j].iRefsValid;iCntMode|=mb4[j].iMVMode;if(mb4[j].refId==-2)refFlags|=1;else if(mb4[j].refId==-1)refFlags|=2;else refFlags|=4;}
debugss("enc 4x4 rot",iHas4x4,iHasRot);
debugss("enc bi, mode",iHasB,iCntMode);
debugss("enc iFlagX, refFlags",iFlagX, refFlags);
*/
   //debugss("vec ",(vlc.iBitPos)>>3,0);
   int px[3]={0,0,0};
   int py[3]={0,0,0};


#ifdef T_CAN_TEST_V

   skX.reset();
   int iBPBeforeVec=vlc.iBitPos;
   int iOfsBits=xc*yc*4;
   vlc.iBitPos+=iOfsBits;
   vlc.iBitPos&=~7;

   int iVecBitsStart=vlc.iBitPos;
#endif
         //skX2.reset();
   for(j=0;j<yc;j++)
   {
      for(i=0;i<xc;i++,mb4++){

         MB_4 *mbx[9];//={NULL,NULL,NULL,NULL,NULL,NULL,NULL};
         loadNearMB(mb4,mbx);
         //mbx[NEXT_BI_MB]=mbx[UPP_BI_MB]=mbx[UP_BI_MB]=mbx[UPP_MB]=mbx[NEXT_MB]=mbx[UP_MB]=NULL;
/*
         if(mb4->iIsBi && (mb4->r.s[0]|mb4->r.s[1])){
            debugss("err bi rot",mb4->iIsBi,(mb4->r.s[0]|mb4->r.s[1]));
            iErr|=1;
         }
         if(mb4->iMVMode && mb4->iIsBi){
            debugss("err mode bi",mb4->iMVMode,mb4->iIsBi);
            iErr|=2;
         }
         if(mb4->iMVMode && (mb4->r.s[0]|mb4->r.s[1])){
            debugss("err mode bi",mb4->iMVMode,(mb4->r.s[0]|mb4->r.s[1]));
            iErr|=4;
         }
*/

         int ff=0;
#ifdef T_CAN_TEST_V
/*
         if(!iIsB && mb4->iBlockMode==mb4->eRef){
            ff=mb4->mv_eM1_dec_enc==mb4->vrefs[1];
            if(ff){
               skX.skipV();
               mb4->mv2[0]=mb4->vrefs[1];
               px[1]=mb4->vrefs[1].x;py[1]=mb4->vrefs[1].y;

            }else skX.encV();
         }
         else */
         if(iIsB && mb4->iIsBi){
            ff=getPredMV_1x1_bbi(&mbx[0]->vrefs[1],&mbx[0]->vrefs[2],mb4,i,j,1);
            if(ff){
               skX.skipV();
               mb4->mv2[0]=mb4->vrefs[1];
               px[1]=mb4->vrefs[1].x;py[1]=mb4->vrefs[1].y;
               px[2]=mb4->vrefs[2].x;py[2]=mb4->vrefs[2].y;

            }else skX.encV();
         }
#endif
         //int iBPx=vlc.iBitPos;
         if(!ff){

         ::encMB_MV(&vlc,mbx,px,py,iHas4x4,iIsB);
    //     int bbx=vlc.iBitPos-bpb;
      //   bpb=vlc.iBitPos;
         if(mb4->iIsBi)encMB_B(&vlc,px,py,mbx,iIsB,0);
         }
         /*
         if(!iIsB && mb4->iBlockMode==mb4->eM1){
            if(mb4->vrefs[1]==mb4->mv_eM1_dec_enc){
               skC[0]++;
               iGainX+=(vlc.iBitPos-iBPx);
            }
            else {iGainX-=vlc.bitsLen[skC[0]];skC[0]=0;}
         }
         */
         //void video_log(int b, int x, int y,const char *format,...);
        // if(iIsB)video_log(mb4->iIsBi,i,j,"b1=%d,b2=%d,m=%d",bbx,vlc.iBitPos-bpb,mb4->iBlockMode);
#ifdef T_CAN_TEST_V
         
    
         if(!iIsB){
            mb4->mv_eM1_dec_enc.iSad=0;
        //    mb4->iBlockMode=getMode(mb4);
            
            if(mb4->iBlockMode==mb4->eRef_m){// || mb4->iBlockMode==MB_4::eRMR_last+4)){
               /*
// 0  1  4  5 
// 2  3  6  7 
// 8  9 12 13
//10 11 14 15
               */
               mb4->mv_eM1_dec_enc.iSad=1;
               mb4->mv_eM1_dec_enc.x=(mb4->mv4[0].x+mb4->mv4[5].x+mb4->mv4[15].x+mb4->mv4[10].x+2)>>2;
               mb4->mv_eM1_dec_enc.y=(mb4->mv4[0].y+mb4->mv4[5].y+mb4->mv4[15].y+mb4->mv4[10].y+2)>>2;
            }
            else {
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eM1];
               mb4->mv_eM1_dec_enc.iSad=mb4->iBlockMode==mb4->eRef_r?2:(mb4->iBlockMode==mb4->eRef || mb4->iBlockMode==mb4->eBi);
               if(mb4->mv_eM1_dec_enc.iSad==0)mb4->mv_eM1_dec_enc.y=mb4->mv_eM1_dec_enc.x=0;
            }
            mb4->mv_eM1_gr_enc_only=mb4->vrefs[mb4->eM1];

         }
         /*
         else if(iIsB && mb4->iBlockMode<4){
            if(mb4->iBlockMode==mb4->eRef){
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eM1];
               mb4->mv_eM1_dec_enc.x*=2;
               mb4->mv_eM1_dec_enc.y*=2;
            }
            else if(mb4->iBlockMode==mb4->eRefN){
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eNext];
               mb4->mv_eM1_dec_enc.x*=-2;
               mb4->mv_eM1_dec_enc.y*=-2;
            }
            else if(mb4->iBlockMode==mb4->eBi){
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eM1];
               mb4->mv_eM1_dec_enc.x-=mb4->vrefs[mb4->eNext].x;
               mb4->mv_eM1_dec_enc.y-=mb4->vrefs[mb4->eNext].y;
            }

         }
         */
#else
         if(!iIsB){
            mb4->mv_eM1_dec_enc.iSad=0;
        //    mb4->iBlockMode=getMode(mb4);
            
            if((mb4->iBlockMode==mb4->eRef_m)){// || mb4->iBlockMode==MB_4::eRMR_last+4)){
               mb4->mv_eM1_dec_enc.iSad=1;
               mb4->mv_eM1_dec_enc.x=(mb4->mv4[0].x+mb4->mv4[5].x+mb4->mv4[15].x+mb4->mv4[10].x+2)>>2;
               mb4->mv_eM1_dec_enc.y=(mb4->mv4[0].y+mb4->mv4[5].y+mb4->mv4[15].y+mb4->mv4[10].y+2)>>2;
            }
            else {
               mb4->mv_eM1_dec_enc=mb4->vrefs[mb4->eM1];
               mb4->mv_eM1_dec_enc.iSad=mb4->iBlockMode==mb4->eRef_r?2:(mb4->iBlockMode==mb4->eRef || mb4->iBlockMode==mb4->eBi);
               if(mb4->mv_eM1_dec_enc.iSad==0)mb4->mv_eM1_dec_enc.y=mb4->mv_eM1_dec_enc.x=0;
            }
            mb4->mv_eM1_gr_enc_only=mb4->vrefs[mb4->eM1];

         }
#endif

      }
   }
#ifdef T_CAN_TEST_V

   int iVecBits=vlc.iBitPos-iVecBitsStart;
  

   vlc.iBitPos=iBPBeforeVec;
   skX.encBlocks(&vlc);
   vlc.copyBits(vlc.pBitBuf,iVecBitsStart,iVecBits);

   /*
   int ibpx=vlc.iBitPos;
  // skX2.encBlocks(&vlc);
   iGainX-=(vlc.iBitPos-ibpx);
   vlc.iBitPos=ibpx;
*/

   //iGainX-=rce()*8;
   iLen=vlc.getBytePos();
/*
 static int iGPr=0;
   if(skC[0])iGainX-=vlc.bitsLen[skC[0]];skC[0]=0;
   if(skC[1])iGainX-=vlc.bitsLen[skC[1]];skC[1]=0;
   if(skC[2])iGainX-=vlc.bitsLen[skC[2]];skC[2]=0;
   if(skC[3])iGainX-=vlc.bitsLen[skC[3]];skC[3]=0;
   if(iGainX){   debugsi("g",iGainX>>3);}
 iGPr=iGainX;
 */
 //  debugsi("iLen",iLen-((iGainX-iGPr)>>3));
//vlc.iBitPos=ibpx;
//iLen=vlc.getBytePos();
  // if(iErr)debugss("err-bi-rot-mode",iErr,iLen);
#else
   iLen=vlc.getBytePos();
#endif
//debugsi("enc v",iLen-((iGainX-iGPr)>>3));
   return 0;
}
CMB4 *getPtr_CMB4(void *ctx){
   TINA_M_STR *m=(TINA_M_STR *)ctx;
   return m->mbCtx;
}


void *initMotion(){
   TINA_M_STR *m=new TINA_M_STR;
   m->mbCtx=new CMB4();
   m->vlc=new CTVLCX();
   return m;
}
void relMotion(void *p){
   TINA_M_STR *m=(TINA_M_STR *)p;
   delete m->mbCtx;
   delete m->vlc;
}

void dec2UV(unsigned char *dst, int w, int h, unsigned char *src){
   int i,j;
   h>>=1;
   src++;//Y
   int stride=w*3;
   for(j=0;j<h;j++){
      for(i=0;i<w;i+=2){
         dst[0]=(src[0]+src[3]+src[stride]+src[stride+3]+2)>>2;
         src++;dst++;
         dst[0]=(src[0]+src[3]+src[stride]+src[stride+3]+2)>>2;
         dst++;
         src+=5;
      }
      src+=stride;
   }
}



#include <stdio.h>
void debugVectors(void *ctx, int MVSize,int iCur, int iIsB,int w, int h){
   TINA_M_STR *tm=(TINA_M_STR*)ctx;
   CMB4 *mbCtx=tm->mbCtx;
   MB_4 *m=mbCtx->mb;
   int i,j,iRef;
   int xc=mbCtx->xc;
   int yc=mbCtx->yc;
   FILE *f=fopen("dbg.txt","w");
   fprintf(f,"------ iIsB=%d -----\n",iIsB);
   for(j=0;j<yc;j++){
      for(i=0;i<xc;i++){
         iRef=MB_4::getID(m->refId);
         fprintf(f,"a[%3d,%3d](m=%d,r=[%2d,%2d],ref=%d,v[%3d,%3d]) ",j,i,m->iMVMode,m->r.s[0],m->r.s[1],iRef,m->vrefs[iRef].x,m->vrefs[iRef].y);
         if(m->iIsBi){
            int iBI2Ref=iRef==1?(iIsB?2:0):1;
            fprintf(f,"vbi[%3d,%3d]\n",m->vrefs[iBI2Ref].x,m->vrefs[iBI2Ref].y);
         }
         else fprintf(f,"\n");
         m++;

      }
   }
   fclose(f);
}
#endif

