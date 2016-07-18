//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#ifndef _C_T_ADDR_H
#define _C_T_ADDR_H



#include <stdio.h>
#include <string.h>
#include "../utils/utils.h" 
#include "../utils/CdtMacros.h"

class ADDR{
   int iPortStr;
public:
   enum {eMaxDNS_SIZE=(128-16)};
   char bufAddr[eMaxDNS_SIZE];//dns
   ADDR(unsigned int ip=0, unsigned int _port=0):ip(ip)
   {
      bufAddr[0]=0;
      iPortStr=0;
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
   void operator =(const char *p)
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
   inline int operator ==(ADDR &addr)
   {
      if(bufAddr[0] && strcmp(bufAddr, addr.bufAddr)==0)return 1;
      if(ip!=addr.ip)return 0;
      if(port)
         return (port==addr.port);
      return portNF==addr.portNF;
   }
   inline int operator !=(ADDR &addr)
   {

      if(ip!=addr.ip)return 1;

      if(port)
         return port!=addr.port;
      return portNF!=addr.portNF;
   }
   inline void operator =(ADDR &addr)
   {
      ip=addr.ip ; port=addr.port;
      portNF=addr.portNF;
      strcpy(bufAddr,addr.bufAddr);
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
//private:
   unsigned int ip;
private:
   unsigned int port;
   unsigned int portNF;
};



#endif 
//_C_T_ADDR_H
