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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import com.silentcircle.silentphone2.R;

/**
 * Square image view.
 * Keeps image height the same as width.
 */
public class SquareImageView extends View {

    private Paint mWhitePaint;
    private Path mClipPathInner = new Path();
    private Path mClipPathOuter = new Path();

    private boolean mIsCircle = false;
    private int mFillColor = 0x80111111;

    public SquareImageView(Context context) {
        this(context, null);
    }

    public SquareImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SquareImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mWhitePaint = new Paint();
        mWhitePaint.setStyle(Paint.Style.STROKE);
        mWhitePaint.setAntiAlias(true);
        mWhitePaint.setStrokeWidth(6f);
        mWhitePaint.setColor(Color.WHITE);
        mFillColor = ContextCompat.getColor(context, R.color.black_translucent);
    }

    @Override
    @SuppressWarnings("SuspiciousNameCombination")
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        if (width != height) {
            setMeasuredDimension(width, width);
        }

        mClipPathOuter.reset();
        mClipPathInner.reset();
        mClipPathOuter.addRect(0.0f, 0.0f, width, width, Path.Direction.CCW);
        mClipPathInner.addCircle(width / 2, width / 2, width / 2, Path.Direction.CCW);
    }

    public boolean isCircle() {
        return mIsCircle;
    }

    public void setIsCircle(boolean isCircle) {
        mIsCircle = isCircle;
        invalidate();
    }

    public void toggleIsCircle() {
        mIsCircle = !mIsCircle;
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        if (isCircle()) {
            canvas.save(Canvas.CLIP_SAVE_FLAG);
            canvas.clipPath(mClipPathOuter);
            canvas.clipPath(mClipPathInner, Region.Op.DIFFERENCE);
            canvas.drawColor(mFillColor);
            canvas.restore();
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, getWidth() / 2, mWhitePaint);
        } else {
            canvas.drawPath(mClipPathOuter, mWhitePaint);
        }
    }

}

