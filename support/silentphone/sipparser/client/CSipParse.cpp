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



///ADD to ren sipUri parse, contact params /contact 
#include "CTSip.h"

#define TOKENS_IN_LINE \
for (j=0,i=0;(strList[j=tokensParsed+i].iFirst!=10 || strList[j].iLast!=58);i++)\
{\
   if (j==n+1) \
   {\
      parsed=TRUE;\
      break;\
   }\
}



#define printError(A,B) {puts(A);if ((B)==DROP) dropPacket=TRUE;}
int CSip::parse(char *pFrom)
{
   tokensParsed = 0;
   parsed = FALSE;
   dropPacket = FALSE;
   
   if(n>MAX_TOKEN_COUNT)n=MAX_TOKEN_COUNT;
   
   memset(strList,0,(sizeof (DSTR))*(n+2));
   
   n=0;
   
   splitSIP(pFrom);
   
   
   if (n==DROP) printError("Error splitLine!",DROP);
   
   if (iSipHasFirstLine && n<MIN_TOKEN_COUNT) 
   {
      // printf("[%.*s]",300,pFrom);
      printError("Error too few tokens!",DROP);
   }
   
   if (dropPacket==TRUE)return DROP;
   
   if(iSipHasFirstLine)
      parseFirstLine();
   
   while (parsed==FALSE && dropPacket==FALSE)
   {
      if (tryParseHdr()==UNKNOWN)
      {
         tokensParsed++;
         parseUnknown();         
         continue;
      }
   }
   if (
       gspSIPMsg->uiOffset-MSG_BUFFER_TAIL_SIZE-gspSIPMsg->uiBytesParsed-1!=gspSIPMsg->dstrContLen.uiVal  
       )
   {
      printError("Error invalid content len",DROP);
      
      dropPacket=TRUE;
   }
   pContent=gspSIPMsg->rawDataBuffer+gspSIPMsg->uiBytesParsed+1;
   
   if (dropPacket==TRUE)return DROP;
   
   return 0;
}


/*
 #define TRYPARSE(MET,HDR,L,X) if(CMP(strList[tokensParsed],HDR,L))\
 {tokensParsed++;return parse##MET(X);}
 
 TRYPARSE(FromTo,"TO",2,&gspSIPMsg->hdrTo);
 TRYPARSE(FromTo,"FROM",4,&gspSIPMsg->hdrFrom);
 TRYPARSE(Via,"VIA",3, );
 TRYPARSE(CSeq,"CSEQ",4, );
 TRYPARSE(Contact,"CONTACT",7, );
 */
const char  CSip::call_id[]  =     "CALL\rID";
const char  CSip::call_rate[]=     "CALL\rRATE";

const char  CSip::content_type[]=  "CONTENT\rTYPE";
const char  CSip::content_length[]="CONTENT\rLENGTH";
const char  CSip::max_forwards[]=  "MAX\rFORWARDS";
const char  CSip::proxy_authen[]=  "PROXY\rAUTHENTICATE";
const char  CSip::proxy_author[]=  "PROXY\rAUTHORIZATION";
const char  CSip::WWW_aut[]   =    "WWW\rAUTHENTICATE";
const char  CSip::rec_rout[]    =  "RECORD\rROUTE";

int CSip::tryParseHdr()
{
   
   //static char * cont_encod=       "CONTENT\rENCODING";
   //ContentEncoding
   
   switch(strList[tokensParsed].uiLen)
   {
      case 0:return UNKNOWN;
      case 1:return UNKNOWN;
      case 2:
         if  (CMP(strList[tokensParsed],"TO",2))
         {tokensParsed++;parseFromTo(&gspSIPMsg->hdrTo);           return 0;}
         return UNKNOWN;
      case 3:
         if  (CMP(strList[tokensParsed],"VIA",3))
         {tokensParsed++;parseVia();           return 0;}
         return UNKNOWN;
      case 4:
         if (CMP(strList[tokensParsed],"CSEQ",4))
         { tokensParsed++;  parseCSeq();           return 0;}
         if  (CMP(strList[tokensParsed],"FROM",4))
         {tokensParsed++;parseFromTo(&gspSIPMsg->hdrFrom);           return 0;}
         return UNKNOWN;
         /*
          case 5:
          if (CMP(strList[tokensParsed],"ALLOW",5))
          {tokensParsed++;parseAllow();           return 0;}
          if  (CMP(strList[tokensParsed],"ROUTE",5))
          {tokensParsed++;parseRoutes(&gspSIPMsg->hldRoute);  return 0;}
          return UNKNOWN;
          */
      case 5:
         if (CMP(strList[tokensParsed],"EVENT",5))
         {tokensParsed++; parseEvent();           return 0;}
         return UNKNOWN;
      case 6:return UNKNOWN;
      case 7:
         if (CMP(strList[tokensParsed],call_id,7))
         {tokensParsed++; parseCallID();           return 0;}
         if (CMP(strList[tokensParsed],"CONTACT",7))
         {tokensParsed++; parseContact();           return 0;}
         if (CMP(strList[tokensParsed],"EXPIRES",7))
         {tokensParsed++;parseMF_EXP_CL(&gspSIPMsg->dstrExpires);           return 0;}
         // if  (CMP(strList[tokensParsed],"REQUIRE",7))
         //   {tokensParsed++;parseSupportReq(&gspSIPMsg->hldRequire);  return 0;}
         return UNKNOWN;
         /*
          case 8:
          if  (CMP(strList[tokensParsed],"SUPORTED",8))                    /// ???
          {tokensParsed++;parseSupportReq(&gspSIPMsg->hldSupprted);           return 0;}
          return UNKNOWN;
          */
      case 9:
         if  (CMP(strList[tokensParsed],call_rate,9))
         {tokensParsed++; parseCallRate();           return 0;}
         //    if  (CMP(strList[tokensParsed],call_info,9))
         //   {tokensParsed++; parseCallInfo();           return 0;}
         return UNKNOWN;
         /*
          case 10:
          if  (CMP(strList[tokensParsed],"UNSUPORTED",12))                    /// ???
          {tokensParsed++;parseSupportReq(&gspSIPMsg->hldUnSupp);           return 0;}
          return UNKNOWN;*/
      case 11:return UNKNOWN;
      case 12:
         if  (CMP(strList[tokensParsed],max_forwards,12))                    /// ???
         {tokensParsed++;parseMF_EXP_CL(&gspSIPMsg->dstrMaxForw);           return 0;}
         if (CMP(strList[tokensParsed],content_type,12))
         {tokensParsed++; parseContentType();           return 0;}
         if  (CMP(strList[tokensParsed],rec_rout,12))
         {tokensParsed++;parseRoutes(&gspSIPMsg->hldRecRoute);  return 0;}
         if  (CMP(strList[tokensParsed],"PORTABILLING",12))
         {tokensParsed++;parsePortaBilling();  return 0;}
         return UNKNOWN;
      case 13:
         if  (CMP(strList[tokensParsed],"AUTHORIZATION",13))
         {tokensParsed++;parseAut(&gspSIPMsg->hdrAuthoriz);  return 0;}//parseAuthorization(); 
         return UNKNOWN;
      case 14:
         if (CMP(strList[tokensParsed],content_length,14))
         {tokensParsed++; parseMF_EXP_CL(&gspSIPMsg->dstrContLen);           return 0;}
         return UNKNOWN;
      case 15:return UNKNOWN;
      case 16:
         if  (CMP(strList[tokensParsed],WWW_aut,16))
         {tokensParsed++;parseAut(&gspSIPMsg->hdrWWWAuth);           return 0;}
         //  if  (CMP(strList[tokensParsed],cont_encod,16))
         //    {tokensParsed++;parseContentEncoding();           return 0;}//TODO single params parse
         return UNKNOWN;
      case 18:
         if (CMP(strList[tokensParsed],proxy_authen,18))
         {tokensParsed++;parseAut(&gspSIPMsg->hdrProxyAuthen) ;         return 0;}//parseProxyAuthenticate();           return 0;}
         return UNKNOWN;
      case 19:
         if (CMP(strList[tokensParsed],proxy_author,19))
         {tokensParsed++;parseAut(&gspSIPMsg->hdrProxyAuthor);    return 0;}//parseProxyAuthenticate();           return 0;}
         return UNKNOWN;
         
   }
   return UNKNOWN;
}


#define FLAG60 1
#define FLAG34 2
//char bufHex[256];
int CSip::splitSIP(char *pFrom)
{
   int flag=0;
   register char * cur=pFrom;
   //pFrom must be zero terminated
   
   
   strList[n].uiLen=0;
   strList[n].strVal=cur;
   
   for (;;cur++)
   {
      if (*cur>65) 
      {
         continue;
      }
      switch(cur[0])
      {
         case 0: case 1: case 2: case 3: case 4:case 5: case 6: case 7: case 8:
         case 11:case 12:        case 14:case 15: case 16: case 17: case 18:case 19:
         case 20:case 21:case 22:case 23:case 24:case 25: case 26: case 27: case 28:case 29:
         case 30:case 31:
         {  
            printError("0,1,2",DROP); 
            return DROP;
         }
         case 34:
            cur++;
            strList[n].strVal=cur;
            while(*cur!=0 && *cur!='"')cur++;
            if(*cur==0){
               printError("*cur==0",DROP);
               return DROP;
            }
            strList[n].iFirst=34;
            strList[n].iLast=34;
            strList[n].uiLen=cur-strList[n].strVal;//
            n++;
            strList[n].strVal=cur+1;
            strList[n].iFirst=34;
            continue;

         case 60:
         
            flag=FLAG60 ;//flag|=FLAG60 ;
            if (cur!=strList[n].strVal)//>0)//
            {
               strList[n].uiLen=cur-strList[n].strVal;//
               n++;
            }
            strList[n].iFirst=60;
            strList[n].strVal=cur+1;
            
            continue;
         case 62: 

            flag=0 ;
            strList[n].iLast=62;
            if (cur!=strList[n].strVal)
            {
               strList[n].iLast=62;
               strList[n].uiLen=cur-strList[n].strVal;//
               n++;
            }
            strList[n].strVal=cur+1;
            
            continue;
            //case 59:
         case 47:

            if(flag)continue;
         case '\n':
  
            if ((*(cur+1)=='\n') || ((*(cur+1)=='\r') && (*(cur+2)=='\n'))) 
            {
               strList[n].uiLen=cur-strList[n].strVal;
               if (cur==strList[n].strVal)n--;//
               //                     if (strList[n].uiLen==0) n--;
               if (strList[n].iLast==0) 
                  strList[n].iLast=cur[0];
               if (flag) return DROP;
               gspSIPMsg->uiBytesParsed=cur-gspSIPMsg->rawDataBuffer+2;
               return n;
            }
         case 9:
         case 13:
         case 32://
         case 38:
         case 44:
         case 58:
         case 59://
         case 61:
         case 63:
         case 64:
            
            if (cur!=strList[n].strVal)
            {
               if (strList[n].iLast==0) strList[n].iLast=cur[0];
               
               strList[n].uiLen=cur-strList[n].strVal;//
               n++;
               strList[n].iFirst=cur[0];
            }
			   strList[n].strVal=cur+1;
            
            if (cur[0]>32 || cur[0]==10)
            { 
               strList[n].iFirst=cur[0];
               //               if (cur[0]!=10) strList[n-1].iLast=cur[0];
               if (strList[n-1].iLast<33)
                  strList[n-1].iLast=cur[0]; 
            }
            continue;
      }      
   }
   printError("ERROR: ilegal end of buffer-(new line not found)",DROP);
   return DROP;
}

//NEW--------->>---

int CSip::parseSipUri(SIP_URI * sipUri, int j,int i)
{

   sipUri->dstrSipAddr=strList[j]; 
   int iMaddrFound=0;
   {
      int iHostItem=j+1;
      int ok=0;
      int un=0;
      if (strList[j].iFirst=='<')
      {
         for (;strList[j].iLast!='>';j++)
         {
            if(!ok && strList[j+1].iFirst=='@')
            {
               ok=1;
               un=iHostItem;
               iHostItem=j+1;
            }
            if(ok && strList[j].iLast=='=' && CMP(strList[j],"MADDR",5) ){
               sipUri->maddr.iFound=iMaddrFound=1;
               j++;
               sipUri->maddr.dstrAddr=strList[j];
               if(strList[j].iLast==':'){j++;sipUri->maddr.dstrPort=strList[j];}
               
            }
            if (j>=i+tokensParsed) {printError("ERROR: parseSipUri '>' not found!",DROP);return DROP;}
         }
      }
      else
      {
         for (;strList[j+1].iFirst!=';' && strList[j].iLast>32;j++)  
         {
            if(!ok && strList[j+1].iFirst=='@')
            {
               ok=1;
               un=iHostItem;
               iHostItem=j+1;
            }
            if (j>=i+tokensParsed) {printError("ERROR: parseSipUri!",DROP);return DROP;}
         }
      }
      if(n<j){printError("ERROR: parseSipUri!",DROP);return DROP;}
      
      if(un)
      {
         sipUri->dstrUserName=strList[un];
      }
      sipUri->dstrHost=strList[iHostItem];
      //DEBUG(strList[iHostItem].uiLen,strList[iHostItem].strVal);
      if(sipUri->dstrHost.strVal[0]>='0' && sipUri->dstrHost.strVal[0]<='9')
      {
         sipUri->dstrHost.uiVal=ipstr2long(D_STR(sipUri->dstrHost));
         if(sipUri->dstrHost.uiVal)
            SWAP_INT(sipUri->dstrHost.uiVal);
      }
      if(strList[iHostItem].iLast==':')
      {
         sipUri->dstrPort=strList[iHostItem+1];
         STR_TO_I( sipUri->dstrPort.uiVal, sipUri->dstrPort.strVal );
      }
      else sipUri->dstrPort.uiVal=DEAFULT_SIP_PORT;
      if(iMaddrFound){
         sipUri->maddr.dstrAddr.uiVal=ipstr2long(D_STR(sipUri->maddr.dstrAddr));
         if(sipUri->maddr.dstrAddr.uiVal)
            SWAP_INT(sipUri->maddr.dstrAddr.uiVal);
         
         if(sipUri->maddr.dstrPort.strVal && sipUri->maddr.dstrPort.uiLen<6)
         {
            STR_TO_I( sipUri->maddr.dstrPort.uiVal, sipUri->maddr.dstrPort.strVal );
         }
         else sipUri->maddr.dstrPort.uiVal=DEAFULT_SIP_PORT;
      }
      
   }
   sipUri->dstrSipAddr.uiLen=strList[j].strVal-sipUri->dstrSipAddr.strVal+strList[j].uiLen;
   sipUri->dstrSipAddr.iLast=strList[j].iLast;
   j++;
   return j;
}


int CSip::parseFirstLine()
{ 
   int i,j;
   tokensParsed=0;
   TOKENS_IN_LINE
   
   
   if (i<3) {printError("First Line Error ",DROP); return DROP;}
   
   tokensParsed+=i;
   
   if (CMP(strList[0],"SIP",3))
   {
      if (strList[1].iFirst=='/') gspSIPMsg->sipHdr.dstrSipVer=strList[1]; 
      else  {printError("Error in ver",DROP); return DROP;}
      
      if (strList[2].uiLen>3) {printError("Error in FL status code",DROP); return DROP;}
      gspSIPMsg->sipHdr.dstrStatusCode=strList[2];
      STR_TO_I(gspSIPMsg->sipHdr.dstrStatusCode.uiVal, strList[2].strVal );
      //gspSIPMsg->sipHdr.dstrStatusCode.uiVal=strtoul((strList[2].strVal),NULL,0);
      
      if ((999<gspSIPMsg->sipHdr.dstrStatusCode.uiVal) || (gspSIPMsg->sipHdr.dstrStatusCode.uiVal<99)) 
      {printError("Error in FL status code",DROP); return DROP;}
      if (strList[3].iFirst==' ') 
      {
         gspSIPMsg->sipHdr.dstrReasonPhrase=strList[3];
         // for (i=3;i<(j-1);i++){}
         //gspSIPMsg->sipHdr.dstrReasonPhrase.uiLen=strList[i].strVal-strList[3].strVal+strList[i].uiLen;
         gspSIPMsg->sipHdr.dstrReasonPhrase.uiLen=strList[j-1].strVal-strList[3].strVal+strList[j-1].uiLen;
      }
      
      gspSIPMsg->sipHdr.iFlag|=SIP_INTERACTION_RESPONSE;
   }
   if (gspSIPMsg->sipHdr.iFlag==0) 
   {
      if  (!(CMP(strList[i-2],"SIP",3))) {printError("Error in first line!",DROP); return DROP;}
      gspSIPMsg->sipHdr.dstrSipVer=strList[i-1];
      
      gspSIPMsg->sipHdr.uiMethodID=0;
      
      if (strList[2].iFirst==':')
      {
         if (CMP(strList[1],"SIP",3) || CMP(strList[1],"SIPS",4))// ((CMP(strList[1],"SIP",3)) || (CMP(strList[1],"SIPS",4)))
         {
            switch(*strList[0].strVal)
            {
#define CMPM(DS,N,L,DST) if(CMP(DS,#N,L)){(DST).uiMethodID|=METHOD_##N;break;}
               case 'I': 
                  CMPM(strList[0],INVITE,6,gspSIPMsg->sipHdr);
                  CMPM(strList[0],INFO,4,gspSIPMsg->sipHdr);
                  break;
               case 'M': 
                  CMPM(strList[0],MESSAGE,7,gspSIPMsg->sipHdr);
                  break;
               case 'N': 
                  CMPM(strList[0],NOTIFY,6,gspSIPMsg->sipHdr);
                  break;
               case 'U': 
                  CMPM(strList[0],UPDATE,6,gspSIPMsg->sipHdr);
                  break;
               case 'A': 
                  CMPM(strList[0],ACK,3,gspSIPMsg->sipHdr);
                  break;
               case 'B': 
                  CMPM(strList[0],BYE,3,gspSIPMsg->sipHdr);
                  break;
               case 'C': 
                  CMPM(strList[0],CANCEL,6,gspSIPMsg->sipHdr);
                  break;
               case 'S': 
                  CMPM(strList[0],SUBSCRIBE,9,gspSIPMsg->sipHdr);
                  break;
               case 'P': 
                  CMPM(strList[0],PUBLISH,7,gspSIPMsg->sipHdr);
                  break;
               case 'R': 
                  CMPM(strList[0],REGISTER,8,gspSIPMsg->sipHdr);
                  CMPM(strList[0],REFER,5,gspSIPMsg->sipHdr);
                  break;
               case 'O': 
                  CMPM(strList[0],OPTIONS,7,gspSIPMsg->sipHdr);
                  break;
               default:
                  if  (gspSIPMsg->sipHdr.uiMethodID==0){printError("Error :unknown method",DONT_DROP); return DROP;}
            }
            gspSIPMsg->sipHdr.iFlag|=SIP_INTERACTION_REQUEST;
            
            j=parseSipUri(&gspSIPMsg->sipHdr.sipUri, 1, i);
            if(j<0){printError("ERROR hdr",j);return j;}
         } 
      }
      if  (gspSIPMsg->sipHdr.iFlag==0){printError("Error in first line!",DROP); return DROP;}
   }
   gspSIPMsg->sipHdr.dstrFullRow.strVal=strList[0].strVal;
   gspSIPMsg->sipHdr.dstrFullRow.uiLen=strList[i-1].strVal-strList[0].strVal+strList[i-1].uiLen;
   
   return 0; 
}

int CSip::parseAccept(){ return 0; }
int CSip::parseAcceptEncoding(){ return 0; }
int CSip::parseAcceptLanguage(){ return 0; }
int CSip::parseAlertInfo(){ return 0; }

int CSip::parseAllow()
{
   int i,j;
   
   TOKENS_IN_LINE
   
   if (i==0) {printError("Error: Allow not found:",DROP); return DROP;}
   
   for (j=tokensParsed;j<tokensParsed+i;j++)
   {
      
      switch(strList[j].uiLen)
      {
         case 0:break;
         case 1:break;
         case 2:break;
         case 3:
            if (CMP(strList[j],"BYE",3)){ gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_BYE;break;}
            if (CMP(strList[j],"ACK",3)) gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_ACK;
            break;
         case 4:
            if (CMP(strList[j],"INFO",4)) gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_INFO;
            break;
         case 5:
            if (CMP(strList[j],"REFER",5)){ gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_REFER;break;}
            break;
         case 6:
            if (CMP(strList[j],"INVITE",6)){ gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_INVITE;break;}
            if (CMP(strList[j],"CANCEL",6)){ gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_CANCEL;break;}
            if (CMP(strList[j],"NOTIFY",6)){ gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_NOTIFY;break;}
            if (CMP(strList[j],"UPDATE",6)) gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_UPDATE;
            break;
         case 7: 
            if (CMP(strList[j],"MESSAGE",7)){ gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_MESSAGE;break;}
            if (CMP(strList[j],"OPTIONS",7)) gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_OPTIONS;
            break;
         case 8: 
            if (CMP(strList[j],"REGISTER",8)) gspSIPMsg->uiAllowHdrMethodFlags|=METHOD_REGISTER;
            break;
      }
      
   }
   if (gspSIPMsg->uiAllowHdrMethodFlags==0) {printError("Error: Allow methods not found!",DONT_DROP); return DROP;}
   
   tokensParsed+=i;
   
   return 0; 
}

int CSip::parseAuthenticationInfo(){ return 0; }
//AUTH
int CSip::parseAut(HDR_AUT * aut)
{
   int i,j;
   TOKENS_IN_LINE
   j=tokensParsed;
   
   if (i==0) {printError("Error in Authorization",DROP); return DROP;}
   
   
   aut->dstrFullRow=strList[j-1];
   aut->dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   
   if (CMP(strList[j],"DIGEST",6)==0) {printError("Error in authorization",DROP); return DROP;}
   j++;
   for(;j<tokensParsed+i;j++)
   {
      
#define CMPAT(DS,N,L,F,DST) if(CMP(DS,#N,L))\
{\
j++;\
aut->DST=DS;\
aut->iFlag|=F;\
continue;\
}
      
      switch(strList[j].strVal[1])
      {         
         case 'c':
         case 'C':
            CMPAT(strList[j],NC,2,1,dstrNonceCount);
            continue;
         case 'e': 
         case 'E': 
            CMPAT(strList[j],REALM,5,2048,dstrRealm);
            CMPAT(strList[j],RESPONSE,8,2,dstrResponse);
            continue;
         case 'l':
         case 'L':
            CMPAT(strList[j],ALGORITHM,9,4,dstrAlgo);
            continue;
         case 'n':
         case 'N':
            CMPAT(strList[j],CNONCE,6,8,dstrCnonce);
            continue;
         case 'o':
         case 'O':
            CMPAT(strList[j],NONCE,5,16,dstrNonce);
            CMPAT(strList[j],QOP,3,32,dstrQOP);
            CMPAT(strList[j],DOMAIN,6,64,dstrDomain);
            continue;
         case 'p':
         case 'P':
            CMPAT(strList[j],OPAQUE,6,128,dstrOpaque);
            continue;
         case 'r':
         case 'R':
            CMPAT(strList[j],URI,3,256,dstrURI);
            continue;
         case 's':
         case 'S':
            CMPAT(strList[j],USERNAME,8,512,dstrUsername);
            continue;
         case 't':
         case 'T':
            if (CMP(strList[j],"STALE",5))
            {
               if (CMP(strList[j+1],"TRUE",4))
               {
                  aut->iStale=TRUE;
                  aut->iFlag|=1024;
                  j++;
                  continue;
               }
               if (CMP(strList[j+1],"FALSE",5))
               {
                  aut->iStale=FALSE;
                  aut->iFlag|=1024;
                  j++;
                  continue;
               }
               printError("Error in wwwAuth:stale!",DROP); 
               return DROP;
            }
         default:
            printError("aut -unknown param",DONT_DROP);
      }
   }
   
   
   tokensParsed+=i;
   return 0; 
}
int CSip::parseCallID()
{
   int i,j;
   TOKENS_IN_LINE
   if (!i){printError("Error in call id",DROP); return DROP;}
   if (i==1) gspSIPMsg->dstrCallID=strList[tokensParsed];
   else
   {
      strList[tokensParsed].uiLen=strList[tokensParsed+i-1].strVal-strList[tokensParsed].strVal+strList[tokensParsed+i-1].uiLen;
      gspSIPMsg->dstrCallID=strList[tokensParsed];
   }
   
   tokensParsed+=i;
   return 0; 
}

int CSip::parseCallInfo()
{ 
   int i,j;
   
   TOKENS_IN_LINE
   
   
   if (i<3) {printError("Error in Call info >3 ",DROP); return DROP;}
   //   for (j=tokensParsed;j<tokensParsed+i;j++) printStr(strList[j]);
   j=tokensParsed;
   
   gspSIPMsg->hdrCallInfo.dstrFullRow=strList[j-1];
   gspSIPMsg->hdrCallInfo.dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   
   for (;j<tokensParsed+i;j++)
   {
      if ((strList[j].iFirst!='<') 
          || (strList[j+1].iLast!='>') 
          || (strList[j+2].iLast!='=')) {printError("Error in Call info!",DROP); return DROP;}
      
      if (CMP(strList[j],"HTTP",4))
      {
         if (CMP(strList[j+2],"PURPOSE",7))
         {
            if (!(gspSIPMsg->hdrCallInfo.iFlag & 1) && (CMP(strList[j+3],"INFO",4)))
            {
               gspSIPMsg->hdrCallInfo.dstrInfoAddr=strList[j+3];
               j+=3;
               gspSIPMsg->hdrCallInfo.iFlag|=1;
               continue;
            }
            if (!(gspSIPMsg->hdrCallInfo.iFlag & 2) && (CMP(strList[j+3],"ICON",4)))
            {
               gspSIPMsg->hdrCallInfo.dstrIconAddr=strList[j+3];
               j+=3;
               gspSIPMsg->hdrCallInfo.iFlag|=2;
               continue;
            }
            if (!(gspSIPMsg->hdrCallInfo.iFlag & 4) && (CMP(strList[j+3],"CARD",4)))
            {
               gspSIPMsg->hdrCallInfo.dstrCardAddr=strList[j+3];
               j+=3;
               gspSIPMsg->hdrCallInfo.iFlag|=4;
               continue;
            }
         }
      }
   }
   // printf("Call info flag==========%d===========\n",gspSIPMsg->hdrCallInfo.iFlag);
   tokensParsed+=i;
   return 0; 
}
int CSip::parseCallRate()
{
   int i,j;
   TOKENS_IN_LINE
   if (!i){printError("Error in  CallRate",DROP); return DROP;}
   if (i==1) gspSIPMsg->dstrCallRate=strList[tokensParsed];
   else
   {
      strList[tokensParsed].uiLen=strList[tokensParsed+i-1].strVal-strList[tokensParsed].strVal+strList[tokensParsed+i-1].uiLen;
      gspSIPMsg->dstrCallRate=strList[tokensParsed];
   }
   
   //DEBUG(gspSIPMsg->dstrCallRate.uiLen,gspSIPMsg->dstrCallRate.strVal);
   
   tokensParsed+=i;
   return 0; 
}

int CSip::contactParams(int i,int j)
{
   for (;j<tokensParsed+i;)
   {
      //      printf("last-1 %c first %c r[%u]\n",strList[j-1].iLast,strList[j].iFirst,gspSIPMsg->hldContact.uiCount);
      if(strList[j-1].iLast==',' || strList[j].iFirst=='<' || strList[j].iFirst=='"')break;
      if (CMP(strList[j],"EXPIRES",7))
      {
         gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrExpires=strList[j+1];
         if( strList[j+1].strVal[0]=='-')
         {printError("Error 2 in contact:EXPIRES!",DROP); return DROP;}
         
         STR_TO_I( gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrExpires.uiVal, strList[j+1].strVal );
         
         gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].iFlag|=8;
         j+=2;
      }
      else
         if (CMP(strList[j],"Q",1))
         {
            gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrQ=strList[j+1];
            gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrQ.fVal=(float)strtod((strList[j+1].strVal),NULL);
            
            if ((gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrQ.fVal>2) || (gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrQ.fVal<0))
            {
               printError("Error 2 in contact:q!",DROP); return DROP;
            }
            gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].iFlag|=4;
            j+=2;
         }
         else
         {
            j++;
            continue;
         }
   }
   return j;
}
int CSip::parseContact()
{ 
   int i,j,iLastToken;
   
   TOKENS_IN_LINE
   if (i==0) {printError("Error in Contact",DROP); return DROP;}
   
   j=tokensParsed;
   iLastToken=tokensParsed+i;
   
   gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrFullRow=strList[j-1];
   gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   if (strList[j+i-1].iLast=='>' || strList[j+i-1].iLast=='"')
      gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrFullRow.uiLen++;
   
   for (;j<iLastToken;)
   {
      if ((CMP(strList[j],"SIP",3) || CMP(strList[j],"SIPS",4)) && strList[j].iLast==':')
      {
         if (strList[j-1].iFirst==34)
            if (strList[j-1].iLast==34)
            {
               gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrName =strList[j-1];
               gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].iFlag|=1;
            }
         
         gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].iFlag|=2;
         
         j=parseSipUri(&gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].sipUri,  j,i);
         if(j<0){printError("ERROR: Contact",j);return j;}
      }
      else
         if (CMP(strList[j],"MAILTO",6)&& strList[j].iLast==':')
         {
            if (strList[j-1].iFirst==34)
               if (strList[j-1].iLast==34)
               {
                  gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrName=strList[j-1];
                  gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].iFlag|=1;
               }
            gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrMailAddr=strList[j];
            gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].dstrMailAddr.uiLen+=strList[j+1].uiLen+1+strList[j+2].uiLen+1;
            gspSIPMsg->hldContact.x[gspSIPMsg->hldContact.uiCount].iFlag|=2;
            j+=2;
         }
         else
         {
            j++;
            continue;
         }
      if(j<iLastToken)
      {
         j=contactParams(i,j);
      }
      
      if (gspSIPMsg->hldContact.uiCount<ARRAY_SIZE) 
         gspSIPMsg->hldContact.uiCount++;  // if count<ARRAY_SIZE
   }
   tokensParsed+=i;
   return 0; 
}
int CSip::parseContentDisposition(){ return 0; }

int CSip::parseContentEncoding(){ 
   int i,j;
   char des_3[]="3DES";
   TOKENS_IN_LINE
   j=tokensParsed;
   des_3[0]=19;//"3" & 0xdf
   
   if(i<1)
   {printError("Error: Content encoding",DROP); return DROP;}
   
   gspSIPMsg->dstrContEncoding=strList[j];
   if(CMP(strList[j],des_3,4))
   {
      //      DEBUG(0,"3-DES");
   }
   
   tokensParsed+=i;
   return 0; 
}
int CSip::parseContentLanguage(){ return 0; }



int CSip::parseContentType()
{
   int i,j;
   //  DEBUG(0,"Conttype info");
   
   TOKENS_IN_LINE
   // for (j=tokensParsed;j<tokensParsed+i;j++) printStr(strList[j]);
   j=tokensParsed;
   gspSIPMsg->hdrContType.dstrFullRow=strList[j-1];
   gspSIPMsg->hdrContType.dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   
   if (i<2) 
   {printError("Error: in contType",DROP); return DROP;}
#define CMPCT(DS,N,L) if(CMP(DS,#N,L)){gspSIPMsg->hdrContType.uiTypeID=CONTENT_TYPE_##N;break;}
   do{
      CMPCT(strList[j],APPLICATION,11) 
      CMPCT(strList[j],TEXT,4)
      CMPCT(strList[j],AUDIO,5)
      CMPCT(strList[j],VIDEO,5)
      CMPCT(strList[j],IMAGE,5)
      CMPCT(strList[j],MESSAGE,7)
      CMPCT(strList[j],MULTIPART,9)
   }while(0);
   
   gspSIPMsg->hdrContType.dstrSubType=strList[j+1];
   
   if (i>3)
   {
      if ((strList[j+2].iFirst==';') && (strList[j+2].iLast=='='))
      {
         gspSIPMsg->hdrContType.dstrValue=strList[j+3];
         gspSIPMsg->hdrContType.dstrAttrib=strList[j+2];
      }
   }   
   
   //   printf("flag=%d, m sub-type %.*s\n",gspSIPMsg->hdrContType.mTypeID,gspSIPMsg->hdrContType.mSubType.uiLen,gspSIPMsg->hdrContType.mSubType);
   
   tokensParsed+=i;
   return 0; 
}

int CSip::parseCSeq()
{ 
   int i,j;
   
   gspSIPMsg->hdrCSeq.uiMethodID=0;
   
   TOKENS_IN_LINE
   // DEBUG(0,"COntact info");
   //  for (j=tokensParsed;j<tokensParsed+i;j++) printStr(strList[j]);
   
   gspSIPMsg->hdrCSeq.dstrFullRow=strList[tokensParsed-1];
   gspSIPMsg->hdrCSeq.dstrFullRow.uiLen=strList[tokensParsed+i-1].strVal-strList[tokensParsed-1].strVal+strList[tokensParsed+i-1].uiLen;
   //j=tokensParsed;
   
   if (i!=2) 
   {printError("Error: CSeq not found:",DROP); return DROP;}
   
   if (strList[tokensParsed].uiLen>10) {printError("Error in CSeq!",DROP); return DROP;}
   
   if(strList[tokensParsed].strVal[0]=='-')
   {printError("Error in CSeq (token starts with '-')",DROP); return DROP;}
   
   gspSIPMsg->hdrCSeq.dstrID=strList[tokensParsed];
   //gspSIPMsg->hdrCSeq.dstrID.uiVal=strtoul((strList[tokensParsed].strVal),NULL,0);
   STR_TO_I( gspSIPMsg->hdrCSeq.dstrID.uiVal, strList[tokensParsed].strVal);
   
   //TODO
   //===========================================================================================
   //#define CMPM(DS,N,L,DST) if(CMP(DS,#N,L)){(DST).uiMethodID|=METHOD_##N;break;}
   do {
      CMPM(strList[tokensParsed+1],INVITE,6,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],ACK,3,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],BYE,3,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],CANCEL,6,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],REGISTER,8,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],OPTIONS,7,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],MESSAGE,7,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],INFO,4,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],UPDATE,6,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],REFER,5,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],NOTIFY,6,gspSIPMsg->hdrCSeq)
      
      CMPM(strList[tokensParsed+1],PUBLISH,7,gspSIPMsg->hdrCSeq)
      CMPM(strList[tokensParsed+1],SUBSCRIBE,9,gspSIPMsg->hdrCSeq)
      
      printError("Error: CSeq method unknow!",DROP); 
      return DROP;
      
   }while(0);
   
   //   printf("CSeq=%u,  Method=%u\n",gspSIPMsg->hdrCSeq.id,gspSIPMsg->hdrCSeq.uiMethodID);
   
   tokensParsed+=i;
   return 0; 
}

int CSip::parseDate(){ return 0; }
int CSip::parseErrorInfo(){ return 0; }
int CSip::parseEvent()
{
   int i,j;
   TOKENS_IN_LINE
   if (!i){printError("Error in event",DROP); return DROP;}
   if (i==1) gspSIPMsg->dstrEvent=strList[tokensParsed];
   else
   {
      strList[tokensParsed].uiLen=strList[tokensParsed+i-1].strVal-strList[tokensParsed].strVal+strList[tokensParsed+i-1].uiLen;
      gspSIPMsg->dstrEvent=strList[tokensParsed];
   }
   
   tokensParsed+=i;
   return 0; 
}


int CSip::parseMF_EXP_CL(DSTR * dstr)
{
   int i,j;
   
   TOKENS_IN_LINE
   
   if (i!=1)
   {printError("Error: parseMF_EXP_CL not found:",DROP); return DROP;}
   
   if (strList[tokensParsed].uiLen>9)
   {printError("Error in parseMF_EXP_CL (token len>9)",DROP); return DROP;}
   
   if(strList[tokensParsed].strVal[0]=='-')
   {printError("Error in parseMF_EXP_CL (token starts with '-')",DROP); return DROP;}
   
   (*dstr)=strList[tokensParsed];
   //dstr->uiVal=strtoul((strList[tokensParsed].strVal),NULL,0);
   STR_TO_I( dstr->uiVal, strList[tokensParsed].strVal);
   //TODO
   
   tokensParsed+=i;
   return 0; 
}


int CSip::parseFromTo(HDR_TO_FROM * ft)
{
   int   i,j;
   
   TOKENS_IN_LINE
   
   //for (j=tokensParsed;j<tokensParsed+i;j++) printStr(strList[j]);
   
   if (i==0) {printError("Error: sip (from | to addr) not found:",DROP); return DROP;}
   
   
   j=tokensParsed;
   ft->dstrFullRow=strList[j-1];
   ft->dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   if (strList[j+i-1].iLast=='>' || strList[j+i-1].iLast=='"')
      ft->dstrFullRow.uiLen++; 
   
   if (strList[j].iLast!=':')
   { 
      ft->dstrName=strList[j]; 
      for(;;j++)
      {
         if(j>=n){printError("ERROR From to uri",j);return DROP;}
         if (strList[j+1].iLast==':') break;
      }
      ft->dstrName.uiLen=strList[j].strVal-ft->dstrName.strVal+strList[j].uiLen;
      j++;
      //ft->flag|=1;
   } 
   if ((CMP(strList[j],"SIP",3)) || (CMP(strList[j],"SIPS",4)))
   {
      j=parseSipUri(&ft->sipUri, j, i);
      if(j<0){printError("ERROR From to ",j);return j;}
      
      //flag|=2;
   }
   else {printError("Error: sip (from|to addr) not found:",DROP); return DROP;}
   
   for(;j<tokensParsed+i-1; j++)
   {
      //if(j==n){printError("ERROR From to uri",j);return DROP;}
      if (CMP(strList[j],"TAG",3))
      {
         if (strList[j].iLast!='=') {printError("Error in from to =;",DROP); return DROP;}
         j++;
         ft->dstrTag=strList[j];
         break;
      }
   }
   tokensParsed+=i;
   return 0; 
}

int CSip::parseInReplyTo(){ return 0; }

int CSip::parseMIMEVersion(){ return 0; }
int CSip::parseMinExpires(){ return 0; }
int CSip::parseOrganization(){ return 0; }

int CSip::parsePortaBilling()
{
   int i,j;
   //  DEBUG(0,"Conttype info");
   
   TOKENS_IN_LINE
   // for (j=tokensParsed;j<tokensParsed+i;j++) printStr(strList[j]);
   j=tokensParsed;
   if (i<4) 
   {printError("Error: in hdrPortaBill",DROP); return DROP;}
   
   gspSIPMsg->hdrPortaBill.dstrFullRow=strList[j-1];
   gspSIPMsg->hdrPortaBill.dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   int iOk=0;
   
   for (;j<tokensParsed+i;j++)
   {
      if(strList[j].iLast!=':' && strList[j].iLast!=' ') {printError("Error in hdrPortaBill!",DONT_DROP); break;}
      if(strList[j].iLast!=':')continue;
      
      if (CMP(strList[j],"CURRENCY",8))
      {
         j++;
         iOk|=1;
         gspSIPMsg->hdrPortaBill.dstrCurrency=strList[j];
         continue;
      }
      //available-funds
      if (CMP(strList[j],"AVAILABLE\rFUNDS",15))
      {
         iOk|=2;
         j++;
         gspSIPMsg->hdrPortaBill.dstrValue=strList[j];
         continue;
      }
   }
   if(iOk!=3)
   {
      memset(&gspSIPMsg->hdrPortaBill,0,sizeof(gspSIPMsg->hdrPortaBill));
   }
   
   tokensParsed+=i;
   return 0; 
   
}
int CSip::parsePriority()
{
   const char *non_urgent="NON\rURGENT";
   int i,j;
   TOKENS_IN_LINE
   if (i==0)
   {printError("Error: Priority :",DROP); return DROP;}
   tokensParsed+=i;
   
   if (CMP(strList[tokensParsed-1],non_urgent,10)) {gspSIPMsg->dstrPriority=strList[tokensParsed-1];gspSIPMsg->dstrPriority.uiVal=PRIORITY_NON_URGENT;return 0;}
   // m="-"|96                                       
   if (CMP(strList[tokensParsed-1],"NORMAL",6))      {gspSIPMsg->dstrPriority=strList[tokensParsed-1];gspSIPMsg->dstrPriority.uiVal=PRIORITY_NORMAL;    return 0;}
   if (CMP(strList[tokensParsed-1],"URGENT",6))      {gspSIPMsg->dstrPriority=strList[tokensParsed-1];gspSIPMsg->dstrPriority.uiVal=PRIORITY_URGENT;    return 0;}
   if (CMP(strList[tokensParsed-1],"EMERGENCY",9))   {gspSIPMsg->dstrPriority=strList[tokensParsed-1];gspSIPMsg->dstrPriority.uiVal=PRIORITY_EMERGENCY; return 0;}
   
   printError("Error in priority!",DONT_DROP);
   return DONT_DROP; 
}

int CSip::parseProxyRequire()
{
   int i,j;
   
   TOKENS_IN_LINE
   j=tokensParsed;
   
   if (i==0)
   {printError("Error: Proxy Require not found:",DROP); return DROP;}
   
   gspSIPMsg->hldProxReq.x[gspSIPMsg->hldProxReq.uiCount].dstrFullRow=strList[j-1];
   gspSIPMsg->hldProxReq.x[gspSIPMsg->hldProxReq.uiCount].dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   
   for (;j<i+tokensParsed-1;j++)
   {
      gspSIPMsg->hldProxReq.x[gspSIPMsg->hldProxReq.uiCount].dstrName=strList[j];
      //if CMP(strList[j],"foo",3))  {gspSIPMsg->hldProxReq.x[gspSIPMsg->hldProxReq.uiCount].req=FOO;continue;}
      if (gspSIPMsg->hldProxReq.uiCount<ARRAY_SIZE) 
         gspSIPMsg->hldProxReq.uiCount++;  // if count<ARRAY_SIZE
   }
   
   
   tokensParsed+=i;
   return 0; 
}

int CSip::parseRetryAfter(){ return 0; }
int CSip::parseRoutes(HLD_ROUTE * rt)
{ // =================kautkas jamaina=============
   int i,j;
   
   TOKENS_IN_LINE
   if (i==0) {printError("Error in Route 1",DROP); return DROP;}
   j=tokensParsed;
   
   rt->x[rt->uiCount].dstrFullRow=strList[j-1];
   rt->x[rt->uiCount].dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   
   for (;j<i+tokensParsed-1;)
   {
      if ((CMP(strList[j],"SIP",3)) || (CMP(strList[j],"SIPS",4)))
      {
         if (strList[j-1].iFirst==34)
         {
            if (strList[j-1].iLast==34)
            {
               rt->x[rt->uiCount].dstrName=strList[j-1];
            }
         }
         
         j=parseSipUri(&rt->x[rt->uiCount].sipUri,  j,i);
         //  DEBUG(0,"parse  1")
         if(j<0){printError("ERROR: parseRoutes",j);return j;}
         
         if (rt->uiCount<ARRAY_SIZE) 
         {
            // DEBUG(0,"parse  2")
            rt->uiCount++;  // if count<ARRAY_SIZE
         }
         //         break;
         continue;
      }
      j++;
   }
   tokensParsed+=i;
   return 0; 
}

int CSip::parseReplayTo(){ return 0; }
int CSip::parseServer(){ return 0; }//TODO parseet kaa caall id

int CSip::parseSubject(){ return 0; }//TODO parseet kaa caall id
int CSip::parseSupportReq(HLD_SUP * sup)
{
   int i,j;
   
   TOKENS_IN_LINE
   j=tokensParsed;
   
   if (i==0)
   {printError("Error: Supported not found:",DROP); return DROP;}
   
   sup->x[sup->uiCount].dstrFullRow=strList[j-1];
   sup->x[sup->uiCount].dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   
   for (;j<i+tokensParsed;j++)
   {
      sup->x[sup->uiCount].dstrName=strList[j];
      //if CMP(strList[j],"foo",3))  {gspSIPMsg->hldProxReq.x[gspSIPMsg->hldProxReq.uiCount].req=FOO;continue;}
      if (sup->uiCount<ARRAY_SIZE) 
         sup->uiCount++;  // if count<ARRAY_SIZE
   }
   
   
   tokensParsed+=i;
   return 0; 
}
int CSip::parseTimestamp(){ return 0; }

int CSip::parseUserAgent(){ return 0; }


int CSip::parseWarning(){ return 0; }

int CSip::parseUnknown()
{
   
   int i,j;
   
   TOKENS_IN_LINE;
   
   j=tokensParsed;
   
   gspSIPMsg->hldUnknownHdr.x[gspSIPMsg->hldUnknownHdr.uiCount]=strList[j-1];
   gspSIPMsg->hldUnknownHdr.x[gspSIPMsg->hldUnknownHdr.uiCount].uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   if (strList[j+i-1].iLast=='>' || strList[j+i-1].iLast=='"')
      gspSIPMsg->hldUnknownHdr.x[gspSIPMsg->hldUnknownHdr.uiCount].uiLen++;
   
   
   
   if (gspSIPMsg->hldUnknownHdr.uiCount<ARRAY_SIZE) 
      gspSIPMsg->hldUnknownHdr.uiCount++;  // if count<ARRAY_SIZE
   
   tokensParsed+=i;
   
   return 0;
}

int CSip::parseVia()
{
 
   int i,j,viaNr=gspSIPMsg->hldVia.uiCount;//=REC_VIA;
   
   //if(gspSIPMsg->hdrVia[REC_VIA].dstrSipVer.uiLen==0) viaNr=REC_VIA;
   
   TOKENS_IN_LINE
   
   
   //  for (j=tokensParsed;j<tokensParsed+i;j++) printStr(strList[j]);
   if (i==0)
   {printError("Error: Via not found:",DROP); return DROP;}
   
   
   j=tokensParsed;
   
   gspSIPMsg->hldVia.x[viaNr].dstrFullRow=strList[j-1];
   gspSIPMsg->hldVia.x[viaNr].dstrFullRow.uiLen=strList[j+i-1].strVal-strList[j-1].strVal+strList[j+i-1].uiLen;
   
   
   
   if (i<3) {printError("Error in Via!",DROP); return DROP;}
   if (!CMP(strList[j],"SIP",3)){printError("errUnknowProtocol",DROP); return DROP;}
   j++;
   
   gspSIPMsg->hldVia.x[viaNr].dstrSipVer=strList[j];
   j++;
   gspSIPMsg->hldVia.x[viaNr].dstrTransport=strList[j];
   j++;
   
   if (strList[j].iFirst!=' ') {printError("Via ErrorSP!",DROP); return DROP;}
   
   if (strList[j].uiLen>3) 
      gspSIPMsg->hldVia.x[viaNr].dstrViaAddr=strList[j]; else {printError("Error: Via address not found!",DROP); return DROP;}
   // printStr(gspSIPMsg->hldVia.x[viaNr].dstrViaAddr);
   j++;
   
   if ((i>3) && (strList[j].iFirst==':'))
   {
      if (strList[j].uiLen>5) {printError("Error in port!",DROP); return DROP;}
      gspSIPMsg->hldVia.x[viaNr].dstrPort=strList[j];
      gspSIPMsg->hldVia.x[viaNr].dstrPort.uiVal=strtoul((strList[j].strVal),NULL,0);
      if ((gspSIPMsg->hldVia.x[viaNr].dstrPort.uiVal>256*256-1) || (gspSIPMsg->hldVia.x[viaNr].dstrPort.uiVal<1)) {printError("Error in port!",DROP); return DROP;}
      
      //      printf("port=%d\n",gspSIPMsg->hldVia.x[viaNr].dstrPort);   
      j++;
   }else gspSIPMsg->hldVia.x[viaNr].dstrPort.uiVal=DEAFULT_SIP_PORT;
   
   for(;j<i+tokensParsed;j++)//((oldFlag==gspSIPMsg->hldVia.x[viaNr].iFlags)||(i<4))
   {
      if (!(gspSIPMsg->hldVia.x[viaNr].iFlags & 1)&&(CMP(strList[j],"BRANCH",6)))
      {
         
         gspSIPMsg->hldVia.x[viaNr].dstrBranch=strList[j+1];
         gspSIPMsg->hldVia.x[viaNr].iFlags|=1;
         j+=1;
         continue;
      }
      
      if (!(gspSIPMsg->hldVia.x[viaNr].iFlags & 8)&&(CMP(strList[j],"RECEIVED",8)))
      {
         gspSIPMsg->hldVia.x[viaNr].dstrRecived=strList[j+1];
         gspSIPMsg->hldVia.x[viaNr].iFlags|=8;
         j+=1;
         continue;
      }   
      if(!(gspSIPMsg->hldVia.x[viaNr].iFlags & 16) && 
         (CMP(strList[j],"RPORT",5) || CMP(strList[j],"REVCPORT",8)))
      {
         if(strList[j].iLast=='=')
         {
            gspSIPMsg->hldVia.x[viaNr].dstrRPort=strList[j+1];
            gspSIPMsg->hldVia.x[viaNr].iFlags|=16;//TODO
            STR_TO_I(gspSIPMsg->hldVia.x[viaNr].dstrRPort.uiVal,gspSIPMsg->hldVia.x[viaNr].dstrRPort.strVal);
            j+=1;
         }
         continue;
      }
      /*
       if (!(gspSIPMsg->hldVia.x[viaNr].iFlags & 2)&&(CMP(strList[j],"TTL",3)))
       {
       gspSIPMsg->hldVia.x[viaNr].dstrTTL=strList[j+1];
       gspSIPMsg->hldVia.x[viaNr].dstrTTL.uiVal=(int)strtod((strList[j+1].strVal),NULL);
       
       if (gspSIPMsg->hldVia.x[viaNr].dstrTTL.uiVal>255 || gspSIPMsg->hldVia.x[viaNr].dstrTTL.uiVal<0) {printError("Error:ttl is invalid",DROP); return DROP;}
       gspSIPMsg->hldVia.x[viaNr].iFlags|=2;
       j+=1;
       continue;
       }
       if (!(gspSIPMsg->hldVia.x[viaNr].iFlags & 4)&&(CMP(strList[j],"MADDR",5)))
       {
       gspSIPMsg->hldVia.x[viaNr].dstrMAddr=strList[j+1];
       gspSIPMsg->hldVia.x[viaNr].iFlags|=4;
       j+=1;
       continue;
       }
       */
   }
   
   if (gspSIPMsg->hldVia.uiCount<VIAS) 
      gspSIPMsg->hldVia.uiCount++;  // if count<ARRAY_SIZE
   tokensParsed+=i;
   return 0; 
}
