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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.views.adapters.ResolvingUserAdapter;
import com.silentcircle.silentphone2.R;

import java.util.List;

/**
 *
 */
public class ContactSelection extends LinearLayout implements ResolvingUserAdapter.OnItemClickListener {

    public interface OnSelectionChangedListener {

        void onItemRemoved(int position, Object item);
    }

    private static class ContactSelectionAdapter extends ResolvingUserAdapter<String> {

        ContactSelectionAdapter(Context context) {
            super(context);
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            private View mRootView;
            private android.widget.TextView mMemberName;
            private ImageView mButtonRemove;

            ViewHolder(View itemView) {
                super(itemView);
                mRootView = itemView;
                mMemberName = (android.widget.TextView) itemView.findViewById(R.id.name);
                mButtonRemove = (ImageView) itemView.findViewById(R.id.remove);
            }

            public void bind(String item, int position) {
                if (item == null) {
                    return;
                }

                mRootView.setTag(item);
                mRootView.setTag(R.id.position, position);

                ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(item);
                String name = ConversationUtils.resolveDisplayName(contactEntry, null);
                if (TextUtils.isEmpty(name)) {
                    name = item;
                    if (contactEntry != null) {
                        if (!TextUtils.isEmpty(contactEntry.name)) {
                            name = contactEntry.name;
                        }
                        else if (!TextUtils.isEmpty(contactEntry.alias)) {
                            name = contactEntry.alias;
                        }
                        else if (!TextUtils.isEmpty(contactEntry.imName)) {
                            name = contactEntry.imName;
                        }
                    }
                }
                name = StringUtils.formatShortName(name);
                mMemberName.setText(name);
                if (ContactsCache.hasExpired(contactEntry)) {
                    doContactRequest(item, position, contactEntry);
                }
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.widget_contact_selection_item, parent, false);
            view.setOnClickListener(this);
            view.setLongClickable(true);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            String item = (String) getItem(position);
            final ViewHolder viewHolder = (ViewHolder) holder;
            viewHolder.bind(item, position);
        }
    };

    private com.silentcircle.messaging.views.RecyclerView mRecyclerView;
    private ContactSelectionAdapter mAdapter;

    private OnSelectionChangedListener mListener;

    public ContactSelection(Context context) {
        this(context, null);
    }

    public ContactSelection(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactSelection(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflate(context, R.layout.widget_contact_selection, this);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context,
                LinearLayoutManager.HORIZONTAL, false);
        mRecyclerView = (com.silentcircle.messaging.views.RecyclerView) findViewById(R.id.contact_selection_list);
        mRecyclerView.setLayoutManager(layoutManager);

        if (isInEditMode()) {
            return;
        }

        mAdapter = new ContactSelectionAdapter(context);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(false);
        mAdapter.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(@NonNull View view, int position, @NonNull Object item) {
        if (mListener != null) {
            mListener.onItemRemoved(position, item);
        }
    }

    @Override
    public void onItemLongClick(@NonNull View view, int position, @NonNull Object item) {
    }

    public void setItems(List<String> items) {
        if (mAdapter != null) {
            mAdapter.setItems(items);
            mAdapter.notifyDataSetChanged();
        }
    }

    public void addItem(int position) {
        if (mAdapter != null) {
            mAdapter.notifyItemInserted(position);
        }
        if (mRecyclerView != null) {
            mRecyclerView.scrollToPosition(position);
        }
    }

    public void removeItem(int position) {
        if (mAdapter != null) {
            mAdapter.notifyItemRemoved(position);
        }
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        mListener = listener;
    }

    public void setEmptyView(View emptyView) {
        if (mRecyclerView != null) {
            mRecyclerView.setEmptyView(emptyView);
        }
    }

    public void stopRequestProcessing() {
        if (mAdapter != null) {
            mAdapter.stopRequestProcessing();
        }
    }
}
