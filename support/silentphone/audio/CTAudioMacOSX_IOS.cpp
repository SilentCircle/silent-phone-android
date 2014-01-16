
#ifdef __APPLE__
#include "TargetConditionals.h"
#endif


#ifdef TARGET_OS_IPHONE
//asda
#define T_IPHONE
#endif

#ifndef T_IPHONE
#include <CoreServices/CoreServices.h>
#endif
#include <stdio.h>
#include <unistd.h>
#include <AudioUnit/AudioUnit.h>
#include <AudioUnit/AudioComponent.h>
//#include <CoreServices/AudioServices.h>
#include <math.h>
#ifndef T_IPHONE

#include <CoreAudio/CoreAudio.h>
#else
#include <AudioToolbox/AudioServices.h>
#endif

#include "../os/CTMutex.h"

static CTMutex mutexDA;
static CTMutex mutexUA;


enum {
   kAsFloat = 32,
   kAs16Bit = 16,
   kAs24Bit = 24
};
/*
enum {
   kAUVoiceIOProperty_BypassVoiceProcessing     = 2100,
   kAUVoiceIOProperty_VoiceProcessingEnableAGC  = 2101,
   kAUVoiceIOProperty_DuckNonVoiceAudio         = 2102,
   kAUVoiceIOProperty_VoiceProcessingQuality    = 2103,
   kAUVoiceIOProperty_MuteOutput                = 2104,
   kAUVoiceIOProperty_FarEndVersionInfo         = 2105
};
 
 UInt32 audioAGC = 1;//[[NSUserDefaults standardUserDefaults]boolForKey:VOICE_AGC];
 status = AudioUnitSetProperty(kAudioUnit, kAUVoiceIOProperty_VoiceProcessingEnableAGC,
                               kAudioUnitScope_Global, kInputBus, &audioAGC, sizeof(audioAGC));
 
*/


int setAudioRouteRest(int iLoudSpkr, int iRest);


template<class T>
double createSine(double fCtx, T *p, int iSamples, float freq, int iRate, int iVol){
   double f=freq*3.1415*2/(double)iRate;
   for(int i=0;i<iSamples;i++){
      p[i]=(T)(sin(fCtx)*(double)iVol);
      fCtx+=f;
   }
   
   return fCtx;
}

char * t_CFStringCopyUTF8String(CFStringRef str,  char *buffer, int iMaxLen);

class CTAudioUsage{
   int iBluetoothIsAvailable;
   int iBluetoothIsUsed;
   char bufLastUsedRoute[64]="";
   int iWaitForBT;
   
   void(*fncCBOnRouteChange)(void *self, void *ptrUserData);
   void *ptrUserData=NULL;
   
public:
   CTAudioUsage(){iWaitForBT=0;iBluetoothIsAvailable=0;iBluetoothIsUsed=0;bufLastUsedRoute[0]=0;fncCBOnRouteChange=NULL;ptrUserData=NULL;}
   //TODO use notifcation center
   
   
   void setAudioRouteChangeCB(void(*_fncCBOnRouteChange)(void *self, void *ptrUserData), void *_ptrUserData){
      fncCBOnRouteChange=_fncCBOnRouteChange;
      ptrUserData=_ptrUserData;
   }
   
   int isBTAvailable(){return iBluetoothIsAvailable || iBluetoothIsUsed;}
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
   
   int isLoudspkrInUse(){return strcmp(bufLastUsedRoute, "SpeakerAndMicrophone")==0;}
   
   const char *getDevName(){return &bufLastUsedRoute[0];}
   
   void setCurRoute(CFStringRef str){
      t_CFStringCopyUTF8String(str, bufLastUsedRoute, sizeof(bufLastUsedRoute));
      iBluetoothIsUsed=isBT(str);
      if(iWaitForBT){
         if(iBluetoothIsUsed)iWaitForBT=0;else iBluetoothIsAvailable=0;
      }
      if(iBluetoothIsUsed)iBluetoothIsAvailable=1;
      
      printf("[new route:%s bt=%d btAv=%d]\n",bufLastUsedRoute,iBluetoothIsUsed, iBluetoothIsAvailable);
      if(fncCBOnRouteChange)fncCBOnRouteChange(this, ptrUserData);
   }
   
   
   static int isBT(CFStringRef str){
      char buf[64];
      char *p=t_CFStringCopyUTF8String(str, buf, sizeof(buf));
      if(!p)return 0;
      if(strcmp(p,"HeadsetBT")==0)return 1;
      return strstr(p,"BT")!=NULL;
   }

   static void propListener(	void *                  inClientData,
                            AudioSessionPropertyID	inID,
                            UInt32                  inDataSize,
                            const void *            inData)
   {
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
               /*
                if (THIS->player->IsRunning()) {
                [THIS pausePlayQueue];
                [[NSNotificationCenter defaultCenter] postNotificationName:@"playbackQueueStopped" object:THIS];
                }
                */
            }
            
            CFStringRef newRoute;
            UInt32 size; size = sizeof(CFStringRef);
            OSStatus error = AudioSessionGetProperty(kAudioSessionProperty_AudioRoute, &size, &newRoute);
            if (error) printf("ERROR GETTING NEW AUDIO ROUTE! %ld\n", error);
            else
            {
               _this->setCurRoute(newRoute);
            }
            
            

            /*
             // stop the queue if we had a non-policy route change
             if (THIS->recorder->IsRunning()) {
             [THIS stopRecord];
             }
             */
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
         if (inDataSize == sizeof(UInt32)) {
            UInt32 isAvailable = *(UInt32*)inData;
            // disable recording if input is not available
            //THIS->btn_record.enabled = (isAvailable > 0) ? YES : NO;
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
   ctBTUsage.setAudioRouteChangeCB(fncCBOnRouteChange, ptrUserData);
}

void initAS(int iForce=0){
   static int iInitOk=0;
   if(!iInitOk || iForce){
      iInitOk=1;
      AudioSessionInitialize(NULL, NULL, NULL,NULL);

      
      AudioSessionAddPropertyListener(kAudioSessionProperty_AudioRouteChange, &CTAudioUsage::propListener, &ctBTUsage);

      
   }

}

#ifndef T_IPHONE
AudioDeviceID NewGetDefaultInputDevice()
{
   AudioDeviceID theAnswer = 0;
   UInt32 theSize = sizeof(AudioDeviceID);
   AudioObjectPropertyAddress theAddress = { kAudioHardwarePropertyDefaultInputDevice,
      kAudioObjectPropertyScopeGlobal,
      kAudioObjectPropertyElementMaster };
   
   OSStatus theError = AudioObjectGetPropertyData(kAudioObjectSystemObject,
                                                  &theAddress,
                                                  0,
                                                  NULL,
                                                  &theSize,
                                                  &theAnswer);
   // handle errors
   
   return theAnswer;
}
#endif
void DestroyAudioBufferList(AudioBufferList* list)
{
   if(list) {
#ifndef T_IPHONE
      UInt32                      i;
    //  sds
      for(i = 0; i < list->mNumberBuffers; i++) {
         if(list->mBuffers[i].mData)
            free(list->mBuffers[i].mData);
      }
#endif
      free(list);
   }
}

// Convenience function to allocate our audio buffers
AudioBufferList *AllocateAudioBufferList(UInt32 numChannels, UInt32 size)
{
   AudioBufferList*            list;
   UInt32                      i;
   // size*=2;
   
   list = (AudioBufferList*)calloc(1, sizeof(AudioBufferList) + numChannels * sizeof(AudioBuffer));
   if(list == NULL)
      return NULL;
   
   list->mNumberBuffers = numChannels;
   for(i = 0; i < numChannels; ++i) {
      list->mBuffers[i].mNumberChannels = 1;
      list->mBuffers[i].mDataByteSize = size;
#ifdef T_IPHONE
      list->mBuffers[i].mData = NULL;
#else
      list->mBuffers[i].mData = malloc(size);
      
      if(list->mBuffers[i].mData == NULL) {
         DestroyAudioBufferList(list);
         return NULL;
      }
      
#endif
   }
   return list;
}

int iRBufPos=0;

class CTMAudio{
  // short rBuf[1024*256];
   AudioUnit   aUnit;
   int iRate,iChannels;
   AudioUnitScope uScope;
   int iACardId;
   int iInitOk;
   int iActive;
   int iNeedStop;
   Float64 outSampleRate;
   
   int iReInit;
   
   int iIsRecorder;
   
   AudioStreamBasicDescription streamFormat;
#ifndef T_IPHONE
 //  aasa
   AudioDeviceID   fInputDeviceID;
#endif
public: 
//   int* getActivePtr(){return &iActive;}
   AudioBufferList *bl;
   AudioUnit getAU(){return aUnit;}
   void *cbRecPlayData;
   
   int (*cbFncRecPlay)(void *, short *, int );
   
   Float64         sSampleRate ;//= 48000;
   SInt32          sNumChannels ;//= 2;
   
   //SInt32          sWhichFormat /= kAsFloat;
   
   
   
   
   UInt32 theFormatID ;//= kAudioFormatLinearPCM;
   
   // these are set based on which format is chosen
   UInt32 theFormatFlags;// = kLinearPCMFormatFlagIsSignedInteger| kLinearPCMFormatFlagIsPacked;
   UInt32 theBytesInAPacket ;
   UInt32 theBitsPerChannel ;
   UInt32 theBytesPerFrame ;
   
   // these are the same regardless of format
   UInt32 theFramesPerPacket ;//= 1; // this shouldn't change
   
   
public:
   int iMachineRate;
   CTMAudio(int iIsRecorder,int iRate, AudioUnitScope uScope, int iCh=1, int iACardId=0)
   :iIsRecorder(iIsRecorder),iRate(iRate),uScope(uScope),iChannels(iCh),iACardId(iACardId){
      cbFncRecPlay=NULL;
      cbRecPlayData=NULL;
      aUnit=NULL;
      iReInit=0;
      iInitOk=0;
      iActive=0;
      iNeedStop=1;
      iMachineRate=0;
      outSampleRate=0;
#ifndef T_IPHONE
      fInputDeviceID=0;
#endif
      theFramesPerPacket=1;
      
      memset(&streamFormat,0,sizeof(streamFormat));
      theFormatID=kAudioFormatLinearPCM;
      theFormatFlags =  kLinearPCMFormatFlagIsSignedInteger 
      | kAudioFormatFlagsNativeEndian
      | kLinearPCMFormatFlagIsPacked
      | kAudioFormatFlagIsNonInterleaved;
      theBytesPerFrame = theBytesInAPacket = 2;
      theBitsPerChannel = 16;  
      
      bl=NULL;
   }
   ~CTMAudio(){
      CTMutexAutoLock _al(mutexUA);
      rel();
      
      if(bl)DestroyAudioBufferList(bl);
   }   
   int startRecPlay(void *p, int (*cbFnc)(void *, short *, int )){
      cbFncRecPlay=cbFnc;
      cbRecPlayData=p;
      return start();
   }
   short tmpBuf[8000];
   int onAudio(AudioBufferList *ioData, int iSamples){
      
      if(iSamples!=ioData->mBuffers[0].mDataByteSize/(streamFormat.mBitsPerChannel>>3))return -1;
      if(iActive!=1)return 0;
      if(streamFormat.mBitsPerChannel==32){
         int i;

         int iIsShort=(streamFormat.mFormatFlags&kLinearPCMFormatFlagIsSignedInteger);
         //kLinearPCMFormatFlagIsSignedInteger
         if(iIsShort || ioData->mBuffers[0].mNumberChannels==2){
            short *p=(short*)ioData->mBuffers[0].mData;
            if(cbFncRecPlay(cbRecPlayData,(short  *)&tmpBuf[0],iSamples)<0)return 0;
            
            if(!p)return 0;
            for(i=0;i<iSamples;i++){
               p[i*2]=tmpBuf[i];
               p[i*2+1]=tmpBuf[i];
            }
            
            return 0;
         }
      
         if(cbFncRecPlay){
            if(cbFncRecPlay(cbRecPlayData,(short  *)&tmpBuf[0],iSamples)<0)return 0;
           // puts("2");
            Float32 *p=(Float32*)ioData->mBuffers[0].mData;
            if(!p)return 0;
            
            for(i=0;i<iSamples;i++){
               p[i]=(Float32)tmpBuf[i]/32800.f;
              // p[i]=p[i];
            }
            for (UInt32 channel = 1; channel < ioData->mNumberBuffers; channel++)
               memcpy (ioData->mBuffers[channel].mData, ioData->mBuffers[0].mData, ioData->mBuffers[0].mDataByteSize);
         }           
         return 0;
      }
//#endif
      if(cbFncRecPlay){
        // iSamples=ioData->mBuffers[0].mDataByteSize;
         if(cbFncRecPlay(cbRecPlayData,(short  *)ioData->mBuffers[0].mData,iSamples)<0)return 0;
         for (UInt32 channel = 1; channel < ioData->mNumberBuffers; channel++)
            memcpy (ioData->mBuffers[channel].mData, ioData->mBuffers[0].mData, ioData->mBuffers[0].mDataByteSize);
         short *p=(short*)ioData->mBuffers[0].mData;
         printf("%d 16bit %d %d\n",iSamples,p[0],p[1]);
         return 0;
      }
#if 0
      if(kAudioUnitScope_Input==uScope){
         //fill  playback data
         short  *p=(short  *)ioData->mBuffers[0].mData;
         static double x;
         static int iPlayPos=0;
         if(iRBufPos<iSamples*4){
            x=createSine(x,p,(int)iSamples,440,(int)streamFormat.mSampleRate,10000);
         }
         else{
            for(int i=0;i<iSamples;i++){
               p[i]=rBuf[iPlayPos];
               // p2[i]=rBuf[iPlayPos];;
               iPlayPos++;
               
            }
            if(iPlayPos>200000){iPlayPos=0;iRBufPos=0;}
         }
         printf("pp=%d",iPlayPos);
         

         for (UInt32 channel = 1; channel < ioData->mNumberBuffers; channel++)
            memcpy (ioData->mBuffers[channel].mData, ioData->mBuffers[0].mData, ioData->mBuffers[0].mDataByteSize);
      }
      else {
         
         int iMax=0;
         // static int s=1;         s=!s;       if(s)return 0;
         
         
         if(streamFormat.mBitsPerChannel==32){
            
            Float32  *p=(Float32  *)ioData->mBuffers[0].mData;
            if(ioData->mNumberBuffers>1){
               float f =32500.f/2.f;//float(SHRT_MAX) /2.001f;
               Float32  *p2=(Float32  *)ioData->mBuffers[1].mData;
               for(int i=0;i<iSamples;i++){
                  int c=(int)(p[i]*f);
                  int c2=(int)(p2[i]*f);
                  rBuf[iRBufPos+i]=c+c2;
                  if(c<0)c=-c;if(c>iMax)iMax=c;
               }
            }
            else{
               float f =32500.f;//float(SHRT_MAX) /2.001f;
               for(int i=0;i<iSamples;i++){
                  int c=(int)(p[i]*f);
                  rBuf[iRBufPos+i]=c;
                  if(c<0)c=-c;if(c>iMax)iMax=c;
               }
            }
            
            
            iRBufPos+=iSamples;
            
         }
         else{
            short  *p=(short  *)ioData->mBuffers[0].mData;
            for(int i=0;i<iSamples;i++){
               int c=p[i];
               if(c<0)c=-c;
               if(c>iMax)iMax=c;
            }
         }
         printf("rp%d max %d",iRBufPos,iMax);
         //new mic data
      }
#endif
      //todo cb
      return 0;
   }
   void stop_and_rel(){
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
         CTMutexAutoLock _al(mutexUA);
         rel();
      });
   }
   void stop(){
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
         CTMutexAutoLock _al(mutexUA);
         stopPR();
      });
   }
   int start(){
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
         CTMutexAutoLock _al(mutexUA);
   
         startPR();
      });
      return 0;
   }

private:
   void stopPR();
   int startPR();
   void rel();
   void init();
   
};



OSStatus    MyRenderer(void                 *inRefCon, 
                       AudioUnitRenderActionFlags  *ioActionFlags, 
                       const AudioTimeStamp        *inTimeStamp, 
                       UInt32                      inBusNumber, 
                       UInt32                      inNumberFrames, 
                       AudioBufferList             *ioData)

{
   CTMAudio *a=((CTMAudio*)inRefCon);
  // printf("z");
   if(!ioData){
    //  printf("x");
      OSStatus err = AudioUnitRender(a->getAU(), ioActionFlags, inTimeStamp, inBusNumber, inNumberFrames, a->bl);
      if(err){
         return err;
      }
      //return 0;
      ioData=a->bl;
      //printf("y");
      
   }
   
 //  static int cnt=0;
   //cnt++;
   
   a->onAudio(ioData, (int )inNumberFrames);
   //
   //printf("bn %d\n",inBusNumber);//ioData->mBuffers[0].mDataByteSize,inNumberFrames,cnt);
   
   return noErr;
}

// ________________________________________________________________________________
//
// CreateDefaultAU
//
void CTMAudio::init()
{
   if(iReInit || 1){
      iReInit=0;
      rel();
      iInitOk=0;
   }
   

   
   
   OSStatus err = noErr;
   if(iInitOk)return;
   iInitOk=0;

 //  UInt32 sessionCategory = kAudioSessionCategory_PlayAndRecord;
   //AudioSessionSetProperty(kAudioSessionProperty_AudioCategory, sizeof(sessionCategory), &sessionCategory);
   
   /*
    UInt32 ac = kAudioSessionCategory_PlayAndRecord;
    err = AudioSessionSetProperty(kAudioSessionProperty_AudioCategory, sizeof(audioCategory), &audioCategory);
    if (err != kAudioSessionNoError) {
    printf("err=%d",err);
    } 
    */
   // Open the default output unit
   /*
    
    float aBufferLength = 0.01; // In seconds
    AudioSessionSetProperty(kAudioSessionProperty_PreferredHardwareIOBufferDuration, 
    sizeof(aBufferLength), &aBufferLength);   
    */
#ifndef T_IPHONE
#define kAudioUnitSubType_RemoteIO kAudioUnitSubType_DefaultOutput
#endif
   
#ifdef T_IPHONE
   initAS();
   
   AudioComponentDescription desc;
   desc.componentType = kAudioUnitType_Output;//uScope==kAudioUnitScope_Input?kAudioUnitType_Output:kAudioUnitType_Input;
   //kAudioUnitSubType_GenericOutput
   desc.componentSubType = kAudioUnitSubType_RemoteIO;//kAudioUnitSubType_DefaultOutput;
   desc.componentManufacturer = kAudioUnitManufacturer_Apple;
   desc.componentFlags = 0;
   desc.componentFlagsMask = 0;
   
   AudioComponent inputComponent = AudioComponentFindNext(NULL, &desc);
   
   // Get audio units
   err = AudioComponentInstanceNew(inputComponent, &aUnit);   
   if (aUnit == NULL || err) { printf ("OpenAComponent=%ld\n", err); return; }
#endif
   
   
#ifndef T_IPHONE
   
   //kAudioUnitSubType_VoiceProcessingIO echo cancel
   ComponentDescription desc;
   //kAudioUnitSubType_VoiceProcessingIO
   desc.componentType = kAudioUnitType_Output;//uScope==kAudioUnitScope_Input?kAudioUnitType_Output:kAudioUnitType_Input;
   desc.componentSubType = iIsRecorder?kAudioUnitSubType_HALOutput:kAudioUnitSubType_RemoteIO;//kAudioUnitSubType_DefaultOutput;
   //   desc.componentSubType = iIsRecorder?kAudioUnitSubType_HALOutput:kAudioUnitSubType_RemoteIO;//kAudioUnitSubType_DefaultOutput;
   desc.componentManufacturer = kAudioUnitManufacturer_Apple;
   desc.componentFlags = 0;
   desc.componentFlagsMask = 0;
   
   Component comp = FindNextComponent(NULL, &desc);
   if (comp == NULL) { printf ("FindNextComponent\n"); return; }
   
   err = OpenAComponent(comp, &aUnit);
   if (comp == NULL) { printf ("OpenAComponent=%ld\n", err); return; }
#endif   
   // Set up a callback function to generate output to the output unit
   
   if(iIsRecorder){
      UInt32 iEnable = 1;
      
      /* Enable input */
      err = AudioUnitSetProperty(aUnit,
                                 kAudioOutputUnitProperty_EnableIO,
                                 kAudioUnitScope_Input,
                                 1,
                                 &iEnable,
                                 sizeof(iEnable));
      if (err != noErr) {
         printf("a err=%ld ",err);
      }
      
      /* Disable output */
      iEnable = 0;
      err = AudioUnitSetProperty(aUnit,
                                 kAudioOutputUnitProperty_EnableIO,
                                 kAudioUnitScope_Output,
                                 0,
                                 &iEnable,
                                 sizeof(iEnable));
      if (err != noErr) {
         printf("b err=%ld ",err);
      }
#ifndef T_IPHONE
#if 0
      UInt32 param  = sizeof(AudioDeviceID);
      err = AudioHardwareGetProperty(kAudioHardwarePropertyDefaultInputDevice, &param, &fInputDeviceID);
      if(err != noErr)
      {
         fprintf(stderr, "failed to get default input device\n");
         return ;
      }
#else
      fInputDeviceID=NewGetDefaultInputDevice();
#endif
      
      // Set the current device to the default input unit.
      err = AudioUnitSetProperty(aUnit, kAudioOutputUnitProperty_CurrentDevice, kAudioUnitScope_Global, 0, &fInputDeviceID, sizeof      (AudioDeviceID));
      
      if(err != noErr)
      {
         fprintf(stderr, "failed to set AU input device\n");
         return ;
      }
#endif      
   }   
   AURenderCallbackStruct input;
   input.inputProc = MyRenderer;
   input.inputProcRefCon = this;
   if(!iIsRecorder){
      
      err = AudioUnitSetProperty (aUnit, 
                                  kAudioUnitProperty_SetRenderCallback, 
                                  uScope,
                                  0, 
                                  &input, 
                                  sizeof(input));
   }else{
      err = AudioUnitSetProperty(aUnit, 
                                 kAudioOutputUnitProperty_SetInputCallback, 
                                 kAudioUnitScope_Global, 
                                 1, 
                                 &input, 
                                 sizeof(input));
   }
   if (err) { printf ("AudioUnitSetProperty-CB=%ld\n", err); return; }
   
   iInitOk=1;
   //printf("======aaae %d=========",iActive);

}

// ________________________________________________________________________________
//
// TestDefaultAU
//
int CTMAudio::startPR()
{
   if(iActive)return 0;
   init();

   if(iActive)return 0;

   iActive=2;
   OSStatus err = noErr;

   int iTry=0;
rep:
    Float64  rate =iRate;
     UInt32 sz=sizeof(rate);
    err=AudioSessionGetProperty(kAudioSessionProperty_CurrentHardwareSampleRate//kAudioSessionProperty_PreferredHardwareSampleRate
                                          									 ,&sz//sizeof(rate)
                                        									 , &rate); 
   printf("rr=%f ",rate);

#if 1
   streamFormat.mSampleRate			= err?iRate:rate;//44100.00;
   streamFormat.mFormatID			= kAudioFormatLinearPCM;
   streamFormat.mFormatFlags		=kLinearPCMFormatFlagIsSignedInteger 
   | kAudioFormatFlagsNativeEndian
   | kLinearPCMFormatFlagIsPacked
   | kAudioFormatFlagIsNonInterleaved;
   streamFormat.mFramesPerPacket	= 1;
   streamFormat.mChannelsPerFrame	= 1;
   streamFormat.mBitsPerChannel		= 32;
   streamFormat.mBytesPerPacket		= 4;
   streamFormat.mBytesPerFrame		= 4;   
#endif
   
   printf ("SampleRate=%f,", streamFormat.mSampleRate);
   printf("Rendering source:\n\t");
   printf ("BytesPerPacket=%ld,", streamFormat.mBytesPerPacket);
   printf ("FramesPerPacket=%ld,", streamFormat.mFramesPerPacket);
   printf ("BytesPerFrame=%ld,", streamFormat.mBytesPerFrame);
   printf ("BitsPerChannel=%ld,", streamFormat.mBitsPerChannel);
   printf ("ChannelsPerFrame=%ld\n", streamFormat.mChannelsPerFrame);
   
   err = AudioUnitSetProperty (aUnit,
                               kAudioUnitProperty_StreamFormat,
                               uScope,
                               0,
                               &streamFormat,
                               sizeof(AudioStreamBasicDescription));
    
   /*
    kAudioUnitErr_InvalidProperty			= -10879,
    kAudioUnitErr_InvalidParameter			= -10878,
    kAudioUnitErr_InvalidElement			= -10877,
    kAudioUnitErr_NoConnection				= -10876,
    kAudioUnitErr_FailedInitialization		= -10875,
    kAudioUnitErr_TooManyFramesToProcess	= -10874,
    kAudioUnitErr_InvalidFile				= -10871,
    kAudioUnitErr_FormatNotSupported		= -10868,
    kAudioUnitErr_Uninitialized				= -10867,
    kAudioUnitErr_InvalidScope				= -10866,
    kAudioUnitErr_PropertyNotWritable		= -10865,
    kAudioUnitErr_CannotDoInCurrentContext	= -10863,
    kAudioUnitErr_InvalidPropertyValue		= -10851,
    kAudioUnitErr_PropertyNotInUse			= -10850,
    kAudioUnitErr_Initialized				= -10849,
    kAudioUnitErr_InvalidOfflineRender		= -10848,
    kAudioUnitErr_Unauthorized				= -10847
  */  

   if(err  && iTry==0){

      iTry++;
      printf ("AudioUnitSetProperty-SF1=%4.4s, %ld %x\n", (char*)&err, err, (int)err);
      AudioStreamBasicDescription df={0};
      UInt32 size = sizeof(AudioStreamBasicDescription);
      
      err = AudioUnitGetProperty( aUnit,
                                 kAudioUnitProperty_StreamFormat,
                                 kAudioUnitScope_Input,//uScope,
                                 iIsRecorder,//kAudioUnitScope_Input==uScope?(AudioUnitElement)0:(AudioUnitElement)1,
                                 &df,
                                 &size);
      
      memcpy(&streamFormat,&df,size);
      printf ("AudioUnitSetProperty-SF1=%4.4s, %ld %x\n", (char*)&err, err, (int)err);
      
       //  streamFormat.mSampleRate=8000;
      err = AudioUnitSetProperty (aUnit,
                                  kAudioUnitProperty_StreamFormat,
                                  kAudioUnitScope_Input,
                                  iIsRecorder,
                                  &streamFormat,
                                  sizeof(AudioStreamBasicDescription));
   }
   if (err) { printf ("AudioUnitSetProperty-SF2=%4.4s, %ld\n", (char*)&err, err); return -1; }
   
   if(iIsRecorder){
      unsigned int as=256;
#ifndef T_IPHONE
      unsigned int defBuf=8000;
      UInt32 param = sizeof(UInt32);
      
      err = AudioUnitGetProperty(aUnit, kAudioDevicePropertyBufferFrameSize, kAudioUnitScope_Input, 1, &as, &param);
      if(!err){defBuf=as;}
      if(defBuf>512){
         printf("def %d ",defBuf);
         err = AudioUnitSetProperty(aUnit, kAudioDevicePropertyBufferFrameSize, kAudioUnitScope_Input, 1, &as, param);
         err = AudioUnitGetProperty(aUnit, kAudioDevicePropertyBufferFrameSize, kAudioUnitScope_Input, 1, &as, &param);
      }
#endif
    //  printf("as %d, bitsPR %ld,%ld\n",as, streamFormat.mBitsPerChannel, streamFormat.mBitsPerChannel);
      if(bl)DestroyAudioBufferList(bl);
      bl = AllocateAudioBufferList(2,as*streamFormat.mBitsPerChannel>>3);//streamFormat.mChannelsPerFrame, streamFormat * streamFormat.mBytesPerFrame);
      
   }
   if(0){
      printf ("SampleRate=%f,", streamFormat.mSampleRate);
      printf("Rendering source:\n\t");
      printf ("BytesPerPacket=%ld,", streamFormat.mBytesPerPacket);
      printf ("FramesPerPacket=%ld,", streamFormat.mFramesPerPacket);
      printf ("BytesPerFrame=%ld,", streamFormat.mBytesPerFrame);
      printf ("BitsPerChannel=%ld,", streamFormat.mBitsPerChannel);
      printf ("ChannelsPerFrame=%ld\n", streamFormat.mChannelsPerFrame);
   }

   err = AudioUnitInitialize(aUnit);
   if (err) { 
      if(err==-12985)iReInit=1;
      printf ("AudioUnitInitialize=%ld\n", err); 
      iActive=0;return -1; 
   }

   UInt32 size = sizeof(Float64);
   err = AudioUnitGetProperty (aUnit,
                               kAudioUnitProperty_SampleRate,
                               uScope,
                               0,
                               &outSampleRate,
                               &size);
   if (err) { 
      printf ("AudioUnitSetProperty-GF=%4.4s, %ld\n", (char*)&err, err); 
      iActive=0;
      AudioUnitUninitialize(aUnit);
      return -1; 
   }
   
   iMachineRate=(int)outSampleRate;
   printf("mr=%d\n",iMachineRate);
   
   // Start the rendering
   // The DefaultOutputUnit will do any format conversions to the format of the default device
   err = AudioOutputUnitStart (aUnit);
   if (err) { printf ("AudioOutputUnitStart=%ld\n", err);iActive=0;AudioUnitUninitialize(aUnit); return -1; }
   iActive=1;
   iNeedStop=0;
   
   int isAudioDevConnected();
   
   int r=isAudioDevConnected();
   if(!r)
      setAudioRouteRest(1,-1);
  // else
    //  setAudioRouteRest(0,-1);

   return 0;
}

void CTMAudio::stopPR(){
   OSStatus err = noErr;
   if(iNeedStop)return;
   if(iActive==2){usleep(20*1000);}


   if(iActive!=1 || iNeedStop){return;}
   iNeedStop=1;
   iActive=0;
   // we call the CFRunLoopRunInMode to service any notifications that the audio
   // system has to deal with
   
   // REALLY after you're finished playing STOP THE AUDIO OUTPUT UNIT!!!!!!    
   // but we never get here because we're running until the process is nuked...    
   // verify_noerr (
   AudioOutputUnitStop (aUnit);
   //);
   
   err = AudioUnitUninitialize (aUnit);
   if (err) { printf (" a AudioUnitUninitialize ao=%ld\n", err); return; }
   err = AudioUnitReset (aUnit, uScope, 0);
}

void CTMAudio::rel()
{
   // Clean up
   if(iInitOk==0)return;
   stopPR();
   iInitOk=0;
   
   if(aUnit)AudioComponentInstanceDispose (aUnit);
}

unsigned int getTickCount();

class CTVibrateOnce{
   int iDuplexPlaying;
   int iShouldVibrate;
   unsigned int uiT;
public:
   CTVibrateOnce(){uiT=0;iDuplexPlaying=0;iShouldVibrate=0;}
   
   void onPlay(){iDuplexPlaying=1;}
   
   void onStop(){
      iDuplexPlaying=0;
      if(iShouldVibrate){
         iShouldVibrate=0;
         int d=getTickCount()-uiT;
         if(d<5000)
            vibrate();
      }
   }
   
   void vibrate(){
      dispatch_async(dispatch_get_main_queue(), ^(void) {
         if(iDuplexPlaying){
            iShouldVibrate=1;
            uiT=getTickCount();
         }
         else{
            iShouldVibrate=0;
            AudioServicesPlaySystemSound (kSystemSoundID_Vibrate);
         }
      });
   }

};

static CTVibrateOnce vOnce;

#pragma mark CTMAudioDuplex class


class CTMAudioDuplex{

   AudioUnit   aUnit;
   int iRate,iChannels;
   int iACardId;
   int iInitOk;
   int iActive;
   int iNeedStop;
   
   int iAudioSessionSetActiveInUse;
   
   
   int iIsFirstPacket;
   
   int iRecStarted;
   int iPlayStarted;
   
   Float64 outSampleRate;
   
   AudioStreamBasicDescription sF_play;
   AudioStreamBasicDescription sF_rec;
#ifndef T_IPHONE   
   AudioDeviceID   fInputDeviceID;
#endif
public:   
   int iReInit;
   AudioBufferList *bl;
   AudioUnit getAU(){return aUnit;}
   
   void *cbPlayData;
   void *cbRecData;
   
   int (*cbFncPlay)(void *, short *, int ,int iRestarted);
   int (*cbFncRec)(void *, short *, int );
   
   UInt32 theFormatID ;//= kAudioFormatLinearPCM;
   
   // these are set based on which format is chosen
   UInt32 theFormatFlags;// = kLinearPCMFormatFlagIsSignedInteger| kLinearPCMFormatFlagIsPacked;
   UInt32 theBytesInAPacket ;
   UInt32 theBitsPerChannel ;
   UInt32 theBytesPerFrame ;
   
   // these are the same regardless of format
   UInt32 theFramesPerPacket ;//= 1; // this shouldn't change
   
   
   unsigned int uiPrevOverFlowAt;
   int iTryingRestart;
   int iAudioDetected;
   
public:
   void on_overFlow(){
      int iSt=iRecStarted || iPlayStarted;
      printf("iActive=%d iNeedStop=%d iSt=%d",iActive,iNeedStop ,iSt);
      if(iTryingRestart  || !iSt)return;
      unsigned int ui=getTickCount();
      if(iAudioDetected)uiPrevOverFlowAt=0;
      
      if(!iAudioDetected && uiPrevOverFlowAt){
         int d=(int)(ui-uiPrevOverFlowAt);
         printf("[dif %d]",d);
         if(d>2000 && d<20000){
            {
               CTMutexAutoLock _al(mutexDA);
               if(iTryingRestart)return;
               
               iTryingRestart=1;
               uiPrevOverFlowAt=ui+1000;
               
               stop();
               rel();
               puts("restart audio");
            }//unlocks mutex
            if(iSt)
               start();
            iTryingRestart=0;
         }
      }
      if(uiPrevOverFlowAt)return;
      uiPrevOverFlowAt=ui;
      iAudioDetected=0;
      puts("Posible audio playback is stoped");
   }
   int iUseAudioSessionSetActive;
   CTMAudioDuplex(int iRate, int iCh=1, int iACardId=0)
   :iRate(iRate),iChannels(iCh),iACardId(iACardId){
      iIsStoping=0;
      iAudioDetected=0;
      iTryingRestart=0;
      iIsFirstPacket=1;
      uiPrevOverFlowAt=0;
      theFormatID=kAudioFormatLinearPCM;
      theFramesPerPacket=1;
      iAudioSessionSetActiveInUse=0;
      iUseAudioSessionSetActive=1;
      cbFncRec=NULL;
      cbFncPlay=NULL;
      cbPlayData=NULL;
      cbRecData=NULL;
      iRecStarted=iRecStarted=0;
      aUnit=NULL;
      iInitOk=0;
      iActive=0;
      iNeedStop=1;
      outSampleRate=0;
      iReInit=0;
#ifndef T_IPHONE
      fInputDeviceID=0;
#endif
      memset(&sF_play,0,sizeof(AudioStreamBasicDescription));
      memset(&sF_rec,0,sizeof(AudioStreamBasicDescription));
      
      theFormatFlags =  kLinearPCMFormatFlagIsSignedInteger 
      | kAudioFormatFlagsNativeEndian
      | kLinearPCMFormatFlagIsPacked
      | kAudioFormatFlagIsNonInterleaved;
      theBytesPerFrame = theBytesInAPacket = 2;
      theBitsPerChannel = 16;  
      
      bl=NULL;
   }
   ~CTMAudioDuplex(){
      int iWasActive=iActive;
      stop();
      if(iWasActive)::usleep(40*1000);//40ms
      rel();
      if(bl)DestroyAudioBufferList(bl);
   }
   int onAudio(AudioBufferList *ioData, int iSamples, int iIsRec){
      AudioStreamBasicDescription *sf=iIsRec?&sF_rec:&sF_play;
      
      
      if(!iIsRec){
         iAudioDetected=1;
         uiPrevOverFlowAt=0;
      }
  
      if(iSamples*(sf->mBitsPerChannel>>3)!=ioData->mBuffers[0].mDataByteSize){
         puts("--f--");
         return -1;
      }
      if(iIsRec){
         if(iRecStarted && cbFncRec)cbFncRec(cbRecData,(short  *)ioData->mBuffers[0].mData,iSamples);
      }
      else {
         if(!iPlayStarted){
            memset(ioData->mBuffers[0].mData,0,ioData->mBuffers[0].mDataByteSize);
            return 0;
         }
         if(iPlayStarted && cbFncPlay){
         //   empty queve if restart max 100ms
            cbFncPlay(cbPlayData,(short  *)ioData->mBuffers[0].mData,iSamples,iIsFirstPacket);
            iIsFirstPacket=0;
         }
      }
      return 0;
      
   }
   int startRec(void *p, int (*cbFnc)(void *, short *, int )){
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
         
         cbRecData=p;
         cbFncRec=cbFnc;
         iRecStarted=1;
         start();
      });
      return 0;
      
   }
   int startPlay(void *p, int (*cbFnc)(void *, short *, int , int iRestarted)){
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
         
         
         iIsFirstPacket=1;
         cbPlayData=p;
         cbFncPlay=cbFnc;
         iPlayStarted=1;
         
         start();
      });
      return 0;
   }
   void stopRec(){
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
         iRecStarted=0;
         if(!iPlayStarted){
            stop_rel();
         }
      });
   }
   void stopPlay(){
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
         iPlayStarted=0;
         if(!iRecStarted){
            stop_rel();
         }
      });
   }
private:
   int start();
   void stop();
   
   int iIsStoping;
   
   void stop_rel(){
      
      if(iIsStoping)return;
      
      CTMutexAutoLock _al(mutexDA);
      if(iIsStoping)return;
      
      iIsStoping=1;
      
      stop();
      rel();
      
      vOnce.onStop();
      
      iIsStoping=0;
   
   }
   void rel();
   void init();
   
};

OSStatus    myRendererDuplexR(void                 *inRefCon, 
                              AudioUnitRenderActionFlags  *ioActionFlags, 
                              const AudioTimeStamp        *inTimeStamp, 
                              UInt32                      inBusNumber, 
                              UInt32                      inNumberFrames, 
                              AudioBufferList             *ioData)

{
   CTMAudioDuplex *a=((CTMAudioDuplex*)inRefCon);
   
   if(!ioData){
      ioData=a->bl;
      ioData->mBuffers[0].mData=NULL;
      
      OSStatus err = AudioUnitRender(a->getAU(), ioActionFlags, inTimeStamp, inBusNumber, inNumberFrames, ioData);
      if(err){
         return err;
      }
      
   }

   
   a->onAudio(ioData, (int )inNumberFrames,1);
   
   return noErr;
}
OSStatus    myRendererDuplexP(void                 *inRefCon, 
                              AudioUnitRenderActionFlags  *ioActionFlags, 
                              const AudioTimeStamp        *inTimeStamp, 
                              UInt32                      inBusNumber, 
                              UInt32                      inNumberFrames, 
                              AudioBufferList             *ioData)

{
   CTMAudioDuplex *a=((CTMAudioDuplex*)inRefCon);
   if(!ioData){
      OSStatus err = AudioUnitRender(a->getAU(), ioActionFlags, inTimeStamp, inBusNumber, inNumberFrames, a->bl);
      if(err){
         return err;
      }
      ioData=a->bl;
   }
   
   a->onAudio(ioData, (int )inNumberFrames,0);
   //   static int cnt=0;cnt++;    printf("play sz=%ld fr=%ld %d\n",ioData->mBuffers[0].mDataByteSize,inNumberFrames,cnt);
   return noErr;
}
#pragma mark CTMAudioDuplex fnc



void CTMAudioDuplex::rel()
{
   
   if(iInitOk==0)return;
   iInitOk=0;

   iNeedStop=1;
   iActive=0;   
   OSStatus e=AudioComponentInstanceDispose (aUnit);
   if(e)printf("rel--ok %ld\n",e);
   
}


void CTMAudioDuplex::init(){
   OSStatus err = noErr;
   if(iReInit){
      iReInit=0;
   
      rel();
   }
   if(iInitOk)return;
   
   iInitOk=0;
   
   initAS();
   int reps=0;
rep1:
   UInt32 sessionCategory = kAudioSessionCategory_PlayAndRecord;
   err = AudioSessionSetProperty(kAudioSessionProperty_AudioCategory, sizeof(sessionCategory), &sessionCategory);
   if(!reps && err == kAudioSessionNotInitialized){
      
      initAS(1);
      reps=1;
      goto rep1;
   }
   
   UInt32 b=1;
   
   err = AudioSessionSetProperty (kAudioSessionProperty_OverrideCategoryEnableBluetoothInput,
                            sizeof(b), &b);
   
   // Open the default output unit
   /*
    
    float aBufferLength = 0.01; // In seconds
    AudioSessionSetProperty(kAudioSessionProperty_PreferredHardwareIOBufferDuration, 
    sizeof(aBufferLength), &aBufferLength);   
    */
#ifndef T_IPHONE
#undef kAudioUnitSubType_RemoteIO
#define kAudioUnitSubType_RemoteIO kAudioUnitSubType_VoiceProcessingIO
#endif
   //kAudioUnitSubType_VoiceProcessingIO echo cancel
   AudioComponentDescription desc;
   desc.componentType = kAudioUnitType_Output;//uScope==kAudioUnitScope_Input?kAudioUnitType_Output:kAudioUnitType_Input;
   // desc.componentSubType = kAudioUnitSubType_RemoteIO;
   desc.componentSubType = kAudioUnitSubType_VoiceProcessingIO;
   desc.componentManufacturer = kAudioUnitManufacturer_Apple;
   desc.componentFlags = 0;
   desc.componentFlagsMask = 0;
   
#if 0
   Component comp = FindNextComponent(NULL, &desc);
   if (comp == NULL) { printf ("FindNextComponent\n"); return; }
   
   err = OpenAComponent(comp, &aUnit);
#else
   AudioComponent comp = AudioComponentFindNext(NULL, &desc);
   if (comp == NULL) { printf ("AudioComponentFindNext\n"); return; }
   
   // Get audio units
   err = AudioComponentInstanceNew(comp, &aUnit);   
#endif
   if (aUnit == NULL || err) { printf ("OpenAComponent=%ld\n", err); return; }
   // Set up a callback function to generate output to the output unit
   
   if(1){
      UInt32 size=4,iEnable = 1;
      
      // Enable input 
      err = AudioUnitSetProperty(aUnit,
                                 kAudioOutputUnitProperty_EnableIO,
                                 kAudioUnitScope_Input,
                                 1,
                                 &iEnable,
                                 size);
      if (err != noErr) {
         printf("a err=%ld ",err);
      }
      
      // Enable output 
      iEnable = 1;
      err = AudioUnitSetProperty(aUnit,
                                 kAudioOutputUnitProperty_EnableIO,
                                 kAudioUnitScope_Output,
                                 0,
                                 &iEnable,
                                 size);
      if (err != noErr) {
         printf("b err=%ld ",err);
      }
#ifndef T_IPHONE
      err=0;
      fInputDeviceID=NewGetDefaultInputDevice();
      err = AudioUnitSetProperty(aUnit, kAudioOutputUnitProperty_CurrentDevice, kAudioUnitScope_Global, 0, &fInputDeviceID, sizeof      (AudioDeviceID));
#endif      
      if(err != noErr)
      {
         fprintf(stderr, "failed to set AU input device %ld\n", err);
         return ;
      }
      
   }   
   AURenderCallbackStruct inputR;
   inputR.inputProc = myRendererDuplexR;
   inputR.inputProcRefCon = this;
   AURenderCallbackStruct inputP;
   inputP.inputProc = myRendererDuplexP;
   inputP.inputProcRefCon = this;
   //play      
   err = AudioUnitSetProperty (aUnit, 
                               kAudioUnitProperty_SetRenderCallback, 
                               kAudioUnitScope_Input,
                               0, 
                               &inputP, 
                               sizeof(inputP));
   //mic
   err += AudioUnitSetProperty(aUnit, 
                               kAudioOutputUnitProperty_SetInputCallback, 
                               kAudioUnitScope_Global, 
                               1, 
                               &inputR, 
                               sizeof(inputR));
   
   if (err) { printf ("AudioUnitSetProperty-CB=%ld\n", err); return; }
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
   
   
   //AudioSessionInitialize(NULL, NULL, NULL,NULL);//interruptionListener, cf) 
   
   
   iInitOk=1;
   
}
int CTMAudioDuplex::start(){
   
   if(iActive)return 0;
   iIsFirstPacket=1;

   
   CTMutexAutoLock _al(mutexDA);
   if(iActive)return 0;

   init();


   if(iActive)return 0;
   
   vOnce.onPlay();
   
   iActive=2;
   OSStatus err = noErr;
   
   // We tell the Output Unit what format we're going to supply data to it
   // this is necessary if you're providing data through an input callback
   // AND you want the DefaultOutputUnit to do any format conversions
   // necessary from your format to the device's format.
   UInt32 size = sizeof(Float64);
   err = AudioUnitGetProperty (aUnit,
                               kAudioUnitProperty_SampleRate,
                               kAudioUnitScope_Input,
                               1,
                               &outSampleRate,
                               &size);
   if (err) { printf ("AudioUnitSetProperty-GF=%4.4s, %ld\n", (char*)&err, err); return -1; }
   
   printf("rate =%f\n",outSampleRate);
   
   
   sF_play.mSampleRate = iRate;//outSampleRate?outSampleRate:iRate;//16000;// sSampleRate;     //  the sample rate of the audio stream
   sF_play.mFormatID = theFormatID;           //  the specific encoding type of audio stream
   sF_play.mFormatFlags = theFormatFlags;     //  flags specific to each format
   sF_play.mBytesPerPacket = theBytesInAPacket;   
   sF_play.mFramesPerPacket = theFramesPerPacket; 
   sF_play.mBytesPerFrame = theBytesPerFrame;     
   sF_play.mChannelsPerFrame = 1;//sNumChannels;  
   sF_play.mBitsPerChannel = theBitsPerChannel;   
   
   int iTry=0;
rep:
   /*
    theFormatFlags =  kAudioFormatFlagsNativeFloatPacked
    | kAudioFormatFlagIsNonInterleaved;
    theBytesPerFrame = theBytesInAPacket = 4;
    theBitsPerChannel = 32;
    */
#if 0

   printf ("SampleRate=%f,", sF_play.mSampleRate);
   printf("Rendering source:\n\t");
   printf ("BytesPerPacket=%ld,", sF_play.mBytesPerPacket);
   printf ("FramesPerPacket=%ld,", sF_play.mFramesPerPacket);
   printf ("BytesPerFrame=%ld,", sF_play.mBytesPerFrame);
   printf ("BitsPerChannel=%ld,", sF_play.mBitsPerChannel);
   printf ("ChannelsPerFrame=%ld\n", sF_play.mChannelsPerFrame);

#endif
   err = AudioUnitSetProperty (aUnit,
                               kAudioUnitProperty_StreamFormat,
                               kAudioUnitScope_Output,
                               1,
                               &sF_play,
                               sizeof(AudioStreamBasicDescription));
   
   if(err)printf ("AudioUnitSetProperty-SF1x=%4.4s, %ld %x\n", (char*)&err, err, (int)err);
   err = AudioUnitSetProperty (aUnit,
                               kAudioUnitProperty_StreamFormat,
                               kAudioUnitScope_Input,
                               0,
                               &sF_play,
                               sizeof(AudioStreamBasicDescription));
   if(err)printf ("AudioUnitSetProperty-SF2x=%4.4s, %ld %x\n", (char*)&err, err, (int)err);
   memcpy(&sF_rec,&sF_play,sizeof(AudioStreamBasicDescription));
   
   if(err  && iTry==0){

      iTry++;
      
      AudioStreamBasicDescription df={0};
      UInt32 size = sizeof(AudioStreamBasicDescription);
      
      err = AudioUnitGetProperty( aUnit,
                                 kAudioUnitProperty_StreamFormat,
                                 kAudioUnitScope_Input,//uScope,
                                 0,//kAudioUnitScope_Input==uScope?(AudioUnitElement)0:(AudioUnitElement)1,
                                 &df,
                                 &size);
      if(err){printf("g ai err=%ld",err);}
      memcpy(&sF_play,&df,size);
      err = AudioUnitSetProperty( aUnit,
                                 kAudioUnitProperty_StreamFormat,
                                 kAudioUnitScope_Input,//uScope,
                                 0,//kAudioUnitScope_Input==uScope?(AudioUnitElement)0:(AudioUnitElement)1,
                                 &sF_play,
                                 size);
      
      if (err) { printf ("AudioUnitSetProperty-SF2=%4.4s, %ld\n", (char*)&err, err); return -1; }
      
      err = AudioUnitGetProperty( aUnit,
                                 kAudioUnitProperty_StreamFormat,
                                 kAudioUnitScope_Output,//uScope,
                                 1,//kAudioUnitScope_Input==uScope?(AudioUnitElement)0:(AudioUnitElement)1,
                                 &df,
                                 &size);
      if(err){printf("g ai err=%ld",err);}
      memcpy(&sF_rec,&df,size);
      err = AudioUnitSetProperty( aUnit,
                                 kAudioUnitProperty_StreamFormat,
                                 kAudioUnitScope_Output,//uScope,
                                 1,//kAudioUnitScope_Input==uScope?(AudioUnitElement)0:(AudioUnitElement)1,
                                 &sF_rec,
                                 size);
      
   }

   
   if (err) { printf ("AudioUnitSetProperty-SF3=%4.4s, %ld\n", (char*)&err, err); return -1; }
   /*
   UInt32 audiobypassProcessing = 1;
   err = AudioUnitSetProperty(aUnit, kAUVoiceIOProperty_BypassVoiceProcessing,
                                 kAudioUnitScope_Global, 1, &audiobypassProcessing, sizeof(audiobypassProcessing));
   */
   
   if(1){
      unsigned int as=256;
#ifndef T_IPHONE     
      UInt32 param = sizeof(UInt32);
      unsigned int defBuf=8000;
      err = AudioUnitGetProperty(aUnit, kAudioDevicePropertyBufferFrameSize, kAudioUnitScope_Input, 1, &as, &param);
      if(!err){defBuf=as;}
      if(defBuf>512){
         printf("def %d ",defBuf);
         err = AudioUnitSetProperty(aUnit, kAudioDevicePropertyBufferFrameSize, kAudioUnitScope_Input, 1, &as, param);
         err = AudioUnitGetProperty(aUnit, kAudioDevicePropertyBufferFrameSize, kAudioUnitScope_Input, 1, &as, &param);
      }
#endif
      printf("as %d, bitsPR %ld,%ld\n",as, sF_rec.mBitsPerChannel, sF_play.mBitsPerChannel);
      if(bl)DestroyAudioBufferList(bl);
      bl = AllocateAudioBufferList(sF_rec.mChannelsPerFrame,as*sF_rec.mBitsPerChannel>>3);//streamFormat.mChannelsPerFrame, streamFormat * streamFormat.mBytesPerFrame);
      
   }
   
   // Initialize unit
   err = AudioUnitInitialize(aUnit);
   if (err) { if(err==-12985)iReInit=1;printf ("AudioUnitInitialize=%ld\n", err); 
      iActive=0;
      goto rel; 
   }

   err = AudioOutputUnitStart (aUnit);
   if (err) { printf ("AudioOutputUnitStart aod=%ld\n", err);iActive=0; goto rel1; }
   iActive=1;
   iNeedStop=0;
   
   if(iUseAudioSessionSetActive)
      iAudioSessionSetActiveInUse=1;
   
   if(iAudioSessionSetActiveInUse)AudioSessionSetActive( true );
   
   setAudioRouteRest(0,1);
   
   //puts("start");
   return 0;
rel1:
   AudioUnitUninitialize(aUnit);
   
   
rel:
   if(aUnit){
      err = AudioUnitReset (aUnit, kAudioUnitScope_Global, 1);
      err|= AudioUnitReset (aUnit, kAudioUnitScope_Global, 0);
      if (err) { printf ("AudioUnitReset=%ld\n", err); }
   }

   return -1;
   
}
void CTMAudioDuplex::stop(){
   OSStatus err = noErr;
   if(iNeedStop)return;
   if(iActive!=1)return;

   iNeedStop=1;
   iActive=0;
   /*
   int setAudioRoute(int iLoudSpkr);
   int e=setAudioRoute(1);
   if(e<0)printf("setAudioRoute()=%d %x %x",e,e,-e);
*/
   // we call the CFRunLoopRunInMode to service any notifications that the audio
   // system has to deal with
   
   // REALLY after you're finished playing STOP THE AUDIO OUTPUT UNIT!!!!!!    
   // but we never get here because we're running until the process is nuked...    
   // verify_noerr (

   
   if(aUnit)AudioOutputUnitStop (aUnit);
   
   if(aUnit){
      err = AudioUnitReset (aUnit, kAudioUnitScope_Global, 1);
      if (err) { printf ("AudioUnitReset1=%ld\n", err);  }
      err= AudioUnitReset (aUnit, kAudioUnitScope_Global, 0);
      if (err) { printf ("AudioUnitReset2=%ld\n", err);  }
     // err= AudioUnitReset (aUnit, kAudioUnitScope_Output, 1);
     // if (err) { printf ("AudioUnitReset3=%ld\n", err);  }
      err= AudioUnitReset (aUnit, kAudioUnitScope_Input, 0);
      if (err) { printf ("AudioUnitReset4=%ld\n", err);  }
   }
   
   if(iAudioSessionSetActiveInUse)AudioSessionSetActive( false );
   iAudioSessionSetActiveInUse=0;
 
   if(aUnit)err = AudioUnitUninitialize (aUnit);
   if (err) { printf ("AudioUnitUninitialize=%ld\n", err); return; }
   
}
/*
 Float32 l1, l2;
 UInt32 size = sizeof(Float32);
 
 if ((AudioSessionGetProperty(kAudioSessionProperty_CurrentHardwareInputLatency,&size, &l1) == kAudioSessionNoError) &&
     (AudioSessionGetProperty(kAudioSessionProperty_CurrentHardwareIOBufferDuration, &size, &l2) == kAudioSessionNoError))
 {
     unsigned int iLatencyMS = (unsigned int) ((k1 + l2) * 1000);

 }
 
 */

/*
 BOOL isHeadsetPluggedIn() {
 UInt32 routeSize = sizeof (CFStringRef);
 CFStringRef route;
 
 OSStatus error = AudioSessionGetProperty (kAudioSessionProperty_AudioRoute,
 &routeSize,
 &route
 );    
 NSLog(@"%@", route);
 
 BOOL isPluggedIn = !error && (route != NULL) && ([(NSString*)route rangeOfString:@"Head"].location != NSNotFound);
 
 
 
 return (isPluggedIn);
 */
/*
 // CFDictionary keys for kAudioSessionProperty_AudioRouteDescription    
 // Available in iOS 5.0 or greater    
 extern const CFStringRef   kAudioSession_AudioRouteKey_Inputs   __OSX_AVAILABLE_STARTING(__MAC_NA,__IPHONE_5_0);
 extern const CFStringRef   kAudioSession_AudioRouteKey_Outputs  __OSX_AVAILABLE_STARTING(__MAC_NA,__IPHONE_5_0);
 
 // key(s) for the CFDictionary associated with each entry of the CFArrays returned by kAudioSession_AudioRouteKey_Inputs
 // and kAudioSession_AudioRouteKey_Outputs.  
 // Available in iOS 5.0 or greater        
 extern const CFStringRef   kAudioSession_AudioRouteKey_Type     __OSX_AVAILABLE_STARTING(__MAC_NA,__IPHONE_5_0);
 
 #define COMPARE(FIRST,SECOND) CFStringCompare(FIRST, SECOND, kCFCompareCaseInsensitive)
 
 -(BOOL)shouldOverrideAudioRoute {
 
 CFDictionaryRef route;
 UInt32 size = sizeof (route);
 
 OSStatus status = AudioSessionGetProperty (kAudioSessionProperty_AudioRouteDescription,&size, &route);
 if (status != noErr) {
 OpenEarsLog(@”Error %d: Unable to get property.”, (int)status);
 return NO;
 }
 
 CFArrayRef outputs = CFDictionaryGetValue(route, kAudioSession_AudioRouteKey_Outputs);
 BOOL shouldOverride = NO;
 
 for(CFIndex i = 0, c = CFArrayGetCount(outputs); i < c; i++)
 {
 CFDictionaryRef item = CFArrayGetValueAtIndex(outputs, i);
 CFStringRef device = CFDictionaryGetValue(item, kAudioSession_AudioRouteKey_Type);
 
 OpenEarsLog(@"Output Device: %@.",(NSString*)device);
 
 if(COMPARE(device,	 kAudioSessionOutputRoute_LineOut)) {
 shouldOverride = NO;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_Headphones)) {
 shouldOverride = NO;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_BluetoothHFP)) {
 shouldOverride = NO;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_BluetoothA2DP)) {
 shouldOverride = NO;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_BuiltInReceiver)) {
 shouldOverride = YES;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_BuiltInSpeaker)) {
 shouldOverride = NO;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_USBAudio)) {
 shouldOverride = NO;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_HDMI)) {
 shouldOverride = NO;
 } else if(COMPARE(device,	kAudioSessionOutputRoute_AirPlay)) {
 shouldOverride = NO;
 }
 }
 
 return shouldOverride;
 }
 */



int _setAudioRouteRest(int iLoudSpkr, int iRest, int BT){
  // return iLoudSpkr;
   static int iPrevSpkr=-1;
   static int iPrevBT=1;
   
   if(iRest==1)iLoudSpkr=iPrevSpkr;
   if(iRest==1)BT=iPrevBT;
   
   OSStatus o;
   int reps=0;
   

rep:
   reps++;
   //kAudioSessionProperty_OverrideCategoryDefaultToSpeaker
   UInt32 dst =iLoudSpkr ?
   kAudioSessionOverrideAudioRoute_Speaker :
   kAudioSessionOverrideAudioRoute_None;

   
   if(iRest==0)iPrevSpkr=iLoudSpkr;
   if(iRest==0)iPrevBT=BT;
   
   if(ctBTUsage.isBTAvailable() && !iLoudSpkr){
      ctBTUsage.shouldSwitchToBT();
   }
   /*
   if(!iLoudSpkr){
      if(!reps)initAS(1);//TODO BT test
      UInt32 sessionCategory = kAudioSessionCategory_PlayAndRecord;
      AudioSessionSetProperty(kAudioSessionProperty_AudioCategory, sizeof(sessionCategory), &sessionCategory);
      
      UInt32 b=(UInt32)BT;
      
      o = AudioSessionSetProperty (
                                   kAudioSessionProperty_OverrideCategoryEnableBluetoothInput,
                                   sizeof(b), &b);
      printf("[AudioSessionSetProperty(BT)=%ld {%4.s} set=%lu]",o, (char*)&o, b);
      
      
       UInt32 sz=sizeof(b);
       
       AudioSessionGetProperty(kAudioSessionProperty_OverrideCategoryEnableBluetoothInput,&sz,&b);
       
       printf("[AudioSessionGetProperty(BT)=%lu sz=%lu]", b,sz);
      
      
   }
   */
#if 1

   
   o = AudioSessionSetProperty (
                                kAudioSessionProperty_OverrideAudioRoute,
                                sizeof(dst), &dst);
   
#else
   UInt32 defaultToSpeaker = dst;
   o = AudioSessionSetProperty (kAudioSessionProperty_OverrideCategoryDefaultToSpeaker,                        
                                     sizeof (defaultToSpeaker),                                   
                                     &defaultToSpeaker                               
                                     );
#endif
   
   if(o == kAudioSessionNotInitialized && reps==1){
      initAS(1);
      goto rep;
   }
   
   if (o != kAudioSessionNoError) {//560557673
      
      return o>0?-o:o;
   }

   return iLoudSpkr;
}

int setAudioRouteRest(int iLoudSpkr, int iRest){
   return _setAudioRouteRest(iLoudSpkr,iRest,1);
}

int switchToEarpiece(){
   return _setAudioRouteRest(0,0,0);
}

int setAudioRoute(int iLoudSpkr){//call only from main thread
   return setAudioRouteRest(iLoudSpkr,0);
}



//int mainz(int argc, const char * argv[])
void *t_native_duplex_init(int iRate){
   CTMAudioDuplex *a=new CTMAudioDuplex(iRate);
   return a;
}

void *t_native_startCB_play(void *ctx, int iRate, int (*cbFnc)(void *, short *, int , int iRestarted), void *cbData){
   CTMAudioDuplex *a=(CTMAudioDuplex*)ctx; 
   if(!a){
      a=new CTMAudioDuplex(iRate);
   }
   a->startPlay(cbData,cbFnc);
   return a;
   
}

void *t_native_startCB_rec(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
   CTMAudioDuplex *a=(CTMAudioDuplex*)ctx; 
   if(!a){
      a=new CTMAudioDuplex(iRate);
   }
   
   a->startRec(cbData,cbFnc);
   return a;
}

void t_onOverFlow(void *ctx){
  
   if(!ctx)return;
   CTMAudioDuplex *a=(CTMAudioDuplex*)ctx; 
   a->on_overFlow();
   
}

void t_native_stop(void *ctx, int iPlay){
   
   CTMAudioDuplex *a=(CTMAudioDuplex*)ctx; 
   if(a){
      if(iPlay)a->stopPlay();else a->stopRec();
   }
   
   return;
}

void t_native_rel(void *ctx){
   CTMAudioDuplex *a=(CTMAudioDuplex*)ctx;
   if(a)delete a;
   return;
}

void *getAudioCtx(int iRate, int iRel){
   static void *ctx;
   if(iRel){if(ctx){void *p=ctx;ctx=NULL;t_native_rel(p);}return NULL;}
   if(!ctx)ctx=t_native_duplex_init(iRate);
   return ctx;
}

void *getPAudioCtx(int iRate){
   CTMAudio *p=new CTMAudio(0,iRate,kAudioUnitScope_Input);
   return p;
}
static int iRel=0;
void t_native_Prel(void *ctx){
   if(iRel)return;
   iRel=1;
   CTMAudio *a=(CTMAudio*)ctx; 
   usleep(50*1000);
   if(a)delete a;
   return;
}

void *t_native_PstartCB(void *ctx, int iRate, int (*cbFnc)(void *, short *, int ), void *cbData){
   if(!ctx)ctx=getPAudioCtx(iRate);
   CTMAudio *a=(CTMAudio*)ctx; 
   a->startRecPlay(cbData,cbFnc);

   return a;
}

void t_native_Pstop(void *ctx){
   if(!ctx)return;
   if(iRel)return;
   CTMAudio *a=(CTMAudio*)ctx; 
   if(a){a->stop_and_rel();}
   return;
}
int t_native_PRate(void *ctx){
   CTMAudio *a=(CTMAudio*)ctx; 
   return (a)?a->iMachineRate:8000;
}
//http://iphonedevwiki.net/index.php/AudioServices

//afconvert -f caff -d aac  -c 1 telephone-ring.wav ring.caf

static SystemSoundID mySSID=0;
/*
 extern OSStatus 
 AudioServicesAddSystemSoundCompletion(  SystemSoundID          					inSystemSoundID,
 CFRunLoopRef                        	inRunLoop,
 CFStringRef                        		inRunLoopMode,
 AudioServicesSystemSoundCompletionProc  inCompletionRoutine,
 void*                               	inClientData)
 AudioServicesRemoveSystemSoundCompletion(SystemSoundID inSystemSoundID)
 */
int iStopRingTone=1;
int iRingRepCount=0;

int iVibrateRepCount=0;

void stoRingTone(){
   iStopRingTone=1;
   
   if(mySSID){
      SystemSoundID ssid=mySSID;
      mySSID=0;
      AudioServicesRemoveSystemSoundCompletion(ssid);
      AudioServicesDisposeSystemSoundID(ssid);
   }
}
static int iRingIsInBackGround=0;
int g_canRing();

void audioServicesSystemSoundCompletionProc(  SystemSoundID  ssID, 
                                        	void*          clientData){
   
   if(ssID==mySSID || (iRingIsInBackGround && ssID==kSystemSoundID_Vibrate)){
      printf("rep s ring %d\n",iRingIsInBackGround);
      if(iStopRingTone==0){
         if((iRingRepCount&15)==15){
            //TODO ceh
            
            if(!g_canRing()){
               stoRingTone();
               return;
            }
         }
         iRingRepCount++;
         
         if(!iRingIsInBackGround){
            if(iRingRepCount>=60){
               stoRingTone();
               return;
            }
            
            AudioServicesPlayAlertSound(mySSID);
          //  AudioServicesPlaySystemSound (kSystemSoundID_Vibrate);
         }
         
         if(iRingIsInBackGround){
            if(iRingRepCount==15){
               stoRingTone();
               return;
            }
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
               sleep(2);
               if(iStopRingTone==0)
                  AudioServicesPlaySystemSound (kSystemSoundID_Vibrate);
            });
         }
      }
   }
}

void audioServicesSystemSoundCompletionProcVibrate(  SystemSoundID  ssID, 
                                            void*          clientData){
   
   if(ssID==kSystemSoundID_Vibrate){
      printf("rep v ring %d\n",iRingIsInBackGround);
      if(iStopRingTone==0){
         if((iVibrateRepCount&15)==15){
            //TODO ceh
            int g_canRing();
            if(!g_canRing()){
               stoRingTone();
               return;
            }
         }
         iVibrateRepCount++;
         

         if(iVibrateRepCount==15){
            stoRingTone();
            return;
         }
         dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
            sleep(2);
            if(iStopRingTone==0)
               AudioServicesPlaySystemSound (kSystemSoundID_Vibrate);
         });
      }
   }
}


void testFNC(){
#if 1
   UInt32 size = sizeof(CFArrayRef);
   CFArrayRef audioOutputRoutes;
   
   OSStatus err =
   AudioSessionGetProperty(kAudioSessionProperty_OutputDestinations,
                           &size, &audioOutputRoutes);
   
   if (err == noErr){
      CFIndex i, c = CFArrayGetCount(audioOutputRoutes);
      CFDictionaryRef audioOutput;
      
      for (i=0; i<c; i++) {
         audioOutput =
         (CFDictionaryRef)CFArrayGetValueAtIndex(audioOutputRoutes, i);
         

         CFStringRef routeDescription =
         (CFStringRef)CFDictionaryGetValue(audioOutput,
                                           kAudioSession_OutputDestinationKey_Description);
         void apple_log_CFStr(const char *p, CFStringRef str);
         apple_log_CFStr("audio dev=",routeDescription);
         
      }
   }

#endif
}

int getAOState(CFStringRef *state){
  // kAudioSessionCategory_PlayAndRecord
   UInt32 propertySize = sizeof(CFStringRef);
   OSStatus status = AudioSessionGetProperty(kAudioSessionProperty_AudioRoute, &propertySize, state);
   return status==kAudioSessionNoError?0:status;
}
//
int isPlaybackVolumeMuted(){
   
   int isPlaybackVolumeMutedIOS6();
   int getIOSVersion();
   if(getIOSVersion()>=6)return isPlaybackVolumeMutedIOS6();
   Float32 volume = 1.0;
   UInt32 dataSize = sizeof(Float32);
   
   AudioSessionGetProperty (
                            kAudioSessionProperty_CurrentHardwareOutputVolume,
                            &dataSize,
                            &volume
                            );
   
   printf("[volume %f]\n",volume);
   return volume<.1;
}

int useRetroRingtone(){
   void *findGlobalCfgKey(const char *key);
   int *pR=(int*)findGlobalCfgKey("iRetroRingtone");
   return (pR && *pR==1);
}

const char * getRingtone(const char *p=NULL);

void vibrateOnce(){
   vOnce.vibrate();
}

void* playDefaultRingTone(int iIsInBack){
   iRingIsInBackGround=iIsInBack;
   iRingRepCount=0;
   iVibrateRepCount=0;
   if(iStopRingTone==0 || mySSID)return NULL;
   


   mySSID=0;
   if(!iRingIsInBackGround){
      
      CFStringRef n =  CFStringCreateWithCString(NULL, getRingtone(), kCFStringEncodingUTF8);

      CFBundleRef mainBundle = CFBundleGetMainBundle ();
      
      // Get the URL to the sound file to play. The file in this case
      // is "tap.aif"
      
      //afconvert -f caff -d aac  -c 1 telephone-ring.wav ring.caf
      

      CFURLRef myURLRef  = CFBundleCopyResourceURL (
                                                  mainBundle,
                                                    n,
                                                  CFSTR ("caf"),
                                                  NULL
                                                  );
      CFRelease(n);
      if(!myURLRef)return NULL;
      
      AudioServicesCreateSystemSoundID (myURLRef, &mySSID);
      printf("[mySSID=%lu]",mySSID);
      CFRelease(myURLRef);
   }
   
   iStopRingTone=0;

   
   dispatch_async(dispatch_get_main_queue(), ^(void) {
#if 1
      if(iRingIsInBackGround){//or silent
         puts("backRing");
         
         setAudioRouteRest(1,-1);
   
         AudioServicesPlaySystemSound (kSystemSoundID_Vibrate);
         
         dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
            sleep(1);
            AudioServicesAddSystemSoundCompletion(kSystemSoundID_Vibrate,
                                                  NULL,NULL,
                                                  audioServicesSystemSoundCompletionProcVibrate,
                                                  //audioServicesSystemSoundCompletionProc,
                                                  NULL);
            AudioServicesPlaySystemSound (kSystemSoundID_Vibrate);
         });
      }
      else{
         puts("!backRing");
         setAudioRouteRest(1,-1);
         
         AudioServicesAddSystemSoundCompletion(kSystemSoundID_Vibrate,
                                               NULL,NULL,
                                               audioServicesSystemSoundCompletionProcVibrate,
                                               NULL);
         
         AudioServicesPlaySystemSound (kSystemSoundID_Vibrate);
         
         AudioServicesPlayAlertSound(mySSID);
         AudioServicesAddSystemSoundCompletion(mySSID,
                                               NULL,NULL,
                                               audioServicesSystemSoundCompletionProc,
                                               NULL);
      }
#endif
   });
   
   


   return (void*)1;

}

static SystemSoundID ringTestSsid=0;


void stopTestRingTone(){
   
   if(ringTestSsid){
      SystemSoundID s=ringTestSsid;ringTestSsid=0;
      AudioServicesRemoveSystemSoundCompletion(s);
      AudioServicesDisposeSystemSoundID(s);
      
   }
}

static void stopTestRingTone_ssid(SystemSoundID s){
   
   if(ringTestSsid==s && s){
      ringTestSsid=0;
      AudioServicesRemoveSystemSoundCompletion(s);
      AudioServicesDisposeSystemSoundID(s);
   }
}

void playTestRingTone(const char *name){
   //playDefaultRingTone(0,1);
   CFBundleRef mainBundle = CFBundleGetMainBundle ();
   
   // Get the URL to the sound file to play. The file in this case
   // is "tap.aif"
   
   //afconvert -f caff -d aac  -c 1 telephone-ring.wav ring.caf
   
   SystemSoundID ssid=0;
   
   CFStringRef n =  CFStringCreateWithCString(NULL, name, kCFStringEncodingUTF8);
   CFURLRef myURLRef  = CFBundleCopyResourceURL (
                                                 mainBundle,
                                                 n,
                                                 CFSTR ("caf"),
                                                 NULL
                                                 );
   if(myURLRef){
      AudioServicesCreateSystemSoundID (myURLRef, &ssid);
      CFRelease(myURLRef);
   }
   printf("[ssid=%lu]",ssid);
   
   CFRelease(n);
   
   if(!ssid)return;
   
   dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
      setAudioRouteRest(1,-1);
      
      stopTestRingTone();


      ringTestSsid=ssid;
      AudioServicesPlayAlertSound(ssid);
  
      sleep(4);
      stopTestRingTone_ssid(ssid);
   });
}


int testA()
{

#if 0
   static CTMAudioDuplex a(8000);

   a.start();
   sleep(4);
   a.stop();
   puts("ok");
   a.start();
   sleep(4);
   a.stop();
   puts("ok2");
#endif
   
#if 0
   CTMAudio ao(0,8000,kAudioUnitScope_Input);
   CTMAudio ai(1,8000,kAudioUnitScope_Global);
   int c=0;
   for(c=0;c<2;c++)
   {
      
      
      ao.start();
      ai.start();
      
      sleep(5);
      
      ao.stop();
      ai.stop();

      printf("-------------------------loop\n");
      sleep(1);
      
   };
#endif
   
   return 0;
}

