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
package com.silentcircle.messaging.location;

import android.location.Location;

import java.util.Comparator;

public class LocationComparator implements Comparator<Location> {

    public static final long DEFAULT_ACCURACY_WINDOW = 50;
    public static final long DEFAULT_TIME_WINDOW = 10 * 1000;
    private static final int PREVIOUS = -1;
    private static final int CANDIDATE = 1;

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private final long timeWindow;
    private final long accuracyWindow;

    /**
     * Convenience constructor. Equivalent to calling
     * {@link LocationComparator#LocationComparator(long, long)} with {@link #DEFAULT_TIME_WINDOW}
     * and {@link #DEFAULT_ACCURACY_WINDOW}.
     */
    public LocationComparator() {
        this(DEFAULT_TIME_WINDOW, DEFAULT_ACCURACY_WINDOW);
    }

    /**
     * @param timeWindow     How much time, in milliseconds, would be considered a significant difference
     *                       between location fixes.
     * @param accuracyWindow What distance, in meters, would be considered a significant difference in accuracy
     *                       between location fixes.
     */
    public LocationComparator(long timeWindow, long accuracyWindow) {
        this.timeWindow = timeWindow;
        this.accuracyWindow = accuracyWindow;
    }

    @Override
    public int compare(Location previous, Location candidate) {

        if (candidate == null) {
            return PREVIOUS;
        }

        if (previous == null) {
            return CANDIDATE;
        }

        long dt = candidate.getTime() - previous.getTime();

        boolean isNewer = dt > 0;
        boolean isSignificantlyNewer = dt > timeWindow;
        boolean isSignificantlyOlder = dt < -timeWindow;

        float da = candidate.getAccuracy() - previous.getAccuracy();

        boolean isMoreAccurate = da < 0;
        boolean isSignificantlyLessAccurate = da > accuracyWindow;

        if (isSignificantlyNewer) {
            return CANDIDATE;
        }

        if (!isSignificantlyOlder) {

            if (isMoreAccurate) {
                return CANDIDATE;
            }

            if (isNewer) {

                if (!isSignificantlyLessAccurate) {
                    return CANDIDATE;
                }

                if (equals(previous.getProvider(), candidate.getProvider())) {
                    return CANDIDATE;
                }

            }

        }

        return PREVIOUS;

    }

}

