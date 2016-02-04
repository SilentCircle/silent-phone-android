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

package com.silentcircle.common.util;

/**
 * Functions to update data usage statistic.
 * 
 * Mainly copied from Android source and then adapted to the Silent Phone use case. Remove
 * code that supports legacy API, update number lookup to cover SIP addresses, etc.
 *
 * Created by werner on 09.02.15.
 */

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.DataUsageFeedback;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;

import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DataUsageStatUpdater {
    private static final String TAG = DataUsageStatUpdater.class.getSimpleName();

    private final ContentResolver mResolver;

    public DataUsageStatUpdater(Context context) {
        mResolver = context.getContentResolver();
    }

    /**
     * Updates usage statistics using comma-separated RFC822 address like 
     * "Joe <joe@example.com>, Due <due@example.com>". 
     *
     * This will cause Disk access so should be called in a background thread. 
     *
     * @return true when update request is correctly sent. False when the request fails, 
     * input has no valid entities. 
     */
    public boolean updateWithRfc822Address(Collection<CharSequence> texts){
        if (texts == null) {
            return false;
        } else {
            final Set<String> addresses = new HashSet<>();
            for (CharSequence text : texts) {
                Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(text.toString().trim());
                for (Rfc822Token token : tokens) {
                    addresses.add(token.getAddress());
                }
            }
            return updateWithAddress(addresses);
        }
    }

    /**
     * Update usage statistics information using a list of email addresses. 
     *
     * This will cause Disk access so should be called in a background thread. 
     *
     * @see #update(Collection, Collection, String)
     *
     * @return true when update request is correctly sent. False when the request fails, 
     * input has no valid entities. 
     */
    public boolean updateWithAddress(Collection<String> addresses) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "updateWithAddress: " + Arrays.toString(addresses.toArray()));
        }
        if (addresses != null && !addresses.isEmpty()) {
            final ArrayList<String> whereArgs = new ArrayList<>();
            final StringBuilder whereBuilder = new StringBuilder();
            final String[] questionMarks = new String[addresses.size()];

            whereArgs.addAll(addresses);
            Arrays.fill(questionMarks, "?");
            // Email.ADDRESS == Email.DATA1. Email.ADDRESS can be available from API Level 11. 
            whereBuilder.append(Email.DATA1 + " IN (")
                    .append(TextUtils.join(",", questionMarks))
                    .append(")");
            final Cursor cursor = mResolver.query(Email.CONTENT_URI,
                    new String[] {Email.RAW_CONTACT_ID, Email._ID}, whereBuilder.toString(),
                    whereArgs.toArray(new String[0]), null);

            if (cursor == null) {
                Log.w(TAG, "Cursor for Email.CONTENT_URI became null.");
            } else {
                final Set<Long> dataIds = new HashSet<>(cursor.getCount());
                try {
                    cursor.move(-1);
                    while(cursor.moveToNext()) {
                        dataIds.add(cursor.getLong(1));
                    }
                } finally {
                    cursor.close();
                }
                return update(dataIds, DataUsageFeedback.USAGE_TYPE_LONG_TEXT);
            }
        }

        return false;
    }

    /**
     * Update usage statistics information using a list of phone numbers. 
     *
     * This will cause Disk access so should be called in a background thread. 
     *
     * @see #update(Collection, Collection, String)
     *
     * @return true when update request is correctly sent. False when the request fails, 
     * input has no valid entities. 
     */
    public boolean updateWithPhoneNumber(String number, long contactId) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "updateWithPhoneNumber: " + number);
        if (number == null)
            return false;

        if (number.charAt(0) == '+') {
            number = number.substring(1);
        }
        String where = "(" + Phone.NUMBER + " LIKE '%" + number +"' OR " + Phone.NORMALIZED_NUMBER + " LIKE '%" + number + "') AND "
                + Phone.CONTACT_ID +"=" + contactId;
        final Cursor cursor = mResolver.query(ContactsContract.Data.CONTENT_URI,
                new String[] {Phone.CONTACT_ID, Phone._ID, Phone.NORMALIZED_NUMBER, Phone.DATA1}, where, null, null);

        if (cursor == null) {
            Log.w(TAG, "Cursor for Phone.CONTENT_URI became null.");
        }
        else {
            final Set<Long> dataIds = new HashSet<>(cursor.getCount());
            try {
                cursor.move(-1);
                while(cursor.moveToNext()) {
                    dataIds.add(cursor.getLong(1));
                }
            } finally {
                cursor.close();
            }
            return update(dataIds, DataUsageFeedback.USAGE_TYPE_CALL);
        }

        return false;
    }

    /**
     * @return true when one or more of update requests are correctly sent. 
     * False when all the requests fail. 
     */
    private boolean update(Collection<Long> dataIds, String type) {
        boolean successful = false;

        // From ICS (SDK_INT 14) we can use per-contact-method structure. We'll check if the device 
        // supports it and call the API. 
        if (Build.VERSION.SDK_INT >= 14) {
            if (dataIds.isEmpty()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Given list for data IDs is null. Ignoring.");
                }
            } else {
                final Uri uri = DataUsageFeedback.FEEDBACK_URI.buildUpon()
                        .appendPath(TextUtils.join(",", dataIds))
                        .appendQueryParameter(DataUsageFeedback.USAGE_TYPE, type)
                        .build();
                if (mResolver.update(uri, new ContentValues(), null, null) > 0) {
                    successful = true;
                } else {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "update toward data rows " + dataIds + " failed");
                    }
                }
            }
        } 
        return successful;
    }
}
