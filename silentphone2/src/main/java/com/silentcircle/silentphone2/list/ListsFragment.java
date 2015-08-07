/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.silentphone2.list;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.ObjectFactory;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.calllognew.ContactInfoHelper;
import com.silentcircle.contacts.calllognew.CallLogAdapter;
import com.silentcircle.contacts.calllognew.CallLogFragment;
import com.silentcircle.contacts.calllognew.CallLogQuery;
import com.silentcircle.contacts.calllognew.CallLogQueryHandler;
import com.silentcircle.contacts.widget.SlidingTabLayout;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.list.ShortcutCardsAdapter.SwipeableShortcutCard;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.OverlappingPaneLayout;

import java.util.ArrayList;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link com.silentcircle.silentphone2.list.ListsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link com.silentcircle.silentphone2.list.ListsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ListsFragment extends Fragment implements CallLogQueryHandler.Listener,
        CallLogAdapter.CallFetcher, ViewPager.OnPageChangeListener {

    private static final boolean DEBUG = false; // ConfigurationUtilities.mTrace;
    private static final String TAG = "ListsFragment";

    public static final int TAB_INDEX_SPEED_DIAL = 0;
    public static final int TAB_INDEX_RECENTS = 1;
    private static final int TAB_INDEX_ALL_CONTACTS = 2;

    private static final int TAB_INDEX_COUNT = 3;

    private static final int MAX_RECENTS_ENTRIES = 20;
    // Oldest recents entry to display is 2 weeks old.
    private static final long OLDEST_RECENTS_DATE = 1000L * 60 * 60 * 24 * 14;

    private static final String KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE = "key_last_dismissed_call_shortcut_date";

    private static final float REMOVE_VIEW_SHOWN_ALPHA = 0.5f;
    private static final float REMOVE_VIEW_HIDDEN_ALPHA = 1;

    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private String[] mTabTitles;
    private ListView mShortcutCardsListView;

    private CallLogAdapter mCallLogAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private ShortcutCardsAdapter mMergedAdapter;

    private RemoveView mRemoveView;
    private View mRemoveViewContent;

    private final ArrayList<ViewPager.OnPageChangeListener> mOnPageChangeListeners = new ArrayList<>();

    /**
     * Call shortcuts older than this date (persisted in shared preferences) will not show up in
     * at the top of the screen
     */
    private long mLastCallShortcutDate = 0;

    /**
     * The date of the current call shortcut that is showing on screen.
     */
    private long mCurrentCallShortcutDate = 0;


    private SpeedDialFragment mSpeedDialFragment;
    private CallLogFragment mRecentsFragment;
    private AllContactsFragment mAllContactsFragment;

    public interface HostInterface {
        public void showCallHistory();
        public int getActionBarHeight();
        @SuppressWarnings("unused")
        public void setActionBarHideOffset(int offset);
    }

    private final OverlappingPaneLayout.PanelSlideCallbacks mPanelSlideCallbacks = new OverlappingPaneLayout.PanelSlideCallbacks() {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {
            // For every 1 percent that the panel is slid upwards, clip 1 percent off the top
            // edge of the shortcut card, to achieve the animated effect of the shortcut card
            // being pushed out of view when the panel is slid upwards. slideOffset is 1 when
            // the shortcut card is fully exposed, and 0 when completely hidden.
            float ratioCardHidden = (1 - slideOffset);
            if (mShortcutCardsListView.getChildCount() > 0) {
                final SwipeableShortcutCard v = (SwipeableShortcutCard) mShortcutCardsListView.getChildAt(0);
                v.clipCard(ratioCardHidden);
            }
        }

        @Override
        public void onPanelOpened(View panel) {
            if (DEBUG) {
                Log.d(TAG, "onPanelOpened");
            }
        }

        @Override
        public void onPanelClosed(View panel) {
            if (DEBUG) {
                Log.d(TAG, "onPanelClosed");
            }
        }

        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void onPanelFlingReachesEdge(int velocityY) {
            if (DEBUG) {
                Log.d(TAG, "onPanelFling: " + velocityY);
            }
            if (getCurrentListView() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getCurrentListView().fling(velocityY);
            }
        }

        @Override
        public boolean isScrollableChildUnscrolled() {
            final AbsListView listView = getCurrentListView();
            return listView != null && (listView.getChildCount() == 0
                    || listView.getChildAt(0).getTop() == listView.getPaddingTop());
        }
    };


    public ListsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(), this, 1);
        final String currentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity()); //  GeoUtil.getCurrentCountryIso(getActivity());
        mCallLogAdapter = ObjectFactory.newCallLogAdapter(getActivity(), this,
                new ContactInfoHelper(getActivity(), currentCountryIso), null, null, 
                (OnPhoneNumberPickerActionListener)getActivity(), false);

        mMergedAdapter = new ShortcutCardsAdapter(getActivity(), this, mCallLogAdapter);

        mTabTitles = new String[TAB_INDEX_COUNT];
        mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
        mTabTitles[TAB_INDEX_RECENTS] = getResources().getString(R.string.tab_recents);
        mTabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
        mViewPager = (ViewPager) parentView.findViewById(R.id.lists_pager);
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(viewPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setCurrentItem(getRtlPosition(TAB_INDEX_SPEED_DIAL));

        // BEGIN_INCLUDE (setup_sliding tab layout)
        // Give the SlidingTabLayout the ViewPager, this must be done AFTER the ViewPager has had
        // it's PagerAdapter set.
        mSlidingTabLayout = (SlidingTabLayout)parentView.findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setCustomTabView(R.layout.tab_text, R.id.text_tab);
        mSlidingTabLayout.setViewPager(mViewPager);
        // END_INCLUDE (setup_sliding tab layout)

        mSlidingTabLayout.setOnPageChangeListener(this);

        mShortcutCardsListView = (ListView)parentView.findViewById(R.id.shortcut_card_list);
        mShortcutCardsListView.setAdapter(mMergedAdapter);

        mRemoveView = (RemoveView) parentView.findViewById(R.id.remove_view);
        mRemoveViewContent = parentView.findViewById(R.id.remove_view_content);

        if (parentView instanceof OverlappingPaneLayout)
            setupPaneLayout((OverlappingPaneLayout) parentView);

        return parentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getActivity().getSharedPreferences(DialerActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        mLastCallShortcutDate = prefs.getLong(KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE, 0);
        fetchCalls();
        mCallLogAdapter.setLoading(true);
    }

    private AbsListView getCurrentListView() {
        final int position = mViewPager.getCurrentItem();
        switch (getRtlPosition(position)) {
            case TAB_INDEX_SPEED_DIAL:
                return mSpeedDialFragment == null ? null : mSpeedDialFragment.getListView();
            case TAB_INDEX_RECENTS:
                return mRecentsFragment == null ? null : mRecentsFragment.getListView();
            case TAB_INDEX_ALL_CONTACTS:
                return mAllContactsFragment == null ? null : mAllContactsFragment.getListView();
        }
        throw new IllegalStateException("No fragment at position " + position);
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public long getItemId(int position) {
            return getRtlPosition(position);
        }

        @Override
        public Fragment getItem(int position) {
            Bundle args = new Bundle();
            switch (getRtlPosition(position)) {
                case TAB_INDEX_SPEED_DIAL:
                    mSpeedDialFragment = new SpeedDialFragment();
                    return mSpeedDialFragment;
                case TAB_INDEX_RECENTS:
                    args.putInt(CallLogFragment.FILTER_TYPE, CallLogQueryHandler.CALL_TYPE_ALL);
                    args.putInt(CallLogFragment.LOG_LIMIT, MAX_RECENTS_ENTRIES);
                    args.putLong(CallLogFragment.DATE_LIMIT, System.currentTimeMillis() - OLDEST_RECENTS_DATE);
                    mRecentsFragment = CallLogFragment.newInstance(args);
                    mRecentsFragment.setHasFooterView(true);
                    return mRecentsFragment;
                case TAB_INDEX_ALL_CONTACTS:
                    mAllContactsFragment = new AllContactsFragment();
                    return mAllContactsFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            // On rotation the FragmentManager handles rotation. Therefore getItem() isn't called.
            // Copy the fragments that the FragmentManager finds so that we can store them in
            // instance variables for later.
            final Fragment fragment =
                    (Fragment) super.instantiateItem(container, position);
            if (fragment instanceof SpeedDialFragment) {
                mSpeedDialFragment = (SpeedDialFragment) fragment;
            } else if (fragment instanceof CallLogFragment) {
                mRecentsFragment = (CallLogFragment) fragment;
            } else if (fragment instanceof AllContactsFragment) {
                mAllContactsFragment = (AllContactsFragment) fragment;
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return TAB_INDEX_COUNT;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }
    }

    public void addOnPageChangeListener(ViewPager.OnPageChangeListener onPageChangeListener) {
        if (!mOnPageChangeListeners.contains(onPageChangeListener)) {
            mOnPageChangeListeners.add(onPageChangeListener);
        }
    }
    
    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset,
                    positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageSelected(position);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrollStateChanged(state);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void showRemoveView(boolean show) {
        mRemoveViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
        mRemoveView.setAlpha(show ? 0 : 1);
        mRemoveView.animate().alpha(show ? 1 : 0).start();

        if (mShortcutCardsListView.getChildCount() > 0) {
            View v = mShortcutCardsListView.getChildAt(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                v.animate().withLayer()
                        .alpha(show ? REMOVE_VIEW_SHOWN_ALPHA : REMOVE_VIEW_HIDDEN_ALPHA)
                        .start();
            }
            else {
                v.animate()
                        .alpha(show ? REMOVE_VIEW_SHOWN_ALPHA : REMOVE_VIEW_HIDDEN_ALPHA)
                        .start();
            }
        }
    }

    public int getRtlPosition(int position) {
        if (Utilities.isRtl()) {
            return TAB_INDEX_COUNT - 1 - position;
        }
        return position;
    }

    public RemoveView getRemoveView() {
        return mRemoveView;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupPaneLayout(OverlappingPaneLayout paneLayout) {
        // TODO: Remove the notion of a capturable view. The entire view be slideable, once
        // the framework better supports nested scrolling.
        paneLayout.setCapturableView(mSlidingTabLayout);
        paneLayout.openPane();
        paneLayout.setPanelSlideCallbacks(mPanelSlideCallbacks);
        paneLayout.setIntermediatePinnedOffset(((HostInterface) getActivity()).getActionBarHeight());

        LayoutTransition transition = paneLayout.getLayoutTransition();
        // Turns on animations for all types of layout changes so that they occur for
        // height changes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            transition.enableTransitionType(LayoutTransition.CHANGING);
    }

    /* ***************************************************
     * CallLogQueryHandler.Listener callback functions
     ***************************************************** */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        // no-op
    }

    @Override
    public boolean onCallsFetched(Cursor cursor) {
        mCallLogAdapter.setLoading(false);

        // Save the date of the most recent call log item
        if (cursor != null && cursor.moveToFirst()) {
            mCurrentCallShortcutDate = cursor.getLong(CallLogQuery.DATE);
        }

        mCallLogAdapter.changeCursor(cursor);
        mMergedAdapter.notifyDataSetChanged();
        // Return true; took ownership of cursor
        return true;
    }

    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchCalls(CallLogQueryHandler.CALL_TYPE_ALL, mLastCallShortcutDate);
    }

    public void dismissShortcut(View view) {
        mLastCallShortcutDate = mCurrentCallShortcutDate;
        final SharedPreferences prefs = view.getContext().getSharedPreferences(
                DialerActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE, mLastCallShortcutDate).apply();
        fetchCalls();
    }

}
