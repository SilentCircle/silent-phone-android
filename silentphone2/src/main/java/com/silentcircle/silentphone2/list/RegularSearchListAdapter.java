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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.silentcircle.common.util.CachedNumberLookupService;
import com.silentcircle.contacts.calllognew.ContactInfo;

/**
 * List adapter to display regular search results.
 */
public class RegularSearchListAdapter extends DialerPhoneNumberListAdapter {

    public RegularSearchListAdapter(Context context) {
        super(context);
    }

    public CachedNumberLookupService.CachedContactInfo getContactInfo(CachedNumberLookupService lookupService, int position) {
        ContactInfo info = new ContactInfo();
        CachedNumberLookupService.CachedContactInfo cacheInfo = lookupService.buildCachedContactInfo(info);
        final Cursor item = (Cursor) getItem(position);

        if (item != null) {
            info.name = item.getString(PhoneQuery.PHONE_DISPLAY_NAME);
            info.type = item.getInt(PhoneQuery.PHONE_TYPE);
            info.label = item.getString(PhoneQuery.PHONE_LABEL);
            info.number = item.getString(PhoneQuery.PHONE_NUMBER);
            final String photoUriStr = item.getString(PhoneQuery.PHONE_PHOTO_URI);
            info.photoUri = photoUriStr == null ? null : Uri.parse(photoUriStr);

//            cacheInfo.setLookupKey(item.getString(PhoneQuery.LOOKUP_KEY));

//            final int partitionIndex = getPartitionForPosition(position);
//            final DirectoryPartition partition =
//                (DirectoryPartition) getPartition(partitionIndex);
//            final long directoryId = partition.getDirectoryId();
//            final String sourceName = partition.getLabel();
//            if (isExtendedDirectory(directoryId)) {
//                cacheInfo.setExtendedSource(sourceName, directoryId);
//            } else {
//                cacheInfo.setDirectorySource(sourceName, directoryId);
//            }
        }
        return cacheInfo;
    }

    @Override
    public void setQueryString(String queryString) {
        final boolean showNumberShortcuts = !TextUtils.isEmpty(getFormattedQueryString());
        boolean changed = setShortcutEnabled(SHORTCUT_DIRECT_CALL, showNumberShortcuts);
        // Either one of the add contacts options should be enabled. If the user entered
        // a dial-able number, then clicking add to contact should add it as a number.
        // Otherwise, it should add it to a new contact as a name.
        changed |= setShortcutEnabled(SHORTCUT_ADD_NUMBER_TO_CONTACTS, showNumberShortcuts);
//        changed |= setShortcutEnabled(SHORTCUT_MAKE_VIDEO_CALL, showNumberShortcuts /* && CallUtil.isVideoEnabled(getContext())*/);
        if (changed) {
            notifyDataSetChanged();
        }
        super.setQueryString(queryString);
    }
}
