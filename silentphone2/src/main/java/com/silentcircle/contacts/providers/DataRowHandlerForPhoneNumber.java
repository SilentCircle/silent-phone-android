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
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.PhoneLookupColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.contacts.providers.aggregation.SimpleRawContactAggregator;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

/**
 * Handler for phone number data rows.
 */
public class DataRowHandlerForPhoneNumber extends DataRowHandlerForCommonDataKind {

    public DataRowHandlerForPhoneNumber(Context context,
            ScContactsDatabaseHelper dbHelper, SimpleRawContactAggregator aggregator) {
        super(context, dbHelper, aggregator, Phone.CONTENT_ITEM_TYPE, Phone.TYPE, Phone.LABEL);
    }

    @Override
    public long insert(SQLiteDatabase db, TransactionContext txContext, long rawContactId, ContentValues values) {

        fillNormalizedNumber(values);

        final long dataId = super.insert(db, txContext, rawContactId, values);
        if (values.containsKey(Phone.NUMBER)) {
            final String number = values.getAsString(Phone.NUMBER);
            final String normalizedNumber = values.getAsString(Phone.NORMALIZED_NUMBER);
            updatePhoneLookup(db, rawContactId, dataId, number, normalizedNumber);
            mSimpleAggregator.updateHasPhoneNumber(db, rawContactId);
            fixRawContactDisplayName(db, txContext, rawContactId);

            triggerAggregation(txContext, rawContactId);
        }
        return dataId;
    }

    @Override
    public boolean update(SQLiteDatabase db, TransactionContext txContext, ContentValues values, Cursor c,
            boolean callerIsSyncAdapter) {

        fillNormalizedNumber(values);

        if (!super.update(db, txContext, values, c, callerIsSyncAdapter)) {
            return false;
        }

        if (values.containsKey(Phone.NUMBER)) {
            long dataId = c.getLong(DataRowHandler.DataUpdateQuery._ID);
            long rawContactId = c.getLong(DataRowHandler.DataUpdateQuery.RAW_CONTACT_ID);
            updatePhoneLookup(db, rawContactId, dataId,
                    values.getAsString(Phone.NUMBER),
                    values.getAsString(Phone.NORMALIZED_NUMBER));
            mSimpleAggregator.updateHasPhoneNumber(db, rawContactId);
            fixRawContactDisplayName(db, txContext, rawContactId);

            triggerAggregation(txContext, rawContactId);
        }

        return true;
    }

    private void fillNormalizedNumber(ContentValues values) {
        // No NUMBER? Also ignore NORMALIZED_NUMBER
        if (!values.containsKey(Phone.NUMBER)) {
            values.remove(Phone.NORMALIZED_NUMBER);
            return;
        }

        // NUMBER is given. Try to extract NORMALIZED_NUMBER from it, unless it is also given
        final String number = values.getAsString(Phone.NUMBER);
        final String numberE164 = values.getAsString(Phone.NORMALIZED_NUMBER);
        if (number != null && numberE164 == null) {
            String newNumberE164 = PhoneNumberHelper.formatNumberToE164(number, mDbHelper.getCurrentCountryIso());
            // This is specific to Silent Contacts and Silent Phone: SP handles numbers from several
            // international operators and this it may not be possible to format as E.164 while
            // in fact these numbers are E.164. Thus just normalize and store it.
            if (newNumberE164 == null)
                newNumberE164 = PhoneNumberHelper.normalizeNumber(number);
            values.put(Phone.NORMALIZED_NUMBER, newNumberE164);
        }
    }

    @Override
    public int delete(SQLiteDatabase db, TransactionContext txContext, Cursor c) {
        long dataId = c.getLong(DataRowHandler.DataDeleteQuery._ID);
        long rawContactId = c.getLong(DataRowHandler.DataDeleteQuery.RAW_CONTACT_ID);

        int count = super.delete(db, txContext, c);

        updatePhoneLookup(db, rawContactId, dataId, null, null);
        mSimpleAggregator.updateHasPhoneNumber(db, rawContactId);
        fixRawContactDisplayName(db, txContext, rawContactId);
        triggerAggregation(txContext, rawContactId);
        return count;
    }

    private void updatePhoneLookup(SQLiteDatabase db, long rawContactId, long dataId, String number, String numberE164) {
        mSelectionArgs1[0] = String.valueOf(dataId);
        db.delete(Tables.PHONE_LOOKUP, PhoneLookupColumns.DATA_ID + "=?", mSelectionArgs1);
        if (number != null) {
            String normalizedNumber = PhoneNumberHelper.normalizeNumber(number);
            if (!TextUtils.isEmpty(normalizedNumber)) {
                ContentValues phoneValues = new ContentValues();
                phoneValues.put(PhoneLookupColumns.RAW_CONTACT_ID, rawContactId);
                phoneValues.put(PhoneLookupColumns.DATA_ID, dataId);
                phoneValues.put(PhoneLookupColumns.NORMALIZED_NUMBER, normalizedNumber);
                phoneValues.put(PhoneLookupColumns.MIN_MATCH, PhoneNumberUtils.toCallerIDMinMatch(normalizedNumber));
                db.insert(Tables.PHONE_LOOKUP, null, phoneValues);

                if (numberE164 != null && !numberE164.equals(normalizedNumber)) {
                    phoneValues.put(PhoneLookupColumns.NORMALIZED_NUMBER, numberE164);
                    phoneValues.put(PhoneLookupColumns.MIN_MATCH, PhoneNumberUtils.toCallerIDMinMatch(numberE164));
                    db.insert(Tables.PHONE_LOOKUP, null, phoneValues);
                }
            }
        }
    }

    @Override
    protected int getTypeRank(int type) {
        switch (type) {
        case Phone.TYPE_SILENT: return 0;
        case Phone.TYPE_MOBILE: return 1;
        case Phone.TYPE_WORK: return 2;
        case Phone.TYPE_HOME: return 3;
//            case Phone.TYPE_PAGER: return 4;
            case Phone.TYPE_CUSTOM: return 5;
//            case Phone.TYPE_OTHER: return 6;
//            case Phone.TYPE_FAX_WORK: return 7;
//            case Phone.TYPE_FAX_HOME: return 8;
            default: return 1000;
        }
    }

    @Override
    public boolean containsSearchableColumns(ContentValues values) {
        return values.containsKey(Phone.NUMBER);
    }

    @Override
    public void appendSearchableData(SearchIndexManager.IndexBuilder builder) {
        String number = builder.getString(Phone.NUMBER);
        if (TextUtils.isEmpty(number)) {
            return;
        }

        String normalizedNumber = PhoneNumberHelper.normalizeNumber(number);
        if (TextUtils.isEmpty(normalizedNumber)) {
            return;
        }

        builder.appendToken(normalizedNumber);

        String numberE164 = PhoneNumberHelper.formatNumberToE164(number, mDbHelper.getCurrentCountryIso());
        if (numberE164 != null && !numberE164.equals(normalizedNumber)) {
            builder.appendToken(numberE164);
        }
    }
}
