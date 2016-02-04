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
 * Copyright (C) 2011 The Android Open Source Project
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
package com.silentcircle.contacts.providers;

import android.content.ContentValues;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DataColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.MimetypesColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.RawContactsColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.SearchIndexColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Nickname;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Organization;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.ProviderStatus;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maintains a search index for comprehensive contact search.
 */
public class SearchIndexManager {
    private static final String TAG = "SearchIndexManager";

    private static final boolean VERBOSE_LOGGING = false;  // Log.isLoggable(TAG, Log.VERBOSE);

    public static final String PROPERTY_SEARCH_INDEX_VERSION = "search_index";
    private static final int SEARCH_INDEX_VERSION = 1;

    private static final class ContactIndexQuery {
        public static final String[] COLUMNS = {
                Data.RAW_CONTACT_ID,
                MimetypesColumns.MIMETYPE,
                Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
                Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10, Data.DATA11,
                Data.DATA12, Data.DATA13, Data.DATA14
        };

        public static final int MIMETYPE = 1;
    }

    public static class IndexBuilder {
        public static final int SEPARATOR_SPACE = 0;
        public static final int SEPARATOR_PARENTHESES = 1;
        public static final int SEPARATOR_SLASH = 2;
        public static final int SEPARATOR_COMMA = 3;

        private StringBuilder mSbContent = new StringBuilder();
        private StringBuilder mSbName = new StringBuilder();
        private StringBuilder mSbTokens = new StringBuilder();
        private StringBuilder mSbElementContent = new StringBuilder();
        private HashSet<String> mUniqueElements = new HashSet<String>();
        private Cursor mCursor;

        void setCursor(Cursor cursor) {
            this.mCursor = cursor;
        }

        void reset() {
            mSbContent.setLength(0);
            mSbTokens.setLength(0);
            mSbName.setLength(0);
            mSbElementContent.setLength(0);
            mUniqueElements.clear();
        }

        public String getContent() {
            return mSbContent.length() == 0 ? null : mSbContent.toString();
        }

        public String getName() {
            return mSbName.length() == 0 ? null : mSbName.toString();
        }

        public String getTokens() {
            return mSbTokens.length() == 0 ? null : mSbTokens.toString();
        }

        public String getString(String columnName) {
            return mCursor.getString(mCursor.getColumnIndex(columnName));
        }

        public int getInt(String columnName) {
            return mCursor.getInt(mCursor.getColumnIndex(columnName));
        }

        @Override
        public String toString() {
            return "Content: " + mSbContent + "\n Name: " + mSbTokens + "\n Tokens: " + mSbTokens;
        }

        public void commit() {
            if (mSbElementContent.length() != 0) {
                String content = mSbElementContent.toString().replace('\n', ' ');
                if (!mUniqueElements.contains(content)) {
                    if (mSbContent.length() != 0) {
                        mSbContent.append('\n');
                    }
                    mSbContent.append(content);
                    mUniqueElements.add(content);
                }
                mSbElementContent.setLength(0);
            }
        }

        public void appendContentFromColumn(String columnName) {
            appendContentFromColumn(columnName, SEPARATOR_SPACE);
        }

        public void appendContentFromColumn(String columnName, int format) {
            appendContent(getString(columnName), format);
        }

        public void appendContent(String value) {
            appendContent(value, SEPARATOR_SPACE);
        }

        private void appendContent(String value, int format) {
            if (TextUtils.isEmpty(value)) {
                return;
            }

            switch (format) {
                case SEPARATOR_SPACE:
                    if (mSbElementContent.length() > 0) {
                        mSbElementContent.append(' ');
                    }
                    mSbElementContent.append(value);
                    break;

                case SEPARATOR_SLASH:
                    mSbElementContent.append('/').append(value);
                    break;

                case SEPARATOR_PARENTHESES:
                    if (mSbElementContent.length() > 0) {
                        mSbElementContent.append(' ');
                    }
                    mSbElementContent.append('(').append(value).append(')');
                    break;

                case SEPARATOR_COMMA:
                    if (mSbElementContent.length() > 0) {
                        mSbElementContent.append(", ");
                    }
                    mSbElementContent.append(value);
                    break;
            }
        }

        public void appendToken(String token) {
            if (TextUtils.isEmpty(token)) {
                return;
            }

            if (mSbTokens.length() != 0) {
                mSbTokens.append(' ');
            }
            mSbTokens.append(token);
        }

        public void appendName(String name) {
            if (TextUtils.isEmpty(name)) {
                return;
            }
            // First, put the original name.
            appendNameInternal(name);

            // Then, if the name contains more than one FTS token, put each token into the index
            // too.
            //
            // This is to make names with special characters searchable, such as "double-barrelled"
            // "L'Image".
            //
            // Here's how it works:
            // Because we "normalize" names when putting into the index, if we only put
            // "double-barrelled", the index will only contain "doublebarrelled".
            // Now, if the user searches for "double-barrelled", the searcher tokenizes it into
            // two tokens, "double" and "barrelled".  The first one matches "doublebarrelled"
            // but the second one doesn't (because we only do the prefix match), so
            // "doublebarrelled" doesn't match.
            // So, here, we put each token in a name into the index too.  In the case above,
            // we put also "double" and "barrelled".
            // With this, queries such as "double-barrelled", "double barrelled", "doublebarrelled"
            // will all match "double-barrelled".
            final List<String> nameParts = splitIntoFtsTokens(name);
            if (nameParts.size() > 1) {
                for (String namePart : nameParts) {
                    if (!TextUtils.isEmpty(namePart)) {
                        appendNameInternal(namePart);
                    }
                }
            }
        }

        /**
         * Normalize a name and add to {@link #mSbName}
         */
        private void appendNameInternal(String name) {
            if (mSbName.length() != 0) {
                mSbName.append(' ');
            }
            String normName = NameNormalizer.normalize(name);
            mSbName.append(normName);
        }
    }

    private final ScContactsProvider mContactsProvider;
    private final ScContactsDatabaseHelper mDbHelper;
    private StringBuilder mSb = new StringBuilder();
    private IndexBuilder mIndexBuilder = new IndexBuilder();
    private ContentValues mValues = new ContentValues();
    private String[] mSelectionArgs1 = new String[1];

    public SearchIndexManager(ScContactsProvider contactsProvider) {
        this.mContactsProvider = contactsProvider;
        mDbHelper = (ScContactsDatabaseHelper) mContactsProvider.getDatabaseHelper();
    }

    public void updateIndex(boolean force) {
        if (force) {
            setSearchIndexVersion(0);
        } else {
            if (getSearchIndexVersion() == SEARCH_INDEX_VERSION) {
                return;
            }
        }
        SQLiteDatabase db = mDbHelper.getDatabase(true);
        db.beginTransaction();
        try {
            // We do a version check again, because the version might have been modified after
            // the first check.  We need to do the check again in a transaction to make sure.
            if (getSearchIndexVersion() != SEARCH_INDEX_VERSION) {
                rebuildIndex(db);
                setSearchIndexVersion(SEARCH_INDEX_VERSION);
                db.setTransactionSuccessful();
            }
        } finally {
            db.endTransaction();
        }
    }

    private void rebuildIndex(SQLiteDatabase db) {
        mContactsProvider.setProviderStatus(ProviderStatus.STATUS_UPGRADING);
        final long start = SystemClock.elapsedRealtime();
        int count = 0;
        try {
            mDbHelper.createSearchIndexTable(db);
            count = buildAndInsertIndex(db, null);
        } finally {
            mContactsProvider.setProviderStatus(ProviderStatus.STATUS_NORMAL);

            final long end = SystemClock.elapsedRealtime();
            Log.i(TAG, "Rebuild contact search index in " + (end - start) + "ms, "
                    + count + " contacts");
        }
    }

    public void updateIndexForRawContacts(Set<Long> rawContactIds) {
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "Updating search index for " + rawContactIds.size() + " raw contacts");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        if (!rawContactIds.isEmpty()) {
            sb.append(RawContactsColumns.CONCRETE_ID + " IN (");
            for (Long rawContactId : rawContactIds) {
                sb.append(rawContactId).append(",");
            }
            sb.setLength(sb.length() - 1);
            sb.append(')');
        }

        sb.append(")");

        // The selection to select raw_contacts.
        final String rawContactsSelection = sb.toString();

        // Remove affected search_index rows.
        final SQLiteDatabase db = mDbHelper.getDatabase(true);
        final int deleted = db.delete(Tables.SEARCH_INDEX,
                SearchIndexColumns.RAW_CONTACT_ID + " IN (SELECT " +
                    RawContacts._ID +
                    " FROM " + Tables.RAW_CONTACTS +
                    " WHERE " + rawContactsSelection +
                    ")"
                , null);

        // Then rebuild index for them.
        final int count = buildAndInsertIndex(db, rawContactsSelection);
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "Updated search index for " + count + " contacts");
        }
    }

    private int buildAndInsertIndex(SQLiteDatabase db, String selection) {
        mSb.setLength(0);
        mSb.append(Data.RAW_CONTACT_ID + ", ");
        mSb.append("(CASE WHEN " + DataColumns.MIMETYPE_ID + "=");
        mSb.append(mDbHelper.getMimeTypeId(Nickname.CONTENT_ITEM_TYPE));
        mSb.append(" THEN -4 ");
        mSb.append(" WHEN " + DataColumns.MIMETYPE_ID + "=");
        mSb.append(mDbHelper.getMimeTypeId(Organization.CONTENT_ITEM_TYPE));
        mSb.append(" THEN -3 ");
        mSb.append(" WHEN " + DataColumns.MIMETYPE_ID + "=");
        mSb.append(mDbHelper.getMimeTypeId(StructuredPostal.CONTENT_ITEM_TYPE));
        mSb.append(" THEN -2");
        mSb.append(" WHEN " + DataColumns.MIMETYPE_ID + "=");
        mSb.append(mDbHelper.getMimeTypeId(Email.CONTENT_ITEM_TYPE));
        mSb.append(" THEN -1");
        mSb.append(" ELSE " + DataColumns.MIMETYPE_ID);
        mSb.append(" END), " + Data.IS_SUPER_PRIMARY + ", " + DataColumns.CONCRETE_ID);

        int count = 0;
        Cursor cursor = db.query(Tables.DATA_JOIN_MIMETYPE_RAW_CONTACTS, ContactIndexQuery.COLUMNS,
                selection, null, null, null, mSb.toString());
        mIndexBuilder.setCursor(cursor);
        mIndexBuilder.reset();
        try {
            long currentContactId = -1;
            while (cursor.moveToNext()) {
                long contactId = cursor.getLong(0);
                if (contactId != currentContactId) {
                    if (currentContactId != -1) {
                        insertIndexRow(db, currentContactId, mIndexBuilder);
                        count++;
                    }
                    currentContactId = contactId;
                    mIndexBuilder.reset();
                }
                String mimetype = cursor.getString(ContactIndexQuery.MIMETYPE);
                DataRowHandler dataRowHandler = mContactsProvider.getDataRowHandler(mimetype);
                if (dataRowHandler.hasSearchableData()) {
                    dataRowHandler.appendSearchableData(mIndexBuilder);
                    mIndexBuilder.commit();
                }
            }
            if (currentContactId != -1) {
                insertIndexRow(db, currentContactId, mIndexBuilder);
                count++;
            }
        } finally {
            cursor.close();
        }
        return count;
    }

    private void insertIndexRow(SQLiteDatabase db, long contactId, IndexBuilder builder) {
        mValues.clear();
        mValues.put(SearchIndexColumns.CONTENT, builder.getContent());
        mValues.put(SearchIndexColumns.NAME, builder.getName());
        mValues.put(SearchIndexColumns.TOKENS, builder.getTokens());
        mValues.put(SearchIndexColumns.RAW_CONTACT_ID, contactId);
        db.insert(Tables.SEARCH_INDEX, null, mValues);
    }
    private int getSearchIndexVersion() {
        return Integer.parseInt(mDbHelper.getProperty(PROPERTY_SEARCH_INDEX_VERSION, "0"));
    }

    private void setSearchIndexVersion(int version) {
        mDbHelper.setProperty(PROPERTY_SEARCH_INDEX_VERSION, String.valueOf(version));
    }

    /**
     * Token separator that matches SQLite's "simple" tokenizer.
     * - Unicode codepoints >= 128: Everything
     * - Unicode codepoints < 128: Alphanumeric and "_"
     * - Everything else is a separator of tokens
     */
    private static final Pattern FTS_TOKEN_SEPARATOR_RE =
            Pattern.compile("[^\u0080-\uffff\\p{Alnum}_]");

    /**
     * Tokenize a string in the way as that of SQLite's "simple" tokenizer.
     */
//    @VisibleForTesting
    static List<String> splitIntoFtsTokens(String s) {
        final ArrayList<String> ret = new ArrayList<String>();
        for (String token : FTS_TOKEN_SEPARATOR_RE.split(s)) {
            if (!TextUtils.isEmpty(token)) {
                ret.add(token);
            }
        }
        return ret;
    }

    /**
     * Tokenizes the query and normalizes/hex encodes each token. The tokenizer uses the same
     * rules as SQLite's "simple" tokenizer. Each token is added to the retokenizer and then
     * returned as a String.
     * @see FtsQueryBuilder#UNSCOPED_NORMALIZING
     * @see FtsQueryBuilder#SCOPED_NAME_NORMALIZING
     */
    public static String getFtsMatchQuery(String query, FtsQueryBuilder ftsQueryBuilder) {
        final StringBuilder result = new StringBuilder();
        for (String token : splitIntoFtsTokens(query)) {
            ftsQueryBuilder.addToken(result, token);
        }
        return result.toString();
    }

    public static abstract class FtsQueryBuilder {
        public abstract void addToken(StringBuilder builder, String token);

        /** Normalizes and space-concatenates each token. Example: "a1b2c1* a2b3c2*" */
        public static final FtsQueryBuilder UNSCOPED_NORMALIZING = new UnscopedNormalizingBuilder();

        /**
         * Scopes each token to a column and normalizes the name.
         * Example: "content:foo* name:a1b2c1* tokens:foo* content:bar* name:a2b3c2* tokens:bar*"
         */
        public static final FtsQueryBuilder SCOPED_NAME_NORMALIZING = new ScopedNameNormalizingBuilder();

        /**
         * Scopes each token to a the content column and also for name with normalization.
         * Also adds a user-defined expression to each token. This allows common criteria to be
         * concatenated to each token.
         * Example (commonCriteria=" OR tokens:123*"):
         * "content:650* OR name:1A1B1C* OR tokens:123* content:2A2B2C* OR name:foo* OR tokens:123*"
         */
        public static FtsQueryBuilder getDigitsQueryBuilder(final String commonCriteria) {
            return new FtsQueryBuilder() {
                @Override
                public void addToken(StringBuilder builder, String token) {
                    if (builder.length() != 0) builder.append(' ');

                    builder.append("content:");
                    builder.append(token);
                    builder.append("* ");

                    final String normalizedToken = NameNormalizer.normalize(token);
                    if (!TextUtils.isEmpty(normalizedToken)) {
                        builder.append(" OR name:");
                        builder.append(normalizedToken);
                        builder.append('*');
                    }

                    builder.append(commonCriteria);
                }
            };
        }
    }

    private static class UnscopedNormalizingBuilder extends FtsQueryBuilder {
        @Override
        public void addToken(StringBuilder builder, String token) {
            if (builder.length() != 0) builder.append(' ');

            // the token could be empty (if the search query was "_"). we should still emit it
            // here, as we otherwise risk to end up with an empty MATCH-expression MATCH ""
            builder.append(NameNormalizer.normalize(token));
            builder.append('*');
        }
    }

    private static class ScopedNameNormalizingBuilder extends FtsQueryBuilder {
        @Override
        public void addToken(StringBuilder builder, String token) {
            if (builder.length() != 0) builder.append(' ');

            builder.append("content:");
            builder.append(token);
            builder.append('*');

            final String normalizedToken = NameNormalizer.normalize(token);
            if (!TextUtils.isEmpty(normalizedToken)) {
                builder.append(" OR name:");
                builder.append(normalizedToken);
                builder.append('*');
            }

            builder.append(" OR tokens:");
            builder.append(token);
            builder.append("*");
        }
    }
}
