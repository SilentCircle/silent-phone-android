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



#include "../encrypt/md5/md5.h"
#include "../sipparser/client/sip.h"
#include "digestmd5.h"

#define DSTR_LUS(c)  (c).uiLen, (unsigned char*)((c).strVal)
#define DSTR_USL(c)  (unsigned char*)((c).strVal),(c).uiLen


void CvtHex(IN HASH Bin, OUT HASHHEX Hex )
{
   unsigned short i;
   unsigned char j;
   
   for (i = 0; i < HASHLEN; i++) {
      j = (Bin[i] >> 4) & 0xf;
      if (j <= 9)
         Hex[i*2] = (j + '0');
      else
         Hex[i*2] = (j + 'a' - 10);
      j = Bin[i] & 0xf;
      if (j <= 9)
         Hex[i*2+1] = (j + '0');
      else
         Hex[i*2+1] = (j + 'a' - 10);
   };
   Hex[HASHHEXLEN] = '\0';
};

int calcMD5(unsigned char *p, int iLen, char *out){
   HASH HA1;
   CTMd5 md5;
   md5.update(p, iLen);
   md5.final((unsigned char *)HA1);
   CvtHex(HA1, out);
   return strlen(out);
}

unsigned int calcMD5(const char *p, int iLen){
   CTMd5 md5;
   if(!iLen)iLen=strlen(p);
   md5.update((unsigned char*)p, iLen);
   return md5.final();
}

unsigned int calcMD5(const char *p, int iLen, int n){
   CTMd5 md5;
   if(!iLen)iLen=strlen(p);
   for(int i=0;i<n;i++)
      md5.update((unsigned char*)p, iLen);
   
   return md5.final();
}
//if you ask why md5 ??? read (RFC 3621, 2617)
/* calculate H(A1) as per spec */
int DigestCalcHA1(IN  HDR_AUT * aut, IN unsigned char *un, IN unsigned char * pwd, 
                  OUT HASHHEX SessionKey)
{
   CTMd5 md5;
   
   HASH HA1;

   
   if((aut->iFlag & 2048)==0) return false;
   //TODO save UN:realm:pwd as hash in xml
   
   md5.update(un, strlen((const char *)un));
   md5.update( (unsigned char *)":", 1);
   md5.update( DSTR_USL(aut->dstrRealm));//TODO realm or domain
   md5.update((unsigned char *)":", 1);
   md5.update( pwd, strlen((const char *)pwd));
   md5.final((unsigned char *)HA1);
   if (CMP(aut->dstrAlgo,"MD\x15\rSESS",8)) {
      
      if ((aut->iFlag & (16|8))!=(16|8)) return false;

      md5.update( (unsigned char *)HA1, HASHLEN);
      md5.update( (unsigned char *)":", 1);
      md5.update(  DSTR_USL(aut->dstrNonce));
      md5.update( (unsigned char *)":", 1);
      md5.update( DSTR_USL(aut->dstrCnonce));
      md5.final((unsigned char *)HA1);
   };
   CvtHex(HA1, SessionKey);

   return true;
};

//if you ask why md5 ??? read (RFC 3621, 2617)
/* calculate request-digest/response-digest as per HTTP Digest spec */
int DigestCalcResponse(
                       IN HASHHEX HA1,           /* H(A1) */
                       IN HDR_AUT * aut,
                       IN unsigned int uiMethodID,
                       IN char *cNonce,
                       IN char *nonceCount,
                       IN STR * sipAddr,
                       IN HASHHEX HEntity,       /* H(entity body) if qop="auth-int" */
                       OUT HASHHEX respHex32      /* request-digest or response-digest */
                       )
{
   
   //if you ask why md5 ??? read (RFC 3621, 2617)
   CTMd5 md5;
   HASH HA2;
   HASH RespHash;
   HASHHEX HA2Hex;
   
   // calculate H(A2)
   switch(uiMethodID)
   {
      case METHOD_INVITE:
         md5.update((unsigned char *)"INVITE", 6);
         break;
      case METHOD_REGISTER:
         md5.update( (unsigned char *)"REGISTER", 8);
         break;
      case METHOD_CANCEL:
         md5.update( (unsigned char *)"CANCEL", 6);
         break;
      case METHOD_BYE:
         md5.update( (unsigned char *)"BYE", 3);
         break;
      case METHOD_ACK:
         md5.update( (unsigned char *)"ACK", 3);
         break;
      case METHOD_MESSAGE:
         md5.update( (unsigned char *)"MESSAGE", 7);
         break;
      case METHOD_OPTIONS:
         md5.update( (unsigned char *)"OPTIONS", 7);
         break;
      case METHOD_INFO:
         md5.update( (unsigned char *)"INFO", 4);
         break;
      default:
         return false;
   }
   md5.update( (unsigned char *)":", 1);
   md5.update( sipAddr->s,sipAddr->len);//TODO check contact
   if (CMP(aut->dstrQOP,"AUTH\rINT",8))
   {
      md5.update( (unsigned char *)":", 1);
      md5.update( (unsigned char *)HEntity, HASHHEXLEN);
   }
   md5.final((unsigned char *)HA2);
   CvtHex(HA2, HA2Hex);

   
   if ((aut->iFlag & 16)==0) return false;
   // calculate response

   
   md5.update( (unsigned char *)HA1, HASHHEXLEN);
   md5.update( (unsigned char *)":", 1);
   md5.update( DSTR_USL(aut->dstrNonce));
   md5.update( (unsigned char *)":", 1);
   if (aut->dstrQOP.strVal) {

      if(nonceCount)
      {
         md5.update( (unsigned char *)nonceCount, strlen(nonceCount));
         md5.update( (unsigned char *)":", 1);
      }
      else if(aut->dstrNonceCount.strVal)
      {
         md5.update( DSTR_USL(aut->dstrNonceCount));
         md5.update( (unsigned char *)":", 1);
      } 
      
      if(cNonce)
         md5.update( (unsigned char *)cNonce, strlen(cNonce));
      else if(aut->dstrCnonce.strVal)
         md5.update( DSTR_USL(aut->dstrCnonce));
      md5.update((unsigned char *)":", 1);
      md5.update( DSTR_USL(aut->dstrQOP));
      md5.update( (unsigned char *)":", 1);
   };
   md5.update( (unsigned char *)HA2Hex, HASHHEXLEN);
   md5.final((unsigned char *)RespHash);
   CvtHex(RespHash, respHex32);
   return true;
};





