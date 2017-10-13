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
package com.silentcircle.messaging.task;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import zina.ZinaNative;

/**
 * Task to handle message read and burn failures by resending requests.
 */
public class HandleMessageFailuresTask extends AsyncTask<String, Void, Integer> {

    private static final String TAG = HandleMessageFailuresTask.class.getSimpleName();

    private final Context mContext;

    private static final int SAVE_ACTION_NONE = 0;
    private static final int SAVE_ACTION_UPDATE = 1;
    private static final int SAVE_ACTION_REMOVE = 2;

    private static final long ITERATION_DELAY = TimeUnit.MILLISECONDS.toMillis(500);
    private static final long PAGING_DELAY = TimeUnit.MILLISECONDS.toMillis(100);
    private static final int PAGE_SIZE = 50;

    public HandleMessageFailuresTask(final Context context) {
        mContext = context;
    }

    @Override
    protected Integer doInBackground(String... params) {
        int count = 0;
        Log.d(TAG, "Started HandleMessageFailuresTask");

        if (!Utilities.isNetworkConnected(mContext)) {
            // return indication for re-run if no network available
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Network not available, returning");
            return -1;
        }

        // check Axolotl registration state
        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        boolean axoRegistered = axoMessaging.isRegistered();

        if (!axoRegistered) {
            // return indication for re-run if Axolotl has not been registered yet
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "AxoMessaging not ready, returning");
            return -1;
        }

        // loop through all conversations and find all failed messages
        // TODO use a persisted list to avoid need to loop through all messages
        ConversationRepository repository = axoMessaging.getConversations();
        List<Conversation> conversations = repository.list();
        for (Conversation conversation : conversations) {
            correctConversationAvatar(repository, conversation);

            if (conversation == null || !ConversationUtils.canMessage(conversation)
                    || !repository.historyOf(conversation).exists()) {
                continue;
            }

            EventRepository eventRepository = repository.historyOf(conversation);
            EventRepository.PagingContext pagingContext =
                    new EventRepository.PagingContext(
                            EventRepository.PagingContext.START_FROM_YOUNGEST, PAGE_SIZE);
            int eventCount;
            do {
                List<Event> events = eventRepository.list(pagingContext);
                eventCount = 0;
                if (events != null && events.size() > 0) {
                    eventCount = events.size();
                    count += searchFailedMessages(repository, conversation, events);
                    Utilities.Sleep(PAGING_DELAY);
                }
            } while (eventCount > 0);

            Utilities.Sleep(ITERATION_DELAY);
        }

        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, " Still have to handle " + count + " message failures.");
        }
        return count;
    }

    private int searchFailedMessages(ConversationRepository repository,
            Conversation conversation, List<Event> events) {
        int count = 0;
        for (Event event : events) {
            if (event instanceof Message) {
                Message message = (Message) event;
                int saveAction = SAVE_ACTION_NONE;

                Long[] flags = message.getFailureFlagsAsArray();
                if (flags.length != 0 && conversation.getPartner().isGroup())  {
                    message.clearFailureFlag(Message.FAILURE_READ_NOTIFICATION);
                    message.clearFailureFlag(Message.FAILURE_BURN_NOTIFICATION);
                    saveAction = SAVE_ACTION_UPDATE;
                }

                flags = message.getFailureFlagsAsArray();
                if (flags.length != 0
                        && (Utilities.canMessage(conversation.getPartner().getUserId())
                            || conversation.getPartner().isGroup())) {
                    saveAction = handleFailure(conversation, message);
                }

                if (saveAction == SAVE_ACTION_REMOVE) {
                    MessageUtils.deleteEvent(mContext, repository, conversation, message);
                }
                else if (saveAction == SAVE_ACTION_UPDATE) {
                    repository.historyOf(conversation).save(message);
                }

                flags = message.getFailureFlagsAsArray();
                if (flags.length != 0) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private int handleFailure(Conversation conversation, Message message) {
        int save = SAVE_ACTION_NONE;

        final boolean isGroup = conversation.getPartner().isGroup();
        final String conversationId = conversation.getPartner().getUserId();

        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "handleFailure: " + message.getId()
                    + " " + Arrays.toString(message.getFailureFlagsAsArray()));
        }
        ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();

        if (message.hasFailureFlagSet(Message.FAILURE_READ_NOTIFICATION)) {
            zinaMessaging.sendReadNotification(message);
            if (!message.hasFailureFlagSet(Message.FAILURE_READ_NOTIFICATION)) {
                save = SAVE_ACTION_UPDATE;
            }
        }
        if (message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
            if (isGroup) {
                int result = ZinaNative.burnGroupMessage(conversationId,
                        new String[]{message.getId()});
                if (result == MessageErrorCodes.SUCCESS || result == MessageErrorCodes.OK) {
                    result = ZinaMessaging.applyGroupChangeSet(conversationId);
                }
                if (result == MessageErrorCodes.SUCCESS) {
                    save = SAVE_ACTION_REMOVE;
                }
            }
            else {
                zinaMessaging.sendBurnNoticeRequest(message, conversationId);
            }
            if (!message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
                save = SAVE_ACTION_REMOVE;
            }
        }
        if (message.hasFailureFlagSet(Message.FAILURE_NOT_SENT)) {
            boolean sent = false;
            message.setAttributes(
                    MessageUtils.getMessageAttributesJSON(message.getBurnNotice(),
                            message.isRequestReceipt(), message.getLocation()));
            if (isGroup) {
                if (Utilities.isNetworkConnected(mContext)) {
                    int result = zinaMessaging.sendGroupMessage(message, null, null);
                    sent = (result == MessageErrorCodes.OK);
                    if (sent) {
                        message.clearFailureFlag(Message.FAILURE_NOT_SENT);
                        message.setExpirationTime(System.currentTimeMillis()
                                + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
                    }
                }
            }
            else {
                sent = zinaMessaging.sendMessage(message, conversation);
            }
            if (sent) {
                message.setState(isGroup ? MessageStates.READ : MessageStates.SENT);
                MessageUtils.notifyConversationUpdated(mContext, conversationId, false,
                        ZinaMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
            }
            save = SAVE_ACTION_UPDATE;
        }
        return save;
    }

    private void correctConversationAvatar(@NonNull ConversationRepository repository,
            @Nullable Conversation conversation) {
        if (conversation != null && conversation.getAvatar() != null
                && conversation.getAvatarIvAsByteArray() == null) {
            Log.d(TAG, "Removing old avatar from conversation structure: "
                    + conversation.getPartner().getUserId());
            AvatarProvider.deleteConversationAvatar(mContext, conversation.getAvatar());
            conversation.setAvatar(null);
            repository.save(conversation);
        }
    }
}
