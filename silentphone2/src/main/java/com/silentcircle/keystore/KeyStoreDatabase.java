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

import android.content.Context;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by werner on 20.09.13.
 */
public class KeyStoreDatabase extends SQLiteOpenHelper  {

    private static final String TAG = "KeyStoreDatabase";
    private static final String DATABASE_NAME = "sc_keystore.db";
    private static final int DATABASE_VERSION = 1;

    // Table name
    public static final String TABLE = "keys";

    // Columns
    public static final String _ID = "_id";
    public static final String ALIAS = "alias";
    public static final String KEY_DATA = "key_data";

    private static Context context;
    private static KeyStoreDatabase sSingleton = null;

    public static synchronized KeyStoreDatabase getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new KeyStoreDatabase(context);
        }
        return sSingleton;
    }

    public KeyStoreDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "create table " + TABLE + "( " + ALIAS + " text not null, " + KEY_DATA + " blob not null);";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpdate, old version: " + oldVersion + ", new version: " + newVersion);
//        if (oldVersion >= newVersion)
//            return;
//
//        String sql = null;
//        if (oldVersion == 1)
//            sql = "alter table " + TABLE + " add note text;";
//        if (oldVersion == 2)
//            sql = "";
//
//        Log.d(TAG, "onUpgrade  : " + sql);
//        if (sql != null)
//            ;// db.execSQL(sql);
    }

    static boolean isDbFileAvailable() {
        File dbFile = context.getDatabasePath(DATABASE_NAME);
        if (!dbFile.exists() || !dbFile.canWrite()) {
            return false;
        }
        return true;
    }

    /**
     * Open input stream to read the binary key store database file.
     *
     * Convenience function that mainly covers the various exceptions and handles them.
     *
     * @return {@code FileInputStream} or {@code null} in case of error.
     */
    private static FileInputStream openInKeyStoreStream() {
        File dbFile = context.getDatabasePath(DATABASE_NAME);
        FileInputStream ksIn = null;
        try {
            ksIn = new FileInputStream(dbFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot find key store database for KeyManager", e);
        }
        return ksIn;
    }

    static boolean backupStoreTo(File to) {
        FileInputStream storeIn = openInKeyStoreStream();
        if (storeIn == null)
            return false;

        FileOutputStream ksOut = null;
        try {
            ksOut = new FileOutputStream(to);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot create backup file.", e);
        }
        if (ksOut == null)
            return false;

        byte[] buffer = new byte[8*1024];
        int read;
        try {
            while((read = storeIn.read(buffer)) != -1) {
                ksOut.write(buffer, 0, read);
            }
        } catch (IOException e) {
            try {
                storeIn.close();
                ksOut.close();
            } catch (IOException e1) {
                to.delete();
                return false;
            }
            to.delete();
            return false;
        }

        try {
            storeIn.close();
        } catch (IOException ignore) {}

        try {
            ksOut.close();
//  This may causes a IOEception "bad file number":  ksOut.getFD().sync();
        } catch (IOException e) {
            to.delete();
            return false;
        }
        return true;
    }

    static boolean restoreStoreFrom(File from) {
        String path = context.getDatabasePath(DATABASE_NAME+ ".restore").getPath();

        File outDir = new File(path);
        if (!outDir.exists())
            outDir.getParentFile().mkdirs();

        FileOutputStream ksOut;
        try {
            ksOut = new FileOutputStream(outDir);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot open restore temp file for key manager", e);
            return false;
        }

        FileInputStream ksIn;
        try {
            ksIn = new FileInputStream(from);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot open backup file for restore operation", e);
            return false;
        }

        byte[] buffer = new byte[8*1024];
        int read;
        try {
            while((read = ksIn.read(buffer)) != -1) {
                ksOut.write(buffer, 0, read);
            }
        } catch (IOException e) {
            try {
                ksIn.close();
                ksOut.close();
            } catch (IOException e1) {
                outDir.delete();    // remove temp file in case of problems
                return false;
            }
            outDir.delete();
            return false;
        }
        try {
            ksIn.close();
        } catch (IOException ignore) {}

        try {
            ksOut.close();
//  This may causes a IOEception "bad file number":  ksOut.getFD().sync();
        } catch (IOException e) {
            outDir.delete();
            return false;
        }
        // At this point the restore temp file is ready, rename it to real store file
        if (!outDir.renameTo(context.getDatabasePath(DATABASE_NAME))) {
            Log.e(TAG, "Restore from backup file: rename to key store name failed");
            return false;
        }
        return true;
    }

}
