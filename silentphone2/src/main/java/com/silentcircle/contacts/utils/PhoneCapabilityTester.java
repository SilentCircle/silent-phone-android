/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.silentcircle.contacts.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.silentcircle.silentphone2.R;

import java.util.List;

/**
 * Provides static functions to quickly test the capabilities of this device. The static
 * members are not safe for threading
 */
public final class PhoneCapabilityTester {
    private static boolean sIsInitialized;
    private static boolean sIsPhone;
    private static boolean sIsSipPhone;

    /**
     * Tests whether the Intent has a receiver registered. This can be used to show/hide
     * functionality (like Phone, SMS)
     */
    public static boolean isIntentRegistered(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> receiverList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return receiverList.size() > 0;
    }

    /**
     * True if this device support SIP phones, always true for SilentContact.
     * @param context
     * @return
     */
    public static boolean isSipPhone(Context context) {
        return true;
    }
    /**
     * True if we are using two-pane layouts ("tablet mode"), false if we are using single views
     * ("phone mode")
     */
    public static boolean isUsingTwoPanes(Context context) {
        return context.getResources().getBoolean(R.bool.config_use_two_panes);
    }

    /**
     * True if the favorites tab should be shown in two-pane mode.  False, otherwise.
     */
    public static boolean isUsingTwoPanesInFavorites(Context context) {
        return context.getResources().getBoolean(R.bool.config_use_two_panes_in_favorites);
    }
}
