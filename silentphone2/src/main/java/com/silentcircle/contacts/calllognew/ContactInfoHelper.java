/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.silentcontacts2.ScContactsContract.DisplayPhoto;
import com.silentcircle.silentcontacts2.ScContactsContract.PhoneLookup;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts.Photo;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Utility class to look up the contact information for a given number.
 */
public class ContactInfoHelper {
    private final static String TAG = "ContactInfoHelper";
    private final Context mContext;
    private final String mCurrentCountryIso;

    public ContactInfoHelper(Context context, String currentCountryIso) {
        mContext = context;
        mCurrentCountryIso = currentCountryIso;
    }

    /**
     * Returns the contact information for the given number.
     * <p>
     * If the number does not match any contact, returns a contact info containing only the number
     * and the formatted number.
     * <p>
     * If an error occurs during the lookup, it returns null.
     *
     * @param number the number to look up
     * @param countryIso the country associated with this number
     */
    public ContactInfo lookupNumber(String number, String countryIso) {
        final ContactInfo info;

        if (com.silentcircle.contacts.utils.PhoneNumberHelper.isUriNumber(number)) {
            // This "number" is really a SIP address.
            ContactInfo sipInfo = queryContactInfoForSipAddress(number);
            if (sipInfo == null || sipInfo == ContactInfo.EMPTY) {
                // Check whether the "username" part of the SIP address is
                // actually the phone number of a contact.
                String username = PhoneNumberHelper.getUsernameFromUriNumber(number);
                if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
                    sipInfo = queryContactInfoForPhoneNumber(username, countryIso);
                }
            }
            info = sipInfo;
        }
        else {
            // Look for a contact that has the given phone number.
            ContactInfo phoneInfo = queryContactInfoForPhoneNumber(number, countryIso);

            if (phoneInfo == null || phoneInfo == ContactInfo.EMPTY) {
                // Check whether the phone number has been saved as an "Internet call" number.
                phoneInfo = queryContactInfoForSipAddress(number);
            }
            info = phoneInfo;
        }

        final ContactInfo updatedInfo;
        if (info == null) {
            // The lookup failed.
            updatedInfo = null;
        } else {
            // If we did not find a matching contact, generate an empty contact info for the number.
            if (info == ContactInfo.EMPTY) {
                // Did not find a matching contact.
                updatedInfo = new ContactInfo();
                updatedInfo.number = number;
                if (com.silentcircle.contacts.utils.PhoneNumberHelper.isUriNumber(number))
                    updatedInfo.formattedNumber = Utilities.removeSipParts(number);
                else
                    updatedInfo.formattedNumber = formatPhoneNumber(number, null, countryIso);
            } else {
                updatedInfo = info;
            }
        }
        return updatedInfo;
    }

    /**
     * Looks up a contact using the given URI.
     * <p>
     * It returns null if an error occurs, {@link ContactInfo#EMPTY} if no matching contact is
     * found, or the {@link ContactInfo} for the given contact.
     * <p>
     * The {@link ContactInfo#formattedNumber} field is always set to {@code null} in the returned
     * value.
     */
    private ContactInfo lookupContactFromUri(Uri uri, boolean isSip) {
        final ContactInfo info;
        Cursor phonesCursor =
                mContext.getContentResolver().query(uri, isSip ? PhoneQuery._PROJECTION_SIP : PhoneQuery._PROJECTION, null, null, null);

        if (phonesCursor != null) {
            try {
                if (phonesCursor.moveToFirst()) {
                    info = new ContactInfo();
                    long contactId = !isSip ? phonesCursor.getLong(PhoneQuery.PERSON_ID) : phonesCursor.getLong(PhoneQuery.DATA_RAW_CONTACT_ID);
                    info.lookupUri = RawContacts.getLookupUri(contactId);
                    info.name = phonesCursor.getString(PhoneQuery.NAME);
                    info.type = phonesCursor.getInt(PhoneQuery.PHONE_TYPE);
                    info.label = phonesCursor.getString(PhoneQuery.LABEL);
                    info.number = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                    info.normalizedNumber = phonesCursor.getString(PhoneQuery.NORMALIZED_NUMBER);
                    info.photoId = phonesCursor.getLong(PhoneQuery.PHOTO_ID);
                    info.photoFileId = phonesCursor.getLong(PhoneQuery.PHOTO_FILE_ID);
                    if (info.photoFileId != 0) {
                        info.photoUri = ContentUris.withAppendedId(DisplayPhoto.CONTENT_URI, info.photoFileId);
                    }
                    else if (info.photoId != 0) {
                        info.photoUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, contactId),
                                Photo.CONTENT_DIRECTORY);
                    }
                    else
                        info.photoUri = null;
                    info.formattedNumber = null;
                } 
                else {
                    info = ContactInfo.EMPTY;
                }
            } finally {
                phonesCursor.close();
            }
        } else {
            // Failed to fetch the data, ignore this request.
            info = null;
        }
        return info;
    }

    /**
     * Determines the contact information for the given SIP address.
     * <p>
     * It returns the contact info if found.
     * <p>
     * If no contact corresponds to the given SIP address, returns {@link ContactInfo#EMPTY}.
     * <p>
     * If the lookup fails for some other reason, it returns null.
     */
    private ContactInfo queryContactInfoForSipAddress(String sipAddress) {
        final ContactInfo info;

        // "contactNumber" is a SIP address, so use the PhoneLookup table with the SIP parameter.
        Uri.Builder uriBuilder = PhoneLookup.CONTENT_FILTER_URI.buildUpon();
        uriBuilder.appendPath(Uri.encode(sipAddress));
        uriBuilder.appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, "1");
        info = lookupContactFromUri(uriBuilder.build(), true);
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = Utilities.removeSipParts(sipAddress);
        }
        return info;
    }

    /**
     * Determines the contact information for the given phone number.
     * <p>
     * It returns the contact info if found.
     * <p>
     * If no contact corresponds to the given phone number, returns {@link ContactInfo#EMPTY}.
     * <p>
     * If the lookup fails for some other reason, it returns null.
     */
    private ContactInfo queryContactInfoForPhoneNumber(String number, String countryIso) {
        String contactNumber = number;
        if (!TextUtils.isEmpty(countryIso)) {
            // Normalize the number: this is needed because the PhoneLookup query below does not
            // accept a country code as an input.
            String numberE164 = PhoneNumberHelper.formatNumberToE164(number, countryIso);
            if (!TextUtils.isEmpty(numberE164)) {
                // Only use it if the number could be formatted to E164.
                contactNumber = numberE164;
            }
        }
        // The "contactNumber" is a regular phone number, so use the PhoneLookup table.
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contactNumber));
        ContactInfo info = lookupContactFromUri(uri, false);
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = formatPhoneNumber(number, null, countryIso);
        }
        return info;
    }

    /**
     * Format the given phone number
     *
     * @param number the number to be formatted.
     * @param normalizedNumber the normalized number of the given number.
     * @param countryIso the ISO 3166-1 two letters country code, the country's
     *        convention will be used to format the number if the normalized
     *        phone is null.
     *
     * @return the formatted number, or the given number if it was formatted.
     */
    private String formatPhoneNumber(String number, String normalizedNumber, String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        // If "number" is really a SIP address, don't try to do any formatting at all.
        if (PhoneNumberHelper.isUriNumber(number)) {
            return number;
        }
        if (TextUtils.isEmpty(countryIso)) {
            countryIso = mCurrentCountryIso;
        }
        return PhoneNumberHelper.formatNumber(number, normalizedNumber, countryIso);
    }
}
