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
 * limitations under the License
 */

package com.silentcircle.contacts.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.silentcircle.contacts.ScContactSaveService;
import com.silentcircle.contacts.editor.ContactEditorFragment;
import com.silentcircle.contacts.editor.ContactEditorFragment.SaveMode;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.utils.DialogManager;
import com.silentcircle.silentphone2.R;
// import com.android.contacts.model.account.AccountWithDataSet;


public class ScContactEditorActivity extends ActionBarActivity implements ScContactSaveService.Listener,
        DialogManager.DialogShowingViewActivity {

    private static final String TAG = "ContactEditorActivity";

    public static final String ACTION_JOIN_COMPLETED = "joinCompleted";
    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";

    /**
     * Boolean intent key that specifies that this activity should finish itself
     * (instead of launching a new view intent) after the editor changes have been
     * saved.
     */
    public static final String INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED = "finishActivityOnSaveCompleted";

    private ContactEditorFragment mFragment;
    private boolean mFinishActivityOnSaveCompleted;

    private DialogManager mDialogManager = new DialogManager(this);

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(getBaseContext());
        boolean dbReady = db.isRegisteredWithKeyManager() && db.isReady();
        if (!dbReady) {
            final String msg = getString(R.string.app_name) + ": " + getString(R.string.locked);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScContactSaveService.registerListener(this);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        // Determine whether or not this activity should be finished after the user is done
        // editing the contact or if this activity should launch another activity to view the
        // contact's details.
        mFinishActivityOnSaveCompleted = intent.getBooleanExtra(INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, false);

        // The only situation where action could be ACTION_JOIN_COMPLETED is if the
        // user joined the contact with another and closed the activity before
        // the save operation was completed.  The activity should remain closed then.
        if (ACTION_JOIN_COMPLETED.equals(action)) {
            finish();
            return;
        }

        if (ACTION_SAVE_COMPLETED.equals(action)) {
            finish();
            return;
        }

        setContentView(R.layout.contact_editor_activity);
        setSupportActionBar((Toolbar) findViewById(R.id.main_toolbar));

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Inflate a custom action bar that contains the "done" button for saving changes
            // to the contact
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View customActionBarView = inflater.inflate(R.layout.editor_custom_action_bar, null);
            View saveMenuItem = customActionBarView.findViewById(R.id.save_menu_item);
            saveMenuItem.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFragment.doSaveAction();
                }
            });
            // Show the custom action bar but hide the home icon and title
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
        }

        mFragment = (ContactEditorFragment)getFragmentManager().findFragmentById(R.id.contact_editor_fragment);
        mFragment.setListener(mFragmentListener);
        Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData() : null;
        mFragment.load(action, uri, getIntent().getExtras());
    }
    @Override
    protected void onDestroy() {
        ScContactSaveService.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public void onServiceCompleted(Intent callbackIntent) {
        onNewIntent(callbackIntent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mFragment == null) {
            return;
        }

        String action = intent.getAction();
        switch (action) {
            case Intent.ACTION_EDIT:
                mFragment.setIntentExtras(intent.getExtras());
                break;
            case ACTION_SAVE_COMPLETED:
                mFragment.onSaveCompleted(true,
                        intent.getIntExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.CLOSE),
                        intent.getBooleanExtra(ScContactSaveService.EXTRA_SAVE_SUCCEEDED, false),
                        intent.getData());
                break;
            case ACTION_JOIN_COMPLETED:
                mFragment.onJoinCompleted(intent.getData());
                break;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, args);

        // Nobody knows about the Dialog
        Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
        return null;
    }

    @Override
    public void onBackPressed() {
        mFragment.save(SaveMode.CLOSE);
    }

    private final ContactEditorFragment.Listener mFragmentListener = new ContactEditorFragment.Listener() {
        @Override
        public void onReverted() {
            finish();
        }

        @Override
        public void onSaveFinished(Intent resultIntent) {
            if (mFinishActivityOnSaveCompleted) {
                setResult(resultIntent == null ? RESULT_CANCELED : RESULT_OK, resultIntent);
            } 
            else if (resultIntent != null) {
                startActivity(resultIntent);
            }
            finish();
        }

        @Override
        public void onContactSplit(Uri newLookupUri) {
            finish();
        }

        @Override
        public void onContactNotFound() {
            finish();
        }

//        @Override
//        public void onEditOtherContactRequested(Uri contactLookupUri, ArrayList<ContentValues> values) {
//            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
//            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
//            intent.putExtra(ContactEditorFragment.INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY, "");
//
//            // Pass on all the data that has been entered so far
//            if (values != null && values.size() != 0) {
//                intent.putParcelableArrayListExtra(ScContactsContract.Intents.Insert.DATA, values);
//            }
//            startActivity(intent);
//            finish();
//        }
//
//        @Override
//        public void onCustomCreateContactActivityRequested(AccountWithDataSet account,  Bundle intentExtras) {
//
//            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(ScContactEditorActivity.this);
//            final AccountType accountType = accountTypes.getAccountType(null, null);
//
//            Intent intent = new Intent();
//            intent.setClassName(accountType.syncAdapterPackageName, accountType.getCreateContactActivityClassName());
//            intent.setAction(Intent.ACTION_INSERT);
//            intent.setType(RawContacts.CONTENT_ITEM_TYPE);
//            if (intentExtras != null) {
//                intent.putExtras(intentExtras);
//            }
////            intent.putExtra(RawContacts.ACCOUNT_NAME, account.name);
////            intent.putExtra(RawContacts.ACCOUNT_TYPE, account.type);
////            intent.putExtra(RawContacts.DATA_SET, account.dataSet);
//            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
//            startActivity(intent);
//            finish();
//        }
//
//        @Override
//        public void onCustomEditContactActivityRequested(AccountWithDataSet account, Uri rawContactUri, Bundle intentExtras, 
//                boolean redirect) {
//            final AccountTypeManager accountTypes =
//                    AccountTypeManager.getInstance(ScContactEditorActivity.this);
//            final AccountType accountType = accountTypes.getAccountType(account.type, account.dataSet);
//
//            Intent intent = new Intent();
//            intent.setClassName(accountType.syncAdapterPackageName, accountType.getEditContactActivityClassName());
//            intent.setAction(Intent.ACTION_EDIT);
//            intent.setData(rawContactUri);
//            if (intentExtras != null) {
//                intent.putExtras(intentExtras);
//            }
//
//            if (redirect) {
//                intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
//                startActivity(intent);
//                finish();
//            } else {
//                startActivity(intent);
//            }
//        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }
}
