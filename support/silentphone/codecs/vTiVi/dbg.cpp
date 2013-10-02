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

#include <windows.h>
#include <stdio.h>
void sleepAfterKey(int keys){
   //return;
   if(keys>1)
     Sleep(60);
   else Sleep(30);
}
void debugDig(int v, unsigned char *p, int stride, int iInv){
   static const char *dig[]={
      "0110"
      "1001"
      "1001"
      "1001"
      "0110",//0

      "0010"
      "0010"
      "0010"
      "0010"
      "0010",//1

      "1110"
      "0001"
      "0110"
      "1000"
      "1111",//2

      "1110"
      "0001"
      "1110"
      "0001"
      "1110",//3

      "1001"
      "1001"
      "1001"
      "1111"
      "0001",//4

      "1111"
      "1000"
      "1110"
      "0001"
      "1110",//5

      "0111"
      "1000"
      "1110"
      "1001"
      "0110",//6
      
      "1111"
      "0001"
      "0010"
      "0100"
      "1000",//7

      "0110"
      "1001"
      "0110"
      "1001"
      "0110",//8

      "0110"
      "1001"
      "0111"
      "0001"
      "0110",//9
   };
   int i,j,pos=0;
   int posn,posp;
   for(j=0;j<5;j++){
      for(i=0;i<4;i++){

         if(iInv){
            pos=i+4*(4-j);
         }
         else pos=i+4*(j);
         if(dig[v][pos]=='1'){
            //int c=(int)p[i*3]+128;
            //if(p[i*3]>232 && c>255)c-=255;else c=255;
            p[i*3]=255;
            p[i*3+1]=192;
            //if(!i || dig[v][pos-1]=='0')p[i*3-3]=128;
            //if(i==3 || dig[v][pos+1]=='0')p[i*3+3]=128;
         }
         
      }
      p+=stride;
   }

}

void debugIntValue(int v, unsigned char *p, int stride){
   int i,iDigs=0;
   int tmp=v;
   int d[10];
   do{d[iDigs]=tmp%10;tmp/=10;iDigs++;}while(tmp>0);
   for(i=0;i<iDigs;i++){
      debugDig(d[iDigs-i-1],p+i*15,stride,1);

   }

}

static int iDbgR;

void debugsi(char *c, int a)
{
#ifdef _WINDOWS_
   HDC hdc=GetDC(0);
   char buf[512];
   int l;
   if(strlen(c)>32){
      l=sprintf(buf,"%.*s   %d   ",32,c,a);
   }
   else l=sprintf(buf,"%s %d",c,a);
   TextOutA(hdc,0,iDbgR,&buf[0],l);
   iDbgR+=15;
   TextOutA(hdc,0,iDbgR,"                                                                 ",45);
   if(iDbgR>700)iDbgR=0;
   ReleaseDC(0,hdc);
#endif
}
void debugs2i(char *c, int a, int b)
{
#ifdef _WINDOWS_
   HDC hdc=GetDC(0);
   char buf[64];
   int l;
   l=sprintf(buf,"%s %d %d  ",c,a,b);
   TextOutA(hdc,0,iDbgR,&buf[0],l);
   iDbgR+=15;
   TextOutA(hdc,0,iDbgR,"                                                                 ",45);
   if(iDbgR>700)iDbgR=0;
   ReleaseDC(0,hdc);
#endif
}

void debugss(char *c, int a, int b)
{
   return;
#ifdef _WINDOWS_
   HDC hdc=GetDC(0);
   char buf[256];
   if(c==NULL){iDbgR=0;c="mark";}
   int l=sprintf(buf,"%s",c);
   if(a!=-123456789)
   {
      l+=sprintf(buf+l,"=%d     ",a);
   }
   if(b!=-123456789)
   {
      l+=sprintf(buf+l,",%d     ",b);
   }
   TextOutA(hdc,0,iDbgR,&buf[0],l);
   iDbgR+=15;
   TextOutA(hdc,0,iDbgR,"                                                                 ",45);
   if(iDbgR>700)iDbgR=0;
   ReleaseDC(0,hdc);
#endif
}
#if !defined(__SYMBIAN32__) && !defined(_WIN32_WCE)
#ifdef T_CAN_TEST_V
#define _TEST_T_BITS
#endif
#endif


#ifdef _TEST_T_BITS

static int stats[32768];
static int iMaxZZ=0;
void saveFile(const char *fn,void *p, int iLen);
char *loadFile(const  char *fn, int &iLen);
int findScore(int val, int  *tab, int iSize,int f, int iCoefs);

#define MAX_SC 32768
//32768
static int iSaveFlag=0;
static int iCanUpdate=1;
int getSaveFlag(){
   if(iSaveFlag){
      iSaveFlag=0;
      return  1;
   }
   return 0;
}
void picDataSLS(int iParam){
   if(iParam==1){
      iSaveFlag=1;

   }
   if(iParam==3){
      iCanUpdate=0;
      debugss("can update",0,0);
   }
   if(iParam==4){
      iCanUpdate=1;
      debugss("can update",1,1);
   }
   /*
   if(iParam==1 && iMaxZZ>10000){
      saveFile("difCodes.int",&stats[0],sizeof(int)*32768);
      int sc=findScore(0,&stats[0],MAX_SC,0,0);
      debugss("saved",0,0);
   }
   */
   
   if(iParam==2){
      int l=0;
      char *p=loadFile("difCodes.int",l);
      if(p){
         iMaxZZ=1000000;
         /*
        debugss("loaded",0,0);
         memcpy(&stats[0],p,sizeof(int)*32768);
         delete p;
         int sc=findScore(0,&stats[0],MAX_SC,0,0);
         */
      }
   }
   

}
unsigned char get_3xL[33000];

int getLen2x_testI(int v, int st){
   /*
   0
   1xx0  1--4
   1xx1xx0 5--21
   1xx1xx1xx0 22 54
   */
   /*
   0
   1x0
   1x1x0

   0
   100
   110
   10100
   10110
   11100
   11110
   */
   int l=1;
   for(int n=st;v>0;n+=st){v-=(1<<n);l+=(st+1);}
   //dec
   /*
   //st=2
   int v=0,n=0,x=0,a=0;
   while(bit[i++]){
      a+=1<<n;
      if(bit[i++])v|=1<<n;n++;
      if(bit[i++])v|=1<<n;n++;
   }
   v+=a;
   */
   //for(n=2;v>0;n+=2){v-=(1<<n);l+=3;}
   //for(n=1;v>0;n+=1){v-=(1<<n);l+=2;}
//2 l+2;v-=2
//7 7-2=5;b=2+1; 5-4
   //for(n=0;v>0;n+=1){v-=(1<<n);l+=2;}
   //for(n=0;bit;n+=2,ofs+=1<<n;){ofs<<=7; ofs|=7bit ;}
   return l;
}
int getLen2x_test(int v){
   static int iInitOk=0;
   if(!iInitOk){
      int i;
      iInitOk=1;
      for(i=0;i<32768;i++){get_3xL[i]=getLen2x_testI(i,2);}
   }
   return get_3xL[v];

}
//getLen_xxlog_test
int getLen_xxlog_test(int v){
   if(v==0)return 1;
   if(v<16)return 5;
   v-=16;
   int l=0;
   while(v>0){l++;v>>=1;}
   


   return 5+2*l;
}
//vlc(len)+(sc)+(len<7)+coefs

typedef struct
{
    int i_bits;
    int i_size;
} vlc_t;

/* XXX: don't forget to change it if you change vlc_t */
#define MKVLC( a, b ) { a, b }

static const vlc_t x264_total_zeros[15][16] =
{
    { /* i_total 1 */
        MKVLC( 0x1, 1 ), /* str=1 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x2, 3 ), /* str=010 */
        MKVLC( 0x3, 4 ), /* str=0011 */
        MKVLC( 0x2, 4 ), /* str=0010 */
        MKVLC( 0x3, 5 ), /* str=00011 */
        MKVLC( 0x2, 5 ), /* str=00010 */
        MKVLC( 0x3, 6 ), /* str=000011 */
        MKVLC( 0x2, 6 ), /* str=000010 */
        MKVLC( 0x3, 7 ), /* str=0000011 */
        MKVLC( 0x2, 7 ), /* str=0000010 */
        MKVLC( 0x3, 8 ), /* str=00000011 */
        MKVLC( 0x2, 8 ), /* str=00000010 */
        MKVLC( 0x3, 9 ), /* str=000000011 */
        MKVLC( 0x2, 9 ), /* str=000000010 */
        MKVLC( 0x1, 9 ), /* str=000000001 */
    },
    { /* i_total 2 */
        MKVLC( 0x7, 3 ), /* str=111 */
        MKVLC( 0x6, 3 ), /* str=110 */
        MKVLC( 0x5, 3 ), /* str=101 */
        MKVLC( 0x4, 3 ), /* str=100 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x5, 4 ), /* str=0101 */
        MKVLC( 0x4, 4 ), /* str=0100 */
        MKVLC( 0x3, 4 ), /* str=0011 */
        MKVLC( 0x2, 4 ), /* str=0010 */
        MKVLC( 0x3, 5 ), /* str=00011 */
        MKVLC( 0x2, 5 ), /* str=00010 */
        MKVLC( 0x3, 6 ), /* str=000011 */
        MKVLC( 0x2, 6 ), /* str=000010 */
        MKVLC( 0x1, 6 ), /* str=000001 */
        MKVLC( 0x0, 6 ), /* str=000000 */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 3 */
        MKVLC( 0x5, 4 ), /* str=0101 */
        MKVLC( 0x7, 3 ), /* str=111 */
        MKVLC( 0x6, 3 ), /* str=110 */
        MKVLC( 0x5, 3 ), /* str=101 */
        MKVLC( 0x4, 4 ), /* str=0100 */
        MKVLC( 0x3, 4 ), /* str=0011 */
        MKVLC( 0x4, 3 ), /* str=100 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x2, 4 ), /* str=0010 */
        MKVLC( 0x3, 5 ), /* str=00011 */
        MKVLC( 0x2, 5 ), /* str=00010 */
        MKVLC( 0x1, 6 ), /* str=000001 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x0, 6 ), /* str=000000 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 4 */
        MKVLC( 0x3, 5 ), /* str=00011 */
        MKVLC( 0x7, 3 ), /* str=111 */
        MKVLC( 0x5, 4 ), /* str=0101 */
        MKVLC( 0x4, 4 ), /* str=0100 */
        MKVLC( 0x6, 3 ), /* str=110 */
        MKVLC( 0x5, 3 ), /* str=101 */
        MKVLC( 0x4, 3 ), /* str=100 */
        MKVLC( 0x3, 4 ), /* str=0011 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x2, 4 ), /* str=0010 */
        MKVLC( 0x2, 5 ), /* str=00010 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x0, 5 ), /* str=00000 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 5 */
        MKVLC( 0x5, 4 ), /* str=0101 */
        MKVLC( 0x4, 4 ), /* str=0100 */
        MKVLC( 0x3, 4 ), /* str=0011 */
        MKVLC( 0x7, 3 ), /* str=111 */
        MKVLC( 0x6, 3 ), /* str=110 */
        MKVLC( 0x5, 3 ), /* str=101 */
        MKVLC( 0x4, 3 ), /* str=100 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x2, 4 ), /* str=0010 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x0, 5 ), /* str=00000 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 6 */
        MKVLC( 0x1, 6 ), /* str=000001 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x7, 3 ), /* str=111 */
        MKVLC( 0x6, 3 ), /* str=110 */
        MKVLC( 0x5, 3 ), /* str=101 */
        MKVLC( 0x4, 3 ), /* str=100 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x2, 3 ), /* str=010 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x0, 6 ), /* str=000000 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 7 */
        MKVLC( 0x1, 6 ), /* str=000001 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x5, 3 ), /* str=101 */
        MKVLC( 0x4, 3 ), /* str=100 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x3, 2 ), /* str=11 */
        MKVLC( 0x2, 3 ), /* str=010 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x0, 6 ), /* str=000000 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 8 */
        MKVLC( 0x1, 6 ), /* str=000001 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x3, 2 ), /* str=11 */
        MKVLC( 0x2, 2 ), /* str=10 */
        MKVLC( 0x2, 3 ), /* str=010 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x0, 6 ), /* str=000000 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 9 */
        MKVLC( 0x1, 6 ), /* str=000001 */
        MKVLC( 0x0, 6 ), /* str=000000 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x3, 2 ), /* str=11 */
        MKVLC( 0x2, 2 ), /* str=10 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x1, 2 ), /* str=01 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 10 */
        MKVLC( 0x1, 5 ), /* str=00001 */
        MKVLC( 0x0, 5 ), /* str=00000 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x3, 2 ), /* str=11 */
        MKVLC( 0x2, 2 ), /* str=10 */
        MKVLC( 0x1, 2 ), /* str=01 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 11 */
        MKVLC( 0x0, 4 ), /* str=0000 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x2, 3 ), /* str=010 */
        MKVLC( 0x1, 1 ), /* str=1 */
        MKVLC( 0x3, 3 ), /* str=011 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 12 */
        MKVLC( 0x0, 4 ), /* str=0000 */
        MKVLC( 0x1, 4 ), /* str=0001 */
        MKVLC( 0x1, 2 ), /* str=01 */
        MKVLC( 0x1, 1 ), /* str=1 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 13 */
        MKVLC( 0x0, 3 ), /* str=000 */
        MKVLC( 0x1, 3 ), /* str=001 */
        MKVLC( 0x1, 1 ), /* str=1 */
        MKVLC( 0x1, 2 ), /* str=01 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 14 */
        MKVLC( 0x0, 2 ), /* str=00 */
        MKVLC( 0x1, 2 ), /* str=01 */
        MKVLC( 0x1, 1 ), /* str=1 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
    { /* i_total 15 */
        MKVLC( 0x0, 1 ), /* str=0 */
        MKVLC( 0x1, 1 ), /* str=1 */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
        MKVLC( 0x0, 0 ), /* str= */
    },
};

/*
int lenBit2(int iCurDV,int iCnt, int l, unsigned char *vlc){
   int z=iCnt-l;
   int iBitsNew=vlc[l];
   //if(l==2 || l==1)iBitsNew--;
   if(l==3)iBitsNew--;   else if(l==7 || l==15)iBitsNew-=2;
   if(iCnt!=15){
      iBitsNew+=x264_total_zeros[l-1][z].i_size;
   }
   int coLeft=l;
   int i;
   for(i=0;i<iCnt;i++){
      if(!z){
         iBitsNew+=coLeft;
         break;
      }
      int co=(iCurDV&(1<<i));
      if(z<coLeft){
         iBitsNew+=2;
         if(co){coLeft--;}else if(z)z--;
      }
      else {
         if(co){coLeft--;iBitsNew+=3;}else {z--;iBitsNew++;}
      }
   }
   return iBitsNew;

}
*/
//#---

int l264(unsigned char *bitsLenVLC,int iCnt, int l, int iCurDV){
   int iBitsNew=0;
   int z=iCnt-l;
   iBitsNew=bitsLenVLC[l];if(l==3)iBitsNew--;   else if(l==7 || l==15)iBitsNew-=2;
   
   if(iCnt!=15){
      int s=x264_total_zeros[l-1][z].i_size;
      if(s>2 && iCnt>2)s--;
      iBitsNew+=s;
   }
   int coLeft=l;
   int i;
   for(i=0;i<iCnt;i++){
      if(!z){
         iBitsNew+=coLeft;
         break;
      }
      int co=(iCurDV&(1<<i));
      if(z<coLeft){
         iBitsNew+=2;
         if(co){coLeft--;}else if(z)z--;
      }
      else {
         if(co){coLeft--;iBitsNew+=3;}else {z--;iBitsNew++;}
      }
   }
   return iBitsNew;

}
int lenTr(unsigned char *bitsLenVLC,int iCnt, int l, int iCurDV){
   int iBitsNew=0;
 
   iBitsNew=getLen2x_test(l);
   
   
   int coLeft=l;
   int i;
   for(i=0;i<iCnt;i++){
      if(coLeft==1){
         iBitsNew+=bitsLenVLC[iCnt-i-1]+1;//zeros+sign
         break;
      }
      int co=(iCurDV&(1<<i));
      if(l<5){
         iBitsNew+=2;
         if(co){coLeft--;}
      }
      else
      {
         if(co){coLeft--;iBitsNew+=3;}else {iBitsNew++;}
      }
   }
   return iBitsNew;

}

//--
//#define T_BITS_SECOND(_ac)  (_ac[l]+1+iCnt+l*2-(l>=6?(iCnt-l):0))
//#define T_BITS_SECOND(_ac)  (lenTr(_ac,iCnt,l,iCurDV)+1)
//#define T_BITS_SECOND(_ac)  (l264(_ac,iCnt,l,iCurDV)+1)
//#define T_BITS_SECOND(_ac)  (_ac[l>>2]+4+iCnt+l*2-(l>=6?(l-5):0)-(iCurDV?0:1))

//#define T_BITS_SECOND(_ac)  (_ac[l]+1+iCnt+l*2)
//#define T_BITS_SECOND(_ac)  (lenBit2(iCurDV,iCnt,l,_ac)+1)
//#define T_BITS_SECOND(_ac)  ((min((getLen_xxlog_test(l)+1+iCnt+l*2+(l>=6?1000:1)),(getLen_xxlog_test(l)+1+2*iCnt+(l<6)))))
//#define T_BITS_SECOND(_ac)  ((l<6?(_ac[l]+1+iCnt+l*2):(_ac[l]+1+2*iCnt)))
//---------
#define T_BITS_SECOND(_ac)  ((l<5?(getLen2x_test(l)+1+iCnt+l*2):(getLen2x_test(l)+1+2*iCnt)))
//#define T_BITS_SECOND(_ac)  ((l<5?(_ac[l]+1+iCnt+l*2):(_ac[l]+1+2*iCnt)))

//#define T_BITS_SECOND(_ac)  ((iCnt>l*2?(_ac[l]+1+iCnt+l*2):(_ac[l]+1+2*iCnt)))
//#define T_BITS_SECOND(_ac)  (min((l<=7?100:0),(_ac[l]+1+2*iCnt+((l<=7)?100:0))))
static unsigned char *g_bitsLenAC;
int remThis(int iCurDV,int sc){
   //len or x,
   return 0;
   /*
   int i;
   int l=0,iCnt=0;
   for(i=0;i<15;i++){
      if(iCurDV&(1<<(i))){iCnt=(i+1);l++;}
   }
   if(l==1)return 0;
   int iBitsOldN=T_BITS_SECOND(g_bitsLenAC);
   int iScBits=sc<16;
   if(sc>1020){sc>>=6;iScBits+=10;}
   iScBits+=g_bitsLenAC[sc];
   iScBits+=l;

   return iScBits>=iBitsOldN;*/
}
#if 0
int calcBits_4xTest(int iPic,int l, int iCnt, int iMax2,int iCurDV, unsigned char *bitsLenAC, unsigned char *bitsLenVLC){

   int iBitsOld=bitsLenVLC[l]+iCnt+l*2;
   
//return 0;//381 95
   int iBitsNew=0;
   if(0){//385 347, pot 396 958KB
//
      int z=iCnt-l;
      iBitsNew=bitsLenVLC[l];if(l==3)iBitsNew--;   else if(l==7 || l==15)iBitsNew-=2;
      
      if(iCnt!=15){
         iBitsNew+=x264_total_zeros[l-1][z].i_size;
      }
      int coLeft=l;
      int i;
      for(i=0;i<iCnt;i++){
         if(!z){
            iBitsNew+=coLeft;
            break;
         }
         int co=(iCurDV&(1<<i));
         if(z<coLeft){
            iBitsNew+=2;
            if(co){coLeft--;}else if(z)z--;
         }
         else {
            if(co){coLeft--;iBitsNew+=3;}else {z--;iBitsNew++;}
         }
      }
      return iBitsOld-iBitsNew;
   }
   if(iMaxZZ==0){
      g_bitsLenAC=bitsLenAC;
      picDataSLS(2);
   }
   //return 0;
   /*
   if(iPic){//simple -5%
      
      
      //iBitsNew=((l<6?(bitsLenVLC[l]+iCnt+l*2):(bitsLenVLC[l]+2*iCnt)));
      iBitsNew=(min((bitsLenVLC[l]+iCnt+l*2+(l>=6?100:1)),(bitsLenVLC[l]+2*iCnt+(l<6)))); 
      if(l==3)iBitsNew--;   else if(l==7 || l==15)iBitsNew-=2;

      return iBitsOld-iBitsNew;

   }
   */
//   return 1;
   if(iMaxZZ<10000)return 0;
 //  static int iG=0;
  // iG=!iG;
   //if(iCurDV==0)return l==1?(iG):(-1);
   
   int iBitsOldN=0;
   iBitsOldN=T_BITS_SECOND(bitsLenAC);
   //return iBitsOld-iBitsOldN+1;
   int sc=iMax2==0?findScore(iCurDV,&stats[0],MAX_SC,1,l):0;//iMaxZZ>20000);
   /*
   if(l==15){
      iBitsOldN=bitsLenVLC[l]+iCnt*2-2+1;
   }
   else{
      iBitsOldN=(min((bitsLenVLC[l]+iCnt+l*2+1+(l>=7?100:0)),(bitsLenVLC[l]+1+2*iCnt+(l<7))));if(l==7 || l==15)iBitsOldN-=2;
   }
   return iBitsOld-iBitsOldN+1;
   */
   //bitsLenVLC[sc>>1]+1
//#define gblx getLen2x_test
//#define gbl bitsLenAC
   
#if 1
   if(sc){
      static int iSCP1=10;
      static int iSCP2=10;
      //15 top,15 left
      //vlc 
      //if(vlc>15)readsigns_max1(vlc_codetab=vlc)
      //else  if(bit)readsigns_max1s(vlc_codetab=vlc)
      //else readoldcoefs(coef_cnt=vlc)
      if(sc<16)iBitsNew++;/// if(sc>15) nevar buut coefu skaits
      //if(sc>15 && sc<32)iBitsNew++;/// if(sc>15) nevar buut coefu skaits
        iBitsNew+=getLen2x_test(sc);
      
 

      
//      if(sc>1020){sc>>=6;iBitsNew+=10;}iBitsNew+=bitsLenAC[sc];
      
      //1
      //01000 
      //01001
      //...
      //01111
      
      //iBitsNew+=bitsLenAC[sc>>4]+4;
      //0=1,1-2=3,3-6=5,7-14=7,15-30=9,31-62=11
      //31>>3 5+xxxx

      iBitsNew+=l;//signs
#define BITS_OLD_C  iBitsOldN

      //--
      //if(l>1 && iBitsNew>BITS_OLD_C){iBitsNew=BITS_OLD_C;sc=0;}//if(l==15)iBitsNew-=15;else if(l==14)iBitsNew-=10;else if(l==13)iBitsNew-=5;}
      //else if(iBitsNew>=iBitsOld+1){iBitsNew=iBitsOld+1;sc=0;}
     // debugss("sc",iBitsNew,iBitsOld);
   }
   else{ 
      iBitsNew=BITS_OLD_C;
      if(l==1)iBitsNew-=2;//nav 1
    //  iBitsNew--;
      //else if(l==15)iBitsNew-=15;else if(l==14)iBitsNew-=10;else if(l==13)iBitsNew-=5;
      //else iBitsNew-=iG;
      
   }
#else
   if(sc>15){
      //vlc 
      //if(vlc>15)readsigns_max1(vlc_codetab=vlc)
      //else  if(bit)readsigns_max1s(vlc_codetab=vlc)
      //else readoldcoefs(coef_cnt=vlc)

      while(sc>2047){sc>>=1;iBitsNew+=2;}
      iBitsNew+=bitsLenAC[sc];
      iBitsNew-=l;
      
      static int iNeedClear=0;
      if(iBitsNew>=bitsLenAC[l]+iCnt){
         //  if((iBitsNew>=iBitsOld && sc<31) || (iBitsNew>=iBitsOld+2 && sc<60)  || (iBitsNew>=iBitsOld+7 && sc<250)){iNeedClear=1;stats[iCurDV]=0;debugss("sc",iCurDV,iBitsNew-iBitsOld);}
         iBitsNew=bitsLenAC[l]+iCnt;sc=0;if(l==15)iBitsNew-=20;
      }

      static int iClear=0;   
      iClear++;
      if( iNeedClear){
         //iNeedClear=0;int scx=findScore(iCurDV,&stats[0],MAX_SC,0);
      }
      //else if(iBitsNew>=iBitsOld+1){iBitsNew=iBitsOld+1;sc=0;}

   }
   else{ 
      iBitsNew=bitsLenAC[l]+iCnt;
      if(l==15)iBitsNew-=20;      
   }
#endif
/*
   static int gb1=0,gb2=0;
   if(iMax2==0){
   gb1+=(iBitsOld-iBitsNew);
   debugsi("g1",gb1>>3);
   }
   else{ 
   gb2+=(iBitsOld-iBitsNew);
   debugsi("g2",gb2>>3);
   }
*/
   return iBitsOld-iBitsNew;
}


void addCoef(int iCurDV,int dbg, int iPic){
   //if(iPic)

   if(!iCurDV)return;
   return;
   if(iMaxZZ==0){
      picDataSLS(2);
   }
   if(!iCanUpdate){
      if(stats[iCurDV]==0){
         stats[iCurDV]++;
         findScore(iCurDV,&stats[0],MAX_SC,0,0);
      }
      return;
   }
#if defined(__SYMBIAN32__) || defined(_WIN32_WCE)
      asdsdaeyte
#endif
   int i;
   if(dbg){

   }

   iMaxZZ++;
  // return;
 //  if(iCurDV>300)return ;
   //if(iCurDV==32767)iCurDV=0;
   stats[iCurDV]++;
   static int iMaxS=2;
   if(iMaxS<stats[iCurDV])iMaxS=stats[iCurDV];
   if((stats[iCurDV]==1 && iMaxZZ>1000) || ((iMaxZZ&511)==0)){
      int sc=findScore(iCurDV,&stats[0],MAX_SC,0,0);
   }

   if((iMaxZZ>30000 && stats[iCurDV]==1) || (stats[iCurDV]>1 && (iMaxZZ&((1<<8)-1)) && iMaxS>2 && (iMaxS<stats[iCurDV]*128+1 || iMaxZZ<stats[iCurDV]*1024))){

      
      if(iCurDV==1 || iCurDV==2){
         //debugss("1-2-",iMaxZZ,stats[iCurDV]);
      }
      else if(iMaxZZ>30000){
         /*
         char bb[32];
         bb[16]=bb[15]=0;
         for(i=1;i<16;i++)bb[i-1]=iCurDV&(1<<(i-1))?'1':'0';
         debugss(&bb[0],iCurDV,stats[iCurDV]);
         */
         /*
         if(iDV0||(iCurDV>128 )){
            int findScore(int val, int  *tab, int iSize,int f);
            int sc=findScore(iCurDV,&stats[0],16383,0);
           // if(sc<1024)debugss("sc=",sc,iCurDV);
         }
         */


         
      }
   }
   /*
   if((iMaxZZ&((1<<8)-1))==0){
//               int statC[1024];
//             for(i=0;i<1024;i++){stats[i]=statC[i];}
      for(i=0;i<3025;i++){
         if(stats[i]*800>iMaxZZ)debugss("i=",i,stats[i]);
      }
   }
   */

}
#endif

#else
void picDataSLS(int iParam){}

#endif