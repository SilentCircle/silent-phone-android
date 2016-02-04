/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.common.list.ContactListItemView.PhotoPosition;

import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.contacts.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.LoadUserInfo;
// import com.silentcircle.contacts.utils.AccountFilterUtil;

/**
 * Fragment containing a phone number list for picking.
 */
public class PhoneNumberPickerFragment extends ScContactEntryListFragment<ScContactEntryListAdapter>
        implements OnShortcutIntentCreatedListener, View.OnClickListener {

    private static final String TAG = PhoneNumberPickerFragment.class.getSimpleName();

    private static final int REQUEST_CODE_ACCOUNT_FILTER = 1;

    private static final String KEY_SHORTCUT_ACTION = "shortcutAction";

    private OnPhoneNumberPickerActionListener mListener;
    private String mShortcutAction;

    private ContactListFilter mFilter;

    private View mAccountFilterHeader;
    /**
     * Lives as ListView's header and is shown when {@link #mAccountFilterHeader} is set
     * to View.GONE.
     */
    private View mPaddingView;

    private static final String KEY_FILTER = "filter";

    /** true if the loader has started at least once. */
    private boolean mLoaderStarted;

    private boolean mUseCallableUri;
    
    private boolean mShowScDirectoryOption = true;

    private CheckBox mScDirectoryBox;
    private CheckBox mScInOrgBox;

    private PhotoPosition mPhotoPosition = ContactListItemView.getDefaultPhotoPosition(false);

//    private class FilterHeaderClickListener implements OnClickListener {
//        @Override
//        public void onClick(View view) {
//            AccountFilterUtil.startAccountFilterActivityForResult(
//                    PhoneNumberPickerFragment.this,
//                    REQUEST_CODE_ACCOUNT_FILTER,
//                    mFilter);
//        }
//    }

    private OnClickListener mFilterHeaderClickListener; // = new FilterHeaderClickListener();

    public PhoneNumberPickerFragment() {
        setQuickContactEnabled(false);
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
//        setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DATA_SHORTCUT);

        // Show nothing instead of letting caller Activity show something.
        setHasOptionsMenu(true);
    }

    public void setOnPhoneNumberPickerActionListener(OnPhoneNumberPickerActionListener listener) {
        this.mListener = listener;
    }

    public OnPhoneNumberPickerActionListener getOnPhoneNumberPickerListener() {
        return mListener;
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        View paddingView = inflater.inflate(R.layout.contact_detail_list_padding, null, false);
        mPaddingView = paddingView.findViewById(R.id.contact_detail_list_padding);
        getListView().addHeaderView(paddingView);
        updateFilterHeaderView();

        setVisibleScrollbarEnabled(!isLegacyCompatibilityMode());
    }

    @Override
    public void onStart() {
        super.onStart();                // super: ScContactEntryListFragment - configures loader etc
        if (mShowScDirectoryOption) {
            useScDirectory(true);
            useScDirectoryOrganization(false);
        }
//        if (mScDirectoryBox != null && mScDirectoryBox.isChecked()) {
//            if (!ScDirectoryLoader.mErrorOnPreviousSearch) {
//                if (mScInOrgBox != null && mScInOrgBox.isChecked()) {
//                }
//            }
//            else {
//                mScDirectoryBox.setChecked(false);
//                mScInOrgBox.setChecked(false);
//                useScDirectory(false);
//                useScDirectoryOrganization(false);
//                ScDirectoryLoader.mErrorOnPreviousSearch = false;
//            }
//        }
    }

    protected boolean getVisibleScrollbarEnabled() {
        return true;
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        updateFilterHeaderView();
    }

    private void updateFilterHeaderView() {
        final ContactListFilter filter = getFilter();
        if (mAccountFilterHeader == null || filter == null) {
            return;
        }
        final boolean shouldShowHeader = !isSearchMode(); 
//                &&  AccountFilterUtil.updateAccountFilterTitleForPhone( mAccountFilterHeader, filter, false);
        if (shouldShowHeader) {
            mPaddingView.setVisibility(View.GONE);
            mAccountFilterHeader.setVisibility(View.VISIBLE);
        } else {
            mPaddingView.setVisibility(View.VISIBLE);
            mAccountFilterHeader.setVisibility(View.GONE);
        }
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mFilter = savedState.getParcelable(KEY_FILTER);
        mShortcutAction = savedState.getString(KEY_SHORTCUT_ACTION);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_FILTER, mFilter);
        outState.putString(KEY_SHORTCUT_ACTION, mShortcutAction);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            if (mListener != null) {
                mListener.onHomeInActionBarSelected();
            }
            ScDirectoryLoader.clearCachedData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * @param shortcutAction either {@link android.content.Intent#ACTION_CALL} or
     *            {@link android.content.Intent#ACTION_SENDTO} or null.
     */
    public void setShortcutAction(String shortcutAction) {
        this.mShortcutAction = shortcutAction;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri phoneUri;
        final String number;
        PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        phoneUri = adapter.getDataUri(position);
        number = adapter.getPhoneNumber(position);
        ScDirectoryLoader.clearCachedData();

        if (phoneUri != null || number != null) {
            pickPhoneNumber(phoneUri, number);
        } else {
            Log.w(TAG, "Item at " + position + " was clicked before adapter is ready. Ignoring");
        }
    }

    protected void cacheContactInfo(int position) {
        // Not implemented. Hook for child classes
    }

    @Override
    public void onClick(View view) {
        CheckBox cb;
        boolean checked;
        switch (view.getId()) {
            case R.id.use_sc_dir:
                cb = (CheckBox)view;
                checked = cb.isChecked();
                if (!useScDirectory(checked))
                    return;
                if (checked && LoadUserInfo.hasOrganization())
                    mScInOrgBox.setVisibility(View.VISIBLE);
                else
                    mScInOrgBox.setVisibility(View.GONE);
                cb.setChecked(checked);
                ScDirectoryLoader.mErrorOnPreviousSearch = false;
                break;

            case R.id.use_sc_own_org:
                cb = (CheckBox)view;
                checked = cb.isChecked();
                if (!useScDirectoryOrganization(checked))
                    return;
                cb.setChecked(checked);
                break;
        }
    }

    protected String getPhoneNumber(int position) {
        final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        return adapter.getPhoneNumber(position);
    }

    protected Uri getPhoneUri(int position) {
        final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        return adapter.getDataUri(position);
    }

    @Override
    protected void startLoading() {
        mLoaderStarted = true;
        super.startLoading();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);

        // disable scroll bar if there is no data
        setVisibleScrollbarEnabled(data.getCount() > 0);
    }

    public void setUseCallableUri(boolean useCallableUri) {
        mUseCallableUri = useCallableUri;
    }

    public boolean usesCallableUri() {
        return mUseCallableUri;
    }

    @Override
    protected ScContactEntryListAdapter createListAdapter() {
        PhoneNumberListAdapter adapter = new PhoneNumberListAdapter(getActivity(), true);
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(mUseCallableUri);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        final ScContactEntryListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        if (!isSearchMode() && mFilter != null) {
            adapter.setFilter(mFilter);
        }

        setPhotoPosition(adapter);
    }

    protected void setPhotoPosition(ScContactEntryListAdapter adapter) {
        ((PhoneNumberListAdapter) adapter).setPhotoPosition(mPhotoPosition);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.contact_list_content, null);
        mScDirectoryBox = (CheckBox)view.findViewById(R.id.use_sc_dir);
        mScDirectoryBox.setOnClickListener(this);
        mScInOrgBox = (CheckBox)view.findViewById(R.id.use_sc_own_org);
        mScInOrgBox.setOnClickListener(this);
        if (mShowScDirectoryOption) {
            view.findViewById(R.id.sc_dir_options).setVisibility(View.VISIBLE);
        }
        return view;
    }

    public void setShowScDirectoryOption(boolean flag) {
        mShowScDirectoryOption = flag;
        View mainView = getView();
        if (mainView == null)
            return;
        View v = mainView.findViewById(R.id.sc_dir_options);
        if (!flag) {
            if (v.getVisibility() == View.VISIBLE)
                getView().findViewById(R.id.sc_dir_options).setVisibility(View.GONE);
        }
        else {
            if (v.getVisibility() != View.VISIBLE)
                getView().findViewById(R.id.sc_dir_options).setVisibility(View.VISIBLE);
        }
    }

    private void pickPhoneNumber(Uri uri, String number) {
        if (mShortcutAction == null) {
            mListener.onCallNumberDirectly(number);
        } 
        else if (uri != null) {
            startPhoneNumberShortcutIntent(uri);
        }
    }

    protected void startPhoneNumberShortcutIntent(Uri uri) {
        ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getActivity(), this);
        builder.createPhoneNumberShortcutIntent(uri, mShortcutAction);
    }

    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
        mListener.onShortcutIntentCreated(shortcutIntent);
    }

    @Override
    public void onPickerResult(Intent data) {
        mListener.onPickPhoneNumberAction(data.getData());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ACCOUNT_FILTER) {
//            if (getActivity() != null) {
//                AccountFilterUtil.handleAccountFilterResult(
//                        ContactListFilterController.getInstance(getActivity()), resultCode, data);
//            } else {
//                Log.e(TAG, "getActivity() returns null during Fragment#onActivityResult()");
//            }
        }
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    public void setFilter(ContactListFilter filter) {
        if ((mFilter == null && filter == null) ||
                (mFilter != null && mFilter.equals(filter))) {
            return;
        }

        mFilter = filter;
        if (mLoaderStarted) {
            reloadData();
        }
        updateFilterHeaderView();
    }

    public void setPhotoPosition(PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
        final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        if (adapter != null) {
            adapter.setPhotoPosition(photoPosition);
        }
    }
}
