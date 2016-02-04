/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.silentcircle.contacts.utils;

/**
 * Basic hex operations: from byte array to string and vice versa.
 *
 * TODO: move to the framework and consider implementing as native code.
 */
public class Hex {

    private static final char[] HEX_DIGITS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final char[] FIRST_CHAR = new char[256];
    private static final char[] SECOND_CHAR = new char[256];
    static {
        for (int i = 0; i < 256; i++) {
            FIRST_CHAR[i] = HEX_DIGITS[(i >> 4) & 0xF];
            SECOND_CHAR[i] = HEX_DIGITS[i & 0xF];
        }
    }

    private static final byte[] DIGITS = new byte['f'+1];
    static {
        for (int i = 0; i <= 'F'; i++) {
            DIGITS[i] = -1;
        }
        for (byte i = 0; i < 10; i++) {
            DIGITS['0' + i] = i;
        }
        for (byte i = 0; i < 6; i++) {
            DIGITS['A' + i] = (byte)(10 + i);
            DIGITS['a' + i] = (byte)(10 + i);
        }
    }

    /**
     * Quickly converts a byte array to a hexadecimal string representation.
     *
     * @param array byte array, possibly zero-terminated.
     */
    public static String encodeHex(byte[] array, boolean zeroTerminated) {
        char[] cArray = new char[array.length * 2];

        int j = 0;
        for (int i = 0; i < array.length; i++) {
            int index = array[i] & 0xFF;
            if (zeroTerminated && index == 0 && i == array.length-1) {
                break;
            }

            cArray[j++] = FIRST_CHAR[index];
            cArray[j++] = SECOND_CHAR[index];
        }

        return new String(cArray, 0, j);
    }

    /**
     * Quickly converts a hexadecimal string to a byte array.
     *
     * TODO Use checked exceptions instead of RuntimeException.  Apparently normalized names *may*
     * contain non-hex strings and we want to make sure the provider won't crash even with such
     * input.
     */
    public static byte[] decodeHex(String hexString) {
        int length = hexString.length();

        if ((length & 0x01) != 0) {
            throw new IllegalArgumentException("Odd number of characters: " + hexString);
        }

        boolean badHex = false;
        byte[] out = new byte[length >> 1];
        for (int i = 0, j = 0; j < length; i++) {
            int c1 = hexString.charAt(j++);
            if (c1 > 'f') {
                badHex = true;
                break;
            }

            final byte d1 = DIGITS[c1];
            if (d1 == -1) {
                badHex = true;
                break;
            }

            int c2 = hexString.charAt(j++);
            if (c2 > 'f') {
                badHex = true;
                break;
            }

            final byte d2 = DIGITS[c2];
            if (d2 == -1) {
                badHex = true;
                break;
            }

            out[i] = (byte) (d1 << 4 | d2);
        }

        if (badHex) {
            throw new IllegalArgumentException("Invalid hexadecimal digit: " + hexString);
        }

        return out;
    }
}
