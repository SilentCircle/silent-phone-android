/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.contacts.calllognew;

import android.database.Cursor;

import com.silentcircle.silentcontacts2.ScCallLog.ScCalls;


/**
 * The query for the call log table.
 */
public final class CallLogQuery {
    // If you alter this, you must also alter the method that inserts a fake row to the headers
    // in the CallLogQueryHandler class called createHeaderCursorFor().
    public static final String[] _PROJECTION = new String[] {
            ScCalls._ID,                       // 0
            ScCalls.NUMBER,                    // 1
            ScCalls.DATE,                      // 2
            ScCalls.DURATION,                  // 3
            ScCalls.TYPE,                      // 4
            ScCalls.COUNTRY_ISO,               // 5
            ScCalls.GEOCODED_LOCATION,         // 6
            ScCalls.CACHED_NAME,               // 7
            ScCalls.CACHED_NUMBER_TYPE,        // 8
            ScCalls.CACHED_NUMBER_LABEL,       // 9
            ScCalls.CACHED_LOOKUP_URI,         // 10
            ScCalls.CACHED_MATCHED_NUMBER,     // 11
            ScCalls.CACHED_NORMALIZED_NUMBER,  // 12
            ScCalls.CACHED_PHOTO_ID,           // 13
            ScCalls.CACHED_FORMATTED_NUMBER,   // 14
            ScCalls.IS_READ,                   // 15
    };

    public static final int ID = 0;
    public static final int NUMBER = 1;
    public static final int DATE = 2;
    public static final int DURATION = 3;
    public static final int CALL_TYPE = 4;
    public static final int COUNTRY_ISO = 5;
    public static final int GEOCODED_LOCATION = 6;
    public static final int CACHED_NAME = 7;
    public static final int CACHED_NUMBER_TYPE = 8;
    public static final int CACHED_NUMBER_LABEL = 9;
    public static final int CACHED_LOOKUP_URI = 10;
    public static final int CACHED_MATCHED_NUMBER = 11;
    public static final int CACHED_NORMALIZED_NUMBER = 12;
    public static final int CACHED_PHOTO_ID = 13;
    public static final int CACHED_FORMATTED_NUMBER = 14;
    public static final int IS_READ = 15;
    /** The indices of the synthetic "section" and relative date columns in the extended projection. */
}
