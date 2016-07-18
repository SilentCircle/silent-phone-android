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
 * This class uses ideas from the standard Android contacts provider, in some parts
 * it's copied verbatim to retain inter-operability, 
 */

package com.silentcircle.contacts.providers;

import android.content.Context;
import android.util.Log;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.contacts.ScCallLog.ScCalls;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class ScCallLogDatabaseHelper extends SQLiteOpenHelper implements KeyManagerSupport.KeyManagerListener {
    private static final String TAG = "ScCallLogDBHelper";
    
    static final int DATABASE_VERSION = 105;

    private static final String DATABASE_NAME = "sc_contacts.db";

    public interface Tables {
        String CALLS = "calls";
    }

    private final Context mContext;
    private final boolean mDatabaseOptimizationEnabled;

    private static ScCallLogDatabaseHelper sSingleton = null;

    private volatile CountDownLatch databasePasswordReadyLatch;

    private SQLiteDatabase writableDatabase;
    private SQLiteDatabase readableDatabase;

    private boolean registeredWithKeyManager;

    public static synchronized ScCallLogDatabaseHelper getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new ScCallLogDatabaseHelper(context, DATABASE_NAME, true);
        }
        return sSingleton;
    }

    /**
     * Returns a new instance for unit tests.
     */
//    @NeededForTesting
    public static ScCallLogDatabaseHelper getNewInstanceForTest(Context context) {
        ScCallLogDatabaseHelper dbHelper = new ScCallLogDatabaseHelper(context, null, false);
        dbHelper.keyManagerReadyForTest();
        return dbHelper;
    }

    /** This functions opens the database with the password and then counts down the latch */
    private boolean keyManagerReadyForTest() {
        if (writableDatabase != null)
            return false;
        writableDatabase = getWritableDatabase("password");
        readableDatabase = writableDatabase;
        databasePasswordReadyLatch.countDown();
        databasePasswordReadyLatch = null;
        return true;
    }

    private ScCallLogDatabaseHelper(Context context, String databaseName, boolean optimizationEnabled) {
        super(context, databaseName, null, DATABASE_VERSION);
        mDatabaseOptimizationEnabled = optimizationEnabled;

        mContext = context;
        databasePasswordReadyLatch = new CountDownLatch(1);
        SQLiteDatabase.loadLibs(context);

        KeyManagerSupport.addListener(this);
//        mUseStrictPhoneNumberComparison = resources.getBoolean(com.android.internal.R.bool.config_use_strict_phone_number_comparation);
    }

    public SQLiteDatabase getDatabase(boolean writable) {
        waitForAccess(databasePasswordReadyLatch);
        return writable ? writableDatabase : readableDatabase;
    }

    /** This functions opens the database with the password and then counts down the latch */
    private synchronized boolean keyManagerReady() {
        if (writableDatabase != null)
            return false;
        char[] pw = readPassword();
        if (pw == null)
            return false;
        writableDatabase = getWritableDatabase(pw);
        Arrays.fill(pw, '\0');
        readableDatabase = writableDatabase;
        databasePasswordReadyLatch.countDown();
        databasePasswordReadyLatch = null;
        return true;
    }

    private char[] readPassword() {
        // get the stored key
        byte[] data = KeyManagerSupport.getPrivateKeyData(mContext.getContentResolver(), "contactsdatabase");
        if (data == null) {             // is not yet available - create one
            data = KeyManagerSupport.randomPrivateKeyData(mContext.getContentResolver(), "contactsdatabase", 32);
        }
        if (data == null)               // could not get random key data, key manager no ready yet
            return null;

        char[] keyChars = Utilities.bytesToHexChars(data);
        Arrays.fill(data, (byte)0);
        return keyChars;
    }

    public void onKeyDataRead() {}

    public void onKeyManagerUnlockRequest() {
        if (sSingleton == null) {
            Log.e(TAG, "Inconsistent initialization - unlock");
            return;
        }
        keyManagerReady();
    }

    public void onKeyManagerLockRequest() {
        if (sSingleton == null) {
            Log.e(TAG, "Inconsistent initialization - lock");
            return;
        }
        close();
    }

    /**
     * Check if key manager is available and register/unregister with key manager.
     *
     * @param register if {@code true} then register with key manager, otherwise unregister
     * @return {@code true} if key manager available and register/unregister was OK
     */
    public synchronized boolean checkRegisterKeyManager(boolean register) {

        if (registeredWithKeyManager && register) {
            return true;
        }
        registeredWithKeyManager = false;
        if (register) {
            long token = KeyManagerSupport.registerWithKeyManager(mContext.getContentResolver(),
                    mContext.getPackageName(), mContext.getString(R.string.app_name));

            if (token == 0) {
                return false;
            }
            registeredWithKeyManager = true;
        }
        else {
            KeyManagerSupport.unregisterFromKeyManager(mContext.getContentResolver());
        }
        return true;
    }

    public boolean isReady() {
        return writableDatabase != null;
    }

    public boolean isRegisteredWithKeyManager() {
        return registeredWithKeyManager;
    }

    /**
     * During startup/open of the data base we block all attempts to get a database
     * until the password is available and the database is open.
     */
    private void waitForAccess(CountDownLatch latch) {
        if (latch == null) {
            return;
        }
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public synchronized void close() {
        writableDatabase = null;
        readableDatabase = null;
        databasePasswordReadyLatch = new CountDownLatch(1);
        super.close();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
    }

    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Bootstrapping database version: " + DATABASE_VERSION);

        // The table for recent calls is here so we can do table joins
        // on people, phones, and calls all in one place.
        db.execSQL("CREATE TABLE " + Tables.CALLS + " (" +
                ScCalls._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ScCalls.NUMBER + " TEXT," +
                ScCalls.DATE + " INTEGER," +
                ScCalls.DURATION + " INTEGER," +
                ScCalls.TYPE + " INTEGER," +
                ScCalls.NEW + " INTEGER," +
                ScCalls.CACHED_NAME + " TEXT," +
                ScCalls.CACHED_NUMBER_TYPE + " INTEGER," +
                ScCalls.CACHED_NUMBER_LABEL + " TEXT," +
                ScCalls.COUNTRY_ISO + " TEXT," +
                ScCalls.IS_READ + " INTEGER," +
                ScCalls.GEOCODED_LOCATION + " TEXT," +
                ScCalls.CACHED_LOOKUP_URI + " TEXT," +
                ScCalls.CACHED_MATCHED_NUMBER + " TEXT," +
                ScCalls.CACHED_NORMALIZED_NUMBER + " TEXT," +
                ScCalls.CACHED_PHOTO_ID + " INTEGER NOT NULL DEFAULT 0," +
                ScCalls.CACHED_FORMATTED_NUMBER + " TEXT" +
                ScCalls.SC_OPTION_INT1 + " INTEGER," +
                ScCalls.SC_OPTION_INT2 + " INTEGER," +
                ScCalls.SC_OPTION_TEXT1 + " TEXT," +
                ScCalls.SC_OPTION_TEXT2 + " TEXT" +
                ");");

        if (mDatabaseOptimizationEnabled) {
            db.execSQL("ANALYZE;"); // This will create a sqlite_stat1 table that is used for query optimization
            updateSqliteStats(db);
        }
    }





    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
             Log.i(TAG, "Upgrading from version " + oldVersion + " to " + newVersion);

        boolean upgradeViewsAndTriggers = false;
        boolean rebuildSqliteStats = false;

        if (oldVersion < 101) {
            upgradeToVersion101(db);
        }

        if (oldVersion < 102) {
            upgradeViewsAndTriggers = true;
        }
        
        if (oldVersion < 103) {
            upgradeToVersion103(db);
        }

        if (oldVersion < 104) {
            upgradeToVersion104(db);
            upgradeViewsAndTriggers = true;
        }

        if (oldVersion < 105) {
            upgradeToVersion105(db);
            upgradeViewsAndTriggers = true;
            oldVersion = 105;
        }

        if (upgradeViewsAndTriggers) {
            rebuildSqliteStats = true;
        }

        if (rebuildSqliteStats) {
            updateSqliteStats(db);
        }

        if (oldVersion != newVersion) {
            throw new IllegalStateException(
                    "error upgrading the database to version " + newVersion);
        }
    }


    private void upgradeToVersion101(SQLiteDatabase db) {
    }

    private void upgradeToVersion103(SQLiteDatabase db) {
    }

    private void upgradeToVersion104(SQLiteDatabase db) {

        // Adding additional columns to Call log
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_INT1 + " INTEGER;");
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_INT2 + " INTEGER;");
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_TEXT1 + " TEXT;");
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_TEXT2 + " TEXT;");
    }

    private void upgradeToVersion105(SQLiteDatabase db) {
    }
        
    /**
     * Wipes all data.
     */
    public void wipeData() {
        SQLiteDatabase db = getDatabase(true);

        db.execSQL("DELETE FROM " + Tables.CALLS + ";");
    }
    /**
     * Adds index stats into the SQLite database to force it to always use the lookup indexes.
     *
     * Note if you drop a table or an index, the corresponding row will be removed from this table.
     * Make sure to call this method after such operations.
     */
    private void updateSqliteStats(SQLiteDatabase db) {
        if (!mDatabaseOptimizationEnabled) {
            return; // We don't use sqlite_stat1 during tests.
        }

        // Specific stats strings are based on an actual large database after running ANALYZE
        // Important here are relative sizes. Raw-Contacts is slightly bigger than Contacts
        // Warning: Missing tables in here will make SQLite assume to contain 1000000 rows,
        // which can lead to catastrophic query plans for small tables

        // What these numbers mean is described in this file.
        // http://www.sqlite.org/cgi/src/finfo?name=src/analyze.c

        // Excerpt:
        /*
        ** Format of sqlite_stat1:
        **
        ** There is normally one row per index, with the index identified by the
        ** name in the idx column.  The tbl column is the name of the table to
        ** which the index belongs.  In each such row, the stat column will be
        ** a string consisting of a list of integers.  The first integer in this
        ** list is the number of rows in the index and in the table.  The second
        ** integer is the average number of rows in the index that have the same
        ** value in the first column of the index.  The third integer is the average
        ** number of rows in the index that have the same value for the first two
        ** columns.  The N-th integer (for N>1) is the average number of rows in
        ** the index which have the same value for the first N-1 columns.  For
        ** a K-column index, there will be K+1 integers in the stat column.  If
        ** the index is unique, then the last integer will be 1.
        **
        ** The list of integers in the stat column can optionally be followed
        ** by the keyword "unordered".  The "unordered" keyword, if it is present,
        ** must be separated from the last integer by a single space.  If the
        ** "unordered" keyword is present, then the query planner assumes that
        ** the index is unordered and will not use the index for a range query.
        **
        ** If the sqlite_stat1.idx column is NULL, then the sqlite_stat1.stat
        ** column contains a single integer which is the (estimated) number of
        ** rows in the table identified by sqlite_stat1.tbl.
        */

        try {
            db.execSQL("DELETE FROM sqlite_stat1");

            updateIndexStats(db, Tables.CALLS, null, "250");

            // Search index
            updateIndexStats(db, "search_index_docsize", null, "9000");
            updateIndexStats(db, "search_index_content", null, "9000");
            updateIndexStats(db, "search_index_stat",    null, "1");
            updateIndexStats(db, "search_index_segments", null, "450");
            updateIndexStats(db, "search_index_segdir", "sqlite_autoindex_search_index_segdir_1", "9 5 1");

            // Force sqlite to reload sqlite_stat1.
            db.execSQL("ANALYZE sqlite_master;");
        } catch (SQLException e) {
            Log.e(TAG, "Could not update index stats", e);
        }
    }

    /**
     * Stores statistics for a given index.
     *
     * @param stats has the following structure: the first index is the expected size of
     * the table.  The following integer(s) are the expected number of records selected with the
     * index.  There should be one integer per indexed column.
     */
    private void updateIndexStats(SQLiteDatabase db, String table, String index,
            String stats) {
        if (index == null) {
            db.execSQL("DELETE FROM sqlite_stat1 WHERE tbl=? AND idx IS NULL",
                    new String[] { table });
        } else {
            db.execSQL("DELETE FROM sqlite_stat1 WHERE tbl=? AND idx=?",
                    new String[] { table, index });
        }
        db.execSQL("INSERT INTO sqlite_stat1 (tbl,idx,stat) VALUES (?,?,?)",
                new String[] { table, index, stats });
    }

}
