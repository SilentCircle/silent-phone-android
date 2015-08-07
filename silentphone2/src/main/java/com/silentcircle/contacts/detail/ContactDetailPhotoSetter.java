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
 * Copyright (C) 2012 The Android Open Source Project
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

package com.silentcircle.contacts.detail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import com.silentcircle.contacts.ContactPhotoManager;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.model.RawContactDeltaList;
import com.silentcircle.contacts.utils.ImageViewDrawableSetter;
// import com.android.contacts.activities.PhotoSelectionActivity;


/**
 * Extends superclass with methods specifically for setting the contact-detail
 * photo.
 */
public class ContactDetailPhotoSetter extends ImageViewDrawableSetter {
    public OnClickListener setupContactPhotoForClick(Context context, Contact contactData, ImageView photoView,
            boolean expandPhotoOnClick) {

        Bitmap bitmap = setupContactPhoto(contactData, photoView);
        return setupClickListener(context, contactData, bitmap, expandPhotoOnClick);
    }

    private static final class PhotoClickListener implements OnClickListener {

        private final Context mContext;
        private final Contact mContactData;
        private final Bitmap mPhotoBitmap;
        private final byte[] mPhotoBytes;
        private final boolean mExpandPhotoOnClick;

        public PhotoClickListener(Context context, Contact contactData, Bitmap photoBitmap,
                byte[] photoBytes, boolean expandPhotoOnClick) {

            mContext = context;
            mContactData = contactData;
            mPhotoBitmap = photoBitmap;
            mPhotoBytes = photoBytes;
            mExpandPhotoOnClick = expandPhotoOnClick;
        }

        @Override
        public void onClick(View v) {
            // Assemble the intent.
            RawContactDeltaList delta = mContactData.createRawContactDeltaList();

            // Find location and bounds of target view, adjusting based on the
            // assumed local density.
            final float appScale = 1.0f;  // mContext.getResources().getCompatibilityInfo().applicationScale;
            final int[] pos = new int[2];
            v.getLocationOnScreen(pos);

            // rect is the bounds (in pixels) of the photo view in screen coordinates
            final Rect rect = new Rect();
            rect.left = (int) (pos[0] * appScale + 0.5f);
            rect.top = (int) (pos[1] * appScale + 0.5f);
            rect.right = (int) ((pos[0] + v.getWidth()) * appScale + 0.5f);
            rect.bottom = (int) ((pos[1] + v.getHeight()) * appScale + 0.5f);

            Uri photoUri = null;
            if (mContactData.getPhotoUri() != null) {
                photoUri = Uri.parse(mContactData.getPhotoUri());
            }
            Log.d("ContactDetPhotoSetter", "*** onClick - sent intent to photoselctor");
// TODO           Intent photoSelectionIntent = PhotoSelectionActivity.buildIntent(mContext,
//                    photoUri, mPhotoBitmap, mPhotoBytes, rect, delta, mContactData.isUserProfile(),
//                    mContactData.isDirectoryEntry(), mExpandPhotoOnClick);
            // Cache the bitmap directly, so the activity can pull it from the
            // photo manager.
            if (mPhotoBitmap != null) {
                ContactPhotoManager.getInstance(mContext).cacheBitmap(photoUri, mPhotoBitmap, mPhotoBytes);
            }
// TODO            mContext.startActivity(photoSelectionIntent);
        }
    }

    private OnClickListener setupClickListener(Context context, Contact contactData, Bitmap bitmap, boolean expandPhotoOnClick) {
        final ImageView target = getTarget();
        if (target == null) 
            return null;

        return new PhotoClickListener(context, contactData, bitmap, getCompressedImage(), expandPhotoOnClick);
    }
}
