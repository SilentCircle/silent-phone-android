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

package com.silentcircle.silentphone2.views;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.EditText;

public class FontFitEditText extends EditText {

    /*
     * Don't care if TAG is in use or not.
     * Android calls the constructors thus they are 'used' but not directly by our Java code.
     */
    @SuppressWarnings("unused")
    private static final String TAG = FontFitEditText.class.getSimpleName();

    // Used to measure text width
    private Paint mTestPaint = new Paint();

    //max size defaults to the initially specified text size unless it is too small
    private final float mTextSize;

    private boolean mHeightSet;
    private int mUseHeight;

    @SuppressWarnings("unused")
    public FontFitEditText(Context context) {
        super(context);
        mTextSize = getTextSize();
    }

    @SuppressWarnings("unused")
    public FontFitEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTextSize = getTextSize();
    }

    @SuppressWarnings("unused")
    public FontFitEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTextSize = getTextSize();
    }

    /* Re size the font so the specified text fits in the text box
     * assuming the text box is the specified width.
     */
    private void refitText(String text, final int textWidth) {
        if (textWidth <= 0)
            return;
        final int targetWidth = textWidth - getTotalPaddingLeft() - getTotalPaddingRight();
        float hi = mTextSize;
        float lo = mTextSize / 2;
        final float threshold = 0.5f; // How close we have to be

        mTestPaint.set(getPaint());

        while((hi - lo) > threshold) {
            float size = (hi+lo)/2;
            mTestPaint.setTextSize(size);
            if (mTestPaint.measureText(text) >= targetWidth) 
                hi = size; // too big
            else
                lo = size; // too small
        }
        // Use lo so that we under shoot rather than overshoot
        // Also use Pixel, not SP because here we are already working with scaled real pixel values
        setTextSize(TypedValue.COMPLEX_UNIT_PX, lo);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int height = mHeightSet ? mUseHeight : getMeasuredHeight();
        refitText(getText().toString(), parentWidth);
        setMeasuredDimension(parentWidth, height);
    }

    @Override
    protected void onTextChanged(final CharSequence text, final int start, final int before, final int after) {
        refitText(text.toString(), getWidth());
    }

    /**
     * Recompute text size if necessary.
     * 
     * On first call to onSizeChanged old width is not equal to current width, thus
     * recompute and remember the current measured height. The height should stay at
     * this value even if the text size shrinks.
     */
    @Override
    protected void onSizeChanged (final int w, final int h, final int oldWith, final int oldHeight) {
        if (w != oldWith) {
            refitText(getText().toString(), w);
            if (!mHeightSet) {
                mUseHeight = getMeasuredHeight();
                mHeightSet = true;
            }
        }
    }
}
