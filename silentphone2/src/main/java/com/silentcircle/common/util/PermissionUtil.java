/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.common.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class PermissionUtil {
    private static final String TAG = PermissionUtil.class.getSimpleName();

    private static final String[] REQUIRED_PERMISSIONS = {
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CONTACTS",
            "android.permission.WAKE_LOCK",
            "android.permission.INTERNET",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.GET_ACCOUNTS",
            "android.permission.MANAGE_ACCOUNTS",
            "android.permission.USE_CREDENTIALS",
            "android.permission.AUTHENTICATE_ACCOUNTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };

    @SuppressWarnings("unused")
    private static final String[] OPTIONAL_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.VIBRATE",
            "android.permission.DISABLE_KEYGUARD",
            "android.permission.CAMERA",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "com.android.launcher.permission.INSTALL_SHORTCUT"
    };

    private static Map<String, Integer> mProtectionLevelMap;

    @SuppressWarnings("unused")
    public static String[] getPermissions(Context context) {
        return getPermissions(context, -1);
    }

    public static String[] getPermissions(Context context, int protectionLevel) {
        if(mProtectionLevelMap == null) {
            mProtectionLevelMap = getProtectionLevelMap(context);
        }

        if(mProtectionLevelMap != null) {
            if(protectionLevel == -1) {
                return mProtectionLevelMap.keySet().toArray(new String[0]);
            }

            List<String> permissions = new ArrayList<String>();

            for(Map.Entry<String, Integer> entry : mProtectionLevelMap.entrySet()) {
                if(entry.getValue().equals(protectionLevel)) {
                    permissions.add(entry.getKey());
                }
            }

            return permissions.toArray(new String[0]);
        }

        return null;
    }

    public static Map<String, Integer> getProtectionLevelMap(Context context) {
        Map<String, Integer> protectionLevelMap = new HashMap<String, Integer>();

        PackageManager packageManager = context.getPackageManager();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);

            for(String permission : packageInfo.requestedPermissions) {
                int protectionLevel = packageManager.getPermissionInfo(permission, PackageManager.GET_META_DATA).protectionLevel;

                protectionLevelMap.put(permission, protectionLevel);
            }
        } catch(PackageManager.NameNotFoundException exception) {
            Log.e(TAG, "Error getting package info", exception);
        }

        return protectionLevelMap;
    }

    // It is important that this is populated frequently, as users can revoke permissions on demand
    public static Map<String, Integer> getFlagMap(Context context) {
        Map<String, Integer> flagMap = new HashMap<String, Integer>();

        PackageManager packageManager = context.getPackageManager();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);

            Iterator<String> permissionIterator = Arrays.asList(packageInfo.requestedPermissions).iterator();
            List<Integer> list = new ArrayList<Integer>(packageInfo.requestedPermissionsFlags.length);
            for (int value : packageInfo.requestedPermissionsFlags) {
                list.add(value);
            }
            Iterator<Integer> flagIterator = list.iterator();

            while (permissionIterator.hasNext() || flagIterator.hasNext()) {
                flagMap.put(permissionIterator.next(), flagIterator.next());
            }
        } catch(PackageManager.NameNotFoundException exception) {
            Log.e(TAG, "Error getting package info", exception);
        }

        return flagMap;
    }

    @SuppressWarnings("unused")
    public static boolean hasGrantedPermissions(Context context, int protectionLevel) {
        String[] permissions = getPermissions(context, protectionLevel);
        Map<String, Integer> flagMap = getFlagMap(context);

        if(permissions == null || permissions.length == 0) {
            return false;
        }

        for(String permission : permissions) {
            if(!flagMap.get(permission).equals(PackageInfo.REQUESTED_PERMISSION_GRANTED)) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unused")
    public static boolean hasGrantedPermission(Context context, String permission) {
        Map<String, Integer> flagMap = getFlagMap(context);

        return flagMap.get(permission).equals(PackageInfo.REQUESTED_PERMISSION_GRANTED);

    }

    @SuppressWarnings("unused")
    public static boolean hasGrantedRequiredPermissions(Context context) {
        Map<String, Integer> flagMap = getFlagMap(context);

        for(String permission : REQUIRED_PERMISSIONS) {
            if(!flagMap.get(permission).equals(PackageInfo.REQUESTED_PERMISSION_GRANTED)) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unused")
    public static String[] parseDeniedPermissions(String[] permissions, int[] grantResults) {
        if(permissions.length != grantResults.length) {
            return null;
        }

        List<String> deniedPermissionsList = new ArrayList<String>();

        for(int i = 0; i < grantResults.length; i++) {
            if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                deniedPermissionsList.add(permissions[i]);
            }
        }

        return deniedPermissionsList.toArray(new String[0]);
    }
}
