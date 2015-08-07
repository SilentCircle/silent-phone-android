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
 * limitations under the License
 */
package com.silentcircle.contacts.group;

/**
 * Meta-data for a contact group.  We load all groups associated with the contact's
 * constituent accounts.
 */
public final class GroupListItem {
    private final String mAccountName;
    private final String mAccountType;
    private final String mDataSet;
    private final long mGroupId;
    private final String mTitle;
    private final boolean mIsFirstGroupInAccount;
    private final int mMemberCount;

    public GroupListItem(String accountName, String accountType, String dataSet, long groupId,
            String title, boolean isFirstGroupInAccount, int memberCount) {
        mAccountName = accountName;
        mAccountType = accountType;
        mDataSet = dataSet;
        mGroupId = groupId;
        mTitle = title;
        mIsFirstGroupInAccount = isFirstGroupInAccount;
        mMemberCount = memberCount;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public String getAccountType() {
        return mAccountType;
    }

    public String getDataSet() {
        return mDataSet;
    }

    public long getGroupId() {
        return mGroupId;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getMemberCount() {
        return mMemberCount;
    }

    public boolean hasMemberCount() {
        return mMemberCount != -1;
    }

    public boolean isFirstGroupInAccount() {
        return mIsFirstGroupInAccount;
    }
}
