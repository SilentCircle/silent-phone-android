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

package com.silentcircle.contacts.list;

import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.util.Log;

import com.silentcircle.contacts.utils.ScCursorFilterWrapper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Cursor loader supporting filtering of SC specific contacts.
 *
 * This loader is a modification of Android's standard cursor loader. It implements
 * a "pre-selection" that selects specific contacts and stores them. Applications
 * may then ask this loader if a contact selected with the normal selection was
 * selected during the "pre-selection" query.
 *
 * The loader uses the {@code Data.CONTENT_URI} to select the contact ids, thus the
 * selector must use {@code Data} fields only.
 *
 * If no pre-selector is set then this loader works in the same way as the standard
 * cursor loader.
 *
 * Example of a simple pre-selector string:
 * <pre>
 *     Data.MIMETYPE + "='vnd.android.cursor.item/com.silentcircle.phone' OR " +
 *          Data.MIMETYPE + "='vnd.android.cursor.item/com.silentcircle.message'";
 * </pre>
 *
 * Example with selector argument:
 * <pre>
 *     Data.MIMETYPE + "='vnd.android.cursor.item/com.silentcircle.phone' AND " + Data.DATA1 + "=?"
 * </pre>
 * Then use {@link #setPreSelectorArguments(String[])} to set the arguments:
 * <pre>
 *     setPreSelectorArguments(new String[] {"some data"});
 * </pre>
 *
 * Created by werner on 22.09.15.
 */
public class CursorLoaderSc extends CursorLoader {
    private static final String TAG = "CursorLoaderSc";

    private String mPreSelector;
    private String[] mPreSelectorArgs;
    private long[] mPreSelectedIds;

    private String mContentFilter;
    private int mContentColumn;

    private final static String[] mAllData = {
            ContactsContract.Data._ID, //.....................0
            ContactsContract.Data.CONTACT_ID, //..............1
            ContactsContract.Data.MIMETYPE, //................2
    };

    private final static int _ID  =          0;
    private final static int CONTACT_ID  =   1;
    private final static int MIME_TYPE =     2;

    public CursorLoaderSc(Context context) {
        super(context);
    }

    public CursorLoaderSc(Context context, Uri uri, String[] projection, String selection,
                          String[] selectionArgs, String sortOrder) {
        super(context, uri, projection, selection, selectionArgs, sortOrder);
    }

    /* Runs on a worker thread */
    @Override
    public Cursor loadInBackground() {
        if (mPreSelector != null) {
            preSelect();
        }
        Cursor cursor = super.loadInBackground();
        ScCursorFilterWrapper scCursor = null;
        if (cursor != null) {
            scCursor = new ScCursorFilterWrapper(cursor, mPreSelectedIds, CONTACT_ID, mPreSelector != null, mContentFilter, mContentColumn);
        }
        return scCursor;
    }

    private void preSelect() {
        Cursor cursor = getContext().getContentResolver().query(Data.CONTENT_URI, mAllData, mPreSelector, mPreSelectorArgs,
                Data.CONTACT_ID + " ASC");
        if (cursor != null && cursor.getCount() > 0) {
            mPreSelectedIds = new long[cursor.getCount()];
            for (int index = 0; cursor.moveToNext(); ) {
                mPreSelectedIds[index] = cursor.getLong(CONTACT_ID);
            }
            cursor.close();
        }
    }

    /**
     * Set pre-selector string and its arguments
     * @param selector the selector string
     * @param args the arguments for the selector or {@code null} if none.
     */
    public void setPreSelector(final String selector, final String[] args) {
        mPreSelector = selector;
        mPreSelectorArgs = args;
    }

    /**
     * Set a content filter for the given content column of the cursor.
     *
     * The content filter applies the filter pattern (regex) to the content of the
     * give cursor column.
     *
     * The content column must be of type text. The filter pattern must match the
     * rules for a Java regex pattern.
     *
     * @param filterPattern a Java regex string
     * @param contentColumn the column to use as content
     */
    public void setContentFilter(final String filterPattern, final int contentColumn) {
        mContentFilter = filterPattern;
        mContentColumn = contentColumn;
    }
}
