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

package com.silentcircle.contacts.detail;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.Toast;

import com.silentcircle.contacts.editor.PhotoActionPopup;
import com.silentcircle.contacts.model.AccountTypeManager;
import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.RawContactDeltaList;
import com.silentcircle.contacts.model.RawContactModifier;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.utils.ContactPhotoUtils19;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;
import com.silentcircle.silentcontacts2.ScContactsContract.DisplayPhoto;
import com.silentcircle.silentphone2.R;

import java.io.FileNotFoundException;

/**
 * Handles displaying a photo selection popup for a given photo view and dealing with the results
 * that come back.
 */
public abstract class PhotoSelectionHandler19 implements OnClickListener {

    private static final String TAG = PhotoSelectionHandler19.class.getSimpleName();

    private static final int REQUEST_CODE_CAMERA_WITH_DATA = 1001;
    private static final int REQUEST_CODE_PHOTO_PICKED_WITH_DATA = 1002;
    private static final int REQUEST_CROP_PHOTO = 1003;

    protected final Context mContext;
    private final View mPhotoView;
    private final int mPhotoMode;
    private final int mPhotoPickSize;
    private final Uri mCroppedPhotoUri;
    private final Uri mTempPhotoUri;
    private final RawContactDeltaList mState;
    private final boolean mIsDirectoryContact;
    private ListPopupWindow mPopup;
    private Fragment parentFragment;

    public PhotoSelectionHandler19(Context context, View photoView, int photoMode, boolean isDirectoryContact,
                                   RawContactDeltaList state, Fragment fragment) {

        mContext = context;
        mPhotoView = photoView;
        mPhotoMode = photoMode;
        mTempPhotoUri = ContactPhotoUtils19.generateTempImageUri(context);
        mCroppedPhotoUri = ContactPhotoUtils19.generateTempCroppedImageUri(mContext);
        mIsDirectoryContact = isDirectoryContact;
        mState = state;
        mPhotoPickSize = getPhotoPickSize();
        parentFragment = fragment;
    }

    public void destroy() {
        if (mPopup != null) {
            mPopup.dismiss();
        }
    }

    public abstract PhotoActionListener getListener();

    @Override
    public void onClick(View v) {
        final PhotoActionListener listener = getListener();
        if (listener != null) {
            if (getWritableEntityIndex() != -1) {
                mPopup = PhotoActionPopup.createPopupMenu(mContext, mPhotoView, listener, mPhotoMode);
                mPopup.setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        listener.onPhotoSelectionDismissed();
                    }
                });
                mPopup.show();
            }
        }
    }

    /**
     * Attempts to handle the given activity result.  Returns whether this handler was able to
     * process the result successfully.
     * @param requestCode The request code.
     * @param resultCode The result code.
     * @param data The intent that was returned.
     * @return Whether the handler was able to process the result.
     */
    public boolean handlePhotoActivityResult(int requestCode, int resultCode, Intent data) {
        final PhotoActionListener listener = getListener();
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                // Cropped photo was returned
                case REQUEST_CROP_PHOTO: {
                    final Uri uri;
                    if (data != null && data.getData() != null) {
                        uri = data.getData();
                    } else {
                        uri = mCroppedPhotoUri;
                    }

                    try {
                        // delete the original temporary photo if it exists
                        mContext.getContentResolver().delete(mTempPhotoUri, null, null);
                        listener.onPhotoSelected(uri);
                        return true;
                    } catch (FileNotFoundException e) {
                        return false;
                    }
                }
                // Photo was successfully taken or selected from gallery, now crop it.
                case REQUEST_CODE_PHOTO_PICKED_WITH_DATA:
                case REQUEST_CODE_CAMERA_WITH_DATA:
                    final Uri uri;
                    boolean isWritable = false;
                    if (data != null && data.getData() != null) {
                        uri = data.getData();
                    } else {
                        uri = listener.getCurrentPhotoUri();
                        isWritable = true;
                    }
                    final Uri toCrop;
                    if (isWritable) {
                        // Since this uri belongs to our file provider, we know that it is writable
                        // by us. This means that we don't have to save it into another temporary
                        // location just to be able to crop it.
                        toCrop = uri;
                    } else {
                        toCrop = mTempPhotoUri;
                        try {
                            ContactPhotoUtils19.savePhotoFromUriToUri(mContext, uri, toCrop, false);
                        } catch (SecurityException e) {
                            Log.d(TAG, "Did not have read-access to uri : " + uri);
                            return false;
                        }
                    }
                    doCropPhoto(toCrop, mCroppedPhotoUri);
                    return true;

            }
        }
        return false;
    }

    /**
     * Return the index of the first entity in the contact data that belongs to a contact-writable
     * account, or -1 if no such entity exists.
     */
    private int getWritableEntityIndex() {
        // Directory entries are non-writable.
        if (mIsDirectoryContact) return -1;
        return mState.indexOfFirstWritableRawContact(mContext);
    }

    /**
     * Return the raw-contact id of the first entity in the contact data that belongs to a
     * contact-writable account, or -1 if no such entity exists.
     */
    protected long getWritableEntityId() {
        int index = getWritableEntityIndex();
        if (index == -1) return -1;
        return mState.get(index).getValues().getId();
    }

    /**
     * Utility method to retrieve the entity delta for attaching the given bitmap to the contact.
     * This will attach the photo to the first contact-writable account that provided data to the
     * contact.  It is the caller's responsibility to apply the delta.
     * @return An entity delta list that can be applied to associate the bitmap with the contact,
     *     or null if the photo could not be parsed or none of the accounts associated with the
     *     contact are writable.
     */
    public RawContactDeltaList getDeltaForAttachingPhotoToContact() {
        // Find the first writable entity.
        int writableEntityIndex = getWritableEntityIndex();
        if (writableEntityIndex != -1) {
            // We are guaranteed to have contact data if we have a writable entity index.
            final RawContactDelta delta = mState.get(writableEntityIndex);

            // Need to find the right account so that EntityModifier knows which fields to add
            final AccountType accountType = AccountTypeManager.getInstance(mContext).getAccountType();

            final RawContactDelta.ValuesDelta child = RawContactModifier.ensureKindExists(delta, accountType, Photo.CONTENT_ITEM_TYPE);
            child.setFromTemplate(false);
            child.setSuperPrimary(true);

            return mState;
        }
        return null;
    }

    /** Used by subclasses to delegate to their enclosing Activity or Fragment. */
    protected abstract void startPhotoActivity(Intent intent, int requestCode, Uri photoUri);

    /**
     * Sends a newly acquired photo to Gallery for cropping
     */
    private void doCropPhoto(Uri inputUri, Uri outputUri) {
        try {
            // Launch gallery to crop the photo
            final Intent intent = getCropImageIntent(inputUri, outputUri);
            startPhotoActivity(intent, REQUEST_CROP_PHOTO, inputUri);
        } catch (Exception e) {
            Log.e(TAG, "Cannot crop image", e);
            Toast.makeText(mContext, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Should initiate an activity to take a photo using the camera.
     * @param photoUri The file path that will be used to store the photo.  This is generally
     *     what should be returned by
     *     {@link com.silentcircle.contacts.detail.PhotoSelectionHandler19.PhotoActionListener#getCurrentPhotoUri()}.
     */
    private void startTakePhotoActivity(Uri photoUri) {
        final Intent intent = getTakePhotoIntent(photoUri);
        startPhotoActivity(intent, REQUEST_CODE_CAMERA_WITH_DATA, photoUri);
    }

    /**
     * Should initiate an activity pick a photo from the gallery.
     * @param photoUri The temporary file that the cropped image is written to before being
     *     stored by the content-provider.
     *     {@link com.silentcircle.contacts.detail.PhotoSelectionHandler19#handlePhotoActivityResult(int, int, android.content.Intent)}.
     */
    private void startPickFromGalleryActivity(Uri photoUri) {
        final Intent intent = getPhotoPickIntent(photoUri);
        startPhotoActivity(intent, REQUEST_CODE_PHOTO_PICKED_WITH_DATA, photoUri);
    }

    private int getPhotoPickSize() {
        // Note that this URI is safe to call on the UI thread.
        Cursor c = mContext.getContentResolver().query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                new String[]{DisplayPhoto.DISPLAY_MAX_DIM}, null, null, null);
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

    /**
     * Constructs an intent for capturing a photo and storing it in a temporary output uri.
     */
    private Intent getTakePhotoIntent(Uri outputUri) {
        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE, null);
        ContactPhotoUtils19.addPhotoPickerExtras(intent, outputUri);
        return intent;
    }

    /**
     * Constructs an intent for picking a photo from Gallery, and returning the bitmap.
     */
    private Intent getPhotoPickIntent(Uri outputUri) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        ContactPhotoUtils19.addPhotoPickerExtras(intent, outputUri);
        return intent;
    }

    /**
     * Constructs an intent for image cropping.
     */
    private Intent getCropImageIntent(Uri inputUri, Uri outputUri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(inputUri, "image/*");
        ContactPhotoUtils19.addPhotoPickerExtras(intent, outputUri);
        ContactPhotoUtils19.addCropExtras(intent, mPhotoPickSize);
        return intent;
    }

    public abstract class PhotoActionListener implements PhotoActionPopup.Listener {
        @Override
        public void onUseAsPrimaryChosen() {
            // No default implementation.
        }

        @Override
        public void onRemovePictureChosen() {
            // No default implementation.
        }

        @Override
        public void onTakePhotoChosen() {
            try {
                // Launch camera to take photo for selected contact
                startTakePhotoActivity(mTempPhotoUri);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onPickFromGalleryChosen() {
            try {
                // Launch picker to choose photo for selected contact
                startPickFromGalleryActivity(mTempPhotoUri);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
            }
        }

        /**
         * Called when the user has completed selection of a photo.
         * @param uri The selected and cropped photo.
         * @throws java.io.FileNotFoundException
         */
        public abstract void onPhotoSelected(Uri uri) throws FileNotFoundException;

        /**
         * Gets the current photo file that is being interacted with.  It is the activity or
         * fragment's responsibility to maintain this in saved state, since this handler instance
         * will not survive rotation.
         */
        public abstract Uri getCurrentPhotoUri();

        /**
         * Called when the photo selection dialog is dismissed.
         */
        public abstract void onPhotoSelectionDismissed();
    }
}
