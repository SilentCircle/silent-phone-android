/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2016, Silent Circle, LLC.  All rights reserved.

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

#ifndef _C_T_TCP_H
#define _C_T_TCP_H
#include "../os/CTiViSock.h"
#ifdef _WIN32
#define snprintf _snprintf
#endif
#ifdef __SYMBIAN32__
void waitForRequestMSec(TRequestStatus *st, int iMSec);
class CTSockTcp: public CTSockBase{
   CTSockCB & cb;
   int iConnected;
   RSocket rSock;
   int iOpened;
   int iListening;
public:
   ~CTSockTcp(){closeSocket();}
    CTSockTcp(CTSockCB & cb):cb(cb)
    {
       iListening=0;
       iOpened=0;
       iConnected=0;
    }
    int createSock(ADDR *addrToBind=NULL, int toAny=1)
    {
  //     if(addrToBind==NULL)
       iOpened=rSock.Open(cb.iSocketServ, KAfInet, KSockStream, KProtocolInetTcp,cb.rConn)==KErrNone;
       return iOpened?0:-1;
    //   return 0;
    }
    void shutDown()
    {
       if(!iConnected)return;
         TRequestStatus st(KErrNone);
         rSock.Shutdown(RSocket::EImmediate,st);
         User::WaitForRequest(st);
         iConnected=0;       
    }
   int closeSocket()
   {
     
         
      if(iConnected)
      {

         shutDown();
      }
       if(!iOpened)return -1;
      iOpened=0;
      rSock.Close();
      iConnected=0;
      return 0;
   }    
    //bind
   int isConected(){return iConnected;}
   int _connect(ADDR *address)
   {
      TRequestStatus st;
      unsigned int i=address->ip;
      SWAP_INT(i);
      TInetAddr a(i,address->getPort());       
      rSock.Connect(a,st);
      waitForRequestMSec(&st,10000);
      iConnected=(st==KErrNone);

      return iConnected?0:-1;
   }
    int _send(char *buf, int iLen)
    {
       if(!iConnected)return -1;
      TRequestStatus st(KErrNone);
      TPtrC8 s((unsigned char*)buf,iLen);  
       rSock.Send(s,0,st);
       User::WaitForRequest(st);
       return st==KErrNone?iLen:-1;
    }
    int _recv(char *buf, int iMaxLen, int iSecondsToWait=-1)
    {
        if(!iConnected)return -1;
        int rec,iBytesRec=0;
        int iReceived=0;
      TRequestStatus st(KErrNone);
      while(1)
      {
         if(iMaxLen<=iBytesRec)
         {
            break;
         }
         TPtr8 s((unsigned char*)buf+iBytesRec,0,iMaxLen-iBytesRec);  
          rSock.Recv(s,0,st);

          if(iSecondsToWait==-1)
            User::WaitForRequest(st);
          else
             waitForRequestMSec(&st,iSecondsToWait*1000);
          rec=s.Length();
          if(rec>=0 && iReceived==0)iReceived=1;
          
          if(rec<=0)
          {
           //  if(rec==0)iBytesRec+=rec;
             break;
          }
           iBytesRec+=rec;
      }
       

          
      return iReceived?iBytesRec:-1;//st==KErrNone?iBytesRec:-1;
    } 
        
};
#else
#ifndef _WIN32
#include <sys/time.h>
#define TIMEVAL struct timeval 
#include <fcntl.h>
//#define ioctlsocket ioctl

#endif

static int netBlock( int fd )
{
#if defined(_WIN32) || defined(_WIN32_WCE)
   unsigned long n = 0;
   return( ioctlsocket( fd, FIONBIO, &n ) );
#else
   return( fcntl( fd, F_SETFL, fcntl( fd, F_GETFL ) & ~O_NONBLOCK ) );
#endif
}

static int netNonBlock( int fd )
{
#if defined(_WIN32) || defined(_WIN32_WCE)
   unsigned long n = 1;
   return( ioctlsocket( fd, FIONBIO, &n ) );
#else
   return( fcntl( fd, F_SETFL, fcntl( fd, F_GETFL ) | O_NONBLOCK ) );
#endif
}
	

class CTSockTcp: public CTSockBase{

   ADDR addrConnected;
   int iConnected;
   int iSending;
   void *p_apple_backgr;
   int iPrevRec;
   int iNeedCallCloseSocket;
public:
   int iIsRTPSock;
   ADDR &getAddrConnected(){return addrConnected;}
   CTSockTcp(CTSockCB &):CTSockBase()
   {
      iIsRTPSock=0;
      iNeedCallCloseSocket=0;
      iPrevRec=-100;
      p_apple_backgr=NULL;
      sock=0;
      iIsBinded=0;
      iNeedClose=0;
      iConnected=0;
      iSending=0;
   }
   CTSockTcp(CTSockTcp *s):CTSockBase()
   {
      sock=s->sock;
      iIsBinded=s->iIsBinded;;
      addr=s->addr;
      iNeedClose=0;
      iConnected=1;
   }
   CTSockTcp(CTSockTcp *s, SOCKET so):CTSockBase()
   {
      sock=so;
      iIsBinded=s->iIsBinded;
      addr=s->addr;
      iNeedClose=0;
      iConnected=1;
   }

   ~CTSockTcp()
   {
      closeSocket();
   }
   int createSock()
   {
      if(sock && iNeedClose==0)return 0;
      iNeedClose=0;
      sock=socket(AF_INET,SOCK_STREAM,0);
      iNeedCallCloseSocket=1;
   //   sock=socket(AF_INET,SOCK_DGRAM,0);
      //--on=1;
      //--setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));
#ifndef _WIN32
      int on;
      on=1;
      setsockopt(sock, SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on));
      //TODO fix TLS
#ifndef __linux__
      int set = 1;
      setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, (void *)&set, sizeof(int));
#endif
#endif
      return (int)(sock);
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




   int closeSocket()
   {
      if(iNeedClose){
         if(iNeedCallCloseSocket)closesocket(sock);
         iNeedCallCloseSocket=0;
         return 0;
        }
  //    printf("[close sock]");
      iNeedClose=1;
      iConnected=0;
      iIsBinded=0;
      addrConnected.clear();
      shutdown(sock,SD_BOTH);
#ifdef __APPLE__
      if(!iIsRTPSock){
         void relTcpBGSock (void *prt) ;
         if(p_apple_backgr)relTcpBGSock(p_apple_backgr);
         p_apple_backgr=NULL;
      }
#endif
     
      closesocket(sock);
      iNeedCallCloseSocket=0;
      sock=0;
      return 0;
   }
   int isConected(){return iConnected;}
   
   int _connect(ADDR *address, int iTimeOut)
   {
      //     puts("a1");
      struct sockaddr_in sa;
      memset(&sa,0,sizeof(sa));
      //    puts("a2");
      sa.sin_addr.s_addr=address->ip;
      sa.sin_port=(unsigned short)address->getPortNF();
      sa.sin_family = AF_INET;
      
      TIMEVAL Timeout;
      Timeout.tv_sec = iTimeOut;
      Timeout.tv_usec = 0;
      
      //set the socket in non-blocking
     // unsigned long iMode = 1;
      //#include <sys/ioctl.h>
      addrConnected=*address;
      iConnected=0;

      int iResult = netNonBlock(sock);//ioctlsocket(sock, FIONBIO, &iMode);
      if (iResult)
      {	
         printf("ioctlsocket failed with error: %d\n", iResult);
      }
      int r= connect(sock,(const struct sockaddr *)&sa,sizeof(struct sockaddr_in));
      
      iResult = netBlock(sock);//
      if (iResult)
      {	
         printf("ioctlsocket failed with error: %d\n", iResult);
      } 

      // restart the socket mode
     // iMode = 0;
      //iResult = ioctlsocket(sock, FIONBIO, &iMode);

   //   printf("connect() =%d\n",r);
      if(r==0)iConnected=1;
      else {//!r){
      
         /*
         fd_set Write, Err;
         FD_ZERO(&Write);
         FD_ZERO(&Err);
         FD_SET(sock, &Write);
         FD_SET(sock, &Err);
         
         // check if the socket is ready
         r=select(0,NULL,&Write,&Err,&Timeout);		
        printf("select=%d\n",r);
         if(FD_ISSET(sock, &Write)) 
         {	
            iConnected=1;//(FD_ISSET(sock, &Err));
         }
         else iConnected=0;
          */
          fd_set fd_r, fd_w;
         FD_ZERO(&fd_r);
         FD_ZERO(&fd_w);
         FD_SET(sock, &fd_r);
         FD_SET(sock, &fd_w);
         
         /*  timeout durring connect() ??  */
         select(sock+1, &fd_r, &fd_w, NULL, &Timeout);
         if(FD_ISSET(sock, &fd_w))
         {
            printf("Alive\n");
             iConnected=1;
         } else {
            printf("Conection timeout\n");
             iConnected=0;
         }
      }
      /*
      if(iConnected){
        r= connect(sock,(const struct sockaddr *)&sa,sizeof(struct sockaddr_in));
        iConnected=!r;
      }
       */
      printf("connected=%d\n",iConnected);;
      if(!iConnected){addrConnected.clear();}
      
#ifdef __APPLE__
      if(iConnected && !iIsRTPSock){
         void relTcpBGSock (void *prt) ;
         if(p_apple_backgr)relTcpBGSock(p_apple_backgr);
         p_apple_backgr=NULL;
         void *prepareTcpSocketForBg (int s) ;
         p_apple_backgr=prepareTcpSocketForBg(sock);
      }
#endif
     
      return r;
   }  
   
   int _connect(ADDR *address)
   {
     //neuzseto atpakalj netblock
      if(!iIsRTPSock)return _connect(address,20);
 //     puts("a1");
      struct sockaddr_in sa;
      memset(&sa,0,sizeof(sa));
  //    puts("a2");
      sa.sin_addr.s_addr=address->ip;
      sa.sin_port=(unsigned short)address->getPortNF();
      sa.sin_family = AF_INET;
 //     puts("a3");
      
      addrConnected=*address;
      int r=connect(sock,(struct sockaddr *)&sa,  sizeof(sa));
      iConnected=r==0;
      if(!iConnected){addrConnected.clear();}
      return r;
   }
   inline int _listen(int iMax)
   {
      return listen(sock,iMax);
   }
   inline int _accept(ADDR *a)
   {
      struct sockaddr_in sa;
      int recSize = sizeof(sa);
      SOCKET s=accept(sock,(struct sockaddr *)&sa,(socklen_t*)&recSize);
      if(s>0)
      {
         a->setPortNF(sa.sin_port);
         a->ip=sa.sin_addr.s_addr;
      }
      return s;
   }
   
   static int _net_is_blocking( void )
   {
#if defined(_WIN32) || defined(_WIN32_WCE)
      return( WSAGetLastError() == WSAEWOULDBLOCK );
#else
      switch( errno )
      {
#if defined EAGAIN
         case EAGAIN:
#endif
#if defined EWOULDBLOCK && EWOULDBLOCK != EAGAIN
         case EWOULDBLOCK:
#endif
            return( 1 );
      }
      return( 0 );
#endif
   }
      
   inline int _send(const char *buf, int iLen)
   {
      /*
      while(iSending && !iNeedClose && iConnected){
         Sleep(15);
      }
       */
      if(iNeedClose || !iConnected)return -1;
     // iSending=1;
      int r=send(sock,buf,iLen,0);
      
      if( r < 0 )
      {
         if( _net_is_blocking() != 0 )
            return 0;//( POLARSSL_ERR_NET_WANT_WRITE );
         
#if defined(_WIN32) || defined(_WIN32_WCE)
         if( WSAGetLastError() == WSAECONNRESET )
            return r;//( POLARSSL_ERR_NET_CONN_RESET );
#else
         if( errno == EPIPE || errno == ECONNRESET )
            return r;//( POLARSSL_ERR_NET_CONN_RESET );
         
         if( errno == EINTR )
            return 0;//( POLARSSL_ERR_NET_WANT_WRITE );//TODO loop ??
#endif
         
      }
          // iSending=0;
      return r;
   }
   inline int _recv(char *buf, int iLen, int iSecondsToWait=-1)
   {
      //struct sockaddr_in sa;
      //int recSize = sizeof(sa);
      if(iNeedClose)
      {
         Sleep(1);
         return -1;
      }
      int ret;
      while(1){

         ret=recv(sock,buf,iLen,0);
         if(iNeedClose)return -1;
         /*
          if( errno == EPIPE || errno == ECONNRESET )addrConnected.clear();
          
          */
         //??????????
         if(p_apple_backgr && ret==-1){
            Sleep(30);
            if(iNeedClose)return -1;
           //-- if( errno == EPIPE || errno == ECONNRESET)return -1;
            ret=0;//TODO return app_back_tryAgain;
            //apple errno ==ETIMEDOUT
           // printf("[apple recv %d %d]\n",errno,EINTR);
            //EINTR
            continue;
         }
         if(ret==0 && iPrevRec==0){
            Sleep(30);
         }
         iPrevRec=ret;

         if(ret>=0)
         {
            if(onRecv(buf, ret,&addrConnected)!=-1)
            {
               if(iNeedClose)return -1;
    //           recSize = sizeof(sa);
               continue;
            }
         }
         else
         {
           // address->clear();
         }
         break;
      };
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
      struct sockaddr_in sa;
      int iMaxTrys=200;
      if(sock==0) createSock();
      if(sock==0)return -1;



      
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
            iIsBinded=1;
            addr=*addrToBind;
            iNeedClose=0;
            break;
         }
         iMaxTrys--;
         addrToBind->setPort(addrToBind->getPort()+2);

      }while(toAny && iMaxTrys>0);


      return iIsBinded?0:-1;
   }
   SOCKET sock;
   ADDR addr;
   int iIsBinded;
private:
   int iNeedClose;

};

//template<int T_MAX_BUF_SIZE>
#endif
void tivi_log(const char* format, ...);

const char *getUserAgent();

#ifndef __SYMBIAN32__
template<class T>
class CTTHttp{


   CTSockCB cbx;
   T *sock;
   char *bufRet;//[T_MAX_BUF_SIZE+1];
   int iBytesReceived;
   int iCreated,iMaxLen;
   char szPrefLang[128];
public:
typedef struct{
   CTTHttp<T> *h;
   int (*respFnc)(void *,int);
   void *ptr;
   int iMaxSeconds;
}HTTP_W_TH;

   T* getSock(){return sock;}
   CTTHttp(char *pBufRet, int iLen):bufRet(pBufRet),iMaxLen(iLen){pMeth=NULL;pContType=NULL;phdrAdd=NULL;sock=NULL;reset();iPrevIp=0;szPrevAddr[0]=0;iBytesReceived=0;iCreated=0;szPrefLang[0]=0;};
   //CTTHttp():sock(cbx){iBytesReceived=0;iCreated=0;};
  // int create(){if(iCreated)return 0;sock.createSock();iCreated=1;return 0;}
   char szPrevAddr[256];
   int iPrevIp;
   int iHttpRespID;
   int iOnResp;
   void reset(){
      iHttpRespID=0;
      iOnResp=0;
   }
   void setLang(const char *p){
      if(!p){
         szPrefLang[0]=0;
         return;
      }
      snprintf(szPrefLang, sizeof(szPrefLang), "Accept-Language: %s\r\n",p);
   }

   const char *pMeth;
   const char *pCont;
   int iContLen;
   const char *pContType;
   const char *phdrAdd;
   int getUrlRep(char *pUrl, char *pAddr){
      return getUrl(pUrl,pAddr,pMeth,pCont,iContLen,pContType,phdrAdd);
   }
   int getUrl(const char *pUrl, const char *pAddr, const char *pMethod="GET", const char *pContent=NULL, int iContentLen=0, const char *pContentType="",const char *hdrAdd="")
   {
      phdrAdd=hdrAdd;
      pMeth=pMethod;
      pCont=pContent;
      pContType=pContentType;
      iContLen=iContentLen;

      if(sock)delete sock;
      sock=new T(cbx);
      reset();
      int ret;
      char bufGet[1024];

      
      ADDR a=pAddr;
      if(a.ip==0){
         if(strcmp(szPrevAddr,a.bufAddr)==0)a.ip=iPrevIp;
         else {
            
            int i=0;
            for(;a.bufAddr[i];i++){
               if(a.bufAddr[i]==':')break;
            }
            a.ip=CTSock::getHostByName(a.bufAddr,a.bufAddr[i]==':'?i:0);
         }
         if(a.ip==0)return -1;
         strcpy(szPrevAddr,a.bufAddr);
         iPrevIp=a.ip;
      }
      if (szPrefLang[0] == 0) {
          snprintf(szPrefLang, sizeof(szPrefLang), "Accept-Language: en\r\n");
      }
      if(a.getPort()==0)a.setPort(80);
      iBytesReceived=0;
      ret=sock->createSock();
      ret=sock->_connect(&a);
      int l=0;
      if(pUrl && pUrl[0]=='/')pUrl++;
      if(!iContentLen)
      {
         l=snprintf(bufGet,sizeof(bufGet),
            "%s /%s HTTP/1.0\r\n"
            "Host: %s\r\n"
            "%s"
            "%s"//lang
            "%s\r\n"
            "Connection: close\r\n"
            "\r\n",pMethod,pUrl,pAddr,hdrAdd,szPrefLang,getUserAgent());
      }
      else
      {
         l=snprintf(bufGet,sizeof(bufGet),
            "%s /%s HTTP/1.0\r\n"
            "Host: %s\r\n"
            "%s"
            "%s"//lang
            "%s\r\n"
            "Content-Type: %s\r\n"
            "Content-Length: %d\r\n"
            "Connection: close\r\n"
            "\r\n",pMethod,pUrl,pAddr,hdrAdd,szPrefLang,getUserAgent(),pContentType,iContentLen);
      }
   //   puts(bufGet);
      ret=sock->_send(&bufGet[0],l);
      if(iContentLen || (pContent && pContent[0]))
      {
         ret=sock->_send(pContent,iContentLen?iContentLen:strlen(pContent));
      }
      while(1)
      {
         l=sock->_recv(&bufRet[iBytesReceived],iMaxLen-iBytesReceived);
         if(l<=0)break;
         iBytesReceived+=l;
      }
      //onResp(l<0);
      if(l<=0)iHttpRespID=-1;else iHttpRespID=200;
      sock->closeSocket();
      return iBytesReceived;
   }
   T *createSock(){
      if(sock)delete sock;
      sock=new T(cbx);
      return sock;
   }
   int getUrl(T *s, const char *pUrl, const char *pAddr, const char *pMethod="GET", const char *pContent=NULL, int iContentLen=0, const char *pContentType="",const char *hdrAdd="")
   {
      phdrAdd=hdrAdd;
      pMeth=pMethod;
      pCont=pContent;
      pContType=pContentType;
      iContLen=iContentLen;

      reset();
      int ret;
      char bufGet[1024];
      sock=s;

      
      ADDR a=pAddr;
      if(a.ip==0){
         if(strcmp(szPrevAddr,a.bufAddr)==0)a.ip=iPrevIp;
         else {
            
            int i=0;
            for(;a.bufAddr[i];i++){
               if(a.bufAddr[i]==':')break;
            }
            a.ip=CTSock::getHostByName(a.bufAddr,a.bufAddr[i]==':'?i:0);
         }
         if(a.ip==0){
            iHttpRespID=-1;
            iBytesReceived=0;
            return -1;
         }
         strcpy(szPrevAddr,a.bufAddr);
         iPrevIp=a.ip;
      }
      if (szPrefLang[0] == 0) {
          snprintf(szPrefLang, sizeof(szPrefLang), "Accept-Language: en\r\n");
      }
      if(a.getPort()==0)a.setPort(80);
      iBytesReceived=0;
      ret=s->createSock();
      ret=s->_connect(&a);
      int l=0;
      if(pUrl && pUrl[0]=='/')pUrl++;
      if(!iContentLen)
      {
         l=snprintf(bufGet,sizeof(bufGet),
            "%s /%s HTTP/1.0\r\n"
            "Host: %s\r\n"
            "%s"//lang
            "%s"
            "%s\r\n"
            "Connection: close\r\n"
            "\r\n",pMethod,pUrl,pAddr,szPrefLang, hdrAdd,getUserAgent());
      }
      else
      {
         l=snprintf(bufGet,sizeof(bufGet),
            "%s /%s HTTP/1.0\r\n"
            "Host: %s\r\n"
            "%s"//lang
            "%s"
            "%s\r\n"
            "Content-Type: %s\r\n"
            "Content-Length: %d\r\n"
            "Connection: close\r\n"
            "\r\n",pMethod,pUrl,pAddr,szPrefLang,hdrAdd,getUserAgent(),pContentType,iContentLen);
      }
     // puts(bufGet);
      ret=s->_send(&bufGet[0],l);
      if(iContentLen || (pContent && pContent[0]))
      {
       // puts(pContent);
         ret=s->_send(pContent,iContentLen?iContentLen:strlen(pContent));
      }
      while(1)
      {
         l=s->_recv(&bufRet[iBytesReceived],iMaxLen-iBytesReceived);
         if(l<=0)break;
         iBytesReceived+=l;
      }
      //onResp(l<0);
      if(l<=0)iHttpRespID=-1;else iHttpRespID=200;
      s->closeSocket();
      return iBytesReceived;
   }

   static int thFncx(void *p){

      HTTP_W_TH *h=(HTTP_W_TH*)p;
      int iMaxMS=h->iMaxSeconds*1000;
      tivi_log("wait %d",h->iMaxSeconds);
      
      while(h->h->iHttpRespID==0 && iMaxMS>=0 && h->h->iOnResp==0){
         iMaxMS-=20;
         Sleep(20);
      }
      if(iMaxMS<0 && h->h->iHttpRespID==0 && h->h->iOnResp==0){
         tivi_log("wait resp timeout");
         h->h->cancel();
         h->h->iHttpRespID=-2;
      }
      tivi_log("wait resp %dms %d",h->iMaxSeconds*1000-iMaxMS,h->h->iHttpRespID);
      if(h->ptr && h->respFnc)h->respFnc(h->ptr,1);




      return 0;

   }
   void onResp(int err){
      iOnResp=1;
         wtSaved.respFnc(wtSaved.ptr,err||iHttpRespID!=200);
         wtSaved.ptr=0;
    
   }
   void goOffline(){cancel();}
      HTTP_W_TH wtSaved;
   void waitResp(HTTP_W_TH *wt, int iMaxSeconds=60){//int (*respFnc)(void *,int),void *ptr,int iMaxSeconds=60){


      wt->h=this;
      wt->iMaxSeconds=iMaxSeconds;
      memcpy(&wtSaved,wt,sizeof(wtSaved));

      CTThread *th=new CTThread();
      th->destroyAfterExit();
      //TODO HTTP_W_TH *t=new HTTP_W_TH
      th->create(CTTHttp::thFncx,&wtSaved);
      //iHttpRespID=200;
      //Sleep(200);
      //onResp(0);

   }
   void cancel(){
      if(sock)sock->closeSocket();
   }

   int getHeaderSize()
   {
      int i;
      int iTo=iBytesReceived-3;
      for(i=20;i<iTo;i++)
      {
         if ('\r'==bufRet[i] && strncmp("\n\r\n",&bufRet[i+1],3)==0)
         {
            i+=4;
            break;
         }
      }
      return i;
   }
   char *getContent(int &iLen)
   {
      iLen=0;
      if(iBytesReceived<=0)return NULL;
      int i=getHeaderSize();
      iLen= iBytesReceived-i;
 //     printf("[%.*s]",iLen,&bufRet[i]);
      return &bufRet[i];
   }
   char *getHeaders(int &iLen)
   {
      iLen=0;
      if(iBytesReceived<=0)return NULL;
      iLen=getHeaderSize();
   //   printf("[%.*s]",iLen,&bufRet[0]);
      return &bufRet[0];
   }

   static int splitUrl(char *url, int iLen, char *addrRet, char *urlRet){
      if(iLen<12)return -1;
      int l=0;
      if(strncmp("http://",url,7)==0)l=7;
      else if(strncmp("https://",url,8)==0)l=8;
      else return -2;

      url+=l;
      iLen-=l;

      char *pAddr=addrRet;

      while(iLen>0 && *url>' ' && *url!='/'){
         *addrRet=*url;addrRet++;
         url++;iLen--;
      }
      while(iLen>0 && *url>' '){
         *urlRet=*url;urlRet++;
         url++;iLen--;
      }
      *addrRet=0;
      *urlRet=0;

      if(l==8){//https
         int i;
         for(i=0;pAddr[0];i++){

            if(pAddr[0]==':')break;
            pAddr++;
         }
         if(!pAddr[0])strcat(addrRet,":443");
      }

      return 0;


   }

   static int findToken(char *tok, int iTLen, char *p, int iLen, char *resp){
      int iRespLen=0;
      while(iLen>iTLen){
         if(strncmp(tok,p,iTLen)==0){
            iLen-=iTLen;
            p+=iTLen;
            while(iLen>0 && *p>' '){resp[iRespLen]=*p;iRespLen++;p++;}
            resp[iRespLen]=0;
            return iRespLen;
         }
         p++;
         iLen--;
      }
      return 0;

   }

};
#endif
//http
#endif

//_C_T_TCP_H
