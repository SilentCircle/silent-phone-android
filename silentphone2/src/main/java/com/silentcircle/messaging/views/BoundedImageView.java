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
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Region;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.silentphone2.R;

/**
 * Image view for attachment thumbnails in chat view.
 */
public class BoundedImageView extends ImageView {

    private static final int MAXIMUM_WIDTH_PERCENTAGE = 60;
    private static final int MAXIMUM_HEIGHT_PERCENTAGE = 30;
    private Point mSize;

    private final Path mClipPathInner = new Path();
    private final Path mClipPathOuter = new Path();

    private final int mDefaultWidth;
    private final int mDefaultHeight;
    private final float mCornerRadius;

    private int mFillColor = 0x00000000;
    private ColorStateList mColorStateList;

    private int mMaximumWidth;
    private int mMaximumHeight;

    private boolean mIsImage = false;

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
        mFillColor = ContextCompat.getColor(context, android.R.color.transparent);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mClipPathOuter.reset();
            mClipPathInner.reset();
            mClipPathOuter.addRect(0.0f, 0.0f, width, height, Path.Direction.CCW);
            mClipPathInner.addRoundRect(0.0f, 0.0f, width, height, mCornerRadius, mCornerRadius,
                    Path.Direction.CCW);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
        if (mColorStateList != null) {
            mFillColor = mColorStateList.getColorForState(getDrawableState(), mFillColor);
        }
        invalidate();
    }

    public void setMaximumWidth(int maximumWidth) {
        mMaximumWidth = maximumWidth;
        requestLayout();
    }

    public void setMaximumHeight(int maximumHeight) {
        mMaximumHeight = maximumHeight;
        requestLayout();
    }

    public void setCornerColor(@ColorInt int color) {
        mFillColor = color;
    }

    public void setCornerColor(ColorStateList colorList) {
        mColorStateList = colorList;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        // draw round corners with current background colour
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.save(Canvas.CLIP_SAVE_FLAG);
            canvas.clipPath(mClipPathOuter);
            canvas.clipPath(mClipPathInner, Region.Op.DIFFERENCE);
            canvas.drawColor(mFillColor);
            canvas.restore();
        }
    }

    public void setIsImage(boolean isImage) {
        mIsImage = isImage;
    }
}
