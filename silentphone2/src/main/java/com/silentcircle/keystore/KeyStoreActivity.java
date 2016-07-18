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

import android.app.ActionBar;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.widget.TextView;

import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.fragments.MessagingPasswordFragment;
import com.silentcircle.silentphone2.R;

import java.lang.ref.WeakReference;

public class KeyStoreActivity extends AppCompatActivity {

    @SuppressWarnings("unused")
    static private String TAG = "KeyManagerActivity";

    private static String KEY_STORE_PASSWORD_PREFIX = "com.silentcircle.keystore.action.PW";
    private static String KEY_STORE_PIN_PREFIX = "com.silentcircle.keystore.action.PIN";

    public static String KEY_STORE_SET_PASSWORD = KEY_STORE_PASSWORD_PREFIX +  "_SET";
    public static String KEY_STORE_CHANGE_PASSWORD= KEY_STORE_PASSWORD_PREFIX +  "_CHANGE";
    public static String KEY_STORE_RESET_PASSWORD= KEY_STORE_PASSWORD_PREFIX +  "_RESET";

    public static String KEY_STORE_SET_PIN = KEY_STORE_PIN_PREFIX +  "_SET";
    public static String KEY_STORE_CHANGE_PIN = KEY_STORE_PIN_PREFIX +  "_CHANGE";
    public static String KEY_STORE_RESET_PIN = KEY_STORE_PIN_PREFIX +  "_RESET";

    public static final String CHAT_PASSWORD_PREFIX = "com.silentcircle.keystore.action.MESSAGING_PW";
    public static String KEY_STORE_SET_CHAT_PASSWORD_CHANGE = CHAT_PASSWORD_PREFIX +  "_RESET";

    private static final String USER_PASS_TAG = "spa_keystore_password_fragment";
    private static final String MESSAGING_PASS_TAG = "spa_messaging_password_fragment";

    static final int KEY_STORE_READY = 1;
    static final int KEY_STORE_FAILED = 2;

    private boolean mExternalStorageAvailable;
    private boolean mExternalStorageWriteable;

    /**
     * Internal handler to receive and process key store state messages.
     */
    InternalHandler mHandler;

    /*
     * The lifecycle functions
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PRNGFixes.apply();
        mHandler = new InternalHandler(this);
        processIntent(getIntent());
    }

    /* **
    *** We may re-enable backup/restore at some time later, via a specific Intent
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {

            case R.id.backup_store: {
                final File file = Environment.getExternalStorageDirectory();
                if (!file.exists() || !file.isDirectory() || !file.canWrite()) {
                    showInputInfo(getString(R.string.backup_access));
                    return true;
                }
                final File backupFile = new File(file, "sc_keymngrdb.scsave");
                if (!KeyStoreDatabase.backupStoreTo(backupFile)) {           // Should this go to a async task?
                    showInputInfo(getString(R.string.backup_failed));
                    return true;
                }
                Toast.makeText(this, getString(R.string.backup_created), Toast.LENGTH_LONG).show();
                break;
            }
            case R.id.restore_store: {
                final File file = new File(Environment.getExternalStorageDirectory(), "sc_keymngrdb.scsave");
                if (!file.exists() || !file.canRead()) {
                    showInputInfo(getString(R.string.restore_access));
                    return true;
                }
                // User selected to restore the database instead of creating a new one
                // Remove the creation specific setting, then restore and show the normal
                // password screen.
                if (storeCreation) {
                    passwordInput.removeTextChangedListener(pwFilter);
                    passwordInput2.setVisibility(View.GONE);
                    pwStrength.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
                    storeCreation = false;
                }
                else {                              // restore an existing, open DB, thus overwriting it
                    KeyStoreHelper.closeDatabase();
                    ProviderDbBackend.sendLockRequests();
                }
                if (!KeyStoreDatabase.restoreStoreFrom(file)) {
                    showInputInfo(getString(R.string.restore_failed));
                    return true;
                }
                showNormalScreen();
                Toast.makeText(this, getString(R.string.restore_created), Toast.LENGTH_LONG).show();
                break;
            }
        }
        return true;
    }
**** */
    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onNewIntent (Intent intent) {
        processIntent(intent);
    }

    private void processIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }
        String action = intent.getAction();
        if (action == null)
            return;
        boolean readyIntent = KeyManagerSupport.KEY_STORE_READY_ACTION.equals(action);

        // If we got a KEY_STORE_READY_ACTION and key store is open or we can open it with default: everything OK.
        if (readyIntent && (KeyStoreHelper.isReady() || KeyStoreHelper.openWithDefault(getApplicationContext()))) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        // Otherwise we need to check what to do.
        setupActionBar();
        setContentView(R.layout.key_store_activity);
        if (readyIntent) {
            if (KeyStoreHelper.getUserPasswordType(this) == KeyStoreHelper.USER_PW_TYPE_PW) {
                passwordUi(action, false);
            }
            else {
                passwordUi(action, true);
            }
        }
        else {
            updateExternalStorageState();
            invalidateOptionsMenu();
            if (action.startsWith(CHAT_PASSWORD_PREFIX)) {
                chatPasswordUi();
            }
            else if (action.startsWith(KEY_STORE_PASSWORD_PREFIX)) {
                action = action.replace(KEY_STORE_PASSWORD_PREFIX, "");
                passwordUi(action, false);
            }
            else {
                action = action.replace(KEY_STORE_PIN_PREFIX, "");
                passwordUi(action, true);
            }
        }
    }

    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar == null)
            return;
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setHomeButtonEnabled(false);

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }

    private void passwordUi(String action, boolean usePin) {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        PasswordFragment usernamePassword = (PasswordFragment)fm.findFragmentByTag(USER_PASS_TAG);
        if (usernamePassword == null) {
            usernamePassword = PasswordFragment.newInstance(action, usePin);
        }
        ft.replace(R.id.ks_container, usernamePassword, USER_PASS_TAG).commitAllowingStateLoss();
    }

    private void chatPasswordUi() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        MessagingPasswordFragment messagingPasswordFragment =
                (MessagingPasswordFragment) fm.findFragmentByTag(MESSAGING_PASS_TAG);
        if (messagingPasswordFragment == null) {
            messagingPasswordFragment = MessagingPasswordFragment.newInstance();
        }
        ft.replace(R.id.ks_container, messagingPasswordFragment, MESSAGING_PASS_TAG).commitAllowingStateLoss();
    }

    void updateExternalStorageState() {
        String state = Environment.getExternalStorageState();
        switch (state) {
            case Environment.MEDIA_MOUNTED:
                mExternalStorageAvailable = mExternalStorageWriteable = true;
                break;
            case Environment.MEDIA_MOUNTED_READ_ONLY:
                mExternalStorageAvailable = true;
                mExternalStorageWriteable = false;
                break;
            default:
                mExternalStorageAvailable = mExternalStorageWriteable = false;
                break;
        }
    }

    static char[] toCharArray(TextView inView) {

        char[] retArray;
        Editable inData = inView.getEditableText();
        if (inData != null) {
            int length = inData.length();
            retArray = new char[length];
            inData.getChars(0, length, retArray, 0);
            inData.clear();
            inView.setText(inData);
        }
        else {
            CharSequence chars = inView.getText();
            if (chars == null)
                return null;
            int length = chars.length();
            retArray = new char[length];
            for (int i = 0; i < length; i++) {
                retArray[i] = chars.charAt(i);
            }
        }
        return retArray;
    }

    /**
     * Internal message handler class.
     *
     * @author werner
     *
     */
    static class InternalHandler extends Handler {
        private final WeakReference<KeyStoreActivity> mTarget;

        InternalHandler(KeyStoreActivity parent) {
            mTarget = new WeakReference<>(parent);
        }

        @Override
        public void handleMessage(Message msg) {
            KeyStoreActivity parent = mTarget.get();
            if (parent == null)
                return;
            switch (msg.what) {
                case KEY_STORE_READY:
                    parent.setResult(RESULT_OK);
                    break;
                case KEY_STORE_FAILED:
                    parent.setResult(RESULT_CANCELED);
                    break;
            }
            parent.finish();
        }
    }

    void showInputInfo(String msg) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, "SilentCircleKeyManagerInfo");
    }

    /*
     * Dialog classes to display Error and Information messages.
     */
    private static String MESSAGE = "message";
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
            builder.setTitle(getString(R.string.information_dialog))
                    .setMessage(getArguments().getString(MESSAGE))
                    .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
