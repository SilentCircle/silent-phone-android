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
package com.silentcircle.silentphone2.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.RelativeLayout;

import com.silentcircle.silentphone2.R;

public class VolumeIndicatorLayout extends RelativeLayout {
    private IndicatorView mIndicator;
    private float mCurrentScale = 1.0f;
    private Paint mPaint;

    public VolumeIndicatorLayout(Context context) {
        this(context, null);
    }

    public VolumeIndicatorLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VolumeIndicatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int indicatorRadius = (int) getResources().getDimension(R.dimen.in_call_btn_width);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(ContextCompat.getColor(getContext(), R.color.indicator_gray));

        LayoutParams rippleParams = new LayoutParams(indicatorRadius, indicatorRadius);
        rippleParams.addRule(CENTER_IN_PARENT, TRUE);
        mIndicator = new IndicatorView(getContext());
        addView(mIndicator, rippleParams);
    }

    private class IndicatorView extends View {
        public IndicatorView(Context context) {
            super(context);
            this.setAlpha(0.5f);
            this.setVisibility(VISIBLE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int radius = (Math.min(getWidth(), getHeight())) / 2;
            canvas.drawCircle(radius, radius, radius, mPaint);
        }
    }

    public void setVolume(int volume) {
        float scale = volume/9.0f + 1.1f; //Scale from 1.1 to 2.1

        Animation anim = new ScaleAnimation(
                mCurrentScale, scale,
                mCurrentScale, scale,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setFillEnabled(true);
        anim.setFillAfter(true);
        anim.setDuration(100);
        mIndicator.startAnimation(anim);

        mCurrentScale = scale;
    }
}