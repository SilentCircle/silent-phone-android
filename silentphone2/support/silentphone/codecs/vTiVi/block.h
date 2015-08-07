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


#ifndef BLOCK_H
#define BLOCK_H

#define T_CAN_TEST_V


typedef void (*t_move_pel_fnc)(unsigned char *dst, unsigned char *src, const int stride, const int stridedst);
typedef void (*t_move_bi_fnc)(unsigned char *dst, unsigned char *src1, t_move_pel_fnc *fnc1,
                                                             unsigned char *src2, t_move_pel_fnc *fnc2, const int stride);
typedef struct{
   //0 fullpel,1 h-hpel,2 v-hpel,3 hv-hpel
   enum{eWH2,eWH4,eWH8,eWH16,eWH32,eWH64,e_LAST};
   t_move_pel_fnc m[e_LAST][4];
   t_move_bi_fnc mbi[e_LAST];

   t_move_pel_fnc mt[e_LAST][4];
   t_move_bi_fnc mbit[e_LAST];
   
}T_MOVE_PEL;
void initMoveBl(T_MOVE_PEL *p);

#endif
