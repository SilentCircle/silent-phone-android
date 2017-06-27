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
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;

/**
 * A gesture detector that detects a simple tap or a long press. If a long press is detected, the
 * dragging is also detected.
 *
 * In order to register a touch as a long press, it must be either held more than
 * {@link #LONGPRESS_TIMEOUT} or dragged more than {@link #MAX_DRAG_DP}. If the first happens, the
 * dragging accumulated so far is reset, to avoid a sudden movement of the dragged view.
 */
public class LongTapAndDragGestureDetector {

    public interface OnGestureListener {

        void onFirstTapDown();

        void onSingleTapUp();

        void onLongPressDown();

        void onLongPressUp();

        void onLongPressCancelled();

        /**
         * The distance in pixels the touch moved in absolute coordinates (not relative to the
         * view).
         *
         * The distance is NOT relative to the view. If the view translates, the distance will not
         * be affected.
         * @param distanceX absolute distance in pixels
         * @param distanceY absolute distance in pixels
         */
        void onDrag(float distanceX, float distanceY);

    }

    private static final String TAG = LongTapAndDragGestureDetector.class.getSimpleName();

    private static final int LONGPRESS_TIMEOUT = 150;
    private static final int MAX_DRAG_DP = 10;
    // If the touch is translated more than this amount, it is registered as long press
    private final float mMaxDragPixels;
    private final Handler mHandler;
    private final OnGestureListener mListener;

    private DragGestureDetector mDragGestureDetector;
    private DragGestureDetector.OnDragListener mOnDragListener;

    private boolean mInLongPress;

    private class GestureHandler extends Handler {

        static final int LONG_PRESS_TIMEOUT = 1;

        GestureHandler() {
            super();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case LONG_PRESS_TIMEOUT:
                    // Reset the accumulated translation up until this time
                    mDragGestureDetector.reset();
                    dispatchLongPress();
                    break;

                default:
                    throw new RuntimeException("Unknown message " + msg); //never
            }
        }
    }

    private class MyOnDragListener implements DragGestureDetector.OnDragListener {

        @Override
        public void onDragBegin() {}

        @Override
        public void onDrag(float relativeDistanceX, float relativeDistanceY, float distanceX, float distanceY, float speedX, float speedY) {
            if (mInLongPress) {
                mListener.onDrag(distanceX, distanceY);
            }
            else {
                // Register as long press if the touch moved too far
                if (Math.abs(distanceX) > mMaxDragPixels || Math.abs(distanceY) > mMaxDragPixels) {
                    mHandler.removeMessages(GestureHandler.LONG_PRESS_TIMEOUT);
                    dispatchLongPress();
                }
            }
        }

        @Override
        public void onDragEnd() {}
    }

    public LongTapAndDragGestureDetector(Context context, OnGestureListener listener) {
        mHandler = new GestureHandler();
        mListener = listener;
        mDragGestureDetector = new DragGestureDetector(context, new MyOnDragListener());
        mMaxDragPixels = ViewUtil.dp(MAX_DRAG_DP, context);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
//                Log.d(TAG, "down");
                mInLongPress = false;

                mHandler.removeMessages(GestureHandler.LONG_PRESS_TIMEOUT);
                mHandler.sendEmptyMessageAtTime(GestureHandler.LONG_PRESS_TIMEOUT, ev.getDownTime() + LONGPRESS_TIMEOUT);
                mListener.onFirstTapDown();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
//                Log.d(TAG, "move");
                break;
            }

            case MotionEvent.ACTION_UP: {
//                Log.d(TAG, "up");
                if (mInLongPress) {
                    mListener.onLongPressUp();
                }
                else {
                    mListener.onSingleTapUp();
                }

                mHandler.removeMessages(GestureHandler.LONG_PRESS_TIMEOUT);
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
//                Log.d(TAG, "cancel");
                if (mInLongPress) {
                    mListener.onLongPressCancelled();
                }
                mHandler.removeMessages(GestureHandler.LONG_PRESS_TIMEOUT);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
//                Log.d(TAG, "pointer up");
                // We don't care if a pointer was lifted, as long an another one is still down.
                break;
            }
        }

        mDragGestureDetector.onTouchEvent(ev); // this is handled in MyOnDragListener

        return true;
    }

    private void dispatchLongPress() {
        mInLongPress = true;
        mListener.onLongPressDown();
    }
}
