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
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.silentphone2.R;

/**
 * Image view for attachment thumbnails in chat view.
 */
public class BoundedImageView extends android.support.v7.widget.AppCompatImageView {

    private static final int MAXIMUM_WIDTH_PERCENTAGE = 60;
    private static final int MAXIMUM_HEIGHT_PERCENTAGE = 30;
    private Point mSize;

    private final int mDefaultWidth;
    private final int mDefaultHeight;
    private final float mCornerRadius;

    private int mMaximumWidth;
    private int mMaximumHeight;

    private boolean mIsImage = false;

    private Path mPath = new Path();
    private RectF mRect = new RectF();

    public BoundedImageView(Context context) {
        this(context, null);
    }

    public BoundedImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BoundedImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mDefaultWidth = (int) getResources().getDimension(R.dimen.messaging_message_thumbnail_width);
        mDefaultHeight = (int) getResources().getDimension(R.dimen.messaging_message_thumbnail_height);
        mCornerRadius = context.getResources().getDimension(R.dimen.messaging_message_thumbnail_corner_radius);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mSize == null) {
            mSize = new Point();
            ViewUtil.getScreenDimensions(getContext(), mSize);
            mMaximumWidth = (MAXIMUM_WIDTH_PERCENTAGE * mSize.x * 2 - mSize.x) / 200;
            mMaximumHeight = (MAXIMUM_HEIGHT_PERCENTAGE * mSize.y * 2 - mSize.y) / 200;
        }

        int width = mDefaultWidth;
        int height = mDefaultHeight;

        // to have old fixed size back this section has to be commented out
        if (mIsImage) {
            width = mMaximumWidth;
            height = mMaximumHeight;

            int measureMode = MeasureSpec.getMode(widthMeasureSpec);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, measureMode);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setMaximumWidth(int maximumWidth) {
        mMaximumWidth = maximumWidth;
        requestLayout();
    }

    public void setMaximumHeight(int maximumHeight) {
        mMaximumHeight = maximumHeight;
        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int save = canvas.save();
            canvas.clipPath(mPath);
            super.onDraw(canvas);
            canvas.restoreToCount(save);
        }
        else {
            super.onDraw(canvas);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // compute the path
        mPath.reset();
        mRect.set(0, 0, w, h);
        mPath.addRoundRect(mRect, mCornerRadius, mCornerRadius, Path.Direction.CW);
        mPath.close();

    }

    public void setIsImage(boolean isImage) {
        mIsImage = isImage;
    }

}
