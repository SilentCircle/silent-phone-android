/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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

package com.silentcircle.silentphone2.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.SelectSecureOca;
import com.silentcircle.silentphone2.fragments.DialDrawerFragment;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.LinkedList;

public class OutgoingCallReceiver extends BroadcastReceiver {
    private static final String TAG = "OutgoingCallReceiver";

    // silentphone 74536874663
    public static final String prefix_add = "SilentPhone";
    public static final String prefix_check = "74536874663"; //silentphone

    public static final String cancel_alpha = "SilentCancel";
    public static final String cancel_numeric = "745368226235"; //silentphone

    public static final String PSTN_NUMBER = "dst_number";
    public static final String OCA_CALL = "oca_all";

    private static final String call_to = "call_to";
    private static final String call_to_timestamp = "call_to_timestamp";

    private static final int STORED_NR_LIFETIME = 3000;       // 3 sec

    private static LinkedList<String> mSeenNumbers = new LinkedList<>();

    public static String getCallToNumber(Context context) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        long storedTS = prefs.getLong(call_to_timestamp, 0);
        long ts = Utilities.get_time_ms();
        long d = ts - storedTS;

        if (d > STORED_NR_LIFETIME || d < -STORED_NR_LIFETIME)
            return null;                                      // expired

        String nr = prefs.getString(call_to, "");

        if (nr == null || nr.length() == 0)
            return null;
        //forget number
        SharedPreferences.Editor e = prefs.edit();
        e.putString(call_to, "").apply();

        return nr;
    }

    private void setCallToNumber(Context context, String s) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(call_to, s).putLong(call_to_timestamp, Utilities.get_time_ms()).apply();
    }

    void startDialer(Context context, String number, Intent intent) {

        for (String num : mSeenNumbers) {
            if (number.equals(num)) {
                mSeenNumbers.remove(num);
            }
        }
        setCallToNumber(context, number);

        Intent i = new Intent(OCA_CALL);

        i.setClass(context, DialerActivity.class);
        i.setData(intent.getData());
        i.putExtra(PSTN_NUMBER, number);

        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        context.startActivity(i);
    }

    private String checkAndExtractNumber(String number, String prefix) {
        int idx = number.indexOf(prefix);
        if (idx == -1)
            return null;
        if (number.length() >= prefix.length() + 4) {
            idx += prefix.length();
            return number.substring(idx);
        }
        return null;
    }

    public void onReceive(Context context, Intent intent) {
        if (ConfigurationUtilities.mTrace) Log.v(TAG, "Received intent: " + intent);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useNativeOca = prefs.getBoolean(DialDrawerFragment.NATIVE_CALL_CHECK, false);

        if (!useNativeOca || DialerActivity.mNumber == null)    // CHECK: use OCA flag in LoadUserInfo?
            return;

        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
            String number = getResultData();

            if (ConfigurationUtilities.mTrace) Log.v(TAG, "Received number: '" + number + "'");
            if (number == null) {
                return;
            }
            String checkedNumber = checkAndExtractNumber(number, prefix_check);
            if (checkedNumber != null) {
                startDialer(context, checkedNumber, intent);
                setResultData(null);
                return;
            }
            checkedNumber = checkAndExtractNumber(number, prefix_add);
            if (checkedNumber != null) {
                startDialer(context, checkedNumber, intent);
                setResultData(null);
                return;
            }

            boolean cancelled = false;
            checkedNumber = checkAndExtractNumber(number, cancel_alpha);
            if (checkedNumber != null) {
                cancelled = true;
                number = checkedNumber;
            }
            else {
                checkedNumber = checkAndExtractNumber(number, cancel_numeric);
                if (checkedNumber != null) {
                    cancelled = true;
                    number = checkedNumber;
                }
            }

            for (String num : mSeenNumbers) {
                if (number.equals(num)) {
                    mSeenNumbers.remove(num);
                    if (cancelled)
                        setResultData(null);
                    return;
                }
            }
            mSeenNumbers.add(number);
            Intent select = new Intent();
            select.setClass(context, SelectSecureOca.class);
            select.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            select.putExtra(SelectSecureOca.TITLE, context.getResources().getString(R.string.app_name));
            select.putExtra(SelectSecureOca.MESSAGE, context.getResources().getString(R.string.do_secure_call));
            select.putExtra(SelectSecureOca.NEGATIVE_BUTTON, R.string.spa_no);
            select.putExtra(SelectSecureOca.POSITIVE_BUTTON, android.R.string.yes);
            select.putExtra(SelectSecureOca.PHONE_NUMBER, number);
            context.startActivity(select);
            setResultData(null);
        }
    }
}
