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
 * Copyright (C) 2012 The Android Open Source Project
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

package com.silentcircle.contacts.widget;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.silentcircle.contacts.detail.ContactDetailDisplayUtils;
import com.silentcircle.contacts.utils.ThemeUtils;

/**
 * A View that other Views can use to create a touch-interceptor layer above
 * their other sub-views. This layer can be enabled and disabled; when enabled,
 * clicks are intercepted and passed to a listener.
 *
 * Also supports an alpha layer to dim the content underneath.  By default, the
 * alpha layer is the same View as the touch-interceptor layer.  However, for
 * some use-cases, you want a few Views to not be dimmed, but still have touches
 * intercepted (for example, {@link CarouselTab}'s label appears above the alpha
 * layer).  In this case, you can specify the View to use as the alpha layer via
 * setAlphaLayer(); in this case you are responsible for managing the z-order of
 * the alpha-layer with respect to your other sub-views.
 *
 * Typically, you would not use this class directly, but rather use another class
 * that uses it, for example {@link FrameLayoutWithOverlay}.
 */
public class AlphaTouchInterceptorOverlay extends FrameLayout {

    private View mInterceptorLayer;
    private View mAlphaLayer;
    private float mAlpha = 0.0f;

    public AlphaTouchInterceptorOverlay(Context context) {
        super(context);

        mInterceptorLayer = new View(context);
        final int resId = ThemeUtils.getSelectableItemBackground(context.getTheme());
        mInterceptorLayer.setBackgroundResource(resId);
        addView(mInterceptorLayer);

        mAlphaLayer = this;
    }

    /**
     * Set the View that the overlay will use as its alpha-layer.  If
     * none is set it will use itself.  Only necessary to set this if
     * some child views need to appear above the alpha-layer but below
     * the touch-interceptor.
     */
    public void setAlphaLayer(View alphaLayer) {
        if (mAlphaLayer == alphaLayer) return;

        // We're no longer the alpha-layer, so make ourself invisible.
        if (mAlphaLayer == this) ContactDetailDisplayUtils.setAlphaOnViewBackground(this, 0.0f);

        mAlphaLayer = (alphaLayer == null) ? this : alphaLayer;
        setAlphaLayerValue(mAlpha);
    }

    /** Sets the alpha value on the alpha layer. */
    public void setAlphaLayerValue(float alpha) {
        mAlpha = alpha;
        if (mAlphaLayer != null) {
            ContactDetailDisplayUtils.setAlphaOnViewBackground(mAlphaLayer, mAlpha);
        }
    }

    /** Delegate to interceptor-layer. */
    public void setOverlayOnClickListener(OnClickListener listener) {
        mInterceptorLayer.setOnClickListener(listener);
    }

    /** Delegate to interceptor-layer. */
    public void setOverlayClickable(boolean clickable) {
        mInterceptorLayer.setClickable(clickable);
    }
}
