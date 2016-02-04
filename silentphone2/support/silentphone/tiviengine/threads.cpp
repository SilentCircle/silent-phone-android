//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#include "CPhone.h"

#if defined(ANDROID_NDK)
#include <android/log.h>
#endif



void tivi_log_scr(const char* format, ...);
void tivi_log(const char* format, ...);
unsigned int getTickCount();

#define _DBG //


#ifdef T_TEST_SYNC_TIMER
#define T_SLEEP_INC_TIME(_A) {}
#else
#define T_SLEEP_INC_TIME(_A) {Sleep(_A);ph->uiGT+=(_A);}
#endif

int CTiViPhone::TimerProc(void *f)
{
   CTiViPhone *ph=(CTiViPhone *)f;
   ph->uiGT=1;
   T_SLEEP_INC_TIME(200);
   int i=0;
   int iSecondsSinceStart=0;
   int iSecondsSinceIPChanged=0;

   if(ph->p_cfg.iIndex < 2){//start only for sc
      CTAxoInterfaceBase::sharedInstance(ph->p_cfg.user.un);
   }
   int ip;
   int iNextSipKA_at=10;
   
   
   t_ph_tick uiPrevIPResetAt=0;
   
#ifdef T_TEST_SYNC_TIMER
   int rc = 0;
#endif
   
   if(ph->p_cfg.GW.ip)
      ph->keepAlive.sendNow();
   
   unsigned int uiT,uiPrevTC=getTickCount();
   t_ph_tick uiPrevGT=0;
   unsigned int uiPrevTick=uiPrevTC;
   t_ph_tick uiGTOk=1;
   
   while(ph->bRun)
   {
      int iWasSuspended=0;
      //TODO if all done, do notify "can suspend"
#ifndef T_TEST_SYNC_TIMER
      i++;
      T_SLEEP_INC_TIME(250);
      if(ph->bRun==0)break;
      //TODO check - do i need to send data?, do we have a tasks(active_cals, stun, rereg, keepalive, checkip...)?
      if((i&(4-1)))continue;
      uiT=getTickCount();
#else
      i+=4;
      pthread_mutex_lock(&ph->timerMutex);
      unsigned int uiTemp = getTickCount();
      rc = pthread_cond_timeout_np(&ph->timerConditional, &ph->timerMutex, 1000);
      
      uiT = getTickCount();
      int waitTime = uiT - uiTemp;
      
      // Check for long waits. Also add real number of ticks if loop wakes up faster than once a second
      if (waitTime > 1100) {
         
         t_logf(log_events,__FUNCTION__, "Current time: %ld, waitTime: %u, uiT: %u, uiGT: %llu\n", time(NULL), waitTime, uiT, ph->uiGT);
         
//         __android_log_print(ANDROID_LOG_DEBUG,"TIMERNEW", "Current time: %ld, waitTime: %u, uiT: %u, uiGT: %llu\n", time(NULL), waitTime, uiT, ph->uiGT);
         ph->uiGT += 1000;                 // Max wait time in ms, according to T_SLEEP_INC_TIME macro - JANIS
         iWasSuspended = waitTime > 5000;
      }
      else 
         ph->uiGT += waitTime>0 ? waitTime : 5;
      
      rc = pthread_mutex_unlock(&ph->timerMutex);
      
      if (ph->bRun == 0)
         break;
#endif
      
      
#if 1
      int d = (int)(uiT-uiPrevTick);
      
      if(d<0){
         d=1;
      }
      else if(d>20000){
         t_logf(log_events,__FUNCTION__, "thread is too slow, posible something is wrong or suspended by OS, time=%llu d=%u\n",ph->uiGT, d);
         if(d>36000001)d=36000001;//10h
         iWasSuspended=1;
      }
      
      ph->uiGT = uiGTOk + d;
      uiGTOk = ph->uiGT;//tmp only - i will stop to update the ph->uiGT in other places
      uiPrevTick = uiT;
      int s = (d >> 10); if(s < 1)s = 1;
      iSecondsSinceStart += s;
      iSecondsSinceIPChanged += s;
      
#else
      
      unsigned int d=(uiT-uiPrevTC);
      unsigned int d2=(ph->uiGT-uiPrevGT);
      int dd=(d-d2);
      unsigned int dPrev=uiT-uiPrevTick;
    
      //apple ios and android backround fix
      if(dd>500 || (dPrev>4000 && dPrev<36000001)){
         if(dd>0 && dd<36000001){
            t_ph_tick u=ph->uiGT;
            u+=dd;
            ph->uiGT=u;
            uiPrevGT=ph->uiGT;
            uiPrevTC=uiT;
            if(dd>20000){
               //it is ok if app was suspended
#if defined(ANDROID_NDK)
               __android_log_print(ANDROID_LOG_DEBUG,"TIMERNEW", "d: %u, dd: %d, dPrev: %llu", d, dd, dPrev);
               __android_log_print(ANDROID_LOG_DEBUG,"TIMERNEW", "thread is too slow, time = %llu (%u)", ph->uiGT, uiT);
#else
               printf("thread is too slow, posible something is wrong, time=%llu d=%u\n",ph->uiGT, d);
#endif
               int tf=dd>>10; // divide by 1000 ,ok by 1024 :)
               iSecondsSinceStart+=tf;
               iSecondsSinceIPChanged+=tf;
            }
         }
         else{
            uiPrevGT=ph->uiGT;
            uiPrevTC=uiT;
         }
         //         printf("new GT %d\n",ph->uiGT);
      }
      
      uiPrevTick=uiT;
      iSecondsSinceStart++;
      iSecondsSinceIPChanged++;
      
#endif
  //    printf("new GT %u\n",ph->uiGT);

      
      if(ph->p_cfg.iAccountIsDisabled)continue;
      
      if(ph->bRun==1){
         
         
         if(iWasSuspended || (iSecondsSinceStart&1) || (ph->ipBinded==0 && ph->p_cfg.GW.ip==0))
         {
            ip = ph->cPhoneCallback->getLocalIp();
            
            
            if(ip!=ph->ipBinded)
            {
               iSecondsSinceIPChanged=0;
               
               uiPrevIPResetAt = ph->uiGT;
               ph->onNewIp(ip,1);
               ph->keepAlive.reset();
               T_SLEEP_INC_TIME(250);
            }
         }
         
         if((ph->extAddr.ip==0 || !ph->iOptionsSent) && ph->ipBinded && hasNetworkConnect(ph->ipBinded))
         {
            if(iSecondsSinceIPChanged>20 && !ph->extAddr.ip){
               //TODO change connection or switch from udp to tcp
            }
            
            if(!ph->iOptionsSent &&  ph->uiGT-uiPrevIPResetAt>T_GT_SECOND){ // the onNewIp() calls the sendSipKA()
               
               //tivi keepalive not supported
               //iKeepaliveSupported=0;//TODO add TO CPhone
               //TODO set session return param if no connection
               //TODO rem prev option keepalives
               //TODO mark as keeplive
               //TODO send KA from one session
               ph->sendSipKA();//,NULL,NULL);
            }
            else if(iSecondsSinceIPChanged>40)
            {
               
               ph->extAddr.ip=ph->ipBinded;
               ph->extAddr.setPort(ph->sockSip.addr.getPort());
            }
            
         }
         
         if(hasNetworkConnect(ph->ipBinded))
         {
            if(ph->p_cfg.GW.ip)
            {
               if(ph->p_cfg.iSipKeepAlive){ //TODO send if udp is active ??
                  
                  if(iNextSipKA_at<iSecondsSinceStart){
                     iNextSipKA_at=iSecondsSinceStart+20;
                     ph->sendSipKA();
                  }
               }
               else{
                  if(ph->extAddr.ip==0)
                     ph->keepAlive.sendNow();
                  else
                     ph->keepAlive.onTimer();
               }
            }
         }
      }
      
      if(ph->p_cfg.iUserRegister==2 && ph->p_cfg.GW.ip)
      {
         ph->p_cfg.iUserRegister=1;
      }
      
      ph->handleSessions();
      T_SLEEP_INC_TIME(60);
      
      if(ph->bRun==1){ph->onTimer(); T_SLEEP_INC_TIME(60);if(ph->bRun==0)break;}
      
      ph->sockSip.sendQueue();
      if(ph->bRun==0)break;
      
      T_SLEEP_INC_TIME(60);
      if(ph->bRun==0)break;
      
      if(ph->bRun==1)ph->cPhoneCallback->onTimer();
#ifdef T_TEST_SYNC_TIMER
      pthread_mutex_lock(&ph->timerDoneMutex);
      pthread_cond_signal(&ph->timerDoneConditional);
      pthread_mutex_unlock(&ph->timerDoneMutex);
      
#endif
   }
   
#ifdef T_TEST_SYNC_TIMER
   pthread_cond_signal(&ph->timerDoneConditional);  // avoid possible lock if while loop ends
#endif
   
   ph->sockSip.sendTo((char*)"1",1,&ph->sockSip.addr);
   
   return 0;
}

void wakeCallback(int iLock);

class CTCpuWakeLock{
   int iLocked;
public:
   CTCpuWakeLock(int iLock){if(iLock){wakeCallback(1);}iLocked=iLock;}
   ~CTCpuWakeLock(){if(iLocked)wakeCallback(0);}
};

int CTiViPhone::thSipRec(void *f)
{
   CTiViPhone *ph=(CTiViPhone *)f;
   SIP_MSG *sMsg = new SIP_MSG ;
   int iResetParam=0;
   
   //int t_AttachCurrentThread();
   //t_AttachCurrentThread();
   
   strcpy(ph->thTimer.thName,"_t_r_timer");
   
   ph->thTimer.create(&CTiViPhone::TimerProc,(void *)ph);
   
   ph->sockSip.sendQueue();
   
   while(ph->bRun)
   {
      //ph->sockSip.sendQuve();if(!ph->bRun)break;
      
      if(iResetParam!=10)
         memset(sMsg,0,sizeof(SIP_MSG));
      
      ADDR addr;
      int rec = ph->sockSip.recvFrom((char *)&sMsg->rawDataBuffer[0],MSG_BUFFER_SIZE-100,&addr);
      
      CTCpuWakeLock wakeLock(rec>100);//should it be after recv or recvfrom
      iResetParam=ph->recMsg(sMsg, rec, addr);
      if(!ph->bRun)break;
      ph->sockSip.sendQueue();
      
   }
   
   delete sMsg;
   
   return 0;
}
