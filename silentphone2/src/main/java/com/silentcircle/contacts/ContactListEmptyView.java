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

package com.silentcircle.contacts;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.widget.ScrollView;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

/**
 * Displays a message when there is nothing to display in a contact list.
 */
public class ContactListEmptyView extends ScrollView {

    private static final String TAG = "ContactListEmptyView";

    public ContactListEmptyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void hide() {
        TextView empty = (TextView) findViewById(R.id.emptyText);
        empty.setVisibility(GONE);
    }

    public void show(boolean searchMode, boolean displayOnlyPhones,
            boolean isFavoritesMode, boolean isQueryMode, boolean isShortcutAction,
            boolean isMultipleSelectionEnabled, boolean showSelectedOnly) {
        if (searchMode) {
            return;
        }

        TextView empty = (TextView) findViewById(R.id.emptyText);
        Context context = getContext();
        if (displayOnlyPhones) {
            empty.setText(context.getText(R.string.noContactsWithPhoneNumbers));
        } else if (isFavoritesMode) {
            empty.setText(context.getText(R.string.noFavoritesHelpText));
        } else if (isQueryMode) {
            empty.setText(context.getText(R.string.noMatchingContacts));
        } if (isMultipleSelectionEnabled) {
            if (showSelectedOnly) {
                empty.setText(context.getText(R.string.no_contacts_selected));
            } else {
                empty.setText(context.getText(R.string.noContactsWithPhoneNumbers));
            }
        } else {
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            boolean hasSim = telephonyManager.hasIccCard();
            if (isSyncActive()) {
                if (isShortcutAction) {
                    // Help text is the same no matter whether there is SIM or not.
                    empty.setText(
                            context.getText(R.string.noContactsHelpTextWithSyncForCreateShortcut));
                } else if (hasSim) {
                    empty.setText(context.getText(R.string.noContactsHelpTextWithSync));
                } else {
                    empty.setText(context.getText(R.string.noContactsNoSimHelpTextWithSync));
                }
            } else {
                if (isShortcutAction) {
                    // Help text is the same no matter whether there is SIM or not.
                    empty.setText(context.getText(R.string.noContactsHelpTextForCreateShortcut));
                } else if (hasSim) {
                    empty.setText(context.getText(R.string.noContactsHelpText));
                } else {
                    empty.setText(context.getText(R.string.noContactsNoSimHelpText));
                }
            }
        }
        empty.setVisibility(VISIBLE);
    }

    private boolean isSyncActive() {
//        Account[] accounts = AccountManager.get(getContext()).getAccounts();
//        if (accounts != null && accounts.length > 0) {
//            IContentService contentService = ContentResolver.getContentService();
//            for (Account account : accounts) {
//                try {
//                    if (contentService.isSyncActive(account, ContactsContract.AUTHORITY)) {
//                        return true;
//                    }
//                } catch (RemoteException e) {
//                    Log.e(TAG, "Could not get the sync status");
//                }
//            }
//        }
        return false;
    }
}
