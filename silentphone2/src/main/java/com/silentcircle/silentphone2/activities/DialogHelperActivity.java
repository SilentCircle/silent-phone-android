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

package com.silentcircle.silentphone2.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;

/**
 * A very simple Activity to display a Dialog
 */
public class DialogHelperActivity extends Activity implements InfoMsgDialogFragment.InfoDialogCallback {

    @SuppressWarnings("unused")
    private static final String TAG = "DialogHelperActivity";

    private InfoMsgDialogFragment mInfoMsg;

    // Static helper function to start this DialogHelperActivity
    public static void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {

        Intent intent = new Intent(SilentPhoneApplication.getAppContext(), DialogHelperActivity.class);
        intent.putExtra(InfoMsgDialogFragment.MESSAGE, msgResId);

        showDialogCommon(intent, titleResId, positiveBtnLabel, negativeBtnLabel);
    }

    public static void showDialog(int titleResId, String msg, int positiveBtnLabel, int negativeBtnLabel) {
        Intent intent = new Intent(SilentPhoneApplication.getAppContext(), DialogHelperActivity.class);
        intent.putExtra(InfoMsgDialogFragment.MESSAGE, msg);

        showDialogCommon(intent, titleResId, positiveBtnLabel, negativeBtnLabel);
    }

    private static void showDialogCommon(Intent intent, int titleResId, int positiveBtnLabel, int negativeBtnLabel) {

        intent.putExtra(InfoMsgDialogFragment.TITLE, titleResId);
        intent.putExtra(InfoMsgDialogFragment.POSITIVE_BTN_LABEL, positiveBtnLabel);
        intent.putExtra(InfoMsgDialogFragment.NEGATIVE_BTN_LABEL, negativeBtnLabel);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        SilentPhoneApplication.getAppContext().startActivity(intent);
    }

    private void showInfo(Bundle args) {
        mInfoMsg = InfoMsgDialogFragment.newInstance(args);
        FragmentManager fragmentManager = getFragmentManager();
        mInfoMsg.show(fragmentManager, "SilentDialogHelper");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        Intent intent = getIntent();
        Bundle args = intent.getExtras();
        if (args == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        args.putBoolean("helper_activity", true);
        showInfo(args);
    }


    /**
     * Callback if user clicks positive button
     */
    @Override
    public void onClickedPositive() {
        mInfoMsg.dismiss();
        super.onBackPressed();
        finish();
    }

    /**
     * Callback if user clicks negative button
     */
    @Override
    public void onClickedNegative() {
        mInfoMsg.dismiss();
        super.onBackPressed();
        finish();
    }

    /**
     * Callback if user cancels (with back button) the dialog
     */
    @Override
    public void onClickedCancel() {
        mInfoMsg.dismiss();
        super.onBackPressed();
        finish();
    }
}
