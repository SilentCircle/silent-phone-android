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

#ifndef _TIVI_PH_MAIN_H
#define _TIVI_PH_MAIN_H



#define L_VERSION "2.2"
#ifdef __SYMBIAN32__
 #define L_SYMB_VERS L_VERSION 
#endif


#define t_ph_tick unsigned long long



#ifndef DONT_USE_VIDEO
#define USE_JPG_VID
#endif


typedef struct _STR{
   unsigned int len;
   unsigned char * s;
}STR;

typedef struct  {
   char strVal[16];
   unsigned int uiLen;
}STR_16;

typedef struct  {
   char strVal[32];
   unsigned int uiLen;
}STR_32;

typedef struct  {
   char strVal[64];
   unsigned int uiLen;
}STR_64;

typedef struct  {
   char strVal[128];
   unsigned int uiLen;
}STR_128;

typedef struct  {
   char strVal[256];
   unsigned int uiLen;
}STR_256;


template<int eMaxSize>
class STR_T{
public:
   STR_T(){uiLen=0;}
   unsigned int uiLen;
   char strVal[eMaxSize];
   inline int getMaxSize(){return eMaxSize-1;}
};


#include "../baseclasses/ADDR.h"

#ifdef __SYMBIAN32__

   #include <e32math.h>
  #ifndef Sleep
   #define Sleep(_a_) User::After(1000*(_a_))
   #define GetTickCount() User::FastCounter()
  #endif
#endif

typedef struct{
   ADDR addr;
   char *pUser;
   int iUserLen;
   int iPriority;
   void clearAll(){
      clear();
      addr.clear();
      iPriority=0;
      
   }
   void clear()
   {
      iUserLen=0;
      pUser=NULL;
   }
}URI;


#define MSG_BUFFER_TAIL       "\0\0\r\n\r\n\r\n"
#define MSG_BUFFER_TAIL_SIZE  (sizeof(MSG_BUFFER_TAIL)-1)
#define MSG_BUFFER_SIZE  (4096-MSG_BUFFER_TAIL_SIZE)



#define MAX_HOST_NAME_LEN 256

//DATA_SIZE - must be bigger than receive SIP buf
#define DATA_SIZE   (MSG_BUFFER_SIZE+1024)


#define CPY_DSTR(A,B,M) {if((B).uiLen<(M) && (B).strVal){\
   memcpy((A).strVal,(B).strVal,(B).uiLen);\
   (A).strVal[(B).uiLen]=0;\
   (A).uiLen=(B).uiLen;}else (A).uiLen=0;}


#define D_STR(c)    (int)(c).uiLen,(c).strVal

#define ADD_0_STR(D,L,S){\
   int LL=0;\
   while(S[LL]){D[L+LL]=S[LL];LL++;}L+=LL;D[L]=0;}

#define ADD_CHAR(D,L,C) {D[L]=C;L++;}

#define ADD_STR(a,b,c) {memcpy((a)+(b),(c),sizeof((c)));(b)+=sizeof((c))-1;}

#define ADD_L_STR(a,b,c,d) {memcpy((a)+(b),(c),(d));(b)+=(d);(a)[(b)]=0;}

#define ADD_DSTRCRLF(a,b,c) \
   {memcpy((a)+(b),(c).strVal,(c).uiLen);\
   (b)+=(c).uiLen+2;\
   (a)[(b)-2]=13;(a)[(b)-1]=10;(a)[(b)]=0;}

#define ADD_DSTR(a,b,c) \
   {memcpy((a)+(b),(c).strVal,(c).uiLen);\
   (b)+=(c).uiLen;\
      (a)[(b)]=0;}

#define ADD_CRLF(a,b) {(a)[(b)]=13;(a)[(b)+1]=10;(b)+=2;(a)[(b)]=0;}

//#define DEBUG_NEEDED

#ifdef DEBUG_NEEDED

   #define DEBUG_T(a,b)
#else
    #define DEBUG_T(a,b)    {};
#endif

#ifdef __SYMBIAN32__
 #define USER_AGENT "User-Agent: TiVi-Symbian/"L_VERSION"\r\n"
#elif _WIN32_WCE
 #define USER_AGENT "User-Agent: TiVi-PDA/"L_VERSION"\r\n"
#else
 #define USER_AGENT "User-Agent: TiVi-PC/"L_VERSION"\r\n"
#endif


#define F_CMP(_P,_V) (_P[0]==_V[0] && _P[1]==_V[1] && strcmp(_P,_V)==0)
#define F_CMPN(_P,_V,_N) ((_N)+1==sizeof(_V) && _P[0]==_V[0] && _P[(_N)>>1]==_V[(_N)>>1] && strncmp(_P,_V,sizeof(_V)-1)==0)



#include "t_cfg.h"

#endif
