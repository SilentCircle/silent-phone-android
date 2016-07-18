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

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Some re-usable async tasks for various purposes.
 *
 * Created by werner on 16.12.15.
 */
public class AsyncTasks {

    private static String TAG = "AsyncTasks";

    public static void asyncCommand(String command) {
        AsynchronousCommandTask task = new AsynchronousCommandTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, command);
    }

    public static void readStream(InputStream in, StringBuilder content) {
        BufferedReader reader;
        content.delete(0, content.length()); // remove old content
        reader = new BufferedReader(new InputStreamReader(in));

        try {
            for (String str = reader.readLine(); str != null; str = reader.readLine()) {
                content.append(str).append('\n');
            }
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "readStream: " + content);
        } catch (IOException e) {
            Log.w(TAG, "I/O Exception: " + e);
            if (ConfigurationUtilities.mTrace) e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                Log.w(TAG, "I/O Exception close stream: " + e);
                if (ConfigurationUtilities.mTrace) e.printStackTrace();
            }
        }
    }

    public static class AsynchronousCommandTask extends AsyncTask<String, Void, Integer> {
        private String mCommand;

        @Override
        protected Integer doInBackground(String... commands) {
            mCommand = commands[0];
            long startTime = System.currentTimeMillis();
            if (mCommand != null) {
                TiviPhoneService.doCmd(mCommand);
            }
            return (int)(System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d("AsynchronousCommandTask", "Processing time for command '"+ mCommand + "': " + time);
        }
    }

    public static class UserDataBackgroundTask extends AsyncTask<String, Void, Integer> {
        private String mUidIn;
        protected byte[] mData;
        protected UserInfo mUserInfo = new UserInfo();
        protected int[] errorCode = new int[1];

        public UserDataBackgroundTask() { }

        @Override
        protected Integer doInBackground(String... uid) {
            mUidIn = uid[0];
            long startTime = System.currentTimeMillis();
            if (mUidIn != null) {
                mData = AxoMessaging.getUserInfo(mUidIn, null, errorCode);
            }
            mUserInfo =  parseUserInfo(mData);
            return (int)(System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace)
                Log.d("UserDataBackgroundTask", "Processing time for getUserData '"+ mUidIn + "': " + time);
        }
    }

    public static abstract class UserDataBackgroundTaskNotMain implements Runnable {
        final private String mUidIn;
        protected byte[] mData;
        protected UserInfo mUserInfo = new UserInfo();
        protected int[] errorCode = new int[1];

        public UserDataBackgroundTaskNotMain(String uid) {
            mUidIn = uid;
        }

        @Override
        @WorkerThread
        public void run () {
            long startTime = System.currentTimeMillis();
            if (mUidIn != null) {
                mData = AxoMessaging.getUserInfo(mUidIn, null, errorCode);
            }
            mUserInfo =  parseUserInfo(mData);
            if (ConfigurationUtilities.mTrace)
                Log.d("UserDataBackgroundTaskN", "Processing time for getUserData '"+ mUidIn + "': " + (System.currentTimeMillis() - startTime));
            onPostRun();
        }

        @WorkerThread
        public abstract void onPostRun();
    }

    public static class UserInfo {
        public String mAlias;
        public String mDisplayName;
        public String mUuid;
        public String mLookupUri;
        public String mAvatarUrl;
    }

    @Nullable
    public static UserInfo parseUserInfo(byte[] userData) {
        if (userData == null)
            return null;
        UserInfo ui = new UserInfo();
        try {
            JSONObject data = new JSONObject(new String(userData));
            if (data.has("alias0")) {
                ui.mAlias = data.optString("alias0");
            }
            if (data.has("display_name")) {
                ui.mDisplayName = data.optString("display_name");
            }
            if (data.has("uid")) {
                ui.mUuid = data.optString("uid");
            }
            if (data.has("lookup_uri")) {
                ui.mLookupUri = data.optString("lookup_uri");
            }
            if (data.has("avatar_url")) {
                ui.mAvatarUrl = data.optString("avatar_url");
            }
        } catch (JSONException ex) {
            Log.d("parseUserInfo", "JSON exception", ex);
            ui = null;
        }
        return ui;
    }
}
