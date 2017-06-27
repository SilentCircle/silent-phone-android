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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.support.v4.content.ContextCompat;

import com.silentcircle.silentphone2.R;

/**
 * Wrapper for burn animation drawable.
 */
public abstract class BurnAnimationDrawable extends AnimationDrawable {

    public BurnAnimationDrawable(Context context) {
        this((AnimationDrawable) ContextCompat.getDrawable(context, R.drawable.poof));
        setOneShot(true);
    }

    protected BurnAnimationDrawable(AnimationDrawable drawable) {
        for (int i = 0; i < drawable.getNumberOfFrames(); i++) {
            addFrame(drawable.getFrame(i), drawable.getDuration(i));
        }
    }

    abstract void onAnimationEnd();

    @Override
    public void start() {
        super.start();
        Handler animationHandler = new Handler();
        animationHandler.postDelayed(new Runnable() {

            public void run() {
                onAnimationEnd();
            }
        }, getTotalDuration());

    }

    public int getTotalDuration() {
        int duration = 0;
        for (int i = 0; i < getNumberOfFrames(); i++, duration += getDuration(i));
        return duration;
    }
}
