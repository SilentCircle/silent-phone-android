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
 * This class uses ideas from the standard Android contacts provider, in some parts
 * it's copied verbatim to retain inter-operability, 
 */

package com.silentcircle.contacts.providers;

import android.content.ContentValues;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.net.Uri;
import android.os.Binder;
import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;

import com.google.common.collect.Table;
import com.silentcircle.contacts.database.DeletedContactsTableUtil;
import com.silentcircle.contacts.database.MoreDatabaseUtils;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentcontacts2.ScBaseColumns;
import com.silentcircle.silentcontacts2.ScCallLog.ScCalls;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.GroupMembership;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Nickname;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Organization;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.SipAddress;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredName;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentcontacts2.ScContactsContract.DisplayNameSources;
import com.silentcircle.silentcontacts2.ScContactsContract.DisplayPhoto;
import com.silentcircle.silentcontacts2.ScContactsContract.FullNameStyle;
import com.silentcircle.silentcontacts2.ScContactsContract.Groups;
import com.silentcircle.silentcontacts2.ScContactsContract.PinnedPositions;
import com.silentcircle.silentcontacts2.ScContactsContract.PhoneticNameStyle;
import com.silentcircle.silentcontacts2.ScContactsContract.PhotoFiles;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts.Photo;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItemPhotos;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItems;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import net.sqlcipher.Cursor;
import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDoneException;
import net.sqlcipher.database.SQLiteOpenHelper;
import net.sqlcipher.database.SQLiteQueryBuilder;
import net.sqlcipher.database.SQLiteStatement;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class ScContactsDatabaseHelper extends SQLiteOpenHelper implements KeyManagerSupport.KeyManagerListener {
    private static final String TAG = "ScContactsDBHelper";
    
    static final int DATABASE_VERSION = 105;

    private static final String DATABASE_NAME = "sc_contacts.db";

    public interface Tables {
        public static final String RAW_CONTACTS = "raw_contacts";
        public static final String DATA = "data";
        public static final String STREAM_ITEMS = "stream_items";
        public static final String STREAM_ITEM_PHOTOS = "stream_item_photos";
        public static final String CALLS = "calls";
        public static final String MIMETYPES = "mimetypes";
        public static final String NAME_LOOKUP = "name_lookup";
        public static final String PHONE_LOOKUP = "phone_lookup";
        public static final String DIRECTORIES = "directories";
        public static final String SEARCH_INDEX = "search_index";
        public static final String PROPERTIES = "properties";
        public static final String PHOTO_FILES = "photo_files";
        public static final String GROUPS = "groups";
        public static final String DELETED_CONTACTS = "deleted_contacts";

        /** Saves all possible prefixes to refer to a contacts. 
         * 
         * This was part of a separate DB in the native Dialer. Because SPA combines everything into 
         * on APK we can use the real contacts DB. This has some advantages with regard to data
         * synchronization. 
         */
        static final String PREFIX_TABLE = "prefix_table";


        // This list of tables contains auto-incremented sequences.
        public static final String[] SEQUENCE_TABLES = new String[] {
            RAW_CONTACTS,
            STREAM_ITEMS,
            STREAM_ITEM_PHOTOS,
            PHOTO_FILES,
            DATA,
            GROUPS,
            CALLS,
            DIRECTORIES
        };

        /**
         * For {@link com.silentcircle.silentcontacts2.ScContactsContract.DataUsageFeedback}. The table structure
         * itself is not exposed outside.
         */
        public static final String DATA_USAGE_STAT = "data_usage_stat";

        public static final String DATA_JOIN_MIMETYPES = "data JOIN mimetypes ON (data.mimetype_id = mimetypes._id)";

        public static final String RAW_CONTACTS_JOIN_ACCOUNTS = Tables.RAW_CONTACTS;

        public static final String DATA_JOIN_RAW_CONTACTS = "data "
                + "JOIN raw_contacts ON (data.raw_contact_id = raw_contacts._id)";

        public static final String DATA_JOIN_MIMETYPE_RAW_CONTACTS = "data "
                + "JOIN mimetypes ON (data.mimetype_id = mimetypes._id) "
                + "JOIN raw_contacts ON (data.raw_contact_id = raw_contacts._id)"
                ;
 
        public static final String CONTACTS_JOIN_RAW_CONTACTS_DATA_FILTERED_BY_GROUPMEMBERSHIP =
                Tables.RAW_CONTACTS
                    + " INNER JOIN " + Tables.DATA
                        + " ON (" + DataColumns.CONCRETE_DATA1 + "=" + GroupsColumns.CONCRETE_ID
                        + " AND "
                        + DataColumns.CONCRETE_RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID
                        + " AND "
                        + DataColumns.CONCRETE_MIMETYPE_ID + "="
                            + "(SELECT " + MimetypesColumns._ID
                            + " FROM " + Tables.MIMETYPES
                            + " WHERE "
                            + MimetypesColumns.CONCRETE_MIMETYPE + "="
                                + "'" + GroupMembership.CONTENT_ITEM_TYPE + "'"
                            + ")"
                        + ")";

    }    

    public interface Joins {
        /**
         * Join string intended to be used with the GROUPS table/view.  The main table must be named
         * as "groups".
         *
         * Adds the "group_member_count column" to the query, which will be null if a group has
         * no members.  Use ifnull(group_member_count, 0) if 0 is needed instead.
         */
        public static final String GROUP_MEMBER_COUNT =
                " LEFT OUTER JOIN (SELECT "
                        + "data.data1 AS member_count_group_id, "
                        + "COUNT(data.raw_contact_id) AS group_member_count "
                    + "FROM data "
                    + "WHERE "
                        + "data.mimetype_id = (SELECT _id FROM mimetypes WHERE "
                            + "mimetypes.mimetype = '" + GroupMembership.CONTENT_ITEM_TYPE + "')"
                    + "GROUP BY member_count_group_id) AS member_count_table" // End of inner query
                + " ON (groups._id = member_count_table.member_count_group_id)";
    }

    public interface Views {
        public static final String DATA = "view_data";
        public static final String RAW_CONTACTS = "view_raw_contacts";
//        public static final String CONTACTS = "view_contacts";
//        public static final String ENTITIES = "view_entities";
        public static final String RAW_ENTITIES = "view_raw_entities";
        public static final String GROUPS = "view_groups";
        public static final String DATA_USAGE_STAT = "view_data_usage_stat";
        public static final String STREAM_ITEMS = "view_stream_items";
    }
    
    public interface Clauses {
        final String GROUP_HAS_ACCOUNT_AND_SOURCE_ID = Groups.SOURCE_ID + "=?";
    }

    public interface RawContactsColumns {
        public static final String CONCRETE_ID = Tables.RAW_CONTACTS + "." + ScBaseColumns._ID;

        public static final String CONCRETE_SOURCE_ID = Tables.RAW_CONTACTS + "." + RawContacts.SOURCE_ID;
        public static final String CONCRETE_VERSION = Tables.RAW_CONTACTS + "." + RawContacts.VERSION;
        public static final String CONCRETE_DIRTY =  Tables.RAW_CONTACTS + "." + RawContacts.DIRTY;
        public static final String CONCRETE_DELETED = Tables.RAW_CONTACTS + "." + RawContacts.DELETED;
        public static final String CONCRETE_SYNC1 = Tables.RAW_CONTACTS + "." + RawContacts.SYNC1;
        public static final String CONCRETE_SYNC2 = Tables.RAW_CONTACTS + "." + RawContacts.SYNC2;
        public static final String CONCRETE_SYNC3 = Tables.RAW_CONTACTS + "." + RawContacts.SYNC3;
        public static final String CONCRETE_SYNC4 = Tables.RAW_CONTACTS + "." + RawContacts.SYNC4;
        public static final String CONCRETE_CUSTOM_RINGTONE = Tables.RAW_CONTACTS + "." + RawContacts.CUSTOM_RINGTONE;
        public static final String CONCRETE_LAST_TIME_CONTACTED = Tables.RAW_CONTACTS + "." + RawContacts.LAST_TIME_CONTACTED;
        public static final String CONCRETE_TIMES_CONTACTED = Tables.RAW_CONTACTS + "." + RawContacts.TIMES_CONTACTED;
        public static final String CONCRETE_STARRED = Tables.RAW_CONTACTS + "." + RawContacts.STARRED;
        public static final String CONCRETE_PINNED = Tables.RAW_CONTACTS + "." + RawContacts.PINNED;

        public static final String DISPLAY_NAME = RawContacts.DISPLAY_NAME_PRIMARY;
        public static final String DISPLAY_NAME_SOURCE = RawContacts.DISPLAY_NAME_SOURCE;
        public static final String AGGREGATION_NEEDED = "aggregation_needed";

        public static final String CONCRETE_DISPLAY_NAME = Tables.RAW_CONTACTS + "." + DISPLAY_NAME;
        public static final String CONCRETE_NAME_VERIFIED = Tables.RAW_CONTACTS + "." + RawContacts.NAME_VERIFIED;

        public static final String CONCRETE_CONTACT_LAST_UPDATED_TIMESTAMP = Tables.RAW_CONTACTS + "."
                + RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP;

        public static final String PHONEBOOK_LABEL_PRIMARY = "phonebook_label";
        public static final String PHONEBOOK_BUCKET_PRIMARY = "phonebook_bucket";
        public static final String PHONEBOOK_LABEL_ALTERNATIVE = "phonebook_label_alt";
        public static final String PHONEBOOK_BUCKET_ALTERNATIVE = "phonebook_bucket_alt";
    }

    public interface DataColumns {
        public static final String PACKAGE_ID = "package_id";
        public static final String MIMETYPE_ID = "mimetype_id";

        public static final String CONCRETE_ID = Tables.DATA + "." + ScBaseColumns._ID;
        public static final String CONCRETE_MIMETYPE_ID = Tables.DATA + "." + MIMETYPE_ID;
        public static final String CONCRETE_RAW_CONTACT_ID = Tables.DATA + "." + Data.RAW_CONTACT_ID;
        public static final String CONCRETE_GROUP_ID = Tables.DATA + "." + GroupMembership.GROUP_ROW_ID;

        public static final String CONCRETE_DATA1 = Tables.DATA + "." + Data.DATA1;
        public static final String CONCRETE_DATA2 = Tables.DATA + "." + Data.DATA2;
        public static final String CONCRETE_DATA3 = Tables.DATA + "." + Data.DATA3;
        public static final String CONCRETE_DATA4 = Tables.DATA + "." + Data.DATA4;
        public static final String CONCRETE_DATA5 = Tables.DATA + "." + Data.DATA5;
        public static final String CONCRETE_DATA6 = Tables.DATA + "." + Data.DATA6;
        public static final String CONCRETE_DATA7 = Tables.DATA + "." + Data.DATA7;
        public static final String CONCRETE_DATA8 = Tables.DATA + "." + Data.DATA8;
        public static final String CONCRETE_DATA9 = Tables.DATA + "." + Data.DATA9;
        public static final String CONCRETE_DATA10 = Tables.DATA + "." + Data.DATA10;
        public static final String CONCRETE_DATA11 = Tables.DATA + "." + Data.DATA11;
        public static final String CONCRETE_DATA12 = Tables.DATA + "." + Data.DATA12;
        public static final String CONCRETE_DATA13 = Tables.DATA + "." + Data.DATA13;
        public static final String CONCRETE_DATA14 = Tables.DATA + "." + Data.DATA14;
        public static final String CONCRETE_DATA15 = Tables.DATA + "." + Data.DATA15;
        public static final String CONCRETE_IS_PRIMARY = Tables.DATA + "." + Data.IS_PRIMARY;
        public static final String CONCRETE_PACKAGE_ID = Tables.DATA + "." + PACKAGE_ID;
    }

    public interface GroupMembershipColumns {
        public static final String RAW_CONTACT_ID = Data.RAW_CONTACT_ID;
        public static final String GROUP_ROW_ID = GroupMembership.GROUP_ROW_ID;
    }

    public interface GroupsColumns {
        public static final String PACKAGE_ID = "package_id";
        public static final String CONCRETE_PACKAGE_ID = Tables.GROUPS + "." + PACKAGE_ID;

        public static final String CONCRETE_ID = Tables.GROUPS + "." + ScBaseColumns._ID;
        public static final String CONCRETE_SOURCE_ID = Tables.GROUPS + "." + Groups.SOURCE_ID;
    }

    public interface PhoneLookupColumns {
        public static final String _ID = ScBaseColumns._ID;
        public static final String DATA_ID = "data_id";
        public static final String RAW_CONTACT_ID = "raw_contact_id";
        public static final String NORMALIZED_NUMBER = "normalized_number";
        public static final String MIN_MATCH = "min_match";
    }

    public interface NameLookupColumns {
        public static final String RAW_CONTACT_ID = "raw_contact_id";
        public static final String DATA_ID = "data_id";
        public static final String NORMALIZED_NAME = "normalized_name";
        public static final String NAME_TYPE = "name_type";
    }

    public final static class NameLookupType {
        public static final int NAME_EXACT = 0;
        public static final int NAME_VARIANT = 1;
        public static final int NAME_COLLATION_KEY = 2;
        public static final int NICKNAME = 3;
        public static final int EMAIL_BASED_NICKNAME = 4;

        // This is the highest name lookup type code plus one
        public static final int TYPE_COUNT = 5;

        public static boolean isBasedOnStructuredName(int nameLookupType) {
            return nameLookupType == NameLookupType.NAME_EXACT
                    || nameLookupType == NameLookupType.NAME_VARIANT
                    || nameLookupType == NameLookupType.NAME_COLLATION_KEY;
        }
    }

    public interface MimetypesColumns {
        public static final String _ID = ScBaseColumns._ID;
        public static final String MIMETYPE = "mimetype";

        public static final String CONCRETE_ID = Tables.MIMETYPES + "." + ScBaseColumns._ID;
        public static final String CONCRETE_MIMETYPE = Tables.MIMETYPES + "." + MIMETYPE;
    }

    public static final class SearchIndexColumns {
        public static final String RAW_CONTACT_ID = "raw_contact_id";
        public static final String CONTENT = "content";
        public static final String NAME = "name";
        public static final String TOKENS = "tokens";
    }

    /**
     * Private table for calculating per-contact-method ranking.
     */
    public interface DataUsageStatColumns {
        /** type: INTEGER (long) */
        public static final String _ID = "stat_id";
        public static final String CONCRETE_ID = Tables.DATA_USAGE_STAT + "." + _ID;

        /** type: INTEGER (long) */
        public static final String DATA_ID = "data_id";
        public static final String CONCRETE_DATA_ID = Tables.DATA_USAGE_STAT + "." + DATA_ID;

        /** type: INTEGER (long) */
        public static final String LAST_TIME_USED = "last_time_used";
        public static final String CONCRETE_LAST_TIME_USED =
                Tables.DATA_USAGE_STAT + "." + LAST_TIME_USED;

        /** type: INTEGER */
        public static final String TIMES_USED = "times_used";
        public static final String CONCRETE_TIMES_USED =
                Tables.DATA_USAGE_STAT + "." + TIMES_USED;

        /** type: INTEGER */
        public static final String USAGE_TYPE_INT = "usage_type";
        public static final String CONCRETE_USAGE_TYPE =
                Tables.DATA_USAGE_STAT + "." + USAGE_TYPE_INT;

        /**
         * Integer values for USAGE_TYPE.
         *
         * @see android.provider.ContactsContract.DataUsageFeedback#USAGE_TYPE
         */
        public static final int USAGE_TYPE_INT_CALL = 0;
        public static final int USAGE_TYPE_INT_LONG_TEXT = 1;
        public static final int USAGE_TYPE_INT_SHORT_TEXT = 2;
    }

    public interface StreamItemsColumns {
        final String CONCRETE_ID = Tables.STREAM_ITEMS + "." + ScBaseColumns._ID;
        final String CONCRETE_RAW_CONTACT_ID =  Tables.STREAM_ITEMS + "." + StreamItems.RAW_CONTACT_ID;
//        final String CONCRETE_PACKAGE = Tables.STREAM_ITEMS + "." + StreamItems.RES_PACKAGE;
        final String CONCRETE_ICON = Tables.STREAM_ITEMS + "." + StreamItems.RES_ICON;
        final String CONCRETE_LABEL = Tables.STREAM_ITEMS + "." + StreamItems.RES_LABEL;
        final String CONCRETE_TEXT = Tables.STREAM_ITEMS + "." + StreamItems.TEXT;
        final String CONCRETE_TIMESTAMP = Tables.STREAM_ITEMS + "." + StreamItems.TIMESTAMP;
        final String CONCRETE_COMMENTS = Tables.STREAM_ITEMS + "." + StreamItems.COMMENTS;
        final String CONCRETE_SYNC1 = Tables.STREAM_ITEMS + "." + StreamItems.SYNC1;
        final String CONCRETE_SYNC2 = Tables.STREAM_ITEMS + "." + StreamItems.SYNC2;
        final String CONCRETE_SYNC3 = Tables.STREAM_ITEMS + "." + StreamItems.SYNC3;
        final String CONCRETE_SYNC4 = Tables.STREAM_ITEMS + "." + StreamItems.SYNC4;
    }

    public interface StreamItemPhotosColumns {
        final String CONCRETE_ID = Tables.STREAM_ITEM_PHOTOS + "." + ScBaseColumns._ID;
        final String CONCRETE_STREAM_ITEM_ID = Tables.STREAM_ITEM_PHOTOS + "." + StreamItemPhotos.STREAM_ITEM_ID;
        final String CONCRETE_SORT_INDEX = Tables.STREAM_ITEM_PHOTOS + "." + StreamItemPhotos.SORT_INDEX;
        final String CONCRETE_PHOTO_FILE_ID = Tables.STREAM_ITEM_PHOTOS + "." + StreamItemPhotos.PHOTO_FILE_ID;
        final String CONCRETE_SYNC1 = Tables.STREAM_ITEM_PHOTOS + "." + StreamItemPhotos.SYNC1;
        final String CONCRETE_SYNC2 = Tables.STREAM_ITEM_PHOTOS + "." + StreamItemPhotos.SYNC2;
        final String CONCRETE_SYNC3 = Tables.STREAM_ITEM_PHOTOS + "." + StreamItemPhotos.SYNC3;
        final String CONCRETE_SYNC4 = Tables.STREAM_ITEM_PHOTOS + "." + StreamItemPhotos.SYNC4;
    }

    public interface PhotoFilesColumns {
        String CONCRETE_ID = Tables.PHOTO_FILES + "." + ScBaseColumns._ID;
        String CONCRETE_HEIGHT = Tables.PHOTO_FILES + "." + PhotoFiles.HEIGHT;
        String CONCRETE_WIDTH = Tables.PHOTO_FILES + "." + PhotoFiles.WIDTH;
        String CONCRETE_FILESIZE = Tables.PHOTO_FILES + "." + PhotoFiles.FILESIZE;
    }

    public static final class DirectoryColumns {
        public static final String TYPE_RESOURCE_NAME = "typeResourceName";
    }

    public interface PropertiesColumns {
        String PROPERTY_KEY = "property_key";
        String PROPERTY_VALUE = "property_value";
    }

    public static interface PrefixColumns extends ScBaseColumns {
        static final String PREFIX = "prefix";
        static final String CONTACT_ID = "contact_id";
    }
    
    public interface Projections {
        String[] ID = new String[] {ScBaseColumns._ID};
        String[] LITERAL_ONE = new String[] {"1"};
    }

    /**
     * Property names for {@link ScContactsDatabaseHelper#getProperty} and {@link ScContactsDatabaseHelper#setProperty}.
     */
    public interface DbProperties {
        String DIRECTORY_SCAN_COMPLETE = "directoryScanComplete";
    }

    /** In-memory cache of previously found MIME-type mappings */
    private final HashMap<String, Long> mMimetypeCache = new HashMap<String, Long>();

    /** In-memory cache the packages table */
    private final HashMap<String, Long> mPackageCache = new HashMap<String, Long>();

    private long mMimeTypeIdEmail;
    private long mMimeTypeIdIm;
    private long mMimeTypeIdNickname;
    private long mMimeTypeIdOrganization;
    private long mMimeTypeIdPhone;
    private long mMimeTypeIdSip;
    private long mMimeTypeIdStructuredName;
    private long mMimeTypeIdStructuredPostal;

    /** Compiled statements for querying and inserting mappings */
    private SQLiteStatement mDataMimetypeQuery;

    /** Precompiled sql statement for setting a data record to the primary. */
    private SQLiteStatement mSetPrimaryStatement;
    /** Precompiled sql statement for setting a data record to the super primary. */
    private SQLiteStatement mSetSuperPrimaryStatement;
    /** Precompiled sql statement for clearing super primary of a single record. */
    private SQLiteStatement mClearSuperPrimaryStatement;
    /** Precompiled sql statement for updating a contact display name */
    private SQLiteStatement mRawContactDisplayNameUpdate;

    private SQLiteStatement mNameLookupInsert;
    private SQLiteStatement mNameLookupDelete;
    private SQLiteStatement mResetNameVerifiedForOtherRawContacts;

    private String[] mSelectionArgs1 = new String[1];
    private NameSplitter mNameSplitter;
    private NameSplitter.Name mName = new NameSplitter.Name();
    private CharArrayBuffer mCharArrayBuffer = new CharArrayBuffer(128);

    private final Context mContext;
    private final boolean mDatabaseOptimizationEnabled;
    private StringBuilder mSb = new StringBuilder();

    private boolean mUseStrictPhoneNumberComparison;

    private static ScContactsDatabaseHelper sSingleton = null;

    private volatile CountDownLatch databasePasswordReadyLatch;

    private SQLiteDatabase writableDatabase;
    private SQLiteDatabase readableDatabase;

    private boolean registeredWithKeyManager;

    public static synchronized ScContactsDatabaseHelper getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new ScContactsDatabaseHelper(context, DATABASE_NAME, true);
        }
        return sSingleton;
    }

    /**
     * Returns a new instance for unit tests.
     */
//    @NeededForTesting
    public static ScContactsDatabaseHelper getNewInstanceForTest(Context context) {
        ScContactsDatabaseHelper dbHelper = new ScContactsDatabaseHelper(context, null, false);
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

    private ScContactsDatabaseHelper(Context context, String databaseName, boolean optimizationEnabled) {
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

    /**
     * Clear all the cached database information and re-initialize it.
     *
     * @param db target database
     */
    private void refreshDatabaseCaches(SQLiteDatabase db) {
//  TODO        mStatusUpdateDelete = null;
//        mStatusUpdateReplace = null;
//        mStatusUpdateInsert = null;
//        mStatusUpdateAutoTimestamp = null;
//        mStatusAttributionUpdate = null;
        mResetNameVerifiedForOtherRawContacts = null;
        mRawContactDisplayNameUpdate = null;
        mSetPrimaryStatement = null;
        mClearSuperPrimaryStatement = null;
        mSetSuperPrimaryStatement = null;
        mNameLookupInsert = null;
        mNameLookupDelete = null;
        mDataMimetypeQuery = null;

        initializeCache(db);
    }

    /**
     * (Re-)initialize the cached database information.
     *
     * @param db target database
     */
    private void initializeCache(SQLiteDatabase db) {
        mMimetypeCache.clear();
        mPackageCache.clear();

        // TODO: This could be optimized into one query instead of 7
        //        Also: We shouldn't have those fields in the first place. This should just be
        //        in the cache
        mMimeTypeIdEmail = lookupMimeTypeId(Email.CONTENT_ITEM_TYPE, db);
        mMimeTypeIdIm = lookupMimeTypeId(Im.CONTENT_ITEM_TYPE, db);
        mMimeTypeIdNickname = lookupMimeTypeId(Nickname.CONTENT_ITEM_TYPE, db);
        mMimeTypeIdOrganization = lookupMimeTypeId(Organization.CONTENT_ITEM_TYPE, db);
        mMimeTypeIdPhone = lookupMimeTypeId(Phone.CONTENT_ITEM_TYPE, db);
        mMimeTypeIdSip = lookupMimeTypeId(SipAddress.CONTENT_ITEM_TYPE, db);
        mMimeTypeIdStructuredName = lookupMimeTypeId(StructuredName.CONTENT_ITEM_TYPE, db);
        mMimeTypeIdStructuredPostal = lookupMimeTypeId(StructuredPostal.CONTENT_ITEM_TYPE, db);
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
        refreshDatabaseCaches(db);
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

        // Raw_contacts table
        db.execSQL("CREATE TABLE " + Tables.RAW_CONTACTS + " (" +
                RawContacts._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                RawContacts.SOURCE_ID + " TEXT," +
                RawContacts.RAW_CONTACT_IS_READ_ONLY + " INTEGER NOT NULL DEFAULT 0," +
                RawContacts.VERSION + " INTEGER NOT NULL DEFAULT 1," +
                RawContacts.DIRTY + " INTEGER NOT NULL DEFAULT 0," +
                RawContacts.DELETED + " INTEGER NOT NULL DEFAULT 0," +
                RawContacts.PHOTO_ID + " INTEGER REFERENCES data(_id)," +
                RawContacts.PHOTO_FILE_ID + " INTEGER REFERENCES photo_files(_id)," +
                RawContacts.CONTACT_TYPE + " INTEGER NOT NULL DEFAULT " + RawContacts.CONTACT_TYPE_NORMAL + "," +
                RawContacts.CUSTOM_RINGTONE + " TEXT," +
                RawContacts.TIMES_CONTACTED + " INTEGER NOT NULL DEFAULT 0," +
                RawContacts.LAST_TIME_CONTACTED + " INTEGER," +
                RawContacts.STARRED + " INTEGER NOT NULL DEFAULT 0," +
                RawContacts.PINNED + " INTEGER NOT NULL DEFAULT "  + PinnedPositions.UNPINNED + ","  +
                RawContacts.HAS_PHONE_NUMBER + " INTEGER NOT NULL DEFAULT 0," +
                RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP + " INTEGER," +
                RawContacts.DISPLAY_NAME_PRIMARY + " TEXT," +
                RawContacts.DISPLAY_NAME_ALTERNATIVE + " TEXT," +
                RawContacts.DISPLAY_NAME_SOURCE + " INTEGER NOT NULL DEFAULT " + DisplayNameSources.UNDEFINED + "," +
                RawContacts.PHONETIC_NAME + " TEXT," +
                RawContacts.PHONETIC_NAME_STYLE + " TEXT," +
                RawContacts.SORT_KEY_PRIMARY + " TEXT COLLATE " + ScContactsProvider.PHONEBOOK_COLLATOR_NAME + "," +
                RawContactsColumns.PHONEBOOK_LABEL_PRIMARY + " TEXT," +
                RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY + " INTEGER," +
                RawContacts.SORT_KEY_ALTERNATIVE + " TEXT COLLATE " + ScContactsProvider.PHONEBOOK_COLLATOR_NAME + "," +
                RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE + " TEXT," +
                RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE + " INTEGER," +
                RawContacts.NAME_VERIFIED + " INTEGER NOT NULL DEFAULT 0," +
                RawContacts.SYNC1 + " TEXT, " +
                RawContacts.SYNC2 + " TEXT, " +
                RawContacts.SYNC3 + " TEXT, " +
                RawContacts.SYNC4 + " TEXT " +
        ");");

        // deleted_contacts table
        DeletedContactsTableUtil.create(db);

        db.execSQL("CREATE TABLE " + Tables.STREAM_ITEMS + " (" +
                StreamItems._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                StreamItems.RAW_CONTACT_ID + " INTEGER NOT NULL, " +
//                StreamItems.RES_PACKAGE + " TEXT, " +
                StreamItems.RES_ICON + " TEXT, " +
                StreamItems.RES_LABEL + " TEXT, " +
                StreamItems.TEXT + " TEXT, " +
                StreamItems.TIMESTAMP + " INTEGER NOT NULL, " +
                StreamItems.COMMENTS + " TEXT, " +
                StreamItems.SYNC1 + " TEXT, " +
                StreamItems.SYNC2 + " TEXT, " +
                StreamItems.SYNC3 + " TEXT, " +
                StreamItems.SYNC4 + " TEXT, " +
                "FOREIGN KEY(" + StreamItems.RAW_CONTACT_ID + ") REFERENCES " +
                        Tables.RAW_CONTACTS + "(" + RawContacts._ID + "));");

        db.execSQL("CREATE TABLE " + Tables.STREAM_ITEM_PHOTOS + " (" +
                StreamItemPhotos._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                StreamItemPhotos.STREAM_ITEM_ID + " INTEGER NOT NULL, " +
                StreamItemPhotos.SORT_INDEX + " INTEGER, " +
                StreamItemPhotos.PHOTO_FILE_ID + " INTEGER NOT NULL, " +
                StreamItemPhotos.SYNC1 + " TEXT, " +
                StreamItemPhotos.SYNC2 + " TEXT, " +
                StreamItemPhotos.SYNC3 + " TEXT, " +
                StreamItemPhotos.SYNC4 + " TEXT, " +
                "FOREIGN KEY(" + StreamItemPhotos.STREAM_ITEM_ID + ") REFERENCES " +
                        Tables.STREAM_ITEMS + "(" + StreamItems._ID + "));");

        db.execSQL("CREATE TABLE " + Tables.PHOTO_FILES + " (" +
                PhotoFiles._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                PhotoFiles.HEIGHT + " INTEGER NOT NULL, " +
                PhotoFiles.WIDTH + " INTEGER NOT NULL, " +
                PhotoFiles.FILESIZE + " INTEGER NOT NULL);");

        // Mimetype mapping table
        db.execSQL("CREATE TABLE " + Tables.MIMETYPES + " (" +
                MimetypesColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                MimetypesColumns.MIMETYPE + " TEXT NOT NULL" +
        ");");

        // Mimetype table requires an index on mime type
        db.execSQL("CREATE UNIQUE INDEX mime_type ON " + Tables.MIMETYPES + " (" +
                MimetypesColumns.MIMETYPE +
        ");");

        // Private phone numbers table used for lookup
        db.execSQL("CREATE TABLE " + Tables.PHONE_LOOKUP + " (" +
                PhoneLookupColumns.DATA_ID
                        + " INTEGER REFERENCES data(_id) NOT NULL," +
                PhoneLookupColumns.RAW_CONTACT_ID
                        + " INTEGER REFERENCES raw_contacts(_id) NOT NULL," +
                PhoneLookupColumns.NORMALIZED_NUMBER + " TEXT NOT NULL," +
                PhoneLookupColumns.MIN_MATCH + " TEXT NOT NULL" +
        ");");

        db.execSQL("CREATE INDEX phone_lookup_index ON " + Tables.PHONE_LOOKUP + " (" +
                PhoneLookupColumns.NORMALIZED_NUMBER + "," +
                PhoneLookupColumns.RAW_CONTACT_ID + "," +
                PhoneLookupColumns.DATA_ID +
        ");");

        db.execSQL("CREATE INDEX phone_lookup_min_match_index ON " + Tables.PHONE_LOOKUP + " (" +
                PhoneLookupColumns.MIN_MATCH + "," +
                PhoneLookupColumns.RAW_CONTACT_ID + "," +
                PhoneLookupColumns.DATA_ID +
        ");");

        db.execSQL("CREATE INDEX phone_lookup_data_id_min_match_index ON " + Tables.PHONE_LOOKUP +
                " (" + PhoneLookupColumns.DATA_ID + ", " + PhoneLookupColumns.MIN_MATCH + ");");

        // Public generic data table
        db.execSQL("CREATE TABLE " + Tables.DATA + " (" +
                Data._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                DataColumns.PACKAGE_ID + " INTEGER REFERENCES package(_id)," +
                DataColumns.MIMETYPE_ID + " INTEGER REFERENCES mimetype(_id) NOT NULL," +
                Data.RAW_CONTACT_ID + " INTEGER REFERENCES raw_contacts(_id) NOT NULL," +
                Data.IS_READ_ONLY + " INTEGER NOT NULL DEFAULT 0," +
                Data.IS_PRIMARY + " INTEGER NOT NULL DEFAULT 0," +
                Data.IS_SUPER_PRIMARY + " INTEGER NOT NULL DEFAULT 0," +
                Data.DATA_VERSION + " INTEGER NOT NULL DEFAULT 0," +
                Data.DATA1 + " TEXT," +
                Data.DATA2 + " TEXT," +
                Data.DATA3 + " TEXT," +
                Data.DATA4 + " TEXT," +
                Data.DATA5 + " TEXT," +
                Data.DATA6 + " TEXT," +
                Data.DATA7 + " TEXT," +
                Data.DATA8 + " TEXT," +
                Data.DATA9 + " TEXT," +
                Data.DATA10 + " TEXT," +
                Data.DATA11 + " TEXT," +
                Data.DATA12 + " TEXT," +
                Data.DATA13 + " TEXT," +
                Data.DATA14 + " TEXT," +
                Data.DATA15 + " TEXT," +
                Data.SYNC1 + " TEXT, " +
                Data.SYNC2 + " TEXT, " +
                Data.SYNC3 + " TEXT, " +
                Data.SYNC4 + " TEXT " +
        ");");

        db.execSQL("CREATE INDEX data_raw_contact_id ON " + Tables.DATA + " (" +
                Data.RAW_CONTACT_ID +
        ");");

        /**
         * For email lookup and similar queries.
         */
        db.execSQL("CREATE INDEX data_mimetype_data1_index ON " + Tables.DATA + " (" +
                DataColumns.MIMETYPE_ID + "," +
                Data.DATA1 +
        ");");

        // Private name/nickname table used for lookup
        db.execSQL("CREATE TABLE " + Tables.NAME_LOOKUP + " (" +
                NameLookupColumns.DATA_ID + " INTEGER REFERENCES data(_id) NOT NULL," +
                NameLookupColumns.RAW_CONTACT_ID + " INTEGER REFERENCES raw_contacts(_id) NOT NULL," +
                NameLookupColumns.NORMALIZED_NAME + " TEXT NOT NULL," +
                NameLookupColumns.NAME_TYPE + " INTEGER NOT NULL," +
                "PRIMARY KEY ("
                        + NameLookupColumns.DATA_ID + ", "
                        + NameLookupColumns.NORMALIZED_NAME + ", "
                        + NameLookupColumns.NAME_TYPE + ")" +
        ");");

        db.execSQL("CREATE INDEX name_lookup_raw_contact_id_index ON " + Tables.NAME_LOOKUP + " (" +
                NameLookupColumns.RAW_CONTACT_ID +
        ");");

        db.execSQL("CREATE TABLE " + Tables.PROPERTIES + " (" +
                PropertiesColumns.PROPERTY_KEY + " TEXT PRIMARY KEY, " +
                PropertiesColumns.PROPERTY_VALUE + " TEXT " +
        ");");


        // Groups table
        db.execSQL("CREATE TABLE " + Tables.GROUPS + " (" +
                Groups._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                GroupsColumns.PACKAGE_ID + " INTEGER REFERENCES package(_id)," +
                Groups.SOURCE_ID + " TEXT," +
                Groups.VERSION + " INTEGER NOT NULL DEFAULT 1," +
                Groups.DIRTY + " INTEGER NOT NULL DEFAULT 0," +
                Groups.TITLE + " TEXT," +
                Groups.TITLE_RES + " INTEGER," +
                Groups.NOTES + " TEXT," +
                Groups.SYSTEM_ID + " TEXT," +
                Groups.DELETED + " INTEGER NOT NULL DEFAULT 0," +
                Groups.GROUP_VISIBLE + " INTEGER NOT NULL DEFAULT 0," +
                Groups.SHOULD_SYNC + " INTEGER NOT NULL DEFAULT 1," +
                Groups.AUTO_ADD + " INTEGER NOT NULL DEFAULT 0," +
                Groups.FAVORITES + " INTEGER NOT NULL DEFAULT 0," +
                Groups.GROUP_IS_READ_ONLY + " INTEGER NOT NULL DEFAULT 0," +
                Groups.SYNC1 + " TEXT, " +
                Groups.SYNC2 + " TEXT, " +
                Groups.SYNC3 + " TEXT, " +
                Groups.SYNC4 + " TEXT " +
        ");");

        createDirectoriesTable(db);
        createSearchIndexTable(db, false /* we build stats table later */);

        db.execSQL("CREATE TABLE " + Tables.DATA_USAGE_STAT + "(" +
                DataUsageStatColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                DataUsageStatColumns.DATA_ID + " INTEGER NOT NULL, " +
                DataUsageStatColumns.USAGE_TYPE_INT + " INTEGER NOT NULL DEFAULT 0, " +
                DataUsageStatColumns.TIMES_USED + " INTEGER NOT NULL DEFAULT 0, " +
                DataUsageStatColumns.LAST_TIME_USED + " INTERGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(" + DataUsageStatColumns.DATA_ID + ") REFERENCES "
                + Tables.DATA + "(" + Data._ID + ")" +
                ");");
        db.execSQL("CREATE UNIQUE INDEX data_usage_stat_index ON " +
                Tables.DATA_USAGE_STAT + " (" +
                DataUsageStatColumns.DATA_ID + ", " +
                DataUsageStatColumns.USAGE_TYPE_INT +
                ");");

        db.execSQL("CREATE TABLE " + Tables.PREFIX_TABLE + " (" +
                PrefixColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                PrefixColumns.PREFIX + " TEXT COLLATE NOCASE, " +
                PrefixColumns.CONTACT_ID + " INTEGER" +
                ");");

        /** Creates index on prefix for fast SELECT operation. */
        db.execSQL("CREATE INDEX IF NOT EXISTS nameprefix_index ON " +
                Tables.PREFIX_TABLE + " (" + PrefixColumns.PREFIX + ");");
        /** Creates index on contact_id for fast JOIN operation. */
        db.execSQL("CREATE INDEX IF NOT EXISTS nameprefix_contact_id_index ON " +
                Tables.PREFIX_TABLE + " (" + PrefixColumns.CONTACT_ID + ");");

        // When adding new tables, be sure to also add size-estimates in updateSqliteStats
        createContactsViews(db);
        createGroupsView(db);
        createContactsTriggers(db);
        createContactsIndices(db, false /* we build stats table later */);

        if (mDatabaseOptimizationEnabled) {
            db.execSQL("ANALYZE;"); // This will create a sqlite_stat1 table that is used for query optimization
            updateSqliteStats(db);
        }
    }

    private void createDirectoriesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Tables.DIRECTORIES + "(" +
                Directory._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Directory.PACKAGE_NAME + " TEXT NOT NULL," +
                Directory.DIRECTORY_AUTHORITY + " TEXT NOT NULL," +
                Directory.TYPE_RESOURCE_ID + " INTEGER," +
                DirectoryColumns.TYPE_RESOURCE_NAME + " TEXT," +
                Directory.DISPLAY_NAME + " TEXT, " +
                Directory.EXPORT_SUPPORT + " INTEGER NOT NULL" +
                        " DEFAULT " + Directory.EXPORT_SUPPORT_NONE + "," +
                Directory.SHORTCUT_SUPPORT + " INTEGER NOT NULL" +
                        " DEFAULT " + Directory.SHORTCUT_SUPPORT_NONE + "," +
                Directory.PHOTO_SUPPORT + " INTEGER NOT NULL" +
                        " DEFAULT " + Directory.PHOTO_SUPPORT_NONE +
        ");");

        // Trigger a full scan of directories in the system
        setProperty(db, DbProperties.DIRECTORY_SCAN_COMPLETE, "0");
    }

    public void createSearchIndexTable(SQLiteDatabase db, boolean rebuildSqliteStats) {
        db.execSQL("DROP TABLE IF EXISTS " + Tables.SEARCH_INDEX);

        // use FTS4 module if we drop API 10
        db.execSQL("CREATE VIRTUAL TABLE " + Tables.SEARCH_INDEX
                + " USING FTS3 ("
                    + SearchIndexColumns.RAW_CONTACT_ID + " INTEGER REFERENCES raw_contacts(_id) NOT NULL,"
                    + SearchIndexColumns.CONTENT + " TEXT, "
                    + SearchIndexColumns.NAME + " TEXT, "
                    + SearchIndexColumns.TOKENS + " TEXT"
                + ")");
    }

    private void createContactsIndices(SQLiteDatabase db, boolean rebuildSqliteStats) {
        db.execSQL("DROP INDEX IF EXISTS name_lookup_index");
        db.execSQL("CREATE INDEX name_lookup_index ON " + Tables.NAME_LOOKUP + " (" +
                NameLookupColumns.NORMALIZED_NAME + "," +
                NameLookupColumns.NAME_TYPE + ", " +
                NameLookupColumns.RAW_CONTACT_ID + ", " +
                NameLookupColumns.DATA_ID +
        ");");

        db.execSQL("DROP INDEX IF EXISTS raw_contact_sort_key1_index");
        db.execSQL("CREATE INDEX raw_contact_sort_key1_index ON " + Tables.RAW_CONTACTS + " (" +
                RawContacts.SORT_KEY_PRIMARY +
        ");");

        db.execSQL("DROP INDEX IF EXISTS raw_contact_sort_key2_index");
        db.execSQL("CREATE INDEX raw_contact_sort_key2_index ON " + Tables.RAW_CONTACTS + " (" +
                RawContacts.SORT_KEY_ALTERNATIVE +
        ");");

    }

    private void createContactsViews(SQLiteDatabase db) {
        String dataColumns =
                Data.IS_PRIMARY + ", "
                + Data.IS_SUPER_PRIMARY + ", "
                + Data.DATA_VERSION + ", "
                + DataColumns.CONCRETE_PACKAGE_ID + ","
                + DataColumns.CONCRETE_MIMETYPE_ID + ","
                + MimetypesColumns.MIMETYPE + " AS " + Data.MIMETYPE + ", "
                + Data.IS_READ_ONLY + ", "
                + Data.DATA1 + ", "
                + Data.DATA2 + ", "
                + Data.DATA3 + ", "
                + Data.DATA4 + ", "
                + Data.DATA5 + ", "
                + Data.DATA6 + ", "
                + Data.DATA7 + ", "
                + Data.DATA8 + ", "
                + Data.DATA9 + ", "
                + Data.DATA10 + ", "
                + Data.DATA11 + ", "
                + Data.DATA12 + ", "
                + Data.DATA13 + ", "
                + Data.DATA14 + ", "
                + Data.DATA15 + ", "
                + Data.SYNC1 + ", "
                + Data.SYNC2 + ", "
                + Data.SYNC3 + ", "
                + Data.SYNC4;

        String syncColumns =
                RawContactsColumns.CONCRETE_SOURCE_ID + " AS " + RawContacts.SOURCE_ID + ","
                + RawContactsColumns.CONCRETE_NAME_VERIFIED + " AS " + RawContacts.NAME_VERIFIED + ","
                + RawContactsColumns.CONCRETE_VERSION + " AS " + RawContacts.VERSION + ","
                + RawContactsColumns.CONCRETE_DIRTY + " AS " + RawContacts.DIRTY + ","
                + RawContactsColumns.CONCRETE_SYNC1 + " AS " + RawContacts.SYNC1 + ","
                + RawContactsColumns.CONCRETE_SYNC2 + " AS " + RawContacts.SYNC2 + ","
                + RawContactsColumns.CONCRETE_SYNC3 + " AS " + RawContacts.SYNC3 + ","
                + RawContactsColumns.CONCRETE_SYNC4 + " AS " + RawContacts.SYNC4;

        String baseContactColumns =
                RawContacts.HAS_PHONE_NUMBER + ", "
                + RawContacts.PHOTO_ID + ", "
                + RawContacts.PHOTO_FILE_ID  + ", "
                + RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP;

        String contactOptionColumns =
                RawContacts.CUSTOM_RINGTONE + ","
                        + RawContacts.LAST_TIME_CONTACTED + ","
                        + RawContacts.TIMES_CONTACTED + ","
                        + RawContacts.STARRED + ","
                        + RawContacts.PINNED;

        String contactNameColumns =
              RawContacts.DISPLAY_NAME_SOURCE + ", "
              + RawContacts.DISPLAY_NAME_PRIMARY + ", "
              + RawContacts.DISPLAY_NAME_ALTERNATIVE + ", "
              + RawContacts.PHONETIC_NAME + ", "
              + RawContacts.PHONETIC_NAME_STYLE + ", "
              + RawContacts.SORT_KEY_PRIMARY + ", "
              + RawContactsColumns.PHONEBOOK_LABEL_PRIMARY + ", "
              + RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY + ", "
              + RawContacts.SORT_KEY_ALTERNATIVE + ", "
              + RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE + ", "
              + RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE;

        String dataSelect = "SELECT "
                + DataColumns.CONCRETE_ID + " AS " + Data._ID + ","
                + Data.RAW_CONTACT_ID + ", "
                + syncColumns + ", "
                + dataColumns  + ", "
                + contactOptionColumns + ", "
                + contactNameColumns  + ", "
                + baseContactColumns + ", "
                + buildDisplayPhotoUriAlias(RawContactsColumns.CONCRETE_ID, RawContacts.PHOTO_URI) + ", "
                + buildThumbnailPhotoUriAlias(RawContactsColumns.CONCRETE_ID, RawContacts.PHOTO_THUMBNAIL_URI) + ", "
                + Tables.GROUPS + "." + Groups.SOURCE_ID + " AS " + GroupMembership.GROUP_SOURCE_ID
                + " FROM " + Tables.DATA
                + " JOIN " + Tables.MIMETYPES + " ON ("
                +   DataColumns.CONCRETE_MIMETYPE_ID + "=" + MimetypesColumns.CONCRETE_ID + ")"
                + " JOIN " + Tables.RAW_CONTACTS + " ON ("
                +   DataColumns.CONCRETE_RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID + ")"
//                + " LEFT OUTER JOIN " + Tables.PACKAGES + " ON ("
//                +   DataColumns.CONCRETE_PACKAGE_ID + "=" + PackagesColumns.CONCRETE_ID + ")"
                + " LEFT OUTER JOIN " + Tables.GROUPS + " ON ("
                +   MimetypesColumns.CONCRETE_MIMETYPE + "='" + GroupMembership.CONTENT_ITEM_TYPE
                +   "' AND " + GroupsColumns.CONCRETE_ID + "="
                        + Tables.DATA + "." + GroupMembership.GROUP_ROW_ID + ")"
                ;

        db.execSQL("DROP VIEW IF EXISTS " + Views.DATA + ";");
        db.execSQL("CREATE VIEW " + Views.DATA + " AS " + dataSelect);

        String rawContactOptionColumns =
                RawContacts.CUSTOM_RINGTONE + ","
                        + RawContacts.LAST_TIME_CONTACTED + ","
                        + RawContacts.TIMES_CONTACTED + ","
                        + RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP + ","
                        + RawContacts.HAS_PHONE_NUMBER + ","
                        + RawContacts.STARRED + ","
                        + RawContacts.PINNED;

        /*
         * Simulate a aggregated Contact, not 100% but nearly.
         */
        String rawContactsSelect = "SELECT "
                + RawContactsColumns.CONCRETE_ID + " AS " + RawContacts._ID + ","
                + RawContacts.CONTACT_TYPE + ", "
                + RawContacts.RAW_CONTACT_IS_READ_ONLY + ", "
                + RawContacts.DELETED + ", "
                + RawContacts.PHOTO_ID + ", "
                + RawContacts.PHOTO_FILE_ID +", "
                + RawContacts.DISPLAY_NAME_SOURCE  + ", "
                + RawContacts.DISPLAY_NAME_PRIMARY  + ", "
                + RawContacts.DISPLAY_NAME_ALTERNATIVE  + ", "
                + RawContacts.PHONETIC_NAME  + ", "
                + RawContacts.PHONETIC_NAME_STYLE  + ", "
                + RawContacts.SORT_KEY_PRIMARY  + ", "
                + RawContactsColumns.PHONEBOOK_LABEL_PRIMARY  + ", "
                + RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY  + ", "
                + RawContacts.SORT_KEY_ALTERNATIVE + ", "
                + RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE  + ", "
                + RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE  + ", "
                + buildDisplayPhotoUriAlias(RawContacts._ID, RawContacts.PHOTO_URI) + ", "
                + buildThumbnailPhotoUriAlias(RawContacts._ID, RawContacts.PHOTO_THUMBNAIL_URI) + ", "
                + rawContactOptionColumns + ", "
                + syncColumns
                + " FROM " + Tables.RAW_CONTACTS
                ;

        db.execSQL("DROP VIEW IF EXISTS " + Views.RAW_CONTACTS + ";");
        db.execSQL("CREATE VIEW " + Views.RAW_CONTACTS + " AS " + rawContactsSelect);

        String rawEntitiesSelect = "SELECT "
                + RawContacts.DISPLAY_NAME_SOURCE  + ", "
                + RawContacts.DISPLAY_NAME_PRIMARY  + ", "
                + RawContacts.DISPLAY_NAME_ALTERNATIVE  + ", "
                + RawContacts.PHONETIC_NAME  + ", "
                + RawContacts.PHOTO_ID + ", "
                + buildDisplayPhotoUriAlias(RawContactsColumns.CONCRETE_ID, RawContacts.PHOTO_URI) + ", "
                + RawContactsColumns.CONCRETE_DELETED + " AS " + RawContacts.DELETED + ","
                + dataColumns + ", "
                + syncColumns + ", "
                + Data.SYNC1 + ", "
                + Data.SYNC2 + ", "
                + Data.SYNC3 + ", "
                + Data.SYNC4 + ", "
                + RawContactsColumns.CONCRETE_ID + " AS " + RawContacts._ID + ", "
                + RawContacts.CONTACT_TYPE + ", "
                + DataColumns.CONCRETE_ID + " AS " + RawContacts.Entity.DATA_ID + ","
                + RawContacts.CUSTOM_RINGTONE + ","
                + RawContactsColumns.CONCRETE_STARRED + " AS " + RawContacts.STARRED + ","
                + RawContacts.HAS_PHONE_NUMBER + ","
                + Tables.GROUPS + "." + Groups.SOURCE_ID + " AS " + GroupMembership.GROUP_SOURCE_ID
                + " FROM " + Tables.RAW_CONTACTS
//                + " JOIN " + Tables.ACCOUNTS + " ON ("
//                +   RawContactsColumns.CONCRETE_ACCOUNT_ID + "=" + AccountsColumns.CONCRETE_ID
//                    + ")"
                + " LEFT OUTER JOIN " + Tables.DATA + " ON ("
                +   DataColumns.CONCRETE_RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID + ")"
//                + " LEFT OUTER JOIN " + Tables.PACKAGES + " ON ("
//                +   DataColumns.CONCRETE_PACKAGE_ID + "=" + PackagesColumns.CONCRETE_ID + ")"
                + " LEFT OUTER JOIN " + Tables.MIMETYPES + " ON ("
                +   DataColumns.CONCRETE_MIMETYPE_ID + "=" + MimetypesColumns.CONCRETE_ID + ")"
                + " LEFT OUTER JOIN " + Tables.GROUPS + " ON ("
                +   MimetypesColumns.CONCRETE_MIMETYPE + "='" + GroupMembership.CONTENT_ITEM_TYPE
                +   "' AND " + GroupsColumns.CONCRETE_ID + "="
                + Tables.DATA + "." + GroupMembership.GROUP_ROW_ID + ")";

        db.execSQL("DROP VIEW IF EXISTS " + Views.RAW_ENTITIES + ";");
        db.execSQL("CREATE VIEW " + Views.RAW_ENTITIES + " AS " + rawEntitiesSelect);

        String streamItemSelect = "SELECT " +
                StreamItemsColumns.CONCRETE_ID + ", " +
                StreamItemsColumns.CONCRETE_RAW_CONTACT_ID +
                        " as " + StreamItems.RAW_CONTACT_ID + ", " +
                RawContactsColumns.CONCRETE_SOURCE_ID +
                        " as " + StreamItems.RAW_CONTACT_SOURCE_ID + ", " +
//                StreamItemsColumns.CONCRETE_PACKAGE + ", " +
                StreamItemsColumns.CONCRETE_ICON + ", " +
                StreamItemsColumns.CONCRETE_LABEL + ", " +
                StreamItemsColumns.CONCRETE_TEXT + ", " +
                StreamItemsColumns.CONCRETE_TIMESTAMP + ", " +
                StreamItemsColumns.CONCRETE_COMMENTS + ", " +
                StreamItemsColumns.CONCRETE_SYNC1 + ", " +
                StreamItemsColumns.CONCRETE_SYNC2 + ", " +
                StreamItemsColumns.CONCRETE_SYNC3 + ", " +
                StreamItemsColumns.CONCRETE_SYNC4 +
                " FROM " + Tables.STREAM_ITEMS
                + " JOIN " + Tables.RAW_CONTACTS + " ON ("
                + StreamItemsColumns.CONCRETE_RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID
                    + ")";

        db.execSQL("DROP VIEW IF EXISTS " + Views.STREAM_ITEMS + ";");
        db.execSQL("CREATE VIEW " + Views.STREAM_ITEMS + " AS " + streamItemSelect);

        String dataUsageStatSelect = "SELECT "
                + DataUsageStatColumns.CONCRETE_ID + " AS " + DataUsageStatColumns._ID + ", "
                + DataUsageStatColumns.DATA_ID + ", "
                + RawContactsColumns.CONCRETE_ID + " AS " + RawContacts._ID + ", "
                + MimetypesColumns.CONCRETE_MIMETYPE + " AS " + Data.MIMETYPE + ", "
                + DataColumns.CONCRETE_RAW_CONTACT_ID + " AS " + Data.RAW_CONTACT_ID + ", "
                + DataUsageStatColumns.USAGE_TYPE_INT + ", "
                + DataUsageStatColumns.TIMES_USED + ", "
                + DataUsageStatColumns.LAST_TIME_USED
                + " FROM " + Tables.DATA_USAGE_STAT
                + " JOIN " + Tables.DATA + " ON ("
                +   DataColumns.CONCRETE_ID + "=" + DataUsageStatColumns.CONCRETE_DATA_ID + ")"
                + " JOIN " + Tables.RAW_CONTACTS + " ON ("
                +   RawContactsColumns.CONCRETE_ID + "=" + DataColumns.CONCRETE_RAW_CONTACT_ID
                + " )"
                + " JOIN " + Tables.MIMETYPES + " ON ("
                +   MimetypesColumns.CONCRETE_ID + "=" + DataColumns.CONCRETE_MIMETYPE_ID + ")";

        db.execSQL("DROP VIEW IF EXISTS " + Views.DATA_USAGE_STAT + ";");
        db.execSQL("CREATE VIEW " + Views.DATA_USAGE_STAT + " AS " + dataUsageStatSelect);
    }

    private static String buildDisplayPhotoUriAlias(String contactIdColumn, String alias) {
        return "(CASE WHEN " + RawContacts.PHOTO_FILE_ID + " IS NULL THEN (CASE WHEN "
                + RawContacts.PHOTO_ID + " IS NULL"
                + " OR " + RawContacts.PHOTO_ID + "=0"
                + " THEN NULL"
                + " ELSE '" + RawContacts.CONTENT_URI + "/'||"
                        + contactIdColumn + "|| '/" + Photo.CONTENT_DIRECTORY + "'"
                + " END) ELSE '" + DisplayPhoto.CONTENT_URI + "/'||"
                        + RawContacts.PHOTO_FILE_ID + " END)"
                + " AS " + alias;
    }

    private static String buildThumbnailPhotoUriAlias(String contactIdColumn, String alias) {
        return "(CASE WHEN "
                + RawContacts.PHOTO_ID + " IS NULL"
                + " OR " + RawContacts.PHOTO_ID + "=0"
                + " THEN NULL"
                + " ELSE '" + RawContacts.CONTENT_URI + "/'||"
                        + contactIdColumn + "|| '/" + Photo.CONTENT_DIRECTORY + "'"
                + " END)"
                + " AS " + alias;
    }

    private void createGroupsView(SQLiteDatabase db) {
        db.execSQL("DROP VIEW IF EXISTS " + Views.GROUPS + ";");

        String groupsColumns =
                Groups.SOURCE_ID + ","
                + Groups.VERSION + ","
                + Groups.DIRTY + ","
                + Groups.TITLE + ","
                + Groups.TITLE_RES + ","
                + Groups.NOTES + ","
                + Groups.SYSTEM_ID + ","
                + Groups.DELETED + ","
                + Groups.GROUP_VISIBLE + ","
                + Groups.SHOULD_SYNC + ","
                + Groups.AUTO_ADD + ","
                + Groups.FAVORITES + ","
                + Groups.GROUP_IS_READ_ONLY + ","
                + Groups.SYNC1 + ","
                + Groups.SYNC2 + ","
                + Groups.SYNC3 + ","
                + Groups.SYNC4; // + ","
//                + PackagesColumns.PACKAGE + " AS " + Groups.RES_PACKAGE;

        // WD - the view is the same as the groups table - I leave it in to provide for further extensions
        String groupsSelect = "SELECT "
                + GroupsColumns.CONCRETE_ID + " AS " + Groups._ID + ","
                + groupsColumns
                + " FROM " + Tables.GROUPS;
//                + " LEFT OUTER JOIN " + Tables.PACKAGES + " ON ("
//                    + GroupsColumns.CONCRETE_PACKAGE_ID + "=" + PackagesColumns.CONCRETE_ID + ")";

        db.execSQL("CREATE VIEW " + Views.GROUPS + " AS " + groupsSelect);
    }

    private void createContactsTriggers(SQLiteDatabase db) {

        /*
         * Automatically delete Data rows when a raw contact is deleted.
         */
        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.RAW_CONTACTS + "_deleted;");
        db.execSQL("CREATE TRIGGER " + Tables.RAW_CONTACTS + "_deleted "
                + "   BEFORE DELETE ON " + Tables.RAW_CONTACTS
                + " BEGIN "
                + "   DELETE FROM " + Tables.DATA
                + "     WHERE " + Data.RAW_CONTACT_ID
                                + "=OLD." + RawContacts._ID + ";"
//                + "   DELETE FROM " + Tables.DEFAULT_DIRECTORY
//                + "     WHERE " + Contacts._ID + "=OLD." + RawContacts.CONTACT_ID
//                + "       AND (SELECT COUNT(*) FROM " + Tables.RAW_CONTACTS
//                + "            WHERE " + RawContacts.CONTACT_ID + "=OLD." + RawContacts.CONTACT_ID
//                + "           )=1;"
                + " END");


        db.execSQL("DROP TRIGGER IF EXISTS contacts_times_contacted;");
        db.execSQL("DROP TRIGGER IF EXISTS raw_contacts_times_contacted;");

        /*
         * Triggers that update {@link RawContacts#VERSION} when the contact is
         * marked for deletion or any time a data row is inserted, updated or
         * deleted.
         */
        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.RAW_CONTACTS + "_marked_deleted;");
        db.execSQL("CREATE TRIGGER " + Tables.RAW_CONTACTS + "_marked_deleted "
                + "   AFTER UPDATE ON " + Tables.RAW_CONTACTS
                + " BEGIN "
                + "   UPDATE " + Tables.RAW_CONTACTS
                + "     SET "
                +         RawContacts.VERSION + "=OLD." + RawContacts.VERSION + "+1 "
                + "     WHERE " + RawContacts._ID + "=OLD." + RawContacts._ID
                + "       AND NEW." + RawContacts.DELETED + "!= OLD." + RawContacts.DELETED + ";"
                + " END");

        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.DATA + "_updated;");
        db.execSQL("CREATE TRIGGER " + Tables.DATA + "_updated AFTER UPDATE ON " + Tables.DATA
                + " BEGIN "
                + "   UPDATE " + Tables.DATA
                + "     SET " + Data.DATA_VERSION + "=OLD." + Data.DATA_VERSION + "+1 "
                + "     WHERE " + Data._ID + "=OLD." + Data._ID + ";"
                + "   UPDATE " + Tables.RAW_CONTACTS
                + "     SET " + RawContacts.VERSION + "=" + RawContacts.VERSION + "+1 "
                + "     WHERE " + RawContacts._ID + "=OLD." + Data.RAW_CONTACT_ID + ";"
                + " END");

        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.DATA + "_deleted;");
        db.execSQL("CREATE TRIGGER " + Tables.DATA + "_deleted BEFORE DELETE ON " + Tables.DATA
                + " BEGIN "
                + "   UPDATE " + Tables.RAW_CONTACTS
                + "     SET " + RawContacts.VERSION + "=" + RawContacts.VERSION + "+1 "
                + "     WHERE " + RawContacts._ID + "=OLD." + Data.RAW_CONTACT_ID + ";"
                + "   DELETE FROM " + Tables.PHONE_LOOKUP
                + "     WHERE " + PhoneLookupColumns.DATA_ID + "=OLD." + Data._ID + ";"
//                + "   DELETE FROM " + Tables.STATUS_UPDATES
//                + "     WHERE " + StatusUpdatesColumns.DATA_ID + "=OLD." + Data._ID + ";"
                + "   DELETE FROM " + Tables.NAME_LOOKUP
                + "     WHERE " + NameLookupColumns.DATA_ID + "=OLD." + Data._ID + ";"
                + " END");


        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.GROUPS + "_updated1;");
        db.execSQL("CREATE TRIGGER " + Tables.GROUPS + "_updated1 "
                + "   AFTER UPDATE ON " + Tables.GROUPS
                + " BEGIN "
                + "   UPDATE " + Tables.GROUPS
                + "     SET "
                +         Groups.VERSION + "=OLD." + Groups.VERSION + "+1"
                + "     WHERE " + Groups._ID + "=OLD." + Groups._ID + ";"
                + " END");

/* Add this if we fully deal with directories, visible/invisible directories for contacts, etc.
 * */
        // Update DEFAULT_FILTER table per AUTO_ADD column update.
        // See also upgradeToVersion411().
//        final String insertContactsWithoutAccount = (
//                " INSERT OR IGNORE INTO " + Tables.DEFAULT_DIRECTORY +
//                "     SELECT " + RawContacts.CONTACT_ID +
//                "     FROM " + Tables.RAW_CONTACTS +
//                "     WHERE " + RawContactsColumns.CONCRETE_ACCOUNT_ID +
//                            "=" + Clauses.LOCAL_ACCOUNT_ID + ";");
//        final String insertContactsWithAccountNoDefaultGroup = (
//                " INSERT OR IGNORE INTO " + Tables.DEFAULT_DIRECTORY +
//                "     SELECT " + RawContacts.CONTACT_ID +
//                "         FROM " + Tables.RAW_CONTACTS +
//                "     WHERE NOT EXISTS" +
//                "         (SELECT " + Groups._ID +
//                "             FROM " + Tables.GROUPS +
//                "             WHERE " + RawContactsColumns.CONCRETE_ACCOUNT_ID + " = " +
//                                    GroupsColumns.CONCRETE_ACCOUNT_ID +
//                "             AND " + Groups.AUTO_ADD + " != 0" + ");");
//        final String insertContactsWithAccountDefaultGroup = (
//                " INSERT OR IGNORE INTO " + Tables.DEFAULT_DIRECTORY +
//                "     SELECT " + RawContacts.CONTACT_ID +
//                "         FROM " + Tables.RAW_CONTACTS +
//                "     JOIN " + Tables.DATA +
//                "           ON (" + RawContactsColumns.CONCRETE_ID + "=" +
//                        Data.RAW_CONTACT_ID + ")" +
//                "     WHERE " + DataColumns.MIMETYPE_ID + "=" +
//                    "(SELECT " + MimetypesColumns._ID + " FROM " + Tables.MIMETYPES +
//                        " WHERE " + MimetypesColumns.MIMETYPE +
//                            "='" + GroupMembership.CONTENT_ITEM_TYPE + "')" +
//                "     AND EXISTS" +
//                "         (SELECT " + Groups._ID +
//                "             FROM " + Tables.GROUPS +
//                "                 WHERE " + RawContactsColumns.CONCRETE_ACCOUNT_ID + " = " +
//                                        GroupsColumns.CONCRETE_ACCOUNT_ID +
//                "                 AND " + Groups.AUTO_ADD + " != 0" + ");");
//
//        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.GROUPS + "_auto_add_updated1;");
//        db.execSQL("CREATE TRIGGER " + Tables.GROUPS + "_auto_add_updated1 "
//                + "   AFTER UPDATE OF " + Groups.AUTO_ADD + " ON " + Tables.GROUPS
//                + " BEGIN "
//                + "   DELETE FROM " + Tables.DEFAULT_DIRECTORY + ";"
//                    + insertContactsWithoutAccount
//                    + insertContactsWithAccountNoDefaultGroup
//                    + insertContactsWithAccountDefaultGroup
//                + " END");
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
            createContactsViews(db);
            createGroupsView(db);
            createContactsTriggers(db);
            createContactsIndices(db, false /* we build stats table later */);
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
        db.execSQL("CREATE TABLE data_usage_stat(" +
                "stat_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "data_id INTEGER NOT NULL, " +
                "usage_type INTEGER NOT NULL DEFAULT 0, " +
                "times_used INTEGER NOT NULL DEFAULT 0, " +
                "last_time_used INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(data_id) REFERENCES data(_id));");
        db.execSQL("CREATE UNIQUE INDEX data_usage_stat_index ON " +
                "data_usage_stat (data_id, usage_type)");

        db.execSQL("ALTER TABLE raw_contacts ADD pinned INTEGER NOT NULL DEFAULT  " +
                ScContactsContract.PinnedPositions.UNPINNED + ";");
    }

    private void upgradeToVersion103(SQLiteDatabase db) {

        ContentValues values = new ContentValues(1);
        values.put(Phone.TYPE, SipAddress.TYPE_SILENT);

        int result = db.update(Tables.DATA, values,
                Phone.NUMBER + " LIKE '%" + mContext.getString(R.string.sc_sip_domain_0) + "%' AND " 
                        + Phone.TYPE + " IS NULL", null);
    }

    private void upgradeToVersion104(SQLiteDatabase db) {

        // Adding additional columns to Call log
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_INT1 + " INTEGER;");
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_INT2 + " INTEGER;");
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_TEXT1 + " TEXT;");
        db.execSQL("ALTER TABLE " + Tables.CALLS + " ADD " +  ScCalls.SC_OPTION_TEXT2 + " TEXT;");

        // Adding timestamp to contacts table.
        db.execSQL("ALTER TABLE " + Tables.RAW_CONTACTS
                + " ADD contact_last_updated_timestamp INTEGER;");

        db.execSQL("UPDATE " + Tables.RAW_CONTACTS
                + " SET contact_last_updated_timestamp"
                + " = " + System.currentTimeMillis());

        db.execSQL("CREATE INDEX contacts_contact_last_updated_timestamp_index "
                + "ON " + Tables.RAW_CONTACTS + "(contact_last_updated_timestamp)");

        // New deleted contacts table.
        DeletedContactsTableUtil.create(db);
    }

    private void upgradeToVersion105(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Tables.PREFIX_TABLE + " (" +
                PrefixColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                PrefixColumns.PREFIX + " TEXT COLLATE NOCASE, " +
                PrefixColumns.CONTACT_ID + " INTEGER" +
                ");");

        /** Creates index on prefix for fast SELECT operation. */
        db.execSQL("CREATE INDEX IF NOT EXISTS nameprefix_index ON " +
                Tables.PREFIX_TABLE + " (" + PrefixColumns.PREFIX + ");");
        /** Creates index on contact_id for fast JOIN operation. */
        db.execSQL("CREATE INDEX IF NOT EXISTS nameprefix_contact_id_index ON " +
                Tables.PREFIX_TABLE + " (" + PrefixColumns.CONTACT_ID + ");");
    }
        
    private void bindString(SQLiteStatement stmt, int index, String value) {
        if (value == null) {
            stmt.bindNull(index);
        }
        else {
            stmt.bindString(index, value);
        }
    }

    /**
     * Regenerates all locale-sensitive data: nickname_lookup, name_lookup and sort keys.
     */
    public void setLocale(ScContactsProvider provider, Locale locale) {
        Log.i(TAG, "Switching to locale " + locale);

        final long start = SystemClock.elapsedRealtime();
        SQLiteDatabase db = getDatabase(true);
        db.setLocale(locale);
        db.beginTransaction();
        try {
            db.execSQL("DROP INDEX IF EXISTS raw_contact_sort_key1_index");
            db.execSQL("DROP INDEX IF EXISTS raw_contact_sort_key2_index");
            db.execSQL("DROP INDEX IF EXISTS name_lookup_index");

//            loadNicknameLookupTable(db);
            insertNameLookup(db);
            rebuildSortKeys(db, provider);
            createContactsIndices(db, true);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        Log.i(TAG, "Locale change completed in " + (SystemClock.elapsedRealtime() - start) + "ms");
    }

    /**
     * Regenerates sort keys for all contacts.
     */
    private void rebuildSortKeys(SQLiteDatabase db, ScContactsProvider provider) {
        Cursor cursor = db.query(Tables.RAW_CONTACTS, new String[]{RawContacts._ID},
                null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long rawContactId = cursor.getLong(0);
                updateRawContactDisplayName(db, rawContactId);
            }
        } finally {
            cursor.close();
        }
    }

    private void insertNameLookup(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + Tables.NAME_LOOKUP);

        SQLiteStatement nameLookupInsert = db.compileStatement(
                "INSERT OR IGNORE INTO " + Tables.NAME_LOOKUP + "("
                        + NameLookupColumns.RAW_CONTACT_ID + ","
                        + NameLookupColumns.DATA_ID + ","
                        + NameLookupColumns.NAME_TYPE + ","
                        + NameLookupColumns.NORMALIZED_NAME +
                ") VALUES (?,?,?,?)");

        try {
            insertStructuredNameLookup(db, nameLookupInsert);
            insertEmailLookup(db, nameLookupInsert);
//            insertNicknameLookup(db, nameLookupInsert);
        } finally {
            nameLookupInsert.close();
        }
    }

    private static final class StructuredNameQuery {
        public static final String TABLE = Tables.DATA;

        public static final String SELECTION =
                DataColumns.MIMETYPE_ID + "=? AND " + Data.DATA1 + " NOT NULL";

        public static final String COLUMNS[] = {
                StructuredName._ID,
                StructuredName.RAW_CONTACT_ID,
                StructuredName.DISPLAY_NAME,
        };

        public static final int ID = 0;
        public static final int RAW_CONTACT_ID = 1;
        public static final int DISPLAY_NAME = 2;
    }

    private class StructuredNameLookupBuilder extends NameLookupBuilder {

        private final SQLiteStatement mNameLookupInsert;
//        private final CommonNicknameCache mCommonNicknameCache;

        public StructuredNameLookupBuilder(NameSplitter splitter,
                /* CommonNicknameCache commonNicknameCache, */SQLiteStatement nameLookupInsert) {
            super(splitter);
            // this.mCommonNicknameCache = commonNicknameCache;
            this.mNameLookupInsert = nameLookupInsert;
        }

        @Override
        protected void insertNameLookup(long rawContactId, long dataId, int lookupType, String name) {
            if (!TextUtils.isEmpty(name)) {
                ScContactsDatabaseHelper.this.insertNormalizedNameLookup(mNameLookupInsert, rawContactId, dataId, lookupType,
                        name);
            }
        }

        @Override
        protected String[] getCommonNicknameClusters(String normalizedName) {
            return null; // mCommonNicknameCache.getCommonNicknameClusters(normalizedName);
        }
    }

    /**
     * Inserts name lookup rows for all structured names in the database.
     */
    private void insertStructuredNameLookup(SQLiteDatabase db, SQLiteStatement nameLookupInsert) {
        NameSplitter nameSplitter = createNameSplitter();
        NameLookupBuilder nameLookupBuilder = new StructuredNameLookupBuilder(nameSplitter, /* new CommonNicknameCache(db), */
                nameLookupInsert);
        final long mimeTypeId = lookupMimeTypeId(db, StructuredName.CONTENT_ITEM_TYPE);

        Cursor cursor = db.query(StructuredNameQuery.TABLE, StructuredNameQuery.COLUMNS, StructuredNameQuery.SELECTION,
                new String[] { String.valueOf(mimeTypeId) }, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long dataId = cursor.getLong(StructuredNameQuery.ID);
                long rawContactId = cursor.getLong(StructuredNameQuery.RAW_CONTACT_ID);
                String name = cursor.getString(StructuredNameQuery.DISPLAY_NAME);
                int fullNameStyle = nameSplitter.guessFullNameStyle(name);
                fullNameStyle = nameSplitter.getAdjustedFullNameStyle(fullNameStyle);
                nameLookupBuilder.insertNameLookup(rawContactId, dataId, name, fullNameStyle);
            }
        }
        finally {
            cursor.close();
        }
    }

    private static final class EmailQuery {
        public static final String TABLE = Tables.DATA;

        public static final String SELECTION =
                DataColumns.MIMETYPE_ID + "=? AND " + Data.DATA1 + " NOT NULL";

        public static final String COLUMNS[] = {
                Email._ID,
                Email.RAW_CONTACT_ID,
                Email.ADDRESS,
        };

        public static final int ID = 0;
        public static final int RAW_CONTACT_ID = 1;
        public static final int ADDRESS = 2;
    }

    /**
     * Inserts name lookup rows for all email addresses in the database.
     */
    private void insertEmailLookup(SQLiteDatabase db, SQLiteStatement nameLookupInsert) {
        final long mimeTypeId = lookupMimeTypeId(db, Email.CONTENT_ITEM_TYPE);
        Cursor cursor = db.query(EmailQuery.TABLE, EmailQuery.COLUMNS, EmailQuery.SELECTION,
                new String[] { String.valueOf(mimeTypeId) }, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long dataId = cursor.getLong(EmailQuery.ID);
                long rawContactId = cursor.getLong(EmailQuery.RAW_CONTACT_ID);
                String address = cursor.getString(EmailQuery.ADDRESS);
                address = extractHandleFromEmailAddress(address);
                insertNameLookup(nameLookupInsert, rawContactId, dataId, NameLookupType.EMAIL_BASED_NICKNAME, address);
            }
        }
        finally {
            cursor.close();
        }
    }

    /**
     * Inserts a record in the {@link Tables#NAME_LOOKUP} table.
     */
    public void insertNameLookup(SQLiteStatement stmt, long rawContactId, long dataId, int lookupType, String name) {

        if (TextUtils.isEmpty(name)) {
            return;
        }

        String normalized = NameNormalizer.normalize(name);
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        insertNormalizedNameLookup(stmt, rawContactId, dataId, lookupType, normalized);
    }

    private void insertNormalizedNameLookup(SQLiteStatement stmt, long rawContactId, long dataId, int lookupType,
            String normalizedName) {

        stmt.bindLong(1, rawContactId);
        stmt.bindLong(2, dataId);
        stmt.bindLong(3, lookupType);
        stmt.bindString(4, normalizedName);
        stmt.executeInsert();
    }

    /**
     * Perform an internal string-to-integer lookup using the compiled
     * {@link net.sqlcipher.database.SQLiteStatement} provided. If a mapping isn't found in database, it will be
     * created. All new, uncached answers are added to the cache automatically.
     *
     * @param query Compiled statement used to query for the mapping.
     * @param insert Compiled statement used to insert a new mapping when no
     *            existing one is found in cache or from query.
     * @param value Value to find mapping for.
     * @param cache In-memory cache of previous answers.
     * @return An unique integer mapping for the given value.
     */
    private long lookupAndCacheId(SQLiteStatement query, SQLiteStatement insert, String value, HashMap<String, Long> cache) {
        long id = -1;
        try {
            // Try searching database for mapping
            DatabaseUtils.bindObjectToProgram(query, 1, value);
            id = query.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            // Nothing found, so try inserting new mapping
            DatabaseUtils.bindObjectToProgram(insert, 1, value);
            id = insert.executeInsert();
        }
        if (id != -1) {
            // Cache and return the new answer
            cache.put(value, id);
            return id;
        } else {
            // Otherwise throw if no mapping found or created
            throw new IllegalStateException("Couldn't find or create internal lookup table entry for value " + value);
        }
    }


    /**
     * Convert a mimetype into an integer, using {@link Tables#MIMETYPES} for
     * lookups and possible allocation of new IDs as needed.
     */
    public long getMimeTypeId(String mimetype) {
        // Try an in-memory cache lookup
        if (mMimetypeCache.containsKey(mimetype))
            return mMimetypeCache.get(mimetype);

        return lookupMimeTypeId(mimetype, getDatabase(true));
    }

    private long lookupMimeTypeId(String mimetype, SQLiteDatabase db) {
        final SQLiteStatement mimetypeQuery = db.compileStatement(
                "SELECT " + MimetypesColumns._ID +
                " FROM " + Tables.MIMETYPES +
                " WHERE " + MimetypesColumns.MIMETYPE + "=?");

        final SQLiteStatement mimetypeInsert = db.compileStatement(
                "INSERT INTO " + Tables.MIMETYPES + "("
                        + MimetypesColumns.MIMETYPE +
                ") VALUES (?)");

        try {
            return lookupAndCacheId(mimetypeQuery, mimetypeInsert, mimetype, mMimetypeCache);
        } finally {
            mimetypeQuery.close();
            mimetypeInsert.close();
        }
    }

    public long getMimeTypeIdForStructuredName() {
        return mMimeTypeIdStructuredName;
    }

    public long getMimeTypeIdForStructuredPostal() {
        return mMimeTypeIdStructuredPostal;
    }

    public long getMimeTypeIdForOrganization() {
        return mMimeTypeIdOrganization;
    }

    public long getMimeTypeIdForIm() {
        return mMimeTypeIdIm;
    }

    public long getMimeTypeIdForEmail() {
        return mMimeTypeIdEmail;
    }

    public long getMimeTypeIdForPhone() {
        return mMimeTypeIdPhone;
    }

    public long getMimeTypeIdForSip() {
        return mMimeTypeIdSip;
    }

    public int getDisplayNameSourceForMimeTypeId(int mimeTypeId) {
        if (mimeTypeId == mMimeTypeIdStructuredName) {
            return DisplayNameSources.STRUCTURED_NAME;
        } else if (mimeTypeId == mMimeTypeIdEmail) {
            return DisplayNameSources.EMAIL;
        } else if (mimeTypeId == mMimeTypeIdPhone) {
            return DisplayNameSources.PHONE;
        } else if (mimeTypeId == mMimeTypeIdOrganization) {
            return DisplayNameSources.ORGANIZATION;
        } else if (mimeTypeId == mMimeTypeIdNickname) {
            return DisplayNameSources.NICKNAME;
        } else {
            return DisplayNameSources.UNDEFINED;
        }
    }

    /**
     * Find the mimetype for the given {@link com.silentcircle.silentcontacts.ScContactsContract.Data#_ID}.
     */
    public String getDataMimeType(long dataId) {
        if (mDataMimetypeQuery == null) {
            mDataMimetypeQuery = getDatabase(true).compileStatement(
                    "SELECT " + MimetypesColumns.MIMETYPE +
                    " FROM " + Tables.DATA_JOIN_MIMETYPES +
                    " WHERE " + Tables.DATA + "." + Data._ID + "=?");
        }
        try {
            // Try database query to find mimetype
            DatabaseUtils.bindObjectToProgram(mDataMimetypeQuery, 1, dataId);
            String mimetype = mDataMimetypeQuery.simpleQueryForString();
            return mimetype;
        } catch (SQLiteDoneException e) {
            // No valid mapping found, so return null
            return null;
        }
    }

    public void invalidateAllCache() {
        Log.w(TAG, "invalidateAllCache: [ ScContactsDatabaseHelper ]");

        mMimetypeCache.clear();
        mPackageCache.clear();
    }

    private interface RawContactNameQuery {
        public static final String RAW_SQL =
                "SELECT "
                        + DataColumns.MIMETYPE_ID + ","
                        + Data.IS_PRIMARY + ","
                        + Data.DATA1 + ","
                        + Data.DATA2 + ","
                        + Data.DATA3 + ","
                        + Data.DATA4 + ","
                        + Data.DATA5 + ","
                        + Data.DATA6 + ","
                        + Data.DATA7 + ","
                        + Data.DATA8 + ","
                        + Data.DATA9 + ","
                        + Data.DATA10 + ","
                        + Data.DATA11 +
                " FROM " + Tables.DATA +
                " WHERE " + Data.RAW_CONTACT_ID + "=?" +
                        " AND (" + Data.DATA1 + " NOT NULL OR " +
                                Organization.TITLE + " NOT NULL)";

        public static final int MIMETYPE = 0;
        public static final int IS_PRIMARY = 1;
        public static final int DATA1 = 2;
        public static final int GIVEN_NAME = 3;                         // data2
        public static final int FAMILY_NAME = 4;                        // data3
        public static final int PREFIX = 5;                             // data4
        public static final int TITLE = 5;                              // data4
        public static final int MIDDLE_NAME = 6;                        // data5
        public static final int SUFFIX = 7;                             // data6
        public static final int PHONETIC_GIVEN_NAME = 8;                // data7
        public static final int PHONETIC_MIDDLE_NAME = 9;               // data8
        public static final int ORGANIZATION_PHONETIC_NAME = 9;         // data8
        public static final int PHONETIC_FAMILY_NAME = 10;              // data9
        public static final int FULL_NAME_STYLE = 11;                   // data10
        public static final int ORGANIZATION_PHONETIC_NAME_STYLE = 11;  // data10
        public static final int PHONETIC_NAME_STYLE = 12;               // data11
    }

    /**
     * Updates a raw contact display name based on data rows, e.g. structured name,
     * organization, email etc.
     */
    public void updateRawContactDisplayName(SQLiteDatabase db, long rawContactId) {
        if (mNameSplitter == null) {
            createNameSplitter();
        }

        int bestDisplayNameSource = DisplayNameSources.UNDEFINED;
        NameSplitter.Name bestName = null;
        String bestDisplayName = null;
        String bestPhoneticName = null;
        int bestPhoneticNameStyle = PhoneticNameStyle.UNDEFINED;

        mSelectionArgs1[0] = String.valueOf(rawContactId);
        Cursor c = db.rawQuery(RawContactNameQuery.RAW_SQL, mSelectionArgs1);
        try {
            while (c.moveToNext()) {
                int mimeType = c.getInt(RawContactNameQuery.MIMETYPE);
                int source = getDisplayNameSourceForMimeTypeId(mimeType);
                if (source < bestDisplayNameSource || source == DisplayNameSources.UNDEFINED) {
                    continue;
                }

                if (source == bestDisplayNameSource
                        && c.getInt(RawContactNameQuery.IS_PRIMARY) == 0) {
                    continue;
                }

                if (mimeType == getMimeTypeIdForStructuredName()) {
                    NameSplitter.Name name;
                    if (bestName != null) {
                        name = new NameSplitter.Name();
                    } else {
                        name = mName;
                        name.clear();
                    }
                    name.prefix = c.getString(RawContactNameQuery.PREFIX);
                    name.givenNames = c.getString(RawContactNameQuery.GIVEN_NAME);
                    name.middleName = c.getString(RawContactNameQuery.MIDDLE_NAME);
                    name.familyName = c.getString(RawContactNameQuery.FAMILY_NAME);
                    name.suffix = c.getString(RawContactNameQuery.SUFFIX);
                    name.fullNameStyle = c.isNull(RawContactNameQuery.FULL_NAME_STYLE)
                            ? FullNameStyle.UNDEFINED
                            : c.getInt(RawContactNameQuery.FULL_NAME_STYLE);
                    name.phoneticFamilyName = c.getString(RawContactNameQuery.PHONETIC_FAMILY_NAME);
                    name.phoneticMiddleName = c.getString(RawContactNameQuery.PHONETIC_MIDDLE_NAME);
                    name.phoneticGivenName = c.getString(RawContactNameQuery.PHONETIC_GIVEN_NAME);
                    name.phoneticNameStyle = c.isNull(RawContactNameQuery.PHONETIC_NAME_STYLE)
                            ? PhoneticNameStyle.UNDEFINED
                            : c.getInt(RawContactNameQuery.PHONETIC_NAME_STYLE);
                    if (!name.isEmpty()) {
                        bestDisplayNameSource = source;
                        bestName = name;
                    }
                } else if (mimeType == getMimeTypeIdForOrganization()) {
                    mCharArrayBuffer.sizeCopied = 0;
                    c.copyStringToBuffer(RawContactNameQuery.DATA1, mCharArrayBuffer);
                    if (mCharArrayBuffer.sizeCopied != 0) {
                        bestDisplayNameSource = source;
                        bestDisplayName = new String(mCharArrayBuffer.data, 0,
                                mCharArrayBuffer.sizeCopied);
                        bestPhoneticName = c.getString(
                                RawContactNameQuery.ORGANIZATION_PHONETIC_NAME);
                        bestPhoneticNameStyle =
                                c.isNull(RawContactNameQuery.ORGANIZATION_PHONETIC_NAME_STYLE)
                                   ? PhoneticNameStyle.UNDEFINED
                                   : c.getInt(RawContactNameQuery.ORGANIZATION_PHONETIC_NAME_STYLE);
                    } else {
                        c.copyStringToBuffer(RawContactNameQuery.TITLE, mCharArrayBuffer);
                        if (mCharArrayBuffer.sizeCopied != 0) {
                            bestDisplayNameSource = source;
                            bestDisplayName = new String(mCharArrayBuffer.data, 0,
                                    mCharArrayBuffer.sizeCopied);
                            bestPhoneticName = null;
                            bestPhoneticNameStyle = PhoneticNameStyle.UNDEFINED;
                        }
                    }
                } else {
                    // Display name is at DATA1 in all other types.
                    // This is ensured in the constructor.

                    mCharArrayBuffer.sizeCopied = 0;
                    c.copyStringToBuffer(RawContactNameQuery.DATA1, mCharArrayBuffer);
                    if (mCharArrayBuffer.sizeCopied != 0) {
                        bestDisplayNameSource = source;
                        bestDisplayName = new String(mCharArrayBuffer.data, 0, mCharArrayBuffer.sizeCopied);
                        bestPhoneticName = null;
                        bestPhoneticNameStyle = PhoneticNameStyle.UNDEFINED;
                    }
                }
            }

        } finally {
            c.close();
        }

        String displayNamePrimary;
        String displayNameAlternative;
        String sortNamePrimary;
        String sortNameAlternative;
        String sortKeyPrimary = null;
        String sortKeyAlternative = null;
        int displayNameStyle = FullNameStyle.UNDEFINED;

        if (bestDisplayNameSource == DisplayNameSources.STRUCTURED_NAME) {
            displayNameStyle = bestName.fullNameStyle;
            if (displayNameStyle == FullNameStyle.CJK
                    || displayNameStyle == FullNameStyle.UNDEFINED) {
                displayNameStyle = mNameSplitter.getAdjustedFullNameStyle(displayNameStyle);
                bestName.fullNameStyle = displayNameStyle;
            }

            displayNamePrimary = mNameSplitter.join(bestName, true, true);
            displayNameAlternative = mNameSplitter.join(bestName, false, true);

            if (TextUtils.isEmpty(bestName.prefix)) {
                sortNamePrimary = displayNamePrimary;
                sortNameAlternative = displayNameAlternative;
            } else {
                sortNamePrimary = mNameSplitter.join(bestName, true, false);
                sortNameAlternative = mNameSplitter.join(bestName, false, false);
            }

            bestPhoneticName = mNameSplitter.joinPhoneticName(bestName);
            bestPhoneticNameStyle = bestName.phoneticNameStyle;
        } else {
            displayNamePrimary = displayNameAlternative = bestDisplayName;
            sortNamePrimary = sortNameAlternative = bestDisplayName;
        }

        if (bestPhoneticName != null) {
            if (displayNamePrimary == null) {
                displayNamePrimary = bestPhoneticName;
            }
            if (displayNameAlternative == null) {
                displayNameAlternative = bestPhoneticName;
            }
            // Phonetic names disregard name order so displayNamePrimary and displayNameAlternative
            // are the same.
            sortKeyPrimary = sortKeyAlternative = bestPhoneticName;
            if (bestPhoneticNameStyle == PhoneticNameStyle.UNDEFINED) {
                bestPhoneticNameStyle = mNameSplitter.guessPhoneticNameStyle(bestPhoneticName);
            }
        } else {
            bestPhoneticNameStyle = PhoneticNameStyle.UNDEFINED;
            if (displayNameStyle == FullNameStyle.UNDEFINED) {
                displayNameStyle = mNameSplitter.guessFullNameStyle(bestDisplayName);
                if (displayNameStyle == FullNameStyle.UNDEFINED
                        || displayNameStyle == FullNameStyle.CJK) {
                    displayNameStyle = mNameSplitter.getAdjustedNameStyleBasedOnPhoneticNameStyle(
                            displayNameStyle, bestPhoneticNameStyle);
                }
                displayNameStyle = mNameSplitter.getAdjustedFullNameStyle(displayNameStyle);
            }
            if (displayNameStyle == FullNameStyle.CHINESE ||
                    displayNameStyle == FullNameStyle.CJK) {
                sortKeyPrimary = sortKeyAlternative = sortNamePrimary;
            }
        }

        if (sortKeyPrimary == null) {
            sortKeyPrimary = sortNamePrimary;
            sortKeyAlternative = sortNameAlternative;
        }

        String phonebookLabelPrimary = "";
        String phonebookLabelAlternative = "";
        int phonebookBucketPrimary = 0;
        int phonebookBucketAlternative = 0;
        ContactLocaleUtils43 localeUtils = ContactLocaleUtils43.getInstance();

        if (sortKeyPrimary != null) {
            phonebookBucketPrimary = localeUtils.getBucketIndex(sortKeyPrimary);
            phonebookLabelPrimary = localeUtils.getBucketLabel(phonebookBucketPrimary);
        }
        if (sortKeyAlternative != null) {
            phonebookBucketAlternative = localeUtils.getBucketIndex(sortKeyAlternative);
            phonebookLabelAlternative = localeUtils.getBucketLabel(phonebookBucketAlternative);
        }
        if (mRawContactDisplayNameUpdate == null) {
            mRawContactDisplayNameUpdate = db.compileStatement(
                    "UPDATE " + Tables.RAW_CONTACTS +
                    " SET " +
                            RawContacts.DISPLAY_NAME_SOURCE + "=?," +
                            RawContacts.DISPLAY_NAME_PRIMARY + "=?," +
                            RawContacts.DISPLAY_NAME_ALTERNATIVE + "=?," +
                            RawContacts.PHONETIC_NAME + "=?," +
                            RawContacts.PHONETIC_NAME_STYLE + "=?," +
                            RawContacts.SORT_KEY_PRIMARY + "=?," +
                            RawContactsColumns.PHONEBOOK_LABEL_PRIMARY + "=?," +
                            RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY + "=?," +
                            RawContacts.SORT_KEY_ALTERNATIVE + "=?," +
                            RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE + "=?," +
                            RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE + "=?" +
                    " WHERE " + RawContacts._ID + "=?");
        }

        mRawContactDisplayNameUpdate.bindLong(1, bestDisplayNameSource);
        bindString(mRawContactDisplayNameUpdate, 2, displayNamePrimary);
        bindString(mRawContactDisplayNameUpdate, 3, displayNameAlternative);
        bindString(mRawContactDisplayNameUpdate, 4, bestPhoneticName);
        mRawContactDisplayNameUpdate.bindLong(5, bestPhoneticNameStyle);
        bindString(mRawContactDisplayNameUpdate, 6, sortKeyPrimary);
        bindString(mRawContactDisplayNameUpdate, 7, phonebookLabelPrimary);
        mRawContactDisplayNameUpdate.bindLong(8, phonebookBucketPrimary);
        bindString(mRawContactDisplayNameUpdate, 9, sortKeyAlternative);
        bindString(mRawContactDisplayNameUpdate, 10, phonebookLabelAlternative);
        mRawContactDisplayNameUpdate.bindLong(11, phonebookBucketAlternative);
        mRawContactDisplayNameUpdate.bindLong(12, rawContactId);
        mRawContactDisplayNameUpdate.execute();
    }

    /*
     * Sets the given dataId record in the "data" table to primary, and resets all data records of
     * the same mimetype and under the same contact to not be primary.
     *
     * @param dataId the id of the data record to be set to primary. Pass -1 to clear the primary
     * flag of all data items of this raw contacts
     */
    public void setIsPrimary(long rawContactId, long dataId, long mimeTypeId) {
        if (mSetPrimaryStatement == null) {
            mSetPrimaryStatement = getDatabase(true).compileStatement(
                    "UPDATE " + Tables.DATA +
                    " SET " + Data.IS_PRIMARY + "=(_id=?)" +
                    " WHERE " + DataColumns.MIMETYPE_ID + "=?" +
                    "   AND " + Data.RAW_CONTACT_ID + "=?");
        }
        mSetPrimaryStatement.bindLong(1, dataId);
        mSetPrimaryStatement.bindLong(2, mimeTypeId);
        mSetPrimaryStatement.bindLong(3, rawContactId);
        mSetPrimaryStatement.execute();
    }

    /*
     * Clears the super primary of all data items of the given raw contact. does not touch
     * other raw contacts of the same joined aggregate
     */
    public void clearSuperPrimary(long rawContactId, long mimeTypeId) {
        if (mClearSuperPrimaryStatement == null) {
            mClearSuperPrimaryStatement = getDatabase(true).compileStatement(
                    "UPDATE " + Tables.DATA +
                    " SET " + Data.IS_SUPER_PRIMARY + "=0" +
                    " WHERE " + DataColumns.MIMETYPE_ID + "=?" +
                    "   AND " + Data.RAW_CONTACT_ID + "=?");
        }
        mClearSuperPrimaryStatement.bindLong(1, mimeTypeId);
        mClearSuperPrimaryStatement.bindLong(2, rawContactId);
        mClearSuperPrimaryStatement.execute();
    }

    /*
     * Sets the given dataId record in the "data" table to "super primary", and resets all data
     * records of the same mimetype and under the same aggregate to not be "super primary".
     *
     * @param dataId the id of the data record to be set to primary.
     */
    public void setIsSuperPrimary(long rawContactId, long dataId, long mimeTypeId) {
        if (mSetSuperPrimaryStatement == null) {
            mSetSuperPrimaryStatement = getDatabase(true).compileStatement(
                    "UPDATE " + Tables.DATA +
                    " SET " + Data.IS_SUPER_PRIMARY + "=(" + Data._ID + "=?)" +
                    " WHERE " + DataColumns.MIMETYPE_ID + "=?" +
                    "   AND " + Data.RAW_CONTACT_ID + " =?");
        }
        mSetSuperPrimaryStatement.bindLong(1, dataId);
        mSetSuperPrimaryStatement.bindLong(2, mimeTypeId);
        mSetSuperPrimaryStatement.bindLong(3, rawContactId);
        mSetSuperPrimaryStatement.execute();
    }

    /**
     * Performs a query and returns true if any Data item of the raw contact with the given
     * id and mimetype is marked as super-primary
     */
    public boolean rawContactHasSuperPrimary(long rawContactId, long mimeTypeId) {
        final Cursor existsCursor = getDatabase(false).rawQuery(
                "SELECT EXISTS(SELECT 1 FROM " + Tables.DATA +
                " WHERE " + Data.RAW_CONTACT_ID + "=?" +
                " AND " + DataColumns.MIMETYPE_ID + "=?" +
                " AND " + Data.IS_SUPER_PRIMARY + "<>0)",
                new String[] { String.valueOf(rawContactId), String.valueOf(mimeTypeId) });
        try {
            if (!existsCursor.moveToFirst()) throw new IllegalStateException();
            return existsCursor.getInt(0) != 0;
        } finally {
            existsCursor.close();
        }
    }

    /**
     * Inserts a record in the {@link Tables#NAME_LOOKUP} table.
     */
    public void insertNameLookup(long rawContactId, long dataId, int lookupType, String name) {
        if (TextUtils.isEmpty(name)) {
            return;
        }

        if (mNameLookupInsert == null) {
            mNameLookupInsert = getDatabase(true).compileStatement(
                    "INSERT OR IGNORE INTO " + Tables.NAME_LOOKUP + "("
                            + NameLookupColumns.RAW_CONTACT_ID + ","
                            + NameLookupColumns.DATA_ID + ","
                            + NameLookupColumns.NAME_TYPE + ","
                            + NameLookupColumns.NORMALIZED_NAME
                    + ") VALUES (?,?,?,?)");
        }
        mNameLookupInsert.bindLong(1, rawContactId);
        mNameLookupInsert.bindLong(2, dataId);
        mNameLookupInsert.bindLong(3, lookupType);
        bindString(mNameLookupInsert, 4, name);
        mNameLookupInsert.executeInsert();
    }

    /**
     * Deletes all {@link Tables#NAME_LOOKUP} table rows associated with the specified data element.
     */
    public void deleteNameLookup(long dataId) {
        if (mNameLookupDelete == null) {
            mNameLookupDelete = getDatabase(true).compileStatement(
                    "DELETE FROM " + Tables.NAME_LOOKUP +
                    " WHERE " + NameLookupColumns.DATA_ID + "=?");
        }
        mNameLookupDelete.bindLong(1, dataId);
        mNameLookupDelete.execute();
    }

    public String extractHandleFromEmailAddress(String email) {
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(email);
        if (tokens.length == 0) {
            return null;
        }

        String address = tokens[0].getAddress();
        int at = address.indexOf('@');
        if (at != -1) {
            return address.substring(0, at);
        }
        return null;
    }

    public String insertNameLookupForEmail(long rawContactId, long dataId, String email) {
        if (TextUtils.isEmpty(email)) {
            return null;
        }

        String address = extractHandleFromEmailAddress(email);
        if (address == null) {
            return null;
        }
        insertNameLookup(rawContactId, dataId, NameLookupType.EMAIL_BASED_NICKNAME, NameNormalizer.normalize(address));
        return address;
    }

    public String getCurrentCountryIso() {
        String locale = mContext.getResources().getConfiguration().locale.getCountry();
        return locale;
    }

    public void insertNameLookupForPhoneticName(long rawContactId, long dataId, String familyName,
            String middleName, String givenName) {
        mSb.setLength(0);
        if (familyName != null) {
            mSb.append(familyName.trim());
        }
        if (middleName != null) {
            mSb.append(middleName.trim());
        }
        if (givenName != null) {
            mSb.append(givenName.trim());
        }

        if (mSb.length() > 0) {
            insertNameLookup(rawContactId, dataId, NameLookupType.NAME_COLLATION_KEY,
                    NameNormalizer.normalize(mSb.toString()));
        }
    }

    public NameSplitter createNameSplitter() {
        mNameSplitter = new NameSplitter(
               mContext.getString(R.string.common_name_prefixes),
               mContext.getString(R.string.common_last_name_prefixes),
               mContext.getString(R.string.common_name_suffixes),
               mContext.getString(R.string.common_name_conjunctions),
               Locale.getDefault());
        return mNameSplitter;
    }
    /**
     * Returns a detailed exception message for the supplied URI.  It includes the calling
     * user and calling package(s).
     */
    public String exceptionMessage(Uri uri) {
        return exceptionMessage(null, uri);
    }

    /**
     * Resets the {@link com.silentcircle.silentcontacts.ScContactsContract.RawContacts#NAME_VERIFIED} flag to 0 on all other raw
     * contacts in the same aggregate
     *
     */
    public void resetNameVerifiedForOtherRawContacts(long rawContactId) {
        if (mResetNameVerifiedForOtherRawContacts == null) {
            mResetNameVerifiedForOtherRawContacts = getDatabase(true).compileStatement(
                    "UPDATE " + Tables.RAW_CONTACTS +
                    " SET " + RawContacts.NAME_VERIFIED + "=0" +
                    " WHERE " + RawContacts._ID + "!=?");
        }
        mResetNameVerifiedForOtherRawContacts.bindLong(1, rawContactId);
        mResetNameVerifiedForOtherRawContacts.execute();
    }

    /**
     * Wipes all data except mime type and package lookup tables.
     */
    public void wipeData() {
        SQLiteDatabase db = getDatabase(true);

        db.execSQL("DELETE FROM " + Tables.RAW_CONTACTS + ";");
        db.execSQL("DELETE FROM " + Tables.STREAM_ITEMS + ";");
        db.execSQL("DELETE FROM " + Tables.STREAM_ITEM_PHOTOS + ";");
        db.execSQL("DELETE FROM " + Tables.PHOTO_FILES + ";");
        db.execSQL("DELETE FROM " + Tables.DATA + ";");
        db.execSQL("DELETE FROM " + Tables.PHONE_LOOKUP + ";");
        db.execSQL("DELETE FROM " + Tables.NAME_LOOKUP + ";");
        db.execSQL("DELETE FROM " + Tables.GROUPS + ";");
//        db.execSQL("DELETE FROM " + Tables.AGGREGATION_EXCEPTIONS + ";");
//        db.execSQL("DELETE FROM " + Tables.SETTINGS + ";");
        db.execSQL("DELETE FROM " + Tables.CALLS + ";");
        db.execSQL("DELETE FROM " + Tables.DIRECTORIES + ";");
        db.execSQL("DELETE FROM " + Tables.SEARCH_INDEX + ";");
        db.execSQL("DELETE FROM " + Tables.DELETED_CONTACTS + ";");

        initializeCache(db);

        // Note: we are not removing reference data from Tables.NICKNAME_LOOKUP
    }
    /**
     * Test if the given column appears in the given projection.
     */
    public static boolean isInProjection(String[] projection, String column) {
        if (projection == null) {
            return true; // Null means "all columns".  We can't really tell if it's in there...
        }
        for (String test : projection) {
            if (column.equals(test)) {
                return true;
            }
        }
        return false;
    }

    public void buildPhoneLookupAndContactQuery(SQLiteQueryBuilder qb, String normalizedNumber, String numberE164) {
        String minMatch = PhoneNumberUtils.toCallerIDMinMatch(normalizedNumber);
        StringBuilder sb = new StringBuilder();
        appendPhoneLookupTables(sb, minMatch);
        qb.setTables(sb.toString());

        sb = new StringBuilder();
        appendPhoneLookupSelection(sb, normalizedNumber, numberE164);
        qb.appendWhere(sb.toString());
    }

    /**
     * Phone lookup method that uses the custom SQLite function phone_number_compare_loose
     * that serves as a fallback in case the regular lookup does not return any results.
     * @param qb The query builder.
     * @param number The phone number to search for.
     */
    public void buildFallbackPhoneLookupAndContactQuery(SQLiteQueryBuilder qb, String number) {
        final String minMatch = PhoneNumberUtils.toCallerIDMinMatch(number);
        final StringBuilder sb = new StringBuilder();
        //append lookup tables
        sb.append(Views.RAW_CONTACTS);

        // Removed the Views.CONTACTS join - no support of aggregated Contacts
        sb.append(" JOIN (SELECT " + PhoneLookupColumns.DATA_ID + "," +
                PhoneLookupColumns.NORMALIZED_NUMBER + " FROM "+ Tables.PHONE_LOOKUP + " "
                + "WHERE (" + Tables.PHONE_LOOKUP + "." + PhoneLookupColumns.MIN_MATCH + " = '");
        sb.append(minMatch);
        sb.append("')) AS lookup " +
                "ON lookup." + PhoneLookupColumns.DATA_ID + "=" + Tables.DATA + "." + Data._ID
                + " JOIN " + Tables.DATA + " "
                + "ON " + Tables.DATA + "." + Data.RAW_CONTACT_ID + "=" + Views.RAW_CONTACTS + "."
                + RawContacts._ID);

        qb.setTables(sb.toString());

        sb.setLength(0);
        sb.append("PHONE_NUMBERS_EQUAL(" + Tables.DATA + "." + Phone.NUMBER + ", ");
        DatabaseUtils.appendEscapedSQLString(sb, number);
        sb.append(mUseStrictPhoneNumberComparison ? ", 1)" : ", 0)");
        qb.appendWhere(sb.toString());
    }

    /**
     * Adds query for selecting the contact with the given {@code sipAddress} to the given
     * {@link StringBuilder}.
     *
     * @return the query arguments to be passed in with the query
     */
    public String[] buildSipContactQuery(StringBuilder sb, String sipAddress) {
        sb.append("upper(");
        sb.append(Data.DATA1);
        sb.append(")=upper(?) AND ");
        sb.append(DataColumns.MIMETYPE_ID);
        sb.append("=");
        sb.append(Long.toString(getMimeTypeIdForSip()));
        // Return the arguments to be passed to the query.
        return new String[]{ sipAddress };
    }

    public String buildPhoneLookupAsNestedQuery(String number) {
        StringBuilder sb = new StringBuilder();
        final String minMatch = PhoneNumberUtils.toCallerIDMinMatch(number);
        sb.append("(SELECT DISTINCT raw_contact_id" + " FROM ");
        appendPhoneLookupTables(sb, minMatch);
        sb.append(" WHERE ");
        appendPhoneLookupSelection(sb, number, null);
        sb.append(")");
        return sb.toString();
    }

    private void appendPhoneLookupTables(StringBuilder sb, final String minMatch) {
//        sb.append(Tables.RAW_CONTACTS);
        sb.append(Views.RAW_CONTACTS);
        sb.append(", (SELECT data_id, normalized_number, length(normalized_number) as len "
                + " FROM phone_lookup " + " WHERE (" + Tables.PHONE_LOOKUP + "."
                + PhoneLookupColumns.MIN_MATCH + " = '");
        sb.append(minMatch);
        sb.append("')) AS lookup, " + Tables.DATA);
    }

    private void appendPhoneLookupSelection(StringBuilder sb, String number, String numberE164) {
        sb.append("lookup.data_id=data._id AND data.raw_contact_id=view_raw_contacts._id");
        boolean hasNumberE164 = !TextUtils.isEmpty(numberE164);
        boolean hasNumber = !TextUtils.isEmpty(number);
        if (hasNumberE164 || hasNumber) {
            sb.append(" AND ( ");
            if (hasNumberE164) {
                sb.append(" lookup.normalized_number = ");
                DatabaseUtils.appendEscapedSQLString(sb, numberE164);
            }
            if (hasNumberE164 && hasNumber) {
                sb.append(" OR ");
            }
            if (hasNumber) {
                // skip the suffix match entirely if we are using strict number comparison
                if (!mUseStrictPhoneNumberComparison) {
                    int numberLen = number.length();
                    sb.append(" lookup.len <= ");
                    sb.append(numberLen);
                    sb.append(" AND substr(");
                    DatabaseUtils.appendEscapedSQLString(sb, number);
                    sb.append(',');
                    sb.append(numberLen);
                    sb.append(" - lookup.len + 1) = lookup.normalized_number");

                    // Some countries (e.g. Brazil) can have incoming calls which contain only the local
                    // number (no country calling code and no area code). This case is handled below.
                    // Details see b/5197612.
                    // This also handles a Gingerbread -> ICS upgrade issue; see b/5638376.
                    sb.append(" OR (");
                    sb.append(" lookup.len > ");
                    sb.append(numberLen);
                    sb.append(" AND substr(lookup.normalized_number,");
                    sb.append("lookup.len + 1 - ");
                    sb.append(numberLen);
                    sb.append(") = ");
                    DatabaseUtils.appendEscapedSQLString(sb, number);
                    sb.append(")");
                } else {
                    sb.append("0");
                }
            }
            sb.append(')');
        }
    }

    /**
     * Test if any of the columns appear in the given projection.
     */
    public static boolean isInProjection(String[] projection, String... columns) {
        if (projection == null) {
            return true;
        }

        // Optimized for a single-column test
        if (columns.length == 1) {
            return isInProjection(projection, columns[0]);
        } else {
            for (String test : projection) {
                for (String column : columns) {
                    if (column.equals(test)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the value from the {@link Tables#PROPERTIES} table.
     */
    public String getProperty(String key, String defaultValue) {
        Cursor cursor = getDatabase(false).query(Tables.PROPERTIES,
                new String[]{PropertiesColumns.PROPERTY_VALUE},
                PropertiesColumns.PROPERTY_KEY + "=?",
                new String[]{key}, null, null, null);
        String value = null;
        try {
            if (cursor.moveToFirst()) {
                value = cursor.getString(0);
            }
        } finally {
            cursor.close();
        }

        return value != null ? value : defaultValue;
    }

    /**
     * Stores a key-value pair in the {@link Tables#PROPERTIES} table.
     */
    public void setProperty(String key, String value) {
        setProperty(getDatabase(true), key, value);
    }

    private void setProperty(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put(PropertiesColumns.PROPERTY_KEY, key);
        values.put(PropertiesColumns.PROPERTY_VALUE, value);
        db.replace(Tables.PROPERTIES, null, values);
    }


    /**
     * Returns a detailed exception message for the supplied URI.  It includes the calling
     * user and calling package(s).
     * 
     * TODO - enable for real operations.
     */
    public String exceptionMessage(String message, Uri uri) {
        StringBuilder sb = new StringBuilder();
        if (message != null) {
            sb.append(message).append("; ");
        }
        sb.append("URI: ").append(uri);
//        final PackageManager pm = mContext.getPackageManager();
        int callingUid = Binder.getCallingUid();
        sb.append(", calling user: ");
//        String userName = pm.getNameForUid(callingUid);
        String userName = null;
        if (userName != null) {
            sb.append(userName);
        } else {
            sb.append(callingUid);
        }

//        final String[] callerPackages = pm.getPackagesForUid(callingUid);
//        if (callerPackages != null && callerPackages.length > 0) {
//            if (callerPackages.length == 1) {
//                sb.append(", calling package:");
//                sb.append(callerPackages[0]);
//            } else {
//                sb.append(", calling package is one of: [");
//                for (int i = 0; i < callerPackages.length; i++) {
//                    if (i != 0) {
//                        sb.append(", ");
//                    }
//                    sb.append(callerPackages[i]);
//                }
//                sb.append("]");
//            }
//        }

        return sb.toString();
    }

    private static long lookupMimeTypeId(SQLiteDatabase db, String mimeType) {
        try {
            return DatabaseUtils.longForQuery(db,
                    "SELECT " + MimetypesColumns._ID +
                            " FROM " + Tables.MIMETYPES +
                            " WHERE " + MimetypesColumns.MIMETYPE
                            + "='" + mimeType + "'", null
            );
        } catch (SQLiteDoneException e) {
            // No rows of this type in the database
            return -1;
        }
    }

    public String extractAddressFromEmailAddress(String email) {
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(email);
        if (tokens.length == 0) {
            return null;
        }
        return tokens[0].getAddress().trim();
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

            updateIndexStats(db, Tables.RAW_CONTACTS, "raw_contact_sort_key2_index", "10000 2");
            updateIndexStats(db, Tables.RAW_CONTACTS, "raw_contact_sort_key1_index", "10000 2");

            updateIndexStats(db, Tables.RAW_CONTACTS, MoreDatabaseUtils.buildIndexName(Tables.RAW_CONTACTS,
                    RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP), "9000 10");

            updateIndexStats(db, Tables.NAME_LOOKUP, "name_lookup_raw_contact_id_index", "35000 4");
            updateIndexStats(db, Tables.NAME_LOOKUP, "name_lookup_index", "35000 2 2 2 1");
            updateIndexStats(db, Tables.NAME_LOOKUP, "sqlite_autoindex_name_lookup_1", "35000 3 2 1");

            updateIndexStats(db, Tables.PHONE_LOOKUP, "phone_lookup_index", "3500 3 2 1");
            updateIndexStats(db, Tables.PHONE_LOOKUP, "phone_lookup_min_match_index", "3500 3 2 2");
            updateIndexStats(db, Tables.PHONE_LOOKUP, "phone_lookup_data_id_min_match_index", "3500 2 2");

            updateIndexStats(db, Tables.DATA, "data_mimetype_data1_index", "60000 5000 2");
            updateIndexStats(db, Tables.DATA, "data_raw_contact_id", "60000 10");

            updateIndexStats(db, Tables.CALLS, null, "250");

            updateIndexStats(db, Tables.STREAM_ITEMS, null, "500");
            updateIndexStats(db, Tables.STREAM_ITEM_PHOTOS, null, "50");

            updateIndexStats(db, Tables.PHOTO_FILES, null, "50");

            updateIndexStats(db, Tables.MIMETYPES, "mime_type", "18 1");

            updateIndexStats(db, Tables.GROUPS, null, "50");

            updateIndexStats(db, Tables.DATA_USAGE_STAT, "data_usage_stat_index", "20 2 1");

            // Tiny tables
            updateIndexStats(db, Tables.DIRECTORIES, null, "3");
            updateIndexStats(db, Tables.PROPERTIES, "sqlite_autoindex_properties_1", "4 1");

            updateIndexStats(db, "android_metadata", null, "1");
            updateIndexStats(db, "_sync_state", "sqlite_autoindex__sync_state_1", "2 1 1");
            updateIndexStats(db, "_sync_state_metadata", null, "1");

            // Search index
            updateIndexStats(db, "search_index_docsize", null, "9000");
            updateIndexStats(db, "search_index_content", null, "9000");
            updateIndexStats(db, "search_index_stat",    null, "1");
            updateIndexStats(db, "search_index_segments", null, "450");
            updateIndexStats(db, "search_index_segdir", "sqlite_autoindex_search_index_segdir_1", "9 5 1");

            updateIndexStats(db, Tables.PREFIX_TABLE, "nameprefix_index", "10000 3");
            updateIndexStats(db, Tables.PREFIX_TABLE, "nameprefix_contact_id_index", "10000 2");

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
