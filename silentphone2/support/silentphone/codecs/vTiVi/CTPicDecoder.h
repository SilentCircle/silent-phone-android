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

#ifndef _C_TPIC_DECODER
#define _C_TPIC_DECODER
   static void copy_pred8( unsigned char *cur,int stride, int *res)
   {
      int i,j;
      int avgR=0;
      int avgG=0;
      int avgB=0;
      int v;
      int tu[24];
      int tl[24];
      for(i=0;i<8;i++){
         tu[i*3]  =cur[-stride+i*3];
         tu[i*3+1]=cur[-stride+i*3+1];
         tu[i*3+2]=cur[-stride+i*3+2];

         tl[i*3]  =cur[stride*i-3];
         tl[i*3+1]=cur[stride*i-3+1];
         tl[i*3+2]=cur[stride*i-3+2];

         avgR+=(tl[i*3]+tu[i*3]);
         avgG+=(tl[i*3+1]+tu[i*3+1]);
         avgB+=(tl[i*3+2]+tu[i*3+2]);
      }
      avgR>>=4;
      avgG>>=4;
      avgB>>=4;
      //avgR=255;
      //avgG=0;
      //avgB=0;

      for(j=0;j<8;j++){
         for(i=0;i<8;i++)
         {
            if(*res==6)
            {
               cur[i*3]=avgR;
               cur[i*3+1]=avgG;
               cur[i*3+2]=avgB;
            }
            else if(*res==4)
            {
               cur[i*3]=tu[i*3];
               cur[i*3+1]=tu[i*3+1];
               cur[i*3+2]=tu[i*3+2];
            }
            else 
            {
               cur[i*3]=tl[j*3];
               cur[i*3+1]=tl[j*3+1];
               cur[i*3+2]=tl[j*3+2];
            }
         }
         cur+=stride;
      }
         /*
      int iSl=sad8P(&bufl[0],cur,stride);
      int iSu=sad8P(&bufu[0],cur,stride);
      int iSm=sad8M(avg,cur,stride);
      if(iSm<iSu && iSm<iSl)*res=4;
      else if(iSl<iSu && iSl<iSm)*res=5;
      else *res=6;
*/
   }




class CTPixDecoder{
public:
   int ceprev[256+4];
   int ce[512+8];
   int cd[256+8];
/*
   int ceprevG[256+4];
   int ceG[512+8];
   int cdG[256+8];
   */
   int iCoder;
   //default =0 baigi labi
   ////
   //2 default
   CTPixDecoder(int iCoder=1):iCoder(iCoder){
      init();
   }
   void init()
   {
      int i;
      int ds=15;
      d1=ds+10000;

      //for(i=-255;i<256;i++)
//      for(i=0;i<257;i++)
      for(i=256;i>=0;i--)
      {

         //ja zip tad if(i<25)

         if(0)//i<4)//i<4)//4) //10 ir labi//sho var lietot ja ir vec
         {
         ce[-i]=initEnc(0);
         ce[i]=initEnc(0);
         }
         else
         {
         ce[-i]=i/2+128;//initEnc(-i);
         ce[i]=i/2+128;//initEnc(i);
         }
         
         //printf("e-(%d=%d),", i,ce[i+255]);
         if(i>=0)cd[i]=initDec(i);
         //if(i>=0)cdG[i]=initDecG(i);
         //cd[i+127]=ce[255-i];


            d1-=ds;
        // else sw=1;

      }


   }
   int d1;
   int d2;
   int initEncG(int c){return initEnc(c,1);}
   unsigned char initEnc(int  c, int g=0)
   {
      //c/=4;
      //iCoder=1;
      int cod=iCoder=4;
      switch(cod)
      {
         case 1:break;
         case 2:c*=6;if(c>0)c+=4;else if(c<0)c-=4;c/=8;break;
         case 3:c*=3;if(c>0)c+=2;else if(c<0)c-=2;c/=5;break;
         case 4:if(c>0)c++;else if(c<0)c--;c/=2;break;
         case 5:c*=3;c/=2;break;
         case 6:c*=2;c/=5;break;
         case 7:c/=4;break;
         //case 9:c=encX(c);break;

         default:
            c*=3;c/=4;

      }


      if(c<=-128)return 0;if(c>=127)return 255;
      return c+128;
   }

   int initDecG(int c){return initDec(c,1);}

   int initDec(int c, int g=0)
   {
      //iCoder=1;
      int cod=iCoder=4;
      c-=128;
      switch(cod)
      {
         case 1:break;
         case 2:c*=8;if(c>0)c+=2;else if(c<0)c-=2;c/=6;break;
         case 3:c*=5;if(c>0)c++;else if(c<0)c--;c/=3;break;
         case 4:c*=2;break;
         case 5:c*=2;c/=3;break;
         case 6:c*=5;if(c>0)c++;else if(c<0)c--;c/=2;break;
         case 7:c*=4;break;
        // case 9:c=decX(c);break;

         default:
            c*=4;c/=3;

      }
      //c=(int)(sinh((double)(c+1)/253.f)*130.0f);
      //c*=49;
      //c+=25;
      //c/=50;
      //c/=2;

      if(c<=-255)return -255;if(c>=255)return 255;
      return c;
   }
   inline unsigned char enc(register unsigned char cur, unsigned register char old)
   {
      //if(cur>=old)return (((int)cur-(int)old)>>1)+128;
       return (((int)cur-(int)old)>>1)+128;
     // return ce[(int)cur-(int)old];
      //register int r=(((int)cur-(int)old))+128;if(r>255)return 255;if(r<0)return 0;return r;
      
     // register int c=(int)cur;
     // c-=(int)old;
      //return ce[c];
      
   }

#define decG dec
#define encG enc
   inline unsigned char dec(register unsigned char cur, register unsigned char old)
   {

      //int r=(int)old-(127-(int)cur)*2;
      //if(r>255)return 255;
      //if(r<0)return 0;
      //return r;
      //register int c=(int)old;
      //c=c+(int)cd[cur];
      /*
      register int c=(int)old+cd[cur];
      if(c>255)return 255;
      if(c<0)return 0;
      return c;
      */
      int r=(int)old+(((int)cur)-128)*2;
      if(r>255)return 255;
      if(r<0)return 0;
      return r;
   }
   /*
   inline unsigned char encG(register unsigned char cur, unsigned register char old)
   {
      register int c=(int)cur;
      c-=(int)old;
      return ceG[c];
   }
   inline unsigned char decG(unsigned char cur, unsigned char old)
   {

      register int c=(int)old;
      c=c+(int)cdG[cur];
      if(c>255)return 255;
      if(c<0)return 0;
      return c;
   }
   */
   inline unsigned char encj(unsigned char cur, unsigned char old)
   {
      int c=(int)cur-(int)old;

      if(c>10){c=10+c/2;}else if(c<-10){c=-10+c/2;}
      
      
      if(c<-127)c=0;else if(c>128)c=255;
      return c+127;
   }
   inline unsigned char decj(unsigned char cur, unsigned char old)
   {

      int cc=(int)cur;
      cc-=127;
      if(cc>10){cc=cc*2+10;}else if(cc<-10){cc=cc*2-10;}

      int c=(int)old;
      c=c+(int)cc;
      if(c>255)return 255;
      if(c<0)return 0;
      return c;
   }
   static void copyX(int iSize, unsigned char *pImg, int w, int h, unsigned char *pParts, int cx, int cy)
   {
      int i,j;
      int iSizeX3=iSize*3;
      int stridew=w*3;//-iSizeX3;
      int stridecx=cx*3;//-iSizeX3;
      for(j=0;j<iSize;j++)
      {
         /*
         for(i=0;i<iSizeX3;i+=3)
         {
            *pImg=*pParts;pImg++;pParts++;
            *pImg=*pParts;pImg++;pParts++;
            *pImg=*pParts;pImg++;pParts++;
         }
         */
         memcpy(pImg,pParts,iSizeX3);
         pImg+=stridew;
         pParts+=stridecx;
      }
      
   }
  // template<int iSize>
   static void copyXDif(int iSize, CTPixDecoder &dif,unsigned char *pImg, int w, int h, unsigned char *pParts, int cx, int cy)
   {
      int i,j;
      int iSizeX3=iSize*3;
      int stridew=w*3-iSizeX3;
      //int stridecx=cx*3-iSizeX3;
      int c;
      for(j=0;j<iSize;j++)
      {
         for(i=0;i<iSize;i++)
         {
            *pImg=dif.dec(*pParts,*pImg);pImg++;pParts++;
            *pImg=dif.decG(*pParts,*pImg);pImg++;pParts++;
            *pImg=dif.dec(*pParts,*pImg);pImg++;pParts++;
         }
         pImg+=stridew;
         //pParts+=stridecx;
      }
   }
   static void copyXDifBw(int iSize, CTPixDecoder &dif,unsigned char *pImg, int w, int h, unsigned char *pParts, int cx, int cy)
   {
      int i,j;
      int iSizeX3=iSize*3;
      int stridew=w*3-iSizeX3;
      //int stridecx=cx*3-iSizeX3;
      int c;
      for(j=0;j<iSize;j++)
      {
         for(i=0;i<iSize;i++)
         {
            /*
            *pImg=dif.dec(*pParts,*pImg);pImg++;pParts++;
            *pImg=dif.decG(*pParts,*pImg);pImg++;pParts++;
            *pImg=dif.dec(*pParts,*pImg);pImg++;pParts++;
            */
            //*pImg=dif.dec(*pParts,*pImg);pImg++;
            //*pImg=dif.decG(*pParts,*pImg);pImg++;
            //*pImg=dif.dec(*pParts,*pImg);pImg++;pParts+=3;
            *pImg=dif.dec(*pParts,*pImg);pImg+=3;pParts+=3;
         }
         pImg+=stridew;
         //pParts+=stridecx;
      }
   }
   static void decodeParts(unsigned char *pImg, unsigned char *pParts, int *pImgPartsBuf, int cx, int cy, int w, int h,CTPixDecoder &dif, int iIsDif)
   {
      //register unsigned char *pDst=v->cIsDiff?img.getBuf():imgPrev.getBuf();
      //unsigned char *pToInfo;
      //CTPixDecoder dif;
      int iSizeOfPart=cx;
      int i,j;
      int iRow=0;
      int iXPos=0;
      int iWPos=0;
      int iImgSizeParts=w*h/(cy*cy);
      int iSizeOfPartx3=iSizeOfPart*3;
      int stridew=w*3;
      int iJump=iSizeOfPartx3*iSizeOfPart;

#if 0
      //if(cy==8)
      {
           CTHdc d2(cx,cy,24);memcpy(d2.pWinData,pParts,d2.getMemSize());

            HDC hdc=GetDC(0);
            static int yy;
            static int col=1;
            int hh=cx;
            BitBlt(hdc,yy,0,cx,cy,d2.hdc,0,0,SRCCOPY);
            //PatBlt(hdc,v->cx,yy,16,16,WHITENESS);
            CTGui::fillRect(CTDeviceCtx((int)hdc),yy,cy,yy+hh,cy+hh,col+=(3*yy));
            if(col>0xffffff)col=2;
            yy+=hh;
            if(yy>320)yy=0;
            ReleaseDC(0,hdc);
      }
#endif


      int id=0;
      int wd8=w/iSizeOfPart;
      int hd8=h/iSizeOfPart;
      int iCur;
       for(j=0;j<hd8;j++)
       {
          for(i=0;i<wd8;i++)
          {
             iCur=pImgPartsBuf[id];
             if(iCur)
             {
                unsigned char *pImqO=pImg+iSizeOfPartx3*(i+j*w);
                if(iCur>3)
                {
                   copy_pred8(pImqO,w*3,&iCur);
                   copyXDif(iSizeOfPart,dif,pImqO,w,h,pParts,cx,cy);
                }
                else if(iCur==3 && iIsDif!=2)
                   copyXDifBw(iSizeOfPart,dif,pImqO,w,h,pParts,cx,cy);
                else if(iCur==1)
                   copyXDif(iSizeOfPart,dif,pImqO,w,h,pParts,cx,cy);
                else if(iIsDif!=2)
                   copyX(iSizeOfPart,pImqO,w,h,pParts,cx,cy);


                pParts+=iJump;
                iWPos++;
             }
             id++;
          }
       }

   }


};
#endif
