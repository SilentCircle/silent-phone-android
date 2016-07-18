#ifndef C_T_AUDIO_H
#define C_T_AUDIO_H
#include "../baseclasses/CTBase.h"
#include "CTAudioIncDecFrq.h"

/*
As it says in <CoreAudio/AudioHardware.h>, AudioDeviceAddIOProc() and AudioDeviceRemoveIOProc() were deprecated in favor of AudioDeviceCreateIOProcID() and AudioDeviceDestroyIOProcID().

 On Nov 18, 2008, at 1:48 AM, tahome izwah wrote:

I am getting a compiler warning about the deprecated AudioDeviceAddIOProc etc.
What is the proper replacement for that call? I can't seem to find any
mention of it being deprecated in the latest docs.
*/
#include <CoreAudio/AudioHardware.h>
#include <CoreAudio/CoreAudio.h>

#include "au.h"
#define FREQUENCY     400//440                 // 440hz = Musical A Note
//#define BUFFERSIZE    16000                // 4k sound buffer

#ifndef PI
#define PI            3.14159265358979
#endif

#define maxValue 32567.0f

class CoreAudioMiniRecorder
{
private:

    AudioDeviceID	deviceW;		/* the device ID */
    void *		deviceWBuffer;
    void *		localWBuffer;
    UInt32		deviceWBufferSize;	/* Buffer size of the audio device */
    static AudioBufferList*	deviceWBufferList;
    static AudioBufferList*	localWBufferList;
    static UInt32 curBuffer;

public:
    void *cbData;

    AudioStreamBasicDescription	deviceFormat;	/* format of the default device */

    CoreAudioMiniRecorder(int milliseconds);
    ~CoreAudioMiniRecorder();
    void record();
    AudioBuffer * CoreAudioMiniRecorder::getData();
    void stopRecord();
    void CoreAudioMiniRecorder::getData_s_short_stereo(signed short * retour, unsigned int samples);

    OSStatus GetAudioDevices (void **devices /*Dev IDs*/, short	*devicesAvailable /*Dev number*/);
    static OSStatus deviceReadingProc (AudioDeviceID  inDevice, const AudioTimeStamp*  inNow, const AudioBufferList*  inInputData, const AudioTimeStamp*  inInputTime, AudioBufferList*  outOutputData, const AudioTimeStamp* inOutputTime, void* inClientData);

    static void CoreAudioMiniRecorder::freeBuffer(AudioBuffer * data);

};

AudioBufferList*	CoreAudioMiniRecorder::deviceWBufferList = NULL;
AudioBufferList*	CoreAudioMiniRecorder::localWBufferList = NULL;
UInt32 CoreAudioMiniRecorder::curBuffer = 0;

void updateAudioBuf(void *p, Float32 *f, int iSamples);

OSStatus CoreAudioMiniRecorder::deviceReadingProc (AudioDeviceID  inDevice, const AudioTimeStamp*  inNow, const AudioBufferList*  inInputData, const AudioTimeStamp*  inInputTime, AudioBufferList*  outOutputData, const AudioTimeStamp* inOutputTime, void* inClientData)
{
    //memcpy(localWBufferList->mBuffers[curBuffer].mData, inInputData->mBuffers[0].mData,  inInputData->mBuffers[0].mDataByteSize);
   //puts("a");
int i;
Float32 * s=(Float32 *)inInputData->mBuffers[0].mData;
int iMax=0;
int val;
for (i=0; i<inInputData->mBuffers[0].mDataByteSize/4; i++) {
   val=(int)((*s)*32000.0f);
   
   
   
   if(iMax<val)iMax=val;
   s++;
}
   void *cb=((CoreAudioMiniRecorder*)inClientData)->cbData;
   if(cb)updateAudioBuf(cb,(Float32 *)inInputData->mBuffers[0].mData,inInputData->mBuffers[0].mDataByteSize/4);// (sizeof(Float32) * inInputData->mBuffers[0].mNumberChannels));
static int x;
x++;
 // if((x%2)==0)printf("%d %d %d %d\n",iMax, (int)inNow->mSampleTime,x,1);//time(NULL));
  //flush(stdout);
//    curBuffer++;
  //  if (curBuffer == localWBufferList->mNumberBuffers) curBuffer = 0;
    return 0;
}


void CoreAudioMiniRecorder::getData_s_short_stereo(signed short * retour, unsigned int samples)
{
    AudioBuffer * Buffer = getData();
    int i,max = Buffer->mDataByteSize / (sizeof(Float32) * Buffer->mNumberChannels);
    Float32 * data = (Float32*)Buffer->mData;

#define maxValue 32567.0f

    if (Buffer->mNumberChannels==1)
    {
        for (i = 0; i<max; i++)
        {
            retour[(i*samples)/max]=(short)(data[i]*maxValue);
            retour[samples+(i*samples)/max]=(short)(data[i]*maxValue);
        }
    }
    if (Buffer->mNumberChannels==2)
    {
        for (i = 0; i<max; i++)
        {
            retour[(i*samples)/max]=(short)(data[i*2]*maxValue);
            retour[samples+(i*samples)/max]=(short)(data[i*2+1]*maxValue);
        }
    }

    freeBuffer(Buffer);
}


AudioBuffer * CoreAudioMiniRecorder::getData()
{
    AudioBuffer * final = (AudioBuffer *) malloc(sizeof(AudioBuffer));
    UInt32 i, j, size, lSize;
    char * dst;

    lSize = localWBufferList->mBuffers[curBuffer].mDataByteSize;
    size = localWBufferList->mNumberBuffers * lSize;

    final->mNumberChannels = localWBufferList->mBuffers[curBuffer].mNumberChannels;
    final->mDataByteSize = size;
    final->mData = malloc (size);

    dst = (char*)final->mData;

    j = curBuffer;
    for (i = 0; i < localWBufferList->mNumberBuffers; i++)
    {
        memcpy(dst,localWBufferList->mBuffers[j].mData, lSize);
        dst += lSize;
        j++;
        if (j == localWBufferList->mNumberBuffers) j = 0;
    }

    return final;
}

void CoreAudioMiniRecorder::freeBuffer(AudioBuffer * data)
{
    if (data == NULL) return;
    if (data->mData != NULL) free(data->mData);
    free(data);
}

CoreAudioMiniRecorder::CoreAudioMiniRecorder(int milliseconds)
{
    cbData=NULL;
    OSStatus	error = 0;
    UInt32	count;
    void *	devices = 0;
    short	devicesAvailable;
    Boolean	outWritable;
//puts("a");
    /* Look for audio devices */
    error = GetAudioDevices (&devices ,&devicesAvailable);
   // puts("z");
    if (error != 0) goto Crash;
    deviceW = ((AudioDeviceID*)(devices))[0]; /* Selecting first device */

    /* Getting buffer size */
    error = AudioDeviceGetPropertyInfo(deviceW, 0, 0, kAudioDevicePropertyBufferSize, &count, &outWritable);
    if (error != 0) goto Crash;
   // printf("aa %u %u\n",count,outWritable);
    error = AudioDeviceGetProperty(deviceW, 0, 0, kAudioDevicePropertyBufferSize, &count, &deviceWBufferSize);
    if (error != 0) goto Crash;
//printf("a %u %u\n",count,deviceWBufferSize);
    /* getting streams configs */
    error = AudioDeviceGetPropertyInfo(deviceW, 0, 0, kAudioDevicePropertyStreamConfiguration,  &count, &outWritable);
    if (error != 0) goto Crash;
  //  printf("c=%u\n",count);
    {
        deviceWBufferList = (AudioBufferList*) malloc(count);

        error = AudioDeviceGetProperty(deviceW, 0, 0, kAudioDevicePropertyStreamConfiguration, &count, deviceWBufferList);
        if (error != 0) goto Crash;
        
#if 1//def DEBUG_MAXIMUS
        {
            unsigned int i;
            //deviceWBufferList->mNumberBuffers=4;
    //        printf("IOProperties : Buffer number = %u\n",deviceWBufferList->mNumberBuffers);
            /*device->outStreamsInfo  = malloc(sizeof(StreamInfo) * device->totalOutputStreams);*/
            for (i = 0; i < deviceWBufferList->mNumberBuffers; i++)
            {
                printf("  Buffer %d Properties : DataByteSize = %u\n",i,deviceWBufferList->mBuffers[i].mDataByteSize);
                printf("  Buffer %d Properties : NumberChannels = %u\n",i,deviceWBufferList->mBuffers[i].mNumberChannels);
                printf("  Buffer %d Properties : Data = %u\n",i,deviceWBufferList->mBuffers[i].mData);
                /*error = GetPhysicalFormatCount(device, i + 1, &device->outStreamsInfo[i].pFormatCount, false);
                device->outStreamsInfo[i].pFormatMenuSelection = 1;

                error = GetActualFormatCount(device, i + 1, &device->outStreamsInfo[i].aFormatCount, false);
                device->outStreamsInfo[i].aFormatMenuSelection = 1;*/
            }
        }
#endif
    }
   // puts("a6");
   // sleep(2);
#if 0
    error = AudioDeviceGetPropertyInfo(deviceW, 0, 0, kAudioDevicePropertyStreamFormat,  &count, &outWritable);
    if (error != 0) goto Crash;
    puts("a65");
    error = AudioDeviceGetProperty(deviceW, 0, 0, kAudioDevicePropertyStreamFormat, &count, &deviceFormat);
    if (error != 0) goto Crash;

#ifdef DEBUG_MAXIMUS
    printf("IOProperties : SampleRate = %f\n",deviceFormat.mSampleRate);
    printf("IOProperties : FormatFlags = %d\n",(int)deviceFormat.mFormatFlags);
    printf("IOProperties : BytesPerPacket = %d\n",(int)deviceFormat.mBytesPerPacket);
    printf("IOProperties : FramesPerPacket = %d\n",(int)deviceFormat.mFramesPerPacket);
    printf("IOProperties : BytesPerFrame = %d\n",(int)deviceFormat.mBytesPerFrame);
    printf("IOProperties : ChannelsPerFrame = %d\n",(int)deviceFormat.mChannelsPerFrame);
    printf("IOProperties : BitsPerChannel = %d\n",(int)deviceFormat.mBitsPerChannel);
#endif

/*
    error = AudioDeviceGetPropertyInfo(deviceW, 0, 0, kAudioDevicePropertyStreamFormat,  &count, &outWritable);
    if (error != 0) goto Crash;
    error = AudioDeviceGetProperty(deviceW, 0, 0, kAudioDevicePropertyStreamFormat, &count, &deviceFormat);
    if (error != 0) goto Crash;
*/
#endif
//puts("a7");
    //error = AudioDeviceAddIOProc(deviceW, this->deviceReadingProc, this);	/* Creates the callback proc */
    //if (error != 0) goto Crash;

if(0)
    {
        int n,i;

        n = 1 + deviceFormat.mBytesPerFrame*milliseconds*((int)(deviceFormat.mSampleRate))/(1000*deviceWBufferList->mBuffers[0].mDataByteSize);
        localWBufferList = (AudioBufferList*) malloc(sizeof(UInt32)+n*sizeof(AudioBuffer));
        localWBufferList->mNumberBuffers = n;
        for (i=0; i<n; i++)
        {
            localWBufferList->mBuffers[i].mNumberChannels = deviceFormat.mChannelsPerFrame;
            localWBufferList->mBuffers[i].mDataByteSize = deviceWBufferList->mBuffers[0].mDataByteSize;
            localWBufferList->mBuffers[i].mData = malloc(deviceWBufferList->mBuffers[0].mDataByteSize);
        }

    }
  //  puts("rec init ok");
    return;

Crash :
    printf("An error occured (%c%c%c%c)\n",((char *)&error)[0],((char *)&error)[1],((char *)&error)[2],((char *)&error)[3]);
    deviceW = NULL;
    return;
}



CoreAudioMiniRecorder::~CoreAudioMiniRecorder()
{
}

void CoreAudioMiniRecorder::record()
{
    // duration of an audio buffer = milliseconds/n

    OSStatus o= AudioDeviceAddIOProc(deviceW, this->deviceReadingProc, this);	/* Creates the callback proc */
//    if (error != 0) goto Crash;

if(!o)
    //AudioTimeStamp outTime;
    o=AudioDeviceStart(deviceW, this->deviceReadingProc);
    printf("rec e=%d\n",o);
    //usleep(milliseconds*1000);
    //AudioDeviceGetCurrentTime(deviceW, &outTime);
    //AudioDeviceRead(deviceW, &outTime, deviceWBufferList);
    //AudioDeviceStop(deviceW, this->deviceReadingProc);
}

void CoreAudioMiniRecorder::stopRecord()
{
    OSStatus o=AudioDeviceStop(deviceW, this->deviceReadingProc);;
    AudioDeviceRemoveIOProc(deviceW, this->deviceReadingProc);
    printf("stop rec  e=%d\n",o);
}

OSStatus CoreAudioMiniRecorder::GetAudioDevices (void **devices /*Dev IDs*/, short	*devicesAvailable /*Dev number*/)
{
#ifdef DEBUG_MAXIMUS
    int i;
    char	cStr[256];
#endif
    OSStatus	err = NULL;
    UInt32 	outSize;
    Boolean	outWritable;

    // find out how many audio devices there are, if any
    err = AudioHardwareGetPropertyInfo(kAudioHardwarePropertyDevices, &outSize, &outWritable);
    if (err != NULL) return (err);

    // calculate the number of device available
    *devicesAvailable = outSize / sizeof(AudioDeviceID);
    // Bail if there aren't any devices
    if (*devicesAvailable < 1) return (-1);

    // make space for the devices we are about to get
    if (*devices != NULL) free(*devices);
    *devices = malloc(outSize);
    // get an array of AudioDeviceIDs
    err = AudioHardwareGetProperty(kAudioHardwarePropertyDevices, &outSize, (void *) *devices);
    if (err != NULL) free(*devices);
#ifdef DEBUG_MAXIMUS
    printf("Found %d Audio Device(s)\n",*devicesAvailable);

    for (i=0; i<*devicesAvailable;i++)
    {
        UInt32 ID = ((UInt32*)(*devices))[i];
        err =  AudioDeviceGetPropertyInfo(ID, 0, 0, kAudioDevicePropertyDeviceName,  &outSize, &outWritable);
        err = AudioDeviceGetProperty(ID, 0, 0, kAudioDevicePropertyDeviceName, &outSize, cStr);
        printf("Device #%d : %s",ID,cStr);
        err = AudioDeviceGetProperty(ID, 0, 0, kAudioDevicePropertyDeviceManufacturer, &outSize, cStr);
        printf(" (%s)\n",cStr);

    }
#endif

    return (err);
}



class CTAudioInXA: public CTAudioInBase{
   int iBlocksInDrv;
   CTAudioCallBack *cb;
   CoreAudioMiniRecorder rec;
   CTIncDecFreq incDecFreq; 
public:
   int iAudioDevToUse;
   CTAudioInXA(CTAudioCallBack *cb, int iBitsPerSample=16, int iRate=8000, int iCh=1, int iAudioDevToUse=0)
      :cb(cb),rec(1000)
   {
     iNeedStop=1;
   }

   virtual int getRate(){return 8000;}
   virtual int getType(){return CTAudioInBase::ePCM16;}
   int init(void *p=NULL)
   {
      return 0;
   }

   int constr(int iBitsPerSample=16, int iRate=8000, int iCh=1)
   {
       return 0;
   }
public:
   ~CTAudioInXA()
   {
      ///destr();
     destr();// stop();
   }
private:
   void destr()
   {
   }
public:
   inline int recording()
   {
      return !iNeedStop;
   }
   int record(void *pSrc)
   {
     if(iNeedStop==0)return 0;
     iNeedStop=0;
     rec.cbData=this;
     incDecFreq.setFreq(44100*2,8000);
     rec.record();
     return 0;
   }
private:
   inline int wait(int iMs=1000){
     return 0;
   }
public:
   void stop()
   {
     if(iNeedStop==0)
       rec.stopRecord();   
     iNeedStop=1;
   }
   int uiFPos;
   short sbufF[10000];
   short sbufR[10000];   
   void updateFloat(Float32 *f, int iSamples)
   {
   
      short *s=&sbufF[0];
      int i=iSamples;
      while(i)
      {
        *s=(short)((*f)*32500.0f);
       // *(s+1)=*s;
        s++;
        f++;
        i--;
      }
      iSamples=incDecFreq.doJob(&sbufF[0],&sbufR[0],iSamples);
      cb->audioIn((char*)&sbufR[0],iSamples*2,uiFPos);
      uiFPos+=(unsigned int)iSamples*2;
     // puts("a");
      
      
    //  update((char*)&sbufF[0],iSamples*2,uiFPos);
      //delete s;
      
   }   

private:
int iNeedStop;
};

void updateAudioBuf(void *p, Float32 *f, int iSamples)
{
   ((CTAudioInXA*)p)->updateFloat(f,iSamples);
}

#define CTAudioIn CTAudioInXA
#include "CTFifo.h"
#undef CTAudioIn
#define CTAudioInX CTPhoneAuidoIn


class CTAudioInX1: public CTAudioInBase{
   CTAudioInX *a;
   CTAudioInX *aprev;
   CTAudioCallBack *cb;
   int iBitsPerSample;
   int iRate, iCh, iAudioDevToUse;
public:
    CTAudioInX1(CTAudioCallBack *cb, int iBitsPerSample=16, int iRate=8000, int iCh=1, int iAudioDevToUse=0)
       :cb(cb),iBitsPerSample(iBitsPerSample),iRate(iRate),iCh(iCh),iAudioDevToUse(iAudioDevToUse)
    {
       aprev=a=NULL;
    }
    ~CTAudioInX1(){
       stop();
       if(aprev)
       {
          delete aprev;
          aprev=NULL;
       }    
    }
    int getRate(){return iRate;}
    inline int recording(){return a?a->recording():0;}
    int getType(){return ePCM16;}
    int record(void *pSrc){
       if(aprev)
       {
          delete aprev;
          aprev=NULL;
       }
      CTAudioInX *b=a;
      if(b==NULL)
         b=new  CTAudioInX(cb,iBitsPerSample,iRate,iCh);//,iAudioDevToUse);
      a=b;
      Sleep(100);
      return a->record(pSrc);   
    }
    void init(void *p=NULL){}
    void stop(void *pSrc){
      CTAudioInX *b=a;
      a=NULL;
      if(b)b->stop(pSrc);
      delete b;
      //aprev=b;
      
    }


};
#define CTAudioIn CTAudioInX1
   #include<sys/times.h>

static unsigned int getTickCountOs()
{
   struct timeval v;
   if(gettimeofday(&v,0)==0)
   return v.tv_sec*1000+
   v.tv_usec/1000;//*1000; 
   return 0;
}

class CTJit{
public:
   unsigned int uiPrevPackTs;
   unsigned int uiPrevPos;
   int iJit;
   double dJit;
   int iMaxJit;
   int iBytesToSetBuf;
   
   int iPrevWasSetPos;

   void resetJit()
   {
      uiPrevPos=uiPrevPackTs=0;
      iMaxJit=iJit=20;
      dJit=0.0;
      iPrevWasSetPos=0;
   }
   unsigned int GetTickCount()
   {
  // struct tms t;
      return getTickCountOs();//times(&t);

//      return clock();
   }
   void checkJit(char *p, int iLen, unsigned int uiPos, int iCanSetPos, int iRateX, int &iMaxBuf, int &iMinBuf, int &iResetPosNow)
   {
      
      if(iLen>iRateX || !iCanSetPos || !uiPos)return ;
      uiPos/=2;
      unsigned int uiCurTs=GetTickCount();
      
      if(uiPrevPackTs && p)
      {
         
         int iDelta=((int)uiPos-(int)uiPrevPos)*1000/iRateX;
         int iDeltaTime=(int)(uiCurTs-uiPrevPackTs);
         if(iDeltaTime>=2000 || iPrevWasSetPos)
         {
            iResetPosNow=1;
            puts("reset audio now");
         }

         if(iDelta<500 && iDeltaTime<2000 && iDeltaTime>=0 && iDelta>0)
         {

          //s->jitter += d - ((s->jitter + 8) >> 4);
          //s->jitter += (1./16.) * ((double)d - s->jitter);
        // iJit+=abs(iDeltaTime-iDelta)-((iJit+8)>>4);

         //iJit+=(((abs(iDeltaTime-iDelta)<<4)-((iJit)))>>4);
         int ix=abs(iDeltaTime-iDelta);
         if(ix>iJit)
         {
            iJit=(ix+iJit)>>1;
         }
         else
            iJit-=abs(ix-iJit)>>5;
         

         //iJit+=(int)((1./16.) *((double)abs(iDeltaTime-iDelta)-(double)iJit));
         dJit+=((1./16.) *((double)abs(iDeltaTime-iDelta)-dJit));
        

         int res=iJit*2;//>>4;///4;

         if(res>iMaxJit)
            iMaxJit=res;
         else
            iMaxJit-=(iMaxJit+res)/32;
         if(iMaxJit>1500)
            iMaxJit=1500;

         res=iMaxJit;

         //res+=iDelta;
         //iMaxBufferSize=((int)res+80)*iRate/500;//iDelta*2;
         iMaxBuf=iRateX*5;//((int)res+80)*iRate/500;//iDelta*2;

         iBytesToSetBuf=((int)res+20)*iRateX/500+iRateX/20;;
         if(iBytesToSetBuf&1)iBytesToSetBuf++;
        // iMaxBufferSize=((int)res)*iRate/500+iDelta*2;
         iMinBuf=iRateX/40;//iMaxBufferSize/8;//0;//iRate/10;//iJit*iRate/1000;//*2;
         //if(iMaxBufferSize>iRate*3/2)iMaxBufferSize=iRate*3/2;

//         char buf[128];sprintf(buf," %5u %5u jd=%4u ji=%4u m=%4u",iDeltaTime,iDelta,(int)dJit, res ,iMaxJit);deb((char*)buf);
#if 1//_CONSOLE
         printf("ts %10u, %5u--%5u %5u  jit=%4u  jit=%4u  \n",uiCurTs,iDeltaTime,iDelta, res, (int)dJit, iJit);
#endif
         }
         
      }
      uiPrevPos=uiPos;
      uiPrevPackTs=uiCurTs;
      iPrevWasSetPos=(p==NULL && iLen==0);

   }

};

class CTAudioOut: public CTAudioOutBase,public CTJit{
   int iMSecPlay,iNeedStop;
   int iMachineRate;
   AUDIO_OUT *ao;
   int iInitOk;
   int iRate;
   int iBufSize;
   unsigned int uiPlayPos;
   short *sbuf, *bufTmp;  
   CTIncDecFreq incDecFreq; 
public:
   void setVolume(int i){}
   CTAudioOut(int iBitsPerSample=16, int iRate=8000, int iCh=1, int id=0,int flag=0)
   :iRate(iRate),iMachineRate(44100)
   {
   
      iMSecPlay=0;
      iNeedStop=1;
      
      iInitOk=0;ao=NULL;
      init(NULL);
      iBufSize=4*iMachineRate*2*3;
      uiFPos=0;
      uiPlayPos=(unsigned int)(iBufSize-iMachineRate*2);
      sbuf=new short[iBufSize];
      bufTmp=new short[iBufSize];
      //iBufSize*=2;      
   }
   static int cbAudio(void *callback_data, float *samples, int frames) 
   {
    //  printf("get Next ");
      int ret=((CTAudioOut *)callback_data)->getNextBuf(samples,frames*2);
      //((CTAudioOutM *)callback_data)->getNextBuf(samples+1,frames);
      if(ret<0)ret=0;
      return frames;
   }
   int getNextBuf(float *samples, int frames) 
   {
     if(iMSecPlay!=-1)
     {
       iMSecPlay-=frames;
       if(iMSecPlay<0){iNeedStop=1;return -1;}
       
     }
     int iBufPos=(int)uiPlayPos;

//-----------
/*
      incDecFreq.setFreq(iRate,iMachineRate*2);//stereo
      
      
         iLen =incDecFreq.doJob((short*)p,&bufTmp[0],iLen/2);
         p=(char*)&bufTmp[0];
         iLen*=2;
         //pos to convert
         //converted bytes;
         

*/
//------------     
     short *p=sbuf+iBufPos/2;
     short *pEnd=sbuf+iBufSize/2;
     uiPlayPos+=(unsigned int)frames*2;
     uiPlayPos%=(unsigned int)iBufSize;
     int iDraw=0;
     int of=frames;
     while(frames>0)
     {
        frames-=1;
        float pin=(float)*p;
        float res=pin/32768.0f;
        *samples=res;//*(samples+1)=res;
        if(p<pEnd)p++;else {p=sbuf;iDraw=1;}
        samples+=1;


     }
     //static int ui
     static int x;x++;
     //if((x%4)==0)printf("(play pos %d,%d wpos %d  %d)\n", uiPlayPos/44/4,of/2,uiWPosLast/44/4,iDraw);
     
     
     return 0;
   }   
  // unsigned int uiWPosLast;
   void rel()
   {
      if(ao)audio_close(ao);
      ao=NULL;
      iInitOk=0;
   }
   int init(void *p)
   {
      if(iRate==16000)return 0;
      if(iInitOk)return 0;
      ao=audio_open(2,iMachineRate);
      iInitOk=ao?1:0;
      return 0;
      
   }
   int uiFPos;
   short sbufF[10000];
   void updateFloat(Float32 *f, int iSamples)
   {
   
      uiFPos+=(unsigned int)iSamples*2;
      short *s=&sbufF[0];
      int i=iSamples;
      while(i)
      {
        *s=(short)((*f)*32500.0f);
       // *(s+1)=*s;
        s++;
        f++;
        i--;
      }
      
      update((char*)&sbufF[0],iSamples*2,uiFPos);
      //delete s;
      
   }
   void constr()
   {
   }
   ~CTAudioOut()
   {
      rel();if(sbuf)delete sbuf; if(bufTmp)delete bufTmp;
   }
 //  unsigned int uiWPosLast;

   inline int getRate(){return 8000;}
   inline int getDirectRate(){return iMachineRate*2;}

  // CTMutex mu;
   void stopAfter(int iMs=-1)
   {
      if(iMs!=-1)
      {
         iMSecPlay=iMs*getDirectRate()/500;
      }
      else
      {
         iMSecPlay=-1;
      }
   }
   int iSetPosNow;
   int play()
   {
     iIncDecFreq=0;
   //   if(iRate==16000)return 0;
//     int i;
     iMSecPlay=-1;

     if(iNeedStop)
     {
        iNeedStop=0;
        resetJit();
         init(NULL);
       //puts("play2");
         audio_play(&cbAudio,ao,this);        
     }
      return 0;
   }
   inline int playing(){return iNeedStop==0;}
   void stop()
   {
      if(iMSecPlay>0)return;//TODO test
      if(iNeedStop==0)
      {
         iNeedStop=1;
         rel();
      }
   }
   int waitBuffers()
   {
      return 0;
   }
//#include <math.h>
   inline int update(char *p, int iLen, unsigned int uiPos)
   {
//   printf("data\n");
      return update(p, iLen,  uiPos, 1);
   }
    //  checkJit(p,iLen,uiPos,iCanSetPos,iRate,iMaxBufferSize,iMinBufferSize,iSetPosNow);
   unsigned int uiWPosLast;
   int bufBytes()
   {
      if(uiWPosLast>uiPlayPos)return uiWPosLast-uiPlayPos;
      return uiWPosLast
      +(unsigned int)iBufSize-uiPlayPos;
     // return 0;
   }
   
   int update(char *p, int iLen, unsigned int uiPos, int iCanResetPos)
   {
     
     //iMachineRate=4000;
     //iRate,iMachineRate*2
     int iOutRate=iRate+iIncDecFreq;
      incDecFreq.setFreq(iOutRate,iMachineRate*2);//stereo
      
      /*
         if(uiPos<10000)//(unsigned int)iMachineRate*2)
         {
         uiPos*=(unsigned int)iMachineRate*2;
         uiPos/=(unsigned int)iRate;
         }
         else
         {
         uiPos/=(unsigned int)iRate;
         uiPos*=(unsigned int)iMachineRate*2;
         }
      */
      
      double d=(double)iMachineRate*2/(double)iOutRate;
      //d=(double)uiPos*d;
     // d+=0.5f;
      d*=100.f;
      
      if(uiPos<1000)
         uiPos=(int)d*uiPos/100;
      else
         uiPos=(int)d/100*uiPos;
         
         
      
      if(iLen && p)
      {
        // int iLen0=iLen;
         iLen =incDecFreq.doJob((short*)p,&bufTmp[0],iLen/2);
         p=(char*)&bufTmp[0];
         iLen*=2;
         if(uiPrevFreqPos+iLen>uiPos-2 && uiPrevFreqPos+iLen<uiPos+2)
         {
           uiPos=uiPrevFreqPos+iLen;
         }
         uiPrevFreqPos=uiPos;
      }
      return updateFreqOk(p,iLen,uiPos,iCanResetPos);
   }
   unsigned int uiPrevFreqPos;
     int iMaxBufferSize;//=iMachineRate*4;
     int iMinBufferSize;//=iMachineRate/10;
   //  int iSetPosNow;
   int updateFreqOk(char *p, int iLen, unsigned int uiPos, int iCanResetPos)
   {
   //puts("a");
     //uiPos=uiWPosLast+(unsigned int )iLen;
     //int iSetPosNow=0;
     if(iCanResetPos)checkJit(p,iLen,uiPos,iCanResetPos,iMachineRate*2,iMaxBufferSize,iMinBufferSize,iSetPosNow);
     uiPos%=(unsigned int)iBufSize;
     short *s=(short *)p;
    // printf("%p,%d,%u\n",p,iLen,uiPos);
    // puts("b");
     if(iLen==0)iLen=iBufSize;
  //   printf("update %d %d\n", iLen, uiPos);
     //iLen/=2;
     short *pWrite=sbuf+uiPos/2;
     short *pEnd=sbuf+iBufSize/2;
     int i=iLen/2;
     while(i>0)
     {
        i--;
        *pWrite=p?*s:0;
        if(pWrite<pEnd)pWrite++;else pWrite=sbuf;
        s++;
     }
     if(p && iLen)
       uiWPosLast=uiPos;
     static int x;x++;
     int ibuf=bufBytes();
     
       // printf("buf %d, %d %d (bs=%d,p=%u %p)\n",ibuf, iMinBufferSize, iMaxBufferSize,iBufSize,uiPos,p);
     if(iCanResetPos && (iSetPosNow || (p && iLen && ( iMinBufferSize>ibuf || ibuf>iMaxBufferSize))) || (p==NULL && iLen==0 && uiPos))
     {
        iSetPosNow=0;
        //uiPlayPos-=(unsigned int)iBytesToSetBuf;
        printf("buf %d,set %d %d (bs=%d,pp=%u,p=%u %p %d %d s=%d)\n",ibuf, iMinBufferSize, iMaxBufferSize,iBufSize,uiPlayPos,uiPos,p, iLen, uiPos,iBytesToSetBuf);
        
        if(uiPos>=(unsigned int)iBytesToSetBuf)
           uiPlayPos=uiPos-(unsigned int)iBytesToSetBuf;
        else
           uiPlayPos=uiPos+(unsigned int)iBufSize-(unsigned int)iBytesToSetBuf;
           
     }
     if(0)//p && uiPos && iLen)
     {
     printf("buf %7d -> %7d ", ibuf, iBytesToSetBuf);
     iIncDecFreq=(ibuf-iBytesToSetBuf)/4;
     if(iIncDecFreq<-300)iIncDecFreq=-300;
     else if(iIncDecFreq>300)iIncDecFreq=300; 
     /*
     if(ibuf<iBytesToSetBuf){
       //eat silence
       iIncDecFreq-=40;
       if(iIncDecFreq<-100)iIncDecFreq=-100;
     }else if(ibuf>iBytesToSetBuf)
     {
       iIncDecFreq+=(ibuf-iBytesToSetBuf)/4;
       //insert silece
       //if(iIncDecFreq>200)iIncDecFreq=200;
     }
     */
     }

    // printf("(w pos %d) \n", uiPos/44/4);
     
     return 0;
   }
   int iIncDecFreq;
   inline int getBufSize(){return iBufSize;}
   //MMTIME mmt;
   inline int getPlayPos()
   {
      return uiPlayPos;
   }
   inline char *getBuf(){return (char *)sbuf;}
   inline void setPlayPos(int iPos)
   {
    uiPlayPos=(unsigned int)iPos;
   }
   inline short *getLastPlayBuf()
   {
      return sbuf;
   }
protected:
  // int iBufSize;
  /*
   int iCurentPlayPos;
   inline int calcBuffered(unsigned int uiPos)
   {

      int ret;
      if(uiPos>(unsigned int)iBufSize)
         uiPos%=(unsigned int)iBufSize;

     // PatBlt(hdc,10,35,220,10,WHITENESS);
      if(uiPos>=(unsigned int)iCurentPlayPos)
         ret=(int)uiPos-iCurentPlayPos;
      else
      {
         ret=(int)uiPos+iBufSize-iCurentPlayPos;
      }


      return ret; 
   }
//   FIR_HP13 filter;
   //(*p2)=(short)ir1.highpass((short)*p2);
   inline void memCpyF(short *s1, short *s2, int iSamples)
   {
      if(iSamples>100000)return;
      while(iSamples>=0)
      {
//         *s1=*s2;//(short)filter.highpass(*s2);
         *s1=*s2;//(short)filter.highpass(*s2);
         s1++;
         s2++;
         iSamples--;
      }
   }

*/
};
//#endif //os

#include "../utils/utils.h"
static void beepAudio(CTAudioOut  & ao, int iFreq, char *szRawFn)
{

      unsigned int uiBufSize=ao.getBufSize();
      int iLen;

      if(szRawFn)
      {

         char *pd=loadFile(szRawFn,iLen);
         if(pd && iLen>0)
         {
         
            //memcpy(p,pd,min((int)uiBufSize,iLen));
            
            
            if(iLen<(int)uiBufSize)
            {
               //memset(p+iLen,0,(int)uiBufSize-iLen);
               ao.update(NULL,0,0,0);
            }
            ao.update(pd,iLen,0,0);
            delete pd;
            ao.setPlayPos(0);
            ao.setVolume(100);
            ao.play();

            ao.stopAfter(iLen*500/ao.getRate()+200);
       
         }
         else szRawFn=NULL;

         
      }

      if(szRawFn==NULL)
      {
         ///memset(p,0,uiBufSize/6);
         uiBufSize=ao.getDirectRate()*2;
         char *p=ao.getBuf();//new char[uiBufSize];
         memset(p,0,uiBufSize);
         genereteTone((float)iFreq,(float)iFreq+50.0f,ao.getDirectRate(), 120, uiBufSize/60,(uiBufSize/6),p,(uiBufSize/6));
//         genereteTone((float)iFreq,(float)iFreq+50.0f,ao.getRate(), 127, uiBufSize/60,(uiBufSize/6),p,(uiBufSize/2));
         //ao.update(NULL,0,0,0);//clear
         //ao.update(p,uiBufSize,0,0);
         //delete p;
         ao.setPlayPos(0);
         ao.setVolume(100);
         ao.play();
         ao.stopAfter(400);
      }

}

#endif //C_T_AUDIO