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

#include <string.h>

const char *getPwdById(int id){
   const char *p[3];
#if  defined(_WIN32_WCE) || defined(__SYMBIAN32__) || defined(ANDROID_NDK)
   p[0]="gzhmewpkjanwtwuqcdey";
   p[1]="drtjwfqyyhsvmgzntgms";
   p[2]="naphgaunpenhrfaxwrun";
#else
   p[0]="fzrssvjzpgdvdcbxsixn";
   p[1]="dsbypdzfbcpruuujwcqm";
   p[2]="ctjjmrgzhsdqzyffknfm";
#endif
   return p[id];
}

const char *getTrialKey(){
   return "fhgbctkcxdytttbyuvkj";
}

void getLicnecePwd(char *p, const char *user){
   strcpy(p,"ACK sip");
   p[7]=':';
   p[8]=0;
   strcat(p,user);
}

void getSecret1(char *pwd){
   
   strcpy(pwd,"REGvangpaoiefnao");
}

const char *getSettingsPWD1(){
   return "a=rtpmap:3 GSM/8000/1\r\n";
}

