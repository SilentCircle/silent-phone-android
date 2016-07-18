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
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.thread.Updater;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.Updatable;
import com.silentcircle.messaging.views.ConversationListItem;
import com.silentcircle.messaging.views.ScreenLockView;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Fragment to list conversations.
 */
public class ConversationsFragment extends ListFragment implements Updatable,
        ScreenLockView.OnUnlockListener, ConversationListItem.OnConversationItemClickListener,
        AlertDialogFragment.OnAlertDialogConfirmedListener {

    private static final String TAG = ConversationsFragment.class.getSimpleName();

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 2;

    private static final int ITEM_NOT_SELECTED = -1;

    /* Dialog request code for delete conversation confirmation */
    private static final int DELETE_CONVERSATION = 1;

    /* Events page size to use when determining conversation's last event */
    private static final int PAGE_SIZE = 10;

    private static final String LAST_CONVERSATION =
            "com.silentcircle.messaging.fragments.ConversationsFragment.lastConversation";
    private static final String LAST_MESSAGE_CACHE =
            "com.silentcircle.messaging.fragments.ConversationsFragment.lastMessageCache";
    private static final String LOCK_TIME_PERSISTED =
            "com.silentcircle.messaging.fragments.ConversationsFragment.lockTimePersisted";
    private static final String DELETE_CONVERSATION_ID =
            "com.silentcircle.messaging.fragments.ConversationsFragment.deleteConversationId";

    protected ScreenLockView mPasswordOverlay;
    protected View mProgressBar;

    protected List<Conversation> mConversations;
    protected ConversationLogAdapter mAdapter;
    protected BroadcastReceiver mViewUpdater;
    protected int mSelectedItem;
    protected String mLastConversationId;

    protected ConcurrentHashMap<String, Message> mLastMessageCache = null;
    protected static final Event DUMMY_EVENT = new Event("Loading...");

    private final Updater mUpdater;
    private final Handler mHandler;

    private AsyncTask mConversationsRefreshTask;
    private AsyncTask mEventsRefreshTask;

    private ContactsCache mContactsCache;

    protected boolean mLockTimePersisted = true;

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
            /*
            mLastMessageCache =
                (HashMap<String, Message>) savedInstanceState.getSerializable(LAST_MESSAGE_CACHE);
             */
            mLockTimePersisted = savedInstanceState.getBoolean(LOCK_TIME_PERSISTED, false);
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

        mPasswordOverlay  = (ScreenLockView) view.findViewById(R.id.password_overlay);
        mPasswordOverlay.setOnUnlockListener(this);

        mProgressBar = view.findViewById(R.id.list_view_progress);

        mAdapter = new ConversationLogAdapter(getActivity());
        setListAdapter(mAdapter);

        DialerUtils.configureEmptyListView(
                listView.getEmptyView(), R.drawable.empty_call_log, R.string.chat_view_empty,
                getResources());
        ViewUtil.addBottomPaddingToListViewForFab(listView, getResources());
    }

    @Override
    public void onListItemClick(ListView list, View v, int position, long id) {
        Conversation conversation = mConversations.get(position);
        if (conversation != null) {
            list.setItemChecked(position, true);
            mSelectedItem = position;
            mLastConversationId = conversation.getPartner().getUserId();

            Intent intent = new Intent(getActivity(), ConversationActivity.class);
            Extra.PARTNER.to(intent, conversation.getPartner().getUserId());
            Extra.ALIAS.to(intent, conversation.getPartner().getAlias());
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

        lockViewIfNecessary();

        scheduleNextUpdate();

        Notifications.cancelMessageNotification(SilentPhoneApplication.getAppContext(), null);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            updateLastLockTimeIfNecessary();
            lockViewIfNecessary();
            Notifications.cancelMessageNotification(SilentPhoneApplication.getAppContext(), null);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(LAST_CONVERSATION, mLastConversationId);
        /* TODO: save cache in saved instance, currently it occasionally fails
           with BadParcelableException for event

           outState.putSerializable(LAST_MESSAGE_CACHE, mLastMessageCache);
        */
        outState.putBoolean(LOCK_TIME_PERSISTED, mLockTimePersisted);
    }

    @Override
    public void onDestroyView() {
        mAdapter = null;
        mPasswordOverlay = null;
        mProgressBar = null;
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        updateLastLockTimeIfNecessary();
        getActivity().unregisterReceiver(mViewUpdater);
        mHandler.removeCallbacks(mUpdater);

        cancelTasks();

        super.onPause();
    }

    @Override
    public void update() {
        Context context = getActivity();
        if (context != null) {
            refreshConversations(context.getApplicationContext());
        }
        /*
         * If periodic locks are necessary (while on the chat view, without leaving)
         *
        lockViewIfNecessary();
         */
        scheduleNextUpdate();
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

    @Override
    public void onUnlock() {
        if (mPasswordOverlay != null) {
            mPasswordOverlay.setVisibility(View.GONE);
        }
        mLockTimePersisted = false;
        updateLastLockTimeIfNecessary();
    }

    @Override
    public void onConversationClick(ConversationListItem view) {
        onListItemClick(getListView(), view, (Integer) view.getTag(), 0);
    }

    @Override
    public void onConversationDeleteClick(ConversationListItem view) {
        Bundle bundle = new Bundle();
        Conversation conversation = mConversations.get((Integer) view.getTag());
        bundle.putString(DELETE_CONVERSATION_ID, conversation.getPartner().getUserId());
        AlertDialogFragment dialogFragment = AlertDialogFragment.getInstance(
                R.string.are_you_sure,
                R.string.warning_delete_conversation,
                R.string.dialog_button_cancel,
                R.string.delete,
                bundle,
                false);
        dialogFragment.setTargetFragment(this, DELETE_CONVERSATION);
        dialogFragment.show(getFragmentManager(), AlertDialogFragment.TAG_ALERT_DIALOG);
    }

    @Override
    public void onConversationContactClick(ConversationListItem view) {
        // TODO
    }

    @Override
    public void onAlertDialogConfirmed(DialogInterface dialog, int requestCode, Bundle bundle,
            boolean saveChoice) {
        if (requestCode == DELETE_CONVERSATION && bundle != null) {
            String conversationPartner = bundle.getString(DELETE_CONVERSATION_ID);
            if (TextUtils.isEmpty(conversationPartner))
                return;

            AxoMessaging axoMessaging = AxoMessaging.getInstance(SilentPhoneApplication.getAppContext());
            boolean axoRegistered = axoMessaging.isRegistered();
            if (axoRegistered) {
                Log.d(TAG, "Delete conversation " + conversationPartner);
                Conversation conversation =
                        axoMessaging.getOrCreateConversation(conversationPartner);
                axoMessaging.getConversations().remove(conversation);
                refreshConversations(SilentPhoneApplication.getAppContext());
            }
            else {
                Log.w(TAG, "Cannot delete conversation " + conversationPartner
                        + ", Axolotl not registered");
            }
        }
    }

    protected void refreshConversations(final Context context) {

        if (mContactsCache == null) {
            mContactsCache = ContactsCache.getInstance(context);
        }

        cancelTasks();

        /* refresh conversations list */
        mConversationsRefreshTask = AsyncUtils.execute(new RefreshConversationsList(context, this));

        /* To show progress on every refresh, set it to visible here. This creates a flicker
         * as view is refreshed every minute to keep counters running.
        if (mProgressBar != null && (mConversations == null || mConversations.size() == 0)) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
         */
    }

    protected void refreshConversationCache(final List<Conversation> conversations) {
        mConversationsRefreshTask = null;

        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }

        mConversations = conversations;
        Collections.sort(mConversations);
        setHighlightedConversation();

        if (mConversations.size() > 0) {
            RefreshLastEventsCache refreshEventsCacheTask =
                    new RefreshLastEventsCache(activity.getApplicationContext(), this);

            Conversation[] conversationArray =
                    mConversations.toArray(new Conversation[mConversations.size()]);
            mEventsRefreshTask = AsyncUtils.executeSerial(refreshEventsCacheTask, conversationArray);
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    protected void updateConversationCache(final List<Conversation> conversations,
            final Map<String, Message> lastMessageCache) {
        mConversations = conversations;
        Collections.sort(mConversations);
        setHighlightedConversation();

        if (mLastMessageCache == null) {
            mLastMessageCache = new ConcurrentHashMap<>();
        }
        mLastMessageCache.clear();
        if (lastMessageCache != null) {
            mLastMessageCache.putAll(lastMessageCache);
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void registerViewUpdater() {

        mViewUpdater = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                switch (Action.from(intent)) {
                    case RECEIVE_MESSAGE:
                    case UPDATE_CONVERSATION:
                        /*
                        CharSequence conversationId = Extra.PARTNER.getCharSequence(intent);
                        ConversationUtils.updateUnreadMessageCount(context, conversationId);
                         */
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
                        && mLastConversationId.equals(conversation.getPartner().getUserId())) {
                    break;
                }
            }
            if (conversationPosition > ITEM_NOT_SELECTED) {
                mSelectedItem = conversationPosition;
                listView.setItemChecked(mSelectedItem, true);
            }
        }
    }

    private void lockViewIfNecessary() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        byte[] chatKeyData =
                KeyManagerSupport.getPrivateKeyData(activity.getContentResolver(),
                        ConfigurationUtilities.getChatProtectionKey());
        MessagingPreferences preferences =
                MessagingPreferences.getInstance(activity.getApplicationContext());

        boolean lockingEnabled = chatKeyData != null;
        boolean expired = lockingEnabled && preferences.isMessagingUnlockTimeExpired();

        if (mPasswordOverlay != null) {
            mPasswordOverlay.setVisibility(expired ? View.VISIBLE : View.GONE);
        }
    }

    private void updateLastLockTimeIfNecessary() {
        if (!mLockTimePersisted) {
            MessagingPreferences.getInstance(getActivity().getApplicationContext())
                    .setLastMessagingUnlockTime(System.currentTimeMillis());
        }
        mLockTimePersisted = true;
    }

    private void cancelTasks() {
        if (mConversationsRefreshTask != null) {
            mConversationsRefreshTask.cancel(false);
            mConversationsRefreshTask = null;
        }
        if (mEventsRefreshTask != null) {
            mEventsRefreshTask.cancel(false);
            mEventsRefreshTask = null;
        }
    }

    // ----------------------------------------------------------------------
    private class ConversationLogAdapter extends BaseAdapter /* GroupingListAdapter */ {

        private final class ContactInfoRequest {
            public ConversationListItem view;
            public Conversation conversation;
            public String number;
        }

        private final Context mContext;
        private final ContactPhotoManagerNew mContactPhotoManager;

        public ConversationLogAdapter(Context context) {
            mContext = context;
            mContactPhotoManager =
                    ContactPhotoManagerNew.getInstance(mContext.getApplicationContext());
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
            String userId = conversation.getPartner().getUserId();
            Event lastEvent = mLastMessageCache == null || TextUtils.isEmpty(userId)
                    ? DUMMY_EVENT : mLastMessageCache.get(userId);

            ConversationListItem conversationListItem = (ConversationListItem) convertView;
            conversationListItem.setEvent(lastEvent);
            conversationListItem.setOnConversationItemClickListener(ConversationsFragment.this);
            conversationListItem.setTag(position);
            conversationListItem.setConversation(conversation, mContactPhotoManager);

            return convertView;
        }

    }

    private static class RefreshConversationsList extends AsyncTask<Void, Void, List<Conversation>> {

        private final Context mContext;
        private final WeakReference<ConversationsFragment> mFragmentReference;

        public RefreshConversationsList(final Context context, final ConversationsFragment fragment) {
            mContext = context;
            mFragmentReference = new WeakReference<>(fragment);

        }

        @Override
        protected List<Conversation> doInBackground(Void... params) {

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
                        || !repository.historyOf(conversation).exists()
                        || conversation.getPartner().getUserId() == null) {
                    iterator.remove();
                }
            }

            return conversations;
        }

        @Override
        protected void onPostExecute(List<Conversation> conversations) {
            ConversationsFragment fragment = mFragmentReference.get();
            if (fragment != null) {
                fragment.refreshConversationCache(conversations);
            }
        }
    }

    private static class RefreshLastEventsCache extends AsyncTask<Conversation, Void, List<Conversation>> {

        private final Context mContext;

        private final WeakReference<ConversationsFragment> mFragmentReference;

        private HashMap<String, Message> mLastMessages = new HashMap<>();

        public RefreshLastEventsCache(final Context context, final ConversationsFragment fragment) {
            mContext = context;
            mFragmentReference = new WeakReference<>(fragment);
        }

        @Override
        protected List<Conversation> doInBackground(Conversation... params) {
            // build cache of last messages for conversations

            AxoMessaging axoMessaging = AxoMessaging.getInstance(mContext);
            boolean axoRegistered = axoMessaging.isRegistered();

            if (!axoRegistered) {
                return Arrays.asList(params);
            }

            ConversationRepository repository = axoMessaging.getConversations();

            List<Conversation> result = new ArrayList<>();
            for (Conversation conversation : params) {

                String partnerUserName = conversation.getPartner().getUserId();
                if (partnerUserName == null) {
                    continue;
                }
                List<Event> events = null;

                // going downwards from top, youngest element
                EventRepository.PagingContext pagingContext =
                        new EventRepository.PagingContext(
                                EventRepository.PagingContext.START_FROM_YOUNGEST, PAGE_SIZE);
                int eventCount = 0;
                boolean hasMessage = false;
                do {
                    List<Event> eventList = repository.historyOf(conversation).list(pagingContext);
                    if (eventList != null) {
                        eventCount = eventList.size();
                        if (events == null) {
                            events = eventList;
                        }
                        else {
                            // FIXME: can we trust and look just at the next page?
                            events.addAll(eventList);
                        }
                        eventList = MessageUtils.filter(eventList, false);
                        hasMessage = findLastEvent(partnerUserName, eventList);
                    }
                } while (eventCount > 0 && !hasMessage);

                if (events != null) {
                    result.add(conversation);
                }
                if (!hasMessage) {
                    mLastMessages.remove(partnerUserName);
                }
            }

            return result;
        }

        public HashMap<String, Message> getLastMessageCache() {
            return mLastMessages;
        }

        @Override
        protected void onPostExecute(List<Conversation> conversations) {
            ConversationsFragment fragment = mFragmentReference.get();
            if (fragment != null) {
                fragment.updateConversationCache(conversations, getLastMessageCache());
            }
        }

        private boolean findLastEvent(String partnerUserName, List<Event> events) {
            boolean hasMessage = false;
            ListIterator<Event> iterator = events.listIterator(events.size());
            while (iterator.hasPrevious() && partnerUserName != null) {
                Event event = iterator.previous();
                if (event instanceof IncomingMessage || event instanceof OutgoingMessage) {
                    if (!((Message) event).isExpired()
                            && ((Message) event).getState() != MessageStates.BURNED) {
                        mLastMessages.put(partnerUserName, (Message) event);
                        hasMessage = true;
                        break;
                    }
                }
            }
            return hasMessage;
        }
    }

}

