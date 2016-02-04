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
package com.silentcircle.contacts.providers;

import com.silentcircle.contacts.utils.Hex;

import java.text.CollationKey;
import java.text.Collator;
import java.text.RuleBasedCollator;
import java.util.Locale;

/**
 * Converts a name to a normalized form by removing all non-letter characters and normalizing
 * UNICODE according to http://unicode.org/unicode/reports/tr15
 */
public class NameNormalizer {

    private static final Object sCollatorLock = new Object();

    private static Locale sCollatorLocale;

    private static RuleBasedCollator sCachedCompressingCollator;
    private static RuleBasedCollator sCachedComplexityCollator;

    /**
     * Ensure that the cached collators are for the current locale.
     */
    private static void ensureCollators() {
        final Locale locale = Locale.getDefault();
        if (locale.equals(sCollatorLocale)) {
            return;
        }
        sCollatorLocale = locale;

        sCachedCompressingCollator = (RuleBasedCollator) Collator.getInstance(locale);
        sCachedCompressingCollator.setStrength(Collator.PRIMARY);
        sCachedCompressingCollator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);

        sCachedComplexityCollator = (RuleBasedCollator) Collator.getInstance(locale);
        sCachedComplexityCollator.setStrength(Collator.SECONDARY);
    }

// TODO    @VisibleForTesting
    static RuleBasedCollator getCompressingCollator() {
        synchronized (sCollatorLock) {
            ensureCollators();
            return sCachedCompressingCollator;
        }
    }

//    @VisibleForTesting
    static RuleBasedCollator getComplexityCollator() {
        synchronized (sCollatorLock) {
            ensureCollators();
            return sCachedComplexityCollator;
        }
    }

    /**
     * Converts the supplied name to a string that can be used to perform approximate matching
     * of names.  It ignores non-letter, non-digit characters, and removes accents.
     */
    public static String normalize(String name) {
        CollationKey key = getCompressingCollator().getCollationKey(lettersAndDigitsOnly(name));
        return Hex.encodeHex(key.toByteArray(), true);
    }

    /**
     * Compares "complexity" of two names, which is determined by the presence
     * of mixed case characters, accents and, if all else is equal, length.
     */
    public static int compareComplexity(String name1, String name2) {
        String clean1 = lettersAndDigitsOnly(name1);
        String clean2 = lettersAndDigitsOnly(name2);
        int diff = getComplexityCollator().compare(clean1, clean2);
        if (diff != 0) {
            return diff;
        }
        // compareTo sorts uppercase first. We know that there are no non-case
        // differences from the above test, so we can negate here to get the
        // lowercase-first comparison we really want...
        diff = -clean1.compareTo(clean2);
        if (diff != 0) {
            return diff;
        }
        return name1.length() - name2.length();
    }

    /**
     * Returns a string containing just the letters and digits from the original string.
     */
    private static String lettersAndDigitsOnly(String name) {
        char[] letters = name.toCharArray();
        int length = 0;
        for (int i = 0; i < letters.length; i++) {
            final char c = letters[i];
            if (Character.isLetterOrDigit(c)) {
                letters[length++] = c;
            }
        }
        if (length != letters.length) {
            return new String(letters, 0, length);
        }

        return name;
    }
}
