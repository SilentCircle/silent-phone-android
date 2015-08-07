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

#ifndef _C_T_BASE_EDIT
#define _C_T_BASE_EDIT



#include "CTBase.h"
#include <stdio.h>

#ifdef __SYMBIAN32__
#include <e32std.h>//TDesC
#endif
//#include "../os/CTOsGui.h"
//#include "../os/CTOsGui.h"

//int     sprintf(char *, const char *, ...);

#include "../utils/utils.h"

#define TED_PWD 1
#define TED_MULTILINE 2
#define TED_NUMERIC 4
#define TED_TEXT  8

#define ED_PWD_STR "******"
//TODO in base class

#define CTStrU(A) CTStr((unsigned short *)&(A),sizeof(A)/2-1)
//#define CTStrU_SET(B,A) (B)->set(A,sizeof(A)/2-1)
#define _T_LEN(A) A,sizeof(A)/2-1

class CTStr: public CTStrBase{
public:
   CTStr(): pData(NULL),iLen(0){}
   CTStr(unsigned short *p, int iLen): pData((short *)p),iLen(iLen){}
   CTStr(const CTStrBase &b)
   {
      set(b);
   }
   inline void set(const CTStrBase &b)
   {
      pData=((CTStrBase &)b).getText();
      iLen=((CTStrBase &)b).getLen();
   }
   //void set(unsigned short *p){pData=(short *)p;iLen=wcslen(p);}
   void set(unsigned short *p, int iNLen){pData=(short *)p;iLen=iNLen;}
   virtual ~CTStr(){}
   inline short * getText(){return pData;}
   inline int getLen(){return iLen;}
protected:
   short *pData;
   int iLen;

};


int cmpU(CTStrBase *b1, CTStrBase *b2);

//static CTStr cStr(_T_LEN(TEXT(ED_PWD_STR)));
int cmpmyUnicode(short *src,short *dst,int iShorts);


class CTEditBase :public CTStrBase{
public:
   //vajagdziigs 
   //lai paraak biezi nevajadzetu lietot re malloc
   int iUpdateSize;
protected:
   int iLen;
   short *pData;
   int iMaxLen;
   int iMaxLenIsLocked;
   CTEditBase(int iMaxLen, short *buf);
public:
   CTEditBase(int iMaxLen);

   CTEditBase(const char *p=0,int iLen=0, int iIsUnicode=0);

   virtual ~CTEditBase(){
      if(pData && !iMaxLenIsLocked)
      {
         delete pData;
         pData=NULL;
      }
   }
   inline void dontRedraw()
   {
      iCannotRedraw++;
   }
   inline void canRedraw(int iUpdate=1)
   {
      iCannotRedraw--;
      if(iCannotRedraw==0 && iUpdate)
         onChange();

   }
   void reset();
   inline CTEditBase& setChar(int pos, int c)
   {
      if(pos>=0 && pos<iLen)
         pData[pos]=c;
      
      return *this;
      
   }
   inline int getChar(int c)
   {
      if(c>=iLen)
         return 0;
      return pData?pData[c]:0;
   }
   inline void addChar(int c)
   {
      short s=(short)c;
      addText((char *)&s,  1, 1);
   }

   inline void insertChar(int iAtPos, int c)
   {
      short s=(short)c;
      insertText(iAtPos, (char *)&s, 1, 1);
   }
   void trim();
   void remCharsFrom(int iPos, int iCharsToRemove=1);
   void remLastChar(int iCharsToRemove=1);
   virtual inline short * getText(){return pData;}
   virtual inline int getLen(){return iLen;}
   int setLen(int l){if(l>=iMaxLen)return -1;iLen=l;pData[l]=0;if(!iCannotRedraw)onChange();return 0;}
//int cmpmyUnicode(short *src,short *dst,int iShorts);
   int cmpN(const CTStrBase &b, int iChars);
   inline void setText(const CTStrBase &b)
   {
      iLen=0;
      addText((char *)((CTStrBase &)b).getText(),  ((CTStrBase &)b).getLen(), 1);
   }
   inline void setText(const char *buf, int iCharCount=0, int iIsUnicode=0)
   {
      iLen=0;
      addText(buf,  iCharCount, iIsUnicode);
   }

   unsigned int toUInt()
   {
      if(!iLen || !pData)return 0;
      getText();

      return strToUint((unsigned short*)pData);;
   }

   void addInt(int i, const char * format="%d");
   //retrn shorts added
   
   inline void addText(const CTStrBase &b)
   {
      addText((char *)((CTStrBase &)b).getText(),  ((CTStrBase &)b).getLen(), 1);
   }

   typedef int (FNC_ADD_TEXT_CALLBACK)(short *pUni, int iMaxLen, void *pUserData);
   void addText(FNC_ADD_TEXT_CALLBACK *fnc, int iMaxLenToAdd, void *pUserData);
   void addText(const char *buf, int iCharCount=0, int iIsUnicode=0);
   void *pOnChangeParam;
   int (*onChangeCB)(void *p);

   int  insertText(int iAtPos, char *buf, int iCharCount=0, int iIsUnicode=0);
   virtual int onChange(){if(onChangeCB && pOnChangeParam)return onChangeCB(pOnChangeParam);return 0;};
   
   char *getTextUtf8(char *pOut, int *iMaxLen);
   
#ifdef __SYMBIAN32__
   inline void addText(const TDesC &des)
   {
      if(des.Length()>0)
         addText((char *)des.Ptr(),des.Length(),1);
   }
   //TDesC16
   inline const TPtrC16 &des()
   {
      getText();
      sc16.Set((unsigned short*)pData,iLen);
      return sc16;
   }
   TPtrC16 sc16;
#endif
private:
   int checkSize(int iCharsToAdd, int *iPos);
protected:
   int iCannotRedraw;//TODO ja shis neljaut arii paarziimeet

};

static void repalceCRLF(CTEditBase *e)
{
   int i,iLen=e->getLen();
   short *p=e->getText();
   for(i=0;i<iLen;i++)
   {
      if(e->getChar(i)=='\\' && e->getChar(i+1)=='n')
      {
         p[i]='\r';
         p[i+1]='\n';
      }
   }
}


class CTStringsBase{
public:
   virtual int setGetTextById(const char *id, int idLen, CTEditBase &val, int iSet)=0;
};
int getText(char *p, int iMaxLen, CTStrBase *ed);


template <int T>
class CTEditBuf: public CTEditBase{
public:
   CTEditBuf():CTEditBase(T,(short *)&buf){buf[0]=0;}
   CTEditBuf(const char *p):CTEditBase(T,(short *)&buf){buf[0]=0;setText(p);}
   short buf[T];

};

class CTEditBuf2: public CTEditBase{
public:
   CTEditBuf2(short *buf, int iMaxLen):CTEditBase(iMaxLen,buf){buf[0]=0;}

};

#endif //_C_T_BASE_EDIT
