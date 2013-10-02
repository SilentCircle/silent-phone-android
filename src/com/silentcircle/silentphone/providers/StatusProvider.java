/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.silentcircle.silentcontacts.ScCallLog;
import com.silentcircle.silentphone.TiviPhoneService;

/**
 *
 * This is a simple contenr provider that returns a 1x1 cursor that holds the
 * status code of SilentPhone.
 *
 * Created by werner on 04.06.13.
 */
public class StatusProvider extends ContentProvider {

    private static final String TAG = "StatusProvider";

    private static final int STATUS = 1;

    public static final String AUTHORITY = "com.silentcircle.silentphone";

    /**
     * The content:// style URL for this provider
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * The content:// style URL for the status "table": a virtual table consisting of one row
     * and one column, integer field, that holds the status data.
     */
    public static final Uri CONTENT_URI_STATUS = Uri.parse("content://" + AUTHORITY + "/status");

    /**
     * The MIME type of a {@link #CONTENT_URI} sub-directory of a single call.
     */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.silentphone.status";


    /**
     * User did not start the SilentPhone application.
     */
    private static final int NOT_STARTED = 1;

    /**
     * User started SilentPhone but provisioning is not yet complete
     */
    private static final int NOT_PROVISIONED = 2;

    /**
     * SilentPhone is offline and not registered with SilentCircle's SIP servers.
     * This is the case if the user selected 'Logout' from the menu or if no
     * network is available.
     */
    private static final int OFFLINE = 3;

    /**
     * SilentPhone is ready.
     */
    private static final int ONLINE = 4;

    /**
     * SilentPhone currently registers with SilentCircle's SIP servers.
     * This is a transient state that may stay for a few seconds.
     */
    private static final int REGISTER = 5;

    /**
     * Cannot determine status. This is an error condition.
     */
    private static final int UNKNOWN_ERROR = -1;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(ScCallLog.AUTHORITY, "status", STATUS);
    }

    public StatusProvider() {
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int status = getCurrentStatus();

        final MatrixCursor c = new MatrixCursor(new String[] {"status"}, 1);
        final MatrixCursor.RowBuilder row = c.newRow();
        row.add(status);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Cannot insert URL: " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot update URL: " + uri);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot delete that URL: " + uri);
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);
        if (match == STATUS)
            return CONTENT_ITEM_TYPE;
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private int getCurrentStatus() {
        if (!TiviPhoneService.isInitialized())
            return NOT_STARTED;

        if (TiviPhoneService.mc == null && TiviPhoneService.doCmd("isProv") == 0)
            return NOT_PROVISIONED;

        int i = TiviPhoneService.getPhoneState();
        switch(i) {
            case 0:
                return OFFLINE;
            case 1:
                return REGISTER;
            case 2:
                return ONLINE;
            default:
                break;
        }
        return UNKNOWN_ERROR;
    }
}
