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

#ifndef _C_T_FIFO_H
#define _C_T_FIFO_H

#ifdef __SYMBIAN32__
//#include "../codecs/g711/g711.h"
#endif
template <int T>
class CTFiFo{
   char buf[T+1];
   char bufRet[T+1];
   int iFillded;
   int iRemoved;
   int iPosToRemove;
   int iPosToFill;

   int iSFillded;
   int iSRemoved;
   int iSPosToRemove;
   int iSPosToFill;
public:
   CTFiFo()
   {
      reset();
   }
   void save(){
      iSFillded=iFillded;
      iSRemoved=iRemoved;
      iSPosToRemove=iPosToRemove;
      iSPosToFill=iPosToFill;
   }
   void restore()
   {
      iFillded=iSFillded;
      iRemoved=iSRemoved;
      iPosToRemove=iSPosToRemove;
      iPosToFill=iSPosToFill;
   }
   void reset()
   {
      iPosToFill=iRemoved=iPosToRemove=iFillded=0;
   }
   void add(char *p, int iLen) 
   {
     if(iPosToFill+iLen<=T)
     {
        if(!p)memset(buf+iPosToFill,0,iLen);else 
        memcpy(buf+iPosToFill,p,iLen);
     }
     else
     {
        int l=T-iPosToFill;//iPosToFill+iLen-T;
        if(!p){memset(buf+iPosToFill,0,l);memset(buf,0,iLen-l);}
        else{ 
        memcpy(buf+iPosToFill,p,l);
        memcpy(buf,p+l,iLen-l);
        }

     }
    // printf("==<%.*s>==\n",T,buf);
     iFillded+=iLen;

     iPosToFill+=iLen;
     if(iPosToFill>=T)iPosToFill-=T;
   }
   void remBytes(int iLen=1){
     iRemoved+=iLen;
     iPosToRemove+=iLen;
     if(iPosToRemove>=T)iPosToRemove%=T;
   }
   
   char *get(int iLen=1)
   {
     char *p;
     if(iPosToRemove+iLen<=T)
     {
        //memcpy(p,buf+iPosToRemove,iLen);
        p=buf+iPosToRemove;
     }
     else
     {
        p=bufRet;
        int l=T-iPosToRemove;
        memcpy(p,buf+iPosToRemove,l);
        memcpy(p+l,buf,iLen-l);
     }
     iRemoved+=iLen;
     iPosToRemove+=iLen;
     if(iPosToRemove>=T)iPosToRemove-=T;

    // printf("<%.*s>%d\n",iLen,p,bytesIn());
     return p;
   }
   inline int bytesIn()
   {
     return iFillded-iRemoved;
   }  
};

#endif //_C_T_FIFO_H
