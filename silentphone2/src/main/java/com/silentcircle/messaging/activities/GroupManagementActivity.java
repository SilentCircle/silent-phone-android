/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.activities;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.silentcircle.contacts.list.ScDirectoryLoader;
import com.silentcircle.messaging.fragments.CreateGroupFragment;
import com.silentcircle.messaging.fragments.GroupListFragment;
import com.silentcircle.messaging.fragments.GroupManagementFragment;
import com.silentcircle.messaging.fragments.SearchAgainFragment;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.list.OnListFragmentScrolledListener;
import com.silentcircle.silentphone2.list.SearchFragment;
import com.silentcircle.silentphone2.passcode.AppLifecycleNotifierBaseActivity;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SearchEditTextLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Activity to show group list, details fragments and group creation wizard.
 */
public class GroupManagementActivity extends AppLifecycleNotifierBaseActivity implements
        OnListFragmentScrolledListener, SearchFragment.HostInterface {

    @SuppressWarnings("unused")
    private static final String TAG = GroupManagementActivity.class.getSimpleName();

    public static final String GROUP_ID = "com.silentcircle.messaging.extra.GROUP_ID";
    public static final String GROUP_MEMBERS = "com.silentcircle.messaging.extra.GROUP_MEMBERS";
    public static final String GROUP_NAME = "com.silentcircle.messaging.extra.GROUP_NAME";
    public static final String TASK = "com.silentcircle.messaging.extra.TASK";
    public static final String TASK_STACK = "com.silentcircle.messaging.extra.TASK_STACK";
    public static final String IN_SEARCH_VIEW = "com.silentcircle.messaging.extra.IN_SEARCH";
    public static final String CLOSE_ON_LEAVE_GROUP = "com.silentcircle.messaging.extra.CLOSE_ON_LEAVE_GROUP";

    /*
     * Minimum length of query string to initiate directory search,
     * same as {@link com.silentcircle.contacts.list.ScDirectoryLoader#MIN_SEARCH_LENGTH}
     */
    public static final int MIN_SEARCH_LENGTH = ScDirectoryLoader.MIN_SEARCH_LENGTH;

    public static final int TASK_LIST = 0;
    public static final int TASK_DETAILS = 100;
    public static final int TASK_DETAILS_FROM_CONVERSATION = 110;
    public static final int TASK_CREATE_NEW = 200;
    public static final int TASK_ADD_PARTICIPANTS = 300;

    public interface OnWizardStateChangeListener {

        boolean onExitView();

        void onVisibilityRestored();
    }

    private FragmentManager.OnBackStackChangedListener mOnBackStackChangeListener =
            new FragmentManager.OnBackStackChangedListener() {

                @Override
                public void onBackStackChanged() {
                    List<Fragment> fragments = getActiveFragments();
                    if (fragments != null && fragments.size() > 0) {
                        Fragment fragment = fragments.get(fragments.size() - 1);
                        if (fragment instanceof OnWizardStateChangeListener) {
                            ((OnWizardStateChangeListener) fragment).onVisibilityRestored();
                        }
                    }
                }
            };

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                // If the query hasn't changed (perhaps due to activity being destroyed
                // and restored, or user launching the same DIAL intent twice), then there is
                // no need to do anything here.
                return;
            }
            mSearchQuery = newText;

            if (mSearchFragment != null && mSearchFragment.isVisible()) {
                mSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (editable.length() < MIN_SEARCH_LENGTH) {
                return;
            }

            Utilities.formatNumberAssistedInput(mSearchView);
        }
    };

    private final TextView.OnEditorActionListener mPhoneSearchQueryEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            boolean consumed = false;
            if (actionId == EditorInfo.IME_ACTION_GO && mSearchFragment != null) {
                List<String> uuids = mSearchFragment.getSelectedUuids();
                final Intent intent = new Intent(GroupManagementActivity.this, GroupManagementActivity.class);
                intent.putExtra(GroupManagementActivity.TASK, GroupManagementActivity.TASK_CREATE_NEW);
                intent.putExtra(GroupManagementActivity.GROUP_MEMBERS,
                        uuids.toArray(new CharSequence[uuids.size()]));
                startActivity(intent);
                consumed = true;
            }
            return consumed;
        }
    };

    private Toolbar mToolbar;
    private EditText mSearchView;
    private SearchAgainFragment mSearchFragment;
    private boolean mInSearchView;

    private int mStartedWithTask;
    private String mSearchQuery;
    private String mGroupId;
    private String mGroupName;
    private CharSequence[] mGroupMembers;

    private Stack<Integer> mTaskStack = new Stack<>();

    /*
     * List to hold references of fragments. Support fragment manager is not used as SearchFragment
     * is not a support fragment.
     */
    private List<WeakReference<Fragment>> mFragmentList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utilities.setTheme(this);
        super.onCreate(savedInstanceState);
        setTitle(R.string.group_messaging_group_members);

        if (savedInstanceState != null) {
            mStartedWithTask = savedInstanceState.getInt(TASK, TASK_LIST);
            mGroupId = savedInstanceState.getString(GROUP_ID);
            mGroupMembers = savedInstanceState.getCharSequenceArray(GROUP_MEMBERS);
            mInSearchView = savedInstanceState.getBoolean(IN_SEARCH_VIEW);
            mGroupName = savedInstanceState.getString(GROUP_NAME);
            List<Integer> tasks = savedInstanceState.getIntegerArrayList(TASK_STACK);
            if (tasks != null) {
                mTaskStack.addAll(tasks);
            }
        } else {
            Intent intent = getIntent();
            mStartedWithTask = intent.getIntExtra(TASK, TASK_LIST);
            mGroupId = intent.getStringExtra(GROUP_ID);
            mGroupMembers = intent.getCharSequenceArrayExtra(GROUP_MEMBERS);
            mGroupName = intent.getStringExtra(GROUP_NAME);
            mTaskStack.push(mStartedWithTask);
        }

        setContentView(R.layout.activity_group_management);

        restoreActionBar();

        getFragmentManager().addOnBackStackChangedListener(mOnBackStackChangeListener);
        showFragment(mStartedWithTask, false);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (isInSearchView()) {
            closeActionBarQueryField();
        }

        mGroupId = intent.getStringExtra(GROUP_ID);
        mGroupMembers = intent.getCharSequenceArrayExtra(GROUP_MEMBERS);
        mGroupName = intent.getStringExtra(GROUP_NAME);
        mStartedWithTask = intent.getIntExtra(TASK, TASK_LIST);
        mTaskStack.push(mStartedWithTask);
        showFragment(mStartedWithTask, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean consumed = false;
        if (item.getItemId() == android.R.id.home) {
            consumed = onExitFragment();
            if (!consumed) {
                consumed = true;
                onBackPressed();
            }
        }
        return consumed || super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!onExitFragment()) {
            super.onBackPressed();
        }

        if (!mTaskStack.empty()) {
            mTaskStack.pop();
            if (!mTaskStack.empty()) {
                mStartedWithTask = mTaskStack.peek();
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(TASK, mStartedWithTask);
        outState.putIntegerArrayList(TASK_STACK, new ArrayList<>(mTaskStack));
        outState.putString(GROUP_ID, mGroupId);
        outState.putCharSequenceArray(GROUP_MEMBERS, mGroupMembers);
        outState.putString(GROUP_NAME, mGroupName);
        outState.putBoolean(IN_SEARCH_VIEW, mInSearchView);
    }

    @Override
    protected void onDestroy() {
        FragmentManager manager = getFragmentManager();
        if (manager != null) {
            manager.removeOnBackStackChangedListener(mOnBackStackChangeListener);
        }
        super.onDestroy();
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
    }

    @Override
    public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount,
        int totalItemCount) {
    }

    @Override
    public boolean isActionBarShowing() {
        return true;
    }

    @Override
    public boolean isDialpadShown() {
        return false;
    }

    @Override
    public int getActionBarHideOffset() {
        return getSupportActionBar().getHideOffset();
    }

    @Override
    public int getActionBarHeight() {
        return getResources().getDimensionPixelSize(R.dimen.action_bar_height_large);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        mFragmentList.add(new WeakReference<>(fragment));
    }

    public void setSubtitle(@NonNull CharSequence subTitle) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(subTitle);
        }
    }

    public boolean isInSearchView() {
        return mInSearchView;
    }

    public void openActionBarQueryField() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            ((SearchEditTextLayout) actionBar.getCustomView()).expand(false /* animate */, true /* requestFocus */);
            ((SearchEditTextLayout) actionBar.getCustomView()).showBackButton(false);
            ((SearchEditTextLayout) actionBar.getCustomView()).setIsDialpadEnabled(false);
            ((SearchEditTextLayout) actionBar.getCustomView()).keyboardLayout(false);
        }
        mInSearchView = true;
    }

    public void closeActionBarQueryField() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setDisplayShowCustomEnabled(false);
        SearchEditTextLayout seTextLayout = (SearchEditTextLayout) actionBar.getCustomView();
        if (seTextLayout.isExpanded()) {
            seTextLayout.collapse(false /* animate */);
        }
        if (seTextLayout.isFadedOut()) {
            seTextLayout.fadeIn();
        }
        mInSearchView = false;
    }

    private void showFragment(int task, boolean addToBackStack) {
        Bundle arguments = new Bundle();
        FragmentManager manager = getFragmentManager();
        Fragment fragment;
        String tag;
        boolean showExisting = false;
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (task == TASK_DETAILS || task == TASK_DETAILS_FROM_CONVERSATION) {
            tag = GroupManagementFragment.TAG_GROUP_MANAGEMENT_FRAGMENT;
            fragment = manager.findFragmentByTag(tag);
            if (fragment == null) {
                arguments.putString(GROUP_ID, mGroupId);
                arguments.putBoolean(CLOSE_ON_LEAVE_GROUP, task == TASK_DETAILS_FROM_CONVERSATION);
                fragment = GroupManagementFragment.newInstance(arguments);
            } else {
                ((GroupManagementFragment) fragment).setGroup(mGroupId);
                ((GroupManagementFragment) fragment).setCloseOnLeaveGroup(task == TASK_DETAILS_FROM_CONVERSATION);
                showExisting = true;
            }
        } else if (task == TASK_CREATE_NEW) {
            tag = CreateGroupFragment.TAG_CREATE_GROUP_FRAGMENT;
            fragment = manager.findFragmentByTag(tag);
            if (fragment == null) {
                arguments.putCharSequenceArray(GROUP_MEMBERS, mGroupMembers);
                arguments.putString(GROUP_NAME, mGroupName);
                fragment = CreateGroupFragment.newInstance(arguments);
            } else {
                ((CreateGroupFragment) fragment).addGroupMembers(mGroupMembers);
                ((CreateGroupFragment) fragment).setGroupName(mGroupName);
                showExisting = true;
            }
        } else if (task == TASK_ADD_PARTICIPANTS) {
            tag = SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT;
            fragment = manager.findFragmentByTag(tag);
            if (fragment == null) {
                arguments.putBoolean(Extra.IS_GROUP.getName(), true);

                mSearchFragment = new SearchAgainFragment();
                mSearchFragment.setShowScDirectoryOption(true);
                mSearchFragment.setShowEmptyListForNullQuery(false);
                mSearchFragment.setArguments(arguments);
                fragment = mSearchFragment;
            } else {
                showExisting = true;
                mSearchFragment = (SearchAgainFragment) fragment;
            }

            openActionBarQueryField();
        } else /* if (task == TASK_LIST) */ {
            tag = GroupListFragment.TAG_GROUP_LIST_FRAGMENT;
            fragment = manager.findFragmentByTag(tag);
            if (fragment == null) {
                fragment = GroupListFragment.newInstance(arguments);
            } else {
                showExisting = true;
            }
        }

        if (fragment != null) {
            if (showExisting) {
                if (SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT.equals(tag)) {
                    Fragment groupFragment =
                            manager.findFragmentByTag(CreateGroupFragment.TAG_CREATE_GROUP_FRAGMENT);
                    if (groupFragment != null) {
                        transaction.hide(groupFragment);
                    }
                }
                transaction.show(fragment);
            } else {
                if (addToBackStack) {
                    transaction.addToBackStack(fragment.getClass().getName());
                }
                transaction.add(R.id.group_container, fragment, tag);
            }
            transaction.commit();
        }
    }

    private void restoreActionBar() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setCustomView(R.layout.search_edittext);

            SearchEditTextLayout searchEditTextLayout =
                    (SearchEditTextLayout) actionBar.getCustomView();

            mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
            mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
            mSearchView.setOnEditorActionListener(mPhoneSearchQueryEditorActionListener);
            mSearchView.setHint(R.string.messaging_conversation_select_conversation_partner);
            mSearchView.setImeOptions(EditorInfo.IME_ACTION_GO);
        }
    }

   private List<Fragment> getActiveFragments() {
        ArrayList<Fragment> fragments = new ArrayList<>();
        for (WeakReference<Fragment> reference : mFragmentList) {
            Fragment fragment = reference.get();
            if (fragment != null) {
                if (fragment.isVisible()) {
                    fragments.add(fragment);
                }
            }
        }
        return fragments;
    }

    private boolean onExitFragment() {
        boolean consumed = false;
        // Support fragment manager would allow manager.getFragments();
        List<Fragment> fragments = getActiveFragments();
        if (fragments != null && fragments.size() > 0) {
            Fragment fragment = fragments.get(fragments.size() - 1);
            if (fragment instanceof OnWizardStateChangeListener) {
                consumed = ((OnWizardStateChangeListener) fragment).onExitView();
            }
        }
        return consumed;
    }

}