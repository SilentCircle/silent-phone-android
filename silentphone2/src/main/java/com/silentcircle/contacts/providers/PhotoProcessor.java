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
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License
 */
package com.silentcircle.contacts.providers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Class that converts a bitmap (or byte array representing a bitmap) into a display
 * photo and a thumbnail photo.
 */
public final class PhotoProcessor {

    /** Compression for display photos. They are very big, so we can use a strong compression */
    private static final int COMPRESSION_DISPLAY_PHOTO = 75;

    /**
     * Compression for thumbnails that don't have a full size photo. Those can be blown up
     * full-screen, so we want to make sure we don't introduce JPEG artifacts here
     */
    private static final int COMPRESSION_THUMBNAIL_HIGH = 95;

    /** Compression for thumbnails that also have a display photo */
    private static final int COMPRESSION_THUMBNAIL_LOW = 90;

    private static final Paint WHITE_PAINT = new Paint();

    static {
        WHITE_PAINT.setColor(Color.WHITE);
    }

    private static int sMaxThumbnailDim;
    private static int sMaxDisplayPhotoDim;

    static {
        final boolean isExpensiveDevice = true;
//                MemoryUtils.getTotalMemorySize() >= PhotoSizes.LARGE_RAM_THRESHOLD;

        sMaxThumbnailDim = PhotoSizes.DEFAULT_THUMBNAIL;
                // SystemProperties.getInt(
//                PhotoSizes.SYS_PROPERTY_THUMBNAIL_SIZE, PhotoSizes.DEFAULT_THUMBNAIL);

        sMaxDisplayPhotoDim = PhotoSizes.DEFAULT_DISPLAY_PHOTO_LARGE_MEMORY;
                // SystemProperties.getInt(
//                PhotoSizes.SYS_PROPERTY_DISPLAY_PHOTO_SIZE,
//                isExpensiveDevice
//                        ? PhotoSizes.DEFAULT_DISPLAY_PHOTO_LARGE_MEMORY
//                        : PhotoSizes.DEFAULT_DISPLAY_PHOTO_MEMORY_CONSTRAINED);
    }

    /**
     * The default sizes of a thumbnail/display picture. This is used in {@link #initialize()}
     */
    private interface PhotoSizes {
        /** Size of a thumbnail */
        public static final int DEFAULT_THUMBNAIL = 96;

        /**
         * Size of a display photo on memory constrained devices (those are devices with less than
         * {@link #DEFAULT_LARGE_RAM_THRESHOLD} of reported RAM
         */
        public static final int DEFAULT_DISPLAY_PHOTO_MEMORY_CONSTRAINED = 480;

        /**
         * Size of a display photo on devices with enough ram (those are devices with at least
         * {@link #DEFAULT_LARGE_RAM_THRESHOLD} of reported RAM
         */
        public static final int DEFAULT_DISPLAY_PHOTO_LARGE_MEMORY = 720;

        /**
         * If the device has less than this amount of RAM, it is considered RAM constrained for
         * photos
         */
        public static final int LARGE_RAM_THRESHOLD = 640 * 1024 * 1024;

        /** If present, overrides the size given in {@link #DEFAULT_THUMBNAIL} */
        public static final String SYS_PROPERTY_THUMBNAIL_SIZE = "contacts.thumbnail_size";

        /** If present, overrides the size determined for the display photo */
        public static final String SYS_PROPERTY_DISPLAY_PHOTO_SIZE = "contacts.display_photo_size";
    }

    private final int mMaxDisplayPhotoDim;
    private final int mMaxThumbnailPhotoDim;
    private final boolean mForceCropToSquare;
    private final Bitmap mOriginal;
    private Bitmap mDisplayPhoto;
    private Bitmap mThumbnailPhoto;

    /**
     * Initializes a photo processor for the given bitmap.
     * @param original The bitmap to process.
     * @param maxDisplayPhotoDim The maximum height and width for the display photo.
     * @param maxThumbnailPhotoDim The maximum height and width for the thumbnail photo.
     * @throws java.io.IOException If bitmap decoding or scaling fails.
     */
    public PhotoProcessor(Bitmap original, int maxDisplayPhotoDim, int maxThumbnailPhotoDim) throws IOException {
        this(original, maxDisplayPhotoDim, maxThumbnailPhotoDim, false);
    }

    /**
     * Initializes a photo processor for the given bitmap.
     * @param originalBytes A byte array to decode into a bitmap to process.
     * @param maxDisplayPhotoDim The maximum height and width for the display photo.
     * @param maxThumbnailPhotoDim The maximum height and width for the thumbnail photo.
     * @throws java.io.IOException If bitmap decoding or scaling fails.
     */
    public PhotoProcessor(byte[] originalBytes, int maxDisplayPhotoDim, int maxThumbnailPhotoDim) throws IOException {
        this(BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.length), maxDisplayPhotoDim,
                maxThumbnailPhotoDim, false);
    }

    /**
     * Initializes a photo processor for the given bitmap.
     * @param original The bitmap to process.
     * @param maxDisplayPhotoDim The maximum height and width for the display photo.
     * @param maxThumbnailPhotoDim The maximum height and width for the thumbnail photo.
     * @param forceCropToSquare Whether to force the processed images to be square.  If the source
     *     photo is not square, this will crop to the square at the center of the image's rectangle.
     *     If this is not set to true, the image will simply be downscaled to fit in the given
     *     dimensions, retaining its original aspect ratio.
     * @throws java.io.IOException If bitmap decoding or scaling fails.
     */
    public PhotoProcessor(Bitmap original, int maxDisplayPhotoDim, int maxThumbnailPhotoDim, boolean forceCropToSquare)
            throws IOException {
        mOriginal = original;
        mMaxDisplayPhotoDim = maxDisplayPhotoDim;
        mMaxThumbnailPhotoDim = maxThumbnailPhotoDim;
        mForceCropToSquare = forceCropToSquare;
        process();
    }

    /**
     * Initializes a photo processor for the given bitmap.
     * @param originalBytes A byte array to decode into a bitmap to process.
     * @param maxDisplayPhotoDim The maximum height and width for the display photo.
     * @param maxThumbnailPhotoDim The maximum height and width for the thumbnail photo.
     * @param forceCropToSquare Whether to force the processed images to be square.  If the source
     *     photo is not square, this will crop to the square at the center of the image's rectangle.
     *     If this is not set to true, the image will simply be downscaled to fit in the given
     *     dimensions, retaining its original aspect ratio.
     * @throws java.io.IOException If bitmap decoding or scaling fails.
     */
    public PhotoProcessor(byte[] originalBytes, int maxDisplayPhotoDim, int maxThumbnailPhotoDim,
            boolean forceCropToSquare) throws IOException {
        this(BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.length),
                maxDisplayPhotoDim, maxThumbnailPhotoDim, forceCropToSquare);
    }

    /**
     * Processes the original image, producing a scaled-down display photo and thumbnail photo.
     * @throws java.io.IOException If bitmap decoding or scaling fails.
     */
    private void process() throws IOException {
        if (mOriginal == null) {
            throw new IOException("Invalid image file");
        }
        mDisplayPhoto = getNormalizedBitmap(mOriginal, mMaxDisplayPhotoDim, mForceCropToSquare);
        mThumbnailPhoto = getNormalizedBitmap(mOriginal,mMaxThumbnailPhotoDim, mForceCropToSquare);
    }

    /**
     * Scales down the original bitmap to fit within the given maximum width and height.
     * If the bitmap already fits in those dimensions, the original bitmap will be
     * returned unmodified unless the photo processor is set up to crop it to a square.
     *
     * Also, if the image has transparency, convert it to white.
     *
     * @param original Original bitmap
     * @param maxDim Maximum width and height (in pixels) for the image.
     * @param forceCropToSquare See {@link #PhotoProcessor(android.graphics.Bitmap, int, int, boolean)}
     * @return A bitmap that fits the maximum dimensions.
     */
    @SuppressWarnings({"SuspiciousNameCombination"})
    static Bitmap getNormalizedBitmap(Bitmap original, int maxDim, boolean forceCropToSquare) {
        final boolean originalHasAlpha = original.hasAlpha();

        // All cropXxx's are in the original coordinate.
        int cropWidth = original.getWidth();
        int cropHeight = original.getHeight();
        int cropLeft = 0;
        int cropTop = 0;
        if (forceCropToSquare && cropWidth != cropHeight) {
            // Crop the image to the square at its center.
            if (cropHeight > cropWidth) {
                cropTop = (cropHeight - cropWidth) / 2;
                cropHeight = cropWidth;
            } else {
                cropLeft = (cropWidth - cropHeight) / 2;
                cropWidth = cropHeight;
            }
        }
        // Calculate the scale factor.  We don't want to scale up, so the max scale is 1f.
        final float scaleFactor = Math.min(1f, ((float) maxDim) / Math.max(cropWidth, cropHeight));

        if (scaleFactor < 1.0f || cropLeft != 0 || cropTop != 0 || originalHasAlpha) {
            final int newWidth = (int) (cropWidth * scaleFactor);
            final int newHeight = (int) (cropHeight * scaleFactor);
            final Bitmap scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            final Canvas c = new Canvas(scaledBitmap);

            if (originalHasAlpha) {
                c.drawRect(0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), WHITE_PAINT);
            }
            final Rect src = new Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight);
            final RectF dst = new RectF(0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());

            c.drawBitmap(original, src, dst, null);
            return scaledBitmap;
        } else {
            return original;
        }
    }

    /**
     * Helper method to compress the given bitmap as a JPEG and return the resulting byte array.
     */
    private byte[] getCompressedBytes(Bitmap b, int quality) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final boolean compressed = b.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        baos.flush();
        baos.close();
        byte[] result = baos.toByteArray();

        if (!compressed) {
            throw new IOException("Unable to compress image");
        }
        return result;
    }

    /**
     * Retrieves the uncompressed display photo.
     */
    public Bitmap getDisplayPhoto() {
        return mDisplayPhoto;
    }

    /**
     * Retrieves the uncompressed thumbnail photo.
     */
    public Bitmap getThumbnailPhoto() {
        return mThumbnailPhoto;
    }

    /**
     * Retrieves the compressed display photo as a byte array.
     */
    public byte[] getDisplayPhotoBytes() throws IOException {
        return getCompressedBytes(mDisplayPhoto, COMPRESSION_DISPLAY_PHOTO);
    }

    /**
     * Retrieves the compressed thumbnail photo as a byte array.
     */
    public byte[] getThumbnailPhotoBytes() throws IOException {
        // If there is a higher-resolution picture, we can assume we won't need to upscale the
        // thumbnail often, so we can compress stronger
        final boolean hasDisplayPhoto = mDisplayPhoto != null &&
                (mDisplayPhoto.getWidth() > mThumbnailPhoto.getWidth() ||
                mDisplayPhoto.getHeight() > mThumbnailPhoto.getHeight());
        return getCompressedBytes(mThumbnailPhoto,
                hasDisplayPhoto ? COMPRESSION_THUMBNAIL_LOW : COMPRESSION_THUMBNAIL_HIGH);
    }

    /**
     * Retrieves the maximum width or height (in pixels) of the display photo.
     */
    public int getMaxDisplayPhotoDim() {
        return mMaxDisplayPhotoDim;
    }

    /**
     * Retrieves the maximum width or height (in pixels) of the thumbnail.
     */
    public int getMaxThumbnailPhotoDim() {
        return mMaxThumbnailPhotoDim;
    }

    /**
     * Returns the maximum size in pixel of a thumbnail (which has a default that can be overriden
     * using a system-property)
     */
    public static int getMaxThumbnailSize() {
        return sMaxThumbnailDim;
    }

    /**
     * Returns the maximum size in pixel of a display photo (which is determined based
     * on available RAM or configured using a system-property)
     */
    public static int getMaxDisplayPhotoSize() {
        return sMaxDisplayPhotoDim;
    }
}
