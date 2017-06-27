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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.silentphone2.R;

/**
 * Recycler view with avatars for items.
 */
public class AvatarChatRecyclerView extends PinnedHeaderChatRecyclerView {

    public interface PinnedAvatarAdapter {

        int getPinnedAvatarCount();

        View getPinnedAvatarView(int viewIndex, View convertView, ViewGroup parent);

        void configurePinnedAvatars(AvatarChatRecyclerView listView);
    }

    public static final class PinnedAvatar {
        View view;
        boolean visible;
        int height;
        int y;
    }

    protected PinnedAvatar[] mAvatars;

    protected int mSize;
    protected int mAvatarPaddingStart;
    protected int mAvatarPaddingTop;
    protected int mAvatarPaddingFirstMessageTop;
    protected int mAvatarPaddingBottom;
    protected int mAvatarWidth;

    private PinnedAvatarAdapter mAdapter;

    private OnScrollListener mOnScrollListener = new OnScrollListener() {

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            ensureAvatarsConfigured();
            super.onScrolled(recyclerView, dx, dy);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                ensureAvatarsConfigured();
            }
            super.onScrollStateChanged(recyclerView, newState);
        }
    };

    public AvatarChatRecyclerView(Context context) {
        this(context, null);
    }

    public AvatarChatRecyclerView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);
    }

    public AvatarChatRecyclerView(Context context, AttributeSet attributes, int defaultStyle) {
        super(context, attributes, defaultStyle);
        addOnScrollListener(mOnScrollListener);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        Resources resources = getContext().getResources();
        mAvatarPaddingStart = (int) resources.getDimension(R.dimen.messaging_message_avatar_padding_start);
        mAvatarWidth = r - l - mAvatarPaddingStart - getPaddingEnd();
        mAvatarPaddingTop = (int) resources.getDimension(R.dimen.messaging_message_avatar_padding_top);
        mAvatarPaddingFirstMessageTop = (int) resources.getDimension(R.dimen.messaging_message_avatar_first_message_padding_top);
        mAvatarPaddingBottom = (int) resources.getDimension(R.dimen.messaging_message_avatar_padding_bottom);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        mAdapter = (PinnedAvatarAdapter) adapter;
        super.setAdapter(adapter);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateAvatars();
        ensureAvatarsConfigured();
    }

    /**
     * Pins avatar to a given group
     *
     * @param viewIndex index of the avatar view
     * @param sectionPosition index of first item in group
     * @param sectionLength length (-1) of group
     */
    public void setAvatarPinnedAtItem(int viewIndex, int sectionPosition, int sectionLength) {

        if (mAvatars.length <= viewIndex) {
            return;
        }

        ensurePinnedAvatarLayout(viewIndex);

        PinnedAvatar avatar = mAvatars[viewIndex];

        // middle
        int y = mAvatarPaddingTop;
        int firstVisiblePosition = getFirstVisiblePosition();
        // top
        if (firstVisiblePosition <= sectionPosition) {
            View view = getChildAt(sectionPosition - firstVisiblePosition);
            if (view != null) {
                y = view.getTop() + mAvatarPaddingFirstMessageTop;
                // slide only for groups with more than one item
                if (sectionLength > 0) {
                    y = Math.max(y, mAvatarPaddingTop);
                }
            }
        }
        // bottom
        else if (firstVisiblePosition >= (sectionPosition + sectionLength)) {
            View view = getChildAt(sectionPosition + sectionLength - firstVisiblePosition);
            if (view != null) {
                y = Math.min(view.getBottom() - avatar.height - mAvatarPaddingBottom, mAvatarPaddingTop);
            }
        }

        avatar.visible = true;
        avatar.y = y;
    }

    /**
     * Makes avatar invisible.
     *
     * @param viewIndex index of the avatar view
     * @param animate true if the transition to the new coordinate should be animated
     */
    public void setAvatarInvisible(int viewIndex, boolean animate) {
        if (mAvatars.length <= viewIndex) {
            return;
        }

        PinnedAvatar avatar = mAvatars[viewIndex];
        avatar.visible = false;
    }

    public void ensureAvatarsConfigured() {
        if (mAdapter != null) {
            int count = mAdapter.getPinnedAvatarCount();
            if (count != mSize) {
                mSize = count;
                if (mAvatars == null) {
                    mAvatars = new PinnedAvatar[mSize];
                } else if (mAvatars.length < mSize) {
                    PinnedAvatar[] avatars = mAvatars;
                    mAvatars = new PinnedAvatar[mSize];
                    System.arraycopy(avatars, 0, mAvatars, 0, avatars.length);
                }
            }

            for (int i = 0; i < mSize; i++) {
                if (mAvatars[i] == null) {
                    mAvatars[i] = new PinnedAvatar();
                }
                mAvatars[i].view = mAdapter.getPinnedAvatarView(i, mAvatars[i].view, AvatarChatRecyclerView.this);
            }

            mAdapter.configurePinnedAvatars(AvatarChatRecyclerView.this);
        }
    }

    private void ensurePinnedAvatarLayout(int viewIndex) {
        if (mAvatars.length <= viewIndex) {
            return;
        }

        View view = mAvatars[viewIndex].view;
        if (view == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        int widthSpec;
        int heightSpec;

        if (layoutParams != null && layoutParams.width > 0) {
            widthSpec = View.MeasureSpec
                    .makeMeasureSpec(layoutParams.width, View.MeasureSpec.EXACTLY);
        } else {
            widthSpec = View.MeasureSpec
                    .makeMeasureSpec(mAvatarWidth, View.MeasureSpec.EXACTLY);
        }

        if (layoutParams != null && layoutParams.height > 0) {
            heightSpec = View.MeasureSpec
                    .makeMeasureSpec(layoutParams.height, View.MeasureSpec.EXACTLY);
        } else {
            heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        view.measure(widthSpec, heightSpec);
        int height = view.getMeasuredHeight();
        mAvatars[viewIndex].height = height;
        view.layout(0, 0, view.getMeasuredWidth(), height);
    }


    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean hasVisibleAvatars = false;
        for (int i = 0; i < mSize; i++) {
            PinnedAvatar avatar = mAvatars[i];
            if (avatar.visible) {
                hasVisibleAvatars = true;
                break;
            }
        }

        if (hasVisibleAvatars) {
            canvas.save();
        }

        super.dispatchDraw(canvas);

        if (hasVisibleAvatars) {
            canvas.restore();

            for (int i = 0; i < mSize; i++) {
                PinnedAvatar avatar = mAvatars[i];
                if (avatar.visible) {
                    drawAvatar(canvas, avatar);
                }
            }
        }
    }

    private void drawAvatar(Canvas canvas, PinnedAvatar avatar) {

        if (avatar.visible) {
            View view = avatar.view;
            int saveCount = canvas.save();
            int translateX = ViewUtil.isViewLayoutRtl(this) ?
                    getWidth() - mAvatarPaddingStart - view.getWidth() :
                    mAvatarPaddingStart;
            canvas.translate(translateX, avatar.y);
            view.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    private void invalidateAvatars() {
        for (int i = 0; i < mSize; i++) {
            PinnedAvatar avatar = mAvatars[i];
            if (avatar.visible && avatar.view != null) {
                avatar.view.invalidate();
            }
        }
    }

}
