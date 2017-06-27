/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.calllognew.ContactInfo;
import com.silentcircle.contacts.calllognew.ContactInfoHelper;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import zina.ZinaNative;

/**
 * TEMPORARY:
 * Create cache with contacts for use in messaging part of application
 */
public final class ContactsCache {

    private static final String TAG = "ContactsCache";
    private static final boolean DEBUG = false;       // Don't submit with true

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
    private static final List<String> mLoadUserDataTaskList =
            Collections.synchronizedList(new ArrayList<String>());

    private static ContactsCache sContactsCache = null;
    protected static ContactInfoHelper sContactInfoHelper = null;

    public static boolean sIsContactObserverRegistered;

    private static ContentObserver mChangeObserver = new ContentObserver(new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            resetContactsCache();
            MessageUtils.requestRefresh();
        }
    };

    public static ContactsCache getInstance() {
        if (sContactsCache == null) {
            sContactsCache = new ContactsCache();
        }
        return sContactsCache;
    }

    protected ContactsCache() {
        Context ctx = SilentPhoneApplication.getAppContext();
        registerContactObserver();

        String currentCountryIso = ContactsUtils.getCurrentCountryIso(ctx);
        sContactInfoHelper = new ContactInfoHelper(ctx, currentCountryIso);
    }

    public static void registerContactObserver() {
        if (sIsContactObserverRegistered) {
            return;
        }

        Context ctx = SilentPhoneApplication.getAppContext();
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            ctx.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                    true, mChangeObserver);

            sIsContactObserverRegistered = true;
        }
    }

    protected static void resetContactsCache() {
        if (DEBUG) Log.d(TAG, "resetContactsCache");
        synchronized (mContactCache) {
            mContactCache.clear();
        }
        synchronized (mLoadUserDataTaskList) {
            mLoadUserDataTaskList.clear();
        }
    }

    public static ContactEntry getContactEntryFromCacheIfExists(final String name) {
        if (DEBUG) Log.d(TAG, "getContactEntryFromCacheIfExists name: " + name);
        ContactEntry result = null;
        if (!TextUtils.isEmpty(name)) {
            String formattedNumber = Utilities.formatNumber(name);
            synchronized (mContactCache) {
                for (ContactEntry entry : mContactCache) {
//                    if (DEBUG) Log.d(TAG, "++++ entry name: " + entry.name + ", imName: " + entry.imName);
                    if (name.equals(entry.name) || name.equals(entry.imName)) {
                        result = entry;
                        break;
                    }
//                    if (DEBUG) Log.d(TAG, "++++ entry number: " + entry.phoneNumber);
                    if (formattedNumber.equals(entry.phoneNumber) || name.equals(entry.phoneNumber)) {
                        result = entry;
                        break;
                    }
                }
            }
        }
        if (DEBUG) Log.d(TAG, "getContactEntryFromCacheIfExists result: " + result);
        return result;
    }

    @MainThread
    public static ContactEntry getContactEntryFromCache(final String name) {
        if (DEBUG) Log.d(TAG, "getContactEntryFromCache name: " + name);

        ContactEntry result = null;
        if (!TextUtils.isEmpty(name)) {

            result = getContactEntryFromCacheIfExists(name);

            if (result != null && TextUtils.isEmpty(result.name) && result.timeCreated != 0) {
                if (result.timeCreated + RETRY_TIME < System.currentTimeMillis()) {
                    if (ConfigurationUtilities.mTrace)
                        Log.d(TAG, "Retry to get user contact data after timeout");
                    // Keep the existing entry until we got some result from server. The RETRY_TIME
                    // should be longer than any network timeouts
                    result.timeCreated = System.currentTimeMillis();
                    loadUserDataBackground(name, result);
                }
            }

            if (result == null) {
                if (DEBUG) Log.d(TAG, "getContactEntry no cached entry for: " + name);
                result = getContactEntryFromContacts(name);
                addCacheEntry(name, result);
            }

            // If result is null then try to get a alias mapping. If successful create a stub
            // entry, set the entry's name to the mapped display name and the name parameter
            // to the phone name. If another cache lookup with the same name shows up we find
            // the stub entry.
            if (result == null) {
                byte[] dpName = null;
                byte[] userInfo = null;
                if (ZinaMessaging.getInstance().isReady()) {
                    dpName = ZinaMessaging.getDisplayName(name);
                    userInfo = ZinaMessaging.getUserInfoFromCache(name);
                }
                if (dpName != null) {
                    result = createTemporaryContactEntry(new String(dpName));
                    result.phoneNumber = name;

                    if (userInfo != null) {
                        AsyncTasks.UserInfo ui = AsyncTasks.parseUserInfo(userInfo);
                        if (ui != null) {
                            result.photoUri = AvatarUtils.getAvatarProviderUri(ui.mAvatarUrl, name);
                            result.alias = ui.mAlias;
                        }
                    }
                    addCacheEntry(name, result);
                }
                else {
                    loadUserDataBackground(name, null);
                }
            }
            if (result == null) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "No valid messaging contact entry found for: '" + name + "'");
            }
        }
        if (DEBUG) Log.d(TAG, "getContactEntryFromCache result: " + name);

        return result;
    }

    @Nullable
    public static ContactEntry getContactEntryFromContacts(final String name) {
        if (DEBUG) Log.d(TAG, "getContactEntryFromContacts name: " + name);
        ContactInfo info = null;
        String alias = null;
        if (Utilities.isUuid(name) || Utilities.isValidSipUsername(name)) {
            info = ContactInfoHelper.queryContactInfoForScUuid(SilentPhoneApplication.getAppContext(), name);

            byte[] userInfoBytes = ZinaNative.getUserInfoFromCache(name);
            if (userInfoBytes != null) {
                AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(userInfoBytes);
                if (userInfo != null && !TextUtils.isEmpty(userInfo.mAlias)) {
                    alias = userInfo.mAlias;
                }
            }
        } else {
            info = sContactInfoHelper.lookupNumberWithoutAxoCache(name, null);

            alias = Utilities.formatNumber(name);
        }

        ContactEntry result = contactInfoToContactEntry(name, alias, info);

        if (DEBUG) Log.d(TAG, "getContactEntryFromContacts result: " + name);
        return result;
    }

    @WorkerThread
    public static ContactEntry getContactEntry(final String name) {
        if (DEBUG) Log.d(TAG, "getContactEntry name: " + name);
        ContactEntry result = null;
        if (!TextUtils.isEmpty(name)) {
            result = getContactEntryFromCacheIfExists(name);

            if (result != null && TextUtils.isEmpty(result.name) && result.timeCreated != 0) {
                if (result.timeCreated + RETRY_TIME < System.currentTimeMillis()) {
                    if (ConfigurationUtilities.mTrace)
                        Log.d(TAG, "Retry to get user contact data after timeout");
                    // Keep the existing entry until we got some result from server. The RETRY_TIME
                    // should be longer than any network timeouts
                    result.timeCreated = System.currentTimeMillis();
                    loadUserDataBackground(name, result);
                }
            }

            if (result == null) {
                if (DEBUG) Log.d(TAG, "getContactEntry no cached entry for: " + name);
                result = getContactEntryFromContacts(name);
                addCacheEntry(name, result);
            }

            // Create contact for an anonymous caller
            if (Contact.UNKNOWN_USER_ID.equals(name)) {
                result = createTemporaryContactEntry(Contact.UNKNOWN_DISPLAY_NAME);
                result.phoneNumber = name;
                result.timeCreated = System.currentTimeMillis();
                addCacheEntry(name, result);
            }

            // If result is null then try to get a alias mapping. If successful create a stub
            // entry, set the entry's name to the mapped display name and the name parameter
            // to the phone name. If another cache lookup with the same name shows up we find
            // the stub entry.
            if (result == null) {
                int[] errorCode = new int[1];
                byte[] userInfo = null;
                if (ZinaMessaging.getInstance().isReady()) {
                    // query axolotl cache first and go online if that fails
                    userInfo = ZinaMessaging.getUserInfoFromCache(name);
                    if (userInfo == null) {
                        userInfo = ZinaMessaging.getUserInfo(name, null, errorCode);
                    }
                }
                if (userInfo != null) {
                    AsyncTasks.UserInfo ui = AsyncTasks.parseUserInfo(userInfo);
                    if (ui != null) {
                        result = createTemporaryContactEntry(ui.mDisplayName);
                        result.alias = ui.mAlias;
                        result.phoneNumber = name;
                        result.photoUri = AvatarUtils.getAvatarProviderUri(ui.mAvatarUrl, name);
                    }
                }
                else {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Scheduling timeout to get user contact data. " + name);
                    result = createTemporaryContactEntry(name);
                    result.phoneNumber = name;
                    result.timeCreated = System.currentTimeMillis();
                }
                addCacheEntry(name, result);
            }
        }
        if (DEBUG) Log.d(TAG, "getContactEntry result: " + result);
        return result;
    }

    public static ContactEntry getTemporaryGroupContactEntry(final String name, final String displayName) {
        ContactEntry result = createTemporaryContactEntry(name);
        result.timeCreated = Long.MAX_VALUE;
        result.photoUri = AvatarUtils.getAvatarProviderUriGroup(null, name);
        addCacheEntry(name, result);
        return result;
    }

    public static boolean hasExpired(@Nullable ContactEntry entry) {
        boolean result = (entry == null);
        if (entry != null && TextUtils.isEmpty(entry.name) && entry.timeCreated != 0) {
            if (entry.timeCreated + RETRY_TIME < System.currentTimeMillis()) {
                result = true;
            }
        }
        if (DEBUG) Log.d(TAG, "hasExpired result: " + result);
        return result;
    }

    public static void removeCachedEntry(final String name) {
        if (DEBUG) Log.d(TAG, "removeCachedEntry name: " + name);
        synchronized (mContactCache) {
            for (final Iterator<ContactEntry> iterator = mContactCache.iterator(); iterator.hasNext();) {
                final ContactEntry entry = iterator.next();
                final String formattedNumber = Utilities.formatNumber(name);
                if (name.equals(entry.name) || name.equals(entry.imName)) {
                    iterator.remove();
                }
                else if (formattedNumber.equals(entry.phoneNumber) || name.equals(entry.phoneNumber)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    public static CharSequence getDisplayName(@NonNull CharSequence name, @Nullable ContactEntry contactEntry) {
        CharSequence result = name;
        if (contactEntry != null) {
            result = !TextUtils.isEmpty(contactEntry.name)
                    ? contactEntry.name
                    : (!TextUtils.isEmpty(contactEntry.alias) ? contactEntry.alias : name);
            if (ConversationUtils.UNKNOWN_DISPLAY_NAME.equals(result)) {
                result = name;
            }
        }
        return result;
    }

    private static void loadUserDataBackground(final String name, final ContactEntry oldEntry) {
        synchronized (mLoadUserDataTaskList) {
            if (mLoadUserDataTaskList.contains(name)) {
                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "Retrieval for " + name + " already scheduled.");
                }
                return;
            }
        }
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
                    result.alias = mUserInfo.mAlias;
                    result.phoneNumber = name;
                    result.photoUri = AvatarUtils.getAvatarProviderUri(photoUri, name);
                }
                else {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Scheduling timeout to get user contact data.");
                    result = createTemporaryContactEntry("");
                    result.phoneNumber = name;
                    result.timeCreated = System.currentTimeMillis();
                }
                if (oldEntry != null) {
                    mContactCache.remove(oldEntry);
                }
                addCacheEntry(name, result);
                mLoadUserDataTaskList.remove(name);
                MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(), name, true);
            }
        };
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Get user data from server/lookup cache for " + name);
        mLoadUserDataTaskList.add(name);
        AsyncUtils.executeSerial(getNameTask);
    }

    private static void addCacheEntry(final String name, final ContactEntry entry) {
        synchronized (mContactCache) {
            if (entry != null) {
                ContactEntry existingEntry = getContactEntryFromCacheIfExists(name);
                if (existingEntry != null) {
                    mContactCache.remove(existingEntry);
                }
                mContactCache.add(entry);
            }
        }
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
        updatedInfo.lookupUri = null; //createTemporaryContactUri(name);
        updatedInfo.lookupKey = name;
        updatedInfo.photoUri = AvatarUtils.getAvatarProviderUri(null, name);
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

    @Nullable
    private static ContactEntry getContactEntry(Cursor cursor) {
        // NGA-497: if the cursor has a wrong column count (why?) then skip it (Sam S.)
        if (cursor.getColumnCount() != COLUMNS.length)
            return null;
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
        return contactEntry;
    }

    /**
     * Translates ContactInfo instance to a ContactEntry instance.
     *
     * TODO It may be necessary to move to ContactInfo and drop ContactEntry to avoid such hacks.
     */
    @Nullable
    private static ContactEntry contactInfoToContactEntry(final String name, final String alias, @Nullable final ContactInfo info) {
        if (info == null || info == ContactInfo.EMPTY) {
            return null;
        }

        ContactEntry contactEntry = new ContactEntry();
        contactEntry.name = info.name;
        contactEntry.phoneLabel = info.label;
        contactEntry.phoneNumber = info.normalizedNumber;
        if (TextUtils.isEmpty(contactEntry.phoneNumber)) {
            contactEntry.phoneNumber = info.number;
        }
        if (TextUtils.isEmpty(contactEntry.phoneNumber)) {
            contactEntry.phoneNumber = info.formattedNumber;
        }

        contactEntry.photoUri = info.photoUri;
        contactEntry.thumbnailUri = null;

        contactEntry.photoId = info.photoId;
        contactEntry.lookupKey = info.lookupKey;
        contactEntry.lookupUri = info.lookupUri;

        /*
         * To guarantee, that this entry is found, set otherwise unused imName to search param value
         */
        contactEntry.imName = name;
        contactEntry.alias = alias;

        if (DEBUG) {
            Log.d(TAG, String.format("created contact entry : name %s, phoneLabel: %s, "
                    + "phoneNumber: %s, photoUri: %s, thumbnailUri: %s, photoId: %d, "
                    + "lookupKey: %s, lookupUri: %s, imName: %s",
                    contactEntry.name, contactEntry.phoneLabel, contactEntry.phoneNumber,
                    contactEntry.photoUri, contactEntry.thumbnailUri, contactEntry.photoId,
                    contactEntry.lookupKey, contactEntry.lookupUri, contactEntry.imName));
        }

        return contactEntry;
    }

}
