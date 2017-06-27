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
package com.silentcircle.silentphone2.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.widget.LetterTileDrawable;
import com.silentcircle.silentphone2.R;

/**
 * Blur-able image view for in-call screens.
 *
 * Blur will only be applied it BitmapDrawable (or bitmap) is used when setting image view source.
 * When blurred image will be darkened.
 */
public class BlurrableImageView extends ImageView {

    public static final int MAX_SIZE = 150;

    protected boolean mBlur = false;

    public BlurrableImageView(Context context) {
        this(context, null);
    }

    public BlurrableImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlurrableImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        setImageDrawable(new BitmapDrawable(getContext().getResources(), bitmap));
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        Drawable firstLayer = drawable;
        BitmapDrawable secondLayer = null;

        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = null;
            try {
                Bitmap srcBitmap = ((BitmapDrawable) drawable).getBitmap();
                bitmap = getBlurredBitmap(srcBitmap);
                if (bitmap != null) {
                    secondLayer = new BitmapDrawable(getContext().getResources(), bitmap);
                }
            } catch (Exception | OutOfMemoryError e) {
                secondLayer = null;
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        } else if (drawable instanceof LetterTileDrawable) {
            /*
             * show default avatar if contact image not found.
             * photo manager will return specific drawable, but this check is not nice.
             */
            firstLayer = ContextCompat.getDrawable(getContext(),
                    R.drawable.ic_contact_picture_holo_dark);
        }

        if (secondLayer != null) {
            Drawable[] layers = new Drawable[2];
            layers[0] = drawable;
            layers[1] = secondLayer;
            TwoLayerDrawable layerDrawable = new TwoLayerDrawable(layers);
            super.setImageDrawable(layerDrawable);
        } else {
            super.setImageDrawable(firstLayer);
        }
        updateImage();
    }

    private Bitmap getBlurredBitmap(Bitmap srcBitmap) {
        Bitmap bitmap;
        Bitmap recyclable = null;
        int width = srcBitmap.getWidth();
        int height = srcBitmap.getHeight();
        if (Math.max(width, height) > MAX_SIZE) {
            int size = MAX_SIZE;
            float aspectRatio = ((float) height) / ((float) width);
            if (aspectRatio > 1.0f) {
                width = size;
                height = (int) (width * aspectRatio);
            } else {
                height = size;
                width = (int) (height * aspectRatio);
            }
            bitmap = Bitmap.createScaledBitmap(srcBitmap, width, height, true);
            if (bitmap != srcBitmap) {
                recyclable = bitmap;
            }
        } else {
            bitmap = srcBitmap;
        }
        bitmap = ViewUtil.fastBlur(bitmap, 1.0f, 10);
        if (recyclable != null) {
            recyclable.recycle();
        }
        return bitmap;
    }

    /*
    public void releaseResources() {
        Drawable d = getDrawable();
        if (d instanceof LayerDrawable) {
            for (int i = 0; i < ((LayerDrawable) d).getNumberOfLayers(); i++) {
                releaseBitmap(((LayerDrawable) d).getDrawable(i));
            }
        } else {
            releaseBitmap(d);
        }
        super.setImageDrawable(null);
    }

    private void releaseBitmap(Drawable d) {
        if (d instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }
     */

    public boolean isBlurred() {
        return mBlur;
    }

    public void setBlurred(boolean blur) {
        mBlur = blur;
        updateImage();
        invalidate();
    }

    protected void updateImage() {
        if (mBlur) {
            setColorFilter(Color.rgb(123, 123, 123), android.graphics.PorterDuff.Mode.MULTIPLY);
        } else {
            clearColorFilter();
        }
        Drawable d = getDrawable();
        if (d instanceof TwoLayerDrawable) {
            ((TwoLayerDrawable) d).setBlurred(mBlur);
        }
    }

    static class TwoLayerDrawable extends LayerDrawable {

        protected boolean mBlur = false;

        public TwoLayerDrawable(Drawable[] layers) {
            super(layers);
        }

        @Override
        public void draw(Canvas canvas) {
            int index = mBlur ? 1 : 0;
            Drawable dr = getNumberOfLayers() > index ? getDrawable(index) : null;
            if (dr != null) {
                dr.draw(canvas);
            }
        }

        public void setBlurred(boolean blur) {
            mBlur = blur;
        }
    }
}
