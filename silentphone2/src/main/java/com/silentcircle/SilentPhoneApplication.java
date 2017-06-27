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
package com.silentcircle;


import android.Manifest;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.silentcircle.common.util.RingtoneUtils;
import com.silentcircle.logs.Log;
import com.silentcircle.logs.LogsService;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.receivers.AttachmentEventReceiver;
import com.silentcircle.messaging.receivers.ChatNotification;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.passcode.PasscodeController;
import com.silentcircle.silentphone2.receivers.AutoStart;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.userinfo.LoadUserInfo;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.PinnedCertificateKeystoreFactory;
import org.acra.sender.ReportSenderFactory;
import org.acra.sender.SentrySenderFactory;

import java.io.File;
import java.util.Map;

/**
 * Application class with basic initializations.
 */
@ReportsCrashes(buildConfigClass = BuildConfig.class, logcatArguments =  { "-t", "1000", "-v", "time" })
public class SilentPhoneApplication extends Application {

    private static final String TAG = SilentPhoneApplication.class.getSimpleName();

    private static SilentPhoneApplication mThisApplication;

    /*
     * Helper variables for crash reporting framework, app context can be available during crash,
     * variables from activities are not.
     */
    public static String sUuid;
    public static String sDisplayName;
    public static String sDisplayAlias;

    private MessagingBroadcastReceiver mViewUpdater;

    private static Runnable sLogsCleanupRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(getAppContext(), LogsService.class);
            getAppContext().startService(intent);
        }
    };

    private static final String KEY_USER_BACKGROUNDED = "user_backgrounded";
    BroadcastReceiver mUserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                return;
            }

            switch (action) {
                case Intent.ACTION_USER_BACKGROUND:
                    setUserBackgrounded(true);
                    break;
                case Intent.ACTION_USER_FOREGROUND:
                    if (userBackgrounded()) {
                        setUserBackgrounded(false);
                        Intent i = new Intent(context, DialerActivity.class);
                        i.setAction(AutoStart.ON_BOOT); // So it is visibly hidden
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mThisApplication = this;

        /* set-up passcode */
        PasscodeController.getSharedController();

        /* initialize static variables for en/de-cryption */
        TiviPhoneService.initLoggingStaticVariables();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppContext());
        if(prefs.getBoolean(SettingsFragment.ENABLE_DEBUG_LOGGING, false)) {
            setLogFile();
            com.silentcircle.logs.Log.setIsDebugLoggingEnabled(true);
            // TODO: Why not now?
            new Handler().postDelayed(sLogsCleanupRunnable, LogsService.INTERVAL);
            prefs.edit().putBoolean(LogsService.LOGS_SERVICE_SCHEDULED, true).apply();
        }

        /* initialize contacts cache */
        ContactsCache.getInstance();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            updateRingTones();

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

        initNotificationReceiver();

        registerReceiver(mUserReceiver, new IntentFilter(Intent.ACTION_USER_FOREGROUND));
        registerReceiver(mUserReceiver, new IntentFilter(Intent.ACTION_USER_BACKGROUND));
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (BuildConfig.ACRA_ENABLED) {
            try {
                SharedPreferences prefs = base.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY,
                        Context.MODE_PRIVATE);
                sDisplayName = prefs.getString(LoadUserInfo.DISPLAY_NAME, null);
                sDisplayAlias = prefs.getString(LoadUserInfo.DISPLAY_ALIAS, null);
                sUuid = prefs.getString(LoadUserInfo.USER_ID, null);
            } catch (Throwable e) {
                // Could not read preferences at this time, uuid and display name will be updated
                // in dialer activity as well
            }

            setUpAcra();
        }
    }

    public static Context getAppContext() {
        return mThisApplication.getApplicationContext();
    }

    private void updateRingTones() {

        Map<String, String> ringTones = RingtoneUtils.getRingtones(getAppContext());

        if (ringTones != null) {
            String ringtoneUri = ringTones.get(RingtoneUtils.TITLE_EMERGENCY);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getAppContext());
            SharedPreferences.Editor editor = preferences.edit();

            if (TextUtils.isEmpty(ringtoneUri)) {
                Log.d(TAG, "Registering ring tone " + RingtoneUtils.TITLE_EMERGENCY);

                try {
                    Uri tone = RingtoneUtils.createRingtone(getAppContext(), RingtoneUtils.TITLE_EMERGENCY,
                            RingtoneUtils.FILE_NAME_EMERGENCY, R.raw.emergency, "audio/*");
                    if (tone != null) {
                        /* if ringtone registration is successful, set emergency ringtone */
                        editor.putString(SettingsFragment.RINGTONE_EMERGENCY_KEY, tone.toString()).apply();
                    }
                } catch (Throwable e) {
                    Log.d(TAG, "Could not register ringtone " + RingtoneUtils.TITLE_EMERGENCY);
                }
            } else if (TextUtils.isEmpty(preferences.getString(SettingsFragment.RINGTONE_EMERGENCY_KEY, null))) {
                Log.d(TAG, "Emergency ringtone preference not set, adding " + ringtoneUri);
                editor.putString(SettingsFragment.RINGTONE_EMERGENCY_KEY, ringtoneUri).apply();
            }
        }
    }

    private void setUpAcra() {
        /*
         * Can be set by adding reportSenderFactoryClasses = {org.acra.sender.SentrySenderFactory.class}
         * to ReportsCrashes annotation.
         */
        @SuppressWarnings("unchecked")
        final Class<? extends ReportSenderFactory>[] senderFactoryClasses = new Class[1];
        senderFactoryClasses[0] = SentrySenderFactory.class;

        try {
            final ACRAConfiguration config = new ConfigurationBuilder(this)
                    .setReportSenderFactoryClasses(senderFactoryClasses)
                    .setKeyStoreFactoryClass(PinnedCertificateKeystoreFactory.class)
                    .build();
            ACRA.init(this, config);
        } catch (ACRAConfigurationException | RuntimeException ignore) {
            // TODO: Why does the SecurityException occur?
            // SEE: https://sentry.silentcircle.org/sentry/spa/issues/4596/

            // TODO: Why does the RuntimeException occur?
            // SEE: https://sentry.silentcircle.org/sentry/spa/issues/14348/
        }
    }

    //DebugLogging:
    public static void setLogFile(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppContext());
        File dir;
//        // Log files stores on internal storage only.
//        // use external storage if it is available for writing. otherwise use internal storage
//        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
//            // ext dir: /storage/emulated/0/silentcircle
//            dir = new File(Environment.getExternalStorageDirectory(), LogsService.LOG_FILE_DIR_NAME);
//            if (!dir.exists()) {
//                if (dir.mkdirs()) {
//                    Log.d(TAG, "External storage: silentcircle log directory is created!");
//                } else {
//                    Log.d(TAG, "External storage: silentcircle log directory is failed!");
//                }
//            }else{
//                // for testing purpose, it needs to be removed later
////                File[] listOfFiles = dir.listFiles();
////                if(listOfFiles != null) {
////                    for (int i = 0; i < listOfFiles.length; i++) {
////                        listOfFiles[i].delete();
////                    }
////                }
//            }
//
//        }
//        else
        {
            // Log files stores on internal storage only which is not accessible for any others.
            //internal dir: /data/data/com.silentcircle.silentphone/files/silentcircle
            dir = new File(getAppContext().getFilesDir(), LogsService.LOG_FILE_DIR_NAME);
            if(!dir.exists()){
                if (dir.mkdirs()) {
                    android.util.Log.d(TAG, "Internal storage: silentcircle log directory is created!");
                } else {
                    android.util.Log.d(TAG, "Internal storage: silentcircle log directory is failed!");
                }
            }
//            else{
//                // for testing purpose, it needs to be removed later
//                File[] listOfFiles = dir.listFiles();
//                if(listOfFiles != null) {
//                    for (int i = 0; i < listOfFiles.length; i++) {
//                        listOfFiles[i].delete();
//                    }
//                }
//            }
        }
        // save log file directory into preference to be used in adding/removing/uploading log files in LogsService/HandleDebugLoggingTask
        // if log file directory failed to create, store it as null or empty string
        // TODO: we may handle it here, because we are not able to save logs into to a file.
        prefs.edit().putString(LogsService.LOG_FILE_DIR, dir.getAbsolutePath()).apply();
        // use System.currentTimeMillis() as log file name which will be compare to the system time again
        // to determine which old file will be removed to avoid infinitly growing.
        long t = System.currentTimeMillis();
        TiviPhoneService.setLogFileName(dir.getAbsolutePath() + "/" + t);
    }

    /*
     * Initialize notification receiver for notifications not handled by views.
     * Local broadcast receiver cannot be defined in manifest so do it here in app context.
     */
    private void initNotificationReceiver() {
        mViewUpdater = new MessagingBroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (Action.from(intent)) {
                    case RECEIVE_MESSAGE:
                        ChatNotification.handleNotificationIntent(context, intent);
                        break;
                    case PROGRESS:
                    case UPLOAD:
                    case ERROR:
                    case RECEIVE_ATTACHMENT:
                        AttachmentEventReceiver.handleNotificationIntent(context, intent);
                        break;
                }
            }
        };

        IntentFilter filter = Action.filter(Action.RECEIVE_MESSAGE/*, Action.DATA_RETENTION_EVENT*/,
                Action.PROGRESS, Action.UPLOAD, Action.ERROR, Action.RECEIVE_ATTACHMENT);
        filter.setPriority(0);
        MessagingBroadcastManager.getInstance(getAppContext()).registerReceiver(mViewUpdater, filter);
    }

    public static boolean userBackgrounded() {
        return getAppContext().getSharedPreferences(TAG, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_BACKGROUNDED, false);
    }

    public static void setUserBackgrounded(boolean backgrounded) {
        getAppContext().getSharedPreferences(TAG, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_BACKGROUNDED, backgrounded)
                .commit();
    }
}
