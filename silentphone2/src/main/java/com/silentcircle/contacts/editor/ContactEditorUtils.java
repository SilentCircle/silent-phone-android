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
 * limitations under the License.
 */

package com.silentcircle.contacts.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.common.annotations.VisibleForTesting;
import com.silentcircle.contacts.model.AccountTypeManager;

// import com.silentcircle.silentcontacts2.model.account.AccountWithDataSet;
//import com.silentcircle.silentcontacts2.test.NeededForTesting;

/**
 * Utility methods for the "account changed" notification in the new contact creation flow.
 */
public class ContactEditorUtils {
    private static final String TAG = "ContactEditorUtils";

    private static final String KEY_DEFAULT_ACCOUNT = "ContactEditorUtils_default_account";
    private static final String KEY_KNOWN_ACCOUNTS = "ContactEditorUtils_known_accounts";
    // Key to tell the first time launch.
    private static final String KEY_ANYTHING_SAVED = "ContactEditorUtils_anything_saved";

//    private static final List<AccountWithDataSet> EMPTY_ACCOUNTS = ImmutableList.of();

    private static ContactEditorUtils sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final AccountTypeManager mAccountTypes;

    private ContactEditorUtils(Context context) {
        this(context, AccountTypeManager.getInstance(context));
    }

    @VisibleForTesting
    ContactEditorUtils(Context context, AccountTypeManager accountTypes) {
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mAccountTypes = accountTypes;
    }

    public static synchronized ContactEditorUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ContactEditorUtils(context);
        }
        return sInstance;
    }

//    @NeededForTesting
    void cleanupForTest() {
        mPrefs.edit().remove(KEY_DEFAULT_ACCOUNT).remove(KEY_KNOWN_ACCOUNTS)
                .remove(KEY_ANYTHING_SAVED).apply();
    }

//    @NeededForTesting
    void removeDefaultAccountForTest() {
        mPrefs.edit().remove(KEY_DEFAULT_ACCOUNT).apply();
    }

    /**
     * Sets the {@link #KEY_KNOWN_ACCOUNTS} and {@link #KEY_DEFAULT_ACCOUNT} preference values to
     * empty strings to reset the state of the preferences file.
     */
    private void resetPreferenceValues() {
        mPrefs.edit().putString(KEY_KNOWN_ACCOUNTS, "").putString(KEY_DEFAULT_ACCOUNT, "").apply();
    }

//    private List<AccountWithDataSet> getWritableAccounts() {
//        return mAccountTypes.getAccounts(true);
//    }

    /**
     * @return true if it's the first launch and {@link #saveDefaultAndAllAccounts} has never
     *     been called.
     */
    private boolean isFirstLaunch() {
        return !mPrefs.getBoolean(KEY_ANYTHING_SAVED, false);
    }

    /**
     * Saves all writable accounts and the default account, which can later be obtained
     * with {@link #getDefaultAccount}.
     *
     * This should be called when saving a newly created contact.
     *
     * @ param defaultAccount the account used to save a newly created contact.  Or pass {@code null}
     *     If the user selected "local only".
     */
    public void saveDefaultAndAllAccounts() {
        final SharedPreferences.Editor editor = mPrefs.edit().putBoolean(KEY_ANYTHING_SAVED, true);

//        if (defaultAccount == null) {
            // If the default is "local only", there should be no writable accounts.
            // This should always be the case with our spec, but because we load the account list
            // asynchronously using a worker thread, it is possible that there are accounts at this
            // point. So if the default is null always clear the account list.
            editor.putString(KEY_KNOWN_ACCOUNTS, "");
            editor.putString(KEY_DEFAULT_ACCOUNT, "");
//        } 
//        else {
//            editor.putString(KEY_KNOWN_ACCOUNTS,
//                    AccountWithDataSet.stringifyList(getWritableAccounts()));
//            editor.putString(KEY_DEFAULT_ACCOUNT, defaultAccount.stringify());
//        }
        editor.apply();
    }

    /**
     * @return the default account saved with {@link #saveDefaultAndAllAccounts}.
     *
     * Note the {@code null} return value can mean either {@link #saveDefaultAndAllAccounts} has
     * never been called, or {@code null} was passed to {@link #saveDefaultAndAllAccounts} --
     * i.e. the user selected "local only".
     *
     * Also note that the returned account may have been removed already.
     */
    public Object getDefaultAccount() {
        return null;
    }

    /**
     * @return true if an account still exists.  {@code null} is considered "local only" here,
     *    so it's valid too.
     */
//    @VisibleForTesting
    boolean isValidAccount() {
        return true; // It's "local only" account, which is valid.
    }

    /**
     * @return saved known accounts, or an empty list if none has been saved yet.
     */
    @VisibleForTesting
//    List<AccountWithDataSet> getSavedAccounts() {
//        final String saved = mPrefs.getString(KEY_KNOWN_ACCOUNTS, null);
//        if (TextUtils.isEmpty(saved)) {
//            return EMPTY_ACCOUNTS;
//        }
//        try {
//            return AccountWithDataSet.unstringifyList(saved);
//        } catch (IllegalArgumentException exception) {
//            Log.e(TAG, "Error with retrieving saved accounts " + exception.toString());
//            // unstringifyList()can throw an exception if the string is not in an expected format.
//            // Hence, if the preferences file is corrupt, just reset the preference values
//            resetPreferenceValues();
//            return EMPTY_ACCOUNTS;
//        }
//    }

    /**
     * @return true if the contact editor should show the "accounts changed" notification, that is:
     * - If it's the first launch.
     * - Or, if an account has been added.
     * - Or, if the default account has been removed.
     * (And some extra sanity check)
     *
     * Note if this method returns {@code false}, the caller can safely assume that
     * {@link #getDefaultAccount} will return a valid account.  (Either an account which still
     * exists, or {@code null} which should be interpreted as "local only".)
     */
    public boolean shouldShowAccountChangedNotification() {
        if (isFirstLaunch()) {
            return true;
        }

//        // Account added?
//        final List<AccountWithDataSet> savedAccounts = getSavedAccounts();
//        final List<AccountWithDataSet> currentWritableAccounts = getWritableAccounts();
//        for (AccountWithDataSet account : currentWritableAccounts) {
//            if (!savedAccounts.contains(account)) {
//                return true; // New account found.
//            }
//        }
//
//        final AccountWithDataSet defaultAccount = getDefaultAccount();
//
//        // Does default account still exist?
//        if (!isValidAccount(defaultAccount)) {
//            return true;
//        }
//
//        // If there is an inconsistent state in the preferences file - default account is null
//        // ("local" account) while there are multiple accounts, then show the notification dialog.
//        // This shouldn't ever happen, but this should allow the user can get back into a normal
//        // state after they respond to the notification.
//        if (defaultAccount == null && currentWritableAccounts.size() > 0) {
//            Log.e(TAG, "Preferences file in an inconsistent state, request that the default account"
//                    + " and current writable accounts be saved again");
//            return true;
//        }
//
        // All good.
        return false;
    }

//    @VisibleForTesting
//    String[] getWritableAccountTypeStrings() {
//        final Set<String> types = Sets.newHashSet();
//        for (AccountType type : mAccountTypes.getAccountTypes(true)) {
//            types.add(type.accountType);
//        }
//        return types.toArray(new String[types.size()]);
//    }

    /**
     * Create an {@link android.content.Intent} to start "add new account" setup wizard.  Selectable account
     * types will be limited to ones that supports editing contacts.
     *
     * Use {@link android.app.Activity#startActivityForResult} or
     * {@link android.app.Fragment#startActivityForResult} to start the wizard, and
     * {@link android.app.Activity#onActivityResult} or {@link android.app.Fragment#onActivityResult} to
     * get the result.
     */
//    public Intent createAddWritableAccountIntent() {
//        return AccountManager.newChooseAccountIntent(
//                null, // selectedAccount
//                new ArrayList<Account>(), // allowableAccounts
//                getWritableAccountTypeStrings(), // allowableAccountTypes
//                false, // alwaysPromptForAccount
//                null, // descriptionOverrideText
//                null, // addAccountAuthTokenType
//                null, // addAccountRequiredFeatures
//                null // addAccountOptions
//                );
//    }

    /**
     * Parses a result from {@link #createAddWritableAccountIntent} and returns the created
     * {@link android.accounts.Account}, or null if the user has canceled the wizard.  Pass the {@code resultCode}
     * and {@code data} parameters passed to {@link android.app.Activity#onActivityResult} or
     * {@link android.app.Fragment#onActivityResult}.
     *
     * Note although the return type is {@link AccountWithDataSet}, return values from this method
     * will never have {@link AccountWithDataSet#dataSet} set, as there's no way to create an
     * extension package account from setup wizard.
     */
//    public AccountWithDataSet getCreatedAccount(int resultCode, Intent resultData) {
//        // Javadoc doesn't say anything about resultCode but that the data intent will be non null
//        // on success.
//        if (resultData == null) return null;
//
//        final String accountType = resultData.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
//        final String accountName = resultData.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
//
//        // Just in case
//        if (TextUtils.isEmpty(accountType) || TextUtils.isEmpty(accountName)) return null;
//
//        return new AccountWithDataSet(accountName, accountType, null);
//    }
}
