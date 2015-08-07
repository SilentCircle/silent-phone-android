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

#ifndef _C_T_G722_H
#define _C_T_G722_H

#include "../CCodecBase.h"
class CTG722: public CCodecBase{
   void *estate;
   void *dstate;
   int iSampleRate;
   int read_size;
public:
   CTG722(int iSampleRate);
   virtual ~CTG722();
   int hasPLC(){return 1;}
   virtual  int encodeFrame(short *pIn,unsigned  char *pOut);
   virtual int decodeFrame(unsigned char *pIn, short *pOut);
   //virtual int canDecode(unsigned char *pIn, int iBytesIn, int iBytesInBuf);
   int canDec(unsigned char *pIn, int iBytesIn, int iBytesInBuf);
   inline int canDecode(unsigned char *pIn, int iBytesIn, int iBytesInBuf)
   {
      if(!CCodecBase::canDecode(pIn,iBytesIn,iBytesInBuf))return 0;
      //check modes
      return canDec(pIn, iBytesIn, iBytesInBuf);
      
   }
   int getCurentDecBytesUsed(){return read_size;}
   
   virtual int getTSMult(){return 2;}
};


class CTG722_8khz: public CTG722{
public:
   CTG722_8khz():CTG722(8000){iCodecSRate=8000;}
};
//64kbit
class CTG722_16khz: public CTG722{
public:
   CTG722_16khz():CTG722(16000){iCodecSRate=16000;}
};

#endif //_C_T_G711_H
