/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.views.adapters;

import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * Pinned header decoration.
 * Based on https://stackoverflow.com/questions/32949971/how-can-i-make-sticky-headers-in-recyclerview-without-external-lib
 */
public class PinnedItemDecoration extends RecyclerView.ItemDecoration {

    @SuppressWarnings("WeakerAccess")
    public interface PinnedHeaderAdapter {

        int getPinnedHeaderCount();

        int getHeaderPositionForItem(int position);

        boolean isHeaderPosition(int position);

        Object getHeaderData(int headerPosition);

        View getHeaderView(View convertView, ViewGroup parent, Object data);

        boolean isSectionHeaderVisible(int headerPosition, int firstVisiblePosition,
            int lastVisiblePosition);
    }

    private static final int MAX_ALPHA = 255;

    private Map<Object, View> mPinnedViews = new HashMap<>();

    private PinnedHeaderAdapter mAdapter;

    public PinnedItemDecoration(@NonNull PinnedHeaderAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        super.onDrawOver(canvas, parent, state);

        final View firstVisibleView = parent.getChildAt(0);
        if (firstVisibleView == null) {
            return;
        }

        int position = parent.getChildAdapterPosition(firstVisibleView);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        int currentHeaderPosition = mAdapter.getHeaderPositionForItem(position);
        final View currentHeader = getHeaderView(currentHeaderPosition, parent);

        int offset = 0;
        int alpha = MAX_ALPHA;
        final View nextHeader = findFirstOverlappingView(parent, currentHeader.getBottom());
        if (nextHeader == null) {
            return;
        }
        int nextHeaderPosition = parent.getChildAdapterPosition(nextHeader);
        // avoid drawing header twice - header version and its list item version
        if (currentHeaderPosition == nextHeaderPosition) {
            return;
        }

        if (mAdapter.isHeaderPosition(nextHeaderPosition)) {
            offset = nextHeader.getTop() - currentHeader.getHeight();
            alpha = MAX_ALPHA * (currentHeader.getHeight()
                    + (nextHeader.getTop() - currentHeader.getHeight())) / currentHeader.getHeight();
        }
        drawHeader(canvas, currentHeader, offset, alpha);
    }

    private View getHeaderView(int headerPosition, RecyclerView parent) {
        Object data = mAdapter.getHeaderData(headerPosition);
        View header = mPinnedViews.get(data);
        if (header == null) {
            header = mAdapter.getHeaderView(header, parent, data);
            layoutHeader(parent, header);
            mPinnedViews.put(data, header);
            header.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        return header;
    }

    private void drawHeader(Canvas canvas, View header, int offset, int alpha) {
        int saveCount = canvas.save();
        header.setAlpha((float) alpha / 255.f);
        canvas.translate(0, offset);
        header.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    private View findFirstOverlappingView(RecyclerView parent, int contactPoint) {
        View childInContact = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getBottom() > contactPoint) {
                if (child.getTop() <= contactPoint) {
                    childInContact = child;
                    break;
                }
            }
        }
        return childInContact;
    }

    private void layoutHeader(ViewGroup parent, View view) {

        int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

        ViewGroup.LayoutParams params = view.getLayoutParams();
        int childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec,
                parent.getPaddingLeft() + parent.getPaddingRight(), params.width);
        int childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec,
                parent.getPaddingTop() + parent.getPaddingBottom(), params.height);

        view.measure(childWidthSpec, childHeightSpec);

        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

}
