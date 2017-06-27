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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.silentcircle.silentphone2.R;

/**
 * Decorative triangles for on-boarding.
 */
public class TriangleDrawable extends View {

    protected int mColor;
    protected float[] mPositions = new float[6];

    protected Paint mPaint;
    protected Path mPath;

    public TriangleDrawable(Context context) {
        this(context, null);
    }

    public TriangleDrawable(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TriangleDrawable(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.TriangleDrawable, 0, 0);
        mColor = typedArray.getColor(R.styleable.TriangleDrawable_triangleColor, 0);
        mPositions[0] = typedArray.getFloat(R.styleable.TriangleDrawable_position1x, 0.0f);
        mPositions[1] = typedArray.getFloat(R.styleable.TriangleDrawable_position2x, 0.0f);
        mPositions[2] = typedArray.getFloat(R.styleable.TriangleDrawable_position3x, 0.0f);
        mPositions[3] = typedArray.getFloat(R.styleable.TriangleDrawable_position1y, 0.0f);
        mPositions[4] = typedArray.getFloat(R.styleable.TriangleDrawable_position2y, 0.0f);
        mPositions[5] = typedArray.getFloat(R.styleable.TriangleDrawable_position3y, 0.0f);
        typedArray.recycle();

        mPaint = new Paint();
        mPaint.setColor(mColor);
        mPath = new Path();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int w = getMeasuredWidth();
        int h = getMeasuredHeight();

        mPath.reset();
        mPath.moveTo(w * mPositions[0], h * mPositions[3]);
        mPath.lineTo(w * mPositions[1], h * mPositions[4]);
        mPath.lineTo(w * mPositions[2], h * mPositions[5]);
        mPath.lineTo(w * mPositions[0], h * mPositions[3]);
        mPath.close();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(mPath, mPaint);
    }

}
