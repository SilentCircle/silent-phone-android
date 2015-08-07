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

#ifndef _CDT_MACROS_H
#define _CDT_MACROS_H


#define CMP1(sp,s) ((*(unsigned int*)(sp))& 0x000000df)==((*(unsigned int*)(s))& 0x000000ff)
#define CMP2(sp,s) ((*(unsigned int*)(sp))& 0x0000dfdf)==((*(unsigned int*)(s))& 0x0000ffff)
#define CMP3(sp,s) ((*(unsigned int*)(sp))& 0x00dfdfdf)==((*(unsigned int*)(s))& 0x00ffffff)
#define CMP4(sp,s) ((*(unsigned int*)(sp))& 0xdfdfdfdf)== (*(unsigned int*)(s))

#define CMP1n(sp,s,n) ((*(unsigned int*)((sp)+(n)))& 0x000000df)==((*(unsigned int*)((s)+(n)))& 0x000000ff)
#define CMP2n(sp,s,n) ((*(unsigned int*)((sp)+(n)))& 0x0000dfdf)==((*(unsigned int*)((s)+(n)))& 0x0000ffff)
#define CMP3n(sp,s,n) ((*(unsigned int*)((sp)+(n)))& 0x00dfdfdf)==((*(unsigned int*)((s)+(n)))& 0x00ffffff)
#define CMP4n(sp,s,n) ((*(unsigned int*)((sp)+(n)))& 0xdfdfdfdf)== (*(unsigned int*)((s)+(n)))

#define CMP5(sp,s) ((CMP4n((sp),(s),0) )&& (CMP1n((sp),(s),4)))
#define CMP6(sp,s) ((CMP4n((sp),(s),0) )&& (CMP2n((sp),(s),4)))
#define CMP7(sp,s) ((CMP4n((sp),(s),0) )&& (CMP3n((sp),(s),4)))
#define CMP8(sp,s) ((CMP4n((sp),(s),0) )&& (CMP4n((sp),(s),4)))

#define  CMP9(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP1n((sp),(s),8)))
#define CMP10(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP2n((sp),(s),8)))
#define CMP11(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP3n((sp),(s),8)))
#define CMP12(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8)))
   
#define  CMP13(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP1n((sp),(s),12)))
#define  CMP14(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP2n((sp),(s),12)))
#define  CMP15(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP3n((sp),(s),12)))
#define  CMP16(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12)))

#define  CMP17(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP1n((sp),(s),16)))
#define  CMP18(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP2n((sp),(s),16)))
#define  CMP19(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP3n((sp),(s),16)))
#define  CMP20(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP4n((sp),(s),16)))

#define  CMP21(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP4n((sp),(s),16) )&&(CMP1n((sp),(s),20)))
#define  CMP22(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP4n((sp),(s),16) )&&(CMP2n((sp),(s),20)))
#define  CMP23(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP4n((sp),(s),16) )&&(CMP3n((sp),(s),20)))
#define  CMP24(sp,s) ((CMP4n((sp),(s),0) )&&(CMP4n((sp),(s),4) )&&(CMP4n((sp),(s),8) )&&(CMP4n((sp),(s),12) )&&(CMP4n((sp),(s),16) )&&(CMP4n((sp),(s),20)))
   

#if defined (__SYMBIAN32__) || defined(ARM)  || defined(ANDROID_NDK) || defined(_WIN32_WCE)
   #define CMP(strr,ss,l) (l==(strr).uiLen && cmpmy((char *)(strr).strVal, (char *)(ss), l))
   #define  CMP_XML(strr,ss,l)  (((l)==(strr).len) && cmpmy((strr).s,(ss),l))
#else
   #define  CMP(strr,ss,l)  (((l)==(strr).uiLen) && CMP##l((strr).strVal,(ss)))
   #define  CMP_XML(strr,ss,l)  (((l)==(strr).len) && CMP##l((strr).s,(ss)))
#endif

#define  CMP_S(strr,str_len,ss,l)  (((l)==(str_len)) && CMP##l((strr),(ss)))

#define SWAP_INT(E){unsigned char  iTmpSwaped[4];\
      iTmpSwaped[0]=((unsigned char *)&(E))[3];\
      iTmpSwaped[1]=((unsigned char *)&(E))[2];\
      iTmpSwaped[2]=((unsigned char *)&(E))[1];\
      iTmpSwaped[3]=((unsigned char *)&(E))[0];\
         (E)=*(int*)iTmpSwaped;}

#define SWAP_SHORT(E){unsigned char  iTmpSwaped[2];\
      iTmpSwaped[0]=((unsigned char *)&(E))[1];\
      iTmpSwaped[1]=((unsigned char *)&(E))[0];\
         (E)=*(short*)iTmpSwaped;}


//conver char to int
#define STR_TO_I(R,P)\
      { char * TMP=(P);(R)=0;\
      while(*TMP==' ' || *TMP=='\t')TMP++;\
      if(*TMP=='-'){TMP++;\
         while(*TMP>='0' && *TMP<='9')\
            {(R)=(R)*10-(*TMP-'0');TMP++;}}\
       else{\
         while(*TMP>='0' && *TMP<='9')\
            {(R)=(R)*10+(*TMP-'0');TMP++;}}}

//conver char to int and update src pointer

#define STR_TO_I_CP(RES,P)\
      {(RES)^=(RES);\
      while(*(P)==' ' || *(P)=='\t')(P)++;\
      if(*(P)=='-'){(P)++;\
         while(*(P)>='0' && *(P)<='9')\
         {(RES)=(RES)*10-(*(P)-'0');(P)++;}}\
      else{\
         while(*(P)>='0' && *(P)<='9')\
         {(RES)=(RES)*10+(*(P)-'0');(P)++;}}}


#define D_P(E) printf("line=%u,"#E"=%u\n",__LINE__,E);

#define D_ROW(EE) printf("line=%u,"#EE">>\n",__LINE__); EE printf("line=%u,"#EE"<<\n",__LINE__);

#define DP_SPEED(C)  {C}
#define DP_TC_SPEED(C) {C}

#define D_STR(c)    (int)(c).uiLen,(c).strVal

#endif //_CDT_MACROS_H


