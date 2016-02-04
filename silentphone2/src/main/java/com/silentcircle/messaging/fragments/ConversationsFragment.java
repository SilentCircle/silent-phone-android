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

import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
import com.silentcircle.messaging.listener.OnConfirmListener;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.thread.Updater;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.Updatable;
import com.silentcircle.messaging.views.ConversationListItem;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fragment to list conversations.
 */
public class ConversationsFragment extends ListFragment implements Updatable {

    private static final String TAG = ConversationsFragment.class.getSimpleName();

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 2;

    private static final int ITEM_NOT_SELECTED = -1;

    private static final String LAST_CONVERSATION =
            "com.silentcircle.messaging.fragments.ConversationsFragment.lastConversation";

    protected List<Conversation> mConversations;
    protected ConversationLogAdapter mAdapter;
    protected BroadcastReceiver mViewUpdater;
    protected int mSelectedItem;
    protected String mLastConversationId;

    protected Map<String, Message> mLastMessageCache = new HashMap<>();

    private final Updater mUpdater;
    private final Handler mHandler;

    private ContactsCache mContactsCache;

    public static ConversationsFragment newInstance(Bundle args) {
        ConversationsFragment fragment = new ConversationsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public ConversationsFragment() {
        mHandler = new Handler();
        mUpdater = new Updater(this);
        mSelectedItem = ITEM_NOT_SELECTED;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.message_log_fragment_new, container, false);
        if (savedInstanceState != null) {
            mLastConversationId = savedInstanceState.getString(LAST_CONVERSATION);
        }
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ListView listView = getListView();
        listView.setEmptyView(view.findViewById(R.id.empty_list_view));
        listView.setItemsCanFocus(true);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        mAdapter = new ConversationLogAdapter(getActivity());
        setListAdapter(mAdapter);

        DialerUtils.configureEmptyListView(
                listView.getEmptyView(), R.drawable.empty_call_log, R.string.chat_view_empty,
                getResources());
        ViewUtil.addBottomPaddingToListViewForFab(listView, getResources());

        refreshConversations(getActivity().getApplicationContext());
    }

    @Override
    public void onListItemClick(ListView list, View v, int position, long id) {
        Conversation conversation = mConversations.get(position);
        if (conversation != null) {
            list.setItemChecked(position, true);
            mSelectedItem = position;
            mLastConversationId = conversation.getPartner().getUsername();

            Intent intent = new Intent(getActivity(), ConversationActivity.class);
            Extra.PARTNER.to(intent, conversation.getPartner().getUsername());
            getActivity().startActivity(intent);
        }
        else {
            mSelectedItem = ITEM_NOT_SELECTED;
            Toast.makeText(getActivity(), "Unable to start conversation", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        registerViewUpdater();
        refreshConversations(getActivity().getApplicationContext());
        setHighlightedConversation();
        scheduleNextUpdate();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            Notifications.cancelMessageNotification(getActivity(), null);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(LAST_CONVERSATION, mLastConversationId);
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mViewUpdater);
        mHandler.removeCallbacks(mUpdater);
        super.onPause();
    }

    @Override
    public void update() {
        Context context = getActivity();
        if (context != null) {
            refreshConversations(context.getApplicationContext());
        }
        scheduleNextUpdate();
    }

    protected void refreshConversations(Context context) {

        if (mContactsCache == null)
            mContactsCache = new ContactsCache(context);

        RefreshContactsCache refreshContactsTask = new RefreshContactsCache(context) {

            @Override
            protected void onPostExecute(List<Conversation> conversations) {
                mConversations = conversations;
                Collections.sort(mConversations);
                setHighlightedConversation();

                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }
        };
        AsyncUtils.execute(refreshContactsTask);
    }


    private void registerViewUpdater() {

        mViewUpdater = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                switch (Action.from(intent)) {
                    case RECEIVE_MESSAGE:
                    case UPDATE_CONVERSATION:
                    case PROGRESS:
                    case CANCEL:
                        refreshConversations(context);
                        break;
                    default:
                        break;
                }
            }
        };

        registerReceiver(mViewUpdater, Action.RECEIVE_MESSAGE.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.UPDATE_CONVERSATION.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.PROGRESS.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.CANCEL.filter(), MESSAGE_PRIORITY);
    }

    private Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int priority) {
        filter.setPriority(priority);
        return getActivity().registerReceiver(receiver, filter);
    }

    private void scheduleNextUpdate() {
        mHandler.postDelayed(mUpdater, TimeUnit.MINUTES.toMillis(1));
    }

    private void setHighlightedConversation() {
        ListView listView = getListView();
        if (listView != null && !TextUtils.isEmpty(mLastConversationId)
                && mConversations != null) {
            int conversationPosition = ITEM_NOT_SELECTED;
            for (Conversation conversation : mConversations) {
                conversationPosition += 1;
                if (conversation != null
                        && mLastConversationId.equals(conversation.getPartner().getUsername())) {
                    break;
                }
            }
            if (conversationPosition > ITEM_NOT_SELECTED) {
                mSelectedItem = conversationPosition;
                listView.setItemChecked(mSelectedItem, true);
            }
        }
    }

    public ListView getListView() {
        ListView listView = null;
        try {
            listView = super.getListView();
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to get list view: " + e.getMessage());
        }
        return listView;
    }

    // ----------------------------------------------------------------------
    private class ConversationLogAdapter extends BaseAdapter /* GroupingListAdapter */ {

        private final Context mContext;

        public ConversationLogAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return mConversations != null ? mConversations.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return mConversations != null ? mConversations.get(position) : 0;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Conversation conversation = (Conversation) getItem(position);
            if (convertView == null) {
                convertView = new ConversationListItem(getActivity());
            }
            Event lastEvent = mLastMessageCache.get(conversation.getPartner().getUsername());

            ConversationListItem conversationListItem = (ConversationListItem) convertView;
            conversationListItem.setConversation(conversation);
            conversationListItem.setEvent(lastEvent);
            conversationListItem.setOnConversationItemClickListener(
                    new ConversationItemClickListener(conversation));
            conversationListItem.setTag(position);

            return convertView;
        }

    }

    protected class ConversationItemClickListener
            implements ConversationListItem.OnConversationItemClickListener {

        private final Conversation mConversation;

        public ConversationItemClickListener(final Conversation conversation) {
            mConversation = conversation;
        }

        @Override
        public void onConversationClick(ConversationListItem view) {
            onListItemClick(getListView(), view, (Integer) view.getTag(), 0);
        }

        @Override
        public void onConversationDeleteClick(ConversationListItem view) {
            OnConfirmListener onConfirm = new OnConfirmListener() {
                @Override
                public void onConfirm(Context context) {
                    AxoMessaging.getInstance(context.getApplicationContext())
                            .getConversations().remove(mConversation);
                    refreshConversations(context);
                }
            };
            new LaunchConfirmDialogOnClick(R.string.are_you_sure,
                    R.string.warning_delete_conversation,
                    R.string.dialog_button_cancel,
                    R.string.delete,
                    onConfirm).show(view.getContext());
        }

        @Override
        public void onConversationContactClick(ConversationListItem view) {
            // TODO:
        }
    }

    private class RefreshContactsCache extends AsyncTask<Void, Void, List<Conversation>> {

        private final Context mContext;

        public RefreshContactsCache(final Context context) {
            mContext = context;
        }

        @Override
        protected List<Conversation> doInBackground(Void... params) {
            ContactsCache.buildContactsCache(mContext);

            // build cache of last messages for conversations
            AxoMessaging axoMessaging = AxoMessaging.getInstance(mContext);
            boolean axoRegistered = axoMessaging.isRegistered();

            if (!axoRegistered) {
                // return empty list if Axolotl has not been registered yet
                return new ArrayList<>();
            }

            ConversationRepository repository = axoMessaging.getConversations();
            List<Conversation> conversations = repository.list();
            Iterator<Conversation> iterator = conversations.iterator();
            while (iterator.hasNext()) {
                Conversation conversation = iterator.next();

                if (conversation == null || conversation.getLastModified() == 0
                        || !repository.historyOf(conversation).exists()) {
                    iterator.remove();
                    continue;
                }

                String partnerUserName = conversation.getPartner().getUsername();
                List<Event> events = repository.historyOf(conversation).list();
                // find last incoming or outgoing message
                if (events != null /* && events.size() > 0 */) {
                    for (Event event : events) {
                        if (event instanceof IncomingMessage || event instanceof OutgoingMessage) {
                            if (!((Message) event).isExpired()) {
                                Event lastEvent = event;
                                mLastMessageCache.put(partnerUserName, (Message) lastEvent);
                                break;
                            }
                        }
                    }
                } else {
                    mLastMessageCache.remove(partnerUserName);
                    iterator.remove();
                }
            }

            return conversations;
        }
    }
}
