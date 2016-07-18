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

import android.os.AsyncTask;

import com.silentcircle.messaging.model.Conversation;
import android.location.Location;

import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.util.MessageUtils;

public class ComposeMessageTask extends AsyncTask<String, Void, Message> {

    private static final String TAG = "ComposeMessageTask";
    private final boolean mShouldRequestDeliveryNotification;
    private final Conversation mConversation;
    private final ConversationRepository mRepository;
    private final String mSelfUserName;
    private final Location mLocation;

    public ComposeMessageTask(String self, Conversation conversation,
                              ConversationRepository repository,
                              Location location,
                              boolean shouldRequestDeliveryNotification) {
        mSelfUserName = self;
        mConversation = conversation;
        mRepository = repository;
        mLocation = location;
        mShouldRequestDeliveryNotification = shouldRequestDeliveryNotification;
    }

    @Override
    protected Message doInBackground(String... messages) {
        if (messages.length <= 0) {
            return null;
        }

        String messageText = messages[0];
        Message message = MessageUtils.composeMessage(mSelfUserName, messageText,
                mShouldRequestDeliveryNotification, mConversation, mLocation);
        message.setState(isNoteToSelf()
                ? MessageStates.SENT
                : (mConversation.isLocationEnabled()
                        ? MessageStates.COMPOSING : MessageStates.COMPOSED));
        return save(message);
    }

    private boolean isNoteToSelf() {
        return mSelfUserName.equals(mConversation.getPartner().getUserId());
    }

    private Message save(Message message) {
        mRepository.historyOf(mConversation).save(message);
        mConversation.setLastModified(System.currentTimeMillis());
        mRepository.save(mConversation);
        return message;
    }

}
