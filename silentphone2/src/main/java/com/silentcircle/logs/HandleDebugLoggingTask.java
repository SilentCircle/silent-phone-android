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

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.widget.Toast;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.activities.DebugLoggingActivity;
import com.silentcircle.logs.fragments.DebugLoggingFragment;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivityInternal;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * HandleDebugLoggingTask handles two tasks -- uploading logs to our server
 *
 * Created by rli on 1/13/16.
 */
public class HandleDebugLoggingTask extends AsyncTask<String, Integer, String> {

    public static final String TAG = "HandleDebugLoggingTask";
    public static final String DEBUG_LOGS_TASK = "debug_logs_task";
    public static final String DECRYPTED_LOG_TEMP_DIR = "/temp";
    public static final String METHOD = "POST";
    public static final String SUCCESS = "success";
    public static final String SYSTEM_LOGCAT_COMMAND = "logcat -d -v time";
    public static final String optFileEolStr = "\r\n"; // factory: "\r\n"
    private AppCompatActivity mActivity;
    private Toast mToast;
    private String mTask;

    private DataOutputStream mWriter;

    private String mError = "";

    private ProgressDialog mProgressDialog;
    public HandleDebugLoggingTask(AppCompatActivity activity){
        mActivity = activity;
    }

    @Override
    public void onPreExecute() {
        super.onPreExecute();
    }

    /*
     * params[0]: task
     * params[1]: description of problem if any
     */
    @Override
    public String doInBackground(String... params) {
        mTask = params[0];
        String result = "";  //to avoid NullPointerException onPostExecute.

        if(mTask.equals(DEBUG_LOGS_TASK)){
            //decrypting log files.
            mError = decryptLogs();
            if(mError != null){
                return mError;
            }

            //Utilities.Sleep(10);
            //uploading log files and system log file
            publishProgress(R.string.uploading);
            result = uploadLogs(params[1]);

            //delete temporary stored decrypted log files and system log file
            deletDecryptedLogFiles();

        }
        return result;
    }

    protected void onProgressUpdate(Integer... resId) {
        ((DebugLoggingActivity)mActivity).updateProgressMessage(resId[0]);
    }
    @Override
    public void onPostExecute(String result) {
        DebugLoggingFragment.enableSendBtn = true;
        ((DebugLoggingActivity)mActivity).stopProgress();
        Fragment fragment = ((DebugLoggingActivity) mActivity).getSupportFragmentManager().findFragmentByTag(DebugLoggingFragment.TAG);
        if(fragment != null){
            ((DebugLoggingFragment)fragment).enabelSendButton();
        }

        if(mTask.equals(DEBUG_LOGS_TASK)) {
            if (result!=null && result.equals(SUCCESS)) {
                ((DebugLoggingActivity) mActivity).finish();
            }
            mToast = Toast.makeText(mActivity, result, Toast.LENGTH_LONG);
            mToast.setGravity(Gravity.CENTER, 0, 0);
            mToast.show();
        }
    }

    private String decryptLogs(){
        String result = null;
        File dir;
        // pass directory where holds all log files to decrypt log files to be sent to the server
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        String logPath = prefs.getString(LogsService.LOG_FILE_DIR, "");
        dir = new File(logPath);
        if (dir.exists()) {
            //create "silentcircle/temp" directory to hold decrypted log files temporarily.
            File tempLogDir = new File(logPath + DECRYPTED_LOG_TEMP_DIR);
            if(!tempLogDir.exists()){
                if (!tempLogDir.mkdirs()) {
                    result = mActivity.getString(R.string.can_not_store_decrypted_logs);
                    return result;
                }
            }


            File[] listOfFiles = dir.listFiles();
            List<String> list = new ArrayList<>();
            if(listOfFiles != null) {
                for (int i = 0; i < listOfFiles.length; i++) {
                    if (listOfFiles[i].isFile()) {
                        String name = listOfFiles[i].getName();
                        try{
                            //Because using system time in millisecond as file name
                            //we check it to avoid corrupted file name before pass it to native code.
                            Long.parseLong(name);
                            list.add(name);
                        } catch (NumberFormatException e) {
                            //delete file
                            listOfFiles[i].delete();
                        }
                    }
                }

                result = TiviPhoneService.decryptLogs(list.toArray(new String[list.size()]), logPath);
            }
            else{
                ((DebugLoggingActivity)mActivity).stopProgress();
                result = mActivity.getString(R.string.error_read_logs);
            }

        }
        else{
            ((DebugLoggingActivity)mActivity).stopProgress();
            result = mActivity.getString(R.string.error_read_logs);
        }
        return result;
    }

    private void deletDecryptedLogFiles(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        String logDir = prefs.getString(LogsService.LOG_FILE_DIR, "")+"/temp";
        //get list of log file names
        File folder;
        File[] listOfFiles = null;
        folder = new File(logDir);
        if(folder.exists()){
            listOfFiles = folder.listFiles();
            for(int i=0; i<listOfFiles.length; i++){
                listOfFiles[i].delete();
            }
        }

    }


    private String uploadLogs(String description){
        FileOutputStream out = null;
        BufferedReader reader = null;
        byte[] data = KeyManagerSupport.getSharedKeyData(mActivity.getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (data == null) {
            mError = mActivity.getString(R.string.logging_no_api_key_error);
            return mError;
        }
        try {
            String devAuthorization = new String(data, "UTF-8");
            // send logs url --> https://sccps.silentcircle.com/v1/feedback/?api_key=%1$s
            String urlString = String.format(ConfigurationUtilities.getSendLogsUrl(mActivity), devAuthorization);
            URL url = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mActivity) + urlString);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod(METHOD);
            connection.setRequestProperty("content-type", "text/plain; charset=utf-8");

            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            mWriter = new DataOutputStream(connection.getOutputStream());

            //uploading description of problem
            //uploading build info
            //uploading device info
            //uploading last 30 mins of system logs
            //uploading log files
            mError = uploading(description);
            mWriter.flush();
            mWriter.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuffer buffer = new StringBuffer();
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }
                try {
                    //{"result": "success"} will be response of successful upload logs.
                    final JSONObject obj = new JSONObject(buffer.toString());
                    return obj.getString("result");
                }catch (JSONException e){
                }
            }
            else{
                mError = connection.getResponseMessage();

                if (ContextCompat.checkSelfPermission(mActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    String line;
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));

                    //TODO: if got 500 response, save error into external storage to get error's reference number on some device/android os
                    if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                        File file = new File(Environment.getExternalStorageDirectory(), "sc_error.html");
                        out = new FileOutputStream(file);
                        while ((line = reader.readLine()) != null) {
                            out.write(line.getBytes());
                        }
                        out.flush();
                        out.close();
                    }
                    reader.close();
                }
            }
        } catch (UnsupportedEncodingException e) {
            mError = e.getMessage();
        }catch (MalformedURLException e){
            mError = e.getMessage();
        }
        catch (IOException e){
            mError = e.getMessage();
        }finally {
            if(mWriter != null){
                try {
                    mWriter.flush();
                    mWriter.close();
                }catch (IOException e) {
                    mError = e.getMessage();
                }
            }
            if(out != null){
                try {
                    out.flush();
                    out.close();
                }catch (IOException e) {
                    mError = e.getMessage();
                }
            }
            if(reader != null){
                try {
                    reader.close();
                }catch (IOException e) {
                    mError = e.getMessage();
                }
            }
        }

        return mError;
    }

    private String uploading(String description){
        //get log files directory
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        String logDir = prefs.getString(LogsService.LOG_FILE_DIR, "")+"/temp";
        //get list of log file names
        File folder;
        File[] listOfFiles = null;
        folder = new File(logDir);
        if(!folder.exists()){
            //Log.d(TAG, Do not have directory to store decrypted log files. This should not happen.");
            mError = mActivity.getString(R.string.can_not_store_decrypted_logs);
            return mError;
        }

        BufferedReader fileReader = null;

        try {
            // write out description of problem which provided by an user
            String line = mActivity.getString(R.string.description_of_problem) + ":\n" + description + "\n\n";
            mWriter.writeBytes(URLEncoder.encode(line, "utf-8"));

            // write out build info
            line = BuildConfig.VERSION_NAME + " (" + BuildConfig.SPA_BUILD_NUMBER + ", " + BuildConfig.SPA_BUILD_COMMIT + ")\n";
            mWriter.writeBytes(URLEncoder.encode(line, "utf-8"));
            line = mActivity.getString(R.string.dial_drawer_build_flavor, BuildConfig.FLAVOR) + "\n";
            mWriter.writeBytes(URLEncoder.encode(line, "utf-8"));
            line = mActivity.getResources().getString(R.string.active_configuration) + ": " +
                    (ConfigurationUtilities.mUseDevelopConfiguration ? mActivity.getResources().getString(R.string.develop) : mActivity.getResources().getString(R.string.production)) + "\n\n";
            mWriter.writeBytes(URLEncoder.encode(line, "utf-8"));
            //write out device info
            line = getDeviceInfo(mActivity) + "\n\n";
            mWriter.writeBytes(URLEncoder.encode(line, "utf-8"));

            //TODO: commented for now, because got 500 response code for some device / Android OS


            //write out last 30 mins of system logs
            Process process = Runtime.getRuntime().exec( SYSTEM_LOGCAT_COMMAND );
            if( process != null ) {
                BufferedReader bufferedReader = new BufferedReader( new InputStreamReader( process.getInputStream() ) );
                line = "\n\n------ start system log ------\n";
                mWriter.writeBytes(URLEncoder.encode(line, "utf-8"));
                while( ( line = bufferedReader.readLine() ) != null ) {
                    mWriter.writeBytes(URLEncoder.encode(line, "utf-8") + optFileEolStr);
                }
                line = "------ end system log ------\n";
                mWriter.writeBytes( URLEncoder.encode(line, "utf-8") );
            }

            listOfFiles = folder.listFiles();
            if(listOfFiles == null || listOfFiles.length == 0){
                mError = mActivity.getString(R.string.no_decrypted_log_files);
                return mError;
            }
            try {
                //sending out decrypted log files and the last 30 minutes of system logs from /temp/syslogs
                //mLog.debug( "obtainSystemLog() executing logcat" );
                for (int i=0; i<listOfFiles.length; i++) {
                    fileReader = new BufferedReader(new FileReader(listOfFiles[i]));
                    while ((line = fileReader.readLine()) != null) {
                        mWriter.writeBytes(URLEncoder.encode(line, "utf-8"));
                    }
                    fileReader.close();
                }
            }catch(IOException e) {
                mError = e.getMessage();
            }
        }catch (IOException e){
            mError = e.getMessage();
        }
        return mError;
    }

    private String getDeviceInfo(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        Configuration config = activity.getResources().getConfiguration();
        final int size = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        final String swSetting = activity.getString(R.string.sw_setting);
        return  activity.getResources().getString(R.string.device_information)+":\n" +
                Build.MANUFACTURER + ", " + Build.BRAND + ", " + Build.MODEL + ", " + Build.DEVICE +
                "\n"+activity.getResources().getString(R.string.screen_density)+": " + metrics.densityDpi + " (" + size + ", " + swSetting + ")" +
                ((DialerActivityInternal.mAutoAnswerForTesting) ? "\n"+activity.getResources().getString(R.string.auto_answered) + DialerActivityInternal.mAutoAnsweredTesting : "");

    }
}
