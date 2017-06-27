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
 * Copyright (C) 2010 The Android Open Source Project
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 * vCard parser for vCard 4.0. DO NOT USE IN PRODUCTION.
 * </p>
 * <p>
 * Currently this parser is based on vCard 4.0 specification rev 15 (partly).
 * Note that some of current implementation lack basic capability required in vCard 4.0.
 * (e.g. PHOTO has data parameter in rev 15 while this implementation requires "ENCODING=b")
 * </p>
 */
public class VCardParser_V40 extends VCardParser {
    static final Set<String> sKnownPropertyNameSet =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    "BEGIN", "END", "VERSION",
                    "SOURCE", "KIND", "FN", "N", "NICKNAME",
                    "PHOTO", "BDAY", "ANNIVERSARY", "GENDER", "ADR", "TEL",
                    "EMAIL", "IMPP", "LANG", "TZ", "GEO", "TITLE", "ROLE",
                    "LOGO", "ORG", "MEMBER", "RELATED", "CATEGORIES",
                    "NOTE", "PRODID", "REV", "SOUND", "UID", "CLIENTPIDMAP",
                    "URL", "KEY", "FBURL", "CALENDRURI", "CALURI", "XML")));

    /**
     * <p>
     * A unmodifiable Set storing the values for the type "ENCODING", available in vCard 4.0.
     * </p>
     */
    static final Set<String> sAcceptableEncoding =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    VCardConstants.PARAM_ENCODING_8BIT,
                    VCardConstants.PARAM_ENCODING_B)));

    private final VCardParserImpl_V40 mVCardParserImpl;

    public VCardParser_V40() {
        mVCardParserImpl = new VCardParserImpl_V40();
    }

    public VCardParser_V40(int vcardType) {
        mVCardParserImpl = new VCardParserImpl_V40(vcardType);
    }

    @Override
    public void addInterpreter(VCardInterpreter interpreter) {
        mVCardParserImpl.addInterpreter(interpreter);
    }

    @Override
    public void parse(InputStream is) throws IOException, VCardException {
        mVCardParserImpl.parse(is);
    }

    @Override
    public void parseOne(InputStream is) throws IOException, VCardException {
        mVCardParserImpl.parseOne(is);
    }

    @Override
    public void cancel() {
        mVCardParserImpl.cancel();
    }
}
