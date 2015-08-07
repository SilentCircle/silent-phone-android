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
 * limitations under the License
 */
package com.silentcircle.contacts;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.ContactsContract;

import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

/**
 * Used to create {@link android.support.v4.content.CursorLoader}s to load different groups of {@link com.silentcircle.contacts.list.ContactTileView}s
 */
public final class ContactTileLoaderFactory {

    public final static int CONTACT_ID = 0;
    public final static int DISPLAY_NAME = 1;
    public final static int STARRED = 2;
    public final static int PHOTO_URI = 3;

    // Only used for StrequentPhoneOnlyLoader
    public final static int PHONE_NUMBER = 4;
    public final static int PHONE_NUMBER_TYPE = 5;
    public final static int PHONE_NUMBER_LABEL = 6;
    public final static int IS_DEFAULT_NUMBER = 7;
    public final static int PINNED = 8;
    // The _ID field returned for strequent items actually contains data._id instead of
    // contacts._id because the query is performed on the data table. In order to obtain the
    // contact id for strequent items, we thus have to use Phone.contact_id instead.
    public final static int CONTACT_ID_FOR_DATA = 9;

    private static final String[] COLUMNS = new String[] {
        RawContacts._ID, // ..........................................0
        RawContacts.DISPLAY_NAME, // .................................1
        RawContacts.STARRED, // ......................................2
        RawContacts.PHOTO_URI, // ....................................3
    };

    /**
     * Projection used for the {@ link Contacts#CONTENT_STREQUENT_URI}
     * query when {@ link ContactsContract#STREQUENT_PHONE_ONLY} flag
     * is set to true. The main difference is the lack of presence
     * and status data and the addition of phone number and label.
     */
    private static final String[] COLUMNS_PHONE_ONLY = new String[] {
        RawContacts._ID, // ..........................................0
        RawContacts.DISPLAY_NAME, // .................................1
        RawContacts.STARRED, // ......................................2
        RawContacts.PHOTO_URI, // ....................................3
        Phone.NUMBER, // .............................................4
        Phone.TYPE, // ...............................................5
        Phone.LABEL, // ..............................................6
        Phone.IS_SUPER_PRIMARY, //..............   ...................7
        RawContacts.PINNED, // .......................................8
        Phone.RAW_CONTACT_ID // ......................................9
    };

    public static CursorLoader createStrequentLoader(Context context) {
        return new CursorLoader(context, RawContacts.CONTENT_STREQUENT_URI, COLUMNS, null, null, null);
    }

    public static CursorLoader createStrequentPhoneOnlyLoader(Context context) {
        Uri uri = RawContacts.CONTENT_STREQUENT_URI.buildUpon()
                .appendQueryParameter(ScContactsContract.STREQUENT_PHONE_ONLY, "true").build();

        return new CursorLoader(context, uri, COLUMNS_PHONE_ONLY, null, null, null);
    }

    public static CursorLoader createStarredLoader(Context context) {
        return new CursorLoader(context, RawContacts.CONTENT_URI, COLUMNS,
                RawContacts.STARRED + "=?", new String[]{"1"}, RawContacts.DISPLAY_NAME + " ASC");
    }

    public static CursorLoader createFrequentLoader(Context context) {
        return new CursorLoader(context, RawContacts.CONTENT_FREQUENT_URI, COLUMNS,
                 RawContacts.STARRED + "=?", new String[]{"0"}, null);
    }
}
