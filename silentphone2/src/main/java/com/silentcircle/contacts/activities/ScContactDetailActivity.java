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

import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.silentcircle.contacts.ScContactSaveService;
import com.silentcircle.contacts.detail.ContactDetailDisplayUtils;
import com.silentcircle.contacts.detail.ContactDetailFragment;
import com.silentcircle.contacts.detail.ContactDetailLayoutController;
import com.silentcircle.contacts.detail.ContactLoaderFragment;
import com.silentcircle.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.silentcircle.contacts.interactions.ContactDeletionInteraction;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.utils.PhoneCapabilityTester;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

import java.util.ArrayList;
// import com.android.contacts.util.PhoneCapabilityTester;

public class ScContactDetailActivity extends ActionBarActivity {
    private static final String TAG = "ContactDetailActivity";

    /** Shows a toogle button for hiding/showing updates. Don't submit with true */
    private static final boolean DEBUG_TRANSITIONS = false;

    private Contact mContactData;
    private Uri mLookupUri;

    private ContactDetailLayoutController mContactDetailLayoutController;
    private ContactLoaderFragment mLoaderFragment;

    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(getBaseContext());
        boolean dbReady = db.isRegisteredWithKeyManager() && db.isReady();
        if (!dbReady) {
            final String msg = getString(R.string.app_name) + ": " + getString(R.string.locked);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
            // This activity must not be shown. We have to select the contact in the
            // PeopleActivity instead ==> Create a forward intent and finish
            final Intent originalIntent = getIntent();
            Intent intent = new Intent();
            intent.setAction(originalIntent.getAction());
            intent.setDataAndType(originalIntent.getData(), originalIntent.getType());

            // If we are launched from the outside, we should create a new task, because the user
            // can freely navigate the app (this is different from phones, where only the UP button
            // kicks the user into the full app)
            if (NavUtils.shouldUpRecreateTask(this, intent)) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                        Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            } else {
                intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                        Intent.FLAG_ACTIVITY_FORWARD_RESULT | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }

            if (!DialerActivity.useBpForwarder())
                intent.setClass(this, ScContactsMainActivity.class);
            else
                intent.setComponent(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactsMainActivityForwarder"));
            try {
                startActivity(intent);
            } catch (Exception ignored) { }
            finish();
            return;
        }

        setContentView(R.layout.contact_detail_activity);
        setSupportActionBar((Toolbar) findViewById(R.id.main_toolbar));

        mContactDetailLayoutController = new ContactDetailLayoutController(this, savedState,
                getFragmentManager(), null, findViewById(R.id.contact_detail_container),
                mContactDetailFragmentListener);

        // We want the UP affordance but no app icon.
        // Setting HOME_AS_UP, SHOW_TITLE and clearing SHOW_HOME does the trick.
        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME);
            actionBar.setTitle("");
        }

//        Log.i(TAG, getIntent().getData().toString());
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
         if (fragment instanceof ContactLoaderFragment) {
            mLoaderFragment = (ContactLoaderFragment) fragment;
            mLoaderFragment.setListener(mLoaderFragmentListener);
            mLoaderFragment.loadUri(getIntent().getData());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.star, menu);
        if (DEBUG_TRANSITIONS) {
            final MenuItem toggleSocial = menu.add(mLoaderFragment.getLoadStreamItems() ? "less" : "more");
            toggleSocial.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            toggleSocial.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mLoaderFragment.toggleLoadStreamItems();
                    invalidateOptionsMenu();
                    return false;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem starredMenuItem = menu.findItem(R.id.menu_star);
        starredMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mLookupUri != null) {
                    // Read the current starred value from the UI instead of using the last
                    // loaded state. This allows rapid tapping without writing the same
                    // value several times
                    final boolean isStarred = starredMenuItem.isChecked();

                    // To improve responsiveness, swap out the picture (and tag) in the UI already
                    ContactDetailDisplayUtils.configureStarredMenuItem(starredMenuItem,
                            mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                            !isStarred);

                    // Now perform the real save
                    Intent intent = ScContactSaveService.createSetStarredIntent(
                            ScContactDetailActivity.this, mLookupUri, !isStarred);
                    ScContactDetailActivity.this.startService(intent);
                }
                return true;
            }
        });
        // If there is contact data, update the starred state
        if (mContactData != null) {
            ContactDetailDisplayUtils.configureStarredMenuItem(starredMenuItem,
                    mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                    mContactData.getStarred());
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // First check if the {@link ContactLoaderFragment} can handle the key
        if (mLoaderFragment != null && mLoaderFragment.handleKeyDown(keyCode)) return true;

        // Otherwise find the correct fragment to handle the event
        FragmentKeyListener mCurrentFragment = mContactDetailLayoutController.getCurrentPage();
        if (mCurrentFragment != null && mCurrentFragment.handleKeyDown(keyCode)) return true;

        // In the last case, give the key event to the superclass.
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mContactDetailLayoutController != null) {
            mContactDetailLayoutController.onSaveInstanceState(outState);
        }
    }

    private final ContactLoaderFragmentListener mLoaderFragmentListener = new ContactLoaderFragmentListener() {
        @Override
        public void onContactNotFound() {
            finish();
        }

        @Override
        public void onDetailsLoaded(final Contact result) {
            if (result == null) {
                return;
            }
            // Since {@link FragmentTransaction}s cannot be done in the onLoadFinished() of the
            // {@link LoaderCallbacks}, then post this {@link Runnable} to the {@link Handler}
            // on the main thread to execute later.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // If the activity is destroyed (or will be destroyed soon), don't update the UI
                    if (isFinishing()) {
                        return;
                    }
                    mContactData = result;
                    mLookupUri = result.getLookupUri();
                    invalidateOptionsMenu();
                    setupTitle();
                    mContactDetailLayoutController.setContactData(mContactData);
                }
            });
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.putExtra(ScContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            // Don't finish the detail activity after launching the editor because when the
            // editor is done, we will still want to show the updated contact details using
            // this activity.
            startActivity(intent);
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ScContactDetailActivity.this, contactUri, true);
        }
    };

    /**
     * Setup the activity title and subtitle with contact name and company.
     */
    private void setupTitle() {
        CharSequence displayName = ContactDetailDisplayUtils.getDisplayName(this, mContactData);
        String company =  ContactDetailDisplayUtils.getCompany(this, mContactData);

        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(displayName);
            actionBar.setSubtitle(company);
        }

        if (!TextUtils.isEmpty(displayName)) {
            AccessibilityManager accessibilityManager =
                    (AccessibilityManager) this.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isEnabled()) {
                View decorView = getWindow().getDecorView();
                decorView.setContentDescription(displayName);
                decorView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            }
        }
    }

    private final ContactDetailFragment.Listener mContactDetailFragmentListener = new ContactDetailFragment.Listener() {
        @Override
        public void onItemClicked(Intent intent) {
            if (intent == null) {
                return;
            }
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onCreateRawContactRequested(ArrayList<ContentValues> values) {
            Toast.makeText(ScContactDetailActivity.this, R.string.toast_making_personal_copy, Toast.LENGTH_LONG).show();
            Intent serviceIntent;
            if (!DialerActivity.useBpForwarder()) {
                serviceIntent = ScContactSaveService.createNewRawContactIntent(
                        ScContactDetailActivity.this, values, ScContactDetailActivity.class, Intent.ACTION_VIEW, null);
            }
            else {
                ComponentName cm = new ComponentName(ScContactDetailActivity.this,
                        "com.silentcircle.contacts.activities.ScContactDetailActivityForwarder");
                serviceIntent = ScContactSaveService.createNewRawContactIntent(
                        ScContactDetailActivity.this, values, null, Intent.ACTION_VIEW, cm);
            }
            try {
                startActivity(serviceIntent);
            } catch (Exception ignored) {}

        }
    };

    /**
     * This interface should be implemented by {@link android.support.v4.app.Fragment}s within this
     * activity so that the activity can determine whether the currently
     * displayed view is handling the key event or not.
     */
    public interface FragmentKeyListener {
        /**
         * Returns true if the key down event will be handled by the implementing class, or false
         * otherwise.
         */
        public boolean handleKeyDown(int keyCode);
    }
}
