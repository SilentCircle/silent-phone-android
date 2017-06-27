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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.SeekBar;

/**
 * Vertical seek bar widget.
 */
public class VerticalSeekBar extends SeekBar {

    private int mThumbOffset;
    private int mPaddingLeft;
    private int mPaddingRight;
    private int mPaddingTop;
    private int mPaddingBottom;

    /**
     * Used in case the rendered height is not available
     * ({@link #getHeight() == 0})
     */
    private int mHeight;

    private OnVerticalSeekBarChangeListener mOnSeekBarChangeListener;

    public interface OnVerticalSeekBarChangeListener extends OnSeekBarChangeListener {

        void onPositionChanged(SeekBar seekBar, int progress, int verticalPosition);

        void onVisibilityChanged(int visibility);
    }

    public VerticalSeekBar(Context context) {
        this(context, null);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mThumbOffset = getThumbOffset();
        mPaddingLeft = getPaddingLeft();
        mPaddingTop = getPaddingTop();
        mPaddingRight = getPaddingRight();
        mPaddingBottom = getPaddingBottom();

        TypedArray ta = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.layout_height });
        if (ta != null) {
            mHeight = ta.getDimensionPixelSize(0, ViewGroup.LayoutParams.MATCH_PARENT);
            ta.recycle();
        }
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();

        Drawable thumb = getThumb();
        if (thumb != null) {
            thumb.jumpToCurrentState();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        if (!result) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                int i = getMax() - (int) (getMax() * event.getY() / getHeight());
                setProgress(i);
                onSizeChanged(getWidth(), getHeight(), 0, 0);
                if (event.getAction() == MotionEvent.ACTION_UP && mOnSeekBarChangeListener != null) {
                    mOnSeekBarChangeListener.onStopTrackingTouch(this);
                }
                break;
        }
        return true;
    }

    public void setOnVerticalSeekBarChangeListener(OnVerticalSeekBarChangeListener l) {
        mOnSeekBarChangeListener = l;
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mOnSeekBarChangeListener != null) {
            mOnSeekBarChangeListener.onVisibilityChanged(visibility);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThumbAndTrackPos(w, h);
    }

    @Override
    public synchronized void setProgress(int progress) {
        super.setProgress(progress);
        setDescription(getHeight(), getThumb(), getScale());
        updateThumbAndTrackPos(getWidth(), getHeight());
    }

    protected void onDraw(Canvas canvas) {
        drawTrack(canvas);
        drawThumb(canvas);
    }

    protected void drawTrack(Canvas canvas) {
        final Drawable track = getProgressDrawable();
        if (track != null) {
            canvas.save();
            canvas.translate(mPaddingLeft, mPaddingTop + mThumbOffset);
            track.draw(canvas);
            canvas.restore();
        }
    }

    /**
     * Draw the thumb.
     */
    protected void drawThumb(Canvas canvas) {
        final Drawable thumbDrawable = getThumb();
        if (thumbDrawable != null) {
            canvas.save();
            canvas.translate(mPaddingLeft, mPaddingTop);
            thumbDrawable.draw(canvas);
            canvas.restore();
        }
    }

    private void updateThumbAndTrackPos(int w, int h) {
        final int paddedHeight = h - mPaddingTop - mPaddingBottom;
        final int paddedWidth = w - mPaddingLeft - mPaddingRight;
        final Drawable track = getProgressDrawable();
        final Drawable thumb = getThumb();

        // The max height does not incorporate padding, whereas the height
        // parameter does.
        final int trackHeight = paddedHeight;;
        final int trackWidth = paddedWidth;;
        final int thumbHeight = thumb == null ? 0 : thumb.getIntrinsicHeight();
        final int thumbWidth = thumb == null ? 0 : thumb.getIntrinsicWidth();

        // Apply offset to whichever item is taller.
        final int trackOffset;
        final int thumbOffset;

        if (thumbWidth > trackWidth) {
            final int offsetWidth = (paddedWidth - thumbWidth) / 2;
            trackOffset = offsetWidth + (thumbHeight - trackWidth) / 2;
            thumbOffset = offsetWidth;
        } else {
            final int offsetWidth = (paddedWidth - trackWidth) / 2;
            trackOffset = offsetWidth;
            thumbOffset = offsetWidth + (trackWidth - thumbWidth) / 2;
        }

        if (track != null) {
            track.setBounds(trackOffset, 0, trackOffset + trackWidth,
                    trackHeight - (thumb != null ? thumb.getIntrinsicHeight() : 0));
        }

        if (thumb != null) {
            setThumbPos(h, thumb, getScale(), thumbOffset);
        }

        setDescription(h, thumb, getScale());
    }

    private float getScale() {
        final int max = getMax();
        return max > 0 ? getProgress() / (float) max : 0;
    }

    /**
     * Updates the thumb drawable bounds.
     *
     * @param h Height of the view, including padding
     * @param thumb Drawable used for the thumb
     * @param scale Current progress between 0 and 1
     * @param offset Horizontal offset for centering. If set to
     *            {@link Integer#MIN_VALUE}, the current offset will be used.
     */
    private void setThumbPos(int h, Drawable thumb, float scale, int offset) {
        int available = h - mPaddingTop - mPaddingBottom;
        final int thumbWidth = thumb.getIntrinsicWidth();
        final int thumbHeight = thumb.getIntrinsicHeight();
        available -= thumbHeight;

        final int thumbPos = (int) (scale * available + 0.5f);

        final int left, right;
        if (offset == Integer.MIN_VALUE) {
            final Rect oldBounds = thumb.getBounds();
            left = oldBounds.left;
            right = oldBounds.right;
        } else {
            left = offset;
            right = offset + thumbHeight;
        }

        final int top = available - thumbPos;
        final int bottom = top + thumbWidth;

        thumb.setBounds(left, top, right, bottom);
    }

    private void setDescription(int h, Drawable thumb, float scale) {
        if (thumb == null) {
            return;
        }

        if (h == 0) {
            h = mHeight;
        }

        int available = h - mPaddingTop - mPaddingBottom;
        final int thumbHeight = thumb.getIntrinsicHeight();
        available -= thumbHeight;
        final int thumbPos = (int) (scale * available + 0.5f);

        if (mOnSeekBarChangeListener != null) {
            int topMargin = available - thumbPos + thumbHeight / 2;
            mOnSeekBarChangeListener.onPositionChanged(this, getProgress(), topMargin);
        }
    }

}
