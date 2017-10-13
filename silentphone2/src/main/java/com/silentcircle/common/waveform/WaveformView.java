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
package com.silentcircle.common.waveform;

import android.content.Context;
import android.graphics.Canvas;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * A view that draws a waveform for the Sound Pressure Levels that were provided using
 * {@link #setLevels(float[])}. If no data are provided, a default waveform is drawn.
 *
 * Drawing is performed using {@link WaveformDrawer} on the data returned by
 * {@link SoundFile#getLevels()}.
 */
public class WaveformView extends View {

    private static final String TAG = WaveformView.class.getSimpleName();
    private static final float[] DEFAULT_WAVEFORM = new float[]{
            -120, -120, -95, -92, -81, -70, -73, -72, -72, -73, -70, -71, -71, -70, -71, -70, -71, -70, -70, -71,
            -71, -71, -70, -71, -62, -64, -67, -69, -69, -71, -68, -70, -43, -27, -23, -24, -26, -29, -28, -30,
            -33, -35, -38, -40, -43, -45, -48, -50, -53, -55, -58, -60, -63, -65, -68, -70, -55, -30, -25, -22,
            -24, -26, -29, -31, -34, -36, -39, -41, -44, -46, -49, -51, -54, -56, -59, -61, -64, -66, -68, -70};

    private int mCanvasWidth = 0;
    private int mCanvasHeight = 0;
    private boolean mDirty = true;

    private float[] mLevels = null;
    private WaveformDrawer mWaveformDrawer = null;
    private WaveformDrawerConfigurator.Configuration mDrawerConfiguration = null;

    private int mDensityDPI;

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mDensityDPI = getResources().getDisplayMetrics().densityDpi;
        mLevels = DEFAULT_WAVEFORM;
        // Rendering the waveform on a software layer dramatically increases performance
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /**
     * Set the levels this view will draw.
     *
     * The levels should be in SPL dB values as defined in {@link SoundFile}.
     *
     * @param levels the level values to draw
     */
    public void setLevels(float[] levels) {
        boolean sameSize = false;
        if (mLevels != null && levels != null && mLevels.length == levels.length) {
            sameSize = true;
        }
        mLevels = levels;

        mDirty = true;
        if (sameSize) {
            invalidate();
        }
        else {
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = (widthMode == MeasureSpec.UNSPECIFIED) ? Integer.MAX_VALUE : MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = heightMode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(heightMeasureSpec);

        int finalWidthMeasureSpec = widthMeasureSpec;
        int finalHeightMeasureSpec = heightMeasureSpec;

        if (mLevels != null) {
            // If we can specify the view's size, we will calculate it so that the levels are rendered
            // in a pixel perfect way
            if (widthMode != MeasureSpec.EXACTLY) {
                mDrawerConfiguration = WaveformDrawerConfigurator.getConfiguration(mLevels.length, mDensityDPI);
                int measuredWidth = mDrawerConfiguration.width;
                finalWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                        measuredWidth, MeasureSpec.EXACTLY);
                if (heightMode != MeasureSpec.EXACTLY) {
                    int measuredHeight = mDrawerConfiguration.height;
                    finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY);
                }
                else {
                    mDrawerConfiguration.height = heightSize;
                }
            } else {
                // Width is specified exactly
                mDrawerConfiguration = WaveformDrawerConfigurator.getConfigurationForWidth(widthSize, mDensityDPI);
                if (heightMode != MeasureSpec.EXACTLY) {
                    int measuredHeight = mDrawerConfiguration.height;
                    finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY);
                }
                else {
                    mDrawerConfiguration.height = heightSize;
                }
            }
        }

        super.onMeasure(finalWidthMeasureSpec, finalHeightMeasureSpec);

//        Log.d(TAG, finalWidthMeasureSpec + " " + finalHeightMeasureSpec);

//        Log.d(TAG, MeasureSpec.AT_MOST + " " + MeasureSpec.EXACTLY + " " + MeasureSpec.UNSPECIFIED);
//        Log.d(TAG, widthMode + " " + widthSize + " " + heightMode + " " + heightSize);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == oldw && h == oldh) {
            return;
        }
        mCanvasWidth = w;
        mCanvasHeight = h;

        mDirty = true;
    }

    private void updateWaveformDrawer() {
        if (mLevels == null || mCanvasWidth == 0 || mCanvasHeight == 0) {
            mWaveformDrawer = null;
            return;
        }
        if (mDrawerConfiguration != null) {
            mWaveformDrawer = new WaveformDrawer(mLevels, mDrawerConfiguration);
        }
        else {
            mWaveformDrawer = new WaveformDrawer(mLevels, mLevels.length, mCanvasWidth, mCanvasHeight);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDirty) {
            mDirty = false;
            updateWaveformDrawer();
        }

        if (mLevels == null || mCanvasWidth == 0 || mCanvasHeight == 0 || mWaveformDrawer == null) {
            return;
        }
        mWaveformDrawer.draw(canvas);
    }
}
