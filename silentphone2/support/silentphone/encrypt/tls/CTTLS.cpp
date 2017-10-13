//
// /FORCE:MULTIPLE --- sha2 polarssl and libzrtp
//warning LNK4088: image being generated due to /FORCE option; image may not run
#include "CTTLS.h"

#include "../../tiviengine/tivi_log.h"

#define T_ENABLE_TLS

#include <string.h>
#include <stdio.h>

#ifdef T_ENABLE_TLS

#if !defined(MBEDTLS_CONFIG_FILE)
#include "mbedtls/config.h"
#else
#include MBEDTLS_CONFIG_FILE
#endif
#include <mbedtls/net_sockets.h>
#include <mbedtls/entropy.h>
#include <mbedtls/ctr_drbg.h>
#include <mbedtls/error.h>
#include <mbedtls/debug.h>

static void * (*volatile memset_volatile)(void *, int, size_t) = memset;

#define DEBUG_LEVEL 2

int mustCheckTLSCert();

#ifdef _WIN32
#define snprintf _snprintf
#endif

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

/*The callback has the following argument:
*                 void *           opaque context for the callback
*                 int              debug level
*                 const char *     file name
*                 int              line number
*                 const char *     message
*/
static void my_ssl_debug(void *ctx, int level, const char *filename, int lineNumber, const char* message)
{
    t_logf(log_events, "ssl_debug", "%d - %s", level, message);
}


void tivi_slog(const char* format, ...);

void tmp_log(const char *p);

void *getEncryptedPtr_debug(void *p){
   
   if(!p)return NULL;//dont leak the key
   
   static unsigned long long bufKey;
   static int iInit=1;
   if(iInit){
      iInit=0;
      FILE *f=fopen("/dev/urandom","rb");;
      if(f){
         fread(&bufKey,1,8,f);
         fclose(f);
      }
   }
   unsigned long long ull=(unsigned long long)p;
   
   if(ull<10000)return p;//dont leak the key
   
   ull^=bufKey;
   
   return (void*)ull;
}


typedef struct{
	mbedtls_ssl_session ssn;
	mbedtls_ssl_context ssl;
    mbedtls_ssl_config conf;

   //   x509_cert cacert;
    mbedtls_x509_crt cacert;
   
   // mbedtls_pk_context pkey;
   
	mbedtls_entropy_context entropy;
	mbedtls_ctr_drbg_context ctr_drbg;
    mbedtls_net_context sock;
} T_SSL;

CTTLS::CTTLS(CTSockCB &c):addrConnected(){
	cert=NULL;
   iConnected=0;
   iClosed=1;
   iNeedCallCloseSocket=0;
   iPeerClosed=0;
	pSSL=new T_SSL;
   pRet=NULL;
   errMsg=NULL;
	memset(pSSL,0,sizeof(T_SSL));
   iWaitForRead=0;
   iCertFailed=0;
   iEntropyInicialized=0;
   iCallingConnect=0;
   bIsVoipSock = 0;
   iDestroyFlag=0;
   iWasConnected = 0;

   
}

void CTTLS::enableBackgroundForVoip(int bTrue){
   bIsVoipSock = bTrue;
}

void CTTLS::initEntropy(){
   if(iEntropyInicialized)return;
   iEntropyInicialized=1;
   int ret;
   char *getEntropyFromZRTP_tmp(unsigned char *p, int iBytes);
   unsigned char br[64];
	
  // unsigned int getTickCount(void);
  // unsigned int ui=getTickCount();
   
	mbedtls_entropy_init( &((T_SSL*)pSSL)->entropy );
	if( ( ret = mbedtls_ctr_drbg_seed( &((T_SSL*)pSSL)->ctr_drbg, mbedtls_entropy_func, &((T_SSL*)pSSL)->entropy,
                             (unsigned char *) getEntropyFromZRTP_tmp(&br[0],63), 63 ) ) != 0 )
	{
        t_logf(log_events, __FUNCTION__,"failed! ctr_drbg_seed returned %d", ret );
	}
  // printf("[init tls entrpoy sp=%d ms]\n",getTickCount()-ui);
}

CTTLS::~CTTLS(){
   iDestroyFlag=1;
   t_logf(log_events, __FUNCTION__, "sockdebug: Closing socket in destructor");
   closeSocket();

    if(iEntropyInicialized) {
        mbedtls_ctr_drbg_free(&((T_SSL*)pSSL)->ctr_drbg);
        mbedtls_entropy_free(&((T_SSL *) pSSL)->entropy);
    }
    memset_volatile(pSSL,0,sizeof(T_SSL));
    delete (T_SSL*)pSSL;
    if(cert)delete cert;
}

int CTTLS::createSock(){
   iPeerClosed=0;
	return 0;
}

int CTTLS::closeSocket(){
   iDestroyFlag=1;

    t_logf(log_events, __FUNCTION__, "sockdebug: Closing a socket [iConnected=%d, iPeerClosed=%d, iClosed=%d, iNeedCallCloseSocket=%d]",
           iConnected, iPeerClosed, iClosed, iNeedCallCloseSocket);
	if((!iConnected && iPeerClosed==0)  || iClosed){
      if(iNeedCallCloseSocket && pSSL && ((T_SSL*)pSSL)->sock.fd){
          mbedtls_net_context server_fd =((T_SSL*)pSSL)->sock;
          mbedtls_net_free(&server_fd);
      }
      iNeedCallCloseSocket=0;
      
      return 0;
   }
   addrConnected.clear();
   iPeerClosed=0;
	iConnected=0;
   iClosed=1;
	
	mbedtls_ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
    mbedtls_ssl_config *conf = &((T_SSL*)pSSL)->conf;

    t_logf(log_events, __FUNCTION__, "sockdebug: Sending ssl_close_notify");
	mbedtls_ssl_close_notify( ssl );
   
   Sleep(60);

   mbedtls_net_context server_fd=((T_SSL*)pSSL)->sock;
   mbedtls_net_free(&server_fd);
   
   Sleep(80);
	if(ssl){
      mbedtls_x509_crt_free( &((T_SSL*)pSSL)->cacert );
      mbedtls_ssl_free( ssl );
        mbedtls_ssl_config_free( conf );
   }
   //
   
   ((T_SSL*)pSSL)->sock.fd=0;
	
   iNeedCallCloseSocket=0;
	return 0;
}
void CTTLS::setCert(char *p, int iLen, char *pCertBufHost){
   if(cert)delete cert;
   cert=new char[iLen+1];
   if(!cert)return;
   memcpy(cert,p,iLen);
   cert[iLen]=0;
   int l = (int)strlen(pCertBufHost);
   if(l>sizeof(bufCertHost)-1)l=sizeof(bufCertHost)-1;
   strncpy(bufCertHost,pCertBufHost,l);
   bufCertHost[l]=0;
}

static int iLastTLSSOCK_TEST;
void test_close_last_sock(){
   closesocket(iLastTLSSOCK_TEST);
}


/*
 int ctr_drbg_randomx( void *p_rng,
 unsigned char *output, size_t output_len ){
 char *getEntropyFromZRTP_tmp(unsigned char *p, int iBytes);
 
 getEntropyFromZRTP_tmp(output,(int)output_len);
 
 return 0;
 }
 */
typedef struct{
   int idx;
   
   int iSelected;
   
   int iFailed;
   
   int iConnected;

    mbedtls_net_context* f;
   
   char host[128];
   char port[128];
   
   int iCanDelete;
   
}TH_C_TCP;


typedef struct{
   int idx;

   int iSelected;

   int iFailed;

   int iConnected;

   int f;

   struct addrinfo res;
   int port;

   int iCanDelete;

}TH_C_TCPv4v6;

static int th_connect_tcp(void *p){
   TH_C_TCP *tcp = (TH_C_TCP*)p;
   
   if(!mbedtls_net_connect(tcp->f, tcp->host, tcp->port, MBEDTLS_NET_PROTO_TCP)){
      tcp->iConnected = 1;
   }
   else tcp->iFailed = 1;
   
   int iMax = 2000;
   while (!tcp->iCanDelete && iMax>0) {
      Sleep(50);
      iMax--;
   }
   
   if(iMax<2){
      puts("WARN: th_connect_tcp max<2");
   }
   //do not delete this immediatly
   if(!tcp->iSelected && tcp->iConnected){
      close(tcp->f->fd);
   }
   Sleep(200);
   delete tcp;
   return 0;
}

static int  th_connect_tcp_v4v6(void *p){
   TH_C_TCPv4v6 *tcp = (TH_C_TCPv4v6*)p;


   tcp->f = socket(tcp->res.ai_family, tcp->res.ai_socktype,
              tcp->res.ai_protocol);

   if(tcp->f < 0){
      tcp->iFailed = 1;
   }
   else{
      if (connect(tcp->f, tcp->res.ai_addr, tcp->res.ai_addrlen) < 0) {
         if(tcp->f>=0)close(tcp->f);
         tcp->f = -1;
         tcp->iFailed = 1;
      }
      else {
         tcp->iConnected = 1;
      }
   }

   int iMax = 4000;
   while (!tcp->iCanDelete && iMax>0) {
      Sleep(50);
      iMax--;
   }

   if(iMax<2){
      puts("WARN: th_connect_tcp max<2");
   }
   //do not delete this imidiatly
   if(!tcp->iSelected && tcp->iConnected && tcp->f>=0){
      close(tcp->f);
      tcp->f=-1;
   }
   Sleep(200);
   delete tcp;
   return 0;
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-function" // ignore warning
//return fastet possible connection
static int fast_tcp_connect(ADDR *address, int iIncPort){

   int checkIPNow(void);
   if(!checkIPNow())return 0;

#define T_MAX_TCP_ADDR_CNT 16
   
   int ips[T_MAX_TCP_ADDR_CNT];
   
   CTSock::getHostByName(address->bufAddr, 0, (int*)&ips, T_MAX_TCP_ADDR_CNT);

   TH_C_TCP *tcp[T_MAX_TCP_ADDR_CNT];
   tcp[0]=NULL;

   for(int i=0;i<T_MAX_TCP_ADDR_CNT;i++){
      if(ips[i]==0)break;
      
      TH_C_TCP *p  = new TH_C_TCP;
      memset(p, 0, sizeof(TH_C_TCP));
      p->idx = i;
      
      ADDR a;
      a.ip = ips[i];
      a.toStr(&p->host[0],0);

       sprintf(p->port,"%u", address->getPort()+iIncPort);

       printf("[net_connect(addr=%s port=%d); idx=%d]\n", &p->host[0], address->getPort()+iIncPort,i);
      
      void startThX(int (cbFnc)(void *p),void *data);
      startThX(th_connect_tcp, p);
      tcp[i] = p;
   }
   

   int iConnected = -1;
   int iMaxTestsLeft=0;
   
   
   
   while(1){
      Sleep(40);
      int iSocketsDone = 0;
      int iSockets = 0;
      for(int i=0;i<T_MAX_TCP_ADDR_CNT;i++){
         if(ips[i]==0)break;
         iSockets++;
         iSocketsDone += (tcp[i]->iConnected || tcp[i]->iFailed);
         
         if(tcp[i]->iConnected && (iConnected==-1 || i < iConnected)){
            iConnected = i;
            //should we  reset here or we should wait just 3sec
         }
      }
      if(iConnected>=0){
         iMaxTestsLeft++;
         if(iMaxTestsLeft > 25 * 3)break; //wait 3sec after first socket connect
         if(iConnected == 0)break;//noone can be better than first record
      }
      if(iSockets == iSocketsDone)break;
   }
   
   int ret = iConnected==-1 ? 0 : tcp[iConnected]->f->fd;
   if(ret){
      tcp[iConnected]->iSelected = 1;
      t_logf(log_events, __FUNCTION__, "[selected net_connect(addr=%s port=%d); idx=%d]\n", &tcp[iConnected]->host[0], address->getPort()+iIncPort,iConnected);
   }
   
   for(int i=0;i<T_MAX_TCP_ADDR_CNT;i++){
      if(ips[i]==0)break;
      tcp[i]->iCanDelete = 1;

   }
   return ret;
}
#pragma clang diagnostic pop

int fast_tcp_connectv4v6(ADDR *address, int iIncPort, int *stopFlag){

   int checkIPNow(void);
   if(!checkIPNow())return 0;


   struct addrinfo hints, *res, *res0;
   int error;

   memset(&hints, 0, sizeof(hints));
   hints.ai_family = PF_UNSPEC;
   hints.ai_socktype = SOCK_STREAM;
#if defined(__APPLE__)
   hints.ai_flags = AI_DEFAULT; //Android does not like this
#else
   hints.ai_flags = 0;
#endif

   char buf[256];

   char *psz = CTSock::removePort(buf, sizeof(buf), &address->bufAddr[0]);

   char port[16];
   snprintf(port, sizeof(port), "%u", address->getPort()+iIncPort);

   error = getaddrinfo(psz, port, &hints, &res0);


   t_logf(log_events, __FUNCTION__,"dns %s=%p e=%d",psz,res0,error);
   if(error)return 0;

   TH_C_TCPv4v6 *tcp[T_MAX_TCP_ADDR_CNT];
   tcp[0]=NULL;

   int i;
   for (i=0,res = res0; res && i+1<T_MAX_TCP_ADDR_CNT;i++, res = res->ai_next) {

      TH_C_TCPv4v6 *p  = new TH_C_TCPv4v6;
      memset(p, 0, sizeof(TH_C_TCPv4v6));
      p->idx = i;

      p->res = *res;

      p->port = address->getPort()+iIncPort;

      t_logf(log_events, __FUNCTION__,"[net_connect(port=%d t=%d); idx=%d]\n", address->getPort()+iIncPort, res->ai_family,i);

      void startThX(int (cbFnc)(void *p),void *data);
      startThX(th_connect_tcp_v4v6, p);
      tcp[i] = p;
      tcp[i+1]=NULL;
   }

   int iConnected = -1;
   int iMaxTestsLeft=0;
#define T_TIME_TO_SLEEP_WHILE_WAIT 20

   while(!stopFlag[0]){
      Sleep(T_TIME_TO_SLEEP_WHILE_WAIT);
      int iSocketsDone = 0;
      int iSockets = 0;
      for(int i=0;i<T_MAX_TCP_ADDR_CNT;i++){
         if(tcp[i]==0)break;
         iSockets++;
         iSocketsDone += (tcp[i]->iConnected || tcp[i]->iFailed);

         if(tcp[i]->iConnected && (iConnected==-1 || i < iConnected)){
            iConnected = i;
         }
      }
      if(iConnected>=0){
         iMaxTestsLeft++;
         if(iMaxTestsLeft > (1000/T_TIME_TO_SLEEP_WHILE_WAIT) * 3)break; //wait 3sec after first socket connect
         if(iConnected == 0)break;//noone can be better than first record
      }
      if(iSockets == iSocketsDone)break;
   }

   int ret = iConnected==-1 ? 0 : tcp[iConnected]->f;
   if(ret){

      if(tcp[iConnected]->res.ai_family == AF_INET6){
         struct sockaddr_in6 *sa6 =  (struct sockaddr_in6 *)tcp[iConnected]->res.ai_addr;

         address->setIpv6sockAddr(sa6, tcp[iConnected]->res.ai_addrlen, sa6->sin6_port);
         address->setIPv6(&sa6->sin6_addr.s6_addr[0]);

      }
      else{
         struct sockaddr_in *addr_in = (struct sockaddr_in *)tcp[iConnected]->res.ai_addr;
         address->ip=addr_in->sin_addr.s_addr;
      }
      /*
       struct sockaddr_in *addr_in = (struct sockaddr_in *)res;
       inet_ntop(AF_INET, &(addr_in->sin_addr), s, INET_ADDRSTRLEN);
       struct sockaddr_in6 *addr_in6 = (struct sockaddr_in6 *)res;
       inet_ntop(AF_INET6, &(addr_in6->sin6_addr), s, INET6_ADDRSTRLEN);
       */

      tcp[iConnected]->iSelected = 1;
      t_logf(log_events, __FUNCTION__, "[selected net_connect(port=%d); idx=%d]\n", address->getPort()+iIncPort,iConnected);
   }

   for(int i=0;i<T_MAX_TCP_ADDR_CNT;i++){
      if(tcp[i]==NULL)break;
      if(iConnected != i && tcp[i]->f>=0){
         close(tcp[i]->f);
         tcp[i]->f=-1;
      }
      tcp[i]->iCanDelete = 1;
   }

   freeaddrinfo(res0);

   return ret;
}

int CTTLS::_connect(ADDR *address, ADDR *preferedAddr){
    char errorBuffer[1000];

    addrConnected=*address;
    // int server_fd=((T_SSL*)pSSL)->sock;
    mbedtls_ssl_context *ssl = &((T_SSL*)pSSL)->ssl;
    mbedtls_ssl_config *conf = &((T_SSL*)pSSL)->conf;

    mbedtls_x509_crt *ca=&((T_SSL*)pSSL)->cacert;

    if(iCallingConnect)return 0;

    CTAutoIntUnlock _a(&iCallingConnect);

    if(!iClosed) {
        t_logf(log_events, __FUNCTION__, "sockdebug: Closing socket while trying to make new connection");
        closeSocket();
        Sleep(100);
        iDestroyFlag = 0;
    }

    char bufX[64];
    address->toStr(&bufX[0],0);
    int iIncPort=0;
    if (address->getPort()==5060)iIncPort++;        // TODO fix

   int ret;
    iConnected=0;

   //TODO free

    memset(ca, 0, sizeof( mbedtls_x509_crt ));

    mbedtls_x509_crt_init(ca);
    //  mbedtls_x509_crt_init( &clicert );
    //mbedtls_pk_init( &pkey );

    do {
        int iCertErr=1;
        char *p=cert;
        if(cert){
            iCertErr = mbedtls_x509_crt_parse(ca, (unsigned char *)p, strlen(p)+1);
        }
        if( iCertErr != 0 )
        {
            mbedtls_strerror(iCertErr, errorBuffer, 1000);
            t_logf(log_events, __FUNCTION__, "FAIL! Certificate parse error %x, Message: %s", -iCertErr, errorBuffer);
        }
        if(mustCheckTLSCert() && (!cert || iCertErr)){
            failedCert("No TLS Certificate",NULL,1);
            return -1;
        }

       //we could try to resolve the domain here,
       //but we can not assing that address to the (ADDR address),
       //Reason: CTSipSock will fight and close socket, if we do so we have to update GW.ip and px.ip
       /*
        memset(port_str, 0, sizeof(port_str));
        snprintf(port_str, sizeof(port_str), "%d", host_port);
        
        // Do name resolution with both IPv6 and IPv4, but only TCP
        memset( &hints, 0, sizeof(hints));
        hints.ai_family = AF_INET;
        hints.ai_socktype = SOCK_STREAM;
        hints.ai_protocol = IPPROTO_TCP;
        
        getaddrinfo(host_ipstr, port_str, &hints, &addr_list);
       // (UInt8*)addr_list->ai_addr,addr_list->ai_addrlen;
        */
#if 0

       ((T_SSL*)pSSL)->sock.fd = fast_tcp_connect(address, iIncPort);

#else
       ((T_SSL*)pSSL)->sock.fd = 0;
        int s;
        if(preferedAddr){


           int ok;
           if(preferedAddr->isV6()){
              s = socket(preferedAddr->isV6()? AF_INET6: AF_INET, SOCK_STREAM, IPPROTO_TCP);

              if(!s){
                 break;
              }

              int len = 0;
              void *pSock = preferedAddr->getV6Sock(&len);
              ok = !connect(s, (struct sockaddr*) pSock, len);
           }
           else{
              preferedAddr->toStr(&bufX[0],0);
              char port[16];
              snprintf(port, sizeof(port), "%u", address->getPort()+iIncPort);
              ok = !mbedtls_net_connect(&((T_SSL*)pSSL)->sock, bufX, port, MBEDTLS_NET_PROTO_TCP);
              s = ((T_SSL*)pSSL)->sock.fd;
           }
           if(ok){
              ((T_SSL*)pSSL)->sock.fd = s;
           }
           else{
              close(s);
              //do not break here, we want to try other options
           }
        }
        if(!((T_SSL*)pSSL)->sock.fd){
            ((T_SSL*)pSSL)->sock.fd = fast_tcp_connectv4v6(address, iIncPort,&iDestroyFlag);
        }
  //     net_connect(&((T_SSL*)pSSL)->sock,bufCertHost,address->getPort()+iIncPort);
#endif
       
       
       if(!((T_SSL*)pSSL)->sock.fd){
          break;
       }

        iLastTLSSOCK_TEST=(((T_SSL*)pSSL)->sock.fd);
        iNeedCallCloseSocket=1;

#ifndef _WIN32
        int on=1;
        /*
         int* delay = X; setsockopt(sockfd,SOL_TCP,TCP_KEEPIDLE,&delay,sizeof(delay)); int count = X; setsockopt(sockfd,SOL_TCP,TCP_KEEPCNT,&count,sizeof(count)); int interval = X; setsockopt(sockfd,SOL_TCP,TCP_KEEPINTVL,&interval,sizeof(interval)); int enable = 1; setsockopt(sockfd,SOL_SOCKET,SO_KEEPALIVE,&enable,sizeof(enable));
         */

        setsockopt((((T_SSL*)pSSL)->sock.fd), SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on));//new 05052012
        //TODO set this if need backgr only
#endif
        //make sure to have blocking socket, the iOS defualts to non-blocking for voip
        mbedtls_net_set_block(&((T_SSL*)pSSL)->sock);//

        initEntropy();

        mbedtls_ssl_init(ssl);
        mbedtls_ssl_config_init(conf);

        if( ( ret = mbedtls_ssl_config_defaults( conf,
                                                 MBEDTLS_SSL_IS_CLIENT,
                                                 MBEDTLS_SSL_TRANSPORT_STREAM,
                                                 /* MBEDTLS_SSL_PRESET_SUITEB */ MBEDTLS_SSL_PRESET_DEFAULT ) ) != 0 )
        {
            mbedtls_strerror(ret, errorBuffer, 1000);
            t_logf(log_events, __FUNCTION__, "FAIL! Set SSL config returned %x, Message: %s", -ret, errorBuffer);
        }

//        mbedtls_ssl_conf_endpoint(conf, MBEDTLS_SSL_IS_CLIENT);
//        mbedtls_ssl_conf_authmode(conf, MBEDTLS_SSL_VERIFY_REQUIRED );
//       mbedtls_ssl_conf_authmode( ssl, iCertErr == 0 ? MBEDTLS_SSL_VERIFY_REQUIRED: MBEDTLS_SSL_VERIFY_NONE );

        mbedtls_ssl_conf_rng(conf, mbedtls_ctr_drbg_random, &((T_SSL*)pSSL)->ctr_drbg);
        mbedtls_ssl_set_bio(ssl, &(((T_SSL*)pSSL)->sock), mbedtls_net_send, mbedtls_net_recv, NULL);

        mbedtls_ssl_conf_ciphersuites(conf, mbedtls_ssl_list_ciphersuites());
        //mbedtls_ssl_set_session( ssl, 1, 600, &((T_SSL*)pSSL)->ssn );//will  timeout after 600, and will be resumed
        //mbedtls_ssl_set_session( ssl, 1, 0, &((T_SSL*)pSSL)->ssn );//will never timeout, and will be resumed

#if defined(__APPLE__) && DEBUG
        //
        // Set the logging threshold for
        // mbedtls in order to log any errors
        // from the library using CocoaLumberjack
        //
        //mbedtls_debug_set_threshold(2);
#endif
        
        mbedtls_ssl_conf_dbg(conf, my_ssl_debug, NULL);                   // *** SSLDEBUG
        mbedtls_ssl_conf_ca_chain( conf, ca, NULL);

        if( ( ret = mbedtls_ssl_setup( ssl, conf ) ) != 0 )
        {
            mbedtls_strerror(ret, errorBuffer, 1000);
            t_logf(log_events, __FUNCTION__, "FAIL! Set SSL setup %x, Message: %s", -ret, errorBuffer);
        }

        iCertFailed=0;
        if (1) { //|| iCertErr == 0){
            mbedtls_ssl_set_hostname( ssl, &bufCertHost[0] );
            int r = checkCert();
            if(r < 0){
               if(r==-1){//do not show the message if TLS handshake fails. SP-453
                  failedCert("Certificate failed",NULL,1);
               }
               iCertFailed = 1;
               iClosed=0;
               return 0;
            }
        }
        iClosed=0;
        iConnected=1;
       if(!iWasConnected)iWasConnected=1;
        addrConnected=*address;
    } while(0);

    return 0;
}

void CTTLS::failedCert(const  char *err, const char *descr, int fatal){
   if(descr)t_logf(log_events, __FUNCTION__,err,descr);
   else t_logf(log_events, __FUNCTION__, err);
   if(fatal){
      if(errMsg)errMsg(pRet,err);
      iCertFailed=1;
   }
   
}

int CTTLS::getInfo(const char *key, char *p, int iMax){
   if(!pSSL)return 0;
   
   mbedtls_ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
    mbedtls_ssl_config *conf=&((T_SSL*)pSSL)->conf;
   p[0]=0;
   
   if(!ssl){
      strncpy(p,"SSL is not connected",iMax);
   }else {
      const char *pdst = mbedtls_ssl_get_ciphersuite( ssl );
      if(pdst && strlen(pdst)>4){
         int b = (int)(conf->dhm_P.n * 8);
         //:
         //(ssl->rsa_key && ssl->rsa_key_len ? ssl->rsa_key_len( ssl->rsa_key ) : 0);
         
         b*=8;
         
         char *stripText(char *dst, int iMax, const char *src, const char *cmp);
         
         int l = snprintf(p, iMax,"%dbits ", b);
         stripText(p+l, iMax-l-1, pdst+4, "-WITH");
      }
   }
   return (int)strlen(p);
}

int CTTLS::checkCert(){
   int ret;

   mbedtls_ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
   log_events( __FUNCTION__, "Starting TLS handshake..." );
 
   while ((ret = mbedtls_ssl_handshake(ssl)) != 0)
   {
      if( ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE )
      {
          char buffer[1000];
          mbedtls_strerror(ret, buffer, 1000);
          t_logf(log_events, __FUNCTION__, "FAIL! ssl_handshake returned %x, Message: %s, host: %s", ret, buffer, bufCertHost);

         return -2;
      }
#ifndef _WIN32
      usleep(20);
#else
      Sleep(15);
#endif
   }
   t_logf(log_events, __FUNCTION__, "OK [Ciphersuite is %s]", mbedtls_ssl_get_ciphersuite( ssl ) );
   /*
    * 5. Verify the server certificate
    */
   t_logf(log_events, __FUNCTION__, "Verifying peer X.509 certificate...");
   
   if (( ret = mbedtls_ssl_get_verify_result( ssl ) ) != 0 ) {

      t_logf(log_events, __FUNCTION__, "Fail: ssl_get_verify_result()=%d",ret);
      
      // mbedtls_ssl_context *ssl2=&((T_SSL*)pSSL)->ssl;
      //  puts((char*)ssl2->ca_chain->subject.val.p);
      
      if( ( ret & MBEDTLS_X509_BADCERT_EXPIRED ) != 0 )
         failedCert( "  ! server certificate has expired",NULL,1 );
      
      if( ( ret & MBEDTLS_X509_BADCERT_REVOKED ) != 0 )
         failedCert( "  ! server certificate has been revoked",NULL,1 );
      
      if( ( ret & MBEDTLS_X509_BADCERT_CN_MISMATCH ) != 0 ){
         failedCert( "  ! CN mismatch (expected CN=%s)",this->bufCertHost,1);
      }
      
      if( ( ret & MBEDTLS_X509_BADCERT_NOT_TRUSTED ) != 0 )
         failedCert( "  ! self-signed or not signed by a trusted CA",NULL,1 );
      
      return -1;
      
   }
   else
     log_events(__FUNCTION__,"OK");
   /* failed!
    tivi_slog( "  . Peer certificate information    ..." );
    x509parse_cert_info( (char *) buf, sizeof( buf ) - 1, "      ", ssl->peer_cert );
    tivi_slog( "%s", buf );*/
   return 0;
}


int CTTLS::_send(const char *buf, int iLen){
    mbedtls_ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
    if(!iConnected)return -1001;
    if(iClosed)return -1002;
    if(iCertFailed){Sleep(30);return -1003;}

    int ret=0;
    int iShouldDisconnect=0;
    while((ret = mbedtls_ssl_write( ssl, (const unsigned char *)buf, iLen ) ) <= 0 )
    {
        sleep(20);

        if (ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE ) {
            iShouldDisconnect=1;
            Sleep(5);
            break;
        }
        if(!iConnected)break;

        if(ret == MBEDTLS_ERR_SSL_WANT_READ){
            iWaitForRead=1;
            for(int i=0;i<5 && iWaitForRead;i++)
                Sleep(20);
        }
    }
    // Use this if we need a SIP trace: tivi_slog("[ssl-send=%p %.*s l=%d ret=%d]", getEncryptedPtr_debug(ssl), 800, buf, iLen, ret);
    // On Android max log line length is about 1K
    t_logf(log_events, __FUNCTION__, "[ssl-send=%p %.*s l=%d ret=%d]", getEncryptedPtr_debug(ssl), 12, buf, iLen, ret);

    if (ret < 0) {
        if(ret==MBEDTLS_ERR_NET_CONN_RESET || ret==MBEDTLS_ERR_NET_SEND_FAILED){
            iShouldDisconnect=1;
        }
        if(iShouldDisconnect){addrConnected.clear();iConnected=0; log_events(__FUNCTION__, "tls_send err clear connaddr");}
        mbedtls_strerror(ret, bufErr, sizeof(bufErr)-1);
        t_logf(log_events, __FUNCTION__, "send[%s] %d", bufErr, iShouldDisconnect);
    }
    else {
        //TODO msg("getCS",5,void *p, int iSize);
        //  tivi_slog( " [ Ciphersuite is %s ]\n", mbedtls_ssl_get_ciphersuite( ssl ) );
    }

    return ret;
}
int CTTLS::_recv(char *buf, int iMaxSize){
	
	int ret=0;
	mbedtls_ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
   if(iCertFailed){Sleep(30);return 0;}
   if(!isConected()){Sleep(iPrevReadRet==100000?30:15);iPrevReadRet=100000;return -1;}
   
   int iPOLARSSL_ERR_NET_WANT_cnt=0;
   
	while(!iClosed){
        
        t_logf(log_events, __PRETTY_FUNCTION__, "1. iConnected: %d iPeerClosed: %d", iConnected, iPeerClosed);
        
      //  puts("read sock");
      iWaitForRead=0;
	   ret = mbedtls_ssl_read( ssl, (unsigned char *)buf, iMaxSize );
      //   void wakeCallback(int iLock);wakeCallback(1);
      
        t_logf(log_events, __PRETTY_FUNCTION__, "2. iConnected: %d iPeerClosed: %d", iConnected, iPeerClosed);
      
      if(!iConnected)break;
      
      if( ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE ){
         
         Sleep(ret == MBEDTLS_ERR_SSL_WANT_WRITE?50:5);
         if(iPrevReadRet==ret)
            Sleep(50);
         iPOLARSSL_ERR_NET_WANT_cnt++;
         
         if(iPOLARSSL_ERR_NET_WANT_cnt<20) t_logf(log_events, __FUNCTION__,"[sock rw]");
         iPrevReadRet=ret;
         continue;
         //break;
      }
      if( ret == MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY || ret == 0){
         iConnected=0;
         iPeerClosed=2;
         Sleep(10);
         break;
      }
      
      if( ret < 0 )
      {
         iPeerClosed = 1;
         t_logf(log_events, __FUNCTION__, "failed  ! ssl_read returned %d", ret );
         break;
      }
      
      break;
   }
   if(iPeerClosed==2 || ret==MBEDTLS_ERR_NET_CONN_RESET || ret==MBEDTLS_ERR_NET_RECV_FAILED){
      t_logf(log_events, __FUNCTION__, "tls_recv err clear connaddr ret=%d pc=%d",ret, iPeerClosed);
      iPeerClosed=1;
      this->addrConnected.clear();
      iConnected=0;
      
   }
   else{
       // Use this if we need a SIP trace: tivi_slog("[ssl-recv=%p %.*s max=%d ret=%d]", getEncryptedPtr_debug(ssl), 800, buf, iMaxSize, ret);
    // On Android max log line length is about 1K
      if(ret>=0)
        t_logf(log_events, __FUNCTION__,"[ssl-recv=%p %.*s max=%d ret=%d]", getEncryptedPtr_debug(ssl), 50<ret?50:ret, buf, iMaxSize, ret);
   }
   
   if(ret<0){
      mbedtls_strerror(ret,&bufErr[0],sizeof(bufErr)-1);
      t_logf(log_events, __FUNCTION__,"<<<rec[%s]pc[%d]",&bufErr[0],iPeerClosed);
   }
   
   
   if(ret<=0 && iPrevReadRet==ret){
      Sleep(ret==iPPrevReadRet?100:50);
   }
   iPPrevReadRet=iPrevReadRet;
   iPrevReadRet=ret;
   
   return ret;
}

void CTTLS::reCreate(){
	//closeSocket();
	//createSock();
}
#else
CTTLS::CTTLS(CTSockCB &c){}
CTTLS::~CTTLS(){}
void CTTLS::reCreate(){}
int CTTLS::createSock(){return 0;}
int CTTLS::closeSocket(){return 0;}
int CTTLS::_connect(ADDR *address){return 0;}
int CTTLS::_send(const char *buf, int iLen){return 0;}
int CTTLS::_recv(char *buf, int iLen){return 0;}
void CTTLS::setCert(char *p, int iLen, char *host){}
void CTTLS::failedCert(const char *err, const char *descr, int fatal){}
int CTTLS::checkCert(){return 0;}
#endif

/*
 int t_recvTLS(void *pSock, char *buf, int len){
 int ret=0;
 T_SSL *s=(T_SSL*)pSock;
 
 do
 {
 memset( buf, 0, len);
 ret = mbedtls_ssl_read( &s->ssl, (unsigned char*)buf, len );
 
 if( ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE )
 continue;
 
 
 if( ret <= 0 )
 {
 switch( ret )
 {
 case MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY:
 DEBUG_TLS(0, " connection was closed gracefully\n" );
 s->iNeedClose=1;
 break;
 
 case MBEDTLS_ERR_NET_CONN_RESET:
 DEBUG_TLS(0, " connection was reset by peer\n" );
 s->iNeedClose=1;
 break;
 
 default:
 DEBUG_RET( " ssl_read returned %d\n", ret );
 s->iNeedClose=2;
 break;
 }
 
 break;
 }
 
 
 
 // printf( " %d bytes read\n\n%s", len, (char *) buf );
 }
 while( 0 );
 
 return ret;
 }
 
 */

//   ADDR addr;

