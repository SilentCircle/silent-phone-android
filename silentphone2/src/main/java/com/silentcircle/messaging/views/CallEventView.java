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

package com.silentcircle.messaging.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;


import com.silentcircle.common.util.CallUtils;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.silentphone2.R;

public class CallEventView extends CheckableRelativeLayout implements View.OnClickListener {

    private CallMessage mCallMessage;
    private TextView mPhoneMessageView;
    private TextView mPhoneTimeView;

    public CallEventView(Context context) {
        this(context, null);
    }

    public CallEventView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CallEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPhoneMessageView = (TextView) findViewById(R.id.phone_message);
        mPhoneTimeView = (TextView) findViewById(R.id.phone_time);
    }

    public void setCallData(CallMessage callMessage) {
        mCallMessage = callMessage;

        int drawableResId = CallUtils.getTypeDrawableResId(callMessage.getCallType());
        String callTime = DateUtils.getMessageTimeFormat(callMessage.getCallTime()).toString();

        mPhoneMessageView.setCompoundDrawablesWithIntrinsicBounds(drawableResId, 0, 0, 0);
        mPhoneMessageView.setText(CallUtils.formatCallData(getContext(), callMessage.getCallType(),
                callMessage.getCallDuration()));

        mPhoneTimeView.setText(callTime);
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof CallMessage) {
            setCallData((CallMessage) tag);
        }
    }

    @Override
    public void onClick(View v) {

    }
}
