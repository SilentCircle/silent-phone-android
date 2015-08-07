/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.silentcircle.contacts.calllognew;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;

import com.silentcircle.common.interactions.TouchPointManager;
import com.silentcircle.contacts.widget.SlidingTabLayout;
import com.silentcircle.silentcontacts2.ScCallLog.ScCalls;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

public class CallLogActivity extends ActionBarActivity implements CallLogQueryHandler.Listener {
    private Handler mHandler;
    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private CallLogFragment mAllCallsFragment;
    private CallLogFragment mMissedCallsFragment;
    private CallLogFragment mVoicemailFragment;

    private SlidingTabLayout mSlidingTabLayout;


    private static final int WAIT_FOR_VOICEMAIL_PROVIDER_TIMEOUT_MS = 300;
    private boolean mSwitchToVoicemailTab;

    private String[] mTabTitles;

    private static final int TAB_INDEX_ALL = 0;
    private static final int TAB_INDEX_MISSED = 1;
    private static final int TAB_INDEX_VOICEMAIL = 2;

    private static final int TAB_INDEX_COUNT_DEFAULT = 2;
    private static final int TAB_INDEX_COUNT_WITH_VOICEMAIL = 3;

//    private boolean mHasActiveVoicemailProvider;

//    private final Runnable mWaitForVoicemailTimeoutRunnable = new Runnable() {
//        @Override
//        public void run() {
//            mViewPagerTabs.setViewPager(mViewPager);
//            mViewPager.setCurrentItem(TAB_INDEX_ALL);
//            mSwitchToVoicemailTab = false;
//        }
//    };

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Bundle args = new Bundle();
            switch (position) {
                case TAB_INDEX_ALL:
                    args.putInt(CallLogFragment.FILTER_TYPE, CallLogQueryHandler.CALL_TYPE_ALL);
                    mAllCallsFragment = CallLogFragment.newInstance(args);
                    return mAllCallsFragment;
                case TAB_INDEX_MISSED:
                    args.putInt(CallLogFragment.FILTER_TYPE, ScCalls.MISSED_TYPE);
                    mMissedCallsFragment = CallLogFragment.newInstance(args);
                    return mMissedCallsFragment;
//                case TAB_INDEX_VOICEMAIL:
//                    mVoicemailFragment = new CallLogFragment(ScCalls.VOICEMAIL_TYPE);
//                    return mVoicemailFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }

        @Override
        public int getCount() {
            return /*mHasActiveVoicemailProvider ? TAB_INDEX_COUNT_WITH_VOICEMAIL : */
                    TAB_INDEX_COUNT_DEFAULT;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            TouchPointManager.getInstance().setPoint((int) ev.getRawX(), (int) ev.getRawY());
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();

        setContentView(R.layout.call_log_activity_new);
//        getWindow().setBackgroundDrawable(null);          // Otherwise screen is garbled

        final Toolbar toolbar = (Toolbar)findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setElevation(0);

        int startingTab = TAB_INDEX_ALL;
        final Intent intent = getIntent();
//        if (intent != null) {
//            final int callType = intent.getIntExtra(ScCalls.EXTRA_CALL_TYPE_FILTER, -1);
//            if (callType == ScCalls.MISSED_TYPE) {
//                startingTab = TAB_INDEX_MISSED;
//            }
//            else if (callType == ScCalls.VOICEMAIL_TYPE) {
//                startingTab = TAB_INDEX_VOICEMAIL;
//            }
//        }

        mTabTitles = new String[TAB_INDEX_COUNT_WITH_VOICEMAIL];
        mTabTitles[0] = getString(R.string.call_log_all_title);
        mTabTitles[1] = getString(R.string.call_log_missed_title);
//        mTabTitles[2] = getString(R.string.call_log_voicemail_title);

        mViewPager = (ViewPager)findViewById(R.id.call_log_pager);
        mViewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);

        // BEGIN_INCLUDE (setup_sliding tab layout)
        // Give the SlidingTabLayout the ViewPager, this must be done AFTER the ViewPager has had
        // it's PagerAdapter set.
        mSlidingTabLayout = (SlidingTabLayout)findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setCustomTabView(R.layout.tab_text, R.id.text_tab);
        // END_INCLUDE (setup_sliding tab layout)

//        if (startingTab == TAB_INDEX_VOICEMAIL) {
//            // The addition of the voicemail tab is an asynchronous process, so wait till the tab
//            // is added, before attempting to switch to it. If the querying of CP2 for voicemail
//            // providers takes too long, give up and show the first tab instead.
//            mSwitchToVoicemailTab = true;
//            mHandler.postDelayed(mWaitForVoicemailTimeoutRunnable,
//                    WAIT_FOR_VOICEMAIL_PROVIDER_TIMEOUT_MS);
//        } 
//        else {
            mSlidingTabLayout.setViewPager(mViewPager);
            mViewPager.setCurrentItem(startingTab);
//        }

//        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CallLogQueryHandler callLogQueryHandler =
                new CallLogQueryHandler(this.getContentResolver(), this);
//        callLogQueryHandler.fetchVoicemailStatus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.call_log_options, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);

        // If onPrepareOptionsMenu is called before fragments loaded. Don't do anything.
        if (mAllCallsFragment != null && itemDeleteAll != null) {
            final CallLogAdapter adapter = mAllCallsFragment.getAdapter();
            itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                final Intent intent = new Intent(this, DialerActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            case R.id.delete_all:
                ClearCallLogDialog.show(getFragmentManager());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
//        if (this.isFinishing()) {
//            return;
//        }
//
//        mHandler.removeCallbacks(mWaitForVoicemailTimeoutRunnable);
//        // Update mHasActiveVoicemailProvider, which controls the number of tabs displayed.
//        int activeSources = mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);
//        if (activeSources > 0 != mHasActiveVoicemailProvider) {
//            mHasActiveVoicemailProvider = activeSources > 0;
//            mViewPagerAdapter.notifyDataSetChanged();
//            mViewPagerTabs.setViewPager(mViewPager);
//            if (mSwitchToVoicemailTab) {
//                mViewPager.setCurrentItem(TAB_INDEX_VOICEMAIL, false);
//            }
//        } else if (mSwitchToVoicemailTab) {
//            // The voicemail tab was requested, but it does not exist because there are no
//            // voicemail sources. Just fallback to the first item instead.
//            mViewPagerTabs.setViewPager(mViewPager);
//        }
    }

    @Override
    public boolean onCallsFetched(Cursor statusCursor) {
        // Return false; did not take ownership of cursor
        return false;
    }
}
