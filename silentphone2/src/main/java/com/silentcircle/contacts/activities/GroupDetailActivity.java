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

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.silentcircle.contacts.group.GroupDetailFragment;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

public class GroupDetailActivity extends ActionBarActivity {

    @SuppressWarnings("unused")
    private static final String TAG = "GroupDetailActivity";

    private GroupDetailFragment mFragment;

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

        // TODO: Create Intent Resolver to handle the different ways users can get to this list.
        // TODO: Handle search or key down

        setContentView(R.layout.group_detail_activity);
        setSupportActionBar((Toolbar) findViewById(R.id.main_toolbar));

        mFragment = (GroupDetailFragment) getFragmentManager().findFragmentById(R.id.group_detail_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.loadGroup(getIntent().getData());
        mFragment.closeActivityAfterDelete(true);

        // We want the UP affordance but no app icon.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE
                    | ActionBar.DISPLAY_SHOW_HOME);
        }
    }

    private final GroupDetailFragment.Listener mFragmentListener = new GroupDetailFragment.Listener() {

        @Override
        public void onGroupSizeUpdated(String size) {
            getSupportActionBar().setSubtitle(size);
        }

        @Override
        public void onGroupTitleUpdated(String title) {
            getSupportActionBar().setTitle(title);
        }

        @Override
        public void onEditRequested(Uri groupUri) {
            final Intent intent = new Intent(GroupDetailActivity.this, GroupEditorActivity.class);
            intent.setData(groupUri);
            intent.setAction(Intent.ACTION_EDIT);
            startActivity(intent);
        }

        @Override
        public void onContactSelected(Uri contactUri) {
            Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
            startActivity(intent);
        }

    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent;
                if (!DialerActivity.useBpForwarder())
                    intent = new Intent(this, ScContactsMainActivity.class);
                else {
                    intent = new Intent();
                    intent.setComponent(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactsMainActivityForwarder"));
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try {
                    startActivity(intent);
                } catch (Exception ignored) { }
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
