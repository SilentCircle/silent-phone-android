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
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.silentcircle.silentphone2.R;

/**
 * Fragment for the contact list used for browsing contacts (as compared to
 * picking a contact with one of the PICK or SHORTCUT intents).
 */
public class ContactPickerFragment extends ScContactEntryListFragment<ScContactEntryListAdapter>
    implements ShortcutIntentBuilder.OnShortcutIntentCreatedListener {

    private static final String KEY_EDIT_MODE = "editMode";
    private static final String KEY_SHORTCUT_REQUESTED = "shortcutRequested";

    private OnContactPickerActionListener mListener;
    private boolean mEditMode;
    private boolean mShortcutRequested;

    public ContactPickerFragment() {

        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
        setQuickContactEnabled(false);
        setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_CONTACT_SHORTCUT);
        setDarkTheme(true);
    }

    public void setOnContactPickerActionListener(OnContactPickerActionListener listener) {
        mListener = listener;
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setEditMode(boolean flag) {
        mEditMode = flag;
    }

    public boolean isShortcutRequested() {
        return mShortcutRequested;
    }

    public void setShortcutRequested(boolean flag) {
        mShortcutRequested = flag;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_EDIT_MODE, mEditMode);
        outState.putBoolean(KEY_SHORTCUT_REQUESTED, mShortcutRequested);
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mEditMode = savedState.getBoolean(KEY_EDIT_MODE);
        mShortcutRequested = savedState.getBoolean(KEY_SHORTCUT_REQUESTED);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        super.onItemClick(parent, view, position, id);
    }

    @Override
    protected void onItemClick(int position, long id) {
        Uri uri;
        uri = ((ScContactListAdapter)getAdapter()).getContactUri(position);

        if (mEditMode) {
            editContact(uri);
        } 
        else if (mShortcutRequested) {
            ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getActivity(), this);
            builder.createContactShortcutIntent(uri);
        }
        else {
            pickContact(uri);
        }
    }

    public void createNewContact() {
        mListener.onCreateNewContactAction();
    }

    public void editContact(Uri contactUri) {
        mListener.onEditContactAction(contactUri);
    }

    public void pickContact(Uri uri) {
        mListener.onPickContactAction(uri);
    }

    @Override
    protected ScContactEntryListAdapter createListAdapter() {
        ScDefaultContactListAdapter adapter = new ScDefaultContactListAdapter(getActivity());
        adapter.setFilter(ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_DEFAULT));
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(false);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        ScContactEntryListAdapter adapter = getAdapter();
        adapter.setEmptyListEnabled(true);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_picker_content, null);
    }

    @Override
    protected void prepareEmptyView() {
        if (isSearchMode()) {
            return;
        }
        else if (isSyncActive()) {
            if (mShortcutRequested) {
                // Help text is the same no matter whether there is SIM or not.
                setEmptyText(R.string.noContactsHelpTextWithSyncForCreateShortcut);
            } 
            else if (hasIccCard()) {
                setEmptyText(R.string.noContactsHelpTextWithSync);
            } 
            else {
                setEmptyText(R.string.noContactsNoSimHelpTextWithSync);
            }
        }
        else {
            if (mShortcutRequested) {
                // Help text is the same no matter whether there is SIM or not.
                setEmptyText(R.string.noContactsHelpTextWithSyncForCreateShortcut);
            }
            else if (hasIccCard()) {
                setEmptyText(R.string.noContactsHelpText);
            } 
            else {
                setEmptyText(R.string.noContactsNoSimHelpText);
            }
        }
    }

    @Override
    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
        mListener.onShortcutIntentCreated(shortcutIntent);
    }

    @Override
    public void onPickerResult(Intent data) {
        mListener.onPickContactAction(data.getData());
    }
}
