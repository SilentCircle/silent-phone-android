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
package com.silentcircle.messaging.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.AxoMessaging;

import java.util.List;

/**
 * Utilities for work with conversation object and conversation repository.
 */
public class ConversationUtils {

    private ConversationUtils() {
    }

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
        ConversationRepository conversations = AxoMessaging.getInstance(context).getConversations();
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
     * @param context Context necessary retrieve AxoMessaging instance.
     *
     * @return Count of conversations with unread messages.
     */
    public static int getConversationsWithUnreadMessages(final Context context) {
        int result = 0;
        for (Conversation conversation : AxoMessaging.getInstance(context).getConversations().list()) {
            if (conversation != null && conversation.containsUnreadMessages()) {
                result += 1;
            }
        }
        return result;
    }

    /**
     * Retrieve count of unread messages in all conversations.
     *
     * @param context Context necessary retrieve AxoMessaging instance.
     *
     * @return Count of unread messages.
     */
    public static int getUnreadMessageCount(final Context context) {
        int result = 0;
        for (Conversation conversation : AxoMessaging.getInstance(context).getConversations().list()) {
            if (conversation != null) {
                result += conversation.getUnreadMessageCount();
            }
        }
        return result;
    }

    @Nullable
    public static ConversationRepository getConversations(Context ctx) {
        AxoMessaging axoMessaging = AxoMessaging.getInstance(ctx);
        if (!axoMessaging.isRegistered()) {
            return null;
        }
        return axoMessaging.getConversations();
    }

    @NonNull
    public static String resolveDisplayName(ContactEntry contactEntry, Conversation conversation) {

        String displayName = null;
        if (contactEntry != null) {
            displayName = contactEntry.name;        // Use name on contact entry if available
        }
        if (TextUtils.isEmpty(displayName) && conversation != null) { // If empty try to get it from name lookup cache
            byte[] dpName = AxoMessaging.getDisplayName(conversation.getPartner().getUserId());
            if (dpName != null) {
                displayName = new String(dpName);
            }
            else {
                // Here we may fall back to a save "display name" we got from the SIP
                // stack when receiving a message
                displayName = conversation.getPartner().getDisplayName();
            }
        }
        return (displayName != null) ? displayName : "";
    }
}
