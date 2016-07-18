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

package com.silentcircle.contacts.utils;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter to apply pre-selected ids and/or a regex to a content column.
 *
 * Created by werner on 22.09.15.
 */
public class ScCursorFilterWrapper extends CursorWrapper {

    private static final String TAG = "ScCursorFilterWrapper";

    final private int[] mIndex;
    private int mCount;
    private int mPosition = -1;
    final private long[] mPreSelectedIds;
    final private boolean mHasPreSelector;
    final private int mContentIndex;
    final private Pattern mContentPattern;

    public ScCursorFilterWrapper(Cursor cursor, long[] contactIds, int columnIndex, boolean hasPreSelector, String contentFilter, int contentIndex) {
        super(cursor);
        mPreSelectedIds = contactIds;
        mHasPreSelector = hasPreSelector;
        mContentIndex = contentIndex;
        mCount = super.getCount();

        mContentPattern = (contentFilter != null)? Pattern.compile(contentFilter) : null;

        if (mPreSelectedIds != null || mContentPattern != null) {
            mIndex = mPreSelectedIds != null ? new int[mPreSelectedIds.length] : new int[mCount];

            for (int i = 0; i < mCount; i++) {
                super.moveToPosition(i);
                final long id = getLong(columnIndex);
                if (isIdInPreSelected(id) && (mContentPattern == null || contentMatches())) {
                    mIndex[++mPosition] = i;
                }
            }
            mCount = mPosition + 1;
            mPosition = -1;
        }
        else {
//            Log.d(TAG, "++++ no preselected Ids, count: " + mCount);
            mIndex = new int[mCount];
            for (int i=0; i < mCount; i++) {
                mIndex[i] = i;
            }
        }
    }

    private boolean contentMatches() {
        String data = getString(mContentIndex);
        if (data == null)
            data = "";
        Matcher matcher = mContentPattern.matcher(data);
        return matcher.matches();
    }

    /**
     * Check if a contact id was found using the pre-selector.
     *
     * If no pre-selector was set then always return true because in this case
     * every contact id is part of the set.
     *
     * If the pre-selector was set but the selection returned no contact id
     * then always return false because it's an empty set.
     *
     * Otherwise check the selected contact ids
     *
     * @param id the contact id
     * @return {@code true} if found, {@code false} otherwise.
     */
    private boolean isIdInPreSelected(long id) {
        if (!mHasPreSelector)
            return true;
        else if (mPreSelectedIds == null)
            return false;
        else {
            int index = Arrays.binarySearch(mPreSelectedIds, id);
            return index >= 0;
        }
    }

    @Override
    public boolean move(int offset) {
        return this.moveToPosition(mPosition + offset);
    }

    @Override
    public boolean moveToNext() {
        return this.moveToPosition(++mPosition);
    }

    @Override
    public boolean moveToPrevious() {
        return this.moveToPosition(--mPosition);
    }

    @Override
    public boolean moveToFirst() {
        return this.moveToPosition(0);
    }

    @Override
    public boolean moveToLast() {
        return this.moveToPosition(mCount - 1);
    }

    @Override
    public boolean moveToPosition(int position) {
//        Log.d(TAG, "++++ moveToPosition: " + position);
        if (position == -1) {
            mPosition = -1;
            return super.moveToPosition(position);
        }
        if (position >= mCount || position < 0)
            return false;
//        Log.d(TAG, "++++ moveToPosition real: " + mIndex[position]);
        mPosition = position;
        return super.moveToPosition(mIndex[position]);
    }

    @Override
    public int getCount() {
        return mCount;
    }

    @Override
    public int getPosition() {
        return mPosition;
    }
}
