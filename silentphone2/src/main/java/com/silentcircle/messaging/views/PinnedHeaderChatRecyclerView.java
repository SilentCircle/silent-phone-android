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
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.silentphone2.R;

/**
 * Recycler view with headers for item groups.
 */
public class PinnedHeaderChatRecyclerView extends ChatRecyclerView {

    /**
     * Adapter interface.  The list adapter must implement this interface.
     */
    public interface PinnedHeaderAdapter {

        /**
         * Returns the overall number of pinned headers, visible or not.
         */
        int getPinnedHeaderCount();

        /**
         * Creates or updates the pinned header view.
         */
        View getPinnedHeaderView(int viewIndex, View convertView, ViewGroup parent);

        /**
         * Configures the pinned headers to match the visible list items. The
         * adapter should call {@link com.silentcircle.common.list.PinnedHeaderListView#setHeaderPinnedAtTop},
         * {@link com.silentcircle.common.list.PinnedHeaderListView#setHeaderPinnedAtBottom},
         * {@link com.silentcircle.common.list.PinnedHeaderListView#setFadingHeader} or
         * {@link com.silentcircle.common.list.PinnedHeaderListView#setHeaderInvisible}, for each header that
         * needs to change its position or visibility.
         */
        void configurePinnedHeaders(PinnedHeaderChatRecyclerView recyclerViewView);

        /**
         * Returns the list position to scroll to if the pinned header is touched.
         * Return -1 if the list does not need to be scrolled.
         */
        int getScrollPositionForHeader(int viewIndex);
    }

    private static final int MAX_ALPHA = 255;
    private static final int TOP = 0;
    private static final int BOTTOM = 1;
    private static final int FADING = 2;

    private static final int DEFAULT_ANIMATION_DURATION = 20;

    private static final int DEFAULT_SMOOTH_SCROLL_DURATION = 100;

    private static final class PinnedHeader {
        View view;
        boolean visible;
        int y;
        int height;
        int alpha;
        int state;
        int position;

        boolean animating;
        boolean targetVisible;
        int sourceY;
        int targetY;
        long targetTime;
    }

    private PinnedHeaderAdapter mAdapter;
    private int mSize;
    private PinnedHeader[] mHeaders;
    private RectF mBounds = new RectF();
    private int mScrollState;

    private boolean mScrollToSectionOnHeaderTouch = false;
    private boolean mHeaderTouched = false;

    private int mAnimationDuration = DEFAULT_ANIMATION_DURATION;
    private boolean mAnimating;
    private long mAnimationTargetTime;
    private int mHeaderPaddingStart;
    private int mHeaderWidth;

    @SuppressWarnings("FieldCanBeLocal")
    private OnScrollListener mOnScrollListener = new OnScrollListener() {

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            ensureHeadersConfigured();
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                ensureHeadersConfigured();
            }
        }
    };

    private boolean mHeaderTouchesEnabled = false;

    public PinnedHeaderChatRecyclerView(Context context) {
        this(context, null);
    }

    public PinnedHeaderChatRecyclerView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);
    }

    public PinnedHeaderChatRecyclerView(Context context, AttributeSet attributes, int defaultStyle) {
        super(context, attributes, defaultStyle);
        setHeaderTouchesEnabled(false);
        addOnScrollListener(mOnScrollListener);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mHeaderPaddingStart = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) ?
                getPaddingStart() : getPaddingLeft();
        mHeaderWidth = r - l - mHeaderPaddingStart - getPaddingEnd();
    }

    @Override
    public void setAdapter(Adapter adapter) {
        mAdapter = (PinnedHeaderAdapter) adapter;
        super.setAdapter(adapter);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        ensureHeadersConfigured();
    }

    public int getPinnedHeaderHeight(int viewIndex) {
        ensurePinnedHeaderLayout(viewIndex);
        return mHeaders[viewIndex].view.getHeight();
    }

    /**
     * Set header to be pinned at the top.
     *
     * @param viewIndex index of the header view
     * @param y is position of the header in pixels.
     * @param animate true if the transition to the new coordinate should be animated
     */
    public void setHeaderPinnedAtTop(int viewIndex, int y, boolean animate) {
        ensurePinnedHeaderLayout(viewIndex);
        PinnedHeader header = mHeaders[viewIndex];
        header.visible = true;
        header.y = y;
        header.state = TOP;

        // TODO perhaps we should animate at the top as well
        header.animating = false;
    }

    /**
     * Set header to be pinned at the bottom.
     *
     * @param viewIndex index of the header view
     * @param y is position of the header in pixels.
     * @param animate true if the transition to the new coordinate should be animated
     */
    public void setHeaderPinnedAtBottom(int viewIndex, int y, boolean animate) {
        ensurePinnedHeaderLayout(viewIndex);
        PinnedHeader header = mHeaders[viewIndex];
        header.state = BOTTOM;
        if (header.animating) {
            header.targetTime = mAnimationTargetTime;
            header.sourceY = header.y;
            header.targetY = y;
        } else if (animate && (header.y != y || !header.visible)) {
            if (header.visible) {
                header.sourceY = header.y;
            } else {
                header.visible = true;
                header.sourceY = y + header.height;
            }
            header.animating = true;
            header.targetVisible = true;
            header.targetTime = mAnimationTargetTime;
            header.targetY = y;
        } else {
            header.visible = true;
            header.y = y;
        }
    }

    /**
     * Returns the sum of heights of headers pinned to the top.
     */
    public int getTotalTopPinnedHeaderHeight() {
        for (int i = mSize; --i >= 0;) {
            PinnedHeader header = mHeaders[i];
            if (header.visible && header.state == TOP) {
                return header.y + header.height;
            }
        }
        return 0;
    }

    /**
     * Set header to be pinned at the top of the first visible item.
     *
     * @param viewIndex index of the header view
     * @param position is position of the header in pixels.
     */
    public void setFadingHeader(int viewIndex, int position, boolean fade) {
        ensurePinnedHeaderLayout(viewIndex);

        View child = getChildAt(position - getFirstVisiblePosition());
        if (child == null) return;

        PinnedHeader header = mHeaders[viewIndex];
        header.visible = true;
        header.state = FADING;
        header.alpha = MAX_ALPHA;
        header.animating = false;

        int top = getTotalTopPinnedHeaderHeight();
        header.y = top;
        if (fade) {
            int bottom = child.getBottom() - top;
            int headerHeight = header.height;
            if (bottom < headerHeight) {
                int portion = bottom - headerHeight;
                header.alpha = MAX_ALPHA * (headerHeight + portion) / headerHeight;
                header.y = top + portion;
            }
        }
    }

    /**
     * Makes header invisible.
     *
     * @param viewIndex index of the header view
     * @param animate true if the transition to the new coordinate should be animated
     */
    public void setHeaderInvisible(int viewIndex, boolean animate) {
        PinnedHeader header = mHeaders[viewIndex];
        if (header.visible && (animate || header.animating) && header.state == BOTTOM) {
            header.sourceY = header.y;
            if (!header.animating) {
                header.visible = true;
                header.targetY = getBottom() + header.height;
            }
            header.animating = true;
            header.targetTime = mAnimationTargetTime;
            header.targetVisible = false;
        } else {
            header.visible = false;
        }
    }

    /**
     * Returns the list item position at the specified y coordinate.
     */
    public int getPositionAt(int y) {
        do {
            View view = findChildViewUnder(getPaddingLeft() + 1, y);
            int position = getChildAdapterPosition(view);
            if (position != -1) {
                return position;
            }
            // If position == -1, we must have hit a separator. Let's examine
            // a nearby pixel
            y--;
        } while (y > 0);
        return 0;
    }

    public void setHeaderTouchesEnabled(boolean enabled) {
        mHeaderTouchesEnabled = enabled;
    }

    private void ensureHeadersConfigured() {
        if (mAdapter != null) {
            int count = mAdapter.getPinnedHeaderCount();
            if (count != mSize) {
                mSize = count;
                if (mHeaders == null) {
                    mHeaders = new PinnedHeader[mSize];
                } else if (mHeaders.length < mSize) {
                    PinnedHeader[] headers = mHeaders;
                    mHeaders = new PinnedHeader[mSize];
                    System.arraycopy(headers, 0, mHeaders, 0, headers.length);
                }
            }

            for (int i = 0; i < mSize; i++) {
                if (mHeaders[i] == null) {
                    mHeaders[i] = new PinnedHeader();
                }
                View view = mHeaders[i].view = mAdapter.getPinnedHeaderView(i, mHeaders[i].view,
                        this);
                if (view != null) {
                    Integer position = (Integer) view.getTag(R.id.view_position);
                    if (position != null) {
                        mHeaders[i].position = position;
                    }
                }
            }

            mAnimationTargetTime = System.currentTimeMillis() + mAnimationDuration;
            mAdapter.configurePinnedHeaders(this);
            invalidateIfAnimating();
        }
    }

    private void ensurePinnedHeaderLayout(int viewIndex) {
        View view = mHeaders[viewIndex].view;
        if (view.isLayoutRequested()) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            int widthSpec;
            int heightSpec;

            if (layoutParams != null && layoutParams.width > 0) {
                widthSpec = View.MeasureSpec
                        .makeMeasureSpec(layoutParams.width, View.MeasureSpec.EXACTLY);
            } else {
                widthSpec = View.MeasureSpec
                        .makeMeasureSpec(mHeaderWidth, View.MeasureSpec.EXACTLY);
            }

            if (layoutParams != null && layoutParams.height > 0) {
                heightSpec = View.MeasureSpec
                        .makeMeasureSpec(layoutParams.height, View.MeasureSpec.EXACTLY);
            } else {
                heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            }
            view.measure(widthSpec, heightSpec);
            int height = view.getMeasuredHeight();
            mHeaders[viewIndex].height = height;
            view.layout(0, 0, view.getMeasuredWidth(), height);
        }
    }

    private void invalidateIfAnimating() {
        mAnimating = false;
        for (int i = 0; i < mSize; i++) {
            if (mHeaders[i].animating) {
                mAnimating = true;
                invalidate();
                return;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mHeaderTouched = false;
        if (super.onInterceptTouchEvent(ev)) {
            return true;
        }

        if (mScrollState == SCROLL_STATE_IDLE) {
            final int y = (int)ev.getY();
            final int x = (int)ev.getX();
            for (int i = mSize; --i >= 0;) {
                PinnedHeader header = mHeaders[i];
                // For RTL layouts, this also takes into account that the scrollbar is on the left
                // side.
                final int padding = getPaddingLeft();
                if (header.visible && header.y <= y && header.y + header.height > y &&
                        x >= padding && padding + header.view.getWidth() >= x) {
                    mHeaderTouched = true;
                    if (mScrollToSectionOnHeaderTouch &&
                            ev.getAction() == MotionEvent.ACTION_DOWN) {
                        return false; // TODO smoothScrollToPartition(i);
                    } else {
                        return !mHeaderTouchesEnabled;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mHeaderTouched && !mHeaderTouchesEnabled) {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                mHeaderTouched = false;
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean more = false;
        boolean doDraw = true;
        Integer position = (Integer) child.getTag(R.id.view_position);
        if (position != null && isHeaderPosition(position)) {
            for (int i = 0; i < mSize; i++) {
                PinnedHeader header = mHeaders[i];
                if (header.position == position) {
                    doDraw = !header.visible || header.view == null || header.view.getWidth() == 0;
                    break;
                }
            }
        }
        if (doDraw) {
            more = super.drawChild(canvas, child, drawingTime);
        }
        return more;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        long currentTime = mAnimating ? System.currentTimeMillis() : 0;

        int top = 0;
        int right = 0;
        int bottom = getBottom();
        boolean hasVisibleHeaders = false;
        for (int i = 0; i < mSize; i++) {
            PinnedHeader header = mHeaders[i];
            if (header.visible) {
                hasVisibleHeaders = true;
                if (header.state == BOTTOM && header.y < bottom) {
                    bottom = header.y;
                } else if (header.state == TOP || header.state == FADING) {
                    int newTop = header.y + header.height;
                    if (newTop > top) {
                        top = newTop;
                    }
                }
            }
        }

        if (hasVisibleHeaders) {
            canvas.save();
        }

        super.dispatchDraw(canvas);

        if (hasVisibleHeaders) {
            canvas.restore();

            // If the first item is visible and if it has a positive top that is greater than the
            // first header's assigned y-value, use that for the first header's y value. This way,
            // the header inherits any padding applied to the list view.
            if (mSize > 0 && getFirstVisiblePosition() == 0) {
                View firstChild = getChildAt(0);
                PinnedHeader firstHeader = mHeaders[0];

                if (firstHeader != null) {
                    int firstHeaderTop = firstChild != null ? firstChild.getTop() : 0;
                    firstHeader.y = Math.max(firstHeader.y, firstHeaderTop);
                }
            }

            // First draw top headers, then the bottom ones to handle the Z axis correctly
            for (int i = mSize; --i >= 0;) {
                PinnedHeader header = mHeaders[i];
                if (header.visible && (header.state == TOP || header.state == FADING)) {
                    drawHeader(canvas, header, currentTime);
                }
            }

            for (int i = 0; i < mSize; i++) {
                PinnedHeader header = mHeaders[i];
                if (header.visible && header.state == BOTTOM) {
                    drawHeader(canvas, header, currentTime);
                }
            }
        }

        invalidateIfAnimating();
    }

    private void drawHeader(Canvas canvas, PinnedHeader header, long currentTime) {
        if (header.animating) {
            int timeLeft = (int)(header.targetTime - currentTime);
            if (timeLeft <= 0) {
                header.y = header.targetY;
                header.visible = header.targetVisible;
                header.animating = false;
            } else {
                header.y = header.targetY + (header.sourceY - header.targetY) * timeLeft
                        / mAnimationDuration;
            }
        }
        if (header.visible && header.view.getWidth() > 0) {
            View view = header.view;
            int saveCount = canvas.save();
            int translateX = ViewUtil.isViewLayoutRtl(this) ?
                    getWidth() - mHeaderPaddingStart - view.getWidth() :
                    mHeaderPaddingStart;
            canvas.translate(translateX, header.y);
            if (header.state == FADING) {
                mBounds.set(0, 0, view.getWidth(), view.getHeight());
                canvas.saveLayerAlpha(mBounds, header.alpha, Canvas.ALL_SAVE_FLAG);
            }
            view.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }
}
