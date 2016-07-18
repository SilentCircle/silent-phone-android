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

package com.silentcircle.purchase.fragments;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.silentcircle.purchase.activities.SelectPurchaseCardActivity;
import com.silentcircle.purchase.interfaces.PaymentForm;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class PaymentFormFragment extends Fragment implements PaymentForm {
    public static final String TAG = "PaymentFormFragment";

    public static final String CARD_NUMBER_ERROR = "card_number_error";
    public static final String CVC_ERROR = "cvc_error";

    private Button saveButton;
    private EditText cardNumber;
    private EditText cvc;
    private Spinner monthSpinner;
    private Spinner yearSpinner;
    private TextInputLayout numberTil, cvcTil;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.payment_form_fragment, container, false);

        this.saveButton = (Button) view.findViewById(R.id.save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveForm();
            }
        });

        this.cardNumber = (EditText) view.findViewById(R.id.card_number_id);
        this.cvc = (EditText) view.findViewById(R.id.cvc_id);
        this.monthSpinner = (Spinner) view.findViewById(R.id.expMonth_id);
        this.yearSpinner = (Spinner) view.findViewById(R.id.expYear_id);

        this.cardNumber.setFocusableInTouchMode(true);
        this.cardNumber.requestFocus();

        numberTil = (TextInputLayout) view.findViewById(R.id.card_number_til_id);
        cvcTil = (TextInputLayout) view.findViewById(R.id.cvc_til_id);

        if(savedInstanceState != null){
            String error = savedInstanceState.getString(CARD_NUMBER_ERROR);
            if(!TextUtils.isEmpty(error)){
                numberTil.setErrorEnabled(true);
                numberTil.setError(error);
            }
            error = savedInstanceState.getString(CVC_ERROR);
            if(!TextUtils.isEmpty(error)){
                cvcTil.setErrorEnabled(true);
                cvcTil.setError(error);
            }
        }

        this.yearSpinner.setAdapter(getYearAdapter(view.getContext()));

        return view;
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        CharSequence err;
        if(numberTil != null) {
            err = numberTil.getError();
            if (err != null) {
                savedInstanceState.putString(CARD_NUMBER_ERROR, err.toString());
            }
        }
        if(cvcTil != null) {
            err = cvcTil.getError();
            if (err != null) {
                savedInstanceState.putString(CVC_ERROR, err.toString());
            }
        }
    }

    @Override
    public String getCardNumber() {
        return this.cardNumber.getText().toString();
    }

    @Override
    public String getCvc() {
        return this.cvc.getText().toString();
    }

    @Override
    public Integer getExpMonth() {
        return getInteger(this.monthSpinner);
    }

    @Override
    public Integer getExpYear() {
        return getInteger(this.yearSpinner);
    }

    public void saveForm() {
        ((SelectPurchaseCardActivity)getActivity()).saveCreditCard(this);
    }

    public void clearForm(){
        if(cardNumber != null) {
            cardNumber.setText("");
            cvc.setText("");
            yearSpinner.setSelection(0);
            monthSpinner.setSelection(0);
        }

    }

    private Integer getInteger(Spinner spinner) {
    	try {
    		return Integer.parseInt(spinner.getSelectedItem().toString());
    	} catch (NumberFormatException e) {
    		return 0;
    	}
    }

    /**
     *
     * @param error : error message
     * @param resId : resource id of TextInputLayout which needs to show error message
     */
    public void handleError(String error, int resId){
        clearError();
        switch (resId){
            case R.id.card_number_til_id:
                numberTil.setErrorEnabled(true);
                numberTil.setError(error);
                break;
            case R.id.cvc_til_id:
                cvcTil.setErrorEnabled(true);
                cvcTil.setError(error);
                break;
            default:
                break;
        }
    }
    public void clearError(){
        if(cvcTil != null) {
            cvcTil.setErrorEnabled(false);
            cvcTil.setError(null);
            numberTil.setErrorEnabled(false);
            numberTil.setError(null);
        }
    }

    private ArrayAdapter<String> getYearAdapter(@NonNull final Context context) {
        ArrayList<String> years = new ArrayList<>();
        Date date = new Date();
        date.setTime(System.currentTimeMillis());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        // create a list of years spanning frm current year to 10 years in future
        for (int j = 0, i = calendar.get(Calendar.YEAR); j < 11; j++, i++) {
            years.add(String.valueOf(i));
        }
        return new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, years);
    }
}
