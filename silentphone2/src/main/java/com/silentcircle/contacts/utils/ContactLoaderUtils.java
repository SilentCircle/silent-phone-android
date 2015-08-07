/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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

package com.silentcircle.contacts.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.net.Uri;

import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

/**
 * Utility methods for the {@link ContactLoader}.
 */
public final class ContactLoaderUtils {

    /** Static helper, not instantiable. */
    private ContactLoaderUtils() {}

    /**
     * Transforms the given Uri and returns a Lookup-Uri that represents the contact.
     * For legacy contacts, a raw-contact lookup is performed. An {@link IllegalArgumentException}
     * can be thrown if the URI is null or the authority is not recognized.
     *
     * Do not call from the UI thread.
     */
    @SuppressWarnings("deprecation")
    public static Uri ensureIsContactUri(final ContentResolver resolver, final Uri uri) throws IllegalArgumentException {

        if (uri == null) 
            throw new IllegalArgumentException("uri must not be null");

        final String authority = uri.getAuthority();

        // Current Style Uri?
        if (ScContactsContract.AUTHORITY.equals(authority)) {
            final String type = resolver.getType(uri);
//            // Contact-Uri? Good, return it
//            if (ContactsContract.Contacts.CONTENT_ITEM_TYPE.equals(type)) {
//                return uri;
//            }

            // RawContact-Uri? Transform it to ContactUri
            if (RawContacts.CONTENT_ITEM_TYPE.equals(type)) {
                final long rawContactId = ContentUris.parseId(uri);
                return ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
            }

            // Anything else? We don't know what this is
            throw new IllegalArgumentException("uri format is unknown");
        }

        throw new IllegalArgumentException("uri authority is unknown");
    }
}
