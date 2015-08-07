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
 * limitations under the License
 */

package com.silentcircle.contacts.database;

import com.silentcircle.common.testing.NeededForTesting;

import android.database.Cursor;
import android.util.Log;

/**
 * Static methods for database operations.
 */
public class MoreDatabaseUtils {

    /**
     * Builds a CREATE INDEX ddl statement for a given table and field.
     *
     * @param table The table name.
     * @param field The field to index.
     * @return The create index sql statement.
     */
    public static String buildCreateIndexSql(String table, String field) {
        return "CREATE INDEX " + buildIndexName(table, field) + " ON " + table
                + "(" + field + ")";
    }

    /**
     * Builds a DROP INDEX ddl statement for a given table and field.
     *
     * @param table The table name that was originally used to create the index.
     * @param field The field that was originally used to create the index.
     * @return The drop index sql statement.
     */
    @NeededForTesting
    public static String buildDropIndexSql(String table, String field) {
        return "DROP INDEX IF EXISTS " + buildIndexName(table, field);
    }

    /**
     * The index is created with a name using the following convention:
     * <p>
     * [table name]_[field name]_index
     */
    public static String buildIndexName(String table, String field) {
        return table + "_" + field + "_index";
    }

    /**
     * Build a bind arg where clause.
     * <p>
     * e.g. Calling this method with value of 4 results in:
     * <p>
     * "?,?,?,?"
     *
     * @param numArgs The number of arguments.
     * @return A string that can be used for bind args in a sql where clause.
     */
    @NeededForTesting
    public static String buildBindArgString(int numArgs) {
        final StringBuilder sb = new StringBuilder();
        String delimiter = "";
        for (int i = 0; i < numArgs; i++) {
            sb.append(delimiter).append("?");
            delimiter = ",";
        }
        return sb.toString();
    }

    /** Debug utility that dumps a cursor on logcat. */
    public static final void dumpCursor(String logTag, String name, Cursor c) {
        Log.d(logTag, "Dumping cursor " + name + " containing " + c.getCount() + " rows");

        // Dump the column names.
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.getColumnCount(); i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(c.getColumnName(i));
        }
        Log.d(logTag, sb.toString());

        // Dump the values.
        c.moveToPosition(-1);
        while (c.moveToNext()) {
            sb.setLength(0);
            sb.append("row#");
            sb.append(c.getPosition());

            for (int i = 0; i < c.getColumnCount(); i++) {
                sb.append(" ");

                String s = c.getString(i);
                sb.append(s == null ? "{null}" : s.replaceAll("\\s", "{space}"));
            }
            Log.d(logTag, sb.toString());
        }
    }
}
