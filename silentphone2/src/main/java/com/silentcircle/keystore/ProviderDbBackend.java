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
package com.silentcircle.keystore;

/**
 * Key Manager provider.
 *
 * Created by werner on 23.08.13.
 */

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.silentcircle.keystore.KeyStoreHelper.AppInfo;

import net.sqlcipher.database.SQLiteDatabase;

import java.security.SecureRandom;
import java.util.Set;

public class ProviderDbBackend extends ContentProvider {

    private static final String TAG = "ProviderKeystore";

    private static final int REGISTER = 1;
    private static final int UNREGISTER = 2;
    private static final int PRIVATE_DATA = 3;
    private static final int SHARED_DATA = 4;
    private static final int PRIVATE_DATA_NEW = 5;

    public static final String AUTHORITY = "com.silentcircle.keystore";

    private static final String SUPPORT_NAME = ".keymanagersupport";    // last part of application's support provider

    /**
     * The MIME type of a {@link # CONTENT_URI} single data item.
     */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.keystore.data";

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(AUTHORITY, "register",    REGISTER);
        sURIMatcher.addURI(AUTHORITY, "unregister",  UNREGISTER);
        sURIMatcher.addURI(AUTHORITY, "private",     PRIVATE_DATA);
        sURIMatcher.addURI(AUTHORITY, "shared",      SHARED_DATA);
        sURIMatcher.addURI(AUTHORITY, "private_new", PRIVATE_DATA_NEW);
    }

    /*
     * The next code section contains the mandatory public provider functions.
     */
    public ProviderDbBackend() {
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final int match = sURIMatcher.match(uri);

        if (match != UNREGISTER && (selectionArgs == null || selectionArgs.length < 1))
            return null;

        String appName = getCallerName();
        long token = 0;

        if (!(match == REGISTER || match == UNREGISTER)) {
            if (TextUtils.isEmpty(appName))
                return null;
            if (!isCallerRegistered(appName)) {
                Log.w(TAG, "Cannot process query request from non-registered application: " + appName);
                return null;
            }
            if (TextUtils.isEmpty(selectionArgs[0]))
                return null;
            token = KeyStoreHelper.getRegisteredApps().get(appName).token;
        }

        byte[] data;
        switch (match) {
            case REGISTER: {
                token = registerWithApplication(appName, selectionArgs[0]);
                if (token == 0)     // registration token must never be zero
                    return null;
                final MatrixCursor c = new MatrixCursor(new String[] {"token"}, 1);
                final MatrixCursor.RowBuilder row = c.newRow();
                row.add(token);     // return token to application here as well. App may use it for a first quick check
                return c;
            }
            case UNREGISTER: {
                if (isCallerRegistered(appName)) {
                    KeyStoreHelper.getRegisteredApps().remove(appName);
                }
                return null;
            }
            case PRIVATE_DATA: {
                String alias = appName + ".private." + selectionArgs[0];
                data = readPrivateData(alias);
                break;
            }
            case SHARED_DATA: {
                String alias = "silentcircle.shared." + selectionArgs[0];
                data = readSharedData(alias);
                break;
            }
            case PRIVATE_DATA_NEW: {
                if (TextUtils.isEmpty(selectionArgs[1]))
                    return null;

                String alias = appName + ".private." + selectionArgs[0];
                data = readPrivateData(alias);
                if (data != null)
                    return null;                // don't generate new data for existing key

                int length = Integer.parseInt(selectionArgs[1]);
                SecureRandom rnd = new SecureRandom();
                data = new byte[length];
                rnd.nextBytes(data);
                if (!storePrivateData(data, alias))
                    return null;
                break;
            }
            default:
                Log.e(TAG, "Cannot query URL: " + uri);
                return null;
        }
        if (data == null)
            return null;

        // TODO: Switch to use 'data', not 'dataStr' after STA uses KeymanagerSupport 2.0.2, then resolve SCA-58
        final String dataStr = Base64.encodeToString(data, Base64.DEFAULT);
        final MatrixCursor c = new MatrixCursor(new String[] {"data", "token"}, 1);
        final MatrixCursor.RowBuilder row = c.newRow();
        row.add(dataStr);                               // return data to application
        row.add(token);                                 // and authorize with registered token
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final int match = sURIMatcher.match(uri);

        String appName = getCallerName();
        if (!values.containsKey("id") || !values.containsKey("tag") || TextUtils.isEmpty(appName))
            return null;

        if (!isCallerRegistered(appName)) {
            Log.w(TAG, "Cannot process insert request from non-registered application: " + appName);
            return null;
        }
        long id = 0;
        try {
            id = values.getAsLong("id");
        } catch (Exception ignored) {}

        String tag = values.getAsString("tag");

        byte[] data;
        switch (match) {
            case PRIVATE_DATA:
                data = getDataFromApp(id, appName, "private");
                if (data == null)
                    return null;

                String alias = appName + ".private." + tag;
                if (!storePrivateData(data, alias))
                    return null;
                return uri;

            case SHARED_DATA:
                data = getDataFromApp(id, appName, "shared");
                if (data == null)
                    return null;

                if (!storeSharedData(data, "silentcircle.shared." + tag))
                    return null;
                return uri;
        }
        Log.e(TAG, "Cannot insert URL: " + uri);
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final int match = sURIMatcher.match(uri);

        String appName = getCallerName();
        if (!values.containsKey("id") || !values.containsKey("tag") || TextUtils.isEmpty(appName))
            return 0;

        if (!isCallerRegistered(appName)) {
            Log.w(TAG, "Cannot process update request from non-registered application: " + appName);
            return 0;
        }
        long id = 0;
        try {
            id = values.getAsLong("id");
        } catch (Exception ignored) {}

        String tag = values.getAsString("tag");

        byte[] data;
        switch (match) {
            case PRIVATE_DATA:
                data = getDataFromApp(id, appName, "private");
                if (data == null)
                    return 0;

                String alias = appName + ".private." + tag;
                if (!updatePrivateData(data, alias))
                    return 0;
                return 1;

            case SHARED_DATA:
                data = getDataFromApp(id, appName, "shared");
                if (data == null)
                    return 0;

                if (!updateSharedData(data, "silentcircle.shared." + tag))
                    return 0;
                return 1;
        }
        Log.e(TAG, "Cannot update URL: " + uri);
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final int match = sURIMatcher.match(uri);

        String appName = getCallerName();

        if (selectionArgs.length != 1 || TextUtils.isEmpty(appName) || TextUtils.isEmpty(selectionArgs[0]))
            return 0;

        if (!isCallerRegistered(appName)) {
            Log.w(TAG, "Cannot process delete request from non-registered application: " + appName);
            return 0;
        }
        switch (match) {
            case PRIVATE_DATA:
                String alias = appName + ".private." + selectionArgs[0];
                if (!deletePrivateData(alias))
                    return 0;
                return 1;

            case SHARED_DATA:
                if (!deleteSharedData("silentcircle.shared." + selectionArgs[0]))
                    return 0;
                return 1;
        }
        Log.e(TAG, "Cannot delete URL: " + uri);
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        final int match = sURIMatcher.match(uri);
        switch (match) {
            case REGISTER:
            case PRIVATE_DATA:
            case SHARED_DATA:
                return CONTENT_ITEM_TYPE;
            default:
                Log.e(TAG, "Cannot get type for URL: " + uri);
                return null;
        }
    }

    /**
     * Query all registered applications with a lock request URI.
     */
    static void sendLockRequests() {
        ContentResolver resolver = KeyStoreHelper.getContentResolver();

        Set<String> registeredNames = KeyStoreHelper.getRegisteredApps().keySet();
        for (String name : registeredNames) {
            // construct the authority and action of registering application's key manager support provider
            final Uri appSupportUri = Uri.parse("content://" + name + SUPPORT_NAME + "/lock");
            resolver.query(appSupportUri, null, null, null, null);
        }
    }

    /**
     * Query all registered applications with an unlock request URI.
     */
    static void sendUnlockRequests() {
        ContentResolver resolver = KeyStoreHelper.getContentResolver();

        Set<String> registeredNames = KeyStoreHelper.getRegisteredApps().keySet();
        for (String name : registeredNames) {
            // construct the authority and action of registering application's key manager support provider
            final Uri appSupportUri = Uri.parse("content://" + name + SUPPORT_NAME + "/unlock");
            try {
                resolver.query(appSupportUri, null, null, null, null);
            } catch (Exception e) {
                Log.w(TAG, "Cannot send unlock request to: " + appSupportUri);
            }
        }
    }

    /*
     * The next code section holds the private functions
     */

    private static String ROWS[] = { KeyStoreDatabase.ALIAS, KeyStoreDatabase.KEY_DATA };
    private static String WHERE = KeyStoreDatabase.ALIAS + " = ?";

    /**
     * Read a single key entry from the key store.
     *
     * @param store the key store database
     * @param alias the name of the key entry
     * @return key data or {@code null} in case of error or no entry.
     */
    private static byte[] readKeyEntry(SQLiteDatabase store, String alias) {
        Cursor cursor = store.query(KeyStoreDatabase.TABLE, ROWS, WHERE, new String[]{alias}, null, null, null);
        if (cursor == null)
            return null;

        try {
            if (cursor.getCount() == 0 || cursor.getCount() > 1)
                return null;

            cursor.moveToFirst();
            return cursor.getBlob(1);
        } finally {
            cursor.close();
        }
    }

    /**
     * Write a single key entry into the key store
     *
     * @param store the key store database
     * @param alias the name of the key entry
     * @param kse the key data
     * @return {@code true} if key entry stored, {@code false} otherwise.
     */
    private static boolean writeKeyEntry(SQLiteDatabase store, String alias, byte[] kse) {
        ContentValues values = new ContentValues();
        values.put(KeyStoreDatabase.ALIAS, alias);
        values.put(KeyStoreDatabase.KEY_DATA, kse);

        long ret = store.insert(KeyStoreDatabase.TABLE, null, values);
        return ret != -1;
    }

    /**
     * Update a single key entry in the key store
     *
     * @param store the key store database
     * @param alias the name of the key entry
     * @param kse the key data
     * @return {@code true} if key entry updated, {@code false} otherwise.
     */
    private static boolean updateKeyEntry(SQLiteDatabase store, String alias, byte[] kse) {
        ContentValues values = new ContentValues();
        values.put(KeyStoreDatabase.KEY_DATA, kse);

        int ret = store.update(KeyStoreDatabase.TABLE, values, WHERE, new String[] {alias});
        return ret == 1;
    }

    /**
     * Delete a single key entry from the key store
     *
     * @param store the key store database
     * @param alias the name of the key entry
     * @return {@code true} if key entry updated, {@code false} otherwise.
     */
    // public int update (String table, ContentValues values, String whereClause, String[] whereArgs)
    private static boolean deleteKeyEntry(SQLiteDatabase store, String alias) {

        int ret = store.delete(KeyStoreDatabase.TABLE, WHERE, new String[]{alias});
        return ret == 1;
    }

    /**
     * Determine caller's package name.
     *
     * Convenience function that mainly covers the various exceptions and handles them.
     *
     * @return the caller's name
     */
    private String getCallerName() {
        // get package name of querying application
        final int uid = Binder.getCallingUid();

        PackageManager pm = getContext().getPackageManager();
        return pm.getNameForUid(uid);
    }

    /**
     * Generate a random token and send it back to caller' key manager support provider.
     *
     * @return the token value
     */
    private long registerWithApplication(String callerName, String displayName) {

        // generate a "long" token (random)
        SecureRandom pseudoRandom = new SecureRandom();
        int r = pseudoRandom.nextInt();
        long token = r << 31;
        r = pseudoRandom.nextInt();
        token |= token | r;

        ContentValues values = new ContentValues(1);
        values.put("token", token);

        // construct the authority and action of registering application's key manager support provider
        final Uri appSupportUri = Uri.parse("content://" + callerName + SUPPORT_NAME + "/register");

        // use application's key manager support provider's "insert" to send this token to it
        ContentResolver resolver = getContext().getContentResolver();
        Uri retUri = null;
        try {
            retUri = resolver.insert(appSupportUri, values);
        } catch (Exception e) {
            Log.w(TAG, "Application support uri: " + appSupportUri.toString());
            Log.w(TAG, "Cannot register with application's KeyManager support provider.", e);
        }
        if (retUri == null)
            return 0;

        AppInfo info = new AppInfo();
        info.token = token;
        info.displayName = displayName;
        KeyStoreHelper.getRegisteredApps().put(callerName, info);
        return token;
    }

    /**
     * Get the real key data from application via its key manager support provider.
     *
     * @param id the data id sent by the application
     * @param appName the caller package/application name
     * @param area    get data for that area, currently 'shared' or 'private'
     * @return the data or {@code null} if none is available
     */
    private byte[] getDataFromApp(long id, String appName, String area) {

        // use application's key manager support provider's "query" to get the real data
        ContentResolver resolver = getContext().getContentResolver();

        /*
         To get the key data set the selection args:
            selectionArgs[0] = id;
         */
        String[] selectionArgs = new String[1];
        selectionArgs[0] = Long.toString(id);

        final Uri appSupportUri = Uri.parse("content://" + appName + SUPPORT_NAME + "/" + area);
        Cursor c;
        try {
            c = resolver.query(appSupportUri, null, null, selectionArgs, null);
        } catch (Exception e) {
            Log.w(TAG, "Cannot query real data with application's KeyManagerSupport provider.", e);
            return null;
        }
        byte[] data = null;
        if (c != null) {
            c.moveToFirst();
            String dataStr = c.getString(0);
            data = Base64.decode(dataStr, Base64.DEFAULT);
            c.close();
        }
        return data;
    }

    /**
     * Check if the caller is registered.
     *
     * @param appName the caller package/application name
     * @return true if registered, false otherwise
     */
    private boolean isCallerRegistered(String appName) {
        return KeyStoreHelper.getRegisteredApps().containsKey(appName);
    }

    private byte[] readPrivateData(String alias) {
        SQLiteDatabase store = KeyStoreHelper.getDatabase();
        if (store == null)
            return null;

        String newAlias = alias.replace('.', '_');
        byte[] kse = readKeyEntry(store, newAlias);
        if (kse == null) {
            return null;
        }
        return kse;
    }

    private byte[] readSharedData(String alias) {
        return readPrivateData(alias);              // Maybe we will have a different key store for shared data
    }

    private boolean storePrivateData(byte[] data, String alias) {
        SQLiteDatabase store = KeyStoreHelper.getDatabase();
        if (store == null)
            return false;

        String newAlias = alias.replace('.', '_');
        byte[] kse = readKeyEntry(store, newAlias);    // don't overwrite existing alias/key entry
        return kse == null && writeKeyEntry(store, newAlias, data);
    }

    private boolean storeSharedData(byte[] data, String alias) {
        return storePrivateData(data, alias);
    }

    private boolean updatePrivateData(byte[] data, String alias) {
        SQLiteDatabase store = KeyStoreHelper.getDatabase();
        if (store == null)
            return false;

        String newAlias = alias.replace('.', '_');
        byte[] kse = readKeyEntry(store, newAlias);    // alias/key entry must exist
        return kse != null && updateKeyEntry(store, newAlias, data);
    }

    private boolean updateSharedData(byte[] data, String alias) {
        return updatePrivateData(data, alias);
    }

    private boolean deletePrivateData(String alias) {
        SQLiteDatabase store = KeyStoreHelper.getDatabase();
        if (store == null)
            return false;

        String newAlias = alias.replace('.', '_');
        byte[] kse = readKeyEntry(store, newAlias);    // alias/key entry must exist
        return kse != null && deleteKeyEntry(store, newAlias);
    }

    private boolean deleteSharedData(String alias) {
        return deletePrivateData(alias);
    }
}
