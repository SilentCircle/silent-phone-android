/*
Created by Janis Narbuts
Copyright © 2004-2012 Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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
#include "../baseclasses/CTEditBase.h"
#include "../os/CTiViSock.h"
#include "../os/CTThread.h"
#include "../os/CTTcp.h"
#include "../encrypt/tls/CTTLS.h"

#ifdef _WIN32
#define snprintf _snprintf
#endif


const char *pEntrustCert=
"-----BEGIN CERTIFICATE-----\r\n"
"MIIE2DCCBEGgAwIBAgIEN0rSQzANBgkqhkiG9w0BAQUFADCBwzELMAkGA1UEBhMC\r\n"
"VVMxFDASBgNVBAoTC0VudHJ1c3QubmV0MTswOQYDVQQLEzJ3d3cuZW50cnVzdC5u\r\n"
"ZXQvQ1BTIGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTElMCMGA1UECxMc\r\n"
"KGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDE6MDgGA1UEAxMxRW50cnVzdC5u\r\n"
"ZXQgU2VjdXJlIFNlcnZlciBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTAeFw05OTA1\r\n"
"MjUxNjA5NDBaFw0xOTA1MjUxNjM5NDBaMIHDMQswCQYDVQQGEwJVUzEUMBIGA1UE\r\n"
"ChMLRW50cnVzdC5uZXQxOzA5BgNVBAsTMnd3dy5lbnRydXN0Lm5ldC9DUFMgaW5j\r\n"
"b3JwLiBieSByZWYuIChsaW1pdHMgbGlhYi4pMSUwIwYDVQQLExwoYykgMTk5OSBF\r\n"
"bnRydXN0Lm5ldCBMaW1pdGVkMTowOAYDVQQDEzFFbnRydXN0Lm5ldCBTZWN1cmUg\r\n"
"U2VydmVyIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MIGdMA0GCSqGSIb3DQEBAQUA\r\n"
"A4GLADCBhwKBgQDNKIM0VBuJ8w+vN5Ex/68xYMmo6LIQaO2f55M28Qpku0f1BBc/\r\n"
"I0dNxScZgSYMVHINiC3ZH5oSn7yzcdOAGT9HZnuMNSjSuQrfJNqc1lB5gXpa0zf3\r\n"
"wkrYKZImZNHkmGw6AIr1NJtl+O3jEP/9uElY3KDegjlrgbEWGWG5VLbmQwIBA6OC\r\n"
"AdcwggHTMBEGCWCGSAGG+EIBAQQEAwIABzCCARkGA1UdHwSCARAwggEMMIHeoIHb\r\n"
"oIHYpIHVMIHSMQswCQYDVQQGEwJVUzEUMBIGA1UEChMLRW50cnVzdC5uZXQxOzA5\r\n"
"BgNVBAsTMnd3dy5lbnRydXN0Lm5ldC9DUFMgaW5jb3JwLiBieSByZWYuIChsaW1p\r\n"
"dHMgbGlhYi4pMSUwIwYDVQQLExwoYykgMTk5OSBFbnRydXN0Lm5ldCBMaW1pdGVk\r\n"
"MTowOAYDVQQDEzFFbnRydXN0Lm5ldCBTZWN1cmUgU2VydmVyIENlcnRpZmljYXRp\r\n"
"b24gQXV0aG9yaXR5MQ0wCwYDVQQDEwRDUkwxMCmgJ6AlhiNodHRwOi8vd3d3LmVu\r\n"
"dHJ1c3QubmV0L0NSTC9uZXQxLmNybDArBgNVHRAEJDAigA8xOTk5MDUyNTE2MDk0\r\n"
"MFqBDzIwMTkwNTI1MTYwOTQwWjALBgNVHQ8EBAMCAQYwHwYDVR0jBBgwFoAU8Bdi\r\n"
"E1U9s/8KAGv7UISX8+1i0BowHQYDVR0OBBYEFPAXYhNVPbP/CgBr+1CEl/PtYtAa\r\n"
"MAwGA1UdEwQFMAMBAf8wGQYJKoZIhvZ9B0EABAwwChsEVjQuMAMCBJAwDQYJKoZI\r\n"
"hvcNAQEFBQADgYEAkNwwAvpkdMKnCqV8IY00F6j7Rw7/JXyNEwr75Ji174z4xRAN\r\n"
"95K+8cPV1ZVqBLssziY2ZcgxxufuP+NXdYR6Ee9GTxj005i7qIcyunL2POI9n9cd\r\n"
"2cNgQ4xYDiKWL2KjLB+6rQXvqzJ4h6BUcxm1XAX5Uj5tLUUL9wqT6u0G+bI=\r\n"
"-----END CERTIFICATE-----\r\n"
;


int retryConnectProvServ(){
#ifdef _WIN32
   return IDRETRY==::MessageBoxA(NULL,"Could not connect to the server. Check your internet connection and try again.","Info",MB_ICONERROR|MB_RETRYCANCEL);
#else
   return 1;
#endif
}

#ifdef _WIN32
int showSSLErrorMsg(void *ret, const char *p){
   MessageBoxA(NULL,"Server's security certificate validation has failed, phone will exit.","SSL TLS error",MB_ICONERROR|MB_OK);
   exit(1);
   return 0;
}
#else
int showSSLErrorMsg(void *ret, const char *p);
#endif

static int respF(void *p, int i){
   int *rc=(int*)p;
   *rc=1;
   return 0;
}

void tivi_log(const char* format, ...);


typedef struct{
   void (*cb)(void *p, int ok, const char *pMsg);
   void *ptr;
}SSL_RET;

int showSSLErrorMsg2(void *ret, const char *p){
   
   
#ifdef _WIN32
   const char *pErr="Server's security certificate validation has failed, phone will exit.";
   MessageBoxA(NULL,pErr,"SSL TLS error",MB_ICONERROR|MB_OK);
   exit(1);
   return 0;
#else
   const char *pErr="Server's security certificate validation has failed.";
   SSL_RET *s=(SSL_RET*)ret;
   if(s){
      s->cb(s->ptr,-2,pErr);
   }
   return 0;
#endif
}


char* download_page2(const char *url, char *buf, int iMaxLen, int &iRespContentLen, 
                    void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   
   char bufU[1024];
   char bufA[1024];
   memset(buf,0,iMaxLen);
   
   //CTSockTcp
   CTTHttp<CTTLS> *s=new CTTHttp<CTTLS>(buf,iMaxLen);
   CTTLS *tls=s->createSock();
   int r=s->splitUrl((char*)url, strlen(url),&bufA[0],&bufU[0]);

   if(r<0){
      cb(cbRet,-1,"Check url");
      return 0;
   }
   
   printf("u=%s U=%s A=%s\n",url,bufU,bufA);
   
   const char *pathFN1="prov_cert.prov";//(const char*)T_getSometing2(NULL,"path","prov_cert.crt");
   
   SSL_RET ssl_ret;
   ssl_ret.cb=cb;
   ssl_ret.ptr=cbRet;
   
   printf("path=%s\n",pathFN1);

   
   if(pathFN1){
      int iLen;
#ifdef __APPLE__
      char *iosLoadFile(const char *fn, int &iLen );
      char *p=iosLoadFile(pathFN1,iLen);
   
#else
      
      const short *pathFN=(const short*)T_getSometing1(NULL,"provCertFN");//fix
      char *p=NULL;
      if(pathFN)p=loadFileW(pathFN,iLen);
      if(!p){
         p=(char*)pEntrustCert;
         iLen=strlen(p);
      }
      
#endif
      printf("path ptr=%p l=%d\n",p,iLen);
      if(p && iLen>0){
         

         tls->errMsg=&showSSLErrorMsg2;
         tls->pRet=&ssl_ret;
         
         char bufZ[256];
         int i=0;
         int iMaxL=sizeof(bufZ)-1;
         for(;i<iMaxL;i++){
            if(bufA[i]==':' || !bufA[i]){break;}
            bufZ[i]=bufA[i];
         }
         bufZ[i]=0;
         printf("path ptr=%p l=%d addr=[%s]\n",p,iLen,&bufZ[0]);
         tls->setCert(p,iLen,&bufZ[0]);
         if(p!=pEntrustCert)
            delete p;
         
      }else{
         cb(cbRet,-1,"No cert");
         return 0;
      }
      
   }else{
      cb(cbRet,-1,"No cert");
      return 0;
   }
   int iRespCode=0;
   
   CTTHttp<CTTLS>::HTTP_W_TH wt;
   wt.ptr=&iRespCode;
   wt.respFnc=respF;
   s->waitResp(&wt,60);
   
   cb(cbRet,1,"Downloading...");
   
   s->getUrl(tls,&bufU[0],&bufA[0],"GET");//locks
   iRespContentLen=0;
   char *p=s->getContent(iRespContentLen);
   
   if(p)cb(cbRet,1,"Downloading ok");
   
   int c=0;
   while(iRespCode==0){Sleep(50);c++;if(c>200)break;}
   delete s;
   return p;
}

//#define PROV_TEST

int findJSonToken(const char *p, int iPLen, const char *key, char *resp, int iMaxLen){
   
   int iKeyLen=strlen(key);
   resp[0]=0;
   
   for(int i=0;i<iPLen;i++){
      if(i+iKeyLen+3>iPLen)return -1;
      if(p[i]=='"' && p[i+iKeyLen+1]=='"' && p[i+iKeyLen+2]==':' 
         && strncmp(key,&p[i+1],iKeyLen)==0){
         
         i+=iKeyLen+2;
         while(p[i] && p[i]!='"' && i<iPLen)i++;
         if(i>=iPLen)return -2;
         i++;
         
         int l=0;
         iMaxLen--;
         
         while(p[i] && p[i]!='"' && l<iMaxLen && i<iPLen){resp[l]=p[i];i++;l++;}
         if(i>=iPLen)return -2;
         resp[l]=0;
         
         return l;
      }
   }
   
   return 0;
}

int getToken(const char *pLink, char *resp, int iMaxLen, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   
   int iRespContentLen=0;
   
   int l;
   
   char bufResp[4096];
   char bufJSonValue[1024];
   
#if 0
   const char *pTest="{\"api_key\": \"z46d3856f8ff292f2eb8dab4e5e51edf5b951fb6e6eb01c80662157z\", \"result\": \"success\"}";
   
   iRespContentLen=strlen(pTest);
   l=findJSonToken(pTest,iRespContentLen,"api_key",&bufResp[0],1023);
   if(l>0)printf("token=[%.*s]\n",l,bufResp);

   l=findJSonToken(pTest,iRespContentLen,"result",&bufResp[0],4095);
   if(l>0)printf("token=[%.*s]\n",l,bufResp);
   exit(1);
#endif
   
   
   memset(bufResp,0,sizeof(bufResp));

   
   char *p=download_page2(pLink, &bufResp[0], sizeof(bufResp)-50, iRespContentLen,cb,cbRet);
   
   if(!p){
      cb(cbRet,0,"Please check network connection.");//download json fail
      return -1;
   }
#ifdef PROV_TEST
   printf("rec[%.*s]\n",iRespContentLen,p);//rem
   printf("rec-t[%s]\n",bufResp);//rem
#endif
   l=findJSonToken(p,iRespContentLen,"result",&bufJSonValue[0],1023);
   if(l<=0){
      cb(cbRet,0,"ERR: Result is not found");
      return -1;
   }
   if(strcmp(&bufJSonValue[0],"success")){
      l=findJSonToken(p,iRespContentLen,"error_msg",&bufJSonValue[0],1023);
      if(l>0)
         cb(cbRet,-1,&bufJSonValue[0]);
      else{
         cb(cbRet,-1,"Could not download configuration!");
      }
      return -1;
   }
   
   
   l=findJSonToken(p,iRespContentLen,"api_key",&bufJSonValue[0],1023);
   if(l<=0 || l>256 || l>iMaxLen){
      cb(cbRet,0,"ERR: Find api_key failed");
      return -1;
   }
   int ret=snprintf(resp,iMaxLen,"%s",&bufJSonValue[0]);
   resp[iMaxLen]=0;
   return ret;
}

const char *pFN_to_save[]  ={"settings.txt","tivi_cfg10555.xml","tivi_cfg.xml","tivi_cfg1.xml",NULL};
int isFileExistsW(const short *fn);
void setCfgFN(CTEditBase &b, int iIndex);
void setCfgFN(CTEditBase &b, const char *fn);

void delProvFiles(const int *p, int iCnt){
   CTEditBase b(1024);
   for(int i=0;i<2;i++){
      if(!pFN_to_save[i])break;
      setCfgFN(b,pFN_to_save[i]);
      deleteFileW(b.getText());
   }
   
   char buf[64];
   
   for(int i=0;i<iCnt;i++){
      printf("del prov %d\n",p[i]);
      if(p[i]==1)continue;//dont delete if created by user
      if(i)snprintf(buf, sizeof(buf)-1, "tivi_cfg%d.xml", i); else strcpy(buf,"tivi_cfg.xml");
      setCfgFN(b,buf);
      deleteFileW(b.getText());
   }
}

static int iProvisioned=-1;//unknown

int checkProv(const char *pUserCode, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   /*
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg.xml?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/settings.txt?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg_glob.txt?api_key=12345
    */
   const char *pLink="https://accounts.silentcircle.com";
   char bufReq[1024];
   const char *t_getDevID_md5();
   const char *t_getDev_name();
   
   const char *dev_id=t_getDevID_md5();
   const char *dev_name=t_getDev_name();
   

#define CHK_BUF \
   if(l+100>sizeof(bufReq)){\
      return -1;\
   }

   int l=snprintf(bufReq,sizeof(bufReq)-10,"%s/provisioning/use_code/?provisioning_code=",pLink);
   
   CHK_BUF 
   
   l+=fixPostEncodingToken(&bufReq[l],sizeof(bufReq)-10-l,pUserCode,strlen(pUserCode));
   
   CHK_BUF
   
   l+=snprintf(&bufReq[l],sizeof(bufReq)-10-l,"&device_id=%s&device_name=",dev_id);
   
   CHK_BUF
   
   l+=fixPostEncodingToken(&bufReq[l],sizeof(bufReq)-10-l, dev_name,strlen(dev_name));
   
   CHK_BUF
   
#undef CHK_BUF
   
   char bufToken[1024];
   int r=getToken(&bufReq[0], &bufToken[0],255,cb,cbRet);
   if(r<0){
      return -1;
   }
   
   cb(cbRet,1,"Configuration code ok");
   
   
   char bufCfg[4096];
   
   const char *pFN_to_download[]   ={"settings.txt","tivi_cfg_glob.txt","tivi_cfg.xml","tivi_cfg1.xml",NULL};

   const char *pFNErr[]={"D-Err1","D-Err2","D-Err3","D-Err4","D-Err5","D-Err6",NULL};
   
   const char *p10_200ok="HTTP/1.0 200 OK";
   const char *p11_200ok="HTTP/1.1 200 OK";
   
   int iLen200ok=strlen(p10_200ok);
   
   int iCfgPos=0;
   
   for(int i=0;;i++){
      if(!pFN_to_download[i] || !pFN_to_save[i])break;
      snprintf(bufReq,sizeof(bufReq)-1,"%s/provisioning/silent_phone/%s?api_key=%s",pLink,pFN_to_download[i],bufToken);
      
      int iRespContentLen=0;
      char* p=download_page2(&bufReq[0], &bufCfg[0], sizeof(bufCfg)-100, iRespContentLen,cb,cbRet);
      if(!p && i>2){
         // we have 1 account
         break;
      }
      
      if(!p || (strncmp(&bufCfg[0],p10_200ok,iLen200ok) && strncmp(&bufCfg[0],p11_200ok,iLen200ok))){
         if(i>2){
            // we have 1 account
            break;
         }
         cb(cbRet,0,pFNErr[i]);
         return -2;
      }
      cb(cbRet,1,pFN_to_save[i]);
      
      void saveCfgFile(const char *fn, void *p, int iLen);
      int saveCfgFile(int iNextPosToTest, void *p, int iLen);
#if 0
#ifndef PROV_TEST
      saveCfgFile(pFN_to_save[i],p,iRespContentLen);
#endif

      printf("Saving %s content=[%.*s]\n",pFN_to_save[i], iRespContentLen,p);
#else
      
      if(strncmp("tivi_cfg", pFN_to_save[i],8) || 0==strcmp("tivi_cfg_glob.txt", pFN_to_download[i])){
#ifndef PROV_TEST
         saveCfgFile(pFN_to_save[i],p,iRespContentLen);
#endif
         
         printf("Saving %s content=[%.*s]\n",pFN_to_save[i], iRespContentLen,p);
      }
      else{
         iCfgPos=saveCfgFile(iCfgPos, p,iRespContentLen);
         printf("Saving pos=%d content=[%.*s]\n",iCfgPos-1, iRespContentLen,p);
      }
      
#endif
   }
   cb(cbRet,1,"OK");
   iProvisioned=1;
   return 0;
}

void setFileBackgroundReadable(CTEditBase &b);
void setOwnerAccessOnly(const short *fn);

int saveCfgFile(int iNextPosToTest, void *p, int iLen){

   char fn[64];
   CTEditBase b(1024);
#define MAX_CFG_FILES 10000
   
   for(int i=iNextPosToTest;i<MAX_CFG_FILES;i++){
      if(i)snprintf(fn, sizeof(fn)-1, "tivi_cfg%d.xml", i); else strcpy(fn,"tivi_cfg.xml");
      setCfgFN(b, fn);
      if(!isFileExistsW(b.getText())){
         //save into i pos
         iNextPosToTest=i+1;
         break;
      }
   }

   saveFileW(b.getText(),p,iLen);
   setOwnerAccessOnly(b.getText());
   setFileBackgroundReadable(b);
   
   return iNextPosToTest;
}



void saveCfgFile(const char *fn, void *p, int iLen){
   
   CTEditBase b(1024);
   setCfgFN(b,fn);
   saveFileW(b.getText(),p,iLen);
   
   setOwnerAccessOnly(b.getText());
   setFileBackgroundReadable(b);
}
void tivi_log1(const char *p, int val);

int isProvisioned(int iCheckNow){
#ifdef PROV_TEST
   return 0;
#endif
   
   
   if(iProvisioned!=-1 && !iCheckNow)return iProvisioned;
   
   CTEditBase b(1024);
   
//   setCfgFN(b,0);
   
   do{
      iProvisioned=0;
      /*
      if(isFileExistsW(b.getText())){
         iProvisioned=1;
         break;
      }
      */
      int getGCfgFileID();
      setCfgFN(b,getGCfgFileID());
      if(isFileExistsW(b.getText())){
         iProvisioned=1;
         break;
      }
      
      tivi_log1("isProvisioned fail ",getGCfgFileID());
      
   }while(0);
   //int isFileExists(const char *fn);
   return iProvisioned;
}

//-----------------android-------------------

class CTProvNoCallBack{
public:
   int iHasData;
   int iProvStat;
   int okCode;
   char bufMsg[256];

   
   CTProvNoCallBack(){
      reset();
   }
   void reset(){
      
      memset(bufMsg, 0, sizeof(bufMsg));
      iHasData=0;
      okCode=0;
      iProvStat=0;
   }
   void provCallBack(void *p, int ok, const char *pMsg){
      
      if(ok<=0){
         if(okCode<ok)return;
         okCode=ok;
         strncpy(bufMsg,pMsg,sizeof(bufMsg)-2);
         bufMsg[sizeof(bufMsg)-1]=0;

      }
      else{

         iProvStat++;
         int res=iProvStat*100/14;
         if(res>100)res=100;
         sprintf(bufMsg,"%d %% done",res);
         //progress
      }
      iHasData++;
   }
   const char *getInfo(){
      iHasData=0;
      return &bufMsg[0];
   }
   
};

CTProvNoCallBack provNCB;

static void provCallBack(void *p, int ok, const char *pMsg){
   provNCB.provCallBack(p, ok, pMsg);
}

const char* getProvNoCallBackResp(){
   return provNCB.getInfo();
}
//prov.tryGetResult
//porv.start=code
int checkProvNoCallBack(const char *pUserCode){
   provNCB.reset();
   return checkProv(pUserCode, provCallBack, &provNCB);
}
/*
-(void)cbTLS:(int)ok  msg:(const char*)msg {
   NSLog(@"prov=[%s] %d",msg,ok);
   
   if(ok<=0){
      if(iPrevErr==-2)return;
      iPrevErr=ok;
      dispatch_async(dispatch_get_main_queue(), ^{
         
         [self showMsgMT:@"Can not download configuration, check code ID and try again."  msg:msg];
      });
   }
   else{
      iProvStat++;
      
      dispatch_async(dispatch_get_main_queue(), ^{
         float f=(float)iProvStat/14.;
         if(f>1.)f=1.;
         [uiProg setProgress:f animated:YES];
      });
   }
}

 */





