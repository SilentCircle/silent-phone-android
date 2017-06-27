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
package com.silentcircle.keystore;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.silentcircle.contacts.providers.ScCallLogDatabaseHelper;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.UUID;

/**
 * The KeyService is a simple class that provides long term storage and
 * some helper functions.
 *
 * In Android Services usually live as long as the applications live and also
 * keeps the application alive. Refer to the relevant lifecycle documentation.
 *
 * Android manages the lifecycle of content providers and a content provider does
 * not offer an 'onDestroy()' method, only 'onCreate()'.
 *
 * Created by werner on 31.08.13.
 */
public class KeyStoreHelper {

    private static String TAG = "KeyStoreHelper";
    private static final String PREF_KEY = "uuid_store";
    private static final String UUID_KEY = "uuid";
    private static final String USER_PW = "user_pw";

    public static final int USER_PW_TYPE_NONE = 0;
    public static final int USER_PW_TYPE_PW = 1;
    public static final int USER_PW_TYPE_PIN = 2;
    public static final int USER_PW_SECURE = 3;

    private static ContentResolver mContentResolver;

    private static boolean ready;

    static class AppInfo {
        long token;
        String displayName;
    }

    private static Hashtable<String, AppInfo> registeredApps = new Hashtable<>(5, 0.75f);
    private static SQLiteDatabase database;

    private static KeyStoreDatabase getDatabaseHelper(final Context context) {
        return KeyStoreDatabase.getInstance(context);
    }

    protected static SQLiteDatabase getDatabase() {
        return (ready) ? database : null;
    }

    private static boolean initialized;
    private static void initialize(Context ctx) {
        if (initialized)
            return;
        initialized = true;

        if (mContentResolver == null) {
            mContentResolver = ctx.getContentResolver();
        }
        //you must set Context on SQLiteDatabase first
        SQLiteDatabase.loadLibs(ctx);
    }

    public static void closeStore() {
        if (!ready)
            return;
        ProviderDbBackend.sendLockRequests();                // closeStore is an 'implicit' lock
        closeDatabase();
    }

    public static boolean isReady() {
        return ready;
    }

    static ContentResolver getContentResolver() {
        return mContentResolver;
    }

    static Hashtable<String, AppInfo> getRegisteredApps() {
        return registeredApps;
    }

    static boolean openOrCreateDatabase(char[] password, Context ctx) {
        initialize(ctx);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Open keystore: " + new String(password));

        try {
            database = getDatabaseHelper(ctx).getWritableDatabase(password);
            Arrays.fill(password, '\0');
        } catch (Exception e) {
            Log.w(TAG, "Cannot open writable database", e);
            return false;
        }
        ready = true;
        ScCallLogDatabaseHelper db = ScCallLogDatabaseHelper.getInstance(ctx);
        if (!db.isReady())
            db.onKeyManagerUnlockRequest();

        ZinaMessaging axoService = ZinaMessaging.getInstance();
        if (!axoService.isReady())
            axoService.initialize();

        return true;
    }

    static void closeDatabase() {
        ready = false;
        if (database != null)
            database.close();
    }

    static boolean openWithDefault(Context ctx) {
        // If user set an own password or pin then don't even try the default key and return false.
        // We cannot open the DB without the user's own password/pin.
        int pwType = getUserPasswordType(ctx);
        if (!(pwType == USER_PW_TYPE_NONE || pwType == USER_PW_SECURE)) {
            return false;
        }
        boolean retVal = false;
        try {
            if (pwType == USER_PW_TYPE_NONE) {
                retVal = openOrCreateDatabase(getDefaultPassword(ctx), ctx);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                char[] secPassword = getSecurePassword(ctx);
                if (secPassword != null) {
                    if (pwType == USER_PW_SECURE) {
                        // openOrCreateDatabase clears password data right after usage
                        retVal = openOrCreateDatabase(combinedBinCharKey(secPassword), ctx);
                    }
                    else if (changePasswordBin(secPassword)) {
                        setUserPasswordType(ctx, USER_PW_SECURE);
                        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Switched from default to secure password");
                    }
                    Arrays.fill(secPassword, '\0');
                }
                else {
                    Log.e(TAG, "Cannot get new secure password, stay with default.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open key store with default", e);
            return false;
        }
        ProviderDbBackend.sendUnlockRequests();
        return retVal;
    }

    // Create a correct char array to open database with the secure password / key
    private static char[] combinedBinCharKey(final char[] binKey) {
        char[] combinedKey = new char[binKey.length + 3];
        combinedKey[0] = 'x';
        combinedKey[1] = '\'';
        System.arraycopy(binKey, 0, combinedKey, 2, binKey.length);
        combinedKey[combinedKey.length-1] = '\'';
        return combinedKey;
    }

    /**
     * Re-key the key store database.
     *
     * The secure password is a user provided string and sqlcipher uses some PBKDF to
     * generate a key to encrypt the database.
     *
     * @param password A user provided password string
     * @return {@code true} if re-keying was successful
     */
    public static boolean changePassword(@NonNull CharSequence password) {
        SQLiteDatabase db = getDatabase();
        if (db == null)       // change only if key store is open
            return false;
        db.changePassword(password.toString());
        return true;
    }

    /**
     * Re-key the key store database.
     *
     * The secure password data must be a character array of 64 hex-decimal characters which
     * represent the secure password/key generated via GenerateSecure.generatePassword().
     *
     * Refer to https://github.com/guardianproject/sqlcipher-android for detailed information
     * about the hex-decimal string.
     *
     * The function constructs the correct string and calls sqlcipher's re-key function
     * and then clears the internal data. It does not clear the input character array.
     *
     * @param securePw the 64 character long password string
     * @return {@code true} if re-keying was successful
     */
    public static boolean changePasswordBin(@NonNull char[] securePw) {
        SQLiteDatabase db = getDatabase();
        if (db == null || securePw.length != 64)  // change only if key store is open and data has correct length
            return false;
        final char[] combinedPw = combinedBinCharKey(securePw);
        db.changePassword(combinedPw);
        Arrays.fill(combinedPw, '\0');
        return true;
    }

    @NonNull
    public static char[] getDefaultPassword(Context ctx) {
        String id = getUuidFromPref(ctx);
        char [] defaultPassword = GenerateDefault.generateDefault(id);
        if (defaultPassword == null) {
            defaultPassword = new char[] {'N', 'o', 'N', 'e'};
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Default password: " + new String((defaultPassword)));
        return defaultPassword;
    }

    @SuppressLint("CommitPrefEdits")
    @NonNull
    private static String getUuidFromPref(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);

        String id = prefs.getString(UUID_KEY, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(UUID_KEY, id).commit();
        }
        return id;
    }

    @Nullable
    public static char[] getSecurePassword(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        String id = getUuidFromPref(ctx);
        char [] defaultPassword = GenerateSecure.generatePassword(id);

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Secure key is inside secure hardware: " + GenerateSecure.isKeyInsideSecureHardware());
        if (defaultPassword != null && ConfigurationUtilities.mTrace) Log.d(TAG, "Secure password: " + new String((defaultPassword)));
        return defaultPassword;
    }

    @SuppressWarnings("unused")
    public static boolean isDefaultAvailable(Context ctx) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        String id = prefs.getString(UUID_KEY, null);
        return (id != null);
    }

    @SuppressLint("CommitPrefEdits")
    public static void setUserPasswordType(Context ctx, int type) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putInt(USER_PW, type).commit();
    }

    public static int getUserPasswordType(Context ctx) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getInt(USER_PW, USER_PW_TYPE_NONE);
    }
}
