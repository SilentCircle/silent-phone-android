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

package com.silentcircle.contacts.utils;

import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.os.MemoryFile;
import android.text.TextUtils;

import net.sqlcipher.Cursor;
import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Static methods for helping us build database query selection strings.
 */
public class DbQueryUtils {
    // Static class with helper methods, so private constructor.
    private DbQueryUtils() {
    }

    /** Returns a WHERE clause asserting equality of a field to a value. */
    public static String getEqualityClause(String field, String value) {
        return getClauseWithOperator(field, "=", value);
    }

    /** Returns a WHERE clause asserting equality of a field to a value. */
    public static String getEqualityClause(String field, long value) {
        return getClauseWithOperator(field, "=", value);
    }

    /** Returns a WHERE clause asserting in-equality of a field to a value. */
    public static String getInequalityClause(String field, long value) {
        return getClauseWithOperator(field, "!=", value);
    }

    private static String getClauseWithOperator(String field, String operator, String value) {
        StringBuilder clause = new StringBuilder();
        clause.append("(");
        clause.append(field);
        clause.append(" ").append(operator).append(" ");
        DatabaseUtils.appendEscapedSQLString(clause, value);
        clause.append(")");
        return clause.toString();
    }

    private static String getClauseWithOperator(String field, String operator, long value) {
        StringBuilder clause = new StringBuilder();
        clause.append("(");
        clause.append(field);
        clause.append(" ").append(operator).append(" ");
        clause.append(value);
        clause.append(")");
        return clause.toString();
    }

    /** Concatenates any number of clauses using "AND". */
    public static String concatenateClauses(String... clauses) {
        StringBuilder builder = new StringBuilder();
        for (String clause : clauses) {
            if (!TextUtils.isEmpty(clause)) {
                if (builder.length() > 0) {
                    builder.append(" AND ");
                }
                builder.append("(");
                builder.append(clause);
                builder.append(")");
            }
        }
        return builder.toString();
    }

    /**
     * Checks if the given ContentValues contains values within the projection  map.
     * 
     * The method uses valueSet()/Entry to get and loop over the keys. The ContentValues.getKeys() method
     * is not available in API 10, only since API 11 
     * 
     * @throws IllegalArgumentException if any value in values is not found in the projection map.
     */
    public static void checkForSupportedColumns(HashMap<String, String> projectionMap, ContentValues values) {
        Set<Entry<String, Object>> valueEntries = values.valueSet();
        for (Entry<String, Object> requestedColumn : valueEntries) {
            if (!projectionMap.keySet().contains(requestedColumn.getKey())) {
                throw new IllegalArgumentException("Column '" + requestedColumn + "' is invalid.");
            }
        }
    }

    /**
     * Escape values to be used in LIKE sqlite clause.
     *
     * The LIKE clause has two special characters: '%' and '_'.  If either of these
     * characters need to be matched literally, then they must be escaped like so:
     *
     * WHERE value LIKE 'android\_%' ESCAPE '\'
     *
     * The ESCAPE clause is required and no default exists as the escape character in this context.
     * Since the escape character needs to be defined as part of the sql string, it must be
     * provided to this method so the escape characters match.
     *
     * @param sb The StringBuilder to append the escaped value to.
     * @param value The value to be escaped.
     * @param escapeChar The escape character to be defined in the sql ESCAPE clause.
     */
    public static void escapeLikeValue(StringBuilder sb, String value, char escapeChar) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' || ch == '_') {
                sb.append(escapeChar);
            }
            sb.append(ch);
        }
    }

    /**
     * Concatenates two SQL WHERE clauses, handling empty or null values.
     */
    public static String concatenateWhere(String a, String b) {
        if (TextUtils.isEmpty(a)) {
            return b;
        }
        if (TextUtils.isEmpty(b)) {
            return a;
        }

        return "(" + a + ") AND (" + b + ")";
    }

    // TODO: the following two methods are sort of workaround for 2.3.3
    /**
     *      * **** NOTE - don't use on Android >= 4, it's doesn't work anymore.

     * Runs an SQLite query and returns an AssetFileDescriptor for the
     * blob in column 0 of the first row. If the first column does
     * not contain a blob, an unspecified exception is thrown.
     *
     * @param db Handle to a readable database.
     * @param sql SQL query, possibly with query arguments.
     * @param selectionArgs Query argument values, or {@code null} for no argument.
     * @return If no exception is thrown, a non-null AssetFileDescriptor is returned.
     * @throws java.io.FileNotFoundException If the query returns no results or the
     *         value of column 0 is NULL, or if there is an error creating the
     *         asset file descriptor.
     */
    public static AssetFileDescriptor getBlobColumnAsAssetFile(SQLiteDatabase db, String sql,
            String[] selectionArgs) throws FileNotFoundException {

        android.os.ParcelFileDescriptor fd = null;

        try {
            MemoryFile file = simpleQueryForBlobMemoryFile(db, sql, selectionArgs);
            if (file == null) {
                throw new FileNotFoundException("No results.");
            }
            Class<?> c = file.getClass();
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod("getParcelFileDescriptor");
                m.setAccessible(true);
                fd = (android.os.ParcelFileDescriptor)m.invoke(file);
            } catch (Exception e) {
                android.util.Log.i("SQLiteContentHelper", "SQLiteCursor.java: " + e);
            }
            AssetFileDescriptor afd = new AssetFileDescriptor(fd, 0, file.length());
            return afd;
        } catch (IOException ex) {
            throw new FileNotFoundException(ex.toString());
        }
    }
    /**
     * Runs an SQLite query and returns a MemoryFile for the
     * blob in column 0 of the first row. If the first column does
     * not contain a blob, an unspecified exception is thrown.
     *
     * @return A memory file, or {@code null} if the query returns no results
     *         or the value column 0 is NULL.
     * @throws java.io.IOException If there is an error creating the memory file.
     */
    // TODO: make this native and use the SQLite blob API to reduce copying
    private static MemoryFile simpleQueryForBlobMemoryFile(SQLiteDatabase db, String sql, String[] selectionArgs)
            throws IOException {

        Cursor cursor = db.rawQuery(sql, selectionArgs);
        if (cursor == null) {
            return null;
        }
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            byte[] bytes = cursor.getBlob(0);
            if (bytes == null) {
                return null;
            }
            MemoryFile file = new MemoryFile(null, bytes.length);
            file.writeBytes(bytes, 0, 0, bytes.length);
//            file.deactivate();
            return file;
        } finally {
            cursor.close();
        }
    }

    /**
     * Query the table for the number of rows in the table.
     * 
     * @param db the database the table is in
     * @param table the name of the table to query
     * @return the number of rows in the table
     */
    public static long queryNumEntries(SQLiteDatabase db, String table) {
        return longForQuery(db, "select count(*) from " + table);
    }

    /**
     * Utility method to run the query on the db and return the value in the
     * first column of the first row.
     */
    public static long longForQuery(SQLiteDatabase db, String query) {
        SQLiteStatement prog = db.compileStatement(query);
        try {
            return prog.simpleQueryForLong();
        } finally {
            prog.close();
        }
    }
}
