/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts.quickcontact;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.silentcircle.contacts.utils.SchedulingUtils;
import com.silentcircle.silentphone2.R;

// import com.android.contacts.test.NeededForReflection;

/**
 * Layout containing single child {@link android.view.View} which it attempts to center
 * around {@link #setChildTargetScreen(android.graphics.Rect)}.
 * <p>
 * Updates drawable state to be {@link android.R.attr#state_first} when child is
 * above target, and {@link android.R.attr#state_last} when child is below
 * target. Also updates {@link android.graphics.drawable.Drawable#setLevel(int)} on child
 * {@link android.view.View#getBackground()} to reflect horizontal center of target.
 * <p>
 * The reason for this approach is because target {@link android.graphics.Rect} is in screen
 * coordinates disregarding decor insets; otherwise something like
 * {@link android.widget.PopupWindow} might work better.
 */
public class FloatingChildLayout extends FrameLayout {
    private static final String TAG = "FloatingChildLayout";
    private int mFixedTopPosition;
    private View mChild;
    private Rect mTargetScreen = new Rect();
    private final int mAnimationDuration;

    /** The phase of the background dim. This is one of the values of {@link BackgroundPhase}  */
    private int mBackgroundPhase = BackgroundPhase.BEFORE;

    private ObjectAnimator mBackgroundAnimator = ObjectAnimator.ofInt(this, "backgroundColorAlpha", 0, DIM_BACKGROUND_ALPHA);
    private ObjectAnimator mBackgroundAnimatorRev = ObjectAnimator.ofInt(this, "backgroundColorAlpha", DIM_BACKGROUND_ALPHA, 0);


    private interface BackgroundPhase {
        public static final int BEFORE = 0;
        public static final int APPEARING_OR_VISIBLE = 1;
        public static final int DISAPPEARING_OR_GONE = 3;
    }

    /** The phase of the contents window. This is one of the values of {@link ForegroundPhase}  */
    private int mForegroundPhase = ForegroundPhase.BEFORE;

    private interface ForegroundPhase {
        public static final int BEFORE = 0;
        public static final int APPEARING = 1;
        public static final int IDLE = 2;
        public static final int DISAPPEARING = 3;
        public static final int AFTER = 4;
    }

    // Black, 50% alpha as per the system default.
    private static final int DIM_BACKGROUND_ALPHA = 0x7F;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public FloatingChildLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        final Resources resources = getResources();
        mFixedTopPosition = resources.getDimensionPixelOffset(R.dimen.quick_contact_top_position);
        mAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            super.setBackground(new ColorDrawable(0));
        else
            super.setBackgroundDrawable(new ColorDrawable(0));
    }

    @Override
    protected void onFinishInflate() {
        mChild = findViewById(android.R.id.content);
        mChild.setDuplicateParentStateEnabled(true);

        // this will be expanded in showChild()
        mChild.setScaleX(0.5f);
        mChild.setScaleY(0.5f);
        mChild.setAlpha(0.0f);
    }

    public View getChild() {
        return mChild;
    }

    /**
     * FloatingChildLayout manages its own background, don't set it.
     */
    @Override
    public void setBackground(Drawable background) {
        Log.wtf(TAG, "don't setBackground(), it is managed internally");
    }

    /**
     * Set {@link android.graphics.Rect} in screen coordinates that {@link #getChild()} should be
     * centered around.
     */
    public void setChildTargetScreen(Rect targetScreen) {
        mTargetScreen = targetScreen;
        requestLayout();
    }

    /**
     * Return {@link #mTargetScreen} in local window coordinates, taking any
     * decor insets into account.
     */
    private Rect getTargetInWindow() {
        final Rect windowScreen = new Rect();
        getWindowVisibleDisplayFrame(windowScreen);

        final Rect target = new Rect(mTargetScreen);
        target.offset(-windowScreen.left, -windowScreen.top);
        return target;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        final View child = mChild;
        final Rect target = getTargetInWindow();

        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        if (mFixedTopPosition != -1) {
            // Horizontally centered, vertically fixed position
            final int childLeft = (getWidth() - childWidth) / 2;
            final int childTop = mFixedTopPosition;
            layoutChild(child, childLeft, childTop);
        }
        else {
            // default is centered horizontally around target...
            final int childLeft = target.centerX() - (childWidth / 2);
            // ... and vertically aligned a bit below centered
            final int childTop = target.centerY() - Math.round(childHeight * 0.35f);

            // when child is outside bounds, nudge back inside
            final int clampedChildLeft = clampDimension(childLeft, childWidth, getWidth());
            final int clampedChildTop = clampDimension(childTop, childHeight, getHeight());

            layoutChild(child, clampedChildLeft, clampedChildTop);
        }
    }

    private static int clampDimension(int value, int size, int max) {
        // when larger than bounds, just center
        if (size > max) {
            return (max - size) / 2;
        }

        // clamp to bounds
        return Math.min(Math.max(value, 0), max - size);
    }

    private static void layoutChild(View child, int left, int top) {
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }

    // @NeededForReflection
    public void setBackgroundColorAlpha(int alpha) {
        setBackgroundColor(alpha << 24);
    }

    public void fadeInBackground() {
        if (mBackgroundPhase == BackgroundPhase.BEFORE) {
            mBackgroundPhase = BackgroundPhase.APPEARING_OR_VISIBLE;

            createChildLayer();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                SchedulingUtils.doAfterDraw(this, new Runnable() {
                    @Override
                    public void run() {
                        mBackgroundAnimator.setDuration(mAnimationDuration).start();
                    }
                });
            }
            else
                mBackgroundAnimator.setDuration(mAnimationDuration).start();
        }
    }

    public void fadeOutBackground() {
        if (mBackgroundPhase == BackgroundPhase.APPEARING_OR_VISIBLE) {
            mBackgroundPhase = BackgroundPhase.DISAPPEARING_OR_GONE;
            if (mBackgroundAnimator.isRunning()) {
                mBackgroundAnimator.reverse();
            }
            else {
                mBackgroundAnimatorRev.setDuration(mAnimationDuration).start();
            }
        }
    }

    public boolean isContentFullyVisible() {
        return mForegroundPhase == ForegroundPhase.IDLE;
    }

    /** Begin animating {@link #getChild()} visible. */
    public void showContent(final Runnable onAnimationEndRunnable) {
        if (mForegroundPhase == ForegroundPhase.BEFORE) {
            mForegroundPhase = ForegroundPhase.APPEARING;
            animateScale(false, onAnimationEndRunnable);
        }
    }

    /**
     * Begin animating {@link #getChild()} invisible. Returns false if animation is not valid in
     * this state
     */
    public boolean hideContent(final Runnable onAnimationEndRunnable) {
        if (mForegroundPhase == ForegroundPhase.APPEARING ||
                mForegroundPhase == ForegroundPhase.IDLE) {
            mForegroundPhase = ForegroundPhase.DISAPPEARING;

            createChildLayer();

            animateScale(true, onAnimationEndRunnable);
            return true;
        }
        else {
            return false;
        }
    }

    private void createChildLayer() {
        mChild.invalidate();
        mChild.setLayerType(LAYER_TYPE_HARDWARE, null);
        mChild.buildLayer();
    }

    /** Creates the open/close animation */
    private void animateScale(final boolean isExitAnimation, final Runnable onAnimationEndRunnable) {
        mChild.setPivotX(mTargetScreen.centerX() - mChild.getLeft());
        mChild.setPivotY(mTargetScreen.centerY() - mChild.getTop());

        final int scaleInterpolator = isExitAnimation
                ? android.R.interpolator.accelerate_quint
                : android.R.interpolator.decelerate_quint;
        final float scaleTarget = isExitAnimation ? 0.5f : 1.0f;

        mChild.animate()
                .setDuration(mAnimationDuration)
                .setInterpolator(AnimationUtils.loadInterpolator(getContext(), scaleInterpolator))
                .scaleX(scaleTarget)
                .scaleY(scaleTarget)
                .alpha(isExitAnimation ? 0.0f : 1.0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mChild.setLayerType(LAYER_TYPE_NONE, null);
                        if (isExitAnimation) {
                            if (mForegroundPhase == ForegroundPhase.DISAPPEARING) {
                                mForegroundPhase = ForegroundPhase.AFTER;
                                if (onAnimationEndRunnable != null)
                                    onAnimationEndRunnable.run();
                            }
                        }
                        else {
                            if (mForegroundPhase == ForegroundPhase.APPEARING) {
                                mForegroundPhase = ForegroundPhase.IDLE;
                                if (onAnimationEndRunnable != null)
                                    onAnimationEndRunnable.run();
                            }
                        }
                    }
                });
    }

    private OnTouchListener mOutsideTouchListener;

    public void setOnOutsideTouchListener(OnTouchListener listener) {
        mOutsideTouchListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // at this point, touch wasn't handled by child view; assume outside
        if (mOutsideTouchListener != null) {
            return mOutsideTouchListener.onTouch(this, event);
        }
        return false;
    }
}
