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
package com.silentcircle.logs;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.logs.fragments.DebugLoggingDialogFragment;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.services.TiviPhoneService;

import java.io.File;

/**
 * This service will clean up LogDatabase every 24 hours to keep the last three days log entries
 * to reduce the size of database.
 *
 * Created by rli on 1/26/16.
 */
public class LogsService extends IntentService {
    public static final String TAG = "LogsCleanupService";
    public static final String LOG_FILE_DIR = "log_file_directory";
    public static final String LOG_FILE_DIR_NAME = "silentcircle";
    public static final String LOGS_SERVICE_SCHEDULED = "logs_service_scheduled";

//    public static final int INTERVALE = 60 * 60 *24;
//    public static final int INTERVAL = 60 * 60 * 5;
//    public static final long INTERVAL = 60 * 60 * 1000; //ONE_HOUR_INTERVAL
    public static final long INTERVAL = 60 * 60 * 1000 *24;  //ONE_DAY_INTERVAL
//    public static final long INTERVAL = 60 * 60 * 1000 * 24 * 3;  //THREE_DAY_INTERVAL

    private long mInterval;
    long mCurrentTimeMillis;

    public LogsService(){
        super(LogsService.class.getSimpleName());
    }

    /*
     * every day create a new log file.
     * selected interval:
     * for example: if selected interval is 3 days which means we keep 3 days log files.
     * so LogsService will be invoked every 24 hrs to create a new log file
     * and removing expired log files depends on selected interval.
     */
    @Override
    protected void onHandleIntent( Intent intent ) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //TODO: needs to do more to get correct interval.
        //For example: retrieve correct item in logging_interval_array according to the single selection.
        int intervalSelection = prefs.getInt(SettingsFragment.DEBUG_LOGGING_INTERVAL_SELECTION, 0) + 1;
        mInterval = INTERVAL * intervalSelection;
        mCurrentTimeMillis = System.currentTimeMillis();

        File folder;
        File[] listOfFiles = null;
        if(prefs.getBoolean(DebugLoggingDialogFragment.ENABLE_DEBUG_LOGGING, true)){
            String logDir = prefs.getString(LogsService.LOG_FILE_DIR, "");
            if(TextUtils.isEmpty(logDir)){
                // logDir should be created in SilentPhoneApplication class, in case a user delete it,
                // if it's in external storage. This will not happen because we use internal stroage only.
                SilentPhoneApplication spApp = (SilentPhoneApplication)this.getApplication();
                spApp.setLogFile();
            }
            else {
                folder = new File(logDir);
                if(folder.exists()) {
                    listOfFiles = folder.listFiles();
                    removeLogFile(listOfFiles);
                    TiviPhoneService.setLogFileName(logDir + "/" + mCurrentTimeMillis);
                }
                else{
                    Log.d(TAG, "*** directory holding log files is not existed. ");
                }
            }
        }

        // service will be stop when all log files are deleted if Logging was disabled.
        if(prefs.getBoolean(DebugLoggingDialogFragment.ENABLE_DEBUG_LOGGING, true) || (listOfFiles != null && listOfFiles.length != 0)) {
            prefs.edit().putBoolean(LOGS_SERVICE_SCHEDULED, true).apply();
            scheduleNextCheck();
        }
        else{
            prefs.edit().putBoolean(LOGS_SERVICE_SCHEDULED, false).apply();
        }
    }

    private void removeLogFile(File[] listOfFiles){
        if(listOfFiles != null) {
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    /*
                     * Delete file if it is older than mInterval days.
                     * Noticed that app takes over 3Gb on my device.
                     * This seemed more appropriate.
                     */
                    if (Long.parseLong(listOfFiles[i].getName()) + mInterval < mCurrentTimeMillis) {
                        listOfFiles[i].delete();
                    }
                }
            }
        }
    }

    private void scheduleNextCheck() {
        Intent intent = new Intent( this, this.getClass() );
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        long nextUpdateTimeMillis = mCurrentTimeMillis + INTERVAL;

        AlarmManager alarmManager = (AlarmManager) getSystemService( Context.ALARM_SERVICE );
        alarmManager.set(AlarmManager.RTC, nextUpdateTimeMillis, pendingIntent);

    }

}
