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

package com.silentcircle.common.list;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.PinnedPositions;
import android.text.TextUtils;

/**
 * Class to hold contact information
 */
public class ContactEntry {
    public String name;
    public String status;
    public String phoneLabel;
    public String phoneNumber;
    public String imName;
    public String alias;
    public Uri photoUri;
    public Uri lookupUri;
    public Uri thumbnailUri;
    public String lookupKey;
    public Drawable presenceIcon;
    public long id;
    public long photoId;
    public int pinned = PinnedPositions.UNPINNED;
    public boolean isFavorite = false;
    public boolean isDefaultNumber = false;
    public long timeCreated;

    public static final ContactEntry BLANK_ENTRY = new ContactEntry();

    @Override
    public String toString() {
        return "name: " + name + ", status: " + status + ", phoneLabel: " + phoneLabel
                + ", phoneNumber: " + phoneNumber + ", imName: " + imName + ", alias: " + alias
                + ", photoUri: " + photoUri + ", lookupUri: " + lookupUri
                + ", thumbnailUri: " + thumbnailUri + ", id: " + id + ", photoId: " + photoId
                + ", pinned: " + pinned + ", timeCreated: " + timeCreated;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof ContactEntry)) return false;

        ContactEntry entry = (ContactEntry) obj;
        return id == entry.id
                && photoId == entry.photoId
                && timeCreated == entry.timeCreated
                && TextUtils.equals(name, entry.name)
                && TextUtils.equals(status, entry.status)
                && TextUtils.equals(phoneLabel, entry.phoneLabel)
                && TextUtils.equals(phoneNumber, entry.phoneNumber)
                && TextUtils.equals(imName, entry.imName)
                && TextUtils.equals(alias, entry.alias)
                && TextUtils.equals(lookupKey, entry.lookupKey)
                && TextUtils.equals(photoUri == null ? null : photoUri.toString(),
                    entry.photoUri == null ? null : entry.photoUri.toString())
                && TextUtils.equals(lookupUri == null ? null : lookupUri.toString(),
                    entry.lookupUri == null ? null : entry.lookupUri.toString());
    }
}
