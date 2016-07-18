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

package com.silentcircle.messaging.util;

import android.net.Uri;
import android.os.Build;
import android.widget.QuickContactBadge;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.contacts.ContactPhotoManagerNew;

/**
 *
 */
public class AvatarUtils {

    private AvatarUtils() {
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            QuickContactBadge quickContactView, ContactEntry contactEntry) {

        int contactType = ContactPhotoManagerNew.TYPE_DEFAULT;

        long photoId = 0;
        Uri photoUri = null;
        Uri contactUri = null;
        String displayName = null;
        String lookupKey = null;
        if (contactEntry != null) {
            photoId = contactEntry.photoId;
            photoUri = contactEntry.photoUri;
            contactUri = contactEntry.lookupUri;
            lookupKey = contactEntry.lookupKey;
            displayName = contactEntry.name;
        }

        setPhoto(contactPhotoManager, quickContactView, photoId, photoUri, contactUri, displayName,
                lookupKey, contactType);
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            QuickContactBadge quickContactView, long photoId, Uri photoUri, Uri contactUri,
            String displayName, String identifier, int contactType) {

//issue NGA-386        quickContactView.assignContactUri(contactUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            quickContactView.setOverlay(null);
        ContactPhotoManagerNew.DefaultImageRequest request =
                new ContactPhotoManagerNew.DefaultImageRequest(displayName, identifier, contactType,
                        true /* isCircular */);
        if (photoId == 0 && photoUri != null) {
            contactPhotoManager.loadDirectoryPhoto(quickContactView, photoUri,
                    false /* darkTheme */, true /* isCircular */, request);
        }
        else {
            contactPhotoManager.loadThumbnail(quickContactView, photoId, false /* darkTheme */,
                    true /* isCircular */, request);
        }
    }


}
