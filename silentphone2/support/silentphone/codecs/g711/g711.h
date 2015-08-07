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

#ifndef _C_T_G711_H
#define _C_T_G711_H

int linear2alaw( int pcm_val);
unsigned char linear2ulaw(int pcm_val);
int alaw2linear(unsigned char	a_val);
int ulaw2linear(unsigned char	a_val);
inline void encodeUlaw(short *pIn, unsigned char *pOut, int iShorts)
{
   while(iShorts)
   {
      *pOut=linear2ulaw(*pIn);
      iShorts--;
      pIn++;
      pOut++;
   }
}
inline void decodeUlaw(unsigned char *pIn, short *pOut, int iBytes)
{
   while(iBytes)
   {
      *pOut=ulaw2linear(*pIn);
      iBytes--;
      pIn++;
      pOut++;
   }
}
#include "../CCodecBase.h"
class CTG711Ulaw: public CCodecBase{
public:
   CTG711Ulaw():CCodecBase(1, 2){}
   ~CTG711Ulaw(){}
   int hasPLC(){return 0;}
   virtual inline int encodeFrame(short *pIn,unsigned  char *pOut)
   {
      //encodeUlaw(pIn, (unsigned char *)pOut,  iCodecFrameSizeEnc);
      *pOut=linear2ulaw(*pIn);
      return iCodecFrameSizeEnc; 
   }
   virtual inline int decodeFrame(unsigned char *pIn, short *pOut)
   {
      //decodeUlaw((unsigned char *)pIn,pOut,iCodecFrameSizeDec);
      *pOut=ulaw2linear(*pIn);
      return iCodecFrameSizeDec;
   }

};
class CTG711Alaw: public CCodecBase{
public:
   CTG711Alaw():CCodecBase(1, 2){}
   ~CTG711Alaw(){}
   int hasPLC(){return 0;}
   virtual inline int encodeFrame(short *pIn,unsigned  char *pOut)
   {
      //encodeUlaw(pIn, (unsigned char *)pOut,  iCodecFrameSizeEnc);
      *pOut=linear2alaw(*pIn);
      return iCodecFrameSizeEnc; 
   }
   virtual inline int decodeFrame(unsigned char *pIn, short *pOut)
   {
      //decodeUlaw((unsigned char *)pIn,pOut,iCodecFrameSizeDec);
      *pOut=alaw2linear(*pIn);
      return iCodecFrameSizeDec;
   }

};
#endif //_C_T_G711_H
