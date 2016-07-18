/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.silentphone2.list;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

public class RemoveView extends FrameLayout {

    DragDropController mDragDropController;
    TextView mRemoveText;
    ImageView mRemoveIcon;
    int mUnHighlightedColor;
    int mHighlightedColor;
    Drawable mRemoveDrawable;

    public RemoveView(Context context) {
      super(context);
    }

    public RemoveView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public RemoveView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @SuppressWarnings("deprecation")
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRemoveText = (TextView) findViewById(R.id.remove_view_text);
        mRemoveIcon = (ImageView) findViewById(R.id.remove_view_icon);
        final Resources r = getResources();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mUnHighlightedColor = r.getColor(R.color.remove_text_color);
            mHighlightedColor = r.getColor(R.color.remove_highlighted_text_color);
            mRemoveDrawable = r.getDrawable(R.drawable.ic_close_dk);
        }
        else {
            mUnHighlightedColor = r.getColor(R.color.remove_text_color, null);
            mHighlightedColor = r.getColor(R.color.remove_highlighted_text_color, null);
            mRemoveDrawable = r.getDrawable(R.drawable.ic_close_dk, null);
        }
    }

    public void setDragDropController(DragDropController controller) {
        mDragDropController = controller;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        final int action = event.getAction();
        switch (action) {
            case DragEvent.ACTION_DRAG_ENTERED:
                setAppearanceHighlighted();
                break;
            case DragEvent.ACTION_DRAG_EXITED:
                setAppearanceNormal();
                break;
            case DragEvent.ACTION_DRAG_LOCATION:
                if (mDragDropController != null) {
                    mDragDropController.handleDragHovered(this, (int) event.getX(),
                            (int) event.getY());
                }
                break;
            case DragEvent.ACTION_DROP:
                if (mDragDropController != null) {
                    mDragDropController.handleDragFinished((int) event.getX(), (int) event.getY(),
                            true);
                }
                setAppearanceNormal();
                break;
        }
        return true;
    }

    private void setAppearanceNormal() {
        mRemoveText.setTextColor(mUnHighlightedColor);
        mRemoveIcon.setColorFilter(mUnHighlightedColor);
        invalidate();
    }

    private void setAppearanceHighlighted() {
        mRemoveText.setTextColor(mHighlightedColor);
        mRemoveIcon.setColorFilter(mHighlightedColor);
        invalidate();
    }
}
