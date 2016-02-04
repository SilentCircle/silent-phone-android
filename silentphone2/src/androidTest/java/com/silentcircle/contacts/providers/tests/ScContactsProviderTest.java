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
 * The implementation re-uses code from the original Android ContactsProvider test project.
 */

package com.silentcircle.contacts.providers.tests;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.test.MoreAsserts;
import android.util.Log;

import com.silentcircle.contacts.providers.DataRowHandlerForPhoto;
import com.silentcircle.contacts.providers.PhotoProcessor;
import com.silentcircle.contacts.providers.PhotoStore;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DataUsageStatColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.RawContactsColumns;
import com.silentcircle.contacts.providers.tests.testutils.ContactUtil;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Callable;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.GroupMembership;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Organization;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.SipAddress;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredName;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.DataUsageFeedback;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentcontacts2.ScContactsContract.DisplayPhoto;
import com.silentcircle.silentcontacts2.ScContactsContract.Groups;
import com.silentcircle.silentcontacts2.ScContactsContract.PhoneLookup;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContactsEntity;
import com.silentcircle.silentcontacts2.ScContactsContract.SearchSnippetColumns;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItemPhotos;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItems;
import com.silentcircle.silentphone2.R;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.silentcircle.contacts.providers.tests.TestUtils.cv;

public class ScContactsProviderTest extends ProviderTestBase<SynchronousContactsProvider> {

    @SuppressWarnings("unused")
    private static final String TAG = "ScContactsProviderTest";

    public ScContactsProviderTest() {
        super(SynchronousContactsProvider.class, ScContactsContract.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

//    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // NOTE Removed columns for support of accounts and profile
    public void testRawContactsProjection() {
        assertProjection(RawContacts.CONTENT_URI, new String[]{
                RawContacts._ID,
//                RawContacts.CONTACT_ID,
//                RawContacts.ACCOUNT_NAME,
//                RawContacts.ACCOUNT_TYPE,
//                RawContacts.DATA_SET,
//                RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                RawContacts.SOURCE_ID,
                RawContacts.VERSION,
//                RawContacts.RAW_CONTACT_IS_USER_PROFILE,
                RawContacts.DIRTY,
                RawContacts.DELETED,
                RawContacts.PHOTO_ID,
                RawContacts.PHOTO_FILE_ID,
                RawContacts.PHOTO_URI,
                RawContacts.PHOTO_THUMBNAIL_URI,
                RawContacts.DISPLAY_NAME_PRIMARY,
                RawContacts.DISPLAY_NAME_ALTERNATIVE,
                RawContacts.DISPLAY_NAME_SOURCE,
                RawContacts.PHONETIC_NAME,
                RawContacts.PHONETIC_NAME_STYLE,
                RawContacts.NAME_VERIFIED,
                RawContacts.SORT_KEY_PRIMARY,
                RawContacts.SORT_KEY_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_LABEL_PRIMARY,
                RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY,
                RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE,
                RawContacts.TIMES_CONTACTED,
                RawContacts.LAST_TIME_CONTACTED,
                RawContacts.CUSTOM_RINGTONE,
                RawContacts.STARRED,
                RawContacts.PINNED,
                RawContacts.HAS_PHONE_NUMBER,
                RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP,
                RawContacts.CONTACT_TYPE,
                RawContacts.SYNC1,
                RawContacts.SYNC2,
                RawContacts.SYNC3,
                RawContacts.SYNC4,
        });
    }

    public void testContactsStrequentProjection() {
        assertProjection(RawContacts.CONTENT_STREQUENT_URI, new String[]{
                RawContacts._ID,
                RawContacts.DISPLAY_NAME_PRIMARY,
                RawContacts.DISPLAY_NAME_ALTERNATIVE,
                RawContacts.DISPLAY_NAME_SOURCE,
                RawContacts.PHONETIC_NAME,
                RawContacts.PHONETIC_NAME_STYLE,
                RawContacts.SORT_KEY_PRIMARY,
                RawContacts.SORT_KEY_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_LABEL_PRIMARY,
                RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY,
                RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE,
                RawContacts.LAST_TIME_CONTACTED,
                RawContacts.TIMES_CONTACTED,
                RawContacts.STARRED,
                RawContacts.PINNED,
                RawContacts.PHOTO_ID,
                RawContacts.PHOTO_FILE_ID,
                RawContacts.PHOTO_URI,
                RawContacts.PHOTO_THUMBNAIL_URI,
                RawContacts.CUSTOM_RINGTONE,
                RawContacts.HAS_PHONE_NUMBER,
                DataUsageStatColumns.TIMES_USED,
                DataUsageStatColumns.LAST_TIME_USED,
        });
    }

    public void testContactsStrequentPhoneOnlyProjection() {
        assertProjection(RawContacts.CONTENT_STREQUENT_URI.buildUpon()
                .appendQueryParameter(ScContactsContract.STREQUENT_PHONE_ONLY, "true").build(), new String[]{
                RawContacts._ID,
                RawContacts.DISPLAY_NAME_PRIMARY,
                RawContacts.DISPLAY_NAME_ALTERNATIVE,
                RawContacts.DISPLAY_NAME_SOURCE,
                RawContacts.PHONETIC_NAME,
                RawContacts.PHONETIC_NAME_STYLE,
                RawContacts.SORT_KEY_PRIMARY,
                RawContacts.SORT_KEY_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_LABEL_PRIMARY,
                RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY,
                RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE,
                RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE,
                RawContacts.LAST_TIME_CONTACTED,
                RawContacts.TIMES_CONTACTED,
                RawContacts.STARRED,
                RawContacts.PINNED,
                RawContacts.PHOTO_ID,
                RawContacts.PHOTO_FILE_ID,
                RawContacts.PHOTO_URI,
                RawContacts.PHOTO_THUMBNAIL_URI,
                RawContacts.CUSTOM_RINGTONE,
                RawContacts.HAS_PHONE_NUMBER,
                DataUsageStatColumns.TIMES_USED,
                DataUsageStatColumns.LAST_TIME_USED,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
                Phone.IS_SUPER_PRIMARY,
                Phone.RAW_CONTACT_ID
        });
    }

    public void testDataProjection() {
        assertProjection(Data.CONTENT_URI, new String[]{
                Data._ID,
                Data.RAW_CONTACT_ID,
                Data.DATA_VERSION,
                Data.IS_PRIMARY,
                Data.IS_SUPER_PRIMARY,
//                Data.RES_PACKAGE,
                Data.MIMETYPE,
                Data.DATA1,
                Data.DATA2,
                Data.DATA3,
                Data.DATA4,
                Data.DATA5,
                Data.DATA6,
                Data.DATA7,
                Data.DATA8,
                Data.DATA9,
                Data.DATA10,
                Data.DATA11,
                Data.DATA12,
                Data.DATA13,
                Data.DATA14,
                Data.DATA15,
                Data.SYNC1,
                Data.SYNC2,
                Data.SYNC3,
                Data.SYNC4,
//                Data.CONTACT_ID,
//                Data.PRESENCE,
//                Data.CHAT_CAPABILITY,
//                Data.STATUS,
//                Data.STATUS_TIMESTAMP,
//                Data.STATUS_RES_PACKAGE,
//                Data.STATUS_LABEL,
//                Data.STATUS_ICON,
                Data.TIMES_USED,
                Data.LAST_TIME_USED,

                // TODO - this is part of Views.DATA, we don't have account and profile support
//                RawContacts.ACCOUNT_NAME,
//                RawContacts.ACCOUNT_TYPE,
//                RawContacts.DATA_SET,
//                RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                RawContacts.SOURCE_ID,
                RawContacts.VERSION,
                RawContacts.DIRTY,
                RawContacts.NAME_VERIFIED,
//                RawContacts.RAW_CONTACT_IS_USER_PROFILE,

                // TODO - Contacts column names same as for RawContacts, use as appropriate
//                Contacts._ID,
                RawContacts.DISPLAY_NAME_PRIMARY,
                RawContacts.DISPLAY_NAME_ALTERNATIVE,
                RawContacts.DISPLAY_NAME_SOURCE,
                RawContacts.PHONETIC_NAME,
                RawContacts.PHONETIC_NAME_STYLE,
//                Contacts.SORT_KEY_PRIMARY,
//                Contacts.SORT_KEY_ALTERNATIVE,
                RawContacts.LAST_TIME_CONTACTED,
                RawContacts.TIMES_CONTACTED,
                RawContacts.STARRED,
//                Contacts.IN_VISIBLE_GROUP,
                RawContacts.PHOTO_ID,
                RawContacts.PHOTO_FILE_ID,
                RawContacts.PHOTO_URI,
                RawContacts.PHOTO_THUMBNAIL_URI,
                RawContacts.CUSTOM_RINGTONE,
//                Contacts.LOOKUP_KEY,
//                Contacts.NAME_RAW_CONTACT_ID,
                RawContacts.HAS_PHONE_NUMBER,
                RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP,
//                Contacts.CONTACT_PRESENCE,
//                Contacts.CONTACT_CHAT_CAPABILITY,
//                Contacts.CONTACT_STATUS,
//                Contacts.CONTACT_STATUS_TIMESTAMP,
//                Contacts.CONTACT_STATUS_RES_PACKAGE,
//                Contacts.CONTACT_STATUS_LABEL,
//                Contacts.CONTACT_STATUS_ICON,
                GroupMembership.GROUP_SOURCE_ID,
        });
    }

    public void testDistinctDataProjection() {
        assertProjection(Phone.CONTENT_FILTER_URI.buildUpon().appendPath("123").build(),
            new String[]{
                Data._ID,
                Data.RAW_CONTACT_ID,
                Data.DATA_VERSION,
                Data.IS_PRIMARY,
                Data.IS_SUPER_PRIMARY,
 //               Data.RES_PACKAGE,
                Data.MIMETYPE,
                Data.DATA1,
                Data.DATA2,
                Data.DATA3,
                Data.DATA4,
                Data.DATA5,
                Data.DATA6,
                Data.DATA7,
                Data.DATA8,
                Data.DATA9,
                Data.DATA10,
                Data.DATA11,
                Data.DATA12,
                Data.DATA13,
                Data.DATA14,
                Data.DATA15,
                Data.SYNC1,
                Data.SYNC2,
                Data.SYNC3,
                Data.SYNC4,
//                Data.CONTACT_ID,
//                Data.PRESENCE,
//                Data.CHAT_CAPABILITY,
//                Data.STATUS,
//                Data.STATUS_TIMESTAMP,
//                Data.STATUS_RES_PACKAGE,
//                Data.STATUS_LABEL,
//                Data.STATUS_ICON,
//                RawContacts.RAW_CONTACT_IS_USER_PROFILE,
//                Contacts._ID,
                    Data.TIMES_USED,
                    Data.LAST_TIME_USED,
                RawContacts.DISPLAY_NAME_PRIMARY,
                RawContacts.DISPLAY_NAME_ALTERNATIVE,
                RawContacts.DISPLAY_NAME_SOURCE,
                RawContacts.PHONETIC_NAME,
                RawContacts.PHONETIC_NAME_STYLE,
//                Contacts.SORT_KEY_PRIMARY,
//                Contacts.SORT_KEY_ALTERNATIVE,
                RawContacts.LAST_TIME_CONTACTED,
                RawContacts.TIMES_CONTACTED,
                RawContacts.STARRED,
//                Contacts.IN_VISIBLE_GROUP,
                RawContacts.PHOTO_ID,
                RawContacts.PHOTO_FILE_ID,
                RawContacts.PHOTO_URI,
                RawContacts.PHOTO_THUMBNAIL_URI,
                RawContacts.HAS_PHONE_NUMBER,
                RawContacts.CONTACT_LAST_UPDATED_TIMESTAMP,
                RawContacts.CUSTOM_RINGTONE,
//                Contacts.LOOKUP_KEY,
//                Contacts.CONTACT_PRESENCE,
//                Contacts.CONTACT_CHAT_CAPABILITY,
//                Contacts.CONTACT_STATUS,
//                Contacts.CONTACT_STATUS_TIMESTAMP,
//                Contacts.CONTACT_STATUS_RES_PACKAGE,
//                Contacts.CONTACT_STATUS_LABEL,
//                Contacts.CONTACT_STATUS_ICON,
                GroupMembership.GROUP_SOURCE_ID,
        });
    }

    public void testRawEntityProjection() {
        assertProjection(RawContactsEntity.CONTENT_URI, new String[]{
                RawContacts.Entity.DATA_ID,
                RawContacts._ID,
                RawContacts.CONTACT_TYPE,
                RawContacts.DISPLAY_NAME_SOURCE,            // 0
                RawContacts.DISPLAY_NAME,                   // 1
                RawContacts.DISPLAY_NAME_ALTERNATIVE,       // 2
                RawContacts.PHONETIC_NAME,                  // 3
                RawContacts.PHOTO_ID,                       // 4
                RawContacts.PHOTO_URI,
                RawContacts.SOURCE_ID,
                RawContacts.VERSION,
                RawContacts.DIRTY,
                RawContacts.NAME_VERIFIED,
                RawContacts.DELETED,
                RawContacts.CUSTOM_RINGTONE,
                RawContacts.STARRED,
                RawContacts.SYNC1,
                RawContacts.SYNC2,
                RawContacts.SYNC3,
                RawContacts.SYNC4,
                RawContacts.STARRED,
                RawContacts.HAS_PHONE_NUMBER,
//                RawContacts.RAW_CONTACT_IS_USER_PROFILE,
                Data.DATA_VERSION,
                Data.IS_PRIMARY,
                Data.IS_SUPER_PRIMARY,
//                Data.RES_PACKAGE,
                Data.MIMETYPE,
                Data.DATA1,
                Data.DATA2,
                Data.DATA3,
                Data.DATA4,
                Data.DATA5,
                Data.DATA6,
                Data.DATA7,
                Data.DATA8,
                Data.DATA9,
                Data.DATA10,
                Data.DATA11,
                Data.DATA12,
                Data.DATA13,
                Data.DATA14,
                Data.DATA15,
                Data.SYNC1,
                Data.SYNC2,
                Data.SYNC3,
                Data.SYNC4,
                GroupMembership.GROUP_SOURCE_ID,
                DataUsageStatColumns.TIMES_USED,
                DataUsageStatColumns.LAST_TIME_USED,
        });
    }

    public void testPhoneLookupProjection() {
        assertProjection(PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath("123").build(),
            new String[]{
                PhoneLookup._ID,
//                PhoneLookup.LOOKUP_KEY,
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup.LAST_TIME_CONTACTED,
                PhoneLookup.TIMES_CONTACTED,
                PhoneLookup.STARRED,
//                PhoneLookup.IN_VISIBLE_GROUP,
                PhoneLookup.PHOTO_ID,
                PhoneLookup.PHOTO_FILE_ID,
                PhoneLookup.PHOTO_URI,
//                PhoneLookup.PHOTO_URI,
//                PhoneLookup.PHOTO_THUMBNAIL_URI,
                PhoneLookup.CUSTOM_RINGTONE,
                PhoneLookup.HAS_PHONE_NUMBER,
                PhoneLookup.NUMBER,
                PhoneLookup.TYPE,
                PhoneLookup.LABEL,
                PhoneLookup.NORMALIZED_NUMBER,
        });
    }

    public void testGroupsProjection() {
        assertProjection(Groups.CONTENT_URI, new String[] {
                Groups._ID,
//                Groups.ACCOUNT_NAME,
//                Groups.ACCOUNT_TYPE,
//                Groups.DATA_SET,
//                Groups.ACCOUNT_TYPE_AND_DATA_SET,
                Groups.SOURCE_ID,
                Groups.DIRTY,
                Groups.VERSION,
//                Groups.RES_PACKAGE,
                Groups.TITLE,
                Groups.TITLE_RES,
                Groups.GROUP_VISIBLE,
                Groups.SYSTEM_ID,
                Groups.DELETED,
                Groups.NOTES,
                Groups.SHOULD_SYNC,
                Groups.FAVORITES,
                Groups.AUTO_ADD,
                Groups.GROUP_IS_READ_ONLY,
                Groups.SYNC1,
                Groups.SYNC2,
                Groups.SYNC3,
                Groups.SYNC4,
        });
    }

    public void testGroupsSummaryProjection() {
        assertProjection(Groups.CONTENT_SUMMARY_URI, new String[]{
                Groups._ID,
//                Groups.ACCOUNT_NAME,
//                Groups.ACCOUNT_TYPE,
//                Groups.DATA_SET,
//                Groups.ACCOUNT_TYPE_AND_DATA_SET,
                Groups.SOURCE_ID,
                Groups.DIRTY,
                Groups.VERSION,
//                Groups.RES_PACKAGE,
                Groups.TITLE,
                Groups.TITLE_RES,
                Groups.GROUP_VISIBLE,
                Groups.SYSTEM_ID,
                Groups.DELETED,
                Groups.NOTES,
                Groups.SHOULD_SYNC,
                Groups.FAVORITES,
                Groups.AUTO_ADD,
                Groups.GROUP_IS_READ_ONLY,
                Groups.SYNC1,
                Groups.SYNC2,
                Groups.SYNC3,
                Groups.SYNC4,
                Groups.SUMMARY_COUNT,
                Groups.SUMMARY_WITH_PHONES,
                Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT,
        });
    }


// TODO - more snippet tests
    public void testContactsWithSnippetProjection() {
        assertProjection(RawContacts.CONTENT_FILTER_URI.buildUpon().appendPath("nothing").build(),
            new String[]{
                RawContacts._ID,
                RawContacts.DISPLAY_NAME_PRIMARY,
                RawContacts.DISPLAY_NAME_ALTERNATIVE,
                RawContacts.DISPLAY_NAME_SOURCE,
                RawContacts.PHONETIC_NAME,
                RawContacts.PHONETIC_NAME_STYLE,
                RawContacts.CONTACT_TYPE,
//                Contacts.SORT_KEY_PRIMARY,
//                Contacts.SORT_KEY_ALTERNATIVE,
                RawContacts.LAST_TIME_CONTACTED,
                RawContacts.TIMES_CONTACTED,
                RawContacts.STARRED,
                RawContacts.HAS_PHONE_NUMBER,
//                Contacts.IN_VISIBLE_GROUP,
                RawContacts.PHOTO_ID,
                RawContacts.PHOTO_FILE_ID,
                RawContacts.PHOTO_URI,
                RawContacts.PHOTO_THUMBNAIL_URI,
                RawContacts.CUSTOM_RINGTONE,
//                Contacts.HAS_PHONE_NUMBER,
//                Contacts.SEND_TO_VOICEMAIL,
//                Contacts.IS_USER_PROFILE,
//                Contacts.LOOKUP_KEY,
//                Contacts.NAME_RAW_CONTACT_ID,
//                Contacts.CONTACT_PRESENCE,
//                Contacts.CONTACT_CHAT_CAPABILITY,
//                Contacts.CONTACT_STATUS,
//                Contacts.CONTACT_STATUS_TIMESTAMP,
//                Contacts.CONTACT_STATUS_RES_PACKAGE,
//                Contacts.CONTACT_STATUS_LABEL,
//                Contacts.CONTACT_STATUS_ICON,

                SearchSnippetColumns.SNIPPET,
        });
    }

    public void testDirectoryProjection() {
        assertProjection(Directory.CONTENT_URI, new String[]{
                Directory._ID,
                Directory.PACKAGE_NAME,
                Directory.TYPE_RESOURCE_ID,
                Directory.DISPLAY_NAME,
                Directory.DIRECTORY_AUTHORITY,
                Directory.EXPORT_SUPPORT,
                Directory.SHORTCUT_SUPPORT,
                Directory.PHOTO_SUPPORT,
        });
    }

    public void testRawContactsInsert() {
        ContentValues values = new ContentValues();

//        values.put(RawContacts.ACCOUNT_NAME, "a");
//        values.put(RawContacts.ACCOUNT_TYPE, "b");
//        values.put(RawContacts.DATA_SET, "ds");
        values.put(RawContacts.SOURCE_ID, "c");
        values.put(RawContacts.VERSION, 42);
        values.put(RawContacts.DIRTY, 1);
        values.put(RawContacts.DELETED, 1);
        values.put(RawContacts.CONTACT_TYPE, RawContacts.CONTACT_TYPE_NORMAL);
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.STARRED, 1);
        values.put(RawContacts.SYNC1, "e");
        values.put(RawContacts.SYNC2, "f");
        values.put(RawContacts.SYNC3, "g");
        values.put(RawContacts.SYNC4, "h");

        Uri rowUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rowUri);

        assertStoredValues(rowUri, values);
        assertSelection(RawContacts.CONTENT_URI, values, RawContacts._ID, rawContactId);
        assertNetworkNotified(true);
    }

    
    public void testDataInsert() {
        long rawContactId = createRawContactWithName("John", "Doe");

        ContentValues values = new ContentValues();
        putDataValues(values, rawContactId);
        Uri dataUri = resolver.insert(Data.CONTENT_URI, values);
        long dataId = ContentUris.parseId(dataUri);

        assertStoredValues(dataUri, values);

        assertSelection(Data.CONTENT_URI, values, Data._ID, dataId);

        // Access the same data through the directory under RawContacts
        Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        Uri rawContactDataUri = Uri.withAppendedPath(rawContactUri, RawContacts.Data.CONTENT_DIRECTORY);
        assertSelection(rawContactDataUri, values, Data._ID, dataId);

//        // Access the same data through the directory under Contacts
//        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
//        Uri contactDataUri = Uri.withAppendedPath(contactUri, Contacts.Data.CONTENT_DIRECTORY);
//        assertSelection(contactDataUri, values, Data._ID, dataId);

        assertNetworkNotified(true);
    }

    public void testRawContactDeletion() {
        long rawContactId = createRawContact();
        Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);

        insertImHandle(rawContactId, Im.PROTOCOL_GOOGLE_TALK, null, "deleteme@android.com");

        assertEquals(1, getCount(Uri.withAppendedPath(uri, RawContacts.Data.CONTENT_DIRECTORY), null, null));

        insertStructuredName(rawContactId, "Meghan", "Knox");
        insertPhoneNumber(rawContactId, "18004664411");
        assertEquals(3, getCount(Data.CONTENT_URI, null, null));

        resolver.delete(uri, null, null);

//        assertStoredValue(uri, RawContacts.DELETED, "1");
//        assertNetworkNotified(true);
//
//        Uri permanentDeletionUri = setCallerIsSyncAdapter(uri, null);
//        resolver.delete(permanentDeletionUri, null, null);
        assertEquals(0, getCount(uri, null, null));
        assertEquals(0, getCount(Uri.withAppendedPath(uri, RawContacts.Data.CONTENT_DIRECTORY), null, null));
        
        assertNetworkNotified(false);
    }


    private void putDataValues(ContentValues values, long rawContactId) {
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, "testmimetype");
//        values.put(Data.RES_PACKAGE, "oldpackage");
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.DATA1, "one");
        values.put(Data.DATA2, "two");
        values.put(Data.DATA3, "three");
        values.put(Data.DATA4, "four");
        values.put(Data.DATA5, "five");
        values.put(Data.DATA6, "six");
        values.put(Data.DATA7, "seven");
        values.put(Data.DATA8, "eight");
        values.put(Data.DATA9, "nine");
        values.put(Data.DATA10, "ten");
        values.put(Data.DATA11, "eleven");
        values.put(Data.DATA12, "twelve");
        values.put(Data.DATA13, "thirteen");
        values.put(Data.DATA14, "fourteen");
        values.put(Data.DATA15, "fifteen");
        values.put(Data.SYNC1, "sync1");
        values.put(Data.SYNC2, "sync2");
        values.put(Data.SYNC3, "sync3");
        values.put(Data.SYNC4, "sync4");
    }

    public void testDataCreateUpdateDeleteByMimeType() throws Exception {
        long rawContactId = createRawContact();

        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, "testmimetype");
//        values.put(Data.RES_PACKAGE, "oldpackage");
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.DATA1, "old1");
        values.put(Data.DATA2, "old2");
        values.put(Data.DATA3, "old3");
        values.put(Data.DATA4, "old4");
        values.put(Data.DATA5, "old5");
        values.put(Data.DATA6, "old6");
        values.put(Data.DATA7, "old7");
        values.put(Data.DATA8, "old8");
        values.put(Data.DATA9, "old9");
        values.put(Data.DATA10, "old10");
        values.put(Data.DATA11, "old11");
        values.put(Data.DATA12, "old12");
        values.put(Data.DATA13, "old13");
        values.put(Data.DATA14, "old14");
        values.put(Data.DATA15, "old15");
        Uri uri = resolver.insert(Data.CONTENT_URI, values);
        assertStoredValues(uri, values);
        assertNetworkNotified(true);

        values.clear();
//        values.put(Data.RES_PACKAGE, "newpackage");
        values.put(Data.IS_PRIMARY, 0);
        values.put(Data.IS_SUPER_PRIMARY, 0);
        values.put(Data.DATA1, "new1");
        values.put(Data.DATA2, "new2");
        values.put(Data.DATA3, "new3");
        values.put(Data.DATA4, "new4");
        values.put(Data.DATA5, "new5");
        values.put(Data.DATA6, "new6");
        values.put(Data.DATA7, "new7");
        values.put(Data.DATA8, "new8");
        values.put(Data.DATA9, "new9");
        values.put(Data.DATA10, "new10");
        values.put(Data.DATA11, "new11");
        values.put(Data.DATA12, "new12");
        values.put(Data.DATA13, "new13");
        values.put(Data.DATA14, "new14");
        values.put(Data.DATA15, "new15");
        resolver.update(Data.CONTENT_URI, values, Data.RAW_CONTACT_ID + "=" + rawContactId +
                " AND " + Data.MIMETYPE + "='testmimetype'", null);
        assertNetworkNotified(true);

        assertStoredValues(uri, values);

        int count = resolver.delete(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=" + rawContactId
                + " AND " + Data.MIMETYPE + "='testmimetype'", null);
        assertEquals(1, count);
        assertEquals(0, getCount(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=" + rawContactId
                        + " AND " + Data.MIMETYPE + "='testmimetype'", null));
        assertNetworkNotified(true);
    }

    /**
     * @param data1 email address or phone number
     * @param usageType One of {@link DataUsageFeedback#USAGE_TYPE}
     * @param values ContentValues for this feedback. Useful for incrementing
     * {Contacts#TIMES_CONTACTED} in the ContentValue. Can be null.
     */
    private void sendFeedback(String data1, String usageType, ContentValues values) {
        final long dataId = getStoredLongValue(Data.CONTENT_URI,
                Data.DATA1 + "=?", new String[] { data1 }, Data._ID);
        MoreAsserts.assertNotEqual(0, updateDataUsageFeedback(usageType, dataId));
//        if (values != null && values.containsKey(RawContacts.TIMES_CONTACTED)) {
//            values.put(RawContacts.TIMES_CONTACTED, values.getAsInteger(RawContacts.TIMES_CONTACTED) + 1);
//        }
    }

    private void updateDataUsageFeedback(String usageType, Uri resultUri) {
        final long id = ContentUris.parseId(resultUri);
        final boolean successful = updateDataUsageFeedback(usageType, id) > 0;
        assertTrue(successful);
    }

    private int updateDataUsageFeedback(String usageType, long... ids) {
        final StringBuilder idList = new StringBuilder();
        for (long id : ids) {
            if (idList.length() > 0) idList.append(",");
            idList.append(id);
        }
        return resolver.update(DataUsageFeedback.FEEDBACK_URI.buildUpon()
                .appendPath(idList.toString())
                .appendQueryParameter(DataUsageFeedback.USAGE_TYPE, usageType)
                .build(), new ContentValues(), null, null);
    }

    /**
     * Checks ContactsProvider2 works well with strequent Uris. The provider should return starred
     * contacts and frequently used contacts.
     */
    public void testQueryContactStrequent() {
        ContentValues values1 = new ContentValues();
        final String email1 = "a@acme.com";
        final int timesContacted1 = 0;
        createRawContact(values1, "Noah", "Tever", "18004664411", email1, 0, timesContacted1, 0, 0, 0);
        final String phoneNumber2 = "18004664412";
        ContentValues values2 = new ContentValues();
        createRawContact(values2, "Sam", "Times", phoneNumber2, "b@acme.com", 0, 3, 0, 0, 0);
        ContentValues values3 = new ContentValues();
        final String phoneNumber3 = "18004664413";
        final int timesContacted3 = 5;
        createRawContact(values3, "Lotta", "Calling", phoneNumber3, "c@acme.com", 0, timesContacted3, 0, 0, 0);
        ContentValues values4 = new ContentValues();
        final long rawContactId4 = createRawContact(values4, "Fay", "Veritt", null, "d@acme.com", 0, 0, 1, 0, 0);

        // Starred contacts should be returned. TIMES_CONTACTED should be ignored and only data
        // usage feedback should be used for "frequently contacted" listing.
        assertStoredValues(RawContacts.CONTENT_STREQUENT_URI, values4);

        // Send feedback for the 3rd phone number, pretending we called that person via phone.
        sendFeedback(phoneNumber3, DataUsageFeedback.USAGE_TYPE_CALL, values3);

        // After the feedback, 3rd contact should be shown after starred one.
        assertStoredValuesOrderly(RawContacts.CONTENT_STREQUENT_URI, values4, values3 );

        sendFeedback(email1, DataUsageFeedback.USAGE_TYPE_LONG_TEXT, values1);
        // Twice.
        sendFeedback(email1, DataUsageFeedback.USAGE_TYPE_LONG_TEXT, values1);

        // After the feedback, 1st and 3rd contacts should be shown after starred one.
        assertStoredValuesOrderly(RawContacts.CONTENT_STREQUENT_URI,values4, values1, values3);

        // With phone-only parameter, 1st and 4th contacts shouldn't be returned because:
        // 1st: feedbacks are only about email, not about phone call.
        // 4th: it has no phone number though starred.
        Uri phoneOnlyStrequentUri = RawContacts.CONTENT_STREQUENT_URI.buildUpon()
                .appendQueryParameter(ScContactsContract.STREQUENT_PHONE_ONLY, "true")
                .build();
        assertStoredValuesOrderly(phoneOnlyStrequentUri, values3);

        // Now the 4th contact has a phone number.
//        Uri phone4 = insertPhoneNumber(rawContactId4, "18004664414");
//        long phone4Id = ContentUris.parseId(phone4);

//        // Phone only strequent should return 4th contact.
//        assertStoredValuesOrderly(phoneOnlyStrequentUri, values4, values3);

        // Now the 4th contact has three phone numbers, one of which is called twice and
        // the other once
        final String phoneNumber4 = "18004664414";
        final String phoneNumber5 = "18004664415";
        final String phoneNumber6 = "18004664416";
        insertPhoneNumber(rawContactId4, phoneNumber4);
        insertPhoneNumber(rawContactId4, phoneNumber5);
        insertPhoneNumber(rawContactId4, phoneNumber6);
        values3.put(Phone.NUMBER, phoneNumber3);
        values4.put(Phone.NUMBER, phoneNumber4);

        sendFeedback(phoneNumber5, DataUsageFeedback.USAGE_TYPE_CALL, values4);
        sendFeedback(phoneNumber5, DataUsageFeedback.USAGE_TYPE_CALL, values4);
        sendFeedback(phoneNumber6, DataUsageFeedback.USAGE_TYPE_CALL, values4);

        // Create a ContentValues object representing the second phone number of contact 4
        final ContentValues values5 = new ContentValues(values4);
        values5.put(Phone.NUMBER, phoneNumber5);

        // Create a ContentValues object representing the third phone number of contact 4
        final ContentValues values6 = new ContentValues(values4);
        values6.put(Phone.NUMBER, phoneNumber6);

        // Phone only strequent should return all phone numbers belonging to the 4th contact,
        // and then contact 3.
        Log.d(TAG, "++++ The following cursor:");
        assertStoredValuesOrderly(phoneOnlyStrequentUri, values5, values6, values4, values3);

        // Send feedback for the 2rd phone number, pretending we send the person a SMS message.
        sendFeedback(phoneNumber2, DataUsageFeedback.USAGE_TYPE_SHORT_TEXT, values1);

        // SMS feedback shouldn't affect phone-only results.
        assertStoredValuesOrderly(phoneOnlyStrequentUri, values5, values6, values4, values3);

        values4.remove(Phone.NUMBER);
        Uri filterUri = Uri.withAppendedPath(RawContacts.CONTENT_STREQUENT_FILTER_URI, "fay");
        assertStoredValues(filterUri, values4);

//        // Delete the 4th phone number
//        int deleteResult = resolver.delete(ContentUris.withAppendedId(Phone.CONTENT_URI, phone4Id), null, null);
//
//        Log.d(TAG, "++++ phone4Uri: " + phone4 + ", delete result: " + deleteResult + ", contact: " + rawContactId4);
//
//        // After deletion of phone number only 3rd phone number
//        assertStoredValuesOrderly(phoneOnlyStrequentUri, values3);
    }

    public void testDisplayNameParsingWhenPartsUnspecified() {
        long rawContactId = createRawContact();
        ContentValues values = new ContentValues();
        values.put(StructuredName.DISPLAY_NAME, "Mr.John Kevin von Smith, Jr.");
        insertStructuredName(rawContactId, values);

        values.clear();
        values.put(RawContacts.DISPLAY_NAME, "Mr.John Kevin von Smith, Jr.");
//        assertStoredValues(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId) , values);

        assertStructuredName(rawContactId, "Mr.", "John", "Kevin", "von Smith", "Jr.");
    }

    public void testDisplayNameParsingWhenPartsAreNull() {
        long rawContactId = createRawContact();
        ContentValues values = new ContentValues();
        values.put(StructuredName.DISPLAY_NAME, "Mr.John Kevin von Smith, Jr.");
        values.putNull(StructuredName.GIVEN_NAME);
        values.putNull(StructuredName.FAMILY_NAME);
        insertStructuredName(rawContactId, values);
        assertStructuredName(rawContactId, "Mr.", "John", "Kevin", "von Smith", "Jr.");
    }

    public void testDisplayNameParsingWhenPartsSpecified() {
        long rawContactId = createRawContact();
        ContentValues values = new ContentValues();
        values.put(StructuredName.DISPLAY_NAME, "Mr.John Kevin von Smith, Jr.");
        values.put(StructuredName.FAMILY_NAME, "Johnson");
        insertStructuredName(rawContactId, values);

        assertStructuredName(rawContactId, null, null, null, "Johnson", null);
    }

    public void testSearchSnippetOrganization() throws Exception {
        long rawContactId = createRawContactWithName();

        // Some random data element
        insertEmail(rawContactId, "inc@corp.com");

        ContentValues values = new ContentValues();
        values.clear();
        values.put(Organization.COMPANY, "acmecorp");
        values.put(Organization.TITLE, "engineer");
        insertOrganization(rawContactId, values);

        // Add another matching organization
        values.put(Organization.COMPANY, "acmeinc");
        insertOrganization(rawContactId, values);

        // Add another non-matching organization
        values.put(Organization.COMPANY, "corpacme");
        insertOrganization(rawContactId, values);

        // And another data element
        insertEmail(rawContactId, "emca@corp.com", true, Email.TYPE_CUSTOM, "Custom");

        Uri filterUri = buildFilterUri("acme", true);

        values.clear();
        values.put(RawContacts._ID, rawContactId);
        values.put(SearchSnippetColumns.SNIPPET, "engineer, acmecorp");
// TODO after fix of snippets        assertStoredValues(filterUri, values);
    }

    public void testSearchSnippetEmail() throws Exception {
        long rawContactId = createRawContact();
        ContentValues values = new ContentValues();

        insertStructuredName(rawContactId, "John", "Doe");
        insertEmail(rawContactId, "acme@corp.com", true, Email.TYPE_CUSTOM, "Custom");

        Uri filterUri = buildFilterUri("acme", true);

        values.clear();
        values.put(RawContacts._ID, rawContactId);
        values.put(SearchSnippetColumns.SNIPPET, "acme@corp.com");
        assertStoredValues(filterUri, values);
    }

    private Uri buildFilterUri(String query, boolean deferredSnippeting) {
        Uri.Builder builder = RawContacts.CONTENT_FILTER_URI.buildUpon().appendPath(Uri.encode(query));
        if (deferredSnippeting) {
            builder.appendQueryParameter(ScContactsContract.DEFERRED_SNIPPETING, "1");
        }
        return builder.build();
    }

    public void testPhonesQuery() {

        ContentValues values = new ContentValues();
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.TIMES_CONTACTED, 54321);
        values.put(RawContacts.STARRED, 1);

        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        insertStructuredName(rawContactId, "Meghan", "Knox");
        Uri uri = insertPhoneNumber(rawContactId, "18004664411");
        long phoneId = ContentUris.parseId(uri);

        values.clear();
        values.put(Data._ID, phoneId);
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, "18004664411");
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        values.putNull(Phone.LABEL);
        values.put(RawContacts.DISPLAY_NAME_PRIMARY, "Meghan Knox");
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.TIMES_CONTACTED, 54321);
        values.put(RawContacts.STARRED, 1);

        assertStoredValues(ContentUris.withAppendedId(Phone.CONTENT_URI, phoneId), values);
        assertSelection(Phone.CONTENT_URI, values, Data._ID, phoneId);
    }

    public void testPhonesNormalizedNumber() {
        final long rawContactId = createRawContact();

        // Write both a number and a normalized number. Those should be written as-is
        final ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, "1234");
        values.put(Phone.NORMALIZED_NUMBER, "5678");
        values.put(Phone.TYPE, Phone.TYPE_HOME);

        final Uri dataUri = resolver.insert(Data.CONTENT_URI, values);

        // Check the lookup table.
        assertEquals(1, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "1234"), null, null));
        assertEquals(1, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "5678"), null, null));

        // Check the data table.
        assertStoredValues(dataUri, cv(Phone.NUMBER, "1234", Phone.NORMALIZED_NUMBER, "5678"));

        // Replace both in an UPDATE
        values.clear();
        values.put(Phone.NUMBER, "4321");
        values.put(Phone.NORMALIZED_NUMBER, "8765");
        resolver.update(dataUri, values, null, null);
        assertEquals(0, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "1234"), null, null));
        assertEquals(1, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "4321"), null, null));
        assertEquals(0, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "5678"), null, null));
        assertEquals(1, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "8765"), null, null));

        assertStoredValues(dataUri, cv(Phone.NUMBER, "4321", Phone.NORMALIZED_NUMBER, "8765"));

        // Replace only NUMBER ==> NORMALIZED_NUMBER will be inferred (we test that by making
        // sure the old manual value can not be found anymore)
        values.clear();
        values.put(Phone.NUMBER, "+1-800-466-5432");
        resolver.update(dataUri, values, null, null);
        assertEquals(1, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "+1-800-466-5432"), null, null));
        assertEquals(0, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "8765"), null, null));

        assertStoredValues(dataUri, cv(Phone.NUMBER, "+1-800-466-5432", Phone.NORMALIZED_NUMBER, "+18004665432"));

        // Replace only NORMALIZED_NUMBER ==> call is ignored, things will be unchanged
        values.clear();
        values.put(Phone.NORMALIZED_NUMBER, "8765");
        resolver.update(dataUri, values, null, null);
        assertEquals(1, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "+1-800-466-5432"), null, null));
        assertEquals(0, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "8765"), null, null));

        assertStoredValues(dataUri, cv(Phone.NUMBER, "+1-800-466-5432", Phone.NORMALIZED_NUMBER, "+18004665432"));

        // Replace NUMBER with an "invalid" number which can't be normalized.  It should clear
        // NORMALIZED_NUMBER.

        // 1. Set 999 to NORMALIZED_NUMBER explicitly.
        values.clear();
        values.put(Phone.NUMBER, "888");
        values.put(Phone.NORMALIZED_NUMBER, "999");
        resolver.update(dataUri, values, null, null);

        assertEquals(1, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "999"), null, null));

        assertStoredValues(dataUri, cv(Phone.NUMBER, "888", Phone.NORMALIZED_NUMBER, "999"));

        // 2. Set an invalid number to NUMBER.
        values.clear();
        values.put(Phone.NUMBER, "1");
        resolver.update(dataUri, values, null, null);

        assertEquals(0, getCount(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, "999"), null, null));

        // Change for silent contacts: Due to an adaption in data row handle for phone numbers the
        // normalized number is either a E.164 number or a simple normalized number, i.e. without
        // separators. In this case normalized_number is thus also "1" and not "null"
        assertStoredValues(dataUri, cv(Phone.NUMBER, "1", Phone.NORMALIZED_NUMBER, "1"));
    }


    public void testPhonesFilterQuery() {
        testPhonesFilterQueryInter(Phone.CONTENT_FILTER_URI);
    }

    /**
     * A convenient method for {@link #testPhonesFilterQuery()} and
     * {@link #testCallablesFilterQuery()}.
     *
     * This confirms if both URIs return identical results for phone-only contacts and
     * appropriately different results for contacts with sip addresses.
     *
     * @param baseFilterUri Either {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Phone#CONTENT_FILTER_URI} or
     * {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Callable#CONTENT_FILTER_URI}.
     */
    private void testPhonesFilterQueryInter(Uri baseFilterUri) {
        assertTrue("Unsupported Uri (" + baseFilterUri + ")",
                Phone.CONTENT_FILTER_URI.equals(baseFilterUri)
                        || Callable.CONTENT_FILTER_URI.equals(baseFilterUri));

        final long rawContactId1 = createRawContactWithName("Hot", "Tamale" /*, ACCOUNT_1 */);  // TODO account not supported
        insertPhoneNumber(rawContactId1, "1-800-466-4411");

        final long rawContactId2 = createRawContactWithName("Chilled", "Guacamole" /*, ACCOUNT_2*/);
        insertPhoneNumber(rawContactId2, "1-800-466-5432");
        insertPhoneNumber(rawContactId2, "0@example.com", false, Phone.TYPE_CUSTOM);
        insertPhoneNumber(rawContactId2, "1@example.com", false, Phone.TYPE_CUSTOM);
        
        final Uri filterUri1 = Uri.withAppendedPath(baseFilterUri, "tamale");
        ContentValues values = new ContentValues();
        values.put(RawContacts.DISPLAY_NAME_PRIMARY, "Hot Tamale");
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, "1-800-466-4411");
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        values.putNull(Phone.LABEL);
        assertStoredValuesWithProjection(filterUri1, values);

        final Uri filterUri2 = Uri.withAppendedPath(baseFilterUri, "1-800-GOOG-411");
        assertStoredValues(filterUri2, values);

        final Uri filterUri3 = Uri.withAppendedPath(baseFilterUri, "18004664");
        assertStoredValues(filterUri3, values);

        final Uri filterUri4 = Uri.withAppendedPath(baseFilterUri, "encilada");
        assertEquals(0, getCount(filterUri4, null, null));

        final Uri filterUri5 = Uri.withAppendedPath(baseFilterUri, "*");
        assertEquals(0, getCount(filterUri5, null, null));

        ContentValues values1 = new ContentValues();
        values1.put(RawContacts.DISPLAY_NAME_PRIMARY, "Chilled Guacamole");
        values1.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values1.put(Phone.NUMBER, "1-800-466-5432");
        values1.put(Phone.TYPE, Phone.TYPE_HOME);
        values1.putNull(Phone.LABEL);

        ContentValues values2 = new ContentValues();
        values2.put(RawContacts.DISPLAY_NAME_PRIMARY, "Chilled Guacamole");
        values2.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values2.put(Phone.NUMBER, "0@example.com");
        values2.put(Phone.TYPE, Phone.TYPE_CUSTOM);
        values2.putNull(Phone.LABEL);

        ContentValues values3 = new ContentValues();
        values3.put(RawContacts.DISPLAY_NAME_PRIMARY, "Chilled Guacamole");
        values3.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values3.put(Phone.NUMBER, "1@example.com");
        values3.put(Phone.TYPE, Phone.TYPE_CUSTOM);
        values3.putNull(Phone.LABEL);

        final Uri filterUri6 = Uri.withAppendedPath(baseFilterUri, "Chilled");
        assertStoredValues(filterUri6, values1, values2, values3);

        // Insert a SIP address. From here, Phone URI and Callable URI may return different results
        // than each other.
        insertSipAddress(rawContactId1, "sip_hot_tamale@example.com");
        insertSipAddress(rawContactId1, "sip:sip_hot@example.com");

        final Uri filterUri7 = Uri.withAppendedPath(baseFilterUri, "sip_hot");
        final Uri filterUri8 = Uri.withAppendedPath(baseFilterUri, "sip_hot_tamale");
        if (Callable.CONTENT_FILTER_URI.equals(baseFilterUri)) {
            ContentValues values4 = new ContentValues();
            values4.put(RawContacts.DISPLAY_NAME_PRIMARY, "Hot Tamale");
            values4.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
            values4.put(SipAddress.SIP_ADDRESS, "sip_hot_tamale@example.com");

            ContentValues values5 = new ContentValues();
            values5.put(RawContacts.DISPLAY_NAME_PRIMARY, "Hot Tamale");
            values5.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
            values5.put(SipAddress.SIP_ADDRESS, "sip:sip_hot@example.com");
            assertStoredValues(filterUri1, values, values4, values5);

            assertStoredValues(filterUri7, values4, values5);
            assertStoredValues(filterUri8, values4);
        } else {
            // Sip address should not affect Phone URI.
            assertStoredValuesWithProjection(filterUri1, values);
            assertEquals(0, getCount(filterUri7, null, null));
        }

        // Sanity test. Run tests for "Chilled Guacamole" again and see nothing changes
        // after the Sip address being inserted.
        assertStoredValues(filterUri2, values);
        assertStoredValues(filterUri3, values);
        assertEquals(0, getCount(filterUri4, null, null));
        assertEquals(0, getCount(filterUri5, null, null));
        assertStoredValues(filterUri6, values1, values2, values3 );
    }

    public void testPhonesFilterSearchParams() {
        final long rid1 = createRawContactWithName("Dad", null);
        insertPhoneNumber(rid1, "123-456-7890");

        final long rid2 = createRawContactWithName("Mam", null);
        insertPhoneNumber(rid2, "323-123-4567");

        // By default, "dad" will match both the display name and the phone number.
        // Because "dad" is "323" after the dialpad conversion, it'll match "Mam" too.
        assertStoredValues(
                Phone.CONTENT_FILTER_URI.buildUpon().appendPath("dad").build(),
                cv(Phone.DISPLAY_NAME, "Dad", Phone.NUMBER, "123-456-7890"),
                cv(Phone.DISPLAY_NAME, "Mam", Phone.NUMBER, "323-123-4567")
                );
        assertStoredValues(
                Phone.CONTENT_FILTER_URI.buildUpon().appendPath("dad")
                    .appendQueryParameter(Phone.SEARCH_PHONE_NUMBER_KEY, "0")
                    .build(),
                cv(Phone.DISPLAY_NAME, "Dad", Phone.NUMBER, "123-456-7890")
                );

        assertStoredValues(
                Phone.CONTENT_FILTER_URI.buildUpon().appendPath("dad")
                    .appendQueryParameter(Phone.SEARCH_DISPLAY_NAME_KEY, "0")
                    .build(),
                cv(Phone.DISPLAY_NAME, "Mam", Phone.NUMBER, "323-123-4567")
                );
        assertStoredValues(
                Phone.CONTENT_FILTER_URI.buildUpon().appendPath("dad")
                        .appendQueryParameter(Phone.SEARCH_DISPLAY_NAME_KEY, "0")
                        .appendQueryParameter(Phone.SEARCH_PHONE_NUMBER_KEY, "0")
                        .build()
        );
    }

    public void testPhoneLookup() {
        ContentValues values = new ContentValues();
        values.put(RawContacts.CUSTOM_RINGTONE, "d");

        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        insertStructuredName(rawContactId, "Hot", "Tamale");
        insertPhoneNumber(rawContactId, "18004664411");

        // We'll create two lookup records, 18004664411 and +18004664411, and the below lookup
        // will match both.

        Uri lookupUri1 = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "8004664411");

        values.clear();
        values.put(PhoneLookup._ID, rawContactId);      // we don't use queryContactId(rawContactId), no aggregated Contact
        values.put(PhoneLookup.DISPLAY_NAME, "Hot Tamale");
        values.put(PhoneLookup.NUMBER, "18004664411");
        values.put(PhoneLookup.TYPE, Phone.TYPE_HOME);
        values.putNull(PhoneLookup.LABEL);
        values.put(PhoneLookup.CUSTOM_RINGTONE, "d");
        assertStoredValues(lookupUri1, null, null, new ContentValues[] {values, values});

        // In the context that 8004664411 is a valid number, "4664411" as a
        // call id should  match to both "8004664411" and "+18004664411".
        Uri lookupUri2 = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "4664411");
        assertEquals(2, getCount(lookupUri2, null, null));

        // A wrong area code 799 vs 800 should not be matched
        lookupUri2 = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "7994664411");
        assertEquals(0, getCount(lookupUri2, null, null));
    }

    public void testIntlPhoneLookupUseCases() {
        // Checks the logic that relies on phone_number_compare_loose(Gingerbread) as a fallback
        //for phone number lookups.
        String fullNumber = "01197297427289";

        ContentValues values = new ContentValues();
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        long rawContactId = ContentUris.parseId(resolver.insert(RawContacts.CONTENT_URI, values));
        insertStructuredName(rawContactId, "Senor", "Chang");
        insertPhoneNumber(rawContactId, fullNumber);

        // Full number should definitely match.
        assertEquals(2, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, fullNumber), null, null));

        // Shorter (local) number with 0 prefix should also match.
        assertEquals(2, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "097427289"), null, null));

        // Number with international (+972) prefix should also match.
        assertEquals(1, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "+97297427289"), null, null));

        // Same shorter number with dashes should match.
        assertEquals(2, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "09-742-7289"), null, null));

        // Same shorter number with spaces should match.
        assertEquals(2, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "09 742 7289"), null, null));

        // Some other number should not match.
        assertEquals(0, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "049102395"), null, null));
    }

    public void testPhoneLookupB5252190() {
        // Test cases from b/5252190
        String storedNumber = "796010101";

        ContentValues values = new ContentValues();
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        long rawContactId = ContentUris.parseId(resolver.insert(RawContacts.CONTENT_URI, values));
        insertStructuredName(rawContactId, "Senor", "Chang");
        insertPhoneNumber(rawContactId, storedNumber);

        assertEquals(1, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "0796010101"), null, null));

        assertEquals(1, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "+48796010101"), null, null));

        assertEquals(1, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "48796010101"), null, null));

        assertEquals(1, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "4-879-601-0101"), null, null));

        assertEquals(1, getCount(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "4 879 601 0101"), null, null));
    }

    
    public void testPhoneUpdate() {
        ContentValues values = new ContentValues();
        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        insertStructuredName(rawContactId, "Hot", "Tamale");
        Uri phoneUri = insertPhoneNumber(rawContactId, "18004664411");

        Uri lookupUri1 = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "8004664411");
        Uri lookupUri2 = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, "8004664422");
        assertEquals(2, getCount(lookupUri1, null, null));
        assertEquals(0, getCount(lookupUri2, null, null));

        values.clear();
        values.put(Phone.NUMBER, "18004664422");
        resolver.update(phoneUri, values, null, null);

        assertEquals(0, getCount(lookupUri1, null, null));
        assertEquals(2, getCount(lookupUri2, null, null));

        // Setting number to null will remove the phone lookup record
        values.clear();
        values.putNull(Phone.NUMBER);
        resolver.update(phoneUri, values, null, null);

        assertEquals(0, getCount(lookupUri1, null, null));
        assertEquals(0, getCount(lookupUri2, null, null));

        // Let's restore that phone lookup record
        values.clear();
        values.put(Phone.NUMBER, "18004664422");
        resolver.update(phoneUri, values, null, null);
        assertEquals(0, getCount(lookupUri1, null, null));
        assertEquals(2, getCount(lookupUri2, null, null));
        assertNetworkNotified(true);
    }

    /** Tests if {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Callable#CONTENT_URI} returns both phones and sip addresses. */
    public void testCallablesQuery() {
        long rawContactId1 = createRawContactWithName("Meghan", "Knox");
        long phoneId1 = ContentUris.parseId(insertPhoneNumber(rawContactId1, "18004664411"));
 
        long rawContactId2 = createRawContactWithName("John", "Doe");
        long sipAddressId2 = ContentUris.parseId(insertSipAddress(rawContactId2, "sip@example.com"));

        ContentValues values1 = new ContentValues();
        values1.put(Data._ID, phoneId1);
        values1.put(Data.RAW_CONTACT_ID, rawContactId1);
        values1.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values1.put(Phone.NUMBER, "18004664411");
        values1.put(Phone.TYPE, Phone.TYPE_HOME);
        values1.putNull(Phone.LABEL);
        values1.put(RawContacts.DISPLAY_NAME_PRIMARY, "Meghan Knox");

        ContentValues values2 = new ContentValues();
        values2.put(Data._ID, sipAddressId2);
        values2.put(Data.RAW_CONTACT_ID, rawContactId2);
        values2.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
        values2.put(SipAddress.SIP_ADDRESS, "sip@example.com");
        values2.put(RawContacts.DISPLAY_NAME_PRIMARY, "John Doe");

        assertEquals(2, getCount(Callable.CONTENT_URI, null, null));
        assertStoredValues(Callable.CONTENT_URI, values1, values2);
    }

    public void testCallablesFilterQuery() {
        testPhonesFilterQueryInter(Callable.CONTENT_FILTER_URI);
    }

    public void testEmailsQuery() {
        ContentValues values = new ContentValues();
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.TIMES_CONTACTED, 54321);
        values.put(RawContacts.STARRED, 1);

        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        final long rawContactId = ContentUris.parseId(rawContactUri);

        insertStructuredName(rawContactId, "Meghan", "Knox");
        final Uri emailUri = insertEmail(rawContactId, "meghan@acme.com");
        final long emailId = ContentUris.parseId(emailUri);

        values.clear();
        values.put(Data._ID, emailId);
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.DATA, "meghan@acme.com");
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.putNull(Email.LABEL);
        values.put(RawContacts.DISPLAY_NAME_PRIMARY, "Meghan Knox");
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.TIMES_CONTACTED, 54321);
        values.put(RawContacts.STARRED, 1);

        assertStoredValues(Email.CONTENT_URI, values);
        assertStoredValues(ContentUris.withAppendedId(Email.CONTENT_URI, emailId), values);
        assertSelection(Email.CONTENT_URI, values, Data._ID, emailId);

        // Check if the provider detects duplicated email addresses.
        final Uri emailUri2 = insertEmail(rawContactId, "meghan@acme.com");
        final long emailId2 = ContentUris.parseId(emailUri2);
        final ContentValues values2 = new ContentValues(values);
        values2.put(Data._ID, emailId2);

        final Uri dedupeUri = Email.CONTENT_URI.buildUpon()
                .appendQueryParameter(ScContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
                .build();

        // URI with ID should return a correct result.
        assertStoredValues(ContentUris.withAppendedId(Email.CONTENT_URI, emailId), values);
        assertStoredValues(ContentUris.withAppendedId(dedupeUri, emailId), values);
        assertStoredValues(ContentUris.withAppendedId(Email.CONTENT_URI, emailId2), values2);
        assertStoredValues(ContentUris.withAppendedId(dedupeUri, emailId2), values2);

        assertStoredValues(Email.CONTENT_URI, values, values2);

        // If requested to remove duplicates, the query should return just one result,
        // whose _ID won't be deterministic.
        values.remove(Data._ID);
        assertStoredValues(dedupeUri, values);
    }

    public void testEmailsLookupQuery() {
        long rawContactId = createRawContactWithName("Hot", "Tamale");
        insertEmail(rawContactId, "tamale@acme.com");

        Uri filterUri1 = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, "tamale@acme.com");
        ContentValues values = new ContentValues();
        values.put(RawContacts.DISPLAY_NAME_PRIMARY, "Hot Tamale");
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.DATA, "tamale@acme.com");
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.putNull(Email.LABEL);
        assertStoredValues(filterUri1, values);

        Uri filterUri2 = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, "Ta<TaMale@acme.com>");
        assertStoredValues(filterUri2, values);

        Uri filterUri3 = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, "encilada@acme.com");
        assertEquals(0, getCount(filterUri3, null, null));
    }

    public void testEmailsFilterQuery() {
        long rawContactId1 = createRawContactWithName("Hot", "Tamale");
        insertEmail(rawContactId1, "tamale@acme.com");
        insertEmail(rawContactId1, "tamale@acme.com");

        long rawContactId2 = createRawContactWithName("Hot", "Tamale");
        insertEmail(rawContactId2, "tamale@acme.com");

        Uri filterUri1 = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, "tam");
        ContentValues values = new ContentValues();
        values.put(RawContacts.DISPLAY_NAME_PRIMARY, "Hot Tamale");
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.DATA, "tamale@acme.com");
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.putNull(Email.LABEL);
        assertStoredValuesWithProjection(filterUri1, values);

        Uri filterUri2 = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, "hot");
        assertStoredValuesWithProjection(filterUri2, values);

        Uri filterUri3 = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, "hot tamale");
        assertStoredValuesWithProjection(filterUri3, values);

        Uri filterUri4 = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, "tamale@acme");
        assertStoredValuesWithProjection(filterUri4, values);

        Uri filterUri5 = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, "encilada");
        assertEquals(0, getCount(filterUri5, null, null));
    }

    /**
     * Tests if ContactsProvider2 returns addresses according to registration order.
     */
    public void testEmailFilterDefaultSortOrder() {
        long rawContactId1 = createRawContact();
        insertEmail(rawContactId1, "address1@email.com");
        insertEmail(rawContactId1, "address2@email.com");
        insertEmail(rawContactId1, "address3@email.com");
        ContentValues v1 = new ContentValues();
        v1.put(Email.ADDRESS, "address1@email.com");
        ContentValues v2 = new ContentValues();
        v2.put(Email.ADDRESS, "address2@email.com");
        ContentValues v3 = new ContentValues();
        v3.put(Email.ADDRESS, "address3@email.com");

        Uri filterUri = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, "address");
        assertStoredValuesOrderly(filterUri, new ContentValues[]{v1, v2, v3});
    }

    /**
     * Tests if ContactsProvider returns primary addresses before the other addresses.
     */
    public void testEmailFilterPrimaryAddress() {
        long rawContactId1 = createRawContact();
        insertEmail(rawContactId1, "address1@email.com");
        insertEmail(rawContactId1, "address2@email.com", true);
        ContentValues v1 = new ContentValues();
        v1.put(Email.ADDRESS, "address1@email.com");
        ContentValues v2 = new ContentValues();
        v2.put(Email.ADDRESS, "address2@email.com");

        Uri filterUri = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, "address");
        assertStoredValuesOrderly(filterUri, new ContentValues[] { v2, v1 });
    }

    public void testPostalsQuery() {
        long rawContactId = createRawContactWithName("Alice", "Nextore");
        Uri dataUri = insertPostalAddress(rawContactId, "1600 Amphiteatre Ave, Mountain View");
        final long dataId = ContentUris.parseId(dataUri);

        ContentValues values = new ContentValues();
        values.put(Data._ID, dataId);
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        values.put(StructuredPostal.FORMATTED_ADDRESS, "1600 Amphiteatre Ave, Mountain View");
        values.put(RawContacts.DISPLAY_NAME_PRIMARY, "Alice Nextore");

        assertStoredValues(StructuredPostal.CONTENT_URI, values);
        assertStoredValues(ContentUris.withAppendedId(StructuredPostal.CONTENT_URI, dataId), values);
        assertSelection(StructuredPostal.CONTENT_URI, values, Data._ID, dataId);

        // Check if the provider detects duplicated addresses.
        Uri dataUri2 = insertPostalAddress(rawContactId, "1600 Amphiteatre Ave, Mountain View");
        final long dataId2 = ContentUris.parseId(dataUri2);
        final ContentValues values2 = new ContentValues(values);
        values2.put(Data._ID, dataId2);

        final Uri dedupeUri = StructuredPostal.CONTENT_URI.buildUpon()
                .appendQueryParameter(ScContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
                .build();

        // URI with ID should return a correct result.
        assertStoredValues(ContentUris.withAppendedId(StructuredPostal.CONTENT_URI, dataId), values);
        assertStoredValues(ContentUris.withAppendedId(dedupeUri, dataId), values);
        assertStoredValues(ContentUris.withAppendedId(StructuredPostal.CONTENT_URI, dataId2),values2);
        assertStoredValues(ContentUris.withAppendedId(dedupeUri, dataId2), values2);

        assertStoredValues(StructuredPostal.CONTENT_URI, new ContentValues[] {values, values2});

        // If requested to remove duplicates, the query should return just one result,
        // whose _ID won't be deterministic.
        values.remove(Data._ID);
        assertStoredValues(dedupeUri, values);
    }

    // Stream item query test cases.
    public void testQueryStreamItemsByRawContactId() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        insertStreamItem(rawContactId, values, null);
        assertStoredValues(
                Uri.withAppendedPath(
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                        RawContacts.StreamItems.CONTENT_DIRECTORY),
                values);
    }

    public void testQueryStreamItems() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        insertStreamItem(rawContactId, values, null);
        assertStoredValues(StreamItems.CONTENT_URI, values);
    }

    public void testQueryStreamItemsWithSelection() {
        long rawContactId = createRawContact();
        ContentValues firstValues = buildGenericStreamItemValues();
        insertStreamItem(rawContactId, firstValues, null);

        ContentValues secondValues = buildGenericStreamItemValues();
        secondValues.put(StreamItems.TEXT, "Goodbye world");
        insertStreamItem(rawContactId, secondValues, null);

        // Select only the first stream item.
        assertStoredValues(StreamItems.CONTENT_URI, StreamItems.TEXT + "=?",
                new String[]{"Hello world"}, firstValues);

        // Select only the second stream item.
        assertStoredValues(StreamItems.CONTENT_URI, StreamItems.TEXT + "=?",
                new String[]{"Goodbye world"}, secondValues);
    }

    public void testQueryStreamItemById() {
        long rawContactId = createRawContact();
        ContentValues firstValues = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, firstValues, null);
        long firstStreamItemId = ContentUris.parseId(resultUri);

        ContentValues secondValues = buildGenericStreamItemValues();
        secondValues.put(StreamItems.TEXT, "Goodbye world");
        resultUri = insertStreamItem(rawContactId, secondValues, null);
        long secondStreamItemId = ContentUris.parseId(resultUri);

        // Select only the first stream item.
        assertStoredValues(ContentUris.withAppendedId(StreamItems.CONTENT_URI, firstStreamItemId), firstValues);

        // Select only the second stream item.
        assertStoredValues(ContentUris.withAppendedId(StreamItems.CONTENT_URI, secondStreamItemId), secondValues);
    }

    // Stream item photo insertion + query test cases.

    public void testQueryStreamItemPhotoWithSelection() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();

        Uri resultUri = insertStreamItem(rawContactId, values, null);
        long streamItemId = ContentUris.parseId(resultUri);

        ContentValues photo1Values = buildGenericStreamItemPhotoValues(1);
        insertStreamItemPhoto(streamItemId, photo1Values, null);
        photo1Values.remove(StreamItemPhotos.PHOTO);  // Removed during processing.
        ContentValues photo2Values = buildGenericStreamItemPhotoValues(2);
        insertStreamItemPhoto(streamItemId, photo2Values, null);

        // Select only the first photo.
        assertStoredValues(StreamItems.CONTENT_PHOTO_URI, StreamItemPhotos.SORT_INDEX + "=?", new String[]{"1"}, photo1Values);
    }

    public void testQueryStreamItemPhotoByStreamItemId() {
        long rawContactId = createRawContact();

        // Insert a first stream item.
        ContentValues firstValues = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, firstValues, null);
        long firstStreamItemId = ContentUris.parseId(resultUri);

        // Insert a second stream item.
        ContentValues secondValues = buildGenericStreamItemValues();
        resultUri = insertStreamItem(rawContactId, secondValues, null);
        long secondStreamItemId = ContentUris.parseId(resultUri);

        // Add a photo to the first stream item.
        ContentValues photo1Values = buildGenericStreamItemPhotoValues(1);
        insertStreamItemPhoto(firstStreamItemId, photo1Values, null);
        photo1Values.remove(StreamItemPhotos.PHOTO);  // Removed during processing.

        // Add a photo to the second stream item.
        ContentValues photo2Values = buildGenericStreamItemPhotoValues(1);
        photo2Values.put(StreamItemPhotos.PHOTO, loadPhotoFromResource(R.drawable.ic_launcher, PhotoSize.ORIGINAL));
        insertStreamItemPhoto(secondStreamItemId, photo2Values, null);
        photo2Values.remove(StreamItemPhotos.PHOTO);  // Removed during processing.

        // Select only the photos from the second stream item.
        assertStoredValues(Uri.withAppendedPath(
                ContentUris.withAppendedId(StreamItems.CONTENT_URI, secondStreamItemId),
                StreamItems.StreamItemPhotos.CONTENT_DIRECTORY), photo2Values);
    }

    public void testQueryStreamItemPhotoByStreamItemPhotoId() {
        long rawContactId = createRawContact();

        // Insert a first stream item.
        ContentValues firstValues = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, firstValues, null);
        long firstStreamItemId = ContentUris.parseId(resultUri);

        // Insert a second stream item.
        ContentValues secondValues = buildGenericStreamItemValues();
        resultUri = insertStreamItem(rawContactId, secondValues, null);
        long secondStreamItemId = ContentUris.parseId(resultUri);

        // Add a photo to the first stream item.
        ContentValues photo1Values = buildGenericStreamItemPhotoValues(1);
        resultUri = insertStreamItemPhoto(firstStreamItemId, photo1Values, null);
        long firstPhotoId = ContentUris.parseId(resultUri);
        photo1Values.remove(StreamItemPhotos.PHOTO);  // Removed during processing.

        // Add a photo to the second stream item.
        ContentValues photo2Values = buildGenericStreamItemPhotoValues(1);
        photo2Values.put(StreamItemPhotos.PHOTO, loadPhotoFromResource(R.drawable.ic_launcher, PhotoSize.ORIGINAL));
        resultUri = insertStreamItemPhoto(secondStreamItemId, photo2Values, null);
        long secondPhotoId = ContentUris.parseId(resultUri);
        photo2Values.remove(StreamItemPhotos.PHOTO);  // Removed during processing.

        // Select the first photo.
        assertStoredValues(ContentUris.withAppendedId(
                Uri.withAppendedPath(
                        ContentUris.withAppendedId(StreamItems.CONTENT_URI, firstStreamItemId),
                        StreamItems.StreamItemPhotos.CONTENT_DIRECTORY),
                firstPhotoId),
                photo1Values);

        // Select the second photo.
        assertStoredValues(ContentUris.withAppendedId(
                Uri.withAppendedPath(
                        ContentUris.withAppendedId(StreamItems.CONTENT_URI, secondStreamItemId),
                        StreamItems.StreamItemPhotos.CONTENT_DIRECTORY),
                secondPhotoId),
                photo2Values);
    }

    public void testInsertStreamItemWithContentValues() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        values.put(StreamItems.RAW_CONTACT_ID, rawContactId);
        resolver.insert(StreamItems.CONTENT_URI, values);
        assertStoredValues(Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.StreamItems.CONTENT_DIRECTORY), values);
    }

    public void testInsertStreamItemOverLimit() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        values.put(StreamItems.RAW_CONTACT_ID, rawContactId);

        List<Long> streamItemIds = new ArrayList<>();

        // Insert MAX + 1 stream items.
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            values.put(StreamItems.TIMESTAMP, baseTime + i);
            Uri resultUri = resolver.insert(StreamItems.CONTENT_URI, values);
            streamItemIds.add(ContentUris.parseId(resultUri));
        }
        Long doomedStreamItemId = streamItemIds.get(0);

        // There should only be MAX items.  The oldest one should have been cleaned up.
        Cursor c = resolver.query(
                Uri.withAppendedPath(
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                        RawContacts.StreamItems.CONTENT_DIRECTORY),
                new String[]{StreamItems._ID}, null, null, null);
        try {
            while(c.moveToNext()) {
                long streamItemId = c.getLong(0);
                streamItemIds.remove(streamItemId);
            }
        } finally {
            c.close();
        }

        assertEquals(1, streamItemIds.size());
        assertEquals(doomedStreamItemId, streamItemIds.get(0));
    }

    public void testInsertStreamItemOlderThanOldestInLimit() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        values.put(StreamItems.RAW_CONTACT_ID, rawContactId);

        // Insert MAX stream items.
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            values.put(StreamItems.TIMESTAMP, baseTime + i);
            Uri resultUri = resolver.insert(StreamItems.CONTENT_URI, values);
            assertNotSame("Expected non-0 stream item ID to be inserted", 0L, ContentUris.parseId(resultUri));
        }

        // Now try to insert a stream item that's older.  It should be deleted immediately
        // and return an ID of 0.
        values.put(StreamItems.TIMESTAMP, baseTime - 1);
        Uri resultUri = resolver.insert(StreamItems.CONTENT_URI, values);
        assertEquals(0L, ContentUris.parseId(resultUri));
    }

    
    // Stream item update test cases.

    public void testUpdateStreamItemById() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, values, null);

        long streamItemId = ContentUris.parseId(resultUri);
        values.put(StreamItems.TEXT, "Goodbye world");
        resolver.update(ContentUris.withAppendedId(StreamItems.CONTENT_URI, streamItemId), values, null, null);

        assertStoredValues(Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.StreamItems.CONTENT_DIRECTORY), values);
    }

    public void testUpdateStreamItemWithContentValues() {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, values, null);
        long streamItemId = ContentUris.parseId(resultUri);
        values.put(StreamItems._ID, streamItemId);
        values.put(StreamItems.TEXT, "Goodbye world");
        resolver.update(StreamItems.CONTENT_URI, values, null, null);
        assertStoredValues(Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.StreamItems.CONTENT_DIRECTORY), values);
    }

    // Stream item photo update test cases.

    public void testUpdateStreamItemPhotoById() throws IOException {
        long rawContactId = createRawContact();
        ContentValues values = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, values, null);
        long streamItemId = ContentUris.parseId(resultUri);
        
        // first load photo R.drawable.earth_normal
        ContentValues photoValues = buildGenericStreamItemPhotoValues(1);
        resultUri = insertStreamItemPhoto(streamItemId, photoValues, null);
        long streamItemPhotoId = ContentUris.parseId(resultUri);

        // update with photo R.drawable.nebula
        photoValues.put(StreamItemPhotos.PHOTO, loadPhotoFromResource(R.drawable.ic_launcher, PhotoSize.ORIGINAL));

        // Uri:  content://com.silentcircle.contacts2/stream_items/#/photo/#" -> STREAM_ITEMS_ID_PHOTOS_ID
        Uri photoUri =
                ContentUris.withAppendedId(
                        Uri.withAppendedPath(ContentUris.withAppendedId(StreamItems.CONTENT_URI, streamItemId),
                                StreamItems.StreamItemPhotos.CONTENT_DIRECTORY),
                        streamItemPhotoId);
        resolver.update(photoUri, photoValues, null, null);
        photoValues.remove(StreamItemPhotos.PHOTO);  // Removed during processing.
        assertStoredValues(photoUri, photoValues);

        // Check that the photo stored is the expected one.
        String displayPhotoUri = getStoredValue(photoUri, StreamItemPhotos.PHOTO_URI);
        EvenMoreAsserts.assertImageRawData(getContext(),
                loadPhotoFromResource(R.drawable.ic_launcher, PhotoSize.DISPLAY_PHOTO),
                resolver.openInputStream(Uri.parse(displayPhotoUri)));
    }

    public void testUpdateStreamItemPhotoWithContentValues() throws IOException {
        long rawContactId = createRawContact();

        ContentValues values = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, values, null);
        long streamItemId = ContentUris.parseId(resultUri);

        ContentValues photoValues = buildGenericStreamItemPhotoValues(1);
        resultUri = insertStreamItemPhoto(streamItemId, photoValues, null);
        long streamItemPhotoId = ContentUris.parseId(resultUri);

        photoValues.put(StreamItemPhotos._ID, streamItemPhotoId);
        photoValues.put(StreamItemPhotos.PHOTO, loadPhotoFromResource(R.drawable.ic_launcher, PhotoSize.ORIGINAL));

        // Uri:  content://com.silentcircle.contacts/stream_items/#/photo" -> STREAM_ITEMS_ID_PHOTOS
        Uri photoUri =
                Uri.withAppendedPath(
                        ContentUris.withAppendedId(StreamItems.CONTENT_URI, streamItemId),
                        StreamItems.StreamItemPhotos.CONTENT_DIRECTORY);
        resolver.update(photoUri, photoValues, null, null);
        photoValues.remove(StreamItemPhotos.PHOTO);  // Removed during processing.
        assertStoredValues(photoUri, photoValues);

        // Check that the photo stored is the expected one.
        String displayPhotoUri = getStoredValue(photoUri, StreamItemPhotos.PHOTO_URI);
        EvenMoreAsserts.assertImageRawData(getContext(),
                loadPhotoFromResource(R.drawable.ic_launcher, PhotoSize.DISPLAY_PHOTO),
                resolver.openInputStream(Uri.parse(displayPhotoUri)));
    }

    // Stream item deletion test cases.

    public void testDeleteStreamItemById() {
        long rawContactId = createRawContact();
        ContentValues firstValues = buildGenericStreamItemValues();
        Uri resultUri = insertStreamItem(rawContactId, firstValues, null);
        long firstStreamItemId = ContentUris.parseId(resultUri);

        ContentValues secondValues = buildGenericStreamItemValues();
        secondValues.put(StreamItems.TEXT, "Goodbye world");
        insertStreamItem(rawContactId, secondValues, null);

        // Delete the first stream item.
        resolver.delete(ContentUris.withAppendedId(StreamItems.CONTENT_URI, firstStreamItemId), null, null);

        // Check that only the second item remains.
        assertStoredValues(Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.StreamItems.CONTENT_DIRECTORY), secondValues);
    }

    public void testDeleteStreamItemWithSelection() {
        long rawContactId = createRawContact();
        ContentValues firstValues = buildGenericStreamItemValues();
        insertStreamItem(rawContactId, firstValues, null);

        ContentValues secondValues = buildGenericStreamItemValues();
        secondValues.put(StreamItems.TEXT, "Goodbye world");
        insertStreamItem(rawContactId, secondValues, null);

        // Delete the first stream item with a custom selection.
        resolver.delete(StreamItems.CONTENT_URI, StreamItems.TEXT + "=?", new String[]{"Hello world"});

        // Check that only the second item remains.
        assertStoredValues(Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.StreamItems.CONTENT_DIRECTORY), secondValues);
    }

    // Stream item photo deletion test cases.

    public void testDeleteStreamItemPhotoById() {
        long rawContactId = createRawContact();
        long streamItemId = ContentUris.parseId(insertStreamItem(rawContactId, buildGenericStreamItemValues(), null));
        long streamItemPhotoId = ContentUris.parseId(
                insertStreamItemPhoto(streamItemId, buildGenericStreamItemPhotoValues(0), null));

        resolver.delete(
                // Uri:  content://com.silentcircle.contacts/stream_items/#/photo/#" -> STREAM_ITEMS_ID_PHOTOS_ID
                ContentUris.withAppendedId(
                        Uri.withAppendedPath(ContentUris.withAppendedId(StreamItems.CONTENT_URI, streamItemId),
                                StreamItems.StreamItemPhotos.CONTENT_DIRECTORY),
                        streamItemPhotoId), null, null);

        Cursor c = resolver.query(StreamItems.CONTENT_PHOTO_URI,
                new String[]{StreamItemPhotos._ID},
                StreamItemPhotos.STREAM_ITEM_ID + "=?", new String[]{String.valueOf(streamItemId)},
                null);
        try {
            assertEquals("Expected photo to be deleted.", 0, c.getCount());
        } finally {
            c.close();
        }
    }

    public void testDeleteStreamItemPhotoWithSelection() {
        long rawContactId = createRawContact();
        long streamItemId = ContentUris.parseId(insertStreamItem(rawContactId, buildGenericStreamItemValues(), null));
        ContentValues firstPhotoValues = buildGenericStreamItemPhotoValues(0);
        ContentValues secondPhotoValues = buildGenericStreamItemPhotoValues(1);

        insertStreamItemPhoto(streamItemId, firstPhotoValues, null);
        firstPhotoValues.remove(StreamItemPhotos.PHOTO);  // Removed while processing.

        insertStreamItemPhoto(streamItemId, secondPhotoValues, null);
        Uri photoUri = Uri.withAppendedPath(
                ContentUris.withAppendedId(StreamItems.CONTENT_URI, streamItemId),
                StreamItems.StreamItemPhotos.CONTENT_DIRECTORY);
        resolver.delete(photoUri, StreamItemPhotos.SORT_INDEX + "=1", null);

        assertStoredValues(photoUri, firstPhotoValues);
    }

    public void testDeleteStreamItemsWhenRawContactDeleted() {
        long rawContactId = createRawContact();
        Uri streamItemUri = insertStreamItem(rawContactId,
                buildGenericStreamItemValues(), null);
        Uri streamItemPhotoUri = insertStreamItemPhoto(ContentUris.parseId(streamItemUri),
                        buildGenericStreamItemPhotoValues(0), null);
        resolver.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                null, null);

        ContentValues[] emptyValues = new ContentValues[0];

        // The stream item and its photo should be gone.
        assertStoredValues(streamItemUri, emptyValues);
        assertStoredValues(streamItemPhotoUri, emptyValues);
    }


    public void testGetPhotoUri() {
        ContentValues values = new ContentValues();
        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);
        insertStructuredName(rawContactId, "John", "Doe");
        long dataId = ContentUris.parseId(insertPhoto(rawContactId, R.drawable.ic_contact_picture_holo_dark));
        long photoFileId = getStoredLongValue(Data.CONTENT_URI, Data._ID + "=?",
                new String[]{String.valueOf(dataId)}, Photo.PHOTO_FILE_ID);

        String photoUri = ContentUris.withAppendedId(DisplayPhoto.CONTENT_URI, photoFileId).toString();
        
        assertStoredValue(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.PHOTO_URI, photoUri);
    }


    public void testInputStreamForPhoto() throws Exception {
        long rawContactId = createRawContact();
        Uri contactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        insertPhoto(rawContactId);
        Uri photoUri = Uri.parse(getStoredValue(contactUri, RawContacts.PHOTO_URI));
        Uri photoThumbnailUri = Uri.parse(getStoredValue(contactUri, RawContacts.PHOTO_THUMBNAIL_URI));

        // Check the thumbnail - disabled until SQLCipher implements
        EvenMoreAsserts.assertImageRawData(getContext(), loadTestPhoto(PhotoSize.THUMBNAIL),
                resolver.openInputStream(photoThumbnailUri));

        // Then check the display photo.  Note because we only inserted a small photo, but not a
        // display photo, this returns the thumbnail image itself, which was compressed at
        // the thumbnail compression rate, which is why we compare to
        // loadTestPhoto(PhotoSize.THUMBNAIL) rather than loadTestPhoto(PhotoSize.DISPLAY_PHOTO)
        // here.
        // (In other words, loadTestPhoto(PhotoSize.DISPLAY_PHOTO) returns the same photo as
        // loadTestPhoto(PhotoSize.THUMBNAIL), except it's compressed at a lower compression rate.)
        EvenMoreAsserts.assertImageRawData(getContext(), loadTestPhoto(PhotoSize.DISPLAY_PHOTO),
                resolver.openInputStream(photoUri));
    }


    public void testOpenDisplayPhotoForRawContactId() throws IOException {
        long rawContactId = createRawContactWithName();
        insertPhoto(rawContactId, R.drawable.ic_contact_picture_holo_dark);

        // uri=content://com.silentcircle.contacts/raw_contacts/#/display_photo
        Uri photoUri = RawContacts.CONTENT_URI.buildUpon()
                .appendPath(String.valueOf(rawContactId))
                .appendPath(RawContacts.DisplayPhoto.CONTENT_DIRECTORY).build();

        EvenMoreAsserts.assertImageRawData(getContext(),
                loadPhotoFromResource(R.drawable.ic_contact_picture_holo_dark, PhotoSize.DISPLAY_PHOTO),
                resolver.openInputStream(photoUri));
    }

    public void testUpdatePhoto() {
        ContentValues values = new ContentValues();
        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);
        insertStructuredName(rawContactId, "John", "Doe");

        Uri twigUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), 
                RawContacts.Photo.CONTENT_DIRECTORY);

        values.clear();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        values.putNull(Photo.PHOTO);
        Uri dataUri = resolver.insert(Data.CONTENT_URI, values);
        long photoId = ContentUris.parseId(dataUri);

        assertEquals(0, getCount(twigUri, null, null));

        values.clear();
        values.put(Photo.PHOTO, loadTestPhoto());
        resolver.update(dataUri, values, null, null);
        assertNetworkNotified(true);

        long twigId = getStoredLongValue(twigUri, Data._ID);
        assertEquals(photoId, twigId);
    }

    public void testUpdateRawContactDataPhoto() {
        // setup a contact with a null photo
        ContentValues values = new ContentValues();
        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        // setup a photo
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        values.putNull(Photo.PHOTO);

        // try to do an update before insert should return count == 0
        Uri dataUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.Data.CONTENT_DIRECTORY);
        assertEquals(0, resolver.update(dataUri, values, Data.MIMETYPE + "=?", new String[] {Photo.CONTENT_ITEM_TYPE}));

        resolver.insert(Data.CONTENT_URI, values);

        // save a photo to the db
        values.clear();
        values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        values.put(Photo.PHOTO, loadTestPhoto());
        assertEquals(1, resolver.update(dataUri, values, Data.MIMETYPE + "=?", new String[] {Photo.CONTENT_ITEM_TYPE}));

        // verify the photo
        Cursor storedPhoto = resolver.query(dataUri, new String[] {Photo.PHOTO},
                Data.MIMETYPE + "=?", new String[] {Photo.CONTENT_ITEM_TYPE}, null);
        storedPhoto.moveToFirst();
        MoreAsserts.assertEquals(loadTestPhoto(PhotoSize.THUMBNAIL), storedPhoto.getBlob(0));
        storedPhoto.close();
    }

    public void testOpenDisplayPhotoByPhotoUri() throws IOException {
        long rawContactId = createRawContactWithName();
        insertPhoto(rawContactId, R.drawable.ic_contact_picture_holo_dark);

        // Get the photo URI out and check the content.
        String photoUri = getStoredValue(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), RawContacts.PHOTO_URI);
        EvenMoreAsserts.assertImageRawData(getContext(),
                loadPhotoFromResource(R.drawable.ic_contact_picture_holo_dark, PhotoSize.DISPLAY_PHOTO),
                resolver.openInputStream(Uri.parse(photoUri)));
    }

    public void testPhotoUriForDisplayPhoto() {
        long rawContactId = createRawContactWithName();

        // Photo being inserted is larger than a thumbnail, so it will be stored as a file.
        long dataId = ContentUris.parseId(insertPhoto(rawContactId, R.drawable.ic_contact_picture_holo_dark));
        String photoFileId = getStoredValue(ContentUris.withAppendedId(Data.CONTENT_URI, dataId),
                Photo.PHOTO_FILE_ID);
        String photoUri = getStoredValue(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), RawContacts.PHOTO_URI);

        // Check that the photo URI differs from the thumbnail.
        String thumbnailUri = getStoredValue(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), RawContacts.PHOTO_THUMBNAIL_URI);
        assertFalse(photoUri.equals(thumbnailUri));

        // URI should be of the form display_photo/ID
        assertEquals(Uri.withAppendedPath(DisplayPhoto.CONTENT_URI, photoFileId).toString(), photoUri);
    }

//    public void testPhotoUriForThumbnailPhoto() throws IOException {
//        long rawContactId = createRawContactWithName();
//
//        // Photo being inserted is a thumbnail, so it will only be stored in a BLOB.  The photo URI
//        // will fall back to the thumbnail URI. -- TODO - this is not true for modified photo handling, maybe check this
//        insertPhoto(rawContactId, R.drawable.earth_small);
//        String photoUri = getStoredValue(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), RawContacts.PHOTO_URI);
//
//        // Check that the photo URI is equal to the thumbnail URI.
//        String thumbnailUri = getStoredValue(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
//                RawContacts.PHOTO_THUMBNAIL_URI);
//        assertEquals(photoUri, thumbnailUri);
//
//        // URI should be of the form contacts/ID/photo
//        assertEquals(Uri.withAppendedPath(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
//                RawContacts.Photo.CONTENT_DIRECTORY).toString(),
//                photoUri);
//
//        // Loading the photo URI content should get the thumbnail.
//        EvenMoreAsserts.assertImageRawData(getContext(),
//                loadPhotoFromResource(R.drawable.earth_small, PhotoSize.THUMBNAIL),
//                resolver.openInputStream(Uri.parse(photoUri)));
//    }

// TODO: Async 
//    public void testWriteNewPhotoToAssetFile() throws Exception {
//        long rawContactId = createRawContactWithName();
//
//        // Load in a huge photo.
//        final byte[] originalPhoto = loadPhotoFromResource(
//                R.drawable.earth_huge, PhotoSize.ORIGINAL);
//
//        // Write it out.
//        final Uri writeablePhotoUri = RawContacts.CONTENT_URI.buildUpon()
//                .appendPath(String.valueOf(rawContactId))
//                .appendPath(RawContacts.DisplayPhoto.CONTENT_DIRECTORY).build();
//        writePhotoAsync(writeablePhotoUri, originalPhoto);
//
//        // Check that the display photo and thumbnail have been set.
//        String photoUri = null;
//        for (int i = 0; i < 10 && photoUri == null; i++) {
//            // Wait a tick for the photo processing to occur.
//            Thread.sleep(100);
//            photoUri = getStoredValue(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
//                RawContacts.PHOTO_URI);
//        }
//
//        assertFalse(TextUtils.isEmpty(photoUri));
//        String thumbnailUri = getStoredValue(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
//                RawContacts.PHOTO_THUMBNAIL_URI);
//        assertFalse(TextUtils.isEmpty(thumbnailUri));
//        assertNotSame(photoUri, thumbnailUri);
//
//        // Check the content of the display photo and thumbnail.
//        EvenMoreAsserts.assertImageRawData(getContext(),
//                loadPhotoFromResource(R.drawable.earth_huge, PhotoSize.DISPLAY_PHOTO),
//                resolver.openInputStream(Uri.parse(photoUri)));
//        EvenMoreAsserts.assertImageRawData(getContext(),
//                loadPhotoFromResource(R.drawable.earth_huge, PhotoSize.THUMBNAIL),
//                resolver.openInputStream(Uri.parse(thumbnailUri)));
//    }
//
//    public void testWriteUpdatedPhotoToAssetFile() throws Exception {
//        long rawContactId = createRawContactWithName();
//
//        // Insert a large photo first.
//        insertPhoto(rawContactId, R.drawable.earth_large);
//        String largeEarthPhotoUri = getStoredValue(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), RawContacts.PHOTO_URI);
//
//        // Load in a huge photo.
//        byte[] originalPhoto = loadPhotoFromResource(R.drawable.earth_huge, PhotoSize.ORIGINAL);
//
//        // Write it out.
//        Uri writeablePhotoUri = RawContacts.CONTENT_URI.buildUpon()
//                .appendPath(String.valueOf(rawContactId))
//                .appendPath(RawContacts.DisplayPhoto.CONTENT_DIRECTORY).build();
//        writePhotoAsync(writeablePhotoUri, originalPhoto);
//
//        // Allow a second for processing to occur.
//        Thread.sleep(1000);
//
//        // Check that the display photo URI has been modified.
//        String hugeEarthPhotoUri = getStoredValue(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), RawContacts.PHOTO_URI);
//        assertFalse(hugeEarthPhotoUri.equals(largeEarthPhotoUri));
//
//        // Check the content of the display photo and thumbnail.
//        String hugeEarthThumbnailUri = getStoredValue(
//                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
//                RawContacts.PHOTO_THUMBNAIL_URI);
//        EvenMoreAsserts.assertImageRawData(getContext(),
//                loadPhotoFromResource(R.drawable.earth_huge, PhotoSize.DISPLAY_PHOTO),
//                resolver.openInputStream(Uri.parse(hugeEarthPhotoUri)));
//        EvenMoreAsserts.assertImageRawData(getContext(),
//                loadPhotoFromResource(R.drawable.earth_huge, PhotoSize.THUMBNAIL),
//                resolver.openInputStream(Uri.parse(hugeEarthThumbnailUri)));
//
//    }
//
//    private void writePhotoAsync(final Uri uri, final byte[] photoBytes) throws Exception {
//        AsyncTask<Object, Object, Object> task = new AsyncTask<Object, Object, Object>() {
//            @Override
//            protected Object doInBackground(Object... params) {
//                OutputStream os;
//                try {
//                    os = resolver.openOutputStream(uri, "rw");
//                    os.write(photoBytes);
//                    os.close();
//                    return null;
//                } catch (IOException ioe) {
//                    throw new RuntimeException(ioe);
//                }
//            }
//        };
//        task.execute((Object[])null).get();
//    }

// C
    public void testPhotoStoreCleanup() throws IOException {
        SynchronousContactsProvider provider = (SynchronousContactsProvider) mActor.mprovider;
        PhotoStore photoStore = provider.getPhotoStore();

        // Trigger an initial cleanup so another one won't happen while we're running this test.
        provider.cleanupPhotoStore();

        // Insert a couple of contacts with photos.
        long rawContactId1 = createRawContactWithName();
        long dataId1 = ContentUris.parseId(insertPhoto(rawContactId1, R.drawable.ic_contact_picture_holo_dark));
        long photoFileId1 =
                getStoredLongValue(ContentUris.withAppendedId(Data.CONTENT_URI, dataId1),
                        Photo.PHOTO_FILE_ID);

        long rawContactId2 = createRawContactWithName();
        long dataId2 = ContentUris.parseId(insertPhoto(rawContactId2, R.drawable.ic_contact_picture_holo_dark));
        long photoFileId2 =
                getStoredLongValue(ContentUris.withAppendedId(Data.CONTENT_URI, dataId2),
                        Photo.PHOTO_FILE_ID);

        // Update the second raw contact with a different photo.
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId2);
        values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        values.put(Photo.PHOTO, loadPhotoFromResource(R.drawable.ic_launcher, PhotoSize.ORIGINAL));
        assertEquals(1, resolver.update(Data.CONTENT_URI, values, Data._ID + "=?", new String[]{String.valueOf(dataId2)}));
        long replacementPhotoFileId =
                getStoredLongValue(ContentUris.withAppendedId(Data.CONTENT_URI, dataId2),
                        Photo.PHOTO_FILE_ID);

        // Insert a third raw contact that has a bogus photo file ID.
        long bogusFileId = 1234567;
        long rawContactId3 = createRawContactWithName();
        values.clear();
        values.put(Data.RAW_CONTACT_ID, rawContactId3);
        values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        values.put(Photo.PHOTO, loadPhotoFromResource(R.drawable.ic_contact_picture_holo_dark,
                PhotoSize.THUMBNAIL));
        values.put(Photo.PHOTO_FILE_ID, bogusFileId);
        values.put(DataRowHandlerForPhoto.SKIP_PROCESSING_KEY, true);
        resolver.insert(Data.CONTENT_URI, values);

        // Insert a fourth raw contact with a stream item that has a photo, then remove that photo
        // from the photo store.
        long rawContactId4 = createRawContactWithName();
        Uri streamItemUri = insertStreamItem(rawContactId4, buildGenericStreamItemValues(), null);
        long streamItemId = ContentUris.parseId(streamItemUri);

        Uri streamItemPhotoUri = insertStreamItemPhoto(streamItemId, buildGenericStreamItemPhotoValues(0), null);
        long streamItemPhotoFileId = getStoredLongValue(streamItemPhotoUri, "stream_item_photos." + StreamItemPhotos.PHOTO_FILE_ID);
        photoStore.remove(streamItemPhotoFileId);

        // Also insert a bogus photo that nobody is using.
        long bogusPhotoId = photoStore.insert(new PhotoProcessor(loadPhotoFromResource(
                R.drawable.ic_launcher, PhotoSize.ORIGINAL), 256, 96));

        // Manually trigger another cleanup in the provider.
        provider.cleanupPhotoStore();

        // The following things should have happened.

        // 1. Raw contact 1 and its photo remain unaffected.
        assertEquals(photoFileId1, (long) getStoredLongValue(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId1),
                RawContacts.PHOTO_FILE_ID));

        // 2. Raw contact 2 retains its new photo.  The old one is deleted from the photo store.
        assertEquals(replacementPhotoFileId, (long) getStoredLongValue(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId2),
                RawContacts.PHOTO_FILE_ID));
        assertNull(photoStore.get(photoFileId2));

        // 3. Raw contact 3 should have its photo file reference cleared.
        assertNull(getStoredValue(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId3),
                RawContacts.PHOTO_FILE_ID));

        // 4. The bogus photo that nobody was using should be cleared from the photo store.
        assertNull(photoStore.get(bogusPhotoId));

        // 5. The bogus stream item photo should be cleared from the stream item.
        assertStoredValues(Uri.withAppendedPath(
                ContentUris.withAppendedId(StreamItems.CONTENT_URI, streamItemId),
                StreamItems.StreamItemPhotos.CONTENT_DIRECTORY));
    }

    public void testGroupQuery() {
        long groupId1 = createGroup("e", "f");
        long groupId2 = createGroup("g", "h");
        Uri uri1 = Groups.CONTENT_URI;
        Uri uri2 = Groups.CONTENT_URI;
        assertEquals(2, getCount(uri1, null, null));  // two groups, no aaccount 
//        assertEquals(1, getCount(uri2, null, null));
        assertStoredValue(uri1, Groups._ID + "=" + groupId1, null, Groups._ID, groupId1) ;
        assertStoredValue(uri2, Groups._ID + "=" + groupId2, null, Groups._ID, groupId2) ;
    }

    public void testGroupInsert() {
        ContentValues values = new ContentValues();

        values.put(Groups.SOURCE_ID, "c");
        values.put(Groups.VERSION, 42);
        values.put(Groups.GROUP_VISIBLE, 1);
        values.put(Groups.TITLE, "d");
        values.put(Groups.TITLE_RES, 1234);
        values.put(Groups.NOTES, "e");
//        values.put(Groups.RES_PACKAGE, "f");
        values.put(Groups.SYSTEM_ID, "g");
        values.put(Groups.DELETED, 1);
        values.put(Groups.SYNC1, "h");
        values.put(Groups.SYNC2, "i");
        values.put(Groups.SYNC3, "j");
        values.put(Groups.SYNC4, "k");

        Uri rowUri = resolver.insert(Groups.CONTENT_URI, values);

        values.put(Groups.DIRTY, 1);
        assertStoredValues(rowUri, values);
    }

    public void testGroupCreationAfterMembershipInsert() {
        long rawContactId1 = createRawContact();
        Uri groupMembershipUri = insertGroupMembership(rawContactId1, "gsid1");

        long groupId = assertSingleGroup(NO_LONG, "gsid1", null);
        assertSingleGroupMembership(ContentUris.parseId(groupMembershipUri), rawContactId1, groupId, "gsid1");
    }

    public void testGroupReuseAfterMembershipInsert() {
        long rawContactId1 = createRawContact();
        long groupId1 = createGroup("gsid1", "title1");
        Uri groupMembershipUri = insertGroupMembership(rawContactId1, "gsid1");

        assertSingleGroup(groupId1, "gsid1", "title1");
        assertSingleGroupMembership(ContentUris.parseId(groupMembershipUri),
                rawContactId1, groupId1, "gsid1");
    }

    public void testGroupInsertFailureOnGroupIdConflict() {
        long rawContactId1 = createRawContact();
        long groupId1 = createGroup("gsid1", "title1");

        ContentValues values = new ContentValues();
        values.put(GroupMembership.RAW_CONTACT_ID, rawContactId1);
        values.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
        values.put(GroupMembership.GROUP_SOURCE_ID, "gsid1");
        values.put(GroupMembership.GROUP_ROW_ID, groupId1);
        try {
            resolver.insert(Data.CONTENT_URI, values);
            fail("the insert was expected to fail, but it succeeded");
        } catch (IllegalArgumentException e) {
            // this was expected
        }
    }

    public void testGroupSummaryQuery() {
        final long groupId1 = createGroup("sourceId1", "title1");
        final long groupId2 = createGroup("sourceId2", "title2");
        final long groupId3 = createGroup("sourceId3", "title3");

        // Prepare raw contact id not used at all, to test group summary uri won't be confused
        // with it.
        createRawContactWithName("firstName0", "lastName0");

        final long rawContactId1 = createRawContactWithName("firstName1", "lastName1");
        insertEmail(rawContactId1, "address1@email.com");
        insertGroupMembership(rawContactId1, groupId1);

        final long rawContactId2 = createRawContactWithName("firstName2", "lastName2");
        insertEmail(rawContactId2, "address2@email.com");
        insertPhoneNumber(rawContactId2, "222-222-2222");
        insertGroupMembership(rawContactId2, groupId1);

        ContentValues v1 = new ContentValues();
        v1.put(Groups._ID, groupId1);
        v1.put(Groups.TITLE, "title1");
        v1.put(Groups.SOURCE_ID, "sourceId1");
        v1.put(Groups.SUMMARY_COUNT, 2);
        v1.put(Groups.SUMMARY_WITH_PHONES, 1);

        ContentValues v2 = new ContentValues();
        v2.put(Groups._ID, groupId2);
        v2.put(Groups.TITLE, "title2");
        v2.put(Groups.SOURCE_ID, "sourceId2");
        v2.put(Groups.SUMMARY_COUNT, 0);
        v2.put(Groups.SUMMARY_WITH_PHONES, 0);

        ContentValues v3 = new ContentValues();
        v3.put(Groups._ID, groupId3);
        v3.put(Groups.TITLE, "title3");
        v3.put(Groups.SOURCE_ID, "sourceId3");
        v3.put(Groups.SUMMARY_COUNT, 0);
        v3.put(Groups.SUMMARY_WITH_PHONES, 0);

        assertStoredValues(Groups.CONTENT_SUMMARY_URI, v1, v2, v3);

        // Now rawContactId1 has two phone numbers.
        insertPhoneNumber(rawContactId1, "111-111-1111");
        insertPhoneNumber(rawContactId1, "111-111-1112");
        // Result should reflect it correctly (don't count phone numbers but raw contacts)
        v1.put(Groups.SUMMARY_WITH_PHONES, v1.getAsInteger(Groups.SUMMARY_WITH_PHONES) + 1);
        assertStoredValues(Groups.CONTENT_SUMMARY_URI, v1, v2, v3);

        // Introduce new raw contact, pretending the user added another info.
        final long rawContactId3 = createRawContactWithName("firstName3", "lastName3");
        insertEmail(rawContactId3, "address3@email.com");
        insertPhoneNumber(rawContactId3, "333-333-3333");
        insertGroupMembership(rawContactId3, groupId2);
        v2.put(Groups.SUMMARY_COUNT, v2.getAsInteger(Groups.SUMMARY_COUNT) + 1);
        v2.put(Groups.SUMMARY_WITH_PHONES, v2.getAsInteger(Groups.SUMMARY_WITH_PHONES) + 1);

        assertStoredValues(Groups.CONTENT_SUMMARY_URI, v1, v2, v3);

        final Uri uri = Groups.CONTENT_SUMMARY_URI;

        // TODO Once SUMMARY_GROUP_COUNT_PER_ACCOUNT is supported remove all the if(false).
        if (false) {
            v1.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 1);
            v2.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 2);
            v3.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 2);
        }
        else {
            v1.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 0);
            v2.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 0);
            v3.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 0);
        }
        assertStoredValues(uri, v1, v2, v3);

        // Introduce another group in account1, testing SUMMARY_GROUP_COUNT_PER_ACCOUNT correctly
        // reflects the change.
        final long groupId4 = createGroup("sourceId4", "title4");
        if (false) {
            v1.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT,
                    v1.getAsInteger(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT) + 1);
        } else {
            v1.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 0);
        }
        ContentValues v4 = new ContentValues();
        v4.put(Groups._ID, groupId4);
        v4.put(Groups.TITLE, "title4");
        v4.put(Groups.SOURCE_ID, "sourceId4");
        v4.put(Groups.SUMMARY_COUNT, 0);
//        v4.put(Groups.SUMMARY_WITH_PHONES, 0);
        if (false) {
            v4.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT,
                    v1.getAsInteger(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT));
        } else {
            v4.put(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, 0);
        }
        assertStoredValues(uri, v1, v2, v3, v4);

        // We change the tables dynamically according to the requested projection.
        // Make sure the SUMMARY_COUNT column exists
        v1.clear();
        v1.put(Groups.SUMMARY_COUNT, 2);
        v2.clear();
        v2.put(Groups.SUMMARY_COUNT, 1);
        v3.clear();
        v3.put(Groups.SUMMARY_COUNT, 0);
        v4.clear();
        v4.put(Groups.SUMMARY_COUNT, 0);
        assertStoredValuesWithProjection(uri, v1, v2, v3, v4);
    }

    private class VCardTestUriCreator {
        private long mLookup1;
        private long mLookup2;

        public VCardTestUriCreator(long lookup1, long lookup2) {
            super();
            mLookup1 = lookup1;
            mLookup2 = lookup2;
        }

        public Uri getUri1() {
            return ContentUris.withAppendedId(RawContacts.CONTENT_VCARD_URI, mLookup1);
        }

        public Uri getUri2() {
            return ContentUris.withAppendedId(RawContacts.CONTENT_VCARD_URI, mLookup2);
        }

//        public Uri getCombinedUri() {
//            return Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI,
//                    Uri.encode(mLookup1 + ":" + mLookup2));
//        }
    }

    public void testContactCounts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            return;
        Uri uri = RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ScContactsContract.ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();

        createRawContact();
        createRawContactWithName("James", "Sullivan");
        createRawContactWithName("The Abominable", "Snowman");
        createRawContactWithName("Mike", "Wazowski");
        createRawContactWithName("randall", "boggs");
        createRawContactWithName("Boo", null);
        createRawContactWithName("Mary", null);
        createRawContactWithName("Roz", null);

        Cursor cursor = resolver.query(uri,
                new String[]{RawContacts.DISPLAY_NAME},
                null, null, RawContacts.SORT_KEY_PRIMARY + " COLLATE LOCALIZED");

        assertFirstLetterValues(cursor, null, "B", "J", "M", "R", "T");
        assertFirstLetterCounts(cursor,    1,   1,   1,   2,   2,   1);
        cursor.close();

        cursor = resolver.query(uri,
                new String[]{RawContacts.DISPLAY_NAME},
                null, null, RawContacts.SORT_KEY_ALTERNATIVE + " COLLATE LOCALIZED DESC");

        assertFirstLetterValues(cursor, "W", "S", "R", "M", "B", null);
        assertFirstLetterCounts(cursor,   1,   2,   1,   1,   2,    1);
        cursor.close();
    }

    private void assertFirstLetterValues(Cursor cursor, String... expected) {
        Bundle bundle = cursor.getExtras();

        if (bundle == Bundle.EMPTY)   // try the dirty trick if Cursor does not support setExtras() function
            bundle = mContext.getContentResolver().call(ScContactsContract.AUTHORITY_URI, "INDEX", null, null);
        String[] actual = bundle.getStringArray(ScContactsContract.ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
        MoreAsserts.assertEquals(expected, actual);
    }


    private void assertFirstLetterCounts(Cursor cursor, int... expected) {
        Bundle bundle = cursor.getExtras();

        if (bundle == Bundle.EMPTY)   // try the dirty trick if Cursor does not support setExtras() function
            bundle = mContext.getContentResolver().call(ScContactsContract.AUTHORITY_URI, "INDEX", null, null);
        int[] actual = bundle.getIntArray(ScContactsContract.ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
        MoreAsserts.assertEquals(expected, actual);
    }

    private VCardTestUriCreator createVCardTestContacts() {
        final long rawContactId1 = createRawContact();
        insertStructuredName(rawContactId1, "John", "Doe");

        final long rawContactId2 = createRawContact();
        insertStructuredName(rawContactId2, "Jane", "Doh");

        return new VCardTestUriCreator(rawContactId1, rawContactId2);
    }

//    public void testQueryMultiVCard() {
//        // No need to create any contacts here, because the query for multiple vcards
//        // does not go into the database at all
//        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode("123:456"));
//        Cursor cursor = mResolver.query(uri, null, null, null, null);
//        assertEquals(1, cursor.getCount());
//        assertTrue(cursor.moveToFirst());
//        assertTrue(cursor.isNull(cursor.getColumnIndex(OpenableColumns.SIZE)));
//        String filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
//
//        // The resulting name contains date and time. Ensure that before and after are correct
//        assertTrue(filename.startsWith("vcards_"));
//        assertTrue(filename.endsWith(".vcf"));
//        cursor.close();
//    }
//

    public void testQueryFileSingleVCard() {
        final VCardTestUriCreator contacts = createVCardTestContacts();

        {
            Cursor cursor = resolver.query(contacts.getUri1(), null, null, null, null);
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(cursor.getColumnIndex(OpenableColumns.SIZE)));
            String filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            assertEquals("John Doe.vcf", filename);
            cursor.close();
        }

        {
            Cursor cursor = resolver.query(contacts.getUri2(), null, null, null, null);
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(cursor.getColumnIndex(OpenableColumns.SIZE)));
            String filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            assertEquals("Jane Doh.vcf", filename);
            cursor.close();
        }
    }

//    public void testOpenAssetFileMultiVCard() throws IOException {
//        final VCardTestUriCreator contacts = createVCardTestContacts();
//
//        final AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(contacts.getCombinedUri(), "r");
//        final FileInputStream inputStream = descriptor.createInputStream();
//        String data = readToEnd(inputStream);
//        inputStream.close();
//        descriptor.close();
//
//        // Ensure that the resulting VCard has both contacts
//        assertTrue(data.contains("N:Doe;John;;;"));
//        assertTrue(data.contains("N:Doh;Jane;;;"));
//    }

    public void testOpenAssetFileSingleVCard() throws IOException {
        final VCardTestUriCreator contacts = createVCardTestContacts();

        // Ensure that the right VCard is being created in each case
        {
            final AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(contacts.getUri1(), "r");
            final FileInputStream inputStream = descriptor.createInputStream();
            final String data = readToEnd(inputStream);
            inputStream.close();
            descriptor.close();

            assertTrue(data.contains("N:Doe;John;;;"));
            assertFalse(data.contains("N:Doh;Jane;;;"));
        }

        {
            final AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(contacts.getUri2(), "r");
            final FileInputStream inputStream = descriptor.createInputStream();
            final String data = readToEnd(inputStream);
            inputStream.close();
            descriptor.close();

            assertFalse(data.contains("N:Doe;John;;;"));
            assertTrue(data.contains("N:Doh;Jane;;;"));
        }
    }

    private String readToEnd(FileInputStream inputStream) {
        try {
            System.out.println("DECLARED INPUT STREAM LENGTH: " + inputStream.available());
            int ch;
            StringBuilder stringBuilder = new StringBuilder();
            int index = 0;
            while (true) {
                ch = inputStream.read();
                if (ch == -1) {
                    break;
                }
                System.out.println("READ CHARACTER: " + index + " " + (char)ch);
                stringBuilder.append((char)ch);
                index++;
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            return null;
        }
    }

    protected void assertNetworkNotified(boolean expected) {
        // TODO: better solution for embedding sync contacts prov wrapper
        // assertEquals(expected, (getContactsProvider()).isNetworkNotified());
    }

    private ContentValues buildGenericStreamItemValues() {
        ContentValues values = new ContentValues();
        values.put(StreamItems.TEXT, "Hello world");
        values.put(StreamItems.TIMESTAMP, System.currentTimeMillis());
        values.put(StreamItems.COMMENTS, "Reshared by 123 others");
        return values;
    }

    private ContentValues buildGenericStreamItemPhotoValues(int sortIndex) {
        ContentValues values = new ContentValues();
        values.put(StreamItemPhotos.SORT_INDEX, sortIndex);
        values.put(StreamItemPhotos.PHOTO, loadPhotoFromResource(R.drawable.ic_contact_picture_holo_dark, PhotoSize.ORIGINAL));
        return values;
    }

    /*
     * Delta API
     */
    public void testContactUpdate_updatesContactUpdatedTimestamp() {
        sMockClock.install();
        long rawContactId = createRawContactWithName("John", "Doe");

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(resolver, rawContactId);

        ContentValues values = new ContentValues();
        values.put(ContactsContract.Contacts.STARRED, 1);

        sMockClock.advance();
        ContactUtil.update(resolver, rawContactId, values);

        long newTime = ContactUtil.queryContactLastUpdatedTimestamp(resolver, rawContactId);
        assertTrue(newTime > baseTime);

        // Clean up
        Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        resolver.delete(uri, null, null);
    }

}
