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
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import com.silentcircle.silentphone2.R;

/**
 * Signal quality indicator, four vertical bars
 * for states bad, weak, acceptable, good, very good.
 */
public class SignalQualityIndicator extends View {

    public static final int QUALITY_LEVEL_BAD = 0;
    public static final int QUALITY_LEVEL_WEAK = 1;
    public static final int QUALITY_LEVEL_ACCEPTABLE = 2;
    public static final int QUALITY_LEVEL_GOOD = 3;
    public static final int QUALITY_LEVEL_VERY_GOOD = 4;

    protected Paint mWhitePaint;
    protected Paint mGreenPaint;
    protected Paint mYellowPaint;
    protected Paint mRedPaint;

    protected Paint mPaint;

    private int mQuality = QUALITY_LEVEL_VERY_GOOD;

    protected Path mPathBar1;
    protected Path mPathBar2;
    protected Path mPathBar3;
    protected Path mPathBar4;

    public SignalQualityIndicator(Context context) {
        this(context, null);
    }

    public SignalQualityIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalQualityIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mWhitePaint = new Paint();
        mWhitePaint.setStyle(Paint.Style.STROKE);
        mWhitePaint.setAntiAlias(false);
        mWhitePaint.setStrokeWidth(1f);
        mWhitePaint.setColor(Color.WHITE);

        int fillColor = ContextCompat.getColor(context, R.color.sc_ng_text_green);
        mGreenPaint = new Paint();
        mGreenPaint.setColor(fillColor);

        fillColor = ContextCompat.getColor(context, R.color.silent_yellow);
        mYellowPaint = new Paint();
        mYellowPaint.setColor(fillColor);

        fillColor = ContextCompat.getColor(context, R.color.sc_ng_text_red);
        mRedPaint = new Paint();
        mRedPaint.setColor(fillColor);

        mPaint = mGreenPaint;

        mPathBar1 = new Path();
        mPathBar2 = new Path();
        mPathBar3 = new Path();
        mPathBar4 = new Path();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int w = getMeasuredWidth();
        int h = getMeasuredHeight();

        mPathBar1.reset();
        mPathBar1.moveTo(0, h);
        mPathBar1.lineTo(0, h * 0.9f);
        mPathBar1.lineTo(w * 0.25f, h * 0.9f);
        mPathBar1.lineTo(w * 0.25f, h);
        mPathBar1.lineTo(0, h);
        mPathBar1.close();

        mPathBar2.reset();
        mPathBar2.moveTo(w * 0.25f, h);
        mPathBar2.lineTo(w * 0.25f, h * 0.6f);
        mPathBar2.lineTo(w * 0.5f, h * 0.6f);
        mPathBar2.lineTo(w * 0.5f, h);
        mPathBar2.lineTo(w * 0.25f, h);
        mPathBar2.close();

        mPathBar3.reset();
        mPathBar3.moveTo(w * 0.5f, h);
        mPathBar3.lineTo(w * 0.5f, h * 0.3f);
        mPathBar3.lineTo(w * 0.75f, h * 0.3f);
        mPathBar3.lineTo(w * 0.75f, h);
        mPathBar3.lineTo(w * 0.5f, h);
        mPathBar3.close();

        mPathBar4.reset();
        mPathBar4.moveTo(w * 0.75f, h);
        mPathBar4.lineTo(w * 0.75f, 0);
        mPathBar4.lineTo(w, 0);
        mPathBar4.lineTo(w, h);
        mPathBar4.lineTo(w * 0.75f, h);
        mPathBar4.close();
    }

    public void setQuality(char quality) {
        mPaint = mGreenPaint;
        switch (quality) {
            case '0':
                mQuality = QUALITY_LEVEL_BAD;
                mPaint = mRedPaint;
                break;
            case '1':
                mQuality = QUALITY_LEVEL_WEAK;
                mPaint = mYellowPaint;
                break;
            case '2':
                mQuality = QUALITY_LEVEL_ACCEPTABLE;
                break;
            case '3':
                mQuality = QUALITY_LEVEL_GOOD;
                break;
            case '4':
                mQuality = QUALITY_LEVEL_VERY_GOOD;
                break;
            default:
                mQuality = QUALITY_LEVEL_BAD;
                mPaint = mRedPaint;
                break;
        }
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (mQuality) {
            case QUALITY_LEVEL_VERY_GOOD:
                canvas.drawPath(mPathBar4, mPaint);
            case QUALITY_LEVEL_GOOD:
                canvas.drawPath(mPathBar3, mPaint);
            case QUALITY_LEVEL_ACCEPTABLE:
                canvas.drawPath(mPathBar2, mPaint);
            case QUALITY_LEVEL_WEAK:
                canvas.drawPath(mPathBar1, mPaint);
            case QUALITY_LEVEL_BAD:
                canvas.drawPath(mPathBar1, mPaint);
                break;
        }
        canvas.drawPath(mPathBar4, mWhitePaint);
        canvas.drawPath(mPathBar3, mWhitePaint);
        canvas.drawPath(mPathBar2, mWhitePaint);
        canvas.drawPath(mPathBar1, mWhitePaint);
        canvas.drawPath(mPathBar1, mWhitePaint);
    }

}
