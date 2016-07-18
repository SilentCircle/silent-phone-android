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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;

public class FailureEventView extends CheckableRelativeLayout implements View.OnClickListener {

    private TextView mText;
    private ErrorEvent mErrorEvent;

    public FailureEventView(Context context) {
        this(context, null);
    }

    public FailureEventView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FailureEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText = (TextView) findViewById(R.id.error_message);
    }

    @Override
    public void onClick(View view) {
        /* show message on click in debug builds */
        if (BuildConfig.DEBUG && mErrorEvent != null
                && !TextUtils.isEmpty(mErrorEvent.getMessageText())) {
            MessageUtils.showEventInfoDialog(view.getContext(), mErrorEvent);
        }
    }

    public void setFailure(ErrorEvent errorEvent) {
        mErrorEvent = errorEvent;

        int failureTextId = MessageErrorCodes.messageErrorToStringId(errorEvent.getError());

        if (errorEvent.hasText()) {
            setText(errorEvent.getText());
        } else {
            setText(getResources().getString(failureTextId) + (errorEvent.isDuplicate() ? " (>1)" : ""));
        }
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof ErrorEvent) {
            setFailure((ErrorEvent) tag);
        }
    }

    public void setText(String text) {
        mText.setText(text);
    }
}
