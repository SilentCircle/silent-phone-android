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
package com.silentcircle.logs;

import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

/**
 * This Custom Log is based on android.util.Log to record all logs here and save to LogDatabse.
 *
 * Created by rli on 1/6/16.
 */
public class Log {
    private static final String DEBUG = "[DEBUG]";
    private static final String INFO = "[INFO]";
    private static final String ERROR = "[ERROR]";
    private static final String VERBOSE = "[VERBOSE]";
    private static final String WARNING = "[WARN]";
    private static final String WHAT_TERRIBLE_FAILURE = "[WTF]";

    private static String mLogString;
    private static boolean mIsDebugLoggingEnabled;
    private static StringBuffer mBuffer = new StringBuffer();

    public static void setIsDebugLoggingEnabled( boolean enabled ) {
        mIsDebugLoggingEnabled = enabled;
        if(mIsDebugLoggingEnabled) {
            TiviPhoneService.setLoggingEnabled(1);
        }
        else{
            TiviPhoneService.setLoggingEnabled(0);
        }
    }

    private static String formatLog(String type, String tag, Throwable throwable, String message) {
        mBuffer.delete( 0, mBuffer.length() );

        mBuffer.append( type + tag + " " +message );
        mBuffer.append(System.getProperty("line.separator"));
        if (throwable != null) {
            mBuffer.append(throwable.getMessage())
                    .append(System.getProperty("line.separator"));
        }
        return mBuffer.toString();
    }

    private static void saveLogEntry(String type, String tag, Throwable throwable, String message ) {
        mLogString = formatLog(type, tag, throwable, message);
        TiviPhoneService.scLog(mLogString);
    }

    public static void d( String tag,  String message ) {
        if( ConfigurationUtilities.mNativeLog ) {
            android.util.Log.d(tag, message);
        }
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(DEBUG, tag, null, message);
        }
    }

    public static void d( String tag, String message, Throwable throwable ) {
        if( ConfigurationUtilities.mNativeLog ) {
            android.util.Log.d(tag, message, throwable);
        }
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(DEBUG, tag, throwable, message);
        }
    }

    public static void i( String tag,  String message ) {
        android.util.Log.i(tag, message);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(INFO, tag, null, message);
        }
    }

    public static void i( String tag, String message, Throwable throwable ) {
        android.util.Log.i(tag, message, throwable);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(INFO, tag, throwable, message);
        }
    }

    public static void w( String tag,  String message ) {
        android.util.Log.w(tag, message);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(WARNING, tag, null, message);
        }
    }

    public static void w( String tag, String message, Throwable throwable ) {
        android.util.Log.w(tag, message, throwable);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(WARNING, tag, throwable, message);
        }
    }

    public static void v( String tag,  String message ) {
        if( ConfigurationUtilities.mNativeLog ) {
            android.util.Log.v(tag, message);
        }
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(VERBOSE, tag, null, message);
        }
    }

    public static void v( String tag, String message, Throwable throwable ) {
        if( ConfigurationUtilities.mNativeLog ) {
            android.util.Log.v(tag, message, throwable);
        }
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(VERBOSE, tag, throwable, message);
        }
    }

    public static void e( String tag,  String message ) {
        android.util.Log.e(tag, message);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(ERROR, tag, null, message);
        }
    }

    public static void e( String tag, String message, Throwable throwable ) {
        android.util.Log.e(tag, message, throwable);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(ERROR, tag, throwable, message);
        }
    }

    public static void wtf( String tag,  String message ) {
        android.util.Log.wtf(tag, message);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(WHAT_TERRIBLE_FAILURE, tag, null, message);
        }
    }

    public static void wtf( String tag, String message, Throwable throwable ) {
        android.util.Log.wtf(tag, message, throwable);
        if( mIsDebugLoggingEnabled ) {
            saveLogEntry(WHAT_TERRIBLE_FAILURE, tag, throwable, message);
        }
    }

}
