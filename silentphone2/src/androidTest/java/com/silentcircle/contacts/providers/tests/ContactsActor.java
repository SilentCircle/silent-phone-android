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
 * Copyright (C) 2009 The Android Open Source Project
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
 * limitations under the License.
 */

package com.silentcircle.contacts.providers.tests;

import android.accounts.Account;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.test.IsolatedContext;
import android.test.RenamingDelegatingContext;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;

import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

//import android.location.Country;
//import android.location.CountryDetector;
//import android.location.CountryListener;

/**
 * Helper class that encapsulates an "actor" which is owned by a specific
 * package name. It correctly maintains a wrapped {@link android.content.Context} and an
 * attached {@link android.test.mock.MockContentResolver}. Multiple actors can be used to test
 * security scenarios between multiple packages.
 */
public class ContactsActor {
    protected static final String TAG = "ScActor";

    private static final String FILENAME_PREFIX = "test.";

    public static final String PACKAGE_GREY = "edu.example.grey";
    public static final String PACKAGE_RED = "net.example.red";
    public static final String PACKAGE_GREEN = "com.example.green";
    public static final String PACKAGE_BLUE = "org.example.blue";

    public Context context;
    public String packageName;
    public MockContentResolver resolver;
    public ContentProvider mprovider;
//    private Country mMockCountry = new Country("us", 0);

    private Account[] mAccounts = new Account[0];

    private Set<String> mGrantedPermissions = new HashSet<String>();
    private final Set<Uri> mGrantedUriPermissions = new HashSet<Uri>();

    private IsolatedContext mProviderContext;

    /**
     * Create an "actor" using the given parent {@link android.content.Context} and the specific
     * package name. Internally, all {@link android.content.Context} method calls are passed to
     * a new instance of {@link com.silentcircle.contacts.providers.tests.ContactsActor.RestrictionMockContext}, which stubs out the
     * security infrastructure.
     */
    public ContactsActor(Context overallContext, String packageName, Class<? extends ContentProvider> providerClass, 
            String authority)  throws Exception {

        resolver = new MockContentResolver();
        context = new RestrictionMockContext(overallContext, packageName, resolver, mGrantedPermissions, mGrantedUriPermissions);
        this.packageName = packageName;

        // Let the Secure class initialize the settings provider, which is done when we first
        // tries to get any setting.  Because our mock context/content resolver doesn't have the
        // settings provider, we need to do this with an actual context, before other classes
        // try to do this with a mock context.
        // (Otherwise ContactsProvider2.initialzie() will crash trying to get a setting with
        // a mock context.)
        android.provider.Settings.Secure.getString(overallContext.getContentResolver(), "dummy");

        RenamingDelegatingContext targetContextWrapper = new RenamingDelegatingContext(context, overallContext, FILENAME_PREFIX);
        mProviderContext = new IsolatedContext(resolver, targetContextWrapper) {
            private final MockSharedPreferences mPrefs = new MockSharedPreferences();

            @Override
            public File getFilesDir() {
                // TODO: Need to figure out something more graceful than this.
                return new File("/data/data/com.silentcircle.silentphone/files");
            }

            @Override
            public Object getSystemService(String name) {
                if (Context.ACCOUNT_SERVICE.equals(name)) {
                    return null; //mMockAccountManager;
                }
                return super.getSystemService(name);
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return mPrefs;
            }
        };

//        mMockAccountManager = new MockAccountManager(mProviderContext);
        mprovider = addProvider(providerClass, authority);
    }

    public void addAuthority(String authority) {
        resolver.addProvider(authority, mprovider);
    }

    public ContentProvider addProvider(Class<? extends ContentProvider> providerClass, String authority) throws Exception {
        ContentProvider provider = providerClass.newInstance();
        ProviderInfo info = new ProviderInfo();
        info.authority = authority;
        provider.attachInfo(mProviderContext, info);
        resolver.addProvider(authority, provider);
        return provider;
    }

    public void addPermissions(String... permissions) {
        mGrantedPermissions.addAll(Arrays.asList(permissions));
    }

    public void removePermissions(String... permissions) {
        mGrantedPermissions.removeAll(Arrays.asList(permissions));
    }

    public void addUriPermissions(Uri... uris) {
        mGrantedUriPermissions.addAll(Arrays.asList(uris));
    }

    public void removeUriPermissions(Uri... uris) {
        mGrantedUriPermissions.removeAll(Arrays.asList(uris));
    }

    /**
     * Mock {@link android.content.Context} that reports specific well-known values for testing
     * data protection. The creator can override the owner package name, and
     * force the {@link android.content.pm.PackageManager} to always return a well-known package
     * list for any call to {@link android.content.pm.PackageManager#getPackagesForUid(int)}.
     * <p>
     * For example, the creator could request that the {@link android.content.Context} lives in
     * package name "com.example.red", and also cause the {@link android.content.pm.PackageManager}
     * to report that no UID contains that package name.
     */
    private static class RestrictionMockContext extends MockContext {
        private final Context mOverallContext;
        private final String mReportedPackageName;
        private final ContactsMockPackageManager mPackageManager;
        private final ContentResolver mResolver;
        private final Resources mRes;
        private final Set<String> mGrantedPermissions;
        private final Set<Uri> mGrantedUriPermissions;

        /**
         * Create a {@link android.content.Context} under the given package name.
         */
        public RestrictionMockContext(Context overallContext, String reportedPackageName,
                ContentResolver resolver, Set<String> grantedPermissions,
                Set<Uri> grantedUriPermissions) {
            mOverallContext = overallContext;
            mReportedPackageName = reportedPackageName;
            mResolver = resolver;
            mGrantedPermissions = grantedPermissions;
            mGrantedUriPermissions = grantedUriPermissions;

            mPackageManager = new ContactsMockPackageManager();
            mPackageManager.addPackage(1000, PACKAGE_GREY);
            mPackageManager.addPackage(2000, PACKAGE_RED);
            mPackageManager.addPackage(3000, PACKAGE_GREEN);
            mPackageManager.addPackage(4000, PACKAGE_BLUE);

            Resources resources = overallContext.getResources();
            Configuration configuration = new Configuration(resources.getConfiguration());
            configuration.locale = Locale.US;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            mRes = resources;
        }

        @Override
        public String getPackageName() {
            return mReportedPackageName;
        }

        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        @Override
        public Resources getResources() {
            return mRes;
        }

        @Override
        public ContentResolver getContentResolver() {
            return mResolver;
        }

        @Override
        public ApplicationInfo getApplicationInfo() {
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = "contactsTestPackage";
            return ai;
        }

        // All permission checks are implemented to simply check against the granted permission set.

        @Override
        public int checkPermission(String permission, int pid, int uid) {
            return checkCallingPermission(permission);
        }

        @Override
        public int checkCallingPermission(String permission) {
            if (mGrantedPermissions.contains(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            } else {
                return PackageManager.PERMISSION_DENIED;
            }
        }

        @Override
        public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
            return checkCallingUriPermission(uri, modeFlags);
        }

        @Override
        public int checkCallingUriPermission(Uri uri, int modeFlags) {
            if (mGrantedUriPermissions.contains(uri)) {
                return PackageManager.PERMISSION_GRANTED;
            } else {
                return PackageManager.PERMISSION_DENIED;
            }
        }

        @Override
        public int checkCallingOrSelfPermission(String permission) {
            return checkCallingPermission(permission);
        }

        @Override
        public void enforcePermission(String permission, int pid, int uid, String message) {
            enforceCallingPermission(permission, message);
        }

        @Override
        public void enforceCallingPermission(String permission, String message) {
            if (!mGrantedPermissions.contains(permission)) {
                throw new SecurityException(message);
            }
        }

        @Override
        public void enforceCallingOrSelfPermission(String permission, String message) {
            enforceCallingPermission(permission, message);
        }

        @Override
        public void sendBroadcast(Intent intent) {
            mOverallContext.sendBroadcast(intent);
        }

        @Override
        public void sendBroadcast(Intent intent, String receiverPermission) {
            mOverallContext.sendBroadcast(intent, receiverPermission);
        }
    }

    static String sCallingPackage = null;

    void ensureCallingPackage() {
        sCallingPackage = this.packageName;
    }

    public long createRawContact(String name) {
        ensureCallingPackage();
        long rawContactId = createRawContact();
        createName(rawContactId, name);
        return rawContactId;
    }

    public long createRawContact() {
        ensureCallingPackage();
        final ContentValues values = new ContentValues();

        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        return ContentUris.parseId(rawContactUri);
    }

//    public long createRawContactWithStatus(String name, String address,
//            String status) {
//        final long rawContactId = createRawContact(name);
//        final long dataId = createEmail(rawContactId, address);
//        createStatus(dataId, status);
//        return rawContactId;
//    }
//
    public long createName(long contactId, String name) {
        ensureCallingPackage();
        final ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, contactId);
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.StructuredName.FAMILY_NAME, name);
        Uri insertUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                contactId), RawContacts.Data.CONTENT_DIRECTORY);
        Uri dataUri = resolver.insert(insertUri, values);
        return ContentUris.parseId(dataUri);
    }

    public long createPhone(long contactId, String phoneNumber) {
        ensureCallingPackage();
        final ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, contactId);
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.TYPE,
                Phone.TYPE_HOME);
        values.put(Phone.NUMBER, phoneNumber);
        Uri insertUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                contactId), RawContacts.Data.CONTENT_DIRECTORY);
        Uri dataUri = resolver.insert(insertUri, values);
        return ContentUris.parseId(dataUri);
    }

    public long createEmail(long contactId, String address) {
        ensureCallingPackage();
        final ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, contactId);
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.put(Email.DATA, address);
        Uri insertUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                contactId), RawContacts.Data.CONTENT_DIRECTORY);
        Uri dataUri = resolver.insert(insertUri, values);
        return ContentUris.parseId(dataUri);
    }

//    public long createStatus(long dataId, String status) {
//        ensureCallingPackage();
//        final ContentValues values = new ContentValues();
//        values.put(StatusUpdates.DATA_ID, dataId);
//        values.put(StatusUpdates.STATUS, status);
//        Uri dataUri = resolver.insert(StatusUpdates.CONTENT_URI, values);
//        return ContentUris.parseId(dataUri);
//    }

    public void updateException(String packageProvider, String packageClient, boolean allowAccess) {
        throw new UnsupportedOperationException("RestrictionExceptions are hard-coded");
    }

    public long getContactForRawContact(long rawContactId) {
        ensureCallingPackage();
        Uri contactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        final Cursor cursor = resolver.query(contactUri, Projections.PROJ_RAW_CONTACTS, null,  null, null);
        if (!cursor.moveToFirst()) {
            cursor.close();
            throw new RuntimeException("Contact didn't have an aggregate");
        }
        final long aggId = cursor.getLong(Projections.COL_CONTACTS_ID);
        cursor.close();
        return aggId;
    }

//    public int getDataCountForContact(long contactId) {
//        ensureCallingPackage();
//        Uri contactUri = Uri.withAppendedPath(ContentUris.withAppendedId(Contacts.CONTENT_URI,
//                contactId), Contacts.Data.CONTENT_DIRECTORY);
//        final Cursor cursor = resolver.query(contactUri, Projections.PROJ_ID, null, null,
//                null);
//        final int count = cursor.getCount();
//        cursor.close();
//        return count;
//    }
//
//    public int getDataCountForRawContact(long rawContactId) {
//        ensureCallingPackage();
//        Uri contactUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI,
//                rawContactId), Contacts.Data.CONTENT_DIRECTORY);
//        final Cursor cursor = resolver.query(contactUri, Projections.PROJ_ID, null, null,
//                null);
//        final int count = cursor.getCount();
//        cursor.close();
//        return count;
//    }

    public void setSuperPrimaryPhone(long dataId) {
        ensureCallingPackage();
        final ContentValues values = new ContentValues();
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        Uri updateUri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);
        resolver.update(updateUri, values, null, null);
    }

//    public long createGroup(String groupName) {
//        ensureCallingPackage();
//        final ContentValues values = new ContentValues();
//        values.put(ScContactsContract.Groups.RES_PACKAGE, packageName);
//        values.put(ScContactsContract.Groups.TITLE, groupName);
//        Uri groupUri = resolver.insert(ScContactsContract.Groups.CONTENT_URI, values);
//        return ContentUris.parseId(groupUri);
//    }
//
//    public long createGroupMembership(long rawContactId, long groupId) {
//        ensureCallingPackage();
//        final ContentValues values = new ContentValues();
//        values.put(Data.RAW_CONTACT_ID, rawContactId);
//        values.put(Data.MIMETYPE, CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE);
//        values.put(CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId);
//        Uri insertUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI,
//                rawContactId), RawContacts.Data.CONTENT_DIRECTORY);
//        Uri dataUri = resolver.insert(insertUri, values);
//        return ContentUris.parseId(dataUri);
//    }
//
//    protected void setAggregationException(int type, long rawContactId1, long rawContactId2) {
//        ContentValues values = new ContentValues();
//        values.put(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
//        values.put(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
//        values.put(AggregationExceptions.TYPE, type);
//        resolver.update(AggregationExceptions.CONTENT_URI, values, null, null);
//    }

    public void setAccounts(Account[] accounts) {
        mAccounts = accounts;
    }

    /**
     * Various internal database projections.
     */
    private interface Projections {
        String[] PROJ_ID = new String[] {
                BaseColumns._ID,
        };

        int COL_ID = 0;

        String[] PROJ_RAW_CONTACTS = new String[] {
                RawContacts._ID
        };

        int COL_CONTACTS_ID = 0;
    }
}
