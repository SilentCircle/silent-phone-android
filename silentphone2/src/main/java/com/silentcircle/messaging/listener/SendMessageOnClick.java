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
package com.silentcircle.messaging.listener;

import android.location.Location;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.silentcircle.common.util.StringUtils;
import com.silentcircle.messaging.location.LocationObserver;
import com.silentcircle.messaging.location.OnLocationReceivedListener;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.task.ComposeMessageTask;
import com.silentcircle.messaging.task.SaveMessageTask;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.MessageUtils;

public class SendMessageOnClick implements OnClickListener {

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

    protected final TextView mSource;
    protected final String mUsername;
    protected final ConversationRepository mRepository;
    private final boolean mShouldRequestDeliveryNotification;

    protected Conversation mConversation;
    protected boolean mIsRetained;

    public SendMessageOnClick(TextView source, String username, Conversation conversation,
              ConversationRepository repository,
              boolean shouldRequestDeliveryNotification,
              boolean isRetained) {
        mSource = source;
        mUsername = username;
        mConversation = conversation;
        mRepository = repository;
        mShouldRequestDeliveryNotification = shouldRequestDeliveryNotification;
        mIsRetained = isRetained;
    }

    @Override
    public void onClick(View button) {

        // Do not allow to send text consisting only of whitespaces
        final String text = StringUtils.trim(mSource.getText()).toString();

        if (TextUtils.isEmpty(text)) {
            return;
        }

        mSource.setText(null);

        ComposeMessageTask task = new ComposeMessageTask(mUsername, mConversation, mRepository,
                null, mShouldRequestDeliveryNotification, mIsRetained) {

            @Override
            protected void onPostExecute(Message message) {

                if (mConversation.isLocationEnabled()) {
                    // notify about conversation changes now, we want to see message as soon as possible
                    MessageUtils.notifyConversationUpdated(mSource.getContext(),
                            message.getConversationID(), true,
                            ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, message.getId());

                    LocationObserver.observe(mSource.getContext(),
                            new SendMessageOnLocationReceived(message));
                }
                else {
                    withMessage(message);
                }
            }
        };

        AsyncUtils.execute(task, text);
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
                withMessage(message);
            }
        };

        AsyncUtils.execute(task, message);
    }

    /**
     * Allows for custom handling of the outgoing message.
     *
     * @param message the outgoing message being sent.
     */
    protected void withMessage(Message message) {
        // By default, do nothing.
    }

}
