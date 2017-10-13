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
package com.silentcircle.silentphone2.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.silentcircle.silentphone2.BuildConfig;

public class DialerActivity extends Activity {

    private static final String TAG = DialerActivity.class.getSimpleName();

    private static final String DIALER_ACTIVITY_CLASS_NAME = DialerActivityInternal.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        Log.d(TAG, "Received intent " + getIntent());

        Intent intent = getIntent();

        if (intent != null) {
            String action = intent.getAction();
            Uri data = intent.getData();
            String scheme = (data != null) ? intent.getScheme() : null;
            String host = (data != null) ? data.getHost() : null;

            if (Intent.ACTION_MAIN.equals(action)) {
                startActivity(createForwardedIntent(intent));
            }
            else if (Intent.ACTION_VIEW.equals(action)) {
                if ("tel".equalsIgnoreCase(scheme)) {
                    startActivity(createForwardedIntent(intent));
                }
                else if("http".equals(scheme) && "silentphone".equals(host)) {
                    startActivity(createForwardedIntent(intent));
                }
                else if (DialerActivityInternal.PHONE_MIME.equals(intent.getType())) {
                    startActivity(createForwardedIntent(intent));
                }
                else if (DialerActivityInternal.MSG_MIME.equals(intent.getType())) {
                    startActivity(createForwardedIntent(intent));
                }
            }
            else if (Intent.ACTION_DIAL.equals(action) && "tel".equalsIgnoreCase(scheme)) {
                startActivity(createForwardedIntent(intent));
            }
            else if (Intent.ACTION_CALL.equals(action) && "tel".equalsIgnoreCase(scheme)) {
                startActivity(createForwardedIntent(intent));
            }
        }

        finish();
    }

    private Intent createForwardedIntent(Intent intent) {
        Intent forwardedIntent = new Intent(intent);
        forwardedIntent.setClassName(BuildConfig.APPLICATION_ID, DIALER_ACTIVITY_CLASS_NAME);
        forwardedIntent.setFlags(0);
        return forwardedIntent;
    }
}
