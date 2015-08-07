package com.silentcircle.silentphone2.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.fragments.DialDrawerFragment;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

public class AutoStart extends BroadcastReceiver {
    private static final String TAG = AutoStart.class.getSimpleName();

    public static final String ON_BOOT = "start_on_boot";

    private static boolean mDisableAutoStart;

    public AutoStart() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action))
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean startOnBoot = prefs.getBoolean(DialDrawerFragment.START_ON_BOOT, true);

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Auto start action: " + action + ", startOnBoot: " + startOnBoot);

        if (startOnBoot && !mDisableAutoStart && Intent.ACTION_BOOT_COMPLETED.equals(action)){
            Intent i = new Intent(context, DialerActivity.class);
            i.setAction(ON_BOOT);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    public static void setDisableAutoStart(boolean disable) {
        mDisableAutoStart = disable;
    }
}
