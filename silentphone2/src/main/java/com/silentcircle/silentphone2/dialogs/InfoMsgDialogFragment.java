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
package com.silentcircle.silentphone2.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

/**
 * Simple dialog fragment.
 *
 * Created by rli on 10/29/14.
 */
public class InfoMsgDialogFragment extends DialogFragment {
    private static String MESSAGE = "message";
    private static String TITLE = "title";
    private static String POSITIVE_BTN_LABEL = "positive_button_label";
    private static String NEGATIVE_BTN_LABEL = "negative_button_label";
    private Activity mParent;

    // There are 4 constructors because of an issue with AlertDialog.Builder#set*(String) stripping links
    public static InfoMsgDialogFragment newInstance(String title, String msg, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment f = new InfoMsgDialogFragment();

        Bundle args = new Bundle();
        args.putString(TITLE, title);
        args.putString(MESSAGE, msg);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        f.setArguments(args);

        return f;
    }

    public static InfoMsgDialogFragment newInstance(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment f = new InfoMsgDialogFragment();

        Bundle args = new Bundle();
        args.putInt(TITLE, titleResId);
        args.putInt(MESSAGE, msgResId);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        f.setArguments(args);

        return f;
    }

    public static InfoMsgDialogFragment newInstance(String title, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment f = new InfoMsgDialogFragment();

        Bundle args = new Bundle();
        args.putString(TITLE, title);
        args.putInt(MESSAGE, msgResId);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        f.setArguments(args);

        return f;
    }

    public static InfoMsgDialogFragment newInstance(int titleResId, String msg, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment f = new InfoMsgDialogFragment();

        Bundle args = new Bundle();
        args.putInt(TITLE, titleResId);
        args.putString(MESSAGE, msg);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

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

        Object title = args.get(TITLE);
        Object message = args.get(MESSAGE);

        if(title instanceof String) {
            builder.setTitle((String) title);
        } else {
            if(args.getInt(TITLE, -1) > 0) {
                builder.setTitle(args.getInt(TITLE));
            }
        }

        if(message instanceof String) {
            builder.setMessage((String) message);
        } else {
            if(args.getInt(MESSAGE, -1) > 0) {
                builder.setMessage(args.getInt(MESSAGE));
            }
        }

        if(args.getInt(POSITIVE_BTN_LABEL, -1) > 0) {
            builder.setPositiveButton(args.getInt(POSITIVE_BTN_LABEL), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    //TODO;
                }
            });
        }
        if(args.getInt(NEGATIVE_BTN_LABEL, -1) > 0) {
            builder.setNegativeButton(args.getInt(NEGATIVE_BTN_LABEL), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    mParent.finish();
                }
            });
        }
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
