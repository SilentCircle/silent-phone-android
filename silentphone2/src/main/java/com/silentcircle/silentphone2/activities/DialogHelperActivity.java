/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.silentcircle.silentphone2.R;

/**
 * A very simple list to display some trace information
 */
public class DialogHelperActivity extends ActionBarActivity {
    public static final String MESSAGE = "message";
    public static final String TITLE = "title";
    public static final String NEGATIVE_BUTTON = "negative_button";
    public static final String POSITIVE_BUTTON = "positive_button";
    public static final String NEUTRAL_BUTTON  = "neutral_button";
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle args = intent.getExtras();
        if (args == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        showInfo(args);
    }

    public void showInfo(Bundle args) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(args);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, "SilentDialogHelper");
    }

    public static class InfoMsgDialogFragment extends DialogFragment {
        private Activity mParent;

        private int mPositive;
        private int mNegative;
        private int mNeutral;

        public static InfoMsgDialogFragment newInstance(Bundle args) {
            InfoMsgDialogFragment f = new InfoMsgDialogFragment();
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

            int positive = args.getInt(POSITIVE_BUTTON, 0);
            mPositive = positive != 0 ? positive : android.R.string.ok;
            mNegative = args.getInt(NEGATIVE_BUTTON, 0);
            mNeutral = args.getInt(NEUTRAL_BUTTON, 0);

            if (args == null)
                return null;
            builder.setTitle(args.getString(TITLE))
                    .setMessage(args.getString(MESSAGE))
                    .setPositiveButton(getString(mPositive), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mParent.setResult(RESULT_OK);
                            mParent.finish();
                        }
                    });
            if (mNegative != 0) {
                builder.setNegativeButton(getString(mNegative), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mParent.setResult(RESULT_CANCELED);
                        mParent.finish();
                    }
                });
            }
            if (mNeutral != 0) {
                builder.setNeutralButton(getString(mNegative), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mParent.setResult(RESULT_FIRST_USER);
                        mParent.finish();
                    }
                });
            }
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

}
