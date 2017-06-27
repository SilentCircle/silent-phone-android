/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.accounts;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import com.silentcircle.logs.Log;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

/**
 * A simple authenticator service that handles requests for SC accounts.
 *
 * The authenticator accepts a the options 'feature_code' and 'sc_account_name' to perform account
 * creation with SC's server or just add an account entry in Android's AccountManager.
 *
 * The AuthenticatorActivity does most of the real work.
 *
 * Created by werner on 09.04.15.
 */
public class ScAuthenticator extends AbstractAccountAuthenticator {
    private static final String TAG = "ScAuthenticator";

    private final Context mContext;

    public ScAuthenticator(Context ctx) {
        super(ctx);
        mContext = ctx;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
                             String[] requiredFeatures, Bundle options) throws NetworkErrorException {
        if (ConfigurationUtilities.mTrace) {
            final String packageName = options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME, "unknown");
            final int uid = options.getInt(AccountManager.KEY_CALLER_UID, 0);
            Log.d(TAG, "addAccount from " + packageName + ", uid: " + uid);
        }

        final Bundle bundle = new Bundle();
        AccountManager am = AccountManager.get(mContext);

        Account[] accounts = new Account[0];
        try {
            accounts = am.getAccountsByType(AccountConstants.ACCOUNT_TYPE);
        } catch (SecurityException ignore) {}
        if (accounts.length >= 1) {
            bundle.putString(AccountManager.KEY_ERROR_CODE, "multiple");
            bundle.putString(AccountManager.KEY_ERROR_MESSAGE, mContext.getString(R.string.account_exists));
            return bundle;
        }

        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_TYPE, accountType);
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TYPE, authTokenType);
        intent.putExtra(AuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, true);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

        intent.putExtra(AuthenticatorActivity.ARG_OPTIONS_BUNDLE, options);

        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {

        if (ConfigurationUtilities.mTrace) {
            final String packageName = options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME, "unknown");
            final int uid = options.getInt(AccountManager.KEY_CALLER_UID, 0);
            Log.d(TAG, "getAuthToken from " + packageName + ", uid: " + uid);
        }

        final Bundle result = new Bundle();
        // If the caller requested an authToken type we don't support, then
        // return an error
        if (!authTokenType.equals(AccountConstants.ACCOUNT_ACCESS)) {
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType");
            return result;
        }
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_TYPE, account.type);
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TYPE, authTokenType);
        intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_NAME, account.name);
        intent.putExtra(AuthenticatorActivity.ARG_OPTIONS_BUNDLE, options);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        if (AccountConstants.ACCOUNT_ACCESS.equals(authTokenType))
            return mContext.getString(R.string.account_access_label);
        else
            return authTokenType + " (Label)";
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
    }

    @NonNull
    @Override
    public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account) throws NetworkErrorException {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "getAccountRemovalAllowed, account: " + account.name);
        final Bundle bundle = new Bundle();
        bundle.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        AccountChangeReceiver.setRemoveAccountRequested();      // refer to comments in AccountChangeReceiver
        return bundle;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return null;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        return null;
    }
}
