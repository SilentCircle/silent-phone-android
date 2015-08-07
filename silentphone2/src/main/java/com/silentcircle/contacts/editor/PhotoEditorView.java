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
 * Copyright (C) 2009 The Android Open Source Project
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

package com.silentcircle.contacts.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.dataitem.DataKind;
import com.silentcircle.contacts.utils.ContactPhotoUtils;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;
import com.silentcircle.silentphone2.R;

/**
 * Simple editor for {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Photo}.
 */
public class PhotoEditorView extends LinearLayout implements Editor {

    private ImageView mPhotoImageView;
    private View mFrameView;

    private RawContactDelta.ValuesDelta mEntry;
    private EditorListener mListener;
    private View mTriangleAffordance;

    private boolean mHasSetPhoto = false;
    private boolean mReadOnly;

    public PhotoEditorView(Context context) {
        super(context);
    }

    public PhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mFrameView.setEnabled(enabled);
    }

    @Override
    public void editNewlyAddedField() {
        // Never called, since the user never adds a new photo-editor;
        // you can only change the picture in an existing editor.
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTriangleAffordance = findViewById(R.id.photo_triangle_affordance);
        mPhotoImageView = (ImageView) findViewById(R.id.photo);
        mFrameView = findViewById(R.id.frame);
        mFrameView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onRequest(EditorListener.REQUEST_PICK_PHOTO);
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void onFieldChanged(String column, String value) {
        throw new UnsupportedOperationException("Photos don't support direct field changes");
    }

    /** {@inheritDoc} */
    @Override
    public void setValues(DataKind kind, RawContactDelta.ValuesDelta values, RawContactDelta state, boolean readOnly, ViewIdGenerator vig) {

        mEntry = values;
        mReadOnly = readOnly;

        setId(vig.getId(state, kind, values, 0));

        if (values != null) {
            // Try decoding photo if actual entry
            final byte[] photoBytes = values.getAsByteArray(Photo.PHOTO);
            if (photoBytes != null) {
                final Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0,
                        photoBytes.length);

                mPhotoImageView.setImageBitmap(photo);
                mFrameView.setEnabled(isEnabled());
                mHasSetPhoto = true;
                mEntry.setFromTemplate(false);
            } else {
                resetDefault();
            }
        } else {
            resetDefault();
        }
    }

    /**
     * Return true if a valid {@link com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Photo} has been set.
     */
    public boolean hasSetPhoto() {
        return mHasSetPhoto;
    }

    /**
     * Assign the given {@link android.graphics.Bitmap} as the new value, updating UI and
     * readying for persisting through {@link com.silentcircle.contacts.model.RawContactDelta.ValuesDelta}.
     */
    public void setPhotoBitmap(Bitmap photo) {
        if (photo == null) {
            // Clear any existing photo and return
            mEntry.put(Photo.PHOTO, (byte[])null);
            resetDefault();
            return;
        }

        mPhotoImageView.setImageBitmap(photo);
        mFrameView.setEnabled(isEnabled());
        mHasSetPhoto = true;
        mEntry.setFromTemplate(false);

        // When the user chooses a new photo mark it as super primary
        mEntry.setSuperPrimary(true);

        // Even though high-res photos cannot be saved by passing them via
        // an EntityDeltaList (since they cause the Bundle size limit to be
        // exceeded), we still pass a low-res thumbnail. This simplifies
        // code all over the place, because we don't have to test whether
        // there is a change in EITHER the delta-list OR a changed photo...
        // this way, there is always a change in the delta-list.
        final int size = ContactsUtils.getThumbnailSize(getContext());
        final Bitmap scaled = Bitmap.createScaledBitmap(photo, size, size, false);
        final byte[] compressed = ContactPhotoUtils.compressBitmap(scaled);
        if (compressed != null) mEntry.setPhoto(compressed);
    }

    /**
     * Set the super primary bit on the photo.
     */
    public void setSuperPrimary(boolean superPrimary) {
        mEntry.put(Photo.IS_SUPER_PRIMARY, superPrimary ? 1 : 0);
    }

    protected void resetDefault() {
        // Invalid photo, show default "add photo" place-holder
        mPhotoImageView.setImageResource(R.drawable.ic_contact_picture_holo_dark);
        mFrameView.setEnabled(!mReadOnly && isEnabled());
        mHasSetPhoto = false;
        mEntry.setFromTemplate(true);
    }

    /** {@inheritDoc} */
    @Override
    public void setEditorListener(EditorListener listener) {
        mListener = listener;

        final boolean isPushable = listener != null;
        mTriangleAffordance.setVisibility(isPushable ? View.VISIBLE : View.INVISIBLE);
        mFrameView.setVisibility(isPushable ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setDeletable(boolean deletable) {
        // Photo is not deletable
    }

    @Override
    public boolean isEmpty() {
        return !mHasSetPhoto;
    }

    @Override
    public void deleteEditor() {
        // Photo is not deletable
    }

    @Override
    public void clearAllFields() {
        resetDefault();
    }
}
