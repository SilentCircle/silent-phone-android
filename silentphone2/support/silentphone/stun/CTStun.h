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

#ifndef _C_T_STUN_H
#define _C_T_STUN_H
//#define DBG_PRF printf
//#define DBG_PRF(_A,_B)
static inline void DBG_PRF(const char * a,...){}
#include "../utils/utils.h"
#include "../os/CTiViSock.h"
#include "../os/CTThread.h"

unsigned int getTickCount();


#ifdef __SYMBIAN32__
  #ifndef Sleep
#define Sleep(_a_) User::After(1000*(_a_))
   #define GetTickCount() User::FastCounter()
  #endif
#endif

#if defined(_WIN32) && !defined(__SYMBIAN32__) && !defined(_WIN32_WCE)
#define USE_FAST_COUNTER
#endif

class CTStun{
   CTSockCB *cbsock;//TODO
   CTSockRecvCB olSockCb;
   int iCanTransferToOld;
   
   CTSockRecvCB mySockCB;
   
public:
   enum E_NAT_TYPE{
      NO_NAT=0,
      FULL_CONE=1,
      PORT_REST=2,
      REST_NAT=4,
      SYMMETRIC=8,
      MOB_NAT=16,
      NO_IP_STUN_OFF=0x02000000,
      HAS_IP_STUN_OFF=0x04000000,
      NOT_DETECTDED=0x08000000,
      FIREWALL=0x0f000000,

   };
   enum{EStunHdrLen =20};
   enum{eMaxResendCnt=40};
   enum{eDefaultPort = 3478};

   CTStun(int ipBinded,CTSockCB &cb, const ADDR &a=ADDR() )
      :cbsock(&cb),sock(NULL),iSockCreated(0),addrToBind(a),olSockCb(NULL,NULL),mySockCB(isStunResponceS,this)
   {
      iCanTransferToOld=0;
   }

   CTStun(int ipBinded, CTSock *s)
      :sock(s),iSockCreated(0),addrToBind(s->addr),olSockCb(NULL,NULL),mySockCB(isStunResponceS,this)
   {
      iCanTransferToOld=0;
   }
   
   static int isNatWithoutFW(int type){
      return type & (NO_NAT|FULL_CONE|PORT_REST|REST_NAT|SYMMETRIC);
   }
   
   static const char *getNatName(int type){
      switch(type){
         case CTStun::NO_NAT:    return "open internet";
         case CTStun::FULL_CONE: return "full cone nat";
         case CTStun::PORT_REST: return "port restricted nat";
         case CTStun::REST_NAT: return "restricted nat";
         case CTStun::SYMMETRIC: return "symmetric nat";
         case CTStun::NOT_DETECTDED:return "not detected";
         case CTStun::FIREWALL: return "firewall(Bad, try different access point or network)";
         case CTStun::NO_IP_STUN_OFF: return "No network - stun off";
         case CTStun::HAS_IP_STUN_OFF: return "stun off";
      }
      return "Unknown";
   }
   
protected:
   void constr()
   {
      iMaxCheckTime=5000;
      addrExt1.clear();
      addrExt2.clear();
      iMaxResend=eMaxResendCnt;
      iActive=0;
      iHasStunResp=iListen=iSendSeq=0,iSendLen=0,iPingTime=0,iSendCnt=0;
      memset(iPingTimeStartN,0,sizeof(iPingTimeStartN));
      iTimeStamp=iNextResend=0;
      iNatType=(int)FIREWALL;

      iGetExtAddrOnly=0;
      if(sock==NULL)
      {
         
         sock = new CTSock(*cbsock);
         iSockCreated = 1;
         sock->createSock(&addrToBind,true);
         sock->addr.ip = addrToBind.ip;

      }
      else
      {
         //
        // sock->setCB(CTSockRecvCB(isStunResponceS,this),olSockCb);
         mySockCB.iRestorable = 0;
         sock->setCB(mySockCB,olSockCb,0);
         iCanTransferToOld = olSockCb.iRestorable;
         
      }
      unsigned int uiTC = getTickCount();

      id[0]=((unsigned long long)this)&0xffffffff;//TODO +CTThread::id()+CTThread::processId();
      id[1]=uiTC+(((unsigned long long)sock)&0xffffffff)+(unsigned int)ipBinded;

   }
public:
   ~CTStun(){
      if(iSockCreated)
      {
         delete sock;
      }
      else if(sock)
      {
         sock->tryRestoreCB(mySockCB,iCanTransferToOld ? &olSockCb: NULL);
      }
   }

   static int isStunResponceS(char *p, int iLen, ADDR *a, void *pUser)
   {
    //  printf("isStunResponceS(%p %d %p)",p, iLen,pUser);
      int ret;
      if(pUser)
      {
         ret=((CTStun*)pUser)->isStunResponce(p,iLen,a);
         if(ret==0 && ((CTStun*)pUser)->iCanTransferToOld)
            return ((CTStun*)pUser)->olSockCb(p,iLen,a);
      }
      return -1;
   }
/*
   static int onRcv(char *p, int iLen,ADDR *a, void *pUser)
   {
      if(iLen<13)return -1;
      if(pUser)return ((CRTPA*)pUser)->onData(p,iLen,a);
      return -1;
   }
   public:
   CRTPA(CSessionsBase &eng, CTAudioEngine &cb, int iCreateSock=1)
      :CTSesMediaBase(eng)
       ,cb(cb),sockth(*cb.cbSock,(char *)&rtp.rtpRec.dataBuf, sizeof(rtp.rtpRec.dataBuf))
   {
      uiMaxSize=4096*2;//
      bufPcm=new char [uiMaxSize+1];
      if(iCreateSock)
      {
         ADDR a;
         a.setPort((unsigned int)eng.p_cfg.iRtpPort);
         sockth.start(CTSockRecvCB(onRcv,this),&a);
      }
   }
*/
   int isStunResponce(char *p, int iLen, ADDR *a)
   {
      //printf("rec stun %d\n",iLen);
      if(iActive==0 || iLen<28 || iLen>80)return 0;
      //TODO check resp id[]
     // printf("rec stun ID %d\n",*(unsigned int*)(p+16)!=id[1]);
      
      if(*(unsigned int*)(p+12)!=id[0] || *(unsigned int*)(p+16)!=id[1])
         return 0;
      
      //printf("rec stun %d==%d\n",iSendSeq, *(int*)(p+4));

      if(*(int*)(p+4)!=iSendSeq)
         return 0;
      if(a->ip!=addrStun.ip && a->ip!=addrStun2.ip)
         return 0;
      
      
      if(parse(p,iLen)==0){
         int n=*(int*)(p+8);
         if(n+1>=sizeof(iPingTimeStartN)/sizeof(int))n=0;

         if(iPingTimeStartN[n] && iLen>20)
         {
            
            int nt= (int)(getTickCount()-iPingTimeStartN[n])+1;
            if((nt<iPingTime && nt>0)|| !iPingTime){
               iPingTime=nt;
               if(iPingTime<=0)iPingTime=3;
               else if(iPingTime>10000)iPingTime=10000;
            }
         }
         return 1;
      }
      return 0;
   }
   int getNatType()
   {
      if(!addrStun.ip)return 0;
      constr();
      iListen=0;
      iActive=1;
      iGetExtAddrOnly=0;
      send();
      return _thFncResend(this);
   }
   int getNatTypeListen()
   {
      ADDR recAddr;
      int rec;
      char buf[100];
      constr();
      iListen=0;
      iActive=1;
      iGetExtAddrOnly=0;

      if(th.create(&_thFncResend,this))
         return -1;
      int iCounter=0;

      while(th.iInThread==0 && iCounter<100)
      {
         Sleep(15);
         iCounter++;
      }
      CTSockRecvCB ocb(NULL,NULL);
      sock->setCB(olSockCb,ocb);
      send();
      iListen=1;
      while(iActive)
      {
         //listen
         rec=sock->recvFrom(buf,100,&recAddr);
         if(rec<=0)
         {
            Sleep(1);
            continue;
         }
         if(!isStunResponce(buf,rec,&recAddr))continue;
      }
      iActive=0;
      th.wait();
      return 0;
   }
   int getExtAddrListen(int iGetAddrOnly=0)
   {
      ADDR recAddr;
      int rec;
      char buf[100];
      constr();
      iActive=1;
      int iCounter=0;
      iGetExtAddrOnly=iGetAddrOnly;
      if(th.create(&_thFncResend,this))
         return -1;

      while(th.iInThread==0 && iCounter<100)
      {
         Sleep(15);
         iCounter++;
      }
      iListen=1;
      send();
      while(iActive)
      {
         //listen
         rec=sock->recvFrom(buf,100,&recAddr);
         if(rec<=0)
         {
            Sleep(1);
            continue;
         }
         if(!isStunResponce(buf,rec,&recAddr))continue;
      }
      iActive=0;
      th.wait();
      return 0;
   }
   int getExtAddr()
   {
      if(!addrStun.ip)return 0;

      constr();
      iListen=0;
      iActive=1;
      iGetExtAddrOnly=1;
      if(iPingTime)iMaxCheckTime=iPingTime*2+1000;
      if(iMaxCheckTime>3000)iMaxCheckTime=3000;
      send();
      return _thFncResend(this);
   }

   void setInActive()
   {
      if(iActive)
      {
         iActive=0;
         iNextResend=0;
         if(iListen)
         {
            sock->sendTo("1",1,&sock->addr);
         }
      }
   }
protected:
   int thFncResend(){
      
      // Sleep(1);
      //#define ST_RESEND_CNT 4
#define ST_RETRANSMIT_MS 50
      iTimeStamp=0;
      if(iNextResend==0)
         iNextResend=ST_RETRANSMIT_MS;
      Sleep(10);
      iTimeStamp=10;
      
      //self->send();
      
      while(iActive && iTimeStamp<iMaxCheckTime)
      {
         
         if(iNextResend && iNextResend<iTimeStamp)
         {
            if(iMaxResend<=0)
            { 
               if(iSendSeq==1)//nav full cone
               {
                  iSendSeq=2;// suutiit uz st2
                  iMaxResend=eMaxResendCnt;
                  send();
               }

               else if(iSendSeq==3)
               {
                  iNatType= PORT_REST;
                  setInActive();
               }
               
               else 
               {
                  printf("[stun_max_resend ss=%d]\n",iSendSeq);
                  setInActive();
                  break;
               }
            }
            else
            {
               send();
               iMaxResend--;
            }
         }
         //printf("%d\n",send());
         
         Sleep(20);//TODO
         iTimeStamp+=20;
      }
      if(iActive){
          printf("[stun_iActive ss=%d t=%d]\n",iSendSeq,iTimeStamp<iMaxCheckTime);
      }
      setInActive();
      iActive=0;
      return 0;
      
   }
   
   static int _thFncResend(void *p)
   {
      CTStun *self=(CTStun*)p;
      return self->thFncResend();
   }
   int checkState()
   {
      //char bufTmp[32];
      iHasStunResp=1;
      do{ 
         if(!iActive)break;
         if((int)addrExt1.ip==ipBinded)
         {
            iNatType=NO_NAT;
            setInActive();
            break;
         }
         if(addrExt2.ip==0 && addrStun2.ip)
         {
            iMaxResend=eMaxResendCnt/4+5;
            iSendSeq=1;
            //change ip, port  , if(rec resp full_cone) end
            send();
            break;

         }
         if(addrExt2.ip)
         {
            if(addrExt1==addrExt2)
            {
               if(iSendSeq==1)
               {
                  iNatType=FULL_CONE;
                  setInActive();
               }
               else if(iSendSeq==2)
               {
                  iSendSeq=3;//s1  change  port
                  iMaxResend=eMaxResendCnt/2;
                  send();
               }
               else if(iSendSeq==3)
               {
                  iNatType=REST_NAT;
                  setInActive();
               }
            }
            else
            {
               iNatType=SYMMETRIC;
               setInActive();
            }
            
            break;
         }
      }while(0);
      
      if(iActive==0)
      {
         //DBG_PRF("nat:%s\n",getNatName(iNatType));
      }

      return 0;
   }
   unsigned short t__htons(int s)
   {
      return ((s<<8)&0xff00)|((s>>8)&0xff);
   }

   int send()
   {
      ADDR *a=&addrStun;
      
      if(addrStun.getPort()==0)
         addrStun.setPort(eDefaultPort);
      
      *(short*)bufSend=0x0100;
      *(int*)(bufSend+4)=iSendSeq;
      iSendCnt++;
      *(int*)(bufSend+8)=iSendCnt;
      *(unsigned int *)(bufSend+12)=id[0];
      *(unsigned int *)(bufSend+16)=id[1];
      iSendLen=EStunHdrLen;
      //char bufTmp[64];
     // bufTmp[0]=0;


      if(iSendSeq==1)
      {
         iSendLen+=8;
         *(short*)(bufSend+EStunHdrLen)=0x0300;
         *(short*)(bufSend+EStunHdrLen+2)=0x0400;//len
         *(int*)(bufSend+EStunHdrLen+4)=0x06000000;//chang ip port
        // strcpy(bufTmp,"Change ip port");

      }
      else if(iSendSeq==3)
      {
         a=&addrStun;
         iSendLen+=8;
         *(short*)(bufSend+EStunHdrLen)=0x0300;
         *(short*)(bufSend+EStunHdrLen+2)=0x0400;//len
         *(int*)(bufSend+EStunHdrLen+4)=0x02000000;//change port
      }
      else
      if(iSendSeq==2)
      {
         a=&addrStun2;
      }

      *(short*)(bufSend+2)=t__htons(iSendLen-EStunHdrLen);
//      DBG_PRF("%s send %s\n",bufTmp,a->toStr(bufTmp+30));
      addrSend=*a;

      if(iPingTime>0 && iPingTime*2<ST_RETRANSMIT_MS)
         iNextResend=iTimeStamp+(ST_RETRANSMIT_MS>>1);
      else
         iNextResend=iTimeStamp+ST_RETRANSMIT_MS;
      
      int n=iSendCnt;
      if(n+1>=sizeof(iPingTimeStartN)/sizeof(int))n=0;

      if(iPingTimeStartN[n]==0)
      {
         iPingTimeStartN[n]=getTickCount();//li.LowPart;
      }
      
   // char b[64];  printf("[send stun  a=%s %p l=%d]\n", a->toStr(b,1),bufSend,iSendLen);
      return sock->sendTo(bufSend,iSendLen,a);
   }

   int parse(char *p, int iLen)
   {
      int iRecLen=0;
      if(iLen<20)return -1;
      short id=*(short*)p;
      if(id!=0x0101)return -2;
      p+=2;
      int iParamLen;
      ADDR *a=&addrExt1;
 

      iRecLen=*(short*)p;
      iRecLen=(int)t__htons((unsigned short)iRecLen);
      if(iRecLen+EStunHdrLen!=iLen)return -3;
      p+=EStunHdrLen-2;
     // iRecLen-=EStunHdrLen;

      while(iRecLen>=4 && iActive)
      {
         id=*(short*)p;
            p+=2;

         iParamLen=*(short*)p;
         iParamLen=(int)t__htons((unsigned short)iParamLen);
         p+=2;
         iRecLen-=4;
         iRecLen-=iParamLen;
         DBG_PRF("%x\n",id);

     //    printf("stun id=%d l=%d iGetExtAddrOnly=%d\n",id, iParamLen,iGetExtAddrOnly);

         switch(id)
         {
            case 0x0100:
            case 0x0400:
            case 0x0500:
               if(iParamLen!=8)return -5;
               if(iRecLen<0)return -6;

               if(id==0x0100)
               {
                  if(addrExt1.ip)
                     a=&addrExt2;
                  else
                     a=&addrExt1;

               }
               else if(id==0x0400)
                  //a=&addrExt1;
                  break;
               else if(id==0x0500)
               {
                  if(addrStun2.ip)
                     break;
                  a=&addrStun2;
               }
               a->ip=*(unsigned int*)(p+4);
               a->setPortNF(*(unsigned short*)(p+2));
               
               if(iGetExtAddrOnly)
               {
                  iActive=0;
               }
               
               break;

            default:
               DBG_PRF("Unknown param\n");


         }
         p+=iParamLen;
      }
      checkState();


      return 0;
   }
   CTThread th;


  // SOCKET sock;
   //ADDR addrSelf;
   CTSock *sock;
public:
   ADDR addrStun;
   ADDR addrStun2;
   ADDR addrSend;
   ADDR addrExt1;
   ADDR addrExt2;
   int iNatType;
   ADDR addrToBind;
   int iPingTime,iTimeStamp;
   int iHasStunResp;

private:
   int iMaxCheckTime;
   int iSockCreated;
   int iGetExtAddrOnly;
   int iActive,iListen;
   int iMaxResend;
   int iSendSeq;
   char bufSend[100];
   int iSendLen;
   int iSendCnt;
   unsigned int iPingTimeStartN[50];
   int iNextResend;
   int ipBinded;
   unsigned int id[2];//rand 64 bit
};
#endif //_C_T_STUN_H
