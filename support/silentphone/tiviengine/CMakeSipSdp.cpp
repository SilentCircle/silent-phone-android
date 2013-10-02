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

#include "CSessions.h"
#include "digestmd5.h"


#define ADD_FSTR(sp,LEN,Ds,X) {memcpy((sp+LEN),Ds,X);LEN+=X; sp[LEN]=0; }
struct SIP_METH{
   int id;
   int uiLen;
   char strVal[56];
};

#define GET_METH(TOMAKE,RET){RET=0;do{if(TOMAKE==sip_meth[RET].id)break;}while(sip_meth[RET++].id);}

const struct SIP_METH CMakeSip::sip_meth[]=
{
   {METHOD_INVITE,6, "INVITE"},
   {METHOD_BYE,3, "BYE"},
   {METHOD_ACK ,3,"ACK"},
   {METHOD_CANCEL,6, "CANCEL"},
   {METHOD_MESSAGE,7, "MESSAGE"},
   {METHOD_INFO,4, "INFO"},
   {METHOD_UPDATE,6, "UPDATE"},
   {METHOD_REGISTER,8, "REGISTER"},
   {METHOD_OPTIONS,7, "OPTIONS"},
   {METHOD_REFER,5, "REFER"},
   {METHOD_NOTIFY,6, "NOTIFY"},
   {METHOD_PUBLISH,7, "PUBLISH"},
   {METHOD_SUBSCRIBE,9, "SUBSCRIBE"}
};
int CMakeSip::addParams(char *p, int iLenAdd)
{
   ADD_L_STR(buf,uiLen,p,iLenAdd);
   return 0;
}

int CMakeSip::addAuth(char *un, char *pwd, SIP_MSG *psMsg)
{
   
   HASHHEX HA1;
   HASHHEX HA2 = "";
   HASHHEX respHex32;
   HDR_AUT * hdrAuth=&psMsg->hdrProxyAuthen;
   
   if (psMsg==NULL || un==NULL || pwd==NULL)
      return -1;
   DEBUG_T(0,"add proxy auth");
   
   if(psMsg->sipHdr.dstrStatusCode.uiVal==401)
   {
      hdrAuth=&sMsg->hdrWWWAuth;
   }
   char *pCNonce=hdrAuth->dstrQOP.strVal?(char *)"6ad34fc1":NULL;
   char *pNC=(char *)"00000001";
   
   
   if (false==DigestCalcHA1(hdrAuth,(unsigned char *)un,(unsigned char *)pwd, HA1))
      return -2;
   if (false==DigestCalcResponse(HA1, hdrAuth,psMsg->hdrCSeq.uiMethodID,pCNonce,pNC, &strDstAddr, HA2, respHex32))
      return -2;
   if(psMsg->sipHdr.dstrStatusCode.uiVal==407)
   {
      ADD_STR(buf,uiLen,"Proxy-Authorization: ");
   }
   else
   {
      ADD_STR(buf,uiLen,"Authorization: ");
   }
   
   //"
   //TODO add MD5 or MD5 sess
   uiLen+= (unsigned int)sprintf(buf+uiLen,
                                 "Digest username=\"%s\", realm=\"%.*s\", nonce=\"%.*s\", uri=\"%.*s\", response=\"%.*s\""
                                 ,un
                                 ,D_STR(hdrAuth->dstrRealm)
                                 ,D_STR(hdrAuth->dstrNonce)
                                 ,strDstAddr.len,strDstAddr.s
                                 ,32,respHex32
                                 );
   
   if(hdrAuth->iFlag & 4)
   {
      uiLen+= (unsigned int)sprintf(buf+uiLen,", algorithm=%.*s",D_STR(hdrAuth->dstrAlgo));
   }
   
   if(hdrAuth->iFlag & 128)
   {
      uiLen+= (unsigned int)sprintf(buf+uiLen,", opaque=\"%.*s\"",D_STR(hdrAuth->dstrOpaque));
   }
   if(hdrAuth->iFlag & 32)
   {
      uiLen+= (unsigned int)sprintf(buf+uiLen,", qop=\"%.*s\"",D_STR(hdrAuth->dstrQOP));
      if(pNC)
      {
         uiLen+= (unsigned int)sprintf(buf+uiLen,", nc=%s",pNC);
      }
      if(pCNonce)
      {
         uiLen+= (unsigned int)sprintf(buf+uiLen,", cnonce=%s",pCNonce);
      }
   }
   
   ADD_CRLF(buf,uiLen);
   
   return 0;
}
int hasRouteLR(HLD_ROUTE *hld, unsigned int id)
{
   if(id<hld->uiCount)
   {
      int l=(int)hld->x[id].sipUri.dstrSipAddr.uiLen;
      char *p=hld->x[id].sipUri.dstrSipAddr.strVal;
      
      int res=(l>4 &&
               p[l-3]==';'  &&
               p[l-2]=='l'  &&
               p[l-1]=='r');
      
      if(res)return res;
      
      for(int i=0;i<l;i++){
         if(i+2>l)break;
         if(p[i]==';' && p[i+1]=='l'&& p[i+2]=='r')return 1;
      }
      
   }
   return 0;
}
int isTiViServ(PHONE_CFG *p);
int CMakeSip::makeReq(int id, PHONE_CFG * cfg, ADDR *addrExt,STR_64 *str64ExtADDR)
{
   unsigned int iMeth=0;
   unsigned int uiFromLoc=0;
   unsigned int uiFromLen=0;
   strDstAddr.s=(unsigned char *)&spSes->dstSipAddr.strVal;
   strDstAddr.len=(int)spSes->dstSipAddr.uiLen;
   
   
   if(cfg){
      
      if(strcmp(&cfg->szSipTransport[0],"TLS")==0){iIsTCP=0;iIsTLS=1;}
      else if(strcmp(&cfg->szSipTransport[0],"TCP")==0){iIsTCP=1;iIsTLS=0;}
      else if(strcmp(&cfg->szSipTransport[0],"UDP")==0){iIsTCP=0;iIsTLS=0;}
      else {iIsTCP=0;iIsTLS=0;}
      
   }
   
   char *s=buf;
   
   int iPrevId=spSes->cs.iSendS;
   spSes->cs.iSendS=id;
   if(id!=METHOD_ACK)
   {
      if(iPrevId!=spSes->cs.iSendS)
      {
         if(spSes->cs.iSendS==METHOD_OPTIONS){
            spSes->sSendTo.setRetransmit(5,3*T_GT_SECOND);
         }
         else if(iIsTCP || iIsTLS)
            spSes->sSendTo.setRetransmit(7,5*T_GT_SECOND);
         else if(spSes->cs.iSendS==METHOD_REGISTER)
            spSes->sSendTo.setRetransmit(7,1*T_GT_SECOND);
         else  if(spSes->cs.iSendS==METHOD_INVITE)
            spSes->sSendTo.setRetransmit(7,(cfg==NULL || cfg->iNetworkIsMobile)?(6*T_GT_SECOND):(3*T_GT_SECOND));
         else
            spSes->sSendTo.setRetransmit();
      }
      spSes->cs.iWaitS=200;
   }
   else
   {
      spSes->cs.iWaitS=0;
   }
   
   //TODO getITFromZZ
   unsigned int uiCSeq;
#if 1
   if(cfg && id==METHOD_REGISTER)
   {
      if(!cfg->uiSipCSeq){
         //next reg should be with greater CSeq, even app restarts, if call-id is same
         //problem was with SIP Thor on OpenSIPS XS 1.4.5, but not  with FreeSwitch
         
         int get_time();
         cfg->uiSipCSeq=(unsigned int)get_time();
         cfg->uiSipCSeq-=1000000000;
      }
      cfg->uiSipCSeq++;
      uiCSeq=(unsigned int)cfg->uiSipCSeq;
   }
   else if(spSes){
      if(id!=METHOD_ACK && id!=METHOD_CANCEL)spSes->uiSipCseq++;
      uiCSeq=spSes->uiSipCseq;
   }
   else {
      uiCSeq=getTickCount();
      while(uiCSeq>1000000000)uiCSeq-=1000000000;
   }
#else
   static unsigned int  ss=User::TickCount();
   ss++;
   uiCSeq=ss;
   
#endif
   if(!uiCSeq)uiCSeq=1;

   int iReplaceRoute=0;
   
   if(iContactId >(int)sMsg->hldContact.uiCount)
      iContactId=0;

   GET_METH(id,iMeth);
   
   //TODO do we need to send route with cancel msg
#define METHOD_CANCEL_IGNORE 0
   
   struct HOLDER_CONTACT *hc = &sMsg->hldContact;
   HLD_ROUTE *hrr = &sMsg->hldRecRoute;
   
   //do i need to check "&& spSes->sSIPMsg.hldRecRoute.uiCount"?
   if(hc->uiCount<1 && spSes && spSes->sSIPMsg.hldContact.uiCount && sMsg->sipHdr.dstrStatusCode.uiVal>=300){
      hc = &spSes->sSIPMsg.hldContact;
      hrr = &spSes->sSIPMsg.hldRecRoute;
   }
   
   
   if(hc->x[iContactId].sipUri.dstrSipAddr.uiLen && (id & (METHOD_CANCEL_IGNORE|METHOD_REGISTER))==0)
   {
      if(hrr->uiCount)//TODO caller calle
      {
         iReplaceRoute=!hasRouteLR(hrr,0);
      }
      if(iReplaceRoute)
      {
         strDstAddr.s=(unsigned char *)hrr->x[0].sipUri.dstrSipAddr.strVal;
         strDstAddr.len=(int)hrr->x[0].sipUri.dstrSipAddr.uiLen;
      }
      else
      {
         strDstAddr.s=(unsigned char *)hc->x[iContactId].sipUri.dstrSipAddr.strVal;
         strDstAddr.len=(int)hc->x[iContactId].sipUri.dstrSipAddr.uiLen;
      }
   }
   //HDR
   ADD_DSTR(s,uiLen,sip_meth[iMeth]); ADD_CHAR(s,uiLen,' ');
   ADD_L_STR(s,uiLen,strDstAddr.s, strDstAddr.len);ADD_0_STR(s,uiLen," SIP/2.0\r\n");
   
   switch(id)
   {
      case METHOD_INVITE:
         spSes->uiSipCseq=uiCSeq;
         //sMsg->hdrCSeq.dstrID.uiVal=uiCSeq;
         break;
      case METHOD_ACK:
      case METHOD_CANCEL:
         uiCSeq=sMsg->hdrCSeq.dstrID.uiVal;
         break;
         
      case METHOD_REFER:
         //  ADD_STR(s,uiLen,"Refer-To: <");
         //ADD_DSTR(s,uiLen,spSes->str32Forward);
         //ADD_0_STR(s,uiLen,">\r\n");
         break;
         
      case METHOD_REGISTER:
         if(sMsg->hdrCSeq.dstrID.uiVal)uiCSeq=sMsg->hdrCSeq.dstrID.uiVal+1;//ast
         sMsg->hdrCSeq.dstrID.uiVal=uiCSeq;
         spSes->uiSipCseq=uiCSeq;
      case METHOD_OPTIONS:
         
         ADD_STR(s,uiLen,"Allow: INVITE,ACK,CANCEL,OPTIONS,MESSAGE,BYE,INFO\r\n");
         if(id==METHOD_OPTIONS)
         {
            ADD_STR(s,uiLen,"Accept: application/sdp, text/plain\r\n");
         }
         break;
         
   }

   //TODO test asterisk add
   //"sMsg->hldVia.x[0].dstrRecived.uiLen<32" - overflow test "spSes->pIPVisble=str64ExtADDR;memcpy"
   if((id & METHOD_REGISTER) && cfg && !cfg->reg.bUnReg && cfg->iUseOnlyNatIp==0 && sMsg &&  sMsg->hldVia.x[0].dstrRecived.uiLen<32)
   {
      if(sMsg->hldVia.x[0].dstrRecived.strVal && sMsg->hldVia.x[0].dstrRecived.uiLen){
         //TODO hi addr
         
         if(str64ExtADDR)
            spSes->pIPVisble=str64ExtADDR;
         
         memcpy(spSes->pIPVisble->strVal
                ,sMsg->hldVia.x[0].dstrRecived.strVal
                ,sMsg->hldVia.x[0].dstrRecived.uiLen);
         
         spSes->pIPVisble->uiLen=sMsg->hldVia.x[0].dstrRecived.uiLen;
         
         if(sMsg->hldVia.x[0].dstrRPort.uiVal)
            spSes->uiUserVisibleSipPort=sMsg->hldVia.x[0].dstrRPort.uiVal;
         
         if(addrExt)
         {
            addrExt->ip=ipstr2long(sMsg->hldVia.x[0].dstrRecived.uiLen,sMsg->hldVia.x[0].dstrRecived.strVal);
            
            SWAP_INT(addrExt->ip);
            addrExt->setPort((unsigned int)spSes->uiUserVisibleSipPort);
            
            
         }
      }
      else if(sMsg->hldVia.x[0].dstrRPort.uiVal){
         //  printf("[reg rport=%d ]",sMsg->hldVia.x[0].dstrRPort.uiVal);
         spSes->uiUserVisibleSipPort=sMsg->hldVia.x[0].dstrRPort.uiVal;
         addrExt->setPort((unsigned int)spSes->uiUserVisibleSipPort);
      }
   }
   
   if(iIsTCP)ADD_STR(s,uiLen,"Via: SIP/2.0/TCP ")
      else if(iIsTLS)ADD_STR(s,uiLen,"Via: SIP/2.0/TLS ")
         else ADD_STR(s,uiLen,"Via: SIP/2.0/UDP ")
            
   ADD_DSTR(s,uiLen,(*spSes->pIPVisble));
   
   if(spSes->uiUserVisibleSipPort!=DEAFULT_SIP_PORT)
      uiLen += sprintf(s + uiLen, ":%u", spSes->uiUserVisibleSipPort);
   
   ADD_STR(s,uiLen,";rport");
   ADD_STR(s,uiLen,";branch=z9hG4bK");
   ADD_L_STR(s,uiLen,sMsg->dstrCallID.strVal,4);
   if(id!=METHOD_CANCEL)
   {
      ADD_L_STR(s,uiLen,s,2);
   }
   else
   {
      ADD_STR(s,uiLen,"IN");
   }
   
   uiLen += sprintf(s + uiLen, "%u",uiCSeq^0x123);ADD_CRLF(s,uiLen);
   
   uiLen += sprintf(s + uiLen, "CSeq: %u ", uiCSeq);
   ADD_DSTRCRLF(s,uiLen,sip_meth[iMeth])
   
   
   
   if((id & (METHOD_REGISTER|METHOD_CANCEL))
      || (spSes->cs.iCaller  && (sMsg==NULL || sMsg->hdrCSeq.uiMethodID==0)))
   {
      
      ADD_FSTR(s,uiLen,"From: ",6);
      {
         uiFromLoc=uiLen;
         if(cfg->user.nick[0]){
            ADD_CHAR(s,uiLen,'"');
            ADD_0_STR(s,uiLen,cfg->user.nick);
            ADD_CHAR(s,uiLen,'"');
         }
         uiLen+=sprintf(s+uiLen," <%.*s>",D_STR(spSes->userSipAddr));
         
         uiFromLen=uiLen-uiFromLoc;
         
         if(spSes->str16Tag.uiLen)
         {
            ADD_0_STR(s,uiLen,";tag=");
            ADD_DSTRCRLF(s,uiLen,spSes->str16Tag);
         }
         else ADD_CRLF(s,uiLen);
         
      }
      {
         
         if((sMsg->hdrTo.dstrTag.uiLen==0 && id!=METHOD_REGISTER)
            || (id & METHOD_CANCEL)
            )
         {
            //  puts("a4");
            ADD_FSTR(s,uiLen,"To: <",5);
            //vajag lai straadaatu 3xx vispaar sho laikam jau nevajag,
            //jo es jau iedoshu liidzi sMsg kuru saneemu
            if(spSes && spSes->sSIPMsg.hdrTo.sipUri.dstrSipAddr.uiLen==0)
            {
               spSes->sSIPMsg.hdrTo.sipUri.dstrSipAddr.strVal=
               (char *)&spSes->sSIPMsg.rawDataBuffer+spSes->sSIPMsg.uiOffset;
               ADD_DSTR((char *)&spSes->sSIPMsg.rawDataBuffer, spSes->sSIPMsg.uiOffset,spSes->dstSipAddr);
               
               spSes->sSIPMsg.hdrTo.sipUri.dstrSipAddr.uiLen=spSes->dstSipAddr.uiLen;
               //varbuut sho saglabaat pie get new ses
            }
            if(sMsg->hdrTo.sipUri.dstrSipAddr.strVal)
            {
               ADD_DSTR(s,uiLen,sMsg->hdrTo.sipUri.dstrSipAddr);
            }
            else
            {
               ADD_DSTR(s,uiLen,spSes->dstSipAddr);
            }
            ADD_FSTR(s,uiLen,">\r\n",3);
            
            // puts("a5");
         }
         else if (id==METHOD_REGISTER)
         {
            //  DEBUG(0,"make register---------")
            ADD_FSTR(s,uiLen,"To: ",4);
            memcpy(s+uiLen,s+uiFromLoc,uiFromLen);
            uiLen+=uiFromLen;
            ADD_CRLF(s,uiLen);
         }
         else
         {
            ADD_DSTRCRLF(s,uiLen,sMsg->hdrTo.dstrFullRow);
            //  puts("a6");
         }
      }
   }
   else
   {
      //TODO use full row
      //      char *pMeth1;
      //    char *pMeth2;
      DSTR *fromAddr;
      DSTR *toAddr;
      DSTR *tag;
      //   puts("a7");
      
      if(sMsg->sipHdr.dstrStatusCode.uiVal)
      {
         fromAddr=&sMsg->hdrFrom.sipUri.dstrSipAddr;
         toAddr=&sMsg->hdrTo.sipUri.dstrSipAddr;
         tag=&sMsg->hdrTo.dstrTag;
         //    puts("a8");
      }
      else
      {
         toAddr=&sMsg->hdrFrom.sipUri.dstrSipAddr;
         fromAddr=&sMsg->hdrTo.sipUri.dstrSipAddr;
         tag=&sMsg->hdrFrom.dstrTag;
         //  puts("a9");
      }
      
      uiLen+=sprintf(s+uiLen,"From: <%.*s>", D_STR(*fromAddr));
      if(spSes->str16Tag.uiLen)
      {
         ADD_0_STR(s,uiLen,";tag=");
         ADD_DSTRCRLF(s,uiLen,spSes->str16Tag);
      }
      else ADD_CRLF(s,uiLen);
      

      uiLen+=sprintf(s+uiLen,"To: <%.*s>", D_STR(*toAddr));
      
      if(tag->uiLen && !fToTagIgnore)
      {
         ADD_0_STR(s,uiLen,";tag=");
         ADD_DSTRCRLF(s,uiLen,(*tag));
      }
      else ADD_CRLF(s,uiLen);
   }
   
   ADD_FSTR(s,uiLen,"Call-ID: ",9);ADD_DSTRCRLF(s,uiLen,sMsg->dstrCallID);
   ADD_STR(s,uiLen,"Max-Forwards: 70\r\n");
   
   if(id==METHOD_REGISTER){
      if(cfg && cfg->szUA[0]){
         ADD_STR(s,uiLen,"User-Agent: ");
         ADD_0_STR(s,uiLen,cfg->szUA);ADD_CRLF(s,uiLen);
      }
      else{
         ADD_STR(s,uiLen,USER_AGENT);
      }
   }
   
   
   if(id != METHOD_CANCEL_IGNORE && (hrr->uiCount || iReplaceRoute))//(toMake & (METHOD_BYE |METHOD_ACK)) && ???
   {
      
      uiLen+=addRoute(s+uiLen, 255, hrr, "Route: ",
                      !((sMsg->hdrCSeq.uiMethodID && sMsg->sipHdr.dstrStatusCode.uiVal) ||
                        (spSes->cs.iCaller && sMsg->hdrCSeq.uiMethodID==0))
                      ,iReplaceRoute, &hc->x[iContactId].sipUri.dstrSipAddr);
      
   }
   
   if(id & (METHOD_INVITE | METHOD_OPTIONS |METHOD_MESSAGE |METHOD_REGISTER|METHOD_REFER|METHOD_SUBSCRIBE|METHOD_PUBLISH|METHOD_UPDATE))
   {
      //unsigned int uiPos;
      if(cfg && cfg->user.nick[0]){
         uiLen+=sprintf(s+uiLen,"Contact: \"%s\" <sip:",cfg->user.nick);//sUserCfg.szUserName);
      }
      else {ADD_STR(s,uiLen,"Contact: <sip:");}
      //uiPos=uiLen;
      
      //TODO if register store contact addr and use it until next reg
      
      if(cfg  && (cfg->isOnline() || (id & METHOD_REGISTER)))
      {
         if(*cfg->user.nr)
            uiLen+=sprintf(s+uiLen,"%s@",cfg->user.nr);
         else if(*cfg->user.un)
            uiLen+=sprintf(s+uiLen,"%s@",cfg->user.un);
      }
      
      ADD_DSTR(s,uiLen,(*spSes->pIPVisble));
      
      
      if(spSes->uiUserVisibleSipPort!=DEAFULT_SIP_PORT)
         uiLen+=sprintf(s+uiLen,":%u",spSes->uiUserVisibleSipPort);
      
      
      
      if(iIsTCP) {ADD_STR(s,uiLen,";transport=tcp>")}else
         if(iIsTLS) {ADD_STR(s,uiLen,";transport=tls>")} else
         { ADD_FSTR(s,uiLen,">",1)}
      
      if(id== METHOD_REGISTER)
      {
         if(cfg->reg.bUnReg)
         {
            ADD_0_STR(s,uiLen,";expires=0")
         }
         else
            if (cfg->reg.bRegistring || cfg->reg.bReReg)
            {
               //TODO add reg addr -store to file (later check if rem)
               uiLen+= sprintf(s+uiLen,";expires=%u",cfg->uiExpires);
               int _TODO_is_serv;
               if(0){
                  ADD_STR(s,uiLen,";nat=yes");
               }
               else if(1){//isTiViServ(cfg)){
                  
                  if(cfg->iUseOnlyNatIp==0){//TODO cfg
                     if(cfg->iNetworkIsMobile)
                     {
                        if((cfg->iUseStun && cfg->iNet!=CTStun::NO_NAT) || (cfg->iUseStun==0 && (cfg->iNet==1)))
                        {
                           if(isTiViServ(cfg))
                           {
                              ADD_STR(s,uiLen,";nat=natm");
                           }
                           else
                           {
                              ADD_STR(s,uiLen,";nat=symetric");
                           }
                        }
                     }
                     else if(cfg->iUseStun)
                     {
                        switch(cfg->iNet)
                        {
                           case CTStun::NO_NAT: break;
                           case CTStun::FULL_CONE: ADD_STR(s,uiLen,";nat=fullcone");break;
                           case CTStun::PORT_REST: ADD_STR(s,uiLen,";nat=portrest");break;
                           case CTStun::REST_NAT:  ADD_STR(s,uiLen,";nat=restrict");break;
                           case CTStun::SYMMETRIC: ADD_STR(s,uiLen,";nat=symetric");break;
                           case CTStun::FIREWALL:  ADD_STR(s,uiLen,";nat=firewall");break;
                              
                              
                        }
                     }
                     else
                     {
                        if(cfg->iNet==1)
                           ADD_STR(s,uiLen,";nat=symetric");//TODO
                     }
                  }
               }
               
            }
         
         
      }
      
      ADD_CRLF(s,uiLen);
      /*
       if(toMake== METHOD_REFER)
       {
       uiLen+=sprintf(s+uiLen,"Referred-By: <sip:%.*s",uiLen-uiPos,s+uiPos);
       }*/
   }
   
   return 0;
}
//iReplaceRoute
unsigned int addRoute(char *p, int iMaxLen, HLD_ROUTE *hld, const char *sz, int iUp, int iReplaceRoute,DSTR *dstrContact)
{
   unsigned int ui,uiLen=0;
   
   int iMaxBytesAdd=iMaxLen;
   
   if(hld->uiCount || iReplaceRoute)
   {
      ADD_0_STR(p,uiLen,sz);
      
      if(iUp)
      {
         ui=iReplaceRoute?1:0;
         for(;ui<hld->uiCount;ui++)
         {
            if(uiLen+hld->x[ui].sipUri.dstrSipAddr.uiLen+10>=iMaxBytesAdd)break;//fail
            uiLen+= sprintf(p+uiLen,"<%.*s>,",D_STR(hld->x[ui].sipUri.dstrSipAddr));
         }
      }
      else
      {
         ui=hld->uiCount-1;
         if(iReplaceRoute)
         {
            ui--;
         }
         for(;(int)ui>=0;ui--)
         {
            if(uiLen+hld->x[ui].sipUri.dstrSipAddr.uiLen+10>=iMaxBytesAdd)break;//fail
            uiLen+= sprintf(p+uiLen,"<%.*s>,",D_STR(hld->x[ui].sipUri.dstrSipAddr));
         }
      }
      if(iReplaceRoute && dstrContact && dstrContact->uiLen)
      {
         if(uiLen+dstrContact->uiLen+10<=iMaxBytesAdd)
            uiLen+= sprintf(p+uiLen,"<%.*s>,",D_STR(*dstrContact));
      }
      uiLen--;
      ADD_CRLF(p,uiLen);
   }
   return uiLen;
}

int CMakeSip::makeResp(int id, PHONE_CFG *cfg, char *uri, int iUriLen)
{
   char *s=buf;
   unsigned int ui;
   if(sMsg==NULL)return -1;
   
   if(cfg){
      if(strcmp(&cfg->szSipTransport[0],"TLS")==0){iIsTCP=0;iIsTLS=1;}
      else if(strcmp(&cfg->szSipTransport[0],"TCP")==0){iIsTCP=1;iIsTLS=0;}
      else if(strcmp(&cfg->szSipTransport[0],"UDP")==0){iIsTCP=0;iIsTLS=0;}
      else {iIsTCP=0;iIsTLS=0;}
   }
   else if(sMsg){
      //sMsg->hldVia[]
   }
   
   if(spSes && spSes->cs.iWaitS==METHOD_ACK && spSes->cs.iCallStat== CALL_STAT::ESendError)
   {
      if(spSes->sSendTo.iRetransmitions<=0)
         spSes->sSendTo.setRetransmit(3);
   }
   
   switch(id)
   {
      case METHOD_ACK:
         ADD_STR(s,uiLen,"ACK ");
         ADD_L_STR(s,uiLen,uri, iUriLen);ADD_0_STR(s,uiLen," SIP/2.0\r\n");
         break;
      case 100:
         ADD_STR(s,uiLen,"SIP/2.0 100 Trying\r\n");
         break;
      case 180:
         ADD_STR(s,uiLen,"SIP/2.0 180 Ringing\r\n");
         break;
      case 183:
         ADD_STR(s,uiLen,"SIP/2.0 183 Session Progress\r\n");
         break;
      case 200:
         ADD_FSTR(s,uiLen,"SIP/2.0 200 OK\r\n",16);
         break;
         //     case 400:
         //  ADD_STR(s,uiLen,"SIP/2.0 400 Bad Request\r\n");
         // break;
      case 404:
         ADD_STR(s,uiLen,"SIP/2.0 404 Not found\r\n");
         break;
      case 405:
         ADD_STR(s,uiLen,"SIP/2.0 405 Method not allowed\r\nAllow: INVITE,CANCEL,INFO,BYE,MESSAGE,ACK,OPTIONS\r\n");
         break;
      case 415:
         //uiLen+=sprintf(s+uiLen,"SIP/2.0 415 Unsupported Media Type\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 415 Unsupported Media Type\r\n");
         break;
      case 480:
         //         uiLen+=sprintf(s+uiLen,"SIP/2.0 480 Temporarily not available\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 480 Temporarily not available -Try later\r\n");
         break;
      case 481:
         //  uiLen+=sprintf(s+uiLen,"SIP/2.0 481 Call Leg/Transaction Does Not Exist\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 481 Call Leg/Transaction Does Not Exist\r\n");
         break;
      case 486:
         //uiLen+=sprintf(s+uiLen,"SIP/2.0 486 Busy Here\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 486 Busy Here\r\n");
         break;
      case 487:
         //uiLen+=sprintf(s+uiLen,"SIP/2.0 486 Busy Here\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 487 Request Terminated\r\n");
         break;
      case 488:
         /// uiLen+=sprintf(s+uiLen,"SIP/2.0 488 Not Acceptable Here\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 488 Not Acceptable Here\r\n");
      case 491:
         ADD_STR(s, uiLen,"SIP/2.0 491 Request Pending\r\n");
         break;
      case 501:
         /// uiLen+=sprintf(s+uiLen,"SIP/2.0 488 Not Acceptable Here\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 501 Not Implemented\r\n");
         break;
      case 603:
         //uiLen+=sprintf(s+uiLen,"SIP/2.0 603 Decline\r\n");
         ADD_STR(s,uiLen,"SIP/2.0 603 Decline\r\n");
         break;
      default:
         ADD_STR(s,uiLen,"SIP/2.0 400 Bad Request\r\n");
   }

   /*
    ---SEND[1050] [46.109.169.1:51864]
    INVITE sip:1035@46.109.169.1:51864 SIP/2.0
    Via: SIP/2.0/TLS 173.255.141.47;branch=z9hG4bK-1035
    Via: SIP/2.0/UDP 173.192.209.158;branch=z9hG4bK-1035
    Via: SIP/2.0/UDP 173.255.141.47;branch=z9hG4bK-1036
    Via: SIP/2.0/TLS 46.109.169.1:58232;branch=z9hG4bK6CaeIN3620340945;rport=58338
    
    ---REC[579] [46.109.169.1:51864]
    SIP/2.0 180 Ringing
    Via: SIP/2.0/TLS 173.255.141.47;branch=z9hG4bK-1035
    Via: SIP/2.0/UDP 173.192.209.158;branch=z9hG4bK-1035
    Via: SIP/2.0/UDP 173.255.141.47;branch=z9hG4bK-1036
    Via: SIP/2.0/TLS 46.109.169.1:58232;branch=z9hG4bK6CaeIN3620340945
    */
   {

      struct HDR_VIA *v=&sMsg->hldVia.x[0];//sMsg->hldVia.uiCount-1];
      ADD_STR(s,uiLen,"Via: SIP/2.0/");
      ADD_DSTR(s,uiLen,v->dstrTransport);
      ADD_STR(s,uiLen," ");
      
      ADD_DSTR(s,uiLen,v->dstrViaAddr);
      if(v->dstrPort.uiLen){
         ADD_STR(s,uiLen,":");
         ADD_DSTR(s,uiLen,v->dstrPort);
      }
      if(v->dstrBranch.uiLen){
         ADD_STR(s,uiLen,";branch=");
         ADD_DSTR(s,uiLen,v->dstrBranch);
      }
      
      if(id==METHOD_ACK){
         ADD_STR(s,uiLen,";rport\r\n");
      }
      else{
         //TODO if rport send rport resp
         ADD_STR(s,uiLen,"\r\n");
      }
   }
   
   if(id!=METHOD_ACK)
   {
      //TODO fix
      for(ui=1;ui<sMsg->hldVia.uiCount;ui++)
         ADD_DSTRCRLF(s,uiLen,sMsg->hldVia.x[ui].dstrFullRow);
      
   }

   if(sMsg->hldRecRoute.uiCount)
   {
      uiLen+=addRoute(s+uiLen, 256,  &sMsg->hldRecRoute,"Record-Route: ",1,0,NULL);
   }
   
   
   ADD_DSTRCRLF(s,uiLen,sMsg->hdrFrom.dstrFullRow);
   
   if (sMsg->hdrTo.dstrTag.uiLen==0 && spSes && spSes->str16Tag.uiLen){
      
      if(spSes && cfg && id!=METHOD_ACK && cfg->user.nick[0]){
         uiLen+=sprintf(s+uiLen,"To: \"%s\" ",cfg->user.nick);
      }
      else{
         ADD_STR(s,uiLen,"To: ");
      }
      
      uiLen+=sprintf(s+uiLen,"<%.*s>;tag=%.*s\r\n"
                     ,D_STR(sMsg->hdrTo.sipUri.dstrSipAddr)
                     ,D_STR(spSes->str16Tag));
   }
   else
   {
      if(spSes && cfg && id!=METHOD_ACK){
         if(cfg && cfg->user.nick[0]){
            uiLen+=sprintf(s+uiLen,"To: \"%s\" ",cfg->user.nick);
         }
         else {
            ADD_STR(s,uiLen,"To: ");
         }
         uiLen+=sprintf(s+uiLen,"<%.*s>;tag=%.*s\r\n",D_STR(sMsg->hdrTo.sipUri.dstrSipAddr),D_STR(spSes->str16Tag));
         
      }
      else{
         ADD_DSTRCRLF(s,uiLen,sMsg->hdrTo.dstrFullRow);
      }
   }
   
   if(id==METHOD_ACK)
   {
      ADD_0_STR(s,uiLen,"CSeq: ");
      ADD_DSTR(s,uiLen,sMsg->hdrCSeq.dstrID);
      ADD_0_STR(s,uiLen," ACK\r\nMax-forwards: 10\r\n");
   }
   else
   {
      ADD_DSTRCRLF(s,uiLen,sMsg->hdrCSeq.dstrFullRow);
   }

   if (sMsg->hdrCSeq.uiMethodID & METHOD_OPTIONS)
   {
      ADD_STR(s,uiLen,"Allow: INVITE,CANCEL,INFO,BYE,ACK,OPTIONS\r\n");
      ADD_STR(s,uiLen,"Accept: application/sdp ,text/plain\r\n");
   }
   
   ADD_0_STR(s,uiLen,"Call-ID: ");ADD_DSTRCRLF(s,uiLen,sMsg->dstrCallID);
   
   if (spSes && sMsg->hdrCSeq.uiMethodID & (METHOD_INVITE|METHOD_MESSAGE|METHOD_OPTIONS))
   {
      
      if(cfg && cfg->user.nick[0]){
         uiLen+=sprintf(s+uiLen,"Contact: \"%s\" <sip:",cfg->user.nick);//sUserCfg.szUserName);
      }
      else {ADD_STR(s,uiLen,"Contact: <sip:");}
      
      if(cfg && cfg->isOnline())
      {
         if(*cfg->user.nr)
            uiLen+=sprintf(s+uiLen,"%s@",cfg->user.nr);
         else if(*cfg->user.un)
            uiLen+=sprintf(s+uiLen,"%s@",cfg->user.un);
      }
      
      
      ADD_DSTR(s,uiLen,(*spSes->pIPVisble));
      
      if(spSes->uiUserVisibleSipPort!=DEAFULT_SIP_PORT)
         uiLen+=sprintf(s+uiLen,":%u",spSes->uiUserVisibleSipPort);
      
      if(iIsTCP) ADD_STR(s,uiLen,";transport=tcp>\r\n")else
         if(iIsTLS) ADD_STR(s,uiLen,";transport=tls>\r\n") else
            ADD_STR(s,uiLen,">\r\n")
   }
   
   return 0;
}
int CMakeSip::addContent16(const char *type, CTStrBase *e)
{
   spSes->sSendTo.pContentType=type;
   iContentAdded=1;
   char *s=buf;
   
   ADD_0_STR(s,uiLen,"Content-Type: ");
   ADD_0_STR(s,uiLen,type);
   ADD_CRLF(s,uiLen);
   
   ADD_0_STR(s,uiLen,"Content-Length:       \r\n\r\n");
   
   
   uiPosToContentLen=uiLen-8;
   uiPosContentStart=uiLen;
   memcpy(s+uiLen,e->getText(),e->getLen()*sizeof(short));
   uiLen+=e->getLen()*sizeof(short);
   
   
   addContent();//
   
   return 0;
}

int CMakeSip::addContent8(const char *type, CTStrBase *e)
{
   spSes->sSendTo.pContentType=type;
   iContentAdded=1;
   char *s=buf;
   
   ADD_0_STR(s,uiLen,"Content-Type: ");
   ADD_0_STR(s,uiLen,type);
   ADD_CRLF(s,uiLen);
   
   ADD_0_STR(s,uiLen,"Content-Length:       \r\n\r\n");
   
   
   uiPosToContentLen=uiLen-8;
   uiPosContentStart=uiLen;
   convert16to8(s+uiLen,e->getText(),e->getLen());
   uiLen+=e->getLen();
   
   
   addContent();//
   
   return 0;
}
int CMakeSip::addContent(const char *type, char *pCont, int iContLen)
{
   iContentAdded=1;
   char *s=buf;
   spSes->sSendTo.pContentType=type;
   
   ADD_0_STR(s,uiLen,"Content-Type: ");
   ADD_0_STR(s,uiLen,type);
   ADD_CRLF(s,uiLen);
   
   ADD_0_STR(s,uiLen,"Content-Length:       \r\n\r\n");
   
   
   uiPosToContentLen=uiLen-8;
   uiPosContentStart=uiLen;
   if(iContLen)
   {
      ADD_L_STR(s,uiLen,pCont,iContLen);
   }
   else
   {
      ADD_0_STR(s,uiLen,pCont);
   }
   addContent();//
   return 0;
}
int CMakeSip::makeSdpHdr(char *pIP_Orig, int iIPLen)
{
   iContentAdded=1;
   char *s=buf;
   
   ADD_0_STR(s,uiLen,"Content-Type: application/sdp\r\nContent-Length:        ");
   
   uiPosToContentLen=uiLen-6;
   
   uiPosContentStart=uiLen+4;
   //iLen += sprintf(s + iLen, "%u",4);
   
   ADD_0_STR(s,uiLen,"\r\n\r\nv=0\r\no=root 1 2 IN IP4 ");
   ADD_L_STR(s,uiLen,pIP_Orig,iIPLen);
   ADD_CRLF(s,uiLen);
   return 0;
}
int CMakeSip::addSdpConnectIP(char *pIP_Cont, int iIPLen, PHONE_CFG *cfg)
{
   char *s=buf;
   if(cfg->szUASDP[0]){
      ADD_0_STR(s,uiLen,"s=");
      ADD_0_STR(s,uiLen,cfg->szUASDP);
      ADD_0_STR(s,uiLen,"\r\nc=IN IP4 ");
   }
   else{
      //ADD_0_STR(s,uiLen,szUA);ADD_CRLF;
#if defined(_WIN32) && !defined(_WIN32_WCE)
      ADD_0_STR(s,uiLen,"s=A SIP call - WinPC\r\nc=IN IP4 ");
#elif defined(_WIN32_WCE)
      ADD_0_STR(s,uiLen,"s=A SIP call - WinMob\r\nc=IN IP4 ");
#elif defined(__SYMBIAN32__)
      ADD_0_STR(s,uiLen,"s=A SIP call - Symbian\r\nc=IN IP4 ");
#elif defined(__arm__) || defined(ARM)
      //sds
      ADD_0_STR(s,uiLen,"s=A SIP call - TiviMob\r\nc=IN IP4 ");
#elif defined(__APPLE__)
      ADD_0_STR(s,uiLen,"s=A SIP call - TiviApple\r\nc=IN IP4 ");
#else
      ADD_0_STR(s,uiLen,"s=A SIP call - TIVI\r\nc=IN IP4 ");
#endif
   }
   ADD_L_STR(s,uiLen,pIP_Cont,iIPLen);
   ADD_0_STR(s,uiLen,"\r\nt=0 0\r\n");
   return 0;
}
int CMakeSip::addSdpMC(char *mediaName, unsigned int uiPort, char *pIP_Cont, int iIPLen)
{
   char *s=buf;
   ADD_0_STR(s,uiLen,"s=A SIP call\r\nc=IN IP4 ");
   ADD_L_STR(s,uiLen,pIP_Cont,iIPLen);
   ADD_0_STR(s,uiLen,"\r\nt=0 0\r\nm=");
   uiLen += sprintf(s + uiLen, "%s %u ",mediaName, uiPort);
   return 0;
}

int CMakeSip::addSdpUdptl(int iMediaType, int iPort, char *pIP_Cont, int iIPLen)
{
   /*
    v=  (protocol version)
    o=  (owner/creator and session identifier).
    s=  (session name)
    i=* (session information)
    u=* (URI of description)
    e=* (email address)
    p=* (phone number)
    c=* (connection information - not required if included in all media)
    b=* (bandwidth information)One or more time descriptions (see below)
    z=* (time zone adjustments)
    k=* (encryption key)
    a=* (zero or more session attribute lines)Zero or more media descriptions (see below)
    t=  (time the session is active)
    r=* (zero or more repeat times)
    */
   char *s=buf;
   ADD_0_STR(s,uiLen,"s=A SIP call\r\nc=IN IP4 ");
   ADD_L_STR(s,uiLen,pIP_Cont,iIPLen);
   ADD_0_STR(s,uiLen,"\r\nt=0 0\r\nm=image ");
   uiLen += sprintf(s + uiLen, "%u udptl t38",iPort);
   
   uiLen += sprintf(s + uiLen,
                    "a=T38FaxVersion:0\r\n"
                    "a=T38MaxBitRate:7200\r\n"
                    "a=T38FaxFillBitRemoval:0\r\n"
                    "a=T38FaxTranscodingMMR:0\r\n"
                    "a=T38FaxTranscodingJBIG:0\r\n"
                    "a=T38FaxRateManagement:transferredTCF\r\n"
                    "a=T38FaxMaxBuffer:200\r\n"
                    "a=T38FaxMaxDatagram:72\r\n"
                    "a=T38FaxUdpEC:t38UDPRedundancy\r\n"
                    

                    );
   
   return 0;
}

int CMakeSip::addSdpMC(int iMediaType, int iPort, char *pIP_Cont, int iIPLen, SDP *my, SDP *dest)
{

   
   return 0;
}


