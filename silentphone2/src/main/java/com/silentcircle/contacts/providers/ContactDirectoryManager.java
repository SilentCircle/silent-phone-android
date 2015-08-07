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

package com.silentcircle.contacts.providers;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DbProperties;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DirectoryColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentphone2.R;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the contents of the {@link com.silentcircle.silentcontacts.ScContactsContract.Directory} table.
 */
public class ContactDirectoryManager {

    private static final String TAG = "ContactDirectoryManager";
    private static final boolean DEBUG = false; // DON'T SUBMIT WITH TRUE

    public static final String CONTACT_DIRECTORY_META_DATA = "android.content.ContactDirectory";

    public static class DirectoryInfo {
        long id;
        String packageName;
        String authority;
        String accountType;
        String displayName;
        int typeResourceId;
        int exportSupport = Directory.EXPORT_SUPPORT_NONE;
        int shortcutSupport = Directory.SHORTCUT_SUPPORT_NONE;
        int photoSupport = Directory.PHOTO_SUPPORT_NONE;
        @Override
        public String toString() {
            return "DirectoryInfo:"
                    + "id=" + id
                    + " packageName=" + accountType
                    + " authority=" + authority
                    + " accountName=***"
                    + " accountType=" + accountType;
        }
    }

    private final static class DirectoryQuery {
        public static final String[] PROJECTION = {
            Directory.DISPLAY_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.EXPORT_SUPPORT,
            Directory.SHORTCUT_SUPPORT,
            Directory.PHOTO_SUPPORT,
        };

        public static final int DISPLAY_NAME = 0;
        public static final int TYPE_RESOURCE_ID = 1;
        public static final int EXPORT_SUPPORT = 2;
        public static final int SHORTCUT_SUPPORT = 3;
        public static final int PHOTO_SUPPORT = 4;
    }

    private final ScContactsProvider mContactsProvider;
    private final Context mContext;
    private final PackageManager mPackageManager;

    public ContactDirectoryManager(ScContactsProvider contactsProvider) {
        mContactsProvider = contactsProvider;
        mContext = contactsProvider.getContext();
        mPackageManager = mContext.getPackageManager();
    }

    public ScContactsDatabaseHelper getDbHelper() {
        return mContactsProvider.getDatabaseHelper();
    }

    /**
     * Scans all packages owned by the specified calling UID looking for contact
     * directory providers.
     */
    public void scanPackagesByUid(int callingUid) {
        final String[] callerPackages = mPackageManager.getPackagesForUid(callingUid);
        if (callerPackages != null) {
            for (String callerPackage : callerPackages) {
                onPackageChanged(callerPackage);
            }
        }
    }

    /**
     * Scans through existing directories to see if the cached resource IDs still
     * match their original resource names.  If not - plays it safe by refreshing all directories.
     *
     * @return true if all resource IDs were found valid
     */
    private boolean areTypeResourceIdsValid() {
        SQLiteDatabase db = getDbHelper().getDatabase(false);

        Cursor cursor = db.query(Tables.DIRECTORIES,
                new String[] { Directory.TYPE_RESOURCE_ID, Directory.PACKAGE_NAME,
                        DirectoryColumns.TYPE_RESOURCE_NAME }, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                int resourceId = cursor.getInt(0);
                if (resourceId != 0) {
                    String packageName = cursor.getString(1);
                    String storedResourceName = cursor.getString(2);
                    String resourceName = getResourceNameById(packageName, resourceId);
                    if (!TextUtils.equals(storedResourceName, resourceName)) {
                        return false;
                    }
                }
            }
        } finally {
            cursor.close();
        }

        return true;
    }

    /**
     * Given a resource ID, returns the corresponding resource name or null if the package name /
     * resource ID combination is invalid.
     */
    private String getResourceNameById(String packageName, int resourceId) {
        try {
            Resources resources = mPackageManager.getResourcesForApplication(packageName);
            return resources.getResourceName(resourceId);
        } catch (NameNotFoundException e) {
            return null;
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Scans all packages for directory content providers.
     */
    public void scanAllPackages(boolean rescan) {
        if (rescan || !areTypeResourceIdsValid()) {
            getDbHelper().setProperty(DbProperties.DIRECTORY_SCAN_COMPLETE, "0");
        }

        scanAllPackagesIfNeeded();
    }

    private void scanAllPackagesIfNeeded() {
        String scanComplete = getDbHelper().getProperty(DbProperties.DIRECTORY_SCAN_COMPLETE, "0");
        if (!"0".equals(scanComplete)) {
            return;
        }

        final long start = SystemClock.elapsedRealtime();
        int count = scanAllPackages();
        getDbHelper().setProperty(DbProperties.DIRECTORY_SCAN_COMPLETE, "1");
        final long end = SystemClock.elapsedRealtime();
        Log.i(TAG, "Discovered " + count + " contact directories in " + (end - start) + "ms");

        // Announce the change to listeners of the contacts authority
        mContactsProvider.notifyChange(false);
    }

    static boolean isDirectoryProvider(ProviderInfo provider) {
        Bundle metaData = provider.metaData;
        if (metaData == null) return false;

        Object trueFalse = metaData.get(CONTACT_DIRECTORY_META_DATA);
        return trueFalse != null && Boolean.TRUE.equals(trueFalse);
    }

    /**
     * @return List of packages that contain a directory provider.
     */
    static Set<String> getDirectoryProviderPackages(PackageManager pm) {
        final Set<String> ret = new HashSet<>();

        // Note to 3rd party developers:
        // queryContentProviders() is a public API but this method doesn't officially support
        // the GET_META_DATA flag.  Don't use it in your app.
        final List<ProviderInfo> providers = pm.queryContentProviders(null, 0, PackageManager.GET_META_DATA);
        if (providers == null) {
            return ret;
        }
        for (ProviderInfo provider : providers) {
            if (isDirectoryProvider(provider)) {
                ret.add(provider.packageName);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Found " + ret.size() + " directory provider packages");
        }
        return ret;
    }

    int scanAllPackages() {
        SQLiteDatabase db = getDbHelper().getDatabase(true);

        insertDefaultDirectory(db);
        insertLocalInvisibleDirectory(db);

        int count = 0;

        // No directory support - no provider based sync support
        //
//        // Prepare query strings for removing stale rows which don't correspond to existing
//        // directories.
//        StringBuilder deleteWhereBuilder = new StringBuilder();
//        ArrayList<String> deleteWhereArgs = new ArrayList<String>();
//        deleteWhereBuilder.append("NOT (" + Directory._ID + "=? OR " + Directory._ID + "=?");
//        deleteWhereArgs.add(String.valueOf(Directory.DEFAULT));
//        deleteWhereArgs.add(String.valueOf(Directory.LOCAL_INVISIBLE));
//        final String wherePart = "(" + Directory.PACKAGE_NAME + "=? AND " + Directory.DIRECTORY_AUTHORITY + "=?";
//
//        for (String packageName : getDirectoryProviderPackages(mPackageManager)) {
//            if (DEBUG) Log.d(TAG, "package=" + packageName);
//
//            // getDirectoryProviderPackages() shouldn't return the contacts provider package
//            // because it doesn't have CONTACT_DIRECTORY_META_DATA, but just to make sure...
//            if (mContext.getPackageName().equals(packageName)) {
//                Log.w(TAG, "  skipping self");
//                continue;
//            }
//
//            final PackageInfo packageInfo;
//            try {
//                packageInfo = mPackageManager.getPackageInfo(packageName,
//                        PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA);
//                if (packageInfo == null)
//                    continue;  // Just in case...
//            } catch (NameNotFoundException nnfe) {
//                continue; // Application just removed?
//            }
//
//            List<DirectoryInfo> directories = updateDirectoriesForPackage(packageInfo, true);
//            if (directories != null && !directories.isEmpty()) {
//                count += directories.size();
//
//                // We shouldn't delete rows for existing directories.
//                for (DirectoryInfo info : directories) {
//                    if (DEBUG) Log.d(TAG, "  directory=" + info);
//                    deleteWhereBuilder.append(" OR ");
//                    deleteWhereBuilder.append(wherePart);
//                    deleteWhereArgs.add(info.packageName);
//                    deleteWhereArgs.add(info.authority);
//                    deleteWhereArgs.add(info.accountName);
//                    deleteWhereArgs.add(info.accountType);
//                }
//            }
//        }
//
//        deleteWhereBuilder.append(")");  // Close "NOT ("
//
//        int deletedRows = db.delete(Tables.DIRECTORIES, deleteWhereBuilder.toString(), deleteWhereArgs.toArray(new String[0]));
//        Log.i(TAG, "deleted " + deletedRows + " stale rows which don't have any relevant directory");
        return count;
    }

    private void insertDefaultDirectory(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(Directory._ID, Directory.DEFAULT);
        values.put(Directory.PACKAGE_NAME, mContext.getApplicationInfo().packageName);
        values.put(Directory.DIRECTORY_AUTHORITY, ScContactsContract.AUTHORITY);
        values.put(Directory.TYPE_RESOURCE_ID, R.string.default_directory);
        values.put(DirectoryColumns.TYPE_RESOURCE_NAME,  mContext.getResources().getResourceName(R.string.default_directory));
        values.put(Directory.EXPORT_SUPPORT, Directory.EXPORT_SUPPORT_NONE);
        values.put(Directory.SHORTCUT_SUPPORT, Directory.SHORTCUT_SUPPORT_FULL);
        values.put(Directory.PHOTO_SUPPORT, Directory.PHOTO_SUPPORT_FULL);
        db.replace(Tables.DIRECTORIES, null, values);
    }

    private void insertLocalInvisibleDirectory(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(Directory._ID, Directory.LOCAL_INVISIBLE);
        values.put(Directory.PACKAGE_NAME, mContext.getApplicationInfo().packageName);
        values.put(Directory.DIRECTORY_AUTHORITY, ScContactsContract.AUTHORITY);
        values.put(Directory.TYPE_RESOURCE_ID, R.string.local_invisible_directory);
        values.put(DirectoryColumns.TYPE_RESOURCE_NAME, mContext.getResources().getResourceName(R.string.local_invisible_directory));
        values.put(Directory.EXPORT_SUPPORT, Directory.EXPORT_SUPPORT_NONE);
        values.put(Directory.SHORTCUT_SUPPORT, Directory.SHORTCUT_SUPPORT_FULL);
        values.put(Directory.PHOTO_SUPPORT, Directory.PHOTO_SUPPORT_FULL);
        db.replace(Tables.DIRECTORIES, null, values);
    }

    /**
     * Scans the specified package for content directories.  The package may have
     * already been removed, so packageName does not necessarily correspond to
     * an installed package.
     */
    public void onPackageChanged(String packageName) {
        PackageInfo packageInfo;

        try {
            packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // The package got removed
            packageInfo = new PackageInfo();
            packageInfo.packageName = packageName;
        }

        if (mContext.getPackageName().equals(packageInfo.packageName)) {
            if (DEBUG) Log.d(TAG, "Ignoring onPackageChanged for self");
            return;
        }
        updateDirectoriesForPackage(packageInfo, false);
    }


    /**
     * Scans the specified package for content directories and updates the {@link com.silentcircle.silentcontacts.ScContactsContract.Directory}
     * table accordingly.
     */
    private List<DirectoryInfo> updateDirectoriesForPackage(PackageInfo packageInfo, boolean initialScan) {
        if (DEBUG) {
            Log.d(TAG, "updateDirectoriesForPackage  packageName=" + packageInfo.packageName + " initialScan=" + initialScan);
        }

        ArrayList<DirectoryInfo> directories = new ArrayList<>();

        ProviderInfo[] providers = packageInfo.providers;
        if (providers != null) {
            for (ProviderInfo provider : providers) {
                if (isDirectoryProvider(provider)) {
                    queryDirectoriesForAuthority(directories, provider);
                }
            }
        }

        if (directories.size() == 0 && initialScan) {
            return null;
        }

        SQLiteDatabase db = getDbHelper().getDatabase(true);
        db.beginTransaction();
        try {
            updateDirectories(db, directories);
            // Clear out directories that are no longer present
            StringBuilder sb = new StringBuilder(Directory.PACKAGE_NAME + "=?");
            if (!directories.isEmpty()) {
                sb.append(" AND " + Directory._ID + " NOT IN(");
                for (DirectoryInfo info: directories) {
                    sb.append(info.id).append(",");
                }
                sb.setLength(sb.length() - 1);  // Remove the extra comma
                sb.append(")");
            }
            final int numDeleted = db.delete(Tables.DIRECTORIES, sb.toString(),
                    new String[] { packageInfo.packageName });
            if (DEBUG) {
                Log.d(TAG, "  deleted " + numDeleted + " stale rows");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return directories;
    }

    /**
     * Sends a {@link com.silentcircle.silentcontacts.ScContactsContract.Directory#CONTENT_URI} request to a specific contact directory
     * provider and appends all discovered directories to the directoryInfo list.
     */
    protected void queryDirectoriesForAuthority(ArrayList<DirectoryInfo> directoryInfo, ProviderInfo provider) {
        Uri uri = new Uri.Builder().scheme("content").authority(provider.authority).appendPath("directories").build();
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(
                    uri, DirectoryQuery.PROJECTION, null, null, null);
            if (cursor == null) {
                Log.i(TAG, providerDescription(provider) + " returned a NULL cursor.");
            } else {
                while (cursor.moveToNext()) {
                    DirectoryInfo info = new DirectoryInfo();
                    info.packageName = provider.packageName;
                    info.authority = provider.authority;
                    info.displayName = cursor.getString(DirectoryQuery.DISPLAY_NAME);
                    if (!cursor.isNull(DirectoryQuery.TYPE_RESOURCE_ID)) {
                        info.typeResourceId = cursor.getInt(DirectoryQuery.TYPE_RESOURCE_ID);
                    }
                    if (!cursor.isNull(DirectoryQuery.EXPORT_SUPPORT)) {
                        int exportSupport = cursor.getInt(DirectoryQuery.EXPORT_SUPPORT);
                        switch (exportSupport) {
                            case Directory.EXPORT_SUPPORT_NONE:
                            case Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY:
                            case Directory.EXPORT_SUPPORT_ANY_ACCOUNT:
                                info.exportSupport = exportSupport;
                                break;
                            default:
                                Log.e(TAG, providerDescription(provider)
                                        + " - invalid export support flag: " + exportSupport);
                        }
                    }
                    if (!cursor.isNull(DirectoryQuery.SHORTCUT_SUPPORT)) {
                        int shortcutSupport = cursor.getInt(DirectoryQuery.SHORTCUT_SUPPORT);
                        switch (shortcutSupport) {
                            case Directory.SHORTCUT_SUPPORT_NONE:
                            case Directory.SHORTCUT_SUPPORT_DATA_ITEMS_ONLY:
                            case Directory.SHORTCUT_SUPPORT_FULL:
                                info.shortcutSupport = shortcutSupport;
                                break;
                            default:
                                Log.e(TAG, providerDescription(provider)
                                        + " - invalid shortcut support flag: " + shortcutSupport);
                        }
                    }
                    if (!cursor.isNull(DirectoryQuery.PHOTO_SUPPORT)) {
                        int photoSupport = cursor.getInt(DirectoryQuery.PHOTO_SUPPORT);
                        switch (photoSupport) {
                            case Directory.PHOTO_SUPPORT_NONE:
                            case Directory.PHOTO_SUPPORT_THUMBNAIL_ONLY:
                            case Directory.PHOTO_SUPPORT_FULL_SIZE_ONLY:
                            case Directory.PHOTO_SUPPORT_FULL:
                                info.photoSupport = photoSupport;
                                break;
                            default:
                                Log.e(TAG, providerDescription(provider)
                                        + " - invalid photo support flag: " + photoSupport);
                        }
                    }
                    directoryInfo.add(info);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, providerDescription(provider) + " exception", t);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Updates the directories tables in the database to match the info received
     * from directory providers.
     */
    private void updateDirectories(SQLiteDatabase db, ArrayList<DirectoryInfo> directoryInfo) {
        // Insert or replace existing directories.
        // This happens so infrequently that we can use a less-then-optimal one-a-time approach
        for (DirectoryInfo info : directoryInfo) {
            ContentValues values = new ContentValues();
            values.put(Directory.PACKAGE_NAME, info.packageName);
            values.put(Directory.DIRECTORY_AUTHORITY, info.authority);
            values.put(Directory.TYPE_RESOURCE_ID, info.typeResourceId);
            values.put(Directory.DISPLAY_NAME, info.displayName);
            values.put(Directory.EXPORT_SUPPORT, info.exportSupport);
            values.put(Directory.SHORTCUT_SUPPORT, info.shortcutSupport);
            values.put(Directory.PHOTO_SUPPORT, info.photoSupport);

            if (info.typeResourceId != 0) {
                String resourceName = getResourceNameById(info.packageName, info.typeResourceId);
                values.put(DirectoryColumns.TYPE_RESOURCE_NAME, resourceName);
            }

            Cursor cursor = db.query(Tables.DIRECTORIES, new String[] { Directory._ID },
                    Directory.PACKAGE_NAME + "=? AND " + Directory.DIRECTORY_AUTHORITY + "=?",
                    new String[] {
                            info.packageName, info.authority },
                    null, null, null);
            try {
                long id;
                if (cursor.moveToFirst()) {
                    id = cursor.getLong(0);
                    db.update(Tables.DIRECTORIES, values, Directory._ID + "=?",
                            new String[] { String.valueOf(id) });
                } else {
                    id = db.insert(Tables.DIRECTORIES, null, values);
                }
                info.id = id;
            } finally {
                cursor.close();
            }
        }
    }

    protected String providerDescription(ProviderInfo provider) {
        return "Directory provider " + provider.packageName + "(" + provider.authority + ")";
    }
}
