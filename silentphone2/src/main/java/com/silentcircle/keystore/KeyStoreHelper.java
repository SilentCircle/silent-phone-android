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

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.silentcircle.contacts.providers.ScContactsDatabaseHelper;
import com.silentcircle.messaging.services.AxoMessaging;
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
    public static final String PREF_KEY = "uuid_store";
    public static final String UUID_KEY = "uuid";
    public static final String USER_PW = "user_pw";

    public static final int USER_PW_TYPE_NONE = 0;
    public static final int USER_PW_TYPE_PW = 1;
    public static final int USER_PW_TYPE_PIN = 2;

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
        try {
            database = getDatabaseHelper(ctx).getWritableDatabase(password);
            Arrays.fill(password, '\0');
        } catch (Exception e) {
            Log.w(TAG, "Cannot open writable database", e);
            return false;
        }
        ready = true;
        ScContactsDatabaseHelper db = ScContactsDatabaseHelper.getInstance(ctx);
        if (!db.isReady())
            db.onKeyManagerUnlockRequest();

        AxoMessaging axoService = AxoMessaging.getInstance(ctx);
        if (!axoService.isReady())
            axoService.initialize();

        return true;
    }

    static void closeDatabase() {
        ready = false;
        if (database != null)
            database.close();
    }

    public static boolean openWithDefault(Context ctx) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);

        // If user set an own password or pin then don't even try the default key and return false.
        // We cannot open the DB without the user's own password/pin.
        if (prefs.getInt(USER_PW, USER_PW_TYPE_NONE) != USER_PW_TYPE_NONE)
            return false;
        boolean retVal;
        try {
            retVal = openOrCreateDatabase(getDefaultPassword(ctx), ctx);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open key store with default", e);
            return false;
        }
        ProviderDbBackend.sendUnlockRequests();
        return retVal;
    }

    // database.rawExecSQL(String.format("PRAGMA key = '%s'", newPassword);
    // Regarding rawExecSQL refer to: https://github.com/sqlcipher/android-database-sqlcipher/issues/72
    // SQLCipher PRAGMA: http://sqlcipher.net/sqlcipher-api/
    static boolean changePassword(CharSequence data) {
        SQLiteDatabase db = getDatabase();
        if (db == null)       // change only if key store is open
            return false;
        db.rawExecSQL(String.format("PRAGMA rekey = '%s'", data));
        return true;
    }

    @SuppressLint("CommitPrefEdits")
    static char[] getDefaultPassword(Context ctx) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);

        String id = prefs.getString(UUID_KEY, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(UUID_KEY, id).commit();
        }
        char [] defaultPassword = GenerateDefault.generateDefault(id);
        if (defaultPassword == null) {
            defaultPassword = new char[] {'N', 'o', 'N', 'e'};
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Default password: " + new String((defaultPassword)));
        return defaultPassword;
    }

    public static boolean isDefaultAvailable(Context ctx) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        String id = prefs.getString(UUID_KEY, null);
        return (id != null);
    }

    @SuppressLint("CommitPrefEdits")
    static void setUserPasswordType(Context ctx, int type) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putInt(USER_PW, type).commit();
    }

    public static int getUserPasswordType(Context ctx) {
        SharedPreferences prefs =  ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getInt(USER_PW, USER_PW_TYPE_NONE);
    }
}
