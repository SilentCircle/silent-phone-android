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

#ifndef _C_T_DATABUF_H
#define _C_T_DATABUF_H

class CTDataBuf{
   int iLen;
   int iPos;
   int iCanDelete;
   char *buf;
public:
   
   CTDataBuf()
   :buf(NULL),iLen(0),iPos(0),iCanDelete(0){}
   
   CTDataBuf(char *p, int iLen, int iCanDelete)
   :buf(NULL),iLen(0),iPos(0),iCanDelete(0){
      
      set(p, iLen, iCanDelete);
   }
   
   ~CTDataBuf(){if(iCanDelete)delete buf;}
   
   void set(char *p, int iNewLen, int _iCanDelete){
      if(iCanDelete)delete buf;
      iPos=0;
      buf=p;
      iLen=iNewLen;
      iCanDelete=_iCanDelete;
   }
   void set(CTDataBuf &s){
      set(s.buf, s.iLen, 0);
   }
   
   void reset(){iPos=0;}
   
   inline int getPos(){return iPos;}
   inline int getLen(){return iLen;}
   
   char *get(int iBytes){
      int np = iPos + iBytes;
      if(np > iLen)return 0;
      int pp = iPos; iPos = np;
      return buf + pp;
   }
   
};

#endif 

