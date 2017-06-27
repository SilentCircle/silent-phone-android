/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts.calllognew;

import android.content.res.Resources;
import android.provider.CallLog.Calls;
import android.text.TextUtils;

import com.silentcircle.silentphone2.R;


/**
 * Helper for formatting and managing the display of phone numbers.
 */
public class PhoneNumberDisplayHelper {
    private final PhoneNumberUtilsWrapper mPhoneNumberUtils;
    private final Resources mResources;

    public PhoneNumberDisplayHelper(Resources resources) {
        mResources = resources;
        mPhoneNumberUtils = new PhoneNumberUtilsWrapper();
    }

    public PhoneNumberDisplayHelper(PhoneNumberUtilsWrapper phoneNumberUtils, Resources resources) {
        mPhoneNumberUtils = phoneNumberUtils;
        mResources = resources;
    }

    /* package */ CharSequence getDisplayName(CharSequence number, int presentation) {
        if (presentation == Calls.PRESENTATION_UNKNOWN) {
            return mResources.getString(R.string.unknown);
        }
        if (presentation == Calls.PRESENTATION_RESTRICTED) {
            return mResources.getString(R.string.private_num);
        }
        if (presentation == Calls.PRESENTATION_PAYPHONE) {
            return mResources.getString(R.string.payphone);
        }
//        if (mPhoneNumberUtils.isVoicemailNumber(number)) {
//            return mResources.getString(R.string.voicemail);
//        }
        if (PhoneNumberUtilsWrapper.isLegacyUnknownNumbers(number)) {
            return mResources.getString(R.string.unknown);
        }
        return "";
    }

    /**
     * Returns the string to display for the given phone number.
     *
     * @param number the number to display
     * @param formattedNumber the formatted number if available, may be null
     */
    public CharSequence getDisplayNumber(CharSequence number,
            int presentation, CharSequence formattedNumber) {

        final CharSequence displayName = getDisplayName(number, presentation);
        if (!TextUtils.isEmpty(displayName)) {
            return displayName;
        }

        if (TextUtils.isEmpty(number)) {
            return "";
        }

        if (TextUtils.isEmpty(formattedNumber)) {
            return number;
        } else {
            return formattedNumber;
        }
    }
}
