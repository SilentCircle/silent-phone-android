/*
Copyright (C) 2013-2017, Silent Circle, LLC.  All rights reserved.

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
 * This  implementation is an edited version of original Android sources.
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
 * limitations under the License.
 */
package com.silentcircle.vcard;

import com.silentcircle.vcard.exception.VCardException;

import java.io.IOException;
import java.io.InputStream;

public abstract class VCardParser {

    /**
     * Registers one {@link VCardInterpreter} instance, which receives events along with
     * vCard parsing.
     *
     * @param interpreter
     */
    public abstract void addInterpreter(VCardInterpreter interpreter);

    /**
     * <p>Parses a whole InputStream as a vCard file and lets registered {@link VCardInterpreter}
     * instances handle callbacks.</p>
     *
     * <p>This method reads a whole InputStream. If you just want to parse one vCard entry inside
     * a vCard file with multiple entries, try {@link #parseOne(java.io.InputStream)}.</p>
     *
     * @param is The source to parse.
     * @throws java.io.IOException, VCardException
     */
    public abstract void parse(InputStream is) throws IOException, VCardException;

    /**
     * <p>Parses the first vCard entry in InputStream and lets registered {@link VCardInterpreter}
     * instances handle callbacks.</p>
     *
     * <p>This method finishes itself when the first entry ended.</p>
     *
     * <p>Note that, registered {@link VCardInterpreter} may still see multiple
     * {@link VCardInterpreter#onEntryStarted()} / {@link VCardInterpreter#onEntryEnded()} calls
     * even with this method.</p>
     *
     * <p>This happens when the first entry contains nested vCards, which is allowed in vCard 2.1.
     * See the following example.</p>
     *
     * <code>
     * BEGIN:VCARD
     * N:a
     * BEGIN:VCARD
     * N:b
     * END:VCARD
     * END:VCARD
     * </code>
     *
     * <p>With this vCard, registered interpreters will grab two
     * {@link VCardInterpreter#onEntryStarted()} and {@link VCardInterpreter#onEntryEnded()}
     * calls. Callers should handle the situation by themselves.</p>
     *
     * @param is  The source to parse.
     * @throws java.io.IOException, VCardException
     */
    public abstract void parseOne(InputStream is) throws IOException, VCardException;

    /**
     * <p>
     * Cancel parsing vCard. Useful when you want to stop the parse in the other threads.
     * </p>
     * <p>
     * Actual cancel is done after parsing the current vcard.
     * </p>
     */
    public abstract void cancel();
}
