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
 * limitations under the License.
 */
package com.silentcircle.contacts.list;

import android.app.Activity;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.silentcircle.contacts.ContactPhotoManager;
import com.silentcircle.contacts.ContactTileLoaderFactory;
import com.silentcircle.silentphone2.R;

/**
 * Fragment containing a list of starred contacts followed by a list of frequently contacted.
 *
 * TODO: Make this an abstract class so that the favorites, frequent, and group list functionality
 * can be separated out. This will make it easier to customize any of those lists if necessary
 * (i.e. adding header views to the ListViews in the fragment). This work was started
 * by creating {@link ContactTileFrequentFragment}.
 */
public class ContactTileListFragment extends Fragment {
    private static final String TAG = ContactTileListFragment.class.getSimpleName();

    public interface Listener {
        void onContactSelected(Uri contactUri, Rect targetRect);
        void onCallNumberDirectly(String phoneNumber);
    }

    private Listener mListener;
    private ContactTileAdapter mAdapter;
    private ContactTileAdapter.DisplayType mDisplayType;
    private TextView mEmptyView;
    private ListView mListView;

    private boolean displayEmpty = true;

    private boolean mOptionsMenuHasFrequents;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        Resources res = getResources();
        int columnCount = res.getInteger(R.integer.contact_tile_column_count_in_favorites);

        mAdapter = new ContactTileAdapter(activity, mAdapterListener,
                columnCount, mDisplayType);
        mAdapter.setPhotoLoader(ContactPhotoManager.getInstance(activity));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflateAndSetupView(inflater, container, savedInstanceState, R.layout.contact_tile_list);
    }

    protected View inflateAndSetupView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState, int layoutResourceId) {
        View listLayout = inflater.inflate(layoutResourceId, container, false);

        mEmptyView = (TextView) listLayout.findViewById(R.id.contact_tile_list_empty);
        mListView = (ListView) listLayout.findViewById(R.id.contact_tile_list);

        mListView.setItemsCanFocus(true);
        mListView.setAdapter(mAdapter);
        return listLayout;
    }

    @Override
    public void onStart() {
        super.onStart();

        // initialize the loader for this display type and destroy all others
        final ContactTileAdapter.DisplayType[] loaderTypes = mDisplayType.values();
        for (int i = 0; i < loaderTypes.length; i++) {
            if (loaderTypes[i] == mDisplayType) {
                getLoaderManager().initLoader(mDisplayType.ordinal(), null, mContactTileLoaderListener);
            }
            else {
                getLoaderManager().destroyLoader(loaderTypes[i].ordinal());
            }
        }
    }

    /**
     * Returns whether there are any frequents with the side effect of setting the
     * internal flag mOptionsMenuHasFrequents to the value.  This should be called externally
     * by the activity that is about to prepare the options menu with the clear frequents
     * menu item.
     */
    public boolean hasFrequents() {
        mOptionsMenuHasFrequents = internalHasFrequents();
        return mOptionsMenuHasFrequents;
    }

    /**
     * Returns whether there are any frequents.
     */
    private boolean internalHasFrequents() {
        return mAdapter.getNumFrequents() > 0;
    }

    public void setColumnCount(int columnCount) {
        mAdapter.setColumnCount(columnCount);
    }

    public void setDisplayType(ContactTileAdapter.DisplayType displayType) {
        mDisplayType = displayType;
        mAdapter.setDisplayType(mDisplayType);
    }

    public void enableQuickContact(boolean enableQuickContact) {
        mAdapter.enableQuickContact(enableQuickContact);
    }

    public void setDisplayEmpty(boolean yesNo) {
        displayEmpty = yesNo;
    }
    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            switch (mDisplayType) {
              case STARRED_ONLY:
                  return ContactTileLoaderFactory.createStarredLoader(getActivity());
//              case STREQUENT:
//                  return ContactTileLoaderFactory.createStrequentLoader(getActivity());
//              case STREQUENT_PHONE_ONLY:
//                  return ContactTileLoaderFactory.createStrequentPhoneOnlyLoader(getActivity());
//              case FREQUENT_ONLY:
//                  return ContactTileLoaderFactory.createFrequentLoader(getActivity());
              default:
                  throw new IllegalStateException("Unrecognized DisplayType " + mDisplayType);
            }
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mAdapter.setContactCursor(data);
            if (displayEmpty) {
                mEmptyView.setText(getEmptyStateText());
                mListView.setEmptyView(mEmptyView);
            }
            // invalidate the menu options if needed
            invalidateOptionsMenuIfNeeded();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {}
    };

    private boolean isOptionsMenuChanged() {
        return mOptionsMenuHasFrequents != internalHasFrequents();
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (isOptionsMenuChanged()) {
            getActivity().invalidateOptionsMenu();
        }
    }

    private String getEmptyStateText() {
        String emptyText;
        switch (mDisplayType) {
            case STREQUENT:
            case STREQUENT_PHONE_ONLY:
            case STARRED_ONLY:
                emptyText = getString(R.string.listTotalAllContactsZeroStarred);
                break;
            case FREQUENT_ONLY:
            case GROUP_MEMBERS:
                emptyText = getString(R.string.noContacts);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized DisplayType " + mDisplayType);
        }
        return emptyText;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private ContactTileView.Listener mAdapterListener =
            new ContactTileView.Listener() {
        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            if (mListener != null) {
                mListener.onContactSelected(contactUri, targetRect);
            }
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            if (mListener != null) {
                mListener.onCallNumberDirectly(phoneNumber);
            }
        }

        @Override
        public int getApproximateTileWidth() {
            return getView().getWidth() / mAdapter.getColumnCount();
        }
    };
}
