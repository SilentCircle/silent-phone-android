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
package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.silentcircle.messaging.listener.DismissDialogOnClick;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
import com.silentcircle.messaging.listener.OnConfirmNoRepeatListener;
import com.silentcircle.silentphone2.R;

/**
 *
 */
public class AlertDialogFragment extends DialogFragment {

    @SuppressWarnings("unused")
    private static final String TAG = AlertDialogFragment.class.getSimpleName();

    public static final String TAG_ALERT_DIALOG =
            "com.silentcircle.messaging.fragments.AlertDialogFragment.dialog";

    public static final String DIALOG_TITLE =
            "com.silentcircle.messaging.fragments.AlertDialogFragment.title";
    public static final String DIALOG_MESSAGE =
            "com.silentcircle.messaging.fragments.AlertDialogFragment.message";
    public static final String DIALOG_CANCEL =
            "com.silentcircle.messaging.fragments.AlertDialogFragment.cancel";
    public static final String DIALOG_CONFIRM =
            "com.silentcircle.messaging.fragments.AlertDialogFragment.confirm";
    public static final String DIALOG_SAVE_CHOICE =
            "com.silentcircle.messaging.fragments.AlertDialogFragment.saveChoice";
    public static final String DIALOG_ADDITIONAL_ARGUMENTS =
            "com.silentcircle.messaging.fragments.AlertDialogFragment.additionalArguments";

    /**
     * Listener interface to pass result to caller.
     */
    public interface OnAlertDialogConfirmedListener {

        void onAlertDialogConfirmed(DialogInterface dialog, int requestCode, Bundle bundle,
            boolean saveChoice);
    }

    public static AlertDialogFragment getInstance(int titleId, int messageId, int cancelId,
            int confirmId, Bundle bundle, boolean saveChoice) {
        AlertDialogFragment fragment = new AlertDialogFragment();
        fragment.setCancelable(true);
        Bundle arguments = new Bundle();
        arguments.putInt(DIALOG_TITLE, titleId);
        arguments.putInt(DIALOG_MESSAGE, messageId);
        arguments.putInt(DIALOG_CANCEL, cancelId);
        arguments.putInt(DIALOG_CONFIRM, confirmId);
        arguments.putBoolean(DIALOG_SAVE_CHOICE, saveChoice);
        arguments.putBundle(DIALOG_ADDITIONAL_ARGUMENTS, bundle);
        fragment.setArguments(arguments);
        return fragment;
    }

    public static AlertDialogFragment getInstance(int titleId, int messageId, int cancelId,
            int confirmId, Bundle bundle) {
        return getInstance(titleId, messageId, cancelId, confirmId, bundle, false);
    }

    public AlertDialogFragment() {
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Bundle arguments = getArguments();
        int titleResourceID = arguments.getInt(DIALOG_TITLE);
        int messageResourceID = arguments.getInt(DIALOG_MESSAGE);
        int cancelLabelResourceID = arguments.getInt(DIALOG_CANCEL);
        int confirmLabelResourceID = arguments.getInt(DIALOG_CONFIRM);
        boolean saveChoice = arguments.getBoolean(DIALOG_SAVE_CHOICE);

        Context context = getActivity();
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);

        alertDialog.setTitle(titleResourceID);
        alertDialog.setMessage(messageResourceID);

        alertDialog.setNegativeButton(cancelLabelResourceID, new DismissDialogOnClick());

        if (saveChoice) {
            LaunchConfirmDialogOnClick.ConfirmDialogNoRepeatListener listener =
                    new LaunchConfirmDialogOnClick.ConfirmDialogNoRepeatListener(new OnConfirmNoRepeatListener() {

                        @Override
                        public void onConfirm(DialogInterface dialog, int which, boolean shouldNotShowAgain) {
                            AlertDialogFragment.this.onClick(dialog, which, shouldNotShowAgain);
                            dialog.dismiss();
                        }
                    });
            alertDialog.setPositiveButton(confirmLabelResourceID, listener);

            LinearLayout layout = (LinearLayout) LayoutInflater.from(context).inflate(
                    R.layout.dialog_with_checkbox, null);
            CheckBox checkbox = (CheckBox) layout.findViewById(R.id.checkbox);
            checkbox.setOnCheckedChangeListener(listener);
            checkbox.setChecked(false);
            checkbox.setText(R.string.make_primary);
            alertDialog.setView(layout);
        }
        else {
            alertDialog.setPositiveButton(confirmLabelResourceID, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    AlertDialogFragment.this.onClick(dialog, which, false);
                    dialog.dismiss();
                }
            });
        }

        Dialog dialog = alertDialog.create();
        dialog.setCanceledOnTouchOutside(false);

        return dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private void onClick(DialogInterface dialog, int which, boolean saveChoice) {

        Bundle bundle = getArguments();
        if (bundle != null) {
            bundle = bundle.getBundle(DIALOG_ADDITIONAL_ARGUMENTS);
        }

        Fragment fragment = getTargetFragment();
        if (fragment != null) {
            onAlertDialogConfirmed(fragment, dialog, which, bundle, saveChoice);
        }
        else {
            Activity activity = getActivity();
            onAlertDialogConfirmed(activity, dialog, which, bundle, saveChoice);
        }
    }

    private void onAlertDialogConfirmed(Fragment fragment, DialogInterface dialog, int which,
            Bundle bundle, boolean saveChoice) {
        try {
            OnAlertDialogConfirmedListener listener =
                    (OnAlertDialogConfirmedListener) fragment;
            if (listener != null) {
                listener.onAlertDialogConfirmed(dialog, getTargetRequestCode(), bundle, saveChoice);
            }
        }
        catch (ClassCastException e) {
            throw new ClassCastException(
                    "Calling fragment must implement interface OnAlertDialogConfirmedListener");

        }
    }

    private void onAlertDialogConfirmed(Activity activity, DialogInterface dialog, int which,
            Bundle bundle, boolean saveChoice) {
        try {
            OnAlertDialogConfirmedListener listener =
                    (OnAlertDialogConfirmedListener) activity;
            if (listener != null) {
                listener.onAlertDialogConfirmed(dialog, which, bundle, saveChoice);
            }
        }
        catch (ClassCastException e) {
            throw new ClassCastException(
                    "Calling activity must implement interface OnAlertDialogConfirmedListener");
        }
    }
}
