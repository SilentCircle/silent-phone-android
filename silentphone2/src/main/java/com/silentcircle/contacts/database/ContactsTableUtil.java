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

package com.silentcircle.contacts.database;

import android.content.ContentValues;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.contacts.utils.Clock;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import net.sqlcipher.database.SQLiteDatabase;

/**
 * Methods for operating on the raw contacts table.
 * 
 * Silent Contacts works with Raw contacts only, thus the functions for aggregated contacts
 * are not used and required.
 *
 * Modified for Silent Contacts by Werner 
 */
public class ContactsTableUtil {

    /**
     * Drop indexes if present.  Create indexes.
     *
     * @param db The sqlite database instance.
     */
//    public static void createIndexes(SQLiteDatabase db) {
//        final String table = Tables.CONTACTS;
//
//        db.execSQL("CREATE INDEX contacts_has_phone_index ON " + table + " (" +
//                Contacts.HAS_PHONE_NUMBER +
//                ");");
//
//        db.execSQL("CREATE INDEX contacts_name_raw_contact_id_index ON " + table + " (" +
//                Contacts.NAME_RAW_CONTACT_ID +
//                ");");
//
//        db.execSQL(MoreDatabaseUtils.buildCreateIndexSql(table,
//                Contacts.CONTACT_LAST_UPDATED_TIMESTAMP));
//    }

    public static void updateContactLastUpdateByContactId(SQLiteDatabase db, long contactId) {
        final ContentValues values = new ContentValues();
        values.put(RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP, Clock.getInstance().currentTimeMillis());
        db.update(Tables.RAW_CONTACTS, values, RawContacts._ID + " = ?", new String[] {String.valueOf(contactId)});
    }

//    /**
//     * Refreshes the last updated timestamp of the contact with the current time.
//     *
//     * @param db The sqlite database instance.
//     * @param rawContactIds A set of raw contacts ids to refresh the contact for.
//     */
//    public static void updateContactLastUpdateByRawContactId(SQLiteDatabase db,
//            Set<Long> rawContactIds) {
//        if (rawContactIds.isEmpty()) {
//            return;
//        }
//
//        db.execSQL(buildUpdateLastUpdateSql(rawContactIds));
//    }
//
//    /**
//     * Build a sql to update the last updated timestamp for contacts.
//     *
//     * @param rawContactIds The raw contact ids that contacts should be updated for.
//     * @return The update sql statement.
//     */
//    private static String buildUpdateLastUpdateSql(Set<Long> rawContactIds) {
//        // Not using bind args here due to sqlite bind arg size limit.  Large number of bind args
//        // will cause a sqlite error:
//        //     android.database.sqlite.SQLiteException: too many SQL variables (code 1)
//        // Sql injection is not possible because input is a set of Long.  If any part of the sql
//        // is built with user input strings, then this must be converted to using bind args.
//        final String sql = "UPDATE " + Tables.RAW_CONTACTS
//                + " SET " + RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP + " = "
//                + Clock.getInstance().currentTimeMillis()
//                + " WHERE " + RawContacts._ID + " IN ( "
//                + "  SELECT " + ScContactsContract.RawContacts.CONTACT_ID
//                + "  FROM " + Tables.RAW_CONTACTS
//                + "  WHERE " + ScContactsContract.RawContacts._ID
//                + " IN (" + TextUtils.join(",", rawContactIds) + ") "
//                + ")";
//        return sql;
//    }

    /**
     * Delete a contact identified by the contact id.
     *
     * @param db The sqlite database instance.
     * @param contactId The contact id to delete.
     * @return The number of records deleted.
     */
    public static int deleteContact(SQLiteDatabase db, long contactId) {
        DeletedContactsTableUtil.insertDeletedContact(db, contactId);
        return db.delete(Tables.RAW_CONTACTS, RawContacts._ID + " = ?", new String[]{contactId + ""});
    }

    /**
     * Delete the aggregate contact if it has no constituent raw contacts other than the supplied
     * one.
     */
//    public static void deleteContactIfSingleton(SQLiteDatabase db, long rawContactId) {
//        // This query will find a contact id if the contact has a raw contacts other than the one
//        // passed in.
//        final String sql = "select " + ScContactsContract.RawContacts.CONTACT_ID + ", count(1)"
//                + " from " + Tables.RAW_CONTACTS
//                + " where " + ScContactsContract.RawContacts.CONTACT_ID + " ="
//                + "  (select " + ScContactsContract.RawContacts.CONTACT_ID
//                + "   from " + Tables.RAW_CONTACTS
//                + "   where " + ScContactsContract.RawContacts._ID + " = ?)"
//                + " group by " + ScContactsContract.RawContacts.CONTACT_ID;
//        final Cursor cursor = db.rawQuery(sql, new String[]{rawContactId + ""});
//        try {
//            if (cursor.moveToNext()) {
//                long contactId = cursor.getLong(0);
//                long numRawContacts = cursor.getLong(1);
//
//                if (numRawContacts == 1) {
//                    // Only one raw contact, we can delete the parent.
//                    deleteContact(db, contactId);
//                }
//            }
//        } finally {
//            MoreCloseables.closeQuietly(cursor);
//        }
//    }
}
