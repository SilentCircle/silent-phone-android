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

package com.silentcircle.contacts.interactions;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.silentcircle.contacts.vcard.ExportVCardActivity;
import com.silentcircle.contacts.vcard.ImportVCardActivity;
import com.silentcircle.silentphone2.R;

// import com.android.contacts.model.account.AccountWithDataSet;
// import com.android.contacts.editor.SelectAccountDialogFragment;
// import AccountTypeManager;
//import com.android.contacts.model.account.AccountWithDataSet;
//import com.android.contacts.util.AccountSelectionUtil;
//import com.android.contacts.util.AccountsListAdapter.AccountListFilter;


/**
 * An dialog invoked to import/export contacts.
 */
public class ImportExportDialogFragment extends DialogFragment {
    public static final String TAG = "ImportExportDialogFragment";

    private static final String KEY_RES_ID = "resourceId";
    private static final String ARG_CONTACTS_ARE_AVAILABLE = "CONTACTS_ARE_AVAILABLE";

//    private final String[] LOOKUP_PROJECTION = new String[] {
//            Contacts.LOOKUP_KEY
//    };

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager, boolean contactsAreAvailable) {
        final ImportExportDialogFragment fragment = new ImportExportDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_CONTACTS_ARE_AVAILABLE, contactsAreAvailable);
        fragment.setArguments(args);
        fragment.show(fragmentManager, ImportExportDialogFragment.TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        final Resources res = getActivity().getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final boolean contactsAreAvailable = getArguments().getBoolean(ARG_CONTACTS_ARE_AVAILABLE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getActivity(), R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false));

                final int resId = getItem(position);
                result.setText(resId);
                return result;
            }
        };

//        if (TelephonyManager.getDefault().hasIccCard()
//                && res.getBoolean(R.bool.config_allow_sim_import)) {
//            adapter.add(R.string.import_from_sim);
//        }
        if (res.getBoolean(R.bool.config_allow_import_from_sdcard)) {
            adapter.add(R.string.import_from_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_export_to_sdcard)) {
            if (contactsAreAvailable) {
                adapter.add(R.string.export_to_sdcard);
            }
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            if (contactsAreAvailable) {
                adapter.add(R.string.share_visible_contacts);
            }
        }

        final DialogInterface.OnClickListener clickListener =  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean dismissDialog;
                final int resId = adapter.getItem(which);
                switch (resId) {
//                    case R.string.import_from_sim:
                    case R.string.import_from_sdcard: {
                        dismissDialog = handleImportRequest(resId);
                        break;
                    }
                    case R.string.export_to_sdcard: {
                        dismissDialog = true;
                        Intent exportIntent = new Intent(getActivity(), ExportVCardActivity.class);
                        getActivity().startActivity(exportIntent);
                        break;
                    }
//                    case R.string.share_visible_contacts: {
//                        dismissDialog = true;
//                        Log.e(TAG, "Share contects not yet available");
//                        doShareVisibleContacts();
//                        break;
//                    }
                    default: {
                        dismissDialog = true;
                        Log.e(TAG, "Unexpected resource: "
                                + getActivity().getResources().getResourceEntryName(resId));
                    }
                }
                if (dismissDialog) {
                    dialog.dismiss();
                }
            }
        };
        return new AlertDialog.Builder(getActivity())
                .setTitle(contactsAreAvailable
                        ? R.string.dialog_import_export
                        : R.string.dialog_import)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }

// TODO - share one RawContact??    private void doShareVisibleContacts() {
//        // TODO move the query into a loader and do this in a background thread
//        final Cursor cursor = getActivity().getContentResolver().query(RawContacts.CONTENT_URI,
//                LOOKUP_PROJECTION, Contacts.IN_VISIBLE_GROUP + "!=0", null, null);
//        if (cursor != null) {
//            try {
//                if (!cursor.moveToFirst()) {
//                    Toast.makeText(getActivity(), R.string.share_error, Toast.LENGTH_SHORT).show();
//                    return;
//                }
//
//                StringBuilder uriListBuilder = new StringBuilder();
//                int index = 0;
//                do {
//                    if (index != 0)
//                        uriListBuilder.append(':');
//                    uriListBuilder.append(cursor.getString(0));
//                    index++;
//                } while (cursor.moveToNext());
//                Uri uri = Uri.withAppendedPath(
//                        Contacts.CONTENT_MULTI_VCARD_URI,
//                        Uri.encode(uriListBuilder.toString()));
//
//                final Intent intent = new Intent(Intent.ACTION_SEND);
//                intent.setType(RawContacts.CONTENT_VCARD_TYPE);
//                intent.putExtra(Intent.EXTRA_STREAM, uri);
//                getActivity().startActivity(intent);
//            } finally {
//                cursor.close();
//            }
//        }
//    }
//
    /**
     * Handle "import from SIM" and "import from SD".
     *
     * @return {@code true} if the dialog show be closed.  {@code false} otherwise.
     */
    private boolean handleImportRequest(int resId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        switch (resId) {
//        case R.string.import_from_sim: {
//            doImportFromSim(getActivity());
//            break;
//        }
        case R.string.import_from_sdcard: {
            doImportFromSdCard(getActivity());
            break;
        }
        }
        return true; // Close the dialog.
    }

    public static void doImportFromSdCard(Context context) {
        Intent importIntent = new Intent(context, ImportVCardActivity.class);

//        if (mVCardShare) {
//            importIntent.setAction(Intent.ACTION_VIEW);
//            importIntent.setData(mPath);
//        }
//        mVCardShare = false;
//        mPath = null;
        context.startActivity(importIntent);
    }
}
