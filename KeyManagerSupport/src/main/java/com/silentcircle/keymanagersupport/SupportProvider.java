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
package com.silentcircle.keymanagersupport;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SupportProvider implements the application part of SilentCircle's Android key manager provider.
 * <p/>
 * Applications shall use the functions of {@link KeyManagerSupport} class only.
 * <p/>
 * Applications <b>must not use</b> the support provider functions: {@code the constructor, onCreate, query, insert,
 * update, delete, getType}.
 *
 * <p/>
 * Created by werner on 23.08.13.
 */
public final class SupportProvider extends ContentProvider {

    @SuppressWarnings("unused")
    private static final String TAG = "SupportProvider";

    private static final Collection<KeyManagerSupport.KeyManagerListener> keyManagerListeners = new LinkedList<KeyManagerSupport.KeyManagerListener>();

    private static final int REGISTER = 1;
    private static final int PRIVATE_DATA = 2;
    private static final int SHARED_DATA = 3;
    private static final int LOCK_REQUEST = 4;
    private static final int UNLOCK_REQUEST = 5;

    /**
     * The MIME type of a {@link # CONTENT_URI} single data item.
     */
    private static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.keymngr.data";

    // The following URLs are for the application's KeyManager support provider. The URLs are different
    // for each application thus we cannot do it statically but we need an initialization during runtime.
    private static final String SUPPORT_NAME = ".keymanagersupport";


    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    protected static long registerToken;

    /*
     * The next code section contains the mandatory public provider functions.
     */

    public SupportProvider() {
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Applications shall not use this function.
     *
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final int match = sURIMatcher.match(uri);

        switch (match) {

            case PRIVATE_DATA:
            case SHARED_DATA:
                if (selectionArgs.length != 1 || TextUtils.isEmpty(selectionArgs[0]))
                    return null;
                long id;
                try {
                    id = Long.parseLong(selectionArgs[0]);
                } catch (NumberFormatException e) {
                    return null;
                }
                String data = Base64.encodeToString(getDataForId(id), Base64.DEFAULT);
                final MatrixCursor c = new MatrixCursor(new String[]{"data"}, 1);
                final MatrixCursor.RowBuilder row = c.newRow();
                row.add(data);                                  // return data to KeyManager provider
                synchronized (keyManagerListeners) {
                    for (KeyManagerSupport.KeyManagerListener l : keyManagerListeners)
                        l.onKeyDataRead();
                }
                return c;

            case LOCK_REQUEST:
                synchronized (keyManagerListeners) {
                    for (KeyManagerSupport.KeyManagerListener l : keyManagerListeners)
                        l.onKeyManagerLockRequest();
                }
                break;

            case UNLOCK_REQUEST:
                synchronized (keyManagerListeners) {
                    for (KeyManagerSupport.KeyManagerListener l : keyManagerListeners)
                        l.onKeyManagerUnlockRequest();
                }
                break;
        }
        return null;
    }

    /**
     * Applications shall not use this function.
     *
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final int match = sURIMatcher.match(uri);

        switch (match) {
            case REGISTER:
                if (!values.containsKey("token"))
                    return null;
                registerToken = values.getAsLong("token");
                if (registerToken == 0)                         // registration token must never be zero
                    return null;
                return uri;
        }
        throw new UnsupportedOperationException("Cannot insert URL: " + uri);
    }

    /**
     * Applications shall not use this function.
     *
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot update URL: " + uri);
    }

    /**
     * Applications shall not use this function.
     *
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot delete that URL: " + uri);
    }

    /**
     * Applications shall not use this function.
     *
     */
    @Override
    public String getType(Uri uri) {
        final int match = sURIMatcher.match(uri);
        switch (match) {
            case REGISTER:
            case PRIVATE_DATA:
            case SHARED_DATA:
                return CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);

        }
    }

    /**
     * Get the key manager's registration token.
     *
     * @return the token. If the token is {@code 0} then this application is not registered.
     */
    protected static long getRegisterToken() {
        return registerToken;
    }

    /**
     * Adds a {@code KeyManagerListener} to the list of listeners.
     *
     * @param l the {@code KeyManagerListener} to add
     */
    protected static void addListener(KeyManagerSupport.KeyManagerListener l) {
        synchronized (keyManagerListeners) {
            if (keyManagerListeners.contains(l))     // don't add twice
                return;
            keyManagerListeners.add(l);
        }
    }

    /**
     * Removes a {@code KeyManagerListener} from the list of listeners.
     *
     * @param l the {@code KeyManagerListener} to remove
     */
    protected static void removeListener(KeyManagerSupport.KeyManagerListener l) {
        synchronized (keyManagerListeners) {
            keyManagerListeners.remove(l);
        }
    }

    protected static void initialize(String pkgName) {
        String AUTHORITY = pkgName + SUPPORT_NAME;
        sURIMatcher.addURI(AUTHORITY, "register", REGISTER);
        sURIMatcher.addURI(AUTHORITY, "private",  PRIVATE_DATA);
        sURIMatcher.addURI(AUTHORITY, "shared",   SHARED_DATA);
        sURIMatcher.addURI(AUTHORITY, "lock",     LOCK_REQUEST);
        sURIMatcher.addURI(AUTHORITY, "unlock",   UNLOCK_REQUEST);
    }

    private static ConcurrentHashMap<Long, byte[]> preparedStorage = new ConcurrentHashMap<Long, byte[]>(4, 0.75f, 3);

    // We don't use random number for security code, only to generating a simple, temporary id
    @SuppressLint("TrulyRandom")
    protected static long prepareForStorage(byte[] data) {
        SecureRandom random = new SecureRandom();
        int r = random.nextInt();
        long id = r << 31;
        r = random.nextInt();
        id |= id | r;
        preparedStorage.put(id, data);
        return id;
    }

    private byte[] getDataForId(long id) {
        byte[] data = preparedStorage.get(id);
        preparedStorage.remove(id);
        return data;
    }
}
