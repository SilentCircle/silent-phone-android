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

#include <stdio.h>
#include <string.h>

#define T_MAX_DEV_ID_LEN 63
#define T_MAX_DEV_ID_LEN_BIN (T_MAX_DEV_ID_LEN/2)

static char bufDevID[T_MAX_DEV_ID_LEN+2];
static char bufMD5[T_MAX_DEV_ID_LEN+32+1];



void setFileAttributes(const char *fn, int iProtect);
char *loadFile(const  char *fn, int &iLen);
void saveFile(const char *fn,void *p, int iLen);
char *getFileStorePath();



void initDevID(){
   
   static int iInitOk=0;
   if(iInitOk)return;
   iInitOk=1;
   memset(bufDevID,0,sizeof(bufDevID));
#if 0
   //depricated  6.0
   NSString *n = [[UIDevice currentDevice]uniqueIdentifier];
   
   const char *pn=n.UTF8String;
#else
   int iDevIdLen=0;
   char fn[1024];
   snprintf(fn,sizeof(fn)-1, "%s/devid-hex.txt", getFileStorePath());
   
   char *pn=loadFile(fn, iDevIdLen);
   
   if(!pn || iDevIdLen<=0){
      
      pn=&bufDevID[0];
      
      FILE *f=fopen("/dev/urandom","rb");
      if(f){
         unsigned char buf[T_MAX_DEV_ID_LEN_BIN+1];
         fread(buf,1,T_MAX_DEV_ID_LEN_BIN,f);
         fclose(f);
         void bin2Hex(unsigned char *Bin, char * Hex ,int iBinLen);
         bin2Hex(buf, bufDevID, T_MAX_DEV_ID_LEN_BIN);
         bufDevID[T_MAX_DEV_ID_LEN]=0;
         
         saveFile(fn, bufDevID, T_MAX_DEV_ID_LEN);
         setFileAttributes(fn,0);
      }
      
   }
   
#endif
   void safeStrCpy(char *dst, const char *name, int iMaxSize);
   safeStrCpy(&bufDevID[0],pn,sizeof(bufDevID)-1);
   
   int calcMD5(unsigned char *p, int iLen, char *out);
   calcMD5((unsigned char*)pn,strlen(pn),&bufMD5[0]);
   
   
}



const char *t_getDevID(int &l){
   initDevID();
   l=63;
   return &bufDevID[0];
}

const char *t_getDevID_md5(){
   initDevID();
   return &bufMD5[0];
}


