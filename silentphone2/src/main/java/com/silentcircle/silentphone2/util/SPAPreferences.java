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
package com.silentcircle.silentphone2.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;

import com.silentcircle.silentphone2.fragments.SettingsFragment;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by rli on 9/22/15.
 */
public class SPAPreferences {

    public static final String C2DM_REGISTRATION_PREFERENCE = "c2dm_registration_preference";

    public static final String PREFERENCE_ACCOUNT_DETAILS_EXPANDED = "sp_drawer_account_details_expanded";

    @IntDef({INDEX_ACCOUNT_DETAILS_HIDDEN, INDEX_ACCOUNT_DETAILS_PARTIALLY_VISIBLE,
            INDEX_ACCOUNT_DETAILS_VISIBLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AccountDetailsVisibility {}

    public static final int INDEX_ACCOUNT_DETAILS_HIDDEN = 0;
    public static final int INDEX_ACCOUNT_DETAILS_PARTIALLY_VISIBLE = 1;
    public static final int INDEX_ACCOUNT_DETAILS_VISIBLE = 2;

    private static SPAPreferences sInstance;
    private final SharedPreferences mPreferences;
    private final SharedPreferences.Editor mEditor;

    public static synchronized SPAPreferences getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new SPAPreferences(context);
        }
        return sInstance;
    }

    @SuppressLint("CommitPrefEdits")
    private SPAPreferences(Context context) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mEditor = mPreferences.edit();
    }

    public synchronized void setRegistrationId(String regId) {
        mEditor.putString(C2DM_REGISTRATION_PREFERENCE, regId);
        mEditor.commit();
    }

    public synchronized String getRegistrationId() {
        return mPreferences.getString(C2DM_REGISTRATION_PREFERENCE, "");
    }

    public synchronized void setAccountDetailsExpanded(@AccountDetailsVisibility int expanded) {
        mEditor.putInt(PREFERENCE_ACCOUNT_DETAILS_EXPANDED, expanded);
        mEditor.commit();
    }

    public synchronized int getAccountDetailsExpanded() {
        return mPreferences.getInt(PREFERENCE_ACCOUNT_DETAILS_EXPANDED, INDEX_ACCOUNT_DETAILS_VISIBLE);
    }

    public synchronized boolean isDeveloper() {
        return mPreferences.getBoolean(SettingsFragment.DEVELOPER, false);
    }

}
