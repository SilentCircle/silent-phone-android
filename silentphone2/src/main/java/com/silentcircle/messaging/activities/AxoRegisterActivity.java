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

package com.silentcircle.messaging.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import com.silentcircle.messaging.fragments.AxoDevicesFragment;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.silentphone2.R;

/**
 * Support activity to handle Axolotl registration.
 *
 * Created by werner on 20.06.15.
 */
public class AxoRegisterActivity  extends AppCompatActivity {

    @SuppressWarnings("unused")
    private static final String TAG = "AxoRegisterActivity";

    public static final String ACTION_REGISTER = "register";
    public static final String ACTION_MANAGE   = "manage";

    public static final String ACTION = "action";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_axo_devices);
        String action = getIntent().getAction();
        if (TextUtils.isEmpty(action)) {
            finish();
            return;
        }

        if (savedInstanceState == null) {
            Bundle arg = new Bundle();
            arg.putString(ACTION, action);
            AxoDevicesFragment axoDevicesFragment = AxoDevicesFragment.newInstance(arg);

            getFragmentManager().beginTransaction()
                    .add(R.id.axo_devices_container, axoDevicesFragment)
                    .commit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void noRegistration() {
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
        axoMessaging.setAskToRegister(false);
        finish();
    }

    public void registerDevice() {
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
        axoMessaging.registerDeviceMessaging(false);
        finish();
    }
}
