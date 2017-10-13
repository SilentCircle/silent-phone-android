package com.silentcircle.messaging.views;

/**
 The MIT License (MIT)

 Copyright (c) 2016 Chau Thai

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.R;

@SuppressLint("RtlHardcoded")
public class SwipeRevealLayout extends ViewGroup {

    private static final String TAG = SwipeRevealLayout.class.getSimpleName();
    private static final boolean DEBUG = false;       // Don't submit with true

    // These states are used only for ViewBindHelper
    protected static final int STATE_CLOSE     = 0;
    protected static final int STATE_CLOSING   = 1;
    protected static final int STATE_OPEN      = 2;
    protected static final int STATE_OPENING   = 3;
    protected static final int STATE_DRAGGING  = 4;

    private static final int DEFAULT_MIN_FLING_VELOCITY = 500; // dp per second

    public static final int DRAG_EDGE_LEFT =   0x1;
    public static final int DRAG_EDGE_RIGHT =  0x1 << 1;
    public static final int DRAG_EDGE_TOP =    0x1 << 2;
    public static final int DRAG_EDGE_BOTTOM = 0x1 << 3;
    public static final int DRAG_EDGE_LEFTRIGHT = 0x1 << 4;

    /**
     * The secondary view will be under the main view.
     */
    public static final int MODE_NORMAL = 0;

    /**
     * The secondary view will stick the edge of the main view.
     */
    public static final int MODE_SAME_LEVEL = 1;

    /**
     * Threshold that has to be reached before view dragging starts.
     */
    public static final int DRAG_THRESHOLD = 15;

    /**
     * Main view is the view which is shown when the layout is closed.
     */
    private View mMainView;

    /**
     * Secondary view is the view which is shown when the layout is opened.
     */
    private View mSecondaryViewLeft;

    /**
     *
     */
    private View mSecondaryViewRight;

    /**
     * The rectangle position of the main view when the layout is closed.
     */
    private Rect mRectMainClose = new Rect();

    /**
     * The rectangle position of the main view when the layout is opened.
     */
    private Rect mRectMainLeftOpen  = new Rect();
    private Rect mRectMainRightOpen  = new Rect();

    /**
     * The rectangle position of the secondary view when the layout is closed.
     */
    private Rect mRectSecLeftClose  = new Rect();
    private Rect mRectSecRightClose  = new Rect();

    /**
     * The rectangle position of the secondary view when the layout is opened.
     */
    private Rect mRectSecLeftOpen = new Rect();
    private Rect mRectSecRightOpen = new Rect();

    private boolean mSectLeftEnabled = true;
    private boolean mSectRightEnabled = true;

    private boolean mIsOpenBeforeInit = false;
    private volatile boolean mAborted = false;
    private volatile boolean mIsScrolling = false;
    private volatile boolean mLockDrag = false;

    private int mMinFlingVelocity = DEFAULT_MIN_FLING_VELOCITY;
    private int mState = STATE_CLOSE;
    private int mMode = MODE_NORMAL;

    private int mLastMainLeft = 0;
    private int mLastMainTop  = 0;

    private int mDragEdge = DRAG_EDGE_LEFT;

    private ViewDragHelper mDragHelper;
    private GestureDetectorCompat mGestureDetector;

    private DragStateChangeListener mDragStateChangeListener; // only used for ViewBindHelper
    private SwipeListener mSwipeListener;

    public interface DragStateChangeListener {
        void onDragStateChanged(int state);
    }

    /**
     * Listener for monitoring events about swipe layout.
     */
    public interface SwipeListener {
        /**
         * Called when the main view becomes completely closed.
         */
        void onClosed(SwipeRevealLayout view);

        /**
         * Called when the main view becomes completely opened.
         */
        void onOpened(SwipeRevealLayout view);

        /**
         * Called when the main view's position changes.
         * @param slideOffset The new offset of the main view within its range, from 0-1
         */
        void onSlide(SwipeRevealLayout view, float slideOffset);
    }

    /**
     * No-op stub for {@link SwipeListener}. If you only want ot implement a subset
     * of the listener methods, you can extend this instead of implement the full interface.
     */
    public static class SimpleSwipeListener implements SwipeListener {
        @Override
        public void onClosed(SwipeRevealLayout view) {}

        @Override
        public void onOpened(SwipeRevealLayout view) {}

        @Override
        public void onSlide(SwipeRevealLayout view, float slideOffset) {}
    }

    public SwipeRevealLayout(Context context) {
        super(context);
        init(context, null);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        mDragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mDragHelper.processTouchEvent(ev);
        mGestureDetector.onTouchEvent(ev);

        boolean settling = mDragHelper.getViewDragState() == ViewDragHelper.STATE_SETTLING;
        boolean idleAfterScrolled = mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE
                && mIsScrolling;

        return settling || idleAfterScrolled;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // get views
        if (getChildCount() >= 3) {
            mSecondaryViewLeft = getChildAt(0);
            mMainView = getChildAt(1);
            mSecondaryViewRight = getChildAt(2);
            if (DEBUG) {
                Log.d(TAG, "mSecondaryViewLeft " + mSecondaryViewLeft);
                Log.d(TAG, "mMainView " + mMainView);
                Log.d(TAG, "mSecondaryViewRight " + mSecondaryViewRight);
            }
        }
        else if (getChildCount() >= 2) {
            mSecondaryViewLeft = getChildAt(0);
            mSecondaryViewRight = getChildAt(0);
            mMainView = getChildAt(1);
        }
        else if (getChildCount() == 1) {
            mMainView = getChildAt(0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mAborted = false;

        for (int index = 0; index < getChildCount(); index++) {
            final View child = getChildAt(index);

            int left, right, top, bottom;
            left = right = top = bottom = 0;

            final int minLeft = getPaddingLeft();
            final int maxRight = Math.max(r - getPaddingRight() - l, 0);
            final int minTop = getPaddingTop();
            final int maxBottom = Math.max(b - getPaddingBottom() - t, 0);

            switch (mDragEdge) {
                case DRAG_EDGE_RIGHT:
                    left    = Math.max(r - child.getMeasuredWidth() - getPaddingRight() - l, minLeft);
                    top     = Math.min(getPaddingTop(), maxBottom);
                    right   = Math.max(r - getPaddingRight() - l, minLeft);
                    bottom  = Math.min(child.getMeasuredHeight() + getPaddingTop(), maxBottom);
                    break;

                case DRAG_EDGE_LEFT:
                    left    = Math.min(getPaddingLeft(), maxRight);
                    top     = Math.min(getPaddingTop(), maxBottom);
                    right   = Math.min(child.getMeasuredWidth() + getPaddingLeft(), maxRight);
                    bottom  = Math.min(child.getMeasuredHeight() + getPaddingTop(), maxBottom);
                    break;

                case DRAG_EDGE_LEFTRIGHT:
                    if (index == 0 || index == 1) {
                        left = Math.min(getPaddingLeft(), maxRight);
                        top = Math.min(getPaddingTop(), maxBottom);
                        right = Math.min(child.getMeasuredWidth() + getPaddingLeft(), maxRight);
                        bottom = Math.min(child.getMeasuredHeight() + getPaddingTop(), maxBottom);
                    } else {
                        left    = Math.max(r - child.getMeasuredWidth() - getPaddingRight() - l, minLeft);
                        top     = Math.min(getPaddingTop(), maxBottom);
                        right   = Math.max(r - getPaddingRight() - l, minLeft);
                        bottom  = Math.min(child.getMeasuredHeight() + getPaddingTop(), maxBottom);
                    }
                    break;

                case DRAG_EDGE_TOP:
                    left    = Math.min(getPaddingLeft(), maxRight);
                    top     = Math.min(getPaddingTop(), maxBottom);
                    right   = Math.min(child.getMeasuredWidth() + getPaddingLeft(), maxRight);
                    bottom  = Math.min(child.getMeasuredHeight() + getPaddingTop(), maxBottom);
                    break;

                case DRAG_EDGE_BOTTOM:
                    left    = Math.min(getPaddingLeft(), maxRight);
                    top     = Math.max(b - child.getMeasuredHeight() - getPaddingBottom() - t, minTop);
                    right   = Math.min(child.getMeasuredWidth() + getPaddingLeft(), maxRight);
                    bottom  = Math.max(b - getPaddingBottom() - t, minTop);
                    break;
            }

            if (DEBUG) Log.d(TAG, "onlayout " + child + " " + left + " " + top + " " + right + " " + bottom);
            child.layout(left, top, right, bottom);
        }

        // taking account offset when mode is SAME_LEVEL
        if (mMode == MODE_SAME_LEVEL) {
            switch (mDragEdge) {
                case DRAG_EDGE_LEFT:
                    mSecondaryViewLeft.offsetLeftAndRight(-mSecondaryViewLeft.getWidth());
                    break;

                case DRAG_EDGE_RIGHT:
                    mSecondaryViewRight.offsetLeftAndRight(mSecondaryViewRight.getWidth());
                    break;

                case DRAG_EDGE_LEFTRIGHT:
                    mSecondaryViewLeft.offsetLeftAndRight(-mSecondaryViewLeft.getWidth());
                    mSecondaryViewRight.offsetLeftAndRight(mSecondaryViewRight.getWidth());
                    break;

                case DRAG_EDGE_TOP:
                    mSecondaryViewLeft.offsetTopAndBottom(-mSecondaryViewLeft.getHeight());
                    break;

                case DRAG_EDGE_BOTTOM:
                    mSecondaryViewLeft.offsetTopAndBottom(mSecondaryViewLeft.getHeight());
            }
        }

        initRects();

        if (mIsOpenBeforeInit) {
            open(false);
        } else {
            close(false);
        }

        mLastMainLeft = mMainView.getLeft();
        mLastMainTop = mMainView.getTop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() < 2) {
            throw new RuntimeException("Layout must have two children");
        }

        final LayoutParams params = getLayoutParams();

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        final int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);

        int desiredWidth = 0;
        int desiredHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            final LayoutParams childParams = child.getLayoutParams();

            if (childParams != null) {
                if (childParams.height == LayoutParams.MATCH_PARENT) {
                    child.setMinimumHeight(measuredHeight);
                }

                if (childParams.width == LayoutParams.MATCH_PARENT) {
                    child.setMinimumWidth(measuredWidth);
                }
            }

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            desiredWidth = Math.max(child.getMeasuredWidth(), desiredWidth);
            desiredHeight = Math.max(child.getMeasuredHeight(), desiredHeight);
        }

        // taking accounts of padding
        desiredWidth += getPaddingLeft() + getPaddingRight();
        desiredHeight += getPaddingTop() + getPaddingBottom();

        // adjust desired width
        if (widthMode == MeasureSpec.EXACTLY) {
            desiredWidth = measuredWidth;
        } else {
            if (params.width == LayoutParams.MATCH_PARENT) {
                desiredWidth = measuredWidth;
            }

            if (widthMode == MeasureSpec.AT_MOST) {
                desiredWidth = (desiredWidth > measuredWidth)? measuredWidth : desiredWidth;
            }
        }

        // adjust desired height
        if (heightMode == MeasureSpec.EXACTLY) {
            desiredHeight = measuredHeight;
        } else {
            if (params.height == LayoutParams.MATCH_PARENT) {
                desiredHeight = measuredHeight;
            }

            if (heightMode == MeasureSpec.AT_MOST) {
                desiredHeight = (desiredHeight > measuredHeight)? measuredHeight : desiredHeight;
            }
        }

        setMeasuredDimension(desiredWidth, desiredHeight);
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public void setSectRightEnabled(boolean sectRightEnabled) {
        mSectRightEnabled = sectRightEnabled;
    }

    public void setSectLeftEnabled(boolean sectLeftEnabled) {
        mSectLeftEnabled = sectLeftEnabled;
    }

    /**
     * Open the panel to show the secondary view
     * @param animation true to animate the open motion. {@link SwipeListener} won't be
     *                  called if is animation is false.
     */
    public void open(boolean animation) {
        if (DEBUG) Log.d(TAG, "open " + animation);
        mIsOpenBeforeInit = true;
        mAborted = false;

        if (animation) {
            mState = STATE_OPENING;
            mDragHelper.smoothSlideViewTo(mMainView, mRectMainLeftOpen.left, mRectMainLeftOpen.top);

            if (mDragStateChangeListener != null) {
                mDragStateChangeListener.onDragStateChanged(mState);
            }
        } else {
            mState = STATE_OPEN;
            mDragHelper.abort();

            mMainView.layout(
                    mRectMainLeftOpen.left,
                    mRectMainLeftOpen.top,
                    mRectMainLeftOpen.right,
                    mRectMainLeftOpen.bottom
            );

            mSecondaryViewLeft.layout(
                    mRectSecLeftOpen.left,
                    mRectSecLeftOpen.top,
                    mRectSecLeftOpen.right,
                    mRectSecLeftOpen.bottom
            );

            mSecondaryViewRight.layout(
                    mRectSecRightOpen.left,
                    mRectSecRightOpen.top,
                    mRectSecRightOpen.right,
                    mRectSecRightOpen.bottom
            );
        }

        ViewCompat.postInvalidateOnAnimation(SwipeRevealLayout.this);
    }

    public void open(boolean animation, boolean openLeft) {
        if (DEBUG) Log.d(TAG, "open " + animation + " " + openLeft);
        mIsOpenBeforeInit = true;
        mAborted = false;

        if (animation) {
            mState = STATE_OPENING;
            Rect openRect = openLeft ? mRectMainLeftOpen : mRectMainRightOpen;
            mDragHelper.smoothSlideViewTo(mMainView, openRect.left, openRect.top);

            if (mDragStateChangeListener != null) {
                mDragStateChangeListener.onDragStateChanged(mState);
            }
        } else {
            mState = STATE_OPEN;
            mDragHelper.abort();

            Rect openRect = openLeft ? mRectMainLeftOpen : mRectMainRightOpen;
            mMainView.layout(
                    openRect.left,
                    openRect.top,
                    openRect.right,
                    openRect.bottom
            );

            if (openLeft) {
                mSecondaryViewLeft.layout(
                        mRectSecLeftOpen.left,
                        mRectSecLeftOpen.top,
                        mRectSecLeftOpen.right,
                        mRectSecLeftOpen.bottom
                );
            }
            else {
                mSecondaryViewRight.layout(
                        mRectSecRightOpen.left,
                        mRectSecRightOpen.top,
                        mRectSecRightOpen.right,
                        mRectSecRightOpen.bottom
                );
            }
        }

        ViewCompat.postInvalidateOnAnimation(SwipeRevealLayout.this);
    }

    /**
     * Close the panel to hide the secondary view
     * @param animation true to animate the close motion. {@link SwipeListener} won't be
     *                  called if is animation is false.
     */
    public void close(boolean animation) {
        if (DEBUG) Log.d(TAG, "close " + animation);
        mIsOpenBeforeInit = false;
        mAborted = false;

        if (animation) {
            mState = STATE_CLOSING;
            mDragHelper.smoothSlideViewTo(mMainView, mRectMainClose.left, mRectMainClose.top);

            if (mDragStateChangeListener != null) {
                mDragStateChangeListener.onDragStateChanged(mState);
            }

        } else {
            mState = STATE_CLOSE;
            mDragHelper.abort();

            mMainView.layout(
                    mRectMainClose.left,
                    mRectMainClose.top,
                    mRectMainClose.right,
                    mRectMainClose.bottom
            );

            mSecondaryViewLeft.layout(
                    mRectSecLeftClose.left,
                    mRectSecLeftClose.top,
                    mRectSecLeftClose.right,
                    mRectSecLeftClose.bottom
            );

            mSecondaryViewRight.layout(
                    mRectSecRightClose.left,
                    mRectSecRightClose.top,
                    mRectSecRightClose.right,
                    mRectSecRightClose.bottom
            );

            invalidate();
        }

        ViewCompat.postInvalidateOnAnimation(SwipeRevealLayout.this);
    }

    /**
     * Set the minimum fling velocity to cause the layout to open/close.
     * @param velocity dp per second
     */
    public void setMinFlingVelocity(int velocity) {
        mMinFlingVelocity = velocity;
    }

    /**
     * Get the minimum fling velocity to cause the layout to open/close.
     * @return dp per second
     */
    public int getMinFlingVelocity() {
        return mMinFlingVelocity;
    }

    /**
     * Set the edge where the layout can be dragged from.
     * @param dragEdge Can be one of these
     *                 <ul>
     *                      <li>{@link #DRAG_EDGE_LEFT}</li>
     *                      <li>{@link #DRAG_EDGE_TOP}</li>
     *                      <li>{@link #DRAG_EDGE_RIGHT}</li>
     *                      <li>{@link #DRAG_EDGE_BOTTOM}</li>
     *                 </ul>
     */
    public void setDragEdge(int dragEdge) {
        mDragEdge = dragEdge;
    }

    /**
     * Get the edge where the layout can be dragged from.
     * @return Can be one of these
     *                 <ul>
     *                      <li>{@link #DRAG_EDGE_LEFT}</li>
     *                      <li>{@link #DRAG_EDGE_TOP}</li>
     *                      <li>{@link #DRAG_EDGE_RIGHT}</li>
     *                      <li>{@link #DRAG_EDGE_BOTTOM}</li>
     *                 </ul>
     */
    public int getDragEdge() {
        return mDragEdge;
    }

    public void setSwipeListener(SwipeListener listener) {
        mSwipeListener = listener;
    }

    /**
     * @param lock if set to true, the user cannot drag/swipe the layout.
     */
    public void setLockDrag(boolean lock) {
        mLockDrag = lock;
    }

    /**
     * @return true if the drag/swipe motion is currently locked.
     */
    public boolean isDragLocked() {
        return mLockDrag;
    }

    /**
     * @return true if layout is fully opened, false otherwise.
     */
    public boolean isOpened() {
        return (mState == STATE_OPEN);
    }

    /**
     * @return true if layout is fully closed, false otherwise.
     */
    public boolean isClosed() {
        return (mState == STATE_CLOSE);
    }

    /** Only used for {@link ViewBinderHelper} */
    void setDragStateChangeListener(DragStateChangeListener listener) {
        mDragStateChangeListener = listener;
    }

    /** Abort current motion in progress. Only used for {@link ViewBinderHelper} */
    protected void abort() {
        mAborted = true;
        mDragHelper.abort();

        if (mDragStateChangeListener != null) {
            mDragStateChangeListener.onDragStateChanged(STATE_CLOSE);
        }
    }

    private int getMainOpenLeft() {
        if (DEBUG) Log.d(TAG, "getMainOpenLeft");
        switch (mDragEdge) {
            case DRAG_EDGE_LEFT:
                return mRectMainClose.left + mSecondaryViewLeft.getWidth();

            case DRAG_EDGE_RIGHT:
                return mRectMainClose.left - mSecondaryViewRight.getWidth();

            case DRAG_EDGE_LEFTRIGHT:
                return mRectMainClose.left + mSecondaryViewLeft.getWidth();

            case DRAG_EDGE_TOP:
                return mRectMainClose.left;

            case DRAG_EDGE_BOTTOM:
                return mRectMainClose.left;

            default:
                return 0;
        }
    }

    private int getMainOpenRight() {
        if (DEBUG) Log.d(TAG, "getMainOpenLeft");
        switch (mDragEdge) {
            case DRAG_EDGE_LEFT:
                return mRectMainClose.left + mSecondaryViewLeft.getWidth();

            case DRAG_EDGE_RIGHT:
                return mRectMainClose.left - mSecondaryViewRight.getWidth();

            case DRAG_EDGE_LEFTRIGHT:
                return mRectMainClose.left - mSecondaryViewRight.getWidth();

            case DRAG_EDGE_TOP:
                return mRectMainClose.left;

            case DRAG_EDGE_BOTTOM:
                return mRectMainClose.left;

            default:
                return 0;
        }
    }

    private int getMainOpenTop() {
        if (DEBUG) Log.d(TAG, "getMainOpenTop");
        switch (mDragEdge) {
            case DRAG_EDGE_LEFT:
                return mRectMainClose.top;

            case DRAG_EDGE_RIGHT:
                return mRectMainClose.top;

            case DRAG_EDGE_LEFTRIGHT:
                return mRectMainClose.top;

            case DRAG_EDGE_TOP:
                return mRectMainClose.top + mSecondaryViewLeft.getHeight();

            case DRAG_EDGE_BOTTOM:
                return mRectMainClose.top - mSecondaryViewLeft.getHeight();

            default:
                return 0;
        }
    }

    private int getSecOpenLeft() {
        if (DEBUG) Log.d(TAG, "getSecOpenLeft");
        if (mMode == MODE_NORMAL || mDragEdge == DRAG_EDGE_BOTTOM || mDragEdge == DRAG_EDGE_TOP) {
            return mRectSecLeftClose.left;
        }

        if (mDragEdge == DRAG_EDGE_LEFT) {
            return mRectSecLeftClose.left + mSecondaryViewLeft.getWidth();
        } else if (mDragEdge == DRAG_EDGE_RIGHT) {
            return mRectSecLeftClose.left - mSecondaryViewRight.getWidth();
        } else if (mDragEdge == DRAG_EDGE_LEFTRIGHT) {
            return mRectSecLeftClose.left + mSecondaryViewLeft.getWidth();
        } else {
            return 0;
        }
    }

    private int getSecOpenRight() {
        if (DEBUG) Log.d(TAG, "getSecOpenRight");
        if (mDragEdge == DRAG_EDGE_LEFT) {
            return mRectSecLeftClose.left + mSecondaryViewLeft.getWidth();
        } else if (mDragEdge == DRAG_EDGE_RIGHT) {
            return mRectSecLeftClose.left - mSecondaryViewRight.getWidth();
        } else if (mDragEdge == DRAG_EDGE_LEFTRIGHT) {
            return mRectSecRightClose.left - mSecondaryViewRight.getWidth();
        } else {
            return 0;
        }
    }

    private int getSecOpenTop() {
        if (DEBUG) Log.d(TAG, "getSecOpenTop");
        if (mMode == MODE_NORMAL || mDragEdge == DRAG_EDGE_LEFT || mDragEdge == DRAG_EDGE_RIGHT || mDragEdge == DRAG_EDGE_LEFTRIGHT) {
            return mRectSecLeftClose.top;
        }

        if (mDragEdge == DRAG_EDGE_TOP) {
            return mRectSecLeftClose.top + mSecondaryViewLeft.getHeight();
        } else {
            return mRectSecLeftClose.top - mSecondaryViewLeft.getHeight();
        }
    }

    private void initRects() {
        // close position of main view
        mRectMainClose.set(
                mMainView.getLeft(),
                mMainView.getTop(),
                mMainView.getRight(),
                mMainView.getBottom()
        );

        // close position of secondary view
        mRectSecLeftClose.set(
                mSecondaryViewLeft.getLeft(),
                mSecondaryViewLeft.getTop(),
                mSecondaryViewLeft.getRight(),
                mSecondaryViewLeft.getBottom()
        );

        // close position of secondary view
        mRectSecRightClose.set(
                mSecondaryViewRight.getLeft(),
                mSecondaryViewRight.getTop(),
                mSecondaryViewRight.getRight(),
                mSecondaryViewRight.getBottom()
        );

        // open position of the main view
        mRectMainLeftOpen.set(
                getMainOpenLeft(),
                getMainOpenTop(),
                getMainOpenLeft() + mMainView.getWidth(),
                getMainOpenTop() + mMainView.getHeight()
        );

        mRectMainRightOpen.set(
                getMainOpenRight(),
                getMainOpenTop(),
                getMainOpenRight() + mMainView.getWidth(),
                getMainOpenTop() + mMainView.getHeight()
        );

        // open position of the secondary view
        mRectSecLeftOpen.set(
                getSecOpenLeft(),
                getSecOpenTop(),
                getSecOpenLeft() + mSecondaryViewLeft.getWidth(),
                getSecOpenTop() + mSecondaryViewLeft.getHeight()
        );

        mRectSecRightOpen.set(
                getSecOpenRight(),
                getSecOpenTop(),
                getSecOpenRight() + mSecondaryViewRight.getWidth(),
                getSecOpenTop() + mSecondaryViewRight.getHeight()
        );

        if (DEBUG) {
            Log.d(TAG, "mRectMainClose " + mRectMainClose.left + " " + mRectMainClose.top + " " + mRectMainClose.right + " " + mRectMainClose.bottom);
            Log.d(TAG, "mRectSecLeftClose " + mRectSecLeftClose.left + " " + mRectSecLeftClose.top + " " + mRectSecLeftClose.right + " " + mRectSecLeftClose.bottom);
            Log.d(TAG, "mRectSecRightClose " + mRectSecRightClose.left + " " + mRectSecRightClose.top + " " + mRectSecRightClose.right + " " + mRectSecRightClose.bottom);

            Log.d(TAG, "mRectMainLeftOpen " + mRectMainLeftOpen.left + " " + mRectMainLeftOpen.top + " " + mRectMainLeftOpen.right + " " + mRectMainLeftOpen.bottom);
            Log.d(TAG, "mRectMainRightOpen " + mRectMainRightOpen.left + " " + mRectMainRightOpen.top + " " + mRectMainRightOpen.right + " " + mRectMainRightOpen.bottom);
            Log.d(TAG, "mRectSecLeftOpen " + mRectSecLeftOpen.left + " " + mRectSecLeftOpen.top + " " + mRectSecLeftOpen.right + " " + mRectSecLeftOpen.bottom);
            Log.d(TAG, "mRectSecRightOpen " + mRectSecRightOpen.left + " " + mRectSecRightOpen.top + " " + mRectSecRightOpen.right + " " + mRectSecRightOpen.bottom);
        }
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null && context != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.SwipeRevealLayout,
                    0, 0
            );

            mDragEdge = a.getInteger(R.styleable.SwipeRevealLayout_dragEdge, DRAG_EDGE_LEFT);
            mMinFlingVelocity = a.getInteger(R.styleable.SwipeRevealLayout_flingVelocity, DEFAULT_MIN_FLING_VELOCITY);
            mMode = a.getInteger(R.styleable.SwipeRevealLayout_mode, MODE_NORMAL);
        }

        mDragHelper = ViewDragHelper.create(this, 1.0f, mDragHelperCallback);
        mDragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_ALL);

        mGestureDetector = new GestureDetectorCompat(context, mGestureListener);
    }

    private final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            mIsScrolling = false;
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mIsScrolling = true;
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mIsScrolling = true;

            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }

            return false;
        }
    };

    private int getHalfwayPivotHorizontal() {
        if (DEBUG) Log.d(TAG, "getHalfwayPivotHorizontal");
        if (mDragEdge == DRAG_EDGE_LEFT) {
            return mRectMainClose.left + mSecondaryViewLeft.getWidth() / 2;
        } else if (mDragEdge == DRAG_EDGE_RIGHT) {
            return mRectMainClose.right - mSecondaryViewRight.getWidth() / 2;
        } else {
            return 0;
        }
    }

    private int getHalfwayPivotVertical() {
        if (DEBUG) Log.d(TAG, "getHalfwayPivotVertical");
        if (mDragEdge == DRAG_EDGE_TOP) {
            return mRectMainClose.top + mSecondaryViewLeft.getHeight() / 2;
        } else {
            return mRectMainClose.bottom - mSecondaryViewLeft.getHeight() / 2;
        }
    }

    private final ViewDragHelper.Callback mDragHelperCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            mAborted = false;

            if (mLockDrag)
                return false;

            mDragHelper.captureChildView(mMainView, pointerId);
            return false;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            if (DEBUG) Log.d(TAG, "clampViewPositionVertical");
            switch (mDragEdge) {
                case DRAG_EDGE_TOP:
                    return Math.max(
                            Math.min(top, mRectMainClose.top + mSecondaryViewLeft.getHeight()),
                            mRectMainClose.top
                    );

                case DRAG_EDGE_BOTTOM:
                    return Math.max(
                            Math.min(top, mRectMainClose.top),
                            mRectMainClose.top - mSecondaryViewLeft.getHeight()
                    );

                default:
                    return child.getTop();
            }
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            if (DEBUG) {
                Log.d(TAG, "clampViewPositionHorizontal " + child + " " + child.getId());
                Log.d(TAG, "clampViewPositionHorizontal left: " + left + " dx: " + dx + " " + getStateString(mState));
            }
            switch (mDragEdge) {
                case DRAG_EDGE_RIGHT:
                    return Math.max(
                            Math.min(left, mRectMainClose.left),
                            mRectMainClose.left - mSecondaryViewLeft.getWidth()
                    );

                case DRAG_EDGE_LEFT:
                    if (mSectLeftEnabled) {
                        if (Math.abs(Math.abs(mRectMainClose.left) - Math.abs(left)) < DRAG_THRESHOLD
                                && Math.abs(dx) < DRAG_THRESHOLD) {
                            return child.getLeft();
                        }
                        return Math.max(
                                Math.min(left, mRectMainClose.left + mSecondaryViewRight.getWidth()),
                                mRectMainClose.left
                        );
                    }
                    else {
                        return child.getLeft();
                    }

                case DRAG_EDGE_LEFTRIGHT:
                    if (Math.abs(Math.abs(mRectMainClose.left) - Math.abs(left)) < DRAG_THRESHOLD
                            && Math.abs(dx) < DRAG_THRESHOLD) {
                        return child.getLeft();
                    }
                    if (left > 0 && mSectLeftEnabled) {
                        return Math.max(
                                Math.min(left, mRectMainClose.left + mSecondaryViewLeft.getWidth()),
                                mRectMainClose.left);
                    } else if (mSectRightEnabled) {
                        return Math.max(
                                Math.min(left, mRectMainClose.left),
                                mRectMainClose.left - mSecondaryViewLeft.getWidth());
                    }

                default:
                    return child.getLeft();
            }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            if (DEBUG) Log.d(TAG, "onViewReleased " + xvel + " " + yvel);
            final boolean velRightExceeded =  pxToDp((int) xvel) >= mMinFlingVelocity;
            final boolean velLeftExceeded =   pxToDp((int) xvel) <= -mMinFlingVelocity;
            final boolean velUpExceeded =     pxToDp((int) yvel) <= -mMinFlingVelocity;
            final boolean velDownExceeded =   pxToDp((int) yvel) >= mMinFlingVelocity;
            if (DEBUG) Log.d(TAG, "onViewReleased velLeftExceeded " + velLeftExceeded);
            if (DEBUG) Log.d(TAG, "onViewReleased velRightExceeded " + velRightExceeded);

            final int pivotHorizontal = getHalfwayPivotHorizontal();
            final int pivotVertical = getHalfwayPivotVertical();

            switch (mDragEdge) {
                case DRAG_EDGE_RIGHT:
                    if (velRightExceeded) {
                        close(true);
                    } else if (velLeftExceeded) {
                        open(true);
                    } else {
                        if (mMainView.getRight() < pivotHorizontal) {
                            open(true);
                        } else {
                            close(true);
                        }
                    }
                    break;

                case DRAG_EDGE_LEFT:
                    if (velRightExceeded && mSectLeftEnabled) {
                        open(true);
                    } else if (velLeftExceeded) {
                        close(true);
                    } else {
                        if (mMainView.getLeft() < pivotHorizontal) {
                            close(true);
                        } else if (mSectLeftEnabled) {
                            open(true);
                        }
                    }
                    break;

                case DRAG_EDGE_LEFTRIGHT:
                    if (velRightExceeded) {
                        if (xvel > 0 && mSectLeftEnabled) {
                            open(true, true);
                        }
                    } else if (velLeftExceeded) {
                        if (xvel < 0 && mSectRightEnabled) {
                            open(true, false);
                        }
                    } else {
                        if (mMainView.getLeft() > (mRectMainClose.left + mSecondaryViewLeft.getWidth() / 2)
                                && mSectLeftEnabled) {
                            open(true, true);
                        } else if (mMainView.getRight() < (mRectMainClose.right - mSecondaryViewRight.getWidth() / 2)
                                && mSectRightEnabled) {
                            open(true, false);
                        } else if (mMainView.getLeft() > mRectMainClose.left && (mMainView.getLeft() < (mRectMainClose.left + mSecondaryViewLeft.getWidth() / 2))) {
                            close(true);
                        } else if (mMainView.getRight() < mRectMainClose.right && (mMainView.getRight() > (mRectMainClose.right - mSecondaryViewRight.getWidth() / 2))) {
                            close(true);
                        } else {
                            close(true);
                        }
                    }
                    break;

                case DRAG_EDGE_TOP:
                    if (velUpExceeded) {
                        close(true);
                    } else if (velDownExceeded) {
                        open(true);
                    } else {
                        if (mMainView.getTop() < pivotVertical) {
                            close(true);
                        } else {
                            open(true);
                        }
                    }
                    break;

                case DRAG_EDGE_BOTTOM:
                    if (velUpExceeded) {
                        open(true);
                    } else if (velDownExceeded) {
                        close(true);
                    } else {
                        if (mMainView.getBottom() < pivotVertical) {
                            open(true);
                        } else {
                            close(true);
                        }
                    }
                    break;
            }
        }

        @Override
        public void onEdgeDragStarted(int edgeFlags, int pointerId) {
            if (DEBUG) Log.d(TAG, "onEdgeDragStarted");
            super.onEdgeDragStarted(edgeFlags, pointerId);

            if (mLockDrag) {
                return;
            }

            boolean edgeStartLeft = (mDragEdge == DRAG_EDGE_RIGHT)
                    && edgeFlags == ViewDragHelper.EDGE_LEFT;

            boolean edgeStartRight = (mDragEdge == DRAG_EDGE_LEFT)
                    && edgeFlags == ViewDragHelper.EDGE_RIGHT;

            boolean edgeStartTop = (mDragEdge == DRAG_EDGE_BOTTOM)
                    && edgeFlags == ViewDragHelper.EDGE_TOP;

            boolean edgeStartBottom = (mDragEdge == DRAG_EDGE_TOP)
                    && edgeFlags == ViewDragHelper.EDGE_BOTTOM;

            if (edgeStartLeft || edgeStartRight || edgeStartTop || edgeStartBottom) {
                mDragHelper.captureChildView(mMainView, pointerId);
            }
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            if (DEBUG) Log.d(TAG, "onViewPositionChanged " + left + " top " + top + " dx " + dx + " dy " + dy);
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            if (mMode == MODE_SAME_LEVEL) {
                if (mDragEdge == DRAG_EDGE_LEFT) {
                    mSecondaryViewLeft.offsetLeftAndRight(dx);
                } else if (mDragEdge == DRAG_EDGE_RIGHT) {
                    mSecondaryViewRight.offsetLeftAndRight(dx);
                } else if (mDragEdge == DRAG_EDGE_LEFTRIGHT) {
                    mSecondaryViewLeft.offsetLeftAndRight(dx);
                    mSecondaryViewRight.offsetLeftAndRight(dx);
                } else {
                    mSecondaryViewLeft.offsetTopAndBottom(dy);
                }
            }

            boolean isMoved = (mMainView.getLeft() != mLastMainLeft) || (mMainView.getTop() != mLastMainTop);
            if (mSwipeListener != null && isMoved) {
                if (mMainView.getLeft() == mRectMainClose.left && mMainView.getTop() == mRectMainClose.top) {
                    mSwipeListener.onClosed(SwipeRevealLayout.this);
                }
                else if (mMainView.getLeft() == mRectMainLeftOpen.left && mMainView.getTop() == mRectMainLeftOpen.top) {
                    mSwipeListener.onOpened(SwipeRevealLayout.this);
                }
                else if (mMainView.getRight() == mRectMainRightOpen.left && mMainView.getTop() == mRectMainRightOpen.top) {
                    mSwipeListener.onOpened(SwipeRevealLayout.this);
                }
                else {
                    mSwipeListener.onSlide(SwipeRevealLayout.this, getSlideOffset());
                }
            }

            mLastMainLeft = mMainView.getLeft();
            mLastMainTop = mMainView.getTop();
            ViewCompat.postInvalidateOnAnimation(SwipeRevealLayout.this);
        }

        private float getSlideOffset() {
            if (DEBUG) Log.d(TAG, "getSlideOffset");
            switch (mDragEdge) {
                case DRAG_EDGE_LEFT:
                    return (float) (mMainView.getLeft() - mRectMainClose.left) / mSecondaryViewLeft.getWidth();

                case DRAG_EDGE_RIGHT:
                    return (float) (mRectMainClose.left - mMainView.getLeft()) / mSecondaryViewRight.getWidth();

                case DRAG_EDGE_LEFTRIGHT:
                    return (float) (mRectMainClose.left - mMainView.getLeft()) / mSecondaryViewRight.getWidth();

                case DRAG_EDGE_TOP:
                    return (float) (mMainView.getTop() - mRectMainClose.top) / mSecondaryViewLeft.getHeight();

                case DRAG_EDGE_BOTTOM:
                    return (float) (mRectMainClose.top - mMainView.getTop()) / mSecondaryViewLeft.getHeight();

                default:
                    return 0;
            }
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (DEBUG) Log.d(TAG, "onViewDragStateChanged " + getStateString(state));
            super.onViewDragStateChanged(state);
            final int prevState = mState;

            switch (state) {
                case ViewDragHelper.STATE_DRAGGING:
                    mState = STATE_DRAGGING;
                    break;

                case ViewDragHelper.STATE_IDLE:

                    // drag edge is left or right
                    if (mDragEdge == DRAG_EDGE_LEFT || mDragEdge == DRAG_EDGE_RIGHT || mDragEdge == DRAG_EDGE_LEFTRIGHT) {
                        if (mMainView.getLeft() == mRectMainClose.left) {
                            mState = STATE_CLOSE;
                        } else {
                            mState = STATE_OPEN;
                        }
                    }

                    // drag edge is top or bottom
                    else {
                        if (mMainView.getTop() == mRectMainClose.top) {
                            mState = STATE_CLOSE;
                        } else {
                            mState = STATE_OPEN;
                        }
                    }
                    break;
            }

            if (mDragStateChangeListener != null && !mAborted && prevState != mState) {
                mDragStateChangeListener.onDragStateChanged(mState);
            }
        }
    };

    public static String getStateString(int state) {
        switch (state) {
            case STATE_CLOSE:
                return "state_close";

            case STATE_CLOSING:
                return "state_closing";

            case STATE_OPEN:
                return "state_open";

            case STATE_OPENING:
                return "state_opening";

            case STATE_DRAGGING:
                return "state_dragging";

            default:
                return "undefined";
        }
    }

    private int pxToDp(int px) {
        Resources resources = getContext().getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return (int) (px / ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
    }
}