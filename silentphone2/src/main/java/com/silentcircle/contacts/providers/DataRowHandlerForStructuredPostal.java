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
import android.text.TextUtils;

import com.silentcircle.contacts.providers.aggregation.SimpleRawContactAggregator;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;


/**
 * Handler for postal address data rows.
 */
public class DataRowHandlerForStructuredPostal extends DataRowHandler {

    /**
     * Specific list of structured fields.
     */
    private final String[] STRUCTURED_FIELDS = new String[] {
            StructuredPostal.STREET,
            StructuredPostal.POBOX,
            StructuredPostal.NEIGHBORHOOD,
            StructuredPostal.CITY,
            StructuredPostal.REGION,
            StructuredPostal.POSTCODE,
            StructuredPostal.COUNTRY,
    };

    private final PostalSplitter mSplitter;

    public DataRowHandlerForStructuredPostal(Context context, ScContactsDatabaseHelper dbHelper,
            SimpleRawContactAggregator aggregator, PostalSplitter splitter) {

        super(context, dbHelper, aggregator, StructuredPostal.CONTENT_ITEM_TYPE);
        mSplitter = splitter;
    }

    @Override
    public long insert(SQLiteDatabase db, TransactionContext txContext, long rawContactId,
            ContentValues values) {
        fixStructuredPostalComponents(values, values);
        return super.insert(db, txContext, rawContactId, values);
    }

    @Override
    public boolean update(SQLiteDatabase db, TransactionContext txContext, ContentValues values,
            Cursor c, boolean callerIsSyncAdapter) {
        final long dataId = c.getLong(DataUpdateQuery._ID);
        final ContentValues augmented = getAugmentedValues(db, dataId, values);
        if (augmented == null) {    // No change
            return false;
        }

        fixStructuredPostalComponents(augmented, values);
        super.update(db, txContext, values, c, callerIsSyncAdapter);
        return true;
    }

    /**
     * Prepares the given {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.StructuredPostal} row, building
     * {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.StructuredPostal#FORMATTED_ADDRESS} to match the structured
     * values when missing. When structured components are missing, the
     * unstructured value is assigned to {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.StructuredPostal#STREET}.
     */
    private void fixStructuredPostalComponents(ContentValues augmented, ContentValues update) {
        final String unstruct = update.getAsString(StructuredPostal.FORMATTED_ADDRESS);

        final boolean touchedUnstruct = !TextUtils.isEmpty(unstruct);
        final boolean touchedStruct = !areAllEmpty(update, STRUCTURED_FIELDS);

        final PostalSplitter.Postal postal = new PostalSplitter.Postal();

        if (touchedUnstruct && !touchedStruct) {
            mSplitter.split(postal, unstruct);
            postal.toValues(update);
        } else if (!touchedUnstruct
                && (touchedStruct || areAnySpecified(update, STRUCTURED_FIELDS))) {
            postal.fromValues(augmented);
            final String joined = mSplitter.join(postal);
            update.put(StructuredPostal.FORMATTED_ADDRESS, joined);
        }
    }


    @Override
    public boolean hasSearchableData() {
        return true;
    }

    @Override
    public boolean containsSearchableColumns(ContentValues values) {
        return values.containsKey(StructuredPostal.FORMATTED_ADDRESS);
    }

    @Override
    public void appendSearchableData(SearchIndexManager.IndexBuilder builder) {
        builder.appendContentFromColumn(StructuredPostal.FORMATTED_ADDRESS);
    }
}
