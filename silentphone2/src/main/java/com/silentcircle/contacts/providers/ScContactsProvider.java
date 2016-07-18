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
 * This provider implementation is a heavily edited version of the original Android ContactsProvider sources.
 * Any enhancement of this provider can and should follow the original provider implementation. To make
 * this more convenient I left the original names of variables and functions.
 */

package com.silentcircle.contacts.providers;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.EntityIterator;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.silentcircle.contacts.database.ContactsTableUtil;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DataColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DataUsageStatColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.GroupsColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Joins;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.NameLookupColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.NameLookupType;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.PhoneLookupColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.PhotoFilesColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Projections;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.RawContactsColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.SearchIndexColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.StreamItemPhotosColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.StreamItemsColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Views;
import com.silentcircle.contacts.providers.aggregation.SimpleRawContactAggregator;
import com.silentcircle.contacts.utils.Clock;
import com.silentcircle.contacts.utils.DbQueryUtils;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentcontacts2.ScBaseColumns;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.GroupMembership;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Note;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Organization;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.SipAddress;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredName;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.DataUsageFeedback;
import com.silentcircle.silentcontacts2.ScContactsContract.DeletedContacts;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentcontacts2.ScContactsContract.DisplayPhoto;
import com.silentcircle.silentcontacts2.ScContactsContract.Groups;
import com.silentcircle.silentcontacts2.ScContactsContract.PhoneLookup;
import com.silentcircle.silentcontacts2.ScContactsContract.PhotoFiles;
import com.silentcircle.silentcontacts2.ScContactsContract.PinnedPositions;
import com.silentcircle.silentcontacts2.ScContactsContract.ProviderStatus;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContactsEntity;
import com.silentcircle.silentcontacts2.ScContactsContract.SearchSnippetColumns;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItemPhotos;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItems;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.vcard.VCardComposer;
import com.silentcircle.vcard.VCardConfig;

import net.sqlcipher.AbstractCursor;
import net.sqlcipher.Cursor;
import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.MatrixCursor;
import net.sqlcipher.MatrixCursor.RowBuilder;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteQueryBuilder;
import net.sqlcipher.database.SQLiteTransactionListener;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class ScContactsProvider extends ContentProvider implements SQLiteTransactionListener {

    private static final String TAG = "ScContactsProvider";
    private static boolean DEBUG = false;
    private static boolean VERBOSE_DEBUG = false;

    
    static final String PHONEBOOK_COLLATOR_NAME = "PHONEBOOK";

    static final String PHOTO_STORE_KEY = "photo_store_key";
    static final String PHOTO_STORE_IV = "photo_store_iv";


    // Regex for splitting query strings - we split on any group of non-alphanumeric characters,
    // excluding the @ symbol.
    /* package */ static final String QUERY_TOKENIZER_REGEX = "[^\\w@]+";

    /** Limit for the maximum number of social stream items to store under a raw contact. */
    private static final int MAX_STREAM_ITEMS_PER_RAW_CONTACT = 5;

    /** Rate limit (in ms) for photo cleanup.  Do it at most once per day. */
    private static final int PHOTO_CLEANUP_RATE_LIMIT = 24 * 60 * 60 * 1000;

    private static final int USAGE_TYPE_ALL = -1;

    private static final String PREF_LOCALE = "locale";

    private static final int BACKGROUND_TASK_INITIALIZE = 0;
    private static final int BACKGROUND_TASK_OPEN_WRITE_ACCESS = 1;
    private static final int BACKGROUND_TASK_UPDATE_ACCOUNTS = 3;
    private static final int BACKGROUND_TASK_UPDATE_LOCALE = 4;
    private static final int BACKGROUND_TASK_UPDATE_SEARCH_INDEX = 6;
    private static final int BACKGROUND_TASK_UPDATE_PROVIDER_STATUS = 7;
    private static final int BACKGROUND_TASK_CHANGE_LOCALE = 9;
    private static final int BACKGROUND_TASK_CLEANUP_PHOTOS = 10;

    private static final int RAW_CONTACTS = 2002;
    private static final int RAW_CONTACTS_ID = 2003;
    private static final int RAW_CONTACTS_ID_DATA = 2004;
    private static final int RAW_CONTACT_ID_ENTITY = 2005;
    private static final int RAW_CONTACTS_ID_DISPLAY_PHOTO = 2006;
    private static final int RAW_CONTACTS_ID_STREAM_ITEMS = 2007;
    private static final int RAW_CONTACTS_ID_STREAM_ITEMS_ID = 2008;
    private static final int RAW_CONTACTS_FILTER = 1005;
    private static final int RAW_CONTACTS_STREQUENT = 1006;
    private static final int RAW_CONTACTS_STREQUENT_FILTER = 1007;

    private static final int RAW_CONTACTS_ID_PHOTO = 1009;

    private static final int RAW_CONTACTS_AS_VCARD = 1015;
    private static final int RAW_CONTACTS_AS_MULTI_VCARD = 1016;
    private static final int RAW_CONTACTS_FREQUENT = 1025;
    private static final int RAW_CONTACTS_DELETE_USAGE = 1026;
    
    private static final int DATA = 3000;
    private static final int DATA_ID = 3001;
    private static final int PHONES = 3002;
    private static final int PHONES_ID = 3003;
    private static final int PHONES_FILTER = 3004;
    private static final int EMAILS = 3005;
    private static final int EMAILS_ID = 3006;
    private static final int EMAILS_LOOKUP = 3007;
    private static final int EMAILS_FILTER = 3008;
    private static final int POSTALS = 3009;
    private static final int POSTALS_ID = 3010;
    private static final int CALLABLES = 3011;
    private static final int CALLABLES_ID = 3012;
    private static final int CALLABLES_FILTER = 3013;

    private static final int IM = 3105;
    private static final int IM_ID = 3106;
    private static final int IM_LOOKUP = 3107;
    private static final int IM_FILTER = 3108;

    private static final int PHONE_LOOKUP = 4000;

    private static final int SYNCSTATE_ID = 11001;

    private static final int RAW_CONTACT_ENTITIES = 15001;

    private static final int COMPLETE_NAME = 18000;

    private static final int DATA_USAGE_FEEDBACK_ID = 20001;

    private static final int STREAM_ITEMS = 21000;
    private static final int STREAM_ITEMS_PHOTOS = 21001;
    private static final int STREAM_ITEMS_ID = 21002;
    private static final int STREAM_ITEMS_ID_PHOTOS = 21003;
    private static final int STREAM_ITEMS_ID_PHOTOS_ID = 21004;
    private static final int STREAM_ITEMS_LIMIT = 21005;

    private static final int DISPLAY_PHOTO_ID = 22000;
    private static final int PHOTO_DIMENSIONS = 22001;

    private static final int GROUPS = 10000;
    private static final int GROUPS_ID = 10001;
    private static final int GROUPS_SUMMARY = 10003;

    private static final int DIRECTORIES = 17001;
    private static final int DIRECTORIES_ID = 17002;

    private static final int PROVIDER_STATUS = 16001;

    private static final int DELETED_CONTACTS = 23000;
    private static final int DELETED_CONTACTS_ID = 23001;

    private static final String DEFAULT_SNIPPET_ARG_START_MATCH = "[";
    private static final String DEFAULT_SNIPPET_ARG_END_MATCH = "]";
    private static final String DEFAULT_SNIPPET_ARG_ELLIPSIS = "...";
    private static final int DEFAULT_SNIPPET_ARG_MAX_TOKENS = -10;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // Contacts URI matching table
        final UriMatcher matcher = sUriMatcher;

        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts", RAW_CONTACTS);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/#", RAW_CONTACTS_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/#/data", RAW_CONTACTS_ID_DATA);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/#/display_photo", RAW_CONTACTS_ID_DISPLAY_PHOTO);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/#/entity", RAW_CONTACT_ID_ENTITY);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/#/stream_items", RAW_CONTACTS_ID_STREAM_ITEMS);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/#/stream_items/#", RAW_CONTACTS_ID_STREAM_ITEMS_ID);

        // Added to match functionality of aggregated Contacts
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/#/photo", RAW_CONTACTS_ID_PHOTO);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/filter", RAW_CONTACTS_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/filter/*", RAW_CONTACTS_FILTER);

        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contact_entities", RAW_CONTACT_ENTITIES);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/as_vcard/#", RAW_CONTACTS_AS_VCARD); // '#' was '*'
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/as_multi_vcard/*", RAW_CONTACTS_AS_MULTI_VCARD);

        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/strequent/", RAW_CONTACTS_STREQUENT);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/strequent/filter/*", RAW_CONTACTS_STREQUENT_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/frequent", RAW_CONTACTS_FREQUENT);

        matcher.addURI(ScContactsContract.AUTHORITY, "raw_contacts/delete_usage", RAW_CONTACTS_DELETE_USAGE);

        matcher.addURI(ScContactsContract.AUTHORITY, "complete_name", COMPLETE_NAME);

        matcher.addURI(ScContactsContract.AUTHORITY, "data", DATA);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/#", DATA_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/phones", PHONES);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/phones/#", PHONES_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/phones/filter", PHONES_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/phones/filter/*", PHONES_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/emails", EMAILS);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/emails/#", EMAILS_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/emails/lookup", EMAILS_LOOKUP);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/emails/lookup/*", EMAILS_LOOKUP);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/emails/filter", EMAILS_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/emails/filter/*", EMAILS_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/postals", POSTALS);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/postals/#", POSTALS_ID);
        /** "*" is in CSV form with data ids ("123,456,789") */
        matcher.addURI(ScContactsContract.AUTHORITY, "data/usagefeedback/*", DATA_USAGE_FEEDBACK_ID);

        matcher.addURI(ScContactsContract.AUTHORITY, "data/im", IM);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/im/#", IM_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/im/lookup", IM_LOOKUP);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/im/lookup/*", IM_LOOKUP);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/im/filter", IM_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/im/filter/*", IM_FILTER);

        matcher.addURI(ScContactsContract.AUTHORITY, "data/callables/", CALLABLES);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/callables/#", CALLABLES_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/callables/filter", CALLABLES_FILTER);
        matcher.addURI(ScContactsContract.AUTHORITY, "data/callables/filter/*", CALLABLES_FILTER);

        matcher.addURI(ScContactsContract.AUTHORITY, "groups", GROUPS);
        matcher.addURI(ScContactsContract.AUTHORITY, "groups/#", GROUPS_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "groups_summary", GROUPS_SUMMARY);

        matcher.addURI(ScContactsContract.AUTHORITY, "phone_lookup/*", PHONE_LOOKUP);

        matcher.addURI(ScContactsContract.AUTHORITY, "stream_items", STREAM_ITEMS);
        matcher.addURI(ScContactsContract.AUTHORITY, "stream_items/photo", STREAM_ITEMS_PHOTOS);
        matcher.addURI(ScContactsContract.AUTHORITY, "stream_items/#", STREAM_ITEMS_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "stream_items/#/photo", STREAM_ITEMS_ID_PHOTOS);
        matcher.addURI(ScContactsContract.AUTHORITY, "stream_items/#/photo/#", STREAM_ITEMS_ID_PHOTOS_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "stream_items_limit", STREAM_ITEMS_LIMIT);

        matcher.addURI(ScContactsContract.AUTHORITY, "display_photo/#", DISPLAY_PHOTO_ID);
        matcher.addURI(ScContactsContract.AUTHORITY, "photo_dimensions", PHOTO_DIMENSIONS);

        matcher.addURI(ScContactsContract.AUTHORITY, "directories", DIRECTORIES);
        matcher.addURI(ScContactsContract.AUTHORITY, "directories/#", DIRECTORIES_ID);

        matcher.addURI(ScContactsContract.AUTHORITY, "provider_status", PROVIDER_STATUS);

        matcher.addURI(ScContactsContract.AUTHORITY, "deleted_contacts", DELETED_CONTACTS);
        matcher.addURI(ScContactsContract.AUTHORITY, "deleted_contacts/#", DELETED_CONTACTS_ID);

    }

    // Contacts.XXX_YYY names are same as RawContacts.XXX_YYY, thus we can map them
    private static final ProjectionMap sRawContactsColumnsAdd = ProjectionMap.builder()
            .add(RawContacts.CUSTOM_RINGTONE)
            .add(RawContacts.DISPLAY_NAME_PRIMARY)
            .add(RawContacts.DISPLAY_NAME_ALTERNATIVE)
            .add(RawContacts.DISPLAY_NAME_SOURCE)
//            .add(Contacts.IN_VISIBLE_GROUP)
            .add(RawContacts.LAST_TIME_CONTACTED)
            .add(RawContacts.PHONETIC_NAME)
            .add(RawContacts.PHONETIC_NAME_STYLE)
            .add(RawContacts.PHOTO_ID)
            .add(RawContacts.PHOTO_FILE_ID)
            .add(RawContacts.PHOTO_URI)
            .add(RawContacts.PHOTO_THUMBNAIL_URI)
            .add(RawContacts.STARRED)
            .add(RawContacts.TIMES_CONTACTED)
            .add(RawContacts.HAS_PHONE_NUMBER)
            .add(RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP)
            .build();

    private static final ProjectionMap sDataColumns = ProjectionMap.builder()
            .add(Data.DATA1)
            .add(Data.DATA2)
            .add(Data.DATA3)
            .add(Data.DATA4)
            .add(Data.DATA5)
            .add(Data.DATA6)
            .add(Data.DATA7)
            .add(Data.DATA8)
            .add(Data.DATA9)
            .add(Data.DATA10)
            .add(Data.DATA11)
            .add(Data.DATA12)
            .add(Data.DATA13)
            .add(Data.DATA14)
            .add(Data.DATA15)
            .add(Data.DATA_VERSION)
            .add(Data.IS_PRIMARY)
            .add(Data.IS_SUPER_PRIMARY)
            .add(Data.MIMETYPE)
//            .add(Data.RES_PACKAGE)
            .add(Data.SYNC1)
            .add(Data.SYNC2)
            .add(Data.SYNC3)
            .add(Data.SYNC4)
            .add(GroupMembership.GROUP_SOURCE_ID)
            .build();

    private static final ProjectionMap sDataUsageColumns = ProjectionMap.builder()
            .add(Data.TIMES_USED, Tables.DATA_USAGE_STAT + "." + Data.TIMES_USED)
            .add(Data.LAST_TIME_USED, Tables.DATA_USAGE_STAT + "." + Data.LAST_TIME_USED)
            .build();

    /** Contains just BaseColumns._COUNT */
    private static final ProjectionMap sCountProjectionMap = ProjectionMap.builder()
            .add(ScBaseColumns._COUNT, "COUNT(*)")
            .build();

    private static final ProjectionMap sRawContactColumns = ProjectionMap.builder()
            .add(RawContacts.DIRTY)
            .add(RawContacts.NAME_VERIFIED)
            .add(RawContacts.SOURCE_ID)
            .add(RawContacts.VERSION)
            .build();

    private static final ProjectionMap sRawContactSyncColumns = ProjectionMap.builder()
            .add(RawContacts.SYNC1)
            .add(RawContacts.SYNC2)
            .add(RawContacts.SYNC3)
            .add(RawContacts.SYNC4)
            .build();

    /** Contains just the raw contacts columns */
    private static final ProjectionMap sRawContactsProjectionMap = ProjectionMap.builder()
            .add(RawContacts._ID)
            .add(RawContacts.DELETED)
            .add(RawContacts.PHOTO_ID)
            .add(RawContacts.PHOTO_FILE_ID)
            .add(RawContacts.PHOTO_URI)
            .add(RawContacts.PHOTO_THUMBNAIL_URI)
            .add(RawContacts.DISPLAY_NAME_PRIMARY)
            .add(RawContacts.DISPLAY_NAME_ALTERNATIVE)
            .add(RawContacts.DISPLAY_NAME_SOURCE)
            .add(RawContacts.PHONETIC_NAME)
            .add(RawContacts.PHONETIC_NAME_STYLE)
            .add(RawContacts.SORT_KEY_PRIMARY)
            .add(RawContacts.SORT_KEY_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_LABEL_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE)
            .add(RawContacts.TIMES_CONTACTED)
            .add(RawContacts.LAST_TIME_CONTACTED)
            .add(RawContacts.CUSTOM_RINGTONE)
            .add(RawContacts.STARRED)
            .add(RawContacts.PINNED)
            .add(RawContacts.HAS_PHONE_NUMBER)
            .add(RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP)
            .add(RawContacts.CONTACT_TYPE)
            .addAll(sRawContactColumns)
            .addAll(sRawContactSyncColumns)
            .build();

    private static final ProjectionMap sRawContactsProjectionMapStrequent = ProjectionMap.builder()
            .add(RawContacts._ID)
            .add(RawContacts.PHOTO_THUMBNAIL_URI)
            .add(RawContacts.DISPLAY_NAME_PRIMARY)
            .add(RawContacts.DISPLAY_NAME_ALTERNATIVE)
            .add(RawContacts.DISPLAY_NAME_SOURCE)
            .add(RawContacts.PHONETIC_NAME)
            .add(RawContacts.PHONETIC_NAME_STYLE)
            .add(RawContacts.SORT_KEY_PRIMARY)
            .add(RawContacts.SORT_KEY_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_LABEL_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE)
            .add(RawContacts.LAST_TIME_CONTACTED)
            .add(RawContacts.TIMES_CONTACTED)
            .add(RawContacts.STARRED)
            .add(RawContacts.PINNED)
            .add(RawContacts.PHOTO_ID)
            .add(RawContacts.PHOTO_FILE_ID)
            .add(RawContacts.PHOTO_URI)
            .add(RawContacts.CUSTOM_RINGTONE)
            .add(RawContacts.HAS_PHONE_NUMBER)
            .build();

    /** Special Map that uses a qualified name to avoid ambiguity. Refer also to
     * {@link #setTablesAndProjectionMapForRawContactsStrequent}
     */
    private static final ProjectionMap sRawContactsProjectionMapFrequent = ProjectionMap.builder()
            .add(RawContacts._ID, "raw_join_view._id")
            .add(RawContacts.PHOTO_THUMBNAIL_URI)
            .add(RawContacts.DISPLAY_NAME_PRIMARY)
            .add(RawContacts.DISPLAY_NAME_ALTERNATIVE)
            .add(RawContacts.DISPLAY_NAME_SOURCE)
            .add(RawContacts.PHONETIC_NAME)
            .add(RawContacts.PHONETIC_NAME_STYLE)
            .add(RawContacts.SORT_KEY_PRIMARY)
            .add(RawContacts.SORT_KEY_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_LABEL_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE)
            .add(RawContacts.LAST_TIME_CONTACTED)
            .add(RawContacts.TIMES_CONTACTED)
            .add(RawContacts.STARRED)
            .add(RawContacts.PINNED)
            .add(RawContacts.PHOTO_ID)
            .add(RawContacts.PHOTO_FILE_ID)
            .add(RawContacts.PHOTO_URI)
            .add(RawContacts.CUSTOM_RINGTONE)
            .add(RawContacts.HAS_PHONE_NUMBER)
            .build();

    /** Contains the columns from the raw entity view*/
    private static final ProjectionMap sRawEntityProjectionMap = ProjectionMap.builder()
            .add(RawContacts._ID)
            .add(RawContacts.DISPLAY_NAME_SOURCE)
            .add(RawContacts.DISPLAY_NAME_PRIMARY)
            .add(RawContacts.DISPLAY_NAME_ALTERNATIVE)
            .add(RawContacts.PHONETIC_NAME)
            .add(RawContacts.PHOTO_ID)
            .add(RawContacts.PHOTO_URI)
            .add(RawContacts.Entity.DATA_ID)
            .add(RawContacts.DELETED)
            .add(RawContacts.CUSTOM_RINGTONE)
            .add(RawContacts.STARRED)
            .add(RawContacts.HAS_PHONE_NUMBER)
            .add(RawContacts.CONTACT_TYPE)
            .addAll(sRawContactColumns)
            .addAll(sRawContactSyncColumns)
            .addAll(sDataColumns)
            .addAll(sDataUsageColumns)
            .build();

    /** Contains columns in PhoneLookup which are not contained in the data view. */
    private static final ProjectionMap sSipLookupColumns = ProjectionMap.builder()
            .add(PhoneLookup.NUMBER, SipAddress.SIP_ADDRESS)
            .add(PhoneLookup.TYPE, "0")
            .add(PhoneLookup.LABEL, "NULL")
            .add(PhoneLookup.NORMALIZED_NUMBER, "NULL")
            .build();

    /** Contains columns from the data view */
    private static final ProjectionMap sDataProjectionMap = ProjectionMap.builder()
            .add(Data._ID)
            .add(Data.RAW_CONTACT_ID)
            .addAll(sDataColumns)
            .addAll(sRawContactColumns)
            .addAll(sRawContactsColumnsAdd)
            .addAll(sDataUsageColumns)
            .build();

    /** Contains columns from the data view used for SIP address lookup. */
    private static final ProjectionMap sDataSipLookupProjectionMap = ProjectionMap.builder()
            .addAll(sDataProjectionMap)
            .addAll(sSipLookupColumns)
            .build();

    /** Contains columns from the data view */
    private static final ProjectionMap sDistinctDataProjectionMap = ProjectionMap.builder()
            .add(Data._ID, "MIN(" + Data._ID + ")")
            .add(Data.RAW_CONTACT_ID)
            .addAll(sDataColumns)
            .addAll(sRawContactsColumnsAdd)
            .addAll(sDataUsageColumns)
            .build();

    /** Contains columns from the data view used for SIP address lookup. */
    private static final ProjectionMap sDistinctDataSipLookupProjectionMap = ProjectionMap.builder()
            .addAll(sDistinctDataProjectionMap)
            .addAll(sSipLookupColumns)
            .build();

    /** Contains the data and contacts columns, for joined tables */
    private static final ProjectionMap sPhoneLookupProjectionMap = ProjectionMap.builder()
            .add(PhoneLookup._ID, Views.RAW_CONTACTS + "." + RawContacts._ID)
            .add(PhoneLookup.DISPLAY_NAME, Views.RAW_CONTACTS + "." + RawContacts.DISPLAY_NAME_PRIMARY)
            .add(PhoneLookup.LAST_TIME_CONTACTED, Views.RAW_CONTACTS + "." + RawContacts.LAST_TIME_CONTACTED)
            .add(PhoneLookup.TIMES_CONTACTED, Views.RAW_CONTACTS + "." + RawContacts.TIMES_CONTACTED)
            .add(PhoneLookup.STARRED, Views.RAW_CONTACTS + "." + RawContacts.STARRED)
//            .add(PhoneLookup.IN_VISIBLE_GROUP, "contacts_view." + Contacts.IN_VISIBLE_GROUP)
            .add(PhoneLookup.PHOTO_ID, Views.RAW_CONTACTS + "." + RawContacts.PHOTO_ID)
            .add(PhoneLookup.PHOTO_FILE_ID, Views.RAW_CONTACTS + "." + RawContacts.PHOTO_FILE_ID)
            .add(PhoneLookup.PHOTO_URI,  Views.RAW_CONTACTS + "." + RawContacts.PHOTO_URI)
            .add(PhoneLookup.CUSTOM_RINGTONE, Views.RAW_CONTACTS + "." + RawContacts.CUSTOM_RINGTONE)
            .add(PhoneLookup.HAS_PHONE_NUMBER, Views.RAW_CONTACTS + "." + RawContacts.HAS_PHONE_NUMBER)
            .add(PhoneLookup.NUMBER, Phone.NUMBER)
            .add(PhoneLookup.TYPE, Phone.TYPE)
            .add(PhoneLookup.LABEL, Phone.LABEL)
            .add(PhoneLookup.NORMALIZED_NUMBER, Phone.NORMALIZED_NUMBER)
            .build();

    /** Contains StreamItems columns */
    private static final ProjectionMap sStreamItemsProjectionMap = ProjectionMap.builder()
            .add(StreamItems._ID)
            .add(StreamItems.RAW_CONTACT_ID)
            .add(StreamItems.RAW_CONTACT_SOURCE_ID)
//            .add(StreamItems.RES_PACKAGE)
            .add(StreamItems.RES_ICON)
            .add(StreamItems.RES_LABEL)
            .add(StreamItems.TEXT)
            .add(StreamItems.TIMESTAMP)
            .add(StreamItems.COMMENTS)
            .add(StreamItems.SYNC1)
            .add(StreamItems.SYNC2)
            .add(StreamItems.SYNC3)
            .add(StreamItems.SYNC4)
            .build();

    private static final ProjectionMap sStreamItemPhotosProjectionMap = ProjectionMap.builder()
            .add(StreamItemPhotos._ID, StreamItemPhotosColumns.CONCRETE_ID)
            .add(StreamItems.RAW_CONTACT_ID)
            .add(StreamItems.RAW_CONTACT_SOURCE_ID, RawContactsColumns.CONCRETE_SOURCE_ID)
            .add(StreamItemPhotos.STREAM_ITEM_ID)
            .add(StreamItemPhotos.SORT_INDEX)
            .add(StreamItemPhotosColumns.CONCRETE_PHOTO_FILE_ID)
            .add(StreamItemPhotos.PHOTO_URI,
                    "'" + DisplayPhoto.CONTENT_URI + "'||'/'||" + StreamItemPhotosColumns.CONCRETE_PHOTO_FILE_ID)
            .add(PhotoFiles.HEIGHT)
            .add(PhotoFiles.WIDTH)
            .add(PhotoFiles.FILESIZE)
            .add(StreamItemPhotos.SYNC1)
            .add(StreamItemPhotos.SYNC2)
            .add(StreamItemPhotos.SYNC3)
            .add(StreamItemPhotos.SYNC4)
            .build();

    /** Contains the just the {@link com.silentcircle.silentcontacts.ScContactsContract.Groups} columns */
    private static final ProjectionMap sGroupsProjectionMap = ProjectionMap.builder()
            .add(Groups._ID)
            .add(Groups.SOURCE_ID)
            .add(Groups.DIRTY)
            .add(Groups.VERSION)
//            .add(Groups.RES_PACKAGE)
            .add(Groups.TITLE)
            .add(Groups.TITLE_RES)
            .add(Groups.GROUP_VISIBLE)
            .add(Groups.SYSTEM_ID)
            .add(Groups.DELETED)
            .add(Groups.NOTES)
            .add(Groups.SHOULD_SYNC)
            .add(Groups.FAVORITES)
            .add(Groups.AUTO_ADD)
            .add(Groups.GROUP_IS_READ_ONLY)
            .add(Groups.SYNC1)
            .add(Groups.SYNC2)
            .add(Groups.SYNC3)
            .add(Groups.SYNC4)
            .build();

    private static final ProjectionMap sDeletedContactsProjectionMap = ProjectionMap.builder()
            .add(DeletedContacts.CONTACT_ID)
            .add(DeletedContacts.CONTACT_DELETED_TIMESTAMP)
            .build();

    /**
     * Contains {@link com.silentcircle.silentcontacts.ScContactsContract.Groups} columns along with summary details.
     *
     * Note {@link com.silentcircle.silentcontacts.ScContactsContract.Groups#SUMMARY_COUNT} doesn't exist in groups/view_groups.
     * When we detect this column being requested, we join {@link Joins#GROUP_MEMBER_COUNT} to
     * generate it.
     *
     */
    private static final ProjectionMap sGroupsSummaryProjectionMap = ProjectionMap.builder()
            .addAll(sGroupsProjectionMap)
            .add(Groups.SUMMARY_COUNT, "ifnull(group_member_count, 0)")
            .add(Groups.SUMMARY_WITH_PHONES,
                    "(SELECT COUNT(" + RawContactsColumns.CONCRETE_ID + ") FROM "
                        + Tables.CONTACTS_JOIN_RAW_CONTACTS_DATA_FILTERED_BY_GROUPMEMBERSHIP
                        + " WHERE " + RawContacts.HAS_PHONE_NUMBER + ")")
            .add(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, "0") // Always returns 0 for now.
            .build();

    /** Contains just the contacts columns */
    private static final ProjectionMap sSnippetsContactsProjectionMap = ProjectionMap.builder()
            .add(RawContacts._ID)
            .add(RawContacts.CONTACT_TYPE)
            .add(RawContacts.CUSTOM_RINGTONE)
            .add(RawContacts.DISPLAY_NAME)
            .add(RawContacts.DISPLAY_NAME_ALTERNATIVE)
            .add(RawContacts.DISPLAY_NAME_SOURCE)
//            .add(Contacts.IN_VISIBLE_GROUP)
            .add(RawContacts.LAST_TIME_CONTACTED)
            .add(RawContacts.PHONETIC_NAME)
            .add(RawContacts.PHONETIC_NAME_STYLE)
            .add(RawContacts.PHOTO_ID)
            .add(RawContacts.PHOTO_FILE_ID)
            .add(RawContacts.PHOTO_URI)
            .add(RawContacts.PHOTO_THUMBNAIL_URI)
            .add(RawContacts.STARRED)
            .add(RawContacts.TIMES_CONTACTED)
            .add(RawContacts.HAS_PHONE_NUMBER)
            .build();

    private static final ProjectionMap sSnippetColumns = ProjectionMap.builder()
            .add(SearchSnippetColumns.SNIPPET)
            .build();

    /** Contains just the contacts columns */
    private static final ProjectionMap sContactsProjectionWithSnippetMap = ProjectionMap.builder()
            .addAll(sSnippetsContactsProjectionMap)
            .addAll(sSnippetColumns)
            .build();

    /** Contains {@link com.silentcircle.silentcontacts.ScContactsContract.Directory} columns */
    private static final ProjectionMap sDirectoryProjectionMap = ProjectionMap.builder()
            .add(Directory._ID)
            .add(Directory.PACKAGE_NAME)
            .add(Directory.TYPE_RESOURCE_ID)
            .add(Directory.DISPLAY_NAME)
            .add(Directory.DIRECTORY_AUTHORITY)
            .add(Directory.EXPORT_SUPPORT)
            .add(Directory.SHORTCUT_SUPPORT)
            .add(Directory.PHOTO_SUPPORT)
            .build();

    /** Contains just the contacts vCard columns */
    private static final ProjectionMap sContactsVCardProjectionMap = ProjectionMap.builder()
            .add(RawContacts._ID)
            .add(OpenableColumns.DISPLAY_NAME, RawContacts.DISPLAY_NAME + " || '.vcf'")
            .add(OpenableColumns.SIZE, "NULL")
            .build();

    /** Used for pushing starred contacts to the top of a times contacted list **/
    private static final ProjectionMap sStrequentStarredProjectionMap = ProjectionMap.builder()
            .addAll(sRawContactsProjectionMapStrequent)
            .add(DataUsageStatColumns.TIMES_USED, String.valueOf(Long.MAX_VALUE))
            .add(DataUsageStatColumns.LAST_TIME_USED, String.valueOf(Long.MAX_VALUE))
            .build();

    private static final ProjectionMap sStrequentFrequentProjectionMap = ProjectionMap.builder()
            .addAll(sRawContactsProjectionMapFrequent)
            .add(DataUsageStatColumns.TIMES_USED, "SUM(" + DataUsageStatColumns.CONCRETE_TIMES_USED + ")")
            .add(DataUsageStatColumns.LAST_TIME_USED, "MAX(" + DataUsageStatColumns.CONCRETE_LAST_TIME_USED + ")")
            .build();

    /**
     * Used for Strequent Uri with {@link ContactsContract#STREQUENT_PHONE_ONLY}, which allows
     * users to obtain part of Data columns. Right now Starred part just returns NULL for
     * those data columns (frequent part should return real ones in data table).
     **/
//    private static final ProjectionMap sStrequentPhoneOnlyStarredProjectionMap = ProjectionMap.builder()
//            .addAll(sRawContactsProjectionMapStrequent)
//            .add(DataUsageStatColumns.TIMES_USED, String.valueOf(Long.MAX_VALUE))
//            .add(DataUsageStatColumns.LAST_TIME_USED, String.valueOf(Long.MAX_VALUE))
//            .add(Phone.NUMBER, "NULL")
//            .add(Phone.TYPE, "NULL")
//            .add(Phone.LABEL, "NULL")
//            .add(Phone.RAW_CONTACT_ID, "NULL")
//            .build();

    /**
     * Used for Strequent Uri with {@link ContactsContract#STREQUENT_PHONE_ONLY}, which allows
     * users to obtain part of Data columns. We hard-code {@link Contacts#IS_USER_PROFILE} to NULL,
     * because sContactsProjectionMap specifies a field that doesn't exist in the view behind the
     * query that uses this projection map.
     **/
    private static final ProjectionMap sStrequentPhoneOnlyProjectionMap  = ProjectionMap.builder()
            .addAll(sRawContactsProjectionMapStrequent)
            .add(DataUsageStatColumns.TIMES_USED, DataUsageStatColumns.CONCRETE_TIMES_USED)
            .add(DataUsageStatColumns.LAST_TIME_USED, DataUsageStatColumns.CONCRETE_LAST_TIME_USED)
            .add(Phone.NUMBER)
            .add(Phone.TYPE)
            .add(Phone.LABEL)
            .add(Phone.IS_SUPER_PRIMARY)
            .add(Phone.RAW_CONTACT_ID)
            .build();

    /**
     * If any of these columns are used in a Data projection, there is no point in
     * using the DISTINCT keyword, which can negatively affect performance.
     */
    private static final String[] DISTINCT_DATA_PROHIBITING_COLUMNS = {
            Data._ID,
            Data.RAW_CONTACT_ID,
            RawContacts.DIRTY,
            RawContacts.NAME_VERIFIED,
            RawContacts.SOURCE_ID,
            RawContacts.VERSION,
    };

    private static final String SELECTION_GROUPMEMBERSHIP_DATA = DataColumns.MIMETYPE_ID + "=? "
            + "AND " + GroupMembership.GROUP_ROW_ID + "=? "
            + "AND " + GroupMembership.RAW_CONTACT_ID + "=?";

    /*
     * Sorting order for email address suggestions: first starred, then the rest.
     * Within the two groups:
     * - three buckets: very recently contacted, then fairly recently contacted, then the rest.
     * Within each of the bucket - descending count of times contacted (both for data row and for
     * contact row).
     * If all else fails, in_visible_group, alphabetical.
     * (Super)primary email address is returned before other addresses for the same contact.
     */
    private static final String EMAIL_FILTER_SORT_ORDER =
        RawContacts.STARRED + " DESC, "
        + Data.IS_SUPER_PRIMARY + " DESC, "
        + Data.IS_PRIMARY + " DESC, "
        + RawContacts.DISPLAY_NAME_PRIMARY;

    /** Name lookup types used for contact filtering */
    private static final String CONTACT_LOOKUP_NAME_TYPES =
            NameLookupType.NAME_COLLATION_KEY + "," 
                    + NameLookupType.EMAIL_BASED_NICKNAME + "," 
                    + NameLookupType.NICKNAME;

    // Contacts contacted within the last 3 days (in seconds)
    private static final long LAST_TIME_USED_3_DAYS_SEC = 3L * 24 * 60 * 60;

    // Contacts contacted within the last 7 days (in seconds)
    private static final long LAST_TIME_USED_7_DAYS_SEC = 7L * 24 * 60 * 60;

    // Contacts contacted within the last 14 days (in seconds)
    private static final long LAST_TIME_USED_14_DAYS_SEC = 14L * 24 * 60 * 60;

    // Contacts contacted within the last 30 days (in seconds)
    private static final long LAST_TIME_USED_30_DAYS_SEC = 30L * 24 * 60 * 60;

    private static final String TIME_SINCE_LAST_USED_SEC =
            "(strftime('%s', 'now') - " + DataUsageStatColumns.LAST_TIME_USED + "/1000)";

    private static final String SORT_BY_DATA_USAGE =
            "(CASE WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_3_DAYS_SEC +
                    " THEN 0 " +
                    " WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_7_DAYS_SEC +
                    " THEN 1 " +
                    " WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_14_DAYS_SEC +
                    " THEN 2 " +
                    " WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_30_DAYS_SEC +
                    " THEN 3 " +
                    " ELSE 4 END), " +
                    DataUsageStatColumns.TIMES_USED + " DESC";

    private static final String FREQUENT_ORDER_BY = DataUsageStatColumns.TIMES_USED + " DESC,"
            + RawContacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

    /** Sql for un-demoting a demoted raw contact **/
    private static final String UN_DEMOTE_RAW_CONTACT =
            "UPDATE " + Tables.RAW_CONTACTS +
                    " SET " + RawContacts.PINNED + " = " + PinnedPositions.UNPINNED +
                    " WHERE " + RawContacts._ID + " = ?1 AND " + RawContacts.PINNED + " <= " + PinnedPositions.DEMOTED;


    /** Currently same as {@link #EMAIL_FILTER_SORT_ORDER} */
    private static final String PHONE_FILTER_SORT_ORDER = EMAIL_FILTER_SORT_ORDER;

    private volatile CountDownLatch mReadAccessLatch;
    private volatile CountDownLatch mWriteAccessLatch;

    // Depending on whether the action being performed is for the profile, we will use one of two
    // database helper instances.
    private final ThreadLocal<ScContactsDatabaseHelper> mDbHelper =  new ThreadLocal<>();
    private final ThreadLocal<TransactionContext> mTransactionContext = new ThreadLocal<>();
    private ScContactsDatabaseHelper mContactsHelper;
    private final TransactionContext mContactTransactionContext = new TransactionContext(false);

    // Depending on whether the action being performed is for the profile or not, we will use one of
    // two photo store instances (with their files stored in separate subdirectories).
    private final ThreadLocal<PhotoStore> mPhotoStore = new ThreadLocal<>();
    private PhotoStore mContactsPhotoStore;

    private int mProviderStatus = ProviderStatus.STATUS_NORMAL;
    private boolean mProviderStatusUpdateNeeded;

    // Depending on whether the action being performed is for the profile or not, we will use one of
    // two aggregator instances.
    private final ThreadLocal<SimpleRawContactAggregator> mAggregator = new ThreadLocal<>();
    private SimpleRawContactAggregator mSimpleAggregator;

    private Handler mBackgroundHandler;

    private Locale mCurrentLocale;
    private NameSplitter mNameSplitter;
    private SearchIndexManager mSearchIndexManager;

    private StructuredNameLookupBuilder mNameLookupBuilder;
    private PostalSplitter mPostalSplitter;

    private ContactDirectoryManager mContactDirectoryManager;

    HashMap<String, DataRowHandler> mDataRowHandlers;

    private final ContentValues mValues = new ContentValues();

    private long mLastPhotoCleanup = 0;

    private FastScrollingIndexCache mFastScrollingIndexCache;

    private boolean doNotify = true;        // applyBatch toggles this to avoid to much UI activity

    private final String[] mSelectionArgs1 = new String[1];
    private final String[] mSelectionArgs2 = new String[2];
    private final String[] mSelectionArgs4 = new String[4];

    private static boolean mWriteOpen;
    
    private boolean mIsBatch;

    public ScContactsProvider() {
    }

    @Override
    public boolean onCreate() {
        if (DEBUG) Log.d(TAG, "ScContactsProvider.onCreate start");
        LocaleChangeReceiver.setProvider(this);

        try {
            return initialize();
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot start provider", e);
            // In production code we don't want to throw here, so that phone will still work
            // in low storage situations.
            // See I5c88a3024ff1c5a06b5756b29a2d903f8f6a2531
            if (shouldThrowExceptionForInitializationError()) {
                throw e;
            }
            return false;
        } finally {
            if (DEBUG) Log.d(TAG, "ScContactsProvider.onCreate finish");
        }
    }

    @Override
    public void shutdown() {
        LocaleChangeReceiver.setProvider(null);
        mDbHelper.get().close();
    }

    protected boolean shouldThrowExceptionForInitializationError() {
        return true;
    }

    private boolean initialize() {

        mFastScrollingIndexCache = new FastScrollingIndexCache(getContext());

        mContactsHelper = getDatabaseHelper(getContext());
        mDbHelper.set(mContactsHelper);


        mContactDirectoryManager = new ContactDirectoryManager(this);
//        mGlobalSearchSupport = new GlobalSearchSupport(this);

        // The provider is closed for business until fully initialized
        mReadAccessLatch = new CountDownLatch(1);
        mWriteAccessLatch = new CountDownLatch(1);

        mContactsHelper.checkRegisterKeyManager(true);

        HandlerThread mBackgroundThread = new HandlerThread("ScContactsProviderWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                performBackgroundTask(msg.what);
            }
        };
        // Perform update provider status before initialize. Initialize open the read gate and an app
        // may get wrong status info
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_PROVIDER_STATUS);
        scheduleBackgroundTask(BACKGROUND_TASK_INITIALIZE);
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_ACCOUNTS);
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_LOCALE);
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_SEARCH_INDEX);
        scheduleBackgroundTask(BACKGROUND_TASK_OPEN_WRITE_ACCESS);
        scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);

        return true;
    }

    /**
     * (Re)allocates all locale-sensitive structures.
     */
    private void initForDefaultLocale() {
        mCurrentLocale = getLocale();
        mNameSplitter = mContactsHelper.createNameSplitter();
        mNameLookupBuilder = new StructuredNameLookupBuilder(mNameSplitter);
        mPostalSplitter = new PostalSplitter(mCurrentLocale);
//        mCommonNicknameCache = new CommonNicknameCache(mContactsHelper.getReadableDatabase());
        ContactLocaleUtils43.setLocale(mCurrentLocale);
        mSimpleAggregator = new SimpleRawContactAggregator(this, mContactsHelper, mNameSplitter);
        mSearchIndexManager = new SearchIndexManager(this);
        mContactsPhotoStore = new PhotoStore(getContext().getFilesDir(), mContactsHelper, this);

        mDataRowHandlers = new HashMap<>();
        initDataRowHandlers(mDataRowHandlers, mContactsHelper, mSimpleAggregator,  mContactsPhotoStore);

        // Set initial thread-local state variables for the Contacts DB.
        switchToContactMode();
    }

    private void initDataRowHandlers(Map<String, DataRowHandler> handlerMap, ScContactsDatabaseHelper dbHelper,
            SimpleRawContactAggregator contactAggregator, PhotoStore photoStore) {

        Context context = getContext();

        handlerMap.put(Email.CONTENT_ITEM_TYPE,  new DataRowHandlerForEmail(context, dbHelper, contactAggregator));
        handlerMap.put(Im.CONTENT_ITEM_TYPE,  new DataRowHandlerForIm(context, dbHelper, contactAggregator));
        handlerMap.put(Organization.CONTENT_ITEM_TYPE, new DataRowHandlerForOrganization(context, dbHelper, contactAggregator));
        handlerMap.put(Phone.CONTENT_ITEM_TYPE, new DataRowHandlerForPhoneNumber(context, dbHelper, contactAggregator));

        //        handlerMap.put(Nickname.CONTENT_ITEM_TYPE, new DataRowHandlerForNickname(context, dbHelper, contactAggregator));
        handlerMap.put(StructuredName.CONTENT_ITEM_TYPE, new DataRowHandlerForStructuredName(context, dbHelper,
                contactAggregator, mNameSplitter, mNameLookupBuilder));

        handlerMap.put(StructuredPostal.CONTENT_ITEM_TYPE, new DataRowHandlerForStructuredPostal(context, dbHelper,
                contactAggregator, mPostalSplitter));
        handlerMap.put(GroupMembership.CONTENT_ITEM_TYPE, new DataRowHandlerForGroupMembership(context, dbHelper,
                contactAggregator));

        handlerMap.put(Photo.CONTENT_ITEM_TYPE, new DataRowHandlerForPhoto(context, dbHelper, contactAggregator, photoStore,
                getMaxDisplayPhotoDim(), getMaxThumbnailDim()));
        handlerMap.put(Note.CONTENT_ITEM_TYPE, new DataRowHandlerForNote(context, dbHelper, contactAggregator));
//        handlerMap.put(Identity.CONTENT_ITEM_TYPE, new DataRowHandlerForIdentity(context, dbHelper, contactAggregator));
    }

    public DataRowHandler getDataRowHandler(final String mimeType) {
        DataRowHandler handler = mDataRowHandlers.get(mimeType);
        if (handler == null) {
            handler = new DataRowHandlerForCustomMimetype(getContext(), mContactsHelper, null, mimeType);
            mDataRowHandlers.put(mimeType, handler);
        }
        return handler;
    }

    protected void setProviderStatus(int status) {
        if (mProviderStatus != status) {
            mProviderStatus = status;
            getContext().getContentResolver().notifyChange(ProviderStatus.CONTENT_URI, null, false);
        }
    }

    /**
     * Switches the provider's thread-local context variables to prepare for performing
     * a contacts operation.
     */
    private void switchToContactMode() {
        if (VERBOSE_DEBUG) {
            Log.i(TAG, "switchToContactMode", new RuntimeException("switchToContactMode"));
        }
        mDbHelper.set(mContactsHelper);
        mTransactionContext.set(mContactTransactionContext);
        mAggregator.set(mSimpleAggregator);
        mPhotoStore.set(mContactsPhotoStore);
    }

    private void updateProviderStatus() {
        if (mProviderStatus != ProviderStatus.STATUS_NORMAL && mProviderStatus != ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS) {
            return;
        }

        // If no contacts status is true if there are no contacts
        long contactsNum = DbQueryUtils.queryNumEntries(mContactsHelper.getDatabase(false), Tables.RAW_CONTACTS);
        if (DEBUG) Log.i(TAG, "Available contacts: " + contactsNum);
        if (contactsNum == 0) {
            setProviderStatus(ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS);
        }
        else {
            setProviderStatus(ProviderStatus.STATUS_NORMAL);
        }
    }

    protected Locale getLocale() {
        return Locale.getDefault();
    }

    public void onLocaleChanged() {
        if (DEBUG) Log.d(TAG, "Locale changed");
        if (mProviderStatus != ProviderStatus.STATUS_NORMAL && mProviderStatus != ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS) {
            return;
        }
        scheduleBackgroundTask(BACKGROUND_TASK_CHANGE_LOCALE);
    }

    /**
     * Verifies that the contacts database is properly configured for the current locale.
     * If not, changes the database locale to the current locale using an asynchronous task.
     * This needs to be done asynchronously because the process involves rebuilding
     * large data structures (name lookup, sort keys), which can take minutes on
     * a large set of contacts.
     */
    protected void updateLocaleInBackground() {

        // The process is already running - postpone the change
        if (mProviderStatus == ProviderStatus.STATUS_CHANGING_LOCALE) {
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        final String providerLocale = prefs.getString(PREF_LOCALE, null);
        final Locale currentLocale = mCurrentLocale;

        if (DEBUG) Log.i(TAG, "updatelocale: providerLocale: " + providerLocale + ", current: " + currentLocale.toString());
        if (currentLocale.toString().equals(providerLocale)) {
            return;
        }

        int providerStatus = mProviderStatus;
        setProviderStatus(ProviderStatus.STATUS_CHANGING_LOCALE);

        mContactsHelper.setLocale(this, currentLocale);
        mSearchIndexManager.updateIndex(true);
        prefs.edit().putString(PREF_LOCALE, currentLocale.toString()).apply();
        invalidateFastScrollingIndexCache();

        setProviderStatus(providerStatus);
    }

    /**
     * Reinitializes the provider for a new locale.
     */
    private void changeLocaleInBackground() {
        // Re-initializing the provider without stopping it.
        // Locking the database will prevent inserts/updates/deletes from
        // running at the same time, but queries may still be running
        // on other threads. Those queries may return inconsistent results.
        SQLiteDatabase db = mContactsHelper.getDatabase(true);

        db.beginTransaction();
        try {
            initForDefaultLocale();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        updateLocaleInBackground();
    }

    protected void updateSearchIndexInBackground() {
        mSearchIndexManager.updateIndex(false);
    }

    protected ScContactsDatabaseHelper getDatabaseHelper(final Context context) {
        return ScContactsDatabaseHelper.getInstance(context);
    }

    protected ScContactsDatabaseHelper getDatabaseHelper() {
        return mContactsHelper;
    }

    public PhotoStore getPhotoStore() {
        return mContactsPhotoStore;
    }

    /**
     * Maximum dimension (height or width) of photo thumbnails.
     */
    public int getMaxThumbnailDim() {
        return PhotoProcessor.getMaxThumbnailSize();
    }

    /**
     * Maximum dimension (height or width) of display photos.  Larger images will be scaled
     * to fit.
     */
    public int getMaxDisplayPhotoDim() {
        return PhotoProcessor.getMaxDisplayPhotoSize();
    }


    /**
     * Wipes all data from the contacts database.
     */
    // @NeededForTesting
    public void wipeData() {
        invalidateFastScrollingIndexCache();
        mContactsHelper.wipeData();
        mContactsPhotoStore.clear();
        mProviderStatus = ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS;
    }

    protected void scheduleBackgroundTask(int task) {
        mBackgroundHandler.sendEmptyMessage(task);
    }

    protected void performBackgroundTask(int task) {
        // Make sure we operate on the contacts db by default.
        switchToContactMode();
        switch (task) {
        case BACKGROUND_TASK_INITIALIZE: {
            initForDefaultLocale();
            mReadAccessLatch.countDown();
            mReadAccessLatch = null;
            break;
        }

        case BACKGROUND_TASK_OPEN_WRITE_ACCESS: {
            mWriteAccessLatch.countDown();
            mWriteAccessLatch = null;
            mWriteOpen = true;
            break;
        }

        case BACKGROUND_TASK_UPDATE_LOCALE: {
            updateLocaleInBackground();
            break;
        }

        case BACKGROUND_TASK_CHANGE_LOCALE: {
            changeLocaleInBackground();
            break;
        }

        case BACKGROUND_TASK_UPDATE_ACCOUNTS: {
            updateDirectoriesInBackground(true);
            break;
        }

        case BACKGROUND_TASK_UPDATE_SEARCH_INDEX: {
            updateSearchIndexInBackground();
            break;
        }

        case BACKGROUND_TASK_UPDATE_PROVIDER_STATUS: {
            updateProviderStatus();
            break;
        }

        case BACKGROUND_TASK_CLEANUP_PHOTOS: {
            // Check rate limit.
            long now = System.currentTimeMillis();
            if (now - mLastPhotoCleanup > PHOTO_CLEANUP_RATE_LIMIT) {
                mLastPhotoCleanup = now;
                // Clean up photo stores for both contacts and profiles.
                switchToContactMode();
                cleanupPhotoStore();
                break;
            }
        }
        }

    }

    public static boolean isWriteOpen() {
        return mWriteOpen;
    }

    /**
     * During initialization, this content provider will
     * block all attempts to change contacts data. In particular, it will hold
     * up all contact syncs. As soon as the import process is complete, all
     * processes waiting to write to the provider are unblocked and can proceed
     * to compete for the database transaction monitor.
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

    public void cleanupPhotoStore() {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        // Assemble the set of photo store file IDs that are in use, and send those to the photo
        // store.  Any photos that aren't in that set will be deleted, and any photos that no
        // longer exist in the photo store will be returned for us to clear out in the DB.
        long photoMimeTypeId = mDbHelper.get().getMimeTypeId(Photo.CONTENT_ITEM_TYPE);
        Cursor c = db.query(Views.DATA, new String[]{Data._ID, Photo.PHOTO_FILE_ID},
                DataColumns.MIMETYPE_ID + "=" + photoMimeTypeId + " AND "
                        + Photo.PHOTO_FILE_ID + " IS NOT NULL", null, null, null, null);

        Set<Long> usedPhotoFileIds = new HashSet<>();
        Map<Long, Long> photoFileIdToDataId = new HashMap<>();
        try {
            while (c.moveToNext()) {
                long dataId = c.getLong(0);
                long photoFileId = c.getLong(1);
                usedPhotoFileIds.add(photoFileId);
                photoFileIdToDataId.put(photoFileId, dataId);
            }
        } finally {
            c.close();
        }

        // Also query for all social stream item photos.
        c = db.query(Tables.STREAM_ITEM_PHOTOS + " JOIN " + Tables.STREAM_ITEMS
                + " ON " + StreamItemPhotos.STREAM_ITEM_ID + "=" + StreamItemsColumns.CONCRETE_ID,
                new String[] {
                    StreamItemPhotosColumns.CONCRETE_ID,
                    StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID,
                    StreamItemPhotos.PHOTO_FILE_ID
                },
                null, null, null, null, null);
        Map<Long, Long> photoFileIdToStreamItemPhotoId = new HashMap<>();
        try {
            while (c.moveToNext()) {
                long streamItemPhotoId = c.getLong(0);
                long streamItemId = c.getLong(1);
                long photoFileId = c.getLong(2);
                usedPhotoFileIds.add(photoFileId);
                photoFileIdToStreamItemPhotoId.put(photoFileId, streamItemPhotoId);
            }
        } finally {
            c.close();
        }

        // Run the photo store cleanup.
        Set<Long> missingPhotoIds = mPhotoStore.get().cleanup(usedPhotoFileIds);

        // If any of the keys we're using no longer exist, clean them up.  We need to do these
        // using internal APIs or direct DB access to avoid permission errors.
        if (!missingPhotoIds.isEmpty()) {
            try {
                db.beginTransaction();
                for (long missingPhotoId : missingPhotoIds) {
                    if (photoFileIdToDataId.containsKey(missingPhotoId)) {
                        long dataId = photoFileIdToDataId.get(missingPhotoId);
                        ContentValues updateValues = new ContentValues();
                        updateValues.putNull(Photo.PHOTO_FILE_ID);
                        updateData(ContentUris.withAppendedId(Data.CONTENT_URI, dataId), updateValues, null, null, false);
                    }
                    if (photoFileIdToStreamItemPhotoId.containsKey(missingPhotoId)) {
                        // For missing photos that were in stream item photos, just delete the
                        // stream item photo.
                        long streamItemPhotoId = photoFileIdToStreamItemPhotoId.get(missingPhotoId);
                        db.delete(Tables.STREAM_ITEM_PHOTOS, StreamItemPhotos._ID + "=?",
                                new String[]{String.valueOf(streamItemPhotoId)});
                    }
                }
                db.setTransactionSuccessful();
            } catch (Exception e) {
                // Cleanup failure is not a fatal problem.  We'll try again later.
                Log.e(TAG, "Failed to clean up outdated photo references", e);
            } finally {
                db.endTransaction();
            }
        }
    }
    /**
     * If the given URI is reading stream items or stream photos, this will run a permission check
     * for the android.permission.READ_SOCIAL_STREAM permission - otherwise it will do nothing.
     * @param uri The URI to check.
     */
    private void enforceSocialStreamReadPermission(Uri uri) {
//        if (SOCIAL_STREAM_URIS.contains(sUriMatcher.match(uri))
//                && !isValidPreAuthorizedUri(uri)) {
//            getContext().enforceCallingOrSelfPermission(
//                    "android.permission.READ_SOCIAL_STREAM", null);
//        }
    }

    /**
     * If the given URI is modifying stream items or stream photos, this will run a permission check
     * for the android.permission.WRITE_SOCIAL_STREAM permission - otherwise it will do nothing.
     * @param uri The URI to check.
     */
    private void enforceSocialStreamWritePermission(Uri uri) {
//        if (SOCIAL_STREAM_URIS.contains(sUriMatcher.match(uri))) {
//            getContext().enforceCallingOrSelfPermission(
//                    "android.permission.WRITE_SOCIAL_STREAM", null);
//        }
    }

    private void updateSearchIndexInTransaction() {
        Set<Long> staleRawContacts = mTransactionContext.get().getStaleSearchIndexRawContactIds();
        if (!staleRawContacts.isEmpty()) {
            mSearchIndexManager.updateIndexForRawContacts(staleRawContacts);
            mTransactionContext.get().clearSearchIndexUpdates();
        }
    }

    protected void updateDirectoriesInBackground(boolean rescan) {
        mContactDirectoryManager.scanAllPackages(rescan);
    }

    protected void notifyChange(boolean syncToNetwork) {
        if (doNotify)
            getContext().getContentResolver().notifyChange(ScContactsContract.AUTHORITY_URI, null, syncToNetwork);
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
            if (VERBOSE_DEBUG) Log.v(TAG, "applyBatch: " + operations.size() + " ops");

        switchToContactMode();
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        mIsBatch = true;
        db.beginTransactionWithListener(this);
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            doNotify = false;

            for (int i = 0; i < numOperations; i++) {
                final ContentProviderOperation operation = operations.get(i);
                results[i] = operation.apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
            mIsBatch = false;
            doNotify = true;
            notifyChange(false);
        }
    }

    @Override
    public void onBegin() {
//        onBeginTransactionInternal(false);
    }

    @Override
    public void onCommit() {
        onCommitInternal(false);
    }

    @Override
    public void onRollback() {
//        onRollbackTransactionInternal(false);
    }

    protected void onCommitInternal(boolean forProfile) {

        flushTransactionalChanges();

        if (mProviderStatusUpdateNeeded) {
            updateProviderStatus();
            mProviderStatusUpdateNeeded = false;
        }
        updateSearchIndexInTransaction();
        notifyChange(false);
    }

    private void flushTransactionalChanges() {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        final Set<Long> changedRawContacts = mTransactionContext.get().getChangedRawContactIds();
        for (long id : changedRawContacts)
            ContactsTableUtil.updateContactLastUpdateByContactId(db, id);
        mTransactionContext.get().clearExceptSearchIndexUpdates();
    }


    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (DEBUG) {
            Log.v(TAG, "insert: uri=" + uri);
        }
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return null;

        waitForAccess(mWriteAccessLatch);
        switchToContactMode();

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        if (!mIsBatch)
            db.beginTransactionWithListener(this);
        try {
            Uri ret = insertLocal(uri, values);
            if (!mIsBatch)
                db.setTransactionSuccessful();
            return ret;
        }
        finally {
            if (!mIsBatch)
                db.endTransaction();
        }
    }

    public Uri insertLocal(Uri uri, ContentValues values) {
        if (DEBUG) {
            Log.v(TAG, "insertLocal: uri=" + uri + "  values=[" + values + "]");
        }
        final boolean callerIsSyncAdapter = readBooleanQueryParameter(uri, ScContactsContract.CALLER_IS_SYNCADAPTER, false);

        final int match = sUriMatcher.match(uri);
        long id;

        switch (match) {
//            case SYNCSTATE:
//                id = mDbHelper.get().getSyncState().insert(db, values);
//                break;
//
            case RAW_CONTACTS: {
                invalidateFastScrollingIndexCache();
                id = insertRawContact(values, callerIsSyncAdapter);
                break;
            }

            case RAW_CONTACTS_ID_DATA: {
                invalidateFastScrollingIndexCache();
                final int segment = 1;
                values.put(Data.RAW_CONTACT_ID, uri.getPathSegments().get(segment));
                id = insertData(values, callerIsSyncAdapter);
                break;
            }

            case RAW_CONTACTS_ID_STREAM_ITEMS: {
                values.put(StreamItems.RAW_CONTACT_ID, uri.getPathSegments().get(1));
                id = insertStreamItem(values);
                break;
            }

            case DATA: {
                invalidateFastScrollingIndexCache();
                id = insertData(values, callerIsSyncAdapter);
                break;
            }

            case GROUPS: {
                id = insertGroup(values, callerIsSyncAdapter);
                break;
            }
//
//            case SETTINGS: {
//                id = insertSettings(uri, values);
//                mSyncToNetwork |= !callerIsSyncAdapter;
//                break;
//            }
//
//            case STATUS_UPDATES:
//                id = insertStatusUpdate(values);
//                break;
//            }
//
            case STREAM_ITEMS: {
                id = insertStreamItem(values);
                break;
            }

            case STREAM_ITEMS_PHOTOS: {
                id = insertStreamItemPhoto(values);
                break;
            }

            case STREAM_ITEMS_ID_PHOTOS: {
                values.put(StreamItemPhotos.STREAM_ITEM_ID, uri.getPathSegments().get(1));
                id = insertStreamItemPhoto(values);
                break;
            }

            default:
                throw new UnsupportedOperationException(mDbHelper.get().exceptionMessage(uri));
        }

        if (id < 0) {
            return null;
        }
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Inserts an item in the raw contacts table
     *
     * @param values the account this contact should be associated with. may be null.
     * @param callerIsSyncAdapter
     * @return the row ID of the newly created row
     */
    private long insertRawContact(ContentValues values, boolean callerIsSyncAdapter) {
        mValues.clear();
        mValues.putAll(values);

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        long rawContactId = db.insert(Tables.RAW_CONTACTS, RawContacts.DISPLAY_NAME_PRIMARY, mValues);
        // Trigger creation of a Contact based on this RawContact at the end of transaction.
        mTransactionContext.get().rawContactInserted(rawContactId);

//        if (!callerIsSyncAdapter) {
//            addAutoAddMembership(rawContactId);     // TODO Group membership
//            final Long starred = values.getAsLong(RawContacts.STARRED);
//            if (starred != null && starred != 0) {
//                updateFavoritesMembership(rawContactId, starred != 0);
//            }
//        }
        mProviderStatusUpdateNeeded = true;
        return rawContactId;
    }

    /**
     * Inserts an item in the data table
     *
     * @param values the values for the new row
     * @return the row ID of the newly created row
     */
    private long insertData(ContentValues values, boolean callerIsSyncAdapter) {
        mValues.clear();
        mValues.putAll(values);

        long rawContactId = mValues.getAsLong(Data.RAW_CONTACT_ID);

        // Replace package with internal mapping
//  TODO       final String packageName = mValues.getAsString(Data.RES_PACKAGE);
//        if (packageName != null) {
//            mValues.put(DataColumns.PACKAGE_ID, mDbHelper.get().getPackageId(packageName));
//        }
//        mValues.remove(Data.RES_PACKAGE);

        // Replace mimetype with internal mapping
        final String mimeType = mValues.getAsString(Data.MIMETYPE);
        if (TextUtils.isEmpty(mimeType)) {
            throw new IllegalArgumentException(Data.MIMETYPE + " is required");
        }
        mValues.put(DataColumns.MIMETYPE_ID, mDbHelper.get().getMimeTypeId(mimeType));
        mValues.remove(Data.MIMETYPE);

        DataRowHandler rowHandler = getDataRowHandler(mimeType);
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        final TransactionContext context = mTransactionContext.get();
        final long dataId = rowHandler.insert(db, context, rawContactId, mValues);
        context.markRawContactDirtyAndChanged(rawContactId, callerIsSyncAdapter);

        return dataId;
    }

    /**
     * Inserts an item in the stream_items table.  The account is checked against the
     * account in the raw contact for which the stream item is being inserted.  If the
     * new stream item results in more stream items under this raw contact than the limit,
     * the oldest one will be deleted (note that if the stream item inserted was the
     * oldest, it will be immediately deleted, and this will return 0).
     *
     * @param values the values for the new row
     * @return the stream item _ID of the newly created row, or 0 if it was not created
     */
    private long insertStreamItem(ContentValues values) {
        long id;
        mValues.clear();
        mValues.putAll(values);

        long rawContactId = mValues.getAsLong(StreamItems.RAW_CONTACT_ID);

        // Insert the new stream item.
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        id = db.insert(Tables.STREAM_ITEMS, null, mValues);
        if (id == -1) {
            // Insertion failed.
            return 0;
        }

        // Check to see if we're over the limit for stream items under this raw contact.
        // It's possible that the inserted stream item is older than the the existing
        // ones, in which case it may be deleted immediately (resetting the ID to 0).
        return cleanUpOldStreamItems(rawContactId, id);
    }

    /**
     * Queries the database for stream items under the given raw contact.  If there are
     * more entries than {@link ScContactsProvider#MAX_STREAM_ITEMS_PER_RAW_CONTACT},
     * the oldest entries (as determined by timestamp) will be deleted.
     * @param rawContactId The raw contact ID to examine for stream items.
     * @param insertedStreamItemId The ID of the stream item that was just inserted,
     *     prompting this cleanup.  Callers may pass 0 if no insertion prompted the
     *     cleanup.
     * @return The ID of the inserted stream item if it still exists after cleanup;
     *     0 otherwise.
     */
    private long cleanUpOldStreamItems(long rawContactId, long insertedStreamItemId) {
        long postCleanupInsertedStreamId = insertedStreamItemId;
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        Cursor c = db.query(Tables.STREAM_ITEMS, new String[]{StreamItems._ID},
                StreamItems.RAW_CONTACT_ID + "=?", new String[]{String.valueOf(rawContactId)},
                null, null, StreamItems.TIMESTAMP + " DESC, " + StreamItems._ID + " DESC");
        try {
            int streamItemCount = c.getCount();
            if (streamItemCount <= MAX_STREAM_ITEMS_PER_RAW_CONTACT) {
                // Still under the limit - nothing to clean up!
                return insertedStreamItemId;
            } else {
                c.moveToLast();
                while (c.getPosition() >= MAX_STREAM_ITEMS_PER_RAW_CONTACT) {
                    long streamItemId = c.getLong(0);
                    if (insertedStreamItemId == streamItemId) {
                        // The stream item just inserted is being deleted.
                        postCleanupInsertedStreamId = 0;
                    }
                    deleteStreamItem(db, c.getLong(0));
                    c.moveToPrevious();
                }
            }
        } finally {
            c.close();
        }
        return postCleanupInsertedStreamId;
    }

    /**
     * Inserts an item in the stream_item_photos table.  The account is checked against
     * the account in the raw contact that owns the stream item being modified.
     *
     * @param uri the insertion URI
     * @param values the values for the new row
     * @return the stream item photo _ID of the newly created row, or 0 if there was an issue
     *     with processing the photo or creating the row
     */
    private long insertStreamItemPhoto(ContentValues values) {
        long id = 0;
        mValues.clear();
        mValues.putAll(values);

        long streamItemId = mValues.getAsLong(StreamItemPhotos.STREAM_ITEM_ID);
        if (streamItemId != 0) {
            // Process the photo and store it.
            if (processStreamItemPhoto(mValues, false)) {
                // Insert the stream item photo.
                final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
                id = db.insert(Tables.STREAM_ITEM_PHOTOS, null, mValues);
            }
        }
        return id;
    }

    /**
     * Processes the photo contained in the {@link com.silentcircle.silentcontacts.ScContactsContract.StreamItemPhotos#PHOTO}
     * field of the given values, attempting to store it in the photo store.  If successful,
     * the resulting photo file ID will be added to the values for insert/update in the table.
     * <p>
     * If updating, it is valid for the picture to be empty or unspecified (the function will
     * still return true).  If inserting, a valid picture must be specified.
     * @param values The content values provided by the caller.
     * @param forUpdate Whether this photo is being processed for update (vs. insert).
     * @return Whether the insert or update should proceed.
     */
    private boolean processStreamItemPhoto(ContentValues values, boolean forUpdate) {
        if (!values.containsKey(StreamItemPhotos.PHOTO)) {
            return forUpdate;
        }
        byte[] photoBytes = values.getAsByteArray(StreamItemPhotos.PHOTO);
        if (photoBytes == null) {
            return forUpdate;
        }

        // Process the photo and store it.
        try {
            long photoFileId = mPhotoStore.get().insert(new PhotoProcessor(photoBytes, getMaxDisplayPhotoDim(),
                    getMaxThumbnailDim(), true), true);

            if (photoFileId != 0) {
                values.put(StreamItemPhotos.PHOTO_FILE_ID, photoFileId);
                values.remove(StreamItemPhotos.PHOTO);
                return true;
            } else {
                // Couldn't store the photo, return 0.
                Log.e(TAG, "Could not process stream item photo for insert");
                return false;
            }
        } catch (IOException ioe) {
            Log.e(TAG, "Could not process stream item photo for insert", ioe);
            return false;
        }
    }

    private void insertDataGroupMembership(long rawContactId, long groupId) {
        ContentValues groupMembershipValues = new ContentValues();
        groupMembershipValues.put(GroupMembership.GROUP_ROW_ID, groupId);
        groupMembershipValues.put(GroupMembership.RAW_CONTACT_ID, rawContactId);
        groupMembershipValues.put(DataColumns.MIMETYPE_ID, mDbHelper.get().getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE));
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        db.insert(Tables.DATA, null, groupMembershipValues);
    }

    private void deleteDataGroupMembership(long rawContactId, long groupId) {
        final String[] selectionArgs = {
                Long.toString(mDbHelper.get().getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE)),
                Long.toString(groupId),
                Long.toString(rawContactId)};
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        db.delete(Tables.DATA, SELECTION_GROUPMEMBERSHIP_DATA, selectionArgs);
    }

    /**
     * Inserts an item in the groups table
     */
    private long insertGroup(ContentValues values, boolean callerIsSyncAdapter) {
        mValues.clear();
        mValues.putAll(values);

        // Replace package with internal mapping
//        final String packageName = mValues.getAsString(Groups.RES_PACKAGE);
//        if (packageName != null) {
//            mValues.put(GroupsColumns.PACKAGE_ID, mDbHelper.get().getPackageId(packageName));
//        }
//        mValues.remove(Groups.RES_PACKAGE);

        final boolean isFavoritesGroup = mValues.getAsLong(Groups.FAVORITES) != null && mValues.getAsLong(Groups.FAVORITES) != 0;

        if (!callerIsSyncAdapter) {
            mValues.put(Groups.DIRTY, 1);
        }

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        long result = db.insert(Tables.GROUPS, Groups.TITLE, mValues);

        if (!callerIsSyncAdapter && isFavoritesGroup) {
            Cursor c = db.query(Tables.RAW_CONTACTS,
                    new String[]{RawContacts._ID, RawContacts.STARRED},
                    null, null, null, null, null);
            try {
                while (c.moveToNext()) {
                    if (c.getLong(1) != 0) {
                        final long rawContactId = c.getLong(0);
                        insertDataGroupMembership(rawContactId, result);
                        mTransactionContext.get().markRawContactDirtyAndChanged(rawContactId, callerIsSyncAdapter);
                    }
                }
            } finally {
                c.close();
            }
        }

//        if (mValues.containsKey(Groups.GROUP_VISIBLE)) {
//            mVisibleTouched = true;
//        }

        return result;
    }
    void invalidateFastScrollingIndexCache() {
        // FastScrollingIndexCache is thread-safe, no need to synchronize here.
        mFastScrollingIndexCache.invalidate();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (DEBUG) {
            Log.v(TAG, "deleteInTransaction: uri=" + uri +
                    "  selection=[" + selection + "]  args=" + Arrays.toString(selectionArgs));
        }
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return 0;

        waitForAccess(mWriteAccessLatch);
        switchToContactMode();

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        if (!mIsBatch)
            db.beginTransactionWithListener(this);
        try {
            int ret = deleteLocal(uri, selection, selectionArgs);
            if (!mIsBatch)
                db.setTransactionSuccessful();
            return ret;
        }
        finally {
            if (!mIsBatch)
                db.endTransaction();
        }
    }

    private int deleteLocal(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        final boolean callerIsSyncAdapter = readBooleanQueryParameter(uri, ScContactsContract.CALLER_IS_SYNCADAPTER, false);
        final int match = sUriMatcher.match(uri);
        switch (match) {
        case RAW_CONTACTS:
        {
            invalidateFastScrollingIndexCache();
            int numDeletes = 0;
            Cursor c = db.query(Views.RAW_CONTACTS, new String[] { RawContacts._ID }, selection, selectionArgs, null, null, null);
            try {
                while (c.moveToNext()) {
                    final long rawContactId = c.getLong(0);
                    numDeletes += deleteRawContact(rawContactId, callerIsSyncAdapter);
                }
            }
            finally {
                c.close();
            }
            return numDeletes;
        }
            case RAW_CONTACTS_DELETE_USAGE: {
                return deleteDataUsage();
            }


            case RAW_CONTACTS_ID:
         {
            invalidateFastScrollingIndexCache();
            final long rawContactId = ContentUris.parseId(uri);
            return deleteRawContact(rawContactId, callerIsSyncAdapter);
        }

        case DATA:
        {
            invalidateFastScrollingIndexCache();
            return deleteData(selection, selectionArgs, callerIsSyncAdapter);
        }

        case DATA_ID:
        case PHONES_ID:
        case EMAILS_ID:
        case CALLABLES_ID:
        case POSTALS_ID:
        {
            invalidateFastScrollingIndexCache();
            long dataId = ContentUris.parseId(uri);
            mSelectionArgs1[0] = String.valueOf(dataId);
            return deleteData(Data._ID + "=?", mSelectionArgs1, callerIsSyncAdapter);
        }

        case GROUPS_ID: {
            return deleteGroup(ContentUris.parseId(uri), callerIsSyncAdapter);
        }

        case GROUPS: {
            int numDeletes = 0;
            Cursor c = db.query(Views.GROUPS, Projections.ID, selection, selectionArgs, null, null, null);
            try {
                while (c.moveToNext()) {
                    numDeletes += deleteGroup(c.getLong(0), callerIsSyncAdapter);
                }
            } finally {
                c.close();
            }
            if (numDeletes > 0) {
            }
            return numDeletes;
        }

//            case SETTINGS: {
//                mSyncToNetwork |= !callerIsSyncAdapter;
//                return deleteSettings(uri, appendAccountToSelection(uri, selection), selectionArgs);
//            }
//
//            case STATUS_UPDATES:
//            case PROFILE_STATUS_UPDATES: {
//                return deleteStatusUpdates(selection, selectionArgs);
//            }
//
        case STREAM_ITEMS: {
            return deleteStreamItems(selection, selectionArgs);
        }

        case STREAM_ITEMS_ID: {
            return deleteStreamItems(StreamItems._ID + "=?",
                    new String[]{uri.getLastPathSegment()});
        }

        case RAW_CONTACTS_ID_STREAM_ITEMS_ID: {
            String rawContactId = uri.getPathSegments().get(1);
            String streamItemId = uri.getLastPathSegment();
            return deleteStreamItems(StreamItems.RAW_CONTACT_ID + "=? AND " + StreamItems._ID + "=?",
                    new String[]{rawContactId, streamItemId});
        }

        case STREAM_ITEMS_ID_PHOTOS: {
            String streamItemId = uri.getPathSegments().get(1);
            String selectionWithId =
                    (StreamItemPhotos.STREAM_ITEM_ID + "=" + streamItemId + " ")
                    + (selection == null ? "" : " AND (" + selection + ")");
            return deleteStreamItemPhotos(selectionWithId, selectionArgs);
        }

        case STREAM_ITEMS_ID_PHOTOS_ID: {
            String streamItemId = uri.getPathSegments().get(1);
            String streamItemPhotoId = uri.getPathSegments().get(3);

            return deleteStreamItemPhotos(StreamItemPhotosColumns.CONCRETE_ID + "=? AND "
                            + StreamItemPhotos.STREAM_ITEM_ID + "=?",
                            new String[]{streamItemPhotoId, streamItemId});
        }

        default: {
            throw new UnsupportedOperationException(mDbHelper.get().exceptionMessage(uri));
        }
        }
    }


    public int deleteRawContact(long rawContactId, boolean callerIsSyncAdapter) {
        mProviderStatusUpdateNeeded = true;

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        // Find and delete stream items associated with the raw contact.
       Cursor c = db.query(Tables.STREAM_ITEMS,
                new String[]{StreamItems._ID},
                StreamItems.RAW_CONTACT_ID + "=?", new String[]{String.valueOf(rawContactId)},
                null, null, null);
        try {
            while (c.moveToNext()) {
                deleteStreamItem(db, c.getLong(0));
            }
        } finally {
            c.close();
        }

        if (callerIsSyncAdapter || rawContactIsLocal(rawContactId)) {
            final int count = ContactsTableUtil.deleteContact(db, rawContactId);
            mTransactionContext.get().markRawContactChangedOrDeletedOrInserted(rawContactId);

            return count;
        }
        return 0;
    }

    /**
     * Returns whether the given raw contact ID is local (i.e. has no account associated with it).
     */
    private boolean rawContactIsLocal(long rawContactId) {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(false);
        Cursor c = db.query(Tables.RAW_CONTACTS, Projections.LITERAL_ONE,
                RawContactsColumns.CONCRETE_ID + "=?",
                new String[] {String.valueOf(rawContactId)}, null, null, null);
        try {
            return c.getCount() > 0;
        } finally {
            c.close();
        }
    }

    /**
     * Delete data row by row so that fixing of primaries etc work correctly.
     */
    private int deleteData(String selection, String[] selectionArgs, boolean callerIsSyncAdapter) {
        int count = 0;

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        // Note that the query will return data according to the access restrictions,
        // so we don't need to worry about deleting data we don't have permission to read.
        Uri dataUri = Data.CONTENT_URI;
        Cursor c = query(dataUri, DataRowHandler.DataDeleteQuery.COLUMNS, selection, selectionArgs, null);
        try {
            while(c.moveToNext()) {
                long rawContactId = c.getLong(DataRowHandler.DataDeleteQuery.RAW_CONTACT_ID);
                String mimeType = c.getString(DataRowHandler.DataDeleteQuery.MIMETYPE);
                DataRowHandler rowHandler = getDataRowHandler(mimeType);
                count += rowHandler.delete(db, mTransactionContext.get(), c);
                mTransactionContext.get().markRawContactDirtyAndChanged(rawContactId, callerIsSyncAdapter);
            }
        } finally {
            c.close();
        }

        return count;
    }

    private int deleteStreamItems(String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        int count = 0;
        final Cursor c = db.query(Views.STREAM_ITEMS, Projections.ID,
                selection, selectionArgs, null, null, null);
        try {
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                count += deleteStreamItem(db, c.getLong(0));
            }
        } finally {
            c.close();
        }
        return count;
    }

    private int deleteStreamItem(SQLiteDatabase db, long streamItemId) {
        deleteStreamItemPhotos(streamItemId);
        return db.delete(Tables.STREAM_ITEMS, StreamItems._ID + "=?",
                new String[]{String.valueOf(streamItemId)});
    }

    private int deleteStreamItemPhotos(String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        return db.delete(Tables.STREAM_ITEM_PHOTOS, selection, selectionArgs);
    }

    private int deleteStreamItemPhotos(long streamItemId) {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        // Note that this does not enforce the modifying account.
        return db.delete(Tables.STREAM_ITEM_PHOTOS, StreamItemPhotos.STREAM_ITEM_ID + "=?",
                new String[] { String.valueOf(streamItemId) });
    }

    public int deleteGroup(long groupId, boolean callerIsSyncAdapter) {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
//        mGroupIdCache.clear();
        final long groupMembershipMimetypeId = mDbHelper.get().getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE);
        db.delete(Tables.DATA, DataColumns.MIMETYPE_ID + "="
                + groupMembershipMimetypeId + " AND " + GroupMembership.GROUP_ROW_ID + "="
                + groupId, null);

        try {
            if (callerIsSyncAdapter) {
                return db.delete(Tables.GROUPS, Groups._ID + "=" + groupId, null);
            } else {
                mValues.clear();
                mValues.put(Groups.DELETED, 1);
                mValues.put(Groups.DIRTY, 1);
                return db.update(Tables.GROUPS, mValues, Groups._ID + "=" + groupId, null);
            }
        } finally {
//            mVisibleTouched = true;
        }
    }

    private int deleteDataUsage() {
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        db.delete(Tables.DATA_USAGE_STAT, null, null);
        return 1;
    }


    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (DEBUG) {
            Log.v(TAG, "update: uri=" + uri);
        }
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return 0;

        waitForAccess(mWriteAccessLatch);
        switchToContactMode();

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        if (!mIsBatch)
            db.beginTransactionWithListener(this);
        try {
            int ret = updateLocal(uri, values, selection, selectionArgs);
            if (!mIsBatch)
                db.setTransactionSuccessful();
            return ret;
        }
        finally {
            if (!mIsBatch)
                db.endTransaction();
        }
    }

    public int updateLocal(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (DEBUG) {
            Log.d(TAG, "updateInTransaction: uri=" + uri +
                    "  selection=[" + selection + "]  args=" + Arrays.toString(selectionArgs) +
                    "  values=[" + values + "]");
        }
        int count;

        final int match = sUriMatcher.match(uri);
        if (match == SYNCSTATE_ID && selection == null) {
            return 1;
        }
        final boolean callerIsSyncAdapter = readBooleanQueryParameter(uri, ScContactsContract.CALLER_IS_SYNCADAPTER, false);
        switch(match) {
//            case SYNCSTATE:
//                return mDbHelper.get().getSyncState().update(db, values,
//                        appendAccountToSelection(uri, selection), selectionArgs);
//
//            case SYNCSTATE_ID: {
//                selection = appendAccountToSelection(uri, selection);
//                String selectionWithId =
//                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
//                        + (selection == null ? "" : " AND (" + selection + ")");
//                return mDbHelper.get().getSyncState().update(db, values,
//                        selectionWithId, selectionArgs);
//            }
//
        case RAW_CONTACTS_ID_DATA:
        {
            invalidateFastScrollingIndexCache();
            final int segment = 1;
            final String rawContactId = uri.getPathSegments().get(segment);
            String selectionWithId = (Data.RAW_CONTACT_ID + "=" + rawContactId + " ")
                    + (selection == null ? "" : " AND " + selection);

            count = updateData(uri, values, selectionWithId, selectionArgs, callerIsSyncAdapter);

            break;
        }

        case DATA:
        {
            invalidateFastScrollingIndexCache();
            count = updateData(uri, values, selection, selectionArgs, callerIsSyncAdapter);
            if (count > 0) {
            }
            break;
        }

        case DATA_ID:
        case PHONES_ID:
        case EMAILS_ID:
        case CALLABLES_ID:
        case POSTALS_ID:
        {
            invalidateFastScrollingIndexCache();
            count = updateData(uri, values, selection, selectionArgs, callerIsSyncAdapter);
            if (count > 0) {
            }
            break;
        }

        case RAW_CONTACTS:
        {
            invalidateFastScrollingIndexCache();
            count = updateRawContacts(values, selection, selectionArgs, callerIsSyncAdapter);
            break;
        }

        case RAW_CONTACTS_ID: {
            invalidateFastScrollingIndexCache();
            long rawContactId = ContentUris.parseId(uri);
            if (selection != null) {
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                count = updateRawContacts(values, RawContacts._ID + "=?" + " AND(" + selection + ")", selectionArgs,
                        callerIsSyncAdapter);
            }
            else {
                mSelectionArgs1[0] = String.valueOf(rawContactId);
                count = updateRawContacts(values, RawContacts._ID + "=?", mSelectionArgs1, callerIsSyncAdapter);
            }
            break;
        }

        case GROUPS: {
            count = updateGroups(values, selection, selectionArgs, callerIsSyncAdapter);
            if (count > 0) {
            }
            break;
        }

        case GROUPS_ID: {
            long groupId = ContentUris.parseId(uri);
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(groupId));
            String selectionWithId = Groups._ID + "=? "
                    + (selection == null ? "" : " AND " + selection);
            count = updateGroups(values, selectionWithId, selectionArgs,
                    callerIsSyncAdapter);
            if (count > 0) {
            }
            break;
        }

//            case SETTINGS: {
//                count = updateSettings(uri, values, appendAccountToSelection(uri, selection),
//                        selectionArgs);
//                mSyncToNetwork |= !callerIsSyncAdapter;
//                break;
//            }
//
//            case STATUS_UPDATES:
//                count = updateStatusUpdate(uri, values, selection, selectionArgs);
//                break;
//            }
//
        case STREAM_ITEMS: {
            count = updateStreamItems(values, selection, selectionArgs);
            break;
        }

        case STREAM_ITEMS_ID: {
            count = updateStreamItems(values, StreamItems._ID + "=?",
                    new String[]{uri.getLastPathSegment()});
            break;
        }

        case RAW_CONTACTS_ID_STREAM_ITEMS_ID: {
            String rawContactId = uri.getPathSegments().get(1);
            String streamItemId = uri.getLastPathSegment();
            count = updateStreamItems(values,
                    StreamItems.RAW_CONTACT_ID + "=? AND " + StreamItems._ID + "=?",
                    new String[]{rawContactId, streamItemId});
            break;
        }

        case STREAM_ITEMS_PHOTOS: {
            count = updateStreamItemPhotos(values, selection, selectionArgs);
            break;
        }

        case STREAM_ITEMS_ID_PHOTOS: {
            String streamItemId = uri.getPathSegments().get(1);
            count = updateStreamItemPhotos(values,
                    StreamItemPhotos.STREAM_ITEM_ID + "=?", new String[]{streamItemId});
            break;
        }

        case STREAM_ITEMS_ID_PHOTOS_ID: {
            String streamItemId = uri.getPathSegments().get(1);
            String streamItemPhotoId = uri.getPathSegments().get(3);
            count = updateStreamItemPhotos(values,
                    StreamItemPhotosColumns.CONCRETE_ID + "=? AND " +
                            StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "=?",
                            new String[]{streamItemPhotoId, streamItemId});
            break;
        }

        case DIRECTORIES: {
            mContactDirectoryManager.scanPackagesByUid(Binder.getCallingUid());
            count = 1;
            break;
        }

            case DATA_USAGE_FEEDBACK_ID: {
                if (handleDataUsageFeedback(uri)) {
                    count = 1;
                } 
                else {
                    count = 0;
                }
                break;
            }

        default: {
            throw new UnsupportedOperationException(mDbHelper.get().exceptionMessage(uri));
        }
        }
        updateSearchIndexInTransaction();
        notifyChange(false);
        return count;
    }


    private int updateRawContacts(ContentValues values, String selection, String[] selectionArgs, boolean callerIsSyncAdapter) {

        if (!callerIsSyncAdapter) {
            selection = DbQueryUtils.concatenateWhere(selection, RawContacts.RAW_CONTACT_IS_READ_ONLY + "=0");
        }

        int count = 0;
        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);
        Cursor cursor = db.query(Views.RAW_CONTACTS, Projections.ID, selection, selectionArgs, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long rawContactId = cursor.getLong(0);
                updateRawContact(db, rawContactId, values, callerIsSyncAdapter);
                count++;
            }
        } finally {
            cursor.close();
        }

        return count;
    }

    private int updateRawContact(SQLiteDatabase db, long rawContactId, ContentValues values,  boolean callerIsSyncAdapter) {
        final String selection = RawContactsColumns.CONCRETE_ID + " = ?";
        mSelectionArgs1[0] = Long.toString(rawContactId);

        int count = db.update(Tables.RAW_CONTACTS, values, selection, mSelectionArgs1);
        if (count > 0) {
            if (values.containsKey(RawContacts.STARRED)) {
                if (!callerIsSyncAdapter) {
//  TODO                    updateFavoritesMembership(rawContactId, values.getAsLong(RawContacts.STARRED) != 0);
                }
            }


            if (values.containsKey(RawContacts.NAME_VERIFIED)) {

                // If setting NAME_VERIFIED for this raw contact, reset it for all
                // other raw contacts in the same aggregate
                if (values.getAsInteger(RawContacts.NAME_VERIFIED) != 0) {
                    mDbHelper.get().resetNameVerifiedForOtherRawContacts(rawContactId);
                }
            }
            mTransactionContext.get().markRawContactChangedOrDeletedOrInserted(rawContactId);
        }
        return count;
    }

    private int updateStreamItems(ContentValues values, String selection,
            String[] selectionArgs) {
        // Stream items can't be moved to a new raw contact.
        values.remove(StreamItems.RAW_CONTACT_ID);

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        // If there's been no exception, the update should be fine.
        return db.update(Tables.STREAM_ITEMS, values, selection, selectionArgs);
    }

    private int updateStreamItemPhotos(ContentValues values, String selection, String[] selectionArgs) {
        // Stream item photos can't be moved to a new stream item.
        values.remove(StreamItemPhotos.STREAM_ITEM_ID);

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        // Process the photo (since we're updating, it's valid for the photo to not be present).
        if (processStreamItemPhoto(values, true)) {
            // If there's been no exception, the update should be fine.
            return db.update(Tables.STREAM_ITEM_PHOTOS, values, selection, selectionArgs);
        }
        return 0;
    }

    private int updateData(Uri uri, ContentValues values, String selection, String[] selectionArgs, boolean callerIsSyncAdapter) {
        mValues.clear();
        mValues.putAll(values);
        mValues.remove(Data._ID);
        mValues.remove(Data.RAW_CONTACT_ID);
        mValues.remove(Data.MIMETYPE);

        String packageName = values.getAsString(Data.RES_PACKAGE);
        if (packageName != null) {
            mValues.remove(Data.RES_PACKAGE);
//  TODO            mValues.put(DataColumns.PACKAGE_ID, mDbHelper.get().getPackageId(packageName));
        }

        if (!callerIsSyncAdapter) {
            selection = DbQueryUtils.concatenateWhere(selection, Data.IS_READ_ONLY + "=0");
        }

        int count = 0;

        // Note that the query will return data according to the access restrictions,
        // so we don't need to worry about updating data we don't have permission to read.
        Cursor c = queryLocal(uri,
                DataRowHandler.DataUpdateQuery.COLUMNS,
                selection, selectionArgs, null, -1 /* directory ID, null */);
        try {
            while(c.moveToNext()) {
                count += updateData(mValues, c, callerIsSyncAdapter);
            }
        } finally {
            c.close();
        }

        return count;
    }

    private int updateData(ContentValues values, Cursor c, boolean callerIsSyncAdapter) {
        if (values.size() == 0) {
            return 0;
        }

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        final String mimeType = c.getString(DataRowHandler.DataUpdateQuery.MIMETYPE);
        DataRowHandler rowHandler = getDataRowHandler(mimeType);
        boolean updated = rowHandler.update(db, mTransactionContext.get(), values, c, callerIsSyncAdapter);
        if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
            scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
        }
        return updated ? 1 : 0;
    }

    private interface GroupAccountQuery {
        String TABLE = Views.GROUPS;

        String[] COLUMNS = new String[] {
                Groups._ID,
        };
        int ID = 0;
    }

    private int updateGroups(ContentValues originalValues, String selectionWithId, String[] selectionArgs,
            boolean callerIsSyncAdapter) {
//        mGroupIdCache.clear();

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        final ContentValues updatedValues = new ContentValues();
        updatedValues.putAll(originalValues);

        if (!callerIsSyncAdapter && !updatedValues.containsKey(Groups.DIRTY)) {
            updatedValues.put(Groups.DIRTY, 1);
        }
//        if (updatedValues.containsKey(Groups.GROUP_VISIBLE)) {
//            mVisibleTouched = true;
//        }

        // Prepare for account change

        // Look for all affected rows, and change them row by row.
        final Cursor c = db.query(GroupAccountQuery.TABLE, GroupAccountQuery.COLUMNS, selectionWithId, selectionArgs, null, null, null);
        int returnCount = 0;
        try {
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                final long groupId = c.getLong(GroupAccountQuery.ID);

                mSelectionArgs1[0] = Long.toString(groupId);

                // Finally do the actual update.
                final int count = db.update(Tables.GROUPS, updatedValues, GroupsColumns.CONCRETE_ID + "=?", mSelectionArgs1);
                returnCount += count;
            }
        } finally {
            c.close();
        }
        return returnCount;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (DEBUG) {
            Log.d(TAG, "query: uri=" + uri + "  projection=" + Arrays.toString(projection) +
                    "  selection=[" + selection + "]  args=" + Arrays.toString(selectionArgs) +
                    "  order=[" + sortOrder + "]");
        }
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return null;

        switchToContactMode();
        waitForAccess(mReadAccessLatch);

        // Enforce stream items access check if applicable.
        enforceSocialStreamReadPermission(uri);

        // Otherwise proceed with a normal query against the contacts DB.
        switchToContactMode();
        String directory = getQueryParameter(uri, ScContactsContract.DIRECTORY_PARAM_KEY);
        if (directory == null) {
            return addSnippetExtrasToCursor(uri,
                    queryLocal(uri, projection, selection, selectionArgs, sortOrder, -1 /*, cancellationSignal*/));
        } else if (directory.equals("0")) {
            return addSnippetExtrasToCursor(uri,
                    queryLocal(uri, projection, selection, selectionArgs, sortOrder, Directory.DEFAULT/*, cancellationSignal*/));
        } else if (directory.equals("1")) {
            return addSnippetExtrasToCursor(uri,
                    queryLocal(uri, projection, selection, selectionArgs, sortOrder, Directory.LOCAL_INVISIBLE/*, cancellationSignal*/));
        }

        // TODO - Directory support for external sync

//        DirectoryInfo directoryInfo = getDirectoryAuthority(directory);
//        if (directoryInfo == null) {
//            Log.e(TAG, "Invalid directory ID: " + uri);
//            return null;
//        }
//
//        Builder builder = new Uri.Builder();
//        builder.scheme(ContentResolver.SCHEME_CONTENT);
//        builder.authority(directoryInfo.authority);
//        builder.encodedPath(uri.getEncodedPath());
//        if (directoryInfo.accountName != null) {
//            builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, directoryInfo.accountName);
//        }
//        if (directoryInfo.accountType != null) {
//            builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, directoryInfo.accountType);
//        }
//
//        String limit = getLimit(uri);
//        if (limit != null) {
//            builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit);
//        }
//
//        Uri directoryUri = builder.build();
//
//        if (projection == null) {
//            projection = getDefaultProjection(uri);
//        }
//
//        Cursor cursor = getContext().getContentResolver().query(directoryUri, projection, selection,
//                selectionArgs, sortOrder);
//
//        if (cursor == null) {
//            return null;
//        }
//
//        // Load the cursor contents into a memory cursor (backed by a cursor window) and close the
//        // underlying cursor.
//        try {
//            MemoryCursor memCursor = new MemoryCursor(null, cursor.getColumnNames());
//            memCursor.fillFromCursor(cursor);
//            return memCursor;
//        } finally {
//            cursor.close();
//        }
        return null;
    }

    private Cursor addSnippetExtrasToCursor(Uri uri, Cursor cursor) {

        // If the cursor doesn't contain a snippet column, don't bother wrapping it.
        if (cursor.getColumnIndex(SearchSnippetColumns.SNIPPET) < 0) {
            return cursor;
        }

       String query = uri.getLastPathSegment();

        // Snippet data is needed for the snippeting on the client side, so store it in the cursor
        if (cursor instanceof AbstractCursor && deferredSnippetingRequested(uri)){
            Bundle oldExtras = cursor.getExtras();
            Bundle extras = new Bundle();
            if (oldExtras != null) {
                extras.putAll(oldExtras);
            }
            extras.putString(ScContactsContract.DEFERRED_SNIPPETING_QUERY, query);

            // setExtras() is hidden in AbstractCursor, do a lookup, make it accessible and use it

            Class<?> c = ((Object)(cursor)).getClass();  // overcome some issues in AS (IntelliJ)
            try {
                java.lang.reflect.Method m = c.getMethod("setExtras", Bundle.class);
                m.setAccessible(true);
                m.invoke(cursor, extras);
            } catch (Exception e) {
                Log.i(TAG, "AbstractCursor setExtra - addSnippetExtrasToCursor: " + e);
            }
        }
        return cursor;
    }

    private Cursor addDeferredSnippetingExtra(Cursor cursor) {
        if (cursor instanceof AbstractCursor){
            Bundle oldExtras = cursor.getExtras();
            Bundle extras = new Bundle();
            if (oldExtras != null) {
                extras.putAll(oldExtras);
            }
            extras.putBoolean(ScContactsContract.DEFERRED_SNIPPETING, true);

            // setExtras() is hidden in AbstractCursor, do a lookup, make it accessible and use it
            Class<?> c = ((Object)(cursor)).getClass();  // overcome some issues in AS (IntelliJ)
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod("setExtras", Bundle.class);
                m.setAccessible(true);
                m.invoke(cursor, extras);
            } catch (Exception e) {
                Log.i(TAG, "AbstractCursor setExtra - addDeferredSnippetingExtra: " + e);
            }
        }
        return cursor;
    }

    protected Cursor queryLocal(final Uri uri, final String[] projection, String selection,
            String[] selectionArgs, String sortOrder, final long directoryId
            /*, final CancellationSignal cancellationSignal*/) {

        final SQLiteDatabase db = mDbHelper.get().getDatabase(false);

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String groupBy = null;
        String having = null;
        String limit = getLimit(uri);
        boolean snippetDeferred = false;

        // The expression used in bundleLetterCountExtras() to get count.
        String addressBookIndexerCountExpression = null;

        final int match = sUriMatcher.match(uri);
        switch (match) {
        case RAW_CONTACTS_AS_VCARD: {
            long contactId = Long.parseLong(uri.getPathSegments().get(2));
            qb.setTables(Views.RAW_CONTACTS);
            qb.setProjectionMap(sContactsVCardProjectionMap);
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
            qb.appendWhere(RawContacts._ID + "=?");
            break;
        }

//            case SYNCSTATE:
        case RAW_CONTACTS_FILTER: {
            String filterParam = "";
            boolean deferredSnipRequested = deferredSnippetingRequested(uri);
            if (uri.getPathSegments().size() > 2) {
                filterParam = uri.getLastPathSegment();
            }
            // If the query consists of a single word, we can do snippetizing after-the-fact for
            // a performance boost.  Otherwise, we can't defer.
            snippetDeferred = isSingleWordQuery(filterParam)  && deferredSnipRequested && snippetNeeded(projection);
            setTablesAndProjectionMapForContactsWithSnippet(qb, uri, projection, filterParam, directoryId, snippetDeferred);
            break;
        }
        case RAW_CONTACTS_ID_PHOTO: {
            long contactId = Long.parseLong(uri.getPathSegments().get(1));
            setTablesAndProjectionMapForData(qb, projection, false);
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
            qb.appendWhere(" AND " + Data.RAW_CONTACT_ID + "=?");
            qb.appendWhere(" AND " + Data._ID + "=" + RawContacts.PHOTO_ID);
            break;
        }

            case RAW_CONTACTS_STREQUENT_FILTER:
            case RAW_CONTACTS_STREQUENT: {
                // Basically the resultant SQL should look like this:
                // (SQL for listing starred items)
                // UNION ALL
                // (SQL for listing frequently contacted items)
                // ORDER BY ...

                final boolean phoneOnly = readBooleanQueryParameter(uri, ScContactsContract.STREQUENT_PHONE_ONLY, false);
                if (match == RAW_CONTACTS_STREQUENT_FILTER && uri.getPathSegments().size() > 3 && phoneOnly) {
                    String filterParam = uri.getLastPathSegment();
                    StringBuilder sb = new StringBuilder();
                    sb.append(RawContacts._ID + " IN ");
                    appendContactFilterAsNestedQuery(sb, filterParam, null);
                    selection = DbQueryUtils.concatenateClauses(selection, sb.toString());
                }

                String[] subProjection = null;
                if (projection != null) {
                    subProjection = new String[projection.length + 2];
                    System.arraycopy(projection, 0, subProjection, 0, projection.length);
                    subProjection[projection.length + 0] = DataUsageStatColumns.TIMES_USED;
                    subProjection[projection.length + 1] = DataUsageStatColumns.LAST_TIME_USED;
                }

//                // Build the first query for starred
//                setTablesAndProjectionMapForRawContactsStrequent(qb, false);
//                qb.setProjectionMap(phoneOnly ?
//                        sStrequentPhoneOnlyStarredProjectionMap : sStrequentStarredProjectionMap);
//                if (phoneOnly) {
//                    qb.appendWhere(DbQueryUtils.concatenateClauses(selection, RawContacts.HAS_PHONE_NUMBER + "=1"));
//                }
//                // ***** SQLCipher has a different QueryBuilder API
////                qb.setStrict(true);
//                final String starredInnerQuery = qb.buildQuery(subProjection,
//                        RawContacts.STARRED + "=1", null, RawContacts._ID, null,
//                        RawContacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC", null);
//
//                // Reset the builder.
//                qb = new SQLiteQueryBuilder();
////                qb.setStrict(true);

                // String that will store the query for starred contacts. For phone only queries,
                // these will return a list of all phone numbers that belong to starred contacts.
                final String starredInnerQuery;
                // String that will store the query for frequents. These JOINS can be very slow
                // if assembled in the wrong order. Be sure to test changes against huge databases.
                final String frequentInnerQuery;

                if (phoneOnly) {
                    final StringBuilder tableBuilder = new StringBuilder();
                    // In phone only mode, we need to look at view_data instead of
                    // contacts/raw_contacts to obtain actual phone numbers. One problem is that
                    // view_data is much larger than view_contacts, so our query might become much
                    // slower.
                    //

                    // For starred phone numbers, we select only phone numbers that belong to
                    // starred contacts, and then do an outer join against the data usage table,
                    // to make sure that even if a starred number hasn't been previously used,
                    // it is included in the list of strequent numbers.
                    tableBuilder.append("(SELECT * FROM " + Views.DATA + " WHERE "
                            + RawContacts.STARRED + "=1)" + " AS " + Tables.DATA
                            + " LEFT OUTER JOIN " + Tables.DATA_USAGE_STAT
                            + " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "="
                            + DataColumns.CONCRETE_ID + " AND "
                            + DataUsageStatColumns.CONCRETE_USAGE_TYPE + "="
                            + DataUsageStatColumns.USAGE_TYPE_INT_CALL + ")");
//                    appendContactPresenceJoin(tableBuilder, projection, RawContacts.CONTACT_ID);
//                    appendContactStatusUpdateJoin(tableBuilder, projection,
//                            ContactsColumns.LAST_STATUS_UPDATE_ID);
                    qb.setTables(tableBuilder.toString());
                    qb.setProjectionMap(sStrequentPhoneOnlyProjectionMap);
                    final long phoneMimeTypeId =
                            mDbHelper.get().getMimeTypeId(Phone.CONTENT_ITEM_TYPE);
                    final long sipMimeTypeId =
                            mDbHelper.get().getMimeTypeId(SipAddress.CONTENT_ITEM_TYPE);

                    qb.appendWhere(DbQueryUtils.concatenateClauses(
                            selection,
                            "(" + RawContacts.STARRED + "=1",
                            DataColumns.MIMETYPE_ID + " IN (" +
                                    phoneMimeTypeId + ", " + sipMimeTypeId + "))"));
                    starredInnerQuery = qb.buildQuery(subProjection, null, null, null,
                            null, Data.IS_SUPER_PRIMARY + " DESC," + SORT_BY_DATA_USAGE, null);

                    qb = new SQLiteQueryBuilder();
//                    qb.setStrict(true);

                    // Construct the query string for frequent phone numbers
                    tableBuilder.setLength(0);
                    // For frequent phone numbers, we start from data usage table and join
                    // view_data to the table, assuming data usage table is quite smaller than
                    // data rows (almost always it should be), and we don't want any phone
                    // numbers not used by the user. This way sqlite is able to drop a number of
                    // rows in view_data in the early stage of data lookup.
                    tableBuilder.append(Tables.DATA_USAGE_STAT
                            + " INNER JOIN " + Views.DATA + " " + Tables.DATA
                            + " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "="
                            + DataColumns.CONCRETE_ID + " AND "
                            + DataUsageStatColumns.CONCRETE_USAGE_TYPE + "="
                            + DataUsageStatColumns.USAGE_TYPE_INT_CALL + ")");
//                    appendContactPresenceJoin(tableBuilder, projection, RawContacts.CONTACT_ID);
//                    appendContactStatusUpdateJoin(tableBuilder, projection,
//                            ContactsColumns.LAST_STATUS_UPDATE_ID);

                    qb.setTables(tableBuilder.toString());
                    qb.setProjectionMap(sStrequentPhoneOnlyProjectionMap);
                    qb.appendWhere(DbQueryUtils.concatenateClauses(
                            selection,
                            "(" + RawContacts.STARRED + "=0 OR " + RawContacts.STARRED + " IS NULL",
                            DataColumns.MIMETYPE_ID + " IN (" + phoneMimeTypeId + ", " + sipMimeTypeId + "))" ));
                    frequentInnerQuery = qb.buildQuery(subProjection, null, null, null, null,
                            SORT_BY_DATA_USAGE, "25");
                } else {
                    // Build the first query for starred contacts
//                    qb.setStrict(true);
                    setTablesAndProjectionMapForRawContactsStrequent(qb, false);
                    qb.setProjectionMap(sStrequentStarredProjectionMap);

                    starredInnerQuery = qb.buildQuery(subProjection,
                            DbQueryUtils.concatenateClauses(selection, RawContacts.STARRED + "=1"),
                            null, RawContacts._ID, null, RawContacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
                            null);

                    if (match == RAW_CONTACTS_STREQUENT_FILTER && uri.getPathSegments().size() > 3) {
                        String filterParam = uri.getLastPathSegment();
                        StringBuilder sb = new StringBuilder();
                        sb.append("raw_join_view._id IN ");
                        appendContactFilterAsNestedQuery(sb, filterParam, "raw_join_view._id");
                        selection = DbQueryUtils.concatenateClauses(selection, sb.toString());
                    }
                    // Reset the builder, and build the second query for frequents contacts
                    qb = new SQLiteQueryBuilder();
//                    qb.setStrict(true);
                    setTablesAndProjectionMapForRawContactsStrequent(qb, true);
                    qb.setProjectionMap(sStrequentFrequentProjectionMap);
                    qb.appendWhere(DbQueryUtils.concatenateClauses(
                            selection,
                            "(" + DataUsageStatColumns.CONCRETE_TIMES_USED + " > 0 AND "
                                + Data.RAW_CONTACT_ID + " = raw_join_view._id  AND "
                                + RawContacts.STARRED + " =0 OR " + RawContacts.STARRED + " IS NULL)"));
                    frequentInnerQuery = qb.buildQuery(subProjection,
                            null, null, "raw_join_view._id", null, SORT_BY_DATA_USAGE, "25");
                }

                // We need to wrap the inner queries in an extra select, because they contain
                // their own SORT and LIMIT
                final String frequentQuery = "SELECT * FROM (" + frequentInnerQuery + ")";
                final String starredQuery = "SELECT * FROM (" + starredInnerQuery + ")";

                // Put them together
                final String unionQuery =
                        qb.buildUnionQuery(new String[] {starredQuery, frequentQuery}, null, null);

                // Here, we need to use selection / selectionArgs (supplied from users) "twice",
                // as we want them both for starred items and for frequently contacted items.
                //
                // e.g. if the user specify selection = "starred =?" and selectionArgs = "0",
                // the resultant SQL should be like:
                // SELECT ... WHERE starred =? AND ...
                // UNION ALL
                // SELECT ... WHERE starred =? AND ...
                String[] doubledSelectionArgs = null;
                if (selectionArgs != null) {
                    final int length = selectionArgs.length;
                    doubledSelectionArgs = new String[length * 2];
                    System.arraycopy(selectionArgs, 0, doubledSelectionArgs, 0, length);
                    System.arraycopy(selectionArgs, 0, doubledSelectionArgs, length, length);
                }

                Cursor cursor = db.rawQuery(unionQuery, doubledSelectionArgs);
                if (cursor != null) {
                    cursor.setNotificationUri(getContext().getContentResolver(), ScContactsContract.AUTHORITY_URI);
                }
                return cursor;
            }

            case RAW_CONTACTS_FREQUENT: {
                setTablesAndProjectionMapForRawContacts(qb);
                qb.setProjectionMap(sStrequentFrequentProjectionMap);
                groupBy = RawContacts._ID;
                if (!TextUtils.isEmpty(sortOrder)) {
                    sortOrder = FREQUENT_ORDER_BY + ", " + sortOrder;
                } else {
                    sortOrder = FREQUENT_ORDER_BY;
                }
                break;
            }


            case STREAM_ITEMS: {
            setTablesAndProjectionMapForStreamItems(qb);
            break;
        }

        case STREAM_ITEMS_ID: {
            setTablesAndProjectionMapForStreamItems(qb);
            selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
            qb.appendWhere(StreamItems._ID + "=?");
            break;
        }

//            case STREAM_ITEMS_LIMIT: {
//                return buildSingleRowResult(projection, new String[] {StreamItems.MAX_ITEMS},
//                        new Object[] {MAX_STREAM_ITEMS_PER_RAW_CONTACT});
//            }
//
        case STREAM_ITEMS_PHOTOS: {
            setTablesAndProjectionMapForStreamItemPhotos(qb);
            break;
        }

        case STREAM_ITEMS_ID_PHOTOS: {
            setTablesAndProjectionMapForStreamItemPhotos(qb);
            String streamItemId = uri.getPathSegments().get(1);
            selectionArgs = insertSelectionArg(selectionArgs, streamItemId);
            qb.appendWhere(StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "=?");
            break;
        }

        case STREAM_ITEMS_ID_PHOTOS_ID: {
            setTablesAndProjectionMapForStreamItemPhotos(qb);
            String streamItemId = uri.getPathSegments().get(1);
            String streamItemPhotoId = uri.getPathSegments().get(3);
            selectionArgs = insertSelectionArg(selectionArgs, streamItemPhotoId);
            selectionArgs = insertSelectionArg(selectionArgs, streamItemId);
            qb.appendWhere(StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "=? AND "
                    + StreamItemPhotosColumns.CONCRETE_ID + "=?");
            break;
        }

            case PHOTO_DIMENSIONS: {
                return buildSingleRowResult(projection,
                        new String[] {DisplayPhoto.DISPLAY_MAX_DIM, DisplayPhoto.THUMBNAIL_MAX_DIM},
                        new Object[] {getMaxDisplayPhotoDim(), getMaxThumbnailDim()});
            }

        case PHONES:
        case CALLABLES:
        {
            final String mimeTypeIsPhoneExpression = DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForPhone();
            final String mimeTypeIsSipExpression = DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForSip();
            setTablesAndProjectionMapForData(qb, projection, false);
            if (match == CALLABLES) {
                qb.appendWhere(" AND ((" + mimeTypeIsPhoneExpression + ") OR (" + mimeTypeIsSipExpression + "))");
            }
            else {
                qb.appendWhere(" AND " + mimeTypeIsPhoneExpression);
            }

            final boolean removeDuplicates = readBooleanQueryParameter(uri, ScContactsContract.REMOVE_DUPLICATE_ENTRIES, false);
            if (removeDuplicates) {
                groupBy = Data.DATA1;

                // In this case, because we dedupe phone numbers, the address book indexer needs
                // to take it into account too. (Otherwise headers will appear in wrong
                // positions.)
                // So use count(distinct pair(CONTACT_ID, PHONE NUMBER)) instead of count(*).
                // But because there's no such thing as pair() on sqlite, we use
                // CONTACT_ID || ',' || PHONE NUMBER instead.
                // This only slows down the query by 14% with 10,000 contacts.
                addressBookIndexerCountExpression = "DISTINCT " + Data.DATA1;
            }
            break;
        }

        case PHONES_ID:
        case CALLABLES_ID:
        {
            final String mimeTypeIsPhoneExpression = DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForPhone();
            final String mimeTypeIsSipExpression = DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForSip();

            setTablesAndProjectionMapForData(qb, projection, false);
            selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());

            if (match == CALLABLES_ID) {
                qb.appendWhere(" AND ((" + mimeTypeIsPhoneExpression + ") OR (" + mimeTypeIsSipExpression + "))");
            }
            else {
                qb.appendWhere(" AND " + mimeTypeIsPhoneExpression);
            }
            qb.appendWhere(" AND " + Data._ID + "=?");
            break;
        }

            case PHONES_FILTER:
            case CALLABLES_FILTER:
                {
            final String mimeTypeIsPhoneExpression = DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForPhone();
            final String mimeTypeIsSipExpression = DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForSip();

            String typeParam = uri.getQueryParameter(DataUsageFeedback.USAGE_TYPE);
            final int typeInt = getDataUsageFeedbackType(typeParam, DataUsageStatColumns.USAGE_TYPE_INT_CALL);
            setTablesAndProjectionMapForData(qb, projection, true, typeInt);
            if (match == CALLABLES_FILTER) {
                qb.appendWhere(" AND ((" + mimeTypeIsPhoneExpression + ") OR (" + mimeTypeIsSipExpression + "))");
            }
            else {
                qb.appendWhere(" AND " + mimeTypeIsPhoneExpression);
            }
            if (uri.getPathSegments().size() > 2) {
                final String filterParam = uri.getLastPathSegment();

                final boolean searchDisplayName = readBooleanQueryParameter(uri, Phone.SEARCH_DISPLAY_NAME_KEY, true);
                final boolean searchPhoneNumber = readBooleanQueryParameter(uri, Phone.SEARCH_PHONE_NUMBER_KEY, true);

                final StringBuilder sb = new StringBuilder();
                sb.append(" AND (");

                boolean hasCondition = false;

                // TODO This only searches the name field. Search other fields, such as
                // note, nickname, as well. (Which should be disabled by default.)
                // Fix EMAILS_FILTER too.
                final String ftsMatchQuery = searchDisplayName ? SearchIndexManager.getFtsMatchQuery(filterParam,
                        SearchIndexManager.FtsQueryBuilder.UNSCOPED_NORMALIZING) : null;

                if (!TextUtils.isEmpty(ftsMatchQuery)) {
                    sb.append(Data.RAW_CONTACT_ID + " IN " + "(SELECT " + RawContactsColumns.CONCRETE_ID + " FROM "
                            + Tables.SEARCH_INDEX + " JOIN " + Tables.RAW_CONTACTS + " ON (" + Tables.SEARCH_INDEX + "."
                            + SearchIndexColumns.RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID + ")" + " WHERE "
                            + SearchIndexColumns.NAME + " MATCH '");
                    sb.append(ftsMatchQuery);
                    sb.append("')");
                    hasCondition = true;
                }

                if (searchPhoneNumber) {
                    final String number = PhoneNumberHelper.normalizeNumber(filterParam);
                    if (!TextUtils.isEmpty(number)) {
                        if (hasCondition) {
                            sb.append(" OR ");
                        }
                        sb.append(Data._ID + " IN (SELECT DISTINCT " + PhoneLookupColumns.DATA_ID + " FROM "
                                + Tables.PHONE_LOOKUP + " WHERE " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                        sb.append(number);
                        sb.append("%')");
                        hasCondition = true;
                    }

                    if (!TextUtils.isEmpty(filterParam) && match == CALLABLES_FILTER) {
                        // If the request is via Callable uri, Sip addresses matching the filter
                        // parameter should be returned.
                        if (hasCondition) {
                            sb.append(" OR ");
                        }
                        sb.append("(");
                        sb.append(mimeTypeIsSipExpression);
                        sb.append(" AND ((" + Data.DATA1 + " LIKE ");
                        DatabaseUtils.appendEscapedSQLString(sb, filterParam + '%');
                        sb.append(") OR (" + Data.DATA1 + " LIKE ");
                        // Users may want SIP URIs starting from "sip:"
                        DatabaseUtils.appendEscapedSQLString(sb, "sip:" + filterParam + '%');
                        sb.append(")))");
                        hasCondition = true;
                    }
                }

                if (!hasCondition) {
                    // If it is neither a phone number nor a name, the query should return
                    // an empty cursor. Let's ensure that.
                    sb.append("0");
                }
                sb.append(")");
                qb.appendWhere(sb);
            }
            if (match == CALLABLES_FILTER) {
                // If the row is for a phone number that has a normalized form, we should use
                // the normalized one as PHONES_FILTER does, while we shouldn't do that
                // if the row is for a sip address.
                String isPhoneAndHasNormalized = "(" + mimeTypeIsPhoneExpression + " AND " + Phone.NORMALIZED_NUMBER
                        + " IS NOT NULL)";
                groupBy = "(CASE WHEN " + isPhoneAndHasNormalized + " THEN " + Phone.NORMALIZED_NUMBER + " ELSE " + Phone.NUMBER
                        + " END)";
            }
            else {
                groupBy = "(CASE WHEN " + Phone.NORMALIZED_NUMBER + " IS NOT NULL THEN " + Phone.NORMALIZED_NUMBER + " ELSE "
                        + Phone.NUMBER + " END)";
            }
            if (sortOrder == null) {
                sortOrder = PHONE_FILTER_SORT_ORDER;
            }
            break;
        }
        //
        case EMAILS: {
            setTablesAndProjectionMapForData(qb, projection, false);
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = " + mDbHelper.get().getMimeTypeIdForEmail());

            final boolean removeDuplicates = readBooleanQueryParameter(uri, ScContactsContract.REMOVE_DUPLICATE_ENTRIES, false);
            if (removeDuplicates) {
                groupBy = Data.DATA1;
                addressBookIndexerCountExpression = "DISTINCT " + Data.DATA1;   // See PHONES for more detail.
            }
            break;
        }

        case IM: {
            setTablesAndProjectionMapForData(qb, projection, false);
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = " + mDbHelper.get().getMimeTypeIdForIm());

            final boolean removeDuplicates = readBooleanQueryParameter(uri, ScContactsContract.REMOVE_DUPLICATE_ENTRIES, false);
            if (removeDuplicates) {
                groupBy = Data.DATA1;
                addressBookIndexerCountExpression = "DISTINCT " + Data.DATA1;   // See PHONES for more detail.
            }
            break;
        }

        case EMAILS_ID: {
            setTablesAndProjectionMapForData(qb, projection, false);
            selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                    + mDbHelper.get().getMimeTypeIdForEmail() + " AND "
                    + Data._ID + "=?");
            break;
        }

        case IM_ID: {
            setTablesAndProjectionMapForData(qb, projection, false);
            selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                    + mDbHelper.get().getMimeTypeIdForIm() + " AND "
                    + Data._ID + "=?");
            break;
        }

        case EMAILS_LOOKUP: {
            setTablesAndProjectionMapForData(qb, projection, false);
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = " + mDbHelper.get().getMimeTypeIdForEmail());
            if (uri.getPathSegments().size() > 3) {
                String email = uri.getLastPathSegment();
                String address = mDbHelper.get().extractAddressFromEmailAddress(email);
                selectionArgs = insertSelectionArg(selectionArgs, address);
                qb.appendWhere(" AND UPPER(" + Email.DATA + ")=UPPER(?)");
            }
            break;
        }

        case IM_LOOKUP: {
            setTablesAndProjectionMapForData(qb, projection, false);
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = " + mDbHelper.get().getMimeTypeIdForIm());
            if (uri.getPathSegments().size() > 3) {
                String email = uri.getLastPathSegment();
                String address = mDbHelper.get().extractAddressFromEmailAddress(email);
                selectionArgs = insertSelectionArg(selectionArgs, address);
                qb.appendWhere(" AND UPPER(" + Im.DATA + ")=UPPER(?)");
            }
            break;
        }

        case EMAILS_FILTER: {
            String typeParam = uri.getQueryParameter(DataUsageFeedback.USAGE_TYPE);
            final int typeInt = getDataUsageFeedbackType(typeParam, DataUsageStatColumns.USAGE_TYPE_INT_LONG_TEXT);
            setTablesAndProjectionMapForData(qb, projection, true, typeInt);
            String filterParam = null;

            if (uri.getPathSegments().size() > 3) {
                filterParam = uri.getLastPathSegment();
                if (TextUtils.isEmpty(filterParam)) {
                    filterParam = null;
                }
            }

            if (filterParam == null) {
                // If the filter is unspecified, return nothing
                qb.appendWhere(" AND 0");
            }
            else {
                StringBuilder sb = new StringBuilder();
                sb.append(" AND " + Data._ID + " IN (");
                sb.append(
                        "SELECT " + Data._ID +
                        " FROM " + Tables.DATA +
                        " WHERE " + DataColumns.MIMETYPE_ID + "=");
                sb.append(mDbHelper.get().getMimeTypeIdForEmail());
                sb.append(" AND " + Data.DATA1 + " LIKE ");
                DatabaseUtils.appendEscapedSQLString(sb, filterParam + '%');
                if (!filterParam.contains("@")) {
                    sb.append(
                            " UNION SELECT " + Data._ID +
                            " FROM " + Tables.DATA +
                            " WHERE +" + DataColumns.MIMETYPE_ID + "=");
                    sb.append(mDbHelper.get().getMimeTypeIdForEmail());
                    sb.append(" AND " + Data.RAW_CONTACT_ID + " IN " +
                            "(SELECT " + RawContactsColumns.CONCRETE_ID +
                            " FROM " + Tables.SEARCH_INDEX +
                            " JOIN " + Tables.RAW_CONTACTS +
                            " ON (" + Tables.SEARCH_INDEX + "." + SearchIndexColumns.RAW_CONTACT_ID
                                    + "=" + RawContactsColumns.CONCRETE_ID + ")" +
                                    " WHERE " + SearchIndexColumns.NAME + " MATCH '");
                    final String ftsMatchQuery = SearchIndexManager.getFtsMatchQuery(filterParam,
                            SearchIndexManager.FtsQueryBuilder.UNSCOPED_NORMALIZING);
                    sb.append(ftsMatchQuery);
                    sb.append("')");
                }
                sb.append(")");
                qb.appendWhere(sb);
            }
            groupBy = Email.DATA;
            if (sortOrder == null) {
                sortOrder = EMAIL_FILTER_SORT_ORDER;
            }
            break;
        }

        case POSTALS: {
            setTablesAndProjectionMapForData(qb, projection, false);
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                    + mDbHelper.get().getMimeTypeIdForStructuredPostal());

            final boolean removeDuplicates = readBooleanQueryParameter(uri, ScContactsContract.REMOVE_DUPLICATE_ENTRIES, false);
            if (removeDuplicates) {
                groupBy = Data.DATA1;
                addressBookIndexerCountExpression = "DISTINCT " + Data.DATA1;   // See PHONES for more detail.
            }
            break;
        }

        case POSTALS_ID: {
            setTablesAndProjectionMapForData(qb, projection, false);
            selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
            qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = " + mDbHelper.get().getMimeTypeIdForStructuredPostal());
            qb.appendWhere(" AND " + Data._ID + "=?");
            break;
        }

        case RAW_CONTACTS:
        {
            setTablesAndProjectionMapForRawContacts(qb);
            break;
        }

        case RAW_CONTACTS_ID:
        {
            long rawContactId = ContentUris.parseId(uri);
            setTablesAndProjectionMapForRawContacts(qb);
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
            qb.appendWhere(" AND " + RawContacts._ID + "=?");
            break;
        }

        case RAW_CONTACTS_ID_DATA:
        {
            final int segment = 1;
            long rawContactId = Long.parseLong(uri.getPathSegments().get(segment));
            setTablesAndProjectionMapForData(qb, projection, false);
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
            qb.appendWhere(" AND " + Data.RAW_CONTACT_ID + "=?");
            break;
        }

        case RAW_CONTACTS_ID_STREAM_ITEMS: {
            long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
            setTablesAndProjectionMapForStreamItems(qb);
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
            qb.appendWhere(StreamItems.RAW_CONTACT_ID + "=?");
            break;
        }

        case RAW_CONTACTS_ID_STREAM_ITEMS_ID: {
            long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
            long streamItemId = Long.parseLong(uri.getPathSegments().get(3));
            setTablesAndProjectionMapForStreamItems(qb);
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(streamItemId));
            selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
            qb.appendWhere(StreamItems.RAW_CONTACT_ID + "=? AND " +  StreamItems._ID + "=?");
            break;
        }

        case DATA:
        {
            setTablesAndProjectionMapForData(qb, projection, false);
            break;
        }

        case DATA_ID:
        {
            setTablesAndProjectionMapForData(qb, projection, false);
            selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
            qb.appendWhere(" AND " + Data._ID + "=?");
            break;
        }
        case PHONE_LOOKUP: {
            // Phone lookup cannot be combined with a selection
            selection = null;
            selectionArgs = null;
            if (readBooleanQueryParameter(uri, PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, false)) {
                if (TextUtils.isEmpty(sortOrder)) {
                    // Default the sort order to something reasonable so we get consistent
                    // results when callers don't request an ordering
                    sortOrder = RawContacts.DISPLAY_NAME_PRIMARY + " ASC";
                }

                String sipAddress = uri.getPathSegments().size() > 1 ? Uri.decode(uri.getLastPathSegment()) : "";
                setTablesAndProjectionMapForData(qb, null, false, true);
                StringBuilder sb = new StringBuilder();
                selectionArgs = mDbHelper.get().buildSipContactQuery(sb, sipAddress);
                selection = sb.toString();
            }
            else {
                if (TextUtils.isEmpty(sortOrder)) {
                    // Default the sort order to something reasonable so we get consistent
                    // results when callers don't request an ordering
                    sortOrder = " length(lookup.normalized_number) DESC";
                }

                String number = uri.getPathSegments().size() > 1 ? uri.getLastPathSegment() : "";
                String numberE164 = PhoneNumberHelper.formatNumberToE164(number, mDbHelper.get().getCurrentCountryIso());
                String normalizedNumber = PhoneNumberHelper.normalizeNumber(number);
                mDbHelper.get().buildPhoneLookupAndContactQuery(qb, normalizedNumber, numberE164);
                qb.setProjectionMap(sPhoneLookupProjectionMap);

                // Peek at the results of the first query (which attempts to use fully
                // normalized and internationalized numbers for comparison).  If no results
                // were returned, fall back to using the SQLite function
                // phone_number_compare_loose.
// TODO                        qb.setStrict(true);
                boolean foundResult = false;
                Cursor cursor = query(db, qb, projection, null, null, sortOrder, null, null, limit/*, cancellationSignal*/);
                try {
                    if (cursor.getCount() > 0) {
                        foundResult = true;
                        return cursor;
                    }
                    else {
                        // Use fallback lookup method

                        qb = new SQLiteQueryBuilder();

                        // use the raw number instead of the normalized number because
                        // phone_number_compare_loose in SQLite works only with non-normalized
                        // numbers
                        mDbHelper.get().buildFallbackPhoneLookupAndContactQuery(qb, number);

                        qb.setProjectionMap(sPhoneLookupProjectionMap);
                    }
                } finally {
                    if (!foundResult) {
                        // We'll be returning a different cursor, so close this one.
                        cursor.close();
                    }
                }
            }
            break;
        }

        case GROUPS: {
            qb.setTables(Views.GROUPS);
            qb.setProjectionMap(sGroupsProjectionMap);
            appendAccountIdFromParameter(qb);
            break;
        }

        case GROUPS_ID: {
            qb.setTables(Views.GROUPS);
            qb.setProjectionMap(sGroupsProjectionMap);
            selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
            qb.appendWhere(Groups._ID + "=?");
            break;
        }

        case GROUPS_SUMMARY: {
            String tables = Views.GROUPS + " AS " + Tables.GROUPS;
            if (ScContactsDatabaseHelper.isInProjection(projection, Groups.SUMMARY_COUNT)) {
                tables = tables + Joins.GROUP_MEMBER_COUNT;
            }
            if (ScContactsDatabaseHelper.isInProjection(projection,
                    Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT)) {
                // TODO Add join for this column too (and update the projection map)
                // TODO Also remove Groups.PARAM_RETURN_GROUP_COUNT_PER_ACCOUNT when it works.
                Log.w(TAG, Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT + " is not supported yet");
            }
            qb.setTables(tables);
            qb.setProjectionMap(sGroupsSummaryProjectionMap);
            appendAccountIdFromParameter(qb);
            groupBy = GroupsColumns.CONCRETE_ID;
            break;
        }

//            case SETTINGS: {
//                qb.setTables(Tables.SETTINGS);
//                qb.setProjectionMap(sSettingsProjectionMap);
//                appendAccountFromParameter(qb, uri);
//
//                // When requesting specific columns, this query requires
//                // late-binding of the GroupMembership MIME-type.
//                final String groupMembershipMimetypeId = Long.toString(mDbHelper.get()
//                        .getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE));
//                if (projection != null && projection.length != 0 &&
//                        mDbHelper.get().isInProjection(projection, Settings.UNGROUPED_COUNT)) {
//                    selectionArgs = insertSelectionArg(selectionArgs, groupMembershipMimetypeId);
//                }
//                if (projection != null && projection.length != 0 &&
//                        mDbHelper.get().isInProjection(
//                                projection, Settings.UNGROUPED_WITH_PHONES)) {
//                    selectionArgs = insertSelectionArg(selectionArgs, groupMembershipMimetypeId);
//                }
//
//                break;
//            }
//
//            case STATUS_UPDATES:
//                setTableAndProjectionMapForStatusUpdates(qb, projection);
//                break;
//            }
//
//            case STATUS_UPDATES_ID: {
//                setTableAndProjectionMapForStatusUpdates(qb, projection);
//                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
//                qb.appendWhere(DataColumns.CONCRETE_ID + "=?");
//                break;
//            }
//
            case RAW_CONTACT_ENTITIES: {
                setTablesAndProjectionMapForRawEntities(qb);
                break;
            }

            case RAW_CONTACT_ID_ENTITY: {
                long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
                setTablesAndProjectionMapForRawEntities(qb);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                qb.appendWhere(" AND " + RawContacts._ID + "=?");
                break;
            }

            case PROVIDER_STATUS: {
                return buildSingleRowResult(projection,
                        new String[] {ProviderStatus.STATUS, ProviderStatus.DATA1},
                        new Object[] {mProviderStatus, 0});
            }

            case DIRECTORIES : {
                qb.setTables(Tables.DIRECTORIES);
                qb.setProjectionMap(sDirectoryProjectionMap);
                break;
            }

            case DIRECTORIES_ID : {
                long id = ContentUris.parseId(uri);
                qb.setTables(Tables.DIRECTORIES);
                qb.setProjectionMap(sDirectoryProjectionMap);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(id));
                qb.appendWhere(Directory._ID + "=?");
                break;
            }

            case COMPLETE_NAME: {
                return completeName(uri, projection);
            }

            case DELETED_CONTACTS: {
                qb.setTables(Tables.DELETED_CONTACTS);
                qb.setProjectionMap(sDeletedContactsProjectionMap);
                break;
            }

            case DELETED_CONTACTS_ID: {
                String id = uri.getLastPathSegment();
                qb.setTables(Tables.DELETED_CONTACTS);
                qb.setProjectionMap(sDeletedContactsProjectionMap);
                qb.appendWhere(DeletedContacts.CONTACT_ID + "=?");
                selectionArgs = insertSelectionArg(selectionArgs, id);
                break;
            }

            default:
                throw new UnsupportedOperationException(mDbHelper.get().exceptionMessage(uri));
        }

        // Auto-rewrite SORT_KEY_{PRIMARY, ALTERNATIVE} sort orders.
        String localizedSortOrder = getLocalizedSortOrder(sortOrder);
        Cursor cursor = query(db, qb, projection, selection, selectionArgs, localizedSortOrder, groupBy, null, limit/*, cancellationSignal*/);

        if (readBooleanQueryParameter(uri, ScContactsContract.ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, false)) {
            bundleFastScrollingIndexExtras(cursor, uri, db, qb, selection, selectionArgs, sortOrder, addressBookIndexerCountExpression);
        }
        if (snippetDeferred) {
            cursor = addDeferredSnippetingExtra(cursor);
        }

        return cursor;
    }

    private boolean returnOnBlocking(Uri uri) {
        boolean nonBlock = readBooleanQueryParameter(uri, ScContactsContract.NON_BLOCKING, false);
        boolean dbReady = getDatabaseHelper().isReady();
        return !dbReady && nonBlock;
    }

    // Rewrites query sort orders using SORT_KEY_{PRIMARY, ALTERNATIVE}
    // to use PHONEBOOK_BUCKET_{PRIMARY, ALTERNATIVE} as primary key; all
    // other sort orders are returned unchanged. Preserves ordering
    // (eg 'DESC') if present.
    protected static String getLocalizedSortOrder(String sortOrder) {
        String localizedSortOrder = sortOrder;
        if (sortOrder != null) {
            String sortKey;
            String sortOrderSuffix = "";
            int spaceIndex = sortOrder.indexOf(' ');
            if (spaceIndex != -1) {
                sortKey = sortOrder.substring(0, spaceIndex);
                sortOrderSuffix = sortOrder.substring(spaceIndex);
            } else {
                sortKey = sortOrder;
            }
            if (TextUtils.equals(sortKey, RawContacts.SORT_KEY_PRIMARY)) {
                localizedSortOrder = RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY
                        + sortOrderSuffix + ", " + sortOrder;
            } else if (TextUtils.equals(sortKey, RawContacts.SORT_KEY_ALTERNATIVE)) {
                localizedSortOrder = RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE
                        + sortOrderSuffix + ", " + sortOrder;
            }
        }
        return localizedSortOrder;
    }

    private static Bundle dirtyTrickBundle;
    /**
     * Add the "fast scrolling index" bundle, generated by {@link #getFastScrollingIndexExtras43},
     * to a cursor as extras.  It first checks {@link FastScrollingIndexCache} to see if we
     * already have a cached result.
     */
    private void bundleFastScrollingIndexExtras(Cursor cursor, Uri queryUri, final SQLiteDatabase db, SQLiteQueryBuilder qb,
            String selection, String[] selectionArgs, String sortOrder, String countExpression) {

        Bundle b;
        // Note even though FastScrollingIndexCache is thread-safe, we really need to put the
        // put-get pair in a single synchronized block, so that even if multiple-threads request the
        // same index at the same time (which actually happens on the phone app) we only execute
        // the query once.
        //
        // This doesn't cause deadlock, because only reader threads get here but not writer
        // threads.  (Writer threads may call invalidateFastScrollingIndexCache(), but it doesn't
        // synchronize on mFastScrollingIndexCache)
        //
        // All reader and writer threads share the single lock object internally in
        // FastScrollingIndexCache, but the lock scope is limited within each put(), get() and
        // invalidate() call, so it won't deadlock.

        // Synchronizing on a non-static field is generally not a good idea, but nobody should
        // modify mFastScrollingIndexCache once initialized, and it shouldn't be null at this point.
        synchronized (mFastScrollingIndexCache) {
            // First, try the cache.
            b = mFastScrollingIndexCache.get(queryUri, selection, selectionArgs, sortOrder, countExpression);

            if (b == null) {
                // Not in the cache. Generate and put.
                final long start = System.currentTimeMillis();

                b = getFastScrollingIndexExtras43(db, qb, selection, selectionArgs, sortOrder, countExpression);

                final long end = System.currentTimeMillis();
                final int time = (int) (end - start);
                if (VERBOSE_DEBUG) {
                    Log.v(TAG, "getLetterCountExtraBundle took " + time + "ms");
                }
                mFastScrollingIndexCache.put(queryUri, selection, selectionArgs, sortOrder, countExpression, b);
            }
        }
        if (!(cursor instanceof AbstractCursor)) {
            dirtyTrickBundle = b;
            return;
        }
        // setExtras() is hidden in AbstractCursor, do a lookup, make it accessible and use it
        Class<?> c = ((Object)cursor).getClass();  // overcome some issues in AS (IntelliJ)
        try {
            java.lang.reflect.Method m = c.getMethod("setExtras", Bundle.class);
            m.setAccessible(true);
            m.invoke(cursor, b);
        } catch (Exception e) {
            Log.i(TAG, "AbstractCursor setExtra - bundleFastScrollingIndexExtras: " + e);
        }
    }

    private static final class AddressBookIndexQuery43 {
        public static final String NAME = "name";
        public static final String BUCKET = "bucket";
        public static final String LABEL = "label";
        public static final String COUNT = "count";

        public static final String[] COLUMNS = new String[] {
                NAME, BUCKET, LABEL, COUNT
        };

        public static final int COLUMN_LABEL = 2;
        public static final int COLUMN_COUNT = 3;

        public static final String GROUP_BY = BUCKET + ", " + LABEL;
        public static final String ORDER_BY =
                BUCKET + ", " +  NAME + " COLLATE " + PHONEBOOK_COLLATOR_NAME;
    }

    /**
     * Computes counts by the address book index labels and returns it as {@link android.os.Bundle} which
     * will be appended to a {@link net.sqlcipher.Cursor} as extras.
     *
     * This function does not use the Android specific SQLite extension GET_PHONEBOOK_INDEX(...) anymore.
     * Instead it uses some new fields of the RawContact that hold the primary and secondary labels and
     * counts. Refer to the database helper class. This was copied from Jelly Bean 4.3 Contact application.
     */
    private static Bundle getFastScrollingIndexExtras43(final SQLiteDatabase db,
                                                      final SQLiteQueryBuilder qb, final String selection, final String[] selectionArgs,
                                                      final String sortOrder, String countExpression) {
        String sortKey;

        // The sort order suffix could be something like "DESC".
        // We want to preserve it in the query even though we will change
        // the sort column itself.
        String sortOrderSuffix = "";
        if (sortOrder != null) {
            int spaceIndex = sortOrder.indexOf(' ');
            if (spaceIndex != -1) {
                sortKey = sortOrder.substring(0, spaceIndex);
                sortOrderSuffix = sortOrder.substring(spaceIndex);
            } else {
                sortKey = sortOrder;
            }
        } else {
            sortKey = RawContacts.SORT_KEY_PRIMARY;
        }

        String bucketKey;
        String labelKey;
        if (TextUtils.equals(sortKey, RawContacts.SORT_KEY_PRIMARY)) {
            bucketKey = RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY;
            labelKey = RawContactsColumns.PHONEBOOK_LABEL_PRIMARY;
        } else if (TextUtils.equals(sortKey, RawContacts.SORT_KEY_ALTERNATIVE)) {
            bucketKey = RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE;
            labelKey = RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE;
        } else {
            return null;
        }

        HashMap<String, String> projectionMap = Maps.newHashMap();
        projectionMap.put(AddressBookIndexQuery43.NAME,
                sortKey + " AS " + AddressBookIndexQuery43.NAME);
        projectionMap.put(AddressBookIndexQuery43.BUCKET,
                bucketKey + " AS " + AddressBookIndexQuery43.BUCKET);
        projectionMap.put(AddressBookIndexQuery43.LABEL,
                labelKey + " AS " + AddressBookIndexQuery43.LABEL);

        // If "what to count" is not specified, we just count all records.
        if (TextUtils.isEmpty(countExpression)) {
            countExpression = "*";
        }

        projectionMap.put(AddressBookIndexQuery43.COUNT,
                "COUNT(" + countExpression + ") AS " + AddressBookIndexQuery43.COUNT);
        qb.setProjectionMap(projectionMap);
        String orderBy = AddressBookIndexQuery43.BUCKET + sortOrderSuffix
                + ", " + AddressBookIndexQuery43.NAME + " COLLATE "
                + PHONEBOOK_COLLATOR_NAME + sortOrderSuffix;

        Cursor indexCursor = qb.query(db, AddressBookIndexQuery43.COLUMNS, selection, selectionArgs,
                AddressBookIndexQuery43.GROUP_BY, null /* having */,
                orderBy, null);

        try {
            int numLabels = indexCursor.getCount();
            String labels[] = new String[numLabels];
            int counts[] = new int[numLabels];

            for (int i = 0; i < numLabels; i++) {
                indexCursor.moveToNext();
                labels[i] = indexCursor.getString(AddressBookIndexQuery43.COLUMN_LABEL);
                counts[i] = indexCursor.getInt(AddressBookIndexQuery43.COLUMN_COUNT);
            }

            return FastScrollingIndexCache.buildExtraBundle(labels, counts);
        } finally {
            indexCursor.close();
        }
    }

    private Cursor query(final SQLiteDatabase db, SQLiteQueryBuilder qb, String[] projection,
            String selection, String[] selectionArgs, String sortOrder, String groupBy,
            String having, String limit /*, CancellationSignal cancellationSignal*/) {

        if (projection != null && projection.length == 1
                && ScBaseColumns._COUNT.equals(projection[0])) {
            qb.setProjectionMap(sCountProjectionMap);
        }
        final Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, having,
                sortOrder, limit/*, cancellationSignal*/);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), ScContactsContract.AUTHORITY_URI);
        }
        return c;
    }

    private void setTablesAndProjectionMapForRawContacts(SQLiteQueryBuilder qb) {
        qb.setTables(Views.RAW_CONTACTS);
        qb.setProjectionMap(sRawContactsProjectionMap);
        appendAccountIdFromParameter(qb);
    }

    private void setTablesAndProjectionMapForRawContactsStrequent(SQLiteQueryBuilder qb, boolean stats) {
        StringBuilder sb = new StringBuilder();
        if (stats) {
            sb.append(Views.DATA_USAGE_STAT + " AS " + Tables.DATA_USAGE_STAT + ", ");
//            sb.append(" INNER JOIN ");
            sb.append(Views.RAW_CONTACTS + " AS raw_join_view ");
        }
        else
            sb.append(Views.RAW_CONTACTS);
        // Just for frequently contacted contacts in Strequent Uri handling.
//        if (stats) {
//            sb.append(" ON (" + DataUsageStatColumns.CONCRETE_TIMES_USED + " > 0" + ")");
//            sb.append(Tables.DATA_USAGE_STAT
//                    + " INNER JOIN " + Views.DATA + " " + Tables.DATA
//                    + " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "=" + DataColumns.CONCRETE_ID + " AND "
//                    + "raw_join_view._id =" + DataColumns.CONCRETE_RAW_CONTACT_ID + ")");
//       }

        qb.setTables(sb.toString());
        qb.setProjectionMap(sRawContactsProjectionMap);
    }

    private void setTablesAndProjectionMapForRawEntities(SQLiteQueryBuilder qb) {
        StringBuilder sb = new StringBuilder();
        sb.append(Views.RAW_ENTITIES);

        // Only support USAGE_TYPE_ALL for now. Can add finer grain if needed in the future.
        appendDataUsageStatJoin(sb, USAGE_TYPE_ALL, RawContacts.Entity.DATA_ID);
        qb.setTables(sb.toString());

        qb.setProjectionMap(sRawEntityProjectionMap);
        appendAccountIdFromParameter(qb);
    }

    /**
     * Finds name lookup records matching the supplied filter, picks one arbitrary match per
     * contact and joins that with other contacts tables.
     */
    private void setTablesAndProjectionMapForContactsWithSnippet(SQLiteQueryBuilder qb, Uri uri,
            String[] projection, String filter, long directoryId, boolean deferSnippeting) {

        StringBuilder sb = new StringBuilder();
        sb.append(Views.RAW_CONTACTS);

        if (filter != null) {
            filter = filter.trim();
        }

        Log.d(TAG, "*** SNIPPET: filter: " + filter + ", defer: " + deferSnippeting);
        if (TextUtils.isEmpty(filter) || (directoryId != -1 && directoryId != Directory.DEFAULT)) {
            sb.append(" JOIN (SELECT NULL AS " + SearchSnippetColumns.SNIPPET + " WHERE 0)");
        } else {
            appendSearchIndexJoin(sb, uri, projection, filter, deferSnippeting);
        }
//        appendContactStatusUpdateJoin(sb, projection, ContactsColumns.LAST_STATUS_UPDATE_ID);
        Log.d(TAG, "**** SNIPPET string: " + sb.toString());
        qb.setTables(sb.toString());
        qb.setProjectionMap(sContactsProjectionWithSnippetMap);
    }

    private void appendSearchIndexJoin(StringBuilder sb, Uri uri, String[] projection, String filter, boolean deferSnippeting) {

        if (snippetNeeded(projection)) {
            String[] args = null;
            String snippetArgs = getQueryParameter(uri, SearchSnippetColumns.SNIPPET_ARGS_PARAM_KEY);
            if (snippetArgs != null) {
                args = snippetArgs.split(",");
            }

            String startMatch = args != null && args.length > 0 ? args[0] : DEFAULT_SNIPPET_ARG_START_MATCH;
            String endMatch = args != null && args.length > 1 ? args[1]   : DEFAULT_SNIPPET_ARG_END_MATCH;
            String ellipsis = args != null && args.length > 2 ? args[2]   : DEFAULT_SNIPPET_ARG_ELLIPSIS;
            int maxTokens = args != null && args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_SNIPPET_ARG_MAX_TOKENS;

            appendSearchIndexJoin(sb, filter, true, startMatch, endMatch, ellipsis, maxTokens, deferSnippeting);
        } else {
            appendSearchIndexJoin(sb, filter, false, null, null, null, 0, false);
        }
    }

    public void appendSearchIndexJoin(StringBuilder sb, String filter, boolean snippetNeeded, String startMatch,
            String endMatch, String ellipsis, int maxTokens, boolean deferSnippeting) {

        boolean isEmailAddress = false;
        String emailAddress = null;
        boolean isPhoneNumber = false;
        String phoneNumber = null;
        String numberE164 = null;


        if (filter.indexOf('@') != -1) {
            emailAddress = mDbHelper.get().extractAddressFromEmailAddress(filter);
            isEmailAddress = !TextUtils.isEmpty(emailAddress);
        } else {
            isPhoneNumber = isPhoneNumber(filter);
            if (isPhoneNumber) {
                phoneNumber = PhoneNumberHelper.normalizeNumber(filter);
                numberE164 = PhoneNumberHelper.formatNumberToE164(phoneNumber,
                        mDbHelper.get().getCurrentCountryIso());
            }
        }

        final String SNIPPET_CONTACT_ID = "snippet_contact_id";
        sb.append(" JOIN (SELECT " + SearchIndexColumns.RAW_CONTACT_ID + " AS " + SNIPPET_CONTACT_ID);
        if (snippetNeeded) {
            sb.append(", ");
            if (isEmailAddress) {
                sb.append("ifnull(");
                if (!deferSnippeting) {
                    // Add the snippet marker only when we're really creating snippet.
                    DatabaseUtils.appendEscapedSQLString(sb, startMatch);
                    sb.append("||");
                }
                sb.append("(SELECT MIN(" + Email.ADDRESS + ")");
                sb.append(" FROM " + Tables.DATA_JOIN_RAW_CONTACTS);
                sb.append(" WHERE  " + Tables.SEARCH_INDEX + "." + SearchIndexColumns.RAW_CONTACT_ID);
                sb.append("=" + RawContacts._ID + " AND " + Email.ADDRESS + " LIKE ");
                DatabaseUtils.appendEscapedSQLString(sb, filter + "%");
                sb.append(")");
                if (!deferSnippeting) {
                    sb.append("||");
                    DatabaseUtils.appendEscapedSQLString(sb, endMatch);
                }
                sb.append(",");

                if (deferSnippeting) {
                    sb.append(SearchIndexColumns.CONTENT);
                } else {
                    appendSnippetFunction(sb, startMatch, endMatch, ellipsis, maxTokens);
                }
                sb.append(")");
            } else if (isPhoneNumber) {
                sb.append("ifnull(");
                if (!deferSnippeting) {
                    // Add the snippet marker only when we're really creating snippet.
                    DatabaseUtils.appendEscapedSQLString(sb, startMatch);
                    sb.append("||");
                }
                sb.append("(SELECT MIN(" + Phone.NUMBER + ")");
                sb.append(" FROM " +
                        Tables.DATA_JOIN_RAW_CONTACTS + " JOIN " + Tables.PHONE_LOOKUP);
                sb.append(" ON " + DataColumns.CONCRETE_ID);
                sb.append("=" + Tables.PHONE_LOOKUP + "." + PhoneLookupColumns.DATA_ID);
                sb.append(" WHERE  " + Tables.SEARCH_INDEX + "." + SearchIndexColumns.RAW_CONTACT_ID);
                sb.append("=" + RawContacts._ID);
                sb.append(" AND " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                sb.append(phoneNumber);
                sb.append("%'");
                if (!TextUtils.isEmpty(numberE164)) {
                    sb.append(" OR " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                    sb.append(numberE164);
                    sb.append("%'");
                }
                sb.append(")");
                if (! deferSnippeting) {
                    sb.append("||");
                    DatabaseUtils.appendEscapedSQLString(sb, endMatch);
                }
                sb.append(",");

                if (deferSnippeting) {
                    sb.append(SearchIndexColumns.CONTENT);
                } else {
                    appendSnippetFunction(sb, startMatch, endMatch, ellipsis, maxTokens);
                }
                sb.append(")");
            } else {
                final String normalizedFilter = NameNormalizer.normalize(filter);
                if (!TextUtils.isEmpty(normalizedFilter)) {
                    if (deferSnippeting) {
                        sb.append(SearchIndexColumns.CONTENT);
                    } else {
                        sb.append("(CASE WHEN EXISTS (SELECT 1 FROM ");
                        sb.append(Tables.RAW_CONTACTS + " AS rc INNER JOIN ");
                        sb.append(Tables.NAME_LOOKUP + " AS nl ON (rc." + RawContacts._ID);
                        sb.append("=nl." + NameLookupColumns.RAW_CONTACT_ID);
                        sb.append(") WHERE nl." + NameLookupColumns.NORMALIZED_NAME);
                        sb.append(" GLOB '").append(normalizedFilter).append("*' AND ");
                        sb.append("nl." + NameLookupColumns.NAME_TYPE + "=");
                        sb.append(NameLookupType.NAME_COLLATION_KEY + " AND ");
                        sb.append(Tables.SEARCH_INDEX + "." + SearchIndexColumns.RAW_CONTACT_ID);
                        sb.append("=rc." + RawContacts._ID);
                        sb.append(") THEN NULL ELSE ");
                        appendSnippetFunction(sb, startMatch, endMatch, ellipsis, maxTokens);
                        sb.append(" END)");
                    }
                } else {
                    sb.append("NULL");
                }
            }
            sb.append(" AS " + SearchSnippetColumns.SNIPPET);
        }

        sb.append(" FROM " + Tables.SEARCH_INDEX);
        sb.append(" WHERE ");
        sb.append(Tables.SEARCH_INDEX + " MATCH '");
        if (isEmailAddress) {
            // we know that the emailAddress contains a @. This phrase search should be
            // scoped against "content:" only, but unfortunately SQLite doesn't support
            // phrases and scoped columns at once. This is fine in this case however, because:
            //  - We can't erronously match against name, as name is all-hex (so the @ can't match)
            //  - We can't match against tokens, because phone-numbers can't contain @
            final String sanitizedEmailAddress = sanitizeMatch(emailAddress);
            sb.append("\"");
            sb.append(sanitizedEmailAddress);
            sb.append("*\"");
        } else if (isPhoneNumber) {
            // normalized version of the phone number (phoneNumber can only have + and digits)
            final String phoneNumberCriteria = " OR tokens:" + phoneNumber + "*";

            // international version of this number (numberE164 can only have + and digits)
            final String numberE164Criteria =
                    (numberE164 != null && !TextUtils.equals(numberE164, phoneNumber))
                    ? " OR tokens:" + numberE164 + "*"
                    : "";

            // combine all criteria
            final String commonCriteria =
                    phoneNumberCriteria + numberE164Criteria;

            // search in content
            sb.append(SearchIndexManager.getFtsMatchQuery(filter,
                    SearchIndexManager.FtsQueryBuilder.getDigitsQueryBuilder(commonCriteria)));
        } else {
            // general case: not a phone number, not an email-address
            sb.append(SearchIndexManager.getFtsMatchQuery(filter, SearchIndexManager.FtsQueryBuilder.SCOPED_NAME_NORMALIZING));
//            sb.append(SearchIndexManager.getFtsMatchQuery(filter, FtsQueryBuilder.UNSCOPED_NORMALIZING));
        }
        // Omit results in "Other Contacts".
        sb.append("')");
//        sb.append("' AND " + SNIPPET_CONTACT_ID + " IN " + Tables.DEFAULT_DIRECTORY + ")");
        sb.append(" ON (" + RawContacts._ID + "=" + SNIPPET_CONTACT_ID + ")");
    }

    private static String sanitizeMatch(String filter) {
        return filter.replace("'", "").replace("*", "").replace("-", "").replace("\"", "");
    }

    private void appendSnippetFunction(StringBuilder sb, String startMatch, String endMatch, String ellipsis, int maxTokens) {
        sb.append("snippet(" + Tables.SEARCH_INDEX + ",");
        DatabaseUtils.appendEscapedSQLString(sb, startMatch);
        sb.append(",");
        DatabaseUtils.appendEscapedSQLString(sb, endMatch);
        sb.append(",");
        DatabaseUtils.appendEscapedSQLString(sb, ellipsis);

        // The index of the column used for the snippet, "content"
        sb.append(",1,");
        sb.append(maxTokens);
        sb.append(")");
    }

    /**
     * Takes components of a name from the query parameters and returns a cursor with those
     * components as well as all missing components.  There is no database activity involved
     * in this so the call can be made on the UI thread.
     */
    private Cursor completeName(Uri uri, String[] projection) {
        if (projection == null) {
            projection = sDataProjectionMap.getColumnNames();
        }

        ContentValues values = new ContentValues();
        DataRowHandlerForStructuredName handler = (DataRowHandlerForStructuredName)
                getDataRowHandler(StructuredName.CONTENT_ITEM_TYPE);

        copyQueryParamsToContentValues(values, uri,
                StructuredName.DISPLAY_NAME,
                StructuredName.PREFIX,
                StructuredName.GIVEN_NAME,
                StructuredName.MIDDLE_NAME,
                StructuredName.FAMILY_NAME,
                StructuredName.SUFFIX,
                StructuredName.PHONETIC_NAME,
                StructuredName.PHONETIC_FAMILY_NAME,
                StructuredName.PHONETIC_MIDDLE_NAME,
                StructuredName.PHONETIC_GIVEN_NAME
        );

        handler.fixStructuredNameComponents(values, values);

        MatrixCursor cursor = new MatrixCursor(projection);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            row[i] = values.get(projection[i]);
        }
        cursor.addRow(row);
        return cursor;
    }

    private void copyQueryParamsToContentValues(ContentValues values, Uri uri, String... columns) {
        for (String column : columns) {
            String param = uri.getQueryParameter(column);
            if (param != null) {
                values.put(column, param);
            }
        }
    }

    private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb, String[] projection, boolean distinct) {
        setTablesAndProjectionMapForData(qb, projection, distinct, false, null);
    }

    private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb,
            String[] projection, boolean distinct, boolean addSipLookupColumns) {
        setTablesAndProjectionMapForData(qb, projection, distinct, addSipLookupColumns, null);
    }

    /**
     * @param usageType when non-null {@link Tables} is joined with the specified
     * type.
     */
    private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb,
            String[] projection, boolean distinct, Integer usageType) {
        setTablesAndProjectionMapForData(qb, projection, distinct, false, usageType);
    }

    private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb,
            String[] projection, boolean distinct, boolean addSipLookupColumns, Integer usageType) {

        StringBuilder sb = new StringBuilder();
        sb.append(Views.DATA);
        sb.append(" data");

//        appendContactPresenceJoin(sb, projection, RawContacts.CONTACT_ID);
//        appendContactStatusUpdateJoin(sb, projection, ContactsColumns.LAST_STATUS_UPDATE_ID);
//        appendDataPresenceJoin(sb, projection, DataColumns.CONCRETE_ID);
//        appendDataStatusUpdateJoin(sb, projection, DataColumns.CONCRETE_ID);
//
        appendDataUsageStatJoin(sb, usageType == null ? USAGE_TYPE_ALL : usageType, DataColumns.CONCRETE_ID);

        qb.setTables(sb.toString());

        boolean useDistinct = distinct || !ScContactsDatabaseHelper.isInProjection(projection, DISTINCT_DATA_PROHIBITING_COLUMNS);
        qb.setDistinct(useDistinct);

        final ProjectionMap projectionMap;
        if (addSipLookupColumns) {
            projectionMap = useDistinct ? sDistinctDataSipLookupProjectionMap : sDataSipLookupProjectionMap;
        } else {
            projectionMap = useDistinct ? sDistinctDataProjectionMap : sDataProjectionMap;
        }

        qb.setProjectionMap(projectionMap);
        appendAccountIdFromParameter(qb);
    }

    private void setTablesAndProjectionMapForStreamItems(SQLiteQueryBuilder qb) {
        qb.setTables(Views.STREAM_ITEMS);
        qb.setProjectionMap(sStreamItemsProjectionMap);
    }

    private void setTablesAndProjectionMapForStreamItemPhotos(SQLiteQueryBuilder qb) {
        qb.setTables(Tables.PHOTO_FILES
                + " JOIN " + Tables.STREAM_ITEM_PHOTOS + " ON ("
                + StreamItemPhotosColumns.CONCRETE_PHOTO_FILE_ID + "="
                + PhotoFilesColumns.CONCRETE_ID
                + ") JOIN " + Tables.STREAM_ITEMS + " ON ("
                + StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "="
                + StreamItemsColumns.CONCRETE_ID + ")"
                + " JOIN " + Tables.RAW_CONTACTS + " ON ("
                + StreamItemsColumns.CONCRETE_RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID
                + ")");
        qb.setProjectionMap(sStreamItemPhotosProjectionMap);
    }

    private void appendAccountIdFromParameter(SQLiteQueryBuilder qb) {
        qb.appendWhere("1");
    }

    private void appendDataUsageStatJoin(StringBuilder sb, int usageType, String dataIdColumn) {
        if (usageType != USAGE_TYPE_ALL) {
            sb.append(" LEFT OUTER JOIN " + Tables.DATA_USAGE_STAT +
                    " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "=");
            sb.append(dataIdColumn);
            sb.append(" AND " + DataUsageStatColumns.CONCRETE_USAGE_TYPE + "=");
            sb.append(usageType);
            sb.append(")");
        } else {
            sb.append(
                    " LEFT OUTER JOIN " +
                            "(SELECT " +
                            DataUsageStatColumns.CONCRETE_DATA_ID + " as STAT_DATA_ID, " +
                            "SUM(" + DataUsageStatColumns.CONCRETE_TIMES_USED +
                            ") as " + DataUsageStatColumns.TIMES_USED + ", " +
                            "MAX(" + DataUsageStatColumns.CONCRETE_LAST_TIME_USED +
                            ") as " + DataUsageStatColumns.LAST_TIME_USED +
                            " FROM " + Tables.DATA_USAGE_STAT + " GROUP BY " +
                            DataUsageStatColumns.CONCRETE_DATA_ID + ") as " + Tables.DATA_USAGE_STAT
            );
            sb.append(" ON (STAT_DATA_ID=");
            sb.append(dataIdColumn);
            sb.append(")");
        }
    }

    @Override
    public String getType(Uri uri) {
        final int match = sUriMatcher.match(uri);
        if (DEBUG) {
            Log.d(TAG, "getType: uri=" + uri +", match: " + match);
        }
        switch (match) {

            case RAW_CONTACTS_AS_VCARD:
            case RAW_CONTACTS_AS_MULTI_VCARD:
                return RawContacts.CONTENT_VCARD_TYPE;

            case RAW_CONTACTS_ID_DISPLAY_PHOTO:
            case DISPLAY_PHOTO_ID:
                return "image/jpeg";
            case RAW_CONTACTS:
                return RawContacts.CONTENT_TYPE;
            case RAW_CONTACTS_ID:
                return RawContacts.CONTENT_ITEM_TYPE;
            case DATA:
                return Data.CONTENT_TYPE;
            case DATA_ID:
                // We need db access for this.
                waitForAccess(mReadAccessLatch);
                long id = ContentUris.parseId(uri);
                return mContactsHelper.getDataMimeType(id);
            case PHONES:
                return Phone.CONTENT_TYPE;
            case PHONES_ID:
                return Phone.CONTENT_ITEM_TYPE;
            case PHONE_LOOKUP:
                return PhoneLookup.CONTENT_TYPE;
            case EMAILS:
                return Email.CONTENT_TYPE;
            case EMAILS_ID:
                return Email.CONTENT_ITEM_TYPE;
            case POSTALS:
                return StructuredPostal.CONTENT_TYPE;
            case POSTALS_ID:
                return StructuredPostal.CONTENT_ITEM_TYPE;
//            case SETTINGS:
//                return Settings.CONTENT_TYPE;
//            case AGGREGATION_SUGGESTIONS:
//                return Contacts.CONTENT_TYPE;
            case DIRECTORIES:
                return Directory.CONTENT_TYPE;
            case DIRECTORIES_ID:
                return Directory.CONTENT_ITEM_TYPE;
            case STREAM_ITEMS:
                return StreamItems.CONTENT_TYPE;
            case STREAM_ITEMS_ID:
                return StreamItems.CONTENT_ITEM_TYPE;
            case STREAM_ITEMS_ID_PHOTOS:
                return StreamItems.StreamItemPhotos.CONTENT_TYPE;
            case STREAM_ITEMS_ID_PHOTOS_ID:
                return StreamItems.StreamItemPhotos.CONTENT_ITEM_TYPE;
            case STREAM_ITEMS_PHOTOS:
                throw new UnsupportedOperationException("Not supported for write-only URI " + uri);
            default:
                throw new UnsupportedOperationException(mDbHelper.get().exceptionMessage(uri));
        }
    }

    /**
     * Inserts an argument at the beginning of the selection arg list.
     */
    private String[] insertSelectionArg(String[] selectionArgs, String arg) {
        if (selectionArgs == null) {
            return new String[] {arg};
        } else {
            int newLength = selectionArgs.length + 1;
            String[] newSelectionArgs = new String[newLength];
            newSelectionArgs[0] = arg;
            System.arraycopy(selectionArgs, 0, newSelectionArgs, 1, selectionArgs.length);
            return newSelectionArgs;
        }
    }

    /**
     * A fast re-implementation of {@link android.net.Uri#getQueryParameter}
     */
    static String getQueryParameter(Uri uri, String parameter) {
        String query = uri.getEncodedQuery();
        if (query == null) {
            return null;
        }

        int queryLength = query.length();
        int parameterLength = parameter.length();

        String value;
        int index = 0;
        while (true) {
            index = query.indexOf(parameter, index);
            if (index == -1) {
                return null;
            }

            // Should match against the whole parameter instead of its suffix.
            // e.g. The parameter "param" must not be found in "some_param=val".
            if (index > 0) {
                char prevChar = query.charAt(index - 1);
                if (prevChar != '?' && prevChar != '&') {
                    // With "some_param=val1&param=val2", we should find second "param" occurrence.
                    index += parameterLength;
                    continue;
                }
            }

            index += parameterLength;

            if (queryLength == index) {
                return null;
            }

            if (query.charAt(index) == '=') {
                index++;
                break;
            }
        }

        int ampIndex = query.indexOf('&', index);
        if (ampIndex == -1) {
            value = query.substring(index);
        } else {
            value = query.substring(index, ampIndex);
        }

        return Uri.decode(value);
    }

    private class StructuredNameLookupBuilder extends NameLookupBuilder {

        public StructuredNameLookupBuilder(NameSplitter splitter) {
            super(splitter);
        }

        @Override
        protected void insertNameLookup(long rawContactId, long dataId, int lookupType, String name) {
            mDbHelper.get().insertNameLookup(rawContactId, dataId, lookupType, name);
        }

        @Override
        protected String[] getCommonNicknameClusters(String normalizedName) {
            return null; // TODO mCommonNicknameCache.getCommonNicknameClusters(normalizedName);
        }
    }

    public void appendContactFilterAsNestedQuery(StringBuilder sb, String filterParam, String rawIdName) {
        final String rawId = rawIdName == null ? RawContacts._ID : rawIdName;
        sb.append("(" +
                "SELECT DISTINCT " + RawContacts._ID +
                " FROM " + Tables.RAW_CONTACTS +
                " JOIN " + Tables.NAME_LOOKUP +
                " ON(" + RawContactsColumns.CONCRETE_ID + "="
                + NameLookupColumns.RAW_CONTACT_ID + ")" +
                " WHERE normalized_name GLOB '");
        sb.append(NameNormalizer.normalize(filterParam));
        sb.append("*' AND " + NameLookupColumns.NAME_TYPE +
                " IN(" + CONTACT_LOOKUP_NAME_TYPES + "))");
    }

    static boolean readBooleanQueryParameter(Uri uri, String parameter, boolean defaultValue) {

        // Manually parse the query, which is much faster than calling uri.getQueryParameter
        String query = uri.getEncodedQuery();
        if (query == null) {
            return defaultValue;
        }

        int index = query.indexOf(parameter);
        if (index == -1) {
            return defaultValue;
        }

        index += parameter.length();

        return !matchQueryParameter(query, index, "=0", false)
                && !matchQueryParameter(query, index, "=false", true);
    }

    public boolean isPhoneNumber(String query) {
        if (TextUtils.isEmpty(query)) {
            return false;
        }
        // assume a phone number if it has at least 1 digit
        return countPhoneNumberDigits(query) > 0;
    }

    /**
     * Returns the number of digitis in a phone number ignoring special characters such as '-'.
     * If the string is not a valid phone number, 0 is returned.
     */
    public static int countPhoneNumberDigits(String query) {
        int numDigits = 0;
        int len = query.length();
        for (int i = 0; i < len; i++) {
            char c = query.charAt(i);
            if (Character.isDigit(c)) {
                numDigits ++;
            } else if (c == '*' || c == '#' || c == 'N' || c == '.' || c == ';'
                    || c == '-' || c == '(' || c == ')' || c == ' ') {
                // carry on
            } else if (c == '+' && numDigits == 0) {
                // plus before any digits is ok
            } else {
                return 0; // not a phone number
            }
        }
        return numDigits;
    }

    public boolean isPhone() {
        return true;
    }

    /**
     * Gets the value of the "limit" URI query parameter.
     *
     * @return A string containing a non-negative integer, or <code>null</code> if
     *         the parameter is not set, or is set to an invalid value.
     */
    private String getLimit(Uri uri) {
        String limitParam = getQueryParameter(uri, ScContactsContract.LIMIT_PARAM_KEY);
        if (limitParam == null) {
            return null;
        }
        // make sure that the limit is a non-negative integer
        try {
            int l = Integer.parseInt(limitParam);
            if (l < 0) {
                Log.w(TAG, "Invalid limit parameter: " + limitParam);
                return null;
            }
            return String.valueOf(l);
        } catch (NumberFormatException ex) {
            Log.w(TAG, "Invalid limit parameter: " + limitParam);
            return null;
        }
    }


    private static boolean matchQueryParameter(String query, int index, String value, boolean ignoreCase) {
        int length = value.length();
        return query.regionMatches(ignoreCase, index, value, 0, length)
                && (query.length() == index + length || query.charAt(index + length) == '&');
    }

    /**
     * Checks the URI for a deferred snippeting request
     * @return a boolean indicating if a deferred snippeting request is in the RI
     */
    private boolean deferredSnippetingRequested(Uri uri) {
        String deferredSnippeting =
            getQueryParameter(uri, SearchSnippetColumns.DEFERRED_SNIPPETING_KEY);
        return !TextUtils.isEmpty(deferredSnippeting) &&  deferredSnippeting.equals("1");
    }

    /**
     * Checks if query is a single word or not.
     * @return a boolean indicating if the query is one word or not
     */
    private boolean isSingleWordQuery(String query) {
        return query.split(QUERY_TOKENIZER_REGEX).length == 1;
    }

    /**
     * Checks the projection for a SNIPPET column indicating that a snippet is needed
     * @return a boolean indicating if a snippet is needed or not.
     */
    private boolean snippetNeeded(String [] projection) {
        return ScContactsDatabaseHelper.isInProjection(projection, SearchSnippetColumns.SNIPPET);
    }

    private boolean handleDataUsageFeedback(Uri uri) {
        final long currentTimeMillis = Clock.getInstance().currentTimeMillis();
        final String usageType = uri.getQueryParameter(ScContactsContract.DataUsageFeedback.USAGE_TYPE);
        final String[] ids = uri.getLastPathSegment().trim().split(",");
        final ArrayList<Long> dataIds = new ArrayList<Long>(ids.length);

        for (String id : ids) {
            dataIds.add(Long.valueOf(id));
        }
        final boolean successful;
        if (TextUtils.isEmpty(usageType)) {
            Log.w(TAG, "Method for data usage feedback isn't specified. Ignoring.");
            successful = false;
        } else {
            successful = updateDataUsageStat(dataIds, usageType, currentTimeMillis) > 0;
        }

        return successful;
    }

    private interface DataUsageStatQuery {
        String TABLE = Tables.DATA_USAGE_STAT;

        String[] COLUMNS = new String[] {
                DataUsageStatColumns._ID,
        };
        int ID = 0;

        String SELECTION = DataUsageStatColumns.DATA_ID + " =? AND "
                + DataUsageStatColumns.USAGE_TYPE_INT + " =?";
    }

    /**
     * Update {@link Tables#DATA_USAGE_STAT}.
     *
     * @return the number of rows affected.
     */
    @VisibleForTesting
    int updateDataUsageStat(
            List<Long> dataIds, String type, long currentTimeMillis) {

        final SQLiteDatabase db = mDbHelper.get().getDatabase(true);

        final String typeString = String.valueOf(getDataUsageFeedbackType(type, null));
        final String currentTimeMillisString = String.valueOf(currentTimeMillis);

        for (long dataId : dataIds) {
            final String dataIdString = String.valueOf(dataId);
            mSelectionArgs2[0] = dataIdString;
            mSelectionArgs2[1] = typeString;
            final Cursor cursor = db.query(DataUsageStatQuery.TABLE,
                    DataUsageStatQuery.COLUMNS, DataUsageStatQuery.SELECTION,
                    mSelectionArgs2, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    final long id = cursor.getLong(DataUsageStatQuery.ID);

                    mSelectionArgs2[0] = currentTimeMillisString;
                    mSelectionArgs2[1] = String.valueOf(id);

                    db.execSQL("UPDATE " + Tables.DATA_USAGE_STAT +
                                    " SET " + DataUsageStatColumns.TIMES_USED + "=" +
                                    "ifnull(" + DataUsageStatColumns.TIMES_USED +",0)+1" +
                                    "," + DataUsageStatColumns.LAST_TIME_USED + "=?" +
                                    " WHERE " + DataUsageStatColumns._ID + "=?",
                            mSelectionArgs2);
                } 
                else {
                    mSelectionArgs4[0] = dataIdString;
                    mSelectionArgs4[1] = typeString;
                    mSelectionArgs4[2] = "1"; // times used
                    mSelectionArgs4[3] = currentTimeMillisString;
                    db.execSQL("INSERT INTO " + Tables.DATA_USAGE_STAT +
                                    "(" + DataUsageStatColumns.DATA_ID +
                                    "," + DataUsageStatColumns.USAGE_TYPE_INT +
                                    "," + DataUsageStatColumns.TIMES_USED +
                                    "," + DataUsageStatColumns.LAST_TIME_USED +
                                    ") VALUES (?,?,?,?)",
                            mSelectionArgs4);
                }
            } finally {
                cursor.close();
            }
        }

        return dataIds.size();
    }

    private static int getDataUsageFeedbackType(String type, Integer defaultType) {
        if (DataUsageFeedback.USAGE_TYPE_CALL.equals(type)) {
            return DataUsageStatColumns.USAGE_TYPE_INT_CALL; // 0
        }
        if (DataUsageFeedback.USAGE_TYPE_LONG_TEXT.equals(type)) {
            return DataUsageStatColumns.USAGE_TYPE_INT_LONG_TEXT; // 1
        }
        if (DataUsageFeedback.USAGE_TYPE_SHORT_TEXT.equals(type)) {
            return DataUsageStatColumns.USAGE_TYPE_INT_SHORT_TEXT; // 2
        }
        if (defaultType != null) {
            return defaultType;
        }
        throw new IllegalArgumentException("Invalid usage type " + type);
    }

    private void undemoteContact(SQLiteDatabase db, long id) {
        final String[] arg = new String[1];
        arg[0] = String.valueOf(id);
        db.execSQL(UN_DEMOTE_RAW_CONTACT, arg);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {

        // The dirtyTrick bundle is valid until we saw a query with EXTRA_ADDRESS_BOOK_INDEX_EXTRAS set to true
        if ("INDEX".equals(method) && dirtyTrickBundle != null) {
            return new Bundle(dirtyTrickBundle);
        
        } else if (PinnedPositions.UNDEMOTE_METHOD.equals(method)) {
//            getContext().enforceCallingOrSelfPermission(WRITE_PERMISSION, null);
            final long id;
            try {
                id = Long.valueOf(arg);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Contact ID must be a valid long number.");
            }
            undemoteContact(mDbHelper.get().getDatabase(true), id);
            return null;
        }
        return null;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return null;

        boolean success = false;
        try {
            if (mode.equals("r")) {
                waitForAccess(mReadAccessLatch);
            }
            else {
                waitForAccess(mWriteAccessLatch);
            }
            final AssetFileDescriptor ret;
            switchToContactMode();
            ret = openAssetFileLocal(uri, mode);
            success = true;
            return ret;
        } finally {
            if (DEBUG) {
                Log.v(TAG, "openAssetFile uri=" + uri + " mode=" + mode + " success=" + success);
            }
        }
    }

    public AssetFileDescriptor openAssetFileLocal(Uri uri, String mode) throws FileNotFoundException {

        final boolean writing = mode.contains("w");

        final SQLiteDatabase db = mDbHelper.get().getDatabase(writing);

        int match = sUriMatcher.match(uri);
        switch (match) {
            case RAW_CONTACTS_ID_PHOTO: {
                Cursor cursor =  queryLocal(uri, new String[] { ScContactsContract.CommonDataKinds.Photo.PHOTO }, null, null, null, -1);
                try {
                    if (cursor == null || !cursor.moveToNext()) {
                        return null;
                    }
                    byte[] data = cursor.getBlob(0);
                    if (data == null) {
                        return null;
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
                    try {
                        baos.write(data);
                    } catch (IOException e) {
                        Log.i(TAG, "Cannot write to output stream", e);
                        return null;
                    }
                    return  buildAssetFileDescriptor(baos);
                }
                finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }

            case RAW_CONTACTS_ID_DISPLAY_PHOTO: {
                long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
                boolean writeable = !(mode.equals("r") || mode.equals("p"));

                // Find the primary photo data record for this raw contact.
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

                String[] projection = new String[]{Data._ID, Photo.PHOTO_FILE_ID};
                setTablesAndProjectionMapForData(qb, projection, false);
                long photoMimeTypeId = mDbHelper.get().getMimeTypeId(Photo.CONTENT_ITEM_TYPE);

                Cursor c = qb.query(db, projection,
                        Data.RAW_CONTACT_ID + "=? AND " + DataColumns.MIMETYPE_ID + "=?",
                        new String[]{String.valueOf(rawContactId), String.valueOf(photoMimeTypeId)},
                        null, null, Data.IS_PRIMARY + " DESC");
                long dataId = 0;
                long photoFileId = 0;
                try {
                    if (c.getCount() >= 1) {
                        c.moveToFirst();
                        dataId = c.getLong(0);
                        photoFileId = c.getLong(1);
                    }
                } finally {
                    c.close();
                }

                // If write, open a write-able file descriptor that we can monitor.
                // When the caller finishes writing content, we'll process the photo and
                // update the data record.
                if (writeable) {
                    return openDisplayPhotoForWrite(rawContactId, dataId, mode);
                } else {
                    // and application may read a file in plain mode, thus it could specify the
                    // mode "p"
                    if (mode.contains("p"))
                        return openDisplayPhotoForRead(photoFileId);
                    else
                        return openDisplayPhotoForReadCipher(photoFileId);
                }
            }

            case DISPLAY_PHOTO_ID: {
                long photoFileId = ContentUris.parseId(uri);
                if (!mode.equals("r")) {
                    throw new IllegalArgumentException(
                            "Display photos retrieved by key can only be read.");
                }
                return openDisplayPhotoForReadCipher(photoFileId);
            }

            case DATA_ID: {
                return null;
//                long dataId = Long.parseLong(uri.getPathSegments().get(1));
//                long photoMimeTypeId = mDbHelper.get().getMimeTypeId(Photo.CONTENT_ITEM_TYPE);
//                return openPhotoAssetFile(db, uri, mode,
//                        Data._ID + "=? AND " + DataColumns.MIMETYPE_ID + "=" + photoMimeTypeId,
//                        new String[]{String.valueOf(dataId)});
            }

            case RAW_CONTACTS_AS_VCARD: {
                // When opening a contact as file, we pass back contents as a
                // vCard-encoded stream. We build into a local buffer first,
                // then pipe into MemoryFile once the exact size is known.
                final ByteArrayOutputStream localStream = new ByteArrayOutputStream();
                outputRawContactsAsVCard(uri, localStream, null, null);
                return buildAssetFileDescriptor(localStream);
            }

//            case CONTACTS_AS_MULTI_VCARD: {
//                final String lookupKeys = uri.getPathSegments().get(2);
//                final String[] loopupKeyList = lookupKeys.split(":");
//                final StringBuilder inBuilder = new StringBuilder();
//                Uri queryUri = Contacts.CONTENT_URI;
//                int index = 0;
//
//                // SQLite has limits on how many parameters can be used
//                // so the IDs are concatenated to a query string here instead
//                for (String lookupKey : loopupKeyList) {
//                    if (index == 0) {
//                        inBuilder.append("(");
//                    } else {
//                        inBuilder.append(",");
//                    }
//                    // TODO: Figure out what to do if the profile contact is in the list.
//                    long contactId = lookupContactIdByLookupKey(db, lookupKey);
//                    inBuilder.append(contactId);
//                    index++;
//                }
//                inBuilder.append(')');
//                final String selection = Contacts._ID + " IN " + inBuilder.toString();
//
//                // When opening a contact as file, we pass back contents as a
//                // vCard-encoded stream. We build into a local buffer first,
//                // then pipe into MemoryFile once the exact size is known.
//                final ByteArrayOutputStream localStream = new ByteArrayOutputStream();
//                outputRawContactsAsVCard(queryUri, localStream, selection, null);
//                return buildAssetFileDescriptor(localStream);
//            }

            default:
                throw new FileNotFoundException(mDbHelper.get().exceptionMessage("File does not exist", uri));
        }
    }

    /* Modified to use DbQueryUtils instead of
     *             return makeAssetFileDescriptor(
     *                       DatabaseUtils.blobFileDescriptorForQuery(db, sql, selectionArgs));
     */
//    private AssetFileDescriptor openPhotoAssetFile(SQLiteDatabase db, Uri uri, String mode, String selection,
//            String[] selectionArgs) throws FileNotFoundException {
//        if (!"r".equals(mode)) {
//            throw new FileNotFoundException(mDbHelper.get().exceptionMessage("Mode " + mode + " not supported.", uri));
//        }
//
//        String sql = "SELECT " + Photo.PHOTO + " FROM " + Views.DATA + " WHERE " + selection;
//        // This does not work on Android >= 4.0 - no solution for the time being
//        return SQLiteContentHelper.getBlobColumnAsAssetFile(db, sql, selectionArgs);
//
        // **** The following code was disabled because we use SQLCipher now ***
//            try {
//                return makeAssetFileDescriptor(DatabaseUtils.blobFileDescriptorForQuery(db, sql, selectionArgs));
//            }
//            catch (SQLiteDoneException e) {
//                // this will happen if the DB query returns no rows (i.e. contact does not exist)
//                throw new FileNotFoundException(uri.toString());
//            }
//    }

    /**
     * Opens a display photo from the photo store for reading.
     *
     * @param photoFileId The display photo file ID
     * @return An asset file descriptor that allows the file to be read.
     * @throws java.io.FileNotFoundException If no photo file for the given ID exists.
     */
    private AssetFileDescriptor openDisplayPhotoForRead(long photoFileId) throws FileNotFoundException {
        PhotoStore.Entry entry = mPhotoStore.get().get(photoFileId);
        if (entry != null) {
            try {
                return makeAssetFileDescriptor(ParcelFileDescriptor.open(new File(entry.path),
                                    ParcelFileDescriptor.MODE_READ_ONLY),
                                    entry.size);
            } catch (FileNotFoundException fnfe) {
                scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
                throw fnfe;
            }
        } else {
            scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
            throw new FileNotFoundException("No photo file found for ID " + photoFileId);
        }
    }

    /**
     * Read encryption key from key manager to encrypt/decrypt display photo files.
     *
     * @return key data
     */
    protected byte[] getKeyData(Context ctx) {
        byte[] keyData = KeyManagerSupport.getPrivateKeyData(ctx.getContentResolver(), PHOTO_STORE_KEY);
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(ctx.getContentResolver(), PHOTO_STORE_KEY, 16);
        }
        return keyData;
    }

    /**
     * Read IV from key manager to encrypt/decrypt display photo files.
     *
     * @return IV data
     */
    protected byte[] getIvData(Context ctx) {
        byte[] ivData = KeyManagerSupport.getPrivateKeyData(ctx.getContentResolver(), PHOTO_STORE_IV);
        if (ivData == null) {
            ivData = KeyManagerSupport.randomPrivateKeyData(ctx.getContentResolver(), PHOTO_STORE_IV, 16);
        }
        return ivData;
    }

    private AssetFileDescriptor openDisplayPhotoForReadCipher(long photoFileId) throws FileNotFoundException {
        PhotoStore.Entry entry = mPhotoStore.get().get(photoFileId);
        if (entry != null) {
            try {
                byte[] keyData = getKeyData(getContext());
                byte[] ivData = getIvData(getContext());

                if (keyData == null || ivData == null)
                    return openDisplayPhotoForRead(photoFileId);

                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Decrypting display photo file");
                SecretKeySpec keySpec =  new SecretKeySpec(keyData, "AES");
                Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivData));

                File inFile = new File(entry.path);
                CipherInputStream cis = new CipherInputStream(new FileInputStream(inFile), cipher);
                return buildAssetFileDescriptorInputStream(cis);

            } catch (FileNotFoundException fnfe) {
                scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
                throw fnfe;
            }  catch (Exception e) {
                Log.e(TAG, "Cannot open CipherInputFile.", e);
                throw new FileNotFoundException("Cannot open CipherInputFile: " + entry.path);
            }
        } else {
            scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
            throw new FileNotFoundException("No photo file found for ID " + photoFileId);
        }
    }

    /**
     * Opens a file descriptor for a photo to be written.
     *
     * When the caller completes writing to the file (closing the output stream), the image
     * will be parsed out and processed. If processing succeeds, the given raw contact ID's
     * primary photo record will be populated with the inserted image (if no primary photo
     * record exists, the data ID can be left as 0, and a new data record will be inserted).
     *
     * @param rawContactId Raw contact ID this photo entry should be associated with.
     * @param dataId Data ID for a photo mimetype that will be updated with the inserted
     *     image.  May be set to 0, in which case the inserted image will trigger creation
     *     of a new primary photo image data row for the raw contact.
     * @param uri The URI being used to access this file.
     * @param mode Read/write mode string.
     * @return An asset file descriptor the caller can use to write an image file for the
     *     raw contact.
     */
    private AssetFileDescriptor openDisplayPhotoForWrite(long rawContactId, long dataId, String mode) {
        try {
            ParcelFileDescriptor[] pipeFds = ParcelFileDescriptor.createPipe();
            PipeMonitor pipeMonitor = new PipeMonitor(rawContactId, dataId, pipeFds[0]);
            pipeMonitor.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Object[]) null);
            return new AssetFileDescriptor(pipeFds[1], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (IOException ioe) {
            Log.e(TAG, "Could not create temp image file in mode " + mode);
            return null;
        }
    }

    /**
     * Async task that monitors the given file descriptor (the read end of a pipe) for
     * the writer finishing.  If the data from the pipe contains a valid image, the image
     * is either inserted into the given raw contact or updated in the given data row.
     */
    private class PipeMonitor extends AsyncTask<Object, Object, Object> {
        private final ParcelFileDescriptor mDescriptor;
        private final long mRawContactId;
        private final long mDataId;
        private PipeMonitor(long rawContactId, long dataId, ParcelFileDescriptor descriptor) {
            mRawContactId = rawContactId;
            mDataId = dataId;
            mDescriptor = descriptor;
        }

        @Override
        protected Object doInBackground(Object... params) {
            AutoCloseInputStream is = new AutoCloseInputStream(mDescriptor);
            try {
                Bitmap b = BitmapFactory.decodeStream(is);
                if (b != null) {
                    waitForAccess(mWriteAccessLatch);
                    PhotoProcessor processor = new PhotoProcessor(b, getMaxDisplayPhotoDim(), getMaxThumbnailDim());

                    // Store the compressed photo in the photo store. PhotoStore handles cipher
                    PhotoStore photoStore = mContactsPhotoStore;
                    long photoFileId = photoStore.insert(processor);

                    // Depending on whether we already had a data row to attach the photo
                    // to, do an update or insert.
                    if (mDataId != 0) {
                        // Update the data record with the new photo.
                        ContentValues updateValues = new ContentValues();

                        // Signal that photo processing has already been handled.
                        updateValues.put(DataRowHandlerForPhoto.SKIP_PROCESSING_KEY, true);

                        if (photoFileId != 0) {
                            updateValues.put(Photo.PHOTO_FILE_ID, photoFileId);
                        }
                        updateValues.put(Photo.PHOTO, processor.getThumbnailPhotoBytes());
                        update(ContentUris.withAppendedId(Data.CONTENT_URI, mDataId),
                                updateValues, null, null);
                    }
                    else {
                        // Insert a new primary data record with the photo.
                        ContentValues insertValues = new ContentValues();

                        // Signal that photo processing has already been handled.
                        insertValues.put(DataRowHandlerForPhoto.SKIP_PROCESSING_KEY, true);

                        insertValues.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
                        insertValues.put(Data.IS_PRIMARY, 1);
                        if (photoFileId != 0) {
                            insertValues.put(Photo.PHOTO_FILE_ID, photoFileId);
                        }
                        insertValues.put(Photo.PHOTO, processor.getThumbnailPhotoBytes());
                        insert(RawContacts.CONTENT_URI.buildUpon()
                                .appendPath(String.valueOf(mRawContactId))
                                .appendPath(RawContacts.Data.CONTENT_DIRECTORY).build(),
                                insertValues);
                    }

                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

    /**
     * Output {@link com.silentcircle.silentcontacts.ScContactsContract.RawContacts} matching the requested selection in the vCard
     * format to the given {@link java.io.OutputStream}. This method returns silently if
     * any errors encountered.
     */
    private void outputRawContactsAsVCard(Uri uri, OutputStream stream, String selection, String[] selectionArgs) {

        final Context context = this.getContext();
        int vcardConfig = VCardConfig.VCARD_TYPE_DEFAULT;
        if(readBooleanQueryParameter(uri, RawContacts.QUERY_PARAMETER_VCARD_NO_PHOTO, false)) {
            vcardConfig |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }
        final VCardComposer composer = new VCardComposer(context, vcardConfig, false);
        Writer writer = null;
        Cursor cursor = null;
        final Uri rawContactsUri;
        rawContactsUri = RawContactsEntity.CONTENT_URI;

        try {
            writer = new BufferedWriter(new OutputStreamWriter(stream));
            if (!composer.init(uri, selection, selectionArgs, null, rawContactsUri)) {
                Log.w(TAG, "Failed to init VCardComposer");
                return;
            }

            // Create the EntityIterator here and hand it to the composer to avoid some
            // permission problems when sharing contacts via Bluetooth and others.
            //
            // **** NOTE: this work for single vCards only, multiple vCard request don't work this way
            String contactId = uri.getPathSegments().get(2);
            final String selectionEntity = Data._ID + "=?";
            final String[] selectionArgsEntity = new String[] {contactId};
            cursor = query(rawContactsUri, null, selectionEntity, selectionArgsEntity, null);

            EntityIterator entityIterator = RawContacts.newEntityIterator(cursor);
            while (!composer.isAfterLast()) {
                writer.write(composer.createOneEntryWithIterator(entityIterator));
            }
            entityIterator.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e);
        } finally {
            if(cursor != null)
                cursor.close();
            composer.terminate();
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.w(TAG, "IOException during closing output stream: " + e);
                }
            }
        }
    }

    /**
     * Returns an {@link android.content.res.AssetFileDescriptor} backed by the
     * contents of the given {@link java.io.ByteArrayOutputStream}.
     */
    private AssetFileDescriptor buildAssetFileDescriptor(ByteArrayOutputStream stream) {
        try {
            stream.flush();

            ByteArrayInputStream bIn = new ByteArrayInputStream(stream.toByteArray());

            ParcelFileDescriptor[] pipe;

            try {
                pipe = ParcelFileDescriptor.createPipe();
                new TransferThread(bIn, new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])).start();
            }
            catch (IOException e) {
                Log.e(getClass().getSimpleName(), "Exception opening pipe", e);
                throw new FileNotFoundException("Could not open pipe for VCard");
            }

            return makeAssetFileDescriptor(pipe[0]);

        } catch (IOException e) {
            Log.w(TAG, "Problem writing stream into an ParcelFileDescriptor: " + e.toString());
            return null;
        }
    }

    /**
     * Returns an {@link android.content.res.AssetFileDescriptor} backed by the
     * contents of the given {@link java.io.InputStream}.
     */
    private AssetFileDescriptor buildAssetFileDescriptorInputStream(InputStream stream) {
        try {

            ParcelFileDescriptor[] pipe;

            try {
              pipe = ParcelFileDescriptor.createPipe();
              new TransferThread(stream, new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])).start();
            }
            catch (IOException e) {
              Log.e(TAG, "Exception opening pipe (InputStream)", e);
              throw new FileNotFoundException("Could not open pipe for CipherStream");
            }

            return makeAssetFileDescriptor(pipe[0]);

        } catch (IOException e) {
            Log.w(TAG, "Problem writing stream into an ParcelFileDescriptor (InputStream): " + e.toString());
            return null;
        }
    }

    private AssetFileDescriptor makeAssetFileDescriptor(ParcelFileDescriptor fd) {
        return makeAssetFileDescriptor(fd, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    private AssetFileDescriptor makeAssetFileDescriptor(ParcelFileDescriptor fd, long length) {
        return fd != null ? new AssetFileDescriptor(fd, 0, length) : null;
    }

    /**
     * Create a single row cursor for a simple, informational queries, such as
     * {@link com.silentcircle.silentcontacts.ScContactsContract.ProviderStatus#CONTENT_URI}.
     */
    static Cursor buildSingleRowResult(String[] projection, String[] availableColumns,  Object[] data) {
        Preconditions.checkArgument(availableColumns.length == data.length);
        if (projection == null) {
            projection = availableColumns;
        }
        final MatrixCursor c = new MatrixCursor(projection, 1);
        final RowBuilder row = c.newRow();

        // It's O(n^2), but it's okay because we only have a few columns.
        for (int i = 0; i < c.getColumnCount(); i++) {
            final String column = c.getColumnName(i);

            boolean found = false;
            for (int j = 0; j < availableColumns.length; j++) {
                if (availableColumns[j].equals(column)) {
                    row.add(data[j]);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Invalid column " + projection[i]);
            }
        }
        return c;
    }

    // Thread that write out data
    static class TransferThread extends Thread {
        InputStream in;
        OutputStream out;

        TransferThread(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            byte[] buf = new byte[8192];
            int len;

            try {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } catch (IOException e) {
                Log.e(getClass().getSimpleName(), "I/O Exception transferring file");
            } finally {
                try {
                    if (in != null)
                        in.close();
                    if (out != null) {
                        out.flush();
                        out.close();
                    }
                } catch (IOException e) {}
            }

        }
    }
}
