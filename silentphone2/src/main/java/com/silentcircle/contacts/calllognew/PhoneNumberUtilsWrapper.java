/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.silentcircle.contacts.calllognew;

import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.google.common.collect.Sets;
import com.silentcircle.contacts.utils.PhoneNumberHelper;

import java.util.Set;

/**
 *
 */
public class PhoneNumberUtilsWrapper {
    public static final PhoneNumberUtilsWrapper INSTANCE = new PhoneNumberUtilsWrapper();
    private static final Set<String> LEGACY_UNKNOWN_NUMBERS = Sets.newHashSet("-1", "-2", "-3");

    /** Returns true if it is possible to place a call to the given number. */
    public static boolean canPlaceCallsTo(CharSequence number, int presentation) {
        return /* presentation == CallLog.Calls.PRESENTATION_ALLOWED &&*/ !TextUtils.isEmpty(number) && !isLegacyUnknownNumbers(number);
    }

    /**
     * Returns true if it is possible to send an SMS to the given number.
     */
    public boolean canSendSmsTo(CharSequence number, int presentation) {
        return canPlaceCallsTo(number, presentation) && !isVoicemailNumber(number) && !isSipNumber(
                number);
    }

    /**
     * Returns true if the given number is the number of the configured voicemail. To be able to
     * mock-out this, it is not a static method.
     */
    public boolean isVoicemailNumber(CharSequence number) {
        return number!= null && PhoneNumberUtils.isVoiceMailNumber(number.toString());
    }

    /**
     * Returns true if the given number is a SIP address. To be able to mock-out this, it is not a
     * static method.
     */
    public boolean isSipNumber(CharSequence number) {
        return number != null && PhoneNumberHelper.isUriNumber(number.toString());
    }

    public static boolean isUnknownNumberThatCanBeLookedUp(CharSequence number, int presentation) {
//        if (presentation == ScCallLog.ScCalls.PRESENTATION_UNKNOWN) {
//            return false;
//        }
//        if (presentation == ScCallLog.ScCalls.PRESENTATION_RESTRICTED) {
//            return false;
//        }
//        if (presentation == ScCallLog.ScCalls.PRESENTATION_PAYPHONE) {
//            return false;
//        }
        if (TextUtils.isEmpty(number)) {
            return false;
        }
//        if (INSTANCE.isVoicemailNumber(number)) {
//            return false;
//        }
//        if (isLegacyUnknownNumbers(number)) {
//            return false;
//        }
        return true;
    }

    public static boolean isLegacyUnknownNumbers(CharSequence number) {
        return number != null && LEGACY_UNKNOWN_NUMBERS.contains(number.toString());
    }
}
