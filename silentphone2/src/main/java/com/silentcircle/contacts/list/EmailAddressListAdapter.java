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
 * limitations under the License.
 */
package com.silentcircle.contacts.list;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.content.CursorLoader;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.contacts.model.account.LabelHelper.EmailLabel;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;

/**
 * A cursor adapter for the {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Email#CONTENT_TYPE} content type.
 */
public class EmailAddressListAdapter extends ScContactEntryListAdapter {

    protected static class EmailQuery {
        private static final String[] PROJECTION_PRIMARY = new String[] {
            Email._ID,                       // 0
            Email.TYPE,                      // 1
            Email.LABEL,                     // 2
            Email.DATA,                      // 3
            Email.PHOTO_ID,                  // 4
            Email.DISPLAY_NAME_PRIMARY,      // 5
        };

        private static final String[] PROJECTION_ALTERNATIVE = new String[] {
            Email._ID,                       // 0
            Email.TYPE,                      // 1
            Email.LABEL,                     // 2
            Email.DATA,                      // 3
            Email.PHOTO_ID,                  // 4
            Email.DISPLAY_NAME_ALTERNATIVE,  // 5
        };

        public static final int EMAIL_ID           = 0;
        public static final int EMAIL_TYPE         = 1;
        public static final int EMAIL_LABEL        = 2;
        public static final int EMAIL_ADDRESS      = 3;
        public static final int EMAIL_PHOTO_ID     = 4;
        public static final int EMAIL_DISPLAY_NAME = 5;
    }

    private final CharSequence mUnknownNameText;

    public EmailAddressListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        final Builder builder;
        if (isSearchMode()) {
            builder = Email.CONTENT_FILTER_URI.buildUpon();
            String query = getQueryString();
            builder.appendPath(TextUtils.isEmpty(query) ? "" : query);
        }
        else {
            builder = Email.CONTENT_URI.buildUpon();
            if (isSectionHeaderDisplayEnabled()) {
                builder.appendQueryParameter(ScContactsContract.ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true");
            }
        }
        builder.appendQueryParameter(ScContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId));
        builder.appendQueryParameter(ScContactsContract.REMOVE_DUPLICATE_ENTRIES, "true");
        loader.setUri(builder.build());

        if (getContactNameDisplayOrder() == ScContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            loader.setProjection(EmailQuery.PROJECTION_PRIMARY);
        }
        else {
            loader.setProjection(EmailQuery.PROJECTION_ALTERNATIVE);
        }

        if (getSortOrder() == ScContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Email.SORT_KEY_PRIMARY);
        }
        else {
            loader.setSortOrder(Email.SORT_KEY_ALTERNATIVE);
        }
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(EmailQuery.EMAIL_DISPLAY_NAME);
    }

    /**
     * Builds a {@link com.silentcircle.silentcontacts.ScContactsContract.Data#CONTENT_URI} for the current cursor
     * position.
     */
    public Uri getDataUri(int position) {
        long id = ((Cursor) getItem(position)).getLong(EmailQuery.EMAIL_ID);
        return ContentUris.withAppendedId(Data.CONTENT_URI, id);
    }

    @Override
    protected ContactListItemView newView(Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = super.newView(context, partition, cursor, position, parent);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        ContactListItemView view = (ContactListItemView)itemView;
        bindSectionHeaderAndDivider(view, position);
        bindName(view, cursor);
        bindPhoto(view, cursor);
        bindEmailAddress(view, cursor);
    }

    protected void bindEmailAddress(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(EmailQuery.EMAIL_TYPE)) {
            final int type = cursor.getInt(EmailQuery.EMAIL_TYPE);
            final String customLabel = cursor.getString(EmailQuery.EMAIL_LABEL);

            // TODO cache
            label = EmailLabel.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, EmailQuery.EMAIL_ADDRESS);
    }

    protected void bindSectionHeaderAndDivider(final ContactListItemView view, int position) {
        final int section = getSectionForPosition(position);
        if (getPositionForSection(section) == position) {
            String title = (String)getSections()[section];
            view.setSectionHeader(title);
        } 
        else {
// TODO remove           view.setDividerVisible(false);
            view.setSectionHeader(null);
        }

        // move the divider for the last item in a section
//        if (getPositionForSection(section + 1) - 1 == position) {
//            view.setDividerVisible(false);
//        }
//        else {
//            view.setDividerVisible(true);
//        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, EmailQuery.EMAIL_DISPLAY_NAME, getContactNameDisplayOrder());
    }

    protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
        long photoId = 0;
        if (!cursor.isNull(EmailQuery.EMAIL_PHOTO_ID)) {
            photoId = cursor.getLong(EmailQuery.EMAIL_PHOTO_ID);
        }
        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, true, true, null);
    }
//
//    protected void bindSearchSnippet(final ContactListItemView view, Cursor cursor) {
//        view.showSnippet(cursor, SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX,
//                SUMMARY_SNIPPET_DATA1_COLUMN_INDEX, SUMMARY_SNIPPET_DATA4_COLUMN_INDEX);
//    }

}
