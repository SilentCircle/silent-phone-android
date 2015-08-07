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

package com.silentcircle.contacts.model.dataitem;

import android.content.ContentValues;

import com.silentcircle.contacts.model.RawContact;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Organization;

/**
 * Represents an organization data item, wrapping the columns in
 * { @link ContactsContract.CommonDataKinds.Organization}.
 */
public class OrganizationDataItem extends DataItem {

    /* package */ OrganizationDataItem(RawContact rawContact, ContentValues values) {
        super(rawContact, values);
    }

    public String getCompany() {
        return getContentValues().getAsString(Organization.COMPANY);
    }

    /**
     * Values are one of Organization.TYPE_*
     */
    public int getType() {
        return getContentValues().getAsInteger(Organization.TYPE);
    }

    public String getLabel() {
        return getContentValues().getAsString(Organization.LABEL);
    }

    public String getTitle() {
        return getContentValues().getAsString(Organization.TITLE);
    }

    public String getDepartment() {
        return getContentValues().getAsString(Organization.DEPARTMENT);
    }

    public String getJobDescription() {
        return getContentValues().getAsString(Organization.JOB_DESCRIPTION);
    }

    public String getSymbol() {
        return getContentValues().getAsString(Organization.SYMBOL);
    }

    public String getPhoneticName() {
        return getContentValues().getAsString(Organization.PHONETIC_NAME);
    }

    public String getOfficeLocation() {
        return getContentValues().getAsString(Organization.OFFICE_LOCATION);
    }

    public String getPhoneticNameStyle() {
        return getContentValues().getAsString(Organization.PHONETIC_NAME_STYLE);
    }
}
