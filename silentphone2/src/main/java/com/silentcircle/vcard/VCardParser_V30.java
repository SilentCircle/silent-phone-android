/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 * vCard parser for vCard 3.0. See RFC 2426 for more detail.
 * </p>
 * <p>
 * This parser allows vCard format which is not allowed in the RFC, since
 * we have seen several vCard 3.0 files which don't comply with it.
 * </p>
 * <p>
 * e.g. vCard 3.0 does not allow "CHARSET" attribute, but some actual files
 * have it and they uses non UTF-8 charsets. UTF-8 is recommended in RFC 2426,
 * but it is not a must. We silently allow "CHARSET".
 * </p>
 */
public class VCardParser_V30 extends VCardParser {
    static final Set<String> sKnownPropertyNameSet =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    "BEGIN", "END", "LOGO", "PHOTO", "LABEL", "FN", "TITLE", "SOUND",
                    "VERSION", "TEL", "EMAIL", "TZ", "GEO", "NOTE", "URL",
                    "BDAY", "ROLE", "REV", "UID", "KEY", "MAILER", // 2.1
                    "NAME", "PROFILE", "SOURCE", "NICKNAME", "CLASS",
                    "SORT-STRING", "CATEGORIES", "PRODID",  // 3.0
                    "IMPP"))); // RFC 4770

    /**
     * <p>
     * A unmodifiable Set storing the values for the type "ENCODING", available in the vCard 3.0.
     * </p>
     * <p>
     * Though vCard 2.1 specification does not allow "7BIT" or "BASE64", we allow them for safety.
     * </p>
     * <p>
     * "QUOTED-PRINTABLE" is not allowed in vCard 3.0 and not in this parser either,
     * because the encoding ambiguates how the vCard file to be parsed.
     * </p>
     */
    static final Set<String> sAcceptableEncoding =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    VCardConstants.PARAM_ENCODING_7BIT,
                    VCardConstants.PARAM_ENCODING_8BIT,
                    VCardConstants.PARAM_ENCODING_BASE64,
                    VCardConstants.PARAM_ENCODING_B)));

    private final VCardParserImpl_V30 mVCardParserImpl;

    public VCardParser_V30() {
        mVCardParserImpl = new VCardParserImpl_V30();
    }

    public VCardParser_V30(int vcardType) {
        mVCardParserImpl = new VCardParserImpl_V30(vcardType);
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
