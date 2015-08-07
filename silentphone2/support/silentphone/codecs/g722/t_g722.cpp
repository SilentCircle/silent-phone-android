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

#include <stdio.h>
#include "config.h"

#include "inttypes.h"

#include "spandsp/telephony.h"

#include "spandsp/g722.h"
#include "t_g722.h"

#if 0
SPAN_DECLARE(g722_encode_state_t *) g722_encode_init(g722_encode_state_t *s, int rate, int options);
SPAN_DECLARE(int) g722_encode_release(g722_encode_state_t *s);
SPAN_DECLARE(int) g722_encode_free(g722_encode_state_t *s);
SPAN_DECLARE(int) g722_encode(g722_encode_state_t *s, uint8_t g722_data[], const int16_t amp[], int len);
SPAN_DECLARE(g722_decode_state_t *) g722_decode_init(g722_decode_state_t *s, int rate, int options);
SPAN_DECLARE(int) g722_decode_release(g722_decode_state_t *s);
SPAN_DECLARE(int) g722_decode_free(g722_decode_state_t *s);
SPAN_DECLARE(int) g722_decode(g722_decode_state_t *s, int16_t amp[], const uint8_t g722_data[], int len);

#endif

#define BPS 64000
#define BYTES_PACK_SIZE (BPS/8/50)


int CTG722::canDec(unsigned char *pIn, int iBytesIn, int iBytesInBuf){
   if(iBytesIn*4>iBytesInBuf)return 0;
   if(!(iBytesIn%(64000/8/50))){read_size=64000/8/50;return 1;}
   if(!(iBytesIn%(56000/8/50))){read_size=56000/8/50;return 1;}
   if(!(iBytesIn%(48000/8/50))){read_size=48000/8/50;return 1;}
   printf("[dec G722  err %d %d]\n",iBytesIn,iBytesInBuf);
   //164 zz
   return 0;
}

int CTG722::encodeFrame(short *pIn,unsigned  char *pOut)
{
   if(!estate){
      estate=g722_encode_init(NULL,BPS,iSampleRate==8000?G722_SAMPLE_RATE_8000|G722_PACKED:G722_PACKED);
      if(!estate)return 0;
   }

 //  for(int i=0;i<iCodecFrameSizeDec/2;i++)pIn[i]>>=1;
   int r=g722_encode((g722_encode_state_t*)estate,pOut,pIn,iCodecFrameSizeDec/2);
//printf(" re=%d %d\n",r,iCodecFrameSizeEnc);
//iCodecFrameSizeEnc=r;
	return r; 
}
int CTG722::decodeFrame(unsigned char *pIn, short *pOut)
{
   if(!dstate){
      dstate=g722_decode_init(NULL,read_size?(read_size*8*50):BPS,iSampleRate==8000?G722_SAMPLE_RATE_8000|G722_PACKED:G722_PACKED);
      if(!dstate)return 0;
   }
   int r;
   if(!read_size)
      r=g722_decode((g722_decode_state_t*)dstate,pOut,pIn,BYTES_PACK_SIZE);
   else 
      r=g722_decode((g722_decode_state_t*)dstate,pOut,pIn,read_size);

//printf(" rd=%d %d\n",r,iCodecFrameSizeDec);
   iCodecFrameSizeDec=r*2;
   return iCodecFrameSizeDec;
}

CTG722::CTG722(int iSampleRate)
   :CCodecBase(0, 320*iSampleRate/8000),
   iSampleRate(iSampleRate){
	estate=NULL;
   dstate=NULL;
   read_size=0;
      puts("CTG722");
}

CTG722::~CTG722(){
   if(estate){
      g722_encode_release((g722_encode_state_t*)estate);
      g722_encode_free((g722_encode_state_t*)estate);
      estate=NULL;
   }
   if(dstate){
      g722_decode_release((g722_decode_state_t*)dstate);
      g722_decode_free((g722_decode_state_t*)dstate);
      dstate=NULL;
   }
}

