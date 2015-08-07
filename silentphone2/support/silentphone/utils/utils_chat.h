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

#ifndef C_T_CHAT_MEM_H
#define C_T_CHAT_MEM_H
#include <string.h>

class CMsgMem{
   typedef struct{
      int iCSeq;
      int iLen;
      struct{
         int iLen;
         char buf[8];
      }tag;
      struct{
         int iLen;
         char buf[16];
      }cid;
   }LAST_MSG;

public:
   CMsgMem(int iCnt=20)
      :iCnt(iCnt),iCur(0)
   {
      root =new LAST_MSG[iCnt];
      memset(root,0,sizeof(LAST_MSG)*iCnt);
   }
   ~CMsgMem(){delete []root;}
//#define MEM_CHAT_ITEM(msg) (msg).

   int hasItem(int iCSeq, int iMsgLen, int iCidLen, char *cid, int iTagLen, char *tag)
   {
      int i,j,ok;
      LAST_MSG *cur;
      for(i=0;i<iCnt;i++)
      {
         cur=&root[i];
         if(
            cur->iCSeq==iCSeq &&
            cur->iLen==iMsgLen &&
            cur->tag.iLen==iTagLen &&
            cur->cid.iLen==iCidLen
            )
         {
            ok=1;
            for(j=0;j<8 && j<iTagLen;j++)
            {
               if(cur->tag.buf[j]!=tag[j])
               {
                  ok=0;
                  break;
               }
            }
            if(!ok)continue;
            for(j=0;j<16 && j<iCidLen;j++)
            {
               if(cur->cid.buf[j]!=cid[j])
               {
                  ok=0;
                  break;
               }
            }
            if(ok)
               return 1;
         }
      }
      addItem(iCSeq, iMsgLen, iCidLen, cid, iTagLen, tag);
      return 0;
   }
private:
   int iCnt,iCur;
   LAST_MSG *root;
   void addItem(int iCSeq, int iMsgLen, int iCidLen, char *cid, int iTagLen, char *tag)
   {
      LAST_MSG *p;
      int i;
      if(iCur>=iCnt)
      {
         iCur=0;
      }
      p=&root[iCur];
      iCur++;

      p->iLen=iMsgLen;
      p->iCSeq=iCSeq;
      p->cid.iLen=iCidLen;
      p->tag.iLen=iTagLen;
      if(iCidLen && cid)
      {
         for(i=0;i<16 && i<iCidLen;i++,cid++)
         {
            p->cid.buf[i]=*cid;
         }
      }
      if(iTagLen && tag)
      {
         for(i=0;i<8 && i<iTagLen;i++,tag++)
         {
            p->tag.buf[i]=*tag;
         }
      }
      
   }
};
#endif //C_T_CHAT_MEM_H
