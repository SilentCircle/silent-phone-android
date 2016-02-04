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
package com.silentcircle.messaging.util;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.common.list.ContactEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TEMPORARY:
 * Create cache with contacts for use in messaging part of application
 */
public final class ContactsCache {

    private static final String[] COLUMNS = new String[] {
            ContactsContract.Contacts._ID,                                // 0
            ContactsContract.Contacts.DISPLAY_NAME,                       // 1
            ContactsContract.Contacts.PHOTO_URI,                          // 2
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,                // 3
            ContactsContract.CommonDataKinds.Phone.NUMBER,                // 4
            ContactsContract.CommonDataKinds.Phone.LABEL,                 // 5
            ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,      // 6
            ContactsContract.Contacts.PHOTO_ID,                           // 7
            ContactsContract.Contacts.LOOKUP_KEY                          // 8
    };

    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_DISPLAY_NAME = 1;
    private static final int COLUMN_INDEX_PHOTO_URI = 2;
    private static final int COLUMN_INDEX_THUMBNAIL_PHOTO_URI = 3;
    private static final int COLUMN_INDEX_PHONE_NUMBER = 4;
    private static final int COLUMN_INDEX_PHONE_LABEL = 5;
    private static final int COLUMN_INDEX_SIP_ADDRESS = 6;
    private static final int COLUMN_INDEX_PHOTO_ID = 7;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 8;

    private static final List<ContactEntry> mContactCache =
            Collections.synchronizedList(new ArrayList<ContactEntry>());


    final private Context mContext;
    private static boolean mDoUpdate = true;

    private ContentObserver mChangeObserver = new ContentObserver(new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            mDoUpdate = true;
            buildContactsCache(mContext);
        }
    };

    public ContactsCache(Context ctx) {
        mContext = ctx;
        mContext.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, mChangeObserver);
    }

    public static void buildContactsCache(final Context context) {
        if (!mDoUpdate)
            return;
        mDoUpdate = false;

        Uri uri;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Uri.Builder uriBuilder = ContactsContract.Contacts.CONTENT_STREQUENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true");
            uri = uriBuilder.build();
//        }
//        else {
//            uri = ContactsContract.Contacts.CONTENT_STREQUENT_URI;
//        }

        List<ContactEntry> entryList = new ArrayList<>();
        Cursor cursor = context.getContentResolver().query(uri, COLUMNS, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ContactEntry contactEntry = new ContactEntry();
                contactEntry.id = cursor.getLong(COLUMN_INDEX_ID);
                contactEntry.name = cursor.getString(COLUMN_INDEX_DISPLAY_NAME);
                contactEntry.phoneLabel = cursor.getString(COLUMN_INDEX_PHONE_LABEL);
                contactEntry.phoneNumber = cursor.getString(COLUMN_INDEX_PHONE_NUMBER);
                contactEntry.imName = cursor.getString(COLUMN_INDEX_SIP_ADDRESS);

                String photoUri = cursor.getString(COLUMN_INDEX_PHOTO_URI);
                if (!TextUtils.isEmpty(photoUri)) {
                    contactEntry.photoUri = Uri.parse(photoUri);
                }

                String thumbnailUri = cursor.getString(COLUMN_INDEX_THUMBNAIL_PHOTO_URI);
                if (!TextUtils.isEmpty(thumbnailUri)) {
                    contactEntry.thumbnailUri = Uri.parse(thumbnailUri);
                }

                contactEntry.photoId = cursor.getLong(COLUMN_INDEX_PHOTO_ID);
                String lookupKey = cursor.getString(COLUMN_INDEX_LOOKUP_KEY);
                contactEntry.lookupUri = ContactsContract.Contacts.getLookupUri(contactEntry.id, lookupKey);
                entryList.add(contactEntry);

                // left for now for debug
                /*
                int c = cursor.getColumnCount();
                for (int i = 0; i < c; i++) {
                    System.out.println("AAA (1) " + cursor.getColumnName(i)
                            + " = [" + cursor.getString(i) + "]");
                }
                 */
            }
            cursor.close();
        }
        if (entryList.size() > 0) {
            synchronized (mContactCache) {
                mContactCache.clear();
                mContactCache.addAll(entryList);
            }
        }
    }

    public static ContactEntry getContactEntryFromCache(final String name) {
        ContactEntry result = null;
        if (!TextUtils.isEmpty(name)) {
            synchronized (mContactCache) {
                for (ContactEntry entry : mContactCache) {
                    if (name.equals(entry.name) || name.equals(entry.imName)) {
                        result = entry;
                        break;
                    }
                    if (entry.imName != null && entry.imName.startsWith(name + "@")) {
                        result = entry;
                        break;
                    }
                }
            }
        }
        return result;
    }

}
