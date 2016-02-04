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

package com.silentcircle.silentphone2.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.silentcircle.common.util.DataUsageStatUpdater;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.silentcontacts2.ScCallLog;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.list.PhoneFavoritesTileAdapter;
import com.silentcircle.silentphone2.list.ShortcutCardsAdapter;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.util.ArrayList;

/**
 * Counts the number of missed new calls in the call log and creates a Notification if
 * the number is greater zero.
 *
 * Created by werner on 11.10.14.
 */
public class InsertCallLogHelper extends AsyncTask<Uri, Void, Cursor> {

    @SuppressWarnings("unused")
    private static final String TAG = "InsertCallLogHelper";
    private static final long NO_CONTACT_FOUND = -1;

    public static final int MISSED_CALL_NOTIFICATION_ID = 47120815;
    private static Bitmap mNotifyLargeIcon;


    private Context mCtx;
    private CallState mCall;
    private ContentValues mInsertValues;

    InsertCallLogHelper(Context ctx, CallState call, ContentValues values) {
        mCtx = ctx;
        mCall = call;
        mInsertValues = values;
    }

    public static void removeMissedCallNotifications(Context ctx) {
        NotificationManager notificationManager = (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(MISSED_CALL_NOTIFICATION_ID);
    }

    private int removeExpiredEntries() {
        final ContentResolver resolver = mCtx.getContentResolver();
        return resolver.delete(ScCallLog.ScCalls.CONTENT_URI, "_id IN " +
                "(SELECT _id FROM calls ORDER BY " + ScCallLog.ScCalls.DEFAULT_SORT_ORDER
                + " LIMIT -1 OFFSET 500)", null);
    }

    @Override
    protected Cursor doInBackground(Uri... uri) {
        ContentResolver resolver = mCtx.getContentResolver();

        resolver.insert(uri[0], mInsertValues);
        int removed = removeExpiredEntries();
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Removed " + removed + " old/expired call log entries");

        // Update usage counter for this number and contact
        String called = mInsertValues.getAsString(ScCallLog.ScCalls.NUMBER);
        DataUsageStatUpdater updater = new DataUsageStatUpdater(mCtx);
        updater.updateWithPhoneNumber(called, mCall.contactId);

        // Now check if we should un-demote this contact because it was used again to make an outgoing call
        int type = mInsertValues.getAsInteger(ScCallLog.ScCalls.TYPE);
        if (type == ScCallLog.ScCalls.OUTGOING_TYPE) {
            long id = getContactIdFromPhoneNumber(mCtx, mInsertValues.getAsString(ScCallLog.ScCalls.NUMBER));
            if (id != NO_CONTACT_FOUND) {
                unDemote(id);
            }
        }
        resolver.notifyChange(ShortcutCardsAdapter.AUTHORITY_URI, null, false);
        // Query new and missed call log entries
        // TODO: request ScCallLog.ScCalls.DATE field and sort by this field. Use field from first record to build the notification below (setWhen(...))
        return resolver.query(ScCallLog.ScCalls.CONTENT_URI, new String[] {ScCallLog.ScCalls._ID},
                ScCallLog.ScCalls.NEW + " = 1" + " AND " + ScCallLog.ScCalls.TYPE + " = " + ScCallLog.ScCalls.MISSED_TYPE, null, null);
    }

    protected void onProgressUpdate(Void... progress) {}

    @Override
    protected void onCancelled(Cursor result) {
        if (result != null)
            result.close();
    }

    @Override
    protected void onPostExecute(Cursor result) {
        int count = 0;
        if (result != null) {
            count = result.getCount();
            result.close();
        }
        if (count <= 0)
            return;

        NotificationManager notificationManager = (NotificationManager)mCtx.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent showCallLog = new Intent();
        showCallLog.setAction(Intent.ACTION_VIEW);
        showCallLog.setType(ScCallLog.ScCalls.CONTENT_TYPE);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(mCtx, 0, showCallLog, 0);

        if (mNotifyLargeIcon == null)
            mNotifyLargeIcon = BitmapFactory.decodeResource(mCtx.getResources(), R.drawable.ic_launcher_sp);

        int callType = mInsertValues.getAsInteger(ScCallLog.ScCalls.TYPE);
        // Show the callers name only if the current call was missed. Otherwise just indicate the log
        // contains one or more missed calls.
        final int ico = R.drawable.stat_notify_missed_call;
        Notification notification = new NotificationCompat.Builder(mCtx)
                .setContentTitle(mCtx.getResources().getQuantityString(R.plurals.number_missed_calls, count, count))
                .setContentText(count == 1 && callType == ScCallLog.ScCalls.MISSED_TYPE? mCall.getNameFromAB() : null)
                .setSmallIcon(ico)
                .setLargeIcon(mNotifyLargeIcon)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(MISSED_CALL_NOTIFICATION_ID, notification);

    }

    private long getContactIdFromPhoneNumber(Context context, String number) {
        final Uri lookupUri;
        final String phoneLookUpId;

        String selection = null;
        String[] projection = null;

        if (PhoneNumberHelper.isUriNumber(number)) {
            // number is a SIP address: use customized lookup
            lookupUri = ContactsContract.Data.CONTENT_URI;
            phoneLookUpId = ContactsContract.Data.CONTACT_ID;
            selection = CallState.createSipLookupClause(number);
            projection = new String[] {ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
        }
        else {
            lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            phoneLookUpId = PhoneLookup._ID;
            projection = new String[] {phoneLookUpId};
        }
        final Cursor cursor = context.getContentResolver().query(lookupUri, projection, selection, null, null);
        if (cursor == null) {
            return NO_CONTACT_FOUND;
        }
        try {
            int idx = cursor.getColumnIndex(phoneLookUpId);
            if (idx == -1)
                return NO_CONTACT_FOUND;
            if (cursor.moveToFirst()) {
                return cursor.getLong(idx);
            }
            else {
                return NO_CONTACT_FOUND;
            }
        } finally {
            cursor.close();
        }
    }

    private void unDemote(long id) {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        PhoneFavoritesTileAdapter.updatePinnedForContact(mCtx, id, PhoneFavoritesTileAdapter.UNPINNED, ops);
        try {
            mCtx.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (OperationApplicationException e) {
            e.printStackTrace();
        }
    }
}
