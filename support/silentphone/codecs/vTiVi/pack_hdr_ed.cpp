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

#define T_CAN_TEST_V

#include "pack_hdr.h"
#define byte unsigned char
class CTBitDec{
public:
   static int addBit(byte b[], int iPos, int iBit)
   {
      int bd8=iPos>>3;
      if(iBit)
      {
         b[bd8] |= (1<<(7-(iPos-(bd8<<3))));
      }
      else
      {
         b[bd8] &= (~(1<<(7-(iPos-(bd8<<3)))));
      }
      

      return iPos+1;
   }
   static int getBit(byte b[], int iPos)
   {
      
      int c;
      int bd8=iPos>>3;
      /*
      c=*(p+(bd8));
      c=(c>>(7-(iPos-(bd8<<3))))&1;
      */
      c=b[bd8];
      c=(c>>(7-(iPos-(bd8<<3))))&1;
      return !!c;
   }   
   static int getNBits(byte b[], int iPos, int iBits)
   {
      int c=0;
      int i;
      for(i=0;i<iBits;i++)
      {
         c<<=1;
         c|=getBit(b,iPos+i);
      }
      return c;
   }
   
};
class CTVLC{
public:
   int iBitPos;
   CTVLC(){iBitPos=0;}
   int getBytePos(){return (iBitPos+7)>>3;}
   void reset(){iBitPos=0;}
   int getBitValArit4(byte pBuf[])
   {
      int v=0;
      int  iBits=1;
      int b=CTBitDec::getBit(pBuf,iBitPos);
      if(b)
      {
         iBits=2;
         b=CTBitDec::getBit(pBuf,iBitPos+1);
         if(b)
         {
            iBits=3;
            v=CTBitDec::getBit(pBuf,iBitPos+2)?3:2;
         }
         else 
            v=1;
         
      }
      iBitPos+=iBits;
      return v;
   }
   int getBitValArit3(byte pBuf[])
   {
      int v=0;
      int iBits=1;
      int b=CTBitDec::getBit(pBuf,iBitPos);
      if(b)
      {
         iBits=2;
         v=CTBitDec::getBit(pBuf,iBitPos+1)?2:1;
      }
      iBitPos+=iBits;
      return v;
   }   
   int getBitValAritSig(byte pBuf[])
   {
      int ret=0;

      int iBits=0;
      int iNegative=0;

      while(CTBitDec::getBit(pBuf,iBitPos+iBits)==0)iBits++;
      if(iBits)
      {
         iBitPos+=iBits;
         ret=CTBitDec::getNBits(pBuf,iBitPos,iBits+1);
         ret--;
         iBits+=2;
         iBitPos+=iBits;
         iNegative=CTBitDec::getBit(pBuf,iBitPos-1);
         if(iNegative)ret=-ret;
      }
      else {iBitPos++;}


      return ret;
   }
   int getBitValArit(byte pBuf[])
   {
      int ret=0;

      int iBits=0;
      //int iNegative=0;
      while(CTBitDec::getBit(pBuf,iBitPos+iBits)==0)iBits++;
      if(iBits)
      {
         ret=CTBitDec::getNBits(pBuf,iBitPos+iBits,iBits+1);
         ret--;
         iBits*=2;iBits++;
         iBitPos+=iBits;
      }
      else {iBitPos++;}

      return ret;
   }
   int getBitValAritMaxUShort(byte pBuf[])
   {

      int ret=0;

      int iBits=0;
   
      while(CTBitDec::getBit(pBuf,iBitPos+iBits)==0)
      {
         iBits++;
         if(iBits==10)break;
      }
      if(iBits)
      {
         iBitPos+=iBits;
         if(iBits==10)iBits=24;
         ret=CTBitDec::getNBits(pBuf,iBitPos,iBits+1);
         ret--;
         iBits++;
         iBitPos+=iBits;
      }
      else {iBitPos++;}
      

      return ret;

   }
   int getBit(byte pBuf[]){
       int r=CTBitDec::getBit(pBuf,iBitPos);
       iBitPos++;
       return r;
   }
   int getNBits(byte pBuf[], int b){
      int r=CTBitDec::getNBits(pBuf,iBitPos,b);
      iBitPos+=b;
      return r;
   }
};

void addBit(unsigned char *p, int iBit, int &iPos);

class CTVLCE{
public:
   CTVLCE(){iBitPos=0;}
   int iBitPos;
   void reset(){iBitPos=0;}
   int getBytePos(){return (iBitPos+7)>>3;}

   void toAritSignedValMaxUShort(byte *p, int v)
   {
      int iBitsInBuf=iBitPos;
      int iVCmp;
      if(v>0)addBit(p,0,iBitsInBuf);
      if(v>2)addBit(p,0,iBitsInBuf);;
      if(v>6)addBit(p,0,iBitsInBuf);;
      if(v>14)addBit(p,0,iBitsInBuf);
      if(v>30)addBit(p,0,iBitsInBuf);
      if(v>62)addBit(p,0,iBitsInBuf);
      if(v>126)addBit(p,0,iBitsInBuf);
      if(v>254)addBit(p,0,iBitsInBuf);
      if(v>510)addBit(p,0,iBitsInBuf);
      if(v>1024)addBit(p,0,iBitsInBuf);

      v++;
      if(v){
         if(v>1){
            if(v>3){
               if(v>7){
                  if(v>15){
                     if(v>31){
                        if(v>63){
                           if(v>127){
                              if(v>255){
                                 if(v>511){
                                    if(v>1023){
                                       for(iVCmp=256*256*256;iVCmp>1023;iVCmp>>=1)
                                       {
                                          addBit(p,v&iVCmp?1:0,iBitsInBuf);
                                       }
                                    }
                                    addBit(p,v&512?1:0,iBitsInBuf);
                                 }
                                 addBit(p,v&256?1:0,iBitsInBuf);
                              }
                              addBit(p,v&128?1:0,iBitsInBuf);
                           }
                           addBit(p,v&64?1:0,iBitsInBuf);
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

   void toAritSignedVal(byte p[], int v)
   {
      int iBitsInBuf=iBitPos;
      int iNeg;
      if(v<0)
      {
         iNeg=1;
         v=-v;
      }
      else iNeg=0;
      if(v>0)addBit(p,0,iBitsInBuf);
      if(v>2)addBit(p,0,iBitsInBuf);;
      if(v>6)addBit(p,0,iBitsInBuf);;
      if(v>14)addBit(p,0,iBitsInBuf);
      if(v>30)addBit(p,0,iBitsInBuf);
      if(v>62)addBit(p,0,iBitsInBuf);
      if(v>126)addBit(p,0,iBitsInBuf);
      if(v>254)addBit(p,0,iBitsInBuf);
      if(v>510)addBit(p,0,iBitsInBuf);
      if(v>1024)addBit(p,0,iBitsInBuf);

      v++;
      if(v){
         if(v>1){
            if(v>3){
               if(v>7){
                  if(v>15){
                     if(v>31){
                        if(v>63){
                           if(v>127){
                              if(v>255){
                                 if(v>511){
                                    if(v>1023){
                                       addBit(p,v&1024?1:0,iBitsInBuf);
                                    }
                                    addBit(p,v&512?1:0,iBitsInBuf);
                                 }
                                 addBit(p,v&256?1:0,iBitsInBuf);
                              }
                              addBit(p,v&128?1:0,iBitsInBuf);
                           }
                           addBit(p,v&64?1:0,iBitsInBuf);
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
         if(v!=1){addBit(p,iNeg?1:0,iBitsInBuf);}

      }
      iBitPos=iBitsInBuf;

   }
   //void toAritUnsignVal(unsigned char *p, int v, int &iBitsInBuf)
   void toAritUnsignVal(byte p[], int v)
   {
      //v=data[i];
      int iBitsInBuf=iBitPos;
      if(v>0)addBit(p,0,iBitsInBuf);
      if(v>2)addBit(p,0,iBitsInBuf);;
      if(v>6)addBit(p,0,iBitsInBuf);;
      if(v>14)addBit(p,0,iBitsInBuf);
      if(v>30)addBit(p,0,iBitsInBuf);
      if(v>62)addBit(p,0,iBitsInBuf);
      if(v>126)addBit(p,0,iBitsInBuf);
      if(v>254)addBit(p,0,iBitsInBuf);
      if(v>510)addBit(p,0,iBitsInBuf);
      if(v>1022)addBit(p,0,iBitsInBuf);
      v++;
      if(v){
         if(v>1){
            if(v>3){
               if(v>7){
                  if(v>15){
                     if(v>31){
                        if(v>63){
                           if(v>127){
                              if(v>255){
                                 if(v>511){
                                    if(v>1023){
                                       addBit(p,v&1024?1:0,iBitsInBuf);
                                    }
                                    addBit(p,v&512?1:0,iBitsInBuf);
                                 }
                                 addBit(p,v&256?1:0,iBitsInBuf);
                              }
                              addBit(p,v&128?1:0,iBitsInBuf);
                           }
                           addBit(p,v&64?1:0,iBitsInBuf);
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

   void toAritMax4Val(byte p[], int v)
   {
      int iBitsInBuf=iBitPos;
      switch(v)
      {
      case 0:addBit(p,0,iBitsInBuf);
         break;
      case 1:addBit(p,1,iBitsInBuf);addBit(p,0,iBitsInBuf);
         break;
      case 2:addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);addBit(p,0,iBitsInBuf);
         break;
      case 3:addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);
         break;
      }
      iBitPos=iBitsInBuf;
   }
   void toAritMax3Val(byte p[], int v)
   {
      int iBitsInBuf=iBitPos;
      switch(v)
      {
      case 0:addBit(p,0,iBitsInBuf);
         break;
      case 1:addBit(p,1,iBitsInBuf);addBit(p,0,iBitsInBuf);
         break;
      case 2:addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);
         break;
      }
      iBitPos=iBitsInBuf;
   }

   void toAritMax2Val(byte p[], int v)
   {
     addBit(p,v&1?1:0,iBitPos);
   }
   void addXBits(byte p[],unsigned int v, unsigned int b){
      unsigned int m=1<<(b);
      while(m>1){
         m>>=1;
         addBit(p,(v&m),iBitPos);
      }
         //   b--;
   }

};


static inline long  t_getInt32(unsigned char* buf, int ofs)
{
   long i=(((long)buf[ofs])&0xff)|((((long)buf[ofs+1])&0xff)<<8)|((((long)buf[ofs+2])&0xff)<<16)|((((long)buf[ofs+3])&0xff)<<24);;
   return i; 
}
static inline long  t_getInt16(unsigned char* buf, int ofs)
{
   long i=(((long)buf[ofs])&0xff)|((((long)buf[ofs+1])&0xff)<<8);
   return i; 
}


#ifndef T_CAN_TEST_V
int TVHDR_PCK::encode(CTVLCE &vlc, unsigned char *out)
{
   out[0]='T';out[1]='I';out[2]='N';out[3]='A';
   out[8]='3';
   out[10]=0;
   
   vlc.iBitPos+=((4+1+1+4+1)<<3);

#define ADD_TO_VLC(_V) vlc.toAritSignedValMaxUShort(out,(int)(_V))
   ADD_TO_VLC(decpos);
   //ADD_TO_VLC(iDrawPos);
   //ADD_TO_VLC(ckunkw);
   //ADD_TO_VLC(ckunkh);
   ADD_TO_VLC(w);
   ADD_TO_VLC(h);
   ADD_TO_VLC(iFrNr);

   //ADD_TO_VLC(iSizeOfChunk);

   ADD_TO_VLC(iIsJpg);
   ADD_TO_VLC(iIsDCT);
   ADD_TO_VLC(iIsWavelet);
   ADD_TO_VLC(iIsKey);
   ADD_TO_VLC(iIsDif);
   ADD_TO_VLC(iSwapRef);
   ADD_TO_VLC(iIsParts);
   ADD_TO_VLC(iIsVect);
   ADD_TO_VLC(iIsZiped);
   //ADD_TO_VLC(iDeblock);
   ADD_TO_VLC(iDecSize);
   ADD_TO_VLC(iIsB);
   ADD_TO_VLC(iIsKRef);

   sizeofHdr=vlc.getBytePos()+6+5;
   out[9]=sizeofHdr;//vlc.getBytePos()+6;
#undef ADD_TO_VLC
   return out[9];
}
int TVHDR_PCK::decode(CTVLC &vlc, unsigned char *in)
{
   //--err arm iSizeOfChunk=*(int*)(in+4);

   iSizeOfChunk=t_getInt32(in,4);//in[4]+(in[5]<<8)+(in[6]<<16)+(in[7]<<24);
   in+=11;
   flagx=in[-1];
   vers=in[-3];
   iCanDraw=(flagx&1);
   if(flagx>1)iDeblock=flagx>>1;else iDeblock=0;
   ckunkw=ckunkh=0;

   sizeofHdr=in[-2];
#define ADD_TO_VLC(_V) (_V)=vlc.getBitValAritMaxUShort(in);


   ADD_TO_VLC(decpos);
   //ADD_TO_VLC(iDrawPos);
  // ADD_TO_VLC(ckunkw);
  // ADD_TO_VLC(ckunkh);
   ADD_TO_VLC(w);
   ADD_TO_VLC(h);
   ADD_TO_VLC(iFrNr);

   //ADD_TO_VLC(iSizeOfChunk);

   ADD_TO_VLC(iIsJpg);
   ADD_TO_VLC(iIsDCT);
   ADD_TO_VLC(iIsWavelet);
   ADD_TO_VLC(iIsKey);
   ADD_TO_VLC(iIsDif);
   ADD_TO_VLC(iSwapRef);
   ADD_TO_VLC(iIsParts);
   ADD_TO_VLC(iIsVect);
   ADD_TO_VLC(iIsZiped);
   //ADD_TO_VLC(iDeblock);
   ADD_TO_VLC(iDecSize);
   ADD_TO_VLC(iIsB);
   ADD_TO_VLC(iIsKRef);

   //TIN4
   //packSZ 16b
   //w h, vlc(w/16,3) vlc(w/32-h/16)+s, avg 8b
   //dif,b,key (1,01,00)
   //if(!b)ref 1b
   //vec 1b//Rep vec 1 01 01
   //frnr 3b
   //flags 1b-Draw,1b-Debl
   //64b

   //pic q 6b aQ
   //bQ 1 cQ 1 dc 1 
   //uv 5 1 1 1

#undef ADD_TO_VLC
   return sizeofHdr;
}
void encSetDrawFlag(unsigned char *p)
{
   //unsigned char  *p=(unsigned char*)pack;
   p[10]|=1;
}
void encSetDeblockValue(unsigned char *p, int iVal)
{
  // unsigned char  *p=(unsigned char*)pack;
   int v=p[10]&1;
   p[10]=((iVal)*2)|v;
}

void encSetChunkSize(unsigned char *pack, int iSize)
{
   int  *p=(int*)pack;
   p[1]=iSize;
}

#else
int TVHDR_PCK::encode(CTVLCE &vlc, unsigned char *out)
{


   //T4
   //packSZ 16b
   //w h, vlc(w/16,3) vlc(w/32-h/16)+s, avg 8b
   //dif,b,key (1,01,00)
   //if(!b)ref 1b
   //vec 1b//Rep vec 1 01 01
   //frnr 3b
   //flags 1b-Draw,1b-Debl
   //64b

   out[0]='T';out[1]='4';
   out[2]=out[3]=out[4]=0;
   
   vlc.iBitPos=((4)<<3);

#define ADD_TO_VLC(_V) vlc.toAritSignedValMaxUShort(out,(int)(_V))
   vlc.iBitPos+=2;

   ADD_TO_VLC(w>>4);
   vlc.toAritSignedVal(out,(h>>4)-((w>>4)>>1));
   //ADD_TO_VLC(iFrNr);
   vlc.addXBits(out,iFrNr,3);
   if(!iIsKey){
      vlc.toAritMax3Val(out,iIsB);
   }
   else {
      vlc.toAritMax3Val(out,2);
   }
   if(!iIsB){
      vlc.toAritMax2Val(out,iIsKRef);
   }
   vlc.toAritMax3Val(out,iIsVect);

   ADD_TO_VLC(decpos);
   //vlc.addXBits(out,1,decpos+1);
 

/*
   ADD_TO_VLC(iIsJpg);
   ADD_TO_VLC(iIsDCT);
   ADD_TO_VLC(iIsWavelet);
   ADD_TO_VLC(iIsKey);
   ADD_TO_VLC(iIsDif);
   ADD_TO_VLC(iSwapRef);
   ADD_TO_VLC(iIsParts);
   ADD_TO_VLC(iIsVect);
   ADD_TO_VLC(iIsZiped);
   //ADD_TO_VLC(iDeblock);
   ADD_TO_VLC(iDecSize);
   ADD_TO_VLC(iIsB);
   ADD_TO_VLC(iIsKRef);
*/
#undef ADD_TO_VLC
   sizeofHdr=vlc.getBytePos();
   return sizeofHdr;
}
int TVHDR_PCK::decode(CTVLC &vlc, unsigned char *in)
{
   //--err arm iSizeOfChunk=*(int*)(in+4);
#define ADD_TO_VLC(_V) (_V)=vlc.getBitValAritMaxUShort(in);

   ckunkw=ckunkh=0;
   /*
   ADD_TO_VLC(iIsJpg);
   ADD_TO_VLC(iIsDCT);
   ADD_TO_VLC(iIsWavelet);
   ADD_TO_VLC(iIsKey);
   ADD_TO_VLC(iIsDif);
   ADD_TO_VLC(iSwapRef);
   ADD_TO_VLC(iIsParts);
   ADD_TO_VLC(iIsVect);
   ADD_TO_VLC(iIsZiped);
   //ADD_TO_VLC(iDeblock);
   ADD_TO_VLC(iDecSize);
   ADD_TO_VLC(iIsB);
   ADD_TO_VLC(iIsKRef);
*/
   iIsJpg=0;
   iIsDCT=0;
   iIsWavelet=0;
   iIsKey=0;
   iSwapRef=0;
   iIsParts=0;
   iIsVect=0;
   iIsZiped=0;
   iDecSize=0;
   iIsB=0;
   iIsKRef=0;


   vers=in[1];
   iSizeOfChunk=t_getInt16(in,2);
   in+=4;
 
   iCanDraw=vlc.getBit(in);
   iDeblock=vlc.getBit(in);

   ADD_TO_VLC(w);w<<=4;
   h=vlc.getBitValAritSig(in);

   h+=((w>>4)>>1);h<<=4;
   //h+=((w>>4)>>1);h<<=4;
   //ADD_TO_VLC(iFrNr);
   iFrNr=vlc.getNBits(in,3);
   int r=vlc.getBitValArit3(in);
   

   switch(r){
      case 0:iIsB=0;break;
      case 1:iIsB=1;break;
      case 2:iIsB=0;iIsKey=1;break;
   }

   if(!iIsB){
      iIsKRef=vlc.getBit(in);
   }
   iIsVect=vlc.getBitValArit3(in);
   if(!iIsVect){iIsDCT=1;iIsParts=1;}

   //decpos=0;while(decpos<9 && vlc.getBit(in))decpos++;
   //vlc.addXBits(1,decpos+1);

   ADD_TO_VLC(decpos);
   //--decpos=!iIsB;


   sizeofHdr=vlc.getBytePos()+4;

/*

   ADD_TO_VLC(decpos);
   //ADD_TO_VLC(iDrawPos);
  // ADD_TO_VLC(ckunkw);
  // ADD_TO_VLC(ckunkh);
   ADD_TO_VLC(w);
   ADD_TO_VLC(h);
   ADD_TO_VLC(iFrNr);

   //ADD_TO_VLC(iSizeOfChunk);

   ADD_TO_VLC(iIsJpg);
   ADD_TO_VLC(iIsDCT);
   ADD_TO_VLC(iIsWavelet);
   ADD_TO_VLC(iIsKey);
   ADD_TO_VLC(iIsDif);
   ADD_TO_VLC(iSwapRef);
   ADD_TO_VLC(iIsParts);
   ADD_TO_VLC(iIsVect);
   ADD_TO_VLC(iIsZiped);
   //ADD_TO_VLC(iDeblock);
   ADD_TO_VLC(iDecSize);
   ADD_TO_VLC(iIsB);
   ADD_TO_VLC(iIsKRef);
*/
   //TIN4
   //packSZ 16b
   //w h, vlc(w/16,3) vlc(w/32-h/16)+s, avg 8b
   //dif,b,key (1,01,00)
   //if(!b)ref 1b
   //vec 1b//Rep vec 1 01 01
   //frnr 3b
   //flags 1b-Draw,1b-Debl
   //64b

   //pic q 6b aQ
   //bQ 1 cQ 1 dc 1 
   //uv 5 1 1 1

#undef ADD_TO_VLC
   return sizeofHdr;
}

void encSetDrawFlag(unsigned char *p)
{
   //unsigned char  *p=(unsigned char*)pack;
   ///p[10]|=1;
   int bp=32;
   addBit(p,1,bp);
   
}
void encSetDeblockValue(unsigned char *p, int iVal)
{
  // unsigned char  *p=(unsigned char*)pack;
   int bp=33;
   addBit(p,!!iVal,bp);
}

void encSetChunkSize(unsigned char *pack, int iSize)
{
   
   short  *p=(short*)pack;
   p[1]=iSize;
}

#endif

int encodePckHdr(TVHDR_PCK *pack, unsigned char *out){
   CTVLCE e;
   return pack->encode(e,out);
}
int decodePckHdr(unsigned char *p,TVHDR_PCK *pack)
{
   CTVLC d;
   
   return pack->decode(d,p);
}