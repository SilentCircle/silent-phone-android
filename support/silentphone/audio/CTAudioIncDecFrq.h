#ifndef _C_T_A_I_D_FREQ_H
#define _C_T_A_I_D_FREQ_H
#include <string.h>

#include "CTUpsample2x.h"

class CTDecFreq{
protected:
   double dStart,dVol,dVolPart,dStep,dNextPos,dPcmVol;
   
   int iSamplesOut;;
   double dFreqIn;
   double dFreqOut;
   int iCalc;
   
public:
   enum{eMaxPcm=32700,eMinPcm=-32700};
   
   CTDecFreq(){
      dStart=dPcmVol=dVol=dVolPart=0.0f;
      
      setFreq(44100,8000);
      
   }
   void setFreq(int iIn, int iOut)
   {
      iCalc=iIn!=iOut;
      if(iCalc==0)return;
      dFreqIn=(double)iIn;
      dFreqOut=(double)iOut;
      if(dFreqIn>dFreqOut)
         dStep=dFreqIn/dFreqOut;
      else
         dStep=dFreqOut/dFreqIn;
      dNextPos=dStep;
   }
   
   
   //sho algo var  lietot arii prieksh liniju krasu mainju, bet katram tonim atsevishki
   
   int incx(short *pcmIn, short *pcmOut, int iSamplesIn, int iSOut=0)
   {
      int i;
      if(iCalc==0)
      {
         memcpy(pcmOut,pcmIn,iSamplesIn*sizeof(short));
         return iSamplesIn;
      }
      if(iSOut){dStep=((double)iSamplesIn+0.2)/(double)iSOut;dNextPos=0;dStart=0;}
      iSamplesOut=0;
      double j;
      // double dPcmNext;
      //int iStep=(int)dStep;
      //iSamplesIn--;
      for(i=0;i<iSamplesIn;i++)
      {
         dPcmVol=dVolPart+dNextPos;//(double)*pcmIn;
         dNextPos=(double)*pcmIn;
         //dNextPos=(dPcmVol+dNextPos)/2;
         pcmIn++;
         //dVolPart=dNextPos*(dStep-(double)(iStep));
         //dNextPos-=dVolPart;
         double cStep=(dNextPos-dPcmVol)/(double)(dStep);//*j/(double)(iStep);
         
         //*pcmOut=(short)dPcmVol;
         double dEnd=dStep+dStart;
         double d2Step=1.0f;// /dStep;
         double ccStep=cStep;
         for(j=dStart;j<dEnd;j+=d2Step)
         {
            //  printf("f-%f,step-%f,dc-%f,dn=%f\n",j,cStep,dPcmVol,dNextPos);
            dVol=dPcmVol+//ccStep;//
            (dNextPos-dPcmVol)*j/(dStep);
            
            ccStep+=cStep;
            if(dVol>eMaxPcm)
               dVol=eMaxPcm;
            else if(dVol<eMinPcm)
               dVol=eMinPcm;
            
            //if(j+1.0f>=dStep)
            {
               //dVolPart=dNextPos-dVol;
               //dVol+=dVolPart;dVolPart=0;
               
            }
            *pcmOut=(short)dVol;
            
            pcmOut++;
            iSamplesOut++;
         }
         dStart=j-dStep;
         
         
         
         
         
      }
      return iSamplesOut; 
   }
   int dec(short *pcmIn, short *pcmOut, int iSamplesIn, int iSOut=0)
   {
      int i;
      
      //if(iSOut){dStep=((double)iSamplesIn+0.2)/(double)iSOut;dNextPos=0;}
      
      iSamplesOut=0;
      if(iCalc==0)
      {
         memcpy(pcmOut,pcmIn,iSamplesIn*sizeof(short));
         return iSamplesIn;
      }
      
      
      for(i=0;i<iSamplesIn;i++)
      {
         
         dPcmVol=(double)*pcmIn;;
         if(dNextPos>(double)(i+1))
         {
            dVol+=dPcmVol;
            pcmIn++;
         }
         else 
         {
            dVolPart=dPcmVol*(dNextPos-(double)i);
            dVol+=dVolPart;
            
            dVol/=dStep;
            if(dVol>eMaxPcm)
               dVol=eMaxPcm;
            else if(dVol<eMinPcm)
               dVol=eMinPcm;
            
            *pcmOut=(short)dVol;
            
            dVol=dPcmVol-dVolPart;
            pcmOut++;
            pcmIn++;
            iSamplesOut++;
            //if(iSOut)dNextPos=(double)(iSamplesOut)*dStep;
            // else 
            dNextPos+=dStep;
         }
         //if(i==iSamplesIn)break;
      }
      /*
       if(iSamplesOut<iSOut){
       if(dPcmVol>eMaxPcm)
       dPcmVol=eMaxPcm;
       else if(dPcmVol<eMinPcm)
       dPcmVol=eMinPcm;
       
       //  *pcmOut=(short)dPcmVol;
       pcmOut[iSamplesOut]=(short)dPcmVol;
       iSamplesOut++;
       }
       */
      //d=dNextPos-(double)i;
      dNextPos-=(double)iSamplesIn;//(i-1);
      
      return iSamplesOut;
   }
};
//#include "../../libs/libresample/include/libresample.h"

/*
 void *resample_open(int      highQuality,
 double   minFactor,
 double   maxFactor);
 
 void *resample_dup(const void *handle);
 
 int resample_get_filter_width(const void *handle);
 
 int resample_process(void   *handle,
 double  factor,
 float  *inBuffer,
 int     inBufferLen,
 int     lastFlag,
 int    *inBufferUsed,
 float  *outBuffer,
 int     outBufferLen);
 
 void resample_close(void *handle);
 */


class CTIncDecFreq: public CTDecFreq{
   CTDecFreq decr;
   
   void *ptr;
   float inB[8000];
   float outB[8000];
   short fifo[4000];
   short soutB[4000];
   int bytesInFifo;
   int iNextIf0UseLast;
   int iInBufFromPRev;
   int iFreqInR,iFreqOutR;
public:
   CTIncDecFreq():CTDecFreq(){iFreqInR=0;iFreqOutR=0;iInBufFromPRev=0;iPrev=0;ptr=NULL;bytesInFifo=0;iNextIf0UseLast=0;}
   ~CTIncDecFreq(){/*if(ptr)resample_close(ptr);*/}
   
   inline int doJob2(short *pcmIn, short *pcmOut, int iSamplesIn, int iSOout)
   {
#if 0
      if(0&&iSamplesIn<iSOout){
         dStep=((double)(iSOout-bytesInFifo/2)+0.2)/(double)iSamplesIn;
         int ret=dec(pcmIn,&soutB[0],iSamplesIn);
         int i;
         int bf=bytesInFifo;
         for(i=0;i<bytesInFifo;i++){
            pcmOut[i]=fifo[i];
         }
         for(i=bytesInFifo;i<iSOout;i++){
            pcmOut[i]=soutB[i-bf];
         }
         bytesInFifo=0;
         for(;i<ret;i++){
            fifo[bytesInFifo]=soutB[i-bf];
         }
        // ret=iSOout;
         return iSOout;
      }
      if(1){
         if(!ptr){
            ptr=resample_open(0,
                              .75,
                              1.25);
         }
         
         double factor=(double)(iSOout-bytesInFifo/2)/(double)(iSamplesIn+iInBufFromPRev);
         int ret=0;
         int used=0;

         if(iSOout==iSamplesIn){
            if(iNextIf0UseLast)
               
               ret=resample_process(ptr,
                                    factor,
                                    &inB[0],
                                    iSamplesIn+iInBufFromPRev,
                                    1,//lastflag
                                    &used,
                                    &outB[0],
                                    8000);
            else memcpy( &outB[0], &inB[0],iSamplesIn*sizeof(float));
            iNextIf0UseLast=0;
            ret=iSOout;
         }
         else {
            iNextIf0UseLast=1;
            ret=resample_process(ptr,
                                 factor,
                                 &inB[0],
                                 iSamplesIn+iInBufFromPRev,
                                 0,//lastflag
                                 &used,
                                 &outB[0],
                                 8000);
         }
         /*
          //bug
          
         if(used<iSamplesIn+iInBufFromPRev){
            int m=(iSamplesIn+iInBufFromPRev)-used;
            for(int i=0;i<m;i++){
               inB[i]=inB[i+used];
            }
            iInBufFromPRev=m;
         }
         */
         int i;
         int bf=bytesInFifo;
         for(i=0;i<bytesInFifo;i++){
            pcmOut[i]=fifo[i];
         }
         for(i=bytesInFifo;i<iSOout;i++){
            float r=outB[i-bf]*32650.;
            if(r<-32700)r=-32700;else if(r>32700)r=32700;
            pcmOut[i]=(short)r;
         }
         bytesInFifo=0;
         for(;i<ret;i++){
            
            float r=outB[i-bf]*32650.;
            if(r<-32700)r=-32700;else if(r>32700)r=32700;
            fifo[bytesInFifo]=(short)r;
            bytesInFifo++;
         }
         
         return iSOout;
      }
      
      if(0){
         
         if(iSamplesIn<iSOout){
            int dif=iSOout-iSamplesIn;
            int iInsert=iSamplesIn/dif;
            int c=iInsert/2;
            for(int i=0;i<iSamplesIn;i++){
               *pcmOut=*pcmIn;
               pcmOut++;
               c--;
               if(c==0 && dif>0){
                  *pcmOut=*pcmIn;
                  pcmOut++;
                  c=iInsert;
                  dif--;
               }
               pcmIn++;
            }
         }
         if(iSamplesIn>iSOout){
            int dif=iSamplesIn-iSOout;
            int iInsert=iSOout/dif;
            int c=iInsert/2;
            for(int i=0;i<iSOout;i++){
               *pcmOut=*pcmIn;
               pcmOut++;
               c--;
               if(c==0 && dif>0){
                  *pcmOut=*pcmIn;
                  pcmIn++;
                  c=iInsert;
                  dif--;
               }
               pcmIn++;
            }
         }
         return iSOout;
      }
#endif 
      if(dFreqIn<dFreqOut)return inc(pcmIn,pcmOut,iSamplesIn,iSOout);
      return dec(pcmIn,pcmOut,iSamplesIn,iSOout);
   }
   
   
   inline int doJob(short *pcmIn, short *pcmOut, int iSamplesIn)
   {
      if(dFreqIn<dFreqOut)return inc(pcmIn,pcmOut,iSamplesIn);
      return dec(pcmIn,pcmOut,iSamplesIn);
   }
   void setFreq(int iIn, int iOut)
   {
      iFreqInR=iIn;
      iFreqOutR=iOut;
      CTDecFreq::setFreq(iIn,iOut);
      int iStep=1+(int)dStep;
      //round
      decr.setFreq(iStep*iIn,(int)dFreqOut);
      
   }
   //decr.setFreq(iStep*(int)dFreqIn,(int)dFreqOut);
   short sbufInt[10000];
   int iPrev;
   CTUpSample2x u2x;
   CTUpSample2x u4x;
   int v21;
   int v41;
   int inc(short *pcmIn, short *pcmOut, int iSamplesIn, int iSOout=0)
   {
   /*
      u2x.doJob(pcmIn, iSamplesIn, pcmOut);
      u4x.doJob(pcmOut, iSamplesIn*2, sbufInt);
      decr.setFreq(iFreqInR*4, iFreqOutR);
      return decr.dec(&sbufInt[0],pcmOut,iSamplesIn*4,iSOout);
     */
      //ok
      int i,j,a=0;
      int iStep=(int)dStep;
      iStep+=1;
      int iCur;
      //int res
      //if()
      int cStep;
      for(i=0;i<iSamplesIn;i++)
      {
         iCur=pcmIn[i];
         cStep=(iCur-iPrev)/iStep;
         //iNext=i+1<iSamplesIn?pcmIn[i+1]:iCur;
         
         //sbufInt[a]=iPrev;a++;
         for(j=0;j<iStep;j++,a++)
         {
            
            sbufInt[a]=iPrev+cStep*j;//(iCur-iPrev)*j/iStep;////(pcmIn[i]);
            
         }
         
         iPrev=iCur;
      }
      sbufInt[iSamplesIn*iStep]=sbufInt[iSamplesIn*iStep-1];
      //decr.setFreq(iStep*(int)dFreqIn,(int)dFreqOut);
      //veel vareet noapaljot visu, liidziigi kaa hi lowpass
      return decr.dec(&sbufInt[0],pcmOut,iSamplesIn*iStep,iSOout);
      
      // return 0;
   }
   
};
#endif