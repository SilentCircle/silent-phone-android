/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.silentcircle.common.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.graphics.Matrix;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

public class AnimUtils {
    public static final int DEFAULT_DURATION = -1;
    public static final int NO_DELAY = 0;

    public static final Interpolator EASE_IN;
    public static final Interpolator EASE_OUT;
    public static final Interpolator EASE_OUT_EASE_IN;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            EASE_IN = new PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f);
            EASE_OUT = new PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f);
            EASE_OUT_EASE_IN = new PathInterpolator(0.4f, 0, 0.2f, 1);
        }
        else {
            EASE_IN = new DecelerateInterpolator(1.0f);
            EASE_OUT = new DecelerateInterpolator(1.0f);
            EASE_OUT_EASE_IN = new DecelerateInterpolator(1.0f);
        }
    }

    public static class AnimationCallback {
        public void onAnimationEnd() {}
        public void onAnimationCancel() {}
    }

    public static void crossFadeViews(View fadeIn, View fadeOut, int duration) {
        fadeIn(fadeIn, duration);
        fadeOut(fadeOut, duration);
    }

    public static void fadeOut(View fadeOut, int duration) {
        fadeOut(fadeOut, duration, null);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void fadeOut(final View fadeOut, int durationMs, final AnimationCallback callback) {
        fadeOut(fadeOut, durationMs, callback, true);
    }

    public static void fadeOut(final View fadeOut, int durationMs, final AnimationCallback callback, final boolean setGone) {
        fadeOut.setAlpha(1);
        ViewPropertyAnimator animator = fadeOut.animate();
        animator.cancel();
        animator = (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) ? animator.alpha(0) : animator.alpha(0).withLayer();

        animator.setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (setGone) {
                    fadeOut.setVisibility(View.GONE);
                }
                if (callback != null) {
                    callback.onAnimationEnd();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if(setGone) {
                    fadeOut.setVisibility(View.GONE);
                }
                fadeOut.setAlpha(0);
                if (callback != null) {
                    callback.onAnimationCancel();
                }
            }
        });
        if (durationMs != DEFAULT_DURATION) {
            animator.setDuration(durationMs);
        }
        animator.start();
    }

    public static void fadeIn(View fadeIn, int durationMs) {
        fadeIn(fadeIn, durationMs, true);
    }

    public static void fadeIn(View fadeIn, int durationMs, boolean resetStart) {
        fadeIn(fadeIn, durationMs, NO_DELAY, null, resetStart);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void fadeIn(final View fadeIn, int durationMs, int delay, final AnimationCallback callback) {
        fadeIn(fadeIn, durationMs, delay, callback, true);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void fadeIn(final View fadeIn, int durationMs, int delay, final AnimationCallback callback, boolean resetStart) {
        if (resetStart) {
            fadeIn.setAlpha(0);
        }
        ViewPropertyAnimator animator = fadeIn.animate();
        animator.cancel();

        animator.setStartDelay(delay);
        animator = (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) ? animator.alpha(1) : animator.alpha(1).withLayer();
        animator.setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                fadeIn.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                fadeIn.setAlpha(1);
                if (callback != null) {
                    callback.onAnimationCancel();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (callback != null) {
                    callback.onAnimationEnd();
                }
            }
        });
        if (durationMs != DEFAULT_DURATION) {
            animator.setDuration(durationMs);
        }
        animator.start();
    }

    /**
     * Scales in the view from scale of 0 to actual dimensions.
     * @param view The view to scale.
     * @param durationMs The duration of the scaling in milliseconds.
     * @param startDelayMs The delay to applying the scaling in milliseconds.
     */
    public static void scaleIn(final View view, int durationMs, int startDelayMs) {
        AnimatorListenerAdapter listener = (new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                view.setScaleX(1);
                view.setScaleY(1);
            }
        });
        scaleInternal(view, 0 /* startScaleValue */, 1 /* endScaleValue */, durationMs, startDelayMs, listener, EASE_IN);
    }


    /**
     * Scales out the view from actual dimensions to 0.
     * @param view The view to scale.
     * @param durationMs The duration of the scaling in milliseconds.
     */
    public static void scaleOut(final View view, int durationMs) {
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                view.setVisibility(View.GONE);
                view.setScaleX(0);
                view.setScaleY(0);
            }
        };

        scaleInternal(view, 1 /* startScaleValue */, 0 /* endScaleValue */, durationMs, NO_DELAY, listener, EASE_OUT);
    }

    private static void scaleInternal(final View view, int startScaleValue, int endScaleValue,
            int durationMs, int startDelay, AnimatorListenerAdapter listener,
            Interpolator interpolator) {
        view.setScaleX(startScaleValue);
        view.setScaleY(startScaleValue);

        final ViewPropertyAnimator animator = view.animate();
        animator.cancel();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            animator.setInterpolator(interpolator)
                    .scaleX(endScaleValue)
                    .scaleY(endScaleValue)
                    .setListener(listener);
        }
        else {
            animator.setInterpolator(interpolator)
                    .scaleX(endScaleValue)
                    .scaleY(endScaleValue)
                    .setListener(listener)
                    .withLayer();
        }
        if (durationMs != DEFAULT_DURATION) {
            animator.setDuration(durationMs);
        }
        animator.setStartDelay(startDelay);

        animator.start();
    }

    /**
     * Animates a view to the new specified dimensions.
     * @param view The view to change the dimensions of.
     * @param newWidth The new width of the view.
     * @param newHeight The new height of the view.
     */
    public static void changeDimensions(final View view, final int newWidth, final int newHeight) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

        final int oldWidth = view.getWidth();
        final int oldHeight = view.getHeight();
        final int deltaWidth = newWidth - oldWidth;
        final int deltaHeight = newHeight - oldHeight;

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                Float value = (Float) animator.getAnimatedValue();

                view.getLayoutParams().width = (int) (value * deltaWidth + oldWidth);
                view.getLayoutParams().height = (int) (value * deltaHeight + oldHeight);
                view.requestLayout();
            }
        });
        animator.start();
    }

    public static void changeHeight(final View view, int startHeight, int endHeight, int duration) {
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, endHeight);
        animator.setDuration(duration);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                Integer value = (Integer) animation.getAnimatedValue();
                view.getLayoutParams().height = value;
                view.requestLayout();
            }
        });
        animator.start();
    }

    public static void blinkView(final View blinkView) {
        blinkView.setVisibility(View.VISIBLE);
        blinkView.setAlpha(1.f);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(blinkView, "alpha", 0f).setDuration(300);
        fadeOut.setInterpolator(new DecelerateInterpolator(1.2f));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fadeOut.setAutoCancel(true);
        }
        AnimatorSet animSetFade = new AnimatorSet();

        animSetFade.play(fadeOut);
        animSetFade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                blinkView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                blinkView.setLayerType(View.LAYER_TYPE_NONE, null);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                blinkView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        animSetFade.start();
    }

    public static class MatrixEvaluator implements TypeEvaluator<Matrix> {

        float[] mTempStartValues = new float[9];

        float[] mTempEndValues = new float[9];

        Matrix mTempMatrix = new Matrix();

        @Override
        public Matrix evaluate(float fraction, Matrix startValue, Matrix endValue) {
            startValue.getValues(mTempStartValues);
            endValue.getValues(mTempEndValues);
            for (int i = 0; i < 9; i++) {
                float diff = mTempEndValues[i] - mTempStartValues[i];
                mTempEndValues[i] = mTempStartValues[i] + (fraction * diff);
            }
            mTempMatrix.setValues(mTempEndValues);
            return mTempMatrix;
        }
    }

    public static void startLayoutTransition(ViewGroup viewGroup, final int transitionType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        final LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(transitionType);
        transition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {}

            @Override
            public void endTransition(LayoutTransition transition2, ViewGroup container, View view, int transitionType) {
                transition.disableTransitionType(transitionType);
            }
        });
        viewGroup.setLayoutTransition(transition);
    }

    public static Animation createFlashingAnimation() {
        final Animation animation = new AlphaAnimation(0, 1);
        animation.setDuration(300);
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
        animation.setStartOffset(200);
        return animation;
    }

    public static void setViewRotation(@Nullable final View view, int duration, int rotation) {
        if (view != null) {
            final ViewPropertyAnimatorCompat animation = ViewCompat.animate(view);
            animation.setDuration(duration).rotation(rotation).start();
        }
    }
}
