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

import android.content.Context;
import android.util.Log;

import com.silentcircle.keymanagersupport.KeyManagerSupport;            // This is the NEW key store !!!!
import com.silentcircle.silentphone2.activities.DialerActivity;

import java.util.Arrays;

/**
 * Store data into new key store.
 *
 * The function requires an initialized new key store and that the caller registered with it.
 */
public class MigrationHelperNew {
    private static final String TAG = "MigrationHelperNew";

    public static boolean storeDataInNewKeystore(Context ctx, byte[] devAuthorization, byte[] deviceId, byte[] sipPwKey) {

        if (!KeyManagerSupport.storeSharedKeyData(ctx.getContentResolver(), devAuthorization,
                ConfigurationUtilities.getShardAuthTag())) {
            Log.e(TAG, "Cannot store the device authorization data in the new key store - already available.");
        }
        Arrays.fill(devAuthorization, (byte) 0);

        if (!KeyManagerSupport.storeSharedKeyData(ctx.getContentResolver(), deviceId,
                ConfigurationUtilities.getShardDevIdTag())) {
            Log.e(TAG, "Cannot store the device id data in the new key store - already available.");
        }

        if (!KeyManagerSupport.storePrivateKeyData(ctx.getContentResolver(), sipPwKey, DialerActivity.SILENT_PHONE_KEY)) {
            Log.e(TAG, "Cannot store the SIP key data data in the new key store.");
            return false;
        }
        Arrays.fill(sipPwKey, (byte) 0);
        return true;
    }
}
