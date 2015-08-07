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


package com.silentcircle.contacts.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utilities related to loading/saving contact photos.
 *
 */
public class ContactPhotoUtils {
    private static final String TAG = "ContactPhotoUtils";

    private static final String PHOTO_DATE_FORMAT = "'IMG'_yyyyMMdd_HHmmss";
    private static final String NEW_PHOTO_DIR_PATH = Environment.getExternalStorageDirectory() + "/DCIM/Camera";


    /**
     * Generate a new, unique file to be used as an out-of-band communication
     * channel, since hi-res Bitmaps are too big to serialize into a Bundle.
     * This file will be passed to other activities (such as the gallery/camera/cropper/etc.),
     * and read by us once they are finished writing it.
     */
    public static File generateTempPhotoFile(Context context) {
        return new File(pathForCroppedPhoto(context, generateTempPhotoFileName()));
    }

    public static String pathForCroppedPhoto(Context context, String fileName) {
        final File dir = new File(context.getExternalCacheDir() + "/tmp");
        dir.mkdirs();
        final File f = new File(dir, fileName);
        return f.getAbsolutePath();
    }

    public static String pathForNewCameraPhoto(String fileName) {
        final File dir = new File(NEW_PHOTO_DIR_PATH);
        dir.mkdirs();
        final File f = new File(dir, fileName);
        return f.getAbsolutePath();
    }

    public static String generateTempPhotoFileName() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat(PHOTO_DATE_FORMAT, Locale.US);
        return "ContactPhoto-" + dateFormat.format(date) + ".jpg";
    }

    /**
     * Creates a byte[] containing the PNG-compressed bitmap, or null if
     * something goes wrong.
     */
    public static byte[] compressBitmap(Bitmap bitmap) {
        final int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        final ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, "Unable to serialize photo: " + e.toString());
            return null;
        }
    }

    /**
     * Adds common extras to gallery intents.
     *
     * @param intent The intent to add extras to.
     * @param croppedPhotoUri The uri of the file to save the image to.
     * @param photoSize The size of the photo to scale to.
     */
    public static void addGalleryIntentExtras(Intent intent, Uri croppedPhotoUri, int photoSize) {
        intent.putExtra("crop", "true");
        intent.putExtra("scale", true);
        intent.putExtra("scaleUpIfNeeded", true);
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", photoSize);
        intent.putExtra("outputY", photoSize);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, croppedPhotoUri);
    }
}


