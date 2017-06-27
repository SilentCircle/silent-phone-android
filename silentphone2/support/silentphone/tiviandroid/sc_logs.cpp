/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
//
//  Created by Rong Li on 3/4/16.
//

#include "sc_logs.h"

#if !defined(MBEDTLS_CONFIG_FILE)
#include "mbedtls/config.h"
#else
#include MBEDTLS_CONFIG_FILE
#endif

#if defined(POLARSSL_PLATFORM_C)
#include "polarssl/platform.h"
#else
#include <stdio.h>
#endif

#include "mbedtls/aes.h"
#include "mbedtls/md.h"
#include "mbedtls/havege.h"

#include <stdlib.h>
#include <dirent.h>
#include <string.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/time.h>

#include <fstream>
#include <iostream>

#ifdef __ANDROID__
#include <android/log.h> // Android.mk => LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog
#define log_debug(D, M, ...) __android_log_print(ANDROID_LOG_DEBUG, D, "[DEBUG] " M "",  ##__VA_ARGS__)
#endif // __ANDROID__

#ifdef __APPLE__
#define log_debug(D, M, ...) fprintf(stderr, D ": [DEBUG] (%s:%d) \n" M "", __FILE__, __LINE__, ##__VA_ARGS__)
#endif


using namespace std;

static char currentLogFileName[256];

//it includes log entry and total IV record and length of log entry.
static const int MAX_SIZE = 1024 + 16 + 4;
//log entry size is 1024.
static const int LOG_MAX_SIZE = 1024;

static const int IV_SIZE = 16;


static const int LOG_LEN_SIZE = 4;
//IV_SIZE + 4 which is for log entry length
static const int PRE_FIX_SIZE = IV_SIZE + LOG_LEN_SIZE;

//keysize  must be 128, 192 or 256
static const int KEY_SIZE = 256;

static unsigned char buffer[MAX_SIZE];
static unsigned char encryptBuff[LOG_MAX_SIZE];
static unsigned char decryptBuff[LOG_MAX_SIZE];

static unsigned char IV[IV_SIZE]="123456789012345";
static unsigned char IV2[IV_SIZE];

static unsigned char key[KEY_SIZE]="silentcircle";
static mbedtls_aes_context enc_ctx;
static mbedtls_aes_context dec_ctx;

static unsigned char LOG_LEN[LOG_LEN_SIZE];

unsigned char* encryptLogAndWriteToFile(char log[]);

#define UTF8_ACCEPT 0
#define UTF8_REJECT 1

static const uint8_t utf8d[] = {
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, // 00..1f
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, // 20..3f
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, // 40..5f
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0, // 60..7f
        1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9, // 80..9f
        7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7, // a0..bf
        8,8,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2, // c0..df
        0xa,0x3,0x3,0x3,0x3,0x3,0x3,0x3,0x3,0x3,0x3,0x3,0x3,0x4,0x3,0x3, // e0..ef
        0xb,0x6,0x6,0x6,0x5,0x8,0x8,0x8,0x8,0x8,0x8,0x8,0x8,0x8,0x8,0x8, // f0..ff
        0x0,0x1,0x2,0x3,0x5,0x8,0x7,0x1,0x1,0x1,0x4,0x6,0x1,0x1,0x1,0x1, // s0..s0
        1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1, // s1..s2
        1,2,1,1,1,1,1,2,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1, // s3..s4
        1,2,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,3,1,3,1,1,1,1,1,1, // s5..s6
        1,3,1,1,1,1,1,3,1,3,1,1,1,1,1,1,1,3,1,1,1,1,1,1,1,1,1,1,1,1,1,1, // s7..s8
};
uint32_t validate_utf8(uint32_t *state, unsigned char *str, size_t len) {
    size_t i;
    uint32_t type;

    for (i = 0; i < len; i++) {
        type = utf8d[(uint8_t)str[i]];
        *state = utf8d[256 + (*state) * 16 + type];

        if (*state == UTF8_REJECT)
            break;
    }

    return *state;
}
void initLoggingStaticVariables(){
    mbedtls_aes_setkey_enc(&enc_ctx, key, KEY_SIZE);
    mbedtls_aes_setkey_dec(&dec_ctx, key, KEY_SIZE);
    memset(IV, 0, IV_SIZE);
}
void setCurrentFilePath(char *filePath){
    snprintf(currentLogFileName, 256, "%s", filePath);
}

/*
 * buffer structur: first is IV with size of 16,
 * followed by length of log entry with size of 4,
 * and followed by log entry content
 */
unsigned char* encryptLogAndWriteToFile(char log[]) {
    memset(buffer, 0, sizeof(buffer));
    memset(encryptBuff, 0, sizeof(encryptBuff));
    memset(IV2, 0, sizeof(IV2));

    //copy IV into buff
    memcpy(buffer, IV, IV_SIZE);

    //add 1: terminator needs to be taken care
    char c[LOG_LEN_SIZE+1];
    snprintf(c, LOG_LEN_SIZE+1, "%zu", strlen(log));

    //copy size of log entry into buffer
    memcpy(&buffer[IV_SIZE], c, LOG_LEN_SIZE);

    mbedtls_aes_crypt_cbc(&enc_ctx, MBEDTLS_AES_ENCRYPT, LOG_MAX_SIZE, IV, (const unsigned char *) log, encryptBuff);

    //copy encrypted log entry content into buffer
    memcpy(&buffer[PRE_FIX_SIZE], encryptBuff, LOG_MAX_SIZE);

    FILE *fout = fopen(currentLogFileName, "ab+");
    if(fout == NULL){
        log_debug("encryptLogAndWriteToFile","error open a file to write logs");
        return NULL;
    }
    //write buffer where has IV, size of log entry and encrypted log entry to a file
    if(fwrite(buffer, sizeof(unsigned char), sizeof(buffer), fout) != sizeof(buffer)){
        log_debug("encryptLogAndWriteToFile","error write encryptBuff to a file");
    }

    fclose(fout);
    return NULL;
}

char* decryptLogs(char *logFileNames[], int count, const char *dir){
    char *error = NULL;
    FILE *fin;
    FILE *fout;
    char enFilePath[256];
    char deFilePath[256]; //temp to store decrypted logs in silentcircle/temp directory
    char tempDir[256];
    uint32_t state;
    for(int i=0; i<count; i++){
        memset(enFilePath, 0, 256);
        memset(deFilePath, 0, 256);
        memset(tempDir, 0, 256);
        //enFilePath (silentcircle/encyptedLogFileName): encrypted log file path
        snprintf(enFilePath, 256, "%s/%s", dir,logFileNames[i]);
        //log_debug("decryptLogFromFile", " *** enFilePath: %s", enFilePath);

        //deFilePath (silentcircle/temp/decrytedLogFileName): decrypted log file path
        snprintf(deFilePath, 256, "%s/temp/%s", dir,logFileNames[i]);
        //log_debug("decryptLogFromFile", " *** deFilePath: %s", deFilePath);
        
        fin = fopen( enFilePath, "rb" ) ;
        fout = fopen( deFilePath, "w" ) ;

        if(fin != NULL && fout != NULL) {
            memset(buffer, 0, MAX_SIZE);
            memset(encryptBuff, 0, LOG_MAX_SIZE);
            memset(IV2, 0, IV_SIZE);
            memset(decryptBuff, 0, LOG_MAX_SIZE);
            memset(LOG_LEN, 0, LOG_LEN_SIZE);
            while (fread(buffer, sizeof(unsigned char), sizeof(buffer), fin) != 0) {
                memcpy(IV2, buffer, IV_SIZE);
                memcpy(LOG_LEN, &buffer[IV_SIZE], LOG_LEN_SIZE);
                char tmp[5];
                memcpy(tmp, LOG_LEN, LOG_LEN_SIZE);

                memcpy(encryptBuff, &buffer[PRE_FIX_SIZE], LOG_MAX_SIZE);
                mbedtls_aes_crypt_cbc(&dec_ctx, MBEDTLS_AES_DECRYPT, LOG_MAX_SIZE, IV2, encryptBuff, decryptBuff);

                int log_len = atoi(tmp);
                //log_debug("decryptLogFromFile", "*** decrypt: %d, %d, %s\n", sizeof(encryptBuff), sizeof(decryptBuff), decryptBuff);

                //validate decrypted data is valid UTF-8 format.
                state = 0;
                if (validate_utf8(&state, decryptBuff, log_len) == UTF8_ACCEPT) {
                    //temp save decrypted logs into files to be sent to server.
#if defined(ANDROID_NDK)
                    if(fprintf(fout, "%s", decryptBuff) == 0){
#else
                    if (fprintf(fout, "%s\n", decryptBuff) == 0) {
#endif

                        log_debug("decryptLogFromFile", "error write decryptBuff to a file");
                        if (error == NULL) {
                            error = (char *) malloc(256 * sizeof(char));
                        } else {
                            memset(error, 0, 256);
                        }
                        snprintf(error, 256, "error write decryptBuff to a file: %s", deFilePath);
                    }

                    memset(buffer, 0, MAX_SIZE);
                    memset(encryptBuff, 0, LOG_MAX_SIZE);
                    memset(IV2, 0, IV_SIZE);
                    memset(decryptBuff, 0, LOG_MAX_SIZE);
                }
            }
            fclose(fin);
            fclose(fout);
        }
        else {
            if(error == NULL){
                error = (char*)malloc(256 * sizeof(char));
            }else{
                memset(error, 0, 256);
            }
            if (fin == NULL) {
                snprintf(error, 256, "Can not open it for read: %s", enFilePath);
            }else{
                snprintf(error, 256, "Can not open it for write: %s", deFilePath);
            }
        }
    }

    return error;
}

void writeToFile(const char *logMessage) {
    // log time
    timeval tp;
    gettimeofday(&tp, 0);
    time_t curtime = tp.tv_sec;
    tm *tt = localtime(&curtime);

    char d[20];
    strftime(d, sizeof(d), "%Y-%m-%d", tt);
    char t[80];
    snprintf(t, 80, "%s %02d:%02d:%02d.%03d", d, tt->tm_hour, tt->tm_min, tt->tm_sec, (int)tp.tv_usec/1000);

    // concatenate log time and log message
    char log[MAX_SIZE];
    snprintf(log, sizeof(log), "%s %s", t, logMessage);

    encryptLogAndWriteToFile(log);
    
}


