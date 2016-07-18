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

package com.silentcircle.contacts.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.provider.CallLog;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.contacts.utils.DbQueryUtils;
import com.silentcircle.contacts.utils.SelectionBuilder;
import com.silentcircle.silentcontacts2.ScCallLog;
import com.silentcircle.silentcontacts2.ScCallLog.ScCalls;
import com.silentcircle.silentcontacts2.ScContactsContract;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteQueryBuilder;

import java.util.HashMap;

public class ScCallLogProvider extends ContentProvider {
    @SuppressWarnings("unused")
    private static final String TAG = "ScCallLogProvider";

    
    private static final int CALLS = 1;

    private static final int CALLS_ID = 2;

    private static final int CALLS_FILTER = 3;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(ScCallLog.AUTHORITY, "calls", CALLS);
        sURIMatcher.addURI(ScCallLog.AUTHORITY, "calls/#", CALLS_ID);
        sURIMatcher.addURI(ScCallLog.AUTHORITY, "calls/filter/*", CALLS_FILTER);
    }

    private static final HashMap<String, String> sCallsProjectionMap;
    static {

        // Calls projection map
        sCallsProjectionMap = new HashMap<>();
        sCallsProjectionMap.put(ScCalls._ID, ScCalls._ID);
        sCallsProjectionMap.put(ScCalls.NUMBER, ScCalls.NUMBER);
        sCallsProjectionMap.put(ScCalls.DATE, ScCalls.DATE);
        sCallsProjectionMap.put(ScCalls.DURATION, ScCalls.DURATION);
        sCallsProjectionMap.put(ScCalls.TYPE, ScCalls.TYPE);
        sCallsProjectionMap.put(ScCalls.NEW, ScCalls.NEW);
        sCallsProjectionMap.put(ScCalls.IS_READ, ScCalls.IS_READ);
        sCallsProjectionMap.put(ScCalls.CACHED_NAME, ScCalls.CACHED_NAME);
        sCallsProjectionMap.put(ScCalls.CACHED_NUMBER_TYPE, ScCalls.CACHED_NUMBER_TYPE);
        sCallsProjectionMap.put(ScCalls.CACHED_NUMBER_LABEL, ScCalls.CACHED_NUMBER_LABEL);
        sCallsProjectionMap.put(ScCalls.COUNTRY_ISO, ScCalls.COUNTRY_ISO);
        sCallsProjectionMap.put(ScCalls.GEOCODED_LOCATION, ScCalls.GEOCODED_LOCATION);
        sCallsProjectionMap.put(ScCalls.CACHED_LOOKUP_URI, ScCalls.CACHED_LOOKUP_URI);
        sCallsProjectionMap.put(ScCalls.CACHED_MATCHED_NUMBER, ScCalls.CACHED_MATCHED_NUMBER);
        sCallsProjectionMap.put(ScCalls.CACHED_NORMALIZED_NUMBER, ScCalls.CACHED_NORMALIZED_NUMBER);
        sCallsProjectionMap.put(ScCalls.CACHED_PHOTO_ID, ScCalls.CACHED_PHOTO_ID);
        sCallsProjectionMap.put(ScCalls.CACHED_FORMATTED_NUMBER, ScCalls.CACHED_FORMATTED_NUMBER);
        sCallsProjectionMap.put(ScCalls.SC_OPTION_TEXT1, ScCalls.SC_OPTION_TEXT1);
        sCallsProjectionMap.put(ScCalls.SC_OPTION_TEXT2, ScCalls.SC_OPTION_TEXT2);
    }

    private ScContactsDatabaseHelper mDbHelper;
    private boolean mUseStrictPhoneNumberComparation = false;

    public ScCallLogProvider() {
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return 0;

        SelectionBuilder selectionBuilder = new SelectionBuilder(selection);

        final SQLiteDatabase db = mDbHelper.getDatabase(true);
        final int matchedUriId = sURIMatcher.match(uri);
        switch (matchedUriId) {
            case CALLS:
                int count = db.delete(Tables.CALLS, selectionBuilder.build(), selectionArgs);
                if (count > 0) {
                    notifyCallLogChange();
                }
                return count;
            default:
                throw new UnsupportedOperationException("Cannot delete that URL: " + uri);
        }
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);
        switch (match) {
            case CALLS:
                return ScCalls.CONTENT_TYPE;
            case CALLS_ID:
                return ScCalls.CONTENT_ITEM_TYPE;
            case CALLS_FILTER:
                return ScCalls.CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return null;

        DbQueryUtils.checkForSupportedColumns(sCallsProjectionMap, values);

        SQLiteDatabase db = mDbHelper.getDatabase(true);
        long rowId = db.insert(Tables.CALLS, null, values);
        if (rowId > 0) {
            notifyCallLogChange();
            return ContentUris.withAppendedId(uri, rowId);
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        final Context context = context();
        mDbHelper = getDatabaseHelper(context);

        mDbHelper.checkRegisterKeyManager(true);
//        mUseStrictPhoneNumberComparation =
//            context.getResources().getBoolean(
//                    com.android.internal.R.bool.config_use_strict_phone_number_comparation);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return null;

        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(Tables.CALLS);
        qb.setProjectionMap(sCallsProjectionMap);
//        qb.setStrict(true); TODO - available since API 14

        final SelectionBuilder selectionBuilder = new SelectionBuilder(selection);

        final int match = sURIMatcher.match(uri);
        switch (match) {
            case CALLS:
                break;

            case CALLS_ID: {
                selectionBuilder.addClause(DbQueryUtils.getEqualityClause(ScCalls._ID, parseCallIdFromUri(uri)));
                break;
            }

            case CALLS_FILTER: {
                String phoneNumber = uri.getPathSegments().get(2);
                qb.appendWhere("PHONE_NUMBERS_EQUAL(number, ");
                qb.appendWhereEscapeString(phoneNumber);
                qb.appendWhere(mUseStrictPhoneNumberComparation ? ", 1)" : ", 0)");
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }

        final int limit = getIntParam(uri, ScCalls.LIMIT_PARAM_KEY, 0);
        final int offset = getIntParam(uri, ScCalls.OFFSET_PARAM_KEY, 0);
        String limitClause = null;
        if (limit > 0) {
            limitClause = offset + "," + limit;
        }

        final SQLiteDatabase db = mDbHelper.getDatabase(false);
        final Cursor c = qb.query(db, projection, selectionBuilder.build(), selectionArgs, null, null, sortOrder, limitClause);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), ScCallLog.CONTENT_URI);
        }
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Don't block caller if NON_BLOCK parameter is true and if DB is not ready
        if (returnOnBlocking(uri))
            return 0;

        DbQueryUtils.checkForSupportedColumns(sCallsProjectionMap, values);

        SelectionBuilder selectionBuilder = new SelectionBuilder(selection);

        final SQLiteDatabase db = mDbHelper.getDatabase(true);
        final int matchedUriId = sURIMatcher.match(uri);
        switch (matchedUriId) {
            case CALLS:
                break;

            case CALLS_ID:
                selectionBuilder.addClause(DbQueryUtils.getEqualityClause(ScCalls._ID, parseCallIdFromUri(uri)));
                break;

            default:
                throw new UnsupportedOperationException("Cannot update URL: " + uri);
        }

        int count = db.update(Tables.CALLS, values, selectionBuilder.build(), selectionArgs);
        if (count > 0) {
            notifyCallLogChange();
        }
        return count;
    }
    
    protected ScContactsDatabaseHelper getDatabaseHelper(final Context context) {
        return ScContactsDatabaseHelper.getInstance(context);
    }

    // Work around to let the test code override the context. getContext() is final so cannot be
    // overridden.
    protected Context context() {
        return getContext();
    }

    private boolean returnOnBlocking(Uri uri) {
        boolean nonBlock = ScContactsProvider.readBooleanQueryParameter(uri, ScCallLog.NON_BLOCKING, false);
        boolean dbReady = mDbHelper.isReady();
        return !dbReady && nonBlock;
    }

    /**
     * Parses the call Id from the given uri, assuming that this is a uri that
     * matches CALLS_ID. For other uri types the behaviour is undefined.
     * @throws IllegalArgumentException if the id included in the Uri is not a valid long value.
     */
    private long parseCallIdFromUri(Uri uri) {
        try {
            return Long.parseLong(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid call id in uri: " + uri, e);
        }
    }

    /**
     * Gets an integer query parameter from a given uri.
     *
     * @param uri The uri to extract the query parameter from.
     * @param key The query parameter key.
     * @param defaultValue A default value to return if the query parameter does not exist.
     * @return The value from the query parameter in the Uri.  Or the default value if the parameter
     * does not exist in the uri.
     * @throws IllegalArgumentException when the value in the query parameter is not an integer.
     */
    private int getIntParam(Uri uri, String key, int defaultValue) {
        String valueString = uri.getQueryParameter(key);
        if (valueString == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(valueString);
        } catch (NumberFormatException e) {
            String msg = "Integer required for " + key + " parameter but value '" + valueString +
                    "' was found instead.";
            throw new IllegalArgumentException(msg, e);
        }
    }

    private void notifyCallLogChange() {
        context().getContentResolver().notifyChange(ScCalls.CONTENT_URI, null, false);
    }
}
