/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.silentcircle.contacts.detail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.google.common.collect.Lists;
import com.silentcircle.contacts.model.AccountTypeManager;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.utils.StreamItemEntry;
import com.silentcircle.silentphone2.R;

import java.util.List;

/**
 * List adapter for stream items of a given contact.
 */
public class StreamItemAdapter extends BaseAdapter {
    /** The header view, hidden under the tab carousel, if present. */
    private static final int ITEM_VIEW_TYPE_HEADER = 0;
    /** The updates in the list. */
    private static final int ITEM_VIEW_TYPE_STREAM_ITEM = 1;

    private final Context mContext;
    private final View.OnClickListener mItemClickListener;
    private final View.OnClickListener mPhotoClickListener;
    private final LayoutInflater mInflater;

    private List<StreamItemEntry> mStreamItems;

    public StreamItemAdapter(Context context, View.OnClickListener itemClickListener, View.OnClickListener photoClickListener) {
        mContext = context;
        mItemClickListener = itemClickListener;
        mPhotoClickListener = photoClickListener;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mStreamItems = Lists.newArrayList();
    }

    @Override
    public int getCount() {
        // The header should only be included as items in the list if there are other
        // stream items.
        int count = mStreamItems.size();
        return (count == 0) ? 0 : (count + 1);
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return null;
        }
        return mStreamItems.get(position - 1);
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return -1;
        }
        return position - 1;
    }

    @Override
    public boolean isEnabled(int position) {
        // Make all list items disabled, so they're not clickable.
        // We make child views clickable in getvView() if the account type supports
        // viewStreamItemActivity or viewStreamItemPhotoActivity.
        return false;
    }

    @Override
    public boolean areAllItemsEnabled() {
        // See isEnabled().
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            return mInflater.inflate(R.layout.updates_header_contact, null);
        }
        final StreamItemEntry streamItem = (StreamItemEntry) getItem(position);
        final AccountTypeManager manager = AccountTypeManager.getInstance(mContext);
        final AccountType accountType = manager.getAccountType();

        final View view = ContactDetailDisplayUtils.createStreamItemView(
                mInflater, mContext, convertView, streamItem,
                // Only pass the photo click listener if the account type has the photo
                // view activity.
                (accountType.getViewStreamItemPhotoActivity() == null) ? null : mPhotoClickListener
                );
        final View contentView = view.findViewById(R.id.stream_item_content);

        // If the account type has the stream item view activity, make the stream container
        // clickable.
        if (accountType.getViewStreamItemActivity() != null) {
            contentView.setTag(streamItem);
            contentView.setFocusable(true);
            contentView.setOnClickListener(mItemClickListener);
            contentView.setEnabled(true);
        }
        else {
            contentView.setTag(null);
            contentView.setFocusable(false);
            contentView.setOnClickListener(null);
            // setOnClickListener makes it clickable, so we need to overwrite it.
            contentView.setClickable(false);
            contentView.setEnabled(false);
        }
        return view;
    }

    @Override
    public int getViewTypeCount() {
        // ITEM_VIEW_TYPE_HEADER and ITEM_VIEW_TYPE_STREAM_ITEM
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return ITEM_VIEW_TYPE_HEADER;
        }
        return ITEM_VIEW_TYPE_STREAM_ITEM;
    }

    public void setStreamItems(List<StreamItemEntry> streamItems) {
        mStreamItems = streamItems;
        notifyDataSetChanged();
    }
}
