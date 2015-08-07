/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.contacts.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.silentcircle.silentphone2.R;


/**
 * An {@link android.widget.ImageView} designed to display QuickContact's contact photo. When requested to draw
 * {@link LetterTileDrawable}'s, this class instead draws a different default avatar drawable.
 *
 * In addition to supporting {@link android.widget.ImageView#setColorFilter} this also supports a {@link #setTint}
 * method.
 *
 * This entire class can be deleted once use of LetterTileDrawable is no longer used
 * inside QuickContactsActivity at all.
 */
public class QuickContactImageView extends ImageView {

    private Drawable mOriginalDrawable;
    private BitmapDrawable mBitmapDrawable;
    private int mTintColor;
    private boolean mIsBusiness;

    public QuickContactImageView(Context context) {
        this(context, null);
    }

    public QuickContactImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickContactImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public QuickContactImageView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @SuppressWarnings("deprecation")
    public void setTint(int color) {
        if (mBitmapDrawable == null || mBitmapDrawable.getBitmap() == null
                || mBitmapDrawable.getBitmap().hasAlpha()) {
            setBackgroundColor(color);
        } 
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                setBackground(null);
            else
                setBackgroundDrawable(null);
        }
        mTintColor = color;
        postInvalidate();
    }

    public boolean isBasedOffLetterTile() {
        return mOriginalDrawable instanceof LetterTileDrawable;
    }

    public void setIsBusiness(boolean isBusiness) {
        mIsBusiness = isBusiness;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        // There is no way to avoid all this casting. Blending modes aren't equally
        // supported for all drawable types.
        final BitmapDrawable bitmapDrawable;
        if (drawable == null || drawable instanceof BitmapDrawable) {
            bitmapDrawable = (BitmapDrawable) drawable;
        } else if (drawable instanceof LetterTileDrawable) {
            if (!mIsBusiness) {
                bitmapDrawable = (BitmapDrawable) getResources().getDrawable(
                        R.drawable.person_white_540dp);
            } else {
                bitmapDrawable = (BitmapDrawable) getResources().getDrawable(
                        R.drawable.person_white_540dp /* R.drawable.generic_business_white_540dp*/);
            }
        } else {
            throw new IllegalArgumentException("Does not support this type of drawable");
        }

        mOriginalDrawable = drawable;
        mBitmapDrawable = bitmapDrawable;
        setTint(mTintColor);
        super.setImageDrawable(bitmapDrawable);
    }

    @Override
    public Drawable getDrawable() {
        return mOriginalDrawable;
    }
}
