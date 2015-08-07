//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

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