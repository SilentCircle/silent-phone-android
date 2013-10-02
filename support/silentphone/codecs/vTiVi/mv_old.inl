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

#include <stdio.h>
//#include <string.h>
#include <string.h>
#include <math.h>

#include "..\ctimgdata.h"
#include "vec_calc.h"
#include "t_vlc.h"


inline int mgabs(int i){return i>=0?i:-i;}
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

void getV_cntX(int *rx,int *x, int cnt);
#if  defined(__SYMBIAN32__) || defined(_WIN32_WCE)
int getMVBitLen(int x, int y){return 1;}
#else
int getMVBitLen(int x, int y);
#endif
static int iDecErrs=0;
void debugss(char *c, int a,int b);

const int pred_mv_2x2[4][10]={
   {10,PREV_MB,5,7,UP_MB,10,11,UP_MB,14,15},
   {10,CUR_MB,1,3,UP_MB,14,15,NEXT_MB,10,11},
   {10,PREV_MB,13,15,CUR_MB,2,3,CUR_MB,6,7},
   {10,CUR_MB,11,9,CUR_MB,6,7,CUR_MB,3,3}
};

// 0  1  4  5 
// 2  3  6  7 
// 8  9 12 13
//10 11 14 15
const int pred_mv_4x4[16][7]={
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
const int tabMode[]={2,4,8,16};

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

   if(i+i<xc && j &&  v[1-xc].iIsBi)mbx[NEXT_BI_MB]=v+1-xc;
   if(j && i &&  v[-xc-1].iIsBi)mbx[UPP_BI_MB]=v-1-xc;

   if(j && i+1<xc &&  v[1-xc].refId==v->refId)mbx[NEXT_MB]=v+1-xc;
   if(j && i &&  v[-1-xc].refId==v->refId)mbx[UPP_MB]=v-1-xc;
}
/*
inline void loadNearMB(MB_4 *v, MB_4 *mbx[]){
   const int i=v->i;
   const int j=v->j;
   const int xc=v->xc;
   mbx[CUR_MB]=v;
   if(i &&  v[-1].refId==v->refId)mbx[PREV_MB]=v-1;
   if(j &&  v[-xc].refId==v->refId)mbx[UP_MB]=v-xc;
   if(j && i+1<xc &&  v[1-xc].refId==v->refId)mbx[NEXT_MB]=v+1-xc;
   if(j && i &&  v[-1-xc].refId==v->refId)mbx[UPP_MB]=v-1-xc;
}
*/
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
const int tab_rotP[]={0,2,0,9,    0,5, //,0,7,
                      1,5, 1,9, 
                      2,8,2,10,
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
   int iIsGoodMB=(sad*3<v->iPosibleMinSad*4 || sad*2<v->iDev) && sad*2<v->iPosibleMinSad*3;
  // const int g=3;
   
   const int qMult=iMVSize>16?5:3;

#define _CMP_MB (v->iExtMVSearch && sad*3>v->iPosibleMinSad*2) || (iIsGoodMB && mgabs(d)<3) || (!iIsGoodMB && d)

   
#define V_IS_OK(_ID) (v[_ID].vrefs[ref].iSad*2<v[_ID].iPosibleMinSad*3 || v[_ID].vrefs[ref].iSad*9<v[_ID].iDev*qMult)
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
      else if((v->refId==-1 && v[-xc].refId==0) || (v->refId==0 && v[-xc].refId==-1)){
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
      else if((v->refId==-1 && v[-1].refId==0) || (v->refId==0 && v[-1].refId==-1) ){
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
      r1[cntR1]=1;r2[cntR2]=1;cntR1++;cntR2++;
      r1[cntR1]=-1;r2[cntR2]=-1;cntR1++;cntR2++;
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

   if(mbx[UP_MB] &&  mbx[UP_MB]->iMVMode==0 && mbx[UP_MB]->r.s[0] && mgabs(mbx[UP_MB]->r.s[0])<7
      && mbx[UP_MB]->vrefs[rid].x==v->vrefs[rid].x
      && mgabs(mbx[UP_MB]->vrefs[rid].y-v->vrefs[rid].y)){
      r1=mbx[UP_MB]->r.s[0];
   }
   if(mbx[PREV_MB] &&  mbx[PREV_MB]->iMVMode==0 && mbx[PREV_MB]->r.s[1] && mgabs(mbx[PREV_MB]->r.s[1])<7
      && mbx[PREV_MB]->vrefs[rid].y==v->vrefs[rid].y
      && mgabs(mbx[PREV_MB]->vrefs[rid].x-v->vrefs[rid].x)<2){
      r2=mbx[PREV_MB]->r.s[1];
   }
  
   for(int az=1;az<5;az++){
      //mbx[az]->refId=v->refId , nevajag jo mbx ir visi ref vienaadi
      if(mbx[az] && mbx[az]->iMVMode==0){
         if(!r1 && mgabs(mbx[az]->r.s[0])>5){x[ac1]=mbx[az]->r.s[0];ac1++;}
         if(!r2 && mgabs(mbx[az]->r.s[1])>5){y[ac2]=mbx[az]->r.s[1];ac2++;}
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

inline int getPredMV_4x4S(const int id, T_CUR_VECTOR *r, MB_4 *mb[], int *x, int *y){

   int cnt=0;
   MB_4 *m;

  // int iMark=0;
   int i;
   int to=pred_mv_4x4[id][0];
   for(i=1;i<to;i+=2){
      m=mb[pred_mv_4x4[id][i]];
      if(m)// && mb[0].refId==m->refId)
      {
         x[cnt]=m->mv4[pred_mv_4x4[id][i+1]].x;
         y[cnt]=m->mv4[pred_mv_4x4[id][i+1]].y;
         cnt++;
         if(cnt==2)break;
      }
    //  else if(i==1){iMark=1;}
   }
   return cnt;
}

inline int getPredMV_2x2S(const int id, T_CUR_VECTOR *r, MB_4 *mb[], int *x, int *y){
   int cnt=0;
   MB_4 *m;
    
   int i;
   //int iMark=0;
   int to=pred_mv_2x2[id][0];
   for(i=1;i<to;i+=3){
      m=mb[pred_mv_2x2[id][i]];
      if(m)// && mb[0].refId==m->refId)
      {
//         x[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].x+m->mv4[pred_mv_2x2[id][i+2]].x)/2;
  //       y[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].y+m->mv4[pred_mv_2x2[id][i+2]].y)/2;
         x[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].x+m->mv4[pred_mv_2x2[id][i+2]].x);if(x[cnt]<0)x[cnt]++;x[cnt]>>=1;
         y[cnt]=(m->mv4[pred_mv_2x2[id][i+1]].y+m->mv4[pred_mv_2x2[id][i+2]].y);if(y[cnt]<0)y[cnt]++;y[cnt]>>=1;
         cnt++;
         if(cnt==2)break;
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
int isValidVector(int x, int y ,int pos1, int pos2,int iMBSize, int w ,int h);
static int isValidVector16_MB_4(int x, int y, MB_4 *m){
   return isValidVector(x,y,m->i*16,m->j*16,16,m->xc*16,m->yc*16);
}
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


T_CUR_VECTOR  getPred_BBi(MB_4 *mb, int ref){

   T_CUR_VECTOR v;
   int vx[4]={0,0,0,0};
   int vy[4]={0,0,0,0};
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
   getV_cntX2(&v.x,&vx[0],cnt,xa);
   getV_cntX2(&v.y,&vy[0],cnt,ya);
   //}
   //v->x=v->y=0;
   return v;
}


int canBeBBi(MB_4 *mb,  int i, int j){
   if(mb->mv_eM1_dec_enc.iSad==2)return 5;
   int xc=mb->xc;
   int t=(i&1)|((j&1)<<1);
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
int getPredMV_1x1_bbi(T_CUR_VECTOR *r1,T_CUR_VECTOR *r2 , MB_4 *mb,  int i, int j, int iEq){
   
   int xc=mb->xc;
   int t=(i&1)|((j&1)<<1);
   int bi=0;
   /*
   if(i && j && i+1<xc){//iSad!=2
      bi+=!!mb[-1].iIsBi;
      bi+=!!mb[-xc].iIsBi;
      bi+=!!mb[1-xc].iIsBi;
      bi+=!!mb[-xc-1].iIsBi;
      if(bi<2){
      int m1=0,m2=0;
         m1+=mb[-1].refId==-1;
         m1+=mb[-xc].refId==-1;
         m1+=mb[-1-xc].refId==-1;
         m1+=mb[1-xc].refId==-1;
         m2+=mb[-1].refId>=0;
         m2+=mb[-xc].refId>=0;
         m2+=mb[-1-xc].refId>=0;
         m2+=mb[1-xc].refId>=0;
         if(m1 && m2)bi+=2;
      }
   }
   else bi=3;
   int m=mb->mv_eM1_dec_enc.iSad==0 ||((i || j) && (t==0 || t==3) && bi>2) || (i&&j && (t==1 || t==2) && mb[-1].iIsBi && mb[-xc].iIsBi);
   */
   int m=mb->mv_eM1_dec_enc.iSad!=2;

/*
   if(mb->mv_eM1_dec_enc.iSad==2)m=0;else
      
   if(!m && i && j && i+1<xc){
      m=1;
      int o=0;

         if(m){
      m=   (mb[-1].vrefs[1].x<=x && x<=mb[ -xc].vrefs[1].x && mb[-1].vrefs[1].y<=y && y<=mb[ -xc].vrefs[1].y)
         ||(mb[-1].vrefs[1].x<=x && x<=mb[1-xc].vrefs[1].x && mb[-1].vrefs[1].y<=y && y<=mb[1-xc].vrefs[1].y)
         ||(mb[-1].vrefs[1].x>=x && x>=mb[ -xc].vrefs[1].x && mb[-1].vrefs[1].y>=y && y>=mb[ -xc].vrefs[1].y)
         ||(mb[-1].vrefs[1].x>=x && x>=mb[1-xc].vrefs[1].x && mb[-1].vrefs[1].y>=y && y>=mb[1-xc].vrefs[1].y);
      if(m){
            m= (mb[-1].vrefs[2].x<=-x && -x<=mb[ -xc].vrefs[2].x && mb[-1].vrefs[2].y<=-y && -y<=mb[ -xc].vrefs[2].y)
            || (mb[-1].vrefs[2].x<=-x && -x<=mb[1-xc].vrefs[2].x && mb[-1].vrefs[2].y<=-y && -y<=mb[1-xc].vrefs[2].y)
            ||( mb[-1].vrefs[2].x>=-x && -x>=mb[ -xc].vrefs[2].x && mb[-1].vrefs[2].y>=-y && -y>=mb[ -xc].vrefs[2].y)
            ||( mb[-1].vrefs[2].x>=-x && -x>=mb[1-xc].vrefs[2].x && mb[-1].vrefs[2].y>=-y && -y>=mb[1-xc].vrefs[2].y);
      }
      m=!m;
         }

   }
   if(iEq==2)return m;
   */
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


int getPredMV_2x2BI(const int id,  MB_4 *mb[], int *x, int *y){
   int cnt=0;
   //MB_4 *m;
   int iIsB=mb[0]->iRefsValid&mb[0]->eRF_next;
   T_CUR_VECTOR r;

   //const int id, T_CUR_VECTOR *r, MB_4 *mb[], int *x, int *y
   return getPredMV_2x2S(id, &r, mb, x, y);
}
inline int getPredMV_1x1S(const int id, T_CUR_VECTOR *r, MB_4 *mb[], int *x, int *y){
     int cnt=0;
   MB_4 *m;

    
   m=mb[PREV_MB];
   if(m)// && mb[0].refId==m->refId)
   {
      if(0&&m->refId==-1){
         x[cnt]=m->vrefs[m->eM1].x;
         y[cnt]=m->vrefs[m->eM1].y;
      }
      else{
      //x[cnt]=(m->mv4[5].x + m->mv4[7].x +m->mv4[13].x+m->mv4[15].x)/4;
//      y[cnt]=(m->mv4[5].y + m->mv4[7].y +m->mv4[13].y+m->mv4[15].y)/4;
         x[cnt]=(m->mv4[5].x + m->mv4[7].x +m->mv4[13].x+m->mv4[15].x);if(x[cnt]<0)x[cnt]+=3;x[cnt]>>=2;
         y[cnt]=(m->mv4[5].y + m->mv4[7].y +m->mv4[13].y+m->mv4[15].y);if(y[cnt]<0)y[cnt]+=3;y[cnt]>>=2;
      }
      cnt++;
   }
   m=mb[UP_MB];
   if(m)// && mb[0].refId==m->refId)
   {
      if(0&&m->refId==-1){
         x[cnt]=m->vrefs[m->eM1].x;
         y[cnt]=m->vrefs[m->eM1].y;
      }
      else{
         x[cnt]=(m->mv4[10].x + m->mv4[11].x +m->mv4[14].x+m->mv4[15].x)/4;
         y[cnt]=(m->mv4[10].y + m->mv4[11].y +m->mv4[14].y+m->mv4[15].y)/4;
      }
      cnt++;
   }
   if(cnt!=2)
   {
      m=mb[NEXT_MB];
      if(m)
      {
         if(0&&m->refId==-1){
            x[cnt]=m->vrefs[m->eM1].x;
            y[cnt]=m->vrefs[m->eM1].y;
         }
         else{
            x[cnt]=m->mv4[10].x;
            y[cnt]=m->mv4[10].y;
         }

         cnt++;
      }
      if(cnt!=2){//else {
         m=mb[UPP_MB];
         if(m){
            if(0&&m->refId==-1){
               x[cnt]=m->vrefs[m->eM1].x;
               y[cnt]=m->vrefs[m->eM1].y;
            }
            else{
               x[cnt]=m->mv4[15].x;
               y[cnt]=m->mv4[15].y;
            }
            cnt++;
         }
      }
   }
   return cnt;
}
int getPredMV_4x4S_pred(const int id, T_CUR_VECTOR *r, MB_4 *mb[],  int iIsB){
   int x[4]={0,0,0,0};
   int y[4]={0,0,0,0};
   int c=getPredMV_4x4S(id,r,mb,&x[0],&y[0]);
   if((x[0]==x[1] && y[0]==y[1]) || c==1){
      r->x=x[0];
      r->y=y[0];
      return 1;
   }

   return 0;
}
int getPredMV_4x4S_predBI(const int id, int *x, int *y, MB_4 *mb[],  int iIsB){
   T_CUR_VECTOR r;
   int c=getPredMV_4x4S(id,&r,mb,&x[0],&y[0]);
   return c;
}
void getPred4xN(int *x, int *y, MB_4 *m, int a,int b,int c,int d){
   x[0]=(m->mv4[a].x + m->mv4[b].x +m->mv4[c].x+m->mv4[d].x)/4;
   y[0]=(m->mv4[a].y + m->mv4[b].y +m->mv4[c].y+m->mv4[d].y)/4;
}



template <int iDec>
int getPredMV_nxn(CTVLCX  * vlc,const int  m, const int id, T_CUR_VECTOR *r, MB_4 *mb[] ,int  px, int py){
   int x[4]={px,0,0,0};
   int y[4]={py,0,0,0};  
   int cnt=0;
    //  r->x=x[0];r->y=y[0];
      //return 1;   
   if(m==1)   cnt=getPredMV_1x1S(id, r, mb,  x, y);
   else if(m==2)   cnt=getPredMV_2x2S(id, r, mb,  x, y); 
   else   cnt=getPredMV_4x4S(id, r, mb,  x, y); 

   //int iIsB=1;

   if(cnt==0){// && mb[0]->refId!=-2){
      int rid=MB_4::getID(mb[0]->refId);
      if(mb[0]->i && mb[0][-1].iIsBi){
         x[0]=mb[0][-1].vrefs[rid].x;
         y[0]=mb[0][-1].vrefs[rid].y;
         cnt++;
      }
      if(mb[0]->j && mb[0][-mb[0]->xc].iIsBi){
         x[0]=mb[0][-mb[0]->xc].vrefs[rid].x;
         y[0]=mb[0][-mb[0]->xc].vrefs[rid].y;
         cnt++;
      }
   }
   if(cnt<2 || (x[0]==x[1] && y[0]==y[1]))
   {
      r->x=x[0];r->y=y[0];
      return 1;
   }
   if(iDec){
      if(vlc->getB()){r->x=x[0];r->y=y[0];}else {r->x=x[1];r->y=y[1];}
      return cnt;
   }      
   int a=r->x-x[0];
   int b=r->y-y[0];
  // if(a>63)a=63;
   //if(b>63)b=63;
   if(!a && !b){
      vlc->addB(1);r->x=x[0];r->y=y[0];
      return cnt;
   }

   int c=r->x-x[1];
   int d=r->y-y[1];
   //if(c>63)c=63;
   //if(d>63)d=63;
   int l=vlc->toMV_calc(a,b);//getMVBitLen(a,b);
   int l2=vlc->toMV_calc(d,c);

   if(l2<l){vlc->addB(0);r->x=x[1];r->y=y[1];}
   else {vlc->addB(1);r->x=x[0];r->y=y[0];}   
   return cnt;
}

void saveVec(CTVLCX  * vlc, int m,int id,  MB_4 *mbx[], T_CUR_VECTOR  * v, int prx, int pry){
   T_CUR_VECTOR va;
   va.x=v->x;va.y=v->y;
   getPredMV_nxn<0>(vlc,m,id,&va,mbx,prx,pry);
   
   const int dx=v->x-va.x;
   const int dy=v->y-va.y;
   
   if(dx | dy){
      vlc->addB(1);
      vlc->toMV(dx);
      vlc->toMV(dy);      
   }
   else vlc->addB(0);
         //if(vlc->getVlc()!=0x5)iDecErrs++;
 //vlc->toVLC(0x5);   
}

int getPred_nonB(MB_4 *mb, T_CUR_VECTOR * v){
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

int encMB_nonB_m1(CTVLCX  * vlc, int *px, int *py, MB_4 *v){

   T_CUR_VECTOR va;
   //va.x=v->x;va.y=v->y;
   getPred_nonB(v,&va);
   
   int rid=v->getID(-1);
   T_CUR_VECTOR *c=&v->vrefs[rid];
   const int dx=c->x-va.x;
   const int dy=c->y-va.y;
   
   if(dx | dy){
      vlc->addB(1);
      vlc->toMV(dx);
      vlc->toMV(dy);      
   }
   else vlc->addB(0);


   return 0;
}
template<int iDec>
int getPred_B(CTVLCX  * vlc,MB_4 *mb, T_CUR_VECTOR * v, int id){
   int vx[]={0,0,0,0};
   int vy[]={0,0,0,0};
   int cnt=0;
   int xc=mb->xc;
   //int iRid=id==1?-1:2;
#define CMP_BI(_A,_ID) _A &&(mb[_ID].iMVMode==0 && mb[_ID].refId!=-2 && (mb[_ID].iIsBi || (mb[_ID].refId==-1 && id==1) || (mb[_ID].refId>0 && id==2)))
#define CMP_BIR(_A,_ID) _A &&  mb[_ID].refId==mb->refId
   //}

   if(CMP_BI(mb->i,-1)){//mb->i && mb[-1].iMVMode==0 && mb[-1].refId!=-2){
      vx[cnt]=mb[-1].vrefs[id].x;
      vy[cnt]=mb[-1].vrefs[id].y;
      cnt++;
   }
   else if(CMP_BIR(mb->i,-1)){
         vx[cnt]=(mb->mv4[10].x + mb->mv4[11].x +mb->mv4[14].x+mb->mv4[15].x)/4;
         vy[cnt]=(mb->mv4[10].y + mb->mv4[11].y +mb->mv4[14].y+mb->mv4[15].y)/4;
      cnt++;
   }
   if(CMP_BI(mb->j,-xc)){//mb->j && mb[-xc].iMVMode==0 && mb[-xc].refId!=-2){
      vx[cnt]=mb[-xc].vrefs[id].x;
      vy[cnt]=mb[-xc].vrefs[id].y;
      cnt++;
   }
   else if(CMP_BIR(mb->j,-xc)){
         vx[cnt]=(mb->mv4[5].x + mb->mv4[7].x +mb->mv4[13].x+mb->mv4[15].x);if(vx[cnt]<0)vx[cnt]+=3;vx[cnt]>>=2;
         vy[cnt]=(mb->mv4[5].y + mb->mv4[7].y +mb->mv4[13].y+mb->mv4[15].y);if(vy[cnt]<0)vy[cnt]+=3;vy[cnt]>>=2;
      cnt++;
   }
   if(cnt<2){

      if(CMP_BI(mb->j && mb->i+1<xc,1-xc)){
         vx[cnt]=mb[-xc+1].vrefs[id].x;
         vy[cnt]=mb[-xc+1].vrefs[id].y;
         cnt++;
      }
      else if(CMP_BIR(mb->j && mb->i+1<xc,1-xc)){
         vx[cnt]=mb[-xc+1].mv4[10].x;
         vy[cnt]=mb[-xc+1].mv4[10].y;
         cnt++;
      }
   }
   if(cnt<2 || (vx[0]==vx[1] && vy[0]==vy[1])){
      v->x=vx[0];
      v->y=vy[0];
      return 0;
   }
   else{

      getV_cntX(&v->x,&vx[0],cnt);
      getV_cntX(&v->y,&vy[0],cnt);
   }
   /*
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
   //if(c>63)c=63;
   //if(d>63)d=63;
   int l=(a|b)?vlc->toMV_calc(a)+vlc->toMV_calc(b):1;//getMVBitLen(a,b);
   int l2=(c|d)?vlc->toMV_calc(d)+vlc->toMV_calc(c):1;

   if(l2<l){vlc->addB(0);v->x=vx[1];v->y=vy[1];}
   else {vlc->addB(1);v->x=vx[0];v->y=vy[0];}   
   return cnt;
*/
   //v->x=v->y=0;
   return 0;
}


int encMB_B(CTVLCX  * vlc, int *px, int *py, MB_4 *v, int iIsB){

   T_CUR_VECTOR va;
   //va.x=v->x;va.y=v->y;
   int rid=v->refId==-1?(iIsB?v->eNext:v->eM2):v->eM1;
   getPred_B<0>(vlc,v,&va, rid);
   //va.x=va.y=0;
   
   
   T_CUR_VECTOR *c=&v->vrefs[rid];
   const int dx=c->x-va.x;
   const int dy=c->y-va.y;
   
   if(dx | dy){
      vlc->addB(1);
      vlc->toMV(dx);
      vlc->toMV(dy);      
   }
   else vlc->addB(0);


   return 0;
}


int encMB_MV(CTVLCX  * vlc, int *px, int *py, MB_4 *v, int iHas4x4){
//   T_CUR_VECTOR va;
   
   MB_4 *mbx[]={NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,};
   int rid=v->getID(v->refId);   
   loadNearMB(v,mbx);
   T_CUR_VECTOR *c;
   int i,j;
   
   if(v->iMVMode==0)
   {
      c=&v->vrefs[rid];
      //if(v->iUpdateed==77)
        // v->iUpdateed=78;
      saveVec(vlc,1,0,mbx,c,px[rid],py[rid]);
      v->mv2[0]=*c;
      
      if(v->r.s[0] || v->r.s[1])
      {
         
         int a1=0;
         int a2=0;
         getPredRot(v,mbx,a1,a2);         
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
         //if(vlc->getVlc()!=0x39)iDecErrs++;
//vlc->toVLC(0x39);
         

      }
      
   }else {
      int m=v->iMVMode&(~1);
      if(iHas4x4){
         vlc->addB(m);
         if(m)for(j=0;j<4;j++){vlc->addB(tabMode[j] & m);}
      }
      for(i=0;i<16;i++){
         j=i>>2;
         if((tabMode[j] & m)==0){
            i+=3;  
            c=&v->mv2[j];
            saveVec(vlc,2,j,mbx,c,px[rid],py[rid]);
            continue;
         }
         c=&v->mv4[i];
         saveVec(vlc,4,i,mbx,c,px[rid],py[rid]);   
      }
      
      
   }
   
   px[rid]=c->x;py[rid]=c->y;
   return 0;   
}





void getVec(CTVLCX  * vlc,int m, int id, MB_4 *mbx[], T_CUR_VECTOR  * v,  int prx, int pry){
  
   getPredMV_nxn<1>(vlc,m,id,v,mbx,prx,pry);
   
   if(vlc->getB()){
      v->x+=vlc->getVlcSM();
      v->y+=vlc->getVlcSM();      
   }
//if(vlc->getVlc()!=0x5)iDecErrs++;
   
}


int decMB_nonB_m1(CTVLCX  * vlc, int *px, int *py, MB_4 *v){

   T_CUR_VECTOR *c;
   int rid=v->getID(-1);
   c=&v->vrefs[rid];
   
   getPred_nonB(v,c);

   if(vlc->getB()){
      c->x+=vlc->getVlcSM();
      c->y+=vlc->getVlcSM();      
   }
   return 0;
}
int decMB_B(CTVLCX  * vlc, int *px, int *py, MB_4 *v, int iIsB){

   T_CUR_VECTOR *c;
   int rid=v->refId==-1?(iIsB?v->eNext:v->eM2):v->eM1;
   c=&v->vrefs[rid];
   
   getPred_B<1>(vlc,v,c, rid);
   //c->x=c->y=0;

   if(vlc->getB()){
      c->x+=vlc->getVlcSM();
      c->y+=vlc->getVlcSM();      
   }
   return 0;
}
//int rleStat[5000];
//int iRlePos=0;;

int decMB_MV(CTVLCX  * vlc, int *px, int *py, MB_4 *v, int iHas4x4){
//   T_CUR_VECTOR va;
   
   MB_4 *mbx[]={NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL};
   int rid=v->getID(v->refId);   
   loadNearMB(v,mbx);
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
      getVec(vlc,1,0,mbx,c,px[rid],py[rid]);
      
      //idx++;

      //if(v->mv2[0]!=*c){v->mv2[0]=*c;iDecErrs++;}
      v->mv2[0]=*c;

      for(i=0;i<16;i++)v->mv4[i]=*c;
      
      if(v->r.s[0] || v->r.s[1])
      {

      
         int a1=0;
         int a2=0;
         getPredRot(v,mbx,a1,a2);         
         if(vlc->getB()){
            v->r.s[1]=0;
            if(a1)v->r.s[0]=a1+vlc->getVlcSM();else
            {
               v->r.s[0]=vlc->getVlc()+1;
               if(vlc->getB())v->r.s[0]=-v->r.s[0];
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
         }
        // static int iRotErr;
         if(mgabs(v->r.s[0])+mgabs(v->r.s[1])>18){
            iDecErrs+=100;
            debugss("rot err           ",v->r.s[0],v->r.s[1]);
         }

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
            getVec(vlc,2,j,mbx,c,px[rid],py[rid]);
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
         getVec(vlc,4,i,mbx,c,px[rid],py[rid]);   
         //if(*c!=v->mv4[i])       iDecErrs++;
         
         v->mv4[i]=*c;
      }
   }
   
   px[rid]=c->x;py[rid]=c->y;
   return 0;   
}


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
      if(i==to){
         if(iCurIsLast){
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
   return 0;
}

int decodeTinaBi(CTVLCX *vlc, MB_4 *mb4, int iCnt, int iCur, int iIsB){
   int iIsBi=1;
   int to=vlc->getVlc();   
   int i;

   for(i=0;i<iCnt;i++,mb4++){
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
   if(to)//!=iCnt)
      return -2;
   return 0;
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
   return 0;
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
         mb4->r.s[0]=mb4->r.s[1]=iCurRot;
      }
   }
   if(cnt)
      return -2;
   return 0;
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


typedef struct {
   CTVLCX *vlc;
   CMB4 *mbCtx;
}TINA_M_STR;
#include "t_aritm.inl"
int decodeTinaMotion(void *pCtx, int &iMBSize2, int iCur, int iIsB, int w, int h, unsigned char *pBin, int &iLen){
   TINA_M_STR *tm=(TINA_M_STR*)pCtx;
   CMB4 *mbCtx=tm->mbCtx;

   int i,j;
   CTVLCX &vlc=*tm->vlc;
   vlc.reset();
   vlc.iCalcBitsOnly=0;
   vlc.pBitBuf=pBin;
   
   iMBSize2=(vlc.getVlc()+1)<<2;
   //int zz=vlc.getVlc();
   int iHasRot=vlc.getB();
   int iHas4x4=vlc.getB();
   int iHasB=vlc.getB();;
   int xc=w/iMBSize2;
   int yc=h/iMBSize2;
//debugss("dec 4x4 rot",iHas4x4,iHasRot);
   mbCtx->init(w,h,iMBSize2);

   MB_4 *mb4=mbCtx->mb;
   int r;
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
   if(r){
      debugss("dec err ", r,iDecErrs);

      return r;
   }
   mb4=mbCtx->mb;

   int px[3]={0,0,0};
   int py[3]={0,0,0};
   for(j=0;j<yc;j++)
   {
      for(i=0;i<xc;i++,mb4++){
         if(!iHasRot)mb4->r.s[0]=mb4->r.s[1]=0;

        // mb4->iIsBi=0;
         ::decMB_MV(&vlc,px,py,mb4,iHas4x4);
         
         if(mb4->iIsBi)decMB_B(&vlc,px,py,mb4,iIsB);
         if(!iIsB){
            if(mb4->refId!=-1 || mb4->iMVMode){mb4->mv_eM1_gr.x=mb4->mv_eM1_gr.y=0;}
            else mb4->mv_eM1_gr=mb4->vrefs[mb4->eM1];
         }
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
   
   if(iLen==0)
      iLen=bp;
   else if(iLen!=bp){
      debugss("dec err  len -------------------------------------------------------", -4,iDecErrs);
      return -4;
   }
   if(iDecErrs)debugss("--------------------------------------",iDecErrs,0);

   return 0;

}

int encodeTinaMotion(void *pCtx, int iMBSize2, int iCur, int  iIsB, int w, int h, unsigned char *pBin, int &iLen){
   TINA_M_STR *tm=(TINA_M_STR*)pCtx;
   CMB4 *mbCtx=tm->mbCtx;

   int i,j;
   CTVLCX &vlc=*tm->vlc;
   vlc.reset();
   vlc.iCalcBitsOnly=0;
   vlc.pBitBuf=pBin;

   int xc=w/iMBSize2;
   int yc=h/iMBSize2;
   //mbCtx->init(w,h,iMBSize2);

   MB_4 *mb4=mbCtx->mb;

   vlc.toVLC((iMBSize2>>2)-1);
   //vlc.toVLC(0);
   int iBitPosR=vlc.iBitPos;
   vlc.addB(0);//has rot
   vlc.addB(0);//has 4x4
   int iHasB=0;

   for(j=0;j<yc;j++)
   {
      for(i=0;i<xc;i++,mb4++){
         if((mb4->r.s[0]|mb4->r.s[1])|| mb4->iMVMode)mb4->iIsBi=0;
         if(0&&mb4->iMVMode==0 && //mb4->refId!=-2  && 
            (mb4->r.s[0]|mb4->r.s[1])==0){// && ){//mgabs(mb4->r.s[0]+mb4->r.s[1])<7){

           //if(mb4->mv_eM1_gr.x)
           //int dx=mmabs(mb4->vrefs[mb4->eM1].x+mb4->vrefs[mb4->eNext].x);
           //int dy=mmabs(mb4->vrefs[mb4->eM1].y+mb4->vrefs[mb4->eNext].y);
           {
               int rid=mb4->getID(mb4->refId);
               int iSad=mb4->vrefs[rid].iSad;

               int iMult=iIsB?15:14;//18;lossless 18
               if(iSad*3>mb4->iDev*2)iMult++;
               if(iSad>mb4->iDev)iMult+=2;
               if(iSad*2<mb4->iPosibleMinSad)iMult--;
               if(iSad*3<mb4->iPosibleMinSad)iMult--;
               if(iSad*4<mb4->iPosibleMinSad)iMult-=2;

               if(iIsB){
                  
               if(mb4->mv_eM1_gr_enc_only.x<mb4->vrefs[mb4->eM1].x || 
                             mb4->mv_eM1_gr_enc_only.y<mb4->vrefs[mb4->eM1].y ||
                             mb4->mv_eM1_gr_enc_only.x<-mb4->vrefs[mb4->eNext].x || 
                             mb4->mv_eM1_gr_enc_only.y<-mb4->vrefs[mb4->eNext].y
                             ){
                                iMult-=2;
                                if(iMult<11)iMult=11;

               }
               else iMult++;
               

                  mb4->iIsBi=//mb4->r.s[0]+mb4->r.s[1]==0 &&
                           (mb4->refId==-1 && mb4->vrefs[mb4->eM1].iSad*iMult> mb4->vrefs[mb4->eNext].iSad*10 && mb4->vrefs[mb4->eNext].iSad<0x0fffffff)
                     ||
                     (mb4->refId>0 && //mb4->vrefs[mb4->eM1].iSad<iGSad && 
                             mb4->vrefs[mb4->eNext].iSad*iMult> mb4->vrefs[mb4->eM1].iSad*10 && mb4->vrefs[mb4->eM1].iSad<0x0fffffff);

                 
                 if(mb4->iIsBi && mb4->vrefs[mb4->eNext].iSad==mb4->vrefs[mb4->eM1].iSad)mb4->iIsBi=0;
               }
               else{
//               mb4->iIsBi=1;
                  iMult=11;
                 
                  mb4->iIsBi=
                           (mb4->refId==-1 && mb4->vrefs[mb4->eM1].iSad*iMult> mb4->vrefs[mb4->eM2].iSad*10 && mb4->vrefs[mb4->eM2].iSad<0x0fffffff)
                     ||
                     (mb4->refId==-2 && 
                              mb4->vrefs[mb4->eM2].iSad*iMult> mb4->vrefs[mb4->eM1].iSad*10 && mb4->vrefs[mb4->eM1].iSad<0x0fffffff);
                 
                 if(mb4->iIsBi && mb4->vrefs[mb4->eM2].iSad==mb4->vrefs[mb4->eM1].iSad)mb4->iIsBi=0;
           }
                 
               
           }
         }
         if(mb4->iIsBi)iHasB++;
      }
   }
   mb4=mbCtx->mb;
   
   int r;

   vlc.addB(iHasB);//has-b

   int iBitPosDbg=vlc.iBitPos;
   //iRlePos=0;

   r=encodeTinaRefs(&vlc,mb4,xc*yc,iCur, iIsB);
   if(iHasB)encodeTinaBi(&vlc,mb4,xc*yc,iCur, iIsB);
   
   int iHas4x4=encodeTinaMode(&vlc,mb4,xc*yc);
   if(iHas4x4)vlc.setBit(iBitPosR+1,1);


   int iBeforeRot=vlc.iBitPos;
   int iHasRot=encodeTinaRot(&vlc,mb4,xc,yc);
   if(iHasRot)vlc.setBit(iBitPosR,iHasRot);else vlc.iBitPos=iBeforeRot;
#if 0
   unsigned char tabsT[20000];
   int to=xc*yc;
   int res=0;
   for(j=0;j<to;j++){
      tabsT[j]=0;
      if(!iIsB){
         if(mb4[j].refId==-2)tabsT[j]|=1;//0 =0
         if(mb4[j].iMVMode)     tabsT[j]|=2;
         if(mb4[j].iMVMode==0 && (mb4[j].r.s[0]|mb4[j].r.s[1]))     tabsT[j]|=4;
   //      res+=vlc.bitsLen[tabsT[j]];
      }
      else{
         if(mb4[j].iIsBi){}//tabsT[j]|=1;
         else {
            if(mb4[j].refId==-1)   tabsT[j]|=1;
            if(mb4[j].refId==-2)   tabsT[j]|=2;// 0= next
            if(mb4[j].iMVMode)     tabsT[j]|=4;
            if(mb4[j].iMVMode==0 && (mb4[j].r.s[0]|mb4[j].r.s[1]))tabsT[j]|=8;
         }
   //      res+=vlc.bitsLen[tabsT[j]];
      }
   }
   res>>=3;

   //int encAritm(unsigned char *out , int *in ,int iCnt);
   int encAritmUC(unsigned char *out , unsigned char *in ,int iCnt);
   unsigned char outTest[40000];
   res=encAritmUC(&outTest[0],&tabsT[0],to);
//int t_zipIt(char* pData, int iDataLen, char *pOut);
   debugss("rle ",(vlc.iBitPos-iBitPosDbg+7)>>3,res);

   int t_zipIt(char* pData, int iDataLen, char *pOut);
   res=t_zipIt((char* )&tabsT[0], to, (char *)&outTest[0]);
   debugss("zip ",res,0);
#endif
   //rleStat[iRlePos]=rle;iRlePos++;
   //iHasRot=1;
//debugss("enc 4x4 rot",iHas4x4,iHasRot);
   int px[3]={0,0,0};
   int py[3]={0,0,0};
   for(j=0;j<yc;j++)
   {
      for(i=0;i<xc;i++,mb4++){

         ::encMB_MV(&vlc,px,py,mb4,iHas4x4);


         if(mb4->iIsBi)encMB_B(&vlc,px,py,mb4,iIsB);

         if(!iIsB)mb4->mv_eM1_gr_enc_only=mb4->vrefs[mb4->eM1];
 /*
         if(0&& iHasB && iIsB && mb4->refId!=-2 && mb4->iMVMode==0 && mgabs(mb4->r.s[0]|mb4->r.s[1])<9){
            if(mb4->iIsBi){
               vlc.addB(1);
               encMB_B(&vlc,px,py,mb4);
            }
            else{
               vlc.addB(0);
            }
         }
   */       
         /*
         if(!iIsB){
            if(mb4->refId!=-1 || mb4->iMVMode)encMB_nonB_m1(&vlc,px,py,mb4);
            mb4->mv_eM1_gr=mb4->vrefs[mb4->eM1];
         }
         */
      }
   }
   iLen=vlc.getBytePos();

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
