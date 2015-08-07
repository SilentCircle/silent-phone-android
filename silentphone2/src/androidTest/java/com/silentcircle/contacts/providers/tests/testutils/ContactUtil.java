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

package com.silentcircle.contacts.providers.tests.testutils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.silentcircle.silentcontacts2.ScContactsContract;

/**
 * Convenience methods for operating on the Contacts table.
 */
public class ContactUtil {

    // primitive value used when record is not found.
    public static final long NOT_FOUND = -1;


    private static final Uri URI = ScContactsContract.RawContacts.CONTENT_URI;

    public static void update(ContentResolver resolver, long contactId,
            ContentValues values) {
        Uri uri = ContentUris.withAppendedId(URI, contactId);
        resolver.update(uri, values, null, null);
    }

    public static void delete(ContentResolver resolver, long contactId) {
        Uri uri = ContentUris.withAppendedId(URI, contactId);
        resolver.delete(uri, null, null);
    }

    public static boolean recordExistsForContactId(ContentResolver resolver, long contactId) {
        String[] projection = new String[]{
                ScContactsContract.RawContacts._ID
        };
        Uri uri = ContentUris.withAppendedId(URI, contactId);
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        if (cursor.moveToNext()) {
            return true;
        }
        return false;
    }

    public static long queryContactLastUpdatedTimestamp(ContentResolver resolver, long contactId) {
        String[] projection = new String[]{
                ScContactsContract.RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP
        };

        Uri uri = ContentUris.withAppendedId(ScContactsContract.RawContacts.CONTENT_URI, contactId);
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        try {
            if (cursor.moveToNext()) {
                return cursor.getLong(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return NOT_FOUND;
    }
}
