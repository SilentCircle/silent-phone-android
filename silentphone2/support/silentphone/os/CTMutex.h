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

#ifndef _C_T_MUTEX_H
#define _C_T_MUTEX_H
#ifdef __SYMBIAN32__
#include <e32std.h>

class CTMutex{// panems no os
public:
   CTMutex(){m.CreateLocal();}    
   ~CTMutex(){m.Close();}    
   inline void lock(){m.Wait();}
   inline void unLock(){m.Signal();}
private:
   RMutex   m;
};
#elif  defined(_WIN32_WCE) ||  defined(_WIN32)

#include <string.h>
#include <winbase.h>

class CTMutex{// panems no os
public:
   CTMutex(){memset(&m,0,sizeof(m));InitializeCriticalSection(&m);}   
   ~CTMutex(){DeleteCriticalSection(&m);}    
   inline void lock(){EnterCriticalSection(&m);}
   inline void unLock(){LeaveCriticalSection(&m);}
private:
  CRITICAL_SECTION m;
};
#else //linux

#include <pthread.h>

class CTMutex{// panems no os
public:
   CTMutex(){pthread_mutex_init(&m,NULL);}   
   ~CTMutex(){pthread_mutex_destroy(&m);}    
   inline void lock(){pthread_mutex_lock(&m);}
   inline void unLock(){pthread_mutex_unlock(&m);}
private:
  pthread_mutex_t m;
};
#endif  //os


class CTMutexAutoLock{
   CTMutex &m;
public:
   CTMutexAutoLock(CTMutex &m):m(m){
      m.lock();
   }
   ~CTMutexAutoLock(){
      m.unLock();
   }
};



#endif //_C_T_MUTEX_H
