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

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.utils.Clock;
import com.silentcircle.silentcontacts2.ScContactsContract;

import net.sqlcipher.database.SQLiteDatabase;

/**
 * Methods for operating on the deleted_contacts table.
 */
public class DeletedContactsTableUtil {

    /**
     * Create deleted_contacts tables and indexes.
     *
     * @param db The sqlite database instance.
     */
    public static void create(SQLiteDatabase db) {
        // Deleted contacts log
        db.execSQL("CREATE TABLE " + ScContactsDatabaseHelper.Tables.DELETED_CONTACTS + " (" +
                ScContactsContract.DeletedContacts.CONTACT_ID + " INTEGER PRIMARY KEY," +
                ScContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP +
                " INTEGER NOT NULL default 0"
                + ");");

        db.execSQL(MoreDatabaseUtils.buildCreateIndexSql(
                ScContactsDatabaseHelper.Tables.DELETED_CONTACTS,
                ScContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP));
    }

    /**
     * Inserts a deleted contact log record.
     *
     * @param db The SQLiteDatabase instance.
     * @param contactId The contact id to insert.
     * @return The row id
     */
    public static long insertDeletedContact(SQLiteDatabase db, long contactId) {
        ContentValues values = new ContentValues();
        values.put(ScContactsContract.DeletedContacts.CONTACT_ID, contactId);
        values.put(ScContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP,
                Clock.getInstance().currentTimeMillis());
        // a.k.a upsert
        return db.insertWithOnConflict(ScContactsDatabaseHelper.Tables.DELETED_CONTACTS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Deletes old log records.
     *
     * @param db The database instance to use.
     */
    public static int deleteOldLogs(SQLiteDatabase db) {

        long time = Clock.getInstance().currentTimeMillis() -
                ScContactsContract.DeletedContacts.DAYS_KEPT_MILLISECONDS;

        String[] args = new String[]{time + ""};

        return db.delete(ScContactsDatabaseHelper.Tables.DELETED_CONTACTS,
                ScContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP + " < ?", args);
    }
}
