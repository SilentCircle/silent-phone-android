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

package com.silentcircle.contacts.detail;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.TextView;

import com.silentcircle.contacts.widget.FrameLayoutWithOverlay;
import com.silentcircle.silentphone2.R;

/**
 * This is a tab in the {@link ContactDetailTabCarousel}.
 */
public class CarouselTab extends FrameLayoutWithOverlay {

    private static final String TAG = CarouselTab.class.getSimpleName();

    private static final long FADE_TRANSITION_TIME = 150;

    private TextView mLabelView;
    private View mLabelBackgroundView;

    /**
     * This view adds an alpha layer over the entire tab (except for the label).
     */
    private View mAlphaLayer;

    public CarouselTab(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLabelView = (TextView) findViewById(R.id.label);
        mLabelBackgroundView = findViewById(R.id.label_background);
        mAlphaLayer = findViewById(R.id.alpha_overlay);
        setAlphaLayer(mAlphaLayer);
    }

    public void setLabel(String label) {
        mLabelView.setText(label);
    }

    public void showSelectedState() {
        mLabelView.setSelected(true);
    }

    public void showDeselectedState() {
        mLabelView.setSelected(false);
    }

    public void fadeInLabelViewAnimator(int startDelay, boolean fadeBackground) {
        final ViewPropertyAnimator labelAnimator = mLabelView.animate();
        mLabelView.setAlpha(0.0f);
        labelAnimator.alpha(1.0f);
        labelAnimator.setStartDelay(startDelay);
        labelAnimator.setDuration(FADE_TRANSITION_TIME);

        if (fadeBackground) {
            final ViewPropertyAnimator backgroundAnimator = mLabelBackgroundView.animate();
            mLabelBackgroundView.setAlpha(0.0f);
            backgroundAnimator.alpha(1.0f);
            backgroundAnimator.setStartDelay(startDelay);
            backgroundAnimator.setDuration(FADE_TRANSITION_TIME);
        }
    }
}
