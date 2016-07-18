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

package com.silentcircle;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.silentcircle.common.util.RingtoneUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.SettingsFragment;

import java.io.File;
import java.util.Map;

/**
 *
 */
public class SilentPhoneApplication extends Application {

    private static final String TAG = SilentPhoneApplication.class.getSimpleName();

    /** Application context. */
    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            updateRingTones();

        /* initialize contacts cache */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
            ContactsCache.buildContactsCache(sContext);

        /* initialize sound notification pool here */
        SoundNotifications.getSoundPool();

        // TODO: Implement upgrade handling - so this only occurs on upgrade
        // Delete *possible* pre-existing Google measurement db files
        // We now explicitly disable this in "disable_app_measurement.xml"
        // NGA-524 Remove google_app_measurement.db from SPA
        File measureDb1 = new File("/data/data/com.silentcircle.silentphone/databases/google_app_measurement.db-journal");
        if (measureDb1.exists()) {
            measureDb1.delete();
        }
        File measureDb2 = new File("/data/data/com.silentcircle.silentphone/databases/google_app_measurement.db");
        if (measureDb2.exists()) {
            measureDb2.delete();
        }
    }

    public static Context getAppContext() {
        return sContext;
    }

    private void updateRingTones() {

        Map<String, String> ringTones = RingtoneUtils.getRingtones(getAppContext());

        if (!ringTones.containsKey(RingtoneUtils.TITLE_EMERGENCY)) {
            Log.d(TAG, "Registering ring tone " + RingtoneUtils.TITLE_EMERGENCY);

            try {
                Uri tone = RingtoneUtils.createRingtone(getAppContext(), RingtoneUtils.TITLE_EMERGENCY,
                        RingtoneUtils.FILE_NAME_EMERGENCY, R.raw.emergency, "audio/*");
                if (tone != null) {
                    /* if ringtone registration is successful, set emergency ringtone */
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getAppContext());
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(SettingsFragment.RINGTONE_EMERGENCY_KEY, tone.toString()).apply();

                }
            } catch (Throwable e) {
                Log.d(TAG, "Could not register ringtone " + RingtoneUtils.TITLE_EMERGENCY);
            }
        }
    }

}
