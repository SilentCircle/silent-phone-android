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

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.contacts.preference.ContactsPreferences;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentcontacts2.ScContactsContract.SearchSnippetColumns;

import java.util.ArrayList;
import java.util.List;

/**
 * A cursor adapter for the { @link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public class ScDefaultContactListAdapter extends ScContactListAdapter {

    static final String TAG = "ScDefaultContactListA";
    
    public static final char SNIPPET_START_MATCH = '\u0001';
    public static final char SNIPPET_END_MATCH = '\u0001';
    public static final String SNIPPET_ELLIPSIS = "\u2026";
    public static final int SNIPPET_MAX_TOKENS = 5;

    public static final String SNIPPET_ARGS = SNIPPET_START_MATCH + "," + SNIPPET_END_MATCH + ","
            + SNIPPET_ELLIPSIS + "," + SNIPPET_MAX_TOKENS;

    public ScDefaultContactListAdapter(Context context) {
        super(context);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        if (loader instanceof ProfileAndContactsLoader) {
            ((ProfileAndContactsLoader) loader).setLoadProfile(shouldIncludeProfile());
        }

        ContactListFilter filter = getFilter();
        if (isSearchMode()) {
            String query = getQueryString();
            if (query == null) {
                query = "";
            }
            query = query.trim();
            if (TextUtils.isEmpty(query)) {
                // Regardless of the directory, we don't want anything returned,
                // so let's just send a "nothing" query to the local directory.
                loader.setUri(RawContacts.CONTENT_URI);
                loader.setProjection(getProjection(false));
                loader.setSelection("0");
            } 
            else {
                Builder builder = RawContacts.CONTENT_FILTER_URI.buildUpon();
                builder.appendPath(query);      // Builder will encode the query
                builder.appendQueryParameter(ScContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId));
                if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
                    builder.appendQueryParameter(ScContactsContract.LIMIT_PARAM_KEY,
                            String.valueOf(getDirectoryResultLimit(directoryId)));
                }
                builder.appendQueryParameter(SearchSnippetColumns.SNIPPET_ARGS_PARAM_KEY, SNIPPET_ARGS);
                builder.appendQueryParameter(SearchSnippetColumns.DEFERRED_SNIPPETING_KEY,"1");
                loader.setUri(builder.build());
                loader.setProjection(getProjection(true));
            }
        } else {
            configureUri(loader, directoryId, filter);
            loader.setProjection(getProjection(false));
            configureSelection(loader, directoryId, filter);
        }

        String sortOrder;
        if (getSortOrder() == ScContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            sortOrder = RawContacts.SORT_KEY_PRIMARY;
        } else {
            sortOrder = RawContacts.SORT_KEY_ALTERNATIVE;
        }
        loader.setSortOrder(sortOrder);
    }

    protected void configureUri(CursorLoader loader, long directoryId, ContactListFilter filter) {
        Uri uri = RawContacts.CONTENT_URI;
        if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, getSelectedContactId());
        }

        if (directoryId == Directory.DEFAULT && isSectionHeaderDisplayEnabled()) {
            uri = buildSectionIndexerUri(uri);
        }

        // The "All accounts" filter is the same as the entire contents of Directory.DEFAULT
        if (filter != null
                && filter.filterType != ContactListFilter.FILTER_TYPE_CUSTOM
                && filter.filterType != ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            final Builder builder = uri.buildUpon();
            builder.appendQueryParameter(ScContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT));
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                filter.addAccountQueryParameterToUrl(builder);
            }
            uri = builder.build();
        }
        loader.setUri(uri);
    }

    private void configureSelection(CursorLoader loader, long directoryId, ContactListFilter filter) {
        if (filter == null) {
            return;
        }

        if (directoryId != Directory.DEFAULT) {
            return;
        }

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();
        
        selection.append(RawContacts.CONTACT_TYPE + "=" + RawContacts.CONTACT_TYPE_NORMAL);

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT: {
                // We have already added the lookup key to the URI, which takes care of this
                // filter
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                selection.append(" AND " + RawContacts.STARRED + "!=0");
                break;
            }
//  TODO           case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
//                selection.append(Contacts.HAS_PHONE_NUMBER + "=1");
//                break;
//            }
//            case ContactListFilter.FILTER_TYPE_CUSTOM: {
//                selection.append(RawContacts.IN_VISIBLE_GROUP + "=1");
//                if (isCustomFilterForPhoneNumbersOnly()) {
//                    selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
//                }
//                break;
//            }
//            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
//                // We use query parameters for account filter, so no selection to add here.
//                break;
//            }
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView)itemView;

        view.setHighlightedPrefix(isSearchMode() ? getUpperCaseQueryString() : null);

        if (isSelectionVisible()) {
            view.setActivated(isSelectedContact(partition, cursor));
        }

        bindSectionHeaderAndDivider(view, position, cursor);

        if (isQuickContactEnabled()) {
            bindQuickContact(view, partition, cursor, ContactQuery.CONTACT_PHOTO_ID,
                    ContactQuery.CONTACT_PHOTO_URI, ContactQuery.CONTACT_ID, ContactQuery.CONTACT_DISPLAY_NAME);
        } else {
            if (getDisplayPhotos()) {
                bindPhoto(view, partition, cursor);
            }
        }

        bindNameAndView(view, cursor);
        bindPresenceAndStatusMessage(view, cursor);

        if (isSearchMode()) {
            bindSearchSnippet(view, cursor);
        } else {
            view.setSnippet(null);
        }
    }

    private boolean isCustomFilterForPhoneNumbersOnly() {
        // TODO: this flag should not be stored in shared prefs.  It needs to be in the db.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getBoolean(ContactsPreferences.PREF_DISPLAY_ONLY_PHONES,
                ContactsPreferences.PREF_DISPLAY_ONLY_PHONES_DEFAULT);
    }
}
