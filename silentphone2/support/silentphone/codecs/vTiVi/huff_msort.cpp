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

#include<stdio.h>
#include<string.h>

#define T_CAN_TEST_V

#if 1
#include "tabs.cpp"

int decDifCodeOnes(int sc){
   return tab_dif_bin_dec[sc];
}
int getDifCodeOnes(int val){
   /*
    int ret=tab_dif_bin_enc[val]+1;
    
    if(ret && val!=tab_dif_bin_dec[ret-1]){
    return 0;
    }
    
    
    */
   return tab_dif_bin_enc[val];
}
int findScore(int val, int  *tab, int iSize, int iFindOnly, int iCoefs){
   
   
   int ret=tab_dif_bin_enc[val]+1;
   /*
    if(ret && val!=tab_dif_bin_dec[ret-1]){
    return 0;
    }
    */
   
   return ret;
}

#include "dc_f2_tabsT.inl"
const static  unsigned short *td_fdc_cbp[]={
   &t_fdc_00_dec[0],
   &t_fdc_01_dec[0],
   &t_fdc_02_dec[0],
   &t_fdc_03_dec[0],
   &t_fdc_04_dec[0],
   &t_fdc_05_dec[0],
   &t_fdc_06_dec[0],
   &t_fdc_07_dec[0],
   &t_fdc_08_dec[0],
   &t_fdc_09_dec[0],
   &t_fdc_10_dec[0],
   &t_fdc_11_dec[0],
   &t_fdc_12_dec[0],
   &t_fdc_13_dec[0],
   &t_fdc_14_dec[0],
   &t_fdc_15_dec[0],
};
const static  unsigned short *te_fdc_cbp[]={
   &t_fdc_00_enc[0],
   &t_fdc_01_enc[0],
   &t_fdc_02_enc[0],
   &t_fdc_03_enc[0],
   &t_fdc_04_enc[0],
   &t_fdc_05_enc[0],
   &t_fdc_06_enc[0],
   &t_fdc_07_enc[0],
   &t_fdc_08_enc[0],
   &t_fdc_09_enc[0],
   &t_fdc_10_enc[0],
   &t_fdc_11_enc[0],
   &t_fdc_12_enc[0],
   &t_fdc_13_enc[0],
   &t_fdc_14_enc[0],
   &t_fdc_15_enc[0],
};
int decScoreFlag_DC(int val, int tid){
   const unsigned short *dcf=td_fdc_cbp[tid];
   return dcf[val];
}
int findScoreFlag_DC(int val, int tid){
   const unsigned short *dcf=te_fdc_cbp[tid];
   /*
    static int iMaxF[_DC_F_TABS];
    
    if(iMaxF[tid]==0){
    if(tab_dc_f[tid][val]>0x5fffffff)iMaxF[tid]=1;
    tab_dc_f[tid][val]++;
    
    if(tab_dc_f[tid][val]<3){
    void debugsi(char *c, int a);
    debugsi("val",val);
    debugsi("valx",tab_dc_f[tid][val]);
    }
    }
    */
   return dcf[val];
}

#include "tab_c_l.cpp"

const static  unsigned char *td[]={
   &dif_pic_Y_UV_00_dec[0],
   &dif_pic_Y_UV_01_dec[0],
   &dif_pic_Y_UV_02_dec[0],
   &dif_pic_Y_UV_03_dec[0],
   &dif_pic_Y_UV_04_dec[0],
   &dif_pic_Y_UV_05_dec[0],
   &dif_pic_Y_UV_06_dec[0],
   &dif_pic_Y_UV_07_dec[0],
   &dif_pic_Y_UV_08_dec[0],
   &dif_pic_Y_UV_09_dec[0],
   &dif_pic_Y_UV_10_dec[0],
   &dif_pic_Y_UV_11_dec[0],
   &dif_pic_Y_UV_12_dec[0],
   &dif_pic_Y_UV_13_dec[0],
   &dif_pic_Y_UV_14_dec[0],
   &dif_pic_Y_UV_15_dec[0],
   &dif_pic_Y_UV_16_dec[0],
   &dif_pic_Y_UV_17_dec[0],
   &dif_pic_Y_UV_18_dec[0],
   &dif_pic_Y_UV_19_dec[0],
   &dif_pic_Y_UV_20_dec[0],
   &dif_pic_Y_UV_21_dec[0],
   &dif_pic_Y_UV_22_dec[0],
   &dif_pic_Y_UV_23_dec[0],
};
const static  unsigned char *te[]={
   &dif_pic_Y_UV_00_enc[0],
   &dif_pic_Y_UV_01_enc[0],
   &dif_pic_Y_UV_02_enc[0],
   &dif_pic_Y_UV_03_enc[0],
   &dif_pic_Y_UV_04_enc[0],
   &dif_pic_Y_UV_05_enc[0],
   &dif_pic_Y_UV_06_enc[0],
   &dif_pic_Y_UV_07_enc[0],
   &dif_pic_Y_UV_08_enc[0],
   &dif_pic_Y_UV_09_enc[0],
   &dif_pic_Y_UV_10_enc[0],
   &dif_pic_Y_UV_11_enc[0],
   &dif_pic_Y_UV_12_enc[0],
   &dif_pic_Y_UV_13_enc[0],
   &dif_pic_Y_UV_14_enc[0],
   &dif_pic_Y_UV_15_enc[0],
   &dif_pic_Y_UV_16_enc[0],
   &dif_pic_Y_UV_17_enc[0],
   &dif_pic_Y_UV_18_enc[0],
   &dif_pic_Y_UV_19_enc[0],
   &dif_pic_Y_UV_20_enc[0],
   &dif_pic_Y_UV_21_enc[0],
   &dif_pic_Y_UV_22_enc[0],
   &dif_pic_Y_UV_23_enc[0],
};


int findScoreCntLast_dec_def(int code, int id, int &iCnt, int &iLast);
int findScoreCntLast_def(int iCnt, int iLast, int  id){
   //  return iCnt-1;
   int val=iLast+(iCnt-1)*8;
   //return val;
   //id=0;
   //id=3;
   const unsigned char *t=te[id];
   /*   int c,l;
    findScoreCntLast_dec_def(t[val],  id, c, l);
    // findScoreCntLast_dec_def(val,  id, c, l);
    
    if( c!=iCnt || l!=iLast){
    return -1;
    }
    */
   //return val;
   return t[val];
}
int findScoreCntLast_dec_def(int code, int id, int &iCnt, int &iLast){
   // id=0;
   //iLast=0;
   // iCnt=code+1;
   // return 0;
   //id=3;
   /*
    static int iInitOk=0;   
    if(!iInitOk){
    iInitOk=1;
    for(int j=0;j<24;j++){
    unsigned char *te1=te[j];
    unsigned char *td1=td[j];
    for(int i=0;i<128;i++){
    td1[te1[i]]=i;
    }
    }
    }
    */
   const unsigned char *t=td[id];
   unsigned int ret=t[code];
   
   iCnt=ret>>3;
   iCnt++;
   iLast=ret&7;
   return 0;
   //return t[val];
}



#endif



#if 1
//!defined(__SYMBIAN32__) && !defined(_WIN32_WCE)
#ifdef T_CAN_TEST_V
#define _TEST_T_BITS
#endif

#endif

#ifdef _TEST_T_BITS
//#define T_CAN_TEST 
#endif

template<class T, int iBSize>
class CTSort{
   int iSorted;
   T tmpBuf[iBSize];
   int iChecks;
public:
   int (* _fncCmp)(const void *, const void *);
   int getStat(){return iChecks;}
   T *p;
   int iSize;
   CTSort(void *p, int iSize):p((T *)p),iSize(iSize){
      iSorted=0;
      iChecks=0;
   }
   void doJob()
   {
      iSorted=0;
      sortZ2(0,iSize);
      
   }
   int sortZ2(int iFrom, int iTo)
   {
      int iCnt=0;
      int z,i;
//      int c;
      int iMax=iTo-iFrom;//(iTo);//>>1;//(iTo+1)>>1;
      int j;
      j=0;
      for(z=2;z<=iMax;z<<=1)
      {
         iTo-=z;
         //iTo++;
         int zs2=z>>1;
         for(i=iFrom;i<iTo;i+=z)join(i,i+zs2,i+z);

         iTo+=z;
         if(i+zs2<iTo)
         {
            join(i,i+zs2,iTo);
            j=1;
         }
         else 
         {
            join(i-z,i,iTo);
         }
      }
      //if(getPow(iTo-iFrom)<<iMax)
      if(j)
         join(iFrom,z>>1,iTo);
      return iCnt;
   }

   int sortS(int iFrom , int iTo)
   {
      int ok=1;
      int i;
      for(i=iFrom+1;i<iTo;i++)
      {
         iChecks++;
         if(p[i]<p[i-1]){swap(&p[i],&p[i-1]);ok=0;}
      }
      //puts("");  show();
      return ok;
   }
   int getPow(int i)
   {
      int v=1;
      while(v<=i)v<<=1;
      return v>>1;//(v>>1);
   }
   void sort3(int iFrom, int iTo)
   {
     // iTo=19;
      int d=iFrom+getPow(iTo-iFrom);
      //if(d<2)return;
      sort2(iFrom,d);
    
      if(d!=iTo)
      {
        // printf("-------------marl=%d\n",d-iFrom);
         int iDif=iTo-d;
         if(iDif==1)
         {
         }
         else if(iDif==2)
         {
            join(d,d+1,iTo);
         }
         else if(iDif==3)
         {
            join(d,d+1,d+2);
            join(d,d+2,iTo);
         }
         /*
         else if(iTo-d<16)//shis var buut 0
         {
            sort4(d,iTo);
         }
         */
         else
         {
            sort3(d,iTo);
         }
         join(iFrom,d,iTo);
      }
      

   }
   int sort2(int iFrom, int iTo)
   {
      int iCnt=0;
      int z,i;
      int c;
      int iMax=iTo-iFrom;//(iTo);//>>1;//(iTo+1)>>1;
      for(z=2;z<=iMax;z<<=1)
      {
         for(i=iFrom;i<iTo;i+=z)
         {
            join(i,i+(z>>1),i+z);
            //puts("");
         }
      }
      return iCnt;
   }
   int sortZ(int iFrom, int iTo)
   {
      int iCnt=0;
      int z,i;
      int c;
      int iMax=iTo-iFrom;//(iTo);//>>1;//(iTo+1)>>1;
      int j;
      for(z=2;z<=iMax;z<<=1)
      {
         j=0;
         for(i=iFrom;i<iTo;i+=z)
         {
            if(z+i<=iTo)
               join(i,i+(z>>1),i+z);
            else if(i+(z>>1)<iTo)
            {
               join(i,i+(z>>1),iTo);
               j=1;
            }
            else 
            {
               join(i-z,i,iTo);
            }
            //puts("");
         }
      }
      //if(getPow(iTo-iFrom)<<iMax)
      if(j)
         join(iFrom,z>>1,iTo);
      return iCnt;
   }


   void join(int iFrom, int c, int iTo)
   {
      //printf("j=");show(iFrom,iTo);
      int i;
      register T *l1=&p[iFrom];
      register T *l2=&p[c];
      if(iFrom+2==iTo && _fncCmp(l1,l2))return;
      register T *l1m=l2;
      register T *l2m=&p[iTo];
      

      register T *r=&tmpBuf[0];
     /*
      int l=iTo-iFrom;
      for(i=0;i<l;i++)
      {
         //iChecks++;
         if(l1<l1m && (*l1<*l2 || l2>=l2m))
         {
            *r=*l1;l1++;
         }
         else
         {
            *r=*l2;l2++;
         }
         r++;
      }
      */
      while(l1<l1m && l2<l2m)
      {
        // iChecks++;
         if(_fncCmp(l1,l2))//*l1<*l2)
         {
            *r=*l1;l1++;
         }
         else {*r=*l2;l2++;}
         r++;
      }
      
      while(l1<l1m)
      {
        *r=*l1;l1++;r++;
      }
#if 0
      if(l2<l2m)
      {
         iTo-=(l2m-l2);
      }
#else
      while(l2<l2m)
      {
        *r=*l2;l2++;r++;
      }
#endif
      
      r=&tmpBuf[0];
      for(i=iFrom;i<iTo;i++)
      {
         p[i]=*r;
         r++;
      }
      //show(iFrom,iTo);puts("");
   }
   int sort4(int iFrom, int iTo)
   {
      int st=iFrom+((iTo-iFrom+1)>>1);
     if(st-iFrom>1)sort4(iFrom,st);
     if(iTo-st>1)sort4(st,iTo);
     //rec++;
      
      //if(rec)
      {
         //printf("<r %2d %2d %2d rec=%2d>\n",iFrom,st,iTo,rec-1);
         join(iFrom,st,iTo);
         

         //while(!sortAll(iFrom,iTo));
         return 0;
      }
      return 0;//sortAll(iFrom,iTo);
      //enum{eMax=0x7f};
      //show();
      //iSorted++;
      //if(!iSwaps){iSorted=iSize;return;}
   }
   inline void swap(T *t1, T *t2)
   {
      T tmp=*t1;*t1=*t2;*t2=tmp;
    //  printf("<s=(%d %d)>",*t1,*t2);
   }
   /*
   void show(int iFrom=0, int iTo=0)
   {
      printf("[");
      if(iTo==0)iTo=iSize;
      for(int i=iFrom;i<iTo;i++)printf("%c",p[i]);
      printf("] %d",iChecks);
   }
   */

};

typedef struct{ 
   int pos;
   int val;
}T_HUFF_TAB;

int compare_H(const void *a, const void *b)
{
   //iScmps++;
   //T_HUFF_TAB* ca=(T_HUFF_TAB*)a;
   //T_HUFF_TAB* cb=(T_HUFF_TAB*)b;
   //if(((T_HUFF_TAB*)a)->val==((T_HUFF_TAB*)b)->val)
     // return ((T_HUFF_TAB*)a)->pos>((T_HUFF_TAB*)b)->pos;
   return ((T_HUFF_TAB*)a)->val>((T_HUFF_TAB*)b)->val;
}

int sortHuff(void *h, int iSize)
{
   int iFrom=0;
   int iTo=0;

   T_HUFF_TAB *hV=(T_HUFF_TAB *)h;
   T_HUFF_TAB *hl=(T_HUFF_TAB *)h;

   int i;
   int iSizeNew=0;
   for(i=0;i<iSize;i++)
   {
      if(hV->val)
      {
         *hl=*hV;
         hl++;
         iSizeNew++;
      }
      hV++;
   }
   if(iSizeNew>1){
      hV=(T_HUFF_TAB *)h;

      CTSort<T_HUFF_TAB,2048> so(hV,iSizeNew);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   if(iSizeNew)memset((T_HUFF_TAB *)h+iSizeNew,0,(iSize-iSizeNew)*sizeof(T_HUFF_TAB));
   /*

   hl+=iSize-1;
   while(hV->val==0){hV++;iFrom++;}
   while(hl->val==0){hl--;iTo++;}


   int l=iSize-iFrom-iTo;
   if(l<=0)return 0;

   CTSort<T_HUFF_TAB,2048> so(hV,l);
   so._fncCmp=&compare_H;
   so.doJob();
   //printf("\n ---r=%d----\n",iScmps);

   if(iFrom)
   {
      if(h!=hV)memmove(h,hV,(l)*sizeof(T_HUFF_TAB));
      hV=(T_HUFF_TAB*)h;
      hV+=l;
      memset(hV,0,(iSize-l)*sizeof(T_HUFF_TAB));
   }
   */
   return 0;
}
#if 0

#if 0
//def T_CAN_TEST


#define RENEW_TABS 0
#if RENEW_TABS

void createTable(char *name, T_HUFF_TAB *t, int iSize,char *fn="tabs.cpp"){

   int i,val;
   int *enc=new int [iSize];
   int *dec=new int [iSize];

   for(val=0;val<iSize;val++){
      for(i=0;i<iSize;i++){if(t[i].pos==val){enc[val]=i;break;}}
//      for(i=0;i<iSize;i++){if(enc[i]==val){dec[val]=i;break;}}
      //td1[te1[i]]
      
   }
   for(i=0;i<iSize;i++){dec[enc[i]]=i;}
   FILE *f=fopen(fn,"a+");

   fputs("//----------",f);
   fprintf(f,"\nstatic const unsigned short %s_enc[]={\n",name);
   for(i=0;i<iSize;i++){
      fprintf(f,"%6d,",enc[i]);if(i+1<iSize && (i&15)==15)fprintf(f,"\n   ");
   }
   fputs("\n};\n",f);

   fputs("//----------",f);
   fprintf(f,"\nstatic const unsigned short %s_dec[]={\n",name);
   for(i=0;i<iSize;i++){
      fprintf(f,"%6d,",dec[i]);if(i+1<iSize && (i&15)==15)fprintf(f,"\n   ");
   }
   fputs("\n};\n",f);

   fclose(f);


   delete enc;
   delete dec;
}
void testTab(){
   int tab[]={1,2,4,3,7,0,8,0,0,0,0,0,};
   static T_HUFF_TAB t[8];
   int i,iSize=8;
   for(i=0;i<iSize;i++){
      t[i].pos=i;t[i].val=tab[i];
   }
   CTSort<T_HUFF_TAB,16> so(t,iSize);
   so._fncCmp=&compare_H;
   so.doJob();
   createTable("a",&t[0],iSize);

}
void createTable_int(char *name, int *tab, int iSize,char *fn){
   int i;
   T_HUFF_TAB *t=new T_HUFF_TAB[iSize*2];
   for(i=0;i<iSize;i++){
      t[i].pos=i;t[i].val=tab[i];

   }
   CTSort<T_HUFF_TAB,32768> so(t,iSize);
   so._fncCmp=&compare_H;
   so.doJob();
   createTable(name,t,iSize,fn);
   delete t;
}

static int i_mTCnt[32768];
int iInitCnt=1;
rwerw
#endif


int zdecDifCodeOnes(int sc){
   return tab_dif_bin_dec[sc];
}
int zgetDifCodeOnes(int val){
   /*
   int ret=tab_dif_bin_enc[val]+1;
   
   if(ret && val!=tab_dif_bin_dec[ret-1]){
      return 0;
   }
   

   */
   return tab_dif_bin_enc[val];
}
int zfindScore(int val, int  *tab, int iSize, int iFindOnly, int iCoefs){

   
   int ret=tab_dif_bin_enc[val]+1;
   /*
   if(ret && val!=tab_dif_bin_dec[ret-1]){
      return 0;
   }
   */

   return ret;
#if 0
   if(val>=32768)return 0;


   static T_HUFF_TAB *t=NULL;
   static int iPrevSize=0;
      int i;
      if(iSize>iPrevSize){
         if(t)delete t;
         t=new T_HUFF_TAB[iSize*3];
         iPrevSize=iSize;
      }
   if(!iFindOnly){
      //testTab();
      iInitCnt=1;
      for(i=0;i<iSize;i++){
         t[i].pos=i;t[i].val=tab[i];

      }
      CTSort<T_HUFF_TAB,32768> so(t,iSize);
      so._fncCmp=&compare_H;
      so.doJob();
//      createTable("tab_dif_bin",t,iSize);
   }
   
   if(iInitCnt){
      iInitCnt=0;
      
      int i,j,sc=1;
      int rem=0;
      /*
      int remThis(int dv,int sc);

      for(i=0;i<32767;i++){
         int r=0;
         while(remThis(t[i].pos,sc) && i<32767){rem++;r++;i++;}
         if(r){
            const int to=32767-r-1;
            for(j=i;j<to;j++){
               t[j]=t[j+r];
            }
         }

         
         else sc++;
      }
*/
      for(i=0;i<32768;i++)i_mTCnt[i]=0;

      int f=0;
      for(i=0;i<32768;i++){
         //f=0;
         //if(i_mTCnt[i])continue;
         for(j=0;j<iSize;j++){if(t[j].pos==i){i_mTCnt[i]=j+1;break;}}
         //if(!f)break;
         
      }
      for(i=0;i<32768;i++)if(!i_mTCnt[i])i_mTCnt[i]=32767;
      //void debugc(char *c);


   }
   return i_mTCnt[val];
   if(iCoefs==0){
      for(i=0;i<iSize;i++){if(t[i].pos==val)return i+1;}
   }
   else{
      int r=1;
      for(i=0;i<iSize;i++){if(t[i].pos==val)return r;if(i_mTCnt[val]==iCoefs)r++;}

   }
   return 0;
#endif
}
#if 0
int findScore16(int val, int  *tab){
#define _C16 (256*16)
   static int iSort=0;
   iSort++;
   static int *prevT=NULL;

   static T_HUFF_TAB t[_C16];
   int i;
   if(iSort<1000 || tab[val]<2 || (iSort&31)==0 || prevT!=tab){
      prevT=tab;
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val)return i;}
   return i;
}


#endif
#undef _C16
int findScore32K(int val){
#define _C16 (32768)
   static int iSort=0;
   static int tab[32769];
   val++;
   static int iMaxF=5;
   tab[val]++;
   if(tab[val]==1){return (iMaxF>>1)+5;}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if((tab[val]==2||  (iSort&127)==127)){
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF<i)iMaxF=i;return i;}}
   return iMaxF;
}
#if 0
#undef _C16
int findScoreTR_COEFS(int val, int tid){
#define _C16 (16*16)
//#define _C16 (128*8)
#define _TABS (16)
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid]+1;}//
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<4 ||  (iSort&(255))==255){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   if((iSort&((1<<15)-1))==511 ){
      void debugsi(char *c, int a);
      for(i=0;i<40;i++){char xx[]="a    00";xx[1]=tid&1?'p':'d';xx[2]=tid&2?'x':' ';xx[3]=tid&4?' ':'k';xx[0]='0'+tid;xx[2+3]=(i/10)+'0';xx[3+3]=(i%10)+'0';if(i<4 || (i&1))debugsi(&xx[0], t[i].val);}
   }
   for(i=0;i<=iMaxF[tid];i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
   
   //return 0;

}
#endif
#undef _C16
/*
int findScore32K_t(int val, int tid){
#define _C16 (256*256)
//#define _C16 (128*8)
#define _TABS (16)
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid]+1;}//
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<4 ||  (iSort&(255))==255){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   if((iSort&((1<<15)-1))==511 ){
      void debugsi(char *c, int a);
      for(i=0;i<40;i++){char xx[]="a    00";xx[1]=tid&1?'p':'d';xx[2]=tid&2?'x':' ';xx[3]=tid&4?' ':'k';xx[0]='0'+tid;xx[2+3]=(i/10)+'0';xx[3+3]=(i%10)+'0';if(i<4 || (i&1))debugsi(&xx[0], t[i].val);}
   }
   for(i=0;i<=iMaxF[tid];i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
   
   //return 0;

}
#undef _C16
*/

#define RENEW_TABS 0
#if RENEW_TABS
static int t_cbp[20*32];
void save_cbp_tabs(){
   
   int *t=&t_cbp[0];
   int i;
#define CBP_TABS 20
   char bufN[64];
   for(i=0;i<CBP_TABS;i++){
      sprintf(bufN,"t_%02d",i);
      createTable_int(&bufN[0],&t[i*32],32,"cbp_tabsT.inl");
   }
}


#define _C16 (8*64)
#define _DC_F_TABS 16
static int tab_dc_f[_DC_F_TABS][_C16];
void save_f_dc_tabs(){
   
  // int *t=&tab_dc_f[0];
   int i;
   char bufN[64];
   for(i=0;i<_DC_F_TABS;i++){
      sprintf(bufN,"t_fdc_%02d",i);
      createTable_int(&bufN[0],&tab_dc_f[i][0],_C16,"dc_f3_tabsT.inl");
   }
}



class CT_CR_TABS{
public:
   CT_CR_TABS(){}
   ~CT_CR_TABS(){
     // save_f_dc_tabs();
   }
};
CT_CR_TABS cr_t;
#endif

#if RENEW_TABS
int findScoreFlag_DCz(int val, int tid){
//#define _C16 (128*8)
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int iMaxF[_DC_F_TABS];
   static int iPrevTab=-1;
   tab_dc_f[tid][val]++;
   if(tab_dc_f[tid][val]==1){iMaxF[tid]++;return iMaxF[tid]+1;}//
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab_dc_f[tid][val]<4 ||  (iSort&(255))==255){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab_dc_f[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   /*
   if((iSort&((1<<15)-1))==511 ){
      void debugsi(char *c, int a);
      for(i=0;i<40;i++){char xx[]="a    00";xx[1]=tid&1?'p':'d';xx[2]=tid&2?' ':'k';xx[0]='0'+tid;xx[2+3]=(i/10)+'0';xx[3+3]=(i%10)+'0';if(i<4 || (i&1))debugsi(&xx[0], t[i].val);}
   }
   */
   for(i=0;i<=iMaxF[tid];i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
   
   //return 0;

}
#endif
#if 0
#undef _C16
int findScoreFlagPredX4(int val, int tid){
#define _C16 (16)
//#define _C16 (128*8)
#define _TABS (256)
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid]+1;}//
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<4 ||  (iSort&(255))==255){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   /*
   if((iSort&((1<<12)-1))==511 ){
      void debugsi(char *c, int a);
      for(i=0;i<40;i++){char xx[]="a    00";xx[1]=tid&1?'p':'d';xx[2]=tid&2?' ':'k';xx[0]='0'+tid;xx[2+3]=(i/10)+'0';xx[3+3]=(i%10)+'0';if(i<4 || (i&1))debugsi(&xx[0], t[i].val);}
   }
   */
   for(i=0;i<=iMaxF[tid];i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
   
   //return 0;

}

#undef _C16

int findScoreCoefRun(int val, int tid){
#define _C16 (16*16*2)
//#define _C16 (128*8)
#define _TABS (8)
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid]+1;}//
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<4 ||  (iSort&(_C16-1))==15){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<=iMaxF[tid];i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
   
   //return 0;

}


#undef _C16
#undef _TABS




static int *txxx;
void saveTx(){
  // createTable_int("tab_4x4_coefs",txxx,32768,"tab_4x4_coefs.inl");
}

int findScore4x4Skips(int val, int tid){
#define _C16 (256*128)
#define _TABS (1)
#if 0
   val&=(_C16-1);
   int r=tab_4x4_coefs_enc[val];
   if(r<1024) return r;
   return 1023;
#else
   int testT=tid==11;
   int sortx=tid==12;
   if(val>=_C16)val&=(_C16-1);
   tid=0;
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   
   tab[tid][val]++;
   if(tab[tid][val]==1 && !sortx){iMaxF[tid]++;return iMaxF[tid];}
   iSort++;
   //static int iSorted=0;
   //if(iSort<5000)return 512;
   static int iSorted=0;
   
   txxx=&tab[tid][0];

   static T_HUFF_TAB t[_C16];
   int i;
   if(!iSorted && sortx){iSorted=1;// || ( (iSort>500 && iMaxF[tid]<1000 && (tab[tid][val]<4))))){
//   if(!iSorted && ((sortx) || (iSort&63)==63)){iSorted=0;// || ( (iSort>500 && iMaxF[tid]<1000 && (tab[tid][val]<4))))){
      
      iPrevTab=tid;
      
//      iSorted=1;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();

      ///createTable_int("tab_4x4_coefs",&tab[tid][i],_C16,"tab_4x4_coefs.inl");
   }
   //if(!iSorted)return 
   int l=iSorted?4096:_C16;
   for(i=0;i<l;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
#endif
}


#undef _C16
#define _TABS4x4 (16*16*4*8*4)
#define _C16_4x4 (16)
int findScore4x4(int val, int tid){
   /*
#define _C16 (_C16_4x4)
#define _TABS _TABS4x4
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
   */
   return 0;
}
#undef _C16
#undef _TABS
int findScore4x4v(int val, int tid){
#define _C16 32
#define _TABS _TABS4x4
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid];}//
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&15)==15){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<iMaxF[tid];i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
   
   //return 0;

}
#undef _C16
#undef _TABS


/*
int findScore4x4dc(int val, int tid){
#define _C16 (_C16_4x4)
#define _TABS _TABS4x4
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   val++;
   tab[tid][val]++;
   if(tab[tid][val]==1){return !iMaxF[tid]?1:iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
}
#undef _C16
#undef _TABS

int findScore4x4Pic(int val, int tid){
#define _C16 (_C16_4x4)
#define _TABS _TABS4x4
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   val++;
   tab[tid][val]++;
   if(tab[tid][val]==1){return !iMaxF[tid]?1:iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
}
#undef _C16
#undef _TABS

*/

int findScore2x2(int val, int tid){
#define _C16 (16)
#define _TABS (16*17)
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   val++;
   tab[tid][val]++;
   if(tab[tid][val]==1){return iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
}
#undef _C16
#undef _TABS


int findScoreMode(int val, int tid, int iAdd){
#define _C16 (32)
#define _TABS (1<<16)
   //5m 5r 5b m2,m1,n
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   
   if(iAdd){
      tab[tid][val]+=iAdd;
      if(tab[tid][val]==iAdd){iMaxF[tid]++;}
      return 0;
   }
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<=iMaxF[tid];i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
}
#undef _C16
#undef _TABS

int findScoreModeSplit(int val, int tid){
#define _C16 (32)
#define _TABS 16
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   
   tab[tid][val]++;
   if(tab[tid][val]==1){iMaxF[tid]++;return iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
}
#undef _C16
#undef _TABS
int findScoreCntLast_defx(int iCnt, int iLast, int  tid){
 //  return iCnt-1;
   int val=iLast+(iCnt-1)*8;
#define _C16 (128)
#define _TABS 24
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   val++;
   tab[tid][val]++;
   if(tab[tid][val]==1){return iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
}
#undef _C16
#undef _TABS

int findScoreDC(int val, int tid){
#define _C16 (256)
#define _TABS 4
   static int iSort=0;
   static int tab[_TABS][_C16+1];
   static int iMaxF[_TABS];
   static int iPrevTab=-1;
   val++;
   tab[tid][val]++;
   if(tab[tid][val]==1){return iMaxF[tid];}
   iSort++;
   

   static T_HUFF_TAB t[_C16];
   int i;
   if(iPrevTab!=tid || tab[tid][val]<8 ||  (iSort&31)==31){
      iPrevTab=tid;
   
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[tid][i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val){if(iMaxF[tid]<i)iMaxF[tid]=i;return i;}}
   return iMaxF[tid];
}
#undef _C16
#endif

int dTabs=0;
void setFlagDebugT(){dTabs=1;}
#if 0
#include "cbp_tabs.inl"
   const static  unsigned char *td_cbp[]={
      &t_00_dec[0],
      &t_01_dec[0],
      &t_02_dec[0],
      &t_03_dec[0],
      &t_04_dec[0],
      &t_05_dec[0],
      &t_06_dec[0],
      &t_07_dec[0],
      &t_08_dec[0],
      &t_09_dec[0],
      &t_10_dec[0],
      &t_11_dec[0],
      &t_12_dec[0],
      &t_13_dec[0],
      &t_14_dec[0],
      &t_15_dec[0],
      &t_16_dec[0],
      &t_17_dec[0],
      &t_18_dec[0],
      &t_19_dec[0],
   };
   const static  unsigned char *te_cbp[]={
      &t_00_enc[0],
      &t_01_enc[0],
      &t_02_enc[0],
      &t_03_enc[0],
      &t_04_enc[0],
      &t_05_enc[0],
      &t_06_enc[0],
      &t_07_enc[0],
      &t_08_enc[0],
      &t_09_enc[0],
      &t_10_enc[0],
      &t_11_enc[0],
      &t_12_enc[0],
      &t_13_enc[0],
      &t_14_enc[0],
      &t_15_enc[0],
      &t_16_enc[0],
      &t_17_enc[0],
      &t_18_enc[0],
      &t_19_enc[0],
   };

int findScore_cbp(int val, int  id){

   return te_cbp[id][val];
}



int findScore_cbpAdd(int val, int  id){
#define _C16 (32)
   static int iSort=0;
   iSort++;
   static int *prevT=NULL;

   int *tab=&t_cbp[id];
   //if(!t_cbp || (int)tab<(int)t_cbp)t_cbp=tab;

   static T_HUFF_TAB t[_C16];
   int i;
   if(iSort<1000 || tab[val]<2 || (iSort&31)==0 || prevT!=tab){
      prevT=tab;
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
      /*
      if(dTabs){
         dTabs=0;
         void debugsi(char *c, int a);
         for(i=0;i<_C16;i++)
            debugsi("t",t[i].pos);
      }
      */
   }
   for(i=0;i<_C16;i++){if(t[i].pos==val)return i;}
   return i;
}


int findScoreCntLast_dc(int iCnt, int iLast, int  *tab){
#undef _C16
#define _C16 (16*16)
   int val=iLast+(iCnt-1)*16;

   static int iSort=0;
   iSort++;
   static int *prevT=NULL;

   static T_HUFF_TAB t[_C16];
   int i;
   if(tab[val]<3 || (iSort&31)==0 || prevT!=tab){
      prevT=tab;
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   }
   tab[val]++;
   
   for(i=0;i<_C16;i++){if(t[i].pos==val)return i;if(!t[i].val)return i;}
   return i;
}
#endif

//T_CAN_TEST_V
#if 0
int findScoreCntLast(int iCnt, int iLast, int  *tab){
#undef _C16
#define _C16 (16*8)
   int val=iLast+(iCnt-1)*8;

   static int iSort=0;
   iSort++;
   static int *prevT=NULL;

   static T_HUFF_TAB t[_C16];
   int i;
   tab[val]++;
   if(tab[val]==1)return 15;
   if(tab[val]<3 || (iSort&31)==0 || prevT!=tab){
      prevT=tab;
      for(i=0;i<_C16;i++){
         t[i].pos=i;t[i].val=tab[i];
      }

      CTSort<T_HUFF_TAB,_C16> so(&t[0],_C16);
      so._fncCmp=&compare_H;
      so.doJob();
   static int iX=32;
   if(t[0].val>iX ){
      iX*=2;
      void debugsi(char *c, int a);
      //xx[0]='0'+tid;xx[1]=tid&1?'p':'d';xx[2]=tid&2?'x':' ';xx[3]=tid&4?' ':'k';
      for(i=0;i<40;i++){char xx[]="a    00";xx[2+3]=(i/10)+'0';xx[3+3]=(i%10)+'0';if(i<4 || (i&1))debugsi(&xx[0], t[i].val);}
   }
   }

   
   for(i=0;i<_C16;i++){if(t[i].pos==val)return i;}
   return i;
}
#endif
#endif
#if 0

int sortHuff_o(void *h, int iSize)
{
   /*
   CTSort<T_HUFF_TAB,2048> sot(h,iSize);
   sot._fncCmp=&compare_H;
   sot.doJob();
   return 0;
*/
   int iFrom=0;
   int iTo=0;

   T_HUFF_TAB *hV=(T_HUFF_TAB *)h;
   T_HUFF_TAB *hl=(T_HUFF_TAB *)h;
   hl+=iSize-1;
   while(hV->val==0){hV++;iFrom++;}
   while(hl->val==0){hl--;iTo++;}


   int l=iSize-iFrom-iTo;
   if(l<=0)return 0;

   CTSort<T_HUFF_TAB,2048> so(hV,l);
   so._fncCmp=&compare_H;
   so.doJob();
   //printf("\n ---r=%d----\n",iScmps);

   if(iFrom)
   {
      if(h!=hV)memmove(h,hV,(l)*sizeof(T_HUFF_TAB));
      hV=(T_HUFF_TAB*)h;
      hV+=l;
      memset(hV,0,(iSize-l)*sizeof(T_HUFF_TAB));
   }
   return 0;
}
#define T int
int toDif(T *in, int iLen)//, int *out)
{
   int i,l=0;
   T iPrev=in[0];
   int iCur;
   //out[l]=iPrev;
   for(i=1;i<iLen;i++)
   {
      iCur=in[i];
      in[i]=iCur-iPrev;
      iPrev=iCur;
   }
   return iLen;
}

#ifndef max
#define max(A,B) ((A)>(B)?(A):(B))
#define min(A,B) ((A)<(B)?(A):(B))
#endif
inline int getMed(int a, int b, int c)
{
   return min(max(a, b),
                  min(max(b, c), max(a, c)));
}
int pack(int *out,int *p, int *r, int cur, int xc, int yc)
{
   int i,j,k2,k4;
   int *outo=out;
   int rid;
   int iBig;
   for(j=0;j<yc;j++)
   for(i=0;i<xc;i++)
   {
      rid=*r;if(rid>80){if(rid>180){rid-=200;iBig=2;}else{ rid-=100;iBig=1;}}
      if(rid!=cur)continue;
      int med;
      if(j && i)
      {
        med =getMed(p[0],p[-xc], p[-1]);
      }
      else if(i)med=p[-1];
      else if(j)med=p[-xc];
      else med=0;

      *out=*p-med;
      p++;out++;
   }
   return out-outo;

}
int packX(int *out,int *p, int *r, int cur, int xc, int yc)
{
   int i,j,k2,k4;
   int *outo=out;
   int rid;
   int iBig;
   for(j=0;j<yc;j++)
   for(i=0;i<xc;i++)
   {
      iBig=0;
      rid=*r;if(rid>80){if(rid>180){rid-=200;iBig=2;}else{ rid-=100;iBig=1;}}
      if(rid==cur)
      {
         if(!iBig)
         {
            *out=*p;
            p++;out++;
         }
         else if(iBig==2)
         {
            *out=*p;
            p++;out++;
            *out=*p;
            p++;out++;
         }
         else
         {
            int iPrev=0;
            int iMode=1;
            int tabMode[]={1,2,4,8};
            int *px=p;
            for(k2=0;k2<4;k2++)
            {
               if(*px==75)
               {
                  iMode|=tabMode[k2];
                  px+=5;
               }
               else px++;
            }
            int vx[20];
            int vy[20];
            
            *out=(iMode-1);
            out++;
            {
               for(k2=0;k2<4;k2++)
               {
                  if((iMode & tabMode[k2])==0)
                  {
                     int iCur=*p;
                     *out=iCur-iPrev;
                     iPrev=iCur;
                     p++;out++;
                     continue;
                  }
                  p++;
                  for(k4=0;k4<4;k4++)
                  {
                     int iCur=*p;
                     *out=iCur-iPrev;
                     iPrev=iCur;
                     p++;out++;
                  }
               }

            }

            /*
            for(k2=0;k2<4;k2++)
            {
               *out=*p;

               if(*p==75)
               {
                  p++;out++;
                  for(k4=0;k4<4;k4++)
                  {
                     *out=*p;
                     p++;out++;
                  }
               }
               else
               {
                  p++;out++;
               }
            }
            */
         }
      }
      else p++;
   }
   /*
   if(out!=outo)
   {
      *out=cur;out++;
      *out=out-outo;out++;
   }
   */
   return out-outo;
}
#if 1
int encAritm(unsigned char *out , int *in ,int iCnt);
int aencode2(unsigned char *buf, int *i, int iCnt);
int testVecEnc(int *v1,int *v2, int *r, int iVLen, int xc, int yc)
{
   unsigned char out[28000];
   int tmp[46000];
   int iCnt=0;
   /*
   //30% gain
   memcpy(tmp,v1,sizeof(int)*iVLen);
   memcpy(&tmp[iVLen],v2,sizeof(int)*iVLen);
   memcpy(&tmp[iVLen*2],r,sizeof(int)*xc*yc/4);

   iCnt=iVLen*2+xc*yc/4;

   */
   xc/=2;yc/=2;

   int j,l1=0,l2=0;
   int ret=0;
   j=0;
   for(j=-4;j<8;j++)
   {
      l1+=packX(&tmp[l1],v1,r,j,xc,yc);
   }
   //toDif(&tmp[0],l1);
   //ret+=encAritm(&out[0],&tmp[0],l1);l1=0;
   for(j=-4;j<8;j++)
   //j=0;
   {
      l1+=packX(&tmp[l1],v2,r,j,xc,yc);
   }
   //toDif(&tmp[0],l1);
   int m1=encAritm(&out[0],&tmp[0],l1);

   //int m2=aencode2(&out[0],&tmp[0],l1);

   //if(m1<m2)
      ret+=m1;///>>2);
   //else
     // ret+=m2;
   l1=0;
   
   //toDif(&tmp[0],l1);
   

   //aencode2

   //toDif(&tmp[0],l2);
   //ret+=encAritm(&out[0],&tmp[0],l2);

   //m1=encAritm(&out[0],r,xc*yc);
   //
   int xx=xc*yc;
   for(j=0;j<xx;j++)tmp[j]=r[j]>200?(r[j]-180):(r[j]>80?(r[j]-100):r[j]);
   m1=encAritm(&out[0],&tmp[0],xx);
   //m2=aencode2(&out[0],r,xc*yc);
   //if(m1<m2)
      ret+=m1;
      //ret-=40;
   //else
     // ret+=m2;
   //return encAritm(&out[0],&tmp[0],iCnt)+  encAritm(&out[0],r,xc*yc);
   //memcpy(&tmp[iCnt],r,sizeof(int)*xc*yc);iCnt+=xc*yc;
   //toDif(&tmp[0],iCnt);

   return ret;//encAritm(&out[0],&tmp[0],iCnt);
}
#endif

#endif
#endif