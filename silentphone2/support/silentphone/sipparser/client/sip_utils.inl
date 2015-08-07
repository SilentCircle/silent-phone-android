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

#include <ctype.h>
#include <stdlib.h>

#define SIP_PACK_MIN_LEN 120
#define SIP_HDR_MIN_LEN (sizeof("RR sip:a@a SIP/2.0\r\n")-1)
#define SIP_HDR_R_MIN_LEN (sizeof("SIP/2.0 200 OK\r\n")-1)

int isValidSipResp(char *buf, int iLen){
   

   if(iLen<SIP_HDR_R_MIN_LEN)return 0;
//TODO aligned unaligned check +  if (*(int32*)buf=='2/PIS')
   if(strncmp(buf,"SIP/2.0 ",8)!=0)return 0;
   int i=0;
   i+=8;
   while(i<iLen){
      if(buf[i]=='\r' && buf[i+1]=='\n')return 1;
     // printf("%d ",buf[i]);
      if(buf[i]<' ' || (buf[i]&0x80))return 0;
      i++;
   }

   return 0;
}

int isValidSipReq(char *buf, int iLen){
   //
//INVITE sip:.....@reg.tivi.lv SIP/2.0

   if(iLen<SIP_HDR_MIN_LEN)return 0;

   int i=0;

   while(i<iLen){
      if(!isupper(buf[i])){
         if(buf[i]==' '){i++;break;}
         return 0;
      }
      i++;
   }
   if(i>=iLen)return 0;
   
   //sip:
   while(i<iLen){
      if(!::islower(buf[i])){
         if(buf[i]==':'){i++;break;}
         return 0;
      }
      i++;
   }
   if(i>=iLen)return 0;
   //find space 
   while(i<iLen){
      if(buf[i]<' ')return 0;
      if(buf[i]==' '){i++;break;}
      i++;
   }
   if(iLen-i<9)return 0;
   //TODO check SIP/2.0
   //
   int to=i+9;
   for(;i<iLen && i<to;i++){
      if(buf[i]=='\r' && buf[i+1]=='\n')return 1;
      if(buf[i]<' ' || buf[i]>127)return 0;
   }
   

   return 0;
}
int isValidSipStart(char *buf, int iLen){
   return isValidSipReq(buf,iLen)|isValidSipResp(buf,iLen);
}

//Content-length:  390
int findContentLen(char *buf, int iLen, int &ofs){
   int i;
   ofs=0;
   int isEqualCase(const char * src, const char * dst, int iLen);
   int to=iLen-(sizeof("Content-length: x\r\n\r\n")-2);
#define _T_CL "ontent-length:"
   for(i=0;i<to;i++){
      //printf("[%.*s]=%d\r\n",8,&buf[i],to-i);
      if(buf[i]=='C' && isEqualCase(&buf[i+1],_T_CL,sizeof(_T_CL)-1)){
         i+=sizeof(_T_CL);
         int r=atoi(&buf[i]); 
         ofs=i;
         return r;
      }
   }
#undef _T_CL
   return -1;

}

int isFullSipPacket(char *buf, int iLen, int &ctx, int iCheckHDR=1){
   
   
   if(iLen<SIP_HDR_MIN_LEN)return 0;

   if(iCheckHDR && !isValidSipStart(buf,iLen))return 0;
   
   //

   int ofs;
   int cl=findContentLen(buf,iLen,ofs);
//printf("cl=%d\n",cl); 
   if(cl<0)return 0;

   //find\r\n\r\n
   while(ofs+3+cl<iLen){
      if(buf[ofs]=='\r' && buf[ofs+1]=='\n' && buf[ofs+2]=='\r' && buf[ofs+3]=='\n'){
//printf("%d==%d\n",ofs+4+cl,iLen); 
         return (ofs+4+cl==iLen)?1:((ofs+4+cl<iLen)?-(ofs+4+cl):0);
      }
      ofs++;
   }
//puts("s4");


   return 0;
}