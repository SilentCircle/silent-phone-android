/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.silentcircle.logs.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;

import static com.silentcircle.silentphone2.util.Utilities.bytesToHexChars;


/**
 * Helper class to generate a secure password for SPA key store.
 *
 * The class loads (or creates a random key at first) a key from the AndroidKeyStore and uses
 * this key to generate a password for the SPA key store. The class uses this key to encrypt
 * some public and persistent random data. The key to perform the encryption is a random key
 * and not readable outside the HW backed AndroidStore, also the encryption takes place in HW.
 *
 * Created by werner on 22.02.17.
 */
class GenerateSecure {

    private static final String TAG = "GenerateSecure";

    private static final String KEY_STORE_PROVIDER = "AndroidKeyStore";

    private static final String SPA_SECURE_KEY = "spa_key_1";

    //                                           "SilentCircleSecu";     // Also used as IV, must be 16 chars (bytes)
    private static final byte[] SILENT_CIRCLE = {0x53, 0x69, 0x6c, 0x65, 0x6e, 0x74, 0x43, 0x69, 0x72, 0x63, 0x6c, 0x65, 0x53, 0x65, 0x63, 0x75};
    private static final int BIN_KEY_LENGTH = 32;                       // 32 bytes of binary key/password
    private static boolean isInsideSecureHardware;

    @Nullable
    static char[] generatePassword(@NonNull String s) {
        SecretKey key = loadSecureKey();
        if (key == null) {
            key = createSecureKey();
            if (key == null) {
                return null;
            }
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            return null;
        }
        digest.reset();
        digest.update(SILENT_CIRCLE);
        digest.update(s.getBytes());
        final byte[] data = digest.digest();

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            if ((data.length % cipher.getBlockSize()) != 0) {
                android.util.Log.e(TAG, "Wrong hash size for encryption mode.");
                return null;
            }
            isInsideSecureHardware = isSecureHardware(key);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(SILENT_CIRCLE));
            final byte[] secKey = cipher.doFinal(data);
            char[] retChars = bytesToHexChars(secKey);

            if (retChars.length != BIN_KEY_LENGTH * 2) {
                android.util.Log.e(TAG, "Wrong key length size for encryption mode.");
                return null;
            }
            Arrays.fill(secKey, (byte)0);
            return retChars;

        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException |
                InvalidAlgorithmParameterException | InvalidKeyException e) {
            Log.e(TAG, "Failed to create secure key for key store", e);
        }
        return null;
    }

    /**
     * Check if the key used to generate the secure password is inside secure hardware.
     *
     * @return {@code true} if inside secure hardware
     */
    public static boolean isKeyInsideSecureHardware() {
        return isInsideSecureHardware;
    }

    @Nullable
    private static SecretKey loadSecureKey() {
        try {
            // The key can also be obtained from the Android Keystore any time as follows:
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return (SecretKey) keyStore.getKey(SPA_SECURE_KEY, null);
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException e) {
            Log.e(TAG, "Failed to load secure key in AndroidKeystore", e);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Nullable
    private static SecretKey createSecureKey() {
        SecretKey key;
        KeyGenerator keyGenerator;

        try {
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_PROVIDER);
            keyGenerator.init(
                    new KeyGenParameterSpec.Builder(SPA_SECURE_KEY, KeyProperties.PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(false)
                    .build());
            key = keyGenerator.generateKey();

            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER);
            keyStore.load(null);
            keyStore.setKeyEntry(SPA_SECURE_KEY, key, null, null);

        } catch (NoSuchAlgorithmException | IOException | KeyStoreException | CertificateException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Failed to create and store secure key in AndroidKeyStore", e);
            return null;
        }
        return key;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static boolean isSecureHardware(SecretKey key) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        SecretKeyFactory factory;
        KeyInfo keyInfo;
        try {
            factory = SecretKeyFactory.getInstance(key.getAlgorithm(), KEY_STORE_PROVIDER);
            keyInfo = (KeyInfo)factory.getKeySpec(key, KeyInfo.class);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
            return false;
        }
        return keyInfo.isInsideSecureHardware();
    }
}
