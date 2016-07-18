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

package com.silentcircle.messaging.views.adapters;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.messaging.views.RelativeLayout;
import com.silentcircle.messaging.views.TextView;
import com.silentcircle.silentphone2.R;

import java.util.Date;

/**
 * Header view with date string. To be used in conversation view as group header.
 */
public class DateHeaderView extends RelativeLayout {

    private TextView mText;

    /*
     * These should be accessed only from UI thread.
     * Defined as member variables to avoid extensive garbage collection when many touch events
     * are dispatched.
     */
    private Rect mHitRectangle = new Rect();
    private int[] mScreenLocation = new int[2];

    public DateHeaderView(Context context) {
        this(context, null);
    }

    public DateHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DateHeaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText = (TextView) findViewById(R.id.message_group_header);
    }

    public void setDate(@Nullable Date date) {
        if (date != null) {
            CharSequence text = DateUtils.getMessageGroupDate(getContext(), date.getTime());
            mText.setText(text);
            mText.setContentDescription(text);
        }
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof Date) {
            setDate((Date) tag);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean shouldDispatch = false;

        if (mText != null) {
            mText.getHitRect(mHitRectangle);
            mText.getLocationOnScreen(mScreenLocation);
            mHitRectangle.offset(mScreenLocation[0] - mText.getLeft(), mScreenLocation[1] - mText.getTop());
            shouldDispatch |= mHitRectangle.contains((int) event.getRawX(), (int) event.getRawY());
        }
        // shouldDispatch ? super.dispatchTouchEvent(event) : true;
        return !shouldDispatch || super.dispatchTouchEvent(event);
    }
}
