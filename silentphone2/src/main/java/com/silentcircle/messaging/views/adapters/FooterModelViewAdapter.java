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

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.silentphone2.R;

import java.util.List;

/**
 * Adapter adds a single item at the bottom of recycler-view.
 * Item's height can be configured.
 */
public class FooterModelViewAdapter extends AvatarModelViewAdapter {

    private class FooterViewHolder extends EventViewHolder {

        FooterViewHolder(View itemView) {
            super(itemView);
        }

        public void bind(Object item, int position) {
            itemView.setTag(item);
        }
    }

    private static final String FOOTER = "footer";

    private View mFooterView;
    private int mFooterHeight;

    public FooterModelViewAdapter(List<?> models, ViewType[] viewTypes, int footerHeight) {
        super(models, viewTypes);
        mFooterHeight = footerHeight;
    }

    @Override
    public int getViewTypeCount() {
        return super.getViewTypeCount() + 1;
    }

    @Override
    public int getCount() {
        return super.getCount() + 1;
    }

    @Override
    public int getItemCount() {
        return getCount();
    }

    @Override
    public int getItemViewType(int position) {
        return position == getCount() - 1 ? (getViewTypeCount() - 1) : super.getItemViewType(position);
    }

    @Override
    public EventViewHolder onCreateViewHolder(ViewGroup parent, int viewTypeIndex) {
        EventViewHolder viewHolder;
        if (viewTypeIndex == (getViewTypeCount() - 1)) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            mFooterView = inflater.inflate(R.layout.widget_footer, parent, false);
            mFooterView.setLayoutParams(
                    new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                            mFooterHeight));
            viewHolder = new FooterViewHolder(mFooterView);
        }
        else {
            viewHolder = super.onCreateViewHolder(parent, viewTypeIndex);
        }
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(EventViewHolder viewHolder, int position) {
        if (position < getCount() - 1) {
            super.onBindViewHolder(viewHolder, position);
        }
        else {
            final FooterViewHolder footerViewHolder = (FooterViewHolder) viewHolder;
            footerViewHolder.bind(FOOTER, position);
        }
    }

    @Override
    public void onBindViewHolder(EventViewHolder viewHolder, int position, List<Object> payloads) {
        if (position < getCount() - 1) {
            super.onBindViewHolder(viewHolder, position, payloads);
        }
        else {
            final FooterViewHolder footerViewHolder = (FooterViewHolder) viewHolder;
            footerViewHolder.bind(FOOTER, position);
        }
    }

    public void setFooterHeight(int height) {
        mFooterHeight = height;
        if (mFooterView != null) {
            // layout params are set for non-null footer item
            ViewGroup.LayoutParams params = mFooterView.getLayoutParams();
            params.height = mFooterHeight;
            mFooterView.setLayoutParams(params);
        }
    }

}
