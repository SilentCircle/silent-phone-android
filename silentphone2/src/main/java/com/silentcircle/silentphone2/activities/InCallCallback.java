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

package com.silentcircle.silentphone2.activities;

import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;

/**
 * Callback functions of InCall activity to support its fragments.
 *
 * Created by werner on 18.02.14.
 */
public interface InCallCallback {

    /**
     * Adds the given <tt>CallStateChangeListener</tt> to the list of call state change listeners.
     *
     * @param l
     *            the <tt>CallStateChangeListener</tt> to add
     */
    void addStateChangeListener(TiviPhoneService.ServiceStateChangeListener l);

    /**
     * Removes the given <tt>CallStateChangeListener</tt> from the list of call state change listeners.
     *
     * @param l
     *            the <tt>CallStateChangeListener</tt> to remove
     */
    void removeStateChangeListener(TiviPhoneService.ServiceStateChangeListener l);

    /**
     * Adds the given <tt>DeviceStateChangeListener</tt> to the list of device state change listeners.
     *
     * @param l
     *            the <tt>DeviceStateChangeListener</tt> to add
     */
    void addDeviceChangeListener(TiviPhoneService.DeviceStateChangeListener l);

    /**
     * Removes the given <tt>DeviceStateChangeListener</tt> from the list of device state change listeners.
     *
     * @param l
     *            the <tt>DeviceStateChangeListener</tt> to remove
     */
    void removeDeviceChangeListener(TiviPhoneService.DeviceStateChangeListener l);

    /**
     * Fragments call this if user answers a call.
     */
    void answerCallCb();

    /**
     * Show dialer to add another call.
     *
     * @param call the currently active call.
     */
    void addCallCb(CallState call);

    /**
     * Fragments call this if user ends a call.
     *
     * @param call the call to stop.
     */
    void endCallCb(CallState call);

    /**
     * Handle the SAS verification dialog.
     *
     * @param Sas the SAS string
     * @param callId The call id of the call state
     */
    void verifySasCb(String Sas, int callId);

    /**
     * The user starts a video call.
     */
    void activateVideoCb();

    /**
     * Remove video fragment after user stopped a video call.
     */
    void removeVideoCb();

    /**
     * Fragments call this if they need to update the proximity wake lock.
     *
     * @param onOrOff tells to acquire or release the wake lock.
     */
    void updateProximityCb(boolean onOrOff);

    /**
     * The call manager changed the active (selected) call.
     *
     * If a call terminates the call manager selects a new active call. If userAction is
     * true the function terminates the old call after it set the active call to the new
     * call.
     *
     * @param oldCall the current active call
     * @param newCall the new call that becomes active. If this is null then the functions
     *                only terminates old call in case userAction is true.
     * @param endOldCall terminate the old call also
     */
    void activeCallChangedCb(CallState oldCall, CallState newCall, boolean endOldCall);

    /**
     * Hide the call manager.
     */
    void hideCallManagerCb();

    /**
     * Fragments call this to set the global microphone mute status.
     *
     * @param onOrOff mute on or off.
     */
    void setMuteStatusCb(boolean onOrOff);

    /**
     * Get the global microphone mute status.
     *
     * @return true if microphone is muted, false otherwise.
     */
    boolean getMuteStateCb();

    /**
     * Get the phone service
     */
    TiviPhoneService getPhoneService();

    /**
     * Show an incoming call notification.
     *
     * @param call the incoming call.
     */
    void setActiveCallNotificationCb(CallState call);
}
