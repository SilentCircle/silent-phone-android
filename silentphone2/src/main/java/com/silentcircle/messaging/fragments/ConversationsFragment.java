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

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.common.base.Objects;
import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.CallUtils;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.DataRetentionBanner;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.thread.Updater;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.CachedEvent;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.messaging.util.Updatable;
import com.silentcircle.messaging.views.ConversationListItem;
import com.silentcircle.messaging.views.SwipeRevealLayout;
import com.silentcircle.messaging.views.ViewBinderHelper;
import com.silentcircle.messaging.views.adapters.PaddedDividerItemDecoration;
import com.silentcircle.messaging.views.adapters.ResolvingUserAdapter;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
public class ConversationsFragment extends BaseFragment implements Updatable,
        ConversationListItem.OnConversationItemClickListener,
        AlertDialogFragment.OnAlertDialogConfirmedListener {

    private static final String TAG = ConversationsFragment.class.getSimpleName();

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 2;

    private static final int ITEM_NOT_SELECTED = -1;

    /* Dialog request code for delete conversation confirmation */
    private static final int DELETE_CONVERSATION = 1;

    private static final long CONVERSATIONS_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(1);

    private static final ArrayList<Conversation> EMPTY_LIST = new ArrayList<>();

    private static final String DELETE_CONVERSATION_ID =
            "com.silentcircle.messaging.fragments.ConversationsFragment.deleteConversationId";

    protected View mProgressBar;

    protected List<Conversation> mConversations = new ArrayList<>();
    protected ConversationLogAdapter mAdapter;
    protected MessagingBroadcastReceiver mViewUpdater;
    protected DataRetentionBanner mDataRetentionBanner;
    protected com.silentcircle.messaging.views.RecyclerView mRecyclerView;
    protected View mEmptyView;

    protected ConcurrentHashMap<String, CachedEvent> mLastMessageCache = new ConcurrentHashMap<>();
    protected static final Event DUMMY_EVENT = new Event("Loading...");
    protected static final Event NO_MESSAGES_EVENT = new Event("No messages");

    protected ConcurrentHashMap<String, Boolean> mRetentionStateCache;

    private final Updater mUpdater;
    private final Handler mHandler;

    private AsyncTask mConversationsRefreshTask;
    private AsyncTask mEventsRefreshTask;

    private static final CachedEvent CACHED_DUMMY_EVENT = new CachedEvent();
    private static final CachedEvent CACHED_NO_MESSAGES_EVENT = new CachedEvent();

    private String mPrefixYou;

    private Runnable mEventUpdater = new Runnable() {
        @Override
        public void run() {
            if (mAdapter == null) {
                return;
            }
            for (CachedEvent lastEvent : mLastMessageCache.values()) {
                Event event = lastEvent.event;
                if (event instanceof Message && ((Message) event).isExpired()) {
                    String conversationId = MessageUtils.getConversationId(event);
                    mAdapter.doMessageLookup(getConversationPosition(conversationId), conversationId);
                }
            }
            scheduleNextEventUpdate();
        }
    };

    public static ConversationsFragment newInstance(Bundle args) {
        ConversationsFragment fragment = new ConversationsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public ConversationsFragment() {
        mHandler = new Handler();
        mUpdater = new Updater(this);
        mRetentionStateCache = new ConcurrentHashMap<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.message_log_fragment_new, container, false);
        DUMMY_EVENT.setText(getString(R.string.messaging_conversation_list_loading));
        DUMMY_EVENT.setId("00000000-0000-0000-0000-000000000000");
        CACHED_DUMMY_EVENT.setEvent(getActivity(), DUMMY_EVENT);
        NO_MESSAGES_EVENT.setText(getString(R.string.messaging_conversation_list_no_messages));
        NO_MESSAGES_EVENT.setId("00000000-0000-0000-0000-000000000001");
        CACHED_NO_MESSAGES_EVENT.setEvent(getActivity(), NO_MESSAGES_EVENT);
        mPrefixYou = getString(R.string.messaging_conversation_list_prefix_you);
        return view;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDataRetentionBanner = (DataRetentionBanner) view.findViewById(R.id.data_retention_status);

        mRecyclerView = (com.silentcircle.messaging.views.RecyclerView) view.findViewById(android.R.id.list);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity()) {

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        mRecyclerView.setLayoutManager(layoutManager);

        mAdapter = new ConversationLogAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (mAdapter != null) {
                    mAdapter.clearSelectedPosition();
                }
            }
        });

        Resources resources = getResources();
        PaddedDividerItemDecoration itemDecoration = new PaddedDividerItemDecoration(getActivity());
        itemDecoration.setMargins(resources.getDimensionPixelSize(R.dimen.conversation_log_outer_margin_left),
                resources.getDimensionPixelSize(R.dimen.conversation_log_outer_margin));
        mRecyclerView.addItemDecoration(itemDecoration);

        mProgressBar = view.findViewById(R.id.list_view_progress);

        mEmptyView = view.findViewById(R.id.empty_list_view);
        DialerUtils.configureEmptyListView(mEmptyView, R.drawable.no_conversation_image,
                R.string.chats_view_empty, R.string.chats_view_empty_header, getResources());
        mRecyclerView.setEmptyView(mEmptyView);

        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        mRecyclerView.setItemAnimator(animator);

        ViewUtil.addBottomPaddingToListViewForFab(mRecyclerView, getResources());
    }

    @Override
    public void onResume() {
        super.onResume();
        registerViewUpdater();
        refreshConversations();

        scheduleNextUpdate();

        updateDataRetentionBanner();

        Notifications.cancelMessageNotification(SilentPhoneApplication.getAppContext());
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            Notifications.cancelMessageNotification(SilentPhoneApplication.getAppContext());
        }
    }

    @Override
    public void onDestroyView() {
        mAdapter = null;
        mProgressBar = null;
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        if (mAdapter != null) {
            mAdapter.stopRequestProcessing();
        }

        unregisterMessagingReceiver(mViewUpdater);
        mHandler.removeCallbacks(mUpdater);
        mHandler.removeCallbacks(mEventUpdater);

        cancelTasks();

        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mAdapter != null) {
            mAdapter.stopRequestProcessing();
        }

        super.onDestroy();
    }

    @Override
    public void update() {
        Context context = getActivity();
        if (context != null) {
            refreshConversations();
        }
        scheduleNextUpdate();
    }

    @Override
    public void onConversationClick(ConversationListItem view) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        Conversation conversation = getConversation(view);
        if (conversation != null) {
            Intent intent = new Intent(activity, ConversationActivity.class);
            Extra.PARTNER.to(intent, conversation.getPartner().getUserId());
            Extra.ALIAS.to(intent, conversation.getPartner().getAlias());
            // add unread message count as it is known in this view
            intent.putExtra(ConversationActivity.UNREAD_MESSAGE_COUNT, getUnreadMessageCount()
                    - conversation.getUnreadMessageCount() - conversation.getUnreadCallMessageCount());
            activity.startActivity(intent);
            cancelTasks();
        }
        else {
            Toast.makeText(activity, "Unable to start conversation", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConversationDeleteClick(ConversationListItem view) {
        if (mAdapter != null) {
            mAdapter.clearSelectedPosition();
        }

        Bundle bundle = new Bundle();
        Conversation conversation = getConversation(view);
        if (conversation == null) {
            Log.w(TAG, "Could not find conversation for view");
            return;
        }
        bundle.putString(DELETE_CONVERSATION_ID, conversation.getPartner().getUserId());
        AlertDialogFragment dialogFragment = AlertDialogFragment.getInstance(
                R.string.are_you_sure,
                conversation.getPartner().isGroup()
                        ? R.string.warning_leave_group_conversation
                        : R.string.warning_delete_conversation,
                R.string.dialog_button_cancel,
                R.string.delete,
                bundle,
                false);
        dialogFragment.setTargetFragment(this, DELETE_CONVERSATION);
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .add(dialogFragment, AlertDialogFragment.TAG_ALERT_DIALOG)
                .commitAllowingStateLoss();
    }

    @Override
    public void onConversationContactClick(ConversationListItem view) {
        // TODO
    }

    @Override
    public void onCallClick(ConversationListItem view) {
        if (mAdapter != null) {
            mAdapter.clearSelectedPosition();
        }

        Conversation conversation = getConversation(view);
        if (conversation != null) {
            final String userId = conversation.getPartner().getUserId();
            CallUtils.checkAndLaunchSilentPhoneCall(getActivity(), userId);
        }
    }

    @Override
    public void onAlertDialogConfirmed(DialogInterface dialog, int requestCode, Bundle bundle,
            boolean saveChoice) {
        if (requestCode == DELETE_CONVERSATION && bundle != null) {

            String conversationPartner = bundle.getString(DELETE_CONVERSATION_ID);
            if (TextUtils.isEmpty(conversationPartner)) {
                return;
            }

            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
            boolean axoRegistered = axoMessaging.isRegistered();
            if (axoRegistered) {
                Log.d(TAG, "Delete conversation " + conversationPartner);

                Conversation conversationToDelete = null;
                for (final ListIterator<Conversation> iterator = mConversations.listIterator(); iterator.hasNext();) {
                    final Conversation conversation = iterator.next();
                    final int position = iterator.previousIndex();
                    if (conversationPartner.equals(conversation.getPartner().getUserId())) {
                        conversationToDelete = conversation;
                        iterator.remove();
                        if (mAdapter != null) {
                            mAdapter.notifyItemRemoved(position);
                        }
                        break;
                    }
                }

                if (conversationToDelete != null) {
                    final ConversationRepository repository = axoMessaging.getConversations();
                    deleteConversation(repository, conversationToDelete);
                }
            }
            else {
                Log.w(TAG, "Cannot delete conversation " + conversationPartner
                        + ", Zina not registered");
            }
            // no need to leave group as invitation not accepted
            refreshConversations();
        }
    }

    @Override
    public void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        if (!isAdded()) {
            return;
        }
        updateDataRetentionBanner();
    }

    private void updateDataRetentionBanner() {
        // check whether data will be retained locally and update banner
        if (mDataRetentionBanner != null) {
            boolean isDataRetained =
                    (LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp() | LoadUserInfo.isLrmp()
                            | LoadUserInfo.isLrmm() | LoadUserInfo.isLrap());

            int visibility = isDataRetained ? View.VISIBLE : View.GONE;
            mDataRetentionBanner.setVisibility(visibility);
            mDataRetentionBanner.refreshBannerTitle();
        }
    }

    public void onCallStateChanged(@Nullable CallState call, TiviPhoneService.CT_cb_msg msg) {
        if (mAdapter != null && call != null && mConversations != null) {
            String conversationId = Utilities.getPeerName(call);
            conversationId = Utilities.removeUriPartsSelective(conversationId);
            if (!TextUtils.isEmpty(conversationId)) {
                int position = 0;
                for (Conversation conversation : mConversations) {
                    if (conversationId.equals(conversation.getPartner().getUserId())) {
                        Log.d(TAG, "Conversation changed for [" + conversationId
                                + "] notify position " + position);
                        mAdapter.notifyItemChanged(position);
                        break;
                    }
                    position++;
                }
            }
        }
    }

    protected void refreshConversations() {
        if (mAdapter != null) {
            mAdapter.clearSelectedPosition();
        }

        if (mConversationsRefreshTask != null) {
            return;
        }

        /* refresh conversations list */
        mConversationsRefreshTask = AsyncUtils.execute(new RefreshConversationsList(this));

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

        if (conversations != null) {
            mConversations.clear();
            mConversations.addAll(conversations);
        }
        Collections.sort(mConversations);

        if (mConversations.size() > 0) {
            for (Conversation conversation : mConversations) {
                final String userId = conversation.getPartner().getUserId();
                if (!mLastMessageCache.containsKey(userId)) {
                    mLastMessageCache.put(userId, CACHED_DUMMY_EVENT);
                }
            }

            if (mEventsRefreshTask == null) {
                RefreshLastEventsCache refreshEventsCacheTask = new RefreshLastEventsCache(this);
                Conversation[] conversationArray =
                        mConversations.toArray(new Conversation[mConversations.size()]);
                mEventsRefreshTask = AsyncUtils.executeSerial(refreshEventsCacheTask, conversationArray);
            }
        }

        notifyConversationsChanged();
    }

    protected void sortConversations() {
        // TODO update only when isSorted(mConversations) is false
        Collections.sort(mConversations);
        mRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                notifyConversationsChanged();
            }
        });
    }

    protected void updateConversationEventCache(@Nullable final Pair<String, CachedEvent> entry) {
        if (entry != null && entry.first != null && entry.second != null) {
            mLastMessageCache.put(entry.first, entry.second);
            if (mRecyclerView != null) {
                mRecyclerView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mAdapter != null) {
                            int position = getConversationPosition(entry.first);
                            if (position != ITEM_NOT_SELECTED) {
                                mAdapter.notifyItemChanged(position);
                            }
                        }
                    }
                });
            }
        }
    }

    @Nullable
    private Conversation getConversation(int position) {
        Conversation conversation = null;
        if (position > ITEM_NOT_SELECTED && position < mConversations.size()) {
            conversation = mConversations.get(position);
        }
        return conversation;
    }

    @Nullable
    private Conversation getConversation(final @NonNull View view) {
        RecyclerView.ViewHolder viewHolder =
                (mRecyclerView != null) ? mRecyclerView.getChildViewHolder(view) : null;
        int position = (viewHolder != null) ? viewHolder.getLayoutPosition() : ITEM_NOT_SELECTED;
        return getConversation(position);
    }

    private int getConversationPosition(@Nullable String conversationId) {
        if (TextUtils.isEmpty(conversationId)) {
            return ITEM_NOT_SELECTED;
        }
        int result = ITEM_NOT_SELECTED;
        int position = 0;
        for (Conversation conversation : mConversations) {
            if (TextUtils.equals(conversationId, conversation.getPartner().getUserId())) {
                result = position;
                break;
            }
            position++;
        }
        return result;
    }

    private int replaceConversationInCache(final @Nullable Conversation conversation) {
        int result = ITEM_NOT_SELECTED;
        if (conversation != null) {
            final String userId = conversation.getPartner().getUserId();
            final int position = getConversationPosition(userId);
            if (position == ITEM_NOT_SELECTED) {
                // conversation not yet cached, add it
                if (mConversations.add(conversation)) {
                    result = mConversations.size() - 1;
                }
                mLastMessageCache.put(userId, CACHED_DUMMY_EVENT);
            } else if (position > ITEM_NOT_SELECTED && position < mConversations.size()) {
                mConversations.set(position, conversation);
                result = position;
            }
        }
        return result;
    }

    private void notifyConversationsChanged() {
        if (mAdapter != null) {
            if (mAdapter.getItemCount() != mConversations.size()) {
                mAdapter.setItems(mConversations);
                mAdapter.notifyDataSetChanged();
            }
            else {
                mAdapter.notifyItemRangeChanged(0, mConversations.size());
            }
        }
    }

    private int getUnreadMessageCount() {
        int result = 0;
        for (Conversation conversation : mConversations) {
            result += conversation.getUnreadMessageCount()
                    + conversation.getUnreadCallMessageCount();
        }
        return result;
    }

    private void registerViewUpdater() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        mViewUpdater = new MessagingBroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                boolean consumeBroadcast = true;
                CharSequence conversationId = Extra.PARTNER.getCharSequence(intent);
                boolean mute = Extra.MUTE.getBoolean(intent);
                switch (Action.from(intent)) {
                    case RECEIVE_MESSAGE:
                        /*
                         * To have notification as banner, do not stop broadcast
                         * and allow it to propagate to notification handler which has lower
                         * priority.
                         * To avoid two sound notifications, a special notification may be
                         * necessary.
                        consumeBroadcast = false;
                         */
                        if (!mute) {
                            SoundNotifications.playReceiveMessageSound();
                        }
                    case UPDATE_CONVERSATION:
                        refreshConversation(conversationId);
                        setConsumed(consumeBroadcast);
                        break;
                    case REFRESH_SELF:
                        updateDataRetentionBanner();
                        refreshConversation(conversationId);
                        break;
                    case REFRESH_CONTACT:
                        if (mAdapter != null) {
                            String id = TextUtils.isEmpty(conversationId) ? null : conversationId.toString();
                            int position = getConversationPosition(id);
                            mAdapter.notifyItemChanged(position);
                        }
                        setConsumed(consumeBroadcast);
                        break;
                    default:
                        break;
                }
            }
        };

        IntentFilter filter = Action.filter(
                // message events
                Action.RECEIVE_MESSAGE, Action.UPDATE_CONVERSATION, Action.REFRESH_SELF);
        registerMessagingReceiver(activity, mViewUpdater, filter, MESSAGE_PRIORITY);
    }

    private void refreshConversation(@Nullable CharSequence conversationId) {
        String id = TextUtils.isEmpty(conversationId) ? null : conversationId.toString();
        if (!TextUtils.isEmpty(id) && mAdapter != null) {
            final Conversation conversation = ConversationUtils.getDisplayableConversation(id);
            final int positionInCache = replaceConversationInCache(conversation);
            if (positionInCache != ITEM_NOT_SELECTED) {
                mAdapter.doMessageLookup(positionInCache, id);
            }
        }
        else {
            // fall back to refreshing the whole list
            refreshConversations();
        }
    }

    private void scheduleNextUpdate() {
        mHandler.removeCallbacks(mUpdater);
        mHandler.postDelayed(mUpdater, CONVERSATIONS_UPDATE_INTERVAL);

        scheduleNextEventUpdate();
    }

    private void scheduleNextEventUpdate() {
        mHandler.removeCallbacks(mEventUpdater);
        long currentMillis = System.currentTimeMillis();
        long autoRefreshTime = Long.MAX_VALUE;

        // next update time is when next message will expire, minimum 1 second
        for (CachedEvent lastEvent : mLastMessageCache.values()) {
            if (lastEvent.event instanceof Message) {
                autoRefreshTime = Math.min(autoRefreshTime, ((Message) lastEvent.event).getExpirationTime());
            }
        }

        if (autoRefreshTime < Long.MAX_VALUE) {
            mHandler.postDelayed(mEventUpdater,
                    Math.max(TimeUnit.SECONDS.toMillis(1), (autoRefreshTime - currentMillis)));
        }
    }

    private void cancelTasks() {
        if (mConversationsRefreshTask != null) {
            mConversationsRefreshTask.cancel(true);
            mConversationsRefreshTask = null;
        }
        if (mEventsRefreshTask != null) {
            mEventsRefreshTask.cancel(true);
            mEventsRefreshTask = null;
        }
    }

    private void deleteConversation(final ConversationRepository repository,
            final Conversation conversationToDelete) {
        // ensure that conversation won't appear during refresh by setting its modification time
        conversationToDelete.setLastModified(0);
        repository.save(conversationToDelete);

        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                String conversationPartner = conversationToDelete.getPartner().getUserId();
                if (!ConversationUtils.deleteConversation(conversationPartner)) {
                    Log.w(TAG, "Cannot delete conversation " + conversationPartner);
                }
            }
        });
    }

    private class ConversationLogAdapter extends ResolvingUserAdapter<Conversation> {

        class ViewHolder extends RecyclerView.ViewHolder {

            SwipeRevealLayout swipeLayout;

            ViewHolder(View itemView) {
                super(itemView);
                swipeLayout = (SwipeRevealLayout) itemView.findViewById(R.id.conversation_log_list_item);
            }

            public void bind(Conversation conversation, int position) {
                final String userId = conversation.getPartner().getUserId();
                final boolean isGroup = conversation.getPartner().isGroup();
                CachedEvent lastEvent = TextUtils.isEmpty(userId)
                        ? CACHED_DUMMY_EVENT
                        : mLastMessageCache.containsKey(userId) ? mLastMessageCache.get(userId) : CACHED_NO_MESSAGES_EVENT;

                ConversationListItem conversationListItem = (ConversationListItem) itemView;
                final boolean conversationChanged = !TextUtils.equals(userId,
                        (CharSequence) conversationListItem.getTag(R.id.view_holder_userid));
                conversationListItem.setTag(R.id.view_holder_userid, userId);

                // for groups add sender's name as prefix
                final CharSequence prefix = lastEvent.event instanceof OutgoingMessage
                        ? mPrefixYou
                        :  (isGroup && lastEvent.event instanceof IncomingMessage)
                            ? getSenderDisplayName(position, (IncomingMessage) lastEvent.event)
                            : null;

                final boolean eventChanged = !TextUtils.equals(lastEvent.getId(),
                        (CharSequence) conversationListItem.getTag(R.id.view_event_id))
                        || !TextUtils.equals(conversation.getUnsentText(),
                        (CharSequence) conversationListItem.getTag(R.id.view_draft))
                        || !TextUtils.equals(prefix,
                        (CharSequence) conversationListItem.getTag(R.id.view_event_prefix));

                if (conversationChanged || eventChanged) {
                    lastEvent.setPrefix(prefix);
                    conversationListItem.setEvent(lastEvent);
                    conversationListItem.setTag(R.id.view_event_id, lastEvent.getId());
                    conversationListItem.setTag(R.id.view_draft, conversation.getUnsentText());
                    conversationListItem.setTag(R.id.view_event_prefix, prefix);
                }
                conversationListItem.setOnConversationItemClickListener(ConversationsFragment.this);
                conversationListItem.setConversation(conversation);

                conversationListItem.setIsCallable(
                        !conversation.getPartner().isGroup()
                        && !TiviPhoneService.calls.hasCallWith(userId));

                Boolean retentionState = /**mRetentionStateCache.get(userId);**/false;
                if (retentionState == null && !Utilities.canMessage(userId)) {
                    retentionState = false;
                }

                ContactEntry contactEntry = !isGroup
                        ? ContactsCache.getContactEntryFromCacheIfExists(userId)
                        : ContactsCache.getTemporaryGroupContactEntry(userId,
                            conversation.getPartner().getDisplayName());

                if (conversationChanged
                        || isGroup
                        || conversationListItem.getTag(R.id.view_user_contact) == null
                        || (contactEntry != null
                            && !contactEntry.equals(conversationListItem.getTag(R.id.view_user_contact)))) {
                    conversationListItem.setContactEntry(contactEntry, conversation);
                    conversationListItem.setAvatar(mContactPhotoManager, conversation, contactEntry);
                    conversationListItem.setTag(R.id.view_user_contact, contactEntry);
                }

                if (retentionState != null) {
                    conversationListItem.setIsDataRetained(retentionState);
                }

                /*
                 * Last event request. But running it on the same thread as contact request
                 * slows down contact resolution.
                 *
                if (lastEvent == DUMMY_EVENT || !mLastMessageCache.containsKey(userId)) {
                    doMessageLookup(position, userId);
                }
                 */

                if (ContactsCache.hasExpired(contactEntry)/* || retentionState == null*/) {
                    doContactRequest(userId, position, contactEntry);
                }

                mBinderHelper.bind(swipeLayout, userId);
            }

            @Nullable
            private CharSequence getSenderDisplayName(int position, @NonNull IncomingMessage lastEvent) {
                String eventUserId = lastEvent.getSender();
                ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(eventUserId);
                if (ContactsCache.hasExpired(contactEntry)) {
                    doContactRequest(eventUserId, position, contactEntry);
                }
                CharSequence displayName = ContactsCache.getDisplayName("", contactEntry);
                return TextUtils.isEmpty(displayName) ? null : StringUtils.formatShortName(displayName.toString()) + ": ";
            }

            public void close() {
                swipeLayout.close(false);
            }
        }

        class LastMessageRequest extends ResolvingUserAdapter.LookupRequest {

            LastMessageRequest(String number, int position) {
                super(number, position);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null) return false;
                if (!(obj instanceof LastMessageRequest)) return false;

                LastMessageRequest other = (LastMessageRequest) obj;
                return Objects.equal(name, other.name);
            }

            @Override
            public int hashCode() {
                return name == null ? 0 : name.hashCode();
            }

            public boolean onRequestResult() {
                if (name != null) {
                    Event event = ConversationUtils.getLastDisplayableEvent(name);
                    mLastMessageCache.put(name,
                            event != null ? new CachedEvent(mContext, event) : CACHED_NO_MESSAGES_EVENT);
                }
                return true;
            }
        }

        private final Context mContext;
        private final ContactPhotoManagerNew mContactPhotoManager;
        private final ViewBinderHelper mBinderHelper;

        ConversationLogAdapter(Context context) {
            super(context);
            mContext = context;
            mContactPhotoManager =
                    ContactPhotoManagerNew.getInstance(mContext.getApplicationContext());
            mBinderHelper = new ViewBinderHelper();
            mBinderHelper.setOpenOnlyOne(true);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new ConversationListItem(mContext);
            view.setLayoutParams(
                    new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                            RecyclerView.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Conversation conversation = (Conversation) getItem(position);
            if (conversation != null) {
                final ViewHolder viewHolder = (ViewHolder) holder;
                viewHolder.bind(conversation, position);
            }
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public void onRequestsProcessed() {
            scheduleNextEventUpdate();
            sortConversations();
        }

        @Override
        public void onRedrawRequested(final int position) {
            mRecyclerView.post(new Runnable() {
                @Override
                public void run() {
                    if (mAdapter != null) {
                        mAdapter.notifyItemChanged(position);
                    }
                }
            });
        }

        void doMessageLookup(int position, String userId) {
            LookupRequest request = new LastMessageRequest(userId, position);
            addRequest(request);
        }

        void clearSelectedPosition() {
            mBinderHelper.closeAll();
        }

        public void saveStates(Bundle outState) {
            mBinderHelper.saveStates(outState);
        }

        public void restoreStates(Bundle inState) {
            mBinderHelper.restoreStates(inState);
        }

    }

    private static class RefreshConversationsList extends AsyncTask<Void, Void, List<Conversation>> {

        private final WeakReference<ConversationsFragment> mFragmentReference;

        RefreshConversationsList(final ConversationsFragment fragment) {
            mFragmentReference = new WeakReference<>(fragment);
        }

        @Override
        protected List<Conversation> doInBackground(Void... params) {

            // build cache of last messages for conversations
            ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
            boolean zinaRegistered = zinaMessaging.isRegistered();

            if (!zinaRegistered) {
                // return empty list if Axolotl has not been registered yet
                return EMPTY_LIST;
            }

            ConversationRepository repository = zinaMessaging.getConversations();
            List<Conversation> conversations = repository.list();
            Iterator<Conversation> iterator = conversations.iterator();
            while (iterator.hasNext()) {
                Conversation conversation = iterator.next();

                if (conversation == null || ((conversation.getLastModified() == 0
                        || !repository.historyOf(conversation).exists()
                        || conversation.getPartner().getUserId() == null))) {
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

    // TODO do not use async task, store reference to most recent event in conversation and update that
    private static class RefreshLastEventsCache extends AsyncTask<Conversation, Pair<String, CachedEvent>, Map<String, CachedEvent>> {

        private final WeakReference<ConversationsFragment> mFragmentReference;

        private HashMap<String, CachedEvent> mLastMessages = new HashMap<>();

        RefreshLastEventsCache(final ConversationsFragment fragment) {
            mFragmentReference = new WeakReference<>(fragment);
        }

        @Override
        protected Map<String, CachedEvent> doInBackground(Conversation... params) {
            // build cache of last messages for conversations

            ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
            boolean zinaRegistered = zinaMessaging.isRegistered();

            if (!zinaRegistered) {
                // no update if zina not available
                return null;
            }

            final ConversationRepository repository = zinaMessaging.getConversations();
            for (Conversation conversation : params) {
                String conversationId = conversation.getPartner().getUserId();
                Event event = ConversationUtils.getLastDisplayableEvent(repository, conversation);
                CachedEvent lastEvent = (event != null)
                        ? new CachedEvent(SilentPhoneApplication.getAppContext(), conversation, event)
                        : CACHED_NO_MESSAGES_EVENT;
                mLastMessages.put(conversationId, lastEvent);

                Pair<String, CachedEvent> progressEntry = new Pair<>(conversationId, lastEvent);
                publishProgress(progressEntry);
                if (isCancelled()) {
                    break;
                }
            }

            // remove, debug version
            // update unread message count for group conversations
            for (Conversation conversation : params) {
                if (conversation.getPartner().isGroup()) {
                    ConversationUtils.updateUnreadMessageCount(repository, conversation);
                }
                if (isCancelled()) {
                    break;
                }
            }

            return mLastMessages;
        }

        @Override
        public void onProgressUpdate(Pair<String, CachedEvent>... values) {
            if (values != null && values.length > 0) {
                ConversationsFragment fragment = mFragmentReference.get();
                if (fragment != null) {
                    fragment.updateConversationEventCache(values[0]);
                }
            }
        }
    }

}
