package com.silentcircle.keymanagersupport;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

/**
 * Application support functions for SilentCircle's key manager provider.
 * <p/>
 * Applications shall only use this class to access key manager functions.
 * <p/>
 * Applications <b>must not use</b> the support provider functions: {@code the constructor, onCreate, query, insert,
 * update, delete, getType}.
 * <p/>
 * Created by werner on 10.09.13.
 */
public class KeyManagerSupport {

    public interface KeyManagerListener {

        /**
         * The key manager read data during a {@code store*} or {@code update*} call.
         */
        void onKeyDataRead();

        /**
         * The support provider received a lock request from the key manager.
         * <p/>
         * The user may <em>lock</em> the key manager. In this case the key manager closes the
         * key store and sends a lock notification to all registered applications. If the key store
         * is locked the key manager cannot process key entry requests. Registration and de-registration
         * is still possible.
         * <p/>
         * An applications may take more actions, such as deleting sensitive data or closing connections.
         */
        void onKeyManagerLockRequest();

        /**
         * The support provider received an unlock request from the key manager.
         * <p/>
         * The user has <em>unlocked</em> the key manager. The key store is open, key manager is ready and
         * sends an unlock notification to all registered applications.
         * <p/>
         * An applications may take more actions, such as reading their key data, shared data etc.
         */
        void onKeyManagerUnlockRequest();
    }

    @SuppressWarnings("unused")
    private static final String TAG = "KeyManagerSupport";

    public static final String KEY_STORE_READY_ACTION= "com.silentcircle.keystore.action.READY";


    /** Tag name for the device authorization data. */
    public static final String DEV_AUTH_DATA_TAG = "device_authorization";

    /** Tag name for the unique device id. */
    public static final String DEV_UNIQUE_ID_TAG = "device_unique_id";

    private static final String AUTHORITY_KEY_CHAIN = "com.silentcircle.keystore";


    /**
     * The content:// style URL for to handle private data.
     */
    private static final Uri CONTENT_URI_PRIVATE_DATA_KEY_CHAIN = Uri.parse("content://" + AUTHORITY_KEY_CHAIN + "/private");

    /**
     * The content:// style URL for to handle private data.
     */
    private static final Uri CONTENT_URI_PRIVATE_DATA_KEY_CHAIN_NEW = Uri.parse("content://" + AUTHORITY_KEY_CHAIN + "/private_new");

    /**
     * The content:// style URL for to handle data that's shared between SC applications.
     */
    private static final Uri CONTENT_URI_SHARED_DATA_KEY_CHAIN = Uri.parse("content://" + AUTHORITY_KEY_CHAIN + "/shared");

    /**
     * The content:// style URL for to "register" an application with the key chain provider.
     */
    private static final Uri CONTENT_URI_REGISTER_KEY_CHAIN = Uri.parse("content://" + AUTHORITY_KEY_CHAIN + "/register");

    /**
     * The content:// style URL for to "unregister" an application with the key chain provider.
     */
    private static final Uri CONTENT_URI_UNREGISTER_KEY_CHAIN = Uri.parse("content://" + AUTHORITY_KEY_CHAIN + "/unregister");

    /**
     * Name of the SilentCircle Key Manager package, included in silent contacts
     */
    private static final String SKA_PKG = "com.silentcircle.silentphone";

    private static boolean initialized;

    private static PackageInfo scPkgInfo;

    /**
     * Check if KeyManager package is available.
     *
     * @param pm the package manager
     * @return {@code true} if KeyManager package is available
     */
    public static boolean hasKeyManager(PackageManager pm) {
        if (scPkgInfo != null && scPkgInfo.versionCode >= 210)
            return true;

        try {
            scPkgInfo = pm.getPackageInfo(SKA_PKG, PackageManager.GET_PERMISSIONS);
            if (scPkgInfo.versionCode < 210)
                return false;
        }
        catch (PackageManager.NameNotFoundException ignored) {
        }
        return scPkgInfo != null;
    }

    /**
     * Check if KeyManager signature matches this application's package signature.
     *
     * @param pm the package manager
     * @param myPackage application package name, usually via context.getPackageName()
     * @return {@code true} if signatures match
     */
    public static boolean signaturesMatch(PackageManager pm, String myPackage) {
        int result = pm.checkSignatures(myPackage, SKA_PKG);
        return result == PackageManager.SIGNATURE_MATCH;
    }

    /**
     * Get the key manager's registration token.
     *
     * @return the token. If the token is {@code 0} then this application is not registered.
     */
    @SuppressWarnings("unused")
    public static long getRegisterToken() {
        return SupportProvider.getRegisterToken();
    }

    /**
     * Create the Intent to check KeyStore ready status.
     * <p/>
     * The application sends this Intent with {@code startActivityForResult}. The Key store
     * returns {@code RESULT_OK} if it could unlock the key store. To unlock the key store the
     * Key Manager may display a UI to get the password.
     *
     * @return Intent, send with {@code startActivityForResult}
     */
    public static Intent getKeyManagerReadyIntent() {
        Intent intent = new Intent(KEY_STORE_READY_ACTION);
        intent.setComponent(new ComponentName(SKA_PKG, "com.silentcircle.keystore.KeyStoreActivity"));
        return intent;
    }

    /**
     * Register this application with the KeyManager provider.
     * <p/>
     * An application may register several times. They key manager generates a new token
     * and overwrites the current registration data.
     * <p/>
     * Registered applications receive notifications such as a <em>lock</em> notification.
     *
     * @param resolver a ContentResolver
     * @param pkgName  application package name, usually via context.getPackageName()
     * @param displayName a human readable name of the application, for example SilentText or SilentPhone
     * @return The initialization token. If the token is {@code 0} then the registration failed.
     */
    public static long registerWithKeyManager(ContentResolver resolver, String pkgName, String displayName) {
        if (!initialized)
            initialize(pkgName);

        /*
          Send the display name to KeyManager provider set it to selection args:
            selectionArgs[0] = data tag name;
         */
        String[] selectionArgs = new String[1];
        selectionArgs[0] = displayName;

        Cursor c = resolver.query(CONTENT_URI_REGISTER_KEY_CHAIN, null, null, selectionArgs, null);
        long token = 0;
        if (c != null) {
            c.moveToFirst();
            token = c.getLong(0);
            c.close();
        }
        return token;
    }

    /**
     * Unregister this application with the KeyManager provider.
     *
     * @param resolver The ContentResolver
     */
    public static void unregisterFromKeyManager(ContentResolver resolver) {
        if (!initialized)
            return;

        resolver.query(CONTENT_URI_UNREGISTER_KEY_CHAIN, null, null, null, null);
        SupportProvider.registerToken = 0;
    }

    /**
     * Get the key data of an application key entry.
     * <p/>
     * The key manager returns an error if an application key entry with this name does not exist.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver a ContentResolver
     * @param tag name of the application key entry
     * @return the key data as byte array or {@code null} if no key entry found
     */
    public static byte[] getPrivateKeyData(ContentResolver resolver, String tag) {
        return getKeyData(resolver, tag, CONTENT_URI_PRIVATE_DATA_KEY_CHAIN);
    }

    /**
     * Get the key data of a shared key entry.
     * <p/>
     * The key manager returns an error if a shared key entry with this name does not exist.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver a ContentResolver
     * @param tag name of the shared key entry
     * @return the key data as byte array or {@code null} if no key entry found
     */
    public static byte[] getSharedKeyData(ContentResolver resolver, String tag) {
        return getKeyData(resolver, tag, CONTENT_URI_SHARED_DATA_KEY_CHAIN);
    }

    private static byte[] getKeyData(ContentResolver resolver, String tag, Uri uri) {
        if (!initialized)
            return null;

        /*
         To get a key set the selection args:
            selectionArgs[0] = entry name;
         */
        String[] selectionArgs = new String[1];
        selectionArgs[0] = tag;

        Cursor c = resolver.query(uri, null, null, selectionArgs, null);
        byte[] data = null;
        long token = 0;
        if (c != null) {
            c.moveToFirst();
            String dataStr = c.getString(0);
            if (dataStr == null)
                return null;
            try {
                data = Base64.decode(dataStr, Base64.DEFAULT);
            } catch (IllegalArgumentException e) {
                data = c.getBlob(0);
            }
            token = c.getLong(1);
            c.close();
        }
        if (token == 0 || token != SupportProvider.getRegisterToken())
            return null;
        return data;
    }

    /**
     * Create an application key entry and store the key data.
     * <p/>
     * The key manager does not overwrite data of an existing application key entry.
     * Use {@link #updatePrivateKeyData(android.content.ContentResolver, byte[], String)} to
     * modify data of an existing application key entry.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver a ContentResolver
     * @param tag name of the new key entry
     * @return {@code true} is key entry was created, {@code false} otherwise
     */
    public static boolean storePrivateKeyData(ContentResolver resolver, byte[] data, String tag ) {
        return storeKeyData(resolver, data, tag, CONTENT_URI_PRIVATE_DATA_KEY_CHAIN);
    }

    /**
     * Create a shared key entry and store the key data.
     * <p/>
     * The key manager does not overwrite data of an existing shared key entry.
     * Use {@link #updateSharedKeyData(android.content.ContentResolver, byte[], String)} to
     * modify data of an existing shared key entry.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver The ContentResolver
     * @param tag name of the new key entry
     * @return {@code true} is new key entry was created, {@code false} otherwise
     */
    public static boolean storeSharedKeyData(ContentResolver resolver, byte[] data, String tag ) {
        return storeKeyData(resolver, data, tag, CONTENT_URI_SHARED_DATA_KEY_CHAIN);
    }

    private static boolean storeKeyData(ContentResolver resolver, byte[] data, String tag, Uri uri ) {
        if (!initialized)
            return false;
        long id = SupportProvider.prepareForStorage(data);
        ContentValues values = new ContentValues(3);
        values.put("id", id);
        values.put("tag", tag);
        Uri retUri;
        retUri = resolver.insert(uri, values);
        return retUri != null;
    }

    /**
     * Update an application key entry with new key data.
     * <p/>
     * The function modifies the key data of an existing application key entry only.
     * Use {@link #storePrivateKeyData(android.content.ContentResolver, byte[], String)}
     * to create a new application key entry.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver The ContentResolver
     * @param tag name of the existing key entry
     * @return {@code true} if key data was updated, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean updatePrivateKeyData(ContentResolver resolver, byte[] data, String tag) {
        return updateKeyData(resolver, data, tag, CONTENT_URI_PRIVATE_DATA_KEY_CHAIN);
    }

    /**
     * Update a shared key entry with new key data.
     * <p/>
     * The function modifies the key data of an existing shared key entry only.
     * Use {@link #storeSharedKeyData(android.content.ContentResolver, byte[], String)}
     * to create a new shared key entry.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver The ContentResolver
     * @param tag name of the existing key entry
     * @return {@code true} if key data was updated, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean updateSharedKeyData(ContentResolver resolver, byte[] data, String tag) {
        return updateKeyData(resolver, data, tag, CONTENT_URI_SHARED_DATA_KEY_CHAIN);
    }

    private static boolean updateKeyData(ContentResolver resolver, byte[] data, String tag, Uri uri) {
        if (!initialized)
            return false;
        long id = SupportProvider.prepareForStorage(data);
        ContentValues values = new ContentValues(3);
        values.put("id", id);
        values.put("tag", tag);
        int updated;
        updated = resolver.update(uri, values, null, null);
        return updated == 1;
    }

    /**
     * Delete an application key entry.
     * <p/>
     * The key manager returns an error if an application entry with this name does not exist.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver The ContentResolver
     * @param tag name of the existing key entry
     * @return {@code true} if key entry was deleted, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean deletePrivateKeyData(ContentResolver resolver, String tag) {
        return deleteKeyData(resolver, tag, CONTENT_URI_PRIVATE_DATA_KEY_CHAIN);
    }

    /**
     * Delete a shared key entry.
     * <p/>
     * The key manager returns an error if a shared entry with this name does not exist.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver The ContentResolver
     * @param tag name of the existing key entry
     * @return {@code true} if key entry was deleted, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean deleteSharedKeyData(ContentResolver resolver, String tag) {
        return deleteKeyData(resolver, tag, CONTENT_URI_SHARED_DATA_KEY_CHAIN);
    }

    private static boolean deleteKeyData(ContentResolver resolver, String tag, Uri uri) {
        if (!initialized)
            return false;
        /*
         To delete a key set the selection args:
            selectionArgs[0] = entry name;
         */
        String[] selectionArgs = new String[1];
        selectionArgs[0] = tag;
        int deleted;
        deleted = resolver.delete(uri, null, selectionArgs);
        return deleted == 1;
    }

    /**
     * Create a new application key entry and generate random key data.
     * <p/>
     * The key manager returns an error an application entry with this name already exists.
     * Otherwise it creates an application key entry and fills the key data with secure
     * random data. After storing the key entry the key manager returns the generated key data.
     * <p/>
     * The application must register with the Key manager before it can use this function.
     *
     * @param resolver The ContentResolver
     * @param tag name of the new key entry
     * @param length length of new key data in bytes, must be greater 0 and less or equal 512
     * @return the key data as byte array or {@code null} in case of an error
     */
    public static byte[] randomPrivateKeyData(ContentResolver resolver, String tag, int length) {
        return randomKeyData(resolver, tag, CONTENT_URI_PRIVATE_DATA_KEY_CHAIN_NEW, length);
    }

    private static byte[] randomKeyData(ContentResolver resolver, String tag, Uri uri, int length) {
        if (!initialized)
            return null;

        if (length <= 0 || length > 512)
            return null;
        /*
         To get new key data and store it with tag set the selection args:
            selectionArgs[0] = entry name;
            selectionArgs[1] = length in bytes (converted to String);
         */
        String[] selectionArgs = new String[2];
        selectionArgs[0] = tag;
        selectionArgs[1] = Integer.toString(length);
        Cursor c;
        c = resolver.query(uri, null, null, selectionArgs, null);
        byte[] data = null;
        long token = 0;
        if (c != null) {
            c.moveToFirst();
            String dataStr = c.getString(0);
            if (dataStr == null)
                return null;
            data = Base64.decode(dataStr, Base64.DEFAULT);
            token = c.getLong(1);
            c.close();
        }
        if (token != SupportProvider.getRegisterToken())
            return null;
        return data;
    }

    /**
     * Adds a {@code KeyManagerListener} to the list of listeners.
     *
     * @param l the {@code KeyManagerListener} to add
     */
    public static void addListener(KeyManagerListener l) {
        SupportProvider.addListener(l);
    }

    /**
     * Removes a {@code KeyManagerListener} from the list of listeners.
     *
     * @param l the {@code KeyManagerListener} to remove
     */
    public static void removeListener(KeyManagerListener l) {
        SupportProvider.removeListener(l);
    }

    private static void initialize(String pkgName) {
        if (initialized)
            return;
        initialized = true;
        SupportProvider.initialize(pkgName);
    }
}
