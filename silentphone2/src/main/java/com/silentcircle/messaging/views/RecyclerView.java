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
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Recycler view with some of the list view convenience methods.
 */
public class RecyclerView extends android.support.v7.widget.RecyclerView {

    private AdapterDataObserver mEmptyObserver = new AdapterDataObserver() {

        @Override
        public void onChanged() {
            updateEmptyStatus(getAdapter(), 0);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            updateEmptyStatus(getAdapter(), itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            updateEmptyStatus(getAdapter(), -itemCount);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            updateEmptyStatus(getAdapter(), 0);
        }

        private void updateEmptyStatus(Adapter<?> adapter, int itemCount) {
            /*
             * onItemRangeRemoved arrives before adapter item count is updated, use item count
             * to determine whether data set will be empty
             */
            RecyclerView.this.updateEmptyStatus(adapter, itemCount);
        }
    };

    private View mEmptyView;

    public RecyclerView(Context context) {
        this(context, null);
    }

    public RecyclerView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);
    }

    public RecyclerView(Context context, AttributeSet attributes, int defaultStyle) {
        super(context, attributes, defaultStyle);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        handleEmptyObserver(adapter);
        super.setAdapter(adapter);
        mEmptyObserver.onChanged();
    }

    @Override
    public void swapAdapter(Adapter adapter, boolean removeAndRecycleExistingViews) {
        handleEmptyObserver(adapter);
        super.swapAdapter(adapter, removeAndRecycleExistingViews);
        mEmptyObserver.onChanged();
    }

    public int getCount() {
        Adapter<?> adapter = getAdapter();
        return adapter != null ? adapter.getItemCount() : 0;
    }

    public void setEmptyView(View emptyView) {
        mEmptyView = emptyView;

        if (emptyView != null
                && emptyView.getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            emptyView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        updateEmptyStatus(getCount() == 0);
    }

    private void handleEmptyObserver(Adapter adapter) {
        final Adapter currentAdapter = getAdapter();
        if (currentAdapter != null) {
            currentAdapter.unregisterAdapterDataObserver(mEmptyObserver);
        }

        if (adapter != null) {
            adapter.registerAdapterDataObserver(mEmptyObserver);
        }
    }

    protected void updateEmptyStatus(@Nullable Adapter adapter, int itemCount) {
        updateEmptyStatus(adapter == null || ((adapter.getItemCount()) <= 0));

    }

    protected void updateEmptyStatus(boolean empty) {
        if (empty) {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(View.VISIBLE);
                setVisibility(View.GONE);
            }
            else {
                setVisibility(View.VISIBLE);
            }
        }
        else {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(View.GONE);
            }
            setVisibility(View.VISIBLE);
        }
    }
}
