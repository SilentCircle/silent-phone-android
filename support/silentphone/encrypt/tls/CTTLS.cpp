//
// /FORCE:MULTIPLE --- sha2 polarssl and libzrtp
//warning LNK4088: image being generated due to /FORCE option; image may not run
#include "CTTLS.h"

#define T_ENABLE_TLS

#include <string.h>
#include <stdio.h>

#ifdef T_ENABLE_TLS

#include <polarssl/config.h>
#include <polarssl/net.h>
#include <polarssl/ssl.h>
#include <polarssl/entropy.h>
#include <polarssl/ctr_drbg.h>
#include <polarssl/error.h>

#define DEBUG_LEVEL 2

#ifdef __APPLE__
void relTcpBGSock(void *ptr);
void *prepareTcpSocketForBg(int s);
#else
void relTcpBGSock(void *ptr){}
void *prepareTcpSocketForBg(int s){return (void*)1;}
#endif

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



void tivi_slog(const char* format, ...);

void tmp_log(const char *p);

void my_debug( void *ctx, int level, const char *str )
{
   /*
    {
        fprintf( (FILE *) ctx, "%s", str );
        fflush(  (FILE *) ctx  );
    }
    */
    //if( level < DEBUG_LEVEL )
      //tivi_slog("lev[%d]%s",level,str);
}


typedef struct{
	ssl_session ssn;
	ssl_context ssl;
   x509_cert cacert;
	entropy_context entropy;
	ctr_drbg_context ctr_drbg;
	int sock;
   void *voipBCKGR;
}T_SSL;

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
}

void CTTLS::initEntropy(){
   if(iEntropyInicialized)return;
   iEntropyInicialized=1;
   int ret;
   char *getEntropyFromZRTP_tmp(unsigned char *p, int iBytes);
   unsigned char br[64];
	
   unsigned int getTickCount();
   unsigned int ui=getTickCount();
   
	entropy_init( &((T_SSL*)pSSL)->entropy );
	if( ( ret = ctr_drbg_init( &((T_SSL*)pSSL)->ctr_drbg, entropy_func, &((T_SSL*)pSSL)->entropy,
                             (unsigned char *) getEntropyFromZRTP_tmp(&br[0],63), 63 ) ) != 0 )
	{
		tivi_slog( " failed\n  ! ctr_drbg_init returned %d", ret );
	}
   printf("[init tls entrpoy sp=%d ms]\n",getTickCount()-ui);
}

CTTLS::~CTTLS(){
   closeSocket();
	memset(pSSL,0,sizeof(T_SSL));
	delete (T_SSL*)pSSL;
	if(cert)delete cert;
}

int CTTLS::createSock(){
   iPeerClosed=0;
	return 0;
}

int CTTLS::closeSocket(){
   
	if((!iConnected && iPeerClosed==0)  || iClosed){
      if(iNeedCallCloseSocket && pSSL && ((T_SSL*)pSSL)->sock){
         SOCKET server_fd=((T_SSL*)pSSL)->sock;
         net_close(server_fd);
      }
      iNeedCallCloseSocket=0;
     
      return 0;
   }
   addrConnected.clear();
   iPeerClosed=0;
	iConnected=0;
   iClosed=1;
	
	ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
   
	ssl_close_notify( ssl );

   Sleep(60);
   
   SOCKET server_fd=((T_SSL*)pSSL)->sock;
   net_close( server_fd );

   Sleep(80);
	if(ssl)ssl_free( ssl );
   
   ((T_SSL*)pSSL)->sock=0;
	
   iNeedCallCloseSocket=0;
	return 0;
}
void CTTLS::setCert(char *p, int iLen, char *pCertBufHost){
   if(cert)delete cert;
   cert=new char[iLen+1];
   if(!cert)return;
   memcpy(cert,p,iLen);
   cert[iLen]=0;
   int l=strlen(pCertBufHost);
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
int CTTLS::_connect(ADDR *address){
	addrConnected=*address;
//	int server_fd=((T_SSL*)pSSL)->sock;
	ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
   x509_cert *ca=&((T_SSL*)pSSL)->cacert;

   
#if 0
static int ssl_default_ciphersuitesz[] =
{
#if defined(POLARSSL_DHM_C)
#if defined(POLARSSL_AES_C)
//    SSL_EDH_RSA_AES_128_SHA,
    SSL_EDH_RSA_AES_256_SHA,
#endif
#if defined(POLARSSL_CAMELLIA_C)
    SSL_EDH_RSA_CAMELLIA_128_SHA,
    SSL_EDH_RSA_CAMELLIA_256_SHA,
#endif
#if defined(POLARSSL_DES_C)
    SSL_EDH_RSA_DES_168_SHA,
#endif
#endif

#if defined(POLARSSL_AES_C)
    SSL_RSA_AES_256_SHA,
#endif
#if defined(POLARSSL_CAMELLIA_C)
    SSL_RSA_CAMELLIA_256_SHA,
#endif
#if defined(POLARSSL_AES_C)
   // SSL_RSA_AES_128_SHA,
#endif
#if defined(POLARSSL_CAMELLIA_C)
   // SSL_RSA_CAMELLIA_128_SHA,
#endif
#if defined(POLARSSL_DES_C)
    SSL_RSA_DES_168_SHA,
#endif
#if defined(POLARSSL_ARC4_C)
    SSL_RSA_RC4_128_SHA,
    SSL_RSA_RC4_128_MD5,
#endif
    0
};
#else
   const int ssl_default_ciphersuitesz[] =
   {
#if defined(POLARSSL_DHM_C)
#if defined(POLARSSL_AES_C)
#if defined(POLARSSL_SHA2_C)
      TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
#endif /* POLARSSL_SHA2_C */
#if defined(POLARSSL_GCM_C) && defined(POLARSSL_SHA4_C)
      TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
#endif
      TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
#if defined(POLARSSL_SHA2_C)
      TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
#endif
#if defined(POLARSSL_GCM_C) && defined(POLARSSL_SHA2_C)
      TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
#endif
      TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
#endif
#if defined(POLARSSL_CAMELLIA_C)
#if defined(POLARSSL_SHA2_C)
      TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256,
#endif /* POLARSSL_SHA2_C */
      TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA,
#if defined(POLARSSL_SHA2_C)
      TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256,
#endif /* POLARSSL_SHA2_C */
      TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA,
#endif
#if defined(POLARSSL_DES_C)
      TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA,
#endif
#endif
      
      
#if defined(POLARSSL_AES_C)
#if defined(POLARSSL_SHA2_C)
      TLS_RSA_WITH_AES_256_CBC_SHA256,
#endif /* POLARSSL_SHA2_C */
#if defined(POLARSSL_GCM_C) && defined(POLARSSL_SHA4_C)
      TLS_RSA_WITH_AES_256_GCM_SHA384,
#endif /* POLARSSL_SHA2_C */
      TLS_RSA_WITH_AES_256_CBC_SHA,
#endif
#if defined(POLARSSL_CAMELLIA_C)
#if defined(POLARSSL_SHA2_C)
      TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256,
#endif /* POLARSSL_SHA2_C */
      TLS_RSA_WITH_CAMELLIA_256_CBC_SHA,
#endif
#if defined(POLARSSL_AES_C)
#if defined(POLARSSL_SHA2_C)
      TLS_RSA_WITH_AES_128_CBC_SHA256,
#endif /* POLARSSL_SHA2_C */
#if defined(POLARSSL_GCM_C) && defined(POLARSSL_SHA2_C)
      TLS_RSA_WITH_AES_128_GCM_SHA256,
#endif /* POLARSSL_SHA2_C */
      TLS_RSA_WITH_AES_128_CBC_SHA,
#endif
#if defined(POLARSSL_CAMELLIA_C)
#if defined(POLARSSL_SHA2_C)
      TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256,
#endif /* POLARSSL_SHA2_C */
      TLS_RSA_WITH_CAMELLIA_128_CBC_SHA,
#endif
#if defined(POLARSSL_DES_C)
      TLS_RSA_WITH_3DES_EDE_CBC_SHA,
#endif
#if defined(POLARSSL_ARC4_C)
    //  TLS_RSA_WITH_RC4_128_SHA,
     // TLS_RSA_WITH_RC4_128_MD5,
#endif
      0
   };
#endif
   if(iCallingConnect)return 0;
   
   CTAutoIntUnlock _a(&iCallingConnect);
   
   if(!iClosed){
      puts("destr tls");
      closeSocket();
      Sleep(100);
      puts("destr tls ok");
   }

   

	char bufX[64];
	address->toStr(&bufX[0],0);
   int iIncPort=0;
   if(address->getPort()==5060)iIncPort++;//TODO fix
	
	iConnected=0;
   int ret;
   memset( ca, 0, sizeof( x509_cert ) );
	do{
#define CERT_VERIFY
      int iCertErr=1;
#ifdef CERT_VERIFY

      char *p=cert;
      if(cert){
         iCertErr = x509parse_crt( ca, (unsigned char *) p,       strlen( p ) );
      }
#endif
      puts(&bufX[0]);
		if(net_connect(&(((T_SSL*)pSSL)->sock),&bufX[0],address->getPort()+iIncPort))
         break;
      
      iLastTLSSOCK_TEST=(((T_SSL*)pSSL)->sock);
      iNeedCallCloseSocket=1;
#ifndef _WIN32
      int on=1;

      setsockopt((((T_SSL*)pSSL)->sock), SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on));//new 05052012
 //TODO set this if need backgr only
#endif
      relTcpBGSock(((T_SSL*)pSSL)->voipBCKGR);
      ((T_SSL*)pSSL)->voipBCKGR=prepareTcpSocketForBg(((T_SSL*)pSSL)->sock);
      
      initEntropy();
      
   	if( ( ret = ssl_init( ssl ) ) != 0 )
		{
         error_strerror(ret,&bufErr[0],sizeof(bufErr)-1);
         tivi_slog("ssl_init[%s]",&bufErr[0]);
			break;
		}
	
		
		ssl_set_endpoint( ssl, SSL_IS_CLIENT );

      ssl_set_authmode( ssl,iCertErr==0?SSL_VERIFY_OPTIONAL:SSL_VERIFY_NONE );
		
		ssl_set_rng( ssl, ctr_drbg_random, &((T_SSL*)pSSL)->ctr_drbg );
      
		ssl_set_dbg( ssl, my_debug, stdout );
		ssl_set_bio( ssl, net_recv, (void*)&(((T_SSL*)pSSL)->sock),
			       net_send, (void*)&(((T_SSL*)pSSL)->sock) );

		ssl_set_ciphersuites( ssl, ssl_default_ciphersuitesz );
		//ssl_set_session( ssl, 1, 600, &((T_SSL*)pSSL)->ssn );//will  timeout after 600, and will be resumed
	//	ssl_set_session( ssl, 1, 0, &((T_SSL*)pSSL)->ssn );//will never timeout, and will be resumed


      iCertFailed=0;
#ifdef CERT_VERIFY
      if(iCertErr==0){
         ssl_set_ca_chain( ssl, ca, NULL, &bufCertHost[0] );
         ssl_set_hostname( ssl, &bufCertHost[0] );
         checkCert();
      }
#endif

      
      iClosed=0;
		iConnected=1;
      addrConnected=*address;
	}while(0);
	
	
	return 0;
}

void CTTLS::failedCert(const  char *err, const char *descr, int fatal){
   if(descr)tivi_slog(err,descr);
   else tivi_slog(err);
   if(fatal){
      if(errMsg)errMsg(pRet,err);
      iCertFailed=1;
   }

}

int CTTLS::getInfo(const char *key, char *p, int iMax){
   if(!pSSL)return 0;
   
   ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
   p[0]=0;
   
   if(!ssl){
      strncpy(p,"SSL is not connected",iMax);
   }else {
      const char *pdst = ssl_get_ciphersuite( ssl );
      if(pdst && strlen(pdst)>4){
         int b = ssl->dhm_P.n ?
                 (ssl->dhm_P.n * 8)  :
         (ssl->rsa_key && ssl->rsa_key_len ? ssl->rsa_key_len( ssl->rsa_key ) : 0);
         
         b*=8;
         
         char *stripText(char *dst, int iMax, const char *src, const char *cmp);
         
         int l = snprintf(p, iMax,"%dbits ", b);
         stripText(p+l, iMax-l-1, pdst+4, "-WITH");
      }
   }
   return strlen(p);
}

int CTTLS::checkCert(){
   int ret;

   ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
    while( ( ret = ssl_handshake( ssl ) ) != 0 )
    {
        if( ret != POLARSSL_ERR_NET_WANT_READ && ret != POLARSSL_ERR_NET_WANT_WRITE )
        {
            tivi_slog( " failed\n  ! ssl_handshake returned %d\n", ret );
           //lev[1]/Users/janisnarbuts/libs/polarssl-1.1.1/iphone/polar_ssl/polar_ssl/../../../library/ssl_tls.c(1035): ssl_fetch_input() returned -29312 (0xffff8d80)

            return -1;
        }
#ifndef _WIN32
       usleep(20);
#else
        Sleep(15);
#endif
    }

    tivi_slog( " ok\n    [ Ciphersuite is %s ]", ssl_get_ciphersuite( ssl ) );

    /*
     * 5. Verify the server certificate
     */
    tivi_slog( "  . Verifying peer X.509 certificate..." );

    if( ( ret = ssl_get_verify_result( ssl ) ) != 0 )
    {
        tivi_slog( " failed" );
        printf("ssl_get_verify_result()=%d\n",ret);
       
       // ssl_context *ssl2=&((T_SSL*)pSSL)->ssl;
     //  puts((char*)ssl2->ca_chain->subject.val.p);

        if( ( ret & BADCERT_EXPIRED ) != 0 )
            failedCert( "  ! server certificate has expired",NULL,1 );

        if( ( ret & BADCERT_REVOKED ) != 0 )
            failedCert( "  ! server certificate has been revoked",NULL,1 );

       if( ( ret & BADCERT_CN_MISMATCH ) != 0 ){
            failedCert( "  ! CN mismatch (expected CN=%s)",this->bufCertHost,1);
       }

        if( ( ret & BADCERT_NOT_TRUSTED ) != 0 )
            failedCert( "  ! self-signed or not signed by a trusted CA",NULL,1 );

    }
    else
        tivi_slog( " ok" );
/*
    tivi_slog( "  . Peer certificate information    ..." );
    x509parse_cert_info( (char *) buf, sizeof( buf ) - 1, "      ", ssl->peer_cert );
    tivi_slog( "%s", buf );*/
    return 0;
}


int CTTLS::_send(const char *buf, int iLen){
	ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
   if(!iConnected)return -1001;
   if(iClosed)return -1002;
   if(iCertFailed){Sleep(30);return -1003;}

	int ret=0;
   int iShouldDisconnect=0;
	while( ( ret = ssl_write( ssl, (const unsigned char *)buf, iLen ) ) <= 0 )
	{
		if( ret != POLARSSL_ERR_NET_WANT_READ && ret != POLARSSL_ERR_NET_WANT_WRITE )
		{
         iShouldDisconnect=1;
         Sleep(5);
         break;
		}
      if(!iConnected)break;
      
      if(ret == POLARSSL_ERR_NET_WANT_READ){
         iWaitForRead=1;
         for(int i=0;i<5 && iWaitForRead;i++)
            Sleep(20);
      }
	}
   void tmp_log(const char *p);
   char d[64];sprintf(d, "[ssl-send=%p %.*s l=%d ret=%d]",ssl, 7,buf, iLen, ret);tmp_log(d);
   
   if(ret<0){
      if(ret==POLARSSL_ERR_NET_CONN_RESET || ret==POLARSSL_ERR_NET_SEND_FAILED){
         iShouldDisconnect=1;
      }
      if(iShouldDisconnect){addrConnected.clear();iConnected=0; tmp_log("tls_send err clear connaddr");}
      error_strerror(ret,&bufErr[0],sizeof(bufErr)-1);
      tivi_slog(" send[%s]%d",&bufErr[0],iShouldDisconnect);
   }
   else{
      //TODO msg("getCS",5,void *p, int iSize);
     //  tivi_slog( " [ Ciphersuite is %s ]\n", ssl_get_ciphersuite( ssl ) );
   }

	return ret;
}
int CTTLS::_recv(char *buf, int iMaxSize){
	
	int ret=0;
	ssl_context *ssl=&((T_SSL*)pSSL)->ssl;
   if(iCertFailed){Sleep(30);return 0;}
   if(!isConected()){Sleep(iPrevReadRet==100000?30:15);iPrevReadRet=100000;return -1;}

	while(!iClosed){
    //  puts("read sock");
      iWaitForRead=0; 
	   ret = ssl_read( ssl, (unsigned char *)buf, iMaxSize );
   //   void wakeCallback(int iLock);wakeCallback(1);
      
      
      if(!iConnected)break;

      if( ret == POLARSSL_ERR_NET_WANT_READ || ret == POLARSSL_ERR_NET_WANT_WRITE ){
         
         Sleep(ret == POLARSSL_ERR_NET_WANT_WRITE?50:5);
         if(iPrevReadRet==ret)Sleep(15);
         iPrevReadRet=ret;
         continue;
         //break;
      }
      if( ret == POLARSSL_ERR_SSL_PEER_CLOSE_NOTIFY ){
          iConnected=0;
          iPeerClosed=2;
		    break;
      }
     
	     if( ret < 0 )
        {
            printf( "failed\n  ! ssl_read returned %d\n", ret );
            break;
        }
      
 
        if( ret == 0 )
        {
            Sleep(20);
            break;
        }
      break;
   };
   if(iPeerClosed==2 || ret==POLARSSL_ERR_NET_CONN_RESET || ret==POLARSSL_ERR_NET_RECV_FAILED){
      char b[64];sprintf(b, "tls_recv err clear connaddr ret=%d pc=%d",ret, iPeerClosed);
      tmp_log(b);
      iPeerClosed=1;
      this->addrConnected.clear();
      iConnected=0;
     
   }
   else{
      void tmp_log(const char *p);
      char d[64];sprintf(d, "[ssl-recv=%p %.*s max=%d ret=%d]",ssl, 12,buf, iMaxSize, ret);tmp_log(d);
   }

   if(ret<0){
      error_strerror(ret,&bufErr[0],sizeof(bufErr)-1);
      tivi_slog("<<<rec[%s]pc[%d]",&bufErr[0],iPeerClosed);
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
 ret = ssl_read( &s->ssl, (unsigned char*)buf, len );
 
 if( ret == POLARSSL_ERR_NET_WANT_READ || ret == POLARSSL_ERR_NET_WANT_WRITE )
 continue;
 
 
 if( ret <= 0 )
 {
 switch( ret )
 {
 case POLARSSL_ERR_SSL_PEER_CLOSE_NOTIFY:
 DEBUG_TLS(0, " connection was closed gracefully\n" );
 s->iNeedClose=1;
 break;
 
 case POLARSSL_ERR_NET_CONN_RESET:
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

