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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.common.util.CallUtils;
import com.silentcircle.messaging.model.CallData;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.views.ResizingTextTextView;

public class CallEventView extends BaseMessageEventView implements View.OnClickListener {

    private CallMessage mCallMessage;
    private ResizingTextTextView mPhoneMessageView;
    private TextView mPhoneTimeView;
    private TextView mBurnNotice;
    private View mRetentionNotice;
    private ViewGroup mContainer;

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
        mPhoneMessageView = (ResizingTextTextView) findViewById(R.id.phone_message);
        mPhoneTimeView = (TextView) findViewById(R.id.phone_time);
        mBurnNotice = (TextView) findViewById(R.id.message_burn_notice);
        mRetentionNotice = findViewById(R.id.message_retained_notice);
        mContainer = (ViewGroup) findViewById(R.id.phone_message_container);
    }

    public void setCallData(CallMessage callMessage) {
        mCallMessage = callMessage;

        int drawableResId = CallUtils.getTypeDrawableResId(callMessage.getCallType());
        String callTime = DateUtils.getMessageTimeFormat(callMessage.getCallTime()).toString();

        mPhoneMessageView.setCompoundDrawablesWithIntrinsicBounds(drawableResId, 0, 0, 0);
        mPhoneMessageView.setText(CallUtils.formatCallData(getContext(), callMessage.getCallType(),
                callMessage.getCallDuration(),
                CallData.translateSipErrorMsg(getContext(), callMessage.getErrorMessage())));

        mPhoneTimeView.setText(callTime);

        restoreViews(callMessage.isRetained());
        updateBurnNotice();
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

    @Override
    public void setBurnNotice(CharSequence text, int visibility) {
        if (mBurnNotice.getVisibility() != visibility) {
            mBurnNotice.setVisibility(visibility);
        }
        if (text != null && !text.equals(mBurnNotice.getText())) {
            mBurnNotice.setText(text);
        }
    }

    private void restoreViews(boolean isRetained) {
        Drawable background = ContextCompat.getDrawable(getContext(), R.drawable.bg_call_card_light);
        int backgroundColorSelectorId = R.attr.sp_call_message_background_selector;

        ColorStateList backgroundTintColor;
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(backgroundColorSelectorId, typedValue, true);
        backgroundTintColor = ContextCompat.getColorStateList(getContext(), typedValue.resourceId);

        background = DrawableCompat.wrap(background);
        DrawableCompat.setTintList(background, backgroundTintColor);
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY);
        mContainer.setBackground(background);

        mRetentionNotice.setVisibility(isRetained ? View.VISIBLE : View.GONE);
    }

}
