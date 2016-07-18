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
package com.silentcircle.contacts.list;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.contacts.utils.AsyncTaskLoader;
import com.silentcircle.silentphone2.R;

/**
 * A specialized loader for the list of directories, see {@link com.silentcircle.silentcontacts.ScContactsContract.Directory}.
 */
public class DirectoryListLoader extends AsyncTaskLoader<Cursor> {

    private static final String TAG = "ContactEntryListAdapter";

    public static final int SEARCH_MODE_NONE = 0;
    public static final int SEARCH_MODE_DEFAULT = 1;
    public static final int SEARCH_MODE_CONTACT_SHORTCUT = 2;
    public static final int SEARCH_MODE_DATA_SHORTCUT = 3;

    private static final class DirectoryQuery {
        public static final Uri URI = Directory.CONTENT_URI;
        public static final String ORDER_BY = Directory._ID;

        public static final String[] PROJECTION = {
            Directory._ID,
            Directory.PACKAGE_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.DISPLAY_NAME,
            Directory.PHOTO_SUPPORT,
        };

        public static final int ID = 0;
        public static final int PACKAGE_NAME = 1;
        public static final int TYPE_RESOURCE_ID = 2;
        public static final int DISPLAY_NAME = 3;
        public static final int PHOTO_SUPPORT = 4;
    }

    public static final String DIRECTORY_TYPE = "directoryType";

    private static final String[] RESULT_PROJECTION = {
        Directory._ID,
        DIRECTORY_TYPE,
        Directory.DISPLAY_NAME,
        Directory.PHOTO_SUPPORT,
    };

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            forceLoad();
        }
    };

    private int mDirectorySearchMode;
    private boolean mLocalInvisibleDirectoryEnabled;

    private MatrixCursor mDefaultDirectoryList;

    public DirectoryListLoader(Context context) {
        super(context);
    }

    public void setDirectorySearchMode(int mode) {
        mDirectorySearchMode = mode;
    }

    /**
     * A flag that indicates whether the {@link com.silentcircle.silentcontacts.ScContactsContract.Directory#LOCAL_INVISIBLE} directory should
     * be included in the results.
     */
    public void setLocalInvisibleDirectoryEnabled(boolean flag) {
        this.mLocalInvisibleDirectoryEnabled = flag;
    }

    @Override
    protected void onStartLoading() {
        getContext().getContentResolver().
                registerContentObserver(Directory.CONTENT_URI, false, mObserver);
        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public Cursor loadInBackground() {
        if (mDirectorySearchMode == SEARCH_MODE_NONE) {
            return getDefaultDirectories();
        }

        MatrixCursor result = new MatrixCursor(RESULT_PROJECTION);
        Context context = getContext();
        PackageManager pm = context.getPackageManager();
        String selection;
        switch (mDirectorySearchMode) {
            case SEARCH_MODE_DEFAULT:
                selection = mLocalInvisibleDirectoryEnabled ? null
                        : (Directory._ID + "!=" + Directory.LOCAL_INVISIBLE);
                break;

            case SEARCH_MODE_CONTACT_SHORTCUT:
                selection = Directory.SHORTCUT_SUPPORT + "=" + Directory.SHORTCUT_SUPPORT_FULL
                        + (mLocalInvisibleDirectoryEnabled ? ""
                                : (" AND " + Directory._ID + "!=" + Directory.LOCAL_INVISIBLE));
                break;

            case SEARCH_MODE_DATA_SHORTCUT:
                selection = Directory.SHORTCUT_SUPPORT + " IN ("
                        + Directory.SHORTCUT_SUPPORT_FULL + ", "
                        + Directory.SHORTCUT_SUPPORT_DATA_ITEMS_ONLY + ")"
                        + (mLocalInvisibleDirectoryEnabled ? ""
                                : (" AND " + Directory._ID + "!=" + Directory.LOCAL_INVISIBLE));
                break;

            default:
                throw new RuntimeException("Unsupported directory search mode: " + mDirectorySearchMode);
        }

        Cursor cursor = context.getContentResolver().query(DirectoryQuery.URI, DirectoryQuery.PROJECTION, selection, 
                null, DirectoryQuery.ORDER_BY);
        try {
            while(cursor.moveToNext()) {
                long directoryId = cursor.getLong(DirectoryQuery.ID);
                String directoryType = null;

                String packageName = cursor.getString(DirectoryQuery.PACKAGE_NAME);
                int typeResourceId = cursor.getInt(DirectoryQuery.TYPE_RESOURCE_ID);
                if (!TextUtils.isEmpty(packageName) && typeResourceId != 0) {
                    try {
                        directoryType = pm.getResourcesForApplication(packageName)
                                .getString(typeResourceId);
                    } catch (Exception e) {
                        Log.e(TAG, "Cannot obtain directory type from package: " + packageName);
                    }
                }
                String displayName = cursor.getString(DirectoryQuery.DISPLAY_NAME);
                int photoSupport = cursor.getInt(DirectoryQuery.PHOTO_SUPPORT);
                result.addRow(new Object[]{directoryId, directoryType, displayName, photoSupport});
            }
        } finally {
            cursor.close();
        }

        return result;
    }

    private Cursor getDefaultDirectories() {
        if (mDefaultDirectoryList == null) {
            mDefaultDirectoryList = new MatrixCursor(RESULT_PROJECTION);
            mDefaultDirectoryList.addRow(new Object[] {
                    Directory.DEFAULT,
                    getContext().getString(R.string.contactsList),
                    null
            });
            mDefaultDirectoryList.addRow(new Object[] {
                    Directory.LOCAL_INVISIBLE,
                    getContext().getString(R.string.local_invisible_directory),
                    null
            });
        }
        return mDefaultDirectoryList;
    }

    @Override
    protected void onReset() {
        stopLoading();
    }
}
