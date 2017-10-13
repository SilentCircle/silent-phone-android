/*
Copyright (C) 2015-2017, Silent Circle, LLC.  All rights reserved.

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
#ifndef AXOLOTL_GLUE_H
#define AXOLOTL_GLUE_H

#include <string>
#include <vector>

typedef int32_t (*RECV_FUNC)(const std::string&, const std::string&, const std::string&);
typedef void (*STATE_FUNC)(int64_t, int32_t, const std::string&);
typedef void (*NOTIFY_FUNC)(int32_t, const std::string&, const std::string&);

typedef bool (*SEND_DATA_FUNC)(uint8_t*, uint8_t*, uint8_t*, size_t, uint64_t);

typedef int32_t (*GROUP_CMD_RECV_FUNC)(const std::string& commandMessage);
typedef int32_t (*GROUP_MSG_RECV_FUNC)(const std::string& messageDescriptor, const std::string& attachmentDescriptor, const std::string& messageAttributes);
typedef void (*GROUP_STATE_FUNC)(int32_t errorCode, const std::string& stateInformation);

class CTAxoInterfaceBase{

public:
   enum{
       eErrorNoTransportReady=-1000,
       eErrorDBNotReady=-1001,
       eErrorNoFunctionPointers=-1002
   };
   //Q:  should I init this when I recv or send via network
   //A: maybe, but I have to get(find) my own username

   //Q2: should I rename this to sharedObject, and use as singleton
   //    and find object by myUsername
   static int isDBFailCode(int v);

   static CTAxoInterfaceBase *sharedInstance(const char *myUsername = NULL);

   static int isAxolotlReady();

   static int addPrekeys(int cnt);//this must be static because it is possible when we call the addPrekeys axolotl is not yet ready

   //from transport,
   //see axolotl/browse/interfaceTransport/Transport.h
   virtual int32_t receiveMessage(uint8_t* data, size_t length) = 0;

   virtual int32_t receiveMessage(uint8_t* data, size_t length, uint8_t* uid, size_t ulength, uint8_t* alias, size_t alength) = 0;

   virtual void stateReport(int64_t messageIdentifier, int32_t stateCode, uint8_t* data, size_t length) = 0;
   virtual void notifyAxo(uint8_t* data, size_t length) = 0;

#ifndef ANDROID_NDK
   static const char *getErrorMsg(int id);
   //from UI, creates JSON itself
  // virtual int64_t sendMessage(const char *dstUsername, const char *msg) = 0;
   virtual int64_t sendJSONMessage( const char *msg, bool toSibling, bool normalMsg, const char *attachment=NULL, const char *attributes=NULL) = 0;

   static const char *generateMsgID(const char *msgToSend, char *dst, int iDstSize);//helper
   static time_t uuid_sz_time(const char * szUUID, struct timeval *ret_tv = NULL);

   static void setCallbacks(STATE_FUNC state, RECV_FUNC msgRec, NOTIFY_FUNC notify);//should call this from UI before init
   static void setGroupCallbacks(GROUP_STATE_FUNC state, GROUP_MSG_RECV_FUNC msgRec, GROUP_CMD_RECV_FUNC cmd);//should call this from UI before init

   void *getAxoAppInterface();
#endif

   static void setSendCallback(SEND_DATA_FUNC p);//should call this from transport side

   //TODO static getErrorCode, getErrorInfo, getKnownUsers
};

//AXOLOTL_GLUE_H
#endif
