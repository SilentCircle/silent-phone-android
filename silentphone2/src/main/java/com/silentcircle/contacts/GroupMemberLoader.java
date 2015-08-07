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
 * limitations under the License
 */
package com.silentcircle.contacts;

import android.content.Context;
import android.net.Uri;
import android.content.CursorLoader;

import com.silentcircle.contacts.preference.ContactsPreferences;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.GroupMembership;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.Directory;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import java.util.ArrayList;
import java.util.List;

/**
 * Group Member loader. Loads all group members from the given groupId
 */
public final class GroupMemberLoader extends CursorLoader {

    public static class GroupEditorQuery {
        private static final String[] PROJECTION = new String[] {
            Data.RAW_CONTACT_ID,                    // 0
            Data.DISPLAY_NAME_PRIMARY,              // 1
            Data.PHOTO_URI,                         // 2
//            Data.LOOKUP_KEY,                        // 3
        };

        public static final int RAW_CONTACT_ID               = 0;
        public static final int CONTACT_DISPLAY_NAME_PRIMARY = 1;
        public static final int CONTACT_PHOTO_URI            = 2;
//        public static final int CONTACT_LOOKUP_KEY           = 3;
    }

    public static class GroupDetailQuery {
        private static final String[] PROJECTION = new String[] {
            Data.PHOTO_URI,                         // 0
            Data.DISPLAY_NAME_PRIMARY,              // 2
            Data.RAW_CONTACT_ID,                        // 0
        };

        public static final int CONTACT_PHOTO_URI            = 0;
        public static final int CONTACT_DISPLAY_NAME_PRIMARY = 1;
        public static final int CONTACT_ID                   = 2;

    }

    private final long mGroupId;

    /**
     * @return GroupMemberLoader object which can be used in group editor.
     */
    public static GroupMemberLoader constructLoaderForGroupEditorQuery(Context context, long groupId) {
        return new GroupMemberLoader(context, groupId, GroupEditorQuery.PROJECTION);
    }

    /**
     * @return GroupMemberLoader object used in group detail page.
     */
    public static GroupMemberLoader constructLoaderForGroupDetailQuery(Context context, long groupId) {
        return new GroupMemberLoader(context, groupId, GroupDetailQuery.PROJECTION);
    }

    private GroupMemberLoader(Context context, long groupId, String[] projection) {
        super(context);
        mGroupId = groupId;
        setUri(createUri());
        setProjection(projection);
        setSelection(createSelection());
        setSelectionArgs(createSelectionArgs());

        ContactsPreferences prefs = new ContactsPreferences(context);
        if (prefs.getSortOrder() == ScContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            setSortOrder(RawContacts.SORT_KEY_PRIMARY);
        }
        else {
            setSortOrder(RawContacts.SORT_KEY_ALTERNATIVE);
        }
    }

    private Uri createUri() {
        Uri uri = Data.CONTENT_URI;
        uri = uri.buildUpon().appendQueryParameter(ScContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                .build();
        return uri;
    }

    private String createSelection() {
        return (Data.MIMETYPE + "=?" + " AND " + GroupMembership.GROUP_ROW_ID + "=?");
    }

    private String[] createSelectionArgs() {
        List<String> selectionArgs = new ArrayList<>();
        selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
        selectionArgs.add(String.valueOf(mGroupId));
        return selectionArgs.toArray(new String[selectionArgs.size()]);
    }
}
