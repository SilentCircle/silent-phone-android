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

//#define DReo
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

#include "sdp.h"
#include "../utils/utils.h"

static int getMediaParams(SDP *psdp, char *buf);

int findMediaId(SDP *sdp, int eType, int n)//TOD
{
   int i;
   int media_id=-1;
   for (i=0;i<sdp->iMediaCnt;i++)
   {
      if(sdp->media[i].eType==eType)
      {
         if(n==0){
            media_id=i;
            break;
         }
         n--;
      }
   }
   return media_id;
   
}


int hasAttrib(SDP &sdp, const char *attr, int iType){
   int i;
   int iAttribLen=strlen(attr);
   
   for(i=0;i<sdp.attribs.iAttribCnt;i++){
      
      if(sdp.attribs.n[i].iMediaType==iType  && iAttribLen==sdp.attribs.n[i].iLen && strncmp(sdp.attribs.n[i].p,attr,iAttribLen)==0){
         printf("[%s ok]",attr);
         return 1;
      }
   }
   return 0;
}

static int findCrLf(char *buf, int &inc)
{
   char *pStart=buf;
   inc=0;
   while(*buf)
   {
      if(*buf=='\r' && *(buf+1)=='\n')
      {
         inc=(int)(buf-pStart+2);
         return 0;
      }
      buf++;
   }
   return -1;
}

static int findip(char *p,int &inc)
{
   int ip=0;
   int ipLen=0;
   inc=0;
   while(*p)
   {
      //this code is used also in server
#if  !defined(_WIN32) && !defined(__SYMBIAN32__) //if not arm
      if(*(unsigned int *)p==*(unsigned int *)"IN I" &&
         *(unsigned int *)(p+3)==*(unsigned int *)"IP4 ")
#else
         if(
            *(p+0)=='I' &&
            *(p+1)=='N' &&
            *(p+2)==' ' &&
            *(p+3)=='I' &&
            *(p+4)=='P' &&
            *(p+5)=='4' &&
            *(p+6)==' ' 
            )
#endif
         {
            inc+=7;
            p+=7;
            if(findCrLf(p,ipLen)==0)
            {
               ip=ipstr2long(ipLen-2,p);
               ip=reverseIP(ip);
               
               inc+=ipLen;
            }
            break;
            
         }
      inc++;
      p++;
      
   }
   
   return ip;
}


int parseSDP(SDP *psdp, char *buf, int iLen)
{
   
#define STR_MIN_SDP \
"v=0\r\n" \
"o=r 1 2 IN IP4 1.1.1.1\r\n"\
"s=x\r\n"\
"c=IN IP4 1.1.1.1\r\n"\
"t=0 0\r\n"\
"m=audio 10 RTP/AVP 9\r\n"
   
#define T_MAX_SDP_SIZE 1000
#define T_MIN_SDP_SIZE (sizeof(STR_MIN_SDP)-1)
   
   psdp->iParsed=0;
   if(iLen<T_MIN_SDP_SIZE || iLen>T_MAX_SDP_SIZE || !buf){
      return -1;
   }
   
   int not_ok=0;
   int inc=0;
   not_ok=findCrLf(buf,inc);
   if(not_ok<0)return -not_ok;
   int iWasCntIp=0;
   
   buf+=inc;
   
   while(*buf)
   {//isascii
      if(!(
           *(buf-2)=='\r' &&
           *(buf-1)=='\n' &&
           *(buf+1)=='='
           ))
      {
         buf++;
         continue;
      }
      if(!islower(*buf))return -1;
      //isascii
      
      switch(*buf)
      {
         case 'a':
            //ADD_STR(p,iLen,"a=rtpmap:9 G722/8000\r\n");
            buf+=2;{
               
               int n=psdp->attribs.iAttribCnt;
               if(n+1>=psdp->attribs.eMaxAttribCnt)n=psdp->attribs.eMaxAttribCnt-1;
               psdp->attribs.n[n].p=buf;
               psdp->attribs.n[n].iMediaType=psdp->iLastMediaType;   
               
               if(psdp->iMediaCnt && strncmp(buf,"x-ssrc:",7)==0)
               {
                  
                  buf+=7;
                  int i;
                  unsigned int x=0,a;
                  for(i=0;i<8;i++)
                  {
                     if(!isxdigit(*buf))
                        break;
                     if(isdigit(*buf))
                        a=*buf-'0';
                     else if(islower(*buf))
                        a=*buf-'a'+10;
                     else
                        a=*buf-'A'+10;
                     
                     x<<=4;
                     x|=a;
                     buf++;
                  }
                  if(i==8)
                     psdp->media[psdp->iMediaCnt-1].uiSSRC=x;
               }
               while(*buf>=' '){buf++;}
               psdp->attribs.n[n].iLen=buf-psdp->attribs.n[n].p;
               if(n+1<psdp->attribs.eMaxAttribCnt)psdp->attribs.iAttribCnt=n+1;
               
            }
            break;
         case 'o':
            buf+=2;
            psdp->ipOrigin=findip(buf,inc);
            buf+=inc;
            break;
         case 's':
            buf+=2;
            psdp->s.pSessName=buf;
            while(*buf>=' '){buf++;}
            psdp->s.iLen=buf-psdp->s.pSessName;
            break;
         case 'c':
            buf+=2;//--> test
            if(!iWasCntIp && psdp->iMediaCnt>0)
            {
               psdp->uiPrevIp=psdp->media[psdp->iMediaCnt-1].ipConnect=findip(buf,inc);
            }
            else //<<---
               psdp->uiPrevIp=psdp->media[psdp->iMediaCnt].ipConnect=findip(buf,inc);
            //if(inc==0)
            buf+=inc;
            iWasCntIp=1;
            break;
         case 'm':
            buf+=2;
            if(getMediaParams(psdp,buf)<0)
            {
               psdp->iParsed=0;//TODO remove
               return -1;//TODO test without this, but must not be removed
            }
            findCrLf(buf,inc);
            break;
            //case 'a':buf+=2; //a=x-ssrc
         default:
            not_ok=findCrLf(buf,inc);
            if(not_ok<0)return not_ok;
            buf+=inc;
            
      }
   }
   
   psdp->iParsed=1;//TODO remove
   return 0;
}
static int getMediaParams(SDP *psdp, char *buf)
{
   int ok=0;
   while(*buf)
   {
      if(strncmp(buf,"audio",5)==0)
      {
         psdp->media[psdp->iMediaCnt].eType=SDP::eAudio;
         break;
      }
      if(strncmp(buf,"video",5)==0)
      {
         psdp->media[psdp->iMediaCnt].eType=SDP::eVideo;
         break;
      }
      if(strncmp(buf,"image",5)==0)
      {
         psdp->media[psdp->iMediaCnt].eType=SDP::eT38;
         break;
      }
      if(strncmp(buf,"data",4)==0)
      {
         psdp->media[psdp->iMediaCnt].eType=SDP::eFileTransf;
         break;
      }
      buf++;
   }
   if(psdp->media[psdp->iMediaCnt].eType==0)
      return -1;
   
   psdp->iLastMediaType=psdp->media[psdp->iMediaCnt].eType;
   //buf+=6;
   while(*buf>32 && *buf!=0)buf++;
   if(*buf==0)return -1;
   
   while(*buf<=32 && *buf!=0)buf++;
   if(*buf==0)return -1;
   //  printf("buf[%s][%.*s]",buf-10,8,buf);
   psdp->media[psdp->iMediaCnt].port=atoi(buf);
   buf++;
   
   while(isdigit(*buf))
      buf++;
   
   while(*buf>' ' && *buf!=0)buf++;
   if(*buf==0)return -1;
   
   //TODO if-audio video rtp/avp------------->>
   
   while(!isdigit(*buf) && *buf>=' ')buf++;
   if(*buf<' ')return -1;
   
   if(psdp->media[psdp->iMediaCnt].eType & (SDP::eVideo|SDP::eAudio))
   {
      //TODO check rtp/avp
      
      while(!ok)//TODO if 1
      {
         int v=atoi(buf);
         if(v<0 || v>127)return -1;
         //TODO save
         psdp->u.codec[psdp->codecCount].uiMediaID=psdp->iMediaCnt;
         psdp->u.codec[psdp->codecCount].uiID=v;
         psdp->u.codec[psdp->codecCount].eType=psdp->media[psdp->iMediaCnt].eType;
         psdp->codecCount++;
         buf++;
         while(isdigit(*buf))buf++;
         while(*buf==' ')buf++;
         
         if(*buf==0 || psdp->codecCount>=SDP::eMaxCodecs)return -1;
         if(*buf=='\r')break;
         
      }
      //---------audio video  rtp/avp-------------<<
   }
   else if(psdp->media[psdp->iMediaCnt].eType == SDP::eT38)
   {
      //TODO if-image udptl------------->>
      //TODO check udptl
      //--------image udptl-------------<<
      
   }
   
   
   //TODO getAtribs
   if(psdp->media[psdp->iMediaCnt].ipConnect==0)
      psdp->media[psdp->iMediaCnt].ipConnect=psdp->uiPrevIp;
   
   if(psdp->iMediaCnt<SDP::eMaxMediaCnt)
      psdp->iMediaCnt++;
   
   return 0;
}
