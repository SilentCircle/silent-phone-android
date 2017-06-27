/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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
#include <stdlib.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <dirent.h>
#include "t_devID.h"

#define T_MAX_DEV_ID_LEN 63
#define T_MAX_DEV_ID_LEN_BIN (T_MAX_DEV_ID_LEN/2)

static char bufDevID[T_MAX_DEV_ID_LEN+2];
static char bufMD5[T_MAX_DEV_ID_LEN+32+1];

void setFileAttributes(const char *fn, int iProtect);
char *getFileStorePath();

devIdErrorCode initDevID(){
    
    static int iInitOk = 0;

    if(iInitOk)
        return devIdErrorCodeNoError;

    // Check if the directory exists
    DIR* dir = opendir(getFileStorePath());
    
    if (dir == NULL)
        return devIdErrorCodeNoDirectory;

    closedir(dir);
    
    memset(bufDevID,0,sizeof(bufDevID));

    size_t iDevIdLen = 0;

    char fn[1024];

    snprintf(fn,sizeof(fn)-1, "%s/devid-hex.txt", getFileStorePath());

    FILE *f;

    if(!(f=fopen(fn,"rb"))) {

        if(errno != ENOENT) {

            // An unhandled error occurred; either the file is there and we
            // can't access it, or something even more strange is happening
            return devIdErrorCodeDevIdHexFOpenMalformed;
        }

        // The deviceID file does not exist; we'll create it and
        // initialize it to a random value.
        if(!(f=fopen("/dev/urandom","rb")))
            return devIdErrorCodeNoRandom;

        unsigned char buf[T_MAX_DEV_ID_LEN_BIN+1];

        if (fread(buf,1,T_MAX_DEV_ID_LEN_BIN,f) != T_MAX_DEV_ID_LEN_BIN) {
            
            // We read too few bytes of entropy.
            return devIdErrorCodeFewBytesOfEntropy;
        }

        if (fclose(f) == EOF)
            return devIdErrorCodeRandomFClose;

        void bin2Hex(unsigned char *Bin, char * Hex ,int iBinLen);
        bin2Hex(buf, bufDevID, T_MAX_DEV_ID_LEN_BIN);
        bufDevID[T_MAX_DEV_ID_LEN]=0;
        iDevIdLen = T_MAX_DEV_ID_LEN;

        if(!(f=fopen(fn,"wb+")))
            return devIdErrorCodeDevIdHexFOpen;

        if (fwrite(bufDevID,1,T_MAX_DEV_ID_LEN,f) != T_MAX_DEV_ID_LEN) {
            
            // We failed to save the full deviceID.
            unlink(fn); // This could fail, but then we're out of options.
            return devIdErrorCodeSaveFailure;
        }

        if (fclose(f) == EOF)
            return devIdErrorCodeDevIdHexFClose;

    } else {

        if (!(iDevIdLen = fread(bufDevID,1,T_MAX_DEV_ID_LEN,f)))
            return devIdErrorCodeSaveFailure;

        bufDevID[T_MAX_DEV_ID_LEN] = 0;

        if (fclose(f) == EOF)
            return devIdErrorCodeDevIdHexFClose;
    }
    
    // The deviceID file already existed or we've now created it.
    int isBackgroundReadable(const char *fn); // TODO: layering violation

    if (!isBackgroundReadable(fn)) {
        
        // The file is set with something other than the
        // NSFileProtectionNone attribute; this would prevent us from
        // accessing the file when the phone is locked; we'll set this
        // attribute.
        void log_file_protection_prop(const char *fn);
        log_file_protection_prop(fn);
        setFileAttributes(fn,0);
    }

    int calcMD5(unsigned char *p, int iLen, char *out);
    // per Travis/Janis hashing with leading zero error
    calcMD5((unsigned char*)bufDevID, (int)strnlen(bufDevID,T_MAX_DEV_ID_LEN),bufMD5);

    iInitOk = 1;

    return devIdErrorCodeNoError;
}

const char *t_getDevID(int &l){
    
    devIdErrorCode devIdErrorCode = initDevID();
    
    if(devIdErrorCode != devIdErrorCodeNoError)
        return NULL;
    
    l = 63; // SP: Can't we have initDevID() also return the length instead of hard coding it?
    
    return bufDevID;
}

const char *t_getDevID_md5(){

    devIdErrorCode devIdErrorCode = initDevID();
    
    if(devIdErrorCode != devIdErrorCodeNoError)
        return NULL;

    return bufMD5;
}
