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
 * This  implementation is an edited version of original Android sources.
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.silentcircle.contacts.vcard;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.silentcircle.silentphone2.R;

/**
 * The Activity for canceling vCard import/export.
 */
public class CancelActivity extends ActionBarActivity implements ServiceConnection {
    private final String LOG_TAG = "VCardCancel";

    /* package */ final static String JOB_ID = "job_id";
    /* package */ final static String DISPLAY_NAME = "display_name";

    /**
     * Type of the process to be canceled. Only used for choosing appropriate title/message.
     * Must be {@link VCardService#TYPE_IMPORT} or {@link VCardService#TYPE_EXPORT}.
     */
    /* package */ final static String TYPE = "type";

    private class RequestCancelListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            bindService(new Intent(CancelActivity.this,
                    VCardService.class), CancelActivity.this, Context.BIND_AUTO_CREATE);
        }
    }

    private class CancelListener
            implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
        @Override
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private final CancelListener mCancelListener = new CancelListener();
    private int mJobId;
    private String mDisplayName;
    private int mType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Uri uri = getIntent().getData();
        mJobId = Integer.parseInt(uri.getQueryParameter(JOB_ID));
        mDisplayName = uri.getQueryParameter(DISPLAY_NAME);
        mType = Integer.parseInt(uri.getQueryParameter(TYPE));
        showDialog(R.id.dialog_cancel_confirmation);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
        case R.id.dialog_cancel_confirmation: {
            final String message;
            if (mType == VCardService.TYPE_IMPORT) {
                message = getString(R.string.cancel_import_confirmation_message, mDisplayName);
            } else {
                message = getString(R.string.cancel_export_confirmation_message, mDisplayName);
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, new RequestCancelListener())
                    .setOnCancelListener(mCancelListener)
                    .setNegativeButton(android.R.string.cancel, mCancelListener);
            return builder.create();
        }
        case R.id.dialog_cancel_failed:
            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.cancel_vcard_import_or_export_failed)
                    .setMessage(getString(R.string.fail_reason_unknown))
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);

            builder.setIconAttribute(android.R.attr.alertDialogIcon);

            return builder.create();
        default:
            Log.w(LOG_TAG, "Unknown dialog id: " + id);
            break;
        }
        return super.onCreateDialog(id, bundle);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        VCardService service = ((VCardService.MyBinder) binder).getService();

        try {
            final CancelRequest request = new CancelRequest(mJobId, mDisplayName);
            service.handleCancelRequest(request, null);
        } finally {
            unbindService(this);
        }

        finish();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // do nothing
    }
}
