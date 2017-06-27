// Copyright 2016 Jay Rambhia
//
// https://github.com/jayrambhia/Tooltip
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.fenchtose.tooltip;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewAnimationUtils;

/**
 * Helper class to create Animator Objects
 */
public class AnimationUtils {

    /**
     * Fade Animation
     * @param view View to be animated
     * @param fromAlpha initial alpha
     * @param toAlpha final alpha
     * @param duration animation duration in milliseconds
     * @return Animator Object
     */
    @NonNull
    public static Animator fade(@NonNull final View view, float fromAlpha, float toAlpha, int duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", fromAlpha, toAlpha);
        animator.setDuration(duration);
        return animator;
    }

    /**
     * Circular Reveal Animation
     * @param view View to be animated
     * @param cx x coordinate of the center of the circle
     * @param cy y coordinate of the center of the circle
     * @param startRadius initial circle radius
     * @param finalRadius final circle radius
     * @param duration animation duration in milliseconds
     * @return Animator Object
     */
    @NonNull
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Animator reveal(@NonNull final View view, int cx, int cy, int startRadius, int finalRadius, int duration) {
        Animator animator = ViewAnimationUtils.createCircularReveal(view, cx, cy, startRadius, finalRadius);
        animator.setDuration(duration);
        return animator;
    }

    /*@NonNull
    public static Animator slideY(@NonNull View view, int fromY, int toY, int duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "y", toY, fromY, toY);
        animator.setDuration(duration);
        return animator;
    }*/

    /**
     * Animator to animate Y scale of the view. X scale is constant
     *
     * @param view View to be animated
     * @param pivotX x coordinate of the pivot
     * @param pivotY y coordinate of the pivot
     * @param fromScale initial scale
     * @param toScale final scale
     * @param duration animation duration in milliseconds
     * @return Animator Object
     */
    @NonNull
    public static Animator scaleY(@NonNull View view, int pivotX, int pivotY, float fromScale, float toScale, int duration) {
        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
        Animator animator = ObjectAnimator.ofFloat(view, "scaleY", fromScale, toScale);
        animator.setDuration(duration);
        return animator;
    }

    /**
     * Animator to animate X scale of the view. Y scale is constant
     *
     * @param view View to be animated
     * @param pivotX x coordinate of the pivot
     * @param pivotY y coordinate of the pivot
     * @param fromScale initial scale
     * @param toScale final scale
     * @param duration animation duration in milliseconds
     * @return Animator Object
     */
    @NonNull
    public static Animator scaleX(@NonNull View view, int pivotX, int pivotY, float fromScale, float toScale, int duration) {
        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
        Animator animator = ObjectAnimator.ofFloat(view, "scaleX", fromScale, toScale);
        animator.setDuration(duration);
        return animator;
    }

}
