#ifndef _C_T_CODEC_BASE_H
#define _C_T_CODEC_BASE_H

#include <string.h>

#include "../audio/CTResampler.h"

class CCodecBase{
   //char *prevPacket_base_dec;//encoder only
   //char *prevPacket_base_enc;//encoder only
   int iPrevPackLenDec;
   int iPrevPackLenEnc;
   int iMaxDecPrevLen;
   short tmpInRateConvEnc[4096];
   short tmpInRateConvDec[4096];
//--   friend class CConvert;
protected:
   int iEncoderInSRate,iDecoderOutSRate;
   int iCodecSRate;
   CTResampler resamplerE;
   CTResampler resamplerD;
   
   int iCodecFrameSizeEnc;
   int iCodecFrameSizeDec;
   friend class CConvert;

   CCodecBase(int iCodecFrameSizeEnc, int iCodecFrameSizeDec)
      :iCodecFrameSizeEnc(iCodecFrameSizeEnc)
      ,iCodecFrameSizeDec(iCodecFrameSizeDec)
   {
      iCodecSRate=8000;
      iEncoderInSRate=8000;
      iDecoderOutSRate=8000;
      iPrevPackLenDec=0;
      iPrevPackLenEnc=0;
     // prevPacket_base_dec =new char [iCodecFrameSizeDec*16+80];
     // prevPacket_base_enc =new char [iCodecFrameSizeEnc*16+80+1024];
      iMaxDecPrevLen=iCodecFrameSizeDec*12;
      //memset(prevPacket_base_dec,5,sizeof(iCodecFrameSizeDec*8));
   }
   virtual int encodeFrame(short *pIn, unsigned char *pOut)=0;
public:
   virtual int decodeFrame(unsigned char *pIn, short *pOut)=0;
   virtual ~CCodecBase(){
      //if(prevPacket_base_enc)delete prevPacket_base_enc;if(prevPacket_base_dec)delete prevPacket_base_dec;prevPacket_base_enc=NULL;prevPacket_base_dec=NULL;
   }
   virtual int hasPLC()=0;
   virtual int getCurentDecBytesUsed(){return iCodecFrameSizeEnc;}
   virtual int canDecode(unsigned char *pIn, int iBytesIn, int iBytesInBuf)
   {
      if(iCodecFrameSizeEnc==0)return 1;//TODO
      if((iBytesIn%iCodecFrameSizeEnc)!=0)return 0;
      
      
      return iBytesIn*iDecoderOutSRate/iCodecFrameSizeEnc*iCodecFrameSizeDec/iCodecSRate<=iBytesInBuf;
   }
   virtual int getTSMult(){
      if(iEncoderInSRate>iCodecSRate)return iEncoderInSRate/iCodecSRate;
      if(iDecoderOutSRate>iCodecSRate)return iDecoderOutSRate/iCodecSRate;
      return 1;
   }
   
   inline int codeSampleRate(){return iCodecSRate;}
   
   void setDecoderOutSRate(int iRate){
      iDecoderOutSRate=iRate;
   }
   
   void setEncoderInSRate(int iRate){
      iEncoderInSRate=iRate;
   }

	   
   virtual inline int encode(short *pIn, unsigned char *pOut, int iBytes)
   {
      int i,t=0;
      /*
       short *pInO=pIn;
       unsigned char *pOutO=pOut;
      if(iBytes<iMaxDecPrevLen && iBytes && iPrevPackLenDec==iBytes && memcmp(pIn,prevPacket_base_dec,iBytes)==0)
      {
         memcpy(pOut,prevPacket_base_enc,iPrevPackLenEnc);
         return iPrevPackLenEnc;
      }
       */
      if(iCodecSRate!=iEncoderInSRate){
         resamplerE.setRates(iEncoderInSRate,iCodecSRate);
         //TODO fix iBytes==0
         iBytes=resamplerE.doJob(pIn,iBytes/2,&tmpInRateConvEnc[0])*2;
         pIn=&tmpInRateConvEnc[0];
      }

      if(iBytes==0)
         t=encodeFrame(pIn,pOut);
      else if(iCodecFrameSizeEnc==0)
      {
         int a;
         i=0;
         while(i<iBytes)
         {
           a=encodeFrame(pIn,pOut);
           if(a==0)return 0;
           i+=iCodecFrameSizeDec;
           pIn+=(iCodecFrameSizeDec/2);
           pOut+=a;
           t+=a;
        }
      }
      else
      {
         t=iBytes/iCodecFrameSizeDec;
         for(i=0 ;  i<t ;  i++, pIn+=(iCodecFrameSizeDec/2), pOut+=iCodecFrameSizeEnc)
         {
            encodeFrame(pIn,pOut);
         }
         t=t*iCodecFrameSizeEnc;
      }
      if(iBytes<iMaxDecPrevLen && t && iBytes)
      {
       //  memcpy(prevPacket_base_dec,pInO,iBytes);
        // memcpy(prevPacket_base_enc,pOutO,t);
         iPrevPackLenDec=iBytes;
         iPrevPackLenEnc=t;
      }
      return t;
   }
   virtual inline int decode(unsigned char *pIn, short *pOut,int iBytes)
   {
      int i;
      int t=0;
      
      short *pOutIn=pOut;
      
      if(iBytes==0)return decodeFrame(pIn,pOut);

      if(iCodecFrameSizeEnc==0)
      {
         int a;
         

         while(iBytes>0)
         {
            i=decodeFrame(pIn,pOut);
            if(i<=0)return 0;
            pOut+=(i)/2;
            a=getCurentDecBytesUsed();
            if(a<=0)return 0;
            pIn+=a;
            iBytes-=a;
            t+=i;
         }
    
      }
      else
      {
         t=iBytes/iCodecFrameSizeEnc;
         for(i=0 ;  i<t ; i++ ,pIn+=iCodecFrameSizeEnc,pOut+=(iCodecFrameSizeDec)/2)
         {
            decodeFrame(pIn,pOut);
         }
         t=iCodecFrameSizeDec*t;
      }
      if(iCodecSRate!=iDecoderOutSRate){
         resamplerD.setRates(iCodecSRate,iDecoderOutSRate);
         t=resamplerD.doJob(pOutIn,t/2,&tmpInRateConvDec[0])*2;
         memcpy(pOutIn,&tmpInRateConvDec[0],t);
      }
      
      return t;//iCodecFrameSizeDec*t; 
   }	   
	   
   inline int getFrameSizeDec(){return iCodecFrameSizeDec;}
   inline int getFrameSizeEnc(){return iCodecFrameSizeEnc;}

   /*
   int id;
   char szNameFull[128];//g711 ulaw, g711 alaw, ...
   char szNameShort[32];//PCMU, PCMA, ...
   */
};
class CConvert{
   char buf[8000];
public:
   CConvert(CCodecBase *c1, CCodecBase *c2,int iMaxBytes=0)
      :c1(c1),c2(c2),bufTmp(NULL),iMaxBytes(8000)
   {
      bufTmp=buf;
      if(iMaxBytes==0)
      {
         iMaxBytes=c1->getFrameSizeDec();
         if(iMaxBytes<c2->getFrameSizeDec())
            iMaxBytes=c2->getFrameSizeDec();
      }

     // bufTmp =new char [iMaxBytes+1];
   }
   virtual ~CConvert()
   {
      ////if(bufTmp)
       //  delete bufTmp;
   }
   inline int convert1to2(unsigned char *p)
   {
      c1->decodeFrame(p,(short *)bufTmp);
      return c2->encodeFrame((short *)bufTmp,p);
   }
   inline int convert2to1(unsigned char *p)
   {
      c2->decodeFrame(p,(short *)bufTmp);
      return c1->encodeFrame((short *)bufTmp,p);
   }   
   inline int convert1to2(unsigned char *p, int iBytes)
   {
      //TODO check tmpBufMaxSize
      if(!c1->canDecode(p,iBytes,iMaxBytes))return -1;
      int i=c1->decode(p,(short *)bufTmp,iBytes);
      return c2->encode((short *)bufTmp,p,i);
   }   
   inline int convert2to1(unsigned char *p, int iBytes)
   {
      if(!c2->canDecode(p,iBytes,iMaxBytes))return -1;
      int i=c2->decode(p,(short *)bufTmp,iBytes);
      return c1->encode((short *)bufTmp,p,i);
   }   


//protected:
   CCodecBase *c1;
   CCodecBase *c2;
private:
   char *bufTmp;
   int iMaxBytes;

};
#endif //_C_T_CODEC_BASE_H
