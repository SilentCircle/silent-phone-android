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
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License
 */
package com.silentcircle.contacts.providers;

import android.content.ContentValues;
import android.content.Context;

import com.silentcircle.contacts.providers.aggregation.SimpleRawContactAggregator;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

/**
 * Handler for email address data rows.
 */
public class DataRowHandlerForEmail extends DataRowHandlerForCommonDataKind {

    public DataRowHandlerForEmail(
            Context context, ScContactsDatabaseHelper dbHelper, SimpleRawContactAggregator aggregator) {
        super(context, dbHelper, aggregator, Email.CONTENT_ITEM_TYPE, Email.TYPE, Email.LABEL);
    }

    @Override
    public long insert(SQLiteDatabase db, TransactionContext txContext, long rawContactId, ContentValues values) {
        String email = values.getAsString(Email.DATA);

        long dataId = super.insert(db, txContext, rawContactId, values);

        fixRawContactDisplayName(db, txContext, rawContactId);
        String address = mDbHelper.insertNameLookupForEmail(rawContactId, dataId, email);
        if (address != null) {
            triggerAggregation(txContext, rawContactId);
        }
        return dataId;
    }

    @Override
    public boolean update(SQLiteDatabase db, TransactionContext txContext, ContentValues values,
            Cursor c, boolean callerIsSyncAdapter) {
        if (!super.update(db, txContext, values, c, callerIsSyncAdapter)) {
            return false;
        }

        if (values.containsKey(Email.DATA)) {
            long dataId = c.getLong(DataUpdateQuery._ID);
            long rawContactId = c.getLong(DataUpdateQuery.RAW_CONTACT_ID);

            String address = values.getAsString(Email.DATA);
            mDbHelper.deleteNameLookup(dataId);
            mDbHelper.insertNameLookupForEmail(rawContactId, dataId, address);
            fixRawContactDisplayName(db, txContext, rawContactId);
            triggerAggregation(txContext, rawContactId);
        }

        return true;
    }

    @Override
    public int delete(SQLiteDatabase db, TransactionContext txContext, Cursor c) {
        long dataId = c.getLong(DataDeleteQuery._ID);
        long rawContactId = c.getLong(DataDeleteQuery.RAW_CONTACT_ID);

        int count = super.delete(db, txContext, c);

        mDbHelper.deleteNameLookup(dataId);
        fixRawContactDisplayName(db, txContext, rawContactId);
        triggerAggregation(txContext, rawContactId);
        return count;
    }

    @Override
    protected int getTypeRank(int type) {
        switch (type) {
            case Email.TYPE_HOME: return 0;
            case Email.TYPE_WORK: return 1;
            case Email.TYPE_CUSTOM: return 2;
            case Email.TYPE_OTHER: return 3;
            default: return 1000;
        }
    }

    @Override
    public boolean containsSearchableColumns(ContentValues values) {
        return values.containsKey(Email.ADDRESS);
    }

    @Override
    public void appendSearchableData(SearchIndexManager.IndexBuilder builder) {
        builder.appendContentFromColumn(Email.ADDRESS);
    }
}
