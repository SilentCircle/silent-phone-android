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
package com.silentcircle.messaging.views.adapters;

import android.os.Debug;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import com.silentcircle.messaging.util.ComparableWeakReference;
import com.silentcircle.silentphone2.R;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends {@link ModelViewAdapter} to implement {@link HasChoiceMode} and apply its mode to
 * inflated views that also implement the {@link HasChoiceMode} interface.
 */
public class MultiSelectModelViewAdapter extends ModelViewAdapter implements HasChoiceMode, View.OnClickListener,
        View.OnLongClickListener {

    public interface OnItemClickListener {

        void onItemClick(@NonNull View view, int position, @NonNull Object item, String itemId);

        void onItemLongClick(@NonNull View view, int position, @NonNull Object item, String itemId);
    }

    private boolean mInChoiceMode;
    private Set<String> mCheckedIds;

    private OnItemClickListener mListener;

    public MultiSelectModelViewAdapter(List<?> models, ViewType[] viewTypes) {
        super(models, viewTypes);
    }

    public MultiSelectModelViewAdapter(ModelProvider modelProvider, ViewType[] viewTypes) {
        super(modelProvider, viewTypes);
    }

    public MultiSelectModelViewAdapter(Object[] models, ViewType[] viewTypes) {
        super(models, viewTypes);
    }

    @Override
    public EventViewHolder onCreateViewHolder(ViewGroup parent, int viewTypeIndex) {
        EventViewHolder viewHolder = super.onCreateViewHolder(parent, viewTypeIndex);
        View view = viewHolder.itemView;
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(EventViewHolder viewHolder, int position) {
        super.onBindViewHolder(viewHolder, position);
        updateView(viewHolder);
    }

    @Override
    public void onBindViewHolder(EventViewHolder viewHolder, int position, List<Object> payloads) {
        super.onBindViewHolder(viewHolder, position, payloads);
        updateView(viewHolder);
    }

    private void updateView(EventViewHolder viewHolder) {
        View view = viewHolder.getView();
        if (view instanceof HasChoiceMode) {
            ((HasChoiceMode) view).setInChoiceMode(isInChoiceMode());
        }
        if (view instanceof Checkable) {
            String eventId = (String) view.getTag(R.id.view_event_id);
            if (eventId != null && mCheckedIds != null) {
                ((Checkable) view).setChecked(mCheckedIds.contains(eventId));
            }
        }
    }

    @Override
    public boolean isInChoiceMode() {
        return mInChoiceMode;
    }

    @Override
    public void setInChoiceMode(boolean inChoiceMode) {
        mInChoiceMode = inChoiceMode;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    public void setCheckedIds(Set<String> checkedIds) {
        mCheckedIds = checkedIds;
    }

    @Override
    public void onClick(View view) {
        if (mListener != null && view != null) {
            mListener.onItemClick(view, getAdapterPosition(view), view.getTag(),
                    (String) view.getTag(R.id.view_event_id));
        }
    }

    @Override
    public boolean onLongClick(View view) {
        boolean result = false;
        if (mListener != null && view != null) {
            mListener.onItemLongClick(view, getAdapterPosition(view), view.getTag(),
                    (String) view.getTag(R.id.view_event_id));
            result = true;
        }
        return result;
    }
}
