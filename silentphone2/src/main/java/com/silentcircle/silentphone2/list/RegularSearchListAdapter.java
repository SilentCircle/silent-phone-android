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
import android.telephony.PhoneNumberUtils;

import com.silentcircle.common.util.CachedNumberLookupService;
import com.silentcircle.common.util.CachedNumberLookupService.CachedContactInfo;
import com.silentcircle.contacts.calllognew.ContactInfo;
import com.silentcircle.contacts.list.DirectoryPartition;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

/**
 * List adapter to display regular search results.
 */
public class RegularSearchListAdapter extends DialerPhoneNumberListAdapter {

    public RegularSearchListAdapter(Context context) {
        super(context, LoadUserInfo.canCallOutboundOca(context));
    }

    public CachedNumberLookupService.CachedContactInfo getContactInfo(CachedNumberLookupService lookupService, int position) {
        ContactInfo info = new ContactInfo();
        CachedContactInfo cacheInfo = lookupService.buildCachedContactInfo(info);
        final Cursor item = (Cursor) getItem(position);

        if (item != null) {
            info.name = item.getString(PhoneQuery.DISPLAY_NAME);
            info.type = item.getInt(PhoneQuery.PHONE_TYPE);
            info.label = item.getString(PhoneQuery.PHONE_LABEL);
            info.number = item.getString(PhoneQuery.PHONE_NUMBER);
            final String photoUriStr = item.getString(PhoneQuery.PHOTO_URI);
            info.photoUri = photoUriStr == null ? null : Uri.parse(photoUriStr);

            cacheInfo.setLookupKey(item.getString(PhoneQuery.LOOKUP_KEY));

            final int partitionIndex = getPartitionForPosition(position);
            final DirectoryPartition partition =
                (DirectoryPartition) getPartition(partitionIndex);
            final long directoryId = partition.getDirectoryId();
            final String sourceName = partition.getLabel();
            if (isExtendedDirectory(directoryId)) {
                cacheInfo.setExtendedSource(sourceName, directoryId);
            } else {
                cacheInfo.setDirectorySource(sourceName, directoryId);
            }
        }
        return cacheInfo;
    }

    @Override
    public void setQueryString(String queryString) {
        final boolean showNumberShortcuts =
                PhoneNumberUtils.isGlobalPhoneNumber(queryString.replaceAll("\\s","")
                        .replaceAll("[()]", ""))
                || queryString.startsWith("*");
        final boolean noExactMatch = isDirectoryEmpty(SC_EXACT_MATCH_ON_V1_USER);
        boolean changed = setShortcutEnabled(SHORTCUT_DIRECT_CALL,
                showNumberShortcuts && !isCheckable() && noExactMatch);
        // Either one of the add contacts options should be enabled. If the user entered
        // a dial-able number, then clicking add to contact should add it as a number.
        // Otherwise, it should add it to a new contact as a name.
//        changed |= setShortcutEnabled(SHORTCUT_ADD_NUMBER_TO_CONTACTS, showNumberShortcuts);

        // For NGA: Don't show the "add to contacts option"
        changed |= setShortcutEnabled(SHORTCUT_ADD_NUMBER_TO_CONTACTS, false);
        changed |= setShortcutEnabled(SHORTCUT_DIRECT_CONVERSATION, false);
//        changed |= setShortcutEnabled(SHORTCUT_MAKE_VIDEO_CALL, showNumberShortcuts /* && CallUtil.isVideoEnabled(getContext())*/);
        if (changed) {
            notifyDataSetChanged();
        }
        super.setQueryString(queryString);
    }

    @Override
    public void onChangeCursor(int partitionIndex) {
        final String queryString = getQueryString();
        final boolean showNumberShortcuts =
                PhoneNumberUtils.isGlobalPhoneNumber(queryString.replaceAll("\\s","")
                        .replaceAll("[()]", ""))
                        || queryString.startsWith("*");
        final boolean noExactMatch = isDirectoryEmpty(SC_EXACT_MATCH_ON_V1_USER);
        boolean changed = setShortcutEnabled(SHORTCUT_DIRECT_CALL,
                showNumberShortcuts && !isCheckable() && noExactMatch);
        if (changed) {
            notifyDataSetChanged();
        }
    }

}
