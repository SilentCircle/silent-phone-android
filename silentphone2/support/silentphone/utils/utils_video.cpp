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

static inline int clamp(int a){if((unsigned int)a>255)a= 255 & (~(a>>31));return a;}
static inline int clamp8(int a){if((unsigned int)a>65535){if(a<0)a=0;else a=65535;}return a&0xff00;}
static inline int clamp16(int a){if((unsigned int)a>16777215){if(a<0)a=0;else a=16777215;}return a&0xff0000;}

static inline int getInt(int y, int u, int v){
   int i;
	int yc=298 * (y-16)+128;
   u-=128;
   v-=128;
   i= clamp(( yc           + 409 * v ) >> 8);//&0xff;
   i|=clamp8(( yc - 100 * u - 208 * v ));//&0xff00;
   i|=clamp16(( yc + 516 * u           )<<8);//&0xff0000;
	
   return i;//(int)(byte)(r&0xff)|((int)((byte)g&0xff)<<8)|((int)((byte)b&0xff)<<16);//(int)((byte)(i&0xff));//(y)|(u<<8)|(v<<16);//i;
}

static inline void getSI(unsigned short &rs, int &ri, int y, int u, int v){
   int c1,c2,c3;
	int yc=298 * (y-16)+128;
   u-=128;
   v-=128;
   c1= clamp(( yc           + 409 * v ) >> 8);//&0xff;
   c2=clamp8(( yc - 100 * u - 208 * v ));//&0xff00;
   c3=clamp16(( yc + 516 * u           )<<8);//&0xff0000;
   ri=c1|c2|c3;
   c1>>=3;
   c2>>=(5);
   c2&=0x07e0;
   c3>>=8;//3+2,3+2+3
   c3&=0xf800;
   c3|=(c2|c1);
   rs=c3;
	
}

void convertNV21toRGB(unsigned char *b, int w, int h, int *idata, unsigned short *sdata){
   int i,j;
   int y=0;
   int uo=w*h;
   int o=0;
   int u,v;
   int strideuv=w;
   if(sdata && idata){
      for(j=0;j<h;j++){
         uo=w*h+(j/2)*strideuv;
         for(i=0;i<w;i+=2){
            u=b[uo];v=b[uo+1];
            getSI(sdata[o],idata[o],b[y],u,v);
            y++;o++;
            getSI(sdata[o],idata[o],b[y],u,v);
            y++;o++;
            uo+=2;
         }
      }
   }
   else if(idata){
      for(j=0;j<h;j++){
         uo=w*h+(j/2)*strideuv;
         for(i=0;i<w;i+=2){
            u=b[uo];v=b[uo+1];
            idata[o]=getInt(b[y],u,v);
            y++;o++;
            idata[o]=getInt(b[y],u,v);
            y++;o++;
            uo+=2;
         }
      }
   }
}
