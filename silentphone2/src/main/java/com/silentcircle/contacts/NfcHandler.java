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
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.util.Log;

import com.silentcircle.contacts.detail.ContactDetailFragment;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
  * This class implements sharing the currently displayed
  * contact to another device using NFC. NFC sharing is only
  * enabled when the activity is in the foreground and resumed.
  * When an NFC link is established, {@link #createMessage}
  * will be called to create the data to be sent over the link,
  * which is a vCard in this case.
  */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcHandler implements NfcAdapter.CreateNdefMessageCallback {

    private static final String TAG = "ContactNfcHandler";
    private final Uri mLookupUri;
    private final Activity mContext;
    private ContactDetailFragment mContactFragment;

    public static void register(Activity activity, Uri lookupUri) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity.getApplicationContext());
        if (adapter == null) {
            return;  // NFC not available on this device
        }
        adapter.setNdefPushMessageCallback(new NfcHandler(lookupUri, activity), activity);
    }

    public static void register(Activity activity, ContactDetailFragment contactFragment) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity.getApplicationContext());
        if (adapter == null) {
            return;  // NFC not available on this device
        }
        adapter.setNdefPushMessageCallback(new NfcHandler(contactFragment), activity);
    }

    public NfcHandler(ContactDetailFragment contactFragment) {
        mContext = null;
        mContactFragment = contactFragment;
        mLookupUri = null;
    }

    public NfcHandler(Uri lookupUri, Activity ctx) {
        mLookupUri = lookupUri;
        mContext = ctx;
        mContactFragment = null;
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        // Get the current contact URI
        Uri contactUri = mLookupUri != null? mLookupUri : mContactFragment.getUri();
        ContentResolver resolver = mContext != null ? mContext.getContentResolver() : mContactFragment.getActivity().getContentResolver();
        if (contactUri != null) {
            long rawContactId = ContentUris.parseId(contactUri);
            Uri shareUri = ContentUris.withAppendedId(RawContacts.CONTENT_VCARD_URI, rawContactId);
            shareUri = shareUri.buildUpon().appendQueryParameter(RawContacts.QUERY_PARAMETER_VCARD_NO_PHOTO, "true").build();

            ByteArrayOutputStream ndefBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int r;
            try {
                InputStream vcardInputStream = resolver.openInputStream(shareUri);
                if (vcardInputStream == null)
                    return null;
                while ((r = vcardInputStream.read(buffer)) > 0) {
                    ndefBytes.write(buffer, 0, r);
                }
                NdefRecord record = NdefRecord.createMime(RawContacts.CONTENT_VCARD_TYPE, ndefBytes.toByteArray());
                return new NdefMessage(record);
            }
            catch (Exception e) {
                Log.e(TAG, "Exception creating vcard.", e);
                return null;
            }
        }
        else {
            Log.w(TAG, "No contact URI to share.");
            return null;
        }
    }
}
