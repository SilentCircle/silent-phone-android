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
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Checkable;

import com.silentcircle.messaging.views.adapters.FooterModelViewAdapter;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;
import com.silentcircle.messaging.views.adapters.MultiSelectModelViewAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class ChatRecyclerView extends com.silentcircle.messaging.views.RecyclerView
        implements MultiSelectModelViewAdapter.OnItemClickListener {

    protected class MultiChoiceModeCallback implements com.silentcircle.messaging.views.MultiChoiceModeListener {

        protected ActionMode mCurrentMode;
        protected final com.silentcircle.messaging.views.MultiChoiceModeListener mDelegate;

        public MultiChoiceModeCallback(com.silentcircle.messaging.views.MultiChoiceModeListener delegate) {
            mDelegate = delegate;
        }

        public boolean hasActionMode() {
            return mCurrentMode != null;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            Set<String> checkedItems = getCheckedIds();
            final List<String> positionsList = new ArrayList<>();
            Iterator<String> iterator = checkedItems.iterator();
            while (iterator.hasNext()) {
                positionsList.add(iterator.next());
            }

            int i = 0;
            String[] positions = new String[positionsList.size()];
            for (String position : positionsList) {
                positions[i++] = position;
            }

            performAction(item.getItemId(), positions);
            return mDelegate.onActionItemClicked(mode, item);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mCurrentMode = mode;
            clearChoices();
            return mDelegate.onCreateActionMode(mode, menu);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mDelegate.onDestroyActionMode(mode);
            mCurrentMode = null;
            ChatRecyclerView.this.invalidate();
            clearChoices();
            requestLayout();
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long itemId, boolean checked) {
            mDelegate.onItemCheckedStateChanged(mode, position, itemId, checked);
            if (!hasCheckedItems()) {
                mode.finish();
            }
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return mDelegate.onPrepareActionMode(mode, menu);
        }

        @Override
        public void performAction(final int menuActionId, final int... positions) {
            mDelegate.performAction(menuActionId, positions);
        }

        @Override
        public void performAction(final int menuActionId, final String... positions) {
            mDelegate.performAction(menuActionId, positions);
        }

        public void setItemCheckedState(String itemId, int position, boolean checked) {
            setItemChecked(itemId, position, checked);
            onItemCheckedStateChanged(mCurrentMode, position, 0, checked);
        }

        public boolean toggleItemCheckedState(String itemId, int position) {
            boolean checked = !isItemChecked(itemId);
            setItemCheckedState(itemId, position, checked);
            return checked;
        }

        public void exitActionMode() {
            if (mCurrentMode != null) {
                mCurrentMode.finish();
            }
        }
    }

    protected View.OnClickListener mClickThroughListener;
    protected MultiChoiceModeCallback mMultiChoiceModeCallback;
    protected Set<String> mCheckIds = new HashSet<>();
    protected boolean mIsScrolling;

    private OnScrollListener mOnScrollListener = new OnScrollListener() {

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            mIsScrolling = newState != SCROLL_STATE_IDLE;
        }

    };

    private MultiSelectModelViewAdapter mAdapter;

    public ChatRecyclerView(Context context) {
        this(context, null);
    }

    public ChatRecyclerView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);
    }

    public ChatRecyclerView(Context context, AttributeSet attributes, int defaultStyle) {
        super(context, attributes, defaultStyle);
        addOnScrollListener(mOnScrollListener);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);

        mAdapter = (MultiSelectModelViewAdapter) adapter;
        if (mAdapter != null) {
            mAdapter.setOnItemClickListener(this);
            mAdapter.setCheckedIds(mCheckIds);
        }
    }

    public void setClickThroughListener(View.OnClickListener listener) {
        mClickThroughListener = listener;
    }

    public void setMultiChoiceModeListener(com.silentcircle.messaging.views.MultiChoiceModeListener listener) {
        mMultiChoiceModeCallback = new MultiChoiceModeCallback(listener);
    }

    public void requestPositionToScreen(int position, boolean smoothScroll) {
        scrollToPosition(position);
        requestLayout();
    }

    public void requestPositionToScreen(int position, boolean smoothScroll, float offset,
            boolean forceScroll) {
        scrollToPosition(position);
        requestLayout();
    }

    public int getCount() {
        return mAdapter != null ? mAdapter.getItemCount() : 0;
    }

    public Object getItemAtPosition(int position) {
        return mAdapter != null ? mAdapter.getItem(position) : null;
    }

    public boolean hasMultipleCheckedItems() {
        return getCheckedItemCount() > 1;
    }

    public void setItemChecked(String itemId, int position, boolean value) {
        if (!isHeaderPosition(position)) {
            if (value) {
                mCheckIds.add(itemId);
            }
            else {
                mCheckIds.remove(itemId);
            }

            if (mAdapter != null) {
                mAdapter.notifyItemChanged(position);
            }
        }
    }

    public void clearItemChecked(String itemId, int position) {
        if (!isHeaderPosition(position)) {
            if (mMultiChoiceModeCallback == null || !mMultiChoiceModeCallback.hasActionMode()) {
                return;
            }

            mMultiChoiceModeCallback.toggleItemCheckedState(itemId, position);
        }
    }

    public boolean isItemChecked(String eventId) {
        return mCheckIds.contains(eventId);
    }

    public boolean hasCheckedItems() {
        return getCheckedItemCount() > 0;
    }

    public int getCheckedItemCount() {
        return mCheckIds.size();
    }

    public Set<String> getCheckedIds() {
        return mCheckIds;
    }

    public void exitActionMode() {
        if (mMultiChoiceModeCallback != null) {
            mMultiChoiceModeCallback.exitActionMode();
        }
    }

    public int getFirstVisiblePosition() {
        return ((LinearLayoutManager) getLayoutManager()).findFirstVisibleItemPosition();
    }

    public int getLastVisiblePosition() {
        return ((LinearLayoutManager) getLayoutManager()).findLastVisibleItemPosition();
    }

    public void clearChoices() {
        mCheckIds.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        invalidate();
    }

    @Override
    public void onItemClick(@NonNull View view, int position, @NonNull Object item, String itemId) {
        if (mIsScrolling) {
            return;
        }

        // mCheckedStateListener.onClick(this, view, position, itemId, item);
        if (isHeaderPosition(position)) {
            return;
        }

        if (mMultiChoiceModeCallback == null || !mMultiChoiceModeCallback.hasActionMode()) {
            if (mClickThroughListener != null) {
                mClickThroughListener.onClick(view);
            }
            return;
        }

        boolean checked = mMultiChoiceModeCallback.toggleItemCheckedState(itemId, position);

        if (view instanceof HasChoiceMode) {
            ((HasChoiceMode) view).setInChoiceMode(true);
        }

        if (view instanceof Checkable) {
            ((Checkable) view).setChecked(checked);
        }
    }

    @Override
    public void onItemLongClick(@NonNull View view, int position, @NonNull Object item, String itemId) {
        if (mIsScrolling) {
            return;
        }

        // long click on a header does not do anything
        if (isHeaderPosition(position)) {
            return;
        }

        if (mMultiChoiceModeCallback == null) {
            return;
        }

        if (mMultiChoiceModeCallback.hasActionMode()) {
            return;
        }

        Context context = getContext();
        if (context instanceof AppCompatActivity) {
            ActionMode mode = ((AppCompatActivity) context).startSupportActionMode(mMultiChoiceModeCallback);
            mMultiChoiceModeCallback.setItemCheckedState(itemId, position, true);
        }
    }

    public boolean fling(int velocityX, int velocityY) {
        // cheating
        return super.fling(velocityX, (int) (0.75 * velocityY));
    }

    public void scrollToBottom() {
        scrollToPosition(getCount() - 1);
    }

    protected void updateEmptyStatus(Adapter adapter, int itemCount) {
        if (adapter instanceof FooterModelViewAdapter) {
            updateEmptyStatus(((adapter.getItemCount() - 1) + itemCount <= 0));
        }
        else {
            super.updateEmptyStatus(adapter, itemCount);
        }
    }

    protected boolean isHeaderPosition(int position) {
        boolean result = false;
        if (mAdapter != null && !mAdapter.isDataPosition(position)) {
            result = true;
        }
        return result;
    }

}
