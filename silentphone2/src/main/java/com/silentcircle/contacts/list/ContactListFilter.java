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
 * Copyright (C) 2010 The Android Open Source Project
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

package com.silentcircle.contacts.list;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Contact list filter parameters.
 */
public final class ContactListFilter implements Comparable<ContactListFilter>, Parcelable {

    public static final int FILTER_TYPE_DEFAULT = -1;
    public static final int FILTER_TYPE_CUSTOM = -3;
    public static final int FILTER_TYPE_STARRED = -4;
    public static final int FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY = -5;
    public static final int FILTER_TYPE_SINGLE_CONTACT = -6;

    public static final int FILTER_TYPE_ACCOUNT = 0;

    /**
     * Obsolete filter which had been used in Honeycomb. This may be stored in
     * {@link android.content.SharedPreferences}, but should be replaced with ALL filter when it is found.
     *
     * TODO: "group" filter and relevant variables are all obsolete. Remove them.
     */
    private static final String KEY_FILTER_TYPE = "filter.type";


    public final int filterType;
    public final Drawable icon;
    private String mId;

    public ContactListFilter(int filterType, Drawable icon) {
        this.filterType = filterType;
        this.icon = icon;
    }

    public static ContactListFilter createFilterWithType(int filterType) {
        return new ContactListFilter(filterType, null);
    }

    /**
     * Returns true if this filter is based on data and may become invalid over time.
     */
    @SuppressWarnings("unused")
    public boolean isValidationRequired() {
        return filterType == FILTER_TYPE_ACCOUNT;
    }

    @Override
    public String toString() {
        switch (filterType) {
            case FILTER_TYPE_DEFAULT:
                return "default";
            case FILTER_TYPE_CUSTOM:
                return "custom";
            case FILTER_TYPE_STARRED:
                return "starred";
            case FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                return "with_phones";
            case FILTER_TYPE_SINGLE_CONTACT:
                return "single";
        }
        return super.toString();
    }

    @Override
    public int compareTo(ContactListFilter another) {
        return filterType - another.filterType;
    }

    @Override
    public int hashCode() {
        return filterType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ContactListFilter)) {
            return false;
        }

        ContactListFilter otherFilter = (ContactListFilter) other;
        return filterType == otherFilter.filterType;

    }

    /**
     * Store the given {@link ContactListFilter} to preferences. If the requested filter is
     * of type {@link #FILTER_TYPE_SINGLE_CONTACT} then do not save it to preferences because
     * it is a temporary state.
     */
    public static void storeToPreferences(SharedPreferences prefs, ContactListFilter filter) {
        if (filter != null && filter.filterType == FILTER_TYPE_SINGLE_CONTACT) {
            return;
        }
        prefs.edit()
            .putInt(KEY_FILTER_TYPE, filter == null ? FILTER_TYPE_DEFAULT : filter.filterType)
            .apply();
    }

    /**
     * Try to obtain ContactListFilter object saved in SharedPreference.
     * If there's no info there, return ALL filter instead.
     */
    public static ContactListFilter restoreDefaultPreferences(SharedPreferences prefs) {
        ContactListFilter filter = restoreFromPreferences(prefs);
        if (filter == null) {
            filter = ContactListFilter.createFilterWithType(FILTER_TYPE_DEFAULT);
        }
        return filter;
    }

    private static ContactListFilter restoreFromPreferences(SharedPreferences prefs) {
        int filterType = prefs.getInt(KEY_FILTER_TYPE, FILTER_TYPE_DEFAULT);
        if (filterType == FILTER_TYPE_DEFAULT) {
            return null;
        }
        return new ContactListFilter(filterType, null);
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(filterType);
    }

    public static final Creator<ContactListFilter> CREATOR = new Creator<ContactListFilter>() {
        @Override
        public ContactListFilter createFromParcel(Parcel source) {
            int filterType = source.readInt();
            return new ContactListFilter(filterType, null);
        }

        @Override
        public ContactListFilter[] newArray(int size) {
            return new ContactListFilter[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns a string that can be used as a stable persistent identifier for this filter.
     */
    public String getId() {
        if (mId == null) {
            mId = String.valueOf(filterType);
        }
        return mId;
    }

    /**
     * Adds the account query parameters to the given {@code uriBuilder}.
     *
     * @throws IllegalStateException if the filter type is not {@link #FILTER_TYPE_ACCOUNT}.
     */
    public Uri.Builder addAccountQueryParameterToUrl(Uri.Builder uriBuilder) {
        if (filterType != FILTER_TYPE_ACCOUNT) {
            throw new IllegalStateException("filterType must be FILTER_TYPE_ACCOUNT");
        }
        return uriBuilder;
    }

    @SuppressWarnings("unused")
    public String toDebugString() {
        return ("[filter type: " + filterType + " (" + filterTypeToString(filterType) + ")") + ", icon: " + icon + "]";
    }

    public static String filterTypeToString(int filterType) {
        switch (filterType) {
            case FILTER_TYPE_DEFAULT:
                return "FILTER_TYPE_DEFAULT";
            case FILTER_TYPE_CUSTOM:
                return "FILTER_TYPE_CUSTOM";
            case FILTER_TYPE_STARRED:
                return "FILTER_TYPE_STARRED";
            case FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                return "FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY";
            case FILTER_TYPE_SINGLE_CONTACT:
                return "FILTER_TYPE_SINGLE_CONTACT";
            case FILTER_TYPE_ACCOUNT:
                return "FILTER_TYPE_ACCOUNT";
            default:
                return "(unknown)";
        }
    }
}
