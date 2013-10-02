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

#ifndef _C_TIVI_SOCK
#define _C_TIVI_SOCK
class ADDR{
public:
   ADDR(unsigned int ip=0,unsigned int port=0):ip(ip)
   {
      setPort(port);
   }
   ADDR(char *p)
   {
       clear();
      *this=(p);
   }
   inline void clear()
   {
      ip=0;port=0;portNF=0;
   }
   inline void setPort(unsigned int uiPort);
   inline void setPortNF(unsigned int uiPortNF);
   void operator =(char *p);
   char *toStr(char *ipStr, int iShowPort=1);
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
         setPort(portNF);
         return port;
      }
      return 0;
   }
   inline int operator ==(ADDR &addr)
   {
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
      if(portNF)
         portNF=addr.portNF;
      else
      {
         portNF=port;
         SWAP_SHORT(portNF);
      }
   }
   unsigned int ip;
private:
   unsigned int port;
   unsigned int portNF;
};


class CTSock{
public:
#define MAX_SOCK_COUNT 4
   typedef struct{
      SOCKET sock;
      int iThId;
   }SOCK;
   CTSock();
   ~CTSock();
   int createSock(ADDR *addrToBind,int toAny)
   {
      if(createSock())
         return Bind(addrToBind,toAny);
      return -1;
   }
   int createSock();
   int reconect();
   int closeSocket();
   int sendTo(char *buf, int iLen, ADDR *address);
   int recvFrom(char *buf, int iLen, ADDR *addr);
   int Bind(ADDR *addrToBind, int toAny);

   int sock;
   ADDR addr;
   int iIsBinded;
private:
   int sockHeelperInUse;
   int iNeedClose;
   SOCK sockHeelper[MAX_SOCK_COUNT];

   int getSocket(int iThId);
};
#endif //_C_TIVI_SOCK