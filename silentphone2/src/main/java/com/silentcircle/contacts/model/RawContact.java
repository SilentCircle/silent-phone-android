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
 * Copyright (C) 2012 The Android Open Source Project
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

package com.silentcircle.contacts.model;

import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.net.Uri;

import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.model.dataitem.DataItem;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import java.util.ArrayList;
import java.util.List;

//import com.android.contacts.model.account.AccountWithDataSet;

/**
 * RawContact represents a single raw contact in the raw contacts database.
 * It has specialized getters/setters for raw contact
 * items, and also contains a collection of DataItem objects.  A RawContact contains the information
 * from a single account.
 *
 * This allows RawContact objects to be thought of as a class with raw contact
 * fields (like account type, name, data set, sync state, etc.) and a list of
 * DataItem objects that represent contact information elements (like phone
 * numbers, email, address, etc.).
 */
public class RawContact {

    private final Context mContext;
    private AccountTypeManager mAccountTypeManager;
    private final ContentValues mValues;
    private final ArrayList<NamedDataItem> mDataItems;

    public static class NamedDataItem {
        public final Uri uri;
        public final DataItem dataItem;

        public NamedDataItem(Uri uri, DataItem dataItem) {
            this.uri = uri;
            this.dataItem = dataItem;
        }
    }

    public static RawContact createFrom(Entity entity) {
        final ContentValues values = entity.getEntityValues();
        final ArrayList<Entity.NamedContentValues> subValues = entity.getSubValues();

        RawContact rawContact = new RawContact(null, values);
        for (Entity.NamedContentValues subValue : subValues) {
            rawContact.addNamedDataItemValues(subValue.uri, subValue.values);
        }
        return rawContact;
    }

    /**
     * A RawContact object can be created with or without a context.
     *
     * The context is used for the buildString() member function in DataItem objects,
     * specifically for retrieving an instance of AccountTypeManager.  It is okay to
     * pass in null for the context in which case, you will not be able to call buildString(),
     * getDataKind(), or getAccountType() from a DataItem object.
     */
    public RawContact(Context context) {
        this(context, new ContentValues());
    }

    public RawContact(Context context, ContentValues values) {
        mContext = context;
        mValues = values;
        mDataItems = new ArrayList<NamedDataItem>();
    }

    public AccountTypeManager getAccountTypeManager() {
        if (mAccountTypeManager == null) {
            mAccountTypeManager = AccountTypeManager.getInstance(mContext);
        }
        return mAccountTypeManager;
    }

    public Context getContext() {
        return mContext;
    }

    public ContentValues getValues() {
        return mValues;
    }

    /**
     * Returns the id of the raw contact.
     */
    public Long getId() {
        return getValues().getAsLong(RawContacts._ID);
    }

//    /**
//     * Returns the account name of the raw contact.
//     */
//    public String getAccountName() {
//        return getValues().getAsString(RawContacts.ACCOUNT_NAME);
//    }
//
//    /**
//     * Returns the account type of the raw contact.
//     */
//    public String getAccountTypeString() {
//        return getValues().getAsString(RawContacts.ACCOUNT_TYPE);
//    }
//
//    /**
//     * Returns the data set of the raw contact.
//     */
//    public String getDataSet() {
//        return getValues().getAsString(RawContacts.DATA_SET);
//    }
//
    /**
     * Returns the account type and data set of the raw contact.
     */
//    public String getAccountTypeAndDataSetString() {
//        return getValues().getAsString(RawContacts.ACCOUNT_TYPE_AND_DATA_SET);
//    }

    public boolean isDirty() {
        return getValues().getAsBoolean(RawContacts.DIRTY);
    }

    public long getVersion() {
        return getValues().getAsLong(RawContacts.DIRTY);
    }

    public String getSourceId() {
        return getValues().getAsString(RawContacts.SOURCE_ID);
    }

    public String getSync1() {
        return getValues().getAsString(RawContacts.SYNC1);
    }

    public String getSync2() {
        return getValues().getAsString(RawContacts.SYNC2);
    }

    public String getSync3() {
        return getValues().getAsString(RawContacts.SYNC3);
    }

    public String getSync4() {
        return getValues().getAsString(RawContacts.SYNC4);
    }

    public boolean isDeleted() {
        return getValues().getAsBoolean(RawContacts.DELETED);
    }

//    public boolean isNameVerified() {
//        return getValues().getAsBoolean(RawContacts.NAME_VERIFIED);
//    }

//    public long getContactId() {
//        return getValues().getAsLong(Contacts.Entity.CONTACT_ID);
//    }
//
//    public boolean isStarred() {
//        return getValues().getAsBoolean(Contacts.STARRED);
//    }

    public AccountType getAccountType() {
        return getAccountTypeManager().getAccountType();    // Is always ScAccountType
    }

    /**
     * Sets the account name, account type, and data set strings.
     * Valid combinations for account-name, account-type, data-set
     * 1) null, null, null (local account)
     * 2) non-null, non-null, null (valid account without data-set)
     * 3) non-null, non-null, non-null (valid account with data-set)
     */
    private void setAccount(String accountName, String accountType, String dataSet) {
        final ContentValues values = getValues();
        if (accountName == null) {
            if (accountType == null && dataSet == null) {
                // This is a local account
                return;
            }
        } 
        throw new IllegalArgumentException(
                "Not a valid combination of account name, type, and data set.");
    }

//    public void setAccount(AccountWithDataSet accountWithDataSet) {
//        setAccount(accountWithDataSet.name, accountWithDataSet.type, accountWithDataSet.dataSet);
//    }

    public void setAccountToLocal() {
        setAccount(null, null, null);
    }

    /**
     * Creates and inserts a DataItem object that wraps the content values, and returns it.
     */
    public DataItem addDataItemValues(ContentValues values) {
        final NamedDataItem namedItem = addNamedDataItemValues(Data.CONTENT_URI, values);
        return namedItem.dataItem;
    }

    public NamedDataItem addNamedDataItemValues(Uri uri, ContentValues values) {
        final NamedDataItem namedItem = new NamedDataItem(uri, DataItem.createFrom(this, values));
        mDataItems.add(namedItem);
        return namedItem;
    }

    public List<DataItem> getDataItems() {
        final ArrayList<DataItem> list = new ArrayList<DataItem>();
        for (NamedDataItem dataItem : mDataItems) {
            if (Data.CONTENT_URI.equals(dataItem.uri)) {
                list.add(dataItem.dataItem);
            }
        }
        return list;
    }

    public List<NamedDataItem> getNamedDataItems() {
        return mDataItems;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RawContact: ").append(mValues);
        for (RawContact.NamedDataItem namedDataItem : mDataItems) {
            sb.append("\n  ").append(namedDataItem.uri);
            sb.append("\n  -> ").append(namedDataItem.dataItem.getContentValues());
        }
        return sb.toString();
    }
}
