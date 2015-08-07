/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License.
 */
package com.silentcircle.silentphone2.list;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.silentcircle.common.list.PinnedHeaderListView;
import com.silentcircle.common.util.CachedNumberLookupService;
import com.silentcircle.common.util.ObjectFactory;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;

public class RegularSearchFragment extends SearchFragment {

    private static final int SEARCH_DIRECTORY_RESULT_LIMIT = 5;

    private static final CachedNumberLookupService mCachedNumberLookupService =
        ObjectFactory.newCachedNumberLookupService();

    public RegularSearchFragment() {
        configureDirectorySearch();
    }

    public void configureDirectorySearch() {
//        setDirectorySearchEnabled(true);
        setDirectoryResultLimit(SEARCH_DIRECTORY_RESULT_LIMIT);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        ((PinnedHeaderListView) getListView()).setScrollToSectionOnHeaderTouch(true);
    }

    protected ScContactEntryListAdapter createListAdapter() {
        RegularSearchListAdapter adapter = new RegularSearchListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(usesCallableUri());
        return adapter;
    }

    @Override
    protected void cacheContactInfo(int position) {
        if (mCachedNumberLookupService != null) {
            final RegularSearchListAdapter adapter =
                (RegularSearchListAdapter) getAdapter();
            mCachedNumberLookupService.addContact(getContext(),
                    adapter.getContactInfo(mCachedNumberLookupService, position));
        }
    }
}
