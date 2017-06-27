/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2017, Silent Circle, LLC.  All rights reserved.

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
#ifndef _C_T_ADDR_H
#define _C_T_ADDR_H



#include <stdio.h>
#include <string.h>
#include "../utils/utils.h" 
#include "../utils/CdtMacros.h"
/*
 class CTInt{
public:
   CTInt(){ip=0;}
   CTInt(unsigned int i){
      ip = i;
   }
   void operator =  (unsigned int i){
      
      ip = i;
      addr->changesIP(ip);
   }
   
   int operator()(){//getter
      return ip;
   }
   
   int operator ! (){
      return !ip;
   }
   inline int operator !=(CTInt &i){return i.ip!=ip;}
   inline int operator !=(unsigned int i){return i!=ip;}
   inline int operator ==(CTInt &i){return i.ip==ip;}
   inline int operator ==(unsigned int i){return i==ip;}
   
   unsigned int ip;

};
*/

typedef struct{
   
   int family;
   int sockType;
   int protocol;
   unsigned char sock_addr[64];
   int sock_len;
   
}T_ADDR_INFO_CONNECTED;

class ADDR{
   int iPortStr;
public:
   void changesIP(unsigned int _new){
   }
   enum {eMaxDNS_SIZE=(128-16)};
   char bufAddr[eMaxDNS_SIZE];//dns
   ADDR(unsigned int ip=0, unsigned int _port=0):ip(ip)
   {
      clear();
      
      setPort(_port);

   }
   ADDR(const char *p)
   {
      clear();
      *this=(p);

   }
   inline void clear()
   {
      iPortStr=0;
      bufAddr[0]=0;
      ip=0;port=0;portNF=0;
      memset(_SOCK_ADDR, 0 ,sizeof(_SOCK_ADDR));
      ipv6AddrLen=0;
      v6[0]=v6[1]=v6[2]=v6[3]=0;
   }
   inline void setPort(unsigned int uiPort)
   {
      unsigned short sp=(unsigned short)uiPort;
      port=sp;
      SWAP_SHORT(sp);
      portNF=(int)sp;
   }
   inline void setPortNF(unsigned int uiPortNF)
   {
      unsigned short sp=(unsigned short)uiPortNF;
      portNF=sp;
      SWAP_SHORT(sp);
      port=(int)sp;
   }
   void operator = (const char *p)
   {
      char buf[eMaxDNS_SIZE];
      strncpy(bufAddr,p,sizeof(bufAddr)-1);
      bufAddr[sizeof(bufAddr)-1]=0;
      
      trim(bufAddr);
      portNF = port = 0;

      strncpy(buf,bufAddr,sizeof(buf)-1);
      buf[sizeof(buf)-1]=0;
      
      char *pPort=buf;

      while(*pPort)
      {
         if(*pPort==':')
         {
            *pPort=0;
            pPort++; 
            setPort(strToUint(pPort));
            break;
         }
         pPort++; 
      }
      ip=ipstr2long(buf);
      SWAP_INT(ip);
      iPortStr=port;

   }
   char *toStrPort(char *ipStr){
      sprintf(ipStr, "%d", port);
      return ipStr;
   }
   char *toStr(char *ipStr, int iShowPort=1)
   {
      if(!ip && bufAddr[0] && port==iPortStr)
      {
         strcpy(ipStr,bufAddr);
         return ipStr;
      }
      int l;
      unsigned int a1=((unsigned char *)&ip)[0];
      unsigned int a2=((unsigned char *)&ip)[1];
      unsigned int a3=((unsigned char *)&ip)[2];
      unsigned int a4=((unsigned char *)&ip)[3];
      l=sprintf(ipStr,"%u.%u.%u.%u",a1,a2,a3,a4);
      if(iShowPort)
      {
         l+=sprintf(ipStr+l,":%u",port);
      }
      return ipStr;
   }
   inline unsigned int getPortNF()
   {
      if(portNF) return portNF;
      if(port)
      {
         setPort(port);
         return portNF;
      }
      return 0;
   }
   inline unsigned int getPort()
   {
      if(port) return port;
      if(portNF)
      {
         setPortNF(portNF);
         return port;
      }
      return 0;
   }
   int sameV6(ADDR &addr){
      if(ipv6AddrLen!=addr.ipv6AddrLen)return 0;
      
      if(v6[0]!=addr.v6[0] || v6[1]!=addr.v6[1] || v6[2]!=addr.v6[2] || v6[3]!=addr.v6[3])return 0;
      
      return 1;
   }
   inline int operator ==(ADDR &addr)
   {
      if(bufAddr[0] && strcmp(bufAddr, addr.bufAddr)==0)return 1;
      
      if(ipv6AddrLen!=addr.ipv6AddrLen)return 0;
      
      if(ipv6AddrLen && memcmp(_SOCK_ADDR, addr._SOCK_ADDR, ipv6AddrLen))return 0;
      if(!ipv6AddrLen){
      
         if(ip!=addr.ip)return 0;
      }
      else{
         if(v6[0]!=addr.v6[0] || v6[1]!=addr.v6[1] || v6[2]!=addr.v6[2] || v6[3]!=addr.v6[3])return 0;
      }
      if(port)
         return (port==addr.port);
      return portNF==addr.portNF;
   }
   inline int operator !=(ADDR &addr)
   {
      return !(*this==addr);
      /*
      if(ipv6AddrLen!=addr.ipv6AddrLen)return 1;
      
      if(ipv6AddrLen && memcmp(_SOCK_ADDR, addr._SOCK_ADDR, ipv6AddrLen))return 1;
      if(!ipv6AddrLen){
         if(ip!=addr.ip)return 1;
      }

      if(port)
         return port!=addr.port;
      return portNF!=addr.portNF;
       */
   }
   inline void operator =(ADDR &addr)
   {
      if(hasIPSet() && ipv6AddrLen &&
         v6[0]==addr.v6[0]&&
         v6[1]==addr.v6[1] &&
         v6[2]==addr.v6[2] &&
         v6[3]==addr.v6[3]){
         //do not reset ip4
         port=addr.port;
         portNF=addr.portNF;
         ipv6AddrLen = addr.ipv6AddrLen;
         memcpy(_SOCK_ADDR, addr._SOCK_ADDR, ipv6AddrLen);
         
         return;
      }
      ip=addr.ip ; port=addr.port;
      portNF=addr.portNF;
      strcpy(bufAddr,addr.bufAddr);
      
      ipv6AddrLen = addr.ipv6AddrLen;
      memcpy(_SOCK_ADDR, addr._SOCK_ADDR, ipv6AddrLen);
      v6[0]=addr.v6[0];v6[1]=addr.v6[1];
      v6[2]=addr.v6[2];v6[3]=addr.v6[3];
      /*
      if(portNF)
         portNF=addr.portNF;
      else
      {
         portNF=port;
         SWAP_SHORT(portNF);
      }
      */
   }
   static void stripPort(const char *in, char *dst, int iDstSize){

      iDstSize--;
      for(int n=0;;n++){
         if(n>=iDstSize || in[n]==':' || in[n]==0){
            dst[n]=0;
            break;
         }
         dst[n]=in[n];
      }
   }
   inline int addr6didChange(){return prevIPv6!=ip && ip;}
   
   void setIpv6sockAddr(void *p, int iLen, unsigned int _portNF){
      memcpy(_SOCK_ADDR, p, iLen);
      ipv6AddrLen = iLen;
      setPortNF(_portNF);
      prevIPv6 = ip;
   }
   
   void setIPv6(void *p){
      unsigned int *p6=(unsigned int*)p;
      v6[0]=p6[0];v6[1]=p6[1];v6[2]=p6[2];v6[3]=p6[3];
   }

   void *getV6Sock(int *len){
      *len = ipv6AddrLen;
      return &_SOCK_ADDR[0];
   }
   int isV6(){return !!ipv6AddrLen;}
   
   int hasIPSet(){return ipv6AddrLen || ip || v6[0] || v6[1]|| v6[2]|| v6[3];}
   
   inline int sameIP(ADDR &addr){
      if(sameV6(addr))return 1;
      
      return addr.ip == ip;
   }
//private:
   unsigned int ip;
private:
   unsigned int prevIPv6;
   int ipv6AddrLen;
   unsigned int v6[4];
   unsigned char _SOCK_ADDR[32];//must be at least sizeof(sockaddr_in6)
   
   unsigned int port;
   unsigned int portNF;
};



#endif 
//_C_T_ADDR_H
