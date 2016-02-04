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

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.text.TextUtils;

import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;

import java.util.Locale;

/**
 * Split and join {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.StructuredPostal} fields.
 */
@SuppressLint("DefaultLocale")
public class PostalSplitter {
    private static final String JAPANESE_LANGUAGE = Locale.JAPANESE.getLanguage().toLowerCase();

    private final Locale mLocale;

    public static class Postal {
        public String street;
        public String pobox;
        public String neighborhood;
        public String city;
        public String region;
        public String postcode;
        public String country;

        public void fromValues(ContentValues values) {
            street = values.getAsString(StructuredPostal.STREET);
            pobox = values.getAsString(StructuredPostal.POBOX);
            neighborhood = values.getAsString(StructuredPostal.NEIGHBORHOOD);
            city = values.getAsString(StructuredPostal.CITY);
            region = values.getAsString(StructuredPostal.REGION);
            postcode = values.getAsString(StructuredPostal.POSTCODE);
            country = values.getAsString(StructuredPostal.COUNTRY);
        }

        public void toValues(ContentValues values) {
            values.put(StructuredPostal.STREET, street);
            values.put(StructuredPostal.POBOX, pobox);
            values.put(StructuredPostal.NEIGHBORHOOD, neighborhood);
            values.put(StructuredPostal.CITY, city);
            values.put(StructuredPostal.REGION, region);
            values.put(StructuredPostal.POSTCODE, postcode);
            values.put(StructuredPostal.COUNTRY, country);
        }
    }

    public PostalSplitter(Locale locale) {
        mLocale = locale;
    }

    /**
     * Parses {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.StructuredPostal#FORMATTED_ADDRESS} and returns parsed
     * components in the {@link Postal} object.
     */
    public void split(Postal postal, String formattedAddress) {
        if (!TextUtils.isEmpty(formattedAddress)) {
            postal.street = formattedAddress;
        }
    }

    private static final String NEWLINE = "\n";
    private static final String COMMA = ",";
    private static final String SPACE = " ";

    /**
     * Flattens the given {@link Postal} into a single field, usually for
     * storage in {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.StructuredPostal#FORMATTED_ADDRESS}.
     */
    public String join(Postal postal) {
        final String[] values = new String[] {
                postal.street, postal.pobox, postal.neighborhood, postal.city,
                postal.region, postal.postcode, postal.country
        };
        // TODO: split off to handle various locales
        if (mLocale != null &&
                JAPANESE_LANGUAGE.equals(mLocale.getLanguage()) &&
                !arePrintableAsciiOnly(values)) {
            return joinJaJp(postal);
        } else {
            return joinEnUs(postal);
        }
    }

    private String joinJaJp(final Postal postal) {
        final boolean hasStreet = !TextUtils.isEmpty(postal.street);
        final boolean hasPobox = !TextUtils.isEmpty(postal.pobox);
        final boolean hasNeighborhood = !TextUtils.isEmpty(postal.neighborhood);
        final boolean hasCity = !TextUtils.isEmpty(postal.city);
        final boolean hasRegion = !TextUtils.isEmpty(postal.region);
        final boolean hasPostcode = !TextUtils.isEmpty(postal.postcode);
        final boolean hasCountry = !TextUtils.isEmpty(postal.country);

        // First block: [country][ ][postcode]
        // Second block: [region][ ][city][ ][neighborhood]
        // Third block: [street][ ][pobox]

        final StringBuilder builder = new StringBuilder();

        final boolean hasFirstBlock = hasCountry || hasPostcode;
        final boolean hasSecondBlock = hasRegion || hasCity || hasNeighborhood;
        final boolean hasThirdBlock = hasStreet || hasPobox;

        if (hasFirstBlock) {
            if (hasCountry) {
                builder.append(postal.country);
            }
            if (hasPostcode) {
                if (hasCountry) builder.append(SPACE);
                builder.append(postal.postcode);
            }
        }

        if (hasSecondBlock) {
            if (hasFirstBlock) {
                builder.append(NEWLINE);
            }
            if (hasRegion) {
                builder.append(postal.region);
            }
            if (hasCity) {
                if (hasRegion) builder.append(SPACE);
                builder.append(postal.city);
            }
            if (hasNeighborhood) {
                if (hasRegion || hasCity) builder.append(SPACE);
                builder.append(postal.neighborhood);
            }
        }

        if (hasThirdBlock) {
            if (hasFirstBlock || hasSecondBlock) {
                builder.append(NEWLINE);
            }
            if (hasStreet) {
                builder.append(postal.street);
            }
            if (hasPobox) {
                if (hasStreet) builder.append(SPACE);
                builder.append(postal.pobox);
            }
        }

        if (builder.length() > 0) {
            return builder.toString();
        } else {
            return null;
        }
    }

    private String joinEnUs(final Postal postal) {
        final boolean hasStreet = !TextUtils.isEmpty(postal.street);
        final boolean hasPobox = !TextUtils.isEmpty(postal.pobox);
        final boolean hasNeighborhood = !TextUtils.isEmpty(postal.neighborhood);
        final boolean hasCity = !TextUtils.isEmpty(postal.city);
        final boolean hasRegion = !TextUtils.isEmpty(postal.region);
        final boolean hasPostcode = !TextUtils.isEmpty(postal.postcode);
        final boolean hasCountry = !TextUtils.isEmpty(postal.country);

        // First block: [street][\n][pobox][\n][neighborhood]
        // Second block: [city][, ][region][ ][postcode]
        // Third block: [country]

        final StringBuilder builder = new StringBuilder();

        final boolean hasFirstBlock = hasStreet || hasPobox || hasNeighborhood;
        final boolean hasSecondBlock = hasCity || hasRegion || hasPostcode;
        final boolean hasThirdBlock = hasCountry;

        if (hasFirstBlock) {
            if (hasStreet) {
                builder.append(postal.street);
            }
            if (hasPobox) {
                if (hasStreet) builder.append(NEWLINE);
                builder.append(postal.pobox);
            }
            if (hasNeighborhood) {
                if (hasStreet || hasPobox) builder.append(NEWLINE);
                builder.append(postal.neighborhood);
            }
        }

        if (hasSecondBlock) {
            if (hasFirstBlock) {
                builder.append(NEWLINE);
            }
            if (hasCity) {
                builder.append(postal.city);
            }
            if (hasRegion) {
                if (hasCity) builder.append(COMMA + SPACE);
                builder.append(postal.region);
            }
            if (hasPostcode) {
                if (hasCity || hasRegion) builder.append(SPACE);
                builder.append(postal.postcode);
            }
        }

        if (hasThirdBlock) {
            if (hasFirstBlock || hasSecondBlock) {
                builder.append(NEWLINE);
            }
            if (hasCountry) {
                builder.append(postal.country);
            }
        }

        if (builder.length() > 0) {
            return builder.toString();
        } else {
            return null;
        }
    }

    private static boolean arePrintableAsciiOnly(final String[] values) {
        if (values == null) {
            return true;
        }
//        for (final String value : values) {
//            if (TextUtils.isEmpty(value)) {
//                continue;
//            }
//            if (!TextUtils.isPrintableAsciiOnly(value)) {
//                return false;
//            }
//        }
        return false; // TODO -- check for isPrintableAsciiOnly(...)
    }
}
