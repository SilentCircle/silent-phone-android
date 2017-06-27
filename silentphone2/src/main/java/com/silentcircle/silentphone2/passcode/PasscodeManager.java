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

import android.content.Context;
import android.content.SharedPreferences;


import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.silentphone2.util.Utilities;


/**
 * Manages everything related to the passcode.
 *
 * <p>The protocol described below must be honored by the other passcode related classes:
 *
 * <p>When a user enters the passcode successfully, he/she is considered authorized. While
 * transitioning between activities (those that inherit from {@link AppLifecycleNotifierBaseActivity},
 * the user remains authorized. When the user leaves a protected activity, a timer is set. When he
 * enters back in the app, depending on the time he was away, he may get re-authorized automatically
 * or may have to enter the passcode again.
 *
 * @author Petros Douvantzis
 */
public class PasscodeManager {

    private static final int MAX_ATTEMPTS_BEFORE_WIPE = 10;
    private static final boolean DEBUG_LOCKDOWN = false;

    // The time the user left the app while being authorized. If "0", the user is in the app and is
    // authorized. If -1, he is not authorized.
    private long mTimestampAuthorized = -1;

    private static final String PREF_KEY = "passcode_store";
    private static final String PREF_ENABLED = "passcode_enabled";
    private static final String PREF_PASSCODE = "passcode_pin";
    private static final String PREF_FINGERPRINT_ENABLED = "fingerprint_enabled";
    private static final String PREF_TIMEOUT = "passcode_timeout";
    private static final String PREF_FAILED_ATTEMPTS_COUNT = "passcode_failed_attempts_count";
    private static final String PREF_WIPE_ENABLED = "passcode_wipe_enabled";
    private static final String PREF_TIMESTAMP_LOCKED = "passcode_lock_timestamp";

    private SharedPreferences prefs;

    //region Singleton
    //----------------------------------------------------
    private static PasscodeManager singletonInstance;
    public static PasscodeManager getSharedManager() {
        if (singletonInstance == null) {
            singletonInstance = new PasscodeManager();
        }
        return singletonInstance;
    }
    //endregion
    //----------------------------------------------------

    public PasscodeManager() {
        prefs = SilentPhoneApplication.getAppContext().getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
    }

    //region Public interface
    //----------------------------------------------------

    /**
     * Checks if passcode protection is enabled
     *
     * @return true if passcode has been enabled, false otherwise
     */
    public boolean isPasscodeEnabled() {
        return prefs.getBoolean(PREF_ENABLED, false);
    }

    /**
     * Enables and disables the passcode. When enabling it, you should also set the current passcode.
     *
     * @param enabled true if you want to enable the passcode, false if you want to disable it.
     * @param passcode set the user's passcode when {@code enabled} is true. Set `null` if
     * {@code enabled} is false.
     */
    public void setPasscodeEnabled(boolean enabled, String passcode) {
        prefs.edit().putBoolean(PREF_ENABLED, enabled).commit();
        if (enabled) {
            String hash = md5(passcode);
            prefs.edit().putString(PREF_PASSCODE, hash).commit();
        }
        else {
            prefs.edit().putString(PREF_PASSCODE, "").commit();
        }
    }

    public boolean isFingerprintUnlockEnabled() {
        return prefs.getBoolean(PREF_FINGERPRINT_ENABLED, false);
    }

    public void setFingerprintUnlockEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_FINGERPRINT_ENABLED, enabled).commit();
    }

    public boolean isWipeEnabled() {
        return prefs.getBoolean(PREF_WIPE_ENABLED, false);
    }

    public void setWipeEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_WIPE_ENABLED, enabled).commit();
    }

    /**
     * Validates the provided passcode.
     *
     * @return true if the passcode provided matches the stored passcode
     */
    public boolean isPasscodeCorrect(String passcode) {
        String hash = md5(passcode);
        String currentPasscode = prefs.getString(PREF_PASSCODE, "");
        return currentPasscode.equals(hash);
    }

    /**
     * Get the timeout interval allowed for re-authorization.
     *
     * @return the timeout in seconds
     */
    public int getTimeout() {
        return prefs.getInt(PREF_TIMEOUT, 0);
    }

    /**
     * Sets the timeout interval allowed for re-authorization.
     *
     * <p>Setting 0 means that the moment the user leaves the app, he it considered de-authorized.
     * When he enters back in, {@link #canReauthorize()} will always return false.
     *
     * @see #canReauthorize()
     * @param timeout the timeout in seconds
     */
    public void setTimeout(int timeout) {
        prefs.edit().putInt(PREF_TIMEOUT, timeout).commit();
    }

    /**
     * Should be called when an authorized user leaves the app, in order to measure the time
     * interval until he gets back into the app.
     *
     * <p>The call to {@link #canReauthorize()} will measure the time interval from this call.
     */
    public void startTimeoutTimer() {
        /*
         * We actually don't start a timer, but just store the timestamp, which we'll compare with
         * the timestamp of the moment the user enters the app.
         */
        if (!isUserAuthorized()) {
            throw new RuntimeException("Can't reset the passcode timeout if not authorized.");
        }
        mTimestampAuthorized = System.currentTimeMillis();
    }

    public void authorize(String passcode) {
        if (!isPasscodeCorrect(passcode)) {
            throw new RuntimeException("Tried to authorize with wrong passcode");
        }
        mTimestampAuthorized = 0;
    }

    //TODO: use a key from Android's keystore that is protected with fingerprint
    public void authorizeFingerprint() {
        if (!isFingerprintAllowed()) {
            throw new RuntimeException("Can't use fingerprint now due to lockdown");
        }
        mTimestampAuthorized = 0;
    }

    public void deAuthorize() {
        mTimestampAuthorized = -1;
    }

    /**
     * Checks if the user has entered his passcode in the past and is still authorized.
     *
     * @return true if he is authorized, false otherwise
     */
    public boolean isUserAuthorized() {
        if (mTimestampAuthorized == 0) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the user can be re-authorized. This should be called when the user gets to the app
     * from the background.
     *
     * <p>If the method returns true, you can call {@link #reAuthorize()}. Otherwise the passlock
     * must be displayed to authorize the user.
     *
     * <p>The method compares the time interval since an authorized user left the app until he returned
     * to it, against the maximum allowed interval from {@link #getTimeout()}. If the user returned in
     * the allowed timeout interval, the method returns 'true'.
     *
     * @return true if the user can be re-authorized
     */
    public boolean canReauthorize() {
        long now = System.currentTimeMillis();
        if (mTimestampAuthorized <= 0) {
            return false;
        }
        if (now - mTimestampAuthorized < getTimeout() * 1000L ) {
            return true;
        }
        return false;
    }

    public void reAuthorize() {
        if (mTimestampAuthorized <= 0) {
            throw new RuntimeException("Can't re-authorize at this state.");
        }
        mTimestampAuthorized = 0;
    }

    /**
     * Returns the number of times the user failed to enter a correct passcode.
     *
     * <p>Can be compared to a maximum allowed threshold.
     *
     * @return the number of consecutive failed attempts
     */
    public int getFailedAttemptsCount() {
        return prefs.getInt(PREF_FAILED_ATTEMPTS_COUNT, 0);
    }

    /**
     * Returns the number of remaining tries before the data are wiped.
     *
     * <p>This can be used if {@link #isWipeEnabled()} it true. Otherwise, it returns -1.
     *
     * @return the number of tries left until wipe when wipe is enabled, -1 when wipe is disabled
     */
    public int getRemainingAttemptsUntilWipe() {
        if (isWipeEnabled()) {
            return MAX_ATTEMPTS_BEFORE_WIPE - getFailedAttemptsCount();
        }
        else {
            return -1;
        }
    }

    /**
     * Increases the failed attempt counter by 1.
     *
     * <p>Must me called after a user's failed attempt.
     *
     * @see #getFailedAttemptsCount()
     */
    public void increaseFailedAttemptsCount() {
        int attempts = getFailedAttemptsCount();
        if (isWipeEnabled() && getRemainingAttemptsUntilWipe() == 0) {
            throw new RuntimeException("Can't increase failed attempts. No remaining attempts left.");
        }
        attempts += 1;
        prefs.edit().putInt(PREF_FAILED_ATTEMPTS_COUNT, attempts).commit();
    }

    /**
     * Sets the failed attempts counter to zero.
     *
     * <p>Must be called after a successful entry of the passcode.
     */
    public void resetFailedAttemptsCount() {
        prefs.edit().putInt(PREF_FAILED_ATTEMPTS_COUNT, 0).commit();
    }

    /**
     * Returns true if we are not in lockdown mode.
     * @return true if fingerprint is allowed
     */
    public boolean isFingerprintAllowed() {
        return (getLockdownTimeInterval() == 0);
    }

    /**
     * Returns the duration needed from SP lock to unlock, according to the current
     * {@link #getFailedAttemptsCount() failed attempts number}.
     *
     * <p>This is not the actual time needed since SP was locked. Use {@link #getRemainingLockDownTime()}
     * for that.
     *
     * <p> It returns {@code 0}, if no lockdown is needed.
     *
     * @return the lockdown time in seconds
     */
    public int getLockdownTimeInterval() {
        int failedAttempts = getFailedAttemptsCount();
        if (!DEBUG_LOCKDOWN) {
            if (failedAttempts < 5) {
                return 0;
            } else if (failedAttempts == 5) {
                return 1 * 60;
            } else if (failedAttempts == 6) {
                return 5 * 60;
            } else if (failedAttempts == 7) {
                return 15 * 60;
            } else {
                return 60 * 60;
            }
        }
        else {
            if (failedAttempts < 5) {
                return 0;
            } else {
                return 7;
            }
        }
    }

    /**
     * Starts counting time from the last failed attempt to enter the passcode.
     *
     * <p>Must be called after a user's failed attempt.
     *
     * @return the lockdown interval according to {@link #getLockdownTimeInterval()}
     */
    public int startLockdownTimer() {
        int timeInterval = getLockdownTimeInterval();
        if (timeInterval != 0) {
            prefs.edit().putLong(PREF_TIMESTAMP_LOCKED, System.currentTimeMillis()).commit();
        }
        return timeInterval;
    }

    /**
     * Resets the lockdown timer.
     *
     * <p>Must be called after a successful entry of the passcode.
     */
    public void resetLockdownTimer() {
        prefs.edit().putLong(PREF_TIMESTAMP_LOCKED, 0).commit();
    }

    /**
     * Returns the actual time needed until SP can be unlocked.
     *
     * <p>It's the time left until SP can be unlocked according to the moment {@link #startLockdownTimer()}
     * was called and the current {@link #getLockdownTimeInterval() countdown interval}.
     *
     * <p>The time returned will be monotonically reduced until it reaches {@code 0}. Negative numbers
     * are not returned. If it returns {@code 0}, either SP was never locked or it was locked and
     * it should be unlocked now.
     *
     * @return seconds left until SP can be unlocked
     */
    public long getRemainingLockDownTime() {
        long timestamp = prefs.getLong(PREF_TIMESTAMP_LOCKED, 0);
        if (timestamp == 0) {
            return 0;
        }
        long timePassed = (System.currentTimeMillis() - timestamp) / 1000; // ms to sec
        long remaining = getLockdownTimeInterval() - timePassed;
        if (remaining < 0) remaining = 0;
        return remaining;
    }

    //----------------------------------------------------
    //endregion


    public String md5(String s) {
        String hash = Utilities.hashMd5(s);
        if (hash == null) {
            hash = Integer.toString(s.hashCode());
        }
        return hash;
    }

}
