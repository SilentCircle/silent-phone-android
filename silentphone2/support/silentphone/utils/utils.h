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

#ifndef _T_UTILS_H
#define _T_UTILS_H

int cmpmyUnicode(short *src,short *dst,int iShorts);
int isEqual(char * src, char * dst, int iLen);
unsigned int ipstr2long(int iLen,char *p);
inline unsigned int ipstr2long(char *p)
{
   return ipstr2long(0,p);
}
int isNatIP(unsigned int ip);
unsigned int reverseIP(unsigned int ip);
unsigned int strToUint(char * p);
unsigned int strToUint(unsigned short *p);
int genereteRecTone(char* data,int iMaxLen,char * ds, int iBitRate);
void genereteTone(float iFreq,float iFreq2,int iBitRate, int iVol, int iPlay,int iWait,char * data, int iBufSize);
inline void genereteTone(float iFreq,int iBitRate, int iVol, int iPlay,int iWait,char *data, int iBufSize)
{
   genereteTone(iFreq, iFreq, iBitRate, iVol,  iPlay, iWait, data, iBufSize);
}
void convert16to8(char *dst, const short *src, int iLen);//TODO utf8
void convert16to8S(char *dst, int iMaxDstSize, const short *src, int iLen);
void convert16_8(short *src, int iLen);
void convert8_16(char *c, int iLen);
void convert8_16(unsigned char *c, unsigned  short *s, int iLen, int fZeroTerminate=1);
const char * intToIPStr(unsigned int ip,char *ipStr, int iMaxIPStrSize);
char * trim(char *sz);
int containChar(char * sz,char * szChars);
int isValidSz(char *sz,char c,char *szValidChars);
int t_isEqual_case(const char *src, const char *dst,int iLen);
int t_isEqual(const char *src, const char *dst,int iLen);
static int cmpmy(const char *src, const char *dst,int iLen){
   return t_isEqual_case(src,dst,iLen);
}

void safeStrCpy(char *dst, const char *name, int iMaxSize);
int t_snprintf(char *buf, int iMaxSize, const char *format, ...);

char *loadFile(const  char *fn, int &iLen);
void saveFile(const char *fn,void *p, int iLen);
void deleteFile(const char *fn);
char *loadFileW(const  short *fn, int &iLen);
void saveFileW(const short *fn,void *p, int iLen);
void deleteFileW(const short *fn);

unsigned int getTickCountOs();
int fixPostEncoding(char *dst, const char *p, int iLen);
int fixPostEncodingToken(char *dst, int iMaxLen, const char *p, int iLen);


int isPhone(const char * sz,int len);






#endif //_T_UTILS_H
