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
package com.silentcircle.silentphone2.passcode;

import android.app.Activity;
import android.content.Intent;

import com.silentcircle.logs.Log;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.InCallActivity;
import com.silentcircle.silentphone2.receivers.AutoStart;

/**
 * This class controls if we need to show the PasscodeEnter screen when transitioning between activities
 * or if the app moves from or to the background.
 *
 * <p>By default, all activities that inherit the {@link AppLifecycleNotifierBaseActivity} will be
 * monitored by the controller and ask the passcode when needed. Special cases, like {@link
 * DialerActivity} where a passcode is needed under specific criteria, must be handled in {@link
 * #isPasscodeRequired(Activity)}. You can also add the activity name in {@link #mNoPasscodeRequiredActivities}
 * array, to disable the passcode requirement.
 *
 * <p>If you need to execute a "sensitive" part of your code only if the user is authorized, use
 * {@link #shouldWaitForPasscode(Activity)}.
 *
 * @author Petros Douvantzis
 */
public class PasscodeController implements AppLifecycleNotifier.ApplifecycleCallback {

    private static final String TAG = PasscodeController.class.getSimpleName();

    private static final Class[] mNoPasscodeRequiredActivities = new Class[] {
            InCallActivity.class
    };

    private PasscodeManager mPasscodeManager;

    //region Singleton
    //----------------------------------------------------
    private static PasscodeController singletonInstance;
    public static PasscodeController getSharedController() {
        if (singletonInstance == null) {
            singletonInstance = new PasscodeController();
        }
        return singletonInstance;
    }
    //endregion
    //----------------------------------------------------

    public PasscodeController() {
        mPasscodeManager = PasscodeManager.getSharedManager();
        AppLifecycleNotifier.getSharedInstance().setCallback(this);
    }

    //region Passcode logic
    //----------------------------------------------------

    /**
     * Checks if the current activity requires a passcode to enter it.
     *
     * For example, activities like {@link InCallActivity} do not require a passcode.
     *
     *
     * @param activity the activity to check passcode requirement
     * @see #mNoPasscodeRequiredActivities
     * @return true if a passcode is required, false if it is not required
     */
    private boolean isPasscodeRequired(Activity activity) {
        Intent intent = activity.getIntent();
        String action = (intent != null) ? intent.getAction() : null;
        String type = (intent != null) ? intent.getType() : null;
        if (DialerActivity.class.isInstance(activity)) {
//            Log.d(TAG, activity.getClass().getSimpleName() + " " + action + " " + type);
            boolean notRequired =
                    AutoStart.ON_BOOT.equals(action)
                            || Action.WIPE.equals(Action.from(intent));
            return !notRequired;
        }
        else if (PasscodeEnterActivity.class.isInstance(activity)) {
            if (action == null) {
                return false;
            }
            switch (action) {
                /*
                 * Usually, the user is already authorized when starting PasscodeEnter activity with
                 * these actions. If he goes away from the activity and returns back, Android has
                 * already destroyed it because of the "noHistory" attribute. So, there is no way he
                 * can enter it an-authorized. There is only an edge case, where if the user locks the
                 * device while on the activity, the activity is not destroyed. So, we need to make
                 * sure he is authorised just for this case.
                 */
                case PasscodeEnterActivity.ACTION_PASSCODE_VALIDATE:
                case PasscodeEnterActivity.ACTION_PASSCODE_CHANGE:
                    return true;
                default:
                    return false;
            }
        }
        else {
            for (Class _class : mNoPasscodeRequiredActivities) {
                if (_class.isInstance(activity)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onActivityStarted(Activity activity, boolean appEnteredForeground) {
        if (!mPasscodeManager.isPasscodeEnabled()) {
            return;
        }
        if (!mPasscodeManager.isUserAuthorized()) {
            if (appEnteredForeground && mPasscodeManager.canReauthorize()) {
                mPasscodeManager.reAuthorize();
            }
            else {
                if (!isPasscodeRequired(activity)) {
                    return;
                }
                Intent intent = new Intent(activity, PasscodeEnterActivity.class);
                intent.setAction(PasscodeEnterActivity.ACTION_PASSCODE_UNLOCK);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                activity.startActivity(intent);
            }
        }
    }

    @Override
    public void onAppEnteredBackground(Activity activity) {
        if (!mPasscodeManager.isPasscodeEnabled()) {
            return;
        }
        if (mPasscodeManager.isUserAuthorized()) {
            mPasscodeManager.startTimeoutTimer();
        }
    }
    //----------------------------------------------------
    //endregion


    //region Public interface
    //----------------------------------------------------

    /**
     * Checks if the activity should not run "sensitive" code, but should wait until the user is
     * authorized.
     *
     * <p>If it returns {@code false}, the activity should not execute the code and should attempt to
     * resume the execution when {@link Activity#onStart()} is called if this method retunrs
     * {@code true} at that time.
     *
     * @param activity the calling activity
     * @return {@code true} if passcode is enabled, the user is not authorized and the calling activity
     * requires it. Returns {@code false} otherwise.
     */
    public boolean shouldWaitForPasscode(Activity activity) {
        if (!mPasscodeManager.isPasscodeEnabled()) {
            return false;
        }
        if (mPasscodeManager.isUserAuthorized()) {
            return false;
        }
        return isPasscodeRequired(activity);
    }

    //----------------------------------------------------
    //endregion

}
