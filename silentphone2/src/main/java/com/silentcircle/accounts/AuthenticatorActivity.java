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

package com.silentcircle.accounts;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * This Activity controls the creation of an account.
 *
 * Created by werner on 09.04.15.
 */
public class AuthenticatorActivity extends ActionBarActivity {
    private AccountAuthenticatorResponse mAccountAuthenticatorResponse;
    private Bundle mResultBundle;

    private final static String TAG = "AuthenticatorActivity";
    public final static String ARG_ACCOUNT_TYPE = "ACCOUNT_TYPE";
    public final static String ARG_AUTH_TYPE = "AUTH_TYPE";
    public final static String ARG_ACCOUNT_NAME = "ACCOUNT_NAME";
    public final static String ARG_IS_ADDING_NEW_ACCOUNT = "IS_ADDING_ACCOUNT";
    public final static String ARG_RONIN_CODE = "ronin_code";
    public final static String ARG_OPTIONS_BUNDLE = "options_bundle";
    public final static String ARG_STEP1 = "arg_step1";

    private static final int KEY_STORE_CHECK = 1004;

    private AccountManager mAccountManager;
    private Bundle mBlackPhoneArgs;
    private Bundle mOptions;
    private boolean mAddAccountEntry;

    private final Bundle mUserData = new Bundle();

    private String mDeviceId;
    private JSONObject mJsonHolder;


    /*
     * Copy code from Android's AccountAuthenticatorActivity into this class because we don't
     * extend that class but the ActionBarActivity class to have full access to action bar/toolbar
     */

    /**
     * Set the result that is to be sent as the result of the request that caused this
     * Activity to be launched. If result is null or this method is never called then
     * the request will be canceled.
     * @param result this is returned as the result of the AbstractAccountAuthenticator request
     */
    public final void setAccountAuthenticatorResult(Bundle result) {
        mResultBundle = result;
    }

    /**
     * Retrieves the AccountAuthenticatorResponse from either the intent of the icicle, if the
     * icicle is non-zero.
     * @param icicle the save instance data of this Activity, may be null
     */
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mAccountAuthenticatorResponse =
                getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

        if (mAccountAuthenticatorResponse != null) {
            mAccountAuthenticatorResponse.onRequestContinued();
        }

        final Intent intent = getIntent();
        mOptions = intent.getBundleExtra(ARG_OPTIONS_BUNDLE);
        mAccountManager = AccountManager.get(getBaseContext());
        mAddAccountEntry = intent.getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false);
        checkAndSetKeyManager();
    }

    /**
     * Sends the result or a Constants.ERROR_CODE_CANCELED error if a result isn't present.
     */
    public void finish() {
        if (mAccountAuthenticatorResponse != null) {
            // send the result bundle back if set, otherwise send an error.
            if (mResultBundle != null) {
                mAccountAuthenticatorResponse.onResult(mResultBundle);
            }
            else {
                mAccountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
            mAccountAuthenticatorResponse = null;
        }
        super.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == KEY_STORE_CHECK) {
            if (resultCode != RESULT_OK) {
                Log.w(TAG, "KeyManager READY request failed - exit.");
                finish();
            }
            else {
                keyManagerChecked();
            }
        }
    }
        // We may need to enhance the "checkKeyManager" process in case we use keys to encrypt
    // the config files. Then we may need to check more often and at different places, needs to
    // be investigated.
    private void checkAndSetKeyManager() {
        final long token = KeyManagerSupport.registerWithKeyManager(getContentResolver(), getPackageName(), getString(R.string.app_name));
        if (token == 0) {
            Log.w(TAG, "Cannot register with KeyManager.");
            finish();
        }
        startActivityForResult(KeyManagerSupport.getKeyManagerReadyIntent(), KEY_STORE_CHECK);
    }

    // This is the second step of the of onCreate flow.
    // The key store is ready and open. Let's check if we already have some data or if we need to run the full
    // provisioning at this point.
    @SuppressLint("CommitPrefEdits")
    private void keyManagerChecked() {
        final Intent intent = getIntent();

        byte[] data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());

        // If data != null then this client/device already created an account on the server and got all
        // necessary data, i.e. API-key and stored it.
        // - if 'addAccountEntry' is false then ScAuthenticator#getAuthToken created this Intent.
        // - if it's a privileged, i.e. Silent Circle, app then return the authorization and device id strings.
        // - if 'mAddAccountEntry' is true then this is a "migration": SPA has data available and just adds
        //   a local account entry (with the device's AccountManager).
        if (data != null) {
            try {
                String accountName = mOptions.getString(AccountConstants.SC_ACCOUNT_NAME);
                if (accountName == null && mAddAccountEntry) {
                    // If user selected to re-provision SPA then the user name was saved in preferences
                    final SharedPreferences prefs =  getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
                    final String name = prefs.getString(ConfigurationUtilities.getReprovisioningNameKey(), null);
                    if (!TextUtils.isEmpty(name)) {
                        prefs.edit().remove(ConfigurationUtilities.getReprovisioningNameKey()).commit();
                        accountName = name;
                    }
                    else {
                        showErrorInfo(getString(R.string.account_missing_name));
                        return;
                    }
                }

                String devAuthorization = new String(data, "UTF-8").trim();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Authentication data (API key) : " + devAuthorization);

//                data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardDevIdTag());
//                if (data == null) {
//                    showErrorInfo(getString(R.string.provisioning_no_data));
//                    return;
//                }
//                mDeviceId = new String(data, "UTF-8").trim();

                // Compute device id from existing data, i.e. don't create a new instance dev id
                // because this is a re-provisioning only
                mDeviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(this, false));
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Shared deviceId : " + mDeviceId);

                final Bundle result = new Bundle();
                result.putString(AccountManager.KEY_ACCOUNT_NAME, accountName);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, intent.getStringExtra(ARG_ACCOUNT_TYPE));
                if (checkPermissions(mOptions, this)) {
                    mUserData.putString(AccountConstants.SC_AUTHORIZATION, devAuthorization);
                    mUserData.putString(AccountConstants.DEVICE_ID, mDeviceId);
                    result.putBundle(AccountManager.KEY_USERDATA, mUserData);
                }

                final Intent res = new Intent();
                res.putExtras(result);
                prepareResult(res);
                return;
            } catch (UnsupportedEncodingException ignored) {}
        }

        // No stored account/provisioning info. Ask user about credentials etc to read from server.
        setContentView(R.layout.activity_provisioning);
        final Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        toolbar.setVisibility(View.GONE);
        setSupportActionBar(toolbar);

        setResult(Activity.RESULT_CANCELED);        // assume failure unless provisioning threads set it to OK
        mDeviceId = TiviPhoneService.getInstanceDeviceId(this, true);

        if (mDeviceId != null) {
            mDeviceId = Utilities.hashMd5(mDeviceId);
        }
        if (mDeviceId == null) {
            finish();
            return;
        }

        String feature = mOptions.getString("feature_code", null);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Got a feature code, BP provisioning: " + feature);
        mBlackPhoneArgs = new Bundle();
        mBlackPhoneArgs.putString(ARG_RONIN_CODE, feature);
        mBlackPhoneArgs.putString(ProvisioningActivity.DEVICE_ID, mDeviceId);
        // If we got a feature/Ronin code the user can create a new account or add the Ronin/feature
        // code to an existing account. Step1 asks the user. Otherwise the create account button
        // will be hidden, and the user can login from the Step1 screen
        accountStep1();
    }

    private void prepareResult(Intent intent) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "prepare result");

        if (mAddAccountEntry) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "addAccountExplicitly");
            String accountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            final Account account = new Account(accountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));
            // Creating the account on the device
            mAccountManager.addAccountExplicitly(account, "dummyPassword", null);
        }
        else {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "prepareResult - don't update existing account entry");
        }
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    public void backStep() {
        getFragmentManager().popBackStack();
    }

    public void clearBackStack() {
        getFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    public JSONObject getJsonHolder() {
        return mJsonHolder;
    }

    public void removeFeatureCode() {
        mBlackPhoneArgs.putString(ARG_RONIN_CODE, null);
    }

    public void provisioningCancel() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    // The blackPhoneStepX functions control the BlackPhone provisioning flow. Step1 needs to call
    // Step2 to go forward or the 'backStep()' function to go backward.
    public void accountStep1() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        AccountStep1 step = (AccountStep1)fm.findFragmentByTag(ProvisioningActivity.STEP1_TAG);
        if (step == null) {
            step = AccountStep1.newInstance(mBlackPhoneArgs);
        }
        ft.replace(R.id.ProvisioningMainContainer, step, ProvisioningActivity.STEP1_TAG).commitAllowingStateLoss();

    }

    public void accountStep2() {
        mBlackPhoneArgs.putBoolean(ProvisioningActivity.USE_EXISTING, true);

        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        AccountStep2 step = (AccountStep2)fm.findFragmentByTag(ProvisioningActivity.STEP2_TAG);
        if (step == null) {
            step = AccountStep2.newInstance(mBlackPhoneArgs);
        }
        ft.replace(R.id.ProvisioningMainContainer, step, ProvisioningActivity.STEP2_TAG).addToBackStack(null).commitAllowingStateLoss();
    }

    public void accountStep2(String username, String password) {
        mBlackPhoneArgs.putBoolean(ProvisioningActivity.USE_EXISTING, false);

        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        AccountStep2 step = (AccountStep2)fm.findFragmentByTag(ProvisioningActivity.STEP2_TAG);
        if (step == null) {
            Bundle b = (Bundle)mBlackPhoneArgs.clone();
            b.putString(ProvisioningActivity.USERNAME, username);
            b.putString(AuthenticatorActivity.ARG_STEP1, password);
            step = AccountStep2.newInstance(b);
        }
        ft.replace(R.id.ProvisioningMainContainer, step, ProvisioningActivity.STEP2_TAG).addToBackStack(null).commitAllowingStateLoss();
    }

    private String mUsername;
    public void accountStep3(JSONObject customerData, String username, boolean useExisting) {
        mJsonHolder = customerData;
        mUsername = username;
        mBlackPhoneArgs.putString(ProvisioningActivity.USERNAME, username);
        mBlackPhoneArgs.putBoolean(ProvisioningActivity.USE_EXISTING, useExisting);
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        AccountStep3 step = (AccountStep3)fm.findFragmentByTag(ProvisioningActivity.STEP3_TAG);
        if (step == null) {
            step = AccountStep3.newInstance(mBlackPhoneArgs);
        }
        ft.replace(R.id.ProvisioningMainContainer, step, ProvisioningActivity.STEP3_TAG).addToBackStack(null).commitAllowingStateLoss();
    }

    // Finalize Username / password provisioning, used by account step 3 to check and
    // store data in KM.
    public void usernamePasswordDone(String devAuthorization) {
        // This should not happen :-) - parallel provisioning at nearly same time ... use stored API key
        byte[] data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (data != null) {
            finish();
            return;
        }
        storeApiKey(devAuthorization);

        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, mUsername);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, getIntent().getStringExtra(ARG_ACCOUNT_TYPE));
        if (checkPermissions(mOptions, this)) {
            result.putBundle(AccountManager.KEY_USERDATA, mUserData);
        }
        final Intent res = new Intent();
        res.putExtras(result);
        prepareResult(res);
    }

    private void storeApiKey(String devAuthorization) {
        try {
            byte[] data = devAuthorization.getBytes("UTF-8");
            if (!KeyManagerSupport.storeSharedKeyData(getContentResolver(),
                    data, ConfigurationUtilities.getShardAuthTag())) {
                Log.e(TAG, "Cannot store the device authorization data with key manager.");
                return;
            }
            Arrays.fill(data, (byte) 0);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Cannot convert device authorization data:", e);
            return;
        }
        mUserData.putString(AccountConstants.SC_AUTHORIZATION, devAuthorization);
        mUserData.putString(AccountConstants.DEVICE_ID, mDeviceId);
    }

    public void showErrorInfo(String msg) {
        ErrorMsgDialogFragment errMsg = ErrorMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getFragmentManager();
        errMsg.show(fragmentManager, "SilentPhoneProvisioningError");
    }

    public void showInputInfo(String msg) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, "SilentPhoneProvisioningInfo");
    }

    public void showPasswordCheck(EditText passwordInput, boolean checked) {
        if (checked) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }
        else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (passwordInput.getText() != null)
            passwordInput.setSelection(passwordInput.getText().length());
    }

    static boolean checkPermissions(final Bundle options, Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        final String callerPackage = options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME, null);
        final String myPackage = ctx.getPackageName();
        int result = pm.checkSignatures(myPackage, callerPackage);
        return result == PackageManager.SIGNATURE_MATCH;
    }


    /*
     * Dialog classes to display Error and Information messages.
     */
    private static String MESSAGE = "message";
    public static class ErrorMsgDialogFragment extends DialogFragment {
        private AuthenticatorActivity mParent;

        public static ErrorMsgDialogFragment newInstance(String msg) {
            ErrorMsgDialogFragment f = new ErrorMsgDialogFragment();

            Bundle args = new Bundle();
            args.putString(MESSAGE, msg);
            f.setArguments(args);

            return f;
        }

        public ErrorMsgDialogFragment() {
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mParent = (AuthenticatorActivity)activity;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
            Bundle args = getArguments();
            if (args == null)
                return null;
            builder.setTitle(getString(R.string.provisioning_error))
                    .setMessage(args.getString(MESSAGE))
                    .setPositiveButton(getString(R.string.close_dialog), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mParent.provisioningCancel();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public static class InfoMsgDialogFragment extends DialogFragment {
        private Activity mParent;

        public static InfoMsgDialogFragment newInstance(String msg) {
            InfoMsgDialogFragment f = new InfoMsgDialogFragment();

            Bundle args = new Bundle();
            args.putString(MESSAGE, msg);
            f.setArguments(args);

            return f;
        }

        public InfoMsgDialogFragment() {
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mParent = activity;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
            Bundle args = getArguments();
            if (args == null)
                return null;
            builder.setTitle(getString(R.string.provisioning_info))
                    .setMessage(args.getString(MESSAGE))
                    .setPositiveButton(getString(R.string.confirm_dialog), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
