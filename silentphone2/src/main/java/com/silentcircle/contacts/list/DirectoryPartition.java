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

import com.silentcircle.contacts.widget.CompositeCursorAdapter;

/**
 * Model object for a {@link com.silentcircle.silentcontacts.ScContactsContract.Directory} row.
 */
public final class DirectoryPartition extends CompositeCursorAdapter.Partition {

    public static final int STATUS_NOT_LOADED = 0;
    public static final int STATUS_LOADING = 1;
    public static final int STATUS_LOADED = 2;

    public static final int RESULT_LIMIT_DEFAULT = -1;

    private long mDirectoryId;
    private String mContentUri;
    private String mDirectoryType;
    private String mDisplayName;
    private int mStatus;
    private boolean mPriorityDirectory;
    private boolean mPhotoSupported;
    private int mResultLimit = RESULT_LIMIT_DEFAULT;
    private boolean mDisplayNumber = true;

    private String mLabel;

    public DirectoryPartition(boolean showIfEmpty, boolean hasHeader) {
        super(showIfEmpty, hasHeader);
    }

    /**
     * Directory ID, see {@link com.silentcircle.silentcontacts.ScContactsContract.Directory}.
     */
    public long getDirectoryId() {
        return mDirectoryId;
    }

    public void setDirectoryId(long directoryId) {
        this.mDirectoryId = directoryId;
    }

    /**
     * Directory type resolved from {@link com.silentcircle.silentcontacts.ScContactsContract.Directory#PACKAGE_NAME} and
     * {@link com.silentcircle.silentcontacts.ScContactsContract.Directory#TYPE_RESOURCE_ID};
     */
    public String getDirectoryType() {
        return mDirectoryType;
    }

    public void setDirectoryType(String directoryType) {
        this.mDirectoryType = directoryType;
    }

    /**
     * See {@link com.silentcircle.silentcontacts.ScContactsContract.Directory#DISPLAY_NAME}.
     */
    public String getDisplayName() {
        return mDisplayName;
    }

    public void setDisplayName(String displayName) {
        this.mDisplayName = displayName;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    public boolean isLoading() {
        return mStatus == STATUS_NOT_LOADED || mStatus == STATUS_LOADING;
    }

    /**
     * Returns true if this directory should be loaded before non-priority directories.
     */
    public boolean isPriorityDirectory() {
        return mPriorityDirectory;
    }

    public void setPriorityDirectory(boolean priorityDirectory) {
        mPriorityDirectory = priorityDirectory;
    }

    /**
     * Returns true if this directory supports photos.
     */
    public boolean isPhotoSupported() {
        return mPhotoSupported;
    }

    public void setPhotoSupported(boolean flag) {
        this.mPhotoSupported = flag;
    }

    /**
     * Max number of results for this directory. Defaults to {@link #RESULT_LIMIT_DEFAULT} which
     * implies using the adapter's
     * {@link com.android.contacts.common.list.ContactListAdapter#getDirectoryResultLimit()}
     */
    public int getResultLimit() {
        return mResultLimit;
    }

    public void setResultLimit(int resultLimit) {
        mResultLimit = resultLimit;
    }

    /**
     * Used by extended directories to specify a custom content URI. Extended directories MUST have
     * a content URI
     */
    public String getContentUri() {
        return mContentUri;
    }

    public void setContentUri(String contentUri) {
        mContentUri = contentUri;
    }

    /**
     * A label to display in the header next to the display name.
     */
    public String getLabel() {
        return mLabel;
    }

    public void setLabel(String label) {
        mLabel = label;
    }

    @Override
    public String toString() {
        return "DirectoryPartition{" +
                "mDirectoryId=" + mDirectoryId +
                ", mContentUri='" + mContentUri + '\'' +
                ", mDirectoryType='" + mDirectoryType + '\'' +
                ", mDisplayName='" + mDisplayName + '\'' +
                ", mStatus=" + mStatus +
                ", mPriorityDirectory=" + mPriorityDirectory +
                ", mPhotoSupported=" + mPhotoSupported +
                ", mResultLimit=" + mResultLimit +
                ", mLabel='" + mLabel + '\'' +
                '}';
    }

    /**
     * Whether or not to display the phone number in app that have that option - Dialer. If false,
     * Phone Label should be used instead of Phone Number.
     */
    public boolean isDisplayNumber() {
        return mDisplayNumber;
    }

    public void setDisplayNumber(boolean displayNumber) {
        mDisplayNumber = displayNumber;
    }
}
