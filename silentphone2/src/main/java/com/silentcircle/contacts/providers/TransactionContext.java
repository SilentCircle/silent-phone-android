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
 * limitations under the License
 */

package com.silentcircle.contacts.providers;

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Set;

/**
 * Accumulates information for an entire transaction. {@link ContactsProvider2} consumes
 * it at commit time.
 */
public class TransactionContext  {

    private final boolean mForProfile;
    /** Map from raw contact id to account Id */
//    private HashMap<Long, Long> mInsertedRawContactsAccounts;
//    private HashSet<Long> mUpdatedRawContacts;
//    private HashSet<Long> mDirtyRawContacts;
    private HashSet<Long> mStaleSearchIndexRawContacts;
    private HashSet<Long> mStaleSearchIndexContacts;
//    private HashMap<Long, Object> mUpdatedSyncStates;

    // Set used to track what has been changed and deleted. This is needed so we can update the
    // contact last touch timestamp.  Dirty set above is only set when sync adapter is false.
    // {@see android.provider.ContactsContract#CALLER_IS_SYNCADAPTER}. While the set below will
    // contain all changed contacts.
    private HashSet<Long> mChangedRawContacts;

    public TransactionContext(boolean forProfile) {
        mForProfile = forProfile;
    }

    public boolean isForProfile() {
        return mForProfile;
    }

    public void rawContactInserted(long rawContactId) {
//        if (mInsertedRawContactsAccounts == null) mInsertedRawContactsAccounts = new HashMap<Long, Long>();
//        mInsertedRawContactsAccounts.put(rawContactId, accountId);

        markRawContactChangedOrDeletedOrInserted(rawContactId);
    }
//
//    public void rawContactUpdated(long rawContactId) {
//        if (mUpdatedRawContacts == null) mUpdatedRawContacts = new HashSet<Long>();
//        mUpdatedRawContacts.add(rawContactId);
//    }
//
    public void markRawContactDirtyAndChanged(long rawContactId, boolean isSyncAdapter) {
//        if (!isSyncAdapter) {
//            if (mDirtyRawContacts == null) mDirtyRawContacts = new HashSet<Long>();
//            mDirtyRawContacts.add(rawContactId);
//        }

        markRawContactChangedOrDeletedOrInserted(rawContactId);
    }

    public void markRawContactChangedOrDeletedOrInserted(long rawContactId) {
        if (mChangedRawContacts == null) {
            mChangedRawContacts = Sets.newHashSet();
        }
        mChangedRawContacts.add(rawContactId);
    }
//
//    public void syncStateUpdated(long rowId, Object data) {
//        if (mUpdatedSyncStates == null) mUpdatedSyncStates = new HashMap<Long, Object>();
//        mUpdatedSyncStates.put(rowId, data);
//    }

    public void invalidateSearchIndexForRawContact(long rawContactId) {
        if (mStaleSearchIndexRawContacts == null) mStaleSearchIndexRawContacts = new HashSet<Long>();
        mStaleSearchIndexRawContacts.add(rawContactId);
    }

    public void invalidateSearchIndexForContact(long contactId) {
        if (mStaleSearchIndexContacts == null) mStaleSearchIndexContacts = new HashSet<Long>();
        mStaleSearchIndexContacts.add(contactId);
    }

//    public Set<Long> getInsertedRawContactIds() {
//        if (mInsertedRawContactsAccounts == null) mInsertedRawContactsAccounts = new HashMap<Long, Long>();
//        return mInsertedRawContactsAccounts.keySet();
//    }
//
//    public Set<Long> getUpdatedRawContactIds() {
//        if (mUpdatedRawContacts == null) mUpdatedRawContacts = new HashSet<Long>();
//        return mUpdatedRawContacts;
//    }
//
//    public Set<Long> getDirtyRawContactIds() {
//        if (mDirtyRawContacts == null) mDirtyRawContacts = new HashSet<Long>();
//        return mDirtyRawContacts;
//    }

    public Set<Long> getChangedRawContactIds() {
        if (mChangedRawContacts == null) mChangedRawContacts = Sets.newHashSet();
        return mChangedRawContacts;
    }

    public Set<Long> getStaleSearchIndexRawContactIds() {
        if (mStaleSearchIndexRawContacts == null) mStaleSearchIndexRawContacts = new HashSet<Long>();
        return mStaleSearchIndexRawContacts;
    }

    public Set<Long> getStaleSearchIndexContactIds() {
        if (mStaleSearchIndexContacts == null) mStaleSearchIndexContacts = new HashSet<Long>();
        return mStaleSearchIndexContacts;
    }

//    public Set<Entry<Long, Object>> getUpdatedSyncStates() {
//        if (mUpdatedSyncStates == null) mUpdatedSyncStates = new HashMap<Long, Object>();
//        return mUpdatedSyncStates.entrySet();
//    }
//
//    public Long getAccountIdOrNullForRawContact(long rawContactId) {
//        if (mInsertedRawContactsAccounts == null) mInsertedRawContactsAccounts = new HashMap<Long, Long>();
//        return mInsertedRawContactsAccounts.get(rawContactId);
//    }
//
//    public boolean isNewRawContact(long rawContactId) {
//        if (mInsertedRawContactsAccounts == null) mInsertedRawContactsAccounts = new HashMap<Long, Long>();
//        return mInsertedRawContactsAccounts.containsKey(rawContactId);
//    }
//
    public void clearExceptSearchIndexUpdates() {
//        mInsertedRawContactsAccounts = null;
//        mUpdatedRawContacts = null;
//        mUpdatedSyncStates = null;
//        mDirtyRawContacts = null;
        mChangedRawContacts = null;

    }

    public void clearSearchIndexUpdates() {
        mStaleSearchIndexRawContacts = null;
        mStaleSearchIndexContacts = null;
    }

    public void clearAll() {
//        clearExceptSearchIndexUpdates();
        clearSearchIndexUpdates();
    }
}
