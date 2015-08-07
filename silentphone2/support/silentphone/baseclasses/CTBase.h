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

#ifndef _C_T_BASE_H
#define _C_T_BASE_H

#ifndef min
#define min(A,B) (A)>(B)?(B):(A)
#endif

#ifndef max
#define max(A,B) (A)>(B)?(A):(B)
#endif


#ifndef UNICODE
#define UNICODE
#endif

#ifndef NULL
#ifdef  __cplusplus
#define NULL    0
#else
#define NULL    ((void *)0)
#endif
#endif


class CTStrBase{
public:
   virtual short *getText() =0;
   virtual int getLen() =0;
   int isASCII()
   {
      short *s=getText();
      int i;
      int iLen=getLen();
      for(i=0;i<iLen;i++,s++)
         if(*s>127 || *s<3)return 0;
      return 1;
      
   }
   int operator ==(CTStrBase *b);
   int operator ==(const char *p);
   inline int operator !=(const char *p){return !(*this==p);}
   inline int operator !=(CTStrBase *b){return !(*this==b);}
};

class CTOSWinBase{//TODO varbuut likt katraa baazee ???
public:
   virtual void redraw()=0;
   virtual void close()=0;
   virtual void beep()=0;
   void *hWin;
   void *hEdit;

};
/*
class CTSliderBase{
public:
   virtual int getPos()=0;
   virtual void setPos()=0;
   virtual void setMinMax(int iMin, int iMax)=0;
};
*/
class CTAlign{
public:
   enum ETAlign{
      top = 0x01,
      bottom= 0x02,
      vcenter= 0x04,
      left= 0x08,
      right= 0x10,
      hcenter= 0x20,
      hproc=0x40,
      vproc=0x80,

      center= hcenter|vcenter,
      normal= top|left,
      br=bottom|right,
      issize=0xffff

   };
   CTAlign(int iFlag=normal):iFlag(iFlag){}
   int iFlag;
};
class CTAlignPos: public CTAlign{
public:
   CTAlignPos(int x=0, int y=0,int alFlag=normal)
      :CTAlign(alFlag),x(x),y(y){}
   CTAlignPos(const CTAlignPos &ap)
      :CTAlign(ap.iFlag),x(ap.x),y(ap.y){}

   int x,y;
   inline CTAlignPos &set(int ix=0, int iy=0,int alFlag=normal){x=ix;y=iy;iFlag=alFlag;return *this;}
};

class CTRect{
public:
   CTRect(){x=y=x1=y1=w=h=0;}
   CTRect(int x, int y, int x1, int y1):x(x),x1(x1),y(y),y1(y1){setWH();}
   CTRect(CTAlignPos &ap1, CTAlignPos &ap2, int nw, int nh){set(ap1,ap2,nw,nh);}
   inline void set(int xs, int ys, int x1s, int y1s)
   {
      x=xs;x1=x1s;y=ys;y1=y1s;setWH();
   }
   void set(CTAlignPos &ap1, CTAlignPos &ap2, int nw, int nh)
   {
      if(ap1.iFlag & CTAlign::hproc && nw){
         x=ap1.x*nw/100;
      }
      else if(ap1.iFlag & CTAlign::right && nw)
         x=nw-ap1.x;
      else x=ap1.x;

      if(ap1.iFlag & CTAlign::vproc && nh){
         y=ap1.y*nh/100;
      }
      else if(ap1.iFlag & CTAlign::bottom && nh)
         y=nh-ap1.y;
      else y=ap1.y;

      if(ap2.iFlag==CTAlign::issize)
      {
         if(ap1.iFlag & CTAlign::right)
            x-=ap2.x;
         if(ap1.iFlag & CTAlign::bottom)
            y-=ap2.y;

         w=ap2.x;
         h=ap2.y;
      }
      else
      {
         if(ap2.iFlag & CTAlign::hproc && nw){
            int a=ap2.x*nw/100;
            w=a-x;
         }
         else if(ap2.iFlag & CTAlign::right && nw)
            w=nw-ap2.x-x;
         else  w=ap2.x-x;

         if(ap2.iFlag & CTAlign::vproc && nh){
            int a=ap2.y*nh/100;
            h=a-y;
         }
         else if(ap2.iFlag & CTAlign::bottom && nh)
            h=nh-ap2.y-y;
         else  h=ap2.y-y;
      }
      if(!nw && !nh){
         if(ap1.iFlag & CTAlign::hproc){
            x=ap1.x*w/100;
         }
         if(ap1.iFlag & CTAlign::vproc){
            y=ap1.y*h/100;
         }
      }

      x1=x+w;
      y1=y+h;

   }
   int x,x1,y,y1;
   int w,h;
private:
   inline void setWH()
   {
      w=x1-x; h=y1-y;
   }

};
class CTAudioCallBack{
public:
   virtual void audioIn(char *p, int iLen, unsigned int uiPos)=0;
};

class CTAudioGetDataCB{
public:
   enum{eUnknown, eShort, eFloat32, eLast};
   virtual int getAudioData(void *p, int iSamples, int iDevRate, int iSampleTypeEnum, int iOneSampleSize, int iIsFirstPack)=0;
   virtual int canUseAudioData(){return 1;}//onlyInConference 
};
//TODO audio base, audio voip base

class CCodecBase;

class CTAudioOutBase{
public:
   enum{ePCM16, eAMR, eG729};
   virtual int getType(){return ePCM16;}
   virtual int getRate()=0;
   virtual int getBufSize()=0;
   virtual int update(char *p, int iLen, unsigned int uiPos)=0;
   virtual int isPlaying()=0;
   virtual int play()=0;
   virtual void stop()=0;
   virtual unsigned int getSamplePlayPos()=0;
   
   //voip
   virtual void onlyInConference(int ok){}//if call->is_in_conf && is call->onhold then ok=1
   virtual int receivedTS(unsigned int uiPos, int iPrevLost, int iIsCN){return -1;}
   virtual int msg(const char *pid, int iLen, void *p, int iSizeofP){return -1;}
   virtual int getLastPlayBuf(short *p, int iSamples, int &iLeftInBuf)=0;
   virtual int isPrevPackSpeech(){return 2;}//return if(ret>0) true, if(ret<=0) false
   
   virtual int addPack(unsigned int ts, unsigned short seq, unsigned char *data, int iDataLen, CCodecBase *c){return -1;}
   
   virtual int addCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab)=0;
   virtual int remCB(CTAudioGetDataCB *cb, CTAudioOutBase *ab)=0;
protected:
   CTAudioGetDataCB *cbAudioDataGet;
   CTAudioOutBase *cAudioOut;
};

class CTAudioInBase{
public:
   enum{ePCM16, eAMR, eG729};
   virtual int getRate()=0;
   virtual int getType()=0;
   virtual int record(void *pCB)=0; //TODO CTAudioCallBack
   virtual void stop(void *pCB)=0;
   virtual int packetTime(int ms){return -1;}
};

class CTVideoCallBack{
public:
     virtual void videoIn(unsigned char *p, int iLen, unsigned int uiPos)=0;
};


class CTVideoInBase{
public:
   virtual int start(void *pData)=0;
   virtual void stop(void *pData)=0;
   
   virtual int init(void *hParent)=0;
   virtual int getXY(int &x , int &y)=0;
   virtual void setXY(int x, int y)=0;
};

class CTBmpBase{
public:   
 // virtual void *getBuf()=0;
  virtual void setScanLine(int iLine, int iXOff, unsigned char *p, int iLen, int iBits)=0;
  virtual void setScanLine(int iLine, unsigned char *p, int iLen, int iBits)=0;
  virtual void setOutputPictureSize(int cx, int cy)=0;
  virtual int setOutputPictureSizeYuv(int cx, int cy){
     setOutputPictureSize(cx,cy);
     return -1;
  }
  virtual int getImgParams(int &iYuv, int &iBpp, int &stride, int &iIsBRG, void **p){return -1;}
  virtual void startDraw()=0;
  virtual void endDraw()=0;
  virtual int rawJpgData(unsigned char *p, int iLen){return -1;}//ret 0 if use it
  virtual void setTimeStamp(unsigned int uiPos){}
   
};

class CTVideoOutBase: public CTBmpBase{
public:
   virtual int start()=0;
   virtual void stop()=0;
   virtual int onData(unsigned char *p, int iLen, unsigned int uiPos)=0;
   virtual int drawFrame(){return -1;}
   //virtual void setScanLine(int iLine, unsigned char *p, int iLen, int iBits)=0;
};

void *t_getSetSometing(void *pBaseCmp, char *pid, int iIDLen, char *pItemName, int iItemLen, void *pDataToSet1,  void *pDataToSet2);
//int t_setSometing(void *pBaseCmp, char *pItemName, int iItemLen, char *pid, int iIDLen, void *pDataToSet1,  void *pDataToSet2);

#define T_getSometing1(_CTX, _P1) t_getSetSometing(_CTX,(char*)_P1,sizeof(_P1)-1,NULL,0,NULL,NULL)
#define T_getSometing2(_CTX, _P1, _P2) t_getSetSometing(_CTX,(char*)_P1,sizeof(_P1)-1,_P2,sizeof(_P2)-1,NULL,NULL)

#define T_setSometing(_CTX, _P1, _V1,_V2) t_getSetSometing(_CTX,(char*)_P1,sizeof(_P1)-1, NULL,0,_V1,_V2)
//#define T_setSometingG(_CTX, _P1, _Gui, _V1,_V2) t_setSometing(_CTX,_Gui,sizeof(_Gui)-1,_P1,sizeof(_P1)-1,_V1,_V2)

#endif //_C_T_BASE_H
