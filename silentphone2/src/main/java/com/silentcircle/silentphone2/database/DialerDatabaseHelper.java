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
 * limitations under the License.
 */

package com.silentcircle.silentphone2.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.silentcircle.common.util.StopWatch;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.silentcontacts2.ScBaseColumns;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Callable;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.dialpad.SmartDialNameMatcher;
import com.silentcircle.silentphone2.dialpad.SmartDialPrefix;

import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Database helper for smart dial. Designed as a singleton to make sure there is
 * only one access point to the database. Provides methods to maintain, update,
 * and query the database.
 */
public class DialerDatabaseHelper {
    private static final String TAG = "DialerDatabaseHelper";
    private static final boolean DEBUG = false;

    private static DialerDatabaseHelper sSingleton = null;

    private static final Object mLock = new Object();
    private static final AtomicBoolean sInUpdate = new AtomicBoolean(false);
    private final Context mContext;

    /**
     * Saves the last update time of smart dial databases to shared preferences.
     */
    private static final String DATABASE_LAST_CREATED_SHARED_PREF = "com.android.dialer";
    private static final String LAST_UPDATED_MILLIS = "last_updated_millis";

    private static final int MAX_ENTRIES = 20;

    public interface Tables {
        /** Saves all possible prefixes to refer to a contacts.*/
        String PREFIX_TABLE = "prefix_table";
    }

    public interface PrefixColumns extends ScBaseColumns {
        String PREFIX = "prefix";
        String CONTACT_ID = "contact_id";
    }

    /** Query options for querying the contact database.*/
    public interface PhoneQuery {
        Uri URI = Callable.CONTENT_URI.buildUpon().
               appendQueryParameter(ScContactsContract.DIRECTORY_PARAM_KEY,
                       String.valueOf(Directory.DEFAULT)).
               appendQueryParameter(ScContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").
               build();

        String[] PROJECTION = new String[] {
                Phone._ID,                          // 0
                Phone.TYPE,                         // 1
                Phone.LABEL,                        // 2
                Phone.NUMBER,                       // 3
                Phone.RAW_CONTACT_ID,               // 4
//            Phone.LOOKUP_KEY,                   // 5
                Phone.DISPLAY_NAME_PRIMARY,         // 6
                Phone.PHOTO_ID,                     // 7
                Data.LAST_TIME_USED,                // 8
                Data.TIMES_USED,                    // 9
                RawContacts.STARRED,                // 10
                Data.IS_SUPER_PRIMARY,              // 11
//            RawContacts.IN_VISIBLE_GROUP,          // 12
                Data.IS_PRIMARY,                    // 12
        };

       int PHONE_ID = 0;
       int PHONE_TYPE = 1;
       int PHONE_LABEL = 2;
       int PHONE_NUMBER = 3;
       int PHONE_CONTACT_ID = 4;
//        static final int PHONE_LOOKUP_KEY = 5;
        int PHONE_DISPLAY_NAME = 5;
        int PHONE_PHOTO_ID = 6;
        int PHONE_LAST_TIME_USED = 7;
        int PHONE_TIMES_USED = 8;
        int PHONE_STARRED = 9;
        int PHONE_IS_SUPER_PRIMARY = 10;
//        static final int PHONE_IN_VISIBLE_GROUP = 12;
        int PHONE_IS_PRIMARY = 11;

        /** Selects only rows that have been updated after a certain time stamp.*/
        String SELECT_UPDATED_CLAUSE =
                Phone.CONTACT_LAST_UPDATED_TIMESTAMP + " > ?";

        String SELECTION = SELECT_UPDATED_CLAUSE;
    }

    /** Query options for querying the deleted contact database.*/
    public interface DeleteContactQuery {
        Uri URI = ScContactsContract.DeletedContacts.CONTENT_URI;

        String[] PROJECTION = new String[] {
            ScContactsContract.DeletedContacts.CONTACT_ID,                          // 0
            ScContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP,           // 1
        };

        int DELETED_CONTACT_ID = 0;
        int DELECTED_TIMESTAMP = 1;

        /** Selects only rows that have been deleted after a certain time stamp.*/
        String SELECT_UPDATED_CLAUSE =
                ScContactsContract.DeletedContacts.CONTACT_DELETED_TIMESTAMP + " > ?";
    }

    /**
     * Gets the sorting order for the smartdial table. This computes a SQL "ORDER BY" argument by
     * composing contact status and recent contact details together.
     */
    private interface SmartDialSortingOrder {
        /** Current contacts - those contacted within the last 3 days (in milliseconds) */
        long LAST_TIME_USED_CURRENT_MS = 3L * 24 * 60 * 60 * 1000;
        /** Recent contacts - those contacted within the last 30 days (in milliseconds) */
        long LAST_TIME_USED_RECENT_MS = 30L * 24 * 60 * 60 * 1000;

        /** Time since last contact. */
        String TIME_SINCE_LAST_USED_MS = "( ?1 - " + Phone.LAST_TIME_USED + ")";

        /** Contacts that have been used in the past 3 days rank higher than contacts that have
         * been used in the past 30 days, which rank higher than contacts that have not been used
         * in recent 30 days.
         */
        String SORT_BY_DATA_USAGE =
                "(CASE WHEN " + TIME_SINCE_LAST_USED_MS + " < " + LAST_TIME_USED_CURRENT_MS +
                " THEN 0 " +
                " WHEN " + TIME_SINCE_LAST_USED_MS + " < " + LAST_TIME_USED_RECENT_MS +
                " THEN 1 " +
                " ELSE 2 END)";

        /** This sort order is similar to that used by the ContactsProvider when returning a list
         * of frequently called contacts.
         */
        String SORT_ORDER =
                RawContacts.STARRED + " DESC, "
                + Phone.IS_SUPER_PRIMARY + " DESC, "
                + SORT_BY_DATA_USAGE + ", "
                + Phone.TIMES_USED + " DESC, "
                + Phone.DISPLAY_NAME_PRIMARY + ", "
                + Phone.RAW_CONTACT_ID + ", "
                + Phone.IS_PRIMARY + " DESC";
    }

    /**
     * Simple data format for a contact, containing only information needed for showing up in
     * smart dial interface.
     */
    public static class ContactNumber {
        public final long id;
        public final long dataId;
        public final String displayName;
        public final String phoneNumber;
        public final String lookupKey;
        public final long photoId;

        public ContactNumber(long id, long dataID, String displayName, String phoneNumber,
                String lookupKey, long photoId) {
            this.dataId = dataID;
            this.id = id;
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.lookupKey = lookupKey;
            this.photoId = photoId;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id, dataId, displayName, phoneNumber, lookupKey, photoId);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object instanceof ContactNumber) {
                final ContactNumber that = (ContactNumber) object;
                return Objects.equal(this.id, that.id)
                        && Objects.equal(this.dataId, that.dataId)
                        && Objects.equal(this.displayName, that.displayName)
                        && Objects.equal(this.phoneNumber, that.phoneNumber)
                        && Objects.equal(this.lookupKey, that.lookupKey)
                        && Objects.equal(this.photoId, that.photoId);
            }
            return false;
        }
    }

    /**
     * Data format for finding duplicated contacts.
     */
    private class ContactMatch {
        private final String lookupKey;
        private final long id;

        public ContactMatch(String lookupKey, long id) {
            this.lookupKey = lookupKey;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(lookupKey, id);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object instanceof ContactMatch) {
                final ContactMatch that = (ContactMatch) object;
                return Objects.equal(this.lookupKey, that.lookupKey)
                        && Objects.equal(this.id, that.id);
            }
            return false;
        }
    }

    /**
     * Access function to get the singleton instance of DialerDatabaseHelper.
     */
    public static synchronized DialerDatabaseHelper getInstance(Context context) {
        if (DEBUG) {
            Log.v(TAG, "Getting Instance");
        }
        if (sSingleton == null) {
            // Use application context instead of activity context because this is a singleton,
            // and we don't want to leak the activity if the activity is not running but the
            // dialer database helper is still doing work.
            sSingleton = new DialerDatabaseHelper(context.getApplicationContext());
        }
        return sSingleton;
    }

    /**
     * Returns a new instance for unit tests. The database will be created in memory.
     */
    @VisibleForTesting
    static DialerDatabaseHelper getNewInstanceForTest(Context context) {
        return new DialerDatabaseHelper(context);
    }

    protected DialerDatabaseHelper(Context context) {
        mContext = Preconditions.checkNotNull(context, "Context must not be null");
        resetSmartDialLastUpdatedTime();        // TODO - check resetting to zero

    }

    private void resetSmartDialLastUpdatedTime() {
        final SharedPreferences databaseLastUpdateSharedPref = mContext.getSharedPreferences(
                DATABASE_LAST_CREATED_SHARED_PREF, Context.MODE_PRIVATE);

        if (!databaseLastUpdateSharedPref.contains(LAST_UPDATED_MILLIS)) {
            final SharedPreferences.Editor editor = databaseLastUpdateSharedPref.edit();
            editor.putLong(LAST_UPDATED_MILLIS, 0).apply();
        }
    }

    /**
     * Starts the database upgrade process in the background.
     */
    public void startSmartDialUpdateThread() {
        new SmartDialUpdateAsyncTask().execute();
    }

    private class SmartDialUpdateAsyncTask extends AsyncTask {
        @Override
        protected Object doInBackground(Object[] objects) {
            if (DEBUG) {
                Log.v(TAG, "Updating database");
            }
            updateSmartDialDatabase();
            return null;
        }

        @Override
        protected void onCancelled() {
            if (DEBUG) {
                Log.v(TAG, "Updating Cancelled");
            }
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(Object o) {
            if (DEBUG) {
                Log.v(TAG, "Updating Finished");
            }
            super.onPostExecute(o);
        }
    }
    /**
     * Removes rows in the smartdial database that matches the contacts that have been deleted
     * by other apps since last update.
     *
     * @param db Database pointer to the dialer database.
     * @param last_update_time Time stamp of last update on the smartdial database
     */
    private void removeDeletedContacts(SQLiteDatabase db, String last_update_time) {
        final Cursor deletedContactCursor = mContext.getContentResolver().query(
                DeleteContactQuery.URI,
                DeleteContactQuery.PROJECTION,
                DeleteContactQuery.SELECT_UPDATED_CLAUSE,
                new String[] {last_update_time}, null);
        if (deletedContactCursor == null) {
            return;
        }

        db.beginTransaction();
        try {
            while (deletedContactCursor.moveToNext()) {
                final Long deleteContactId =
                        deletedContactCursor.getLong(DeleteContactQuery.DELETED_CONTACT_ID);
                db.delete(Tables.PREFIX_TABLE,
                        PrefixColumns.CONTACT_ID + "=" + deleteContactId, null);
            }

            db.setTransactionSuccessful();
        } finally {
            deletedContactCursor.close();
            db.endTransaction();
        }
    }

    /**
     * Removes potentially corrupted entries in the database. These contacts may be added before
     * the previous instance of the dialer was destroyed for some reason. For data integrity, we
     * delete all of them.

     * @param db Database pointer to the dialer database.
     * @param last_update_time Time stamp of last successful update of the dialer database.
     */
    private void removePotentiallyCorruptedContacts(SQLiteDatabase db, String last_update_time) {
//        db.delete(Tables.PREFIX_TABLE,
//                PrefixColumns.CONTACT_ID + " IN " +
//                "(SELECT " + SmartDialDbColumns.CONTACT_ID + " FROM " + Tables.SMARTDIAL_TABLE +
//                " WHERE " + SmartDialDbColumns.LAST_SMARTDIAL_UPDATE_TIME + " > " +
//                last_update_time + ")",
//                null);
    }

    /**
     * Removes all entries in the smartdial contact database.
     */
    @VisibleForTesting
    void removeAllContacts(SQLiteDatabase db) {
        db.delete(Tables.PREFIX_TABLE, null, null);
    }

    /**
     * Counts number of rows of the prefix table.
     */
    @VisibleForTesting
    int countPrefixTableRows(SQLiteDatabase db) {
        return (int)DatabaseUtils.longForQuery(db, "SELECT COUNT(1) FROM " + Tables.PREFIX_TABLE, null);
    }

    /**
     * Removes rows in the smartdial database that matches updated contacts.
     *
     * @param db Database pointer to the smartdial database
     * @param updatedContactCursor Cursor pointing to the list of recently updated contacts.
     */
    private void removeUpdatedContacts(SQLiteDatabase db, Cursor updatedContactCursor) {
        db.beginTransaction();
        try {
            while (updatedContactCursor.moveToNext()) {
                final Long contactId = updatedContactCursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
                db.delete(Tables.PREFIX_TABLE, PrefixColumns.CONTACT_ID + "=" + contactId, null);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Inserts updated contacts as rows to the smartdial table.
     *
     * @param db Database pointer to the smartdial database.
     * @param updatedContactCursor Cursor pointing to the list of recently updated contacts.
     * @param currentMillis Current time to be recorded in the smartdial table as update timestamp.
     */
    @VisibleForTesting
    protected void insertUpdatedContactsAndNumberPrefix(SQLiteDatabase db,
            Cursor updatedContactCursor, Long currentMillis) {
            updatedContactCursor.moveToPosition(-1);
        db.beginTransaction();

        final String numberSqlInsert = "INSERT INTO " + Tables.PREFIX_TABLE + " (" +
                PrefixColumns.CONTACT_ID + ", " +
                PrefixColumns.PREFIX  + ") " +
                " VALUES (?, ?)";
        final SQLiteStatement numberInsert = db.compileStatement(numberSqlInsert);

        try {

            while (updatedContactCursor.moveToNext()) {
                // Handle string columns which can possibly be null first. In the case of certain
                // null columns (due to malformed rows possibly inserted by third-party apps
                // or sync adapters), skip the phone number row.
                String number = updatedContactCursor.getString(PhoneQuery.PHONE_NUMBER);
                if (TextUtils.isEmpty(number)) {
                    continue;
                }
                // No lookupKey in Silent Contacts
//                final String lookupKey = updatedContactCursor.getString(PhoneQuery.PHONE_LOOKUP_KEY);
//                if (TextUtils.isEmpty(lookupKey)) {
//                    continue;
//                } 
                if (PhoneNumberHelper.isUriNumber(number))
                    number = PhoneNumberHelper.getUsernameFromUriNumber(number);
                final ArrayList<String> numberPrefixes =  SmartDialPrefix.parseToNumberTokens(number);

                for (String numberPrefix : numberPrefixes) {
                    numberInsert.bindLong(1, updatedContactCursor.getLong( PhoneQuery.PHONE_CONTACT_ID));
                    numberInsert.bindString(2, numberPrefix);
                    numberInsert.executeInsert();
                    numberInsert.clearBindings();
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            numberInsert.close();
        }
    }

    /**
     * Inserts prefixes of contact names to the prefix table.
     *
     * @param db Database pointer to the smartdial database.
     * @param nameCursor Cursor pointing to the list of distinct updated contacts.
     */
    @VisibleForTesting
    void insertNamePrefixes(SQLiteDatabase db, Cursor nameCursor) {
        final int columnIndexName = nameCursor.getColumnIndex(Phone.DISPLAY_NAME_PRIMARY);
        final int columnIndexContactId = nameCursor.getColumnIndex(Phone.RAW_CONTACT_ID);

        db.beginTransaction();
        final String sqlInsert = "INSERT INTO " + Tables.PREFIX_TABLE + " (" +
                PrefixColumns.CONTACT_ID + ", " +
                PrefixColumns.PREFIX  + ") " +
                " VALUES (?, ?)";
        final SQLiteStatement insert = db.compileStatement(sqlInsert);

        try {
            while (nameCursor.moveToNext()) {
                /** Computes a list of prefixes of a given contact name. */
                final String name = nameCursor.getString(columnIndexName);
                if (name == null) {
                    Log.w(TAG, "Found user without name");
                    continue;
                }
                final ArrayList<String> namePrefixes =
                        SmartDialPrefix.generateNamePrefixes(nameCursor.getString(columnIndexName));

                for (String namePrefix : namePrefixes) {
                    if (DEBUG)
                        Log.v(TAG, "++++ add name prefix: " + namePrefix);
                    insert.bindLong(1, nameCursor.getLong(columnIndexContactId));
                    insert.bindString(2, namePrefix);
                    insert.executeInsert();
                    insert.clearBindings();
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            insert.close();
        }
    }

    /**
     * Updates the smart dial and prefix database.
     * This method queries the Delta API to get changed contacts since last update, and updates the
     * records in smartdial database and prefix database accordingly.
     * It also queries the deleted contact database to remove newly deleted contacts since last
     * update.
     */
    public void updateSmartDialDatabase() {
        final SQLiteDatabase db = ScContactsDatabaseHelper.getInstance(mContext).getDatabase(true);

        synchronized(mLock) {
            if (DEBUG) {
                Log.v(TAG, "Starting to update database");
            }
            final StopWatch stopWatch = DEBUG ? StopWatch.start("Updating databases") : null;

            /** Gets the last update time on the database. */
            final SharedPreferences databaseLastUpdateSharedPref = mContext.getSharedPreferences(
                    DATABASE_LAST_CREATED_SHARED_PREF, Context.MODE_PRIVATE);
            final String lastUpdateMillis = String.valueOf(databaseLastUpdateSharedPref.getLong(LAST_UPDATED_MILLIS, 0));

            if (DEBUG) {
                Log.v(TAG, "++++ Last updated at " + lastUpdateMillis);
            }
            /** Queries the contact database to get contacts that have been updated since the last
             * update time.
             */
            final Cursor updatedContactCursor = mContext.getContentResolver().query(PhoneQuery.URI,
                    PhoneQuery.PROJECTION, PhoneQuery.SELECTION,
                    new String[]{lastUpdateMillis}, null);
            if (updatedContactCursor == null) {
                if (DEBUG) {
                    Log.e(TAG, "SmartDial query received null for updated contact cursor");
                }
                return;
            }
            if (DEBUG) {
                Log.v(TAG, "++++ Contacts to update " + updatedContactCursor.getCount());
            }

            /** Sets the time after querying the database as the current update time. */
            final Long currentMillis = System.currentTimeMillis();

            try {
                if (DEBUG) {
                    stopWatch.lap("Queried the Contacts database");
                }

                /** Prevents the app from reading the dialer database when updating. */
                sInUpdate.getAndSet(true);

                /** Removes contacts that have been deleted. */
                removeDeletedContacts(db, lastUpdateMillis);
                removePotentiallyCorruptedContacts(db, lastUpdateMillis);

                if (DEBUG) {
                    stopWatch.lap("Finished deleting deleted entries");
                }

                /** If the database did not exist before, jump through deletion as there is nothing
                 * to delete.
                 */
                if (!lastUpdateMillis.equals("0")) {
                    /** Removes contacts that have been updated. Updated contact information will be
                     * inserted later.
                     */
                    removeUpdatedContacts(db, updatedContactCursor);
                    if (DEBUG) {
                        stopWatch.lap("Finished deleting updated entries");
                    }
                }

                /** Inserts recently updated contacts to the smartdial database.*/
                insertUpdatedContactsAndNumberPrefix(db, updatedContactCursor, currentMillis);
                if (DEBUG) {
                    stopWatch.lap("Finished building the smart dial table");
                }
                updatedContactCursor.moveToPosition(-1);
                insertNamePrefixes(db, updatedContactCursor);
            } finally {
                /** Inserts prefixes of phone numbers into the prefix table.*/
                updatedContactCursor.close();
            }

            sInUpdate.getAndSet(false);

            final SharedPreferences.Editor editor = databaseLastUpdateSharedPref.edit();
            editor.putLong(LAST_UPDATED_MILLIS, currentMillis);
            editor.commit();
        }
    }

    /**
     * Returns a list of candidate contacts where the query is a prefix of the dialpad index of
     * the contact's name or phone number.
     *
     * @param query The prefix of a contact's dialpad index.
     * @return A list of top candidate contacts that will be suggested to user to match their input.
     */
    public ArrayList<ContactNumber>  getLooseMatches(String query, SmartDialNameMatcher nameMatcher) {
        final boolean inUpdate = sInUpdate.get();
        if (inUpdate) {
            return Lists.newArrayList();
        }

        final SQLiteDatabase db = ScContactsDatabaseHelper.getInstance(mContext).getDatabase(false);

        /** Uses SQL query wildcard '%' to represent prefix matching.*/
        final String looseQuery = query + "%";

        final ArrayList<ContactNumber> result = Lists.newArrayList();

        final StopWatch stopWatch = DEBUG ? StopWatch.start(":Name Prefix query " + looseQuery) : null;

        final String currentTimeStamp = Long.toString(System.currentTimeMillis());

        final String[] PROJECTION = new String[] {
                Phone._ID,
                Phone.DISPLAY_NAME_PRIMARY,
                Phone.PHOTO_ID,
                Phone.NUMBER,
                Phone.RAW_CONTACT_ID              
        };

        final String SELECTION = Phone.RAW_CONTACT_ID + " IN " +
                " (SELECT " + PrefixColumns.CONTACT_ID +
                " FROM " + Tables.PREFIX_TABLE +
                " WHERE " + Tables.PREFIX_TABLE + "." + PrefixColumns.PREFIX +
                " LIKE '" + looseQuery + "')";


        /** Queries the database to find contacts that have an index matching the query prefix. */
        final Cursor cursor = mContext.getContentResolver().query(PhoneQuery.URI,
                PROJECTION, SELECTION,
                new String[]{currentTimeStamp}, SmartDialSortingOrder.SORT_ORDER);

        if (cursor == null) {
            return result;
        }
        try {
            if (DEBUG) {
                stopWatch.lap("Prefix query completed");
            }

            /** Gets the column ID from the cursor.*/
            final int columnDataId = 0;
            final int columnDisplayNamePrimary = 1;
            final int columnPhotoId = 2;
            final int columnNumber = 3;
            final int columnId = 4;
            if (DEBUG) {
                stopWatch.lap("Found column IDs");
            }

            final Set<ContactMatch> duplicates = new HashSet<>();
            int counter = 0;
            if (DEBUG) {
                stopWatch.lap("Moved cursor to start");
            }
            /** Iterates the cursor to find top contact suggestions without duplication.*/
            while ((cursor.moveToNext()) && (counter < MAX_ENTRIES)) {
                final long dataID = cursor.getLong(columnDataId);
                final String displayName = cursor.getString(columnDisplayNamePrimary);
                final String phoneNumber = cursor.getString(columnNumber);
                final long id = cursor.getLong(columnId);
                final long photoId = cursor.getLong(columnPhotoId);

                /** If a contact already exists and another phone number of the contact is being
                 * processed, skip the second instance.
                 */
                final ContactMatch contactMatch = new ContactMatch(null /*lookupKey*/, id);
                if (duplicates.contains(contactMatch)) {
                    continue;
                }

                /**
                 * If the contact has either the name or number that matches the query, add to the
                 * result.
                 */
                final boolean nameMatches = displayName != null && nameMatcher.matches(displayName);
                final boolean numberMatches = (nameMatcher.matchesNumber(phoneNumber, query) != null);
                if (nameMatches || numberMatches) {
                    /** If a contact has not been added, add it to the result and the hash set.*/
                    duplicates.add(contactMatch);
                    result.add(new ContactNumber(id, dataID, displayName, phoneNumber, null /* lookupKey */, photoId));
                    counter++;
                    if (DEBUG) {
                        stopWatch.lap("Added one result: Name: " + displayName);
                    }
                }
            }

            if (DEBUG) {
                stopWatch.stopAndLog(TAG + "Finished loading cursor", 0);
            }
        } finally {
            cursor.close();
        }
        return result;
    }
}
