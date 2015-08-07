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


#ifndef RTP_CN_PT
#define RTP_CN_PT    13

#define KOSMOSS_DATA_ENC 18
#define KOSMOSS_DATA_DEC 10
#define PT_KOSMOSS      120

#define S_R_RTP_BUF_SIZE (2048*2-1)

typedef struct {
   int len;
   unsigned char *s;
   int iTest_lin_Rem;
}STR_RTP;

typedef struct _RTP_PACKET_S{
   unsigned int  csrcCount:4;    // 4 bit
   unsigned int  padding:1;      // 1 bit
   unsigned int  extension:1;    // 1 bit
   unsigned int  version:2;      // 2 bit
   unsigned int  pt:7;           // 7 bit
   unsigned int  marker:1;       // 1 bit
   unsigned int  seqNr:16;       //16 bit
  
   unsigned int  ts;           //32 bit
   unsigned int  ssrc;         //32 bit
   unsigned int  csrc[16];     //32 bit*16
   unsigned int  hdrLen;
   STR_RTP           data;
   STR_RTP           allPack;
   unsigned char          dataBuf[S_R_RTP_BUF_SIZE+1];
}RTP_PACKET_S;

typedef struct _RTP_PACKET_P{
   unsigned int  csrcCount:4;    // 4 bit
   unsigned int  padding:1;      // 1 bit
   unsigned int  extension:1;    // 1 bit
   unsigned int  version:2;      // 2 bit
   unsigned int  pt:7;           // 7 bit
   unsigned int  marker:1;       // 1 bit
   unsigned int  seqNr:16;       //16 bit
  
   unsigned int  ts;           //32 bit
   unsigned int  ssrc;         //32 bit
   unsigned int  csrc[16];     //32 bit*16
   unsigned int  hdrLen;
   STR_RTP           data;
   STR_RTP           allPack;
   unsigned char  dataBuf[S_R_RTP_BUF_SIZE+1];
  // unsigned char          pcmDataHolder[BLOCK_SIZE];
}RTP_PACKET_P;
int parseRTP(RTP_PACKET_P *recPacket, char *p);
#endif

