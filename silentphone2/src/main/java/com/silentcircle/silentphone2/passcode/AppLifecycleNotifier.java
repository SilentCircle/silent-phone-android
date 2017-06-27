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
import android.os.Handler;
import android.os.Looper;

import com.silentcircle.logs.Log;

import java.lang.ref.WeakReference;

/**
 * Class that detects when the app went to background or entered foreground and calls the
 * corresponding callback methods.
 *
 * <p>All activities must call the {@link #onStart(Activity)} and {@link #onStop(Activity)} methods,
 * so that the class can determine when a transitioned was made from one app activity to another
 * or when the app when to the background.
 *
 * <p>If the app enters an activity that does not call these methods, the class will infer that the
 * app when to the background. If an app starts an external activity, call
 * {@link #onWillStartExternalActivity(boolean)} to make the notifier not register that the app
 * went to the background.
 *
 * @author Petros Douvantzis
 * @see AppLifecycleNotifierBaseActivity
 */
public class AppLifecycleNotifier {

    public interface ApplifecycleCallback {

        /**
         * Called when the app starts an activity.
         *
         * @param activity the activity that starts
         * @param appEnteredForeground If true, the app entered the activity after being on the
         *                             background. If false the app transitioned to this activity
         *                             from the previous one.
         */
        public void onActivityStarted(Activity activity, boolean appEnteredForeground);

        /**
         * Called when the app enters background.
         *
         * @param activity the activity that is being stopped in order to get the app to the background
         */
        public void onAppEnteredBackground(Activity activity);
    }

    private static final String TAG = AppLifecycleNotifier.class.getSimpleName();
    private static final int ACTIVITY_STOPPED_INTERVAL = 1000;

    private boolean mInBackground = true;
    private WeakReference<Activity> mCurrentActivity;
    private ApplifecycleCallback callback;
    private boolean mExternalActivityStarted;
    private Handler mHandler;

    //region Singleton
    //----------------------------------------------------
    private static AppLifecycleNotifier singletonInstance;
    public static AppLifecycleNotifier getSharedInstance() {
        if (singletonInstance == null) {
            singletonInstance = new AppLifecycleNotifier();
        }
        return singletonInstance;
    }
    //endregion
    //----------------------------------------------------

    public AppLifecycleNotifier() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void setCallback(ApplifecycleCallback callback) {
        this.callback = callback;
    }

    /**
     * Must be called by all of the app's activities in the onStart().
     *
     * @param activity the calling activity
     */
    public void onStart(Activity activity) {
//        Log.d(TAG, "onStart " + activity.getClass().getSimpleName());
        mExternalActivityStarted = false;
        onEnteredActivity(activity);
    }


    /**
     * Must be called by all of the app's activities in the onResume().
     *
     * @param activity the calling activity
     */
    public void onResume(Activity activity) {
//        Log.d(TAG, "onResume " + activity.getClass().getSimpleName());
        mExternalActivityStarted = false;
        /*
         * If not equal, it means that onResume() is called without calling onStart() first. This
         * can happen if activity A starts activity B which finishes really fast. Activity A is just
         * paused and not stopped. So, after activity B is paused, activity A is resumed and activity
         * B is stopped.
         * If we don't act here, when activity's B onStop() is called, we will decide that the app
         * went to background because another activity was not started.
         */
        if (activity != mCurrentActivity.get()) {
            onEnteredActivity(activity);
        }
    }

    /**
     * Called by {@link #onStart(Activity)} and {@link #onResume(Activity)}.
     */
    private void onEnteredActivity(Activity activity) {
        mHandler.removeCallbacksAndMessages(null);

        mCurrentActivity = new WeakReference<Activity>(activity);
        boolean wasInBackground = (mInBackground == true);
        mInBackground = false;

        Log.d(TAG, "Activity entered. App entered foreground: " + wasInBackground);
        if (callback != null) {
            callback.onActivityStarted(activity, wasInBackground);
        }
    }

    /**
     * Must be called by all of the app's activities in the onPause().
     *
     * @param activity the calling activity
     */
    public void onPause(Activity activity) {
//        Log.d(TAG, "onPause " + activity.getClass().getSimpleName());
    }

    /**
     * Must be called by all of the app's activities in the onStop().
     *
     * @param activity the calling activity
     */
    public void onStop(final Activity activity) {
//        Log.d(TAG, "onStop " + activity.getClass().getSimpleName());

        /* When transitioning to another activity, the onStop() of the previous activity is called
         * after the onStart() or onResume() of the next activity. This check makes sure that we have not
         * transitioned to another activity, but we have gone to background.
         */
        if (mCurrentActivity.get() == activity && !mExternalActivityStarted) {
            /*
             * Android may destroy and create again our activity. This is interpreted as getting
             * to background, since no activity transition occurred. As a workaround, we postpone
             * deciding that we on the background. If no other activity is started or resumed soon,
             * the runnable will run.
             */
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mInBackground = true;
                    Log.d(TAG, "app entered background");
                    if (callback != null) {
                        callback.onAppEnteredBackground(activity);
                    }
                }
            }, ACTIVITY_STOPPED_INTERVAL);
        }
        mExternalActivityStarted = false;
    }

    //region Public interface
    //----------------------------------------------------

    /**
     * Call the method to let the notifier know that you started an external activity so that it
     * doesn't infer that the app went to background.
     *
     * <p>If you start an activity that belongs to another app, the notifier will assume that the app
     * went to background. Practically, the user is still "inside" our task even though he is using code
     * not controlled by us. Calling this method, makes the notifier not "register" that the app went
     * to the background.
     *
     * <p>Note that if the user sends the task in the background while
     * the external activity is running, we can't know that it went to the background. When the user
     * is back to one {@link AppLifecycleNotifierBaseActivity}, the notifier will function as normal.
     *
     * @param start Set true when tou are about to start an external activity. Set false, if
     *              you didn't actually start the activity because of an error.
     */
    public void onWillStartExternalActivity(boolean start) {
        mExternalActivityStarted = start;
    }

    //endregion
    //----------------------------------------------------
}
