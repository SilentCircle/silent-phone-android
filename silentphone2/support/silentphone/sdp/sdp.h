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

#ifndef _SDP_H_MY
#define _SDP_H_MY
//#define UINT unsigned int

struct CODECS{
   //unsigned int          uiID;
   //unsigned int          eType;//1 audio 2 video
   unsigned char          uiID;//:7 jo 0..127
   unsigned char          eType;//1 audio 2 video
   unsigned short         uiMediaID;
   //unsigned int type audio or video or...
   //STR_SDP_PARSE_ID name;
   //DSTR     name;
  // UINT          uiSps;
  // UINT          uiChanels;
  // UINT          type; //audio or video or...
};

typedef struct {
   enum E_MEDIA_TYPE{eNone, eAudio=1, eVideo=2, eT38=4,eFileTransf=8, eMaxMediaCnt=4};
   
   
   E_MEDIA_TYPE iLastMediaType;//tmp , for parser sdp only
   unsigned int uiPrevIp;//tmp
 
   
   unsigned int ipOrigin;
   struct{
      E_MEDIA_TYPE eType;//1 audio, 2 video
      unsigned int port;
      unsigned int ipConnect;
      //TODO atribs
      unsigned int uiSSRC;
   }media[eMaxMediaCnt];
   union{
      struct CODECS    codec[128];
      char szFileName[256];
      //TODO T38 atribs
   }u;
   struct{
      char *pSessName;
      int iLen;
   }s;//s=
   struct{
      enum {eMaxAttribCnt=64};
      struct{
         char *p;
         int iLen;
         E_MEDIA_TYPE iMediaType;
      }n[eMaxAttribCnt];
      int iAttribCnt;
   }attribs;
   
   enum{eMaxCodecs=40};//max must be 7bit signed, 63 values

   int iMediaCnt:8;
   int iParsed:1;//TODO remove not used
   int codecCount:7;// will suport max 63 codecs
   
}SDP;

int findMediaId(SDP *sdp, int eType, int n);
int hasAttrib(SDP &sdp, const char *attr, int iType);
int parseSDP(SDP *psdp, char *buf, int iLen);

/*
struct CODECS{
   unsigned int          uiID;
   //STR_SDP_PARSE_ID name;
   //DSTR     name;
  // UINT          uiSps;
  // UINT          uiChanels;
  // UINT          type; //audio or video or...
};

typedef struct {
   unsigned int port;
   unsigned int ipOriginAudio;
   unsigned int ipConnectAudio;
   unsigned int iParsed;
   unsigned int codecCount;
   struct CODECS    codec[128];

   unsigned int uiSSRC;
}SDP;
*/
/*

#define HDRS     15
#define MEMBERS  7
#define MAX_DEEP 1
#define SDP_VERSION        0 //v=
#define SDP_ORIGIN         1 //o=
#define SDP_S_NAME         2 //s=
#define SDP_S_M_INFO       3 //i=
#define SDP_URI            4 //u=
#define SDP_EMAIL_ADDR     5 //e=
#define SDP_PHONE_NUM      6 //p=
#define SDP_CON_DATA       7 //c=
#define SDP_BANDWIDTH      8 //b=
#define SDP_TIME           9 //t=
#define SDP_REPEATE_TIME   10 //r=
#define SDP_TIME_ZONE      11 //z=
#define SDP_ENCR_KEYS      12 //k=
#define SDP_MEDIA_ANNOUN   13 //m=
#define SDP_ATRIB          14 //a=

#define SDP_TTL       0
#define SDP_DIGIT1    1 
#define SDP_DIGIT2    2 
#define SDP_STRING1   3 
#define SDP_STRING2   4 
#define SDP_IPADDR    5 
//#define SDP_TYPE      6
#define SDP_PORT      6

#define SDP_ID_USER       1
#define SDP_ID_IP4        2
#define SDP_ID_IP6        3
#define SDP_ID_PROTOCOL   4
#define SDP_ID_PORT       5
#define SDP_ID_AUDIO      6
#define SDP_ID_VIDEO      7
#define SDP_ID_RTPAVP     8
#define SDP_ID_MT         9
#define SDP_ID_DATA      10
#define SDP_ID_UDP       11


struct CODECS{
   UINT          uiID;
   //STR_SDP_PARSE_ID name;
   DSTR     name;
   UINT          uiSps;
   UINT          uiChanels;
   UINT          type; //audio or video or...
};

typedef struct {
   DSTR           sdp[HDRS][MEMBERS][MAX_DEEP];
   int                deep[HDRS];
   struct CODECS      codec[128];
   UINT                codecCount;
   int iParsed;
   char buf[2048];
}SDP;
*/
//extern SDP recSDP;


#endif

