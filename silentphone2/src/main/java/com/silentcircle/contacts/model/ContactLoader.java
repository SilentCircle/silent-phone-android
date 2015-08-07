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

package com.silentcircle.contacts.model;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.GroupMetaData;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.model.dataitem.DataItem;
import com.silentcircle.contacts.model.dataitem.PhoneDataItem;
import com.silentcircle.contacts.model.dataitem.PhotoDataItem;
import com.silentcircle.contacts.utils.AsyncTaskLoader;
import com.silentcircle.contacts.utils.ContactLoaderUtils;
import com.silentcircle.contacts.utils.StreamItemEntry;
import com.silentcircle.contacts.utils.StreamItemPhotoEntry;
import com.silentcircle.contacts.utils.UriUtils;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentcontacts2.ScContactsContract.Groups;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItemPhotos;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItems;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Loads a single Contact and all it constituent RawContacts.
 */
public class ContactLoader extends AsyncTaskLoader<Contact> {
    private static final String TAG = ContactLoader.class.getSimpleName();

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** A short-lived cache that can be set by {@link #cacheResult()} */
    private static Contact sCachedResult = null;

    private final Uri mRequestedUri;
    private Uri mLookupUri;
    private boolean mLoadGroupMetaData;
    private boolean mLoadStreamItems;
    private boolean mLoadInvitableAccountTypes;
    private boolean mPostViewNotification;
    private boolean mComputeFormattedPhoneNumber;
    private Contact mContact;
    private ForceLoadContentObserver mObserver;
    private final Set<Long> mNotifiedRawContactIds = Sets.newHashSet();
    
    public ContactLoader(Context context, Uri lookupUri, boolean postViewNotification) {
        this(context, lookupUri, false, false, false, postViewNotification, false);
    }

    public ContactLoader(Context context, Uri lookupUri, boolean loadGroupMetaData,
            boolean loadStreamItems, boolean loadInvitableAccountTypes,
            boolean postViewNotification, boolean computeFormattedPhoneNumber) {

        super(context);
        mLookupUri = lookupUri;
        mRequestedUri = lookupUri;
        mLoadGroupMetaData = loadGroupMetaData;
        mLoadStreamItems = loadStreamItems;
        mLoadInvitableAccountTypes = false; // TODO - check account stuff loadInvitableAccountTypes;
        mPostViewNotification = postViewNotification;
        mComputeFormattedPhoneNumber = computeFormattedPhoneNumber;
    }

    /**
     * Projection used for the query that loads all data for the entire contact (except for
     * social stream items).
     */
    private static class ContactQuery {
        static final String[] COLUMNS = new String[] {
                RawContacts.DISPLAY_NAME_SOURCE,            // 0
                RawContacts.DISPLAY_NAME,                   // 1
                RawContacts.DISPLAY_NAME_ALTERNATIVE,       // 2
                RawContacts.PHONETIC_NAME,                  // 3
                RawContacts.PHOTO_ID,                       // 4
                RawContacts.STARRED,                        // 5

                RawContacts.DIRTY,                          // 6
                RawContacts.VERSION,                        // 7
                RawContacts.SOURCE_ID,                      // 8
                RawContacts.SYNC1,                          // 9
                RawContacts.SYNC2,                          // 10
                RawContacts.SYNC3,                          // 11
                RawContacts.SYNC4,                          // 12
                RawContacts.DELETED,                        // 13
                RawContacts.NAME_VERIFIED,                  // 14

                Data.DATA1,                                 // 15
                Data.DATA2,
                Data.DATA3,
                Data.DATA4,
                Data.DATA5,
                Data.DATA6,
                Data.DATA7,
                Data.DATA8,
                Data.DATA9,
                Data.DATA10,
                Data.DATA11,
                Data.DATA12,
                Data.DATA13,
                Data.DATA14,
                Data.DATA15,                                // 29
                Data.SYNC1,
                Data.SYNC2,
                Data.SYNC3,
                Data.SYNC4,
                Data.DATA_VERSION,
                Data.IS_PRIMARY,
                Data.IS_SUPER_PRIMARY,
                Data.MIMETYPE,

                RawContacts.PHOTO_URI,
                RawContacts.CUSTOM_RINGTONE,
                RawContacts._ID,
                RawContacts.Entity.DATA_ID,
                RawContacts.CONTACT_TYPE

//              GroupMembership.GROUP_SOURCE_ID,
//              Data.RES_PACKAGE,

        };

        public static final int DISPLAY_NAME_SOURCE = 0;
        public static final int DISPLAY_NAME = 1;
        public static final int ALT_DISPLAY_NAME = 2;
        public static final int PHONETIC_NAME = 3;
        public static final int PHOTO_ID = 4;
        public static final int STARRED = 5;

        public static final int DIRTY = 6;
        public static final int VERSION = 7;
        public static final int SOURCE_ID = 8;
        public static final int SYNC1 = 9;
        public static final int SYNC2 = 10;
        public static final int SYNC3 = 11;
        public static final int SYNC4 = 12;
        public static final int DELETED = 13;
        public static final int NAME_VERIFIED = 14;

        public static final int DATA1 = 15;
        public static final int DATA2 = 16;
        public static final int DATA3 = 17;
        public static final int DATA4 = 18;
        public static final int DATA5 = 19;
        public static final int DATA6 = 20;
        public static final int DATA7 = 21;
        public static final int DATA8 = 22;
        public static final int DATA9 = 23;
        public static final int DATA10 = 24;
        public static final int DATA11 = 25;
        public static final int DATA12 = 26;
        public static final int DATA13 = 27;
        public static final int DATA14 = 28;
        public static final int DATA15 = 29;
        public static final int DATA_SYNC1 = 30;
        public static final int DATA_SYNC2 = 31;
        public static final int DATA_SYNC3 = 32;
        public static final int DATA_SYNC4 = 33;
        public static final int DATA_VERSION = 34;
        public static final int IS_PRIMARY = 35;
        public static final int IS_SUPERPRIMARY = 36;
        public static final int MIMETYPE = 37;

        public static final int PHOTO_URI = 38;
        public static final int CUSTOM_RINGTONE = 39;
        public static final int RAW_CONTACT_ID = 40;
        public static final int DATA_ID = 41;
        public static final int CONTACT_TYPE = 42;
//      public static final int GROUP_SOURCE_ID = xx;
//      public static final int RES_PACKAGE = xx;
    }

    /**
     * Projection used for the query that loads all data for the entire contact.
     */
    private static class DirectoryQuery {
        static final String[] COLUMNS = new String[] {
            Directory.DISPLAY_NAME,
            Directory.PACKAGE_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.EXPORT_SUPPORT,
        };

        public static final int DISPLAY_NAME = 0;
        public static final int PACKAGE_NAME = 1;
        public static final int TYPE_RESOURCE_ID = 2;
        public static final int EXPORT_SUPPORT = 3;
    }

    private static class GroupQuery {
        static final String[] COLUMNS = new String[] {
            Groups._ID,
            Groups.TITLE,
            Groups.AUTO_ADD,
            Groups.FAVORITES,
        };

        public static final int ID = 0;
        public static final int TITLE = 1;
        public static final int AUTO_ADD = 2;
        public static final int FAVORITES = 3;
    }

    @Override
    public Contact loadInBackground() {
        try {
            final ContentResolver resolver = getContext().getContentResolver();
            final Uri uriCurrentFormat = ContactLoaderUtils.ensureIsContactUri(resolver, mLookupUri);
            final Contact cachedResult = sCachedResult;
            sCachedResult = null;
            // Is this the same Uri as what we had before already? In that case, reuse that result
            final Contact result;
            final boolean resultIsCached;
            if (cachedResult != null &&  UriUtils.areEqual(cachedResult.getLookupUri(), mLookupUri)) {
                // We are using a cached result from earlier. Below, we should make sure
                // we are not doing any more network or disc accesses
                result = new Contact(mRequestedUri, cachedResult);
                resultIsCached = true;
            } 
            else {
                result = loadContactEntity(resolver, uriCurrentFormat);
                resultIsCached = false;
            }
            if (result.isLoaded()) {
                if (result.isDirectoryEntry()) {
                    if (!resultIsCached) {
                        loadDirectoryMetaData(result);
                    }
                } else if (mLoadGroupMetaData) {
                    if (result.getGroupMetaData() == null) {
                        loadGroupMetaData(result);
                    }
                }
                if (mLoadStreamItems && result.getStreamItems() == null) {
                    loadStreamItems(result);
                }
                if (mComputeFormattedPhoneNumber) {
                    computeFormattedPhoneNumbers(result);
                }
                if (!resultIsCached) loadPhotoBinaryData(result);

                // Note ME profile should never have "Add connection"
//                if (mLoadInvitableAccountTypes && result.getInvitableAccountTypes() == null) {
//                    loadInvitableAccountTypes(result);
//                }
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error loading the contact: " + mLookupUri, e);
            return Contact.forError(mRequestedUri, e);
        }
    }

    private Contact loadContactEntity(ContentResolver resolver, Uri contactUri) {
        Uri entityUri = Uri.withAppendedPath(contactUri, RawContacts.Entity.CONTENT_DIRECTORY);
        Cursor cursor = resolver.query(entityUri, ContactQuery.COLUMNS, null, null,  RawContacts.Entity.DATA_ID);
        if (cursor == null) {
            Log.e(TAG, "No cursor returned in loadContactEntity");
            return Contact.forNotFound(mRequestedUri);
        }

        try {
            if (!cursor.moveToFirst()) {
                cursor.close();
                return Contact.forNotFound(mRequestedUri);
            }

            // Create the loaded contact starting with the header data.
            Contact contact = loadContactHeaderData(cursor, contactUri);

            // Fill in the raw contacts, which is wrapped in an Entity and any
            // status data.  Initially, result has empty entities and statuses.
            long currentRawContactId = -1;
            RawContact rawContact = null;
            ImmutableList.Builder<RawContact> rawContactsBuilder = new ImmutableList.Builder<RawContact>();
            do {
                long rawContactId = cursor.getLong(ContactQuery.RAW_CONTACT_ID);
                if (rawContactId != currentRawContactId) {
                    // First time to see this raw contact id, so create a new entity, and
                    // add it to the result's entities.
                    currentRawContactId = rawContactId;
                    rawContact = new RawContact(getContext(), loadRawContactValues(cursor));
                    rawContactsBuilder.add(rawContact);
                }
                if (!cursor.isNull(ContactQuery.DATA_ID)) {
                    ContentValues data = loadDataValues(cursor);
                    final DataItem item = rawContact.addDataItemValues(data);

//                    if (!cursor.isNull(ContactQuery.PRESENCE)
//                            || !cursor.isNull(ContactQuery.STATUS)) {
//                        final DataStatus status = new DataStatus(cursor);
//                        final long dataId = cursor.getLong(ContactQuery.DATA_ID);
//                        statusesBuilder.put(dataId, status);
//                    }
                }
            } while (cursor.moveToNext());

            contact.setRawContacts(rawContactsBuilder.build());

            return contact;
        } finally {
            cursor.close();
        }
    }

    /**
     * Looks for the photo data item in entities. If found, creates a new Bitmap instance. If
     * not found, returns null
     */
    private void loadPhotoBinaryData(Contact contactData) {
        loadThumbnailBinaryData(contactData);

        // If we have a photo URI, try loading that first.
        String photoUri = contactData.getPhotoUri();
        if (photoUri != null) {
            try {
                AssetFileDescriptor fd = getContext().getContentResolver()
                        .openAssetFileDescriptor(Uri.parse(photoUri), "r");
                byte[] buffer = new byte[16 * 1024];
                FileInputStream fis = fd.createInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    int size;
                    while ((size = fis.read(buffer)) != -1) {
                        baos.write(buffer, 0, size);
                    }
                    contactData.setPhotoBinaryData(baos.toByteArray());
                } finally {
                    fis.close();
                    fd.close();
                }
                return;
            } catch (IOException ioe) {
                // Just fall back to the case below.
            }
        }
    }
    
    private void loadThumbnailBinaryData(Contact contactData) {
        // If we couldn't load from a file, fall back to the data blob.
        final long photoId = contactData.getPhotoId();
        if (photoId <= 0) {
            // No photo ID
            return;
        }

        for (RawContact rawContact : contactData.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                if (dataItem.getId() == photoId) {
                    if (!(dataItem instanceof PhotoDataItem)) {
                        break;
                    }
                    final PhotoDataItem photo = (PhotoDataItem) dataItem;
                    contactData.setPhotoBinaryData(photo.getPhoto());
                    break;
                }
            }
        }
    }

    /**
     * Sets the "invitable" account types to {@link Contact#mInvitableAccountTypes}.
     */
//    private void loadInvitableAccountTypes(Contact contactData) {
//        final ImmutableList.Builder<AccountType> resultListBuilder =
//                new ImmutableList.Builder<AccountType>();
//        if (!contactData.isUserProfile()) {
//            Map<AccountTypeWithDataSet, AccountType> invitables =
//                    AccountTypeManager.getInstance(getContext()).getUsableInvitableAccountTypes();
//            if (!invitables.isEmpty()) {
//                final Map<AccountTypeWithDataSet, AccountType> resultMap =
//                        Maps.newHashMap(invitables);
//
//                // Remove the ones that already have a raw contact in the current contact
//                for (RawContact rawContact : contactData.getRawContacts()) {
//                    final AccountTypeWithDataSet type = AccountTypeWithDataSet.get(
//                            rawContact.getAccountTypeString(),
//                            rawContact.getDataSet());
//                    resultMap.remove(type);
//                }
//
//                resultListBuilder.addAll(resultMap.values());
//            }
//        }
//
//        // Set to mInvitableAccountTypes
//        contactData.setInvitableAccountTypes(resultListBuilder.build());
//    }

    /**
     * Extracts Contact level columns from the cursor.
     */
    private Contact loadContactHeaderData(final Cursor cursor, Uri contactUri) {
        final String directoryParameter = contactUri.getQueryParameter(ScContactsContract.DIRECTORY_PARAM_KEY);
        final long directoryId = directoryParameter == null ? Directory.DEFAULT : Long.parseLong(directoryParameter);
        final long contactId = cursor.getLong(ContactQuery.RAW_CONTACT_ID);
        final String lookupKey = RawContacts.getLookupUri(contactId).toString();
        final long nameRawContactId = cursor.getLong(ContactQuery.RAW_CONTACT_ID); // was NAME_RAW_CONTACT_ID
        final int displayNameSource = cursor.getInt(ContactQuery.DISPLAY_NAME_SOURCE);
        final String displayName = cursor.getString(ContactQuery.DISPLAY_NAME);
        final String altDisplayName = cursor.getString(ContactQuery.ALT_DISPLAY_NAME);
        final String phoneticName = cursor.getString(ContactQuery.PHONETIC_NAME);
        final long photoId = cursor.getLong(ContactQuery.PHOTO_ID);
        final String photoUri = cursor.getString(ContactQuery.PHOTO_URI);
        final boolean starred = cursor.getInt(ContactQuery.STARRED) != 0;
        final String customRingtone = cursor.getString(ContactQuery.CUSTOM_RINGTONE);
        final boolean isUserProfile = cursor.getInt(ContactQuery.CONTACT_TYPE) == RawContacts.CONTACT_TYPE_OWN;

        Uri lookupUri;
//        if (directoryId == Directory.DEFAULT || directoryId == Directory.LOCAL_INVISIBLE) {
//            lookupUri = ContentUris.withAppendedId(
//                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), contactId);
//        } else {
            lookupUri = contactUri;
//        }

        return new Contact(mRequestedUri, contactUri, lookupUri, directoryId, lookupKey,
                contactId, nameRawContactId, displayNameSource, photoId, photoUri, displayName,
                altDisplayName, phoneticName, starred, null, false,
                customRingtone, isUserProfile);
    }

    private static final int FIELD_TYPE_NULL = 0;
    private static final int FIELD_TYPE_INTEGER = 1;
    private static final int FIELD_TYPE_STRING = 2;
    private static final int FIELD_TYPE_BLOB = 3;
    /**
     * Extracts RawContact level columns from the cursor.
     */
    private ContentValues loadRawContactValues(Cursor cursor) {
        ContentValues cv = new ContentValues();

        cv.put(RawContacts._ID, cursor.getLong(ContactQuery.RAW_CONTACT_ID));

        cursorColumnToContentValues(cursor, cv, ContactQuery.DIRTY, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.VERSION, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SOURCE_ID, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC1, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC2, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC3, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.SYNC4, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DELETED, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.RAW_CONTACT_ID, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.STARRED, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.NAME_VERIFIED, FIELD_TYPE_INTEGER);

        return cv;
    }

    /**
     * Extracts Data level columns from the cursor.
     */
    private ContentValues loadDataValues(Cursor cursor) {
        ContentValues cv = new ContentValues();

        cv.put(Data._ID, cursor.getLong(ContactQuery.DATA_ID));

        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA1, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA2, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA3, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA4, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA5, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA6, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA7, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA8, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA9, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA10, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA11, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA12, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA13, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA14, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA15, FIELD_TYPE_BLOB);      // Photo bitmap byte array
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC1, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC2, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC3, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_SYNC4, FIELD_TYPE_STRING);
        cursorColumnToContentValues(cursor, cv, ContactQuery.DATA_VERSION, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.IS_PRIMARY, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.IS_SUPERPRIMARY, FIELD_TYPE_INTEGER);
        cursorColumnToContentValues(cursor, cv, ContactQuery.MIMETYPE, FIELD_TYPE_STRING);
//        cursorColumnToContentValues(cursor, cv, ContactQuery.RES_PACKAGE);
//        cursorColumnToContentValues(cursor, cv, ContactQuery.GROUP_SOURCE_ID);
//        cursorColumnToContentValues(cursor, cv, ContactQuery.CHAT_CAPABILITY);

        return cv;
    }

    private void cursorColumnToContentValues(Cursor cursor, ContentValues values, int index, int type) {
        switch (type /*cursor.getType(index)*/) {
            case /*Cursor.*/FIELD_TYPE_NULL:
                // don't put anything in the content values
                break;
            case /*Cursor.*/FIELD_TYPE_INTEGER:
                values.put(ContactQuery.COLUMNS[index], cursor.getLong(index));
                break;
            case /*Cursor.*/FIELD_TYPE_STRING:
                values.put(ContactQuery.COLUMNS[index], cursor.getString(index));
                break;
            case /*Cursor.*/FIELD_TYPE_BLOB:
                values.put(ContactQuery.COLUMNS[index], cursor.getBlob(index));
                break;
            default:
                throw new IllegalStateException("Invalid or unhandled data type");
        }
    }

    private void loadDirectoryMetaData(Contact result) {
        long directoryId = result.getDirectoryId();

        Cursor cursor = getContext().getContentResolver().query(
                ContentUris.withAppendedId(Directory.CONTENT_URI, directoryId),
                DirectoryQuery.COLUMNS, null, null, null);
        if (cursor == null) {
            return;
        }
        try {
            if (cursor.moveToFirst()) {
                final String displayName = cursor.getString(DirectoryQuery.DISPLAY_NAME);
                final String packageName = cursor.getString(DirectoryQuery.PACKAGE_NAME);
                final int typeResourceId = cursor.getInt(DirectoryQuery.TYPE_RESOURCE_ID);
//                final String accountType = cursor.getString(DirectoryQuery.ACCOUNT_TYPE);
//                final String accountName = cursor.getString(DirectoryQuery.ACCOUNT_NAME);
                final int exportSupport = cursor.getInt(DirectoryQuery.EXPORT_SUPPORT);
                String directoryType = null;
                if (!TextUtils.isEmpty(packageName)) {
                    PackageManager pm = getContext().getPackageManager();
                    try {
                        Resources resources = pm.getResourcesForApplication(packageName);
                        directoryType = resources.getString(typeResourceId);
                    } catch (NameNotFoundException e) {
                        Log.w(TAG, "Contact directory resource not found: "
                                + packageName + "." + typeResourceId);
                    }
                }

                result.setDirectoryMetaData(
                        displayName, directoryType, null, null, exportSupport);
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Loads groups meta-data for all groups associated with all constituent raw contacts'
     * accounts.
     */
    private void loadGroupMetaData(Contact result) {
        StringBuilder selection = new StringBuilder();
        ArrayList<String> selectionArgs = new ArrayList<String>();
//        for (RawContact rawContact : result.getRawContacts()) {
//            final String accountName = rawContact.getAccountName();
//            final String accountType = rawContact.getAccountTypeString();
//            final String dataSet = rawContact.getDataSet();
//            if (accountName != null && accountType != null) {
//                if (selection.length() != 0) {
//                    selection.append(" OR ");
//                }
//                selection.append(
//                        "(" + Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE + "=?");
//                selectionArgs.add(accountName);
//                selectionArgs.add(accountType);
//
//                if (dataSet != null) {
//                    selection.append(" AND " + Groups.DATA_SET + "=?");
//                    selectionArgs.add(dataSet);
//                } else {
//                    selection.append(" AND " + Groups.DATA_SET + " IS NULL");
//                }
//                selection.append(")");
//            }
//        }
        final ImmutableList.Builder<GroupMetaData> groupListBuilder = new ImmutableList.Builder<GroupMetaData>();
        final Cursor cursor = getContext().getContentResolver().query(Groups.CONTENT_URI,
                GroupQuery.COLUMNS, selection.toString(), selectionArgs.toArray(new String[0]),
                null);
        try {
            while (cursor.moveToNext()) {
//                final String accountName = cursor.getString(GroupQuery.ACCOUNT_NAME);
//                final String accountType = cursor.getString(GroupQuery.ACCOUNT_TYPE);
//                final String dataSet = cursor.getString(GroupQuery.DATA_SET);
                final long groupId = cursor.getLong(GroupQuery.ID);
                final String title = cursor.getString(GroupQuery.TITLE);
                final boolean defaultGroup = cursor.isNull(GroupQuery.AUTO_ADD)
                        ? false
                        : cursor.getInt(GroupQuery.AUTO_ADD) != 0;
                final boolean favorites = cursor.isNull(GroupQuery.FAVORITES)
                        ? false
                        : cursor.getInt(GroupQuery.FAVORITES) != 0;

                groupListBuilder.add(new GroupMetaData(groupId, title, defaultGroup, favorites));
            }
        } finally {
            cursor.close();
        }
        result.setGroupMetaData(groupListBuilder.build());
    }

    /**
     * Loads all stream items and stream item photos belonging to this contact.
     */
    private void loadStreamItems(Contact result) {
        
        final Uri queryUri = Uri.withAppendedPath(result.getLookupUri(), RawContacts.StreamItems.CONTENT_DIRECTORY);
        
        final Cursor cursor = getContext().getContentResolver().query(queryUri, null, null, null, null);
        final LinkedHashMap<Long, StreamItemEntry> streamItemsById = new LinkedHashMap<Long, StreamItemEntry>();
        final ArrayList<StreamItemEntry> streamItems = new ArrayList<StreamItemEntry>();
        try {
            while (cursor.moveToNext()) {
                StreamItemEntry streamItem = new StreamItemEntry(cursor);
                streamItemsById.put(streamItem.getId(), streamItem);
                streamItems.add(streamItem);
            }
        } finally {
            cursor.close();
        }

        // Pre-decode all HTMLs
        final long start = System.currentTimeMillis();
        for (StreamItemEntry streamItem : streamItems) {
            streamItem.decodeHtml(getContext());
        }
        final long end = System.currentTimeMillis();
        if (DEBUG) {
            Log.d(TAG, "Decoded HTML for " + streamItems.size() + " items, took "
                    + (end - start) + " ms");
        }

        // Now retrieve any photo records associated with the stream items.
        if (!streamItems.isEmpty()) {
            if (result.isUserProfile()) {
                // If the stream items we're loading are for the profile, we can't bulk-load the
                // stream items with a custom selection.
                for (StreamItemEntry entry : streamItems) {
                    Cursor siCursor = getContext().getContentResolver().query(
                            Uri.withAppendedPath(
                                    ContentUris.withAppendedId(
                                            StreamItems.CONTENT_URI, entry.getId()),
                                    StreamItems.StreamItemPhotos.CONTENT_DIRECTORY),
                            null, null, null, null);
                    try {
                        while (siCursor.moveToNext()) {
                            entry.addPhoto(new StreamItemPhotoEntry(siCursor));
                        }
                    } finally {
                        siCursor.close();
                    }
                }
            } else {
                String[] streamItemIdArr = new String[streamItems.size()];
                StringBuilder streamItemPhotoSelection = new StringBuilder();
                streamItemPhotoSelection.append(StreamItemPhotos.STREAM_ITEM_ID + " IN (");
                for (int i = 0; i < streamItems.size(); i++) {
                    if (i > 0) {
                        streamItemPhotoSelection.append(",");
                    }
                    streamItemPhotoSelection.append("?");
                    streamItemIdArr[i] = String.valueOf(streamItems.get(i).getId());
                }
                streamItemPhotoSelection.append(")");
                Cursor sipCursor = getContext().getContentResolver().query(
                        StreamItems.CONTENT_PHOTO_URI,
                        null, streamItemPhotoSelection.toString(), streamItemIdArr,
                        StreamItemPhotos.STREAM_ITEM_ID);
                try {
                    while (sipCursor.moveToNext()) {
                        long streamItemId = sipCursor.getLong(sipCursor.getColumnIndex(StreamItemPhotos.STREAM_ITEM_ID));
                        StreamItemEntry streamItem = streamItemsById.get(streamItemId);
                        streamItem.addPhoto(new StreamItemPhotoEntry(sipCursor));
                    }
                } finally {
                    sipCursor.close();
                }
            }
        }

        // Set the sorted stream items on the result.
        Collections.sort(streamItems);
        result.setStreamItems(new ImmutableList.Builder<StreamItemEntry>()
                .addAll(streamItems.iterator())
                .build());
    }

    /**
     * Iterates over all data items that represent phone numbers are tries to calculate a formatted
     * number. This function can safely be called several times as no unformatted data is
     * overwritten
     */
    private void computeFormattedPhoneNumbers(Contact contactData) {
        final String countryIso = ContactsUtils.getCurrentCountryIso(getContext());
        final ImmutableList<RawContact> rawContacts = contactData.getRawContacts();
        final int rawContactCount = rawContacts.size();

        for (int rawContactIndex = 0; rawContactIndex < rawContactCount; rawContactIndex++) {
            final RawContact rawContact = rawContacts.get(rawContactIndex);
            final List<DataItem> dataItems = rawContact.getDataItems();
            final int dataCount = dataItems.size();

            for (int dataIndex = 0; dataIndex < dataCount; dataIndex++) {
                final DataItem dataItem = dataItems.get(dataIndex);
                if (dataItem instanceof PhoneDataItem) {
                    final PhoneDataItem phoneDataItem = (PhoneDataItem) dataItem;
                    phoneDataItem.computeFormattedPhoneNumber(countryIso);
                }
            }
        }
    }

    @Override
    public void deliverResult(Contact result) {
        unregisterObserver();

        // The creator isn't interested in any further updates
        if (isReset() || result == null) {
            return;
        }

        mContact = result;

        if (result.isLoaded()) {
            mLookupUri = result.getLookupUri();

            if (!result.isDirectoryEntry()) {
                if (mObserver == null) {
                    mObserver = new ForceLoadContentObserver();
                }
                getContext().getContentResolver().registerContentObserver(
                        mLookupUri, true, mObserver);
            }

            if (mPostViewNotification) {
                // inform the source of the data that this contact is being looked at
                postViewNotificationToSyncAdapter();
            }
        }

        super.deliverResult(mContact);
    }

    /**
     * Posts a message to the contributing sync adapters that have opted-in, notifying them
     * that the contact has just been loaded
     */
    private void postViewNotificationToSyncAdapter() {
        Context context = getContext();
        for (RawContact rawContact : mContact.getRawContacts()) {
            final long rawContactId = rawContact.getId();
            if (mNotifiedRawContactIds.contains(rawContactId)) {
                continue; // Already notified for this raw contact.
            }
            mNotifiedRawContactIds.add(rawContactId);
            final AccountType accountType = rawContact.getAccountType();
            final String serviceName = accountType.getViewContactNotifyServiceClassName();
            final String servicePackageName = accountType.getViewContactNotifyServicePackageName();
            if (!TextUtils.isEmpty(serviceName) && !TextUtils.isEmpty(servicePackageName)) {
                final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
                final Intent intent = new Intent();
                intent.setClassName(servicePackageName, serviceName);
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, RawContacts.CONTENT_ITEM_TYPE);
                try {
                    context.startService(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message to source-app", e);
                }
            }
        }
    }

    private void unregisterObserver() {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
    }

    /**
     * Sets whether to load stream items. Will trigger a reload if the value has changed.
     * At the moment, this is only used for debugging purposes
     */
    public void setLoadStreamItems(boolean value) {
        if (mLoadStreamItems != value) {
            mLoadStreamItems = value;
            onContentChanged();
        }
    }

    /**
     * Fully upgrades this ContactLoader to one with all lists fully loaded. When done, the
     * new result will be delivered
     */
    public void upgradeToFullContact() {
        // Everything requested already? Nothing to do, so let's bail out
        if (mLoadGroupMetaData && mLoadInvitableAccountTypes && mLoadStreamItems
                && mPostViewNotification && mComputeFormattedPhoneNumber) return;

        mLoadGroupMetaData = true;
        mLoadInvitableAccountTypes = true;
        mLoadStreamItems = true;
        mPostViewNotification = true;
        mComputeFormattedPhoneNumber = true;

        // Cache the current result, so that we only load the "missing" parts of the contact.
        cacheResult();

        // Our load parameters have changed, so let's pretend the data has changed. Its the same
        // thing, essentially.
        onContentChanged();
    }

    public boolean getLoadStreamItems() {
        return mLoadStreamItems;
    }

    public Uri getLookupUri() {
        return mLookupUri;
    }

    @Override
    protected void onStartLoading() {
        if (mContact != null) {
            deliverResult(mContact);
        }

        if (takeContentChanged() || mContact == null) {
            forceLoad();
        }
    }

    @Override
    @SuppressLint("NewApi")             // cancelLoad is defined in ScLoader (via AsyncTaskLoader) and handled there
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    @SuppressLint("NewApi")             // cancelLoad is defined in ScLoader (via AsyncTaskLoader) and handled there
    protected void onReset() {
        super.onReset();
        cancelLoad();
        unregisterObserver();
        mContact = null;
    }

    /**
     * Caches the result, which is useful when we switch from activity to activity, using the same
     * contact. If the next load is for a different contact, the cached result will be dropped
     */
    public void cacheResult() {
        if (mContact == null || !mContact.isLoaded()) {
            sCachedResult = null;
        } else {
            sCachedResult = mContact;
        }
    }
}
