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

package com.silentcircle.contacts.model;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.common.collect.Lists;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Container for multiple {@link RawContactDelta} objects, usually when editing
 * together as an entire aggregate. Provides convenience methods for parceling
 * and applying another {@link RawContactDeltaList} over it.
 */
public class RawContactDeltaList extends ArrayList<RawContactDelta> implements Parcelable {
    private static final String TAG = RawContactDeltaList.class.getSimpleName();
    private static final boolean VERBOSE_LOGGING = false; // true;  // Log.isLoggable(TAG, Log.VERBOSE);

    private boolean mSplitRawContacts;
    private long[] mJoinWithRawContactIds;

    private RawContactDeltaList() {
    }

    /**
     * Create an {@link RawContactDeltaList} that contains the given {@link RawContactDelta},
     * usually when inserting a new {@link Contacts} entry.
     */
    public static RawContactDeltaList fromSingle(RawContactDelta delta) {
        final RawContactDeltaList state = new RawContactDeltaList();
        state.add(delta);
        return state;
    }

    /**
     * Create an {@link RawContactDeltaList} based on {@link Contacts} specified by the
     * given query parameters. This closes the {@link android.content.EntityIterator} when
     * finished, so it doesn't subscribe to updates.
     */
    public static RawContactDeltaList fromQuery(Uri entityUri, ContentResolver resolver,
            String selection, String[] selectionArgs, String sortOrder) {

        final EntityIterator iterator = RawContacts.newEntityIterator(
                resolver.query(entityUri, null, selection, selectionArgs, sortOrder));
        try {
            return fromIterator(iterator);
        }
        finally {
            iterator.close();
        }
    }

    /**
     * Create an {@link RawContactDeltaList} that contains the entities of the Iterator as before
     * values.  This function can be passed an iterator of Entity objects or an iterator of
     * RawContact objects.
     */
    public static RawContactDeltaList fromIterator(Iterator<?> iterator) {
        final RawContactDeltaList state = new RawContactDeltaList();
        // Perform background query to pull contact details
        while (iterator.hasNext()) {
            // Read all contacts into local deltas to prepare for edits
            Object nextObject = iterator.next();
            final RawContact before = nextObject instanceof Entity
                    ? RawContact.createFrom((Entity) nextObject)
                    : (RawContact) nextObject;
            final RawContactDelta rawContactDelta = RawContactDelta.fromBefore(before);
            state.add(rawContactDelta);
        }
        return state;
    }

    /**
     * Merge the "after" values from the given {@link RawContactDeltaList}, discarding any
     * previous "after" states. This is typically used when re-parenting user
     * edits onto an updated {@link RawContactDeltaList}.
     */
    public static RawContactDeltaList mergeAfter(RawContactDeltaList local, RawContactDeltaList remote) {
        if (local == null) local = new RawContactDeltaList();

        // For each entity in the remote set, try matching over existing
        for (RawContactDelta remoteEntity : remote) {
            final Long rawContactId = remoteEntity.getValues().getId();

            // Find or create local match and merge
            final RawContactDelta localEntity = local.getByRawContactId(rawContactId);
            final RawContactDelta merged = RawContactDelta.mergeAfter(localEntity, remoteEntity);

            if (localEntity == null && merged != null) {
                // No local entry before, so insert
                local.add(merged);
            }
        }

        return local;
    }

    /**
     * Build a list of {@link android.content.ContentProviderOperation} that will transform all
     * the "before" {@link android.content.Entity} states into the modified state which all
     * {@link RawContactDelta} objects represent. This method specifically creates
     * any {@link AggregationExceptions} rules needed to groups edits together.
     */
    public ArrayList<ContentProviderOperation> buildDiff() {
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "buildDiff: list=" + toString());
        }
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();

        final long rawContactId = this.findRawContactId();
        int firstInsertRow = -1;

        // First pass enforces versions remain consistent
        for (RawContactDelta delta : this) {
            delta.buildAssert(diff);
        }

        final int assertMark = diff.size();
        int backRefs[] = new int[size()];

        int rawContactIndex = 0;

        // Second pass builds actual operations
        for (RawContactDelta delta : this) {
            final int firstBatch = diff.size();
            final boolean isInsert = delta.isContactInsert();
            backRefs[rawContactIndex++] = isInsert ? firstBatch : -1;

            delta.buildDiff(diff);

            // If the user chose to join with some other existing raw contact(s) at save time,
            // add aggregation exceptions for all those raw contacts.
//            if (mJoinWithRawContactIds != null) {
//                for (Long joinedRawContactId : mJoinWithRawContactIds) {
//                    final Builder builder = beginKeepTogether();
//                    builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, joinedRawContactId);
//                    if (rawContactId != -1) {
//                        builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId);
//                    } else {
//                        builder.withValueBackReference(
//                                AggregationExceptions.RAW_CONTACT_ID2, firstBatch);
//                    }
//                    diff.add(builder.build());
//                }
//            }

            // Only create rules for inserts
            if (!isInsert)
                continue;

            // If we are going to split all contacts, there is no point in first combining them
            if (mSplitRawContacts)
                continue;

//            if (rawContactId != -1) {
//                if (VERBOSE_LOGGING) {
//                    Log.v(TAG, "existing contact, rawContactId: " + rawContactId);
//                }
//                // Has existing contact, so bind to it strongly
//                final Builder builder = beginKeepTogether();
//                builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId);
//                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, firstBatch);
//                diff.add(builder.build());
//
//            } else if (firstInsertRow == -1) {
//                // First insert case, so record row
//                firstInsertRow = firstBatch;
//
//            } else {
//                if (VERBOSE_LOGGING) {
//                    Log.v(TAG, "existing contact, rawContactId: " + rawContactId);
//                }
//                // Additional insert case, so point at first insert
//                final Builder builder = beginKeepTogether();
//                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID1, firstInsertRow);
//                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, firstBatch);
//                diff.add(builder.build());
//            }
        }

//        if (mSplitRawContacts) {
//            buildSplitContactDiff(diff, backRefs);
//        }

        // No real changes if only left with asserts
        if (diff.size() == assertMark) {
            diff.clear();
        }
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "buildDiff: ops=" + diffToString(diff));
        }
        return diff;
    }

    private static String diffToString(ArrayList<ContentProviderOperation> ops) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (ContentProviderOperation op : ops) {
            sb.append(op.toString());
            sb.append(",\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    /**
     * Start building a {@link android.content.ContentProviderOperation} that will keep two
     * {@link com.silentcircle.silentcontacts.ScContactsContract.RawContacts} together.
     */
//    protected Builder beginKeepTogether() {
//        final Builder builder = ContentProviderOperation
//                .newUpdate(AggregationExceptions.CONTENT_URI);
//        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
//        return builder;
//    }

    /**
     * Builds {@link AggregationExceptions} to split all constituent raw contacts into
     * separate contacts.
     */
//    private void buildSplitContactDiff(final ArrayList<ContentProviderOperation> diff,
//            int[] backRefs) {
//        int count = size();
//        for (int i = 0; i < count; i++) {
//            for (int j = 0; j < count; j++) {
//                if (i != j) {
//                    buildSplitContactDiff(diff, i, j, backRefs);
//                }
//            }
//        }
//    }

    /**
     * Construct a {@link AggregationExceptions#TYPE_KEEP_SEPARATE}.
     */
//    private void buildSplitContactDiff(ArrayList<ContentProviderOperation> diff, int index1,
//            int index2, int[] backRefs) {
//        Builder builder =
//                ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI);
//        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_SEPARATE);
//
//        Long rawContactId1 = get(index1).getValues().getAsLong(RawContacts._ID);
//        int backRef1 = backRefs[index1];
//        if (rawContactId1 != null && rawContactId1 >= 0) {
//            builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
//        } else if (backRef1 >= 0) {
//            builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID1, backRef1);
//        } else {
//            return;
//        }
//
//        Long rawContactId2 = get(index2).getValues().getAsLong(RawContacts._ID);
//        int backRef2 = backRefs[index2];
//        if (rawContactId2 != null && rawContactId2 >= 0) {
//            builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
//        } else if (backRef2 >= 0) {
//            builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, backRef2);
//        } else {
//            return;
//        }
//
//        diff.add(builder.build());
//    }

    /**
     * Search all contained {@link RawContactDelta} for the first one with an
     * existing {@link com.silentcircle.silentcontacts.ScContactsContract.RawContacts#_ID} value. Usually used when creating
     * {@link AggregationExceptions} during an update.
     */
    public long findRawContactId() {
        for (RawContactDelta delta : this) {
            final Long rawContactId = delta.getValues().getAsLong(RawContacts._ID);
            if (rawContactId != null && rawContactId >= 0) {
                return rawContactId;
            }
        }
        return -1;
    }

    /**
     * Find {@link com.silentcircle.silentcontacts.ScContactsContract.RawContacts#_ID} of the requested {@link RawContactDelta}.
     */
    public Long getRawContactId(int index) {
        if (index >= 0 && index < this.size()) {
            final RawContactDelta delta = this.get(index);
            final RawContactDelta.ValuesDelta values = delta.getValues();
            if (values.isVisible()) {
                return values.getAsLong(RawContacts._ID);
            }
        }
        return null;
    }

    /**
     * Find the raw-contact (an {@link RawContactDelta}) with the specified ID.
     */
    public RawContactDelta getByRawContactId(Long rawContactId) {
        final int index = this.indexOfRawContactId(rawContactId);
        return (index == -1) ? null : this.get(index);
    }

    /**
     * Find index of given {@link com.silentcircle.silentcontacts.ScContactsContract.RawContacts#_ID} when present.
     */
    public int indexOfRawContactId(Long rawContactId) {
        if (rawContactId == null) return -1;
        final int size = this.size();
        for (int i = 0; i < size; i++) {
            final Long currentId = getRawContactId(i);
            if (rawContactId.equals(currentId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Return the index of the first RawContactDelta corresponding to a writable raw-contact, or -1.
     * */
    public int indexOfFirstWritableRawContact(Context context) {
        // Find the first writable entity.
        int entityIndex = 0;
        for (RawContactDelta delta : this) {
            if (delta.getRawContactAccountType(context).areContactsWritable()) return entityIndex;
            entityIndex++;
        }
        return -1;
    }

    /**  Return the first RawContactDelta corresponding to a writable raw-contact, or null. */
    public RawContactDelta getFirstWritableRawContact(Context context) {
        final int index = indexOfFirstWritableRawContact(context);
        return (index == -1) ? null : get(index);
    }

    public RawContactDelta.ValuesDelta getSuperPrimaryEntry(final String mimeType) {
        RawContactDelta.ValuesDelta primary = null;
        RawContactDelta.ValuesDelta randomEntry = null;
        for (RawContactDelta delta : this) {
            final ArrayList<RawContactDelta.ValuesDelta> mimeEntries = delta.getMimeEntries(mimeType);
            if (mimeEntries == null) return null;

            for (RawContactDelta.ValuesDelta entry : mimeEntries) {
                if (entry.isSuperPrimary()) {
                    return entry;
                } else if (primary == null && entry.isPrimary()) {
                    primary = entry;
                } else if (randomEntry == null) {
                    randomEntry = entry;
                }
            }
        }
        // When no direct super primary, return something
        if (primary != null) {
            return primary;
        }
        return randomEntry;
    }

    /**
     * Sets a flag that will split ("explode") the raw_contacts into seperate contacts
     */
    public void markRawContactsForSplitting() {
        mSplitRawContacts = true;
    }

    public boolean isMarkedForSplitting() {
        return mSplitRawContacts;
    }

    public void setJoinWithRawContacts(long[] rawContactIds) {
        mJoinWithRawContactIds = rawContactIds;
    }

    public boolean isMarkedForJoining() {
        return mJoinWithRawContactIds != null && mJoinWithRawContactIds.length > 0;
    }

    /** {@inheritDoc} */
    @Override
    public int describeContents() {
        // Nothing special about this parcel
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        final int size = this.size();
        dest.writeInt(size);
        for (RawContactDelta delta : this) {
            dest.writeParcelable(delta, flags);
        }
        dest.writeLongArray(mJoinWithRawContactIds);
        dest.writeInt(mSplitRawContacts ? 1 : 0);
    }

    @SuppressWarnings("unchecked")
    public void readFromParcel(Parcel source) {
        final ClassLoader loader = getClass().getClassLoader();
        final int size = source.readInt();
        for (int i = 0; i < size; i++) {
            this.add(source.<RawContactDelta> readParcelable(loader));
        }
        mJoinWithRawContactIds = source.createLongArray();
        mSplitRawContacts = source.readInt() != 0;
    }

    public static final Creator<RawContactDeltaList> CREATOR =
            new Creator<RawContactDeltaList>() {
        @Override
        public RawContactDeltaList createFromParcel(Parcel in) {
            final RawContactDeltaList state = new RawContactDeltaList();
            state.readFromParcel(in);
            return state;
        }

        @Override
        public RawContactDeltaList[] newArray(int size) {
            return new RawContactDeltaList[size];
        }
    };

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append("Split=");
        sb.append(mSplitRawContacts);
        sb.append(", Join=[");
        sb.append(Arrays.toString(mJoinWithRawContactIds));
        sb.append("], Values=");
        sb.append(super.toString());
        sb.append(")");
        return sb.toString();
    }
}
