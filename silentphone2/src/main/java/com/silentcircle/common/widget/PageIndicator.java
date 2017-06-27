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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.silentcircle.silentphone2.R;

import java.util.Vector;

/**
 * Widget to show active position within a list of positions.
 */
public class PageIndicator extends LinearLayout {

    public static final int DEFAULT_PAGE_COUNT = 4;

    protected Drawable mDrawableActive;
    protected Drawable mDrawableInactive;
    protected int mItemMargin;

    protected int mCount = -1;
    protected int mActive = -1;
    protected Vector<ImageView> mIndicators = new Vector<>();

    public PageIndicator(Context context) {
        this(context, null);
    }

    public PageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                com.silentcircle.silentphone2.R.styleable.PageIndicator, 0, 0);
        mDrawableActive = typedArray.getDrawable(R.styleable.PageIndicator_srcActive);
        mDrawableInactive = typedArray.getDrawable(R.styleable.PageIndicator_srcInactive);
        mItemMargin = typedArray.getDimensionPixelSize(R.styleable.PageIndicator_itemMargin, 15);
        typedArray.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setCount(DEFAULT_PAGE_COUNT);
    }

    public void setCount(int count) {
        if (count < 1) {
            return;
        }
        mCount = count;

        removeAllViews();
        mIndicators.clear();

        for (int i = 0; i < mCount; i++) {
            ImageView view = new ImageView(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(i > 0 ? mItemMargin : 0, 0, 0, 0);
            view.setLayoutParams(params);
            view.setImageDrawable(mDrawableInactive);
            mIndicators.add(view);
            addView(view);
        }
        setActive(mActive < 0 ? 0 : mActive);
    }

    public void setActive(int index) {
        if (index > mCount - 1 || index < 0) {
            return;
        }
        setInactive(mActive);
        mActive = index;
        mIndicators.get(index).setImageDrawable(mDrawableActive);

        Context context = getContext();
        if (context != null) {
            setContentDescription(getResources().getString(
                    R.string.messaging_pdf_viewer_showing_page_voiceover, index + 1, mCount));
        }
    }

    public void setInactive(int index) {
        if (index > mCount - 1 || index < 0) {
            return;
        }
        mIndicators.get(index).setImageDrawable(mDrawableInactive);
    }

}
