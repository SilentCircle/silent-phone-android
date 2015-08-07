/*
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

#import <AVFoundation/AVFoundation.h>

int getIOSVersion();
char * t_CFStringCopyUTF8String(CFStringRef str,  char *buffer, int iMaxLen);
int isDuplexAudioIsRunning();

#define T_CAN_USE_DEPRICATED




class CTAudioUsage{
   int iBluetoothIsAvailable;
   int iBluetoothIsAvailableReally;
   int iBluetoothIsUsed;
   char bufLastUsedRoute[64]="";
   int iWaitForBT;
   
   void(*fncCBOnRouteChange)(void *self, void *ptrUserData);
   void *ptrUserData=NULL;
   
public:
   CTAudioUsage(){iBluetoothIsAvailableReally=0;iWaitForBT=0;iBluetoothIsAvailable=0;iBluetoothIsUsed=0;bufLastUsedRoute[0]=0;fncCBOnRouteChange=NULL;ptrUserData=NULL;}
   //TODO use notifcation center
   
   
   void setAudioRouteChangeCB(void(*_fncCBOnRouteChange)(void *self, void *ptrUserData), void *_ptrUserData){
      fncCBOnRouteChange=_fncCBOnRouteChange;
      ptrUserData=_ptrUserData;
   }
   
   int isBTAvailable(){return iBluetoothIsAvailableReally || iBluetoothIsAvailable || iBluetoothIsUsed;}//TODO ios7 input array
   int isBTUsed(){return iBluetoothIsUsed;}
   
   void shouldSwitchToBT(){
      /*
       //fails if - airplay button is used
       //can not switch bt on, if it airplay button was touched
       //must reset BT dev
       if(!iBluetoothIsUsed && iBluetoothIsAvailable){
       iWaitForBT=1;
       }
       */
   }
   
#if 0
   /* input port types */
   AVF_EXPORT NSString *const AVAudioSessionPortLineIn       NS_AVAILABLE_IOS(6_0); /* Line level input on a dock connector */
   AVF_EXPORT NSString *const AVAudioSessionPortBuiltInMic   NS_AVAILABLE_IOS(6_0); /* Built-in microphone on an iOS device */
   AVF_EXPORT NSString *const AVAudioSessionPortHeadsetMic   NS_AVAILABLE_IOS(6_0); /* Microphone on a wired headset.  Headset refers to an
                                                                                     accessory that has headphone outputs paired with a
                                                                                     microphone. */
   
   /* output port types */
   AVF_EXPORT NSString *const AVAudioSessionPortLineOut          NS_AVAILABLE_IOS(6_0); /* Line level output on a dock connector */
   AVF_EXPORT NSString *const AVAudioSessionPortHeadphones       NS_AVAILABLE_IOS(6_0); /* Headphone or headset output */
   AVF_EXPORT NSString *const AVAudioSessionPortBluetoothA2DP    NS_AVAILABLE_IOS(6_0); /* Output on a Bluetooth A2DP device */
   AVF_EXPORT NSString *const AVAudioSessionPortBuiltInReceiver  NS_AVAILABLE_IOS(6_0); /* The speaker you hold to your ear when on a phone call */
   AVF_EXPORT NSString *const AVAudioSessionPortBuiltInSpeaker   NS_AVAILABLE_IOS(6_0); /* Built-in speaker on an iOS device */
   AVF_EXPORT NSString *const AVAudioSessionPortHDMI             NS_AVAILABLE_IOS(6_0); /* Output via High-Definition Multimedia Interface */
   AVF_EXPORT NSString *const AVAudioSessionPortAirPlay          NS_AVAILABLE_IOS(6_0); /* Output on a remote Air Play device */
   AVF_EXPORT NSString *const AVAudioSessionPortBluetoothLE	  NS_AVAILABLE_IOS(7_0); /* Output on a Bluetooth Low Energy device */
   
   /* port types that refer to either input or output */
   AVF_EXPORT NSString *const AVAudioSessionPortBluetoothHFP NS_AVAILABLE_IOS(6_0); /* Input or output on a Bluetooth Hands-Free Profile device */
   AVF_EXPORT NSString *const AVAudioSessionPortUSBAudio     NS_AVAILABLE_IOS(6_0); /* Input or output on a Universal Serial Bus device */
#endif
   
   int isLoudspkrInUse(){
      printf("[isLoudspkrInUse: current_route =%s]",bufLastUsedRoute);
      
      if(strcmp(bufLastUsedRoute, AVAudioSessionPortBuiltInReceiver.UTF8String)==0)return 0;
      if(strcmp(bufLastUsedRoute, AVAudioSessionPortHeadphones.UTF8String)==0)return 0;
      if(strcmp(bufLastUsedRoute, AVAudioSessionPortBuiltInSpeaker.UTF8String)==0)return 1;
      if(strcmp(bufLastUsedRoute, AVAudioSessionPortBuiltInMic.UTF8String)==0)return 0;
      if(strcmp(bufLastUsedRoute, AVAudioSessionPortHeadsetMic.UTF8String)==0)return 0;
      
      
      return strcmp(bufLastUsedRoute, "SpeakerAndMicrophone")==0;
   }
   
   const char *getDevName(){return &bufLastUsedRoute[0];}
   
   void setCurRoute(CFStringRef str){
      t_CFStringCopyUTF8String(str, bufLastUsedRoute, sizeof(bufLastUsedRoute));
      setCurRoute(bufLastUsedRoute);
   }
   
   void setCurRoute(const char * p){
      strncpy(bufLastUsedRoute,p,sizeof(bufLastUsedRoute));
      bufLastUsedRoute[sizeof(bufLastUsedRoute)-1]=0;
      
      iBluetoothIsUsed=isBT(p);
      
      if(iWaitForBT){
         if(iBluetoothIsUsed)iWaitForBT=0;else iBluetoothIsAvailable=0;
      }
      if(iBluetoothIsUsed)iBluetoothIsAvailable=1;
      
      printf("[new route:%s bt=%d btAv=%d btR=%d]\n",bufLastUsedRoute,iBluetoothIsUsed, iBluetoothIsAvailable,iBluetoothIsAvailableReally);
      if(fncCBOnRouteChange)fncCBOnRouteChange(this, ptrUserData);
   }
   
   
   static int isBT(CFStringRef str){
      char buf[64];
      char *p=t_CFStringCopyUTF8String(str, buf, sizeof(buf));
      return isBT(p);
   }
   static int isBT(const char *p){

      if(!p)return 0;
      if(strcmp(p,"HeadsetBT")==0)return 1;
      if(strstr(p,"Bluetooth")!=NULL)return 1;
      return strstr(p,"BT")!=NULL;
   }
   
   static void propListener(	void *                  inClientData,
                            AudioSessionPropertyID	inID,
                            UInt32                  inDataSize,
                            const void *            inData)
   {
#ifdef T_CAN_USE_DEPRICATED
      typedef struct{
         union{
            int i;
            char c[4];
         };
      }sss;
      
      sss s;
      s.i=inID;
      
      CTAudioUsage *_this = (CTAudioUsage*)inClientData;
      
      printf("[id=%c%c%c%c]",s.c[0],s.c[1],s.c[2],s.c[3]);
      //SpeakHereController *THIS = (SpeakHereController*)inClientData;
      if (inID == kAudioSessionProperty_AudioRouteChange)
      {
         CFDictionaryRef routeDictionary = (CFDictionaryRef)inData;
         //CFShow(routeDictionary);
         CFNumberRef reason = (CFNumberRef)CFDictionaryGetValue(routeDictionary, CFSTR(kAudioSession_AudioRouteChangeKey_Reason));
         SInt32 reasonVal;
         CFNumberGetValue(reason, kCFNumberSInt32Type, &reasonVal);
         
         printf("[reasonVal=%ld reason=%d]\n",reasonVal,reason);
         
         int iOldWasBT=0;
         
         
         if (reasonVal != kAudioSessionRouteChangeReason_CategoryChange)
         {
            CFStringRef oldRoute = (CFStringRef)CFDictionaryGetValue(routeDictionary, CFSTR(kAudioSession_AudioRouteChangeKey_OldRoute));
            if (oldRoute)
            {
               iOldWasBT=isBT(oldRoute);
               printf("[old route: rw=%d obt=%d]\n", reasonVal,iOldWasBT);
               CFShow(oldRoute);
               
            }
            else
               printf("ERROR GETTING OLD AUDIO ROUTE!\n");
            
            if (reasonVal == kAudioSessionRouteChangeReason_OldDeviceUnavailable)
            {
               if(iOldWasBT)_this->iBluetoothIsAvailable=0;
               
            }
            
            CFStringRef newRoute;
            UInt32 size; size = sizeof(CFStringRef);
            OSStatus error = AudioSessionGetProperty(kAudioSessionProperty_AudioRoute, &size, &newRoute);
            if (error) printf("ERROR GETTING NEW AUDIO ROUTE! %ld\n", error);
            else
            {
               _this->setCurRoute(newRoute);
            }
            
         }
         else{
            CFStringRef newRoute;
            UInt32 size; size = sizeof(CFStringRef);
            OSStatus error = AudioSessionGetProperty(kAudioSessionProperty_AudioRoute, &size, &newRoute);
            if (error) printf("ERROR GETTING NEW AUDIO ROUTE! %ld\n", error);
            else
            {
               _this->setCurRoute(newRoute);
               
            }
         }
      }
      else if (inID == kAudioSessionProperty_AudioInputAvailable)
      {
         /*
          if (inDataSize == sizeof(UInt32)) {
          UInt32 isAvailable = *(UInt32*)inData;
          // disable recording if input is not available
          //THIS->btn_record.enabled = (isAvailable > 0) ? YES : NO;
          }
          */
      }
#endif
   }
   
   void checkRouteCB(NSNotification *notification){
      
      UInt8 reasonValue = [[notification.userInfo valueForKey: AVAudioSessionRouteChangeReasonKey] intValue];
      
      AVAudioSessionRouteDescription *prev = (AVAudioSessionRouteDescription*)[notification.userInfo valueForKey:AVAudioSessionRouteChangePreviousRouteKey];
      
      int iOldWasBT = 0;
      
      if(prev){
         NSArray *a = [prev outputs];
         if(a && a.count>0){
            AVAudioSessionPortDescription *pd = a[0];
            if(pd.portName)iOldWasBT = isBT(pd.portType.UTF8String);
            iBluetoothIsAvailable = iOldWasBT;
            NSLog(@"AS prev %@", pd);
            
         }
      }
      
      //AVAudioSessionRouteChangePreviousRouteKey
      
      if(AVAudioSessionRouteChangeReasonCategoryChange == reasonValue || reasonValue==AVAudioSessionRouteChangeReasonOverride){
         checkCurrent();
      }
      else if (AVAudioSessionRouteChangeReasonNewDeviceAvailable == reasonValue){
         NSLog(@"     NewDeviceAvailable");
         checkCurrent();
      }
      else if(AVAudioSessionRouteChangeReasonOldDeviceUnavailable == reasonValue) {
         
         NSLog(@"     OldDeviceUnavailable");
      }
   }
private:
   
   void checkCurrent(){

      AVAudioSession *a = [AVAudioSession sharedInstance];

      NSArray * ar = [a availableInputs];
      if(ar){
         int bt=0;
         for(int i=0; i<ar.count; i++){
            AVAudioSessionPortDescription *pd =ar[i];
            NSLog(@"[ido=%d %@]",i,pd);
            if(isBT(pd.portType.UTF8String))bt=1;
         }
         iBluetoothIsAvailableReally=bt;
      }

      
      AVAudioSessionRouteDescription * rd =  [a currentRoute];
      
      if(rd){
         NSArray *a = rd.outputs;
         for(int i=0; i<a.count; i++){
            NSLog(@"[id=%d %@]",i,a[i]);
         }
         if(a && a.count>0){
            AVAudioSessionPortDescription *pd= a[0];
           // pd.portType = AVAudioSessionPortBuiltInMic;
            this->setCurRoute(pd.portType.UTF8String);
            NSLog(@"currentRoute %@",pd);
         }
      }
      
   }
   
};

static CTAudioUsage ctBTUsage;

int isLoudspkrInUse(){
   return ctBTUsage.isLoudspkrInUse();
}

int isBTUsed(){
   return ctBTUsage.isBTUsed();
}

int isBTAvailable(){
   return ctBTUsage.isBTAvailable();
}
const char * getAudioDevName(){
   return ctBTUsage.getDevName();
}

void setAudioRouteChangeCB(void(*fncCBOnRouteChange)(void *self, void *ptrUserData), void *ptrUserData){
   if(fncCBOnRouteChange){
      AudioSessionAddPropertyListener(kAudioSessionProperty_AudioRouteChange, &CTAudioUsage::propListener, &ctBTUsage);
   }
   ctBTUsage.setAudioRouteChangeCB(fncCBOnRouteChange, ptrUserData);
}

void t_routeChangeHandler(NSNotification *notification){
   ctBTUsage.checkRouteCB(notification);
   
}



#ifdef T_CAN_USE_DEPRICATED
static void initAS_ios_old(int iForce=0){
   static int iInitOk=0;
   if(!iInitOk || iForce){
      iInitOk=1;
      AudioSessionInitialize(NULL, NULL, NULL,NULL);
   }
}
#endif

double t_iOSpreferredSampleRate(){
#ifdef T_CAN_USE_DEPRICATED
   if(getIOSVersion()<7){
   
      OSStatus err = noErr;
      Float64  rate = 44100;
      UInt32 sz=sizeof(rate);
      err=AudioSessionGetProperty(kAudioSessionProperty_CurrentHardwareSampleRate//kAudioSessionProperty_PreferredHardwareSampleRate
                                  ,&sz
                                  , &rate);
      return rate;
   }
#endif
   return [[AVAudioSession sharedInstance]preferredSampleRate];
}

void t_initAS(int duplex){
#ifdef T_CAN_USE_DEPRICATED
   if(getIOSVersion()<7){
      int reps=0;
rep1:
      OSStatus err = noErr;
      UInt32 sessionCategory = kAudioSessionCategory_PlayAndRecord;
      err = AudioSessionSetProperty(kAudioSessionProperty_AudioCategory, sizeof(sessionCategory), &sessionCategory);
      if(!reps && err == kAudioSessionNotInitialized){
         
         initAS_ios_old(1);
         reps=1;
         goto rep1;
      }
      
      UInt32 b=1;
      
      err = AudioSessionSetProperty (kAudioSessionProperty_OverrideCategoryEnableBluetoothInput, sizeof(b), &b);
      return;
   }
#endif
   
   //
   
   AVAudioSession *a = [AVAudioSession sharedInstance];
   if(duplex){
   //   [a setCategory:AVAudioSessionCategoryPlayAndRecord error:nil];
      [a setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionAllowBluetooth error:nil];
      
      const char* sendEngMsg(void *pEng, const char *p);
      
      const char *ap = sendEngMsg(NULL,"cfg.iEnableAirplay");
      if(ap && ap[0]=='1')
         [a setMode:AVAudioSessionModeVideoChat error:nil];
      
    //  AVAudioSessionCategoryOptionAllowBluetooth
   }
   else{
      
      //if(!Ecording)
      //if(!)recording
      if(!isDuplexAudioIsRunning()){
         [a setCategory:AVAudioSessionCategoryPlayback error:nil];
      }
   }
   
   return ;
}


void t_setActiveAS(int yes){
#ifdef T_CAN_USE_DEPRICATED
   if(getIOSVersion()<7){
      AudioSessionSetActive(yes? true: false);
      return;
   }
#endif
   NSError *err = 0;
   [[AVAudioSession sharedInstance] setActive: yes?YES:NO error: &err];
   //setPreferredIOBufferDuration
}



/*
 if(getIOSVersion()<7){
 AudioSessionSetActive(yes? true: false);
 return;
 }
 */



void t_setGoodHWBuffDuration(){
#ifdef T_CAN_USE_DEPRICATED
   if(getIOSVersion()<7){
      OSStatus err = noErr;
      Float32 bufSizeInSec=0;
      UInt32 sz=sizeof(bufSizeInSec);
      
      err = AudioSessionGetProperty(
                                    kAudioSessionProperty_PreferredHardwareIOBufferDuration,
                                    &sz, &bufSizeInSec);
      printf("sz=%f",bufSizeInSec);
      if (err) { printf ("AudioUnitSetProperty-CB2=%ld\n", err); }
      
      if(bufSizeInSec>.02f){
         bufSizeInSec=.02f;
         
         err = AudioSessionSetProperty(
                                       kAudioSessionProperty_PreferredHardwareIOBufferDuration,
                                       sizeof(bufSizeInSec), &bufSizeInSec);
         if (err) { printf ("AudioUnitSetProperty-CB3=%ld\n", err); }
      }
      return;
   }
#endif
   
   AVAudioSession *a = [AVAudioSession sharedInstance];
   [a setPreferredIOBufferDuration:0.01 error:nil];
}


static int isPlaybackVolumeMutedIOS6(){

   float volume = [[AVAudioSession sharedInstance] outputVolume];
   printf("[volume %f]\n",volume);
   return volume<.1;
}

int isPlaybackVolumeMuted(){
   
   
   if(!isDuplexAudioIsRunning())return 0;
   
   if(getIOSVersion()>=6)return isPlaybackVolumeMutedIOS6();
#ifdef T_CAN_USE_DEPRICATED
   Float32 volume = 1.0;
   UInt32 dataSize = sizeof(Float32);
   
   AudioSessionGetProperty (
                            kAudioSessionProperty_CurrentHardwareOutputVolume,
                            &dataSize,
                            &volume
                            );
   
   printf("[volume %f]\n",volume);
   return volume<.1;
#else
   return 0;
#endif
}

static int t_setSpeakerMode(int loud){
   
   BOOL ok;
   NSError *err = 0;
#ifdef T_CAN_USE_DEPRICATED
   if(getIOSVersion()<7){
      return 0;
   }
#endif
   
   ok = [[AVAudioSession sharedInstance] overrideOutputAudioPort:
         loud ? AVAudioSessionPortOverrideSpeaker : AVAudioSessionPortOverrideNone
                                                           error:&err];
   return ok;
}

int _setAudioRouteRest(int iLoudSpkr, int iRest, int BT){
   // return iLoudSpkr;
   static int iPrevSpkr=-1;
   static int iPrevBT=1;
   
   if(iRest==1)iLoudSpkr=iPrevSpkr;
   if(iRest==1)BT=iPrevBT;
   
   if(iRest==0)iPrevSpkr=iLoudSpkr;
   if(iRest==0)iPrevBT=BT;
   
   if(getIOSVersion()>=6){
      t_setSpeakerMode(iLoudSpkr);
      return iLoudSpkr;
   }
#ifdef T_CAN_USE_DEPRICATED
   OSStatus o;
   int reps=0;
   
   
rep:
   reps++;
   //kAudioSessionProperty_OverrideCategoryDefaultToSpeaker
   UInt32 dst =iLoudSpkr ?
   kAudioSessionOverrideAudioRoute_Speaker :
   kAudioSessionOverrideAudioRoute_None;
   
   

   
   
   o = AudioSessionSetProperty (
                                kAudioSessionProperty_OverrideAudioRoute,
                                sizeof(dst), &dst);
   
   
   
   if(o == kAudioSessionNotInitialized && reps==1){
      initAS_ios_old(1);
      goto rep;
   }
   
   if (o != kAudioSessionNoError) {//560557673
      
      return o>0?-o:o;
   }
#endif
   
   return iLoudSpkr;
}

static int getAOState(CFStringRef *state){
   // kAudioSessionCategory_PlayAndRecord
   UInt32 propertySize = sizeof(CFStringRef);
   OSStatus status = AudioSessionGetProperty(kAudioSessionProperty_AudioRoute, &propertySize, state);
   return status==kAudioSessionNoError?0:status;
}


int isAudioDevConnected(){
   
   CFStringRef s;
   getAOState(&s);
   if(!s)return 0;
   NSString *str=(__bridge NSString *)s;
   
   NSLog(@"ao=%@",s);
   
   NSRange r;
   
   NSString *ao0[]={@"ReceiverAndMicrophone",@"SpeakerAndMicrophone",@"Speaker" ,NULL};
   NSString *ao1[]={@"Heads",@"Headp",@"BT", @"Headphone",@"HeadphonesAndMicrophone",@"HeadphonesBT",@"HeadsetBT",
      @"MicrophoneBluetooth",@"HeadsetInOut" ,@"AirTunes",NULL};
   
   for(int i=0;;i++){
      if(!ao0[i])break;
      r=[str rangeOfString : ao0[i]];
      if(r.location!=NSNotFound){
         puts("isAudioDevConnected ret0");
         return 0;
      }
   }
   
   
   for(int i=0;;i++){
      if(!ao1[i])break;
      r=[str rangeOfString : ao1[i]];
      if(r.location!=NSNotFound){
         puts("isAudioDevConnected ret1");
         if([str rangeOfString : @"BT"].location!=NSNotFound)return 2;
         return 1;
      }
   }
   puts("[isAudioDevConnected ret0 unkn]");
   
   //SpeakerAndMicrophone Speaker HeadphonesAndMicrophone ReceiverAndMicrophone Headphone
   //  HeadphonesBT  HeadsetBT MicrophoneBluetooth
   
   return 0;
}


