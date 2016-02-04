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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.receivers.OutgoingCallReceiver;

/**
 * A very simple list to display some trace information
 */
public class SelectSecureOca extends ActionBarActivity {
    public static final String TAG = "SelectSecureOca";
    
    public static final String MESSAGE = "message";
    public static final String TITLE = "title";
    public static final String NEGATIVE_BUTTON = "negative_button";
    public static final String POSITIVE_BUTTON = "positive_button";
    public static final String NEUTRAL_BUTTON  = "neutral_button";
    public static final String PHONE_NUMBER  = "phone_number";
    
    
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
        private String mNumber;

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
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            dialCancel();                       // This causes our OCA receiver to dismiss the call
            mParent.finish();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
            Bundle args = getArguments();
            if (args == null) {
                mParent.finish();
                return null;
            }

            int positive = args.getInt(POSITIVE_BUTTON, 0);
            mPositive = positive != 0 ? positive : android.R.string.ok;
            mNegative = args.getInt(NEGATIVE_BUTTON, 0);
            mNeutral = args.getInt(NEUTRAL_BUTTON, 0);
            mNumber = args.getString(PHONE_NUMBER);

            builder.setTitle(args.getString(TITLE))
                    .setMessage(args.getString(MESSAGE))
                    .setIcon(R.drawable.ic_launcher_sp)
                    .setPositiveButton(getString(mPositive), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialOca();
                            mParent.finish();
                        }
                    });
            if (mNegative != 0) {
                builder.setNegativeButton(getString(mNegative), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialNormal();
                        mParent.finish();
                    }
                });
            }
            if (mNeutral != 0) {
                builder.setNeutralButton(getString(mNegative), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mParent.finish();
                    }
                });
            }
            // Create the AlertDialog object and return it
            return builder.create();
        }
        
        // An ACTION_CALL triggers the SPA Outgoing call receiver which just activated us :-).
        // The receiver keeps a list of recent seen numbers to avoid this.
        // If the user selects to route the call via Silent Phone the function adds a prefix
        // to the normal phone number. SPA's outgoing call receiver detects this and performs
        // the necessary actions.
        private void dialNormal() {
            Intent phoneIntent = new Intent(Intent.ACTION_CALL);
            phoneIntent.setData(Uri.parse("tel:" + mNumber));
            startActivity(phoneIntent);
        }
        
        private void dialOca() {
            Intent phoneIntent = new Intent(Intent.ACTION_CALL);
            phoneIntent.setData(Uri.parse("tel:" + OutgoingCallReceiver.prefix_add + mNumber));
            startActivity(phoneIntent);
        }

        private void dialCancel() {
            Intent phoneIntent = new Intent(Intent.ACTION_CALL);
            phoneIntent.setData(Uri.parse("tel:" + OutgoingCallReceiver.cancel_alpha + mNumber));
            startActivity(phoneIntent);
        }
    }
}
