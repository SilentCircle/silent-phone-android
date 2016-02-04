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

import com.silentcircle.contacts.providers.ScCallLogProvider;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.silentcontacts2.ScCallLog;
import com.silentcircle.silentcontacts2.ScCallLog.ScCalls;
import com.silentcircle.silentcontacts2.ScContactsContract;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.test.mock.MockPackageManager;
import android.util.Log;

public class ScCallLogProviderTest extends ProviderTestBase<ScCallLogProviderTest.TestScCallLogProvider> {

    @SuppressWarnings("unused")
    private static String TAG = "ScCallLogProviderTest";

    public ScCallLogProviderTest() {
        super(TestScCallLogProvider.class, ScCallLog.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
       super.setUp();
       addProvider(TestScCallLogProvider.class, ScCallLog.AUTHORITY);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected Class<? extends ContentProvider> getProviderClass() {
       return SynchronousContactsProvider.class;
    }

    @Override
    protected String getAuthority() {
        return ScContactsContract.AUTHORITY;
    }

    public void testInsert_RegularCallRecord() {
        ContentValues values = getDefaultValues(ScCalls.INCOMING_TYPE);
        Uri uri = resolver.insert(ScCalls.CONTENT_URI, values);
        assertNotNull("Got null URI after inserting data", uri);
        Log.d(TAG, "URI: " + uri.toString());
//        values.put(ScCalls.COUNTRY_ISO, "us");
        assertStoredValues(uri, values);
        assertSelection(uri, values, ScCalls._ID, ContentUris.parseId(uri));
    }

    public void testUpdate() {
        Uri uri = insertCallRecord();
        ContentValues values = new ContentValues();
        values.put(ScCalls.TYPE, ScCalls.OUTGOING_TYPE);
        values.put(ScCalls.NUMBER, "1-800-263-7643");
        values.put(ScCalls.DATE, 2000);
        values.put(ScCalls.DURATION, 40);
        values.put(ScCalls.CACHED_NAME, "1-800-GOOG-411");
//        values.put(ScCalls.CACHED_NUMBER_TYPE, Phone.TYPE_CUSTOM);  // TODO: fix if contact contract etc available
        values.put(ScCalls.CACHED_NUMBER_LABEL, "Directory");

        int count = resolver.update(uri, values, null, null);
        assertEquals(1, count);
        assertStoredValues(uri, values);
    }

    public void testDelete() {
        Uri uri = insertCallRecord();           // Returns a URI 'CALL_ID'
        try {
            resolver.delete(uri, null, null);   // Deletes records only if URI is 'CALL' with selection parameters
            fail();
        } catch (UnsupportedOperationException ex) {
            // Expected
        }

        int count = resolver.delete(ScCalls.CONTENT_URI, ScCalls._ID + "=" + ContentUris.parseId(uri), null);
        assertEquals(1, count);
        assertEquals(0, getCount(uri, null, null));
    }

    public void testCallLogFilter() {
        ContentValues values = getDefaultValues(ScCalls.INCOMING_TYPE);
        resolver.insert(ScCalls.CONTENT_URI, values);

        Uri filterUri = Uri.withAppendedPath(ScCalls.CONTENT_FILTER_URI, "1-800-4664-411");
        Cursor c = resolver.query(filterUri, null, null, null, null);
        assertEquals(1, c.getCount());
        c.moveToFirst();
        assertCursorValues(c, values);
        c.close();

        filterUri = Uri.withAppendedPath(ScCalls.CONTENT_FILTER_URI, "1-888-4664-411");
        c = resolver.query(filterUri, null, null, null, null);
        assertEquals(0, c.getCount());
        c.close();
    }

    // TODO - implement add call, check which CallerInfo to use in this case.
//    public void testAddCall() {
//        CallerInfo ci = new CallerInfo();
//        ci.name = "1-800-GOOG-411";
//        ci.numberType = Phone.TYPE_CUSTOM;
//        ci.numberLabel = "Directory";
//        Uri uri = ScCalls.addCall(ci, getMockContext(), "1-800-263-7643",
//                PhoneConstants.PRESENTATION_ALLOWED, ScCalls.OUTGOING_TYPE, 2000, 40);
//
//        ContentValues values = new ContentValues();
//        values.put(ScCalls.TYPE, ScCalls.OUTGOING_TYPE);
//        values.put(ScCalls.NUMBER, "1-800-263-7643");
//        values.put(ScCalls.DATE, 2000);
//        values.put(ScCalls.DURATION, 40);
//        values.put(ScCalls.CACHED_NAME, "1-800-GOOG-411");
//        values.put(ScCalls.CACHED_NUMBER_TYPE, Phone.TYPE_CUSTOM);
//        values.put(ScCalls.CACHED_NUMBER_LABEL, "Directory");
//        values.put(ScCalls.COUNTRY_ISO, "us");
//        values.put(ScCalls.GEOCODED_LOCATION, "usa");
//        assertStoredValues(uri, values);
//    }

    public void testUriWithBadLimitParamThrowsException() {
        assertParamThrowsIllegalArgumentException(ScCalls.LIMIT_PARAM_KEY, "notvalid");
    }

    public void testUriWithBadOffsetParamThrowsException() {
        assertParamThrowsIllegalArgumentException(ScCalls.OFFSET_PARAM_KEY, "notvalid");
    }

    private void assertParamThrowsIllegalArgumentException(String key, String value) {
        Uri uri = ScCalls.CONTENT_URI.buildUpon()
                .appendQueryParameter(key, value)
                .build();
        try {
            resolver.query(uri, null, null, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue("Error does not contain value in question.",
                    e.toString().contains(value));
        }
    }

    private ContentValues getDefaultValues(int callType) {
        ContentValues values = new ContentValues();
        values.put(ScCalls.TYPE, callType);
        values.put(ScCalls.NUMBER, "1-800-4664-411");
        values.put(ScCalls.DATE, 1000);
        values.put(ScCalls.DURATION, 30);
        values.put(ScCalls.NEW, 1);
        return values;
    }

    private Uri insertCallRecord() {
        return resolver.insert(ScCalls.CONTENT_URI, getDefaultValues(ScCalls.INCOMING_TYPE));
    }

    public static class TestScCallLogProvider extends ScCallLogProvider {
        private static ScContactsDatabaseHelper mDbHelper;

        @Override
        protected ScContactsDatabaseHelper getDatabaseHelper(final Context context) {
           if (mDbHelper == null) {
                mDbHelper = ScContactsDatabaseHelper.getNewInstanceForTest(context);
            }
            return mDbHelper;
        }

//        @Override
//        protected CallLogInsertionHelper createCallLogInsertionHelper(Context context) {
//            return new CallLogInsertionHelper() {
//                @Override
//                public String getGeocodedLocationFor(String number, String countryIso) {
//                    return "usa";
//                }
//
//                @Override
//                public void addComputedValues(ContentValues values) {
//                    values.put(ScCalls.COUNTRY_ISO, "us");
//                    values.put(ScCalls.GEOCODED_LOCATION, "usa");
//                }
//            };

        @Override
        protected Context context() {
            return new ContextWrapper(super.context()) {
                @Override
                public PackageManager getPackageManager() {
                    return new MockPackageManager();
                }

                @Override
                public void sendBroadcast(Intent intent, String receiverPermission) {
                   // Do nothing for now.
                }
            };
        }
    }
}

