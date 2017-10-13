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

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.contacts.ScCallLog;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.activities.DialerActivityInternal;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import zina.ZinaNative;

public class RefreshTask extends AsyncTask<String, Void, List<Event>> {

    private static final String TAG = RefreshTask.class.getSimpleName();

    private final Conversation mConversation;
    private final ConversationRepository mRepository;

    private int mUnreadMessageCount = 0;
    private int mUnreadCallMessageCount = 0;

    public RefreshTask(ConversationRepository repository, Conversation conversation) {
        mRepository = repository;
        mConversation = conversation;
    }

    @Override
    protected List<Event> doInBackground(String... args) {

        String partner = args[0];

        if (partner == null) {
            partner = mConversation.getPartner().getUserId();
        }

        if (mConversation == null || mRepository == null) {
            // cannot proceed with refresh here
            return new ArrayList<>();
        }

        Context context = SilentPhoneApplication.getAppContext();

        EventRepository history = mRepository.historyOf(mConversation);
        List<Event> events = history.list();
        events = removeExpiredMessages(history, events);

        mUnreadMessageCount = 0;
        mUnreadCallMessageCount = 0;
        long autoRefreshTime = Long.MAX_VALUE;
        int numThumbnailDownloads = 0; // Used to modify the auto refresh time in case a download fails
        for (Event event : events) {
            if (event instanceof Message) {
                Message message = (Message) event;
                boolean save = false;
                if (event instanceof IncomingMessage || event instanceof CallMessage
                        || event instanceof OutgoingMessage) {
                    save = handleMessageByState(message);

                    if (message.hasAttachment() && !AttachmentUtils.hasThumbnail(message)) {
                        context.startService(
                                SCloudService.getDownloadThumbnailIntent(event, partner, context));
                        numThumbnailDownloads++;
                    }
                }
                long currentMillis = System.currentTimeMillis();
                /*
                 * calculate next timeslot for refresh,
                 * do not refresh sooner than after one second
                 */
                autoRefreshTime =
                        Math.max(
                                Math.min(autoRefreshTime, message.getExpirationTime()),
                                currentMillis + TimeUnit.SECONDS.toMillis(1));

                if (save) {
                    history.save(message);
                }
            }
        }
        // TODO: Have data structure to mark if a thumbnail is currently downloading (to avoid redundant downlaods)
        // If there are thumbnails downloading, schedule the next update in at most 3 * numThumbnailDownloads seconds
        onScheduleNext(numThumbnailDownloads > 0
                ? Math.min(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(3) * numThumbnailDownloads
                    , autoRefreshTime)
                : autoRefreshTime);

        // attempt to correct unread message count for conversation
        checkUnreadMessageCounts(partner);

        return MessageUtils.filter(events, DialerActivityInternal.mShowErrors);
    }

    protected void onScheduleNext(long next) {
    }

    private boolean handleMessageByState(Message message) {
        boolean save = false;
        int state = message.getState();
        if (MessageStates.RECEIVED == state) {
            mUnreadMessageCount += 1;
        } else if (MessageStates.COMPOSED == state
                && message instanceof CallMessage
                && ((CallMessage) message).callType == ScCallLog.ScCalls.MISSED_TYPE) {
            mUnreadCallMessageCount += 1;
        }
        return save;
    }

    private List<Event> removeExpiredMessages(EventRepository history, List<Event> events) {
        List<Event> newList = new ArrayList<>();
        final boolean isGroup = mConversation.getPartner().isGroup();
        final String conversationId = mConversation.getPartner().getUserId();
        for (Event event : events) {
            if (event instanceof Message) {
                Message message = (Message) event;

                if (MessageStates.BURNED == message.getState()
                        && message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
                    Log.d(TAG, "#list resend burn request: " + message.getId());
                    if (isGroup) {
                        int result = ZinaNative.burnGroupMessage(conversationId,
                                new String[]{message.getId()});
                        if (result == MessageErrorCodes.SUCCESS || result == MessageErrorCodes.OK) {
                            result = ZinaMessaging.applyGroupChangeSet(conversationId);
                        }
                        if (result == MessageErrorCodes.SUCCESS) {
                            // sending was successful, clear failure flag so messages get deleted
                            message.clearFailureFlag(Message.FAILURE_BURN_NOTIFICATION);
                        }
                    }
                    else {
                        ZinaMessaging.getInstance().sendBurnNoticeRequest(message, conversationId);
                    }
                    history.save(message);
                    continue;
                }

                /*
                 * For group conversations update message burn time if conversation's burn time
                 * has changed and is less than burn time for message itself.
                 */
                if (isGroup && mConversation.getBurnDelay() < message.getBurnNotice()) {
                    long burnDelay = mConversation.getBurnDelay();
                    if (ConfigurationUtilities.mTrace) {
                        Log.d(TAG, "#list updating message expiry time: " + message.getId()
                                + " from " + message.getBurnNotice()
                                + " to " + burnDelay);
                    }

                    message.setBurnNotice(burnDelay);
                    message.setExpirationTime(message.getComposeTime()
                            + TimeUnit.SECONDS.toMillis(burnDelay));
                    history.save(message);
                }

                if (message.isExpired() || MessageStates.BURNED == message.getState()) {
                    if (ConfigurationUtilities.mTrace) {
                        Log.d(TAG, "#list removing expired id: " + message.getId());
                    }

                    /*
                     * MessageUtils.removeMessage could be used but it starts another async task
                     * and here we already are on background thread.
                     */
                    history.remove(message);
                    MessageUtils.startAttachmentCleanUp(SilentPhoneApplication.getAppContext(),
                            mConversation.getPartner().getUserId(), message);
                    MessageUtils.notifyConversationUpdated(
                            SilentPhoneApplication.getAppContext(), conversationId,
                            false, ZinaMessaging.UPDATE_ACTION_MESSAGE_BURNED, message.getId());
                    continue;
                }
            }
            newList.add(event);
        }
        return newList;
    }

    private void checkUnreadMessageCounts(@NonNull final String partner) {
        // re-read conversation to avoid using possibly stale version
        Conversation conversation = mRepository.findById(partner);
        if (conversation != null) {
            int unreadMessageCount = conversation.getUnreadMessageCount();
            if (unreadMessageCount != mUnreadMessageCount) {
                Log.w(TAG, "Discrepancy between actual and reported unread message count for conversation: "
                        + mUnreadMessageCount + "/" + unreadMessageCount);
                conversation.setUnreadMessageCount(mUnreadMessageCount);
                mRepository.save(conversation);
            }

            int unreadCallMessageCount = conversation.getUnreadCallMessageCount();
            if (unreadCallMessageCount != mUnreadCallMessageCount) {
                Log.w(TAG, "Discrepancy between actual and reported unread call message count for conversation: "
                        + mUnreadCallMessageCount + "/" + unreadMessageCount);
                conversation.setUnreadCallMessageCount(mUnreadCallMessageCount);
                mRepository.save(conversation);
            }
        }
    }
}

