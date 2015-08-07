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

#ifndef _C_T_SIP_SOCK_H
#define _C_T_SIP_SOCK_H

#include "../os/CTiViSock.h"
#include "../os/CTThread.h"
#include "../os/CTTcp.h"
#include "../encrypt/tls/CTTLS.h"
#include "../os/CTMutex.h"

#include "../tiviengine/main.h"

class CTTCP_TLS_SendQ{
#define Q_CNT 32
   
   int iPrevAddedToQuevePos;
   

public:
   enum {eNotInUse=0, ePrepearing, eReadyToSend, eSending };
   
   typedef struct{
      int iBusy;
      unsigned int uiTS;
      long long msg_id;
      
      int *onSentCB(void *p, int iLen);//TODO
      void *pRet;
      int iSockType;
      int iLen;
      ADDR a;
      
      char buf[DATA_SIZE];//TODO must be DATA_SIZE
   }AA_SEND_Q;
   
   AA_SEND_Q sendQ[Q_CNT];
   int iPrevSendPos;
   
   CTTCP_TLS_SendQ(){
      for(int i=0;i<Q_CNT;i++)
         memset(&sendQ[i],0,sizeof(AA_SEND_Q));
      iPrevAddedToQuevePos=0;
      iPrevSendPos=0;
   }
   
   
   int addToQueue(const char *buf, int iLen, ADDR *address, int iSockType);
   
   CTTCP_TLS_SendQ::AA_SEND_Q *getOldest();
};


class CTSipSock: public CTSockBase{
   int iType;
   int iPrevUsedType;
   int iIsSuspended;
   int iSending;
   CTSockCB sockCB;
   typedef struct{
      unsigned int uiDeleteAT;
      int iIsTLS;
      void *sock;
   }DEL_SOCK;
   
   enum{eDelSocketCnt=64};
   DEL_SOCK deleteSock[eDelSocketCnt];
   
   void addToDelete(int iIsTLS, void *s);
   
public:
   enum{eUnknown,eUDP,eTCP,eTLS};
   
   CTSipSock(CTSockCB &c);
   ~CTSipSock();
   

   
   void setTLSErrCB(int (*fnc)(void *pRet, const char *err), void *pRet){
      errMsgTLS=fnc;
      pRetTLS=pRet;
   }
   
   
   void setSockType(const char *p);
   void setSockType(int iNewType);

   
   inline int isTCP(){return iType==eTCP;}
   inline int isTLS(){return iType==eTLS;}
//   static int getHostByName(char *psz,int iLen=0);
   int createSock(ADDR *addrToBind, BOOL toAny);
   int setNewPort(int port);
   int getInfo(const char *key, char *p, int iMax);

   void reCreate();
   SOCKET createSock();
   SOCKET createSockBind(int iBind);
   void checkCert(ADDR *address);
   int closeSocket();
   int sendQueue();
   int sendToReal(const char *buf, int iLen, ADDR *address);
   int sendTo(const char *buf, int iLen, ADDR *address);
   int recvFrom(char *buf, int iLen, ADDR *address);
   inline int Bind(int port, BOOL toAny)
   {
      ADDR a;
      a.setPort(port);
      return Bind(&a,toAny);
   }
   int Bind(ADDR *addrToBind, BOOL toAny);
   ADDR addr;
   CTSock &getUdp(){return udp;}
   
   int needRecreate(int iClearFlag){
      int r=iFlagRecreate;
      if(iClearFlag)iFlagRecreate=0;
      return r;//;
   }
   void emptyQueue();
   
   void forceRecreate();
   
private:
   void del_sock(DEL_SOCK *s);
   int (*errMsgTLS)(void *pRet, const char *err);
   void *pRetTLS;
   
   int iTcpIsSent;
   int iTlsIsSent;
   int iExiting;
   int iFlagRecreate;

   CTSock udp;
   CTSockTcp *tcp;
   CTSockTcp *tcpPrev;
   CTTLS *tlsPrev;
   CTTLS *tls;
   char tmpBuf[4096*2];//
   int iBytesInNextTmpBuf;
   int iIsReceiving;
   CTTCP_TLS_SendQ sq;

   CTMutex testTCP_TLSMutex;
};
#endif