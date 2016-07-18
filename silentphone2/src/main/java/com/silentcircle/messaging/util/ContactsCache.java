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
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.contacts.UpdateScContactDataService;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TEMPORARY:
 * Create cache with contacts for use in messaging part of application
 */
public final class ContactsCache {

    private static final String TAG = "ContactsCache";

    // Not sure about correctness this
    private static  final String SIP_DOMAIN_SILENTCIRCLE = "@sip.silentcircle.net";

    private static final String[] COLUMNS = new String[] {
            ContactsContract.Contacts.DISPLAY_NAME,                       // 0
            ContactsContract.Contacts.PHOTO_URI,                          // 1
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,                // 2
            ContactsContract.Data.DATA1,                                  // 3
            ContactsContract.CommonDataKinds.Phone.LABEL,                 // 4
            ContactsContract.Contacts.PHOTO_ID,                           // 5
            ContactsContract.Contacts.LOOKUP_KEY                          // 6
    };

    private static final int COLUMN_INDEX_DISPLAY_NAME = 0;
    private static final int COLUMN_INDEX_PHOTO_URI = 1;
    private static final int COLUMN_INDEX_THUMBNAIL_PHOTO_URI = 2;
    private static final int COLUMN_INDEX_DATA1 = 3;
    private static final int COLUMN_INDEX_PHONE_LABEL = 4;
    private static final int COLUMN_INDEX_PHOTO_ID = 5;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 6;

    // The RETRY_TIME should be longer than any network timeouts
    private static final long RETRY_TIME = 30 * 1000;                       // Try server every 30s

    private static final List<ContactEntry> mContactCache =
            Collections.synchronizedList(new ArrayList<ContactEntry>());


    private static Context mContext;
    private static boolean mDoUpdate = true;
    private static ContactsCache sContactsCache = null;

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

    public static ContactsCache getInstance(@NonNull Context context) {
        if (sContactsCache == null) {
            sContactsCache = new ContactsCache(context);
        }
        return sContactsCache;
    }

    protected ContactsCache(Context ctx) {
        mContext = ctx;
        mContext.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, mChangeObserver);
    }

    public static void buildContactsCache(final Context context) {
        if (!mDoUpdate)
            return;
        mDoUpdate = false;

        synchronized (mContactCache) {
            mContactCache.clear();
        }

        Uri lookupUri = ContactsContract.Data.CONTENT_URI;
        String selection = ContactsContract.Data.MIMETYPE + "='" +
                UpdateScContactDataService.SC_MSG_CONTENT_TYPE + "' OR " +
                ContactsContract.Data.MIMETYPE + "='" + UpdateScContactDataService.SC_PHONE_CONTENT_TYPE + "'";

        List<ContactEntry> entryList = new ArrayList<>();
        Cursor cursor = context.getContentResolver().query(lookupUri, COLUMNS, selection, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {

                // NGA-497: if the cursor has a wrong column count (why?) then skip it (Sam S.)
                if (cursor.getColumnCount() != COLUMNS.length)
                    continue;
                ContactEntry contactEntry = new ContactEntry();
                contactEntry.name = cursor.getString(COLUMN_INDEX_DISPLAY_NAME);
                contactEntry.phoneLabel = cursor.getString(COLUMN_INDEX_PHONE_LABEL);
                contactEntry.phoneNumber = Utilities.removeUriPartsSelective(cursor.getString(COLUMN_INDEX_DATA1));

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
                contactEntry.lookupKey = lookupKey;
                contactEntry.lookupUri = ContactsContract.Contacts.getLookupUri(contactEntry.id, lookupKey);
                entryList.add(contactEntry);

                // left for now for debug
//                int c = cursor.getColumnCount();
//                for (int i = 0; i < c; i++) {
//                    Log.d(TAG, "++++ " + cursor.getColumnName(i) + " = [" + cursor.getString(i) + "]");
//                }
            }
            cursor.close();
        }
        if (entryList.size() > 0) {
            synchronized (mContactCache) {
                mContactCache.addAll(entryList);
            }
        }
    }

    @MainThread
    public static ContactEntry getContactEntryFromCache(final String name) {
        ContactEntry result = null;
//        Log.d(TAG, "++++ look for name: " + name);
        if (!TextUtils.isEmpty(name)) {

            synchronized (mContactCache) {
                for (ContactEntry entry : mContactCache) {
//                    Log.d(TAG, "++++ entry name: " + entry.name + ", imName: " + entry.imName);
                    if (name.equals(entry.name) || name.equals(entry.imName)) {
                        result = entry;
                        break;
                    }
//                    Log.d(TAG, "++++ entry number: " + entry.phoneNumber);
                    if (name.equals(entry.phoneNumber)) {
                        result = entry;
                        break;
                    }
                }
                if (result != null && TextUtils.isEmpty(result.name) && result.timeCreated != 0) {
                    if (result.timeCreated + RETRY_TIME < System.currentTimeMillis()) {
                        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Retry to get user contact data after timeout");
                        // Keep the existing entry until we got some result from server. The RETRY_TIME
                        // should be longer than any network timeouts
                        result.timeCreated = System.currentTimeMillis();
                        loadUserDataBackground(name, result);
                    }
                }
                // If result is null then try to get a alias mapping. If successful create a stub
                // entry, set the entry's name to the mapped display name and the name parameter
                // to the phone name. If another cache lookup with the same name shows up we find
                // the stub entry.
                if (result == null) {
                    byte[] dpName = null;
                    byte[] userInfo = null;
                    if (AxoMessaging.getInstance(mContext).isReady()) {
                        dpName = AxoMessaging.getDisplayName(name);
                        userInfo = AxoMessaging.getUserInfoFromCache(name);
                    }
                    if (dpName != null) {
                        result = createTemporaryContactEntry(new String(dpName));
                        result.phoneNumber = name;

                        if (userInfo != null) {
                            AsyncTasks.UserInfo ui = AsyncTasks.parseUserInfo(userInfo);
                            if (ui != null) {
                                if (!TextUtils.isEmpty(ui.mAvatarUrl) && !LoadUserInfo.URL_AVATAR_NOT_SET.equals(ui.mAvatarUrl)) {
                                    result.photoUri = Uri.parse(ConfigurationUtilities.getProvisioningBaseUrl(SilentPhoneApplication.getAppContext()) + ui.mAvatarUrl);
                                }
                            }
                        }
                        mContactCache.add(result);
                    }
                    else {
                        loadUserDataBackground(name, null);
                    }
                }
                if (result == null) {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "No valid messaging contact entry found for: '" + name + "'");
                }
            }
        }
        return result;
    }

    private static void loadUserDataBackground(final String name, final ContactEntry oldEntry) {
        AsyncTasks.UserDataBackgroundTaskNotMain getNameTask = new AsyncTasks.UserDataBackgroundTaskNotMain(name) {
            @Override
            @WorkerThread
            public void onPostRun() {
                ContactEntry result;
                String photoUri = null;
                if (mUserInfo != null) {
                    if (!TextUtils.isEmpty(mUserInfo.mAvatarUrl) && !LoadUserInfo.URL_AVATAR_NOT_SET.equals(mUserInfo.mAvatarUrl)) {
                        photoUri = mUserInfo.mAvatarUrl;
                    }

                    result = createTemporaryContactEntry(mUserInfo.mDisplayName);
                    result.phoneNumber = name;
                    if (!TextUtils.isEmpty(photoUri)) {
                        result.photoUri = Uri.parse(ConfigurationUtilities.getProvisioningBaseUrl(SilentPhoneApplication.getAppContext()) + photoUri);
                    }
                }
                else {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Scheduling timeout to get user contact data.");
                    result = createTemporaryContactEntry("");
                    result.phoneNumber = name;
                    result.timeCreated = System.currentTimeMillis();
                }
                if (oldEntry != null) {
                    synchronized (mContactCache) {
                        mContactCache.remove(oldEntry);
                    }
                }
                synchronized (mContactCache) {
                    mContactCache.add(result);
                }
                // notify conversation list or conversation view to force refresh
                MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(), name, false);
            }
        };
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Get user data from server/lookup cache for " + name);
        AsyncUtils.executeSerial(getNameTask);
    }

    /**
     * Creates ContactEntry instance filling in fields name and lookupUri.
     *
     * @param name - Unknown SIP name
     * @return ContactEntry instance.
     */
    private static ContactEntry createTemporaryContactEntry(final String name) {
        ContactEntry updatedInfo = new ContactEntry();
        updatedInfo.name = name;
        updatedInfo.lookupUri = createTemporaryContactUri(name);
        updatedInfo.lookupKey = name;
        return updatedInfo;
    }

    /**
     * Adapted from ContactInfoHelper#createTemporaryContactUri
     *
     * Creates a JSON-encoded lookup uri for a unknown number without an associated contact
     *
     * @param name - Assumed SIP name
     * @return JSON-encoded URI that can be used to perform a lookup when clicking on the quick
     *         contact card.
     */
    private static Uri createTemporaryContactUri(String name) {
        try {
            final JSONObject contactRows = new JSONObject().put(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                    new JSONObject().put(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,
                            Utilities.isUriNumber(name) ? name : name + SIP_DOMAIN_SILENTCIRCLE));

            final String jsonString = new JSONObject().put(ContactsContract.Contacts.DISPLAY_NAME, name)
                    .put(ContactsContract.Contacts.DISPLAY_NAME_SOURCE, ContactsContract.DisplayNameSources.PHONE)
                    .put(ContactsContract.Contacts.CONTENT_ITEM_TYPE, contactRows).toString();

            return ContactsContract.Contacts.CONTENT_LOOKUP_URI
                    .buildUpon()
                    .appendPath(Constants.LOOKUP_URI_ENCODED)
                    .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                            String.valueOf(Long.MAX_VALUE))
                    .encodedFragment(jsonString)
                    .build();
        } catch (JSONException e) {
            return null;
        }
    }
}
