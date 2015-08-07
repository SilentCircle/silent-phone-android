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
 * The implementation re-uses code from the original Android ContactsProvider test project.
 */

package com.silentcircle.contacts.providers.aggregation;

import android.util.Log;

import com.silentcircle.contacts.providers.NameSplitter;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DataColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.RawContactsColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.contacts.providers.ScContactsProvider;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;


public class SimpleRawContactAggregator {

    private static final String TAG = "SimpleRawContactAggregator";

    private ScContactsProvider mContactsProvider;
    private ScContactsDatabaseHelper mDbHelper;

    private SQLiteStatement mPhotoIdUpdate;

    private String[] mSelectionArgs1 = new String[1];

    public SimpleRawContactAggregator(ScContactsProvider contactsProvider,
            ScContactsDatabaseHelper contactsDatabaseHelper, NameSplitter nameSplitter ) {

        mContactsProvider = contactsProvider;
        mDbHelper = contactsDatabaseHelper;

        SQLiteDatabase db = mDbHelper.getDatabase(false);

        mPhotoIdUpdate = db.compileStatement(
                "UPDATE " + Tables.RAW_CONTACTS +
                " SET " + RawContacts.PHOTO_ID + "=?," + RawContacts.PHOTO_FILE_ID + "=? " +
                " WHERE " + RawContactsColumns.CONCRETE_ID + "=?");

    }

    private interface PhotoIdQuery {
        final String[] COLUMNS = new String[] {
            DataColumns.CONCRETE_ID,
            Photo.PHOTO_FILE_ID,
        };

        int DATA_ID = 0;
        int PHOTO_FILE_ID = 1;
    }

    /**
     * Updates the {@link com.silentcircle.silentcontacts.ScContactsContract.RawContacts#HAS_PHONE_NUMBER} flag for the aggregate contact containing the
     * specified raw contact.
     */
    public void updateHasPhoneNumber(SQLiteDatabase db, long rawContactId) {

        final SQLiteStatement hasPhoneNumberUpdate = db.compileStatement(
                "UPDATE " + Tables.RAW_CONTACTS +
                " SET " + RawContacts.HAS_PHONE_NUMBER + "="
                        + "(SELECT (CASE WHEN COUNT(*)=0 THEN 0 ELSE 1 END)"
                        + " FROM " + Tables.DATA_JOIN_RAW_CONTACTS
                        + " WHERE " + DataColumns.MIMETYPE_ID + "=?"
                                + " AND " + Phone.NUMBER + " NOT NULL)" +
                " WHERE " + RawContacts._ID + "=?");
        try {
            hasPhoneNumberUpdate.bindLong(1, mDbHelper.getMimeTypeId(Phone.CONTENT_ITEM_TYPE));
            hasPhoneNumberUpdate.bindLong(2, rawContactId);
            hasPhoneNumberUpdate.execute();
        } finally {
            hasPhoneNumberUpdate.close();
        }
    }

    public void updatePhotoId(SQLiteDatabase db, long rawContactId) {

        long bestPhotoId = -1;
        long bestPhotoFileId = 0;

        long photoMimeType = mDbHelper.getMimeTypeId(Photo.CONTENT_ITEM_TYPE);

        String tables = Tables.RAW_CONTACTS
                + " JOIN " + Tables.DATA + " ON("
                + DataColumns.CONCRETE_RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID
                + " AND (" + DataColumns.MIMETYPE_ID + "=" + photoMimeType + " AND " + Photo.PHOTO + " NOT NULL))";

        mSelectionArgs1[0] = String.valueOf(rawContactId);
        final Cursor c = db.query(tables, PhotoIdQuery.COLUMNS, RawContactsColumns.CONCRETE_ID + "=?", mSelectionArgs1, null, null, null);
        try {
            while (c.moveToNext()) {
                long dataId = c.getLong(PhotoIdQuery.DATA_ID);
                long photoFileId = c.getLong(PhotoIdQuery.PHOTO_FILE_ID);

                bestPhotoId = dataId;
                bestPhotoFileId = photoFileId;
                break;
             }
        } finally {
            c.close();
        }

        if (bestPhotoId == -1) {
            mPhotoIdUpdate.bindNull(1);
        }
        else {
            mPhotoIdUpdate.bindLong(1, bestPhotoId);
        }

        if (bestPhotoFileId == 0) {
            mPhotoIdUpdate.bindNull(2);
        }
        else {
            mPhotoIdUpdate.bindLong(2, bestPhotoFileId);
        }
        mPhotoIdUpdate.bindLong(3, rawContactId);
        mPhotoIdUpdate.execute();
    }
}
