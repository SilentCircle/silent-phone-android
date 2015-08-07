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

#ifndef media_relay_tmr_create_h
#define media_relay_tmr_create_h
#include "tmr_parse.h"
#include <string.h>

static int addFieldS(char *dst, int iMaxSize, TMR_PACK_STR::TMR_PACK_IDS e, void *p, int psize = 0){
   int l=0;
   
   if(iMaxSize<8)return 0;
   
   if(psize==0)psize = (int)strlen((char*)p);
   
   if(iMaxSize<8+psize)return 0;
   
   *(int*)dst = psize + 8;
   dst+=4;
   l+=4;
   
   *(int*)dst = e;
   dst+=4;
   l+=4;
   
   memcpy(dst, p, psize);
   l+=psize;
   
  // printf("add id_%x l_%d\n",e,l);
   
   return l;
}

template<class T>
static int addField(char *dst, int iMaxSize, TMR_PACK_STR::TMR_PACK_IDS e, T i){
   
   return addFieldS(dst, iMaxSize, e, &i, sizeof(T));
}


#endif
