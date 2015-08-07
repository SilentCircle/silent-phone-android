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
import com.silentcircle.contacts.group.GroupEditorFragment;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.utils.DialogManager;
import com.silentcircle.silentphone2.R;
//import com.android.contacts.util.PhoneCapabilityTester;

public class GroupEditorActivity extends ActionBarActivity implements ScContactSaveService.Listener,
    DialogManager.DialogShowingViewActivity {

    private static final String TAG = "GroupEditorActivity";

    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";
    public static final String ACTION_ADD_MEMBER_COMPLETED = "addMemberCompleted";
    public static final String ACTION_REMOVE_MEMBER_COMPLETED = "removeMemberCompleted";

    private GroupEditorFragment mFragment;

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

        String action = getIntent().getAction();

        if (ACTION_SAVE_COMPLETED.equals(action)) {
            finish();
            return;
        }
        setContentView(R.layout.group_editor_activity);
        setSupportActionBar((Toolbar) findViewById(R.id.main_toolbar));

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Inflate a custom action bar that contains the "done" button for saving changes
            // to the group
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View customActionBarView = inflater.inflate(R.layout.editor_custom_action_bar, null);
            View saveMenuItem = customActionBarView.findViewById(R.id.save_menu_item);
            saveMenuItem.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFragment.onDoneClicked();
                }
            });
            // Show the custom action bar but hide the home icon and title
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME |
                    ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
        }

        mFragment = (GroupEditorFragment) getFragmentManager().findFragmentById(R.id.group_editor_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.setContentResolver(getContentResolver());

        // NOTE The fragment will restore its state by itself after orientation changes, so
        // we need to do this only for a new instance.
        if (savedState == null) {
            Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData() : null;
            mFragment.load(action, uri, getIntent().getExtras());
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) {
            return mDialogManager.onCreateDialog(id, args);
        } else {
            // Nobody knows about the Dialog
            Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        // If the change could not be saved, then revert to the default "back" button behavior.
        if (!mFragment.save()) {
            super.onBackPressed();
        }
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
        if (ACTION_SAVE_COMPLETED.equals(action)) {
            mFragment.onSaveCompleted(true, intent.getData());
        }
    }

    private final GroupEditorFragment.Listener mFragmentListener =  new GroupEditorFragment.Listener() {
        @Override
        public void onGroupNotFound() {
            finish();
        }

        @Override
        public void onReverted() {
            finish();
        }

        @Override
        public void onAccountsNotFound() {
            finish();
        }

        @Override
        public void onSaveFinished(int resultCode, Intent resultIntent) {
            // TODO: Collapse these 2 cases into 1 that will just launch an intent with the VIEW
            // action to see the group URI (when group URIs are supported)
            // For a 2-pane screen, set the activity result, so the original activity (that launched
            // the editor) can display the group detail page
//            if (PhoneCapabilityTester.isUsingTwoPanes(GroupEditorActivity.this)) {
//                setResult(resultCode, resultIntent);
//            } 
//            else 
                if (resultIntent != null) {
                // For a 1-pane screen, launch the group detail page
                Intent intent = new Intent(GroupEditorActivity.this, GroupDetailActivity.class);
                intent.setData(resultIntent.getData());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            finish();
        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }
}
