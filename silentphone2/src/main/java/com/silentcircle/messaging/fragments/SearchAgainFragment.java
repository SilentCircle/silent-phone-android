/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.list.ContactListFilter;
import com.silentcircle.contacts.list.PhoneNumberListAdapter;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;
import com.silentcircle.contacts.list.ScDirectoryLoader;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessagingSearchListAdapter;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.list.DialerPhoneNumberListAdapter;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Fragment for contact selection when forwarding a message.
 */
public class SearchAgainFragment extends com.silentcircle.silentphone2.list.SearchFragment {

    private static final String TAG = SearchAgainFragment.class.getSimpleName();

    public static final String TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT = "com.silentcircle.messaging.contactsearch";

    public SearchAgainFragment() {
        setPhotoLoaderEnabled(true);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // This filter works in the non-search mode only
        setFilter(ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_WITH_SIP_NUMBERS_ONLY));
    }

    public void startConversation(final String userName, final String uuid) {
        Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            Intent messagingIntent = ContactsUtils.getMessagingIntent(uuid, activity);
            Extra.ALIAS.to(messagingIntent, userName);

            // We store these pending attachment strings temporarily in the search fragment
            // They are passed back to the activity when a recipient is selected
            String pendingDataFile = Extra.DATA.from(getArguments());

            if(!TextUtils.isEmpty(pendingDataFile)) {
                Extra.DATA.to(messagingIntent, pendingDataFile);
            } else {
                String pendingDataText = Extra.TEXT.from(getArguments());

                if (!TextUtils.isEmpty(pendingDataText)) {
                    Extra.TEXT.to(messagingIntent, pendingDataText);
                }
            }

            activity.startActivity(messagingIntent);
        }
    }

    @Override
    protected ScContactEntryListAdapter createListAdapter() {
        MessagingSearchListAdapter adapter = new MessagingSearchListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(usesCallableUri());
        adapter.searchScData(true);


        // This filter works in non-search and in search mode
        adapter.setContentFilter(".*" + getString(R.string.sc_sip_domain_0), PhoneNumberListAdapter.PhoneQuery.PHONE_NUMBER);
        adapter.setScDirectoryFilter(ScDirectoryLoader.NAMES_ONLY);
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final MessagingSearchListAdapter adapter = (MessagingSearchListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final String number = Utilities.removeUriPartsSelective(adapter.getPhoneNumber(position));

        final Cursor cursor = (Cursor)adapter.getItem(position);
        String uuid = number;
        if (cursor != null) {
            final int scIndex = cursor.getColumnIndex(ScDirectoryLoader.SC_UUID_FIELD);
            if (scIndex >= 0) {
                uuid = cursor.getString(scIndex);
            }
        }
        ScDirectoryLoader.clearCachedData();

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                /*
                 * User clicked on a search result item, start conversation if username is not
                 * empty, without username validation through API call.
                 */
                if (TextUtils.isEmpty(number)) {
                    Log.e(TAG, "Click on a contact item gives empty number. Cannot start conversation. " + number);
                }
                else {
                    startConversation(number, uuid);
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION:
                validateUser(getQueryString());
                break;
            default:
                Log.e(TAG, "Wrong shortcut type detected: " + shortcutType);
                break;
        }
    }
}
