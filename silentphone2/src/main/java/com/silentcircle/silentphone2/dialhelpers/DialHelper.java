/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone2.dialhelpers;

import android.widget.EditText;

/**
 * All dial helper implementation must implement this interface.
 *
 * Created by werner on 22.06.14.
 */
public interface DialHelper {

    /**
     * Resets the helper to a defined state.
     */
    void resetAnalyser();

    /**
     * Analyse a number string and return a modified string.
     *
     * Analyse the number string according to the rules and perform modifications if
     * necessary. If the function performs modifications it returns {@code true} and the
     * {@code out} parameter contains the modified string. Otherwise it returns {@code false}
     * and does not store anything in {@code out}.
     *
     * @param in the number string to analyse
     * @param out an empty StringBuilder where the function stores a modified number string
     * @return {@code true} if functions created a modified number, {@code false} otherwise
     */
    boolean analyseModifyNumberString(String in, StringBuilder out);

    /**
     * Analyse characters as they are typed and modify the EditText field.
     *
     * The function gets each typed (dialled) character, analyses it and may modify the the
     * EditText according to the current state.
     *
     * The default action is to send the typed character to the EditText field. Depending
     * on the state the functions may replace characters or add additional characters to
     * the EditText field.
     *
     * @param in the typed character
     * @param field the EditText field
     * @return {@code true} if the function added or replaced characters, {@code false} if it just
     *         sent the character to the EditText field (default handling).
     */
    boolean analyseCharModifyEditText(int in, EditText field);

    /**
     * Get the string resource id of the helper's explanation string.
     * @return string resource id
     */
    int getExplanation();
}
