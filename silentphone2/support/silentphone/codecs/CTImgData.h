#ifndef _C_T_IMG_DATA
#define _C_T_IMG_DATA

#ifndef _C_T_BASE_H
#include "../baseclasses/CTBase.h"
#endif

class CTImgData: public CTBmpBase{
  unsigned char *buf;
  unsigned char *bufPic;
  int x,y;
  int iMustDelete;
  int iInitSize;
public:  
   unsigned char *getBuf(){return buf;}
   CTImgData(){iInitSize=x=y=0;iMustDelete=0;bufPic=buf=NULL;}
   virtual ~CTImgData(){if(iMustDelete)delete bufPic;buf=bufPic=NULL;x=0;y=0;}
   void copy(CTImgData *img){
      int stride=x*3;
      int size=x*y*3;
      memcpy(buf,img->buf,size);
      
      memcpy(buf-stride,img->buf,stride);
      memcpy(buf+size,img->buf+size-stride,stride);
   }
   unsigned char * setNewBuf(CTImgData *img)
   {
      unsigned char *pOld=buf;
      img->getXY(x,y);
      buf=img->getBuf();
      if(buf)
         bufPic=img->bufPic;//buf-(2*x+2*y)*3;
      else 
         bufPic=NULL;

      return pOld;
   }
   static void swap(CTImgData *p1,CTImgData *p2)
   {
      CTImgData tmp;
      tmp.setNewBuf(p1);
      p1->setNewBuf(p2);
      p2->setNewBuf(&tmp);
   }
   void getXY(int &w ,int &h){w=x;h=y;}
   inline void setScanLine(int iLine, unsigned char *p, int iLen, int iBits)
   {
      return setScanLine(iLine,0,p,iLen,iBits);
   }
   inline void setScanLine(int iLine, int iXOff, unsigned char *p, int iLen, int iBits)
   {
      if(iLine>=y || iLine<0 || !buf)return;
      int bpp=24;
      unsigned char *c=buf+(x*bpp/8*(iLine)+iXOff*bpp/8);
      memcpy(c,p,iLen);
   }
   virtual void setOutputPictureSize(int cx, int cy)
   {
      if(cx!=x  || cy!=y)
      {
         if(bufPic)delete bufPic;
         buf=bufPic=NULL;
         iMustDelete=0;
         x=cx;
         y=cy;
         if(cx && cy)
         {
           iMustDelete=1;
           iInitSize=(x+4)*(y+30)*3+4095;
           //iInitSize=(iInitSize+4095)&(~4095);
           bufPic=new unsigned char[iInitSize];
           buf=bufPic+(x*15)*3+32;//(x+3)*(y+2)*3+32;
           buf=(unsigned char*)(((size_t) buf+15)&(~15));
         }
      }
   }
   virtual void startDraw(){}
   virtual void endDraw(){}
   virtual int rawJpgData(unsigned char *p, int iLen){return -1;}//ret 0 if use it
};
#endif
