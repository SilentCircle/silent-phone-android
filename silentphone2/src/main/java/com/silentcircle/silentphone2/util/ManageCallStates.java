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

package com.silentcircle.silentphone2.util;

import android.text.TextUtils;

public class ManageCallStates {

    public static final int MAX_GUI_CALLS = 12;
    private CallState[] calls = null;

    public CallState selectedCall = null;

    public static final int eConfCall = 0;
    public static final int ePrivateCall = 1;
    public static final int eStartupCall = 2;

    private int iCurrentCallID;

    public ManageCallStates() {
        calls = new CallState[MAX_GUI_CALLS];
        for (int i = 0; i < MAX_GUI_CALLS; i++)
            calls[i] = new CallState();
    }

    public void setCurCall(CallState c) {
        selectedCall = c;
    }

    /**
     * Check if a call is of a specific type and still active.
     * 
     * Checks the call type and checks if the call is active and if it's type matches
     * its' state: a conference call must be in a conference, a private call must not
     * be in a conference.
     * 
     * @param c the call to check
     * @param type the call type
     * @return true if the call matches the conditions
     */
    public static boolean isCallType(CallState c, int type) {
        if (c == null)
            return false;
        if (!c.iInUse || c.iRecentsUpdated || c.callReleasedAt != 0 || c.callEnded)
            return false;

        switch (type) {
            case eConfCall:
                return c.iActive && c.isInConference;
            case ePrivateCall:
                return c.iActive && !c.isInConference;
            case eStartupCall:
                return !c.iActive;
        }
        return false;
    }

    /**
     * Lookup an valid call at a given index.
     *
     * Valid means the call state holds an active call, a conference call, or a startup call.
     *
     * @param ofs index.
     * @return call state or {@code null} if nothing found.
     */
    public CallState getCall(int ofs) {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (isCallType(calls[i], eConfCall) || isCallType(calls[i], ePrivateCall) || isCallType(calls[i], eStartupCall)) {
                if (n == ofs)
                    return calls[i];
                n++;
            }
        }
        return null;
    }

    /**
     * Lookup an active call of a specific call type at a given index.
     *
     * @param ofs index.
     * @return call state or {@code null} if nothing found.
     */
    public CallState getCall(int type, int ofs) {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (isCallType(calls[i], type)) {
                if (n == ofs)
                    return calls[i];
                n++;
            }
        }
        return null;
    }

    /**
     * Count active calls of a given call type.
     * 
     * @param type the call type
     * @return number of calls
     */
    public int getCallCnt(int type) {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (isCallType(calls[i], type))
                n++;
        }
        return n;
    }

    /**
     * Count currently active calls and clear selectCall variable if no more call.
     * 
     * This method resets the object variable {@link #selectedCall} to {@code null} if
     * no calls are active (count is zero). Only the monitoring thread of InCallActivity
     * should use this function. It handles call termination correctly.
     * 
     * @return number of active calls
     */
    public int getCallCountWithClear() {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && !calls[i].callEnded && calls[i].callReleasedAt == 0)
                n++;
        }
        if (n == 0)
            selectedCall = null;
        return n;
    }

    /**
     * Count currently active calls.
     *
     * This method resets the object variable {@link #selectedCall} to {@code null} if
     * no calls are active (count is zero).
     *
     * @return number of active calls
     */
    public int getCallCount() {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && !calls[i].callEnded && calls[i].callReleasedAt == 0)
                n++;
        }
        return n;
    }

    /**
     * Lookup an active call.
     *
     * @return an active call or {@code null} if none found.
     */
    public CallState getLastCall() {

        CallState c = null;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && !calls[i].callEnded && calls[i].callReleasedAt == 0) {
                c = calls[i];
                break;
            }
        }
        return c;
    }

    /**
     * Set time of release for a call.
     * 
     * @param c the call info object.
     */
    public void onUpdateRecents(CallState c) {
        if (c == null || c.callReleasedAt != 0)
            return;
        c.callReleasedAt = System.currentTimeMillis() + 5000;

    }

    public CallState getEmptyCall() {

        long ui = System.currentTimeMillis();

        // This loop releases call state object after their associated calls were released
        // several seconds ago. Thus the call state objects are still valid during this
        // time.
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            int d = ((int) calls[i].callReleasedAt - (int) ui);
            if (d < 0)
                d = -d;
            if (calls[i].iInUse && calls[i].callReleasedAt != 0 && d > 10000) {
                calls[i].iInUse = false;
                calls[i].callReleasedAt = 0;
            }
        }

        // Lookup for a free call state object, start where the last search
        // stopped. Wrap if we reach the maximum index and start again from
        // index 0.
        if (iCurrentCallID >= MAX_GUI_CALLS)
            iCurrentCallID = MAX_GUI_CALLS;

        for (int i = iCurrentCallID; i < MAX_GUI_CALLS; i++) {
            if (!calls[i].iInUse) {
                calls[i].reset();
                calls[i].iInUse = true;
                iCurrentCallID = i + 1;
                return calls[i];
            }

        }
        for (int i = 0; i < iCurrentCallID; i++) {
            if (!calls[i].iInUse) {
                calls[i].reset();
                calls[i].iInUse = true;
                iCurrentCallID = i + 1;
                return calls[i];
            }
        }
        for (int i = iCurrentCallID; i < MAX_GUI_CALLS; i++) {
            if (!calls[i].iInUse) {
                calls[i].reset();
                calls[i].iInUse = true;
                iCurrentCallID = i + 1;
                return calls[i];
            }

        }
        return null;
    }

    public CallState findCallById(int iCallId) {
        // curCall->reset();
        if (iCallId == 0)
            return null;

        int i;
        for (i = iCurrentCallID; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && iCallId == calls[i].iCallId && !calls[i].callEnded) {
                return calls[i];
            }
        }
        for (i = 0; i < iCurrentCallID; i++) {
            if (calls[i].iInUse && iCallId == calls[i].iCallId && !calls[i].callEnded) {
                return calls[i];
            }
        }
        for (i = 0; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && iCallId == calls[i].iCallId) {
                return calls[i];
            }
        }
        return null;
    }

    public CallState findCallByNumberAndNoCallID(String number) {

        CallState call = null;
        number = Utilities.removeUriPartsSelective(number);

        if (TextUtils.isEmpty(number))
            return null;

        int maxMatched = 0;
        for (int i=0; i < MAX_GUI_CALLS; i++){
            if (calls[i].iInUse && calls[i].bufDialed.getLen() > 0 && calls[i].iCallId == 0 && !calls[i].callEnded) {
                final int cm = charsMatch(calls[i].bufDialed.toString(), number);
                if(call == null || cm > maxMatched) {
                    call = calls[i];
                    maxMatched = cm;
                }
            }
        }
        return call;
    }

    private static int charsMatch(final String a, final String b){
        if (TextUtils.isEmpty(b))
            return 0;

        if (a.equals(b))
            return Integer.MAX_VALUE;

        final char aArray[] = a.toCharArray();
        final char bArray[] = b.toCharArray();
        int n = 0;

        for(int i = 0; i < aArray.length; i++) {
            if (i >= bArray.length)
                return n;

            if (aArray[i] == bArray[i]) {
                n++;
            }
            else {
                return n;
            }
        }
        return n;
    }
}
