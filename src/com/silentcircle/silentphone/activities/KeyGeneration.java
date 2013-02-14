/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

// NOTE: 
// This Activity is disabled until further notice. Works only
// for Android >= API 14. To enable it remove the dummy class below
// and un-comment the other code. The functions were tested with
// Android > 4 and work as expected.

package com.silentcircle.silentphone.activities;

//import java.io.ByteArrayOutputStream;
//import java.math.BigInteger;
//import java.security.KeyPair;
//import java.security.KeyPairGenerator;
//import java.security.KeyStore;
//import java.security.NoSuchAlgorithmException;
//import java.security.NoSuchProviderException;
//import java.security.PrivateKey;
//import java.security.PublicKey;
//import java.security.cert.Certificate;
//import java.security.cert.X509Certificate;
//import java.security.spec.InvalidKeySpecException;
//import java.util.Date;
//
//import javax.crypto.Cipher;
//
//import org.bouncycastle.asn1.x500.X500NameBuilder;
//import org.bouncycastle.asn1.x500.style.BCStyle;
//import org.bouncycastle.cert.X509v1CertificateBuilder;
//import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
//import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
//import org.bouncycastle.crypto.digests.SHA256Digest;
//import org.bouncycastle.crypto.macs.HMac;
//import org.bouncycastle.crypto.params.KeyParameter;
//import org.bouncycastle.operator.ContentSigner;
//import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
//import org.bouncycastle.util.Arrays;

import com.silentcircle.silentphone.R;
//import com.silentcircle.silentphone.TiviPhoneService;
//
//import android.os.AsyncTask;
import android.os.Bundle;
//import android.preference.PreferenceManager;
//import android.security.KeyChain;
//import android.security.KeyChainAliasCallback;
//import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
//import android.support.v4.app.FragmentManager;
//import android.app.Activity;
//import android.app.AlertDialog;
//import android.app.Dialog;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.util.Log;
import android.view.Menu;
//import android.view.View;


public class KeyGeneration extends FragmentActivity {
    
    public static final String NEW_PROVISIONING = "NEW_PROVISIONING";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_generation);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_key_generation, menu);
        return true;
    }    
}

//public class KeyGeneration extends FragmentActivity implements KeyChainAliasCallback {
//
//    private static final String LOG_TAG = "KeyGeneration";
//    private static final String BC = org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
//    
//    private static final String KEY_ALIAS_NAME = "SilentPhone";
//    public static final String NEW_PROVISIONING = "NEW_PROVISIONING";
//
//    private static final String SECRET_KEY_INITALIZED = "SECRET_KEY_INIATLIZED";
//    private static final int KEY_CHAIN_RESULT = 815;
//
////  "SilentPhone secret key\0"
//    public static final byte[] spSecretKey = {
//        (byte)0x53, (byte)0x69, (byte)0x6c, (byte)0x65, (byte)0x6e, (byte)0x74, (byte)0x50, (byte)0x68,
//        (byte)0x6f, (byte)0x6e, (byte)0x65, (byte)0x20, (byte)0x73, (byte)0x65, (byte)0x63, (byte)0x72,
//        (byte)0x65, (byte)0x74, (byte)0x20, (byte)0x6b, (byte)0x65, (byte)0x70, (byte)0x0};
//
//    private HMac hmacFunction = new HMac(new SHA256Digest());
//    public static final int SHA256_DIGEST_LENGTH = 32;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_key_generation);
//        
//        boolean newProvisioning = getIntent().getBooleanExtra(NEW_PROVISIONING, false);
//        if (newProvisioning)
//            setInitialized(false);
//
//        if (!isInitialized()) {
//            generateAndStoreKeyData();
//        }
//        else {
//            getSecretKey();
//        }
//    }
//
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.activity_key_generation, menu);
//        return true;
//    }
//
//    /**
//     * Used for test trigger only.
//     * 
//     * @param v
//     */
//    public void keyGenTest(View v) {
////        generateAndStoreKeyData();
////        if (isInitialized()) {
////            getSecretKey();
////            setInitialized(false);
////        }
//    }
//
//    private void setInitialized(boolean yesNo) {
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        SharedPreferences.Editor edit = prefs.edit();
//        edit.putBoolean(SECRET_KEY_INITALIZED, yesNo);
//        edit.commit();
//    }
//
//    private boolean isInitialized() {
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        return prefs.getBoolean(SECRET_KEY_INITALIZED, false);
//    }
//
//    private byte[] privateData;
//
//    private void generateAndStoreKeyData() {
//        final Context ctx = this;
//        Runnable r = new Runnable() {
//            public void run() {
//                try {
//                    new AsyncTask<Void, Void, Void>() {
//
//                        @Override
//                        protected Void doInBackground(Void... arg0) {
//                            // Generate the key pair
//                            if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "keygen start: " + System.currentTimeMillis());
//                            byte[] pkcs12 = null;
//
//                            try {
//                                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", BC);
//                                keyGen.initialize(2048);
//                                KeyPair key = keyGen.generateKeyPair();
//                                PrivateKey privKey = key.getPrivate();
//
//                                Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding");
//                                cipher.init(Cipher.ENCRYPT_MODE, privKey);
//                                privateData = cipher.doFinal(spSecretKey);
//                                if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "cipher length (0): " + privateData.length);
//
//                                pkcs12 = KeyGeneration.create(key);
//                            }
//                            catch (NoSuchAlgorithmException e) {
//                                Log.e(LOG_TAG, "Error creating secret key.", e);
//                                showErrorInfo(getString(R.string.key_generation_error_key_generation) + e.getMessage());
//                                return null;
//                            }
//                            catch (NoSuchProviderException e) {
//                                Log.e(LOG_TAG, "Error creating secret key.", e);
//                                showErrorInfo(getString(R.string.key_generation_error_key_generation) + e.getMessage());
//                                return null;
//                            }
//                            catch (InvalidKeySpecException e) {
//                                Log.e(LOG_TAG, "Error creating secret key.", e);
//                                showErrorInfo(getString(R.string.key_generation_error_key_generation) + e.getMessage());
//                                return null;
//                           }
//                            catch (Exception e) {
//                                Log.e(LOG_TAG, "Error creating secret key.", e);
//                                showErrorInfo(getString(R.string.key_generation_error_key_generation) + e.getMessage());
//                                return null;
//                            }
//                            if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "keygen stop: " + System.currentTimeMillis());
// 
//                            KeyGeneration.this.storePkcs12(KeyGeneration.this, pkcs12);
//                            return null;
//                        }
//                    }.execute();
//                }
//                catch (Exception e) {
//                    Log.e(LOG_TAG, "Error creating secret key.", e);
//                    showErrorInfo(getString(R.string.key_generation_error_key_generation) + e.getMessage());
//                    return;
//                }
//            }
//        };
//        runOnUiThread(r);
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (requestCode == KEY_CHAIN_RESULT) {
//            if (resultCode != RESULT_OK) {
//                Log.e(LOG_TAG, "Could not store the generated secret key!");
//                Arrays.fill(privateData, (byte)0);
//                setInitialized(false);
//                showErrorInfo(getString(R.string.key_generation_storage_fail));
//                return;
//            }
//            // Secret key was successfully stored, generate the key via KDF using the private exponent, 
//            // store in service, etc, then finish this activity
//            if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "Stored the generated private key!");
//
//            byte[] key = KDF(privateData, spSecretKey, /* KDFcontext, */ 256);
//            TiviPhoneService.setSecretKey(key);
//            Arrays.fill(privateData, (byte)0);
//            setInitialized(true);
//            setResult(Activity.RESULT_OK);
//            finish();
//            return;
//        }
//    }
//
//    private void storePkcs12(Activity act, byte[] p12) {
//        Intent intent = KeyChain.createInstallIntent();
//        intent.putExtra(KeyChain.EXTRA_PKCS12, p12);
//        act.startActivityForResult(intent, KEY_CHAIN_RESULT);
//        Log.d(LOG_TAG, "PKCS12 store intent sent");
//    }
//
//    private void getSecretKey() {
//        KeyChain.choosePrivateKeyAlias(this, this, new String[] { KEY_ALIAS_NAME }, null, null, -1, null);
//    }
//
//    @Override
//    public void alias(final String alias) {        
//        // Check if user does not permitted key usage, or deleted KeyChain store, ...
//        if (alias == null && isInitialized()) {
//            // Show a nice information, then finish if user closes the Alert dialog
//            showErrorInfo(getString(R.string.key_generation_missing_key));
//            return;
//        }
//
//        if (!KEY_ALIAS_NAME.equals(alias)) {
//            // Show a nice information, then finish if user closes the Alert dialog
//            showErrorInfo(getString(R.string.key_generation_wrong_key));
//            return;
//        }
//
//        final Context ctx = this;
//        Runnable r = new Runnable() {
//            public void run() {
//                new AsyncTask<Void, Void, Void>() {
//                    @Override
//                    protected Void doInBackground(Void... arg0) {
//                        try {
//                            PrivateKey privKey = KeyChain.getPrivateKey(ctx, alias);
//                            Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding");
//                            cipher.init(Cipher.ENCRYPT_MODE, privKey);
//                            privateData = cipher.doFinal(spSecretKey);
//                            if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "cipher length (1): " + privateData.length);
//
//                            // Generate the key via KDF, store in service, etc, then finish this activity
//                            byte[] key = KDF(privateData, spSecretKey, /* KDFcontext, */ 256);
//                            TiviPhoneService.setSecretKey(key);
//                            Arrays.fill(privateData, (byte)0);
//                            setResult(Activity.RESULT_OK);
//                            finish();
//                            return null;
//                        }
//                        catch (Exception e) {
//                            Log.e(LOG_TAG, "Error reading private key", e);
//                            showErrorInfo(getString(R.string.key_generation_error_key));
//                            return null;
//                        }
//                    }
//                }.execute();
//            }
//        };
//        runOnUiThread(r);
//    }
//
//    private void showErrorInfo(String msg) {
//        errMessage = msg;
//        ErrorMsgDialogFragment errMsg = new ErrorMsgDialogFragment();
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        errMsg.show(fragmentManager, "SilentPhoneKeyGenError");
//    }
//    
//    /**
//     * Cancel key generation because of an error.
//     * 
//     * Finish activity, main activity to decide what to do - usually terminates whole application.
//     */
//    public void keyGenCancel(View view) {
//        setResult(Activity.RESULT_CANCELED);
//        finish();
//    }
//
//
//    private static char[] passwd = { ' ' };
//
//    /**
//     * Use the key pair to create an associated certificate and return the PKCS#12 keystore data.
//     */
//    static private byte[] create(KeyPair key) throws Exception {
//
//        PrivateKey privKey = key.getPrivate();
//        PublicKey pubKey = key.getPublic();
//
//        // Set distinguished name table for SilentPhone
//        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
//
//        builder.addRDN(BCStyle.C, "US");
//        builder.addRDN(BCStyle.O, "Silent Circle LLC");
//        builder.addRDN(BCStyle.L, "National Harbor");
//        builder.addRDN(BCStyle.ST, "MD_20745");
//        builder.addRDN(BCStyle.E, "support@silentcircle.com");
//
//        // create a version 1 certificate
//        ContentSigner sigGen = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider(BC).build(privKey);
//        X509v1CertificateBuilder certGen1 = new JcaX509v1CertificateBuilder(builder.build(), 
//                BigInteger.valueOf(1), new Date(System.currentTimeMillis() - 50000), 
//                new Date(System.currentTimeMillis() + 50000), builder.build(), pubKey);
//
//        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certGen1.build(sigGen));
//
//        Certificate[] chain = new Certificate[1];
//        chain[0] = cert;
//
//        // Get an empty PKCS12 key store to store the key and the certificate chain
//        KeyStore store = KeyStore.getInstance("PKCS12", BC);
//        store.load(null, null);
//
//        // the name will be the alias name of the key
//        store.setKeyEntry(KEY_ALIAS_NAME, privKey, null, chain);
//
//        ByteArrayOutputStream baOut = new ByteArrayOutputStream();
//
//        store.store(baOut, passwd);
//        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "PKCS#12 key store created");
//
//        return baOut.toByteArray();
//    }
//
//    /*
//     * A KDF function as per ZRTP specification 4.5.1 - modified with regard to context
//     */
//    private byte[] KDF(byte[] ki, byte[] label, /* byte[] context,*/ int L) {
//        KeyParameter key = new KeyParameter(ki, 0, hmacFunction.getMacSize());
//        hmacFunction.init(key);
//
//        byte[] counter = int32ToArray(1);
//        hmacFunction.update(counter, 0, 4);
//        hmacFunction.update(label, 0, label.length); // the label includes the 0 byte separator
////        hmacFunction.update(context, 0, context.length);
//        byte[] length = int32ToArray(L);
//        hmacFunction.update(length, 0, 4);
//
//        byte[] retval = new byte[hmacFunction.getMacSize()];
//        hmacFunction.doFinal(retval, 0);
//        return retval;
//    }
//
//    /**
//     * Convert a 32 bit integer into a byte array, network order.
//     * 
//     * @param data the 32 bit integer
//     * @return the byte array containing the converted integer
//     */
//    private static byte[] int32ToArray(int data) {
//        byte[] output = new byte[4];
//        output[0]  = (byte) (data >> 24);
//        output[1]  = (byte) (data >> 16);
//        output[2] = (byte) (data >> 8);
//        output[3] = (byte) data;
//        return output;
//    }
//
//    private static String errMessage;
//    public static class ErrorMsgDialogFragment extends DialogFragment {
//        @Override
//        public Dialog onCreateDialog(Bundle savedInstanceState) {
//            // Use the Builder class for convenient dialog construction
//            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//            builder.setTitle(getString(R.string.key_generation_error))
//                .setMessage(errMessage)
//                .setPositiveButton(getString(R.string.close_dialog), new DialogInterface.OnClickListener() {
//                       public void onClick(DialogInterface dialog, int id) {
//                           ((KeyGeneration)getActivity()).keyGenCancel(null);
//                       }
//                   });
//            // Create the AlertDialog object and return it
//            return builder.create();
//        }
//    }
//}
