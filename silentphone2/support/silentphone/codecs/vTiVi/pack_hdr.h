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

#ifndef _C_T_PACK_VHDR
#define _C_T_PACK_VHDR

#define T_CAN_TEST_V

class CTVLCE;
class CTVLC;

typedef struct{
   enum {
       edecPos,ew,eh,eiFrNr
      ,eiIsJpg,eiIsDCT,eiIsWavelet,eiIsKey,eiIsDif,eiSwapRef,eiIsParts,eiIsVect,eiIsZiped
      ,eiDecSize,eiIsB,eiIsKRef,eLast
   };

   int id;
   int iSizeOfChunk;
   unsigned char vers;
   unsigned char sizeofHdr;
   unsigned char flagx;//1 canDraw, else deblock

   int iCanDraw;//from flagx
   int iDeblock;//from flagx
   
   //vlc
   char decpos;
   unsigned int ckunkw,ckunkh;
   short w,h;
   short iFrNr;

   unsigned int iIsJpg;
   unsigned int iIsDCT;
   unsigned int iIsWavelet;
   unsigned int iIsKey;
   unsigned int iIsDif;
   unsigned int iSwapRef;
   unsigned int iIsParts;
   unsigned int iIsVect;
   unsigned int iIsZiped;
   unsigned int iDecSize;
   unsigned int iIsB;
   unsigned int iIsKRef;

  // int iBitPosFlags;

   

   //getLenFromVlc
   int encode(CTVLCE &vlc, unsigned char *out);
   int decode(CTVLC &vlc, unsigned char *in);

   unsigned char *pStart;
}TVHDR_PCK;

//


int encodePckHdr(TVHDR_PCK *pack, unsigned char *out);
void encSetChunkSize(unsigned char  *pack, int iSize);
int decodePckHdr(unsigned char *p,TVHDR_PCK *pack);
void encSetDrawFlag(unsigned char  *pack);
void encSetDeblockValue(unsigned char  *pack, int iVal);


#endif 
