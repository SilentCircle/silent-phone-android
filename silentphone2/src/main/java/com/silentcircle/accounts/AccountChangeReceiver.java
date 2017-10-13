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
package com.silentcircle.accounts;

import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.activities.DialerActivityInternal;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

/**
 * Listener to monitor account changes.
 *
 * The AccountManager sends this event on every account change and for every account and
 * the broadcast Intent does not contains any information about the changed account.
 *
 * To overcome this limitation this listener needs support from ScAuthenticator: if
 * ScAuthenticator permits removal of the SC account (currently we allow only _one_
 * SC account per device) then ScAuthenticator sets the {@code mRemoveAccountRequested} flag.
 *
 * On the following broadcast the receiver initiates the removal of the internal account
 * data, i.e. provisioning data, which also triggers SPA to exit.
 *
 * If (sometime later) we the support more than on account per device then this class needs
 * some extensions, for example read information (name or number) of all known internal
 * account, get all account from the AccountManager and check which account entry was
 * deleted.
 */
public class AccountChangeReceiver extends BroadcastReceiver {
    private static final String TAG = AccountChangeReceiver.class.getSimpleName();

    private static boolean mRemoveAccountRequested;

    public AccountChangeReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action))
            return;

        if (AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION.equals(action) && mRemoveAccountRequested) {
            mRemoveAccountRequested = false;
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "remove SC account");
            Intent i = new Intent(context, DialerActivityInternal.class);
            i.setAction(DialerActivityInternal.ACTION_REMOVE_ACCOUNT);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    public static void setRemoveAccountRequested() {
        mRemoveAccountRequested = true;
    }
}
