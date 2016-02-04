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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.List;

public class ListView extends android.widget.ListView {

    protected class MultiChoiceModeCallback implements com.silentcircle.messaging.views.MultiChoiceModeListener {

        protected ActionMode currentMode;
        protected final com.silentcircle.messaging.views.MultiChoiceModeListener delegate;

        public MultiChoiceModeCallback(com.silentcircle.messaging.views.MultiChoiceModeListener delegate) {
            this.delegate = delegate;
        }

        public boolean hasActionMode() {
            return currentMode != null;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            SparseBooleanArray checkedItems = getCheckedItemPositions();
            final List<Integer> positionsList = new ArrayList<>();
            for (int i = 0; i < checkedItems.size(); i++) {
                int position = checkedItems.keyAt(i);
                if (checkedItems.get(position)) {
                    positionsList.add(position);
                }
            }

            int i = 0;
            int[] positions = new int[positionsList.size()];
            for (Integer position : positionsList) {
                positions[i++] = position;
            }

            performAction(item.getItemId(), positions);
            return delegate.onActionItemClicked(mode, item);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            currentMode = mode;
            clearChoices();
            return delegate.onCreateActionMode(mode, menu);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            delegate.onDestroyActionMode(mode);
            currentMode = null;
            invalidateViews();
            clearChoices();
            requestLayout();
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long itemId, boolean checked) {
            delegate.onItemCheckedStateChanged(mode, position, itemId, checked);
            if (!hasCheckedItems()) {
                mode.finish();
            }
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return delegate.onPrepareActionMode(mode, menu);
        }

        @Override
        public void performAction(final int menuActionId, final int... positions) {
            delegate.performAction(menuActionId, positions);
        }

        public void setItemCheckedState(int position, boolean checked) {
            setItemChecked(position, checked);
            onItemCheckedStateChanged(currentMode, position, 0, checked);
        }

        public boolean toggleItemCheckedState(int position) {
            boolean checked = getCheckedItemPositions().get(position, false);
            setItemCheckedState(position, checked);
            return checked;
        }

    }

    protected class SetCheckedStateOnItemClick implements OnItemClickListener {

        private OnItemClickListener delegate;

        @Override
        public void onItemClick(AdapterView<?> parentView, View view, int position, long itemId) {

            if (multiChoiceModeCallback == null || !multiChoiceModeCallback.hasActionMode()) {
                if (delegate != null) {
                    delegate.onItemClick(parentView, view, position, itemId);
                }
                return;
            }

            boolean checked = multiChoiceModeCallback.toggleItemCheckedState(position);

            if (parentView instanceof ListView) {
                ListView listView = (ListView) parentView;
                listView.setItemChecked(position, checked);
            }

        }

        public void setDelegate(OnItemClickListener delegate) {
            this.delegate = delegate;
        }

    }

    protected class StartActionModeOnItemLongClick implements OnItemLongClickListener {

        private OnItemLongClickListener delegate;

        @Override
        public boolean onItemLongClick(AdapterView<?> parentView, View view, int position, long itemId) {

            if (multiChoiceModeCallback == null) {
                return delegate == null ? false : delegate.onItemLongClick(parentView, view, position, itemId);
            }

            if (multiChoiceModeCallback.hasActionMode()) {
                return false;
            }

            Context context = getContext();

            // TODO: anru
            if (context instanceof ActionBarActivity) {
                ((ActionBarActivity) context).startSupportActionMode(multiChoiceModeCallback);
                multiChoiceModeCallback.setItemCheckedState(position, true);
                return true;
            }

            return false;
        }

        public void setDelegate(OnItemLongClickListener delegate) {
            this.delegate = delegate;
        }

    }

    protected MultiChoiceModeCallback multiChoiceModeCallback;

    public ListView(Context context) {
        super(context);
        prepareItemListeners();
    }

    public ListView(Context context, AttributeSet attributes) {
        super(context, attributes);
        prepareItemListeners();
    }

    public ListView(Context context, AttributeSet attributes, int defaultStyle) {
        super(context, attributes, defaultStyle);
        prepareItemListeners();
    }

    @Override
    public int getCheckedItemCount() {
        int count = 0;
        SparseBooleanArray checkedItems = getCheckedItemPositions();
        for (int i = 0; i < checkedItems.size(); i++) {
            if (checkedItems.valueAt(i)) {
                count++;
            }
        }
        return count;
    }

    public boolean hasCheckedItems() {
        return getCheckedItemCount() > 0;
    }

    public boolean hasMultipleCheckedItems() {
        return getCheckedItemCount() > 1;
    }

    protected void prepareItemListeners() {
        super.setOnItemClickListener(new SetCheckedStateOnItemClick());
        super.setOnItemLongClickListener(new StartActionModeOnItemLongClick());
    }

    public void setItemsChecked(boolean checked) {
        ListAdapter adapter = getAdapter();
        int length = adapter == null ? 0 : adapter.getCount();
        for (int i = 0; i < length; i++) {
            setItemChecked(i, checked);
        }
    }

    public void setMultiChoiceModeListener(com.silentcircle.messaging.views.MultiChoiceModeListener listener) {
        multiChoiceModeCallback = new MultiChoiceModeCallback(listener);
        setChoiceMode(CHOICE_MODE_MULTIPLE);
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        ((SetCheckedStateOnItemClick) getOnItemClickListener()).setDelegate(listener);
    }

    @Override
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        ((StartActionModeOnItemLongClick) getOnItemLongClickListener()).setDelegate(listener);
    }

}

