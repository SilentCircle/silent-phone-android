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
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;

import com.silentcircle.logs.Log;

/**
 * Drag gesture detector.
 *
 * Based on https://developer.android.com/training/gestures/scale.html
 */
public class DragGestureDetector {

    public interface OnDragListener {
        void onDragBegin();

        /**
         * The distance in pixels the the touch moved.
         *
         * Two types of distances are returned. The distance relative to the view and the absolute
         * distance. If the view translates, the first one will be affected, but the second one will
         * not.
         * @param relativeDistanceX relative distance in pixels
         * @param relativeDistanceY relative distance in pixels
         * @param distanceX absolute distance in pixels
         * @param distanceY absolute distance in pixels
         * @param speedX absolute speed in pixels/sec
         * @param speedY absolute speed in pixels/sec
         */
        void onDrag(float relativeDistanceX, float relativeDistanceY, float distanceX, float distanceY, float speedX, float speedY);
        void onDragEnd();
    }

    private Context mContext;
    private OnDragListener mListener;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastRawTouchX;
    private float mLastRawTouchY;
    private float mPosX; // stores the total relative translation to the view
    private float mPosY;
    private float mRawPosX; // stores the total raw translation
    private float mRawPosY;

    private long mLastTimestamp;
    private float mSpeedX;
    private float mSpeedY;

    private boolean mShouldReset;

    private static final float FILTER_VALUE = 0.25f; // lower values are more smooth

    private static final int INVALID_POINTER_ID = -1;
    // The ‘active pointer’ is the one currently moving our object.
    private int mActivePointerId = INVALID_POINTER_ID;

    public DragGestureDetector(Context context, OnDragListener listener) {
        mContext = context;
        mListener = listener;
    }

    public void reset() {
        mPosX = 0;
        mPosY = 0;
        mRawPosX = 0;
        mRawPosY = 0;
        mShouldReset = true;
    }

    public boolean onTouchEvent(MotionEvent ev) {

        final int action = MotionEventCompat.getActionMasked(ev);

        // Calculate the offset that can be used to map the coordinates of touches from the view
        // coordinate system to the root layout coordinate system
        float relativeToRawOffsetX = ev.getRawX() - ev.getX();
        float relativeToRawOffsetY = ev.getRawY() - ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float xRaw = x + relativeToRawOffsetX;
                final float yRaw = y + relativeToRawOffsetY;

                // Remember where we started (for dragging)
                mLastTouchX = x;
                mLastTouchY = y;
                mLastRawTouchX = xRaw;
                mLastRawTouchY = yRaw;

                mSpeedX = 0;
                mSpeedY = 0;
                mLastTimestamp = ev.getEventTime();
                // Save the ID of this pointer (for dragging)
                mActivePointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
                // Reset translation
                mPosX = 0;
                mPosY = 0;
                mRawPosX = 0;
                mRawPosY = 0;

                mListener.onDragBegin();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // Find the index of the active pointer and fetch its position
                final int pointerIndex =
                        MotionEventCompat.findPointerIndex(ev, mActivePointerId);

                if (pointerIndex == -1) {
                    return true;
                }

                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float xRaw = x + relativeToRawOffsetX;
                final float yRaw = y + relativeToRawOffsetY;

                // Calculate the distance moved
                float dx = x - mLastTouchX;
                float dy = y - mLastTouchY;
                float dxRaw = xRaw - mLastRawTouchX;
                float dyRaw = yRaw - mLastRawTouchY;

                if (mShouldReset) {
                    dx = 0;
                    dy = 0;
                    dxRaw = 0;
                    dyRaw = 0;
                    mShouldReset = false;
                }

                mPosX += dx;
                mPosY += dy;
                mRawPosX += dxRaw;
                mRawPosY += dyRaw;

                // Calculate the absolute speed
                long dt = ev.getEventTime() - mLastTimestamp;

                float speedX = (dt == 0) ? 0 : 1000 * (dxRaw) / dt;
                float speedY = (dt == 0) ? 0 : 1000 * (dyRaw) / dt;
                float a = dt / (dt + 1 / FILTER_VALUE);
                mSpeedX = mSpeedX * (1 - a) + speedX * a;
                mSpeedY = mSpeedY * (1 - a) + speedY * a;

                mListener.onDrag(mPosX, mPosY, mRawPosX, mRawPosY, mSpeedX, mSpeedY);

                // Remember this touch position for the next move event
                mLastTouchX = x;
                mLastTouchY = y;
                mLastRawTouchX = xRaw;
                mLastRawTouchY = yRaw;
                mLastTimestamp = ev.getEventTime();

                break;
            }

            case MotionEvent.ACTION_UP: {
                if (mActivePointerId == INVALID_POINTER_ID) {
                    return true;
                }

                mActivePointerId = INVALID_POINTER_ID;
                mListener.onDragEnd();
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mActivePointerId == INVALID_POINTER_ID) {
                    return true;
                }

                mActivePointerId = INVALID_POINTER_ID;
                mListener.onDragEnd();
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                if (mActivePointerId == INVALID_POINTER_ID) {
                    return true;
                }

                final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    // Remember where we started (for dragging)
                    mLastTouchX = MotionEventCompat.getX(ev, newPointerIndex);
                    mLastTouchY = MotionEventCompat.getY(ev, newPointerIndex);
                    mLastRawTouchX = mLastTouchX + relativeToRawOffsetX;
                    mLastRawTouchY = mLastTouchY + relativeToRawOffsetY;
                    mSpeedX = 0;
                    mSpeedY = 0;
                    mLastTimestamp = ev.getEventTime();
                    // Save the ID of this pointer (for dragging)
                    mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

}
