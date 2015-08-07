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

import com.google.common.annotations.VisibleForTesting;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;

/**
 * The details of a phone call to be shown in the UI.
 */
public class PhoneCallDetails {
    /** The number of the other party involved in the call. */
    public final CharSequence number;
    /** The number presenting rules set by the network, e.g., {@link android.provider.CallLog.Calls#PRESENTATION_ALLOWED} */
    public final int numberPresentation;
    /** The formatted version of {@link #number}. */
    public final CharSequence formattedNumber;
    /** The country corresponding with the phone number. */
    public final String countryIso;
    /** The geocoded location for the phone number. */
    public final String geocode;
    /**
     * The type of calls, as defined in the call log table, e.g., {@link android.provider.CallLog.Calls#INCOMING_TYPE}.
     * <p>
     * There might be multiple types if this represents a set of entries grouped together.
     */
    public final int[] callTypes;
    /** The date of the call, in milliseconds since the epoch. */
    public final long date;
    /** The duration of the call in milliseconds, or 0 for missed calls. */
    public final long duration;
    /** The name of the contact, or the empty string. */
    public final CharSequence name;
    /** The type of phone, e.g., {@link android.provider.ContactsContract.CommonDataKinds.Phone#TYPE_HOME}, 0 if not available. */
    public final int numberType;
    /** The custom label associated with the phone number in the contact, or the empty string. */
    public final CharSequence numberLabel;
    /** The URI of the contact associated with this phone call. */
    public final Uri contactUri;
    /**
     * The photo URI of the picture of the contact that is associated with this phone call or
     * null if there is none.
     * <p>
     * This is meant to store the high-res photo only.
     */
    public final Uri photoUri;
    /**
     * The source type of the contact associated with this call.
     */
    public final int sourceType;
    /**
     * The unique identifier for the account associated with the call.
     */
    public final String accountLabel;
    /**
     * The icon for the account associated with the call.
     */
    public final Drawable accountIcon;
    /**
     * Features applicable to this call.
     */
    public final int features;
    /**
     * Total data usage for this call.
     */
    public final Long dataUsage;
    /**
     * Voicemail transcription
     */
    public final String transcription;

    /**
     * Create the details for a call, with empty defaults specified for extra fields that are
     * not necessary for testing.
     */
    @VisibleForTesting
    public PhoneCallDetails(CharSequence number, int numberPresentation,
            CharSequence formattedNumber, String countryIso, String geocode,
            int[] callTypes, long date, long duration) {
        this (number, numberPresentation, formattedNumber, countryIso, geocode,
        callTypes, date, duration, "", 0, "", null, null, 0, null, null, 0, null, null);
    }

    /** Create the details for a call with a number not associated with a contact. */
    public PhoneCallDetails(CharSequence number, int numberPresentation,
            CharSequence formattedNumber, String countryIso, String geocode,
            int[] callTypes, long date, long duration, String accountLabel, Drawable accountIcon,
            int features, Long dataUsage, String transcription) {
        this(number, numberPresentation, formattedNumber, countryIso, geocode,
                callTypes, date, duration, "", 0, "", null, null, 0, accountLabel, accountIcon,
                features, dataUsage, transcription);
    }

    /** Create the details for a call with a number associated with a contact. */
    public PhoneCallDetails(CharSequence number, int numberPresentation,
            CharSequence formattedNumber, String countryIso, String geocode,
            int[] callTypes, long date, long duration, CharSequence name,
            int numberType, CharSequence numberLabel, Uri contactUri,
            Uri photoUri, int sourceType, String accountLabel, Drawable accountIcon, int features,
            Long dataUsage, String transcription) {
        this.number = number;
        this.numberPresentation = numberPresentation;
        this.formattedNumber = formattedNumber;
        this.countryIso = countryIso;
        this.geocode = geocode;
        this.callTypes = callTypes;
        this.date = date;
        this.duration = duration;
        this.name = name;
        this.numberType = numberType;
        this.numberLabel = numberLabel;
        this.contactUri = contactUri;
        this.photoUri = photoUri;
        this.sourceType = sourceType;
        this.accountLabel = accountLabel;
        this.accountIcon = accountIcon;
        this.features = features;
        this.dataUsage = dataUsage;
        this.transcription = transcription;
    }
}
