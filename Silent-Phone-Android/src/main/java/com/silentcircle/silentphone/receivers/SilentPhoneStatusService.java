/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.receivers;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.silentcircle.silentphone.ISilentPhoneInfo;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.activities.TMActivity;

/**
 * This is a simple service that implements a remote interface to provide the current
 * status of SilentPhone.
 *
 * Created by werner on 01.06.13.
 */
public class SilentPhoneStatusService extends Service {

    private static final String LOG_TAG = "SilentPhoneStatusService";

    private static final String SC_REMOTE = "com.silentcircle.silentphone.status";

    /**
     * User did not start the SilentPhone application.
     */
    private static final int NOT_STARTED = 1;

    /**
     * User started SilentPhone but provisioning is not yet complete
     */
    private static final int NOT_PROVISIONED = 2;

    /**
     * SilentPhone is offline and not registered with SilentCircle's SIP servers.
     * This is the case if the user selected 'Logout' from the menu or if no
     * network is available.
     */
    private static final int OFFLINE = 3;

    /**
     * SilentPhone is ready.
     */
    private static final int ONLINE = 4;

    /**
     * SilentPhone currently registers with SilentCircle's SIP servers.
     * This is a transient state that may stay for a few seconds.
     */
    private static final int REGISTER = 5;

    /**
     * Cannot determine status. This is an error condition.
     */
    private static final int UNKNOWN_ERROR = -1;

    @Override
    public IBinder onBind(Intent intent) {
        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "started via onBind: " + intent.getAction());
        if(SC_REMOTE.equals(intent.getAction()))
            return mBinderRemote;
        else
            return null;
    }

    private final IBinder mBinderRemote = new StatusService(this);

    private class StatusService extends ISilentPhoneInfo.Stub {
        private final SilentPhoneStatusService service;

        public StatusService(SilentPhoneStatusService service) {
            this.service = service;
        }
        @Override
        public int getCurrentStatus() {
            return service.getCurrentStatus();
        }
    }

    public int getCurrentStatus() {
        if (!TiviPhoneService.isInitialized())
            return NOT_STARTED;

        if (TiviPhoneService.mc == null && TiviPhoneService.doCmd("isProv") == 0)
            return NOT_PROVISIONED;

        int i = TiviPhoneService.getPhoneState();
        switch(i) {
            case 0:
                return OFFLINE;
            case 1:
                return REGISTER;
            case 2:
                return ONLINE;
            default:
                break;
        }
        return UNKNOWN_ERROR;
    }
}
