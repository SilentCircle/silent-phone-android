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

#ifndef _C_TIVI_SOCK
#define _C_TIVI_SOCK

#include <stdio.h>
#include <string.h> 

#include "../baseclasses/ADDR.h"//ADDR

typedef int (FNC_ON_SOCK_RECV_CALLBACK)(char *p, int iRec, ADDR *addr, void *pUserData);

class CTSockRecvCB{
public:
   CTSockRecvCB(FNC_ON_SOCK_RECV_CALLBACK *fnc,void *pCBRetdata)
   :fnc(fnc),pCBRetdata(pCBRetdata){iRestorable=1;}
   
   FNC_ON_SOCK_RECV_CALLBACK *fnc;
   void *pCBRetdata;
   
   int iRestorable;//if object is on stack or is short living set iRestorable  to 0
   
   inline int operator ()(char *p, int iLen, ADDR *a)
   {
      if(fnc==NULL)return -1;

      return fnc(p,iLen,a,pCBRetdata);
   }
   inline void operator =(const CTSockRecvCB &cb)
   {
      pCBRetdata=cb.pCBRetdata;
      fnc=cb.fnc;
      iRestorable = cb.iRestorable;
   }
};

class CTSockBase{
   CTSockRecvCB cbRecv;
   CTSockRecvCB cbPrevOkRecv;
protected:
   inline int onRecv(char *p, int iRec, ADDR *addr)
   {
      //if(cbRecv.fnc==NULL)return -1;
      return cbRecv(p,iRec,addr);//(cbRecv.fnc)(p,iRec,addr,cbRecv.pCBRetdata);
   }
   CTSockBase():cbRecv(NULL,NULL),cbPrevOkRecv(NULL,NULL),iBytesSent(0){}
   
public:
   long long iBytesSent;
   void tryRestoreCB(const CTSockRecvCB &cur,  const CTSockRecvCB *prev){
      if(cur.fnc==cbRecv.fnc && cur.pCBRetdata==cbRecv.pCBRetdata && prev && prev->iRestorable)
         cbRecv=*prev;
      else if(cbPrevOkRecv.iRestorable){
         cbRecv=cbPrevOkRecv;
         cbRecv.iRestorable=1;
      }
      else {cbRecv.fnc=NULL;}
   }
   
//TODO addCB,remCB(const CTSockRecvCB &cb)
   void setCB(const CTSockRecvCB &cb, CTSockRecvCB &old, int iCanRestore=1)
   {
      //CTSockRecvCB *old=recvCB;
      //recvCB=cb;
      
      if(cbRecv.iRestorable){
         cbPrevOkRecv.iRestorable = 2;
         cbPrevOkRecv=cbRecv;
      }
      old=cbRecv;
      cbRecv=cb;
      cbRecv.iRestorable=iCanRestore;
      
      if(iCanRestore){
         cbPrevOkRecv.iRestorable = 2;
         cbPrevOkRecv=cbRecv;
      }
   }

};
////---------symb
#if defined(T_USE_CUSTOM_SOCKETS)

#define CLOSE_TH  CloseHandle
//#define SOCKET int
   #include <winsock2.h>
   #define socklen_t int

class CTSockCB{
public:
   void *pSockUserData;
};
class CTSock: public CTSockBase{
public:
   CTSockCB &pSockCB;
   CTSock(CTSockCB &cb);
   virtual ~CTSock();
   static int getHostByName(char *psz,int iLen=0);
   static int getLocalIp();
   SOCKET createSock();
   int closeSocket();
   int sendTo(char *buf, int iLen, ADDR *address);
   int recvFrom(char *buf, int iLen, ADDR *address);
   int Bind(ADDR *addrToBind, BOOL toAny);
   void reCreate()
   {
      createSock(&addr,1);
   }
   inline int Bind(int port, BOOL toAny){
      ADDR a;
      a.setPort(port);
      return Bind(&a,toAny);
   }
   int createSock(ADDR *addrToBind,BOOL toAny)
   {
      if(createSock())
         return Bind(addrToBind,toAny);
      return -1;
   }
   int setNewPort(int port)
   {
      closeSocket();
      sock=0;
      Bind(port,1);
      return 0;
   }

   SOCKET sock;//sheit likt custdata
   ADDR addr;
   int iIsBinded;
private:
   int iNeedClose;

};


inline int hasNetworkConnect(unsigned int ip)//TODO may bee use addr
{
   return (ip!=0);
}


#elif defined(__SYMBIAN32__)




#include <coecntrl.h>

#define SOCKET int
inline int hasNetworkConnect(unsigned int ip)
{
   return (ip!=0);
}


#ifndef USE_STD_SOCKETS

#include <in_sock.h>
#include <es_sock.h> 
#ifndef _C_T_THREAD_H
#include "CTThread.h"
#endif

class CTSockCB{//: public CTSockBase{
public:
   CTSockCB(RSocketServ &iSocketServ, RConnection &rConn, int &iMustRecreateSockets)
      :iSocketServ(iSocketServ),rConn(rConn),iMustRecreateSockets(iMustRecreateSockets){}
   RSocketServ &iSocketServ;
   RConnection &rConn;
   int &iMustRecreateSockets;
};

class CTSock: public CTSockBase{
public://tmp
   RSocket *rSock;
   int iIsClosed;
private://temp
   int iNeedClose;
   CTSockCB &cb;

public:
   ADDR addr;

   CTSock(CTSockCB &cb)
      :CTSockBase(),cb(cb)
   {
      rSock=NULL;
      iIsClosed=0;
      iNeedClose=0;

   }

   ~CTSock()
   {
      closeSocket();
   }


   int createSock(ADDR *addrToBind,int toAny)
   {
      if(createSock()==0)
         return Bind(addrToBind,toAny);
      return -1;
   }

   int  createSock()
   {
      //if(checkHandle()!=0)return -3;
      if(rSock){iIsClosed=1;iNeedClose=1;User::After(2000);}
      RSocket *n=NULL;
      RSocket *old=rSock;
      n=new RSocket();
      cb.iMustRecreateSockets=0;
      int r=n->Open(cb.iSocketServ, KAfInet, KSockDatagram, KProtocolInetUdp,cb.rConn)==KErrNone?0:-1;
      rSock=n;
      iIsClosed=0;
      iNeedClose=0;
      if(old)delete old;
      return r;
   }
   void reCreate()
   {
      createSock(&addr,1);
   }
   int setNewPort(int port)
   {
      closeSocket();
      addr.setPort(port);
      CTThread *th=new CTThread();
      th->destroyAfterExit();
      th->create(&thFnc,this);
     // Bind(port,1);
      return 0;
   }
   static int thFnc(void *p){
      CTSock *l=(CTSock*)p;
      Sleep(500);
      l->createSock(&l->addr,1);
      return 0;
   }

   int closeSocket()
   {
      if(iIsClosed)return 0;
      ADDR a="127.0.0.1";
      a.setPort(addr.getPort());
      sendTo("1",1,&a);
      iIsClosed=1;
      iNeedClose=1;
      User::After(5000);

      TRequestStatus aStatus(KErrNone);
      rSock->Shutdown(RSocket::EImmediate,aStatus);
      User::WaitForRequest(aStatus);
      
      User::After(20000);

      rSock->Close();
      delete rSock;
      rSock=NULL;
      return 0;
   }
   int sendTo(char *buf, int iLen, ADDR *address)
   {
      if(0)//cb.iMustRecreateSockets)
      {
         closeSocket();
         addr.ip=0;
         if(createSock(&addr,1))return -1;
      }
      if(iNeedClose || iIsClosed)return -1;
      TRequestStatus aStatus(KErrNone);
      TPtrC8 s((unsigned char*)buf,iLen);

//      checkHandle();

      unsigned int i=address->ip;
      if(!i)i=0x0100007f;
      SWAP_INT(i);
      TInetAddr iAddrUdp(i,address->getPort());

      rSock->SendTo(s,iAddrUdp,0, aStatus);
      User::WaitForRequest(aStatus);
      iBytesSent+=iLen;
      return aStatus==KErrNone?iLen:-1;

   }

   int recvFrom(char *buf, int iLen, ADDR *a)
   {
      
      if(iNeedClose || iIsClosed)
      {
         User::After(100*1000);
         return -2;
      }
      TRequestStatus aStatus(KErrNone);
      TPtr8 s((unsigned char*)buf,0,iLen);
      TInetAddr iAddrUdp;
      

//      checkHandle();

      do{
         rSock->RecvFrom(s,iAddrUdp,0, aStatus);
         User::WaitForRequest(aStatus);
         if(iNeedClose)return -2;
         if(aStatus==KErrNone)
         {
            int rec=s.Length();
            a->setPort(iAddrUdp.Port());
            a->ip=iAddrUdp.Address();
            SWAP_INT(a->ip)
            if(onRecv(buf, rec,a)!=-1)
            {
              // s.SetLength(0);
              // continue;
            }
            return rec;
         }
      }while(0);
     
      a->clear();
      User::After(100*1000);

      return -1;
   }
   int Bind(ADDR *addrToBind, int toAny)
   {
      if(iNeedClose || iIsClosed)
      {
         User::After(100*1000);
         return -2;
      }

      int iMaxTrys=100;
      addr=*addrToBind;
        
      while(iMaxTrys>0){
       //  a.SetPort(addr.getPort());
         if(KErrNone!=rSock->SetLocalPort(addr.getPort()))
         {
            if(toAny==0)
            {
               iMaxTrys=0;
               break;
            }
            addr.setPort(addr.getPort()+2);
            iMaxTrys--;
            continue;
         }
         break;
      }

      return iMaxTrys>0?0:-2;


   }
   //Rth

};

#else //USE_STD_SOCKETS

#include <sys\\types.h>
#include <sys\\unistd.h>
#include <sys\\socket.h>
#include <netinet\\in.h>
#include <arpa\\inet.h>

class CTSock{
public:
#define MAX_SOCK_COUNT 4
   typedef struct{
      SOCKET sock;
      int iThId;
   }SOCK;
   CTSock()
   {
      sock=0;
      iIsBinded=0;
      memset(sockHeelper ,0,sizeof(SOCK)*MAX_SOCK_COUNT);
      sockHeelperInUse=0;
      iNeedClose=0;
   }

   ~CTSock()
   {
      closeSocket();
   }
   /*   //
   static int getHostByName(char *psz,int iLen=0)
   {
      return 0;
   }

   static int getLocalIp()
   {

      return inet_addr("192.168.0.80");;
   }
   */
   int createSock(ADDR *addrToBind,int toAny)
   {
      if(createSock())
         return Bind(addrToBind,toAny);
      return -1;
   }
   void reCreate()
   {
      createSock(&addr,1);
   }

   SOCKET createSock()
   {
      return sock=socket(AF_INET,SOCK_DGRAM,0);
   }


   int closeSocket()
   {
      int i;
      if(iNeedClose==0)
         iNeedClose=1;
      if(sock==0)return 0;
      char buf[16];

      sendTo((char *)&buf,5,&addr);
      
      for(i=0;i<MAX_SOCK_COUNT;i++)
      {
         if(sockHeelper[i].sock)
         {
           close(sockHeelper[i].sock);
           sockHeelperInUse--;
           sockHeelper[i].sock=0;
           sockHeelper[i].iThId=0;

         }
      }
      //TODO shutdown(sock,SHUT_RDWR);//if binded send keep alive
      //int ok=shutdown(sock,3);
      
      return iNeedClose==1?close(sock):0;
   }
   int sendTo(char *buf, int iLen, ADDR *address)
   {
      struct sockaddr_in sa;
      int iThId=(int)RThread().Id();
      SOCKET s=getSocket(iThId);
      if(s<=0)return -2;
      memset(&sa,0,sizeof(struct sockaddr_in));
      sa.sin_family = AF_INET;
      if(iThId==iIsBinded && address->ip)
      {
         sa.sin_addr.s_addr=address->ip;
         sa.sin_port=(unsigned short)address->getPortNF();
        // puts("s1");
      }
      else
      {
         unsigned short us=(unsigned short)address->getPortNF();
         sa.sin_addr.s_addr = 0x0100007f;
         sa.sin_port=(unsigned short)addr.getPortNF();//selfaddr

         //printf("port=%d\n",addr.port);
         if(address->ip==0x0100007f && iNeedClose==0)return 0;

         Mem::Copy(buf+iLen,&address->ip,4);
         Mem::Copy(buf+iLen+4,&us,2);

         iLen+=6;
      }
      return sendto(s,buf,iLen,0,(struct sockaddr *)&sa,  sizeof(sa));
   }
   int recvFrom(char *buf, int iLen, ADDR *addr)
   {
      struct sockaddr_in sa;
      unsigned int recSize = sizeof(sa);
      int ret;

      sa.sin_family = AF_INET;

      while(1)
      {
         ret=recvfrom(sock,buf,iLen,0,(struct sockaddr *)&sa, &recSize);
        // printf("rec=%d\n",ret);
         if(ret>=0 && iNeedClose==0)
         {
            if(sa.sin_addr.s_addr == 0x0100007f && ret>=6)
            {
             //  puts("r1");
               memset(&sa.sin_zero,0,sizeof(sa.sin_zero));
               memcpy(&sa.sin_port,buf+ret-2,2);
               memcpy(&sa.sin_addr.s_addr,buf+ret-6,4);
               if(sa.sin_addr.s_addr!=0x0100007f)
               {
                  ret=sendto(sock,buf,ret-6,0,(struct sockaddr *)&sa, sizeof(sa));
                  if(ret<0)
                     User::After(1000);
                //  printf("sent=%d\n",ret);
               }
               continue;
            }
            else
            {
            //   puts("r2");
               addr->setPortNF(sa.sin_port);
               addr->ip=sa.sin_addr.s_addr;
            }
         }
         else 
         {
            if(iNeedClose==1)
            {
               addr->clear();
            }
            else if(iNeedClose==2)
            {
               iNeedClose=0;
               close(sock);
               this->addr.setPort(0);
               createSock(&this->addr,0);
               continue;
            }
         }
         break;
      }

      return ret;
   }
   int Bind(ADDR *addrToBind, int toAny)
   {
      struct sockaddr_in sa;
      int iMaxTrys=200;
      
      do{
         memset((char *) &sa,0,sizeof(struct sockaddr_in));
         sa.sin_family = AF_INET;

         if(addrToBind->ip)
            sa.sin_addr.s_addr = addrToBind->ip;
         else
            sa.sin_addr.s_addr =htonl(INADDR_ANY);

         sa.sin_port = (unsigned short)addrToBind->getPortNF();

         if(bind(sock, (struct sockaddr *) &sa, sizeof(sa)) >= 0)
         {
            //DEBUG(0,"bind ok");
            //TThreadId &id=
             //  RThread.Id();
            //iIsBinded=id.TThreadId();
            addrToBind->ip=0x0100007f;
            iIsBinded=(int)RThread().Id();//.TUint();
            memcpy(&addr,addrToBind,sizeof(ADDR));

            break;
         }
         iMaxTrys--;

         addrToBind->setPort(addrToBind->getPort()+2);

      }while(toAny && iMaxTrys>0);
/*
      {
         char buf[15];
         ADDR a("159.148.8.103:5060");
         sendTo((char *)&buf,3,&a);
      }
*/
      return iIsBinded?0:-1;
   }
   SOCKET sock;
   ADDR addr;
   int iIsBinded;
private:
   int sockHeelperInUse;
   int iNeedClose;
   SOCK sockHeelper[MAX_SOCK_COUNT];

   SOCKET getSocket(int iThId)
   {
      int i;
      if(iIsBinded==iThId)
      {
         return sock;
      }
      for(i=0;i<MAX_SOCK_COUNT;i++)
      {
         if(sockHeelper[i].sock && iThId==sockHeelper[i].iThId)
         {
            return sockHeelper[i].sock;
         }
      }
      for(i=0;i<MAX_SOCK_COUNT;i++)
      {
         if(sockHeelper[i].sock==0)
         {
            sockHeelperInUse++;
            sockHeelper[i].iThId=iThId;
            sockHeelper[i].sock=socket(AF_INET,SOCK_DGRAM,0);
            return sockHeelper[i].sock;
         }
      }


      return 0;
   }
   //Rth

};
#endif //USE_STD_SOCKETS
#else //os
///---------
//#pragma message("os ! symb")
#include  <ctype.h>

#if  defined(_WIN32_WCE) ||  defined(_WIN32)
#ifndef INADDR_ANY
   #include <winsock2.h>
#endif
   #define socklen_t int

#define CLOSE_TH  CloseHandle
#else

   #define SOCKET int
   #define closesocket close
   #define Sleep(U) usleep((U)*1000)
   #define SetThreadPriority  //
   #define FALSE 0
   #define SD_BOTH SHUT_RDWR
   #define BOOL int
   #define CLOSE_TH  pthread_detach

   #include <getopt.h>
   #include <unistd.h>
   #include <sys/stat.h>

#ifndef __APPLE__

   #include <net/if.h>
   #include <net/if_arp.h>
#endif
   #include <sys/ioctl.h>

   #include <errno.h>
   #include <sys/types.h>
   #include <netdb.h>
   #include <sys/socket.h>
   #include <arpa/inet.h>
   #include <string.h>
   
#endif
class CTSockCB{
};

class CTSock: public CTSockBase{
   int iErrCnt;
   
   int iRecreateIfNeededNow;//helps to remove a false-positive when checking a network
   
   inline int sendCloseLocal()
   {
      if(!sock){Sleep(15);return 0;}
      struct sockaddr_in sa;
      memset(&sa,0,sizeof(sa));
      sa.sin_addr.s_addr=addr.ip;
      if(!sa.sin_addr.s_addr)sa.sin_addr.s_addr=0x0100007f;
      sa.sin_port=(unsigned short)addr.getPortNF();
      sa.sin_family = AF_INET;
      if(!sa.sin_port)return 0;

      //printf("[rtp-send=%d %d]",iLen,address->ip);
      int r=sendto(sock,"12345",5,0,(struct sockaddr *)&sa,  sizeof(sa));
      
      return r;
   }

public:
   CTSock(CTSockCB &):CTSockBase()
   {
      sock=0;
      iIsBinded=0;
      iNeedClose=0;
      iSuspended=0;
      iErrCnt=0;
      iRecreateIfNeededNow=0;
   }

   ~CTSock()
   {
      closeSocket();
   }
private:
   static char *removePort(char *dst, int iSizeOfDest, const char *pin, int iLen=0){
      
      iSizeOfDest--;
      
      if(iLen==0)iLen=strlen(pin);
      if(iLen+1>=iSizeOfDest)return NULL;
      
      strncpy(dst,pin,iLen);
      dst[iLen]=0;
      
      for(int i=0;i<iLen;i++)if(dst[i]==':'){dst[i]=0;break;}
      
      return dst;
   }
public:
   //for (i = 1; hp->h_addr_list[i] != NULL; i++)
   
   static int getHostByName(const char *psz,int iLen=0, int *ipList = NULL, int iMaxIPCnt=0)
   {
      int ip=0;
      char buf[256];
      struct hostent *h;
      
      psz = removePort(buf, sizeof(buf), psz, iLen);
      if(!psz)return 0;
      
      h =gethostbyname(psz);

      if(h)
      {
         memcpy(&ip,h->h_addr_list[0],sizeof(int));
         if(ipList){
            ipList[0]=0;
            
            struct in_addr **al;
            al = (struct in_addr **)h->h_addr_list;
            
            for(int i=0; i+1<iMaxIPCnt; i++){
               
               if(al[i] == NULL)break;
               
               memcpy(ipList,al[i],sizeof(int));
               printf("[ip-n%d=%x]",i,ipList[0]);
               ipList++;
               ipList[0]=0;
            }
         }
      }
      else if(ipList)ipList[0]=0;
      return ip;
   }
   
   static int getLocalIp()
   {
      int ip=0;
#if  defined(_WIN32_WCE) ||  defined(_WIN32)
      struct hostent *h =gethostbyname("");
      if(h)
      {
         memcpy(&ip,h->h_addr_list[0],sizeof(int));
         if(((ip&0xff)==0 || (ip&0xff)==169)  && h->h_addr_list[1])
         {
            memcpy(&ip,h->h_addr_list[1],sizeof(int));
         }
      }
#else
#ifndef __APPLE__
      struct ifreq ifr;
      u_char *addr;
      int iSock=socket(AF_INET,SOCK_DGRAM,0);
      ifr.ifr_addr.sa_family = AF_INET;

      memset (&ifr, 0, sizeof (struct ifreq));

      strcpy (ifr.ifr_name, "eth0");
      ioctl(iSock, SIOCGIFADDR, &ifr);
   
      addr=(u_char*)&(((struct sockaddr_in * )&ifr.ifr_addr)->sin_addr);
      //printf("eth %s, addr %d.%d.%d.%d\n", ifr.ifr_name,addr[0],addr[1],addr[2],addr[3]);
      ip=*(int*)addr;
      close(iSock);
#endif
#endif
      return ip;
   }
   int createSock(ADDR *addrToBind,BOOL toAny)
   {
      if(createSock())
         return Bind(addrToBind,toAny);
      return -1;
   }
   int setNewPort(int port)
   {
      iSuspended=1;
      closeSocket();
      sock=0;
      Bind(port,1);
      iSuspended=0;
      return 0;
   }

   void reCreate()
   {
      createSock(&addr,1);
   }
   SOCKET createSock()
   {
   //   iIsClosed=0;
      if(sock)closeSocket();
      iIsBinded=0;
      iNeedClose=0;
      sock=socket(AF_INET,SOCK_DGRAM,0);
      // SO_NOSIGPIPE vajag arii udp priesh ios
#ifdef __APPLE__
      int set=1;setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, (void *)&set, sizeof(int));
#endif
      iErrCnt=0;
      return sock;
   }


   int closeSocket()
   {
      if(!sock)return 0;

      iNeedClose=1;
      if(iIsBinded){
         iIsBinded=0;
         sendCloseLocal();
         Sleep(20);
      }
#ifndef SD_BOTH
#define SD_BOTH 2
#endif
      shutdown(sock,SD_BOTH);
      int ret=closesocket(sock);
      sock=0;
     // iIsClosed=1;

      return ret;
   }
   
   void recreateIfNeededNow(){
      iRecreateIfNeededNow=2;
      
   }
   
   inline int sendTo(const char *buf, int iLen, ADDR *address)
   {
      if(iSuspended || !sock || iNeedClose){Sleep(20);return 0;}
      struct sockaddr_in sa;
      memset(&sa,0,sizeof(sa));
      sa.sin_addr.s_addr=address->ip;
      if(!sa.sin_addr.s_addr)sa.sin_addr.s_addr=0x0100007f;
      sa.sin_port=(unsigned short)address->getPortNF();
      sa.sin_family = AF_INET;
      if(!sa.sin_port)return 0;
      iBytesSent+=iLen;

      int r = sendto(sock,buf,iLen,0,(struct sockaddr *)&sa,  sizeof(sa));
      if(r>0){
         iRecreateIfNeededNow=0;
      }
     // ENETDOWN
      if(r<0 && (isalpha(buf[0]) || iRecreateIfNeededNow)){//debug only sip
         printf("[ERR: send udp failed err=%d res=%d]",errno,r);
#ifndef _WIN32         
         if((iRecreateIfNeededNow || errno==EPIPE) && !iSuspended){
            reStartSock();
            r = sendto(sock,buf,iLen,0,(struct sockaddr *)&sa,  sizeof(sa));
            if(r<0){printf("[resend failed]");}
         }
#endif
         //EPIPE
         //errno==ENETDOWN
         
      }
      
      //char b[64];  printf("[send udp   a=%s ret=%d l=%d]\n", address->toStr(b,1),r,iLen);
      
      return r;
   }
   inline int recvFrom(char *buf, int iLen, ADDR *address)
   {
      struct sockaddr_in sa;
      sa.sin_port=0;
      int recSize = sizeof(sa);
      if(iNeedClose)
      {
         Sleep(1);
         return -1;
      }
      int ret;
      do{
         int iIsBack=0;
#ifdef __APPLE__
         int isInBackGround();
         iIsBack = isInBackGround();
#endif
         if(iErrCnt>100 && iIsBack && 1 && !iRecreateIfNeededNow){//ios kills app if it read_broken_socket in background
            ret=-2;
            Sleep(10);
         }
         else{
            
            ret=recvfrom(sock,buf,iLen,0,(struct sockaddr *)&sa, (socklen_t*)&recSize);
            if(ret>0){
               iRecreateIfNeededNow = 0;
            }
            if(iNeedClose)return -1;
         }
         
     //    printf("[rtp=%d]",ret);   

         if(ret>=0)
         {
            
            iErrCnt=0;
            address->ip=sa.sin_addr.s_addr;
            address->setPortNF(sa.sin_port);
          //--  char b[64];  printf("[rec udp   a=%s  l=%d]\n", address->toStr(b,1),ret);
            //TODO listener list
            if(onRecv(buf, ret,address)!=-1)
            {
               if(iNeedClose)return -1;
               recSize = sizeof(sa);
               return 0;
            }
         }
         else //if(0)
         {
            int s=iErrCnt*10;
            if(s>80)s=80;
            Sleep(s);//TODO if in background sleep longer
            iErrCnt++;
            address->clear();
            if((iErrCnt&15)==15)
               printf("[--port=%d lport=%d ip=%x iErrCnt=%d ret=%d iIsBack=%d errno=%d--]",
                      htons(sa.sin_port),addr.getPort(),sa.sin_addr.s_addr,iErrCnt,ret,iIsBack,errno);
#ifdef __APPLE__
            int isTmpWorkingInBackGround();

            int iBi=iIsBinded; //ETIMEDOUT
            if(iRecreateIfNeededNow || (iBi && !iSuspended && iErrCnt>10 && (!isInBackGround()||(iErrCnt>35 && isTmpWorkingInBackGround())))){
               iErrCnt=0;
               
               reStartSock();
            }
            else{
               if((iErrCnt&3)==3)Sleep(200);
            }
#endif
         }
      }while(0);
      return ret;
   }
   inline int Bind(int port, BOOL toAny)
   {
      ADDR a;
      a.setPort(port);
      return Bind(&a,toAny);
   }
   int Bind(ADDR *addrToBind, BOOL toAny)
   {
      printf("[bind1]");
      struct sockaddr_in sa;
      int iMaxTrys=200;
      if(sock==0) createSock();
      if(sock==0)return -1;
      printf("[bind2]");
      


      
      do{
         memset((char *) &sa,0,sizeof(struct sockaddr_in));
         sa.sin_family = AF_INET;

         if(addrToBind->ip && addrToBind->ip != htonl(INADDR_LOOPBACK))
            sa.sin_addr.s_addr = addrToBind->ip;
         else
            sa.sin_addr.s_addr =htonl(INADDR_ANY);

         sa.sin_port = (unsigned short)addrToBind->getPortNF();;

         if(bind(sock, (struct sockaddr *) &sa, sizeof(sa)) >= 0)
         {
//            DEBUG_T(0,"bind ok");
            printf("[bind-ok %d tr=%d]",addrToBind->getPort(),iMaxTrys);
            iIsBinded=1;
            addr=*addrToBind;
            iNeedClose=0;
            break;
         }
         iMaxTrys--;
         addrToBind->setPort(addrToBind->getPort()+2);

      }while(toAny && iMaxTrys>0);
/*
      int iOptVal = 0xb8;
#ifdef _WIN32
#include <ws2tcpip.h>
#endif
      if (setsockopt (sock, 
                     IPPROTO_IP, 
                     IP_TOS, 
                     (char  *)&iOptVal , 
                     sizeof (int)) == -1)
      {
        puts("setsockopt failed for receiver.");
      }
      */
  


      /*
      {
     // struct sockaddr_in sa;
      int ui=sizeof(sa);
      getsockname(sock,(struct sockaddr *) &sa,&ui);
      }
      */

      return iIsBinded?0:-1;
   }
   SOCKET sock;
   ADDR addr;
   int iIsBinded;
private:
   int iNeedClose;
   int iSuspended;
   
   void reStartSock(){
      if(iSuspended)return;
      iSuspended=1;
      //TODO setFlag sock was recreted
      puts("recreate");
      Sleep(20);
      createSock();
      Sleep(20);
      
      printf("[rebind()=%d]", Bind(&addr,1));
      iSuspended=0;
      if(iRecreateIfNeededNow>0)iRecreateIfNeededNow--;
   }

};
inline int hasNetworkConnect(unsigned int ip){
   
   return (htonl(INADDR_LOOPBACK)!=ip) && ip;
}

#endif //os
#endif //_C_TIVI_SOCK
