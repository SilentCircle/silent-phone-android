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

#include <string.h>
#include <CFNetwork/CFNetwork.h>
#include "../baseclasses/CTBase.h"

#define SKIP_WS

class CTIOS_VOIP_BG{
public:
   CFReadStreamRef readStream;
#ifndef SKIP_WS
   CFWriteStreamRef writeStream;
#endif
   CFStreamClientContext myContext;// = {0, NULL, NULL, NULL, NULL};
   
   CTIOS_VOIP_BG(int s){
      memset(&myContext,0,sizeof(CFStreamClientContext));
   
      CTIOS_VOIP_BG *p=this;
      readStream=NULL;
#ifdef SKIP_WS
      CFStreamCreatePairWithSocket(kCFAllocatorDefault,s,&p->readStream,NULL);

      CFReadStreamSetProperty(p->readStream, kCFStreamNetworkServiceType,kCFStreamNetworkServiceTypeVoIP);
      
      
      CFReadStreamOpen(p->readStream);	
#else
      CFStreamCreatePairWithSocket(kCFAllocatorDefault,s,&p->readStream,&p->writeStream);
      
      CFReadStreamSetProperty(p->readStream, kCFStreamNetworkServiceType,kCFStreamNetworkServiceTypeVoIP);
      
      CFWriteStreamSetProperty(p->writeStream, kCFStreamNetworkServiceType,kCFStreamNetworkServiceTypeVoIP);
      
      CFReadStreamOpen(p->readStream);	
      CFWriteStreamOpen(p->writeStream);
      
#endif
   }
   ~CTIOS_VOIP_BG(){
      if(readStream){
      CFReadStreamClose(readStream);
      CFRelease(readStream);
         readStream=NULL;
      }
#ifndef SKIP_WS
      CFWriteStreamClose(writeStream);
      CFRelease(writeStream);
#endif
   }
};

void *prepareTcpSocketForBg(int s){
   //return NULL;
   CTIOS_VOIP_BG *p=new CTIOS_VOIP_BG(s);
   return p;
}

void relTcpBGSock(void *ptr){
   //return;
   if(!ptr)return;
   
   CTIOS_VOIP_BG *p=(CTIOS_VOIP_BG *)ptr;
   delete p;
   
}


NSString *toNSFromTBN(CTStrBase *b, int N){
   if(!b || !b->getLen())return @"";
   NSString *r=[NSString stringWithCharacters:(const unichar*)b->getText() length:min(N,b->getLen())];  
   return r;
}

NSString *toNSFromTB(CTStrBase *b){
   if(!b || !b->getLen())return @"";

   NSString *r=[NSString stringWithCharacters:(const unichar*)b->getText() length:b->getLen()];   
   return r;
}

char * t_CFStringCopyUTF8String(CFStringRef str,  char *buffer, int iMaxLen) {
   if (str == NULL || !buffer || iMaxLen<1) {
      return NULL;
   }
   buffer[0]=0;
   iMaxLen--;
   
   // CFIndex length = CFStringGetLength(aString);
   // CFIndex maxSize  = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingUTF8);
   
   if (CFStringGetCString(str, buffer, iMaxLen, kCFStringEncodingUTF8)) {
      return buffer;
   }
   return NULL;
}


