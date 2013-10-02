/* 
Copyright (C) 1991-2, RSA Data Security, Inc. Created 1991. All
rights reserved.

License to copy and use this software is granted provided that it
is identified as the "RSA Data Security, Inc. MD5 Message-Digest
Algorithm" in all material mentioning or referencing this software
or this function.

License is also granted to make and use derivative works provided
that such works are identified as "derived from the RSA Data
Security, Inc. MD5 Message-Digest Algorithm" in all material
mentioning or referencing the derived work.

RSA Data Security, Inc. makes no representations concerning either
the merchantability of this software or the suitability of this
software for any particular purpose. It is provided "as is"
without express or implied warranty of any kind.
These notices must be retained in any copies of any part of this
documentation and/or software.
*/

/* POINTER defines a generic pointer type */
#ifndef _T_MD5_H_
#define _T_MD5_H_
typedef unsigned char *POINTER;
typedef unsigned short int UINT2;
typedef unsigned int UINT4;

/* MD5 context. */
typedef struct {
  UINT4 state[4];                                   /* state (ABCD) */
  UINT4 count[2];        /* number of bits, modulo 2^64 (lsb first) */
  unsigned char buffer[64];                         /* input buffer */
} MD5_CTX;

void MD5Init (MD5_CTX *);
void MD5Update (MD5_CTX *, unsigned char *, unsigned int);
void MD5Final (unsigned char [16], MD5_CTX *);
void md5_calc(unsigned char *output, unsigned char *input, unsigned int inlen);
//int  getHash(unsigned char *strHash, const char *strzNonce, const char *strzPwd);
int  getHash16(unsigned char *strHash16, const char *strzNonce, const char *strzPwd);
int  getHash32(unsigned char *strHash32, const char *strzNonce, const char *strzPwd);
//void writeHash(const char *strzNonce, const char *strzPwd);

class CTMd5{
public:
   CTMd5():iFinal(0)
   {
      MD5Init(&ctx);
   }
   ~CTMd5(){}
   void update(unsigned char *p, unsigned int uiLen)
   {
      if(iFinal)
      {
         iFinal=0;
         MD5Init(&ctx);
      }
      MD5Update(&ctx,p,uiLen);
   }
   void final(unsigned char buf[16])
   {
      MD5Final (buf, &ctx);
      iFinal=1;
   }
   
   unsigned int final()
   {
      int i;
      unsigned int r;
      iFinal=1;
      unsigned int buf[8];//we need only 128bits

      MD5Final ((unsigned char *)&buf[0], &ctx);
      
      r = buf[0];
      
      for(i=1;i<4;i++)
      {
         r^=buf[i];
      }
      
      return r;
   }
   
   const static unsigned char PADDING[64];
private:
   MD5_CTX ctx;
   int iFinal;
};
#endif //_T_MD5_H_
