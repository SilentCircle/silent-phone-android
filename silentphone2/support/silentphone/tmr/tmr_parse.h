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

#ifndef media_relay_tmr_parse_h
#define media_relay_tmr_parse_h



typedef struct{
   
   enum TMR_PACK_IDS{
      eStart='TMR0',
      
      eMEDIA_ID,
      
      eRESP_CODE,
      eRESP_MSG,
      
      eSERV_IP4,
      eSERV_IP6,
      eSERV_PORT, //2bytes
      
      eSERV_MAX_SOCK_PER_CALL,
      
      eCREATOR_SSRC,
      
      ePEER_SSRC,
      ePEER_IP4,
      ePEER_IP6,
      ePEER_PORT, //2bytes
      
      eNONCE,
      eNRESPONSE,
      
      //peer p2p candidates
      ePEER_P2P_IP4,
      ePEER_P2P_IP6,
      ePEER_P2P_PORT,
      
      eLast
   };
   
   typedef struct{
      unsigned char *p;
      int iLen;
      int iSize;
   }ITEM;
   
   ITEM n[eLast-eStart];
   int iItemsFound;
   
   inline ITEM *get(TMR_PACK_IDS e){
      if(!iItemsFound)return NULL;

      
      if(e<=eStart || e>=eLast)return NULL;
      
      ITEM *i = &n[e - eStart];
      
      return(!i->p || !i->iSize) ? NULL : i;
   }
   
   template<class X> int getValue(TMR_PACK_IDS e, X *x){
      ITEM *i = get(e);
      if(!i)return -1;
      if(sizeof(X)!=i->iLen)return -2;
      
      for(int n = 0 ; n<sizeof(X); n++){
         ((unsigned char *)x)[n] = i->p[n];
      }
      return 0;
   }
   
}TMR_PACK_STR;

int parseTMR(TMR_PACK_STR *s, unsigned char *pack, int iLen);


#endif
