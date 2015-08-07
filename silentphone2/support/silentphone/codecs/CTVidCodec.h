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

#ifndef _C_T_VID_CODEC
#define _C_T_VID_CODEC
#include <string.h>
#ifndef min
#define min(A,B) (A)>(B)?B:A
#endif
inline int absm(int a)
{
   return (a>=0)?a:-a;
}
//#define abs(A) (A>0)?A:-A
class CVCodecBase{
public:
   virtual int encode(unsigned char *pI, unsigned char *pO, int iLen)=0;
   //virtual int encodeNext(){return 0;};
   virtual void reset(){}
   virtual void setXY(int x, int y)=0;
   virtual int decode(unsigned char *pI, unsigned char *pO, int iLen)=0;
   CTBmpBase *cVO;
   virtual void getDecXY(int &x, int &y)=0;
   virtual void setQuality(int q)=0;
   virtual const char *getName(){return getShortName();}
   virtual const char *getShortName()=0;
   virtual int getId()=0;
   virtual int canSkipThis(unsigned char *p, int iLen)=0;   
   virtual int isDrawableFrame(unsigned char *p, int iLen)=0;
   virtual int wasDrawableFrame(unsigned char *p, int iLen)=0;
   virtual int getSamplesPerSec(){return 1000;}
   virtual int videoAhead(){return 20;}
   virtual int bytesEncodedLeft(){return -1;}

};


class CTincDecSize{
public:
typedef struct{
   short id;
   short len;
   short x;
   short y;
   short w;
   short h;
   short a;
   short b;
   short cx;
   short cy;
}vhdr ;
   static int incSize(int w, int h, int dx, int dy, unsigned char *in, unsigned char *out)
   {
      CTincDecSize::vhdr v;
      v.x=0;
      v.y=0;
      v.a=w/dx;
      v.b=h/dy;
      v.w=w;
      v.h=h;
      v.cx=w;
      v.cy=h;
      CTincDecSize inc;
      return inc.incSize(&v,in,out);
   }

   int incSize(vhdr *v, unsigned char *in, unsigned char *out)
   {
      int dx=(v->w-v->x)/v->a;
      int dy=(v->h-v->y)/v->b;
      int i,j,a,b;
      unsigned char *out1;

      for(j=v->y;j<v->h;j+=dy)
      {
        // outp2=out1=&out[((j+0)*v->cx+0+0)*3];
         
         for(i=v->x;i<v->w;i+=dx)
         {
           // outp=out1;//=&out[((j+0)*v->cx+i+0)*3];
            for(b=0;b<dy;b++)
            {
               out1=&out[((j+b)*v->cx+i)*3];
               for(a=0;a<dx;a++)
               {
                  //out1=&out[((j+b)*v->cx+i+a)*3];
                  *out1=*(in+0); out1++;*out1=*(in+1); out1++;*out1=*(in+2);
                  out1++;
               }
            }
            in+=3;
            //out1=outp2+i*3;
         }
         
      }
      return 0;
   }
   static int decSize(int w, int h, int dx, int dy, unsigned char *in, unsigned char *out)
   {
      CTincDecSize::vhdr v;
      v.x=0;
      v.y=0;
      v.a=w/dx;
      v.b=h/dy;
      v.w=w;
      v.h=h;
      v.cx=w;
      v.cy=h;
      CTincDecSize inc;
      return inc.decSize(&v,in,out);
   }
   int decSize(vhdr *v, unsigned char  *in, unsigned char  *out)
   {
      int dx=(v->w-v->x)/v->a;
      int dy=(v->h-v->y)/v->b;
      int i,j,a,b;
      unsigned char *in1;
      int rc,gc,bc;
      int dd=dx*dy;
      int iShifter=0;
      if(dd==2)iShifter=1;
      else if(dd==4)iShifter=2;
      else if(dd==8)iShifter=3;
      else if(dd==16)iShifter=4;
      else if(dd==32)iShifter=5;
      else if(dd==64)iShifter=6;
      else if(dd==128)iShifter=7;
      else if(dd==256)iShifter=8;

      const int cx=v->cx;
      const int stride=cx*3;
      const int w=v->w;
      const int h=v->h;

      if(iShifter){
       //  iShifter>>=1;
         for(j=v->y;j<h;j+=dy)for(i=v->x;i<w;i+=dx)
         {
            const int at=(i+dx)*3;
            const int bt=j+dy;
            rc=gc=bc=0;
            for(a=i*3;a<at;a+=3)for(b=j;b<bt;b++)
            {
               in1=&in[b*stride+a];
               rc+=*in1;in1++;gc+=*in1; in1++; bc+=*in1;
            }
            *out=rc>>iShifter;out++; *out=gc>>iShifter; out++; *out=bc>>iShifter; out++;
            //out->r=rc/dd;
            //out->g=gc/dd;
            //out->b=bc/dd;
         }
      }
      else{
         unsigned char *pout=out;
         int shd=(1<<14)/dd+1;
         //unsigned char *pin=in;
         for(j=v->y;j<h;j+=dy){
            out=pout+(j*int(w/dd))*3;
            for(i=v->x;i<w;i+=dx)
            {
               const int at=(i+dx)*3;
               const int bt=j+dy;
               rc=gc=bc=0;
               for(a=i*3;a<at;a+=3)for(b=j;b<bt;b++)
               {
                  in1=&in[b*stride+a];
                  rc+=*in1;in1++;gc+=*in1; in1++; bc+=*in1;
               }
//               *out=rc/dd;out++; *out=gc/dd; out++; *out=bc/dd; out++;
               *out=(rc*shd)>>14;out++; *out=(gc*shd)>>14; out++; *out=(bc*shd)>>14; out++;
               //out->r=rc/dd;
               //out->g=gc/dd;
               //out->b=bc/dd;
            }
         }
      }

      
      return 0;//
   }
};
class CTConvImgBits{
   unsigned char pal16[16];//={0,16,32,48,64,96,112,128,144,160,176,192,208,224,240,255};
   unsigned char pal256[256];
public:
   CTConvImgBits()
   {
      int i;
      int a=0;
      int b=16+4;
      for(i=0;i<16;i++)
      {
         pal16[i]=a;
         a+=b;
         if(a>255)a=255;
         if(a<128) b-=1; else b+=1;

      }
      pal16[15]=255;
      for(i=0;i<256;i++)
      {
         pal256[i]=pal16[i/16]/16;
      }
   }
//   int mono(t_rgb *in, unsigned char *out);
   inline void Bits12to24(unsigned char *pI3, unsigned char *pOut6)
   {
      //unsigned char pal16[16]={0,16,32,48,64,96,112,128,144,160,176,192,208,224,240,255};
      unsigned int r,g,b,r2;
      r=pI3[0];
      g=pI3[0];
      b=pI3[1];
      r2=b;
      r>>=4;//r>>=8;
      g&=0x0f;
      b>>=4;//4;b>>=8;

      pOut6[0]=pal16[r]; pOut6[1]=pal16[g]; pOut6[2]=pal16[b];

      g=pI3[2];
      b=pI3[2];

      r=r2&0x0f;

      g>>=4;//g>>=8;
      b&=0x0f;

      pOut6[3]=pal16[r]; pOut6[4]=pal16[g];pOut6[5]=pal16[b];

   }
   inline void Bits24to12(unsigned char *pIn6, unsigned char *pO)
   {

      pO[0]=pal256[pIn6[0]]<<4;
      pO[0]|=pal256[pIn6[1]];

      pO[1]=pal256[pIn6[2]]<<4;
      pO[1]|=pal256[pIn6[3]];

      pO[2]=pal256[pIn6[4]]<<4;
      pO[2]|=pal256[pIn6[5]];

   }

#ifndef RGB888to565
#define RGB888to565(R,G,B) (((R&0xf8)<<8)|((G&0xfC)<<3)|(B>>3))
#endif
//TODO CTincDecSize class 
   void bits888to565(CTincDecSize::vhdr *v, unsigned char  *in, unsigned short  *out)
   {
      int i, j,r,g,b;
      unsigned short c;
      unsigned char  *intmp;
      for(j=0;j<v->b;j++)
      {
         //of=
         intmp=in+(j+v->y)*v->cx*3;
         for(i=0;i<v->a;i++)
         {
            r=*intmp;intmp++;
            g=*intmp;intmp++;
            b=*intmp;intmp++;
            c=RGB888to565(r,g,b);
            out[v->cx*(j+v->y)+i+v->x]=c;
            //of+=3;
         }
      }
   }
   int tmp_bit_i1;
   int tmp_bit_to1;
   void bits24to12(CTincDecSize::vhdr *v, unsigned char  *in, unsigned char  *out)
   {
      
      tmp_bit_to1=v->a*v->b;
      for(tmp_bit_i1=0;tmp_bit_i1<tmp_bit_to1;tmp_bit_i1+=2)
      {
         Bits24to12(in,out);
         in+=6;
         out+=3;
      }
   }
   int tmp_bit_i2,tmp_bit_to2;
   void bits12to24(CTincDecSize::vhdr *v, unsigned char *in, unsigned char *out)
   {
      tmp_bit_to2=v->a*v->b;
      for(tmp_bit_i2=0;tmp_bit_i2<tmp_bit_to2;tmp_bit_i2+=2)
      {
         Bits12to24(in,out);
         out+=6;
         in+=3;
      }
   }

};
class CTisVSilence{
protected:
   enum{eMaskSx=8,eMaskSy=8};//TODO use template
   unsigned char o_difs[eMaskSx*eMaskSy*3];
   unsigned char c_difs[eMaskSx*eMaskSy*3];
   CTincDecSize::vhdr vTmp;
   CTincDecSize idimg;
public:
   CTisVSilence(){iDifConst=1500;}
   int iDifConst;
   int iPrevDif;
   void reset(){}
   inline int isVideoSilence(unsigned char *in, int w, int h)
   {
      if(iDifConst==0)return 0;
      
      vTmp.w=vTmp.cx=w;
      vTmp.h=vTmp.cy=h;
      vTmp.x=vTmp.y=0;
      vTmp.a=eMaskSx;
      vTmp.b=eMaskSy;
      idimg.decSize(&vTmp,in,(unsigned char *)&c_difs);
      iPrevDif=calcDifs(c_difs);
      int ret=iPrevDif<iDifConst;
      if(!ret)
         memcpy(o_difs,c_difs,sizeof(o_difs));
      return ret;

   }
//vidoe silence << 
   int calcDifs(unsigned char *pO)
   {
      int i;
      int x=0,a=eMaskSx*eMaskSy*3;
      int iDifs=0;
      int c1,c2;
      for(i=0;i<a;i++)
      {
         c1=(int)o_difs[i];
         c2=(int)pO[i];
         x=absm(c1-c2);
         x/=4;
         iDifs+=(x)*(x);
      }
      return iDifs;
   }
};
class CTVCodCol:public CTisVSilence, public CTConvImgBits, public CVCodecBase{

   CTincDecSize resizer;


public:
   typedef struct{
      unsigned char r;
      unsigned char g;
      unsigned char b;
   }t_rgb;
   int iSeq;
   int iLast;
   int iSendNext;
   int iI,iJ;
   virtual int canSkipThis(unsigned char *p, int iLen){return 1;}
   virtual int isDrawableFrame(unsigned char *p, int iLen){return 1;}
   virtual int wasDrawableFrame(unsigned char *p, int iLen){return 1;}
   CTVCodCol()
   {
      iI=iJ=0;
      iSendNext=iLast=iSeq=0;
   }
   void setXY(int x, int y)
   {

   }
   void getDecXY(int &x,int &y)
   {
      x=160;
      y=120;
   }

   virtual void setQuality(int q){};
   //virtual char *getName(){return getShortName();}
   virtual const  char *getShortName(){return "tivi-12bitcol";};
   virtual int getId(){return 111122;};

   virtual int encode( unsigned char *pI, unsigned char *pO, int iLen)
   {
 
      CTincDecSize::vhdr *v=(CTincDecSize::vhdr *)pO;
      pO+=sizeof(CTincDecSize::vhdr);

      v->id=1;
      v->cx=160;
      v->cy=120;
      int iX=0;

      if(iSeq==0)
      {
         v->a=16;
         v->b=12;
         v->w=160;
         v->h=120;
         v->x=0;
         v->y=0;

         resizer.decSize(v,pI,pO);

         if(calcDifs(pO)>1500)
         {
            iSendNext=30;
            iI=40;
            iJ=0;
         }
         else
         {
            iX=1;
         }

         if(iX==0)
            memcpy(o_difs,pO,sizeof(o_difs));
      }
      if(iSendNext<=0)return 0;

      if(iX)
      {
         int cx=40,cy=20;
         v->a=40;
         v->b=20;
         if(iSendNext>26)
         {
            cx=80;cy=40;
         }
         //{


         v->w=cx;
         v->h=cy;

         v->y=iJ;
         v->x=iI;

         iJ+=cy;
         if(iJ>=120)
         {
            iJ=0;
            iI+=cx;
         }

         


         iLast=1;
         iSendNext--;
         
         if(iI>=160)iI=0;

      }
      else
      {
         v->a=40;
         v->b=15;     
         iLast=0;
         switch(iSeq)
         {
            case 0:v->x=0;v->y=0;v->w=160;v->h=60;break;
            case 1:v->x=0;v->y=60;v->w=160;v->h=60;break;
         }
         iSeq++;
         if(iSeq>1)
         {
            iSeq=0;
            iLast=1;
         }
      }
 
      v->w+=v->x;
      v->h+=v->y;

      resizer.decSize(v,pI,pO);
      bits24to12(v,pO, pO);
      return v->b*v->a*3/2+sizeof(CTincDecSize::vhdr);
   }
   virtual int encodeNext(){

      return !iLast;
   }
   //t_rgb outx[160*120];
   unsigned char outx[160*120*3];
   virtual int decode(unsigned char *pI, unsigned char *pO, int iLen)
   {
      CTincDecSize::vhdr *v=(CTincDecSize::vhdr *)pI;
      if(v->id!=1)return -1;
      pI+=sizeof(CTincDecSize::vhdr);
      bits12to24(v,pI,(unsigned char *)&outx);
      resizer.incSize(v,(unsigned char *)&outx,pO);

      // incSize(v,pI,pO);
      

      return v->cx*v->cy*3;
   }
};

class CTVCod: public CVCodecBase{
   typedef struct{
      unsigned char r;
      unsigned char g;
      unsigned char b;
   }t_rgb;
   typedef struct{
      short id;
      short len;
      short x;
      short y;
      short w;
      short h;
      short a;
      short b;
      short cx;
      short cy;
   }vhdr ;
public:
   int iSeq;
   int iF;
   int jF;
   unsigned char c_difs[30*40];
   unsigned char o_difs[30*40];
   int iDifs;
   int iMinDifs;
   int iAvgDifs;
   int iDifCnt;
   int iSendNext;
   int iPrevDif;
   CTVCod()
   {
      cVO=NULL;
      memset(c_difs,0,sizeof(c_difs));
      memset(o_difs,0,sizeof(o_difs));
      iSeq=-1;
      iF=jF=0;
      iMinDifs=0x7fffffff;
      iSendNext=5;
      iAvgDifs=0;
      iDifCnt=0;
      iPrevDif=0;
   };
   const char *getName(){return "TiVi-CC";}
   const char *getShortName(){return getName();}
   int getId(){return 1115;}
   int encode(unsigned char *pI, unsigned char *pO, int iLen)
   {
      int ol=iLen;
      iLen=getMask40x30(pI,pO,iLen);
      memcpy(c_difs,pO+sizeof(vhdr),1200);;
      int i;

      int x;
      iDifs=0;
      for(i=0;i<1200;i++)
      {
         x=absm((int)o_difs[i]-(int)c_difs[i]);
         iDifs+=(x+1)*(x+1);
       //  o_difs[i]=c_difs[i];
         //memcpy(o_difs,pO+sizeof(vhdr),1200);;
      }
      if(iMinDifs==0x7fffffff)
      {
         iDifs=10000;
      }



      //if(iDifs<iMinDifs*2)//;iMinDifs*10/8)

      //if(iAvgDifs*4>iDifs*iDifCnt)//;iMinDifs*10/8)
      int iIsSilence;
      
      if(iPrevDif>iDifs)
      {
         if(iDifs==0)
            iDifs=1;
         iIsSilence=iPrevDif*4/iDifs;
      }
      else
      {
         if(iPrevDif==0)
            iPrevDif=1;
         iIsSilence=iDifs*4/iPrevDif;

      }
      iIsSilence=iIsSilence<8;


      if((iDifs+iPrevDif)<70000)
         //iIsSilence || 
         //absm(iPrevDif-iDifs)*2<iMinDifs*8)
      {
         iSendNext--;
         if(iSendNext<0)
         {
            if(iSendNext==-1)
               iSeq=1;
            iLen=0;
         }
      }
      else
      {
         memcpy(o_difs,c_difs,1200);;
         iSendNext=1;
         if(iDifCnt>100)
         {
            iAvgDifs=iAvgDifs/iDifCnt;
            iDifCnt=40;
            iAvgDifs*=iDifCnt;
         }
         else
         {
            iAvgDifs+=iDifs;
            iDifCnt++;
         }
      }
      iPrevDif=iDifs;


      iMinDifs=min(iMinDifs,iDifs);
      if(iLen==0)
      {
         if(iSeq!=-1)
         {
            iLen=getMask40x30(pI,pO,ol,iSeq);
            iSeq++;
            if(iSeq>20)iSeq=-1;
         }
      }
      return iLen;

   }
   int getMask40x30(unsigned char *pI, unsigned char *pO, int iLen ,int id=0)
   {
      int i,j;
      unsigned char *tmp=pO;
      vhdr *v=(vhdr *)pO;
      pO+=sizeof(vhdr);


      if(id>4)
      {
      v->w=40;
      v->h=30;

      iF+=v->w;
      if(iF>=160)
      {
         iF=0;
         jF+=v->h;
         if(jF>=120)
         {
            jF=0;
         }
      }
      v->x=iF;
      v->y=jF;
      }
      else
      {


      switch(id)

      {
         case 1:v->x=0;v->y=0;v->w=80;v->h=60;break;
         case 2:v->x=0;v->y=60;v->w=80;v->h=60;break;
         case 3:v->x=80;v->y=0;v->w=80;v->h=60;break;
         case 4:v->x=80;v->y=60;v->w=80;v->h=60;break;
         default:v->x=0;v->y=0;v->w=160;v->h=120;break;
      }
      }

      v->w+=v->x;
      v->h+=v->y;
      

      v->cx=160;
      v->cy=120;
      v->a=40;
      v->b=30;

      int dx=(v->w-v->x)/v->a;
      int dy=(v->h-v->y)/v->b;

      int w=v->cx;//v->w-v->x;
      int col;
     // t_rgb *r=(t_rgb*)pI;
//      t_rgb *r1;
      int dc=dx*dy*3;
      unsigned char *pr;

      int a,b;
      for(j=v->y;j<v->h;j+=dy)
         for(i=v->x;i<v->w;i+=dx)//4=v->w/v->a;
         {
            col=0;

            for(a=0;a<dx;a++)
               for(b=0;b<dy;b++)
               {
                //  r1=&r[w*(j+b)+(i+a)];
                 // col+=r1->g+r1->b+r1->r;
                  pr=&pI[(w*(j+b)+(i+a))*3];
                  col+=*(pr)+*(pr+1)+*(pr+2);
                  //col+=pI[(w*(j+b))*3+(i+a)*3];

               }
               
            //r1=&r[w*(j+b)+(i+a)];
            //col=pI[(w*(j+b))*3+(i+a)*3];

            col/=dc;

            *pO=col;

            pO++;
         }
      v->id=1;
      v->len=pO-tmp;


      return v->len;


   }
   int decode(unsigned char *pI, unsigned char *pO, int iLen)
   {

      vhdr *v=(vhdr *)pI;
      pI+=sizeof(vhdr);
      int i,j;

      int dx=(v->w-v->x)/v->a;
      int dy=(v->h-v->y)/v->b;


      int w=v->cx;

      unsigned char  *o1=pO;
      unsigned char  *o;
      int a,b;

      for(j=v->y;j<v->h;j+=dy)
         for(i=v->x;i<v->w;i+=dx)
         {
          
            for(a=0;a<dx;a++)
               for(b=0;b<dy;b++)
            {
               //o=&o1[w*(j+b)+(i+a)];
               //o->r=o->g=o->b=*pI;
               o=&o1[(w*(j+b)+(i+a))*3];
               *(o)=*(o+1)=*(o+2)=*pI;

            }
              
            pI++;

           // iOutLen+=dx*dy*3;
         }
         //saveold

      return  (v->cx*v->cy*3);// (iLen-sizeof(vhdr))*3*dx*dy;// iOutLen;
   }

};

#endif
