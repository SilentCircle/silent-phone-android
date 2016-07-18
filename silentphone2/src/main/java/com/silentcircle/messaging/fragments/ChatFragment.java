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
package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.view.ActionMode;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.listener.ClickthroughWhenNotInChoiceMode;
import com.silentcircle.messaging.listener.MultipleChoiceSelector;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.FailureEventView;
import com.silentcircle.messaging.views.ListView;
import com.silentcircle.messaging.views.MessageEventView;
import com.silentcircle.messaging.views.adapters.AvatarModelViewAdapter;
import com.silentcircle.messaging.views.CallEventView;
import com.silentcircle.messaging.views.adapters.DateHeaderView;
import com.silentcircle.messaging.views.adapters.ModelViewAdapter;
import com.silentcircle.messaging.views.adapters.ModelViewType;
import com.silentcircle.messaging.views.adapters.MultiSelectModelViewAdapter;
import com.silentcircle.messaging.views.adapters.ViewType;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;

public class ChatFragment extends BaseFragment implements MultipleChoiceSelector.ActionPerformer,
        AbsListView.OnScrollListener {

    public static final String TAG_CONVERSATION_CHAT_FRAGMENT =
            "com.silentcircle.messaging.chat";

    private static final String FIRST_RUN =
            "com.silentcircle.messaging.fragments.ChatFragment.firsRun";


    private static final String TAG = ChatFragment.class.getSimpleName();

    /* Priority for this view to handle message broadcasts, higher than ConversationActivity. */
    private static final int MESSAGE_PRIORITY = 3;
    /* Invalid position in list view */
    private static final int INVALID_POSITION = -1;

    public interface Callback {

        void onActionModeCreated(ActionMode mode);

        void onActionModeDestroyed();

        void onActionPerformed();

        void performAction(int actionID, List<Event> targets);
    }

    private static final ViewType[] VIEW_TYPES = {
        new ModelViewType(IncomingMessage.class, MessageEventView.class, R.layout.messaging_chat_item_incoming_message),
        new ModelViewType(OutgoingMessage.class, MessageEventView.class, R.layout.messaging_chat_item_outgoing_message),
        new ModelViewType(CallMessage.class, CallEventView.class, R.layout.messaging_chat_item_phone),
        new ModelViewType(ErrorEvent.class, FailureEventView.class, R.layout.messaging_chat_item_error),
        new ModelViewType(Event.class, TextView.class, R.layout.messaging_chat_item_text),
        new ModelViewType(Date.class, DateHeaderView.class, R.layout.messaging_date_header_view)
    };

    private static void attachEventsToAdapter(ModelViewAdapter adapter, List<Event> events) {
        adapter.setModels(events);
        adapter.notifyDataSetChanged();
    }

    private Callback mCallback;
    private ListView mEventsView;
    private List<Event> mEvents;
    private boolean mFirstRun = true;

    private BroadcastReceiver mViewUpdater = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                pauseEventUpdate();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                resumeEventUpdate();
            } else {
                switch (Action.from(intent)) {
                    case UPDATE_CONVERSATION:
                        boolean isHandled = handleUpdateNotification(intent);
                        if (isOrderedBroadcast() && isHandled) {
                            abortBroadcast();
                        }
                        break;
                    case RECEIVE_MESSAGE:
                        handleReceiveMessageIntent(intent);
                    default:
                }
            }
        }
    };

    private MultiSelectModelViewAdapter.OnClearViewListener mAdapterClearListener =
            new MultiSelectModelViewAdapter.OnClearViewListener() {

        @Override
        public void onClearView(View view) {
            if (view instanceof MessageEventView) {
                ((MessageEventView) view).cancelUpdates();
            }
        }
    };

    private Runnable mMarkMessagesAndUpdateRunnable = new Runnable() {

        @Override
        public void run() {
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

    private void attachEventsToView(ListView view, List<Event> events) {
        AvatarModelViewAdapter adapter = new AvatarModelViewAdapter(events, VIEW_TYPES);
        view.setAdapter(adapter);

        view.setOnItemClickListener(ClickthroughWhenNotInChoiceMode.getInstance());
        view.setMultiChoiceModeListener(new ChatFragmentMultipleChoiceSelector(this, adapter,
                R.menu.multiselect_event, getString(R.string.n_selected)));
        adapter.notifyDataSetChanged();
    }

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

    public boolean hasMultipleCheckedItems() {
        return mEventsView != null && mEventsView.hasMultipleCheckedItems();
    }

    public SparseBooleanArray getCheckedItemPositions() {
        return mEventsView != null ? mEventsView.getCheckedItemPositions() : null;
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
        }
        return inflater.inflate(R.layout.messaging_chat_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEventsView = (ListView) findViewById(R.id.chat_events);
        mEventsView.setOnScrollListener(this);
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
        registerReceiver();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(FIRST_RUN, mFirstRun);
    }

    @Override
    public void onPause() {
        super.onPause();

        getActivity().unregisterReceiver(mViewUpdater);

        pauseEventUpdate();
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
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        // ensure that re-used views have their update timers scheduled properly
        // this could also be done in MessageEventView#setMessage function.
        resumeEventUpdate();
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void setEvents(List<Event> events) {
        mEvents = events;

        if (mEventsView != null) {
            ListAdapter adapter = mEventsView.getAdapter();
            if (adapter instanceof ModelViewAdapter) {
                attachEventsToAdapter((ModelViewAdapter) adapter, events);
            } else {
                attachEventsToView(mEventsView, events);
            }
            mEventsView.post(mMarkMessagesAndUpdateRunnable);
        }
    }

    public void scrollToBottom() {
        if (mEventsView != null) {
            mEventsView.setSelection(mEventsView.getCount() - 1);
        }
    }

    public void scrollToFirstUnread() {
        if (mEventsView != null && mEvents != null) {
            int firstUnreadPosition = INVALID_POSITION;
            int position = 0;
            for (Event event : mEvents) {
                if (event instanceof Message) {
                    if (((Message) event).getState() == MessageStates.RECEIVED) {
                        firstUnreadPosition = position;
                        break;
                    }
                    position += 1;
                }
            }
            // Try to scroll to first unread position showing also last read message
            firstUnreadPosition = getScreenPosition(firstUnreadPosition - 1);
            mEventsView.setSelection(firstUnreadPosition == INVALID_POSITION
                    ? mEventsView.getCount() - 1 : firstUnreadPosition);
        }
    }

    private void resumeEventUpdate() {
        if (mEventsView != null) {
            for (int i = 0; i < mEventsView.getChildCount(); i++) {
                View view = mEventsView.getChildAt(i);
                if (view instanceof MessageEventView) {
                    ((MessageEventView) view).update();
                }
            }
        }
    }

    private void pauseEventUpdate() {
        if (mEventsView != null) {
            for (int i = 0; i < mEventsView.getChildCount(); i++) {
                View view = mEventsView.getChildAt(i);
                if (view instanceof MessageEventView) {
                    ((MessageEventView) view).cancelUpdates();
                }
            }

            ListAdapter adapter = mEventsView.getAdapter();

            /*
             * RecyclerListener.onMovedToScrapHeap does not work as documented.
             * Android issue: https://code.google.com/p/android/issues/detail?id=15607
             *
             * Using workaround - track views in model view adapter.
             */

            if (adapter != null)
                ((MultiSelectModelViewAdapter) adapter).clearViews(mAdapterClearListener);
        }
    }

    private void registerReceiver() {
        Activity activity = getActivity();
        /* register for screen on/off events */
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        activity.registerReceiver(mViewUpdater, filter);

        /* register for UPDATE_CONVERSATION, RECEIVE_MESSAGE events */
        IntentFilter updateConversationFilter = Action.UPDATE_CONVERSATION.filter();
        updateConversationFilter.setPriority(MESSAGE_PRIORITY);
        IntentFilter receiveMessageFilter = Action.RECEIVE_MESSAGE.filter();
        receiveMessageFilter.setPriority(MESSAGE_PRIORITY);
        activity.registerReceiver(mViewUpdater, updateConversationFilter);
        activity.registerReceiver(mViewUpdater, receiveMessageFilter);
    }

    private boolean handleReceiveMessageIntent(Intent intent) {
        if (mEvents == null) {
            return false;
        }

        CharSequence conversationId = Extra.PARTNER.getCharSequence(intent);
        ArrayList<CharSequence> messageIds = getMessageIds(intent);

        boolean changed = false;
        if (!TextUtils.isEmpty(conversationId)) {
            if (conversationId.equals(getConversationId())) {
                for (CharSequence messageId : messageIds) {
                    if (TextUtils.isEmpty(messageId)) {
                        continue;
                    }
                    Event event = MessageUtils.getEventById(SilentPhoneApplication.getAppContext(),
                            conversationId.toString(), messageId.toString());
                    if (event != null) {
                        updateEventInCache(event);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            mEvents = MessageUtils.filter(mEvents, DialerActivity.mShowErrors);
            setEvents(mEvents);
        }

        return false;
    }

    private ArrayList<CharSequence> getMessageIds(Intent intent) {
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

    private boolean handleUpdateNotification(Intent intent) {
        boolean isHandled = false;
        int reason = Extra.REASON.getInt(intent);
        boolean forceRefresh = Extra.FORCE.getBoolean(intent);
        ArrayList<CharSequence> messageIds = getMessageIds(intent);
        CharSequence conversationId = Extra.PARTNER.getCharSequence(intent);

        // TODO: avoid need for force refresh, it slows down burn animation and burn timer updates
        if (reason == AxoMessaging.UPDATE_ACTION_MESSAGE_BURNED) {
            for (CharSequence messageId : messageIds) {
                isHandled |= handleBurnNotification(messageId);
            }
        } else if (reason == AxoMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE
                || reason == AxoMessaging.UPDATE_ACTION_MESSAGE_SEND) {
            for (CharSequence messageId : messageIds) {
                isHandled |= handleStatusUpdateNotification(conversationId, messageId);
            }
        }
        return isHandled && !forceRefresh;
    }

    private boolean handleBurnNotification(@NonNull CharSequence messageId) {
        boolean eventFound = false;

        if (mEventsView != null) {

            int firstVisible = mEventsView.getFirstVisiblePosition();
            int lastVisible = mEventsView.getLastVisiblePosition();

            // TODO: RecyclerView may handle removal animations better
            for (int i = firstVisible; i <= lastVisible; i++) {
                final int position = getDataPosition(i);
                if (position == INVALID_POSITION) {
                    continue;
                }

                final Event event = mEvents.get(position);
                if (!messageId.equals(event.getId())) {
                    continue;
                }
                eventFound = true;

                final View view = mEventsView.getChildAt(i - firstVisible);
                if (view instanceof MessageEventView) {
                    ((MessageEventView) view).animateBurn(new Runnable() {

                        @Override
                        public void run() {
                            removeEvent(event);
                        }
                    });
                } else {
                    removeEvent(event);
                }
            }
        }

        if (!eventFound) {
            for (final ListIterator<Event> iterator = mEvents.listIterator(); iterator.hasNext();) {
                final Event event = iterator.next();
                if (messageId.equals(event.getId())) {
                    eventFound = true;
                    iterator.remove();
                }
            }
            setEvents(mEvents);
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

    private boolean handleStatusUpdateNotification(CharSequence conversationId, CharSequence messageId) {
        Activity activity = getActivity();
        if (activity == null || TextUtils.isEmpty(conversationId) || TextUtils.isEmpty(messageId)) {
            return false;
        }
        Context context = activity.getApplicationContext();
        Event updatedEvent = MessageUtils.getEventById(context, conversationId.toString(),
                messageId.toString());

        boolean isHandled = false;
        if (updatedEvent != null) {
            updateEventInCache(updatedEvent);
            isHandled = true;
            setEvents(mEvents);
        }
        return isHandled;
    }

    private void updateEventInCache(Event updatedEvent) {
        if (mEvents != null) {
            boolean eventFound = false;
            ListIterator<Event> iterator = mEvents.listIterator();
            while (iterator.hasNext()) {
                Event event = iterator.next();
                if (event.getId().equals(updatedEvent.getId())) {
                    iterator.set(updatedEvent);
                    eventFound = true;
                    break;
                }
            }
            if (!eventFound) {
                mEvents.add(updatedEvent);
                mEvents = MessageUtils.filter(mEvents, DialerActivity.mShowErrors);
            }
        }
    }

    private void removeEvent(Event event) {
        if (mEvents != null) {
            mEvents.remove(event);
            setEvents(mEvents);
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

        Vector<Message> unreadMessages = new Vector<>();

        for (int i = 0; i < mEvents.size(); i++) {
            final Event event = mEvents.get(i);

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
            MessageUtils.markMessagesAsRead(unreadMessages.toArray(new Message[unreadMessages.size()]));
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

}
