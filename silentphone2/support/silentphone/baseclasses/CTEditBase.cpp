/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2017, Silent Circle, LLC.  All rights reserved.

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
//#include "../os/CTOsGui.h"
#if defined(__APPLE__) || defined(ANDROID_NDK) ||  defined(__linux__)


#define T_SKIP_GUI_BUILD
#include "../baseclasses/CTBase.h"
#include "../baseclasses/CTEditBase.h"

#else
#include "../os/ctosgui.h"
#include "../baseclasses/CTLabelBase.h"
#endif

#include <string.h>
#include <stdio.h>
#include <time.h>
#include <algorithm>

#ifndef UNICODE
#define UNICODE
#endif

#ifdef _WIN32
#define snprintf _snprintf
#endif


#if defined(__SYMBIAN32__)
#include <libc\ctype.h>
#endif



int  insertTime(char *buf)
{
//#if defined(_WIN32) && !defined(_T_CONSOLE_ ) && !defined(__SYMBIAN32__)
   int i;
#ifdef _WIN32_WCE
   SYSTEMTIME st;
   GetLocalTime(&st);//TODO store time
   i = sprintf(buf, "%02u:%02u:%02u",st.wHour,st.wMinute,st.wSecond);
#else

   time_t ti=time(NULL);
   struct tm *t=localtime(&ti);

   i = sprintf(buf, "%02u:%02u:%02u",t->tm_hour,t->tm_min,t->tm_sec);
#endif
 
   return i;
//#endif
}

void  insertTime(CTEditBase *e)
{
   char buf[32];
   int i=insertTime(&buf[0]);
   e->addText(buf,i);
}

int getDaysFromNYear(int f){
   int r=0;
   int y;
   int m;
   int d;

   int tn[]={31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
   int tl[]={31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
#ifdef _WIN32_WCE
   SYSTEMTIME st;
   GetLocalTime(&st);
   y=st.wYear;
   m=st.wMonth-1;
   d=st.wDay-1;
   //st.
#else

   time_t ti=time(NULL);
   struct tm *t=localtime(&ti);
   y=1900+t->tm_year;
   m=t->tm_mon;
   d=t->tm_mday-1;

  // i = sprintf(buf, "%02u:%02u:%02u",t->tm_hour,t->tm_min,t->tm_sec);
#endif
   int *tx=y&3?&tn[0]:&tl[0];
   r=(y-f)*365+(((y-f))>>2)+1;
   while(m>0){m--;r+=tx[m];}
   while(d>0){r++;d--;}
   return r;
}



void insertTimeDateFriendly(char  *buf, int iTime, int utc)
{
#ifndef _WIN32_WCE
	time_t tt=(time_t)iTime;
   
   struct tm *t = utc ? gmtime(&tt) : localtime(&tt);
   
   const static char *mon_name[12] = {
      "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
   };

   struct tm ts;
   memcpy(&ts,t,sizeof(struct tm));

   sprintf(buf, "%s %2d, %d %02u:%02u",mon_name[ts.tm_mon],ts.tm_mday,ts.tm_year+1900,ts.tm_hour,ts.tm_min);
   
   
#endif
   
   
   
   //e->addText(asctime(t));
}

int isToday(time_t iTime){
   time_t now=time(NULL);
   time_t tt=(time_t)iTime;
   struct tm *t=localtime(&tt);
   
   struct tm ts;
   memcpy(&ts,t,sizeof(struct tm));
   struct tm *t2=localtime(&now);
   
   return t2->tm_yday==ts.tm_yday && ts.tm_year==t2->tm_year;
}

void insertDateFriendly(char  *buf, int iTime ,int iInsertToday)
{
   
#ifndef _WIN32_WCE
	time_t tt=(time_t)iTime;
   
   struct tm *t=localtime(&tt);
   
   const static char *mon_name[12] = {
      "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
   };
   
   const static char *w_name[7] = {
      "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
   };
   //if()
   
   //const char *mb[]={"Jan","Feb","Mar","Apr","" };
   
   //char buf[32];

   //12.02.2004 12:24
   time_t now=time(NULL);
   struct tm ts;
   memcpy(&ts,t,sizeof(struct tm));
   struct tm *t2=localtime(&now);
   
   int iThisYear=ts.tm_year==t2->tm_year;
   int iIsToday=t2->tm_yday==ts.tm_yday && iThisYear;
   
   if(iIsToday){
      if(iInsertToday)strcpy(buf, "Today");
      else sprintf(buf, "%02u:%02u",ts.tm_hour,ts.tm_min);
   }else{
      int difD=t2->tm_yday-ts.tm_yday;
      if(difD==1 && iThisYear){
         sprintf(buf, "Yesterday %02u:%02u",ts.tm_hour,ts.tm_min);
      }
      else if(difD<7 && difD>0 && iThisYear)
         sprintf(buf,"%s",w_name[ts.tm_wday]);
      else
         sprintf(buf, "%s %2d, %d",mon_name[ts.tm_mon],ts.tm_mday,ts.tm_year+1900);
   }
   
#endif
   
   
   
   //e->addText(asctime(t));
}
void insertDateFriendly(CTEditBase *e, int iTime ,int iInsertToday)
{
   
   char buf[32];
   insertDateFriendly(&buf[0], iTime, iInsertToday);
   e->addText(buf);
   //e->addText(asctime(t));
}

int getText(char *p, int iMaxLen, CTStrBase *ed)//TODO check validity iedot liidzi funktoru uz fnc
{
   
   int iLen = ed->getLen();//(unsigned short *)p,iMaxLen/2);//jo utf-16
   
   if(iLen)
   {
#if 0
      //TODO converToUtf8
      iLen = min(iMaxLen , iLen);
      convert16to8(p, ed->getText(),  iLen);
#else
      //utf8
      int t_ucs2_to_utf8(const unsigned short *str16, int len, unsigned char  *utf8, int iMaxSize);
      iLen = t_ucs2_to_utf8((const unsigned short*)ed->getText(), iLen, (unsigned char *)p, iMaxLen);
#endif
   }
   p[iLen] = 0;
   return iLen;
}



void insertDateTime(char  *buf, int iTime ,int iTimeOrDayOnly)
{
#ifndef _WIN32_WCE
	time_t tt=(time_t)iTime;

   struct tm *t=localtime(&tt);
   
   
   //char buf[32];
   int i;
   //12.02.2004 12:24
   if(iTimeOrDayOnly)
   {
      time_t now=time(NULL);
      struct tm ts;
      memcpy(&ts,t,sizeof(struct tm));
      struct tm *t2=localtime(&now);

      int iIsToday=t2->tm_yday==ts.tm_yday && ts.tm_year==t2->tm_year;
      if(iIsToday)
         i = sprintf(buf, "%02u:%02u",ts.tm_hour,ts.tm_min);
      else
         i = sprintf(buf, "%02u.%02u.%04u",ts.tm_mday,ts.tm_mon+1,ts.tm_year+1900);

   }
   else
   {
      i = sprintf(buf, "%02u.%02u.%04u %02u:%02u",t->tm_mday,t->tm_mon+1,t->tm_year+1900,t->tm_hour,t->tm_min);
   }
  // e->addText(buf,i);
#endif



   //e->addText(asctime(t));
}

void insertDateTime(CTEditBase *e, int iTime ,int iTimeOrDayOnly)
{
   
   char buf[32];
   insertDateTime(&buf[0], iTime, iTimeOrDayOnly);
   e->addText(buf);
   //e->addText(asctime(t));
}


void insertDateTime(CTEditBase *e, int iTime)
{
   return insertDateTime(e,iTime,0);
}

#include <ctype.h> 
int cmpU(CTStrBase *b1, CTStrBase *b2)
{
   /*
   TPtrC16 s1((unsigned short*)b1->getText(),b1->getLen());
   TPtrC16 s2((unsigned short*)b2->getText(),b2->getLen());
   return s1.CompareC(s2);
   */

   short *s1=b1->getText(),*s2=b2->getText();
   int ret, iLen=std::min(b1->getLen(),b2->getLen());
   if(iLen==0)
   {
      return b1->getLen()-b2->getLen();
   }

   while(1)
   {
      iLen--;
      //ret=((*s1)|0x20)-((*s2)|0x20);//TODO use to lower
      ret=tolower(((*s1)&0xff))-tolower(((*s2)&0xff));
      if(ret || *s1==0  || iLen==0)
         break;
      s1++;
      s2++;

   }
   if(ret==0)
   {
      return b1->getLen()-b2->getLen();
   }
   return ret;
}

int cmpU_LastChar(CTStrBase *b1, CTStrBase *b2, int iLastChar)
{

   short *s1=b1->getText(),*s2=b2->getText();
   int ret, iLen=std::min(b1->getLen(),b2->getLen());
   if(iLen==0)
   {
      return b1->getLen()-b2->getLen();
   }

   while(1)
   {
      iLen--;
      //ret=((*s1)|0x20)-((*s2)|0x20);//TODO use to lower
      if((*s1==iLastChar && *s2==0)|| (*s2==iLastChar && *s1==0))return 0;
      if(*s1==iLastChar || *s2==iLastChar)return 1;
      
      ret=tolower(((*s1)&0xff))-tolower(((*s2)&0xff));
      if(ret || *s1==0  || iLen==0)
         break;
      s1++;
      s2++;

   }
   if(ret==0)
   {
      return b1->getLen()-b2->getLen();
   }
   return ret;
}


//static CTStr cStr(_T_LEN(TEXT(ED_PWD_STR)));
int cmpmyUnicode(short *src,short *dst,int iShorts);

CTEditBase::CTEditBase(int iMaxLen, short *buf)
   :iUpdateSize(0),iLen(0),pData(buf),iMaxLen(iMaxLen-1),iMaxLenIsLocked(1),iCannotRedraw(0)
{
onChangeCB=NULL;
}

CTEditBase::CTEditBase(int iMaxLen)
   :iUpdateSize(128),iLen(0),pData(NULL),iMaxLen(iMaxLen),iMaxLenIsLocked(0),iCannotRedraw(0)
{
   onChangeCB=NULL;
   pData=new short[iMaxLen+1];
   pData[0]=0;
}

CTEditBase::CTEditBase(const char *p,int iLen, int iIsUnicode)
   :iUpdateSize(64),iLen(iLen),pData(NULL),iMaxLen(0),iMaxLenIsLocked(0),iCannotRedraw(0)//TODO aisardziibu sheit
{
   onChangeCB=NULL;
   if (p)
   {
      setText(p,iLen,iIsUnicode);
   }
   else 
      setText("",0);
}

int CTStrBase::operator == (const char *p){
   if(!p)return 0;
   int l = (int)strlen(p);
   if(l!=getLen() )return 0;
   if(l==0)return 1;
   short *pD=getText();
   l--;
   for(;l>=0;){
      if(pD[l]!=p[l])return 0;
      l--;
   }
   return 1;
}

int CTStrBase::operator==(CTStrBase *b){
   if(!b)return 0;
   int l=getLen();
   if(b->getLen()!=l )return 0;
   if(!l)return 1;
   short *pD=getText();
   short *ps=b->getText();
   if(ps[l>>1]!=pD[l>>1])return 0;
   return memcmp(pD,ps,l*2)==0;
}

void CTEditBase::trim()
{
   //char * ret=sz;
   short *sz=pData;
   int i,x=0;//strlen(sz);
   for (i=0;sz[i]!=0;i+=1)
   {
      if (sz[i]=='\t' || sz[i]=='\n' || sz[i]==' ' || sz[i]=='\r')
      {
         x+=1;
      }
      else break;
   }
   if (x)
   {
      for (i=0;sz[i]!=0;i+=1)
         sz[i]=sz[i+x];
   }
   else
   {
      //i=strlen(sz);
      i=0;
      short *tmp=pData;
      while(*tmp){i++;tmp++;}
   }
   i-=x;
   while(i>0)
   {
      if (sz[i-1]=='\t' || sz[i-1]=='\n' || sz[i-1]==' ' || sz[i-1]=='\r')
         sz[--i]=0;
      else break;
   }
   iLen=i;

   onChange();
}

void CTEditBase::reset()
{
   //TODO prot;
   iLen=0;
   if(pData)pData[0]=0;
   if(!iMaxLenIsLocked)
      iMaxLen=0;
   
   if(pData && !iMaxLenIsLocked)
   {
      delete pData;
      pData=NULL;
   }
   
   onChange();
}

//   void CTEditBase::remLastChar(int iCharsToRemove=1)
void CTEditBase::remLastChar(int iCharsToRemove)
{
   if(iLen>0)
   {
      if(iCharsToRemove>iLen)
         iCharsToRemove=iLen;
      iLen-=iCharsToRemove;

      pData[iLen]=0;
      if(iCannotRedraw==0)
         onChange();
   }
}

//int cmpmyUnicode(short *src,short *dst,int iShorts);
int CTEditBase::cmpN(const CTStrBase &b, int iChars)
{
   int iLen2=((CTStrBase &)b).getLen();
   iLen2=std::min(iLen2,iLen);
   if(iChars<iLen2)
      return 0;
   return cmpmyUnicode(((CTStrBase &)b).getText(),pData,iChars);

}

void CTEditBase::addInt(int i, const char * format)
{
   char buf[64];
   int iLenAdd=snprintf(buf,sizeof(buf),format,i);
   addText((char *)&buf, iLenAdd);
}

//typedef int (FNC_ADD_TEXT_CALLBACK)(short *pUni, int iMaxLen, void *pUserData);
void CTEditBase::addText(FNC_ADD_TEXT_CALLBACK *fnc, int iMaxLenToAdd, void *pUserData)
{
   int iPos;
   iMaxLenToAdd=checkSize(iMaxLenToAdd,&iPos);
   iLen=iPos;
   iLen += fnc(pData+iLen,iMaxLenToAdd,pUserData);
   pData[iLen]=0;
   if(iCannotRedraw==0)
      onChange();

}
void CTEditBase::remCharsFrom(int iPos, int iCharsToRemove)
{
   /*
   if(iPos>=iCharsToRemove)
   {
      iPos-=iCharsToRemove;
   }
   else
   {
      iCharsToRemove=iPos;
      iPos=0;
   }
   */
   if(iCharsToRemove>iLen)
      iCharsToRemove=iLen;
   if(iCharsToRemove>iPos)
      iCharsToRemove=iPos;

   if(iPos>iLen)
      iPos=iLen;
   else if(iPos<iCharsToRemove)iPos=iCharsToRemove;

   int iCharsToMove=iLen-iPos;


   if(iCharsToMove)
   {

      short *tmp=pData+iPos-iCharsToRemove;
      int i=iCharsToMove;
      while(i>=0)
      {
        *tmp=*(tmp+iCharsToRemove);
         tmp++;
         i--;
      }

   }

   iLen-=iCharsToRemove;


   pData[iLen]=0;
   if(iCannotRedraw==0)
      onChange();

}
int CTEditBase::insertText(int iAtPos, char *buf, int iCharCount, int iIsUnicode)
{
   if(iAtPos>iLen)
      iAtPos=iLen;
   else if(iAtPos<0)iAtPos=0;

   if(iCharCount==0 && buf)
   {
      if(iIsUnicode)
      {
         short *tmp=(short *)buf;
         while(*tmp){iCharCount++;tmp++;}
      }
      else
         iCharCount=(int)strlen(buf);
   }
   int iPos;
   iCharCount=checkSize(iCharCount,&iPos);
   iLen=iPos;

   int iCharsToMove=iLen-iAtPos;

   if(iCharsToMove)
   {
   
      short *tmp=pData+iLen;
      int i=iCharsToMove;
      while(i>=0)
      {
        *(tmp+iCharCount)=*tmp;
         tmp--;
         i--;
      }
     
   }

   if(buf && iCharCount>0)
   {
      if(iIsUnicode)
      {
         memcpy(pData+iAtPos,buf,iCharCount*sizeof(short));
      }
      else
      {
         convert8_16((unsigned char *)buf, (unsigned  short *)pData+iAtPos, iCharCount,0);
      }
      iLen+=iCharCount;
   }
   pData[iLen]=0;
   if(iCannotRedraw==0)
      onChange();
   return iCharCount;
}

char *CTEditBase::getTextUtf8(char *pOut, int *iMaxLen){
   if(!iMaxLen)return pOut;
   int l=::getText(pOut,*iMaxLen,this);
   *iMaxLen=l;
   return pOut;
}

unsigned short  *
t_utf8_to_ucs2(const unsigned char *str, int len, int *ucs2_len, unsigned short  *ucs2_str)
{
   unsigned int ch = 0;
   int      cnt = 0;
   int             i_str, i_ucs2_str;
   
   if (len == 0)
      return NULL;
   
   for (i_ucs2_str = 0, i_str = 0; i_str < len; i_str++) {
      if (cnt > 0) {
         unsigned int        byte = str[i_str];
         
         if ((byte & 0xc0) != 0x80) {
            i_str--;
            cnt = 0;
         } else {
            ch <<= 6;
            ch |= byte & 0x3f;
            if (--cnt == 0) {
               ucs2_str[i_ucs2_str++] = ch;
            }
         }
      } else {
         ch = str[i_str];
         if (ch < 0x80) {
            /* One byte sequence.  */
            ucs2_str[i_ucs2_str++] = ch;
         } else {
            if (ch >= 0xc2 && ch < 0xe0) {
               /* We expect two bytes.  The first byte cannot be 0xc0
                * or 0xc1, otherwise the wide character could have been
                * represented using a single byte.  */
               cnt = 2;
               ch &= 0x1f;
            } else if ((ch & 0xf0) == 0xe0) {
               /* We expect three bytes.  */
               cnt = 3;
               ch &= 0x0f;
            } else if ((ch & 0xf8) == 0xf0) {
               /* We expect four bytes.  */
               cnt = 4;
               ch &= 0x07;
            } else if ((ch & 0xfc) == 0xf8) {
               /* We expect five bytes.  */
               cnt = 5;
               ch &= 0x03;
            } else if ((ch & 0xfe) == 0xfc) {
               /* We expect six bytes.  */
               cnt = 6;
               ch &= 0x01;
            } else {
               cnt = 1;
            }
            --cnt;
         }
      }
   }
   
   *ucs2_len = i_ucs2_str;
   return ucs2_str;
}

static int ucs2_to_utf8 (int ucs2, unsigned char * utf8)
{
   if (ucs2 < 0x80) {
      utf8[0] = ucs2;
      utf8[1] = '\0';
      return 1;
   }
   if (ucs2 >= 0x80  && ucs2 < 0x800) {
      utf8[0] = (ucs2 >> 6)   | 0xC0;
      utf8[1] = (ucs2 & 0x3F) | 0x80;
      utf8[2] = '\0';
      return 2;
   }
   if (ucs2 >= 0x800 && ucs2 < 0xFFFF) {
      if (ucs2 >= 0xD800 && ucs2 <= 0xDFFF) {

         return -2;
      }
      utf8[0] = ((ucs2 >> 12)       ) | 0xE0;
      utf8[1] = ((ucs2 >> 6 ) & 0x3F) | 0x80;
      utf8[2] = ((ucs2      ) & 0x3F) | 0x80;
      utf8[3] = '\0';
      return 3;
   }
   if (ucs2 >= 0x10000 && ucs2 < 0x10FFFF) {

      utf8[0] = 0xF0 | (ucs2 >> 18);
      utf8[1] = 0x80 | ((ucs2 >> 12) & 0x3F);
      utf8[2] = 0x80 | ((ucs2 >> 6) & 0x3F);
      utf8[3] = 0x80 | ((ucs2 & 0x3F));
      utf8[4] = '\0';
      return 4;
   }
   return -1;
}

int t_ucs2_to_utf8(const unsigned short *str16, int len, unsigned char  *utf8, int iMaxSize){
   int ret = 0;
   for(int i = 0; i<len;i++){
      
      if(ret + 5 > iMaxSize) break;
      
      int c = ucs2_to_utf8(str16[i], &utf8[ret]);
      
      if(c<0) break;

      ret+=c;
   }
   utf8[ret]=0;
   return ret;
}


void CTEditBase::addText(const char *buf, int iCharCount, int iIsUnicode)
{
  // int iPrevLen=iLen;
   //calc len
   if(iCharCount==0 && buf)
   {
      if(iIsUnicode)
      {
         short *tmp=(short *)buf;
         while(*tmp){iCharCount++;tmp++;}
      }
      else
         iCharCount=(int)strlen(buf);

   }
   int iPos;
   iCharCount=checkSize(iCharCount,&iPos);
   iLen=iPos;
   
   if(buf && iCharCount>0)
   {
      if(iIsUnicode)
      {
         memcpy(pData+iLen,buf,iCharCount*sizeof(short));
      }
      else
      {
         
         unsigned  short *d=(unsigned  short *)pData+iLen;
         unsigned char *b=(unsigned char *)buf;
     
         int dstLen=0;
         t_utf8_to_ucs2(b, iCharCount, &dstLen, d);
         iCharCount=dstLen;
      }
      iLen+=iCharCount;
   }
   pData[iLen]=0;
   if(iCannotRedraw==0)
      onChange();
}


int CTEditBase::checkSize(int iCharsToAdd, int *iPos)
{
   if(iMaxLenIsLocked)
   {
      //TODO use m
      if(iMaxLen<iLen+iCharsToAdd)
      {
         iCharsToAdd=iMaxLen-iLen;
      }

   }
   else
   {
      short *pOld=pData;
      if(iMaxLen<iLen+iCharsToAdd)
      {
         int iNewLen=iUpdateSize+iLen+iCharsToAdd;
         short *newData=new  short[iNewLen+1];
         if(pOld)
         {
            memcpy(newData,pOld,iLen*sizeof(short));
            pData=newData;
            delete pOld;
         }
         pData=newData;
         iMaxLen=iNewLen;
      }
      else if(pData==NULL) pData=new  short[1];
   }
   *iPos=iLen;      
   return iCharsToAdd;
}
#ifndef T_SKIP_GUI_BUILD
void CTFontN::initFont()
{
   int i;
   int hdc=0;
#if defined(_WIN32) && !defined(_T_CONSOLE_ ) && !defined(__SYMBIAN32__)
   hdc=(int)GetDC(0);
   HGDIOBJ old=SelectObject((HDC)hdc,font);
   {
      TEXTMETRICW m;
      GetTextMetricsW((HDC)hdc,&m);
      this->iHi=m.tmHeight;
      this->iAvgWidth=m.tmAveCharWidth;
      this->iAscentInPixels=m.tmAscent;
   }
#endif
   memset(bufCharW,0,sizeof(bufCharW));

#if defined( _WIN32_WCE) 
   int b=std::min(0xff+1,CHAR_CNT_IN_BUF);
#elif _WIN32
//   int b=min(0xffff+1,CHAR_CNT_IN_BUF);
   int b=std::min(0xff+1,CHAR_CNT_IN_BUF);
#else
   int b=std::min(128,CHAR_CNT_IN_BUF);
#endif
   for(i=32;i<b;i++)
   {
      //bufCharW[i]=
       getCharWos(i,hdc);
   }

   if(CHAR_CNT_IN_BUF>'t')
   {
      bufCharW['\t']=getCharWos(' ',hdc)*3;
   }
#if defined(_WIN32) && !defined(_T_CONSOLE_ ) && !defined(__SYMBIAN32__)
   old=SelectObject((HDC)hdc,old);
   ReleaseDC(0,(HDC)hdc);
#endif
}


CTLabelBase::CTLabelBase(int iMaxLen)
   :CTEditBase(iMaxLen)
   ,iCol(-2)
   ,fontLb(NULL)
{
}
CTLabelBase::CTLabelBase(char *p, int iLen,int iIsUnicode, int col)
//   CTLabelBase::CTLabelBase(char *p, int iLen=0,int iIsUnicode=0, int col=-2)
   :CTEditBase(p,iLen,iIsUnicode)
   ,iCol(col)
   ,fontLb(NULL)
{

}

void drawTextX(CTEditBase *e, CTDeviceCtx & iBase, const CTRect &rc, const CTAlign &al, int iXOff, int iYOff)
{
   CTAlign align(al.iFlag);

   if(iYOff)
   {
      align.iFlag&=~CTAlign::top;
      align.iFlag|=CTAlign::bottom;
   }

   if(rc.h<iBase.font->iHi && iYOff==0)
   {
      align.iFlag|=CTAlign::top;
      align.iFlag&=~CTAlign::bottom;
   }
   //int iFHI=0;

   drawText(iBase,*e,rc,align);


}
void CTLabelBase::draw(CTDeviceCtx & iBase,  const CTRect &rc, int iXOff, int iYOff, int iIsPwd)
//   void CTLabelBase::draw(int iBase, const CTRect &rc, int iXOff=0, int iYOff=0, int iIsPwd=0)
{
   if(iLen==0 || rc.y1<=rc.y)return;


   CTAlign align(al.iFlag);

   CTFontN *fCur=fontLb;//oSmaller

   int iFW=CTFontN::getTextWidth(fCur?fCur:iBase.font,this);

   if(fCur && fCur->foSmaller){
      if(iFW>rc.w || rc.h+iYOff<fCur->iHi){
         CTFontN *fo=iBase.font;
         fCur=fCur->foSmaller;
         int d=fo->iHi-fCur->iHi;
         if(iYOff)iYOff-=d;if(iYOff<0)iYOff=0;
         iFW=CTFontN::getTextWidth(fCur,this);
      }      
      //if(iYOff>0)
   }
   else if(iBase.font && iBase.font->foSmaller){
      if(iFW>rc.w || rc.h+iYOff<iBase.font->iHi){
         CTFontN *fo=iBase.font;
         fCur=iBase.font->foSmaller;
         int d=fo->iHi-fCur->iHi;
         if(iYOff)iYOff-=d;if(iYOff<0)iYOff=0;
         iFW=CTFontN::getTextWidth(fCur,this);
      }
   }
      //iBase.font

   if(iYOff)
   {
      align.iFlag&=~CTAlign::top;
      align.iFlag|=CTAlign::bottom;
   }

   if(iCol==-2)
   {
      iCol=iBase.iTextColor;
   }
   CTSetTextCol pc(iBase,iCol);
   CTSetFont sf(iBase,fCur);

   


   if(rc.h<iBase.font->iHi && iYOff==0)
   {
      align.iFlag|=CTAlign::top;
      align.iFlag&=~CTAlign::bottom;
   }
   //int iFHI=0;

     if(iIsPwd)
     {
        CTEditBuf<sizeof(ED_PWD_STR)> b;
        b.setText(ED_PWD_STR);
        drawText(iBase,b,rc,align);
     }
     else{
        if(iFW>rc.w){
           CTEditBuf<128> b;
           unsigned short *p=(unsigned short*)getText();
           int i,l=this->getLen();
           int iDotW=iBase.font->getCharW('.',0)*3;
           int w=rc.w-iDotW-4;
           for(i=0;i<l && w>0;i++){w-=iBase.font->getCharW(p[i],0);if(w<=3)break;}
              
              
           b.setText(*this);
           if(i<iLen)b.setLen(i);
           b.addText("...",3);
           drawText(iBase,b,rc,align);
        }
        else
          drawText(iBase,*this,rc,align);
     }

     
}

int getClipData(CTEditBase *e, int iAtPos, char *pAlowedChars)
{
   int l=0;
#if defined(_WIN32) && !defined(_T_CONSOLE_ ) && !defined(__SYMBIAN32__)
    char* ClipboardData;
    HGLOBAL   hglb;
    int iIsUnicode=1;
    
    
    if(!IsClipboardFormatAvailable(CF_UNICODETEXT)) 
    {
       if(!IsClipboardFormatAvailable( CF_TEXT))
         return -1;
       iIsUnicode=0;

    }
     if(!OpenClipboard(NULL))
        return -1;
     hglb= GetClipboardData(iIsUnicode?CF_UNICODETEXT:CF_TEXT);
     if(hglb)
     {
        ClipboardData = (char *)GlobalLock(hglb); 
        if(ClipboardData)
        {
           if(pAlowedChars==NULL)
              l=e->insertText(iAtPos,ClipboardData,0,iIsUnicode);
           else
           {
              int ok=0;
              int al=strlen(pAlowedChars);
              int i;
              
              if(iIsUnicode)
              {
                 unsigned short *p=(unsigned short *)ClipboardData;
                 for(;*p;)
                 {
                    for(i=0;i<al;i++)
                    {
                       if(pAlowedChars[i]==(char)(*p))
                       {
                          ok=1;
                          break;
                       }
                    }
                    

                    if(ok==0)break;
                    p++;
                    l++;
                 }
              }
              else
              {
                 char *p=(char *)ClipboardData;
                 for(;*p;)
                 {
                    for(i=0;i<al;i++)
                    {
                       if(pAlowedChars[i]==(char)(*p))
                       {
                          ok=1;
                          break;
                       }
                    }
                    if(ok==0)break;
                    p++;
                    l++;
                 }
              }
              if(ok)
              {
                  e->insertText(iAtPos,ClipboardData,l,iIsUnicode);
              }
           }
           GlobalUnlock(hglb);
        }
     }
    CloseClipboard();
#endif
    return l;

}

int copyDataToClip(char * pData,int iLen, int iIsUnicode=0)
{
#if defined(_WIN32) && !defined(_T_CONSOLE_ ) && !defined(__SYMBIAN32__)
 //  int iAnswerLen=selected.iSelTo-selected.iSelFrom;
   if(iIsUnicode)iLen*=sizeof(short);
   if (pData && iLen>0)
   {
      HGLOBAL hMem;
      char *chh;
      int i;
      //Open the clipboard
      if(!OpenClipboard(NULL))
         return -1;

      EmptyClipboard(); 

      hMem = GlobalAlloc(GMEM_MOVEABLE, iLen+4);//GMEM_DDESHARE | GMEM_MOVEABLE

      chh= (char*) GlobalLock(hMem);
      /*
      memcpy(chh,pData,iLen);
      */
      char *pp=(char *)pData;
      for(i=0;i<iLen;)
      {
         if(iIsUnicode)
         {
            if(*(pp+1)!=0 || *pp>3)
            {
               *chh=*pp;
               chh++;
               pp++;
               *chh=*pp;
               chh++;
               pp++;
            }
            else
            {
               pp+=2;
            }
            i+=2;
         }
         else 
         {
            if(pData[i]>3)
            {
               *chh=(char )pData[i];
               chh++;
            }
            i++;
         }
      }
      *chh=0;
      *(chh+1)=0;
      if(iIsUnicode)*(chh+2)=0;      

      GlobalUnlock(hMem);
      //Code to copy data to clipboard

      //Set the data for clipboard
      //You can specify Formats like CF_BITMAP,CF_TEXT etc.
      SetClipboardData(iIsUnicode?CF_UNICODETEXT:CF_TEXT,hMem);
      //Close the clipboard
      CloseClipboard();
      GlobalFree(hMem);
   }
   #endif //_WIN32
   return 0;
}
#endif 
   
