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

package com.silentcircle.common.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class RingtoneUtils {

    private static final String TAG = RingtoneUtils.class.getSimpleName();

    public static String TITLE_EMERGENCY = "Emergency";
    public static String NAME_EMERGENCY = "emergency_spa4";
    public static String FILE_NAME_EMERGENCY = NAME_EMERGENCY + ".ogg";

    /**
     * Return true if the system has a system default ringtone. This is always
     * false for most tablets.
     *
     * @param ctx the {@link Context}
     * @return true or false
     */
    public static boolean hasSystemDefaultRingtone(Context ctx, int type) {
        return RingtoneManager.getActualDefaultRingtoneUri(ctx, type)
                != null;
    }

    public static boolean hasSystemDefaultRingtone(Context ctx) {
        return hasSystemDefaultRingtone(ctx, RingtoneManager.TYPE_RINGTONE);
    }

    /**
     * Returns the system default ringtone URI {@link Uri}, or
     * the first available ringtone when the system default
     * does not exist (usually tablets).
     *
     * @param ctx the {@link Context}
     * @return Uri ringtone URI
     */
    public static Uri getDefaultRingtoneUri(Context ctx, int type) {
        Uri uri;

        uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, type);

        if (uri == null) {
            // The default ringtone doesn't exist - probably a tablet
            // Return the first available
            RingtoneManager rm = new RingtoneManager(ctx);
            rm.setType(type);

            Cursor cursor = rm.getCursor();
            cursor.moveToFirst();

            String idString = cursor.getString(RingtoneManager.ID_COLUMN_INDEX);
            String uriString = cursor.getString(RingtoneManager.URI_COLUMN_INDEX);

            uri = Uri.parse(uriString + '/' + idString);

            cursor.close();

            return uri;
        } else {
            // Return system default ringtone
            return uri;
        }
    }

    public static Uri getDefaultRingtoneUri(Context ctx) {
        return getDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE);
    }

    /**
     * Returns a map of ring tones registered on system. Map key is ring tone name,
     * value is ring tone uri.
     *
     * @param context {@link Context} used to access system data.
     * @return Map of ring tones.
     */
    public static Map<String, String> getRingtones(@NonNull Context context, int type) {
        RingtoneManager manager = new RingtoneManager(context);
        manager.setType(type);
        Cursor cursor = manager.getCursor();

        Map<String, String> map = new HashMap<>();
        while (cursor.moveToNext()) {
            String notificationTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
            String notificationUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX);

            map.put(notificationTitle, notificationUri);
        }

        return map;
    }

    public static Map<String, String> getRingtones(@NonNull Context context) {
        return getRingtones(context, RingtoneManager.TYPE_RINGTONE);
    }

    /**
     * Returns whether given ringtone uri is valid (will produce sound). Can be used to determine
     * validity of a ring tone uri which is retrieved from preferences.
     *
     * @param context {@link Context} used to access system data.
     * @param ringTone {@link Uri} for ring tone.
     *
     * @return true if there is a registered ring tone with given uri, false otherwise.
     */
    public static boolean isRingtoneValid(@NonNull Context context, @NonNull Uri ringTone) {
        Map<String, String> ringTones = getRingtones(context);
        return (ringTones.values().contains(ringTone.toString()));
    }

    /**
     * Creates and registers a ring tone from an asset file in application.
     *
     * @param context {@link Context} used to access system data.
     * @param name Ring tone name.
     * @param assetName File name to use for ring tone.
     * @param resourceId Asset id within application
     * @param mimeType Mime type to use when registering ring tone. Should be an audio type (audio/*).
     *
     * @return {@link Uri} for ringtone if creation is successful, null otherwise.
     *
     * @throws IOException if it is not possible to create ring tone file on external storage.
     */
    @Nullable
    public static Uri createRingtone(@NonNull Context context, @NonNull String name,
            @NonNull String assetName, int resourceId, @NonNull String mimeType) throws IOException {
        Log.d(TAG, "Create ring tone " + name);
        Uri savedRingtoneUri = null;
        ContentResolver contentResolver = context.getContentResolver();

        boolean isStorageMounted = Environment.getExternalStorageState().equals(
            Environment.MEDIA_MOUNTED);
        if (!isStorageMounted) {
            Log.e(TAG, "External storage not ready, cannot create ringtone.");
            return null;
        }

        File ringtoneDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES);
        ringtoneDir.mkdirs();
        File ringtone = new File(ringtoneDir, assetName);

        Uri assetUri = Uri.parse("android.resource://com.silentcircle.silentphone/" + resourceId);
        AssetFileDescriptor assetRingtoneDescriptor = null;
        try {
            assetRingtoneDescriptor = contentResolver.openAssetFileDescriptor(assetUri, "r");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File by resource " + resourceId + " not found.");
        }

        if (assetRingtoneDescriptor != null) {
            InputStream input = assetRingtoneDescriptor.createInputStream();
            IOUtils.writeToFile(ringtone, input);
            IOUtils.close(input);
            assetRingtoneDescriptor.close();
        }
        else {
            return null;
        }

        Uri uri = MediaStore.Audio.Media.getContentUriForPath(ringtone.getAbsolutePath());

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, ringtone.getAbsolutePath());
        values.put(MediaStore.MediaColumns.TITLE, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.Audio.Media.ARTIST, R.string.app_name);
        values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true);
        values.put(MediaStore.Audio.Media.IS_ALARM, true);
        values.put(MediaStore.Audio.Media.IS_MUSIC, false);

        int updated = 0;
        // check if file already exists in MediaStore
        String[] projection = {MediaStore.Audio.Media._ID};
        String selectionClause = MediaStore.Audio.Media.DATA + "=?";
        String[] selectionArgs = {ringtone.getAbsolutePath()};
        Cursor cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selectionClause, selectionArgs, null);

        if (cursor != null && cursor.getCount() > 0) {
            // entry already exists, overwrite with new values
            try {
                updated = contentResolver.update(uri, values, selectionClause, selectionArgs);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "Cannot update ringtone " + name);
            }
            Log.d(TAG, "Ringtone with such name exists, update: " + updated);
            if (updated < 1) {
                Log.w(TAG, "Ringtone not updated, will create a new one.");
            }
        }
        if (cursor != null) {
            cursor.close();
        }

        if (updated < 1) {
            Log.d(TAG, "Creating a new ringtone " + uri);
            try {
                savedRingtoneUri = contentResolver.insert(uri, values);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "Cannot create ringtone " + name);
            }
        }

        return savedRingtoneUri;
    }

    /**
     * Remove ring tone from system by its name. Actual file on file system is not removed.
     * If application still refers to the deleted ringtone by its uri, ring tone use will not work,
     * playing it will produce no sound.
     *
     * @param context {@link Context} used to access system data.
     * @param name Ring tone name.
     */
    public static void removeRingTone(@NonNull Context context, @NonNull String name) {
        Log.w(TAG, "Removing ring tone " + name);
        ContentResolver contentResolver = context.getContentResolver();
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.TITLE};
        String selectionClause = MediaStore.MediaColumns.TITLE + "=?";
        String[] selectionArgs = {name};
        Cursor cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selectionClause, selectionArgs, null);

        String ringTonePath = null;
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToNext();
            ringTonePath = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
            cursor.close();
        }

        if (!TextUtils.isEmpty(ringTonePath)) {
            Uri uri = MediaStore.Audio.Media.getContentUriForPath(ringTonePath);
            int deleted = context.getContentResolver().delete(uri,
                    MediaStore.MediaColumns.DATA + "=\"" + ringTonePath + "\"", null);

            if (deleted <= 0) {
                Log.w(TAG, "Could not remove ring tone " + name);
            }
        }
    }

}
