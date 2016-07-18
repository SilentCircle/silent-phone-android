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
import android.content.Intent;
import android.location.Location;

import com.silentcircle.messaging.location.LocationObserver;
import com.silentcircle.messaging.location.OnLocationReceivedListener;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.model.CallData;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.Manifest;

/**
 * Allows us to easily and completely compose and send
 * 1. Text messages
 * 2. Attachment messages
 * 3. Call messages (will be sent only to siblings)
 *
 * This will use the context of a conversation - so burn timer and location will be set
 */
public class MessageSender {

    public class SendMessageOnLocationReceived implements OnLocationReceivedListener {

        private final Message mMessage;

        public SendMessageOnLocationReceived(final Message message) {
            mMessage = message;
        }

        @Override
        public void onLocationReceived(Location location) {
            setMessageLocation(mMessage, location);
        }

        @Override
        public void onLocationUnavailable() {
            setMessageLocation(mMessage, null);
        }
    }

    private final Context mContext;
    private final String mUsername;
    private final ConversationRepository mRepository;

    private Conversation mConversation;

    private boolean mComposeOnly;
    private boolean mSiblingsOnly;

    public MessageSender(Context context, String username, Conversation conversation,
                         ConversationRepository repository) {
        mContext = context;
        mUsername = username;
        mConversation = conversation;
        mRepository = repository;
    }

    public void sendTextMessage(String text) {
        send(text, null, null);
    }

    public void sendAttachmentMessage(String attachment) {
        send(null, attachment, null);
    }

    public void sendPhoneMessage(CallData callData) {
        mSiblingsOnly = true;

        send(null, null, callData);
    }

    public void composeTextMessage(String text) {
        mComposeOnly = true;

        send(text, null, null);
    }

    public void composeAttachmentMessage(String attachment) {
        mComposeOnly = true;

        send(null, attachment, null);
    }

    public void composePhoneMessage(CallData callData) {
        mComposeOnly = true;

        send(null, null, callData);
    }

    private void send(String text, String attachment, CallData calldata) {
        ComposeMessageTask task = new ComposeMessageTask(mUsername, mConversation, mRepository,
                null, attachment, calldata, false) {

            @Override
            protected void onPostExecute(Message message) {
                if (mConversation.isLocationEnabled()) {
                    // notify about conversation changes now, we want to see message as soon as possible
                    Intent intent = Action.UPDATE_CONVERSATION.intent();
                    Extra.PARTNER.to(intent, message.getConversationID());
                    mContext.sendOrderedBroadcast(intent, Manifest.permission.READ);

                    LocationObserver.observe(mContext,
                            new SendMessageOnLocationReceived(message));
                }
                else {
                    sendMessage(message);
                }
            }
        };

        AsyncUtils.execute(task, text == null ? "" : text);
    }

    protected void setMessageLocation(final Message message, final Location location) {
        // update message location, if it is not null;
        if (location != null) {
            MessageUtils.setMessageLocation(message, location);
        }

        message.setState(MessageStates.COMPOSED);

        SaveMessageTask task = new SaveMessageTask(mConversation, mRepository) {

            @Override
            protected void onPostExecute(Message message) {
                sendMessage(message);
            }
        };

        AsyncUtils.execute(task, message);
    }

    protected void sendMessage(Message message) {
        if (mComposeOnly) {
            return;
        }

        SendMessageTask task = new SendMessageTask(mContext, mSiblingsOnly) {

            @Override
            protected void onPostExecute(Message message) {

            }
        };
        AsyncUtils.execute(task, message);
    }

}
