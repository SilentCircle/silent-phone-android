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

package com.silentcircle.silentphone2.activities;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.silentcircle.accounts.AccountConstants;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.ProvisioningAutomatic;
import com.silentcircle.silentphone2.fragments.ProvisioningUserPassword;
import com.silentcircle.silentphone2.fragments.ProvisioningVertuStep1;
import com.silentcircle.silentphone2.fragments.ProvisioningVertuStep2;
import com.silentcircle.silentphone2.fragments.ProvisioningVertuStep3;
import com.silentcircle.silentphone2.providers.StatusProvider;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Constants;
import com.silentcircle.silentphone2.util.DeviceDetectionVertu;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class ProvisioningActivity extends AppCompatActivity {

    private static final String LOG_TAG = "ProvisioningActivity";

    public static final String USER_PASS_TAG = "spa_username_password_fragment";

    public static final String STEP1_TAG = "spa_step1_fragment";
    public static final String STEP2_TAG = "spa_step2_fragment";
    public static final String STEP3_TAG = "spa_step3_fragment";
    public static final String STEPCORP1_TAG = "spa_corp_step1_fragment";
    public static final String STEPCORP2_TAG = "spa_corp_step2_fragment";
    public static final String STEPCORP3_TAG = "spa_corp_step3_fragment";

    public static final String AUTOMATIC_TAG = "spa_automatic_fragment";

    public static final String PREF_KM_API_KEY = "com.silentcircle.silentphone.prov_km_api_key";
    public static final String PROVISIONING_DONE = "provisioning_done";
    private static final boolean PROVISIONING_FLAG = true;     // set to true after testing

    public static final String DEVICE_ID = "device_id";
    public static final String USE_EXISTING = "use_existing";
    public static final String USERNAME = "username";

    private static final int DID_SELECTION_DONE = 11;

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public interface ProvisioningCallback {
        /**
         * Provisioning activity calls this after the C++ based provisioning finished.
         *
         * @param code return code from provisioning thread (C++ part)
         * @param result error message in case a problem occurred.
         */
        void provisioningDone(int code, String result);
    }

    private JSONObject mJsonHolder;

    private boolean hasKeyManager;
    private String deviceId;

    private ProvisioningCallback mProvCallback;
    private boolean mStoreApiKey;
    private String mDevAuthorization;

    private Activity mContext;

    private boolean mIsPaused = false;
    private boolean mIsDestroyed = false;
    private CheckDeviceStatus mCheckDeviceStatusTask;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        // Gives more space on smaller screens to fill in the data fields
//        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Utilities.setTheme(this);
        ConfigurationUtilities.initializeDebugSettings(getBaseContext());

        setContentView(R.layout.activity_provisioning);
        final Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setResult(Activity.RESULT_CANCELED);        // assume failure unless provisioning threads set it to OK

        hasKeyManager = getIntent().getBooleanExtra("KeyManager", false);

        if (DeviceDetectionVertu.isVertu()) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            pref.edit().remove(ConfigurationUtilities.getInstanceDevIdSaveKey()).apply();
            Log.d(LOG_TAG, "Remove stored device instance id - creating new one");

            deviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(this, true));
            if (deviceId == null) {
                finish();                               // a major problem
                return;
            }
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Device id: " + deviceId);
            vertuStep1();
            return;
        }
        // Get preference storage
        final SharedPreferences prefs =  getSharedPreferences(PREF_KM_API_KEY, Context.MODE_PRIVATE);
        final String feature = prefs.getString(StatusProvider.BP_FEATURECODE, null);
        useAccountManager(feature);
        mIsPaused = false;
        mIsDestroyed = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DID_SELECTION_DONE)
            finalizeProvisionAfterDid();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
        cancelCheckDeviceStatusTask();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mIsDestroyed = true;
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


    // The vertuStepX functions control the Vertu provisioning flow. Step1 needs to call
    // Step2 to go forward or the 'backStep()' functions to go backward.
    public void vertuStep1() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ProvisioningVertuStep1 vertuStep1 = (ProvisioningVertuStep1)fm.findFragmentByTag(STEP1_TAG);
        if (vertuStep1 == null) {
            vertuStep1 = ProvisioningVertuStep1.newInstance();
        }
        ft.replace(R.id.ProvisioningMainContainer, vertuStep1, STEP1_TAG).commitAllowingStateLoss();
    }

    public void vertuStep2() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ProvisioningVertuStep2 vertuStep2 = (ProvisioningVertuStep2)fm.findFragmentByTag(STEP2_TAG);
        if (vertuStep2 == null) {
            vertuStep2 = ProvisioningVertuStep2.newInstance();
        }
        ft.replace(R.id.ProvisioningMainContainer, vertuStep2, STEP2_TAG).addToBackStack(null).commitAllowingStateLoss();
    }

    public void vertuStep3(JSONObject customerData) {
        mJsonHolder = customerData;
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ProvisioningVertuStep3 vertuStep3 = (ProvisioningVertuStep3)fm.findFragmentByTag(STEP3_TAG);
        if (vertuStep3 == null) {
            vertuStep3 = ProvisioningVertuStep3.newInstance();
        }
        ft.replace(R.id.ProvisioningMainContainer, vertuStep3, STEP3_TAG).addToBackStack(null).commitAllowingStateLoss();
    }

    // Username / password provisioning has one step only, also used in case something happens
    // during Vertu provisioning
    public void usernamePassword() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ProvisioningUserPassword usernamePassword = (ProvisioningUserPassword)fm.findFragmentByTag(USER_PASS_TAG);
        if (usernamePassword == null) {
            Bundle arg = new Bundle();
            arg.putString(DEVICE_ID, deviceId);
            usernamePassword = ProvisioningUserPassword.newInstance(arg);
        }
        ft.replace(R.id.ProvisioningMainContainer, usernamePassword, USER_PASS_TAG).commitAllowingStateLoss();
    }

    // Finalize Username / password provisioning then start the 2nd phase of provisioning to get the
    // provisioning data.
    public void usernamePasswordDone(String devAuthorization, ProvisioningCallback callback) {
        boolean storeApiKey = false;
        // This should not happen :-) - parallel provisioning at nearly same time ... use stored API key
        if (hasKeyManager) {
            byte[] data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
            if (data != null) {
                try {
                    devAuthorization = new String(data, "UTF-8").trim();
                } catch (UnsupportedEncodingException ignored) {}
            }
            else
                storeApiKey = true;
        }
        provisionWithAuthorization(devAuthorization, storeApiKey, callback);
    }

    private void useAccountManager(String feature) {
        final AccountManager am = AccountManager.get(this);

        final Bundle addAccountOptions = new Bundle();
        if (!TextUtils.isEmpty(feature))
            addAccountOptions.putString("feature_code", feature);

        // This convenience helper combines the functionality of
        // getAccountsByTypeAndFeatures(String, String[], AccountManagerCallback, Handler),
        // getAuthToken(Account, String, Bundle, Activity, AccountManagerCallback, Handler),
        // and addAccount(String, String, String[], Bundle, Activity, AccountManagerCallback, Handler).
        am.getAuthTokenByFeatures(AccountConstants.ACCOUNT_TYPE, AccountConstants.ACCOUNT_ACCESS, null /*features */,
                this, addAccountOptions, null,
                new AccountManagerCallback<Bundle>() {
                    public void run(AccountManagerFuture<Bundle> future) {
                        try {
                            final Bundle result = future.getResult();
                            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "useAccountManager result: " + result);
                            am.invalidateAuthToken(AccountConstants.ACCOUNT_TYPE, result.getString(AccountManager.KEY_AUTHTOKEN));
                            final Bundle userData = result.getBundle(AccountManager.KEY_USERDATA);
                            if (userData == null) {
                                Log.e(LOG_TAG, "AccountManager did not provide provisioning data");
                                provisioningCancel(null);
                                return;
                            }
                            final String authorization = userData.getString(AccountConstants.SC_AUTHORIZATION, null);
                            deviceId = userData.getString(AccountConstants.DEVICE_ID, null);
                            if (authorization == null || deviceId == null) {
                                Log.e(LOG_TAG, "AccountManager returned incomplete provisioning data: " + (deviceId == null ? "device id" : "authorization"));
                                provisioningCancel(null);
                                return;
                            }
                            checkDeviceStatus(authorization);
                            return;
                        } catch (OperationCanceledException e) {
                            Log.w(LOG_TAG, "getAuthTokenByFeatures canceled", e);
                        } catch (IOException e) {
                            Log.w(LOG_TAG, "getAuthTokenByFeatures I/O problem", e);
                        } catch (AuthenticatorException e) {
                            Log.w(LOG_TAG, "getAuthTokenByFeatures Authenticator exception", e);
                        }
                        provisioningCancel(null);
                    }
                }, null);

    }

    // Automatic provision runs if this or another SC application was provisioned and stored the
    // API key (device authorization) with the key manager. In this case we don't ask for username
    // and password: use the API key and forward it to the fragment which handles it like the other
    // provisioning methods. The fragment shows a progress bar only.
    private void automaticProvisioning(String devAuthorization) {
        if (isFinishing() || mIsDestroyed) {
            return;
        }

        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ProvisioningAutomatic automatic = (ProvisioningAutomatic)fm.findFragmentByTag(AUTOMATIC_TAG);
        if (automatic == null) {
            Bundle arg = new Bundle();
            arg.putString(ProvisioningAutomatic.deviceAuthorization, devAuthorization);
            automatic = ProvisioningAutomatic.newInstance(arg);
        }
        ft.replace(R.id.ProvisioningMainContainer, automatic, AUTOMATIC_TAG).commitAllowingStateLoss();
    }

    // Only used by vertuStep3 to start off the 2nd phase of provisioning to get the provisioning data
    // Vertu is using an activation code, all other provisioning flows use API key (devAuthorization)
    public void provisionWithActivationCode(String activationCode, ProvisioningCallback callback) {
        mProvCallback = callback;
        new Thread(new ProvisioningThread(activationCode)).start();
        new Thread(new ProvisioningMonitorThread()).start();
    }

    public void provisionWithAuthorization(String devAuthorization, boolean storeApiKey, ProvisioningCallback callback) {
        // Save the info. We need it after DID selection flow to finalize provisioning.
        // See the provisioning threads below.
        mDevAuthorization = devAuthorization;
        mStoreApiKey = storeApiKey;
        mProvCallback = callback;

        // this will either start the DID selection flow or finalize the provisioning, depending
        // if we could get a region or not. DID selection is an own activity that returns a result
        // after it's done and then we finalize provisioning. See onActivityResult().
        checkStartDidSelection();
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

    public void provisioningCancel(View view) {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    // Called after DID selection, either if no DID available or after user selected a DID
    private void finalizeProvisionAfterDid() {
        new Thread(new ProvisioningThreadAuthorization()).start();
        new Thread(new ProvisioningMonitorThread()).start();
    }

    /* ********************************************************************************
     * Handle second phase of provisioning flow.
     * 
     * After we either got an API key or an activation (provisioning) code we start two
     * threads:
     *  - either a thread that issues the actual provisioning command and waits until done.
     *     or
     *  - a thread that uses the device authorization data to perform provisioning.
     * 
     * The second thread monitors the provisioning progress. It terminates if the first
     * thread terminates and sets the stop flag.
     * ********************************************************************************
     */

    /*
    Provisioning commands to use with C++ code:

    C++ code supports two provisioning methods. The first method use the normal 'provisioning code'
    that a user gets from the SC account Web site.
    The second method uses authorization data (API key) that we either get via provisioning API during
    the previous provisioning steps or this data was stored by another SC app with the key manager.

    TiviPhoneService.doCmd("prov.start=" + provCode);          //start prov with user provisioning code
    TiviPhoneService.doCmd("prov.start.apikey=" + apiKey);     //start prov with authorization data (API key)

    To get authorization data (API key) from a provisioning process if provisioning code was used:
    String result = TiviPhoneService.getInfo(-1, -1, "prov.getAPIKey");

    Clear the authorization data in C++ code:
    TiviPhoneService.doCmd("prov.clear.apikey"); to clear the API key.
    */

    private int provisioningResult = 0;
    private boolean stopProvMonitoring = false;

    // Provisioning starts this thread if we got the activation, either via user input
    // or via automatic Vertu provisioning.
    private class ProvisioningThread implements Runnable {
        private String provCode;

        ProvisioningThread(String code) {
            provCode = code;
        }

        public void run() {
            // Run Provisioning in own thread because it may take several seconds.
            // The ProvisioningMonitorThread reports possible error message.
            provisioningResult = TiviPhoneService.doCmd("prov.start=" + provCode);
            if (provisioningResult >= 0) {
                // if the device has Silent Circle key manager then get the authorization data
                // and store it with the key manager.
                if (hasKeyManager) {
                    String devAuthorization = TiviPhoneService.getInfo(-1, -1, "prov.getAPIKey");
                    storeApiKey(devAuthorization);
                }
                // in any case clear the authorization data (apikey) in C++ code
                TiviPhoneService.doCmd("prov.clear.apikey");
            }
            stopProvMonitoring = true;
        }
    }

    // Provisioning starts this thread if we could get the device's authorization code from
    // the key manager or via Web API.
    private class ProvisioningThreadAuthorization implements Runnable {

        ProvisioningThreadAuthorization() { }

        public void run() {
            // Run Provisioning in own thread because it may take several seconds. Store the API key
            // with the key manager only if provisioning was successful.
            // The ProvisioningMonitorThread reports possible error message.
            provisioningResult = TiviPhoneService.doCmd("prov.start.apikey=" + mDevAuthorization);
            if (mStoreApiKey && provisioningResult >= 0)
                storeApiKey(mDevAuthorization);
            stopProvMonitoring = true;
        }
    }

    private void storeApiKey(String devAuthorization) {
        try {
            byte[] data = devAuthorization.getBytes("UTF-8");
            if (!KeyManagerSupport.storeSharedKeyData(ProvisioningActivity.this.getContentResolver(),
                    data, ConfigurationUtilities.getShardAuthTag())) {
                Log.e(LOG_TAG, "Cannot store the device authorization data with key manager.");
                return;
            }
            Arrays.fill(data, (byte) 0);
        } catch (UnsupportedEncodingException e) {
            Log.e(LOG_TAG, "Cannot convert device authorization data:", e);
        }
    }

    private class ProvisioningMonitorThread implements Runnable {
        String result;

        public void run() {
            while (!stopProvMonitoring) {
                Utilities.Sleep(1000); // Wait for one second
                result = TiviPhoneService.getInfo(-1, -1, "prov.tryGetResult");
            }
            if (provisioningResult >= 0) {
                // Start loading my user info asap, the authorization data is available in KeyManager
                LoadUserInfo li = new LoadUserInfo(getApplicationContext(), true);
                li.refreshUserInfo();

                SharedPreferences prefs = getSharedPreferences(PREF_KM_API_KEY, Context.MODE_PRIVATE);
                // provisioned, remove the feature code if it exists.
                prefs.edit()
                        .putBoolean(PROVISIONING_DONE, PROVISIONING_FLAG)
                        .remove(StatusProvider.BP_FEATURECODE)
                        .apply();
            }
            // The fragment handles this and also finishes activity
            if (mProvCallback != null) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        result = getString(R.string.error_code) + result;
                        if (mProvCallback != null) {
                            mProvCallback.provisioningDone(provisioningResult, result);
                        }
                    }
                });
                return;
            }
            if (provisioningResult >= 0) {
                setResult(Activity.RESULT_OK);
                finish();
            }
            else {
                runOnUiThread(new Runnable() {
                    public void run() {
                        result = getString(R.string.error_code) + result;
                        showErrorInfo(result);
                    }
                });
            }
        }
    }

    public static class FilterEnter implements TextWatcher {

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void afterTextChanged(Editable s) {
            for (int i = s.length(); i > 0; i--) {

                if (s.subSequence(i - 1, i).toString().equals("\n")) {
                    Log.d("LOG_TAG", "filtered a ENTER code");
                    s.replace(i - 1, i, "");
                }
            }
        }
    }

    /**
     * A simple password strength check.
     *
     * This check just looks for length and some different characters to give an indication
     * about a password strength.
     */
    public static int passwordStrength(Editable s) {
        String str = s.toString();
        int strLength = str.length();
        int strength = 0;

        if (strLength == 0) {
            return -1;
        }
        boolean digit = false;
        boolean lower = false;
        boolean upper = false;
        boolean other = false;

        for (int i = 0; i < strLength; i++) {
            char chr = str.charAt(i);
            if (Character.isDigit(chr))
                digit = true;
            else if (Character.isLowerCase(chr))
                lower = true;
            else if (Character.isUpperCase(chr))
                upper = true;
            else
                other = true;
        }
        strength += (digit) ? 1 : 0;
        strength += (lower) ? 1 : 0;
        strength += (upper) ? 1 : 0;
        strength += (other) ? 1 : 0;

        if (((strength >= 2 && strLength >= 7) || (strength >= 3 && strLength >= 6)) || strLength > 8) {
            return 1;
        }
        if ((strength >= 3 && strLength >= 7) || strLength > 10) {
            return 2;
        }
        return 0;

    }
    /*
     * Dialog classes to display Error and Information messages.
     */
    private static String MESSAGE = "message";
    public static class ErrorMsgDialogFragment extends DialogFragment {
        private ProvisioningActivity mParent;

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
            mParent = (ProvisioningActivity)activity;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
            Bundle args = getArguments();
            if (args == null)
                return null;
            builder.setTitle(getString(R.string.provisioning_error))
                //.setMessage(args.getString(MESSAGE))
                .setMessage(getString(R.string.provisioning_error_message))
                .setPositiveButton(getString(R.string.close_dialog), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mParent.provisioningCancel(null);
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

    private void checkStartDidSelection() {
        LoaderTaskGetRegionList loaderTask = new LoaderTaskGetRegionList();

        URL regionUrl = null;
        try {
            regionUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(getBaseContext()) +
                    ConfigurationUtilities.getDidSelectionBase(getBaseContext()) + "?api_key=" +
                    Uri.encode(mDevAuthorization));
        } catch (MalformedURLException e) {
            Log.d(LOG_TAG, "URL exception to get DID region");
            finalizeProvisionAfterDid();                        // Try to finalize provisioning
        }
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "DID region URL: " + regionUrl);
        loaderTask.execute(regionUrl);
    }

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        if (isFinishing() || mIsDestroyed) {
            return;
        }

        com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment infoMsg = com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = mContext.getFragmentManager();
        if (fragmentManager != null) {
            infoMsg.show(fragmentManager, LOG_TAG);
        }
    }

    private class LoaderTaskGetRegionList extends AsyncTask<URL, Integer, Integer> {

        private StringBuilder content = new StringBuilder();

        LoaderTaskGetRegionList() { }

        @Override
        protected Integer doInBackground(URL... params) {
            HttpsURLConnection urlConnection = null;
            try {
                urlConnection = (HttpsURLConnection)params[0].openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(LOG_TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestMethod("GET");
                urlConnection.setDoInput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "HTTP code getRegions: " + ret);
                if (ret == HttpsURLConnection.HTTP_OK) {
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getInputStream()), content);
                }
                return ret;

            } catch (IOException e) {
                if(!Utilities.isNetworkConnected(mContext)){
                    return Constants.NO_NETWORK_CONNECTION;
                }
                //TODO: should we remove the "no network" dialog here? keep the rest which are not related on client side issue for debug purpose
                showInputInfo(getString(R.string.provisioning_no_network) + e.getLocalizedMessage());
                Log.e(LOG_TAG, "Network not available: " + e.getMessage());
                return -1;
            } catch (Exception e) {
                showInputInfo(getString(R.string.provisioning_error) + e.getLocalizedMessage());
                Log.e(LOG_TAG, "Network connection problem: " + e.getMessage());
                return -1;
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                //start DID selection for result
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Start DID selection");
                Intent intent = new Intent(ProvisioningActivity.this, DidSelectionActivity.class);
                intent.putExtra(DidSelectionActivity.REGIONS, content.toString());
                intent.putExtra(DidSelectionActivity.API_KEY, mDevAuthorization);
                startActivityForResult(intent, DID_SELECTION_DONE);
            }
            else if(result == Constants.NO_NETWORK_CONNECTION) {
                provisioningCancel(null);
                showDialog(R.string.information_dialog, R.string.connected_to_network, android.R.string.ok, -1);
            }
            else {
                // continue provisioning
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "No DID selection, finalize provisioning");
                finalizeProvisionAfterDid();
            }
        }
    }

    private void checkDeviceStatus(String authorization) {
        cancelCheckDeviceStatusTask();
        mCheckDeviceStatusTask = new CheckDeviceStatus(authorization);
        mCheckDeviceStatusTask.execute();
    }

    private void cancelCheckDeviceStatusTask() {
        if (mCheckDeviceStatusTask != null) {
            mCheckDeviceStatusTask.cancel(true);
            mCheckDeviceStatusTask = null;
        }
    }

    /**
     * Async task to check device availability on the provisioning server.
     *
     * If the device is still known on the provisioning server then continue with the re-provisioning,
     * otherwise ask user how to proceed.
     */
    private class CheckDeviceStatus extends AsyncTask<Void, Integer, Integer> {

        private URL requestUrl;
        private String mAuthorization;

        CheckDeviceStatus(String authorization) {
            String resourceSec = ConfigurationUtilities.getProvisioningBaseUrl(getBaseContext()) +
                    ConfigurationUtilities.getDeviceManagementBase(getBaseContext());  // /v1/me/device/
            mAuthorization = authorization;
            try {
                // /v1/me/device/{device_id}/?api_key={api_key}
                requestUrl = new URL(resourceSec + Uri.encode(deviceId) + "/?api_key=" + Uri.encode(authorization));
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Device check URL: " + requestUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                finish();
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            HttpsURLConnection urlConnection = null;
            try {
                urlConnection = (HttpsURLConnection)requestUrl.openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(LOG_TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestMethod("GET");
                urlConnection.setDoInput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "HTTP code device status: " + ret);
                return ret;

            } catch (IOException e) {
                if(!Utilities.isNetworkConnected(ProvisioningActivity.this)){
                    return Constants.NO_NETWORK_CONNECTION;
                }
                Log.e(LOG_TAG, "Network not available: " + e.getMessage());
                return -1;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Network connection problem: " + e.getMessage());
                return -1;
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Device status is OK");
                automaticProvisioning(mAuthorization);
            }
            else if(result == Constants.NO_NETWORK_CONNECTION) {
                showDialog(R.string.information_dialog, R.string.connected_to_network, android.R.string.ok, -1);
            }
            else if (result == HttpsURLConnection.HTTP_FORBIDDEN || result == HttpsURLConnection.HTTP_NOT_FOUND) {
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Device status check - not found on provisioning server");
                KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
                KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardDevIdTag());
                usernamePassword();
            }
            else {
                showDialog(R.string.information_dialog, R.string.connected_to_network,
                        android.R.string.ok, -1);
            }
        }
    }
}
