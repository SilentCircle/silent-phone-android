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

#include "../baseclasses/CTBase.h"
#include "CTSipSock.h"
#include "../sipparser/client/sip_utils.inl"

unsigned int getTickCount();
void tmp_log(const char *p);

#define T_UNKNOWN_SOCK_RET (-2)

CTSipSock::~CTSipSock(){

   iExiting=1;
   closeSocket();
   Sleep(20);
   //tcpPrev=tcp;
   if(tls)delete tls;
   if(tcp)delete tcp;
   tcp=NULL;
   tls=NULL;
   addToDelete(-1,NULL);//clean prev sockets
}

void CTSipSock::del_sock(DEL_SOCK *s){
   if(!s)return;
   if(s->iIsTLS && ((CTTLS*)s)->iCallingConnect)return;
      
   s->uiDeleteAT=0;
   puts("del sock");
   if(s->iIsTLS)delete(CTTLS*) s->sock;
   else delete(CTSockTcp*)s->sock;
   s->sock=NULL;
   
}
//iIsTLS = -1 -delete all sock
void CTSipSock::addToDelete(int iIsTLS, void *s){
   unsigned int uiNow=getTickCount();

   int iEmptyCnt=0;
   
   for(int i=0;i<eDelSocketCnt;i++){
      int d=(int)(uiNow-deleteSock[i].uiDeleteAT);//rollover fix
      if(((deleteSock[i].uiDeleteAT && d>0) || iIsTLS==-1) && deleteSock[i].sock) {
         del_sock(&deleteSock[i]);
      }
      if(!deleteSock[i].sock)iEmptyCnt++;
   }
   if(!s || iIsTLS==-1)return;
   
   if(!iEmptyCnt)puts("[err,iEmptyCnt=0]");//TODO next to delete
   
   for(int i=0;i<eDelSocketCnt;i++){
      
      if(!deleteSock[i].uiDeleteAT && !deleteSock[i].sock){
         deleteSock[i].uiDeleteAT=uiNow+10000;
         deleteSock[i].sock=s;
         deleteSock[i].iIsTLS=iIsTLS;
         return;
      }
   }
   if(iIsTLS)delete (CTTLS*)s;
   else delete (CTSockTcp*)s;
}

CTSipSock::CTSipSock(CTSockCB &c):CTSockBase(),udp(c),tcp(NULL),sockCB(c),tls(NULL)
{
   memset(&deleteSock[0],0,sizeof(DEL_SOCK)*eDelSocketCnt);

   errMsgTLS=NULL;
   pRetTLS=NULL;
   iBytesInNextTmpBuf=0;
   iExiting=0;
   iIsSuspended=0;
   iTcpIsSent=0;
   iTlsIsSent=0;
   iIsReceiving=0;
   iPrevUsedType=eUnknown;
   iType=eUnknown;
   tcpPrev=NULL;
   tlsPrev=NULL;
   iSending=0;
   tcp = new CTSockTcp(c);
   tls = new CTTLS(c);
}

void CTSipSock::setSockType(const char *p){
   
   if(strcmp(p,"TLS")==0){setSockType(eTLS);}
   else if(strcmp(p,"TCP")==0){setSockType(eTCP);}
   else if(strcmp(p,"UDP")==0){setSockType(eUDP);}
   else setSockType(eUDP);
   
}

void CTSipSock::setSockType(int iNewType){

   if(iExiting)return ;
   iPrevUsedType=iType;
   int iWasCreatedSock=0;
   if(iPrevUsedType!=iNewType){
      if(iType==eUDP && udp.iIsBinded)iWasCreatedSock=1;else 
      if(iType==eTCP && tcp && tcp->isConected())iWasCreatedSock=1;else 
      if(iType==eTLS && tls && tls->isConected())iWasCreatedSock=1;
      closeSocket();//
      
   }
 
   iType=iNewType;
   if(iWasCreatedSock){
      createSock(&addr,1);
   }
}
int CTSipSock::createSock(ADDR *addrToBind,BOOL toAny){
   if(iExiting)return -1;

   if(iType==eUDP || iType==eTCP || iType==eTLS){
      int r=createSock();
      if(r>=0)r=Bind(addrToBind,toAny);
      return r;
   }

   return T_UNKNOWN_SOCK_RET;
}
int CTSipSock::setNewPort(int port){
   if(iType==eUDP)
      udp.setNewPort(port);
   addr.setPort(port);
   
   return 0;//T_UNKNOWN_SOCK_RET;
}
int CTSipSock::getInfo(const char *key, char *p, int iMax){
   if(iType==eTLS){
      return tls->getInfo(key,p,iMax);
   }
   else if(iType==eTCP){
      strcpy(p,"none, TCP");
      return strlen(p);
   }
   else {
      strcpy(p,"none, UDP");
      return strlen(p);
   }
   return 0;
}

void CTSipSock::reCreate(){
   if(iExiting)return;
   createSock();
}
SOCKET CTSipSock::createSock(){
   return createSockBind(0);
}
SOCKET CTSipSock::createSockBind(int iBind){
   iFlagRecreate=0;
   if(iExiting)return -1;
   if(iType==eUDP){if(iBind)udp.Bind(addr.getPort(),1);return udp.createSock();}
   
   int z;
   for( z=0;z<30 && iIsReceiving;z++)Sleep(15);
   
   if(iType==eTCP){
      tcpPrev=tcp;
      iIsSuspended=1;
      iTcpIsSent=0;
      CTSockTcp *n=new CTSockTcp(sockCB);
      SOCKET r=n->createSock();
      if(iBind)Bind(addr.getPort(),1);
      tcp=n;
      if(tcpPrev){
         tcpPrev->closeSocket();
         Sleep(15);

         addToDelete(0,tcpPrev);
         tcpPrev=NULL;
      }
      iIsSuspended=0;
      iTcpIsSent=0;
      return r;
      //TODO setuptime
   }
   if(iType==eTLS){
      tlsPrev=tls;
      iTlsIsSent=0;
      iIsSuspended=1;
      CTTLS *n=new CTTLS(sockCB);
      SOCKET r=n->createSock();
      if(iBind)Bind(addr.getPort(),1);
      tls=n;
      if(tlsPrev){
         tlsPrev->closeSocket();
         Sleep(15);
         
         addToDelete(1,tlsPrev);
         tlsPrev=NULL;
      }
      iIsSuspended=0;
      iTlsIsSent=0;


      return r;
      //TODO setuptime
   }
   return (SOCKET)T_UNKNOWN_SOCK_RET;
}
const char *pSipCert=
 "-----BEGIN CERTIFICATE-----\r\n"
 "MIIErTCCA5WgAwIBAgIJAL4xsEuuSfJxMA0GCSqGSIb3DQEBBQUAMIGVMQswCQYD\r\n"
 "VQQGEwJVUzERMA8GA1UECBMITWFyeWxhbmQxGDAWBgNVBAcTD05hdGlvbmFsIEhh\r\n"
 "cmJvcjEaMBgGA1UEChMRU2lsZW50IENpcmNsZSBMTEMxLDAqBgNVBAsTI1NpbGVu\r\n"
 "dCBDaXJjbGUgQ2VydGlmaWNhdGUgQXV0aG9yaXR5MQ8wDQYDVQQDEwZUTFMgQ0Ew\r\n"
 "HhcNMTIwODE1MTYzNjAzWhcNMjIwODEzMTYzNjAzWjCBlTELMAkGA1UEBhMCVVMx\r\n"
 "ETAPBgNVBAgTCE1hcnlsYW5kMRgwFgYDVQQHEw9OYXRpb25hbCBIYXJib3IxGjAY\r\n"
 "BgNVBAoTEVNpbGVudCBDaXJjbGUgTExDMSwwKgYDVQQLEyNTaWxlbnQgQ2lyY2xl\r\n"
 "IENlcnRpZmljYXRlIEF1dGhvcml0eTEPMA0GA1UEAxMGVExTIENBMIIBIjANBgkq\r\n"
 "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAm2MXGIfpgg2fCZEGE7wNQ7aRNeDeSDdo\r\n"
 "gVvniMbG0KfVj0S2AQUtpcFoTcxxIA/Gc9Onb3T/37pzbs9WRlgwCs+JwhLaO211\r\n"
 "RXvvC69E7FctqryzJa4bsT267A9bzjF0ROElPY7ISpq77kzRLmRheGAmM9rezavX\r\n"
 "cYr7ek0QpGu3NQv4wyEZaYFkIi2XJC96PstaqAZ3tBlo6EFPzufjrzE1HgRX6V7N\r\n"
 "GzhYjO9VFyCWPweXxQpXrpI86R+Ng9oVwKLF5VINY0QfCxDliGn+YHpOtgPPsRRB\r\n"
 "TSNRvdb5AHCy8+L7wzWlWjEnw/t+cMUUaJ5UCKQlxD5LSRy6FPoWkQIDAQABo4H9\r\n"
 "MIH6MB0GA1UdDgQWBBQduiU7DYffC153cALpjBHtfwmb9jCBygYDVR0jBIHCMIG/\r\n"
 "gBQduiU7DYffC153cALpjBHtfwmb9qGBm6SBmDCBlTELMAkGA1UEBhMCVVMxETAP\r\n"
 "BgNVBAgTCE1hcnlsYW5kMRgwFgYDVQQHEw9OYXRpb25hbCBIYXJib3IxGjAYBgNV\r\n"
 "BAoTEVNpbGVudCBDaXJjbGUgTExDMSwwKgYDVQQLEyNTaWxlbnQgQ2lyY2xlIENl\r\n"
 "cnRpZmljYXRlIEF1dGhvcml0eTEPMA0GA1UEAxMGVExTIENBggkAvjGwS65J8nEw\r\n"
 "DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQUFAAOCAQEAfk9R+lg7oh/58pr2nfCF\r\n"
 "GtBM8Bq45YYTS/DSc+aoiJcH4VyizOclICzImK1rfaVFp7f4wYs6DiQ8xja1f/0A\r\n"
 "4mF/frXrn6Idlw64L+ix9iuknv6aWa0loADQ4ZbK39JDE8lvA4wX1Uz9lNUtOCox\r\n"
 "ZMlQgPjd/OKpAgOgE7qT5yuD2Mr4QYMQYnLcZsXJCYloTns6+WO+2tIZmY4lNYTt\r\n"
 "xr4ai2Xh9JfW87QHwKPFnaugBmF6wvprRaCO3xmGjJ8DGf0by2yeU+tRZNhw5pei\r\n"
 "UNgB80tVgRBGenoa9ROEuAZMOkpXTchSClwLCMQXENFLlGaoHX9/JhpELtTzkDyp\r\n"
 "mg==\r\n"
 "-----END CERTIFICATE-----\r\n";

 
void CTSipSock::checkCert(ADDR *address){
   //TODO T_getSometing(NULL,"sipCertFN","getBySock",this);
   //or T_getSometing(NULL,"sipCertFN","getByServ",&address->bufAddr[0]);
   
#if 1
   short *pathFN=(short*)T_getSometing1(NULL,"sipCertFN");
   
   int iLen=strlen(pSipCert);
   char *p=(char*)pSipCert;
   int iDel=0;
   
   if(pathFN){
      int iLen2;
      char *p2=loadFileW(pathFN,iLen2);
      if(p2 && iLen2>0){p=p2;iLen=iLen2;iDel=1;}
      
   }
   
   if(p){
      
      int showSSLErrorMsg(void *ret, const char *p);
      
      tls->errMsg=&showSSLErrorMsg;
      tls->pRet=this;
      
      if(errMsgTLS)tls->errMsg=errMsgTLS;
      if(pRetTLS)tls->pRet=pRetTLS;
      
      char bufZ[sizeof(address->bufAddr)];
      char *bufA=&address->bufAddr[0];
      int i=0;
      for(;i<sizeof(bufZ)-1;i++){
         if(bufA[i]==':' || !bufA[i]){break;}
         bufZ[i]=bufA[i];
      }
      bufZ[i]=0;
      ADDR ax=bufZ;
      if(!ax.ip)//DONT
         tls->setCert(p,iLen,bufZ);
      
      if(iDel)delete p;
   }
   


   
#endif
}
int CTSipSock::closeSocket(){
   if(iIsSuspended){Sleep(20);return 0;}
   if(iType==eUDP)return udp.closeSocket();
   if(iType==eTCP && tcp){return tcp->closeSocket();}
   if(iType==eTLS && tls){return tls->closeSocket();}
   return T_UNKNOWN_SOCK_RET;
}
#include <stdarg.h>

void tivi_slog(const char* format, ...)
#if defined(_WIN32) || defined(__linux__)  || defined(__APPLE__) 
{

   {
      va_list arg;
      va_start(arg, format);
      vprintf(format,arg);
      printf("\n");
      va_end( arg );
   }
#if  defined(__APPLE__)
   return;
#endif
   
#define T_TLOG_FN "sip_log.txt"
   
   //if sip_log.txt exists - save log
   
   static int iOk=0;
   if(iOk==-1)return;
   if(iOk==0){
      FILE *fx=fopen(T_TLOG_FN,"rb");
      if(!fx){
         iOk=-1;
         return;
      }
      iOk=1;
      fclose(fx);
   }

   FILE *f=fopen(T_TLOG_FN,"a+");
   if(!f){iOk=-1;return;}
   
   va_list arg;
   va_start(arg, format);
   vfprintf(f,format,arg);
   fprintf(f,"\r\n");
   va_end( arg );

   fclose(f);
}
#endif



int CTTCP_TLS_SendQ::addToQuve(const char *buf, int iLen, ADDR *address, int iSockType)
{
   // mq.
 //nedriikst saglabaat sock
   //TODO if new ip remove or new sockt remo prev msg from q
   if(iLen<11)return 0;///iLen<8 gaja
    
   for(int i=0;i<Q_CNT;i++){
      //TODO chekc addr and content if same skip
      AA_SEND_Q *p=&sendQ[i];
      if(!p->iBusy)continue;
      if(p->iSockType!=iSockType || *address!=p->a){
         p->uiTS=0;
         p->iBusy=0;
         continue;
      }
      if(p->iSockType==iSockType && iLen==p->iLen &&  p->a ==*address && memcmp(p->buf,buf,iLen)==0){
         p->uiTS=getTickCount();
         return 0;
      }
   }
   for(int z=0;z<2;z++){
      if(z)iPrevAddedToQuevePos=0;
      for(int i=iPrevAddedToQuevePos;i<Q_CNT;i++){
         //TODO chekc addr and content if same skip
         AA_SEND_Q *p=&sendQ[i];
         
         if(!sendQ[i].iBusy){ 
            //TODO create multi sockets
            p->iSockType=iSockType;
            p->iBusy=1;
            p->uiTS=getTickCount();
            if(iLen>sizeof(p->buf)-1){
               iLen=sizeof(p->buf)-1;
               tmp_log("AQ: send buf is too small");
            }
            
            memcpy(p->buf,buf,iLen);
            memcpy(&p->a,address,sizeof(ADDR));
            p->iLen=iLen;
            p->iBusy=2;
            iPrevAddedToQuevePos=i+1;
            
            return i;
         }
      }
   }
   return -1;
}

//TODO rem prevCall msg from quve
void CTSipSock::emptyQueve(){
   if(iExiting)return ;
   for(int i=0;i<Q_CNT;i++){
      CTTCP_TLS_SendQ::AA_SEND_Q *q=&sq.sendQ[i];
      if(q->iBusy)q->iBusy=0;
   }
   
}

class CTAutoIntUnlock{
   int *iV;
public:
   CTAutoIntUnlock(int *iV):iV(iV){
      *iV=1;
   }
   ~CTAutoIntUnlock(){
      *iV=0;
   }
};



int CTSipSock::sendQuve(){
   
   if(iExiting)return -1;
   
   if(iIsSuspended){Sleep(20);return -1;}
   
   if(iSending){Sleep(5);return 0;}
   
   CTAutoIntUnlock _s(&iSending);

   unsigned int uiTS=getTickCount();
   int c=0,sent=0;;

   int failCount=0;
   
   int i;
   int iCnt=0;
   int pp=sq.iPrevSendPos;
   
   for(int z=0;z<2;z++){
      
      if(z){if(!pp)break;pp=0;}
      for(i=pp;i<Q_CNT;i++){
         iCnt++;
         if(iCnt>Q_CNT){
            //if(!z)puts("sip sock e icnt");
            break;
         }
         
         if(iExiting)break;
         
         CTTCP_TLS_SendQ::AA_SEND_Q *q=&sq.sendQ[i];
         
         sq.iPrevSendPos=i+1;
         
         if(q->iBusy==2){
            
            q->iBusy=3;
            //if binded addr
            int d = (int)(uiTS-q->uiTS);
            if(d>8000){
               //timeout
               if(q->uiTS && q->a.ip)
                  printf("[e-timeout l=%d ofs=%d]\n",q->iLen,d);
               else
                  sent++;//??
            }
            else if(q->iSockType!=this->iType){
               puts("e-socktype");
            }
            else {
               
               printf("[send %s [%.*s] %d]",q->a.bufAddr, 30, q->buf, getTickCount()-q->uiTS);
               int ret=sendToReal(q->buf, q->iLen, &q->a);
               uiTS=getTickCount();
               printf("[inq=%d]\n", uiTS-q->uiTS);
               if(ret<0){
                  failCount++;
                  q->iBusy=0;
                  char d[64];sprintf(d,"sendToReal()=%d t=%dms\n",ret, uiTS-q->uiTS);tmp_log(d);
                  Sleep(30);
               }
               else sent++;
            }
 
            q->iBusy=0;
            c++;
            if(sent>16 || (sent>8 && failCount) || failCount>3 || (failCount && getTickCount()-uiTS>3000))break;
         }
      }
   }

   if(failCount && c) {
      char d[64];sprintf(d,"send quve %d %d %d\n",c,sent,failCount);tmp_log(d);
   }
   return c;
}

int CTSipSock::sendTo(const char *buf, int iLen, ADDR *address){
   
   
   if((iType==eUDP || sq.addToQuve(buf,iLen,address,iType)<0)){
      return sendToReal(buf,iLen,address);
   }
   return iLen;
}

int CTSipSock::sendToReal(const char *buf, int iLen, ADDR *address){
   if(iExiting)return -1;
   if(iIsSuspended){Sleep(20);return -2;}

  if(iType==eUDP){
      return udp.sendTo(buf,iLen,address);
   }
   if(iLen==1)return 1;
   
   CTMutexAutoLock al(testTCP_TLSMutex); 

   if((iFlagRecreate && (iFlagRecreate&4)==0) || (iType==eTLS && tls && (tls->isClosed() || !tls->isConected())) ||
      (iType==eTCP && (!tcp || ((tcp && tcp->getAddrConnected().ip && tcp->getAddrConnected()!=*address))))  ||
      (iType==eTLS && (!tls || ((tls && tls->getAddrConnected().ip && tls->getAddrConnected()!=*address))))
      )
   {
      if(iFlagRecreate)tmp_log("[iFlagRecreate]");
      else if(iType==eTCP){
         if(tcp)printf("[tcp %d=%d,%d=%d]",tcp->getAddrConnected().ip,address->ip,tcp->getAddrConnected().getPort(),address->getPort());
      }
      else if(iType==eTLS){
         if(tls){
            char bufA[32];
            char bufB[32];
            tls->getAddrConnected().toStr(&bufA[0]);
            address->toStr(&bufB[0]);
            
            char d[64];sprintf(d,"[tls conn=%s dst=%s]",bufA,bufB);
            tmp_log(d);
         }
      }

      iFlagRecreate|=4;
      
      iTcpIsSent=0;
      iTlsIsSent=0;
      
    //  closeSocket(); //createSockBind deletes prev one
      Sleep(20);
      

      int r=createSockBind(1);
      void tivi_log1(const char *p, int val);
      tivi_log1("_recr",r + iType*100);
     // printf("[cr=%d]",r);
      Bind(addr.getPort(),1);
   }

   if(iType==eTCP){
   
      if(tcp){
         if(!tcp->isConected()){tcp->_connect(address);}
         if(tcp->isConected()){
            printf("[tcp-connected]");
            
            if(iLen<20){
               iLen=0;//do not send keeplive
               return 0;//tcp->_send("\r\n\r\n",0);//test
            }
            if(!iTcpIsSent){
               iTcpIsSent=1;
               Sleep(60);//wait recv starts listen
            }
            int r=tcp->_send(buf,iLen);
            if(iLen>0)tivi_slog("sent[%d]-tcp\n[%.*s]",r,min(iLen,10),buf);
            if(r<0){
               puts("[tcp recreate]");
               iFlagRecreate|=2;
               iFlagRecreate&=~4;
            }
            return r;
         }
         else return -5;
      }
      else return -4;
   }

   if(iType==eTLS){
      if(tls){
         if(!tls->isConected() && iLen>0){checkCert(address);tmp_log("tls-conn");tls->_connect(address);}
         if(tls->isConected()){
            int r=0;
            if(iLen<20){
               iLen=0;//do not send keeplive
            }
            else {
               if(!iTlsIsSent){
                  iTlsIsSent=1;
                  Sleep(50);//wait recv starts listen
               }
               r=tls->_send(buf,iLen);
               if(iLen>0)tivi_slog("sent[%d]-tls\n[%.*s]",r,min(iLen,10),buf);
               
               if(r<0){
                  tmp_log("f-recr");
                  iFlagRecreate|=2;//ok
                  iFlagRecreate&=~4;
               }
            }
            return r;

         }
         else return -5;
      }
      else return -3;
   }

   return T_UNKNOWN_SOCK_RET;
}


typedef struct{
   
   int (*recv)(void *pSock, char *buf, int len);

   void *sock;
   
}T_SOCK_CB;

static int t_recvTLS(void *pSock, char *buf, int len){
   return ((CTTLS*)pSock)->_recv(buf,len);
}

static int t_recvTCP(void *pSock, char *buf, int len){
   return ((CTSockTcp*)pSock)->_recv(buf,len);
}



static int recFrom2(T_SOCK_CB *cb, char *buf, int iLen, char *tmpBuf, int iTmpSize, int *iBytesInNTB){
   int rec=0;
   int ctx=0;
   int r;
   int sk=0;
   
   int iBytesInNextTmpBuf=*iBytesInNTB;
   
   if(iBytesInNextTmpBuf){

      if(iBytesInNextTmpBuf>iLen){
         rec=iLen;
         memcpy(buf,&tmpBuf[0],rec);
         iBytesInNextTmpBuf-=rec;
         memmove(&tmpBuf[0],&tmpBuf[iLen],iBytesInNextTmpBuf);
         
      }
      else{
         rec=iBytesInNextTmpBuf;iBytesInNextTmpBuf=0;
         memcpy(buf,&tmpBuf[0],rec);
      }
      sk=1;
      r=rec;

   }
   
   do{
      // if(!tcp->iIsBinded)tcp->Bind(&addr,1);
      if(!sk){r=cb->recv(cb->sock, buf+rec,iLen-rec);rec+=r; }
      sk=0;
      if(r<0){*iBytesInNTB=0; return r;}
      if(!r){*iBytesInNTB=0;return 0;};
      
      int isValidSipResp(char *buf, int iLen);
      int isValidSipReq(char *buf, int iLen);;
      
      
      int iFail=1;
      
      {  //finds valid sip start     
         int t=0;
         while(t<rec){
            if(rec-t<SIP_HDR_R_MIN_LEN)break;
            //TODO check [UPPER] [UPPER] [ONE SPACE]
            if(isupper(buf[t]) && (isValidSipResp(&buf[t],rec-t) || isValidSipReq(&buf[t],rec-t))){
               iFail=0;
               break;
            }
            t++;
         }
         if(t==rec){
            *iBytesInNTB=iBytesInNextTmpBuf; 
            return rec;
         }
         if(t){
#if 0
            //removes trash before sip hdr
            rec-=t;
            memmove(buf,&buf[t],rec);
#else       
            //keep trash before sip hdr
            int cc=t;
            int iBI=iBytesInNextTmpBuf;
            iBytesInNextTmpBuf+=(rec-cc);
            memcpy(&tmpBuf[iBI],buf+cc,(rec-cc));
            rec=cc;
            break;
#endif
         }
      }
      
      
      int i_TODO_set_wait_timeout_when_first_part_received;
      
      if(!iFail){
         
         int rr=isFullSipPacket(buf,rec,ctx,0);
         
         if(rr==1)break;
         
         if(rr<0){
            int cc=-rr;
            
            int iBI=iBytesInNextTmpBuf;
            iBytesInNextTmpBuf+=(rec-cc);
            
            memcpy(&tmpBuf[iBI],buf+cc,(rec-cc));
            rec=cc;
            break;
            
         }
      }
      if(rec==iLen)break; //test this

   }while(r>=0);
   *iBytesInNTB=iBytesInNextTmpBuf; 
   
   return rec;
}


int CTSipSock::recvFrom(char *buf, int iLen, ADDR *address){
   if(iExiting)return -1;
   if(iIsSuspended){Sleep(20);return -1;}
   if(iType==eUDP){if(!udp.iIsBinded)udp.Bind(&addr,1);return udp.recvFrom(buf,iLen,address);}
   
   CTAutoIntUnlock a(&iIsReceiving);
   
   if(iType==eTCP){
      int rec=0;
      if(!tcp || !iTcpIsSent || !tcp->isConected()) {
         Sleep(30);
         return 0;
      }

      T_SOCK_CB cbTcp;
      cbTcp.sock=tcp;
      cbTcp.recv=t_recvTCP;
      
      rec=recFrom2(&cbTcp,buf,iLen,&tmpBuf[0],sizeof(tmpBuf),&iBytesInNextTmpBuf);
      if(rec<=0){
         printf("[rec-tcp-err %d]",rec);
         if(rec==0){
            puts("[recFrom2 recreate]");
            iFlagRecreate|=2;
            iFlagRecreate&=~4;
            Sleep(30);
            address->clear();
            return rec;
         }
      }
      


      *address=tcp->getAddrConnected();
      
      return rec;
   }
   if(iType==eTLS){
      int rec=0;
      if(!tls || !iTlsIsSent || !tls->isConected()) {Sleep(20);return 0;}

      T_SOCK_CB cbTcp;
      cbTcp.sock=tls;
      cbTcp.recv=t_recvTLS;
      
      rec=recFrom2(&cbTcp,buf,iLen,&tmpBuf[0],sizeof(tmpBuf),&iBytesInNextTmpBuf);
      if(rec<=0){
         if(tls->peerClosed()==1){
            tmp_log("[tls->peerClosed() recreate]");
            iFlagRecreate|=2;
            iFlagRecreate&=~4;
            return rec;
         }
      }
      else if(rec==0){
      //  puts("[tls rec=0 recreate]");
        //iFlagRecreate=1;
      }

      *address=tls->getAddrConnected();

      return rec;
   }

   return T_UNKNOWN_SOCK_RET;
}

int CTSipSock::Bind(ADDR *addrToBind, BOOL toAny){
   if(iExiting)return -1;
   if(iType==eUDP){int r= udp.Bind(addrToBind,toAny);      addr=udp.addr;return r;}
   if(iType==eTCP){
      if(!tcp)return -1;
      int r=0;tcp->addr=*addrToBind;//skip bind tcp->Bind(addrToBind,toAny);
      addr=tcp->addr;
      return r;
   }
   if(iType==eTLS){
      if(!tls)return -1;
      int r=0;tls->addr=*addrToBind;//skip bind  tls->Bind(addrToBind,toAny);
      addr=tls->addr;
      return r;
   }
   return T_UNKNOWN_SOCK_RET;
}

