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
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.silentcircle.contacts.list.ContactListFilter;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;
import com.silentcircle.contacts.list.ScContactEntryListFragment;
import com.silentcircle.contacts.list.ScContactListAdapter;
import com.silentcircle.contacts.list.ScDefaultContactListAdapter;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ContactAdder;

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
                itemView.setTag(cursor.getLong(ContactQuery.CONTACT_ID));
            }
        };
        adapter.setDisplayPhotos(true);
        adapter.setFilter(ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_DEFAULT));
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_adder_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final String name = getAdapter().getContactDisplayName(position);
        mParent.setContactName(name);
        final long contactId = (Long) view.getTag();
        getOtherRawId(contactId);
    }

    private String[] projection = {
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
    };
    private static final int ID = 0;
    private static final int ACCOUNT_NAME = 1;
    private static final int ACCOUNT_TYPE = 2;

    private final static String rawQuery = ContactsContract.RawContacts.CONTACT_ID + "=?";

    // Check if the selected account already has another (non-SC) associated raw account. If
    // yes then report the first found raw contact id to the parent. The parent then joins this
    // raw contact id with the newly created SC raw contact.
    private void getOtherRawId(long id) {
        final Cursor cursor = mParent.getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI,
                projection, rawQuery, new String[] {Long.toString(id)}, null);

        if (cursor == null)
            return;

        Account account = mParent.getAccountInfo();
        if (account == null) {
            cursor.close();
            return;
        }
        while (cursor.moveToNext()) {
            String accountName = cursor.getString(ACCOUNT_NAME);
            String accountType = cursor.getString(ACCOUNT_TYPE);
            if (account.name.equals(accountName) && account.type.equals(accountType))
                continue;
            mParent.setOtherRawId(cursor.getLong(ID));
            break;
        }
        cursor.close();
    }

    @Override
    protected void onItemClick(int position, long id) {
        // Do nothing. Implemented to satisfy ContactEntryListFragment.
    }
}
