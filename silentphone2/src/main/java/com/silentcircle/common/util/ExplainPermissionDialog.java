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

package com.silentcircle.common.util;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.util.Log;

/**
 * Helper class to show a permission explanation and callback after user read and confirmed explanation.
 *
 * Created by werner on 28.02.16.
 */
public class ExplainPermissionDialog {

    private final static String TAG = "ExplainPermissionDialog";

    private final static String MESSAGE = "MSG";
    private final static String TITLE = "TI";
    private final static String TOKEN = "TK";
    private final static String BUNDLE = "BN";


    public interface AfterReading {
        void explanationRead(int token, Bundle callerBundle);
    }

    /**
     * Show a dialog to explain why SPA needs a specific permission.
     *
     * After the user read the explanation the dialog informs the caller using the
     * {@code explanationRead} callback.
     *
     * @param activity The calling activity
     * @param token A token to identify this request during callback via 'explanationRead'
     * @param title The title of the dialog bix
     * @param message The message (long explanation)
     * @param callerBundle An optional Bundle. The call may store additional parameters/data for callback,
     *                     maybe {@code null}.
     */
    public static void showExplanation(Activity activity, int token, String title, CharSequence message, @Nullable Bundle callerBundle) {
        Bundle args = new Bundle();
        args.putString(TITLE, title);
        args.putCharSequence(MESSAGE, message);
        args.putInt(TOKEN, token);
        args.putBundle(BUNDLE, callerBundle);

        ExplainDialog infoMsg = ExplainDialog.newInstance(args);
        FragmentManager fragmentManager = activity.getFragmentManager();
        infoMsg.show(fragmentManager, "SilentExplainDialog");
    }

    public static class ExplainDialog extends DialogFragment {
        private Activity mParent;
        private Bundle mArgs;

        public static ExplainDialog newInstance(Bundle args) {
            ExplainDialog f = new ExplainDialog();
            f.setArguments(args);

            return f;
        }

        public ExplainDialog() {
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mArgs = getArguments();
            mParent = activity;
            if (!(mParent instanceof AfterReading)) {
                ClassCastException ce = new ClassCastException("Parent Activity must implement ExplainPermissionDialog#AfterReading interface.");
                Log.e(TAG, "Parent Activity must implement ExplainPermissionDialog#AfterReading interface.");
                throw ce;
            }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            ((AfterReading)mParent).explanationRead(mArgs.getInt(TOKEN), mArgs.getBundle(BUNDLE));
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
            if (mArgs == null)
                return null;

            builder.setTitle(mArgs.getString(TITLE))
                    .setMessage(mArgs.getCharSequence(MESSAGE))
                    .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ((AfterReading)mParent).explanationRead(mArgs.getInt(TOKEN), mArgs.getBundle(BUNDLE));
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
