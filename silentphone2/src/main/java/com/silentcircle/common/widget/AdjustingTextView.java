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
package com.silentcircle.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

/**
 *
 */
public class AdjustingTextView extends TextView {

    /*
     * Two line solution
     *
    private static final int TEXT_SIZE_FOR_TWO_ROWS = 10;
     */

    private float mTextSize;
    private float mMinTextSize;

    private final Paint mPaint = new Paint();
    private final Rect mBounds = new Rect();

    public AdjustingTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mTextSize = getTextSize();
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                com.silentcircle.silentphone2.R.styleable.AdjustingTextView, 0, 0);
        mMinTextSize = typedArray.getDimensionPixelSize(R.styleable.AdjustingTextView_minTextSize, 0);
        typedArray.recycle();
    }

    public AdjustingTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextPreferenceStyle);
    }

    public AdjustingTextView(Context context) {
        this(context, null);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        adjustTextSize();
    }

    protected void adjustTextSize() {
        if (TextUtils.isEmpty(getText())) {
            return;
        }

        final String text = getText().toString();

        setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        /*
         * Two line solution
         *
        setMaxLines(1);
        setSingleLine(true);
         */

        float availableWidth = getMeasuredWidth();
        float textSize = getTextSize();
        mPaint.setTypeface(getTypeface());
        mPaint.setTextSize(textSize);
        mPaint.getTextBounds(text, 0, text.length(), mBounds);

        while (mBounds.width() > availableWidth) {
            textSize -= 1;
            if (textSize < mMinTextSize) {
                break;
            }
            mPaint.setTextSize(textSize);
            mPaint.getTextBounds(text, 0, text.length(), mBounds);
        }

        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

        /*
         * Two line solution
         *
        float scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        if (textSize/scaledDensity <= TEXT_SIZE_FOR_TWO_ROWS) {
            setMaxLines(2);
            setSingleLine(false);
        }
         */

        invalidate();
    }

}
