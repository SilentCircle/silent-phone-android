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

#ifndef _TINA_EXP_H
#define _TINA_EXP_H
#define _T_EXP_V extern 
class CTBmpBase;
_T_EXP_V void relTinaEnc(void *p);
_T_EXP_V void relTinaDec(void *p);
_T_EXP_V void *initTinaEnc();
_T_EXP_V void *initTinaDec();
_T_EXP_V int tina_decode(void *ctx, unsigned char *pI, int iLen, CTBmpBase *cVO);
_T_EXP_V int tina_encode(void *ctx, unsigned char *pI, unsigned char *pOut, int cx, int cy);
_T_EXP_V int tinaSpliter(unsigned char *pI, int iLen);
_T_EXP_V int tinaCmdD(void *ctx, const char *cmd);
_T_EXP_V int tinaCmdE(void *ctx, const char *cmd);
_T_EXP_V int tinaCanDrawThis(unsigned char *pI, int iLen);


_T_EXP_V int tinaCanSkipThis(unsigned char *pI, int iLen);
_T_EXP_V int tina_canDecode(unsigned char *pI, int iLen);
#endif
