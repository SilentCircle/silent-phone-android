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
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.silentphone2.R;

/**
 *
 */
public class GroupMemberAdapter<T> extends ResolvingUserAdapter<T> {

    public interface OnGroupMemberItemClickListener extends
            ResolvingUserAdapter.OnItemClickListener {

        /* Callback for click event on add-to-contacts button */
        void onGroupMemberAddToContactsClick(final View view, final View parentView, int position, @NonNull Object item);

        /* Callback for click event on delete button */
        void onGroupMemberDeleteClick(final View view, final View parentView, int position, @NonNull Object item);

        /* Callback for click event on call button */
        void onGroupMemberCallClick(final View view, final View parentView, int position, @NonNull Object item);
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView mMemberName;
        private TextView mMemberAlias;
        private TextView mLastModified;
        private ImageButton mButtonRemove;
        private ImageButton mButtonAddToContacts;
        private QuickContactBadge mContactBadge;

        ViewHolder(View itemView) {
            super(itemView);
            mMemberName = (TextView) itemView.findViewById(R.id.name);
            mMemberAlias = (TextView) itemView.findViewById(R.id.alias);
            mLastModified = (TextView) itemView.findViewById(R.id.time);
            mContactBadge = (QuickContactBadge) itemView.findViewById(R.id.quick_contact_photo);
            mButtonAddToContacts =  (ImageButton) itemView.findViewById(R.id.add_to_contacts);
            mButtonRemove = (ImageButton) itemView.findViewById(R.id.remove);

            mButtonAddToContacts.setOnClickListener(this);
            mButtonRemove.setOnClickListener(this);
        }

        public void bind(String item, int position) {
            if (item == null) {
                return;
            }

            itemView.setTag(item);
            itemView.setTag(R.id.position, position);

            mButtonRemove.setVisibility(View.GONE);

            mLastModified.setText(null);

            ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(item);

            if (contactEntry == null) {
                mMemberName.setText(item);
                mMemberAlias.setVisibility(View.GONE);
            } else {
                mMemberName.setText(contactEntry.name);
                mMemberAlias.setText(item);
                mMemberAlias.setVisibility(View.VISIBLE);
            }
            AvatarUtils.setPhoto(getPhotoManager(), mContactBadge, contactEntry);
            if (ContactsCache.hasExpired(contactEntry)) {
                doContactRequest(item, position, contactEntry);
            }
        }

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.remove:
                    if (mListener != null) {
                        mListener.onGroupMemberDeleteClick(view, itemView,
                                (Integer) itemView.getTag(R.id.position), itemView.getTag());
                    }
                    break;
                case R.id.add_to_contacts:
                    if (mListener != null) {
                        mListener.onGroupMemberAddToContactsClick(view, itemView,
                                (Integer) itemView.getTag(R.id.position), itemView.getTag());
                    }
                    break;
                default:
                    GroupMemberAdapter.this.onClick(view);
                    break;
            }
        }
    }

    private OnGroupMemberItemClickListener mListener;

    public GroupMemberAdapter(Context context) {
        super(context);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = getLayoutInflater().inflate(R.layout.messaging_group_member, parent, false);
        ViewHolder viewHolder = new GroupMemberAdapter.ViewHolder(view);
        view.setOnClickListener(this);
        view.setLongClickable(true);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        String item = (String) getItem(position);
        final GroupMemberAdapter.ViewHolder viewHolder = (GroupMemberAdapter.ViewHolder) holder;
        viewHolder.bind(item, position);
    }

    public void setOnItemClickListener(OnGroupMemberItemClickListener listener) {
        mListener = listener;
        super.setOnItemClickListener(listener);
    }
};
