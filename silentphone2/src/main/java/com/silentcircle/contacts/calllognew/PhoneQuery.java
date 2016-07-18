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

import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.PhoneLookup;

/**
 * The query to look up the {@link ContactInfo} for a given number in the Call Log.
 */
final class PhoneQuery {
    public static final String[] _PROJECTION = new String[] {
            PhoneLookup._ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER,
            PhoneLookup.NORMALIZED_NUMBER,
            PhoneLookup.PHOTO_ID,
            PhoneLookup.LOOKUP_KEY,
            PhoneLookup.PHOTO_URI};


    public static final String[] _PROJECTION_SIP = new String[] {
            SipAddress._ID,
            SipAddress.CONTACT_ID,
            SipAddress.DISPLAY_NAME,
            SipAddress.TYPE,
            SipAddress.LABEL,
            SipAddress.SIP_ADDRESS,
            SipAddress.PHOTO_ID,
            SipAddress.LOOKUP_KEY,
            SipAddress.PHOTO_URI};

    public static final String[] _PROJECTION_EMAIL = new String[] {
            Email._ID,
            Email.CONTACT_ID,
            Data.DISPLAY_NAME,
            Email.TYPE,
            Email.LABEL,
            Email.ADDRESS,
            Email.PHOTO_ID,
            Email.LOOKUP_KEY,
            Email.PHOTO_URI};

    public static final int PERSON_ID = 0;
    public static final int NAME = 1;
    public static final int PHONE_TYPE = 2;
    public static final int LABEL = 3;
    public static final int MATCHED_NUMBER = 4;
    public static final int NORMALIZED_NUMBER = 5;
    public static final int PHOTO_ID = 6;
    public static final int LOOKUP_KEY = 7;
    public static final int PHOTO_URI = 8;

    public static final int SIP_PERSON_ID = 1;   // CONTACT_ID, same a PhoneLookup_ID
    public static final int SIP_NAME = 2;
    public static final int SIP_PHONE_TYPE = 3;
    public static final int SIP_LABEL = 4;
    public static final int SIP_ADDRESS = 5;
    public static final int SIP_PHOTO_ID = 6;
    public static final int SIP_LOOKUP_KEY = 7;
    public static final int SIP_PHOTO_URI = 8;

    public static final int EMAIL_PERSON_ID = 1;   // CONTACT_ID, same a PhoneLookup_ID
    public static final int EMAIL_NAME = 2;
    public static final int EMAIL_PHONE_TYPE = 3;
    public static final int EMAIL_LABEL = 4;
    public static final int EMAIL_ADDRESS = 5;
    public static final int EMAIL_PHOTO_ID = 6;
    public static final int EMAIL_LOOKUP_KEY = 7;
    public static final int EMAIL_PHOTO_URI = 8;
}
