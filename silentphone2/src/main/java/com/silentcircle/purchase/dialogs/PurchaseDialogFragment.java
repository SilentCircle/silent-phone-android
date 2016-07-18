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

package com.silentcircle.purchase.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.silentcircle.purchase.activities.PaymentUseStripeActivity;
import com.silentcircle.purchase.activities.SelectPurchaseCardActivity;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

// Handle dialogs for in-app purchase
public class PurchaseDialogFragment extends DialogFragment {
    public static final String TAG = "PurchaseDialogFragment";
    public static final String PURCHASE_INPUT_DIALOG = "purchase_input_dialog";
    public static final String PURCHASE_CONFIRM_DIALOG = "purchase_confirm_dialog";
    public static final String PURCHASE_PROGRESS_DIALOG = "purchase_progress_dialog";
    public static final String PURCHASE_ERROR_DIALOG = "purchase_error_dialog";

    public static final String DIALOG_TYPE = "dialog_type";
    public static final String DIALOG_MESSAGE = "dialog_message";
    public static final String PURCHASE_AMOUNT = "purchase_amount";
    public static final String PURCHASE_ERROR = "purchase_error";

    public static final double MIN_PAY = 5.00;

    private String mMessage;

    private EditText mInput;
    private TextInputLayout mAmountTil;

    public static PurchaseDialogFragment newInstance(String type, String message){
        PurchaseDialogFragment fragment = new PurchaseDialogFragment();
        fragment.setCancelable(false);
        Bundle args = new Bundle();
        args.putString(DIALOG_TYPE, type);
        if(!TextUtils.isEmpty(message)){
            args.putString(DIALOG_MESSAGE, message);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public PurchaseDialogFragment(){

    }

    public void setMessage(String message){
        mMessage = message;
    }

    public String getMessage(){
        return mMessage;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String  type = getArguments().getString(DIALOG_TYPE);

        Dialog dialog = null;

        if(type.equals(PURCHASE_INPUT_DIALOG)){
            dialog = createPurchaseInputDialog(savedInstanceState);
        }
        else if(type.equals(PURCHASE_CONFIRM_DIALOG)){
            dialog = createConfirmPurchaseDialog(getArguments().getString(DIALOG_MESSAGE));
        }
        else if(type.equals(PURCHASE_PROGRESS_DIALOG)){
            dialog = createProgressDialog();
        }
        else if(type.equals(PURCHASE_ERROR_DIALOG)){
            dialog = createErrorDialog(getArguments().getString(DIALOG_MESSAGE));
        }
        return dialog;
    }

    private Dialog createPurchaseInputDialog(Bundle savedInstanceState){
        LinearLayout layout = (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                R.layout.dialog_inapp_purchase_amount, null);
        mInput = (EditText) layout.findViewById(R.id.edittext_amount);
        mAmountTil = (TextInputLayout) layout.findViewById(R.id.amount_til_id);

        // Handle rotation
        if(savedInstanceState != null){
            String savedAmount = savedInstanceState.getString(PURCHASE_AMOUNT);
            mInput.setText(savedAmount);
            mInput.setSelection(savedAmount != null ? savedAmount.length() : 0);

            String error = savedInstanceState.getString(PURCHASE_ERROR);
            if(!TextUtils.isEmpty(error)){
                mAmountTil.setErrorEnabled(true);
                mAmountTil.setError(error);
            }
        }

        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 1 && !s.toString().equals("$")) {
                    mInput.setText("$" + s.toString());
                    mInput.setSelection(mInput.getText().length());
                }
                if (s.length() == 1 && s.toString().equals("$")) {
                    mInput.setText(null);
                }
            }
        });

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(getString(R.string.in_app_purchase));
        alertDialog.setMessage(getString(R.string.select_amount));
        alertDialog.setView(layout);
        alertDialog.setPositiveButton(getString(android.R.string.ok), null);
        alertDialog.setNegativeButton(getString(android.R.string.cancel), null);

        /*
         * Show keyboard immediately for amount entry
         *

        mInput.post(new Runnable() {
            @Override
            public void run() {
                DialerUtils.showInputMethod(mInput);
            }
        });
         */

        final AlertDialog dialog = alertDialog.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                Button positiveButton = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        String amount = mInput.getText().toString().trim();
                        double payAmount = 0;
                        if (!TextUtils.isEmpty(amount)) {
                            payAmount = Double.parseDouble(amount.substring(1).trim());
                        }
                        if (payAmount < MIN_PAY) {

                            mAmountTil.setErrorEnabled(true);
                            mAmountTil.setError(getActivity().getString(R.string.minimum_payment));
                            return;
                        }
                        else{
                            if(getActivity() instanceof PaymentUseStripeActivity) {
                                mAmountTil.setErrorEnabled(false);
                                PaymentUseStripeActivity activity = (PaymentUseStripeActivity) getActivity();
                                Intent intent = new Intent(activity, SelectPurchaseCardActivity.class);
                                intent.putExtra(PURCHASE_AMOUNT, amount);
                                activity.startActivityForResult(intent, SelectPurchaseCardActivity.PURCHASE_REQUEST_CODE);
                                activity.setCheckedRadioButton(0);
                                dialog.dismiss();
                            }
                            else{
                                if(ConfigurationUtilities.mTrace){
                                    Log.d(TAG, "Should not get here." );
                                }
                            }
                        }
                    }
                });
                Button negativeButton = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE);
                negativeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        //if custom amount canceled, goes back to in-app purchse default selection
                        if(getActivity() instanceof PaymentUseStripeActivity) {
                            ((PaymentUseStripeActivity)getActivity()).setCheckedRadioButton(0);
                        }
                        mAmountTil.setErrorEnabled(false);
                        dialog.dismiss();
                    }
                });
            }
        });

        return dialog;
    }

    private Dialog createConfirmPurchaseDialog(String message){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(getString(R.string.confirm_payment_title));
        alertDialog.setMessage(message);
        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (getActivity() instanceof SelectPurchaseCardActivity) {
                    ((SelectPurchaseCardActivity) getActivity()).processCharge();
                }
                dialogInterface.dismiss();
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        return alertDialog.create();
    }

    private Dialog createProgressDialog(){
        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setMessage(mMessage);
        return dialog;
    }

    private Dialog createErrorDialog(String error) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(getString(R.string.validationErrors));
        if(TextUtils.isEmpty(mMessage)){
            alertDialog.setMessage(error);
        }
        else{
            alertDialog.setMessage(mMessage);
        }
        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        return alertDialog.create();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if(mInput != null) {
            savedInstanceState.putString(PURCHASE_AMOUNT, mInput.getText().toString());
        }
        if(!TextUtils.isEmpty(mAmountTil.getError())){
            savedInstanceState.putString(PURCHASE_ERROR, mAmountTil.getError().toString());
        }
    }
}
