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

#include "../baseclasses/CTBase.h"
#include "CTSipSock.h"
#include "../sipparser/client/sip_utils.inl"

#include "tivi_log.h"

#ifdef _WIN32
#define snprintf _snprintf
#endif

#ifdef ANDROID_NDK
#include <android/log.h>                          // To pull in va_list et al
int androidLog(char const *fmt, va_list arg);
#endif
unsigned int getTickCount();
void tmp_log(const char *p);
int mustUseTLS();

#define T_UNKNOWN_SOCK_RET (-2)

/*
 * Silent Phone uses the following root certificates to setup TLS with the
 * SIP servers of the production network.
 */
static const char *productionCert =
// Silent Circle root certificate, serial number be:31:b0:4b:ae:49:f2:71, SHA1: E8:F3:C4:98
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
"-----END CERTIFICATE-----\r\n"

 // Entrust root certificate, serial number 45:6b:50:54, SHA1: B3:1E:B1:B7
"-----BEGIN CERTIFICATE-----\r\n"
"MIIEkTCCA3mgAwIBAgIERWtQVDANBgkqhkiG9w0BAQUFADCBsDELMAkGA1UEBhMC\r\n"
"VVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xOTA3BgNVBAsTMHd3dy5lbnRydXN0\r\n"
"Lm5ldC9DUFMgaXMgaW5jb3Jwb3JhdGVkIGJ5IHJlZmVyZW5jZTEfMB0GA1UECxMW\r\n"
"KGMpIDIwMDYgRW50cnVzdCwgSW5jLjEtMCsGA1UEAxMkRW50cnVzdCBSb290IENl\r\n"
"cnRpZmljYXRpb24gQXV0aG9yaXR5MB4XDTA2MTEyNzIwMjM0MloXDTI2MTEyNzIw\r\n"
"NTM0MlowgbAxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1FbnRydXN0LCBJbmMuMTkw\r\n"
"NwYDVQQLEzB3d3cuZW50cnVzdC5uZXQvQ1BTIGlzIGluY29ycG9yYXRlZCBieSBy\r\n"
"ZWZlcmVuY2UxHzAdBgNVBAsTFihjKSAyMDA2IEVudHJ1c3QsIEluYy4xLTArBgNV\r\n"
"BAMTJEVudHJ1c3QgUm9vdCBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTCCASIwDQYJ\r\n"
"KoZIhvcNAQEBBQADggEPADCCAQoCggEBALaVtkNC+sZtKm9I35RMOVcF7sN5EUFo\r\n"
"Nu3s/poBj6E4KPz3EEZmLk0eGrEaTsbRwJWIsMn/MYszA9u3g3s+IIRe7bJWKKf4\r\n"
"4LlAcTfFy0cOlypowCKVYhXbR9n10Cv/gkvJrT7eTNuQgFA/CYqEAOwwCj0Yzfv9\r\n"
"KlmaI5UXLEWeH25DeW0MXJj+SKfFI0dcXv1u5x609mhF0YaDW6KKjbHjKYD+JXGI\r\n"
"rb68j6xSlkuqUY3kEzEZ6E5Nn9uss2rVvDlUccp6en+Q3X0dgNmBu1kmwhH+5pPi\r\n"
"94DkZfs0Nw4pgHBNrziGLp5/V6+eF67rHMsoIV+2HNjnogQi+dPa2MsCAwEAAaOB\r\n"
"sDCBrTAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zArBgNVHRAEJDAi\r\n"
"gA8yMDA2MTEyNzIwMjM0MlqBDzIwMjYxMTI3MjA1MzQyWjAfBgNVHSMEGDAWgBRo\r\n"
"kORnpKZTgMeGZqTx90tD+4S9bTAdBgNVHQ4EFgQUaJDkZ6SmU4DHhmak8fdLQ/uE\r\n"
"vW0wHQYJKoZIhvZ9B0EABBAwDhsIVjcuMTo0LjADAgSQMA0GCSqGSIb3DQEBBQUA\r\n"
"A4IBAQCT1DCw1wMgKtD5Y+iRDAUgqV8ZyntyTtSx29CW+1RaGSwMCPeyvIWonX9t\r\n"
"O1KzKtvn1ISMY/YPyyYBkVBs9F8U4pN0wBOeMDpQ47RgxRzwIkSNcUesyBrJ6Zua\r\n"
"AGAT/3B+XxFNSRuzFVJ7yVTav52Vr2ua2J7p8eRDjeIRRDq/r72DQnNSi6q7pynP\r\n"
"9WQcCk3RvKqsnyrQ/39/2n3qse0wJcGE2jTSW3iDVuycNsMm4hH2Z0kdkquM++v/\r\n"
"eu6FSqdQgPCnXEqULl8FmTxSQeDNtGPPAUO6nIPcj2A781q0tHuu2guQOHXvgR1m\r\n"
"0vdXcDazv/wor3ElhVsT/h5/WrQ8\r\n"
"-----END CERTIFICATE-----\r\n"
;

/*
 * Silent Phone uses the following certificate chain to setup TLS with the
 * SIP servers of the development network.
 */
static const char *developmentCert =
// Silent Circle root certificate, serial number be:31:b0:4b:ae:49:f2:71, SHA1: E8:F3:C4:98
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
"-----END CERTIFICATE-----\r\n"

    // Entrust root certificate, serial number: 38:63:DE:F8, SHA1: 50:30:06:09
"-----BEGIN CERTIFICATE-----\r\n"
"MIIEKjCCAxKgAwIBAgIEOGPe+DANBgkqhkiG9w0BAQUFADCBtDEUMBIGA1UEChMLRW50cnVzdC5u\r\n"
"ZXQxQDA+BgNVBAsUN3d3dy5lbnRydXN0Lm5ldC9DUFNfMjA0OCBpbmNvcnAuIGJ5IHJlZi4gKGxp\r\n"
"bWl0cyBsaWFiLikxJTAjBgNVBAsTHChjKSAxOTk5IEVudHJ1c3QubmV0IExpbWl0ZWQxMzAxBgNV\r\n"
"BAMTKkVudHJ1c3QubmV0IENlcnRpZmljYXRpb24gQXV0aG9yaXR5ICgyMDQ4KTAeFw05OTEyMjQx\r\n"
"NzUwNTFaFw0yOTA3MjQxNDE1MTJaMIG0MRQwEgYDVQQKEwtFbnRydXN0Lm5ldDFAMD4GA1UECxQ3\r\n"
"d3d3LmVudHJ1c3QubmV0L0NQU18yMDQ4IGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTEl\r\n"
"MCMGA1UECxMcKGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDEzMDEGA1UEAxMqRW50cnVzdC5u\r\n"
"ZXQgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkgKDIwNDgpMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\r\n"
"MIIBCgKCAQEArU1LqRKGsuqjIAcVFmQqK0vRvwtKTY7tgHalZ7d4QMBzQshowNtTK91euHaYNZOL\r\n"
"Gp18EzoOH1u3Hs/lJBQesYGpjX24zGtLA/ECDNyrpUAkAH90lKGdCCmziAv1h3edVc3kw37XamSr\r\n"
"hRSGlVuXMlBvPci6Zgzj/L24ScF2iUkZ/cCovYmjZy/Gn7xxGWC4LeksyZB2ZnuU4q941mVTXTzW\r\n"
"nLLPKQP5L6RQstRIzgUyVYr9smRMDuSYB3Xbf9+5CFVghTAp+XtIpGmG4zU/HoZdenoVve8AjhUi\r\n"
"VBcAkCaTvA5JaJG/+EfTnZVCwQ5N328mz8MYIWJmQ3DW1cAH4QIDAQABo0IwQDAOBgNVHQ8BAf8E\r\n"
"BAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUVeSB0RGAvtiJuQijMfmhJAkWuXAwDQYJ\r\n"
"KoZIhvcNAQEFBQADggEBADubj1abMOdTmXx6eadNl9cZlZD7Bh/KM3xGY4+WZiT6QBshJ8rmcnPy\r\n"
"T/4xmf3IDExoU8aAghOY+rat2l098c5u9hURlIIM7j+VrxGrD9cv3h8Dj1csHsm7mhpElesYT6Yf\r\n"
"zX1XEC+bBAlahLVu2B064dae0Wx5XnkcFMXj0EyTO2U87d89vqbllRrDtRnDvV5bu/8j72gZyxKT\r\n"
"J1wDLW8w0B62GqzeWvfRqqgnpv55gcR5mTNXuhKwqeBCbJPKVt7+bYQLCIt+jerXmCHG8+c8eS9e\r\n"
"nNFMFY3h7CI3zJpDC5fcgJCNs2ebb0gIFVbPv/ErfF6adulZkMV8gzURZVE=\r\n"
"-----END CERTIFICATE-----\r\n"

;

// Default certificate: use the production network
static const char *sipCert = productionCert;

/**
 * @brief Set SIP link and certificate for use in development network.
 * 
 * The Tivi development SIP server uses the production certificates.
 */
void setSipToDevelop() {
    sipCert = productionCert;  // developmentCert;
}

/**
 * @brief Set SIP link and certificate for use in production network.
 */
void setSipToProduction() {
    sipCert = productionCert;
}


#include <stdarg.h>

void tivi_slog(const char* format, ...)
#if defined(_WIN32) || defined(__linux__)  || defined(__APPLE__)
{
   
   {
      va_list arg;
      va_start(arg, format);
      vprintf(format,arg);
#if defined(ANDROID_NDK)
      androidLog(format, arg); // __android_log_vprint(ANDROID_LOG_DEBUG, "tivi-log", format, arg);
#endif
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


CTSipSock::~CTSipSock(){

   iExiting=1;
   CTMutexAutoLock al(testTCP_TLSMutex);
   closeSocket();
   Sleep(20);
   //tcpPrev=tcp;
   if(tls)delete tls;
   if(tcp)delete tcp;
   tcp=NULL;
   tls=NULL;
   addToDelete(-1,NULL); //clean prev sockets
}

void CTSipSock::del_sock(DEL_SOCK *s){
   if(!s)return;
   if(s->iIsTLS && s->sock && ((CTTLS*)(s->sock))->iCallingConnect)return;
      
   s->uiDeleteAT=0;
   tivi_slog("DEL_SOCK");
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
   
   if(!iEmptyCnt)log_events( __FUNCTION__,"[WARN: addToDelete: iEmptyCnt=0]");//TODO next to delete
   
   for(int i=0;i<eDelSocketCnt;i++){
      
      if(!deleteSock[i].uiDeleteAT && !deleteSock[i].sock){
         deleteSock[i].uiDeleteAT = uiNow + 10000;
         deleteSock[i].sock=s;
         deleteSock[i].iIsTLS=iIsTLS;
         return;
      }
   }
   t_logf(log_events, __FUNCTION__,"Should not be here, trying to delete socket");
   
   if(iIsTLS)delete (CTTLS*)s;
   else delete (CTSockTcp*)s;
}

CTSipSock::CTSipSock(CTSockCB &c):CTSockBase(),udp(c),tcp(NULL),sockCB(c),tls(NULL)
{
   memset(&deleteSock[0],0,sizeof(DEL_SOCK)*eDelSocketCnt);
   addr.clear();
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
   
   if(strcmp(p,"TLS")==0 || mustUseTLS()){setSockType(eTLS);}
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
      if(r>=0){
         r=Bind(addrToBind,toAny);
      }
      else{
         addr = *addrToBind;
      }
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
   t_logf(log_events, __FUNCTION__, "sockdebug: Creating a new socket binding");
   iFlagRecreate=0;
   if(iExiting)return -1;
   if(iType==eUDP){if(iBind)udp.Bind(addr.getPort(),1);return udp.createSock();}
   
   int z;
   for( z=0;z<30 && iIsReceiving;z++)Sleep(15);
   if(iExiting)return -1;
   
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
      t_logf(log_events, __FUNCTION__, "sockdebug: Creating a new TLS socket binding");
      tlsPrev=tls;
      iTlsIsSent=0;
      iIsSuspended=1;
      CTTLS *n=new CTTLS(sockCB);
      SOCKET r=n->createSock();
      if(iBind)Bind(addr.getPort(),1);
      tls=n;
      if(tlsPrev){
         t_logf(log_events, __FUNCTION__, "sockdebug: Closing the previous TLS socket binding");
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
 
void CTSipSock::checkCert(ADDR *address){
   //TODO T_getSometing(NULL,"sipCertFN","getBySock",this);
   //or T_getSometing(NULL,"sipCertFN","getByServ",&address->bufAddr[0]);

    int iLen=strlen(sipCert);
    int showSSLErrorMsg(void *ret, const char *p);

    tls->errMsg = &showSSLErrorMsg;
    tls->pRet = this;

    if (errMsgTLS)
        tls->errMsg = errMsgTLS;
    if (pRetTLS)
        tls->pRet = pRetTLS;

    char bufZ[sizeof(address->bufAddr)];
    char *bufA = &address->bufAddr[0];
    int i = 0;
    for(; i < sizeof(bufZ)-1; i++){
        if(bufA[i]==':' || !bufA[i]) {
            break;
        }
        bufZ[i] = bufA[i];
    }
    bufZ[i] = 0;

    ADDR ax = bufZ;
    if(!ax.ip) {                           //DONT
        tls->setCert(const_cast<char *>(sipCert), iLen, bufZ);
    }
}

int CTSipSock::closeSocket(){
   if(iIsSuspended){Sleep(20);return 0;}
   if(iType==eUDP)return udp.closeSocket();
   if(iType==eTCP && tcp){return tcp->closeSocket();}
   if(iType==eTLS && tls){return tls->closeSocket();}
   return T_UNKNOWN_SOCK_RET;
}




int CTTCP_TLS_SendQ::addToQueue(const char *buf, int iLen, ADDR *address, int iSockType, int iFromSIPThread)
{
   // mq.
 //nedriikst saglabaat sock

   if(iLen<11)return 0;//ignore udp keepalive or any invalid SIP packet
   static long long s_msg_id=1;
    
   for(int i=0;i<Q_CNT;i++){
      
      AA_SEND_Q *p=&sendQ[i];
      if(!p->iBusy)continue;
      if(p->iSockType!=iSockType || *address!=p->a){
         p->uiTS=0;
         p->iBusy=0;
         continue;
      }
      //chekc addr and content if the same skip
      if(p->iSockType==iSockType && iLen==p->iLen &&  p->a ==*address && memcmp(p->buf,buf,iLen)==0){
         p->uiTS=getTickCount();
         return 0;
      }
   }
   for(int z=0;z<2;z++){
      if(z)iPrevAddedToQuevePos=0;
      for(int i=iPrevAddedToQuevePos;i<Q_CNT;i++){

         AA_SEND_Q *p=&sendQ[i];
         
         if(!sendQ[i].iBusy){
            p->iSentFromSIPThread = iFromSIPThread;
            p->msg_id = s_msg_id;s_msg_id++;
            p->iSockType=iSockType;
            p->iBusy=ePrepearing;
            p->uiTS=getTickCount();
            if(iLen>sizeof(p->buf)-1){
               iLen=sizeof(p->buf)-1;
               log_events(__FUNCTION__,"AQ: send buf is too small");
            }
            
            memcpy(p->buf,buf,iLen);
            memcpy(&p->a,address,sizeof(ADDR));
            p->iLen=iLen;
            p->iBusy=eReadyToSend;
            iPrevAddedToQuevePos=i+1;
            
            return i;
         }
      }
   }
   return -1;
}

//TODO rem prevCall msg from quve
void CTSipSock::emptyQueue(){
   if(iExiting)return ;
   for(int i=0;i<Q_CNT;i++){
      CTTCP_TLS_SendQ::AA_SEND_Q *q=&sq.sendQ[i];
      if(q->iBusy)q->iBusy=0;
   }
   
}

CTTCP_TLS_SendQ::AA_SEND_Q *CTTCP_TLS_SendQ::getOldest(int mustBeFromSIPThread){
   AA_SEND_Q *ret=NULL;
   
   int pp=iPrevSendPos;
   int sp=0;
   
   int i, iCnt = 0;
   
   for(int z = 0 ; z < 2 ; z++){
      
      if(z){if(!pp)break;pp=0;}
      
      for(i = pp ; i < Q_CNT ; i++){
         iCnt++;
         
         if(iCnt > Q_CNT){
            break;
         }
         
         AA_SEND_Q *q=&sendQ[i];
         
         if(q->iBusy==CTTCP_TLS_SendQ::eReadyToSend && (!q->iSentFromSIPThread || q->iSentFromSIPThread == mustBeFromSIPThread)){
            
            if(!ret){ret = q;sp = i;continue;}
            if(q->msg_id  < ret->msg_id){ret = q;sp = i;}//select older first
            continue;
         }
      }
      
   }
   if(ret)sp=iPrevSendPos+1;

   return ret;
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



int CTSipSock::sendQueue(int isSIPRecvThread){
   
   if(iExiting)return -1;
   
   if(iIsSuspended){Sleep(20);return -1;}
   
   if(iSending){Sleep(5);return 0;}
   
   CTAutoIntUnlock _s(&iSending);

   unsigned int uiTS=getTickCount();
   int c=0,sent=0;;

   int failCount=0;
   
   int i;

   int iErrFlag=0;

   for(i=0;i<Q_CNT;i++){
   
      CTTCP_TLS_SendQ::AA_SEND_Q *q=sq.getOldest(isSIPRecvThread);
   
      if(iExiting || !q)break;
   
      if(q->iBusy==CTTCP_TLS_SendQ::eReadyToSend){
         
         q->iBusy=CTTCP_TLS_SendQ::eSending;

         int d = (int)(uiTS-q->uiTS);
         if(d>8000){
            iErrFlag|=1;
            //timeout
            if(q->uiTS && q->a.ip)
               t_logf(log_events, __FUNCTION__,"e-timeout l=%d ofs=%d\n",q->iLen,d);
            else
               sent++;//??
         }
         else if(q->iSockType!=this->iType){
            log_events( __FUNCTION__,"e-socktype");
            iErrFlag|=2;
         }
         else {
            int inQ = getTickCount()-q->uiTS;
            if(inQ > 1000){
               t_logf(log_events, __FUNCTION__,"[send %s [%.*s] %d]",q->a.bufAddr, 30, q->buf, inQ);
            }
            int ret=sendToReal(q->buf, q->iLen, &q->a);
            
            if(ret<0){
               uiTS=getTickCount();
               iErrFlag|=4;
               failCount++;
               q->iBusy=0;
               t_logf(log_events, __FUNCTION__,"sendToReal()=%d t=%dms\n",ret, uiTS-q->uiTS);
               Sleep(30);
            }
            else sent++;
         }
         
         q->iBusy=CTTCP_TLS_SendQ::eNotInUse;
         c++;
         if(sent>16 || (sent>8 && failCount) || failCount>3 || (failCount && getTickCount()-uiTS>3000))break;
      }
   }

   if((failCount && c) || iErrFlag) {
      t_logf(log_events, __FUNCTION__,"WARN: send quve %d %d %d e%d\n",c,sent,failCount,iErrFlag);
   }
   return c;
}

int CTSipSock::sendTo(const char *buf, int iLen, ADDR *address, int iFromSIPThread){
   
   if(iLen > 10){ //ignore SIP keepalive
      t_logf(log_sip, __FUNCTION__, "ADDR=%08x LEN=%d\n[%.*s]",address->ip, iLen, iLen, buf);
   }
   
   if(iType==eUDP){
      return sendToReal(buf,iLen,address);
   }
   if(sq.addToQueue(buf,iLen,address,iType, iFromSIPThread)<0){
      //this will never happen, queue is big enough
      return -1;
   }
   return iLen;
}

void CTSipSock::forceRecreate(){
   t_logf(log_events, __FUNCTION__, "sockdebug: Setting flags to force socket recreation");
   iFlagRecreate|=2;
   iFlagRecreate &= ~4;
}

int CTSipSock::sendToReal(const char *buf, int iLen, ADDR *address){
    if(iExiting)return -1;
    if(iIsSuspended){Sleep(20);return -2;}
   

    if(iType==eUDP){
        return udp.sendTo(buf,iLen,address);
    }
    if(iLen==1)return 1;

    CTMutexAutoLock al(testTCP_TLSMutex); 
 
    if(iExiting)return -1;

    if ((iFlagRecreate && (iFlagRecreate&4)==0) || (iType==eTLS && tls && (tls->isClosed() || !tls->isConected())) ||
        (iType==eTCP && (!tcp || ((tcp && tcp->getAddrConnected().ip && tcp->getAddrConnected()!=*address))))  ||
        (iType==eTLS && (!tls || ((tls && tls->getAddrConnected().ip && tls->getAddrConnected()!=*address))))
    ) {
        t_logf(log_events, __FUNCTION__, "sockdebug: Some kind of disconnection detected, may need to recreate socket");
        if(iFlagRecreate)t_logf(log_events,__FUNCTION__, "iFlagRecreate=%x",iFlagRecreate);
        else if(iType==eTCP){
            if(tcp)t_logf(log_events, __FUNCTION__, "[tcp %d=%d,%d=%d]",tcp->getAddrConnected().ip,address->ip,tcp->getAddrConnected().getPort(),address->getPort());
        }
        else if(iType==eTLS){
            int isRelease();

            if(tls && !isRelease()){
                char bufA[32];
                char bufB[32];
                tls->getAddrConnected().toStr(&bufA[0]);
                address->toStr(&bufB[0]);
                t_logf(log_events, __FUNCTION__, "[tls conn=%s dst=%s]",bufA, bufB);
            }
        }

        iFlagRecreate |= 4;
        iTcpIsSent=0;
        iTlsIsSent=0;
        //  closeSocket(); //createSockBind deletes prev one
        Sleep(20);

        int r = createSockBind(1);
        t_logf(log_events, __FUNCTION__, "_recr = %d", r + iType*100);
        Bind(addr.getPort(),1);
    }
    if(iType==eTCP){

        if(tcp){
            if(!tcp->isConected()){tcp->_connect(address);}
            if(tcp->isConected()){
                t_logf(log_events, __FUNCTION__, "[tcp-connected]");
                if(iLen<20){
                    iLen=0;//do not send keeplive
                    return 0;//tcp->_send("\r\n\r\n",0);//test
                }
                if(!iTcpIsSent){
                    iTcpIsSent=1;
                    Sleep(60);//wait recv starts listen
                }
                int r=tcp->_send(buf,iLen);
                if(iLen>0)t_logf(log_events, __FUNCTION__,"sent[%d]-tcp\n[%.*s]",r,min(iLen,10),buf);
                if(r<0){
                    t_logf(log_events, __FUNCTION__,"[tcp recreate]");
                    iFlagRecreate|=2;
                    iFlagRecreate &= ~4;
                }
                return r;
            }
            else return -5;
        }
        else return -4;
    }

    if (iType==eTLS) {
        if (tls) {
            if (!tls->isConected() && iLen > 0) {
                tls->enableBackgroundForVoip(1);
                checkCert(address);
                t_logf(log_events, __FUNCTION__, "tls-connect");
                tls->_connect(address);
            }
            if (tls->isConected()){
                int r=0;
                if(iLen<20){
                    iLen=0;//do not send keeplive
                }
                else {
                    r=tls->_send(buf,iLen);
                    if (iLen > 0)
                        t_logf(log_events, __FUNCTION__,"sent[%d]-tls [%.*s]", r, min(iLen, 20), buf);

                    if (r < 0) {
                        t_logf(log_events, __FUNCTION__,"f-recr err:%d",r);
                        iFlagRecreate|=2;//ok
                        iFlagRecreate&=~4;
                    }
                    else if (!iTlsIsSent){
                        iTlsIsSent=1;
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
         t_logf(log_events, __FUNCTION__,"[rec-tcp-err %d]",rec);
         if(rec==0){
            t_logf(log_events, __FUNCTION__,"[recFrom2 recreate]");
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
         if(tls->peerClosed()==1 && iTlsIsSent){
            log_events( __FUNCTION__, "[tls->peerClosed() recreate]");
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

