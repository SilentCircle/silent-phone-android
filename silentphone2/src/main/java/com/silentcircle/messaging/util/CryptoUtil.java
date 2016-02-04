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

package com.silentcircle.messaging.util;

import android.util.Log;

import java.math.BigInteger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper functions to encrypt/decrypt data.
 *
 * Created by werner on 23.05.15.
 */
public class CryptoUtil {
    private static final String TAG = "CryptoUtil";

    public static final int AES_BLOCK_SIZE = 16;
    public static final Random RANDOM = new SecureRandom();


    public static byte[] decrypt(Key key, byte[] data) {
        if( data == null ) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init( Cipher.DECRYPT_MODE, key, new IvParameterSpec(data, 0, AES_BLOCK_SIZE ) );
            return cipher.doFinal(data, AES_BLOCK_SIZE, data.length - AES_BLOCK_SIZE);
        } catch( Exception exception ) {
            throw new RuntimeException( exception );
        }
    }

    public static byte[] encrypt(Key key, byte[] data) {
        if (data == null) {
            return null;
        }
        byte[] IV = new byte[AES_BLOCK_SIZE];
        RANDOM.nextBytes(IV);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(IV));
            byte[] encryptData = cipher.doFinal(data);
            return IOUtils.concat(IV, encryptData);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
    public static void randomize(byte[] data) { } //TODO

    public static void randomize(double[] data) { } //TODO

    public static byte [] randomBytes(int byteSize) {
        byte [] byteArray = new byte[byteSize];

        RANDOM.nextBytes(byteArray);

        return byteArray;
    }

    public static int randomInt(int byteSize) {
        return new BigInteger(randomBytes(byteSize)).intValue();
    }

    public static CipherOutputStream getCipherOutputStream(File file, byte[] keyData, byte[] ivData) {
        if (file == null || keyData == null || ivData == null)
            return null;

        if (ivData.length != AES_BLOCK_SIZE)
            return null;

        if (!(keyData.length == 16 || keyData.length == 24 || keyData.length == 32))
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


    public static CipherInputStream getCipherInputStream(File file, byte[] keyData, byte[] ivData) {
        if (file == null || keyData == null || ivData == null)
            return null;

        if (ivData.length != AES_BLOCK_SIZE)
            return null;

        if (!(keyData.length == 16 || keyData.length == 24 || keyData.length == 32))
            return null;

        SecretKeySpec keySpec =  new SecretKeySpec(keyData, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivData));
            return new CipherInputStream(new FileInputStream(file), cipher);
        } catch (Exception e) {
            Log.e(TAG, "Cannot create CipherOutputFile.", e);
        }
        return null;
    }
}
