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
package com.silentcircle.messaging.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.contacts.ScCallLog;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.Device;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import zina.JsonStrings;
import zina.ZinaNative;

import static com.silentcircle.messaging.services.SCloudService.SCLOUD_ATTACHMENT_CLOUD_URL;
import static zina.JsonStrings.GROUP_ATTRIBUTE;
import static zina.JsonStrings.GROUP_AVATAR;
import static zina.JsonStrings.GROUP_BURN_MODE;
import static zina.JsonStrings.GROUP_BURN_SEC;
import static zina.JsonStrings.GROUP_DESC;
import static zina.JsonStrings.GROUP_ID;
import static zina.JsonStrings.GROUP_MAX_MEMBERS;
import static zina.JsonStrings.GROUP_MEMBER_COUNT;
import static zina.JsonStrings.GROUP_MOD_TIME;
import static zina.JsonStrings.GROUP_NAME;
import static zina.JsonStrings.GROUP_OWNER;
import static zina.JsonStrings.MEMBER_ATTRIBUTE;
import static zina.JsonStrings.MEMBER_ID;
import static zina.JsonStrings.MEMBER_MOD_TIME;

/**
 * Utilities for work with conversation object and conversation repository.
 */
public class ConversationUtils {

    private static final String TAG = ConversationUtils.class.getSimpleName();

    public static final String UNKNOWN_DISPLAY_NAME = "_!NULL!_";

    /**
     * Helper class to support the group data JSON parsing
     *
     * The 'lastModified' is given in seconds since the epoch, the getter multiplies
     * with 1000 and converts it into ms since the epoch.
     *
     * {
     *   "grpId":"47AD8038-0262-11E7-B727-8DB8FAB52D82",
     *   "name":"Test group creation 1",
     *   "ownerId":"ardr3",
     *   "desc":"",
     *   "maxMbr":30,
     *   "mbrCnt":3,
     *   "grpA":1,
     *   "grpMT":1488820312,
     *   "BSec":259200,
     *   "BMode":1,
     *   "Ava":""
     * }
     */
    public static class GroupData {
        @SerializedName(GROUP_ID) String groupId;
        @SerializedName(GROUP_NAME) String groupName;
        @SerializedName(GROUP_OWNER) String groupOwner;
        @SerializedName(GROUP_DESC) String groupDescription;
        @SerializedName(GROUP_MAX_MEMBERS) int groupMaxMembers;
        @SerializedName(GROUP_ATTRIBUTE) int groupAttribute;
        @SerializedName(GROUP_MEMBER_COUNT) int memberCount;
        @SerializedName(GROUP_MOD_TIME) long lastModified;
        @SerializedName(GROUP_BURN_SEC) long burnTime;
        @SerializedName(GROUP_BURN_MODE) int burnMode;
        @SerializedName(GROUP_AVATAR) String avatarInfo;

        public String getGroupId() {
            return groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public String getGroupOwner() {
            return groupOwner;
        }

        public String getGroupDescription() {
            return groupDescription;
        }

        public int getGroupMaxMembers() {
            return groupMaxMembers;
        }

        public int getGroupAttribute() {
            return groupAttribute;
        }

        public int getMemberCount() {
            return memberCount;
        }

        public long getLastModified() {
            return lastModified * 1000;
        }

        public long getBurnTime() {
            return burnTime;
        }

        public int getBurnMode() {
            return burnMode;
        }

        public String getAvatarInfo() {
            return avatarInfo;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class UnreadMessageStats {
        int conversationsWithUnreadMessages;
        int conversationsWithUnreadCallMessages;
        int groupConversationsWithUnreadMessages;
        int unreadMessageCount;
        int unreadCallMessageCount;

        Message lastUnreadMessage;
    }

    /**
     * Helper class to support the group data JSON parsing
     *
     * The 'lastModified' is given in seconds since the epoch, the getter multiplies
     * with 1000 and converts it into ms since the epoch.
     */
    public static class MemberData {
        @SerializedName(GROUP_ID) String groupId;
        @SerializedName(MEMBER_ID) String memberId;
        @SerializedName(MEMBER_ATTRIBUTE) int memberAttribute;
        @SerializedName(MEMBER_MOD_TIME) long lastModified;

        public String getGroupId() {
            return groupId;
        }

        public String getMemberId() {
            return memberId;
        }

        public int getMemberAttribute() {
            return memberAttribute;
        }

        public long getLastModified() {
            return lastModified * 1000;
        }
    }

    private ConversationUtils() {
    }

    public static class UnreadEventsRunnable implements Runnable {

        public interface OnUnreadEventsListener {

            void onUnreadEventsCounted(int unreadMessageCount, int unreadCallCount);
        }

        private final String mFilterConversation;
        private final WeakReference<OnUnreadEventsListener> mListener;

        public UnreadEventsRunnable(@Nullable final String filterConversation,
                @Nullable final OnUnreadEventsListener listener) {
            mFilterConversation = filterConversation;
            mListener = new WeakReference<>(listener);
        }

        @Override
        public void run() {
            int unreadMessagesCount = 0;
            int unreadCallCount = 0;
            ConversationRepository repository = getConversations();
            if (repository != null) {
                List<Conversation> conversations = repository.listCached();
                if (conversations == null || conversations.isEmpty()) {
                    conversations = repository.list();
                }
                for (Conversation conversation : conversations) {
                    if (conversation == null) {
                        continue;
                    }
                    String userId = conversation.getPartner().getUserId();
                    if (userId != null && userId.equals(mFilterConversation)) {
                        continue;
                    }
                    unreadMessagesCount += conversation.getUnreadMessageCount();
                    unreadCallCount += conversation.getUnreadCallMessageCount();
                }
            }
            OnUnreadEventsListener listener = mListener.get();
            if (listener != null) {
                listener.onUnreadEventsCounted(unreadMessagesCount, unreadCallCount);
            }
        }
    };

    /**
     * Retrieve last message text for a conversation with specific conversation partner.
     *
     * @param context Context necessary retrieve AxoMessaging instance.
     * @param conversationPartner Conversation partner's username. Used to identify conversation.
     *
     * @return Text of last message in conversation, {@code null} if conversation was not found or
     *         was empty.
     */
    public static String getLastMessageForConversation(final Context context,
            final String conversationPartner) {
        String result = null;
        ConversationRepository conversations = ZinaMessaging.getInstance().getConversations();
        if (conversations != null) {
            Conversation conversation = conversations.findByPartner(conversationPartner);
            if (conversation != null) {
                List<Event> events = conversations.historyOf(conversation).list();
                if (events != null && !events.isEmpty()) {
                    Event event = events.get(0);
                    result = event.getText();
                }
            }
        }

        return result;
    }

    /**
     * Retrieve count of conversations which have unread messages.
     *
     * @return Count of conversations with unread messages.
     */
    public static int getConversationsWithUnreadMessages() {
        int result = 0;
        for (Conversation conversation : ZinaMessaging.getInstance().getConversations().list()) {
            if (conversation != null
                    && (conversation.containsUnreadMessages())) {
                result += 1;
            }
        }
        return result;
    }

    public static boolean isGroupKnown(@NonNull String groupId) {
        ConversationUtils.GroupData groupData = getGroup(groupId);
        if (groupData == null) {
            Log.d(TAG, "Group " + groupId + " unknown");
        }
        return (groupData != null);
    }

    public static int getGroupConversationsWithUnreadMessages() {
        int result = 0;
        for (Conversation conversation : ZinaMessaging.getInstance().getConversations().list()) {
            if (conversation != null
                    && conversation.getPartner().isGroup()
                    && conversation.containsUnreadMessages()) {
                result += 1;
            }
        }
        return result;
    }

    public static int getConversationsWithUnreadCallMessages() {
        int result = 0;
        for (Conversation conversation : ZinaMessaging.getInstance().getConversations().list()) {
            if (conversation != null
                    && conversation.containsUnreadCallMessages()) {
                result += 1;
            }
        }
        return result;
    }

    /**
     * Retrieve count of conversations which have unread messages or calls.
     *
     * @param context Context necessary retrieve AxoMessaging instance.
     *
     * @return Count of conversations with unread messages and unattended calls.
     */
    public static int getConversationsWithUnreadEvents(final Context context) {
        int result = 0;
        for (Conversation conversation : ZinaMessaging.getInstance().getConversations().list()) {
            if (conversation != null
                    && (conversation.containsUnreadMessages() || conversation.containsUnreadCallMessages())) {
                result += 1;
            }
        }
        return result;
    }

    /**
     * Retrieve count of unread messages in all conversations.
     *
     * @return Count of unread messages.
     */
    public static int getUnreadMessageCount() {
        int result = 0;
        for (Conversation conversation : ZinaMessaging.getInstance().getConversations().list()) {
            if (conversation != null) {
                result += conversation.getUnreadMessageCount();
            }
        }
        return result;
    }

    /**
     * Retrieve count of unattended calls in all conversations.
     *
     * @return Count of new calls.
     */
    public static int getUnreadCallMessageCount() {
        int result = 0;
        for (Conversation conversation : ZinaMessaging.getInstance().getConversations().list()) {
            if (conversation != null) {
                result += conversation.getUnreadCallMessageCount();
            }
        }
        return result;
    }

    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static UnreadMessageStats getUnreadMessageStats(final @Nullable CharSequence conversationId) {
        final UnreadMessageStats stats = new UnreadMessageStats();
        final boolean axoRegistered = ZinaMessaging.getInstance().isRegistered();

        if (!axoRegistered) {
            return stats;
        }

        final ConversationRepository repository = ZinaMessaging.getInstance().getConversations();
        final List<Conversation> conversations = repository.list();
        for (Conversation conversation : conversations) {
            if (conversation == null
                    || conversation.getLastModified() == 0L
                    || conversation.getPartner().getUserId() == null) {
                continue;
            }
            if (conversation.containsUnreadMessages()) {
                stats.conversationsWithUnreadMessages++;
                if (conversation.getPartner().isGroup()) {
                    stats.groupConversationsWithUnreadMessages++;
                }
            }
            if (conversation.containsUnreadCallMessages()) {
                stats.conversationsWithUnreadCallMessages++;
            }
            stats.unreadMessageCount += conversation.getUnreadMessageCount();
            stats.unreadCallMessageCount += conversation.getUnreadCallMessageCount();
            if (conversationId != null
                    && TextUtils.equals(conversationId, conversation.getPartner().getUserId())) {
                stats.lastUnreadMessage = getLastUnreadMessage(repository, conversation);
            }
        }
        if (TextUtils.isEmpty(conversationId)) {
            Conversation conversation =
                    getLastModifiedConversationWithUnreadMessages(repository, conversations);
            if (conversation != null) {
                stats.lastUnreadMessage = getLastUnreadMessage(repository, conversation);
            }
        }
        return stats;
    }

    @Nullable
    @SuppressWarnings("WeakerAccess")
    // TODO: Consolidate all the unread logic
    public static Message getLastUnreadMessage() {
        ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
        boolean axoRegistered = zinaMessaging.isRegistered();

        if (!axoRegistered) {
            return null;
        }

        Message result = null;
        ConversationRepository repository = zinaMessaging.getConversations();
        Conversation conversation = getLastModifiedConversationWithUnreadMessages(repository);
        if (conversation != null) {
            result = getLastUnreadMessage(repository, conversation);
        }

        return result;
    }

    private static Message getLastUnreadMessage(final @NonNull ConversationRepository repository,
            final @NonNull Conversation conversation) {
        Message result = null;
        EventRepository eventRepository = repository.historyOf(conversation);
        EventRepository.PagingContext pagingContext =
                new EventRepository.PagingContext(
                        EventRepository.PagingContext.START_FROM_YOUNGEST, 10);
        int eventCount;
    pagingLoop:
        do {
            List<Event> events = eventRepository.list(pagingContext);
            eventCount = 0;
            if (events != null && events.size() > 0) {
                eventCount = events.size();
                for (Event event : events) {
                    if (event instanceof IncomingMessage
                            || (event instanceof CallMessage
                            && ((CallMessage) event).getCallType() != ScCallLog.ScCalls.OUTGOING_TYPE)) {
                        if (((Message) event).getState() != MessageStates.READ) {
                            result = (Message) event;
                            break pagingLoop;
                        }
                    }
                }
            }
        } while (eventCount > 0);
        return result;
    }

    @Nullable
    @SuppressWarnings("WeakerAccess")
    public static Conversation getLastModifiedConversationWithUnreadMessages(
            @NonNull final ConversationRepository repository) {
        List<Conversation> conversations = repository.list();
        return getLastModifiedConversationWithUnreadMessages(repository, conversations);
    }

    @Nullable
    @SuppressWarnings("WeakerAccess")
    public static Conversation getLastModifiedConversationWithUnreadMessages(
            @NonNull final ConversationRepository repository,
            @NonNull final List<Conversation> conversations) {
        Iterator<Conversation> conversationIterator = conversations.iterator();
        while (conversationIterator.hasNext()) {
            Conversation conversation = conversationIterator.next();

            if (conversation == null
                    || conversation.getLastModified() == 0
                    || !repository.historyOf(conversation).exists()
                    || conversation.getPartner().getUserId() == null
                    || (!conversation.containsUnreadCallMessages()
                        && !conversation.containsUnreadMessages())) {
                conversationIterator.remove();
            }
        }

        if (conversations.isEmpty()) {
            return null;
        }

        Collections.sort(conversations);
        return conversations.get(0);
    }

    @Nullable
    public static Event getLastDisplayableEvent(@Nullable final CharSequence conversationId) {
        if (TextUtils.isEmpty(conversationId)) {
            return null;
        }

        final ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
        boolean zinaRegistered = zinaMessaging.isRegistered();
        if (!zinaRegistered) {
            return null;
        }

        final ConversationRepository repository = zinaMessaging.getConversations();
        final Conversation conversation = repository.findById(conversationId.toString());

        return conversation != null ? getLastDisplayableEvent(repository, conversation) : null;
    }

    @Nullable
    public static Event getLastDisplayableEvent(@NonNull final ConversationRepository repository,
            @NonNull final Conversation conversation) {
        Event result = null;
        List<Event> events = null;

        // going downwards from top, youngest element
        EventRepository.PagingContext pagingContext =
                new EventRepository.PagingContext(
                        EventRepository.PagingContext.START_FROM_YOUNGEST, 15);
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
                result = findLastEvent(eventList);
                hasMessage = result != null;
            }
        } while (eventCount > 0 && !hasMessage);
        return result;
    }

    private static Event findLastEvent(List<Event> events) {
        Event result = null;
        ListIterator<Event> iterator = events.listIterator(events.size());
        while (iterator.hasPrevious()) {
            Event event = iterator.previous();
            if (event instanceof IncomingMessage
                    || event instanceof OutgoingMessage
                    || event instanceof CallMessage) {
                if (!((Message) event).isExpired()
                        && ((Message) event).getState() != MessageStates.BURNED) {
                    result = event;
                    break;
                }
            }
            else if (event instanceof InfoEvent) {
                result = event;
                break;
            }
        }
        return result;
    }

    public static void updateUnreadMessageCount(@Nullable final CharSequence conversationId) {
        if (TextUtils.isEmpty(conversationId)) {
            return;
        }

        final ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        boolean axoRegistered = axoMessaging.isRegistered();
        if (!axoRegistered) {
            return;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        Conversation conversation = repository.findById(conversationId.toString());

        updateUnreadMessageCount(repository, conversation);
    }

    public static void updateUnreadMessageCount(@NonNull final ConversationRepository repository,
            @NonNull final Conversation conversation) {
        List<Event> events = repository.historyOf(conversation).list();
        // Count incoming messages with MessageStates.RECEIVED which represents an unread message
        int unreadMessageCount = 0;
        for (Event event : events) {
            if (event instanceof IncomingMessage) {
                int state = ((Message)event).getState();
                if (state == MessageStates.RECEIVED && !((Message) event).isExpired()) {
                    unreadMessageCount += 1;
                }
            }
        }
        if (unreadMessageCount != conversation.getUnreadMessageCount()) {
            conversation.setUnreadMessageCount(unreadMessageCount);
            repository.save(conversation);
        }
    }

    @Nullable
    public static ConversationRepository getConversations() {
        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }
        return axoMessaging.getConversations();
    }

    public static boolean canMessage(@Nullable Conversation conversation) {
        if (conversation == null) {
            return false;
        }
        Contact partner = conversation.getPartner();
        return Utilities.canMessage(partner.getUserId()) || partner.isGroup();
    }

    @NonNull
    public static String resolveDisplayName(@Nullable ContactEntry contactEntry,
            @Nullable Conversation conversation) {

        String displayName = null;
        if (contactEntry != null && conversation != null) {
            if (TextUtils.isEmpty(contactEntry.name)) {
                // It is possible the user has changed the name (unlikely to have a nameless contact)
                contactEntry = ContactsCache.getContactEntry(conversation.getPartner().getUserId());
            }

            displayName = contactEntry.name;
        }
        if ((TextUtils.isEmpty(displayName)
                || UNKNOWN_DISPLAY_NAME.equals(displayName)
                || Utilities.isUuid(displayName))
                && conversation != null) { // If empty try to get it from name lookup cache
            byte[] dpName = ZinaMessaging.getDisplayName(conversation.getPartner().getUserId());
            if (dpName != null) {
                displayName = new String(dpName);
            } else {
                // Here we may fall back to a save "display name" we got from the SIP
                // stack when receiving a message
                displayName = conversation.getPartner().getDisplayName();
            }

            // FIXME: Should we handle this in Axolotl?
            if ((TextUtils.isEmpty(displayName) || UNKNOWN_DISPLAY_NAME.equals(displayName))
                    && contactEntry != null
                    && !TextUtils.isEmpty(contactEntry.phoneNumber)) {
                displayName = Utilities.formatNumber(contactEntry.phoneNumber);
            }
        } else if (contactEntry != null && contactEntry.imName == null
                && !TextUtils.isEmpty(contactEntry.phoneNumber)
                && contactEntry.phoneNumber.equals(contactEntry.name)) {
            // Definitely a temporary phone number entry
            displayName = Utilities.formatNumber(contactEntry.phoneNumber);
        }
        return (displayName != null) ? displayName : "";
    }

    @Nullable
    public static Conversation getConversation(final @Nullable String partner) {
        if (TextUtils.isEmpty(partner)) {
            return null;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        return repository.findByPartner(partner);
    }

    @Nullable
    public static Conversation getDisplayableConversation(final @Nullable String partner) {
        if (TextUtils.isEmpty(partner)) {
            return null;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        Conversation conversation = repository.findByPartner(partner);
        if (conversation == null
                || ((conversation.getLastModified() == 0
                    || !repository.historyOf(conversation).exists()
                    || conversation.getPartner().getUserId() == null))) {
            conversation = null;
        }

        return conversation;
    }


    @Nullable
    public static Conversation getOrCreateConversation(@Nullable String partner) {
        if (TextUtils.isEmpty(partner)) {
            return null;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        return axoMessaging.getOrCreateConversation(partner);
    }

    /**
     * Get a list of conversation ids which have provided user id as participant. List includes
     * regular conversation with this user and all group conversations which have this user in
     * their member list.
     *
     * @param userId User identifier to look for in conversation participants.
     * @return List of conversation identifiers on success, empty list if user id is  empty.
     */
    @NonNull
    public static List<String> getConversationsWithParticipant(String userId) {
        final List<String> conversations = new ArrayList<>();
        if (!TextUtils.isEmpty(userId)) {
            // add partner's id
            conversations.add(userId);
            // add all known groups which have this user as participant
            final int[] code = new int[1];
            final byte[][] groups = ZinaMessaging.listAllGroupsWithMember(userId, code);
            if (groups != null && groups.length > 0) {
                Gson gson = new Gson();
                for (byte[] group : groups) {
                    ConversationUtils.GroupData groupData = gson.fromJson(new String(group),
                            ConversationUtils.GroupData.class);
                    conversations.add(groupData.getGroupId());
                }
            }
        }
        return conversations;
    }

    public static void setConversationAvatar(@Nullable Context context,
            @Nullable String conversationId, @Nullable String avatarUrl, @Nullable String base64Bitmap) {
        if (context == null || TextUtils.isEmpty(conversationId)) {
            return;
        }

        try {
            if (TextUtils.isEmpty(base64Bitmap)) {
                setConversationAvatar(context, conversationId, avatarUrl, (Bitmap) null);
            }
            else {
                byte[] bitmapBytes = Base64.decode(base64Bitmap, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                if (bitmap != null) {
                    setConversationAvatar(context, conversationId, avatarUrl, bitmap);
                    bitmap.recycle();
                }
            }
        } catch (Throwable t) {
            // failed to update conversation's avatar, proceed with it
        }
    }

    public static void setConversationAvatar(@NonNull Context context,
            @NonNull ConversationRepository repository, @NonNull Conversation conversation,
            @Nullable String avatarUrl, @Nullable Bitmap bitmap) {
        // delete previous file first
        AvatarProvider.deleteConversationAvatar(context, conversation.getAvatar());
        // if no bitmap is provided
        if (bitmap == null) {
            conversation.setAvatar(null);
            conversation.setAvatarIv((String) null);
            conversation.setAvatarUrl(null);
        }
        else {
            // save avatar file, update conversation object
            AvatarProvider.saveConversationAvatar(context, conversation, bitmap, avatarUrl);
        }
        repository.save(conversation);
    }

    public static void setConversationAvatar(@Nullable Context context,
            @Nullable String conversationId, @Nullable String avatarUrl, @Nullable Bitmap bitmap) {
        if (context == null || TextUtils.isEmpty(conversationId)) {
            return;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        Conversation conversation = repository.findByPartner(conversationId);
        if (conversation != null) {
            setConversationAvatar(context, repository, conversation, avatarUrl, bitmap);
        }
    }

    public static void verifyConversationAvatar(@Nullable Context context,
            @Nullable String conversationId, @Nullable String avatarInfo) {
        if (context == null || TextUtils.isEmpty(conversationId) || TextUtils.isEmpty(avatarInfo)) {
            return;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        Conversation conversation = repository.findByPartner(conversationId);
        if (conversation != null) {
            String conversationAvatar = conversation.getAvatarUrl();
            if (!TextUtils.isEmpty(conversationAvatar)
                    && conversationAvatar.startsWith(AvatarProvider.AVATAR_TYPE_DOWNLOADED)) {

                String cloudUrl = null;
                try {
                    JSONObject json = new JSONObject(avatarInfo);
                    cloudUrl = json.optString(SCLOUD_ATTACHMENT_CLOUD_URL);
                } catch (JSONException exception) {
                    // cloud url stays empty, no download
                }

                if (!TextUtils.isEmpty(cloudUrl) && !conversationAvatar.contains(cloudUrl)) {
                    Intent serviceIntent = Action.DOWNLOAD.intent(context, SCloudService.class);
                    Extra.PARTNER.to(serviceIntent, conversationId);
                    // not passing event id, info event can already be removed by user
                    serviceIntent.putExtra(SCloudService.ATTACHMENT_INFO, avatarInfo);
                    serviceIntent.putExtra(SCloudService.FLAG_GROUP_AVATAR, true);
                    context.startService(serviceIntent);
                }
            }
        }
    }

    @Nullable
    public static Conversation getOrCreateGroupConversation(final @Nullable Context context,
            final @Nullable String groupId) {
        if (context == null || TextUtils.isEmpty(groupId)) {
            return null;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        ConversationRepository repository = getConversations();
        if (repository == null) {
            return null;
        }

        GroupData groupData = getGroup(groupId);

        Conversation conversation = repository.findById(groupId);

        if (conversation == null) {
            conversation = new Conversation(groupId);
            conversation.setBurnNotice(false);
            conversation.setBurnDelay(TimeUnit.DAYS.toSeconds(3));
            conversation.setBurnNotice(true);
            conversation.setLastModified(System.currentTimeMillis());

            Contact partner = conversation.getPartner();
            partner.setGroup(true);
            partner.setUserId(groupId);

            String displayName = groupData != null ? groupData.getGroupName() : groupId;
            if (TextUtils.isEmpty(displayName)) {
                displayName = context.getResources().getString(R.string.group_messaging_new_group_conversation);
            }
            partner.setDisplayName(displayName);

            repository.save(conversation);
        }

        return conversation;
    }

    public static void fillDeviceData(@NonNull final ConversationRepository repository,
            final @NonNull Contact partner, String device, boolean hasHadDevices) {
        final DeviceInfo.DeviceData devInfo = DeviceInfo.parseDeviceInfo(device);
        if (devInfo != null) {
            final Conversation conversation = repository.findByPartner(partner.getUserId());
            List<Device> knownDevices = partner.getDeviceInfo();
            if (knownDevices == null || !hasHadDevices) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Fill in device device for "
                        + partner.getUserId() + " " + device);
                // there have been no known devices for this user, add without creating info event
                partner.addDeviceInfo(devInfo.name, devInfo.devId, devInfo.identityKey, devInfo.zrtpVerificationState);
                if (conversation != null) {
                    repository.save(conversation);
                }
            }
            else if (!partner.hasDevice(devInfo.devId)) {
                final List<DeviceInfo.DeviceData> devInfos = new ArrayList<>();
                final List<String> conversations = getConversationsWithParticipant(partner.getUserId());
                devInfos.add(devInfo);
                if (conversation != null) {
                    updateDeviceData(repository, conversation, conversations, devInfos, false);
                }
            }
        }
    }

    public static void updateDeviceData(@NonNull final ConversationRepository repository,
            @NonNull final Collection<String> conversations, @Nullable final byte[] userId) {
        final String user = (userId != null) ? new String(userId) : null;
        Conversation conversation = repository.findByPartner(user);
        if (conversation == null) {
            return;
        }
        final List<DeviceInfo.DeviceData> devInfos = new ArrayList<>();
        byte[][] devices = ZinaMessaging.getIdentityKeys(userId);
        if (devices != null) {
            for (byte[] device : devices) {
                DeviceInfo.DeviceData devInfo = DeviceInfo.parseDeviceInfo(new String(device));
                if (devInfo != null) {
                    devInfos.add(devInfo);
                }
            }
        }
        updateDeviceData(repository, conversation, conversations, devInfos, true);
    }

    private static void updateDeviceData(@NonNull final ConversationRepository repository,
            final @NonNull Conversation conversation,
            final @NonNull Collection<String> conversations,
            final @NonNull Collection<DeviceInfo.DeviceData> devInfos,
            final boolean canRemove) {
        final String user = conversation.getPartner().getUserId();
        ContactEntry contactEntry = ContactsCache.getContactEntry(user);
        String displayName = resolveDisplayName(contactEntry, conversation);
        boolean hasHadDeviceInfos = conversation.getPartner().numDeviceInfos() >= 0;
        List<Event> eventsToSave = new ArrayList<>();
        // remove device infos for no longer known/unknown devices
        List<Device> knownDevices = conversation.getPartner().getDeviceInfo();
        if (canRemove && knownDevices != null) {
            for (Device device : knownDevices) {
                boolean hasDevice = false;
                for (DeviceInfo.DeviceData devInfo : devInfos) {
                    if (TextUtils.equals(devInfo.devId, device.getDeviceId())) {
                        hasDevice = true;
                        break;
                    }
                }
                if (!hasDevice) {
                    Log.d(TAG, "Removed device for " + user + " " + device.getName());
                    conversation.getPartner().removeDeviceInfo(device.getDeviceId());
                    String details = StringUtils.jsonFromPairs(
                            new Pair<String, Object>(JsonStrings.MSG_DEVICE_NAME, device.getName()),
                            new Pair<String, Object>(JsonStrings.MSG_USER_ID, user),
                            new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                    Event event = MessageUtils.createInfoEvent(
                            conversation.getPartner().getUserId(),
                            InfoEvent.INFO_DEVICE_REMOVED,
                            displayName + " removed device " + device.getName(),
                            details);
                    eventsToSave.add(event);
                }
            }
        }
        // add or update device infos for known devices
        for (DeviceInfo.DeviceData devInfo : devInfos) {
            if (!conversation.getPartner().hasDevice(devInfo.devId)) {
                Log.d(TAG, "New device for " + user + " " + devInfo.name);
                conversation.getPartner().addDeviceInfo(devInfo.name, devInfo.devId,
                        devInfo.identityKey, devInfo.zrtpVerificationState);
                String details = StringUtils.jsonFromPairs(
                        new Pair<String, Object>(JsonStrings.MSG_DEVICE_NAME, devInfo.name),
                        new Pair<String, Object>(JsonStrings.MSG_USER_ID, user),
                        new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
                Event event = MessageUtils.createInfoEvent(
                        conversation.getPartner().getUserId(),
                        InfoEvent.INFO_DEVICE_ADDED,
                        displayName + " added device " + devInfo.name,
                        details);
                eventsToSave.add(event);
            }
            else {
                conversation.getPartner().updateDeviceInfo(devInfo.name, devInfo.devId,
                        devInfo.identityKey, devInfo.zrtpVerificationState);
            }
        }
        if (devInfos.isEmpty()) {
            String text = SilentPhoneApplication.getAppContext().getResources().getString(
                    R.string.group_messaging_no_devices_for_user, displayName);
            int tag = InfoEvent.INFO_NO_DEVICES_FOR_USER;
            String details = StringUtils.jsonFromPairs(
                    new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName),
                    new Pair<String, Object>(JsonStrings.MSG_USER_ID, user));
            InfoEvent event = MessageUtils.createInfoEvent(conversation.getPartner().getUserId(),
                    tag, text, details);
            eventsToSave.add(event);
        }

        repository.save(conversation);

        /*
         * Do not add device info events if user did not have any known devices up to now.
         * This is to avoid having all his devices listed in regular conversation when
         * any message exchange was in a group conversation.
         */
        if (hasHadDeviceInfos) {
            for (String conversationId : conversations) {
                Conversation conversationToNotify = repository.findByPartner(conversationId);
                if (conversationToNotify == null) {
                    continue;
                }
                for (Event event : eventsToSave) {
                    event.setConversationID(conversationId);
                    repository.historyOf(conversationToNotify).save(event);
                    MessageUtils.notifyConversationUpdated(
                            SilentPhoneApplication.getAppContext(), user, true,
                            ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, event.getId());
                }
            }
        }
    }

    /**
     * Return device ids which are not yet stored in conversation object for given user id.
     */
    @NonNull
    public static List<String> getNewDeviceIds(@NonNull final ConversationRepository repository,
            @Nullable final byte[] userId) {
        final List<String> result = new ArrayList<>();
        final String user = (userId != null) ? new String(userId) : null;
        byte[][] devices = ZinaMessaging.getIdentityKeys(userId);

        if (devices == null || devices.length == 0) {
            Log.d(TAG, "No known devices for " + user);
        }
        else {
            Conversation conversation = repository.findByPartner(user);
            if (conversation != null) {
                for (byte[] device : devices) {
                    DeviceInfo.DeviceData devInfo = DeviceInfo.parseDeviceInfo(new String(device));
                    if (devInfo != null) {
                        if (!conversation.getPartner().hasDevice(devInfo.devId)) {
                            Log.d(TAG, "New device for " + user + " " + new String(device));
                            result.add(devInfo.devId);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void updateGroupConversationName(@Nullable String groupId,
            @Nullable String groupName, @Nullable String userId, @Nullable CharSequence displayName) {
        if (TextUtils.isEmpty(groupId) || TextUtils.isEmpty(groupName)) {
            return;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return;
        }

        ConversationRepository repository = getConversations();
        if (repository == null) {
            return;
        }

        Conversation conversation = repository.findById(groupId);

        if (conversation != null) {
            Contact partner = conversation.getPartner();
            partner.setDisplayName(groupName);
            conversation.setLastModified(System.currentTimeMillis());
            repository.save(conversation);

            createNameChangeEvent(groupId, groupName, userId, displayName, repository, conversation);
        }
    }

    public static boolean deleteConversation(final @Nullable String conversationId) {
        return deleteConversation(conversationId, true);
    }

    public static boolean deleteConversation(final @Nullable String conversationId, boolean runPendingCommand) {
        if (TextUtils.isEmpty(conversationId)) {
            return false;
        }

        final ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
        if (!zinaMessaging.isRegistered()) {
            return false;
        }

        final ConversationRepository repository = getConversations();
        if (repository == null) {
            return false;
        }

        Log.d(TAG, "deleteConversation " + conversationId);
        final Conversation conversation = repository.findById(conversationId);
        if (conversation != null) {
            if (runPendingCommand && conversation.getPartner().isGroup()) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        int result = ZinaMessaging.leaveGroup(conversationId);
                        Log.d(TAG, "Leaving group " + conversationId + " result " + result);

                        /*
                         * Workaround to send "lve" to siblings immediately as Zina will not do it.
                         * User who leaves is removed from group participants before leave command
                         * is sent.
                         *
                        final JSONObject attributeJson = new JSONObject();
                        try {
                            attributeJson.put(JsonStrings.GROUP_COMMAND, JsonStrings.LEAVE);
                            attributeJson.put(JsonStrings.GROUP_ID, conversationId);
                            attributeJson.put(JsonStrings.MEMBER_ID, zinaMessaging.getUserName());

                            ZinaNative.sendGroupCommandToMember(conversationId,
                                    IOUtils.encode(zinaMessaging.getUserName()),
                                    null,
                                    IOUtils.encode(attributeJson.toString()));
                        } catch (JSONException ignore) {}
                         */

                        /*
                         * Changes to group applied by Zina, no call to applyGroupChangeSet here
                         */
                    }
                };
                AsyncUtils.execute(runnable);
            }

            repository.remove(conversation);
        }

        return true;
    }

    @Nullable
    public static GroupData getGroup(@Nullable String groupId) {
        if (TextUtils.isEmpty(groupId)) {
            return null;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        GroupData result = null;
        final int[] code = new int[1];
        final byte[] group = ZinaMessaging.getGroup(groupId, code);
        if (group != null && group.length > 0) {
            final Gson gson = new Gson();
            result = gson.fromJson(new String(group), GroupData.class);
        }
        return result;
    }

    @Nullable
    public static List<GroupData> getGroups() {
        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        final int[] code = new int[1];
        final byte[][] groups = ZinaMessaging.listAllGroups(code);

        if (groups == null || groups.length == 0) {
            return null;
        }

        final ArrayList<GroupData> groupList = new ArrayList<>();
        final Gson gson = new Gson();
        for (byte[] group : groups) {
            GroupData groupData = gson.fromJson(new String(group), GroupData.class);
            groupList.add(groupData);
        }
        return groupList;
    }

    /**
     * Retrieve ids of all group participants.
     *
     * @param groupId Group id in Zina.
     * @return List of group participant ids
     *         or empty list if request failed or group id not known or empty.
     */
    @NonNull
    public static List<String> getGroupParticipants(@Nullable final String groupId) {
        List<String> result = new ArrayList<>();

        if (!TextUtils.isEmpty(groupId)) {
            final int[] code = new int[1];
            byte[][] groupMembers = ZinaMessaging.getAllGroupMembers(groupId, code);
            if (groupMembers != null) {
                Gson gson = new Gson();
                for (byte[] member : groupMembers) {
                    ConversationUtils.MemberData memberData = gson.fromJson(new String(member),
                            ConversationUtils.MemberData.class);
                    result.add(memberData.getMemberId());
                }
            }
        }
        return result;
    }

    @WorkerThread
    public static void addParticipants(@Nullable final Context context,
            @Nullable final String groupId, @Nullable final List<CharSequence> participants) {
        if (context == null || participants == null || TextUtils.isEmpty(groupId)) {
            return;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return;
        }

        final ConversationRepository repository = getConversations();
        if (repository == null) {
            return;
        }

        final Conversation conversation = repository.findByPartner(groupId);
        final EventRepository events = conversation != null ? repository.historyOf(conversation) : null;

        // update invite list to contain only participants which are not yet in group
        final Set<CharSequence> newParticipants = new HashSet<>(participants);
        newParticipants.remove(null);
        newParticipants.remove("");
        final int[] code = new int[1];
        byte[][] groupMembers = ZinaMessaging.getAllGroupMembers(groupId, code);
        if (groupMembers != null) {
            Gson gson = new Gson();
            for (byte[] member : groupMembers) {
                ConversationUtils.MemberData memberData = gson.fromJson(new String(member),
                        ConversationUtils.MemberData.class);
                newParticipants.remove(memberData.getMemberId());
            }
        }

        // send invite and create corresponding event in group conversation
        List<CharSequence> ids = new ArrayList<>();
        Map<String, Integer> results = new HashMap<>();
        for (CharSequence userId : newParticipants) {
            int result = ZinaMessaging.addUser(groupId, IOUtils.encode(userId.toString()));
            results.put(userId.toString(), result);
            Log.d(TAG, "Inviting user " + userId + ", result: " + result);
        }

        // apply changes to group
        int result = ZinaMessaging.applyGroupChangeSet(groupId);
        Log.d(TAG, "Applying group changes, result: " + result
                + ", " + ZinaMessaging.getErrorInfo() + " (" + ZinaMessaging.getErrorCode() + ")");

        if (events != null) {
            for (CharSequence userId : newParticipants) {
                if (TextUtils.isEmpty(userId)) {
                    continue;
                }
                CharSequence displayName = ContactsCache.getDisplayName(userId,
                        ContactsCache.getContactEntry(userId.toString()));
                int inviteResult = results.get(userId.toString());
                // (result == MessageErrorCodes.SUCCESS) ? results.get(userId.toString()) : result;
                // create a 'participant added' event
                int textId = inviteResult == MessageErrorCodes.SUCCESS
                        ? R.string.group_messaging_invite
                        : R.string.group_messaging_invite_failed;
                String text = context.getResources().getString(textId, displayName);
                int tag = inviteResult == MessageErrorCodes.SUCCESS
                        ? InfoEvent.INFO_INVITE_USER
                        : InfoEvent.INFO_INVITE_USER_FAILED;
                String details = StringUtils.jsonFromPairs(
                        new Pair<String, Object>(JsonStrings.MEMBER_DISPLAY_NAME, displayName),
                        new Pair<String, Object>(JsonStrings.MEMBER_ID, userId));
                InfoEvent event = MessageUtils.createInfoEvent(groupId, tag, text, details);

                events.save(event);
                ids.add(event.getId());
            }

            if (result != MessageErrorCodes.SUCCESS) {
                if (result == MessageErrorCodes.NO_DEVS_FOUND) {
                    AsyncUtils.execute(new Runnable() {
                        @Override
                        public void run() {
                            checkUserDevices(context, groupId, newParticipants);
                        }
                    });
                }
                else {
                    ErrorEvent errorEvent =
                            MessageUtils.createErrorEvent(groupId, result, ZinaMessaging.getErrorInfo());
                    events.save(errorEvent);
                    ids.add(errorEvent.getId());
                }
            }
        }

        if (ids.size() > 0) {
            if (conversation != null) {
                conversation.setLastModified(System.currentTimeMillis());
                repository.save(conversation);
            }
            MessageUtils.notifyConversationUpdated(context, groupId, true,
                    ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND,
                    ids.toArray(new CharSequence[ids.size()]));
        }
    }

    private static void createNameChangeEvent(@NonNull String groupId, @NonNull String groupName,
            @Nullable String user, @Nullable CharSequence displayName,
            @NonNull ConversationRepository repository, @NonNull Conversation conversation) {
        Context context = SilentPhoneApplication.getAppContext();
        String text = context.getString(R.string.group_messaging_new_name_this, groupName);
        int tag = InfoEvent.INFO_NEW_GROUP_NAME;
        String details = StringUtils.jsonFromPairs(
                new Pair<String, Object>(JsonStrings.GROUP_NAME, groupName),
                new Pair<String, Object>(JsonStrings.MEMBER_ID, user),
                new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
        InfoEvent event = MessageUtils.createInfoEvent(groupId, tag, text, details);
        repository.historyOf(conversation).save(event);
        MessageUtils.notifyConversationUpdated(context, groupId, true,
                ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, event.getId());
    }

    /**
     * Generate conversation display name from list for participants.
     *
     * @param participantIds Collection of conversation participant ids.
     * @return List of comma separated participant display names
     *         or empty string if no participants provided
     */
    @WorkerThread
    public static String getGeneratedGroupName(@Nullable Collection<String> participantIds) {
        if (participantIds == null || participantIds.size() <= 0) {
            return "";
        }

        List<String> names = new ArrayList<>();
        for (String participant : participantIds) {
            if (TextUtils.equals(LoadUserInfo.getUuid(), participant)) {
                /* include oneself */
                String name = LoadUserInfo.getDisplayName();
                if (TextUtils.isEmpty(name)) {
                    name = LoadUserInfo.getDisplayAlias();
                }
                name = StringUtils.formatShortName(name);
                if (!TextUtils.isEmpty(name)) {
                    names.add(name);
                    continue;
                }
            }
            ContactEntry contactEntry = ContactsCache.getContactEntry(participant);
            if (contactEntry != null) {
                String name = contactEntry.name;
                if (TextUtils.isEmpty(name)) {
                    name = contactEntry.alias;
                }
                names.add(StringUtils.formatShortName(name));
            }
        }
        Collections.sort(names, StringUtils.NAME_COMPARATOR);
        return TextUtils.join(", ", names);
    }

    /**
     * Generate and set group conversation display name from a list of participants.
     *
     * @param conversationId Group conversation for which to set name.
     * @param participantIds Collection of conversation participant ids.
     */
    @WorkerThread
    public static void setGeneratedGroupName(@Nullable String conversationId,
            @Nullable Collection<String> participantIds) {
        if (TextUtils.isEmpty(conversationId) || participantIds == null
                || participantIds.size() <= 0) {
            return;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        Conversation conversation = repository.findByPartner(conversationId);
        if (conversation != null) {
            String displayName = getGeneratedGroupName(participantIds);
            conversation.getPartner().setDisplayName(displayName);
            repository.save(conversation);
        }
    }

    /**
     * Generate and set group conversation display name from its participants.
     *
     * @param conversationId Group conversation for which to set name.
     */
    public static void setGeneratedGroupName(@Nullable String conversationId) {
        Set<String> participantIds = new HashSet<>();
        // add all already known participants
        participantIds.addAll(ConversationUtils.getGroupParticipants(conversationId));
        ConversationUtils.setGeneratedGroupName(conversationId, participantIds);
    }

    /**
     * Send updated group metadata to group participants.
     *
     * Apply group changes by sending them to other participants. Sending is done on background
     * thread. If an error is encountered, error code is logged and show in a toast message.
     *
     * @param activity Context to use to show error messages.
     * @param groupId Identifier of group for which to apply changes.
     */
    public static void applyGroupChangeSet(@Nullable final Activity activity,
            @Nullable final String groupId) {
        if (activity == null || TextUtils.isEmpty(groupId)) {
            return;
        }

        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                int result = ZinaMessaging.applyGroupChangeSet(groupId);
                if (result != MessageErrorCodes.SUCCESS) {
                    final String message = "Failed to send group update: error code: "
                            + ZinaNative.getErrorCode() + ", info: " + ZinaNative.getErrorInfo();
                    Log.e(TAG, message);
                    /*
                     * Ignore error if failure is due to absent devices, user will receive
                     * full info when joining with a valid device.
                     */
                    if (result != MessageErrorCodes.NO_DEVS_FOUND) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * Check whether users have any devices for messaging and create info events for those
     * who don't.
     *
     * @param context Context for resources.
     * @param conversationId Group conversation identifier.
     * @param participantIds Participant id list in group conversation.
     */
    public static void checkUserDevices(@Nullable Context context, @Nullable String conversationId,
            @Nullable Collection<? extends CharSequence> participantIds) {
        if (context == null || TextUtils.isEmpty(conversationId) || participantIds == null
                || participantIds.size() <= 0) {
            return;
        }

        ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
        if (!zinaMessaging.isRegistered()) {
            return;
        }

        final ConversationRepository repository = zinaMessaging.getConversations();
        final Conversation conversation = repository.findByPartner(conversationId);
        final EventRepository events = conversation != null ? repository.historyOf(conversation) : null;

        if (events == null) {
            return;
        }

        // go through participants and check who is the one who does not have any devices
        for (CharSequence userId : participantIds) {
            if (TextUtils.isEmpty(userId)) {
                continue;
            }
            byte[] data = IOUtils.encode(userId.toString());
            int[] code = new int[1];
            // look up user devices on server
            ZinaMessaging.zinaCommand("rescanUserDevices", data, code);
            // if request is successful and there are no known devices, create an entry for this
            byte[][] devices = ZinaMessaging.getIdentityKeys(data);
            if (code[0] == MessageErrorCodes.SUCCESS && (devices == null || devices.length == 0)) {
                CharSequence displayName = ContactsCache.getDisplayName(userId,
                        ContactsCache.getContactEntry(userId.toString()));
                String text = context.getResources().getString(
                        R.string.group_messaging_no_devices_for_user, displayName);
                int tag = InfoEvent.INFO_NO_DEVICES_FOR_USER;
                String details = StringUtils.jsonFromPairs(
                        new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName),
                        new Pair<String, Object>(JsonStrings.MSG_USER_ID, userId));
                InfoEvent event = MessageUtils.createInfoEvent(conversationId, tag, text, details);
                events.save(event);
                Log.d(TAG, "No devices for user " + userId);

                MessageUtils.notifyConversationUpdated(context, conversationId, false,
                        ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, event.getId());

            }
        }
    }

}
