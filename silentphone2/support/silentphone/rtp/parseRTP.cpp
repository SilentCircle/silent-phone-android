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

//#include "main.h"
//#include "main.h"
#include "../utils/CdtMacros.h"
#include "rtp.h"

#define u_int32 unsigned int

typedef struct {
   unsigned int cc:4;        /* CSRC count */
   unsigned int x:1;         /* header extension flag */
   unsigned int p:1;         /* padding flag */
   unsigned int version:2;   /* protocol version */
   unsigned int pt:7;        /* payload type */
   unsigned int m:1;         /* marker bit */
   unsigned int seq:16;      /* sequence number */
   u_int32 ts;               /* timestamp */
   u_int32 ssrc;             /* synchronization source */
  // u_int32 csrc[1];          /* optional CSRC list */
} rtp_hdr_t;

int getRTPSSRC(char *pack, int iLen, unsigned int *resp){
   if(iLen<12 || !resp)return -1;
   
   unsigned int c = *(unsigned int*)(pack+8);
   SWAP_INT(c)
   *resp=c;
   
   return 0;
}

int isSameRtpSSRC(char *pack, int iLen, unsigned int ssrc){
   unsigned int res=0;
   if(getRTPSSRC(pack,iLen,&res)<0)return 0;
   return ssrc == res;
}

int parseRTP(RTP_PACKET_P *recPacket, char *p)
{
   unsigned int c;

   unsigned char * buf=(unsigned char *)p;//=recPacket->dataBuf;

   if(!buf)
      buf=recPacket->dataBuf;
   recPacket->allPack.s=buf;

   c=*(unsigned int *)buf;

   recPacket->csrcCount=c & 0xf;
   c>>=4;
   recPacket->hdrLen=12+recPacket->csrcCount*4;
   recPacket->data.s=buf+recPacket->hdrLen;
   recPacket->data.len=recPacket->allPack.len-(int)recPacket->hdrLen;
   if(recPacket->data.len<0)return -1;
   
   recPacket->padding=c&1;
   c>>=1;
   recPacket->extension=c&1;
   c>>=1;
   recPacket->version=c & 3;
   c>>=2;
   recPacket->pt=c & 0x7f;
   c>>=7;
   recPacket->marker=c & 1;
   c>>=1;
   recPacket->seqNr=((c & 0xff)<<8)+(c>>8);

   buf+=4;
   c=*(unsigned int *)buf;
   SWAP_INT(c)
   recPacket->ts=c;

   buf+=4;
   c=*(unsigned int *)buf;
   SWAP_INT(c)
   recPacket->ssrc=c;
   
   //#define RTP_VALUES_DEBUG1
#ifdef RTP_VALUES_DEBUG1
   printf("Version     =%u\n",recPacket->version);
   printf("Padding     =%u\n",recPacket->padding);
   printf("Extension   =%u\n",recPacket->extension);
   printf("CSRC Count  =%u\n",recPacket->csrcCount);
   printf("Marker      =%u\n",recPacket->marker);
   printf("Payload Type=%u\n",recPacket->pt);
   printf("Seq.Nr      =%u\n",recPacket->seqNr);
   printf("Time Stamp  =%u\n",recPacket->ts);
   printf("SSRC        =%u\n",recPacket->ssrc);
   printf("Data     len=%u\n",recPacket->data.len);
   
   for (i=0;i<recPacket->data.len;i++){
      data=(unsigned char)(recPacket->data.s[i]);
      //   putchar(abs(packet.data.s[i]));
      printf("%x  ",data);
   }
   putchar(10);
 //  exit(-1);
   
#endif
#if 0
   
   rtp_hdr_t rtpx=*(rtp_hdr_t *)p;
   
   
   SWAP_INT(rtpx.ts)
   SWAP_INT(rtpx.ssrc)
   short s=rtpx.seq;
   SWAP_SHORT(s);rtpx.seq=s;
   
   puts("----");
   
   printf("n v%d pt%d seq%d  m%d ssrc%u ts%u\n",rtpx.version,        rtpx.pt,       rtpx.seq,                  rtpx.m,            rtpx.ssrc,               rtpx.ts);
   printf("o v%d pt%d seq%d  m%d ssrc%u ts%u\n",recPacket->version, recPacket->pt,recPacket->seqNr, recPacket->marker,recPacket->ssrc, recPacket->ts);
#endif

   return 0;
}

