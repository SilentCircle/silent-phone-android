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
#include <stdlib.h>
#include <string.h>
#include "CSessions.h"
#include "../baseclasses/CTListBase.h"


class CListItemTR_MSG: public CListItem{
public:
   int iRespID;
   char bufItemN[128];
   int iBufNLen;
   char bufResp[256];
   int iRespLen;
   typedef struct{
      int id;
      char *p;
      int iLen;
   }V;
   
   CListItemTR_MSG(char *name ,int len, int msgID, char *pbufResp, int iiRespLen):CListItem(0){
      iRespID=msgID;
      if(len>=sizeof(bufItemN))len=sizeof(bufItemN)-1;
      if(iiRespLen>=sizeof(bufResp))iiRespLen=sizeof(bufResp)-1;
      strncpy(bufItemN,name,len);iBufNLen=len;bufItemN[len]=0;
      strncpy(bufResp,pbufResp,iiRespLen);iRespLen=iiRespLen;bufResp[iiRespLen]=0;
   }
   virtual int isItem(void *p, int iSize){
      
      int isEqual(char *src,char *dst,int iLen);
      
      if(iSize!=sizeof(V))return 0;
      V *v=(V*)p;
      if(v->iLen)
      {
         return v->iLen==iBufNLen && isEqual(( char*)v->p,&bufItemN[0],iBufNLen);
      }
      return iRespID && iRespID==v->id;
   }
};

static CListItemTR_MSG *getLine(char *p, int iLen, int &iPos){
   CListItemTR_MSG *it=NULL;
   char *name=0;
   int nl;
   int id;
   char *resp=0;
   int rl;
   if(iPos<iLen)
   {
      id=atoi(&p[iPos]);
      while(iPos<iLen){iPos++;if(p[iPos-1]=='^')break;}
      
      name=&p[iPos];
      while(iPos<iLen){iPos++;if(p[iPos-1]=='^')break;}
      nl=&p[iPos]-name-1;
      
      resp=&p[iPos];
      while(iPos<iLen){iPos++;if(p[iPos-1]<' ')break;}
      rl=&p[iPos]-resp;
      if(p[iPos-1]<' ')rl--;
      if(rl<=0 || nl<=0)return NULL;
      it =new CListItemTR_MSG(name,nl,id,resp,rl);
      //  printf("%d <%.*s><%.*s>\n",id,nl,name,rl,resp);
   }
   return it;
}

void* loadMsg(char *szMsgTranslateFN){
   int iLen=0;
   char *p=loadFile(szMsgTranslateFN,iLen);
   if(!p && (szMsgTranslateFN[0]=='c' || szMsgTranslateFN[0]=='C')){
      char buf[256];
      strncpy(buf,szMsgTranslateFN,sizeof(buf)-1);
      buf[sizeof(buf)-1]=0;
      buf[0]='e';//checking flash drive on symbian
      p=loadFile(buf,iLen);
   }
   if(!p)  return NULL;
   
   if(iLen<1){
      delete p;
      return NULL;
   }
   CTList *l=new CTList();
   int iPos=0;
   CListItemTR_MSG *it;
   while(1){
      it=getLine(p,iLen,iPos);
      if(!it)break;
      l->addToRoot(it);
   }
   
   
   delete p;
   
   
   return l;
}

void relTransMsgList(CPhSesions *ph){
   CTList *l=(CTList*)ph->pTransMsgList;
   if(!l)return;
   delete l;
   l=NULL;
}

int  tryTranslateMsg(CPhSesions *ph, SIP_MSG *sMsg,CTEditBase &b){
   CTList *l=(CTList*)ph->pTransMsgList;
   if(!l || !sMsg)return -1;
   CListItemTR_MSG::V v;
   v.id=(int)sMsg->sipHdr.dstrStatusCode.uiVal;
   v.iLen=(int)sMsg->sipHdr.dstrReasonPhrase.uiLen;
   v.p=sMsg->sipHdr.dstrReasonPhrase.strVal;
   
   CListItemTR_MSG *resp=(CListItemTR_MSG *)l->findItem(&v,sizeof(CListItemTR_MSG::V));
   
   if(!resp)return -1;
   
   b.setText(resp->bufResp,resp->iRespLen);
   
   return 0;
}
