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
#ifndef _C_T_TLS_H
#define _C_T_TLS_H
#include "../../os/CTiViSock.h"

class CTTLS{
public:
   CTTLS(CTSockCB &c);
   ~CTTLS();
   void initEntropy();
   int createSock(ADDR *addrToBind, int toAny){return createSock();}
   int setNewPort(int port){addr.setPort(port);return 0;}//set local
   void reCreate();
   int createSock();
   int closeSocket();
   int _connect(ADDR *address, ADDR *preferedAddr = NULL);
   int _send(const char *buf, int iLen);
   int _recv(char *buf, int iMaxSize);
   inline int Bind(int port, int toAny) {addr.setPort(port); return 0;}
   inline int Bind(ADDR *addrToBind, int toAny){addr=*addrToBind;return 0;}
   inline int isConected(){return iConnected && addrConnected.ip;}//TODO check ssl sock
   ADDR &getAddrConnected(){return addrConnected;}
   ADDR addr;
   int (*errMsg)(void *pRet, const char *err);
   void *pRet;
   void setCert(char *p, int iLen, char *host);
   inline int peerClosed(){int r=iPeerClosed;if(r)iPeerClosed=3;return r;}
   int getInfo(const char *key, char *p, int iMax);
   
   int isClosed(){return iClosed;}
   int wasConnected(){return iWasConnected;}
   
   void enableBackgroundForVoip(int bTrue);
   
   int iCallingConnect;
private:
   int iEntropyInicialized;
   int iCertFailed;
   void failedCert(const char *err, const char *descr, int fatal);
   int checkCert();
   char bufCertHost[256];
   char *cert;
   int iPeerClosed;
   int iClosed;
   int iConnected;
   int iNeedCallCloseSocket;
   int iPrevReadRet,iPPrevReadRet;
   ADDR addrConnected;
   void *pSSL;
   char bufErr[256];
   int iWaitForRead;
   int iDestroyFlag;
   int iWasConnected;
   
   int bIsVoipSock;
};

#endif
