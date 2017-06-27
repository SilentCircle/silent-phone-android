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
package com.silentcircle.messaging.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.widget.DataRetentionBanner;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.listener.ClickthroughWhenNotInChoiceMode;
import com.silentcircle.messaging.listener.LoadingScrollListener;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.listener.MultipleChoiceSelector;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.AvatarChatRecyclerView;
import com.silentcircle.messaging.views.BaseMessageEventView;
import com.silentcircle.messaging.views.CallEventView;
import com.silentcircle.messaging.views.FailureEventView;
import com.silentcircle.messaging.views.IncomingMessageEventView;
import com.silentcircle.messaging.views.InfoEventView;
import com.silentcircle.messaging.views.OutgoingMessageEventView;
import com.silentcircle.messaging.views.adapters.DateHeaderView;
import com.silentcircle.messaging.views.adapters.FooterModelViewAdapter;
import com.silentcircle.messaging.views.adapters.GroupingModelViewAdapter;
import com.silentcircle.messaging.views.adapters.ModelViewAdapter;
import com.silentcircle.messaging.views.adapters.ModelViewType;
import com.silentcircle.messaging.views.adapters.ViewType;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import zina.ZinaNative;

public class ChatFragment extends BaseFragment implements MultipleChoiceSelector.ActionPerformer,
        View.OnClickListener {

    public static final String TAG_CONVERSATION_CHAT_FRAGMENT =
            "com.silentcircle.messaging.chat";

    private static final boolean DEBUG = false;       // Don't submit with true

    private static final String FIRST_RUN =
            "com.silentcircle.messaging.fragments.ChatFragment.firsRun";
    private static final String LAST_MESSAGE_NUMBER =
            "com.silentcircle.messaging.fragments.ChatFragment.lastMessageNumber";
    private static final String EVENT_COUNT =
            "com.silentcircle.messaging.fragments.ChatFragment.eventCount";

    private static final String TAG = ChatFragment.class.getSimpleName();

    /* Priority for this view to handle message broadcasts, higher than ConversationActivity. */
    private static final int MESSAGE_PRIORITY = 3;
    /* Invalid position in list view */
    private static final int INVALID_POSITION = -1;
    /* Events page size to use when determining conversation's last event */
    private static final int PAGE_SIZE = 15;
    /* Number of events to load when first opening chat view */
    private static final int PAGE_SIZE_INITIAL = 30;
    /* Page size for all elements. */
    private static final int PAGE_SIZE_UNLIMITED = -1;

    public interface Callback {

        void onActionModeCreated(ActionMode mode);

        void onActionModeDestroyed();

        void onActionPerformed();

        void performAction(int actionID, List<Event> targets);
    }

    public static final ViewType[] VIEW_TYPES = {
        new ModelViewType(IncomingMessage.class, IncomingMessageEventView.class, R.layout.messaging_chat_item_incoming_message),
        new ModelViewType(OutgoingMessage.class, OutgoingMessageEventView.class, R.layout.messaging_chat_item_outgoing_message),
        new ModelViewType(CallMessage.class, CallEventView.class, R.layout.messaging_chat_item_phone),
        new ModelViewType(ErrorEvent.class, FailureEventView.class, R.layout.messaging_chat_item_error),
        new ModelViewType(Event.class, InfoEventView.class, R.layout.messaging_chat_item_info),
        new ModelViewType(Date.class, DateHeaderView.class, R.layout.messaging_date_header_view),
    };

    private Callback mCallback;
    private AvatarChatRecyclerView mEventsView;
    private LinearLayoutManager mLayoutManager;
    private DataRetentionBanner mDataRetentionBanner;
    private View mEmptyListView;

    private GroupingModelViewAdapter mAdapter;
    private List<Event> mEvents;
    private List<Event> mEventsLoadCache;
    private boolean mFirstRun = true;

    private int mInitialPageSize = PAGE_SIZE_INITIAL;
    private boolean mIsPartnerMessagingDrEnabled = false;
    private boolean mIsPartnerCallDrEnabled = false;
    private final AtomicBoolean mIsLoading = new AtomicBoolean();

    private EventRepository.PagingContext mPagingContext;
    private int mLastMessageNumber = INVALID_POSITION;

    private BroadcastReceiver mScreenUpdateListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                pauseEventUpdate();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                resumeEventUpdate();
            }
        }
    };

    private MessagingBroadcastReceiver mViewUpdater = new MessagingBroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String partner = intent.getStringExtra(Extra.PARTNER.getName());
            boolean forCurrentConversation = TextUtils.equals(partner, getConversationId());
            switch (Action.from(intent)) {
                case UPDATE_CONVERSATION:
                    if (forCurrentConversation) {
                        boolean isHandled = handleUpdateNotification(intent);
                        abortIfNecessary(isHandled);
                        if (mEventsView != null) {
                            mEventsView.post(mMarkMessagesAndUpdateRunnable);
                        }
                    }
                    break;
                case RECEIVE_MESSAGE:
                    if (forCurrentConversation) {
                        handleReceiveMessageIntent(intent);
                        postScrollToBottom();
                        if (mEventsView != null) {
                            mEventsView.post(mMarkMessagesAndUpdateRunnable);
                        }
                    }
                    break;
                case REFRESH_SELF:
                    // contacts update will not specify a conversation
                    if (mAdapter != null) {
                        mAdapter.resetNameCache();
                        mAdapter.notifyDataSetChanged();
                        mOnScrollListener.resetState();
                    }
                    if (forCurrentConversation) {
                        // reload all events in background and notifyItemRangeChanged.
                        resetPagingContext();
                        setEvents(getConversationId());
                    }
                    break;
                case DATA_RETENTION_EVENT:
                    if (forCurrentConversation) {
                        // nothing to handle, user sees error event
                        abortIfNecessary(true);
                    }
                    break;
                default:
            }
        }

        private void abortIfNecessary(boolean isHandled) {
            if (isOrdered() && isHandled) {
                setConsumed(true);
            }
        }
    };

    private Runnable mMarkMessagesAndUpdateRunnable = new Runnable() {

        @Override
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "markMessagesAndUpdateRunnable: first run " + mFirstRun);
            }
            // scroll to first unread message on first run
            if (mFirstRun) {
                scrollToFirstUnread();
                mFirstRun = false;
            }

            // ensure that visible events have their update routine running
            resumeEventUpdate();
            // ensure that visible events are marked as read
            markReceivedMessagesAsRead();
        }
    };

    private Runnable mScrollToBottomRunnable = new Runnable() {
        @Override
        public void run() {
            if (mEventsView != null) {
                mEventsView.scrollToBottom();
            }
        }
    };

    private LoadingScrollListener mOnScrollListener = new LoadingScrollListener(PAGE_SIZE) {

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                resumeEventUpdate();
            }
        }

        public void onLoadNextPage() {
            loadNextPage(getConversationId());
        }

        public int getItemCount() {
            return mLayoutManager.getItemCount();
        }

        public int getFirstVisiblePosition() {
            return mLayoutManager.findFirstVisibleItemPosition();
        }

    };

    protected Callback getCallback() {
        if (mCallback != null) {
            return mCallback;
        }
        Activity activity = getActivity();
        return activity instanceof Callback ? (Callback) activity : null;
    }

    @Nullable
    public Event getEvent(int position) {
        /* Allow retrieval only for valid data positions */
        if (getDataPosition(position) == INVALID_POSITION) {
            return null;
        }
        /*
         * Check validity of position, should be smaller than item count.
         * Negative values are checked in list view's getItemAtPosition().
         */
        if (mEventsView != null && mEventsView.getCount() > position) {
            return (Event) mEventsView.getItemAtPosition(position);
        }
        return null;
    }

    @Nullable
    public Event getEvent(String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        Event result = null;
        if (mEvents != null) {
            for (Event event : mEvents) {
                if (id.equals(event.getId())) {
                    result = event;
                    break;
                }

            }
        }
        return result;
    }

    public boolean hasMultipleCheckedItems() {
        return mEventsView != null && mEventsView.hasMultipleCheckedItems();
    }

    public Set<String> getCheckedItems() {
        return mEventsView != null ? mEventsView.getCheckedIds() : null;
    }

    @Override
    public void onActionPerformed() {
        Callback callback = getCallback();
        if (callback != null) {
            callback.onActionPerformed();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mFirstRun = savedInstanceState.getBoolean(FIRST_RUN);
            mInitialPageSize = savedInstanceState.getInt(EVENT_COUNT, PAGE_SIZE_INITIAL);
            mLastMessageNumber = savedInstanceState.getInt(LAST_MESSAGE_NUMBER, INVALID_POSITION);
        }
        return inflater.inflate(R.layout.messaging_chat_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean isGroupConversation = isGroupConversation();

        mLayoutManager = new LinearLayoutManager(getActivity()) {

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        mLayoutManager.setStackFromEnd(true);
        mLayoutManager.setSmoothScrollbarEnabled(true);

        int footerHeight = Utilities.canMessage(getConversationId()) || isGroupConversation
                ? (int) getResources().getDimension(R.dimen.messaging_compose_height)
                : 0;
        mAdapter = new FooterModelViewAdapter(mEvents, VIEW_TYPES, footerHeight);
        mAdapter.setIsGroupConversation(isGroupConversation);

        mEventsView = (AvatarChatRecyclerView) findViewById(R.id.chat_events);
        mEventsView.setLayoutManager(mLayoutManager);
        mEventsView.setAdapter(mAdapter);
        mEventsView.setHasFixedSize(false);
        mEventsView.setClickThroughListener(ClickthroughWhenNotInChoiceMode.getInstance());
        mEventsView.setMultiChoiceModeListener(new ChatFragmentMultipleChoiceSelector(this,
                mAdapter, R.menu.multiselect_event, getString(R.string.n_selected)));

        /*
        mEventsView.setItemViewCacheSize(2 * PAGE_SIZE_INITIAL);
         */
        /*
        mEventsView.setDrawingCacheEnabled(true);
        mEventsView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
         */

        /*
         * Animator is responsible for some artifacts on screen when sending a message or scrolling.
         * But it will be necessary for burn animation when removing an item.
         *
         * Item animator occasionally fails to move header items to proper position.
         *
         * ChatItemAnimator animator = new ChatItemAnimator();
         * mEventsView.setItemAnimator(animator);
         */
        mEventsView.setItemAnimator(null);

        mEmptyListView = view.findViewById(R.id.empty_list_view);
        mEventsView.setEmptyView(mEmptyListView);

        mDataRetentionBanner = (DataRetentionBanner) findViewById(R.id.data_retention_status);
    }

    @Override
    public void onDestroyView() {
        mEventsView = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();

        resumeEventUpdate();
        registerUserInfoListener();
        registerReceiver();

        updateDataRetentionBanner();
        configureEmptyListView();

        if (mAdapter != null) {
            mAdapter.setIsGroupConversation(isGroupConversation());
        }
        if (mEventsView != null) {
            mEventsView.postInvalidate();
            mEventsView.addOnScrollListener(mOnScrollListener);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(FIRST_RUN, mFirstRun);
        if (mEvents != null) {
            outState.putInt(EVENT_COUNT, mEvents.size());
        }
        outState.putInt(LAST_MESSAGE_NUMBER, mLastMessageNumber);
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(mScreenUpdateListener);
        unregisterMessagingReceiver(mViewUpdater);
        unregisterUserInfoListener();

        pauseEventUpdate();

        resetPagingContext();

        if (mEventsView != null) {
            mEventsView.postInvalidate();
            mEventsView.removeOnScrollListener(mOnScrollListener);
        }
    }

    @Override
    public void performAction(final int menuActionId, final int... positions) {
        final Callback callback = getCallback();
        if (callback != null && positions.length > 0) {
            final List<Event> events = new ArrayList<>(positions.length);
            for (int position : positions) {
                Event event = getEvent(position);
                if (event != null) {
                    events.add(event);
                }
            }

            callback.performAction(menuActionId, events);
        }
    }

    @Override
    public void performAction(final int menuActionId, final String... ids) {
        final Callback callback = getCallback();
        if (callback != null && ids.length > 0) {
            final List<Event> events = new ArrayList<>(ids.length);
            for (String id : ids) {
                Event event = getEvent(id);
                if (event != null) {
                    events.add(event);
                }
            }

            callback.performAction(menuActionId, events);
        }
    }

    @Override
    public void onClick(View v) {
        //
    }

    @Override
    public void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        if (!isAdded()) {
            return;
        }
        updateDataRetentionBanner();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void setEvents(String conversationId) {
        if (DEBUG) {
            Log.d(TAG, "setEvents: " + conversationId);
        }
        final ConversationRepository repository =
                ConversationUtils.getConversations();
        final Conversation conversation =
                ConversationUtils.getConversation(conversationId);
        if (repository == null || conversation == null) {
            return;
        }

        List<Event> events;

        if (mPagingContext == null) {
            if (DEBUG) {
                Log.d(TAG, "setEvents: paging context empty for " + conversationId);
            }
            mPagingContext =
                    new EventRepository.PagingContext(
                            EventRepository.PagingContext.START_FROM_YOUNGEST,
                            Math.max((conversation.getUnreadMessageCount()
                                    + conversation.getUnreadCallMessageCount()) + PAGE_SIZE, mInitialPageSize));
            if (mLastMessageNumber != INVALID_POSITION) {
                mPagingContext.setLastMessageNumber(mLastMessageNumber);

                /*
                 * we want to have all younger events from a specified offset.
                 * AppRepository#loadEvents allows to query events between offset and offset + number - 1
                 * so we try to make a query between offset and max value of int32_t.
                 *
                 * events = repository.historyOf(conversation).list(mLastMessageNumber,
                 *       0x7fffffff - mLastMessageNumber + 1, 0);
                 *
                 * A better way is to relay on SQLite and use negative value for row number.
                 * "If the LIMIT expression evaluates to a negative value, then there is no upper
                 * bound on the number of rows returned."
                 */
                events = repository.historyOf(conversation).list(mLastMessageNumber,
                        PAGE_SIZE_UNLIMITED, EventRepository.PagingContext.START_FROM_OLDEST);
            } else {
                events = repository.historyOf(conversation).list(mPagingContext);
            }
            if (events != null) {
                /*
                 * Ensure enough events for first page are loaded.
                 *
                 * This should be done on background thread as the paging. Currently to
                 * avoid jumps in recycler view do it on main thread before any items are set
                 * in adapter.
                 */
                events = MessageUtils.filter(events, DialerActivity.mShowErrors);
                if (events.size() < mPagingContext.getPageSize()) {
                    events = getPageEvents(repository, conversation, events, mPagingContext);
                }
            }

            if (events != null) {
                setEvents(events);
            }
            mPagingContext.setPageSize(PAGE_SIZE);
        }
        else {
            loadNextPage(conversationId);
        }
    }

    private void loadNextPage(final String conversationId) {

        if (mPagingContext == null) {
            if (DEBUG) {
                Log.d(TAG, "loadNextPage: paging context empty");
            }
            setEvents(conversationId);
        }
        else {
            if (mIsLoading.get()) {
                if (DEBUG) {
                    Log.d(TAG, "loadNextPage: already loading");
                }
                return;
            }

            if (DEBUG) {
                Log.d(TAG, "loadNextPage: load");
            }
            final EventRepository.PagingContext pagingContext = mPagingContext;
            /*
             * To avoid event duplication check whether paging context has been used.
             * Youngest events will be retrieved when it is not and will duplicate cache entries.
             */
            final boolean isPagingContextInitialized =
                    pagingContext.getLastMessageNumber() != INVALID_POSITION;
            mEventsLoadCache = mEvents == null ? null : new ArrayList<>(mEvents);
            mIsLoading.set(true);
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    final ConversationRepository repository =
                            ConversationUtils.getConversations();
                    final Conversation conversation =
                            ConversationUtils.getConversation(conversationId);
                    if (repository == null || conversation == null) {
                        mIsLoading.set(false);
                        return;
                    }

                    List<Event> events = null;
                    events = getPageEvents(repository, conversation, events, pagingContext);
                    if (events == null || events.size() == 0) {
                        if (DEBUG) {
                            Log.d(TAG, "loadNextPage: nothing loaded");
                        }
                        mIsLoading.set(false);
                        return;
                    }

                    // ensure that newly loaded events are marked as read it there are any un-read events
                    markReceivedMessagesAsRead(events);

                    Activity activity = getActivity();
                    if (activity != null) {
                        final int addedEventCount = events.size();
                        if (mEventsLoadCache != null && isPagingContextInitialized) {
                            mEventsLoadCache.addAll(0, events);
                        } else {
                            mEventsLoadCache = events;
                        }

                        // TODO !Risk is for mEvents to get updated while page is loaded!
                        /*
                         * determine actual number of events added, as this number is used
                         * to scroll the list view to correct position after update.
                         */
                        final int eventsCount = mEventsLoadCache.size();
                        final List<Event> filteredEvents = MessageUtils.filter(mEventsLoadCache,
                                DialerActivity.mShowErrors);

                        if (DEBUG) {
                            Log.d(TAG, "loadNextPage: loading mFirstRun " + mFirstRun
                                    + ", current count: " + mAdapter.getCount());
                        }
                        // post runnable so it is not conflicting with ongoing scroll
                        mEventsView.post(new Runnable() {
                            @Override
                            public void run() {
                                // do this in place so data set reference in adapter does not change
                                mEvents.clear();
                                mEvents.addAll(filteredEvents); // retainAll seems slower
                                int filteredEventsCount = mEvents.size();
                                int finalAddedEventCount = addedEventCount;
                                if (filteredEventsCount != eventsCount) {
                                    finalAddedEventCount -= eventsCount - filteredEventsCount;
                                }

                                if (mAdapter.getModels() == mEvents) {
                                    mAdapter.notifyItemRangeInserted(0, finalAddedEventCount);
                                    /*
                                     * There is need to update items as with loading new page and
                                     * sorting it is possible that event from new page has to be
                                     * shown somewhere between already loaded events.
                                     * Items in DB are stored as they arrive, not by date they
                                     * should be shown by.
                                     */
                                    mAdapter.notifyItemRangeChanged(finalAddedEventCount + 1,
                                            mEvents.size() - 1);
                                }
                                else {
                                    // if adapter's data set is not mEvents then perform full update
                                    mAdapter.setModels(mEvents);
                                    mAdapter.notifyDataSetChanged();
                                }
                                if (DEBUG) {
                                    Log.d(TAG, "loadNextPage: loaded mFirstRun " + mFirstRun
                                            + ", current count: " + mAdapter.getCount());
                                }

                                mIsLoading.set(false);
                            }
                        });
                    }
                    else {
                        mIsLoading.set(false);

                    }
                }
            });
        }
    }

    public void resetPagingContext() {
        mLastMessageNumber =
                mPagingContext != null ? mPagingContext.getLastMessageNumber() : INVALID_POSITION;
        mPagingContext = null;
    }

    public void clearPagingContext() {
        if (DEBUG) {
            Log.d(TAG, "clearPagingContext");
        }
        mLastMessageNumber = INVALID_POSITION;
        mPagingContext = null;
        mFirstRun = true;
    }

    @Nullable
    private List<Event> getPageEvents(ConversationRepository repository, Conversation conversation,
            List<Event> events, EventRepository.PagingContext pagingContext) {
        while (true) {
            List<Event> page = repository.historyOf(conversation).list(pagingContext);
            if (events == null) {
                events = page;
            } else {
                if (page != null) {
                    events.addAll(page);
                }
            }
            if (events != null && events.size() > 0) {
                List<Event> filteredEvents = MessageUtils.filter(events, DialerActivity.mShowErrors);
                if (filteredEvents.size() >= pagingContext.getPageSize()) {
                    break;
                }
            }
            if (page == null || page.size() == 0) {
                break;
            }
        }
        return events;
    }

    public void setEvents(List<Event> events) {
        mEvents = MessageUtils.filter(events, DialerActivity.mShowErrors);
        if (mAdapter != null) {
            mAdapter.setIsGroupConversation(isGroupConversation());
            mAdapter.setModels(mEvents);
            mAdapter.notifyDataSetChanged();
            mOnScrollListener.resetState();
        }
        if (mEventsView != null) {
            mEventsView.post(mMarkMessagesAndUpdateRunnable);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void setFooter(int height) {
        if (mAdapter instanceof FooterModelViewAdapter) {
            ((FooterModelViewAdapter) mAdapter).setFooterHeight(height);
//            if (mEventsView != null) {
//                mEventsView.requestLayout();
//            }
        }
    }

    public void scrollToBottom() {
        if (mEventsView != null) {
            mEventsView.scrollToBottom();
        }
    }

    public void postScrollToBottom() {
        if (mEventsView != null /*&& !isScrolledToBottom()*/) {
            mEventsView.post(mScrollToBottomRunnable);
        }
    }

    /*
     * Try to scroll view so that first unread message is visible after last read or sent message
     * which is at the very top.
     * First scroll to the very bottom and if first unread message is not visible, adjust the view
     * so it becomes visible.
     */
    public void scrollToFirstUnread() {
        if (mEventsView != null && mEvents != null) {
            int firstUnreadPosition = INVALID_POSITION;
            int position = 0;
            for (Event event : mEvents) {
                if (event instanceof Message) {
                    if (((Message) event).getState() == MessageStates.RECEIVED
                            && !((Message) event).isExpired()) {
                        firstUnreadPosition = position;
                        break;
                    }
                }
                position += 1;
            }

            // Try to scroll to first unread position showing also last read message
            if (firstUnreadPosition != INVALID_POSITION) {
                // get last read position
                firstUnreadPosition = getScreenPosition(firstUnreadPosition - 1);
            }
            if (firstUnreadPosition != INVALID_POSITION && mLayoutManager != null) {
                // if last read position is not visible, scroll
                if (mLayoutManager.findFirstCompletelyVisibleItemPosition() > firstUnreadPosition) {
                    mEventsView.scrollToPosition(firstUnreadPosition);
                }
            }
            else {
                // scroll to bottom, in case entering view with forwarded message
                scrollToBottom();
            }
        }
    }

    private void resumeEventUpdate() {
        if (mEventsView != null) {
            for (int i = 0; i < mEventsView.getChildCount(); i++) {
                View view = mEventsView.getChildAt(i);
                if (view instanceof BaseMessageEventView) {
                    ((BaseMessageEventView) view).update();
                }
            }
            mEventsView.postInvalidate();
        }
    }

    private void pauseEventUpdate() {
        if (mEventsView != null) {
            for (int i = 0; i < mEventsView.getChildCount(); i++) {
                View view = mEventsView.getChildAt(i);
                if (view instanceof BaseMessageEventView) {
                    ((BaseMessageEventView) view).cancelUpdates();
                }
            }
        }
    }

    private void registerReceiver() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        /* register for screen on/off events */
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        activity.registerReceiver(mScreenUpdateListener, filter);

        /* register for UPDATE_CONVERSATION, RECEIVE_MESSAGE, REFRESH_SELF events */
        filter = Action.filter(Action.RECEIVE_MESSAGE, Action.UPDATE_CONVERSATION,
                Action.REFRESH_SELF, Action.DATA_RETENTION_EVENT);
        registerMessagingReceiver(activity, mViewUpdater, filter, MESSAGE_PRIORITY);
    }

    private boolean handleReceiveMessageIntent(@NonNull Intent intent) {
        if (mEvents == null) {
            return false;
        }

        CharSequence conversationId = Extra.PARTNER.getCharSequence(intent);
        ArrayList<CharSequence> messageIds = getMessageIds(intent);

        if (!TextUtils.isEmpty(conversationId)) {
            if (conversationId.equals(getConversationId())) {
                for (CharSequence messageId : messageIds) {
                    if (TextUtils.isEmpty(messageId)) {
                        continue;
                    }
                    Event event = MessageUtils.getEventById(conversationId.toString(),
                            messageId.toString());
                    if (event != null) {
                        updateEventInCache(event, true);
                        /*
                        if (event instanceof Message) {
                            checkMessageRetentionStatus((Message) event);
                        }
                         */
                    }
                }
            }
        }

        return false;
    }

    private ArrayList<CharSequence> getMessageIds(@NonNull Intent intent) {
        CharSequence id = Extra.ID.getCharSequence(intent);
        ArrayList<CharSequence> messageIds = new ArrayList<>();
        CharSequence[] ids = Extra.IDS.getCharSequences(intent);
        if (ids != null) {
            // TODO something nicer
            messageIds.addAll(Arrays.asList(ids));
        }
        if (!TextUtils.isEmpty(id)) {
            messageIds.add(id);
        }
        return messageIds;
    }

    private boolean handleUpdateNotification(@NonNull Intent intent) {
        boolean isHandled = false;

        CharSequence conversationId = Extra.PARTNER.getCharSequence(intent);
        if (TextUtils.isEmpty(conversationId) || !conversationId.equals(getConversationId())) {
            return false;
        }

        int reason = Extra.REASON.getInt(intent);
        boolean forceRefresh = Extra.FORCE.getBoolean(intent);
        ArrayList<CharSequence> messageIds = getMessageIds(intent);

        // TODO: avoid need for force refresh, it slows down burn animation and burn timer updates
        if (reason == ZinaMessaging.UPDATE_ACTION_MESSAGE_BURNED) {
            isHandled = handleBurnNotifications(messageIds);
        } else if (reason == ZinaMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE
                || reason == ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND) {
            isHandled = handleStatusUpdateNotifications(conversationId, messageIds,
                    /* can add */ reason == ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND);
            if (reason == ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND) {
                postScrollToBottom();
            }
        }
        return isHandled && !forceRefresh;
    }

    private boolean handleBurnNotifications(@NonNull ArrayList<CharSequence> messageIds) {
        boolean eventFound = false;

        // with checked items based on event id action mode could be kept during burns
        /*
        if (mEventsView != null) {
            mEventsView.exitActionMode();
        }
         */

        final ListIterator<Event> iterator = mEvents.listIterator(mEvents.size());
        while (iterator.hasPrevious()) {
            final Event event = iterator.previous();
            for (CharSequence messageId : messageIds) {
                if (TextUtils.isEmpty(messageId)) {
                    continue;
                }
                if (messageId.equals(event.getId())) {
                    eventFound = true;
                    final int screenPosition = getScreenPosition(iterator.nextIndex());
                    if (mEventsView != null) {
                        mEventsView.clearItemChecked(messageId.toString(), screenPosition);
                    }
                    iterator.remove();
                    if (mAdapter != null) {
                        mAdapter.notifyItemRemoved(screenPosition);
                        // Even though the item that has the same position as the deleted one
                        // hasn't changed, notifying will trigger a re-bind of the view holder and
                        // will allow ModelViewAdapter to change its background if needed
                        mAdapter.notifyItemChanged(screenPosition);
                    }
                }
            }
        }

        /*
         * To be sure that dataset is valid, call notifyDataSetChanged
         * TODO this may cancel animations, find a way to avoid it
         *
         */
//        if (mAdapter != null) {
//            mAdapter.notifyDataSetChanged();
//        }
//        mOnScrollListener.resetState();

        /*
         * try to load more events when something gets burned or removed
         * to avoid situation where no events are shown although conversation is not empty
         */
        if (mEvents.size() <= PAGE_SIZE) {
            loadNextPage(getConversationId());
        }

        return eventFound;
    }

    private int getScreenPosition(int position) {
        int result = INVALID_POSITION;
        try {
            ModelViewAdapter adapter = (ModelViewAdapter) mEventsView.getAdapter();
            result = adapter.getScreenPosition(position);
        } catch (Exception e) {
            // item not found, return INVALID_POSITION
        }
        return result;
    }

    private int getDataPosition(int position) {
        int result = INVALID_POSITION;
        try {
            ModelViewAdapter adapter = (ModelViewAdapter) mEventsView.getAdapter();
            result = adapter.getDataPosition(position);
        } catch (Exception e) {
            // item not found, return INVALID_POSITION
        }
        return result;
    }

    private boolean isScrolledToBottom() {
        boolean result = false;
        if (mEventsView != null) {
            int lastPosition = mEventsView.getCount() - 1;
            View view = mEventsView.getChildAt(lastPosition - mEventsView.getFirstVisiblePosition());
            result = view != null && mEventsView.getLastVisiblePosition() == lastPosition &&
                    view.getBottom() <= mEventsView.getHeight();
        }
        return result;
    }

    private boolean handleStatusUpdateNotifications(@Nullable CharSequence conversationId,
            @Nullable ArrayList<CharSequence> messageIds, boolean canAdd) {
        Activity activity = getActivity();
        if (activity == null || TextUtils.isEmpty(conversationId)
                || messageIds == null || messageIds.size() == 0) {
            return false;
        }

        EventRepository events = MessageUtils.getEventRepository(conversationId.toString());
        if (events == null) {
            return false;
        }

        boolean isHandled = false;
        for (CharSequence messageId : messageIds) {
            if (TextUtils.isEmpty(messageId)) {
                continue;
            }

            Event updatedEvent = events.findById(messageId.toString());
            if (updatedEvent != null) {
                updateEventInCache(updatedEvent, canAdd);
                isHandled = true;
            }
        }

        return isHandled;
    }


    private void updateEventInCache(Event updatedEvent, boolean canAdd) {
        if (mEvents != null) {
            boolean eventFound = false;
            ListIterator<Event> iterator = mEvents.listIterator();
            int position = 0;
            while (iterator.hasNext()) {
                Event event = iterator.next();
                if (event.getId().equals(updatedEvent.getId())) {
                    iterator.set(updatedEvent);
                    eventFound = true;
                    mAdapter.notifyItemChanged(getScreenPosition(position), updatedEvent);
                    break;
                }
                position++;
            }
            if (!eventFound && canAdd) {
                mEvents.add(updatedEvent);
                // refresh sections so getScreenPosition works correctly
                mAdapter.refreshSections(mEvents);
                mAdapter.notifyItemInserted(getScreenPosition(mEvents.size() - 1));
            }
        }
    }

    private void markReceivedMessagesAsRead() {
        if (mEventsView == null) {
            return;
        }

        // Old code for only marking visible messages as read
//        int firstVisible = mEventsView.getFirstVisiblePosition();
//        int lastVisible = mEventsView.getLastVisiblePosition();
//
//        Vector<Message> unreadMessages = new Vector<>(lastVisible - firstVisible + 1);
//        for (int i = firstVisible; i <= lastVisible; i++) {
//            final int position = getDataPosition(i);
//            if (position == INVALID_POSITION) {
//                continue;
//            }
//
//            final Event event = mEvents.get(position);
//            if (event instanceof Message) {
//                Message message = (Message) event;
//                if (message.getState() == MessageStates.RECEIVED) {
//                    unreadMessages.add(message);
//                }
//            }
//        }
        markReceivedMessagesAsRead(mEvents);
    }

    private void markReceivedMessagesAsRead(final @NonNull List<Event> events) {
        Vector<Message> unreadMessages = new Vector<>();

        for (int i = 0; i < events.size(); i++) {
            final Event event = events.get(i);

            if (event instanceof Message) {
                Message message = (Message) event;
                if (message.getState() == MessageStates.RECEIVED) {
                    unreadMessages.add(message);
                }

                if (message instanceof CallMessage && message.getState() != MessageStates.READ) {
                    unreadMessages.add(message);
                }
            }
        }

        if (unreadMessages.size() > 0) {
            MessageUtils.markMessagesAsRead(getConversationId(),
                    unreadMessages.toArray(new Message[unreadMessages.size()]));
        }
    }

    private String getConversationId() {
        String result = null;
        Activity activity = getActivity();
        if (activity != null) {
            result = ((ConversationActivity) activity).getPartner();
        }
        if (TextUtils.isEmpty(result)) {
            if (mEvents != null && mEvents.size() > 0) {
                result = MessageUtils.getConversationId(mEvents.get(0));
            }
        }
        return result;
    }

    protected boolean isGroupConversation() {
        boolean result = false;
        Activity activity = getActivity();
        if (activity != null) {
            Conversation conversation = ((ConversationActivity) activity).getConversation();
            if (conversation != null) {
                result = conversation.getPartner().isGroup();
            }
        }
        return result;
    }

    private boolean isTalkingToSelf() {
        boolean result = false;
        Activity activity = getActivity();
        if (activity != null) {
            result = ((ConversationActivity) activity).isTalkingToSelf();
        }
        return result;
    }

    private boolean isTalkingToScUser() {
        boolean result = false;
        Activity activity = getActivity();
        if (activity != null) {
            Conversation conversation = ((ConversationActivity) activity).getConversation();
            if (conversation != null) {
                result = Utilities.canMessage(conversation.getPartner().getUserId());
            }
        }
        return result;
    }

    private boolean refreshRetentionState(@Nullable String peerName) {
        if (!TextUtils.isEmpty(peerName) && Utilities.canMessage(peerName)) {
            mDataRetentionBanner.addConversationPartner(peerName);
            byte[] mPartnerUserInfo = ZinaNative.getUserInfoFromCache(peerName);
            if (mPartnerUserInfo != null) {
                AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(mPartnerUserInfo);
                mIsPartnerMessagingDrEnabled = /**userInfo != null
                        && (userInfo.rrmm | userInfo.rrmp | userInfo.rrap);**/false;
                mIsPartnerCallDrEnabled = /**userInfo != null
                        && (userInfo.rrcm | userInfo.rrcp);**/false;
            }
        }
        return mIsPartnerMessagingDrEnabled | mIsPartnerCallDrEnabled;
    }

    private void updateDataRetentionBanner() {
        if (isTalkingToSelf()) {
            return;
        }

        // check whether data will be retained locally and update banner
        if (mDataRetentionBanner != null) {
            boolean isDataRetained =
                    (LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp() | LoadUserInfo.isLrmp()
                            | LoadUserInfo.isLrmm() | LoadUserInfo.isLrap());
            isDataRetained |= refreshRetentionState(getConversationId());

            int visibility = isDataRetained ? View.VISIBLE : View.GONE;
            mDataRetentionBanner.setVisibility(visibility);
            mDataRetentionBanner.refreshBannerTitle();
        }
    }

    private void checkMessageRetentionStatus(Message message) {
        if (message.isRetained() != mIsPartnerMessagingDrEnabled) {
            // update user information in axolotl cache and request for activity to refresh DR banner
            final String conversationId = MessageUtils.getConversationId(message);
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    int[] errorCode = new int[1];
                    byte[] partnerUserInfo = ZinaNative.getUserInfo(conversationId, null, errorCode);
                    if (partnerUserInfo != null) {
                        AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(partnerUserInfo);
                        final StringBuilder sb = new StringBuilder();
                        if (userInfo != null) {
                            mIsPartnerMessagingDrEnabled = userInfo.rrmm | userInfo.rrmp | userInfo.rrap;
                            String organization = userInfo.retentionOrganization;
                            if (TextUtils.isEmpty(organization)) {
                                organization = getString(R.string.data_retention_remote_organization);
                            }
                            sb.append(getString(R.string.data_retention_data_retained_by, organization));
                        }
                        final Activity activity = getActivity();
                        if (mIsPartnerMessagingDrEnabled && activity != null) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Snackbar.make(findViewById(R.id.chat_content), sb.toString(),
                                            Snackbar.LENGTH_INDEFINITE)
                                            .setAction(R.string.got_it, ChatFragment.this)
                                            .show();
                                }
                            });
                        }

                        MessageUtils.requestRefresh();
                    }
                }
            });
        }
    }

    private void configureEmptyListView() {
        boolean isGroupConversation = isGroupConversation();
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(getConversationId());
        // default avatar which to use when contact has no image
        int imageId = isGroupConversation ? R.drawable.ic_profile_group : R.drawable.ic_profile;
        // for group conversations show only default avatar
        Uri photoUri = isGroupConversation ? null : contactEntry != null ? contactEntry.photoUri : null;
        if (photoUri != null) {
            // do not resize avatar image, use what size is stored
            photoUri = photoUri.buildUpon()
                    .appendQueryParameter(AvatarProvider.PARAM_AVATAR_SIZE,
                            String.valueOf(AvatarProvider.LOADED_AVATAR_SIZE))
                    .build();
        }
        int textId = isGroupConversation ? R.string.group_chat_view_empty : isTalkingToScUser()
                ? R.string.chat_view_empty : R.string.phone_chat_view_empty;
        configureEmptyListView(mEmptyListView, photoUri, textId, imageId, getResources());
    }

    public static void configureEmptyListView(View emptyListView, Uri photoUri,
            int strResId, int imageResId, Resources res) {
        QuickContactBadge emptyListViewImage =
                (QuickContactBadge) emptyListView.findViewById(R.id.emptyListViewImage);
        emptyListViewImage.setEnabled(false);

        if (photoUri == null) {
            emptyListViewImage.setImageResource(imageResId);
        } else {
            ContactPhotoManagerNew photoManager =
                    ContactPhotoManagerNew.createContactPhotoManager(SilentPhoneApplication.getAppContext());
            AvatarUtils.setPhoto(photoManager, emptyListViewImage, 0, photoUri, null, "", null, 0, true);
        }
        emptyListViewImage.setContentDescription(res.getString(strResId));

        TextView emptyListViewMessage =
                (TextView) emptyListView.findViewById(R.id.emptyListViewMessage);
        emptyListViewMessage.setText(res.getString(strResId));
    }

}
