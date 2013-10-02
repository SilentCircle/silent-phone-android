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

package com.silentcircle.silentphone.activities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.utils.Utilities;
import com.silentcircle.silentphone.utils.DeviceDetectionVertu;

public class Provisioning extends FragmentActivity {

    private static final int PROVISIONING_STEP0 = 0;
    private static final int PROVISIONING_STEP1 = 1;
    private static final int PROVISIONING_STEP2 = 2;
    private static final int PROVISIONING_STEP3 = 3;
    private static final int PROVISIONING_STEP4 = 4;
    private static final int PROVISIONING_STEP5 = 5;

    private static final String LOG_TAG = "Provisioning";
    private static final int KEY_GEN_RESULT = 7118;

    private Thread provFirstThread;
    private StringBuilder content = new StringBuilder();

    private int provisioningStep = PROVISIONING_STEP0;
    private Thread provSecondThread;

    /**
     * If server returns some data we assume at least MIN_CONTENT_LENGTH characters.
     */
    private static final int MIN_CONTENT_LENGTH = 10;

    private RelativeLayout codeInputLayout;
    private RelativeLayout spinnerLayout;
    private RelativeLayout checkboxLayout;
    private RelativeLayout welcomeLayout;
    private RelativeLayout extendedProvLayout;
    private ScrollView scrollFrameLayout;
    
    private TextView infoText;
    private TextView stepInfoText;
    private TextView pwStrength;

    private EditText usernameInput;

    private EditText passwordInput;
//    private EditText passwordInput2;

    private EditText firstNameInput;

    private EditText lastNameInput;

    private EditText emailInput;

    private Button provCancel;
    private Button provOk;

    private EditText provCodeInput;

    private boolean normalProvisioning = true;
    private boolean termsAndConditionsOk = false;

    private String deviceProvisioningData[];

    private JSONArray requiredArray;
    private JSONArray optionalArray;

    private URL requestUrl;
    
    private FilterEnter filterEnter = new FilterEnter();
    private PasswordFilter pwFilter = new PasswordFilter();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Gives more space on smaller screens to fill in the data fields
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_provisioning);

        extendedProvLayout = (RelativeLayout)findViewById(R.id.ProvisioningUserFields);
        codeInputLayout = (RelativeLayout)findViewById(R.id.ProvisioningCodeLayout);
        spinnerLayout = (RelativeLayout)findViewById(R.id.ProvisioningSpinnerLayout);
        checkboxLayout = (RelativeLayout)findViewById(R.id.ProvisioningCheckboxLayout);
        welcomeLayout = (RelativeLayout)findViewById(R.id.ProvisioningWelcomeLayout);
        scrollFrameLayout = (ScrollView)findViewById(R.id.ProvisioningScrollFrameLayout);

        infoText = (TextView) findViewById(R.id.ProvisioningInfoText);
        stepInfoText = (TextView) findViewById(R.id.ProvisioningStepInfo);
        pwStrength = (TextView)findViewById(R.id.ProvisioningPasswordStrength);

        usernameInput = (EditText) findViewById(R.id.ProvisioningUsernameInput);
        usernameInput.addTextChangedListener(filterEnter);

        passwordInput = (EditText) findViewById(R.id.ProvisioningPasswordInput);
        passwordInput.addTextChangedListener(pwFilter);
//        passwordInput2 = (EditText) findViewById(R.id.ProvisioningPasswordInput2);

        firstNameInput = (EditText) findViewById(R.id.ProvisioningFirstNameInput);
        firstNameInput.addTextChangedListener(filterEnter);

        lastNameInput = (EditText) findViewById(R.id.ProvisioningLastNameInput);
        lastNameInput.addTextChangedListener(filterEnter);

        emailInput = (EditText) findViewById(R.id.ProvisioningEmailInput);
        emailInput.addTextChangedListener(filterEnter);

        provCodeInput = (EditText) findViewById(R.id.ProvisioningCodeInput);

        provCancel = (Button) findViewById(R.id.ProvisioningCancel);
        provOk = (Button) findViewById(R.id.ProvisioningOK);

        if (TiviPhoneService.use_password_key) {
            Intent i = new Intent(this, KeyGeneration.class);
            i.putExtra(KeyGeneration.NEW_PROVISIONING, false);
            startActivityForResult(i, KEY_GEN_RESULT);
        }
        else {
            if (DeviceDetectionVertu.isVertu()) {
                deviceProvisioningData = DeviceDetectionVertu.getDeviceData(getApplicationContext());
                try {
                    requestUrl = new URL(deviceProvisioningData[0]);
                }
                catch (MalformedURLException e) {
                    finish();
                }

                provisioningStep = PROVISIONING_STEP1;
                welcomeLayout.setVisibility(View.VISIBLE);
            }
            else
                switchToNormalProvisioning();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == KEY_GEN_RESULT) {
            if (resultCode != RESULT_OK) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
            else {
                provFirstThread.start();
            }
        }
    }

    private void setupThreads() {
        
        provFirstThread = new Thread(new Runnable() {
            public void run() {
                HttpsURLConnection urlConnection = null;
                try {
                    urlConnection = (HttpsURLConnection) requestUrl.openConnection();
                    int ret = urlConnection.getResponseCode();
                    if (TMActivity.SP_DEBUG)
                        Log.d(LOG_TAG, "HTTP code: " + ret);

                    
                    switch (ret) {
                    case HttpsURLConnection.HTTP_NOT_FOUND:
                        runOnUiThread(new Runnable() {
                            public void run() {
                                switchToNormalProvisioning();
                            }
                        });
                        break;

                    case HttpsURLConnection.HTTP_OK:
                        normalProvisioning = false;
                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                        readStream(in);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                switchToExtraProvisioning();
                            }
                        });
                        break;

                    case HttpsURLConnection.HTTP_FORBIDDEN:
                        showInputInfo(getString(R.string.provisioning_already_registered));
                        runOnUiThread(new Runnable() {
                            public void run() {
                                switchToNormalProvisioning();
                            }
                        });
                        break;

                    default:
                        showInputInfo(getString(R.string.error_code) + ret);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                switchToNormalProvisioning();
                            }
                        });
                        break;
                    }
                }
                catch (IOException e) {
                    showErrorInfo(getString(R.string.provisioning_no_network) + e.getLocalizedMessage());
                    Log.e(LOG_TAG, "Network not available: " + e.getMessage());
                    return;
                }
                finally {
                    urlConnection.disconnect();
                }
            }
        });
        
        provSecondThread = new Thread(new Runnable() {
            public void run() {
                HttpsURLConnection urlConnection = null;
                Intent result = new Intent();

                int contentLength = 0;
                String body = customerData.toString();
                if (body != null) {
                    contentLength = body.getBytes().length;
                }

                setResult(Activity.RESULT_CANCELED, result);
                try {
                    urlConnection = (HttpsURLConnection) requestUrl.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setDoInput(true);
                    urlConnection.setDoOutput(true);
                    urlConnection.setRequestProperty("Content-Type", "application/json");
                    urlConnection.setFixedLengthStreamingMode(contentLength);

                    OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                    out.write(body.getBytes());
                    out.flush();

                    int ret = urlConnection.getResponseCode();
                    if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "HTTP code-2: " + ret);

                    if (ret == HttpsURLConnection.HTTP_NOT_FOUND) {
                        String msg = getString(R.string.provisioning_no_data); // "No provisioning data available";
                        Log.w(LOG_TAG, "No provisioning data available" + ret);
                        showErrorInfo(msg);
                        return;
                    }
                    else if (ret == HttpsURLConnection.HTTP_OK) {
                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                        readStream(in);
                        if (content.length() > MIN_CONTENT_LENGTH) {
                            try {
                                JSONObject jsonObj = new JSONObject(content.toString());
                                provisioningCode = jsonObj.getString("provisioning_code");
                            }
                            catch (JSONException e) {
                                String msg = getString(R.string.provisioning_wrong_format) + e.getMessage();
                                Log.w(LOG_TAG, "JSON exception: " + e);
                                showErrorInfo(msg);
                                return;
                            }
                        }
                    }
                    // Here we may build in some more dedicated error handling, for example to allow the user
                    // to re-enter the user name in case it's already used by someone else. Need to sync with
                    // server return codes. Instead of showing an error show and info dialog, and enable buttons
                    // again. Something like this:
                    //
                    // showInputInfo("some nice informative message");
                    // runOnUiThread(new Runnable() {
                    //     public void run() {
                    //         enableButtons();
                    //     }
                    // });
                    // return;
                    else {
                        // Disable spinner, show fields again
                        runOnUiThread(new Runnable() {
                            public void run() {
                                spinnerLayout.setVisibility(View.INVISIBLE);
                                extendedProvLayout.setVisibility(View.VISIBLE);
                                setupThreads();
                                enableButtons(true);
                            }
                        });

                        InputStream err = new BufferedInputStream(urlConnection.getErrorStream());
                        if (err != null)
                            readStream(err);
                        String msg = getString(R.string.error_code) + ret;
                        try {
                            JSONObject jsonObj = new JSONObject(content.toString());
                            msg = jsonObj.getString("error_msg");
                        }
                        catch (JSONException e) {
                            Log.w(LOG_TAG, "JSON exception: " + e);
                        }
                        showErrorInfo(msg);
                        return;
                    }
                }
                catch (IOException e) {
                    showErrorInfo(getString(R.string.provisioning_no_network) + e.getLocalizedMessage());
                    Log.e(LOG_TAG, "Network not available: " + e.getMessage());
                    return;
                }
                finally {
                    urlConnection.disconnect();
                }
                new Thread(new ProvisioningThread(provisioningCode)).start();
                new Thread(new ProvisioningMonitorThread()).start();
            }
        });
    }

    private void switchToNormalProvisioning() {
        extendedProvLayout.setVisibility(View.INVISIBLE);
        welcomeLayout.setVisibility(View.INVISIBLE);
        spinnerLayout.setVisibility(View.INVISIBLE);
        provisioningStep = PROVISIONING_STEP5;
        codeInputLayout.setVisibility(View.VISIBLE);
        stepInfoText.setText(null);
        infoText.setText(null);
        enableButtons(true);
    }

    private void switchToExtraProvisioning() {

        /*
         * OK, here we got a positive answer, pre-provisioned account was found. Check if we have some content, switch on the
         * fields for extra provisioning info
         */
        String greet = null;
        String partner = null;

        if (content.length() > MIN_CONTENT_LENGTH) {
            try {
                JSONObject jsonObj = new JSONObject(content.toString());
                greet = jsonObj.getString("partner_greeting");
                partner = jsonObj.getString("partner");
                requiredArray = jsonObj.getJSONArray("required"); 
                optionalArray = jsonObj.getJSONArray("optional"); 
            }
            catch (JSONException e) {
            }
        }
//        if (greet != null)
//            infoText.setText(greet);

        provisioningStep = PROVISIONING_STEP2;
        spinnerLayout.setVisibility(View.INVISIBLE);
        extendedProvLayout.setVisibility(View.VISIBLE);
        enableButtons(true);
    }

    private void enableButtons(boolean enable) {
        if (enable) {
            provCancel.setVisibility(View.VISIBLE);
            provCancel.setEnabled(true);
            provOk.setVisibility(View.VISIBLE);
            provOk.setEnabled(true);
        }
        else {
            provCancel.setVisibility(View.INVISIBLE);
            provCancel.setEnabled(false);
            provOk.setVisibility(View.INVISIBLE);
            provOk.setEnabled(false);
        }
    }

    private boolean checkInputAndCopy(String field, String input) {
        if (isRequired(field) && (input == null || input.isEmpty()))
            return false;

        if (input == null || input.isEmpty())
            return true;
        try {
            customerData.put(field, input);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return true;
    }
    
    /**
     * Check if an input field is marked as required.
     * 
     * @param field name of the field.
     * @return <code>true</code> if the field is marked as required
     */
    private boolean isRequired(String field) {
        if (requiredArray == null)
            return false;
        for (int idx = 0; idx < requiredArray.length(); idx++) {
            try {
                String reqField = requiredArray.getString(idx);
                if (reqField == null)
                    return false;
                if (reqField.equals(field))
                    return true;
            }
            catch (JSONException e) {
            }
        }
        return false;
    }
    
    /**
     * A simple check if two passwords are equal.
     * 
     * Returns false if one or both passwords are null or empty or if the passwords
     * don't match.
     * 
     * @param pw1 first password
     * @param pw2 second password
     * @return true if both passwords match and are not empty.
     */
    private boolean checkPassword(String pw1, String pw2) {
        if (pw1 == null || pw2 == null)
            return false;
        if (pw1.isEmpty() || pw2.isEmpty())
            return false;
        return pw1.equals(pw2);
    }
    
    private void showErrorInfo(String msg) {
        ErrorMsgDialogFragment errMsg = ErrorMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getSupportFragmentManager();
        errMsg.show(fragmentManager, "SilentPhoneProvisioningError");
    }

    private void showInputInfo(String msg) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getSupportFragmentManager();
        infoMsg.show(fragmentManager, "SilentPhoneProvisioningInfo");
    }

    private JSONObject customerData;
    private String provisioningCode;

    /**
     * User confirmed the provisioning code.
     * 
     * This method checks user input and starts the thread that sends user data to the provisioning server. 
     * If the server checked and accepted the data it sends back the provisioning code. Then start 
     * the real provisioning threads and set up the client configuration.
     * 
     * @param view the Confirm button
     */
    public void provisioningOk(View view) {

        if (normalProvisioning) {
            provisioningCode = provCodeInput.getText().toString();
            codeInputLayout.setVisibility(View.INVISIBLE);
            spinnerLayout.setVisibility(View.VISIBLE);
            new Thread(new ProvisioningThread(provisioningCode)).start();
            new Thread(new ProvisioningMonitorThread()).start();
            return;
        }

        switch (provisioningStep) {
        case PROVISIONING_STEP2:
            customerData = new JSONObject();
            if (!checkInputAndCopy("psn", deviceProvisioningData[1])) {
                showInputInfo(getString(R.string.provisioning_psn_invalid));
                provisioningCancel(null);
                return;
            }
            if (!checkInputAndCopy((String) usernameInput.getTag(), usernameInput.getText().toString())) {
                showInputInfo(getString(R.string.provisioning_user_req));
                return;
            }
            if (!checkInputAndCopy((String) passwordInput.getTag(), passwordInput.getText().toString())) {
                showInputInfo(getString(R.string.provisioning_password_req));
                return;
            }
            // if (!checkPassword(passwordInput.getText().toString(), passwordInput2.getText().toString())) {
            // showInputInfo(getString(R.string.provisioning_password_match));
            // enableButtons();
            // return;
            // }
            if (!checkInputAndCopy((String) firstNameInput.getTag(), firstNameInput.getText().toString())) {
                showInputInfo(getString(R.string.provisioning_firstname_req));
                return;
            }
            if (!checkInputAndCopy((String) lastNameInput.getTag(), lastNameInput.getText().toString())) {
                showInputInfo(getString(R.string.provisioning_lastname_req));
                return;
            }
            if (!checkInputAndCopy((String) emailInput.getTag(), emailInput.getText().toString())) {
                showInputInfo(getString(R.string.provisioning_email_req));
                return;
            }
            extendedProvLayout.setVisibility(View.INVISIBLE);
            checkboxLayout.setVisibility(View.VISIBLE);
            scrollFrameLayout.fullScroll(ScrollView.FOCUS_UP);
            provOk.setText(R.string.provisioning_create);
            stepInfoText.setText(getString(R.string.provisioning_stepinfo_2));
            provisioningStep = PROVISIONING_STEP3;
            break;

        case PROVISIONING_STEP3:
            
            // Check the check boxes for terms and conditions etc, of if set then
            // start provisioning second thread to get the provisioning code.
            if (!termsAndConditionsOk) {
                showInputInfo(getString(R.string.provisioning_check_tc));
                break;
            }
            enableButtons(false);
            checkboxLayout.setVisibility(View.INVISIBLE);
            spinnerLayout.setVisibility(View.VISIBLE);
            stepInfoText.setText(null);
            provSecondThread.start();
        }
    }

    /**
     * Remember if user checked the Terms and Condition box.
     * 
     * @param v the check box view
     */
    public void termsAndConditionsCheck(View v) {
        CheckedTextView ctv = (CheckedTextView)v;
        ctv.toggle();
        termsAndConditionsOk = ctv.isChecked();
    }

    /**
     * User clicked on use existing account button on Welcome screen.
     * 
     * @param v the button's view
     */
    public void provisioningExistingAccount(View v) {
        welcomeLayout.setVisibility(View.INVISIBLE);
        switchToNormalProvisioning();
    }

    /**
     * User clicked on create new account button on Welcome screen.
     * 
     * @param v the button's view
     */
    public void provisioningNewAccount(View v) {
        provisioningStep = PROVISIONING_STEP2;
        enableButtons(true);
        welcomeLayout.setVisibility(View.INVISIBLE);
        setupThreads();
        infoText.setText(getString(R.string.provisioning_infotext));
        stepInfoText.setText(getString(R.string.provisioning_stepinfo_1));
        spinnerLayout.setVisibility(View.VISIBLE);
        provFirstThread.start();
    }

    public void showPasswordCheck(View v) {
        CheckBox cbv = (CheckBox)v;
        if (cbv.isChecked()) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        }
        else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        passwordInput.setSelection(passwordInput.getText().length());
    }

    /**
     * User pressed the 'back' button. 
     * 
     * Depending of the provisioning state we may take different actions. If possible we just got back
     * to the previous step (screen). If already at the welcome screen we do nothing.
     * 
     * Going back from normal provisioning (STEP5) means we finish the activity and return a CANCEL
     * to the main activity which then decides what to do - usually terminates whole application.
     */
    public void provisioningCancel(View view) {
        switch (provisioningStep) {
        case PROVISIONING_STEP2:
            provisioningStep = PROVISIONING_STEP1;
            enableButtons(false);
            stepInfoText.setText(null);
            infoText.setText(R.string.provisioning_vertu_welcome);
            extendedProvLayout.setVisibility(View.INVISIBLE);
            spinnerLayout.setVisibility(View.INVISIBLE);
            welcomeLayout.setVisibility(View.VISIBLE);
            break;

        case PROVISIONING_STEP3:
            provisioningStep = PROVISIONING_STEP2;
            checkboxLayout.setVisibility(View.INVISIBLE);
            stepInfoText.setText(getString(R.string.provisioning_stepinfo_1));
            provOk.setText(R.string.next);
            extendedProvLayout.setVisibility(View.VISIBLE);
            break;
        
         // This case is the normal provisioning where you need to enter a provisioning code
        case PROVISIONING_STEP5:
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    private void readStream(InputStream in) {
        BufferedReader reader = null;
        content.delete(0, content.length()); // remove old content
        reader = new BufferedReader(new InputStreamReader(in));

        try {
            for (String str = reader.readLine(); str != null; str = reader.readLine()) {
                content.append(str).append('\n');
            }
            if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "Result: " + content);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /* ********************************************************************************
     * Handle second step of provisioning process.
     * 
     * After user entered the provisioning code or server supplied a provisioning code we start two threads: one thread that
     * issues the actual provisioning command and waits until done.
     * 
     * The second thread monitors the provisioning progress. Terminates if the first thread terminates and sets stop flag.
     * ********************************************************************************
     */

    private int provisioningResult = 0;
    private boolean stopProvMonitoring = false;

    private class ProvisioningThread implements Runnable {
        private String provCode;

        ProvisioningThread(String code) {
            provCode = code;
        }

        public void run() {
            // Run Provisioning in own thread because it may take several seconds.
            // The ProvisioningMonitorThread reports possible error message.
            provisioningResult = TiviPhoneService.doCmd("prov.start=" + provCode);
            stopProvMonitoring = true;
        }
    }

    private class ProvisioningMonitorThread implements Runnable {
        String result;

        public void run() {
            boolean errorMessage = false;
            while (!stopProvMonitoring) {
                Utilities.Sleep(1000); // Wait for one second
                result = TiviPhoneService.getInfo(-1, -1, "prov.tryGetResult");
                runOnUiThread(new Runnable() {
                    public void run() {
                        provCodeInput.setText(result);
                        provCodeInput.invalidate();
                    }
                });
            }
            if (provisioningResult < 0) {
                errorMessage = true;
                result = getString(R.string.error_code) + result;
                showErrorInfo(result);
            }
            if (errorMessage) {
                Utilities.Sleep(5000);
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    if (provisioningResult >= 0) {
                        setResult(Activity.RESULT_OK);
                    }
                    else {
                        setResult(Activity.RESULT_CANCELED);
                    }
                    finish();
                    return;
                }
            });
        }
    }

    
    private class FilterEnter implements TextWatcher {

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
     * 
     * @author werner
     *
     */
    private class PasswordFilter implements TextWatcher {

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void afterTextChanged(Editable s) {
            String str = s.toString();
            int strLength = str.length();
            int strength = 0;

            if (strLength == 0) {
               pwStrength.setText(null);
               return;
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

            pwStrength.setText(getString(R.string.provisioning_pwstrength_weak));
            if (((strength >= 2 && strLength >= 7) || (strength >= 3 && strLength >= 6)) || strLength > 8) {
                pwStrength.setText(getString(R.string.provisioning_pwstrength_good));
            }
            if ((strength >= 3 && strLength >= 7) || strLength > 10) {
                pwStrength.setText(getString(R.string.provisioning_pwstrength_strong));
            }
        }
    }

    /*
     * Dialog classes to display Error and Information messages.
     */
    private static String MESSAGE = "message";
    public static class ErrorMsgDialogFragment extends DialogFragment {
        
        public static ErrorMsgDialogFragment newInstance(String msg) {
            ErrorMsgDialogFragment f = new ErrorMsgDialogFragment();

            Bundle args = new Bundle();
            args.putString(MESSAGE, msg);
            f.setArguments(args);

            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.provisioning_error))
                .setMessage(getArguments().getString(MESSAGE))
                .setPositiveButton(getString(R.string.close_dialog), new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           ((Provisioning)getActivity()).provisioningCancel(null);
                       }
                   });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public static class InfoMsgDialogFragment extends DialogFragment {

        public static InfoMsgDialogFragment newInstance(String msg) {
            InfoMsgDialogFragment f = new InfoMsgDialogFragment();

            Bundle args = new Bundle();
            args.putString(MESSAGE, msg);
            f.setArguments(args);

            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.provisioning_info))
                .setMessage(getArguments().getString(MESSAGE))
                .setPositiveButton(getString(R.string.confirm_dialog), new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                       }
                   });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
