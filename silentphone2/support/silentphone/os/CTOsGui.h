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

#ifndef _C_GUI_OS_H
#define _C_GUI_OS_H

class CTDebugCB{
public:
   virtual void onDebug(void *p, char *psz, int i)=0;
};
class CTDebugBase{
public:
   CTDebugBase(){dbgPrt=0;}
   CTDebugCB *dbgPrt;
   void cDbg(void *psz, int i)
   {
      if(dbgPrt)dbgPrt->onDebug(this,(char *)psz,i);
   }
};

#pragma message("gui os compile---")
#ifndef _T_CONSOLE_
#if defined(__linux__) ||  defined(__APPLE__)  
#define _T_CONSOLE_
#endif
#endif
#include "../baseclasses/CTBase.h"
#ifndef _T_CONSOLE_
#ifdef __SYMBIAN32__//os
#include <w32std.h>
#include <string.h>

#ifdef __SERIES60__
#include <aknutils.h> 		// Fonts
#include <aknnotewrappers.h> 
#else //uiq
//#include <aknutils.h> 		// Fonts
//#include <aknnotewrappers.h> nav uiq
#endif

#include <gdi.h>
#elif defined(_WIN32)//os
#ifndef _WIN32_WCE
#include <windows.h>
#else
#include <aygshell.h>
#endif
#endif//os
#else
#include <string.h>
#include <stdio.h>


#endif //_T_CONSOLE_
#ifdef _T_CONSOLE_

#define CHAR_CNT_IN_BUF 0x1
#define getCharW getCharWos
#define getCharWX getCharWos
inline int getCharWos(unsigned int c,int)
{
   return 1;
}
#endif

class CTFontN{
public:
#ifdef __SYMBIAN32__ 
   #define CHAR_CNT_IN_BUF 0x100
   inline int getCharWos(unsigned int c, int)
   {
      if(c<' ')return 0;
      if(c<CHAR_CNT_IN_BUF)
      {
         if(bufCharW[c])return (int)bufCharW[c];
         {
            int ch=font?((CFont*)font)->CharWidthInPixels(TChar(c)):iAvgWidth;
            bufCharW[c]=ch;
            return ch;
         }
      }
      return font?((CFont*)font)->CharWidthInPixels(TChar(c)):iAvgWidth;
   }

#elif _WIN32
//#ifdef _WIN32_WCE
  // #define CHAR_CNT_IN_BUF 0x100
//#else 
#ifndef _T_CONSOLE_
   #define CHAR_CNT_IN_BUF 0x10000
//#endif
   /*
oldf
   */
   inline int getCharWX(unsigned int c, int hdc)
   {
      if(c<' ')return 0;
      HGDIOBJ oldf=NULL;
      if(hdc==0)
      {
   //if(CHAR_CNT_IN_BUF!=0x10000)
         hdc=(int)GetDC(0);
         oldf=SelectObject((HDC)hdc,font);
         
      }
      int ret=0;
      if(!GetCharWidth32((HDC)hdc,c,c,&ret))
         ret=(iAvgWidth+iHi+1)>>1;//iHi-1;//10;
      if(oldf)
      {
         SelectObject((HDC)hdc,oldf);
         ReleaseDC(0,(HDC)hdc);
      }
      return  ret;
   }

#endif //_T_CONSOLE_
   inline int getCharWos(unsigned int c, int iHdc)
   {


      if(c<CHAR_CNT_IN_BUF)
      {
         if(bufCharW[c])return (int)bufCharW[c];
         {
            int ch=getCharWX(c,iHdc);
            bufCharW[c]=ch;
            return ch;
         }
      }
     
      return getCharWX(c,iHdc);

      //return iAvgWidth+2;
   }
#else
#define CHAR_CNT_IN_BUF 0x1
#define getCharW getCharWos
#define getCharWX getCharWos
inline int getCharWos(unsigned int c,int)
{
   return 1;
}

#endif
private:
   void initFont();
   unsigned char bufCharW[CHAR_CNT_IN_BUF+1];
public:
   void *font;//os HFONT, CFont ..
   int iHi;
   int iAvgWidth;
   int iIsSystem;
   int iAscentInPixels;
#define getCharW getCharWos
   CTFontN *foSmaller;
   CTFontN(void *f, int iHi, int iAvgWidth):font(f),iHi(iHi),iAvgWidth(iAvgWidth)
   {
      foSmaller=NULL;

      iAscentInPixels=0;
#if defined(__SYMBIAN32__)
      iAscentInPixels=((CFont*)f)->AscentInPixels();

#endif

      iIsSystem=0;
      initFont();
   }
   ~CTFontN()
   {
#if defined(_WIN32) && !defined(_T_CONSOLE_ ) && !defined(__SYMBIAN32__)
      if(!iIsSystem)
         DeleteObject((HGDIOBJ)font);
      //TODO check is system
#endif
   }


   static int getTextWidth(CTFontN *f,CTStrBase *b)
   {

      if(f==NULL)return 0;
      if(f->iHi==0)
      {
          f->iAvgWidth=f->getCharW('@',0);//pda
          f->iHi=f->iAvgWidth*145/100;//if pda
         // f->iAvgWidth=f->getCharW('A',0)
          //f->iHi=f->iAvgWidth*2;//if pda
      }
     // return f->iAvgWidth*b->getLen();
      int w=0;
      int i=0;
      unsigned short *p=(unsigned short*)b->getText();
      int L=b->getLen();
      for(i=0;i<L;i++)
      {
         w+=f->getCharW(*p,0);
         p++;
      }
      return w;
   }
};



class CTDeviceCtx{
   int iCreated;
public:
   int iDrawFromY;
   int iDrawToY;
   void *cTHdc;
   CTDeviceCtx(int iDc, CTFontN *cFont=NULL
      , int iBckColor=0, int iTextColor=0, int iPenColor=0)
      :iDeviceCtx(iDc),font(cFont),iBckColor(iBckColor),iTextColor(iTextColor),iPenColor(iPenColor)
   {
      iDrawFromY=0;
      iDrawToY=5000;
      cTHdc=NULL;
      iCreated=0;
   }
#if defined(_WIN32) && !defined(__SYMBIAN32__)  && !defined(_T_CONSOLE_)
private:
   HWND hwnd;
public:
   CTDeviceCtx(HWND hwnd=NULL)
      :hwnd(hwnd),iDeviceCtx(0),font(0),iBckColor(0xffffff),iTextColor(0),iPenColor(0)
   {
      iDrawFromY=0;
      iDrawToY=5000;
      cTHdc=NULL;
      iDeviceCtx=(int)GetDC(hwnd);
      iCreated=1;
   }
   ~CTDeviceCtx()
   {
      if(iCreated)ReleaseDC(hwnd,(HDC)iDeviceCtx);
   }
#endif
   int iDeviceCtx;//hdc, CWindowGc
   CTFontN *font;
   int iBckColor;
   int iTextColor;
   int iPenColor;
};



#ifndef _T_CONSOLE_

#ifdef __SYMBIAN32__ 

 



class CTSetFont{
   CTDeviceCtx & iBase;
   CTFontN *pOldF;

public:
   CTSetFont(CTDeviceCtx & iBase, CTFontN *f):iBase(iBase),pOldF(NULL)
   {
      //if win
      if(iBase.font!=f && f)
      {
         pOldF=iBase.font;
         iBase.font=f;
         ((CWindowGc *)iBase.iDeviceCtx)->UseFont((CFont*)f->font);
      }
   }
   ~CTSetFont()
   {
      if(pOldF)
      {
         iBase.font=pOldF;
         ((CWindowGc *)iBase.iDeviceCtx)->UseFont((CFont*)pOldF->font);
      }
   }

};
static int setTextColor(CTDeviceCtx & iBase, int iCol)
{
   int io=iBase.iTextColor;
   if(iBase.iTextColor!=iCol)
   {
      iBase.iPenColor=iBase.iTextColor=iCol;
      ((CWindowGc *)iBase.iDeviceCtx)->SetPenColor(TRgb((unsigned int )iCol));
   }
   return io;
}

class CTSetPenCol{
   CTDeviceCtx & iBase;
   int iOldC;
   int iRestore;
public:
   CTSetPenCol(CTDeviceCtx & iBase, int iCol, int iSet=1):iBase(iBase),iRestore(0)
   {
      iBase.iTextColor=iBase.iPenColor;
      if(iCol!=iBase.iPenColor && iSet)
      {
         iRestore=1;
         iOldC=iBase.iPenColor;
         iBase.iTextColor=iBase.iPenColor=iCol;
         ((CWindowGc *)iBase.iDeviceCtx)->SetPenColor(TRgb(iCol));
      }
   }
   ~CTSetPenCol()
   {
      if(iRestore)
      {
         iBase.iTextColor=iBase.iPenColor=iOldC;
         //iBase.iTextColor=iOldC;
         ((CWindowGc *)iBase.iDeviceCtx)->SetPenColor(TRgb(iOldC));
      }
   }
};
#define CTSetTextCol CTSetPenCol
/*
class CTFont{
   CTFont(int iBase, void* f)
   {
   }
};
*/
TDes  &getImgFn(TDes & d,int id);
class CTBitmap{
public:
   int iHasAlpha;
   //CWsBitmap
   //CFbsBitmap
   CTBitmap(CFbsBitmap *bmp, int iLoad=1)
      :bitmap(bmp),iSizeX(0),iSizeY(0),iImgCnt(1)
   {
      iHasAlpha=0;
      mask=NULL;
      iCreated=0;
      iCanDraw=1;
      if(iLoad)load(0, 0);else {
         iCreated=1;
          TSize sz;
          sz=bitmap->SizeInPixels();
          iSizeX=sz.iWidth; iSizeY=sz.iHeight;
      }
   }
   CTBitmap(unsigned short *fn)
      :bitmap(NULL),iSizeX(0),iSizeY(0),iCanDraw(0),iImgCnt(1)
   {
      iHasAlpha=0;
      mask=NULL;
      iCreated=0;
   }
   CTBitmap(int id, int iIsGif=0)//TODO maskas
      :bitmap(NULL),iSizeX(0),iSizeY(0),iCanDraw(0),iImgCnt(1)
   {
      iHasAlpha=0;
      mask=NULL;
      iCreated=1;
      bitmap=new CFbsBitmap();
      load(id,0);

   }
   ~CTBitmap(){if(iCreated)delete bitmap;}
   inline void bitBlt(CTDeviceCtx & iBase, int x,int y, int cx, int cy, int iPosX, int iPosY)
   {
      //BitBlt(const TPoint &aDestination, const CFbsBitmap *aBitmap, const TRect &aSource);
      if(iCanDraw){
         if(mask)
         {//mask->getBitmap()
            ((CWindowGc *)iBase.iDeviceCtx)->
            BitBltMasked(TPoint(x,y),bitmap,TRect(iPosX,iPosY,iPosX+cx,iPosY+cy)
            , mask->getBitmap(),1);
         }
         else if(cx==iSizeX && cy==iSizeY && iPosX==0 && iPosY==0)
         {
            ((CWindowGc *)iBase.iDeviceCtx)->BitBlt(TPoint(x,y),bitmap);
         }
         else
         {
            ((CWindowGc *)iBase.iDeviceCtx)->BitBlt(TPoint(x,y),bitmap,TRect(iPosX,iPosY,iPosX+cx,iPosY+cy));
         }
      }
      
   }
   inline int getPixColor(int x, int y){return 0;}//GetPixel(hdc,x,y);}
   CTBitmap *mask;
   int getH(){return iSizeY;}
   int getW(){return iSizeX;}
   int canDraw(){return iCanDraw;}
   CFbsBitmap *getBitmap(){return bitmap;}
   CTBitmap(const TDesC &aFileName, int id)
      :bitmap(NULL),iSizeX(0),iSizeY(0),iCanDraw(0),iImgCnt(1)
   {
      mask=NULL;
      iCreated=1;
      bitmap=new CFbsBitmap();
      load(aFileName,id);

   }

private:
   int load(const TDesC &aFileName, int id)
   {
      int e=bitmap->Load(aFileName,id,EFalse);
      
      if(!e)
      {
         TSize sz;
         sz=bitmap->SizeInPixels();
         iSizeX=sz.iWidth; iSizeY=sz.iHeight;
         iCanDraw=1;
      }
      return !iCanDraw;
   }
   int load(int id, int iIsGif)
   {

   int e;
      if(iCreated)
      {
         

         TBuf<64> b;
         e=bitmap->Load(getImgFn(b,0),id,EFalse);
         if(e)e=bitmap->Load(getImgFn(b,1),id,EFalse);
         if(e)
         {
            e=bitmap->Load(getImgFn(b,2),id,EFalse);
            if(e)
            {
               e=bitmap->Load(_L("z:\\system\\data\\avkon.mbm"));
               iCreated=0;
            }

         }
         
         iCanDraw=KErrNone==e;
      }
      

      if(iCanDraw)
      {
          TSize sz;
          sz=bitmap->SizeInPixels();
          iSizeX=sz.iWidth; iSizeY=sz.iHeight;
      }
      return !iCanDraw;
   }
   CFbsBitmap *bitmap;

   int iSizeX;
   int iSizeY;
   int iCanDraw;
   int iCreated;
public:
   int iImgCnt;

};

inline void drawLine(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1)
{
  // CWindowGc *gc=(CWindowGc *)iBase;
   ((CWindowGc *)iBase.iDeviceCtx)->DrawLine(TPoint(x, y), TPoint(x1, y1));
   ///Rectangle((HDC)iBase,x,y,x1,y1);
}

//void DrawLine(const TPoint& aStart,const TPoint& aEnd);
inline void drawElipse(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1)
{
   //CWindowGc *gc=(CWindowGc *)iBase;
   ((CWindowGc *)iBase.iDeviceCtx)->DrawEllipse(TRect( x,  y,  x1,  y1));
   
}
inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect)
{
   
   //CWindowGc *gc=(CWindowGc *)iBase;
   ((CWindowGc *)iBase.iDeviceCtx)->DrawText(TPtrC16((const unsigned short *)((CTStrBase &)b).getText(),((CTStrBase &)b).getLen()),TPoint(rect.x, rect.y1));
}

inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect, const CTAlign &a)
{
   
   CWindowGc *gc=(CWindowGc *)iBase.iDeviceCtx;
   CGraphicsContext::TTextAlign aHoriz=CGraphicsContext::ELeft;
   int iFontHi=iBase.font->iHi;

   int iTopM=iBase.font->iAscentInPixels;//iFontHi*95/100;//rect.y1-rect.y;

   if(iFontHi<rect.h)//TODO test
   {
      if(a.iFlag & CTAlign::bottom)
      {
         iTopM+=(rect.h-iFontHi);
      }

      else if(a.iFlag & CTAlign::vcenter)
      {
         iTopM=(rect.h+iTopM)/2;
      }
      if(a.iFlag & CTAlign::top)
      {
         iTopM=iFontHi;//
      }
   }
   else if(a.iFlag & CTAlign::bottom && rect.h<iFontHi)
//         iTopM=rect.h-(iFontHi-iTopM);///20-1;
         iTopM-=(iFontHi-rect.h);//-(iFontHi-iTopM);///20-1;

   if(iTopM<0)return;


   TRect r(rect.x, rect.y, rect.x1, rect.y1);


   if(a.iFlag & CTAlign::hcenter)aHoriz=CGraphicsContext::ECenter;
   if(a.iFlag & CTAlign::right)aHoriz=CGraphicsContext::ERight;

   gc->DrawText(TPtrC16((const unsigned short *)((CTStrBase &)b).getText(),((CTStrBase &)b).getLen()),r,iTopM,aHoriz);//,TTextAlign aHoriz=ELeft,TInt aLeftMrg=0);

}
inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect, int iXOff, int iYOff)
{
   TRect r(rect.x, rect.y, rect.x1, rect.y1);
   CWindowGc *gc=(CWindowGc *)iBase.iDeviceCtx;
   //GraphicsContext::TTextAlign aHoriz=CGraphicsContext::ELeft;
   int hi=rect.y1-rect.y+iYOff;

   int iFontHi=iBase.font->iHi;

   if(iYOff<0)iYOff=0;
   if(iXOff<0)iXOff=0;

   int iConstOff;//=iFontHi/4;//def bija 3
   iConstOff=iFontHi-iBase.font->iAscentInPixels;//f->AscentInPixels();
   //kas tas
  // fontUsed->AscentInPixels()/2; 

   if(iYOff)
      hi=(hi-iConstOff)-iYOff;
   else if(hi<iFontHi+iConstOff)
   {
      hi=iFontHi;
   }

  // DrawText((HDC)iBase,(const unsigned short *)((CTStrBase &)b).getText(),((CTStrBase &)b).getLen(),&rc,f);
   gc->DrawText(TPtrC16((const unsigned short *)((CTStrBase &)b).getText(),((CTStrBase &)b).getLen())
      ,r,hi,CGraphicsContext::ELeft,iXOff);//,TTextAlign aHoriz=ELeft,TInt aLeftMrg=0);
}
//CTGui::fillRect(iBase,x,y, x1,y1,0xcecece);
class CTGui{
public:
   static inline  void fillRect(CTDeviceCtx & iBase, CTRect &r, int iCol)
   {
      fillRect(iBase,r.x,r.y,r.x1,r.y1,iCol);

   }
   static inline  void fillRect(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1, int iCol)
   {
       CWindowGc *gc=(CWindowGc *)iBase.iDeviceCtx;
       gc->SetBrushColor( TRgb(iCol) );
       gc->SetBrushStyle( CGraphicsContext::ESolidBrush);//ENullBrush);//ESolidBrush );
       gc->Clear(TRect( x,  y,  x1,  y1));//shis iekraaso fonu
       gc->SetBrushStyle( CGraphicsContext::ENullBrush);//ENullBrush);//ESolidBrush );
       gc->SetBrushColor( KRgbWhite );
   }
};

#elif  defined(_WIN32_WCE) ||  defined(_WIN32)


inline void drawLine(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1)
{
   MoveToEx((HDC)iBase.iDeviceCtx,x,y,NULL);
   LineTo((HDC)iBase.iDeviceCtx,x1,y1);

}

inline void drawElipse(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1)
{
   Ellipse((HDC)iBase.iDeviceCtx,x,y,x1,y1);
}
inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect)
{
   //int f=;//=a.iFlag;
   //RECT rc={rect.x,rect.y,rect.x1,rect.y1};
#ifdef _WIN32_WCE
   RECT rc={rect.x,rect.y-2,rect.x1,rect.y1};
#else
   RECT rc={rect.x,rect.y,rect.x1,rect.y1};
#endif
   DrawTextW((HDC)iBase.iDeviceCtx,(LPCWSTR)((CTStrBase &)b).getText(),((CTStrBase &)b).getLen(),&rc,DT_SINGLELINE|DT_TOP|DT_LEFT|DT_NOPREFIX);

   
//   DrawText((HDC)iBase,(const unsigned short *)b.getText(),b.getLen(),&rc,DT_SINGLELINE|DT_TOP|DT_LEFT);
}

#ifndef _WIN32_WCE
   HBITMAP SHLoadImageFile(void* fn);
   

#endif
   HBITMAP SHLoadImage(char *p, int iLen);
class CTHdc{
   int w,h,bits;
   HDC hdcSrc;
   void *old;
public:
   HDC hdc;
   HBITMAP hBmp;
   void * pWinData;

   CTHdc(int w, int h, int bits,HDC hdcSrc=NULL)
      :hdc(NULL)
      ,old(NULL)
      ,hBmp(NULL)
      ,pWinData(NULL),w(w),h(h),bits(bits)
      ,hdcSrc(hdcSrc)
   {
      constr();
   }
   void setNewSrcHdc(HDC hdcNew)
   {
      if(hdcSrc!=hdcNew)
      {
         hdcSrc=hdcNew;
         int ow=w,oh=h;
         rel();
         reset(ow,oh,bits);

         
      }
   }
   void resetBits(int iBits)
   {
      reset(w,h,iBits);
   }
   void reset(int nw,int nh, int iBits=0)
   {
      if(iBits==0)iBits=bits;
      if(nw!=w || nh!=h || iBits!=bits || hdc==NULL)
      {
         
         rel();
         
         if(w>0 && h>0)
         {
            bits=iBits;
            w=nw;h=nh;
            constr();
         }
      }
   }
   void clear(int iByte=255)
   {
      if(w && h && hdc && pWinData)
      {
         memset(pWinData,iByte,getMemSize());
      }
   }
   inline int getMemSize(){return w*h*bits/8;}
   ~CTHdc()
   {
      rel();
   }
   inline int getW(){return w;}
   inline int getH(){return h;}
   inline int getBitsPP(){return bits;}
private:
   void constr()
   {
      HDC xdc=hdcSrc?NULL:GetDC(0);
      hdc=CreateCompatibleDC(hdcSrc?hdcSrc:xdc);
      if(xdc)ReleaseDC(0,xdc);
      BITMAPINFO bmi;
      bmi.bmiHeader.biSize = sizeof( BITMAPINFOHEADER );
      bmi.bmiHeader.biWidth = w;
      bmi.bmiHeader.biHeight = h;
      bmi.bmiHeader.biPlanes = 1;
      bmi.bmiHeader.biBitCount = bits;
      bmi.bmiHeader.biCompression = BI_RGB;
      bmi.bmiHeader.biSizeImage = 0; 
      bmi.bmiHeader.biXPelsPerMeter = 0; 
      bmi.bmiHeader.biYPelsPerMeter = 0; 
      bmi.bmiHeader.biClrUsed = 0; 
      bmi.bmiHeader.biClrImportant = 0;
     //NULL
      hBmp=CreateDIBSection( hdc, &bmi, DIB_RGB_COLORS, (void **)&pWinData, NULL, 0 );
      old=SelectObject(hdc,hBmp);
   }
   void rel()
   {
      if(hdc)
      {
         if(hBmp)
         {
            if(old)
               SelectObject(hdc,old);
            old=NULL;
            DeleteObject(hBmp);
            hBmp=NULL;

         }
         DeleteDC(hdc);
         hdc=NULL;
         
      }
   }
};
#ifdef _WIN32_WCE
//#define T_REINIT_DC
#endif
class CTBitmap{
   unsigned short fname[256];
//   CTEditBase fname;
   char *ptr;
   int iSizeBuf;
public:
   CTBitmap(unsigned short *fn)
      :hBmp(NULL),hdc(NULL),old(NULL),iSizeX(0),iSizeY(0),iCanDraw(0),iImgCnt(1)
   {
      iHasAlpha=0;
      iSizeBuf=0;
      //fname.setText((char*)fn,0,1);;
      //memcpy(&fname[0],fn,
      for(int i=0;;i++){
         fname[i]=fn[i];
         if(!fname[i])break;
      }
    //  wsprintfW((LPWSTR)&fname[0],(LPCWSTR)fn);
      load(0,0,fn);
   }
   CTBitmap(char *p,int iSize)
      :hBmp(NULL),hdc(NULL),old(NULL),iSizeX(0),iSizeY(0),iCanDraw(0),iImgCnt(1)
   {
      iHasAlpha=0;
      iSizeBuf=iSize;
      ptr=p;


      fname[0]=0;;
      load(p,iSize);
   }
   CTBitmap(int id, int iIsGif=0)
      :hBmp(NULL),hdc(NULL),old(NULL),iSizeX(0),iSizeY(0),iCanDraw(0),iImgCnt(1)
   {
      iHasAlpha=0;
      fname[0]=0;
      load(id,iIsGif,NULL);
   }
   ~CTBitmap()
   {
      if(old)
      {
         SelectObject(hdc ,old);
         DeleteDC(hdc);
         
      }
      if(hBmp)DeleteObject(hBmp);
      if(m)delete m;
   }
   class CTGetHdcBMP{
      HDC *phdc;
      void *old;
   public:
      CTGetHdcBMP(HBITMAP hBmp, HDC *hdc){
         *hdc   = CreateCompatibleDC(NULL);
         old= SelectObject(*hdc ,hBmp);
         phdc=hdc;
      }
      ~CTGetHdcBMP(){
         SelectObject(*phdc ,old);
         DeleteDC(*phdc);
         phdc=NULL;
      }

   };
   inline int getPixColor(int x, int y){
#ifdef T_REINIT_DC
      CTGetHdcBMP zzz(hBmp,&hdc);
#endif
      return GetPixel(hdc,x,y);}
   int canDraw(){return iCanDraw;}
   int iFirstTime;
   CTHdc *m;
   int iHasAlpha;
   inline void bitBlt(CTDeviceCtx & iBase, int x,int y, int cx, int cy, int iPosX, int iPosY)
   {
      if(y>=iBase.iDrawToY || iBase.iDrawFromY>y+cy)return;
#ifdef T_REINIT_DC
      CTGetHdcBMP zzz(hBmp,&hdc);
#endif
      if(iCanDraw==1)
      {
        // GetRValue
         if(iFirstTime==3 && m)
         {
             BitBlt((HDC)iBase.iDeviceCtx,x,y,cx,cy,m->hdc,iPosX, iPosY,SRCCOPY);
             return;
         }
         if(iFirstTime==1)
         {
            
            iFirstTime=0;

         }

         if(iHasAlpha){
            unsigned int val,_m;
#define CALC_MASK_A val=(255-_m)*(*pBck)+(*pImg)*(_m);val>>=8;*pImg=(unsigned char)val;
#define CALC_MASK_A2 val=(255-_m)*(*pBck)+(*pImg)*(_m);val>>=8;*pBck=(unsigned char)val;
/*            
            if(iBase.cTHdc && ((CTHdc*)(iBase.cTHdc))->getBitsPP()==32){
               CTHdc *phdc=(CTHdc*)iBase.cTHdc;
               CTHdc img(cx,cy,32);
               BitBlt(img.hdc,0,0,cx,cy,(HDC)hdc,iPosX, iPosY,SRCCOPY);
               int stride=phdc->getW()*4;
               unsigned char *pImg=(unsigned char *)img.pWinData;
               unsigned char *pMask=(unsigned char *)pImg+3;
               unsigned char *pBck=(unsigned char *)phdc->pWinData+iPosX*4+iPosY*stride;
               unsigned int val,_m;

               int i,j;
               for(j=0;j<cy;j++){
                  for(i=0;i<cx;i++)
                  {
                     _m=*pMask;
                     if(_m==0){
                        *(int*)pBck=*(int*)pBck;
                        pImg+=4,pBck+=4;
                     }else if(_m==255){
                        pImg+=4,pBck+=4;
                     }
                     else{
                        CALC_MASK_A2;pImg++,pBck++;
                        CALC_MASK_A2;pImg++,pBck++;
                        CALC_MASK_A2;pImg+=2,pBck+=2;
                     }
                     pMask+=4;
                  }
                  pBck+=stride-cx*4;
               }

               //phdc->p
               //BitBlt((HDC)iBase.iDeviceCtx,x,y,cx,cy,img.hdc,0, 0,SRCCOPY);
               return;
            }
            else */
            {
            CTHdc bck(cx,cy,32);//(iSizeX, iSizeY,32);
            BitBlt(bck.hdc,0,0,cx,cy,(HDC)iBase.iDeviceCtx,x, y,SRCCOPY);
            CTHdc img(cx,cy,32);
            BitBlt(img.hdc,0,0,cx,cy,(HDC)hdc,iPosX, iPosY,SRCCOPY);
            unsigned char *pImg=(unsigned char *)img.pWinData;
          //  unsigned char *pMask=(unsigned char *)pImg+3;
            unsigned char *pBck=(unsigned char *)bck.pWinData;
            unsigned char *pEnd=(unsigned char *)bck.pWinData+cx*cy*4;
            
            while(pBck<pEnd){
               _m=pImg[3];
               if(_m==0){
                  *(int*)pImg=*(int*)pBck;
                  pImg+=4,pBck+=4;
               }else if(_m==255){
                  pImg+=4,pBck+=4;
               }
               else{
                  CALC_MASK_A;pImg++,pBck++;
                  CALC_MASK_A;pImg++,pBck++;
                  CALC_MASK_A;pImg+=2,pBck+=2;
               }
            }

            BitBlt((HDC)iBase.iDeviceCtx,x,y,cx,cy,img.hdc,0, 0,SRCCOPY);
            return;
            }
         }
         
#undef CALC_MASK_A
#undef CALC_MASK_A2
         
         if(mask && mask->canDraw())
         {
#ifdef T_REINIT_DC
            CTGetHdcBMP mmm(mask->hBmp,&mask->hdc);
#endif

            int x1=iPosX%mask->getW();
            int y1=iPosY%mask->getH();

               unsigned int px1=GetPixel(mask->hdc, x1,y1);
               unsigned int px2=GetPixel(mask->hdc, x1+cx-1,y1);
               unsigned int px3=GetPixel(mask->hdc, x1+cx-1,y1+cy-1);
               unsigned int px4=GetPixel(mask->hdc, x1,y1+cy-1);
               unsigned int pxc=GetPixel(mask->hdc,x1+(cx-1)/2,y1+(cy-1)/2);
            
            if(iPosY>20 && iPosX>20)
            {

               if(px1==0  
                  && px2==0
                  && px3==0
                  && px4==0
                  && pxc==0
                  )
               {
                  //paatrina ramjiem
                  BitBlt((HDC)iBase.iDeviceCtx,x,y,cx,cy,hdc,iPosX, iPosY,SRCCOPY);
                  return;
               }
               if((px1==0xffffff00 && px2==0xffffff00  && px3==0xffffff00   && px4==0xffffff00   && pxc==0xffffff00) ||
                  (px1==0xffffff && px2==0xffffff  && px3==0xffffff   && px4==0xffffff   && pxc==0xffffff))
               {                  //paatrina ramjiem
                   //nezimmee jo jaatstaj backg
                  return;
               }
}            
            {
            CTHdc m(cx,cy,32);
            BitBlt(m.hdc,0,0,cx,cy,(HDC)mask->hdc,x1, y1,SRCCOPY);
            CTHdc bck(cx,cy,32);//(iSizeX, iSizeY,32);
            BitBlt(bck.hdc,0,0,cx,cy,(HDC)iBase.iDeviceCtx,x, y,SRCCOPY);
            CTHdc img(cx,cy,32);
            BitBlt(img.hdc,0,0,cx,cy,(HDC)hdc,iPosX, iPosY,SRCCOPY);
            unsigned char *pMask=(unsigned char *)m.pWinData;
            unsigned char *pImg=(unsigned char *)img.pWinData;
            unsigned char *pBck=(unsigned char *)bck.pWinData;

               if(iPosY>20 && iPosX>20 && (
                  (px1==0x80808000 && px2==0x80808000  && px3==0x80808000   && px4==0x80808000   && pxc==0x80808000) ||
                  (px1==0x808080 && px2==0x808080  && px3==0x808080   && px4==0x808080   && pxc==0x808080)))

               {

                  //paatrina ramjiem
                  #define OP_AVG_BIN(a, b, dst) dst = ( ((a)|(b)) - ((((a)^(b))&0xFEFEFEFE)>>1) )
                  unsigned int *pI=(unsigned int *)pImg;
                  unsigned int *pB=(unsigned int *)pBck;
                  int i,sz=cx*cy*4;
                  for(i=0;i<sz;i+=4)
                  {
                     OP_AVG_BIN(pI[0],pB[0],pI[0]);
                     pI++;pB++;
                  }
                  //paatrina ramjiem
                   //nezimmee jo jaatstaj backg

               }else {
            
               int i,sz=cx*cy*4;
               int val;
   #define CALC_MASK {{val=(255-*pMask)*(*pImg)+(*pBck)*(*pMask);val>>=8;*pImg=(unsigned char)val;}}

               for(i=0;i<sz;i+=4)
               {
                  if(*pMask<10){
                     pImg+=4,pBck+=4,pMask+=4;
                     continue;
                  }
                  if(*pMask>245){
                     *(int*)pImg=*(int*)pBck;
                     pImg+=4,pBck+=4,pMask+=4;
                     continue;
                  }

                  CALC_MASK
                  pImg++,pBck++,pMask++;
                  CALC_MASK
                  pImg++,pBck++,pMask++;
                  CALC_MASK
                  pImg+=2,pBck+=2,pMask+=2;
                  //i+=4;

               }
               }
          
            
 

            
            BitBlt((HDC)iBase.iDeviceCtx,x,y,cx,cy,img.hdc,0, 0,SRCCOPY);
            }
         }
         else
           BitBlt((HDC)iBase.iDeviceCtx,x,y,cx,cy,hdc,iPosX, iPosY,SRCCOPY);

      }
      //drawRect(iBase, x, y, x+cx, y+cy);
   }
   inline void stretchBlt(CTDeviceCtx & iBase, int x,int y, int cx, int cy, int con)
   {
      if(iCanDraw)
         StretchBlt((HDC)iBase.iDeviceCtx,x,y,cx,cy,hdc,con, con,iSizeX-con*2,iSizeY-con*2,SRCCOPY);
   }
   CTBitmap *getCopy()
   {
      CTBitmap *b;
      if(fname[0])
      {
        b=new CTBitmap(&fname[0]);
      }
      else
      {
         b= new CTBitmap(ptr,iSizeBuf);
      }
      if(b->canDraw())
      {
         b->iImgCnt=iImgCnt;
         return b;
      }
      delete b;
      return NULL;

   }
   int getH(){return iSizeY;}
   int getW(){return iSizeX;}
   void* getBmp(){return  hBmp;}
   void* getDC(){return  hdc;}
   int iImgCnt;
   CTBitmap *mask;
private: 

   int onBitmap()
   {
     // hBmp=NULL;
      iFirstTime=1;
      mask=NULL;
      m=NULL;
      if(!hBmp)return -1;
      iCanDraw=1;

      BITMAP bmp;
      GetObject(hBmp,   sizeof(bmp), &bmp);
      iSizeX = bmp.bmWidth;
      iSizeY = bmp.bmHeight;
#ifndef T_REINIT_DC
      hdc   = CreateCompatibleDC(NULL);
      old= SelectObject(hdc ,hBmp);
#endif
      return 0;
   }
   int load(char *p,int iSize)
   {
      //iCanDraw=2;return 0;
      hBmp=SHLoadImage(p, iSize);
      return onBitmap();
   }


   
   int load(int id, int iIsGif ,unsigned short *fn)
   {
      //iCanDraw=2;return 0;
      if(fn)
      {
#ifdef _WIN32_WCE
         //??hBmp=SHLoadImageFile((LPCTSTR)fn);
         HBITMAP SHLoadImageFilePng(void* fn);
         hBmp=SHLoadImageFilePng((void*)fn);
         if(!hBmp)hBmp=SHLoadImageFile((LPCTSTR)fn);
#else
         hBmp=SHLoadImageFile(fn);

#endif

      }
      else
      //SHLoadImageFile
      if(iIsGif)
      {
#ifdef _WIN32_WCE
         hBmp=SHLoadImageResource(GetModuleHandle(0),   id);
#endif
      }
      else
         hBmp=LoadBitmap(GetModuleHandle(0),   MAKEINTRESOURCE(id));

      return onBitmap();

   }

   HBITMAP hBmp;
   HDC hdc;
   void *old;
   int iSizeX;
   int iSizeY;
   int iCanDraw;

};


inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect,const CTAlign &a)
{
   if(rect.y>=iBase.iDrawToY || iBase.iDrawFromY>rect.y1)return;
   //return ;
   int f=DT_SINGLELINE|DT_NOPREFIX;//=a.iFlag;
   RECT rc={rect.x,rect.y,rect.x1,rect.y1};
   /*
#define DT_TOP              0x00000000
#define DT_LEFT             0x00000000
#define DT_CENTER           0x00000001
#define DT_RIGHT            0x00000002
#define DT_VCENTER          0x00000004
#define DT_BOTTOM           0x00000008
   */
#define SET_AL_F(A,B) if(a.iFlag & CTAlign::A)f|=(B)
   SET_AL_F(top,DT_TOP);
   SET_AL_F(left,DT_LEFT);
   SET_AL_F(right,DT_RIGHT);
   SET_AL_F(bottom,DT_BOTTOM);
   SET_AL_F(vcenter,DT_VCENTER);
   SET_AL_F(hcenter,DT_CENTER);
   //if(a.iFlag & CTAlign::top)f|=DT_TOP;

   DrawTextW((HDC)iBase.iDeviceCtx,(LPCWSTR)((CTStrBase &)b).getText(),((CTStrBase &)b).getLen(),&rc,f);
   
}
inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect, int iXOff, int iYOff)
{
   if(rect.y>=iBase.iDrawToY || iBase.iDrawFromY>rect.y1)return;
   int f=DT_SINGLELINE|DT_NOPREFIX;//=a.iFlag;
   RECT rc={rect.x,rect.y,rect.x1,rect.y1};
   f|=(iYOff>0)?DT_BOTTOM:DT_TOP;
   f|=(iXOff>0)?DT_RIGHT:DT_LEFT;
   
   DrawTextW((HDC)iBase.iDeviceCtx,(LPCWSTR)((CTStrBase &)b).getText(),((CTStrBase &)b).getLen(),&rc,f);
}
//CTGui::fillRect(iBase,x,y, x1,y1,0xcecece);
class CTGui{
public:
   static inline  void fillRect(CTDeviceCtx & iBase, CTRect &r, int iCol)
   {
      fillRect(iBase,r.x,r.y,r.x1,r.y1,iCol);

   }
   static inline  void fillRect(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1, int iCol)
   {
      if(iCol==0xffffff)
      {
         PatBlt((HDC)iBase.iDeviceCtx,x,y,x1-x,y1-y,WHITENESS);
      }
      else if(iCol==0)
      {
         PatBlt((HDC)iBase.iDeviceCtx,x,y,x1-x,y1-y,BLACKNESS);
      }
      else
      {
         HBRUSH hbr=CreateSolidBrush(iCol);
         RECT r={x,y,x1,y1};
         FillRect((HDC)iBase.iDeviceCtx,&r,hbr);
         DeleteObject(hbr);
      }
   }
};
//#define SET_COLOR(a,_rgb) SetTextColor((HDC)a,_rgb)//TODO class

static int setTextColor(CTDeviceCtx & iBase, int iCol)
{
   int io=iBase.iTextColor;
   if(iBase.iTextColor!=iCol)
   {
      iBase.iTextColor=iCol;
      SetTextColor((HDC)iBase.iDeviceCtx,iCol);
   }
   return io;
}

class CTSetFont{
   void *oldObj;
   CTFontN *fOld;
   CTDeviceCtx & iBase;

public:
   CTSetFont(CTDeviceCtx & iBase, CTFontN *f):oldObj(NULL),iBase(iBase)
   {
      if(f!=iBase.font && f)
      {
         fOld=iBase.font;
         iBase.font=f;
         oldObj=SelectObject((HDC)iBase.iDeviceCtx,f->font);
      }
     
   }
   ~CTSetFont()
   {
      if(oldObj)//win
      {
         iBase.font=fOld;
         SelectObject((HDC)iBase.iDeviceCtx,oldObj);
      }
   }

};

//CTFontN
class CTSetTextCol{
   CTDeviceCtx &iBase;
   int iOCol;
   int iRetore;
public:
   CTSetTextCol(CTDeviceCtx & iBase, int col, int iSet=1):iBase(iBase),iRetore(0)
   {
      if(col!=iBase.iTextColor && iSet)
      {
         iRetore=1;
         iOCol=iBase.iTextColor;
         iBase.iTextColor=col;
         //iOCol=
            SetTextColor((HDC)iBase.iDeviceCtx,col);
      }
   }
   ~CTSetTextCol()
   {
      if(iRetore)
      {
         iBase.iTextColor=iOCol;
         SetTextColor((HDC)iBase.iDeviceCtx,iOCol);
      }
   }

};

class CTSetPenCol{
   HDC hdc;
   HGDIOBJ old;
   HPEN hPen;
   int iOCol;
public:
   CTSetPenCol(CTDeviceCtx & iBase, int col, int iSet=1):old(NULL)
   {
      if(col!=iBase.iPenColor)
      {
         iOCol=iBase.iPenColor;
         iBase.iPenColor=col;
         hdc=(HDC)iBase.iDeviceCtx;
         hPen=CreatePen(PS_SOLID,1,col);
         old=SelectObject(hdc,hPen);
      }
   }
   ~CTSetPenCol()
   {
      if(old)
      {
         SelectObject(hdc,old);
         DeleteObject(hPen);
      }
   }

};


#endif //gui
#else //if _T_CONSOLE_

typedef struct{
   char cTextCol;
   char cBackgCol;
   char c;
}CONS_POINT;

typedef struct 
{
//#define 
   int iPenColor;
   int w;
   int h;
   CONS_POINT consPoint[150 * 100];//max Width

}CONS_DEVICE_CTX;


static int setTextColor(CTDeviceCtx & iBase, int iCol){return iCol;}


class CTSetPenCol{
public:
   CTSetPenCol(CTDeviceCtx & iBase, int f)
   {
   }
};
#define CTSetTextCol CTSetPenCol
#ifndef CHAR_CNT_IN_BUF
#define CHAR_CNT_IN_BUF 0x1

inline int getCharWos(unsigned int c,int)
{
   return 1;
}
#endif //CHAR_CNT_IN_BUF

class CTSetFont{
public:
   CTSetFont(CTDeviceCtx & iBase, CTFontN *f){}
};

inline void drawChar(CTDeviceCtx & iBase,int c,int x, int y)
{
   CONS_DEVICE_CTX *dc=(CONS_DEVICE_CTX *)iBase.iDeviceCtx;
   CONS_POINT *p=&dc->consPoint[y*dc->w+x];
   p->c=(char)c;
   p->cTextCol=(char)0xff;
   p->cBackgCol=(char)0;
}
inline void drawPoint(CTDeviceCtx & iBase,int c,int x, int y)
{
   drawChar(iBase,c,x,y);
}
inline void drawLine(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1)
{
//   int i,j;
  // for(i=x,j=y;i<x1,j<y1;j++,i++)//TODO
    //  drawChar(iBase,'.',i,j);
}

inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect)
{
   CONS_DEVICE_CTX *dc=(CONS_DEVICE_CTX *)iBase.iDeviceCtx;
   short *p=((CTStrBase &)b).getText();
   int iLen=((CTStrBase &)b).getLen();

   if(!p || !iLen)return;
   int i;
   //TODO align
   for(i=0;i<iLen;i++)
   {
      drawChar(iBase,p[i],rect.x+i,rect.y);

   }
}
inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect,const CTAlign &a )
{
   drawText(iBase,b,rect);
}
inline void drawText(CTDeviceCtx & iBase, const CTStrBase &b, const CTRect &rect, int iXOff, int iYOff)
{
   drawText(iBase,b,rect);
}

class CTGui{
public:
   static inline  void fillRect(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1, int iCol)
   {
      int i,j;
      for(i=x;i<x1;i++)
         for(j=y;j<y1;j++)
            drawPoint(iBase,iCol,i,j);
   }
};

class CTBitmap{
public:

   inline void bitBlt(CTDeviceCtx & iBase, int x,int y, int cx, int cy, int iPosX, int iPosY)
   {
      //BitBlt(const TPoint &aDestination, const CFbsBitmap *aBitmap, const TRect &aSource);
      
   }
   int getH(){return iSizeY;}
   int getW(){return iSizeX;}
   int canDraw(){return iCanDraw;}

private:
   //HBITMAP hBmp;
  // HDC hdc;
 //  void *old;
   int iSizeX;
   int iSizeY;
   int iCanDraw;
public:
   int iImgCnt;

};

inline void drawToScreen(CTDeviceCtx & iBase)
{
   CONS_DEVICE_CTX *dc=(CONS_DEVICE_CTX *)iBase.iDeviceCtx;
   int i,iLen=dc->w*dc->h;
   for(i=0;i<iLen;i++)
   {
      //TODO cls
      if(dc->consPoint[i].c)
         putchar(dc->consPoint[i].c);
      else
         putchar('x');
   }
}

#endif 
//if _T_CONSOLE_
 //__SYMBIAN32__  add to os 



inline void drawRect(CTDeviceCtx & iBase, int x, int y,  int x1,  int y1)
{
   //Rectangle((HDC)iBase,x,y,x1,y1);
   drawLine(iBase,x,y,x1,y);
   drawLine(iBase,x,y,x,y1);
   drawLine(iBase,x1,y,x1,y1);
   drawLine(iBase,x,y1,x1+1,y1);

}



#endif
