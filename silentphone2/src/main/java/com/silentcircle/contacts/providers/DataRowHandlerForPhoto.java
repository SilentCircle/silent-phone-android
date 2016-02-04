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
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import com.silentcircle.contacts.providers.aggregation.SimpleRawContactAggregator;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.IOException;


/**
 * Handler for photo data rows.
 */
public class DataRowHandlerForPhoto extends DataRowHandler {

    private static final String TAG = "DataRowHandlerForPhoto";

    private final PhotoStore mPhotoStore;
    private final int mMaxDisplayPhotoDim;
    private final int mMaxThumbnailPhotoDim;

    /**
     * If this is set in the ContentValues passed in, it indicates that the caller has
     * already taken care of photo processing, and that the row should be ready for
     * insert/update.  This is used when the photo has been written directly to an
     * asset file.
     */
    public static final String SKIP_PROCESSING_KEY = "skip_processing";

    public DataRowHandlerForPhoto(Context context, ScContactsDatabaseHelper dbHelper, SimpleRawContactAggregator aggregator,
            PhotoStore photoStore, int maxDisplayPhotoDim, int maxThumbnailPhotoDim) {

        super(context, dbHelper, aggregator, Photo.CONTENT_ITEM_TYPE);
        mPhotoStore = photoStore;
        mMaxDisplayPhotoDim = maxDisplayPhotoDim;
        mMaxThumbnailPhotoDim = maxThumbnailPhotoDim;
    }

    @Override
    public long insert(SQLiteDatabase db, TransactionContext txContext, long rawContactId, ContentValues values) {

        if (values.containsKey(SKIP_PROCESSING_KEY)) {
            values.remove(SKIP_PROCESSING_KEY);
        } 
        else {
            // Pre-process the photo if one exists.
            if (!preProcessPhoto(values)) {
                return 0;
            }
        }

        long dataId = super.insert(db, txContext, rawContactId, values);
// TODO?       if (!txContext.isNewRawContact(rawContactId)) {
            mSimpleAggregator.updatePhotoId(db, rawContactId);
//        }
        return dataId;
    }

    @Override
    public boolean update(SQLiteDatabase db, TransactionContext txContext, ContentValues values,
            Cursor c, boolean callerIsSyncAdapter) {

        long rawContactId = c.getLong(DataUpdateQuery.RAW_CONTACT_ID);

        if (values.containsKey(SKIP_PROCESSING_KEY)) {
            values.remove(SKIP_PROCESSING_KEY);
        } else {
            // Pre-process the photo if one exists.
            if (!preProcessPhoto(values)) {
                return false;
            }
        }
        // Do the actual update.
        if (!super.update(db, txContext, values, c, callerIsSyncAdapter)) {
            return false;
        }
        mSimpleAggregator.updatePhotoId(db, rawContactId);
        return true;
    }

    /**
     * Pre-processes the given content values for update or insert.  If the photo column contains
     * null or an empty byte array, both that column and the photo file ID will be nulled out.
     * If a photo was specified but could not be processed, this will return false.
     * @param values The content values passed in.
     * @return Whether processing was successful - on failure, the operation should abort.
     */
    private boolean preProcessPhoto(ContentValues values) {
        if (values.containsKey(Photo.PHOTO)) {
            boolean photoExists = hasNonNullPhoto(values);
            if (photoExists) {
                if (!processPhoto(values)) {
                    // A photo was passed in, but we couldn't process it.  Update failed.
                    return false;
                }
            } else {
                // The photo key was passed in, but it was either null or an empty byte[].
                // We should set the photo and photo file ID fields to null for the update.
                values.putNull(Photo.PHOTO);
                values.putNull(Photo.PHOTO_FILE_ID);
            }
        }
        return true;
    }

    private boolean hasNonNullPhoto(ContentValues values) {
        byte[] photoBytes = values.getAsByteArray(Photo.PHOTO);
        return photoBytes != null && photoBytes.length > 0;
    }

    @Override
    public int delete(SQLiteDatabase db, TransactionContext txContext, Cursor c) {
        long rawContactId = c.getLong(DataDeleteQuery.RAW_CONTACT_ID);
        int count = super.delete(db, txContext, c);
        mSimpleAggregator.updatePhotoId(db, rawContactId);
        return count;
    }

    /**
     * Reads the photo out of the given values object and processes it, placing the processed
     * photos (a photo store file ID and a compressed thumbnail) back into the ContentValues
     * object.
     * @param values The values being inserted or updated - assumed to contain a photo BLOB.
     * @return Whether an image was successfully decoded and processed.
     */
    private boolean processPhoto(ContentValues values) {
        byte[] originalPhoto = values.getAsByteArray(Photo.PHOTO);
        if (originalPhoto != null) {
            try {
                PhotoProcessor processor = new PhotoProcessor(originalPhoto, mMaxDisplayPhotoDim, mMaxThumbnailPhotoDim);
                long photoFileId = mPhotoStore.insert(processor, true); // WD - Added 'true', Unit test: testInputStreamForPhoto
                if (photoFileId != 0) {
                    values.put(Photo.PHOTO_FILE_ID, photoFileId);
                } else {
                    values.putNull(Photo.PHOTO_FILE_ID);
                }
                values.put(Photo.PHOTO, processor.getThumbnailPhotoBytes());
                return true;
            } catch (IOException ioe) {
                Log.e(TAG, "Could not process photo for insert or update", ioe);
            }
        }
        return false;
    }
}
