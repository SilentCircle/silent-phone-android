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

package com.silentcircle.contacts.vcard;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.vcard.VCardEntry;

/**
 * A simple ExportImport listener used during contact data migration.
 * This class does not set-up any notifications.
 */
public class MigrationExportListener implements VCardImportExportListener {

    static final String DEFAULT_NOTIFICATION_TAG = "MigrationExportListener";
    private final Activity mContext;

    public MigrationExportListener(Activity activity) {
        mContext = activity;
    }

    @Override
    public void onImportProcessed(ImportRequest request, int jobId, int sequence) {
        if (ConfigurationUtilities.mTrace) Log.d(DEFAULT_NOTIFICATION_TAG, "Migration ImportProcessed");
    }

    @Override
    public void onImportParsed(ImportRequest request, int jobId, VCardEntry entry, int currentCount, int totalCount) {
    }

    @Override
    public void onImportFinished(ImportRequest request, int jobId, Uri createdUri) {
        if (ConfigurationUtilities.mTrace) Log.d(DEFAULT_NOTIFICATION_TAG, "Migration ImportFinished: " + request.displayName);
    }

    @Override
    public void onImportFailed(ImportRequest request) {
        if (ConfigurationUtilities.mTrace) Log.d(DEFAULT_NOTIFICATION_TAG, "Migration ImportFailed: " + request.displayName);
    }

    @Override
    public void onImportCanceled(ImportRequest request, int jobId) {
        if (ConfigurationUtilities.mTrace)Log.d(DEFAULT_NOTIFICATION_TAG, "Migration ImportCanceled: " + request.displayName);
    }

    @Override
    public void onExportProcessed(ExportRequest request, int jobId) {
        if (ConfigurationUtilities.mTrace) Log.d(DEFAULT_NOTIFICATION_TAG, "Migration ExportProcessed");
    }

    @Override
    public void onExportFailed(ExportRequest request) {
        if (ConfigurationUtilities.mTrace) Log.d(DEFAULT_NOTIFICATION_TAG, "Nigration ExportFailed");
    }

    @Override
    public void onCancelRequest(CancelRequest request, int type) {
        if (ConfigurationUtilities.mTrace) Log.d(DEFAULT_NOTIFICATION_TAG, "Migration CancelRequest: " + request.displayName);
    }

    @Override
    public void onComplete() {
        mContext.finish();
    }
}
