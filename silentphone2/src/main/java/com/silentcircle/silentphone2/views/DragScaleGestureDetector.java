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
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * A gesture detector that fuses the scale and drag gesture detectors.
 *
 * When a stream of touches starts, it devices if it's a drag or a scale gesture and propagates only
 * the detected gesture's listener calls. Some of the first calls of the drag gesture listener  will
 * not be propagated, so we call it manually the
 * {@link DragGestureDetector.OnDragListener#onDragBegin()}.
 */
public class DragScaleGestureDetector {

    private static final long WAIT_TIME_MS = 50;

    public enum Mode {
        None,
        Starting,
        Scaling,
        Dragging
    }

    private Context mContext;
    private Mode mGestureMode = Mode.None;
    private DragGestureDetector mDragGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private DragGestureDetector.OnDragListener mOnDragListenerCallback;
    private ScaleGestureDetector.SimpleOnScaleGestureListener mOnScaleListenerCallback;

    private class MyOnDragListener implements DragGestureDetector.OnDragListener {

        @Override
        public void onDragBegin() {
            if (mGestureMode == Mode.Dragging) {
                mOnDragListenerCallback.onDragBegin();
            }
        }

        @Override
        public void onDrag(float relativeDistanceX, float relativeDistanceY, float distanceX, float distanceY, float speedX, float speedY) {
            if (mGestureMode == Mode.Dragging) {
                mOnDragListenerCallback.onDrag(relativeDistanceX, relativeDistanceY, distanceX, distanceY, speedX, speedY);
            }
        }

        @Override
        public void onDragEnd() {
            if (mGestureMode == Mode.Dragging) {
                mOnDragListenerCallback.onDragEnd();
            }
        }
    }

    private class MyOnScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (mGestureMode == DragScaleGestureDetector.Mode.Scaling) {
                mOnScaleListenerCallback.onScaleBegin(detector);
            }
            return super.onScaleBegin(detector);
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (mGestureMode == Mode.Scaling) {
                mOnScaleListenerCallback.onScale(detector);
            }
            return super.onScale(detector);
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (mGestureMode == Mode.Scaling) {
                mOnScaleListenerCallback.onScaleEnd(detector);
            }
            super.onScaleEnd(detector);
        }
    }


    public DragScaleGestureDetector(Context context, DragGestureDetector.OnDragListener dragListener,
                                    ScaleGestureDetector.SimpleOnScaleGestureListener scaleListener) {
        mContext = context;
        mOnDragListenerCallback = dragListener;
        mOnScaleListenerCallback = scaleListener;

        mDragGestureDetector = new DragGestureDetector(mContext, new MyOnDragListener());
        mScaleGestureDetector = new ScaleGestureDetector(mContext, new MyOnScaleGestureListener());
    }

    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mGestureMode = Mode.Starting;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (mGestureMode == Mode.Starting && mOnScaleListenerCallback != null
                        && (ev.getEventTime() - ev.getDownTime()) < WAIT_TIME_MS) {
                    mGestureMode = Mode.Scaling;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mGestureMode == Mode.Starting && mOnDragListenerCallback != null
                        && (ev.getEventTime() - ev.getDownTime()) > WAIT_TIME_MS) {
                    mGestureMode = Mode.Dragging;
                    mDragGestureDetector.reset();
                    mOnDragListenerCallback.onDragBegin();
                }
                break;

        }

        mScaleGestureDetector.onTouchEvent(ev);
        mDragGestureDetector.onTouchEvent(ev);

        switch ( (ev.getActionMasked())) {
            case MotionEvent.ACTION_UP:
                mGestureMode = Mode.None;
                break;

            case MotionEvent.ACTION_CANCEL:
                mGestureMode = Mode.None;
                break;
        }

        return true;
    }
}
