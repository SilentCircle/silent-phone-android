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
 * </p>
 * vCard parser for vCard 2.1. See the specification for more detail about the spec itself.
 * </p>
 * <p>
 * The spec is written in 1996, and currently various types of "vCard 2.1" exist.
 * To handle real the world vCard formats appropriately and effectively, this class does not
 * obey with strict vCard 2.1.
 * In stead, not only vCard spec but also real world vCard is considered.
 * </p>
 * e.g. A lot of devices and softwares let vCard importer/exporter to use
 * the PNG format to determine the type of image, while it is not allowed in
 * the original specification. As of 2010, we can see even the FLV format
 * (possible in Japanese mobile phones).
 * </p>
 */
public final class VCardParser_V21 extends VCardParser {
    /**
     * A unmodifiable Set storing the property names available in the vCard 2.1 specification.
     */
    static final Set<String> sKnownPropertyNameSet =
            Collections.unmodifiableSet(new HashSet<String>(
                    Arrays.asList("BEGIN", "END", "LOGO", "PHOTO", "LABEL", "FN", "TITLE", "SOUND",
                            "VERSION", "TEL", "EMAIL", "TZ", "GEO", "NOTE", "URL",
                            "BDAY", "ROLE", "REV", "UID", "KEY", "MAILER")));

    /**
     * A unmodifiable Set storing the types known in vCard 2.1.
     */
    static final Set<String> sKnownTypeSet =
            Collections.unmodifiableSet(new HashSet<String>(
                    Arrays.asList("DOM", "INTL", "POSTAL", "PARCEL", "HOME", "WORK",
                            "PREF", "VOICE", "FAX", "MSG", "CELL", "PAGER", "BBS",
                            "MODEM", "CAR", "ISDN", "VIDEO", "AOL", "APPLELINK",
                            "ATTMAIL", "CIS", "EWORLD", "INTERNET", "IBMMAIL",
                            "MCIMAIL", "POWERSHARE", "PRODIGY", "TLX", "X400", "GIF",
                            "CGM", "WMF", "BMP", "MET", "PMB", "DIB", "PICT", "TIFF",
                            "PDF", "PS", "JPEG", "QTIME", "MPEG", "MPEG2", "AVI",
                            "WAVE", "AIFF", "PCM", "X509", "PGP")));

    /**
     * A unmodifiable Set storing the values for the type "VALUE", available in the vCard 2.1.
     */
    static final Set<String> sKnownValueSet =
            Collections.unmodifiableSet(new HashSet<String>(
                    Arrays.asList("INLINE", "URL", "CONTENT-ID", "CID")));

    /**
     * <p>
     * A unmodifiable Set storing the values for the type "ENCODING", available in the vCard 2.1.
     * </p>
     * <p>
     * Though vCard 2.1 specification does not allow "B" encoding, some data may have it.
     * We allow it for safety.
     * </p>
     */
    static final Set<String> sAvailableEncoding =
        Collections.unmodifiableSet(new HashSet<String>(
                Arrays.asList(VCardConstants.PARAM_ENCODING_7BIT,
                        VCardConstants.PARAM_ENCODING_8BIT,
                        VCardConstants.PARAM_ENCODING_QP,
                        VCardConstants.PARAM_ENCODING_BASE64,
                        VCardConstants.PARAM_ENCODING_B)));

    private final VCardParserImpl_V21 mVCardParserImpl;

    public VCardParser_V21() {
        mVCardParserImpl = new VCardParserImpl_V21();
    }

    public VCardParser_V21(int vcardType) {
        mVCardParserImpl = new VCardParserImpl_V21(vcardType);
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
