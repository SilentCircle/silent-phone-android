/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.silentphone2.list;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.silentcircle.silentphone2.R;


/**
 * Copy of packages/apps/UnifiedEmail - com.android.mail.ui.SwipeHelper with changes.
 */
public class SwipeHelper {
    private static final String TAG = SwipeHelper.class.getSimpleName();
    private static final boolean DEBUG_INVALIDATE = false;
    private static final boolean CONSTRAIN_SWIPE = true;
    private static final boolean FADE_OUT_DURING_SWIPE = true;
    private static final boolean DISMISS_IF_SWIPED_FAR_ENOUGH = true;
    private static final boolean LOG_SWIPE_DISMISS_VELOCITY = false; // STOPSHIP - DEBUG ONLY

    // --Commented out by Inspection (02.03.15 17:09):private static final int IS_SWIPEABLE_TAG = R.id.is_swipeable_tag;
    // --Commented out by Inspection (02.03.15 17:09):private static final Object IS_SWIPEABLE = new Object();

    public static final int X = 0;
    private static final int Y = 1;

    private static final LinearInterpolator sLinearInterpolator = new LinearInterpolator();

    private static int SWIPE_ESCAPE_VELOCITY = -1;
    private static int DEFAULT_ESCAPE_ANIMATION_DURATION;
    private static int MAX_ESCAPE_ANIMATION_DURATION;
    private static int MAX_DISMISS_VELOCITY;
    private static int SNAP_ANIM_LEN;
    private static int SWIPE_SCROLL_SLOP;
    private static float MIN_SWIPE;
    private static float MIN_VERT;
    private static float MIN_LOCK;

    private static final float ALPHA_FADE_START = 0f; // fraction of thumbnail width
                                               // where fade starts
    private static final float ALPHA_FADE_END = 0.7f; // fraction of thumbnail width
                                              // beyond which alpha->0
    private static final float FACTOR = 1.2f;

    private float mMinAlpha = 0.3f;

    private final float mPagingTouchSlop;
    private final SwipeHelperCallback mCallback;
    private final int mSwipeDirection;
    private final VelocityTracker mVelocityTracker;

    private float mInitialTouchPosX;
    private boolean mDragging;
    private View mCurrView;
    private View mCurrAnimView;
    private boolean mCanCurrViewBeDismissed;
    private final float mDensityScale;
    private float mLastY;
    private float mInitialTouchPosY;

    private float mStartAlpha;

    @SuppressWarnings("FieldCanBeLocal")
    private final float mChildSwipedFarEnoughFactor = 0.4f;
    @SuppressWarnings("FieldCanBeLocal")
    private final float mChildSwipedFastEnoughFactor = 0.05f;

    public SwipeHelper(Context context, int swipeDirection, SwipeHelperCallback callback, float densityScale,
            float pagingTouchSlop) {
        mCallback = callback;
        mSwipeDirection = swipeDirection;
        mVelocityTracker = VelocityTracker.obtain();
        mDensityScale = densityScale;
        mPagingTouchSlop = pagingTouchSlop;
        if (SWIPE_ESCAPE_VELOCITY == -1) {
            Resources res = context.getResources();
            SWIPE_ESCAPE_VELOCITY = res.getInteger(R.integer.swipe_escape_velocity);
            DEFAULT_ESCAPE_ANIMATION_DURATION = res.getInteger(R.integer.escape_animation_duration);
            MAX_ESCAPE_ANIMATION_DURATION = res.getInteger(R.integer.max_escape_animation_duration);
            MAX_DISMISS_VELOCITY = res.getInteger(R.integer.max_dismiss_velocity);
            SNAP_ANIM_LEN = res.getInteger(R.integer.snap_animation_duration);
            SWIPE_SCROLL_SLOP = res.getInteger(R.integer.swipe_scroll_slop);
            MIN_SWIPE = res.getDimension(R.dimen.min_swipe);
            MIN_VERT = res.getDimension(R.dimen.min_vert);
            MIN_LOCK = res.getDimension(R.dimen.min_lock);
        }
    }

//    public void setDensityScale(float densityScale) {
//        mDensityScale = densityScale;
//    }
//
//    public void setPagingTouchSlop(float pagingTouchSlop) {
//        mPagingTouchSlop = pagingTouchSlop;
//    }
//
//    public void setChildSwipedFarEnoughFactor(float factor) {
//        mChildSwipedFarEnoughFactor = factor;
//    }
//
//    public void setChildSwipedFastEnoughFactor(float factor) {
//        mChildSwipedFastEnoughFactor = factor;
//    }

    private float getVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getXVelocity() :
                vt.getYVelocity();
    }

    private ObjectAnimator createTranslationAnimation(View v, float newPos) {
        return ObjectAnimator.ofFloat(v, mSwipeDirection == X ? "translationX" : "translationY", newPos);
    }

    private ObjectAnimator createDismissAnimation(View v, float newPos, int duration) {
        ObjectAnimator anim = createTranslationAnimation(v, newPos);
        anim.setInterpolator(sLinearInterpolator);
        anim.setDuration(duration);
        return anim;
    }

    private float getPerpendicularVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getYVelocity() :
                vt.getXVelocity();
    }

    private void setTranslation(View v, float translate) {
        if (mSwipeDirection == X) {
            v.setTranslationX(translate);
        } else {
            v.setTranslationY(translate);
        }
    }

    private float getSize(View v) {
        return mSwipeDirection == X ? v.getMeasuredWidth() :
                v.getMeasuredHeight();
    }

// --Commented out by Inspection START (02.03.15 17:08):
//    public void setMinAlpha(float minAlpha) {
//        mMinAlpha = minAlpha;
//    }
// --Commented out by Inspection STOP (02.03.15 17:08)

    private float getAlphaForOffset(View view) {
        float viewSize = getSize(view);
        final float fadeSize = ALPHA_FADE_END * viewSize;
        float result = mStartAlpha;
        float pos = view.getTranslationX();
        if (pos >= viewSize * ALPHA_FADE_START) {
            result = mStartAlpha - (pos - viewSize * ALPHA_FADE_START) / fadeSize;
        } else if (pos < viewSize * (mStartAlpha - ALPHA_FADE_START)) {
            result = mStartAlpha + (viewSize * ALPHA_FADE_START + pos) / fadeSize;
        }
        return Math.max(mMinAlpha, result);
    }

    // invalidate the view's own bounds all the way up the view hierarchy
    private static void invalidateGlobalRegion(View view) {
        invalidateGlobalRegion(
                view,
                new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
    }

    // invalidate a rectangle relative to the view's coordinate system all the way up the view
    // hierarchy
    private static void invalidateGlobalRegion(View view, RectF childBounds) {
        // childBounds.offset(view.getTranslationX(), view.getTranslationY());
        if (DEBUG_INVALIDATE)
            Log.v(TAG, "-------------");
        while (view.getParent() != null && view.getParent() instanceof View) {
            view = (View) view.getParent();
            view.getMatrix().mapRect(childBounds);
            view.invalidate((int) Math.floor(childBounds.left),
                    (int) Math.floor(childBounds.top),
                    (int) Math.ceil(childBounds.right),
                    (int) Math.ceil(childBounds.bottom));
            if (DEBUG_INVALIDATE) {
                Log.v(TAG, "INVALIDATE(" + (int) Math.floor(childBounds.left)
                        + "," + (int) Math.floor(childBounds.top)
                        + "," + (int) Math.ceil(childBounds.right)
                        + "," + (int) Math.ceil(childBounds.bottom));
            }
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastY = ev.getY();
                mDragging = false;
                mCurrView = mCallback.getChildAtPosition(ev);
                mVelocityTracker.clear();
                if (mCurrView != null) {
                    mCurrAnimView = mCallback.getChildContentView(mCurrView);
                    mStartAlpha = mCurrAnimView.getAlpha();
                    mCanCurrViewBeDismissed = mCallback.canChildBeDismissed(mCurrView);
                    mVelocityTracker.addMovement(ev);
                    mInitialTouchPosX = ev.getX();
                    mInitialTouchPosY = ev.getY();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    // Check the movement direction.
                    if (mLastY >= 0 && !mDragging) {
                        float currY = ev.getY();
                        float currX = ev.getX();
                        float deltaY = Math.abs(currY - mInitialTouchPosY);
                        float deltaX = Math.abs(currX - mInitialTouchPosX);
                        if (deltaY > SWIPE_SCROLL_SLOP && deltaY > (FACTOR * deltaX)) {
                            mLastY = ev.getY();
                            mCallback.onScroll();
                            return false;
                        }
                    }
                    mVelocityTracker.addMovement(ev);
                    float pos = ev.getX();
                    float delta = pos - mInitialTouchPosX;
                    if (Math.abs(delta) > mPagingTouchSlop) {
                        mCallback.onBeginDrag(mCallback.getChildContentView(mCurrView));
                        mDragging = true;
                        mInitialTouchPosX = ev.getX() - mCurrAnimView.getTranslationX();
                        mInitialTouchPosY = ev.getY();
                    }
                }
                mLastY = ev.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                mCurrView = null;
                mCurrAnimView = null;
                mLastY = -1;
                break;
        }
        return mDragging;
    }

    /**
     * @param view The view to be dismissed
     * @param velocity The desired pixels/second speed at which the view should
     *            move
     */
    private void dismissChild(final View view, float velocity) {
        final View animView = mCallback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        float newPos = determinePos(animView, velocity);
        int duration = determineDuration(animView, newPos, velocity);

        animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ObjectAnimator anim = createDismissAnimation(animView, newPos, duration);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCallback.onChildDismissed(view);
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                //noinspection PointlessBooleanExpression
                if (FADE_OUT_DURING_SWIPE && canAnimViewBeDismissed) {
                    animView.setAlpha(getAlphaForOffset(animView));
                }
                invalidateGlobalRegion(animView);
            }
        });
        anim.start();
    }

    private int determineDuration(View animView, float newPos, float velocity) {
        int duration = MAX_ESCAPE_ANIMATION_DURATION;
        if (velocity != 0) {
            duration = Math
                    .min(duration,
                            (int) (Math.abs(newPos - animView.getTranslationX()) * 1000f / Math
                                    .abs(velocity)));
        } else {
            duration = DEFAULT_ESCAPE_ANIMATION_DURATION;
        }
        return duration;
    }

    private float determinePos(View animView, float velocity) {
        float newPos;
        if (velocity < 0 || (velocity == 0 && animView.getTranslationX() < 0)
                // if we use the Menu to dismiss an item in landscape, animate up
                || (velocity == 0 && animView.getTranslationX() == 0 && mSwipeDirection == Y)) {
            newPos = -getSize(animView);
        } 
        else {
            newPos = getSize(animView);
        }
        return newPos;
    }

    void snapChild(final View view, @SuppressWarnings("UnusedParameters") float velocity) {
        final View animView = mCallback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        ObjectAnimator anim = createTranslationAnimation(animView, 0);
        int duration = SNAP_ANIM_LEN;
        anim.setDuration(duration);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                //noinspection PointlessBooleanExpression
                if (FADE_OUT_DURING_SWIPE && canAnimViewBeDismissed) {
                    animView.setAlpha(getAlphaForOffset(animView));
                }
                invalidateGlobalRegion(animView);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animView.setAlpha(mStartAlpha);
                mCallback.onDragCancelled(mCurrView);
            }
        });
        anim.start();
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!mDragging) {
            return false;
        }
        mVelocityTracker.addMovement(ev);
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    float deltaX = ev.getX() - mInitialTouchPosX;
                    float deltaY = Math.abs(ev.getY() - mInitialTouchPosY);
                    // If the user has gone vertical and not gone horizontal AT
                    // LEAST minBeforeLock, switch to scroll. Otherwise, cancel
                    // the swipe.
                    if (!mDragging && deltaY > MIN_VERT && (Math.abs(deltaX)) < MIN_LOCK
                            && deltaY > (FACTOR * Math.abs(deltaX))) {
                        mCallback.onScroll();
                        return false;
                    }
                    float minDistance = MIN_SWIPE;
                    if (Math.abs(deltaX) < minDistance) {
                        // Don't start the drag until at least X distance has
                        // occurred.
                        return true;
                    }
                    // don't let items that can't be dismissed be dragged more
                    // than maxScrollDistance
                    //noinspection PointlessBooleanExpression
                    if (CONSTRAIN_SWIPE && !mCallback.canChildBeDismissed(mCurrView)) {
                        float size = getSize(mCurrAnimView);
                        float maxScrollDistance = 0.15f * size;
                        if (Math.abs(deltaX) >= size) {
                            deltaX = deltaX > 0 ? maxScrollDistance : -maxScrollDistance;
                        } else {
                            deltaX = maxScrollDistance
                                    * (float) Math.sin((deltaX / size) * (Math.PI / 2));
                        }
                    }
                    setTranslation(mCurrAnimView, deltaX);
                    //noinspection PointlessBooleanExpression
                    if (FADE_OUT_DURING_SWIPE && mCanCurrViewBeDismissed) {
                        mCurrAnimView.setAlpha(getAlphaForOffset(mCurrAnimView));
                    }
                    invalidateGlobalRegion(mCallback.getChildContentView(mCurrView));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCurrView != null) {
                    float maxVelocity = MAX_DISMISS_VELOCITY * mDensityScale;
                    mVelocityTracker.computeCurrentVelocity(1000 /* px/sec */, maxVelocity);
                    float escapeVelocity = SWIPE_ESCAPE_VELOCITY * mDensityScale;
                    float velocity = getVelocity(mVelocityTracker);
                    float perpendicularVelocity = getPerpendicularVelocity(mVelocityTracker);

                    // Decide whether to dismiss the current view
                    // Tweak constants below as required to prevent erroneous
                    // swipe/dismiss
                    float translation = Math.abs(mCurrAnimView.getTranslationX());
                    float currAnimViewSize = getSize(mCurrAnimView);
                    // Long swipe = translation of {@link #mChildSwipedFarEnoughFactor} * width
                    //noinspection PointlessBooleanExpression
                    boolean childSwipedFarEnough = DISMISS_IF_SWIPED_FAR_ENOUGH
                            && translation > mChildSwipedFarEnoughFactor * currAnimViewSize;
                    // Fast swipe = > escapeVelocity and translation of
                    // {@link #mChildSwipedFastEnoughFactor} * width
                    boolean childSwipedFastEnough = (Math.abs(velocity) > escapeVelocity)
                            && (Math.abs(velocity) > Math.abs(perpendicularVelocity))
                            && (velocity > 0) == (mCurrAnimView.getTranslationX() > 0)
                            && translation > mChildSwipedFastEnoughFactor * currAnimViewSize;
                    if (LOG_SWIPE_DISMISS_VELOCITY) {
                        Log.v(TAG, "Swipe/Dismiss: " + velocity + "/" + escapeVelocity + "/"
                                + perpendicularVelocity + ", x: " + translation + "/"
                                + currAnimViewSize);
                    }

                    boolean dismissChild = mCallback.canChildBeDismissed(mCurrView)
                            && (childSwipedFastEnough || childSwipedFarEnough);

                    if (dismissChild) {
                        dismissChild(mCurrView, childSwipedFastEnough ? velocity : 0f);
                    } else {
                        snapChild(mCurrView, velocity);
                    }
                }
                break;
        }
        return true;
    }

// --Commented out by Inspection START (02.03.15 17:08):
//    public static void setSwipeable(View view, boolean swipeable) {
//        view.setTag(IS_SWIPEABLE_TAG, swipeable ? IS_SWIPEABLE : null);
//    }
// --Commented out by Inspection STOP (02.03.15 17:08)

// --Commented out by Inspection START (02.03.15 17:08):
//    public static boolean isSwipeable(View view) {
//        return IS_SWIPEABLE == view.getTag(IS_SWIPEABLE_TAG);
//    }
// --Commented out by Inspection STOP (02.03.15 17:08)

    public interface SwipeHelperCallback {
        View getChildAtPosition(MotionEvent ev);

        View getChildContentView(View v);

        void onScroll();

        boolean canChildBeDismissed(View v);

        void onBeginDrag(View v);

        void onChildDismissed(View v);

        void onDragCancelled(View v);

    }

    public interface OnItemGestureListener {
        void onSwipe(View view);

        @SuppressWarnings("unused")
        void onTouch();

        @SuppressWarnings("unused")
        boolean isSwipeEnabled();
    }
}
