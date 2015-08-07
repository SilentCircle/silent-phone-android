/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.silentphone2.dialpad;

/**
 * Note: These methods currently take characters as arguments. For future planned language support,
 * they will need to be changed to use codepoints instead of characters.
 *
 * http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#codePointAt(int)
 *
 * If/when this change is made, LatinSmartDialMap(which operates on chars) will continue to work
 * by simply casting from a codepoint to a character.
 */
public interface SmartDialMap {
    /*
     * Returns true if the provided character can be mapped to a key on the dialpad
     */
    public boolean isValidDialpadCharacter(char ch);

    /*
     * Returns true if the provided character is a letter, and can be mapped to a key on the dialpad
     */
    public boolean isValidDialpadAlphabeticChar(char ch);

    /*
     * Returns true if the provided character is a digit, and can be mapped to a key on the dialpad
     */
    public boolean isValidDialpadNumericChar(char ch);

    /*
     * Get the index of the key on the dialpad which the character corresponds to
     */
    public byte getDialpadIndex(char ch);

    /*
     * Get the actual numeric character on the dialpad which the character corresponds to
     */
    public char getDialpadNumericCharacter(char ch);

    /*
     * Converts uppercase characters to lower case ones, and on a best effort basis, strips accents
     * from accented characters.
     */
    public char normalizeCharacter(char ch);
}
