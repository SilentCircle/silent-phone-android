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
package com.silentcircle.messaging.providers;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import com.silentcircle.logs.Log;
import android.view.View;

import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.views.VCardPreview;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utilities for use with VCardProvider uri.
 */
public class VCardProviderUtils {

    private static final String TAG = VCardProviderUtils.class.getSimpleName();

    public static Bitmap getVCardPreviewForContact(final Context context, final Uri uri) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Generating vCard preview for: " + uri);

        Drawable drawable =
                ContextCompat.getDrawable(context, R.drawable.ic_contact_picture_holo_dark);
        String displayName = null;

        String lookupKey = uri.getLastPathSegment();
        ContentResolver resolver = context.getContentResolver();
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
        Uri contactUri = null;

        try {
            contactUri = ContactsContract.Contacts.lookupContact(resolver, lookupUri);
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to look up contact: " + throwable.getMessage());
        }

        if (contactUri != null) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(contactUri, null, null, null, null);
            } catch (Exception ignore) {
                // Ignore exceptions such as CursorWindowAllocationException which occurs intermittently
                // We should not expect a crash here
                Log.e(TAG, "Ignoring an exception: " + ignore +
                        ((ConfigurationUtilities.mNativeLog) ? ", getVCardPreviewForContact - uri: " + contactUri : ""));
            }
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null && !cursor.isClosed()) {
                    cursor.close();
                }
            }
            InputStream inputStream = openPhoto(context, contactUri);
            if (inputStream != null) {
                Bitmap photo = BitmapFactory.decodeStream(inputStream);
                photo.setDensity(DisplayMetrics.DENSITY_HIGH);
                drawable = new BitmapDrawable(context.getResources(), photo);
                IOUtils.close(inputStream);
            }
        }

        VCardPreview vCardPreview = new VCardPreview(context, drawable, displayName);

        return loadBitmapFromView(vCardPreview);
    }

    private static InputStream openDisplayPhoto(final Context context, final Uri contactUri) {
        Uri displayPhotoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
        try {
            AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(displayPhotoUri, "r");
            return fd.createInputStream();
        } catch (IOException e) {
            return null;
        }
    }

    private static InputStream openPhoto(final Context context, final Uri contactUri) {
        Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        Cursor cursor = context.getContentResolver().query(photoUri,
                new String[] {ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                if (data != null) {
                    return new ByteArrayInputStream(data);
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }


    private static Bitmap loadBitmapFromView(View view) {
        int specWidth = View.MeasureSpec.makeMeasureSpec(0 /* any */, View.MeasureSpec.UNSPECIFIED);
        view.measure(specWidth, specWidth);
        int questionWidth = view.getMeasuredWidth();
        int questionHeight = view.getMeasuredHeight();
        Bitmap bitmap = Bitmap.createBitmap(questionWidth, questionHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.draw(canvas);
        return bitmap;
    }
}
