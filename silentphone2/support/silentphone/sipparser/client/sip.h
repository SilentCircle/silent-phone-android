/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2017, Silent Circle, LLC.  All rights reserved.

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

#ifndef DEAFULT_SIP_PORT
#define DEAFULT_SIP_PORT 5060
#include "../../tiviengine/main.h"//?????


#define DONT_DROP 0
#define DROP        -1
#define SIP_OK      0
#define UNKNOWN     -3


#define  IS_NOT_ALL_REQ_SIP_FIELDS(s) \
   ((s).dstrCallID.uiLen==0 ||\
    (s).hdrCSeq.dstrFullRow.uiLen==0 ||\
    (s).hdrFrom.sipUri.dstrSipAddr.uiLen==0 ||\
    (s).hdrTo.sipUri.dstrSipAddr.uiLen==0 || \
    (s).hldVia.x[0].dstrViaAddr.uiLen==0)





#define FALSE 0
#define TRUE 1

/*
#define MSG_BUFFER_TAIL 8
#define MSG_BUFFER_SIZE   1024*2-MSG_BUFFER_TAIL
*/

#define MAX_TOKEN_COUNT   (MSG_BUFFER_SIZE/2+2) //k,k,g;h
#define MAX_CONTENT_LEN   (MSG_BUFFER_SIZE/2)
#define M_COOKIE          "z9hG4bK"   //branch=z9hG4bK+....
#define MIN_TOKEN_COUNT   20 //--------------jaizdomaa pareizaa veertiiba
#define MIN_SIP_MSG_LEN   120 //--------------jaizdomaa pareizaa veertiiba

#define ARRAY_SIZE         10


#define VIAS       10

#define SIP_INTERACTION_RESPONSE  1
#define SIP_INTERACTION_REQUEST   2
#define SIP_INTERACTION_P_AUTHOR  4
#define SIP_INTERACTION_WWW_AUTHEN  8
#define SIP_CT_RTP_HOLD    16
#define SIP_CT_TEXT   32
#define SIP_CT_SDP    64
#define SIP_CT_SDP_FT    128

// METHOD
#define METHOD_INVITE       1
#define METHOD_ACK          2
#define METHOD_OPTIONS      4
#define METHOD_BYE          8
#define METHOD_CANCEL      16
#define METHOD_REGISTER    32
#define METHOD_MESSAGE     64
#define METHOD_INFO       128
#define METHOD_UPDATE     256
#define METHOD_REFER      512
#define METHOD_NOTIFY    1024
#define METHOD_SUBSCRIBE 2048
#define METHOD_PUBLISH   4096

// CONTENT TYPE
#define CONTENT_TYPE_TEXT         1
#define CONTENT_TYPE_IMAGE        2
#define CONTENT_TYPE_AUDIO        3
#define CONTENT_TYPE_VIDEO        4
#define CONTENT_TYPE_APPLICATION  5
#define CONTENT_TYPE_MESSAGE      6
#define CONTENT_TYPE_MULTIPART    7


// PRIORITY
#define PRIORITY_NON_URGENT   1
#define PRIORITY_NORMAL       2
#define PRIORITY_URGENT       3
#define PRIORITY_EMERGENCY    4



typedef struct _DSTR{
   unsigned int uiLen;
   char         *strVal;
   union{
      unsigned int uiVal;  // s_uint;  //
      float        fVal;   // s_float; //
   };
   int          iLast;  // l;       // iL;
   int          iFirst; // f;       // iF;
} DSTR;


typedef struct _SIP_URI{// TODO renameit to URI
   
   DSTR dstrUriScheme;
   DSTR dstrSipAddr;
//   DSTR dstrTransport;
//   DSTR dstrUser;
   //DSTR dstrPwd;

   
   DSTR dstrHost;
   DSTR dstrUserName;
   //DSTR dstrTTL;
   struct{
      int iFound;
      DSTR dstrAddr;
      DSTR dstrPort;
   }maddr;
  // DSTR dstrLR;
   DSTR dstrPort;//New 
   int  iMethodFlag;
   
   DSTR dstrX_SC_DevID;
} SIP_URI;

//-------
struct HDR_SUPPORT{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrName;
};

typedef struct{
   struct HDR_SUPPORT    x[ARRAY_SIZE+1];
   unsigned int          uiCount;
}HLD_SUP;
//----------
struct HDR_PROX_REQUIRE{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrName;
};

struct HOLDER_PROX_REQUIRE{
   struct HDR_PROX_REQUIRE   x[ARRAY_SIZE+1];
   unsigned int              uiCount;
};


struct HDR_ROUTE{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrName;
   SIP_URI sipUri;
};

typedef struct {
   struct HDR_ROUTE      x[ARRAY_SIZE+1];
   unsigned int          uiCount;
}HLD_ROUTE;


struct HDR_CONT_TYPE{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrSubType;
   DSTR dstrAttrib;
   DSTR dstrValue;
   unsigned int uiTypeID; //mTypeID;
};

struct HDR_CALL_INFO{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrIconAddr;
   DSTR dstrInfoAddr;
   DSTR dstrCardAddr;
   DSTR dstrNotSplit; 
   unsigned int iFlag;    // should be ui
};  

typedef struct {
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrName;
   DSTR dstrTag;
   SIP_URI sipUri;   
}HDR_TO_FROM;

struct HOLDER_P_A_ID{
   HDR_TO_FROM   x[ARRAY_SIZE+1];
   unsigned int          uiCount;
};

struct HDR_CONTACT{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrName;
   DSTR dstrQ;           // ???
   DSTR dstrExpires;
   DSTR dstrMailAddr;
   SIP_URI      sipUri;
   unsigned int iFlag;

};
struct HOLDER_CONTACT{
   struct HDR_CONTACT   x[ARRAY_SIZE+1];
   unsigned int          uiCount;
};

struct HDR_CSEQ{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrID;
   unsigned int  uiMethodID; //   INVITE ACK BYE ...
};

typedef struct {
   DSTR dstrFullRow; //rindas saakums un garums  ieskaitot hdr_name
   DSTR dstrAlgo;
   DSTR dstrAuthParm;
   DSTR dstrCnonce;
   DSTR dstrDomain; 
   DSTR dstrNonceCount;
   DSTR dstrNonce;
   DSTR dstrOpaque;
   DSTR dstrQOP;
   DSTR dstrRealm;
   DSTR dstrResponse;
   DSTR dstrURI;
   DSTR dstrUsername; 
   int  iStale;
   int  iFlag;
}HDR_AUT;

struct HDR_PORTA_BILL{
   DSTR dstrFullRow; //rindas saakums un garums  ieskaitot hdr_name
   DSTR dstrCurrency;
   DSTR dstrValue;
};

struct HDR_VIA{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrSipVer;  // "SIP"+"/"+#+"."+#
   DSTR dstrTransport;
   DSTR dstrViaAddr;
   DSTR dstrPort;
   DSTR dstrBranch;     //flag 1  branch=z9hG4bK......
   DSTR dstrTTL;     //flag 1  branch=z9hG4bK......
   DSTR dstrMAddr;      //flag 4  maddr=x.x.x.x
   DSTR dstrRecived;    //flag 8  recived=x.x.x.x or recived= (ipv6address)
   DSTR dstrExtension;  //flag 16 generic-param
   DSTR dstrRPort;
   ///int  iViaCount;
   int  iFlags;
};

struct HOLDER_VIA{
   struct HDR_VIA   x[VIAS+1];
   unsigned int          uiCount;
};

struct HOLDER_UNKNOWN_HDR{
   DSTR          x[ARRAY_SIZE+1];
   unsigned int  uiRowID;
   unsigned int  uiCount;
};

struct HDR_X_SC_MSG_META{
   DSTR dstrFullRow;
   DSTR dstr64bitID;
};

struct HDR_X_SC_DISPLAY_ALIAS{
   DSTR dstrFullRow;
   DSTR dstrAlias;
   DSTR dstrType;
};

struct SIP_HDR{
   DSTR dstrFullRow; //rindas saakums un garums ieskaitot hdr_name
   DSTR dstrSipVer;   // "SIP"+"/"+#+"."+#
   DSTR dstrStatusCode;  //  200 or ...
   DSTR dstrReasonPhrase;//  OK or .... 
   SIP_URI sipUri;
   unsigned int  iFlag;
   unsigned int  uiMethodID;

   //TODO if unknown meth
};

//rfc3326

#define CALL_COMPETED_ELSWHERE "Call completed elsewhere"

struct HDR_REASON{
   //Reason: SIP ;cause=200 ;text="Call completed elsewhere"
   //Reason: Q.850 ;cause=16 ;text="Terminated"
   DSTR proto; //SIP or Q.850
   DSTR cause;
   DSTR text;
};

typedef struct _SIP_MSG
{
   DSTR dstrAcceptEnc;
   DSTR dstrAlertInfo;
   DSTR dstrCallID;
   DSTR dstrCallRate;
   DSTR dstrContLen;
   DSTR dstrExpires;
   DSTR dstrMaxForw;
   DSTR dstrPriority;
   DSTR dstrEvent;
   DSTR dstrContEncoding;
   
   struct SIP_HDR             sipHdr;
   struct HDR_CALL_INFO       hdrCallInfo;
   struct HDR_CONT_TYPE       hdrContType;
   struct HDR_CSEQ            hdrCSeq;
   struct HDR_PORTA_BILL      hdrPortaBill;
   
   struct HDR_REASON hdrReason;
 
   HDR_TO_FROM       hdrFrom;
   HDR_TO_FROM       hdrTo;
 
   //TODO union
   //UINT unionAuthFlag;
   //union{
   HDR_AUT                    hdrAuthoriz;
   HDR_AUT                    hdrProxyAuthor;
   HDR_AUT                    hdrProxyAuthen;
   HDR_AUT                    hdrWWWAuth;
   //};

   struct HOLDER_VIA          hldVia; // VIAS_COUNT
   struct HOLDER_CONTACT      hldContact;
   
   struct HOLDER_P_A_ID       hldP_Asserted_id;
   HLD_ROUTE                  hldAssociated_URI;
   
   struct HDR_X_SC_MSG_META hdrXSCMsgMeta;
   struct HDR_X_SC_DISPLAY_ALIAS hdrXSCDisplayAlias;
   
   
   HLD_ROUTE  hldRoute;
   HLD_ROUTE  hldRecRoute;
   struct HOLDER_PROX_REQUIRE hldProxReq;
   
   HLD_SUP  hldRequire;
   HLD_SUP  hldSupprted;
   HLD_SUP  hldUnSupp;
   struct HOLDER_UNKNOWN_HDR  hldUnknownHdr;
   
   unsigned int               uiAllowHdrMethodFlags;
   unsigned int               uiOffset;
   char                       rawDataBuffer[MSG_BUFFER_SIZE+MSG_BUFFER_TAIL_SIZE];
   unsigned int               uiBytesParsed;
//   unsigned int               uiSipMsgRecIPNF;
  // unsigned int               uiSipMsgRecPortNF;
   ADDR addrSipMsgRec;
   //char *                     pszContentStart;
} SIP_MSG;


// functions from sip_parse
//int parseSip(SIP_MSG *sMsg);

//extern SIP_MSG gsSIPMsg;
//extern SIP_MSG * gspSIPMsg;
// functions from sip_create

#endif

/*
int printError(char *errStr,int drop);
//int parseSDP();

int parseFirstLine();

int parseAccept();
int parseAcceptEncoding();
int parseAcceptLanguage();
int parseAlertInfo();
int parseAllow();
int parseAuthenticationInfo();
int parseAuthorization();


int parseCallID();
int parseCallInfo();
int parseContact();
int parseContentDisposition();
int parseContentEncoding();
int parseContentLanguage();
int parseContentLength();
int parseContentType();
int parseCSeq();

int parseDate();
int parseErrorInfo();
int parseExpires();
int parseFrom();
int parseInReplyTo();
int parseMaxForwards();
int parseMIMEVersion();
int parseMinExpires();
int parseOrganization();
int parsePriority();
int parseProxyAuthenticate();
int parseProxyAuthorization();
int parseProxyRequire();
int parseRequire();
int parseRetryAfter();
int parseRoute();
int parseRecordRoute();
int parseReplayTo();
int parseServer();

int parseSubject();


int parseSupported();
int parseTimestamp();

int parseTo();
int parseToOld();
int parseUnsupported();
int parseUserAgent();

int parseVia();
int parseWarning();
int parseWWWAuthenticate();

int parseUnknown();
*/

//int splitLine();

