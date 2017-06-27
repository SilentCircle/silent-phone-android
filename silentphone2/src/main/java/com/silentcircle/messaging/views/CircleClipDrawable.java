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

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class CircleClipDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float strokeWidth;
    private final int innerColor;
    private final int outerColor;
    private final Drawable wrapped;
    private final Path circle = new Path();

    public CircleClipDrawable(Drawable wrapped) {
        this(wrapped, 0, 0, 0);
    }

    public CircleClipDrawable(Drawable wrapped, int innerColor, int outerColor, float strokeWidth) {
        this.wrapped = wrapped;
        this.innerColor = innerColor;
        this.outerColor = outerColor;
        this.strokeWidth = strokeWidth;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
    }

    public CircleClipDrawable(Drawable wrapped, Resources resources, int innerColorResourceID, int outerColorResourceID, int strokeWidthDimenResourceID) {
        this(wrapped, innerColorResourceID != 0 ? resources.getColor(innerColorResourceID) : 0, outerColorResourceID != 0 ? resources.getColor(outerColorResourceID) : 0, strokeWidthDimenResourceID != 0 ? resources.getDimension(strokeWidthDimenResourceID) : 0);
    }

    @Override
    public void draw(Canvas canvas) {

        circle.reset();
        Rect bounds = extractBounds(canvas);

        if (bounds.isEmpty()) {
            return;
        }

        float x = bounds.exactCenterX();
        float y = bounds.exactCenterY();
        float r = x < y ? x : y;
        float dr = strokeWidth / 2;

        r -= dr;
        paint.setColor(outerColor);
        canvas.drawCircle(x, y, r, paint);

        r -= dr;
        circle.addCircle(x, y, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(circle);
        wrapped.draw(canvas);
        canvas.restore();

        r -= dr;
        paint.setColor(innerColor);
        canvas.drawCircle(x, y, r, paint);

    }

    private Rect extractBounds(Canvas canvas) {

        Rect bounds = getBounds();

        if (bounds.isEmpty()) {
            canvas.getClipBounds(bounds);
        }

        if (bounds.isEmpty()) {
            bounds.set(0, 0, canvas.getWidth(), canvas.getHeight());
        }

        if (bounds.isEmpty()) {
            bounds.set(0, 0, wrapped.getIntrinsicWidth(), wrapped.getIntrinsicHeight());
        }

        if (bounds.isEmpty()) {
            bounds.set(wrapped.getBounds());
        } else {
            wrapped.setBounds(bounds);
        }

        return bounds;

    }

    @Override
    public int getIntrinsicHeight() {
        return wrapped.getIntrinsicHeight();
    }

    @Override
    public int getIntrinsicWidth() {
        return wrapped.getIntrinsicWidth();
    }

    @Override
    public int getOpacity() {
        return wrapped.getOpacity();
    }

    @Override
    public void setAlpha(int alpha) {
        wrapped.setAlpha(alpha);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        wrapped.setBounds(left, top, right, bottom);
    }

    @Override
    public void setBounds(Rect bounds) {
        super.setBounds(bounds);
        wrapped.setBounds(bounds);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        wrapped.setColorFilter(cf);
    }

}

