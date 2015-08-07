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
 * This class uses many (a lot) ideas from the standard Android contacts provider, in some parts
 * it's even copied verbatim to reduce the learning curve for Android aficionados.
 */

package com.silentcircle.contacts.activities;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.silentcircle.contacts.ScContactSaveService;
import com.silentcircle.contacts.detail.ContactDetailFragment;
import com.silentcircle.contacts.detail.ContactDetailLayoutController;
import com.silentcircle.contacts.detail.ContactLoaderFragment;
import com.silentcircle.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.silentcircle.contacts.editor.ContactEditorFragment;
import com.silentcircle.contacts.group.GroupBrowseListFragment;
import com.silentcircle.contacts.group.GroupDetailFragment;
import com.silentcircle.contacts.interactions.ContactDeletionInteraction;
import com.silentcircle.contacts.interactions.ImportExportDialogFragment;
import com.silentcircle.contacts.list.ContactListFilter;
import com.silentcircle.contacts.list.ContactListFilterController;
import com.silentcircle.contacts.list.ContactTileAdapter;
import com.silentcircle.contacts.list.ContactTileListFragment;
import com.silentcircle.contacts.list.ContactsIntentResolver;
import com.silentcircle.contacts.list.ContactsRequest;
import com.silentcircle.contacts.list.ContactsUnavailableFragment;
import com.silentcircle.contacts.list.DirectoryListLoader;
import com.silentcircle.contacts.list.OnContactBrowserActionListener;
import com.silentcircle.contacts.list.ProviderStatusWatcher;
import com.silentcircle.contacts.list.ScContactEntryListFragment;
import com.silentcircle.contacts.list.ScDefaultContactBrowseListFragment;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.preference.ContactsPreferenceActivity;
import com.silentcircle.contacts.preference.DisplayOptionsPreferenceFragment;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.contacts.utils.HelpUtils;
import com.silentcircle.contacts.utils.PhoneCapabilityTester;
import com.silentcircle.contacts.utils.UriUtils;
import com.silentcircle.contacts.vcard.ManageVCardActivity;
import com.silentcircle.contacts.widget.SlidingTabLayout;
import com.silentcircle.contacts.widget.TransitionAnimationView;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentcontacts2.ScContactsContract.ProviderStatus;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

import java.util.ArrayList;


public class ScContactsMainActivity extends ActionBarActivity implements
    ContactListFilterController.ContactListFilterListener, ActionBarAdapter.Listener,

    ProviderStatusWatcher.ProviderStatusListener {
    
    private static final String TAG = "ScContactsMainActivity";

    // Take from Honeycomb
    public static final int SCROLLBAR_POSITION_LEFT = 1;
    public static final int SCROLLBAR_POSITION_RIGHT = 2;

    private static final int SUBACTIVITY_NEW_CONTACT = 2;
    private static final int SUBACTIVITY_EDIT_CONTACT = 3;
    private static final int SUBACTIVITY_NEW_GROUP = 4;
    private static final int SUBACTIVITY_EDIT_GROUP = 5;
    private static final int KEY_MANAGER_READY = 7;

    private final Handler mHandler = new Handler();

    private ActionBarAdapter mActionBarAdapter;
    private boolean mIsRecreatedInstance;
    
    private boolean mOptionsMenuContactsAvailable;
    
    private ContactsRequest mRequest;
    private ContactsIntentResolver mIntentResolver;
    private ContactListFilterController mContactListFilterController;
    private boolean mCurrentFilterIsValid;

    private ProviderStatusWatcher mProviderStatusWatcher;
    private ProviderStatusWatcher.Status mProviderStatus;

    private ContactsUnavailableFragment mContactsUnavailableFragment;

    private ContactDetailFragment mContactDetailFragment;       // used in landscape (two-pane) layouts

    private ContactLoaderFragment mContactDetailLoaderFragment;
    private final ContactDetailLoaderFragmentListener mContactDetailLoaderFragmentListener =
            new ContactDetailLoaderFragmentListener();

    private ContactDetailLayoutController mContactDetailLayoutController;

    private GroupDetailFragment mGroupDetailFragment;
    private final GroupDetailFragmentListener mGroupDetailFragmentListener =  new GroupDetailFragmentListener();

    private View mFavoritesView;
    private View mBrowserView;
    private TransitionAnimationView mContactDetailsView;
    private TransitionAnimationView mGroupDetailsView;

    /** ViewPager for swipe, used only on the phone (i.e. one-pane mode) */
    private ViewPager mTabPager;
    private TabPagerAdapter mTabPagerAdapter;
    private final TabPagerListener mTabPagerListener = new TabPagerListener();


    /**
     * If {@link #configureFragments(boolean)} is already called.  Used to avoid calling it twice
     * in {@link #onStart}.
     * (This initialization only needs to be done once in onStart() when the Activity was just
     * created from scratch -- i.e. onCreate() was just called)
     */
    private boolean mFragmentInitialized;

    private boolean mContinueResume;

    public ScContactsMainActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
        mProviderStatusWatcher = ProviderStatusWatcher.getInstance(this);
    }

    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (!processIntent(false)) {
            finish();
            return;
        }
        mProviderStatusWatcher.addListener(this);
        mContactListFilterController = ContactListFilterController.getInstance(this);
        mContactListFilterController.checkFilterValidity(false);
        mContactListFilterController.addListener(this);

        mIsRecreatedInstance = (savedState != null);

        createViewsAndFragments(savedState);
        checkAndSetKeyManager();
    }

    private void keyManagerChecked() {
        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(getBaseContext());
        if (!db.isReady())
            db.onKeyManagerUnlockRequest();
        if (mContinueResume) {
            mContinueResume = false;
            continueResume();
        }
    }

    private void checkAndSetKeyManager() {
        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(getBaseContext());

        if (db.isReady())
            return;
        boolean hasKeyManager = db.checkRegisterKeyManager(true);
        if (hasKeyManager) {
            mActionBarAdapter.setCurrentTab(ActionBarAdapter.TabState.ALL);
            startActivityForResult(KeyManagerSupport.getKeyManagerReadyIntent(), KEY_MANAGER_READY);
        }
        else {
            finish();
        }
    }

    /**
     * Initialize fragments that are (or may not be) in the layout.
     *
     * For the fragments that are in the layout, we initialize them in
     * {@link #createViewsAndFragments(android.os.Bundle)} after inflating the layout.
     *
     * However, there are special fragments which may not be in the layout, so we have to do the
     * initialization here.
     * The target fragments are:
     * - {@link com.silentcircle.contacts.detail.ContactDetailFragment}:  May not be
     *   in the layout depending on the configuration.  (i.e. portrait)
     * - {@link com.silentcircle.contacts.list.ContactsUnavailableFragment}: We always create it at runtime.
     */
    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactDetailFragment) {
            mContactDetailFragment = (ContactDetailFragment) fragment;
        }
        else if (fragment instanceof ContactsUnavailableFragment) {
            mContactsUnavailableFragment = (ContactsUnavailableFragment)fragment;
            mContactsUnavailableFragment.setOnContactsUnavailableActionListener(new ContactsUnavailableFragmentListener());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        if (!processIntent(true)) {
            finish();
            return;
        }
        mActionBarAdapter.initialize(null, mRequest);

        mContactListFilterController.checkFilterValidity(false);
        mCurrentFilterIsValid = true;

        // Re-configure fragments.
        configureFragments(true /* from request */);
        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Resolve the intent and initialize {@link #mRequest}, and launch another activity if redirect
     * is needed.
     *
     * @param forNewIntent set true if it's called from {@link #onNewIntent(android.content.Intent)}.
     * @return {@code true} if this activity should continue running.  {@code false}
     *         if it shouldn't, in which case the caller should finish() itself and shouldn't do
     *         farther initialization.
     */
    private boolean processIntent(boolean forNewIntent) {
        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, this + " processIntent: forNewIntent=" + forNewIntent + " intent=" + getIntent() + " request=" + mRequest);
        }
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            return false;
        }

        Intent redirect = mRequest.getRedirectIntent();
        if (redirect != null) {
            // Need to start a different activity
            startActivity(redirect);
            return false;
        }

        if (mRequest.getActionCode() == ContactsRequest.ACTION_VIEW_CONTACT && !PhoneCapabilityTester.isUsingTwoPanes(this)) {
            if (!DialerActivity.useBpForwarder()) {
                redirect = new Intent(this, ScContactDetailActivity.class);
            }
            else {
                redirect = new Intent();
                ComponentName cm = new ComponentName(this, "com.silentcircle.contacts.activities.ScContactDetailActivityForwarder");
                redirect.setComponent(cm);
            }
            redirect.setAction(Intent.ACTION_VIEW);
            redirect.setData(mRequest.getContactUri());
            startActivity(redirect);
            return false;
        }
        return true;
    }

    public boolean areContactsAvailable() {
        return (mProviderStatus != null) && mProviderStatus.status == ProviderStatus.STATUS_NORMAL;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.people_options, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuContactsAvailable = areContactsAvailable();
        if (!mOptionsMenuContactsAvailable) {
            return false;
        }
        // Get references to individual menu items in the menu
        final MenuItem addContactMenu = menu.findItem(R.id.menu_add_contact);

        MenuItem addGroupMenu = menu.findItem(R.id.menu_add_group);

        final MenuItem clearFrequentsMenu = menu.findItem(R.id.menu_clear_frequents);
        final MenuItem helpMenu = menu.findItem(R.id.menu_help);

        final boolean isSearchMode = mActionBarAdapter.isSearchMode();

        if (isSearchMode) {
            addContactMenu.setVisible(false);
            addGroupMenu.setVisible(false);
//            contactsFilterMenu.setVisible(false);
            clearFrequentsMenu.setVisible(false);
            helpMenu.setVisible(false);
        }
        else {
            switch (mActionBarAdapter.getCurrentTab()) {
                case ActionBarAdapter.TabState.FAVORITES:
                    addContactMenu.setVisible(false);
                    addGroupMenu.setVisible(false);
//                    contactsFilterMenu.setVisible(false);
                    clearFrequentsMenu.setVisible(hasFrequents());
                    break;

                case ActionBarAdapter.TabState.ALL:
                    addContactMenu.setVisible(true);
                    addGroupMenu.setVisible(false);
//                    contactsFilterMenu.setVisible(true);
                    clearFrequentsMenu.setVisible(false);
                    break;

                case ActionBarAdapter.TabState.GROUPS:
                    addGroupMenu.setVisible(true);
                    addContactMenu.setVisible(false);
//                    contactsFilterMenu.setVisible(false);
                    clearFrequentsMenu.setVisible(false);
                    break;
            }
            HelpUtils.prepareHelpMenuItem(this, helpMenu, R.string.help_url_people_main);
        }
        final boolean showMiscOptions = !isSearchMode;
        makeMenuItemVisible(menu, R.id.menu_search, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_import_export, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_remove_vcards, showMiscOptions);
//        makeMenuItemVisible(menu, R.id.menu_accounts, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_settings, showMiscOptions && !ContactsPreferenceActivity.isEmpty(this));
//        makeMenuItemVisible(menu, R.id.menu_about, showMiscOptions);

        // Debug options need to be visible even in search mode.
//        makeMenuItemVisible(menu, R.id.export_database, mEnableDebugMenuOptions);
        return true;
    }

    /**
     * Returns whether there are any frequently contacted people being displayed
     * @return
     */
    private boolean hasFrequents() {
//  TODO       if (PhoneCapabilityTester.isUsingTwoPanesInFavorites(this)) {
//            return mFrequentFragment.hasFrequents();
//        } else {
//            return mFavoritesFragment.hasFrequents();
//        }
        return false;
    }

    private void makeMenuItemVisible(Menu menu, int itemId, boolean visible) {
        MenuItem item =menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    @Override
    public void onBackPressed() {
        if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setSearchMode(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                // The home icon on the action bar is pressed
                if (mActionBarAdapter.isUpShowing()) {
                    // "UP" icon press -- should be treated as "back".
                    onBackPressed();
                }
                return true;
            }
            case R.id.menu_settings: {
                final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
                // as there is only one section right now, make sure it is selected
                // on small screens, this also hides the section selector
                // Due to b/5045558, this code unfortunately only works properly on phones
                boolean settingsAreMultiPane = getResources().getBoolean(R.bool.preferences_prefer_dual_pane);
                if (!settingsAreMultiPane) {
                    intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, DisplayOptionsPreferenceFragment.class.getName());
                    intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE, R.string.preference_displayOptions);
                }
                startActivity(intent);
                return true;
            }
//            case R.id.menu_contacts_filter: {
//                AccountFilterUtil.startAccountFilterActivityForResult(this, SUBACTIVITY_ACCOUNT_FILTER,
//                            mContactListFilterController.getFilter());
//                return true;
//            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add_contact: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI);
//                // On 2-pane UI, we can let the editor activity finish itself and return
//                // to this activity to display the new contact.
                if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
                    intent.putExtra(ScContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
                    startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
                }
                else {
                    // Otherwise, on 1-pane UI, we need the editor to launch the view contact
                    // intent itself.
                    startActivity(intent);
                }
                return true;
            }
            case R.id.menu_add_group: {
                createNewGroup();
                return true;
            }
            case R.id.menu_import_export: {
                final boolean available = areContactsAvailable();
                // Call doImport directly because SCA does not support Import from SIM card
                if (!available)
                    ImportExportDialogFragment.doImportFromSdCard(this);
                else
                    ImportExportDialogFragment.show(getFragmentManager(), available);
                return true;
            }
            case R.id.menu_remove_vcards: {
                Intent importIntent = new Intent(this, ManageVCardActivity.class);
                startActivity(importIntent);
                return true;
            }
            case R.id.menu_about: {
                String about = getString(R.string.sca_about, BuildConfig.SPA_BUILD_NUMBER, BuildConfig.SPA_BUILD_COMMIT);
                showAboutInfo(about);
                return true;
            }

            case R.id.menu_clear_frequents: {
//                ClearFrequentsDialog.show(getFragmentManager());
                return true;
            }
//            case R.id.menu_accounts: {
//                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
//                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {ScContactsContract.AUTHORITY});
//                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
//                startActivity(intent);
//                return true;
//            }
            case R.id.export_database: {
//                final Intent intent = new Intent("com.android.providers.contacts.DUMP_DATABASE");
//                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
//                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mFragmentInitialized) {
            mFragmentInitialized = true;
            /* Configure fragments if we haven't.
             *
             * Note it's a one-shot initialization, so we want to do this in {@link #onCreate}.
             *
             * However, because this method may indirectly touch views in fragments but fragments
             * created in {@link #configureContentView} using a {@link FragmentTransaction} will NOT
             * have views until {@link Activity#onCreate} finishes (they would if they were inflated
             * from a layout), we need to do it here in {@link #onStart()}.
             *
             * (When {@link Fragment#onCreateView} is called is different in the former case and
             * in the latter case, unfortunately.)
             *
             * Also, we skip most of the work in it if the activity is a re-created one.
             * (so the argument.)
             */
            configureFragments(!mIsRecreatedInstance);
        }
        else if (PhoneCapabilityTester.isUsingTwoPanes(this) && !mCurrentFilterIsValid) {
            // We only want to do the filter check in onStart for wide screen devices where it
            // is often possible to get into single contact mode. Only do this check if
            // the filter hasn't already been set properly (i.e. onCreate or onActivityResult).

            // Since there is only one {@link ContactListFilterController} across multiple
            // activity instances, make sure the filter controller is in sync withthe current
            // contact list fragment filter.
            // TODO: Clean this up. Perhaps change {@link ContactListFilterController} to not be a
            // singleton?
            mContactListFilterController.setContactListFilter(mAllFragment.getFilter(), true);
            mContactListFilterController.checkFilterValidity(true);
            mCurrentFilterIsValid = true;
        }
    }

    @Override
    protected void onPause() {
        mOptionsMenuContactsAvailable = false;
        mProviderStatusWatcher.stop();
        super.onPause();
    }

    private void continueResume() {
        mProviderStatusWatcher.start();
        updateViewConfiguration(true);

        // Re-register the listener, which may have been cleared when onSaveInstanceState was
        // called.  See also: onSaveInstanceState
        mActionBarAdapter.setListener(this);
        // Current tab may have changed since the last onSaveInstanceState().  Make sure
        // the actual contents match the tab.
        updateFragmentsVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Continue with resume (display views etc) only if DB is ready. We open DB in function
        // keyManagerChecked() which get triggered by the onActivityResult for the KeyManager ready
        // intent.
        // Waiting for the DB here has a much better UI because the views don't need to handle failed queries.
        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(getBaseContext());
        if (db.isReady())
            continueResume();
        else
            mContinueResume = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mCurrentFilterIsValid = false;
    }

    @Override
    protected void onDestroy() {
        mProviderStatusWatcher.removeListener(this);

        // Some of variables will be null if this Activity redirects Intent.
        // See also onCreate() or other methods called during the Activity's initialization.
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }
        if (mContactListFilterController != null) {
            mContactListFilterController.removeListener(this);
        }

        super.onDestroy();
    }

    private void createNewGroup() {
        final Intent intent = new Intent(this, GroupEditorActivity.class);
        intent.setAction(Intent.ACTION_INSERT);
        startActivityForResult(intent, SUBACTIVITY_NEW_GROUP);
    }

    @Override
    public boolean onSearchRequested() { // Search key pressed.
        mActionBarAdapter.setSearchMode(true);
        return true;
    }

    public void showSlidingTabs(boolean show) {
        if (mSlidingTabLayout == null)
            return;
        if (show)
            mSlidingTabLayout.setVisibility(View.VISIBLE);
        else
            mSlidingTabLayout.setVisibility(View.GONE);
    }

    /**
     * Showing a list of Contacts. Also used for showing search results in search mode.
     */
    private ScDefaultContactBrowseListFragment mAllFragment;
    private GroupBrowseListFragment mGroupsFragment;
//    private ContactTileFrequentFragment mFrequentFragment;

    private ContactTileListFragment mFavoritesFragment;
    private ContactTileListFragment.Listener mFavoritesFragmentListener = new StrequentContactListFragmentListener();

    final String CALL_LOG_TAG = "sc-calllog-all";
    final String ALL_TAG = "sc-contacts-all";
    final String FAVORITES_TAG = "sc-favourites-all";
    final String GROUP_TAG = "sc-groups-all";

    private SlidingTabLayout mSlidingTabLayout;
    private void createViewsAndFragments(Bundle savedState) {
        setContentView(R.layout.sccontacts_main_activity);
        setSupportActionBar((Toolbar)findViewById(R.id.main_toolbar));

        final FragmentManager fragmentManager = this.getFragmentManager();

        // Hide all tabs (the current tab will later be reshown once a tab is selected)
        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        // Prepare the fragments which are used both on 1-pane and on 2-pane.
        final boolean isUsingTwoPanes = PhoneCapabilityTester.isUsingTwoPanes(this);

        if (isUsingTwoPanes) {
            mFavoritesFragment = getFragment(R.id.favorites_fragment);
            mAllFragment = getFragment(R.id.all_fragment);
            mGroupsFragment = getFragment(R.id.groups_fragment);
        }
        else {
            mTabPager = getView(R.id.tab_pager);
            mTabPagerAdapter = new TabPagerAdapter();
            mTabPager.setAdapter(mTabPagerAdapter);

            // BEGIN_INCLUDE (setup_slidingtablayout)
            // Give the SlidingTabLayout the ViewPager, this must be done AFTER the ViewPager has had
            // it's PagerAdapter set.
            mSlidingTabLayout = getView(R.id.sliding_tabs);
            mSlidingTabLayout.setViewPager(mTabPager);
            // END_INCLUDE (setup_slidingtablayout)

            mSlidingTabLayout.setOnPageChangeListener(mTabPagerListener);

            // Selecting a new tab will only change the visibility; it'll never create/destroy fragments.
            // However, if it's after screen rotation, the fragments have been re-created by the fragment
            // manager, so first see if the target fragments exists.
            mFavoritesFragment = (ContactTileListFragment) fragmentManager.findFragmentByTag(FAVORITES_TAG);
            mAllFragment = (ScDefaultContactBrowseListFragment) fragmentManager.findFragmentByTag(ALL_TAG);
            mGroupsFragment = (GroupBrowseListFragment) fragmentManager.findFragmentByTag(GROUP_TAG);

            if (mAllFragment == null) {
                mAllFragment = new ScDefaultContactBrowseListFragment();
                mFavoritesFragment = new ContactTileListFragment();
                mGroupsFragment = new GroupBrowseListFragment();

                transaction.add(R.id.tab_pager, mAllFragment, ALL_TAG);
                transaction.add(R.id.tab_pager, mGroupsFragment, GROUP_TAG);
                transaction.add(R.id.tab_pager, mFavoritesFragment, FAVORITES_TAG);
            }
            mFavoritesFragment.setDisplayEmpty(false);
        }
        mFavoritesFragment.setListener(mFavoritesFragmentListener);

        mAllFragment.setOnContactListActionListener(new ContactBrowserActionListener());
        mAllFragment.setDarkTheme(true);

        mGroupsFragment.setListener(new GroupBrowserActionListener());

        // Hide all fragments for now.  We adjust visibility when we get onSelectedTabChanged()
        // from ActionBarAdapter.
        transaction.hide(mAllFragment);
        transaction.hide(mGroupsFragment);
        transaction.hide(mFavoritesFragment);

        if (isUsingTwoPanes) {
            // Prepare 2-pane only fragments/views...

            // Container views for fragments
            mFavoritesView = getView(R.id.favorites_view);
            mContactDetailsView = getView(R.id.contact_details_view);
            mGroupDetailsView = getView(R.id.group_details_view);
            mBrowserView = getView(R.id.browse_view);

            // Only favorites tab with two panes has a separate frequent fragment
//  TODO: frequent URI and stuff         if (PhoneCapabilityTester.isUsingTwoPanesInFavorites(this)) {
//                mFrequentFragment = getFragment(R.id.frequent_fragment);
//                mFrequentFragment.setListener(mFavoritesFragmentListener);
//                mFrequentFragment.setDisplayType(DisplayType.FREQUENT_ONLY);
//                mFrequentFragment.enableQuickContact(true);
//            }

            mContactDetailLoaderFragment = getFragment(R.id.contact_detail_loader_fragment);
            mContactDetailLoaderFragment.setListener(mContactDetailLoaderFragmentListener);

            mGroupDetailFragment = getFragment(R.id.group_detail_fragment);
            mGroupDetailFragment.setListener(mGroupDetailFragmentListener);
            mGroupDetailFragment.setQuickContact(true);

            if (mContactDetailFragment != null) {
                transaction.hide(mContactDetailFragment);
            }
            transaction.hide(mGroupDetailFragment);

            // Configure contact details
            mContactDetailLayoutController = new ContactDetailLayoutController(this, savedState,
                    getFragmentManager(), mContactDetailsView,
                    findViewById(R.id.contact_detail_container),
                    new ContactDetailFragmentListener());
        }
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();

        // Setting Properties after fragment is created
        if (PhoneCapabilityTester.isUsingTwoPanesInFavorites(this)) {
            mFavoritesFragment.enableQuickContact(true);
            mFavoritesFragment.setDisplayType(ContactTileAdapter.DisplayType.STARRED_ONLY);
        }
        else {
            // TODO - currently only STARRED_ONLY supported
            mFavoritesFragment.enableQuickContact(false);
            mFavoritesFragment.setDisplayType(ContactTileAdapter.DisplayType.STARRED_ONLY);
        }
        // Configure action bar
        mActionBarAdapter = new ActionBarAdapter(this, this, this.getSupportActionBar(), isUsingTwoPanes);
        mActionBarAdapter.initialize(savedState, mRequest);

        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Updates the fragment/view visibility according to the current mode, such as
     * {@link ActionBarAdapter#isSearchMode()} and {@link ActionBarAdapter#getCurrentTab()}.
     */
    private void updateFragmentsVisibility() {

        int tab = mActionBarAdapter.getCurrentTab();

        // We use ViewPager on 1-pane.
        final boolean useTwoPane = PhoneCapabilityTester.isUsingTwoPanes(this);
        if (!useTwoPane) {
            if (mActionBarAdapter.isSearchMode()) {
                mTabPagerAdapter.setSearchMode(true);
            }
            else {
                // No smooth scrolling if quitting from the search mode.
                final boolean wasSearchMode = mTabPagerAdapter.isSearchMode();
                mTabPagerAdapter.setSearchMode(false);
                if (mTabPager.getCurrentItem() != tab) {
                    mTabPager.setCurrentItem(tab, !wasSearchMode);
                }
            }
            invalidateOptionsMenuLocal();
            showEmptyStateForTab(tab);
            if (tab == ActionBarAdapter.TabState.GROUPS) {
                mGroupsFragment.setAddAccountsVisibility(false);
            }
            return;
        }

        // for the tablet...

        // If in search mode, we use the all list + contact details to show the result.
        if (mActionBarAdapter.isSearchMode()) {
            tab = ActionBarAdapter.TabState.ALL;
        }
        switch (tab) {
            case ActionBarAdapter.TabState.FAVORITES:
                mFavoritesView.setVisibility(View.VISIBLE);
                mBrowserView.setVisibility(View.GONE);
                mGroupDetailsView.setVisibility(View.GONE);
                mContactDetailsView.setVisibility(View.GONE);
                break;
            case ActionBarAdapter.TabState.GROUPS:
                mFavoritesView.setVisibility(View.GONE);
                mBrowserView.setVisibility(View.VISIBLE);
                mGroupDetailsView.setVisibility(View.VISIBLE);
                mContactDetailsView.setVisibility(View.GONE);
                mGroupsFragment.setAddAccountsVisibility(false);
                break;
            case ActionBarAdapter.TabState.ALL:
                mFavoritesView.setVisibility(View.GONE);
                mBrowserView.setVisibility(View.VISIBLE);
                mContactDetailsView.setVisibility(View.VISIBLE);
                mGroupDetailsView.setVisibility(View.GONE);
                break;
        }
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();

        // Note mContactDetailLoaderFragment is an invisible fragment, but we still have to show/
        // hide it so its options menu will be shown/hidden.
        switch (tab) {
            case ActionBarAdapter.TabState.FAVORITES:
                showFragment(ft, mFavoritesFragment);
                hideFragment(ft, mAllFragment);
                hideFragment(ft, mContactDetailLoaderFragment);
                hideFragment(ft, mContactDetailFragment);
                hideFragment(ft, mGroupsFragment);
                hideFragment(ft, mGroupDetailFragment);
                break;

            case ActionBarAdapter.TabState.ALL:
                hideFragment(ft, mFavoritesFragment);
                showFragment(ft, mAllFragment);
                showFragment(ft, mContactDetailLoaderFragment);
                showFragment(ft, mContactDetailFragment);
                hideFragment(ft, mGroupsFragment);
                hideFragment(ft, mGroupDetailFragment);
                break;

            case ActionBarAdapter.TabState.GROUPS:
                hideFragment(ft, mFavoritesFragment);
                hideFragment(ft, mAllFragment);
                hideFragment(ft, mContactDetailLoaderFragment);
                hideFragment(ft, mContactDetailFragment);
                showFragment(ft, mGroupsFragment);
                showFragment(ft, mGroupDetailFragment);
                break;
        }
        if (!ft.isEmpty()) {
            ft.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
            // When switching tabs, we need to invalidate options menu, but executing a
            // fragment transaction does it implicitly.  We don't have to call invalidateOptionsMenu
            // manually.
        }
        showEmptyStateForTab(tab);
    }

    @Override
    public void onContactListFilterChanged() {
        if (mAllFragment == null || !mAllFragment.isAdded()) {
            return;
        }
        mAllFragment.setFilter(mContactListFilterController.getFilter());
        invalidateOptionsMenuIfNeeded();
    }

    private void setupGroupDetailFragment(Uri groupUri) {
        // If we are switching from one group to another, do a cross-fade
        if (mGroupDetailFragment != null && mGroupDetailFragment.getGroupUri() != null &&
                !UriUtils.areEqual(mGroupDetailFragment.getGroupUri(), groupUri)) {
            mGroupDetailsView.startMaskTransition(false);
        }
        mGroupDetailFragment.loadGroup(groupUri);
        invalidateOptionsMenuIfNeeded();
    }

    private void setupContactDetailFragment(final Uri contactLookupUri) {
        mContactDetailLoaderFragment.loadUri(contactLookupUri);
        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                // Tell the fragments that we're in the search mode
                configureFragments(false /* from request */);
                updateFragmentsVisibility();
                showSlidingTabs(false);
                invalidateOptionsMenuLocal();
                break;

            case ActionBarAdapter.Listener.Action.STOP_SEARCH_MODE:
                setQueryTextToFragment("");
                updateFragmentsVisibility();
                showSlidingTabs(true);
                invalidateOptionsMenuLocal();
                break;

            case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
                final String queryString = mActionBarAdapter.getQueryString();
                setQueryTextToFragment(queryString);
//                updateDebugOptionsVisibility(ENABLE_DEBUG_OPTIONS_HIDDEN_CODE.equals(queryString));
                break;
            default:
                throw new IllegalStateException("Unknown ActionBarAdapter action: " + action);
        }
    }

    @Override
    public void onSelectedTabChanged() {
        updateFragmentsVisibility();
    }

    private void showEmptyStateForTab(int tab) {
        if (mContactsUnavailableFragment != null) {
            switch (tab) {
                case ActionBarAdapter.TabState.FAVORITES:
                    mContactsUnavailableFragment.setMessageText(R.string.listTotalAllContactsZeroStarred, -1);
                    break;
                case ActionBarAdapter.TabState.GROUPS:
                    mContactsUnavailableFragment.setMessageText(R.string.noGroups, -1);
                    break;
                case ActionBarAdapter.TabState.ALL:
                    mContactsUnavailableFragment.setMessageText(R.string.noContacts, -1);
                    break;
            }
        }
    }

    private void configureFragments(boolean fromRequest) {
        if (fromRequest) {
            ContactListFilter filter = null;
            int actionCode = mRequest.getActionCode();
            boolean searchMode = mRequest.isSearchMode();
            final int tabToOpen;
            switch (actionCode) {
//                case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
//                    filter = ContactListFilter.createFilterWithType(
//                            ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
//                    tabToOpen = TabState.ALL;
//                    break;
//
//                case ContactsRequest.ACTION_FREQUENT:
//                case ContactsRequest.ACTION_STREQUENT:
                case ContactsRequest.ACTION_STARRED:
                    tabToOpen = ActionBarAdapter.TabState.FAVORITES;
                    break;

                case ContactsRequest.ACTION_VIEW_CONTACT:
                    // We redirect this intent to the detail activity on 1-pane, so we don't get
                    // here.  It's only for 2-pane.
                    Uri currentlyLoadedContactUri = mContactDetailFragment.getUri();
                    if (currentlyLoadedContactUri != null
                            && !mRequest.getContactUri().equals(currentlyLoadedContactUri)) {
                        mContactDetailsView.setMaskVisibility(true);
                    }
                    tabToOpen = ActionBarAdapter.TabState.ALL;
                    break;
//                case ContactsRequest.ACTION_GROUP:
//                    tabToOpen = TabState.GROUPS;
//                    break;
                default:
                    tabToOpen = -1;
                    break;
            }
            if (tabToOpen != -1) {
                mActionBarAdapter.setCurrentTab(tabToOpen);
            }

            if (filter != null) {
                mContactListFilterController.setContactListFilter(filter, false);
                searchMode = false;
            }

            if (mRequest.getContactUri() != null) {
                searchMode = false;
            }

            mActionBarAdapter.setSearchMode(searchMode);
            configureContactListFragmentForRequest();
        }
        configureContactListFragment();
        configureGroupListFragment();

        invalidateOptionsMenuIfNeeded();
    }

    private void configureContactListFragmentForRequest() {
        Uri contactUri = mRequest.getContactUri();
        if (contactUri != null) {
            // For an incoming request, explicitly require a selection if we are on 2-pane UI,
            // (i.e. even if we view the same selected contact, the contact may no longer be
            // in the list, so we must refresh the list).
            if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
                mAllFragment.setSelectionRequired(true);
            }
            mAllFragment.setSelectedContactUri(contactUri);
        }

        mAllFragment.setFilter(mContactListFilterController.getFilter());
        setQueryTextToFragment(mActionBarAdapter.getQueryString());

        if (mRequest.isDirectorySearchEnabled()) {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        }
        else {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
        }
    }

    private void configureContactListFragment() {
        // Filter may be changed when this Activity is in background.
        mAllFragment.setFilter(mContactListFilterController.getFilter());

        final boolean useTwoPane = PhoneCapabilityTester.isUsingTwoPanes(this);
        mAllFragment.setVerticalScrollbarPosition(useTwoPane ? SCROLLBAR_POSITION_LEFT : SCROLLBAR_POSITION_RIGHT);
        mAllFragment.setSelectionVisible(useTwoPane);
        mAllFragment.setQuickContactEnabled(!useTwoPane);
    }

    private void configureGroupListFragment() {
        final boolean useTwoPane = PhoneCapabilityTester.isUsingTwoPanes(this);
        mGroupsFragment.setVerticalScrollbarPosition(useTwoPane ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        mGroupsFragment.setSelectionVisible(useTwoPane);
    }


    private void invalidateOptionsMenuLocal() {
       invalidateOptionsMenu();
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (isOptionsMenuChanged()) {
            invalidateOptionsMenuLocal();
        }
    }

    @Override
    public void onProviderStatusChange() {
        updateViewConfiguration(false);
    }

    private void updateViewConfiguration(boolean forceUpdate) {
        ProviderStatusWatcher.Status providerStatus = mProviderStatusWatcher.getProviderStatus();

        if (!forceUpdate && (mProviderStatus != null) && (providerStatus.status == mProviderStatus.status))
            return;

        mProviderStatus = providerStatus;

        View contactsUnavailableView = findViewById(R.id.contacts_unavailable_view);
        View mainView = findViewById(R.id.main_view);

//        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(getBaseContext());
//        boolean dbReady = db.isRegisteredWithKeyManager() && db.isReady();
//        if (!dbReady) {
//            startActivityForResult(KeyManagerSupport.getKeyManagerReadyIntent(), KEY_MANAGER_READY);
//        }
        if (mProviderStatus.status == ProviderStatus.STATUS_NORMAL) {
            contactsUnavailableView.setVisibility(View.GONE);

            if (mainView != null) {
                mainView.setVisibility(View.VISIBLE);
            }
            if (mAllFragment != null) {
                mAllFragment.setEnabled(true);
            }
            mFavoritesFragment.setDisplayEmpty(true);
            mContactsUnavailableFragment = null;
        }
        else {

            // Otherwise, continue setting up the page so that the user can still use the app
            // without an account.
            if (mAllFragment != null) {
                mAllFragment.setEnabled(false);
            }
            if (mContactsUnavailableFragment == null) {
                mContactsUnavailableFragment = new ContactsUnavailableFragment();
                mContactsUnavailableFragment.setOnContactsUnavailableActionListener(new ContactsUnavailableFragmentListener());
                getFragmentManager().beginTransaction()
                        .replace(R.id.contacts_unavailable_container, mContactsUnavailableFragment)
                        .commitAllowingStateLoss();
            }
            mContactsUnavailableFragment.updateStatus(mProviderStatus);

            // Show the contactsUnavailableView, and hide the mTabPager so that we don't
            // see it sliding in underneath the contactsUnavailableView at the edges.
            contactsUnavailableView.setVisibility(View.VISIBLE);

            if (mainView != null) {
                mainView.setVisibility(View.INVISIBLE);
            }
            showEmptyStateForTab(mActionBarAdapter.getCurrentTab());
        }
        invalidateOptionsMenuIfNeeded();
    }

    public boolean isOptionsMenuChanged() {
        if (mOptionsMenuContactsAvailable != areContactsAvailable()) {
            return true;
        }

        if (mAllFragment != null && mAllFragment.isOptionsMenuChanged()) {
            return true;
        }

        if (mContactDetailLoaderFragment != null && mContactDetailLoaderFragment.isOptionsMenuChanged()) {
            return true;
        }

        return mGroupDetailFragment != null && mGroupDetailFragment.isOptionsMenuChanged();

    }

    private void setQueryTextToFragment(String query) {
        mAllFragment.setQueryString(query, true);
        mAllFragment.setVisibleScrollbarEnabled(!mAllFragment.isSearchMode());
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mActionBarAdapter.onSaveInstanceState(outState);
        if (mContactDetailLayoutController != null) {
            mContactDetailLayoutController.onSaveInstanceState(outState);
        }

        // Clear the listener to make sure we don't get callbacks after onSaveInstanceState,
        // in order to avoid doing fragment transactions after it.
        // TODO Figure out a better way to deal with the issue.
        mActionBarAdapter.setListener(null);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // In our own lifecycle, the focus is saved and restore but later taken away by the
        // ViewPager. As a hack, we force focus on the SearchView if we know that we are searching.
        // This fixes the keyboard going away on screen rotation
        if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setFocusOnSearchView();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case SUBACTIVITY_NEW_CONTACT:
            case SUBACTIVITY_EDIT_CONTACT: {
                if (resultCode == RESULT_OK && PhoneCapabilityTester.isUsingTwoPanes(this)) {
                    mRequest.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
                    mAllFragment.setSelectionRequired(true);
                    mAllFragment.setSelectedContactUri(data.getData());
                    // Suppress IME if in search mode
                    if (mActionBarAdapter != null) {
                        mActionBarAdapter.clearFocusOnSearchView();
                    }
                    // No need to change the contact filter
                    mCurrentFilterIsValid = true;
                }
                break;
            }

            case SUBACTIVITY_NEW_GROUP:
            case SUBACTIVITY_EDIT_GROUP: {
                if (resultCode == RESULT_OK && PhoneCapabilityTester.isUsingTwoPanes(this)) {
                    mRequest.setActionCode(ContactsRequest.ACTION_GROUP);
                    mGroupsFragment.setSelectedUri(data.getData());
                }
                break;
            }

            // TODO: Using the new startActivityWithResultFromFragment API this should not be needed anymore
            case ScContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
                if (resultCode == RESULT_OK) {
                    mAllFragment.onPickerResult(data);
                }
                break;

            case KEY_MANAGER_READY:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this, "SilentContacts: KeyManager request failed.", Toast.LENGTH_SHORT).show();
                    finish();
                }
                else {
                    keyManagerChecked();
                }
                break;
        }
    }


    /**
     * Convenient version of {@link #findViewById(int)}, which throws
     * an exception if the view doesn't exist.
     */
    @SuppressWarnings("unchecked")
    public <T extends View> T getView(int id) {
        T result = (T)findViewById(id);
        if (result == null) {
            throw new IllegalArgumentException("view 0x" + Integer.toHexString(id) + " doesn't exist");
        }
        return result;
    }

    /**
     * Convenient version of {@link android.support.v4.app.FragmentManager#findFragmentById(int)}, which throws
     * an exception if the fragment doesn't exist.
     */
    @SuppressWarnings("unchecked")
    public <T extends Fragment> T getFragment(int id) {
        T result = (T)getFragmentManager().findFragmentById(id);
        if (result == null) {
            throw new IllegalArgumentException("fragment 0x" + Integer.toHexString(id) + " doesn't exist");
        }
        return result;
    }

    protected static void showFragment(FragmentTransaction ft, Fragment f) {
        if ((f != null) && f.isHidden()) ft.show(f);
    }

    protected static void hideFragment(FragmentTransaction ft, Fragment f) {
        if ((f != null) && !f.isHidden()) ft.hide(f);
    }

    private class TabPagerListener implements ViewPager.OnPageChangeListener {

        // This package-protected constructor is here because of a possible compiler bug.
        // PeopleActivity$1.class should be generated due to the private outer/inner class access
        // needed here.  But for some reason, PeopleActivity$1.class is missing.
        // Since $1 class is needed as a jvm work around to get access to the inner class,
        // changing the constructor to package-protected or public will solve the problem.
        // To verify whether $1 class is needed, javap PeopleActivity$TabPagerListener and look for
        // references to PeopleActivity$1.
        //
        // When the constructor is private and PeopleActivity$1.class is missing, proguard will
        // correctly catch this and throw warnings and error out the build on user/userdebug builds.
        //
        // All private inner classes below also need this fix.
        TabPagerListener() {}

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            // Make sure not in the search mode, in which case position != TabState.ordinal().
            if (!mTabPagerAdapter.isSearchMode()) {
                mActionBarAdapter.setCurrentTab(position, false);
                showEmptyStateForTab(position);
                if (position == ActionBarAdapter.TabState.GROUPS) {
                    mGroupsFragment.setAddAccountsVisibility(false);
                }
                invalidateOptionsMenuLocal();
            }
        }
    }

    /**
     * Adapter for the {@link android.support.v4.view.ViewPager}.
     *
     * {@link #instantiateItem} returns existing fragments, and {@link #instantiateItem}/
     * {@link #destroyItem} show/hide fragments instead of attaching/detaching.
     *
     * In search mode, we always show the "all" fragment, and disable the swipe.  We change the
     * number of items to 1 to disable the swipe.
     *
     * TODO figure out a more straight way to disable swipe.
     */
    private class TabPagerAdapter extends PagerAdapter {
        private final FragmentManager mFragmentManager;
        private FragmentTransaction mCurTransaction = null;

        private boolean mTabPagerAdapterSearchMode;

        private Fragment mCurrentPrimaryItem;

        public TabPagerAdapter() {
            mFragmentManager = getFragmentManager();
        }

        public boolean isSearchMode() {
            return mTabPagerAdapterSearchMode;
        }

        public void setSearchMode(boolean searchMode) {
            if (searchMode == mTabPagerAdapterSearchMode) {
                return;
            }
            mTabPagerAdapterSearchMode = searchMode;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabPagerAdapterSearchMode ? 1 : ActionBarAdapter.TabState.COUNT;
        }

        /** Gets called when the number of items changes. */
        @Override
        public int getItemPosition(Object object) {
            if (mTabPagerAdapterSearchMode) {
                if (object == mAllFragment) {
                    return 0; // Only 1 page in search mode
                }
            } else {
                if (object == mFavoritesFragment) {
                    return ActionBarAdapter.TabState.FAVORITES;
                }
                if (object == mAllFragment) {
                    return ActionBarAdapter.TabState.ALL;
                }
                if (object == mGroupsFragment) {
                    return ActionBarAdapter.TabState.GROUPS;
                }
            }
            return POSITION_NONE;
        }

        @Override
        public void startUpdate(ViewGroup container) {
        }

        private Fragment getFragment(int position) {
            if (mTabPagerAdapterSearchMode) {
                if (position != 0) {
                    // This has only been observed in monkey tests.
                    // Let's log this issue, but not crash
                    Log.w(TAG, "Request fragment at position=" + position + ", even though we are in search mode");
                }
                return mAllFragment;
            } else {
                if (position == ActionBarAdapter.TabState.FAVORITES) {
                    return mFavoritesFragment;
                }
                else if (position == ActionBarAdapter.TabState.ALL) {
                    return mAllFragment;
                }
                else if (position == ActionBarAdapter.TabState.GROUPS) {
                    return mGroupsFragment;
                }
            }
            throw new IllegalArgumentException("position: " + position);
        }

        @Override
        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
        public Object instantiateItem(ViewGroup container, int position) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            Fragment f = getFragment(position);
            mCurTransaction.show(f);

            // Non primary pages are not visible.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                f.setUserVisibleHint(f == mCurrentPrimaryItem);
            return f;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            mCurTransaction.hide((Fragment) object);
        }

        @Override
        public void finishUpdate(ViewGroup container) {
            if (mCurTransaction != null) {
                mCurTransaction.commitAllowingStateLoss();
                mCurTransaction = null;
                mFragmentManager.executePendingTransactions();
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return ((Fragment) object).getView() == view;
        }

        @Override
        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            Fragment fragment = (Fragment) object;
            if (mCurrentPrimaryItem != fragment) {
                if (mCurrentPrimaryItem != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    mCurrentPrimaryItem.setUserVisibleHint(false);
                }
                if (fragment != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    fragment.setUserVisibleHint(true);
                }
                mCurrentPrimaryItem = fragment;
            }
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
        }
    }

    private class ContactDetailLoaderFragmentListener implements ContactLoaderFragmentListener {
        ContactDetailLoaderFragmentListener() {}

        @Override
        public void onContactNotFound() {
            // Nothing needs to be done here
        }

        @Override
        public void onDetailsLoaded(final Contact result) {
            if (result == null) {
                // Nothing is loaded. Show empty state.
                mContactDetailLayoutController.showEmptyState();
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
                    mContactDetailLayoutController.setContactData(result);
                }
            });
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.putExtra(ScContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            startActivityForResult(intent, SUBACTIVITY_EDIT_CONTACT);
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ScContactsMainActivity.this, contactUri, false);
        }
    }

    public class ContactDetailFragmentListener implements ContactDetailFragment.Listener {
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
            Toast.makeText(ScContactsMainActivity.this, R.string.toast_making_personal_copy, Toast.LENGTH_LONG).show();
            Intent serviceIntent;
            if (!DialerActivity.useBpForwarder()) {
                serviceIntent = ScContactSaveService.createNewRawContactIntent(
                        ScContactsMainActivity.this, values,
                        ScContactsMainActivity.class, Intent.ACTION_VIEW, null);
            }
            else {
                ComponentName cm = new ComponentName(ScContactsMainActivity.this,
                        "com.silentcircle.contacts.activities.ScContactsMainActivityForwarder");
                serviceIntent = ScContactSaveService.createNewRawContactIntent(
                        ScContactsMainActivity.this, values,
                        null, Intent.ACTION_VIEW, cm);
            }
            try {
                startActivity(serviceIntent);
            } catch (Exception ignored) { }
        }
    }

    private class GroupDetailFragmentListener implements GroupDetailFragment.Listener {

        GroupDetailFragmentListener() {}

        @Override
        public void onGroupSizeUpdated(String size) {
            // Nothing needs to be done here because the size will be displayed in the detail
            // fragment
        }

        @Override
        public void onGroupTitleUpdated(String title) {
            // Nothing needs to be done here because the title will be displayed in the detail
            // fragment
        }

        @Override
        public void onEditRequested(Uri groupUri) {
            final Intent intent = new Intent(ScContactsMainActivity.this, GroupEditorActivity.class);
            intent.setData(groupUri);
            intent.setAction(Intent.ACTION_EDIT);
            startActivityForResult(intent, SUBACTIVITY_EDIT_GROUP);
        }

        @Override
        public void onContactSelected(Uri contactUri) {
            // Nothing needs to be done here because either quickcontact will be displayed
            // or activity will take care of selection
        }
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {

        ContactBrowserActionListener() {}

        @Override
        public void onSelectionChange() {
            if (PhoneCapabilityTester.isUsingTwoPanes(ScContactsMainActivity.this)) {
                setupContactDetailFragment(mAllFragment.getSelectedContactUri());
            }
        }

        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            if (PhoneCapabilityTester.isUsingTwoPanes(ScContactsMainActivity.this)) {
                setupContactDetailFragment(contactLookupUri);
            }
            else {
                Intent intent = new Intent(Intent.ACTION_VIEW, contactLookupUri);
                startActivity(intent);
            }
        }

        @Override
        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        @Override
        public void onEditContactAction(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            intent.putExtra(ScContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            startActivityForResult(intent, SUBACTIVITY_EDIT_CONTACT);
        }

        @Override
        public void onAddToFavoritesAction(Uri contactUri) {
            Log.i(TAG, "++ missing onAddToFavorites() - uri: " + contactUri);
//            ContentValues values = new ContentValues(1);
//            values.put(Contacts.STARRED, 1);
//            getContentResolver().update(contactUri, values, null, null);
        }

        @Override
        public void onRemoveFromFavoritesAction(Uri contactUri) {
            Log.i(TAG, "++ missing onRemoveFromFavorites() - uri: " + contactUri);
//            ContentValues values = new ContentValues(1);
//            values.put(Contacts.STARRED, 0);
//            getContentResolver().update(contactUri, values, null, null);
        }

        @Override
        public void onCallContactAction(Uri contactUri) {
            Log.i(TAG, "++ missing onCallContactAction() - uri: " + contactUri);
//            PhoneNumberInteraction.startInteractionForPhoneCall(PeopleActivity.this, contactUri);
        }

        @Override
        public void onSmsContactAction(Uri contactUri) {
            Log.i(TAG, "++ missing onSmsContactAction() - uri: " + contactUri);
//            PhoneNumberInteraction.startInteractionForTextMessage(PeopleActivity.this, contactUri);
        }

        @Override
        public void onDeleteContactAction(Uri contactUri) {
            ContactDeletionInteraction.start(ScContactsMainActivity.this, contactUri, false);
        }

        @Override
        public void onFinishAction() {
            onBackPressed();
        }

        @Override
        public void onInvalidSelection() {
            ContactListFilter filter;
           ContactListFilter currentFilter = mAllFragment.getFilter();
            if (currentFilter != null && currentFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                filter = ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_DEFAULT);
                mAllFragment.setFilter(filter);
            }
            else {
                filter = ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
                mAllFragment.setFilter(filter, false);
            }
            mContactListFilterController.setContactListFilter(filter, true);
        }
    }

    private final class GroupBrowserActionListener implements GroupBrowseListFragment.OnGroupBrowserActionListener {

        GroupBrowserActionListener() {}

        @Override
        public void onViewGroupAction(Uri groupUri) {
            if (PhoneCapabilityTester.isUsingTwoPanes(ScContactsMainActivity.this)) {
                setupGroupDetailFragment(groupUri);
            }
            else
            {
                Intent intent = new Intent(ScContactsMainActivity.this, GroupDetailActivity.class);
                intent.setData(groupUri);
                startActivity(intent);
            }
        }
    }

    private final class StrequentContactListFragmentListener implements ContactTileListFragment.Listener {
        StrequentContactListFragmentListener() {
        }

        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
//            if (PhoneCapabilityTester.isUsingTwoPanes(PeopleActivity.this)) {
//                QuickContact.showQuickContact(PeopleActivity.this, targetRect, contactUri, 0, null);
//            }
//            else 
            {
                startActivity(new Intent(Intent.ACTION_VIEW, contactUri));
            }
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            // No need to call phone number directly from People app.
            Log.w(TAG, "unexpected invocation of onCallNumberDirectly()");
        }
    }

    private class ContactsUnavailableFragmentListener implements
            ContactsUnavailableFragment.OnContactsUnavailableActionListener {

        ContactsUnavailableFragmentListener() {
        }

        @Override
        public void onCreateNewContactAction() {
            startActivity(new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI));
        }

        @Override
        public void onCreateNewProfileAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI);
            intent.putExtra(ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
            startActivity(intent);
        }

        @Override
        public void onImportContactsFromFileAction() {
            final boolean available = areContactsAvailable();
            // Call doImport directly because SCA does not support Import from SIM card
            if (!available)
                ImportExportDialogFragment.doImportFromSdCard(ScContactsMainActivity.this);
            else
                ImportExportDialogFragment.show(getFragmentManager(), available);
        }

        @Override
        public void onFreeInternalStorageAction() {
            startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        }
    }

    private void showAboutInfo(String msg) {
        InfoAboutDialog infoMsg = InfoAboutDialog.newInstance(msg);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, "InfoAboutDialog");
    }

    public static class InfoAboutDialog extends DialogFragment {
        private static String MESSAGE_ID = "messageId";

        public static InfoAboutDialog newInstance(String msg) {
            InfoAboutDialog f = new InfoAboutDialog();

            Bundle args = new Bundle();
            args.putString(MESSAGE_ID, msg);
            f.setArguments(args);

            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.information_dialog)
                    .setMessage(getArguments().getString(MESSAGE_ID))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
