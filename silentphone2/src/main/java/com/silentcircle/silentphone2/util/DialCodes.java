/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import com.silentcircle.silentphone2.activities.TraceListActivity;
import com.silentcircle.silentphone2.receivers.OutgoingCallReceiver;
import com.silentcircle.silentphone2.services.TiviPhoneService;

/**
 * Created by werner on 31.01.15.
 */
public class DialCodes {
    public static boolean internalCallStatic(Activity activity, TiviPhoneService phoneService, String internalCommand) {

        if ("*##*112*".compareTo(internalCommand) == 0) {
//                String engineLog = TiviPhoneService.getInfo(0, -1, "");
//                Log.d(TAG, "++++ engine log: " + engineLog);
            Intent intent = new Intent(activity, TraceListActivity.class);
            activity.startActivity(intent);
            return true;
        }

        if ("*##*23466*".compareTo(internalCommand) == 0){
            return true;
        }

        if ("*##*1*".compareTo(internalCommand) == 0){
            Toast.makeText(activity, Build.BRAND + " " + Build.MANUFACTURER, Toast.LENGTH_SHORT).show();
            return true;
        }

        if ("*##*4430*".compareTo(internalCommand) == 0) {
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=0");
            Toast.makeText(activity, "Force TMR off", Toast.LENGTH_SHORT).show();
            TiviPhoneService.doCmd("set cfg.bufTMRAddr=");
            TiviPhoneService.doCmd(":s");
            return true;
        }

        if ("*##*4431*".compareTo(internalCommand) == 0) {
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=1");
            TiviPhoneService.doCmd(":s");
            Toast.makeText(activity, "Force TMR on", Toast.LENGTH_SHORT).show();
            return true;
        }

        if ("*##*4432*".compareTo(internalCommand) == 0) {
            String s = TiviPhoneService.getInfo(-1, -1, "cfg.iForceFWTraversal");
            String a = TiviPhoneService.getInfo(0, -1, "cfg.bufTMRAddr");
            Toast.makeText(activity, s + ": " + a, Toast.LENGTH_SHORT).show();
            return true;
        }

        if ("*##*4433*".compareTo(internalCommand) == 0) {
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=1");
            TiviPhoneService.doCmd(":s");
            Toast.makeText(activity, "FW traversal enabled", Toast.LENGTH_SHORT).show();
            return true;
        }

        if ("*##*4434*".compareTo(internalCommand) == 0) {
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=0");
            TiviPhoneService.doCmd(":s");
            Toast.makeText(activity, "FW traversal disabled", Toast.LENGTH_SHORT).show();
            return true;
        }

        if ("*##*8*".compareTo(internalCommand) == 0){
            TiviPhoneService.doCmd(":s");
            Toast.makeText(activity, "Save", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (phoneService != null && "*##*71*".compareTo(internalCommand) == 0) {
            phoneService.enableDisableWifiLock(true);
            Toast.makeText(activity, "WiFi on", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (phoneService != null && "*##*70*".compareTo(internalCommand) == 0) {
            phoneService.enableDisableWifiLock(false);
            Toast.makeText(activity, "WiFi off", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }
}
