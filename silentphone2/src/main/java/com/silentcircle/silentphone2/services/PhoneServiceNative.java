/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone2.services;

import android.app.Service;

import java.util.ArrayList;

/**
 * This class defines the native functions, codes, and callback functions of the native phone service.
 *
 * The actual phone service must extend this class and implement the abstract functions.
 *
 * Created by werner on 14.03.14.
 */
// @SuppressLint("unused")
@SuppressWarnings({"unused", "JniMissingFunction"})
public abstract class PhoneServiceNative extends Service  {

    /**
     * Bit-mask values that define which options are compiled into native phone service.
     *
     * {@link #getOptionFlags()} returns an integer that's a combination of
     * these values. Make sure these values can be combined as a bit-mask and do
     * not use the sign bit.
     */
    protected static final int ZID_READ_OPTION =           1;

    /**
     * Return values of {@link #readAllZid(java.util.ArrayList)} function.
     */
    protected static final int ZID_READ_NO_LIST =         -1;    // Java part did not provide a array list
    protected static final int ZID_READ_OPEN_FAILED =     -2;    // Could not open the cache DB
    protected static final int ZID_READ_DB_FAILURE =      -3;    // Could not read the DB or perform the SQL statement
    protected static final int ZID_READ_NO_CLASS =        -4;    // Could not find Java's ArrayList class
    protected static final int ZID_READ_NO_METHOD =       -5;    // Could not find the 'add' method of ArrayList
    protected static final int ZID_READ_NOT_IMPLEMENTED = -6;    // The function is not implemented at all (it's mask bit is also not set)

    /**
     * Return values of {@link #doInit(int)}
     */
    protected static final int NO_GLOBAL_REF =        -1;       // Could no get a global ref to this object
    protected static final int NO_CLASS =             -2;       // Could not the class of this object
    protected static final int NO_WAKE_UP_CALLBACK =  -3;       // wakeCallback method not found
    protected static final int NO_STATE_CALLBACK =    -4;       // stateChangeCallback method not found

    /* *************************************************************************************************
     * Initialization and declaration of the native interfaces to the C/C++ Tivi SIP/RTP/Codec engine
     *
     * libtivi linked with database sqlcipher
     ************************************************************************************************* */
    static {
        System.loadLibrary("sqlcipher");
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("tina");
        System.loadLibrary("aec");
        System.loadLibrary("tivi");
    }

    /*
    * The following native functions MUST NOT be static because their native implementations use the "this" object.
    */
    public native int doInit(int iDebugFlag);

    // can pass command to engine, if iEngineID==-1 || iCallID==-1 then it is not used
    public static native String getInfo(int iEngineID, int iCallID, String z);

    public static native int setKeyData(byte[] key);

    public static native int setSIPPassword(byte[] key);

    public static native int setSIPAuthName(String authName);

    public static native int initPhone(int configuration, int debugFlag, String versionName);

    public static native int savePath(String str);

    public static native int saveImei(String str);

    public static native int checkNetState(int iIsWifi, int IP, int iIsIpValid);

    public static native int doCmd(String z);

    public static native int getPhoneState();// deprecated, use getInfo

    public static native void nv21ToRGB32(byte[] b, int[] idata, short[] sdata, int w, int h, int angle);

    public static native int getVFrame(int iPrevID, int[] idata, int[] sxy);

    public static native int readAllZid(ArrayList<String> zidList);

    public static native int getOptionFlags();

    public static native int[] getZrtpCounters(int iCallID);

    public static native void setPushToken(String gcmRegID);

    //DebugLogging
    public static native void initLoggingStaticVariables();
    public static native void scLog(String logEntry);
    public static native void setLogFileName(String logFileName);
    public static native String decryptLogs(String[] logFileNames, String logBaseDir);
    public static native void setLoggingEnabled(int mIsDebugLoggingEnabled);

    // Callback from native code to monitor if SIP stack requires a wake lock - currently not used
    abstract void wakeCallback(int iLock);

    /**
     * Callback from C/C++ code on status changes.
     *
     * @param iEngID  the Tivi engine id
     * @param iCallID the internal call id
     * @param msgId   the new status message/id
     * @param str     an optional string for the new status
     */
    abstract void stateChangeCallback(int iEngID, int iCallID, int msgId, String str);
}
