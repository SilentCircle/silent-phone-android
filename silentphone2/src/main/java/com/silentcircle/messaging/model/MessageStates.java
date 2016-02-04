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
package com.silentcircle.messaging.model;

import android.support.annotation.IntDef;

import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Message state constants.
 */
public final class MessageStates {

    @IntDef({UNKNOWN, RESEND_REQUESTED, COMPOSING, COMPOSED, SENT, SENT_TO_SERVER, DELIVERED,
            RECEIVED, READ, BURNED, SYNC, FAILED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MessageState {}

    public static final int UNKNOWN = 0;
    public static final int RESEND_REQUESTED = 1; // resend request for message received

    public static final int COMPOSING = 2;        // message is being composed
    public static final int COMPOSED = 3;         // message composed but not passed to axo
    public static final int SENT = 4;             // message passed to AxoMessaging

    public static final int SENT_TO_SERVER = 5;   // message saved on server, recipient offline
    public static final int DELIVERED = 6;        // message delivered to partner's device

    public static final int RECEIVED = 7;         // message from partner received
    public static final int READ = 8;             // incoming or outgoing message read
    public static final int BURNED = 9;           // message burned

    public static final int SYNC = 11;            // a sync-ed outgoing message
    public static final int FAILED = 12;          // message sending failed

    private static final int[] STATES_TO_STRING_IDS = {
        R.string.message_state_unknown,
        R.string.message_state_resend_requested,
        R.string.message_state_composing,
        R.string.message_state_composed,
        R.string.message_state_sent,
        BuildConfig.DEBUG ? R.string.message_state_sent_to_server : R.string.message_state_sent,
        R.string.message_state_delivered,
        R.string.message_state_received,
        R.string.message_state_read,
        R.string.message_state_burned,
        R.string.message_state_unused,
        R.string.message_state_sync,
        R.string.message_state_failed
    };

    private MessageStates() {
    }

    public static int messageStateToStringId(final int state) {
        return state < STATES_TO_STRING_IDS.length ? STATES_TO_STRING_IDS[state] : R.string.message_state_unknown;
    }

}

