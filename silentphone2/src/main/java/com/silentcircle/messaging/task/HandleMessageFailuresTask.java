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

package com.silentcircle.messaging.task;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.Iterator;
import java.util.List;

/**
 * .
 */
public class HandleMessageFailuresTask extends AsyncTask<String, Void, Integer> {

    private static final String TAG = HandleMessageFailuresTask.class.getSimpleName();

    private final Context mContext;

    private static final int SAVE_ACTION_NONE = 0;
    private static final int SAVE_ACTION_UPDATE = 1;
    private static final int SAVE_ACTION_REMOVE = 2;

    public HandleMessageFailuresTask(final Context context) {
        mContext = context;
    }

    @Override
    protected Integer doInBackground(String... params) {
        int count = 0;

        if (!Utilities.isNetworkConnected(mContext)) {
            // return indication for re-run if no network available
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Network not available, returning");
            return -1;
        }

        // check Axolotl registration state
        AxoMessaging axoMessaging = AxoMessaging.getInstance(mContext);
        boolean axoRegistered = axoMessaging.isRegistered();

        if (!axoRegistered) {
            // return indication for re-run if Axolotl has not been registered yet
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "AxoMessaging not ready, returning");
            return -1;
        }

        // loop through all conversations and find all failed messages
        ConversationRepository repository = axoMessaging.getConversations();
        List<Conversation> conversations = repository.list();
        Iterator<Conversation> iterator = conversations.iterator();
        while (iterator.hasNext()) {
            Conversation conversation = iterator.next();

            if (conversation == null || !repository.historyOf(conversation).exists()) {
                continue;
            }

            List<Event> events = repository.historyOf(conversation).list();
            if (events != null && events.size() > 0) {
                count += searchFailedMessages(count, repository, conversation, events);
            }
        }

        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, " Still have to handle " + count + " message failures.");
        }
        return count;
    }

    private int searchFailedMessages(int count, ConversationRepository repository,
            Conversation conversation, List<Event> events) {
        for (Event event : events) {
            if (event instanceof Message) {
                Message message = (Message) event;
                int saveAction = SAVE_ACTION_NONE;

                Long[] flags = message.getFailureFlags();
                if (flags.length != 0) {
                    saveAction = handleFailure(message);
                }

                if (saveAction == SAVE_ACTION_REMOVE) {
                    MessageUtils.deleteEvent(mContext, repository, conversation, message);
                }
                else if (saveAction == SAVE_ACTION_UPDATE) {
                    repository.historyOf(conversation).save(message);
                }

                flags = message.getFailureFlags();
                if (flags.length != 0) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private int handleFailure(Message message) {
        int save = SAVE_ACTION_NONE;

        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "handleFailure: " + message.getId() + " " + message.getFailureFlags());
        }

        if (message.hasFailureFlagSet(Message.FAILURE_READ_NOTIFICATION)) {
            AxoMessaging.getInstance(mContext).sendReadNotification(message);
            if (!message.hasFailureFlagSet(Message.FAILURE_READ_NOTIFICATION)) {
                save = SAVE_ACTION_UPDATE;
            }
        }
        if (message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
            String conversationId = MessageUtils.getConversationId(message);
            AxoMessaging.getInstance(mContext).sendBurnNoticeRequest(
                    message, conversationId);
            if (!message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
                save = SAVE_ACTION_REMOVE;
            }
        }
        return save;
    }
}
