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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.silentcircle.logs.Log;

import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

/**
 * Provider (wrapper) for converting native contact to a VCard.
 *
 */
public class VCardProvider extends ContentProvider {

    private static final String TAG = VCardProvider.class.getSimpleName();

    public static final String VCARD_NAME = "contact";
    public static final String VCARD_FILE_EXTENSION = ".vcf";

    public static final String AUTHORITY = BuildConfig.AUTHORITY_BASE + ".messaging.provider.vcard";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    private static final HashMap<String, String> MIME_TYPES = new HashMap<>();

    private Context mContext;

    static {
        MIME_TYPES.put(VCARD_FILE_EXTENSION, "text/vcard");
    }

    /**
     * From a system contact uri create a uri which can be handled by this provider.
     */
    public static Uri getVCardUriForContact(final Context context, final Uri uri) {
        Uri vCardUri = null;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
            vCardUri = Uri.withAppendedPath(VCardProvider.CONTENT_URI, lookupKey);
            cursor.close();
        }
        return vCardUri;
    }

    @Override
    public boolean onCreate() {
        mContext = getContext();
        if (mContext == null)
            return false;
        mContext.getContentResolver().notifyChange(CONTENT_URI, null);
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        /* only support for file name query is implemented */
        String name = getContactName(uri);
        String[] columns = new String[] {OpenableColumns.DISPLAY_NAME};
        MatrixCursor matrixCursor = new MatrixCursor(columns);
        matrixCursor.addRow(new Object[] {name + VCARD_FILE_EXTENSION});
        return matrixCursor;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return MIME_TYPES.get(VCARD_FILE_EXTENSION);
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        String name = getContactName(uri);
        File file = new File(mContext.getFilesDir(), name + VCARD_FILE_EXTENSION);
        if (file.exists()) {
            file.delete();
        }
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        File file = getVCardFile(uri);
        if (file.exists()) {
            writeVCardToFile(file, uri);
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        }
        else {
            throw new FileNotFoundException(uri.getPath());
        }
    }

    private File getVCardFile(final Uri uri) {
        String name = getContactName(uri);
        File file = new File(mContext.getFilesDir(), name + VCARD_FILE_EXTENSION);
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, "Failed to get file for uri [" + uri + "], " + e.getMessage());
        }
        return file;
    }

    private void writeVCardToFile(final File file, final Uri uri) {
        PrintWriter writer = null;
        try {
            String vCard = getVCardAsString(uri);
            writer = new PrintWriter(file);
            writer.print(vCard);
            writer.flush();
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to write VCard for uri [" + uri + "], " + e.getMessage());
        }
        finally {
            IOUtils.close(writer);
        }
    }

    private String getContactName(final Uri uri) {
        String name = null;

        String lookupKey = uri.getLastPathSegment();
        ContentResolver resolver = mContext.getContentResolver();
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
        Uri contactUri = ContactsContract.Contacts.lookupContact(resolver, lookupUri);
        Cursor cursor =  resolver.query(contactUri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            cursor.close();
        }
        if (TextUtils.isEmpty(name)) {
            name = VCARD_NAME;
        }
        return name;
    }

    @Nullable
    private String getVCardAsString(final Uri uri) throws IOException {
        String lookupKey = uri.getLastPathSegment();
        Uri vCardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey);

        AssetFileDescriptor descriptor;
        byte[] buffer = null;
        descriptor = mContext.getContentResolver().openAssetFileDescriptor(vCardUri, "r");
        if (descriptor != null) {
            FileInputStream inputStream = descriptor.createInputStream();
            buffer = IOUtils.readFully(inputStream);
            IOUtils.close(inputStream, descriptor);
        }
        return buffer != null ? new String(buffer) : null;
    }
}
