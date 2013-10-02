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

//----------
static const unsigned char t_00_enc[]={
     0,    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,
        1,    16,    15,     3,     2,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,
};
//----------
static const unsigned char t_00_dec[]={
     0,    16,    20,    19,    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    18,
       17,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,
};
//----------
static const unsigned char t_01_enc[]={
     1,    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,
        0,    16,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     2,     5,     4,     3,
};
//----------
static const unsigned char t_01_dec[]={
    16,     0,    28,    31,    30,    29,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,
       17,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,
};
//----------
static const unsigned char t_02_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_02_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_03_enc[]={
    31,    30,     0,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,
       16,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,
};
//----------
static const unsigned char t_03_dec[]={
     2,    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,
       16,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     1,     0,
};
//----------
static const unsigned char t_04_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_04_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_05_enc[]={
     1,    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,
        0,    16,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,
};
//----------
static const unsigned char t_05_dec[]={
    16,     0,    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,
       17,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,
};
//----------
static const unsigned char t_06_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_06_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_07_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
        0,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,
};
//----------
static const unsigned char t_07_dec[]={
    16,    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_08_enc[]={
     2,    19,    18,    24,    16,    25,    31,    29,    17,    30,    23,    28,    21,    26,    27,    15,
        1,    13,    14,     9,    12,    10,    20,     6,    11,    22,     7,     5,     8,     3,     4,     0,
};
//----------
static const unsigned char t_08_dec[]={
    31,    16,     0,    29,    30,    27,    23,    26,    28,    19,    21,    24,    20,    17,    18,    15,
        4,     8,     2,     1,    22,    12,    25,    10,     3,     5,    13,    14,    11,     7,     9,     6,
};
//----------
static const unsigned char t_09_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_09_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_10_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_10_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_11_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_11_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_12_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_12_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_13_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_13_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_14_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_14_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_15_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_15_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_16_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_16_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_17_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_17_dec[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
//----------
static const unsigned char t_18_enc[]={
     1,    19,    21,    22,    18,    31,    30,    29,    17,    28,    27,    26,    20,    23,    25,    24,
        0,     6,     9,     3,     5,    12,    16,    13,    10,    15,     8,     7,     4,    14,    11,     2,
};
//----------
static const unsigned char t_18_dec[]={
    16,     0,    31,    19,    28,    20,    17,    27,    26,    18,    24,    30,    21,    23,    29,    25,
       22,     8,     4,     1,    12,     2,     3,    13,    15,    14,    11,    10,     9,     7,     6,     5,
};
//----------
static const unsigned char t_19_enc[]={
    31,    30,    29,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,    16,
        2,    15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     1,     3,     0,
};
//----------
static const unsigned char t_19_dec[]={
    31,    29,    16,    30,    28,    27,    26,    25,    24,    23,    22,    21,    20,    19,    18,    17,
       15,    14,    13,    12,    11,    10,     9,     8,     7,     6,     5,     4,     3,     2,     1,     0,
};
