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
package com.silentcircle.messaging.model.event;

import com.silentcircle.messaging.util.IOUtils;

/**
 * Event type for various information events.
 */
public class InfoEvent extends Event {

    public static final int TAG_NOT_SET = -1;
    // group chat info events
    public static final int INFO_INVITE_USER = 1000;
    public static final int INFO_INVITE_USER_FAILED = 1010;
    public static final int INFO_RESPONSE_HELLO = 1020;
    public static final int INFO_INVITE_RESPONSE_ACCEPTED = 1030;
    public static final int INFO_INVITE_RESPONSE_DECLINED = 1040;
    public static final int INFO_USER_LEFT = 1050;
    public static final int INFO_INVITE_RESPONSE_SELF_ACCEPTED = 1060;
    public static final int INFO_INVITE_RESPONSE_SELF_DECLINED = 1070;
    public static final int INFO_NEW_BURN = 1080;
    public static final int INFO_NEW_GROUP_NAME = 1090;
    public static final int INFO_NEW_GROUP = 2000;
    public static final int INFO_NEW_AVATAR = 2010;
    public static final int INFO_AVATAR_REMOVED = 2020;

    public static final int INFO_DEVICE_ADDED = 3000;
    public static final int INFO_DEVICE_REMOVED = 3050;
    public static final int INFO_NO_DEVICES_FOR_USER = 3100;

    private int mTag;

    // can be json
    private byte[] mDetails;

    // default text is un-internationalizable plaintext

    public InfoEvent() {
        mTag = TAG_NOT_SET;
    }

    public int getTag() {
        return mTag;
    }

    public void setTag(int tag) {
        mTag = tag;
    }

    public byte[] getDetailsAsByteArray() {
        return mDetails;
    }

    public String getDetails() {
        return toString(getDetailsAsByteArray());
    }

    public void setDetails(byte[] details) {
        mDetails = details;
    }

    public void setDetails(CharSequence details) {
        setDetails(IOUtils.toByteArray(details));
    }
}
