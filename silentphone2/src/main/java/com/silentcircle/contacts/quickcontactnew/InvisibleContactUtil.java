/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.contacts.quickcontactnew;


import com.google.common.collect.Iterables;

import com.silentcircle.contacts.ScContactSaveService;
import com.silentcircle.contacts.GroupMetaData;
import com.silentcircle.contacts.model.AccountTypeManager;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.model.RawContact;
import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.RawContactDelta.ValuesDelta;
import com.silentcircle.contacts.model.RawContactDeltaList;
import com.silentcircle.contacts.model.RawContactModifier;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.model.dataitem.DataItem;
import com.silentcircle.contacts.model.dataitem.DataKind;
import com.silentcircle.contacts.model.dataitem.GroupMembershipDataItem;

import android.content.Context;
import android.content.Intent;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;

import java.util.List;

/**
 * Utility class to support adding invisible contacts. Ie, contacts that don't belong to the
 * default group.
 */
public class InvisibleContactUtil {

    public static boolean isInvisibleAndAddable(Contact contactData, Context context) {
        // Only local contacts
        if (contactData == null || contactData.isDirectoryEntry()) return false;

        // User profile cannot be added to contacts
        if (contactData.isUserProfile()) return false;

        // Only if exactly one raw contact
        if (contactData.getRawContacts().size() != 1) return false;

        // test if the default group is assigned
        final List<GroupMetaData> groups = contactData.getGroupMetaData();

        // For accounts without group support, groups is null
        if (groups == null) return false;

        // remember the default group id. no default group? bail out early
        final long defaultGroupId = getDefaultGroupId(groups);
        if (defaultGroupId == -1) return false;

        final RawContact rawContact = (RawContact) contactData.getRawContacts().get(0);
        final AccountType type = rawContact.getAccountType(/* context */);
        // Offline or non-writeable account? Nothing to fix
        if (type == null || !type.areContactsWritable()) return false;

        // Check whether the contact is in the default group
        boolean isInDefaultGroup = false;
        for (DataItem dataItem : Iterables.filter(
                rawContact.getDataItems(), GroupMembershipDataItem.class)) {
            GroupMembershipDataItem groupMembership = (GroupMembershipDataItem) dataItem;
            final Long groupId = groupMembership.getGroupRowId();
            if (groupId != null && groupId == defaultGroupId) {
                isInDefaultGroup = true;
                break;
            }
        }

        return !isInDefaultGroup;
    }

    public static void addToDefaultGroup(Contact contactData, Context context) {
        final long defaultGroupId = getDefaultGroupId(contactData.getGroupMetaData());
        // there should always be a default group (otherwise the button would be invisible),
        // but let's be safe here
        if (defaultGroupId == -1) return;

        // add the group membership to the current state
        final RawContactDeltaList contactDeltaList = contactData.createRawContactDeltaList();
        final RawContactDelta rawContactEntityDelta = contactDeltaList.get(0);

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(
                context);
        final AccountType type = rawContactEntityDelta.getAccountType(accountTypes);
        final DataKind groupMembershipKind = type.getKindForMimetype(
                GroupMembership.CONTENT_ITEM_TYPE);
        final ValuesDelta entry = RawContactModifier.insertChild(rawContactEntityDelta,
                groupMembershipKind);
        if (entry == null) return;
        entry.setGroupRowId(defaultGroupId);

        // and fire off the intent. we don't need a callback, as the database listener
        // should update the ui
        final Intent intent = ScContactSaveService.createSaveContactIntent(
                context,
                contactDeltaList, "", 0, false, QuickContactActivityV21.class,
                Intent.ACTION_VIEW, null);
        context.startService(intent);
    }

    /** return default group id or -1 if no group or several groups are marked as default */
    private static long getDefaultGroupId(List<GroupMetaData> groups) {
        long defaultGroupId = -1;
        for (GroupMetaData group : groups) {
            if (group.isDefaultGroup()) {
                // two default groups? return neither
                if (defaultGroupId != -1) return -1;
                defaultGroupId = group.getGroupId();
            }
        }
        return defaultGroupId;
    }
}
