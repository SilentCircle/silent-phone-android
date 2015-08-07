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
package com.silentcircle.contacts.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;


/**
 * Create a round, clickable image with stroke.
 *
 * Created by werner on 11.01.14.
 */
public class CircleImageSelectable extends ImageView {

    /*
     * Don't care if TAG is in use or not.
     * Android calls the constructors thus they are 'used' but not directly by our Java code.
     */
    @SuppressWarnings("unused")
    private static final String TAG = CircleImageSelectable.class.getSimpleName();

    private Bitmap mCircleBitmap;
    private Bitmap mSourceBitmap;
    private int mBackgroundColor;
    private int mPixelDiff;

    /** A given diameter value of 0.0: adapt the diameter to the measured size (see onSizeChanged)  */
    private float mGivenDiameter;
    private boolean mUseMeasuredSize;
    private float mStrokeWidth;
    private int mDiameter;
    private int mStrokeColor;
    private int mShadowColor;

    @SuppressWarnings("unused")
    public CircleImageSelectable(Context context) {
        this(context, null);
    }

    @SuppressWarnings("unused")
    public CircleImageSelectable(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("unused")
    public CircleImageSelectable(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.background });
        if (a != null) {
            mBackgroundColor = a.getColor(0, 0);
            a.recycle();
        }
        Resources.Theme theme = context.getTheme();
        mStrokeWidth = 0;
        mStrokeColor = android.R.color.white;
        mShadowColor = android.R.color.black;
        if (theme != null) {
            a = theme.obtainStyledAttributes(attrs, R.styleable.CircleImageSelectable, 0, 0);
            if (a != null) {
                mGivenDiameter = a.getDimension(R.styleable.CircleImageSelectable_sp_round_image_diameter, mGivenDiameter);
                mStrokeWidth = a.getDimension(R.styleable.CircleImageSelectable_sp_round_image_stroke_width, 0);
                mStrokeColor = a.getColor(R.styleable.CircleImageSelectable_sp_round_image_stroke_color, android.R.color.white);
                mShadowColor = a.getColor(R.styleable.CircleImageSelectable_sp_round_image_shadow_color, android.R.color.black);
                a.recycle();
            }
        }
        if (mGivenDiameter == 0)
            mUseMeasuredSize = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
///     Log.d(TAG, "+++ Width spec: " + MeasureSpec.toString(widthMeasureSpec) + ", height spec: " + MeasureSpec.toString(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);

        int xPadding = getPaddingLeft() + getPaddingRight();
        int width = w - xPadding;

        int yPadding = getPaddingTop() + getPaddingBottom();
        int height = h - yPadding;

        int maxDiameter;
        int paddingOffset;
        if (width > height) {
            maxDiameter = height;
            paddingOffset = yPadding;
        }
        else {
            maxDiameter = width;
            paddingOffset = xPadding;
        }
        if (mUseMeasuredSize)
            mGivenDiameter = maxDiameter;

        int oldDiameter = mDiameter;
        mDiameter = (int)mGivenDiameter;
        mPixelDiff = 0;

        // Surrounding box is bigger than given diameter - compute how much pixels to shift to center the circle
        if (maxDiameter > mDiameter) {
            mPixelDiff = (maxDiameter - mDiameter) + paddingOffset;
        }
        // Surrounding box is smaller than given diameter - reduce diameter, shift to center to circle
        else if (mDiameter > maxDiameter) {
            mDiameter = maxDiameter;
            mPixelDiff = paddingOffset;
        }
        // need to change size of bitmaps because the size changed, thus reset the cached source bitmap.
        if (oldDiameter != mDiameter)
            mSourceBitmap = null;

        if (BuildConfig.DEBUG)Log.v(TAG, "onSizeChanged width: " + width + ", height: " + height +
                ", diameter: " + mDiameter + ", pixel shift: " + mPixelDiff + ", diameter changed: " + (oldDiameter != mDiameter));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            setBackground(createStateListDrawable(mDiameter));
        }
        else {
            //noinspection deprecation
            setBackgroundDrawable(createStateListDrawable(mDiameter));
        }
    }

    private RectF circle = new RectF();
    private Paint circleColor = new Paint();
    private RectF shadow = new RectF();
    private Paint shadowColor = new Paint();

    @Override
    protected void onDraw(Canvas canvas) {

        Drawable drawable = getDrawable();

        if (drawable == null) {
            return;
        }
        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) {
            return;
        }
        if (BuildConfig.DEBUG) Log.v(TAG, "onDraw width: " + width + ", height: " + height + ", diameter: " + mDiameter);

        Bitmap b;
        // Handle TransitionView as follows: get the drawable of the last (topmost) layer, get the
        // bitmap and use it. Thus we don't have real transitions.
        if (drawable instanceof TransitionDrawable) {
            TransitionDrawable td = (TransitionDrawable)drawable;
            Drawable draw = td.getDrawable(td.getNumberOfLayers()-1);
            b = ((BitmapDrawable)draw).getBitmap();
        }
        else
            b = ((BitmapDrawable)drawable).getBitmap();

        int shadowWidth = 0;
        if (mStrokeWidth >= 2) {
            shadowWidth = (int)(mStrokeWidth / 2);
        }
        int strokeWidthSum = shadowWidth + (int)mStrokeWidth;

        // The reduce the real diameter by the overall stroke widths
        int diameter = mDiameter - strokeWidthSum * 2;
        if (b != mSourceBitmap) {
            mSourceBitmap = b;
            mCircleBitmap = createRoundImage(getScaledBitmap(b, diameter));
        }

        // shift rectangle to center of surrounding box
        int shiftPixels = mPixelDiff / 2;

        // Rectangle of the stroke. The pixels of the stroke go left and right of the imaginary
        // circle line. Thus adjust by stroke-width / 2.
        float left = shiftPixels + shadowWidth + mStrokeWidth/2;
        float right = left + diameter + mStrokeWidth;
        float upper = shiftPixels + shadowWidth + mStrokeWidth/2;
        float bottom = upper + diameter + mStrokeWidth;
//        Log.d(TAG, "left: " + left + ", right: " + right + ", upper: " + upper + ", bottom: " + bottom + ", mStroke: " + mStrokeWidth);

        // Compute the shadow oval: position the left and right line on the outside of the stroke,
        // thus only half of the shadow stoke is visible. On the bottom we see the full
        // show stroke, on the top it's completely hidden.
        //
        // Order is important: first the shadow oval, the the round bit image, the the stroke.
        if (shadowWidth > 0) {
            float leftShadow = left - mStrokeWidth/2;
            float rightShadow = right + mStrokeWidth/2;
            float upperShadow = upper + shadowWidth/2;
            float bottomShadow = bottom + mStrokeWidth/2 + shadowWidth/2;
            shadow.set(leftShadow, upperShadow, rightShadow, bottomShadow);
            shadowColor.setAntiAlias(true);
            shadowColor.setColor(mShadowColor);
            shadowColor.setStyle(Paint.Style.STROKE);
            shadowColor.setStrokeWidth(shadowWidth);
            canvas.drawArc(shadow, 0, 360, true, shadowColor);
        }
        canvas.drawBitmap(mCircleBitmap, shiftPixels + strokeWidthSum, shiftPixels + strokeWidthSum, null);
        if (mStrokeWidth > 0) {
            circle.set(left, upper, right, bottom);
            circleColor.setAntiAlias(true);
            circleColor.setColor(mStrokeColor);
            circleColor.setStyle(Paint.Style.STROKE);
            circleColor.setStrokeWidth(mStrokeWidth);
            canvas.drawArc(circle, 0, 360, true, circleColor);
        }
    }

    // This function assumes a square bitmap
    private Bitmap createRoundImage(Bitmap scaledImage) {
        Bitmap circleBitmap = Bitmap.createBitmap(scaledImage.getWidth(), scaledImage.getHeight(), Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(scaledImage, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(shader);

        Canvas c = new Canvas(circleBitmap);
        int radius = Math.min(scaledImage.getWidth(), scaledImage.getHeight()) / 2;
        c.drawCircle(scaledImage.getWidth() / 2, scaledImage.getHeight() / 2, radius, paint);

        return circleBitmap;
    }

    // Create a scaled square bitmap from the input bitmap
    private static Bitmap getScaledBitmap(Bitmap bmp, int diameter) {
        Bitmap scaledBmp;

        int bmpWidth = bmp.getWidth();
        int bmpHeight = bmp.getHeight();

        // Check size and scale bitmap to match the diameter
        if (bmpWidth != diameter || bmpHeight != diameter) {
            float smallest = Math.min(bmpWidth, bmpHeight);
            float factor = smallest / diameter;
            scaledBmp = Bitmap.createScaledBitmap(bmp, (int)(bmpWidth / factor), (int)(bmpHeight / factor), false);
        }
        else {
            scaledBmp = bmp;
        }
        return scaledBmp;
    }

    private StateListDrawable createStateListDrawable(int size) {
        StateListDrawable stateListDrawable = new StateListDrawable();

        if (!isInEditMode()) {
            OvalShape ovalShape = new OvalShape();
            ovalShape.resize(size, size);
            ShapeDrawable shapeDrawable = new ShapeDrawable(ovalShape);
            shapeDrawable.getPaint().setColor(mBackgroundColor);

            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, shapeDrawable);
            stateListDrawable.addState(new int[]{android.R.attr.state_focused}, shapeDrawable);
            stateListDrawable.addState(new int[]{}, null);
        }
        return stateListDrawable;
    }
}
