//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#include <string.h>
#import <UIKit/UIKit.h>
#include <CFNetwork/CFNetwork.h>
#include "../baseclasses/CTBase.h"
#import "STKeychain.h"

#define SKIP_WS

class CTIOS_VOIP_BG{
public:
   CFReadStreamRef readStream;
#ifndef SKIP_WS
   CFWriteStreamRef writeStream;
#endif
   CFStreamClientContext myContext;// = {0, NULL, NULL, NULL, NULL};
   
   CTIOS_VOIP_BG(int s){
      memset(&myContext,0,sizeof(CFStreamClientContext));
   
      CTIOS_VOIP_BG *p=this;
      readStream=NULL;
#ifdef SKIP_WS
      CFStreamCreatePairWithSocket(kCFAllocatorDefault,s,&p->readStream,NULL);

      CFReadStreamSetProperty(p->readStream, kCFStreamNetworkServiceType,kCFStreamNetworkServiceTypeVoIP);
      
      
      CFReadStreamOpen(p->readStream);	
#else
      CFStreamCreatePairWithSocket(kCFAllocatorDefault,s,&p->readStream,&p->writeStream);
      
      CFReadStreamSetProperty(p->readStream, kCFStreamNetworkServiceType,kCFStreamNetworkServiceTypeVoIP);
      
      CFWriteStreamSetProperty(p->writeStream, kCFStreamNetworkServiceType,kCFStreamNetworkServiceTypeVoIP);
      
      CFReadStreamOpen(p->readStream);	
      CFWriteStreamOpen(p->writeStream);
      
#endif
   }
   ~CTIOS_VOIP_BG(){
      if(readStream){
      CFReadStreamClose(readStream);
      CFRelease(readStream);
         readStream=NULL;
      }
#ifndef SKIP_WS
      CFWriteStreamClose(writeStream);
      CFRelease(writeStream);
#endif
   }
};

void *prepareTcpSocketForBg(int s){
   //return NULL;
   CTIOS_VOIP_BG *p=new CTIOS_VOIP_BG(s);
   return p;
}

void relTcpBGSock(void *ptr){
   //return;
   if(!ptr)return;
   
   CTIOS_VOIP_BG *p=(CTIOS_VOIP_BG *)ptr;
   delete p;
   
}


NSString *toNSFromTBN(CTStrBase *b, int N){
   if(!b || !b->getLen())return @"";
   NSString *r=[NSString stringWithCharacters:(const unichar*)b->getText() length:min(N,b->getLen())];  
   return r;
}

NSString *toNSFromTB(CTStrBase *b){
   if(!b || !b->getLen())return @"";

   NSString *r=[NSString stringWithCharacters:(const unichar*)b->getText() length:b->getLen()];   
   return r;
}

char * t_CFStringCopyUTF8String(CFStringRef str,  char *buffer, int iMaxLen) {
   if (str == NULL || !buffer || iMaxLen<1) {
      return NULL;
   }
   buffer[0]=0;
   iMaxLen--;
   
   // CFIndex length = CFStringGetLength(aString);
   // CFIndex maxSize  = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingUTF8);
   
   if (CFStringGetCString(str, buffer, iMaxLen, kCFStringEncodingUTF8)) {
      return buffer;
   }
   return NULL;
}

const char *t_getVersion(){
   NSString* nsB = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleShortVersionString"];
   if(!nsB)return "error";
   return nsB.UTF8String;
}



int showSSLErrorMsg(void *ret, const char *p){
   NSLog(@"tls err --exiting %s",p);
   exit(1);
   return 0;
}

NSString *findFilePathNS(const char *fn){
   char bufFN[256];
   const char *pExt="";
   int l=strlen(fn);
   strncpy(bufFN,fn,255);
   bufFN[255]=0;
   if(l>255)l=255;
   for(int i=l-1;i>=0;i--){
      bufFN[i]=fn[i];
      if(fn[i]=='.'){
         bufFN[i]=0;
         pExt=&bufFN[i+1];
         
      }
      
   }
   printf("[f=%s ext=%s]\n",&bufFN[0],pExt);
   // return "";
   NSString *ns= [[NSBundle mainBundle] pathForResource: [NSString stringWithUTF8String:&bufFN[0]] ofType: [NSString stringWithUTF8String:pExt]];
   return ns;
   
}
const char *findFilePath(const char *fn){
   NSString *ns=findFilePathNS(fn);
   if(!ns)return NULL;
   return [ns UTF8String];
   
}

char *iosLoadFile(const char *fn, int &iLen )
{
   NSString *ns=findFilePathNS(fn);
   iLen=0;
   if(!ns)return 0;
   NSData *data = [NSData dataWithContentsOfFile:ns];//autorelease];
   if(!data)return NULL;
   
   char *p=new char[data.length+1];
   if(p){
      iLen=data.length;
      memcpy(p,data.bytes,iLen);
      p[iLen]=0;
   }
   //--printf("[ptr=%p,%p]",data,p);
   //[data release];//?? crash
   return p;
}

int isBackgroundReadable(const char *fn);
void log_file_protection_prop(const char *fn);
void setFileAttributes(const char *fn, int iProtect);
char *loadFile(const  char *fn, int &iLen);
void saveFile(const char *fn,void *p, int iLen);
void bin2Hex(unsigned char *Bin, char * Hex ,int iBinLen);
int hex2BinL(unsigned char *Bin, char *Hex, int iLen);



//http://www.ios-developer.net/iphone-ipad-programmer/development/file-saving-and-loading/using-the-document-directory-to-store-files
void setFileStorePath(const char *p);
char *getFileStorePath();

void setFSPath(char *p){
   
   // char *b=getFileStorePath();
   NSString *path;
	NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
	path = [[paths objectAtIndex:0] stringByAppendingPathComponent:@"tivi"];
   NSError *error;
	if (![[NSFileManager defaultManager] fileExistsAtPath:path])	//Does directory already exist?
	{
      NSString * const pr = NSFileProtectionCompleteUntilFirstUserAuthentication;// NSFileProtectionNone;
      NSDictionary *d=[NSDictionary dictionaryWithObject:pr
                                                  forKey:NSFileProtectionKey];
      
		if (![[NSFileManager defaultManager] createDirectoryAtPath:path
                                     withIntermediateDirectories:NO
                                                      attributes:d
                                                           error:&error])
		{
			NSLog(@"Create directory error: %@", error);
		}
	}
   else{
      const char *str=[path UTF8String];
      
      if(!isBackgroundReadable(str)){
         setFileAttributes(str,0);
      }
   }
   
   setFileStorePath([path UTF8String]);
   
}


void *initARPool(){
#if !__has_feature(objc_arc)
   return [[NSAutoreleasePool alloc] init];
#else
   return (void*)1;//[[NSAutoreleasePool alloc] init];
#endif
}
void relARPool(void *p){
#if !__has_feature(objc_arc)
   NSAutoreleasePool *pool=(NSAutoreleasePool*)p;
   [pool release];
#else
   
#endif
}



const char *t_getDev_name(){
   NSString *n = [[UIDevice currentDevice]model];
   return n.UTF8String;
}

const char *t_getDev_keychainId(const char *s){
    NSString *dId = [STKeychain getEncodedDeviceIdForUsername:[NSString stringWithUTF8String:s]];
    return dId.UTF8String;
}


int isBackgroundReadable(const char *fn){
   NSString *p = [[[NSFileManager defaultManager] attributesOfItemAtPath: [NSString stringWithUTF8String: fn ]
                                                                   error:NULL] valueForKey:NSFileProtectionKey];
   return [p isEqualToString:NSFileProtectionNone];
}

void log_file_protection_prop(const char *fn){
   /*
    NSFileProtectionKey
    NSFileProtectionNone
    
    */
   
   NSString *p = [[[NSFileManager defaultManager] attributesOfItemAtPath: [NSString stringWithUTF8String: fn ]
                                                                   error:NULL] valueForKey:NSFileProtectionKey];
   NSLog(@"[fn(%s)=%@",fn,p);
}

void setFileAttributes(const char *fn, int iProtect){
   
   NSString * const pr = iProtect? NSFileProtectionComplete : NSFileProtectionNone;
   NSDictionary *d=[NSDictionary dictionaryWithObject:pr
                                               forKey:NSFileProtectionKey];
   /*
    attributes:[NSDictionary dictionaryWithObject:NSFileProtectionComplete
    forKey:NSFileProtectionKey]];
    */
   /*
    - (BOOL)setAttributes:(NSDictionary *)attributes ofItemAtPath:(NSString *)path error:(NSError **)error
    */
   NSError *err = nil;
   
   
   BOOL b = [[NSFileManager defaultManager]  setAttributes: d ofItemAtPath:[NSString stringWithUTF8String: fn ]error:&err  ];
   if(!b){
      NSLog(@"setFileAttributes(%s,%d)=%d er=[%@]",fn, iProtect,b , err);
   }
}



