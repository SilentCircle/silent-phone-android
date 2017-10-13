/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.silentcircle.contacts.list.ContactListFilter;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;
import com.silentcircle.contacts.list.ScDirectoryLoader;
import com.silentcircle.contacts.list.ScV1UserLoader;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.activities.GroupManagementActivity;
import com.silentcircle.messaging.task.ScConversationLoader;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessagingSearchListAdapter;
import com.silentcircle.silentphone2.list.DialerPhoneNumberListAdapter;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.List;

/**
 * Fragment for contact selection when forwarding a message.
 */
public class SearchAgainFragment extends com.silentcircle.silentphone2.list.SearchFragment
        implements GroupManagementActivity.OnWizardStateChangeListener {

    private static final String TAG = SearchAgainFragment.class.getSimpleName();

    public static final String TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT =
            "com.silentcircle.messaging.fragments.ContactsSearch";

    public SearchAgainFragment() {
        setPhotoLoaderEnabled(true);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // This filter works in the non-search mode only
        setFilter(ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_WITH_SIP_NUMBERS_ONLY));
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // hide unnecessary menu items
        for (int i = 0; i < menu.size(); ++i) {
            final MenuItem item = menu.getItem(i);
            item.setVisible(false);
        }
    }

    @Override
    protected ScContactEntryListAdapter createListAdapter() {
        MessagingSearchListAdapter adapter = new MessagingSearchListAdapter(getActivity(),
                !inUserSelectionMode());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(usesCallableUri());
        adapter.searchScData(false);
        adapter.setShowIfEmpty(0, true);
        adapter.setShowIfEmpty(1, false);

        // This filter works in non-search and in search mode
        /*
         * Disable this filter as it would remove all silentphone:* entries form contacts.
         *
         * adapter.setContentFilter(".*" + getString(R.string.sc_sip_domain_0),
         *     PhoneNumberListAdapter.PhoneQuery.PHONE_NUMBER);
         */
        adapter.setScDirectoryFilter(ScDirectoryLoader.NAMES_ONLY);
        /* Show exact-match section */
        adapter.setScExactMatchFilter(ScV1UserLoader.SHOW);
        adapter.setCheckable(inUserSelectionMode());
        adapter.setSelectedItems(getSelectedItems());
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        // currently this fragment can be entered for message forward and for group
        // conversation member selection

        // show conversation shortcut for message forward
        // never show group conversation shortcut
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        if (adapter != null) {
            /*
             * Don't show group conversations when selecting users for group,
             * Otherwise assume message is forwarded and show groups.
             */
            adapter.setScConversationFilter(inUserSelectionMode()
                    ? ScConversationLoader.NONE
                    : ScConversationLoader.GROUP_CONVERSATIONS_ONLY);

            boolean changed = adapter.setShortcutEnabled(
                    DialerPhoneNumberListAdapter.SHORTCUT_START_GROUP_CHAT, false);
            changed |= adapter.setShortcutEnabled(
                    DialerPhoneNumberListAdapter.SHORTCUT_ADD_TO_GROUP_CHAT, false);
            if (inUserSelectionMode()) {
                changed |= adapter.setShortcutEnabled(
                        DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL, false);
                changed |= adapter.setShortcutEnabled(
                        DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION, false);
            }
            if (changed) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        final MessagingSearchListAdapter adapter = (MessagingSearchListAdapter) getAdapter();
        // use adjusted position to determine correct contact entry
        final int adjustedPosition = adapter.getSuperPosition(position);
        // determine shortcut type from unadjusted position as shortcuts are at the top
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final String number = Utilities.removeUriPartsSelective(adapter.getPhoneNumber(adjustedPosition));

        final Cursor cursor = (Cursor)adapter.getItem(adjustedPosition);
        String uuid = number;
        if (cursor != null) {
            final int scIndex = cursor.getColumnIndex(ScDirectoryLoader.SC_UUID_FIELD);
            if (scIndex >= 0) {
                uuid = cursor.getString(scIndex);
            }
        }
        ScDirectoryLoader.clearCachedData();

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                /*
                 * User clicked on a search result item, start conversation if username is not
                 * empty, without username validation through API call.
                 */
                if (isGroup(adapter, adjustedPosition)) {
                    startGroupConversation(null, getUuid(adapter, adjustedPosition), true);
                }
                else if (TextUtils.isEmpty(number)) {
                    Log.e(TAG, "Click on a contact item gives empty number. Cannot start conversation. " + number);
                }
                else {
                    if (!inUserSelectionMode()) {
                        validateUser(uuid);
                    } else {
                        selectItem(position);
                    }
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION:
                if (!inUserSelectionMode()) {
                    validateUser(getQueryString());
                } else {
                    validateUserAndStartGroupConversation(getGroupName(), getQueryString());
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_ADD_TO_GROUP_CHAT:
                if (inUserSelectionMode()) {
                    addToSelection(getQueryString());
                }
                clearQuery();
            default:
                Log.e(TAG, "Wrong shortcut type detected: " + shortcutType);
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        setDialpadEnabled(false);

        // restore query string in action bar if known (saved) query string is different
        setVisibleSearchQueryIfDifferent(getQueryString());
    }

    @Override
    public boolean onExitView() {
        Activity activity = getActivity();
        if (activity instanceof GroupManagementActivity) {
            ((GroupManagementActivity) activity).closeActionBarQueryField();
        }
        return false;
    }

    @Override
    public void onVisibilityRestored() {
        Activity activity = getActivity();
        if (activity instanceof GroupManagementActivity) {
            ((GroupManagementActivity) activity).openActionBarQueryField();
        }
    }

    @Override
    protected void addSelectionToConversation() {
        Activity activity = getActivity();
        // pass selected items to origin
        // TODO a better way
        List<String> uuids = getSelectedUuids();
        if (!uuids.isEmpty()) {
            Intent intent = null;
            if (activity instanceof GroupManagementActivity) {
                // in group creation wizard
                intent = new Intent(activity, GroupManagementActivity.class);
                intent.putExtra(GroupManagementActivity.TASK, GroupManagementActivity.TASK_CREATE_NEW);
                intent.putExtra(GroupManagementActivity.GROUP_MEMBERS,
                        uuids.toArray(new CharSequence[uuids.size()]));
                intent.putExtra(GroupManagementActivity.GROUP_NAME, getGeneratedGroupName(uuids));
            }
            else if (activity instanceof ConversationActivity) {
                // in group chat, adding participants
                intent = getConversationIntent(null, getGroupName(), false, activity);
                Extra.GROUP.to(intent, getGroupName());
                Extra.IS_GROUP.to(intent, true);
                CharSequence[] members = uuids.toArray(new CharSequence[uuids.size()]);
                Extra.PARTICIPANTS.to(intent, members);
            }
            if (intent != null) {
                startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        }
    }

}
