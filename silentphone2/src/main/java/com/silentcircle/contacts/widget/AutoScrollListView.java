/*
Copyright (C) 2013-2017, Silent Circle, LLC.  All rights reserved.

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
 * Copyright (C) 2010 The Android Open Source Project
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
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * A ListView that can be asked to scroll (smoothly or otherwise) to a specific
 * position.  This class takes advantage of similar functionality that exists
 * in {@link android.widget.ListView} and enhances it.
 */
public class AutoScrollListView extends ListView {

    /**
     * Position the element at about 1/3 of the list height
     */
    private static final float PREFERRED_SELECTION_OFFSET_FROM_TOP = 0.33f;

    private int mRequestedScrollPosition = -1;
    private boolean mSmoothScrollRequested;
    private float mOffset;
    private boolean mForceScroll;

    public AutoScrollListView(Context context) {
        super(context);
    }

    public AutoScrollListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoScrollListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Brings the specified position to view by optionally performing a jump-scroll maneuver:
     * first it jumps to some position near the one requested and then does a smooth
     * scroll to the requested position.  This creates an impression of full smooth
     * scrolling without actually traversing the entire list.  If smooth scrolling is
     * not requested, instantly positions the requested item at a preferred offset.
     */
    public void requestPositionToScreen(int position, boolean smoothScroll) {
        mRequestedScrollPosition = position;
        mSmoothScrollRequested = smoothScroll;
        mOffset = (getHeight() * PREFERRED_SELECTION_OFFSET_FROM_TOP);
        requestLayout();
    }

    public void requestPositionToScreen(int position, boolean smoothScroll, float offset,
            boolean forceScroll) {
        mRequestedScrollPosition = position;
        mSmoothScrollRequested = smoothScroll;
        mOffset = offset;
        mForceScroll = forceScroll;
        requestLayout();
    }

    public float getPreferredOffset() {
        float result = mOffset;
        return result;
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (mRequestedScrollPosition == -1) {
            return;
        }

        final int position = mRequestedScrollPosition;
        mRequestedScrollPosition = -1;

        final boolean forceScroll = mForceScroll;
        mForceScroll = false;

        int firstPosition = getFirstVisiblePosition() + 1;
        int lastPosition = getLastVisiblePosition();

        if (position >= firstPosition && position <= lastPosition && !forceScroll) {
            return; // Already on screen
        }

        final int offset = (int) getPreferredOffset();
        if (!mSmoothScrollRequested) {
            setSelectionFromTop(position, offset);

            // Since we have changed the scrolling position, we need to redo child layout
            // Calling "requestLayout" in the middle of a layout pass has no effect,
            // so we call layoutChildren explicitly
            super.layoutChildren();

        } else {
            // We will first position the list a couple of screens before or after
            // the new selection and then scroll smoothly to it.
            int twoScreens = (lastPosition - firstPosition) * 2;
            int preliminaryPosition;
            if (position < firstPosition) {
                preliminaryPosition = position + twoScreens;
                if (preliminaryPosition >= getCount()) {
                    preliminaryPosition = getCount() - 1;
                }
                if (preliminaryPosition < firstPosition) {
                    setSelection(preliminaryPosition);
                    super.layoutChildren();
                }
            } else {
                preliminaryPosition = position - twoScreens;
                if (preliminaryPosition < 0) {
                    preliminaryPosition = 0;
                }
                if (preliminaryPosition > lastPosition) {
                    setSelection(preliminaryPosition);
                    super.layoutChildren();
                }
            }
            smoothScrollToPositionFromTop(position, offset);
        }
    }
}
