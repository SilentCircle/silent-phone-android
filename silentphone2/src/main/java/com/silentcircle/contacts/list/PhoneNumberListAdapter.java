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
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactPhotoManagerNew.DefaultImageRequest;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.model.account.LabelHelper.PhoneLabel;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Callable;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A cursor adapter for the {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Phone#CONTENT_ITEM_TYPE} and
 * { @link SipAddress#CONTENT_ITEM_TYPE}.
 *
 * By default this adapter just handles phone numbers. When {@link #setUseCallableUri(boolean)} is
 * called with "true", this adapter starts handling SIP addresses too, by using {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Callable}
 * API instead of {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Phone}.
 */
public class PhoneNumberListAdapter extends ScContactEntryListAdapter {
    private static final String TAG = PhoneNumberListAdapter.class.getSimpleName();

    public static class PhoneQuery {
        public static final String[] PROJECTION_PRIMARY = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.RAW_CONTACT_ID,               // 4
            Phone.PHOTO_ID,                     // 5
            Phone.DISPLAY_NAME_PRIMARY,         // 6
            Phone.PHOTO_URI,                    // 7
        };

        public static final String[] PROJECTION_ALTERNATIVE = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.RAW_CONTACT_ID,               // 4
            Phone.PHOTO_ID,                     // 5
            Phone.DISPLAY_NAME_ALTERNATIVE,     // 6
            Phone.PHOTO_URI,                    // 7
        };

        public static final int PHONE_ID           = 0;
        public static final int PHONE_TYPE         = 1;
        public static final int PHONE_LABEL        = 2;
        public static final int PHONE_NUMBER       = 3;
        public static final int PHONE_CONTACT_ID   = 4;
        public static final int PHONE_PHOTO_ID     = 5;
        public static final int PHONE_DISPLAY_NAME = 6;
        public static final int PHONE_PHOTO_URI    = 7;
    }

    private final CharSequence mUnknownNameText;

    private ContactListItemView.PhotoPosition mPhotoPosition;

    private boolean mUseCallableUri;

    public PhoneNumberListAdapter(Context context, boolean enableScDir) {
        super(context, enableScDir);
        setDefaultFilterHeaderText(R.string.list_filter_phones);
        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    protected CharSequence getUnknownNameText() {
        return mUnknownNameText;
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        if (directoryId != Directory.DEFAULT) {
            Log.w(TAG, "PhoneNumberListAdapter is not ready for non-default directory ID ("
                    + "directoryId: " + directoryId + ")");
        }
        final Builder builder;
        if (isSearchMode()) {
            final Uri baseUri =  mUseCallableUri ? Callable.CONTENT_FILTER_URI : Phone.CONTENT_FILTER_URI;
            builder = baseUri.buildUpon();
            final String query = getQueryString();
            if (TextUtils.isEmpty(query)) {
                builder.appendPath("");
            } else {
                builder.appendPath(query);      // Builder will encode the query
            }
            builder.appendQueryParameter(ScContactsContract.DIRECTORY_PARAM_KEY,
                    String.valueOf(directoryId));
        }
        else {
            final Uri baseUri = mUseCallableUri ? Callable.CONTENT_URI : Phone.CONTENT_URI;
            builder = baseUri.buildUpon().appendQueryParameter(
                    ScContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT));
            if (isSectionHeaderDisplayEnabled()) {
                builder.appendQueryParameter(ScContactsContract.ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true");
            }
            applyFilter(loader, builder, directoryId, getFilter());
        }

        // Remove duplicates when it is possible.
        builder.appendQueryParameter(ScContactsContract.REMOVE_DUPLICATE_ENTRIES, "true");
        loader.setUri(builder.build());

        // TODO a projection that includes the search snippet
        if (getContactNameDisplayOrder() == ScContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            loader.setProjection(PhoneQuery.PROJECTION_PRIMARY);
        } else {
            loader.setProjection(PhoneQuery.PROJECTION_ALTERNATIVE);
        }

        if (getSortOrder() == ScContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Phone.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(Phone.SORT_KEY_ALTERNATIVE);
        }
    }

    public void configureScDirLoader(ScDirectoryLoader loader) {
        boolean displayAlternative = !(getContactNameDisplayOrder() == ScContactsContract.Preferences.DISPLAY_ORDER_PRIMARY);
        boolean sortAlternative = !(getSortOrder() == ScContactsContract.Preferences.SORT_ORDER_PRIMARY);

        loader.setDisplayAlternative(displayAlternative);
        loader.setSortAlternative(sortAlternative);
        loader.setQueryString(getQueryString());
        loader.useScDirectoryOrganization(isUseScDirLoaderOrg());
    }

    /**
     * Configure {@code loader} and {@code uriBuilder} according to {@code directoryId} and {@code
     * filter}.
     */
    private void applyFilter(CursorLoader loader, Builder uriBuilder, long directoryId, ContactListFilter filter) {
        if (filter == null || directoryId != Directory.DEFAULT) {
            return;
        }

        final StringBuilder selection = new StringBuilder();
        final List<String> selectionArgs = new ArrayList<String>();

        switch (filter.filterType) {
//            case ContactListFilter.FILTER_TYPE_CUSTOM: {
//                selection.append(RawContacts.IN_VISIBLE_GROUP + "=1");
//                selection.append(" AND " + RawContacts.HAS_PHONE_NUMBER + "=1");
//                break;
//            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                filter.addAccountQueryParameterToUrl(uriBuilder);
                break;
            }
//            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS:
            case ContactListFilter.FILTER_TYPE_DEFAULT:
                break; // No selection needed.
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                break; // This adapter is always "phone only", so no selection needed either.
            default:
                Log.w(TAG, "Unsupported filter type came " +
                        "(type: " + filter.filterType + ", toString: " + filter + ")" +
                        " showing all contacts.");
                // No selection.
                break;
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(PhoneQuery.PHONE_DISPLAY_NAME);
    }

    public String getPhoneNumber(int position) {
        final Cursor item = (Cursor)getItem(position);
        if (item == null)
            return null;
        String numName = item.getString(PhoneQuery.PHONE_NUMBER);
        if (Character.isLetter(numName.charAt(0)))
            return numName+getContext().getString(R.string.sc_sip_domain_0);
        return numName;
    }

    /**
     * Builds a {@link com.silentcircle.silentcontacts.ScContactsContract.Data#CONTENT_URI} for the given cursor position.
     *
     * @return Uri for the data. may be null if the cursor is not ready.
     */
    public Uri getDataUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));

        int pi = getPartitionForPosition(position);
        if (cursor != null) {
            if (((DirectoryPartition)getPartition(pi)).getDirectoryId() == SC_DIRECTORY) {
                return null;            // we don't have a data URI for SC directory entries/numbers
            }
            else {
                long id = cursor.getLong(PhoneQuery.PHONE_ID);
                return ContentUris.withAppendedId(Data.CONTENT_URI, id);
            }
        } else {
            Log.w(TAG, "Cursor was null in getDataUri() call. Returning null instead.");
            return null;
        }
    }

    @Override
    protected ContactListItemView newView(Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = super.newView(context, partition, cursor, position, parent);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        view.setPhotoPosition(mPhotoPosition);
        return view;
    }

    protected void setHighlight(ContactListItemView view, Cursor cursor) {
        view.setHighlightedPrefix(isSearchMode() ? getUpperCaseQueryString() : null);
    }

    // Override default, which would return number of phone numbers, so we
    // instead return number of contacts.
    @Override
    protected int getResultCount(Cursor cursor) {
        if (cursor == null) {
            return 0;
        }
        cursor.moveToPosition(-1);
        long curContactId = -1;
        int numContacts = 0;
        while(cursor.moveToNext()) {
            final long contactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
            if (contactId != curContactId) {
                curContactId = contactId;
                ++numContacts;
            }
        }
        return numContacts;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        ContactListItemView view = (ContactListItemView)itemView;

        view.setHighlightedPrefix(isSearchMode() ? getUpperCaseQueryString() : null);

        // Look at elements before and after this position, checking if contact IDs are same.
        // If they have one same contact ID, it means they can be grouped.
        //
        // In one group, only the first entry will show its photo and its name, and the other
        // entries in the group show just their data (e.g. phone number, email address).
        cursor.moveToPosition(position);
        boolean isFirstEntry = true;
        boolean showBottomDivider = true;
        final long currentContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
        if (cursor.moveToPrevious() && !cursor.isBeforeFirst()) {
            final long previousContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
            if (currentContactId == previousContactId) {
                isFirstEntry = false;
            }
        }
        cursor.moveToPosition(position);
        if (cursor.moveToNext() && !cursor.isAfterLast()) {
            final long nextContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
            if (currentContactId == nextContactId) {
                // The following entry should be in the same group, which means we don't want a
                // divider between them.
                // TODO: we want a different divider than the divider between groups. Just hiding
                // this divider won't be enough.
                showBottomDivider = false;
            }
        }
        cursor.moveToPosition(position);

        bindSectionHeaderAndDivider(view, position);
        if (isFirstEntry) {
            bindName(view, cursor);
            if (isQuickContactEnabled()) {
                // No need for photo uri here, because we can not have directory results. If we
                // ever do, we need to add photo uri to the query
                bindQuickContact(view, partition, cursor, PhoneQuery.PHONE_PHOTO_ID, -1, 
                        PhoneQuery.PHONE_CONTACT_ID, PhoneQuery.PHONE_DISPLAY_NAME);
            } 
            else {
                bindPhoto(view, cursor);
            }
        } 
        else {
            unbindName(view);

            view.removePhotoView(true, false);
        }
        bindPhoneNumber(view, cursor);
// TODO remove        view.setDividerVisible(showBottomDivider);
    }

    protected void bindPhoneNumber(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(PhoneQuery.PHONE_TYPE)) {
            final int type = cursor.getInt(PhoneQuery.PHONE_TYPE);
            final String customLabel = cursor.getString(PhoneQuery.PHONE_LABEL);

            // TODO cache
            label = PhoneLabel.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, PhoneQuery.PHONE_NUMBER);
    }

    protected void bindSectionHeaderAndDivider(final ContactListItemView view, int position) {
        if (isSectionHeaderDisplayEnabled()) {
            Placement placement = getItemPlacementInSection(position);
            view.setSectionHeader(placement.firstInSection ? placement.sectionHeader : null);
// TODO remove            view.setDividerVisible(!placement.lastInSection);
        } 
        else {
            view.setSectionHeader(null);
// TODO remove            view.setDividerVisible(true);
        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, PhoneQuery.PHONE_DISPLAY_NAME, getContactNameDisplayOrder());
        // Note: we don't show phonetic names any more (see issue 5265330)
    }

    protected void unbindName(final ContactListItemView view) {
        view.hideDisplayName();
    }

    protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
        final String lookupKey = ScContactsContract.RawContacts.getLookupUri(cursor.getLong(PhoneQuery.PHONE_CONTACT_ID)).toString();
        final String displayName = cursor.getString(PhoneQuery.PHONE_DISPLAY_NAME);
        DefaultImageRequest request =  new DefaultImageRequest(displayName, lookupKey, true);
        long photoId = 0;
        if (!cursor.isNull(PhoneQuery.PHONE_PHOTO_ID)) {
            photoId = cursor.getLong(PhoneQuery.PHONE_PHOTO_ID);
        }
        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, true, true, request);
    }

    public void setPhotoPosition(ContactListItemView.PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
    }

    public ContactListItemView.PhotoPosition getPhotoPosition() {
        return mPhotoPosition;
    }

    public void setUseCallableUri(boolean useCallableUri) {
        mUseCallableUri = useCallableUri;
    }

    public boolean usesCallableUri() {
        return mUseCallableUri;
    }
}
