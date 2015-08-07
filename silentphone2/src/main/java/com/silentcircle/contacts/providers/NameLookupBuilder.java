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

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.NameLookupType;
import com.silentcircle.silentcontacts2.ScContactsContract.FullNameStyle;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Given a full name, constructs all possible variants of the name.
 */
public abstract class NameLookupBuilder {

    private static final int MAX_NAME_TOKENS = 4;

    private final NameSplitter mSplitter;
    private String[][] mNicknameClusters = new String[MAX_NAME_TOKENS][];
    private StringBuilder mStringBuilder = new StringBuilder();
    private String[] mNames = new String[NameSplitter.MAX_TOKENS];

    private static final int[] KOREAN_JAUM_CONVERT_MAP = {
        // JAUM in Hangul Compatibility Jamo area 0x3131 ~ 0x314E to
        // in Hangul Jamo area 0x1100 ~ 0x1112
        0x1100, // 0x3131 HANGUL LETTER KIYEOK
        0x1101, // 0x3132 HANGUL LETTER SSANGKIYEOK
        0x00,   // 0x3133 HANGUL LETTER KIYEOKSIOS (Ignored)
        0x1102, // 0x3134 HANGUL LETTER NIEUN
        0x00,   // 0x3135 HANGUL LETTER NIEUNCIEUC (Ignored)
        0x00,   // 0x3136 HANGUL LETTER NIEUNHIEUH (Ignored)
        0x1103, // 0x3137 HANGUL LETTER TIKEUT
        0x1104, // 0x3138 HANGUL LETTER SSANGTIKEUT
        0x1105, // 0x3139 HANGUL LETTER RIEUL
        0x00,   // 0x313A HANGUL LETTER RIEULKIYEOK (Ignored)
        0x00,   // 0x313B HANGUL LETTER RIEULMIEUM (Ignored)
        0x00,   // 0x313C HANGUL LETTER RIEULPIEUP (Ignored)
        0x00,   // 0x313D HANGUL LETTER RIEULSIOS (Ignored)
        0x00,   // 0x313E HANGUL LETTER RIEULTHIEUTH (Ignored)
        0x00,   // 0x313F HANGUL LETTER RIEULPHIEUPH (Ignored)
        0x00,   // 0x3140 HANGUL LETTER RIEULHIEUH (Ignored)
        0x1106, // 0x3141 HANGUL LETTER MIEUM
        0x1107, // 0x3142 HANGUL LETTER PIEUP
        0x1108, // 0x3143 HANGUL LETTER SSANGPIEUP
        0x00,   // 0x3144 HANGUL LETTER PIEUPSIOS (Ignored)
        0x1109, // 0x3145 HANGUL LETTER SIOS
        0x110A, // 0x3146 HANGUL LETTER SSANGSIOS
        0x110B, // 0x3147 HANGUL LETTER IEUNG
        0x110C, // 0x3148 HANGUL LETTER CIEUC
        0x110D, // 0x3149 HANGUL LETTER SSANGCIEUC
        0x110E, // 0x314A HANGUL LETTER CHIEUCH
        0x110F, // 0x314B HANGUL LETTER KHIEUKH
        0x1110, // 0x314C HANGUL LETTER THIEUTH
        0x1111, // 0x314D HANGUL LETTER PHIEUPH
        0x1112  // 0x314E HANGUL LETTER HIEUH
    };

    public NameLookupBuilder(NameSplitter splitter) {
        mSplitter = splitter;
    }

    /**
     * Inserts a name lookup record with the supplied column values.
     */
    protected abstract void insertNameLookup(long rawContactId, long dataId, int lookupType,
            String string);

    /**
     * Returns common nickname cluster IDs for a given name. For example, it
     * will return the same value for "Robert", "Bob" and "Rob". Some names belong to multiple
     * clusters, e.g. Leo could be Leonard or Leopold.
     *
     * May return null.
     *
     * @param normalizedName A normalized first name, see {@link NameNormalizer#normalize}.
     */
    protected abstract String[] getCommonNicknameClusters(String normalizedName);

    /**
     * Inserts name lookup records for the given structured name.
     */
    public void insertNameLookup(long rawContactId, long dataId, String name, int fullNameStyle) {
        int tokenCount = mSplitter.tokenize(mNames, name);
        if (tokenCount == 0) {
            return;
        }

        for (int i = 0; i < tokenCount; i++) {
            mNames[i] = normalizeName(mNames[i]);
        }

        boolean tooManyTokens = tokenCount > MAX_NAME_TOKENS;
        if (tooManyTokens) {
            insertNameVariant(rawContactId, dataId, tokenCount, NameLookupType.NAME_EXACT, true);

            // Favor longer parts of the name
            Arrays.sort(mNames, 0, tokenCount, new Comparator<String>() {

                public int compare(String s1, String s2) {
                    return s2.length() - s1.length();
                }
            });

            // Insert a collation key for each extra word - useful for contact filtering
            // and suggestions
            String firstToken = mNames[0];
            for (int i = MAX_NAME_TOKENS; i < tokenCount; i++) {
                mNames[0] = mNames[i];
                insertCollationKey(rawContactId, dataId, MAX_NAME_TOKENS);
            }
            mNames[0] = firstToken;

            tokenCount = MAX_NAME_TOKENS;
        }

        // Phase I: insert all variants not involving nickname clusters
        for (int i = 0; i < tokenCount; i++) {
            mNicknameClusters[i] = getCommonNicknameClusters(mNames[i]);
        }

        insertNameVariants(rawContactId, dataId, 0, tokenCount, !tooManyTokens, true);
        insertNicknamePermutations(rawContactId, dataId, 0, tokenCount);
    }

    public void appendToSearchIndex(SearchIndexManager.IndexBuilder builder, String name, int fullNameStyle) {
        int tokenCount = mSplitter.tokenize(mNames, name);
        if (tokenCount == 0) {
            return;
        }

        for (int i = 0; i < tokenCount; i++) {
            builder.appendName(mNames[i]);
        }

        appendNameShorthandLookup(builder, name, fullNameStyle);
        appendNameLookupForLocaleBasedName(builder, name, fullNameStyle);
    }

    /**
     * Insert more name indexes according to locale specifies.
     */
    private void appendNameLookupForLocaleBasedName(SearchIndexManager.IndexBuilder builder,
            String fullName, int fullNameStyle) {
        if (fullNameStyle == FullNameStyle.KOREAN) {
            NameSplitter.Name name = new NameSplitter.Name();
            mSplitter.split(name, fullName, fullNameStyle);
            if (name.givenNames != null) {
                builder.appendName(name.givenNames);
                appendKoreanNameConsonantsLookup(builder, name.givenNames);
            }
            appendKoreanNameConsonantsLookup(builder, fullName);
        }
    }

    /**
     * Inserts Korean lead consonants records of name for the given structured name.
     */
    private void appendKoreanNameConsonantsLookup(SearchIndexManager.IndexBuilder builder, String name) {
        int position = 0;
        int consonantLength = 0;
        int character;

        final int stringLength = name.length();
        mStringBuilder.setLength(0);
        do {
            character = name.codePointAt(position++);
            if ((character == 0x20) || (character == 0x2c) || (character == 0x2E)) {
                // Skip spaces, commas and periods.
                continue;
            }
            // Exclude characters that are not in Korean leading consonants area
            // and Korean characters area.
            if ((character < 0x1100) || (character > 0x1112 && character < 0x3131) ||
                    (character > 0x314E && character < 0xAC00) ||
                    (character > 0xD7A3)) {
                break;
            }
            // Decompose and take a only lead-consonant for composed Korean characters.
            if (character >= 0xAC00) {
                // Lead consonant = "Lead consonant base" +
                //      (character - "Korean Character base") /
                //          ("Lead consonant count" * "middle Vowel count")
                character = 0x1100 + (character - 0xAC00) / 588;
            } else if (character >= 0x3131) {
                // Hangul Compatibility Jamo area 0x3131 ~ 0x314E :
                // Convert to Hangul Jamo area 0x1100 ~ 0x1112
                if (character - 0x3131 >= KOREAN_JAUM_CONVERT_MAP.length) {
                    // This is not lead-consonant
                    break;
                }
                character = KOREAN_JAUM_CONVERT_MAP[character - 0x3131];
                if (character == 0) {
                    // This is not lead-consonant
                    break;
                }
            }
            mStringBuilder.appendCodePoint(character);
            consonantLength++;
        } while (position < stringLength);

        // At least, insert consonants when Korean characters are two or more.
        // Only one character cases are covered by NAME_COLLATION_KEY
        if (consonantLength > 1) {
            builder.appendName(mStringBuilder.toString());
        }
    }

    protected String normalizeName(String name) {
        return NameNormalizer.normalize(name);
    }

    /**
     * Inserts all name variants based on permutations of tokens between
     * fromIndex and toIndex
     *
     * @param initiallyExact true if the name without permutations is the exact
     *            original name
     * @param buildCollationKey true if a collation key makes sense for these
     *            permutations (false if at least one of the tokens is a
     *            nickname cluster key)
     */
    private void insertNameVariants(long rawContactId, long dataId, int fromIndex, int toIndex,
            boolean initiallyExact, boolean buildCollationKey) {
        if (fromIndex == toIndex) {
            insertNameVariant(rawContactId, dataId, toIndex,
                    initiallyExact ? NameLookupType.NAME_EXACT : NameLookupType.NAME_VARIANT,
                    buildCollationKey);
            return;
        }

        // Swap the first token with each other token (including itself, which is a no-op)
        // and recursively insert all permutations for the remaining tokens
        String firstToken = mNames[fromIndex];
        for (int i = fromIndex; i < toIndex; i++) {
            mNames[fromIndex] = mNames[i];
            mNames[i] = firstToken;

            insertNameVariants(rawContactId, dataId, fromIndex + 1, toIndex,
                    initiallyExact && i == fromIndex, buildCollationKey);

            mNames[i] = mNames[fromIndex];
            mNames[fromIndex] = firstToken;
        }
    }

    /**
     * Inserts a single name variant and optionally its collation key counterpart.
     */
    private void insertNameVariant(long rawContactId, long dataId, int tokenCount,
            int lookupType, boolean buildCollationKey) {
        mStringBuilder.setLength(0);

        for (int i = 0; i < tokenCount; i++) {
            if (i != 0) {
                mStringBuilder.append('.');
            }
            mStringBuilder.append(mNames[i]);
        }

        insertNameLookup(rawContactId, dataId, lookupType, mStringBuilder.toString());

        if (buildCollationKey) {
            insertCollationKey(rawContactId, dataId, tokenCount);
        }
    }

    /**
     * Inserts a collation key for the current contents of {@link #mNames}.
     */
    private void insertCollationKey(long rawContactId, long dataId, int tokenCount) {
        mStringBuilder.setLength(0);

        for (int i = 0; i < tokenCount; i++) {
            mStringBuilder.append(mNames[i]);
        }

        insertNameLookup(rawContactId, dataId, NameLookupType.NAME_COLLATION_KEY,
                mStringBuilder.toString());
    }

    /**
     * For all tokens that correspond to nickname clusters, substitutes each cluster key
     * and inserts all permutations with that key.
     */
    private void insertNicknamePermutations(long rawContactId, long dataId, int fromIndex,
            int tokenCount) {
        for (int i = fromIndex; i < tokenCount; i++) {
            String[] clusters = mNicknameClusters[i];
            if (clusters != null) {
                String token = mNames[i];
                for (int j = 0; j < clusters.length; j++) {
                    mNames[i] = clusters[j];

                    // Insert all permutations with this nickname cluster
                    insertNameVariants(rawContactId, dataId, 0, tokenCount, false, false);

                    // Repeat recursively for other nickname clusters
                    insertNicknamePermutations(rawContactId, dataId, i + 1, tokenCount);
                }
                mNames[i] = token;
            }
        }
    }

    private void appendNameShorthandLookup(SearchIndexManager.IndexBuilder builder, String name, int fullNameStyle) {
        Iterator<String> it =
                ContactLocaleUtils43.getInstance().getNameLookupKeys(name, fullNameStyle);
        if (it != null) {
            while (it.hasNext()) {
                builder.appendName(it.next());
            }
        }
    }
}
