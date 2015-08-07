/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;

import com.silentcircle.silentphone2.util.CallState;

/**
 * Add a calllog entry into the native call log if the call was trigger via native contacts.
 *
 * Created by werner on 23.02.2015.
 */
public class InsertCallLogHelperNative extends AsyncTask<Uri, Void, Void> {

    @SuppressWarnings("unused")
    private static final String TAG = "InsertCallLogHelper";

    public static final int MISSED_CALL_NOTIFICATION_ID = 47120815;
    private static Bitmap mNotifyLargeIcon;


    private Context mCtx;
    private CallState mCall;
    private ContentValues mInsertValues;

    InsertCallLogHelperNative(Context ctx, CallState call, ContentValues values) {
        mCtx = ctx;
        mCall = call;

        // Only for Lollipop and up
//        values.put(Calls.NUMBER_PRESENTATION, Integer.valueOf(Calls.PRESENTATION_ALLOWED));
//        values.put(Calls.FEATURES, 0);
//        values.put(Calls.PHONE_ACCOUNT_COMPONENT_NAME, (String)null);
//        values.put(Calls.PHONE_ACCOUNT_ID, (String)null);

        mInsertValues = values;
    }

    public static void removeMissedCallNotification(Context ctx) {
        NotificationManager notificationManager = (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(MISSED_CALL_NOTIFICATION_ID);
    }

    @Override
    protected Void doInBackground(Uri... uri) {
        ContentResolver resolver = mCtx.getContentResolver();

        resolver.insert(uri[0], mInsertValues);
        return null;
    }

    protected void onProgressUpdate(Void... progress) {}

    @Override
    protected void onPostExecute(Void nothing) {}
}
