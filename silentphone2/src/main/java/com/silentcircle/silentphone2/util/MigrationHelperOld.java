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

package com.silentcircle.silentphone2.util;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.silentcircle.keymngrsupport.KeyManagerSupport;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

/**
 * Reads data from old key manager
 */
public class MigrationHelperOld {
    private static final String TAG = "MigrationHelperOld";

    public static void checkOldKeyManager(Activity ctx, int code) {
        try {
            ctx.startActivityForResult(KeyManagerSupport.getKeyManagerReadyIntent(), code);
        } catch (Exception ignored) {}
    }

    public static boolean copyOldKeyData(Context ctx) {

        long token = KeyManagerSupport.registerWithKeyManager(ctx.getContentResolver(), ctx.getPackageName(), ctx.getString(R.string.app_name));

        if(token==0) {
            Log.e(TAG, "Failed to register with old key manager");
            return false;
        }
        byte[] devAuthorization = KeyManagerSupport.getSharedKeyData(ctx.getContentResolver(),
                ConfigurationUtilities.getShardAuthTag());
        byte[] deviceId = KeyManagerSupport.getSharedKeyData(ctx.getContentResolver(),
                ConfigurationUtilities.getShardDevIdTag());
        byte[] keyData = KeyManagerSupport.getPrivateKeyData(ctx.getContentResolver(),
                DialerActivity.SILENT_PHONE_KEY);

        if (devAuthorization == null || deviceId == null || keyData == null) {
            Log.e(TAG, "Missing data in old key manager");
            return false;
        }
        return MigrationHelperNew.storeDataInNewKeystore(ctx, devAuthorization, deviceId, keyData);
    }

}
