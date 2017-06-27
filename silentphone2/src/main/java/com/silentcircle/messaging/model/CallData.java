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
package com.silentcircle.messaging.model;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.silentcircle.silentphone2.R;

public class CallData {
    public int type;
    public int duration;
    public long time;
    public String errorMessage;

    public CallData(int type, int duration, long time, String errorMessage) {
        this.type = type;
        this.duration = duration;
        this.time = time;
        this.errorMessage = errorMessage;
    }

    /**
     * Returns correct resource string for a given SIP error message saved in call event.
     *
     * Based on function TiviPhoneService#translateSipErrorMsg.
     */
    @SuppressLint("DefaultLocale")
    @Nullable
    public static String translateSipErrorMsg(@Nullable final Context context, @Nullable final String msg) {
        if (TextUtils.isEmpty(msg)) {
            return null;
        }

        if (context == null) {
            return null;
        }

        Resources resources = context.getResources();

        String trimmed = msg.trim().toLowerCase();

        if (trimmed.contains("policy conflict #1984"))
            return resources.getString(R.string.sip_error_call_decline_dr_blocked_errblk_log);
//        if (trimmed.contains("data retention rejected"))
//            return resources.getString(R.string.sip_error_call_decline_dr_rejected_errdrj_log);
        if (trimmed.contains("user not found"))
            return resources.getString(R.string.sip_error_no_user);
        if (trimmed.startsWith("call declined elsewhere"))
            return resources.getString(R.string.sip_error_call_declined_elsewhere);
        if (trimmed.contains("decline"))
            return resources.getString(R.string.sip_error_decline);
        if (trimmed.startsWith("cannot connect"))
            return resources.getString(R.string.sip_error_generic);
        if (trimmed.startsWith("remote party is out of coverage"))
            return resources.getString(R.string.sip_error_no_cover);
        if (trimmed.startsWith("could not reach server"))
            return resources.getString(R.string.sip_error_no_server);
        if (trimmed.contains("unavailable") || trimmed.contains("not available") || trimmed.contains("timeout"))
            return resources.getString(R.string.call_unavailable);
        if (trimmed.startsWith("cannot register"))
            return resources.getString(R.string.sip_error_register);
        if (trimmed.startsWith("user not online"))
            return resources.getString(R.string.sip_error_not_online);
        if (trimmed.startsWith("call completed elsewhere"))
            return resources.getString(R.string.sip_error_call_answered_elsewhere);

        if (Character.isDigit(msg.charAt(0))) {
            // Remove any prefixed 3 digit error code (402, 404, etc.)
            return msg.substring(4);
        }

        return msg;
    }
}