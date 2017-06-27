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
package com.silentcircle.common.util;

import android.content.Context;

import com.silentcircle.contacts.calllognew.ContactInfo;

public interface CachedNumberLookupService {

    interface CachedContactInfo {
        ContactInfo getContactInfo();

        void setDirectorySource(String name, long directoryId);
        void setExtendedSource(String name, long directoryId);
        void setLookupKey(String lookupKey);
    }

    CachedContactInfo buildCachedContactInfo(ContactInfo info);

    /**
     * Perform a lookup using the cached number lookup service to return contact
     * information stored in the cache that corresponds to the given number.
     *
     * @param context Valid context
     * @param number Phone number to lookup the cache for
     * @return A {@link com.silentcircle.common.util.CachedNumberLookupService.CachedContactInfo} containing the contact information if the phone
     * number is found in the cache, {@link ContactInfo#EMPTY} if the phone number was
     * not found in the cache, and null if there was an error when querying the cache.
     */
    CachedContactInfo lookupCachedContactFromNumber(Context context, String number);

    void addContact(Context context, CachedContactInfo info);

    boolean isCacheUri(String uri);

    boolean isBusiness(int sourceType);
    boolean canReportAsInvalid(int sourceType, String objectId);

    boolean addPhoto(Context context, String number, byte[] photo);

    /**
     * Remove all cached phone number entries from the cache, regardless of how old they
     * are.
     *
     * @param context Valid context
     */
    void clearAllCacheEntries(Context context);
}
