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
 * Copyright (C) 2009 The Android Open Source Project
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

package com.silentcircle.contacts.model.account;

import android.content.Context;
import android.util.Log;

import com.silentcircle.silentphone2.R;

/**
 * This class implements all known data kinds for a SilentContacts contact record.
 * 
 * We use the original FallbackAccountType and modify it to include GroupMembership. We
 * may do some further modifications to meet requirements of SilentContacts.
 * 
 * @author werner
 *
 */
public class ScAccountType extends BaseAccountType {
    private static final String TAG = "ScAccountType";

    private ScAccountType(Context context, String resPackageName) {
        this.accountType = null;
        this.dataSet = null;
        this.titleRes = R.string.account_phone;
        this.iconRes = R.drawable.ic_launcher;

        // Note those are only set for unit tests.
        this.resourcePackageName = resPackageName;
        this.syncAdapterPackageName = resPackageName;

        try {
            addDataKindGroupMembership(context);
            addDataKindStructuredName(context);
            addDataKindDisplayName(context);
            addDataKindPhoneticName(context);
            addDataKindNickname(context);
            addDataKindPhone(context);
            addDataKindSipAddress(context);
            addDataKindEmail(context);
            addDataKindStructuredPostal(context);
            addDataKindIm(context);
            addDataKindOrganization(context);
            addDataKindPhoto(context);
            addDataKindNote(context);

            mIsInitialized = true;
        } catch (DefinitionException e) {
            Log.e(TAG, "Problem building account type", e);
        }
    }

    public ScAccountType(Context context) {
        this(context, null);
    }

    /**
     * Used to compare with an { @link ExternalAccountType} built from a test contacts.xml.
     * In order to build {@link com.silentcircle.contacts.model.dataitem.DataKind}s with the same resource package name,
     * {@code resPackageName} is injectable.
     */
//    @NeededForTesting
    static AccountType createWithPackageNameForTest(Context context, String resPackageName) {
        return new ScAccountType(context, resPackageName);
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }
}
