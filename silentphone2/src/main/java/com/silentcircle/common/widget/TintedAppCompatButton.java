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
package com.silentcircle.common.widget;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;

/**
 * Android issue 173697
 * AppCompatButton does not support drawableTint attribute
 * (https://code.google.com/p/android/issues/detail?id=173697)
 *
 * Extended AppCompatButton to work around this.
 */
public class TintedAppCompatButton extends AppCompatButton {

    protected static final int LEFT = 0;
    protected static final int TOP = 1;
    protected static final int RIGHT = 2;
    protected static final int BOTTOM = 3;

    protected static final int[] VIEW_ATTRS = {android.R.attr.tint};

    protected int mDrawableTintColor;

    public TintedAppCompatButton(Context context) {
        this(context, null);
    }

    public TintedAppCompatButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TintedAppCompatButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, VIEW_ATTRS, defStyleAttr, 0);
        mDrawableTintColor = ContextCompat.getColor(getContext(), typedArray.getResourceId(0, 0));
        typedArray.recycle();
        tint();
    }

    private void tint() {
        PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
        Drawable[] drawables = getCompoundDrawables();
        tint(drawables[LEFT], mDrawableTintColor, tintMode);
        tint(drawables[TOP], mDrawableTintColor, tintMode);
        tint(drawables[RIGHT], mDrawableTintColor, tintMode);
        tint(drawables[BOTTOM], mDrawableTintColor, tintMode);
    }

    private void tint(@Nullable Drawable drawable, int color, PorterDuff.Mode tintMode) {
        if (drawable != null) {
            drawable.mutate().setColorFilter(color, tintMode);
        }
    }
}