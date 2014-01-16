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

package com.silentcircle.silentphone.utils;

public class CTCalls {

    public static final int MAX_GUI_CALLS = 12;
    private CTCall[] calls = null;

    public CTCall selectedCall = null;

    public static final int eConfCall = 0;
    public static final int ePrivateCall = 1;
    public static final int eStartupCall = 2;

    private int iCurrentCallID;

    public CTCalls() {
        calls = new CTCall[MAX_GUI_CALLS];
        for (int i = 0; i < MAX_GUI_CALLS; i++)
            calls[i] = new CTCall();
    }

    void lock() {
    }

    void unLock() {
    }

    public void setCurCall(CTCall c) {
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
     * @param iType the call type
     * @return true if the call matches the conditions
     */
    public static boolean isCallType(CTCall c, int iType) {
        if (c == null)
            return false;
        if (!c.iInUse || c.iRecentsUpdated || c.uiRelAt != 0)
            return false;
        if (iType == eConfCall && c.iEnded == 0 && c.iActive && c.iIsInConferece)
            return true;
        if (iType == ePrivateCall && c.iEnded == 0 && c.iActive && !c.iIsInConferece)
            return true;
        return iType == eStartupCall && c.iEnded == 0 && !c.iActive;
    }

    public CTCall getCall(int ofs) {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (isCallType(calls[i], eConfCall) || isCallType(calls[i], ePrivateCall)) {
                if (n == ofs)
                    return calls[i];
                n++;
            }
        }
        return null;
    }

    public CTCall getCall(int iType, int ofs) {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (isCallType(calls[i], iType)) {
                if (n == ofs)
                    return calls[i];
                n++;
            }
        }
        return null;
    }

    /**
     * Count active calls of a give call type.
     * 
     * @param iType the call type
     * @return number of calls
     */
    public int getCallCnt(int iType) {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (isCallType(calls[i], iType))
                n++;
        }
        return n;
    }

    /**
     * Count currently active calls.
     * 
     * This method resets the object variable <code>selectedCall</code>to <code>null</code> if
     * no calls are active (count is zero).
     * 
     * @return number of active calls
     */
    public int getCallCnt() {
        int n = 0;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && calls[i].iEnded == 0 && calls[i].uiRelAt == 0)
                n++;
        }
        if (n == 0)
            selectedCall = null;
        // if(n && !curCall)curCall=getLastCall();
        return n;
    }

    public CTCall getLastCall() {

        CTCall c = null;
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && calls[i].iEnded == 0 && calls[i].uiRelAt == 0) {
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
    public void onUpdateRecents(CTCall c) {
        if (c == null || c.uiRelAt != 0)
            return;
        c.uiRelAt = System.currentTimeMillis() + 5000;

    }

    public CTCall getEmptyCall(boolean iIsMainThread) {

        lock();

        long ui = System.currentTimeMillis();

        // if(iIsMainThread){
        for (int i = 0; i < MAX_GUI_CALLS; i++) {
            int d = ((int) calls[i].uiRelAt - (int) ui);// loops ui>0xffff fffa
            if (d < 0)
                d = -d;
            if (calls[i].iInUse && calls[i].uiRelAt != 0 && d > 10000) {
                if (iIsMainThread)
                    calls[i].reset();
                calls[i].iInUse = false;
                calls[i].uiRelAt = 0;
                // TODO rel img
            }
        }

        // freemem_to_log();
        if (iCurrentCallID >= MAX_GUI_CALLS)
            iCurrentCallID = MAX_GUI_CALLS;

        for (int i = iCurrentCallID; i < MAX_GUI_CALLS; i++) {
            if (!calls[i].iInUse) {
                calls[i].reset();
                calls[i].iInUse = true;
                iCurrentCallID = i + 1;
                unLock();
                return calls[i];
            }

        }
        for (int i = 0; i < iCurrentCallID; i++) {
            if (!calls[i].iInUse) {
                calls[i].reset();
                calls[i].iInUse = true;
                iCurrentCallID = i + 1;
                unLock();
                return calls[i];
            }
        }
        for (int i = iCurrentCallID; i < MAX_GUI_CALLS; i++) {
            if (!calls[i].iInUse) {
                calls[i].reset();
                calls[i].iInUse = true;
                iCurrentCallID = i + 1;
                unLock();
                return calls[i];
            }

        }
        unLock();
        return null;
    }

    public CTCall findCallById(int iCallId) {
        // curCall->reset();
        if (iCallId == 0)
            return null;

        int i;
        for (i = iCurrentCallID; i < MAX_GUI_CALLS; i++) {
            if (calls[i].iInUse && iCallId == calls[i].iCallId && calls[i].iEnded == 0) {
                // -- NSLog(@"found call [Aidx=%d]",i);
                return calls[i];
            }
        }
        for (i = 0; i < iCurrentCallID; i++) {
            if (calls[i].iInUse && iCallId == calls[i].iCallId && calls[i].iEnded == 0) {
                // -- NSLog(@"found call [Bidx=%d]",i);
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

}
