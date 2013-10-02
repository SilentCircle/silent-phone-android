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

static inline void predDCX(unsigned char *p, int stride, unsigned char *dc){
   int i,j;
      //YUVY
   unsigned int col0=dc[0]|(dc[0]<<24)|(dc[2]<<16)|(dc[1]<<8);
   //UVYU
   unsigned int col1=dc[1]|(dc[1]<<24)|(dc[0]<<16)|(dc[2]<<8);
   //VYUV
   unsigned int col2=dc[2]|(dc[2]<<24)|(dc[1]<<16)|(dc[0]<<8);
   stride-=48;
   for(j=0;j<16;j++){
      for(i=0;i<48;i+=12){
         
         *(unsigned int*)(p)=col0;p+=4;
         *(unsigned int*)(p)=col1;p+=4;
         *(unsigned int*)(p)=col2;p+=4;
         /*
         p[0]=dc[0];
         p[1]=dc[1];
         p[3]=dc[2];p+=3;
         */
      }
      p+=stride;
   }
   
}
static int pred_h_l(unsigned char *p, int stride, int F){
  // return 0;
   if((F&1)==0)return 0;
   int i,j;
   stride-=48;
   for(j=0;j<16;j++){
      //YUVY
      p-=3;
      unsigned int col0=p[0]|(p[0]<<24)|(p[2]<<16)|(p[1]<<8);
      //UVYU
      unsigned int col1=p[1]|(p[1]<<24)|(p[0]<<16)|(p[2]<<8);
      //VYUV
      unsigned int col2=p[2]|(p[2]<<24)|(p[1]<<16)|(p[0]<<8);
      p+=3;
      for(i=0;i<48;i+=12){
         *(unsigned int*)(p)=col0;p+=4;
         *(unsigned int*)(p)=col1;p+=4;
         *(unsigned int*)(p)=col2;p+=4;
      }
      p+=stride;
   }
   return 1;
}

static int pred_t_l(unsigned char *p, int stride, int F){
   int i,j;
   //return 0;
   if((F&2)==0)return 0;
   unsigned char *pTopLine=p-stride;
   for(j=0;j<16;j++){
      memcpy(p,pTopLine,48);
      p+=stride;
   }
   unsigned char bl2[48*16];
   return 1;
}

static int pred_th_l(unsigned char *p, int stride, int F){
   int i,j;
   //return 0;
   if(F!=3)return 0;
   pred_h_l(p,stride,F);
   unsigned char *pTopLine=p-stride;
   for(j=0;j<16;j++){
      for(i=0;i<48;i++){
         p[i]=(p[i]+pTopLine[i]+1)>>1;
      }
      p+=stride;
   }
   return 1;
}
static int pred_th_x(unsigned char *p, int stride, int F){
   int i,j;
   //return 0;
   if(F!=3)return 0;
   unsigned char *pTopLine=p-stride;
   unsigned char *tl=p-3-stride;
   for(j=0;j<16;j++){
      for(i=0;i<48;i++){
         int a=p[i-3]+pTopLine[i]-tl[i];
         if(a<=0)a=0;else if(a>=255)a=255;
         p[i]=a;
      }
      tl+=stride;
      pTopLine+=stride;
      p+=stride;
   }
   return 1;
}

static int pred_top(unsigned char *p, int stride, int F){
   //return 0;
   if((F&2)==0)return 0;
   unsigned char *pIn=p;
   unsigned char dc[4];
   int dcY=0;
   int dcU=0;
   int dcV=0;
   int i;
   p-=stride;
   for(i=0;i<16;i++){
      dcY+=p[0];
      dcU+=p[1];
      dcV+=p[2];
      p+=3;
   }
   dc[0]=(dcY+8)>>4;
   dc[1]=(dcU+8)>>4;
   dc[2]=(dcV+8)>>4;
   predDCX(pIn,stride,&dc[0]);
   return 1;

}

static int pred_hor(unsigned char *p, int stride, int F){
   //return 0;
   if((F&1)==0)return 0;
   unsigned char dc[4];
   unsigned char *pIn=p;
   int dcY=0;
   int dcU=0;
   int dcV=0;
   int i;
   p-=3;
   for(i=0;i<16;i++){
      dcY+=p[0];
      dcU+=p[1];
      dcV+=p[2];
      p+=stride;
   }
   dc[0]=(dcY+8)>>4;
   dc[1]=(dcU+8)>>4;
   dc[2]=(dcV+8)>>4;
   predDCX(pIn,stride,&dc[0]);
   return 1;

}
static int pred_dc(unsigned char *p, int stride, int F){
   //return 0;
   unsigned char dc[4];
   if((F&3)==3){
      unsigned char *pIn=p;
      int dcY=0;
      int dcU=0;
      int dcV=0;
      int i;
      p-=3;
      for(i=0;i<16;i++){
         dcY+=p[0];
         dcU+=p[1];
         dcV+=p[2];
         p+=stride;
      }
      p-=stride*17-3;
      for(i=0;i<16;i++){
         dcY+=p[0];
         dcU+=p[1];
         dcV+=p[2];
         p+=3;
      }   
      dc[0]=(dcY+16)>>5;
      dc[1]=(dcU+16)>>5;
      dc[2]=(dcV+16)>>5;
      p=pIn;
   }
   else{
      dc[1]=dc[2]=dc[0]=128;
   }
   predDCX(p,stride,&dc[0]);
   return 1;
}
typedef int (fPRED16 )(unsigned char *src, int stride, int F);
static fPRED16 *fncPred16[7];
void loadPred16(){
   fncPred16[0]=&pred_dc;
   fncPred16[1]=&pred_th_x;
   fncPred16[2]=&pred_hor;
   fncPred16[3]=&pred_top;
   fncPred16[4]=&pred_h_l;
   fncPred16[5]=&pred_t_l;
   fncPred16[6]=&pred_th_l;
}