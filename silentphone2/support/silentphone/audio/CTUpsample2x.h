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
#ifndef _C_T_UPSAMPLE_2X_H
#define _C_T_UPSAMPLE_2X_H

class CTUpSample2x{
   int iPrev,iPrevPrev,iNextNext,iNext,iCur;
   int iHasNextSample;
   short nextSample;
public:
   CTUpSample2x(){
      reset();
   }
   void reset(){
      iPrev=0;iPrevPrev=0;
      iNextNext=iNext=0;
      iCur=0;
      iHasNextSample=0;
      nextSample=0;
   }
   
   
   void doJob(short *in, int iSamples, short *out, int iOutSamples=-1, int *used=NULL)
   {
      //int i;
      int res = 0;
      if(iOutSamples==-1)iOutSamples=iSamples*2;
      
      if(iHasNextSample){
         iHasNextSample=0;
         *out=nextSample;
         out++;
         iOutSamples--;
      }
      int iRememberLast=0;
      if(iOutSamples&1){iRememberLast=1;iSamples++;}
      
      int iUsed=0;
      
      while(iSamples)
      {
         iUsed++;
         iNextNext=*in;in++;
         //  if(iPix>2)iPrevPrev=in[i];//-iStep*2];
         //  iPrevPrev=in[i];
         
         res=((iCur*7 + iPrev*3)+4-iPrevPrev-iNext)>>3;
         
         //res=((iCur*14 + iPrev*4)+8-iPrevPrev-iNext)>>4;
         if(res<-32766)res=-32766;else if(res>32766)res=32766;
         
         *out=(short)res;out++;
         
         //--??-- res=((iCur*13 + iNext*5)+8-iPrev-iNextNext)>>4;
         res=((iCur*7 + iNext*3)+4-iPrev-iNextNext)>>3;
         if(res<-32766)res=-32766;else if(res>32766)res=32766;
         
         *out=(short)res;out++;
         
         iPrevPrev=iPrev;
         iPrev=iCur;
         iCur=iNext;
         iNext=iNextNext;
         
         
         iSamples--;
         iOutSamples-=2;
         if(iOutSamples<=0)break;
         
      }
      if(iRememberLast){
         iHasNextSample=1;
         nextSample=res;
         
      }
      if(used)*used=iUsed;
   }
   
   
};

#if 0
#include <stdio.h>
#include <math.h>


int main(){
   short t[100];//={1,2,5,7,2,-5,-10,-15,0,18,20,30,50,80,100,0,-20,-40};
   short o[200];
   CTUpSample2x c;
   
   for(int i=0;i<100;i++){t[i]=sin((double)i/10)*1000;}
   
   c.doJob(&t[0],100,&o[0]);
   for(int i=0;i<100;i++){
 //     printf("[%d(%d %d)]",t[i],o[i*2],o[i*2+1]);
      printf(" %d %d",o[i*2],o[i*2+1]);
   }
   puts("");
   return 0;
}

#endif

#endif



