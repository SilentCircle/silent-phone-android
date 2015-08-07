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

import android.app.ListFragment;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.OnScrollListener;

import com.silentcircle.contacts.activities.ScContactDetailActivity;
import com.silentcircle.contacts.model.AccountTypeManager;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.utils.StreamItemEntry;
import com.silentcircle.silentcontacts2.ScContactsContract.StreamItems;
import com.silentcircle.silentphone2.R;

public class ContactDetailUpdatesFragment extends ListFragment implements ScContactDetailActivity.FragmentKeyListener {

    private static final String TAG = "ContactDetailUpdatesFragment";

    private Contact mContactData;
    private Uri mLookupUri;

    private LayoutInflater mInflater;
    private StreamItemAdapter mStreamItemAdapter;

    private OnScrollListener mVerticalScrollListener;

    /**
     * Listener on clicks on a stream item.
     * <p>
     * It assumes the view has a tag of type {@link com.silentcircle.contacts.utils.StreamItemEntry} associated with it.
     */
    private final View.OnClickListener mStreamItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            StreamItemEntry streamItemEntry = (StreamItemEntry) view.getTag();
            if (streamItemEntry == null) {
                // Ignore if this item does not have a stream item associated with it.
                return;
            }
            final AccountType accountType = getAccountTypeForStreamItemEntry(streamItemEntry);

            final Uri uri = ContentUris.withAppendedId(StreamItems.CONTENT_URI,
                    streamItemEntry.getId());
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getViewStreamItemActivity());
            startActivity(intent);
        }
    };

    private final View.OnClickListener mStreamItemPhotoItemClickListener
            = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ContactDetailDisplayUtils.StreamPhotoTag tag = (ContactDetailDisplayUtils.StreamPhotoTag) view.getTag();
            if (tag == null) {
                return;
            }
            final AccountType accountType = getAccountTypeForStreamItemEntry(tag.streamItem);

            final Intent intent = new Intent(Intent.ACTION_VIEW, tag.getStreamItemPhotoUri());
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getViewStreamItemPhotoActivity());
            startActivity(intent);
        }
    };

    private AccountType getAccountTypeForStreamItemEntry(StreamItemEntry streamItemEntry) {
        return AccountTypeManager.getInstance(getActivity()).getAccountType();
    }

    public ContactDetailUpdatesFragment() {
        // Explicit constructor for inflation
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mInflater = inflater;
        return mInflater.inflate(R.layout.contact_detail_updates_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStreamItemAdapter = new StreamItemAdapter(getActivity(), mStreamItemClickListener,
                mStreamItemPhotoItemClickListener);
        setListAdapter(mStreamItemAdapter);
        getListView().setOnScrollListener(mVerticalScrollListener);

        // It is possible that the contact data was set to the fragment when it was first attached
        // to the activity, but before this method was called because the fragment was not
        // visible on screen yet (i.e. using a {@link ViewPager}), so display the data if we already
        // have it.
        if (mContactData != null) {
            mStreamItemAdapter.setStreamItems(mContactData.getStreamItems());
        }
    }

    public void setData(Uri lookupUri, Contact result) {
        if (result == null) {
            return;
        }
        mLookupUri = lookupUri;
        mContactData = result;

        // If the adapter has been created already, then try to set stream items. Otherwise,
        // wait for the adapter to get initialized, after which we will try to set the stream items
        // again.
        if (mStreamItemAdapter != null) {
            mStreamItemAdapter.setStreamItems(mContactData.getStreamItems());
        }
    }

    /**
     * Reset the list adapter in this {@link android.support.v4.app.ListFragment} to get rid of any saved scroll position
     * from a previous contact.
     */
    public void resetAdapter() {
        setListAdapter(mStreamItemAdapter);
    }

    @Override
    public boolean handleKeyDown(int keyCode) {
        return false;
    }

    public void setVerticalScrollListener(OnScrollListener listener) {
        mVerticalScrollListener = listener;
    }

    /**
     * Returns the top coordinate of the first item in the {@link android.widget.ListView}. If the first item
     * in the {@link android.widget.ListView} is not visible or there are no children in the list, then return
     * Integer.MIN_VALUE. Note that the returned value will be <= 0 because the first item in the
     * list cannot have a positive offset.
     */
    public int getFirstListItemOffset() {
        return ContactDetailDisplayUtils.getFirstListItemOffset(getListView());
    }

    /**
     * Tries to scroll the first item to the given offset (this can be a no-op if the list is
     * already in the correct position).
     * @param offset which should be <= 0
     */
    public void requestToMoveToOffset(int offset) {
        ContactDetailDisplayUtils.requestToMoveToOffset(getListView(), offset);
    }
}
