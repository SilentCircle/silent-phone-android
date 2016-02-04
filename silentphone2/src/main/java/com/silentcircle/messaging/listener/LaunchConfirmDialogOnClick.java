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
package com.silentcircle.messaging.listener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.silentcircle.silentphone2.R;

public class LaunchConfirmDialogOnClick implements View.OnClickListener {

    private final OnConfirmListener onConfirmListener;
    private final OnConfirmNoRepeatListener onConfirmNoRepeatListener;
    private final int titleResourceID;
    private final int messageResourceID;
    private final int cancelLabelResourceID;
    private final int confirmLabelResourceID;

    public LaunchConfirmDialogOnClick(OnConfirmListener onConfirmListener) {
        this(R.string.are_you_sure, R.string.cannot_be_undone, onConfirmListener);
    }

    public LaunchConfirmDialogOnClick(int titleResourceID, int messageResourceID,
            OnConfirmListener onConfirmListener) {
        this(titleResourceID, messageResourceID, R.string.dialog_button_cancel,
                R.string.dialog_button_yes, onConfirmListener);
    }

    public LaunchConfirmDialogOnClick(int titleResourceID, int messageResourceID,
            int cancelLabelResourceID, int confirmLabelResourceID,
            OnConfirmListener onConfirmListener) {
        this.titleResourceID = titleResourceID;
        this.messageResourceID = messageResourceID;
        this.cancelLabelResourceID = cancelLabelResourceID;
        this.confirmLabelResourceID = confirmLabelResourceID;
        this.onConfirmListener = onConfirmListener;
        this.onConfirmNoRepeatListener = null;
    }

    public LaunchConfirmDialogOnClick(OnConfirmNoRepeatListener onConfirmListener) {
        this(R.string.are_you_sure, R.string.cannot_be_undone, onConfirmListener);
    }

    public LaunchConfirmDialogOnClick(int titleResourceID, int messageResourceID,
                                      OnConfirmNoRepeatListener onConfirmListener) {
        this(titleResourceID, messageResourceID, R.string.dialog_button_cancel,
                R.string.dialog_button_yes, onConfirmListener);
    }

    public LaunchConfirmDialogOnClick(int titleResourceID, int messageResourceID,
            int cancelLabelResourceID, int confirmLabelResourceID,
            OnConfirmNoRepeatListener onConfirmListener) {
        this.titleResourceID = titleResourceID;
        this.messageResourceID = messageResourceID;
        this.cancelLabelResourceID = cancelLabelResourceID;
        this.confirmLabelResourceID = confirmLabelResourceID;
        this.onConfirmListener = null;
        this.onConfirmNoRepeatListener = onConfirmListener;
    }

    @Override
    public void onClick(View v) {
        show(v.getContext());
    }

    public void show(final Context context) {
        show(context, false);
    }

    public void show(final Context context, final boolean flagSaveChoice) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);

        alert.setTitle(titleResourceID);
        alert.setMessage(messageResourceID);

        alert.setNegativeButton(cancelLabelResourceID, new DismissDialogOnClick());
        alert.setPositiveButton(confirmLabelResourceID, new ConfirmOnClick(onConfirmListener));

        if (flagSaveChoice) {
            ConfirmDialogNoRepeatListener listener = new ConfirmDialogNoRepeatListener(onConfirmNoRepeatListener);
            alert.setPositiveButton(confirmLabelResourceID, listener);

            LinearLayout layout = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.dialog_with_checkbox, null);
            CheckBox checkbox = (CheckBox) layout.findViewById(R.id.checkbox);
            checkbox.setOnCheckedChangeListener(listener);
            checkbox.setChecked(false);
            checkbox.setText(R.string.make_primary);
            alert.setView(layout);
        }
        else {
            alert.setPositiveButton(confirmLabelResourceID, new ConfirmOnClick(onConfirmListener));
        }

        alert.show();
    }

    protected class ConfirmDialogNoRepeatListener implements DialogInterface.OnClickListener,
            CompoundButton.OnCheckedChangeListener {

        private final OnConfirmNoRepeatListener mConfirmListener;
        private boolean mShouldNotShowAgain;

        public ConfirmDialogNoRepeatListener(OnConfirmNoRepeatListener listener) {
            mShouldNotShowAgain = false;
            mConfirmListener = listener;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mShouldNotShowAgain = isChecked;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            mConfirmListener.onConfirm(((AlertDialog) dialog).getContext(), mShouldNotShowAgain);
        }

    }
}
