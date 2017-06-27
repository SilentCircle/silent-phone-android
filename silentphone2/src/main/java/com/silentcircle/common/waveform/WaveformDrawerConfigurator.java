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

import static android.util.DisplayMetrics.DENSITY_DEFAULT;

public class WaveformDrawerConfigurator {

    public static class Configuration {
        public int width;   // in pixels
        public int height;  // in pixels
        public int bins;
        public float binWidth; // in pixels
        public int densityDPI;

        public Configuration(int width, int height, int bins, float binWidth, int densityDPI) {
            this.width = width;
            this.height = height;
            this.bins = bins;
            this.binWidth = binWidth;
            this.densityDPI = densityDPI;
        }
    }

    public static final int MIN_BINS = 50;
    public static final int MAX_BINS = 100;
    public static final float BIN_WIDTH_DP = 2.f/3.f; // 2 pixels in xxhdpi
    public static final int HEIGHT_DP = 20;

    /**
     * Creates a configuration for {@link WaveformDrawer} so that the result will have a standard
     * bin width and bins will render in a pixel perfect way.
     *
     * The canvas width of the resulting configuration will be linear on the the number of bins and
     * each bin will have a width of an integer amount of pixels.
     *
     * @param bins the number of bins to render
     * @param density the density of the screen or bitmap to render to
     * @return
     */
    public static Configuration getConfiguration(int bins, int density) {
        float dpRatio = density / DENSITY_DEFAULT;
        int binWidth = Math.round(dpRatio * BIN_WIDTH_DP); // pixel perfect
        binWidth = Math.max(binWidth, 1);

        bins = thresholdBins(bins);

        int width = bins * binWidth * 2;
        int height = (int) (HEIGHT_DP * dpRatio);

        Configuration configuration = new Configuration(width, height, bins, binWidth, density);
        return configuration;
    }

    /**
     * Creates a configuration for {@link WaveformDrawer} so that the result will have a standard
     * bin width and bins will render in a pixel perfect way.
     *
     * The number of bins will depend on the provied canvas width and each bin will have a width of
     * an integer amount of pixels.
     *
     * @param width the canvas width in pixels
     * @param density the density of the screen or bitmap to render to
     * @return
     */
    public static Configuration getConfigurationForWidth(int width, int density) {
        float dpRatio = density / DENSITY_DEFAULT;
        int binWidth = Math.round(dpRatio * BIN_WIDTH_DP); // pixel perfect
        binWidth = Math.max(binWidth, 1);

        int bins = width / (binWidth * 2);
        int height = (int) (HEIGHT_DP * dpRatio);

        Configuration configuration = new Configuration(width, height, bins, binWidth, density);
        return configuration;
    }

    public static int thresholdBins(int bins) {
        int finalBins = bins;
        finalBins = Math.min(MAX_BINS, finalBins);
        finalBins = Math.max(MIN_BINS, finalBins);
        return finalBins;
    }
}