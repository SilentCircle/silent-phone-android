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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import static android.util.DisplayMetrics.DENSITY_DEFAULT;

/**
 * WaveformDrawer draws a waveform according to the provided Sound Pressure Levels array in dB.
 *
 * The class can be used either for drawing on a bitmap or on a view's canvas.
 */
public class WaveformDrawer {

    private static final String TAG = WaveformDrawer.class.getSimpleName();

    public static final int MIN_LEVEL_DB = -40;
    private static final float VERTICAL_WAVEFORM_HEIGHT = 0.95f;
    private static final float MIN_HEIGHT_BIN_RATIO = 1.f/40;
    private static boolean NORMALIZE = true;

    private float[] mLevels;
    private float[] mNormalizedLevels;
    private int mMinLevel;
    private int mMaxLevel;

    private int mWidth;
    private int mHeight;
    private int mDensity;
    private int mSlots; // Waveform is split horizontally in slots, consisting of drawn bins and spaces.
    private int mBins; // The number of bins. This is the half of the slots.
    private float mBinWidth = -1;
    private boolean mShouldSample;

    private static Paint mPaint = new Paint();

    static {
        mPaint.setColor(Color.BLACK);
    }

    /**
     * Creates a new WaveformDrawer that can be used to draw a bitmap using {@link #drawBitmap()}
     * having the specified size in DP and density.
     *
     * Drawer will render as many bins as specified in the specified canvas width. Thus, the width of the
     * bins will be calculated according to the specified parameters. If the number of bins does
     * not match the length of {@code levels} array, drawer will interpolate the data.
     *
     * @param levels the levels array contain the dB values to draw
     * @param widthDP the width of the bitmap in DP
     * @param heightDP the height of the bitmap in DP
     * @param density the density of the bitmap
     */
    public WaveformDrawer(float[] levels, int bins, int widthDP, int heightDP, int density) {
        float dpRatio = density / DENSITY_DEFAULT;
        mWidth = (int) (widthDP * dpRatio);
        mHeight = (int) (heightDP * dpRatio);
        mDensity = density;

        mBins = bins;
        mLevels = levels;
        init();
    }

    /**
     * Creates a new WaveformDrawer that can be used to draw on a canvas, having the provided size.
     *
     * Rendering will be performed as described in {@link #WaveformDrawer(float[], int, int, int, int)}.
     *
     * @param levels the levels array contain the dB values to draw
     * @param width the width of the canvas in pixels
     * @param height the height of the canvas in pixels
     */
    public WaveformDrawer(float[] levels, int bins, int width, int height) {
        mWidth = width;
        mHeight = height;
        mDensity = DENSITY_DEFAULT;

        mBins = bins;
        mLevels = levels;
        init();
    }

    /**
     * Creates a new WaveformDrawer that can be used to draw a bitmap using {@link #drawBitmap()}
     * or {@link #draw(Canvas)}.
     *
     * Drawer will render the number of the specified bins having the specified width in the specified
     * canvas width.
     *
     * A configuration file can be calculated with one of the methods in {@link WaveformDrawerConfigurator.Configuration}
     * and a pixel-perfect result can be achieved.
     *
     * @param levels the levels array contain the dB values to draw
     * @param configuration the parameters that drawer will respect when drawing
     */
    public WaveformDrawer(float levels[], WaveformDrawerConfigurator.Configuration configuration) {
        mWidth = configuration.width;
        mHeight = configuration.height;
        mDensity = configuration.densityDPI;

        mBins = configuration.bins;
        mBinWidth = configuration.binWidth;

        mLevels = levels;
        init();
    }

    private void init() {
        // Calculate variables for current size and bin number
        mSlots = mBins * 2;
        if (mBinWidth == -1) {
            mBinWidth = ((float) mWidth / mSlots);
            if (mBinWidth < 1) {
                mBinWidth = 1;
            }
            mSlots = (int) (mWidth / mBinWidth); // re-calculate based on new bin width
            mBins = mSlots / 2;
        }

        mShouldSample = mLevels.length != mBins;

//        Log.d(TAG, "Bins: " + mBins + " (" + mBinWidth  + "px), dB levels: " + mLevels.length);

        // Update paint state
        boolean useAntiAlias = mBinWidth != (int) Math.floor(mBinWidth);
        if (useAntiAlias != mPaint.isAntiAlias()) {
            mPaint.setAntiAlias(useAntiAlias);
        }
        if (mPaint.getStrokeWidth() != mBinWidth) {
            mPaint.setStrokeWidth(mBinWidth);
        }

        // Scale db to [0, 1] range
        mNormalizedLevels = new float[mLevels.length];
        for (int i = 0; i < mLevels.length; i++) {
            float value = mLevels[i] / -MIN_LEVEL_DB + 1;
            value = Math.max(0, value);
            mNormalizedLevels[i] = value;
        }

        // Build histogram of 256 bins and figure out the  max level
        mMaxLevel = 0;
        mMinLevel = 0;
        if (NORMALIZE) {
            int levelHist[] = new int[256];
            for (int i = 0; i < mNormalizedLevels.length; i++) {
                int level = (int) (mNormalizedLevels[i] * 255);
                if (level < 0)
                    level = 0;
                if (level > 255)
                    level = 255;

                if (level > mMaxLevel)
                    mMaxLevel = level;

                levelHist[level]++;
            }

            // Re-calibrate the max to be 99%
            int sum = 0;
            while (mMaxLevel > 2 && sum < mNormalizedLevels.length / 100) {
                sum += levelHist[mMaxLevel];
                mMaxLevel--;
            }
            mMaxLevel = Math.max(mMaxLevel, 100);
        }
    }

    /**
     * Draws the waveform on a bitmap having a white background.
     *
     * The bitmap will have the density and size in DP as specified in the drawer's constructor.
     *
     * @return the bitmap
     */
    public Bitmap drawBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(mDensity);

        Canvas canvas = new Canvas(bitmap);

        // Draw
//        canvas.drawColor(Color.WHITE);
        draw(canvas);

        return bitmap;
    }

    /**
     * Draws the waveform on the provided canvas.
     *
     * No background is drawn.
     * @param canvas the canvas to draw to
     */
    public void draw(Canvas canvas) {
        float minBinHeight = Math.round(mHeight * MIN_HEIGHT_BIN_RATIO);
        minBinHeight = Math.max(minBinHeight, 1.0f);
        float minBinHalfHeight = minBinHeight / 2;

        float ctr = mHeight / 2;

        int binIndex = 0;
        for (int i = 0; i < mSlots; i++) {
            // draw a bin once per 2 slots
            if (i%2 == 1) {
                float level = sampleLevel(binIndex, mNormalizedLevels);

                // Normalize gain based on histogram
                if (NORMALIZE) {
                    float range = mMaxLevel - mMinLevel;
                    level = (level * 255 - mMinLevel) / range;
                    level = Math.min(level, 1);
                }

                // Scale to pixels
                float gainScaled = level * mHeight /2 * VERTICAL_WAVEFORM_HEIGHT;
                gainScaled = Math.max(gainScaled, minBinHalfHeight);

                float x = i * mBinWidth + mBinWidth / 2;
                canvas.drawLine(x, ctr - gainScaled, x, ctr + gainScaled, mPaint);

                binIndex++;
            }
        }
    }

    private float[] createFilteredLevelsArray() {
        float alpha = 0.7f;
        float[] filteredLevels = new float[mLevels.length];
        filteredLevels [0] = mLevels[0];
        float previousOutput = filteredLevels[0];
        for (int i = 1; i < mLevels.length; i++) {
            filteredLevels[i] = (alpha * mLevels[i]) + (1 - alpha) * previousOutput;
            previousOutput = filteredLevels[i];
        }
        return filteredLevels;
    }

    /**
     * Returns the level on the specified bin index.
     *
     * When the number of levels is no the same as the bins that will be draw, the method returns
     * the nearest level to the one that was requested.
     * @param binIndex the index that specifies where to sample from the levels array
     * @param levels the levels array to sample from
     * @return
     */
    private float sampleLevel(int binIndex, float[] levels) {
        int adjustedIndex = binIndex;
        if (mShouldSample) {
            adjustedIndex = Math.round(((float) adjustedIndex) * mLevels.length / mBins);
            if (adjustedIndex > levels.length - 1) {
                adjustedIndex = levels.length - 1;
            }
        }
        return levels[adjustedIndex];
    }

    private float sampleLevelLinearInterpolation(int binIndex, float[] levels) {
        if (mShouldSample) {
            float adjustedIndex = ((float) binIndex) * mLevels.length / mBins;
            if (adjustedIndex > levels.length - 1) {
                adjustedIndex = levels.length - 1;
            }
            int nearestIndex = Math.round(adjustedIndex);
            int previous;
            int next;
            if (adjustedIndex < nearestIndex) {
                previous = nearestIndex - 1;
                next = nearestIndex;
            }
            else {
                previous = nearestIndex;
                next = nearestIndex + 1;
            }
            float value;
            if (previous >= 0 && next <= levels.length - 1) {
                float a = adjustedIndex - previous;
                value = (1 - a) * levels[previous] + a * levels[next];
            }
            else {
                value = levels[nearestIndex];
            }
            return value;
        }
        else {
            return levels[binIndex];
        }
    }
}
