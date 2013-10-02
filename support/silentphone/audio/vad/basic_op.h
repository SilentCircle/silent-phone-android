/*
Created by Janis Narbuts
Copyright © 2004-2012, Tivi LTD,www.tiviphone.com. All rights reserved.
Copyright © 2012-2013, Silent Circle, LLC. All rights reserved.

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

/*___________________________________________________________________________
 |                                                                           |
 |   Constants and Globals                                                   |
 |                                                                           |
 | $Id $
 |___________________________________________________________________________|
*/

//class CTBasicOps2
#ifndef _BASIC_OP_H
#define _BASIC_OP_H


#define CT_USE_CLASS

#ifdef CT_USE_CLASS

#ifndef MAX_32

#define MAX_32 (Word32)0x7fffffffL
#define MIN_32 (Word32)0x80000000L

#define MAX_16 (Word16)0x7fff
#define MIN_16 (Word16)0x8000
#endif

#if (WMOPS)
#include "count.h"
extern BASIC_OP multiCounter[MAXCOUNTERS];
extern int currCounter;

#endif

#include "basicop2.inl"


#else
#define T_EXTERN extern
#define T_STATIC    

T_EXTERN Flag Overflow;
T_EXTERN Flag Carry;



/*___________________________________________________________________________
 |                                                                           |
 |   Prototypes for basic arithmetic operators                               |
 |___________________________________________________________________________|
*/

T_STATIC Word16 add (Word16 var1, Word16 var2);    /* Short add,           1   */
T_STATIC Word16 sub (Word16 var1, Word16 var2);    /* Short sub,           1   */
T_STATIC Word16 abs_s (Word16 var1);               /* Short abs,           1   */
T_STATIC Word16 shl (Word16 var1, Word16 var2);    /* Short shift left,    1   */
T_STATIC Word16 shr (Word16 var1, Word16 var2);    /* Short shift right,   1   */
T_STATIC Word16 mult (Word16 var1, Word16 var2);   /* Short mult,          1   */
T_STATIC Word32 L_mult (Word16 var1, Word16 var2); /* Long mult,           1   */
T_STATIC Word16 negate (Word16 var1);              /* Short negate,        1   */
T_STATIC Word16 extract_h (Word32 L_var1);         /* Extract high,        1   */
T_STATIC Word16 extract_l (Word32 L_var1);         /* Extract low,         1   */
T_STATIC Word16 round (Word32 L_var1);             /* Round,               1   */
T_STATIC Word32 L_mac (Word32 L_var3, Word16 var1, Word16 var2);   /* Mac,  1  */
T_STATIC Word32 L_msu (Word32 L_var3, Word16 var1, Word16 var2);   /* Msu,  1  */
T_STATIC Word32 L_macNs (Word32 L_var3, Word16 var1, Word16 var2); /* Mac without
                                                             sat, 1   */
T_STATIC Word32 L_msuNs (Word32 L_var3, Word16 var1, Word16 var2); /* Msu without
                                                             sat, 1   */
T_STATIC Word32 L_add (Word32 L_var1, Word32 L_var2);    /* Long add,        2 */
T_STATIC Word32 L_sub (Word32 L_var1, Word32 L_var2);    /* Long sub,        2 */
T_STATIC Word32 L_add_c (Word32 L_var1, Word32 L_var2);  /* Long add with c, 2 */
T_STATIC Word32 L_sub_c (Word32 L_var1, Word32 L_var2);  /* Long sub with c, 2 */
T_STATIC Word32 L_negate (Word32 L_var1);                /* Long negate,     2 */
T_STATIC Word16 mult_r (Word16 var1, Word16 var2);       /* Mult with round, 2 */
T_STATIC Word32 L_shl (Word32 L_var1, Word16 var2);      /* Long shift left, 2 */
T_STATIC Word32 L_shr (Word32 L_var1, Word16 var2);      /* Long shift right, 2*/
T_STATIC Word16 shr_r (Word16 var1, Word16 var2);        /* Shift right with
                                                   round, 2           */
T_STATIC Word16 mac_r (Word32 L_var3, Word16 var1, Word16 var2); /* Mac with
                                                           rounding,2 */
T_STATIC Word16 msu_r (Word32 L_var3, Word16 var1, Word16 var2); /* Msu with
                                                           rounding,2 */
T_STATIC Word32 L_deposit_h (Word16 var1);        /* 16 bit var1 -> MSB,     2 */
T_STATIC Word32 L_deposit_l (Word16 var1);        /* 16 bit var1 -> LSB,     2 */

T_STATIC Word32 L_shr_r (Word32 L_var1, Word16 var2); /* Long shift right with
                                                round,  3             */
Word32 L_abs (Word32 L_var1);            /* Long abs,              3  */
Word32 L_sat (Word32 L_var1);            /* Long saturation,       4  */
Word16 norm_s (Word16 var1);             /* Short norm,           15  */
Word16 div_s (Word16 var1, Word16 var2); /* Short division,       18  */
Word16 norm_l (Word32 L_var1);           /* Long norm,            30  */   
#endif
#endif
//};
