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

package com.silentcircle.contacts.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.widget.Toast;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.contacts.utils.NotifyingAsyncQueryHandler;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.Intents;
import com.silentcircle.silentcontacts2.ScContactsContract.PhoneLookup;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

/**
 * Handle several edge cases around showing or possibly creating contacts in
 * connected with a specific E-mail address or phone number. Will search based
 * on incoming {@link android.content.Intent#getData()} as described by
 * {@link com.silentcircle.silentcontacts.ScContactsContract.Intents#SHOW_OR_CREATE_CONTACT}.
 * <ul>
 * <li>If no matching contacts found, will prompt user with dialog to add to a
 * contact, then will use {@link android.content.Intent#ACTION_INSERT_OR_EDIT} to let create new
 * contact or edit new data into an existing one.
 * <li>If one matching contact found, directly show {@link android.content.Intent#ACTION_VIEW}
 * that specific contact.
 * <li>If more than one matching found, show list of matching contacts using
 * {@link android.content.Intent#ACTION_SEARCH}.
 * </ul>
 */
public final class ScShowOrCreateActivity extends ActionBarActivity implements NotifyingAsyncQueryHandler.AsyncQueryListener {
    static final String TAG = "ScShowOrCreateActivity";

    static final String[] PHONES_PROJECTION = new String[] {
        PhoneLookup._ID,
    };

    static final String[] CONTACTS_PROJECTION = new String[] {
        Email.RAW_CONTACT_ID,
    };

    static final String[] IM_CONTACTS_PROJECTION = new String[] {
        Im.RAW_CONTACT_ID,
    };

    static final int CONTACT_ID_INDEX = 0;

    static final int CREATE_CONTACT_DIALOG = 1;

    static final int QUERY_TOKEN = 42;

    private NotifyingAsyncQueryHandler mQueryHandler;

    private Bundle mCreateExtras;
    private String mCreateDescrip;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(getBaseContext());
        boolean dbReady = db.isRegisteredWithKeyManager() && db.isReady();
        if (!dbReady) {
            final String msg = getString(R.string.app_name) + ": " + getString(R.string.locked);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Create handler if doesn't exist, otherwise cancel any running
        if (mQueryHandler == null) {
            mQueryHandler = new NotifyingAsyncQueryHandler(this, this);
        }
        else {
            mQueryHandler.cancelOperation(QUERY_TOKEN);
        }

        final Intent intent = getIntent();
        final Uri data = intent.getData();

        // Unpack scheme and target data from intent
        String scheme = null;
        String ssp = null;
        if (data != null) {
            scheme = data.getScheme();
            ssp = data.getSchemeSpecificPart();
        }

        // Build set of extras for possible use when creating contact
        mCreateExtras = new Bundle();
        Bundle originalExtras = intent.getExtras();
        if (originalExtras != null) {
            mCreateExtras.putAll(originalExtras);
        }

        // Read possible extra with specific title
        mCreateDescrip = intent.getStringExtra(Intents.EXTRA_CREATE_DESCRIPTION);
        if (mCreateDescrip == null) {
            mCreateDescrip = ssp;
        }

        // Handle specific query request
        if (Constants.SCHEME_MAILTO.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.EMAIL, ssp);

            Uri uri = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, Uri.encode(ssp));
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, CONTACTS_PROJECTION, null, null, null);
        }
        else if (Constants.SCHEME_TEL.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.PHONE, ssp);

            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, ssp);
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, PHONES_PROJECTION, null, null, null);
        }
        else if (Constants.SCHEME_IMTO.equals(scheme)) {
            String imAddress = data.getSchemeSpecificPart();
            int slashIdx = imAddress.indexOf('/');
            if (slashIdx > 0) {                         // skip protocol part
                imAddress = imAddress.substring(slashIdx+1);
            }
            mCreateExtras.putString(Intents.Insert.IM_HANDLE, imAddress);
            mCreateExtras.putInt(Intents.Insert.IM_PROTOCOL, ScContactsContract.CommonDataKinds.Im.PROTOCOL_SILENT);

            if (mCreateDescrip == ssp)          // Intent didn't contain a create description
                mCreateDescrip = imAddress;

            Uri uri = Uri.withAppendedPath(Im.CONTENT_LOOKUP_URI, imAddress);
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, IM_CONTACTS_PROJECTION, null, null, null);
        }
        else {
            Log.w(TAG, "Invalid intent:" + getIntent());
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mQueryHandler != null) {
            mQueryHandler.cancelOperation(QUERY_TOKEN);
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null) {
            // Bail when problem running query in background
            finish();
            return;
        }

        // Count contacts found by query
        int count = 0;
        long contactId = -1;
        try {
            count = cursor.getCount();
            if (count == 1 && cursor.moveToFirst()) {
                // Try reading ID if only one contact returned
                contactId = cursor.getLong(CONTACT_ID_INDEX);
            }
        } finally {
            cursor.close();
        }

        if (count == 1 && contactId != -1) {
            // If we only found one item, jump right to viewing it
            final Uri contactUri = ContentUris.withAppendedId (RawContacts.CONTENT_URI, contactId);
            final Intent viewIntent = new Intent(Intent.ACTION_VIEW, contactUri);
            startActivity(viewIntent);
            finish();

        } else if (count > 1) {
            // If more than one, show pick list
            Intent listIntent = new Intent(Intent.ACTION_SEARCH);
            if (!DialerActivity.useBpForwarder())
                listIntent.setComponent(new ComponentName(this, ScContactsMainActivity.class));
            else
                listIntent.setComponent(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactsMainActivityForwarder"));

            listIntent.putExtras(mCreateExtras);
            try {
                startActivity(listIntent);
            } catch (Exception ignored) {}
            finish();

        } else {
            // No matching contacts found
            showDialog(CREATE_CONTACT_DIALOG);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case CREATE_CONTACT_DIALOG:
                // Prompt user to insert or edit contact
                final Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                createIntent.putExtras(mCreateExtras);
                createIntent.setType(RawContacts.CONTENT_ITEM_TYPE);

                final CharSequence message = getResources().getString(R.string.add_contact_dlg_message_fmt, mCreateDescrip);

                return new AlertDialog.Builder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new IntentClickListener(this, createIntent))
                        .setNegativeButton(android.R.string.cancel, new IntentClickListener(this, null))
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                finish(); // Close the activity.
                            }
                        })
                        .create();
        }
        return super.onCreateDialog(id);
    }

    /**
     * Listener for {@link android.content.DialogInterface} that launches a given {@link android.content.Intent}
     * when clicked. When clicked, this also closes the parent using
     * {@link android.app.Activity#finish()}.
     */
    private static class IntentClickListener implements DialogInterface.OnClickListener {
        private Activity mParent;
        private Intent mIntent;

        /**
         * @param parent {@link android.app.Activity} to use for launching target.
         * @param intent Target {@link android.content.Intent} to launch when clicked.
         */
        public IntentClickListener(Activity parent, Intent intent) {
            mParent = parent;
            mIntent = intent;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (mIntent != null) {
                mParent.startActivity(mIntent);
            }
            mParent.finish();
        }
    }
}
