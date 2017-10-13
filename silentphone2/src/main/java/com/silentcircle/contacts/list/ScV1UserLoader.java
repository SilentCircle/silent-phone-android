/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.userinfo.LoadUserInfo;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

/**
 * Loader for exact match on v1/user. Replaces shortcuts Call <user> and Write to <user>.
 */
public class ScV1UserLoader extends AsyncTaskLoader<Cursor> {

    private static final String TAG = ScV1UserLoader.class.getSimpleName();

    public static final int MIN_SEARCH_LENGTH = 2;

    public static final int MAX_RECORDS = 1;

    public static final int NONE = 0;
    public static final int SHOW = 1;

    /* *** IMPORTANT: Keep in-sync with PhoneNumberListAdapter projections, see also addRow() below *** */
    private static final String[] PROJECTION = new String[] {
            ContactsContract.CommonDataKinds.Phone._ID,                          // 0
            ContactsContract.CommonDataKinds.Phone.TYPE,                         // 1
            ContactsContract.CommonDataKinds.Phone.LABEL,                        // 2
            ContactsContract.CommonDataKinds.Phone.NUMBER,                       // 3
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,                   // 4
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,                   // 5
            ContactsContract.CommonDataKinds.Phone.PHOTO_ID,                     // 6
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,         // 7
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,          // 8
            ScDirectoryLoader.SC_PRIVATE_FIELD,                                  // 9
            // must be at the end, otherwise the adapters/fragments get confused
            ScDirectoryLoader.SC_UUID_FIELD,                                    // 10
    };

    private int mFilterType = SHOW;
    private String mSearchText = "";
    private String mAuthorization;

    private static final MatrixCursor EMPTY_CURSOR = new MatrixCursor(PROJECTION, 0);

    public ScV1UserLoader(Context context) {
        super(context);
        byte[] data = KeyManagerSupport.getSharedKeyData(context.getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (data != null) {
            try {
                mAuthorization = new String(data, "UTF-8").trim();
                if (ConfigurationUtilities.mTrace)
                    Log.d(TAG, "Authentication data SC dir (API key) : " + mAuthorization);
            } catch (UnsupportedEncodingException ignored) {
                // any search will not be possible and return empty set
            }
        }
    }

    public void setQueryString(String query) {
        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "setQueryString query: " + query);
        }
        mSearchText = TextUtils.isEmpty(query) ? "" : query.toLowerCase(Locale.getDefault());
        if (PhoneNumberUtils.isGlobalPhoneNumber(mSearchText.replaceAll("\\s", ""))) {
            mSearchText = PhoneNumberHelper.normalizeNumber(mSearchText);
        }
        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "setQueryString:  " + mSearchText);
        }
    }

    public void setFilterType(final int filterType) {
        mFilterType = filterType;
    }

    @Override
    protected void onStartLoading() {
        Log.d(TAG, "onStartLoading query: " + mSearchText + ", filter type: " + mFilterType);
        if (mFilterType == NONE || TextUtils.isEmpty(mSearchText)) {
            deliverResult(EMPTY_CURSOR);
        }
        else {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
    }

    @Override
    public void onCanceled(Cursor data) {
        if (data != null) {
            data.close();
        }
    }

    @Override
    public Cursor loadInBackground() {
        Log.d(TAG, "loadInBackground query: " + mSearchText);
        if (mSearchText == null || mSearchText.length() < MIN_SEARCH_LENGTH) {
            return EMPTY_CURSOR;
        }

        ScDirectoryLoader.UserData userData;
        try {
            userData = loadV1User(mSearchText);
        }
        catch (Throwable t) {
            Log.d(TAG, "loadInBackground return empty cursor: " + t.getMessage());
            return EMPTY_CURSOR;
        }
        return createCursor(userData);
    }

    @Override
    protected void onReset() {
        stopLoading();
    }

    private Cursor createCursor(ScDirectoryLoader.UserData userData) {
        if (userData == null) {
            return EMPTY_CURSOR;
        }

        MatrixCursor cursor = new MatrixCursor(PROJECTION, MAX_RECORDS);

        int _id = 3;
        addRow(cursor, userData, _id);

        return cursor;
    }

    private void addRow(MatrixCursor cursor, ScDirectoryLoader.UserData ud, long _id/*, int type*/) {
        MatrixCursor.RowBuilder row;
        row = cursor.newRow();
        row.add(_id);                               // _ID
                                                    // TYPE, must be an in-circle number
        row.add(ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM);
        row.add(getContext().getString(R.string.phoneTypeSilent));
        row.add(ud.userName);
        row.add(_id);                               // CONTACT_ID
        row.add(null);                              // LOOKUP-KEY
        row.add(null);                              // PHOTO_ID
        row.add(ud.displayName);
        row.add(null);                              // Phone.PHOTO_THUMBNAIL_URI
        /* populate private field with organization only if organization differs */
        row.add(!ud.inSameOrganization
                ? ud.organization
                : null);                            // SC_PRIVATE_FIELD
        row.add(ud.uuid);                           // SC_UUID_FIELD
    }

    @Nullable
    private ScDirectoryLoader.UserData loadV1User(String query) {
        Log.d(TAG, "loadV1User query: " + query);
        ScDirectoryLoader.UserData result = null;

        int[] error = new int[1];
        byte[] data = ZinaMessaging.getUserInfo(query, IOUtils.encode(mAuthorization), error);
        AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(data);
        /*
         * Organization comparison is case sensitive and takes place only when a regular string
         * is looked up. If search query is a phone number, result from v1/user will be shown
         * for one's own organization as well.
         */
        if (userInfo != null
                && (PhoneNumberUtils.isGlobalPhoneNumber(query.replaceAll("\\s", ""))
                    || !TextUtils.equals(LoadUserInfo.getDisplayOrg(), userInfo.organization))) {
            result = new ScDirectoryLoader.UserData(userInfo.mDisplayName, userInfo.mDisplayName,
                    userInfo.mAlias, userInfo.mUuid, userInfo.mDisplayName, userInfo.organization,
                    userInfo.isInSameOrganization);
        }
        return result;
    }

}
