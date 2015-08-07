/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.common.interactions;

import android.graphics.Point;

/**
 * Singleton class to keep track of where the user last touched the screen.
 *
 * Used to pass on to the InCallUI for animation.
 */
public class TouchPointManager {
    public static final String TOUCH_POINT = "touchPoint";

    private static TouchPointManager sInstance = new TouchPointManager();

    private Point mPoint = new Point();

    /**
     * Private constructor.  Instance should only be acquired through getInstance().
     */
    private TouchPointManager() {
    }

    public static TouchPointManager getInstance() {
        return sInstance;
    }

    public Point getPoint() {
        return mPoint;
    }

    public void setPoint(int x, int y) {
        mPoint.set(x, y);
    }

    /**
     * When a point is initialized, its value is (0,0). Since it is highly unlikely a user will
     * touch at that exact point, if the point in TouchPointManager is (0,0), it is safe to assume
     * that the TouchPointManager has not yet collected a touch.
     *
     * @return True if there is a valid point saved. Define a valid point as any point that is
     * not (0,0).
     */
    public boolean hasValidPoint() {
        return mPoint.x != 0 || mPoint.y != 0;
    }
}
