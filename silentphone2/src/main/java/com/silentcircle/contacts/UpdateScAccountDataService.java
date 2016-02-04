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
package com.silentcircle.contacts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.silentcircle.accounts.AccountConstants;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.ArrayList;

public class UpdateScAccountDataService extends IntentService {

    private static final String TAG = UpdateScAccountDataService.class.getSimpleName();

    private static final Uri mDataUri = Data.CONTENT_URI;
    private static final String SC_PHONE_CONTENT_TYPE = "vnd.android.cursor.item/com.silentcircle.phone";
    private static final String SC_MSG_CONTENT_TYPE = "vnd.android.cursor.item/com.silentcircle.message";

    private String SC_DOMAIN;
    private String SC_CALL;
    private String SC_TEXT;

    private Account mSelectedAccount;
    private final ArrayList<ContentProviderOperation> mOperations = new ArrayList<>();
    private ContentResolver mResolver;

    private final static String[] mAllData = {
            Data._ID, //.....................0
            Data.CONTACT_ID, //..............1
            Data.MIMETYPE, //................2
            Data.DISPLAY_NAME_PRIMARY, //....3
            Data.DATA1, //...................4
            Data.RAW_CONTACT_ID, //..........5
            Data.SYNC4 //....................6
    };

    private final static int _ID  =          0;
    private final static int CONTACT_ID  =   1;
    private final static int MIME_TYPE =     2;
    private final static int DISPLAY_NAME =  3;
    private final static int DATA =          4;
    private final static int RAW_CONTACT =   5;
    private final static int SYNC4 =         6;

    // Check and compare these mime types when updating existing SC account entries
    private final static String [] mScUpdateMime= {
            SipAddress.CONTENT_ITEM_TYPE,
            SC_PHONE_CONTENT_TYPE,
            SC_MSG_CONTENT_TYPE
    };

    // Holds data for SC account entries that need updating
    private static class ScContactDataDelta {
        public String scPhoneSip;
        public String sipAddress;
        public long rawContactId;
        public long contactId;
        public long dataId;
        public long scPhoneDataId;
        public long scMsgDataId;
        public boolean hasSipAddress;
    }

    // Query to find all data records that belong to SC account entries
    private final static String subQuery = Data.MIMETYPE + "='vnd.android.cursor.item/com.silentcircle.phone' OR " +
                    Data.MIMETYPE + "='vnd.android.cursor.item/com.silentcircle.message'";

    public UpdateScAccountDataService() {
        super(UpdateScAccountDataService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(intent == null) {
            return;
        }
        if(TextUtils.isEmpty(intent.getAction())) {
            return;
        }
        SC_DOMAIN = getString(R.string.sc_sip_domain_0);
        SC_CALL = getResources().getString(R.string.call_other);
        SC_TEXT = getResources().getString(R.string.chat);

        mResolver = getContentResolver();
        if (!checkAndCreateAccount())
            return;

        mOperations.clear();
        processContacts();
    }

    private boolean checkAndCreateAccount() {
        // Get account data from system
        Account[] accounts = AccountManager.get(this).getAccountsByType(AccountConstants.ACCOUNT_TYPE);
        if (accounts.length == 0) {
            Log.w(TAG, "No SilentCircle account available - no update of contacts");
            return false;
        }
        mSelectedAccount = accounts[0];
        return true;
    }

    private void processContacts() {
        Cursor cursor = mResolver.query(mDataUri, mAllData, subQuery, null, Data.CONTACT_ID);
        if (cursor != null && cursor.getCount() > 0) {
//            DatabaseUtils.dumpCursor(cursor);
            updateExisting(cursor);
        }
        addMissing(cursor);
        if (cursor != null)
            cursor.close();

        // We found at least on entry to process, run them as batch.
        if (mOperations.size() > 0) {
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, mOperations);
            } catch (Exception e) {
                // Display warning
                Context ctx = getApplicationContext();
                CharSequence txt = getString(R.string.contactCreationFailure);
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(ctx, txt, duration);
                toast.show();

                // Log exception
                Log.e(TAG, "Exception encountered while inserting contact: " + e);
            }
        }
    }

    private void addMissing(final Cursor cursor) {
        if (cursor == null)
            return;
        cursor.moveToPosition(-1);

        // Create query to get all contact data that have a SC SIP address but not yet
        // a SC account record:
        // contact_id NOT IN (a,b,c,...) AND (mime_type='vnd.android.cursor.item/sip_address' AND data1 LIKE '%@sip.silentcircle.net')
        //
        // As a result we get all display names and SC SIP addresses
        StringBuilder inIds = new StringBuilder();
        if (cursor.getCount() > 0) {
            ArrayList<Long> duplicates = new ArrayList<>();
            inIds.append(Data.CONTACT_ID).append(" NOT IN (");
            while (cursor.moveToNext()) {
                long id = cursor.getLong(CONTACT_ID);
                if (duplicates.contains(id))
                    continue;
                inIds.append(id).append(',');
                duplicates.add(id);
            }
            int length = inIds.length();
            inIds.deleteCharAt(length - 1);
            inIds.append(") AND ");
        }
        inIds.append('(').append(Data.MIMETYPE)
                .append("='").append(SipAddress.CONTENT_ITEM_TYPE).append("' AND ")
                .append(Data.DATA1)
                .append(" LIKE '%").append(SC_DOMAIN).append("')");

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Select for missing with SIP addresses: " + inIds.toString());
        Cursor cursor1 = mResolver.query(mDataUri, mAllData, inIds.toString(), null, null);

        if (ConfigurationUtilities.mTrace) Log.d(TAG,"Found records: " + (cursor1 == null ? "None" : cursor1.getCount()));
        if (cursor1 != null) {
//            DatabaseUtils.dumpCursor(cursor1);
            ArrayList<Long> duplicates = new ArrayList<>();
            while (cursor1.moveToNext()) {
                long id = cursor1.getLong(CONTACT_ID);
                if (duplicates.contains(id))
                    continue;
                duplicates.add(id);
                // Don't add SC raw contact and data if we already have it. We my get triggered before
                // contacts aggregation took place and thus we could get wrong results on the first query
                if (!scSipExists(cursor1.getString(DATA)))
                    addScContactData(cursor1.getString(DISPLAY_NAME), cursor1.getString(DATA), cursor1.getLong(_ID));
            }
            cursor1.close();
        }
        cursor.moveToPosition(-1);
    }

    // Update existing SC account entries
    private void updateExisting(final Cursor cursor) {
        cursor.moveToPosition(-1);

        StringBuilder inIds = new StringBuilder();

        // Create query to get all contact data that have a SC raw contact, however may have deleted
        // the SIP address data:
        //
        // contact_id IN (a,b,c,...) AND (mime_type='vnd.android.cursor.item/sip_address' OR (subQuery)')
        //
        // As a result we get all display names and SC SIP addresses
        ArrayList<Long> duplicates = new ArrayList<>();
        inIds.append(Data.CONTACT_ID).append(" IN (");
        while (cursor.moveToNext()) {
            long id = cursor.getLong(CONTACT_ID);
            if (duplicates.contains(id))
                continue;
            inIds.append(id).append(',');
            duplicates.add(id);
        }
        cursor.moveToPosition(-1);

        int length = inIds.length();
        inIds.deleteCharAt(length - 1);
        inIds.append(") AND (").append(Data.MIMETYPE)
                .append("='").append(SipAddress.CONTENT_ITEM_TYPE).append("'")
                .append(" OR ").append(subQuery).append(")");

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Select for update check: " + inIds.toString());
        Cursor cursor1 = mResolver.query(mDataUri, mAllData, inIds.toString(), null, Data.CONTACT_ID);
        if (ConfigurationUtilities.mTrace) Log.d(TAG,"Found records: " + (cursor1 == null ? "None" : cursor1.getCount()));
        if (cursor1 == null) {
            return;
        }
//        DatabaseUtils.dumpCursor(cursor1);

        ArrayList<ScContactDataDelta> updateData = new ArrayList<>();
        ScContactDataDelta scd = null;

        // Cursor data sorted by CONTACT_ID
        while (cursor1.moveToNext()) {
            String mimeType = cursor1.getString(MIME_TYPE);
            if (!Utilities.isAnyOf(mimeType, mScUpdateMime))
                continue;

            long contactId = cursor1.getLong(CONTACT_ID);

            if (scd == null || scd.contactId != contactId) {
                scd = new ScContactDataDelta();
                scd.contactId = contactId;
            }

            if (SC_PHONE_CONTENT_TYPE.equals(mimeType)) {
                scd.scPhoneSip = cursor1.getString(DATA);
                scd.scPhoneDataId = cursor1.getLong(_ID);
                scd.rawContactId = cursor1.getLong(RAW_CONTACT);
            }
            // If this CONTACT_ID has a SIP address data record use the data, otherwise
            // skip the contact (see if statement below)
            else if (SipAddress.CONTENT_ITEM_TYPE.equals(mimeType)) {
                scd.hasSipAddress = true;
                scd.dataId = cursor1.getLong(_ID);      // The SC raw contact data mirrors this original data
                scd.sipAddress = cursor1.getString(DATA);
                if (!scd.sipAddress.startsWith("sip:"))
                    scd.sipAddress = "sip:" + scd.sipAddress;
            }
            else {
                scd.scMsgDataId = cursor1.getLong(_ID);
            }
            if (!scd.hasSipAddress || TextUtils.isEmpty(scd.scPhoneSip) || scd.scPhoneSip.equals(scd.sipAddress))
                continue;

            if (!updateData.contains(scd)) {
                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "scData: " + scd.scPhoneSip + ", " + scd.scPhoneDataId + ", " + scd.scMsgDataId);
                    Log.d(TAG, "other data: " + scd.sipAddress + ", " + scd.rawContactId + ", " + scd.contactId);
                }
                updateData.add(scd);
            }
        }
        cursor1.close();
        if (updateData.size() > 0)
            prepareUpdateOps(updateData);
    }

    String existsQuery = Data.MIMETYPE + "='vnd.android.cursor.item/com.silentcircle.phone' AND " +
            Data.DATA1 + "=?";

    private boolean scSipExists(String sipAddress) {
        if (!sipAddress.startsWith("sip:"))
            sipAddress = "sip:" + sipAddress;

        Cursor cursor = mResolver.query(mDataUri, new String[] {Data.CONTACT_ID}, existsQuery, new String[] {sipAddress}, null, null);
        if (cursor != null) {
            DatabaseUtils.dumpCursor(cursor);
            boolean found = cursor.getCount() > 0;
            cursor.close();
            return found;
        }
        return false;
    }


    private void prepareUpdateOps(ArrayList<ScContactDataDelta> updateData) {
        for (ScContactDataDelta scd : updateData) {
            if (TextUtils.isEmpty(scd.sipAddress)) {
                deleteScContactData(scd);
                continue;
            }
            int delimiterIndex = scd.sipAddress.indexOf('@');
            // Not a valid SIP address anymore
            if (delimiterIndex < 0) {
                deleteScContactData(scd);
                continue;
            }
            // Does not belong to SC domain anymore
            String domain = scd.sipAddress.substring(delimiterIndex);
            if (!SC_DOMAIN.equals(domain)) {
                deleteScContactData(scd);
                continue;
            }
            String scName = Utilities.removeSipParts(scd.scPhoneSip);
            String sipName = Utilities.removeSipParts(scd.sipAddress);

            // Has no name part anymore
            if (TextUtils.isEmpty(sipName)) {
                deleteScContactData(scd);
                continue;
            }
            if (!scName.equals(sipName)) {
                updateScContactData(scd);
            }
        }
    }

    private void deleteScContactData(ScContactDataDelta scd) {
        Uri deleteUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, scd.rawContactId);
        mOperations.add(ContentProviderOperation.newDelete(deleteUri).build());
    }

    private void updateScContactData(ScContactDataDelta scd) {
        final String scName = Utilities.removeSipParts(scd.sipAddress);

        String phoneName = scName;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            phoneName = scName + " (" + SC_CALL + ")";
        }
        Uri updateUri = ContentUris.withAppendedId(mDataUri, scd.scPhoneDataId);
        mOperations.add(ContentProviderOperation.newUpdate(updateUri)
                .withValue(Data.DATA1, scd.sipAddress)
                .withValue(Data.DATA3, phoneName)
                .withValue(Data.SYNC4, scd.dataId)      // The SC record mirrors content of this original Data record
                .build());

        String msgName = scName;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            msgName = scName + " (" + SC_TEXT + ")";
        }
        updateUri = ContentUris.withAppendedId(mDataUri, scd.scMsgDataId);
        mOperations.add(ContentProviderOperation.newUpdate(updateUri)
                .withValue(Data.DATA1, scd.sipAddress)
                .withValue(Data.DATA3, msgName)
                .withValue(Data.SYNC4, scd.dataId)      // The SC record mirrors content of this original Data record
                .build());
    }

    private void addScContactData(String displayName, String sipAddress, long dataId) {
        final String scName = Utilities.removeSipParts(sipAddress);
        if (!sipAddress.startsWith("sip:"))
            sipAddress = "sip:" + sipAddress;

        // Prepare contact creation request
        //
        // Note: We use RawContacts because this data must be associated with a particular account.
        //       The system will aggregate this with any other data for this contact and create a
        //       corresponding entry in the ContactsContract.Contacts provider for us.

        int rawContactInsertIndex = mOperations.size();
        mOperations.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, mSelectedAccount.type)
                .withValue(RawContacts.ACCOUNT_NAME, mSelectedAccount.name)
                .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT)
                .build());

        mOperations.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, displayName)
                .build());

        String phoneName = scName;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            phoneName = scName + " (" + SC_CALL + ")";
        }
        mOperations.add(ContentProviderOperation.newInsert(mDataUri)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(Data.MIMETYPE, SC_PHONE_CONTENT_TYPE)
                .withValue(Data.DATA1, sipAddress)
                .withValue(Data.DATA2, "Silent Circle")
                .withValue(Data.DATA3, phoneName)
                .withValue(Data.SYNC4, dataId)      // The SC record mirrors content of this original Data record
                .build());

        String msgName = scName;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            msgName = scName + " (" + SC_TEXT + ")";
        }
        mOperations.add(ContentProviderOperation.newInsert(mDataUri)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(Data.MIMETYPE, SC_MSG_CONTENT_TYPE)
                .withValue(Data.DATA1, sipAddress)
                .withValue(Data.DATA2, "Silent Circle")
                .withValue(Data.DATA3, msgName)
                .withValue(Data.SYNC4, dataId)      // The SC record mirrors content of this original Data record
                .build());
    }
}
