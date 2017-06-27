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
package com.silentcircle.silentphone2.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import com.silentcircle.logs.Log;
import android.widget.ArrayAdapter;

import com.silentcircle.silentphone2.R;

/**
 * Dialog fragment to show a list of choices from array.
 */
public class SingleChoiceDialogFragment extends DialogFragment {

    private static final String TAG = SingleChoiceDialogFragment.class.getSimpleName();

    public static final String TAG_CHOICE_DIALOG =
            "com.silentcircle.silentphone2.fragments.SingleChoiceDialogFragment.dialog";

    public static final String DIALOG_TITLE =
            "com.silentcircle.silentphone2.fragments.SingleChoiceDialogFragment.title";
    public static final String DIALOG_ITEMS =
            "com.silentcircle.silentphone2.fragments.SingleChoiceDialogFragment.items";
    public static final String DIALOG_SELECTED_ITEM =
            "com.silentcircle.silentphone2.fragments.SingleChoiceDialogFragment.selectedItem";

    protected static final int NO_SELECTION = -1;

    private OnSingleChoiceDialogItemSelectedListener mListener = null;
    private int mRequestCode;

    /**
     * Listener interface to pass selected item index to caller.
     */
    public interface OnSingleChoiceDialogItemSelectedListener {

        void onSingleChoiceDialogItemSelected(DialogInterface dialog, int requestCode, int index);
    }

    public static SingleChoiceDialogFragment getInstance(int titleId, @NonNull CharSequence[] items,
            int selectedItem) {
        SingleChoiceDialogFragment fragment = new SingleChoiceDialogFragment();
        fragment.setCancelable(true);
        Bundle arguments = new Bundle();
        arguments.putInt(DIALOG_TITLE, titleId);
        arguments.putCharSequenceArray(DIALOG_ITEMS, items);
        arguments.putInt(DIALOG_SELECTED_ITEM, selectedItem);
        fragment.setArguments(arguments);
        return fragment;
    }

    public SingleChoiceDialogFragment() {
    }

    public void setListener(OnSingleChoiceDialogItemSelectedListener listener, int requestCode) {
        mListener = listener;
        mRequestCode = requestCode;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Bundle arguments = getArguments();
        int titleId = arguments.getInt(DIALOG_TITLE);
        int selectedItem = arguments.getInt(DIALOG_SELECTED_ITEM, NO_SELECTION);
        CharSequence[] items = arguments.getCharSequenceArray(DIALOG_ITEMS);
        if (items == null) {
            Log.e(TAG, "Item selection dialog requires a list of items");
            items = new CharSequence[0];
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(titleId);
        alertDialog.setNegativeButton(R.string.dialog_button_cancel, null);

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getActivity(),
                R.layout.dialog_item_single_choice_light, items);
        alertDialog.setSingleChoiceItems(adapter, selectedItem, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int index) {
                try {
                    OnSingleChoiceDialogItemSelectedListener listener;
                    int requestCode;
                    if (mListener != null) {
                        listener = mListener;
                        requestCode = mRequestCode;
                    }
                    else {
                        listener = (OnSingleChoiceDialogItemSelectedListener) getTargetFragment();
                        requestCode = getTargetRequestCode();
                    }
                    listener.onSingleChoiceDialogItemSelected(dialog, requestCode, index);
                }
                catch (ClassCastException e) {
                    throw new ClassCastException(
                            "Calling fragment must implement interface OnSingleChoiceDialogItemSelectedListener");
                }
                dialog.dismiss();
            }
        });

        Dialog dialog = alertDialog.create();
        dialog.setCanceledOnTouchOutside(false);

        return dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

}
