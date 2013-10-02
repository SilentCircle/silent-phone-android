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

#ifndef _T_OS_DEFINE_H
#define _T_OS_DEFINE_H
#ifdef _WIN32
   #define close closesocket
   #define sleep Sleep
   #define THREAD_FNC DWORD WINAPI
   #define SHUT_RDWR SD_BOTH
   #define TH_HANDLE HANDLE
   #define CREATE_TH(H,FNC,PARAM,RET) H=CreateThread(NULL,0,FNC,(void *)PARAM,0,(DWORD *)&(RET))
   #define snprintf _snprintf
   #define CLOSE_TH  CloseHandle
#else
   #define closesocket close
   #define Sleep(U) usleep((U)*1000);
   #define THREAD_FNC void *
   #define SOCKET int
   #define UINT unsigned int
   #define USHORT unsigned short
   #define BOOL unsigned int
   #define TH_HANDLE pthread_t
   #define TRUE  1
   #define FALSE 0
   #define SD_BOTH SHUT_RDWR
   #define SetThreadPriority  //
   #define CLOSE_TH  pthread_detach
   #define socklen_t int
   //pthread_create(&thComDataReader, NULL, (void*)thfnComDataReader, NULL);
   #define CREATE_TH(H,FNC,PARAM,RET)  RET=pthread_create(&(H), NULL, FNC, PARAM);
#endif


#ifdef _WIN32
   #include <winsock2.h>
   #include <windowsx.h>
   #include <mmsystem.h>
  // #include "C:\vajag\cpp\Xml\parse_xml.h"
#else
//   #include <netinet/ip.h>
   //#include <netinet/udp.h>
   #include <getopt.h>
   #include <unistd.h>
   #include <sys/stat.h>
   //#include <sys/time.h>
   #include <net/if.h>
   #include <sys/ioctl.h>
   #include <net/if_arp.h>

   #include <errno.h>
   #include <sys/types.h>
   #include <pthread.h>
   #include <netdb.h>
   #include <sys/socket.h>
   #include <arpa/inet.h>
#endif
#endif //_T_OS_DEFINE_H