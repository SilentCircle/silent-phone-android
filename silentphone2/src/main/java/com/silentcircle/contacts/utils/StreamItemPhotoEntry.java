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
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts.utils;

import android.database.Cursor;

import com.silentcircle.silentcontacts2.ScContactsContract.PhotoFiles;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItemPhotos;

/**
 * Data object for a photo associated with a social stream item.  These are comparable;
 * entries with a lower sort index will be displayed on top (with the ID used as a
 * tie-breaker).
 */
public class StreamItemPhotoEntry implements Comparable<StreamItemPhotoEntry> {
    private final long mId;
    private final int mSortIndex;
    private final long mPhotoFileId;
    private final String mPhotoUri;
    private final int mHeight;
    private final int mWidth;
    private final int mFileSize;

    public StreamItemPhotoEntry(long id, int sortIndex, long photoFileId, String photoUri,
            int height, int width, int fileSize) {
        mId = id;
        mSortIndex = sortIndex;
        mPhotoFileId = photoFileId;
        mPhotoUri = photoUri;
        mHeight = height;
        mWidth = width;
        mFileSize = fileSize;
    }

    public StreamItemPhotoEntry(Cursor cursor) {
        // This is expected to be populated via a cursor containing a join of all
        // StreamItemPhotos columns and all PhotoFiles columns (except for ID).
        mId = getLong(cursor, StreamItemPhotos._ID);
        mSortIndex = getInt(cursor, StreamItemPhotos.SORT_INDEX, -1);
        mPhotoFileId = getLong(cursor, StreamItemPhotos.PHOTO_FILE_ID);
        mPhotoUri = getString(cursor, StreamItemPhotos.PHOTO_URI);
        mHeight = getInt(cursor, PhotoFiles.HEIGHT, -1);
        mWidth = getInt(cursor, PhotoFiles.WIDTH, -1);
        mFileSize = getInt(cursor, PhotoFiles.FILESIZE, -1);
    }

    public long getId() {
        return mId;
    }

    public int getSortIndex() {
        return mSortIndex;
    }

    public long getPhotoFileId() {
        return mPhotoFileId;
    }

    public String getPhotoUri() {
        return mPhotoUri;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getFileSize() {
        return mFileSize;
    }

    @Override
    public int compareTo(StreamItemPhotoEntry streamItemPhotoEntry) {
        // Sort index is used to compare, falling back to ID if neither entry has a
        // sort index specified (entries without a sort index are sorted after entries
        // that have one).
        if (mSortIndex == streamItemPhotoEntry.mSortIndex) {
            if (mSortIndex == -1) {
                return mId == streamItemPhotoEntry.mId ? 0
                        : mId < streamItemPhotoEntry.mId ? -1 : 1;
            } else {
                return 0;
            }
        } else {
            if (mSortIndex == -1) {
                return 1;
            }
            if (streamItemPhotoEntry.mSortIndex == -1) {
                return -1;
            }
            return mSortIndex == streamItemPhotoEntry.mSortIndex ? 0
                    : mSortIndex < streamItemPhotoEntry.mSortIndex ? -1 : 1;
        }
    }

    private static String getString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndex(columnName));
    }

    private static int getInt(Cursor cursor, String columnName, int missingValue) {
        final int columnIndex = cursor.getColumnIndex(columnName);
        return cursor.isNull(columnIndex) ? missingValue : cursor.getInt(columnIndex);
    }

    private static long getLong(Cursor cursor, String columnName) {
        final int columnIndex = cursor.getColumnIndex(columnName);
        return cursor.getLong(columnIndex);
    }
}
