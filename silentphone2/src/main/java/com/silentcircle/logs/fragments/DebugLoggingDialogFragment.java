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
package com.silentcircle.logs.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;

import com.silentcircle.silentphone2.R;

/**
 * Created by rli on 1/12/16.
 */
public class DebugLoggingDialogFragment extends DialogFragment {

    public static final String TAG = "DebugLoggingDialogFragment";
    public static final String PROGRESS_DIALOG = "progress_dialog";
    public static final String ERROR_DIALOG = "error_dialog";
    public static final String DIALOG_TYPE = "dialog_type";
    public static final String DIALOG_MESSAGE = "dialog_message";
    public static final String ENABLE_DEBUG_LOGGING = "enable_debug_logging";

    private ProgressDialog mProgressDialog;

    public DebugLoggingDialogFragment(){

    }

    public static DebugLoggingDialogFragment newInstance(String type, String message){
        DebugLoggingDialogFragment fragment = new DebugLoggingDialogFragment();

        Bundle args = new Bundle();
        args.putString(DIALOG_TYPE, type);
        if(!TextUtils.isEmpty(message)){
            args.putString(DIALOG_MESSAGE, message);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public void setProgressMessage(String message){
        if(mProgressDialog != null){
            mProgressDialog.setMessage(message);
        }
    }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String  type = getArguments().getString(DIALOG_TYPE);

        Dialog dialog = null;

        if(type.equals(PROGRESS_DIALOG)){
            dialog = createProgressDialog(getArguments().getString(DIALOG_MESSAGE));
        }
        else if(type.equals(ERROR_DIALOG)){
            dialog = createErrorDialog(getArguments().getString(DIALOG_MESSAGE));
        }
        return dialog;
    }

    private Dialog createProgressDialog(String message){
        mProgressDialog = new ProgressDialog(getActivity());
        mProgressDialog.setMessage(message);
        return mProgressDialog;
    }

    private Dialog createErrorDialog(String error) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(getString(R.string.logging_error));
        if(TextUtils.isEmpty(error)){
            alertDialog.setMessage(error);
        }
        else{
            alertDialog.setMessage(error);
        }
        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        return alertDialog.create();
    }
}
