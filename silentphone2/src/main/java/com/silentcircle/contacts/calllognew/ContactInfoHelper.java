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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.contacts.UpdateScContactDataService;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.contacts.utils.UriUtils;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Utility class to look up the contact information for a given number.
 */
public class ContactInfoHelper {
    private final static String TAG = "ContactInfoHelper";

    private static  final String SIP_DOMAIN_SILENTCIRCLE = "@sip.silentcircle.net";

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
    @WorkerThread
    public ContactInfo lookupNumber(String number, String countryIso) {
        final ContactInfo info;

        if (Utilities.isUriNumber(number)) {
            // Try to remove the domain name
            String address = Utilities.removeUriPartsSelective(number);

            // If it still has a domain name then it's a SSO. the above remove
            // function only removes SC SIP address domain and SC namespace domain.
            if (Utilities.isUriNumber(address)) {
                info = queryContactInfoForEmailAddress(number);
            }
            else {
                // This "number" is really a SIP address.
                ContactInfo sipInfo = queryContactInfoForSipAddress(mContext, number);
                if (sipInfo == null || sipInfo == ContactInfo.EMPTY) {
                    // Check whether the "username" part of the SIP address is
                    // actually the phone number of a contact.
                    String username = Utilities.removeSipParts(number);
                    if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
                        sipInfo = queryContactInfoForPhoneNumber(username, countryIso);
                    }
                }
                info = sipInfo;
            }
        }
        else {
            // Look for a contact that has the given phone number.
            ContactInfo phoneInfo = queryContactInfoForPhoneNumber(number, countryIso);

            if (phoneInfo == null || phoneInfo == ContactInfo.EMPTY) {
                // Check whether the phone number has been saved as an "Internet call" number.
                phoneInfo = queryContactInfoForSipAddress(mContext, number);
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
                final String pureNumber = Utilities.removeUriPartsSelective(number);
                if (com.silentcircle.contacts.utils.PhoneNumberHelper.isUriNumber(number))
                    updatedInfo.formattedNumber = pureNumber;
                else
                    updatedInfo.formattedNumber = formatPhoneNumber(number, null, countryIso);

                updatedInfo.number = number;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    updatedInfo.normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
                updatedInfo.lookupUri = createTemporaryContactUri(updatedInfo.formattedNumber);
                updatedInfo.lookupKey = updatedInfo.formattedNumber;

                // Use the pureNumber (which could be an UUID or a simple name) as an alias
                // and lookup user info for it. This may trigger a network transaction, however
                // we are running in a worker thread. If user info is available then set the
                // name to the user's display name. The view/display functions prefer the
                // info.name if set, then the formattedNumber, and number is the lowest priority
                int[] errorCode = new int[1];
                byte[] data = ZinaMessaging.getUserInfo(pureNumber, null, errorCode);
                AsyncTasks.UserInfo ui = AsyncTasks.parseUserInfo(data);
                if (ui != null && ui.mDisplayName != null) {
                    updatedInfo.name = ui.mDisplayName;
                    if (!TextUtils.isEmpty(ui.mAvatarUrl)) {
                        updatedInfo.photoUri = Uri.parse(
                                ConfigurationUtilities.getProvisioningBaseUrl(
                                        SilentPhoneApplication.getAppContext()) + ui.mAvatarUrl);
                    }
                }
            } else {
                updatedInfo = info;
            }
        }
        return updatedInfo;
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
    public ContactInfo lookupNumberWithoutAxoCache(String number, String countryIso) {
        final ContactInfo info;

        if (Utilities.isUriNumber(number)) {
            // Try to remove the domain name
            String address = Utilities.removeUriPartsSelective(number);

            // If it still has a domain name then it's a SSO. the above remove
            // function only removes SC SIP address domain and SC namespace domain.
            if (Utilities.isUriNumber(address)) {
                info = queryContactInfoForEmailAddress(number);
            }
            else {
                // This "number" is really a SIP address.
                ContactInfo sipInfo = queryContactInfoForSipAddress(mContext, number);
                if (sipInfo == null || sipInfo == ContactInfo.EMPTY) {
                    // Check whether the "username" part of the SIP address is
                    // actually the phone number of a contact.
                    String username = Utilities.removeSipParts(number);
                    if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
                        sipInfo = queryContactInfoForPhoneNumber(username, countryIso);
                    }
                }
                info = sipInfo;
            }
        }
        else {
            // Look for a contact that has the given phone number.
            ContactInfo phoneInfo = queryContactInfoForPhoneNumber(number, countryIso);

            if (phoneInfo == null || phoneInfo == ContactInfo.EMPTY) {
                // Check whether the phone number has been saved as an "Internet call" number.
                phoneInfo = queryContactInfoForSipAddress(mContext, number);
            }
            info = phoneInfo;
        }

        return info;
    }

    /**
     * Creates a JSON-encoded lookup uri for a unknown number without an associated contact
     *
     * @param number - Unknown phone number
     * @return JSON-encoded URI that can be used to perform a lookup when clicking on the quick
     *         contact card.
     */
    private static Uri createTemporaryContactUri(String number) {
        try {
            JSONObject contactRows;
            if (Utilities.isValidSipUsername(number)) {
                contactRows = new JSONObject().put(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                        new JSONObject().put(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,
                                Utilities.isUriNumber(number) ? number : number + SIP_DOMAIN_SILENTCIRCLE));
            }
            else {
                contactRows = new JSONObject().put(Phone.CONTENT_ITEM_TYPE,
                        new JSONObject().put(Phone.NUMBER, number).put(Phone.TYPE, Phone.TYPE_CUSTOM));
            }

            final String jsonString = new JSONObject().put(Contacts.DISPLAY_NAME, number)
                    .put(Contacts.DISPLAY_NAME_SOURCE, ContactsContract.DisplayNameSources.PHONE)
                    .put(Contacts.CONTENT_ITEM_TYPE, contactRows).toString();

            return Contacts.CONTENT_LOOKUP_URI
                    .buildUpon()
                    .appendPath(Constants.LOOKUP_URI_ENCODED)
                    .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                            String.valueOf(Long.MAX_VALUE))
                    .encodedFragment(jsonString)
                    .build();
        } catch (JSONException e) {
            return null;
        }
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
    private ContactInfo lookupContactFromUri(Context context, Uri uri) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        final ContactInfo info;
        Cursor phonesCursor =
                mContext.getContentResolver().query(uri, PhoneQuery._PROJECTION, null, null, null);

        if (phonesCursor != null) {
            try {
                if (phonesCursor.moveToFirst()) {
                    info = new ContactInfo();
                    long contactId = phonesCursor.getLong(PhoneQuery.PERSON_ID);
                    String lookupKey = phonesCursor.getString(PhoneQuery.LOOKUP_KEY);
                    info.lookupKey = lookupKey;
                    info.lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    info.name = phonesCursor.getString(PhoneQuery.NAME);
                    info.type = phonesCursor.getInt(PhoneQuery.PHONE_TYPE);
                    info.label = phonesCursor.getString(PhoneQuery.LABEL);
                    info.number = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                    info.normalizedNumber = phonesCursor.getString(PhoneQuery.NORMALIZED_NUMBER);
                    info.photoId = phonesCursor.getLong(PhoneQuery.PHOTO_ID);
                    info.photoUri =
                            UriUtils.parseUriOrNull(phonesCursor.getString(PhoneQuery.PHOTO_URI));
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

    // Creates a selection clause to query specific SIP entries.
    @NonNull
    private static String createScSipLookupClause(final String sipAddress) {
        final StringBuilder sb = new StringBuilder();
        sb.append(ContactsContract.Data.DATA1 + " LIKE ");
        DatabaseUtils.appendEscapedSQLString(sb, '%' + sipAddress);
        sb.append(" AND (" + ContactsContract.Data.MIMETYPE + "='" + UpdateScContactDataService.SC_PHONE_CONTENT_TYPE +
                "' OR " + ContactsContract.Data.MIMETYPE + "='" + SipAddress.CONTENT_ITEM_TYPE + "')");
        return sb.toString();
    }

    // Creates a selection clause to query for specific website entry.
    @NonNull
    private static String createScWebLookupClause(final String webAddress) {
        final StringBuilder sb = new StringBuilder();
        sb.append(ContactsContract.Data.DATA1).append("=");
        DatabaseUtils.appendEscapedSQLString(sb, webAddress);
        sb.append(" AND ").append(ContactsContract.Data.MIMETYPE).append("='")
                .append(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE).append("'");

        return sb.toString();
    }

    public static ContactInfo queryContactInfoForScUuid(Context context, String uuid) {
        ContactInfo contactInfo = queryContactInfoForSipAddress(context, "sip:" + uuid + context.getString(R.string.sc_sip_domain_0));
        if (contactInfo == null || ContactInfo.EMPTY.equals(contactInfo)) {
            contactInfo = queryContactInfoForWebAddress(context, "silentphone:" + uuid);
        }
        return contactInfo;
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
    public static ContactInfo queryContactInfoForSipAddress(Context context, String sipAddress) {
        final ContactInfo info;

        Uri lookupUri = ContactsContract.Data.CONTENT_URI;
        String selection = createScSipLookupClause(sipAddress);

        info = lookupContactFromUriSip16(context, lookupUri, selection);
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = Utilities.removeUriPartsSelective(sipAddress);
        }
        return info;
    }

    private static ContactInfo lookupContactFromUriSip16(Context context, Uri uri, String selection) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        final ContactInfo info;
        Cursor phonesCursor =
                context.getContentResolver().query(uri, PhoneQuery._PROJECTION_SIP, selection, null, null);

        if (phonesCursor != null) {
            try {
//                DatabaseUtils.dumpCursor(phonesCursor);
                if (phonesCursor.moveToFirst()) {
                    info = new ContactInfo();
                    long contactId = phonesCursor.getLong(PhoneQuery.SIP_PERSON_ID);
                    String lookupKey = phonesCursor.getString(PhoneQuery.SIP_LOOKUP_KEY);
                    info.lookupKey = lookupKey;
                    info.lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    info.name = phonesCursor.getString(PhoneQuery.SIP_NAME);
                    info.type = phonesCursor.getInt(PhoneQuery.SIP_PHONE_TYPE);
                    info.label = phonesCursor.getString(PhoneQuery.SIP_LABEL);
                    info.number = phonesCursor.getString(PhoneQuery.SIP_ADDRESS);
                    info.normalizedNumber = null;
                    info.photoId = phonesCursor.getLong(PhoneQuery.SIP_PHOTO_ID);
                    info.photoUri =
                            UriUtils.parseUriOrNull(phonesCursor.getString(PhoneQuery.SIP_PHOTO_URI));
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
     * Determines the contact information for the given WEB address.
     * <p>
     * It returns the contact info if found.
     * <p>
     * If no contact corresponds to the given WEB address, returns {@link ContactInfo#EMPTY}.
     * <p>
     * If the lookup fails for some other reason, it returns null.
     */
    public static ContactInfo queryContactInfoForWebAddress(Context context, String sipAddress) {
        final ContactInfo info;

        Uri lookupUri = ContactsContract.Data.CONTENT_URI;
        String selection = createScWebLookupClause(sipAddress);

        info = lookupContactFromUriWeb(context, lookupUri, selection);
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = Utilities.removeUriPartsSelective(sipAddress);
        }
        return info;
    }

    private static ContactInfo lookupContactFromUriWeb(Context context, Uri uri, String selection) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        ContactInfo info = ContactInfo.EMPTY;
        Cursor phonesCursor =
                context.getContentResolver().query(uri, PhoneQuery._PROJECTION_WEB, selection, null, null);

        if (phonesCursor != null) {
            try {
                // DatabaseUtils.dumpCursor(phonesCursor);
                if (phonesCursor.moveToFirst()) {
                    info = new ContactInfo();
                    long contactId = phonesCursor.getLong(PhoneQuery.WEB_PERSON_ID);
                    String lookupKey = phonesCursor.getString(PhoneQuery.WEB_LOOKUP_KEY);
                    info.lookupKey = lookupKey;
                    info.lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    info.name = phonesCursor.getString(PhoneQuery.WEB_NAME);
                    info.type = phonesCursor.getInt(PhoneQuery.WEB_ADDRESS_TYPE);
                    info.label = phonesCursor.getString(PhoneQuery.WEB_LABEL);
                    info.number = phonesCursor.getString(PhoneQuery.WEB_ADDRESS);
                    info.normalizedNumber = null;
                    info.photoId = phonesCursor.getLong(PhoneQuery.WEB_PHOTO_ID);
                    info.photoUri =
                            UriUtils.parseUriOrNull(phonesCursor.getString(PhoneQuery.WEB_PHOTO_URI));
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
        ContactInfo info = lookupContactFromUri(mContext, uri);
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = formatPhoneNumber(number, null, countryIso);
        }
        return info;
    }

    @NonNull
    private static String createEmailLookupClause(final String emailAddress) {
        final StringBuilder sb = new StringBuilder();
        sb.append(ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ");
        DatabaseUtils.appendEscapedSQLString(sb, emailAddress + '%');
        sb.append(" AND " + ContactsContract.CommonDataKinds.Email.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'");

        return sb.toString();
    }

    private ContactInfo queryContactInfoForEmailAddress(String address) {
        final ContactInfo info;
        String selection = createEmailLookupClause(address);
        Cursor emailCursor =
                mContext.getContentResolver().query(ContactsContract.Data.CONTENT_URI, PhoneQuery._PROJECTION_EMAIL, selection, null, null);

        if (emailCursor != null) {
            try {
                if (emailCursor.moveToFirst()) {
                    info = new ContactInfo();
                    long contactId = emailCursor.getLong(PhoneQuery.EMAIL_PERSON_ID);
                    String lookupKey = emailCursor.getString(PhoneQuery.EMAIL_LOOKUP_KEY);
                    info.lookupKey = lookupKey;
                    info.lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    info.name = emailCursor.getString(PhoneQuery.EMAIL_NAME);
                    info.type = emailCursor.getInt(PhoneQuery.EMAIL_PHONE_TYPE);
                    info.label = emailCursor.getString(PhoneQuery.EMAIL_LABEL);
                    info.number = emailCursor.getString(PhoneQuery.EMAIL_ADDRESS);
                    info.normalizedNumber = null;
                    info.photoId = emailCursor.getLong(PhoneQuery.EMAIL_PHOTO_ID);
                    info.photoUri =
                            UriUtils.parseUriOrNull(emailCursor.getString(PhoneQuery.EMAIL_PHOTO_URI));
                    info.formattedNumber = null;
                }
                else {
                    info = ContactInfo.EMPTY;
                }
            } finally {
                emailCursor.close();
            }
        } else {
            // Failed to fetch the data, ignore this request.
            info = null;
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

    /**
     * Parses the given URI to determine the original lookup key of the contact.
     */
    public static String getLookupKeyFromUri(Uri lookupUri) {
        // Would be nice to be able to persist the lookup key somehow to avoid having to parse
        // the uri entirely just to retrieve the lookup key, but every uri is already parsed
        // once anyway to check if it is an encoded JSON uri, so this has negligible effect
        // on performance.
        if (lookupUri != null && !UriUtils.isEncodedContactUri(lookupUri)) {
            final List<String> segments = lookupUri.getPathSegments();
            // This returns the third path segment of the uri, where the lookup key is located.
            // See {@link android.provider.ContactsContract.Contacts#CONTENT_LOOKUP_URI}.
            return (segments.size() < 3) ? null : Uri.encode(segments.get(2));
        } else {
            return null;
        }
    }

    /**
     * Given a contact's sourceType, return true if the contact is a business
     *
     * @param sourceType sourceType of the contact. This is usually populated by
     *        {@link #mCachedNumberLookupService}.
     */
//    public boolean isBusiness(int sourceType) {
//        return mCachedNumberLookupService != null
//                && mCachedNumberLookupService.isBusiness(sourceType);
//    }

    /**
     * This function looks at a contact's source and determines if the user can
     * mark caller ids from this source as invalid.
     *
     * @param sourceType The source type to be checked
     * @param objectId The ID of the Contact object.
     * @return true if contacts from this source can be marked with an invalid caller id
     */
//    public boolean canReportAsInvalid(int sourceType, String objectId) {
//        return mCachedNumberLookupService != null
//                && mCachedNumberLookupService.canReportAsInvalid(sourceType, objectId);
//    }
}
