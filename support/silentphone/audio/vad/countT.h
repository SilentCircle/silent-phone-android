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

/*
 * functions for counting operations
 *
 * These functions, and the ones in basic_op.h, makes it possible to measure
 * the wMOPS of a codec.
 *
 * All functions in this file, and in basic_op.h, uppdates a structure so that
 * it will be possible the see how many calls to add, mul mulAdd ... that the
 * code made, and estimate the wMOPS (and MIPS) for a sertain part of code
 *
 * It is also possible to measure the wMOPS separatly for different parts
 * of the codec.
 *
 * This is done by creating a counter group (getCounterId) for each part of the
 * code that one wants a separte measure for. Before a part of the code
 * is executed a call to the "setCounter" function is needed to identify
 * which counter group to use.
 *
 * Currently there is a limit of 255 different counter groups.
 *
 * In the end of this file there is a pice of code illustration how the
 * functions can be used.
 */
#ifndef count_h
#define count_h "$Id $"


static void move16 (void){}
static void move32 (void){}
static void logic16 (void){}
static void logic32 (void){}
static void test (void){}

#endif
