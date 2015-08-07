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

#ifndef _T_VLC_H
#define _T_VLC_H

#define T_CAN_TEST_V

static const unsigned int expg_len[]={
 1, 3, 3, 5, 5, 5, 5, 7, 7, 7, 7, 7, 7, 7, 7, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,11,
11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,13,
13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,
13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,13,15,
15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,
15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,
15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,
15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,17,
};
//
static const unsigned int expg_val[]={
  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64,
 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96,
 97, 98, 99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,
129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,
161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,
193,194,195,196,197,198,199,200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224,
225,226,227,228,229,230,231,232,233,234,235,236,237,238,239,240,241,242,243,244,245,246,247,248,249,250,251,252,253,254,255,256,
};


static const unsigned  char tblZ[]={
   8,7,6,6,5,5,5,5,4,4,4,4,4,4,4,4,
   3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,
   2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,
   2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,
   1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
   1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
   1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
   1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
   0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
};

static inline int getZeroBits_f(register unsigned char *p, register int iPos){
#if 0
   int iPosIn=iPos;
   while(!(p[iPos>>3]&(128>>(iPos&7)))){
      iPos++;
   }

   return iPos-iPosIn;
#else
   register unsigned int c;
   int iCnt=0;
   const int iMaxIgnore=iPos&7;
   iPos>>=3;
   c=p[iPos];

   c<<=iMaxIgnore;
   c&=0xff;
   c>>=iMaxIgnore;

   iCnt=tblZ[c]-iMaxIgnore;

   while(!c){
      iPos++;
      c=p[iPos];
      iCnt+=tblZ[c];
   }
   
   return iCnt;
#endif
}
static inline int getNBits_f2b(register unsigned char *p, register int iPos)
{
   unsigned int b;
   p+=(iPos>>3);
   b=p[1]|(p[0]<<8);//|(p[1]<<16)|(p[0]<<24);
   b>>=(14-(iPos&7));//(iPos&7);
   b&=3;
   return (int)b;
}
static inline int getNBits_f(register unsigned char *p, register int iPos, int iBits)
{
#if 0
   register int c=0;
   int iLast=iPos+iBits;
   while(iPos<iLast){
      c<<=1;
     // register const int b=p[iPos>>3];
      if((p[iPos>>3]&(128>>(iPos&7))))c|=1;
      iPos++;

   }
   
   return c;
#else

#if 1
   const static unsigned int msk[33] =
   {
     0x00000000,0x00000001,0x00000003,0x00000007,
     0x0000000f,0x0000001f,0x0000003f,0x0000007f,
     0x000000ff,0x000001ff,0x000003ff,0x000007ff,
     0x00000fff,0x00001fff,0x00003fff,0x00007fff,
     0x0000ffff,0x0001ffff,0x0003ffff,0x0007ffff,
     0x000fffff,0x001fffff,0x003fffff,0x007fffff,
     0x00ffffff,0x01ffffff,0x03ffffff,0x07ffffff,
     0x0fffffff,0x1fffffff,0x3fffffff,0x7fffffff,
     0xffffffff
   };
   unsigned int b;
   p+=(iPos>>3);
   b=p[3]|(p[2]<<8)|(p[1]<<16)|(p[0]<<24);
   b>>=(32-(iPos&7)-iBits);//(iPos&7);
   b&=msk[iBits];
  // b<<=(iPos&7); b>>=(32-iBits);


   return b;

#else

   register unsigned char c;
   unsigned int res=0;
   
   unsigned int iBitsShift=(iPos&7);
   iPos>>=3;
   c=p[iPos];
   c<<=(iBitsShift);
   c>>=(iBitsShift);
   res|=c;
   iBits-=(8-iBitsShift);
   if(iBits<0){
      res>>=(-iBits);
      return (int)res;
   }
   while(iBits>0){
      iPos++;
      res<<=8;
      res|=p[iPos];
      if(iBits<8){
         res>>=(8-iBits);
         break;
      }
      iBits-=8;
   }

   return (int)res;
#endif
#endif
}


void addBit(unsigned char *p, int iBit, int &iPos);
int getBit(unsigned char *p, int iPos);
int getNBits(unsigned char *p, int iPos, int iBits);
int getZeroBits(unsigned char *p, int iPos);
void setBitF(unsigned char *p, int iBit, int &iPos);
//#define getNBits_f(_A,_B,_C) getNBits(_A,_B,_C)
static inline int getNotZeroBits(unsigned char *p, int iPos){
   /*
   int iPosIn=iPos;
   while(1){
      const int b=p[iPos>>3];
      if(!(b&(128>>(iPos&7))))break;
      iPos++;
   }
   return iPos-iPosIn;
   */
   int iPosIn=iPos;
   while((p[iPos>>3]&(128>>(iPos&7)))){
      iPos++;
   }
   return iPos-iPosIn;
}
static const unsigned char t_clearBits[]={0,0x80,0xc0,0xe0,0xf0,0xf8,0xfc,0xfe};


class CTVLCX{
   int iMaxBitsIn;
   int iMaxBytesIn;
   
public:
   unsigned char *pBitBuf;
   
   #define BL_SZ 8192
   
   void setDataBuf(unsigned char *p, int iBytes){
      pBitBuf=p;
      iMaxBytesIn=iBytes;
      iMaxBitsIn=iBytes*8;
   }
   inline int isBitPosOk(){return iBitPos<=iMaxBitsIn;}
   
   unsigned char bitsLen[BL_SZ];
   unsigned char bitsLenMV[2048];
   unsigned char bitsLenAC[1024];
   unsigned char bitsLenX2[256];
   
   unsigned char pButBufTest[256];
   CTVLCX(){
      iMaxBytesIn=100000;//TODO use setDataBuf
      iMaxBitsIn=iMaxBytesIn*8;
      iBitPos=0;iCalcBitsOnly=0;
      int i;
      unsigned char bits[16];
      for(i=0;i<BL_SZ;i++){
         iBitPos=0;
         pBitBuf=&bits[0];
         if(i<2048){
#ifdef T_CAN_TEST_V
            this->toVLC_PNS(i,1);
#else
         toAC_valSM(&bits[0],i);
#endif
         
            //CTVLCX::encZero2b(*this,i);
            //this->toMV(i);

            bitsLenMV[i]=iBitPos;
            if(i<256){
               iBitPos=0;
               this->toVLC_X(i,2);
               bitsLenX2[i]=iBitPos;
            }
         }
         iBitPos=0;
         toAC_val(&bits[0],i);
         bitsLen[i]=iBitPos;
      }
      for(i=0;i<1024;i++){bitsLenAC[i]=bitsLen[i<<1];}
      pBitBuf=&pButBufTest[0];

      iBitPos=0;
   }
   int iBitPos;
   int iCalcBitsOnly;
   void reset(){iBitPos=0;}
   int getBytePos(){return (iBitPos+7)>>3;}
#define byte unsigned char
   inline static int s_getVlcSigned(unsigned char *pBuf, int iPos, int &iBits)
   {
      int ret=s_getVlc(pBuf,iPos,iBits);
      if(ret){
         if(getBit(pBuf,iPos+iBits))ret=-ret;
         iBits++;
      }
      return ret;

   }
   inline int getZeroBits(){
      int iBits=getZeroBits_f(pBitBuf,iBitPos);
      iBitPos+=iBits;
      return iBits;

   }
   inline int get000n1(){
      int iBits=getZeroBits_f(pBitBuf,iBitPos);
      iBitPos+=iBits+1;
      return iBits;
   }
   inline void add000n1(int v){
      addXBits(1,v+1);
   }
   inline static int s_getVlc_x(unsigned char *pBuf, int iPos, int &iBits, int x)
   {
      //return s_getVlc(pBuf,iPos,iBits);
//      int i=0;
      int ret=0;

      iBits=getZeroBits_f(pBuf,iPos);
      //int iNegative=0;
      //iBits=0;while(getBit(pBuf,iPos+iBits)==0)iBits++;
      if(iBits)
      {
         //1 010 01xx
         ret=1;         
         for(int i=1;i<iBits;i++){ret+=(1<<((i)*x));}
         iPos+=iBits+1;
  
         ret+=getNBits_f(pBuf,iPos,iBits*x);
         //iBits=(iBits*2)+1+(iBits*(x-1));
         iBits=(iBits*(1+x))+1;
         

         //if(iNegative)ret=-ret;
      }
      else {iBits=1;}


      return ret;
   }
   inline static int s_getVlc_b(unsigned char *pBuf, int iPos, int &iBits, int b)
   {
//      int i=0;
      int ret=0;

     // if(getBit(pBuf,iPos)){iBits++;return 0;}
      iBits=getZeroBits_f(pBuf,iPos);
      //int iNegative=0;
      //iBits=0;while(getBit(pBuf,iPos+iBits)==0)iBits++;
     //-- 
      if(iBits)
      {
         iPos+=iBits;
         iBits++;
         ret=getNBits_f(pBuf,iPos,iBits+b);
         int r=ret&((1<<b)-1);
         ret>>=b;

         iBits<<=1;
         
         ret--;
         iBits--;
         iBits+=b;
         ret<<=b;ret|=r;
         //if(iNegative)ret=-ret;
      }
      else {ret=getNBits_f(pBuf,iPos+1,b);iBits=b+1;}

      return ret;
   }
   inline static int s_getVlc_bz(unsigned char *pBuf, int iPos, int &iBits, int b)
   {
//      int i=0;
      int ret=0;

     // if(getBit(pBuf,iPos)){iBits++;return 0;}
      iBits=getZeroBits_f(pBuf,iPos);
      //int iNegative=0;
      //iBits=0;while(getBit(pBuf,iPos+iBits)==0)iBits++;
     //-- 
      if(iBits)
      {
         iPos+=iBits;
         iBits++;
         ret=getNBits_f(pBuf,iPos,iBits+b);
         int r=ret&((1<<b)-1);ret>>=b;
         iBits<<=1;
         
         ret-=2;
         iBits--;

       //  ret--;
         ret<<=b;
         ret|=r;//getNBits_f(pBuf,iPos+iBits,b);
         iBits+=b;
         ret++;
         /*
      int r=getVlc();
     // int r=this->get000n1();
      if(!r)return 0; 
      r--;
      r<<=n;
      
      return (r|getNBitsC(n))+1;
         */
      }
      else {iBits=1;}

      return ret;
   }

   inline static int s_getVlc(unsigned char *pBuf, int iPos, int &iBits)
   {
//      int i=0;
      int ret=0;

     // if(getBit(pBuf,iPos)){iBits++;return 0;}
      iBits=getZeroBits_f(pBuf,iPos);
      //int iNegative=0;
      //iBits=0;while(getBit(pBuf,iPos+iBits)==0)iBits++;
     //-- 
      if(iBits)
      {
         iPos+=iBits;
         iBits++;
         ret=getNBits_f(pBuf,iPos,iBits);
         iBits<<=1;
         
         ret--;
         iBits--;
         //if(iNegative)ret=-ret;
      }
      else {iBits=1;}

      return ret;
   }
   inline static int s_L_getVlc(int b, unsigned char *pBuf, int iPos, int &iBits)
   {
      //int i=0;
      int ret=0;

      iBits=getZeroBits_f(pBuf,iPos);
      //int iNegative=0;
      //iBits=0;while(getBit(pBuf,iPos+iBits)==0)iBits++;
      if(iBits)
      {
         iPos+=iBits;
         iBits++;
         /*
         for(i=0;i<iBits;i++)
         {
            ret|=getBit(pBuf,iPos+i);
            if(i+1!=iBits)
               ret<<=1;
        //    else
          //     iNegative=getBit(pBuf,iPos+i+1);
         }
         */
         ret=getNBits_f(pBuf,iPos,iBits+b);
         iBits<<=1;
         
         ret--;
         iBits--;
         iBits+=b;
         //if(iNegative)ret=-ret;
      }
      else {iBits=1;}

      return ret;
   }

   static int  decZero2b(CTVLCX &vlc){
      int val=0;
      /*
      val=vlc.getVlc_PN(2);

      if(!val)return 0;
      {
         if(val&1){
            val++;
            val>>=1;
            val=-val;
         }
         else val>>=1;
      }
      return val;
*/
     // return vlc.getVlc_PNS(1);

      int r=getNBits_f2b(vlc.pBitBuf,vlc.iBitPos);//getNBits_f(vlc.pBitBuf,vlc.iBitPos,2);
      vlc.iBitPos+=2;
      switch(r){
         case 0:
            return 1;
         case 1:
            return -1;
         case 2:
            if(vlc.getB()){
               val=-vlc.getVlc()-2;
            }
            else{
               val=vlc.getVlc()+2;
            }
            break;
         case 3:
            return 0;

      }
      /*
      if(vlc.getB()){
         // zero or >1
         if(vlc.getB()){return 0;}
         //val=vlc.getAC();if(val<0)val-=1;else val+=2;
         
         if(vlc.getB()){
            val=-vlc.getVlc()-2;
         }
         else{
            val=vlc.getVlc()+2;
         }

      }
      else{
         val=vlc.getB()?-1:1;
      }
      */
      return val;

   }
   static void encZero2b(CTVLCX &vlc, int val){

      //vlc.toVLC_PNS(val,1);return ;

      if(val==0){vlc.addB(1); vlc.addB(1);return;}
      else if(val==1){vlc.addB(0); vlc.addB(0); }
      else if(val==-1){vlc.addB(0); vlc.addB(1); }
      else {vlc.addB(1); vlc.addB(0); if(val>0){vlc.addB(0);vlc.toVLC(val-2);}else {vlc.addB(1);vlc.toVLC(-val-2);}}
      //else {vlc.addB(1); vlc.addB(0); if(val>0){vlc.toAC(val-2);}else {vlc.toAC(val+1);}}
   }
   static void encNonZero(CTVLCX &vlc, int val)
   {
      /*
      val=val>=0?(val<<1):(((-val)<<1)-1);
      val--;
      vlc.toVLC_PNZ(val,2);
      return;
      */
      if(val<0){vlc.toVLC(-val-1);vlc.addB(1);}else {vlc.toVLC(val-1);vlc.addB(0);} 
   }
   static int decNonZero(CTVLCX &vlc)
   {
      /*
      int r=vlc.getVlc_PNZ(2)+1;
      if(r&1){
         r++;
         r>>=1;
         r=-r;
      }
      else r>>=1;
      */
      
      int r=vlc.getVlc()+1;
      if(vlc.getB())return -r;
      
      return r;
   }
   


   inline int getDC(){int b=0; int ret=s_getVlcSigned(pBitBuf,iBitPos,b);iBitPos+=b;return ret;}
   inline int getAC(){return getVlcSM();}

   inline int getNextAC(int &iSk){
      while((pBitBuf[iBitPos>>3]&(128>>(iBitPos&7)))){iSk++;iBitPos++;}
      //iSk+=getSkipBits2(pBitBuf);
      //return getVlcSM();
      int b=0;
      int ret=s_getVlc(pBitBuf,iBitPos,b);iBitPos+=b;
      if(ret&1){
         ret++;
         ret>>=1;
         return -ret;
      }
     // else ret>>=1;
      return ret>>1;
   }
   inline int getNextDC(int &iSk){
      while((pBitBuf[iBitPos>>3]&(128>>(iBitPos&7)))){iSk++;iBitPos++;}
      //return getVlcSM();
      int b=0;
      int ret=s_getVlcSigned(pBitBuf,iBitPos,b);iBitPos+=b;
      return ret;
   }
   inline int getVlc(){int b=0;int ret=s_getVlc(pBitBuf,iBitPos,b);iBitPos+=b;return ret;}
   inline int getVlcX(int x){int b=0;int ret=s_getVlc_x(pBitBuf,iBitPos,b,x);iBitPos+=b;return ret;}
#define SGN_MAP_INV(_V) (_V?(_V&1?(-((_V+1)>>1)):(_V>>1)):0)
   inline int getVlcSM(){
      int ret=getVlc();
      if(!ret)return 0;
      {
         if(ret&1){
            ret++;
            ret>>=1;
            ret=-ret;
         }
         else ret>>=1;
      }
      return ret;
   }
   inline int get_L_Val(int l){
      //int b=0;int ret=s_L_getVlc(l,pBitBuf,iBitPos,b);iBitPos+=b;return ret;
            //toAC_val(pBitBuf,(v>>b)+1);
      
      
      if(getB())return 0;
      int ret=getVlc();ret<<=l;
      int i;
      ret|=getBit(pBitBuf,iBitPos);iBitPos++;
      for(i=1;i<l;i++){ret|=(getBit(pBitBuf,iBitPos)<<i);iBitPos++;}
      ret++;
      return ret;

   }
   inline int get_L_Val_S(int l){
      
      int ret=get_L_Val(l);
      if(!ret)return 0;
      {
         if(ret&1){
            ret++;
            ret>>=1;
            ret=-ret;
         }
         else ret>>=1;
      }
      return ret;
   }

   inline void setBit(int iPos,int v){setBitF(pBitBuf,v,iPos);}
   inline int getVlcSigned(byte *pBuf=NULL){int b=0;if(!pBuf)pBuf=pBitBuf;int ret=s_getVlcSigned(pBuf,iBitPos,b);iBitPos+=b;return ret;}
   inline int getB(byte *p){int ret=getBit(p,iBitPos);iBitPos++;return ret;}
   //inline int getB(){int ret=getBit(pBitBuf,iBitPos);iBitPos++;return ret;}//(p[iPos>>3]&(128>>(iPos&7)))
//   inline int getB(){int c=pBitBuf[iBitPos>>3]&(128>>(iBitPos&7));iBitPos++;return !!c;}//(p[iPos>>3]&(128>>(iPos&7)))
   inline int getB(){int c=pBitBuf[iBitPos>>3]>>(7-(iBitPos&7));;iBitPos++;return c&1;}
   //(p[iPos>>3]&(128>>(iPos&7)))
   inline int showBit(int b){return getBit(pBitBuf,b);}
   inline int showNextBit(byte *p){if(!p)p=pBitBuf;return getBit(p,iBitPos);}
   inline void addB(int v){if(iCalcBitsOnly){iBitPos++;return ;}addBit(pBitBuf,v,iBitPos);}
   inline void toAritmDC_pred(byte *p,int v){//max 5
      addBit(p,v,iBitPos); // 0 vai 1xx,0,1,2,3,4
      if(v)
      {
         v--;
         addBit(p,v&2,iBitPos);
         addBit(p,v&1,iBitPos);
      }
   }
   inline void toAC_valSigned(byte *p, int v)
   {
      int iNeg;
      if(v<0){iNeg=1;v=-v;}else iNeg=0;
      toAC_val(p,v);
      if(v){
         if(iCalcBitsOnly)iBitPos++;
         else addBit(p,iNeg,iBitPos);
      }


   }
   inline void toMV(int v){
      return toAC_valSM(pBitBuf,v);
   }
   inline int toMV_calc(int x, int y){
#ifdef T_CAN_TEST_V
      if(!y && x>0)x--;
      int s=x>>31;x=(x+s)^s;
      int r=0;
      if(x<2048)r+=bitsLenMV[x];
      else r+=bitsLenMV[x>>4]+6;
      
      s=y>>31;y=(y+s)^s;
      if(y<2048)r+=bitsLenMV[y];
      else r+=bitsLenMV[y>>4]+6;

#else
      int s=x>>31;x=(x+s)^s;
      int r=0;
      if(x<2048)r+=bitsLenMV[x];
      else r+=bitsLenMV[x>>4]+6;
      
      s=y>>31;y=(y+s)^s;
      if(y<2048)r+=bitsLenMV[y];
      else r+=bitsLenMV[y>>4]+8;

#endif
      return r;
      /*
      if(!v)return 1;
      v=v>=0?(v<<1):(((-v)<<1)-1);
      if(v<2047)return bitsLen[v];
      return bitsLen[v>>10]+20;
      */
      //if(v<0)v=-v;
   }
   inline int toDC_calc(int v){
      if(!v)return 1;
      if(v<0)v=-v;
      v>>=2;
      return bitsLen[v>2047?2047:v]+3;
   }
   inline void toVLC_PN(int v, int n){
      toAC_val(pBitBuf,v>>n);
      addXBits(v,n);
   }
#define SGN_MAP(_A) (_A>=0?(_A<<1):(((-_A)<<1)-1))
   inline void toVLC_PNS(int v, int n){
      v=SGN_MAP(v);
      toAC_val(pBitBuf,v>>n);
      addXBits(v,n);
   }
   inline int getVlc_PN_old(int n){
      int r=getVlc();
      r<<=n;
      return r|getNBitsC(n);
   }
   inline int getVlc_PN(int n){

      int b;
      int ret=s_getVlc_b(pBitBuf,iBitPos,b,n);iBitPos+=b;
      return ret;
   }
   inline int getVlc_PNS(int n){
      int r=getVlc_PN(n);
      return SGN_MAP_INV(r);

   }

   inline void toVLC_PNZ(int v, int n){
#if 0
      if(v){
         addB(0);
         v--;
         toAC_val(pBitBuf,v>>n);
         addXBits(v,n);
      }
      else addB(1);
#else
      if(v){
         v--;
         toAC_val(pBitBuf,(v>>n)+1);
         //this->add000n1((v>>n)+1);
         addXBits(v,n);
      }
      else toAC_val(pBitBuf,0);
#endif
   }
   inline int getVlc_PNZ_old(int n){
#if 0
      if(getB())return 0;
      int r=getVlc();
      r<<=n;
      return (r|getNBitsC(n))+1;
#else
      int r=getVlc();
     // int r=this->get000n1();
      if(!r)return 0; 
      r--;
      r<<=n;
      
      return (r|getNBitsC(n))+1;
#endif
   }
   inline int getVlc_PNZ(int n){
      int b;
      int ret=s_getVlc_bz(pBitBuf,iBitPos,b,n);iBitPos+=b;
      return ret;
   }
   inline void toVLC(int v){
      return toAC_val(pBitBuf,v);
   }
   inline void toAC_valSM(byte *p, int v){
      v=SGN_MAP(v);//v>=0?(v<<1):(((-v)<<1)-1);
      toAC_val(p,v);
   }
   inline void toDC(int v){toAC_valSigned(pBitBuf,v);}
   inline void toAC(int v){toAC_valSM(pBitBuf,v);}
   inline static int getL(int x)
   {
      if(x==0)return 0;
      if(x<0)x=-x;
      if(x==1)return 1;
      if(x<4)return 2;
      if(x<8)return 3;
      int c=3;
      
      while(x>=((1<<c)))c++;

      return c;
   }
   void toLenVal(byte *p, int len, int v){
      if(iCalcBitsOnly){iBitPos+=len;return;}
      addBit(p,v<0,iBitPos);
      if(v<0)v=-v;
      len--;
      while(len>0){
         addBit(p,v&1,iBitPos);
         v>>=1;
         len--;
      }
   }
   void toLogVal(byte *p, int v){
      int l=getL(v);
      toAC_val(p,l);
      if(l)toLenVal(p,l,v);

   }
   inline void toL_Val_S(int v, int b){
      v=v>=0?(v<<1):(((-v)<<1)-1);
      toL_Val(v,b);
   }
   void toL_Val(int v, int b){
      if(!v){addBit(pBitBuf,1,iBitPos);return;}
      v--;
      addBit(pBitBuf,0,iBitPos);
      toAC_val(pBitBuf,(v>>b));
      int i;
      for(i=0;i<b;i++,v>>=1)addBit(pBitBuf,v&1,iBitPos);

      /*
      if(iCalcBitsOnly)
      {
         if(v<0)v=0;else if(v>2047)v=2047;
         iBitPos+=bitsLen[v>>b]+b;
         return;
      }
      unsigned char *p=pBitBuf;
      if(v==0){addBit(p,1,iBitPos);return ;}
      const unsigned char ma[]={0,0x80,0xc0,0xe0,0xf0,0xf8,0xfc,0xfe};

      const int bitSh3=iBitPos>>3;
      p[bitSh3]&=ma[iBitPos&7];
      p[bitSh3+1]=0;
      p[bitSh3+2]=0;
      p[bitSh3+3]=0;

      int tmp=(v>>b)+2;
      int m=4;

      iBitPos++;

      for(;tmp>m;){m<<=1;iBitPos++;}//b++;

      //TODO addBit(p,1,iBitPos);m>2;m>>=1;
      
      v++;
      m<<=b;

      for(;m>1;){m>>=1;if(v&m)p[iBitPos>>3]|=(128>>(iBitPos&7)); iBitPos++;}
*/
   }
#if 0
#define _T_264_EXPG
   static inline void bs_write( unsigned char *p, int i_count, int  i_bits, int &iPos )
   {
      int iAddBits=i_count;
       int iLeft=8-(iPos&7);
       p+=(iPos>>3);
       p[0]>>=(iLeft);

       while( i_count > 0 )
       {
           if( i_count < 32 )
               i_bits &= (1<<i_count)-1;
           if( i_count < iLeft)
           {
               *p = (*p << i_count) | i_bits;
               p[1]=0;
               iLeft -= i_count;
               break;
           }
           else
           {
               *p = (*p << iLeft) | (i_bits >> (i_count - iLeft));
               i_count -= iLeft;
               p++;
               p[0]=0;
               p[1]=0;
               iLeft = 8;
           }
       }
       iPos+=iAddBits;
       if(iLeft!=8)p[0]<<=(iLeft);
   }

   static inline void bs_write_ue( unsigned char  *p, unsigned int val, int &iPos )
   {
       int i_size = 0;
       static const unsigned char i_size0_255[256] =
       {
           1,1,2,2,3,3,3,3,4,4,4,4,4,4,4,4,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
           6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
           7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
           7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
           8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
           8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
           8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
           8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8
       };

       if( val == 0 )
       {
        //   bs_write( p, 1,1,iPos );
          addBit(p,1,iPos);
       }
       else
       {
           unsigned int tmp = ++val;

           if( tmp >= 0x00010000 )
           {
               i_size += 16;
               tmp >>= 16;
           }
           if( tmp >= 0x100 )
           {
               i_size += 8;
               tmp >>= 8;
           }
           i_size += i_size0_255[tmp];

           bs_write( p, 2 * i_size - 1, val ,iPos);
       }
   }


#endif
   inline void toAC_val(byte *p, int v)
   {
      if(iCalcBitsOnly)
      {
         if(v>2047)iBitPos+=bitsLen[v>>10]+20;
         else iBitPos+=bitsLen[v];
         return;
      }
      
      
      if(v==0){addBit(p,1,iBitPos);return ;}
#ifdef _T_264_EXPG
      if(1){// && iBitPos>15){
        // if(iBitPos&7)bs_write(&p[-1],iBitPos&7,p[-1]>>(8-(iBitPos&7)),bp);
         bs_write_ue(p,v,iBitPos);
         return ;
      }
#endif
      //static const unsigned char ma[]={0,0x80,0xc0,0xe0,0xf0,0xf8,0xfc,0xfe};
      const int bitSh3=iBitPos>>3;
      p[bitSh3]&=t_clearBits[iBitPos&7];
      p[bitSh3+1]=0;
      p[bitSh3+2]=0;
      if(v<255){
         addXBitsZ(expg_val[v],expg_len[v]);
         return;
      }
      p[bitSh3+3]=0;
      p[bitSh3+4]=0;
     // *(int*)(char*)(((int)p+(iBitsInBuf>>3)+100)&~3)=0;
      //--int b=2;
      int tmp=v+2;
      int m=4;

      iBitPos++;
      while(tmp>m){m<<=1;iBitPos++;}
      
      //TODO addBit(p,1,iBitPos);m>2;m>>=1;
      
      v++;
      while(m>1){m>>=1;if(v&m)p[iBitPos>>3]|=(128>>(iBitPos&7)); iBitPos++;}
   }
   int getNBitsC(unsigned int b){
      int ret=getNBits_f(pBitBuf,iBitPos,b);
      iBitPos+=b;
      return ret;
   }
   int showNBitsC(unsigned int b, int iFrom){
      int ret=getNBits_f(pBitBuf,iFrom,b);
      
      return ret;
   }
   void copyBits(unsigned char *p, int iFrom, int b){
      int i,ret,l;
      if(b>100 && 0){
         memset(pBitBuf+1+((iBitPos)>>3),0,((b)>>3)-2);
         ret=getNBits_f(p,iFrom,24);
         addXBits(ret,24);
         iFrom+=24;
         b-=24;

         for(i=24;i<b;i+=24){

            l=b-i;
            if(l>24)l=24;
            ret=getNBits_f(p,iFrom,l);
            addXBitsZ(ret,l);
            iFrom+=l;
         }
         ret=getNBits_f(p,iFrom,24);
         addXBits(ret,24);
         iFrom+=24;
      }
      else{
         for(i=0;i<b;i+=24){

            l=b-i;
            if(l>24)l=24;
            ret=getNBits_f(p,iFrom,l);
            const int shz=iBitPos>>3;
            p[shz]&=t_clearBits[iBitPos&7];
            p[shz+1]=p[shz+2]=p[shz+3]=0;
            addXBitsZ(ret,l);
            iFrom+=l;
         }
      }
   }
   void addXBitsZ(unsigned int v, unsigned int b){
      if(!v){iBitPos+=b;return; }
      unsigned int m=1<<(b);
      while(m>1){
         m>>=1;
        if(v&m)addBit(pBitBuf,1,iBitPos);else iBitPos++;
      //   b--;
      }
   }
   void addXBits(unsigned int v, unsigned int b){
#if 1
      unsigned int m=1<<(b);
      while(m>1){
         m>>=1;
         addBit(pBitBuf,(v&m),iBitPos);
      //   b--;
      }
#else
      //int iAddBits=b;
       int iLeft=8-(iBitPos&7);
       unsigned char *p=pBitBuf+(iBitPos>>3);
       iBitPos+=b;
       p[0]>>=(iLeft);

       while( b > 0 )
       {
           if( b < 32 )
               v &= (1<<b)-1;
           if( b < iLeft)
           {
               *p = (*p << b) | v;
               //p[1]=0;
               iLeft -= b;
               break;
           }
           else
           {
               *p = (*p << iLeft) | (v >> (b - iLeft));
               b -= iLeft;
               p++;
               //p[0]=0;
               //p[1]=0;
               iLeft = 8;
           }
       }
       //iBitPos+=iAddBits;
       if(iLeft!=8)p[0]<<=(iLeft);      
#endif
   }
   void toVLC_X(int v, int x)
   {
      byte *p=pBitBuf;
      
      if(iCalcBitsOnly)
      {
         if(!v){iBitPos++;return ;}
         if(v>2047)iBitPos+=bitsLen[v>>2]+x;
         else iBitPos+=bitsLen[v>>2]+x;
         
         return;
      }
      
      
      if(v==0){addBit(p,1,iBitPos);return ;}


      const int bitSh3=iBitPos>>3;
      p[bitSh3]&=t_clearBits[iBitPos&7];
      p[bitSh3+1]=0;
      p[bitSh3+2]=0;
      p[bitSh3+3]=0;
      p[bitSh3+4]=0;
      p[bitSh3+5]=0;

      int iRem=x;
v--;
      while(1){
         
         iBitPos++;
         if(v<(1<<(iRem)))break;
         v-=1<<(iRem);
         iRem+=x;
      }
      addBit(p,1,iBitPos);

      addXBitsZ(v,iRem);

      return;


   }
   
   void toAritSignedValMaxUShort(byte *p, int v)
   {
      int iBitsInBuf=iBitPos;
//      int iVCmp;
      if(v>0)addBit(p,0,iBitsInBuf);
      if(v>2)addBit(p,0,iBitsInBuf);;
      if(v>6)addBit(p,0,iBitsInBuf);;
      if(v>14)addBit(p,0,iBitsInBuf);
      if(v>30)addBit(p,0,iBitsInBuf);
      if(v>62)addBit(p,0,iBitsInBuf);
  //    if(v>126)addBit(p,0,iBitsInBuf);
    //  if(v>254)addBit(p,0,iBitsInBuf);
      //if(v>510)addBit(p,0,iBitsInBuf);
      //if(v>1024)addBit(p,0,iBitsInBuf);

      v++;
      if(v){
         if(v>1){
            if(v>3){
               if(v>7){
                  if(v>15){
                     if(v>31){
                        if(v>63){
                           addBit(p,v & 128?1:0,iBitsInBuf);
                           addBit(p,v &  64?1:0,iBitsInBuf);
                        }
                        addBit(p,v&32?1:0,iBitsInBuf);
                     }
                     addBit(p,v&16?1:0,iBitsInBuf);
                  }
                  addBit(p,v&8?1:0,iBitsInBuf);
               }
               addBit(p,v&4?1:0,iBitsInBuf);
            }
            addBit(p,v&2?1:0,iBitsInBuf);
         }
         addBit(p,v&1?1:0,iBitsInBuf);

      }
      iBitPos=iBitsInBuf;
   }

};
#endif
