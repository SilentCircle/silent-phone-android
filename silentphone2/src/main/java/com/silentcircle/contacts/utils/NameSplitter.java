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

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.FullNameStyle;
import android.provider.ContactsContract.PhoneticNameStyle;
import android.text.TextUtils;

import java.lang.Character.UnicodeBlock;
import java.util.HashSet;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * The purpose of this class is to split a full name into given names and last
 * name. The logic only supports having a single last name. If the full name has
 * multiple last names the output will be incorrect.
 * <p>
 * Core algorithm:
 * <ol>
 * <li>Remove the suffixes (III, Ph.D., M.D.).</li>
 * <li>Remove the prefixes (Mr., Pastor, Reverend, Sir).</li>
 * <li>Assign the last remaining token as the last name.</li>
 * <li>If the previous word to the last name is one from LASTNAME_PREFIXES, use
 * this word also as the last name.</li>
 * <li>Assign the rest of the words as the "given names".</li>
 * </ol>
 */
@SuppressLint("DefaultLocale")
public class NameSplitter {

    public static final int MAX_TOKENS = 10;

    private static final String JAPANESE_LANGUAGE = Locale.JAPANESE.getLanguage().toLowerCase();
    private static final String KOREAN_LANGUAGE = Locale.KOREAN.getLanguage().toLowerCase();

    // This includes simplified and traditional Chinese
    private static final String CHINESE_LANGUAGE = Locale.CHINESE.getLanguage().toLowerCase();

    private final HashSet<String> mPrefixesSet;
    private final HashSet<String> mSuffixesSet;
    private final int mMaxSuffixLength;
    private final HashSet<String> mLastNamePrefixesSet;
    private final HashSet<String> mConjuctions;
    private final Locale mLocale;
    private final String mLanguage;

    /**
     * Two-Chracter long Korean family names.
     * http://ko.wikipedia.org/wiki/%ED%95%9C%EA%B5%AD%EC%9D%98_%EB%B3%B5%EC%84%B1
     */
    private static final String[] KOREAN_TWO_CHARCTER_FAMILY_NAMES = {
        "\uAC15\uC804", // Gang Jeon
        "\uB0A8\uAD81", // Nam Goong
        "\uB3C5\uACE0", // Dok Go
        "\uB3D9\uBC29", // Dong Bang
        "\uB9DD\uC808", // Mang Jeol
        "\uC0AC\uACF5", // Sa Gong
        "\uC11C\uBB38", // Seo Moon
        "\uC120\uC6B0", // Seon Woo
        "\uC18C\uBD09", // So Bong
        "\uC5B4\uAE08", // Uh Geum
        "\uC7A5\uACE1", // Jang Gok
        "\uC81C\uAC08", // Je Gal
        "\uD669\uBCF4"  // Hwang Bo
    };

    public static class Name {
        public String prefix;
        public String givenNames;
        public String middleName;
        public String familyName;
        public String suffix;

        public int fullNameStyle;

        public String phoneticFamilyName;
        public String phoneticMiddleName;
        public String phoneticGivenName;

        public int phoneticNameStyle;

        public Name() {
        }

        public Name(String prefix, String givenNames, String middleName, String familyName, String suffix) {
            this.prefix = prefix;
            this.givenNames = givenNames;
            this.middleName = middleName;
            this.familyName = familyName;
            this.suffix = suffix;
        }

        // @NeededForTesting
        public String getPrefix() {
            return prefix;
        }

        public String getGivenNames() {
            return givenNames;
        }

        public String getMiddleName() {
            return middleName;
        }

        public String getFamilyName() {
            return familyName;
        }

        // @NeededForTesting
        public String getSuffix() {
            return suffix;
        }

        public int getFullNameStyle() {
            return fullNameStyle;
        }

        public String getPhoneticFamilyName() {
            return phoneticFamilyName;
        }

        public String getPhoneticMiddleName() {
            return phoneticMiddleName;
        }

        public String getPhoneticGivenName() {
            return phoneticGivenName;
        }

        public int getPhoneticNameStyle() {
            return phoneticNameStyle;
        }

        public void fromValues(ContentValues values) {
            prefix = values.getAsString(StructuredName.PREFIX);
            givenNames = values.getAsString(StructuredName.GIVEN_NAME);
            middleName = values.getAsString(StructuredName.MIDDLE_NAME);
            familyName = values.getAsString(StructuredName.FAMILY_NAME);
            suffix = values.getAsString(StructuredName.SUFFIX);

            Integer integer = values.getAsInteger(StructuredName.FULL_NAME_STYLE);
            fullNameStyle = integer == null ? FullNameStyle.UNDEFINED : integer;

            phoneticFamilyName = values.getAsString(StructuredName.PHONETIC_FAMILY_NAME);
            phoneticMiddleName = values.getAsString(StructuredName.PHONETIC_MIDDLE_NAME);
            phoneticGivenName = values.getAsString(StructuredName.PHONETIC_GIVEN_NAME);

            integer = values.getAsInteger(StructuredName.PHONETIC_NAME_STYLE);
            phoneticNameStyle = integer == null ? PhoneticNameStyle.UNDEFINED : integer;
        }

        public void toValues(ContentValues values) {
            putValueIfPresent(values, StructuredName.PREFIX, prefix);
            putValueIfPresent(values, StructuredName.GIVEN_NAME, givenNames);
            putValueIfPresent(values, StructuredName.MIDDLE_NAME, middleName);
            putValueIfPresent(values, StructuredName.FAMILY_NAME, familyName);
            putValueIfPresent(values, StructuredName.SUFFIX, suffix);
            values.put(StructuredName.FULL_NAME_STYLE, fullNameStyle);
            putValueIfPresent(values, StructuredName.PHONETIC_FAMILY_NAME, phoneticFamilyName);
            putValueIfPresent(values, StructuredName.PHONETIC_MIDDLE_NAME, phoneticMiddleName);
            putValueIfPresent(values, StructuredName.PHONETIC_GIVEN_NAME, phoneticGivenName);
            values.put(StructuredName.PHONETIC_NAME_STYLE, phoneticNameStyle);
        }

        private void putValueIfPresent(ContentValues values, String name, String value) {
            if (value != null) {
                values.put(name, value);
            }
        }

        public void clear() {
            prefix = null;
            givenNames = null;
            middleName = null;
            familyName = null;
            suffix = null;
            fullNameStyle = FullNameStyle.UNDEFINED;
            phoneticFamilyName = null;
            phoneticMiddleName = null;
            phoneticGivenName = null;
            phoneticNameStyle = PhoneticNameStyle.UNDEFINED;
        }

        public boolean isEmpty() {
            return TextUtils.isEmpty(givenNames)
                    && TextUtils.isEmpty(middleName)
                    && TextUtils.isEmpty(familyName)
                    && TextUtils.isEmpty(suffix)
                    && TextUtils.isEmpty(phoneticFamilyName)
                    && TextUtils.isEmpty(phoneticMiddleName)
                    && TextUtils.isEmpty(phoneticGivenName);
        }

        @Override
        public String toString() {
            return "[prefix: " + prefix + " given: " + givenNames + " middle: " + middleName
                    + " family: " + familyName + " suffix: " + suffix + " ph/given: "
                    + phoneticGivenName + " ph/middle: " + phoneticMiddleName + " ph/family: "
                    + phoneticFamilyName + "]";
        }
    }

    private static class NameTokenizer extends StringTokenizer {
        private final String[] mTokens;
        private int mDotBitmask;
        private int mCommaBitmask;
        private int mStartPointer;
        private int mEndPointer;

        public NameTokenizer(String fullName) {
            super(fullName, " .,", true);

            mTokens = new String[MAX_TOKENS];

            // Iterate over tokens, skipping over empty ones and marking tokens that
            // are followed by dots.
            while (hasMoreTokens() && mEndPointer < MAX_TOKENS) {
                final String token = nextToken();
                if (token.length() > 0) {
                    final char c = token.charAt(0);
                    if (c == ' ') {
                        continue;
                    }
                }

                if (mEndPointer > 0 && token.charAt(0) == '.') {
                    mDotBitmask |= (1 << (mEndPointer - 1));
                } else if (mEndPointer > 0 && token.charAt(0) == ',') {
                    mCommaBitmask |= (1 << (mEndPointer - 1));
                } else {
                    mTokens[mEndPointer] = token;
                    mEndPointer++;
                }
            }
        }

        /**
         * Returns true if the token is followed by a dot in the original full name.
         */
        public boolean hasDot(int index) {
            return (mDotBitmask & (1 << index)) != 0;
        }

        /**
         * Returns true if the token is followed by a comma in the original full name.
         */
        public boolean hasComma(int index) {
            return (mCommaBitmask & (1 << index)) != 0;
        }
    }

    /**
     * Constructor.
     *
     * @param commonPrefixes comma-separated list of common prefixes,
     *            e.g. "Mr, Ms, Mrs"
     * @param commonLastNamePrefixes comma-separated list of common last name prefixes,
     *            e.g. "d', st, st., von"
     * @param commonSuffixes comma-separated list of common suffixes,
     *            e.g. "Jr, M.D., MD, D.D.S."
     * @param commonConjunctions comma-separated list of common conjuctions,
     *            e.g. "AND, Or"
     */
    public NameSplitter(String commonPrefixes, String commonLastNamePrefixes, String commonSuffixes, String commonConjunctions,
            Locale locale) {
        // TODO: refactor this to use <string-array> resources
        mPrefixesSet = convertToSet(commonPrefixes);
        mLastNamePrefixesSet = convertToSet(commonLastNamePrefixes);
        mSuffixesSet = convertToSet(commonSuffixes);
        mConjuctions = convertToSet(commonConjunctions);
        mLocale = locale != null ? locale : Locale.getDefault();
        mLanguage = mLocale.getLanguage().toLowerCase();

        int maxLength = 0;
        for (String suffix : mSuffixesSet) {
            if (suffix.length() > maxLength) {
                maxLength = suffix.length();
            }
        }

        mMaxSuffixLength = maxLength;
    }

    /**
     * Converts a comma-separated list of Strings to a set of Strings. Trims strings
     * and converts them to upper case.
     */
    private static HashSet<String> convertToSet(String strings) {
        HashSet<String> set = new HashSet<String>();
        if (strings != null) {
            String[] split = strings.split(",");
            for (int i = 0; i < split.length; i++) {
                set.add(split[i].trim().toUpperCase());
            }
        }
        return set;
    }

    /**
     * Parses a full name and returns components as a list of tokens.
     */
    public int tokenize(String[] tokens, String fullName) {
        if (fullName == null) {
            return 0;
        }

        NameTokenizer tokenizer = new NameTokenizer(fullName);

        if (tokenizer.mStartPointer == tokenizer.mEndPointer) {
            return 0;
        }

        String firstToken = tokenizer.mTokens[tokenizer.mStartPointer];
        if (mPrefixesSet.contains(firstToken.toUpperCase())) {
           tokenizer.mStartPointer++;
        }
        int count = 0;
        for (int i = tokenizer.mStartPointer; i < tokenizer.mEndPointer; i++) {
            tokens[count++] = tokenizer.mTokens[i];
        }

        return count;
    }


    /**
     * Parses a full name and returns parsed components in the Name object.
     */
    public void split(Name name, String fullName) {
        if (fullName == null) {
            return;
        }

        int fullNameStyle = guessFullNameStyle(fullName);
        if (fullNameStyle == FullNameStyle.CJK) {
            fullNameStyle = getAdjustedFullNameStyle(fullNameStyle);
        }

        split(name, fullName, fullNameStyle);
    }

    /**
     * Parses a full name and returns parsed components in the Name object
     * with a given fullNameStyle.
     */
    public void split(Name name, String fullName, int fullNameStyle) {
        if (fullName == null) {
            return;
        }

        name.fullNameStyle = fullNameStyle;

        switch (fullNameStyle) {
            case FullNameStyle.CHINESE:
                splitChineseName(name, fullName);
                break;

            case FullNameStyle.JAPANESE:
                splitJapaneseName(name, fullName);
                break;

            case FullNameStyle.KOREAN:
                splitKoreanName(name, fullName);
                break;

            default:
                splitWesternName(name, fullName);
        }
    }

    /**
     * Splits a full name composed according to the Western tradition:
     * <pre>
     *   [prefix] given name(s) [[middle name] family name] [, suffix]
     *   [prefix] family name, given name [middle name] [,suffix]
     * </pre>
     */
    private void splitWesternName(Name name, String fullName) {
        NameTokenizer tokens = new NameTokenizer(fullName);
        parsePrefix(name, tokens);

        // If the name consists of just one or two tokens, treat them as first/last name,
        // not as suffix.  Example: John Ma; Ma is last name, not "M.A.".
        if (tokens.mEndPointer > 2) {
            parseSuffix(name, tokens);
        }

        if (name.prefix == null && tokens.mEndPointer - tokens.mStartPointer == 1) {
            name.givenNames = tokens.mTokens[tokens.mStartPointer];
        } else {
            parseLastName(name, tokens);
            parseMiddleName(name, tokens);
            parseGivenNames(name, tokens);
        }
    }

    /**
     * Splits a full name composed according to the Chinese tradition:
     * <pre>
     *   [family name [middle name]] given name
     * </pre>
     */
    private void splitChineseName(Name name, String fullName) {
        StringTokenizer tokenizer = new StringTokenizer(fullName);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (name.givenNames == null) {
                name.givenNames = token;
            } else if (name.familyName == null) {
                name.familyName = name.givenNames;
                name.givenNames = token;
            } else if (name.middleName == null) {
                name.middleName = name.givenNames;
                name.givenNames = token;
            } else {
                name.middleName = name.middleName + name.givenNames;
                name.givenNames = token;
            }
        }

        // If a single word parse that word up.
        if (name.givenNames != null && name.familyName == null && name.middleName == null) {
            int length = fullName.length();
            if (length == 2) {
                name.familyName = fullName.substring(0, 1);
                name.givenNames = fullName.substring(1);
            } else if (length == 3) {
                name.familyName = fullName.substring(0, 1);
                name.middleName = fullName.substring(1, 2);
                name.givenNames = fullName.substring(2);
            } else if (length == 4) {
                name.familyName = fullName.substring(0, 2);
                name.middleName = fullName.substring(2, 3);
                name.givenNames = fullName.substring(3);
            }

        }
    }

    /**
     * Splits a full name composed according to the Japanese tradition:
     * <pre>
     *   [family name] given name(s)
     * </pre>
     */
    private void splitJapaneseName(Name name, String fullName) {
        StringTokenizer tokenizer = new StringTokenizer(fullName);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (name.givenNames == null) {
                name.givenNames = token;
            } else if (name.familyName == null) {
                name.familyName = name.givenNames;
                name.givenNames = token;
            } else {
                name.givenNames += " " + token;
            }
        }
    }

    /**
     * Splits a full name composed according to the Korean tradition:
     * <pre>
     *   [family name] given name(s)
     * </pre>
     */
    private void splitKoreanName(Name name, String fullName) {
        StringTokenizer tokenizer = new StringTokenizer(fullName);
        if (tokenizer.countTokens() > 1) {
            // Each name can be identified by separators.
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                if (name.givenNames == null) {
                    name.givenNames = token;
                } else if (name.familyName == null) {
                    name.familyName = name.givenNames;
                    name.givenNames = token;
                } else {
                    name.givenNames += " " + token;
                }
            }
        } else {
            // There is no separator. Try to guess family name.
            // The length of most family names is 1.
            int familyNameLength = 1;

            // Compare with 2-length family names.
            for (String twoLengthFamilyName : KOREAN_TWO_CHARCTER_FAMILY_NAMES) {
                if (fullName.startsWith(twoLengthFamilyName)) {
                    familyNameLength = 2;
                    break;
                }
            }

            name.familyName = fullName.substring(0, familyNameLength);
            if (fullName.length() > familyNameLength) {
                name.givenNames = fullName.substring(familyNameLength);
            }
        }
    }

    /**
     * Concatenates components of a name according to the rules dictated by the name style.
     *
     * @param givenNameFirst is ignored for CJK display name styles
     */
    public String join(Name name, boolean givenNameFirst, boolean includePrefix) {
        String prefix = includePrefix ? name.prefix : null;
        switch (name.fullNameStyle) {
            case FullNameStyle.CJK:
            case FullNameStyle.CHINESE:
            case FullNameStyle.KOREAN:
                return join(prefix, name.familyName, name.middleName, name.givenNames,
                        name.suffix, false, false, false);

            case FullNameStyle.JAPANESE:
                return join(prefix, name.familyName, name.middleName, name.givenNames,
                        name.suffix, true, false, false);

            default:
                if (givenNameFirst) {
                    return join(prefix, name.givenNames, name.middleName, name.familyName,
                            name.suffix, true, false, true);
                } else {
                    return join(prefix, name.familyName, name.givenNames, name.middleName,
                            name.suffix, true, true, true);
                }
        }
    }

    /**
     * Concatenates components of the phonetic name following the CJK tradition:
     * family name + middle name + given name(s).
     */
    public String joinPhoneticName(Name name) {
        return join(null, name.phoneticFamilyName,
                name.phoneticMiddleName, name.phoneticGivenName, null, true, false, false);
    }

    /**
     * Concatenates parts of a full name inserting spaces and commas as specified.
     */
    private String join(String prefix, String part1, String part2, String part3, String suffix,
            boolean useSpace, boolean useCommaAfterPart1, boolean useCommaAfterPart3) {
        prefix = prefix == null ? null: prefix.trim();
        part1 = part1 == null ? null: part1.trim();
        part2 = part2 == null ? null: part2.trim();
        part3 = part3 == null ? null: part3.trim();
        suffix = suffix == null ? null: suffix.trim();

        boolean hasPrefix = !TextUtils.isEmpty(prefix);
        boolean hasPart1 = !TextUtils.isEmpty(part1);
        boolean hasPart2 = !TextUtils.isEmpty(part2);
        boolean hasPart3 = !TextUtils.isEmpty(part3);
        boolean hasSuffix = !TextUtils.isEmpty(suffix);

        boolean isSingleWord = true;
        String singleWord = null;

        if (hasPrefix) {
            singleWord = prefix;
        }

        if (hasPart1) {
            if (singleWord != null) {
                isSingleWord = false;
            } else {
                singleWord = part1;
            }
        }

        if (hasPart2) {
            if (singleWord != null) {
                isSingleWord = false;
            } else {
                singleWord = part2;
            }
        }

        if (hasPart3) {
            if (singleWord != null) {
                isSingleWord = false;
            } else {
                singleWord = part3;
            }
        }

        if (hasSuffix) {
            if (singleWord != null) {
                isSingleWord = false;
            } else {
                singleWord = normalizedSuffix(suffix);
            }
        }

        if (isSingleWord) {
            return singleWord;
        }

        StringBuilder sb = new StringBuilder();

        if (hasPrefix) {
            sb.append(prefix);
        }

        if (hasPart1) {
            if (hasPrefix) {
                sb.append(' ');
            }
            sb.append(part1);
        }

        if (hasPart2) {
            if (hasPrefix || hasPart1) {
                if (useCommaAfterPart1) {
                    sb.append(',');
                }
                if (useSpace) {
                    sb.append(' ');
                }
            }
            sb.append(part2);
        }

        if (hasPart3) {
            if (hasPrefix || hasPart1 || hasPart2) {
                if (useSpace) {
                    sb.append(' ');
                }
            }
            sb.append(part3);
        }

        if (hasSuffix) {
            if (hasPrefix || hasPart1 || hasPart2 || hasPart3) {
                if (useCommaAfterPart3) {
                    sb.append(',');
                }
                if (useSpace) {
                    sb.append(' ');
                }
            }
            sb.append(normalizedSuffix(suffix));
        }

        return sb.toString();
    }

    /**
     * Puts a dot after the supplied suffix if that is the accepted form of the suffix,
     * e.g. "Jr." and "Sr.", but not "I", "II" and "III".
     */
    private String normalizedSuffix(String suffix) {
        int length = suffix.length();
        if (length == 0 || suffix.charAt(length - 1) == '.') {
            return suffix;
        }

        String withDot = suffix + '.';
        if (mSuffixesSet.contains(withDot.toUpperCase())) {
            return withDot;
        } else {
            return suffix;
        }
    }

    /**
     * If the supplied name style is undefined, returns a default based on the language,
     * otherwise returns the supplied name style itself.
     *
     * @param nameStyle See {@link com.silentcircle.silentcontacts.ScContactsContract.FullNameStyle}.
     */
    public int getAdjustedFullNameStyle(int nameStyle) {
        if (nameStyle == FullNameStyle.UNDEFINED) {
            if (JAPANESE_LANGUAGE.equals(mLanguage)) {
                return FullNameStyle.JAPANESE;
            } else if (KOREAN_LANGUAGE.equals(mLanguage)) {
                return FullNameStyle.KOREAN;
            } else if (CHINESE_LANGUAGE.equals(mLanguage)) {
                return FullNameStyle.CHINESE;
            } else {
                return FullNameStyle.WESTERN;
            }
        } else if (nameStyle == FullNameStyle.CJK) {
            if (JAPANESE_LANGUAGE.equals(mLanguage)) {
                return FullNameStyle.JAPANESE;
            } else if (KOREAN_LANGUAGE.equals(mLanguage)) {
                return FullNameStyle.KOREAN;
            } else {
                return FullNameStyle.CHINESE;
            }
        }
        return nameStyle;
    }

    /**
     * Parses the first word from the name if it is a prefix.
     */
    private void parsePrefix(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }

        String firstToken = tokens.mTokens[tokens.mStartPointer];
        if (mPrefixesSet.contains(firstToken.toUpperCase())) {
            if (tokens.hasDot(tokens.mStartPointer)) {
                firstToken += '.';
            }
            name.prefix = firstToken;
            tokens.mStartPointer++;
        }
    }

    /**
     * Parses the last word(s) from the name if it is a suffix.
     */
    private void parseSuffix(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }

        String lastToken = tokens.mTokens[tokens.mEndPointer - 1];

        // Take care of an explicit comma-separated suffix
        if (tokens.mEndPointer - tokens.mStartPointer > 2
                && tokens.hasComma(tokens.mEndPointer - 2)) {
            if (tokens.hasDot(tokens.mEndPointer - 1)) {
                lastToken += '.';
            }
            name.suffix = lastToken;
            tokens.mEndPointer--;
            return;
        }

        if (lastToken.length() > mMaxSuffixLength) {
            return;
        }

        String normalized = lastToken.toUpperCase();
        if (mSuffixesSet.contains(normalized)) {
            name.suffix = lastToken;
            tokens.mEndPointer--;
            return;
        }

        if (tokens.hasDot(tokens.mEndPointer - 1)) {
            lastToken += '.';
        }
        normalized += ".";

        // Take care of suffixes like M.D. and D.D.S.
        int pos = tokens.mEndPointer - 1;
        while (normalized.length() <= mMaxSuffixLength) {

            if (mSuffixesSet.contains(normalized)) {
                name.suffix = lastToken;
                tokens.mEndPointer = pos;
                return;
            }

            if (pos == tokens.mStartPointer) {
                break;
            }

            pos--;
            if (tokens.hasDot(pos)) {
                lastToken = tokens.mTokens[pos] + "." + lastToken;
            } else {
                lastToken = tokens.mTokens[pos] + " " + lastToken;
            }

            normalized = tokens.mTokens[pos].toUpperCase() + "." + normalized;
        }
    }

    private void parseLastName(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }

        // If the first word is followed by a comma, assume that it's the family name
        if (tokens.hasComma(tokens.mStartPointer)) {
           name.familyName = tokens.mTokens[tokens.mStartPointer];
           tokens.mStartPointer++;
           return;
        }

        // If the second word is followed by a comma and the first word
        // is a last name prefix as in "de Sade" and "von Cliburn", treat
        // the first two words as the family name.
        if (tokens.mStartPointer + 1 < tokens.mEndPointer
                && tokens.hasComma(tokens.mStartPointer + 1)
                && isFamilyNamePrefix(tokens.mTokens[tokens.mStartPointer])) {
            String familyNamePrefix = tokens.mTokens[tokens.mStartPointer];
            if (tokens.hasDot(tokens.mStartPointer)) {
                familyNamePrefix += '.';
            }
            name.familyName = familyNamePrefix + " " + tokens.mTokens[tokens.mStartPointer + 1];
            tokens.mStartPointer += 2;
            return;
        }

        // Finally, assume that the last word is the last name
        name.familyName = tokens.mTokens[tokens.mEndPointer - 1];
        tokens.mEndPointer--;

        // Take care of last names like "de Sade" and "von Cliburn"
        if ((tokens.mEndPointer - tokens.mStartPointer) > 0) {
            String lastNamePrefix = tokens.mTokens[tokens.mEndPointer - 1];
            if (isFamilyNamePrefix(lastNamePrefix)) {
                if (tokens.hasDot(tokens.mEndPointer - 1)) {
                    lastNamePrefix += '.';
                }
                name.familyName = lastNamePrefix + " " + name.familyName;
                tokens.mEndPointer--;
            }
        }
    }

    /**
     * Returns true if the supplied word is an accepted last name prefix, e.g. "von", "de"
     */
    private boolean isFamilyNamePrefix(String word) {
        final String normalized = word.toUpperCase();

        return mLastNamePrefixesSet.contains(normalized)
                || mLastNamePrefixesSet.contains(normalized + ".");
    }


    private void parseMiddleName(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }

        if ((tokens.mEndPointer - tokens.mStartPointer) > 1) {
            if ((tokens.mEndPointer - tokens.mStartPointer) == 2
                    || !mConjuctions.contains(tokens.mTokens[tokens.mEndPointer - 2].
                            toUpperCase())) {
                name.middleName = tokens.mTokens[tokens.mEndPointer - 1];
                if (tokens.hasDot(tokens.mEndPointer - 1)) {
                    name.middleName += '.';
                }
                tokens.mEndPointer--;
            }
        }
    }

    private void parseGivenNames(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }

        if ((tokens.mEndPointer - tokens.mStartPointer) == 1) {
            name.givenNames = tokens.mTokens[tokens.mStartPointer];
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = tokens.mStartPointer; i < tokens.mEndPointer; i++) {
                if (i != tokens.mStartPointer) {
                    sb.append(' ');
                }
                sb.append(tokens.mTokens[i]);
                if (tokens.hasDot(i)) {
                    sb.append('.');
                }
            }
            name.givenNames = sb.toString();
        }
    }

    /**
     * Makes the best guess at the expected full name style based on the character set
     * used in the supplied name.  If the phonetic name is also supplied, tries to
     * differentiate between Chinese, Japanese and Korean based on the alphabet used
     * for the phonetic name.
     */
    public void guessNameStyle(Name name) {
        guessFullNameStyle(name);
        guessPhoneticNameStyle(name);
        name.fullNameStyle = getAdjustedNameStyleBasedOnPhoneticNameStyle(name.fullNameStyle,
                name.phoneticNameStyle);
    }

    /**
     * Updates the display name style according to the phonetic name style if we
     * were unsure about display name style based on the name components, but
     * phonetic name makes it more definitive.
     */
    public int getAdjustedNameStyleBasedOnPhoneticNameStyle(int nameStyle, int phoneticNameStyle) {
        if (phoneticNameStyle != PhoneticNameStyle.UNDEFINED) {
            if (nameStyle == FullNameStyle.UNDEFINED || nameStyle == FullNameStyle.CJK) {
                if (phoneticNameStyle == PhoneticNameStyle.JAPANESE) {
                    return FullNameStyle.JAPANESE;
                } else if (phoneticNameStyle == PhoneticNameStyle.KOREAN) {
                    return FullNameStyle.KOREAN;
                }
                if (nameStyle == FullNameStyle.CJK && phoneticNameStyle == PhoneticNameStyle.PINYIN) {
                    return FullNameStyle.CHINESE;
                }
            }
        }
        return nameStyle;
    }

    /**
     * Makes the best guess at the expected full name style based on the character set
     * used in the supplied name.
     */
    private void guessFullNameStyle(NameSplitter.Name name) {
        if (name.fullNameStyle != FullNameStyle.UNDEFINED) {
            return;
        }

        int bestGuess = guessFullNameStyle(name.givenNames);
        // A mix of Hanzi and latin chars are common in China, so we have to go through all names
        // if the name is not JANPANESE or KOREAN.
        if (bestGuess != FullNameStyle.UNDEFINED && bestGuess != FullNameStyle.CJK
                && bestGuess != FullNameStyle.WESTERN) {
            name.fullNameStyle = bestGuess;
            return;
        }

        int guess = guessFullNameStyle(name.familyName);
        if (guess != FullNameStyle.UNDEFINED) {
            if (guess != FullNameStyle.CJK && guess != FullNameStyle.WESTERN) {
                name.fullNameStyle = guess;
                return;
            }
            bestGuess = guess;
        }

        guess = guessFullNameStyle(name.middleName);
        if (guess != FullNameStyle.UNDEFINED) {
            if (guess != FullNameStyle.CJK && guess != FullNameStyle.WESTERN) {
                name.fullNameStyle = guess;
                return;
            }
            bestGuess = guess;
        }

        guess = guessFullNameStyle(name.prefix);
        if (guess != FullNameStyle.UNDEFINED) {
            if (guess != FullNameStyle.CJK && guess != FullNameStyle.WESTERN) {
                name.fullNameStyle = guess;
                return;
            }
            bestGuess = guess;
        }

        guess = guessFullNameStyle(name.suffix);
        if (guess != FullNameStyle.UNDEFINED) {
            if (guess != FullNameStyle.CJK && guess != FullNameStyle.WESTERN) {
                name.fullNameStyle = guess;
                return;
            }
            bestGuess = guess;
        }

        name.fullNameStyle = bestGuess;
    }

    public int guessFullNameStyle(String name) {
        if (name == null) {
            return FullNameStyle.UNDEFINED;
        }

        int nameStyle = FullNameStyle.UNDEFINED;
        int length = name.length();
        int offset = 0;
        while (offset < length) {
            int codePoint = Character.codePointAt(name, offset);
            if (Character.isLetter(codePoint)) {
                UnicodeBlock unicodeBlock = UnicodeBlock.of(codePoint);

                if (!isLatinUnicodeBlock(unicodeBlock)) {

                    if (isCJKUnicodeBlock(unicodeBlock)) {
                        // We don't know if this is Chinese, Japanese or Korean -
                        // trying to figure out by looking at other characters in the name
                        return guessCJKNameStyle(name, offset + Character.charCount(codePoint));
                    }

                    if (isJapanesePhoneticUnicodeBlock(unicodeBlock)) {
                        return FullNameStyle.JAPANESE;
                    }

                    if (isKoreanUnicodeBlock(unicodeBlock)) {
                        return FullNameStyle.KOREAN;
                    }
                }
                nameStyle = FullNameStyle.WESTERN;
            }
            offset += Character.charCount(codePoint);
        }
        return nameStyle;
    }

    private int guessCJKNameStyle(String name, int offset) {
        int length = name.length();
        while (offset < length) {
            int codePoint = Character.codePointAt(name, offset);
            if (Character.isLetter(codePoint)) {
                UnicodeBlock unicodeBlock = UnicodeBlock.of(codePoint);
                if (isJapanesePhoneticUnicodeBlock(unicodeBlock)) {
                    return FullNameStyle.JAPANESE;
                }
                if (isKoreanUnicodeBlock(unicodeBlock)) {
                    return FullNameStyle.KOREAN;
                }
            }
            offset += Character.charCount(codePoint);
        }

        return FullNameStyle.CJK;
    }

    private void guessPhoneticNameStyle(NameSplitter.Name name) {
        if (name.phoneticNameStyle != PhoneticNameStyle.UNDEFINED) {
            return;
        }

        int bestGuess = guessPhoneticNameStyle(name.phoneticFamilyName);
        if (bestGuess != FullNameStyle.UNDEFINED && bestGuess != FullNameStyle.CJK) {
            name.phoneticNameStyle = bestGuess;
            return;
        }

        int guess = guessPhoneticNameStyle(name.phoneticGivenName);
        if (guess != FullNameStyle.UNDEFINED) {
            if (guess != FullNameStyle.CJK) {
                name.phoneticNameStyle = guess;
                return;
            }
            bestGuess = guess;
        }

        guess = guessPhoneticNameStyle(name.phoneticMiddleName);
        if (guess != FullNameStyle.UNDEFINED) {
            if (guess != FullNameStyle.CJK) {
                name.phoneticNameStyle = guess;
                return;
            }
            bestGuess = guess;
        }
    }

    public int guessPhoneticNameStyle(String name) {
        if (name == null) {
            return PhoneticNameStyle.UNDEFINED;
        }

        int nameStyle = PhoneticNameStyle.UNDEFINED;
        int length = name.length();
        int offset = 0;
        while (offset < length) {
            int codePoint = Character.codePointAt(name, offset);
            if (Character.isLetter(codePoint)) {
                UnicodeBlock unicodeBlock = UnicodeBlock.of(codePoint);
                if (isJapanesePhoneticUnicodeBlock(unicodeBlock)) {
                    return PhoneticNameStyle.JAPANESE;
                }
                if (isKoreanUnicodeBlock(unicodeBlock)) {
                    return PhoneticNameStyle.KOREAN;
                }
                if (isLatinUnicodeBlock(unicodeBlock)) {
                    return PhoneticNameStyle.PINYIN;
                }
            }
            offset += Character.charCount(codePoint);
        }

        return nameStyle;
    }

    private static boolean isLatinUnicodeBlock(UnicodeBlock unicodeBlock) {
        return unicodeBlock == UnicodeBlock.BASIC_LATIN ||
                unicodeBlock == UnicodeBlock.LATIN_1_SUPPLEMENT ||
                unicodeBlock == UnicodeBlock.LATIN_EXTENDED_A ||
                unicodeBlock == UnicodeBlock.LATIN_EXTENDED_B ||
                unicodeBlock == UnicodeBlock.LATIN_EXTENDED_ADDITIONAL;
    }

    private static boolean isCJKUnicodeBlock(UnicodeBlock block) {
        return block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == UnicodeBlock.CJK_RADICALS_SUPPLEMENT
                || block == UnicodeBlock.CJK_COMPATIBILITY
                || block == UnicodeBlock.CJK_COMPATIBILITY_FORMS
                || block == UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }

    private static boolean isKoreanUnicodeBlock(UnicodeBlock unicodeBlock) {
        return unicodeBlock == UnicodeBlock.HANGUL_SYLLABLES ||
                unicodeBlock == UnicodeBlock.HANGUL_JAMO ||
                unicodeBlock == UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }

    private static boolean isJapanesePhoneticUnicodeBlock(UnicodeBlock unicodeBlock) {
        return unicodeBlock == UnicodeBlock.KATAKANA ||
                unicodeBlock == UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS ||
                unicodeBlock == UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
                unicodeBlock == UnicodeBlock.HIRAGANA;
    }
}
