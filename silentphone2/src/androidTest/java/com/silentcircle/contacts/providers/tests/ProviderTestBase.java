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
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2012 The Android Open Source Project
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

// ---LICENSE_BEGIN---
/*
 * Copyright © 2013, Silent Circle
 * All rights reserved.
 */
// ---LICENSE_END---


package com.silentcircle.contacts.providers.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.silentcircle.contacts.providers.PhotoProcessor;
import com.silentcircle.contacts.providers.tests.testutils.MockClock;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.GroupMembership;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Organization;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.SipAddress;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredName;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.Groups;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItems;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.R;
import com.silentcircle.contacts.utils.Hex;

import android.accounts.Account;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.mock.MockContentResolver;
import android.text.TextUtils;
import android.util.Log;

public class ProviderTestBase<T extends ContentProvider> extends AndroidTestCase /*ProviderTestCase2<T>*/ {

    @SuppressWarnings("unused")
    protected static final String TAG = "ProviderTestBase";
    public static final String READ_ONLY_ACCOUNT_TYPE =  SynchronousContactsProvider.READ_ONLY_ACCOUNT_TYPE;

    protected ContactsActor mActor;
    protected MockContentResolver resolver;

    protected final static Long NO_LONG = (long)0;
    protected final static String NO_STRING = "";

    /**
     * Use {@link MockClock#install()} to start using it.
     * It'll be automatically uninstalled by {@link #tearDown()}.
     */
    protected static final MockClock sMockClock = new MockClock();
    

    protected Class<? extends ContentProvider> getProviderClass() {
        return SynchronousContactsProvider.class;
    }

    protected String getAuthority() {
        return ScContactsContract.AUTHORITY;
    }

    public ProviderTestBase(Class<T> clazz, String authority) {
        super();
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActor = new ContactsActor(getContext(), ContactsActor.PACKAGE_GREY, getProviderClass(), getAuthority());
        resolver = mActor.resolver;
        if (mActor.mprovider instanceof SynchronousContactsProvider) {
            getContactsProvider().wipeData();
        }

        // Give the actor access to read/write contacts and profile data by default.
        mActor.addPermissions(
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.READ_SOCIAL_STREAM",
                "android.permission.WRITE_SOCIAL_STREAM",
                "android.permission.READ_PROFILE",
                "android.permission.WRITE_PROFILE");
    }

//    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public SynchronousContactsProvider getContactsProvider() {
        return (SynchronousContactsProvider) mActor.mprovider;
    }

    public ContentProvider addProvider(Class<? extends ContentProvider> providerClass, String authority) throws Exception {
        return mActor.addProvider(providerClass, authority);
    }

    /**
     * Constructs a selection (where clause) out of all supplied values, uses it
     * to query the provider and verifies that a single row is returned and it
     * has the same values as requested.
     */
    protected void assertSelection(Uri uri, ContentValues values, String idColumn, long id) {
        assertSelection(uri, values, idColumn, id, null);
    }

    @SuppressWarnings("unused")
    public void assertSelectionWithProjection(Uri uri, ContentValues values, String idColumn, long id) {
        assertSelection(uri, values, idColumn, id, buildProjection(values));
    }

    private void assertSelection(Uri uri, ContentValues values, String idColumn, long id, String[] projection) {
        StringBuilder sb = new StringBuilder();
        ArrayList<String> selectionArgs = new ArrayList<>(values.size());
        if (idColumn != null) {
            sb.append(idColumn).append("=").append(id);
        }
        Set<Entry<String, Object>> entries = values.valueSet();
        for (Entry<String, Object> entry : entries) {
            String column = entry.getKey();
            Object value = entry.getValue();
            if (sb.length() != 0) {
                sb.append(" AND ");
            }
            sb.append(column);
            if (value == null) {
                sb.append(" IS NULL");
            } else {
                sb.append("=?");
                selectionArgs.add(String.valueOf(value));
            }
        }

        Cursor c = resolver.query(uri, projection, sb.toString(), selectionArgs.toArray(new String[selectionArgs.size()]), null);
        try {
            assertEquals("Record count", 1, c.getCount());
            c.moveToFirst();
            assertCursorValues(c, values);
        } catch (Error e) {
            TestUtils.dumpCursor(c);
            throw e;
        } finally {
            c.close();
        }
    }

    protected void assertStoredValues(Uri rowUri, ContentValues expectedValues) {
        assertStoredValues(rowUri, null, null, expectedValues);
    }

    protected void assertStoredValues(Uri rowUri, ContentValues... expectedValues) {
        assertStoredValues(rowUri, null, null, expectedValues);
    }

    protected void assertStoredValuesWithProjection(Uri rowUri, ContentValues expectedValues) {
        assertStoredValuesWithProjection(rowUri, new ContentValues[] {expectedValues});
    }

    protected void assertStoredValuesWithProjection(Uri rowUri, ContentValues... expectedValues) {
        assertTrue("Need at least one ContentValues for this test", expectedValues.length > 0);
        Cursor c = resolver.query(rowUri, buildProjection(expectedValues[0]), null, null, null);
        try {
            assertEquals("Record count", expectedValues.length, c.getCount());
            c.moveToFirst();
            assertCursorValues(c, expectedValues);
        } catch (Error e) {
            TestUtils.dumpCursor(c);
            throw e;
        } finally {
            c.close();
        }
    }

    protected void assertStoredValues(Uri rowUri, String selection, String[] selectionArgs, ContentValues... expectedValues) {
        assertStoredValues(resolver.query(rowUri, null, selection, selectionArgs, null), expectedValues);
    }

    protected void assertStoredValues(Uri rowUri, String selection, String[] selectionArgs, ContentValues expectedValues) {
        Cursor c = resolver.query(rowUri, null, selection, selectionArgs, null);
        try {
            assertEquals("Record count", 1, c.getCount());
            c.moveToFirst();
            assertCursorValues(c, expectedValues);
        } catch (Error e) {
            TestUtils.dumpCursor(c);
            throw e;
        } finally {
            c.close();
        }
    }

    private void assertStoredValues(Cursor c, ContentValues... expectedValues) {
        try {
            assertEquals("Record count", expectedValues.length, c.getCount());
            assertCursorValues(c, expectedValues);
        } catch (Error e) {
            TestUtils.dumpCursor(c);
            throw e;
        } finally {
            c.close();
        }
    }

    @SuppressWarnings("unused")
    protected void assertCursorValue(Cursor cursor, String column, Object expectedValue) {
        String actualValue = cursor.getString(cursor.getColumnIndex(column));
        assertEquals("Column " + column, String.valueOf(expectedValue),
                String.valueOf(actualValue));
    }

    protected void assertCursorValues(Cursor cursor, ContentValues expectedValues) {
        StringBuilder message = new StringBuilder();
        boolean result = equalsWithExpectedValues(cursor, expectedValues, message);
        assertTrue(message.toString(), result);
    }

    protected void assertCursorValues(Cursor cursor, ContentValues... expectedValues) {
        StringBuilder message = new StringBuilder();

        // In case if expectedValues contains multiple identical values, remember which cursor
        // rows are "consumed" to prevent multiple ContentValues from hitting the same row.
        final BitSet used = new BitSet(cursor.getCount());

        for (ContentValues v : expectedValues) {
            boolean found = false;
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                final int pos = cursor.getPosition();
                if (used.get(pos)) continue;
                found = equalsWithExpectedValues(cursor, v, message);
                if (found) {
                    used.set(pos);
                    break;
                }
            }
            assertTrue("Expected values can not be found " + v + "," + message.toString(), found);
        }
    }

    private void assertCursorValuesOrderly(Cursor cursor, ContentValues... expectedValues) {
        StringBuilder message = new StringBuilder();
        cursor.moveToPosition(-1);
        for (ContentValues v : expectedValues) {
            assertTrue(cursor.moveToNext());
            boolean ok = equalsWithExpectedValues(cursor, v, message);
            assertTrue("ContentValues didn't match.  Pos=" + cursor.getPosition() + ", values=" +
                    v + message.toString(), ok);
        }
    }

    protected void assertStoredValuesOrderly(Uri rowUri, ContentValues... expectedValues) {
        assertStoredValuesOrderly(rowUri, null, null, expectedValues);
    }

    protected void assertStoredValuesOrderly(Uri rowUri, String selection,
            String[] selectionArgs, ContentValues... expectedValues) {
        Cursor c = resolver.query(rowUri, null, selection, selectionArgs, null);
        try {
            assertEquals("Record count", expectedValues.length, c.getCount());
            assertCursorValuesOrderly(c, expectedValues);
        } catch (Error e) {
            TestUtils.dumpCursor(c);
            throw e;
        } finally {
            c.close();
        }
    }

    @SuppressWarnings("unused")
    protected void assertStoredValue(Uri contentUri, long id, String column, Object expectedValue) {
        assertStoredValue(ContentUris.withAppendedId(contentUri, id), column, expectedValue);
    }

    protected void assertStoredValue(Uri rowUri, String selection, String[] selectionArgs, String column, Object expectedValue) {
        String value = getStoredValue(rowUri, selection, selectionArgs, column);
        if (expectedValue == null) {
            assertNull("Column value " + column, value);
        } else {
            assertEquals("Column value " + column, String.valueOf(expectedValue), value);
        }
    }

    protected void assertStoredValue(Uri rowUri, String column, Object expectedValue) {
        String value = getStoredValue(rowUri, column);
        if (expectedValue == null) {
            assertNull("Column value " + column, value);
        } else {
            assertEquals("Column value " + column, String.valueOf(expectedValue), value);
        }
    }


    protected void assertStructuredName(long rawContactId, String prefix, String givenName,
            String middleName, String familyName, String suffix) {
        Uri uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.Data.CONTENT_DIRECTORY);

        final String[] projection = new String[] {
                StructuredName.PREFIX, StructuredName.GIVEN_NAME, StructuredName.MIDDLE_NAME,
                StructuredName.FAMILY_NAME, StructuredName.SUFFIX
        };

        Cursor c = resolver.query(uri, projection, Data.MIMETYPE + "='"
                + StructuredName.CONTENT_ITEM_TYPE + "'", null, null);

        assertTrue(c.moveToFirst());
        assertEquals(prefix, c.getString(0));
        assertEquals(givenName, c.getString(1));
        assertEquals(middleName, c.getString(2));
        assertEquals(familyName, c.getString(3));
        assertEquals(suffix, c.getString(4));
        c.close();
    }

    protected void assertProjection(Uri uri, String[] expectedProjection) {
        Cursor cursor = resolver.query(uri, null, "0", null, null);
        String[] actualProjection = cursor.getColumnNames();
        MoreAsserts.assertEquals("Incorrect projection for URI: " + uri,
                new HashSet<>(Arrays.asList(expectedProjection)), new HashSet<>(Arrays.asList(actualProjection)));
        cursor.close();
    }

    protected long assertSingleGroup(Long rowId, String sourceId, String title) {
        Cursor c = resolver.query(Groups.CONTENT_URI, null, null, null, null);
        try {
            assertTrue(c.moveToNext());
            long actualRowId = assertGroup(c, rowId, sourceId, title);
            assertFalse(c.moveToNext());
            return actualRowId;
        } finally {
            c.close();
        }
    }

    protected long assertSingleGroupMembership(Long rowId, Long rawContactId, Long groupRowId,
            String sourceId) {
        Cursor c = resolver.query(Data.CONTENT_URI, null, null, null, null);
        try {
            assertTrue(c.moveToNext());
            long actualRowId = assertGroupMembership(c, rowId, rawContactId, groupRowId, sourceId);
            assertFalse(c.moveToNext());
            return actualRowId;
        } finally {
            c.close();
        }
    }

    protected long assertGroupMembership(Cursor c, Long rowId, Long rawContactId, Long groupRowId, String sourceId) {
        assertNullOrEquals(c, rowId, Data._ID);
        assertNullOrEquals(c, rawContactId, GroupMembership.RAW_CONTACT_ID);
        assertNullOrEquals(c, groupRowId, GroupMembership.GROUP_ROW_ID);
        assertNullOrEquals(c, sourceId, GroupMembership.GROUP_SOURCE_ID);
        return c.getLong(c.getColumnIndexOrThrow("_id"));
    }

    protected long assertGroup(Cursor c, Long rowId, String sourceId, String title) {
        assertNullOrEquals(c, rowId, Groups._ID);
        assertNullOrEquals(c, sourceId, Groups.SOURCE_ID);
        assertNullOrEquals(c, title, Groups.TITLE);
        return c.getLong(c.getColumnIndexOrThrow("_id"));
    }


    private void assertNullOrEquals(Cursor c, Long value, String columnName) {
        if (value != NO_LONG) {
            if (value == null) assertTrue(c.isNull(c.getColumnIndexOrThrow(columnName)));
            else assertEquals((long) value, c.getLong(c.getColumnIndexOrThrow(columnName)));
        }
    }

    private void assertNullOrEquals(Cursor c, String value, String columnName) {
        if (value != NO_STRING) {
            if (value == null) assertTrue(c.isNull(c.getColumnIndexOrThrow(columnName)));
            else assertEquals(value, c.getString(c.getColumnIndexOrThrow(columnName)));
        }
    }

    protected Long getStoredLongValue(Uri uri, String selection, String[] selectionArgs, String column) {
        Long value = null;
        Cursor c = resolver.query(uri, new String[] { column }, selection, selectionArgs, null);
        try {
            assertEquals("Record count", 1, c.getCount());

            if (c.moveToFirst()) {
                value = c.getLong(c.getColumnIndex(column));
            }
        } finally {
            c.close();
        }
        return value;
    }

    protected Long getStoredLongValue(Uri uri, String column) {
        return getStoredLongValue(uri, null, null, column);
    }

    /**
     * Retrieves the string value in the given column, handling deferred snippeting if the requested
     * column is the snippet and the cursor specifies it.
     */
    protected String getCursorStringValue(Cursor c, String column) {
        String value = c.getString(c.getColumnIndex(column));
//        if (SearchSnippetColumns.SNIPPET.equals(column)) {
//            Bundle extras = c.getExtras();
//            if (extras.containsKey(ContactsContract.DEFERRED_SNIPPETING)) {
//                String displayName = "No display name";
//                int displayNameColumnIndex = c.getColumnIndex(Contacts.DISPLAY_NAME);
//                if (displayNameColumnIndex != -1) {
//                    displayName = c.getString(displayNameColumnIndex);
//                }
//                String query = extras.getString(ContactsContract.DEFERRED_SNIPPETING_QUERY);
//                value = ContactsContract.snippetize(value, displayName, query,
//                        '[', ']', "...", 5);
//            }
//        }
        return value;
    }

    protected Uri maybeAddAccountQueryParameters(Uri uri, Account account) {
        if (account == null) {
            return uri;
        }
        return uri.buildUpon()
//                .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
//                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
                .build();
    }

    protected long createRawContact() {
        return createRawContact(null);
    }

    protected long createRawContactWithName() {
        return createRawContactWithName(null);
    }

    protected long createRawContactWithName(Account account) {
        return createRawContactWithName("John", "Doe", account);
    }

    protected long createRawContactWithName(String firstName, String lastName) {
        return createRawContactWithName(firstName, lastName, null);
    }

    protected long createRawContactWithName(String firstName, String lastName, Account account) {
        long rawContactId = createRawContact(account);
        insertStructuredName(rawContactId, firstName, lastName);
        return rawContactId;
    }

    protected long createRawContact(Account account, String... extras) {
        ContentValues values = new ContentValues();
        extrasVarArgsToValues(values, extras);
        final Uri uri = maybeAddAccountQueryParameters(RawContacts.CONTENT_URI, account);
        Uri contactUri = resolver.insert(uri, values);
        return ContentUris.parseId(contactUri);
    }

    private static void extrasVarArgsToValues(ContentValues values, String... extras) {
        for (int i = 0; i < extras.length; ) {
            values.put(extras[i], extras[i + 1]);
            i += 2;
        }
    }

    protected long createRawContact(ContentValues values, String firstName, String lastName,String phoneNumber, String email,
                                  int presenceStatus, int timesContacted, int starred, long groupId, int chatMode) {
        values.put(RawContacts.STARRED, starred);
        values.put(RawContacts.CUSTOM_RINGTONE, "beethoven5");
        values.put(RawContacts.TIMES_CONTACTED, timesContacted);

        Uri insertionUri = RawContacts.CONTENT_URI;
        Uri rawContactUri = resolver.insert(insertionUri, values);
        long rawContactId = ContentUris.parseId(rawContactUri);
        insertStructuredName(rawContactId, firstName, lastName);
        Uri photoUri = insertPhoto(rawContactId);
        long photoId = ContentUris.parseId(photoUri);
        values.put(RawContacts.PHOTO_ID, photoId);
        if (!TextUtils.isEmpty(phoneNumber)) {
            insertPhoneNumber(rawContactId, phoneNumber);
        }
        if (!TextUtils.isEmpty(email)) {
            insertEmail(rawContactId, email);
        }

//        insertStatusUpdate(Im.PROTOCOL_GOOGLE_TALK, null, email, presenceStatus, "hacking",
//                chatMode, isUserProfile);

        if (groupId != 0) {
            insertGroupMembership(rawContactId, groupId);
        }

        return rawContactId;
    }


    protected long createGroup(String sourceId, String title) {
        return createGroup(sourceId, title, 1, false, false);
    }

    @SuppressWarnings("unused")
    protected long createGroup(String sourceId, String title, int visible) {
        return createGroup(sourceId, title, visible, false, false);
    }

    @SuppressWarnings("unused")
    protected long createAutoAddGroup() {
        return createGroup("auto", "auto", 0 /* visible */,  true /* auto-add */, false /* fav */);
    }

    protected long createGroup(String sourceId, String title, int visible, boolean autoAdd, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put(Groups.SOURCE_ID, sourceId);
        values.put(Groups.TITLE, title);
        values.put(Groups.GROUP_VISIBLE, visible);
        values.put(Groups.AUTO_ADD, autoAdd ? 1 : 0);
        values.put(Groups.FAVORITES, favorite ? 1 : 0);
        final Uri uri = Groups.CONTENT_URI;
        return ContentUris.parseId(resolver.insert(uri, values));
    }

    protected Uri insertOrganization(long rawContactId, ContentValues values) {
        return insertOrganization(rawContactId, values, false);
    }

    protected Uri insertOrganization(long rawContactId, ContentValues values, boolean primary) {
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
        values.put(Organization.TYPE, Organization.TYPE_WORK);
        if (primary) {
            values.put(Data.IS_PRIMARY, 1);
        }
        return resolver.insert(Data.CONTENT_URI, values);
    }

    protected Uri insertStructuredName(long rawContactId, String givenName, String familyName) {
        ContentValues values = new ContentValues();
        StringBuilder sb = new StringBuilder();
        if (givenName != null) {
            sb.append(givenName);
        }
        if (givenName != null && familyName != null) {
            sb.append(" ");
        }
        if (familyName != null) {
            sb.append(familyName);
        }
        values.put(StructuredName.DISPLAY_NAME, sb.toString());
        values.put(StructuredName.GIVEN_NAME, givenName);
        values.put(StructuredName.FAMILY_NAME, familyName);

        return insertStructuredName(rawContactId, values);
    }

    protected Uri insertStructuredName(long rawContactId, ContentValues values) {
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        return resolver.insert(Data.CONTENT_URI, values);
    }


    private boolean equalsWithExpectedValues(Cursor cursor, ContentValues expectedValues, StringBuilder msgBuffer) {
        Set<Entry<String, Object>> values = expectedValues.valueSet();
        for (Entry<String, Object> column : values) {            
            int index = cursor.getColumnIndex(column.getKey());
            if (index == -1) {
                msgBuffer.append(" No such column: ").append(column);
                return false;
            }
            Object expectedValue = expectedValues.get(column.getKey());
            String value;
            if (expectedValue instanceof byte[]) {
                expectedValue = Hex.encodeHex((byte[])expectedValue, false);
                value = Hex.encodeHex(cursor.getBlob(index), false);
            } else {
                expectedValue = expectedValues.getAsString(column.getKey());
                value = getCursorStringValue(cursor, column.getKey());
            }
            if (expectedValue != null && !expectedValue.equals(value) || value != null && !value.equals(expectedValue)) {
                msgBuffer
                        .append(" Column value ")
                        .append(column)
                        .append(" expected <")
                        .append(expectedValue)
                        .append(">, but was <")
                        .append(value)
                        .append('>');
                return false;
            }
        }
        return true;
    }

    private String[] buildProjection(ContentValues values) {
        String[] projection = new String[values.size()];
        Iterator<Entry<String, Object>> iter = values.valueSet().iterator();
        for (int i = 0; i < projection.length; i++) {
            projection[i] = iter.next().getKey();
        }
        return projection;
    }

    protected int getCount(Uri uri, String selection, String[] selectionArgs) {
        Cursor c = resolver.query(uri, null, selection, selectionArgs, null);
        try {
            return c.getCount();
        } finally {
            c.close();
        }
    }

    @SuppressWarnings("unused")
    protected Cursor queryRawContact(long rawContactId) {
        return resolver.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), null, null, null, null);
    }
    
    protected Uri insertPhoneNumber(long rawContactId, String phoneNumber) {
        return insertPhoneNumber(rawContactId, phoneNumber, false);
    }

    protected Uri insertPhoneNumber(long rawContactId, String phoneNumber, boolean primary) {
        return insertPhoneNumber(rawContactId, phoneNumber, primary, Phone.TYPE_HOME);
    }

    protected Uri insertPhoneNumber(long rawContactId, String phoneNumber, boolean primary, int type) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, phoneNumber);
        values.put(Phone.TYPE, type);
        if (primary) {
            values.put(Data.IS_PRIMARY, 1);
        }

        return resolver.insert(Data.CONTENT_URI, values);
    }

    protected Uri insertEmail(long rawContactId, String email) {
        return insertEmail(rawContactId, email, false);
    }

    protected Uri insertEmail(long rawContactId, String email, boolean primary) {
        return insertEmail(rawContactId, email, primary, Email.TYPE_HOME, null);
    }

    protected Uri insertEmail(long rawContactId, String email, boolean primary, int type, String label) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.DATA, email);
        values.put(Email.TYPE, type);
        values.put(Email.LABEL, label);
        if (primary) {
            values.put(Data.IS_PRIMARY, 1);
        }
        return resolver.insert(Data.CONTENT_URI, values);
    }


    protected Uri insertSipAddress(long rawContactId, String sipAddress) {
        return insertSipAddress(rawContactId, sipAddress, false);
    }

    protected Uri insertSipAddress(long rawContactId, String sipAddress, boolean primary) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
        values.put(SipAddress.SIP_ADDRESS, sipAddress);
        if (primary) {
            values.put(Data.IS_PRIMARY, 1);
        }

        return resolver.insert(Data.CONTENT_URI, values);
    }

    protected Uri insertPostalAddress(long rawContactId, String formattedAddress) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        values.put(StructuredPostal.FORMATTED_ADDRESS, formattedAddress);

        return resolver.insert(Data.CONTENT_URI, values);
    }

    @SuppressWarnings("unused")
    protected Uri insertPostalAddress(long rawContactId, ContentValues values) {
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        return resolver.insert(Data.CONTENT_URI, values);
    }

    protected Uri insertPhoto(long rawContactId) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        values.put(Photo.PHOTO, loadTestPhoto());
        return resolver.insert(Data.CONTENT_URI, values);
    }

    protected Uri insertPhoto(long rawContactId, int resourceId) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        values.put(Photo.PHOTO, loadPhotoFromResource(resourceId, PhotoSize.ORIGINAL));
        return resolver.insert(Data.CONTENT_URI, values);
    }


    protected Uri insertStreamItem(long rawContactId, ContentValues values, Account account) {
        return resolver.insert(
                maybeAddAccountQueryParameters(
                        // Uri:  content://com.silentcircle.contacts/raw_contacts/#/stream_items", RAW_CONTACTS_ID_STREAM_ITEMS
                        Uri.withAppendedPath(
                                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                                RawContacts.StreamItems.CONTENT_DIRECTORY),
                        account),
                values);
    }

    protected Uri insertStreamItemPhoto(long streamItemId, ContentValues values, Account account) {
        return resolver.insert(
                maybeAddAccountQueryParameters(
                        // Uri:  content://com.silentcircle.contacts/stream_items/#/photo -> STREAM_ITEMS_ID_PHOTOS
                        Uri.withAppendedPath(
                                ContentUris.withAppendedId(StreamItems.CONTENT_URI, streamItemId),
                                StreamItems.StreamItemPhotos.CONTENT_DIRECTORY),
                        account),
                values);
    }

    protected Uri insertGroupMembership(long rawContactId, String sourceId) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
        values.put(GroupMembership.GROUP_SOURCE_ID, sourceId);
        return resolver.insert(Data.CONTENT_URI, values);
    }

    protected Uri insertGroupMembership(long rawContactId, Long groupId) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
        values.put(GroupMembership.GROUP_ROW_ID, groupId);
        return resolver.insert(Data.CONTENT_URI, values);
    }

    @SuppressWarnings("unused")
    public void removeGroupMemberships(long rawContactId) {
        resolver.delete(Data.CONTENT_URI,
                Data.MIMETYPE + "=? AND " + GroupMembership.RAW_CONTACT_ID + "=?",
                new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(rawContactId) });
    }

    protected Uri insertImHandle(long rawContactId, int protocol, String customProtocol, String handle) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.PROTOCOL, protocol);
        values.put(Im.CUSTOM_PROTOCOL, customProtocol);
        values.put(Im.DATA, handle);
        values.put(Im.TYPE, Im.TYPE_HOME);

        return resolver.insert(Data.CONTENT_URI, values);
    }

    protected String getStoredValue(Uri rowUri, String column) {
        return getStoredValue(rowUri, null, null, column);
    }

    @SuppressWarnings("unused")
    protected Uri setCallerIsSyncAdapter(Uri uri, Account account) {
        if (account == null) {
            return uri;
        }
        final Uri.Builder builder = uri.buildUpon();
        builder.appendQueryParameter(ScContactsContract.CALLER_IS_SYNCADAPTER, "true");
        return builder.build();
    }


    protected String getStoredValue(Uri uri, String selection, String[] selectionArgs, String column) {
        String value = null;
        Cursor c = resolver.query(uri, new String[] { column }, selection, selectionArgs, null);
        try {
            assertEquals("Record count for " + uri, 1, c.getCount());

            if (c.moveToFirst()) {
                value = getCursorStringValue(c, column);
            }
        } finally {
            c.close();
        }
        return value;
    }

    protected enum PhotoSize {
        ORIGINAL,
        DISPLAY_PHOTO,
        THUMBNAIL
    }

    private Map<Integer, PhotoEntry> photoResourceCache = new HashMap<>();

    protected final class PhotoEntry {
        Map<PhotoSize, byte[]> photoMap = new HashMap<>();
        public PhotoEntry(byte[] original) {
            try {
                PhotoProcessor processor = newPhotoProcessor(original, false);
                photoMap.put(PhotoSize.ORIGINAL, original);
                photoMap.put(PhotoSize.DISPLAY_PHOTO, processor.getDisplayPhotoBytes());
                photoMap.put(PhotoSize.THUMBNAIL, processor.getThumbnailPhotoBytes());
            } catch (IOException ignored) {
                Log.d("ProviderTestBase", "PhotoProcessor exception ", ignored);
                // Test is probably going to fail as a result anyway.
            }
        }

        public byte[] getPhoto(PhotoSize size) {
            return photoMap.get(size);
        }
    }
    
    // The test photo will be loaded frequently in tests, so we'll just process it once.
    protected PhotoEntry testPhotoEntry;

    /**
     * Create a new {@link com.silentcircle.contacts.providers.PhotoProcessor} for unit tests.
     *
     * The instance generated here is always configured for 600x600 maximal regardless of the
     * device memory size. This covers the xxhdpi resolution for the pictures we use.
     */
    protected PhotoProcessor newPhotoProcessor(byte[] data, boolean forceCropToSquare) throws IOException {
        return new PhotoProcessor(data, /*256*/ 600, 96, forceCropToSquare);
    }

    protected byte[] loadTestPhoto() {
        int testPhotoId = R.drawable.ic_contact_picture_holo_dark;
        if (testPhotoEntry == null) {
            loadPhotoFromResource(testPhotoId, PhotoSize.ORIGINAL);
            testPhotoEntry = photoResourceCache.get(testPhotoId);
        }
        return testPhotoEntry.getPhoto(PhotoSize.ORIGINAL);
    }

    protected byte[] loadTestPhoto(PhotoSize size) {
        loadTestPhoto();
        return testPhotoEntry.getPhoto(size);
    }

    protected byte[] loadPhotoFromResource(int resourceId, PhotoSize size) {
        PhotoEntry entry = photoResourceCache.get(resourceId);
        if (entry == null) {
            final Resources resources = getContext().getResources();
            InputStream is = resources.openRawResource(resourceId);
            byte[] content = readInputStreamFully(is);
            entry = new PhotoEntry(content);
            photoResourceCache.put(resourceId, entry);
        }
        return entry.getPhoto(size);
    }

    protected byte[] readInputStreamFully(InputStream is) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[10000];
        int count;
        try {
            while ((count = is.read(buffer)) != -1) {
                os.write(buffer, 0, count);
            }
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return os.toByteArray();
    }

}
