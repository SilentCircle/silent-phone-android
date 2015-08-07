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

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.contacts.ContactsSearchManager;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.Intents;
import com.silentcircle.silentcontacts2.ScContactsContract.Intents.Insert;
import com.silentcircle.silentcontacts2.ScContactsContract.Intents.UI;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

/**
 * Parses a Contacts intent, extracting all relevant parts and packaging them
 * as a {@link ContactsRequest} object.
 */
public class ContactsIntentResolver {

    private static final String TAG = "ContactsIntentResolver";

    private final Activity mContext;

    public ContactsIntentResolver(Activity context) {
        this.mContext = context;
    }

    public ContactsRequest resolveIntent(Intent intent) {
        ContactsRequest request = new ContactsRequest();

        String action = intent.getAction();

        if (ConfigurationUtilities.mTrace) Log.i(TAG, "Called with action: " + action);

        if (UI.LIST_DEFAULT.equals(action) ) {
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
        }
        else if (UI.LIST_ALL_CONTACTS_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_ALL_CONTACTS);
        }
        else if (UI.LIST_CONTACTS_WITH_PHONES_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_CONTACTS_WITH_PHONES);
        }
        else if (UI.LIST_STARRED_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_STARRED);
        }
        else if (UI.LIST_FREQUENT_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_FREQUENT);
        }
        else if (UI.LIST_STREQUENT_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_STREQUENT);
        }
        else if (UI.LIST_GROUP_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_GROUP);
            // We no longer support UI.GROUP_NAME_EXTRA_KEY
        }
        else if (Intent.ACTION_PICK.equals(action)) {
            final String resolvedType = intent.resolveType(mContext);

            if (RawContacts.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT);
            }
            else if (Phone.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
            }
            else if (StructuredPostal.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
            }
            else if (Email.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_EMAIL);
            }
        }
        else if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            String component = intent.getComponent().getClassName();
            if (component.equals("alias.DialShortcut")) {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_CALL);
            } else if (component.equals("alias.MessageShortcut")) {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_SMS);
            } else {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT);
            }
        }
        else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            String type = intent.getType();

            if (RawContacts.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT);
            }
            else if (Phone.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
            }
            else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
            }
        }
        else if (Intent.ACTION_INSERT_OR_EDIT.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT);
        }
        else if (Intent.ACTION_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);

            // If the {@link SearchManager.QUERY} is empty, then check if a phone number, email
            // or IM contact is specified, in that priority.
            if (TextUtils.isEmpty(query)) {
                query = intent.getStringExtra(Insert.PHONE);
            }
            if (TextUtils.isEmpty(query)) {
                query = intent.getStringExtra(Insert.EMAIL);
            }
            if (TextUtils.isEmpty(query)) {
                query = intent.getStringExtra(Insert.IM_HANDLE);
            }
            request.setQueryString(query);
            request.setSearchMode(true);
        }
        else if (Intent.ACTION_VIEW.equals(action)) {
            final String resolvedType = intent.resolveType(mContext);
            if (ScContactsContract.RawContacts.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_ALL_CONTACTS);
            } 
            else {
                request.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
                request.setContactUri(intent.getData());
                intent.setAction(Intent.ACTION_DEFAULT);
                intent.setData(null);
            }
        }
        else if (UI.FILTER_CONTACTS_ACTION.equals(action)) {
            // When we get a FILTER_CONTACTS_ACTION, it represents search in the context
            // of some other action. Let's retrieve the original action to provide proper
            // context for the search queries.
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
            Bundle extras = intent.getExtras();
            if (extras != null) {
                request.setQueryString(extras.getString(UI.FILTER_TEXT_EXTRA_KEY));

                ContactsRequest originalRequest = (ContactsRequest)extras.get(ContactsSearchManager.ORIGINAL_REQUEST_KEY);
                if (originalRequest != null) {
                    request.copyFrom(originalRequest);
                }
            }

            request.setSearchMode(true);

        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        }
        else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            Uri data = intent.getData();
            request.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
            request.setContactUri(data);
            intent.setAction(Intent.ACTION_DEFAULT);
            intent.setData(null);
        }
        // Allow the title to be set to a custom String using an extra on the intent
        String title = intent.getStringExtra(UI.TITLE_EXTRA_KEY);
        if (title != null) {
            request.setActivityTitle(title);
        }
        return request;
    }
}
