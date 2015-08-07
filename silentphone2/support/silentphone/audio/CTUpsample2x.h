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
      int res;
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



