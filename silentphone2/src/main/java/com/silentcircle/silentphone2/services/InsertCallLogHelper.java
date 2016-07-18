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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.silentcontacts2.ScCallLog;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.list.ShortcutCardsAdapter;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Counts the number of missed new calls in the call log and creates a Notification if
 * the number is greater zero.
 *
 * Created by werner on 11.10.14.
 */
public class InsertCallLogHelper extends AsyncTask<Uri, Void, Cursor> {

    @SuppressWarnings("unused")
    private static final String TAG = "InsertCallLogHelper";
//    private static final long NO_CONTACT_FOUND = -1;

    public static final int MISSED_CALL_NOTIFICATION_ID = 47120815;
    private static Bitmap mNotifyLargeIcon;


    private Context mCtx;
    private CallState mCall;
    private ContentValues mInsertValues;

    InsertCallLogHelper(Context ctx, CallState call) {
        mCtx = ctx;
        mCall = call;
        mInsertValues = new android.content.ContentValues(10);
    }

    public static void removeMissedCallNotifications(Context ctx) {
        if (ctx != null) {
            NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(MISSED_CALL_NOTIFICATION_ID);
        }
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

        if (!prepareCallLog(mCall))
            return null;

        resolver.insert(uri[0], mInsertValues);
        int removed = removeExpiredEntries();
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Removed " + removed + " old/expired call log entries");

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
        ContentResolver resolver = mCtx.getContentResolver();
        resolver.notifyChange(ShortcutCardsAdapter.AUTHORITY_URI, null, false);

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

    @WorkerThread
    private boolean prepareCallLog(CallState call) {

        long duration = 0;
        String caller;

        if (call.uiStartTime != 0) {
            duration = System.currentTimeMillis() - call.uiStartTime;
            call.uiStartTime = 0;
        }

        // Here we can use the same values for CallLog and SCCallLog: kept in sync
        int iType = call.iIsIncoming ? ScCallLog.ScCalls.INCOMING_TYPE : ScCallLog.ScCalls.OUTGOING_TYPE;

        if (!call.mAnsweredElsewhere && duration == 0 && iType != ScCallLog.ScCalls.OUTGOING_TYPE) {
            iType = ScCallLog.ScCalls.MISSED_TYPE;
        }

        // If mDisplayNameFromSip is set then, on an incoming call, we have no contact
        // entry of the caller, thus 'fillFromContacts' sets this buffer with the peername
        // it got from the SIP stack. Usually the peername of PAI.
        // The buffer may also be empty in case of an outgoing call if the SIP stack reports
        // and error.

        // Depending of the call mode:
        // - on outgoing call bufPeer is set to the display name first to have something nice
        //   to show to the user, on eCalling replaces it with the data we get from SIP during
        //   the callback, usually the UUID SIP URI.
        // - on incoming SC calls it's the PAI data (UUID) that we use to lookup other data
        //   of the caller if available. On incoming OCA calls this is the caller's PSTN number
        // - on incoming OCA calls the bufPeer contains the From header data which is usually
        //   the caller's phone number or SIP URI
        //
        // On outgoing calls bufDialed always contains the string we use to create the call
        // command, the UUID of the called party
        if (call.iIsIncoming && !call.isOcaCall) {
            caller = call.mAssertedName.toString();
        }
        else {
            if (call.bufPeer.getLen() > 0)
                caller = call.bufPeer.toString();
            else if (call.bufDialed.getLen() > 0)
                caller = call.bufDialed.toString();
            else
                caller = "";                    // must not be here
        }

        if (Utilities.isUriNumber(caller)) {
            if (caller.startsWith("sip:")) {
                mInsertValues.put(ScCallLog.ScCalls.NUMBER, caller);
            }
            else {
                mInsertValues.put(ScCallLog.ScCalls.NUMBER, "sip:" + caller);
            }
        }
        else {
            if (PhoneNumberUtils.isGlobalPhoneNumber(caller))
                mInsertValues.put(ScCallLog.ScCalls.NUMBER, caller);
            else
                mInsertValues.put(ScCallLog.ScCalls.NUMBER, "sip:" + caller + mCtx.getString(R.string.sc_sip_domain_0));
        }
        mInsertValues.put(ScCallLog.ScCalls.TYPE, iType);
        mInsertValues.put(ScCallLog.ScCalls.DATE, System.currentTimeMillis() - duration);
        mInsertValues.put(ScCallLog.ScCalls.DURATION, duration / 1000);
        mInsertValues.put(ScCallLog.ScCalls.NEW, true);
        if (iType == ScCallLog.ScCalls.MISSED_TYPE)
            mInsertValues.put(ScCallLog.ScCalls.IS_READ, 0);

        // SIP error handling on outgoing calls cannot provide enough information, thus try
        // construct some meaningful data here and create an asserted name URI. Construct
        // an asserted name in an error case only if it's not an OCA call
        final String assertedName;
        if (!call.isOcaCall && call.hasSipErrorMessage && call.mAssertedName.getLen() == 0 && call.bufDialed.getLen() != 0) {
            assertedName = "sip:" + call.bufDialed.toString() + mCtx.getString(R.string.sc_sip_domain_0);
        }
        else {
            final String[] fields = Utilities.splitFields(call.mAssertedName.toString(), ";");
            assertedName = fields != null ? fields[0] : null;

            // If call reached ringing state then handle a SIP error as "soft" error, usually call decline
            // of user not answering. In this case we have user data from SIP, like asserted id etc.
            if (call.initialStates == TiviPhoneService.CT_cb_msg.eRinging)
                call.hasSipErrorMessage = false;
        }
        if (!TextUtils.isEmpty(assertedName)) {
            mInsertValues.put(ScCallLog.ScCalls.SC_OPTION_TEXT1, assertedName);
        }
        final String displayName = call.mDisplayNameForCallLog.toString();
        final boolean displayNameAvailable = !TextUtils.isEmpty(displayName);
        if (displayNameAvailable) {
            mInsertValues.put(ScCallLog.ScCalls.SC_OPTION_TEXT2, displayName);
        }

        // This is a call with an error condition where we only know the UUID, have no
        // SIP display name (peername): get the default display name for it.
        // OCA calls to a illegal/not permitted number will not return an error, the
        // OCA gateway plays an announcement. In case the OCA gateway returns an error
        // and we have no SIP display name then don't add a call log yet.
        if (call.hasSipErrorMessage && !displayNameAvailable) {
            // get the uuid from dialed buf
            int[] errorCode = new int[1];
            final byte[] userData = AxoMessaging.getUserInfo(call.bufDialed.toString(), null, errorCode);

            String displayNameUserInfo = null;
            if (userData != null) {
                try {
                    JSONObject data = new JSONObject(new String(userData));
                    displayNameUserInfo = data.has("display_name") ? data.getString("display_name") : null;
                } catch (JSONException ignore) { }
            }
            else {
                Log.w(TAG, "No user info found for: '" + call.bufDialed.toString() + "'");
                return false;
            }
            if (!TextUtils.isEmpty(displayNameUserInfo)) {
                mInsertValues.put(ScCallLog.ScCalls.SC_OPTION_TEXT2, displayNameUserInfo);
            }
        }
        return true;
    }
}
