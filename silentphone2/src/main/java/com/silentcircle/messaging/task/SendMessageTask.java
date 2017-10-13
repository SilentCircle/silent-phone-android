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

import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.silentphone2.util.Utilities;

import org.acra.sender.SentrySender;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import zina.ZinaNative;

/**
 * Async task to send a composed message.
 */
public class SendMessageTask extends AsyncTask<Message, Void, Message> {

    private final Context mContext;

    private boolean mResultStatus = true;
    private int mResultCode = 0;
    private String mResultInfo = null;
    private String mUser;
    private Collection<String> mDeviceIds;

    public SendMessageTask(Context context) {
        this(context, null, null);
    }

    public SendMessageTask(Context context, String user, Collection<String> deviceIds) {
        mContext = context;
        mUser = user;
        mDeviceIds = deviceIds;
    }

    @Override
    protected Message doInBackground(Message... params) {
        Message message = params[0];

        if (message != null) {

            ZinaMessaging msgService = ZinaMessaging.getInstance();
            Conversation conversation =
                    msgService.getOrCreateConversation(message.getConversationID());
            boolean isGroupMessage = conversation.getPartner().isGroup();

            mResultStatus = isGroupMessage
                    ? ((mResultCode = msgService.sendGroupMessage(message, mUser, mDeviceIds)) == MessageErrorCodes.OK)
                    : msgService.sendMessage(message, conversation);

            ConversationRepository repository = msgService.getConversations();

            /* mark message as read immediately for group messages to start burn countdown */
            if (isGroupMessage) {
                if (!Utilities.isNetworkConnected(mContext)) {
                    message.setFailureFlag(Message.FAILURE_NOT_SENT);
                }
                else {
                    message.setExpirationTime(System.currentTimeMillis()
                            + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
                    message.setState(MessageStates.READ);
                }
                repository.historyOf(conversation).save(message);
            }

            if (!mResultStatus && !isGroupMessage) {
                message.setState(MessageStates.FAILED);
                mResultCode = ZinaNative.getErrorCode();
                mResultInfo = ZinaNative.getErrorInfo();
                // Save message with new state here
                repository.historyOf(conversation).save(message);
                // report failure to Sentry, but error is probably absence of devices to send to
                SentrySender.sendMessageStateReport(message, mResultCode, MessageErrorCodes.SUCCESS);
            } else {
                message.setState(MessageStates.SENT);

                // update and persist conversation modification time
                conversation.setLastModified(System.currentTimeMillis());
                repository.save(conversation);

                SoundNotifications.playSentMessageSound();
            }
            // notify about conversation changes
            MessageUtils.notifyConversationUpdated(mContext, message.getConversationID(), false,
                    ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, message.getId());
        }

        return message;
    }

    public boolean getResultStatus() {
        return mResultStatus;
    }

    public int getResultCode() {
        return mResultCode;
    }

    public String getResultInfo() {
        return mResultInfo;
    }
}
