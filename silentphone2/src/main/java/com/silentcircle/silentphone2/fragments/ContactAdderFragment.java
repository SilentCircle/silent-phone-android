/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.silentcircle.silentphone2.fragments;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.list.ContactListFilter;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;
import com.silentcircle.contacts.list.ScContactEntryListFragment;
import com.silentcircle.contacts.list.ScDefaultContactListAdapter;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ContactAdder;

import java.util.ArrayList;

public final class ContactAdderFragment extends ScContactEntryListFragment<ScDefaultContactListAdapter>
{
    public static final String TAG = "ContactsAdderFragment";

    private ContactAdder mParent;

    public ContactAdderFragment() {
        setQuickContactEnabled(false);
        setAdjustSelectionBoundsEnabled(true);
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(false);
        setDarkTheme(false);
        setVisibleScrollbarEnabled(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (ContactAdder)activity;
    }

    /**
     * Called when this activity is about to be destroyed by the system.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected ScDefaultContactListAdapter createListAdapter() {
        final ScDefaultContactListAdapter adapter = new ScDefaultContactListAdapter(getActivity()) {
            @Override
            protected void bindView(View itemView, int partition, Cursor cursor, int position) {
                super.bindView(itemView, partition, cursor, position);
                itemView.setTag(this.getContactUri(partition, cursor));
            }
        };
        adapter.setDisplayPhotos(true);
        adapter.setFilter(ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_DEFAULT));
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_adder_fragment, null);
    }

    @Override
    public void onViewCreated(View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Uri uri = (Uri) view.getTag();
        getContactName(uri);
    }

    private String[] projection = {
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    };
    private static final int DISPLAY_NAME = 0;

    private void getContactName(Uri lookupUri) {
        final Cursor cursor = mParent.getContentResolver().query(lookupUri, projection, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            String name = cursor.getString(DISPLAY_NAME);
            cursor.close();
            mParent.setContactName(name);
        }
        if (cursor != null && !cursor.isClosed())
            cursor.close();
    }

    @Override
    protected void onItemClick(int position, long id) {
        // Do nothing. Implemented to satisfy ContactEntryListFragment.
    }
}
