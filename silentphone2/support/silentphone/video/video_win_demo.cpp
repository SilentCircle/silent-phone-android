void startThX(int (cbFnc)(void *p),void *data);

#include <windows.h>
class CTMsDC{
   HWND h;
public:
   HDC hdc;
   CTMsDC(HWND h):h(h)
   {
      hdc=GetDC(h);
   }
   ~CTMsDC(){
      ReleaseDC(h,hdc);
   }

};
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


CTMsDC h(NULL);

CTHdc out(10,10,24,0); 

int init(struct _S_TVIDEO_OUT *ctx, void *vo){return 0;}
int release(struct _S_TVIDEO_OUT *ctx, void *vo){return 0;}

//shoud show video screen
int start(struct _S_TVIDEO_OUT *ctx, void *vo){return 0;}

//should hide video screen
void stop(struct _S_TVIDEO_OUT *ctx, void *vo){return ;}

//should allocate frame memmory x-width y-height
void setOutputPictureSize(struct _S_TVIDEO_OUT *ctx, void *vo, int x, int y){
   out.reset(x,y,24);
}

//lock should not change,update,delete video buffer
void lockFrame(struct _S_TVIDEO_OUT *ctx, void *vo){}

//unlock, could change,update,delete video buffer but shoud wait for drawFrame
void unLockFrame(struct _S_TVIDEO_OUT *ctx, void *vo){}

//should draw on screen when see this fnc
int drawFrameNow(struct _S_TVIDEO_OUT *ctx, void *vo){
   BitBlt(h.hdc,100,100,out.getW(),out.getH(),out.hdc,0,0,SRCCOPY);
   return 0;
}

//if(getImgParams can not return direct buffer)will call this fnc  after lockFrame
//RGB24 only
void setScanLine(struct _S_TVIDEO_OUT *ctx, void *vo, int iLine, int iXOff, unsigned char *p, int iLen, int iBits){
   if(iLine<0 || iLine>=out.getH())return;
   char *po= (char*)out.pWinData;
   po+=(out.getH()-iLine-1)*out.getW()*3;
   memcpy(po,p,iLen);
}

//will call this fnc after lockFrame
int getImgParams(struct _S_TVIDEO_OUT *ctx, void *vo, int &iYuv, int &iBpp, int &stride, int &iIsBRG, void **p){return -1;}


void setupV(){
   static S_TVIDEO_OUT v;

   v.init=init;
   v.release=release;
   v.drawFrameNow=drawFrameNow;
   v.getImgParams = getImgParams;
   v.start=start;
   v.stop=stop;
   v.setOutputPictureSize=setOutputPictureSize;
   v.setScanLine=setScanLine;
   v.unLockFrame=unLockFrame;
   v.lockFrame=lockFrame;
   v.setOutputPictureSizeYuv=NULL;

   setVO_CB(&v);

}

static int iTestCameraRunning=0;

int thVideoCamera(void *){
   CTMsDC src(NULL);
   iTestCameraRunning=1;
   CTHdc outData(320,160,32,0); 
   while(iTestCameraRunning){
      BitBlt(outData.hdc,0,0,outData.getW(),outData.getH(),src.hdc,352+100,100,SRCCOPY);

      void tivi_sleep_ms(unsigned int ms );
      tivi_sleep_ms(60);
      onNewVideoData((int*)outData.pWinData,NULL,outData.getW(),outData.getH(),180);
   }
   return 0;
}

void test_startCamera(){
   iTestCameraRunning=1;
   
   startThX(&thVideoCamera,NULL);
}
void test_stopCamera(){
   iTestCameraRunning=0;
}
