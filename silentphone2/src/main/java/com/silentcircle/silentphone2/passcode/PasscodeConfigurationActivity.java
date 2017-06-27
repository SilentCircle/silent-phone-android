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
package com.silentcircle.silentphone2.passcode;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.common.primitives.Ints;
import com.silentcircle.messaging.fragments.CameraFragment;
import com.silentcircle.messaging.providers.VideoProvider;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.SingleChoiceDialogFragment;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SettingsItem;

import java.util.List;
import java.util.Set;

import static android.widget.Toast.LENGTH_SHORT;

/**
 *
 */
public class PasscodeConfigurationActivity extends AppLifecycleNotifierBaseActivity
        implements View.OnClickListener, SingleChoiceDialogFragment.OnSingleChoiceDialogItemSelectedListener,
        FingerprintDialogFragment.FingerprintDialogCallback{

    private static final String DIALOG_FRAGMENT_TAG = "fingerprintFragment";

    private static final int REQ_PASSCODE_SET = 1;
    private static final int REQ_PASSCODE_DISABLE = 2;
    private static final int REQ_PASSCODE_CHANGE = 3;
    private static final int REQ_PASSCODE_WIPE_TOGGLE = 4;

    private SettingsItem mPasscodeEnableButton;
    private SettingsItem mPasscodeChangeButton;
    private SettingsItem mPasscodeTimeoutSelector;
    private SettingsItem mPasscodeWipeToggle;
    private SettingsItem mFingerprintUnlockToggle;
    private List<Integer> mTimeoutSelectorValues;
    private String[] mTimeoutSelectorStrings;
    private PasscodeManager mPasscodeManager;

    private FingerprintManagerCompat mFingerprintManager;
    private boolean mHasFingerprintHW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utilities.setTheme(this);
        super.onCreate(savedInstanceState);

        mPasscodeManager = PasscodeManager.getSharedManager();

        setContentView(R.layout.activity_passcode_configuration);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.passcode_toolbar);
        setSupportActionBar(myToolbar);
        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setTitle(R.string.passcode_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        prepareTimeoutSelectorArrays();

        mPasscodeEnableButton = (SettingsItem) findViewById(R.id.passcode_enable);
        mPasscodeEnableButton.setOnClickListener(this);
        mPasscodeChangeButton = (SettingsItem) findViewById(R.id.passcode_change);
        mPasscodeChangeButton.setOnClickListener(this);
        mFingerprintUnlockToggle = (SettingsItem) findViewById(R.id.fingerprint_unlock);
        mFingerprintUnlockToggle.setOnClickListener(this);
        mFingerprintUnlockToggle.setChecked(mPasscodeManager.isFingerprintUnlockEnabled());
        mPasscodeTimeoutSelector = (SettingsItem) findViewById(R.id.passcode_timeout);
        mPasscodeTimeoutSelector.setOnClickListener(this);
        mPasscodeTimeoutSelector.setDescription(mTimeoutSelectorStrings[getCurrentIndexOfPasscodeTimeout()]);
        mPasscodeWipeToggle = (SettingsItem) findViewById(R.id.passcode_wipe);
        mPasscodeWipeToggle.setOnClickListener(this);
        mPasscodeWipeToggle.setChecked(mPasscodeManager.isWipeEnabled());

        mFingerprintManager = FingerprintManagerCompat.from(this);
        mHasFingerprintHW = mFingerprintManager.isHardwareDetected();
        int mFingerprintUnlockToggleVisibility = mHasFingerprintHW ? View.VISIBLE : View.GONE;
        mFingerprintUnlockToggle.setVisibility(mFingerprintUnlockToggleVisibility);

        if (mPasscodeManager.isPasscodeEnabled()) {
            setPasscodeEnabledUI();
        }
        else {
            setPasscodeDisabledUI();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void prepareTimeoutSelectorArrays() {
        mTimeoutSelectorValues = Ints.asList(0, 10, 30, 60, 160, 1800);
        mTimeoutSelectorStrings = new String[mTimeoutSelectorValues.size()];
        int index = 0;
        for (int secs : mTimeoutSelectorValues) {
            int minutes = secs / 60;
            String string;
            if (secs == 0) {
                string = getString(R.string.passcode_timeout_immediately);
            }
            else if (minutes == 0) {
                string = getString(R.string.in) + " " + secs + " " + getString(R.string.seconds);
            }
            else {
                int minuteStringID = (minutes == 1) ? R.string.minute : R.string.minutes;
                string = getString(R.string.in) + " " + minutes + " " + getString(minuteStringID);
            }
            mTimeoutSelectorStrings[index] = string;
            index++;
        }
    }

    private int getCurrentIndexOfPasscodeTimeout() {
        return mTimeoutSelectorValues.indexOf(mPasscodeManager.getTimeout());
    }

    private void setPasscodeEnabledUI() {
        mPasscodeEnableButton.setText(getString(R.string.passcode_disable));
        mPasscodeChangeButton.setEnabled(true);
        mPasscodeTimeoutSelector.setEnabled(true);
        mPasscodeWipeToggle.setEnabled(true);
        if (mHasFingerprintHW) {
            mFingerprintUnlockToggle.setEnabled(true);
        }
    }

    private void setPasscodeDisabledUI() {
        mPasscodeEnableButton.setText(getString(R.string.passcode_enable));
        mPasscodeChangeButton.setEnabled(false);
        mPasscodeTimeoutSelector.setEnabled(false);
        mPasscodeWipeToggle.setEnabled(false);
        if (mHasFingerprintHW) {
            mFingerprintUnlockToggle.setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // make action bar "back" key, go back
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.passcode_enable: {
                Intent intent = new Intent(this, PasscodeEnterActivity.class);
                String action = (mPasscodeManager.isPasscodeEnabled()) ? PasscodeEnterActivity.ACTION_PASSCODE_VALIDATE :
                        PasscodeEnterActivity.ACTION_PASSCODE_SET;
                intent.setAction(action);
                int requestCode = (mPasscodeManager.isPasscodeEnabled()) ?
                        REQ_PASSCODE_DISABLE : REQ_PASSCODE_SET;
                startActivityForResult(intent, requestCode);
            }
            break;
            case R.id.passcode_change: {
                Intent intent = new Intent(this, PasscodeEnterActivity.class);
                intent.setAction(PasscodeEnterActivity.ACTION_PASSCODE_CHANGE);
                startActivityForResult(intent, REQ_PASSCODE_CHANGE);
            }
            break;
            case R.id.fingerprint_unlock: {
                if (mFingerprintUnlockToggle.isChecked()) {
                    if (!mFingerprintManager.hasEnrolledFingerprints()) {
                        mFingerprintUnlockToggle.setChecked(false);
                        showNoFingerprintEnrolledWarning();
                        return;
                    }
                    FingerprintDialogFragment fingerprintDialog = new FingerprintDialogFragment();
                    Bundle args = new Bundle();
                    args.putInt(FingerprintDialogFragment.TYPE_KEY,
                            FingerprintDialogFragment.TYPE_ENROLL);
                    fingerprintDialog.setArguments(args);
                    fingerprintDialog.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
                }
                else {
                    mPasscodeManager.setFingerprintUnlockEnabled(false);
                }
            }
            break;
            case R.id.passcode_timeout: {
                int currectIndex = getCurrentIndexOfPasscodeTimeout();
                showSelectionDialog(R.string.passcode_timeout, mTimeoutSelectorStrings, currectIndex, 0);
            }
            break;
            case R.id.passcode_wipe: {
                Intent intent = new Intent(this, PasscodeEnterActivity.class);
                intent.setAction(PasscodeEnterActivity.ACTION_PASSCODE_VALIDATE);
                startActivityForResult(intent, REQ_PASSCODE_WIPE_TOGGLE);
            }
            break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_PASSCODE_SET: {
                if (resultCode == RESULT_OK) {
                    String pin = data.getStringExtra(PasscodeEnterActivity.EXTRA_PASSCODE_KEY);
                    mPasscodeManager.setPasscodeEnabled(true, pin);
                    mPasscodeManager.authorize(pin);
                    setPasscodeEnabledUI();
                    Toast.makeText(this, getString(R.string.passcode_set), LENGTH_SHORT).show();
                }
            }
            break;
            case REQ_PASSCODE_DISABLE: {
                if (resultCode == RESULT_OK) {
                    mPasscodeManager.deAuthorize();
                    mPasscodeManager.setPasscodeEnabled(false, null);
                    // also disable fingerprint, so that when passcode is re-enabled the user
                    // has to manually re-enable by adding a valid fingerprint
                    if (mHasFingerprintHW) {
                        mPasscodeManager.setFingerprintUnlockEnabled(false);
                        mFingerprintUnlockToggle.setChecked(false);
                    }
                    setPasscodeDisabledUI();
                    Toast.makeText(this, getString(R.string.passcode_disabled), LENGTH_SHORT).show();
                }
            }
            break;
            case REQ_PASSCODE_CHANGE: {
                if (resultCode == RESULT_OK) {
                    String pin = data.getStringExtra(PasscodeEnterActivity.EXTRA_PASSCODE_KEY);
                    mPasscodeManager.setPasscodeEnabled(true, pin);
                    Toast.makeText(this, getString(R.string.passcode_set), LENGTH_SHORT).show();
                }
            }
            break;
            case REQ_PASSCODE_WIPE_TOGGLE: {
                boolean newState = !mPasscodeManager.isWipeEnabled();
                if (resultCode == RESULT_OK) {
                    mPasscodeManager.setWipeEnabled(newState);
                    mPasscodeWipeToggle.setChecked(newState);
                }
                else {
                    mPasscodeWipeToggle.setChecked(!newState);
                }
            }
            break;
        }
    }

    private void showSelectionDialog(int titleId, String[] itemArray, int selectedItemIndex, int requestCode) {
        SingleChoiceDialogFragment fragment = SingleChoiceDialogFragment.getInstance(
                titleId,
                itemArray,
                selectedItemIndex);
        fragment.setListener(this, requestCode);
        fragment.show(getFragmentManager(), SingleChoiceDialogFragment.TAG_CHOICE_DIALOG);
    }

    @Override
    public void onSingleChoiceDialogItemSelected(DialogInterface dialog, int requestCode, int index) {
        mPasscodeManager.setTimeout(mTimeoutSelectorValues.get(index));
        mPasscodeTimeoutSelector.setDescription(mTimeoutSelectorStrings[index]);
    }

    private void showNoFingerprintEnrolledWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.fingerprint_enroll_title)
                .setMessage(R.string.fingerprint_enroll_message)
                .setCancelable(true)
                .setPositiveButton(R.string.fingerprint_enroll_action,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent settingsIntent = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                                settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(settingsIntent);
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.description_dismiss, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.create().show();
    }

    @Override
    public void onFingerprintSuccess(DialogFragment dialogFragment) {
        mPasscodeManager.setFingerprintUnlockEnabled(true);
        dialogFragment.dismissAllowingStateLoss();
    }

    @Override
    public void onFingerprintFail() {
        mPasscodeManager.setFingerprintUnlockEnabled(false);
        mFingerprintUnlockToggle.setChecked(false);
    }
}
