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

package com.silentcircle.messaging.util;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helper functions to get device information and parse the device info string.
 *
 * Created by werner on 03.02.16.
 */
public class DeviceInfo {

    private final static String TAG = "DeviceInfo";

    public static class DeviceData {
        final public String name;
        final public String devId;
        final public String identityKey;
        final public String zrtpVerificationState;

        public DeviceData(String name, String id, String key, String verifyState) {
            this.name = name;
            this.devId = id;
            identityKey = key;
            zrtpVerificationState = verifyState;
        }
    }

    /**
     * Return an information string that shows verification state of messaging devices.
     *
     * @param partnerId The messaging partner if
     * @return {@code null} if the messaging partner has no or more than one messaging device, a DeviceInfo structure
     *         otherwise.
     */
    @Nullable
    public static String getDeviceVerificationString(Context context, @Nullable String partnerId) {
        if (partnerId == null)
            return null;
        byte[][] devices = ZinaMessaging.getIdentityKeys(IOUtils.encode(partnerId));

        if (devices == null || devices.length == 0) {
            return null;
        }
//        int checked = 0;
        int verified = 0;
        for (byte[] device : devices) {
            DeviceData devInfo = parseDeviceInfo(new String(device));
            if (devInfo == null)
                continue;
            switch (devInfo.zrtpVerificationState) {
                case "0":
                    break;
//                case "1":
//                    checked++;
//                    break;
                case "2":
                    verified++;
                    break;
            }
        }
        if (verified == 0)
            return null;

        Resources res = context.getResources();

        if (verified == devices.length) {
            return res.getString(R.string.messaging_checked_verified);
        }
        return null;

        // Below the code that we can use if we like to display detailed checked/verify
        // information to the user. Phil's feedback was that it's to complex, thus show
        // only "Verified", and only in case all devices of the partner are verified. That's
        // the check above.
//        if (devices.length == 1) {
//            return (checked > 0) ? res.getString(R.string.messaging_checked_only) :
//                    res.getString(R.string.messaging_checked_verified);
//        }
//        else {
//            if (verified == devices.length) {
//                return res.getString(R.string.messaging_all_verified, devices.length);
//            }
//            if (checked == devices.length) {
//                return res.getString(R.string.messaging_all_checked, devices.length);
//            }
//            if (checked == 0) {
//                return res.getString(R.string.messaging_partly_verified, verified, devices.length);
//            }
//            if (verified == 0) {
//                return res.getString(R.string.messaging_partly_checked, checked, devices.length);
//            }
//            return res.getString(R.string.messaging_partly_c_v, checked, devices.length, verified, devices.length);
//        }
    }


    // identityKey:device name:device id:verify state
    @Nullable
    public static DeviceData parseDeviceInfo(String devData) {
        String elements[] = devData.split(":");
        if (elements.length != 4) {
            return null;
        }
        // Convert id key to hex string with leading zeros
        byte[] idKey = Base64.decode(elements[0], Base64.DEFAULT);
        final String idKeyFingerprint = fingerprint(idKey);
        return new DeviceData(elements[1], elements[2], idKeyFingerprint, elements[3]);
    }

    @NonNull
    public static String fingerprint(byte[] data) {
        final StringBuilder sb = new StringBuilder(80);
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return sb.toString();
        }
        final byte[] hash = md.digest(data);

        final String hexString = new String(Utilities.bytesToHexChars(hash, true));
        final int len = hexString.length();
        for (int i = 1; i <= len; i++) {
            sb.append(hexString.charAt(i-1));
            if ((i % 2) == 0)
                sb.append(':');
            if ((i % 16) == 0)
                sb.append('\n');
        }
        if (sb.charAt(sb.length()-2) == ':') {
            sb.deleteCharAt(sb.length() - 2);
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
