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

import com.silentcircle.silentcontacts2.ScContactsContract.Groups;

/**
 * Group loader for the group list that includes details such as the number of contacts per group
 * and number of groups per account. This list is sorted by account type, account name, where the
 * group names are in alphabetical order. Note that the list excludes default, favorite, and deleted
 * groups.
 */
public final class GroupListLoader extends CursorLoader {

    private final static String[] COLUMNS = new String[] {
        Groups._ID,
        Groups.TITLE,
        Groups.SUMMARY_COUNT,
    };

    public final static int GROUP_ID = 0;
    public final static int TITLE = 1;
    public final static int MEMBER_COUNT = 2;

    private static final Uri GROUP_LIST_URI = Groups.CONTENT_SUMMARY_URI;

    public GroupListLoader(Context context) {
        super(context, GROUP_LIST_URI, COLUMNS, Groups.AUTO_ADD + "=0 AND " +
                Groups.FAVORITES + "=0 AND " + Groups.DELETED + "=0", null,
                Groups.TITLE + " COLLATE LOCALIZED ASC");
    }
}
