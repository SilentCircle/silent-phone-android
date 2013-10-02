/*
Created by Janis Narbuts
Copyright © 2004-2012, Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC. All rights reserved.

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

#ifndef _CFG_PARSER_H
#define _CFG_PARSER_H
class CTCfgParser{
public:


   static char * getText(char *p, const char *name, int &iLen, int iMaxLen=0)
   {
      iLen=0;
      char *ret=NULL;
      if(!iMaxLen)iMaxLen=strlen(p);
      int iNLen=strlen(name);
      char *pEnd=p+iMaxLen;
  //    printf("[ml=%d]",iMaxLen);
      while(*p && p<pEnd)
      {
         if(isalnum(p[0]))
         {
            if(name[0]==p[0] && name[iNLen-1]==p[iNLen-1] && name[iNLen>>1]==p[iNLen>>1] && strncmp(name,p,iNLen)==0)
            {
               while(*p!=':' && p<pEnd)
               {
                  if(!*p || p>=pEnd)return NULL;
                  p++;
               }
                  
               p++;
               while(!isalnum(p[0]) && p<pEnd)p++;
               if(p>=pEnd){
                  puts("[err1]");
                 return ret;
               }
               ret=p;
               while(*p>=' ' && p<pEnd)p++;
               
              // while(*(p-1)==' ' && p<pEnd)p--;
               iLen=p-ret;
  //             printf("[found %s=%d ]",name,iLen);

               return ret;
            }
            while(*p>=' ' && p<pEnd)p++;
            if(p>=pEnd)break;
         }
         else
            p++;
      }
      return ret;
   }
   static int getText2Buf(char *p, const char *name, char *retBuf, int iMaxRetSize,int iCfgLen)
   {
      int iLen;
      char *param=getText(p,name,iLen,iCfgLen);
      if(param==NULL)return -1;
      iMaxRetSize--;
      if(iLen>iMaxRetSize)iLen=iMaxRetSize;
      strncpy(retBuf,param,iLen);
      retBuf[iLen]=0;
      return 0;

   }
   static  int getInt(int iCfgLen, char *p, const char *name,  int &iRet, int id=1)
   {
      int iLen;
      char *param=getText(p,name,iLen,iCfgLen);
      if(param==NULL)return -1;
      int i;
      char *pEnd=iLen+param;
      for(i=0;i<id-1;i++)
      {
         strtoul(param,&param,0);
         //param++;
         if(pEnd<=param)return -1;
      }
      iRet=strtoul(param,NULL,0);
      return 0;
   }
   static unsigned int getUInt(int iCfgLen,char *p, char *name, unsigned int &iRet, int id=1)
   {
      int iLen;
      char *param=getText(p,name,iLen,iCfgLen);
      if(param==NULL)return -1;
      int i;
      char *pEnd=iLen+param;
      for(i=0;i<id-1;i++)
      {
         strtoul(param,&param,0);
         //param++;
         if(pEnd<=param)return -1;
      }
      iRet=strtoul(param,NULL,0);
      return 0;
   }

#define M_FNC_2I(_F2_,_F1_)       {                   \
      int a=CTCfgParser::getInt(p,#_F1_,i1,1);  \
      if(a==0)a+=CTCfgParser::getInt(p,#_F1_,i2,2);     \
      if(a==0){ _F2_##_F1_ (i1,i2);}}

#define M_FNC_1I(_F2_,_F1_)       {                   \
      int a=CTCfgParser::getInt(p,#_F1_,i1,1);  \
      if(a==0){ _F2_##_F1_ (i1);}}

#define M_FNC_0(_F2_,_F1_)       {                   \
      char *a=CTCfgParser::getText(p,#_F1_,i1);  \
      if(a){ _F2_##_F1_ ();}}

#define M_FNC_INT(_F2_,_F1_)       {                   \
      int a=CTCfgParser::getInt(p,#_F1_,i1,1);  \
      if(a==0){ _F2_##_F1_ =(i1);}}
};
#endif
