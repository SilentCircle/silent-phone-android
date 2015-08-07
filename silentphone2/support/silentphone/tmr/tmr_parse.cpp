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
#include <string.h>
#include "tmr_parse.h"

int parseTMR(TMR_PACK_STR *s, unsigned char *pack, int iLen){
   
   memset(s, 0, sizeof(TMR_PACK_STR));
   
   while(iLen>7){
      //size32bit|enum_id_32bit|data
      //size32bit = (4byte+enum4bytes+dataBytes);
      int size  = *(int*)pack;
      //printf("size=%d\n",size);
      if(size<8 || size>iLen)return -1;
      
      pack+=4;
      int id =  *(int*)pack;
      if(id<=TMR_PACK_STR::eStart || id>=TMR_PACK_STR::eLast)return -2;
      //printf("id=%d l=%d sz=%d\n",id, size-8, iLen);
      pack+=4;
      id-=TMR_PACK_STR::eStart;
      s->n[id].p=pack;
      s->n[id].iLen=size-8;
      s->n[id].iSize=size;
      s->iItemsFound++;
      iLen-=size;
      pack+=s->n[id].iLen;
      
      if(iLen==0)return 0;
      
   }
   return -3;
}


