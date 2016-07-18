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
package com.silentcircle.silentphone2.list;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
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
import com.silentcircle.contacts.calllognew.CallLogAdapter;
import com.silentcircle.contacts.calllognew.CallLogFragment;
import com.silentcircle.contacts.calllognew.CallLogQuery;
import com.silentcircle.contacts.calllognew.CallLogQueryHandler;
import com.silentcircle.contacts.calllognew.ContactInfoHelper;
import com.silentcircle.contacts.widget.SlidingTabLayout;
import com.silentcircle.messaging.fragments.ConversationsFragment;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.list.ShortcutCardsAdapter.SwipeableShortcutCard;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.OverlappingPaneLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link com.silentcircle.silentphone2.list.ListsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link com.silentcircle.silentphone2.list.ListsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ListsFragment extends Fragment implements CallLogQueryHandler.Listener,
        CallLogAdapter.CallFetcher, ViewPager.OnPageChangeListener, AxoMessaging.AxoMessagingStateCallback {

    private static final boolean DEBUG = false; // ConfigurationUtilities.mTrace;
    private static final String TAG = "ListsFragment";

//    public static final int TAB_INDEX_SPEED_DIAL = 0;
    public static final int TAB_INDEX_RECENTS = 0;  // 1;
    private static final int TAB_INDEX_ALL_CONTACTS = 1; // 2;

    // The CHAT fragment is optional, thus *must* have the highest index number.
    public static final int TAB_INDEX_CHAT = 2; // 3;

    private static final int TAB_INDEX_COUNT = 3; // 4;

    private int[] mTabIndexNoAxo = {/*TAB_INDEX_SPEED_DIAL,*/ TAB_INDEX_RECENTS, TAB_INDEX_ALL_CONTACTS};
    private int[] mTabIndexAxo = {/*TAB_INDEX_SPEED_DIAL,*/ TAB_INDEX_CHAT, TAB_INDEX_RECENTS, TAB_INDEX_ALL_CONTACTS};
    private int[] mTabIndexMapAxo = {/*TAB_INDEX_SPEED_DIAL,*/ TAB_INDEX_ALL_CONTACTS, TAB_INDEX_CHAT, TAB_INDEX_RECENTS};
    private int[] mTabIndex;
    private int[] mTabIndexMap;

    private static final int MAX_RECENTS_ENTRIES = 20;
    // Oldest recents entry to display is 2 weeks old.
    private static final long OLDEST_RECENTS_DATE = 1000L * 60 * 60 * 24 * 14;

    private static final String KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE = "key_last_dismissed_call_shortcut_date";

    private static final float REMOVE_VIEW_SHOWN_ALPHA = 0.5f;
    private static final float REMOVE_VIEW_HIDDEN_ALPHA = 1;

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 1;

    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private String[] mTabTitles;
    private ListView mShortcutCardsListView;

    private CallLogAdapter mCallLogAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private ShortcutCardsAdapter mMergedAdapter;

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

    private CallLogFragment mRecentsFragment;
    private ConversationsFragment mConversationsFragment;
    private AllContactsFragment mAllContactsFragment;

    private boolean mAxoRegistered;
    protected BroadcastReceiver mViewUpdater;
    /* handler for failed messages runnable */
    protected Handler mMessageHandler;

    protected final Runnable mRunMessageFailureHandling = new Runnable() {
        @Override
        public void run() {
            handleMessageFailures();
        }
    };

    public interface HostInterface {
        void showCallHistory();
        int getActionBarHeight();
        @SuppressWarnings("unused")
        void setActionBarHideOffset(int offset);
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
            /**
             * Returning true here solves the issue of the main view being
             * able to be pulled down even when there is no recent call,
             * ending up with an undesired blank space.
             **/
            return true;
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
                new ContactInfoHelper(getActivity(), currentCountryIso), null,
                (OnPhoneNumberPickerActionListener)getActivity(), false);

        mMergedAdapter = new ShortcutCardsAdapter(getActivity(), this, mCallLogAdapter);
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getActivity().getApplicationContext());
        mAxoRegistered = axoMessaging.isRegistered();
        axoMessaging.addStateChangeListener(this);
        mTabIndex = mAxoRegistered ? mTabIndexAxo : mTabIndexNoAxo;
        mTabIndexMap = mAxoRegistered ? mTabIndexMapAxo : mTabIndexNoAxo;

        mTabTitles = new String[TAB_INDEX_COUNT];
//        mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
        mTabTitles[TAB_INDEX_RECENTS] = getResources().getString(R.string.tab_recents);
        mTabTitles[TAB_INDEX_CHAT] = getResources().getString(R.string.tab_chat);
        mTabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);

        mMessageHandler = new Handler();
    }

    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
        mViewPager = (ViewPager) parentView.findViewById(R.id.lists_pager);
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(viewPagerAdapter);
        mViewPager.setOffscreenPageLimit(3);
        setPagerItem(/*TAB_INDEX_SPEED_DIAL*/ mAxoRegistered ? TAB_INDEX_CHAT : TAB_INDEX_RECENTS);

        // BEGIN_INCLUDE (setup_sliding tab layout)
        // Give the SlidingTabLayout the ViewPager, this must be done AFTER the ViewPager has had
        // it's PagerAdapter set.
        mSlidingTabLayout = (SlidingTabLayout)parentView.findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setCustomTabView(R.layout.tab_text, R.id.text_tab);
        mSlidingTabLayout.setSelectedIndicatorColors(ContextCompat.getColor(getActivity(), R.color.sc_ng_text_red));
        mSlidingTabLayout.setDividerColors(ContextCompat.getColor(getActivity(), android.R.color.transparent));
        mSlidingTabLayout.setDefaultTabTextColor(R.color.sc_ng_text_grey_2);
        mSlidingTabLayout.setSelectedTabTextColor(R.color.sc_ng_text_red);
        mSlidingTabLayout.setViewPager(mViewPager);
        // END_INCLUDE (setup_sliding tab layout)

        mSlidingTabLayout.setOnPageChangeListener(this);

        mShortcutCardsListView = (ListView)parentView.findViewById(R.id.shortcut_card_list);
        mShortcutCardsListView.setAdapter(mMergedAdapter);

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
        registerViewUpdater();
        refreshTabView();
        handleMessageFailures();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mViewUpdater);
        mMessageHandler.removeCallbacks(mRunMessageFailureHandling);
        mCallLogAdapter.stopRequestProcessing();
    }

    private void registerViewUpdater() {

        mViewUpdater = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                switch (Action.from(intent)) {
                    case RECEIVE_MESSAGE:
                        refreshTabView();
                        SoundNotifications.playReceiveMessageSound();
                        if (isOrderedBroadcast()) {
                            abortBroadcast();
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        IntentFilter filter = Action.RECEIVE_MESSAGE.filter();
        filter.setPriority(MESSAGE_PRIORITY);

        Context context = getActivity();
        if (context != null) {
            context.registerReceiver(mViewUpdater, filter);
        }
    }

    private void setTabViewNotificationVisibility(int position, boolean visible) {
        if (mSlidingTabLayout != null) {
            mSlidingTabLayout.setTabHighlighted(position, visible);
        }
    }

    private void handleMessageFailures() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (mMessageHandler != null) {
            mMessageHandler.removeCallbacks(mRunMessageFailureHandling);
        }

        if (mAxoRegistered) {
            AsyncUtils.execute(new HandleMessageFailuresTask(activity.getApplicationContext(), this));
        }
        else {
            scheduleMessageFailuresTask();
        }
    }

    private void scheduleMessageFailuresTask() {
        if (mMessageHandler != null) {
            mMessageHandler.removeCallbacks(mRunMessageFailureHandling);
            mMessageHandler.postDelayed(mRunMessageFailureHandling, TimeUnit.MINUTES.toMillis(5));
        }
    }

    public void setPagerItem(int position) {
        if(mViewPager != null) {
            mViewPager.setCurrentItem(getRtlPosition(mappedPosition(position)));
        }
    }

    @SuppressWarnings("unused")
    public void setPagerItem(int position, boolean smoothScroll) {
        if(mViewPager != null) {
            mViewPager.setCurrentItem(getRtlPosition(mappedPosition(position)), smoothScroll);
        }
    }

    private void refreshTabView() {
        if (mAxoRegistered) {
            int conversationsWithUnreadMessages =
                    ConversationUtils.getConversationsWithUnreadMessages(getActivity().getApplicationContext());
            setTabViewNotificationVisibility(mTabIndexMap[TAB_INDEX_CHAT],
                    conversationsWithUnreadMessages > 0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMergedAdapter != null) {
            mMergedAdapter.unregisterContentObserver();
        }
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getActivity().getApplicationContext());
        axoMessaging.removeStateChangeListener(this);
        mCallLogAdapter.stopRequestProcessing();
    }

    private AbsListView getCurrentListView() {
        final int position = indexPosition(mViewPager.getCurrentItem());
        switch (getRtlPosition(position)) {
            case TAB_INDEX_RECENTS:
                return mRecentsFragment == null ? null : mRecentsFragment.getListView();
            case TAB_INDEX_CHAT:
                return mConversationsFragment == null? null : mConversationsFragment.getListView();
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
            return getRtlPosition(indexPosition(position));
        }

        @Override
        public Fragment getItem(int position) {
            Bundle args = new Bundle();
            switch (getRtlPosition(indexPosition(position))) {
                case TAB_INDEX_RECENTS:
                    args.putInt(CallLogFragment.FILTER_TYPE, CallLogQueryHandler.CALL_TYPE_ALL);
                    args.putInt(CallLogFragment.LOG_LIMIT, MAX_RECENTS_ENTRIES);
                    args.putLong(CallLogFragment.DATE_LIMIT, System.currentTimeMillis() - OLDEST_RECENTS_DATE);
                    mRecentsFragment = CallLogFragment.newInstance(args);
                    mRecentsFragment.setHasFooterView(true);
                    return mRecentsFragment;
                case TAB_INDEX_CHAT:
                    mConversationsFragment = ConversationsFragment.newInstance(args);
                    return mConversationsFragment;
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
            final Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (fragment instanceof CallLogFragment) {
                mRecentsFragment = (CallLogFragment) fragment;
            } else if (fragment instanceof AllContactsFragment) {
                mAllContactsFragment = (AllContactsFragment) fragment;
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return mAxoRegistered ? TAB_INDEX_COUNT : TAB_INDEX_COUNT - 1;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[indexPosition(position)];
        }
    }

    private static class HandleMessageFailuresTask extends com.silentcircle.messaging.task.HandleMessageFailuresTask {

        private final WeakReference<ListsFragment> mFragmentReference;

        public HandleMessageFailuresTask(Context context, ListsFragment fragment) {
            super(context);
            mFragmentReference = new WeakReference<>(fragment);
        }

        @Override
        protected void onPostExecute(Integer count) {
            ListsFragment fragment = mFragmentReference.get();
            if (count != 0 && fragment != null) {
                fragment.scheduleMessageFailuresTask();
            }
        }
    }

    public void addOnPageChangeListener(ViewPager.OnPageChangeListener onPageChangeListener) {
        if (!mOnPageChangeListeners.contains(onPageChangeListener)) {
            mOnPageChangeListeners.add(onPageChangeListener);
        }
    }

    @Override
    public void axoRegistrationStateChange(boolean registered) {
        if (mAxoRegistered != registered) {
            mAxoRegistered = registered;
            mTabIndex = mTabIndexAxo;
            mTabIndexMap = mTabIndexMapAxo;
            ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
            mViewPager.setAdapter(viewPagerAdapter);
            mSlidingTabLayout.setViewPager(mViewPager);
            refreshTabView();
            handleMessageFailures();
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        position = indexPosition(position);
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset,
                    positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        position = indexPosition(position);
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

    public int getRtlPosition(int position) {
        if (Utilities.isRtl()) {
            return TAB_INDEX_COUNT - 1 - position;
        }
        return position;
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

    private int indexPosition(int position) {
        return mTabIndex[position];
    }

    private int mappedPosition(int position) {
        return mTabIndexMap[position];
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
