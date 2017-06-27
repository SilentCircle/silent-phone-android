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

package com.silentcircle.userinfo;

import android.os.Handler;
import android.os.Looper;

import com.silentcircle.logs.Log;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

/**
 * Implements the callback for generic SIP NOTIFY.
 *
 * The class must have an empty constructor, the C++ code relies on it.
 *
 * Created by werner on 24.11.15.
 */
public class SipNotifyHandler {
    private static final String TAG = SipNotifyHandler.class.getSimpleName();

    private static String REFRESH_PROVISIONING = "x-sc-refresh-provisioning";

    public SipNotifyHandler() {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Create empty SipNotifyHandler");
    }

    @SuppressWarnings("unused")
    public void onGenericSipNotify(byte[] content, byte[] event, byte[] contentType) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onGenericSipNotify");

        String contentString = null;
        if (content != null && content.length > 0)
            contentString = new String(content);

        String eventString = null;
        if (event != null && event.length > 0)
            eventString = new String(event);

        String contentTypeString = null;
        if (contentType != null && contentType.length > 0)
            contentTypeString = new String(contentType);
//        Log.d(TAG, "++++ content: " + contentString + ", event: " + eventString + ", type: " + contentTypeString);

        // We need the phone service to get a context and also to make sure a set of initialized
        // configuration data. LoadUserInfo requires the user name (or UUID etc).
        if (REFRESH_PROVISIONING.equals(eventString)) {
            ZinaMessaging.getInstance().rescanSiblingDevices();
            // refreshUserData starts an AsyncTask which requires that it runs on main loop.
            Runnable stateChange = new Runnable() {
                @Override
                public void run() {
                    LoadUserInfo li = new LoadUserInfo(true);
                    li.refreshUserInfo();
                }
            };
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(stateChange);
        }
    }
}
