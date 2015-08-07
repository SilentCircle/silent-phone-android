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
 * Copyright (C) 2006 The Android Open Source Project
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

package com.silentcircle.contacts.utils;

import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * One helper function to normalize phone number - copied from Android phone utils.
 */
public class PhoneNumberHelper
{
    private PhoneNumberHelper() {}
    /**
     * Normalize a phone number by removing the characters other than digits. If
     * the given number has keypad letters, the letters will be converted to
     * digits first.
     *
     * @param phoneNumber
     *            the number to be normalized.
     * @return the normalized number.
     *
     * @hide
     */
    public static String normalizeNumber(String phoneNumber) {
        if (phoneNumber == null)
            return null;
        StringBuilder sb = new StringBuilder();
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            // Character.digit() supports ASCII and Unicode digits (fullwidth, Arabic-Indic, etc.)
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                sb.append(digit);
            } else if (i == 0 && c == '+') {
                sb.append(c);
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return normalizeNumber(PhoneNumberUtils.convertKeypadLettersToDigits(phoneNumber));
            }
        }
        return sb.toString();
    }


    /**
     * Format the given phoneNumber to the E.164 representation.
     * <p>
     * The given phone number must have an area code and could have a country
     * code.
     * <p>
     * The defaultCountryIso is used to validate the given number and generate
     * the E.164 phone number if the given number doesn't have a country code.
     *
     * @param phoneNumber
     *            the phone number to format
     * @param defaultCountryIso
     *            the ISO 3166-1 two letters country code
     * @return the E.164 representation, or null if the given phone number is
     *         not valid.
     *
     * @hide
     */
    public static String formatNumberToE164(String phoneNumber, String defaultCountryIso) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        String result = null;
        try {
            PhoneNumber pn = util.parse(phoneNumber, defaultCountryIso);
            if (util.isValidNumber(pn)) {
                result = util.format(pn, PhoneNumberFormat.E164);
            }
        } catch (NumberParseException e) {
        }
        return result;
    }

    /**
     * Format a phone number.
     * <p>
     * If the given number doesn't have the country code, the phone will be
     * formatted to the default country's convention.
     *
     * @param phoneNumber
     *            the number to be formatted.
     * @param defaultCountryIso
     *            the ISO 3166-1 two letters country code whose convention will
     *            be used if the given number doesn't have the country code.
     * @return the formatted number, or null if the given number is not valid.
     *
     * @hide
     */
    public static String formatNumber(String phoneNumber, String defaultCountryIso) {
        if (phoneNumber == null)
            return null;
        // Do not attempt to format numbers that start with a hash or star symbol.
        if (phoneNumber.startsWith("#") || phoneNumber.startsWith("*")) {
            return phoneNumber;
        }

        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        String result = null;
        try {
            PhoneNumber pn = util.parseAndKeepRawInput(phoneNumber, defaultCountryIso);
            result = util.formatInOriginalFormat(pn, defaultCountryIso);
        } catch (NumberParseException e) {
        }
        return result;
    }

    /**
     * Format the phone number only if the given number hasn't been formatted.
     * <p>
     * The number which has only dailable character is treated as not being
     * formatted.
     *
     * @param phoneNumber
     *            the number to be formatted.
     * @param phoneNumberE164
     *            the E164 format number whose country code is used if the given
     *            phoneNumber doesn't have the country code.
     * @param defaultCountryIso
     *            the ISO 3166-1 two letters country code whose convention will
     *            be used if the phoneNumberE164 is null or invalid, or if phoneNumber
     *            contains IDD.
     * @return the formatted number if the given number has been formatted,
     *            otherwise, return the given number.
     *
     * @hide
     */
    public static String formatNumber(
            String phoneNumber, String phoneNumberE164, String defaultCountryIso) {
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            if (!isDialable(phoneNumber.charAt(i))) {
                return phoneNumber;
            }
        }
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        // Get the country code from phoneNumberE164
        if (phoneNumberE164 != null && phoneNumberE164.length() >= 2
                && phoneNumberE164.charAt(0) == '+') {
            try {
                // The number to be parsed is in E164 format, so the default region used doesn't
                // matter.
                PhoneNumber pn = util.parse(phoneNumberE164, "ZZ");
                String regionCode = util.getRegionCodeForNumber(pn);
                if (!TextUtils.isEmpty(regionCode) &&
                    // This makes sure phoneNumber doesn't contain an IDD
                    normalizeNumber(phoneNumber).indexOf(phoneNumberE164.substring(1)) <= 0) {
                    defaultCountryIso = regionCode;
                }
            } catch (NumberParseException e) {
            }
        }
        String result = formatNumber(phoneNumber, defaultCountryIso);
        return result != null ? result : phoneNumber;
    }

    /**
     * Determines if the specified number is actually a URI
     * (i.e. a SIP address) rather than a regular PSTN phone number,
     * based on whether or not the number contains an "@" character.
     *
     * @param number
     * @return true if number contains @
     */
    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    /**
     * @return the "username" part of the specified SIP address,
     *         i.e. the part before the "@" character (or "%40").
     *
     * @param number SIP address of the form "username@domainname"
     *               (or the URI-escaped equivalent "username%40domainname")
     * @see isUriNumber
     */
    public static String getUsernameFromUriNumber(String number) {
        // The delimiter between username and domain name can be
        // either "@" or "%40" (the URI-escaped equivalent.)
        int delimiterIndex = number.indexOf('@');
        if (delimiterIndex < 0) {
            delimiterIndex = number.indexOf("%40");
        }
        if (delimiterIndex < 0) {
            delimiterIndex = number.length();
        }
        return number.substring(0, delimiterIndex);
    }
    /*
     * Special characters
     *
     * (See "What is a phone number?" doc)
     * 'p' --- GSM pause character, same as comma
     * 'n' --- GSM wild character
     * 'w' --- GSM wait character
     */
    public static final char PAUSE = ',';
    public static final char WAIT = ';';
    public static final char WILD = 'N';

    /** True if c is ISO-LATIN characters 0-9, *, # , +, WILD  */
    public final static boolean
    isDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+' || c == WILD;
    }

    /** True if c is ISO-LATIN characters 0-9, *, # , + (no WILD)  */
    public final static boolean
    isReallyDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+';
    }


}
