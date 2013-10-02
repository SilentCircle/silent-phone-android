/*
Created by Janis Narbuts
Copyright © 2004-2012 Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

#ifdef _WIN32
#define snprintf _snprintf
#endif

#include <stdio.h>
#include <string.h>
#include <cryptcommon/aescpp.h>

int hex2BinL(unsigned char *Bin, char *Hex, int iLen);
void bin2Hex(unsigned char *Bin, char * Hex ,int iBinLen);

/**
 * These funtions encrypt and decrypt the SIP password.
 *
 * The functions use the Cipher Feedback (CFB) cipher mode to avoid padding of the SIP password in
 * case the SIP password is not a multiple of the cipher blocksize.
 *
 * Using a static, non-random Initialization Vector (IV) is perfectly acceptable here because we have
 * the following conditions:
 *
 * - The SIP password is random data and different for each installation/client.
 * - The encryption key is random and each client installation generates its own key.
 * - The client uses the SIP password only as key to generate the response during SIP authentication.
 * - Tampering with the SIP password in its encrypted form leads wrong decryption and in turn to a
 *   Denial of Service (DoS) and does not reveal any sensitive information.
 * - The client never sends this encrypted data to any other party or client.
 *
 * For the same reasons we don't authenticate the encrypted data with a MAC. Computing a MAC does
 * not enhance security but complicates code and adds handling and storage of the MAC.
 */

//iv must be atleast AES_BLOCK_SIZE long
#define IV_INIT "___salt,12345678"

static AESencrypt aes;       // WD - use after initialization and setting the key
static int iPasswordWasSet = 0;

int isAESKeySet(){
   return iPasswordWasSet;
}


void setPWDKey(unsigned char *k, int iLen){

   //aes.init();            // WD - just as a safeguard in case AES compiled without static tables

   // The AES object stores the key internalliy in "ready to use" format. No need
   // to store it otherwise.. The key length is given in bits: 128 bits == 16 bytes.
   iPasswordWasSet = 1;
   if (iLen == 16)
      aes.key128(k);
   else if (iLen == 24)
      aes.key192(k);
   else if (iLen == 32)
      aes.key256(k);
   else{
      iPasswordWasSet=0;
      return  ;//should we exit here?
   }
}
/*
int encryptTest(unsigned char *binIn, int iLen,  int iIndex){
 
   unsigned char inCpy[1024];
   unsigned char iv[] = IV_INIT;
   snprintf((char*)&iv[0],sizeof(iv),"%d",iIndex);
   
   for(int i=0;i<iLen;i+=1000){
      int bytesEncrypt=iLen;
      if(bytesEncrypt>1000)bytesEncrypt=1000;
      memcpy(inCpy, &binIn[i], bytesEncrypt);
      aes.cfb_encrypt((unsigned char*)inCpy, &binIn[i], bytesEncrypt, iv);
   }
   aes.mode_reset();
   return iLen;
}
*/

int encryptPWD(const char *pwd, int iLen, char *hexOut, int iMaxOut, int iIndex){

   if(iLen==0 && iMaxOut){
      hexOut[0]=0;
      return 0;
   }
   
   if (iLen < 1 || iLen*2 >= iMaxOut || iLen >= 128 || !iPasswordWasSet){
      return -1;
   }

   unsigned char o[256];

   // WD - Initailization vector can be plaintext
   // JN - iv can not be global - aes::cfb_encrypt is changing it
   unsigned char iv[] = IV_INIT;
   snprintf((char*)&iv[0],sizeof(iv),"%d",iIndex);
   aes.cfb_encrypt((unsigned char*)pwd, o, iLen, iv);
   aes.mode_reset();

   bin2Hex(o, hexOut, iLen);

   return iLen*2;
}

int decryptPWD(const char *hexIn, int iLen, char *outPwd, int iMaxOut, int iIndex){

   if (iLen < 1 || iLen >= 2*iMaxOut || iLen >= 255 || !iPasswordWasSet){

      return -1;
   }
   unsigned char o[256];
   hex2BinL(o, (char*)hexIn, iLen);

   //printf("len=%d idx=%d\n",iLen,iIndex);
   // WD - Initialization vector can be plaintext
   // JN - iv can not be global - aes::cfb_encrypt is changing it
   unsigned char iv[] = IV_INIT;
   snprintf((char*)&iv[0],sizeof(iv),"%d",iIndex);

   aes.cfb_decrypt(o, (unsigned char*)outPwd, iLen/2, iv);
   aes.mode_reset();
   outPwd[iLen/2]=0;

   return iLen/2;
}

void test_pwd_ed(int iSetKey){

   // The password can be of variable length, no need to be a multiple of AES_BLOCKSIZE
   const char *pwd = "==ok==pfjadfaspdfo0 vfav;hu;fkvm eprflk;s'alp'ofpe ==ok==";
   char hex[256];
   char pwdOut[256];

   if(iSetKey){
      unsigned char key[16] = "bbcxabbaxcbqbdx";
      int iKeyLen = 16;

      // need to call this only once per AES key.
      // After setting the key you can use the aes object to encrypt/decrypt many times.
      setPWDKey(key, iKeyLen);
   }
   // encrypt some data. If done with the complete data reset the mode context. In our
   // case we only need to call encrypt once (password is short). If we need to encrypt a
   // big file we would loop and read the file in e.g. 4KB blocks and encrypt each block
   // and call mode_reset() only after the whole file was processed.
   int l = encryptPWD(pwd, strlen(pwd), hex, sizeof(hex)-1,0);

   // decrypt some data. If done with the complete data reset the mode context.
   decryptPWD(hex, l, pwdOut, sizeof(pwdOut)-1,0);

   printf("pwd test [%s] [%s]\n",pwd, pwdOut);
   if(strcmp(pwd, pwdOut))
      puts("pwd test fail");
   else
      puts("pwd test ok");
}

