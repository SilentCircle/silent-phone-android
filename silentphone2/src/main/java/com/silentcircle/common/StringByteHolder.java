/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.common;

import com.silentcircle.messaging.util.IOUtils;

/*
 * A class that holds a value that can be represented by a String or a byte array.
 *
 * It allows the value to be set either way and be retrieved by the getters. The getter makes the
 * conversion lazily if needed and caches the value.
 */
public class StringByteHolder {

    private String mStringValue = null;
    private byte[] mByteArrayValue = null;

    public void set(String value) {
        mByteArrayValue = null;
        mStringValue = value;
    }

    public void set(byte[] value) {
        mStringValue = null;
        mByteArrayValue = value;
    }

    public String getString() {
        if (mStringValue != null) {
            return mStringValue;
        }
        if (mByteArrayValue != null) {
            mStringValue = IOUtils.toString(mByteArrayValue);
            return mStringValue;
        }
        return null;
    }

    public byte[] getByteArray() {
        if (mByteArrayValue != null) {
            return mByteArrayValue;
        }
        if (mStringValue != null) {
            mByteArrayValue = IOUtils.toByteArray(mStringValue);
            return mByteArrayValue;
        }
        return null;
    }

}
