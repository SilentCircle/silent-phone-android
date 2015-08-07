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

package com.silentcircle.contacts.model;

import android.content.Context;
import android.util.Log;

import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.model.account.ScAccountType;
import com.silentcircle.contacts.model.dataitem.DataKind;

/**
 *
 * Singleton holder for all parsed {@link AccountType} available on the system.
 * 
 * For SC we only have <em>one</em> account, thus most of the original AccountTypeManager
 * functions are useless and are therefore removed.
 */
public abstract class AccountTypeManager {
    static final String TAG = "AccountTypeManager";

    public static final String ACCOUNT_TYPE_SERVICE = "contactAccountTypes";

    private static AccountTypeManager mAccountTypeManager;

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static AccountTypeManager getInstance(Context context) {
        if (mAccountTypeManager == null) {
            mAccountTypeManager = AccountTypeManager.createAccountTypeManager(context);
        }
        return mAccountTypeManager;
    }

    public static synchronized AccountTypeManager createAccountTypeManager(Context context) {
        return new AccountTypeManagerImpl(context);
    }

    public abstract AccountType getAccountType();

    /**
     * Check if the SC account supports a MIME type.
     *
     * @param mimeType Get the {@link com.silentcircle.contacts.model.dataitem.DataKind} for this MIME type
     *
     * @return the {@link com.silentcircle.contacts.model.dataitem.DataKind} if found, {@code null} otherwise
     */
    public abstract DataKind getKindOrFallback(String mimeType);
}

class AccountTypeManagerImpl extends AccountTypeManager {

    private AccountType mScAccountType;

    /**
     * Internal constructor that only performs initial parsing.
     */
    public AccountTypeManagerImpl(Context context) {
        mScAccountType = new ScAccountType(context);
    }

    @Override
    public DataKind getKindOrFallback(String mimeType) {

        DataKind kind =  mScAccountType.getKindForMimetype(mimeType);
        if (kind == null) {
            Log.w(TAG, "Unknown mime=" + mimeType);
        }
        return kind;
    }

    /**
     * Return {@link AccountType} for the given account type and data set.
     */
    @Override
    public AccountType getAccountType() {
        return mScAccountType;
    }
}
