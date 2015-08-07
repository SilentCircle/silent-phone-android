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

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.util.Log;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.PhotoFilesColumns;
import com.silentcircle.contacts.providers.ScContactsDatabaseHelper.Tables;
import com.silentcircle.silentcontacts2.ScContactsContract.PhotoFiles;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Photo storage system that stores the files directly onto the hard disk
 * in the specified directory.
 */
public class PhotoStore {

    private final String TAG = PhotoStore.class.getSimpleName();

    // Directory name under the root directory for photo storage.
    private final String DIRECTORY = "photos";

    /** Map of keys to entries in the directory. */
    private final Map<Long, Entry> mEntries;

    /** Total amount of space currently used by the photo store in bytes. */
    private long mTotalSize = 0;

    /** The file path for photo storage. */
    private final File mStorePath;

    /** The database helper. */
    private final ScContactsDatabaseHelper mDatabaseHelper;

    /** The database to use for storing metadata for the photo files. */
    private SQLiteDatabase mDb;

    private final ScContactsProvider mProvider;
    /**
     * Constructs an instance of the PhotoStore under the specified directory.
     * @param rootDirectory The root directory of the storage.
     * @param databaseHelper Helper class for obtaining a database instance.
     */
    public PhotoStore(File rootDirectory, ScContactsDatabaseHelper databaseHelper, ScContactsProvider provider) {
        mStorePath = new File(rootDirectory, DIRECTORY);
        if (!mStorePath.exists()) {
            if(!mStorePath.mkdirs()) {
                throw new RuntimeException("Unable to create photo storage directory "
                        + mStorePath.getPath());
            }
        }
        mDatabaseHelper = databaseHelper;
        mProvider = provider;
        mEntries = new HashMap<>();
        initialize();
    }

    /**
     * Clears the photo storage. Deletes all files from disk.
     */
    public void clear() {
        File[] files = mStorePath.listFiles();
        if (files != null) {
            for (File file : files) {
                cleanupFile(file);
            }
        }
        if (mDb == null) {
            mDb = mDatabaseHelper.getDatabase(true);
        }
        mDb.delete(Tables.PHOTO_FILES, null, null);
        mEntries.clear();
        mTotalSize = 0;
    }

    public long getTotalSize() {
        return mTotalSize;
    }

    /**
     * Returns the entry with the specified key if it exists, null otherwise.
     */
    public Entry get(long key) {
        return mEntries.get(key);
    }

    /**
     * Initializes the PhotoStore by scanning for all files currently in the
     * specified root directory.
     */
    public final void initialize() {
        File[] files = mStorePath.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Entry entry = new Entry(file);
                putEntry(entry.id, entry);
            } catch (NumberFormatException nfe) {
                // Not a valid photo store entry - delete the file.
                cleanupFile(file);
            }
        }

        // Get a reference to the database.
        mDb = mDatabaseHelper.getDatabase(true);
    }

    /**
     * Cleans up the photo store such that only the keys in use still remain as
     * entries in the store (all other entries are deleted).
     *
     * If an entry in the keys in use does not exist in the photo store, that key
     * will be returned in the result set - the caller should take steps to clean
     * up those references, as the underlying photo entries do not exist.
     *
     * @param keysInUse The set of all keys that are in use in the photo store.
     * @return The set of the keys in use that refer to non-existent entries.
     */
    public Set<Long> cleanup(Set<Long> keysInUse) {
        Set<Long> keysToRemove = new HashSet<Long>();
        keysToRemove.addAll(mEntries.keySet());
        keysToRemove.removeAll(keysInUse);
        if (!keysToRemove.isEmpty()) {
            Log.d(TAG, "cleanup removing " + keysToRemove.size() + " entries");
            for (long key : keysToRemove) {
                remove(key);
            }
        }

        Set<Long> missingKeys = new HashSet<Long>();
        missingKeys.addAll(keysInUse);
        missingKeys.removeAll(mEntries.keySet());
        return missingKeys;
    }

    /**
     * Inserts the photo in the given photo processor into the photo store.  If the display photo
     * is already thumbnail-sized or smaller, this will do nothing (and will return 0).
     * @param photoProcessor A photo processor containing the photo data to insert.
     * @return The photo file ID associated with the file, or 0 if the file could not be created or
     *     is thumbnail-sized or smaller.
     */
    public long insert(PhotoProcessor photoProcessor) {
        return insert(photoProcessor, false);
    }

    /**
     * Inserts the photo in the given photo processor into the photo store.  If the display photo
     * is already thumbnail-sized or smaller, this will do nothing (and will return 0) unless
     * allowSmallImageStorage is specified.
     * @param photoProcessor A photo processor containing the photo data to insert.
     * @param allowSmallImageStorage Whether thumbnail-sized or smaller photos should still be
     *     stored in the file store.
     * @return The photo file ID associated with the file, or 0 if the file could not be created or
     *     is thumbnail-sized or smaller and allowSmallImageStorage is false.
     */
    public long insert(PhotoProcessor photoProcessor, boolean allowSmallImageStorage) {
        Bitmap displayPhoto = photoProcessor.getDisplayPhoto();
        int width = displayPhoto.getWidth();
        int height = displayPhoto.getHeight();
        int thumbnailDim = photoProcessor.getMaxThumbnailPhotoDim();

        if (allowSmallImageStorage || width > thumbnailDim || height > thumbnailDim) {
            // Write the photo to a temp file, create the DB record for tracking it, and rename the
            // temp file to match.
            File file = null;
            try {
                // Write the display photo to a temp file.
                byte[] photoBytes = photoProcessor.getDisplayPhotoBytes();
                file = File.createTempFile("img", null, mStorePath);
                CipherOutputStream cos = getCipherOutputStream(file);
                if (cos != null) {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Encrypting display photo file");
                    cos.write(photoBytes);
                    cos.close();
                }
                else {
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(photoBytes);
                    fos.close();
                }

                // Create the DB entry.
                ContentValues values = new ContentValues();
                values.put(PhotoFiles.HEIGHT, height);
                values.put(PhotoFiles.WIDTH, width);
                values.put(PhotoFiles.FILESIZE, photoBytes.length);
                long id = mDb.insert(Tables.PHOTO_FILES, null, values);
                if (id != 0) {
                    // Rename the temp file.
                    File target = getFileForPhotoFileId(id);
                    if (file.renameTo(target)) {
                        Entry entry = new Entry(target);
                        putEntry(entry.id, entry);
                        return id;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                // Write failed - will delete the file below.
            }

            // If anything went wrong, clean up the file before returning.
            if (file != null) {
                cleanupFile(file);
            }
        }
        return 0;
    }

    private CipherOutputStream getCipherOutputStream(File file) {

        byte[] keyData = mProvider.getKeyData(mProvider.getContext());
        byte[] ivData = mProvider.getIvData(mProvider.getContext());

        if (keyData == null || ivData == null)
            return null;

        SecretKeySpec keySpec =  new SecretKeySpec(keyData, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(ivData));
            return new CipherOutputStream(new FileOutputStream(file), cipher);
        } catch (Exception e) {
            Log.e(TAG, "Cannot create CipherOutputFile.", e);
        }
        return null;
    }

    private void cleanupFile(File file) {
        boolean deleted = file.delete();
        if (!deleted) {
            Log.d(TAG, "Could not clean up file: " + file.getAbsolutePath());
        }
    }

    /**
     * Removes the specified photo file from the store if it exists.
     */
    public void remove(long id) {
        cleanupFile(getFileForPhotoFileId(id));
        removeEntry(id);
    }

    /**
     * Returns a file object for the given photo file ID.
     */
    private File getFileForPhotoFileId(long id) {
        return new File(mStorePath, String.valueOf(id));
    }

    /**
     * Puts the entry with the specified photo file ID into the store.
     * @param id The photo file ID to identify the entry by.
     * @param entry The entry to store.
     */
    private void putEntry(long id, Entry entry) {
        if (!mEntries.containsKey(id)) {
            mTotalSize += entry.size;
        } else {
            Entry oldEntry = mEntries.get(id);
            mTotalSize += (entry.size - oldEntry.size);
        }
        mEntries.put(id, entry);
    }

    /**
     * Removes the entry identified by the given photo file ID from the store, removing
     * the associated photo file entry from the database.
     */
    private void removeEntry(long id) {
        Entry entry = mEntries.get(id);
        if (entry != null) {
            mTotalSize -= entry.size;
            mEntries.remove(id);
        }
        mDb.delete(ScContactsDatabaseHelper.Tables.PHOTO_FILES, PhotoFilesColumns.CONCRETE_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public static class Entry {
        /** The photo file ID that identifies the entry. */
        public final long id;

        /** The size of the data, in bytes. */
        public final long size;

        /** The path to the file. */
        public final String path;

        public Entry(File file) {
            id = Long.parseLong(file.getName());
            size = file.length();
            path = file.getAbsolutePath();
        }
    }
}
