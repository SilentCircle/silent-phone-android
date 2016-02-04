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
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License
 */
package com.silentcircle.contacts.providers;

import android.content.ContentValues;
import android.content.Context;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Clauses;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.DataColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.GroupsColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Projections;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.contacts.providers.aggregation.SimpleRawContactAggregator;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.GroupMembership;
import com.silentcircle.silentcontacts2.ScContactsContract.Groups;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import net.sqlcipher.Cursor;
import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * Handler for group membership data rows.
 */
public class DataRowHandlerForGroupMembership extends DataRowHandler {

    /**
     * An entry in group id cache.
     *
     * TODO: Move this and {@link #mGroupIdCache} to {@link DataRowHandlerForGroupMembership}.
     */
    public static class GroupIdCacheEntry {
        long accountId;
        String sourceId;
        long groupId;
    }

    /**
     * Map from group source IDs to lists of {@link GroupIdCacheEntry}s.
     *
     * We don't need a soft cache for groups - the assumption is that there will only
     * be a small number of contact groups. The cache is keyed off source id.  The value
     * is a list of groups with this group id.
     */
    private HashMap<String, ArrayList<GroupIdCacheEntry>> mGroupIdCache = new HashMap<String, ArrayList<GroupIdCacheEntry>>();

    interface RawContactsQuery {
        String TABLE = Tables.RAW_CONTACTS;

        String[] COLUMNS = new String[] {
                RawContacts.DELETED,
        };

        int DELETED = 0;
    }

    private static final String SELECTION_RAW_CONTACT_ID = RawContacts._ID + "=?";

    private static final String QUERY_COUNT_FAVORITES_GROUP_MEMBERSHIPS_BY_RAW_CONTACT_ID =
            "SELECT COUNT(*) FROM " + Tables.DATA + " LEFT OUTER JOIN " + Tables .GROUPS
                    + " ON " + Tables.DATA + "." + GroupMembership.GROUP_ROW_ID
                    + "=" + GroupsColumns.CONCRETE_ID
                    + " WHERE " + DataColumns.MIMETYPE_ID + "=?"
                    + " AND " + Tables.DATA + "." + GroupMembership.RAW_CONTACT_ID + "=?"
                    + " AND " + Groups.FAVORITES + "!=0";


    public DataRowHandlerForGroupMembership(Context context, ScContactsDatabaseHelper dbHelper,
            SimpleRawContactAggregator aggregator) {
        super(context, dbHelper, aggregator, GroupMembership.CONTENT_ITEM_TYPE);
    }

    @Override
    public long insert(SQLiteDatabase db, TransactionContext txContext, long rawContactId, ContentValues values) {
        resolveGroupSourceIdInValues(txContext, rawContactId, db, values, true);
        long dataId = super.insert(db, txContext, rawContactId, values);
        if (hasFavoritesGroupMembership(db, rawContactId)) {
            updateRawContactsStar(db, rawContactId, true /* starred */);
        }
        updateVisibility(txContext, rawContactId);
        return dataId;
    }

    @Override
    public boolean update(SQLiteDatabase db, TransactionContext txContext, ContentValues values,
            Cursor c, boolean callerIsSyncAdapter) {
        long rawContactId = c.getLong(DataUpdateQuery.RAW_CONTACT_ID);
        boolean wasStarred = hasFavoritesGroupMembership(db, rawContactId);
        resolveGroupSourceIdInValues(txContext, rawContactId, db, values, false);
        if (!super.update(db, txContext, values, c, callerIsSyncAdapter)) {
            return false;
        }
        boolean isStarred = hasFavoritesGroupMembership(db, rawContactId);
        if (wasStarred != isStarred) {
            updateRawContactsStar(db, rawContactId, isStarred);
        }
        updateVisibility(txContext, rawContactId);
        return true;
    }

    private void updateRawContactsStar(SQLiteDatabase db, long rawContactId, boolean starred) {
        ContentValues rawContactValues = new ContentValues();
        rawContactValues.put(RawContacts.STARRED, starred ? 1 : 0);
        if (db.update(Tables.RAW_CONTACTS, rawContactValues, SELECTION_RAW_CONTACT_ID,
                new String[]{Long.toString(rawContactId)}) > 0) {
//            mContactAggregator.updateStarred(rawContactId);
        }
    }

    private boolean hasFavoritesGroupMembership(SQLiteDatabase db, long rawContactId) {
        // TODO compiled SQL statement
        final long groupMembershipMimetypeId = mDbHelper
                .getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE);
        boolean isStarred = 0 < DatabaseUtils
                .longForQuery(db, QUERY_COUNT_FAVORITES_GROUP_MEMBERSHIPS_BY_RAW_CONTACT_ID,
                        new String[]{Long.toString(groupMembershipMimetypeId), Long.toString(rawContactId)});
        return isStarred;
    }

    @Override
    public int delete(SQLiteDatabase db, TransactionContext txContext, Cursor c) {
        long rawContactId = c.getLong(DataDeleteQuery.RAW_CONTACT_ID);
        boolean wasStarred = hasFavoritesGroupMembership(db, rawContactId);
        int count = super.delete(db, txContext, c);
        boolean isStarred = hasFavoritesGroupMembership(db, rawContactId);
        if (wasStarred && !isStarred) {
            updateRawContactsStar(db, rawContactId, false /* starred */);
        }
        updateVisibility(txContext, rawContactId);
        return count;
    }

    private void updateVisibility(TransactionContext txContext, long rawContactId) {
    }

    private void resolveGroupSourceIdInValues(TransactionContext txContext,
            long rawContactId, SQLiteDatabase db, ContentValues values, boolean isInsert) {
        boolean containsGroupSourceId = values.containsKey(GroupMembership.GROUP_SOURCE_ID);
        boolean containsGroupId = values.containsKey(GroupMembership.GROUP_ROW_ID);
        if (containsGroupSourceId && containsGroupId) {
            throw new IllegalArgumentException(
                    "you are not allowed to set both the GroupMembership.GROUP_SOURCE_ID "
                            + "and GroupMembership.GROUP_ROW_ID");
        }

        if (!containsGroupSourceId && !containsGroupId) {
            if (isInsert) {
                throw new IllegalArgumentException(
                        "you must set exactly one of GroupMembership.GROUP_SOURCE_ID "
                                + "and GroupMembership.GROUP_ROW_ID");
            } else {
                return;
            }
        }

        if (containsGroupSourceId) {
            final String sourceId = values.getAsString(GroupMembership.GROUP_SOURCE_ID);
            final long groupId = getOrMakeGroup(db, rawContactId, sourceId, null);
//                    txContext.getAccountIdOrNullForRawContact(rawContactId));
            values.remove(GroupMembership.GROUP_SOURCE_ID);
            values.put(GroupMembership.GROUP_ROW_ID, groupId);
        }
    }

    /**
     * Returns the group id of the group with sourceId and the same account as rawContactId.
     * If the group doesn't already exist then it is first created.
     *
     * @param db SQLiteDatabase to use for this operation
     * @param rawContactId the raw contact this group is associated with
     * @param sourceId the source ID of the group to query or create
     * @param accountIdOrNull the account ID for the raw contact.  If null it'll be queried from
     *    the raw_contacts table.
     * @return the group id of the existing or created group
     * @throws IllegalArgumentException if the contact is not associated with an account
     * @throws IllegalStateException if a group needs to be created but the creation failed
     */
    private long getOrMakeGroup(SQLiteDatabase db, long rawContactId, String sourceId, Long accountIdOrNull) {

//        if (accountIdOrNull == null) {
//            mSelectionArgs1[0] = String.valueOf(rawContactId);
//            Cursor c = db.query(RawContactsQuery.TABLE, RawContactsQuery.COLUMNS,
//                    RawContactsColumns.CONCRETE_ID + "=?", mSelectionArgs1, null, null, null);
//            try {
//                if (c.moveToFirst()) {
//                    accountIdOrNull = c.getLong(RawContactsQuery.ACCOUNT_ID);
//                }
//            } finally {
//                c.close();
//            }
//        }
//
//        if (accountIdOrNull == null) {
//            throw new IllegalArgumentException("Raw contact not found for _ID=" + rawContactId);
//        }
//        final long accountId = accountIdOrNull;
        final long accountId = 0;

        ArrayList<GroupIdCacheEntry> entries = mGroupIdCache.get(sourceId);
        if (entries == null) {
            entries = new ArrayList<GroupIdCacheEntry>(1);
            mGroupIdCache.put(sourceId, entries);
        }

        int count = entries.size();
        for (int i = 0; i < count; i++) {
            GroupIdCacheEntry entry = entries.get(i);
            if (entry.accountId == accountId) {
                return entry.groupId;
            }
        }

        GroupIdCacheEntry entry = new GroupIdCacheEntry();
        entry.accountId = accountId;
        entry.sourceId = sourceId;
        entries.add(0, entry);

        // look up the group that contains this sourceId and has the same account as the contact
        // referred to by rawContactId
        Cursor c = db.query(Tables.GROUPS, Projections.ID, Clauses.GROUP_HAS_ACCOUNT_AND_SOURCE_ID,
                new String[]{sourceId}, null, null, null);

        try {
            if (c.moveToFirst()) {
                entry.groupId = c.getLong(0);
            } else {
                ContentValues groupValues = new ContentValues();
                groupValues.put(Groups.SOURCE_ID, sourceId);
                long groupId = db.insert(Tables.GROUPS, null, groupValues);
                if (groupId < 0) {
                    throw new IllegalStateException("unable to create a new group with "
                            + "this sourceid: " + groupValues);
                }
                entry.groupId = groupId;
            }
        } finally {
            c.close();
        }

        return entry.groupId;
    }
}
